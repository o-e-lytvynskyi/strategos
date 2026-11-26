package dev.strategos.core.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxCpuCountersTest {
    @TempDir
    Path directory;

    private LinuxCpuCounters reader(String system, String process) throws IOException {
        Path a = directory.resolve("system");
        Path b = directory.resolve("process");
        Files.writeString(a, system);
        Files.writeString(b, process);
        return new LinuxCpuCounters(a, b);
    }

    private static String process(String command) {
        return "42 (" + command + ") S 1 2 3 4 5 6 7 8 9 10 11 12 99\n";
    }

    @Test
    void readsSharedUnitsAndExcludesGuestDoubleCounting() throws IOException {
        try (var r = reader("cpu 10 2 3 40 5 6 7 8 999 999\ncpu0 0\n", process("name ) with spaces"))) {
            assertThat(r.read()).isEqualTo(new CpuTimes(81, 45, 23));
            assertThat(r.read()).isEqualTo(new CpuTimes(81, 45, 23));
        }
    }

    @Test
    void missingOrMalformedCountersAreErrors() throws IOException {
        try (var r = reader("cpu0 1 2 3\n", process("name"))) {
            assertThatThrownBy(r::read).isInstanceOf(IOException.class);
        }
        try (var r = reader("cpu 1 2 3\n", process("name"))) {
            assertThatThrownBy(r::read).isInstanceOf(IOException.class);
        }
        try (var r = reader("cpu 1 2 3 4 5 6 7 8\n", "42 (bad) S 1\n")) {
            assertThatThrownBy(r::read).isInstanceOf(IOException.class);
        }
    }

    @Test
    void overflowingCountersDoNotProduceValidLoad() throws IOException {
        try (var r = reader("cpu 9223372036854775807 2 3 4 5 6 7 8\n", process("name"))) {
            assertThatThrownBy(r::read).isInstanceOf(ArithmeticException.class);
        }
    }

    @Test
    void repeatedSamplingDoesNotPolluteKernelAllocationMeasurements() throws IOException {
        try (var r = reader("cpu 10 2 3 40 5 6 7 8\n", process("name"))) {
            for (int i = 0; i < 2_000; i++) r.read();
            var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            assertThat(bean.isThreadAllocatedMemorySupported()).isTrue();
            if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().threadId();
            long before = bean.getThreadAllocatedBytes(thread);
            long total = 0;
            for (int i = 0; i < 1_000; i++) total += r.read().total();
            long bytes = bean.getThreadAllocatedBytes(thread) - before;
            assertThat(total).isEqualTo(81_000);
            assertThat(bytes).isBetween(0L, 128_000L);
        }
    }
}
