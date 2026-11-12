package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CMB-17: lines and flashes come only from what the viewer received (L1, view model). */
class AttackLinesTest {
    private static final int MATCH = 1;
    private static final int OWN = 0;

    private static UnitSummary.Entry at(int index, int faction, double x, double z) {
        return UnitSummary.Entry.at(index, faction, 0, 0, x, z);
    }

    private static UnitDetail.Unit detail(int handle, int target) {
        return new UnitDetail.Unit(handle, 0, 2, 100f, 100f, 0.2f, List.of(), -1, 0, target);
    }

    private static ClientMatchModel model(List<UnitSummary.Entry> units, UnitDetail.Unit... details) {
        ClientMatchModel m = new ClientMatchModel();
        m.accept(new UnitSummary(MATCH, 10, units), MATCH);
        m.accept(new UnitDetail(MATCH, 10, List.of(details)), MATCH);
        return m;
    }

    private static void extract(ClientMatchModel m, AttackLines.Buffer out, Integer... handles) {
        Selection s = new Selection(OWN);
        s.set(List.of(handles));
        int[] sorted = s.handles().stream().mapToInt(Integer::intValue).toArray();
        AttackLines.extract(m, sorted, sorted.length, out);
    }

    @Test
    void onlyWhatTheViewerMayReceive() {
        int a = Handle.of(3, 1);
        int b = Handle.of(4, 1);
        int c = Handle.of(5, 1);
        ClientMatchModel m = model(
                List.of(at(3, OWN, 8, 8), at(4, OWN, 9, 9), at(5, 1, 20, 24)),
                detail(a, 5), // seen target: a line
                detail(b, 77), // a target the frame does not hold (fog): nothing
                detail(c, 3)); // not selected: nothing
        AttackLines.Buffer out = new AttackLines.Buffer();
        extract(m, out, a, b);
        assertThat(out.count()).isEqualTo(1);
        assertThat(out.fromX(0)).isEqualTo(8f);
        assertThat(out.toX(0)).isEqualTo(20f);
        assertThat(out.toZ(0)).isEqualTo(24f);

        extract(m, out);
        assertThat(out.count()).as("lines only for the selection").isZero();
        extract(m, out, a);
        assertThat(out.count()).as("a reused buffer starts empty").isEqualTo(1);
    }

    private static int spearman(SimHarness h, int faction, Vec2d at) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(faction), at, 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theServerSendsATargetOnlyToWhoMaySeeIt() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int own = spearman(h, 0, new Vec2d(8, 8));
        int enemy = spearman(h, 1, new Vec2d(12, 8));
        h.submit(new PlayerSlot(0), new Attack(UnitSet.of(own), enemy, QueueMode.REPLACE));
        h.runTicks(1);
        FactionSlot viewer = new FactionSlot(0);
        UnitDetail seen = ReplicationService.detail(h.queries(), MATCH, viewer, List.of(own), Visibility.ALL);
        assertThat(seen.units().get(0).target()).isEqualTo(Handle.index(enemy));
        UnitDetail hidden = ReplicationService.detail(h.queries(), MATCH, viewer, List.of(own), (v, u) -> false);
        assertThat(hidden.units().get(0).target()).isEqualTo(UnitDetail.NO_TARGET);
        UnitDetail enemys = ReplicationService.detail(h.queries(), MATCH, viewer, List.of(enemy), Visibility.ALL);
        assertThat(enemys.units()).as("an enemy's detail is never available").isEmpty();
    }

    @Test
    void theTargetRidesTheWireAndAnOldFrameIsUnchanged() {
        UnitDetail plain = new UnitDetail(MATCH, 9, List.of(detail(5, UnitDetail.NO_TARGET)));
        byte[] bytes = WireCodec.encode(UnitDetail.WIRE, plain);
        assertThat(WireCodec.decode(UnitDetail.WIRE, bytes)).isEqualTo(plain);
        UnitDetail aimed =
                new UnitDetail(MATCH, 9, List.of(detail(5, 300), detail(6, UnitDetail.NO_TARGET), detail(7, 2)));
        byte[] more = WireCodec.encode(UnitDetail.WIRE, aimed);
        assertThat(WireCodec.decode(UnitDetail.WIRE, more)).isEqualTo(aimed);
        assertThat(WireCodec.encode(
                        UnitDetail.WIRE, new UnitDetail(MATCH, 9, List.of(detail(5, UnitDetail.NO_TARGET)))))
                .as("no target, no extra byte")
                .hasSize(bytes.length);
        assertThat(more.length).isLessThanOrEqualTo(UnitDetail.WIRE.maxSize());
        assertThat(OrderKind.attacks(OrderKind.ATTACK)).isTrue();
    }

    @Test
    void theFlashComesOnlyFromAHitHudOnOwnUnits() {
        ClientMatchModel m = model(List.of(at(3, OWN, 1, 1), at(4, 1, 2, 2), at(5, OWN, 3, 3)));
        Flashes.Buffer out = new Flashes.Buffer();
        m.accept(new UnitHud(MATCH, 11, List.of(new UnitHud.Entry(3, 200), new UnitHud.Entry(4, 100))), MATCH, 1_000);
        Flashes.extract(m, OWN, 1_000, out);
        assertThat(out.count())
                .as("the enemy never flashes, the unhit own unit neither")
                .isEqualTo(1);
        assertThat(out.index(0)).isEqualTo(3);
        assertThat(out.strength(0)).isEqualTo(1f);

        Flashes.extract(m, OWN, 1_000 + Flashes.MILLIS / 2, out);
        assertThat(out.strengthOf(3)).isEqualTo(0.5f);
        Flashes.extract(m, OWN, 1_000 + Flashes.MILLIS, out);
        assertThat(out.count()).as("over").isZero();

        m.accept(new UnitHud(MATCH, 12, List.of(new UnitHud.Entry(3, 250))), MATCH, 5_000);
        Flashes.extract(m, OWN, 5_000, out);
        assertThat(out.count()).as("healing is no hit").isZero();
        m.accept(new UnitHud(MATCH + 1, 12, List.of(new UnitHud.Entry(3, 10))), MATCH, 5_000);
        Flashes.extract(m, OWN, 5_000, out);
        assertThat(out.count()).as("another match's hud is ignored").isZero();
    }
}
