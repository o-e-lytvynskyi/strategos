package dev.strategos.mod.mixin;

import dev.strategos.mod.server.commander.CommanderGuard;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Route the decision after vanilla's thread handoff and before any entity lookup or teleport. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Inject(
            method = "handleTeleportToEntityPacket",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void strategos$denySpectatorTeleport(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (CommanderGuard.deniesTeleport(((ServerGamePacketListenerImpl) (Object) this).player)) ci.cancel();
    }
}
