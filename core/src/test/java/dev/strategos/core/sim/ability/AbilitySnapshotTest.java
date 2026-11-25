package dev.strategos.core.sim.ability;

import static dev.strategos.core.sim.ability.AbilityFixtures.F0;
import static dev.strategos.core.sim.ability.AbilityFixtures.P0;
import static dev.strategos.core.sim.ability.AbilityFixtures.spearman;
import static dev.strategos.core.sim.ability.AbilityFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.protocol.Target;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UseAbility;
import dev.strategos.core.shared.AbilityId;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.persist.SchemaVersion;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/**
 * UNIT-10.6: the abilities' state is the M9 kind of optional section of {@code play}: written only when something is in
 * it, read as empty from a save that has none, the schema unchanged; and a restore refuses rows that cannot be.
 */
class AbilitySnapshotTest {
    private static JsonObject encode(MatchSnapshot snapshot) {
        return MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot).getAsJsonObject();
    }

    private static MatchSnapshot decode(JsonObject json) {
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
    }

    private static SimHarness used() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("rally"), Target.none()));
        h.step();
        h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("mend"), Target.none()));
        h.step();
        return h;
    }

    @Test
    void theSectionIsWrittenWhenSomethingIsInItAndReadsBackEqual() {
        SimHarness h = used();
        MatchSnapshot saved = h.core().snapshot();
        JsonObject abilities = encode(saved).getAsJsonObject("play").getAsJsonObject("abilities");
        assertThat(abilities.getAsJsonArray("ids"))
                .hasSize(AbilityFixtures.set(AbilityFixtures.ALL).abilities().size());
        assertThat(abilities.getAsJsonArray("cooldowns")).hasSize(6);
        assertThat(abilities.getAsJsonArray("timed")).hasSize(3);
        MatchSnapshot read = decode(encode(saved));
        assertThat(read.play().abilities()).isEqualTo(saved.play().abilities());
        assertThat(read).isEqualTo(saved);
        assertThat(saved.play().abilities().ids())
                .as("the saving set's whole table, which the rows index into")
                .isEqualTo(AbilityFixtures.set(AbilityFixtures.ALL).abilities().stream()
                        .map(a -> a.id())
                        .toList());
    }

    @Test
    void aSaveWithoutTheSectionReadsAsEmptyAndKeepsTheSchema() {
        SimHarness h = AbilityFixtures.match();
        spearman(h, F0, 10, 16);
        JsonObject json = encode(h.core().snapshot());
        assertThat(json.has("play") && json.getAsJsonObject("play").has("abilities"))
                .isFalse();
        assertThat(decode(json).play().abilities().ids()).isEmpty();
        assertThat(SchemaVersion.CURRENT).isEqualTo(3);
        JsonObject withOutOtherSections = encode(used().core().snapshot());
        withOutOtherSections.getAsJsonObject("play").remove("abilities");
        assertThat(decode(withOutOtherSections).play().abilities()).isEqualTo(MatchSnapshot.Play.EMPTY.abilities());
    }

    @Test
    void aCooldownAloneIsASaveAndRestoresItsCooldown() {
        SimHarness h = AbilityFixtures.match();
        int a = spearman(h, F0, 10, 16);
        h.submit(P0, UseAbility.replace(UnitSet.of(a), new AbilityId("mend"), Target.none()));
        h.step();
        MatchSnapshot saved = decode(encode(h.core().snapshot()));
        assertThat(saved.play().abilities().timed()).isEmpty();
        SimHarness restored = SimHarness.restore(saved, AbilityFixtures.set(AbilityFixtures.ALL));
        assertThat(state(restored).play().abilities().readyAt(a, "mend"))
                .isEqualTo(state(h).play().abilities().readyAt(a, "mend"))
                .isPositive();
        assertThat(restored.stateHash()).isEqualTo(h.stateHash());
    }

    @Test
    void anAbilityTheSetLacksIsDroppedAndBadRowsAreRefused() {
        MatchSnapshot saved = used().core().snapshot();
        SimHarness fewer = SimHarness.restore(
                saved,
                AbilityFixtures.set("{\"mend\":{\"target\":\"none\",\"cooldown\":20,"
                        + "\"effects\":[{\"kind\":\"heal\",\"params\":{\"amount\":15}}]}}"));
        assertThat(state(fewer).play().abilities().timed().empty())
                .as("rally is not in that set")
                .isTrue();
        assertThat(state(fewer).play().abilities().empty()).isFalse(); // mend's cooldown stays

        JsonObject odd = encode(saved);
        odd.getAsJsonObject("play")
                .getAsJsonObject("abilities")
                .getAsJsonArray("cooldowns")
                .remove(0);
        assertThatThrownBy(() -> SimHarness.restore(decode(odd), AbilityFixtures.set(AbilityFixtures.ALL)))
                .hasMessageContaining("rows of three");

        JsonObject stranger = encode(saved);
        JsonArray timed =
                stranger.getAsJsonObject("play").getAsJsonObject("abilities").getAsJsonArray("timed");
        timed.set(0, new com.google.gson.JsonPrimitive(0x7FFFF)); // a handle nobody holds
        assertThatThrownBy(() -> SimHarness.restore(decode(stranger), AbilityFixtures.set(AbilityFixtures.ALL)))
                .hasMessageContaining("no live unit");

        JsonObject lost = encode(saved);
        lost.getAsJsonObject("play")
                .getAsJsonObject("abilities")
                .getAsJsonArray("timed")
                .set(1, new com.google.gson.JsonPrimitive(99));
        assertThatThrownBy(() -> SimHarness.restore(decode(lost), AbilityFixtures.set(AbilityFixtures.ALL)))
                .hasMessageContaining("no id at 99");
    }
}
