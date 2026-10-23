package dev.strategos.core.sim.econ;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.world.WritePriority;

/**
 * Berry bushes (ECO-05, M3 spec §2.3): a bush holds its amount and regrows its rate a second up to what it was found
 * with, while it holds any; an emptied bush stays empty. Its block's {@code age} shows what it holds,
 * {@code ceil(3 × amount ÷ most)}, written by the core when that changes and once at discovery, never by random ticks
 * (the adapter denies a match bush's growth).
 */
@CoreApi
public final class Forage {
    /** The age of a bush that holds all it can: sweet berry bushes grow from 0 to 3. */
    public static final int STAGES = 3;

    private Forage() {}

    /** The age bush {@code node} shows. */
    public static int age(NodeStore nodes, int node) {
        long most = nodes.most(node);
        if (most <= 0) {
            return 0;
        }
        return (int) ((STAGES * nodes.amount(node) + most - 1) / most);
    }

    /** Takes up to {@code want} thousandths from bush {@code node}; what it gave. Its age follows. */
    public static long take(MatchState s, int node, long want) {
        NodeStore nodes = s.nodes();
        int before = age(nodes, node);
        long given = nodes.take(node, want);
        if (age(nodes, node) != before) {
            show(s, node);
        }
        return given;
    }

    /** One tick of regrowth for every bush; each gain goes to the ledger. */
    public static void regrow(MatchState s) {
        NodeStore nodes = s.nodes();
        for (int id = 1; id <= nodes.count(); id++) {
            if (nodes.regrow(id) == 0) {
                continue;
            }
            int before = age(nodes, id);
            long gained = nodes.grow(id);
            if (gained > 0) {
                s.ledger().regrown(nodes.resource(id), gained);
                if (age(nodes, id) != before) {
                    show(s, id);
                }
            }
        }
    }

    /** Every bush shows its age: at discovery, when the core does not yet know what the blocks show. */
    public static void showAll(MatchState s) {
        NodeStore nodes = s.nodes();
        for (int id = 1; id <= nodes.count(); id++) {
            if (nodes.kind(id) == MaterialKind.FORAGE) {
                show(s, id);
            }
        }
    }

    private static void show(MatchState s, int node) {
        NodeStore nodes = s.nodes();
        long[] blocks = nodes.blocks(node);
        if (nodes.gone(node) >= blocks.length) {
            return; // someone else broke it
        }
        long p = blocks[nodes.gone(node)];
        s.outputs()
                .add(new WorldEffect(
                        node,
                        WritePriority.COSMETIC,
                        NodeStore.x(p),
                        NodeStore.y(p),
                        NodeStore.z(p),
                        "[age=" + age(nodes, node) + "]",
                        0));
    }
}
