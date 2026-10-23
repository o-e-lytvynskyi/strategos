package dev.strategos.core.client.hud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The selection panel's view-model for M2 (UI-02; the full panel is UI-06): how many units are selected and how many
 * of each type, the largest group first, then by template; or, with nothing selected, the unit being inspected.
 */
public record SelectionSummary(
        int total, List<TypeCount> types, @Nullable String inspected) {
    public static final SelectionSummary EMPTY = new SelectionSummary(0, List.of(), null);

    /** One type's count; {@code template} is the template's net index, -1 for a body not seen yet. */
    public record TypeCount(int template, String name, int count) {}

    /**
     * From the selected handles, the template of each as the client sees it ({@code templateOf}, -1 if unknown), and
     * the templates' names; {@code inspected} names the inspected unit, or null.
     */
    public static SelectionSummary of(
            List<Integer> handles, IntUnaryOperator templateOf, IntFunction<String> names, @Nullable String inspected) {
        if (handles.isEmpty()) {
            return inspected == null ? EMPTY : new SelectionSummary(0, List.of(), inspected);
        }
        int[] templates = new int[handles.size()];
        for (int i = 0; i < templates.length; i++) {
            templates[i] = templateOf.applyAsInt(handles.get(i));
        }
        Arrays.sort(templates);
        List<TypeCount> types = new ArrayList<>();
        for (int i = 0; i < templates.length; ) {
            int j = i;
            while (j < templates.length && templates[j] == templates[i]) {
                j++;
            }
            types.add(new TypeCount(templates[i], templates[i] < 0 ? "?" : names.apply(templates[i]), j - i));
            i = j;
        }
        types.sort(Comparator.comparingInt(TypeCount::count).reversed().thenComparingInt(TypeCount::template));
        return new SelectionSummary(handles.size(), List.copyOf(types), null);
    }
}
