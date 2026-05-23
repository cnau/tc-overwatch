# Spring Boot Conventions

Operational guidance for the backend. Strategy lives in `architecture.md` § Backend (locked-in defaults, layered DTOs, Kotlin extension-function mappers, RLS, three Postgres roles, two-layer validation). This file = how to write code that respects those decisions.

Stack: Spring Boot 4 + Kotlin 2.2 + JDK 21+, gRPC via `net.devh` starter, JPA/Hibernate, Liquibase (Groovy DSL — see `liquibase.md`), JUnit 5 + MockK + AssertK + Testcontainers.

## Layer boundaries

Controller → Service → DAO → Repository → Database. Strategic detail in `architecture.md` § Layered architecture. Operational rule: a service or controller that imports an `*Entity` class is broken layering — entities never leave the DAO. (Kotlin `internal` can't enforce this with the current single-module layout; it's a review-time check.)

## Controller (gRPC)

- `*RpcController` per service, `@GrpcService`, constructor-injected service.
- Convert proto → service DTO via the mapper extension, call service, convert response.
- `@Valid` runs on the *converted service DTO*. Shape validation only — no DB queries.
- Service exceptions propagate; a controller-side advisor maps domain exceptions → gRPC status codes. Don't catch in the controller.
- HTTP endpoints (`/oauth/callback`, `/api/auth/*`, `/actuator/health`) use Spring MVC `@RestController` — see `architecture.md` § HTTP surface. They're the exception, not the rule.

## Service

- `FooService` class, `@Service`. No interface unless there are actually multiple impls.
- `@Transactional` on write methods; `@Transactional(readOnly = true)` for multi-DAO reads.
- Works in service DTOs only. Never imports entities or proto types. Never imports `api/.*Controller`.
- Cross-feature work goes through the other feature's service, not directly to its DAO.
- Business + DB-level validation (uniqueness, FK existence, invariants). Throws domain exceptions.

## DAO — when justified

A dedicated `*Dao` class (`@Repository`, constructor-injected with the repository) is justified by **complex queries, multi-step orchestration, projection/aggregation, or non-trivial entity ↔ DTO mapping**. For 1:1 CRUD-on-a-repository, the service can use the repository directly and call the mapper extension inline — don't add the indirection just to honor the diagram.

When a DAO exists: `@Transactional` on write methods (belt + suspenders), entities never leave. Use `repository.findByIdOrNull(id) ?: throw NotFoundException(...)` for lookups; `getReferenceById(id)` for FK assignments without loading.

## Repository

```kotlin
interface FooRepository : JpaRepository<FooEntity, UUID> {
    // RLS auto-filters by tenant.
    fun findByName(name: String): List<FooEntity>
}
```

Thin. Derived query methods + `@Query` for one-off SQL. Complex queries → DAO.

## Multi-tenancy / RLS — code-level rules

Strategy in `architecture.md` § Multi-tenancy. In code:

- Never write `WHERE tenant_id = ?` — RLS filters automatically once `app.tenant_id` is set on the session.
- Cross-tenant work goes through an explicit `withAdminConnection { ... }` block on the `tco_admin` pool. Grep-able, reviewable, rare.
- Per-tenant background jobs `SET LOCAL app.tenant_id` before any DB work. A wrapper utility makes this automatic; forgetting it shows up as queries returning empty under RLS.

## Mappers — Kotlin extension functions

Two boundary mappings exist: **proto ↔ service DTO** at the controller, and **entity ↔ service DTO** at the DAO. Both are written as top-level Kotlin extension functions co-located with the target type.

```kotlin
// feature/foo/api/FooProtoMapper.kt
internal fun FooRequest.toServiceRequest(now: Instant = Instant.now()): ServiceFooRequest =
    ServiceFooRequest(message = message, receivedAt = now)

internal fun ServiceFooResponse.toProtoResponse(): FooResponse =
    FooResponse.newBuilder()
        .setId(id)
        .setEcho(echo)
        .setServerReceivedAt(receivedAt.toString())
        .build()
```

```kotlin
// feature/foo/persistence/FooEntityMapper.kt
internal fun ServiceFooRequest.toEntity(): FooEntity =
    FooEntity(message = message, receivedAt = receivedAt)

internal fun FooEntity.toServiceResponse(): ServiceFooResponse =
    ServiceFooResponse(
        id = requireNotNull(id) { "FooEntity must have id when mapping to response" },
        echo = message,
        receivedAt = receivedAt,
    )
```

- Naming: `Foo.toX()`. Files: `FooProtoMapper.kt` (api), `FooEntityMapper.kt` (persistence).
- `internal` visibility — not Spring beans; called directly, no injected mapper field.
- Audit fields (`tenantId`, `createdAt`, `updatedAt`) are populated by JPA lifecycle hooks (`@PrePersist`/`@PreUpdate` on a `BaseEntity`, or `AuditingEntityListener` once wired). The mapper never writes them.
- For updates, take the ID as a separate service-method parameter, not from the proto.
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
- Mixing proto types with service DTOs in service signatures.
- `Optional` in method parameters; `@Autowired` field injection.
