package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.strategos.core.testkit.Seeds;
import dev.strategos.gametest.net.NetFuzzCorpus;
import dev.strategos.gametest.net.ServerboundCases;
import dev.strategos.gametest.net.ServerboundCases.Input;
import dev.strategos.mod.registry.ModPayloads;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every real C2S decoder, permanent regressions and at least 100,000 bounded seeded probes per build. */
class ServerPayloadFuzzTest {
    private record Probe(byte[] bytes, @Nullable Boolean refused) {}

    static Stream<Input<?>> inputs() {
        return ServerboundCases.inputs();
    }

    static Stream<NetFuzzCorpus.Entry> permanent() {
        return NetFuzzCorpus.read("fixtures/net-fuzz/corpus.json").stream();
    }

    @Test
    void corpusHasUniqueIdsAndCoversExactlyTheRegisteredServerboundTypes() {
        var entries = permanent().toList();
        assertThat(entries).extracting(NetFuzzCorpus.Entry::id).doesNotHaveDuplicates();
        assertThat(entries.stream().map(NetFuzzCorpus.Entry::payload).collect(Collectors.toSet()))
                .isEqualTo(ModPayloads.specs().stream()
                        .filter(spec -> !spec.toClient())
                        .map(spec -> spec.id().toString())
                        .collect(Collectors.toSet()));
    }

    @Test
    void boundedEngineCorpusIsAnExactSubsetOfThePermanentCorpusAndCoversAllTypes() {
        var canonical = permanent().collect(Collectors.toMap(NetFuzzCorpus.Entry::id, c -> c));
        var subset = NetFuzzCorpus.read("fixtures/net-fuzz/ingress.json");
        assertThat(subset).isNotEmpty().hasSizeLessThanOrEqualTo(100);
        assertThat(subset).extracting(NetFuzzCorpus.Entry::id).doesNotHaveDuplicates();
        for (var entry : subset) assertThat(entry).isEqualTo(canonical.get(entry.id()));
        assertThat(subset.stream().map(NetFuzzCorpus.Entry::payload).collect(Collectors.toSet()))
                .isEqualTo(inputs().map(c -> c.spec().id().toString()).collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @MethodSource("permanent")
    void permanentInputHasItsRecordedOutcome(NetFuzzCorpus.Entry entry) {
        var input = inputs().filter(c -> c.spec().id().toString().equals(entry.payload()))
                .findFirst()
                .orElseThrow();
        checkProbe(input, new Probe(entry.bytes(), entry.refused()), 0, -1, entry.id());
    }

    @ParameterizedTest
    @MethodSource("inputs")
    <P extends CustomPacketPayload> void seededInputsCannotEscapeTheBoundedIngressBoundary(Input<P> input) {
        long[] seeds = Seeds.set("m7-net-fuzz").toArray();
        int types = (int) inputs().count();
        int perSeed = Math.ceilDiv(Math.ceilDiv(100_000, types), seeds.length);
        byte[] valid = encode(input, input.valid());
        byte[] overCap = new byte[input.spec().cap() + 1];
        int executed = 0;
        int acceptedControls = 0;
        for (long seed : seeds) {
            // Fixed boundaries and positives are additional controls, excluded from the 100,000 generated probes.
            checkProbe(input, new Probe(valid, false), seed, -1, "valid-control");
            checkProbe(input, new Probe(overCap, true), seed, -1, "over-cap-control");
            checkProbe(input, new Probe(Arrays.copyOf(valid, valid.length - 1), true), seed, -1, "truncated-control");
            checkProbe(input, new Probe(Arrays.copyOf(valid, valid.length + 1), true), seed, -1, "trailing-control");
            acceptedControls++;
            var random =
                    new SplittableRandom(seed ^ input.spec().id().toString().hashCode());
            for (int i = 0; i < perSeed; i++) {
                int kind = i % 3;
                Probe probe = switch (kind) {
                    case 0 ->
                        new Probe(randomBytes(random, Math.min(256, input.spec().cap())), null);
                    case 1 -> new Probe(mutate(random, valid), null);
                    default ->
                        new Probe(
                                Arrays.copyOf(
                                        valid,
                                        random.nextInt(Math.min(input.spec().cap(), valid.length * 2) + 1)),
                                null);
                };
                checkProbe(input, probe, seed, i, "generated-" + kind);
                executed++;
            }
        }
        assertThat((long) executed * types)
                .as("every decoder receives its share of at least 100,000 actual generated probes")
                .isGreaterThanOrEqualTo(100_000);
        assertThat(acceptedControls).isPositive();
    }

    private static byte[] randomBytes(SplittableRandom random, int cap) {
        byte[] bytes = new byte[random.nextInt(cap + 1)];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) random.nextInt(256);
        return bytes;
    }

    private static byte[] mutate(SplittableRandom random, byte[] valid) {
        byte[] bytes = valid.clone();
        bytes[random.nextInt(bytes.length)] ^= (byte) (1 << random.nextInt(8));
        return bytes;
    }

    private static <P extends CustomPacketPayload> void checkProbe(
            Input<P> input, Probe probe, long seed, int index, String kind) {
        try {
            var buffer = buffer(probe.bytes());
            try {
                P decoded = input.codec().decode(buffer);
                assertThat(decoded).isNotNull();
                assertThat(buffer.readableBytes())
                        .as("the safe decoder consumes the complete input")
                        .isZero();
                boolean refused = input.refused().test(decoded);
                Boolean expected = probe.refused();
                if (expected != null) assertThat(refused).isEqualTo(expected);
                if (!refused) {
                    byte[] canonical = encode(input, decoded);
                    var roundTrip = buffer(canonical);
                    try {
                        P again = input.codec().decode(roundTrip);
                        assertThat(input.refused().test(again)).isFalse();
                        assertThat(roundTrip.readableBytes()).isZero();
                        assertThat(encode(input, again)).containsExactly(canonical);
                    } finally {
                        roundTrip.release();
                    }
                }
            } finally {
                buffer.release();
            }
        } catch (RuntimeException | AssertionError failure) {
            archive(input, probe, seed, index, kind, failure);
            throw failure;
        }
    }

    private static <P extends CustomPacketPayload> byte[] encode(Input<P> input, P value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            input.codec().encode(buffer, value);
            assertThat(buffer.readableBytes()).isLessThanOrEqualTo(input.spec().cap());
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer(byte[] bytes) {
        return new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static void archive(Input<?> input, Probe probe, long seed, int index, String kind, Throwable failure) {
        String id = input.spec().id().toString().replaceAll("[^a-zA-Z0-9_.-]", "_") + "-" + seed + "-" + index + "-"
                + kind.replaceAll("[^a-zA-Z0-9_.-]", "_");
        Path dir = Path.of("build", "reports", "net-fuzz");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(id + ".bin"), probe.bytes());
            var json = new JsonObject();
            json.addProperty("payload", input.spec().id().toString());
            json.addProperty("seed", seed);
            json.addProperty("index", index);
            json.addProperty("kind", kind);
            json.addProperty("bytes", probe.bytes().length);
            Files.writeString(dir.resolve(id + ".json"), json.toString(), StandardCharsets.UTF_8);
            System.err.println("Exact fuzz input saved: " + id + " kind=" + kind + " bytes=" + probe.bytes().length);
        } catch (IOException archiveFailure) {
            failure.addSuppressed(archiveFailure);
        }
    }
}
