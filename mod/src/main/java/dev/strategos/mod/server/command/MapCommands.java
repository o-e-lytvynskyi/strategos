package dev.strategos.mod.server.command;

import static dev.strategos.mod.server.command.StrategosCommands.run;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.strategos.core.content.MapManifest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.content.MapPackage;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.match.MapExporter;
import dev.strategos.mod.server.match.MapImporter;
import dev.strategos.mod.server.match.MatchRefusedException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The {@code /strategos map} subtree: listing, importing and exporting map packages. */
final class MapCommands {
    private MapCommands() {}

    /** {@code map list|import|export}: map packages (MAP-02, MAP-03). */
    static LiteralArgumentBuilder<CommandSourceStack> map() {
        return Commands.literal("map")
                .then(Commands.literal("list").executes(ctx -> listMaps(ctx.getSource())))
                .then(Commands.literal("import")
                        .then(Commands.argument("map", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        MapPackageLoader.list(ctx.getSource().getServer()), builder))
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
                                                        BlockPosArgument.getLoadedBlockPos(ctx, "to")))))));
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
}
