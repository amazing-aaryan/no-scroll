## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

<!-- universal-session-context:start -->
## Universal Session Context Protocol

This file is mandatory session context for all agents.

- At session start, read this `AGENTS.md` and every applicable `reasoning.md`.
- If `reasoning.md` is missing in the session cwd, create it before substantive work.
- Treat `reasoning.md` as append-only decision memory. Do not delete or rewrite prior entries unless the user explicitly asks.
- After any notable decision, append a dated entry to `reasoning.md` with `Decision`, `Why`, and `Impact`.
- Subagents and delegated agents must follow the same read and update protocol before acting.

Reasoning entry format:

```markdown
## [YYYY-MM-DD HH:MM] <one-line summary>
**Decision:** <specific action or approach>
**Why:** <tradeoff, constraint, or preference>
**Impact:** <what future agents must know>
```
<!-- universal-session-context:end -->
