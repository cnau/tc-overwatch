# Spring Boot Conventions

Shared backend guidance. Imported by `server/CLAUDE.md`. The *strategic* architecture (why layered DTOs, why MapStruct at boundaries, why RLS day one, why three Postgres roles, why separate frontend deploy) is pinned in `docs/architecture.md`. This file is the operational complement: how to write code that respects those decisions.

## Tech baseline

- Spring Boot 4.0.x, Spring Framework 7
- Kotlin 2.2.x, JDK 21+ (23 locally per scaffold notes)
- gRPC + Connect via `net.devh:grpc-server-spring-boot-starter`
- Spring Data JPA + Hibernate, MapStruct for boundary conversions
- Liquibase (Groovy DSL) for schema migrations — see `liquibase.md`
- JUnit 5 + MockK + AssertK + Testcontainers (Postgres)

See `docs/architecture.md` § Backend for the locked-in defaults (ktlint + detekt, structured JSON logging, package-by-feature, `@Transactional` at service *and* DAO, two-layer validation).

## Respect architecture boundaries

Never skip layers. Controllers must not call DAOs or repositories directly. Services must not call another feature's DAO. Each layer has one job:

```
Controller (gRPC) ─► Service ─► DAO ─► Repository ─► Database
   ▲                   ▲          ▲
   proto ↔ DTO         |          entity ↔ DTO
   (api mapper)        |          (dao mapper)
                       service DTOs only
```

**Three distinct type families** flow through the layers:

- **Proto messages** — wire format; live next to the gRPC controller (`feature/<name>/api/`).
- **Service DTOs** — Kotlin data classes; the lingua franca between controller, service, and DAO (`feature/<name>/service/`).
- **JPA entities** — persistence model; **never escape the DAO layer**.

Kotlin can't enforce the "entities don't escape the DAO" rule via `internal` visibility while DAOs are also public (see scaffold notes in `architecture.md`). It's enforced by code review — if a service or controller imports an `*Entity` class, the layering is wrong.

## Controller pattern (gRPC)

- One `*RpcController` class per service, annotated with `@GrpcService`.
- Constructor-inject the service + a `*ProtoMapper`.
- Convert request proto → service DTO via the mapper, call the service, convert returned service DTO → response proto via the mapper.
- **Bean Validation (`@Valid`)** is applied to the converted *service DTO*, not the proto. Shape validation only (required, format, length, regex) — no DB queries from the controller.
- Domain exceptions thrown by the service are caught by a controller-side advisor and mapped to gRPC status codes. The controller itself doesn't catch service exceptions.

HTTP endpoints are deliberately small in scope (`/oauth/callback`, `/api/auth/*`, `/actuator/health`) per `architecture.md` § HTTP surface — use Spring MVC `@RestController` for those, but they remain the exception, not the rule.

## Service pattern

- Class-per-feature: `FooService` (no interface unless multiple implementations actually exist).
- `@Service` on the class.
- `@Transactional` on write methods; `@Transactional(readOnly = true)` on read methods that span multiple DAO calls.
- Works with service DTOs — never imports entities or proto types.
- Services may compose multiple DAOs; they may not call another feature's DAO directly. Cross-feature work goes through that feature's service.
- Business-rule and DB-level validation lives here (uniqueness checks, FK existence, cross-entity invariants). Throw domain exceptions that the controller advisor maps.
- **No imports from `api/` packages** — if you see `import ...api.*Controller` in a service, the layering is wrong.

## DAO pattern

- Class-per-entity: `FooDao`. Concrete class, constructor-injected with the repository + entity mapper.
- DAOs are `@Component` (or `@Repository` for exception translation) — they're not interfaces unless multiple implementations actually exist.
- `@Transactional` on write methods as a belt-and-suspenders against accidental call paths that skip the service.
- DAO mapper (MapStruct) converts entity ↔ service DTO at the boundary.
- Use `findByIdOrThrow(id)` (or extension) over `findById(id).orElseThrow(...)` for the common lookup.
- Entities never leave the DAO. Method signatures take and return service DTOs.

## Repository pattern

```kotlin
interface FooRepository : JpaRepository<FooEntity, UUID> {
    // RLS automatically filters by tenant — no explicit tenant_id clauses in queries.
    fun findByName(name: String): List<FooEntity>
}
```

Keep repositories thin: derived query methods, `@Query` for one-off SQL. Complex multi-step queries belong in the DAO.

## Multi-tenancy and RLS

The full design is in `docs/architecture.md` § Multi-tenancy. Operational rules in code:

- **Every tenant-scoped table** has `tenant_id UUID NOT NULL` and an RLS policy declared in the same Liquibase changeset (see `liquibase.md`).
- **Never write `WHERE tenant_id = ?`** in application code. RLS filters automatically once `app.tenant_id` is set on the session.
- **Never bypass RLS** in application code paths. The `tco_app` role doesn't have `BYPASSRLS`; you can't accidentally cross tenants.
- **Cross-tenant operations** (tenant provisioning, admin RPCs, system-wide jobs) go through an explicit `withAdminConnection { ... }` block that uses the `tco_admin` pool. These call sites must be grep-able, reviewable, and rare.
- **Per-tenant background jobs** use the `tco_app` pool and the worker `SET LOCAL app.tenant_id` to the job's tenant before any DB work. A wrapper utility makes this automatic; forgetting it surfaces as obvious test failures (queries return empty under RLS).

## Mappers (MapStruct)

- **Proto mapper** (`*ProtoMapper`) — lives in the controller's `api/` package. Maps proto request → service DTO and service DTO → proto response. Always ignore audit fields (`id` on create, `tenantId`, `createdAt`, `updatedAt`) on request → DTO. For updates, take the ID as a separate parameter.
- **Entity mapper** (`*EntityMapper`) — lives in the DAO's `persistence/` package. Maps entity ↔ service DTO. Audit fields populated by Hibernate listeners, not the mapper.
- Both use `@Mapper(componentModel = "spring")`.
- Hand-rolled mappers are a code smell — reach for one only when the mapping is genuinely non-trivial (e.g. proto builders that surface MapStruct warnings for `mergeFrom` / `clearField`, per the scaffold notes). Document the why in the file.

## Testing

- New services and business logic must have unit tests.
- Test behavior, not implementation — assert on what a method does, not how.
- One concept per test method. Tight, focused tests.
- Mock only direct dependencies. Don't over-mock.
- Don't write tests for trivial code (data class getters, no-op delegations).
- Use `@Spy` (not `@Mock`) for MapStruct mappers in tests.
- Use real DTOs in tests, not mocks.
- DAO and integration tests run against Testcontainers Postgres with the same RLS policies enabled. Tests must explicitly set the tenant context to read/write — that catches cross-tenant bugs at unit-test time.

## Configuration

- `application.yml` — defaults.
- `application-local.yml`, `application-unraid.yml`, `application-prod.yml` — profile overrides only.
- Secrets and env-specific values come from environment variables / Secret Manager / `.env` (per profile). Never check secrets in.
- Don't add a fourth `application-<env>.yml` without a corresponding architectural reason — the three-profile model is intentional.

## Anti-patterns to avoid

- Returning entities from controllers or services. They never leave the DAO.
- Skipping a layer (controller calling DAO, service calling repository).
- Manually filtering by `tenant_id` in code — RLS does it. Manual filters are a sign the writer doesn't trust RLS, which is a smell on its own.
- Catching exceptions in the controller — let the advisor map them.
- Hand-rolled entity-DTO mapping when MapStruct would generate it.
- Mixing proto types and service DTOs in service signatures.
- `Optional` in method parameters.
- `@Autowired` field injection — use constructor injection.
- Reaching for a new library (caching, scheduling, JSON-handling) without checking the existing stack first.

## Greenfield reminders

There is no legacy code yet. The `ping` feature is a smoke-test scaffold, not a pattern to extend — real features replace it and become the patterns to copy. Be deliberate: what you ship today becomes the baseline tomorrow.
