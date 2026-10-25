package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DATA-03.4 and .5 (spec §2.8): a {@code ContentSwap} at a tick boundary gives units their new values from that tick
 * on; it is journaled, and the run replays from its journal, through the disk format, to the same hash; a set that
 * lacks a live unit's template is refused and the match keeps its set.
 */
class ContentSwapReplayTest {
    private static final FactionSlot F0 = new FactionSlot(0);

    @Test
    void aSwapCarryingTechsSurvivesTheJournalFormat() {
        SimHarness h = harness();
        TemplateSet withTechs = withWalkerSpeed(0.5);
        assertThat(withTechs.techs()).as("the swap carries the builtin techs").hasSize(3);
        h.schedule(h.tick() + 1, new ContentSwap(withTechs));
        h.runTicks(3);

        JournalExport read = JournalFormat.decode(
                JournalFormat.encode(new JournalExport(h.core().journal(), h.tick(), h.stateHash())));

        List<TemplateSet> swapped = new ArrayList<>();
        read.journal()
                .nonEmptyTicks()
                .forEach(tick -> tick.events().stream()
                        .filter(ContentSwap.class::isInstance)
                        .forEach(e -> swapped.add(((ContentSwap) e).set())));
        assertThat(swapped).hasSize(1);
        assertThat(swapped.get(0).version()).isEqualTo(withTechs.version());
        assertThat(swapped.get(0).techs()).isEqualTo(withTechs.techs());
    }

    @Test
    void aSwapAppliesFromItsTickAndReplays() {
        SimHarness h = harness();
        int unit = h.spawn(F0, new Vec2d(4.5, 16.5), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(60.5, 16.5)));
        h.runTicks(10);
        TemplateSet faster = withWalkerSpeed(0.5);
        long swapAt = h.tick() + 1;
        h.schedule(swapAt, new ContentSwap(faster));
        List<Double> steps = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            double x = x(h, unit);
            h.step();
            steps.add(x(h, unit) - x);
        }
        h.runTicks(40);

        assertThat(steps.get(0)).as("the swap's own tick").isCloseTo(0.5, within(1e-9));
        assertThat(steps).allSatisfy(s -> assertThat(s).isCloseTo(0.5, within(1e-9)));
        assertThat(h.queries().templates().version()).isEqualTo(faster.version());
        JournalExport export = new JournalExport(h.core().journal(), h.tick(), h.stateHash());
        JournalExport read = JournalFormat.decode(JournalFormat.encode(export));
        assertThat(read.journal().nonEmptyTicks())
                .anySatisfy(tick -> assertThat(tick.events()).hasAtLeastOneElementOfType(ContentSwap.class));
        ReplayResult replay = ReplayDriver.replay(read.journal(), TemplateSet.builtin());
        assertThat(replay.matches()).isTrue();
        assertThat(replay.finalHash()).isEqualTo(h.stateHash());
    }

    @Test
    void aSetWithoutALiveUnitsTemplateIsRefused() {
        SimHarness h = harness();
        int unit = h.spawn(F0, new Vec2d(4.5, 16.5), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(60.5, 16.5)));
        h.runTicks(5);
        TemplateSet without = only(TemplateSet.INFANTRY);
        h.schedule(h.tick() + 1, new ContentSwap(without));
        double x = x(h, unit);
        h.step();

        assertThat(h.queries().templates().version())
                .isEqualTo(TemplateSet.builtin().version());
        assertThat(x(h, unit) - x).as("the old speed").isCloseTo(0.25, within(1e-9));
    }

    /** The builtin set with the debug walker at {@code speed}, at the same net indices. */
    private static TemplateSet withWalkerSpeed(double speed) {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : TemplateSet.builtin().all()) {
            JsonObject json = TemplateSet.builtin().flattened(t.id());
            assertThat(json).isNotNull();
            if (t.id().equals(TemplateSet.DEBUG_WALKER)) {
                json.getAsJsonObject("UnitMotion").addProperty("walkSpeed", speed);
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(
                flats,
                TemplateSet.builtin().techs().stream()
                        .map(TemplateCompiler.TechFlat::of)
                        .toList());
        assertThat(result.ok()).isTrue();
        return result.set();
    }

    private static TemplateSet only(dev.strategos.core.shared.TemplateId id) {
        UnitTemplate t = TemplateSet.builtin().byId(id);
        assertThat(t).isNotNull();
        JsonObject json = TemplateSet.builtin().flattened(id);
        assertThat(json).isNotNull();
        return TemplateCompiler.fromFlattened(List.of(new TemplateCompiler.Flat(id, t.netIndex(), json)), List.of())
                .set();
    }

    private static double x(SimHarness h, int unit) {
        UnitView u = h.queries().unit(unit);
        assertThat(u).isNotNull();
        return u.x();
    }

    private static SimHarness harness() {
        SimHarness h = SimHarness.builder().size(64, 32).build();
        h.publishAll(VoxelFixtures.flat(64, 32, 16, 1));
        return h;
    }
}
