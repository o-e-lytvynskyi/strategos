# Contributing to Strategos

Thank you for considering it. Strategos (a working title) is a Minecraft 1.21.1 NeoForge mod that
recreates the RTS mechanics of 0 A.D. and Age of Empires. It is built by volunteers, with or
without AI agents, under one set of rules: [AGENTS.md](AGENTS.md).

## Before you start

- **Volunteer work.** Contributions are voluntary and unpaid; there is no obligation on either
  side, and nothing here is a job or a contract for services.
- **The project's licence** is the [PolyForm Noncommercial License 1.0.0](LICENSE): all rights
  belong to the owner; you may use, change and share Strategos for personal and other
  non-commercial purposes; commercial use is not allowed for now. The terms may change after the
  full release.
- **Licence of contributions.** By opening a pull request you confirm that:
  1. the contribution is your own work, or you have the right to submit it, and it follows the
     clean-room rule below;
  2. you grant the owner a perpetual, worldwide, non-exclusive, royalty-free, irrevocable licence
     to use, copy, modify, distribute, sublicense and relicense your contribution under any terms,
     including commercial ones and the licence chosen at the full release;
  3. you keep your copyright, and the owner has no duty to use your contribution.

  If you cannot agree, please do not submit.
- **Clean-room.** Never read or copy the GPL or all-rights-reserved projects listed in AGENTS.md
  (Reign of Nether, MineColonies, Villager Recruits and others, 0 A.D. code or data), not even on
  the web. MIT/Apache-2.0 code may be adapted with its notice in `NOTICE`.

## How to join

1. Read [docs/onboarding.md](docs/onboarding.md): tools, approach, the path from claim to merge,
   reports. Tool-specific setup (Claude Code, Codex): [docs/developer-setup.md](docs/developer-setup.md).
2. Add yourself to [docs/people.md](docs/people.md) as a developer, in your first pull request.
3. Find work in [docs/STATE.md](docs/STATE.md) "Open for claims" and check
   [docs/claims.md](docs/claims.md) for what is taken.

## The flow

1. **Claim before you code**: a pull request that adds your row to `docs/claims.md` and your branch
   file, nothing else. Develop only after it is merged. Work assigned to someone else is never
   taken without their explicit approval.
2. **Build on your branch**, test-first, recording progress in your branch file.
3. **Test locally until the milestone is done.** CI runs only on hand-in pull requests to `main`
   (not on pushes, not on drafts), to save runner minutes. Do not open the hand-in pull request
   early; keep it a draft if you must.
4. **Hand in** a pull request whose body is the milestone report (the template guides you).
5. **Review** by a maintainer, with fixes on your branch; the owner merges.

## Collaborators and forks

- **Collaborators** (write access, listed in people.md) work on `dev/<handle>/<work>` branches in
  this repository.
- **Forks are equal to developer branches, at a lower priority.** Everyone can contribute from a
  fork: the same claims, the same records, the same review. Your claims row names the branch as
  `<your-login>/strategos:dev/<handle>/<work>`. Lower priority means: when a collaborator's claim
  and yours compete for the same work, the collaborator's goes first; reviews take collaborators'
  hand-ins first; the owner accepts outside claims by hand and approves each CI run of a first
  pull request. A fork contributor who keeps delivering may be offered collaborator access.

## Using AI agents

Welcome, with any tool. The same rules apply to people and agents; you are responsible for what
your agent submits. Commits and pull requests carry no traces of the agent or its tool, and no
personal data ([docs/security.md](docs/security.md) "Privacy"). Agent roles live in
`.claude/agents/` and may be improved by pull request ([docs/agents.md](docs/agents.md)).

## Platforms and measurements

So far everything has been developed and measured on one machine: an Apple M4 Pro with 24 GB on
macOS 26. CI runs the tests on Linux. **Windows has not been tested at all**, and every performance
baseline reflects that one Mac, so it is subjective. Results from other machines, operating
systems and GPUs are among the most useful contributions: use the "Benchmark or platform report"
issue template.

## Reporting

- **Bugs:** the "Bug report" issue template.
- **Vulnerabilities:** privately, see [SECURITY.md](SECURITY.md).
- **Conduct:** [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
