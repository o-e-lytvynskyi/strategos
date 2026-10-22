package dev.strategos.devtools;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** What a scenario may ask of the runner: its result, screenshots and another world. */
final class ScenarioContext {
    final Path out;
    final JsonObject result = new JsonObject();

    @Nullable String captureName;

    @Nullable String nextWorld;

    long nextSeed;

    ScenarioContext(Path out) {
        this.out = out;
    }

    /** A PNG of the next rendered frame, {@code <name>.png} in the output directory. */
    void capture(String name) {
        captureName = name;
    }

    /** Save and quit, then create and enter another world, as the vanilla pause menu does. */
    void switchWorld(String name, long seed) {
        nextWorld = name;
        nextSeed = seed;
    }
}
