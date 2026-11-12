package dev.strategos.core.bench;

import com.google.gson.JsonParser;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Compiling 500 templates (DATA-01.5: under 200 ms): 10 abstract bases, 40 abstract middles, 20 mixins and 430 units,
 * each unit with a parent, two mixins and {@code $op}s, as a large content set would be.
 */
@Fork(2)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class TemplateCompileBench {
    private List<RawTemplate> raws;

    @Setup(Level.Trial)
    public void setUp() {
        raws = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            raws.add(raw("strategos:base/b" + i, String.format(Locale.ROOT, """
                    {"abstract": true, "Identity": {"classes": "Unit C%d"},
                     "UnitMotion": {"walkSpeed": 0.2, "turnRate": 45},
                     "Body": {"entityType": "strategos:unit_humanoid", "width": 0.6, "height": 1.8},
                     "Health": {"max": %d}, "Cost": {"food": 50, "wood": %d}}
                    """, i % 20, 100 + i, i)));
        }
        for (int i = 0; i < 40; i++) {
            raws.add(raw("strategos:mid/m" + i, String.format(Locale.ROOT, """
                    {"parent": "strategos:base/b%d", "abstract": true, "Identity": {"classes": "+C%d"},
                     "Health": {"max": {"$op": "mul", "value": 1.1}}, "Attack": {"Melee": {"damage": {"hack": %d}}}}
                    """, i % 10, (i + 3) % 20, 2 + i % 5)));
        }
        for (int i = 0; i < 20; i++) {
            raws.add(raw("strategos:mixin/x" + i, String.format(Locale.ROOT, """
                    {"abstract": true, "Identity": {"classes": "+C%d"}, "Cost": {"wood": {"$op": "add", "value": %d}},
                     "Abilities": {"$append": ["a%d"]}}
                    """, (i + 7) % 20, i, i)));
        }
        for (int i = 0; i < 430; i++) {
            raws.add(raw(
                    "strategos:unit/u" + i,
                    String.format(
                            Locale.ROOT,
                            """
                    {"parent": "strategos:mid/m%d", "mixins": ["strategos:mixin/x%d", "strategos:mixin/x%d"],
                     "Identity": {"name": "u%d", "classes": "+C%d -C%d"}, "Cost": {"food": {"$op": "add", "value": %d}},
                     "UnitMotion": {"walkSpeed": 0.%d}, "Body": {"skin": "s%d"}}
                    """,
                            i % 40,
                            i % 20,
                            (i + 7) % 20,
                            i,
                            i % 20,
                            (i + 1) % 20,
                            i % 30,
                            10 + i % 80,
                            i)));
        }
    }

    @Benchmark
    public TemplateCompiler.Result compile500() {
        TemplateCompiler.Result result = TemplateCompiler.compile(raws);
        if (!result.ok()) {
            throw new IllegalStateException(
                    "the bench set does not compile: " + result.problems().get(0));
        }
        return result;
    }

    private static RawTemplate raw(String id, String json) {
        return new RawTemplate(
                new TemplateId(id), id + ".json", JsonParser.parseString(json).getAsJsonObject());
    }
}
