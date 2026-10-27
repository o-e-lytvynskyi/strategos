package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.combat.CombatSystem;
import dev.strategos.core.sim.combat.Damage;
import dev.strategos.core.sim.combat.ShotStore;
import dev.strategos.core.sim.combat.ShotSystem;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.unitai.UnitAi;
import dev.strategos.core.testkit.Seeds;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Active-shot hash and a 60-shot COMBAT volley in a field of 300 fighting infantry. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ShotBench {
    private static final int SHOTS = 300;

    @State(Scope.Thread)
    public static class HashState {
        MatchState state;

        @Setup(Level.Trial)
        public void setUp() {
            MatchCore core = match();
            step(
                    core,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(10.5, 10.5), 1, 0),
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(20.5, 10.5), 1, 0));
            int[] handles = core.queries().unitHandlesOf(new FactionSlot(0));
            if (handles.length != 2) throw new IllegalStateException("two debug bodies required");
            state = ((MatchCoreImpl) core).state();
            List<ShotStore.Shot> rows = new ArrayList<>(SHOTS);
            for (int i = 1; i <= SHOTS; i++) {
                Damage damage = new Damage(
                        handles[1],
                        handles[0],
                        0.5f,
                        1,
                        0.25f,
                        0,
                        List.of(new Damage.Bonus("Infantry", 1.5f), new Damage.Bonus("Cavalry", 0.75f)),
                        false);
                rows.add(new ShotStore.Shot(
                        i, handles[0], handles[1], state.tick() + 10, 20.5 + i * 0.01, 1.9, 20.5, damage));
            }
            state.shots().restore(new ShotStore.Snapshot(SHOTS + 1, rows));
        }
    }

    @State(Scope.Thread)
    public static class VolleyState {
        @Param({"preferred", "collateral", "miss"})
        public String mode;

        MatchState state;
        ShotStore.Snapshot due;
        ShotSystem shots;
        CombatSystem combat;
        int[] attackers;
        int[] defenders;

        @Setup(Level.Trial)
        public void setUp() {
            MatchCore core = match();
            step(
                    core,
                    new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(2.5, 2.5), 150, 0),
                    new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(3.5, 2.5), 150, 0));
            state = ((MatchCoreImpl) core).state();
            attackers = core.queries().unitHandlesOf(new FactionSlot(0));
            defenders = core.queries().unitHandlesOf(new FactionSlot(1));
            if (attackers.length != 150
                    || defenders.length != 150
                    || state.entities().aliveCount() != 300) {
                throw new IllegalStateException("300 infantry in two factions required");
            }
            for (int i = 0; i < 150; i++) {
                double x = 2.5 + 4 * (i % 15);
                double z = 2.5 + 5 * (i / 15);
                position(attackers[i], x, z, defenders[i], x + 1);
                position(defenders[i], x + 1, z, attackers[i], x);
            }
            state.spatial().rebuild(state);
            List<ShotStore.Shot> rows = new ArrayList<>(60);
            for (int i = 0; i < 60; i++) {
                double x = 2.5 + 4 * (i % 15);
                double z = 2.5 + 5 * (i / 15);
                int other = (i + 1) % 150;
                double landX = switch (mode) {
                    case "preferred" -> x + 1;
                    case "collateral" -> 3.5 + 4 * (other % 15);
                    case "miss" -> x + 0.5;
                    default -> throw new IllegalArgumentException(mode);
                };
                double landZ = mode.equals("collateral") ? 2.5 + 5 * (other / 15) : z;
                Damage damage = new Damage(defenders[i], attackers[i], 0, 1f / 256, 0, 0, List.of(), false);
                rows.add(
                        new ShotStore.Shot(i + 1, attackers[i], defenders[i], state.tick(), landX, 1.9, landZ, damage));
            }
            due = new ShotStore.Snapshot(61, rows);
            shots = new ShotSystem(state.shots());
            combat = new CombatSystem();
            reset();
            if (state.shots().count() != 60) throw new IllegalStateException("not 60 active shots");
            int expected = mode.equals("miss") ? 0 : 60;
            if (resolveHits() != expected || state.entities().aliveCount() != 300) {
                throw new IllegalStateException("volley fixture expected " + expected + " nonlethal hits");
            }
            reset();
            if (state.shots().count() != 60
                    || resolveHits() != expected
                    || state.entities().aliveCount() != 300) {
                throw new IllegalStateException("volley fixture did not repeat the same workload");
            }
            reset();
        }

        private void position(int handle, double x, double z, int opponent, double opponentX) {
            int index = Handle.index(handle);
            state.motion().set(index, x, 1, z, 0);
            state.ai().set(index, UnitAi.ATTACKING);
            state.orders()
                    .restore(index, opponentX, z, new OrderQueues.Saved(OrderKind.ATTACK, 0, 0, opponent, List.of()));
        }

        @Setup(Level.Invocation)
        public void reset() {
            state.takeDamage();
            state.outputs().clear();
            for (int handle : attackers) state.health().reset(Handle.index(handle));
            for (int handle : defenders) state.health().reset(Handle.index(handle));
            state.shots().restore(due);
        }

        private int resolve() {
            shots.run(state);
            combat.run(state);
            return state.outputs().size();
        }

        /** The fixture's workload: the hits, since every impact also reports a ShotLanded (T3c.1). */
        private int resolveHits() {
            resolve();
            int hurt = 0;
            for (int i = 0; i < state.outputs().size(); i++) {
                if (state.outputs().get(i) instanceof UnitHurt) hurt++;
            }
            return hurt;
        }
    }

    @Benchmark
    public long hashActive300(HashState fixture) {
        return StateHasher.hash(fixture.state);
    }

    @Benchmark
    public int volley60(VolleyState fixture) {
        return fixture.resolve();
    }

    private static MatchCore match() {
        return MatchCore.create(
                new MatchConfig(
                        new MatchId(1),
                        Seeds.first("canary"),
                        new MatchBounds(64, 64, 0, 32, 1),
                        2,
                        MatchRules.DEFAULTS),
                TemplateSet.builtin(),
                CorePorts.of(System::nanoTime));
    }

    private static void step(MatchCore core, Command... commands) {
        long tick = core.queries().tick() + 1;
        List<InputEvent> events = new ArrayList<>(commands.length);
        for (int i = 0; i < commands.length; i++) {
            events.add(new CommandEnvelope(PlayerSlot.ADMIN, (int) tick * 1000 + i, tick - 1, commands[i]));
        }
        core.step(new TickInputs(tick, events));
    }
}
