package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.unitai.StateId;
import dev.strategos.core.sim.unitai.UnitAi;
import java.util.ArrayList;
import java.util.List;

/** Takes a match state's snapshot and rebuilds a state from one. */
final class Snapshots {
    private Snapshots() {}

    /**
     * The state at its current tick: everything a restore needs, units in index order. Path jobs in flight are joined,
     * so their results travel with the snapshot.
     */
    static MatchSnapshot take(MatchState s) {
        EntityStore entities = s.entities();
        List<UnitRecord> units = new ArrayList<>(entities.aliveCount());
        for (int i = 0; i < entities.highWater(); i++) {
            if (!entities.aliveAt(i)) {
                continue;
            }
            MotionStore motion = s.motion();
            OrderStore orders = s.orders();
            AiStateStore ai = s.ai();
            units.add(new UnitRecord(
                    entities.handleAt(i),
                    s.templates().byIndex(s.identity().templateIndex(i)).id(),
                    new FactionSlot(s.identity().faction(i)),
                    motion.x(i),
                    motion.y(i),
                    motion.z(i),
                    motion.yaw(i),
                    new AiStateStore.Saved(UnitAi.leaf(ai.code(i)), ai.data0(i), ai.data1(i)),
                    orders.kind(i) == OrderStore.MOVE ? new Vec2d(orders.targetX(i), orders.targetZ(i)) : null,
                    s.paths().unitPath(i),
                    motion.extra(i)));
        }
        return new MatchSnapshot(
                s.config(),
                s.tick(),
                s.templates().hash(),
                entities.slots(),
                units,
                List.copyOf(s.pendingCommands()),
                s.pageRefs(),
                s.paths().queue());
    }

    /**
     * A state at the snapshot's tick. Templates are looked up by id, so a changed set still loads; a unit whose template
     * the set lacks, or whose state is no leaf of the unit tree, throws, naming the unit. Each unit's machine is put back
     * as saved, without hooks. Terrain pages are rebuilt from {@code slices}; one whose slice is missing
     * stays unpublished with its slice hash known, and a slice that does not hash to its reference throws.
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
            StateId leaf = unit.ai().state();
            if (!leaf.equals(UnitAi.leaf(leaf.name()))) {
                throw new IllegalArgumentException("unit " + Handle.toString(unit.handle()) + " is in state " + leaf
                        + ", which is no leaf of the unit tree");
            }
            int index = Handle.index(unit.handle());
            state.identity().set(index, template.netIndex(), unit.faction().value());
            state.motion().set(index, unit.x(), unit.y(), unit.z(), unit.yaw());
            state.ai().restore(index, unit.ai());
            Vec2d target = unit.target();
            if (target != null) {
                state.orders().setMove(index, target.x(), target.z());
            } else {
                state.orders().clear(index);
            }
            state.paths().set(index, unit.path());
            state.motion().setExtra(index, unit.motion());
        }
        state.pendingCommands().addAll(snapshot.pendingCommands());
        int missing = 0;
        List<TerrainPage> pages = new ArrayList<>();
        for (PageRef ref : snapshot.pages()) {
            byte[] slice = slices.slice(ref.slice());
            if (slice == null) {
                state.expect(ref);
                missing++;
                continue;
            }
            TerrainPage page = TerrainPage.rebuild(new PageCoord(ref.x(), ref.z()), slice);
            if (page.sliceHash() != ref.slice()) {
                throw new IllegalArgumentException("the slice of page (" + ref.x() + ", " + ref.z() + ") hashes to "
                        + Long.toHexString(page.sliceHash()) + ", the snapshot says " + Long.toHexString(ref.slice()));
            }
            pages.add(page);
        }
        state.publishAll(pages);
        state.paths().restore(snapshot.paths()); // after the pages, whose publication must not count as a change
        state.spatial().rebuild(state); // as the saved tick's movement left it
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
