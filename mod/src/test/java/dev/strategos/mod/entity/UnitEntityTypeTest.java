package dev.strategos.mod.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.registry.ModEntityTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.junit.jupiter.api.Test;

/** UNIT-01.1: the unit body's type is a never-saved, never-summoned view with its own attributes (§1.6 row 29). */
class UnitEntityTypeTest {

    @Test
    void theHumanoidUnitIsAViewNeverSavedOrSummoned() {
        EntityType<UnitEntity> type = ModEntityTypes.UNIT_HUMANOID.get();

        assertThat(BuiltInRegistries.ENTITY_TYPE.getKey(type)).isEqualTo(Ids.id("unit_humanoid"));
        assertThat(type.getCategory()).isEqualTo(MobCategory.MISC);
        assertThat(type.canSerialize()).as("noSave, so getEncodeId is null").isFalse();
        assertThat(type.canSummon()).isFalse();
        assertThat(type.fireImmune()).isTrue();
        assertThat(type.clientTrackingRange()).isEqualTo(10);
        assertThat(type.updateInterval()).isEqualTo(3);
        assertThat(type.trackDeltas()).as("no velocity packets").isFalse();
        assertThat(type.getWidth()).isEqualTo(0.6F);
        assertThat(type.getHeight()).isEqualTo(1.8F);
    }

    @Test
    void hasItsAttributes() {
        // DefaultAttributes.validate() skips MISC types: without this a missing entry would crash only on creation.
        assertThat(DefaultAttributes.hasSupplier(ModEntityTypes.UNIT_HUMANOID.get()))
                .isTrue();
    }
}
