package dev.strategos.mod.entity;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jspecify.annotations.Nullable;

/**
 * The body of one core unit. A view: never saved (its type is {@code noSave}), created only by the body registry and
 * posed by the kinematic driver; the core owns its position. No physics, pushing, loot, riding, portals, damage or
 * equipment.
 */
public final class UnitEntity extends LivingEntity implements IEntityWithComplexSpawn {
    private @Nullable UnitSpawnData spawnData;
    private boolean removedByRegistry;

    public UnitEntity(EntityType<? extends UnitEntity> type, Level level) {
        super(type, level);
    }

    /** On the server, before {@code addFreshEntity}: which unit this body shows. */
    public void bind(UnitSpawnData data) {
        this.spawnData = data;
    }

    public boolean isBound() {
        return spawnData != null;
    }

    /** The server's binding, or what the client read from the spawn packet; null before either. */
    public @Nullable UnitSpawnData spawnData() {
        return spawnData;
    }

    /** The body registry removes this body itself, so its leave event is not a lost body. */
    public void markRemovedByRegistry() {
        removedByRegistry = true;
    }

    public boolean removedByRegistry() {
        return removedByRegistry;
    }

    /** Puts the body where the core says: position, and yaw for the body, the head and the entity. */
    public void applyPose(double x, double y, double z, float yaw) {
        setPos(x, y, z);
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    /** The client runs the vanilla tick for interpolation and the walk animation; on the server the core moves it. */
    @Override
    public void tick() {
        if (level().isClientSide()) {
            super.tick();
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Both sides: a client would otherwise query the players around it every tick. */
    @Override
    protected void pushEntities() {}

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    /** M0 has no damage model: nothing hurts a body. T7b reports the attempt to the match; CMB-01 (M2) routes it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {}

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

    /** The server's spawn packet; a body spawned unbound is a bug of the body registry. */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        UnitSpawnData data = spawnData;
        if (data == null) {
            throw new IllegalStateException("unit body " + getId() + " spawned without its spawn data");
        }
        UnitSpawnData.STREAM_CODEC.encode(buffer, data);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        spawnData = UnitSpawnData.STREAM_CODEC.decode(additionalData);
    }
}
