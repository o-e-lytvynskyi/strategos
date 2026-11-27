package dev.strategos.core.content;

import com.google.gson.JsonObject;
import dev.strategos.core.shared.CivId;

/** A civilisation file as read: its id (from its path), the file name for messages, and its JSON. */
public record RawCiv(CivId id, String file, JsonObject json) {
    public RawCiv {
        json = json.deepCopy();
    }

    @Override
    public JsonObject json() {
        return json.deepCopy();
    }
}
