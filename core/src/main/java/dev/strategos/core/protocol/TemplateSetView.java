package dev.strategos.core.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CanonicalJson;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;

/**
 * A template set as it travels (DATA-03, ADR-0012): to a client as {@code template_set}, and in a match's journal as a
 * {@code ContentSwap}: the set's version, the session's id table, and each template's net index, id and flattened
 * JSON in canonical form. {@link #toSet()} decodes it as the server compiled it, so its version is the server's.
 */
@CoreApi
public record TemplateSetView(String version, List<TemplateId> table, List<TemplateSetView.Template> templates) {
    /** The bound of {@code template_set} (spec §2.7). */
    public static final int MAX_BYTES = 256 * 1024;

    static final int MAX_ID = 256;
    static final int MAX_JSON = 64 * 1024;
    static final int MAX_TEMPLATES = 4_096;

    /** One template: its net index, id and canonical flattened JSON. */
    @CoreApi
    public record Template(int netIndex, TemplateId id, String json) {}

    public TemplateSetView {
        table = List.copyOf(table);
        templates = List.copyOf(templates);
    }

    public static TemplateSetView of(TemplateSet set, TemplateIdTable table) {
        List<Template> templates = new ArrayList<>();
        for (UnitTemplate t : set.all()) {
            JsonObject json = set.flattened(t.id());
            templates.add(
                    new Template(t.netIndex(), t.id(), CanonicalJson.write(json == null ? new JsonObject() : json)));
        }
        return new TemplateSetView(set.version(), table.ids(), templates);
    }

    /** The set this view describes, decoded as the server's compiler decodes; its version is checked by the caller. */
    public TemplateSet toSet() {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (Template t : templates) {
            flats.add(new TemplateCompiler.Flat(
                    t.id(), t.netIndex(), JsonParser.parseString(t.json()).getAsJsonObject()));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(flats);
        if (!result.ok()) {
            throw new IllegalArgumentException("a template set view does not decode: " + result.problems());
        }
        return result.set();
    }

    public static final WireCodec<TemplateSetView> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, TemplateSetView view) {
            out.writeString(view.version(), 64);
            out.writeVarInt(view.table().size());
            for (TemplateId id : view.table()) {
                out.writeString(id.value(), MAX_ID);
            }
            out.writeVarInt(view.templates().size());
            for (Template t : view.templates()) {
                out.writeVarInt(t.netIndex());
                out.writeString(t.id().value(), MAX_ID);
                out.writeString(t.json(), MAX_JSON);
            }
        }

        @Override
        public TemplateSetView read(WireReader in) {
            String version = in.readString(64);
            int ids = in.readVarInt(MAX_TEMPLATES);
            List<TemplateId> table = new ArrayList<>(ids);
            for (int k = 0; k < ids; k++) {
                table.add(id(in.readString(MAX_ID)));
            }
            int count = in.readVarInt(MAX_TEMPLATES);
            List<Template> templates = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                int index = in.readVarInt(MAX_TEMPLATES);
                TemplateId id = id(in.readString(MAX_ID));
                templates.add(new Template(index, id, in.readString(MAX_JSON)));
            }
            return new TemplateSetView(version, table, templates);
        }

        @Override
        public int maxSize() {
            return MAX_BYTES;
        }
    };

    private static TemplateId id(String value) {
        try {
            return new TemplateId(value);
        } catch (IllegalArgumentException e) {
            throw new WireException("template id: " + e.getMessage());
        }
    }
}
