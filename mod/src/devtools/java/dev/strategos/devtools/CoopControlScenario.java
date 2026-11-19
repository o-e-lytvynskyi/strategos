package dev.strategos.devtools;

import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.select.Seen;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.PresentationSetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.menu.ControlSharingScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;

/** Actual P1/F0 admission and private/palette frames; named-ally DTOs exercise sharing presentation only. */
final class CoopControlScenario implements Scenario {
    private static final String[] FRAMES = {"coop_unit", "coop_primary", "shared_building", "sharing_panel"};
    private volatile int worker;
    private volatile int ownBuilding;
    private volatile int alliedBuilding;
    private volatile long ownFood;
    private volatile int food;
    private volatile boolean frozen;
    private int idle;
    private int phase;
    private int age;
    private int captures;
    private int calm;
    private boolean clientConfigured;
    private boolean waitingCapture;
    private volatile @Nullable String setupFailure;

    @Override
    public String name() {
        return "m7-coop";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        var match = ScenarioWorld.matchAtAnchor(server, 48, 0, false);
        var f0 = new FactionSlot(0);
        var f1 = new FactionSlot(1);
        var p1 = new PlayerSlot(1);
        var p2 = new PlayerSlot(2);
        var diplomacy = DiplomacyState.identity(2)
                .withTeam(f1, 0)
                .bind(p1, f0)
                .bind(p2, f1)
                .grant(p2, p1)
                .lockTeams();
        var styles = new FactionPresentation(List.of(
                new FactionPresentation.Style("strategos:placeholder", 14),
                new FactionPresentation.Style("strategos:placeholder", 11)));
        match.initializeDiplomacy(new DiplomacySetup(diplomacy.snapshot()), new PresentationSetup(styles));
        match.submit(PlayerSlot.ADMIN, new Grant(f0, "food", 880));
        match.submit(PlayerSlot.ADMIN, new Grant(f0, "wood", 440));
        match.submit(PlayerSlot.ADMIN, new Grant(f1, "food", 11_111));
        var centre = new TemplateId("strategos:building/civic_centre");
        match.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, f0, 5, 5, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnBuilding(centre, f1, 27, 8, 0));
        match.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, f0, new Vec2d(17, 17), 1, 0));
        match.addListener(out -> {
            var core = match.core();
            if (core == null) return;
            var queries = core.queries();
            food = queries.config().rules().economy().indexOf("food");
            ownFood = queries.treasury(f0).stock().get(food);
            if (out.tick() == 1) {
                worker = queries.unitHandlesOf(f0)[0];
                ownBuilding = queries.buildings().stream()
                        .filter(b -> b.faction() == 0)
                        .findFirst()
                        .orElseThrow()
                        .handle();
                alliedBuilding = queries.buildings().stream()
                        .filter(b -> b.faction() == 1)
                        .findFirst()
                        .orElseThrow()
                        .handle();
                for (int i = 0; i < 8; i++) {
                    match.submit(PlayerSlot.ADMIN, new Train(ownBuilding, TemplateSet.WORKER));
                    match.submit(PlayerSlot.ADMIN, new Train(alliedBuilding, TemplateSet.WORKER));
                }
                var player = ScenarioWorld.player(server);
                StrategosServer.get().matches().join(match, player.getUUID(), p1);
                StrategosServer.get().commanders().enter(player, match);
                StrategosServer.get().net().joined(match, player, p1);
            }
            idle = StrategosServer.get().writer().pending(server.overworld()) == 0 ? idle + 1 : 0;
            if (out.tick() == 80) {
                if (idle < 8) setupFailure = "block writer not idle at the deterministic freeze tick";
                else {
                    ScenarioWorld.run(server, "tick freeze");
                    frozen = true;
                }
            }
        });
        var anchor = ScenarioWorld.anchor(server);
        ScenarioWorld.camera(server, anchor.getX() + 0.5, anchor.getY() + 28, anchor.getZ() + 0.5, 0, 80);
        ctx.holdCamera(anchor.getX() + 0.5, anchor.getY() + 28, anchor.getZ() + 0.5, 0, 80);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        String failed = setupFailure;
        if (failed != null) return failure(ctx, failed);
        if (tick > 600) return failure(ctx, "co-op presentation stalled at phase " + phase);
        if (!clientConfigured) {
            ScenarioWorld.renderDistance(mc, 4);
            clientConfigured = true;
        }
        var join = StrategosClient.match();
        var publicView = StrategosClient.presentation();
        var faction = StrategosClient.model().faction();
        if (!frozen || join == null || publicView == null || faction == null || tick < 50) return false;
        if (join.slot().value() != 1
                || join.primaryFaction() != 0
                || StrategosClient.rts().selection().faction() != 0)
            return failure(ctx, "player identity replaced the co-op primary perspective");
        if (faction.faction() != 0 || faction.stock().get(food) != ownFood)
            return failure(ctx, "private treasury differs from the authoritative primary owner");
        if (!StrategosClient.rts().active()) {
            StrategosClient.rts().enter();
            return false;
        }
        var own = building(ownBuilding);
        var allied = building(alliedBuilding);
        if (own == null || allied == null || StrategosClient.model().unit(Handle.index(worker)) == null) return false;
        if (!own.own()
                || own.queue().isEmpty()
                || allied.own()
                || !allied.queue().isEmpty()
                || allied.rally()) return failure(ctx, "primary queue or granted-ally private projection is wrong");
        if (own.state() != CommandCard.COMPLETE || allied.state() != CommandCard.COMPLETE) return false;
        calm = mc.levelRenderer.hasRenderedAllSections() ? calm + 1 : 0;
        if (calm < 20 || !StrategosClient.hud().feedShown().isEmpty() || ctx.captureName != null) return false;
        if (waitingCapture) {
            waitingCapture = false;
            if (age >= 20) {
                phase++;
                age = 0;
            }
        }
        if (phase >= FRAMES.length) {
            ctx.result.addProperty("frames", captures);
            ctx.result.addProperty("playerSlot", 1);
            ctx.result.addProperty("primaryFaction", 0);
            ctx.result.addProperty(
                    "source",
                    "actual co-op admission and palette/private frames; decoded named-ally visual DTO only for sharing panel");
            ctx.result.addProperty("ok", captures == 8);
            return true;
        }
        if (age == 0) {
            var selection = StrategosClient.rts().selection();
            selection.clear();
            if (phase == 0) selection.click(new Seen(worker, 0, TemplateSet.WORKER, 0, 0, 0, 0), false);
            else if (phase == 1) selection.clickBuilding(ownBuilding, 0);
            else if (phase == 2) selection.clickBuilding(alliedBuilding, 1);
            else {
                var self = publicView.participants().stream()
                        .filter(p -> p.player().equals(join.slot()))
                        .findFirst()
                        .orElseThrow();
                var names = new MatchPresentation(
                        join.matchId(),
                        publicView.revision() + 1,
                        publicView.factions(),
                        List.of(
                                new MatchPresentation.Participant(new PlayerSlot(0), 0, "AlphaCoop", true),
                                self,
                                new MatchPresentation.Participant(new PlayerSlot(2), 1, "BravoAlly", true)));
                var visualNames =
                        WireCodec.decode(MatchPresentation.WIRE, WireCodec.encode(MatchPresentation.WIRE, names));
                StrategosClient.presentation(visualNames);
                mc.setScreen(new ControlSharingScreen(null));
            }
            if (phase < 3) mc.setScreen(null);
        }
        age++;
        if (age == 10 || age == 20) {
            ctx.capture(FRAMES[phase] + (age == 20 ? "_2" : ""));
            captures++;
            waitingCapture = true;
        }
        return false;
    }

    private static @Nullable BuildingState.Entry building(int handle) {
        return StrategosClient.model().buildings().stream()
                .filter(b -> b.handle() == handle)
                .findFirst()
                .orElse(null);
    }

    private static boolean failure(ScenarioContext ctx, String message) {
        ctx.result.addProperty("failure", message);
        ctx.result.addProperty("ok", false);
        return true;
    }
}
