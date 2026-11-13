package dev.strategos.core.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CanonicalJson;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.List;

/**
 * A template set as it travels (DATA-03, ADR-0012): to a client as {@code template_set}, and in a match's journal as a
 * {@code ContentSwap}: the set's version, the session's id table, and each template's net index, id and flattened
 * JSON in canonical form. {@link #toSet()} decodes it as the server compiled it, so its version is the server's.
 */
@CoreApi
public record TemplateSetView(
        String version,
        List<TemplateId> table,
        List<TemplateSetView.Template> templates,
        List<TemplateSetView.Tech> techs) {
    /** The bound of {@code template_set} (spec §2.7). */
    public static final int MAX_BYTES = 256 * 1024;

    /** Ends the version of a view that carries techs; the version itself is hex. */
    static final String TECHS_MARK = "+t";

    static final int MAX_ID = 256;
    static final int MAX_JSON = 64 * 1024;
    static final int MAX_TEMPLATES = 4_096;

    /** One template: its net index, id and canonical flattened JSON. */
    @CoreApi
    public record Template(int netIndex, TemplateId id, String json) {}

    /** One technology (TECH-01): its net index, id and canonical JSON. */
    @CoreApi
    public record Tech(int netIndex, TechId id, String json) {}

    public TemplateSetView {
        table = List.copyOf(table);
        templates = List.copyOf(templates);
        techs = List.copyOf(techs);
    }

    /** A view without technologies: what streams written before TECH-01 hold. */
    public TemplateSetView(String version, List<TemplateId> table, List<Template> templates) {
        this(version, table, templates, List.of());
    }

    public static TemplateSetView of(TemplateSet set, TemplateIdTable table) {
        List<Template> templates = new ArrayList<>();
        for (UnitTemplate t : set.all()) {
            JsonObject json = set.flattened(t.id());
            templates.add(
                    new Template(t.netIndex(), t.id(), CanonicalJson.write(json == null ? new JsonObject() : json)));
        }
        List<Tech> techs = new ArrayList<>();
        for (TechDef t : set.techs()) {
            techs.add(new Tech(t.netIndex(), t.id(), CanonicalJson.write(t.toJson())));
        }
        return new TemplateSetView(set.version(), table.ids(), templates, techs);
    }

    /** The set this view describes, decoded as the server's compiler decodes; its version is checked by the caller. */
    public TemplateSet toSet() {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        List<TemplateCompiler.TechFlat> techFlats = new ArrayList<>();
        try {
            for (Template t : templates) {
                flats.add(new TemplateCompiler.Flat(
                        t.id(), t.netIndex(), JsonParser.parseString(t.json()).getAsJsonObject()));
            }
            for (Tech t : techs) {
                techFlats.add(new TemplateCompiler.TechFlat(
                        t.id(), t.netIndex(), JsonParser.parseString(t.json()).getAsJsonObject()));
            }
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException(
                    "a template set view holds JSON that does not parse: " + e.getMessage(), e);
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(flats, techFlats);
        if (!result.ok()) {
            throw new IllegalArgumentException("a template set view does not decode: " + result.problems());
        }
        return result.set();
    }

    public static final WireCodec<TemplateSetView> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, TemplateSetView view) {
            // Streams are read back to back (a journal's inputs), so the bytes left cannot tell a trailing section:
            // a view with techs marks its version, which as hex never ends in TECHS_MARK.
            if (view.techs().isEmpty() && view.version().endsWith(TECHS_MARK)) {
                throw new IllegalArgumentException("a version ending in '" + TECHS_MARK + "' needs techs");
            }
            out.writeString(view.techs().isEmpty() ? view.version() : view.version() + TECHS_MARK, 64 + 2);
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
            if (!view.techs().isEmpty()) {
                out.writeVarInt(view.techs().size());
                for (Tech t : view.techs()) {
                    out.writeVarInt(t.netIndex());
                    out.writeString(t.id().value(), MAX_ID);
                    out.writeString(t.json(), MAX_JSON);
                }
            }
        }

        @Override
        public TemplateSetView read(WireReader in) {
            String marked = in.readString(64 + 2);
            boolean withTechs = marked.endsWith(TECHS_MARK);
            String version = withTechs ? marked.substring(0, marked.length() - TECHS_MARK.length()) : marked;
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
            List<Tech> techs = new ArrayList<>();
            if (withTechs) {
                int n = in.readVarInt(MAX_TEMPLATES);
                if (n == 0) {
                    throw new WireException("a template set marked with techs holds none");
                }
                for (int k = 0; k < n; k++) {
                    int index = in.readVarInt(MAX_TEMPLATES);
                    TechId id = techId(in.readString(MAX_ID));
                    techs.add(new Tech(index, id, in.readString(MAX_JSON)));
                }
            }
            return new TemplateSetView(version, table, templates, techs);
        }

        @Override
        public int maxSize() {
            return MAX_BYTES;
        }
    };

    private static TechId techId(String value) {
        try {
            return new TechId(value);
        } catch (IllegalArgumentException e) {
            throw new WireException("tech id: " + e.getMessage());
        }
    }

    private static TemplateId id(String value) {
        try {
            return new TemplateId(value);
        } catch (IllegalArgumentException e) {
            throw new WireException("template id: " + e.getMessage());
        }
    }
}
