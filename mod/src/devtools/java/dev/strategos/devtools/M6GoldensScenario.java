package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.FactionLook;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.Resign;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.client.hud.RtsHud;
import dev.strategos.mod.client.menu.SkirmishScreen;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * M6's goldens (T27): the screens and the HUD the milestone added, and the shroud, each scene frozen so a run draws the
 * same frame as the last. A flat world holds a revealed match with a small town: the setup screen ({@code
 * setup_screen}), the spearman with the starting gear ({@code spearman_base}), the phase panel with four of Town's five
 * buildings ({@code phase_panel}), the storehouse's research card ({@code research_card}), the controls overlay
 * ({@code controls_overlay}); then the match goes through Town and both equipment tiers at a sprint and the spearman
 * stands again ({@code spearman_tiered}); the feed ({@code feed}), a minimap ping ({@code minimap_ping}) and the raid
 * bar ({@code raid_bar}) are fed to the client as the server's payloads would be, and a resignation ends the match
 * ({@code end_screen}). A second world holds a match under Fog: the start ({@code shroud_unexplored}), after a scout has
 * ridden out and home with the three kinds of ground in view ({@code shroud_all}), and the minimap ({@code
 * minimap_fog}). Each static scene is shot twice, ten ticks apart, the second as {@code <name>_2}; the animated ones
 * (feed, ping, raid bar: the HUD's own clock) once, at a fixed HUD tick. Run with {@code -Pcompat} for the Sodium set,
 * where the same shroud scene is the one to look at. About {@value #MINUTES} minutes.
 */
final class M6GoldensScenario implements Scenario {
    static final int MINUTES = 5;

    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    private static final TemplateId FARMSTEAD = new TemplateId("strategos:building/farmstead");
    private static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final TechId PHASE_TOWN = new TechId("strategos:phase_town");
    private static final TechId IRON_WEAPONS = new TechId("strategos:iron_weapons");
    private static final TechId CHAINMAIL = new TechId("strategos:chainmail");
    private static final FactionSlot OWN = new FactionSlot(0);
    private static final FactionSlot ENEMY = new FactionSlot(1);

    private static final String FOG_WORLD = "strategos-m6_goldens-fog";
    private static final int TOWN_SIZE = 256;
    private static final int FOG_SIZE = 128;
    private static final int SPEARMEN = 6;
    /** Where the spearmen stand, world X and Z, and how the camera frames them. */
    private static final double SPEARMEN_Z = -3;

    private static final double SPEARMEN_HEIGHT = 16;
    /** Blocks between the spearmen's centres: well beyond any separation between bodies. */
    private static final double SPEARMEN_PITCH = 3;
    /** The scout's three places in the fog world, world X on Z 0: home, the far end of its ride, and "home" again. */
    private static final double HOME_X = -26;

    private static final double FAR_X = 30;
    /** How close the scout must come to count as there. */
    private static final double THERE = 4;
    /** Ticks for a pose to settle: the rig eases 35 % of the way a tick. */
    private static final int SETTLE = 50;
    /** Ticks after a freeze for the last packets and the chunk sections. */
    private static final int ARRIVE = 20;
    /** Ticks between a static scene's two takes. */
    private static final int BETWEEN = 10;

    private static final int LIMIT = 600;
    private static final int SPRINT_LIMIT = 6_000;
    /** HUD ticks after the news arrives to the frame of a ping (it lasts 60), and of the feed (it fades from 120). */
    private static final int PING_AGE = 30;

    private static final int FEED_AGE = 10;
    /** Consecutive ticks the section compiler must be idle before a shot, and the world unchanged before a freeze. */
    private static final int CALM_RENDER = 20;

    private static final int CALM_WORLD = 60;
    /** Server steps the block writer's queue must stay empty before the freeze. */
    private static final int IDLE_STEPS = 8;

    /** One step of a scene: true once it is done; it runs once a client tick, {@link #inStep} ticks in. */
    @FunctionalInterface
    private interface Step {
        boolean run(Minecraft mc, ScenarioContext ctx);
    }

    private final StringBuilder failures = new StringBuilder();
    // the server's side, written on its thread and read by the client's
    private final Set<TechId> researched = ConcurrentHashMap.newKeySet();
    private final List<String> refusals = new CopyOnWriteArrayList<>();
    private volatile @Nullable MatchRuntime match;
    /** The town's progression through Town and the tiers: 0 idle, 1 asked, 2 barracks placed, 3 Town asked, 4 tiers asked, 5 done. */
    private volatile int town;

    private volatile int scout = -1;
    /** The scout's ride: 0 idle, 1 out, 2 back, 3 home. */
    private volatile int ride;

    /** Set by the client: freeze the server once the writer's queue has stayed empty for a few steps. */
    private volatile boolean freezeOnIdle;

    private volatile boolean frozenIdle;
    private volatile boolean mapCornerOnServerAtFreeze;
    private volatile int forcedChunksAtFreeze;
    private volatile int serverViewAtFreeze;
    private int idleSteps;
    private volatile int endStage;
    private volatile long endTick;

    private boolean fog;
    private boolean rtsWanted;
    private boolean cursorAside;
    private boolean abort;
    private List<Step> steps = List.of();
    private int at;
    private int inStep;
    /** The HUD tick a timed scene counts from. */
    private long mark;

    private int calm;
    private int lastHash;

    @Override
    public String name() {
        return "m6_goldens";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "gamerule doMobSpawning false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        // the town's match shows everything, so its scenes are the HUD's; the fog world's is under Fog
        PlayRules play = fog ? PlayRules.DEFAULTS : new PlayRules(PlayRules.Victory.NONE, true, false, false);
        int size = fog ? FOG_SIZE : TOWN_SIZE;
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), size, size),
                        new MatchOptions(
                                5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor, null, play));
        match = rt;
        // The minimap must sample the whole fixture even though spectator chunk generation is disabled.
        // Town is 16 by 16 chunks (256); the fog world is smaller. Keep vanilla's command limit intact.
        var space = rt.space();
        ScenarioWorld.run(
                server,
                "forceload add " + space.origin().getX() + " " + space.origin().getZ()
                        + " " + (space.origin().getX() + space.sizeX() - 1) + " "
                        + (space.origin().getZ() + space.sizeZ() - 1));
        researched.clear();
        town = 0;
        scout = -1;
        ride = 0;
        endStage = 0;
        rt.addListener(out -> onStep(server, rt, out));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(0.5, floor + CameraRig.DEFAULT_HEIGHT, -back, 0, 55);
    }

    // ---- the server's side ----

    /** On the server thread after each step: the town's progression, the scout's ride and the end. */
    private void onStep(IntegratedServer server, MatchRuntime rt, TickOutputs out) {
        long tick = out.tick();
        for (OutputEvent e : out.events()) {
            if (e instanceof TechResearched t) {
                researched.add(t.tech());
            } else if (e instanceof CommandResult r && r.rejected() > 0) {
                refusals.add(r.kind() + " " + r.firstReason() + " at tick " + tick);
            }
        }
        if (freezeOnIdle) {
            idleSteps = StrategosServer.get().writer().pending(server.overworld()) == 0 ? idleSteps + 1 : 0;
            if (idleSteps >= IDLE_STEPS) { // longer than a site's 4-tick burst interval
                freezeOnIdle = false;
                mapCornerOnServerAtFreeze = server.overworld()
                        .hasChunk(
                                rt.space().origin().getX() >> 4,
                                rt.space().origin().getZ() >> 4);
                forcedChunksAtFreeze = server.overworld().getForcedChunks().size();
                serverViewAtFreeze = server.getPlayerList().getViewDistance();
                ScenarioWorld.run(server, "tick freeze");
                frozenIdle = true;
            }
        }
        var core = rt.core();
        if (core == null) {
            return;
        }
        MatchQueries q = core.queries();
        switch (town) {
            case 1 -> {
                place(rt, BARRACKS, OWN, 26, -14);
                town = 2;
            }
            case 2 -> {
                BuildingView centre = building(q, CIVIC_CENTRE, 0);
                if (building(q, BARRACKS, 0) != null && centre != null) {
                    rt.submit(new PlayerSlot(0), new Research(centre.handle(), PHASE_TOWN));
                    town = 3;
                }
            }
            case 3 -> {
                BuildingView barracks = building(q, BARRACKS, 0);
                if (researched.contains(PHASE_TOWN) && barracks != null) {
                    rt.submit(new PlayerSlot(0), new Research(barracks.handle(), IRON_WEAPONS));
                    rt.submit(new PlayerSlot(0), new Research(barracks.handle(), CHAINMAIL));
                    town = 4;
                }
            }
            case 4 -> {
                if (researched.contains(IRON_WEAPONS) && researched.contains(CHAINMAIL)) {
                    stopSprint(server);
                    town = 5;
                }
            }
            default -> {}
        }
        if (scout >= 0 && (ride == 1 || ride == 2)) {
            double x = ride == 1 ? FAR_X : HOME_X;
            UnitView u = q.unit(scout);
            if (u != null
                    && Math.hypot(
                                    u.x() - rt.space().localX(x),
                                    u.z() - rt.space().localZ(0))
                            <= THERE) {
                if (ride == 1) {
                    rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(scout), local(rt, HOME_X, 0)));
                    ride = 2;
                } else {
                    stopSprint(server);
                    ride = 3;
                }
            }
        }
        if (endStage == 1 && tick >= endTick + 3) { // the rule is in force from the next step
            rt.submit(PlayerSlot.ADMIN, new Resign(ENEMY));
            endStage = 2;
        }
    }

    /** The sprint over and the server frozen where it stands. Server thread. */
    private static void stopSprint(IntegratedServer server) {
        ScenarioWorld.run(server, "tick sprint stop");
        ScenarioWorld.run(server, "tick freeze");
    }

    private static @Nullable BuildingView building(MatchQueries q, TemplateId template, int faction) {
        for (BuildingView b : q.buildings()) {
            if (b.faction() == faction && b.template().equals(template)) {
                return b;
            }
        }
        return null;
    }

    /** A finished building of {@code template} for {@code faction}, its middle at world (x, z). */
    private static void place(MatchRuntime rt, TemplateId template, FactionSlot faction, double x, double z) {
        TemplateSet templates = StrategosServer.get().templates().set();
        UnitTemplate t = Objects.requireNonNull(templates.byId(template), template.value());
        Blueprint bp = Objects.requireNonNull(templates.blueprint(t.netIndex()), template.value());
        int cornerX = (int) Math.floor(rt.space().localX(x)) - bp.sizeX() / 2;
        int cornerZ = (int) Math.floor(rt.space().localZ(z)) - bp.sizeZ() / 2;
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, faction, cornerX, cornerZ, 0));
    }

    private static Vec2d local(MatchRuntime rt, double worldX, double worldZ) {
        return new Vec2d(rt.space().localX(worldX), rt.space().localZ(worldZ));
    }

    /** The town: a civic centre, four of Town's five village buildings, the enemy's centre, six spearmen, and stock. */
    private static void spawnTown(MatchRuntime rt) {
        place(rt, CIVIC_CENTRE, OWN, 0, 14);
        place(rt, HOUSE, OWN, -24, -14);
        place(rt, HOUSE, OWN, -12, -14);
        place(rt, STOREHOUSE, OWN, 0, -14);
        place(rt, FARMSTEAD, OWN, 12, -14);
        place(rt, CIVIC_CENTRE, ENEMY, 70, 0);
        // one by one on a grid, so far apart that no crowd separation ever moves them (a cluster's outcome depends on
        // the tick its spawn lands on)
        for (int k = 0; k < SPEARMEN; k++) {
            double x = (k % 3 - 1) * SPEARMEN_PITCH;
            int row = k / 3;
            double z = SPEARMEN_Z + (row - 0.5) * SPEARMEN_PITCH;
            rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, OWN, local(rt, x, z), 1, 0));
        }
        for (String resource : List.of("food", "wood", "stone", "iron")) {
            rt.submit(PlayerSlot.ADMIN, new Grant(OWN, resource, 2_000));
        }
    }

    /** The fog world: the two centres, workers, and the scout. */
    private static void spawnFog(MatchRuntime rt) {
        place(rt, CIVIC_CENTRE, OWN, -34, 0);
        place(rt, CIVIC_CENTRE, ENEMY, 34, 0);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.WORKER, OWN, local(rt, -34, 10), 4, 1.5));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, OWN, local(rt, HOME_X, 0), 1, 0));
    }

    /** The scout's handle, once it stands in the match. Server thread. */
    private static int scoutOf(MatchQueries q) {
        for (int h : q.unitHandlesOf(OWN)) {
            UnitView u = q.unit(h);
            if (u != null && q.isAlive(h) && u.template().equals(HORSEMAN)) {
                return h;
            }
        }
        return -1;
    }

    // ---- the client's side: the scenes as steps ----

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        long window = mc.getWindow().getWindow();
        // the cursor rests mid-window, as a player's does; a screen's widgets would light under it, so it waits aside
        GLFW.glfwSetCursorPos(
                window,
                cursorAside ? 4.0 : mc.getWindow().getScreenWidth() / 2.0,
                cursorAside ? 4.0 : mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
            // Warping the cursor away from widgets must not pan this fixture's scripted camera.
            StrategosClientConfig.INSTANCE.edgePan.set(false);
            // the hints would show in whatever frame the first selection or the first spare house falls in
            StrategosClientConfig.INSTANCE.seenHints.set(List.of("first_order", "idle_worker", "build_house"));
            steps = fog ? fogSteps() : townSteps();
            at = 0;
            inStep = 0;
        }
        if (rtsWanted && tick >= 20 && tick % 20 == 0 && !rts.active() && StrategosClient.match() != null) {
            rts.enter();
        }
        if (ctx.captureName != null) {
            return false; // the shot comes first
        }
        if (abort || tick > 60_000) {
            return fail(ctx, "stuck at step " + at);
        }
        if (at >= steps.size()) {
            return done(ctx);
        }
        inStep++;
        if (steps.get(at).run(mc, ctx)) {
            at++;
            inStep = 0;
        }
        return false;
    }

    private List<Step> townSteps() {
        List<Step> s = new ArrayList<>();
        s.add(until("the town's world", LIMIT, mc -> StrategosClient.match() != null && rendered(mc)));
        // the setup screen, over the world before RTS mode takes the screen
        s.add(act((mc, ctx) -> {
            cursorAside = true;
            mc.options.hideGui = true; // the golden is the screen's: no HUD behind it
            mc.setScreen(new SkirmishScreen(new TitleScreen()));
        }));
        s.add(wait(20));
        // no widget focused: a focused field blinks its caret
        s.add(act((mc, ctx) -> {
            if (mc.screen != null) {
                mc.screen.setFocused(null);
            }
        }));
        s.add(wait(2));
        shoot(s, "setup_screen", true);
        s.add(act((mc, ctx) -> {
            mc.setScreen(null);
            mc.options.hideGui = false;
            cursorAside = false;
            rtsWanted = true;
        }));
        s.add(until("RTS mode", LIMIT, mc -> {
            RtsMode rts = StrategosClient.rts();
            return rts.active() && rts.view() != null && rts.rig() != null;
        }));
        s.add(act((mc, ctx) -> frame(0, 0, CameraRig.DEFAULT_HEIGHT)));
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> spawnTown(rt))));
        s.add(until(
                "the town and its spearmen",
                LIMIT,
                mc -> ownBuilding(CIVIC_CENTRE) != null
                        && ownBuilding(STOREHOUSE) != null
                        && ownBuilding(FARMSTEAD) != null
                        && bodies(mc, 0, SPEARMEN_Z) >= SPEARMEN));
        s.add(until("the complete town minimap terrain", LIMIT, M6GoldensScenario::mapChunksReady));
        freeze(s);
        // the spearman with the starting gear
        s.add(act((mc, ctx) -> frame(0, SPEARMEN_Z, SPEARMEN_HEIGHT)));
        s.add(wait(SETTLE));
        s.add(act((mc, ctx) -> {
            FactionLook look = StrategosClient.looks(0);
            check(look.weapon() == 0 && look.armour() == 0, "the starting gear is " + look);
        }));
        shoot(s, "spearman_base", true);
        // Town's checklist: four of five
        s.add(act((mc, ctx) -> select(CIVIC_CENTRE)));
        s.add(wait(15));
        s.add(act((mc, ctx) -> check(card(CommandCard.Page.BUILDING), "the civic centre showed no building card")));
        shoot(s, "phase_panel", true);
        // the storehouse's research card
        s.add(act((mc, ctx) -> select(STOREHOUSE)));
        s.add(wait(15));
        s.add(act((mc, ctx) -> {
            boolean research = StrategosClient.hud().card().buttons().stream()
                    .anyMatch(b -> b.kind() == CommandCard.Kind.RESEARCH);
            check(research, "the storehouse's card has no research button");
        }));
        shoot(s, "research_card", true);
        // the controls overlay
        s.add(act((mc, ctx) -> {
            StrategosClient.rts().selection().clear();
            StrategosClient.hud().toggleControls();
        }));
        s.add(wait(5));
        s.add(act((mc, ctx) -> check(StrategosClient.hud().controlsShown(), "the controls overlay is not on")));
        shoot(s, "controls_overlay", true);
        s.add(act((mc, ctx) -> StrategosClient.hud().toggleControls()));
        // the town goes through Town and both tiers at a sprint
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            town = 1;
            ScenarioWorld.run(server, "tick unfreeze");
            ScenarioWorld.run(server, "tick sprint 1000000");
        })));
        s.add(until("Town and both tiers", SPRINT_LIMIT, mc -> town == 5));
        s.add(until("both tiers on the client", LIMIT, mc -> {
            FactionLook look = StrategosClient.looks(0);
            return look.weapon() == 1 && look.armour() == 1;
        }));
        settle(s);
        s.add(until(
                "the feed to be empty",
                400,
                mc -> StrategosClient.hud().feedShown().isEmpty()));
        s.add(act((mc, ctx) -> frame(0, SPEARMEN_Z, SPEARMEN_HEIGHT)));
        s.add(wait(SETTLE));
        shoot(s, "spearman_tiered", true);
        // the feed: the server's notices and news, as its payloads would bring them
        s.add(act((mc, ctx) -> injectFeed()));
        s.add(until(
                "five feed entries", 60, mc -> StrategosClient.hud().feedShown().size() == 5));
        s.add(act((mc, ctx) -> mark = StrategosClient.hud().clientTicks()));
        s.add(until("the feed's moment", 60, mc -> StrategosClient.hud().clientTicks() >= mark + FEED_AGE));
        shoot(s, "feed", false);
        // a ping on the minimap
        s.add(until(
                "the feed to be empty",
                400,
                mc -> StrategosClient.hud().feedShown().isEmpty()));
        s.add(until("the minimap", LIMIT, mc -> minimapReady(TOWN_SIZE)));
        s.add(act((mc, ctx) -> news(MatchNews.ALERT, 0, 0, 40, -30)));
        s.add(until("the ping's moment", 60, mc -> StrategosClient.hud().alertAge() == PING_AGE));
        shoot(s, "minimap_ping", false);
        // the raid bar, half full: the wave leaves in 15 s of a 30 s warning
        s.add(until("the ping to end", 120, mc -> StrategosClient.hud().alertAge() > 70));
        s.add(act((mc, ctx) -> {
            long now = StrategosClient.model().tick();
            MatchJoin join = Objects.requireNonNull(StrategosClient.match());
            StrategosClient.model()
                    .accept(
                            new MatchNews(
                                    join.matchId(),
                                    now - 300,
                                    MatchNews.WAVE,
                                    1,
                                    now + 300,
                                    join.sizeX() / 2f + 70,
                                    join.sizeZ() / 2f),
                            join.matchId());
            mark = StrategosClient.hud().clientTicks();
        }));
        s.add(until("the raid bar's moment", 60, mc -> StrategosClient.hud().clientTicks() >= mark + PING_AGE));
        shoot(s, "raid_bar", false);
        // the end: the rule that ends a match on a lost centre, then the enemy resigns
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            var core = rt.core();
            endTick = core == null ? 0 : core.queries().tick();
            rt.submit(PlayerSlot.ADMIN, new RuleChange("victory", "civic_centre"));
            endStage = 1;
            ScenarioWorld.run(server, "tick unfreeze");
        })));
        s.add(until("the end", LIMIT, mc -> {
            var state = StrategosClient.state();
            return StrategosClient.model().end() != null && state != null && state.results() != null;
        }));
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> ScenarioWorld.run(server, "tick freeze"))));
        s.add(until(
                "the HUD's news to be gone",
                400,
                mc -> StrategosClient.hud().feedShown().isEmpty()
                        && StrategosClient.hud().alertAge() > 70));
        s.add(wait(ARRIVE));
        shoot(s, "end_screen", true);
        s.add(act((mc, ctx) -> {
            ctx.result.addProperty("townOk", failures.length() == 0 && refusals.isEmpty());
            fog = true;
            rtsWanted = false;
            steps = List.of();
            at = 0;
            inStep = 0;
            ctx.switchWorld(FOG_WORLD, ScenarioWorld.SEED);
        }));
        return s;
    }

    private List<Step> fogSteps() {
        List<Step> s = new ArrayList<>();
        s.add(act((mc, ctx) -> rtsWanted = true));
        s.add(until("RTS mode in the fog world", LIMIT, mc -> {
            RtsMode rts = StrategosClient.rts();
            return rts.active()
                    && rts.view() != null
                    && rts.rig() != null
                    && StrategosClient.model().visionCellsX() > 0
                    && rendered(mc);
        }));
        s.add(act((mc, ctx) -> frame(0, 0, CameraRig.DEFAULT_HEIGHT)));
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> spawnFog(rt))));
        s.add(until(
                "the two centres and the scout",
                LIMIT,
                mc -> ownBuilding(CIVIC_CENTRE) != null && bodies(mc, HOME_X, 0) >= 1));
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            var core = rt.core();
            scout = core == null ? -1 : scoutOf(core.queries());
        })));
        s.add(until("the complete fog minimap terrain", LIMIT, M6GoldensScenario::mapChunksReady));
        freeze(s);
        s.add(act((mc, ctx) -> {
            StrategosClient.rts().centreOn(-8, 0);
            Objects.requireNonNull(StrategosClient.rts().rig()).zoomTo(CameraRig.MAX_HEIGHT);
        }));
        s.add(wait(SETTLE));
        s.add(act((mc, ctx) -> {
            int explored = cells(StrategosClient.model().exploredBits());
            int visible = cells(StrategosClient.model().visibleBits());
            ctx.result.addProperty("startExplored", explored);
            ctx.result.addProperty("startVisible", visible);
            check(visible > 0 && explored >= visible, "at the start " + explored + " explored, " + visible + " seen");
        }));
        shoot(s, "shroud_unexplored", true);
        // the scout rides out and home at a sprint
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            if (scout < 0) {
                refusals.add("no scout in the fog world");
                return;
            }
            ride = 1;
            rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(scout), local(rt, FAR_X, 0)));
            ScenarioWorld.run(server, "tick unfreeze");
            ScenarioWorld.run(server, "tick sprint 1000000");
        })));
        s.add(until("the scout's ride", SPRINT_LIMIT, mc -> ride == 3));
        settle(s);
        s.add(act((mc, ctx) -> {
            StrategosClient.rts().centreOn(0, 0);
            Objects.requireNonNull(StrategosClient.rts().rig()).zoomTo(CameraRig.MAX_HEIGHT);
        }));
        s.add(wait(SETTLE));
        s.add(act((mc, ctx) -> {
            var model = StrategosClient.model();
            int explored = cells(model.exploredBits());
            int visible = cells(model.visibleBits());
            int total = model.visionCellsX() * model.visionCellsZ();
            ctx.result.addProperty("trailExplored", explored);
            ctx.result.addProperty("trailVisible", visible);
            ctx.result.addProperty("cells", total);
            check(
                    visible > 0 && explored > visible && explored < total,
                    "after the ride " + explored + " explored, " + visible + " seen of " + total);
        }));
        shoot(s, "shroud_all", true);
        s.add(act((mc, ctx) -> frame(HOME_X, 0, 24)));
        s.add(wait(SETTLE));
        s.add(until("the minimap", LIMIT, mc -> minimapReady(FOG_SIZE)));
        shoot(s, "minimap_fog", true);
        return s;
    }

    // ---- step builders ----

    private Step wait(int ticks) {
        return (mc, ctx) -> inStep >= ticks;
    }

    private static Step act(BiConsumer<Minecraft, ScenarioContext> action) {
        return (mc, ctx) -> {
            action.accept(mc, ctx);
            return true;
        };
    }

    private Step until(String what, int limit, Predicate<Minecraft> done) {
        return (mc, ctx) -> {
            if (done.test(mc)) {
                return true;
            }
            if (inStep > limit) {
                if (what.equals("the client to settle")) {
                    ctx.result.addProperty("settleRendered", mc.levelRenderer.hasRenderedAllSections());
                    ctx.result.addProperty("settleBuilt", allBuilt());
                    ctx.result.addProperty(
                            "settleMinimapCorner", StrategosClient.hud().minimapPixel(1, 1));
                    ctx.result.addProperty("settleMinimapHash", minimapHash());
                    ctx.result.addProperty(
                            "settleHudTicks", StrategosClient.hud().clientTicks());
                    ctx.result.addProperty("settleServerMapCorner", mapCornerOnServerAtFreeze);
                    ctx.result.addProperty("settleForcedChunks", forcedChunksAtFreeze);
                    ctx.result.addProperty("settleServerView", serverViewAtFreeze);
                    ctx.result.addProperty(
                            "settleClientView", mc.options.renderDistance().get());
                    var join = Objects.requireNonNull(StrategosClient.match());
                    var level = Objects.requireNonNull(mc.level);
                    ctx.result.addProperty("settleOriginX", join.originX());
                    ctx.result.addProperty("settleOriginZ", join.originZ());
                    ctx.result.addProperty(
                            "settleClientCornerChunk",
                            level.getChunkSource().hasChunk(join.originX() >> 4, join.originZ() >> 4));
                    var corner = new BlockPos(
                            join.originX() + 1,
                            level.getHeight(Heightmap.Types.WORLD_SURFACE, join.originX() + 1, join.originZ() + 1) - 1,
                            join.originZ() + 1);
                    ctx.result.addProperty(
                            "settleCornerBlock", level.getBlockState(corner).toString());
                    var player = Objects.requireNonNull(mc.player);
                    ctx.result.addProperty("settlePlayerX", player.getX());
                    ctx.result.addProperty("settlePlayerZ", player.getZ());
                    ctx.result.addProperty("settleCalmTicks", calm);
                    ctx.result.addProperty(
                            "settleBuildings",
                            StrategosClient.model().buildings().size());
                    ctx.result.addProperty(
                            "settleIncompleteBuildings",
                            StrategosClient.model().buildings().stream()
                                    .filter(e -> e.state() != CommandCard.COMPLETE)
                                    .count());
                    ctx.capture("failure_settle");
                }
                failures.append("no ")
                        .append(what)
                        .append(" within ")
                        .append(limit)
                        .append(" ticks; ");
                abort = true;
            }
            return false;
        };
    }

    /** A shot of the scene as it stands, and for a static one a second {@value #BETWEEN} ticks later. */
    private void shoot(List<Step> s, String name, boolean twice) {
        s.add(act((mc, ctx) -> calm = 0));
        s.add(until("the level renderer to be done for " + name, LIMIT, mc -> {
            calm = mc.levelRenderer.hasRenderedAllSections() ? calm + 1 : 0;
            return calm >= CALM_RENDER;
        }));
        s.add(act((mc, ctx) -> {
            RtsGoldensScenario.pinBodies(mc);
            ctx.capture(name);
        }));
        if (twice) {
            s.add(wait(BETWEEN));
            s.add(act((mc, ctx) -> {
                RtsGoldensScenario.pinBodies(mc);
                ctx.capture(name + "_2");
            }));
        }
    }

    /**
     * Freezes the server as soon as the block writer has written every building (its queue takes many ticks, and a
     * freeze before it is empty leaves a footprint half written, at a run-dependent point; units are spaced so they
     * never drift meanwhile), then waits for the client to settle: every building finished, the section
     * compiler idle and the minimap's image unchanged for {@value #CALM_WORLD} ticks. The minimap uploads on the
     * client's own tick count, so a frozen game still refreshes it.
     */
    private void freeze(List<Step> s) {
        s.add(act((mc, ctx) -> {
            idleSteps = 0;
            frozenIdle = false;
            freezeOnIdle = true;
        }));
        s.add(until("the writer idle and the server frozen", 2 * LIMIT, mc -> frozenIdle));
        settle(s);
    }

    /** Waits, the server frozen already, for the client's world to stand still. */
    private void settle(List<Step> s) {
        s.add(act((mc, ctx) -> {
            calm = 0;
            lastHash = 0;
        }));
        s.add(until("the client to settle", 2 * LIMIT, mc -> {
            int hash = minimapHash();
            boolean still = hash == lastHash
                    && mc.levelRenderer.hasRenderedAllSections()
                    && allBuilt()
                    && StrategosClient.hud().minimapPixel(1, 1) != 0;
            lastHash = hash;
            calm = still ? calm + 1 : 0;
            return calm >= CALM_WORLD;
        }));
        s.add(wait(ARRIVE));
    }

    /** ClientLevel.hasChunk is always true; inspect the actual cache before freezing the server. */
    private static boolean mapChunksReady(Minecraft mc) {
        var join = StrategosClient.match();
        var level = mc.level;
        if (join == null || level == null) return false;
        for (int x = join.originX() >> 4; x <= (join.originX() + join.sizeX() - 1) >> 4; x++) {
            for (int z = join.originZ() >> 4; z <= (join.originZ() + join.sizeZ() - 1) >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    /** Whether every building the client knows of is finished. */
    private static boolean allBuilt() {
        for (BuildingState.Entry e : StrategosClient.model().buildings()) {
            if (e.state() != CommandCard.COMPLETE) {
                return false;
            }
        }
        return true;
    }

    /** A hash of the minimap's image, sampled every fourth column. */
    private static int minimapHash() {
        MatchJoin join = StrategosClient.match();
        int h = 1;
        if (join != null) {
            for (int z = 0; z < join.sizeZ(); z += 4) {
                for (int x = 0; x < join.sizeX(); x += 4) {
                    h = 31 * h + StrategosClient.hud().minimapPixel(x, z);
                }
            }
        }
        return h;
    }

    // ---- what the steps do ----

    /** Runs {@code work} on the server thread with the match, once it stands. */
    private void onServer(Minecraft mc, BiConsumer<IntegratedServer, MatchRuntime> work) {
        IntegratedServer server = mc.getSingleplayerServer();
        MatchRuntime rt = match;
        if (server == null || rt == null) {
            failures.append("no server or match to work on; ");
            abort = true;
            return;
        }
        server.execute(() -> work.accept(server, rt));
    }

    private static boolean rendered(Minecraft mc) {
        return mc.level != null && mc.levelRenderer.hasRenderedAllSections();
    }

    /** The camera on world (x, z) at {@code height}. */
    private static void frame(double x, double z, double height) {
        RtsMode rts = StrategosClient.rts();
        var rig = Objects.requireNonNull(rts.rig());
        rts.centreOn(x, z);
        rig.zoomTo(height);
    }

    /** The handle of the player's finished building of {@code template}, or null. */
    private static @Nullable Integer ownBuilding(TemplateId template) {
        UnitTemplate t = StrategosClient.templates().byId(template);
        if (t == null) {
            return null;
        }
        for (BuildingState.Entry e : StrategosClient.model().buildings()) {
            if (e.own() && e.template() == t.netIndex() && e.state() == CommandCard.COMPLETE) {
                return e.handle();
            }
        }
        return null;
    }

    private void select(TemplateId template) {
        Integer handle = ownBuilding(template);
        if (handle == null) {
            failures.append("no own ").append(template.value()).append(" to select; ");
            return;
        }
        StrategosClient.rts().selection().clickBuilding(handle, 0);
    }

    private static boolean card(CommandCard.Page page) {
        return StrategosClient.hud().card().page() == page;
    }

    /** The feed's five entries: a batch trained, a building built, a tech researched, a phase, an attack. */
    private static void injectFeed() {
        RtsHud hud = StrategosClient.hud();
        hud.feedback(new NoticeMsg(NoticeKind.TRAINED, 0, TemplateSet.INFANTRY.value()));
        hud.feedback(new NoticeMsg(NoticeKind.BUILT, 0, HOUSE.value()));
        hud.feedback(new NoticeMsg(NoticeKind.RESEARCHED, 0, IRON_WEAPONS.value()));
        news(MatchNews.PHASE, 0, 1, 0, 0);
        news(MatchNews.ALERT, 0, 0, -30, 20);
    }

    /** Match news of {@code kind} for {@code faction}, at the match's centre plus (dx, dz), taken as the payload is. */
    private static void news(int kind, int faction, long ticks, float dx, float dz) {
        MatchJoin join = Objects.requireNonNull(StrategosClient.match());
        MatchNews news = new MatchNews(
                join.matchId(),
                StrategosClient.model().tick(),
                kind,
                faction,
                ticks,
                join.sizeX() / 2f + dx,
                join.sizeZ() / 2f + dz);
        StrategosClient.model().accept(news, join.matchId());
    }

    /** Whether the minimap has drawn the match's middle column. */
    private static boolean minimapReady(int size) {
        return StrategosClient.hud().minimapPixel(size / 2, size / 2) != 0;
    }

    private static int cells(long[] bits) {
        int n = 0;
        for (long word : bits) {
            n += Long.bitCount(word);
        }
        return n;
    }

    /** Unit bodies within 12 blocks of world (x, z). */
    private static int bodies(Minecraft mc, double x, double z) {
        int n = 0;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity && Math.abs(e.getX() - x) < 12 && Math.abs(e.getZ() - z) < 12) {
                    n++;
                }
            }
        }
        return n;
    }

    private void check(boolean ok, String what) {
        if (!ok) {
            failures.append(what).append("; ");
        }
    }

    private boolean fail(ScenarioContext ctx, String why) {
        failures.append(why);
        return done(ctx);
    }

    private boolean done(ScenarioContext ctx) {
        StringBuilder all = new StringBuilder(failures);
        if (!refusals.isEmpty()) {
            all.append("the match refused: ").append(refusals);
        }
        String f = all.toString();
        if (!f.isEmpty()) {
            ctx.result.addProperty("failures", f);
        }
        ctx.result.addProperty("step", at);
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
