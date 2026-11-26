package dev.strategos.core.client.order;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.ClientFactionView;
import dev.strategos.core.client.hud.CommandCard;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.CancelBuilding;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.DeleteUnits;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** UI-17 in L1: the commands of the all clear, garrison and delete hotkeys choose their targets from what the client knows. */
class HotkeyOrdersTest {
    static final TemplateSet T = TemplateSet.builtin();

    static int index(String id) {
        return Objects.requireNonNull(T.byId(new TemplateId(id))).netIndex();
    }

    static final int CENTRE = index("strategos:building/civic_centre");
    static final int HOUSE = index("strategos:building/house");
    static final int FIELD = index("strategos:building/field");
    static final int TOWER = index("strategos:building/tower");
    static final int WORKER = index("strategos:unit/worker");
    static final int INFANTRY = index("strategos:unit/infantry");

    static BuildingState.Entry building(int handle, int template, int x, int z, boolean own, int state) {
        return new BuildingState.Entry(
                handle, template, own ? 0 : 1, x, z, 0, 1, state, 1000, 255, own, List.of(), false, 0, 0, 0, -1, 0);
    }

    static BuildingState.Entry done(int handle, int template, int x, int z) {
        return building(handle, template, x, z, true, CommandCard.COMPLETE);
    }

    static int handle(int index) {
        return (1 << 20) | index;
    }

    @Test
    void backToWorkSoundsTheSelectedHolderOrElseEveryOwnHolder() {
        BuildingState.Entry centre = done(0x100001, CENTRE, 4, 4);
        BuildingState.Entry house = done(0x100002, HOUSE, 20, 4);
        BuildingState.Entry field = done(0x100003, FIELD, 30, 4);
        BuildingState.Entry site = building(0x100004, HOUSE, 40, 4, true, 1);
        BuildingState.Entry enemy = building(0x100005, HOUSE, 50, 4, false, CommandCard.COMPLETE);
        List<BuildingState.Entry> all = List.of(centre, house, field, site, enemy);

        assertThat(HotkeyOrders.backToWork(T, house, all)).containsExactly(new AllClear(0x100002));
        assertThat(HotkeyOrders.backToWork(T, null, all))
                .as("none selected: the faction's finished holders, not fields, sites or enemies")
                .containsExactly(new AllClear(0x100001), new AllClear(0x100002));
        assertThat(HotkeyOrders.backToWork(T, field, all))
                .as("a building that holds no garrison: the faction's holders")
                .containsExactly(new AllClear(0x100001), new AllClear(0x100002));
        assertThat(HotkeyOrders.backToWork(T, null, List.of(field, enemy))).isEmpty();
    }

    @Test
    void garrisonTakesTheSelectionToTheNearestHolderThatAdmitsAny() {
        BuildingState.Entry near = done(0x100001, HOUSE, 18, 16); // workers only
        BuildingState.Entry far = done(0x100002, CENTRE, 90, 16); // workers and infantry
        BuildingState.Entry tower = done(0x100003, TOWER, 40, 16); // infantry only
        List<BuildingState.Entry> all = List.of(near, far, tower);
        ClientFactionView view = new ClientFactionView(T, null, all, null);
        UnitSummary.Entry w = new UnitSummary.Entry(1, 0, WORKER, 0, 16 * 8, 16 * 8);
        UnitSummary.Entry s = new UnitSummary.Entry(2, 0, INFANTRY, 0, 16 * 8, 16 * 8);

        Command workers = HotkeyOrders.garrison(T, view, List.of(handle(1)), i -> i == 1 ? w : null, all);
        assertThat(workers).isEqualTo(new Garrison(UnitSet.of(handle(1)), 0x100001));

        Command soldiers = HotkeyOrders.garrison(T, view, List.of(handle(2)), i -> i == 2 ? s : null, all);
        assertThat(soldiers)
                .as("the house does not admit infantry: the tower is next")
                .isEqualTo(new Garrison(UnitSet.of(handle(2)), 0x100003));

        Command mixed =
                HotkeyOrders.garrison(T, view, List.of(handle(1), handle(2)), i -> i == 1 ? w : i == 2 ? s : null, all);
        assertThat(mixed)
                .as("only the units the chosen holder admits go in")
                .isEqualTo(new Garrison(UnitSet.of(handle(1)), 0x100001));
    }

    @Test
    void garrisonHasNothingToDoWithoutAHolderOrAKnownUnit() {
        BuildingState.Entry field = done(0x100001, FIELD, 18, 16);
        BuildingState.Entry enemyHouse = building(0x100002, HOUSE, 20, 16, false, CommandCard.COMPLETE);
        List<BuildingState.Entry> all = List.of(field, enemyHouse);
        ClientFactionView view = new ClientFactionView(T, null, all, null);
        UnitSummary.Entry w = new UnitSummary.Entry(1, 0, WORKER, 0, 128, 128);

        assertThat(HotkeyOrders.garrison(T, view, List.of(handle(1)), i -> w, all))
                .isNull();
        assertThat(HotkeyOrders.garrison(T, view, List.of(handle(1)), i -> null, List.of(done(0x100003, HOUSE, 1, 1))))
                .isNull();
        assertThat(HotkeyOrders.garrison(T, view, List.of(), i -> w, all)).isNull();
    }

    @Test
    void deleteKillsTheUnitsAndDemolishesAFinishedBuildingOrCancelsASite() {
        List<Integer> units = List.of(handle(1), handle(2));
        BuildingState.Entry done = done(0x100001, HOUSE, 4, 4);
        BuildingState.Entry site = building(0x100002, HOUSE, 4, 4, true, 1);
        BuildingState.Entry enemy = building(0x100003, HOUSE, 4, 4, false, CommandCard.COMPLETE);

        assertThat(HotkeyOrders.delete(units, null)).containsExactly(new DeleteUnits(UnitSet.of(handle(1), handle(2))));
        assertThat(HotkeyOrders.delete(List.of(), done)).containsExactly(new Demolish(0x100001));
        assertThat(HotkeyOrders.delete(List.of(), site)).containsExactly(new CancelBuilding(0x100002));
        assertThat(HotkeyOrders.delete(List.of(), enemy)).as("never an enemy's").isEmpty();
        assertThat(HotkeyOrders.delete(List.of(), null)).isEmpty();
    }
}
