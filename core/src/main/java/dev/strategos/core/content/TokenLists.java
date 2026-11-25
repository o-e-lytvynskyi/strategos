package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A template's token list as one faction sees it (ADR-0013): the list's base tokens, then the {@code tokens} effects of
 * the faction's civilisation and of the techs it has researched (a tier a researched one supersedes gives none), whose filter selects the template, applied in the
 * stat service's canonical order. The server's {@code StatService.blockTokens} is the same evaluation over its
 * modifiers; the client has no modifiers, so it runs this over its template copy and the research it was told
 * (TECH-05: the command card's research lists). A parity test holds the two together.
 */
@CoreApi
public final class TokenLists {
    /** The source id of a civilisation's bonuses: just above the AI handicap's, below every tech's and handle's. */
    public static final int CIV_SOURCE = Integer.MIN_VALUE + 1;

    private static final Comparator<Edit> ORDER = Comparator.comparingInt(Edit::stage)
            .thenComparingInt(Edit::source)
            .thenComparingInt(e -> -e.priority())
            .thenComparing(Edit::tokens)
            .thenComparing(Edit::filter);

    private TokenLists() {}

    /** The source id of tech {@code netIndex}'s modifiers: negative, below the civilisation's. */
    public static int techSource(int netIndex) {
        return -1 - netIndex;
    }

    private record Edit(int stage, int source, int priority, String tokens, String filter) {}

    /**
     * Template {@code template}'s list at token path {@code path} for a faction of {@code civ} that has researched the
     * techs {@code researched} (net indexes); empty when the set lacks the path or the template has no such list.
     */
    public static List<String> effective(
            TemplateSet templates, int template, String path, @Nullable CivDef civ, Collection<Integer> researched) {
        ValuePath id = templates.tokenPaths().byPath(path);
        String text = id == null || !templates.has(template) ? null : templates.text(template, id.id());
        List<String> list = new ArrayList<>();
        if (text == null) {
            return list;
        }
        for (String token : split(text)) {
            if (!list.contains(token)) {
                list.add(token);
            }
        }
        long classes = templates.tokens().bits(templates.byIndex(template).classes());
        List<Edit> edits = new ArrayList<>();
        if (civ != null) {
            collect(edits, civ.bonuses(), CIV_SOURCE, path, classes, templates);
        }
        List<TechId> superseded = new ArrayList<>(); // a tier a researched one supersedes no longer gives its effects
        for (int tech : researched) {
            TechDef def = templates.techByIndex(tech);
            if (def != null && def.supersedes() != null) {
                superseded.add(def.supersedes());
            }
        }
        for (int tech : researched) {
            TechDef def = templates.techByIndex(tech);
            if (def != null && !superseded.contains(def.id())) {
                collect(edits, def.effects(), techSource(tech), path, classes, templates);
            }
        }
        edits.sort(ORDER);
        for (Edit e : edits) {
            for (String item : split(e.tokens())) {
                String token = item.substring(1);
                if (item.charAt(0) == '+') {
                    if (!list.contains(token)) {
                        list.add(token);
                    }
                } else {
                    list.remove(token);
                }
            }
        }
        return list;
    }

    private static void collect(
            List<Edit> out, List<TechEffect> effects, int source, String path, long classes, TemplateSet templates) {
        for (TechEffect e : effects) {
            if (e.op() == TechEffect.Op.TOKENS
                    && e.tokens() != null
                    && path.equals(e.path())
                    && ClassFilter.compile(e.filter(), templates.tokens()).matches(classes)) {
                out.add(new Edit(e.stage(), source, e.priority(), e.tokens(), e.filter()));
            }
        }
    }

    private static List<String> split(String text) {
        return text.isBlank() ? List.of() : List.of(text.trim().split("\\s+", -1));
    }
}
