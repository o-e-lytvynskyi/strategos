package dev.strategos.core.sim.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Token lists and their edits (ADR-0013): a list is a string of space-separated tokens, as {@code Identity/classes};
 * an edit's items {@code +a} append a token the list lacks and {@code -b} remove one, left to right.
 */
final class TokenEdit {
    private TokenEdit() {}

    static boolean valid(String edit) {
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

    /** The tokens of a list, in order. */
    static List<String> split(String text) {
        List<String> tokens = new ArrayList<>();
        if (!text.isBlank()) {
            for (String token : text.trim().split("\\s+", -1)) {
                if (!tokens.contains(token)) {
                    tokens.add(token);
                }
            }
        }
        return List.copyOf(tokens);
    }

    static void apply(List<String> list, String edit) {
        for (String item : edit.trim().split("\\s+", -1)) {
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

    private static boolean sign(char c) {
        return c == '+' || c == '-';
    }
}
