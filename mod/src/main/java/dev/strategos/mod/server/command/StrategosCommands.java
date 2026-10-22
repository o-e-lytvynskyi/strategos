package dev.strategos.mod.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.JournalExport;
import dev.strategos.core.sim.journal.JournalFormat;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackage;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.match.MapExporter;
import dev.strategos.mod.server.match.MapImporter;
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
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
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
                                                            ServerPlayer player =
                                                                    EntityArgument.getPlayer(ctx, "player");
                                                            return run(
                                                                    ctx.getSource(),
                                                                    () -> join(
                                                                            StringArgumentType.getString(ctx, "id"),
                                                                            IntegerArgumentType.getInteger(ctx, "slot"),
                                                                            player));
                                                        }))))))
                .then(Commands.literal("map")
                        .then(Commands.literal("list").executes(ctx -> listMaps(ctx.getSource())))
                        .then(Commands.literal("import")
                                .then(Commands.argument("map", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                MapPackageLoader.list(
                                                        ctx.getSource().getServer()),
                                                builder))
                                        .then(Commands.argument("at", BlockPosArgument.blockPos())
                                                .executes(ctx -> importMap(
                                                        ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "map"),
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "at"))))))
                        .then(Commands.literal("export")
                                .then(Commands.argument("map", ResourceLocationArgument.id())
                                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                        .executes(ctx -> exportMap(
                                                                ctx.getSource(),
                                                                ResourceLocationArgument.getId(ctx, "map"),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                                                BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))))
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
                .then(Commands.literal("nav")
                        .then(Commands.literal("show")
                                .executes(
                                        ctx -> withMatchAtSource(ctx.getSource(), rt -> showPage(ctx.getSource(), rt)))
                                .then(Commands.argument("pageX", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("pageZ", IntegerArgumentType.integer(0))
                                                .executes(ctx -> withMatchAtSource(
                                                        ctx.getSource(),
                                                        rt -> showPage(
                                                                rt,
                                                                IntegerArgumentType.getInteger(ctx, "pageX"),
                                                                IntegerArgumentType.getInteger(ctx, "pageZ")))))))
                        .then(Commands.literal("path")
                                .then(Commands.argument("handle", IntegerArgumentType.integer())
                                        .executes(ctx -> withMatchAtSource(
                                                ctx.getSource(),
                                                rt -> showPath(
                                                        ctx.getSource(),
                                                        rt,
                                                        IntegerArgumentType.getInteger(ctx, "handle"))))))
                        .then(withMatch("rebuild", (source, rt) -> {
                            registry().rebuildPages(rt);
                            return rt.id() + ": every page captured again, published next tick";
                        })))
                .then(Commands.literal("stats")
                        .then(Commands.argument("handle", IntegerArgumentType.integer())
                                .executes(ctx -> withMatchAtSource(
                                        ctx.getSource(),
                                        rt -> showStats(rt, IntegerArgumentType.getInteger(ctx, "handle"))))
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(ctx -> withMatchAtSource(
                                                ctx.getSource(),
                                                rt -> showStat(
                                                        rt,
                                                        IntegerArgumentType.getInteger(ctx, "handle"),
                                                        StringArgumentType.getString(ctx, "path")))))))
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

    private static int listMaps(CommandSourceStack source) {
        List<ResourceLocation> maps = MapPackageLoader.list(source.getServer());
        String line = maps.isEmpty() ? "no map packages" : "maps: " + maps;
        source.sendSuccess(() -> Component.literal(line), false);
        return maps.size();
    }

    /** A map imported at {@code at}, outside any match; it finishes over the next ticks. */
    private static int importMap(CommandSourceStack source, ResourceLocation map, BlockPos at) {
        return run(source, () -> {
            MapPackage pkg;
            try {
                pkg = MapPackageLoader.load(source.getServer(), map);
            } catch (IllegalArgumentException e) {
                throw new MatchRefusedException(String.valueOf(e.getMessage()));
            }
            StrategosServer server = StrategosServer.get();
            server.mapImports().start(new MapImporter(source.getLevel(), pkg, at, 0, server.writer()));
            return "importing " + map + " at " + at.toShortString();
        });
    }

    private static int exportMap(CommandSourceStack source, ResourceLocation map, BlockPos from, BlockPos to) {
        return run(source, () -> {
            MapManifest m = MapExporter.export(source.getLevel(), map, from, to);
            return "exported " + map + ": " + m.sizeX() + " x " + m.height() + " x " + m.sizeZ() + ", "
                    + m.tilesX() * m.tilesZ() + " tiles, terrain "
                    + m.terrainHash().substring(0, 12);
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

    /** {@code nav show}: the page under the source. */
    private static String showPage(CommandSourceStack source, MatchRuntime rt) {
        Vec3 at = source.getPosition();
        int localX = (int) Math.floor(rt.space().localX(at.x));
        int localZ = (int) Math.floor(rt.space().localZ(at.z));
        return showPage(rt, Math.max(0, localX) >> 4, Math.max(0, localZ) >> 4);
    }

    /** {@code nav show <pageX> <pageZ>}: a page's surfaces, overflow and hashes, and the path service's counts. */
    private static String showPage(MatchRuntime rt, int pageX, int pageZ) {
        MatchQueries q = coreOf(rt).queries();
        SurfaceGrid grid = q.surfaces();
        if (pageX >= grid.pagesX() || pageZ >= grid.pagesZ()) {
            throw new MatchRefusedException(
                    "page " + pageX + ", " + pageZ + " is outside " + grid.pagesX() + " x " + grid.pagesZ());
        }
        SurfacePage page = grid.page(pageX, pageZ);
        int columns = 0;
        int passable = 0;
        for (int column = 0; column < SurfacePage.COLUMNS; column++) {
            columns += page.count(column) > 0 ? 1 : 0;
            for (int layer = 0; layer < page.count(column); layer++) {
                passable += Surface.passable(page.surface(column, layer)) ? 1 : 0;
            }
        }
        String slice = q.pageRefs().stream()
                .filter(ref -> ref.x() == pageX && ref.z() == pageZ)
                .map(ref -> Hex64.format(ref.slice()))
                .findFirst()
                .orElse("none (not published)");
        PathService.Stats paths = q.pathStats();
        return rt.id() + " page " + pageX + ", " + pageZ + ": " + page.surfaceCount() + " surfaces (" + passable
                + " passable) in " + columns + " of 256 columns, overflow " + page.overflow() + ", page hash "
                + Hex64.format(page.hash()) + ", slice " + slice + "; grid version " + grid.version() + "; paths: "
                + paths.requests() + " asked, " + paths.decided() + " at once, " + paths.ok() + " ok, "
                + paths.partial() + " partial, " + paths.noPath() + " none, " + paths.superseded() + " superseded, "
                + paths.inFlight() + " in flight";
    }

    /** {@code nav path <handle>}: a unit's path in world coordinates, marked with particles for a few seconds. */
    private static String showPath(CommandSourceStack source, MatchRuntime rt, int handle) {
        MatchQueries q = coreOf(rt).queries();
        PathStore.UnitPath path = q.path(handle);
        UnitView unit = q.unit(handle);
        if (path == null || unit == null) {
            throw new MatchRefusedException(rt.id() + " has no unit " + Handle.toString(handle));
        }
        PathStore.Waypoints w = path.waypoints();
        MatchSpace space = rt.space();
        StringBuilder line = new StringBuilder(
                rt.id() + " " + Handle.toString(handle) + ": " + unit.state().id()
                        + ", path " + path.status().id() + ", waypoint " + path.cursor() + " of " + w.count()
                        + (w.truncated() ? " (truncated)" : "")
                        + (path.request() != 0 ? ", waiting for request " + path.request() : ""));
        ServerLevel level = source.getLevel();
        for (int i = 0; i < w.count(); i++) {
            double x = space.worldX(w.x(i));
            double y = space.minY() + w.floor(i) / 16.0;
            double z = space.worldZ(w.z(i));
            if (i < 8) {
                line.append(i == 0 ? ": " : " ").append(String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", x, y, z));
            }
            level.sendParticles(
                    i < path.cursor() ? ParticleTypes.SMOKE : ParticleTypes.END_ROD,
                    x,
                    y + 0.2,
                    z,
                    4,
                    0.1,
                    0.1,
                    0.1,
                    0);
        }
        if (w.count() > 8) {
            line.append(" …");
        }
        return line.toString();
    }

    /** Seats {@code player} in match {@code id} at {@code slot} and tells it so; it leaves any match it sat in. */
    private static String join(String id, int slot, ServerPlayer player) {
        MatchRuntime rt = byId(id);
        PlayerSlot seat = new PlayerSlot(slot);
        registry().join(rt, player.getUUID(), seat);
        StrategosServer.get().net().joined(rt, player, seat);
        return player.getGameProfile().getName() + " sits in " + rt.id() + " at slot " + slot;
    }

    /** Every value a unit has, after modifiers. */
    private static String showStats(MatchRuntime rt, int handle) {
        MatchQueries q = coreOf(rt).queries();
        UnitView unit = q.unit(handle);
        if (unit == null) {
            throw new MatchRefusedException(rt.id() + " has no unit " + Handle.toString(handle));
        }
        StringBuilder line = new StringBuilder(rt.id() + " " + Handle.toString(handle) + " " + unit.template() + ":");
        for (ValuePath p : q.templates().valuePaths().all()) {
            StatBreakdown b = q.stats(handle, p.path());
            if (b != null && !Double.isNaN(b.result())) {
                line.append(' ').append(p.path()).append(' ').append(number(b.result()));
                if (!b.modifiers().isEmpty()) {
                    line.append(" (").append(b.modifiers().size()).append(" modifiers)");
                }
            }
        }
        return line.toString();
    }

    /** One value's base, the modifiers that make it, and the result. */
    private static String showStat(MatchRuntime rt, int handle, String path) {
        MatchQueries q = coreOf(rt).queries();
        if (q.unit(handle) == null) {
            throw new MatchRefusedException(rt.id() + " has no unit " + Handle.toString(handle));
        }
        StatBreakdown b = q.stats(handle, path);
        if (b == null) {
            throw new MatchRefusedException("no value path '" + path + "'; /strategos stats " + handle + " lists them");
        }
        StringBuilder line =
                new StringBuilder(rt.id() + " " + Handle.toString(handle) + " " + path + ": base " + number(b.base()));
        for (Modifier m : b.modifiers()) {
            line.append(", ")
                    .append(m.op().name().toLowerCase(Locale.ROOT))
                    .append(' ')
                    .append(number(m.value()))
                    .append(" (source ")
                    .append(m.sourceId())
                    .append(", stage ")
                    .append(m.stage())
                    .append(')');
        }
        return line.append(", result ").append(number(b.result())).toString();
    }

    /** A value as data writes it: {@code 45}, {@code 0.1875}. */
    private static String number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v);
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
