package dev.strategos.selftest;

// checkFixturesSelftest's broken input (never compiled): a fixture that does not exist and a literal seed.
class BrokenFixtureTest {
    void readsAMissingFixture() {
        load("fixtures/match_v9.json");
    }

    void usesALiteralSeed() {
        builder().seed(1234).build();
    }
}
