package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.body;
import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.onlyUnit;
import static dev.strategos.gametest.suites.Units.spawn;

import com.mojang.authlib.GameProfile;
import dev.strategos.core.client.audio.Sounds;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.ReplayDriver;
import dev.strategos.core.sim.journal.ReplayResult;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * The damage model on real bodies (CMB-01): vanilla's blows reach the core as its own damage, the body's vanilla health
 * stays full until the core says it died, and a dead unit's body plays vanilla's death and goes.
 */
public final class CombatTests {
    private CombatTests() {}

    /**
     * CMB-01.4: a player's blow, an arrow, an explosion and {@code /kill} reach the core as hack, pierce, crush and
     * lethal; the blows take hit points off the unit while the body's vanilla health stays full (CMB-01.3's half that
     * vanilla sources can show), and the kill kills it.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void vanillaBlowsBecomeCoreDamage(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 1, 0);
        var state = new Object() {
            int unit;
            double before;
            float vanilla;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    state.unit = onlyUnit(rt);
                    body(rt, state.unit);
                })
                .thenExecute(() -> {
                    UnitEntity body = body(rt, state.unit);
                    state.before = core(rt).queries().hitPoints(state.unit);
                    state.vanilla = body.getHealth();
                    FakePlayer player = FakePlayerFactory.get(
                            h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(new byte[] {21}), "strategos-cmb1"));
                    player.moveTo(body.getX() - 1.5, body.getY(), body.getZ(), -90, 0);
                    player.attack(body);
                    Arrow arrow = new Arrow(
                            h.getLevel(), body.getX(), body.getY() + 3, body.getZ(), new ItemStack(Items.ARROW), null);
                    body.hurt(h.getLevel().damageSources().arrow(arrow, player), 4);
                    h.getLevel()
                            .explode(
                                    null,
                                    body.getX() + 1.5,
                                    body.getY() + 0.5,
                                    body.getZ(),
                                    1.5f,
                                    Level.ExplosionInteraction.NONE);
                })
                .thenExecuteAfter(2, () -> {
                    List<ExternalDamage.Kind> kinds = kinds(rt, state.unit);
                    check(
                            kinds.contains(ExternalDamage.Kind.HACK)
                                    && kinds.contains(ExternalDamage.Kind.PIERCE)
                                    && kinds.contains(ExternalDamage.Kind.CRUSH),
                            "the core got " + kinds + " for a blow, an arrow and an explosion");
                    double now = core(rt).queries().hitPoints(state.unit);
                    check(now < state.before - 4, "the unit kept " + now + " of " + state.before + " hit points");
                    check(
                            body(rt, state.unit).getHealth() == state.vanilla,
                            "the body's vanilla health went from " + state.vanilla + " to "
                                    + body(rt, state.unit).getHealth());
                    body(rt, state.unit).kill(); // /kill's own call
                })
                .thenExecuteAfter(2, () -> {
                    check(kinds(rt, state.unit).contains(ExternalDamage.Kind.LETHAL), "/kill was not lethal");
                    check(core(rt).queries().unit(state.unit) == null, "the unit outlived /kill");
                })
                .thenSucceed();
    }

    /**
     * CMB-01.5: a unit killed in the core loses its record at once; its body drops to vanilla health 0, plays the death
     * ticks and is gone after them, and the owner's player gets the loss notice within a tick.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void aDeadUnitsBodyDiesAndGoes(GameTestHelper h) {
        MatchRuntime rt = match(h);
        Vec3 at = h.absoluteVec(new Vec3(8.5, 20, 8.5));
        MockViewer owner = MockViewer.join(h.getLevel(), "strategos-owner", at.x, at.y, at.z, 4);
        StrategosServer.get().matches().join(rt, owner.player().getUUID(), new PlayerSlot(0));
        StrategosServer.get().net().joined(rt, owner.player(), new PlayerSlot(0)); // the faction's audience
        spawn(rt, 16, 16, 1, 0);
        var state = new Object() {
            int unit;
            UnitEntity body;
            long diedAt;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    state.unit = onlyUnit(rt);
                    state.body = body(rt, state.unit);
                })
                .thenExecute(() -> {
                    owner.poll();
                    owner.capture().clear();
                    rt.damage(new ExternalDamage(
                            state.unit, ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
                    state.diedAt = h.getTick();
                })
                .thenWaitUntil(() -> check(core(rt).queries().unit(state.unit) == null, "the unit is alive"))
                .thenExecute(() -> {
                    check(
                            h.getTick() - state.diedAt <= 2,
                            "the unit died " + (h.getTick() - state.diedAt) + " ticks later");
                    check(state.body.getHealth() == 0 && state.body.isDeadOrDying(), "the body is not dying");
                    check(rt.bodies().get(state.unit) == null, "the registry still holds the body");
                })
                .thenExecuteAfter(
                        1,
                        () -> { // the gateway flushes after the GameTest ticker, at the tick's end
                            owner.poll();
                            check(
                                    lossNotices(owner, state.unit) == 1,
                                    lossNotices(owner, state.unit) + " loss notices");
                        })
                .thenWaitUntil(() ->
                        check(state.body.isRemoved(), "the body is still there, death tick " + state.body.deathTime))
                .thenExecute(() -> {
                    check(rt.bodies().dyingCount() == 0, rt.bodies().dyingCount() + " bodies still dying");
                    check(Units.bodiesOf(h.getLevel(), rt).isEmpty(), "a body of the match is still in the level");
                    owner.close();
                })
                .thenSucceed();
    }

    /**
     * AUD-01.2: bodies hit by a player, shot and killed, and asked to sound the way vanilla's splash, fire and fall
     * do, never sound on the server through either of vanilla's sound events, while a pig hit beside them does (the
     * positive control); and every cue the client plays is a vanilla sound.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void noServerSounds(GameTestHelper h) {
        for (Sounds.Cue cue : List.of(
                Sounds.ORDER_ACCEPTED, Sounds.ORDER_REFUSED, Sounds.of(FxKind.UNIT_HIT), Sounds.of(FxKind.UNIT_DIED))) {
            check(
                    BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.parse(cue.sound())),
                    cue.sound() + " is no vanilla sound");
        }
        MatchRuntime rt = match(h);
        spawn(rt, 16, 16, 2, 3);
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, new Vec3(4.5, 2, 4.5));
        var heard = new Object() {
            int bodies;
            int pig;
        };
        Consumer<PlayLevelSoundEvent.AtEntity> atEntity = e -> {
            if (e.getEntity() instanceof UnitEntity) {
                heard.bodies++;
            }
        };
        Consumer<PlayLevelSoundEvent.AtPosition> atPosition = e -> {
            for (UnitEntity body : Units.bodiesOf(h.getLevel(), rt)) {
                if (body.position().distanceTo(e.getPosition()) < 1e-3) {
                    heard.bodies++;
                }
            }
            if (pig.position().distanceTo(e.getPosition()) < 1e-3) {
                heard.pig++;
            }
        };
        var state = new Object() {
            int[] units = new int[0];
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    state.units = Units.handles(rt);
                    check(state.units.length == 2, state.units.length + " units");
                    body(rt, state.units[0]);
                    body(rt, state.units[1]);
                })
                .thenExecute(() -> {
                    NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtEntity.class, atEntity);
                    NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, atPosition);
                    UnitEntity body = body(rt, state.units[0]);
                    FakePlayer player = FakePlayerFactory.get(
                            h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(new byte[] {22}), "strategos-aud1"));
                    player.moveTo(body.getX() - 1.5, body.getY(), body.getZ(), -90, 0);
                    player.attack(body);
                    Arrow arrow = new Arrow(
                            h.getLevel(), body.getX(), body.getY() + 3, body.getZ(), new ItemStack(Items.ARROW), null);
                    body.hurt(h.getLevel().damageSources().arrow(arrow, player), 4);
                    body.playSound(
                            SoundEvents.GENERIC_SPLASH, 1, 1); // the call a splash, a fire's hiss or a fall makes
                    body.makeSound(SoundEvents.PLAYER_HURT);
                    pig.hurt(h.getLevel().damageSources().playerAttack(player), 1);
                    rt.damage(new ExternalDamage(
                            state.units[1], ExternalDamage.Kind.LETHAL, 0, ExternalDamage.SourceKind.NONE));
                })
                .thenWaitUntil(() ->
                        check(Units.bodiesOf(h.getLevel(), rt).size() == 1, "the dead body is still there"))
                .thenExecute(() -> {
                    NeoForge.EVENT_BUS.unregister(atEntity);
                    NeoForge.EVENT_BUS.unregister(atPosition);
                    check(core(rt).queries().unit(state.units[1]) == null, "the killed unit lives");
                    check(heard.pig > 0, "the pig's hurt was not heard: the listeners hear nothing");
                    check(heard.bodies == 0, "the bodies sounded " + heard.bodies + " times on the server");
                })
                .thenSucceed();
    }

    /**
     * CMB-02.5 and CMB-01.3's unit-on-unit half: five spearmen a side attack-move into each other on the arena floor
     * and fight until one side is gone. The match's journal, replayed in L1 from its start (the terrain's pages
     * included), ends on the live match's hash, so the same units survive with the same hit points; and every body
     * kept its full vanilla health until its unit died, however often it was hit.
     */
    @StrategosTest(timeoutTicks = 3_000)
    public static void squadsFightAsTheCorePredicts(GameTestHelper h) {
        MatchRuntime rt = match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(6, 16), 5, 2));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(1), new Vec2d(26, 16), 5, 2));
        var state = new Object() {
            boolean ordered;
            int hurtBodies;
        };
        h.onEachTick(() -> {
            MatchCore core = core(rt);
            int[] west = core.queries().unitHandlesOf(new FactionSlot(0));
            int[] east = core.queries().unitHandlesOf(new FactionSlot(1));
            if (!state.ordered) {
                if (west.length == 5 && east.length == 5) {
                    rt.submit(new PlayerSlot(0), AttackMove.replace(UnitSet.of(west), new Vec2d(26, 16)));
                    rt.submit(new PlayerSlot(1), AttackMove.replace(UnitSet.of(east), new Vec2d(6, 16)));
                    state.ordered = true;
                }
                return;
            }
            for (UnitEntity body : Units.bodiesOf(h.getLevel(), rt)) {
                if (!body.isDeadOrDying()) {
                    UnitSpawnData data = body.spawnData();
                    check(
                            body.getHealth() == body.getMaxHealth(),
                            "a living body's vanilla health fell to " + body.getHealth());
                    if (data != null
                            && core.queries().hitPoints(data.handle())
                                    < core.queries().maxHitPoints(data.handle())) {
                        state.hurtBodies++;
                    }
                }
            }
            if (west.length > 0 && east.length > 0) {
                return;
            }
            check(state.hurtBodies > 0, "no living body was ever hurt");
            ReplayResult replay = ReplayDriver.replay(core.journal(), TemplateSet.builtin());
            check(replay.matches(), "the journal does not replay: " + replay);
            check(replay.finalHash() == core.stateHash(), "the replay ends on another hash: " + replay);
            check(west.length + east.length > 0, "both sides died to the last unit");
            h.succeed();
        });
    }

    /** CMB-04: a ranged volley damages core units, preserves living bodies' health, and replays exactly. */
    @StrategosTest(timeoutTicks = 600)
    public static void archerVolleyReplaysAsTheCorePredicts(GameTestHelper h) {
        MatchRuntime rt = match(h);
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(new TemplateId("strategos:unit/archer"), new FactionSlot(0), new Vec2d(8, 16), 5, 2));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 16), 5, 2));
        var state = new Object() {
            boolean ordered;
            int elapsed;
            int hurtBodies;
        };
        h.onEachTick(() -> {
            MatchCore core = core(rt);
            if (!state.ordered) {
                int[] archers = core.queries().unitHandlesOf(new FactionSlot(0));
                int[] targets = core.queries().unitHandlesOf(new FactionSlot(1));
                if (archers.length != 5
                        || targets.length != 5
                        || Units.bodiesOf(h.getLevel(), rt).size() != 10) {
                    return;
                }
                rt.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(archers), targets[0]));
                state.ordered = true;
                return;
            }
            for (UnitEntity body : Units.bodiesOf(h.getLevel(), rt)) {
                if (!body.isDeadOrDying()) {
                    UnitSpawnData data = body.spawnData();
                    check(body.getHealth() == body.getMaxHealth(), "a living body's vanilla health fell");
                    if (data != null
                            && core.queries().unit(data.handle()) != null
                            && core.queries().hitPoints(data.handle())
                                    < core.queries().maxHitPoints(data.handle())) {
                        state.hurtBodies++;
                    }
                }
            }
            if (++state.elapsed < 120) {
                return;
            }
            check(state.hurtBodies > 0, "the archer volley did no core damage");
            ReplayResult replay = ReplayDriver.replay(core.journal(), TemplateSet.builtin());
            check(replay.matches(), "the journal does not replay: " + replay);
            check(replay.finalHash() == core.stateHash(), "the replay ends on another hash: " + replay);
            h.succeed();
        });
    }

    /**
     * CMB-16.4: six archers stand on a six-block stone platform and six on the floor, each with a walker 25.4 blocks
     * away on the floor: past the archer's range of 20, inside the 26 of six blocks up. The raised archers hurt a walker
     * in the core without moving, while their twins on the floor, the same distance from their walkers, have to walk
     * to get in range. Every block stays inside the eight-block-high arena, so GameTest clears it for the next test.
     */
    @StrategosTest(arena = "arena_flat_48_high", timeoutTicks = 300, skyAccess = true)
    public static void highGroundReachesFarther(GameTestHelper h) {
        for (int x = 0; x < 12; x++) {
            for (int z = 2; z < 18; z++) {
                for (int y = 2; y <= 7; y++) {
                    h.setBlock(new BlockPos(x, y, z), Blocks.STONE); // six blocks over the floor at helper y 1
                }
            }
        }
        // A raised body reaches past the arena's top: the match's band is taller, with nothing placed above the arena
        net.minecraft.world.phys.AABB box = h.getBounds();
        MatchSpace space = new MatchSpace(
                h.getLevel().dimension(),
                new BlockPos((int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ)),
                48,
                48,
                (int) Math.floor(box.minY),
                (int) Math.floor(box.minY) + 12);
        MatchRuntime rt = MatchFixture.create(h, space, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        double[] highZ = {3.5, 5.5, 7.5, 9.5, 11.5, 13.5};
        double[] lowZ = {28.5, 30.5, 32.5, 34.5, 36.5, 38.5};
        TemplateId archer = new TemplateId("strategos:unit/archer");
        for (double[] row : new double[][] {highZ, lowZ}) {
            for (double z : row) {
                rt.submit(PlayerSlot.ADMIN, new SpawnUnits(archer, new FactionSlot(0), new Vec2d(6.5, z), 1, 0));
                rt.submit(
                        PlayerSlot.ADMIN,
                        new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(32.1, z), 1, 0));
            }
        }
        var state = new Object() {
            boolean ordered;
        };
        h.onEachTick(() -> {
            MatchCore core = core(rt);
            int[] archers = core.queries().unitHandlesOf(new FactionSlot(0));
            int[] walkers = core.queries().unitHandlesOf(new FactionSlot(1));
            if (archers.length != 12 || walkers.length != 12) {
                return;
            }
            if (!state.ordered) {
                for (int a : archers) {
                    for (int w : walkers) {
                        if (Math.abs(Units.unit(rt, a).z() - Units.unit(rt, w).z()) < 0.5) {
                            rt.submit(new PlayerSlot(0), Attack.replace(UnitSet.of(a), w));
                        }
                    }
                }
                for (int a : archers) {
                    for (int b : archers) {
                        double over = Units.unit(rt, a).y() - Units.unit(rt, b).y();
                        if (Units.unit(rt, a).z() < 22 && Units.unit(rt, b).z() > 22) {
                            check(
                                    over > 5.5 && over < 6.5,
                                    "a raised archer stands " + over + " blocks over the floor");
                        }
                    }
                }
                state.ordered = true;
                return;
            }
            for (int a : archers) {
                UnitView u = Units.unit(rt, a);
                if (u.z() < 22) {
                    check(u.x() == 6.5, "a raised archer walked to " + u.x());
                }
            }
            int hurtHigh = 0;
            for (int w : walkers) {
                if (Units.unit(rt, w).z() < 22
                        && core.queries().hitPoints(w) < core.queries().maxHitPoints(w)) {
                    hurtHigh++;
                }
            }
            if (hurtHigh == 0) {
                return;
            }
            // Only the raised archers can have hurt these walkers; they did it from where they stood, while their
            // ground twins, 25.4 blocks from theirs, had to walk to get in range
            int walked = 0;
            for (int a : archers) {
                UnitView u = Units.unit(rt, a);
                if (u.z() > 22 && u.x() > 6.5) {
                    walked++;
                }
            }
            check(walked == 6, "only " + walked + " ground archers walked");
            h.succeed();
        });
    }

    /**
     * ORD-04.3: five spearmen attack-move through three debug walkers standing in their way, kill them and walk on to
     * the point; replayed in L1, the journal ends on the live hash.
     */
    @StrategosTest(timeoutTicks = 3_000)
    public static void attackMoveThroughASquadAsTheCorePredicts(GameTestHelper h) {
        MatchRuntime rt = match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(4, 16), 5, 2));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(16, 16), 3, 1.5));
        var state = new Object() {
            boolean ordered;
            int[] squad = new int[0];
        };
        h.onEachTick(() -> {
            MatchCore core = core(rt);
            if (!state.ordered) {
                int[] west = core.queries().unitHandlesOf(new FactionSlot(0));
                if (west.length == 5 && core.queries().unitHandlesOf(new FactionSlot(1)).length == 3) {
                    state.squad = west;
                    rt.submit(new PlayerSlot(0), AttackMove.replace(UnitSet.of(west), new Vec2d(28, 16)));
                    state.ordered = true;
                }
                return;
            }
            if (core.queries().unitHandlesOf(new FactionSlot(1)).length > 0) {
                return;
            }
            for (int unit : state.squad) {
                if (!core.queries().orders(unit).isEmpty()) {
                    return; // still walking on
                }
            }
            for (int unit : state.squad) {
                check(
                        core.queries().unit(unit).x() > 24,
                        "a spearman stopped short at "
                                + core.queries().unit(unit).x());
            }
            ReplayResult replay = ReplayDriver.replay(core.journal(), TemplateSet.builtin());
            check(replay.matches(), "the journal does not replay: " + replay);
            check(replay.finalHash() == core.stateHash(), "the replay ends on another hash: " + replay);
            h.succeed();
        });
    }

    /** The kinds of the vanilla damage the unit took, as the journal holds it. */
    private static List<ExternalDamage.Kind> kinds(MatchRuntime rt, int unit) {
        List<ExternalDamage.Kind> kinds = new ArrayList<>();
        for (TickInputs tick : core(rt).journal().nonEmptyTicks()) {
            for (InputEvent e : tick.events()) {
                if (e instanceof ExternalDamage d && d.handle() == unit) {
                    kinds.add(d.kind());
                }
            }
        }
        return kinds;
    }

    private static int lossNotices(MockViewer viewer, int unit) {
        int n = 0;
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof FeedbackPayload f
                    && f.message() instanceof NoticeMsg notice
                    && notice.kind() == NoticeKind.UNIT_LOST
                    && notice.handle() == unit) {
                n++;
            }
        }
        return n;
    }

    static final dev.strategos.core.shared.TemplateId HOUSE =
            new dev.strategos.core.shared.TemplateId("strategos:building/house");

    /** The level's fire tick before the burn test turned it on, put back after its batch; null untouched. */
    private static final java.util.concurrent.atomic.AtomicReference<Boolean> FIRE_TICK =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** After the burn batch: the fire tick the test found. */
    public static void restoreFireTick(net.minecraft.server.level.ServerLevel level) {
        Boolean before = FIRE_TICK.getAndSet(null);
        if (before != null) {
            level.getGameRules()
                    .getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)
                    .set(before, level.getServer());
        }
    }

    /** Faction 1's house at (6, 10) and ten of faction 0's spearmen sent to burn it once it stands. */
    private static void raid(MatchRuntime rt) {
        rt.submit(PlayerSlot.ADMIN, new dev.strategos.core.protocol.SpawnBuilding(HOUSE, new FactionSlot(1), 6, 10, 0));
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.INFANTRY, new FactionSlot(0), new Vec2d(8, 22), 10, 1.5));
    }

    private static void torch(MatchRuntime rt) {
        int house = core(rt).queries().buildings().get(0).handle();
        rt.submit(
                new PlayerSlot(0),
                dev.strategos.core.protocol.Attack.replace(
                        UnitSet.of(core(rt).queries().unitHandles()), house));
    }

    /**
     * CMB-03.4: with fire tick on, ten spearmen burn a house down and no fire block appears anywhere in the match
     * space, while a control fire on planks outside it spreads.
     */
    @StrategosTest(batch = "combat_burn", timeoutTicks = 1_600, solo = true)
    public static void aBurnMakesNoFireBlock(GameTestHelper h) {
        var rule = h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
        FIRE_TICK.compareAndSet(null, rule.get());
        rule.set(true, h.getLevel().getServer());
        for (int x = 27; x <= 31; x++) {
            for (int z = 4; z <= 10; z++) {
                h.setBlock(new net.minecraft.core.BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.OAK_PLANKS);
            }
        }
        h.setBlock(new net.minecraft.core.BlockPos(29, 2, 7), net.minecraft.world.level.block.Blocks.FIRE);
        MatchRuntime rt = dev.strategos.gametest.MatchFixture.create(
                h,
                0,
                0,
                24,
                32,
                dev.strategos.gametest.MatchFixture.options(dev.strategos.core.testkit.SimHarness.DEFAULT_SEED, h));
        raid(rt);
        h.onEachTick(() -> {
            if (h.getTick() % 5 != 0) {
                return;
            }
            for (int x = 0; x < 24; x++) {
                for (int z = 0; z < 32; z++) {
                    for (int y = 1; y <= 9; y++) {
                        var pos = new net.minecraft.core.BlockPos(x, y, z);
                        if (h.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) {
                            h.fail("a fire block in the match at " + pos);
                        }
                    }
                }
            }
        });
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().unitHandles().length == 10
                                && !core(rt).queries().buildings().isEmpty(),
                        "not ready"))
                .thenExecute(() -> torch(rt))
                .thenWaitUntil(() -> check(core(rt).queries().buildings().isEmpty(), "the house still stands"))
                .thenWaitUntil(() -> {
                    int fires = 0;
                    int planks = 0;
                    for (int x = 27; x <= 31; x++) {
                        for (int z = 4; z <= 10; z++) {
                            if (h.getBlockState(new net.minecraft.core.BlockPos(x, 2, z))
                                    .is(net.minecraft.tags.BlockTags.FIRE)) {
                                fires++;
                            }
                            if (h.getBlockState(new net.minecraft.core.BlockPos(x, 1, z))
                                    .is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)) {
                                planks++;
                            }
                        }
                    }
                    check(
                            fires > 1 || planks < 35,
                            "the control fire has not spread yet: " + fires + " fires, " + planks + " planks");
                })
                .thenSucceed();
    }

    /** CMB-03.5: burnt down, a house leaves the match and its blocks above the floor are gone, the ground free. */
    @StrategosTest(timeoutTicks = 1_200)
    public static void aBurntBuildingIsRemovedAndCleared(GameTestHelper h) {
        MatchRuntime rt = match(h);
        raid(rt);
        var state = new Object() {
            dev.strategos.core.sim.build.BuildingView house;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        core(rt).queries().unitHandles().length == 10
                                && !core(rt).queries().buildings().isEmpty(),
                        "not ready"))
                .thenExecute(() -> {
                    state.house = core(rt).queries().buildings().get(0);
                    torch(rt);
                })
                .thenWaitUntil(() -> check(core(rt).queries().buildings().isEmpty(), "the house still stands"))
                .thenWaitUntil(() -> {
                    var house = state.house;
                    MatchCore core = core(rt);
                    int index = java.util.Objects.requireNonNull(
                                    core.queries().templates().byId(HOUSE))
                            .netIndex();
                    var b = java.util.Objects.requireNonNull(
                            core.queries().templates().blueprint(index));
                    var at = house.footprint();
                    var origin = rt.space().origin();
                    for (int k = 0; k < b.count(); k++) {
                        if (b.y(k) == 0) {
                            continue; // the floor layer stays, as every fall leaves it
                        }
                        var pos = new net.minecraft.core.BlockPos(
                                origin.getX() + at.worldX(b.x(k), b.z(k)),
                                house.baseY() + b.y(k),
                                origin.getZ() + at.worldZ(b.x(k), b.z(k)));
                        check(
                                h.getLevel().getBlockState(pos).isAir(),
                                pos + " still stands: " + h.getLevel().getBlockState(pos));
                    }
                })
                .thenSucceed();
    }
}
