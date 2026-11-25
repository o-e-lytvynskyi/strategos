package dev.strategos.core.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import dev.strategos.core.content.RawCiv;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateIdTable;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateSetViewTest {
    @Test
    void theViewKeepsTheTechsAndTheShaSurvivesTheRoundTrip() {
        TemplateSet set = TemplateSet.builtin();
        TemplateSetView view = TemplateSetView.of(set, TemplateIdTable.EMPTY);

        assertThat(view.techs()).hasSize(10);
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

    private static TemplateSet withCivs() {
        TemplateSet builtin = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = builtin.all().stream()
                .map(t -> new TemplateCompiler.Flat(t.id(), t.netIndex(), builtin.flattened(t.id())))
                .toList();
        List<TemplateCompiler.TechFlat> techs =
                builtin.techs().stream().map(TemplateCompiler.TechFlat::of).toList();
        JsonObject tribe = new JsonObject();
        tribe.addProperty("name", "civ.t.tribe");
        JsonObject roles = new JsonObject();
        roles.addProperty("house", "strategos:building/house");
        tribe.add("roles", roles);
        return TemplateCompiler.fromFlattened(flats, techs, List.of(new RawCiv(new CivId("t:tribe"), "t:tribe", tribe)))
                .set();
    }

    @Test
    void aViewWithoutCivilisationsCarriesNoneAndTheClientGetsTheBuiltInDefault() {
        TemplateSet set = TemplateSet.builtin();
        TemplateSetView view = TemplateSetView.of(set, TemplateIdTable.EMPTY);

        assertThat(view.civs()).as("the implied default is not sent").isEmpty();
        TemplateSet back = WireCodec.decode(TemplateSetView.WIRE, WireCodec.encode(TemplateSetView.WIRE, view))
                .toSet();
        assertThat(back.version()).isEqualTo(set.version());
        assertThat(back.civs()).isEqualTo(set.civs());
        assertThat(back.civ(new CivId("strategos:default"))).isNotNull();
        assertThat(view.version()).as("hex, no mark").matches("[0-9a-f]{64}");
    }

    @Test
    void aViewWithCivilisationsKeepsTheirCanonicalJsonAndToSetReproducesTheServersVersion() {
        TemplateSet set = withCivs();
        TemplateSetView view = TemplateSetView.of(set, TemplateIdTable.EMPTY);

        assertThat(set.hasImpliedCivs()).isFalse();
        assertThat(view.civs()).extracting(c -> c.id().value()).containsExactly("t:tribe");
        TemplateSetView read = WireCodec.decode(TemplateSetView.WIRE, WireCodec.encode(TemplateSetView.WIRE, view));
        assertThat(read).isEqualTo(view);
        TemplateSet back = read.toSet();
        assertThat(back.version()).isEqualTo(set.version());
        assertThat(back.version()).isNotEqualTo(TemplateSet.builtin().version());
        assertThat(back.civs()).isEqualTo(set.civs());
    }

    @Test
    void theCivilisationMarkFollowsTheTechsMarkAndAViewWithoutTechsMarksOnlyCivilisations() {
        TemplateSetView both = TemplateSetView.of(withCivs(), TemplateIdTable.EMPTY);
        WireReader in = new WireReader(WireCodec.encode(TemplateSetView.WIRE, both));
        assertThat(in.readString(68)).endsWith("+t+c");

        TemplateSet bare = new TemplateSet(List.of(TemplateSet.builtin().byIndex(0)));
        TemplateSetView civsOnly = new TemplateSetView(
                bare.version(),
                List.of(),
                TemplateSetView.of(bare, TemplateIdTable.EMPTY).templates(),
                List.of(),
                both.civs());
        WireReader only = new WireReader(WireCodec.encode(TemplateSetView.WIRE, civsOnly));
        assertThat(only.readString(68)).endsWith("+c").doesNotEndWith("+t+c");
        assertThat(WireCodec.decode(TemplateSetView.WIRE, WireCodec.encode(TemplateSetView.WIRE, civsOnly)))
                .isEqualTo(civsOnly);
    }

    @Test
    void aViewWithoutCivilisationsRefusesToWriteAVersionThatLooksMarked() {
        TemplateSetView marked = new TemplateSetView("abc+c", List.of(), List.of());

        assertThatThrownBy(() -> WireCodec.encode(TemplateSetView.WIRE, marked))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMarkedViewWithNoCivilisationsDoesNotRead() {
        WireWriter out = new WireWriter();
        out.writeString("abc+c", 66);
        out.writeVarInt(0);
        out.writeVarInt(0);
        out.writeVarInt(0);

        assertThatThrownBy(() -> WireCodec.decode(TemplateSetView.WIRE, out.toByteArray()))
                .isInstanceOf(WireException.class);
    }

    @Test
    void civilisationJsonThatDoesNotParseIsAnIllegalArgument() {
        TemplateSetView torn = new TemplateSetView(
                "v", List.of(), List.of(), List.of(), List.of(new TemplateSetView.Civ(new CivId("t:a"), "{")));
        TemplateSetView notObject = new TemplateSetView(
                "v", List.of(), List.of(), List.of(), List.of(new TemplateSetView.Civ(new CivId("t:a"), "7")));
        TemplateSetView bad = new TemplateSetView(
                "v",
                List.of(),
                List.of(),
                List.of(),
                List.of(new TemplateSetView.Civ(new CivId("t:a"), "{\"colour\":1}")));

        for (TemplateSetView view : List.of(torn, notObject, bad)) {
            assertThatThrownBy(view::toSet).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
