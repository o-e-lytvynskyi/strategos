package dev.strategos.core.content;

import com.google.gson.JsonObject;
import dev.strategos.core.shared.TemplateId;
import java.util.List;

/**
 * A unit type as the core reads it (04 §8.3, §8.8), or a building's ({@link #isBuilding}, BLD-01): {@code netIndex} is its index in the set, sent instead of the id;
 * classes are its tokens, sorted; speed in blocks per tick, turn rate in degrees per tick; the body's entity type,
 * model archetype, skin and size in blocks; {@code radius} is its footprint for avoidance and slot spacing.
 */
public record UnitTemplate(
        TemplateId id,
        int netIndex,
        String name,
        List<String> classes,
        double walkSpeed,
        double turnRate,
        String mobility,
        String bodyType,
        String model,
        String skin,
        float width,
        float height,
        double radius) {
    public static final double DEFAULT_TURN_RATE = 45;
    public static final double DEFAULT_RADIUS = 0.35;
    /** The mobility of a building's template: it never moves and has no body (BLD-01). */
    public static final String BUILDING = "building";

    public UnitTemplate {
        classes = List.copyOf(classes);
    }

    /** M0's form: an unnamed humanoid of infantry mobility with default turn rate, radius and no classes. */
    public UnitTemplate(TemplateId id, int netIndex, String bodyType, double walkSpeed, float width, float height) {
        this(
                id,
                netIndex,
                id.value(),
                List.of(),
                walkSpeed,
                DEFAULT_TURN_RATE,
                "infantry",
                bodyType,
                "humanoid",
                "",
                width,
                height,
                DEFAULT_RADIUS);
    }

    /** A building's template (BLD-01): a name and classes; its numbers are value paths, its shape its blueprint. */
    public static UnitTemplate building(TemplateId id, int netIndex, String name, List<String> classes) {
        return new UnitTemplate(id, netIndex, name, classes, 0, 0, BUILDING, "", "", "", 0, 0, 0);
    }

    /** Whether this is a building's template, not a unit's. */
    public boolean isBuilding() {
        return BUILDING.equals(mobility);
    }

    /** This template at another net index (the session's {@link TemplateIdTable} gives it). */
    public UnitTemplate atIndex(int index) {
        return new UnitTemplate(
                id, index, name, classes, walkSpeed, turnRate, mobility, bodyType, model, skin, width, height, radius);
    }

    /** The template as flattened JSON, the form a data file would compile to. */
    public JsonObject toJson() {
        JsonObject identity = new JsonObject();
        identity.addProperty("name", name);
        identity.addProperty("classes", String.join(" ", classes));
        JsonObject motion = new JsonObject();
        motion.addProperty("walkSpeed", walkSpeed);
        motion.addProperty("turnRate", turnRate);
        motion.addProperty("mobility", mobility);
        JsonObject body = new JsonObject();
        body.addProperty("entityType", bodyType);
        body.addProperty("model", model);
        body.addProperty("skin", skin);
        body.addProperty("width", (double) width);
        body.addProperty("height", (double) height);
        body.addProperty("radius", radius);
        JsonObject json = new JsonObject();
        json.add("Identity", identity);
        json.add("UnitMotion", motion);
        json.add("Body", body);
        return json;
    }
}
