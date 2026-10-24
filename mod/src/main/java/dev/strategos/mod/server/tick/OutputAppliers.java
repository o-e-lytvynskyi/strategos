package dev.strategos.mod.server.tick;

import dev.strategos.core.protocol.MatchNews;
import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.BlockCracked;
import dev.strategos.core.sim.BuildingDestroyed;
import dev.strategos.core.sim.BuildingHurt;
import dev.strategos.core.sim.CarcassGone;
import dev.strategos.core.sim.CarcassLeft;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.FactionDefeated;
import dev.strategos.core.sim.FlameShown;
import dev.strategos.core.sim.MatchEnded;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnderAttack;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitGear;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.WaveAnnounced;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.body.GearItems;
import dev.strategos.mod.server.body.KinematicDriver;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.FxSender;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchEventPayload;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;

/** Applies a step's outputs to the world: fx, replies, body requests, block writes for the writer, then the poses. */
public final class OutputAppliers {
    private OutputAppliers() {}

    public static void apply(MatchRuntime rt, ServerLevel level, TickOutputs out) {
        FxSender.send(rt, out, StrategosServer.get().net()); // while the dead still have their bodies
        for (OutputEvent event : out.events()) {
            switch (event) {
                case CommandResult result -> {
                    Consumer<CommandResult> reply = rt.takeReply(result);
                    if (reply != null) {
                        reply.accept(result);
                    } else {
                        rt.log().debug(out.tick(), "{}", result);
                    }
                }
                case UnitCreated created -> rt.bodies().want(created.handle());
                case UnitStruck struck -> {
                    UnitEntity body = rt.bodies().get(struck.attacker());
                    if (body != null) { // the arm's swing, for those who track the body
                        body.swing(InteractionHand.MAIN_HAND);
                    }
                }
                case UnitHurt hurt -> {
                    UnitEntity body = rt.bodies().get(hurt.handle());
                    if (body != null) { // the red flash, for those who track the body
                        level.broadcastDamageEvent(body, level.damageSources().generic());
                    }
                }
                case UnitDied died -> {
                    int carcass = carcassOf(out, died.handle());
                    if (carcass != 0) {
                        rt.bodies().carcass(died.handle(), carcass); // an animal's body stays (ECO-16)
                    } else {
                        rt.bodies().die(died.handle(), level.damageSources().generic());
                    }
                }
                case CarcassLeft left -> {} // its UnitDied kept the body
                case CarcassGone gone -> rt.bodies().carcassGone(gone.node());
                case BlockCracked cracked -> {} // FxSender sent it before the outputs applied
                case FlameShown flame -> {} // as a crack: FxSender sent it
                case BuildingHurt hurt -> {} // the under-attack alert's (UI-12)
                case FactionDefeated defeated ->
                    StrategosServer.get()
                            .net()
                            .send(
                                    new Audience.Match(rt.id()),
                                    new MatchEventPayload(new MatchNews(
                                            rt.id().value(),
                                            out.tick(),
                                            MatchNews.DEFEATED,
                                            defeated.faction().value(),
                                            out.tick())));
                case UnderAttack attacked ->
                    StrategosServer.get()
                            .net()
                            .send(
                                    new Audience.Faction(rt.id(), attacked.faction()),
                                    new MatchEventPayload(new MatchNews(
                                            rt.id().value(),
                                            out.tick(),
                                            MatchNews.ALERT,
                                            attacked.faction().value(),
                                            0,
                                            (float) attacked.x(),
                                            (float) attacked.z())));
                case WaveAnnounced wave ->
                    StrategosServer.get()
                            .net()
                            .send(
                                    new Audience.Match(rt.id()),
                                    new MatchEventPayload(new MatchNews(
                                            rt.id().value(),
                                            out.tick(),
                                            MatchNews.WAVE,
                                            wave.faction().value(),
                                            wave.leaves(),
                                            wave.x(),
                                            wave.z())));
                case MatchEnded ended -> {
                    StrategosServer.get()
                            .net()
                            .send(
                                    new Audience.Match(rt.id()),
                                    new MatchEventPayload(new MatchNews(
                                            rt.id().value(),
                                            out.tick(),
                                            MatchNews.ENDED,
                                            ended.winner(),
                                            ended.ticks())));
                    StrategosServer.get().matches().end(rt.id());
                }
                case BuildingDestroyed destroyed ->
                    rt.log().info(out.tick(), "building {} fell", destroyed.handle()); // its blocks are effects
                case WorldEffect effect -> {
                    BlockWriter writer = StrategosServer.get().writer();
                    BlockPos origin = rt.space().origin();
                    BlockPos pos = new BlockPos(origin.getX() + effect.x(), effect.y(), origin.getZ() + effect.z());
                    BlockState state = effect.propertiesOnly()
                            ? BlockWriter.withProperties(level.getBlockState(pos), effect.state())
                            : writer.state(effect.state());
                    if (state != null) { // properties the block standing there lacks: someone replaced it
                        writer.enqueue(
                                level,
                                new WriteSite(rt.id().value(), effect.site()),
                                effect.priority(),
                                pos,
                                state,
                                effect.owner());
                    }
                }
                case UnitGear gear -> {
                    var core = rt.core(); // outputs come from a step, so the core is there
                    if (core != null) {
                        rt.bodies()
                                .gear(
                                        gear.handle(),
                                        GearItems.tool(gear.tool()),
                                        GearItems.load(
                                                core.queries().config().rules().economy(),
                                                gear.resource(),
                                                gear.subtype()));
                    }
                }
                case Notice notice ->
                    StrategosServer.get()
                            .net()
                            .send(
                                    new Audience.Faction(rt.id(), notice.faction()),
                                    new FeedbackPayload(
                                            new NoticeMsg(notice.kind(), notice.handle(), notice.detail())));
                case UnitStateChanged changed ->
                    rt.log()
                            .debug(
                                    out.tick(),
                                    "unit {} {} -> {}",
                                    Handle.toString(changed.handle()),
                                    changed.from(),
                                    changed.to());
            }
        }
        KinematicDriver.apply(level, rt.space(), rt.bodies(), out.bodies());
        rt.bodies().tickDying();
    }

    /** The carcass node animal {@code handle} left this step, or 0. */
    private static int carcassOf(TickOutputs out, int handle) {
        for (OutputEvent e : out.events()) {
            if (e instanceof CarcassLeft left && left.handle() == handle) {
                return left.node();
            }
        }
        return 0;
    }

    /** The leaf output types {@link #apply} handles; L2 checks they are all of them. */
    public static List<Class<? extends OutputEvent>> handledTypes() {
        return List.of(
                CommandResult.class,
                UnitCreated.class,
                UnitStateChanged.class,
                UnitStruck.class,
                UnitHurt.class,
                UnitDied.class,
                BuildingDestroyed.class,
                BuildingHurt.class,
                BlockCracked.class,
                FlameShown.class,
                CarcassLeft.class,
                CarcassGone.class,
                FactionDefeated.class,
                MatchEnded.class,
                UnderAttack.class,
                WaveAnnounced.class,
                UnitGear.class,
                WorldEffect.class,
                Notice.class);
    }
}
