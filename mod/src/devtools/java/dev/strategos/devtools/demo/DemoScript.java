package dev.strategos.devtools.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A chapter's script: steps run one after another on the server thread, a step at a time until it is done. Waits
 * count server ticks, 20 a second at the normal pace; {@link #fast} sprints the server through a wait nobody wants to
 * sit through and goes back to the normal pace after it. A step that throws, or a wait that runs out, fails the
 * chapter, and the show goes on with the next.
 */
public final class DemoScript {
    /** One step; {@code tick} counts from 0 within it, true when it is done. */
    interface Step {
        boolean tick(DemoStage stage, int tick);
    }

    /** What a camera step asks of the client: the view's centre, world blocks, and its distance from it. */
    public record Look(double x, double z, double distance) {}

    /** The longest line the screen shows whole at the scenario's 1280 × 720 and GUI scale. */
    static final int MAX_LINE = 55;

    final List<Step> steps = new ArrayList<>();

    /** A line under the screen's middle and in the log, for the narrator. */
    public DemoScript say(String line) {
        if (line.length() > MAX_LINE) {
            throw new IllegalArgumentException("a line past " + MAX_LINE + " characters runs off the screen: " + line);
        }
        steps.add((stage, tick) -> {
            DemoDirector.say(stage, line);
            return true;
        });
        return this;
    }

    /** Anything on the stage, once. */
    public DemoScript run(Consumer<DemoStage> action) {
        steps.add((stage, tick) -> {
            action.accept(stage);
            return true;
        });
        return this;
    }

    /** The camera on plot (dx, dz) from {@code distance} blocks. */
    public DemoScript look(double dx, double dz, double distance) {
        steps.add((stage, tick) -> {
            DemoDirector.look(
                    new Look(stage.centre().getX() + dx + 0.5, stage.centre().getZ() + dz + 0.5, distance));
            return true;
        });
        return this;
    }

    /** {@code seconds} of the show at the normal pace. */
    public DemoScript pause(double seconds) {
        int ticks = (int) Math.round(seconds * 20);
        steps.add((stage, tick) -> tick >= ticks);
        return this;
    }

    /** Waits at the normal pace until {@code done} holds, at most {@code seconds}; past them the chapter fails. */
    public DemoScript until(String what, double seconds, Predicate<DemoStage> done) {
        steps.add(wait(what, seconds, done));
        return this;
    }

    /** As {@link #until}, the server sprinting meanwhile; {@code seconds} of game time. */
    public DemoScript fast(String what, double seconds, Predicate<DemoStage> done) {
        Step wait = wait(what, seconds, done);
        steps.add((stage, tick) -> {
            if (tick == 0) {
                stage.command("tick sprint 1000000");
            }
            boolean over;
            try {
                over = wait.tick(stage, tick);
            } catch (RuntimeException e) {
                stage.command("tick sprint stop");
                throw e;
            }
            if (over) {
                stage.command("tick sprint stop");
            }
            return over;
        });
        return this;
    }

    private static Step wait(String what, double seconds, Predicate<DemoStage> done) {
        int ticks = (int) Math.round(seconds * 20);
        return (stage, tick) -> {
            if (stage.queries() != null && done.test(stage)) {
                return true;
            }
            if (tick >= ticks) {
                throw new IllegalStateException("not within " + seconds + " s: " + what);
            }
            return false;
        };
    }
}
