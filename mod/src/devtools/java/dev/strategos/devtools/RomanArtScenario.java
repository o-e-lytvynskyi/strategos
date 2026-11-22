package dev.strategos.devtools;

import dev.strategos.core.content.CivilisationArt;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.PresentationSetup;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Roman silhouettes at a fixed RTS zoom, followed by the same town with a damaged house and an active build site. */
final class RomanArtScenario implements Scenario {
    private static final int HEIGHT = 60;
    private static final int FOCUS_X = 64;
    private static final int FOCUS_Z = 64;
    private static final int ROLES_Z = 104;
    private static final int NEW_HOUSE_X = 70;
    private static final int NEW_HOUSE_Z = 68;
    private static final long STOP_PROGRESS = 380;
    private static final FactionSlot BLUE = new FactionSlot(0);
    private static final FactionSlot RED = new FactionSlot(1);
    private static final PlayerSlot HOST = new PlayerSlot(0);
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");

    private record Site(String type, int x, int z) {}

    private static final List<Site> TOWN = List.of(
            new Site("civic_centre", 58, 52),
            new Site("house", 49, 64),
            new Site("barracks", 70, 52),
            new Site("storehouse", 51, 51),
            new Site("farmstead", 75, 67),
            new Site("tower", 45, 54),
            new Site("arsenal", 54, 67));

    // Scenario ticks and the integrated-server listener run on separate threads.
    private volatile @Nullable MatchRuntime match;
    private volatile boolean townReady;
    private volatile long construction;
    private boolean placed;
    private int stage;
    private int calm;

    @Override
    public String name() {
        return "m7-art";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        var anchor = ScenarioWorld.anchor(server);
        ScenarioWorld.camera(server, anchor.getX() + 0.5, anchor.getY() + HEIGHT, anchor.getZ() - 35, 0, 55);
        var rt = ScenarioWorld.matchAtAnchor(server, 128, 0, false);
        match = rt;
        var factions = new FactionPresentation(List.of(
                new FactionPresentation.Style(CivilisationArt.ROMAN, 0),
                new FactionPresentation.Style(CivilisationArt.ROMAN, 1)));
        rt.initializeDiplomacy(
                new DiplomacySetup(
                        DiplomacyState.identity(2).withTeam(RED, 0).lockTeams().snapshot()),
                new PresentationSetup(factions));
        for (Site site : TOWN) {
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(
                            new TemplateId("strategos:building/" + site.type()), BLUE, site.x(), site.z(), 0));
        }
        rt.submit(PlayerSlot.ADMIN, new Grant(BLUE, "wood", 1_000));
        ScenarioWorld.spawnSpaced(rt, TemplateSet.WORKER, BLUE, new Vec2d(49, ROLES_Z), 4, 2, 3.0);
        ScenarioWorld.spawnSpaced(rt, TemplateSet.INFANTRY, BLUE, new Vec2d(59, ROLES_Z), 4, 2, 3.0);
        ScenarioWorld.spawnSpaced(rt, ARCHER, RED, new Vec2d(69, ROLES_Z), 4, 2, 3.0);
        ScenarioWorld.spawnSpaced(rt, HORSEMAN, RED, new Vec2d(79, ROLES_Z), 4, 2, 3.0);
        rt.addListener(out -> {
            if (out.tick() == 1) {
                var player = ScenarioWorld.player(server);
                var strategos = StrategosServer.get();
                strategos.matches().join(rt, player.getUUID(), HOST);
                strategos.commanders().enter(player, rt);
                strategos.net().joined(rt, player, HOST);
            }
            var core = rt.core();
            if (core == null) return;
            boolean drained = StrategosServer.get().writer().pending(server.overworld()) == 0;
            var queries = core.queries();
            if (out.tick() > 150 && drained && queries.buildings().size() == TOWN.size()) {
                townReady = true;
            }
            var site = queries.buildings().stream()
                    .filter(b -> b.footprint().anchorX() == NEW_HOUSE_X
                            && b.footprint().anchorZ() == NEW_HOUSE_Z)
                    .findFirst()
                    .orElse(null);
            if (site != null && site.state() != BuildingStore.COMPLETE && site.progress() >= STOP_PROGRESS) {
                if (!placed) {
                    rt.submit(HOST, new Stop(workers(queries)));
                    placed = true;
                } else if (drained
                        && queries.buildings().stream()
                                .anyMatch(b -> b.hitPoints() < b.maxHitPoints() * 0.65
                                        && b.state() == BuildingStore.COMPLETE)) {
                    construction = site.progress();
                }
            }
        });
    }

    private static UnitSet workers(MatchQueries queries) {
        return UnitSet.of(java.util.Arrays.stream(queries.unitHandlesOf(BLUE))
                .filter(h -> {
                    var unit = queries.unit(h);
                    return unit != null && unit.template().equals(TemplateSet.WORKER);
                })
                .toArray());
    }

    private static void stageTown(MatchRuntime rt) {
        var core = rt.core();
        if (core == null) return;
        var queries = core.queries();
        var house = queries.buildings().stream()
                .filter(b -> b.template().equals(HOUSE))
                .findFirst()
                .orElseThrow();
        rt.damage(new ExternalDamage(
                house.handle(),
                ExternalDamage.Kind.CRUSH,
                (float) (house.maxHitPoints() * 0.55),
                ExternalDamage.SourceKind.NONE));
        rt.submit(HOST, new PlaceBuilding(HOUSE, NEW_HOUSE_X, NEW_HOUSE_Z, 0, workers(queries), QueueMode.REPLACE));
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        if (tick > 2_600) {
            ctx.result.addProperty("ok", false);
            ctx.result.addProperty("failure", "Roman art phase " + stage + " timed out");
            return true;
        }
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
            StrategosClientConfig.INSTANCE.edgePan.set(false);
        }
        if (ctx.captureName != null) return false;
        var joined = StrategosClient.match();
        var view = StrategosClient.presentation();
        var rts = StrategosClient.rts();
        var player = mc.player;
        if (joined == null || view == null || player == null || mc.level == null) return false;
        if (!rts.active()) {
            rts.enter();
            return false;
        }
        var rig = rts.rig();
        if (rig == null) return false;
        double x = joined.originX() + FOCUS_X + 0.5;
        double townZ = joined.originZ() + FOCUS_Z + 0.5;
        double rolesZ = joined.originZ() + ROLES_Z + 0.5;
        if (stage == 0) {
            rts.centreOn(x, townZ);
            rig.zoomTo(HEIGHT);
            mc.options.hideGui = true;
            stage++;
            return false;
        }
        double z = stage == 3 || stage == 4 ? rolesZ : townZ;
        boolean settled = Math.abs(rig.height() - HEIGHT) < 0.01
                && Math.hypot(rig.focusX() - x, rig.focusZ() - z) < 0.5
                && Math.hypot(
                                player.getX() - rig.pose().x(),
                                player.getZ() - rig.pose().z())
                        < 0.5
                && mc.levelRenderer.hasRenderedAllSections();
        calm = settled && townReady ? calm + 1 : 0;
        if (calm < 30) return false;
        if (stage == 1) {
            ctx.result.addProperty("cameraHeight", rig.height());
            ctx.capture("roman_town_60");
            stage++;
        } else if (stage == 2) {
            rts.centreOn(x, rolesZ);
            calm = 0;
            stage++;
        } else if (stage == 3) {
            int shields = 0;
            int dressed = 0;
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof UnitEntity body && body.dressed()) {
                    dressed++;
                    var shield = body.getItemBySlot(EquipmentSlot.OFFHAND);
                    if (shield.is(Items.SHIELD) && shield.has(DataComponents.BANNER_PATTERNS)) shields++;
                }
            }
            if (shields != 8
                    || dressed != 16
                    || !view.factions().hasCivilisation(BLUE.value(), CivilisationArt.ROMAN)) {
                return false;
            }
            RtsGoldensScenario.pinBodies(mc);
            ctx.result.addProperty("romanShields", shields);
            ctx.result.addProperty("rolesDressed", dressed);
            ctx.capture("roman_roles_60");
            stage++;
        } else if (stage == 4) {
            var server = mc.getSingleplayerServer();
            var rt = match;
            if (server == null || rt == null) return false;
            rts.centreOn(x, townZ);
            server.execute(() -> stageTown(rt));
            calm = 0;
            stage++;
        } else if (stage == 5 && construction > 0) {
            RtsGoldensScenario.pinBodies(mc);
            ctx.result.addProperty("constructionPermille", construction);
            ctx.capture("roman_stages_60");
            stage++;
        } else if (stage == 6) {
            mc.options.hideGui = false;
            ctx.result.addProperty("ok", true);
            return true;
        }
        return false;
    }
}
