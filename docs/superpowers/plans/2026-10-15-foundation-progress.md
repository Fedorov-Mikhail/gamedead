# SDD ledger — plan: docs/superpowers/plans/2026-10-15-foundation.md

Execution: inline, approved 2026-10-15. New repository on codex/foundation in the approved workspace; no existing checkout to isolate. Baseline: only the approved spec and plan, no application or tests.

Pre-flight: Task 1 provides schema/build to Task 2; Task 2 provides account service/records to Task 3; Task 3 provides HTTP/security to Task 4. Interfaces match the approved spec.

Task 1: in progress. Official Gradle 9.8.1 and Spring Boot 4.1.1 endpoints verified. Initial sandbox networking restriction resolved by approved elevated download access.

Ruling: execute directly in this new repository, as approved in the plan; no separate checkout needed. Cost if wrong: no unrelated existing code was present to protect.

User preference: after the initial downloads, all further Gradle invocations use --offline. The user manually downloaded integration-test dependencies and reported successful compilation. No further downloads by the agent.

Task 1: guard RED (6 unsafe URLs accepted) -> GREEN (7 checks). Migration RED (no current migration) -> GREEN on isolated PostgreSQL 18.6 at 127.0.0.1:15432, database gamedead_test. Main gamedead never connected. Official Gradle distribution and generated wrapper checksums match the published values. Packaging check pending.

Task 2: account validation RED (missing normalization/bounds) -> GREEN. Registration service and real PostgreSQL tests pass, including two simultaneous normalized logins and transaction rollback on profile constraint failure.

Task 3: real HTTP RED (registration 401, endpoints absent) -> GREEN for registration/login/me/logout, isolated accounts, expired/revoked/disabled tokens. Session service unit checks pass; real server restart preserves an existing token.

Task 4: body-size RED (oversized and chunked requests reached handler) -> GREEN; real HTTP limits tested. Additional contract test caught incorrect HTTP 500 for unsupported media type; handler now returns 415. Final suite pending.

Ruling: use JUnit Jupiter 6.0.3 from the selected Spring Boot BOM instead of the initial JUnit 5 wording; actual dependency resolution already selected 6.0.3. Cost if wrong: extension compatibility would require reconsidering test dependencies; application behavior is unchanged.

Execution notes: account validation, token and limiter tests were written while Gradle downloaded; their stub implementations were observed failing before completion. Integration support uses actual HTTP rather than the planned redundant AuthApiTest controller mock. Git commits use explicit Codex author identity only for each invocation because user identity is unset; global Git configuration is unchanged.

Additional regression: URL-encoded auth paths bypassed the size filter (HTTP expected 413, received 400). Filters now match decoded servlet paths, consistent with controller routing; regression also checks an encoded login cannot bypass the rate limiter.
