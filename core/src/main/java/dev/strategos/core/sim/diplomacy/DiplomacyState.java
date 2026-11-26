package dev.strategos.core.sim.diplomacy;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.PlayerSlot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Immutable core authority state, updated by the match's single writer. Mutation methods return a new state;
 * previous views remain valid snapshots. All iteration is by faction/player slot, never insertion order.
 *
 * <p>Bindings are setup authority, not connection status: disconnecting does not unbind a seat. The caller must
 * authenticate setup/grant intents. A grantor can delegate only its primary faction, never an inherited grant.
 * MatchState owns the current immutable value; journaled setup and schema 4 preserve it. Asset command authority,
 * private-query routing and UUID roster integration retain their separate M7 T8/T9/T5 gates.
 */
@CoreApi
public final class DiplomacyState implements DiplomacyView {
    private static final int SEATS = FactionSlot.MAX + 1;
    private static final int UNBOUND = -1;

    private final int[] teams;
    private final int[] enemyMasks;
    private final int[] bindings;
    private final int[] grants; // receiving player -> bitset of granting factions
    private final boolean locked;
    private final boolean legacyIdentity;

    /** Creates configured factions with distinct teams and no player bindings, including no implicit identity map. */
    public DiplomacyState(int factions) {
        if (factions < 1 || factions > SEATS) {
            throw new IllegalArgumentException("factions outside 1.." + SEATS);
        }
        teams = new int[factions];
        for (int f = 0; f < factions; f++) {
            teams[f] = f;
        }
        enemyMasks = enemyMasks(teams);
        bindings = new int[SEATS];
        Arrays.fill(bindings, UNBOUND);
        grants = new int[SEATS];
        locked = false;
        legacyIdentity = false;
    }

    private DiplomacyState(int[] teams, int[] bindings, int[] grants, boolean locked) {
        this.teams = teams;
        enemyMasks = enemyMasks(teams);
        this.bindings = bindings;
        this.grants = grants;
        this.locked = locked;
        legacyIdentity = legacyIdentity(teams, bindings, grants, locked);
    }

    /** Explicit migration/default setup for the legacy one-player-per-faction authority model. */
    public static DiplomacyState identity(int factions) {
        DiplomacyState state = new DiplomacyState(factions);
        for (int f = 0; f < factions; f++) {
            state = state.bind(new PlayerSlot(f), new FactionSlot(f));
        }
        return state;
    }

    @Override
    public int factions() {
        return teams.length;
    }

    @Override
    public Optional<FactionSlot> factionOf(PlayerSlot player) {
        int faction = primaryFaction(player);
        return faction == UNBOUND ? Optional.empty() : Optional.of(new FactionSlot(faction));
    }

    @Override
    public int teamOf(FactionSlot faction) {
        return teams[requireFaction(faction)];
    }

    @Override
    public boolean teamsLocked() {
        return locked;
    }

    @Override
    public boolean allied(FactionSlot first, FactionSlot second) {
        return alliedFactions(first.value(), second.value());
    }

    /** Primitive kernel query; absent factions, including Gaia, are never allies. */
    public boolean alliedFactions(int first, int second) {
        return first >= 0 && second >= 0 && first < factions() && second < factions() && teams[first] == teams[second];
    }

    /** The opponents of a configured faction. An absent source has no team. */
    public int enemyMask(int faction) {
        return faction >= 0 && faction < teams.length ? enemyMasks[faction] : (1 << teams.length) - 1;
    }

    private static int[] enemyMasks(int[] teams) {
        int[] allies = new int[SEATS];
        for (int f = 0; f < teams.length; f++) allies[teams[f]] |= 1 << f;
        int all = (1 << teams.length) - 1;
        int[] result = new int[teams.length];
        for (int f = 0; f < teams.length; f++) result[f] = all & ~allies[teams[f]];
        return result;
    }

    /** Allocation-free primary-faction query; admin and unbound players have no primary faction. */
    public int primaryFaction(PlayerSlot player) {
        return player.isAdmin() ? UNBOUND : bindings[player.value()];
    }

    /** Lowest bound primary controller, or -1. AI never impersonates a numeric faction seat. */
    public int firstPrimaryPlayer(int faction) {
        if (faction < 0 || faction >= factions()) return UNBOUND;
        for (int p = 0; p < SEATS; p++) {
            if (bindings[p] == faction) return p;
        }
        return UNBOUND;
    }

    @Override
    public boolean controls(PlayerSlot player, FactionSlot faction) {
        return controlsFaction(player, faction.value());
    }

    /** Allocation-free command authority. Gaia and absent factions remain outside diplomacy. */
    public boolean controlsFaction(PlayerSlot player, int faction) {
        if (faction < 0 || faction >= factions()) return false;
        if (player.isAdmin()) return true;
        int primary = primaryFaction(player);
        return primary != UNBOUND && (primary == faction || (grants[player.value()] & (1 << faction)) != 0);
    }

    public DiplomacyState withTeam(FactionSlot faction, int team) {
        int f = requireFaction(faction);
        if (team < 0 || team >= SEATS) {
            throw new IllegalArgumentException("team outside 0.." + FactionSlot.MAX);
        }
        if (teams[f] == team) {
            return this;
        }
        if (locked) {
            throw new IllegalStateException("teams are locked");
        }
        int[] nextTeams = teams.clone();
        nextTeams[f] = team;
        return normalized(nextTeams, bindings, grants, locked);
    }

    public DiplomacyState lockTeams() {
        return locked ? this : new DiplomacyState(teams, bindings, grants, true);
    }

    /** A changed seat loses all received grants; removing a faction's last primary seat revokes its grants too. */
    public DiplomacyState bind(PlayerSlot player, FactionSlot faction) {
        return bindTo(requirePlayer(player), requireFaction(faction));
    }

    public DiplomacyState unbind(PlayerSlot player) {
        return bindTo(requirePlayer(player), UNBOUND);
    }

    private DiplomacyState bindTo(int player, int faction) {
        if (bindings[player] == faction) {
            return this;
        }
        int[] nextBindings = bindings.clone();
        nextBindings[player] = faction;
        int[] nextGrants = grants.clone();
        nextGrants[player] = 0;
        return normalized(teams, nextBindings, nextGrants, locked);
    }

    public DiplomacyState grant(PlayerSlot grantor, PlayerSlot recipient) {
        int faction = requirePrimary(grantor);
        return grantFrom(faction, requirePlayer(recipient));
    }

    public DiplomacyState revoke(PlayerSlot grantor, PlayerSlot recipient) {
        int faction = requirePrimary(grantor);
        int player = requirePlayer(recipient);
        int[] next = grants.clone();
        next[player] &= ~(1 << faction);
        return new DiplomacyState(teams, bindings, next, locked);
    }

    private DiplomacyState grantFrom(int faction, int player) {
        int primary = bindings[player];
        if (primary == UNBOUND
                || primary == faction
                || teams[primary] != teams[faction]
                || !hasPrimary(bindings, faction)) {
            throw new IllegalArgumentException("control grants require distinct, present allied participants");
        }
        int[] next = grants.clone();
        next[player] |= 1 << faction;
        return new DiplomacyState(teams, bindings, next, locked);
    }

    private static DiplomacyState normalized(int[] teams, int[] bindings, int[] grants, boolean locked) {
        int[] next = grants.clone();
        for (int p = 0; p < SEATS; p++) {
            for (int f = 0; f < teams.length; f++) {
                int primary = bindings[p];
                if (primary == UNBOUND || primary == f || teams[primary] != teams[f] || !hasPrimary(bindings, f)) {
                    next[p] &= ~(1 << f);
                }
            }
        }
        return new DiplomacyState(teams, bindings, next, locked);
    }

    private static boolean hasPrimary(int[] bindings, int faction) {
        for (int primary : bindings) {
            if (primary == faction) {
                return true;
            }
        }
        return false;
    }

    /** Canonical slot order; this contract does not choose or change the match save schema. */
    public Snapshot snapshot() {
        List<Integer> teamList = new ArrayList<>(teams.length);
        for (int team : teams) {
            teamList.add(team);
        }
        List<Binding> bindingList = new ArrayList<>();
        List<Grant> grantList = new ArrayList<>();
        for (int p = 0; p < SEATS; p++) {
            PlayerSlot player = new PlayerSlot(p);
            if (bindings[p] != UNBOUND) {
                bindingList.add(new Binding(player, new FactionSlot(bindings[p])));
            }
            for (int f = 0; f < teams.length; f++) {
                if ((grants[p] & (1 << f)) != 0) {
                    grantList.add(new Grant(new FactionSlot(f), player));
                }
            }
        }
        return new Snapshot(teamList, bindingList, grantList, locked);
    }

    /** Validates the complete contract before publishing a reconstructed state; malformed authority is rejected. */
    public static DiplomacyState restore(Snapshot saved) {
        int n = saved.teams().size();
        if (n < 1 || n > SEATS) throw new IllegalArgumentException("factions outside 1.." + SEATS);
        int[] teams = new int[n];
        int[] bindings = new int[SEATS];
        int[] grants = new int[SEATS];
        Arrays.fill(bindings, UNBOUND);
        for (int f = 0; f < n; f++) {
            int team = saved.teams().get(f);
            if (team < 0 || team >= SEATS) throw new IllegalArgumentException("invalid saved team");
            teams[f] = team;
        }
        for (Binding binding : saved.bindings()) {
            int p = requirePlayer(binding.player());
            int f = binding.faction().value();
            if (bindings[p] != UNBOUND) throw new IllegalArgumentException("duplicate player binding");
            if (f >= n) throw new IllegalArgumentException("faction outside this match");
            bindings[p] = f;
        }
        for (Grant grant : saved.grants()) {
            int f = grant.faction().value();
            int p = requirePlayer(grant.recipient());
            int primary = bindings[p];
            if (f >= n
                    || primary == UNBOUND
                    || primary == f
                    || teams[primary] != teams[f]
                    || !hasPrimary(bindings, f)) {
                throw new IllegalArgumentException("control grants require distinct, present allied participants");
            }
            if ((grants[p] & (1 << f)) != 0) throw new IllegalArgumentException("duplicate control grant");
            grants[p] |= 1 << f;
        }
        return new DiplomacyState(teams, bindings, grants, saved.teamsLocked());
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other
                || (other instanceof DiplomacyState d
                        && locked == d.locked
                        && Arrays.equals(teams, d.teams)
                        && Arrays.equals(bindings, d.bindings)
                        && Arrays.equals(grants, d.grants));
    }

    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(teams);
        hash = 31 * hash + Arrays.hashCode(bindings);
        hash = 31 * hash + Arrays.hashCode(grants);
        return 31 * hash + (locked ? 1 : 0);
    }

    public boolean isLegacyIdentity() {
        return legacyIdentity;
    }

    private static boolean legacyIdentity(int[] teams, int[] bindings, int[] grants, boolean locked) {
        if (locked) return false;
        for (int f = 0; f < teams.length; f++) {
            if (teams[f] != f) return false;
        }
        for (int p = 0; p < SEATS; p++) {
            if (bindings[p] != (p < teams.length ? p : UNBOUND) || grants[p] != 0) return false;
        }
        return true;
    }

    /** Allocation-free contribution for the running match hash; order matches hashInto. */
    public long mixHash(long hash) {
        long h = Hasher.mix(Hasher.mix(hash, factions()), locked ? 1 : 0);
        for (int team : teams) h = Hasher.mix(h, team);
        for (int p = 0; p < SEATS; p++) {
            h = Hasher.mix(Hasher.mix(h, bindings[p]), grants[p]);
        }
        return h;
    }

    /** Fixed slot order includes absence, teams, grants and the lock; T2b must wire it into the match hash. */
    public void hashInto(Hasher hasher) {
        hasher.putInt(factions()).putBoolean(locked);
        for (int team : teams) {
            hasher.putInt(team);
        }
        for (int p = 0; p < SEATS; p++) {
            hasher.putInt(bindings[p]).putInt(grants[p]);
        }
    }

    private int requireFaction(FactionSlot faction) {
        if (faction.value() >= factions()) {
            throw new IllegalArgumentException("faction outside this match");
        }
        return faction.value();
    }

    private static int requirePlayer(PlayerSlot player) {
        if (player.isAdmin()) {
            throw new IllegalArgumentException("ADMIN is not a player binding");
        }
        return player.value();
    }

    private int requirePrimary(PlayerSlot player) {
        int faction = bindings[requirePlayer(player)];
        if (faction == UNBOUND) {
            throw new IllegalArgumentException("player has no primary faction");
        }
        return faction;
    }

    @CoreApi
    public record Binding(PlayerSlot player, FactionSlot faction) {}

    @CoreApi
    public record Grant(FactionSlot faction, PlayerSlot recipient) {}

    @CoreApi
    public record Snapshot(List<Integer> teams, List<Binding> bindings, List<Grant> grants, boolean teamsLocked) {
        public Snapshot {
            teams = List.copyOf(teams);
            bindings = List.copyOf(bindings);
            grants = List.copyOf(grants);
        }
    }
}
