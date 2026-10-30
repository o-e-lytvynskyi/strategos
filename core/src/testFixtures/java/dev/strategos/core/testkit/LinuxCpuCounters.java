package dev.strategos.core.testkit;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Reusable Linux CPU-counter reader for allocation-sensitive profilers. Only the returned CpuTimes is allocated;
 * proc files are read into fixed buffers without decoding or splitting strings. A profiler retains this reader
 * for its fork's lifetime; shorter-lived callers close it. Counters use the same kernel clock-tick unit.
 */
public final class LinuxCpuCounters implements AutoCloseable {
    private final RandomAccessFile system;
    private final RandomAccessFile process;
    private final byte[] systemBytes = new byte[4096];
    private final byte[] processBytes = new byte[4096];

    public LinuxCpuCounters(Path systemPath, Path processPath) throws IOException {
        system = new RandomAccessFile(systemPath.toFile(), "r");
        try {
            process = new RandomAccessFile(processPath.toFile(), "r");
        } catch (IOException e) {
            system.close();
            throw e;
        }
    }

    public static LinuxCpuCounters open() throws IOException {
        return new LinuxCpuCounters(Path.of("/proc/stat"), Path.of("/proc/self/stat"));
    }

    public CpuTimes read() throws IOException {
        system.seek(0);
        int count = system.read(systemBytes);
        if (count < 4
                || systemBytes[0] != 'c'
                || systemBytes[1] != 'p'
                || systemBytes[2] != 'u'
                || systemBytes[3] != ' ') {
            throw new IOException("no aggregate cpu counters");
        }
        int end = 4;
        while (end < count && systemBytes[end] != '\n') end++;
        if (end == count && count == systemBytes.length) throw new IOException("aggregate cpu line too long");
        int at = 4;
        long total = 0;
        long idle = 0;
        for (int field = 0; field < 8; field++) {
            at = skipSpaces(systemBytes, at, end);
            int next = numberEnd(systemBytes, at, end);
            long value = number(systemBytes, at, next);
            total = Math.addExact(total, value);
            if (field == 3 || field == 4) idle = Math.addExact(idle, value);
            at = next;
        }

        process.seek(0);
        count = process.read(processBytes);
        if (count <= 0 || count == processBytes.length) throw new IOException("process cpu record missing or too long");
        at = count - 1;
        while (at >= 0 && processBytes[at] != ')') at--;
        if (at < 0) throw new IOException("process command terminator missing");
        at++;
        long own = 0;
        for (int field = 3; field <= 15; field++) {
            at = skipSpaces(processBytes, at, count);
            int next = at;
            while (next < count && processBytes[next] != ' ' && processBytes[next] != '\n') next++;
            if (next == at) throw new IOException("process cpu fields missing");
            if (field == 14 || field == 15) own = Math.addExact(own, number(processBytes, at, next));
            at = next;
        }
        return new CpuTimes(total, idle, own);
    }

    private static int skipSpaces(byte[] bytes, int at, int end) {
        while (at < end && bytes[at] == ' ') at++;
        return at;
    }

    private static int numberEnd(byte[] bytes, int at, int end) throws IOException {
        int next = at;
        while (next < end && bytes[next] >= '0' && bytes[next] <= '9') next++;
        if (next == at || (next < end && bytes[next] != ' ')) throw new IOException("malformed cpu counter");
        return next;
    }

    private static long number(byte[] bytes, int at, int end) throws IOException {
        if (at == end) throw new IOException("empty cpu counter");
        long value = 0;
        for (int i = at; i < end; i++) {
            int digit = bytes[i] - '0';
            if (digit < 0 || digit > 9) throw new IOException("malformed cpu counter");
            value = Math.addExact(Math.multiplyExact(value, 10), digit);
        }
        return value;
    }

    @Override
    public void close() throws IOException {
        try {
            process.close();
        } finally {
            system.close();
        }
    }
}
