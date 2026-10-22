package dev.strategos.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.shared.Hex64;
import dev.strategos.core.shared.TemplateId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * An immutable set of unit templates in net-index order, with their flattened JSON (04 §8.3): its version is the
 * SHA-256 of the canonical JSON of every template keyed by id, so two servers with equal content have equal versions.
 * {@link TemplateCompiler} builds sets from data; the constructor builds one from code, as tests and M0 did.
 */
public final class TemplateSet {
    public static final TemplateId DEBUG_WALKER = new TemplateId("strategos:unit/debug_walker");
    public static final TemplateId INFANTRY = new TemplateId("strategos:unit/infantry");

    private static final TemplateSet BUILTIN = BuiltinTemplates.compile();

    private final List<UnitTemplate> templates;
    private final List<JsonObject> flattened;
    private final byte[] sha;
    private final ClassTokens tokens;
    private final ValuePaths valuePaths;
    private final ValuePaths tokenPaths;
    private final double[][] values; // by net index, then value path id; NaN where a template has no such number
    private final @Nullable String[][] texts; // by net index, then token path id

    /** A set from code: each template's JSON is {@link UnitTemplate#toJson()}. */
    public TemplateSet(List<UnitTemplate> templates) {
        this(templates, json(templates));
    }

    /** Each template's {@code netIndex} must be its position; ids must be distinct; speeds and sizes positive. */
    TemplateSet(List<UnitTemplate> templates, List<JsonObject> flattened) {
        if (templates.size() != flattened.size()) {
            throw new IllegalArgumentException("one JSON per template");
        }
        Set<String> tokenNames = new LinkedHashSet<>();
        for (int i = 0; i < templates.size(); i++) {
            UnitTemplate t = templates.get(i);
            if (t.netIndex() != i) {
                throw new IllegalArgumentException(t.id() + " has net index " + t.netIndex() + " at position " + i);
            }
            if (!(t.walkSpeed() > 0 && t.width() > 0 && t.height() > 0 && t.turnRate() > 0 && t.radius() > 0)) {
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
        List<String> paths = new ArrayList<>(List.of(CorePaths.WALK_SPEED, CorePaths.TURN_RATE));
        List<String> lists = new ArrayList<>();
        for (int i = 0; i < flattened.size(); i++) {
            JsonObject json = flattened.get(i).deepCopy();
            copies.add(json);
            canonical.add(templates.get(i).id().value(), json.deepCopy());
            leafPaths(json, "", paths, lists);
        }
        this.flattened = List.copyOf(copies);
        this.sha = CanonicalJson.sha256(CanonicalJson.write(canonical));
        this.tokens = new ClassTokens(tokenNames);
        this.valuePaths = new ValuePaths(paths);
        this.tokenPaths = new ValuePaths(lists);
        this.values = new double[templates.size()][valuePaths.size()];
        this.texts = new String[templates.size()][tokenPaths.size()];
        for (int i = 0; i < templates.size(); i++) {
            JsonObject json = copies.get(i);
            for (ValuePath p : valuePaths.all()) {
                JsonElement leaf = leaf(json, p.path());
                values[i][p.id()] = leaf != null
                                && leaf.isJsonPrimitive()
                                && leaf.getAsJsonPrimitive().isNumber()
                        ? leaf.getAsDouble()
                        : typed(templates.get(i), p.path());
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
        if (netIndex < 0 || netIndex >= templates.size()) {
            throw new IllegalArgumentException("no template with net index " + netIndex);
        }
        return templates.get(netIndex);
    }

    /** In net-index order. */
    public List<UnitTemplate> all() {
        return templates;
    }

    /** A copy of a template's flattened JSON, or null for an unknown id. */
    public @Nullable JsonObject flattened(TemplateId id) {
        UnitTemplate t = byId(id);
        return t == null ? null : flattened.get(t.netIndex()).deepCopy();
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
    private static void leafPaths(JsonObject json, String prefix, List<String> numbers, List<String> strings) {
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
            default -> Double.NaN;
        };
    }
}
