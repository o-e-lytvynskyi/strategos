package dev.strategos.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A building's shape as the core reads it (BLD-01, 04 §11.7): its size, a palette of block states as Minecraft writes
 * them, its blocks in build order (Y up, then X, then Z), each block's damage rank (-1 for never removed by damage: the
 * two lowest layers of the outer ring), and its markers (spawn, door, rally, work), all in blueprint coordinates. It
 * lives in a building template's flattened JSON under {@code Blueprint}, so the set's version, the journal and the
 * client copy carry it; the mod writes and reads the same shape as a vanilla {@code .nbt} structure.
 */
@CoreApi
public final class Blueprint {
    /** Whether a data-mode structure block may name {@code kind}: spawn, door, rally or work. */
    public static boolean markerKind(String kind) {
        return switch (kind) {
            case "spawn", "door", "rally", "work" -> true;
            default -> false;
        };
    }

    /** A marker: its kind and where it is, in blueprint coordinates. */
    @CoreApi
    public record Marker(String kind, int x, int y, int z) {}

    private final String id;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<String> palette;
    private final int[] blocks;
    private final int[] ranks;
    private final List<Marker> markers;

    /** {@code blocks} four numbers a block (x, y, z, palette index) in build order; {@code ranks} one each. */
    public Blueprint(
            String id,
            int sizeX,
            int sizeY,
            int sizeZ,
            List<String> palette,
            int[] blocks,
            int[] ranks,
            List<Marker> markers) {
        if (blocks.length % 4 != 0 || ranks.length != blocks.length / 4) {
            throw new IllegalArgumentException(
                    id + ": " + blocks.length + " block numbers and " + ranks.length + " ranks");
        }
        this.id = id;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette);
        this.blocks = blocks.clone();
        this.ranks = ranks.clone();
        this.markers = List.copyOf(markers);
    }

    public String id() {
        return id;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public List<String> palette() {
        return palette;
    }

    public List<Marker> markers() {
        return markers;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Blueprint b
                && b.id.equals(id)
                && b.sizeX == sizeX
                && b.sizeY == sizeY
                && b.sizeZ == sizeZ
                && b.palette.equals(palette)
                && Arrays.equals(b.blocks, blocks)
                && Arrays.equals(b.ranks, ranks)
                && b.markers.equals(markers);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + Arrays.hashCode(blocks);
    }

    @Override
    public String toString() {
        return "Blueprint[" + id + ", " + sizeX + " x " + sizeY + " x " + sizeZ + ", " + count() + " blocks]";
    }

    /** How many blocks the blueprint places. */
    public int count() {
        return ranks.length;
    }

    public int x(int block) {
        return blocks[block * 4];
    }

    public int y(int block) {
        return blocks[block * 4 + 1];
    }

    public int z(int block) {
        return blocks[block * 4 + 2];
    }

    /** The block's palette index. */
    public int state(int block) {
        return blocks[block * 4 + 3];
    }

    public int rank(int block) {
        return ranks[block];
    }

    /** The first marker of {@code kind}, or null. */
    public @Nullable Marker marker(String kind) {
        for (Marker m : markers) {
            if (m.kind().equals(kind)) {
                return m;
            }
        }
        return null;
    }

    /** Whether column (x, z) of the blueprint holds a block: the footprint. */
    public boolean covers(int x, int z) {
        for (int b = 0; b < count(); b++) {
            if (x(b) == x && z(b) == z) {
                return true;
            }
        }
        return false;
    }

    /** The blueprint as its JSON object, the form templates carry. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        JsonArray size = new JsonArray();
        size.add(sizeX);
        size.add(sizeY);
        size.add(sizeZ);
        json.add("size", size);
        JsonArray p = new JsonArray();
        palette.forEach(p::add);
        json.add("palette", p);
        JsonArray b = new JsonArray();
        for (int v : blocks) {
            b.add(v);
        }
        json.add("blocks", b);
        JsonArray r = new JsonArray();
        for (int v : ranks) {
            r.add(v);
        }
        json.add("ranks", r);
        JsonArray m = new JsonArray();
        for (Marker marker : markers) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", marker.kind());
            JsonArray pos = new JsonArray();
            pos.add(marker.x());
            pos.add(marker.y());
            pos.add(marker.z());
            o.add("pos", pos);
            m.add(o);
        }
        json.add("markers", m);
        return json;
    }

    /** The blueprint of a template's {@code Blueprint} object; problems go to the list, and a bad one is null. */
    public static @Nullable Blueprint fromJson(JsonObject json, String file, List<TemplateProblem> problems) {
        try {
            JsonArray size = json.getAsJsonArray("size");
            List<String> palette = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("palette")) {
                palette.add(e.getAsString());
            }
            JsonArray b = json.getAsJsonArray("blocks");
            int[] blocks = new int[b.size()];
            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = b.get(i).getAsInt();
            }
            JsonArray r = json.getAsJsonArray("ranks");
            int[] ranks = new int[r.size()];
            for (int i = 0; i < ranks.length; i++) {
                ranks[i] = r.get(i).getAsInt();
            }
            List<Marker> markers = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("markers")) {
                JsonObject o = e.getAsJsonObject();
                JsonArray pos = o.getAsJsonArray("pos");
                markers.add(new Marker(
                        o.get("kind").getAsString(),
                        pos.get(0).getAsInt(),
                        pos.get(1).getAsInt(),
                        pos.get(2).getAsInt()));
            }
            Blueprint bp = new Blueprint(
                    json.get("id").getAsString(),
                    size.get(0).getAsInt(),
                    size.get(1).getAsInt(),
                    size.get(2).getAsInt(),
                    palette,
                    blocks,
                    ranks,
                    markers);
            for (int k = 0; k < bp.count(); k++) {
                if (bp.state(k) < 0 || bp.state(k) >= palette.size()) {
                    problems.add(TemplateProblem.error(
                            file,
                            "/Blueprint/blocks",
                            "block " + k + " names palette entry " + bp.state(k) + " of " + palette.size()));
                    return null;
                }
            }
            return bp;
        } catch (RuntimeException e) {
            problems.add(TemplateProblem.error(file, "/Blueprint", "a malformed blueprint: " + e.getMessage()));
            return null;
        }
    }
}
