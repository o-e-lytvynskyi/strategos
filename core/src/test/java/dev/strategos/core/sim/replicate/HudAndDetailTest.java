package dev.strategos.core.sim.replicate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * NET-08's tier 3 in L1: {@code unit_hud} sends a player only the units it sees whose hit points changed, once each;
 * {@code unit_detail} answers for the asker's own units only (NET-08.4's core side); all three codecs round-trip.
 */
class HudAndDetailTest {
    private static final FactionSlot A = new FactionSlot(0);
    private static final FactionSlot B = new FactionSlot(1);

    @Test
    void theHudListsChangesOnceAndOnlyWhatTheViewerSees() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int own = h.spawn(A, new Vec2d(8, 8), 1, 0)[0];
        int enemy = h.spawn(B, new Vec2d(20, 20), 1, 0)[0];
        h.runTicks(1);
        HudMemory memory = new HudMemory();
        assertThat(memory.changes(h.queries(), 1, A, Visibility.ALL).units())
                .as("whole units are not news")
                .isEmpty();

        h.damage(new ExternalDamage(own, ExternalDamage.Kind.HACK, 25, ExternalDamage.SourceKind.NONE));
        h.damage(new ExternalDamage(enemy, ExternalDamage.Kind.HACK, 50, ExternalDamage.SourceKind.NONE));
        h.step();
        UnitHud seen = memory.changes(h.queries(), 1, A, Visibility.ALL);
        assertThat(seen.units())
                .containsExactly(
                        new UnitHud.Entry(Handle.index(own), 191), new UnitHud.Entry(Handle.index(enemy), 128));
        assertThat(memory.changes(h.queries(), 1, A, Visibility.ALL).units())
                .as("sent once")
                .isEmpty();

        HudMemory blind = new HudMemory();
        assertThat(blind.changes(h.queries(), 1, A, (viewer, unit) -> false).units())
                .as("a hidden enemy's hit points never reach the viewer")
                .containsExactly(new UnitHud.Entry(Handle.index(own), 191));
    }

    @Test
    void detailsAnswerForOwnUnitsOnly() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int own = h.spawn(A, new Vec2d(8, 8), 1, 0)[0];
        int enemy = h.spawn(B, new Vec2d(20, 20), 1, 0)[0];
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(own), new Vec2d(30, 8)));
        h.runTicks(2);
        UnitDetail d = ReplicationService.detail(h.queries(), 1, A, List.of(enemy, own, 12345));
        assertThat(d.units()).extracting(UnitDetail.Unit::handle).containsExactly(own);
        UnitDetail.Unit u = d.units().get(0);
        assertThat(u.maxHealth()).isEqualTo(100f);
        assertThat(u.health()).isEqualTo(100f);
        assertThat(u.walkSpeed()).isEqualTo(0.25f);
        assertThat(u.orders()).hasSize(1);
        assertThat(u.orders().get(0).x()).isEqualTo(30f);
    }

    @Test
    void theCodecsRoundTrip() {
        UnitHud hud = new UnitHud(2, 99, List.of(new UnitHud.Entry(40, 3), new UnitHud.Entry(7, 255)));
        assertThat(WireCodec.decode(UnitHud.WIRE, WireCodec.encode(UnitHud.WIRE, hud)))
                .isEqualTo(hud);
        UiQuery query = new UiQuery(List.of(1 << 20, 5, 77));
        assertThat(WireCodec.decode(UiQuery.WIRE, WireCodec.encode(UiQuery.WIRE, query)))
                .isEqualTo(query);
        UnitDetail detail = new UnitDetail(
                2,
                99,
                List.of(
                        new UnitDetail.Unit(5, 1, 1, 40.5f, 100f, 0.25f, List.of(new UnitDetail.Order(1, 3, 4)), -1, 0),
                        new UnitDetail.Unit(6, 2, 3, 25f, 25f, 0.2f, List.of(), 1, 7_250)));
        assertThat(WireCodec.decode(UnitDetail.WIRE, WireCodec.encode(UnitDetail.WIRE, detail)))
                .isEqualTo(detail);
        assertThat(UnitHud.Entry.health(0.1, 100)).as("alive is never 0").isEqualTo(1);
        assertThat(UnitHud.Entry.health(100, 100)).isEqualTo(255);
    }
}
