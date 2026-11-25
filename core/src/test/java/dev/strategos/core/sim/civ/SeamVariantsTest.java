package dev.strategos.core.sim.civ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import dev.strategos.core.client.build.ClientPlacement;
import dev.strategos.core.client.build.ClientWall;
import dev.strategos.core.client.hud.ClientFactionView;
import dev.strategos.core.content.CivDef;
import dev.strategos.core.content.PlacementRules;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Demolish;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.Gather;
import dev.strategos.core.protocol.PlaceBuilding;
import dev.strategos.core.protocol.PlaceWall;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SetRally;
import dev.strategos.core.protocol.SpawnBuilding;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.Train;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.CivId;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.FactionDefeated;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchEnded;
import dev.strategos.core.sim.MatchFactionView;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.ai.AiBudget;
import dev.strategos.core.sim.ai.AiView;
import dev.strategos.core.sim.ai.ComputerPlayer;
import dev.strategos.core.sim.build.Placing;
import dev.strategos.core.sim.econ.Amount;
import dev.strategos.core.sim.econ.EconomyChecks;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.sim.nav.Material;
import dev.strategos.core.sim.nav.MaterialKind;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.prod.Techs;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.TreeFixtures;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CIV-05: each quiet seam is honoured. One test-only variant per seam ({@link TestRules}), selected by a civilisation
 * of the test set for faction 0, the default civilisation for faction 1, run through a catalogue built with {@code with}:
 * a phase from a building count, a defeat while any unit lives, placement ignoring territory, a faction that does not use
 * stone, and passive income through the treasury's {@code granted}. A match through such a catalogue replays to the live
 * hash when the replay is given the same catalogue.
 */
class SeamVariantsTest {
    private static final dev.strategos.core.shared.TemplateId PALISADE =
            new dev.strategos.core.shared.TemplateId("strategos:building/palisade");
    private static final List<CivId> LEAN_VS_DEFAULT = List.of(CivScene.LEAN, CivId.DEFAULT);

    private static final MatchRules CONQUEST_OFF =
            MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.NONE, false, true, false));
    private static final MatchRules CENTRES =
            MatchRules.DEFAULTS.withPlay(new PlayRules(PlayRules.Victory.CIVIC_CENTRE, false, true, false));

    private static SimHarness lean(String rules) {
        return lean(rules, CONQUEST_OFF);
    }

    private static SimHarness lean(String rules, MatchRules play) {
        return CivScene.match(CivScene.with(TestRules.civ(rules)), LEAN_VS_DEFAULT, TestRules.catalogue(), play);
    }

    private static void assertReplays(SimHarness h) {
        ReplayResult replay = h.replay();
        assertThat(replay.matches()).as("the replay reaches every checkpoint").isTrue();
        assertThat(replay.finalHash()).as("and the live hash").isEqualTo(h.stateHash());
    }

    @Test
    void aPhaseRuleTellsThePhase() {
        SimHarness h = lean("{\"phase\":{\"id\":\"buildings\",\"params\":{\"cap\":2}}}");
        MatchState s = KernelAccess.state(h.core());

        assertThat(Techs.phase(s, 0)).as("one finished building").isEqualTo(1);
        assertThat(Techs.phase(s, 1))
                .as("the default civilisation: none researched")
                .isZero();
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(0), 20, 20, 0));
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(0), 24, 20, 0));
        h.step();
        assertThat(Techs.phase(s, 0)).as("three buildings, the cap is 2").isEqualTo(2);
        assertThat(Techs.phase(s, 1)).isZero();

        MatchFactionView lean = new MatchFactionView(s, 0);
        MatchFactionView plain = new MatchFactionView(s, 1);
        assertThat(lean.reached("village")).isTrue();
        assertThat(lean.reached("town"))
                .as("phase 2 is at least Town's ordinal")
                .isTrue();
        assertThat(plain.reached("town"))
                .as("the default rule: Town researched, or not")
                .isFalse();
        assertThat(lean.reached("nonesuch")).isFalse();
        assertThat(ReplicationService.factionState(h.queries(), 1, new FactionSlot(0))
                        .phase())
                .as("what the HUD is told")
                .isEqualTo(2);
        assertThat(ReplicationService.factionState(h.queries(), 1, new FactionSlot(1))
                        .phase())
                .isZero();
        assertReplays(h);
    }

    @Test
    void aDefeatRuleDecidesWhoStaysInPlay() {
        SimHarness h = lean("{\"defeat\":{\"id\":\"units\"}}", CENTRES);
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(CivScene.WORKER, new FactionSlot(0), new Vec2d(20, 20), 1, 0));
        int worker = h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
        h.runTicks(2);
        assertThat(s.outcome().held(0)).as("it holds through its unit").isTrue();

        h.submit(new PlayerSlot(0), new Demolish(CivScene.building(h, 0, CivScene.CENTRE, 2, 2))); // journaled
        h.runTicks(40);
        assertThat(h.events(FactionDefeated.class))
                .as("its centre fell, a unit lives")
                .isEmpty();
        assertThat(h.events(MatchEnded.class)).isEmpty();

        h.damage(new ExternalDamage(
                worker, ExternalDamage.Kind.HACK, 1_000_000f, ExternalDamage.SourceKind.NONE)); // journaled
        h.runTicks(5);
        assertThat(h.events(FactionDefeated.class))
                .containsExactly(new FactionDefeated(new FactionSlot(0), FactionDefeated.CENTRE_LOST));
        assertThat(h.events(MatchEnded.class)).hasSize(1);
        assertThat(h.events(MatchEnded.class).get(0).winner()).isEqualTo(1);
        assertReplays(h);
    }

    @Test
    void aPlacementRuleChangesWhereABuildingMayStand() {
        TemplateSet set = CivScene.with(TestRules.civ("{\"placement\":{\"id\":\"anywhere\"}}"));
        SimHarness variant = CivScene.match(set, LEAN_VS_DEFAULT, TestRules.catalogue(), CONQUEST_OFF);
        SimHarness control =
                CivScene.match(set, List.of(CivId.DEFAULT, CivId.DEFAULT), TestRules.catalogue(), CONQUEST_OFF);
        int house = KernelAccess.state(variant.core())
                .templates()
                .byId(CivScene.HOUSE)
                .netIndex();
        for (SimHarness h : List.of(variant, control)) {
            MatchState s = KernelAccess.state(h.core());
            assertThat(h.runUntil(
                            () -> s.play().territorySystem() != null
                                    && s.play().territorySystem().recomputes() > 0,
                            400))
                    .as("a territory grid")
                    .isTrue();
        }

        int x = -1;
        int z = -1;
        for (int cx = 30; cx < 64 && x < 0; cx += 2) { // a spot where the default rule says TERRITORY
            for (int cz = 30; cz < 64 && x < 0; cz += 2) {
                if ("TERRITORY".equals(control.queries().placementRefusal(house, cx, cz, 0))) {
                    x = cx;
                    z = cz;
                }
            }
        }
        assertThat(x).as("some spot outside faction 0's own land").isPositive();
        assertThat(variant.queries().placementRefusal(house, x, z, 0))
                .as("the variant ignores territory")
                .isNull();

        int builder = worker(variant, 0, x, z - 3);
        variant.submit(
                new PlayerSlot(0), new PlaceBuilding(CivScene.HOUSE, x, z, 0, UnitSet.of(builder), QueueMode.REPLACE));
        variant.step();
        CommandResult placed = variant.results().get(variant.results().size() - 1);
        assertThat(placed.outcome()).isEqualTo(CommandOutcome.ACCEPTED);

        int other = worker(control, 0, x, z - 3);
        control.submit(
                new PlayerSlot(0), new PlaceBuilding(CivScene.HOUSE, x, z, 0, UnitSet.of(other), QueueMode.REPLACE));
        control.step();
        CommandResult refused = control.results().get(control.results().size() - 1);
        assertThat(refused.outcome()).isEqualTo(CommandOutcome.REJECTED);
        assertThat(refused.firstReason()).isEqualTo(RejectReason.OUTSIDE_TERRITORY);
        assertReplays(variant);
    }

    private static int worker(SimHarness h, int faction, int x, int z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(CivScene.WORKER, new FactionSlot(faction), new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aResourceRuleKeepsAFactionFromGatheringWhatItDoesNotUse() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        for (int[] at : new int[][] {{14, 14}, {46, 46}}) { // a rock beside each centre
            for (int x = at[0]; x < at[0] + 3; x++) {
                for (int z = at[1]; z < at[1] + 3; z++) {
                    for (int y = 1; y <= 2; y++) {
                        world.set(x, y, z, VoxelFixtures.SOLID);
                        world.material(x, y, z, new Material(MaterialKind.STONE, "stone", "rock", Amount.of(5)));
                    }
                }
            }
        }
        TemplateSet set = CivScene.with(TestRules.civ("{\"resources\":{\"id\":\"skip\",\"params\":{\"index\":2}}}"));
        SimHarness h = CivScene.match(set, LEAN_VS_DEFAULT, TestRules.catalogue(), CONQUEST_OFF, world);
        MatchState s = KernelAccess.state(h.core());
        int stone = resourceIndex(s, "stone");
        assertThat(stone).isEqualTo(2);
        assertThat(s.civs().rules(0).resource().uses(0, stone)).isFalse();
        assertThat(s.civs().rules(0).resource().uses(0, 0)).isTrue();
        assertThat(s.civs().rules(1).resource().uses(1, stone)).isTrue();

        int mine = worker(h, 0, 12, 12);
        int theirs = worker(h, 1, 44, 44);
        h.submit(new PlayerSlot(0), Gather.replace(UnitSet.of(mine), 14, 2, 14));
        h.submit(new PlayerSlot(1), Gather.replace(UnitSet.of(theirs), 46, 2, 46));
        h.step();
        List<CommandResult> orders = h.results();
        assertThat(orders.get(orders.size() - 2).outcome())
                .as("faction 0's order")
                .isEqualTo(CommandOutcome.REJECTED);
        assertThat(orders.get(orders.size() - 2).firstReason()).isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(orders.get(orders.size() - 1).outcome())
                .as("faction 1's order")
                .isEqualTo(CommandOutcome.ACCEPTED);
        h.runTicks(1_200);

        assertThat(s.treasury().gathered(1, stone))
                .as("the default civilisation mines")
                .isPositive();
        assertThat(s.treasury().gathered(0, stone))
                .as("faction 0 does not use stone")
                .isZero();

        SimHarness control =
                CivScene.match(set, List.of(CivId.DEFAULT, CivId.DEFAULT), TestRules.catalogue(), CONQUEST_OFF, world);
        int same = worker(control, 0, 12, 12);
        control.submit(new PlayerSlot(0), Gather.replace(UnitSet.of(same), 14, 2, 14));
        control.runTicks(1_200);
        assertThat(KernelAccess.state(control.core()).treasury().gathered(0, stone))
                .as("the same worker on the same rock, with the default rule")
                .isPositive();
        assertReplays(h);
    }

    private static VoxelWorld rocks() {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        for (int x = 14; x < 17; x++) {
            for (int z = 14; z < 17; z++) {
                for (int y = 1; y <= 2; y++) {
                    world.set(x, y, z, VoxelFixtures.SOLID);
                    world.material(x, y, z, new Material(MaterialKind.STONE, "stone", "rock", Amount.of(5)));
                }
            }
        }
        return world;
    }

    private static final String SKIP_STONE = "{\"resources\":{\"id\":\"skip\",\"params\":{\"index\":2}}}";

    private static int firstOrderKindOfNewWorker(SimHarness h) {
        MatchState s = KernelAccess.state(h.core());
        int centre = CivScene.building(h, 0, CivScene.CENTRE, 2, 2);
        h.submit(new PlayerSlot(0), new SetRally(UnitSet.of(centre), 14, 2, 14)); // a rock of stone
        h.submit(new PlayerSlot(0), new Train(centre, CivScene.WORKER));
        for (int t = 0; t < 900; t++) {
            for (var e : h.step().events()) {
                if (e instanceof UnitCreated made) {
                    return s.orders().kind(Handle.index(made.handle()));
                }
            }
        }
        throw new AssertionError("no worker trained");
    }

    @Test
    void aRallyToAnUnusedNodeSendsNoWorkersToGatherIt() {
        TemplateSet set = CivScene.with(TestRules.civ(SKIP_STONE));
        SimHarness variant = CivScene.match(set, LEAN_VS_DEFAULT, TestRules.catalogue(), CONQUEST_OFF, rocks());
        SimHarness control = CivScene.match(
                set, List.of(CivId.DEFAULT, CivId.DEFAULT), TestRules.catalogue(), CONQUEST_OFF, rocks());

        assertThat(firstOrderKindOfNewWorker(control))
                .as("the default rule: gather")
                .isEqualTo(OrderKind.GATHER);
        assertThat(firstOrderKindOfNewWorker(variant))
                .as("no stone for faction 0: walk to it")
                .isEqualTo(OrderKind.MOVE);
    }

    @Test
    void theAiDoesNotPickANodeOfAResourceItsFactionDoesNotUse() throws Exception {
        VoxelWorld world = VoxelFixtures.flat(64, 64, 32, 1);
        TreeFixtures.draw(world, TreeFixtures.Species.OAK, 30, 1, 30);
        String skipWood = "{\"resources\":{\"id\":\"skip\",\"params\":{\"index\":1}}}";
        TemplateSet set = CivScene.with(TestRules.civ(skipWood));
        String script =
                Files.readString(Path.of("../mod/src/main/resources/data/strategos/strategos/ai/first_loop.json"));
        for (boolean uses : new boolean[] {true, false}) {
            SimHarness h = CivScene.match(
                    set,
                    uses ? List.of(CivId.DEFAULT, CivId.DEFAULT) : LEAN_VS_DEFAULT,
                    TestRules.catalogue(),
                    CONQUEST_OFF,
                    world);
            worker(h, 0, 24, 24);
            AiBudget budget = new AiBudget(AiBudget.CAP);
            List<Command> sent = new ArrayList<>();
            new ComputerPlayer(0, "first_loop", script)
                    .think(new AiView(h.core().queries(), 0, budget), budget, sent::add);
            assertThat(sent.stream().anyMatch(Gather.class::isInstance))
                    .as("the first-loop ratio is food and wood; wood used: %s", uses)
                    .isEqualTo(uses);
        }
    }

    @Test
    void theClientsPhaseRequirementsFollowTheServersRule() {
        SimHarness h = lean("{\"phase\":{\"id\":\"buildings\",\"params\":{\"cap\":2}}}");
        MatchState s = KernelAccess.state(h.core());
        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(0), 20, 20, 0));
        h.step(); // two finished buildings: phase 2
        BuildingState buildings = ReplicationService.buildingState(h.queries(), 1, new FactionSlot(0));
        FactionState wire = WireCodec.decode(
                FactionState.WIRE,
                WireCodec.encode(
                        FactionState.WIRE, ReplicationService.factionState(h.queries(), 1, new FactionSlot(0))));
        CivDef lean = s.civs().of(0);
        MatchFactionView server = new MatchFactionView(s, 0);

        ClientFactionView client = new ClientFactionView(s.templates(), wire, buildings.buildings(), lean);
        for (String phase : List.of("village", "town", "city", "nonesuch")) {
            assertThat(client.reached(phase))
                    .as("phase %s, the variant rule", phase)
                    .isEqualTo(server.reached(phase));
        }
        assertThat(client.reached("town"))
                .as("the client has no Town tech researched, the rule says reached")
                .isTrue();
        ClientFactionView plain = new ClientFactionView(
                s.templates(), wire, buildings.buildings(), s.civs().of(1));
        assertThat(plain.reached("town"))
                .as("the default rule: researched techs only")
                .isFalse();
    }

    @Test
    void oneCivilisationHasOneCentreClassForDefeatPlacementAndTheAi() {
        JsonObject civ = TestRules.civ("{}");
        civ.addProperty("centreClass", "House");
        SimHarness h = CivScene.match(CivScene.with(civ), LEAN_VS_DEFAULT, TestRules.catalogue(), CONQUEST_OFF);
        MatchState s = KernelAccess.state(h.core());
        int house = s.templates().byId(CivScene.HOUSE).netIndex();
        int centre = s.templates().byId(CivScene.CENTRE).netIndex();

        assertThat(s.civs().centreClass(0)).isEqualTo("House");
        assertThat(s.civs().centreClass(1)).isEqualTo("CivicCentre");
        assertThat(Placing.params(s, house, 0).civicCentre())
                .as("a house is a centre for faction 0")
                .isTrue();
        assertThat(Placing.params(s, centre, 0).civicCentre()).isFalse();
        assertThat(Placing.params(s, house, 1).civicCentre()).isFalse();
        assertThat(Placing.params(s, centre, 1).civicCentre()).isTrue();
        assertThat(ClientPlacement.params(s.templates(), house, List.of(), null, "House")
                        .civicCentre())
                .as("the client reads the same class")
                .isTrue();
        assertThat(DefeatRule.DEFAULT.holds(s, 0))
                .as("and the defeat rule: no House yet")
                .isFalse();

        h.submit(PlayerSlot.ADMIN, new SpawnBuilding(CivScene.HOUSE, new FactionSlot(0), 20, 20, 0));
        h.step();
        assertThat(DefeatRule.DEFAULT.holds(s, 0)).isTrue();
        AiView view = new AiView(h.queries(), 0, new AiBudget(AiBudget.CAP));
        assertThat(view.buildings())
                .filteredOn(view::isCentre)
                .extracting(b -> b.template())
                .containsExactly(CivScene.HOUSE);
        assertThat(Placing.world(s, 0).civicCentres())
                .as("the placement world lists a faction's centres by its own class")
                .hasSize(2);
    }

    @Test
    void aWallPreviewWithAVariantRuleGivesTheServersVerdict() {
        TemplateSet set = CivScene.with(TestRules.civ("{\"placement\":{\"id\":\"anywhere\"}}"));
        SimHarness variant = CivScene.match(set, LEAN_VS_DEFAULT, TestRules.catalogue(), CONQUEST_OFF);
        SimHarness control =
                CivScene.match(set, List.of(CivId.DEFAULT, CivId.DEFAULT), TestRules.catalogue(), CONQUEST_OFF);
        int wall = KernelAccess.state(variant.core()).templates().byId(PALISADE).netIndex();
        for (SimHarness h : List.of(variant, control)) {
            MatchState s = KernelAccess.state(h.core());
            assertThat(h.runUntil(
                            () -> s.play().territorySystem() != null
                                    && s.play().territorySystem().recomputes() > 0,
                            400))
                    .isTrue();
            int builder = worker(h, 0, 30, 30);
            MatchState state = KernelAccess.state(h.core());
            BuildingState buildings = ReplicationService.buildingState(h.queries(), 1, new FactionSlot(0));
            PlacementRules.World client = ClientPlacement.world(
                    state.templates(),
                    buildings.buildings(),
                    64,
                    64,
                    new ClientPlacement.Columns() {
                        @Override
                        public double floor(int x, int z) {
                            return Placing.floor(
                                    state.surfaces(), state.config().bounds(), x, z);
                        }

                        @Override
                        public int territory(int x, int z) {
                            return state.play().territorySystem().grid().ownerAt(x, z);
                        }

                        @Override
                        public boolean node(int x, int z) {
                            return false;
                        }
                    },
                    f -> "CivicCentre");
            var preview = ClientWall.preview(
                    state.civs().rules(0).placement(),
                    state.templates(),
                    wall,
                    28,
                    40,
                    40,
                    40,
                    List.of(),
                    client,
                    0,
                    List.of("food", "wood", "stone", "iron"),
                    null,
                    "CivicCentre");
            h.submit(new PlayerSlot(0), new PlaceWall(PALISADE, 28, 40, 40, 40, List.of(), UnitSet.of(builder)));
            h.step();
            CommandResult last = h.results().get(h.results().size() - 1);
            boolean accepted = last.outcome() == CommandOutcome.ACCEPTED;
            assertThat(preview).isNotEmpty();
            assertThat(preview.stream().allMatch(seg -> seg.verdict().ok()))
                    .as("the client's preview agrees with the server's verdict (%s)", last.outcome())
                    .isEqualTo(accepted);
            assertThat(accepted).as("variant accepts, default refuses").isEqualTo(h == variant);
        }
    }

    private static int resourceIndex(MatchState s, String id) {
        List<String> ids = s.config().rules().economy().resources().stream()
                .map(r -> r.id())
                .toList();
        return ids.indexOf(id);
    }

    @Test
    void anUpkeepRulePaysThroughTheTreasurysGrantedCounterAndConservationHolds() {
        SimHarness h = lean("{\"upkeep\":{\"id\":\"income\",\"params\":{\"index\":1,\"amount\":1000}}}");
        MatchState s = KernelAccess.state(h.core());
        long granted0 = s.treasury().granted(0, 1);
        long granted1 = s.treasury().granted(1, 1);
        long stock0 = s.treasury().stock(0, 1);
        long stock1 = s.treasury().stock(1, 1);

        h.runTicks(50);

        assertThat(s.treasury().granted(0, 1) - granted0)
                .as("1 wood a step, 50 steps")
                .isEqualTo(50_000);
        assertThat(s.treasury().stock(0, 1) - stock0).isEqualTo(50_000);
        assertThat(s.treasury().granted(1, 1))
                .as("the default civilisation's faction gets none")
                .isEqualTo(granted1);
        assertThat(s.treasury().stock(1, 1)).isEqualTo(stock1);
        assertThat(EconomyChecks.broken(s))
                .as("granted + gathered + refunded = stock + spent")
                .isNull();
        assertThat(h.invariantViolations()).isEmpty();
        assertReplays(h);
    }

    @Test
    void aRestoredMatchKeepsItsCataloguesRules() {
        SimHarness h = lean("{\"phase\":{\"id\":\"buildings\"}}");
        h.runTicks(10);

        SimHarness back = SimHarness.restore(
                h.core().snapshot(),
                KernelAccess.state(h.core()).templates(),
                TestRules.catalogue(),
                dev.strategos.core.sim.SliceSource.NONE);

        assertThat(Techs.phase(KernelAccess.state(back.core()), 0)).isEqualTo(1);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
        back.runTicks(10);
        h.runTicks(10);
        assertThat(back.stateHash()).isEqualTo(h.stateHash());
    }

    @Test
    void restoreAndReplayThroughTheDefaultCatalogueRefuseAVariantMatch() {
        SimHarness h = lean("{\"phase\":{\"id\":\"buildings\"}}");
        h.runTicks(10);
        TemplateSet templates = KernelAccess.state(h.core()).templates();

        assertThatThrownBy(() -> SimHarness.restore(h.core().snapshot(), templates))
                .as("the catalogue that made the match matters")
                .isInstanceOf(CivBindException.class)
                .hasMessageContaining("unknown rule 'buildings'");
        assertThatThrownBy(() -> ReplayDriver.replay(h.core().journal(), templates))
                .isInstanceOf(CivBindException.class);
        assertReplays(h); // through its own catalogue it is fine
    }
}
