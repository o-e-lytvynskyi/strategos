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

/** The mod's entity types: the unit bodies, by size. */
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

    /**
     * The mounted units' bodies (UNIT-06): the humanoid's settings at a horse and rider's size, still one entity per
     * unit with no passenger and no vanilla horse; the renderer composes the horse and its rider.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<UnitEntity>> UNIT_MOUNT = ENTITY_TYPES.register(
            "unit_mount",
            () -> EntityType.Builder.<UnitEntity>of(UnitEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.0F)
                    .noSummon()
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("strategos:unit_mount"));

    /**
     * The siege units' bodies (CMB-09): the humanoid's settings at a ram's 2 x 2, tracked a chunk farther (12) so a long
     * frame never pops in at the edge; the renderer draws a ram from cubes of its own, and the entity's culling box is
     * wider than its hit box for the log that reaches out of it.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<UnitEntity>> UNIT_LARGE = ENTITY_TYPES.register(
            "unit_large",
            () -> EntityType.Builder.<UnitEntity>of(UnitEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F)
                    .noSummon()
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("strategos:unit_large"));

    private ModEntityTypes() {}

    /** The body type template {@code netIndex} names in {@code Body/entityType}; a humanoid's by default. */
    public static EntityType<UnitEntity> forTemplate(dev.strategos.core.content.TemplateSet templates, int netIndex) {
        var path = templates.tokenPaths().byPath("Body/entityType");
        String type = path == null || !templates.has(netIndex) ? null : templates.text(netIndex, path.id());
        if ("strategos:unit_mount".equals(type)) {
            return UNIT_MOUNT.get();
        }
        if ("strategos:unit_large".equals(type)) {
            return UNIT_LARGE.get();
        }
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
        event.put(UNIT_MOUNT.get(), LivingEntity.createLivingAttributes().build());
        event.put(UNIT_LARGE.get(), LivingEntity.createLivingAttributes().build());
    }
}
