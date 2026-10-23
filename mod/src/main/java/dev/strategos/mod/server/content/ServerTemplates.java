package dev.strategos.mod.server.content;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.UnitView;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import org.jspecify.annotations.Nullable;

/**
 * The server's templates (DATA-03, ADR-0012): the set running matches use and new ones start with, the session's
 * append-only id table, and the problems of the last load. A load whose set has errors, or that drops the template of
 * a live unit of a running match, is refused, and the set stays; a good one goes to every match as a {@link
 * ContentSwap} at its next tick boundary. Before data packs load it holds the bundled set the core has built in.
 * Server thread only.
 */
public final class ServerTemplates {
    private TemplateSet set = TemplateSet.builtin();
    private TemplateIdTable table = new TemplateIdTable(
            TemplateSet.builtin().all().stream().map(UnitTemplate::id).toList());
    private List<TemplateProblem> problems = List.of();
    private String outcome = "the bundled set; no data pack loaded yet";
    private long loads;

    /** What a load did: whether its set is now the server's, why not, and every problem it found. */
    public record Outcome(boolean accepted, String message, List<TemplateProblem> problems) {}

    public TemplateSet set() {
        return set;
    }

    public TemplateIdTable table() {
        return table;
    }

    /** The last load's problems: its errors when it was refused, its warnings when not. */
    public List<TemplateProblem> problems() {
        return problems;
    }

    /** What the last load did, in words. */
    public String outcome() {
        return outcome;
    }

    /** Loads so far, refused ones included. */
    public long loads() {
        return loads;
    }

    /**
     * Compiles {@code raws} against the session's id table, lints the set and, when it is good and no running match
     * holds a unit of a template it lacks, makes it the server's and queues its swap in every match.
     */
    public Outcome load(
            List<RawTemplate> raws,
            List<TemplateProblem> readProblems,
            RegistryAccess registries,
            Collection<MatchRuntime> matches,
            Map<String, Blueprint> blueprints) {
        loads++;
        TemplateCompiler.Result result = TemplateCompiler.compile(raws, table, blueprints);
        List<TemplateProblem> found = new ArrayList<>(readProblems);
        found.addAll(result.problems());
        found.addAll(TemplateLint.check(result.set(), registries, t -> fileOf(raws, t)));
        long errors = found.stream()
                .filter(p -> p.severity() == TemplateProblem.Severity.ERROR)
                .count();
        if (errors > 0) {
            return refuse(found, errors + " template error" + (errors == 1 ? "" : "s") + "; the set stays " + set);
        }
        String used = usedButMissing(result.set(), matches);
        if (used != null) {
            found.add(TemplateProblem.error("", "", used));
            return refuse(found, used + "; the set stays " + set);
        }
        set = result.set();
        table = result.table();
        problems = List.copyOf(found);
        outcome = "loaded " + set + " from " + raws.size() + " files";
        for (MatchRuntime rt : matches) {
            rt.queueSwap(new ContentSwap(set));
        }
        return new Outcome(true, outcome, problems);
    }

    private Outcome refuse(List<TemplateProblem> found, String why) {
        problems = List.copyOf(found);
        outcome = "refused: " + why;
        return new Outcome(false, outcome, problems);
    }

    /** The first live unit of a running match whose template {@code next} lacks, in words; null when none. */
    private static @Nullable String usedButMissing(TemplateSet next, Collection<MatchRuntime> matches) {
        for (MatchRuntime rt : matches) {
            MatchCore core = rt.core();
            if (core == null) {
                continue;
            }
            for (int handle : core.queries().unitHandles()) {
                UnitView unit = core.queries().unit(handle);
                if (unit != null && next.byId(unit.template()) == null) {
                    return "template " + unit.template() + " is in use in match "
                            + rt.id().value() + " and the new set drops it";
                }
            }
        }
        return null;
    }

    private static String fileOf(List<RawTemplate> raws, UnitTemplate t) {
        for (RawTemplate raw : raws) {
            if (raw.id().equals(t.id())) {
                return raw.file();
            }
        }
        return t.id().value();
    }
}
