package dev.strategos.mod.entity;

import dev.strategos.mod.server.body.BodyEvents;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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

    private boolean selectedOnClient;

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

    /** How far a body renders (UNIT-03): 192 blocks, the RTS camera's reach at its highest. */
    static final double RENDER_DISTANCE = 192;

    /** On a client: whether the player has it selected, which draws its outline (UNIT-03); the client's code sets it. */
    public void selectedOnClient(boolean selected) {
        this.selectedOnClient = selected;
    }

    /** A selected body glows on its own client only: never a scoreboard team, never the server's glowing flag. */
    @Override
    public boolean isCurrentlyGlowing() {
        return level().isClientSide() ? selectedOnClient : super.isCurrentlyGlowing();
    }

    /** The outline's colour: its faction's (blue, red, green, yellow, purple, orange, cyan; beyond them, white). */
    @Override
    public int getTeamColor() {
        UnitSpawnData data = spawnData;
        return switch (data == null ? -1 : data.faction()) {
            case 0 -> 0x3B82F6;
            case 1 -> 0xEF4444;
            case 2 -> 0x22C55E;
            case 3 -> 0xEAB308;
            case 4 -> 0xA855F7;
            case 5 -> 0xF97316;
            case 6 -> 0x06B6D4;
            default -> 0xF5F5F5;
        };
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
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

    /**
     * Nothing hurts a body: the damage becomes the core's {@code ExternalDamage} input (UNIT-09), which CMB-01 (M2)
     * applies; until then a unit takes none, and false tells vanilla the blow did nothing.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) {
            BodyEvents.onHurt(this, source, amount); // inside the match's fault barrier
        }
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    /**
     * The unit died in the core (CMB-01): vanilla health, full until now, drops to 0 and {@code die} sends the death
     * event and the dying pose to the trackers, which play the fall; no loot drops ({@link #shouldDropLoot}).
     */
    public void dieAsUnit(DamageSource source) {
        setHealth(0);
        die(source);
    }

    /** One tick of vanilla's death, which the server's body never runs itself; at the twentieth it is removed. */
    public void driveDeath() {
        tickDeath();
    }

    /** The fog gate (NET-01): another match's member is never paired with this body; an unbound body with no one. */
    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        UnitSpawnData data = spawnData;
        return data != null && BodyEvents.canSee(data.matchId(), player);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        UnitSpawnData data = spawnData;
        if (data != null) {
            BodyEvents.paired(player, data, true);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        UnitSpawnData data = spawnData;
        if (data != null) {
            BodyEvents.paired(player, data, false);
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
