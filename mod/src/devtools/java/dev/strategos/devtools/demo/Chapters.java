package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What the M9 chapters share: the village that reaches Town, placements, grants and the chapters' own checks. */
final class Chapters {
    /** The resources a faction is granted for a scene: more than the scene spends. */
    static final int PLENTY = 3_000;

    private Chapters() {}

    /**
     * Faction 0's village on plot ground: a civic centre at ({@code cx}, {@code cz}) and, south of it, two houses, a
     * storehouse, a farmstead and a barracks: the five village buildings Town asks for. All finished.
     */
    static void village(Stage st, double cx, double cz) {
        st.building(Ids.CIVIC_CENTRE, 0, cx, cz);
        st.building(Ids.HOUSE, 0, cx - 24, cz - 22);
        st.building(Ids.HOUSE, 0, cx - 12, cz - 22);
        st.building(Ids.STOREHOUSE, 0, cx, cz - 22);
        st.building(Ids.FARMSTEAD, 0, cx + 12, cz - 22);
        st.building(Ids.BARRACKS, 0, cx + 26, cz - 22);
    }

    /** Every resource of faction {@code faction}'s treasury topped up with {@link #PLENTY}. */
    static void grantAll(Stage st, int faction) {
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            st.admin(new Grant(new FactionSlot(faction), resource, PLENTY));
        }
    }

    /** Faction 0's first finished building of {@code template}, or null. */
    static @Nullable BuildingView finished(Stage st, int faction, TemplateId template) {
        for (BuildingView b : st.buildings(faction, template)) {
            if (b.state() == BuildingStore.COMPLETE) {
                return b;
            }
        }
        return null;
    }

    /** Faction 0's civic centre researches Town. */
    static void researchTown(Stage st) {
        BuildingView c = finished(st, 0, Ids.CIVIC_CENTRE);
        if (c == null) {
            throw new IllegalStateException("no finished civic centre to research Town in");
        }
        st.order(0, new Research(c.handle(), Ids.PHASE_TOWN));
    }

    /** Faction 0 places {@code template} with its middle at plot (dx, dz), {@code builders} sent to build it. */
    static void place(Stage st, TemplateId template, double dx, double dz, int[] builders) {
        int[] corner = st.corner(template, dx, dz);
        st.order(0, new PlaceBuilding(template, corner[0], corner[1], 0, UnitSet.of(builders), QueueMode.REPLACE));
    }

    /** How many units of faction {@code faction} sit garrisoned. */
    static int garrisoned(Stage st, int faction) {
        int n = 0;
        for (int h : st.q().unitHandlesOf(new FactionSlot(faction))) {
            var u = st.q().unit(h);
            if (u != null && u.state() == UnitState.GARRISONED && st.q().isAlive(h)) {
                n++;
            }
        }
        return n;
    }

    /** The chapter's own check: a failed one fails the chapter. */
    static void require(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
    }
}
