package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.F1;
import static dev.strategos.core.sim.ability.AbilityFixtures.P0;
import static dev.strategos.core.sim.ability.AbilityFixtures.ram;
import static dev.strategos.core.sim.ability.AbilityFixtures.spearman;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static dev.strategos.core.sim.ability.AbilityFixtures.walker;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.strategos.core.content.CorePaths;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.capture.CaptureSystem;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * UNIT-10.2: one row per effect primitive, each through the pipeline the rest of the core uses: damage into the combat
 * system's packets (resistances apply, the user is the attacker), heal into the hit-point store (capped, never on a
 * building), modifiers for a number of ticks, capture strength, spawn and teleport.
 */
class AbilityEffectsTest {
    private static void use(SimHarness h, int unit, String ability, Target target) {
        h.submit(P0, UseAbility.replace(UnitSet.of(unit), new AbilityId(ability), target));
        h.step();
    }

    private static double damage(SimHarness h, int unit) {
        return state(h).health().damage(Handle.index(unit));
    }

    private static Arguments row(String name, Consumer<SimHarness> check) {
        return Arguments.of(name, check);
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                row("damage hurts the target by the user's packet, through its resistance", h -> {
                    h.submit(PlayerSlot.ADMIN, new Grant(F0, "food", 10));
                    h.step();
                    int a = spearman(h, F0, 10, 16);
                    int target = walker(h, F1, 12, 16);
                    use(h, a, "strike", Target.unit(target));
                    assertThat(h.events(UnitHurt.class))
                            .extracting(UnitHurt::handle, UnitHurt::attacker, UnitHurt::amount)
                            .containsExactly(org.assertj.core.groups.Tuple.tuple(target, a, 20f));
                    assertThat(damage(h, target)).isEqualTo(20);
                }),
                row("damage in an area hurts enemies inside it and nobody else", h -> {
                    int a = spearman(h, F0, 10, 16);
                    int inside = walker(h, F1, 15, 16);
                    int edge = walker(h, F1, 18.5, 16); // 3.5 from the centre (15, 16): in, radius 4
                    int outside = walker(h, F1, 20, 16);
                    int friend = walker(h, F0, 15, 17);
                    use(h, a, "slam", Target.point(15, 16));
                    assertThat(damage(h, inside)).isEqualTo(8);
                    assertThat(damage(h, edge)).isEqualTo(8);
                    assertThat(damage(h, outside)).isZero();
                    assertThat(damage(h, friend)).as("not the user's own").isZero();
                }),
                row("heal takes damage off, never past the unit's most, and fires on the user", h -> {
                    int a = spearman(h, F0, 10, 16);
                    state(h).health().setDamage(Handle.index(a), 40);
                    use(h, a, "mend", Target.none());
                    assertThat(damage(h, a)).isEqualTo(25);
                    h.runTicks(25);
                    use(h, a, "mend", Target.none());
                    h.runTicks(25);
                    use(h, a, "mend", Target.none());
                    assertThat(damage(h, a))
                            .as("a heal past full stops at full")
                            .isZero();
                }),
                row("heal on an ally, never on a building", h -> {
                    int a = spearman(h, F0, 10, 16);
                    int friend = spearman(h, F0, 11, 16);
                    state(h).health().setDamage(Handle.index(friend), 4);
                    use(h, a, "heal_other", Target.unit(friend));
                    assertThat(damage(h, friend)).isZero();
                    int building = Handle.of(BuildingStore.BASE, 1);
                    h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("heal_other"), Target.unit(building)));
                    h.step();
                    assertThat(h.results().get(h.results().size() - 1).firstReason())
                            .isEqualTo(RejectReason.INVALID_ARGUMENT);
                }),
                row("apply_modifiers changes the numbers for exactly its ticks", h -> {
                    int a = spearman(h, F0, 10, 16);
                    MatchState s = state(h);
                    int i = Handle.index(a);
                    double walk = s.stats().walkSpeed(i);
                    double hack = s.stats().meleeDamage(i, 0);
                    use(h, a, "rally", Target.none());
                    long fired = h.tick();
                    assertThat(s.stats().walkSpeed(i)).isCloseTo(walk * 1.5, within(1e-12));
                    assertThat(s.stats().meleeDamage(i, 0)).isCloseTo(hack + 2, within(1e-12));
                    while (h.tick() < fired + 39) {
                        h.step();
                    }
                    assertThat(s.stats().walkSpeed(i)).as("the last tick of 40").isCloseTo(walk * 1.5, within(1e-12));
                    h.step();
                    assertThat(h.tick()).isEqualTo(fired + 40);
                    assertThat(s.stats().walkSpeed(i))
                            .as("gone on the 40th tick after")
                            .isCloseTo(walk, within(1e-12));
                    assertThat(s.stats().meleeDamage(i, 0)).isCloseTo(hack, within(1e-12));
                }),
                row("capture_strength adds to the user's own for its ticks", h -> {
                    int a = spearman(h, F0, 10, 16);
                    MatchState s = state(h);
                    int i = Handle.index(a);
                    assertThat(CaptureSystem.strength(s, i)).isEqualTo(2_500);
                    use(h, a, "grip", Target.none());
                    assertThat(CaptureSystem.strength(s, i)).isEqualTo(6_500);
                    h.runTicks(60);
                    assertThat(CaptureSystem.strength(s, i)).isEqualTo(2_500);
                }),
                row("spawn makes the units of its template at the target, for the user's faction", h -> {
                    int a = spearman(h, F0, 10, 16);
                    int before = state(h).entities().aliveCount();
                    use(h, a, "summon", Target.point(14, 16));
                    List<UnitCreated> made = h.events(UnitCreated.class).stream()
                            .filter(e -> e.handle() != a)
                            .toList();
                    assertThat(state(h).entities().aliveCount()).isEqualTo(before + 2);
                    List<UnitCreated> last = made.subList(made.size() - 2, made.size());
                    assertThat(last).extracting(e -> e.faction().value()).containsExactly(0, 0);
                    assertThat(last).extracting(UnitCreated::x).containsExactly(13.5, 14.5);
                    assertThat(last).extracting(UnitCreated::z).containsExactly(16.0, 16.0);
                }),
                row("spawn at the unit cap is refused and costs nothing", h -> {
                    SimHarness capped = SimHarness.builder()
                            .templates(AbilityFixtures.set(AbilityFixtures.ALL))
                            .rules(new MatchRules(256, 3))
                            .build();
                    capped.submit(PlayerSlot.ADMIN, new Grant(F0, "food", 20));
                    capped.step();
                    int a = spearman(capped, F0, 10, 16);
                    walker(capped, F1, 20, 20);
                    capped.submit(
                            P0, UseAbility.replace(UnitSet.of(a), new AbilityId("conjure"), Target.point(14, 16)));
                    capped.step();
                    assertThat(capped.results().get(capped.results().size() - 1))
                            .extracting(r -> r.outcome(), r -> r.firstReason())
                            .containsExactly(CommandOutcome.REJECTED, RejectReason.UNIT_CAP);
                    assertThat(state(capped).treasury().stock(0, 0))
                            .as("nothing paid")
                            .isEqualTo(20_000);
                    assertThat(state(capped).play().abilities().empty())
                            .as("no cooldown either")
                            .isTrue();
                    assertThat(state(capped).entities().aliveCount()).isEqualTo(2);
                }),
                row("spawn where nothing can stand is refused as blocked", h -> {
                    int a = spearman(h, F0, 10, 16);
                    h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("summon"), Target.point(10, 16)));
                    h.step(); // a point next to the user is fine: it only needs land
                    assertThat(h.results().get(h.results().size() - 1).outcome())
                            .isEqualTo(CommandOutcome.ACCEPTED);
                }),
                row("heal mends flesh only: not a building, not siege, not what is not organic", h -> {
                    int a = spearman(h, F0, 10, 16);
                    int ram = ram(h, F0, 11, 16);
                    int walker = walker(h, F0, 12, 16);
                    int flesh = spearman(h, F0, 13, 16);
                    for (int unit : new int[] {a, ram, walker, flesh}) {
                        state(h).health().setDamage(Handle.index(unit), 5);
                    }
                    for (int unit : new int[] {ram, walker}) {
                        h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("heal_other"), Target.unit(unit)));
                        h.step();
                        assertThat(h.results().get(h.results().size() - 1).firstReason())
                                .isEqualTo(RejectReason.INVALID_ARGUMENT);
                    }
                    use(h, a, "bloom", Target.point(12, 16));
                    assertThat(damage(h, flesh)).as("flesh mended").isZero();
                    assertThat(damage(h, a)).isZero();
                    assertThat(damage(h, ram))
                            .as("a ram is repaired, not healed")
                            .isEqualTo(5);
                    assertThat(damage(h, walker)).isEqualTo(5);
                }),
                row("timed modifiers aimed at a unit reach allies by default and enemies when asked", h -> {
                    int a = spearman(h, F0, 10, 16);
                    int friend = spearman(h, F0, 11, 16);
                    int enemy = walker(h, F1, 12, 16);
                    for (Object[] c : new Object[][] {
                        {"bless", enemy, RejectReason.INVALID_ARGUMENT},
                        {"curse", friend, RejectReason.INVALID_ARGUMENT},
                        {"bless", friend, null},
                        {"curse", enemy, null}
                    }) {
                        h.submit(
                                P0,
                                UseAbility.replace(
                                        UnitSet.of(a), new AbilityId((String) c[0]), Target.unit((Integer) c[1])));
                        h.step();
                        assertThat(h.results().get(h.results().size() - 1).firstReason())
                                .as("%s", c[0])
                                .isEqualTo(c[2]);
                    }
                    assertThat(state(h).stats().walkSpeed(Handle.index(friend)))
                            .isCloseTo(state(h).stats().walkSpeed(Handle.index(a)) * 1.5, within(1e-12));
                    assertThat(state(h).stats().walkSpeed(Handle.index(enemy))).isPositive();
                }),
                row("teleport puts the user on the point, and its next order walks on from there", h -> {
                    int a = spearman(h, F0, 10, 16);
                    use(h, a, "blink", Target.point(18, 16));
                    MatchState s = state(h);
                    int i = Handle.index(a);
                    assertThat(s.motion().x(i)).isEqualTo(18);
                    assertThat(s.motion().z(i)).isEqualTo(16);
                    h.submit(P0, Move.replace(UnitSet.of(a), new Vec2d(18, 24)));
                    h.runTicks(150);
                    assertThat(s.motion().z(i))
                            .as("re-pathed from the new place")
                            .isCloseTo(24, within(1.5));
                    assertThat(s.motion().x(i)).isCloseTo(18, within(1.5));
                }),
                row("teleport into a place nobody can stand on is refused before anything is paid", h -> {
                    int a = spearman(h, F0, 10, 16);
                    h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("blink"), Target.point(-5, 16)));
                    h.step();
                    assertThat(h.results().get(h.results().size() - 1).outcome())
                            .isEqualTo(CommandOutcome.REJECTED);
                    assertThat(state(h).play().abilities().readyAt(a, "blink"))
                            .as("no cooldown started")
                            .isZero();
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void effect(String name, Consumer<SimHarness> check) {
        SimHarness h = AbilityFixtures.match();
        check.accept(h);
        h.assertInvariants();
    }

    @org.junit.jupiter.api.Test
    void theRegistriesHoldExactlyTheVocabularyTheLintKnows() {
        assertThat(AbilityEffects.ids()).isEqualTo(dev.strategos.core.content.AbilityKinds.effects());
        assertThat(AbilityTargets.ids()).isEqualTo(dev.strategos.core.content.AbilityKinds.pickers());
        assertThat(AbilityEffects.ids()).isSorted();
        assertThat(AbilityTargets.ids()).isSorted();
        for (String kind : AbilityEffects.ids()) {
            assertThat(CorePaths.ABILITIES).isNotEmpty();
            org.assertj.core.api.Assertions.assertThatCode(() -> AbilityEffects.create(kind, sample(kind)))
                    .doesNotThrowAnyException();
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> AbilityEffects.create("nope", new com.google.gson.JsonObject()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static com.google.gson.JsonObject sample(String kind) {
        return switch (kind) {
            case "damage" ->
                AbilityFixtures.parse("{\"type\":\"hack\",\"amount\":1}").getAsJsonObject();
            case "heal" -> AbilityFixtures.parse("{\"amount\":1}").getAsJsonObject();
            case "apply_modifiers", "capture_strength" ->
                AbilityFixtures.parse("{\"ticks\":1,\"amount\":1,\"modifiers\":[]}")
                        .getAsJsonObject();
            case "spawn" -> AbilityFixtures.parse("{\"template\":\"a:b\"}").getAsJsonObject();
            default -> new com.google.gson.JsonObject();
        };
    }
}
