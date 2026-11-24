package dev.strategos.devtools;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlUtil;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * PERF-14, the client frame gate: the RTS view over 2 × 100 units at render distance 16. Four windows of
 * {@link #WINDOW} ticks with the frame limit off: the RTS view with no units (base), the units standing (idle), the
 * units walking (moving), and the own hundred selected and outlined with a queued order's marker (rts). The units'
 * cost is moving less base and our drawing is rts less moving, both in the frame interval's p50, which includes the
 * GPU; the frame is the rts window's. The client tick is timed in one more window after rts, at vanilla's default
 * limit of {@link #TICK_CAP} frames a second (rtsCapped): with no limit the GPU never idles, and vanilla's
 * animated-texture upload in the tick waits for it (M2 §1.6). The card's budgets decide {@code ok}.
 *
 * <p>The border scene ({@code rts_frame_borders}, M9 TERR-03.3) is the same run with two civic centres 96 blocks apart
 * on the armies' row, so the territory's border runs through the walk, and borders are drawn on the terrain and on the
 * minimap in every window, the base one included: our drawing in the rts window then holds the borders' cost, and the
 * run's numbers are read against {@code rts_frame}'s. It waits for the borders to be meshed (the territory as the
 * scene's and the section compiler idle for {@value #BORDER_CALM} ticks) before the base window.
 */
final class RtsFrameScenario implements Scenario {
    private static final int PER_SIDE = 100;
    /**
     * The armies' template, {@code -Pframe.units}: the bare debug walker by default, PERF-14's baseline; the spearman
     * for ART-01.5's pass, dressed in armour with a trident and a shield (M3 T20).
     */
    private static final TemplateId UNITS =
            new TemplateId("strategos:unit/" + System.getProperty("strategos.frame.units", "debug_walker"));

    private static final int WINDOW = 160;
    private static final int SETTLE = 40;
    /** After the render distance grows to 16, the server and the chunk builders settle for this long before timing. */
    private static final int LOAD_MIN = 300;

    private static final int RENDER_DISTANCE = 16;
    /** The border scene's centres, match-local, on the armies' row: the border is the line X = 128. */
    private static final TemplateId CENTRE = new TemplateId("strategos:building/civic_centre");

    private static final int CENTRE_X_MID = 128;
    private static final int OWN_CENTRE_X = 80;
    private static final int ENEMY_CENTRE_X = 176;
    private static final int CENTRE_Z = 144;
    /** Consecutive ticks the territory must be the scene's and the section compiler idle before the base window. */
    private static final int BORDER_CALM = 60;
    /** Up to 2,500 frames a second over a window. */
    private static final int CAPACITY = 20_000;
    /** The armies' rows and the ends of their walks, in match-local blocks; the camera's focus is at (128, 144). */
    private static final double OWN_Z = 138;

    private static final double ENEMY_Z = 150;
    private static final double WEST = 116;
    private static final double EAST = 140;
    private static final int LEGS = 12;
    private static final int UNLIMITED = 260;
    static final int TICK_CAP = 120;
    /** Ticks after a frame-limit change before a window's timers start. */
    private static final int LEAD = 20;

    static final double FRAME_P50_MS = 16.7;
    static final double FRAME_P99_MS = 25;
    static final double OURS_MS = 3;
    static final double UNITS_MS = 6;
    static final double CLIENT_TICK_MS = 1;

    private enum Stage {
        LOADING,
        BASE,
        SPAWNING,
        IDLE,
        ORDERING,
        MOVING,
        SELECTING,
        RTS,
        RTS_CAPPED,
        DONE
    }

    /** One window's three timers. */
    static final class Sample {
        final FrameTimer frames = new FrameTimer(CAPACITY);
        final FrameTimer intervals = new FrameTimer(CAPACITY);
        final FrameTimer ticks = new FrameTimer(CAPACITY);
        /** The HUD's own timers (UI-02): its layers' drawing each frame, its view-models' building each tick. */
        final FrameTimer hudDraw = new FrameTimer(CAPACITY);

        final FrameTimer hudBuild = new FrameTimer(CAPACITY);
        /** The health bars' pass each frame (CMB-06): their extraction from the bodies and their drawing. */
        final FrameTimer bars = new FrameTimer(CAPACITY);

        JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("cpuFrameP50Ms", frames.ms(0.50));
            o.addProperty("cpuFrameP99Ms", frames.ms(0.99));
            o.addProperty("frameP50Ms", intervals.ms(0.50));
            o.addProperty("frameP99Ms", intervals.ms(0.99));
            o.addProperty("frameMaxMs", intervals.maxMs());
            o.addProperty("clientTickP50Ms", ticks.ms(0.50));
            o.addProperty("clientTickP99Ms", ticks.ms(0.99));
            o.addProperty("hudDrawP50Ms", hudDraw.ms(0.50));
            o.addProperty("hudDrawP99Ms", hudDraw.ms(0.99));
            o.addProperty("hudBuildP50Ms", hudBuild.ms(0.50));
            o.addProperty("barsP50Ms", bars.ms(0.50));
            o.addProperty("barsP99Ms", bars.ms(0.99));
            return o;
        }
    }

    /** The baseline's rules: the map revealed, so no shroud is drawn and every unit shows (PERF-14 before M6). */
    static final PlayRules REVEALED = ScenarioWorld.REVEALED;

    /**
     * M6's shroud: every cell explored and none revealed, so the shroud draws over all ground out of sight (FOW-07,
     * budget F with the shroud).
     */
    static final PlayRules EXPLORED = new PlayRules(PlayRules.Victory.NONE, false, true, false);

    private final String name;
    private final PlayRules play;
    private final boolean borders;
    private boolean centresSent;
    private int borderCalm;
    private final StringBuilder failures = new StringBuilder();
    private volatile @Nullable MatchRuntime match;
    private volatile int bodiesOnServer;
    private Stage stage = Stage.LOADING;
    private int inStage;
    /** The current window's timers, once its lead is over; null between windows. */
    private @Nullable Sample sample;

    private @Nullable Sample pending;
    private int lead;
    private final Map<String, Sample> samples = new HashMap<>();
    private final Map<Integer, double[]> startedAt = new HashMap<>();
    /** The camera's focus when timing starts, which every window must end at. */
    private double @Nullable [] focus;

    RtsFrameScenario(String name, PlayRules play) {
        this(name, play, false);
    }

    /** {@code borders}: PERF-14's border scene, two civic centres whose territories meet in the armies' view. */
    RtsFrameScenario(String name, PlayRules play, boolean borders) {
        this.name = name;
        this.play = play;
        this.borders = borders;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 256, 256),
                        new MatchOptions(
                                5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor, null, play));
        match = rt;
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        // RTS mode puts its focus the default height's reach ahead of the player: here, the armies' centre
        double back = 24 / Math.tan(Math.toRadians(55));
        player.connection.teleport(0.5, floor + 24, 16 - back, 0, 55);
    }

    @Override
    public void frameStart() {
        Sample s = sample;
        if (s != null) {
            s.intervals.end();
            s.frames.start();
            s.intervals.start();
        }
    }

    @Override
    public void frameEnd() {
        Sample s = sample;
        if (s != null) {
            s.frames.end();
            s.hudDraw.add(StrategosClient.hud().drawNanos());
            s.bars.add(StrategosClient.healthBars().nanos());
        }
    }

    @Override
    public void clientTickStart() {
        Sample s = sample;
        if (s != null) {
            s.ticks.start();
        }
    }

    @Override
    public void clientTickEnd() {
        Sample s = sample;
        if (s != null) {
            s.ticks.end();
            s.hudBuild.add(StrategosClient.hud().buildNanos());
        }
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, RENDER_DISTANCE);
            mc.options.framerateLimit().set(UNLIMITED);
            mc.options.particles().set(ParticleStatus.ALL); // a player's default
        }
        if (borders && !centresSent && match != null && match.lifecycle() == MatchLifecycle.RUNNING) {
            centresSent = true; // a match takes commands once it runs, not while it prepares
            spawnCentres();
        }
        // the cursor rests mid-window, as a player's does: at an edge it would pan the camera off the armies
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 20) {
            rts.enter();
        }
        var rig = rts.rig();
        if (rig != null && focus == null && stage != Stage.LOADING) {
            focus = new double[] {rig.focusX(), rig.focusZ()};
        }
        inStage++;
        if (pending != null && inStage >= lead) {
            sample = pending;
            pending = null;
        }
        switch (stage) {
            case LOADING -> {
                if (inStage >= LOAD_MIN
                        && rts.active()
                        && rts.view() != null
                        && mc.levelRenderer.hasRenderedAllSections()
                        && bordersMeshed()) {
                    ctx.result.addProperty("loadTicks", tick);
                    ctx.result.addProperty("borders", borders);
                    next(Stage.BASE, true);
                }
            }
            case BASE -> {
                if (inStage >= WINDOW) {
                    keep("base");
                    spawn();
                    next(Stage.SPAWNING, false);
                }
            }
            case SPAWNING -> {
                count();
                if (bodiesOnServer == 2 * PER_SIDE && bodies(mc) == 2 * PER_SIDE && inStage >= SETTLE) {
                    int seen = onScreen(mc, rts);
                    ctx.result.addProperty("onScreen", seen);
                    check(seen == 2 * PER_SIDE, seen + " of " + 2 * PER_SIDE + " units on screen");
                    ctx.capture("rts_frame_idle");
                    next(Stage.IDLE, true);
                } else if (inStage > 600) {
                    return fail(ctx, "only " + bodies(mc) + " bodies arrived");
                }
            }
            case IDLE -> {
                if (inStage >= WINDOW) {
                    keep("idle");
                    walk();
                    next(Stage.ORDERING, false);
                }
            }
            case ORDERING -> {
                if (inStage >= SETTLE) {
                    mark(mc);
                    next(Stage.MOVING, true);
                }
            }
            case MOVING -> {
                if (inStage >= WINDOW) {
                    keep("moving");
                    int moved = moved(mc);
                    ctx.result.addProperty("movedWhileTimed", moved);
                    check(moved >= 2 * PER_SIDE * 3 / 4, "only " + moved + " units walked in the moving window");
                    next(Stage.SELECTING, false);
                }
            }
            case SELECTING -> {
                Screen screen = mc.screen;
                if (screen == null || !rts.active()) {
                    return fail(ctx, "RTS mode is not on");
                }
                if (inStage == 1) {
                    screen.mouseClicked(1, 1, 0);
                    screen.mouseMoved(screen.width - 2, screen.height - 2);
                    screen.mouseReleased(screen.width - 2, screen.height - 2, 0);
                    int selected = rts.selection().handles().size();
                    ctx.result.addProperty("selected", selected);
                    int own =
                            ownOnScreen(mc, rts); // the spearmen stand wider than the walkers: some walk off the window
                    ctx.result.addProperty("ownOnScreen", own);
                    check(selected == own, "the drag selected " + selected + " of the " + own + " own on screen");
                    View v = rts.view();
                    double[] w = v == null ? null : Picking.project(v, 0.5, floorY(mc), 16);
                    if (w != null) {
                        rts.order(w[0], w[1], true); // queued behind the walk: the marker stays 200 ticks
                    }
                } else if (inStage >= 10) {
                    check(rts.ordersSent() == 1, rts.ordersSent() + " orders sent");
                    mark(mc);
                    next(Stage.RTS, true);
                }
            }
            case RTS -> {
                if (inStage >= WINDOW) {
                    keep("rts");
                    ctx.result.addProperty("movedWhileTimedRts", moved(mc));
                    ctx.capture("rts_frame_selected");
                    mc.options.framerateLimit().set(TICK_CAP);
                    next(Stage.RTS_CAPPED, true, LEAD);
                }
            }
            case RTS_CAPPED -> {
                if (inStage >= LEAD + WINDOW) {
                    keep("rtsCapped");
                    next(Stage.DONE, false);
                }
            }
            case DONE -> {
                return finish(mc, ctx);
            }
        }
        return false;
    }

    private void next(Stage to, boolean timed) {
        next(to, timed, 0);
    }

    /** The next stage, timed from {@code after} ticks on. */
    private void next(Stage to, boolean timed, int after) {
        stage = to;
        inStage = 0;
        sample = null;
        pending = timed ? new Sample() : null;
        lead = after;
    }

    private void keep(String name) {
        Sample s = sample;
        if (s != null) {
            samples.put(name, s);
        }
        var rig = StrategosClient.rts().rig();
        double[] f = focus;
        if (rig == null || f == null || Math.hypot(rig.focusX() - f[0], rig.focusZ() - f[1]) > 0.5) {
            failures.append("the camera moved before the end of ").append(name).append("; ");
        }
        sample = null;
        pending = null;
    }

    /** The border scene's two civic centres, finished, on the armies' row (server thread via the server's queue). */
    private void spawnCentres() {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt == null || server == null) {
            return;
        }
        server.execute(() -> {
            TemplateSet templates = StrategosServer.get().templates().set();
            UnitTemplate t = Objects.requireNonNull(templates.byId(CENTRE), CENTRE.value());
            Blueprint bp = Objects.requireNonNull(templates.blueprint(t.netIndex()), CENTRE.value());
            int cornerZ = CENTRE_Z - bp.sizeZ() / 2;
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(CENTRE, new FactionSlot(0), OWN_CENTRE_X - bp.sizeX() / 2, cornerZ, 0));
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(CENTRE, new FactionSlot(1), ENEMY_CENTRE_X - bp.sizeX() / 2, cornerZ, 0));
        });
    }

    /**
     * True without borders; with them, once the client's territory has each centre's ground its owner's and the border's
     * two sides the two owners for {@value #BORDER_CALM} ticks running (the section compiler is idle by the caller's
     * own test, and a territory version dirties its sections, so the meshes are done by then).
     */
    private boolean bordersMeshed() {
        if (!borders) {
            return true;
        }
        TerritoryGrid grid = StrategosClient.model().territory().grid();
        boolean held = grid != null
                && grid.ownerAt(OWN_CENTRE_X, CENTRE_Z) == 0
                && grid.ownerAt(ENEMY_CENTRE_X, CENTRE_Z) == 1
                && grid.ownerAt(CENTRE_X_MID - 8, CENTRE_Z) == 0
                && grid.ownerAt(CENTRE_X_MID + 8, CENTRE_Z) == 1;
        borderCalm = held ? borderCalm + 1 : 0;
        return borderCalm >= BORDER_CALM;
    }

    private void spawn() {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt == null || server == null) {
            return;
        }
        server.execute(() -> {
            rt.submit(PlayerSlot.ADMIN, new SpawnUnits(UNITS, new FactionSlot(0), new Vec2d(128, OWN_Z), PER_SIDE, 6));
            rt.submit(
                    PlayerSlot.ADMIN, new SpawnUnits(UNITS, new FactionSlot(1), new Vec2d(128, ENEMY_Z), PER_SIDE, 6));
        });
    }

    /** Both armies walk back and forth across the view, the enemy the other way, for longer than the timed windows. */
    private void walk() {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt == null || server == null) {
            return;
        }
        server.execute(() -> {
            var core = rt.core();
            if (core == null) {
                return;
            }
            UnitSet own = UnitSet.of(core.queries().unitHandlesOf(new FactionSlot(0)));
            UnitSet enemy = UnitSet.of(core.queries().unitHandlesOf(new FactionSlot(1)));
            for (int leg = 0; leg < LEGS; leg++) {
                QueueMode mode = leg == 0 ? QueueMode.REPLACE : QueueMode.QUEUE;
                boolean east = leg % 2 == 0;
                rt.submit(PlayerSlot.ADMIN, new Move(own, new Vec2d(east ? EAST : WEST, OWN_Z), mode, 0));
                rt.submit(PlayerSlot.ADMIN, new Move(enemy, new Vec2d(east ? WEST : EAST, ENEMY_Z), mode, 0));
            }
        });
    }

    private void count() {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt != null && server != null) {
            server.execute(() -> bodiesOnServer = rt.bodies().count());
        }
    }

    /** Where each body stands as a window starts. */
    private void mark(Minecraft mc) {
        startedAt.clear();
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity) {
                    startedAt.put(e.getId(), new double[] {e.getX(), e.getZ()});
                }
            }
        }
    }

    /** The bodies that moved more than two blocks since {@link #mark}. */
    private int moved(Minecraft mc) {
        int n = 0;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                double[] at = startedAt.get(e.getId());
                if (at != null && Math.hypot(e.getX() - at[0], e.getZ() - at[1]) > 2) {
                    n++;
                }
            }
        }
        return n;
    }

    private static double floorY(Minecraft mc) {
        return mc.level == null ? 0 : mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 16);
    }

    private static int bodies(Minecraft mc) {
        int n = 0;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Bodies whose feet and heads project inside the window. */
    private static int onScreen(Minecraft mc, RtsMode rts) {
        View v = rts.view();
        int n = 0;
        if (v == null || mc.level == null) {
            return 0;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity) {
                double[] feet = Picking.project(v, e.getX(), e.getY(), e.getZ());
                double[] head = Picking.project(v, e.getX(), e.getBoundingBox().maxY, e.getZ());
                if (inside(v, feet) && inside(v, head)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** The own bodies whose middle projects inside the window: what a whole-window drag selects. */
    private static int ownOnScreen(Minecraft mc, RtsMode rts) {
        View v = rts.view();
        int n = 0;
        if (v == null || mc.level == null) {
            return 0;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && body.spawnData().faction() == 0) {
                // the box's own rule (Seen.inBox): the feet or the head on screen, so a unit at the edge counts alike
                if (inside(v, Picking.project(v, e.getX(), e.getY(), e.getZ()))
                        || inside(v, Picking.project(v, e.getX(), e.getBoundingBox().maxY, e.getZ()))) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean inside(View v, double @Nullable [] w) {
        return w != null && w[0] >= 0 && w[0] <= v.width() && w[1] >= 0 && w[1] <= v.height();
    }

    private boolean finish(Minecraft mc, ScenarioContext ctx) {
        JsonObject windows = new JsonObject();
        samples.forEach((name, s) -> windows.add(name, s.json()));
        ctx.result.add("windows", windows);
        Sample base = samples.get("base");
        Sample moving = samples.get("moving");
        Sample rts = samples.get("rts");
        Sample rtsCapped = samples.get("rtsCapped");
        if (base == null || moving == null || rts == null || rtsCapped == null) {
            return fail(ctx, "a window is missing: " + samples.keySet());
        }
        double units = moving.intervals.ms(0.50) - base.intervals.ms(0.50);
        double ours = rts.intervals.ms(0.50) - moving.intervals.ms(0.50);
        ctx.result.addProperty("unitsMs", units);
        ctx.result.addProperty("oursMs", ours);
        ctx.result.addProperty("frameP50Ms", rts.intervals.ms(0.50));
        ctx.result.addProperty("frameP99Ms", rts.intervals.ms(0.99));
        ctx.result.addProperty("clientTickP50Ms", rtsCapped.ticks.ms(0.50));
        ctx.result.addProperty("clientTickP99Ms", rtsCapped.ticks.ms(0.99));
        ctx.result.addProperty("tickCap", TICK_CAP);
        ctx.result.addProperty("hudDrawP50Ms", rts.hudDraw.ms(0.50));
        ctx.result.addProperty("hudDrawP99Ms", rts.hudDraw.ms(0.99));
        ctx.result.addProperty("hudBuildP50Ms", rtsCapped.hudBuild.ms(0.50));
        ctx.result.addProperty("barsP50Ms", rts.bars.ms(0.50));
        ctx.result.addProperty("barsP99Ms", rts.bars.ms(0.99));
        ctx.result.addProperty("barsDrawn", StrategosClient.healthBars().drawn());
        ctx.result.addProperty("units", 2 * PER_SIDE);
        ctx.result.addProperty("template", UNITS.toString());
        ctx.result.addProperty("renderDistance", RENDER_DISTANCE);
        ctx.result.addProperty("sodium", ModList.get().isLoaded("sodium"));
        ctx.result.addProperty("iris", ModList.get().isLoaded("iris"));
        ctx.result.addProperty("gl", GlUtil.getRenderer());
        ctx.result.addProperty(
                "window",
                mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " points, "
                        + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " pixels");
        ctx.result.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        ctx.result.addProperty("cores", Runtime.getRuntime().availableProcessors());
        check(rts.intervals.ms(0.50) <= FRAME_P50_MS, "frame p50 " + rts.intervals.ms(0.50) + " ms");
        check(rts.intervals.ms(0.99) <= FRAME_P99_MS, "frame p99 " + rts.intervals.ms(0.99) + " ms");
        check(units <= UNITS_MS, "units " + units + " ms");
        check(ours <= OURS_MS, "our drawing " + ours + " ms");
        check(rtsCapped.ticks.ms(0.50) <= CLIENT_TICK_MS, "client tick p50 " + rtsCapped.ticks.ms(0.50) + " ms");
        return done(ctx);
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
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
