package dev.strategos.core.sim.econ;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.Reseed;
import dev.strategos.core.protocol.SetAutoReseed;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import org.junit.jupiter.api.Test;

/**
 * ECO-17 in L1: a farmstead sows an empty field in its reach by itself once its 40 wood can be paid, and the field waits
 * while it cannot (1); with the toggle off nothing sows it by itself, and the manual reseed still does (2).
 */
class AutoReseedTest {
    static final TemplateId FIELD = new TemplateId("strategos:building/field");
    static final TemplateId FARMSTEAD = new TemplateId("strategos:building/farmstead");

    /** Reaps field {@code field} empty at once, as farmers would. */
    private static void reap(EconScene e, int field) {
        int node = e.s.buildings().field(field);
        e.s.nodes().setRipening(node, 0);
        e.s.nodes().take(node, e.s.nodes().amount(node));
    }

    private static long wood(EconScene e) {
        return e.s.treasury().stock(0, EconScene.WOOD);
    }

    @Test
    void reseedsWhenPaidElseWaits() {
        EconScene e = EconScene.flat(48, 48).build();
        int farmstead = e.building(FARMSTEAD, 0, 20, 20);
        int field = e.building(FIELD, 0, 30, 20);
        int far = e.building(FIELD, 0, 2, 40); // 26 blocks from the farmstead's middle: out of its reach
        assertThat(e.s.buildings().autoReseed(farmstead))
                .as("on from the start")
                .isTrue();
        reap(e, field);
        reap(e, far);
        e.h.step();
        assertThat(Fields.empty(e.s, field)).as("no wood: it waits").isTrue();
        e.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 100));
        e.h.step();
        assertThat(Fields.empty(e.s, field)).as("paid: sown again").isFalse();
        assertThat(wood(e)).as("its 40 wood").isEqualTo(60_000);
        assertThat(Fields.empty(e.s, far)).as("out of reach").isTrue();
    }

    @Test
    void manualReseedWithTheToggleOff() {
        EconScene e = EconScene.flat(48, 48).build();
        int farmstead = e.building(FARMSTEAD, 0, 20, 20);
        int field = e.building(FIELD, 0, 30, 20);
        e.h.submit(PlayerSlot.ADMIN, new Grant(new FactionSlot(0), "wood", 100));
        e.h.submit(EconScene.P0, new SetAutoReseed(farmstead, false));
        e.h.step();
        assertThat(e.s.buildings().autoReseed(farmstead)).isFalse();
        reap(e, field);
        for (int t = 0; t < 40; t++) {
            e.h.step();
        }
        assertThat(Fields.empty(e.s, field)).as("off: nothing sows it").isTrue();
        e.h.submit(EconScene.P0, new Reseed(field));
        e.h.step();
        assertThat(Fields.empty(e.s, field)).as("sown by hand").isFalse();
        assertThat(wood(e)).isEqualTo(60_000);
    }
}
