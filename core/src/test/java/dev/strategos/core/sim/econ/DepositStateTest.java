package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.hud.SelectionPanel;
import dev.strategos.core.client.hud.SelectionSummary;
import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.protocol.SpawnDeposit;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.replicate.ReplicationService;
import org.junit.jupiter.api.Test;

/**
 * ECO-08's "remaining amount shown on selection" in L1: the deposit state a player gets holds each deposit's corner and
 * what it has left, which falls as it is mined; a click on its blocks picks it, and the panel shows it alone.
 */
class DepositStateTest {
    private static final int MATCH = 1;

    @Test
    void aClickOnADepositShowsWhatItHasLeft() {
        EconScene e = EconScene.flat(48, 48).build();
        e.civicCentre(0, 30, 30);
        e.h.submit(PlayerSlot.ADMIN, new SpawnDeposit(new TemplateId("strategos:building/iron_mine"), 10, 10));
        e.h.step();
        DepositState fresh = ReplicationService.depositState(e.h.queries(), MATCH);
        assertThat(fresh.deposits()).hasSize(1);
        DepositState.Entry d = fresh.deposits().get(0);
        assertThat(d.x()).isEqualTo(10);
        assertThat(d.z()).isEqualTo(10);
        assertThat(d.left()).isEqualTo(1_000);
        assertThat(d.most()).isEqualTo(1_000);
        assertThat(e.h.queries()
                        .config()
                        .rules()
                        .economy()
                        .resources()
                        .get(d.resource())
                        .id())
                .isEqualTo("iron");

        e.gather(e.workers(0, new Vec2d(8.5, 11.5), 2, 1), 11, 1, 11);
        for (int t = 0; t < 2_000; t++) {
            e.h.step();
        }
        DepositState mined = ReplicationService.depositState(e.h.queries(), MATCH);
        assertThat(mined.deposits().get(0).left()).as("mined").isLessThan(1_000);
        assertThat(mined.sameAs(fresh)).isFalse();

        ClientMatchModel model = new ClientMatchModel();
        model.accept(WireCodec.decode(DepositState.WIRE, WireCodec.encode(DepositState.WIRE, mined)), MATCH);
        TemplateSet templates = e.h.queries().templates();
        assertThat(model.depositAt(templates, 11, 11))
                .isEqualTo(mined.deposits().get(0));
        assertThat(model.depositAt(templates, 9, 11)).as("beside it").isNull();

        Selection selection = new Selection(0);
        selection.clickDeposit(d.node());
        assertThat(selection.deposit()).isEqualTo(d.node());
        SelectionPanel panel = SelectionPanel.of(
                SelectionSummary.EMPTY, null, null, model.deposits().get(0));
        assertThat(panel.kind()).isEqualTo(SelectionPanel.Kind.DEPOSIT);
        DepositState.Entry shown = mined.deposits().get(0);
        assertThat(SelectionPanel.depositLine(shown, "iron")).isEqualTo(shown.left() + " / 1000 iron left");
        selection.click(null, false);
        assertThat(selection.deposit()).as("an empty click drops it").isNull();
    }

    @Test
    void anEmptyDepositSaysSo() {
        DepositState.Entry empty = new DepositState.Entry(1, 0, 0, 0, 0, 0, 1_000);
        assertThat(SelectionPanel.depositLine(empty, "stone")).isEqualTo("Exhausted");
    }
}
