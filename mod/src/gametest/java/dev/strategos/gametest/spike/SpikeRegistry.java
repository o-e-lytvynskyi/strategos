package dev.strategos.gametest.spike;

import dev.strategos.mod.Strategos;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.platform.Ids;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;

/**
 * The spikes' own entity types and ticket controller, registered only when {@code strategos.spike} is set (and not
 * S9's), so no other run, L2 or production ever sees them (spec 6.10). Mod bus, which FML infers for these events.
 */
@EventBusSubscriber(modid = Strategos.MOD_ID)
public final class SpikeRegistry {
    public static final TicketController TICKETS = new TicketController(Ids.id("spike_tickets"));

    static @Nullable EntityType<SpikeMob> mob;
    static @Nullable EntityType<SpikePhysicsBody> physics;
    static @Nullable EntityType<UnitEntity> unitCreature;

    private SpikeRegistry() {}

    /** Not for S9 and QA-19.4, whose real clients join: a client without these types fails the registry sync. */
    static boolean active() {
        String spike = System.getProperty("strategos.spike", "");
        return !spike.isBlank() && !spike.equals("s9") && !spike.equals("qa19");
    }

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        if (!active()) {
            return;
        }
        event.register(Registries.ENTITY_TYPE, helper -> {
            mob = EntityType.Builder.<SpikeMob>of(SpikeMob::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .noSave()
                    .build("strategos:spike_mob");
            physics = EntityType.Builder.<SpikePhysicsBody>of(SpikePhysicsBody::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .noSave()
                    .build("strategos:spike_physics");
            unitCreature = EntityType.Builder.<UnitEntity>of(UnitEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .noSave()
                    .build("strategos:spike_unit_creature");
            helper.register(Ids.id("spike_mob"), mob);
            helper.register(Ids.id("spike_physics"), physics);
            helper.register(Ids.id("spike_unit_creature"), unitCreature);
        });
    }

    @SubscribeEvent
    static void onAttributes(EntityAttributeCreationEvent event) {
        if (!active()) {
            return;
        }
        event.put(require(mob), SpikeMob.attributes().build());
        event.put(require(physics), LivingEntity.createLivingAttributes().build());
        event.put(require(unitCreature), LivingEntity.createLivingAttributes().build());
    }

    @SubscribeEvent
    static void onRegisterTickets(RegisterTicketControllersEvent event) {
        if (active()) {
            event.register(TICKETS);
        }
    }

    static <T> T require(@Nullable T type) {
        if (type == null) {
            throw new IllegalStateException("the spike entity types are registered only when strategos.spike is set");
        }
        return type;
    }
}
