package dev.strategos.core.client.hud;

import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.Requirement;
import dev.strategos.core.content.Requirements;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.TokenLists;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.QueueEntry;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The technology tree as the screen shows it (TECH-05): the phases as columns, the technologies grouped into lines (a
 * tier chain by {@code supersedes}, a pair by {@code excludes}) as rows, each with the state the viewer's faction has
 * it in. Pure: the screen paints it, and the research stays on the buildings' cards.
 */
@CoreApi
public record TechTreeModel(List<Column> columns, List<Row> rows) {
    /** Where a technology stands for the viewer's faction. */
    public enum State {
        RESEARCHED,
        RESEARCHING,
        AVAILABLE,
        /** A phase it needs is not reached yet. */
        NEEDS_PHASE,
        /** The previous tier, or another technology it needs, is not researched yet. */
        NEEDS_PREVIOUS,
        /** Buildings it needs are not standing yet. */
        NEEDS_BUILDINGS,
        /** Its pair is researched or queued. */
        LOCKED,
        /** Not in the viewer's civilisation's roster (CIV-01). */
        OUTSIDE_ROSTER
    }

    /** A phase: its column's header, the phase technology's own state, and the name's language key. */
    public record Column(int ordinal, String nameKey, State state) {}

    /**
     * One technology: its state, the pair's name when {@code LOCKED}, the building template that researches it for the
     * viewer (null for none, a phase's included), its cost and time, and its effect summary for the tooltip.
     */
    public record Cell(
            TechDef tech,
            State state,
            @Nullable String lockedBy,
            @Nullable String researcher,
            Map<String, Double> cost,
            double time,
            List<String> effects) {}

    /** A line of the tree: a name key and, per column, the technologies standing in it. */
    public record Row(String line, List<List<Cell>> cells) {}

    public TechTreeModel {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    /** The tree for {@code view}'s faction; {@code buildings} are the faction's, for what is queued and who researches. */
    public static TechTreeModel of(
            TemplateSet templates, ClientFactionView view, @Nullable CivDef civ, List<BuildingState.Entry> buildings) {
        List<Integer> queued = new ArrayList<>();
        for (BuildingState.Entry b : buildings) {
            if (b.own()) {
                queued.addAll(b.queue());
            }
        }
        List<TechDef> phases = templates.techs().stream()
                .filter(TechDef::isPhase)
                .sorted(Comparator.comparingInt(TechDef::ordinal))
                .toList();
        List<Column> columns = new ArrayList<>();
        for (TechDef phase : phases) {
            columns.add(new Column(
                    phase.ordinal(),
                    PhasePanel.phaseKey(civ, phase.ordinal()),
                    state(templates, view, civ, queued, phase)));
        }
        Map<Integer, List<TechDef>> lines = lines(templates);
        List<Row> rows = new ArrayList<>();
        for (List<TechDef> line : lines.values()) {
            List<List<Cell>> cells = new ArrayList<>();
            for (int c = 0; c < columns.size(); c++) {
                cells.add(new ArrayList<>());
            }
            for (TechDef tech : line) {
                int column = Math.min(columns.size() - 1, Math.max(0, phaseOf(templates, tech.requires())));
                cells.get(column).add(cell(templates, view, civ, queued, tech));
            }
            rows.add(new Row(lineName(line.get(0)), cells));
        }
        return new TechTreeModel(columns, rows);
    }

    /** A line's name: its first technology's id without the tier number, {@code gather_wood} for {@code gather_wood_1}. */
    private static String lineName(TechDef first) {
        String id = first.id().value();
        return id.substring(id.indexOf(':') + 1).replaceAll("_\\d+$", "");
    }

    private static Cell cell(
            TemplateSet templates, ClientFactionView view, @Nullable CivDef civ, List<Integer> queued, TechDef tech) {
        State state = state(templates, view, civ, queued, tech);
        return new Cell(
                tech,
                state,
                state == State.LOCKED ? CommandCard.lockedBy(templates, view, queued, tech) : null,
                researcher(templates, view, civ, tech),
                tech.cost(),
                tech.time(),
                CommandCard.techEffects(tech));
    }

    /**
     * The first building template whose research list names {@code tech} once every phase is reached (what a tooltip
     * wants before Town is in), null for none; the faction's own research counts as well.
     */
    private static @Nullable String researcher(
            TemplateSet templates, ClientFactionView view, @Nullable CivDef civ, TechDef tech) {
        List<Integer> reached = new ArrayList<>(view.researchedIndexes());
        for (TechDef t : templates.techs()) {
            if (t.isPhase() && !reached.contains(t.netIndex())) {
                reached.add(t.netIndex());
            }
        }
        for (int t : CommandCard.buildings(templates)) {
            if (TokenLists.effective(templates, t, "Researcher/techs", civ, reached)
                    .contains(tech.id().value())) {
                return templates.byIndex(t).name();
            }
        }
        return null;
    }

    private static State state(
            TemplateSet templates, ClientFactionView view, @Nullable CivDef civ, List<Integer> queued, TechDef tech) {
        if (civ != null && !civ.roster().hasTech(tech.id())) {
            return State.OUTSIDE_ROSTER;
        }
        if (view.researched(tech.id())) {
            return State.RESEARCHED;
        }
        if (queued.contains(QueueEntry.techEntry(tech.netIndex()))) {
            return State.RESEARCHING;
        }
        if (CommandCard.lockedBy(templates, view, queued, tech) != null) {
            return State.LOCKED;
        }
        if (Requirements.evaluate(tech.requires(), view)) {
            return State.AVAILABLE;
        }
        boolean phase = false;
        boolean previous = false;
        for (Requirements.Leaf leaf : Requirements.checklist(tech.requires(), view)) {
            phase |= !leaf.met() && leaf.leaf() instanceof Requirement.Phase;
            previous |= !leaf.met() && leaf.leaf() instanceof Requirement.Tech;
        }
        // a phase first: the broadest gap
        return phase ? State.NEEDS_PHASE : previous ? State.NEEDS_PREVIOUS : State.NEEDS_BUILDINGS;
    }

    /** The phase ordinal a requirement waits for: the highest of an {@code all}, the lowest of an {@code any}. */
    private static int phaseOf(TemplateSet templates, Requirement req) {
        return switch (req) {
            case Requirement.Phase p -> {
                TechDef phase = templates.tech(new TechId("strategos:phase_" + p.name()));
                yield phase == null ? 0 : phase.ordinal();
            }
            case Requirement.All all ->
                all.of().stream().mapToInt(r -> phaseOf(templates, r)).max().orElse(0);
            case Requirement.Any any ->
                any.of().stream().mapToInt(r -> phaseOf(templates, r)).min().orElse(0);
            default -> 0;
        };
    }

    /**
     * The non-phase technologies grouped into lines by net index of their first member: techs joined by
     * {@code supersedes} or {@code excludes} share one, each line's techs in tier then net-index order.
     */
    private static Map<Integer, List<TechDef>> lines(TemplateSet templates) {
        List<TechDef> techs =
                templates.techs().stream().filter(t -> !t.isPhase()).toList();
        Map<Integer, Integer> parent = new TreeMap<>();
        for (TechDef t : techs) {
            parent.put(t.netIndex(), t.netIndex());
        }
        for (TechDef t : techs) {
            if (t.supersedes() != null) {
                union(parent, t, templates.tech(t.supersedes()));
            }
            for (TechId other : t.excludes()) {
                union(parent, t, templates.tech(other));
            }
        }
        Map<Integer, List<TechDef>> out = new TreeMap<>();
        for (TechDef t : techs) {
            out.computeIfAbsent(find(parent, t.netIndex()), k -> new ArrayList<>())
                    .add(t);
        }
        Map<Integer, List<TechDef>> sorted = new TreeMap<>();
        for (List<TechDef> line : out.values()) {
            line.sort(Comparator.comparingInt(TechDef::netIndex));
            sorted.put(line.get(0).netIndex(), line);
        }
        return sorted;
    }

    private static void union(Map<Integer, Integer> parent, TechDef a, @Nullable TechDef b) {
        if (b != null && parent.containsKey(b.netIndex())) {
            int ra = find(parent, a.netIndex());
            int rb = find(parent, b.netIndex());
            parent.put(Math.max(ra, rb), Math.min(ra, rb));
        }
    }

    private static int find(Map<Integer, Integer> parent, int x) {
        int r = x;
        int up = parent.getOrDefault(r, r);
        while (up != r) {
            r = up;
            up = parent.getOrDefault(r, r);
        }
        return r;
    }
}
