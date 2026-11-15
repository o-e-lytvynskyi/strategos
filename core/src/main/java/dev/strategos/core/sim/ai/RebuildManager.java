package dev.strategos.core.sim.ai;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.DefenceLog;
import dev.strategos.core.sim.build.BuildingView;
import java.util.List;

/**
 * Puts a lost building up again (AI-04, 04 §14): a building the enemy destroyed (the match's log of the player's
 * losses, kept five minutes) is placed once more on the same corner and turn, with the two workers nearest, as soon
 * as the stock covers it, the site is free and no enemy has been at the door for {@value #CALM} ticks. A loss is
 * handled once an own building of its template stands at its corner, in any state, so the memory keeps only the tick
 * of the last placement ({@value #RETRY} ticks apart). One at a time, the oldest first.
 */
final class RebuildManager implements AiModule {
    /** Ticks without an enemy at the door before a rebuild starts: 10 seconds. */
    static final long CALM = 200;

    /** Ticks between two placements, as the build order's. */
    static final long RETRY = BuildManager.RETRY;

    private final AiMemory memory;

    RebuildManager(AiMemory memory) {
        this.memory = memory;
    }

    @Override
    public void think(AiView view, AiBudget budget, CommandSink sink) {
        long tick = view.tick();
        long placed = memory.get(Slots.REBUILD);
        long near = memory.get(Slots.THREAT);
        if (budget.exhausted()
                || (placed != 0 && tick - (placed - 1) < RETRY)
                || (near != 0 && tick - (near - 1) < CALM)) {
            return;
        }
        List<DefenceLog.Loss> losses = view.losses();
        if (losses.isEmpty()) {
            return;
        }
        List<BuildingView> own = view.buildings();
        for (DefenceLog.Loss l : losses) {
            TemplateId id = view.templateId(l.template());
            UnitTemplate t = id == null ? null : view.templates().byId(id);
            Blueprint bp = t == null ? null : view.templates().blueprint(t.netIndex());
            if (id == null || bp == null || standsAgain(own, id, l)) {
                continue;
            }
            if (!view.affords(id) || view.placementRefusal(id, l.x(), l.z()) != null) {
                continue; // not yet, or the site is taken: the next loss may do
            }
            Footprint f = Footprint.of(bp, l.x(), l.z(), l.rotation());
            List<Integer> builders =
                    BuildManager.nearestWorkers(view, f.anchorX() + f.spanX() / 2.0, f.anchorZ() + f.spanZ() / 2.0, 2);
            if (builders.isEmpty() || !budget.spend(20)) {
                return;
            }
            sink.send(new PlaceBuilding(
                    id,
                    l.x(),
                    l.z(),
                    l.rotation(),
                    UnitSet.of(builders.stream().mapToInt(Integer::intValue).toArray()),
                    QueueMode.REPLACE));
            memory.set(Slots.REBUILD, tick + 1);
            return;
        }
    }

    private static boolean standsAgain(List<BuildingView> own, TemplateId id, DefenceLog.Loss l) {
        for (BuildingView b : own) {
            Footprint f = b.footprint();
            if (b.template().equals(id) && f.anchorX() == l.x() && f.anchorZ() == l.z()) {
                return true;
            }
        }
        return false;
    }
}
