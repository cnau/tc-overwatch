# Protobuf & gRPC Conventions

Shared guidance for the proto-first API. Imported by `proto/CLAUDE.md` and `server/CLAUDE.md` (for the controller side).

## Tech baseline

- `.proto` files in `proto/src/main/proto/com/tcoverwatch/<package>/` — single source of truth.
- **Backend stubs** (Kotlin/Java) come from the Gradle protobuf plugin in `proto/build.gradle.kts`. The `:proto` module is a regular Gradle dependency of `:server`.
- **Frontend stubs** (TypeScript Connect-ES + Connect client) come from Buf (`buf generate`) — config in `buf.gen.yaml`, output to `frontend/src/gen/`.
- **Buf** governs linting and breaking-change detection — config in `buf.yaml`. Lint uses the `STANDARD` rule set (one exception: `PACKAGE_VERSION_SUFFIX` is disabled because we already use `v1`).

## Why proto-first

The proto contract is the explicit boundary between backend and frontend, which deploy independently (`docs/architecture.md` § Frontend deployment). A change that compiles on the backend but breaks the frontend client *should* fail in CI before anything ships — that's what Buf's breaking-change check is for. Treat `.proto` edits as contract changes, not implementation details.

## Commands

```
buf lint                 # validate proto style
buf breaking --against '.git#branch=main'   # detect breaking changes vs. main
buf generate             # regenerate frontend TS clients into frontend/src/gen/
./gradlew :proto:build   # regenerate backend Kotlin/Java stubs
```

## Naming and layout

- Package: `com.tcoverwatch.<feature>.v<N>` (e.g. `com.tcoverwatch.email.v1`). The `v<N>` segment is the API version — bump only on a backwards-incompatible reshape, never for additive changes.
- File: one file per service, named after the service in lower_snake_case (`email_service.proto`).
- `option java_package = "com.tcoverwatch.<feature>.v<N>";` and `option java_multiple_files = true;` at the top of every file.
- Service name: `<Domain>Service` (e.g. `EmailService`, `ContactService`, `OnboardingService`). Matches the planned services in `architecture.md` § API.
- Request / response messages: `<Method>Request` / `<Method>Response`. One per RPC; don't share request types across RPCs even if they look similar today — divergence is the common case and shared types make breaking-change detection less useful.

## Field rules

- **Numbers are sacred.** Once a field number is used, never reuse it for a different field — even if the original field is removed. Mark removed fields with `reserved N;` (and optionally `reserved "old_name";`).
- **Optional vs. proto3 defaults**: prefer explicit `optional` when distinguishing "unset" from "default value" matters (a `string` field defaults to `""`; you can't tell from the wire alone whether the sender intended empty or left it unset). Use `optional` for nullable fields. Skip `optional` when the default genuinely means "no value provided" (e.g. an integer count).
- **Wrappers** (`google.protobuf.StringValue`, etc.) — avoid in favor of `optional`. Wrappers are a legacy proto3 escape hatch with worse ergonomics.
- **Enums**: every enum has a `UNSPECIFIED = 0` value (Buf enforces this). Treat `UNSPECIFIED` as "sender didn't set this" and handle it explicitly.
- **Timestamps**: `google.protobuf.Timestamp`. Don't pass timestamps as strings on the wire — the wire format is the canonical form.
- **IDs**: `string` for UUIDs (the proto wire type for stringified UUIDs); the backend service-DTO can use Kotlin's `java.util.UUID`. Mapper converts at the boundary.

## Breaking changes

- **Additive** (new field, new RPC, new message) — safe; doesn't break the wire.
- **Renaming** a field or RPC — breaking. Add the new one, deprecate the old with a comment, remove only after both backend and frontend have moved.
- **Changing a field's type or number** — breaking. Reserve the old number, add a new field with a new number.
- **Removing a field** — soft-breaking. Mark with `reserved` and remove from code paths; clients on old generated code will silently lose data, which is usually fine when the field is no longer populated.
- Buf's `breaking --against '.git#branch=main'` is the source of truth. Fix what it reports.

## Comments

- Every service, RPC, message, and field carries a comment. The proto file is the public contract; the comments are the spec.
- Comment the **intent** ("the address the email belongs to, normalized to full form"), not the **type** ("a string field").
- For RPCs, describe error conditions and what status codes are returned for them.

## Anti-patterns to avoid

- Sharing one giant request message across many RPCs ("flexible" → ambiguous).
- Encoding business state as a `map<string, string>` to avoid a schema update — write the fields.
- `bytes` fields without a documented format (it's just a blob — say what the blob is).
- Sending JSON over a `string` field. If you need structured data, model it in proto.
- Versioning by renaming (`EmailServiceV2`) instead of bumping the package version — Buf detects this poorly.
