package dev.strategos.devtools.demo;

import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Runs the chosen chapters one after another, from the server's tick: each on the next plot, the player moved there
 * first so its chunks load, then its script a step at a time; its match is disposed after it, failed or not. The log
 * carries {@code DEMO start}, {@code DEMO say} and {@code DEMO end} lines for a narrator. The client side reads
 * {@link #look()} for the camera and {@link #finished()} for the end.
 */
public final class DemoDirector {
    private static final Logger LOG = LogUtils.getLogger();
    /** Ticks on a new plot before its setup: the player's chunks there load and send. */
    private static final int ARRIVE = 60;

    private static volatile DemoScript.@Nullable Look look;
    private static final AtomicLong LOOKS = new AtomicLong();
    private static final AtomicLong SAYS = new AtomicLong();

    private final MinecraftServer server;
    private final ServerPlayer player;
    private final List<DemoChapter> chapters;
    private final List<String> failures = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private int next;
    private @Nullable DemoChapter chapter;
    private @Nullable DemoStage stage;
    private List<DemoScript.Step> steps = List.of();
    private int step;
    private int inStep;
    private int arriving;
    private volatile boolean finished;

    public DemoDirector(MinecraftServer server, ServerPlayer player, List<DemoChapter> chapters) {
        this.server = server;
        this.player = player;
        this.chapters = List.copyOf(chapters);
    }

    /** Starts the show: it runs off the server's ticks. */
    public void start() {
        player.setGameMode(GameType.SPECTATOR);
        NeoForge.EVENT_BUS.addListener(this::onTick);
    }

    public boolean finished() {
        return finished;
    }

    public List<String> failures() {
        return List.copyOf(failures);
    }

    public List<String> shown() {
        return List.copyOf(shown);
    }

    /** The camera the script asks for last, and how many it asked for so far. */
    public static DemoScript.@Nullable Look look() {
        return look;
    }

    public static long lookSeq() {
        return LOOKS.get();
    }

    /** How many lines the show has said so far. */
    public static long says() {
        return SAYS.get();
    }

    static void look(DemoScript.Look l) {
        look = l;
        LOOKS.incrementAndGet();
    }

    /** A line under the screen's middle, and in the log. */
    static void say(DemoStage stage, String line) {
        LOG.info("DEMO say {}", line);
        SAYS.incrementAndGet();
        stage.command("title @a times 5 80 15");
        stage.command("title @a subtitle " + new JsonPrimitive(line));
        stage.command("title @a title \"\"");
    }

    private void onTick(ServerTickEvent.Post event) {
        if (finished) {
            return;
        }
        if (chapter == null) {
            begin();
            return;
        }
        DemoStage s = stage;
        if (s == null) {
            return;
        }
        if (arriving > 0) {
            arriving--;
            return;
        }
        try {
            if (step >= steps.size()) {
                end(null);
            } else if (steps.get(step).tick(s, inStep++)) {
                step++;
                inStep = 0;
            }
        } catch (RuntimeException e) {
            LOG.warn("DEMO chapter {} failed; {}", chapter.id(), describe(s), e);
            end(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static String describe(DemoStage s) {
        try {
            return s.describe();
        } catch (RuntimeException e) {
            return "cannot describe: " + e;
        }
    }

    private void begin() {
        if (next >= chapters.size()) {
            finished = true;
            LOG.info("DEMO finished: {} shown, {} failed", shown.size(), failures.size());
            return;
        }
        DemoChapter c = chapters.get(next);
        DemoStage s = new DemoStage(server, player, next);
        next++;
        chapter = c;
        stage = s;
        DemoScript script = new DemoScript();
        c.script(script);
        steps = script.steps;
        step = 0;
        inStep = 0;
        arriving = ARRIVE;
        LOG.info("DEMO start {} {}/{}: {}", c.id(), next, chapters.size(), c.title());
        s.command("title @a times 10 60 20");
        s.command("title @a subtitle " + new JsonPrimitive(next + " / " + chapters.size()));
        s.command("title @a title " + new JsonPrimitive(c.title()));
        player.teleportTo(
                s.level(), s.centre().getX() + 0.5, s.floor() + 40, s.centre().getZ() - 30.5, 0, 50);
        look(new DemoScript.Look(s.centre().getX() + 0.5, s.centre().getZ() + 0.5, 60));
    }

    private void end(@Nullable String failure) {
        DemoChapter c = chapter;
        DemoStage s = stage;
        if (c == null || s == null) {
            return;
        }
        s.command("tick sprint stop");
        s.command("tick unfreeze");
        try {
            s.dispose();
        } catch (RuntimeException e) {
            LOG.warn("DEMO chapter {} left its match behind", c.id(), e);
        }
        if (failure == null) {
            shown.add(c.id());
            try {
                DemoCatalog.markSeen(DemoCatalog.seenFile(), c);
            } catch (RuntimeException e) {
                LOG.warn("DEMO cannot mark {} seen", c.id(), e);
            }
            LOG.info("DEMO end {} ok", c.id());
        } else {
            failures.add(c.id() + ": " + failure);
            LOG.info("DEMO end {} failed: {}", c.id(), failure);
        }
        chapter = null;
        stage = null;
    }
}
