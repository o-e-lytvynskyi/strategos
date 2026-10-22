package dev.strategos.core.sim.journal;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.SliceSource;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.nav.PageCoord;

/**
 * Every journaled input as a one-byte kind and its body: 1 a command envelope; 2 a terrain page, as its page
 * coordinates and its slice's hash, the slice itself in the segment's 'L' records and the page rebuilt from it when
 * read; 6 damage a body took, as its handle, kind, amount and source kind (3 to 5 are the spec's later inputs).
 */
final class InputCodec {
    private static final int COMMAND = 1;
    private static final int TERRAIN_PAGE = 2;
    private static final int EXTERNAL_DAMAGE = 6;

    private InputCodec() {}

    static void write(WireWriter out, InputEvent value) {
        switch (value) {
            case CommandEnvelope envelope -> {
                out.writeByte(COMMAND);
                CommandEnvelope.WIRE.write(out, envelope);
            }
            case TerrainPage page -> {
                out.writeByte(TERRAIN_PAGE);
                out.writeVarInt(page.coord().x());
                out.writeVarInt(page.coord().z());
                out.writeLong(page.sliceHash());
            }
            case ExternalDamage damage -> {
                out.writeByte(EXTERNAL_DAMAGE);
                out.writeInt(damage.handle());
                out.writeByte(damage.kind().code());
                out.writeFloat(damage.amount());
                out.writeByte(damage.sourceKind().code());
            }
        }
    }

    static InputEvent read(WireReader in, SliceSource slices) {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case COMMAND -> CommandEnvelope.WIRE.read(in);
            case TERRAIN_PAGE -> {
                PageCoord coord = new PageCoord(in.readVarInt(), in.readVarInt());
                long hash = in.readLong();
                byte[] slice = slices.slice(hash);
                if (slice == null) {
                    throw new WireException("terrain page " + coord + " names slice " + Long.toHexString(hash)
                            + ", which no 'L' record of its segment holds");
                }
                TerrainPage page = TerrainPage.rebuild(coord, slice);
                if (page.sliceHash() != hash) {
                    throw new WireException(
                            "the slice of terrain page " + coord + " does not hash to " + Long.toHexString(hash));
                }
                yield page;
            }
            case EXTERNAL_DAMAGE -> {
                int handle = in.readInt();
                int damageKind = in.readUnsignedByte();
                float amount = in.readFloat();
                int source = in.readUnsignedByte();
                try {
                    yield new ExternalDamage(
                            handle,
                            ExternalDamage.Kind.ofCode(damageKind),
                            amount,
                            ExternalDamage.SourceKind.ofCode(source));
                } catch (IllegalArgumentException e) {
                    throw new WireException("external damage: " + e.getMessage());
                }
            }
            default -> throw new WireException("unknown input kind " + kind);
        };
    }
}
