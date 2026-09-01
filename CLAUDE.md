## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Release safety

Before work that can affect distribution or upgrades, read `RELEASE.md` and `app/build.gradle`.

- Keep the existing package ID `com.noscroll` stable unless the task explicitly calls for a new app/package.
- Increment `versionCode` for every new Play-distributed build and never reuse an uploaded code.
- Verify the current Google Play target API policy when preparing a release.
- Keep keystores, signing passwords, Play credentials, and other secrets out of Git.
- Do not call a build beta-ready solely because it compiles. Use the automated and physical-device regression gates in `RELEASE.md`, including testing the artifact installed through the intended distribution channel.
- Update `RELEASE.md` whenever release/signing/versioning/package/distribution assumptions change.
