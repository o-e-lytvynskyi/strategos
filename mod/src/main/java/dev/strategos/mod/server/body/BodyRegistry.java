package dev.strategos.mod.server.body;

import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.registry.ModEntityTypes;
import dev.strategos.mod.server.match.MatchSpace;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The bodies of one match, and the only creator of {@link UnitEntity}s. A unit wants a body from its creation; bodies
 * are made in handle order, only where the level ticks entities, and only while the server tick's spawn budget lasts;
 * a body whose unit walks out of there is retired. A body that leaves the level is handled by how it left:
 *
 * <ul>
 *   <li>its section stopped being tracked, with no removal reason: its chunk is unloading, or it was moved into a
 *       chunk that is not loaded. The entity is still in the level, out of sight; it is discarded at the next
 *       materialise, outside the event, and the unit quietly waits for a body where entities tick;
 *   <li>removed with {@code UNLOADED_TO_CHUNK} (only an explicit removal; the engine sets that reason after the leave
 *       event): the unit is parked, bodiless, until its position is really gone;
 *   <li>removed for any other reason: the unit gets a body again next tick, and the first such loss per unit is
 *       logged.
 * </ul>
 */
public final class BodyRegistry {
    private final MatchId id;
    private final MatchSpace space;
    private final Int2ObjectRBTreeMap<UnitEntity> bodies = new Int2ObjectRBTreeMap<>();
    private final IntRBTreeSet wanted = new IntRBTreeSet();
    private final IntRBTreeSet parked = new IntRBTreeSet();
    private final IntRBTreeSet reported = new IntRBTreeSet();
    private final List<UnitEntity> stale = new ArrayList<>();
    private final List<UnitEntity> dying = new ArrayList<>();

    public BodyRegistry(MatchId id, MatchSpace space) {
        this.id = id;
        this.space = space;
    }

    public @Nullable UnitEntity get(int handle) {
        return bodies.get(handle);
    }

    public int count() {
        return bodies.size();
    }

    public int wantedCount() {
        return wanted.size();
    }

    public void want(int handle) {
        wanted.add(handle);
    }

    /** Creates the bodies the units want, as far as the world and the spawn budget allow. */
    public void materialise(ServerLevel level, MatchQueries q, SpawnBudget budget) {
        discardStale();
        IntIterator parkedHandles = parked.iterator();
        while (parkedHandles.hasNext()) {
            int handle = parkedHandles.nextInt();
            UnitView unit = q.unit(handle);
            if (unit == null) {
                parkedHandles.remove();
            } else if (!level.isPositionEntityTicking(blockOf(unit))) {
                parkedHandles.remove(); // its chunk really unloaded: the body comes back with the chunk
                wanted.add(handle);
            }
        }
        IntIterator wantedHandles = wanted.iterator();
        while (wantedHandles.hasNext()) {
            int handle = wantedHandles.nextInt();
            UnitView unit = q.unit(handle);
            if (unit == null || bodies.containsKey(handle)) {
                wantedHandles.remove();
                continue;
            }
            if (!level.isPositionEntityTicking(blockOf(unit))) {
                continue; // later, when its chunk ticks entities
            }
            if (!budget.tryTake()) {
                return;
            }
            UnitEntity body = spawn(level, handle, unit);
            if (body != null) {
                bodies.put(handle, body);
                wantedHandles.remove();
            }
        }
    }

    /**
     * A body left the level. Bodies this registry removed are ignored; one unloaded with its chunk parks its unit,
     * quietly; any other removal wants the unit's body again. Returns true the first time a handle loses its body that
     * other way, for the caller to log once.
     */
    public boolean onLeft(UnitEntity body) {
        UnitSpawnData data = body.spawnData();
        // Entity#equals compares entity ids: a stale body of the same handle is not the registered one.
        if (body.removedByRegistry() || data == null || !body.equals(bodies.get(data.handle()))) {
            return false;
        }
        int handle = data.handle();
        bodies.remove(handle);
        Entity.RemovalReason reason = body.getRemovalReason();
        if (reason == null) {
            // Not removed: its section stopped being tracked. Discarding it here would change the section the engine
            // is streaming, so it waits for the next materialise; left alone, a returning chunk would show it again.
            stale.add(body);
            wanted.add(handle);
            return false;
        }
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            parked.add(handle);
            return false;
        }
        wanted.add(handle);
        return reported.add(handle);
    }

    /** The unit's next pose leaves where entities tick: its body goes quietly, and the unit wants one again. */
    public void retire(int handle) {
        UnitEntity body = bodies.remove(handle);
        if (body != null) {
            body.markRemovedByRegistry();
            body.discard();
        }
        wanted.add(handle);
    }

    /**
     * The unit died (CMB-01): its body leaves the registry and plays vanilla's death, health 0 and {@code die}, which
     * sends the death event and pose to its trackers; {@link #tickDying} runs its death ticks until vanilla removes it.
     */
    public void die(int handle, DamageSource source) {
        wanted.remove(handle);
        parked.remove(handle);
        UnitEntity body = bodies.remove(handle);
        if (body != null) {
            body.markRemovedByRegistry();
            body.dieAsUnit(source);
            dying.add(body);
        }
    }

    /** One death tick for each dying body; vanilla removes one at its twentieth. */
    public void tickDying() {
        dying.removeIf(body -> {
            if (!body.isRemoved()) {
                body.driveDeath();
            }
            return body.isRemoved();
        });
    }

    /** Bodies still playing their death, for tests. */
    public int dyingCount() {
        return dying.size();
    }

    /** Discards every body, marked so their leave events are ignored, and forgets every wish. */
    public void discardAll() {
        for (UnitEntity body : new ArrayList<>(bodies.values())) {
            body.markRemovedByRegistry();
            body.discard();
        }
        bodies.clear();
        for (UnitEntity body : dying) {
            body.discard();
        }
        dying.clear();
        discardStale();
        wanted.clear();
        parked.clear();
    }

    /** Removes the bodies whose sections stopped being tracked; those the engine already unloaded are just dropped. */
    private void discardStale() {
        for (UnitEntity body : stale) {
            if (!body.isRemoved()) {
                body.markRemovedByRegistry();
                body.discard();
            }
        }
        stale.clear();
    }

    private @Nullable UnitEntity spawn(ServerLevel level, int handle, UnitView unit) {
        UnitEntity body = ModEntityTypes.UNIT_HUMANOID.get().create(level);
        if (body == null) {
            return null; // the type is disabled in this level
        }
        body.bind(new UnitSpawnData(
                id.value(), handle, unit.templateIndex(), unit.faction().value(), UnitSpawnData.KINEMATIC));
        body.moveTo(space.worldX(unit.x()), unit.y(), space.worldZ(unit.z()), unit.yaw(), 0);
        body.setYHeadRot(unit.yaw());
        body.setYBodyRot(unit.yaw());
        body.setOnGround(true); // the tracker records it at creation and sends a teleport on every flip
        // The spawn packet carries the synched values the constructor set (health); left dirty, the tracker's first
        // update would send them again, one entity-data packet per body.
        var unused = body.getEntityData().packDirty();
        return level.addFreshEntity(body) ? body : null;
    }

    private BlockPos blockOf(UnitView unit) {
        return BlockPos.containing(space.worldX(unit.x()), unit.y(), space.worldZ(unit.z()));
    }
}
