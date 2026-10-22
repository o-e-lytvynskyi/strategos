package dev.strategos.mod.server.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One tile of a map package, in the vanilla structure format (StructureTemplate.java:609 {@code save}, 706 {@code
 * load}): {@code size}, a {@code palette} of block states, {@code blocks} each with a {@code pos} and a {@code state}
 * index, no {@code entities}, and the {@code DataVersion}. Its own reader, since a template's palettes are private
 * (StructureTemplate.java:66); air in a tile, as vanilla's own saves hold it, is skipped.
 */
public final class MapTile {
    private MapTile() {}

    /** A block of the tile, at tile-local coordinates. */
    public record Placed(int x, int y, int z, BlockState state) {}

    public static CompoundTag write(int sizeX, int sizeY, int sizeZ, List<Placed> blocks) {
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        ListTag entries = new ListTag();
        for (Placed b : blocks) {
            int index = palette.computeIfAbsent(b.state(), k -> palette.size());
            CompoundTag entry = new CompoundTag();
            entry.put("pos", ints(b.x(), b.y(), b.z()));
            entry.putInt("state", index);
            entries.add(entry);
        }
        ListTag states = new ListTag();
        palette.keySet().forEach(state -> states.add(NbtUtils.writeBlockState(state)));
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(sizeX, sizeY, sizeZ));
        tag.put("palette", states);
        tag.put("blocks", entries);
        tag.put("entities", new ListTag());
        return NbtUtils.addCurrentDataVersion(tag);
    }

    /** The tile's blocks that are not air, in the tile's order. */
    public static List<Placed> read(CompoundTag tag, HolderGetter<Block> lookup) {
        ListTag states = tag.contains("palettes", Tag.TAG_LIST)
                ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
                : tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            palette.add(NbtUtils.readBlockState(lookup, states.getCompound(i)));
        }
        ListTag entries = tag.getList("blocks", Tag.TAG_COMPOUND);
        List<Placed> blocks = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            int index = entry.getInt("state");
            if (pos.size() != 3 || index < 0 || index >= palette.size()) {
                throw new IllegalArgumentException("tile block " + i + " has a bad pos or state");
            }
            BlockState state = palette.get(index);
            if (!state.isAir()) {
                blocks.add(new Placed(pos.getInt(0), pos.getInt(1), pos.getInt(2), state));
            }
        }
        return blocks;
    }

    private static ListTag ints(int a, int b, int c) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(a));
        list.add(IntTag.valueOf(b));
        list.add(IntTag.valueOf(c));
        return list;
    }
}
