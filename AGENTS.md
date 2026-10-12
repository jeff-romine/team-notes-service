# AGENTS.md

Guidance for AI agents working in this repository.

- Build and test: `./mvnw verify` (Java 25; the wrapper fetches Maven). Run: `java -jar service/target/team-notes-service.jar`. Seeded users and the API walk are in `README.md`.
- `service/src/main/java/io/github/jeffromine/notes/domain/core` is the functional core and must stay pure: no clock, no random ids, no I/O, no exceptions for control flow. `ArchitectureTest` enforces it; keep it green.
- A change to a decider, guard, policy or text transform requires a co-located test in `service/src/test/java/.../domain/core`.
- Adapters implement the interfaces in `application/port`; a new adapter runs the existing contract test (`AbstractStoreContractTest`, `AbstractBlobStoreContractTest`, `AbstractSearchIndexContractTest`).
- No silent fallbacks outside `SpringNoteEventPublisher`, the one place a failure is deliberately logged and swallowed.
- Markdown paragraphs in this repo are one line each, never hard-wrapped.
