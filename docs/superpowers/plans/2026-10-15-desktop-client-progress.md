# Desktop client implementation record

Specification: `docs/superpowers/specs/2026-10-15-desktop-client-design.md`.

The user approved the specification and explicitly requested implementation without another planning/approval cycle. Work continued in the existing `codex/foundation` checkout. No server implementation, HTTP contracts, or database migrations were changed.

## Delivered

- `client → protocol`, Java 21, libGDX 1.14.2, Jackson 3.1.5; desktop launcher, Russian registration/login/profile screens and procedural fantasy theme.
- Async HTTP with bounded responses, full-body deadline, safe status errors and no redirect forwarding; local-memory session and UI-generation guard.
- User-run dependency/font preparation and offline client startup scripts, desktop distribution and startup documentation.
- Noto Sans and its OFL license bundled with client resources. New dependencies and font were downloaded by the user, not the agent.

## Verification evidence

- HTTP tests initially failed against the unimplemented API; 23 now pass against real local JDK HttpServer fixtures, including Unicode/CRLF transport, authorization, redirect, body size, malformed responses, timeout, unavailable server and error statuses.
- State tests exposed missing transitions before implementation; all 11 now pass, covering registration, validation, profile-before-menu, duplicate submission, logout, expiration response, stale completion, UI dispatch and close.
- The explicit `:client:desktopCheck --offline` task renders real hidden libGDX/OpenGL windows at 1280×720 and 960×640, exercises registration/login/profile/refresh/logout and saves screenshots under `client/build/reports/desktop`. Screenshots were inspected for layout and Cyrillic rendering. This task is excluded from the ordinary build.
- Review found stock TextField silently stripping CR/LF from pasted passwords. Actual Ctrl+V regression with an in-memory clipboard failed before the fix. `ExactPasswordField` preserves pasted content and keeps physical Enter as submit. The graphics regression now passes at both sizes; it verifies emoji, CRLF and surrounding spaces, visibility toggle, password clearing, and exact API credentials. No access to the user's system clipboard was needed.
- Independent read-only reviewer confirmed the password finding was addressed and found no residual issue in the reviewed client, scripts and documentation.
- Final `build :client:installDist --rerun-tasks --offline --console=plain`: BUILD SUCCESSFUL; 34 client and 32 server tests, zero failures. Known upstream JVM CDS warning from Mockito remains. No new downloads or database connections.
- Distribution checked for bundled fonts/OFL, protocol and native libraries; Spring/JPA/PostgreSQL dependencies are absent from client distribution.

## Scope and remaining manual check

Live registration/login against the user's Spring/PostgreSQL server and physical keyboard/system clipboard interactions remain user acceptance checks. The graphics harness uses a controlled API, and HTTP tests use isolated local fixtures. Existing PostgreSQL integration tests were not rerun because server implementation and migrations were unchanged and the user's working database must remain separate from test fixtures.

Multiplayer rooms, arena simulation, combat and boss are subsequent stages. The target remains 2–4 players, about 30 minutes per run and Shift dash.
