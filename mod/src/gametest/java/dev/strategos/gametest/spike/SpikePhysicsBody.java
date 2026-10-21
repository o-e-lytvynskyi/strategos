package dev.strategos.gametest.spike;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Body L, a physics LivingEntity (spec 8.3): the full vanilla tick, walking forward at 0.25 towards its waypoint; it
 * turns between two waypoints when given both, and pushes and is pushed only in the {@code L+push} configuration.
 */
public final class SpikePhysicsBody extends LivingEntity {
    private static final float SPEED = 0.25F;

    private @Nullable Vec3 waypoint;
    private @Nullable Vec3 other;
    private boolean push;

    public SpikePhysicsBody(EntityType<? extends SpikePhysicsBody> type, Level level) {
        super(type, level);
    }

    /** Walks to {@code waypoint}; with {@code other} set, turns between the two on arrival. */
    void walk(@Nullable Vec3 waypoint, @Nullable Vec3 other) {
        this.waypoint = waypoint;
        this.other = other;
    }

    void setPush(boolean push) {
        this.push = push;
    }

    @Override
    protected void serverAiStep() {
        super.serverAiStep();
        Vec3 target = waypoint;
        if (target == null) {
            zza = 0;
            return;
        }
        double dx = target.x - getX();
        double dz = target.z - getZ();
        if (dx * dx + dz * dz < 1) {
            if (other == null) {
                zza = 0;
                return;
            }
            waypoint = other;
            other = target;
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setSpeed(SPEED);
        zza = 1;
    }

    @Override
    public boolean isPushable() {
        return push;
    }

    @Override
    protected void pushEntities() {
        if (push) {
            super.pushEntities();
        }
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
