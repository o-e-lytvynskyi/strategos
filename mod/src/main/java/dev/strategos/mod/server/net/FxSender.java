package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.sim.BlockCracked;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.FxPayload;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

/**
 * A step's {@code fx} (AUD-01, AUD-04): a unit hit or killed in the tick sounds once, at its body, for the players who
 * track the body, a death in place of the tick's hits; it runs before the outputs apply, while the dead still have
 * bodies. The world's edits go to those who see their cells: the core's cracks with the step, the blocks the writer
 * broke and placed after its flush. A page's events of a tick travel together, at most {@value Fx#MAX_EVENTS} to a
 * payload; until fog (M8) every member sees every cell.
 */
public final class FxSender {
    private FxSender() {}

    public static void send(MatchRuntime rt, TickOutputs out, NetGateway net) {
        Map<Integer, FxKind> units = new LinkedHashMap<>();
        for (OutputEvent event : out.events()) {
            if (event instanceof UnitHurt hurt) {
                units.putIfAbsent(hurt.handle(), FxKind.UNIT_HIT);
            } else if (event instanceof UnitDied died) {
                units.put(died.handle(), FxKind.UNIT_DIED);
            }
        }
        MatchSpace space = rt.space();
        List<Fx.Event> cracks = new ArrayList<>();
        for (OutputEvent event : out.events()) {
            if (event instanceof BlockCracked c) {
                cracks.add(Fx.Event.block(
                        FxKind.BLOCK_CRACK, c.x(), c.y() - rt.space().origin().getY(), c.z(), c.stage()));
            }
        }
        sendWorld(rt, cracks, net);
        units.forEach((handle, kind) -> {
            UnitEntity body = rt.bodies().get(handle);
            if (body != null) {
                Fx.Event at = Fx.Event.at(
                        kind,
                        space.localX(body.getX()),
                        body.getY() - space.origin().getY(),
                        space.localZ(body.getZ()));
                net.send(
                        new Audience.TrackersOf(rt.id(), handle),
                        new FxPayload(new Fx(rt.id().value(), List.of(at))));
            }
        });
    }

    /** The blocks the writer broke and placed in {@code rt}'s space this tick, after its flush. */
    public static void sendWritten(MatchRuntime rt, List<BlockWriter.Written> written, NetGateway net) {
        BlockPos origin = rt.space().origin();
        List<Fx.Event> events = new ArrayList<>(written.size());
        for (BlockWriter.Written w : written) {
            int x = w.pos().getX() - origin.getX();
            int z = w.pos().getZ() - origin.getZ();
            if (x >= 0 && z >= 0 && x < rt.space().sizeX() && z < rt.space().sizeZ()) {
                events.add(Fx.Event.block(w.kind(), x, w.pos().getY() - origin.getY(), z, w.stateId()));
            }
        }
        sendWorld(rt, events, net);
    }

    /** World events grouped by 16 × 16 page, in order, each group to those who see its cells, 64 to a payload. */
    private static void sendWorld(MatchRuntime rt, List<Fx.Event> events, NetGateway net) {
        if (events.isEmpty()) {
            return;
        }
        Map<Long, List<Fx.Event>> pages = new TreeMap<>();
        for (Fx.Event e : events) {
            long page = ((long) (e.x8() >> 7) << 32) | (e.z8() >> 7); // eighths: 128 to a page
            pages.computeIfAbsent(page, k -> new ArrayList<>()).add(e);
        }
        pages.forEach((page, list) -> {
            int cellX = (int) (page >>> 32) * 16;
            int cellZ = (int) (page & 0xFFFF_FFFFL) * 16;
            for (int from = 0; from < list.size(); from += Fx.MAX_EVENTS) {
                List<Fx.Event> part = list.subList(from, Math.min(list.size(), from + Fx.MAX_EVENTS));
                net.send(
                        new Audience.SeesCell(rt.id(), cellX, cellZ),
                        new FxPayload(new Fx(rt.id().value(), List.copyOf(part))));
            }
        });
    }
}
