package dev.strategos.mod.server.match;

import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Transient replies belong to submissions, never to reusable player/sequence numbers. */
final class CommandReplies {
    private final Map<CommandEnvelope, Consumer<CommandResult>> pending = new IdentityHashMap<>();

    void register(CommandEnvelope submission, Consumer<CommandResult> reply) {
        pending.put(submission, reply);
    }

    @Nullable Consumer<CommandResult> take(CommandResult result) {
        CommandEnvelope source = result.source();
        return source == null ? null : pending.remove(source);
    }
}
