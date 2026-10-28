package dev.strategos.devtools.demo;

import dev.strategos.core.shared.TemplateId;

/** What the combat chapters ask of a battle. */
final class Fights {
    private Fights() {}

    /** Faction {@code faction}'s live units of {@code template}. */
    static int alive(DemoStage st, int faction, TemplateId template) {
        return st.handles(faction, template).length;
    }

    /** How many of faction {@code faction}'s live {@code template} units are below their most hit points. */
    static int hurt(DemoStage st, int faction, TemplateId template) {
        int n = 0;
        for (int h : st.handles(faction, template)) {
            if (st.q().hitPoints(h) < st.q().maxHitPoints(h)) {
                n++;
            }
        }
        return n;
    }
}
