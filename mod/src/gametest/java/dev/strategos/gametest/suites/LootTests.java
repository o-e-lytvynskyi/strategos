package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.gametest.framework.GameTestHelper;

/** CMB-13 on the shipped data (M10 spec §2.10): what a kill pays, through the real match runtime. */
public final class LootTests {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final long FOOD = 11_000; // a spearman's loot in thousandths (data): five plus a tenth of sixty
    private static final long WOOD = 4_000; // and a tenth of forty

    private LootTests() {}

    /**
     * Five spearmen of one faction attack one of the other: when it falls, the killers' faction has been paid exactly
     * the spearman's loot from the data, once, as {@code looted}, the fallen's faction nothing for it, and the
     * treasury's balance holds (the core checks it every step, and here it is read again).
     */
    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 1_200)
    public static void kill_pays(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, F0, new Vec2d(20, 24), 5, 1.5));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, F1, new Vec2d(30, 24), 1, 0));
        var state = new Object() {
            boolean ordered;
        };
        h.onEachTick(() -> {
            int[] west = core(rt).queries().unitHandlesOf(F0);
            int[] east = core(rt).queries().unitHandlesOf(F1);
            if (!state.ordered) {
                if (west.length == 5 && east.length == 1) {
                    rt.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(west), east[0]));
                    state.ordered = true;
                }
                return;
            }
            if (east.length > 0) {
                return;
            }
            FactionTreasury paid = core(rt).queries().treasury(F0);
            FactionTreasury other = core(rt).queries().treasury(F1);
            int food = 0;
            int wood = 1;
            check(
                    paid.looted().get(food) == FOOD,
                    "food looted " + paid.looted().get(food) + ", not " + FOOD);
            check(
                    paid.looted().get(wood) == WOOD,
                    "wood looted " + paid.looted().get(wood) + ", not " + WOOD);
            check(paid.looted().get(2) == 0 && paid.looted().get(3) == 0, "stone or iron looted from a spearman");
            check(other.looted().stream().allMatch(v -> v == 0), "the fallen's faction was paid for it");
            for (FactionTreasury t : new FactionTreasury[] {paid, other}) {
                for (int r = 0; r < t.stock().size(); r++) {
                    check(
                            t.granted().get(r)
                                            + t.gathered().get(r)
                                            + t.refunded().get(r)
                                            + t.looted().get(r)
                                    == t.stock().get(r) + t.spent().get(r),
                            "the treasury is out of balance in resource " + r);
                }
            }
            h.succeed();
        });
    }
}
