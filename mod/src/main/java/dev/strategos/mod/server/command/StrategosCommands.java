package dev.strategos.mod.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.world.ArenaPolicy;
import dev.strategos.mod.server.world.ArenaWorld;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/**
 * {@code /strategos}, for operators only: players below permission level 2 neither run nor see it. A command without a
 * match id acts on the match that holds the source's position. Debug orders go through the match's inbox like any
 * command and reply with their result after the next step.
 */
public final class StrategosCommands {
    public static final int DEFAULT_SIZE = 64;

    /** A new match's band reaches this far under the source's feet and above them, cut to the build height. */
    public static final int BELOW = 8;

    public static final int ABOVE = 24;

    private static final Pattern JOURNAL_NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    private StrategosCommands() {}

    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Builds the tree on any dispatcher, so tests can inspect it. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("strategos")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("match")
                        .then(Commands.literal("create")
                                .executes(ctx -> create(ctx.getSource(), DEFAULT_SIZE, DEFAULT_SIZE))
                                .then(Commands.argument("sizeX", size())
                                        .then(Commands.argument("sizeZ", size())
                                                .executes(ctx -> create(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "sizeX"),
                                                        IntegerArgumentType.getInteger(ctx, "sizeZ"))))))
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(withMatch("pause", (source, rt) -> {
                            registry().pause(rt.id());
                            return rt.id() + " paused";
                        }))
                        .then(withMatch("resume", (source, rt) -> {
                            registry().resume(rt.id());
                            return rt.id() + " resumed";
                        }))
                        .then(withMatch("dispose", (source, rt) -> {
                            registry().dispose(rt.id());
                            return rt.id() + " disposed";
                        })))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .executes(ctx -> spawn(ctx.getSource(), template(ctx), 1, 0))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, UnitSet.MAX_SIZE))
                                        .executes(ctx -> spawn(
                                                ctx.getSource(),
                                                template(ctx),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                0))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, FactionSlot.MAX))
                                                .executes(ctx -> spawn(
                                                        ctx.getSource(),
                                                        template(ctx),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        IntegerArgumentType.getInteger(ctx, "slot")))))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("spawn")
                                .executes(ctx -> spawn(ctx.getSource(), 1, 0))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, UnitSet.MAX_SIZE))
                                        .executes(ctx ->
                                                spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), 0))
                                        .then(Commands.argument(
                                                        "faction", IntegerArgumentType.integer(0, FactionSlot.MAX))
                                                .executes(ctx -> spawn(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                        IntegerArgumentType.getInteger(ctx, "faction"))))))
                        .then(Commands.literal("move")
                                .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                                        .executes(ctx -> move(ctx, null))
                                        .then(Commands.argument("handle", IntegerArgumentType.integer())
                                                .executes(ctx ->
                                                        move(ctx, IntegerArgumentType.getInteger(ctx, "handle"))))))
                        .then(Commands.literal("stop")
                                .executes(ctx -> stop(ctx.getSource(), null))
                                .then(Commands.argument("handle", IntegerArgumentType.integer())
                                        .executes(ctx ->
                                                stop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "handle")))))
                        .then(withMatch("replay", StrategosCommands::replay)))
                .then(Commands.literal("journal")
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> withMatchAtSource(
                                                ctx.getSource(),
                                                rt -> saveJournal(rt, StringArgumentType.getString(ctx, "name"))))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> run(
                                                        ctx.getSource(),
                                                        () -> saveJournal(
                                                                byId(StringArgumentType.getString(ctx, "id")),
                                                                StringArgumentType.getString(ctx, "name"))))))))
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

    private static IntegerArgumentType size() {
        return IntegerArgumentType.integer(MatchRegistry.MIN_SIZE, MatchRegistry.MAX_SIZE);
    }

    /** In an arena world, in the next slot; elsewhere around the source's feet (M0). Saved with the world either way. */
    private static int create(CommandSourceStack source, int sizeX, int sizeZ) {
        if (ArenaWorld.isArena(source.getServer())) {
            return run(source, () -> {
                MatchRuntime rt = registry().createInArena(sizeX, sizeZ, MatchOptions.generated(ArenaPolicy.FLOOR_Y));
                return rt.id() + " created, " + sizeX + " x " + sizeZ + " in the arena slot at "
                        + rt.space().origin().toShortString();
            });
        }
        ServerLevel level = source.getLevel();
        BlockPos feet = BlockPos.containing(source.getPosition());
        MatchOptions options = MatchOptions.generated(feet.getY());
        return run(source, () -> {
            MatchRuntime rt = registry().create(level, spaceAround(level, feet, sizeX, sizeZ), options);
            return rt.id() + " created, " + sizeX + " x " + sizeZ + " around " + feet.toShortString();
        });
    }

    private static int list(CommandSourceStack source) {
        List<MatchRuntime> all = registry().all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("no matches"), false);
            return 0;
        }
        for (MatchRuntime rt : all) {
            MatchCore core = rt.core();
            String line =
                    rt.id() + " " + rt.lifecycle() + " in " + rt.space().level().location() + " at "
                            + rt.space().origin().toShortString() + ", "
                            + rt.space().sizeX() + " x " + rt.space().sizeZ()
                            + ", tick " + rt.tick() + ", "
                            + (core == null ? 0 : core.queries().unitCount()) + " units, "
                            + rt.bodies().count() + " bodies";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return all.size();
    }

    private static int spawn(CommandSourceStack source, int count, int faction) {
        return spawn(source, TemplateSet.DEBUG_WALKER, count, faction);
    }

    /** Units of {@code template} around the source, through the command path (an unknown template is refused there). */
    private static int spawn(CommandSourceStack source, TemplateId template, int count, int faction) {
        return withMatchAtSource(source, rt -> {
            MatchSpace space = rt.space();
            Vec2d at = new Vec2d(space.localX(source.getPosition().x), space.localZ(source.getPosition().z));
            send(source, rt, new SpawnUnits(template, new FactionSlot(faction), at, count, count > 1 ? 2 : 0));
            return null;
        });
    }

    private static int move(CommandContext<CommandSourceStack> ctx, @Nullable Integer handle) {
        CommandSourceStack source = ctx.getSource();
        ColumnPos column = ColumnPosArgument.getColumnPos(ctx, "pos");
        return withMatchAtSource(source, rt -> {
            Vec2d target =
                    new Vec2d(rt.space().localX(column.x() + 0.5), rt.space().localZ(column.z() + 0.5));
            send(source, rt, Move.replace(unitsOf(rt, handle), target));
            return null;
        });
    }

    private static int stop(CommandSourceStack source, @Nullable Integer handle) {
        return withMatchAtSource(source, rt -> {
            send(source, rt, new Stop(unitsOf(rt, handle)));
            return null;
        });
    }

    private static String replay(CommandSourceStack source, MatchRuntime rt) {
        MatchCore core = coreOf(rt);
        ReplayResult result = ReplayDriver.replay(core.journal(), core.queries().templates());
        if (result.matches()) {
            return rt.id() + " replay OK at tick " + result.finalTick() + ", hash " + Hex64.format(result.finalHash());
        }
        return rt.id() + " replay diverges at checkpoint tick " + result.firstDivergentCheckpoint() + ": recorded "
                + Hex64.format(result.expectedHash()) + ", replayed " + Hex64.format(result.actualHash());
    }

    /** The given handle, or the match's first 300 units. */
    private static UnitSet unitsOf(MatchRuntime rt, @Nullable Integer handle) {
        if (handle != null) {
            return UnitSet.of(handle);
        }
        int[] all = coreOf(rt).queries().unitHandles();
        return UnitSet.of(Arrays.copyOf(all, Math.min(all.length, UnitSet.MAX_SIZE)));
    }

    /** Submits as ADMIN; the result comes back to the source after the next step. */
    private static void send(CommandSourceStack source, MatchRuntime rt, Command command) {
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
    private static LiteralArgumentBuilder<CommandSourceStack> withMatch(String name, MatchWork work) {
        return Commands.literal(name)
                .executes(ctx -> withMatchAtSource(ctx.getSource(), rt -> work.run(ctx.getSource(), rt)))
                .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    return run(ctx.getSource(), () -> work.run(ctx.getSource(), byId(id)));
                }));
    }

    private static int withMatchAtSource(CommandSourceStack source, Function<MatchRuntime, @Nullable String> work) {
        return run(source, () -> {
            MatchRuntime rt = registry().at(source.getLevel(), BlockPos.containing(source.getPosition()));
            if (rt == null) {
                throw new MatchRefusedException("no match here; stand in one or give its id, as in m1");
            }
            return work.apply(rt);
        });
    }

    /** Runs the work and reports its line; a refusal becomes the command's failure message. */
    private static int run(CommandSourceStack source, Work work) {
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

    private static MatchRuntime byId(String id) {
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

    /**
     * CORE-11.4: writes the match's journal, from its oldest kept segment to now, to
     * {@code <world>/strategos/journals/<name>.strj}, which {@code ReplayRunner} replays in plain JUnit.
     */
    private static String saveJournal(MatchRuntime rt, String name) {
        if (!JOURNAL_NAME.matcher(name).matches()) {
            throw new MatchRefusedException("journal names are 1 to 64 of a-z, 0-9, _ and -; got '" + name + "'");
        }
        MatchCore core = coreOf(rt);
        Path file = StrategosServer.get()
                .server()
                .getWorldPath(LevelResource.ROOT)
                .resolve("strategos")
                .resolve("journals")
                .resolve(name + ".strj");
        JournalExport export = new JournalExport(core.journal(), core.queries().tick(), core.stateHash());
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, JournalFormat.encode(export));
        } catch (IOException e) {
            throw new MatchRefusedException("cannot write " + file + ": " + e.getMessage());
        }
        return rt.id() + ": journal of ticks " + export.journal().startTick() + "–" + export.liveTick()
                + " saved to strategos/journals/" + name + ".strj";
    }

    private static TemplateId template(CommandContext<CommandSourceStack> ctx) {
        return new TemplateId(ResourceLocationArgument.getId(ctx, "template").toString());
    }

    private static MatchCore coreOf(MatchRuntime rt) {
        MatchCore core = rt.core();
        if (core == null) {
            throw new MatchRefusedException(rt.id() + " is " + rt.lifecycle() + " and has no core");
        }
        return core;
    }

    private static MatchRegistry registry() {
        return StrategosServer.get().matches();
    }

    @FunctionalInterface
    private interface Work {
        @Nullable String run();
    }

    @FunctionalInterface
    private interface MatchWork {
        String run(CommandSourceStack source, MatchRuntime rt);
    }
}
