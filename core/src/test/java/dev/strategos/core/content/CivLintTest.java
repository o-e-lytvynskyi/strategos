package dev.strategos.core.content;

import static dev.strategos.core.content.CivTestSupport.MINIMAL;
import static dev.strategos.core.content.CivTestSupport.civ;
import static dev.strategos.core.content.CivTestSupport.compile;
import static dev.strategos.core.content.CivTestSupport.problems;
import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.shared.CivId;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/** CIV-01: what a civilisation file may not say, each problem naming its file and JSON pointer. */
class CivLintTest {
    private static final String FILE = "data/t/strategos/civs/probe.json";
    private static final String HOUSE = "\"roles\":{\"house\":\"strategos:building/house\"}";

    private static void assertOnly(List<TemplateProblem> problems, String pointer, String messagePart) {
        assertThat(problems)
                .extracting(TemplateProblem::file, TemplateProblem::pointer)
                .containsExactly(Tuple.tuple(FILE, pointer));
        assertThat(problems.get(0).message()).contains(messagePart);
    }

    @Test
    void aCleanCivilisationHasNoProblem() {
        assertThat(problems(MINIMAL)).isEmpty();
    }

    @Test
    void anUnknownKeyIsReportedAtEveryLevel() {
        assertOnly(problems("{" + HOUSE + ",\"colour\":\"red\"}"), "/colour", "unknown key");
        assertOnly(problems("{" + HOUSE + ",\"roster\":{\"unit\":[]}}"), "/roster/unit", "unknown key");
        assertOnly(problems("{" + HOUSE + ",\"start\":{\"gold\":1}}"), "/start/gold", "unknown key");
        assertOnly(
                problems("{" + HOUSE + ",\"start\":{\"entities\":[{\"role\":\"house\",\"y\":1}]}}"),
                "/start/entities/0/y",
                "unknown key");
        assertOnly(problems("{" + HOUSE + ",\"ai\":{\"level\":1}}"), "/ai/level", "unknown key");
    }

    @Test
    void aRosterIdThatDoesNotExistIsAnError() {
        assertOnly(
                problems("{" + HOUSE + ",\"roster\":{\"templates\":[\"strategos:building/house\",\"t:ghost\"]}}"),
                "/roster/templates",
                "unknown template 't:ghost'");
        assertOnly(
                problems("{" + HOUSE + ",\"roster\":{\"techs\":[\"t:ghost\"]}}"),
                "/roster/techs",
                "unknown tech 't:ghost'");
    }

    @Test
    void aRoleNamingNoTemplateOrOneOutsideTheRosterIsAnError() {
        assertOnly(
                problems("{\"roles\":{\"house\":\"strategos:building/house\",\"worker\":\"t:ghost\"}}"),
                "/roles/worker",
                "names no template");
        assertOnly(
                problems("{\"roster\":{\"templates\":[\"strategos:unit/worker\"]},"
                        + "\"roles\":{\"house\":\"strategos:building/house\"}}"),
                "/roles/house",
                "not in the roster");
    }

    @Test
    void aStartKitEntityNeedsAKnownRoleOrTemplateAndExactlyOneOfThem() {
        assertOnly(
                problems("{" + HOUSE + ",\"start\":{\"entities\":[{\"role\":\"worker\"}]}}"),
                "/start/entities/0/role",
                "unknown role");
        assertOnly(
                problems("{" + HOUSE + ",\"start\":{\"entities\":[{\"template\":\"t:ghost\"}]}}"),
                "/start/entities/0/template",
                "unknown template");
        assertOnly(
                problems("{" + HOUSE + ",\"start\":{\"entities\":[{\"role\":\"house\","
                        + "\"template\":\"strategos:building/house\"}]}}"),
                "/start/entities/0",
                "not both or neither");
        assertOnly(
                problems("{" + HOUSE + ",\"start\":{\"entities\":[{\"dx\":3}]}}"),
                "/start/entities/0",
                "not both or neither");
        assertOnly(
                problems("{\"roster\":{\"templates\":[\"strategos:building/house\"]}," + HOUSE
                        + ",\"start\":{\"entities\":[{\"template\":\"strategos:unit/worker\"}]}}"),
                "/start/entities/0/template",
                "not in the roster");
        assertOnly(
                problems("{" + HOUSE + ",\"start\":{\"entities\":[{\"role\":\"house\",\"count\":0}]}}"),
                "/start/entities/0/count",
                "whole number");
        assertOnly(
                problems("{" + HOUSE + ",\"start\":{\"resources\":{\"food\":-5}}}"),
                "/start/resources/food",
                "whole amount");
    }

    @Test
    void aBonusNeedsAKnownPathAndOp() {
        assertOnly(
                problems("{" + HOUSE + ",\"bonuses\":[{\"path\":\"No/such/number\",\"op\":\"mul\",\"value\":2}]}"),
                "/bonuses/0/path",
                "no template has a number");
        assertOnly(
                problems(
                        "{" + HOUSE + ",\"bonuses\":[{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"pow\",\"value\":2}]}"),
                "/bonuses/0/op",
                "unknown op");
        assertOnly(
                problems("{" + HOUSE + ",\"bonuses\":[{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"mul\"}]}"),
                "/bonuses/0/value",
                "needs a number");
        assertThat(problems("{" + HOUSE + ",\"bonuses\":[{\"path\":\"UnitMotion/walkSpeed\",\"op\":\"mul\","
                        + "\"value\":1.1,\"filter\":\"Worker\"}]}"))
                .isEmpty();
    }

    @Test
    void aRuleIsAnIdAndAnObjectOfParams() {
        assertOnly(problems("{" + HOUSE + ",\"rules\":{\"population\":\"fixed\"}}"), "/rules/population", "expected {");
        assertOnly(
                problems("{" + HOUSE + ",\"rules\":{\"population\":{\"id\":7}}}"), "/rules/population/id", "id string");
        assertOnly(problems("{" + HOUSE + ",\"rules\":{\"population\":{}}}"), "/rules/population/id", "id string");
        assertOnly(
                problems("{" + HOUSE + ",\"rules\":{\"population\":{\"id\":\"fixed\",\"params\":[1]}}}"),
                "/rules/population/params",
                "expected an object");
        assertOnly(
                problems("{" + HOUSE + ",\"rules\":{\"population\":{\"id\":\"fixed\",\"cap\":4}}}"),
                "/rules/population/cap",
                "unknown key");
        assertThat(problems("{" + HOUSE + ",\"rules\":{\"population\":{\"id\":\"fixed\",\"params\":{\"cap\":4}}}}"))
                .isEmpty();
    }

    @Test
    void theHouseRoleIsRequiredUnlessTheCivilisationSelectsItsOwnPopulationRule() {
        assertOnly(problems("{}"), "/roles", "no 'house' role");
        assertThat(problems("{\"rules\":{\"population\":{\"id\":\"fixed\",\"params\":{\"cap\":40}}}}"))
                .as("a fixed cap needs no house")
                .isEmpty();
    }

    @Test
    void aCivilisationWithAProblemIsLeftOutAndTheOthersStand() {
        TemplateCompiler.Result result = compile(
                civ("t:good", MINIMAL),
                civ("t:bad", "{\"roles\":{\"house\":\"t:ghost\"}}"),
                civ("t:torn", "{\"roles\":[]}"));

        assertThat(result.ok()).isFalse();
        assertThat(result.set().civs()).extracting(CivDef::id).containsExactly(new CivId("t:good"));
        assertThat(result.problems())
                .extracting(TemplateProblem::file)
                .containsExactlyInAnyOrder("data/t/strategos/civs/bad.json", "data/t/strategos/civs/torn.json");
    }

    @Test
    void aCivilisationDefinedTwiceIsAnError() {
        TemplateCompiler.Result result = compile(civ("t:twin", MINIMAL), civ("t:twin", MINIMAL));

        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().get(0).message()).contains("also defined");
    }

    @Test
    void aMalformedCivilisationIdIsRejectedByTheId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CivId("Tribe"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
