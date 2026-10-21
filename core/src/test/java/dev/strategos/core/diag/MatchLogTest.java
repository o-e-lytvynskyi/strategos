package dev.strategos.core.diag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.MatchId;
import org.junit.jupiter.api.Test;

class MatchLogTest {

    @Test
    void prefixNamesTheMatchAndTheTick() {
        assertThat(MatchLog.prefix(new MatchId(7), 1234)).isEqualTo("[m7 t1234] ");
    }

    @Test
    void prefixMatchesTheLogScanPattern() {
        // The T10 log scan requires "[m<id> t<tick>] " at the start of every match-code message.
        assertThat(MatchLog.prefix(new MatchId(12), 0) + "spawned").matches("\\[m\\d+ t\\d+\\] .*");
    }
}
