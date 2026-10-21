package dev.strategos.mod.testing;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The mod's hooks into the engine (ADR-0020, rule B12): the inventory in {@code engine-surface.json} on one side, what
 * the code registers on the other, and one comparison. M0 reads entity types, saved-data names, command roots, mixin
 * configs and access transformers from the code; the other kinds have no probe yet (payloads and data maps get theirs
 * with M1's first ones), so the code side repeats the inventory for them.
 */
public record EngineSurface(Map<String, List<String>> kinds) {
    /** Every kind, in the inventory's order. */
    public static final List<String> KINDS = List.of(
            "entityTypes",
            "savedData",
            "commands",
            "mixinConfigs",
            "accessTransformers",
            "payloads",
            "dataMaps",
            "worldgen",
            "ticketControllers",
            "reloadListeners",
            "configs");

    /** The kinds the code side reads in M0. */
    public static final List<String> PROBED =
            List.of("entityTypes", "savedData", "commands", "mixinConfigs", "accessTransformers");

    public EngineSurface {
        Map<String, List<String>> sorted = new LinkedHashMap<>();
        for (String kind : KINDS) {
            sorted.put(kind, List.copyOf(new TreeSet<>(kinds.getOrDefault(kind, List.of()))));
        }
        kinds = sorted;
    }

    public List<String> of(String kind) {
        return kinds.getOrDefault(kind, List.of());
    }

    /** The inventory file: {@code {"milestone": …, "<kind>": [ids]}}. */
    public static EngineSurface read(Path json) {
        try (Reader reader = Files.newBufferedReader(json, StandardCharsets.UTF_8)) {
            Inventory inventory = new Gson().fromJson(reader, Inventory.class);
            Map<String, List<String>> kinds = new LinkedHashMap<>();
            kinds.put("entityTypes", inventory.entityTypes);
            kinds.put("savedData", inventory.savedData);
            kinds.put("commands", inventory.commands);
            kinds.put("mixinConfigs", inventory.mixinConfigs);
            kinds.put("accessTransformers", inventory.accessTransformers);
            kinds.put("payloads", inventory.payloads);
            kinds.put("dataMaps", inventory.dataMaps);
            kinds.put("worldgen", inventory.worldgen);
            kinds.put("ticketControllers", inventory.ticketControllers);
            kinds.put("reloadListeners", inventory.reloadListeners);
            kinds.put("configs", inventory.configs);
            return new EngineSurface(kinds);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the inventory " + json, e);
        }
    }

    /**
     * The code's surface from what the probes read: registered entity type ids (any namespace; only {@code
     * strategos:} ones count), saved-data names, command roots, the mod's {@code neoforge.mods.toml} and its access
     * transformer file ({@code null} when there is none). Unprobed kinds are copied from {@code inventory}.
     */
    public static EngineSurface ofCode(
            Collection<String> entityTypeIds,
            List<String> savedData,
            Collection<String> commandRoots,
            String modsToml,
            @Nullable String accessTransformerCfg,
            EngineSurface inventory) {
        Map<String, List<String>> kinds = new LinkedHashMap<>();
        kinds.put(
                "entityTypes",
                entityTypeIds.stream().filter(id -> id.startsWith("strategos:")).toList());
        kinds.put("savedData", savedData);
        kinds.put("commands", List.copyOf(commandRoots));
        kinds.put("mixinConfigs", tomlEntries(modsToml, "mixins", "config"));
        List<String> transformers = new ArrayList<>(tomlEntries(modsToml, "accessTransformers", "file"));
        if (accessTransformerCfg != null) {
            accessTransformerCfg
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(transformers::add);
        }
        kinds.put("accessTransformers", transformers);
        for (String kind : KINDS) {
            if (!PROBED.contains(kind)) {
                kinds.put(kind, inventory.of(kind));
            }
        }
        return new EngineSurface(kinds);
    }

    /** One line per difference, empty when the code and the inventory agree. */
    public static List<String> diff(EngineSurface expected, EngineSurface actual) {
        List<String> differences = new ArrayList<>();
        for (String kind : KINDS) {
            for (String id : actual.of(kind)) {
                if (!expected.of(kind).contains(id)) {
                    differences.add(kind + ": " + id + " is in the code but not in the inventory");
                }
            }
            for (String id : expected.of(kind)) {
                if (!actual.of(kind).contains(id)) {
                    differences.add(kind + ": " + id + " is in the inventory but not in the code");
                }
            }
        }
        return differences;
    }

    /**
     * The {@code key} values of every {@code [[table]]} entry in a TOML text; an entry without the key counts as
     * {@code [[table]]}. Line-based: enough for neoforge.mods.toml, which writes one key per line.
     */
    static List<String> tomlEntries(String toml, String table, String key) {
        List<String> entries = new ArrayList<>();
        boolean inTable = false;
        boolean found = false;
        for (String raw : toml.lines().toList()) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                if (inTable && !found) {
                    entries.add("[[" + table + "]]");
                }
                inTable = line.equals("[[" + table + "]]");
                found = false;
            } else if (inTable && line.matches(key + "\\s*=.*")) {
                entries.add(line.substring(line.indexOf('=') + 1).strip().replace("\"", ""));
                found = true;
            }
        }
        if (inTable && !found) {
            entries.add("[[" + table + "]]");
        }
        return entries;
    }

    /** The inventory file's shape, for Gson. */
    private static final class Inventory {
        List<String> entityTypes = List.of();
        List<String> savedData = List.of();
        List<String> commands = List.of();
        List<String> mixinConfigs = List.of();
        List<String> accessTransformers = List.of();
        List<String> payloads = List.of();
        List<String> dataMaps = List.of();
        List<String> worldgen = List.of();
        List<String> ticketControllers = List.of();
        List<String> reloadListeners = List.of();
        List<String> configs = List.of();
    }
}
