package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateSetTest {

    @Test
    void builtinHoldsTheDebugWalkerTheInfantryAndTheWorker() {
        TemplateSet set = TemplateSet.builtin();
        UnitTemplate walker = set.byId(TemplateSet.DEBUG_WALKER);
        UnitTemplate infantry = set.byId(TemplateSet.INFANTRY);
        UnitTemplate worker = set.byId(TemplateSet.WORKER);

        assertThat(walker).isNotNull();
        assertThat(walker.netIndex()).as("ids sort debug_walker first").isZero();
        assertThat(walker.walkSpeed()).isEqualTo(0.25);
        assertThat(walker.bodyType()).isEqualTo("strategos:unit_humanoid");
        assertThat(walker.width()).isEqualTo(0.6f);
        assertThat(walker.height()).isEqualTo(1.8f);
        assertThat(walker.classes()).containsExactly("Unit");
        assertThat(infantry).isNotNull();
        assertThat(infantry.netIndex()).isEqualTo(1);
        assertThat(infantry.walkSpeed()).as("3/16, exact in binary").isEqualTo(0.1875);
        assertThat(infantry.classes()).containsExactly("Infantry", "Organic", "Spearman", "Unit");
        assertThat(set.byIndex(0)).isSameAs(walker);
        assertThat(worker).isNotNull();
        assertThat(worker.netIndex())
                .as("ids sort the worker after the infantry: M2's indices stay")
                .isEqualTo(2);
        assertThat(worker.classes()).containsExactly("Builder", "Gatherer", "Organic", "Unit", "Worker");
        assertThat(set.all()).containsExactly(walker, infantry, worker);
        assertThat(set.byId(new TemplateId("strategos:unit/nobody"))).isNull();
        assertThat(set.tokens().all())
                .containsExactly("Builder", "Gatherer", "Infantry", "Organic", "Spearman", "Unit", "Worker");
        assertThat(set.valuePaths().byPath("UnitMotion/walkSpeed")).isNotNull();
    }

    @Test
    void theVersionIsTheShaOfTheCanonicalJson() {
        TemplateSet set = TemplateSet.builtin();
        assertThat(set.version()).as("pins the canonical form").isEqualTo(BUILTIN_VERSION);
        assertThat(set.hash()).isEqualTo(Long.parseUnsignedLong(BUILTIN_VERSION.substring(0, 16), 16));
    }

    @Test
    void everyFieldChangesTheHash() {
        long base = set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f))
                .hash();
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f))
                        .hash())
                .as("equal content, equal hash")
                .isEqualTo(base);
        assertThat(set(new UnitTemplate(
                                new TemplateId("strategos:unit/other"), 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_mount", 0.25, 0.6f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.3, 0.6f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.7f, 1.8f))
                        .hash())
                .isNotEqualTo(base);
        assertThat(set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.9f))
                        .hash())
                .isNotEqualTo(base);
    }

    @Test
    void rejectsAnInconsistentSet() {
        UnitTemplate walker = TemplateSet.builtin().byIndex(0);
        UnitTemplate infantry = TemplateSet.builtin().byIndex(1);
        assertThatThrownBy(() -> new TemplateSet(List.of(infantry, walker)))
                .as("net indices out of order")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> set(new UnitTemplate(walker.id(), -1, walker.bodyType(), 0.25, 0.6f, 1.8f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new TemplateSet(List.of(walker, new UnitTemplate(walker.id(), 1, "b", 0.25, 0.6f, 1.8f))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> set(new UnitTemplate(walker.id(), 0, walker.bodyType(), 0, 0.6f, 1.8f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemplateSet.builtin().byIndex(3)).isInstanceOf(IllegalArgumentException.class);
    }

    private static final String BUILTIN_VERSION =
            "b99c4facfd893f776c9f82334fe7141520ee8a9239ac165e0bb6651edbf63713"; // M3: the worker, the spearman's cost

    private static TemplateSet set(UnitTemplate template) {
        return new TemplateSet(List.of(template));
    }
}
