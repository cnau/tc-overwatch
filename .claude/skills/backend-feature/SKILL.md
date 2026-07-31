---
name: backend-feature
description: Use when writing or changing Kotlin backend code in server/ — adding a @RestController, service, DAO, entity, or repository; naming request/response/DTO types; writing boundary mappers; wiring RLS or tenant context; adding a Spring Security filter chain; or writing backend tests. Covers the layer boundaries, the feature-by-package layout, and the springdoc/OpenAPI gotchas that shape frontend types.
---

# Backend feature work

Layering strategy and the reasoning behind these choices live in `docs/architecture.md`
§ Backend. This is how to write code that respects them.

## Layer boundaries

Controller → Service → DAO → Repository → Database. Never skip a layer.

**A controller never touches a Hibernate entity** — not as a parameter, return type, local, or
import. Controllers consume services (which expose DTOs) and produce response DTOs.

Services may hold an entity locally inside a `@Transactional` method that orchestrates
persistence — `AuthService` does this — but **service public method signatures never use
entities**. The boundary is the method signature.

Kotlin `internal` can't enforce this: a public DAO can't take `internal` entity or repository
types as constructor parameters, so those stay public and the rule is a review-time check.
Mapper extensions themselves can be `internal` — they aren't Spring beans.

## Feature layout

Each feature is `feature/<name>/` with `api/`, `service/`, optional `dao/`, and `persistence/`.
`feature/ping/` is the complete worked example — read it rather than guessing. It is a scaffold
smoke test, so copy its *shape*, not its content.

- **Request and response DTOs share one `<Feature>Contracts.kt`** — tightly coupled, small.
- **Each service-layer DTO gets its own file** (`AppUserDto.kt`, `SignInResult.kt`) — standalone
  types, often referenced from several call sites.
- **Mapper extensions live in the file of the class that calls them**, never a sibling
  `*Mapper.kt`. `toDto`/`toResponse` → the controller file. `toEntity`/`toDto` → the DAO file.
- `persistence/` holds only the JPA entity and the Spring Data repository.

## Naming

One shape per layer, no redundant suffixes: `FooRequest`, `FooResponse`, `FooDto`, and the
entity is just `Foo` (`@Entity` + `@Table` already say what it is). These simple names become
the schema names in `/v3/api-docs` — do **not** rename to `*ApiRequest` / `*ApiResponse`.

**Primary keys are `UUID`, always.** Entity uses `@GeneratedValue(strategy = GenerationType.UUID)`
so the id is set client-side at flush; the column default is belt-and-suspenders for raw SQL
inserts. No `BIGSERIAL`, no `Long` keys — UUIDs avoid monotonic-insert contention, stay
collision-safe if we ever shard by tenant, and serialize uniformly on the JSON wire.

Default to a single `FooDto` carrying everything the layer needs, with nullable fields for
not-yet-set values like an unsaved `id`. Split into `FooDto` + `FooSummaryDto` only when
request and response shapes genuinely diverge (search/projection endpoints, later).

## Controller

- `@RestController`, `@RequestMapping("/api/<resource>")`, constructor-injected service.
- `@Valid` on the request DTO with Jakarta annotations on its fields. **Shape validation only** —
  no DB queries here.
- **Return the response DTO directly. Never wrap in `ResponseEntity`.** Declare the status with
  `@ResponseStatus(HttpStatus.X)` on the method — including `OK` for success endpoints. For
  header or cookie side effects, inject `HttpServletResponse` as a parameter. The status is then
  visible at the signature and the response shape is just the DTO.
- **Never catch exceptions in a controller.** Let them propagate;
  `common/api/ApiErrorAdvice.kt` maps every `DomainException` subclass to a status and emits the
  `{ code, message, details? }` envelope. Read that file for the current mapping rather than
  trusting a copy of it — it also handles `@Valid` failures, unparseable bodies, method and
  media-type mismatches, and unhandled exceptions (500, generic message, logged with stack).
- Read the principal via `@AuthenticationPrincipal principal: AuthenticatedPrincipal?` — never
  reach into `SecurityContextHolder`.

## Service

- `@Service` class, no interface unless there are genuinely multiple implementations.
- `@Transactional` on writes, `@Transactional(readOnly = true)` for multi-DAO reads.
- Service DTOs only — never imports entities or api-layer types.
- Cross-feature work goes through the other feature's **service**, never its DAO.
- Owns business and DB-level validation (uniqueness, FK existence, invariants) and throws
  domain exceptions.

## DAO — only when justified

A `*Dao` is justified by complex queries, multi-step orchestration, projection/aggregation, or
non-trivial entity↔DTO mapping. **For 1:1 CRUD, the service uses the repository directly** and
calls the mapper inline — don't add indirection to honor the diagram. `feature/auth` has no DAO
for exactly this reason; `feature/ping` has one.

`@Component`, **not** `@Repository`: `@Repository` exists to enable persistence-exception
translation, which is needed at the layer that directly throws JPA exceptions — the Spring Data
interface, already annotated. A DAO sits above that and doesn't throw raw JPA exceptions, so
`@Repository` there is misleading.

Use `repository.findByIdOrNull(id) ?: throw NotFoundException(…)` for lookups, and
`getReferenceById(id)` for FK assignment without loading.

## Mappers — Kotlin extension functions

Top-level `internal` extension functions. Exactly three names, everywhere: `toDto`, `toEntity`,
`toResponse`. **No MapStruct, ModelMapper, or any annotation-processor mapping framework** — no
`kapt` pipeline in this build. See `feature/ping/api/PingController.kt` and
`feature/ping/dao/PingDao.kt` for both directions.

- Audit fields (`tenantId`, `createdAt`, `updatedAt`) are populated by JPA lifecycle hooks. A
  mapper never writes them.
- For updates, take the id as a separate service-method parameter (from `@PathVariable`), not
  from the request body.
- `requireNotNull(entity.id) { "…" }` when mapping a saved entity — fail loudly, not silently.
- A mapping ballooning past ~50 fields means the *types* are wrong. Split.

## Multi-tenancy / RLS

- **Never write `WHERE tenant_id = ?`.** RLS filters automatically once `app.tenant_id` is set.
- `common/multitenancy/TenantBindingAspect` sets it for you. It fires *inside* every
  `@Transactional` method — Spring's transactional advisor is pinned to `order = 0` in
  `MultiTenancyConfig` and the aspect is `@Order(1)`, so it wraps inside the transaction. It
  reads `AuthenticatedPrincipal.tenantId` from the `SecurityContext` and calls
  `set_config('app.tenant_id', tenantId, true)`. No principal → no-op, so the auth-gate path
  still works.
- **So: just write normal `@Transactional` methods with tenant-scoped queries.** Don't call
  `set_config` by hand unless the operation is genuinely cross-tenant.
- **Pointcut limitation**: `@Transactional` on a Spring Data repository method is only matched
  when the call arrives through a Spring-managed service. A direct controller→repository call
  bypasses the aspect entirely — another reason the layering rule is load-bearing.
- **Cross-tenant lookups go through a `SECURITY DEFINER` Postgres function.**
  `tco.find_app_user_by_email` (in `fn-auth-lookups.sql`) is the precedent: owned by
  `tco_migrate`, bypasses RLS, `search_path` locked to `tco, pg_temp`. Follow it for any future
  cross-tenant access.
- Per-tenant background jobs have no `SecurityContext`, so they must `SET LOCAL app.tenant_id`
  themselves before any DB work. A `withTenantContext(tenantId) { … }` helper lands with the
  first such job.

## Security

Three `SecurityFilterChain` beans with non-overlapping matchers; ordering is for clarity.

| Bean | Location | Gate | Handles |
|---|---|---|---|
| `OAuthSecurityConfig` | `feature/auth/api/` | `@ConditionalOnBean(ClientRegistrationRepository)`, `@Order(0)` | `/oauth2/authorization/**`, `/login/oauth2/code/**` |
| `DevSecurityConfig` | `feature/auth/api/` | `@Profile("local")`, `HIGHEST_PRECEDENCE` | `POST /api/dev/invitations` |
| `SecurityConfig` | `common/security/` | none (no matcher) | everything else |

The `ClientRegistrationRepository` that gates the OAuth chain is itself supplied by
`GoogleClientRegistrationConfig`, which is `@ConditionalOnExpression` on a non-empty
`GOOGLE_CLIENT_ID` — so an unset client id makes the whole OAuth chain vanish rather than fail
at startup. `CorsConfig` (also in `common/security/`) holds the cross-origin allowlist.

- `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, verifies via `JwtService`, and
  populates the context with `AuthenticatedPrincipal(email, userId?, tenantId?)`.
- **Stateless — no cookies, no server-side sessions.** The OAuth callback returns the JWT in the
  redirect URL fragment; logout is a 204 no-op and the client discards the token. A revocation
  list lands when there's a real reason for one.
- The OAuth success handler is **IdP-agnostic**: it extracts the verified email through the OIDC
  `OidcUser` interface, so Microsoft/Apple/Okta/Auth0 need only a
  `spring.security.oauth2.client.registration.*` block plus an SPA button. A non-OIDC provider
  (GitHub) would extend the email resolver at the explicit seam in the handler.
- 401/403 from the main chain delegates to Spring MVC's `HandlerExceptionResolver` so responses
  route through `ApiErrorAdvice` and match every other error envelope. **Never serialize error
  JSON inside the security layer.**
- **Profile-gated or condition-gated security beans get their own file** — never mix conditional
  and unconditional `SecurityFilterChain` beans in one file. Different lifetimes, different files.

## OpenAPI — two gotchas that shape frontend code

springdoc walks every `@RestController` and picks up Jackson, Bean Validation, and Kotlin types
automatically. No annotations needed on existing code. Two known gaps — **don't fix
preemptively**:

- **Response fields render as optional even when the Kotlin type is non-null.** springdoc 3.0.3
  doesn't mark Kotlin non-null `val`s as required on responses, so generated TS types are wider
  than reality. Live with it; add `@field:Schema(requiredMode = REQUIRED)` only where a specific
  call site forces it.
- **Field-level KDoc does not become an OpenAPI `description`.** springdoc doesn't read KDoc. Use
  `@field:Schema(description = "…")` if a field genuinely needs prose. Default: trust
  well-named fields, don't paper the spec with annotations.

## Comments

**Default to no comments; when you write one, 2 lines or less.** If it needs three, the code is
the wrong shape — fix the code, or lift the explanation into this skill. **Never restate a
project rule in a code comment** — the rule docs are the source of truth, and a duplicated rule
drifts. A comment earns its place only when the *why* is non-obvious: a hidden constraint, a
workaround for a specific bug, an invariant a reader would otherwise miss. Don't explain what
the code does; identifiers do that.

## Kotlin specifics that differ from defaults

Generic Kotlin style is enforced by ktlint — don't spend judgment there. These are the
repo-specific calls:

- **`-Xjsr305=strict` is on.** Platform types from Java are treated as non-null.
- **`value class` for identifiers that would be bugs if swapped** —
  `value class TenantId(val value: UUID)` makes tenant id a distinct compile-time type at zero
  runtime cost. Use sparingly; only where passing the wrong UUID is a real hazard.
- **Sealed types for expected alternative outcomes** (`SignInResult`), exceptions for genuine
  errors. `when` over a sealed type used as an expression gets compiler-enforced exhaustiveness.
- **`Result<T>` / `runCatching` only at external boundaries** (parsing, remote calls). Inside the
  service layer, exceptions are clearer.
- **No `kotlinx.coroutines`.** v0 is uniformly blocking (Spring MVC → service → JDBC); mixing
  suspending controllers with blocking JPA creates surprise edges. Surface the need explicitly
  if a real fan-out appears.
- **No `!!` in production code**, no top-level mutable state, no `Any?` to dodge type design.

## Testing

**No backend tests exist yet** — `server/src/test` is empty. JUnit 5, MockK, AssertK, and
Testcontainers are all wired in `server/build.gradle.kts`, so the first test author sets the
precedent for everything after. The conventions below are decided, just unexercised:

- **MockK only for direct dependencies.** Pure functions — mappers, value objects, validation
  helpers — use the real implementation. Mocking a mapper hides whether the mapping is correct.
- **Real DTOs, never `mockk()` versions.**
- **DAO and integration tests run against Testcontainers Postgres with RLS enabled, and must
  `SET LOCAL app.tenant_id = …` before any tenant-scoped read or write.** This is the
  cross-tenant bug catcher — a test that skips it is testing nothing about isolation.
- AssertK chains over JUnit `assertEquals`.

Also note **detekt is task-disabled** in `server/build.gradle.kts` (detekt 1.23 embeds Kotlin
2.0, incompatible with 2.2). CI's `detekt` call is a no-op; only ktlint actually runs.
