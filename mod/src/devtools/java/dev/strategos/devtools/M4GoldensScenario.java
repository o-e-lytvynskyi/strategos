package dev.strategos.devtools;

import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * M4's goldens, ART-02's frames (T20): every resource node at its stages, seen from 60 blocks. The server lays the
 * scene out and works it to its stages under {@code /tick sprint}: a row of iron mines and one of quarries, untouched,
 * at three quarters, a half, a quarter and spent; two rock outcrops, whole and half mined; fields full, half reaped,
 * reaped bare and ripening; a sheep, a pig and a chicken standing and a sheep's carcass half gathered. Each job's
 * workers stop at its amount, so the frozen state is the same every run. Frames: {@code deposit_stages},
 * {@code outcrop_stages}, {@code field_stages}, {@code livestock} and {@code deposit_selected}, the mine at three
 * quarters selected with its ring (the node's selection marker) and its "750 / 1000" line; then the town's: the civic
 * centre's and the barracks' cards ({@code card_civic_centre}, {@code card_barracks}, UI-08), the idle worker's build
 * page ({@code build_page}, UI-09), three houses placed in order for one builder with their progress bars ({@code
 * sites_in_order}, UI-09) and a house crushed past its three damage stages ({@code damage_stages}, BLD-05). Run with
 * {@code -Pcompat} for the Sodium set.
 */
final class M4GoldensScenario implements Scenario {
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    private static final TemplateId FIELD = new TemplateId("strategos:building/field");
    private static final TemplateId IRON_MINE = new TemplateId("strategos:building/iron_mine");
    private static final TemplateId QUARRY = new TemplateId("strategos:building/quarry");
    private static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");
    private static final TemplateId PIG = new TemplateId("strategos:unit/pig");
    private static final TemplateId CHICKEN = new TemplateId("strategos:unit/chicken");
    private static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    /** The town's row, world Z: the barracks, three houses placed in order, a house crushed to its stages. */
    private static final int TOWN_Z = -24;

    private static final int[] ROW_X = {2, 9, 16};
    private static final int CRUSHED_X = 24;
    /** How much of the crushed house's hit points the blow takes: past its three stages (85, 65 and 35 %). */
    private static final double CRUSH = 0.7;

    private static final FactionSlot OWN = new FactionSlot(0);
    /** The slot after the two players': Gaia's, the animals'. */
    private static final FactionSlot GAIA = new FactionSlot(2);

    /** Ticks for a pose to settle: the rig eases 35 % of the way a tick. */
    private static final int SETTLE = 50;
    /** Ticks after a freeze for the last packets and the chunk sections. */
    private static final int ARRIVE = 20;
    /** The core tick the jobs are given. */
    private static final long JOBS_AT = 40;
    /** How long the last field has ripened when the scene freezes: half its 30 s. */
    private static final long RIPENING = 300;
    /**
     * The last ticks before the freeze run at the normal pace: under the sprint the client has no time to live out the
     * dust of the crushed house's blocks, and the freeze would keep it on the frames at random places.
     */
    private static final long SETTLE_PACE = 80;
    /** The camera's height for a view from 60 blocks at the default pitch. */
    private static final double SIXTY = 60 * Math.sin(Math.toRadians(CameraRig.DEFAULT_PITCH));

    /** The deposits' columns, world X; the mines' row at Z 0, the quarries' at Z 10. */
    private static final int[] DEPOSIT_X = {-12, -4, 4, 12, 20};
    /** What each deposit of a row keeps, of its 1,000. */
    private static final long[] DEPOSIT_LEFT = {1_000_000, 750_000, 500_000, 250_000, 0};

    private static final int[] FIELD_X = {4, 11, 18, 25};
    private static final int FIELD_Z = 22;
    /** What the first three fields keep, of their 300; the fourth is sown last and frozen ripening. */
    private static final long[] FIELD_LEFT = {300_000, 150_000, 0};

    private static final int[] OUTCROP_X = {-14, -8};
    private static final int OUTCROP_Z = 22;

    private final StringBuilder failures = new StringBuilder();
    private final AtomicInteger serverStage = new AtomicInteger();
    private final List<Job> jobs = new ArrayList<>();
    private final List<Integer> spawned = new ArrayList<>();
    private int floor;
    private long sownAt = -1;
    private volatile long frozenAt = -1;
    /** Each job's node at the freeze, for the run's result. */
    private volatile String amounts = "";

    private int phase;
    private int inPhase;

    /** Workers sent to a node, stopped once it holds {@code left} or less; its anchor finds it. */
    private static final class Job {
        final double x;
        final double z;
        final long left;
        final int workers;
        final List<Integer> units = new ArrayList<>();
        boolean done;

        Job(double x, double z, long left, int workers) {
            this.x = x;
            this.z = z;
            this.left = left;
            this.workers = workers;
        }
    }

    @Override
    public String name() {
        return "m4_goldens";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = ScenarioWorld.player(server);
        floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        for (int ox : OUTCROP_X) { // rock above the ground round it, before the match captures its pages (ECO-07)
            for (int x = ox; x < ox + 3; x++) {
                for (int z = OUTCROP_Z; z < OUTCROP_Z + 3; z++) {
                    for (int y = 0; y < 2; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, floor + y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 128, 128),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        var space = rt.space();
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(CIVIC_CENTRE, OWN, (int) space.localX(-32), (int) space.localZ(-16), 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(STOREHOUSE, OWN, (int) space.localX(-24), (int) space.localZ(2), 0));
        rt.submit(PlayerSlot.ADMIN, new Grant(OWN, "food", 1_000));
        rt.submit(PlayerSlot.ADMIN, new Grant(OWN, "wood", 1_000));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(BARRACKS, OWN, (int) space.localX(-10), (int) space.localZ(TOWN_Z), 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnBuilding(HOUSE, OWN, (int) space.localX(CRUSHED_X), (int) space.localZ(TOWN_Z), 0));
        for (int k = 0; k < DEPOSIT_X.length; k++) {
            rt.submit(PlayerSlot.ADMIN, new SpawnDeposit(IRON_MINE, (int) space.localX(DEPOSIT_X[k]), (int)
                    space.localZ(0)));
            rt.submit(PlayerSlot.ADMIN, new SpawnDeposit(QUARRY, (int) space.localX(DEPOSIT_X[k]), (int)
                    space.localZ(10)));
            int miners = DEPOSIT_LEFT[k] < DEPOSIT_LEFT[0] ? 3 : 0; // three slots a small deposit
            jobs.add(new Job(DEPOSIT_X[k] + 1.5, 1.5, DEPOSIT_LEFT[k], miners));
            jobs.add(new Job(DEPOSIT_X[k] + 1.5, 11.5, DEPOSIT_LEFT[k], miners));
        }
        for (int k = 0; k < FIELD_LEFT.length; k++) {
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(FIELD, OWN, (int) space.localX(FIELD_X[k]), (int) space.localZ(FIELD_Z), 0));
            jobs.add(new Job(FIELD_X[k] + 2.5, FIELD_Z + 2.5, FIELD_LEFT[k], FIELD_LEFT[k] < FIELD_LEFT[0] ? 3 : 0));
        }
        jobs.add(new Job(OUTCROP_X[0] + 1.5, OUTCROP_Z + 1.5, Long.MAX_VALUE, 0)); // whole
        jobs.add(new Job(OUTCROP_X[1] + 1.5, OUTCROP_Z + 1.5, 45_000, 2)); // half its 90 stone
        int animal = 0;
        for (TemplateId t : List.of(SHEEP, PIG, CHICKEN, SHEEP)) {
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(t, GAIA, new Vec2d(space.localX(-12.5 + 4 * animal), space.localZ(34.5)), 1, 0));
            animal++;
        }
        jobs.add(new Job(-0.5, 34.5, 50_000, 1)); // the last sheep, slaughtered and half gathered
        int workers = jobs.stream().mapToInt(j -> j.workers).sum() + 2; // a builder for the row and one left idle
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.WORKER, OWN, new Vec2d(space.localX(-18.5), space.localZ(6.5)), workers, 3));
        rt.addListener(out -> server(server, rt, out));
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        double back = SIXTY / Math.tan(Math.toRadians(CameraRig.DEFAULT_PITCH));
        player.connection.teleport(4.5, floor + SIXTY, 10 - back, 0, 55);
    }

    /** The server's side, after each step: the jobs at their tick, each stopped at its amount, then the freeze. */
    private void server(IntegratedServer server, MatchRuntime rt, TickOutputs out) {
        MatchCore core = rt.core();
        if (core == null || serverStage.get() != 0) {
            return;
        }
        MatchQueries q = core.queries();
        long tick = out.tick();
        for (OutputEvent e : out.events()) {
            if (e instanceof UnitCreated c && c.faction().equals(OWN)) {
                spawned.add(c.handle());
            }
        }
        if (tick == JOBS_AT) {
            ScenarioWorld.run(server, "tick sprint 1000000");
        }
        if (tick < JOBS_AT) {
            return;
        }
        var space = rt.space();
        boolean all = true;
        for (Job j : jobs) {
            if (j.done) {
                continue;
            }
            NodeView n = nodeAt(q, space.localX(j.x), space.localZ(j.z));
            if (j.workers == 0
                    || (n != null && n.ripening() == 0 && n.amount() <= j.left)) { // a ripening field holds 0
                stop(rt, j);
                continue;
            }
            all = false;
            if (n == null || n.ripening() > 0 || !j.units.isEmpty()) {
                continue; // a field still ripening, or the job under way
            }
            for (int k = 0; k < j.workers && !spawned.isEmpty(); k++) {
                j.units.add(spawned.removeFirst());
            }
            rt.submit(
                    new PlayerSlot(0),
                    Gather.replace(
                            UnitSet.of(
                                    j.units.stream().mapToInt(Integer::intValue).toArray()),
                            n.topX(),
                            n.topY(),
                            n.topZ()));
        }
        if (all && sownAt < 0) {
            sownAt = tick; // the last field, sown now and frozen half ripe
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnBuilding(FIELD, OWN, (int) space.localX(FIELD_X[3]), (int) space.localZ(FIELD_Z), 0));
            town(rt, q);
        }
        if (sownAt >= 0 && tick == sownAt + RIPENING - SETTLE_PACE) {
            ScenarioWorld.run(server, "tick sprint stop");
        }
        if (sownAt >= 0 && tick == sownAt + RIPENING) {
            StringBuilder amounts = new StringBuilder();
            for (Job j : jobs) {
                NodeView n = nodeAt(q, space.localX(j.x), space.localZ(j.z));
                amounts.append(n == null ? "none" : n.amount() / 1000 + (n.ripening() > 0 ? " ripening" : ""))
                        .append(", ");
            }
            NodeView last = nodeAt(q, space.localX(FIELD_X[3] + 2.5), space.localZ(FIELD_Z + 2.5));
            amounts.append("sown ").append(last == null ? "none" : last.ripening() + " ticks to ripen");
            this.amounts = amounts.toString();
            ScenarioWorld.run(server, "tick freeze");
            frozenAt = tick;
            serverStage.incrementAndGet();
        }
    }

    /** The row of houses placed in order for one builder (UI-09's Shift), and the blow that crushes the last house. */
    private void town(MatchRuntime rt, MatchQueries q) {
        var space = rt.space();
        if (!spawned.isEmpty()) {
            int builder = spawned.get(0);
            for (int k = 0; k < ROW_X.length; k++) {
                rt.submit(
                        new PlayerSlot(0),
                        new PlaceBuilding(
                                HOUSE,
                                (int) space.localX(ROW_X[k]),
                                (int) space.localZ(TOWN_Z),
                                0,
                                UnitSet.of(builder),
                                k == 0 ? QueueMode.REPLACE : QueueMode.QUEUE));
            }
        }
        for (BuildingView b : q.buildings()) {
            if (b.template().equals(HOUSE) && b.footprint().anchorX() == (int) space.localX(CRUSHED_X)) {
                rt.damage(new ExternalDamage(
                        b.handle(),
                        ExternalDamage.Kind.CRUSH,
                        (float) (CRUSH * b.maxHitPoints()),
                        ExternalDamage.SourceKind.NONE));
            }
        }
    }

    private static void stop(MatchRuntime rt, Job j) {
        j.done = true;
        if (!j.units.isEmpty()) {
            rt.submit(
                    PlayerSlot.ADMIN,
                    new Stop(UnitSet.of(
                            j.units.stream().mapToInt(Integer::intValue).toArray())));
        }
    }

    /** The live node whose anchor is within half a block and a half of match-local (x, z). */
    private static @Nullable NodeView nodeAt(MatchQueries q, double x, double z) {
        for (NodeView n : q.nodes()) {
            if (Math.abs(n.anchorX() - x) <= 1.5 && Math.abs(n.anchorZ() - z) <= 1.5) {
                return n;
            }
        }
        return null;
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
        if (tick == 20) {
            rts.enter();
        }
        Screen screen = mc.screen;
        if (tick < 40 || screen == null || !rts.active() || rts.view() == null) {
            return tick > 600 && fail(ctx, "RTS mode is not on");
        }
        if (tick > 12_000) {
            return fail(ctx, "stuck in phase " + phase + ", the server at stage " + serverStage.get());
        }
        if (ctx.captureName != null || serverStage.get() == 0) {
            return false; // the shot, or the server's freeze, comes first
        }
        inPhase++;
        try {
            return frames(mc, ctx, rts, screen);
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    /** Frozen: each group framed from 60 blocks, then the mine at three quarters selected. */
    private boolean frames(Minecraft mc, ScenarioContext ctx, RtsMode rts, Screen screen) {
        CameraRig rig = Objects.requireNonNull(rts.rig());
        switch (phase) {
            case 0 -> {
                if (inPhase >= ARRIVE) {
                    RtsGoldensScenario.pinBodies(mc);
                    look(rts, rig, 5.5, 6);
                }
            }
            case 1 -> shot(mc, ctx, "deposit_stages", () -> look(rts, rig, -5, OUTCROP_Z + 1.5));
            case 2 -> shot(mc, ctx, "outcrop_stages", () -> look(rts, rig, 17, FIELD_Z + 2.5));
            case 3 -> shot(mc, ctx, "field_stages", () -> look(rts, rig, -6.5, 34.5));
            case 4 -> shot(mc, ctx, "livestock", () -> look(rts, rig, 5.5, 6));
            case 5 -> {
                if (inPhase >= SETTLE) {
                    var join = Objects.requireNonNull(StrategosClient.match());
                    click(screen, gui(screen, rts, DEPOSIT_X[1] + 1.5, floor + 1.5, 1.5));
                    Integer node = rts.selection().deposit();
                    check(node != null, "the click selected no deposit");
                    DepositState.Entry e = StrategosClient.model().deposits().stream()
                            .filter(d -> node != null && d.node() == node)
                            .findFirst()
                            .orElse(null);
                    check(e != null && e.left() == 750, "the selected deposit: " + e + " at " + join.originX());
                    next();
                }
            }
            case 6 -> {
                if (inPhase >= 15) { // its panel line and ring
                    check(!rts.nodeMarkerShown().isEmpty(), "no ring round the selected deposit");
                    ctx.capture("deposit_selected");
                    next();
                }
            }
            case 7 -> {
                rts.selection().clear();
                look(rts, rig, -16, -14);
            }
            case 8 -> {
                if (inPhase >= SETTLE) {
                    click(screen, gui(screen, rts, -26.5, floor + 7, -10.5));
                    check(rts.selection().building() != null, "the click selected no civic centre");
                    next();
                }
            }
            case 9 -> {
                if (inPhase >= 15) { // its building_state, and the card
                    ctx.capture("card_civic_centre");
                    click(screen, gui(screen, rts, -5.5, floor + 6, TOWN_Z + 3.5));
                    check(rts.selection().building() != null, "the click selected no barracks");
                    next();
                }
            }
            case 10 -> {
                if (inPhase >= 15) {
                    ctx.capture("card_barracks");
                    rts.selection().clear();
                    screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0); // the idle worker, the one left without a job
                    check(
                            rts.selection().handles().size() == 1,
                            "the idle key took " + rts.selection().handles());
                    next();
                }
            }
            case 11 -> {
                if (inPhase >= SETTLE) { // the key centres the camera on it
                    check(StrategosClient.hud().pressCard(0), "no build page button");
                    next();
                }
            }
            case 12 -> {
                if (inPhase >= 5) {
                    ctx.capture("build_page");
                    rts.selection().clear();
                    look(rts, rig, 10.5, TOWN_Z + 2.5);
                }
            }
            case 13 -> shot(mc, ctx, "sites_in_order", () -> look(rts, rig, CRUSHED_X + 2.5, TOWN_Z + 2.5));
            case 14 -> shot(mc, ctx, "damage_stages", this::next);
            case 15 -> {
                rts.selection().clear();
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> ScenarioWorld.run(server, "tick unfreeze"));
                }
                ctx.result.addProperty("frozenAt", frozenAt);
                ctx.result.addProperty("amounts", amounts);
                next();
            }
            default -> {
                return done(ctx);
            }
        }
        return false;
    }

    /** Once the pose has settled and every section is drawn, frame {@code name}, then turn to the next view. */
    private void shot(Minecraft mc, ScenarioContext ctx, String name, Runnable then) {
        if (inPhase >= SETTLE && mc.levelRenderer.hasRenderedAllSections()) {
            ctx.capture(name);
            then.run();
        }
    }

    /** The camera on world (x, z) from 60 blocks; the next phase. */
    private void look(RtsMode rts, CameraRig rig, double x, double z) {
        rts.centreOn(x, z);
        rig.zoomTo(SIXTY);
        next();
    }

    private void next() {
        phase++;
        inPhase = 0;
    }

    private static void click(Screen screen, double[] at) {
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
    }

    private static double[] gui(Screen screen, RtsMode rts, double x, double y, double z) {
        View v = rts.view();
        double[] w = v == null ? null : Picking.project(v, x, y, z);
        if (v == null || w == null) {
            throw new IllegalStateException("cannot project " + x + ", " + y + ", " + z);
        }
        return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
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
