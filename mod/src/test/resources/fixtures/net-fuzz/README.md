# Serverbound decoder corpus

Append cases; retain their IDs, payload type, exact bytes and expected outcome.
The seeded L2 run exercises at least 100,000 bounded inputs across every registered
serverbound codec, including valid controls, malformed bounds and mutations.
Seeds are committed in `seeds/m7-net-fuzz.txt`; `strategos.seed` reproduces a seed.
Unexpected exceptions retain the exact input under the cloud test reports before
it is promoted to a permanent case. Never replace a failing seed or soften a gate.
The bounded L3 subset is checked against this canonical corpus by L2.
