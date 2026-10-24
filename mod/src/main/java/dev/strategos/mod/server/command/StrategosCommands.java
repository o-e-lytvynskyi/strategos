package dev.strategos.mod.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/**
 * {@code /strategos}, for operators only: players below permission level 2 neither run nor see it. A command without a
 * match id acts on the match that holds the source's position. Debug orders go through the match's inbox like any
 * command and reply with their result after the next step. The subtrees live in {@link MatchCommands}, {@link
 * MapCommands}, {@link UnitCommands}, {@link TemplateCommands} and {@link InspectCommands}; this class holds the root
 * and what they share.
 */
public final class StrategosCommands {
    public static final int DEFAULT_SIZE = 64;

    /** A new match's band reaches this far under the source's feet and above them, cut to the build height. */
    public static final int BELOW = 8;

    public static final int ABOVE = 24;

    private StrategosCommands() {}

    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Builds the tree on any dispatcher, so tests can inspect it. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("strategos")
                .requires(source -> source.hasPermission(2))
                .then(MatchCommands.match())
                .then(MatchCommands.skirmish())
                .then(MapCommands.map())
                .then(UnitCommands.spawn())
                .then(BuildCommands.build())
                .then(UnitCommands.order())
                .then(TemplateCommands.templates())
                .then(UnitCommands.debug())
                .then(MatchCommands.journal())
                .then(InspectCommands.nav())
                .then(InspectCommands.stats())
                .then(withMatch("hash", (source, rt) -> {
                    MatchCore core = coreOf(rt);
                    return rt.id() + " tick " + core.queries().tick() + ", hash " + Hex64.format(core.stateHash());
                })));
    }

    /** The match's space for {@code match create}: centred on the feet, its band cut to the level's build height. */
    public static MatchSpace spaceAround(ServerLevel level, BlockPos feet, int sizeX, int sizeZ) {
        int below = Math.min(BELOW, feet.getY() - level.getMinBuildHeight());
        int above = Math.min(ABOVE, level.getMaxBuildHeight() - 1 - feet.getY());
        return MatchSpace.centredOn(level.dimension(), feet, sizeX, sizeZ, below, above);
    }

    /** "accepted 3/3", or the refusal and its first reason. */
    public static String describe(CommandResult result) {
        int total = result.accepted() + result.rejected();
        String counts = result.accepted() + "/" + total;
        if (result.outcome() == CommandOutcome.ACCEPTED) {
            return "accepted " + counts;
        }
        String reason = result.firstReason() == null ? "" : ", " + result.firstReason();
        return (result.outcome() == CommandOutcome.PARTIAL ? "partly accepted " : "refused, accepted ")
                + counts
                + reason;
    }

    /** Submits as ADMIN; the result comes back to the source after the next step. */
    static void send(CommandSourceStack source, MatchRuntime rt, Command command) {
        rt.submit(PlayerSlot.ADMIN, command, result -> {
            String text = rt.id() + " " + result.kind() + ": " + describe(result);
            if (result.outcome() == CommandOutcome.REJECTED) {
                source.sendFailure(Component.literal(text));
            } else {
                source.sendSuccess(() -> Component.literal(text), false);
            }
        });
    }

    /** {@code <name> [id]}: the work gets the match named by id, or the one at the source. */
    static LiteralArgumentBuilder<CommandSourceStack> withMatch(String name, MatchWork work) {
        return Commands.literal(name)
                .executes(ctx -> withMatchAtSource(ctx.getSource(), rt -> work.run(ctx.getSource(), rt)))
                .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    return run(ctx.getSource(), () -> work.run(ctx.getSource(), byId(id)));
                }));
    }

    static int withMatchAtSource(CommandSourceStack source, Function<MatchRuntime, @Nullable String> work) {
        return run(source, () -> {
            MatchRuntime rt = registry().at(source.getLevel(), BlockPos.containing(source.getPosition()));
            if (rt == null) {
                throw new MatchRefusedException("no match here; stand in one or give its id, as in m1");
            }
            return work.apply(rt);
        });
    }

    /** Runs the work and reports its line; a refusal becomes the command's failure message. */
    static int run(CommandSourceStack source, Work work) {
        try {
            String line = work.run();
            if (line != null) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        } catch (MatchRefusedException | IllegalStateException e) {
            source.sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }

    static MatchRuntime byId(String id) {
        String digits = id.startsWith("m") ? id.substring(1) : id;
        int value;
        try {
            value = Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new MatchRefusedException("'" + id + "' is not a match id, such as m1");
        }
        MatchRuntime rt = value < 1 ? null : registry().get(new MatchId(value));
        if (rt == null) {
            throw new MatchRefusedException("no match " + id);
        }
        return rt;
    }

    static TemplateId template(CommandContext<CommandSourceStack> ctx) {
        return new TemplateId(ResourceLocationArgument.getId(ctx, "template").toString());
    }

    static MatchCore coreOf(MatchRuntime rt) {
        MatchCore core = rt.core();
        if (core == null) {
            throw new MatchRefusedException(rt.id() + " is " + rt.lifecycle() + " and has no core");
        }
        return core;
    }

    static MatchRegistry registry() {
        return StrategosServer.get().matches();
    }

    @FunctionalInterface
    interface Work {
        @Nullable String run();
    }

    @FunctionalInterface
    interface MatchWork {
        String run(CommandSourceStack source, MatchRuntime rt);
    }
}
