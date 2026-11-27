package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraPose;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.hud.ClientFactionView;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.client.input.Modifiers;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Research;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DayClock;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.TechResearched;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.StrategosClientConfig;
import dev.strategos.mod.client.hud.TechTreeScreen;
import dev.strategos.mod.client.rts.OrthoView;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.client.rts.RtsScreen;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * M10's goldens (T30, the consent batch): five scenes on a flat world, the server frozen so a run draws the same frame
 * as the last. One village stands for all of them: a civic centre and a storehouse, three soldiers beside them, and two
 * more soldiers far to the west and the east. The storehouse researches Iron axes first (the server sprints to it, and
 * is frozen again). Scene one ({@code m10_tree}, TECH-05): the technology tree screen opened as the phase panel's
 * button does, with one technology researched. Scene two ({@code m10_insignia}, CMB-11): the three soldiers selected,
 * ranks 0, 1 and 3 (given to the bodies by the path a {@code UnitRanked} takes), seen from 40 blocks, their bars
 * with the pips over them. Scene three ({@code m10_idle_1}, {@code
 * m10_idle_2}, UI-17): nothing selected, the idle-soldier key (Shift + I) pressed on the RTS screen twice, a far soldier
 * selected and the camera on it after each jump. Scene four ({@code m10_ortho}, CAM-06): the orthographic view over the
 * village, then back to perspective; under a render mod (the Sodium and Iris run is this scenario under {@code
 * -Pcompat}) the option alone is refused, and opted in the view is drawn and shot, or falls back after a fault: the
 * result's {@code ortho_default} and {@code ortho_opted_in} say which. Scene five ({@code m10_night}, MATCH-16): the rule
 * on, the fog back, the runner's noon pin released (the level's time is the match's night, and the client must show
 * it), the server sprinting to the night and frozen there, the village as the dark leaves it; {@code night_*} in the
 * result give both sides' day time. Each
 * scene is shot twice, ten ticks apart, the second as {@code <name>_2}. About {@value #MINUTES} minutes.
 */
final class M10GoldensScenario implements Scenario {
    static final int MINUTES = 5;

    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    private static final TechId IRON_AXES = new TechId("strategos:gather_wood_1");
    private static final FactionSlot OWN = new FactionSlot(0);

    private static final int SIZE = 256;
    /** World X and Z of the village: the centre, the storehouse, and the three soldiers' middle (four blocks apart). */
    private static final int CENTRE_X = -24;

    private static final int STORE_X = 4;
    private static final int VILLAGE_Z = -44;
    private static final int SOLDIERS_X = -8;
    private static final int SOLDIERS_Z = -30;
    /** The soldiers' ranks left to right, and the two far soldiers' places. */
    private static final int[] RANKS = {0, 1, 3};

    private static final int WEST_X = -70;
    private static final int EAST_X = 70;
    private static final int FAR_Z = 50;

    /** The camera heights: 33 over the pitch of 55 degrees is 40 blocks from the eye, and the village's frame. */
    private static final double INSIGNIA_HEIGHT = 33;

    private static final double VILLAGE_HEIGHT = 40;
    private static final double SEEN_FROM = 40;
    /** The match tick the night scene is frozen at: past night's start, the sky set at the clock's next whole second. */
    private static final long NIGHT_TICK = DayClock.NIGHT_FROM - DayClock.START + 60;

    /** Ticks for a pose to settle: the rig eases 35 % of the way a tick. */
    private static final int SETTLE = 50;
    /** Ticks after a freeze for the last packets and the chunk sections. */
    private static final int ARRIVE = 20;
    /** Ticks between a scene's two takes. */
    private static final int BETWEEN = 10;

    private static final int LIMIT = 600;
    /** The client ticks a sprint may take: the night is 7,000 server ticks away. */
    private static final int SPRINT_LIMIT = 6000;
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

    /** What must hold before a shot: null when it does, else what was not true. */
    @FunctionalInterface
    private interface Check {
        @Nullable String problem(Minecraft mc);
    }

    private final StringBuilder failures = new StringBuilder();
    private volatile @Nullable MatchRuntime match;
    /** Set by the client: freeze the server once the writer's queue has stayed empty for a few steps. */
    private volatile boolean freezeOnIdle;

    private volatile boolean frozenIdle;
    private int idleSteps;
    /** Set by the client: sprint stops, on the server thread, at the first step this holds for the tick. */
    private volatile @Nullable LongPredicate sprintStop;

    private volatile boolean sprintDone;
    private volatile boolean researched;

    private boolean abort;
    /** The orthographic scene under a render mod: nothing to shoot, the result says why. */
    private boolean noShots;

    private List<Step> steps = List.of();
    private int at;
    private int inStep;
    private int calm;
    private int lastHash;
    private int firstIdle = -1;
    private int secondIdle = -1;

    @Override
    public String name() {
        return "m10_goldens";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        // revealed, so no shroud, until the night scene turns it back on; the day and night rule starts off
        PlayRules play = new PlayRules(PlayRules.Victory.NONE, true, false, false);
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, y, 0), SIZE, SIZE),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), y, null, play));
        match = rt;
        rt.addListener(out -> onStep(server, out));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(0.5, y + CameraRig.DEFAULT_HEIGHT, -back, 0, 55);
    }

    // ---- the server's side ----

    /** On the server thread after each step: the research seen, a sprint stopped at its goal, the freeze once the writer is done. */
    private void onStep(IntegratedServer server, TickOutputs out) {
        out.events().forEach(e -> {
            if (e instanceof TechResearched t && t.tech().equals(IRON_AXES)) {
                researched = true;
            }
        });
        LongPredicate stop = sprintStop;
        if (stop != null && stop.test(out.tick())) {
            sprintStop = null;
            ScenarioWorld.run(server, "tick sprint stop"); // the rate manager gives back the freeze it found
            sprintDone = true;
        }
        if (freezeOnIdle) {
            idleSteps = StrategosServer.get().writer().pending(server.overworld()) == 0 ? idleSteps + 1 : 0;
            if (idleSteps >= IDLE_STEPS) { // longer than a site's 4-tick burst interval
                freezeOnIdle = false;
                ScenarioWorld.run(server, "tick freeze");
                frozenIdle = true;
            }
        }
    }

    /** A finished building of {@code template}, its middle at world (x, z). */
    private static void place(MatchRuntime rt, TemplateId template, double x, double z) {
        TemplateSet templates = StrategosServer.get().templates().set();
        UnitTemplate t = Objects.requireNonNull(templates.byId(template), template.value());
        Blueprint bp = Objects.requireNonNull(templates.blueprint(t.netIndex()), template.value());
        int cornerX = (int) Math.floor(rt.space().localX(x)) - bp.sizeX() / 2;
        int cornerZ = (int) Math.floor(rt.space().localZ(z)) - bp.sizeZ() / 2;
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(template, OWN, cornerX, cornerZ, 0));
    }

    /** The village: both buildings finished at once, the two far soldiers first, then the three beside the centre. */
    private static void raise(MatchRuntime rt) {
        place(rt, CIVIC_CENTRE, CENTRE_X, VILLAGE_Z);
        place(rt, STOREHOUSE, STORE_X, VILLAGE_Z);
        ScenarioWorld.spawnSpaced(
                rt,
                TemplateSet.INFANTRY,
                OWN,
                new Vec2d(rt.space().localX(WEST_X), rt.space().localZ(FAR_Z)),
                1,
                1,
                1);
        ScenarioWorld.spawnSpaced(
                rt,
                TemplateSet.INFANTRY,
                OWN,
                new Vec2d(rt.space().localX(EAST_X), rt.space().localZ(FAR_Z)),
                1,
                1,
                1);
        ScenarioWorld.spawnSpaced(
                rt,
                TemplateSet.INFANTRY,
                OWN,
                new Vec2d(rt.space().localX(SOLDIERS_X), rt.space().localZ(SOLDIERS_Z)),
                RANKS.length,
                RANKS.length,
                4);
        rt.submit(PlayerSlot.ADMIN, new Grant(OWN, "food", 500));
        rt.submit(PlayerSlot.ADMIN, new Grant(OWN, "wood", 500));
    }

    /** The handle of faction 0's unit nearest world (x, z), -1 for none. */
    private static int unitNear(MatchRuntime rt, double x, double z) {
        MatchQueries q = Objects.requireNonNull(rt.core()).queries();
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int h : q.unitHandlesOf(OWN)) {
            UnitView u = q.unit(h);
            if (u != null) {
                double d = Math.hypot(rt.space().worldX(u.x()) - x, rt.space().worldZ(u.z()) - z);
                if (d < bestD) {
                    best = h;
                    bestD = d;
                }
            }
        }
        return best;
    }

    // ---- the client's side: the scenes as steps ----

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        // the cursor rests mid-window, as a player's does: at an edge it would pan the camera; over the tree, out of
        // its cells, so no tooltip is in the shot
        boolean tree = mc.screen instanceof TechTreeScreen;
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                tree ? mc.getWindow().getScreenWidth() - 2.0 : mc.getWindow().getScreenWidth() / 2.0,
                tree ? mc.getWindow().getScreenHeight() - 2.0 : mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
            StrategosClientConfig.INSTANCE.seenHints.set(List.of("first_order", "idle_worker", "build_house"));
            steps = scenes();
            at = 0;
            inStep = 0;
        }
        if (tick >= 20 && tick % 20 == 0 && !rts.active() && StrategosClient.match() != null) {
            rts.enter();
        }
        if (ctx.captureName != null) {
            return false; // the shot comes first
        }
        if (abort || tick > 30_000) {
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

    private List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(until("the world", LIMIT, mc -> StrategosClient.match() != null && rendered(mc)));
        s.add(until("RTS mode", LIMIT, mc -> {
            RtsMode rts = StrategosClient.rts();
            return rts.active() && rts.view() != null && rts.rig() != null;
        }));
        // the village, the ranks (a body's rank is a synched value, so they are given while the server still runs)
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> raise(rt))));
        s.add(until(
                "the village and its five soldiers",
                LIMIT,
                mc -> ownSoldiers(mc).size() == 2 + RANKS.length
                        && StrategosClient.model().buildings().size() == 2));
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            for (int k = 0; k < RANKS.length; k++) {
                rt.bodies().rank(unitNear(rt, SOLDIERS_X + (k - 1) * 4.0, SOLDIERS_Z), RANKS[k]);
            }
        })));
        s.add(wait(ARRIVE));
        freeze(s);
        // scene one: the tree, with one technology researched under a sprint
        s.add(act((mc, ctx) -> onServer(mc, (server, rt) -> {
            MatchQueries q = Objects.requireNonNull(rt.core()).queries();
            for (BuildingView b : q.buildings()) {
                if (b.template().equals(STOREHOUSE)) {
                    rt.submit(new PlayerSlot(0), new Research(b.handle(), IRON_AXES));
                }
            }
        })));
        sprint(s, tick -> researched);
        s.add(until("Iron axes on the client", LIMIT, mc -> clientResearched()));
        s.add(wait(ARRIVE));
        s.add(act((mc, ctx) -> frame(-10, -38, VILLAGE_HEIGHT)));
        s.add(act((mc, ctx) -> StrategosClient.hud().openTechTree()));
        s.add(until("the technology tree screen", LIMIT, mc -> mc.screen instanceof TechTreeScreen));
        s.add(wait(10));
        shoot(
                s,
                "m10_tree",
                mc -> mc.screen instanceof TechTreeScreen && clientResearched()
                        ? null
                        : "the tree screen is not open, or Iron axes is not researched");
        s.add(act((mc, ctx) -> Objects.requireNonNull(mc.screen).onClose()));
        s.add(until("the RTS screen back", LIMIT, mc -> mc.screen instanceof RtsScreen));
        // scene two: the insignia from 40 blocks
        s.add(act((mc, ctx) -> {
            frame(SOLDIERS_X, SOLDIERS_Z, INSIGNIA_HEIGHT);
            var chosen = new ArrayList<Integer>();
            for (UnitEntity body : ownSoldiers(mc)) {
                if (Math.abs(body.getZ() - SOLDIERS_Z) < 2) {
                    chosen.add(Objects.requireNonNull(body.spawnData()).handle());
                }
            }
            StrategosClient.rts().selection().set(chosen);
        }));
        s.add(wait(SETTLE));
        shoot(s, "m10_insignia", M10GoldensScenario::insigniaProblem);
        // scene three: idle cycling, two jumps to far soldiers
        s.add(act((mc, ctx) -> {
            StrategosClient.rts().selection().clear();
            frame(0, 0, VILLAGE_HEIGHT);
        }));
        s.add(wait(SETTLE));
        idleJump(s, "m10_idle_1", true);
        idleJump(s, "m10_idle_2", false);
        // scene four: the orthographic view
        s.add(act((mc, ctx) -> frame(-10, -38, VILLAGE_HEIGHT)));
        s.add(wait(SETTLE));
        // CAM-06.4: under Sodium or Iris the option alone is refused; opted in, the view is drawn or falls back to
        // perspective after a fault. Either is clean; the result says which, and only a drawn view is shot
        boolean renderMod = OrthoView.renderModLoaded();
        s.add(act((mc, ctx) -> {
            StrategosClient.rts().selection().clear();
            StrategosClientConfig.INSTANCE.orthographicWithRenderMods.set(false);
            StrategosClientConfig.INSTANCE.orthographic.set(true); // what Ctrl + O flips; the view follows next tick
        }));
        if (renderMod) {
            s.add(wait(SETTLE));
            s.add(act((mc, ctx) -> {
                check(!OrthoView.active(), "m10_ortho: the view is on under a render mod without the opt-in");
                ctx.result.addProperty("ortho_default", "refused under a render mod");
                StrategosClientConfig.INSTANCE.orthographicWithRenderMods.set(true);
            }));
            s.add(wait(SETTLE)); // long enough for a fault in the mixins to turn the view off
            s.add(act((mc, ctx) -> {
                noShots = !OrthoView.active();
                ctx.result.addProperty("ortho_opted_in", noShots ? "fell back to perspective after a fault" : "drawn");
            }));
        } else {
            s.add(until("the orthographic view on", LIMIT, mc -> OrthoView.active()));
        }
        shoot(s, "m10_ortho", mc -> noShots || OrthoView.active() ? null : "the orthographic view is not active");
        s.add(act((mc, ctx) -> {
            noShots = false;
            StrategosClientConfig.INSTANCE.orthographic.set(false);
            StrategosClientConfig.INSTANCE.orthographicWithRenderMods.set(false);
        }));
        // CAM-06.3: the switch back never freezes the game; the client keeps ticking into perspective
        s.add(until("the perspective view back", LIMIT, mc -> !OrthoView.active()));
        // scene five: the night, the fog back, the village in the dark
        s.add(act((mc, ctx) -> {
            // the runner's noon pin would hide the level's night: from here the client shows the server's time
            ctx.worldLight();
            onServer(mc, (server, rt) -> {
                rt.submit(PlayerSlot.ADMIN, new RuleChange("dayNight", "true"));
                rt.submit(PlayerSlot.ADMIN, new RuleChange("revealMap", "false"));
            });
        }));
        sprint(s, tick -> tick >= NIGHT_TICK);
        s.add(until(
                "the night on the client",
                LIMIT,
                mc -> DayClock.night(StrategosClient.model().tick())));
        s.add(until("the night in the client's level", LIMIT, M10GoldensScenario::clientNight));
        s.add(wait(SETTLE));
        s.add(act((mc, ctx) -> {
            // what decides the night frame, server and client side (the server is frozen, so its reads are still)
            IntegratedServer server = mc.getSingleplayerServer();
            MatchRuntime rt = match;
            MatchCore core = rt == null ? null : rt.core();
            ctx.result.addProperty("night_model_tick", StrategosClient.model().tick());
            ctx.result.addProperty("night_client_day_time", mc.level == null ? -1 : mc.level.getDayTime());
            ctx.result.addProperty(
                    "night_server_day_time",
                    server == null ? -1 : server.overworld().getDayTime());
            ctx.result.addProperty(
                    "night_rule",
                    core != null && core.queries().config().rules().play().dayNight());
            ctx.result.addProperty(
                    "night_server_tick", core == null ? -1 : core.queries().tick());
        }));
        shoot(s, "m10_night", M10GoldensScenario::nightProblem);
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

    /** A shot of the scene as it stands, and a second {@value #BETWEEN} ticks later as {@code <name>_2}. */
    private void shoot(List<Step> s, String name, Check precondition) {
        s.add(act((mc, ctx) -> calm = 0));
        s.add(until("the level renderer to be done for " + name, LIMIT, mc -> {
            calm = mc.levelRenderer.hasRenderedAllSections() ? calm + 1 : 0;
            return calm >= CALM_RENDER;
        }));
        s.add(act((mc, ctx) -> {
            String problem = precondition.problem(mc);
            check(problem == null, name + ": " + problem);
            if (!noShots) {
                ctx.capture(name);
            }
        }));
        s.add(wait(BETWEEN));
        s.add(act((mc, ctx) -> {
            if (!noShots) {
                ctx.capture(name + "_2");
            }
        }));
    }

    /** Frozen server, the writer done, and the client still: every building finished, the minimap unchanged for a while. */
    private void freeze(List<Step> s) {
        s.add(act((mc, ctx) -> {
            idleSteps = 0;
            frozenIdle = false;
            freezeOnIdle = true;
        }));
        s.add(until("the writer idle and the server frozen", 2 * LIMIT, mc -> frozenIdle));
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

    /** The server sprints until {@code stop} holds for a match tick, then is frozen again (it was frozen before). */
    private void sprint(List<Step> s, LongPredicate stop) {
        s.add(act((mc, ctx) -> {
            sprintDone = false;
            sprintStop = stop;
            onServer(mc, (server, rt) -> ScenarioWorld.run(server, "tick sprint 1000000"));
        }));
        s.add(until("the sprint to reach its goal", SPRINT_LIMIT, mc -> sprintDone));
    }

    /**
     * Shift + I on the RTS screen, the key's own path: a far soldier selected, the camera on it. The first jump's
     * soldier is kept, so the second is checked to be another and far from it.
     */
    private void idleJump(List<Step> s, String name, boolean first) {
        s.add(act((mc, ctx) -> {
            Objects.requireNonNull(mc.screen).keyPressed(KeyCode.I, 0, Modifiers.SHIFT);
        }));
        s.add(until(
                "a soldier selected by the idle key for " + name,
                LIMIT,
                mc -> !selected().isEmpty()));
        s.add(wait(SETTLE));
        shoot(s, name, mc -> {
            List<Integer> chosen = selected();
            UnitEntity body = bodyOf(mc, chosen.isEmpty() ? -1 : chosen.getFirst());
            var rig = StrategosClient.rts().rig();
            if (chosen.size() != 1 || body == null || rig == null) {
                return "not exactly one soldier selected and in view: " + chosen;
            }
            if (Math.hypot(rig.focusX() - body.getX(), rig.focusZ() - body.getZ()) > 3) {
                return "the camera is not on the selected soldier";
            }
            int handle = chosen.getFirst();
            if (first) {
                firstIdle = handle;
                return Math.abs(body.getZ() - FAR_Z) < 2 ? null : "the first idle soldier is not a far one";
            }
            secondIdle = handle;
            return secondIdle != firstIdle ? null : "the second jump selected the same soldier";
        });
        if (first) {
            s.add(act((mc, ctx) -> StrategosClient.rts().selection().clear()));
        }
    }

    // ---- what the steps check ----

    private static @Nullable String insigniaProblem(Minecraft mc) {
        var rig = StrategosClient.rts().rig();
        List<UnitEntity> row = new ArrayList<>();
        for (UnitEntity body : ownSoldiers(mc)) {
            if (Math.abs(body.getZ() - SOLDIERS_Z) < 2) {
                row.add(body);
            }
        }
        row.sort((a, b) -> Double.compare(a.getX(), b.getX()));
        if (row.size() != RANKS.length || rig == null) {
            return "expected " + RANKS.length + " soldiers in the row, saw " + row.size();
        }
        for (int k = 0; k < RANKS.length; k++) {
            UnitEntity body = row.get(k);
            if (body.rank() != RANKS[k]) {
                return "soldier " + k + " shows rank " + body.rank() + ", not " + RANKS[k];
            }
            if (!StrategosClient.rts()
                    .selection()
                    .contains(Objects.requireNonNull(body.spawnData()).handle())) {
                return "soldier " + k + " is not selected, so it shows no bar";
            }
        }
        CameraPose eye = rig.pose();
        UnitEntity middle = row.get(1);
        double away = Math.sqrt(Math.pow(eye.x() - middle.getX(), 2)
                + Math.pow(eye.y() - middle.getY(), 2)
                + Math.pow(eye.z() - middle.getZ(), 2));
        return Math.abs(away - SEEN_FROM) <= 4 ? null : "the camera is " + Math.round(away) + " blocks away, not 40";
    }

    /** The client's level time is night: the server's time packet, once a second, has arrived. */
    private static boolean clientNight(Minecraft mc) {
        return mc.level != null
                && DayClock.phaseAt((int) (mc.level.getDayTime() % DayClock.CYCLE)) == DayClock.DayPhase.NIGHT;
    }

    private static @Nullable String nightProblem(Minecraft mc) {
        long tick = StrategosClient.model().tick();
        if (!DayClock.night(tick)) {
            return "it is not night at match tick " + tick;
        }
        var state = StrategosClient.state();
        boolean dark = clientNight(mc) || (state != null && state.tint());
        return dark ? null : "the level's time is not night and the client draws no tint";
    }

    private static boolean clientResearched() {
        return new ClientFactionView(
                        StrategosClient.templates(),
                        StrategosClient.model().faction(),
                        StrategosClient.model().buildings(),
                        StrategosClient.viewerCiv())
                .researched(IRON_AXES);
    }

    private static List<Integer> selected() {
        return StrategosClient.rts().selection().handles();
    }

    /** The own soldiers' bodies the client has. */
    private static List<UnitEntity> ownSoldiers(Minecraft mc) {
        List<UnitEntity> out = new ArrayList<>();
        var join = StrategosClient.match();
        if (mc.level == null || join == null) {
            return out;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body) {
                UnitSpawnData data = body.spawnData();
                if (data != null && data.matchId() == join.matchId() && data.faction() == OWN.value()) {
                    out.add(body);
                }
            }
        }
        return out;
    }

    private static @Nullable UnitEntity bodyOf(Minecraft mc, int handle) {
        for (UnitEntity body : ownSoldiers(mc)) {
            if (Objects.requireNonNull(body.spawnData()).handle() == handle) {
                return body;
            }
        }
        return null;
    }

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
        var join = StrategosClient.match();
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
        String f = failures.toString();
        if (!f.isEmpty()) {
            ctx.result.addProperty("failures", f);
        }
        ctx.result.addProperty("step", at);
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
