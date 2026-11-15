package dev.strategos.mod.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.registry.ModEntityTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.junit.jupiter.api.Test;

/** CMB-09.3: the siege body's type has ADR-0011's settings: 2.0 x 2.0, tracked within 12 chunks, an update every 3 ticks. */
class UnitLargeTypeTest {

    @Test
    void theSiegeUnitIsAHumanoidViewAtARamsSize() {
        EntityType<UnitEntity> type = ModEntityTypes.UNIT_LARGE.get();

        assertThat(BuiltInRegistries.ENTITY_TYPE.getKey(type)).isEqualTo(Ids.id("unit_large"));
        assertThat(type.getCategory()).isEqualTo(MobCategory.MISC);
        assertThat(type.canSerialize()).as("noSave").isFalse();
        assertThat(type.canSummon()).isFalse();
        assertThat(type.fireImmune()).isTrue();
        assertThat(type.clientTrackingRange()).isEqualTo(12);
        assertThat(type.updateInterval()).isEqualTo(3);
        assertThat(type.trackDeltas()).as("no velocity packets").isFalse();
        assertThat(type.getWidth()).isEqualTo(2.0F);
        assertThat(type.getHeight()).isEqualTo(2.0F);
        assertThat(DefaultAttributes.hasSupplier(type)).isTrue();
    }

    @Test
    void theRamsTemplateNamesIt() {
        TemplateSet set = TemplateSet.builtin();
        var ram = set.byId(new TemplateId("strategos:unit/ram"));

        assertThat(ram).isNotNull();
        assertThat(ModEntityTypes.forTemplate(set, ram.netIndex())).isSameAs(ModEntityTypes.UNIT_LARGE.get());
        assertThat(ram.bodyType()).isEqualTo("strategos:unit_large");
    }
}
