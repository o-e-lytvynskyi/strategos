package dev.strategos.devtools;

import dev.strategos.core.client.hud.Minimap;
import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.client.hud.RtsHud;
import dev.strategos.mod.client.rts.RtsScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.lwjgl.glfw.GLFW;

/** Real minimap input, integrated-server echo, world/minimap rendering and marker expiry. */
final class TeamHudScenario implements Scenario {
    private static final int X = 24;
    private static final int Z = 23;
    private static final PlayerSlot HOST = new PlayerSlot(0);
    private int phase;
    private int calm;
    private int clickedAt;
    private boolean framed;
    private boolean frozen;
    private boolean configured;

    @Override
    public String name() {
        return "m7-team-hud";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        var match = ScenarioWorld.matchAtAnchor(server, 48, 0, false);
        match.initializeDiplomacy(new DiplomacySetup(DiplomacyState.identity(2)
                .withTeam(new FactionSlot(1), 0)
                .lockTeams()
                .snapshot()));
        var anchor = ScenarioWorld.anchor(server);
        ScenarioWorld.camera(server, anchor.getX() + 0.5, anchor.getY() + 36, anchor.getZ() + 0.5, 0, 80);
        match.addListener(out -> {
            if (out.tick() != 1) return;
            var player = ScenarioWorld.player(server);
            var strategos = StrategosServer.get();
            strategos.matches().join(match, player.getUUID(), HOST);
            strategos.commanders().enter(player, match);
            strategos.net().joined(match, player, HOST);
        });
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        if (tick > 750) return fail(ctx, "team HUD did not finish phase " + phase);
        if (StrategosClientConfig.INSTANCE.edgePan.get()) StrategosClientConfig.INSTANCE.edgePan.set(false);
        if (!configured) {
            ScenarioWorld.renderDistance(mc, 4);
            configured = true;
        }
        if (ctx.captureName != null) return false;
        var join = StrategosClient.match();
        var view = StrategosClient.presentation();
        if (join == null || view == null || mc.level == null) return false;
        var rts = StrategosClient.rts();
        if (!rts.active()) {
            rts.enter();
            return false;
        }
        var rig = rts.rig();
        if (rig == null) return false;
        double x = join.originX() + X + 0.5;
        double z = join.originZ() + Z + 0.5;
        if (!framed) {
            rig.zoomTo(36);
            rts.centreOn(x, z);
            framed = true;
            return false;
        }
        var player = mc.player;
        boolean settled = player != null
                && Math.hypot(rig.focusX() - x, rig.focusZ() - z) < 0.5
                && Math.hypot(
                                player.getX() - rig.pose().x(),
                                player.getZ() - rig.pose().z())
                        < 0.5
                && mc.levelRenderer.hasRenderedAllSections()
                && StrategosClient.hud().minimapPixel(0, 0) != 0
                && StrategosClient.hud().minimapPixel(join.sizeX() - 1, join.sizeZ() - 1) != 0;
        calm = settled ? calm + 1 : 0;
        if (calm < 20) return false;
        if (!frozen) {
            var server = mc.getSingleplayerServer();
            if (server == null) return fail(ctx, "integrated server absent");
            server.execute(() -> ScenarioWorld.run(server, "tick freeze"));
            frozen = true;
            calm = 0;
            return false;
        }

        switch (phase) {
            case 0 -> {
                var host = view.participants().stream()
                        .filter(p -> p.player().equals(HOST))
                        .findFirst()
                        .orElseThrow();
                if (host.team() != 0 || !host.connected()) return fail(ctx, "real host roster row wrong");
                var names = new MatchPresentation(
                        view.matchId(),
                        view.revision() + 1,
                        view.factions(),
                        List.of(host, new MatchPresentation.Participant(new PlayerSlot(1), 1, 0, "Ally", false)));
                var frame = WireCodec.decode(MatchPresentation.WIRE, WireCodec.encode(MatchPresentation.WIRE, names));
                StrategosClient.presentation(frame);
                ctx.result.addProperty("rosterFixture", "real host with an encoded offline ally row");
                ctx.capture("team_roster");
                phase++;
            }
            case 1 -> {
                if (!(mc.screen instanceof RtsScreen screen)) return false;
                var minimap = new Minimap(RtsHud.layout().minimap(), join.sizeX(), join.sizeZ());
                double[] at = minimap.toMap(X + 0.5, Z + 0.5);
                screen.mouseClicked(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
                screen.mouseReleased(at[0], at[1], GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
                clickedAt = tick;
                phase++;
            }
            case 2 -> {
                var pings = StrategosClient.teamPings().active();
                if (pings.isEmpty()) {
                    if (tick - clickedAt > 50) return fail(ctx, "real middle click received no allied echo");
                    return false;
                }
                var ping = pings.getFirst();
                if (pings.size() != 1 || !ping.player().equals(HOST) || ping.x() != X || ping.z() != Z)
                    return fail(ctx, "real echo changed the sender or map coordinates");
                ctx.result.addProperty("echoX", ping.x());
                ctx.result.addProperty("echoZ", ping.z());
                ctx.capture("team_ping");
                phase++;
            }
            case 3 -> {
                if (!StrategosClient.teamPings().active().isEmpty()) return false;
                ctx.result.addProperty("expired", true);
                ctx.capture("team_expired");
                phase++;
            }
            default -> {
                ctx.result.addProperty("ok", true);
                return true;
            }
        }
        return false;
    }

    private static boolean fail(ScenarioContext ctx, String message) {
        ctx.result.addProperty("failure", message);
        ctx.result.addProperty("ok", false);
        return true;
    }
}
