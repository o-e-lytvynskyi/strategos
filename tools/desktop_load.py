#!/usr/bin/env python3
"""A typical desktop's CPU load, simulated: calibration condition (c) of ADR-0026.

    python3 tools/desktop_load.py [--seconds N] [--seed S] [--scale F]

One process per simulated application, each holding a steady duty cycle of one core and drawing random bursts:
a browser (several tabs, one playing video), a chat client, a messenger, two editor windows with their language
servers, and the window server. The figures are those of an ordinary working session on a laptop (Activity Monitor,
"% CPU" of one core), not a stress test. --scale multiplies every figure. Runs until --seconds pass or it is
stopped (SIGINT or SIGTERM); the seed makes the burst schedule repeatable. Standard library only.
"""

import argparse
import multiprocessing
import os
import random
import signal
import sys
import time

SLICE = 0.01  # seconds: duty cycles are held per 10 ms slice

# name, steady share of one core, mean seconds between bursts, burst length range (s), burst share of one core
APPS = [
    ("browser-ui", 0.04, 20, (0.2, 1.0), 0.60),
    ("browser-tab-video", 0.12, 60, (0.5, 2.0), 0.35),
    ("browser-tab-page", 0.02, 15, (0.3, 2.0), 0.80),
    ("browser-tab-mail", 0.01, 45, (0.2, 0.8), 0.50),
    ("chat", 0.03, 25, (0.2, 1.0), 0.40),
    ("messenger", 0.01, 40, (0.1, 0.5), 0.30),
    ("editor-1", 0.02, 30, (0.5, 3.0), 0.90),
    ("editor-2", 0.01, 60, (0.5, 2.0), 0.70),
    ("window-server", 0.05, 10, (0.1, 0.4), 0.30),
]


def busy_for(seconds):
    end = time.perf_counter() + seconds
    x = 0
    while time.perf_counter() < end:
        x += 1
    return x


def app(name, steady, gap, burst_range, burst_share, seed, scale, stop_at):
    signal.signal(signal.SIGINT, signal.SIG_DFL)
    rng = random.Random(f"{seed}:{name}")
    next_burst = time.monotonic() + rng.expovariate(1 / gap)
    burst_end = 0.0
    while time.monotonic() < stop_at:
        now = time.monotonic()
        if now >= next_burst:
            burst_end = now + rng.uniform(*burst_range)
            next_burst = burst_end + rng.expovariate(1 / gap)
        share = min(1.0, (burst_share if now < burst_end else steady) * scale)
        busy_for(SLICE * share)
        time.sleep(SLICE * (1 - share))


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--seconds", type=float, default=float("inf"))
    parser.add_argument("--seed", default="adr-0026")
    parser.add_argument("--scale", type=float, default=1.0)
    args = parser.parse_args(argv)
    stop_at = time.monotonic() + args.seconds
    procs = [multiprocessing.Process(target=app, args=(*a, args.seed, args.scale, stop_at), name=a[0], daemon=True)
             for a in APPS]
    for p in procs:
        p.start()
    expected = sum(a[1] for a in APPS) * args.scale
    print(f"desktop load: {len(procs)} applications, steady {expected:.2f} of a core plus bursts, pid {os.getpid()}",
          flush=True)

    def stop(*_):
        for p in procs:
            p.terminate()
        sys.exit(0)

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    for p in procs:
        p.join()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
