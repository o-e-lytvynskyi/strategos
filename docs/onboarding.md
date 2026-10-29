# Onboarding

For a new developer, whatever AI tool they use — Claude Code, Codex, another agent, or none. It
covers the tools, the approach and the path from first clone to a merged change. Tool-specific
setup is in [developer-setup.md](developer-setup.md); the binding rules are in
[AGENTS.md](../AGENTS.md). Written 2026-10-25.

## 1. The project in five minutes

- **What:** a Minecraft 1.21.1 NeoForge mod recreating the RTS mechanics of 0 A.D. and Age of
  Empires. The plan is [ROADMAP.md](ROADMAP.md): milestones M0–M15, each a set of approved feature
  cards; a milestone is built from its implementation spec in `milestones/` (tasks T1..Tn).
- **How it is built:** the game rules live in `core` (plain Java, no Minecraft, deterministic and
  seed-stable); `mod` adapts them to Minecraft. Read
  [04-architecture.md](04-architecture.md) by section, and the ADRs it cites.
- **Where it stands:** [STATE.md](STATE.md) — "Milestones", "Open for claims"; who works on what:
  the branch files in [branches/](branches/README.md), one per claim.

| Directory | Contents |
|---|---|
| `core/` | the rules, their L1 tests, JMH benchmarks |
| `mod/` | the NeoForge adapter: L2 unit tests, L3 GameTests, the devtools source set (L4 client scenarios, the demo) |
| `build-logic/`, `gradle/` | build conventions, the version catalog |
| `perf/` | approved performance baselines |
| `tools/` | `roadmap.py` (statuses and progress), `claims.py` (who works on what), image tools |
| `docs/` | everything else: specs, cards, ADRs, research, the knowledge base `kb/`, branch files `branches/` |

## 2. Tools

| Tool | Why | Check |
|---|---|---|
| JDK 21 | the build; Gradle's toolchain resolver fetches it if missing | `./gradlew --version` |
| Git with SSH access to `o-e-lytvynskyi/strategos` | branches, pushes | `git fetch` |
| `gh`, logged in | pull requests, CI runs | `gh auth status` |
| Python 3 | `tools/*.py` (standard library only) | `python3 tools/roadmap.py check` |
| A Minecraft-capable desktop (optional) | L4 client scenarios; without one, CI's Xvfb route | `mod/src/devtools/README.md` |

Everyday commands (all end on their own; details in [05-testing.md](05-testing.md)):

| Command | When |
|---|---|
| `./gradlew verifyFast` | after every edit batch (static checks + L1) |
| `./gradlew verify` | before every task commit (L0–L3, L5, harness guards) |
| `./gradlew verify verifySlow verifySoak` | before committing movement, pathing, orders or tick changes; before a hand-in |
| `./gradlew :core:jmh :core:jmhCompare` | after touching the core's step, codecs or a `@HotPath` kernel |
| `./gradlew :mod:runScenario -Pscenario=<id>` | client scenarios, under AGENTS.md "Client windows" |
| `python3 tools/roadmap.py check` / `summary` | after any change to cards, specs or statuses |
| `python3 tools/claims.py --fetch list` / `owner` | before claiming; to see open questions |

## 3. The approach

- **Tests first, at the lowest level** that can express the behaviour (L1 before L3); every
  acceptance criterion of a card has a test at its stated level. Never weaken a check to get
  green.
- **Verify engine names** in the decompiled sources or with `javap` before using them; never from
  memory.
- **Clean-room:** never open the GPL or all-rights-reserved projects listed in AGENTS.md, not even
  on the web.
- **Small steps:** a task is 300–1,500 lines; larger ones are split in the spec first.
- **Records are part of the work:** the spec's "Progress", card statuses with evidence, your branch
  file, and new pitfalls in [kb/](kb/README.md). The repository must tell the next person what
  happened without asking you.
- **Decide and record:** take the recommended option, write it in "Decisions taken"; only what
  AGENTS.md keeps for the owner waits for the owner.
- **Context economy is a rule** for people and agents alike: read by heading and range, keep logs
  out of the conversation, delegate what produces large output.

## 4. Working with an AI agent (any tool)

The same structure whatever the tool ([agents.md](agents.md)):
- **The main session orchestrates** on the strongest model: it picks the work, designs, writes
  briefs, reads every diff, commits.
- **Workers on smaller models** do routine work against fixed contracts: `implementer`,
  `reviewer`, `runner`, `api-lookup`, `recorder`. Their definitions are in `.claude/agents/` (the
  bodies are tool-neutral text; Codex maps them as in developer-setup.md R2).
- **The procedure** the agent follows is `.claude/skills/strategos-dev/SKILL.md`, plain Markdown.
  Point any agent at AGENTS.md and that file, and say "continue".
- **Improving the agents is welcome:** new roles, better contracts, a role taken out of demo — by
  pull request, like code ([agents.md](agents.md) "Changing the roster").

Working without an agent is fine: follow the same procedure by hand.

## 5. From claim to merge

1. **Find work:** STATE "Open for claims"; `python3 tools/claims.py --fetch list` for what is taken
   (the branch files in [branches/](branches/README.md); a `reserved` one in your name is yours).
2. **Assess its dependencies:** what it needs from milestones not yet merged; plan independent
   work first, contracts and fakes where possible, `AFTER-M<n>: …` markers where something must be
   finished or validated later (procedure §2a step 0).
3. **Claim it before writing code:** branch `dev/<you>/<work>`, a commit with only your
   branch file ([branches/README.md](branches/README.md)), a pull request
   `Claim: <work>` to `main`. Start developing once a maintainer has merged it. Work assigned to
   someone else is never taken without their explicit approval.
4. **Build it** task by task: tests, code, gates, records, one commit per task, push your branch.
   **All tests are local until the milestone is done:** CI runs only on the hand-in pull request.
   From a fork, everything is the same at a lower priority (CONTRIBUTING.md).
   Merge `origin/main` into it when `main` moves; never rebase or force-push.
5. **Hand it in** when the claim is done: the milestone gates, `Status: ready for review`, a pull
   request whose body is the milestone report (§6).
6. **Review:** a maintainer session that did not build it validates it; findings come back in the
   pull request and your branch file's "Review"; fix them on the branch.
7. **Merge:** on the owner's word, by the maintainer. Meanwhile you have claimed the next work —
   stacked on your branch if it depends on it.

## 6. Reports

- **After every milestone (epic), a report** — mandatory. It is the hand-in pull request's body,
  and the same text in chat if someone is watching: what was built (cards), gate results with
  numbers, deviations and "Decisions taken", what needs the owner, the demo steps. Work does not
  pause for it.
- **Tasks (T) are documented, not reported:** the spec checkbox, card statuses with evidence and a
  log line in your branch file are required; a chat report per task is not.
- **When a session stops** (nothing ready, blocked, out of budget): a short note of why and what
  is next, in the branch file and in chat.

## 7. First day

- [ ] Access and clone; `./gradlew verifyFast` green.
- [ ] Read AGENTS.md, this page, the procedure's §1–§3, [05-testing.md](05-testing.md) "Levels".
- [ ] Set up your tool ([developer-setup.md](developer-setup.md)).
- [ ] Pick a handle and add yourself to [people.md](people.md) by pull request; claim a small piece of ready work; take it through §5 once.
