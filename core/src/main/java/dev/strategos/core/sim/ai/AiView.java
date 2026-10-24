package dev.strategos.core.sim.ai;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.content.ValuePath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.order.OrderQueues;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a computer player may read (AI-01, 04 §14): its own faction in full, everything else as its faction sees it,
 * which until fog (M6) is everything. Read-only: it wraps the match's queries. Each list it gives spends a unit of the
 * budget per entry, once a think: the list is kept for the rest of it.
 */
public final class AiView {
    private final MatchQueries q;
    private final int faction;
    private final AiBudget budget;
    private @Nullable List<UnitView> units;
    private @Nullable List<UnitView> enemies;
    private @Nullable List<BuildingView> all;
    private @Nullable List<NodeView> nodes;

    public AiView(MatchQueries q, int faction, AiBudget budget) {
        this.q = q;
        this.faction = faction;
        this.budget = budget;
    }

    public int faction() {
        return faction;
    }

    public long tick() {
        return q.tick();
    }

    public TemplateSet templates() {
        return q.templates();
    }

    public MatchBounds bounds() {
        return q.config().bounds();
    }

    /** The match's resources in order. */
    public List<String> resources() {
        List<String> out = new ArrayList<>();
        q.config().rules().economy().resources().forEach(r -> out.add(r.id()));
        return out;
    }

    /** Its stock of resource {@code id}, in thousandths; 0 for one the match does not have. */
    public long stock(String id) {
        int r = q.config().rules().economy().indexOf(id);
        return r < 0 ? 0 : q.treasury(new FactionSlot(faction)).stock().get(r);
    }

    public int populationUsed() {
        return q.populationUsed(new FactionSlot(faction));
    }

    public int populationCap() {
        return q.populationCap(new FactionSlot(faction));
    }

    /** Its gatherers per resource, in the match's order, and last its idle ones. */
    public int[] gatherers() {
        return q.workers(new FactionSlot(faction));
    }

    /** Its own units. */
    public List<UnitView> units() {
        if (units == null) {
            List<UnitView> own = new ArrayList<>();
            List<UnitView> others = new ArrayList<>();
            for (int h : q.unitHandles()) {
                UnitView u = q.unit(h);
                if (u != null) {
                    (u.faction().value() == faction ? own : others).add(u);
                }
            }
            budget.spend(own.size() + others.size());
            units = own;
            enemies = others;
        }
        return units;
    }

    /** The other factions' units. */
    public List<UnitView> enemyUnits() {
        units();
        return enemies == null ? List.of() : enemies;
    }

    /** Its own buildings. */
    public List<BuildingView> buildings() {
        return buildingsOf(true);
    }

    /** The other factions' buildings. */
    public List<BuildingView> enemyBuildings() {
        return buildingsOf(false);
    }

    private List<BuildingView> buildingsOf(boolean own) {
        if (all == null) {
            all = q.buildings();
            budget.spend(all.size());
        }
        List<BuildingView> out = new ArrayList<>();
        for (BuildingView b : all) {
            if ((b.faction() == faction) == own) {
                out.add(b);
            }
        }
        return out;
    }

    /** The resource nodes. */
    public List<NodeView> nodes() {
        if (nodes == null) {
            nodes = q.nodes();
            budget.spend(nodes.size());
        }
        return nodes;
    }

    /** Whether template {@code id} carries class {@code token}. */
    public boolean is(TemplateId id, String token) {
        UnitTemplate t = q.templates().byId(id);
        return t != null && t.classes().contains(token);
    }

    /** Template {@code id}'s cost in resource {@code resource}, thousandths. */
    public long cost(TemplateId id, String resource) {
        UnitTemplate t = q.templates().byId(id);
        ValuePath path = q.templates().valuePaths().byPath("Cost/resources/" + resource);
        if (t == null || path == null) {
            return 0;
        }
        double v = q.templates().value(t.netIndex(), path.id());
        return Double.isFinite(v) && v > 0 ? Math.round(v * 1000) : 0;
    }

    /** Whether its stock covers template {@code id}'s cost. */
    public boolean affords(TemplateId id) {
        for (String r : resources()) {
            if (stock(r) < cost(id, r)) {
                return false;
            }
        }
        return true;
    }

    /** Whether building template {@code building} trains unit template {@code unit}: its {@code Trainer/units}. */
    public boolean trains(TemplateId building, TemplateId unit) {
        UnitTemplate t = q.templates().byId(building);
        ValuePath path = q.templates().tokenPaths().byPath("Trainer/units");
        String units = t == null || path == null ? null : q.templates().text(t.netIndex(), path.id());
        if (units == null) {
            return false;
        }
        for (String id : units.trim().split("\\s+", -1)) {
            if (id.equals(unit.value())) {
                return true;
            }
        }
        return false;
    }

    /** The building {@code u}'s current order builds, or 0 when it builds none. */
    public int buildingSite(UnitView u) {
        List<OrderQueues.Order> orders = q.orders(u.handle());
        return orders.isEmpty() || orders.get(0).kind() != OrderKind.BUILD
                ? 0
                : orders.get(0).target();
    }

    /** Whether {@code u}'s current order is to build: a builder on its way or at work. */
    public boolean building(UnitView u) {
        List<OrderQueues.Order> orders = q.orders(u.handle());
        return !orders.isEmpty() && orders.get(0).kind() == OrderKind.BUILD;
    }

    /** The resource unit {@code u} is set to gather, by its current order, or null when it gathers nothing. */
    public @Nullable String gathering(UnitView u) {
        List<OrderQueues.Order> orders = q.orders(u.handle());
        if (orders.isEmpty() || orders.get(0).kind() != OrderKind.GATHER) {
            return null;
        }
        int node = orders.get(0).target();
        for (NodeView n : nodes()) {
            if (n.id() == node) {
                return n.resource();
            }
        }
        return null;
    }
}
