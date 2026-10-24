package dev.strategos.mod.server.command;

import static dev.strategos.mod.server.command.StrategosCommands.DEFAULT_SIZE;
import static dev.strategos.mod.server.command.StrategosCommands.byId;
import static dev.strategos.mod.server.command.StrategosCommands.coreOf;
import static dev.strategos.mod.server.command.StrategosCommands.registry;
import static dev.strategos.mod.server.command.StrategosCommands.run;
import static dev.strategos.mod.server.command.StrategosCommands.spaceAround;
import static dev.strategos.mod.server.command.StrategosCommands.withMatch;
import static dev.strategos.mod.server.command.StrategosCommands.withMatchAtSource;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.protocol.Resign;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.content.Scenarios;
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
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** The {@code /strategos match} and {@code journal} subtrees: creating, listing, pausing, seating and journals. */
final class MatchCommands {
    private MatchCommands() {}

    private static final Pattern JOURNAL_NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    /** {@code match create|list|pause|resume|dispose|join}: matches and their seats. */
    static LiteralArgumentBuilder<CommandSourceStack> match() {
        return Commands.literal("match")
                .then(Commands.literal("create")
                        .executes(ctx -> create(ctx.getSource(), DEFAULT_SIZE, DEFAULT_SIZE))
                        .then(Commands.literal("map")
                                .then(Commands.argument("map", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                MapPackageLoader.list(
                                                        ctx.getSource().getServer()),
                                                builder))
                                        .executes(ctx -> createOnMap(
                                                ctx.getSource(), ResourceLocationArgument.getId(ctx, "map")))))
                        .then(Commands.argument("sizeX", size())
                                .then(Commands.argument("sizeZ", size())
                                        .executes(ctx -> create(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "sizeX"),
                                                IntegerArgumentType.getInteger(ctx, "sizeZ"))))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("scenario")
                        .then(Commands.argument("scenario", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        Scenarios.list(ctx.getSource().getServer()), builder))
                                .executes(ctx -> withMatchAtSource(
                                        ctx.getSource(),
                                        rt -> scenario(
                                                ctx.getSource(),
                                                rt,
                                                ResourceLocationArgument.getId(ctx, "scenario"))))))
                .then(Commands.literal("config")
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("populationMax", "victory", "revealMap", "aiVision"), builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> withMatchAtSource(ctx.getSource(), rt -> {
                                            StrategosCommands.send(
                                                    ctx.getSource(),
                                                    rt,
                                                    new RuleChange(
                                                            StringArgumentType.getString(ctx, "key"),
                                                            StringArgumentType.getString(ctx, "value")));
                                            return null;
                                        })))))
                .then(Commands.literal("resign")
                        .then(Commands.argument("faction", IntegerArgumentType.integer(0, FactionSlot.MAX))
                                .executes(ctx -> withMatchAtSource(ctx.getSource(), rt -> {
                                    StrategosCommands.send(
                                            ctx.getSource(),
                                            rt,
                                            new Resign(
                                                    new FactionSlot(IntegerArgumentType.getInteger(ctx, "faction"))));
                                    return null;
                                }))))
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
                }))
                .then(Commands.literal("join")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, FactionSlot.MAX))
                                        .executes(ctx -> {
                                            ServerPlayer player =
                                                    ctx.getSource().getPlayerOrException();
                                            return run(
                                                    ctx.getSource(),
                                                    () -> join(
                                                            StringArgumentType.getString(ctx, "id"),
                                                            IntegerArgumentType.getInteger(ctx, "slot"),
                                                            player));
                                        })
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                    return run(
                                                            ctx.getSource(),
                                                            () -> join(
                                                                    StringArgumentType.getString(ctx, "id"),
                                                                    IntegerArgumentType.getInteger(ctx, "slot"),
                                                                    player));
                                                })))));
    }

    /** {@code journal save <name> [id]} (CORE-11.4). */
    static LiteralArgumentBuilder<CommandSourceStack> journal() {
        return Commands.literal("journal")
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
                                                        StringArgumentType.getString(ctx, "name")))))));
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

    /** The scenario set up in {@code rt} from its map's start slots (MATCH-01). */
    private static String scenario(CommandSourceStack source, MatchRuntime rt, ResourceLocation id) {
        ResourceLocation map = rt.map();
        if (map == null) {
            throw new MatchRefusedException(rt.id() + " was made on no map: a scenario needs its start slots");
        }
        Scenario scenario;
        List<StartSlot> starts;
        try {
            scenario = Scenarios.load(source.getServer(), id);
            starts = MapPackageLoader.load(source.getServer(), map).manifest().startSlots();
        } catch (IllegalArgumentException e) {
            throw new MatchRefusedException(String.valueOf(e.getMessage()));
        }
        if (!scenario.map().equals(map.toString())) {
            throw new MatchRefusedException(id + " is made for " + scenario.map() + ", not " + map);
        }
        var core = rt.core();
        if (core == null) {
            throw new MatchRefusedException(rt.id() + " has no core yet");
        }
        int sent = Scenarios.apply(rt, scenario, starts, core.queries().templates())
                .size();
        return rt.id() + " set up as " + id + ": " + sent + " commands";
    }

    /**
     * A match on a map package: in an arena world, in the next slot; elsewhere, its west edge 8 blocks east of the source
     * and its band from 16 blocks under the source's feet. It stays PREPARING while the map imports.
     */
    private static int createOnMap(CommandSourceStack source, ResourceLocation map) {
        if (ArenaWorld.isArena(source.getServer())) {
            return run(source, () -> {
                MatchRuntime rt = registry().createInArena(map, MatchOptions.generated(ArenaPolicy.FLOOR_Y));
                return rt.id() + " created on " + map + " in the arena slot at "
                        + rt.space().origin().toShortString() + "; importing";
            });
        }
        ServerLevel level = source.getLevel();
        BlockPos feet = BlockPos.containing(source.getPosition());
        return run(source, () -> {
            MapManifest m;
            try {
                m = MapPackageLoader.load(source.getServer(), map).manifest();
            } catch (IllegalArgumentException e) {
                throw new MatchRefusedException(String.valueOf(e.getMessage()));
            }
            BlockPos origin = new BlockPos(feet.getX() + 8, feet.getY() - 16, feet.getZ() - m.sizeZ() / 2);
            MatchSpace space = new MatchSpace(
                    level.dimension(), origin, m.sizeX(), m.sizeZ(), origin.getY(), origin.getY() + m.height() - 1);
            double floor = origin.getY()
                    + (m.startSlots().isEmpty() ? 0 : m.startSlots().get(0).y());
            MatchRuntime rt = registry()
                    .create(level, space, MatchOptions.generated(feet.getY()).withMap(map, floor));
            return rt.id() + " created on " + map + " at " + origin.toShortString() + "; importing";
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

    /**
     * Seats {@code player} in match {@code id} at {@code slot}, makes it the match's commander and tells it so; it
     * leaves any match it sat in.
     */
    private static String join(String id, int slot, ServerPlayer player) {
        MatchRuntime rt = byId(id);
        PlayerSlot seat = new PlayerSlot(slot);
        registry().join(rt, player.getUUID(), seat);
        StrategosServer.get().commanders().enter(player, rt);
        StrategosServer.get().net().joined(rt, player, seat);
        return player.getGameProfile().getName() + " sits in " + rt.id() + " at slot " + slot;
    }
}
