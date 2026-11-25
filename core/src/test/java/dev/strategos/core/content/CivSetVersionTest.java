package dev.strategos.core.content;

import static dev.strategos.core.content.CivTestSupport.MINIMAL;
import static dev.strategos.core.content.CivTestSupport.civ;
import static dev.strategos.core.content.CivTestSupport.compile;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.strategos.core.shared.CivId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-01: the implied default leaves every existing version alone ({@link TemplateSetTest} pins the built-in set's), and
 * any other civilisation, or a changed default, is part of the version.
 */
class CivSetVersionTest {
    private static String defaultJson() {
        return BuiltinTemplates.DEFAULT_CIV.toJson().toString();
    }

    @Test
    void theBuiltInSetAndASetWithOnlyTheDefaultKeepOneVersionAndHaveNoCivsEntry() {
        TemplateSet builtin = TemplateSet.builtin();
        TemplateSet none = compile().set();
        TemplateSet explicit = compile(civ("strategos:default", defaultJson())).set();

        assertThat(builtin.hasImpliedCivs()).isTrue();
        assertThat(none.hasImpliedCivs()).isTrue();
        assertThat(explicit.hasImpliedCivs()).isTrue();
        assertThat(none.version()).isEqualTo(builtin.version());
        assertThat(explicit.version()).isEqualTo(builtin.version());
        assertThat(explicit.hash()).isEqualTo(builtin.hash());
        assertThat(BuiltinTemplates.compile(false).hasImpliedCivs()).isTrue();
    }

    @Test
    void aSecondCivilisationAddsCivsAndChangesTheVersion() {
        TemplateSet builtin = TemplateSet.builtin();
        TemplateSet two = compile(civ("strategos:default", defaultJson()), civ("t:tribe", MINIMAL))
                .set();

        assertThat(two.hasImpliedCivs()).isFalse();
        assertThat(two.civs()).hasSize(2);
        assertThat(two.version()).isNotEqualTo(builtin.version());
        assertThat(two.all()).as("the templates are the same").isEqualTo(builtin.all());
    }

    @Test
    void aChangedDefaultOrAnotherCivilisationAloneIsPartOfTheVersion() {
        JsonObject renamed = BuiltinTemplates.DEFAULT_CIV.toJson();
        renamed.addProperty("name", "civ.t.renamed");
        TemplateSet changed =
                compile(civ("strategos:default", renamed.toString())).set();
        TemplateSet other = compile(civ("t:tribe", MINIMAL)).set();
        TemplateSet otherAgain = compile(civ("t:tribe", MINIMAL)).set();
        TemplateSet differs = compile(civ("t:tribe", "{\"name\":\"civ.t.other\"," + MINIMAL.substring(1)))
                .set();

        assertThat(changed.hasImpliedCivs()).isFalse();
        assertThat(changed.version()).isNotEqualTo(TemplateSet.builtin().version());
        assertThat(other.civs()).extracting(CivDef::id).containsExactly(new CivId("t:tribe"));
        assertThat(other.version()).isNotEqualTo(TemplateSet.builtin().version());
        assertThat(otherAgain.version()).as("equal content, equal version").isEqualTo(other.version());
        assertThat(differs.version()).isNotEqualTo(other.version());
    }

    @Test
    void theOrderCivilisationsAreDeclaredInDoesNotMatter() {
        TemplateSet ab = compile(civ("t:a", MINIMAL), civ("t:b", MINIMAL)).set();
        TemplateSet ba = compile(civ("t:b", MINIMAL), civ("t:a", MINIMAL)).set();

        assertThat(ab.version()).isEqualTo(ba.version());
        assertThat(ab.civs()).isEqualTo(ba.civs());
    }

    @Test
    void aCivilisationListedTwiceInACodeBuiltSetIsRefused() {
        CivDef tribe = compile(civ("t:tribe", MINIMAL)).set().civs().get(0);
        TemplateSet builtin = TemplateSet.builtin();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new TemplateSet(builtin.all(), List.of(), builtin.techs(), List.of(tribe, tribe)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
