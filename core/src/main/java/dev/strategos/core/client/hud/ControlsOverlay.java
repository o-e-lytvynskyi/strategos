package dev.strategos.core.client.hud;

import dev.strategos.core.client.input.Binding;
import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The controls overlay's rows (UI-14, spec §2.15): each rebindable binding's name and the key it is on now, rebinds
 * included. The client supplies the current key's label (a key mapping's translated message); here only the list.
 */
@CoreApi
public final class ControlsOverlay {
    private ControlsOverlay() {}

    /** A row: the translation key of the mapping's name and the label of its current key. */
    @CoreApi
    public record Row(String nameKey, String keyLabel) {}

    /**
     * The rows of the rebindable bindings in their order; a binding whose label is null (no mapping registered for
     * it) has no row.
     */
    public static List<Row> rows(List<Binding> bindings, Function<Binding, @Nullable String> label) {
        List<Row> rows = new ArrayList<>();
        for (Binding b : bindings) {
            if (!b.rebindable()) {
                continue;
            }
            String key = label.apply(b);
            if (key != null) {
                rows.add(new Row(b.mappingName(), key));
            }
        }
        return List.copyOf(rows);
    }
}
