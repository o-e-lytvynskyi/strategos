package dev.strategos.devtools;

import dev.strategos.core.client.model.ClientResume;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;

/** Real Save and Quit/reopen, automatic UUID reclaim, then a different world with colliding identities. */
final class ReconnectScenario implements Scenario {
    private volatile int world;
    private volatile boolean prepared;
    private volatile int matchId;
    private volatile int unit;
    private volatile @Nullable MatchRuntime original;
    private volatile @Nullable String setupFailure;
    private ClientResume.@Nullable Pose saved;
    private long oldEpoch;
    private int phase;
    private int age;

    @Override
    public String name() {
        return "m7-reconnect";
    }

    @Override
    public boolean openWorld(Minecraft mc) {
        if (world != 1) return false;
        mc.createWorldOpenFlows().openWorld(worldName(), () -> {
            throw new IllegalStateException("could not reopen the scenario's saved world");
        });
        return true;
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        var holder = StrategosServer.get();
        var player = ScenarioWorld.player(server);
        if (world == 1) {
            var restored = holder.matches().get(new MatchId(matchId));
            var seat = holder.matches().seatOf(player.getUUID());
            if (restored == null
                    || restored == original
                    || seat == null
                    || seat.match() != restored
                    || seat.slot().value() != 0
                    || !restored.roster().isConnected(player.getUUID())
                    || restored.lifecycle() != MatchLifecycle.PAUSED) {
                setupFailure = "saved-world reopen did not automatically reclaim the original paused UUID seat";
            }
            prepared = true;
            return;
        }
        var match = ScenarioWorld.matchAtAnchor(server, 96, world == 0 ? 2 : 1, world == 0);
        if (world == 0) {
            original = match;
            matchId = match.id().value();
        } else if (match.id().value() != matchId) {
            setupFailure = "second world did not reproduce the numeric match identity collision";
        }
        match.addListener(out -> {
            if (out.tick() != 3) return;
            int handle = Objects.requireNonNull(match.core()).queries().unitHandles()[0];
            if (world == 0) unit = handle;
            else if (handle != unit) setupFailure = "second world did not reproduce the unit handle collision";
            holder.matches().pause(match.id());
            holder.matches().join(match, player.getUUID(), new PlayerSlot(0));
            holder.commanders().enter(player, match);
            holder.net().joined(match, player, new PlayerSlot(0));
            prepared = true;
        });
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        String problem = setupFailure;
        if (problem != null) return fail(ctx, problem);
        StrategosClientConfig.INSTANCE.edgePan.set(false);
        var join = StrategosClient.match();
        var rts = StrategosClient.rts();
        var rig = rts.rig();
        if (!prepared
                || join == null
                || rig == null
                || !rts.active()
                || StrategosClient.model().units().isEmpty()) return false;
        if (phase == 0) {
            if (join.matchId() != matchId || join.primaryFaction() != 0 || StrategosClient.epoch() <= 0)
                return fail(ctx, "initial private handshake did not confirm the actual match/primary");
            oldEpoch = StrategosClient.epoch();
            rig.zoomTo(60);
            rts.centreOn(join.originX() + 12, join.originZ() + 20);
            phase = 1;
            age = 0;
            return false;
        }
        if (phase == 1) {
            if (++age < 60) return false;
            var selection = rts.selection();
            selection.set(List.of(unit));
            rts.groups().set(1, selection);
            if (!rts.groups().members(1).equals(List.of(unit)))
                return fail(ctx, "original control group did not contain the real owned handle");
            saved = new ClientResume.Pose(rig.focusX(), rig.focusZ(), rig.yaw(), rig.height());
            ctx.capture("before_reconnect");
            phase = 2;
            age = 0;
            return false;
        }
        if (phase == 2) {
            if (++age < 4) return false;
            world = 1;
            prepared = false;
            ctx.switchWorld(worldName(), seed());
            phase = 3;
            age = 0;
            return false;
        }
        if (phase == 3) {
            if (++age < 30) return false;
            var pose = Objects.requireNonNull(saved);
            if (StrategosClient.epoch() == oldEpoch || join.matchId() != matchId || join.primaryFaction() != 0)
                return fail(ctx, "reopened connection did not deliver a fresh confirmed match epoch");
            if (!rts.groups().members(1).equals(List.of(unit)))
                return fail(ctx, "actual reconnect lost the valid client-owned control group");
            if (Math.abs(rig.focusX() - pose.focusX()) > 0.5
                    || Math.abs(rig.focusZ() - pose.focusZ()) > 0.5
                    || Math.abs(rig.height() - pose.height()) > 0.5
                    || Math.abs(rig.yaw() - pose.yaw()) > 0.5)
                return fail(ctx, "actual reconnect did not retain the camera pose through server confirmation");
            ctx.result.addProperty("automaticUuidReclaim", true);
            ctx.result.addProperty("freshEpoch", true);
            ctx.result.addProperty("cameraRestored", true);
            ctx.result.addProperty("groupsRestored", true);
            ctx.capture("after_reconnect");
            phase = 4;
            age = 0;
            return false;
        }
        if (phase == 4) {
            if (++age < 4) return false;
            world = 2;
            prepared = false;
            ctx.switchWorld("strategos-m7-reconnect-other", seed() + 1);
            phase = 5;
            age = 0;
            return false;
        }
        if (phase == 5) {
            if (++age < 30) return false;
            if (join.matchId() != matchId || !rts.groups().members(1).isEmpty())
                return fail(ctx, "another world with colliding identities restored the old control group");
            var pose = Objects.requireNonNull(saved);
            if (Math.hypot(rig.focusX() - pose.focusX(), rig.focusZ() - pose.focusZ()) <= 1)
                return fail(ctx, "another world's camera was restored from the old realm");
            ctx.result.addProperty("realmIsolation", true);
            ctx.capture("other_world");
            phase = 6;
            age = 0;
            return false;
        }
        if (++age < 4) return false;
        ctx.result.addProperty("ok", true);
        return true;
    }

    private static boolean fail(ScenarioContext ctx, String reason) {
        ctx.result.addProperty("ok", false);
        ctx.result.addProperty("reason", reason);
        return true;
    }
}
