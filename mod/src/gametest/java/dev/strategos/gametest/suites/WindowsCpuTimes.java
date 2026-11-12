package dev.strategos.gametest.suites;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.FILETIME;
import dev.strategos.core.testkit.CpuTimes;

/**
 * Same-window Windows CPU accounting through JNA already supplied by Minecraft. Kernel system time includes idle;
 * all counters are in 100 ns units and sum across processors. No recent-load sampling intervals are mixed.
 *
 * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getsystemtimes">GetSystemTimes</a>
 * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getprocesstimes">GetProcessTimes</a>
 */
final class WindowsCpuTimes {
    private WindowsCpuTimes() {}

    static CpuTimes read() {
        if (Runtime.getRuntime().availableProcessors() > 64) {
            throw new IllegalStateException(
                    "Windows CPU accounting requires a single processor group (at most 64 CPUs)");
        }
        FILETIME idle = new FILETIME();
        FILETIME kernel = new FILETIME();
        FILETIME user = new FILETIME();
        FILETIME created = new FILETIME();
        FILETIME exited = new FILETIME();
        FILETIME ownKernel = new FILETIME();
        FILETIME ownUser = new FILETIME();
        Kernel32 api = Kernel32.INSTANCE;
        if (!api.GetSystemTimes(idle, kernel, user)
                || !api.GetProcessTimes(api.GetCurrentProcess(), created, exited, ownKernel, ownUser)) {
            throw new IllegalStateException("Windows CPU counters unavailable, error " + api.GetLastError());
        }
        return new CpuTimes(value(kernel) + value(user), value(idle), value(ownKernel) + value(ownUser));
    }

    private static long value(FILETIME time) {
        return Integer.toUnsignedLong(time.dwHighDateTime) << 32 | Integer.toUnsignedLong(time.dwLowDateTime);
    }
}
