package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.SliceSource;
import org.junit.jupiter.api.Test;

class AttributedDamageInputCodecTest {
    @Test
    void attributedDamageUsesItsOwnAppendOnlyTagAndRetainsTheSourceFaction() {
        ExternalDamage damage =
                new ExternalDamage(1_048_576, ExternalDamage.Kind.CRUSH, 12, ExternalDamage.SourceKind.PLAYER, 0);
        WireWriter out = new WireWriter(16);
        InputCodec.write(out, damage);
        byte[] bytes = out.toByteArray();
        assertThat(Byte.toUnsignedInt(bytes[0])).isEqualTo(10);
        assertThat(InputCodec.read(new WireReader(bytes), SliceSource.NONE)).isEqualTo(damage);
    }

    @Test
    void legacyUnattributedDamageKeepsItsOriginalTagAndElevenByteLayout() {
        ExternalDamage damage =
                new ExternalDamage(1_048_576, ExternalDamage.Kind.HACK, 10, ExternalDamage.SourceKind.MOB);
        WireWriter out = new WireWriter(16);
        InputCodec.write(out, damage);
        byte[] bytes = out.toByteArray();
        assertThat(bytes).hasSize(11);
        assertThat(Byte.toUnsignedInt(bytes[0])).isEqualTo(6);
        assertThat(InputCodec.read(new WireReader(bytes), SliceSource.NONE)).isEqualTo(damage);
    }

    @Test
    void attributedSourcesAreBoundedAndTruncationCannotBecomeWorldDamage() {
        for (int faction : new int[] {17, Integer.MAX_VALUE}) {
            WireWriter out = header();
            out.writeVarInt(faction);
            assertThatThrownBy(() -> InputCodec.read(new WireReader(out.toByteArray()), SliceSource.NONE))
                    .isInstanceOf(WireException.class);
        }
        WireWriter truncated = header();
        assertThatThrownBy(() -> InputCodec.read(new WireReader(truncated.toByteArray()), SliceSource.NONE))
                .isInstanceOf(WireException.class);
    }

    private static WireWriter header() {
        return new WireWriter(16)
                .writeByte(10)
                .writeInt(1_048_576)
                .writeByte(ExternalDamage.Kind.HACK.code())
                .writeFloat(10)
                .writeByte(ExternalDamage.SourceKind.PLAYER.code());
    }
}
