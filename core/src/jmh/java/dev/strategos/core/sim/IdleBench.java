package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** UNIT-05: steady-state core cost of 300 idle units on the same 48-square floor as the L6 comparison. */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class IdleBench {
    private MatchCore core;

    @Setup(Level.Trial)
    public void setUp() {
        core = MatchCore.create(
                new MatchConfig(new MatchId(1), 42, new MatchBounds(48, 48, 0, 16, 1), 2, MatchRules.DEFAULTS),
                TemplateSet.builtin(),
                CorePorts.of(System::nanoTime));
        var world = VoxelFixtures.flat(48, 48, 16, 1);
        PageBuilder builder = new PageBuilder(MobilityClass.INFANTRY);
        List<InputEvent> first = new ArrayList<>();
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                PageCoord page = new PageCoord(x, z);
                first.add(TerrainPage.build(page, world.slice(page), builder));
            }
        }
        first.add(new CommandEnvelope(
                PlayerSlot.ADMIN,
                1,
                0,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8.5, 8.5), 150, 6)));
        first.add(new CommandEnvelope(
                PlayerSlot.ADMIN,
                2,
                0,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(39.5, 39.5), 150, 6)));
        core.step(new TickInputs(1, first));
        for (int i = 0; i < 200; i++) {
            core.step(TickInputs.empty(core.queries().tick() + 1));
        }
        if (core.queries().unitCount() != 300) {
            throw new IllegalStateException("the idle benchmark must measure 300 units");
        }
    }

    @Benchmark
    public TickOutputs step300() {
        return core.step(TickInputs.empty(core.queries().tick() + 1));
    }
}
