package dev.strategos.core.sim;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.unitai.UnitAi;

/** A unit's coming and going (ECO-06): every store reset on spawn, population and claims given back on removal. */
final class UnitLifecycle {
    private UnitLifecycle() {}

    static void remove(MatchState m, int handle) {
        int i = Handle.index(handle);
        int faction = m.identity().faction(i);
        if (faction < m.config().factions()) { // Gaia has no population
            m.population().remove(faction, m.unitPopulation(i));
        }
        m.health().reset(i);
        m.stances().clear(i);
        m.garrison().remove(i);
        releaseClaim(m, i, handle);
        dropLoad(m, i);
        m.mobilityCache().removing(i);
        m.entities().destroy(handle);
    }

    static int spawn(MatchState m, int template, int faction, double x, double y, double z, float yaw) {
        int handle = m.entities().create();
        int index = Handle.index(handle);
        m.ensureCapacity(index + 1);
        m.identity().set(index, template, faction);
        m.mobilityCache().spawned(index);
        m.motion().set(index, x, y, z, yaw);
        m.orders().clear(index);
        m.paths().reset(index);
        m.motion().setExtra(index, MotionStore.Extra.NONE);
        m.stats().clearPersonal(index);
        m.health().reset(index);
        m.stances().clear(index);
        m.garrison().remove(index);
        m.ai().reset(index, UnitAi.IDLE);
        boolean player = faction < m.config().factions();
        if (player) {
            m.population().add(faction, m.unitPopulation(index));
        }
        m.outputs().add(new UnitCreated(handle, template, new FactionSlot(faction), x, y, z, yaw));
        if (player) {
            m.matchStats().unitTrained(faction);
        }
        return handle;
    }

    static void releaseClaim(MatchState m, int i, int handle) {
        var carry = m.carry();
        int node = carry.claimed(i);
        if (node != 0) {
            m.nodes().release(node, handle);
            carry.setClaimed(i, 0);
        }
        int site = carry.site(i);
        if (site != 0) {
            m.buildings().release(site, handle);
            carry.setSite(i, 0);
        }
    }

    static void dropLoad(MatchState m, int i) {
        int resource = m.carry().resource(i);
        long dropped = m.carry().clear(i);
        if (dropped > 0) {
            m.ledger().dropped(resource, dropped);
        }
    }
}
