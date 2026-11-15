package dev.strategos.devtools;

import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.menu.ArenaWorldLauncher;
import dev.strategos.mod.client.menu.SkirmishScreen;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * MATCH-02.3: from the title screen to a running skirmish on the first map at Normal in at most {@value #MOST} clicks.
 * The scenario clicks as a player would, through the screens' own {@code mouseClicked} on the middle of a button, and
 * counts: Skirmish on the title screen, Start on the setup screen, which is two. The defaults of the setup are checked
 * to be the first map at Normal; the match that comes is checked on the server to be on that map, seated with the
 * player, and the client to be in RTS mode on a running match. The frame is {@code skirmish_running}, evidence of the
 * result: a computer player plays in it, so it is no golden. About {@value #MINUTES} minutes (the map is imported
 * first).
 */
final class SkirmishFlowScenario implements Scenario {
    static final int MOST = 5;
    static final int MINUTES = 3;
    /** Ticks after the match runs and RTS mode is on, for the chunks and the HUD. */
    private static final int ARRIVE = 60;

    private final StringBuilder failures = new StringBuilder();
    private int clicks;
    private boolean clicked;
    private boolean frozen;
    private boolean shot;
    private int running;
    private volatile @Nullable String serverSide;

    @Override
    public String name() {
        return "skirmish_flow";
    }

    @Override
    public String worldName() {
        return ArenaWorldLauncher.WORLD;
    }

    /** On the title screen: the clicks, which open the arena world themselves. */
    @Override
    public boolean openWorld(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen title)) {
            failures.append("the title screen is not showing; ");
            return false;
        }
        if (!click(title, Component.translatable("strategos.skirmish.button"))) {
            return false;
        }
        Screen setup = mc.screen;
        if (!(setup instanceof SkirmishScreen)) {
            failures.append("Skirmish opened ").append(setup).append("; ");
            return false;
        }
        if (!click(setup, Component.translatable("strategos.skirmish.start"))) {
            return false;
        }
        clicked = true;
        return true;
    }

    /** One click as a player's: a press and a release on the middle of the button named {@code label}. */
    private boolean click(Screen screen, Component label) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button b && b.getMessage().equals(label) && b.active) {
                double x = b.getX() + b.getWidth() / 2.0;
                double y = b.getY() + b.getHeight() / 2.0;
                clicks++;
                screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                return true;
            }
        }
        failures.append("no active button ")
                .append(label.getString())
                .append(" on ")
                .append(screen.getClass());
        failures.append("; ");
        return false;
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (ctx.captureName != null) {
            return false; // the shot comes first
        }
        if (!clicked) { // the runner made a world of its own: the clicks failed, and failures says why
            return done(ctx);
        }
        if (tick > 20_000) {
            failures.append("no running skirmish in time; ");
            return done(ctx);
        }
        MatchStateMsg state = StrategosClient.state();
        boolean live = StrategosClient.match() != null
                && state != null
                && state.lifecycle() == MatchStateMsg.Lifecycle.RUNNING;
        if (!live) {
            return false;
        }
        if (tick % 20 == 0 && !rts.active()) {
            rts.enter();
        }
        if (!rts.active() || rts.view() == null || ++running < ARRIVE) {
            return false;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            failures.append("no integrated server; ");
            return done(ctx);
        }
        if (!frozen) { // the match stands as it is for the frame
            frozen = true;
            server.execute(() -> {
                serverSide = inspect(server);
                ScenarioWorld.run(server, "tick freeze");
            });
            running = 0;
            return false;
        }
        if (serverSide == null || running < ARRIVE) {
            return false;
        }
        if (!shot) {
            shot = true;
            ctx.capture("skirmish_running");
            return false;
        }
        check(clicks <= MOST, clicks + " clicks from the title screen to a running skirmish, at most " + MOST);
        check(
                SkirmishSetup.DEFAULTS.map().equals(SkirmishSetup.FIRST_SKIRMISH)
                        && SkirmishSetup.DEFAULTS.level() == SkirmishSetup.Level.NORMAL,
                "the setup's defaults are " + SkirmishSetup.DEFAULTS);
        String found = serverSide;
        if (found != null && !found.isEmpty()) {
            failures.append(found).append("; ");
        }
        ctx.result.addProperty("clicks", clicks);
        ctx.result.addProperty("map", SkirmishSetup.DEFAULTS.map());
        ctx.result.addProperty("level", SkirmishSetup.DEFAULTS.level().name());
        return done(ctx);
    }

    /** On the server thread: a match on the first map, with the player seated in it. Empty when there is. */
    private static String inspect(IntegratedServer server) {
        List<String> found = new ArrayList<>();
        MatchRuntime mine = null;
        for (MatchRuntime rt : StrategosServer.get().matches().all()) {
            ResourceLocation map = rt.map();
            if (map != null && map.toString().equals(SkirmishSetup.FIRST_SKIRMISH)) {
                mine = rt;
            }
        }
        if (mine == null) {
            found.add("no match on " + SkirmishSetup.FIRST_SKIRMISH);
        } else if (StrategosServer.get()
                        .matches()
                        .seatOf(ScenarioWorld.player(server).getUUID())
                == null) {
            found.add("the player sits in no match");
        }
        return String.join("; ", found);
    }

    private void check(boolean ok, String what) {
        if (!ok) {
            failures.append(what).append("; ");
        }
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
