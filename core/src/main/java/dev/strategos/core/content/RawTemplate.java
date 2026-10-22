package dev.strategos.core.content;

import com.google.gson.JsonObject;
import dev.strategos.core.shared.TemplateId;

/** A template file as read: its id (from its path), the file name for messages, and its JSON, unresolved. */
public record RawTemplate(TemplateId id, String file, JsonObject json) {
    public RawTemplate {
        json = json.deepCopy();
    }

    @Override
    public JsonObject json() {
        return json.deepCopy();
    }
}
