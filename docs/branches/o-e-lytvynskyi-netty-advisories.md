# netty-advisories on dev/o-e-lytvynskyi/netty-advisories

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/netty-advisories · **Work:** netty-advisories · **Base:** main @ 541f283 · **Claimed:** 2026-10-29 · **Status:** ready for review

**Note:** the owner's word in the maintainer session, 2026-10-29 («исключи эту уязвимость отдельным ПРом»); claim and delivery in one pull request on that word.

## Now

- **Work:** dependency-review failed every pull request touching `*.gradle` or workflows on Netty 4.1.97, which
  Minecraft 1.21.1 pins (and Guava 32.1.2, the same case). The seven advisories are allowed by id, with the reason in the workflow. Done; hand-in pull
  request open.
- **In progress:** none.
- **Merged from main last:** 541f283 (2026-10-29), stacked on the records pull request #42.

## Depends on unfinished work

- None.

## Log

### 2026-10-29 — Netty advisories allowed

- `.github/workflows/dependency-review.yml`: `allow-ghsas` for the six advisories reported on #41 (run 40921554738),
  all on `io.netty` 4.1.97.Final: GHSA-c4c3-7fpv-j4q5 (critical, SNI routing), GHSA-4g8c-wm8x-jfhw (SslHandler),
  GHSA-3qp7-7mw8-wx86 (IpSubnetFilter IPv6), GHSA-x4gw-5cx5-pgmh (SniHandler allocation), GHSA-c653-97m9-rcg9
  (trust manager hostname check), GHSA-fccg-mwvh-qqg4 (SNI reassembly). Each is in TLS or the subnet filter; the
  game's protocol is plain TCP and uses neither. The licence check and every other advisory stay enforced.
- The pull request's own run then reported GHSA-xxph-c9ww-hj94 (high, published 2026-10-27): Guava 32.1.2-jre,
  deserialization of CompactHashMap/CompactHashSet/MapMakerInternalMap allocates without bound. The same case: the
  game's pinned Guava (core takes it through DFU), and nothing deserializes Java-serialized Guava collections. Allowed
  with the Netty ones.

## For the owner

- None.

## Decisions taken

- Allow by advisory id, not by package: a new Netty advisory still fails the check. Undo: delete `allow-ghsas`.
