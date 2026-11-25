package dev.strategos.core.content;

import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Decodes and checks the civilisations of a set (CIV-01), run by {@link TemplateCompiler}: each file decodes
 * ({@link CivDef#fromJson}); every id it names exists (roster, roles, start kit); a role's template is in the roster; a
 * start-kit entity's role is one of the civilisation's; a bonus's path is a number (a token list for {@code TOKENS})
 * of some template; and the {@code house} role exists unless the civilisation selects its own {@code population}
 * rule. Each problem is reported once and leaves its civilisation out.
 */
final class CivChecks {
    /** The seam whose default needs a {@code house} role. */
    static final String POPULATION = "population";

    private CivChecks() {}

    /** The civilisations that stand, ascending by id. */
    static List<CivDef> compile(
            List<RawCiv> raws,
            List<TemplateCompiler.Flat> templates,
            Set<TechId> techs,
            List<TemplateProblem> problems) {
        Map<String, RawCiv> byId = new TreeMap<>();
        for (RawCiv raw : raws) {
            RawCiv previous = byId.put(raw.id().value(), raw);
            if (previous != null) {
                problems.add(TemplateProblem.error(
                        raw.file(), "", "civilisation " + raw.id() + " is also defined in " + previous.file()));
            }
        }
        if (byId.isEmpty()) {
            return List.of();
        }
        Set<TemplateId> templateIds = new TreeSet<>((a, b) -> a.value().compareTo(b.value()));
        for (TemplateCompiler.Flat f : templates) {
            templateIds.add(f.id());
        }
        @Nullable List<String> numbers = null;
        @Nullable List<String> strings = null;
        List<CivDef> kept = new ArrayList<>();
        for (RawCiv raw : byId.values()) {
            CivDef civ = CivDef.fromJson(raw.id(), raw.json(), raw.file(), problems);
            if (civ == null) {
                continue;
            }
            if (!civ.bonuses().isEmpty() && numbers == null) {
                numbers = new ArrayList<>(CorePaths.all());
                strings = new ArrayList<>();
                for (TemplateCompiler.Flat f : templates) {
                    TemplateSet.leafPaths(f.json(), "", numbers, strings);
                }
            }
            int before = problems.size();
            check(civ, raw.file(), templateIds, techs, numbers, strings, problems);
            if (problems.size() == before) {
                kept.add(civ);
            }
        }
        return kept;
    }

    private static void check(
            CivDef civ,
            String file,
            Set<TemplateId> templateIds,
            Set<TechId> techs,
            @Nullable List<String> numbers,
            @Nullable List<String> strings,
            List<TemplateProblem> problems) {
        CivDef.Roster roster = civ.roster();
        List<TemplateId> listed = roster.templates();
        for (int i = 0; listed != null && i < listed.size(); i++) {
            if (!templateIds.contains(listed.get(i))) {
                problems.add(TemplateProblem.error(
                        file, "/roster/templates", "unknown template '" + listed.get(i) + "' in the roster"));
            }
        }
        List<TechId> listedTechs = roster.techs();
        for (int i = 0; listedTechs != null && i < listedTechs.size(); i++) {
            if (!techs.contains(listedTechs.get(i))) {
                problems.add(TemplateProblem.error(
                        file, "/roster/techs", "unknown tech '" + listedTechs.get(i) + "' in the roster"));
            }
        }
        for (Map.Entry<String, TemplateId> e : civ.roles().byRole().entrySet()) {
            String at = "/roles/" + e.getKey();
            if (!templateIds.contains(e.getValue())) {
                problems.add(TemplateProblem.error(file, at, "role names no template: '" + e.getValue() + "'"));
            } else if (!roster.hasTemplate(e.getValue())) {
                problems.add(TemplateProblem.error(file, at, "template '" + e.getValue() + "' is not in the roster"));
            }
        }
        List<StartKit.Entity> entities = civ.start().entities();
        for (int i = 0; i < entities.size(); i++) {
            StartKit.Entity e = entities.get(i);
            String at = "/start/entities/" + i;
            String role = e.role();
            TemplateId template = e.template();
            if (role != null) {
                if (!civ.roles().has(role)) {
                    problems.add(TemplateProblem.error(file, at + "/role", "unknown role '" + role + "'"));
                }
            } else if (template != null) {
                if (!templateIds.contains(template)) {
                    problems.add(TemplateProblem.error(file, at + "/template", "unknown template '" + template + "'"));
                } else if (!roster.hasTemplate(template)) {
                    problems.add(TemplateProblem.error(
                            file, at + "/template", "template '" + template + "' is not in the roster"));
                }
            }
        }
        for (int i = 0; i < civ.bonuses().size(); i++) {
            TechEffect e = civ.bonuses().get(i);
            boolean tokens = e.op() == TechEffect.Op.TOKENS;
            List<String> known = tokens ? strings : numbers;
            if (known != null && !known.contains(e.path())) {
                problems.add(TemplateProblem.error(
                        file,
                        "/bonuses/" + i + "/path",
                        "no template has " + (tokens ? "a token list" : "a number") + " at '" + e.path() + "'"));
            }
        }
        if (!civ.roles().has(CivRoles.HOUSE) && !civ.rules().containsKey(POPULATION)) {
            problems.add(
                    TemplateProblem.error(file, "/roles", "no 'house' role, and no 'population' rule that needs none"));
        }
    }
}
