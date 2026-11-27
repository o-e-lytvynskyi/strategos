package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * What a chapter's script asks of its stage: blocks, the match, the orders and the queries. {@link DemoStage} is the
 * stage over a {@code ServerLevel}; the L1 tests run a script on a fake over the core's harness. A chapter takes this
 * interface, never the class.
 */
public interface Stage {
    /** The plot's middle on the ground: (0, 0) of its coordinates. */
    BlockPos centre();

    /** The ground's height, the first free block above the floor. */
    int floor();

    /** A block at plot (dx, dy above the floor, dz); before {@link #match}, so the match's pages hold it. */
    void block(int dx, int dy, int dz, BlockState state);

    /** A box of {@code state} from (x0, y0, z0) to (x1, y1, z1), plot coordinates, ends included. */
    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state);

    /** An oak at plot (dx, dz): a trunk of {@code trunk} logs and a small crown. */
    void oak(int dx, int dz, int trunk);

    /** A ripe berry bush at plot (dx, dz). */
    void bush(int dx, int dz);

    /** The plot's match, two factions on a square round its middle, with the watching player seated in slot 0. */
    void match();

    /** As {@link #match()}, on a square of side {@code size}; the map is revealed. */
    void match(int size);

    /** As {@link #match()} under Fog: only the chapter about fog opts in. */
    void fogMatch();

    /** Starts listening to the match's outputs; the match is made first. */
    void listen(Consumer<TickOutputs> listener);

    /** The match, once made: the real stage's, never the fake's. */
    MatchRuntime rt();

    /** Its queries, or null until the match steps. */
    @Nullable MatchQueries queries();

    /** As {@link #queries}, for a step that runs once the match does. */
    MatchQueries q();

    /** Plot X {@code dx} as the match's X. */
    double x(double dx);

    /** Plot Z {@code dz} as the match's Z. */
    double z(double dz);

    /** A command from the admin: spawns, grants, the world's hand. */
    void admin(Command command);

    /** A command from faction {@code faction}'s seat, as its player would send it. */
    void order(int faction, Command command);

    /** The match corner of {@code template}'s footprint with its middle at plot (dx, dz). */
    int[] corner(TemplateId template, double dx, double dz);

    /** A server command, its output kept out of the chat. */
    void command(String command);

    ServerLevel level();

    MinecraftServer server();

    /** The watching player. */
    ServerPlayer player();

    /** Plot (dx, dz) as the match's point. */
    default Vec2d at(double dx, double dz) {
        return new Vec2d(x(dx), z(dz));
    }

    /**
     * {@code count} units of {@code template} for {@code faction} at plot (dx, dz): a command, so the units exist from
     * the next tick, and an order to them waits for a later step.
     */
    default void units(TemplateId template, int faction, double dx, double dz, int count, double spread) {
        admin(new SpawnUnits(template, new FactionSlot(faction), at(dx, dz), count, spread));
    }

    /** A finished building of {@code template} for {@code faction}, its middle at plot (dx, dz). */
    default void building(TemplateId template, int faction, double dx, double dz) {
        int[] corner = corner(template, dx, dz);
        admin(new SpawnBuilding(template, new FactionSlot(faction), corner[0], corner[1], 0));
    }

    /** Faction {@code faction}'s live units of {@code template}, by handle. */
    default int[] handles(int faction, TemplateId template) {
        MatchQueries q = q();
        List<Integer> out = new ArrayList<>();
        for (int h : q.unitHandlesOf(new FactionSlot(faction))) {
            UnitView u = q.unit(h);
            if (u != null && q.isAlive(h) && u.template().equals(template)) {
                out.add(h);
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Faction {@code faction}'s buildings of {@code template}. */
    default List<BuildingView> buildings(int faction, TemplateId template) {
        List<BuildingView> out = new ArrayList<>();
        for (BuildingView b : q().buildings()) {
            if (b.faction() == faction && b.template().equals(template)) {
                out.add(b);
            }
        }
        return out;
    }

    /** The live node of {@code resource} nearest plot (dx, dz) that has a block to gather, or null. */
    default @Nullable NodeView node(String resource, double dx, double dz) {
        NodeView best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (NodeView n : q().nodes()) {
            if (!n.resource().equals(resource) || n.amount() <= 0 || !n.hasBlock()) {
                continue;
            }
            double d = Math.hypot(n.anchorX() - x(dx), n.anchorZ() - z(dz));
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    /** How much of {@code resource} faction {@code faction} has gathered so far, whole units. */
    default long gathered(int faction, String resource) {
        MatchQueries q = q();
        List<String> ids = new ArrayList<>();
        q.config().rules().economy().resources().forEach(r -> ids.add(r.id()));
        int k = ids.indexOf(resource);
        return k < 0 ? 0 : q.treasury(new FactionSlot(faction)).gathered().get(k) / 1000;
    }

    /** How many of {@code units} stand alive within {@code radius} of plot (dx, dz). */
    default int near(int[] units, double dx, double dz, double radius) {
        MatchQueries q = q();
        int n = 0;
        for (int h : units) {
            UnitView u = q.unit(h);
            if (u != null && Math.hypot(u.x() - x(dx), u.z() - z(dz)) <= radius) {
                n++;
            }
        }
        return n;
    }

    /** Sends {@code units} to gather node {@code n}, as a player's right click on its block. */
    default void gather(int[] units, NodeView n) {
        admin(Gather.replace(UnitSet.of(units), n.topX(), n.topY(), n.topZ()));
    }

    /** The nearest live node of {@code resource} to plot (dx, dz), or a failure of the chapter. */
    default NodeView needNode(String resource, double dx, double dz) {
        NodeView n = node(resource, dx, dz);
        if (n == null) {
            throw new IllegalStateException("no " + resource + " node near " + dx + ", " + dz);
        }
        return n;
    }

    /** The match at a glance, for a failed chapter's log: units by state, nodes near the middle, the treasuries. */
    default String describe() {
        MatchQueries q = queries();
        if (q == null) {
            return "no match stepping";
        }
        StringBuilder out = new StringBuilder("tick ").append(q.tick());
        for (int f = 0; f < 3; f++) {
            java.util.Map<String, Integer> states = new java.util.TreeMap<>();
            for (int h : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(h);
                if (u != null) {
                    states.merge(
                            u.template().value().replace("strategos:unit/", "") + " " + u.state(), 1, Integer::sum);
                }
            }
            out.append("; faction ").append(f).append(' ').append(states);
            if (f < 2) {
                out.append(" gathered ").append(q.treasury(new FactionSlot(f)).gathered());
            }
        }
        out.append("; first orders");
        for (int f = 0; f < 2; f++) {
            int[] own = q.unitHandlesOf(new FactionSlot(f));
            if (own.length > 0) {
                out.append(' ').append(q.orders(own[0]));
            }
        }
        out.append("; floor ")
                .append(floor())
                .append(" minY ")
                .append(q.config().bounds().minY());
        out.append("; buildings");
        for (BuildingView b : q.buildings()) {
            out.append(' ')
                    .append(b.faction())
                    .append(':')
                    .append(b.template().value().replace("strategos:building/", ""))
                    .append('/')
                    .append(b.state());
        }
        out.append("; nodes");
        for (NodeView n : q.nodes()) {
            if (Math.hypot(n.anchorX() - x(0), n.anchorZ() - z(0)) <= 32) {
                out.append(String.format(
                        java.util.Locale.ROOT,
                        " %s@%.0f,%.0f=%d free %d/%d top %d,%d,%d",
                        n.resource(),
                        n.anchorX() - x(0),
                        n.anchorZ() - z(0),
                        n.amount() / 1000,
                        n.freeSlots(),
                        n.slots(),
                        n.topX(),
                        n.topY(),
                        n.topZ()));
            }
        }
        return out.toString();
    }
}
