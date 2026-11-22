package dev.strategos.devtools;

import dev.strategos.core.content.CivilisationArt;
import dev.strategos.core.protocol.SkirmishSetup;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.menu.ArenaWorldLauncher;
import dev.strategos.mod.client.menu.ClientLobby;
import dev.strategos.mod.client.menu.MultiplayerLobbyScreen;
import dev.strategos.mod.client.rts.RtsScreen;
import dev.strategos.mod.server.net.LobbyConfiguration;
import dev.strategos.mod.server.net.LobbyReply;
import dev.strategos.mod.server.net.LobbyState;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Finite DTO fixtures prove presentation; the final real button flow exercises registered production transport. */
final class LobbyScenario implements Scenario {
    private static final String[] FRAMES = {
        "lobby_entry", "lobby_host", "lobby_ready", "lobby_member", "lobby_preparing", "lobby_error", "lobby_refused"
    };
    private final StringBuilder failures = new StringBuilder();
    private int phase;
    private int inPhase;
    private int captures;
    private int clicks;
    private boolean shown;
    private boolean capturing;

    @Override
    public String name() {
        return "m7-lobby";
    }

    @Override
    public String worldName() {
        return ArenaWorldLauncher.WORLD;
    }

    @Override
    public boolean openWorld(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen title)) return false;
        StrategosClient.arena().launch(mc, title, null);
        return true;
    }

    private static LobbyState fixture(
            int self, boolean mayConfigure, LobbyState.Phase phase, boolean ready, String reason) {
        var seats = new ArrayList<LobbyConfiguration.Seat>();
        var members = new ArrayList<LobbyState.Member>();
        String[] names = {"Alpha", "Bravo", "Charlie", "Delta"};
        for (int i = 0; i < 4; i++) {
            seats.add(new LobbyConfiguration.Seat(i, LobbyConfiguration.Kind.HUMAN, i, i, "strategos:placeholder", i));
            members.add(new LobbyState.Member(i, names[i], ready || (reason.isEmpty() && i % 2 == 1), i == 0));
        }
        return new LobbyState(
                new LobbyConfiguration(4, seats, SkirmishSetup.DEFAULTS),
                members,
                self,
                mayConfigure,
                12,
                7,
                phase,
                reason);
    }

    private void show(Minecraft mc) {
        ClientLobby.clear();
        if (phase != 0) {
            ClientLobby.session().beginRequest();
            ClientLobby.accept(fixture(
                    phase == 3 ? 1 : 0,
                    phase != 3,
                    phase == 4 ? LobbyState.Phase.PREPARING : LobbyState.Phase.EDITING,
                    phase == 2 || phase == 3 || phase == 4,
                    phase == 5 ? "preparation_failed" : ""));
            if (phase == 6) ClientLobby.reply(LobbyReply.REFUSED);
        }
        ClientLobby.open(null);
        if (!(mc.screen instanceof MultiplayerLobbyScreen screen)) {
            failures.append("lobby screen missing; ");
            return;
        }
        if (screen.isPauseScreen()) failures.append("lobby pauses the server; ");
        if (phase == 0) {
            expectButton(screen, "strategos.lobby.create", true);
            expectButton(screen, "strategos.lobby.join", true);
        } else {
            expectButton(screen, "strategos.lobby.start", phase == 2);
            expectButton(
                    screen,
                    phase == 2 || phase == 3 || phase == 4 ? "strategos.lobby.not_ready" : "strategos.lobby.ready",
                    phase != 4);
            expectButton(screen, "strategos.lobby.leave", true);
        }
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget
                    && (widget.getX() < 0
                            || widget.getY() < 0
                            || widget.getX() + widget.getWidth() > screen.width
                            || widget.getY() + widget.getHeight() > screen.height)) {
                failures.append("off-screen lobby control at fixture ")
                        .append(phase)
                        .append("; ");
            }
        }
    }

    private void expectButton(Screen screen, String key, boolean active) {
        Component label = Component.translatable(key);
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().equals(label)) {
                if (widget.active != active)
                    failures.append("wrong enabled state: ").append(key).append("; ");
                return;
            }
        }
        failures.append("missing button: ").append(key).append("; ");
    }

    private boolean click(Screen screen, String key) {
        Component label = Component.translatable(key);
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget
                    && widget.active
                    && widget.getMessage().equals(label)) {
                double x = widget.getX() + widget.getWidth() / 2.0;
                double y = widget.getY() + widget.getHeight() / 2.0;
                screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                clicks++;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        inPhase++;
        if (phase < FRAMES.length) {
            if (!shown) {
                show(mc);
                shown = true;
            }
            if (!capturing && inPhase >= 8) {
                ctx.capture(FRAMES[phase]);
                capturing = true;
                captures++;
            } else if (capturing && ctx.captureName == null) {
                phase++;
                inPhase = 0;
                shown = false;
                capturing = false;
            }
            return false;
        }
        if (inPhase > 600) {
            failures.append("real UI transport stalled at phase ").append(phase);
            var model = ClientLobby.session().model();
            ctx.result.addProperty(
                    "lastLobbyPhase",
                    model == null ? "none" : model.state().phase().name());
            ctx.result.addProperty(
                    "lastScreen",
                    mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            ctx.result.addProperty("matchAdmitted", StrategosClient.match() != null);
            return done(ctx, false);
        }
        switch (phase) {
            case 7 -> {
                ClientLobby.clear();
                mc.setScreen(new PauseScreen(true));
                next();
            }
            case 8 -> {
                if (mc.screen != null && click(mc.screen, "strategos.lobby.button")) next();
            }
            case 9 -> {
                if (mc.screen instanceof MultiplayerLobbyScreen && click(mc.screen, "strategos.lobby.create")) next();
            }
            case 10 -> {
                var model = ClientLobby.session().model();
                if (model != null
                        && model.rows().size() == 2
                        && mc.screen != null
                        && click(mc.screen, "strategos.lobby.kind.human")) next();
            }
            case 11 -> {
                var model = ClientLobby.session().model();
                if (model != null
                        && model.state().configuration().seats().get(1).kind() == LobbyConfiguration.Kind.AI
                        && !ClientLobby.session().waiting()
                        && mc.screen != null
                        && click(mc.screen, "strategos.lobby.civilisation_default")) next();
            }
            case 12 -> {
                var model = ClientLobby.session().model();
                if (model != null
                        && model.state()
                                .configuration()
                                .seats()
                                .getFirst()
                                .civilisation()
                                .equals(CivilisationArt.ROMAN)
                        && !ClientLobby.session().waiting()
                        && mc.screen != null
                        && click(mc.screen, "strategos.lobby.ready")) next();
            }
            case 13 -> {
                var model = ClientLobby.session().model();
                if (model != null
                        && model.mayStart()
                        && !ClientLobby.session().waiting()
                        && mc.screen != null
                        && click(mc.screen, "strategos.lobby.start")) next();
            }
            case 14 -> {
                var model = ClientLobby.session().model();
                var join = StrategosClient.match();
                var presentation = StrategosClient.presentation();
                if (model != null
                        && model.state().phase() == LobbyState.Phase.STARTED
                        && join != null
                        && presentation != null
                        && presentation.matchId() == join.matchId()
                        && mc.screen instanceof RtsScreen
                        && StrategosClient.rts().rig() != null) {
                    if (join.slot().value() != 0 || join.factions() != 2) failures.append("wrong real admission; ");
                    boolean roman = presentation.factions().hasCivilisation(0, CivilisationArt.ROMAN);
                    ctx.result.addProperty("romanThroughProductionLobby", roman);
                    if (!roman) failures.append("Roman choice was lost on real admission; ");
                    return done(ctx, true);
                }
            }
            default -> throw new IllegalStateException("unknown lobby scenario phase");
        }
        return false;
    }

    private void next() {
        phase++;
        inPhase = 0;
    }

    private boolean done(ScenarioContext ctx, boolean transport) {
        ctx.result.addProperty("presentationFixtureFrames", captures);
        ctx.result.addProperty("actualTransportFlow", transport);
        ctx.result.addProperty("clicks", clicks);
        ctx.result.addProperty("ok", transport && captures == FRAMES.length && failures.isEmpty());
        if (!failures.isEmpty()) ctx.result.addProperty("failures", failures.toString());
        ClientLobby.clear();
        return true;
    }
}
