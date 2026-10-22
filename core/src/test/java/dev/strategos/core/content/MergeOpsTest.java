package dev.strategos.core.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** DATA-01.2: each merge construct, one row each, problems included. */
class MergeOpsTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            objects merge deeply      | {'a':{'x':1,'y':2}}          | {'a':{'y':3,'z':4}}                      | {'a':{'x':1,'y':3,'z':4}} |
            scalars replace           | {'a':1}                      | {'a':2}                                  | {'a':2}                   |
            arrays replace            | {'a':[1,2]}                  | {'a':[3]}                                | {'a':[3]}                 |
            $op add                   | {'a':5}                      | {'a':{'$op':'add','value':2.5}}          | {'a':7.5}                 |
            $op mul                   | {'a':4}                      | {'a':{'$op':'mul','value':0.5}}          | {'a':2}                   |
            $op replace               | {'a':{'x':1}}                | {'a':{'$op':'replace','value':[1]}}      | {'a':[1]}                 |
            $disable removes          | {'a':{'x':1},'b':2}          | {'a':{'$disable':true}}                  | {'b':2}                   |
            $append                   | {'a':[1]}                    | {'a':{'$append':[2,3]}}                  | {'a':[1,2,3]}             |
            $remove                   | {'a':[1,2,1,3]}              | {'a':{'$remove':[1]}}                    | {'a':[2,3]}               |
            class tokens edit         | {'I':{'classes':'A B'}}      | {'I':{'classes':'+C -A'}}                | {'I':{'classes':'B C'}}   |
            class tokens replace      | {'I':{'classes':'A B'}}      | {'I':{'classes':'D C C'}}                | {'I':{'classes':'C D'}}   |
            $op add on nothing        | {}                           | {'a':{'$op':'add','value':1}}            | {}                        | /a
            $op add on a string       | {'a':'x'}                    | {'a':{'$op':'add','value':1}}            | {'a':'x'}                 | /a
            unknown $op               | {'a':1}                      | {'a':{'$op':'pow','value':2}}            | {'a':1}                   | /a/$op
            $append on an object      | {'a':{'x':1}}                | {'a':{'$append':[1]}}                    | {'a':{'x':1}}             | /a
            $op with extra keys       | {'a':1}                      | {'a':{'$op':'add','value':1,'z':0}}      | {'a':1}                   | /a
            """)
    void mergesAsTheTableSays(String name, String base, String overlay, String expected, String problemAt) {
        List<TemplateProblem> problems = new ArrayList<>();

        JsonElement merged = MergeOps.merge(json(base), json(overlay), "t.json", "", problems);

        assertThat(merged).isNotNull();
        assertThat(CanonicalJson.write(merged)).isEqualTo(CanonicalJson.write(json(expected)));
        if (problemAt == null || problemAt.isBlank()) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).extracting(TemplateProblem::pointer).containsExactly(problemAt);
            assertThat(problems.get(0).file()).isEqualTo("t.json");
        }
    }

    private static JsonElement json(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"'));
    }
}
