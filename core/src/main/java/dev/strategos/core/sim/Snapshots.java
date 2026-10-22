package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.nav.PageCoord;
import java.util.ArrayList;
import java.util.List;

/** Takes a match state's snapshot and rebuilds a state from one. */
final class Snapshots {
    private Snapshots() {}

    /** The state at its current tick: everything a restore needs, units in index order. */
    static MatchSnapshot take(MatchState s) {
        EntityStore entities = s.entities();
        List<UnitRecord> units = new ArrayList<>(entities.aliveCount());
        for (int i = 0; i < entities.highWater(); i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            MotionStore motion = s.motion();
            OrderStore orders = s.orders();
            units.add(new UnitRecord(
                    entities.handleAt(i),
                    s.templates().byIndex(s.identity().templateIndex(i)).id(),
                    new FactionSlot(s.identity().faction(i)),
                    motion.x(i),
                    motion.y(i),
                    motion.z(i),
                    motion.yaw(i),
                    s.ai().state(i),
                    orders.kind(i) == OrderStore.MOVE ? new Vec2d(orders.targetX(i), orders.targetZ(i)) : null));
        }
        return new MatchSnapshot(
                s.config(),
                s.tick(),
                s.templates().hash(),
                entities.slots(),
                units,
                List.copyOf(s.pendingCommands()),
                s.pageRefs());
    }

    /**
     * A state at the snapshot's tick. Templates are looked up by id, so a changed set still loads; a unit whose template
     * the set lacks throws, naming the unit. Terrain pages are rebuilt from {@code slices}; one whose slice is missing
     * stays unpublished, and a slice that does not hash to its reference throws.
     */
    static MatchState restore(MatchSnapshot snapshot, TemplateSet templates, MatchLog log, SliceSource slices) {
        int[] alive = new int[snapshot.units().size()];
        for (int i = 0; i < alive.length; i++) {
            alive[i] = snapshot.units().get(i).handle();
        }
        EntityStore entities = EntityStore.restore(snapshot.slots(), alive);
        MatchState state = new MatchState(snapshot.config(), templates, entities, log);
        state.setTick(snapshot.tick());
        for (UnitRecord unit : snapshot.units()) {
            UnitTemplate template = templates.byId(unit.template());
            if (template == null) {
                throw new IllegalArgumentException("unit " + Handle.toString(unit.handle()) + " needs template "
                        + unit.template() + ", which the template set lacks");
            }
            int index = Handle.index(unit.handle());
            state.identity().set(index, template.netIndex(), unit.faction().value());
            state.motion().set(index, unit.x(), unit.y(), unit.z(), unit.yaw());
            state.ai().set(index, unit.state());
            Vec2d target = unit.target();
            if (target != null) {
                state.orders().setMove(index, target.x(), target.z());
            } else {
                state.orders().clear(index);
            }
        }
        state.pendingCommands().addAll(snapshot.pendingCommands());
        int missing = 0;
        for (PageRef ref : snapshot.pages()) {
            byte[] slice = slices.slice(ref.slice());
            if (slice == null) {
                missing++;
                continue;
            }
            TerrainPage page = TerrainPage.rebuild(new PageCoord(ref.x(), ref.z()), slice);
            if (page.sliceHash() != ref.slice()) {
                throw new IllegalArgumentException("the slice of page (" + ref.x() + ", " + ref.z() + ") hashes to "
                        + Long.toHexString(page.sliceHash()) + ", the snapshot says " + Long.toHexString(ref.slice()));
            }
            state.publish(page);
        }
        if (missing > 0) {
            log.info(
                    snapshot.tick(),
                    "{} of {} terrain pages restored without their slices",
                    missing,
                    snapshot.pages().size());
        }
        if (snapshot.templateSetHash() != templates.hash()) {
            log.warn(
                    snapshot.tick(),
                    "restored with a different template set: saved {}, now {}",
                    Long.toHexString(snapshot.templateSetHash()),
                    Long.toHexString(templates.hash()));
        }
        return state;
    }
}
