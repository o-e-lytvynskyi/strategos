package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a {@link Requirement} tree against a faction (TECH-01): pure, for the server's rules, the phase panel and
 * the AI alike. {@code all([])} is met, {@code any([])} is not.
 */
@CoreApi
public final class Requirements {
    private Requirements() {}

    /** One leaf of the checklist: whether it holds and the amounts the panel shows. */
    public record Leaf(Requirement leaf, boolean met, int have, int need) {}

    public static boolean evaluate(Requirement req, FactionView view) {
        return switch (req) {
            case Requirement.All all -> all.of().stream().allMatch(r -> evaluate(r, view));
            case Requirement.Any any -> any.of().stream().anyMatch(r -> evaluate(r, view));
            case Requirement.Tech t -> view.researched(t.tech());
            case Requirement.Phase p -> view.reached(p.name());
            case Requirement.Buildings b -> view.completeBuildings(b.filter()) >= b.count();
        };
    }

    /** The tech, phase and building leaves of {@code req}, depth first, each with its own met flag and amounts. */
    public static List<Leaf> checklist(Requirement req, FactionView view) {
        List<Leaf> out = new ArrayList<>();
        collect(req, view, out);
        return out;
    }

    private static void collect(Requirement req, FactionView view, List<Leaf> out) {
        switch (req) {
            case Requirement.All all -> all.of().forEach(r -> collect(r, view, out));
            case Requirement.Any any -> any.of().forEach(r -> collect(r, view, out));
            case Requirement.Tech t -> {
                boolean met = view.researched(t.tech());
                out.add(new Leaf(t, met, met ? 1 : 0, 1));
            }
            case Requirement.Phase p -> {
                boolean met = view.reached(p.name());
                out.add(new Leaf(p, met, met ? 1 : 0, 1));
            }
            case Requirement.Buildings b -> {
                int have = view.completeBuildings(b.filter());
                out.add(new Leaf(b, have >= b.count(), have, b.count()));
            }
        }
    }
}
