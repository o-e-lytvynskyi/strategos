/*
 * Adapted from RVO2 Library 2.0.3, src/Agent.cc (https://github.com/snape/RVO2, commit
 * 75822bba14462c2e872ca96423825017ecd24764).
 *
 * Copyright 2008 University of North Carolina at Chapel Hill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Changes from the original, by the Strategos authors (2026):
 * - ported from C++ to Java, in double precision instead of float, on the XZ plane;
 * - only the solver (linearProgram1-3) and the agent ORCA lines of Agent::computeNewVelocity are kept; the kd-tree,
 *   the simulator and the obstacle polygons are not, and obstacles are half-planes given by the caller (addLimit);
 * - each agent line takes a responsibility: one half for another unit, as in RVO2, the whole for a non-reactive agent;
 * - a collision whose relative velocity leaves no direction adds no line instead of dividing by zero;
 * - lines live in a pool of mutable objects, so a solve allocates nothing;
 * - a solve whose preferred velocity a line binds is repeated with the preference turned 0.001 radian anticlockwise,
 *   so two agents meeting head-on on one line pass each other instead of braking to a stop (what RVO2's examples break
 *   with random noise, deterministically).
 */
package dev.strategos.core.sim.move;

import dev.strategos.core.shared.HotPath;

/**
 * ORCA's velocity solver (PATH-06, 04 §9.5): the velocity nearest a preferred one, within a maximum speed, that keeps
 * clear of every neighbour for a time horizon, given as half-planes of velocities. Static limits (blocked surface edges)
 * come first and are never given up; where the agent lines together allow nothing, the velocity that least violates
 * them is taken (linear program 3). Where a line binds, the preference is turned {@value #KEEP_SIDE} radian
 * anticlockwise and solved again: every agent leans the same way, so a pair meeting head-on on one line passes, where
 * plain ORCA brakes both to a stop. Velocities are in blocks a tick, times in ticks. Deterministic: the same lines in
 * the same order give the same velocity.
 */
@HotPath
public final class Orca {
    /** RVO2's tolerance for parallel lines. */
    static final double EPSILON = 1e-5;

    /** How far a bound preference turns, in radians: enough to break a symmetry, too little to show. */
    static final double KEEP_SIDE = 1e-3;

    private OrcaLine[] lines;
    private OrcaLine[] projected;
    private int count;
    private int limits;
    private double resultX;
    private double resultZ;

    /** A solver for up to {@code capacity} lines, growing past it if ever needed. */
    public Orca(int capacity) {
        this.lines = pool(capacity);
        this.projected = pool(capacity);
    }

    /** Starts a new solve: no lines. */
    public void clear() {
        count = 0;
        limits = 0;
    }

    public int lines() {
        return count;
    }

    /**
     * A static limit: the velocity's component along the unit normal (nx, nz) may not exceed {@code limit}. Limits come
     * before every agent line.
     */
    public void addLimit(double nx, double nz, double limit) {
        if (limits != count) {
            throw new IllegalStateException("a static limit after an agent line");
        }
        // the allowed side, left of the direction (-nz, nx), is the one away from the normal
        next().set(limit * nx, limit * nz, -nz, nx);
        limits++;
    }

    /**
     * The ORCA line of a neighbour at (relX, relZ) from the agent, moving at (otherVx, otherVz) while the agent moves at
     * (vx, vz), with radii summing to {@code combinedRadius}, kept clear for {@code horizon} ticks; the agent takes
     * {@code responsibility} of the avoidance: one half for another unit, which takes the other half, the whole for a
     * non-reactive agent.
     */
    public void addAgent(
            double relX,
            double relZ,
            double vx,
            double vz,
            double otherVx,
            double otherVz,
            double combinedRadius,
            double horizon,
            double responsibility) {
        double relVx = vx - otherVx;
        double relVz = vz - otherVz;
        double distSq = relX * relX + relZ * relZ;
        double radiusSq = combinedRadius * combinedRadius;
        double dirX;
        double dirZ;
        double uX;
        double uZ;
        if (distSq > radiusSq) {
            double invHorizon = 1 / horizon;
            double wX = relVx - invHorizon * relX; // from the cut-off centre to the relative velocity
            double wZ = relVz - invHorizon * relZ;
            double wLengthSq = wX * wX + wZ * wZ;
            double dot = wX * relX + wZ * relZ;
            if (dot < 0 && dot * dot > radiusSq * wLengthSq) {
                // project on the cut-off circle
                double wLength = Math.sqrt(wLengthSq);
                double unitX = wX / wLength;
                double unitZ = wZ / wLength;
                dirX = unitZ;
                dirZ = -unitX;
                double scale = combinedRadius * invHorizon - wLength;
                uX = scale * unitX;
                uZ = scale * unitZ;
            } else {
                // project on the legs
                double leg = Math.sqrt(distSq - radiusSq);
                if (det(relX, relZ, wX, wZ) > 0) {
                    dirX = (relX * leg - relZ * combinedRadius) / distSq;
                    dirZ = (relX * combinedRadius + relZ * leg) / distSq;
                } else {
                    dirX = -(relX * leg + relZ * combinedRadius) / distSq;
                    dirZ = -(-relX * combinedRadius + relZ * leg) / distSq;
                }
                double along = relVx * dirX + relVz * dirZ;
                uX = along * dirX - relVx;
                uZ = along * dirZ - relVz;
            }
        } else {
            // a collision: project on the cut-off circle of one tick
            double wX = relVx - relX;
            double wZ = relVz - relZ;
            double wLength = Math.sqrt(wX * wX + wZ * wZ);
            if (wLength == 0) {
                return; // no direction to leave in; the caller keeps coincident agents apart
            }
            double unitX = wX / wLength;
            double unitZ = wZ / wLength;
            dirX = unitZ;
            dirZ = -unitX;
            double scale = combinedRadius - wLength;
            uX = scale * unitX;
            uZ = scale * unitZ;
        }
        next().set(vx + responsibility * uX, vz + responsibility * uZ, dirX, dirZ);
    }

    /** Solves for the velocity nearest (prefX, prefZ) within {@code maxSpeed}; read it with {@link #x()}, {@link #z()}. */
    public void solve(double prefX, double prefZ, double maxSpeed) {
        program(prefX, prefZ, maxSpeed);
        if (resultX != prefX || resultZ != prefZ) {
            program(prefX - prefZ * KEEP_SIDE, prefZ + prefX * KEEP_SIDE, maxSpeed);
        }
    }

    private void program(double prefX, double prefZ, double maxSpeed) {
        int fail = linearProgram2(lines, count, maxSpeed, prefX, prefZ, false);
        if (fail < count) {
            linearProgram3(fail, maxSpeed);
        }
    }

    public double x() {
        return resultX;
    }

    public double z() {
        return resultZ;
    }

    /**
     * Linear program 1: the best velocity on line {@code lineNo} under the lines before it and the speed circle; false
     * when they leave none, the result then unchanged.
     */
    private boolean linearProgram1(
            OrcaLine[] set, int lineNo, double radius, double optX, double optZ, boolean directionOpt) {
        OrcaLine line = set[lineNo];
        double dot = line.pointX * line.directionX + line.pointZ * line.directionZ;
        double discriminant = dot * dot + radius * radius - (line.pointX * line.pointX + line.pointZ * line.pointZ);
        if (discriminant < 0) {
            return false; // the speed circle rules out the whole line
        }
        double sqrtDiscriminant = Math.sqrt(discriminant);
        double tLeft = -dot - sqrtDiscriminant;
        double tRight = -dot + sqrtDiscriminant;
        for (int i = 0; i < lineNo; i++) {
            OrcaLine other = set[i];
            double denominator = det(line.directionX, line.directionZ, other.directionX, other.directionZ);
            double numerator =
                    det(other.directionX, other.directionZ, line.pointX - other.pointX, line.pointZ - other.pointZ);
            if (Math.abs(denominator) <= EPSILON) {
                if (numerator < 0) {
                    return false; // (almost) parallel, and the other line rules this one out
                }
                continue;
            }
            double t = numerator / denominator;
            if (denominator >= 0) {
                tRight = Math.min(tRight, t); // the other line bounds this one on the right
            } else {
                tLeft = Math.max(tLeft, t); // on the left
            }
            if (tLeft > tRight) {
                return false;
            }
        }
        double t;
        if (directionOpt) {
            t = optX * line.directionX + optZ * line.directionZ > 0 ? tRight : tLeft;
        } else {
            t = line.directionX * (optX - line.pointX) + line.directionZ * (optZ - line.pointZ);
            if (t < tLeft) {
                t = tLeft;
            } else if (t > tRight) {
                t = tRight;
            }
        }
        resultX = line.pointX + t * line.directionX;
        resultZ = line.pointZ + t * line.directionZ;
        return true;
    }

    /**
     * Linear program 2: the best velocity under the first {@code n} lines of {@code set} and the speed circle; returns
     * the line it failed on, or {@code n}.
     */
    private int linearProgram2(OrcaLine[] set, int n, double radius, double optX, double optZ, boolean directionOpt) {
        if (directionOpt) {
            resultX = optX * radius; // the optimisation velocity has unit length here
            resultZ = optZ * radius;
        } else if (optX * optX + optZ * optZ > radius * radius) {
            double length = Math.sqrt(optX * optX + optZ * optZ);
            resultX = optX / length * radius;
            resultZ = optZ / length * radius;
        } else {
            resultX = optX;
            resultZ = optZ;
        }
        for (int i = 0; i < n; i++) {
            OrcaLine line = set[i];
            if (det(line.directionX, line.directionZ, line.pointX - resultX, line.pointZ - resultZ) > 0) {
                double keptX = resultX;
                double keptZ = resultZ;
                if (!linearProgram1(set, i, radius, optX, optZ, directionOpt)) {
                    resultX = keptX;
                    resultZ = keptZ;
                    return i;
                }
            }
        }
        return n;
    }

    /**
     * Linear program 3: from line {@code beginLine} on, where the agent lines allow no velocity, the one that violates
     * them least, the static limits still kept.
     */
    private void linearProgram3(int beginLine, double radius) {
        double distance = 0;
        for (int i = beginLine; i < count; i++) {
            OrcaLine line = lines[i];
            if (det(line.directionX, line.directionZ, line.pointX - resultX, line.pointZ - resultZ) <= distance) {
                continue;
            }
            int n = 0;
            for (int k = 0; k < limits; k++) {
                projected[n++].copy(lines[k]);
            }
            for (int j = limits; j < i; j++) {
                OrcaLine other = lines[j];
                double determinant = det(line.directionX, line.directionZ, other.directionX, other.directionZ);
                double pointX;
                double pointZ;
                if (Math.abs(determinant) <= EPSILON) {
                    if (line.directionX * other.directionX + line.directionZ * other.directionZ > 0) {
                        continue; // parallel, the same way
                    }
                    pointX = 0.5 * (line.pointX + other.pointX); // parallel, opposite ways
                    pointZ = 0.5 * (line.pointZ + other.pointZ);
                } else {
                    double s = det(
                                    other.directionX,
                                    other.directionZ,
                                    line.pointX - other.pointX,
                                    line.pointZ - other.pointZ)
                            / determinant;
                    pointX = line.pointX + s * line.directionX;
                    pointZ = line.pointZ + s * line.directionZ;
                }
                double dirX = other.directionX - line.directionX;
                double dirZ = other.directionZ - line.directionZ;
                double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
                projected[n++].set(pointX, pointZ, dirX / length, dirZ / length);
            }
            double keptX = resultX;
            double keptZ = resultZ;
            if (linearProgram2(projected, n, radius, -line.directionZ, line.directionX, true) < n) {
                // in principle impossible: the result already lies in this program's region; a rounding error
                resultX = keptX;
                resultZ = keptZ;
            }
            distance = det(line.directionX, line.directionZ, line.pointX - resultX, line.pointZ - resultZ);
        }
    }

    private OrcaLine next() {
        if (count == lines.length) {
            lines = grow(lines);
            projected = grow(projected);
        }
        return lines[count++];
    }

    private static double det(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }

    private static OrcaLine[] pool(int capacity) {
        OrcaLine[] pool = new OrcaLine[Math.max(1, capacity)];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new OrcaLine();
        }
        return pool;
    }

    private static OrcaLine[] grow(OrcaLine[] old) {
        OrcaLine[] grown = pool(old.length * 2);
        System.arraycopy(old, 0, grown, 0, old.length);
        return grown;
    }
}
