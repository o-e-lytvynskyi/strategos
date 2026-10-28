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

/** UNIT-06.2: the mounted body's type has ADR-0011's settings at a horse and rider's size. */
class UnitMountTypeTest {

    @Test
    void theMountedUnitIsAHumanoidViewAtAHorsesSize() {
        EntityType<UnitEntity> type = ModEntityTypes.UNIT_MOUNT.get();

        assertThat(BuiltInRegistries.ENTITY_TYPE.getKey(type)).isEqualTo(Ids.id("unit_mount"));
        assertThat(type.getCategory()).isEqualTo(MobCategory.MISC);
        assertThat(type.canSerialize()).as("noSave").isFalse();
        assertThat(type.canSummon()).isFalse();
        assertThat(type.fireImmune()).isTrue();
        assertThat(type.clientTrackingRange()).isEqualTo(10);
        assertThat(type.updateInterval()).isEqualTo(3);
        assertThat(type.trackDeltas()).as("no velocity packets").isFalse();
        assertThat(type.getWidth()).isEqualTo(1.4F);
        assertThat(type.getHeight()).isEqualTo(2.0F);
        assertThat(DefaultAttributes.hasSupplier(type)).isTrue();
    }

    @Test
    void theHorsemansTemplateNamesIt() {
        TemplateSet set = TemplateSet.builtin();
        var horseman = set.byId(new TemplateId("strategos:unit/horseman"));

        assertThat(horseman).isNotNull();
        assertThat(ModEntityTypes.forTemplate(set, horseman.netIndex())).isSameAs(ModEntityTypes.UNIT_MOUNT.get());
        assertThat(ModEntityTypes.forTemplate(set, 1)).isSameAs(ModEntityTypes.UNIT_HUMANOID.get());
    }
}
