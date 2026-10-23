package dev.strategos.mod.server.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import dev.strategos.core.content.RawTemplate;
import dev.strategos.core.content.TemplateProblem;
import dev.strategos.core.shared.TemplateId;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * The template files of the loaded data packs (spec §6.6): {@code data/<namespace>/strategos/templates/**.json}, each
 * the template {@code <namespace>:<path below templates, without .json>}. Reading parses them, off the main thread in a
 * reload; {@link #raws} then keeps those whose NeoForge conditions hold, without the conditions key. A file that does
 * not parse is a problem naming it.
 */
public final class TemplateFiles {
    public static final String DIRECTORY = "strategos/templates";
    private static final String SUFFIX = ".json";

    private TemplateFiles() {}

    /** The parsed files, by resource location, and the problems of those that did not parse. */
    public record Read(Map<ResourceLocation, JsonElement> files, List<TemplateProblem> problems) {}

    public static Read read(ResourceManager resources) {
        Map<ResourceLocation, JsonElement> files = new TreeMap<>();
        List<TemplateProblem> problems = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : resources
                .listResources(DIRECTORY, at -> at.getPath().endsWith(SUFFIX))
                .entrySet()) {
            ResourceLocation at = e.getKey();
            try (Reader reader = e.getValue().openAsReader()) {
                files.put(at, JsonParser.parseReader(reader));
            } catch (IOException | JsonParseException ex) {
                problems.add(TemplateProblem.error(file(at), "", "does not parse: " + ex.getMessage()));
            }
        }
        return new Read(files, problems);
    }

    /**
     * The templates of {@code read} whose conditions hold under {@code ops}, a file that is no object a problem; on
     * the main thread, where the conditions' context is.
     */
    public static List<RawTemplate> raws(Read read, DynamicOps<JsonElement> ops, List<TemplateProblem> problems) {
        List<RawTemplate> raws = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : read.files().entrySet()) {
            ResourceLocation at = e.getKey();
            if (!e.getValue().isJsonObject()) {
                problems.add(TemplateProblem.error(file(at), "", "a template is a JSON object"));
                continue;
            }
            JsonObject json = e.getValue().getAsJsonObject().deepCopy();
            if (!ICondition.conditionsMatched(ops, json)) {
                continue;
            }
            json.remove(ConditionalOps.DEFAULT_CONDITIONS_KEY);
            String path =
                    at.getPath().substring(DIRECTORY.length() + 1, at.getPath().length() - SUFFIX.length());
            TemplateId id;
            try {
                id = new TemplateId(at.getNamespace() + ":" + path);
            } catch (IllegalArgumentException ex) {
                problems.add(TemplateProblem.error(file(at), "", String.valueOf(ex.getMessage())));
                continue;
            }
            raws.add(new RawTemplate(id, file(at), json));
        }
        return raws;
    }

    /** The file as a pack holds it: {@code data/<namespace>/<path>}. */
    static String file(ResourceLocation at) {
        return "data/" + at.getNamespace() + "/" + at.getPath();
    }
}
