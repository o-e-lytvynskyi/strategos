package dev.strategos.core.bench;

import dev.strategos.core.client.hud.GroupBar;
import dev.strategos.core.client.hud.HudLayout;
import dev.strategos.core.client.hud.SelectionSummary;
import dev.strategos.core.client.select.ControlGroups;
import dev.strategos.core.client.select.Selection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * UI-02.2: one frame's HUD data for 200 selected units of four types (budget: 0.5 ms): the layout, the selection
 * summary from the bodies' templates, and the control-group bar over ten groups; the client builds these at most once
 * a client tick.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HudBench {
    private final Map<Integer, Integer> templates = new HashMap<>();
    private final ControlGroups groups = new ControlGroups();
    private List<Integer> selected = List.of();

    @Setup(Level.Trial)
    public void setUp() {
        List<Integer> handles = new ArrayList<>();
        for (int h = 0; h < 200; h++) {
            handles.add(h);
            templates.put(h, h % 4);
        }
        selected = List.copyOf(handles);
        Selection selection = new Selection(0);
        for (int g = 0; g < 10; g++) {
            selection.set(handles.subList(g * 20, g * 20 + 20));
            groups.set(g, selection);
        }
    }

    @Benchmark
    public Object hudFrame200() {
        HudLayout layout = HudLayout.compute(2560, 1440, 2);
        SelectionSummary summary =
                SelectionSummary.of(selected, h -> templates.getOrDefault(h, -1), t -> "template " + t, null);
        GroupBar bar = GroupBar.of(groups);
        return new Object[] {layout, summary, bar};
    }
}
