package dev.strategos.mod.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.protocol.DebugSubscribe;
import dev.strategos.core.shared.MatchId;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.debug.PerfDump;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/**
 * PATH-10's commands under {@code /strategos}, for the config's {@code debugPermission}: {@code perf start|stop|dump
 * [id]} records a match's metrics and writes them as CSV ({@link PerfDump}); {@code debug subscribe <layers>} subscribes
 * the player who runs it to the overlay's feed, as the overlay's own payload does. They are registered beside {@link
 * StrategosCommands}, whose tree they join: Brigadier merges a second {@code strategos} root into the first.
 */
public final class DebugCommands {
    private static final Pattern RADIUS = Pattern.compile("\\d{1,3}");

    private DebugCommands() {}

    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Builds the commands on any dispatcher, so tests can inspect them. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("strategos")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("perf")
                        .requires(DebugCommands::permitted)
                        .then(perf("start", PerfDump::start))
                        .then(perf("stop", PerfDump::stop))
                        .then(perf("dump", (perf, rt) -> {
                            Path file = perf.dump(rt);
                            return rt.id() + ": perf written to strategos/perf/" + file.getFileName();
                        })))
                .then(Commands.literal("debug")
                        .then(Commands.literal("subscribe")
                                .requires(DebugCommands::permitted)
                                .then(Commands.argument("layers", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(words(), builder))
                                        .executes(ctx -> subscribe(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "layers")))))));
    }

    /**
     * A subscription from text: {@code all}, {@code none} or layer names, and a radius in blocks, separated by spaces
     * or commas; the radius is {@value DebugSubscribe#DEFAULT_RADIUS} unless given.
     */
    public static DebugSubscribe parse(String text) {
        int layers = 0;
        int radius = DebugSubscribe.DEFAULT_RADIUS;
        for (String token : text.trim().split("[\\s,]+", -1)) {
            DebugLayer layer = DebugLayer.byId(token);
            if (token.equals("all")) {
                layers = DebugLayer.ALL;
            } else if (token.equals("none")) {
                layers = 0;
            } else if (layer != null) {
                layers |= layer.bit();
            } else if (RADIUS.matcher(token).matches()) {
                radius = Integer.parseInt(token);
            } else if (!token.isEmpty()) {
                throw new IllegalArgumentException("unknown debug layer '" + token + "'; use " + words());
            }
        }
        return new DebugSubscribe(layers, radius);
    }

    /** The layers' names, comma-separated. */
    public static String names(int layers) {
        StringJoiner names = new StringJoiner(", ");
        for (DebugLayer layer : DebugLayer.values()) {
            if (layer.in(layers)) {
                names.add(layer.id());
            }
        }
        return names.toString();
    }

    private static List<String> words() {
        List<String> words = new ArrayList<>(List.of("all", "none"));
        for (DebugLayer layer : DebugLayer.values()) {
            words.add(layer.id());
        }
        return words;
    }

    private static int subscribe(CommandSourceStack source, String text) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("only a player subscribes to the debug feed"));
            return 0;
        }
        DebugSubscribe request;
        try {
            request = parse(text);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
        if (!StrategosServer.get().debug().subscribe(player, request)) {
            return 0; // the feed told the player why
        }
        String line = request.layers() == 0
                ? "debug feed off"
                : "debug feed: " + names(request.layers()) + " within " + request.radius() + " blocks";
        source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** {@code <name> [id]}: the work gets the match named by id, or the one at the source. */
    private static LiteralArgumentBuilder<CommandSourceStack> perf(String name, PerfWork work) {
        return Commands.literal(name)
                .executes(ctx -> run(ctx.getSource(), work, null))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> run(ctx.getSource(), work, StringArgumentType.getString(ctx, "id"))));
    }

    private static int run(CommandSourceStack source, PerfWork work, @Nullable String id) {
        try {
            StrategosServer server = StrategosServer.get();
            MatchRuntime rt = id == null
                    ? server.matches().at(source.getLevel(), BlockPos.containing(source.getPosition()))
                    : server.matches().get(new MatchId(matchId(id)));
            if (rt == null) {
                throw new MatchRefusedException(
                        id == null ? "no match here; stand in one or give its id, as in m1" : "no match " + id);
            }
            String line = work.run(server.perf(), rt);
            source.sendSuccess(() -> Component.literal(line), false);
            return 1;
        } catch (MatchRefusedException | IllegalStateException | IOException e) {
            source.sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static int matchId(String id) {
        try {
            int value = Integer.parseInt(id.startsWith("m") ? id.substring(1) : id);
            if (value >= 1) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new MatchRefusedException("'" + id + "' is not a match id, such as m1");
    }

    /** Whether the source may use the debug commands: the feed's permission level, 2 by default. */
    private static boolean permitted(CommandSourceStack source) {
        StrategosServer server = StrategosServer.getOrNull();
        return source.hasPermission(server == null ? 2 : server.debug().permission());
    }

    @FunctionalInterface
    private interface PerfWork {
        String run(PerfDump perf, MatchRuntime rt) throws IOException;
    }
}
