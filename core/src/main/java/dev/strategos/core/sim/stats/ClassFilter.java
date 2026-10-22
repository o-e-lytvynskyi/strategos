package dev.strategos.core.sim.stats;

import dev.strategos.core.content.ClassTokens;
import java.util.Arrays;

/**
 * A compiled class filter (04 §8.1): space-separated clauses are OR-ed; inside a clause {@code +} joins tokens with AND
 * and {@code !} negates one, as in {@code "Infantry+Melee !Champion"}. Each clause is a must-have and a must-not bitset
 * over a set's tokens, so a test is a few {@code long} operations. The empty filter matches every template; a clause
 * that needs a token the set lacks matches none, and a negated token the set lacks always holds.
 */
public final class ClassFilter {
    private final String text;
    private final boolean all;
    private final long[] must;
    private final long[] mustNot;

    private ClassFilter(String text, boolean all, long[] must, long[] mustNot) {
        this.text = text;
        this.all = all;
        this.must = must;
        this.mustNot = mustNot;
    }

    public static ClassFilter compile(String filter, ClassTokens tokens) {
        check(filter);
        if (filter.isBlank()) {
            return new ClassFilter(filter, true, new long[0], new long[0]);
        }
        String[] clauses = filter.trim().split("\\s+", -1);
        long[] must = new long[clauses.length];
        long[] mustNot = new long[clauses.length];
        int n = 0;
        clauses:
        for (String clause : clauses) {
            long have = 0;
            long not = 0;
            for (String term : clause.split("\\+", -1)) {
                boolean negated = term.charAt(0) == '!';
                int bit = tokens.bit(negated ? term.substring(1) : term);
                if (negated) {
                    if (bit >= 0) {
                        not |= 1L << bit;
                    }
                } else if (bit < 0) {
                    continue clauses; // needs a token no template has
                } else {
                    have |= 1L << bit;
                }
            }
            must[n] = have;
            mustNot[n] = not;
            n++;
        }
        return new ClassFilter(filter, false, Arrays.copyOf(must, n), Arrays.copyOf(mustNot, n));
    }

    /** Throws on a malformed filter: an empty term, as in {@code "A++B"} or {@code "+A"}, or a bare {@code "!"}. */
    static void check(String filter) {
        if (filter.isBlank()) {
            return;
        }
        for (String clause : filter.trim().split("\\s+", -1)) {
            for (String term : clause.split("\\+", -1)) {
                String token = term.startsWith("!") ? term.substring(1) : term;
                if (token.isEmpty() || token.startsWith("!")) {
                    throw new IllegalArgumentException("malformed class filter '" + filter + "'");
                }
            }
        }
    }

    /** Whether a template with these class bits passes. */
    public boolean matches(long classes) {
        if (all) {
            return true;
        }
        for (int k = 0; k < must.length; k++) {
            if ((classes & must[k]) == must[k] && (classes & mustNot[k]) == 0) {
                return true;
            }
        }
        return false;
    }

    public String text() {
        return text;
    }
}
