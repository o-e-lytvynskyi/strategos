---
name: new-agent
description: Create, change or retire a delegated worker agent for Strategos (.claude/agents/*.md) when the roster in docs/agents.md does not cover a recurring kind of work — «сделай агента под …», «нужен агент для …», or when the orchestrator notices it keeps doing the same routine inline. Picks the tier and model, writes the role file with a strict output contract, trials it on a real task, and records it in the roster and the Codex spec.
---

# Making a new worker agent

The roster and its rules are in `docs/agents.md`; read it first. This skill adds to it.

## 1. Is a new agent justified?

Make one only if all hold:
- the work **recurs** — it has come up in at least two tasks, or a milestone spec plans it
  repeatedly (check `docs/worklog.md` and the spec);
- **no existing role fits** with a different brief. A new brief for `implementer`, `runner` or
  `reviewer` is cheaper than a new agent; prefer it;
- its output can be **summarised** in a fixed format much shorter than its input — that is where
  the saving comes from;
- it needs **no judgement** the orchestrator must own (design, scope, owner decisions).

Otherwise write the brief inline and stop.

## 2. Pick the tier

| The work is… | Tier | Claude model | Effort |
|---|---|---|---|
| running, reading, extracting, applying dictated edits | C | `haiku` | omit |
| writing code or tests from a complete brief | B | `sonnet` | `medium` |
| finding defects, comparing against specs, analysing measurements | B | `sonnet` | `high` |
| design, cross-cutting trade-offs | — | stays with the orchestrator | — |

Choose the smallest tier that does the work right on the trial (§4). Raise it only on evidence.

## 3. Write `.claude/agents/<name>.md`

Name: a lowercase noun for the role (`asset-checker`, not `helper2`). Frontmatter fields are
camelCase; unknown ones are ignored silently, so spell them exactly:

```
---
name: <name>
description: Tier-<B|C> <role> for Strategos. <What it does, in one sentence>. Use for <when>; never for <what it must not take>.
model: <haiku|sonnet>
effort: <medium|high>              # tier B only
disallowedTools: Agent, Edit, Write, NotebookEdit   # read-only roles; workers that edit drop Edit/Write from the list
omitClaudeMd: true                 # tier C roles that need no project rules; keep the rules for anything that writes code or judges it
color: <red|blue|green|yellow|purple|orange|pink|cyan>
---
```

The body, under ~60 lines:
1. **One paragraph:** what it does and who decided the work (the orchestrator).
2. **Rules:** the scope limit; the hard rules that apply to this role (clean-room, `core` without
   Minecraft, API verification, no weakened checks); **never commit, push or edit STATE /
   ROADMAP / worklog**; stop and report after two failed attempts.
3. **How:** paths and commands it needs, spelled the way `.claude/settings.json` allows them.
4. **Return:** a fenced template with a line limit. This contract is the point of the agent: the
   orchestrator reads only this.

Workers never spawn agents (`Agent` is always in `disallowedTools`).

## 4. Trial it

1. Run it on a real pending piece of work with a normal brief.
2. Check: did it stay in scope; is the return within its limit and sufficient to act on without
   re-reading what it read; was the result right (the orchestrator verifies it — re-run the
   command, read the diff).
3. Fix the role file and re-run until one clean trial. If the smaller tier fails twice on the
   substance (not the format), move it up a tier.

## 5. Record it

- `docs/agents.md` "The roster": a row (role, tier, use, returns).
- `docs/codex-setup.md`: the same role in the Codex spec's roster table, so the other tool keeps
  parity.
- The commit: `Docs: add the <name> agent`, with the trial's outcome in the message body.
- If it changes how a session works (a new step in the loop), update the `strategos-dev` skill.

## Changing or retiring an agent

Same evidence bar: a change follows a trial that showed the problem; an agent unused for a whole
milestone, or always overridden by its brief, is removed from `.claude/agents/`, `docs/agents.md`
and `docs/codex-setup.md` in one commit.
