package dev.strategos.core.content;

/**
 * The syntax of class filters and token edits (04 §8.1), in one place: the content layer checks tech files with it,
 * and the stat service's {@code ClassFilter} and {@code TokenEdit} (sim.stats, a layer above) refuse what it refuses.
 */
public final class StatSyntax {
    private StatSyntax() {}

    /** Whether a class filter is well formed: no empty term, as in {@code "A++B"} or {@code "+A"}, no bare {@code "!"}. */
    public static boolean validFilter(String filter) {
        if (filter.isBlank()) {
            return true;
        }
        for (String clause : filter.trim().split("\\s+", -1)) {
            for (String term : clause.split("\\+", -1)) {
                String token = term.startsWith("!") ? term.substring(1) : term;
                if (token.isEmpty() || token.startsWith("!")) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a token edit reads {@code "+a -b"}: signed tokens, nothing else. */
    public static boolean validTokenEdit(String edit) {
        if (edit.isBlank()) {
            return false;
        }
        for (String item : edit.trim().split("\\s+", -1)) {
            if (item.length() < 2 || !sign(item.charAt(0)) || sign(item.charAt(1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sign(char c) {
        return c == '+' || c == '-';
    }
}
