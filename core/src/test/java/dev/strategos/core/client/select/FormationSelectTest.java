package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** ORD-06.6: a click on a formation's member selects that unit; Alt+click selects its whole formation. */
class FormationSelectTest {
    private static final int MINE = 0;
    private static final int THEIRS = 1;

    private static Seen unit(int handle, int faction, int formation) {
        return new Seen(handle, faction, 0, 10, 10, 10, 8, formation);
    }

    private static final List<Seen> SCENE = List.of(
            unit(1, MINE, 7),
            unit(2, MINE, 7),
            unit(3, MINE, 7),
            unit(4, MINE, 9), // another formation of the same player
            unit(5, MINE, 0), // in none
            unit(6, THEIRS, 7)); // an enemy that happens to carry the same id: never trusted

    @Test
    void aPlainClickOnAMemberSelectsOnlyThatUnit() {
        Selection s = new Selection(MINE);
        s.click(SCENE.get(1), false);
        assertThat(s.handles()).containsExactly(2);
    }

    @Test
    void altClickOnAMemberSelectsEveryMemberOfItsFormation() {
        Selection s = new Selection(MINE);
        s.clickFormation(SCENE, SCENE.get(1), false);
        assertThat(s.handles()).containsExactly(1, 2, 3);
    }

    @Test
    void altClickReplacesTheSelectionAndShiftAddsToIt() {
        Selection s = new Selection(MINE);
        s.click(SCENE.get(4), false);
        s.clickFormation(SCENE, SCENE.get(0), false);
        assertThat(s.handles()).containsExactly(1, 2, 3);
        s.clickFormation(SCENE, SCENE.get(3), true);
        assertThat(s.handles()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void aMemberOffScreenIsNotTaken() {
        Selection s = new Selection(MINE);
        s.clickFormation(List.of(SCENE.get(0), SCENE.get(1), SCENE.get(3)), SCENE.get(0), false);
        assertThat(s.handles()).containsExactly(1, 2);
    }

    @Test
    void aUnitInNoFormationIsSelectedAlone() {
        Selection s = new Selection(MINE);
        s.clickFormation(SCENE, SCENE.get(4), false);
        assertThat(s.handles()).containsExactly(5);
    }

    @Test
    void anotherFactionsFormationAddsNothing() {
        Selection s = new Selection(MINE);
        s.click(SCENE.get(0), false);
        s.clickFormation(SCENE, SCENE.get(5), false);
        assertThat(s.handles()).as("an enemy is inspected, never selected").isEmpty();
        assertThat(s.inspected()).isEqualTo(6);

        Selection other = new Selection(MINE);
        other.clickFormation(SCENE, SCENE.get(0), false);
        assertThat(other.handles())
                .as("the enemy with the same formation id is not taken")
                .doesNotContain(6);
    }

    @Test
    void aBoxAndADoubleClickAreWhatTheyWere() {
        Selection s = new Selection(MINE);
        s.box(SCENE, 0, 0, 20, 20, false);
        assertThat(s.handles()).containsExactly(1, 2, 3, 4, 5);
        Selection t = new Selection(MINE);
        t.allOfType(SCENE, SCENE.get(0), false);
        assertThat(t.handles()).containsExactly(1, 2, 3, 4, 5);
    }
}
