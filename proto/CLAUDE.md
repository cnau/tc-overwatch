# CLAUDE.md — proto

@../docs/claude/proto.md

## Module overview

`:proto` is the API contract module. `.proto` files in `src/main/proto/com/tcoverwatch/<feature>/v<N>/` are the single source of truth for every RPC, message, and enum the system speaks. The Gradle protobuf plugin generates the backend Kotlin/Java stubs at build time; Buf generates the frontend TypeScript clients (output to `frontend/src/gen/`).

## Commands

```
./gradlew :proto:build         # regenerate backend Kotlin/Java stubs
buf lint                       # validate proto style
buf breaking --against '.git#branch=main'   # check for breaking changes
buf generate                   # regenerate frontend TS clients into frontend/src/gen/
```

## Module-specific notes

- The current `ping.proto` is a smoke-test service. It exists to prove the codegen and gRPC plumbing work end-to-end. Real services (`EmailService`, `TransactionService`, `ContactService`, `DashboardService`, `OnboardingService` per `docs/architecture.md` § API) replace and extend the pattern.
- Backend stubs are consumed by `:server` as a regular Gradle dependency (`implementation(project(":proto"))`).
- Frontend stubs land in `frontend/src/gen/` — that directory is gitignored regen output; never hand-edit.
- `buf.yaml` disables `PACKAGE_VERSION_SUFFIX` (Buf's default flags `v1` itself as a smell; we keep `v1` for SaaS-readable versioning).
- See `docs/architecture.md` § API for the planned service shape and `docs/claude/proto.md` for naming, versioning, and breaking-change rules.
