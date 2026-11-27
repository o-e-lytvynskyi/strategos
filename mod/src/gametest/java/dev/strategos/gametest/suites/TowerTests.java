package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import com.google.gson.JsonObject;
import dev.strategos.core.content.TemplateCompiler;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.Grant;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UpgradeBuilding;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.build.BuildingStore;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * BLD-12.2 on the real world: a damaged sentry tower with three infantry inside is upgraded to the defence tower; the
 * building is the new template with its garrison still inside, its health ratio kept, and the level's blocks over its
 * footprint are the new blueprint. The Town gate and the upgrade's time are L1's ({@code UpgradeTest}), so the set
 * here has no phase requirement and a two-second upgrade.
 */
public final class TowerTests {
    private static final TemplateId SENTRY = new TemplateId("strategos:building/sentry_tower");
    private static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final int GARRISONED = 3;
    private static final int RAIDERS = 4;

    private TowerTests() {}

    /** The built-in set whose sentry tower upgrades without Town and in two seconds. */
    private static TemplateSet quickUpgrade() {
        TemplateSet b = TemplateSet.builtin();
        List<TemplateCompiler.Flat> flats = new ArrayList<>();
        for (UnitTemplate t : b.all()) {
            JsonObject json = b.flattened(t.id()).deepCopy();
            if (t.id().equals(SENTRY)) {
                JsonObject upgrade = json.getAsJsonObject("Upgrade");
                upgrade.remove("requires");
                upgrade.addProperty("time", 2);
            }
            flats.add(new TemplateCompiler.Flat(t.id(), t.netIndex(), json));
        }
        return TemplateCompiler.fromFlattened(
                        flats,
                        b.techs().stream()
                                .map(t -> new TemplateCompiler.TechFlat(t.id(), t.netIndex(), t.toJson()))
                                .toList())
                .set();
    }

    private static BuildingView only(MatchRuntime rt) {
        List<BuildingView> all = core(rt).queries().buildings();
        check(all.size() == 1, "buildings: " + all.size());
        return all.get(0);
    }

    private static boolean allInside(MatchRuntime rt, int[] units) {
        for (int unit : units) {
            if (Units.unit(rt, unit).state() != UnitState.GARRISONED) {
                return false;
            }
        }
        return true;
    }

    /**
     * BLD-12.2: three infantry inside a sentry tower that raiders have hurt; the upgrade runs its two seconds and the
     * building is the defence tower, the three still inside, its hit points the same share of its most, its blocks the
     * defence tower's blueprint.
     */
    @StrategosTest(batch = "construction", timeoutTicks = 3_000)
    public static void upgrade_keeps_garrison(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnBuilding(SENTRY, F0, 20, 20, 0));
        rt.submit(PlayerSlot.ADMIN, new Grant(F0, "wood", 1_000));
        rt.submit(PlayerSlot.ADMIN, new Grant(F0, "stone", 1_000));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, F0, new Vec2d(21, 28), GARRISONED, 1.5));
        rt.queueSwap(new ContentSwap(quickUpgrade()));
        var state = new Object() {
            int[] inside;
            double ratio;
            boolean raided;
            boolean ordered;
        };
        h.onEachTick(() -> {
            var q = core(rt).queries();
            if (state.inside == null) {
                int[] mine = q.unitHandlesOf(F0);
                if (mine.length != GARRISONED
                        || q.buildings().size() != 1
                        || q.buildings().get(0).state() != BuildingStore.COMPLETE) {
                    return;
                }
                state.inside = mine;
                rt.submit(
                        P0, new Garrison(UnitSet.of(mine), q.buildings().get(0).handle()));
                return;
            }
            if (!state.raided) {
                if (!allInside(rt, state.inside)) {
                    return;
                }
                rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, F1, new Vec2d(22, 30), RAIDERS, 1.5));
                state.raided = true;
                return;
            }
            BuildingView b = only(rt);
            int[] raiders = q.unitHandlesOf(F1);
            if (!state.ordered) {
                if (raiders.length == RAIDERS) {
                    rt.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(raiders), b.handle()));
                    state.ordered = true;
                }
                return;
            }
            if (state.ratio == 0) {
                if (b.hitPoints() < 0.95 * b.maxHitPoints()) { // hurt, but still whole to the damage stages
                    for (int raider : raiders) {
                        rt.damage(new ExternalDamage(
                                raider, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
                    }
                    state.ratio = -1; // the raiders go this tick; the ratio is read once they have
                }
                return;
            }
            if (state.ratio < 0) {
                if (raiders.length == 0) {
                    state.ratio = b.hitPoints() / b.maxHitPoints();
                    check(state.ratio < 1 && state.ratio > 0.86, "the ratio is " + state.ratio);
                    rt.submit(P0, new UpgradeBuilding(b.handle()));
                }
                return;
            }
            if (!b.template().equals(TOWER)) {
                check(b.queue().size() <= 1, "the queue holds " + b.queue());
                return;
            }
            check(core(rt).queries().buildings().size() == 1, "another building appeared");
            check(
                    Math.abs(b.hitPoints() / b.maxHitPoints() - state.ratio) < 1e-6,
                    "the health ratio is " + b.hitPoints() / b.maxHitPoints() + ", was " + state.ratio);
            check(allInside(rt, state.inside), "the garrison came out");
            check(b.queue().isEmpty(), "the upgrade is still queued");
            if (StrategosServer.get().writer().pending(h.getLevel()) > 0) { // the new blocks are still being written
                return;
            }
            ConstructionTests.equalsBlueprint(h, rt, b);
            h.succeed();
        });
    }
}
