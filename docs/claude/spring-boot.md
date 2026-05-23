# Spring Boot Conventions

Operational guidance for the backend. Strategy lives in `architecture.md` § Backend (locked-in defaults, layered DTOs, Kotlin extension-function mappers, RLS, three Postgres roles, two-layer validation). This file = how to write code that respects those decisions.

Stack: Spring Boot 4 + Kotlin 2.2 + JDK 21+, plain HTTP/JSON via Spring MVC `@RestController`s + Jackson, JPA/Hibernate, Liquibase (Groovy DSL — see `liquibase.md`), JUnit 5 + MockK + AssertK + Testcontainers.

## Layer boundaries

Controller → Service → DAO → Repository → Database. Strategic detail in `architecture.md` § Layered architecture. Operational rule: a service or controller that imports an `*Entity` class is broken layering — entities never leave the DAO. (Kotlin `internal` can't enforce this with the current single-module layout; it's a review-time check.)

## Controller (HTTP)

- `*Controller` per feature, `@RestController`, `@RequestMapping("/api/<resource>")`, constructor-injected service.
- Request/response DTOs are Kotlin `data class`es co-located with the controller. Jackson handles (de)serialization automatically.
- `@Valid` on the request DTO + Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@Email`, etc.) on its fields. Shape validation only — no DB queries.
- Convert request DTO → service DTO via the api-mapper extension, call service, convert response.
- Service exceptions propagate; a `@RestControllerAdvice` maps domain exceptions → HTTP status codes (`NotFoundException` → 404, `ValidationException` → 400, `PermissionDeniedException` → 403, `UnauthenticatedException` → 401). Don't catch in the controller.
- Error response body: `{ code: string, message: string, details?: object }`. Frontend reads `code` for branching, `message` for display.

## Service

- `FooService` class, `@Service`. No interface unless there are actually multiple impls.
- `@Transactional` on write methods; `@Transactional(readOnly = true)` for multi-DAO reads.
- Works in service DTOs only. Never imports entities or controller-layer DTOs. Never imports `api/.*Controller`.
- Cross-feature work goes through the other feature's service, not directly to its DAO.
- Business + DB-level validation (uniqueness, FK existence, invariants). Throws domain exceptions.

## DAO — when justified

A dedicated `*Dao` class (`@Component`, constructor-injected with the repository) is justified by **complex queries, multi-step orchestration, projection/aggregation, or non-trivial entity ↔ DTO mapping**. For 1:1 CRUD-on-a-repository, the service can use the repository directly and call the mapper extension inline — don't add the indirection just to honor the diagram.

When a DAO exists: `@Transactional` on write methods (belt + suspenders), entities never leave. Use `repository.findByIdOrNull(id) ?: throw NotFoundException(...)` for lookups; `getReferenceById(id)` for FK assignments without loading.

**Why `@Component`, not `@Repository`**: `@Repository` exists to enable Spring's persistence-exception translation. That translation is needed at the layer that *directly* throws JPA exceptions — i.e., the Spring Data interface (`PingRepository : JpaRepository<...>`), which is already annotated. The DAO sits one level above, calling the repository; it doesn't throw raw JPA exceptions itself, so adding `@Repository` here is misleading. Plain `@Component`.

## Repository

```kotlin
interface FooRepository : JpaRepository<Foo, UUID> {
    // RLS auto-filters by tenant.
    fun findByName(name: String): List<Foo>
}
```

Thin. Derived query methods + `@Query` for one-off SQL. Complex queries → DAO.

## Multi-tenancy / RLS — code-level rules

Strategy in `architecture.md` § Multi-tenancy. In code:

- Never write `WHERE tenant_id = ?` — RLS filters automatically once `app.tenant_id` is set on the session.
- Cross-tenant work goes through an explicit `withAdminConnection { ... }` block on the `tco_admin` pool. Grep-able, reviewable, rare.
- Per-tenant background jobs `SET LOCAL app.tenant_id` before any DB work. A wrapper utility makes this automatic; forgetting it shows up as queries returning empty under RLS.

## Naming

Each feature has one shape per layer, named without redundant suffixes:

| Layer | Type | Example |
|---|---|---|
| API request | `FooRequest` | `PingRequest` |
| API response | `FooResponse` | `PingResponse` |
| Service / DAO DTO | `FooDto` | `PingDto` |
| Entity | `Foo` | `Ping` (`@Entity` + `@Table` define it; the suffix would be redundant) |

**Primary keys are `UUID`, always.** Liquibase column type `UUID` with `defaultValueComputed: 'gen_random_uuid()'` (see `liquibase.md` § Tenant-scoped table template). Hibernate entity uses `@GeneratedValue(strategy = GenerationType.UUID)` so the id is set client-side at flush time; the DB default is belt-and-suspenders for raw SQL inserts (fixtures, ops). No `BIGSERIAL`, no `Long` primary keys — UUIDs are non-sequential (no contention on monotonic inserts), tenant-collision-safe across shards if we ever scale out, and serialize uniformly as strings on the JSON wire.

For features where service request and service response need genuinely different shapes (rare for v0 CRUD; common for search/projection endpoints later), split into `FooDto` + `FooSummaryDto` or similar — but default to a single `FooDto` that carries everything the layer needs, with nullable fields for "not yet set" values like an unsaved `id`.

## Mappers — Kotlin extension functions

Two boundary mappings exist: **request/response ↔ DTO** at the controller, and **entity ↔ DTO** at the DAO. Both are top-level Kotlin extension functions, co-located with the target type. Three function names, used consistently across every feature: `toDto`, `toEntity`, `toResponse`.

```kotlin
// feature/foo/api/FooController.kt (or a sibling FooApiMapper.kt)
internal fun FooRequest.toDto(now: Instant = Instant.now()): FooDto =
    FooDto(message = message, receivedAt = now)

internal fun FooDto.toResponse(): FooResponse =
    FooResponse(echo = message, serverReceivedAt = receivedAt.toString(), id = requireNotNull(id) { ... })
```

```kotlin
// feature/foo/persistence/FooEntityMapper.kt
internal fun FooDto.toEntity(): Foo =
    Foo(message = message, receivedAt = receivedAt)

internal fun Foo.toDto(): FooDto =
    FooDto(
        id = requireNotNull(id) { "Foo must have an id when mapping to FooDto" },
        message = message,
        receivedAt = receivedAt,
    )
```

- Files: api-side mappers can live in the controller file (small) or `FooApiMapper.kt`; persistence-side in `FooEntityMapper.kt`.
- `internal` visibility — not Spring beans; called directly, no injected mapper field.
- Audit fields (`tenantId`, `createdAt`, `updatedAt`) are populated by JPA lifecycle hooks (`@PrePersist`/`@PreUpdate` on a `BaseEntity`, or `AuditingEntityListener` once wired). The mapper never writes them.
- For updates, take the ID as a separate service-method parameter (typically from `@PathVariable`), not from the request body.
- `requireNotNull(entity.id) { "..." }` when mapping a saved entity — loud, not silent.
- If a mapping balloons past ~50 fields, the *types* are wrong. Split.

## Testing

- MockK only for direct dependencies. Pure functions (mappers, value objects, validation helpers) use the real implementation — mocking a mapper hides whether the mapping is correct.
- Real DTOs, not `mockk()` versions.
- DAO + integration tests run against Testcontainers Postgres with RLS enabled. **Tests must `SET LOCAL app.tenant_id = ...`** before any tenant-scoped read or write — that's the cross-tenant bug catcher.
- AssertK chains over JUnit `assertEquals`.

## Configuration

Three profiles only: `local`, `unraid`, `prod`. Profile selection drives DB connection / secret source / OAuth client ID. Secrets come from env vars / Secret Manager / `.env` — never checked in. Adding a fourth profile needs a real architectural reason.

## Anti-patterns

- Returning entities from controllers or services.
- Skipping a layer (controller → DAO, service → repository).
- Manual `WHERE tenant_id = ?` filters — RLS does it.
- Catching exceptions in the controller — let the advisor map them.
- MapStruct, ModelMapper, or any annotation-processor mapping framework (see Mappers).
- Mixing api-layer DTOs with service DTOs in service signatures.
- `Optional` in method parameters; `@Autowired` field injection.
