package dev.strategos.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * The derived kinds (04 §8.3, DATA-01.4): a foundation, a placement preview and a fog mirage are computed from one
 * definition by keeping some of its components and overriding others.
 *
 * <ul>
 *   <li>{@link #FOUNDATION}: identity, footprint, cost and obstruction; health driven by construction progress; no
 *       attack, vision or anything else;
 *   <li>{@link #PREVIEW}: identity, footprint and the body's visuals, for the placement ghost;
 *   <li>{@link #MIRAGE}: identity, footprint, body and health, the last-seen record of a building in fog.
 * </ul>
 */
public enum TemplateFilter {
    FOUNDATION,
    PREVIEW,
    MIRAGE;

    public JsonObject apply(JsonObject flattened) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : flattened.entrySet()) {
            if (keeps(e.getKey())) {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        if (this == FOUNDATION && out.has("Health") && out.get("Health").isJsonObject()) {
            out.getAsJsonObject("Health").addProperty("progressDriven", true);
        }
        return out;
    }

    private boolean keeps(String component) {
        return switch (this) {
            case FOUNDATION ->
                component.equals("Identity")
                        || component.equals("Footprint")
                        || component.equals("Cost")
                        || component.equals("Obstruction")
                        || component.equals("Health");
            case PREVIEW -> component.equals("Identity") || component.equals("Footprint") || component.equals("Body");
            case MIRAGE ->
                component.equals("Identity")
                        || component.equals("Footprint")
                        || component.equals("Body")
                        || component.equals("Health");
        };
    }
}
