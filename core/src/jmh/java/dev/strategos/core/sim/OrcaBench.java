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
 * PATH-06.5: one tick of {@code MotionSystem} and the grid's rebuild while two formations of 300 walk through each
 * other on flat terrain, 20 columns by 15 rows 1.2 blocks apart, row against row: every unit plans around its
 * neighbours (ORCA, or separation where more than six crowd it), so this is avoidance at its busiest (a {@code @HotPath}
 * kernel: no allocation). Every {@value #LOOP} ticks the units are put back at their starts, standing, on the same
 * paths. Per unit: the score divided by 600 (budget: 5 µs).
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class OrcaBench {
    private static final int UNITS = 600;
    private static final int COLUMNS = 20;
    private static final int ROWS = 15;
    private static final double SPACING = 1.2;
    private static final int LOOP = 120;

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
        MatchConfig config = new MatchConfig(new MatchId(1), 42, new MatchBounds(160, 64, 0, 16, 1.0), 2, rules());
        MatchCore core = MatchCore.create(config, TemplateSet.builtin(), CorePorts.of(System::nanoTime));
        List<InputEvent> first = new ArrayList<>();
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        var w = VoxelFixtures.flat(160, 64, 16, 1);
        for (int pz = 0; pz < 4; pz++) {
            for (int px = 0; px < 10; px++) {
                PageCoord page = new PageCoord(px, pz);
                first.add(TerrainPage.build(page, w.slice(page), builder));
            }
        }
        int seq = 1;
        for (int c = 0; c < COLUMNS; c++) {
            for (int r = 0; r < ROWS; r++) {
                double gx = 50 + SPACING * c;
                double gz = 23 + SPACING * r;
                first.add(command(
                        seq++, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(gx, gz), 1, 0)));
                first.add(command(
                        seq++,
                        new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(gx + 28, gz), 1, 0)));
            }
        }
        core.step(new TickInputs(1, first));
        List<InputEvent> orders = new ArrayList<>();
        for (int handle : core.queries().unitHandles()) {
            UnitView u = core.queries().unit(handle);
            double target = u.x() < 80 ? 150.5 : 9.5;
            orders.add(command(seq++, Move.replace(UnitSet.of(handle), new Vec2d(target, u.z()))));
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
                state.motion().setVelocity(i, 0, 0);
                state.paths().follow(i, paths[k], PathStatus.OK);
                MotionStore m = state.motion(); // a fresh walk: the stuck ladder starts again from its first rung
                m.setProgress(i, m.aimX(i), m.aimZ(i), Double.POSITIVE_INFINITY, 0);
                m.setGhostTicks(i, 0);
                m.setDetour(i, 0, 0, 0);
                m.setPushed(i, 0);
                m.setLatch(i, false, 0, 0);
            }
            state.spatial().rebuild(state);
        }
        state.frame().reset();
        motion.run(state);
        state.spatial().rebuild(state);
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
