package dev.strategos.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/** One L4 client scenario (spec 7.7), driven by {@link ScenarioRunner}. */
interface Scenario {
    String name();

    /** The first world's name and seed; a scenario may ask for more worlds through the context. */
    default String worldName() {
        return "strategos-" + name();
    }

    default long seed() {
        return 42;
    }

    /** On the integrated server's thread, once a world is loaded and rendered; called again for each new world. */
    void setup(IntegratedServer server, ScenarioContext ctx);

    /** On the client thread, every tick after a setup; true when the scenario has finished. */
    boolean tick(Minecraft mc, ScenarioContext ctx, int tick);

    /** Frame and client-tick boundaries, for the timing scenarios. */
    default void frameStart() {}

    default void frameEnd() {}

    default void clientTickStart() {}

    default void clientTickEnd() {}
}
