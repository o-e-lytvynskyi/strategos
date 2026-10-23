package dev.strategos.mod.server.command;

import static dev.strategos.mod.server.command.StrategosCommands.send;
import static dev.strategos.mod.server.command.StrategosCommands.template;
import static dev.strategos.mod.server.command.StrategosCommands.withMatchAtSource;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.mod.server.match.MatchSpace;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.server.level.ColumnPos;

/**
 * {@code /strategos build place <template> <pos> [rotation] [faction]} (BLD-01): a finished building whose blueprint's
 * corner stands at the column, through the command path, for scenarios and a sandbox; players place theirs through
 * placement (BLD-02).
 */
final class BuildCommands {
    private BuildCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("build")
                .then(Commands.literal("place")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                                        .executes(ctx -> place(ctx, 0, 0))
                                        .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                                .executes(ctx ->
                                                        place(ctx, IntegerArgumentType.getInteger(ctx, "rotation"), 0))
                                                .then(Commands.argument(
                                                                "faction",
                                                                IntegerArgumentType.integer(0, FactionSlot.MAX))
                                                        .executes(ctx -> place(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "rotation"),
                                                                IntegerArgumentType.getInteger(ctx, "faction"))))))));
    }

    private static int place(CommandContext<CommandSourceStack> ctx, int rotation, int faction) {
        CommandSourceStack source = ctx.getSource();
        ColumnPos column = ColumnPosArgument.getColumnPos(ctx, "pos");
        return withMatchAtSource(source, rt -> {
            MatchSpace space = rt.space();
            send(
                    source,
                    rt,
                    new SpawnBuilding(
                            template(ctx),
                            new FactionSlot(faction),
                            column.x() - space.origin().getX(),
                            column.z() - space.origin().getZ(),
                            rotation));
            return null;
        });
    }
}
