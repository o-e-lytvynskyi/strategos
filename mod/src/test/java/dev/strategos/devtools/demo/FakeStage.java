package dev.strategos.devtools.demo;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * L1's {@link Stage}: a chapter's script on the core's {@link SimHarness}, no Minecraft. The plot is flat ground of
 * the match's size with its middle at the match's, blocks are counted and dropped, the match is the harness, and time
 * is one harness step after every step call of the script, as the director gives one server tick. It does not cover
 * what needs a level, a server or a player ({@code rt}, {@code level}, {@code server}, {@code player}): they throw
 * {@code UnsupportedOperationException("not in the fake")}; commands to the server are recorded, never run.
 */
final class FakeStage implements Stage {
    /** The ground's height in the harness: the first free block above it. */
    static final int FLOOR = 1;

    private final TemplateSet templates = TemplateSet.builtin();
    private final List<Consumer<TickOutputs>> listeners = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private int blocks;
    private int size = DemoStage.SIZE;
    private @Nullable SimHarness harness;
    private boolean stepped;

    /** Runs {@code chapter}'s script to its end, or until a step throws. */
    static FakeStage run(DemoChapter chapter) {
        FakeStage stage = new FakeStage();
        DemoScript script = new DemoScript();
        chapter.script(script);
        for (DemoScript.Step step : script.steps) {
            int tick = 0;
            boolean done;
            do {
                try {
                    done = step.tick(stage, tick++);
                } catch (RuntimeException e) {
                    throw new IllegalStateException(e.getMessage() + "; " + stage.report(), e);
                }
                stage.advance();
            } while (!done);
        }
        return stage;
    }

    /** One tick of the match, if there is one; its outputs go to the listeners. */
    void advance() {
        if (harness != null) {
            TickOutputs out = harness.step();
            stepped = true;
            listeners.forEach(l -> l.accept(out));
        }
    }

    /** What a failed script's reader needs: the stage at a glance and the match's refusals. */
    private String report() {
        SimHarness h = harness;
        if (h == null) {
            return "no match";
        }
        List<String> refused = new ArrayList<>();
        for (CommandResult r : h.events(CommandResult.class)) {
            if (r.rejected() > 0) {
                refused.add(r.kind() + " " + r.firstReason());
            }
        }
        return (stepped ? describe() : "not stepped") + "; refused " + refused;
    }

    /** The harness the match runs on. */
    SimHarness harness() {
        return Objects.requireNonNull(harness, "no match was made");
    }

    /** The server commands the script gave (titles, sprints), in order. */
    List<String> commands() {
        return commands;
    }

    /** How many blocks the script placed, counting every cell of a fill. */
    int blocks() {
        return blocks;
    }

    @Override
    public BlockPos centre() {
        return new BlockPos(0, FLOOR, 0);
    }

    @Override
    public int floor() {
        return FLOOR;
    }

    @Override
    public void block(int dx, int dy, int dz, BlockState state) {
        blocks++;
    }

    @Override
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        blocks += (Math.abs(x1 - x0) + 1) * (Math.abs(y1 - y0) + 1) * (Math.abs(z1 - z0) + 1);
    }

    @Override
    public void oak(int dx, int dz, int trunk) {
        blocks += trunk + 9;
    }

    @Override
    public void bush(int dx, int dz) {
        blocks++;
    }

    @Override
    public void match() {
        match(DemoStage.SIZE);
    }

    @Override
    public void match(int size) {
        make(size, false);
    }

    @Override
    public void fogMatch() {
        make(DemoStage.SIZE, true);
    }

    private void make(int side, boolean fog) {
        if (harness != null) {
            throw new IllegalStateException("the chapter made its match twice");
        }
        size = side;
        SimHarness h = SimHarness.builder()
                .size(side, side)
                .yBand(0, 31, FLOOR)
                .factions(2)
                .templates(templates)
                .rules(MatchRules.DEFAULTS.withPlay(DemoStage.rules(fog)))
                .build();
        h.publishAll(VoxelFixtures.flat(side, side, 32, FLOOR));
        harness = h;
    }

    @Override
    public void listen(Consumer<TickOutputs> listener) {
        listeners.add(listener);
    }

    @Override
    public @Nullable MatchQueries queries() {
        return harness == null || !stepped ? null : harness.queries();
    }

    @Override
    public MatchQueries q() {
        return Objects.requireNonNull(queries(), "the match does not step yet");
    }

    @Override
    public double x(double dx) {
        return size / 2.0 + dx;
    }

    @Override
    public double z(double dz) {
        return size / 2.0 + dz;
    }

    @Override
    public void admin(Command command) {
        harness().submit(PlayerSlot.ADMIN, command);
    }

    @Override
    public void order(int faction, Command command) {
        harness().submit(new PlayerSlot(faction), command);
    }

    @Override
    public int[] corner(TemplateId template, double dx, double dz) {
        UnitTemplate t = Objects.requireNonNull(templates.byId(template), template.value());
        Blueprint bp = Objects.requireNonNull(templates.blueprint(t.netIndex()), template.value());
        return new int[] {(int) Math.floor(x(dx)) - bp.sizeX() / 2, (int) Math.floor(z(dz)) - bp.sizeZ() / 2};
    }

    @Override
    public void command(String command) {
        commands.add(command);
    }

    @Override
    public MatchRuntime rt() {
        throw new UnsupportedOperationException("not in the fake");
    }

    @Override
    public ServerLevel level() {
        throw new UnsupportedOperationException("not in the fake");
    }

    @Override
    public MinecraftServer server() {
        throw new UnsupportedOperationException("not in the fake");
    }

    @Override
    public ServerPlayer player() {
        throw new UnsupportedOperationException("not in the fake");
    }

    /** The plot's point, for a test's own look at the state. */
    Vec2d point(double dx, double dz) {
        return at(dx, dz);
    }
}
