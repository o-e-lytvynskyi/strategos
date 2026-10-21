package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateSetTest {

    @Test
    void builtinHoldsTheDebugWalker() {
        TemplateSet set = TemplateSet.builtin();
        UnitTemplate walker = set.byId(new TemplateId("strategos:unit/debug_walker"));

        assertThat(walker).isNotNull();
        assertThat(walker.netIndex()).isZero();
        assertThat(walker.walkSpeed()).isEqualTo(0.25);
        assertThat(walker.bodyType()).isEqualTo("strategos:unit_humanoid");
        assertThat(walker.width()).isEqualTo(0.6f);
        assertThat(walker.height()).isEqualTo(1.8f);
        assertThat(set.byIndex(0)).isSameAs(walker);
        assertThat(set.all()).containsExactly(walker);
        assertThat(set.byId(new TemplateId("strategos:unit/nobody"))).isNull();
    }

    @Test
    void hashMatchesItsGoldenValue() {
        // Computed once from the field order of TemplateSet#hash by an independent implementation.
        assertThat(TemplateSet.builtin().hash()).isEqualTo(1408803309861886479L);
    }

    @Test
    void everyFieldChangesTheHash() {
        long base = set(new UnitTemplate(TemplateSet.DEBUG_WALKER, 0, "strategos:unit_humanoid", 0.25, 0.6f, 1.8f))
                .hash();
        assertThat(base).isEqualTo(TemplateSet.builtin().hash());
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
        assertThatThrownBy(() -> set(new UnitTemplate(walker.id(), 1, walker.bodyType(), 0.25, 0.6f, 1.8f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new TemplateSet(List.of(walker, new UnitTemplate(walker.id(), 1, "b", 0.25, 0.6f, 1.8f))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> set(new UnitTemplate(walker.id(), 0, walker.bodyType(), 0, 0.6f, 1.8f)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TemplateSet.builtin().byIndex(1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static TemplateSet set(UnitTemplate template) {
        return new TemplateSet(List.of(template));
    }
}
