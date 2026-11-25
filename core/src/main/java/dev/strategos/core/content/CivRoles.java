package dev.strategos.core.content;

import com.google.gson.JsonObject;
import dev.strategos.core.shared.TemplateId;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A civilisation's roles (ADR-0028): the engine's role names, each mapped to the civilisation's template. Code that
 * needs "a house of this faction" asks the role, never a template id. The names below are the ones the engine reads;
 * a civilisation may add others for its own content. Kept sorted, so the canonical JSON is stable.
 */
public record CivRoles(SortedMap<String, TemplateId> byRole) {
    public static final String CENTRE = "centre";
    public static final String HOUSE = "house";
    public static final String WORKER = "worker";
    public static final String INFANTRY = "infantry";
    public static final String RANGED = "ranged";
    public static final String CAVALRY = "cavalry";
    public static final String SIEGE = "siege";
    public static final String BARRACKS = "barracks";
    public static final String TOWER = "tower";
    public static final String FARMSTEAD = "farmstead";
    public static final String FIELD = "field";
    public static final String STOREHOUSE = "storehouse";
    public static final String WALL = "wall";
    public static final String GATE = "gate";

    /** What a role name looks like. */
    public static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

    public static final CivRoles NONE = new CivRoles(new TreeMap<>());

    public CivRoles {
        byRole = Collections.unmodifiableSortedMap(new TreeMap<>(byRole));
    }

    public @Nullable TemplateId get(String role) {
        return byRole.get(role);
    }

    public boolean has(String role) {
        return byRole.containsKey(role);
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        byRole.forEach((role, template) -> o.addProperty(role, template.value()));
        return o;
    }
}
