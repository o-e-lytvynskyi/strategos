package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SetAi;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.AiHandicap;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.Construction;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.testkit.SimHarness;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI-05 in L1 (M6 §2.20): the resolver turns {@code ai/skirmish.json} and a level into the script {@code SetAi}
 * carries, which fits {@code SetAi}'s limit; each knob of the table is in it and does what it says: the think period,
 * the commands per think, the reaction before a defence, the wave size and the open handicap.
 */
class LevelsTest {
    private static final String SKIRMISH = HeadlessMatch.read("ai/skirmish.json");

    private static StrategyScript resolved(String level) {
        return StrategyScript.parse(Levels.resolve(SKIRMISH, level));
    }

    @Test
    void theKnobsOfTheTableAreInTheResolvedScripts() {
        StrategyScript easy = resolved("easy");
        assertThat(easy.level())
                .isEqualTo(new StrategyScript.Level("easy", 40, 100, 8, new StrategyScript.Handicap(0.85, 1.15)));
        assertThat(easy.battle().enabled())
                .as("Easy has no counters, focus or retreat")
                .isFalse();
        StrategyScript normal = resolved("normal");
        assertThat(normal.level())
                .isEqualTo(new StrategyScript.Level("normal", 20, 60, 16, StrategyScript.Handicap.NONE));
        assertThat(normal.battle().enabled()).isTrue();
        StrategyScript hard = resolved("hard");
        assertThat(hard.level()).isEqualTo(new StrategyScript.Level("hard", 20, 20, 32, StrategyScript.Handicap.NONE));
        assertThat(hard.battle()).as("Hard's tactics are Normal's").isEqualTo(normal.battle());
    }

    @Test
    void theWaveSizeScalesEveryAttack() {
        StrategyScript base = StrategyScript.parse(SKIRMISH);
        for (var level : List.of("easy:0.6", "normal:1.0", "hard:1.4")) {
            String name = level.substring(0, level.indexOf(':'));
            double wave = Double.parseDouble(level.substring(level.indexOf(':') + 1));
            StrategyScript s = resolved(name);
            assertThat(s.attack().min()).as(name).isEqualTo((int)
                    Math.round(base.attack().min() * wave));
            for (int k = 0; k < base.openings().size(); k++) {
                var was = base.openings().get(k).attack();
                var now = s.openings().get(k).attack();
                assertThat(now.isPresent()).isEqualTo(was.isPresent());
                if (was.isPresent()) {
                    assertThat(now.get().min())
                            .as(name + " " + base.openings().get(k).name())
                            .isEqualTo((int) Math.round(was.get().min() * wave));
                    assertThat(now.get().first())
                            .as("timings are not the wave size's")
                            .isEqualTo(was.get().first());
                }
            }
        }
    }

    @Test
    void theResolvedScriptHasNoLevelTableAndEveryLevelFitsSetAi() {
        for (String level : Levels.names()) {
            String json = Levels.resolve(SKIRMISH, level);
            assertThat(JsonParser.parseString(json).getAsJsonObject().has("levels"))
                    .as(level)
                    .isFalse();
            assertThat(json.getBytes(StandardCharsets.UTF_8).length)
                    .as("the %s script's bytes against SetAi's %d", level, SetAi.SCRIPT_BYTES)
                    .isLessThanOrEqualTo(SetAi.SCRIPT_BYTES);
        }
    }

    @Test
    void aMissingOrMalformedLevelIsRefused() {
        assertThatThrownBy(() -> Levels.resolve(SKIRMISH, "brutal")).isInstanceOf(IllegalArgumentException.class);
        JsonObject bonus = JsonParser.parseString(SKIRMISH).getAsJsonObject();
        bonus.getAsJsonObject("levels")
                .getAsJsonObject("hard")
                .add("handicap", JsonParser.parseString("{\"gather\":1.2}"));
        assertThatThrownBy(() -> Levels.resolve(bonus.toString(), "hard"))
                .as("a gathering bonus is no handicap")
                .isInstanceOf(IllegalArgumentException.class);
        JsonObject quicker = JsonParser.parseString(SKIRMISH).getAsJsonObject();
        quicker.getAsJsonObject("levels")
                .getAsJsonObject("hard")
                .add("handicap", JsonParser.parseString("{\"train\":0.8}"));
        assertThatThrownBy(() -> Levels.resolve(quicker.toString(), "hard"))
                .as("a quicker training is no handicap")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void researchAndPhaseAreBuildSteps() {
        StrategyScript s = StrategyScript.parse("{\"ratio\":{},\"attack\":{\"first\":1,\"every\":1},\"build\":["
                + "{\"building\":\"strategos:building/house\",\"population\":8},"
                + "{\"research\":\"strategos:carry_1\",\"population\":12},"
                + "{\"phase\":\"town\"}]}");
        assertThat(s.build()).hasSize(3);
        assertThat(s.build().get(0).researches()).isFalse();
        assertThat(s.build().get(1).research()).isEqualTo("strategos:carry_1");
        assertThat(s.build().get(2).research()).isEqualTo("strategos:phase_town");
        assertThatThrownBy(
                        () -> StrategyScript.parse(
                                "{\"ratio\":{},\"attack\":{\"first\":1,\"every\":1},"
                                        + "\"build\":[{\"building\":\"strategos:building/house\",\"research\":\"strategos:carry_1\"}]}"))
                .as("a step is a building or a research")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StrategyScript.parse(
                        "{\"ratio\":{},\"attack\":{\"first\":1,\"every\":1},\"build\":[{\"population\":3}]}"))
                .isInstanceOf(IllegalArgumentException.class);
        StrategyScript skirmish = StrategyScript.parse(SKIRMISH);
        assertThat(skirmish.build().stream().map(StrategyScript.Step::research))
                .as("the skirmish script goes up to Town")
                .contains("strategos:phase_town");
    }

    // ---- the handicap reaches the stats, and a restore derives it again

    private static double trainSeconds(SimHarness h, int faction) {
        MatchState s = KernelAccess.state(h.core());
        int worker = s.templates().byId(TemplateSet.WORKER).netIndex();
        return s.stats().blockValue(worker, faction, s.stats().path(Construction.TIME));
    }

    private static double gatherRate(SimHarness h, int faction) {
        MatchState s = KernelAccess.state(h.core());
        int worker = s.templates().byId(TemplateSet.WORKER).netIndex();
        return s.stats().blockValue(worker, faction, s.stats().path("ResourceGatherer/rates/wood/tree"));
    }

    private static SimHarness twoLevels(String first, String second) {
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "skirmish", Levels.resolve(SKIRMISH, first)));
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(1), "skirmish", Levels.resolve(SKIRMISH, second)));
        h.step();
        return h;
    }

    @Test
    void easySlowsItsGatheringAndTrainingAndNothingGivesMore() {
        SimHarness h = twoLevels("easy", "normal");
        assertThat(trainSeconds(h, 0)).isEqualTo(trainSeconds(h, 1) * 1.15);
        assertThat(gatherRate(h, 0)).isEqualTo(gatherRate(h, 1) * 0.85);
        assertThat(gatherRate(h, 1)).as("Normal's rate is the template's").isPositive();
        SimHarness hard = twoLevels("hard", "normal");
        assertThat(trainSeconds(hard, 0)).isEqualTo(trainSeconds(hard, 1));
        assertThat(gatherRate(hard, 0)).isEqualTo(gatherRate(hard, 1));
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "", ""));
        h.step();
        assertThat(trainSeconds(h, 0)).as("handed back, handicap gone").isEqualTo(trainSeconds(h, 1));
    }

    @Test
    void aRestoreDerivesTheHandicapAgain() {
        SimHarness h = twoLevels("easy", "normal");
        h.runTicks(40);
        MatchSnapshot snap = h.core().snapshot();
        SimHarness back = SimHarness.restore(
                snap, TemplateSet.builtin(), h.core().journal().segments().get(0)::slice);
        assertThat(trainSeconds(back, 0)).isEqualTo(trainSeconds(h, 0));
        assertThat(gatherRate(back, 0)).isEqualTo(gatherRate(h, 0));
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        AiHandicap.reapply(KernelAccess.state(back.core())); // idempotent: a swap derives it again
        assertThat(trainSeconds(back, 0)).isEqualTo(trainSeconds(h, 0));
    }

    // ---- the pace

    @Test
    void aSlowerPlayerThinksOnItsOwnPeriodAndSendsAtMostItsCommandsPerThink() {
        String script = HeadlessMatch.read("ai/first_loop.json");
        JsonObject o = JsonParser.parseString(script).getAsJsonObject();
        o.add("level", JsonParser.parseString("{\"think\":40,\"commands\":1}"));
        List<Long> sentAt = new ArrayList<>();
        long[] last = {0};
        HeadlessMatch.run(
                2, // the seed: faction 0's turn is at the period's multiples
                1_200,
                o.toString(),
                (h, out) -> {
                    long now = KernelAccess.state(h.core()).aiSlots().get(0).commands();
                    if (now != last[0]) {
                        assertThat(now - last[0])
                                .as("commands in tick %d", h.tick())
                                .isEqualTo(1);
                        sentAt.add(h.tick());
                        last[0] = now;
                    }
                },
                false);
        assertThat(sentAt).hasSizeGreaterThan(5);
        for (long t : sentAt) {
            assertThat(t % 40).as("a command of tick %d", t).isEqualTo(0);
        }
    }

    /** Ticks from the defence first seeing the attack to its committed soldiers, for a bot that reacts after {@code reaction}. */
    private static long defenceDelay(int think, int reaction) {
        String quiet = DefenceTest.QUIET.substring(0, DefenceTest.QUIET.length() - 1) + ",\"level\":{\"think\":" + think
                + ",\"reaction\":" + reaction + "}}";
        SimHarness h = BattleBotTest.ground(TemplateSet.builtin());
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 0, 40, 10, 6);
        BattleBotTest.spawn(h, TemplateSet.WORKER, 0, 12, 24, 4);
        BattleBotTest.spawn(h, BattleBotTest.SPEAR, 1, 14, 56, 3);
        List<Integer> workers = handles(h, 0, TemplateSet.WORKER.value());
        List<Integer> raiders = handles(h, 1, BattleBotTest.SPEAR.value());
        List<Integer> soldiers = handles(h, 0, BattleBotTest.SPEAR.value());
        h.submit(PlayerSlot.ADMIN, new SetAi(new FactionSlot(0), "defence", quiet));
        for (int k = 0; k < 3; k++) {
            h.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(raiders.get(k)), workers.get(k)));
        }
        while (h.tick() < 1_500) {
            h.step();
            long alert = BattleBotTest.bot(h).memory().get(Slots.ALERT_SINCE);
            if (alert != 0 && soldiers.stream().filter(s -> fights(h, s)).count() >= 4) {
                return h.tick() - (alert - 1);
            }
        }
        throw new AssertionError("the soldiers never committed");
    }

    private static boolean fights(SimHarness h, int handle) {
        for (OrderQueues.Order o : h.core().queries().orders(handle)) {
            if (o.kind() == OrderKind.ATTACK || o.kind() == OrderKind.ATTACK_MOVE || o.kind() == OrderKind.ENGAGE) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> handles(SimHarness h, int faction, String id) {
        List<Integer> out = new ArrayList<>();
        for (int handle : h.core().queries().unitHandlesOf(new FactionSlot(faction))) {
            UnitView u = h.core().queries().unit(handle);
            if (u != null && u.template().value().equals(id)) {
                out.add(handle);
            }
        }
        return out;
    }

    @Test
    void theReactionHoldsTheDefenceBackAndEasyStillCommitsWithinTenSeconds() {
        long hard = defenceDelay(20, 20);
        long normal = defenceDelay(20, 60);
        long easy = defenceDelay(40, 100);
        assertThat(hard).as("Hard").isLessThan(normal);
        assertThat(normal).as("Normal").isLessThan(easy);
        assertThat(normal).as("Normal waits its reaction").isGreaterThanOrEqualTo(60);
        assertThat(easy).as("Easy waits its reaction").isGreaterThanOrEqualTo(100);
        assertThat(easy)
                .as("Easy: 100 + 40 + 1 ticks and the orders' arrival, within AI-04.2's ten seconds")
                .isLessThanOrEqualTo(200);
    }

    @Test
    void aHardAgainstNormalMatchCanBeSetUp() {
        Scenario scenario = DifficultyTest.firstSkirmish();
        HeadlessMatch.Result r = HeadlessMatch.run(
                HeadlessMatch.Ground.firstSkirmish(),
                scenario,
                1,
                600,
                List.of(Levels.resolve(SKIRMISH, "hard"), Levels.resolve(SKIRMISH, "normal")),
                (h, out) -> {
                    if (h.tick() == 100) {
                        var slots = KernelAccess.state(h.core()).aiSlots();
                        assertThat(slots.get(0).script()).contains("\"name\":\"hard\"");
                        assertThat(slots.get(1).script()).contains("\"name\":\"normal\"");
                    }
                },
                false);
        assertThat(r.summary().failure()).isEmpty();
        assertThat(r.summary().commands()).allMatch(c -> c > 0);
    }
}
