package dev.strategos.core.content;

/**
 * A stat's address, such as {@code UnitMotion/walkSpeed}, interned to {@code id} when templates compile (04 §8.1), with
 * the range a modified value is clamped to ({@link CorePaths}).
 */
public record ValuePath(int id, String path, double min, double max) {}
