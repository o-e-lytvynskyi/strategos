package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.AbilityDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UseAbility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/** UNIT-10.3: the command card's ability buttons come from the selected units' templates, with their cooldown. */
class AbilityCardTest {
    /** Three abilities on the infantry, in id order: a self-use with a cost, a point and a unit target. */
    private static final String ABILITIES = """
            {
              "alpha": { "target": "none", "cooldown": 100, "icon": "minecraft:iron_horse_armor",
                         "Cost": { "resources": { "food": 10 } },
                         "effects": [ { "kind": "heal", "params": { "amount": 5 } } ] },
              "bravo": { "target": "point", "range": 10, "cooldown": 40,
                         "effects": [ { "kind": "teleport", "params": {} } ] },
              "carol": { "target": "unit", "range": 4,
                         "effects": [ { "kind": "heal", "params": { "amount": 5 } } ] }
            }
            """;

    private static final TemplateSet WITH = set(ABILITIES);
    private static final TemplateSet WITHOUT = TemplateSet.builtin();
    private static final int INFANTRY =
            Objects.requireNonNull(WITH.byId(TemplateSet.INFANTRY)).netIndex();
    private static final int WORKER =
            Objects.requireNonNull(WITH.byId(TemplateSet.WORKER)).netIndex();

    private static TemplateSet set(String abilities) {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            JsonObject json = Objects.requireNonNull(TemplateSet.builtin().flattened(t.id()));
            if (t.id().equals(TemplateSet.INFANTRY)) {
                json.add("Abilities", JsonParser.parseString(abilities));
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(
                flats,
                TemplateSet.builtin().techs().stream()
                        .map(TemplateCompiler.TechFlat::of)
                        .toList());
        assertThat(result.problems()).isEmpty();
        return result.set();
    }

    private static List<CommandCard.Button> abilities(
            TemplateSet templates, List<Integer> units, Map<Integer, Integer> cooldowns, long food) {
        CommandCard card = CommandCard.of(
                new CommandCard.Input(
                        templates,
                        CommandCardTest.RESOURCES,
                        CommandCardResearchTest.faction(food, 1_000, List.of()),
                        units,
                        false,
                        List.of(),
                        false,
                        null,
                        null,
                        cooldowns),
                null);
        return card.buttons().stream()
                .filter(b -> b.kind() == CommandCard.Kind.ABILITY)
                .toList();
    }

    @Test
    void aTemplateWithoutAbilitiesShowsNone() {
        assertThat(abilities(WITH, List.of(WORKER), Map.of(), 50_000)).isEmpty();
        assertThat(abilities(WITHOUT, List.of(INFANTRY), Map.of(), 50_000)).isEmpty();
    }

    @Test
    void theAbilitiesShowInTheOrderOfTheirIdsInTheSlotsTheyTake() {
        List<CommandCard.Button> buttons = abilities(WITH, List.of(INFANTRY), Map.of(), 50_000);
        assertThat(buttons).hasSize(3);
        assertThat(buttons)
                .extracting(b -> WITH.abilities().get(b.template()).id())
                .containsExactly("alpha", "bravo", "carol");
        assertThat(buttons).extracting(CommandCard.Button::slot).containsExactly(1, 2, 3);
        assertThat(buttons)
                .extracting(b -> CommandCard.hotkey(b.slot()))
                .as("the grid's keys beside the build page's Q")
                .containsExactly('W', 'E', 'R');
        AbilityDef alpha = WITH.abilities().get(buttons.get(0).template());
        assertThat(alpha.icon()).isEqualTo("minecraft:iron_horse_armor");
        assertThat(alpha.nameKey()).isEqualTo("strategos.ability.alpha");
        assertThat(alpha.descriptionKey()).isEqualTo("strategos.ability.alpha.desc");
    }

    @Test
    void aMixedSelectionShowsTheAbilitiesOfWhoeverHasThem() {
        assertThat(abilities(WITH, List.of(WORKER, INFANTRY, INFANTRY), Map.of(), 50_000))
                .hasSize(3);
    }

    @Test
    void theCostIsOnTheButtonAndTheStockDecidesWhetherItCanBeUsed() {
        CommandCard.Button rich =
                abilities(WITH, List.of(INFANTRY), Map.of(), 50_000).get(0);
        assertThat(rich.cost()).containsExactly(10_000L, 0L, 0L, 0L);
        assertThat(rich.affordable()).isTrue();
        assertThat(rich.cooldown()).isZero();
        CommandCard.Button poor =
                abilities(WITH, List.of(INFANTRY), Map.of(), 4_000).get(0);
        assertThat(poor.affordable()).isFalse();
        assertThat(poor.reason()).isEqualTo("Needs 6 food");
        assertThat(abilities(WITH, List.of(INFANTRY), Map.of(), 50_000).get(1).cost())
                .containsOnly(0L);
    }

    @Test
    void anAbilityOnCooldownIsGreyedWithItsTicksLeft() {
        int alpha = WITH.abilityIndex("alpha");
        List<CommandCard.Button> buttons = abilities(WITH, List.of(INFANTRY), Map.of(alpha, 41), 50_000);
        assertThat(buttons.get(0).cooldown()).isEqualTo(41);
        assertThat(buttons.get(0).affordable()).isFalse();
        assertThat(buttons.get(0).reason()).isEqualTo("Ready in 3 s");
        assertThat(buttons.get(1).cooldown()).as("the others are ready").isZero();
        assertThat(buttons.get(1).affordable()).isTrue();
    }

    @Test
    void aSelectionRestsOnlyWhenEveryUnitThatHasTheAbilityRests() {
        int alpha = WITH.abilityIndex("alpha");
        List<Integer> units = List.of(INFANTRY, WORKER, INFANTRY);
        List<List<UnitDetail.Cooldown>> resting = List.of(
                List.of(new UnitDetail.Cooldown(alpha, 80)), List.of(), List.of(new UnitDetail.Cooldown(alpha, 30)));
        assertThat(CommandCard.cooldowns(WITH, units, resting))
                .as("the least of those that rest; the worker has none")
                .containsExactly(Map.entry(alpha, 30));
        assertThat(CommandCard.cooldowns(
                        WITH, units, List.of(List.of(), List.of(), List.of(new UnitDetail.Cooldown(alpha, 30)))))
                .as("a unit that is ready makes it ready")
                .isEmpty();
        assertThat(CommandCard.cooldowns(WITH, List.of(INFANTRY), List.of())).isEmpty();
    }

    private static final IntUnaryOperator TEMPLATES = h -> h < 100 ? INFANTRY : WORKER;

    @Test
    void anAbilityWithoutATargetFiresAtOnceForTheSelectedUnitsThatHaveIt() {
        CardController controller = new CardController();
        CommandCard.Button button =
                abilities(WITH, List.of(INFANTRY), Map.of(), 50_000).get(0);
        List<Command> sent = new ArrayList<>();
        CardController.Click click =
                controller.click(button, List.of(5, 150, 6), TEMPLATES, null, WITH, sent::add, false);
        assertThat(click.effect()).isEqualTo(CardController.Effect.SENT);
        assertThat(sent).hasSize(1);
        UseAbility use = (UseAbility) sent.get(0);
        assertThat(use.ability().value()).isEqualTo("alpha");
        assertThat(use.units().size()).isEqualTo(2);
        assertThat(use.units().get(0)).isEqualTo(5);
        assertThat(use.units().get(1)).isEqualTo(6);
        assertThat(use.target()).isEqualTo(Target.none());
        assertThat(use.mode()).as("the orders it interrupts resume").isEqualTo(QueueMode.PUSH_FRONT);
    }

    @Test
    void anAbilityWithATargetArmsTargetingAndSendsNothingYet() {
        CardController controller = new CardController();
        List<CommandCard.Button> buttons = abilities(WITH, List.of(INFANTRY), Map.of(), 50_000);
        for (int k : new int[] {1, 2}) { // the point and the unit ability
            List<Command> sent = new ArrayList<>();
            CardController.Click click =
                    controller.click(buttons.get(k), List.of(5), TEMPLATES, null, WITH, sent::add, false);
            assertThat(click.effect()).isEqualTo(CardController.Effect.TARGET);
            assertThat(click.template()).isEqualTo(buttons.get(k).template());
            assertThat(sent).isEmpty();
        }
    }

    @Test
    void anAbilityNoSelectedUnitHasDoesNothing() {
        CardController controller = new CardController();
        CommandCard.Button button =
                abilities(WITH, List.of(INFANTRY), Map.of(), 50_000).get(0);
        List<Command> sent = new ArrayList<>();
        assertThat(controller.click(button, List.of(150), TEMPLATES, null, WITH, sent::add, false))
                .isEqualTo(new CardController.Click(CardController.Effect.NONE, -1));
        assertThat(sent).isEmpty();
    }
}
