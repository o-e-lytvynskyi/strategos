package dev.strategos.gametest.suites;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ptr.IntByReference;
import com.sun.management.OperatingSystemMXBean;
import dev.strategos.core.testkit.CpuTimes;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cumulative all-processor CPU counters per platform, each in one unit for the machine total, its idle time and this
 * process: Windows 100 ns, macOS and Linux clock ticks (100 a second). Anything that cannot be read throws
 * {@link Unavailable}; a missing counter is never read as zero load.
 */
final class CpuCounters {
    /** The counters cannot be read here; the message says why. */
    static final class Unavailable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unavailable(String message) {
            super(message);
        }

        Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** libSystem's Mach host calls (mach/host_info.h): flavor 3 is HOST_CPU_LOAD_INFO, four ticks counters. */
    private interface Mach extends Library {
        int host_statistics(int host, int flavor, int[] info, IntByReference count);

        int mach_host_self();
    }

    private static final int HOST_CPU_LOAD_INFO = 3;
    private static final int CPU_STATE_USER = 0;
    private static final int CPU_STATE_SYSTEM = 1;
    private static final int CPU_STATE_IDLE = 2;
    private static final int CPU_STATE_NICE = 3;
    private static final long NANOS_PER_TICK = 10_000_000L; // 100 ticks a second, as the Mach counters

    private static volatile Mach mach;
    /** natural_t ticks wrap at 2^32 (about 41 days of 12 busy-or-idle CPUs): widened here by unsigned deltas. */
    private static final int[] LAST_TICKS = new int[4];

    private static final long[] WIDE_TICKS = new long[4];
    private static boolean ticksSeen;

    private CpuCounters() {}

    /** What the report names as the foreign load's source. */
    static String source() {
        if (Platform.isWindows()) {
            return "Win32 cumulative counters (GetSystemTimes, GetProcessTimes)";
        } else if (Platform.isMac()) {
            return "Mach host_statistics ticks, process CPU time from the MXBean";
        } else if (Platform.isLinux()) {
            return "/proc/stat and /proc/self/stat ticks";
        }
        return "unsupported platform";
    }

    static CpuTimes read() {
        try {
            if (Platform.isWindows()) {
                return WindowsCpuTimes.read();
            } else if (Platform.isMac()) {
                return readMac();
            } else if (Platform.isLinux()) {
                return readLinux();
            }
        } catch (Unavailable e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            throw new Unavailable("CPU counters unreadable: " + e, e);
        }
        throw new Unavailable("no CPU counters for this platform: " + System.getProperty("os.name"));
    }

    private static synchronized CpuTimes readMac() {
        Mach api = mach;
        if (api == null) {
            api = Native.load("System", Mach.class);
            mach = api;
        }
        int[] ticks = new int[4];
        IntByReference count = new IntByReference(ticks.length);
        int kern = api.host_statistics(api.mach_host_self(), HOST_CPU_LOAD_INFO, ticks, count);
        if (kern != 0) {
            throw new Unavailable("host_statistics failed, kern_return " + kern);
        }
        for (int i = 0; i < ticks.length; i++) {
            WIDE_TICKS[i] = ticksSeen
                    ? WIDE_TICKS[i] + Integer.toUnsignedLong(ticks[i] - LAST_TICKS[i])
                    : Integer.toUnsignedLong(ticks[i]);
            LAST_TICKS[i] = ticks[i];
        }
        ticksSeen = true;
        long idle = WIDE_TICKS[CPU_STATE_IDLE];
        long total = WIDE_TICKS[CPU_STATE_USER] + WIDE_TICKS[CPU_STATE_SYSTEM] + idle + WIDE_TICKS[CPU_STATE_NICE];
        long processNanos =
                ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class).getProcessCpuTime();
        if (processNanos < 0) {
            throw new Unavailable("process CPU time unavailable");
        }
        return new CpuTimes(total, idle, processNanos / NANOS_PER_TICK);
    }

    private static CpuTimes readLinux() {
        try {
            String stat = Files.readString(Path.of("/proc/stat"), StandardCharsets.US_ASCII);
            String[] cpu = stat.lines()
                    .filter(l -> l.startsWith("cpu "))
                    .findFirst()
                    .orElseThrow(() -> new Unavailable("no aggregate cpu line in /proc/stat"))
                    .trim()
                    .split("\\s+", -1);
            // cpu user nice system idle iowait irq softirq steal (guest time is already inside user and nice)
            if (cpu.length < 9) {
                throw new Unavailable("/proc/stat cpu line has " + (cpu.length - 1) + " counters, expected 8");
            }
            long total = 0;
            for (int i = 1; i <= 8; i++) {
                total += Long.parseLong(cpu[i]);
            }
            long idle = Long.parseLong(cpu[4]) + Long.parseLong(cpu[5]);
            String self = Files.readString(Path.of("/proc/self/stat"), StandardCharsets.US_ASCII);
            // the command name is in parentheses and may hold spaces; the fields resume after the last ')'
            String[] fields = self.substring(self.lastIndexOf(')') + 1).trim().split("\\s+", -1);
            if (fields.length < 13) { // state is field 3 (index 0), utime 14 (11), stime 15 (12)
                throw new Unavailable("/proc/self/stat has too few fields");
            }
            return new CpuTimes(total, idle, Long.parseLong(fields[11]) + Long.parseLong(fields[12]));
        } catch (IOException | NumberFormatException e) {
            throw new Unavailable("/proc counters unreadable: " + e, e);
        }
    }
}
