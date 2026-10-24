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
import org.jspecify.annotations.Nullable;

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

    /** The data packs' computer strategies: {@code data/<ns>/strategos/ai/<profile>.json}. */
    public static final String AI_DIR = "strategos/ai";

    /**
     * Strategy {@code profile}'s JSON, compact, so the command carrying it stays small; a profile without a namespace is
     * Strategos's. Null when no data pack has it.
     */
    public static @Nullable String script(MinecraftServer server, String profile) {
        ResourceLocation id = profile.contains(":")
                ? ResourceLocation.parse(profile)
                : ResourceLocation.fromNamespaceAndPath("strategos", profile);
        ResourceLocation file =
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), AI_DIR + "/" + id.getPath() + ".json");
        var resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("strategy " + profile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Sets {@code scenario} up in {@code rt}, slot k as faction k at {@code starts.get(k)}, its {@code ai:} slots
     * played by their strategies from the data packs; the commands it sent.
     */
    public static List<Command> apply(
            MinecraftServer server, MatchRuntime rt, Scenario scenario, List<StartSlot> starts, TemplateSet templates) {
        List<Command> commands =
                ScenarioSetup.commands(scenario, starts, templates, profile -> script(server, profile));
        for (Command c : commands) {
            rt.submit(PlayerSlot.ADMIN, c);
        }
        return commands;
    }
}
