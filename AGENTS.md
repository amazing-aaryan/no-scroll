## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Release and distribution guardrails

`RELEASE.md` is the source of truth for beta/release distribution, signing, versioning, and upgrade testing.

Before changing Android package/version/release configuration:

- Read `RELEASE.md` and `app/build.gradle`.
- Preserve `applicationId "com.noscroll"` for the existing app unless the user explicitly intends to create a separate app/package.
- Never reuse a `versionCode` that has already been uploaded to Google Play.
- Verify the current Google Play target API requirement before preparing a Play release; do not assume the repository's existing `targetSdk` is still accepted.
- Never commit signing keystores, signing passwords, Play service-account credentials, or other release secrets.
- Treat a successful Gradle build as necessary but not sufficient for beta/release readiness. Run the manual regression and Play-installed upgrade checks in `RELEASE.md`.
- When release behavior, package identity, versioning, permissions, or distribution flow changes, update `RELEASE.md` and any affected README/product documentation in the same change.

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
