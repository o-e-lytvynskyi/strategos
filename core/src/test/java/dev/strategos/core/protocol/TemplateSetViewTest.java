package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateSetViewTest {
    @Test
    void theViewKeepsTheTechsAndTheShaSurvivesTheRoundTrip() {
        TemplateSet set = TemplateSet.builtin();
        TemplateSetView view = TemplateSetView.of(set, TemplateIdTable.EMPTY);

        assertThat(view.techs()).hasSize(3);
        assertThat(view.techs().get(0).id().value()).isEqualTo("strategos:gather_wood_1");
        TemplateSet back = view.toSet();
        assertThat(back.version()).isEqualTo(set.version());
        assertThat(back.techs()).isEqualTo(set.techs());

        byte[] bytes = WireCodec.encode(TemplateSetView.WIRE, view);
        TemplateSetView read = WireCodec.decode(TemplateSetView.WIRE, bytes);
        assertThat(read).isEqualTo(view);
        assertThat(read.toSet().version()).isEqualTo(set.version());
    }

    @Test
    void aViewWrittenWithoutTechsIsTheOldWireFormAndReadsWithNone() {
        TemplateSet set = new TemplateSet(List.of(TemplateSet.builtin().byIndex(0)));
        TemplateSetView view = TemplateSetView.of(set, TemplateIdTable.EMPTY);
        byte[] bytes = WireCodec.encode(TemplateSetView.WIRE, view);

        // The form before TECH-01: version, table, templates, nothing after.
        WireWriter old = new WireWriter();
        old.writeString(view.version(), 64);
        old.writeVarInt(view.table().size());
        old.writeVarInt(view.templates().size());
        for (TemplateSetView.Template t : view.templates()) {
            old.writeVarInt(t.netIndex());
            old.writeString(t.id().value(), 256);
            old.writeString(t.json(), 64 * 1024);
        }
        assertThat(bytes).as("a set without techs writes the old bytes").isEqualTo(old.toByteArray());

        TemplateSetView read = WireCodec.decode(TemplateSetView.WIRE, old.toByteArray());
        assertThat(read.techs()).isEmpty();
        assertThat(read.toSet().version()).isEqualTo(set.version());
        assertThat(read.toSet().all())
                .extracting(UnitTemplate::id)
                .containsExactly(set.byIndex(0).id());
    }

    @Test
    void viewsAreReadBackToBackFromOneStream() {
        TemplateSetView with = TemplateSetView.of(TemplateSet.builtin(), TemplateIdTable.EMPTY);
        TemplateSetView without = TemplateSetView.of(new TemplateSet(List.of()), TemplateIdTable.EMPTY);
        WireWriter out = new WireWriter();
        TemplateSetView.WIRE.write(out, without);
        TemplateSetView.WIRE.write(out, with);
        TemplateSetView.WIRE.write(out, without);

        WireReader in = new WireReader(out.toByteArray());
        assertThat(TemplateSetView.WIRE.read(in)).isEqualTo(without);
        assertThat(TemplateSetView.WIRE.read(in)).isEqualTo(with);
        assertThat(TemplateSetView.WIRE.read(in)).isEqualTo(without);
        in.expectEnd();
    }

    @Test
    void aViewWithoutTechsRefusesToWriteAVersionThatLooksMarked() {
        TemplateSetView marked = new TemplateSetView("abc+t", List.of(), List.of());

        assertThatThrownBy(() -> WireCodec.encode(TemplateSetView.WIRE, marked))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMarkedViewWithNoTechsDoesNotRead() {
        WireWriter out = new WireWriter();
        out.writeString("abc+t", 66);
        out.writeVarInt(0);
        out.writeVarInt(0);
        out.writeVarInt(0);

        assertThatThrownBy(() -> WireCodec.decode(TemplateSetView.WIRE, out.toByteArray()))
                .isInstanceOf(WireException.class);
    }

    @Test
    void jsonThatDoesNotParseOrIsNoObjectIsAnIllegalArgument() {
        TemplateSetView torn = new TemplateSetView(
                "v",
                List.of(),
                List.of(new TemplateSetView.Template(0, new TemplateId("t:unit/a"), "{ \"Identity\": ")));
        TemplateSetView notObject = new TemplateSetView(
                "v", List.of(), List.of(new TemplateSetView.Template(0, new TemplateId("t:unit/a"), "[1]")));
        TemplateSetView tornTech = new TemplateSetView(
                "v", List.of(), List.of(), List.of(new TemplateSetView.Tech(0, new TechId("t:a"), "{")));
        TemplateSetView techNotObject = new TemplateSetView(
                "v", List.of(), List.of(), List.of(new TemplateSetView.Tech(0, new TechId("t:a"), "7")));

        for (TemplateSetView view : List.of(torn, notObject, tornTech, techNotObject)) {
            assertThatThrownBy(view::toSet).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
