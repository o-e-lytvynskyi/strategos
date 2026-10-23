package dev.strategos.devtools;

import com.mojang.blaze3d.platform.InputConstants;
import dev.strategos.core.client.input.KeyCode;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * CAM-05 and UI-04.4 on screen: in {@link RtsGoldensScenario}'s scene the own twelve are box-selected and kept in group
 * 1; V steps down into first person, which closes the RTS screen and keeps the selection and the group; looking 20°
 * down, G sends the selection to the block in view; after the 5 s cooldown F6 comes back, which reopens the RTS screen
 * with the selection and the group as they were. It times the way back from the key to the RTS screen.
 */
final class RtsFirstPersonScenario implements Scenario {
    private final StringBuilder failures = new StringBuilder();
    private volatile @Nullable MatchRuntime match;
    private int phase;
    private int inPhase;
    private int backPressed;

    @Override
    public String name() {
        return "rts_first_person";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        match = RtsGoldensScenario.scene(server);
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        RtsMode rts = StrategosClient.rts();
        if (tick == 20) {
            rts.enter();
        }
        if (tick < 40 || mc.player == null) {
            return false;
        }
        if (ctx.captureName != null) {
            return false;
        }
        inPhase++;
        Screen screen = mc.screen;
        switch (phase) {
            case 0 -> {
                if (RtsGoldensScenario.bodies(mc) == RtsGoldensScenario.OWN + RtsGoldensScenario.ENEMIES
                        && screen != null
                        && rts.active()
                        && inPhase >= 60) {
                    GLFW.glfwSetCursorPos(
                            mc.getWindow().getWindow(),
                            mc.getWindow().getScreenWidth() / 2.0,
                            mc.getWindow().getScreenHeight() / 2.0);
                    screen.mouseClicked(1, 1, 0);
                    screen.mouseMoved(screen.width - 2, screen.height - 2);
                    screen.mouseReleased(screen.width - 2, screen.height - 2, 0);
                    screen.keyPressed(KeyCode.digit(1).code(), 0, 2); // Ctrl+1
                    check(
                            rts.selection().handles().size() == RtsGoldensScenario.OWN,
                            "selected " + rts.selection().handles().size());
                    check(
                            rts.groups().members(1).size() == RtsGoldensScenario.OWN,
                            "group 1 holds " + rts.groups().members(1).size());
                    next();
                } else if (inPhase > 600) {
                    return fail(ctx, "the scene never stood");
                }
            }
            case 1 -> {
                if (screen != null) {
                    screen.keyPressed(KeyCode.V, 0, 0); // to first person
                    next();
                }
            }
            case 2 -> {
                if (!rts.active() && mc.screen == null && mode(mc) == GameType.ADVENTURE) {
                    ctx.result.addProperty("toFirstPersonTicks", inPhase);
                    check(rts.firstPerson(), "the client does not know it is in first person");
                    check(
                            rts.selection().handles().size() == RtsGoldensScenario.OWN,
                            "first person lost the selection");
                    check(rts.groups().members(1).size() == RtsGoldensScenario.OWN, "first person lost group 1");
                    mc.player.setXRot(20); // look at the ground a few blocks ahead
                    next();
                } else if (inPhase > 100) {
                    return fail(ctx, "no first person: screen " + mc.screen + ", mode " + mode(mc));
                }
            }
            case 3 -> {
                if (inPhase == 5) {
                    ctx.capture("first_person");
                } else if (inPhase == 10) {
                    KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(KeyCode.G)); // the selection to the view
                } else if (inPhase == 20) {
                    check(rts.ordersSent() == 1, rts.ordersSent() + " orders sent from first person");
                    ctx.result.addProperty("orderedFromFirstPerson", ordered());
                    check(ordered() == RtsGoldensScenario.OWN, ordered() + " units took the order from first person");
                } else if (inPhase >= 110) { // past the 5 s cooldown since the switch
                    KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(KeyCode.F6)); // back
                    backPressed = tick;
                    next();
                }
            }
            case 4 -> {
                if (rts.active() && mc.screen != null && mode(mc) == GameType.SPECTATOR) {
                    ctx.result.addProperty("backTicks", tick - backPressed);
                    check(tick - backPressed <= 10, "the way back took " + (tick - backPressed) + " ticks");
                    check(!rts.firstPerson(), "back, the client still thinks it is in first person");
                    check(
                            rts.selection().handles().size() == RtsGoldensScenario.OWN,
                            "the round trip lost the selection");
                    check(rts.groups().members(1).size() == RtsGoldensScenario.OWN, "the round trip lost group 1");
                    next();
                } else if (inPhase > 100) {
                    return fail(ctx, "not back: screen " + mc.screen + ", mode " + mode(mc));
                }
            }
            case 5 -> {
                if (inPhase >= 10) {
                    ctx.capture("back_to_rts");
                    next();
                }
            }
            default -> {
                return done(ctx);
            }
        }
        return false;
    }

    private static @Nullable GameType mode(Minecraft mc) {
        return mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
    }

    /** Own units whose first order is a Move the server accepted from first person. */
    private int ordered() {
        MatchRuntime rt = match;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (rt == null || server == null) {
            return -1;
        }
        return server.submit(() -> {
                    var core = rt.core();
                    if (core == null) {
                        return -1;
                    }
                    int n = 0;
                    for (int h : core.queries().unitHandlesOf(new FactionSlot(0))) {
                        if (!core.queries().orders(h).isEmpty()) {
                            n++;
                        }
                    }
                    return n;
                })
                .join();
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
