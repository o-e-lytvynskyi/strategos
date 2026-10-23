package dev.strategos.mod.server.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.TemplateSetView;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.mod.server.net.payload.TemplateSetPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * DATA-03.7 (spec §2.7): {@code template_set} stays under its 256 KB bound for the MVP's content, taken as 300 unit
 * templates of the bundled ones' size and a little more, and the payload's codec gives back the view it was given.
 */
class TemplateSetPayloadSizeTest {

    @Test
    void threeHundredTemplatesFitAndRoundTrip() {
        TemplateSet bundled = TemplateSet.builtin();
        JsonObject walker = bundled.flattened(TemplateSet.DEBUG_WALKER);
        assertThat(walker).isNotNull();
        walker.getAsJsonObject("Identity").addProperty("classes", "Unit Infantry Organic Melee Spear Citizen Soldier");
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (int k = 0; k < 300; k++) {
            JsonObject json = walker.deepCopy();
            json.getAsJsonObject("Identity").addProperty("name", "strategos.unit.mvp_unit_number_" + k);
            flats.add(new TemplateCompiler.Flat(new TemplateId("strategos:unit/mvp_" + k), k, json));
        }
        TemplateCompiler.Result result = TemplateCompiler.fromFlattened(flats);
        TemplateSetView view = TemplateSetView.of(result.set(), result.table());

        int size = WireCodec.encode(TemplateSetView.WIRE, view).length;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        TemplateSetPayload.STREAM_CODEC.encode(buf, new TemplateSetPayload(view));
        TemplateSetPayload back = TemplateSetPayload.STREAM_CODEC.decode(buf);

        assertThat(size).as("bytes for 300 templates").isLessThan(TemplateSetView.MAX_BYTES);
        System.out.println("template_set for 300 templates: " + size + " bytes; the bundled set: "
                + WireCodec.encode(TemplateSetView.WIRE, TemplateSetView.of(bundled, TemplateIdTable.EMPTY)).length);
        assertThat(back.view()).isEqualTo(view);
        assertThat(back.view().toSet().version()).isEqualTo(result.set().version());
    }
}
