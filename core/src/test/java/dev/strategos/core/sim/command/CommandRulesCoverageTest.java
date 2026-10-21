package dev.strategos.core.sim.command;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.CommandKinds;
import org.junit.jupiter.api.Test;

class CommandRulesCoverageTest {

    @Test
    void m0RulesCoverTheDefaultCatalogueExactly() {
        assertThatCode(() -> CommandRules.m0().verifyCovers(CommandKinds.DEFAULT))
                .doesNotThrowAnyException();
    }

    @Test
    void aMissingRuleFailsTheCheck() {
        assertThatThrownBy(() -> new CommandRules().register(new MoveRule()).verifyCovers(CommandKinds.DEFAULT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strategos:stop");
    }

    @Test
    void aSecondRuleForAKindThrows() {
        assertThatThrownBy(() -> CommandRules.m0().register(new MoveRule()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategos:move");
    }
}
