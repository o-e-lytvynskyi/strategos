package dev.strategos.mod.server.tick;

import dev.strategos.mod.server.match.FaultPolicy;
import dev.strategos.mod.server.match.MatchRuntime;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Runs match code so that a fault stops only its match (04 §2.4): the match becomes FAULTED, a report goes to
 * {@code <server dir>/strategos/faults/<match>-<tick>/}, the fault is logged once, players standing in the match get a
 * notice, and the server keeps running; under the RETHROW policy the fault is rethrown after it was recorded.
 */
public final class FaultBarrier {
    private final MinecraftServer server;

    public FaultBarrier(MinecraftServer server) {
        this.server = server;
    }

    /** Where fault reports go. */
    public Path faultDir() {
        return server.getServerDirectory().resolve("strategos").resolve("faults");
    }

    public void run(MatchRuntime rt, String where, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException | StackOverflowError | AssertionError t) {
            if (rt.faultMessage() == null) {
                rt.fault(t, where);
                Path report = FaultReport.write(faultDir(), rt, t, where);
                rt.log()
                        .error(
                                rt.tick(),
                                "match fault in " + where + "; the match is now " + rt.lifecycle() + "; report in "
                                        + report,
                                t);
                notifyPlayers(rt);
            }
            if (rt.faultPolicy() == FaultPolicy.RETHROW) {
                throw new MatchFaultException(rt.id(), where, t);
            }
        }
    }

    private void notifyPlayers(MatchRuntime rt) {
        @Nullable ServerLevel level = server.getLevel(rt.space().level());
        if (level == null) {
            return;
        }
        Component notice =
                Component.literal("Match " + rt.id() + " stopped after an error; report in strategos/faults/");
        for (ServerPlayer player : level.players()) {
            if (rt.space().contains(player.blockPosition())) {
                player.sendSystemMessage(notice);
            }
        }
    }
}
