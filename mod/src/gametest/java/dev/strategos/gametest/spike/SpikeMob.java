package dev.strategos.gametest.spike;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;

/**
 * Body P, a stripped PathfinderMob (spec 8.3): no goals, never despawns, never pushes or is pushed, no movement
 * emission; it walks with its navigation and fights with vanilla melee. MAX_HEALTH is clamped by vanilla to 1,024.
 */
public final class SpikeMob extends PathfinderMob {
    public SpikeMob(EntityType<? extends SpikeMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.MAX_HEALTH, 1_000_000);
    }

    @Override
    protected void registerGoals() {}

    /** Real vanilla melee against {@code opponent}. */
    void fight(LivingEntity opponent) {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        setTarget(opponent);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {}

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }
}
