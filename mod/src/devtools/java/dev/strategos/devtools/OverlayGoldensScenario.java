package dev.strategos.devtools;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * PATH-10.3: the movement overlay's goldens, on {@link RtsGoldensScenario}'s scene in the RTS view. The overlay is
 * turned on, the own twelve walk round the stone pad, and the server freezes the game at a fixed match tick, so the units,
 * their paths and markers stand where the deterministic core put them; the feed goes on sending that frozen state. The
 * GUI is hidden: the overlay's panel holds timings, and the toggles talk in the chat. Frames: every layer, then the
 * surfaces and components alone.
 */
final class OverlayGoldensScenario implements Scenario {
    /** Match ticks the walk runs before the freeze. */
    private static final int WALK = 40;

    private final StringBuilder failures = new StringBuilder();
    private volatile @Nullable MatchRuntime match;
    /** The match tick to freeze at, set on the server thread; -1 before the walk. */
    private volatile long freezeAt = -1;

    private volatile long frozeAt = -1;
    private int phase;
    private int inPhase;

    @Override
    public String name() {
        return "overlay_goldens";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        match = RtsGoldensScenario.scene(server);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> freezeOnTime(e.getServer()));
    }

    /** At the match tick asked for, after the match stepped: the next ticks step nothing. */
    private void freezeOnTime(net.minecraft.server.MinecraftServer server) {
        MatchRuntime rt = match;
        var core = rt == null ? null : rt.core();
        if (core == null || freezeAt < 0 || frozeAt >= 0 || core.queries().tick() < freezeAt) {
            return;
        }
        server.tickRateManager().setFrozen(true);
        frozeAt = core.queries().tick();
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        GLFW.glfwSetCursorPos(
                mc.getWindow().getWindow(),
                mc.getWindow().getScreenWidth() / 2.0,
                mc.getWindow().getScreenHeight() / 2.0);
        if (tick == 1) {
            mc.options.renderDistance().set(12);
        }
        if (tick == 20) {
            rts.enter();
        }
        if (tick < 40 || mc.screen == null || !rts.active() || rts.view() == null) {
            return tick > 600 && fail(ctx, "RTS mode is not on");
        }
        if (ctx.captureName != null) {
            return false;
        }
        inPhase++;
        switch (phase) {
            case 0 -> {
                if (RtsGoldensScenario.bodies(mc) == RtsGoldensScenario.OWN + RtsGoldensScenario.ENEMIES
                        && inPhase >= 100
                        && mc.levelRenderer.hasRenderedAllSections()) {
                    mc.options.hideGui = true;
                    check(ClientCommandHandler.runCommand("strategos-client overlay"), "the overlay command failed");
                    next();
                } else if (inPhase > 600) {
                    return fail(ctx, RtsGoldensScenario.bodies(mc) + " bodies arrived");
                }
            }
            case 1 -> {
                if (inPhase >= 40) { // the surface pages arrive at 2 Hz
                    walk(mc);
                    next();
                }
            }
            case 2 -> {
                if (frozeAt >= 0 && inPhase >= 40) { // the frozen state reaches the client: paths every 4 ticks
                    ctx.result.addProperty("frozeAtMatchTick", frozeAt);
                    RtsGoldensScenario.pinBodies(mc);
                    ctx.capture("overlay_all");
                    next();
                } else if (inPhase > 400) {
                    return fail(ctx, "the game never froze");
                }
            }
            case 3 -> {
                check(ClientCommandHandler.runCommand("strategos-client overlay paths"), "the paths toggle failed");
                check(ClientCommandHandler.runCommand("strategos-client overlay units"), "the units toggle failed");
                next();
            }
            case 4 -> {
                if (inPhase >= 5) {
                    ctx.capture("overlay_surfaces");
                    next();
                }
            }
            default -> {
                IntegratedServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> server.tickRateManager().setFrozen(false));
                }
                return done(ctx);
            }
        }
        return false;
    }

    /** The own twelve to the far side of the stone pad, round its corner; the freeze comes {@value #WALK} ticks later. */
    private void walk(Minecraft mc) {
        MatchRuntime rt = match;
        IntegratedServer server = mc.getSingleplayerServer();
        if (rt == null || server == null) {
            return;
        }
        server.execute(() -> {
            var core = rt.core();
            if (core == null) {
                return;
            }
            UnitSet own = UnitSet.of(core.queries().unitHandlesOf(new FactionSlot(0)));
            rt.submit(PlayerSlot.ADMIN, Move.replace(own, new Vec2d(74, 92)));
            freezeAt = core.queries().tick() + WALK;
        });
    }

    private void next() {
        phase++;
        inPhase = 0;
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
