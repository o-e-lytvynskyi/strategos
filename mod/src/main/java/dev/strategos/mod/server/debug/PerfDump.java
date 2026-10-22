package dev.strategos.mod.server.debug;

import dev.strategos.core.diag.PhaseTimers;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * {@code /strategos perf start|stop|dump} (04 §15.3): a match's metrics, one row per step from {@code start} to {@code
 * stop} (at most {@value #MAX_ROWS}, half an hour), which {@code dump} writes as CSV under {@code
 * <world>/strategos/perf/} for the assistant to read. A row holds the match's tick and units, the step's time in each
 * phase, the step's, the adapter's and their total, in nanoseconds, and the path service's queue with its counts and
 * main-thread join time in that tick. Only the server thread touches it.
 */
public final class PerfDump {
    public static final int MAX_ROWS = 36_000;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final Map<MatchId, Recording> recordings = new LinkedHashMap<>();

    private static final class Recording {
        final List<String> phases;
        final List<long[]> rows = new ArrayList<>();
        boolean on = true;
        long lastTick;
        PathService.Stats last;

        Recording(List<String> phases, long tick, PathService.Stats stats) {
            this.phases = phases;
            this.lastTick = tick;
            this.last = stats;
        }
    }

    public PerfDump(MinecraftServer server, Supplier<MatchRegistry> matches) {
        this.server = server;
        this.matches = matches;
    }

    /** Starts recording {@code rt}'s steps afresh; what an earlier recording held is dropped. */
    public String start(MatchRuntime rt) {
        MatchCore core = core(rt);
        MatchQueries q = core.queries();
        recordings.put(rt.id(), new Recording(core.timers().phaseNames(), q.tick(), q.pathStats()));
        return rt.id() + ": recording perf from tick " + q.tick();
    }

    /** Stops recording {@code rt}; its rows stay for {@link #dump}. */
    public String stop(MatchRuntime rt) {
        Recording r = recording(rt);
        r.on = false;
        return rt.id() + ": perf recording stopped, " + r.rows.size() + " ticks recorded";
    }

    /** Writes what {@code rt}'s recording holds as {@code m<id>-t<first>-t<last>.csv}; returns the file. */
    public Path dump(MatchRuntime rt) throws IOException {
        Recording r = recording(rt);
        if (r.rows.isEmpty()) {
            throw new IllegalStateException(rt.id() + " has recorded no step yet");
        }
        StringBuilder csv = new StringBuilder(header(r.phases)).append('\n');
        for (long[] row : r.rows) {
            for (int i = 0; i < row.length; i++) {
                csv.append(i == 0 ? "" : ",").append(row[i]);
            }
            csv.append('\n');
        }
        Path file = server.getWorldPath(LevelResource.ROOT)
                .resolve("strategos")
                .resolve("perf")
                .resolve(rt.id() + "-t" + r.rows.get(0)[0] + "-t" + r.rows.get(r.rows.size() - 1)[0] + ".csv");
        Files.createDirectories(file.getParent());
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        return file;
    }

    /** The CSV's first line. */
    static String header(List<String> phases) {
        StringBuilder h = new StringBuilder("tick,units,step_ns,adapter_ns,total_ns");
        for (String phase : phases) {
            h.append(',').append(phase.toLowerCase(Locale.ROOT)).append("_ns");
        }
        return h.append(",path_queue,path_requests,path_decided,path_ok,path_partial,path_no_path,path_superseded")
                .append(",path_expansions,path_join_ns")
                .toString();
    }

    /** Once a server tick, after the matches stepped: a row for each recording whose match stepped. */
    public void tick() {
        recordings.entrySet().removeIf(e -> matches.get().get(e.getKey()) == null); // disposed
        for (Map.Entry<MatchId, Recording> e : recordings.entrySet()) {
            Recording r = e.getValue();
            MatchRuntime rt = matches.get().get(e.getKey());
            MatchCore core = rt == null ? null : rt.core();
            if (!r.on || core == null || core.queries().tick() == r.lastTick) {
                continue;
            }
            r.rows.add(row(core, r.last));
            r.lastTick = core.queries().tick();
            r.last = core.queries().pathStats();
            if (r.rows.size() >= MAX_ROWS) {
                r.on = false;
            }
        }
    }

    /** The last step's row; the path counts are those of this tick, against {@code before}. */
    static long[] row(MatchCore core, PathService.Stats before) {
        MatchQueries q = core.queries();
        PhaseTimers timers = core.timers();
        long[] phases = timers.lastPhaseNanos();
        PathService.Stats s = q.pathStats();
        long[] row = new long[5 + phases.length + 9];
        int i = 0;
        row[i++] = q.tick();
        row[i++] = q.unitCount();
        row[i++] = timers.lastStepNanos();
        row[i++] = timers.lastAdapterNanos();
        row[i++] = timers.lastTotalNanos();
        for (long phase : phases) {
            row[i++] = phase;
        }
        row[i++] = s.inFlight();
        row[i++] = s.requests() - before.requests();
        row[i++] = s.decided() - before.decided();
        row[i++] = s.ok() - before.ok();
        row[i++] = s.partial() - before.partial();
        row[i++] = s.noPath() - before.noPath();
        row[i++] = s.superseded() - before.superseded();
        row[i++] = s.expansions() - before.expansions();
        row[i] = s.joinNanos() - before.joinNanos();
        return row;
    }

    private Recording recording(MatchRuntime rt) {
        Recording r = recordings.get(rt.id());
        if (r == null) {
            throw new IllegalStateException(rt.id() + " is not recorded; /strategos perf start first");
        }
        return r;
    }

    private static MatchCore core(MatchRuntime rt) {
        MatchCore core = rt.core();
        if (core == null) {
            throw new IllegalStateException(rt.id() + " is " + rt.lifecycle() + " and has no core");
        }
        return core;
    }
}
