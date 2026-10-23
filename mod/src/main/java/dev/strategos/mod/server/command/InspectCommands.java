package dev.strategos.mod.server.command;

import static dev.strategos.mod.server.command.StrategosCommands.coreOf;
import static dev.strategos.mod.server.command.StrategosCommands.registry;
import static dev.strategos.mod.server.command.StrategosCommands.withMatch;
import static dev.strategos.mod.server.command.StrategosCommands.withMatchAtSource;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.Surface;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.nav.SurfacePage;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.mod.server.match.MatchRefusedException;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** The inspection subtrees of {@code /strategos}: navigation pages and paths, and unit stats. */
final class InspectCommands {
    private InspectCommands() {}

    /** {@code nav show|path|rebuild}: terrain pages and paths (PATH-02, PATH-03). */
    static LiteralArgumentBuilder<CommandSourceStack> nav() {
        return Commands.literal("nav")
                .then(Commands.literal("show")
                        .executes(ctx -> withMatchAtSource(ctx.getSource(), rt -> showPage(ctx.getSource(), rt)))
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
                                                ctx.getSource(), rt, IntegerArgumentType.getInteger(ctx, "handle"))))))
                .then(withMatch("rebuild", (source, rt) -> {
                    registry().rebuildPages(rt);
                    return rt.id() + ": every page captured again, published next tick";
                }));
    }

    /** {@code stats <handle> [path]}: a unit's values and their modifiers (DATA-02). */
    static LiteralArgumentBuilder<CommandSourceStack> stats() {
        return Commands.literal("stats")
                .then(Commands.argument("handle", IntegerArgumentType.integer())
                        .executes(ctx -> withMatchAtSource(
                                ctx.getSource(), rt -> showStats(rt, IntegerArgumentType.getInteger(ctx, "handle"))))
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> withMatchAtSource(
                                        ctx.getSource(),
                                        rt -> showStat(
                                                rt,
                                                IntegerArgumentType.getInteger(ctx, "handle"),
                                                StringArgumentType.getString(ctx, "path"))))));
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
}
