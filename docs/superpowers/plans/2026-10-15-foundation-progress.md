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

Final review: independent read-only reviewer reviewed c9eed71..0885333, confirmed the previous 40 tests, found no Critical issues and one Important issue: independent Flyway connection properties could bypass the integration datasource guard. Regression used a harmless closed port, failed trying that port before fix. Both bootstraps now share guarded connection properties for datasource, Hikari and Flyway, including credentials. Second regression covers Spring's dynamic property bootstrap.

Final: Ruling: re-grade the review's Unicode-login documentation issue from Minor to Important in this Windows PowerShell 5.1/Russian-user context: the documented sequence could register a Cyrillic password correctly then corrupt it on login. Fix the example to send explicit UTF-8 bytes and exercise a Cyrillic/emoji password in the HTTP contract test. Cost if wrong: only a documentation encoding change; server protocol is unchanged.

Final: Ruling: retain documented scope for external HTTPS/proxy and multi-server counters, old session-row cleanup at future scale, and graphical client/gameplay; these were the review's Declined to judge items. No external deployment, multiple server nodes or game client is claimed. Cost if wrong: these features require a later implementation stage before their use.

Final: fixed independent Flyway configuration bypass — inheritedFlywayConnectionCannotOverrideTestDatabase RED (connection attempted to harmless closed port) -> GREEN; independentConnectionOverridesCannotEscapeTestDatabase also checks actual Flyway and application connection catalogs. The shared guard pins datasource, Hikari and Flyway credentials/URLs.

Final verification: `gradlew.bat clean build :server:integrationTest --offline --console=plain` -> BUILD SUCCESSFUL; 42 tests, 0 failures, 0 errors (32 unit, 10 PostgreSQL/HTTP). UTF-8 Cyrillic/emoji password login passes. No libGDX/LWJGL in server runtime graph. Packaged executable JAR starts on isolated test DB and returns the expected JSON 401. Windows launcher parses successfully; source has a UTF-8 BOM for Windows PowerShell 5.1.

Task 1: complete — Wrapper and checksums, independent build, PostgreSQL migration and safe repeat, isolated schema guard, runtime dependency separation, executable JAR.
Task 2: complete — normalized account/profile creation, BCrypt, transaction rollback and concurrent login conflict.
Task 3: complete — HTTP registration/login/me/logout, persisted sessions, expiration/revocation/disabled-account handling, restart verification.
Task 4: complete — bounded request body (including unknown length), bounded per-IP counters, encoded path regression, safe errors, Russian launch/API instructions and independent final review.

Deferred minors: none from final review; the Unicode documentation item was re-graded and fixed. JVM emits the upstream Mockito/CDS sharing warning in tests; no Gradle deprecation or application test failures remain.

Delivery: local branch codex/foundation retained in the user's original project folder. There is no base/remote branch to merge or push into. Main database gamedead was not accessed; isolated temporary PostgreSQL is stopped and scratch downloads/data are removed, preserving the user's Gradle dependency cache.
