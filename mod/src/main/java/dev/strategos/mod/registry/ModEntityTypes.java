package dev.strategos.mod.registry;

import dev.strategos.mod.Strategos;
import dev.strategos.mod.entity.UnitEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's entity types: M0's one unit body. */
public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Strategos.MOD_ID);

    /**
     * {@code MISC} keeps the natural spawner's per-entity work away; {@code noSave} keeps bodies out of chunk storage
     * (and lets {@code build} skip the datafixer lookup); no summoning, no fire, tracked within 10 chunks, a position
     * update every 3 ticks and no velocity packets, since the client interpolates.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<UnitEntity>> UNIT_HUMANOID = ENTITY_TYPES.register(
            "unit_humanoid",
            () -> EntityType.Builder.<UnitEntity>of(UnitEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .noSummon()
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("strategos:unit_humanoid"));

    /**
     * The animals' bodies (ECO-16): as a unit's, sized for the largest of them, a sheep; the renderer draws each by its
     * template's {@code Body/model}.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<UnitEntity>> UNIT_FAUNA = ENTITY_TYPES.register(
            "unit_fauna",
            () -> EntityType.Builder.<UnitEntity>of(UnitEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.3F)
                    .noSummon()
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("strategos:unit_fauna"));

    private ModEntityTypes() {}

    /** The body type template {@code netIndex} names in {@code Body/entityType}; a humanoid's by default. */
    public static EntityType<UnitEntity> forTemplate(dev.strategos.core.content.TemplateSet templates, int netIndex) {
        var path = templates.tokenPaths().byPath("Body/entityType");
        String type = path == null || !templates.has(netIndex) ? null : templates.text(netIndex, path.id());
        return "strategos:unit_fauna".equals(type) ? UNIT_FAUNA.get() : UNIT_HUMANOID.get();
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, ModEntityTypes::onAttributes);
    }

    /** {@code DefaultAttributes.validate()} skips MISC types, so a missing entry would only crash on creation. */
    static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(UNIT_HUMANOID.get(), LivingEntity.createLivingAttributes().build());
        event.put(UNIT_FAUNA.get(), LivingEntity.createLivingAttributes().build());
    }
}
