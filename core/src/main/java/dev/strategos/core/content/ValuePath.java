package dev.strategos.core.content;

/** A stat's address, such as {@code UnitMotion/walkSpeed}, interned to {@code id} when templates compile (04 §8.1). */
public record ValuePath(int id, String path) {}
