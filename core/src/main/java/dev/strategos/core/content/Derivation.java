package dev.strategos.core.content;

import dev.strategos.core.shared.TemplateId;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A value of a concrete template that a relationship set (DATA-04): its template and value path, the value it got, the
 * relationship's formula and note, or, for an override, the template's reason and what the formula would have given.
 */
public record Derivation(
        TemplateId template,
        String path,
        BigDecimal value,
        String formula,
        String note,
        boolean overridden,
        @Nullable BigDecimal derived) {}
