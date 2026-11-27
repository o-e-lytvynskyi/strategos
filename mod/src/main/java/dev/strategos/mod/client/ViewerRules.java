package dev.strategos.mod.client;

import dev.strategos.core.content.CivDef;
import dev.strategos.core.sim.civ.CivRules;
import dev.strategos.core.sim.civ.RuleCatalogue;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Makes the viewer's rule seams from its civilisation (CIV-05), so the client's placement preview and resource bar run
 * the rules the server runs. A civilisation the client's set lacks is every default (a stale copy: the roster is not
 * narrowed either); one whose rules the catalogue refuses plays on the defaults too, and says so through {@code warn}:
 * the server lints a civilisation's rules at load, so a client that disagrees has a stale or damaged copy.
 */
final class ViewerRules {
    private ViewerRules() {}

    static CivRules resolve(RuleCatalogue catalogue, @Nullable CivDef civ, Consumer<String> warn) {
        try {
            return catalogue.resolve(civ);
        } catch (IllegalArgumentException e) {
            warn.accept("civilisation " + (civ == null ? "?" : civ.id().value()) + ": " + e.getMessage()
                    + "; the rules stay at their defaults");
            return CivRules.DEFAULTS;
        }
    }
}
