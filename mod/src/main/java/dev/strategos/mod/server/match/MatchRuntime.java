package dev.strategos.mod.server.match;

import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.mod.server.body.AgentTracker;
import dev.strategos.mod.server.body.BodyRegistry;
import dev.strategos.mod.server.body.SpawnBudget;
import dev.strategos.mod.server.persist.MatchData;
import dev.strategos.mod.server.tick.InputCollector;
import dev.strategos.mod.server.tick.OutputAppliers;
import dev.strategos.mod.server.world.PageJobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
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
    private final @Nullable MatchData data;
    private final BodyRegistry bodies;
    private final SpawnBudget spawnBudget;
    private final MatchLog log;
    private final @Nullable ResourceLocation map;
    private final PageJobs pages;
    private final MatchRoster roster;
    private final AgentTracker agents;
    private final List<CommandEnvelope> inbox = new ArrayList<>();
    private final List<ExternalDamage> damage = new ArrayList<>();
    private final Map<ReplyKey, Consumer<CommandResult>> replies = new HashMap<>();
    private final List<Consumer<TickOutputs>> listeners = new ArrayList<>();
    private MatchLifecycle lifecycle = MatchLifecycle.CREATED;
    private @Nullable String faultMessage;
    private int nextClientSeq = 1;
    private Runnable onLifecycleChange = () -> {};
    private @Nullable MapImporter importer;
    private @Nullable String importedHash;
    private long importMillis = -1;

    MatchRuntime(
            MatchId id,
            MatchSpace space,
            MatchSource source,
            FaultPolicy faultPolicy,
            boolean persistent,
            @Nullable MatchCore core,
            @Nullable MatchData data,
            SpawnBudget spawnBudget,
            @Nullable ResourceLocation map) {
        this.map = map;
        this.pages = new PageJobs(
                space, core != null ? core.queries().config().rules().pageK() : MatchRules.DEFAULTS.pageK());
        this.id = id;
        this.space = space;
        this.source = source;
        this.faultPolicy = faultPolicy;
        this.persistent = persistent;
        this.core = core;
        this.data = data;
        this.spawnBudget = spawnBudget;
        this.bodies = new BodyRegistry(id, space);
        this.log = new MatchLog(LoggerFactory.getLogger(MatchRuntime.class), id);
        this.roster = data != null ? data.roster() : new MatchRoster();
        this.agents = new AgentTracker(space);
    }

    /** Who sits in the match; a saved match's is its blob's. */
    public MatchRoster roster() {
        return roster;
    }

    /** The match's terrain pages: dirtied by block changes, captured, built and delivered as inputs. */
    public PageJobs pages() {
        return pages;
    }

    /** The map package the match was made on, or null. */
    public @Nullable ResourceLocation map() {
        return map;
    }

    /** The import that prepares the match, until it is done; null for a match without a map or once it is RUNNING. */
    public @Nullable MapImporter importer() {
        return importer;
    }

    void importer(@Nullable MapImporter importer) {
        this.importer = importer;
    }

    /** The terrain hash the finished import read back, or null before (or without a map). */
    public @Nullable String importedHash() {
        return importedHash;
    }

    /** How long the import took, or -1. */
    public long importMillis() {
        return importMillis;
    }

    void imported(@Nullable String hash, long millis) {
        this.importedHash = hash;
        this.importMillis = millis;
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

    /** A saved match's blob; null for a match that is never saved. */
    public @Nullable MatchData data() {
        return data;
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

    /** Damage one of the match's bodies took, for the next step's inputs, after the commands. */
    public void damage(ExternalDamage hit) {
        damage.add(hit);
    }

    /**
     * A player's command, numbered {@code clientSeq} by its client, for the next step; {@code reply} gets its result.
     * Only a RUNNING or PAUSED match takes commands.
     */
    public void submit(PlayerSlot issuer, Command command, int clientSeq, Consumer<CommandResult> reply) {
        if (lifecycle != MatchLifecycle.RUNNING && lifecycle != MatchLifecycle.PAUSED) {
            throw new MatchRefusedException("match " + id + " is " + lifecycle + " and takes no commands");
        }
        inbox.add(new CommandEnvelope(issuer, clientSeq, requireCore().queries().tick(), command));
        replies.put(new ReplyKey(issuer.value(), clientSeq), reply);
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
        long next = c.queries().tick() + 1;
        AgentUpdate agents = this.agents.track(level, roster.members());
        TickOutputs out = c.step(InputCollector.collect(next, inbox, pages.due(next), agents, damage));
        long start = System.nanoTime();
        OutputAppliers.apply(this, level, out);
        bodies.materialise(level, c.queries(), spawnBudget);
        if (data != null) {
            data.setDirty(); // a flag only: autosave and the stop encode the snapshot at a tick boundary
        }
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
        onLifecycleChange.run();
    }

    /** The registry's hook: a saved match's record follows its lifecycle. */
    void onLifecycleChange(Runnable hook) {
        this.onLifecycleChange = hook;
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
        onLifecycleChange.run();
    }

    /** A match restored FAULTED: why, as the record or the quarantine says. */
    void restoredFaulted(String reason) {
        faultMessage = reason;
        lifecycle = lifecycle.transition(MatchLifecycle.FAULTED);
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
