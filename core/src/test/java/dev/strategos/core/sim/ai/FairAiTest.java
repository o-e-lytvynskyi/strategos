package dev.strategos.core.sim.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.StartSlot;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AI-18 in L1: under fog the AI only commands visible targets, finds the enemy from the map's start slots, and
 * fogged bot matches still end within 45 game minutes (on the proving ground until MAP-02).
 */
class FairAiTest {
    static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId SPEAR = new TemplateId("strategos:unit/infantry");

    /** 45 game minutes at 20 ticks a second (AI-18.3). */
    static final long FORTY_FIVE_MIN = 54_000;

    /** A battle-less wave script: one spearman at 1 s, muster 8 blocks out, 30 s of warning (script minimum). */
    static String waveScript() {
        return """
                {"maxWorkers":0,"ratio":{},"build":[],"housingMargin":0,"soldierQueue":0,\
                "attack":{"first":1,"every":600,"min":1,"muster":8,"announce":30},"sites":[]}""";
    }

    @Test
    void everyTargetWasVisible() {
        List<String> bad = new ArrayList<>();
        HeadlessMatch.run(
                1,
                HeadlessMatch.LIMIT,
                HeadlessMatch.read("ai/first_loop.json"),
                (h, out) -> checkPendingAttacks(h, out, bad),
                true);
        assertThat(bad).isEmpty();
    }

    /**
     * After each tick, every pending {@link Attack} from a computer player targets a unit its faction can see (the
     * visibility the think used, or the one WORLD just refreshed — hysteresis keeps a one-tick gap safe).
     */
    private static void checkPendingAttacks(SimHarness h, TickOutputs out, List<String> bad) {
        MatchState s = KernelAccess.state(h.core());
        MatchQueries q = h.core().queries();
        for (CommandEnvelope e : s.pendingCommands()) {
            if (e.issuer().isAdmin() || !(e.command() instanceof Attack attack)) {
                continue;
            }
            int faction = e.issuer().value();
            int target = attack.target();
            boolean visible = BuildingStore.isBuilding(target)
                    ? buildingVisible(q, faction, target)
                    : q.visibleUnit(faction, target);
            if (!visible) {
                bad.add("tick " + out.tick() + " faction " + faction + " Attack on hidden " + target);
            }
        }
    }

    private static boolean buildingVisible(MatchQueries q, int faction, int handle) {
        for (BuildingView b : q.buildings()) {
            if (b.handle() == handle) {
                return q.visibleBuilding(faction, b);
            }
        }
        return false;
    }

    @Test
    void findsTheBaseFromTheStarts() {
        MatchRules d = MatchRules.DEFAULTS;
        SimHarness h = SimHarness.builder()
                .size(128, 128)
                .factions(2)
                .rules(d.withEconomy(new EconomyRules(d.economy().resources(), 200)))
                .yBand(0, 31, 1)
                .build();
        h.publishAll(VoxelFixtures.flat(128, 128, 32, 1));
        StartSlot west = new StartSlot(8, 1, 8, 0f);
        StartSlot east = new StartSlot(112, 1, 112, 0f);
        KernelAccess.state(h.core()).setStartPositions(List.of(west, east));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(0), 8, 8, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CENTRE, new FactionSlot(1), 112, 112, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(SPEAR, new FactionSlot(0), new Vec2d(14, 14), 4, 2));
        h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 500));
        h.step();
        h.step(); // buildings finish; vision stamps

        AiBudget probe = new AiBudget(AiBudget.CAP);
        AiView view = new AiView(h.core().queries(), 0, probe);
        assertThat(view.enemyBuildings()).as("fog hides the far civic centre").isEmpty();
        assertThat(view.startPositions()).containsExactly(west, east);

        // Drive the military think directly: muster, wait out the 30 s announce, then leave toward the enemy start.
        ComputerPlayer bot = new ComputerPlayer(0, "fair", waveScript());
        List<Command> sent = new ArrayList<>();
        AttackMove march = null;
        for (int think = 0; think < 40 && march == null; think++) {
            for (int i = 0; i < 20; i++) {
                h.step();
            }
            sent.clear();
            AiBudget budget = new AiBudget(AiBudget.CAP);
            bot.think(new AiView(h.core().queries(), 0, budget), budget, sent::add);
            march = sent.stream()
                    .filter(AttackMove.class::isInstance)
                    .map(AttackMove.class::cast)
                    .findFirst()
                    .orElse(null);
        }
        assertThat(march).as("the wave attack-moves toward the enemy start under fog").isNotNull();
        AttackMove leave = java.util.Objects.requireNonNull(march);
        // Short of the start (Tactics.front), not onto the civic centre footprint.
        assertThat(DetMath.length(leave.target().x() - (east.x() + 0.5), leave.target().z() - (east.z() + 0.5)))
                .as("march aims near the east start")
                .isBetween(8.0, 24.0);

        // A scout that arrives by the enemy start (the march's destination) sees the civic centre.
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(SPEAR, new FactionSlot(0), new Vec2d(east.x() - 2, east.z()), 1, 0));
        h.step();
        h.step();
        AiView arrived = new AiView(h.core().queries(), 0, new AiBudget(AiBudget.CAP));
        assertThat(arrived.enemyBuildings().stream().anyMatch(b -> arrived.is(b.template(), "CivicCentre")))
                .as("from the enemy start the civic centre is visible")
                .isTrue();
    }

    @Test
    @Tag("slow")
    void foggedMatchesEndWithin45() {
        HeadlessMatch.Summary match = HeadlessMatch.play(1, FORTY_FIVE_MIN);
        assertThat(match.failure()).isEmpty();
        assertThat(match.winner()).isGreaterThanOrEqualTo(0);
        assertThat(match.ticks()).isLessThanOrEqualTo(FORTY_FIVE_MIN);
    }
}
