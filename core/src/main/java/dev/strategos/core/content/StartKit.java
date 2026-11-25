package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * A civilisation's start kit (ADR-0028): the starting resources and the entities it owns at its start slot, each by
 * role or by template, relative to the slot as a scenario's entities are ({@link Scenario.Entity}).
 */
public record StartKit(SortedMap<String, Long> resources, List<Entity> entities) {
    public static final StartKit EMPTY = new StartKit(new TreeMap<>(), List.of());

    private static final String KEYS = "resources, entities";
    private static final String ENTITY_KEYS = "role, template, dx, dz, count, spread, rotation";

    /**
     * Entities of one role or one template (exactly one of the two): a building (count 1) or {@code count} units,
     * {@code spread} apart.
     */
    public record Entity(
            @Nullable String role,
            @Nullable TemplateId template,
            int dx,
            int dz,
            int count,
            double spread,
            int rotation) {
        public Entity {
            if ((role == null) == (template == null)) {
                throw new IllegalArgumentException("a start-kit entity has a role or a template, not both or neither");
            }
        }
    }

    public StartKit {
        resources = Collections.unmodifiableSortedMap(new TreeMap<>(resources));
        entities = List.copyOf(entities);
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonObject res = new JsonObject();
        resources.forEach(res::addProperty);
        o.add("resources", res);
        JsonArray list = new JsonArray();
        for (Entity e : entities) {
            JsonObject j = new JsonObject();
            if (e.role() != null) {
                j.addProperty("role", e.role());
            } else {
                j.addProperty("template", String.valueOf(e.template()));
            }
            j.addProperty("dx", e.dx());
            j.addProperty("dz", e.dz());
            j.addProperty("count", e.count());
            j.addProperty("spread", e.spread());
            j.addProperty("rotation", e.rotation());
            list.add(j);
        }
        o.add("entities", list);
        return o;
    }

    /** Decodes {@code json} at pointer {@code at}; entries with problems are left out after recording each problem. */
    static StartKit fromJson(@Nullable JsonElement json, String file, String at, List<TemplateProblem> problems) {
        if (json == null) {
            return EMPTY;
        }
        if (!json.isJsonObject()) {
            problems.add(TemplateProblem.error(file, at, "expected a start kit object"));
            return EMPTY;
        }
        JsonObject o = json.getAsJsonObject();
        unknownKeys(o, KEYS, file, at, problems);
        SortedMap<String, Long> resources = new TreeMap<>();
        JsonElement res = o.get("resources");
        if (res != null) {
            if (!res.isJsonObject()) {
                problems.add(TemplateProblem.error(file, at + "/resources", "expected an object of resource amounts"));
            } else {
                for (Map.Entry<String, JsonElement> e :
                        res.getAsJsonObject().asMap().entrySet()) {
                    JsonElement v = e.getValue();
                    if (!(v instanceof JsonPrimitive p)
                            || !p.isNumber()
                            || !(p.getAsDouble() >= 0)
                            || p.getAsDouble() != Math.rint(p.getAsDouble())
                            || p.getAsDouble() > 1_000_000_000) {
                        problems.add(TemplateProblem.error(
                                file,
                                at + "/resources/" + e.getKey(),
                                "expected a whole amount of 0 or more, got " + v));
                    } else {
                        resources.put(e.getKey(), p.getAsLong());
                    }
                }
            }
        }
        List<Entity> entities = new ArrayList<>();
        JsonElement list = o.get("entities");
        if (list != null) {
            if (!list.isJsonArray()) {
                problems.add(TemplateProblem.error(file, at + "/entities", "expected an array of entities"));
            } else {
                for (int i = 0; i < list.getAsJsonArray().size(); i++) {
                    Entity e = entity(list.getAsJsonArray().get(i), file, at + "/entities/" + i, problems);
                    if (e != null) {
                        entities.add(e);
                    }
                }
            }
        }
        return new StartKit(resources, entities);
    }

    private static @Nullable Entity entity(JsonElement json, String file, String at, List<TemplateProblem> problems) {
        if (!json.isJsonObject()) {
            problems.add(TemplateProblem.error(file, at, "expected an entity object"));
            return null;
        }
        JsonObject o = json.getAsJsonObject();
        int before = problems.size();
        unknownKeys(o, ENTITY_KEYS, file, at, problems);
        String role = null;
        TemplateId template = null;
        JsonElement r = o.get("role");
        if (r != null) {
            if (r instanceof JsonPrimitive p
                    && p.isString()
                    && CivRoles.NAME.matcher(p.getAsString()).matches()) {
                role = p.getAsString();
            } else {
                problems.add(TemplateProblem.error(file, at + "/role", "expected a role name, got " + r));
            }
        }
        JsonElement t = o.get("template");
        if (t != null) {
            if (t instanceof JsonPrimitive p && p.isString()) {
                try {
                    template = new TemplateId(p.getAsString());
                } catch (IllegalArgumentException e) {
                    problems.add(TemplateProblem.error(file, at + "/template", String.valueOf(e.getMessage())));
                }
            } else {
                problems.add(TemplateProblem.error(file, at + "/template", "expected a template id, got " + t));
            }
        }
        if ((r == null) == (t == null)) {
            problems.add(TemplateProblem.error(file, at, "an entity names a role or a template, not both or neither"));
        }
        int dx = whole(o, "dx", 0, -100_000, 100_000, file, at, problems);
        int dz = whole(o, "dz", 0, -100_000, 100_000, file, at, problems);
        int count = whole(o, "count", 1, 1, 200, file, at, problems);
        int rotation = whole(o, "rotation", 0, 0, 3, file, at, problems);
        double spread = 2.0;
        JsonElement s = o.get("spread");
        if (s != null) {
            if (s instanceof JsonPrimitive p && p.isNumber() && Double.isFinite(p.getAsDouble())) {
                spread = p.getAsDouble();
            } else {
                problems.add(TemplateProblem.error(file, at + "/spread", "expected a number, got " + s));
            }
        }
        if (problems.size() > before) {
            return null;
        }
        return new Entity(role, template, dx, dz, count, spread, rotation);
    }

    private static int whole(
            JsonObject o, String key, int fallback, int min, int max, String file, String at, List<TemplateProblem> p) {
        JsonElement e = o.get(key);
        if (e == null) {
            return fallback;
        }
        if (!(e instanceof JsonPrimitive n)
                || !n.isNumber()
                || n.getAsDouble() != Math.rint(n.getAsDouble())
                || n.getAsDouble() < min
                || n.getAsDouble() > max) {
            p.add(TemplateProblem.error(
                    file, at + "/" + key, "expected a whole number " + min + ".." + max + ", got " + e));
            return fallback;
        }
        return n.getAsInt();
    }

    static void unknownKeys(JsonObject o, String known, String file, String at, List<TemplateProblem> problems) {
        List<String> names = List.of(known.split(", "));
        for (String key : new TreeSet<>(o.keySet())) {
            if (!names.contains(key)) {
                problems.add(
                        TemplateProblem.error(file, at + "/" + key, "unknown key '" + key + "'; known here: " + known));
            }
        }
    }
}
