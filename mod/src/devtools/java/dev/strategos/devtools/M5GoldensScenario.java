package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.ShotFired;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * M5's goldens (T17): the combat and garrison looks, each scene laid out in turn 60 blocks apart on the flat world and
 * frozen at a core tick the server chooses, so a run draws the same frame as the last. Frames:
 * {@code rider_low}, {@code rider_default}, {@code rider_high} (five horsemen with their riders at the camera's
 * heights 8, 24 and 64, UNIT-06.4); {@code volley_launch} and {@code volley_impact} (six archers shooting at four
 * spearmen 18 blocks off: the first shot's arrows one tick after its release and one tick before it lands, CMB-04);
 * {@code attack_lines} (the selected archers' lines to their targets, and one of them flashing as if just hit,
 * CMB-17); {@code bell_walking} and {@code bell_empty} (eight workers 30 ticks after the bell, then with all inside the
 * civic centre and no body left, BLD-16). Then a second world holds the map {@code proving_ground} and
 * {@code strategos:battle_at_the_ford}: {@code ford_battle}, the armies at the fords {@value #BATTLE_TICKS} ticks into
 * the battle, from 64 blocks. Run with {@code -Pcompat} for the Sodium set. About {@value #MINUTES} minutes.
 */
final class M5GoldensScenario implements Scenario {
    static final int MINUTES = 3;
    /** The core tick the ford battle is frozen at, counted from the scenario's setup. */
    static final int BATTLE_TICKS = 400;
    /**
     * The north ford's middle: match-local Z 143.5, in world blocks, which {@code RtsMode.centreOn} takes (the map's
     * match is centred on the origin, so 143.5 - 192).
     */
    static final double FORD_Z = 143.5;

    static final double FORD_WORLD_Z = FORD_Z - 192;

    static final ResourceLocation MAP = ResourceLocation.fromNamespaceAndPath("strategos", "proving_ground");
    static final ResourceLocation BATTLE = ResourceLocation.fromNamespaceAndPath("strategos", "battle_at_the_ford");

    private static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    private static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final FactionSlot OWN = new FactionSlot(0);
    private static final FactionSlot ENEMY = new FactionSlot(1);

    /** The scenes' centres, world X; all on world Z 0. */
    private static final double RIDERS_X = -90;

    private static final double VOLLEY_X = -30;
    private static final double LINES_X = 30;
    private static final double BELL_X = 90;
    /** The camera's heights for the riders: the rig's lowest, its default and its highest. */
    private static final double[] HEIGHTS = {CameraRig.MIN_HEIGHT, CameraRig.DEFAULT_HEIGHT, CameraRig.MAX_HEIGHT};

    private static final String[] RIDER_FRAMES = {"rider_low", "rider_default", "rider_high"};
    private static final int HORSEMEN = 5;
    private static final int WORKERS = 8;
    private static final int VOLLEY_RANGE = 18;
    /** Core ticks after the first release to the launch frame, and after the lines' scene is spawned to its frame. */
    private static final int LAUNCH_AFTER = 1;

    private static final int LINES_AFTER = 25;
    private static final int RING_AFTER = 30;
    /** Ticks for a pose to settle: the rig eases 35 % of the way a tick. */
    private static final int SETTLE = 50;
    /** Ticks after a freeze for the last packets and the chunk sections. */
    private static final int ARRIVE = 20;

    private static final int LIMIT = 600;
    private static final int NEAR = 25;
    private static final String FORD_WORLD = "strategos-m5_goldens-ford";

    private final StringBuilder failures = new StringBuilder();
    // the server's side, written on its thread and read by the client's
    private final AtomicInteger frozen = new AtomicInteger();
    private final List<Integer> collected = new CopyOnWriteArrayList<>();
    private final AtomicBoolean fordJoined = new AtomicBoolean();
    private volatile long[] freezeAt = new long[0];
    /** The next of {@link #freezeAt} to freeze at; the server thread's alone. */
    private int freezeNext;

    private volatile boolean watchShots;
    private volatile boolean collecting;
    private volatile int @Nullable [] insideWatch;
    private volatile long volleyLaunch = -1;
    private volatile long volleyLand = -1;
    private volatile @Nullable MatchRuntime match;

    private boolean inFord;
    private int phase;
    private int inPhase;
    private int shot;

    @Override
    public String name() {
        return "m5_goldens";
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
        if (inFord) {
            setupFord(server, player);
            return;
        }
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 256, 256),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        match = rt;
        rt.addListener(out -> onStep(server, rt, out));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(RIDERS_X + 0.5, floor + CameraRig.DEFAULT_HEIGHT, -back, 0, 55);
    }

    /** The battle's world: the map in the air above the flat ground, the player seated as its observer once it runs. */
    private void setupFord(IntegratedServer server, ServerPlayer player) {
        frozen.set(0);
        freezeAt = new long[0];
        freezeNext = 0;
        MatchRuntime rt = createFord(server);
        double floor = floorOf(server);
        match = rt;
        rt.addListener(out -> onStep(server, rt, out));
        double back = CameraRig.DEFAULT_HEIGHT / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(0.5, floor + CameraRig.DEFAULT_HEIGHT, FORD_WORLD_Z - back, 0, 55);
    }

    /** The map {@code proving_ground} imported into a match centred on the world's origin, in the air at its own band. */
    static MatchRuntime createFord(IntegratedServer server) {
        ServerLevel level = server.overworld();
        MapManifest m = MapPackageLoader.load(server, MAP).manifest();
        BlockPos origin = new BlockPos(-m.sizeX() / 2, m.minY(), -m.sizeZ() / 2);
        MatchSpace space =
                new MatchSpace(level.dimension(), origin, m.sizeX(), m.sizeZ(), m.minY(), m.minY() + m.height() - 1);
        double floor = floorOf(server);
        return StrategosServer.get()
                .matches()
                .create(
                        level,
                        space,
                        MatchOptions.gameTest(ScenarioWorld.SEED, floor).withMap(MAP, floor));
    }

    /** Where the map's units stand: its band's bottom and its first start slot's height. */
    static double floorOf(IntegratedServer server) {
        MapManifest m = MapPackageLoader.load(server, MAP).manifest();
        return m.minY() + m.startSlots().get(0).y();
    }

    /** On the server thread after each step: what the scenes watch for, and the freezes they ask for. */
    private void onStep(IntegratedServer server, MatchRuntime rt, TickOutputs out) {
        long tick = out.tick();
        for (OutputEvent e : out.events()) {
            if (watchShots && e instanceof ShotFired f) {
                watchShots = false;
                volleyLaunch = f.launchTick();
                volleyLand = f.landTick();
                freezeAt = new long[] {f.launchTick() + LAUNCH_AFTER, f.landTick() - 1};
                freezeNext = 0;
            } else if (collecting && e instanceof UnitCreated c && c.faction().equals(OWN)) {
                collected.add(c.handle());
            }
        }
        int[] inside = insideWatch;
        if (inside != null && allInside(rt, inside)) {
            insideWatch = null;
            freezeAt = new long[] {tick};
            freezeNext = 0;
        }
        long[] at = freezeAt;
        if (freezeNext < at.length && tick >= at[freezeNext]) {
            freezeNext++;
            ScenarioWorld.run(server, "tick freeze");
            frozen.incrementAndGet();
        }
    }

    private static boolean allInside(MatchRuntime rt, int[] handles) {
        for (int handle : handles) {
            if (rt.bodies().get(handle) != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
        }
        if (tick >= 20 && tick % 20 == 0 && !rts.active() && (!inFord || fordJoined.get())) {
            rts.enter();
        }
        if (ctx.captureName != null) {
            return false; // the shot comes first
        }
        if (inFord) {
            return ford(mc, ctx, rts, tick);
        }
        if (tick < 40 || mc.screen == null || !rts.active() || rts.view() == null) {
            return tick > LIMIT && fail(ctx, "RTS mode is not on");
        }
        if (tick > 20_000) {
            return fail(ctx, "stuck in phase " + phase);
        }
        inPhase++;
        return town(mc, ctx, rts);
    }

    private boolean town(Minecraft mc, ScenarioContext ctx, RtsMode rts) {
        IntegratedServer server = mc.getSingleplayerServer();
        MatchRuntime rt = match;
        var rig = rts.rig();
        if (server == null || rt == null || rig == null) {
            return false;
        }
        switch (phase) {
            case 0 -> look(rts, rig, RIDERS_X, 24);
            case 1 -> {
                if (inPhase >= SETTLE) {
                    server.execute(() -> rt.submit(
                            PlayerSlot.ADMIN, new SpawnUnits(HORSEMAN, OWN, local(rt, RIDERS_X, 0), HORSEMEN, 3)));
                    next();
                }
            }
            case 2 -> {
                if (near(mc, RIDERS_X) >= HORSEMEN && inPhase >= ARRIVE) {
                    ctx.result.addProperty("horsemen", near(mc, RIDERS_X));
                    server.execute(() -> ScenarioWorld.run(server, "tick freeze"));
                    next();
                } else if (inPhase > LIMIT) {
                    return fail(ctx, "only " + near(mc, RIDERS_X) + " horsemen arrived");
                }
            }
            case 3 -> {
                if (inPhase >= ARRIVE) { // the next height
                    rig.zoomTo(HEIGHTS[shot]);
                    next();
                }
            }
            case 4 -> {
                if (inPhase >= SETTLE && mc.levelRenderer.hasRenderedAllSections()) {
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture(RIDER_FRAMES[shot]);
                    if (++shot < HEIGHTS.length) {
                        phase = 3;
                        inPhase = 0;
                    } else {
                        next();
                    }
                }
            }
            case 5 -> look(rts, rig, VOLLEY_X, CameraRig.DEFAULT_HEIGHT);
            case 6 -> {
                if (inPhase >= SETTLE) {
                    server.execute(() -> {
                        rt.submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(ARCHER, OWN, local(rt, VOLLEY_X - VOLLEY_RANGE / 2.0, 0), 6, 2));
                        rt.submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(
                                        TemplateSet.INFANTRY,
                                        ENEMY,
                                        local(rt, VOLLEY_X + VOLLEY_RANGE / 2.0, 0),
                                        4,
                                        2));
                        watchShots = true;
                        ScenarioWorld.run(server, "tick unfreeze");
                    });
                    next();
                }
            }
            case 7 -> {
                if (frozen.get() >= 1) { // one tick after the first release
                    next();
                } else if (inPhase > LIMIT) {
                    return fail(ctx, "no arrow was shot");
                }
            }
            case 8 -> {
                if (inPhase >= ARRIVE) {
                    check(
                            volleyLand - volleyLaunch > LAUNCH_AFTER + 2,
                            "the first shot flew " + (volleyLand - volleyLaunch) + " ticks");
                    ctx.result.addProperty("volleyLaunchTick", volleyLaunch);
                    ctx.result.addProperty("volleyLandTick", volleyLand);
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("volley_launch");
                    next();
                }
            }
            case 9 -> {
                server.execute(() -> ScenarioWorld.run(server, "tick unfreeze"));
                next();
            }
            case 10 -> {
                if (frozen.get() >= 2) { // one tick before the same shot lands
                    next();
                } else if (inPhase > LIMIT) {
                    return fail(ctx, "the arrow never came down");
                }
            }
            case 11 -> {
                if (inPhase >= ARRIVE) {
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("volley_impact");
                    next();
                }
            }
            case 12 -> look(rts, rig, LINES_X, CameraRig.DEFAULT_HEIGHT);
            case 13 -> {
                if (inPhase >= SETTLE) {
                    server.execute(() -> {
                        rt.submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(ARCHER, OWN, local(rt, LINES_X - VOLLEY_RANGE / 2.0, 0), 4, 1.5));
                        rt.submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(
                                        TemplateSet.INFANTRY,
                                        ENEMY,
                                        local(rt, LINES_X + VOLLEY_RANGE / 2.0, 0),
                                        3,
                                        1.5));
                        freezeAt = new long[] {coreTick(rt) + LINES_AFTER};
                        freezeNext = 0;
                        ScenarioWorld.run(server, "tick unfreeze");
                    });
                    next();
                }
            }
            case 14 -> {
                if (frozen.get() >= 3) {
                    next();
                } else if (inPhase > LIMIT) {
                    return fail(ctx, "the lines' scene never froze");
                }
            }
            case 15 -> {
                if (inPhase >= ARRIVE) { // the last details are in: select the archers
                    List<Integer> own = ownHandles(mc, LINES_X);
                    check(own.size() == 4, own.size() + " archers of the lines' scene on the client");
                    rts.selection().set(own);
                    RtsGoldensScenario.pinBodies(mc);
                    next();
                }
            }
            case 16 -> {
                if (inPhase >= 10) { // the overlay has extracted the lines
                    flash(ownHandles(mc, LINES_X));
                    ctx.capture("attack_lines");
                    next();
                }
            }
            case 17 -> {
                rts.selection().clear();
                look(rts, rig, BELL_X, CameraRig.DEFAULT_HEIGHT);
            }
            case 18 -> {
                if (inPhase >= SETTLE) {
                    server.execute(() -> {
                        rt.submit(
                                PlayerSlot.ADMIN,
                                new SpawnBuilding(
                                        CIVIC_CENTRE,
                                        OWN,
                                        (int) rt.space().localX(BELL_X),
                                        (int) rt.space().localZ(0),
                                        0));
                        collecting = true;
                        rt.submit(
                                PlayerSlot.ADMIN,
                                new SpawnUnits(TemplateSet.WORKER, OWN, local(rt, BELL_X, 16), WORKERS, 3));
                        ScenarioWorld.run(server, "tick unfreeze");
                    });
                    next();
                }
            }
            case 19 -> {
                if (near(mc, BELL_X) >= WORKERS && inPhase >= ARRIVE) {
                    server.execute(() -> ring(rt));
                    next();
                } else if (inPhase > LIMIT) {
                    return fail(ctx, "only " + near(mc, BELL_X) + " workers arrived");
                }
            }
            case 20 -> {
                if (frozen.get() >= 4) {
                    next();
                } else if (inPhase > LIMIT) {
                    return fail(ctx, "the bell's scene never froze");
                }
            }
            case 21 -> {
                if (inPhase >= ARRIVE) {
                    ctx.result.addProperty("walking", near(mc, BELL_X));
                    check(near(mc, BELL_X) > 0, "no worker is walking in");
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("bell_walking");
                    next();
                }
            }
            case 22 -> {
                server.execute(() -> {
                    insideWatch = collected.stream().mapToInt(Integer::intValue).toArray();
                    ScenarioWorld.run(server, "tick unfreeze");
                });
                next();
            }
            case 23 -> {
                if (frozen.get() >= 5) {
                    next();
                } else if (inPhase > 2 * LIMIT) {
                    return fail(ctx, "the workers never all went in");
                }
            }
            case 24 -> {
                if (inPhase >= ARRIVE) {
                    int left = near(mc, BELL_X);
                    ctx.result.addProperty("outside", left);
                    check(left == 0, left + " worker bodies left outside the empty town");
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("bell_empty");
                    next();
                }
            }
            case 25 -> {
                server.execute(() -> ScenarioWorld.run(server, "tick unfreeze"));
                ctx.result.addProperty("townOk", failures.length() == 0);
                inFord = true;
                match = null;
                phase = 0;
                inPhase = 0;
                ctx.switchWorld(FORD_WORLD, ScenarioWorld.SEED);
            }
            default -> {
                return fail(ctx, "no phase " + phase);
            }
        }
        return false;
    }

    /** The bell of the civic centre, then a freeze {@value #RING_AFTER} ticks on. */
    private void ring(MatchRuntime rt) {
        collecting = false;
        var core = rt.core();
        if (core == null) {
            return;
        }
        for (BuildingView b : core.queries().buildings()) {
            if (b.template().equals(CIVIC_CENTRE) && b.faction() == OWN.value()) {
                rt.submit(new PlayerSlot(0), new RingBell(b.handle()));
            }
        }
        freezeAt = new long[] {coreTick(rt) + RING_AFTER};
        freezeNext = 0;
    }

    /** The second world: the battle at the ford, frozen at its tick, then one frame of it from 64 blocks. */
    private boolean ford(Minecraft mc, ScenarioContext ctx, RtsMode rts, int tick) {
        IntegratedServer server = mc.getSingleplayerServer();
        MatchRuntime rt = match;
        if (server == null || rt == null) {
            return false;
        }
        inPhase++;
        if (tick > 20_000) {
            return fail(ctx, "stuck in the ford world, phase " + phase);
        }
        switch (phase) {
            case 0 -> { // the map imported and running: seat the observer, set the battle up, and ask for the freeze
                server.execute(() -> {
                    if (!fordJoined.get() && rt.lifecycle() == MatchLifecycle.RUNNING) {
                        seatAndApply(server, rt);
                        freezeBattle(rt);
                        fordJoined.set(true);
                    }
                });
                var rig = rts.rig();
                if (fordJoined.get()
                        && rts.active()
                        && rts.view() != null
                        && rig != null
                        && StrategosClient.match() != null) {
                    rts.centreOn(0, FORD_WORLD_Z);
                    rig.zoomTo(CameraRig.MAX_HEIGHT);
                    next();
                } else if (inPhase > 3 * LIMIT) {
                    return fail(ctx, "the map's match never ran");
                }
            }
            case 1 -> {
                if (frozen.get() >= 1 && inPhase >= SETTLE) {
                    next();
                } else if (inPhase > 6 * LIMIT) {
                    return fail(ctx, "the battle never reached tick " + BATTLE_TICKS);
                }
            }
            case 2 -> {
                if (inPhase >= ARRIVE && mc.levelRenderer.hasRenderedAllSections()) {
                    RtsGoldensScenario.pinBodies(mc);
                    int bodies = bodies(mc);
                    ctx.result.addProperty("fordBodies", bodies);
                    check(bodies >= 100, "only " + bodies + " bodies in the battle");
                    ctx.capture("ford_battle");
                    next();
                }
            }
            default -> {
                return done(ctx);
            }
        }
        return false;
    }

    /**
     * The battle's scenario applied as {@code Skirmishes} does, with the player seated as its observer (the admin's
     * slot, which sees everything); the freeze at {@value #BATTLE_TICKS}. Server thread.
     */
    static void seatAndApply(IntegratedServer server, MatchRuntime rt) {
        applyBattle(server, rt);
        ServerPlayer player = ScenarioWorld.player(server);
        StrategosServer.get().matches().join(rt, player.getUUID(), PlayerSlot.ADMIN);
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, PlayerSlot.ADMIN);
    }

    /** The battle's scenario set up in the running match {@code rt}, every slot played by the computer. */
    static void applyBattle(IntegratedServer server, MatchRuntime rt) {
        var core = rt.core();
        if (core == null) {
            return;
        }
        dev.strategos.core.content.Scenario scenario = Scenarios.load(server, BATTLE);
        List<dev.strategos.core.content.Scenario.Slot> computers = new ArrayList<>();
        for (dev.strategos.core.content.Scenario.Slot slot : scenario.slots()) {
            computers.add(new dev.strategos.core.content.Scenario.Slot(
                    slot.player().startsWith(ScenarioSetup.AI) ? slot.player() : ScenarioSetup.AI + "battle",
                    slot.resources(),
                    slot.entities()));
        }
        Scenarios.apply(
                server,
                rt,
                new dev.strategos.core.content.Scenario(scenario.map(), scenario.victory(), computers),
                MapPackageLoader.load(server, MAP).manifest().startSlots(),
                core.queries().templates());
    }

    /** The core tick the ford battle freezes at, for this run's match. */
    private void freezeBattle(MatchRuntime rt) {
        freezeAt = new long[] {coreTick(rt) + BATTLE_TICKS};
        freezeNext = 0;
    }

    private static long coreTick(MatchRuntime rt) {
        var core = rt.core();
        return core == null ? 0 : core.queries().tick();
    }

    private static Vec2d local(MatchRuntime rt, double worldX, double worldZ) {
        return new Vec2d(rt.space().localX(worldX), rt.space().localZ(worldZ));
    }

    /** The camera on world (x, 0) at {@code height}; the next phase. */
    private void look(RtsMode rts, CameraRig rig, double x, double height) {
        rts.centreOn(x, 0);
        rig.zoomTo(height);
        next();
    }

    private void next() {
        phase++;
        inPhase = 0;
    }

    /** Unit bodies within {@value #NEAR} blocks of world (x, 0). */
    private static int near(Minecraft mc, double x) {
        int n = 0;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity && Math.abs(e.getX() - x) < NEAR && Math.abs(e.getZ()) < NEAR) {
                    n++;
                }
            }
        }
        return n;
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

    /** Handles of the own bodies within {@value #NEAR} blocks of world (x, 0), ascending. */
    private static List<Integer> ownHandles(Minecraft mc, double x) {
        List<Integer> handles = new ArrayList<>();
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof UnitEntity body
                        && body.spawnData() != null
                        && body.spawnData().faction() == OWN.value()
                        && Math.abs(e.getX() - x) < NEAR
                        && Math.abs(e.getZ()) < NEAR) {
                    handles.add(body.spawnData().handle());
                }
            }
        }
        handles.sort(Integer::compare);
        return handles;
    }

    /**
     * The first of {@code handles} hit just now, as a {@code unit_hud} that lowers its hit points would say. The flash
     * is timed by the wall clock and lasts 400 ms, so a real hit could not be framed; the frame takes the glow
     * a few milliseconds old.
     */
    private static void flash(List<Integer> handles) {
        MatchJoin join = StrategosClient.match();
        if (join == null || handles.isEmpty()) {
            return;
        }
        int index = Handle.index(handles.get(0));
        StrategosClient.model()
                .accept(
                        new UnitHud(
                                join.matchId(),
                                0,
                                List.of(new UnitHud.Entry(
                                        index, StrategosClient.model().health(index) - 40))),
                        join.matchId(),
                        System.currentTimeMillis());
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
        ctx.result.addProperty("phase", phase);
        ctx.result.addProperty("ok", f.isEmpty());
        return true;
    }
}
