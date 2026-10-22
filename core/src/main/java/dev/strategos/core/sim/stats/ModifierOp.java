package dev.strategos.core.sim.stats;

import dev.strategos.core.shared.CoreApi;

/**
 * How a modifier changes its path (ADR-0013), in the order a stage applies them: a replacement, then products, then
 * sums; {@code TOKENS} edits a token list instead of a number.
 */
@CoreApi
public enum ModifierOp {
    REPLACE,
    MUL,
    ADD,
    TOKENS
}
