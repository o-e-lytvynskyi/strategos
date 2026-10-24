package dev.strategos.core.client.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UI-01.2: our default bindings conflict with no vanilla 1.21.1 default, by the committed table (generated from
 * Options.java and NeoForge's conflict contexts): a vanilla {@code UNIVERSAL} binding conflicts in every context, an
 * {@code IN_GAME} one only with ours in game or in first person; and no default needs Ctrl + click.
 */
class DefaultKeysTest {
    private record Vanilla(String name, KeyCode key, String context) {}

    @Test
    void noDefaultConflictsWithVanilla() throws IOException {
        List<String> conflicts = new ArrayList<>();
        for (Binding ours : DefaultBindings.all()) {
            if (!ours.rebindable() || ours.modifiers() != Modifiers.NONE) {
                continue; // read by the RTS screen itself, never a KeyMapping; or held with Alt (UI-08's card cells),
                // which
                // no vanilla default is, in a screen where vanilla's gameplay keys never act
            }
            for (Vanilla v : vanilla()) {
                if (v.key().equals(ours.key()) && ours.context().conflictsWithVanilla(v.context())) {
                    conflicts.add(ours.mappingName() + " on " + v.name() + " (" + v.context() + ")");
                }
            }
        }

        assertThat(conflicts).isEmpty();
    }

    @Test
    void theTableCatchesAConflict() throws IOException {
        Binding onQ = new Binding(Action.CAMERA_ROTATE_LEFT, ActionContext.RTS, KeyCode.key('Q'), Modifiers.NONE, true);

        assertThat(vanilla())
                .anySatisfy(v -> assertThat(
                                v.key().equals(onQ.key()) && onQ.context().conflictsWithVanilla(v.context()))
                        .as(v.name())
                        .isTrue());
    }

    @Test
    void noDefaultNeedsCtrlAndAClick() {
        assertThat(DefaultBindings.all())
                .filteredOn(b -> b.key().kind() == KeyCode.Kind.MOUSE)
                .allSatisfy(b -> assertThat(Modifiers.ctrl(b.modifiers()))
                        .as(b.action().name())
                        .isFalse());
    }

    private static List<Vanilla> vanilla() throws IOException {
        try (InputStream in = DefaultKeysTest.class.getResourceAsStream("/client/vanilla-keys-1.21.1.json")) {
            assertThat(in).isNotNull();
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            List<Vanilla> out = new ArrayList<>();
            for (JsonElement e : doc.getAsJsonArray("bindings")) {
                JsonObject b = e.getAsJsonObject();
                int code = b.get("code").getAsInt();
                KeyCode key = b.get("type").getAsString().equals("mouse") ? KeyCode.mouse(code) : KeyCode.key(code);
                out.add(new Vanilla(
                        b.get("name").getAsString(), key, b.get("context").getAsString()));
            }
            return out;
        }
    }
}
