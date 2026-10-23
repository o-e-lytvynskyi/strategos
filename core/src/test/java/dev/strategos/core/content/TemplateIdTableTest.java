package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DATA-03.2 (spec §5.3): the session's id table is append-only across reloads: a template keeps its net index, a new
 * one takes the next, in id order, and a removed one's index is retired, never given again.
 */
class TemplateIdTableTest {

    @Test
    void indicesSurviveReloadsAndOnlyGrow() {
        TemplateCompiler.Result first = TemplateCompiler.compile(raws("b", "d"));
        TemplateCompiler.Result second = TemplateCompiler.compile(raws("a", "b", "c", "d"), first.table());
        TemplateCompiler.Result third = TemplateCompiler.compile(raws("a", "c", "d", "e"), second.table());

        assertThat(index(first, "b")).isZero();
        assertThat(index(first, "d")).isEqualTo(1);
        assertThat(index(second, "b")).as("kept").isZero();
        assertThat(index(second, "d")).as("kept").isEqualTo(1);
        assertThat(index(second, "a")).as("new, in id order").isEqualTo(2);
        assertThat(index(second, "c")).isEqualTo(3);
        assertThat(index(third, "e")).as("after every index the session gave").isEqualTo(4);
        assertThat(third.set().has(0)).as("b's index is retired").isFalse();
        assertThat(third.set().indexCount()).isEqualTo(5);
        assertThat(third.table().ids()).startsWith(second.table().ids().toArray(new TemplateId[0]));
        assertThat(third.set().byIndex(1).id()).isEqualTo(id("d"));
    }

    @Test
    void aFreshTableGivesIdOrder() {
        TemplateCompiler.Result result = TemplateCompiler.compile(raws("c", "a", "b"));

        assertThat(result.table().ids()).containsExactly(id("a"), id("b"), id("c"));
        assertThat(TemplateIdTable.EMPTY.with(List.of(id("x"))).sha())
                .isNotEqualTo(TemplateIdTable.EMPTY.with(List.of(id("y"))).sha());
    }

    @Test
    void aViewDecodesToTheSameSet() {
        TemplateCompiler.Result first = TemplateCompiler.compile(raws("b", "d"));
        TemplateCompiler.Result second = TemplateCompiler.compile(raws("a", "d"), first.table());

        TemplateSet decoded = dev.strategos.core.protocol.TemplateSetView.of(second.set(), second.table())
                .toSet();

        assertThat(decoded.version()).isEqualTo(second.set().version());
        assertThat(decoded.byId(id("d")).netIndex()).isEqualTo(1);
        assertThat(decoded.byId(id("a")).netIndex()).isEqualTo(2);
        assertThat(decoded.has(0)).isFalse();
    }

    private static int index(TemplateCompiler.Result result, String name) {
        UnitTemplate t = result.set().byId(id(name));
        assertThat(t).as(name).isNotNull();
        return t.netIndex();
    }

    private static TemplateId id(String name) {
        return new TemplateId("test:unit/" + name);
    }

    private static List<RawTemplate> raws(String... names) {
        List<RawTemplate> raws = new ArrayList<>();
        for (String name : names) {
            raws.add(new RawTemplate(
                    id(name),
                    "test/unit/" + name + ".json",
                    JsonParser.parseString("""
                    {
                      "Identity": { "classes": "Unit" },
                      "UnitMotion": { "walkSpeed": 0.25 },
                      "Body": { "entityType": "strategos:unit_humanoid", "width": 0.6, "height": 1.8 }
                    }
                    """).getAsJsonObject()));
        }
        return raws;
    }
}
