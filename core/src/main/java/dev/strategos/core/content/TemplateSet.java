package dev.strategos.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * An immutable set of unit templates in net-index order, with their flattened JSON (04 §8.3): its version is the
 * SHA-256 of the canonical JSON of every template keyed by id and, when the set has techs, of the techs under the key
 * {@code $techs}, so two servers with equal content have equal versions.
 * Net indices come from the session's {@link TemplateIdTable}, so a set after a reload may leave a retired one out.
 * {@link TemplateCompiler} builds sets from data; the constructor builds one from code, as tests and M0 did.
 */
public final class TemplateSet {
    public static final TemplateId DEBUG_WALKER = new TemplateId("strategos:unit/debug_walker");
    public static final TemplateId INFANTRY = new TemplateId("strategos:unit/infantry");
    public static final TemplateId WORKER = new TemplateId("strategos:unit/worker");

    private static final TemplateSet BUILTIN = BuiltinTemplates.compile();

    private final List<UnitTemplate> templates;
    private final List<JsonObject> flattened;
    private final int[] position; // by net index: the template's place in the list, -1 for a retired index
    private final byte[] sha;
    private final ClassTokens tokens;
    private final ValuePaths valuePaths;
    private final ValuePaths tokenPaths;
    private final double[][] values; // by net index, then value path id; NaN where a template has no such number
    private final @Nullable String[][] texts; // by net index, then token path id
    private final @Nullable Blueprint[] blueprints; // by net index: a building's shape
    private final List<TechDef> techs; // ascending by net index
    private final int[] techPosition; // by tech net index: the tech's place in the list, -1 for a retired index

    /** A set from code: each template's JSON is {@link UnitTemplate#toJson()}. */
    public TemplateSet(List<UnitTemplate> templates) {
        this(templates, json(templates));
    }

    /** Templates ascending by {@code netIndex}, ids distinct; speeds and sizes positive. */
    TemplateSet(List<UnitTemplate> templates, List<JsonObject> flattened) {
        this(templates, flattened, List.of());
    }

    /** As above, with the technologies (TECH-01) ascending by net index, ids distinct. */
    TemplateSet(List<UnitTemplate> templates, List<JsonObject> flattened, List<TechDef> techs) {
        if (templates.size() != flattened.size()) {
            throw new IllegalArgumentException("one JSON per template");
        }
        Set<String> tokenNames = new LinkedHashSet<>();
        int indices = templates.stream().mapToInt(UnitTemplate::netIndex).max().orElse(-1) + 1;
        this.position = new int[Math.max(0, indices)];
        Arrays.fill(position, -1);
        for (int i = 0; i < templates.size(); i++) {
            UnitTemplate t = templates.get(i);
            if (t.netIndex() < 0
                    || (i > 0 && t.netIndex() <= templates.get(i - 1).netIndex())) {
                throw new IllegalArgumentException(t.id() + " has net index " + t.netIndex() + " at position " + i);
            }
            position[t.netIndex()] = i;
            if (!t.isBuilding()
                    && !(t.walkSpeed() > 0 && t.width() > 0 && t.height() > 0 && t.turnRate() > 0 && t.radius() > 0)) {
                throw new IllegalArgumentException(t.id() + " needs a positive speed, turn rate and size");
            }
            for (int j = 0; j < i; j++) {
                if (templates.get(j).id().equals(t.id())) {
                    throw new IllegalArgumentException(t.id() + " listed twice");
                }
            }
            tokenNames.addAll(t.classes());
        }
        this.templates = List.copyOf(templates);
        List<JsonObject> copies = new ArrayList<>();
        JsonObject canonical = new JsonObject();
        List<String> paths = new ArrayList<>(CorePaths.all());
        List<String> lists = new ArrayList<>();
        for (int i = 0; i < flattened.size(); i++) {
            JsonObject json = flattened.get(i).deepCopy();
            copies.add(json);
            canonical.add(templates.get(i).id().value(), json.deepCopy());
            leafPaths(json, "", paths, lists);
        }
        this.flattened = List.copyOf(copies);
        int techIndices = techs.stream().mapToInt(TechDef::netIndex).max().orElse(-1) + 1;
        this.techPosition = new int[Math.max(0, techIndices)];
        Arrays.fill(techPosition, -1);
        JsonObject techJson = new JsonObject();
        for (int i = 0; i < techs.size(); i++) {
            TechDef d = techs.get(i);
            if (d.netIndex() < 0 || (i > 0 && d.netIndex() <= techs.get(i - 1).netIndex())) {
                throw new IllegalArgumentException(d.id() + " has net index " + d.netIndex() + " at position " + i);
            }
            techPosition[d.netIndex()] = i;
            for (int j = 0; j < i; j++) {
                if (techs.get(j).id().equals(d.id())) {
                    throw new IllegalArgumentException(d.id() + " listed twice");
                }
            }
            techJson.add(d.id().value(), d.toJson());
        }
        this.techs = List.copyOf(techs);
        if (!techs.isEmpty()) {
            canonical.add("$techs", techJson); // no template id can be '$techs': sets without techs keep their hash
        }
        this.sha = CanonicalJson.sha256(CanonicalJson.write(canonical));
        this.tokens = new ClassTokens(tokenNames);
        this.valuePaths = new ValuePaths(paths);
        this.tokenPaths = new ValuePaths(lists);
        this.blueprints = new Blueprint[indices];
        for (int k = 0; k < templates.size(); k++) {
            JsonElement bp = copies.get(k).get("Blueprint");
            if (templates.get(k).isBuilding() && bp != null && bp.isJsonObject()) {
                List<TemplateProblem> problems = new ArrayList<>();
                blueprints[templates.get(k).netIndex()] = Blueprint.fromJson(
                        bp.getAsJsonObject(), templates.get(k).id().value(), problems);
            }
        }
        this.values = new double[indices][valuePaths.size()];
        this.texts = new String[indices][tokenPaths.size()];
        for (double[] row : values) {
            Arrays.fill(row, Double.NaN); // a retired index has no numbers
        }
        for (int k = 0; k < templates.size(); k++) {
            int i = templates.get(k).netIndex();
            JsonObject json = copies.get(k);
            for (ValuePath p : valuePaths.all()) {
                JsonElement leaf = leaf(json, p.path());
                values[i][p.id()] = leaf != null
                                && leaf.isJsonPrimitive()
                                && leaf.getAsJsonPrimitive().isNumber()
                        ? leaf.getAsDouble()
                        : typed(templates.get(k), p.path());
            }
            for (ValuePath p : tokenPaths.all()) {
                JsonElement leaf = leaf(json, p.path());
                texts[i][p.id()] = leaf != null
                                && leaf.isJsonPrimitive()
                                && leaf.getAsJsonPrimitive().isString()
                        ? leaf.getAsString()
                        : null;
            }
        }
    }

    /** {@link #DEBUG_WALKER} (net index 0, 0.25 blocks per tick) and {@link #INFANTRY}, as the bundled data defines them. */
    public static TemplateSet builtin() {
        return BUILTIN;
    }

    public @Nullable UnitTemplate byId(TemplateId id) {
        for (UnitTemplate t : templates) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    public UnitTemplate byIndex(int netIndex) {
        if (!has(netIndex)) {
            throw new IllegalArgumentException("no template with net index " + netIndex);
        }
        return templates.get(position[netIndex]);
    }

    /** Building template {@code netIndex}'s blueprint (BLD-01), or null for a unit's or a building without one. */
    public @Nullable Blueprint blueprint(int netIndex) {
        return netIndex >= 0 && netIndex < blueprints.length ? blueprints[netIndex] : null;
    }

    /** Whether a template holds {@code netIndex}: false for a retired index or one past the set's. */
    public boolean has(int netIndex) {
        return netIndex >= 0 && netIndex < position.length && position[netIndex] >= 0;
    }

    /** One past the highest net index: the size of anything indexed by template. */
    public int indexCount() {
        return position.length;
    }

    /** In net-index order. */
    public List<UnitTemplate> all() {
        return templates;
    }

    /** The technologies (TECH-01) in net-index order. */
    public List<TechDef> techs() {
        return techs;
    }

    public @Nullable TechDef tech(TechId id) {
        for (TechDef t : techs) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    /** The tech holding {@code netIndex}, or null for a retired or unknown index. */
    public @Nullable TechDef techByIndex(int netIndex) {
        return netIndex >= 0 && netIndex < techPosition.length && techPosition[netIndex] >= 0
                ? techs.get(techPosition[netIndex])
                : null;
    }

    /** A copy of a template's flattened JSON, or null for an unknown id. */
    public @Nullable JsonObject flattened(TemplateId id) {
        UnitTemplate t = byId(id);
        return t == null ? null : flattened.get(position[t.netIndex()]).deepCopy();
    }

    /** The SHA-256 of the canonical JSON of every template. */
    public byte[] sha() {
        return sha.clone();
    }

    /** {@link #sha()} in hex. */
    public String version() {
        StringBuilder sb = new StringBuilder();
        for (byte b : sha) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** The version's first 8 bytes: part of a match's identity in journals and saves. */
    public long hash() {
        long h = 0;
        for (int i = 0; i < 8; i++) {
            h = (h << 8) | (sha[i] & 0xFF);
        }
        return h;
    }

    public ClassTokens tokens() {
        return tokens;
    }

    /** The numbers templates carry, and the core's own paths ({@link CorePaths}) even where none does. */
    public ValuePaths valuePaths() {
        return valuePaths;
    }

    /** The token lists templates carry: string leaves of space-separated tokens, such as {@code Identity/classes}. */
    public ValuePaths tokenPaths() {
        return tokenPaths;
    }

    /** Template {@code netIndex}'s number at value path {@code path}, the typed default for a core path, else NaN. */
    public double value(int netIndex, int path) {
        return values[netIndex][path];
    }

    /** Template {@code netIndex}'s token list at token path {@code path}, or null when it has none. */
    public @Nullable String text(int netIndex, int path) {
        return texts[netIndex][path];
    }

    @Override
    public String toString() {
        return "TemplateSet[" + templates.size() + " templates, " + Hex64.format(hash()) + "]";
    }

    private static List<JsonObject> json(List<UnitTemplate> templates) {
        List<JsonObject> list = new ArrayList<>();
        for (UnitTemplate t : templates) {
            list.add(t.toJson());
        }
        return list;
    }

    /** Every numeric leaf's path, such as {@code UnitMotion/walkSpeed}, and every string leaf's. */
    static void leafPaths(JsonObject json, String prefix, List<String> numbers, List<String> strings) {
        Map<String, JsonElement> sorted = new TreeMap<>(json.asMap());
        for (Map.Entry<String, JsonElement> e : sorted.entrySet()) {
            String path = prefix.isEmpty() ? e.getKey() : prefix + "/" + e.getKey();
            if (e.getValue().isJsonObject()) {
                leafPaths(e.getValue().getAsJsonObject(), path, numbers, strings);
            } else if (e.getValue().isJsonPrimitive()
                    && e.getValue().getAsJsonPrimitive().isNumber()) {
                numbers.add(path);
            } else if (e.getValue().isJsonPrimitive()
                    && e.getValue().getAsJsonPrimitive().isString()) {
                strings.add(path);
            }
        }
    }

    private static @Nullable JsonElement leaf(JsonObject json, String path) {
        JsonElement at = json;
        for (String key : path.split("/", -1)) {
            if (!at.isJsonObject()) {
                return null;
            }
            at = at.getAsJsonObject().get(key);
            if (at == null) {
                return null;
            }
        }
        return at;
    }

    /** A core path a template's JSON leaves out has the typed template's default. */
    private static double typed(UnitTemplate t, String path) {
        return switch (path) {
            case CorePaths.WALK_SPEED -> t.walkSpeed();
            case CorePaths.TURN_RATE -> t.turnRate();
            case CorePaths.HEALTH -> CorePaths.DEFAULT_HEALTH;
            case CorePaths.RESIST_HACK,
                    CorePaths.RESIST_PIERCE,
                    CorePaths.RESIST_CRUSH,
                    CorePaths.MELEE_HACK,
                    CorePaths.MELEE_PIERCE,
                    CorePaths.MELEE_CRUSH,
                    CorePaths.FLEE -> 0;
            case CorePaths.MELEE_REACH -> CorePaths.DEFAULT_REACH;
            case CorePaths.MELEE_PREPARE -> CorePaths.DEFAULT_PREPARE;
            case CorePaths.MELEE_REPEAT -> CorePaths.DEFAULT_REPEAT;
            case CorePaths.VISION -> CorePaths.DEFAULT_VISION;
            default -> Double.NaN;
        };
    }
}
