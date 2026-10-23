package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.FxPayload;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A step's {@code fx} (AUD-01): a unit hit or killed in the tick sounds once, at its body, for the players who track
 * the body, a death in place of the tick's hits. It runs before the outputs apply, while the dead still have bodies.
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
}
