package dev.strategos.mod.server.content;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.Scenario;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.command.ScenarioSetup;
import dev.strategos.mod.server.match.MatchRuntime;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * The data packs' scenarios (MATCH-01): {@code data/<namespace>/strategos/scenarios/<path>.json}, read when one is
 * used. Applying one to a running match submits its commands as the admin, so the setup is journaled with the match.
 */
public final class Scenarios {
    public static final String DIR = "strategos/scenarios";

    private Scenarios() {}

    /** Every scenario id the data packs hold. */
    public static List<ResourceLocation> list(MinecraftServer server) {
        TreeSet<ResourceLocation> ids = new TreeSet<>();
        server.getResourceManager()
                .listResources(DIR, rl -> rl.getPath().endsWith(".json"))
                .keySet()
                .forEach(rl -> ids.add(ResourceLocation.fromNamespaceAndPath(
                        rl.getNamespace(),
                        rl.getPath().substring(DIR.length() + 1, rl.getPath().length() - ".json".length()))));
        return List.copyOf(ids);
    }

    /** Scenario {@code id}; a missing or malformed file throws {@link IllegalArgumentException}. */
    public static Scenario load(MinecraftServer server, ResourceLocation id) {
        ResourceLocation file =
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), DIR + "/" + id.getPath() + ".json");
        var resource = server.getResourceManager()
                .getResource(file)
                .orElseThrow(() -> new IllegalArgumentException("no scenario " + id));
        try (Reader reader = resource.openAsReader()) {
            return Scenario.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(message -> new IllegalArgumentException("scenario " + id + ": " + message));
        } catch (IOException e) {
            throw new IllegalArgumentException("scenario " + id + ": " + e.getMessage(), e);
        }
    }

    /** Sets {@code scenario} up in {@code rt}, slot k as faction k at {@code starts.get(k)}; the commands it sent. */
    public static List<Command> apply(
            MatchRuntime rt, Scenario scenario, List<StartSlot> starts, TemplateSet templates) {
        List<Command> commands = ScenarioSetup.commands(scenario, starts, templates);
        for (Command c : commands) {
            rt.submit(PlayerSlot.ADMIN, c);
        }
        return commands;
    }
}
