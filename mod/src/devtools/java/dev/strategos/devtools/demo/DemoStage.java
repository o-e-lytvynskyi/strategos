package dev.strategos.devtools.demo;

import com.mojang.logging.LogUtils;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.Objects;
import java.util.function.Consumer;
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
public final class DemoStage implements Stage {
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
    @Override
    public BlockPos centre() {
        return centre;
    }

    /** The ground's height, the first free block above the floor. */
    @Override
    public int floor() {
        return centre.getY();
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    /** A block at plot (dx, dy above the floor, dz); before {@link #match}, so the match's pages hold it. */
    @Override
    public void block(int dx, int dy, int dz, BlockState state) {
        level.setBlockAndUpdate(centre.offset(dx, dy, dz), state);
    }

    /** A box of {@code state} from (x0, y0, z0) to (x1, y1, z1), plot coordinates, ends included. */
    @Override
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
    @Override
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
    @Override
    public void bush(int dx, int dz) {
        block(dx, 0, dz, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
    }

    /**
     * The plot's match, two factions on a square of {@link #SIZE} round its middle, made now, with the watching player
     * seated in slot 0 and in command of it.
     */
    @Override
    public void match() {
        match(SIZE);
    }

    /** As {@link #match()}, on a square of side {@code size}, {@link #PLOT} at most; the map is revealed. */
    @Override
    public void match(int size) {
        make(size, rules(false));
    }

    /** As {@link #match()} under Fog: only the chapter about fog opts in. */
    @Override
    public void fogMatch() {
        make(SIZE, rules(true));
    }

    @Override
    public void listen(Consumer<TickOutputs> listener) {
        rt().addListener(listener);
    }

    /** The play rules of a chapter's match: the map revealed, so the scene shows everything, unless {@code fog}. */
    static PlayRules rules(boolean fog) {
        return new PlayRules(PlayRules.Victory.NONE, !fog, false, false);
    }

    private void make(int size, PlayRules play) {
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, centre, size, size),
                        new MatchOptions(
                                7L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor(), null, play));
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
    }

    /** The match, once made. */
    @Override
    public MatchRuntime rt() {
        return Objects.requireNonNull(match, "the chapter made no match yet");
    }

    /** Its queries, or null until the match steps. */
    @Override
    public @Nullable MatchQueries queries() {
        MatchCore core = match == null ? null : match.core();
        return core == null ? null : core.queries();
    }

    /** As {@link #queries}, for a step that runs once the match does. */
    @Override
    public MatchQueries q() {
        return Objects.requireNonNull(queries(), "the match does not step yet");
    }

    /** Plot X {@code dx} as the match's X. */
    @Override
    public double x(double dx) {
        return rt().space().localX(centre.getX() + dx);
    }

    /** Plot Z {@code dz} as the match's Z. */
    @Override
    public double z(double dz) {
        return rt().space().localZ(centre.getZ() + dz);
    }

    /** A command from the admin: spawns, grants, the world's hand. */
    @Override
    public void admin(Command command) {
        rt().submit(PlayerSlot.ADMIN, command);
    }

    /** A command from faction {@code faction}'s seat, as its player would send it. */
    @Override
    public void order(int faction, Command command) {
        rt().submit(new PlayerSlot(faction), command);
    }

    /** The match corner of {@code template}'s footprint with its middle at plot (dx, dz). */
    @Override
    public int[] corner(TemplateId template, double dx, double dz) {
        TemplateSet templates = StrategosServer.get().templates().set();
        UnitTemplate t = Objects.requireNonNull(templates.byId(template), template.value());
        Blueprint bp = Objects.requireNonNull(templates.blueprint(t.netIndex()), template.value());
        return new int[] {(int) Math.floor(x(dx)) - bp.sizeX() / 2, (int) Math.floor(z(dz)) - bp.sizeZ() / 2};
    }

    /** {@code units[from..to)}. */
    public static int[] part(int[] units, int from, int to) {
        return java.util.Arrays.copyOfRange(units, Math.min(from, units.length), Math.min(to, units.length));
    }

    /** A server command, its output kept out of the chat. */
    @Override
    public void command(String command) {
        server.getCommands()
                .performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    @Override
    public MinecraftServer server() {
        return server;
    }

    /** The watching player. */
    @Override
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
