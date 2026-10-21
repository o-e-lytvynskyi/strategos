# Fixture-check self-test inputs

A small fake repository for `checkFixturesSelftest` (QA-17.5, M0 spec 7.10). `checkFixtures` must
report each of its three broken inputs: a test naming a fixture that does not exist, a test passing
a literal seed, and a `released-fixtures.txt` naming a deleted file. Nothing here is compiled.
