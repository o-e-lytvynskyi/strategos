package dev.strategos.core.content;

import com.google.gson.JsonObject;
import dev.strategos.core.shared.TechId;

/** A technology file as read: its id (from its path), the file name for messages, and its JSON. */
public record RawTech(TechId id, String file, JsonObject json) {
    public RawTech {
        json = json.deepCopy();
    }

    @Override
    public JsonObject json() {
        return json.deepCopy();
    }
}
