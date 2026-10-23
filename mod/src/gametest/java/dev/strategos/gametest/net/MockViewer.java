package dev.strategos.gametest.net;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * A headless player that sees what a client would (QA-19). It joins as {@code
 * GameTestHelper#makeMockServerPlayerInLevel} joins its mock player (GameTestHelper.java:291–310), a connection on an
 * {@link EmbeddedChannel} placed in the player list, configured as a NeoForge client with all our channels, but keeps
 * the channel: {@link #poll()} takes every packet written
 * to it since the last poll, unbundled, into its {@link PacketCapture} and {@link ByteCounter}, and answers each chunk
 * batch as a client does (ServerGamePacketListenerImpl.java:1904), so the server goes on sending the chunks of its
 * view; a player that never answers gets one batch. Tests poll every tick and close the viewer; a batch's end closes
 * any left open.
 */
public final class MockViewer implements AutoCloseable {
    /** What a client asks for when it answers a batch: the server's highest rate. */
    private static final float CHUNKS_PER_TICK = 64;

    private static final List<MockViewer> OPEN = new ArrayList<>();

    private final MinecraftServer server;
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private final boolean acknowledging;
    private final PacketCapture capture = new PacketCapture();
    private final ByteCounter bytes;
    private final Set<ChunkPos> chunks = new HashSet<>();
    private int batches;
    private boolean operator;

    private MockViewer(MinecraftServer server, ServerPlayer player, EmbeddedChannel channel, boolean acknowledging) {
        this.server = server;
        this.player = player;
        this.channel = channel;
        this.acknowledging = acknowledging;
        this.bytes = new ByteCounter(server.registryAccess());
    }

    /**
     * An acknowledging viewer named {@code name} (at most 16 characters, as the protocol allows) at (x, y, z) of {@code
     * level}, asking for {@code viewDistance}.
     */
    public static MockViewer join(ServerLevel level, String name, double x, double y, double z, int viewDistance) {
        return join(level, name, x, y, z, viewDistance, true);
    }

    /** As above; a viewer that does not acknowledge gets one chunk batch, as a client would that never answers. */
    public static MockViewer join(
            ServerLevel level, String name, double x, double y, double z, int viewDistance, boolean acknowledging) {
        if (name.length() > 16) {
            throw new IllegalArgumentException("a player name has at most 16 characters, not '" + name + "'");
        }
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(uuidOf(name), name);
        ClientInformation information = new ClientInformation(
                "en_us", viewDistance, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false);
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, information, false, ConnectionType.NEOFORGE);
        ServerPlayer player = new ServerPlayer(server, level, profile, information);
        player.moveTo(x, y, z, 0, 0);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection); // a NeoForge client with every channel of ours
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        MockViewer viewer = new MockViewer(server, player, channel, acknowledging);
        OPEN.add(viewer);
        return viewer;
    }

    /** The UUID a viewer named {@code name} joins with, the same in every run: one can be seated before it joins. */
    public static UUID uuidOf(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Takes the packets sent since the last poll; on the server thread, once a tick. The server holds a tick's packets
     * unflushed until it sends chunks, after the GameTest ticker ({@code MinecraftServer#tickChildren} suspends
     * flushing), so the poll flushes the channel first: it sees this tick's packets, not the last tick's.
     */
    public void poll() {
        if (channel.isOpen()) { // a closed channel already holds all it was sent, the server's last packet included
            channel.flushOutbound();
        }
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof Packet<?> packet) { // the rest are pipeline tasks a real channel would run
                receive(packet);
            }
        }
    }

    private void receive(Packet<?> packet) {
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> part : bundle.subPackets()) {
                receive(part);
            }
            return;
        }
        capture.add(packet);
        bytes.count(packet);
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            chunks.add(new ChunkPos(chunk.getX(), chunk.getZ()));
        } else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
            batches++;
            if (acknowledging) {
                player.connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(CHUNKS_PER_TICK));
            }
        }
    }

    /**
     * As the client's position packet would (ServerGamePacketListenerImpl.java:970), at least once a second: the chunk
     * map pairs the viewer with each entity whose chunk it has received since. A viewer that joins beside standing
     * entities gets them only this way: their chunks were still pending when it joined (ChunkMap.java:245, 1024), and
     * sending a chunk pairs nothing (PlayerChunkSender.java:76).
     */
    public void moved() {
        player.serverLevel().getChunkSource().move(player);
    }

    /** As the client would send {@code payload}: the server's connection reads it, on the server thread. */
    public void send(CustomPacketPayload payload) {
        channel.writeInbound(new ServerboundCustomPayloadPacket(payload));
    }

    /**
     * Makes this viewer an operator of permission {@code level} until it closes. GameTestServer grants operators added
     * the usual way level 0 ({@code getOperatorUserPermissionLevel}), so the entry names its level itself; it is saved in
     * the server's {@code ops.json} and removed again on close.
     */
    public MockViewer operator(int level) {
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), level, false));
        operator = true;
        return this;
    }

    /** Whether the server closed this viewer's connection. */
    public boolean disconnected() {
        return !channel.isOpen();
    }

    public ServerPlayer player() {
        return player;
    }

    public PacketCapture capture() {
        return capture;
    }

    public ByteCounter bytes() {
        return bytes;
    }

    /** The chunks it received, by position. */
    public Set<ChunkPos> chunks() {
        return Set.copyOf(chunks);
    }

    /** The chunk batches it was sent. */
    public int batches() {
        return batches;
    }

    /** Leaves: out of the player list and its level, the channel closed. */
    @Override
    public void close() {
        if (OPEN.remove(this)) {
            if (operator) {
                server.getPlayerList().getOps().remove(player.getGameProfile());
            }
            server.getPlayerList().remove(player);
            channel.finishAndReleaseAll(); // closes it, dropping what it still held
        }
    }

    /** Closes every viewer still open: a batch's cleanup. */
    public static void closeAll() {
        for (MockViewer viewer : List.copyOf(OPEN)) {
            viewer.close();
        }
    }
}
