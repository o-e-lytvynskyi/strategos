package dev.strategos.core.sim.stats;

import dev.strategos.core.shared.CoreApi;

/**
 * One change to a value path (ADR-0013). {@code REPLACE}, {@code MUL} and {@code ADD} change a number by {@code value};
 * {@code TOKENS} edits a token list by {@code tokens}, such as {@code "+a -b"} (add {@code a} if missing, remove
 * {@code b}). It reaches the templates its class filter matches ({@link ClassFilter}; empty: all) in {@code stage},
 * ascending from 0; {@code priority} decides between replacements; {@code sourceId} orders products and sums and names
 * the source, to remove it and to show it.
 */
@CoreApi
public record Modifier(
        String path, ModifierOp op, double value, String tokens, String filter, int stage, int priority, int sourceId) {
    public Modifier {
        if (path.isBlank()) {
            throw new IllegalArgumentException("a modifier needs a value path");
        }
        if (op == ModifierOp.TOKENS) {
            if (value != 0 || !TokenEdit.valid(tokens)) {
                throw new IllegalArgumentException("a TOKENS modifier edits by '+a -b', not '" + tokens + "'");
            }
        } else if (!Double.isFinite(value) || !tokens.isEmpty()) {
            throw new IllegalArgumentException("a " + op + " modifier needs a finite value and no tokens");
        }
        if (stage < 0) {
            throw new IllegalArgumentException("stage " + stage + " is below 0");
        }
        ClassFilter.check(filter);
    }

    public static Modifier replace(String path, double value, int sourceId) {
        return new Modifier(path, ModifierOp.REPLACE, value, "", "", 0, 0, sourceId);
    }

    public static Modifier mul(String path, double value, int sourceId) {
        return new Modifier(path, ModifierOp.MUL, value, "", "", 0, 0, sourceId);
    }

    public static Modifier add(String path, double value, int sourceId) {
        return new Modifier(path, ModifierOp.ADD, value, "", "", 0, 0, sourceId);
    }

    public static Modifier tokens(String path, String tokens, int sourceId) {
        return new Modifier(path, ModifierOp.TOKENS, 0, tokens, "", 0, 0, sourceId);
    }

    public Modifier withFilter(String filter) {
        return new Modifier(path, op, value, tokens, filter, stage, priority, sourceId);
    }

    public Modifier atStage(int stage) {
        return new Modifier(path, op, value, tokens, filter, stage, priority, sourceId);
    }

    public Modifier withPriority(int priority) {
        return new Modifier(path, op, value, tokens, filter, stage, priority, sourceId);
    }
}
