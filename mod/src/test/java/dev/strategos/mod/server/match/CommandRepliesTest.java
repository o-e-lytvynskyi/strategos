package dev.strategos.mod.server.match;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CommandRepliesTest {
    @Test
    void identicalNumberAndBodyCannotOverwriteAnOlderOrigin() {
        var replies = new CommandReplies();
        var old = envelope();
        var fresh = envelope();
        Consumer<CommandResult> oldReply = ignored -> {};
        Consumer<CommandResult> freshReply = ignored -> {};
        replies.register(old, oldReply);
        replies.register(fresh, freshReply);

        assertThat(replies.take(result(old))).isSameAs(oldReply);
        assertThat(replies.take(result(fresh))).isSameAs(freshReply);
        assertThat(replies.take(result(old))).isNull();
        assertThat(replies.take(result(fresh))).isNull();
    }

    @Test
    void restoredOrAiOrNoReplyCommandsCannotConsumeALiveCallback() {
        var replies = new CommandReplies();
        var fresh = envelope();
        Consumer<CommandResult> callback = ignored -> {};
        replies.register(fresh, callback);

        assertThat(replies.take(result(envelope()))).isNull();
        assertThat(replies.take(result(envelope()))).isNull();
        assertThat(replies.take(result(fresh))).isSameAs(callback);
    }

    @Test
    void aResultWithoutAnOriginCannotConsumeALiveCallback() {
        var replies = new CommandReplies();
        var fresh = envelope();
        Consumer<CommandResult> callback = ignored -> {};
        replies.register(fresh, callback);

        assertThat(replies.take(
                        new CommandResult(new PlayerSlot(0), 1, "strategos:stop", CommandOutcome.ACCEPTED, 1, 0, null)))
                .isNull();
        assertThat(replies.take(result(fresh))).isSameAs(callback);
    }

    private static CommandEnvelope envelope() {
        return new CommandEnvelope(new PlayerSlot(0), 1, 0, new Stop(UnitSet.of(1)));
    }

    private static CommandResult result(CommandEnvelope source) {
        return new CommandResult(
                source.issuer(),
                source.clientSeq(),
                "strategos:stop",
                CommandOutcome.ACCEPTED,
                1,
                0,
                null,
                List.of(),
                source);
    }
}
