package dev.strategos.core.sim.formation;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FormationMove;
import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PageRef;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The matches of the formation tests (ORD-06): flat ground, with or without terrain, and units spawned in blocks. */
final class FormationFixtures {
    static final FactionSlot F0 = new FactionSlot(0);
    static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");

    final SimHarness h;
    final MatchState s;
    private final VoxelWorld world;

    private FormationFixtures(SimHarness h, VoxelWorld world) {
        this.h = h;
        this.s = KernelAccess.state(h.core());
        this.world = world;
    }

    /** A match 200 by 120 blocks; with {@code terrain} the flat floor is published, so units walk paths and avoid. */
    static FormationFixtures flat(boolean terrain) {
        return flat(terrain, null);
    }

    static FormationFixtures flat(boolean terrain, VoxelWorld world) {
        return flat(terrain, world, 2);
    }

    static FormationFixtures flat(boolean terrain, VoxelWorld world, int factions) {
        VoxelWorld w = world != null ? world : VoxelFixtures.flat(200, 120, 8, 1);
        SimHarness h = SimHarness.builder()
                .size(w.sizeX(), w.sizeZ())
                .yBand(0, w.height() - 1, 1)
                .factions(factions)
                .build();
        if (terrain) {
            h.publishAll(w);
        }
        h.step();
        return new FormationFixtures(h, w);
    }

    /** A match restored from {@code snapshot}, its terrain slices cut from this match's world. */
    SimHarness restore(MatchSnapshot snapshot) {
        Map<Long, byte[]> slices = new TreeMap<>();
        for (PageRef page : snapshot.pages()) {
            slices.put(
                    page.slice(), world.slice(new PageCoord(page.x(), page.z())).encode());
        }
        return SimHarness.restore(snapshot, s.templates(), slices::get);
    }

    /** {@code count} units of {@code template} in a block {@code columns} wide, {@code gap} apart, its corner at (x, z). */
    int[] block(TemplateId template, int count, int columns, double gap, double x, double z) {
        for (int k = 0; k < count; k++) {
            Vec2d at = new Vec2d(x + (k % columns) * gap, z + (k / columns) * gap);
            h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, F0, at, 1, 0));
        }
        int[] handles = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
        return handles;
    }

    /** The handles of {@code a}, then {@code b}. */
    static int[] both(int[] a, int[] b) {
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** The formation order for {@code units}, from the owner's player, stepped once; the formation it made. */
    FormationController march(int[] units, double x, double z, FormationShape shape) {
        h.submit(PlayerSlot.ADMIN, FormationMove.replace(UnitSet.of(units), new Vec2d(x, z), shape));
        h.step();
        return only();
    }

    /** The only formation of the match. */
    FormationController only() {
        FormationStore store = s.play().formations();
        if (store.count() != 1) {
            throw new AssertionError("expected one formation, found " + store.count());
        }
        return store.at(0);
    }

    double x(int handle) {
        return s.motion().x(Handle.index(handle));
    }

    double z(int handle) {
        return s.motion().z(Handle.index(handle));
    }

    /** How far member {@code k} of {@code c} stands from its place, now. */
    double error(FormationController c, int k) {
        return Math.hypot(x(c.member(k)) - c.slotX(k), z(c.member(k)) - c.slotZ(k));
    }

    /** The members of {@code c}, in place order. */
    static List<Integer> members(FormationController c) {
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k < c.size(); k++) {
            out.add(c.member(k));
        }
        return out;
    }

    /** The plain infantry template, for tests of a mixed group. */
    static TemplateId infantry() {
        return TemplateSet.INFANTRY;
    }
}
