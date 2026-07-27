## Status
Accepted

## Context
This project is built solo, with an AI assistant (Gemini, Claude) used as an active pair-programming
partner throughout the development workflow — theory review, issue creation, branch work,
and PR generation. This differs from typical AI-assisted development in that the AI is
treated as a consistent team member across the full lifecycle, not an occasional autocomplete
tool.

In practice, this exposed a structural limitation common to all general-purpose chat LLMs:
finite context windows and session statelessness. Once a chat grew long enough, or a new
session was started, the assistant lost track of the domain model (`User`, `BikeModel`,
`BikeInstance`, `Reservation`), established architectural rules (e.g. optimistic locking vs.
database-level overlap constraints), and the repo's issue/PR template conventions. This
caused repeated re-explanation, and in several cases the assistant generated issues and PRs
using invented structures instead of the project's actual templates.

We evaluated three approaches: (1) switching to a different AI model or provider, (2) relying
purely on longer, more careful prompting within each session, and (3) externalizing project
context into the repository itself and grounding the assistant in it explicitly. Switching
models does not address the underlying cause — statelessness is a property of all current
chat LLMs, not a specific provider. Relying on prompting alone does not survive new sessions
and does not scale as the domain model and rule set grow.

## Decision
We will treat **AI context management as a documented part of the engineering workflow**,
not an ad hoc habit, using two artifacts:

- **`docs/context/PROJECT_CONTEXT.md`** — a committed, single-source-of-truth file covering
  stack, domain model, architectural rules with rationale, explicitly rejected anti-patterns,
  a domain glossary, conventions (templates, commit style, branching), and current sprint
  status. This file is treated as living documentation: updated at the end of each closed
  issue, not retroactively.
- **A configured Gemini Gem**, grounded in `PROJECT_CONTEXT.md` via attached knowledge, with
  system instructions that (a) require reading the context file before generating any
  issue, PR, or technical suggestion, (b) enforce exact adherence to existing templates
  rather than inventing new structures, (c) default to explanation-first, code-on-request
  behavior to support learning rather than blind code generation, and (d) require the
  assistant to flag stale or missing context rather than guess.

GitHub Copilot (installed in IntelliJ IDEA) is not included in this decision. Its role in
the workflow is unresolved and left as an open question — see Consequences.

## Consequences

### Positive
- **Portable grounding:** Any AI assistant (not just Gemini) can be grounded correctly by
  reading a single, version-controlled file, rather than depending on chat history.
- **Consistency at scale:** Issue and PR generation now reliably follows the repo's actual
  templates instead of assistant-invented structures.
- **Reduced repetition:** Domain model, schema, and architectural rules no longer need to be
  re-explained at the start of every session.
- **Documentation as a side effect:** The context file is independently useful to a human
  reviewer or future contributor, not just to AI — it doubles as onboarding documentation.
- **Forces explicit rationale:** Writing "why" alongside every architectural rule (not just
  "what") surfaces decisions that would otherwise stay implicit.

### Negative
- **Maintenance burden:** The context file and Gem knowledge attachments are not
  auto-synced with the codebase. A stale context file actively misleads rather than
  helping, which requires discipline to keep updated after every merged PR.
- **Does not eliminate in-session drift:** Grounding fixes cold-start amnesia at the start
  of a new chat, but does not prevent context degradation within a single long-running
  session. Long sessions still require manual restart discipline.
- **Two sources of truth risk:** The Gem's attached knowledge and the repo's actual
  `PROJECT_CONTEXT.md` can diverge if the Gem file isn't re-uploaded after edits.
- **Open question — Copilot's role:** GitHub Copilot is installed but not yet integrated
  into this workflow or governed by this ADR. Its fit (in-IDE, codebase-indexed assistance
  vs. chat-based Gem) is still being evaluated and may be addressed in a future ADR or
  issue if adopted.