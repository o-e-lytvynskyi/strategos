package dev.strategos.core.shared;

/** State that feeds a state hash, in a fixed order. */
public interface Hashable {
    void hashInto(Hasher h);
}
