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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The bodies of one match, and the only creator of {@link UnitEntity}s. A unit wants a body from its creation; bodies
 * are made in handle order, only where the level ticks entities, and only while the server tick's spawn budget lasts.
 * A body that leaves the level either parks its unit (unloaded with its chunk: bodiless, and wanted again once that
 * chunk is really gone, so the body comes back with it) or is wanted again at once (any other removal: back next
 * tick).
 */
public final class BodyRegistry {
    private final MatchId id;
    private final MatchSpace space;
    private final Int2ObjectRBTreeMap<UnitEntity> bodies = new Int2ObjectRBTreeMap<>();
    private final IntRBTreeSet wanted = new IntRBTreeSet();
    private final IntRBTreeSet parked = new IntRBTreeSet();
    private final IntRBTreeSet reported = new IntRBTreeSet();

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
        if (body.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            parked.add(handle);
            return false;
        }
        wanted.add(handle);
        return reported.add(handle);
    }

    /** Discards every body, marked so their leave events are ignored, and forgets every wish. */
    public void discardAll() {
        for (UnitEntity body : new ArrayList<>(bodies.values())) {
            body.markRemovedByRegistry();
            body.discard();
        }
        bodies.clear();
        wanted.clear();
        parked.clear();
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
        return level.addFreshEntity(body) ? body : null;
    }

    private BlockPos blockOf(UnitView unit) {
        return BlockPos.containing(space.worldX(unit.x()), unit.y(), space.worldZ(unit.z()));
    }
}
