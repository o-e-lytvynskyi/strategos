package dev.strategos.core.testkit;

import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.content.UnitTemplate;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.build.BuildingView;
import java.util.ArrayList;
import java.util.List;

/**
 * PERF-08's battle (M5 spec §6) on the proving ground: each of two factions holds a civic centre with {@code garrison}
 * workers inside, and {@code fighters} spearmen, archers and horsemen in about equal shares of cost (6, 5 and 4 in 15,
 * their prices being 100, 120 and 150), sent across the river's fords by attack-move to a point within reach of the
 * other's centre, so the centres' arrows fire. Every {@value #WAVE_TICKS} ticks both sides are filled up again
 * and the new units sent; fighters never attack buildings by themselves, so the centres
 * stand. The same match gets the same commands.
 */
public final class BattleScene implements SoakScene {
    public static final int WAVE_TICKS = 100;
    public static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    public static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    public static final TemplateId CIVIC_CENTRE = FirstLoopScene.CIVIC_CENTRE;
    /** The roster, in the order of its shares of 15. */
    private static final TemplateId[] ROSTER = {TemplateSet.INFANTRY, ARCHER, HORSEMAN};

    private static final int[] SHARE = {6, 5, 4};
    private static final int SHARES = 15;
    private static final double BANK = 20.5;
    /** The fords' middles in Z; the waves come in at both. */
    private static final double[] FORD_Z = SoakWaves.FORD_Z;
    /** How far in front of the enemy centre a wave stops: within its arrows' reach (24). */
    private static final double FRONT = 16;

    private static final double YARD = 26;
    private static final int EVERY = 20;

    /** The soak's scale and the stress pass's, twice it (M5 spec §6): fighters and garrisoned workers a faction. */
    public record Config(int fighters, int garrison) implements SoakScene.Spec {
        public static final Config SOAK = new Config(150, 10);
        public static final Config STRESS = new Config(300, 20);

        @Override
        public int units() {
            return 2 * (fighters + garrison);
        }

        @Override
        public int unitsAtStart() {
            return 2 * garrison; // the fighters come with the waves
        }

        @Override
        public SoakScene create(List<StartSlot> starts) {
            return new BattleScene(this, starts);
        }
    }

    private final Config config;
    private final List<StartSlot> starts;
    private int waves;
    private int waveTicks;
    private boolean sendWave;

    public BattleScene(Config config, List<StartSlot> starts) {
        this.config = config;
        this.starts = starts;
    }

    @Override
    public List<Issue> setup(TemplateSet templates) {
        List<Issue> out = new ArrayList<>();
        for (int f = 0; f < 2; f++) {
            FactionSlot faction = new FactionSlot(f);
            StartSlot s = starts.get(f);
            UnitTemplate t = templates.byId(CIVIC_CENTRE);
            Blueprint bp = t == null ? null : templates.blueprint(t.netIndex());
            if (bp == null) {
                throw new IllegalArgumentException("no blueprint for " + CIVIC_CENTRE);
            }
            Footprint probe = Footprint.of(bp, 0, 0, 0);
            out.add(admin(new SpawnBuilding(
                    CIVIC_CENTRE,
                    faction,
                    (int) Math.floor(s.x() + 0.5) - probe.spanX() / 2,
                    (int) Math.floor(s.z() + 0.5) - probe.spanZ() / 2,
                    0)));
            out.add(admin(new SpawnUnits(
                    TemplateSet.WORKER,
                    faction,
                    new Vec2d(s.x() + 0.5, s.z() + 0.5 - mirror(f) * YARD),
                    config.garrison(),
                    4)));
        }
        return out;
    }

    @Override
    public List<Issue> next(MatchQueries q) {
        List<Issue> out = new ArrayList<>();
        if (q.tick() % EVERY == 0) {
            garrison(q, out);
        }
        int[][] fighters = new int[2][ROSTER.length];
        for (int f = 0; f < 2; f++) {
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                int role = u == null ? -1 : roleOf(u.template());
                if (role >= 0) {
                    fighters[f][role]++;
                }
            }
        }
        if (sendWave) {
            sendWave = false;
            waves++;
            waveTicks = 0;
            for (int f = 0; f < 2; f++) {
                List<Integer> idle = new ArrayList<>();
                for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                    UnitView u = q.unit(handle);
                    if (u != null && u.state() == UnitState.IDLE && roleOf(u.template()) >= 0) {
                        idle.add(handle);
                    }
                }
                if (!idle.isEmpty()) {
                    out.add(admin(AttackMove.replace(
                            UnitSet.of(idle.stream().mapToInt(Integer::intValue).toArray()), front(1 - f))));
                }
            }
            return out;
        }
        if (++waveTicks < WAVE_TICKS && waves > 0) {
            return out;
        }
        for (int f = 0; f < 2; f++) {
            for (int role = 0; role < ROSTER.length; role++) {
                int missing = quota(role) - fighters[f][role];
                for (int k = 0; k < 2 && missing > 0; k++) {
                    int here = k == 0 ? (missing + 1) / 2 : missing / 2;
                    if (here > 0) {
                        out.add(admin(new SpawnUnits(
                                ROSTER[role], new FactionSlot(f), new Vec2d(bank(f), FORD_Z[k]), here, 10)));
                    }
                }
            }
        }
        sendWave = true; // the tick after, when the new units are in the match
        return out;
    }

    /** Each faction's workers not yet inside its centre go in. */
    private void garrison(MatchQueries q, List<Issue> out) {
        for (int f = 0; f < 2; f++) {
            int centre = -1;
            for (BuildingView b : q.buildings()) {
                if (b.faction() == f && b.template().equals(CIVIC_CENTRE)) {
                    centre = b.handle();
                }
            }
            if (centre < 0) {
                continue;
            }
            List<Integer> outside = new ArrayList<>();
            for (int handle : q.unitHandlesOf(new FactionSlot(f))) {
                UnitView u = q.unit(handle);
                if (u != null && u.template().equals(TemplateSet.WORKER) && u.state() != UnitState.GARRISONED) {
                    outside.add(handle);
                }
            }
            if (!outside.isEmpty()) {
                out.add(new Issue(
                        new PlayerSlot(f),
                        new Garrison(
                                UnitSet.of(outside.stream()
                                        .mapToInt(Integer::intValue)
                                        .toArray()),
                                centre)));
            }
        }
    }

    /** A faction's fighters of the roster's {@code role}: its share of 15, the spearmen taking the remainder. */
    private int quota(int role) {
        int share = config.fighters() * SHARE[role] / SHARES;
        return role == 0 ? config.fighters() - config.fighters() * (SHARE[1] + SHARE[2]) / SHARES : share;
    }

    private static int roleOf(TemplateId template) {
        for (int role = 0; role < ROSTER.length; role++) {
            if (ROSTER[role].equals(template)) {
                return role;
            }
        }
        return -1;
    }

    /** Where faction {@code f}'s units come in: its bank of the river. */
    private static double bank(int f) {
        return f == 0 ? ProvingGround.RIVER_X - BANK : ProvingGround.RIVER_X + BANK;
    }

    /** The point in front of faction {@code f}'s centre, toward the river, where the other's wave stops. */
    private Vec2d front(int f) {
        StartSlot s = starts.get(f);
        return new Vec2d(s.x() + 0.5 + mirror(f) * FRONT, s.z() + 0.5);
    }

    /** +1 for the west start, -1 for the east one. */
    private int mirror(int f) {
        return starts.get(f).x() < ProvingGround.SIZE / 2 ? 1 : -1;
    }

    @Override
    public void placementResult(boolean accepted) {}

    @Override
    public long placed() {
        return 0;
    }

    @Override
    public long refusedPlacements() {
        return 0;
    }

    @Override
    public int waves() {
        return waves;
    }

    private static Issue admin(dev.strategos.core.protocol.Command c) {
        return new Issue(PlayerSlot.ADMIN, c);
    }
}
