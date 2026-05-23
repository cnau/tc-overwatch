# Architecture — tc-overwatch

The implementation stack. Decisions locked here are the result of explicit requirements/UX work in `GOALS.md` and the trade-off discussion captured here. Implementation-level details that aren't strategic (specific library choices, version pins beyond majors) are intentionally deferred and called out below.

## Stack at a glance

| Layer | Choice | Notes |
|---|---|---|
| Build | Gradle 9.5.1 | Kotlin DSL |
| JVM | 21+ | Spring Boot 4 requires it |
| Backend | Spring Boot 4.0.6 + Kotlin | Bleeding edge — watch third-party compat |
| Database | Postgres 18 | `pg_trgm` for fuzzy match, `JSONB` for flexible fields |
| Migrations | Liquibase (Groovy DSL) | Changelogs in `src/main/resources/db/changelog/`; runs via the `migrate` one-shot container as `tco_migrate` |
| API wire format | HTTP / JSON | Spring MVC `@RestController` + Jackson |
| Frontend | React + TypeScript + Vite | TanStack Query + `fetch` |
| Background jobs | Postgres-backed queue (Spring `@Scheduled` + `LISTEN/NOTIFY`) | Upgrade to Temporal only if SaaS scale demands |
| Auth | Google OAuth2 via Spring Security | `gmail.modify` scope; tokens stored server-side encrypted |
| Local dev | Docker Compose (Postgres) + `gradle bootRun` + `vite dev` | |
| Deploy target | GKE Autopilot | Helm chart per environment |
| Managed Postgres | Cloud SQL | via Cloud SQL Auth Proxy sidecar |

## Backend

**Spring Boot 4 + Kotlin** with Gradle (Kotlin DSL).

### Persistence and ORM

- **Spring Data JPA + Hibernate** for persistence.
- **Liquibase (Groovy DSL)** for schema migrations. Changelogs in `src/main/resources/db/changelog/`. Run by a one-shot `migrate` container in the deploy workflow as the `tco_migrate` role, never on app startup.

### Layered architecture

Strict layering: **Controller → Service → DAO → Repository**. Each layer has its own types; types from a lower layer never leak upward.

| Layer | Inputs | Outputs | Notes |
|---|---|---|---|
| **Controller** (Spring MVC `@RestController`) | Request DTOs (Jackson-bound) | Response DTOs (Jackson-bound) | Converts wire-level DTOs ↔ service DTOs |
| **Service** | Service DTOs | Service DTOs | Business logic, multi-step coordination, transactions (`@Transactional`) |
| **DAO** | Service DTOs | Service DTOs | Wraps Spring Data repository; converts service DTOs ↔ JPA entities. **Hibernate entities never escape this layer.** |
| **Repository** | JPA entities | JPA entities | Pure Spring Data JPA interface |

Rationale: the wire format, the business model, and the persistence model evolve at different rates. Coupling them means a DB schema change ripples through the API contract; decoupling them confines change. The cost is more types and more mapping code — paid by **Kotlin extension functions** (see below), not by hand-rolled imperative converters or annotation-processor magic.

### Kotlin extension-function mappers at boundaries

Two boundary mappings exist: **request/response ↔ DTO** at the controller, and **entity ↔ DTO** at the DAO. Both are implemented as **top-level Kotlin extension functions** co-located with the target type. Three canonical function names used across every feature: `toDto`, `toEntity`, `toResponse`.

```kotlin
// feature/foo/api/FooController.kt (or a sibling FooApiMapper.kt for larger features)
internal fun FooRequest.toDto(): FooDto = FooDto(...)
internal fun FooDto.toResponse(): FooResponse = FooResponse(...)

// feature/foo/persistence/FooEntityMapper.kt
internal fun FooDto.toEntity(): Foo = Foo(...)
internal fun Foo.toDto(): FooDto = FooDto(...)
```

Why extension functions, explicitly **not MapStruct**:

- Kotlin already has the language features MapStruct compensates for in Java — named arguments, data-class `copy()`, immutable constructors, null safety in the type system.
- No `kapt` annotation processor in the build → faster incremental compiles, no generated-source surprise, no `@Mapping(expression = "java(...)")` escape hatches.
- The mapping is *the code* — readable in the diff, debuggable like any function, trivially testable without `Mappers.getMapper(...)` boilerplate.
- Extension functions don't need to be Spring beans → the DAO and controller don't carry a mapper field; they just call `dto.toEntity()` / `request.toDto()`.

Discipline: if a mapping is genuinely tedious (50+ fields, multi-source enrichment), it's signalling that the *types* are wrong, not that you need MapStruct — split the DTO or simplify the mapping target.

The naming convention is pinned in `docs/claude/spring-boot.md` § Naming + § Mappers.

### HTTP API surface

The application exposes plain HTTP/JSON endpoints from Spring MVC `@RestController`s. Jackson handles request/response (de)serialization. There is **no gRPC**, no Protobuf wire format, no Connect/Connect-Web layer — the browser-can't-speak-gRPC-trailers constraint made the proto-first path more friction than its (theoretical-for-our-scope) benefits could earn. JSON is what the React SPA can consume natively; one fewer wire-format translation in the stack.

If a future service-to-service caller actually needs a typed RPC contract, revisit then — for v0 / pilot, one single-page-app calling one Spring service is the entire API consumer set.

### Authentication / Security

- **Spring Security** with OAuth2 client for Google sign-in.
- Refresh tokens are encrypted at the application layer with a KEK (from Secret Manager on GCP / `.env` on Unraid) before persisting to Postgres.
- Session = HTTP-only Secure SameSite=Lax cookie holding an opaque session token; backend validates server-side.

### Multi-tenancy in the request pipeline

Single Postgres, every tenant-scoped table carries `tenant_id` (UUID, NOT NULL). **RLS is enforced from day one** — full details in *Multi-tenancy* section below. A `TenantContext` interceptor on every request derives the tenant from the authenticated session and sets `app.tenant_id` on the DB session via `SET LOCAL` at transaction start.

### Locked-in defaults

- **Testing**: JUnit 5 + MockK + Testcontainers (Postgres + any other infra) + AssertK assertions.
- **Code style**: ktlint + detekt, both enforced in CI.
- **Logging**: Logback (Spring Boot default) with a structured JSON encoder for the `prod` / `unraid` profiles.
- **JSON**: Jackson at the API boundary. `@JsonInclude(Include.NON_NULL)` as the default.
- **Package layout**: by-feature (`feature/<name>/api/`, `feature/<name>/service/`, `feature/<name>/persistence/`). Cross-cutting concerns (`config/`, `common/`, `security/`) at the top level.
- **Transactions**: `@Transactional` on service-layer methods *and* DAO-layer methods (belt-and-suspenders against accidental call paths that skip the service).
- **Validation**: two-layer.
  - **Controller boundary**: shape validation only — Bean Validation (Jakarta `@Valid`) on the converted service DTO. Required-field, format, length, regex. *No DB queries here* — controllers must not need persistence to validate.
  - **Service**: business-rule + DB-level validation (uniqueness checks, foreign-key existence, cross-entity invariants). Throws domain exceptions caught by a `@RestControllerAdvice` that maps them to HTTP status codes.

### HTTP surface

The Spring Boot process exposes Spring MVC on port 8080. The surface is small and deliberate:

- **`/oauth/callback`** — Google OAuth redirect target. Spring MVC `@RestController` that exchanges the `code` for tokens, encrypts the refresh token, creates a session, and issues a 302 to the frontend SPA's root URL with the session cookie set.
- **`/api/auth/*`** — any other HTTP-only auth endpoints (logout, session refresh) as needed.
- **`/actuator/health`** — Spring Actuator health endpoint for Docker / K8s liveness/readiness probes.

The backend **does not serve the React SPA**. Frontend and backend are separate codebases and separate deploys (see *Frontend deployment* below).

### Frontend deployment — fully separate from backend

The React frontend has its own build pipeline, its own deploy target, its own URL, and lives in a sibling `frontend/` directory in this repo. It is **never** bundled into the backend container.

Rationale (informed by past pain): tightly coupling frontend assets to backend deploys means every UI tweak requires a full backend redeploy, asset-serving scaling is bound to API scaling, and rollback granularity is coarsened. Separation isn't free (CORS, two deployments, more pipeline) but the benefits dominate quickly.

Where the frontend lives per environment:

- **Local dev**: Vite dev server (`localhost:5173`) with a **Vite proxy** forwarding `/api/*` and `/oauth/*` to the backend on `localhost:8080`. Same-origin in the browser during dev — no CORS needed locally.
- **Unraid pilot**: separate `nginx`-based container hosting the static build, on its own Cloudflare Tunnel hostname (e.g. `app.example.com` for the frontend, `api.example.com` for the backend). Each tunnel hostname is independent; either can be deployed without touching the other.
- **GCP production**: frontend built and pushed to **Cloud Storage + Cloud CDN** (or a small Cloud Run static container — pick later). Backend on GKE. Two distinct deploy pipelines.

### CORS and cross-origin sessions

Because frontend and backend live on different origins, CORS rules and cross-origin session cookies are part of day-one config:

- **Backend CORS allowlist** (Spring Security) is per-profile:
  - `local`: `http://localhost:5173` (Vite dev) — though the Vite proxy makes this rarely hit
  - `unraid`: the public frontend hostname (e.g. `https://app.example.com`)
  - `prod`: production frontend hostname(s)
- **`Access-Control-Allow-Credentials: true`** on backend responses so cookies flow.
- **Session cookie attributes**: `HttpOnly; Secure; SameSite=None; Domain=.example.com` so the browser sends the cookie on cross-origin requests within the registered parent domain. Frontend and backend hostnames **must share a parent domain** (e.g. `app.example.com` + `api.example.com` both under `.example.com`).
- **`fetch` calls** use `credentials: 'include'` so the browser attaches cookies on every API call.
- **CSRF**: `SameSite=None; Secure` cookies + a custom `Content-Type: application/json` (browsers won't send cross-origin without preflight) + an explicit origin check on the OAuth callback is sufficient for v0. If we ever need stricter posture, switching from session cookies to **Bearer tokens in the `Authorization` header** is a clean upgrade.

### Why not Bearer tokens from day one?

Bearer tokens (issued at OAuth-callback time, stored in frontend memory, sent on every API call) avoid cross-origin cookie semantics entirely and are arguably more standard for SaaS APIs. Trade-off: storing the token safely in the frontend (memory-only, never localStorage; refresh-on-load via a short-lived "is the session still good?" call) adds frontend complexity that cookie-based sessions don't need.

Cookies win for v0 because the auth complexity is centralized in the backend and the parent-domain requirement is a one-time DNS decision. Revisit if SaaS goes mobile (where bearer tokens are friendlier) or if a security review demands it.

## API — HTTP / JSON

Plain HTTP + JSON via Spring MVC `@RestController`s and Jackson. The browser SPA consumes the API directly with `fetch` (wrapped in typed helpers under `frontend/src/api/<domain>.ts`).

- **Wire format**: JSON request + JSON response. `Content-Type: application/json`. No proto, no gRPC, no Connect, no codegen.
- **URL convention**: `/api/<resource>` for collection-style endpoints, `/api/<resource>/<id>` for items, `/api/<resource>/<id>/<action>` for action endpoints. Hyphenate multi-word resources (`/api/known-contacts`). Use `POST` for any state change (we're not REST-purists about PUT vs PATCH for the v0 surface).
- **Request/Response DTOs**: Kotlin `data class`es co-located with the controller (`feature/<name>/api/`). Jackson auto-serializes. Bean Validation annotations (`@NotBlank`, `@Size`, `@Email`, etc.) on the request DTO; validated via `@Valid`.
- **Errors**: a `@RestControllerAdvice` maps domain exceptions to HTTP status codes (`NotFoundException` → 404; `ValidationException` → 400; `PermissionDeniedException` → 403; `UnauthenticatedException` → 401). The error response body is a small JSON shape (`{ code, message, details? }`) for the frontend to render.
- **Types stay in sync by discipline**, not by codegen: the request/response DTOs on the server and the matching TypeScript types in `frontend/src/api/<domain>.ts` are kept aligned by review. For v0's small surface, this is cheaper than running OpenAPI codegen. Revisit if the surface grows.

**Initial endpoint set** (lands as feature epics ship):
- `/api/ping` — smoke test (scaffold; throwaway once real endpoints land)
- `/api/email/*` — email triage queries + actions
- `/api/transactions/*` — transaction list + details + lifecycle
- `/api/contacts/*` — known-contacts directory + governance
- `/api/dashboard/*` — aggregate queries for the dashboard
- `/api/onboarding/*` — first-sync consent + backfill status

## Frontend

**React + TypeScript + Vite**.

- **State + data fetching**: **TanStack Query v5+** with hand-written typed `fetch` wrappers under `src/api/<domain>.ts`. One file per domain exports the request/response types, the fetch function, and the `useQuery` / `useMutation` hooks that wrap it.
- **UI primitives**: **Mantine v8+** — hooks-first, TypeScript-first component library. Ships finished primitives (modals, drawers, dialogs, date pickers, notifications, dropzone, etc.). Theme + CSS variables; no required CSS-in-JS (Mantine 7 dropped emotion, Mantine 8 continued the pure-CSS direction). Mantine's own form package (`@mantine/form`) is intentionally **not** used — see Forms below.
- **Routing**: React Router v6.4+ with the Data Router (`createBrowserRouter`, route loaders, `errorElement`).
- **Forms**: **react-hook-form + Zod** (via `@hookform/resolvers/zod`). Mantine inputs wired via react-hook-form's `Controller`; thin per-input wrappers (`RhfTextInput`, `RhfSelect`) hide the Controller boilerplate once N>1 forms exist. Backend service-DTO shape is the source of truth; Zod schemas on the frontend mirror it for client-side validation.
- The frontend never talks to Gmail or any external service directly; all data flows through the backend HTTP API.

**Why Mantine over shadcn/ui or MUI** — solo developer, less React-fluent, small v0 surface, B2B SaaS (not a design-forward consumer product). Mantine ships finished components and stays as an npm dependency that can be `npm update`d, instead of either (a) requiring assembly of headless primitives (shadcn copies source into the repo — more code to own) or (b) imposing the Material aesthetic (MUI). Aesthetic is clean and doesn't read as Google/Material. If the SaaS grows past v0 and a custom design system becomes important, Mantine can be re-themed deeply before any migration is needed.

## Background jobs

**Postgres-backed work queue** — a `jobs` table with status + payload + run_after + attempts. Spring `@Scheduled` polls; `LISTEN/NOTIFY` wakes workers immediately on enqueue (no polling latency in the common case).

Job classes for v0:
- **Backfill chunks** — historical Gmail read, paginated. Resumable; checkpoint per chunk so re-runs don't redo work.
- **Live processing** — per-message classification, label application, signature parsing.
- **Signature enrichment** — extract phones from contact signatures, populate the Contact.

Upgrade path: Temporal (durable workflows) or Cloud Tasks if/when one of: workflows get multi-step + long-running, retries need richer policies, SaaS scale stresses single-DB queue throughput.

## Gmail integration

- **v0**: poll via the Gmail History API every ~60s per active user. Stores the last `historyId` in the DB; processes new messages since.
- **Upgrade**: Gmail `users.watch()` + Cloud Pub/Sub push notifications for near-real-time. Migrate when 1-minute latency starts feeling slow.

## Local development

Single repo runs locally with:

```
docker compose up -d postgres        # Postgres 18 container, bound to localhost:5432
./gradlew bootRun                    # Spring Boot dev server on :8080
cd frontend && npm run dev           # Vite dev server on :5173
```

- A separate Google Cloud OAuth client is used for local dev with `http://localhost:5173/oauth/callback` as an authorized redirect URI. The OAuth client *secret* never lives in `application-local.yml` (which is checked in) — it lives in `.env.local` (gitignored) or in an OS keyring that the local profile picks up via env vars.
- `docker-compose.local.yml` includes Postgres (and the role-init script under `scripts/db-init/`).
- Background jobs run in-process during local dev (no separate worker container).

## Deployment

**GKE Autopilot + Cloud SQL Postgres**, packaged as a **Helm chart**.

Helm chart structure:

```
deploy/helm/tc-overwatch-server/
├── Chart.yaml
├── values.yaml              # defaults
├── values-staging.yaml
├── values-prod.yaml
└── templates/
    ├── deployment.yaml       # backend container + Cloud SQL Auth Proxy sidecar
    ├── service.yaml
    ├── ingress.yaml          # GKE managed cert
    ├── configmap.yaml
    ├── externalsecret.yaml   # External Secrets Operator → GCP Secret Manager
    ├── hpa.yaml              # horizontal autoscaler
    └── networkpolicy.yaml
```

- **Secrets**: GCP Secret Manager, surfaced into pods via External Secrets Operator. Never in `values.yaml`.
- **Database**: Cloud SQL Postgres, connected via the Cloud SQL Auth Proxy as a sidecar in the backend pod. No public DB exposure.
- **Frontend**: deployed *separately* from the backend — its own static bundle, either served from a tiny Nginx container or pushed to Cloud Storage + Cloud CDN. Independent deploy pipeline, independent rollback. Backend never bundles the SPA.
- **OAuth client (prod)**: separate from local-dev client; redirect URI is the prod backend hostname (e.g. `https://api.example.com/oauth/callback`).
- **CI/CD**: TBD. Likely GitHub Actions → Artifact Registry image push → Helm upgrade. Not blocking; can ship the first version manually.

## Alternate deployment: private network (Unraid)

The GCP path above remains the **production / SaaS target** — nothing in this section changes those goals. This section adds a parallel **homelab path** for the MVP pilot phase, where running on a private Unraid server saves cost and gives the developer full control while the system is being validated against a single TC.

### Shape — same image, simpler envelope

The application container image is identical for both paths. What differs is what wraps it:

| Concern | GCP production | Unraid pilot |
|---|---|---|
| Orchestrator | GKE Autopilot + Helm | **Docker Compose** (recommended) or k3s/microk8s |
| Database | Cloud SQL (Private Service Connect, Auth Proxy sidecar) | Postgres 18 Docker container on a private docker network, persistent volume on Unraid storage |
| Edge | Cloud HTTPS LB + Cloud Armor + managed cert | **Cloudflare Tunnel** (free tier) — provides public HTTPS without exposing the homelab IP, no port forwarding |
| Public IP | Static GCP global IP | None — Cloudflare Tunnel terminates externally |
| TLS | Google-managed cert | Cloudflare-managed at the tunnel edge |
| Secrets | Secret Manager + External Secrets Operator | `.env` file outside the Git repo (e.g. `/mnt/user/appdata/tco/.env`), restricted file permissions; mounted into containers |
| Workload Identity | Yes (no keys mounted) | Static Google service account JSON key, read-only mounted into the backend container |
| Backups | Cloud SQL automated + PITR | Daily `pg_dump` cron writing to Unraid storage; weekly off-host copy (e.g. to a NAS share or B2/S3 bucket) |
| Monitoring | Cloud Logging + Cloud Monitoring | Container stdout → Unraid log viewer; optional self-hosted Grafana + Prometheus later |

**Why Docker Compose over k3s/microk8s for the pilot**: fewer moving parts to debug, faster setup, no Helm/Kubernetes overhead for one user. The cloud path already exercises k8s/Helm; the pilot doesn't need to repeat that learning. If the developer wants exact k8s parity, k3s on Unraid works too — same Helm chart with a homelab-specific `values-unraid.yaml`.

### Public access via Cloudflare Tunnel

Cloudflare Tunnel is the recommended bridge from the public internet to the homelab:

- **No port forwarding** on the home router; the tunnel is initiated outbound from a `cloudflared` container.
- **Free TLS** at the Cloudflare edge with a real public hostname (e.g. `tco.example.com` if the developer owns a domain, or a `*.trycloudflare.com` URL otherwise — prefer the owned domain).
- **OAuth-friendly**: Google's OAuth flow needs a real HTTPS redirect URI; Cloudflare-fronted hostnames work transparently. Register the **backend** hostname (`api.example.com/oauth/callback`) as an authorized redirect URI in the Google OAuth client. Frontend and backend each get their own Cloudflare Tunnel hostname (e.g. `app.example.com` for frontend, `api.example.com` for backend) — independent, parent-domain-shared so session cookies work cross-origin.
- **Cloudflare WAF + bot protection** (free tier) gives baseline edge protection comparable in *intent* to Cloud Armor, though not feature-equivalent.
- **Cloudflare Access** (zero-trust application gate) is a free add-on if the developer wants an extra auth layer in front of the app during pilot — e.g. "only specific email addresses can even reach the login page." Probably overkill for invitation-only pilot but worth knowing it's there.

### Security zone mapping

The GCP zone model translates as follows:

- **Edge** (GCP: Cloud Armor + LB + cert) ↔ **Cloudflare Tunnel + Cloudflare's free WAF/DDoS**.
- **Cluster** (GCP: private GKE, NetworkPolicies, Pod Security Restricted) ↔ **Docker network isolation**: containers on a private `tco-net` docker network, no `ports:` mappings exposed to the host except for the `cloudflared` connector. Pod Security and Workload Identity are GKE-specific and don't translate; mitigated by running containers as non-root with read-only filesystems where possible (same Compose flags as production).
- **Data** (GCP: Cloud SQL private IP + Auth Proxy) ↔ **Postgres container on `tco-net`**, never exposed outside docker. Three roles, RLS, and the three-DB-role pattern apply identically.
- **Secrets** (GCP: Secret Manager + ESO) ↔ **`.env` files** outside the repo with strict file permissions (`chmod 600`). For more rigor, **sops + age** encrypts secrets in Git and decrypts at boot; nice but not v0-mandatory.

### What stays identical between paths

- Application code, schema, RLS, three-DB-role pattern, app-layer refresh-token encryption (KEK comes from `.env` instead of Secret Manager but the encryption is the same).
- Spring profiles: `prod` (GCP), `unraid` (homelab), `local` (dev laptop). Profile selection drives DB connection details, secret source, and OAuth client ID.
- The same `Dockerfile`-built container image runs in all three.

### When to use which path

- **Local dev (developer's laptop)**: `docker compose -f docker-compose.local.yml up`
- **MVP pilot (Unraid + Cloudflare Tunnel)**: `docker compose -f docker-compose.unraid.yml up` on the Unraid Docker tab. Use this through the entire single-TC pilot.
- **SaaS / production**: GKE Autopilot via Helm. Migrate to this when (a) the pilot validates the product, (b) we open up to additional users, or (c) compliance/SLA requirements demand managed infrastructure.

### Migration story (Unraid → GCP)

When it's time to graduate from Unraid to GCP:

1. `pg_dump` the Unraid Postgres database; `pg_restore` into a fresh Cloud SQL instance.
2. Re-encrypt the OAuth refresh tokens with a new KEK from Secret Manager (the app-layer encryption supports KEK rotation).
3. Swap the Spring profile from `unraid` to `prod` (DB connection, secret source, OAuth client all change).
4. Update the Google OAuth client to add the production redirect URI; keep the Unraid one if you want a fallback during cutover.
5. Cloudflare Tunnel can stay or be retired; the GCP LB has its own IP.

Schema, code, and business logic don't change at all — only the deployment envelope.

## CI/CD

GitHub Actions for the pipeline; GitHub Container Registry (GHCR) for the image. One built image is the artifact for *both* deployment paths (Unraid pilot now, GCP later) — no per-environment image variants. Promotion is "did the same image deploy successfully to environment X?"

### Pipeline shape

**Two parallel build pipelines** (one per codebase), each with its own deploy fan-out:

```
On push to main:
  ┌─────────────────────────┐    ┌─────────────────────────┐
  │  build-server (GH-A)    │    │  build-frontend (GH-A)  │
  │  1. setup Java 21       │    │  1. setup Node          │
  │  2. ./gradlew test      │    │  2. npm ci && npm test  │
  │  3. bootBuildImage      │    │  3. npm run build       │
  │  4. push → GHCR (SHA)   │    │  4. push static bundle  │
  │                          │    │     to GHCR (nginx     │
  │                          │    │     image) or asset    │
  │                          │    │     store              │
  └────────────┬─────────────┘    └────────────┬────────────┘
               │                                │
         ┌─────┴──────┐                  ┌──────┴──────┐
         ▼            ▼                  ▼             ▼
   ┌──────────┐  ┌──────────┐      ┌──────────┐  ┌──────────┐
   │ deploy-  │  │ deploy-  │      │ deploy-  │  │ deploy-  │
   │ unraid-  │  │ prod-    │      │ unraid-  │  │ prod-    │
   │ server   │  │ server   │      │ frontend │  │ frontend │
   └──────────┘  └──────────┘      └──────────┘  └──────────┘
```

Frontend and backend deploys are independent. Either can ship without the other. The contract between them is the JSON shape of the HTTP API — kept aligned by review today, and slated to be enforced by OpenAPI codegen + CI drift check per issue #83.

### Unraid deploy: self-hosted runner

For the homelab path, the key trick is using a **GitHub Actions self-hosted runner** that lives on the Unraid box itself. The runner makes outbound calls to GitHub to fetch jobs — no inbound network exposure required. This is the same model as how Cloudflare Tunnel works for the app.

Setup:

- One `actions/runner` container (or `myoung34/github-runner` image) running on Unraid, labeled `self-hosted, unraid`, registered to the repo.
- Mount the runner's working directory and the compose files onto the runner so it can `docker compose` against the host's Docker socket (or use Docker-in-Docker if isolation matters more).
- Runner uses a fine-scoped PAT or GitHub App credentials with permission only to receive jobs for the repo.

`deploy-unraid-server` job steps (backend):

```yaml
runs-on: [self-hosted, unraid]
steps:
  - name: Pull new server image
    run: docker compose -f docker-compose.unraid.yml pull backend
  - name: Run DB migrations (separate one-shot container)
    run: docker compose -f docker-compose.unraid.yml run --rm migrate
  - name: Restart backend
    run: docker compose -f docker-compose.unraid.yml up -d backend
  - name: Wait for healthcheck
    run: ./scripts/wait-for-healthy.sh backend 60s
  - name: Smoke test (HTTP ping)
    run: ./scripts/smoke.sh
```

`deploy-unraid-frontend` job steps:

```yaml
runs-on: [self-hosted, unraid]
steps:
  - name: Pull new frontend image (nginx + static bundle)
    run: docker compose -f docker-compose.unraid.yml pull frontend
  - name: Restart frontend
    run: docker compose -f docker-compose.unraid.yml up -d frontend
  - name: Wait for healthcheck
    run: ./scripts/wait-for-healthy.sh frontend 30s
```

Important properties:

- **Migrations run in a separate container** (`migrate` service in compose, using the `tco_migrate` DB role), not on app startup. Migration failures = clean deploy failure rather than silent crash loop.
- **Backend starts only after migrations succeed.** Compose `depends_on` + the `migrate` service's `restart: "no"` + serial job steps enforce this.
- **Frontend deploy never touches the backend, and vice versa.** Two independent pipelines, two independent Cloudflare Tunnel hostnames.
- **No secrets in the CI workflow** — the runner has local access to the `.env` file on Unraid that docker-compose mounts. CI never sees DB credentials or OAuth secrets.

### Image tagging strategy

- Every build tags the image with the **full git SHA** (immutable, traceable).
- `main` is a **moving tag** pointing at the latest successful main build.
- Release versions (`v0.1.0`, `v1.0.0`) are immutable tags for prod deploys.
- Unraid pilot tracks `main` (continuous deployment is fine — single user, fast feedback loop).
- GCP production tracks release tags only — explicit promotion, no surprise upgrades.

### Watchtower as a simpler alternative

If the self-hosted-runner setup feels heavyweight, **Watchtower** is a viable substitute: it runs on Unraid, polls GHCR for the `main` tag every few minutes, and `docker compose up -d`'s any container whose image has changed. Trade-offs:

- **Loses**: migration ordering as an explicit step (migrations would run on app startup again), health gating before traffic flip, smoke tests, easy rollback workflow.
- **Gains**: zero CI configuration on the Unraid side beyond installing Watchtower and labeling containers.
- **Recommendation**: start with self-hosted runner. Watchtower is a fine fallback if the runner becomes high-maintenance.

### GCP deploy (later)

When the time comes for GCP:

- **Backend**: same GHCR image, same SHA-tagging. GitHub Actions deploy job runs `helm upgrade --install` against the GKE cluster. **OIDC token federation** for GCP authentication — GitHub Actions exchanges its OIDC token for short-lived GCP credentials via Workload Identity Federation. **No service account JSON keys stored in GitHub Secrets.**
- **Frontend**: built and pushed to Cloud Storage + Cloud CDN (or a small Cloud Run static container — pick at the time). Independent deploy job using the same OIDC federation pattern. Cache-busting via content-hashed filenames in the build output.
- **Promotion gate**: only release tags (not `main`) trigger the GCP deploy, for both backend and frontend.

### Local dev needs none of this

Developer runs `gradle bootRun` and `vite dev` from their laptop against a local Postgres in Docker. No CI loop required for inner-loop development. The pipeline only kicks in on push.

## Multi-tenancy

- Single Postgres database, single backend deployment, every table tenant-scoped via `tenant_id` (UUID).
- `tenant_id` is derived from the authenticated user. For the v0 single-user pilot, there is exactly one tenant row; the SaaS retrofit is "add tenants table, route signups to it" without schema changes.
- **Row-Level Security (RLS) is enforced from day one** on every tenant-scoped table. Retrofitting RLS is painful; turning it on now while the schema is small is cheap, and it makes cross-tenant data leaks structurally impossible regardless of application bugs.

### RLS implementation

- Every tenant-scoped table has a `tenant_id UUID NOT NULL` column and an RLS policy:
  ```sql
  ALTER TABLE [table_name] ENABLE ROW LEVEL SECURITY;
  CREATE POLICY tenant_isolation ON [table_name]
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
  ```
- The Spring Boot request pipeline sets `app.tenant_id` on the Postgres session at the start of each transaction, derived from the authenticated user. A small `@Component` `TenantConnectionInterceptor` (or equivalent JPA listener) executes `SET LOCAL app.tenant_id = '<uuid>'` on connection acquisition; the value is cleared at transaction end.
- Migrations declare RLS policies alongside the tables they protect (Liquibase (Groovy DSL) migrations include both `CREATE TABLE` and `CREATE POLICY` in the same file).
- Tests use Testcontainers Postgres with the same RLS policies enabled; a test must explicitly set the tenant context to read/write rows, which catches cross-tenant bugs at unit-test time.

### Three Postgres roles, separated by privilege

Some operations legitimately need to cross tenant boundaries (tenant provisioning, admin/operator queries, system-wide maintenance jobs). Rather than weakening the app role, we separate concerns with three distinct DB roles:

| Role | `BYPASSRLS` | Used by | Notes |
|---|---|---|---|
| `tco_app` | No | ~99% of code paths — all per-tenant request handlers and per-tenant jobs | Every query runs with `SET LOCAL app.tenant_id` |
| `tco_admin` | Yes | Tenant lifecycle (create / delete), admin RPCs, cross-tenant reports, system-wide maintenance jobs | Connections acquired via explicit `withAdminConnection { ... }` marker only |
| `tco_migrate` | Superuser (DDL) | Liquibase (Groovy DSL) migrations only | Never used by application code at runtime |

Code-level enforcement: there are exactly two connection pools — the default app pool (`tco_app`) and the admin pool (`tco_admin`). Switching pools requires an explicit `withAdminConnection { ... }` block that's grep-able, reviewable, and unambiguous about intent. Implicit / accidental admin-role access is impossible.

### Background workers and tenant context

- **Per-tenant jobs** (the default): use `tco_app`. The job table has a `tenant_id` column; the worker `SET LOCAL app.tenant_id` to the job's tenant before doing any DB work. A wrapper utility around job execution makes this automatic for typical jobs (forgetting to set it = the job's queries see nothing under RLS, which surfaces as obvious test failures).
- **System-wide jobs** (rare — e.g. "check for expired invitations across all tenants"): use `tco_admin` via the explicit `withAdminConnection` pattern. The job table's `tenant_id` is null for these; the dispatcher routes them to the admin pool.

### Future: audit trail on admin-role access

Not v0-blocking but worth adding once SaaS launches: every `withAdminConnection { ... }` block logs `(operator, operation, timestamp, affected_tenant(s))` to an immutable audit table. Lets us answer "who touched what across tenants" for security review and incident response.

The cost of RLS + role separation is a small constant overhead per query, one extra `SET LOCAL` per transaction, and one extra connection pool. The benefit is that a developer error (e.g. forgetting a `WHERE tenant_id = ?` clause in a hand-written query) cannot leak data across tenants, *and* that the legitimate cross-tenant operations are explicit and reviewable rather than hidden.

## Access control / signup

Invitation-only for MVP, implemented as a **configurable signup mode** (`invitation` / `open` / `paid`) so opening up later is a configuration change rather than a code change.

- `system_config` table (or feature-flag store) carries the current `signup_mode`. Server-side only; never branched on by the frontend.
- `Invitation` table: `id`, `email`, `token`, `created_at`, `created_by`, `expires_at`, `accepted_at`, `tenant_id` (nullable until accepted).
- Auth gate runs on the OAuth callback path (`AuthService.GoogleCallback` or equivalent). In `invitation` mode: authenticated email must match a pending `Invitation`. Mismatch → reject; no user/tenant is created.
- On match: mark invitation accepted, provision the tenant + user, return a session token to the frontend. Frontend then routes the user to the **first-sync consent** screen (separate flow — see `GOALS.md` Onboarding principle).
- Invitation creation in v0 is via direct DB insert or a single admin RPC. No admin UI until SaaS.
- New signup modes plug into the same gate by adding a check function (e.g. `subscriptionGate()` for `paid` mode).

## Security zones

The service is organized into four zones with strict boundaries. Designed for day-one setup because retrofitting network architecture later is painful and risky.

### Zone overview

| Zone | What's there | Public IP? |
|---|---|---|
| **Edge** | Cloud Armor (WAF + rate limit), Cloud HTTPS LB, managed TLS cert | Yes — single global static IP |
| **Cluster** | Private GKE Autopilot — backend pods, worker pods, frontend static assets | No — nodes have no public IPs |
| **Data** | Cloud SQL Postgres via Private Service Connect | No — private IP only |
| **Secrets / Egress** | GCP Secret Manager; outbound TLS to Gmail and Google OAuth | N/A (managed services) |

Only the edge LB has a public IP. Everything else is private and reachable only through controlled paths.

### Edge zone

- **Cloud HTTPS Load Balancer** with a global static IP. Google-managed TLS certificate; auto-renewal.
- **Google Cloud Armor** policy attached: OWASP Top 10 WAF rules (preconfigured), per-IP rate limiting, DDoS protection (always-on at L3/L4, opt-in tuning at L7).
- HTTP → HTTPS forced redirect.
- Geo / bot restrictions: not enabled in v0; available as Cloud Armor rules when needed.

### Cluster zone

- **Private GKE Autopilot** cluster: nodes have no public IPs. Control plane is reachable only from authorized networks (the developer's IP + Cloud Build / CI ranges) for `kubectl` operations.
- **VPC-native** with separate subnets for nodes, pods, services.
- **Workload Identity** binds every workload's Kubernetes ServiceAccount to a GCP service account. **No JSON service account keys** mounted anywhere — that's an anti-pattern that leaks credentials.
- **Pod Security Standards: Restricted** profile in production namespaces (no privileged, no host namespaces, non-root, read-only filesystem where possible).
- **NetworkPolicies**: default-deny in every namespace, explicit allows for the few flows that need to work (frontend → backend, backend → Cloud SQL Auth Proxy, workers → DB, all pods → DNS, all pods → metrics/logging endpoints).
- **Resource requests + limits** on every container (prevents noisy-neighbor; required by Autopilot anyway).
- **Distroless or minimal base images** for the Spring Boot container — reduce attack surface, faster startup, smaller CVE footprint.
- **Container vulnerability scanning** via Artifact Registry (on by default for Standard tier).
- **Binary Authorization** with signed images: `[BACKLOG]` for when CI signing is set up.

### Data zone

- **Cloud SQL Postgres** with **Private Service Connect** — no public IP, no Authorized Networks list to maintain.
- Backend pods connect via the **Cloud SQL Auth Proxy** sidecar, authenticated via Workload Identity.
- Three database roles already documented in the *Multi-tenancy* section: `tco_app` (default, no `BYPASSRLS`), `tco_admin` (explicit cross-tenant access), `tco_migrate` (DDL only).
- **RLS** enforced on every tenant-scoped table from day one.
- **Encryption at rest** with Google-managed keys (default). Migration to CMEK (customer-managed encryption keys) is a configuration change in Cloud SQL — `[BACKLOG]` for compliance-driven moments.
- **Automated backups** + point-in-time recovery enabled. Retention default = 7 days, tune up at SaaS launch.

### Secrets

- **All secrets in GCP Secret Manager**, accessed via Workload Identity. No secret values in container images, `values.yaml`, Git, or environment variables baked into deployments.
- **External Secrets Operator** (ESO) materializes Secret Manager values into Kubernetes Secrets at runtime. ExternalSecret manifests live in the Helm chart; pods consume the resulting Secrets normally.
- Rotation: OAuth client secret rotation is a manual process (rare); DB passwords rotate via Cloud SQL's IAM authentication when we move to IAM-DB-auth (`[BACKLOG]`).
- **Application-layer encryption for OAuth refresh tokens**: defense-in-depth. Refresh tokens are encrypted with a KEK fetched from Secret Manager before persisting to Postgres. Even a full DB dump doesn't expose plaintext Gmail refresh tokens. The KEK rotates separately.

### Egress

- Outbound TLS to Gmail API and Google OAuth — no other external destinations needed in v0.
- Workload Identity binds the backend pod's ServiceAccount to a GCP service account with only the API scopes we actually use.
- Egress firewall rules restrict outbound traffic to required endpoints once the destination set is stable (`[BACKLOG]` — wait until we know all destinations).

### Authentication / authorization at the API boundary

- **Spring Security filter chain** runs on every request, default-deny. Method/path authorization rules opt in via annotations or filter-chain config; unauthenticated endpoints (e.g. the OAuth callback handler) are an explicit allowlist.
- **Session model**: HTTP-only, Secure, SameSite=Lax cookie carries an opaque session token. Backend looks up the session server-side; no JWT verification dance for v0.
- **CSRF**: `Content-Type: application/json` + SameSite cookies + an explicit origin check on the OAuth callback is sufficient for v0. The OAuth callback path uses the standard `state` parameter to prevent CSRF on that specific HTTP flow.
- **Admin RPCs** require not just authentication but explicit admin-role membership on the authenticated user; checked at the interceptor. Admin RPCs are also the only entry points that may eventually call `withAdminConnection { ... }` for DB access.

### Observability for security

- **Cloud Audit Logs** for all GCP-side actions (who did what in the project, including Secret Manager access, IAM changes, DB connection events). On by default for admin activity; data access logging opt-in per service.
- **Application logs** through Cloud Logging with structured JSON. Field-level redaction for known-sensitive fields (email subjects/bodies are logged at most by ID, never content; OAuth tokens are never logged regardless).
- **Application-level audit log** for `withAdminConnection { ... }` invocations (already mentioned in the RLS section).
- **Alerting** on anomalies: bursts of failed auth, sudden spike in admin-role connections, sustained 5xx rate. Set up after launch; not v0-blocking.

### Day-one vs. later

**Day-one (set up before first deploy)**:

- Private GKE cluster with Workload Identity
- Cloud HTTPS LB + managed cert + Cloud Armor basic policy
- Cloud SQL with Private Service Connect, Auth Proxy sidecar
- Three DB roles, RLS enabled, application-layer refresh-token encryption
- Secret Manager + External Secrets Operator
- NetworkPolicies (default-deny)
- Pod Security Restricted, distroless images, non-root containers
- Spring Security filter chain, session cookies, CSRF protection on the OAuth callback
- Cloud Audit Logs on, structured logging with PII discipline

**Later (deliberately deferred)**:

- Binary Authorization with signed images (needs CI signing infrastructure first)
- Service mesh / mTLS between pods (overkill for the current pod count)
- CMEK on Cloud SQL (Google-managed keys are fine until a compliance requirement appears)
- Detailed alerting playbooks (set up after a few weeks of normal traffic baseline)
- Egress firewall rules locked down (after destination set stabilizes)
- IAM-based DB authentication (eliminates password rotation entirely)

## Things deliberately not pinned

These are implementation choices to make at code-writing time, not in this doc:

- US-address parser library
- Fuzzy-match library (string distance / token set ratio)
- Phone normalization library (likely `libphonenumber` since it's industry standard)
- Mantine sub-packages to pull in (`@mantine/dates`, `@mantine/notifications`, `@mantine/modals`, etc. land as features need them)
- Whether to layer `mantine-react-table` (TanStack Table wrapper) for advanced data grids, or stick with Mantine's basic `Table` component — defer until a real grid requirement appears
- Test framework choices (JUnit 5 + MockK + Testcontainers are the safe bets for backend; Vitest + React Testing Library for frontend)

## Watch items (Spring Boot 4 specifically)

Spring Boot 4 + Spring Framework 7 are recent enough that **third-party Spring Boot starters may not yet have GA-tagged compatible releases**. The most likely friction points:

- gRPC Spring starters — **moot**: scaffold initially picked proto-first / gRPC, then pivoted to plain HTTP/JSON when the browser-can't-read-HTTP/2-trailers constraint surfaced. See git tag `pivot-to-json` for the cutover. No third-party gRPC starter currently in play.
- Any Spring Security extensions — still untested; first usage lands when auth is implemented.
- JPA-extension libraries — base Spring Data JPA loads fine.

Mitigation: pin to specific Spring Boot 4–compatible versions where they exist.

## Scaffold notes (from `scaffold/initial` branch)

Things discovered while building the initial scaffold that are worth remembering:

- **JDK toolchain**: the `org.gradle.toolchains.foojay-resolver-convention` plugin at version `0.10.0` has a known bug with Gradle 9.5.1 (throws `"IBM_SEMERU"` when attempting auto-download). Workaround used: rely on a locally-installed JDK (Java 23 on the dev machine) by setting the toolchain to `JavaLanguageVersion.of(23)` in both module build files. Revisit when foojay ships a fix or when JDK 21 is installed locally for stricter version alignment.
- **Kotlin `internal` visibility on persistence classes**: cannot mark the entity / repository as `internal` because a public DAO (called from the service layer) takes them as constructor parameters — a public function cannot expose an internal type. The "entities don't leak from DAOs" architectural principle is therefore **enforced by code review** (DAOs return DTOs, not entities) rather than by the Kotlin compiler. If stricter enforcement is desired later, options are: (a) make the entire `feature.<name>.persistence` package internal including the DAO, with the DAO consumed within the same module only (matches the current single-module layout); or (b) extract a `persistence-api` interface that's public and a private implementation. Neither is in v0 scope. Extension-function mappers themselves can stay `internal` since they're not Spring beans and aren't consumed by upstream layers.
- **CI lint steps**: ktlint + detekt (backend) and `npm run lint` (frontend) are enforced — scaffold-era `continue-on-error: true` flags removed after the first clean pass. Detekt itself is still task-disabled in `server/build.gradle.kts` pending the 2.0.0 release (incompatible with Kotlin 2.2 today); ktlint covers formatting in the meantime.
