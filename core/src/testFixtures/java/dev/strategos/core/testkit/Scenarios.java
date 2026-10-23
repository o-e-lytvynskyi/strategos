package dev.strategos.core.testkit;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.UnitView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The shared scenarios. Each runs in L1 through {@link SimHarness#run} and as a GameTest (M0 spec 7.4); every call
 * returns a fresh instance, because a scenario remembers its own run. They act on the match's state, never on how often
 * {@link Scenario#onTick} is called. The scenarios themselves live by area in {@link OrderScenarios}, {@link
 * MovementScenarios} and {@link CrowdScenarios}; this class makes them and holds what they share.
 */
public final class Scenarios {
    static final FactionSlot FACTION_0 = new FactionSlot(0);
    static final FactionSlot FACTION_1 = new FactionSlot(1);

    private Scenarios() {}

    /** The functional scenarios, one GameTest each: walking_skeleton, out_of_bounds, stop_mid_walk. */
    public static List<Scenario> all() {
        return List.of(walkingSkeleton(Seeds.first("skeleton-20")), outOfBounds(), stopMidWalk());
    }

    /**
     * ORD-01: one unit on 32 x 32 at (4.5 + seed mod 5, 8.5 + 2 (seed mod 7)) (a floor modulus, so any seed stays
     * inside), spawned in setup; once it exists, at tick 1, an ADMIN Move 20 blocks along +X. The Move applies at tick
     * 2 and the unit walks 80 steps of 0.25: IDLE exactly on the target at tick 81, 80 ticks after the order and within
     * ORD-01.1's bound distance / speed + 1 = 81 ticks; the trace is exactly IDLE->WALKING, WALKING->IDLE.
     */
    public static Scenario walkingSkeleton(long seed) {
        return new OrderScenarios.WalkingSkeleton(seed);
    }

    /** ORD-01.2: an ADMIN Move to (40, 16) on 32 x 32 is REJECTED OUT_OF_BOUNDS, and the unit stays put for 40 ticks. */
    public static Scenario outOfBounds() {
        return new OrderScenarios.OutOfBounds();
    }

    /** A unit walking 24 blocks along +X gets a Stop that applies at tick 30: IDLE at once, then frozen for 20 ticks. */
    public static Scenario stopMidWalk() {
        return new OrderScenarios.StopMidWalk();
    }

    /**
     * Load and isolation runs: {@code units} units of faction 0 spawned in setup in the west quarter; every tick, the
     * idle units of each half get a Move to the far end, so the crowd shuttles east and west for {@code ticks} ticks.
     */
    public static Scenario patrol(long seed, int units, int sizeX, int sizeZ, int ticks) {
        return new MovementScenarios.Patrol(seed, units, sizeX, sizeZ, ticks);
    }

    /**
     * S6's determinism canary: 64 x 64, two factions of 30 units; every 10 ticks each faction's player sends what
     * {@link RandomCommands} draws; 2,000 ticks.
     */
    public static Scenario canary(long seed) {
        return new MovementScenarios.Canary(seed);
    }

    /**
     * PATH-03's golden journal (spec §5.10): 64 x 48 of terrain, a ridge of one-block steps at x 20..26, then a river at
     * x 36..41, two blocks deep but for a ford at z 30..35. Setup publishes the pages and spawns four units in the west;
     * once they exist they are ordered east, across the ridge and through the ford; done when all four stand on their
     * targets. L1 only: a GameTest takes its terrain from its world.
     */
    public static Scenario crossingSynthetic() {
        return new MovementScenarios.CrossingSynthetic();
    }

    /**
     * PATH-06's golden journal and its acceptance 1 and 2 in L1 (spec §5.10, §5.11): 64 x 32 of flat terrain with a
     * wall two blocks thick at x 31..32, three high, but for a gap six blocks wide at z 13..18. Fifty units of faction
     * 0 stand in ten columns and five rows 1.5 blocks apart from (4.5, 12.5), fifty of faction 1 mirrored east of the
     * wall; once all exist, each group is ordered {@value CrowdScenarios.HeadOnGap#SHIFT} blocks across the wall, keeping its
     * formation, so the groups meet head-on in the gap, row against row on the same lines, and the first to arrive
     * take the farthest places. The spacing leaves room to walk between units that have arrived: one block, with radii
     * of 0.35, would not. Done when every unit is idle within 2.5 blocks of its target: it arrives on it, or within
     * 1.5 blocks while another stands on it, and may step aside since by less than the latch's block. No two units may stay closer
     * than {@value CrowdScenarios.HeadOnGap#CLOSE} block for more than {@value CrowdScenarios.HeadOnGap#CLOSE_TICKS} ticks in a row. L1 only.
     */
    public static Scenario headOnGap() {
        return new CrowdScenarios.HeadOnGap();
    }

    /**
     * ORD-03's golden journal and its acceptance 2 in L1 (spec §5.9): on 64 x 48 of flat terrain, fifty units spawned
     * in a disc of radius 6 round (10, 24) get one Move to (46.5, 24.5) and take places round it. Done {@value
     * GroupOf50Slots#SETTLE} ticks after all stand idle, since standing units part slowly; each came to rest within
     * {@value CrowdScenarios.GroupOf50Slots#NEAR} blocks of its place, the one it holds when it does (members trade places in a crowd;
     * it may step aside for others after), and no two stand nearer than {@value CrowdScenarios.GroupOf50Slots#APART} at the end. L1
     * only.
     */
    public static Scenario groupOf50Slots() {
        return groupOf50Slots(50);
    }

    /** As above with another seed, which scatters the spawn disc differently. */
    public static Scenario groupOf50Slots(long seed) {
        return new CrowdScenarios.GroupOf50Slots(seed);
    }

    static void check(boolean ok, String failure) {
        if (!ok) {
            throw new ScenarioFailure(failure);
        }
    }

    static UnitView alive(ScenarioContext ctx, int unit) {
        UnitView view = unit == Handle.NONE ? null : ctx.queries().unit(unit);
        if (view == null) {
            throw new ScenarioFailure("the unit " + Handle.toString(unit) + " does not exist");
        }
        return view;
    }

    /** The first unit of faction 0, or NONE before it exists. */
    static int firstUnit(ScenarioContext ctx) {
        int[] units = ctx.queries().unitHandlesOf(FACTION_0);
        return units.length == 0 ? Handle.NONE : units[0];
    }

    static List<String> trace(ScenarioContext ctx, int unit) {
        return ctx.stateChanges().stream()
                .filter(c -> c.handle() == unit)
                .map(c -> c.from() + "->" + c.to())
                .toList();
    }

    static @Nullable CommandResult resultOf(ScenarioContext ctx, PlayerSlot issuer, int clientSeq) {
        for (CommandResult r : ctx.results()) {
            if (r.issuer().equals(issuer) && r.clientSeq() == clientSeq) {
                return r;
            }
        }
        return null;
    }
}
