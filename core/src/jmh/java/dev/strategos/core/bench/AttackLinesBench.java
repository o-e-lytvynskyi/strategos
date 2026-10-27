package dev.strategos.core.bench;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.client.select.AttackLines;
import dev.strategos.core.client.select.Flashes;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.Handle;
import java.util.ArrayList;
import java.util.List;
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
 * CMB-17.2: one frame's attack lines and under-attack flashes for 300 units, all selected, a query's most of them with
 * a target and a third hit (budget: 0.3 ms; a {@code @HotPath} kernel: no allocation), into reused buffers.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class AttackLinesBench {
    private static final int MATCH = 1;
    private static final int UNITS = 300;
    private static final long NOW = 100_000;

    private final ClientMatchModel model = new ClientMatchModel();
    private final AttackLines.Buffer lines = new AttackLines.Buffer();
    private final Flashes.Buffer flashes = new Flashes.Buffer();
    private int[] selected = new int[0];

    @Setup(Level.Trial)
    public void setUp() {
        List<UnitSummary.Entry> entries = new ArrayList<>();
        List<Integer> handles = new ArrayList<>();
        List<UnitHud.Entry> hits = new ArrayList<>();
        List<UnitDetail.Unit> details = new ArrayList<>();
        for (int k = 0; k < UNITS; k++) {
            int faction = k % 2;
            entries.add(UnitSummary.Entry.at(k, faction, 0, 1, k % 40, k / 40.0));
            int handle = Handle.of(k, 1);
            if (faction == 0) {
                handles.add(handle);
                if (details.size() < UiQuery.MAX_HANDLES) {
                    details.add(new UnitDetail.Unit(handle, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0, (k + 1) % UNITS));
                }
                if (k % 3 == 0) {
                    hits.add(new UnitHud.Entry(k, 100));
                }
            }
        }
        model.accept(new UnitSummary(MATCH, 10, entries), MATCH);
        model.accept(new UnitDetail(MATCH, 10, details), MATCH);
        model.accept(new UnitHud(MATCH, 10, hits), MATCH, NOW);
        Selection selection = new Selection(0);
        selection.set(handles);
        selected = selection.handles().stream().mapToInt(Integer::intValue).toArray();
    }

    @Benchmark
    public int extract300() {
        AttackLines.extract(model, selected, selected.length, lines);
        Flashes.extract(model, 0, NOW + 100, flashes);
        return lines.count() + flashes.count();
    }
}
