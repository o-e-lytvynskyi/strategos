# JMH profiler allocation contamination on Linux

M7 paired run 41048405027 showed five allocating kernels on both delivered main and M7, with
the same approximately 0.208 MB/s background allocation rate. Timing ratios stayed below 15%
and foreign CPU was valid. This was evidence of shared measurement overhead, not attribution
to any changed kernel. The new InternalProfiler called the Linux MXBean CPU-load reader from
iteration callbacks inside the GC profiler's accounting window.

The repaired Linux path reads cumulative `/proc/stat` and `/proc/self/stat` counters through
reused file handles and byte buffers. It counts aggregate user/nice/system/idle/iowait/irq/
softirq/steal, excludes guest double counting, and handles spaces/parentheses in the process
name. The sole sampling allocation is a small CpuTimes value; malformed, unavailable or
inconsistent counters still produce invalid telemetry. Each profiler retains its two handles
for the JMH fork's lifetime; other callers close the reader.

Validation run 41051803906 applied the identical measurement-only repair to both pinned main
8111c0a and M7, recording its full control patch and source digests. All 27 configurations
matched, all existing timing/allocation/validity gates passed, and the non-event allocation
rate returned to about 0.0073 MB/s. Motion was 2.451/2.452 B/op versus 116.49/115.91 in the
earlier experiment. The runs used different hosts, so do not attribute their absolute timing
difference to the repair. The controlled within-run comparison isolates production-runtime
differences; the workload-independent allocation-rate change and bounded-allocation reader
test support the profiler diagnosis.

Never compensate for profiler allocation by increasing BACKGROUND_BYTES_PER_SECOND, subtracting
an observed constant, omitting GC metrics or changing a kernel baseline. The 14,000 B/s
allowance, 1 B/op floor, event allowance, 15% comparison and accepted CPU validity limits stay
unchanged. Non-Linux profiler behavior was retained; no new native baseline was accepted.
