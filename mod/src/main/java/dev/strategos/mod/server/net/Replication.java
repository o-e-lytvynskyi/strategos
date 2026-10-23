package dev.strategos.mod.server.net;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/**
 * Replication tier 2's sender (NET-08, 04 §12.4): every {@value #SUMMARY_EVERY} server ticks, after the matches stepped
 * and before the gateway's flush, each running match's factions that have seated players get their summary frame, own
 * units and the enemies {@link #visibility} lets them see. Server thread only.
 */
public final class Replication {
    /** Ticks between two summary frames: 2 Hz. */
    public static final int SUMMARY_EVERY = 10;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final NetGateway net;
    private Visibility visibility = Visibility.ALL;

    public Replication(MinecraftServer server, Supplier<MatchRegistry> matches, NetGateway net) {
        this.server = server;
        this.matches = matches;
        this.net = net;
    }

    /** Who sees which enemy; every unit until fog (M6), a test's own rule to check the filter. */
    public void visibility(Visibility rule) {
        this.visibility = rule;
    }

    public void tick() {
        if (server.getTickCount() % SUMMARY_EVERY != 0) {
            return;
        }
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || rt.lifecycle() != MatchLifecycle.RUNNING) {
                continue;
            }
            for (int f = 0; f < core.queries().config().factions(); f++) {
                FactionSlot faction = new FactionSlot(f);
                if (rt.roster().of(faction).isEmpty()) {
                    continue;
                }
                net.send(
                        new Audience.Faction(rt.id(), faction),
                        new UnitSummaryPayload(ReplicationService.summary(
                                core.queries(), rt.id().value(), faction, visibility)));
            }
        }
    }
}
