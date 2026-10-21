package dev.strategos.mod.server.match;

import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.mod.server.body.BodyRegistry;
import dev.strategos.mod.server.body.SpawnBudget;
import dev.strategos.mod.server.tick.InputCollector;
import dev.strategos.mod.server.tick.OutputAppliers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * One match in the world: its core, its bodies, its lifecycle and the commands waiting for the next step. Only the
 * server thread touches it; only the match registry makes one.
 */
public final class MatchRuntime {
    private final MatchId id;
    private final MatchSpace space;
    private final MatchSource source;
    private final FaultPolicy faultPolicy;
    private final boolean persistent;
    private final @Nullable MatchCore core;
    private final BodyRegistry bodies;
    private final SpawnBudget spawnBudget;
    private final MatchLog log;
    private final List<CommandEnvelope> inbox = new ArrayList<>();
    private final Map<ReplyKey, Consumer<CommandResult>> replies = new HashMap<>();
    private final List<Consumer<TickOutputs>> listeners = new ArrayList<>();
    private MatchLifecycle lifecycle = MatchLifecycle.CREATED;
    private @Nullable String faultMessage;
    private int nextClientSeq = 1;

    MatchRuntime(
            MatchId id,
            MatchSpace space,
            MatchSource source,
            FaultPolicy faultPolicy,
            boolean persistent,
            @Nullable MatchCore core,
            SpawnBudget spawnBudget) {
        this.id = id;
        this.space = space;
        this.source = source;
        this.faultPolicy = faultPolicy;
        this.persistent = persistent;
        this.core = core;
        this.spawnBudget = spawnBudget;
        this.bodies = new BodyRegistry(id, space);
        this.log = new MatchLog(LoggerFactory.getLogger(MatchRuntime.class), id);
    }

    public MatchId id() {
        return id;
    }

    public MatchSpace space() {
        return space;
    }

    public MatchLifecycle lifecycle() {
        return lifecycle;
    }

    public MatchSource source() {
        return source;
    }

    /** Null only for a FAULTED match that never restored. */
    public @Nullable MatchCore core() {
        return core;
    }

    public BodyRegistry bodies() {
        return bodies;
    }

    public FaultPolicy faultPolicy() {
        return faultPolicy;
    }

    public boolean persistent() {
        return persistent;
    }

    public @Nullable String faultMessage() {
        return faultMessage;
    }

    /** The adapter's log for this match: every line starts with {@code [m<id> t<tick>]}. */
    public MatchLog log() {
        return log;
    }

    /** The core's last completed tick, or -1 without a core. */
    public long tick() {
        return core == null ? -1 : core.queries().tick();
    }

    /**
     * Queues a command for the next step, received this tick with this match's next sequence number; returns that
     * number. Only a RUNNING or PAUSED match takes commands.
     */
    public int submit(PlayerSlot issuer, Command command) {
        if (lifecycle != MatchLifecycle.RUNNING && lifecycle != MatchLifecycle.PAUSED) {
            throw new MatchRefusedException("match " + id + " is " + lifecycle + " and takes no commands");
        }
        int seq = nextClientSeq++;
        inbox.add(new CommandEnvelope(issuer, seq, requireCore().queries().tick(), command));
        return seq;
    }

    /** As {@link #submit(PlayerSlot, Command)}, and {@code reply} gets the command's result after its step. */
    public int submit(PlayerSlot issuer, Command command, Consumer<CommandResult> reply) {
        int seq = submit(issuer, command);
        replies.put(new ReplyKey(issuer.value(), seq), reply);
        return seq;
    }

    /** The reply registered for a result, removed; null when the command had none. */
    public @Nullable Consumer<CommandResult> takeReply(CommandResult result) {
        return replies.remove(new ReplyKey(result.issuer().value(), result.clientSeq()));
    }

    /** Tests and debug tools: every step's outputs, after they were applied. */
    public void addListener(Consumer<TickOutputs> listener) {
        listeners.add(listener);
    }

    /** The tick driver's step: the inbox becomes the next tick's inputs; outputs and bodies are applied after it. */
    public void tick(ServerLevel level) {
        MatchCore c = requireCore();
        TickOutputs out = c.step(InputCollector.collect(c.queries().tick() + 1, inbox));
        long start = System.nanoTime();
        OutputAppliers.apply(this, level, out);
        bodies.materialise(level, c.queries(), spawnBudget);
        c.timers().recordAdapter(System.nanoTime() - start);
        for (Consumer<TickOutputs> listener : listeners) {
            listener.accept(out);
        }
    }

    /** The tick driver's work for a PAUSED match: its units get their bodies, nothing moves. */
    public void materialiseOnly(ServerLevel level) {
        bodies.materialise(level, requireCore().queries(), spawnBudget);
    }

    /** The match registry's lifecycle step. */
    void transition(MatchLifecycle next) {
        lifecycle = lifecycle.transition(next);
    }

    /** The fault barrier's record of the first fault: where and what, and the match is FAULTED where it can be. */
    public void fault(Throwable t, String where) {
        if (faultMessage != null) {
            return;
        }
        faultMessage = where + ": " + t;
        if (lifecycle.canTransitionTo(MatchLifecycle.FAULTED)) {
            lifecycle = MatchLifecycle.FAULTED;
        }
    }

    private MatchCore requireCore() {
        MatchCore c = core;
        if (c == null) {
            throw new IllegalStateException("match " + id + " has no core");
        }
        return c;
    }

    private record ReplyKey(int issuer, int clientSeq) {}
}
