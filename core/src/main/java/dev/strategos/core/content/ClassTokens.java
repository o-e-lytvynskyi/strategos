package dev.strategos.core.content;

import java.util.List;
import java.util.TreeSet;

/**
 * The class tokens of a template set (04 §8.1), interned to bit indices in alphabetical order, so filters such as
 * {@code "Infantry+Melee !Champion"} compile to bitsets. At most 64 tokens in a set.
 */
public final class ClassTokens {
    public static final int MAX = 64;

    private final List<String> tokens;

    public ClassTokens(java.util.Collection<String> tokens) {
        List<String> sorted = List.copyOf(new TreeSet<>(tokens));
        if (sorted.size() > MAX) {
            throw new IllegalArgumentException(sorted.size() + " class tokens, more than " + MAX);
        }
        this.tokens = sorted;
    }

    /** The bit of a token, or -1 when the set has no such token. */
    public int bit(String token) {
        int i = java.util.Collections.binarySearch(tokens, token);
        return i < 0 ? -1 : i;
    }

    /** The bitset of a template's tokens; an unknown token is a bug and throws. */
    public long bits(List<String> classes) {
        long bits = 0;
        for (String token : classes) {
            int bit = bit(token);
            if (bit < 0) {
                throw new IllegalArgumentException("class token '" + token + "' is not in the set");
            }
            bits |= 1L << bit;
        }
        return bits;
    }

    public List<String> all() {
        return tokens;
    }
}
