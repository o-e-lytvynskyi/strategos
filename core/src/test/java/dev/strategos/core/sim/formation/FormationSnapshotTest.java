package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.persist.SchemaVersion;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * ORD-06.7: a formation is saved and hashed while one exists and not otherwise (the schema stays 3); a restore
 * through the codec re-creates the controllers and every member's state exactly, and the restored match walks on to
 * the hashes of the live one; a journal replay reaches the live hash.
 */
class FormationSnapshotTest {
    private static JsonObject encode(MatchSnapshot snapshot) {
        return MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot).getAsJsonObject();
    }

    private static MatchSnapshot decode(JsonObject json) {
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
    }

    /** Twenty spearmen and four archers on the march, 90 ticks in, on terrain (paths and avoidance saved too). */
    private static FormationFixtures marching() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] spears = f.block(TemplateSet.INFANTRY, 20, 5, 1.5, 20, 40);
        int[] archers = f.block(FormationFixtures.ARCHER, 4, 4, 1.5, 20, 48);
        f.march(FormationFixtures.both(spears, archers), 120, 50, FormationShape.BOX);
        f.h.runTicks(90);
        return f;
    }

    @Test
    void theSectionIsWrittenOnlyWhileAFormationExistsAndTheSchemaStays() {
        FormationFixtures empty = FormationFixtures.flat(false);
        empty.block(TemplateSet.INFANTRY, 4, 4, 1.5, 20, 40);
        JsonObject plain = encode(empty.h.core().snapshot());
        assertThat(plain.has("play") && plain.getAsJsonObject("play").has("formations"))
                .isFalse();
        assertThat(decode(plain).play().formations().formations()).isEmpty();

        FormationFixtures f = marching();
        JsonObject json = encode(f.h.core().snapshot());
        assertThat(json.getAsJsonObject("play").getAsJsonArray("formations")).hasSize(1);
        assertThat(SchemaVersion.CURRENT).isEqualTo(3);
        json.getAsJsonObject("play").remove("formations");
        assertThat(decode(json).play().formations()).isEqualTo(MatchSnapshot.Play.EMPTY.formations());
    }

    @Test
    void theHashMixesFormationsOnlyWhileOneExists() {
        FormationFixtures f = FormationFixtures.flat(false);
        int[] units = f.block(TemplateSet.INFANTRY, 8, 4, 1.5, 20, 40);
        long before = f.h.stateHash();
        f.march(units, 120, 50, FormationShape.BOX);
        assertThat(f.s.play().formations().empty()).isFalse();
        FormationController c = f.only();
        long marching = f.h.stateHash();
        f.h.step();
        assertThat(f.h.stateHash()).isNotEqualTo(marching);
        assertThat(marching).isNotEqualTo(before);
        assertThat(c.id()).isPositive();
    }

    @Test
    void aRestoreThroughTheCodecIsEqualAndWalksOnToTheLiveHashes() {
        FormationFixtures live = marching();
        MatchSnapshot saved = decode(encode(live.h.core().snapshot()));
        assertThat(saved).isEqualTo(live.h.core().snapshot());
        SimHarness restored = live.restore(saved);
        assertThat(restored.stateHash()).isEqualTo(live.h.stateHash());
        FormationStore a = live.s.play().formations();
        FormationStore b = KernelAccess.state(restored.core()).play().formations();
        assertThat(b.save()).isEqualTo(a.save());
        for (int k = 0; k < a.at(0).size(); k++) {
            int handle = a.at(0).member(k);
            int i = Handle.index(handle);
            assertThat(KernelAccess.state(restored.core()).ai().code(i))
                    .isEqualTo(live.s.ai().code(i));
            assertThat(KernelAccess.state(restored.core()).orders().saved(i))
                    .isEqualTo(live.s.orders().saved(i));
        }

        for (int t = 0; t < 8; t++) {
            live.h.runTicks(25);
            restored.runTicks(25);
            assertThat(restored.stateHash())
                    .as("after %d more ticks", 25 * (t + 1))
                    .isEqualTo(live.h.stateHash());
        }
        assertThat(b.at(0).along()).isEqualTo(a.at(0).along());
    }

    @Test
    void aRestoredFormationStillBreaksAndLosesMembers() {
        FormationFixtures live = marching();
        SimHarness restored = live.restore(decode(encode(live.h.core().snapshot())));
        int first = live.s.play().formations().at(0).member(0);
        var move = dev.strategos.core.protocol.Move.replace(
                dev.strategos.core.protocol.UnitSet.of(first), new dev.strategos.core.shared.Vec2d(30, 100));
        live.h.submit(dev.strategos.core.shared.PlayerSlot.ADMIN, move);
        restored.submit(dev.strategos.core.shared.PlayerSlot.ADMIN, move);
        live.h.runTicks(10);
        restored.runTicks(10);

        assertThat(live.s.play().formations().at(0).size()).isEqualTo(23);
        assertThat(restored.stateHash()).isEqualTo(live.h.stateHash());
    }

    @Test
    void aReplayOfTheJournalReachesTheLiveHash() {
        FormationFixtures f = marching();
        f.h.runTicks(60);

        assertThat(f.h.replay().matches()).isTrue();
    }
}
