package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * A computer player announces its next wave (AI-17): it musters at match-local ({@code x}, {@code z}) and leaves in
 * {@code ticks} ticks, at least 30 seconds; the other factions' players hear it. Only a faction a computer plays sends
 * one.
 */
public record AnnounceWave(int x, int z, int ticks) implements Command {
    /** The least warning: 30 seconds. */
    public static final int LEAST = 600;

    static final MapCodec<AnnounceWave> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.INT.fieldOf("x").forGetter(AnnounceWave::x),
                    Codec.INT.fieldOf("z").forGetter(AnnounceWave::z),
                    Codec.INT.fieldOf("ticks").forGetter(AnnounceWave::ticks))
            .apply(i, AnnounceWave::new));

    /** Where and when; wire id 17. */
    static final WireCodec<AnnounceWave> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, AnnounceWave value) {
            out.writeZigZag(value.x()).writeZigZag(value.z()).writeVarInt(value.ticks());
        }

        @Override
        public AnnounceWave read(WireReader in) {
            int x = in.readZigZagInt();
            int z = in.readZigZagInt();
            return new AnnounceWave(x, z, in.readVarInt());
        }

        @Override
        public int maxSize() {
            return 15;
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 17); // the command type: ... 16 RuleChange, 17 AnnounceWave
        h.putInt(x).putInt(z).putInt(ticks);
    }
}
