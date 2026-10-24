package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitView;
import java.util.ArrayList;
import java.util.List;

/**
 * The soaks' waves (M3 spec §6): both sides filled up to {@code soldiers} spearmen at their bank's fords, then sent
 * across to the other's with attack-move; a wave ends when a side is gone or after {@value #WAVE_TICKS} ticks.
 */
final class SoakWaves {
    static final int WAVE_TICKS = 600;
    static final double[] FORD_Z = {143.5, 239.5};
    private static final double BANK = 20.5;

    private final int soldiers;
    private int waves;
    private int waveTicks;
    private boolean sendWave;

    SoakWaves(int soldiers) {
        this.soldiers = soldiers;
    }

    int waves() {
        return waves;
    }

    /** This tick's wave commands into {@code out}. */
    void next(MatchQueries q, List<SoakScene.Issue> out) {
        int[][] sides = new int[2][];
        for (int f = 0; f < 2; f++) {
            List<Integer> own = new ArrayList<>();
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                if (u != null && u.template().equals(TemplateSet.INFANTRY)) {
                    own.add(handle);
                }
            }
            sides[f] = own.stream().mapToInt(Integer::intValue).toArray();
        }
        if (sendWave) {
            sendWave = false;
            waves++;
            waveTicks = 0;
            for (int f = 0; f < 2; f++) {
                for (int k = 0; k < 2; k++) {
                    List<Integer> here = new ArrayList<>();
                    for (int unit : sides[f]) {
                        UnitView u = q.unit(unit);
                        if (u != null && (u.z() < 191.5) == (k == 0)) {
                            here.add(unit);
                        }
                    }
                    if (!here.isEmpty()) {
                        out.add(admin(AttackMove.replace(
                                UnitSet.of(here.stream()
                                        .mapToInt(Integer::intValue)
                                        .toArray()),
                                new Vec2d(bank(1 - f), FORD_Z[k]))));
                    }
                }
            }
            return;
        }
        if (++waveTicks < WAVE_TICKS && sides[0].length > 0 && sides[1].length > 0 && waves > 0) {
            return;
        }
        for (int f = 0; f < 2; f++) {
            int count = soldiers - sides[f].length;
            for (int k = 0; k < 2 && count > 0; k++) {
                int here = k == 0 ? (count + 1) / 2 : count / 2;
                if (here > 0) {
                    out.add(admin(new SpawnUnits(
                            TemplateSet.INFANTRY, new FactionSlot(f), new Vec2d(bank(f), FORD_Z[k]), here, 5)));
                }
            }
        }
        sendWave = true; // the tick after, when the new units are in the match
    }

    private static double bank(int f) {
        return f == 0 ? ProvingGround.RIVER_X - BANK : ProvingGround.RIVER_X + BANK;
    }

    private static SoakScene.Issue admin(dev.strategos.core.protocol.Command c) {
        return new SoakScene.Issue(PlayerSlot.ADMIN, c);
    }
}
