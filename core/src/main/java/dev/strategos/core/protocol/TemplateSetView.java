package dev.strategos.core.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.strategos.core.content.CanonicalJson;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.TechDef;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CivId;
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
        List<TemplateSetView.Tech> techs,
        List<TemplateSetView.Civ> civs) {
    /** The bound of {@code template_set} (spec §2.7). */
    public static final int MAX_BYTES = 256 * 1024;

    /** Ends the version of a view that carries techs; the version itself is hex. */
    static final String TECHS_MARK = "+t";

    /**
     * Ends the version of a view that carries civilisations (after {@link #TECHS_MARK} when both are there): the set's
     * civilisations are not just the implied default.
     */
    static final String CIVS_MARK = "+c";

    static final int MAX_ID = 256;
    static final int MAX_JSON = 64 * 1024;
    static final int MAX_TEMPLATES = 4_096;
    static final int MAX_CIVS = 256;

    /** One template: its net index, id and canonical flattened JSON. */
    @CoreApi
    public record Template(int netIndex, TemplateId id, String json) {}

    /** One technology (TECH-01): its net index, id and canonical JSON. */
    @CoreApi
    public record Tech(int netIndex, TechId id, String json) {}

    /** One civilisation (CIV-01): its id and canonical JSON. */
    @CoreApi
    public record Civ(CivId id, String json) {}

    public TemplateSetView {
        table = List.copyOf(table);
        templates = List.copyOf(templates);
        techs = List.copyOf(techs);
        civs = List.copyOf(civs);
    }

    /** A view without civilisations: the set has the implied default (what streams written before CIV-01 hold). */
    public TemplateSetView(String version, List<TemplateId> table, List<Template> templates, List<Tech> techs) {
        this(version, table, templates, techs, List.of());
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
        List<Civ> civs = new ArrayList<>();
        if (!set.hasImpliedCivs()) {
            for (CivDef c : set.civs()) {
                civs.add(new Civ(c.id(), CanonicalJson.write(c.toJson())));
            }
        }
        return new TemplateSetView(set.version(), table.ids(), templates, techs, civs);
    }

    /** The set this view describes, decoded as the server's compiler decodes; its version is checked by the caller. */
    public TemplateSet toSet() {
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        List<TemplateCompiler.TechFlat> techFlats = new ArrayList<>();
        List<RawCiv> civFiles = new ArrayList<>();
        try {
            for (Template t : templates) {
                flats.add(new TemplateCompiler.Flat(
                        t.id(), t.netIndex(), JsonParser.parseString(t.json()).getAsJsonObject()));
            }
            for (Tech t : techs) {
                techFlats.add(new TemplateCompiler.TechFlat(
                        t.id(), t.netIndex(), JsonParser.parseString(t.json()).getAsJsonObject()));
            }
            for (Civ c : civs) {
                civFiles.add(new RawCiv(
                        c.id(), c.id().value(), JsonParser.parseString(c.json()).getAsJsonObject()));
            }
        } catch (JsonParseException | IllegalStateException e) {
            throw new IllegalArgumentException(
                    "a template set view holds JSON that does not parse: " + e.getMessage(), e);
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(flats, techFlats, civFiles);
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
            if (view.civs().isEmpty() && view.version().endsWith(CIVS_MARK)) {
                throw new IllegalArgumentException("a version ending in '" + CIVS_MARK + "' needs civilisations");
            }
            out.writeString(
                    view.version()
                            + (view.techs().isEmpty() ? "" : TECHS_MARK)
                            + (view.civs().isEmpty() ? "" : CIVS_MARK),
                    64 + 4);
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
            if (!view.civs().isEmpty()) {
                out.writeVarInt(view.civs().size());
                for (Civ c : view.civs()) {
                    out.writeString(c.id().value(), MAX_ID);
                    out.writeString(c.json(), MAX_JSON);
                }
            }
        }

        @Override
        public TemplateSetView read(WireReader in) {
            String marked = in.readString(64 + 4);
            boolean withCivs = marked.endsWith(CIVS_MARK);
            String unmarked = withCivs ? marked.substring(0, marked.length() - CIVS_MARK.length()) : marked;
            boolean withTechs = unmarked.endsWith(TECHS_MARK);
            String version = withTechs ? unmarked.substring(0, unmarked.length() - TECHS_MARK.length()) : unmarked;
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
            List<Civ> civs = new ArrayList<>();
            if (withCivs) {
                int n = in.readVarInt(MAX_CIVS);
                if (n == 0) {
                    throw new WireException("a template set marked with civilisations holds none");
                }
                for (int k = 0; k < n; k++) {
                    CivId id = civId(in.readString(MAX_ID));
                    civs.add(new Civ(id, in.readString(MAX_JSON)));
                }
            }
            return new TemplateSetView(version, table, templates, techs, civs);
        }

        @Override
        public int maxSize() {
            return MAX_BYTES;
        }
    };

    private static CivId civId(String value) {
        try {
            return new CivId(value);
        } catch (IllegalArgumentException e) {
            throw new WireException("civilisation id: " + e.getMessage());
        }
    }

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
