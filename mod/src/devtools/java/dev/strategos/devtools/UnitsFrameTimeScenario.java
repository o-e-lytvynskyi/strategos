package dev.strategos.devtools;

import dev.strategos.mod.server.match.MatchRuntime;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * S4 (PERF-04.1): frame and client-tick times at {@code strategos.s4.count} bare humanoids under a camera 30 blocks up
 * at 50° pitch, render distance {@code strategos.s4.renderDistance}; first 600 frames with no units, then 600 with
 * them, so the units' share of the frame is (p50 with − p50 without) / p50 with.
 */
final class UnitsFrameTimeScenario implements Scenario {
    private static final int FRAMES = 600;
    private static final int SETTLE = 40;
    /** After the render distance changes, at least this many ticks and every section rendered before timing. */
    private static final int LOAD_MIN = 100;

    private enum Stage {
        LOADING,
        BASELINE,
        SPAWNING,
        SETTLING,
        UNITS,
        DONE
    }

    private final int count = Integer.getInteger("strategos.s4.count", 200);
    private final int renderDistance = Integer.getInteger("strategos.s4.renderDistance", 16);
    private final AtomicInteger bodies = new AtomicInteger();
    private volatile @Nullable MatchRuntime match;
    private @Nullable IntegratedServer server;
    private Stage stage = Stage.LOADING;
    private int settled;
    private int loading;
    private FrameTimer frames = new FrameTimer(FRAMES);
    /** From one frame's start to the next's, with no frame limit: CPU and GPU together, as the screen sees it. */
    private FrameTimer intervals = new FrameTimer(FRAMES);

    private FrameTimer ticks = new FrameTimer(FRAMES);
    private double baselineIntervalP50 = Double.NaN;
    private double baselineP50 = Double.NaN;

    @Override
    public String name() {
        return "s4_units";
    }

    @Override
    public void setup(IntegratedServer server, ScenarioContext ctx) {
        this.server = server;
        BlockPos anchor = ScenarioWorld.anchor(server);
        ScenarioWorld.camera(server, anchor.getX() + 0.5, anchor.getY() + 30, anchor.getZ() - 24.5, 0, 50);
        ctx.holdCamera(anchor.getX() + 0.5, anchor.getY() + 30, anchor.getZ() - 24.5, 0, 50);
    }

    @Override
    public void frameStart() {
        intervals.end();
        if (stage == Stage.BASELINE || stage == Stage.UNITS) {
            frames.start();
            intervals.start();
        }
    }

    @Override
    public void frameEnd() {
        frames.end();
    }

    @Override
    public void clientTickStart() {
        if (stage == Stage.UNITS) {
            ticks.start();
        }
    }

    @Override
    public void clientTickEnd() {
        ticks.end();
    }

    @Override
    public boolean tick(Minecraft mc, ScenarioContext ctx, int tick) {
        if (tick == 1) {
            ScenarioWorld.renderDistance(mc, renderDistance);
            mc.options.framerateLimit().set(260); // unlimited, so the interval measures the frame, not the cap
        }
        IntegratedServer s = server;
        switch (stage) {
            case LOADING -> {
                if (++loading >= LOAD_MIN && mc.levelRenderer.hasRenderedAllSections()) {
                    ctx.result.addProperty("loadTicks", loading);
                    stage = Stage.BASELINE;
                }
            }
            case BASELINE -> {
                if (frames.full()) {
                    baselineP50 = frames.ms(0.50);
                    baselineIntervalP50 = intervals.ms(0.50);
                    frames = new FrameTimer(FRAMES);
                    intervals = new FrameTimer(FRAMES);
                    if (s != null) {
                        s.execute(() -> match = ScenarioWorld.matchAtAnchor(s, 64, count, false));
                    }
                    stage = Stage.SPAWNING;
                }
            }
            case SPAWNING -> {
                MatchRuntime m = match;
                if (s != null && m != null) {
                    s.execute(() -> bodies.set(m.bodies().count()));
                }
                if (bodies.get() == count) {
                    stage = Stage.SETTLING;
                }
            }
            case SETTLING -> {
                if (++settled >= SETTLE) {
                    ctx.capture("s4-" + count + "-rd" + renderDistance);
                    stage = Stage.UNITS;
                }
            }
            case UNITS -> {
                if (frames.full()) {
                    double p50 = frames.ms(0.50);
                    ctx.result.addProperty("units", count);
                    ctx.result.addProperty("sodium", ModList.get().isLoaded("sodium"));
                    ctx.result.addProperty("renderDistance", renderDistance);
                    ctx.result.addProperty("frameBaselineP50Ms", baselineP50);
                    ctx.result.addProperty("frameP50Ms", p50);
                    ctx.result.addProperty("frameP99Ms", frames.ms(0.99));
                    ctx.result.addProperty("frameMaxMs", frames.maxMs());
                    ctx.result.addProperty("clientTickP50Ms", ticks.ms(0.50));
                    ctx.result.addProperty("clientTickP99Ms", ticks.ms(0.99));
                    ctx.result.addProperty("entityShare", (p50 - baselineP50) / p50);
                    double interval = intervals.ms(0.50);
                    ctx.result.addProperty("frameIntervalBaselineP50Ms", baselineIntervalP50);
                    ctx.result.addProperty("frameIntervalP50Ms", interval);
                    ctx.result.addProperty("frameIntervalP99Ms", intervals.ms(0.99));
                    ctx.result.addProperty("frameIntervalShare", (interval - baselineIntervalP50) / interval);
                    if (mc.player != null) {
                        ctx.result.addProperty(
                                "camera",
                                mc.player.getX() + " " + mc.player.getY() + " " + mc.player.getZ() + " "
                                        + mc.player.getYRot() + " " + mc.player.getXRot());
                    }
                    ctx.result.addProperty("ok", true);
                    stage = Stage.DONE;
                    return true;
                }
            }
            case DONE -> {
                return true;
            }
        }
        return false;
    }
}
