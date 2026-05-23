# Protobuf & gRPC Conventions

Proto-first: `.proto` files in `proto/src/main/proto/com/tcoverwatch/<package>/` are the contract. Backend Kotlin/Java stubs come from the Gradle protobuf plugin (`:proto` is a Gradle dep of `:server`). Frontend TS stubs come from `buf generate` into `frontend/src/gen/` using:

- `buf.build/bufbuild/es` — message types
- `buf.build/connectrpc/es` — Connect client classes
- `buf.build/connectrpc/query-es` — TanStack Query hooks per RPC (add when Connect-Query lands, see `react.md`)

Buf governs lint + breaking-change. `STANDARD` lint, `PACKAGE_VERSION_SUFFIX` disabled (we keep `v1`).

Treat `.proto` edits as contract changes — backend and frontend deploy independently (`architecture.md` § Frontend deployment); Buf's breaking-change check is what stops a frontend-breaking proto from shipping.

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

## Errors

Connect uses gRPC status codes on the wire — clients receive them as a `ConnectError` with a `Code` enum value (`unauthenticated`, `permission_denied`, `not_found`, `invalid_argument`, `failed_precondition`, etc.).

- **Backend**: throw domain exceptions; the gRPC controller advisor maps them to specific `Status` codes. Don't return error-as-payload (`oneof { result, error }`) for predictable failures — use Connect's error channel.
- **Status code conventions**:
  - `INVALID_ARGUMENT` — request shape valid but values don't pass business rules.
  - `NOT_FOUND` — resource lookup miss.
  - `ALREADY_EXISTS` — uniqueness violation.
  - `FAILED_PRECONDITION` — request is invalid in the current state (closed transaction, expired invite).
  - `PERMISSION_DENIED` — authenticated but lacks rights.
  - `UNAUTHENTICATED` — no/invalid session.
- **Frontend**: catch `ConnectError`, switch on `code`. Don't parse error messages — codes are the contract.
- **Error details**: when a structured payload is needed beyond a code + message, attach a Connect error detail message defined in proto. This is the proper escape hatch — string parsing is not.

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
