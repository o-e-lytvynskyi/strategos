package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import org.jspecify.annotations.Nullable;

/**
 * The data form of a modifier a technology brings (TECH-01, ADR-0013): the same fields as a {@code Modifier} but its
 * source, which research assigns; {@link Op} has {@code ModifierOp}'s names. {@code TOKENS} has {@code tokens} and no value; the other ops have a value.
 */
@CoreApi
public record TechEffect(
        String path, Op op, double value, @Nullable String tokens, String filter, int stage, int priority) {
    /** The ops of {@code ModifierOp}, by name: the content layer cannot name the simulation's enum. */
    public enum Op {
        REPLACE,
        MUL,
        ADD,
        TOKENS
    }
}
