package dev.strategos.core.sim.journal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.FactionPresentation;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.sim.PresentationSetup;
import dev.strategos.core.sim.SliceSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresentationInputCodecTest {
    @Test
    void firstTickPresentationUsesANewTagWithoutReusingDamageProvenanceTagTen() {
        var input = new PresentationSetup(
                new FactionPresentation(List.of(new FactionPresentation.Style("strategos:placeholder", 14))));
        var out = new WireWriter(4096);
        InputCodec.write(out, input);
        byte[] bytes = out.toByteArray();
        assertThat(Byte.toUnsignedInt(bytes[0])).isEqualTo(11);
        assertThat(InputCodec.read(new WireReader(bytes), SliceSource.NONE)).isEqualTo(input);
    }
}
