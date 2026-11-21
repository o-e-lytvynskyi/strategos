package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class IngressReasonTest {
    @Test
    void auditCodesAreUniqueBoundedIdentifiersRatherThanPayloadOrDiagnosticText() {
        var codes =
                Arrays.stream(IngressReason.values()).map(IngressReason::code).toList();
        assertThat(codes).doesNotHaveDuplicates().allSatisfy(code -> {
            assertThat(code).matches("[a-z][a-z0-9_]*").hasSizeLessThanOrEqualTo(48);
        });
        assertThat(codes)
                .contains(
                        "unreadable",
                        "stale_connection",
                        "command_sequence",
                        "debug_permission",
                        "skirmish_permission",
                        "auxiliary_rate");
    }

    @Test
    void semanticRefusalsAreAuditedWithoutSpendingTheProtocolViolationBudget() {
        var counters = new NetCounters();
        counters.rejected("first", IngressReason.SKIRMISH_PERMISSION);
        counters.rejected("first", IngressReason.SKIRMISH_PERMISSION);
        counters.rejected("other", IngressReason.SKIRMISH_PERMISSION);
        assertThat(counters.violations("first")).isZero();
        assertThat(counters.violations("other")).isZero();
        var json = counters.toJson();
        assertThat(json.getAsJsonObject("violations").size()).isZero();
        var audit = json.getAsJsonObject("rejections");
        assertThat(audit.size()).isEqualTo(2);
        assertThat(audit.get("first skirmish_permission").getAsInt()).isEqualTo(2);
        assertThat(audit.get("other skirmish_permission").getAsInt()).isEqualTo(1);
    }

    @Test
    void protocolViolationsAlsoAppearInTheAuditWithNoUnrelatedPlayerCharge() {
        var counters = new NetCounters();
        counters.violation("first", IngressReason.INVALID_UI_QUERY);
        counters.violation("first", IngressReason.INVALID_UI_QUERY);
        counters.rejected("first", IngressReason.UI_QUERY_RATE);
        assertThat(counters.violations("first")).isEqualTo(2);
        assertThat(counters.violations("other")).isZero();
        var json = counters.toJson();
        assertThat(json.getAsJsonObject("violations")
                        .get("first invalid_ui_query")
                        .getAsInt())
                .isEqualTo(2);
        assertThat(json.getAsJsonObject("rejections")
                        .get("first invalid_ui_query")
                        .getAsInt())
                .isEqualTo(2);
        assertThat(json.getAsJsonObject("rejections").get("first ui_query_rate").getAsInt())
                .isEqualTo(1);
    }
}
