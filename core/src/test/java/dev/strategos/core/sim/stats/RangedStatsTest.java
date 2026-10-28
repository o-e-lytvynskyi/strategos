package dev.strategos.core.sim.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MatchBus;
import java.util.List;
import org.junit.jupiter.api.Test;

class RangedStatsTest {
    private static final FactionSlot F0 = new FactionSlot(0);

    @Test
    void absentRangedWeaponStaysUnavailableAndMeleeStillWorks() {
        StatService stats = service(template(null, true));
        assertThat(stats.canRanged(0)).isFalse();
        assertThat(stats.canMelee(0)).isTrue();
        assertThat(stats.canAttack(0)).isTrue();
        assertThat(stats.rangedRange(0)).isZero();
        for (int type = 0; type < 4; type++)
            assertThat(stats.rangedDamage(0, type)).isZero();
        assertThat(stats.rangedPrepare(0)).isEqualTo(16);
        assertThat(stats.rangedRepeat(0)).isEqualTo(25);
        assertThat(stats.rangedSpeed(0)).isEqualTo(4);
        assertThat(stats.rangedSpread(0)).isEqualTo(2.25);
    }

    @Test
    void typedRangedValuesCoverEveryDamageTypeAndRoundTickValues() {
        StatService stats = service(template("""
                {"hack":1,"pierce":2,"crush":3,"fire":4,"range":18,"prepare":16.6,
                 "repeat":25.6,"speed":5.5,"spread":1.25}
                """, false));
        assertThat(stats.canMelee(0)).isFalse();
        assertThat(stats.canRanged(0)).isTrue();
        assertThat(stats.canAttack(0)).isTrue();
        for (int type = 0; type < 4; type++)
            assertThat(stats.rangedDamage(0, type)).isEqualTo(type + 1);
        assertThat(stats.rangedRange(0)).isEqualTo(18);
        assertThat(stats.rangedPrepare(0)).isEqualTo(17);
        assertThat(stats.rangedRepeat(0)).isEqualTo(26);
        assertThat(stats.rangedSpeed(0)).isEqualTo(5.5);
        assertThat(stats.rangedSpread(0)).isEqualTo(1.25);
    }

    @Test
    void missingOptionalFieldsUseTypedDefaultsAndZeroRangeOrDamageDisables() {
        StatService stats = service(template("{" + "\"fire\":2}", false));
        assertThat(stats.rangedDamage(0, 3)).isEqualTo(2);
        assertThat(stats.rangedRange(0)).isZero();
        assertThat(stats.canRanged(0)).isFalse();
        stats.swap(template("{\"range\":20}", false));
        assertThat(stats.rangedRange(0)).isEqualTo(20);
        assertThat(stats.canRanged(0)).isFalse();
        assertThat(stats.canAttack(0)).isFalse();
    }

    @Test
    void modifiersAndReloadRebindTheTypedPaths() {
        StatService stats = service(template("{\"pierce\":2,\"range\":12}", false));
        stats.add(F0, Modifier.add(CorePaths.RANGED_PIERCE, 1, 1));
        stats.add(F0, Modifier.add(CorePaths.RANGED_RANGE, 200, 2));
        assertThat(stats.rangedDamage(0, 1)).isEqualTo(3);
        assertThat(stats.rangedRange(0)).isEqualTo(128);
        stats.remove(F0, 2);
        assertThat(stats.rangedRange(0)).isEqualTo(12);
        stats.swap(template("{\"fire\":5,\"range\":30}", false));
        assertThat(stats.rangedDamage(0, 1)).isEqualTo(1);
        assertThat(stats.rangedDamage(0, 3)).isEqualTo(5);
        assertThat(stats.rangedRange(0)).isEqualTo(30);
        assertThat(stats.canRanged(0)).isTrue();
    }

    @Test
    void excessiveModifierDamageClampsBeforeItsFloatPacket() {
        StatService stats = service(template("{\"pierce\":2,\"range\":12}", false));
        stats.add(F0, Modifier.add(CorePaths.RANGED_PIERCE, Double.MAX_VALUE, 3));
        assertThat(stats.rangedDamage(0, 1)).isEqualTo((double) Float.MAX_VALUE);
        assertThat((float) stats.rangedDamage(0, 1)).isFinite();
    }

    @Test
    void conflictingFiniteModifiersCannotCreateAnInvalidShotPacket() {
        StatService stats = service(template("{\"pierce\":2,\"range\":12,\"speed\":4}", false));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_PIERCE, Double.MAX_VALUE, 4));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_PIERCE, 0, 5));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_SPEED, Double.MAX_VALUE, 6));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_SPEED, 0, 7));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_RANGE, Double.MAX_VALUE, 8));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_RANGE, 0, 9));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_SPREAD, Double.MAX_VALUE, 10));
        stats.add(F0, Modifier.mul(CorePaths.RANGED_SPREAD, 0, 11));
        assertThat(stats.rangedDamage(0, 1)).isZero();
        assertThat(stats.rangedSpeed(0)).isEqualTo(0.01);
        assertThat(stats.rangedRange(0)).isZero();
        assertThat(stats.rangedSpread(0)).isZero();
        assertThat(stats.canRanged(0)).isFalse();
    }

    private static StatService service(TemplateSet set) {
        IdentityStore identity = new IdentityStore();
        identity.ensureCapacity(1);
        identity.set(0, 0, 0);
        return new StatService(set, 2, identity, new MatchBus());
    }

    private static TemplateSet template(String ranged, boolean melee) {
        JsonObject json = JsonParser.parseString("""
                {"Identity":{"classes":"Infantry"},"UnitMotion":{"walkSpeed":0.25},
                 "Body":{"entityType":"strategos:unit_humanoid","width":0.6,"height":1.8}}
                """).getAsJsonObject();
        JsonObject attack = new JsonObject();
        if (melee) attack.add("Melee", JsonParser.parseString("{\"hack\":2}").getAsJsonObject());
        if (ranged != null) attack.add("Ranged", JsonParser.parseString(ranged).getAsJsonObject());
        if (melee || ranged != null) json.add("Attack", attack);
        TemplateCompiler.Result result = TemplateCompiler.compile(
                List.of(new RawTemplate(new TemplateId("strategos:unit/test"), "test.json", json)));
        assertThat(result.ok()).as(result.problems().toString()).isTrue();
        return result.set();
    }
}
