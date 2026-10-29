# netty-advisories on dev/o-e-lytvynskyi/netty-advisories

**Developer:** o-e-lytvynskyi (Claude Code, Opus) · **Branch:** dev/o-e-lytvynskyi/netty-advisories · **Work:** netty-advisories · **Base:** main @ 541f283 · **Claimed:** 2026-10-29 · **Status:** ready for review

**Note:** the owner's word in the maintainer session, 2026-10-29 («исключи эту уязвимость отдельным ПРом», then «Исключить зависимости игры»); claim and delivery in one pull request on that word.

## Now

- **Work:** dependency review failed every pull request touching `*.gradle` or workflows on advisories in the
  libraries Minecraft 1.21.1 and NeoForge's build pin. main now submits its dependency graph, so a pull request is
  judged only on what it adds or changes. Done; hand-in pull request open.
- **In progress:** none.
- **Merged from main last:** 541f283 (2026-10-29), stacked on the records pull request #42.

## Depends on unfinished work

- None.

## Log

### 2026-10-29 — dependency review judges only the pull request's own changes

- First try: `allow-ghsas` for the six Netty 4.1.97 advisories of #41's run; the next runs surfaced more (Guava
  32.1.2, then Netty codec). A query of the advisory database for all 197 dependencies of the graph found 38
  advisories, all in game- or build-pinned libraries (Netty 13, LZ4 Java 1.8.0 7, Commons Compress 1.18 6, Log4j
  2.22.1 6, Guava, Commons Lang, JLine, Plexus Utils). The allow-list was dropped.
- Cause: the review log says "snapshots compared for the base SHA (0) and the head SHA (1) do not match": main never
  had a submitted Gradle graph, so every dependency counted as added.
- Fix: `.github/workflows/dependency-graph.yml` submits main's graph on each push to main (and by hand,
  `workflow_dispatch`); dependency-review.yml is unchanged. docs/security.md says so. This pull request's own run
  stays red: its base has no graph yet. The first pull request based on main after this merge is the check.

## For the owner

1. After merging, run `dependency-graph` once by hand (Actions → dependency-graph → Run workflow) only if a pull
   request's base predates the merge; otherwise each push to main submits it.
2. The 38 advisories in the game's libraries are not fixed, only no longer reported on every pull request; they are
   judged again when Minecraft or NeoForge moves (Dependabot does not move them, docs/security.md).

## Decisions taken

- A graph for main instead of an allow-list or excluded configurations: the review then checks exactly what a pull
  request adds or changes, with no list to keep. It adds a ~4-minute job on each push to main (the repository is
  public, so the minutes are free), against the rule that CI runs on pull requests only; it runs no tests. Undo:
  delete the workflow.
