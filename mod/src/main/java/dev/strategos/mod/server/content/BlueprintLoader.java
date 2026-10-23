package dev.strategos.mod.server.content;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.PlaceholderBlueprints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;

/**
 * Blueprints as vanilla structure files (BLD-01, 04 §11.7): read by our own parser, since
 * {@code StructureTemplate}'s palettes are private (StructureTemplate.java:66), and written in the same form
 * ({@code size}, {@code palette}, {@code blocks} with {@code pos} and {@code state}, as StructureTemplate.java:611–650
 * saves them). A data-mode structure block is a marker, its {@code metadata} the kind, never a block of the building.
 * Reading gives build order and damage ranks by {@link PlaceholderBlueprints#build}, so a file's block order never
 * matters; the first palette of a {@code palettes} file is used.
 */
public final class BlueprintLoader {
    private static final String STRUCTURE_BLOCK = "minecraft:structure_block";

    private BlueprintLoader() {}

    /** The blueprint {@code id} of structure {@code tag}; a malformed file throws, naming what is wrong. */
    public static Blueprint read(String id, CompoundTag tag) {
        ListTag size = tag.getList("size", Tag.TAG_INT);
        if (size.size() != 3) {
            throw new IllegalArgumentException(id + ": a structure's size is three numbers");
        }
        int sx = size.getInt(0);
        int sy = size.getInt(1);
        int sz = size.getInt(2);
        ListTag palette = tag.contains("palettes", Tag.TAG_LIST)
                ? tag.getList("palettes", Tag.TAG_LIST).getList(0)
                : tag.getList("palette", Tag.TAG_COMPOUND);
        List<String> names = new ArrayList<>();
        int[] remap = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i));
            String text = BlockStateParser.serialize(state);
            if (state.is(Blocks.STRUCTURE_BLOCK)) {
                remap[i] = -1;
                continue;
            }
            int at = names.indexOf(text);
            if (at < 0) {
                at = names.size();
                names.add(text);
            }
            remap[i] = at;
        }
        int[] cells = new int[sx * sy * sz];
        Arrays.fill(cells, -1);
        List<Blueprint.Marker> markers = new ArrayList<>();
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            int state = b.getInt("state");
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || state < 0 || state >= remap.length) {
                throw new IllegalArgumentException(id + ": block " + i + " at " + pos + " outside the structure");
            }
            if (remap[state] < 0) {
                CompoundTag nbt = b.getCompound("nbt");
                if ("DATA".equals(nbt.getString("mode"))) {
                    markers.add(new Blueprint.Marker(nbt.getString("metadata"), x, y, z));
                }
                continue;
            }
            cells[(x * sy + y) * sz + z] = remap[state];
        }
        return PlaceholderBlueprints.build(id, sx, sy, sz, names, cells, order(markers));
    }

    /** Markers in the placeholders' order: door before spawn, then the rest by kind. */
    private static List<Blueprint.Marker> order(List<Blueprint.Marker> markers) {
        List<Blueprint.Marker> out = new ArrayList<>();
        for (String kind : List.of("door", "spawn", "rally", "work")) {
            markers.stream().filter(m -> m.kind().equals(kind)).forEach(out::add);
        }
        markers.stream().filter(m -> !out.contains(m)).forEach(out::add);
        return out;
    }

    /** Blueprint {@code b} as a structure file: its palette, then a data-mode structure block for the markers. */
    public static CompoundTag write(Blueprint b) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(
                "DataVersion",
                SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(b.sizeX()));
        size.add(IntTag.valueOf(b.sizeY()));
        size.add(IntTag.valueOf(b.sizeZ()));
        tag.put("size", size);
        ListTag palette = new ListTag();
        for (String text : b.palette()) {
            palette.add(NbtUtils.writeBlockState(parse(text)));
        }
        int marker = palette.size();
        palette.add(NbtUtils.writeBlockState(
                Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.DATA)));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int k = 0; k < b.count(); k++) {
            CompoundTag block = new CompoundTag();
            block.put("pos", pos(b.x(k), b.y(k), b.z(k)));
            block.putInt("state", b.state(k));
            blocks.add(block);
        }
        for (Blueprint.Marker m : b.markers()) {
            CompoundTag block = new CompoundTag();
            block.put("pos", pos(m.x(), m.y(), m.z()));
            block.putInt("state", marker);
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", STRUCTURE_BLOCK);
            nbt.putString("mode", "DATA");
            nbt.putString("metadata", m.kind());
            block.put("nbt", nbt);
            blocks.add(block);
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag pos(int x, int y, int z) {
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        return pos;
    }

    private static BlockState parse(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false)
                    .blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("not a block state: " + text, e);
        }
    }
}
