package dev.strategos.mod.server.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.strategos.core.sim.econ.EconomyRules;
import dev.strategos.core.sim.econ.ResourceDef;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The resource types of the loaded data packs (ECO-01, D-10): {@code data/<namespace>/strategos/resources/<id>.json},
 * each {@code {"order": n, "subtypes": [..]}}, the resource named by its file; ordered by {@code order}, then id. A
 * match copies them into its rules when it is created, so a reload changes only later matches. Display names are the
 * language keys {@code strategos.resource.<id>}.
 */
public final class ResourceFiles {
    public static final String DIRECTORY = "strategos/resources";
    private static final String SUFFIX = ".json";

    private ResourceFiles() {}

    /** The economy the data packs define; {@code problems} gets each bad file, and with any, D-10's four are used. */
    public static EconomyRules read(ResourceManager resources, List<String> problems) {
        record Entry(int order, ResourceDef def) {}
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : resources
                .listResources(DIRECTORY, at -> at.getPath().endsWith(SUFFIX))
                .entrySet()) {
            ResourceLocation at = e.getKey();
            String id =
                    at.getPath().substring(DIRECTORY.length() + 1, at.getPath().length() - SUFFIX.length());
            try (Reader reader = e.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                List<String> subtypes = new ArrayList<>();
                for (JsonElement s : json.getAsJsonArray("subtypes")) {
                    subtypes.add(s.getAsString());
                }
                entries.add(new Entry(json.get("order").getAsInt(), new ResourceDef(id, subtypes, 0)));
            } catch (IOException
                    | JsonParseException
                    | IllegalStateException
                    | IllegalArgumentException
                    | NullPointerException
                    | UnsupportedOperationException ex) {
                problems.add("data/" + at.getNamespace() + "/" + at.getPath() + ": " + ex.getMessage());
            }
        }
        entries.sort(Comparator.comparingInt(Entry::order)
                .thenComparing(en -> en.def().id()));
        if (!problems.isEmpty() || entries.isEmpty()) {
            return EconomyRules.DEFAULTS;
        }
        try {
            return new EconomyRules(entries.stream().map(Entry::def).toList());
        } catch (IllegalArgumentException ex) {
            problems.add(String.valueOf(ex.getMessage()));
            return EconomyRules.DEFAULTS;
        }
    }
}
