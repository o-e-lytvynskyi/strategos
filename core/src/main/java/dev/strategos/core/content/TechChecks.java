package dev.strategos.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** The checks that decide which techs of a set stand (TECH-01, TECH-02), run by {@link TemplateCompiler}. */
final class TechChecks {
    private TechChecks() {}

    /**
     * The techs that stand (TECH-01): each error is reported once and leaves its tech out, then every tech that needs a
     * tech left out. A {@code requires} or {@code supersedes} names a declared tech; those edges hold no cycle; an
     * effect's path is a number path of the templates (a token path for {@code TOKENS}); and a template's {@code
     * Researcher/techs} names techs of the set.
     */
    static List<TechDef> check(
            List<TechDef> techs,
            Set<String> declared,
            Function<TechId, String> techFiles,
            List<TemplateCompiler.Flat> templates,
            Function<TemplateId, String> templateFiles,
            List<TemplateProblem> problems) {
        List<TechDef> kept = new ArrayList<>();
        for (TechDef t : techs) {
            String file = techFiles.apply(t.id());
            int before = problems.size();
            for (TechEdge e : edges(t)) {
                if (!declared.contains(e.target().value())) {
                    problems.add(TemplateProblem.error(file, e.pointer(), "unknown tech '" + e.target() + "'"));
                }
            }
            if (problems.size() == before) {
                kept.add(t);
            }
        }
        kept = withPhaseRules(kept, techFiles, problems);
        kept = withoutCycles(kept, techFiles, problems);
        List<String> numbers = new ArrayList<>(CorePaths.all());
        List<String> strings = new ArrayList<>();
        for (TemplateCompiler.Flat f : templates) {
            TemplateSet.leafPaths(f.json(), "", numbers, strings);
        }
        List<TechDef> withPaths = new ArrayList<>();
        for (TechDef t : kept) {
            int before = problems.size();
            for (int i = 0; i < t.effects().size(); i++) {
                TechEffect e = t.effects().get(i);
                boolean tokens = e.op() == TechEffect.Op.TOKENS;
                if (!(tokens ? strings : numbers).contains(e.path())) {
                    problems.add(TemplateProblem.error(
                            techFiles.apply(t.id()),
                            "/effects/" + i + "/path",
                            "no template has " + (tokens ? "a token list" : "a number") + " at '" + e.path() + "'"));
                }
            }
            if (problems.size() == before) {
                withPaths.add(t);
            }
        }
        kept = withoutDependents(withPaths, techFiles, problems);
        kept = withResolvedUnlocks(kept, templates, techFiles, problems);
        kept = withoutDependents(kept, techFiles, problems);
        List<String> ids = kept.stream().map(t -> t.id().value()).toList();
        for (TemplateCompiler.Flat f : templates) {
            JsonElement list = leafOf(f.json(), "Researcher", "techs");
            if (list != null
                    && list.isJsonPrimitive()
                    && list.getAsJsonPrimitive().isString()) {
                for (String token : MergeOps.tokens(list.getAsString())) {
                    if (!ids.contains(token)) {
                        problems.add(TemplateProblem.error(
                                templateFiles.apply(f.id()), "/Researcher/techs", "unknown tech '" + token + "'"));
                    }
                }
            }
        }
        return kept;
    }

    /**
     * A tech has the class {@code Phase} if and only if its id's path starts with {@code phase_}, and phase ordinals
     * are distinct (TECH-02): the later id of two alike is left out.
     */
    private static List<TechDef> withPhaseRules(
            List<TechDef> techs, Function<TechId, String> techFiles, List<TemplateProblem> problems) {
        List<TechDef> kept = new ArrayList<>();
        Map<Integer, String> byOrdinal = new TreeMap<>();
        for (TechDef t : techs.stream()
                .sorted((a, b) -> a.id().value().compareTo(b.id().value()))
                .toList()) {
            String file = techFiles.apply(t.id());
            String path = t.id().value().substring(t.id().value().indexOf(':') + 1);
            boolean phaseId = path.startsWith("phase_");
            if (phaseId != t.isPhase()) {
                problems.add(TemplateProblem.error(
                        file,
                        "/classes",
                        phaseId
                                ? "tech " + t.id() + " is a phase by its id and needs the class Phase and an ordinal"
                                : "tech " + t.id() + " has the class Phase, so its id's path starts with 'phase_'"));
                continue;
            }
            if (t.isPhase()) {
                String other = byOrdinal.putIfAbsent(t.ordinal(), t.id().value());
                if (other != null) {
                    problems.add(TemplateProblem.error(
                            file, "/ordinal", "ordinal " + t.ordinal() + " is also " + other + "'s"));
                    continue;
                }
            }
            kept.add(t);
        }
        return techs.stream().filter(kept::contains).toList();
    }

    /**
     * Each unlock names what it unlocks: a {@code Trainer/units} or {@code Builder/buildings} addition a template of the set, a {@code
     * Researcher/techs} addition a tech that stands. The error is at the unlock.
     */
    private static List<TechDef> withResolvedUnlocks(
            List<TechDef> techs,
            List<TemplateCompiler.Flat> templates,
            Function<TechId, String> techFiles,
            List<TemplateProblem> problems) {
        List<String> templateIds = templates.stream().map(f -> f.id().value()).toList();
        List<String> techIds = techs.stream().map(t -> t.id().value()).toList();
        List<TechDef> kept = new ArrayList<>();
        for (TechDef t : techs) {
            int before = problems.size();
            for (TechEffect e : t.effects()) {
                if (e.op() != TechEffect.Op.TOKENS || e.tokens() == null || !TechDef.unlocking(e.path())) {
                    continue;
                }
                boolean units = TechDef.unlockingTemplates(e.path());
                for (String item : MergeOps.tokens(e.tokens())) {
                    String id = item.substring(1);
                    if (item.startsWith("+") && !(units ? templateIds : techIds).contains(id)) {
                        problems.add(TemplateProblem.error(
                                techFiles.apply(t.id()),
                                "/unlocks/" + t.unlocks().indexOf(id),
                                "unlocks " + (units ? "template '" : "tech '") + id + "', which the set lacks"));
                    }
                }
            }
            if (problems.size() == before) {
                kept.add(t);
            }
        }
        return kept;
    }

    private static @Nullable JsonElement leafOf(JsonObject json, String component, String key) {
        JsonElement c = json.get(component);
        return c != null && c.isJsonObject() ? c.getAsJsonObject().get(key) : null;
    }

    /** An edge of the tech graph: the tech a {@code requires} leaf or {@code supersedes} names, and where. */
    private record TechEdge(TechId target, String pointer) {}

    private static List<TechEdge> edges(TechDef t) {
        List<TechEdge> edges = new ArrayList<>();
        t.requires().techLeaves("/requires", (ref, pointer) -> edges.add(new TechEdge(ref, pointer)));
        if (t.supersedes() != null) {
            edges.add(new TechEdge(t.supersedes(), "/supersedes"));
        }
        return edges;
    }

    /** Reports each cycle once, at the edge that closes it, and leaves its members out. */
    private static List<TechDef> withoutCycles(
            List<TechDef> techs, Function<TechId, String> files, List<TemplateProblem> problems) {
        Map<String, TechDef> byId = new TreeMap<>();
        techs.forEach(t -> byId.put(t.id().value(), t));
        Map<String, Boolean> onStack = new TreeMap<>(); // present: visited; true: still on the stack
        Set<String> inCycle = new TreeSet<>();
        for (String id : byId.keySet()) {
            if (!onStack.containsKey(id)) {
                visit(id, byId, onStack, new ArrayList<>(), inCycle, files, problems);
            }
        }
        return techs.stream().filter(t -> !inCycle.contains(t.id().value())).collect(Collectors.toList());
    }

    private static void visit(
            String id,
            Map<String, TechDef> byId,
            Map<String, Boolean> onStack,
            List<String> stack,
            Set<String> inCycle,
            Function<TechId, String> files,
            List<TemplateProblem> problems) {
        onStack.put(id, true);
        stack.add(id);
        TechDef t = Objects.requireNonNull(byId.get(id));
        for (TechEdge e : edges(t)) {
            String to = e.target().value();
            if (!byId.containsKey(to)) {
                continue;
            }
            Boolean state = onStack.get(to);
            if (state == null) {
                visit(to, byId, onStack, stack, inCycle, files, problems);
            } else if (state) {
                List<String> cycle = new ArrayList<>(stack.subList(stack.indexOf(to), stack.size()));
                inCycle.addAll(cycle);
                cycle.add(to);
                problems.add(TemplateProblem.error(
                        files.apply(t.id()), e.pointer(), "tech cycle " + String.join(" → ", cycle)));
            }
        }
        stack.remove(stack.size() - 1);
        onStack.put(id, false);
    }

    /** Drops, with an error, each tech that names a tech not in the list, until none does. */
    private static List<TechDef> withoutDependents(
            List<TechDef> techs, Function<TechId, String> files, List<TemplateProblem> problems) {
        List<TechDef> kept = new ArrayList<>(techs);
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> ids = new TreeSet<>();
            kept.forEach(t -> ids.add(t.id().value()));
            for (TechDef t : kept) {
                for (TechEdge e : edges(t)) {
                    if (!ids.contains(e.target().value())) {
                        problems.add(TemplateProblem.error(
                                files.apply(t.id()), e.pointer(), "its tech '" + e.target() + "' does not compile"));
                        kept.remove(t);
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    break;
                }
            }
        }
        return kept;
    }
}
