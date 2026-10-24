package dev.strategos.devtools.demo;

import com.mojang.logging.LogUtils;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A chapter's plot of the demo world and its match: a square of flat ground of its own, so a chapter runs alone or
 * after any other. A chapter places blocks first ({@link #block}, {@link #oak}, {@link #fill}), then makes its match
 * ({@link #match}), which seats the watching player in slot 0; after that it speaks in the plot's own coordinates,
 * blocks from the plot's middle, which {@link #x} and {@link #z} turn into the match's. Server thread only.
 */
public final class DemoStage {
    /** A plot's side, in blocks: a match's square at most. */
    public static final int PLOT = 192;

    private static final Logger LOG = LogUtils.getLogger();
    /** The usual match's side. */
    public static final int SIZE = 96;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final ServerPlayer player;
    private final BlockPos centre;
    private @Nullable MatchRuntime match;

    DemoStage(MinecraftServer server, ServerPlayer player, int plot) {
        this.server = server;
        this.player = player;
        this.level = player.serverLevel();
        int x = plot * (PLOT + 64);
        level.getChunk(x >> 4, 0); // loaded first: an unloaded chunk's heightmap says the world's bottom
        this.centre = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, 0), 0);
    }

    /** The plot's middle on the ground: (0, 0) of its coordinates. */
    public BlockPos centre() {
        return centre;
    }

    /** The ground's height, the first free block above the floor. */
    public int floor() {
        return centre.getY();
    }

    public ServerLevel level() {
        return level;
    }

    /** A block at plot (dx, dy above the floor, dz); before {@link #match}, so the match's pages hold it. */
    public void block(int dx, int dy, int dz, BlockState state) {
        level.setBlockAndUpdate(centre.offset(dx, dy, dz), state);
    }

    /** A box of {@code state} from (x0, y0, z0) to (x1, y1, z1), plot coordinates, ends included. */
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    block(x, y, z, state);
                }
            }
        }
    }

    /** An oak at plot (dx, dz): a trunk of {@code trunk} logs and a small crown. */
    public void oak(int dx, int dz, int trunk) {
        for (int y = 0; y < trunk; y++) {
            block(dx, y, dz, Blocks.OAK_LOG.defaultBlockState());
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                block(
                        dx + x,
                        trunk,
                        dz + z,
                        Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            }
        }
    }

    /** A ripe berry bush at plot (dx, dz). */
    public void bush(int dx, int dz) {
        block(dx, 0, dz, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
    }

    /**
     * The plot's match, two factions on a square of {@link #SIZE} round its middle, made now, with the watching player
     * seated in slot 0 and in command of it.
     */
    public MatchRuntime match() {
        return match(SIZE);
    }

    /** As {@link #match()}, on a square of side {@code size}, {@link #PLOT} at most. */
    public MatchRuntime match(int size) {
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, centre, size, size),
                        new MatchOptions(7L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor()));
        match = rt;
        rt.addListener(out -> out.events().forEach(e -> {
            if (e instanceof CommandResult r && r.rejected() > 0) { // a refusal is a chapter's bug, or the mod's
                LOG.info(
                        "DEMO refused {} {}: {} of {}, {} {}",
                        r.issuer(),
                        r.kind(),
                        r.rejected(),
                        r.accepted() + r.rejected(),
                        r.firstReason(),
                        r.missing());
            }
        }));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        return rt;
    }

    /** The match, once made. */
    public MatchRuntime rt() {
        return Objects.requireNonNull(match, "the chapter made no match yet");
    }

    /** Its queries, or null until the match steps. */
    public @Nullable MatchQueries queries() {
        MatchCore core = match == null ? null : match.core();
        return core == null ? null : core.queries();
    }

    /** As {@link #queries}, for a step that runs once the match does. */
    public MatchQueries q() {
        return Objects.requireNonNull(queries(), "the match does not step yet");
    }

    /** Plot X {@code dx} as the match's X. */
    public double x(double dx) {
        return rt().space().localX(centre.getX() + dx);
    }

    /** Plot Z {@code dz} as the match's Z. */
    public double z(double dz) {
        return rt().space().localZ(centre.getZ() + dz);
    }

    /** Plot (dx, dz) as the match's point. */
    public Vec2d at(double dx, double dz) {
        return new Vec2d(x(dx), z(dz));
    }

    /** A command from the admin: spawns, grants, the world's hand. */
    public void admin(Command command) {
        rt().submit(PlayerSlot.ADMIN, command);
    }

    /** A command from faction {@code faction}'s seat, as its player would send it. */
    public void order(int faction, Command command) {
        rt().submit(new PlayerSlot(faction), command);
    }

    /** {@code count} units of {@code template} for {@code faction} at plot (dx, dz). */
    public void units(TemplateId template, int faction, double dx, double dz, int count, double spread) {
        admin(new SpawnUnits(template, new FactionSlot(faction), at(dx, dz), count, spread));
    }

    /** A finished building of {@code template} for {@code faction}, its middle at plot (dx, dz). */
    public void building(TemplateId template, int faction, double dx, double dz) {
        int[] corner = corner(template, dx, dz);
        admin(new SpawnBuilding(template, new FactionSlot(faction), corner[0], corner[1], 0));
    }

    /** The match corner of {@code template}'s footprint with its middle at plot (dx, dz). */
    public int[] corner(TemplateId template, double dx, double dz) {
        TemplateSet templates = StrategosServer.get().templates().set();
        UnitTemplate t = Objects.requireNonNull(templates.byId(template), template.value());
        Blueprint bp = Objects.requireNonNull(templates.blueprint(t.netIndex()), template.value());
        return new int[] {(int) Math.floor(x(dx)) - bp.sizeX() / 2, (int) Math.floor(z(dz)) - bp.sizeZ() / 2};
    }

    /** Faction {@code faction}'s live units of {@code template}, by handle. */
    public int[] handles(int faction, TemplateId template) {
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
    public List<BuildingView> buildings(int faction, TemplateId template) {
        List<BuildingView> out = new ArrayList<>();
        for (BuildingView b : q().buildings()) {
            if (b.faction() == faction && b.template().equals(template)) {
                out.add(b);
            }
        }
        return out;
    }

    /** The live node of {@code resource} nearest plot (dx, dz) that has a block to gather, or null. */
    public @Nullable NodeView node(String resource, double dx, double dz) {
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
    public long gathered(int faction, String resource) {
        MatchQueries q = q();
        List<String> ids = new ArrayList<>();
        q.config().rules().economy().resources().forEach(r -> ids.add(r.id()));
        int k = ids.indexOf(resource);
        return k < 0 ? 0 : q.treasury(new FactionSlot(faction)).gathered().get(k) / 1000;
    }

    /** How many of {@code units} stand alive within {@code radius} of plot (dx, dz). */
    public int near(int[] units, double dx, double dz, double radius) {
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
    public void gather(int[] units, NodeView n) {
        admin(Gather.replace(UnitSet.of(units), n.topX(), n.topY(), n.topZ()));
    }

    /** The nearest live node of {@code resource} to plot (dx, dz), or a failure of the chapter. */
    public NodeView needNode(String resource, double dx, double dz) {
        NodeView n = node(resource, dx, dz);
        if (n == null) {
            throw new IllegalStateException("no " + resource + " node near " + dx + ", " + dz);
        }
        return n;
    }

    /** {@code units[from..to)}. */
    public static int[] part(int[] units, int from, int to) {
        return java.util.Arrays.copyOfRange(units, Math.min(from, units.length), Math.min(to, units.length));
    }

    /** The match at a glance, for a failed chapter's log: units by state, nodes near the middle, the treasuries. */
    public String describe() {
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

    /** A server command, its output kept out of the chat. */
    public void command(String command) {
        server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    public MinecraftServer server() {
        return server;
    }

    /** The watching player. */
    public ServerPlayer player() {
        return player;
    }

    /** Ends the plot's match for good, if it made one. */
    void dispose() {
        MatchRuntime rt = match;
        if (rt != null) {
            StrategosServer.get().commanders().leave(server, player);
            StrategosServer.get().matches().dispose(rt.id());
            match = null;
        }
    }
}
