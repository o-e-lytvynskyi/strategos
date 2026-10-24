package dev.strategos.devtools;

import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.client.hud.HudLayout;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.hud.ResourceBar;
import dev.strategos.core.client.hud.SelectionPanel;
import dev.strategos.core.client.pick.Picking;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Resign;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.hud.RtsHud;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.command.StrategosCommands;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * UI-06 on screen (M3 T17): the player, seated in slot 0 with a civic centre, three workers and some food and wood,
 * enters RTS mode. A click on the centre selects it, and the command card offers a worker; a click on that button sends
 * one Train, which the server queues; a right click on the ground sets the centre's rally point. A click on a worker
 * shows its details in the panel; the card's build page opens and its house button arms placement, which Escape
 * disarms; the idle-worker key then takes each idle worker in turn (UI-10). The resource bar shows the stock and the population before and after the worker's cost (UI-07).
 * Last, faction 1 resigns and the end screen shows the victory (MATCH-01). Screenshots of the centre's and the worker's
 * selection, the bar, the idle worker and the end screen, for T30's goldens.
 */
final class RtsEconomyScenario implements Scenario {
    private static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    private static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    private static final int WORKERS = 3;

    private final StringBuilder failures = new StringBuilder();
    private int floor;
    private int phase;
    private int wait;
    private int sent;
    private volatile @Nullable MatchRuntime match;

    @Override
    public String name() {
        return "rts_economy";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        MatchRuntime rt = StrategosServer.get()
                .matches()
                .create(
                        level,
                        StrategosCommands.spaceAround(level, new BlockPos(0, floor, 0), 256, 256),
                        new MatchOptions(5L, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), floor));
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(CIVIC_CENTRE, new FactionSlot(0), 123, 144, 0));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "food", 300));
        rt.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 300));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.WORKER, new FactionSlot(0), new Vec2d(128.5, 136), WORKERS, 2));
        match = rt;
        StrategosServer.get().matches().join(rt, player.getUUID(), new PlayerSlot(0));
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, new PlayerSlot(0));
        player.connection.teleport(0.5, floor + 30, -14, 0, 40);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        if (tick == 20) {
            rts.enter();
            return false;
        }
        if (tick < 40 || mc.level == null) {
            return false;
        }
        if (tick < 600
                && (bodies(mc) < WORKERS || StrategosClient.model().buildings().isEmpty())) {
            return false; // the bodies and the centre's state arrive
        }
        Screen screen = mc.screen;
        if (screen == null || !rts.active() || rts.view() == null) {
            return fail(ctx, "RTS mode is not on");
        }
        if (wait > 0) {
            wait--;
            return false;
        }
        try {
            return step(mc, ctx, rts, screen);
        } catch (IllegalStateException e) {
            return fail(ctx, e.getMessage());
        }
    }

    private boolean step(Minecraft mc, ScenarioContext ctx, RtsMode rts, Screen screen) {
        RtsHud hud = StrategosClient.hud();
        HudLayout layout = RtsHud.layout();
        int pad = HudTheme.DEFAULT.padding();
        switch (phase) {
            case 0 -> {
                var centre = StrategosClient.model().buildings().get(0);
                var join = Objects.requireNonNull(StrategosClient.match());
                // the middle of the centre's front wall, two blocks up
                click(
                        screen,
                        gui(
                                screen,
                                rts,
                                join.originX() + centre.x() + 5.5,
                                centre.baseY() + 3,
                                join.originZ() + centre.z()));
                check(
                        Objects.equals(rts.selection().building(), centre.handle()),
                        "the click selected building " + rts.selection().building() + ", not " + centre.handle());
                wait = 2; // the HUD rebuilds on the next client tick
            }
            case 1 -> {
                CommandCard card = hud.card();
                check(
                        card.page() == CommandCard.Page.BUILDING
                                && !card.buttons().isEmpty()
                                && card.buttons().get(0).kind() == CommandCard.Kind.TRAIN,
                        "the centre's card " + card);
                check(
                        hud.panel().kind() == SelectionPanel.Kind.BUILDING,
                        "the panel shows " + hud.panel().kind());
                ResourceBar bar = hud.resources();
                check(
                        bar.resources().size() == 4
                                && bar.resources().get(0).stock() == 300
                                && bar.populationUsed() == WORKERS
                                && bar.populationCap() == 20,
                        "the resource bar " + bar);
                ctx.capture("select_civic_centre");
                wait = 2;
            }
            case 2 -> {
                sent = rts.ordersSent();
                click(screen, centreOf(CommandCard.cell(layout.commandCard(), pad, 0)));
                check(rts.ordersSent() == sent + 1, rts.ordersSent() - sent + " commands for one click");
                ctx.result.addProperty("trainSent", rts.ordersSent() - sent);
                wait = 10;
            }
            case 3 -> {
                BuildingView c = centre();
                check(c != null && c.queue().size() == 1, "the server's queue " + (c == null ? null : c.queue()));
                ctx.result.addProperty("queued", c == null ? -1 : c.queue().size());
                ResourceBar bar = hud.resources();
                check(
                        bar.resources().get(0).stock() == 240 && bar.populationUsed() == WORKERS + 1,
                        "after the worker's 60 food and its reserved place, the bar " + bar);
                ctx.result.addProperty("foodAfterTrain", bar.resources().get(0).stock());
                ctx.capture("resource_bar");
                double[] ground = gui(screen, rts, 12.5, floor, 6.5);
                screen.mouseClicked(ground[0], ground[1], 1);
                screen.mouseReleased(ground[0], ground[1], 1);
                check(rts.lastCommand() instanceof SetRally, "the right click sent " + rts.lastCommand());
                wait = 10;
            }
            case 4 -> {
                BuildingView c = centre();
                check(c != null && c.rallyKind() != 0, "no rally point on the server: " + c);
                ctx.result.addProperty("rally", c != null && c.rallyKind() != 0);
                click(screen, workerOnScreen(mc, screen, rts));
                check(
                        rts.selection().handles().size() == 1,
                        "a worker click took " + rts.selection().handles());
                check(rts.selection().building() == null, "the centre stayed selected");
                wait = 15; // its unit_detail arrives
            }
            case 5 -> {
                check(
                        hud.panel().kind() == SelectionPanel.Kind.UNIT,
                        "the panel shows " + hud.panel().kind());
                ctx.capture("select_worker");
                wait = 2;
            }
            case 6 -> {
                click(screen, centreOf(CommandCard.cell(layout.commandCard(), pad, 0)));
                check(
                        hud.card().page() == CommandCard.Page.BUILD,
                        "the build page is " + hud.card().page());
            }
            case 7 -> {
                int house = Objects.requireNonNull(StrategosClient.templates().byId(HOUSE))
                        .netIndex();
                CommandCard.Button button = hud.card().buttons().stream()
                        .filter(b -> b.template() == house)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("no house on the build page " + hud.card()));
                click(screen, centreOf(CommandCard.cell(layout.commandCard(), pad, button.slot())));
                check(rts.placementTemplate() == house, "placement armed with " + rts.placementTemplate());
                ctx.result.addProperty("armed", rts.placementTemplate() == house);
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                check(rts.placementTemplate() == -1, "Escape left placement armed");
                rts.selection().clear();
            }
            case 8 -> { // UI-10: the idle-worker key takes the idle workers one by one, in index order
                java.util.List<Integer> taken = new java.util.ArrayList<>();
                for (int k = 0; k < WORKERS + 1; k++) {
                    screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
                    check(
                            rts.selection().handles().size() == 1,
                            "the key selected " + rts.selection().handles());
                    taken.addAll(rts.selection().handles());
                }
                check(
                        taken.size() == WORKERS + 1
                                && taken.subList(0, WORKERS).stream().distinct().count() == WORKERS
                                && taken.get(WORKERS).equals(taken.get(0)),
                        "the key took " + taken);
                ctx.result.addProperty("idleVisited", taken.stream().distinct().count());
                wait = 2;
            }
            case 9 -> {
                ctx.capture("idle_worker");
                MatchRuntime rt = match;
                IntegratedServer server = mc.getSingleplayerServer();
                if (rt != null && server != null) { // MATCH-01: faction 1 resigns, faction 0 wins
                    server.execute(() -> {
                        rt.submit(PlayerSlot.ADMIN, new RuleChange("victory", "civic_centre"));
                        rt.submit(PlayerSlot.ADMIN, new Resign(new FactionSlot(1)));
                    });
                }
                wait = 15;
            }
            case 10 -> {
                var end = StrategosClient.model().end();
                check(end != null && end.faction() == 0, "the end the client heard: " + end);
                ctx.result.addProperty("won", end != null && end.faction() == 0);
                ctx.capture("end_screen");
                wait = 2; // the frame is taken after this tick
            }
            case 11 -> {
                return finish(ctx);
            }
            default -> {
                return finish(ctx);
            }
        }
        phase++;
        return false;
    }

    private @Nullable BuildingView centre() {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt == null || server == null) {
            return null;
        }
        return server.submit(() -> {
                    var core = rt.core();
                    return core == null || core.queries().buildings().isEmpty()
                            ? null
                            : core.queries().buildings().get(0);
                })
                .join();
    }

    private static void click(Screen screen, double[] at) {
        screen.mouseClicked(at[0], at[1], 0);
        screen.mouseReleased(at[0], at[1], 0);
    }

    private static double[] centreOf(HudRect r) {
        return new double[] {r.x() + r.width() / 2.0, r.y() + r.height() / 2.0};
    }

    /** A worker whose middle projects inside the middle of the window, in GUI pixels. */
    private static double[] workerOnScreen(Minecraft mc, Screen screen, RtsMode rts) {
        View v = rts.view();
        if (v == null || mc.level == null) {
            throw new IllegalStateException("no view");
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body
                    && body.spawnData() != null
                    && body.spawnData().faction() == 0) {
                double[] w = Picking.project(v, e.getX(), e.getY() + 0.9, e.getZ());
                if (w != null
                        && w[0] > v.width() * 0.2
                        && w[0] < v.width() * 0.8
                        && w[1] > v.height() * 0.2
                        && w[1] < v.height() * 0.7) {
                    return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
                }
            }
        }
        throw new IllegalStateException("no worker in the middle of the window");
    }

    private static double[] gui(Screen screen, RtsMode rts, double x, double y, double z) {
        View v = rts.view();
        double[] w = v == null ? null : Picking.project(v, x, y, z);
        if (v == null || w == null) {
            throw new IllegalStateException("cannot project " + x + ", " + y + ", " + z);
        }
        return new double[] {w[0] * screen.width / v.width(), w[1] * screen.height / v.height()};
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

    private void check(boolean ok, String what) {
        if (!ok) {
            failures.append(what).append("; ");
        }
    }

    private boolean fail(ScenarioContext ctx, String why) {
        failures.append(why);
        return finish(ctx);
    }

    private boolean finish(ScenarioContext ctx) {
        String f = failures.toString();
        @Nullable String none = f.isEmpty() ? null : f;
        if (none != null) {
            ctx.result.addProperty("failures", none);
        }
        ctx.result.addProperty("phase", phase);
        ctx.result.addProperty("ok", none == null);
        return true;
    }
}
