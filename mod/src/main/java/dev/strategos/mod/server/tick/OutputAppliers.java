package dev.strategos.mod.server.tick;

import dev.strategos.core.protocol.NoticeMsg;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.OutputEvent;
import dev.strategos.core.sim.TickOutputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.UnitDied;
import dev.strategos.core.sim.UnitHurt;
import dev.strategos.core.sim.UnitStateChanged;
import dev.strategos.core.sim.UnitStruck;
import dev.strategos.core.sim.WorldEffect;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.body.KinematicDriver;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.FxSender;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;

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
                case UnitDied died ->
                    rt.bodies().die(died.handle(), level.damageSources().generic());
                case WorldEffect effect -> {
                    BlockWriter writer = StrategosServer.get().writer();
                    BlockPos origin = rt.space().origin();
                    writer.enqueue(
                            level,
                            new WriteSite(rt.id().value(), effect.site()),
                            effect.priority(),
                            new BlockPos(origin.getX() + effect.x(), effect.y(), origin.getZ() + effect.z()),
                            writer.state(effect.state()),
                            effect.owner());
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

    /** The leaf output types {@link #apply} handles; L2 checks they are all of them. */
    public static List<Class<? extends OutputEvent>> handledTypes() {
        return List.of(
                CommandResult.class,
                UnitCreated.class,
                UnitStateChanged.class,
                UnitStruck.class,
                UnitHurt.class,
                UnitDied.class,
                WorldEffect.class,
                Notice.class);
    }
}
