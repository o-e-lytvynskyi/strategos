package dev.strategos.core.sim;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Resolves persisted numeric building and production references through their saved stable ids. */
final class SavedTemplates {
    private SavedTemplates() {}

    static List<BuildingStore.Saved> buildings(MatchSnapshot snapshot, TemplateSet next) {
        Map<Integer, TemplateId> ids = new TreeMap<>();
        Set<String> distinct = new TreeSet<>();
        for (MatchSnapshot.TemplateBinding binding : snapshot.templateBindings()) {
            if (binding.index() < 0
                    || ids.putIfAbsent(binding.index(), binding.id()) != null
                    || !distinct.add(binding.id().value())) {
                throw new IllegalArgumentException("invalid saved template binding " + binding);
            }
        }
        boolean legacy = snapshot.templateBindings().isEmpty();
        return snapshot.buildings().stream()
                .map(saved -> {
                    if (!saved.alive()) {
                        return saved;
                    }
                    int building = resolve(saved.template(), ids, legacy, next, true);
                    BuildingStore.Production p = saved.production();
                    List<Integer> queue = p.queue().stream()
                            .map(index -> resolve(index, ids, legacy, next, false))
                            .toList();
                    return new BuildingStore.Saved(
                            saved.generation(),
                            true,
                            building,
                            saved.faction(),
                            saved.anchorX(),
                            saved.anchorZ(),
                            saved.rotation(),
                            saved.baseY(),
                            saved.state(),
                            saved.progress(),
                            saved.damage(),
                            saved.site(),
                            new BuildingStore.Production(
                                    queue, p.trained(), p.reserved(), p.waiting(), p.batches(), p.autoReseed()),
                            saved.rally(),
                            saved.field(),
                            saved.stage());
                })
                .toList();
    }

    private static int resolve(
            int index, Map<Integer, TemplateId> ids, boolean legacy, TemplateSet next, boolean building) {
        TemplateId id = ids.get(index);
        if (legacy) {
            // Built-in indices are seeded before custom ids in every server session. Custom indices
            // cannot be reconstructed: even equal content hashes do not describe the session table.
            TemplateSet builtin = TemplateSet.builtin();
            if (!builtin.has(index)) {
                throw new IllegalArgumentException("legacy save has ambiguous template index " + index
                        + "; its original session template table is required");
            }
            id = builtin.byIndex(index).id();
        }
        if (id == null) {
            throw new IllegalArgumentException("saved template index " + index + " has no stable id");
        }
        UnitTemplate template = next.byId(id);
        if (template == null || template.isBuilding() != building) {
            throw new IllegalArgumentException(
                    "saved " + (building ? "building" : "production") + " needs template " + id + " of the same kind");
        }
        return template.netIndex();
    }
}
