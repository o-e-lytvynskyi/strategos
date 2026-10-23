package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.move.MotionSystem;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.nav.PathStatus;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * PATH-05.4: one tick of {@code MotionSystem} with 600 units walking on terrain, each along its path across a 128²
 * floor with steps (a {@code @HotPath} kernel: no allocation). Every 200 ticks the units are put back at their starts
 * on the same paths, from arrays, so they never arrive. Per unit: the score divided by 600 (budget: 5 µs).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MotionBench {
    private static final int UNITS = 600;
    private static final int LOOP = 200;

    private MatchState state;
    private MotionSystem motion;
    private int[] index;
    private double[] x;
    private double[] y;
    private double[] z;
    private PathStore.Waypoints[] paths;
    private int ticks;

    @Setup(Level.Trial)
    public void setUp() {
        VoxelWorld w = VoxelFixtures.flat(128, 128, 16, 1);
        for (int x = 40; x < 128; x += 24) {
            w.fill(x, 1, 0, x + 11, 1, 127, VoxelFixtures.SOLID); // terraces one block up, crossed by every walk
        }
        MatchConfig config = new MatchConfig(new MatchId(1), 42, new MatchBounds(128, 128, 0, 16, 1.0), 2, rules());
        MatchCore core = MatchCore.create(config, TemplateSet.builtin(), CorePorts.of(System::nanoTime));
        List<InputEvent> first = new ArrayList<>();
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        for (int pz = 0; pz < 8; pz++) {
            for (int px = 0; px < 8; px++) {
                PageCoord page = new PageCoord(px, pz);
                first.add(TerrainPage.build(page, w.slice(page), builder));
            }
        }
        first.add(command(1, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(10, 64), 300, 8)));
        first.add(command(2, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(20, 64), 300, 8)));
        core.step(new TickInputs(1, first));
        List<InputEvent> orders = new ArrayList<>();
        int seq = 10;
        for (int handle : core.queries().unitHandles()) {
            UnitView u = core.queries().unit(handle);
            orders.add(command(seq++, Move.replace(UnitSet.of(handle), new Vec2d(120.5, u.z()))));
        }
        core.step(new TickInputs(2, orders));
        for (long tick = 3; tick <= 6; tick++) {
            core.step(TickInputs.empty(tick)); // the requests are delivered
        }
        state = ((MatchCoreImpl) core).state();
        motion = new MotionSystem(state);
        index = new int[UNITS];
        x = new double[UNITS];
        y = new double[UNITS];
        z = new double[UNITS];
        paths = new PathStore.Waypoints[UNITS];
        int n = 0;
        for (int i = 0; i < state.entities().highWater(); i++) {
            if (state.entities().aliveAt(i)) {
                if (state.ai().state(i) != UnitState.WALKING
                        || state.paths().path(i).count() == 0) {
                    throw new IllegalStateException("unit " + i + " walks no path");
                }
                index[n] = i;
                x[n] = state.motion().x(i);
                y[n] = state.motion().y(i);
                z[n] = state.motion().z(i);
                paths[n] = state.paths().path(i);
                n++;
            }
        }
        if (n != UNITS) {
            throw new IllegalStateException(n + " units, not " + UNITS);
        }
    }

    @Benchmark
    public double step600() {
        if (++ticks % LOOP == 0) {
            for (int k = 0; k < UNITS; k++) {
                int i = index[k];
                state.motion().set(i, x[k], y[k], z[k], state.motion().yaw(i));
                state.paths().follow(i, paths[k], PathStatus.OK);
            }
        }
        state.frame().reset();
        motion.run(state);
        return state.motion().x(index[0]);
    }

    private static CommandEnvelope command(int seq, Command command) {
        return new CommandEnvelope(PlayerSlot.ADMIN, seq, 0, command);
    }

    /**
     * M1's rules with no admission window: every unit's path is delivered in the few ticks the set-up steps, as before
     * spike S7 set the window (M1 T22), which spreads 600 requests over about eight ticks.
     */
    private static MatchRules rules() {
        MatchRules m = new MatchRules(UNITS, 4096);
        return new MatchRules(
                m.commandsPerTick(),
                m.unitCap(),
                m.delivery(),
                m.pathK(),
                m.pageK(),
                Integer.MAX_VALUE,
                m.maxOutstanding(),
                m.busyRetry(),
                m.searchCap(),
                m.joinCap(),
                m.shortMove(),
                m.groupMin());
    }
}
