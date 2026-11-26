package dev.strategos.core.sim;

import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.StartSlot;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.FactionLook;
import dev.strategos.core.protocol.MatchResults;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Stance;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.sim.build.BuildingView;
import dev.strategos.core.sim.econ.EconomyTimes;
import dev.strategos.core.sim.econ.FactionTreasury;
import dev.strategos.core.sim.econ.NodeView;
import dev.strategos.core.sim.nav.PathService;
import dev.strategos.core.sim.nav.PathStore;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.sim.stats.StatBreakdown;
import dev.strategos.core.sim.unitai.ActivityTier;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Read-only questions about a match at its last completed tick. */
@CoreApi
public interface MatchQueries {
    /** Why a template swap would be unsafe, or null when every live reference remains valid. */
    @Nullable String templateSwapProblem(TemplateSet next);

    /** The match's standing buildings, by slot (BLD-01). */
    List<BuildingView> buildings();

    /** The match's resource nodes, by id (ECO-02); empty before discovery. */
    List<NodeView> nodes();

    /** Faction {@code faction}'s treasury (ECO-01); the match's resources are {@code config().rules().economy()}. */
    FactionTreasury treasury(FactionSlot faction);

    /** The population faction {@code faction} uses, its units' and its reservations (ECO-06). */
    int populationUsed(FactionSlot faction);

    /** The most population faction {@code faction} may use now (ECO-06). */
    int populationCap(FactionSlot faction);

    /**
     * Faction {@code faction}'s gatherers (ECO-05): per resource of the economy those on a gather order at a node of
     * it, and last the idle ones.
     */
    int[] workers(FactionSlot faction);

    /** The stance a player gave a live unit (CMB-05), or null when it fights as its template says or is dead. */
    @Nullable Stance explicitStance(int handle);

    /** The ticks until live unit {@code handle} may use ability {@code ability} again (UNIT-10); 0 when it is ready or dead. */
    int abilityCooldown(int handle, String ability);

    /** Live unit {@code handle}'s rank (CMB-11): 0 for none, or when it is dead. */
    default int rank(int handle) {
        return 0;
    }

    /** Live unit {@code handle}'s experience in thousandths of a point (CMB-11); 0 when it has none or is dead. */
    default long experience(int handle) {
        return 0;
    }

    /** The resource a live unit carries, its index in the economy, or -1 when it carries none or is dead (ECO-05). */
    int carriedResource(int handle);

    /** How much a live unit carries, in thousandths; 0 when it carries none or is dead. */
    long carriedAmount(int handle);

    /** A live unit's hit points now (CMB-01): its most less the damage it took; NaN for no live unit. */
    double hitPoints(int handle);

    /** A live unit's most hit points, as modifiers leave them; NaN for no live unit. */
    double maxHitPoints(int handle);

    long tick();

    MatchConfig config();

    TemplateSet templates();

    int unitCount();

    boolean isAlive(int handle);

    @Nullable UnitView unit(int handle);

    /** The formation a live unit keeps a place in (ORD-06): its id, a group id of the match; 0 for none. */
    default int formationOf(int handle) {
        return 0;
    }

    /** Whether a live unit's current order is a patrol or a guard (ORD-07, UI-17): a guard whose charge died holds none. */
    default boolean holdsStandingOrder(int handle) {
        return false;
    }

    /**
     * How far a live unit stands from its place in its formation, in blocks (ORD-06); NaN for a unit in none, which
     * tests and the debug overlay read.
     */
    default double formationSlotError(int handle) {
        return Double.NaN;
    }

    /** A sorted copy; cold path. */
    int[] unitHandles();

    int[] unitHandlesOf(FactionSlot faction);

    /** The published terrain pages: an immutable grid, replaced when a page is published. */
    SurfaceGrid surfaces();

    /** Derived navigation memory across every mobility class; fake views may expose a single grid. */
    default long navigationBytes() {
        return surfaces().bytes();
    }

    /** The published pages by the hash of their slices, in page order. */
    List<PageRef> pageRefs();

    /** A live unit's path: its waypoints, the next one, and the request it waits for; null for a dead handle. */
    PathStore.@Nullable UnitPath path(int handle);

    /** A live unit's orders, the current one first (ORD-02); empty for an idle unit or a dead handle. */
    List<OrderQueues.Order> orders(int handle);

    /** How often each rung of the stuck ladder (1 to 5) was reached in this run, by rung; not saved (PATH-07). */
    long[] ladderRungs();

    /** Unit {@code handle}'s activity tier (UNIT-05), IDLE for a dead one. */
    ActivityTier tier(int handle);

    /** How many live units are in each activity tier, by {@link ActivityTier#code()}. */
    int[] tierCounts();

    /**
     * For an {@code ADAPTIVE} adapter about to step tick {@code tick}: the path requests in flight due by then whose jobs
     * have finished, ascending, which it hands in as that tick's {@link JobDelivery} (PATH-16).
     */
    int[] finishedPathJobs(long tick);

    /** A live unit's terrain motion: velocity, floor, stuck monitor, arrival latch; null for a dead handle. */
    MotionStore.@Nullable Extra motion(int handle);

    /**
     * How a live unit's value at a value path comes about: base, modifiers, result (ADR-0013); null for a dead handle or
     * a path the set lacks.
     */
    @Nullable StatBreakdown stats(int handle, String path);

    /** The techs {@code faction} has researched, in the order it researched them (UI-13). */
    default List<TechId> researched(FactionSlot faction) {
        return List.of();
    }

    /** {@code faction}'s phase ordinal, as its civilisation's phase rule tells it (CIV-05); 0 is Village. */
    int phase(FactionSlot faction);

    /** The class token of {@code faction}'s centres, from its civilisation (CIV-01). */
    String centreClass(FactionSlot faction);

    /** The template {@code faction}'s civilisation gives {@code role} (CIV-05), null when it has none. */
    @Nullable TemplateId role(FactionSlot faction, String role);

    /**
     * Whether a {@code building} of {@code faction} trains {@code unit}: its effective {@code Trainer/units} for that
     * faction (technologies and civilisation bonuses included) names it, and the faction's roster has it — the rule
     * production applies (CIV-05: the probe civilisation found the computer player reading the static list).
     */
    boolean trains(FactionSlot faction, TemplateId building, TemplateId unit);

    /**
     * What the rules {@code faction} plays imply for its computer player, a mask of {@code AiHint}s (CIV-05); every hint
     * for a faction outside the match.
     */
    default int aiHints(FactionSlot faction) {
        return dev.strategos.core.sim.civ.AiHint.ALL;
    }

    /** Whether {@code faction}'s civilisation uses resource {@code resource} (an id of the match's economy), CIV-05. */
    boolean usesResource(FactionSlot faction, String resource);

    /** The technology whose modifiers carry {@code sourceId} (TECH-01), or null for any other source. */
    default @Nullable TechId techOf(int sourceId) {
        return null;
    }

    /**
     * The civilisation whose bonuses carry {@code sourceId} for {@code faction} (CIV-01), or null for any other source:
     * what a stat breakdown names instead of the number.
     */
    default @Nullable CivDef civOf(int sourceId, FactionSlot faction) {
        return null;
    }

    /** The match results (M6 §2.3): winner, ended tick and per-faction statistics; null before ENDED. */
    default @Nullable MatchResults matchResults() {
        return null;
    }

    /** Each faction's equipment tiers (TECH-03), by faction; empty for a core without the tier values. */
    default List<FactionLook> factionLooks() {
        return List.of();
    }

    /** The path service's counts since the match started. */
    PathService.Stats pathStats();

    /** The blows on computer player {@code faction}'s units and buildings of the last 30 seconds, one a unit (AI-04). */
    default List<DefenceLog.Blow> blows(int faction) {
        return List.of();
    }

    /** Computer player {@code faction}'s destroyed buildings its rebuilder has not handled yet (AI-04). */
    default List<DefenceLog.Loss> losses(int faction) {
        return List.of();
    }

    /** The finished palisade gates and their doors' state now (BLD-13): for the mod's fence gates. */
    default List<dev.strategos.core.sim.build.GateDoor> gateDoors() {
        return List.of();
    }

    /** The blocks the core owns, as they must stand (MATCH-04): what a resumed match's reconciler puts back. */
    default List<WorldEffect> worldClaims() {
        return List.of();
    }

    /**
     * Why faction {@code faction}'s building of template {@code template} with its corner at ({@code x}, {@code z}),
     * unturned, breaks a placement rule of the ground (BLD-02's, as the server judges them), or null; cost aside. For a
     * computer player's site search (AI-02).
     */
    default @Nullable String placementRefusal(int template, int x, int z, int faction) {
        return null;
    }

    /** Building {@code building}'s capture points in thousandths, 0 for one that cannot be captured or is dead (AI-08). */
    default long captureTotal(int building) {
        return 0;
    }

    /** Building {@code building}'s owner's regeneration a second in thousandths, its garrison's included; 0 for none (AI-08). */
    default long captureRegen(int building) {
        return 0;
    }

    /** Live unit {@code unit}'s capture strength in thousandths a second, 0 for none or a dead unit (AI-08). */
    default long captureStrength(int unit) {
        return 0;
    }

    /** The places free in building {@code building} for live unit {@code unit}: 0 when it does not admit it or is full (AI-08). */
    default int garrisonRoom(int unit, int building) {
        return 0;
    }

    /**
     * Whether the research command's rule would take {@code tech} at {@code building} but for the price and the queue's
     * room: the building's effective list, the roster, what is researched, queued, superseded or excluded, and the
     * requirements (AI-09).
     */
    default boolean researchable(int building, dev.strategos.core.content.TechDef tech) {
        return false;
    }

    /**
     * Resource node {@code node}'s work slots, the cells round its base where gatherers stand, each packed as
     * {@code x << 16 | z} in match-local columns; none for an unknown node (AI-02's sites keep them free).
     */
    default int[] nodeSlotCells(int node) {
        return new int[0];
    }

    /** The live units inside building {@code building}, as handles; none for a dead building (AI-09's healing). */
    default int[] garrisonOccupants(int building) {
        return new int[0];
    }

    /** The faction owning the territory tile of match-local column ({@code x}, {@code z}), -1 for neutral or no grid (AI-08). */
    default int territoryOwner(int x, int z) {
        return -1;
    }

    /** The most buildings of template {@code template} faction {@code faction} may have, 0 for no limit (AI-08). */
    default int buildingLimit(int template, int faction) {
        return 0;
    }

    /** The standing height of match-local column ({@code x}, {@code z}), NaN for none: for reachability (AI-02). */
    default double floorAt(int x, int z) {
        return Double.NaN;
    }

    /** Whether a building's footprint blocks match-local column ({@code x}, {@code z}). */
    default boolean footprintAt(int x, int z) {
        return false;
    }

    /** PERF-08's combat systems' times so far. */
    default dev.strategos.core.sim.combat.CombatTimes.Stats combatTimes() {
        return new dev.strategos.core.sim.combat.CombatTimes.Stats(0, 0, 0, 0, 0);
    }

    /** PERF-16's territory, join, garrison and capture times so far. */
    default dev.strategos.core.diag.SystemTimes.Stats systemTimes() {
        return new dev.strategos.core.diag.SystemTimes.Stats(0, 0, 0, 0);
    }

    /** How many territory grids were delivered so far (0 before the first): PERF-16's cadence check. */
    default long territoryRecomputes() {
        return 0;
    }

    /** PERF-07.4's node index and dropsite query times so far. */
    default EconomyTimes.Stats economyTimes() {
        return new EconomyTimes.Stats(0, 0, 0, 0, 0, 0);
    }

    /**
     * The map's start slots (AI-18); empty until the match is seated on a map. Known to every faction under fog.
     */
    default List<StartSlot> startPositions() {
        return List.of();
    }

    /**
     * Whether {@code faction} sees unit {@code handle} under fog (AI-18 / FOW-01): own units, or an enemy the
     * {@code VisibilityIndex} holds. True when the map is revealed, AI vision is on, or vision is not yet wired.
     */
    default boolean visibleUnit(int faction, int handle) {
        return true;
    }

    /**
     * Whether {@code faction} sees building {@code building} under fog (AI-18): any cell of its footprint has vision.
     * True when the map is revealed, AI vision is on, or vision is not yet wired.
     */
    default boolean visibleBuilding(int faction, BuildingView building) {
        return true;
    }

    /**
     * Whether {@code faction} has explored the cell of block column ({@code x}, {@code z}) (MATCH-07): what a gather or
     * a placement there needs. True when the map is revealed or explored, AI vision is on, or vision is not wired.
     */
    default boolean explored(int faction, double x, double z) {
        return true;
    }
}
