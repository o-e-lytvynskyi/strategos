package dev.strategos.devtools;

import com.google.gson.JsonArray;
import com.mojang.logging.LogUtils;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.devtools.demo.DemoCatalog;
import dev.strategos.devtools.demo.DemoChapter;
import dev.strategos.devtools.demo.DemoDirector;
import dev.strategos.devtools.demo.DemoScript;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The demo (devtools/demo): the chapters {@code -Pdemo} picks, less those {@code -Pdemo.skip} names, shown one after
 * another in RTS mode, for the owner to watch. The server runs the show ({@link DemoDirector}); this side keeps RTS
 * mode on, entered anew in each chapter's match so the camera's bounds are that match's, and turns the camera where
 * the script asks. {@code -Pdemo=list} only lists the chapters. A chapter that fails is listed in the result and the
 * show goes on; the run is ok when none failed.
 */
final class DemoScenario implements Scenario {
    private static final Logger LOG = LogUtils.getLogger();

    private final List<DemoChapter> chapters;
    private final boolean list;
    private volatile @Nullable DemoDirector director;
    private long applied = -1;
    private long matchSeen = Long.MIN_VALUE;
    private int rested;
    /** {@code -Pdemo.shots}: a frame two seconds after each line, {@code demo-NN.png}, to check the show without a viewer. */
    private final boolean shots = Boolean.getBoolean("strategos.demo.shots");

    private long said;
    private int sinceSaid = -1;

    DemoScenario() {
        String select = System.getProperty("strategos.demo", "all");
        list = select.trim().equals("list");
        chapters = list
                ? DemoCatalog.CHAPTERS
                : DemoCatalog.pick(
                        DemoCatalog.CHAPTERS,
                        select,
                        System.getProperty("strategos.demo.skip", ""),
                        DemoCatalog.seen(DemoCatalog.seenFile()));
    }

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        ScenarioWorld.run(server, "gamerule sendCommandFeedback false");
        ScenarioWorld.run(server, "gamerule doDaylightCycle false");
        ScenarioWorld.run(server, "gamerule doWeatherCycle false");
        ScenarioWorld.run(server, "time set 6000");
        ScenarioWorld.run(server, "weather clear");
        if (list || chapters.isEmpty()) {
            return;
        }
        LOG.info("DEMO chapters: {}", chapters.stream().map(DemoChapter::id).toList());
        DemoDirector d = new DemoDirector(server, ScenarioWorld.player(server), chapters);
        d.start();
        director = d;
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        if (list || chapters.isEmpty()) {
            JsonArray lines = new JsonArray();
            DemoCatalog.describe(list ? chapters : DemoCatalog.CHAPTERS).forEach(lines::add);
            LOG.info("DEMO catalog:\n{}", String.join("\n", DemoCatalog.describe(DemoCatalog.CHAPTERS)));
            ctx.result.add("catalog", lines);
            ctx.result.addProperty("ok", true);
            return true;
        }
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, 12);
        }
        DemoDirector d = director;
        if (d == null) {
            return false;
        }
        rts();
        shoot(ctx);
        if (!d.finished()) {
            return false;
        }
        if (++rested < 40) { // the last line's fade
            return false;
        }
        JsonArray shown = new JsonArray();
        d.shown().forEach(shown::add);
        JsonArray failed = new JsonArray();
        d.failures().forEach(failed::add);
        ctx.result.add("shown", shown);
        ctx.result.add("failures", failed);
        ctx.result.addProperty("ok", d.failures().isEmpty());
        return true;
    }

    private void shoot(ScenarioContext ctx) {
        long says = DemoDirector.says();
        if (!shots) {
            return;
        }
        if (says != said) {
            said = says;
            sinceSaid = 0;
        } else if (sinceSaid >= 0 && ++sinceSaid == 40) {
            ctx.capture(String.format(java.util.Locale.ROOT, "demo-%02d", said));
            sinceSaid = -1;
        }
    }

    /** RTS mode on in the current match, entered anew when the match changes, and the camera where the script says. */
    private void rts() {
        RtsMode rts = StrategosClient.rts();
        MatchJoin join = StrategosClient.match();
        long id = join == null ? Long.MIN_VALUE : join.matchId();
        if (id != matchSeen) {
            matchSeen = id;
            if (rts.active()) {
                rts.leave();
            }
            applied = -1;
            return;
        }
        if (!rts.active()) {
            rts.enter();
            return;
        }
        CameraRig rig = rts.rig();
        DemoScript.Look look = DemoDirector.look();
        long seq = DemoDirector.lookSeq();
        if (rig != null && look != null && seq != applied) {
            applied = seq;
            rts.centreOn(look.x(), look.z());
            rig.zoomTo(look.distance() * Math.sin(Math.toRadians(CameraRig.DEFAULT_PITCH)));
        }
    }
}
