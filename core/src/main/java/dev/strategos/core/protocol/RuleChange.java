package dev.strategos.core.protocol;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.Hasher;

/**
 * Admin only (MATCH-01): match rule {@code key} takes {@code value} from the next step on, journaled like every command
 * (`/strategos match config`). The keys: {@code populationMax} (1 to 1,000), {@code victory} ({@code none} or {@code
 * civic_centre}), {@code revealMap} and {@code aiVision} ({@code true} or {@code false}).
 */
public record RuleChange(String key, String value) implements Command {
    /** The most bytes of a key or a value on the wire. */
    static final int BYTES = 32;

    static final MapCodec<RuleChange> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.fieldOf("key").forGetter(RuleChange::key),
                    Codec.STRING.fieldOf("value").forGetter(RuleChange::value))
            .apply(i, RuleChange::new));

    /** Key, value; wire id 16. */
    static final WireCodec<RuleChange> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, RuleChange value) {
            out.writeString(value.key(), BYTES);
            out.writeString(value.value(), BYTES);
        }

        @Override
        public RuleChange read(WireReader in) {
            String key = in.readString(BYTES);
            return new RuleChange(key, in.readString(BYTES));
        }

        @Override
        public int maxSize() {
            return 2 * (5 + BYTES);
        }
    };

    @Override
    public void hashInto(Hasher h) {
        h.putByte((byte) 16); // the command type: ... 15 Resign, 16 RuleChange
        h.putString(key).putString(value);
    }
}
