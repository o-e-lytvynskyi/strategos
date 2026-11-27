package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.F1;
import static dev.strategos.core.sim.ability.AbilityFixtures.P0;
import static dev.strategos.core.sim.ability.AbilityFixtures.spearman;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static dev.strategos.core.sim.ability.AbilityFixtures.walker;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * UNIT-10.1: the core validates an ability at the tick boundary: the unit has it, the target is the kind it takes and
 * is there, the range holds at firing (the unit walks into it), the cooldown has run out and the faction can pay,
 * the cost paid once, at firing, through the treasury; every refusal has its reason. The sets are
 * {@link AbilityFixtures#ALL}: {@code strike} is a 3-block unit ability for 20 hack, 10 food and a 50-tick cooldown.
 */
class AbilityValidationTest {
    private static CommandResult use(SimHarness h, int unit, String ability, Target target) {
        return use(h, new PlayerSlot(0), unit, ability, target);
    }

    private static CommandResult use(SimHarness h, PlayerSlot issuer, int unit, String ability, Target target) {
        int before = h.results().size();
        h.submit(issuer, new UseAbility(UnitSet.of(unit), new AbilityId(ability), target, QueueMode.REPLACE));
        h.step();
        return h.results().get(before);
    }

    private static void food(SimHarness h, long units) {
        h.submit(PlayerSlot.ADMIN, new Grant(F0, "food", units));
        h.step();
    }

    private static long stock(SimHarness h) {
        return state(h).treasury().stock(0, 0);
    }

    /** The ticks on which blows of {@code attacker} landed, over {@code ticks} steps. */
    private static List<Long> blows(SimHarness h, int attacker, int ticks) {
        List<Long> at = new ArrayList<>();
        for (int k = 0; k < ticks; k++) {
            TickOutputs out = h.step();
            out.events().stream()
                    .filter(UnitHurt.class::isInstance)
                    .map(UnitHurt.class::cast)
                    .filter(e -> e.attacker() == attacker)
                    .forEach(e -> at.add(out.tick()));
        }
        return at;
    }

    private static void assertRefused(CommandResult r, @Nullable RejectReason reason) {
        assertThat(r.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(r.firstReason()).isEqualTo(reason);
    }

    @Test
    void aUnitWithoutTheAbilityAndAnAbilityNoUnitHasAreRefusedAsUnsupported() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int target = walker(h, F1, 12, 16);
        assertRefused(use(h, a, "nope", Target.unit(target)), RejectReason.UNSUPPORTED);
        assertRefused(use(h, walker(h, F0, 8, 16), "strike", Target.unit(target)), RejectReason.UNSUPPORTED);
    }

    @Test
    void theTargetMustBeTheKindTheAbilityTakesAndBeThere() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int enemy = walker(h, F1, 12, 16);
        assertRefused(use(h, a, "strike", Target.none()), RejectReason.INVALID_ARGUMENT);
        assertRefused(use(h, a, "strike", Target.point(12, 16)), RejectReason.INVALID_ARGUMENT);
        assertRefused(use(h, a, "mend", Target.unit(enemy)), RejectReason.INVALID_ARGUMENT);
        assertRefused(use(h, a, "blink", Target.point(-5, 16)), RejectReason.OUT_OF_BOUNDS);
        assertRefused(use(h, a, "strike", Target.unit(0x7FFFF)), RejectReason.UNKNOWN_TARGET);
        assertRefused(use(h, a, "strike", Target.unit(0)), RejectReason.UNKNOWN_TARGET);
        assertThat(use(h, a, "slam", Target.unit(enemy)).outcome())
                .as("an area takes a point or a unit at its centre")
                .isEqualTo(CommandOutcome.ACCEPTED);
        assertRefused(use(h, new PlayerSlot(1), a, "mend", Target.none()), RejectReason.NOT_OWNER);
    }

    @Test
    void anAllyIsNoTargetForDamageAndAnEnemyNoneForAHeal() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        int friend = spearman(h, F0, 11, 16);
        int enemy = walker(h, F1, 12, 16);
        assertRefused(use(h, a, "strike", Target.unit(friend)), RejectReason.INVALID_ARGUMENT);
        assertRefused(use(h, a, "heal_other", Target.unit(enemy)), RejectReason.INVALID_ARGUMENT);
        assertThat(use(h, a, "heal_other", Target.unit(friend)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void theRangeIsCheckedAtFiringAndTheUnitWalksIntoIt() {
        SimHarness h = AbilityFixtures.match();
        food(h, 100);
        int a = spearman(h, F0, 4, 16);
        int target = walker(h, F1, 16, 16); // 12 blocks off: far outside 3
        List<OrderFinished> finished = new ArrayList<>();
        state(h).bus().subscribe(OrderFinished.class, finished::add);
        assertThat(use(h, a, "strike", Target.unit(target)).outcome())
                .as("out of range is no refusal: the unit walks")
                .isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(blows(h, a, 1)).as("not on the first tick").isEmpty();
        assertThat(stock(h)).as("nothing is paid while it walks").isEqualTo(100_000);
        List<Long> at = blows(h, a, 200);
        assertThat(at).as("one blow, once in range").hasSize(1);
        int i = Handle.index(a);
        MatchState s = state(h);
        double gap = Math.hypot(s.motion().x(Handle.index(target)) - s.motion().x(i), 0) - 0.7;
        assertThat(gap).as("fired from within its 3 blocks").isLessThanOrEqualTo(3.0 + 1e-6);
        assertThat(finished).containsExactly(new OrderFinished(a, false));
        assertThat(s.orders().size(i)).isZero();
    }

    @Test
    void anAbilityInRangeFiresOnTheTickItIsOrdered() {
        SimHarness h = AbilityFixtures.match();
        food(h, 100);
        int a = spearman(h, F0, 10, 16);
        int target = walker(h, F1, 12, 16);
        h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("strike"), Target.unit(target)));
        TickOutputs out = h.step();
        assertThat(out.events())
                .filteredOn(UnitHurt.class::isInstance)
                .map(UnitHurt.class::cast)
                .extracting(UnitHurt::handle, UnitHurt::attacker)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(target, a));
    }

    @Test
    void theCooldownRefusesUntilItHasRunOut() {
        SimHarness h = AbilityFixtures.match();
        food(h, 100);
        int a = spearman(h, F0, 10, 16);
        int target = walker(h, F1, 12, 16);
        assertThat(use(h, a, "strike", Target.unit(target)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        long fired = h.tick();
        assertThat(state(h).play().abilities().readyAt(a, "strike")).isEqualTo(fired + 50);
        assertRefused(use(h, a, "strike", Target.unit(target)), RejectReason.ON_COOLDOWN);
        while (h.tick() < fired + 47) {
            h.step();
        }
        assertRefused(use(h, a, "strike", Target.unit(target)), RejectReason.ON_COOLDOWN); // at tick fired + 48
        assertRefused(use(h, a, "strike", Target.unit(target)), RejectReason.ON_COOLDOWN); // the tick before: 49
        assertThat(h.tick()).isEqualTo(fired + 49);
        assertThat(use(h, a, "strike", Target.unit(target)).outcome())
                .as("at tick fired + 50 it is ready again")
                .isEqualTo(CommandOutcome.ACCEPTED);
    }

    @Test
    void theCostIsPaidOnceAtFiringAndTheBooksBalance() {
        SimHarness h = AbilityFixtures.match();
        food(h, 25);
        int a = spearman(h, F0, 4, 16);
        int target = walker(h, F1, 12, 16);
        assertThat(use(h, a, "strike", Target.unit(target)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(stock(h)).as("walking, not yet paid").isEqualTo(25_000);
        assertThat(blows(h, a, 100)).hasSize(1);
        assertThat(stock(h)).as("10 food paid, once").isEqualTo(15_000);
        assertThat(state(h).treasury().spent(0, 0)).isEqualTo(10_000);
        assertThat(state(h).treasury().broken()).as("conservation").isNull();
        h.runTicks(60);
        assertThat(use(h, a, "strike", Target.unit(target)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(stock(h)).isEqualTo(5_000);
        h.runTicks(60);
        CommandResult broke = use(h, a, "strike", Target.unit(target));
        assertRefused(broke, RejectReason.NOT_ENOUGH_RESOURCES);
        assertThat(stock(h)).as("a refusal pays nothing").isEqualTo(5_000);
        h.assertInvariants();
    }

    @Test
    void whatWasAffordableAtTheCommandButIsNotAtFiringFailsTheOrderWithoutPaying() {
        SimHarness h = AbilityFixtures.match();
        food(h, 10); // enough for one strike
        int far = spearman(h, F0, 4, 16); // the lower handle: checked first, walks
        int near = spearman(h, F0, 10, 16); // checked second, in range: fires and pays at once
        int target = walker(h, F1, 12, 16);
        List<OrderFinished> finished = new ArrayList<>();
        state(h).bus().subscribe(OrderFinished.class, finished::add);
        h.submit(P0, UseAbility.replace(UnitSet.of(far, near), new AbilityId("strike"), Target.unit(target)));
        h.step();
        assertThat(h.results().get(h.results().size() - 1))
                .extracting(CommandResult::outcome, CommandResult::firstReason)
                .as("each unit was checked alone, before the other paid: both could")
                .containsExactly(CommandOutcome.ACCEPTED, null);
        assertThat(stock(h)).as("the unit in range paid").isZero();
        assertThat(finished).containsExactly(new OrderFinished(near, false));
        h.runTicks(100);
        assertThat(finished)
                .as("the one that walked found the treasury empty")
                .containsExactly(new OrderFinished(near, false), new OrderFinished(far, true));
        assertThat(state(h).treasury().spent(0, 0)).as("paid once").isEqualTo(10_000);
        assertThat(state(h).treasury().broken()).isNull();
    }

    @Test
    void anAbilityJoinsTheQueueLikeAnAttackAndIsUsedWhenItsTurnComes() {
        SimHarness h = AbilityFixtures.match();
        food(h, 100);
        int a = spearman(h, F0, 4, 16);
        int target = walker(h, F1, 10, 22);
        int i = Handle.index(a);
        h.submit(P0, Move.replace(UnitSet.of(a), new Vec2d(10, 24)));
        h.submit(P0, new UseAbility(UnitSet.of(a), new AbilityId("strike"), Target.unit(target), QueueMode.QUEUE));
        h.step();
        assertThat(state(h).orders().size(i)).isEqualTo(2);
        assertThat(state(h).orders().kind(i)).as("the move first").isEqualTo(OrderKind.MOVE);
        assertThat(blows(h, a, 1)).isEmpty();
        List<Long> at = blows(h, a, 300);
        assertThat(at).as("used once the move was done").hasSize(1);
        assertThat(state(h).motion().z(i)).as("it had arrived").isGreaterThan(22);
        assertThat(state(h).orders().size(i)).isZero();
    }

    @Test
    void aQueuedUseIsCheckedForCooldownAndCostWhenItFiresNotWhenOrdered() {
        SimHarness h = AbilityFixtures.match();
        food(h, 100);
        int a = spearman(h, F0, 10, 16);
        int target = walker(h, F1, 12, 16);
        int i = Handle.index(a);
        assertThat(use(h, a, "strike", Target.unit(target)).outcome()).isEqualTo(CommandOutcome.ACCEPTED);
        long first = h.tick();
        h.submit(P0, Move.replace(UnitSet.of(a), new Vec2d(10, 28))); // a walk longer than the cooldown
        h.submit(P0, new UseAbility(UnitSet.of(a), new AbilityId("strike"), Target.unit(target), QueueMode.QUEUE));
        h.step();
        CommandResult queued = h.results().get(h.results().size() - 1);
        assertThat(queued.outcome()).as("on cooldown, but its turn is later").isEqualTo(CommandOutcome.ACCEPTED);
        assertRefused(use(h, a, "strike", Target.unit(target)), RejectReason.ON_COOLDOWN); // a replacing one is not
        List<Long> at = blows(h, a, 400);
        assertThat(at)
                .as("the queued use fired once its turn came, after the cooldown")
                .isNotEmpty();
        h.submit(P0, new UseAbility(UnitSet.of(a), new AbilityId("strike"), Target.unit(target), QueueMode.PUSH_FRONT));
        h.step();
        assertThat(h.results().get(h.results().size() - 1).outcome())
                .as("in front of the queue, on cooldown again, accepted all the same")
                .isEqualTo(CommandOutcome.ACCEPTED);
        assertThat(at.get(0)).isGreaterThanOrEqualTo(first + 50);
        assertThat(state(h).orders().size(i)).isZero();
    }

    @Test
    void aTargetThatDiesOnTheWayFailsTheOrder() {
        SimHarness h = AbilityFixtures.match();
        food(h, 100);
        int a = spearman(h, F0, 4, 16);
        int target = walker(h, F1, 20, 16);
        List<OrderFinished> finished = new ArrayList<>();
        state(h).bus().subscribe(OrderFinished.class, finished::add);
        use(h, a, "strike", Target.unit(target));
        h.runTicks(3);
        state(h).removeUnit(target);
        h.runTicks(3);
        assertThat(finished).containsExactly(new OrderFinished(a, true));
        assertThat(stock(h)).as("nothing paid").isEqualTo(100_000);
    }
}
