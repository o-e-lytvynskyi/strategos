package dev.strategos.mod.server.command;

import static dev.strategos.mod.server.command.StrategosCommands.coreOf;
import static dev.strategos.mod.server.command.StrategosCommands.send;
import static dev.strategos.mod.server.command.StrategosCommands.template;
import static dev.strategos.mod.server.command.StrategosCommands.withMatch;
import static dev.strategos.mod.server.command.StrategosCommands.withMatchAtSource;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.strategos.core.content.MapWaypoint;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.order.ContextResolver;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.mod.server.content.MapPackageLoader;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ColumnPos;
import org.jspecify.annotations.Nullable;

/** The unit subtrees of {@code /strategos}: spawning, the debug orders and {@code order move|stop} (ORD-02). */
final class UnitCommands {
    private UnitCommands() {}

    /** {@code spawn <template> [count] [slot]} (DATA-01). */
    static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn")
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
                                                IntegerArgumentType.getInteger(ctx, "slot"))))));
    }

    /** {@code debug spawn|move|stop|replay}: M0's debug orders and the journal replay. */
    static LiteralArgumentBuilder<CommandSourceStack> debug() {
        return Commands.literal("debug")
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawn(ctx.getSource(), 1, 0))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, UnitSet.MAX_SIZE))
                                .executes(
                                        ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), 0))
                                .then(Commands.argument("faction", IntegerArgumentType.integer(0, FactionSlot.MAX))
                                        .executes(ctx -> spawn(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count"),
                                                IntegerArgumentType.getInteger(ctx, "faction"))))))
                .then(Commands.literal("move")
                        .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                                .executes(ctx -> move(ctx, null))
                                .then(Commands.argument("handle", IntegerArgumentType.integer())
                                        .executes(ctx -> move(ctx, IntegerArgumentType.getInteger(ctx, "handle"))))))
                .then(Commands.literal("stop")
                        .executes(ctx -> stop(ctx.getSource(), null))
                        .then(Commands.argument("handle", IntegerArgumentType.integer())
                                .executes(ctx -> stop(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "handle")))))
                .then(withMatch("replay", UnitCommands::replay));
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

    /** Which units an order command names, resolved in the match at the source. */
    private interface Units {
        int[] of(CommandContext<CommandSourceStack> ctx, MatchRuntime rt);
    }

    /** Where an order command sends its units, in match coordinates. */
    private interface Target {
        Vec2d of(CommandContext<CommandSourceStack> ctx, MatchRuntime rt);
    }

    /**
     * {@code order move <units> <x> <z> [replace|queue|front]}, {@code order move <units> waypoint <name> [mode]} and
     * {@code order stop <units>} (ORD-02), {@code <units>} being {@code @units}, {@code @group <id>} or one handle: for
     * the match at the source, as ADMIN, through the context of a right-click on the ground.
     */
    static LiteralArgumentBuilder<CommandSourceStack> order() {
        Units all = (ctx, rt) -> coreOf(rt).queries().unitHandles();
        Units group = (ctx, rt) -> group(rt, IntegerArgumentType.getInteger(ctx, "group"));
        Units one = (ctx, rt) -> new int[] {IntegerArgumentType.getInteger(ctx, "handle")};
        return Commands.literal("order")
                .then(Commands.literal("move")
                        .then(moveTargets(Commands.literal("@units"), all))
                        .then(Commands.literal("@group")
                                .then(moveTargets(Commands.argument("group", IntegerArgumentType.integer(1)), group)))
                        .then(moveTargets(Commands.argument("handle", IntegerArgumentType.integer()), one)))
                .then(Commands.literal("stop")
                        .then(Commands.literal("@units").executes(ctx -> orderStop(ctx, all)))
                        .then(Commands.literal("@group")
                                .then(Commands.argument("group", IntegerArgumentType.integer(1))
                                        .executes(ctx -> orderStop(ctx, group))))
                        .then(Commands.argument("handle", IntegerArgumentType.integer())
                                .executes(ctx -> orderStop(ctx, one))));
    }

    /** {@code <x> <z> [mode]}, a world column, or {@code waypoint <name> [mode]}, a waypoint of the match's map. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T moveTargets(T units, Units which) {
        Target column = (ctx, rt) -> {
            ColumnPos pos = ColumnPosArgument.getColumnPos(ctx, "pos");
            return new Vec2d(rt.space().localX(pos.x() + 0.5), rt.space().localZ(pos.z() + 0.5));
        };
        Target waypoint = (ctx, rt) -> waypoint(ctx.getSource(), rt, StringArgumentType.getString(ctx, "name"));
        return units.then(withModes(Commands.argument("pos", ColumnPosArgument.columnPos()), which, column))
                .then(Commands.literal("waypoint")
                        .then(withModes(Commands.argument("name", StringArgumentType.word()), which, waypoint)));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withModes(T node, Units which, Target where) {
        return node.executes(ctx -> orderMove(ctx, which, where, QueueMode.REPLACE))
                .then(Commands.literal("replace").executes(ctx -> orderMove(ctx, which, where, QueueMode.REPLACE)))
                .then(Commands.literal("queue").executes(ctx -> orderMove(ctx, which, where, QueueMode.QUEUE)))
                .then(Commands.literal("front").executes(ctx -> orderMove(ctx, which, where, QueueMode.PUSH_FRONT)));
    }

    /** One command per {@value UnitSet#MAX_SIZE} units, so each such batch is one group. */
    private static int orderMove(CommandContext<CommandSourceStack> ctx, Units which, Target where, QueueMode mode) {
        return withMatchAtSource(ctx.getSource(), rt -> {
            int[] units = which.of(ctx, rt);
            if (units.length == 0) {
                throw new MatchRefusedException("no such unit in " + rt.id());
            }
            Vec2d target = where.of(ctx, rt);
            for (int from = 0; from < units.length; from += UnitSet.MAX_SIZE) {
                int[] part = Arrays.copyOfRange(units, from, Math.min(units.length, from + UnitSet.MAX_SIZE));
                send(ctx.getSource(), rt, ContextResolver.ground(UnitSet.of(part), target, mode));
            }
            return null;
        });
    }

    private static int orderStop(CommandContext<CommandSourceStack> ctx, Units which) {
        return withMatchAtSource(ctx.getSource(), rt -> {
            int[] units = which.of(ctx, rt);
            if (units.length == 0) {
                throw new MatchRefusedException("no such unit in " + rt.id());
            }
            for (int from = 0; from < units.length; from += UnitSet.MAX_SIZE) {
                int[] part = Arrays.copyOfRange(units, from, Math.min(units.length, from + UnitSet.MAX_SIZE));
                send(ctx.getSource(), rt, new Stop(UnitSet.of(part)));
            }
            return null;
        });
    }

    /** The units whose current order belongs to group {@code id}. */
    private static int[] group(MatchRuntime rt, int id) {
        MatchQueries q = coreOf(rt).queries();
        return Arrays.stream(q.unitHandles())
                .filter(u -> {
                    List<OrderQueues.Order> orders = q.orders(u);
                    return !orders.isEmpty() && orders.get(0).group() == id;
                })
                .toArray();
    }

    /** A waypoint of the match's map, at its column's centre in match coordinates. */
    private static Vec2d waypoint(CommandSourceStack source, MatchRuntime rt, String name) {
        ResourceLocation map = rt.map();
        if (map == null) {
            throw new MatchRefusedException(rt.id() + " was not made from a map, so it has no waypoints");
        }
        MapWaypoint w = MapPackageLoader.load(source.getServer(), map)
                .manifest()
                .waypoints()
                .get(name);
        if (w == null) {
            throw new MatchRefusedException(map + " has no waypoint '" + name + "'");
        }
        return new Vec2d(w.x() + 0.5, w.z() + 0.5);
    }
}
