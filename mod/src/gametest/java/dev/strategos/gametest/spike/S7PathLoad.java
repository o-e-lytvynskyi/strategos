package dev.strategos.gametest.spike;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.sim.nav.DeliveryMode;
import dev.strategos.core.testkit.ProvingGround;
import dev.strategos.gametest.PathLoad;
import dev.strategos.mod.server.jobs.PathThreads;
import dev.strategos.mod.server.jobs.PooledJobExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spike S7 (spec §8.4, PATH-16): {@link PathLoad} on the spike server, with the mod's job pool, for each configuration
 * of delivery mode, path threads, {@code k}, W and the slow-host proxy: the main thread's path work a tick (p50, p99,
 * max) and its joins (p99), the group's result latency in ticks (p50, p95, max) and everyone's (p95), the results
 * joined before their job had finished, and the ticks until every unit had its answer. The slow-host proxy is one path
 * thread while twice as many busy threads as cores load the host. Writes {@code results.csv}, {@code results.md},
 * {@code env.json} and {@code DONE} to {@code out/s7}.
 */
final class S7PathLoad implements Spike {
    private static final Logger LOG = LoggerFactory.getLogger(S7PathLoad.class);
    private static final long MAIN_EXIT_NANOS = 2_000_000;
    private static final int GROUP_EXIT_TICKS = 5;
    private static final int BASE_W = 1_600_000;

    private record Config(String name, DeliveryMode mode, int threads, int k, int w, boolean slowHost) {}

    private final List<Config> configs = new ArrayList<>();
    private final List<PathLoad.Result> results = new ArrayList<>();
    private final PathLoad load = new PathLoad();
    private int next;

    S7PathLoad(MinecraftServer server) {
        for (DeliveryMode mode : DeliveryMode.values()) {
            String m = mode.id();
            for (int w : new int[] {200_000, 400_000, 800_000, 1_200_000, BASE_W, 2_400_000, 3_200_000}) {
                configs.add(new Config(m + " W " + w, mode, 4, 2, w, false));
            }
            for (int threads = 0; threads < 4; threads++) {
                configs.add(new Config(m + " threads " + threads, mode, threads, 2, BASE_W, false));
            }
            for (int k = 1; k <= 4; k++) {
                if (k != 2) {
                    configs.add(new Config(m + " k " + k, mode, 4, k, BASE_W, false));
                }
            }
            for (int w : new int[] {800_000, BASE_W}) {
                configs.add(new Config(m + " slow host W " + w, mode, 1, 2, w, true));
            }
        }
        LOG.info("S7: {} configurations on the {}² proving ground", configs.size(), ProvingGround.SIZE);
    }

    @Override
    public void preTick() {
        if (next < configs.size()) {
            Config config = configs.get(next++);
            PathLoad.Result result = run(config);
            results.add(result);
            LOG.info(
                    "S7 {}: main p99 {} ms, joins p99 {} ms, group p95 {} ticks, all answered in {} ticks",
                    config.name(),
                    ms(result.workP99()),
                    ms(result.joinP99()),
                    result.groupP95(),
                    result.ticks());
        }
    }

    @Override
    public boolean postTick() {
        return next >= configs.size();
    }

    @Override
    public void finish(boolean complete) {
        SpikeReport report = new SpikeReport(SpikeReport.dirFor("s7"));
        StringBuilder csv = new StringBuilder("config,mode,threads,k,w,slow_host,ticks,main_p50_ms,main_p99_ms,"
                + "main_max_ms,join_p99_ms,group_p50,group_p95,group_max,all_p95,unfinished,unanswered,expansions\n");
        StringBuilder md = new StringBuilder("# S7 path service under load\n\n| Config | main p50 ms | main p99 ms"
                + " | main max ms | join p99 ms | group p50 | group p95 | group max | all p95 | unfinished joins |"
                + " ticks to all | exits |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        JsonArray json = new JsonArray();
        for (int i = 0; i < results.size(); i++) {
            Config c = configs.get(i);
            PathLoad.Result r = results.get(i);
            boolean main = r.workP99() <= MAIN_EXIT_NANOS;
            boolean group = r.groupP95() <= GROUP_EXIT_TICKS;
            csv.append(String.join(
                            ",",
                            c.name(),
                            c.mode().id(),
                            String.valueOf(c.threads()),
                            String.valueOf(c.k()),
                            String.valueOf(c.w()),
                            String.valueOf(c.slowHost()),
                            String.valueOf(r.ticks()),
                            ms(r.workP50()),
                            ms(r.workP99()),
                            ms(r.workMax()),
                            ms(r.joinP99()),
                            String.valueOf(r.groupP50()),
                            String.valueOf(r.groupP95()),
                            String.valueOf(r.groupMax()),
                            String.valueOf(r.allP95()),
                            String.valueOf(r.stats().unfinished()),
                            String.valueOf(r.unanswered()),
                            String.valueOf(r.stats().expansions())))
                    .append('\n');
            md.append(String.format(
                    Locale.ROOT,
                    "| %s | %s | %s | %s | %s | %d | %d | %d | %d | %d | %d | %s |%n",
                    c.name(),
                    ms(r.workP50()),
                    ms(r.workP99()),
                    ms(r.workMax()),
                    ms(r.joinP99()),
                    r.groupP50(),
                    r.groupP95(),
                    r.groupMax(),
                    r.allP95(),
                    r.stats().unfinished(),
                    r.ticks(),
                    (main ? "main" : "-") + " " + (group ? "group" : "-")));
            JsonObject row = new JsonObject();
            row.addProperty("config", c.name());
            row.addProperty("mainP99Ns", r.workP99());
            row.addProperty("joinP99Ns", r.joinP99());
            row.addProperty("groupP95Ticks", r.groupP95());
            row.addProperty("ticks", r.ticks());
            json.add(row);
        }
        report.writeText("results.csv", csv.toString());
        report.writeText("results.md", md.toString());
        report.writeText(
                "results.json", new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
        report.writeEnv();
        if (complete) {
            report.done();
        }
    }

    private PathLoad.Result run(Config config) {
        JobExecutor jobs = PathThreads.executor(config.threads());
        List<Thread> burners = config.slowHost() ? burn() : List.of();
        try {
            return load.run(PathLoad.rules(config.mode(), config.k(), config.w()), jobs);
        } finally {
            for (Thread burner : burners) {
                burner.interrupt();
            }
            if (jobs instanceof PooledJobExecutor pool) {
                pool.close();
            }
        }
    }

    /** Twice as many busy threads as cores, for the slow-host proxy. */
    private static List<Thread> burn() {
        List<Thread> burners = new ArrayList<>();
        int n = 2 * Runtime.getRuntime().availableProcessors();
        for (int k = 0; k < n; k++) {
            Thread burner = new Thread(
                    () -> {
                        long x = 1;
                        while (!Thread.currentThread().isInterrupted()) {
                            x = x * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L;
                        }
                        if (x == 42) {
                            LOG.trace("unlikely");
                        }
                    },
                    "s7-burner-" + k);
            burner.setDaemon(true);
            burner.start();
            burners.add(burner);
        }
        return burners;
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1e6);
    }
}
