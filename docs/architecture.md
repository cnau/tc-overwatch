# Architecture — tc-overwatch

The implementation stack. Decisions locked here are the result of explicit requirements/UX work in `GOALS.md` and the trade-off discussion captured here. Implementation-level details that aren't strategic (specific library choices, version pins beyond majors) are intentionally deferred and called out below.

## Stack at a glance

| Layer | Choice | Notes |
|---|---|---|
| Build | Gradle 9.5.1 | Kotlin DSL |
| JVM | 21+ | Spring Boot 4 requires it |
| Backend | Spring Boot 4.0.6 + Kotlin | Bleeding edge — watch third-party compat |
| Database | Postgres 18 | `pg_trgm` for fuzzy match, `JSONB` for flexible fields |
| API wire format | gRPC + Protobuf | Connect protocol for browser compat |
| Schema management | Buf | proto linting, breaking-change detection, codegen |
| Frontend | React + TypeScript + Vite | Connect-ES client |
| Background jobs | Postgres-backed queue (Spring `@Scheduled` + `LISTEN/NOTIFY`) | Upgrade to Temporal only if SaaS scale demands |
| Auth | Google OAuth2 via Spring Security | `gmail.modify` scope; tokens stored server-side encrypted |
| Local dev | Docker Compose (Postgres) + `gradle bootRun` + `vite dev` | |
| Deploy target | GKE Autopilot | Helm chart per environment |
| Managed Postgres | Cloud SQL | via Cloud SQL Auth Proxy sidecar |

## Backend

**Spring Boot 4 + Kotlin** with Gradle (Kotlin DSL).

- **JPA** (Hibernate) for persistence is the default; switch to **jOOQ** or **Exposed** later if generated query DSLs become a bottleneck. Start with the boring choice.
- **Spring Security** for auth (Google OAuth2 client). Refresh tokens are encrypted at rest in Postgres; never returned to the browser.
- **Multi-tenancy**: single Postgres, every domain table carries a `tenant_id` column from day one. Tenant context is bound to the request via a `TenantContext` interceptor populated from the authenticated session. Postgres row-level security is available later for hard isolation but not enabled in v0.
- **gRPC integration**: a Spring Boot starter for grpc-java wraps the gRPC server lifecycle in Spring conventions. **Open**: pick a specific starter (e.g. `grpc-spring-boot-starter` by LogNet/yidongnan, or native `grpc-java` with manual Spring wiring). Compatibility with Spring Boot 4 is the deciding factor — confirm before committing.

## API — gRPC + Connect

The API is **proto-first**. `.proto` files in `proto/` are the single source of truth; they generate both backend stubs (Kotlin) and frontend clients (TypeScript).

- **Server**: speaks both standard gRPC (HTTP/2) and Connect/gRPC-Web (browser-compatible). Both wire from the same handler implementations.
- **Browser**: cannot speak raw gRPC (HTTP/2 trailers issue). Uses **Connect-ES** (`@connectrpc/connect-web`) which speaks Connect protocol and falls back to gRPC-Web — both are server-compatible with our gRPC server.
- **Schema management**: **Buf** for proto linting, breaking-change detection, and codegen. `buf.gen.yaml` defines codegen plugins for Kotlin server stubs and TypeScript client stubs.
- **No JSON REST API in v0.** gRPC + Connect handles everything. If a future integration partner needs REST, generate a REST gateway from the same protos at that point.

**Service shape (initial)**:
- `EmailService` — list / detail for processed emails by transaction
- `TransactionService` — CRUD, lifecycle transitions
- `ContactService` — CRUD, role corrections, manual promotion/demotion
- `DashboardService` — aggregate queries for the dashboard
- `OnboardingService` — first-sync consent, backfill status

Full proto definitions are TBD as implementation begins.

## Frontend

**React + TypeScript + Vite**.

- **State + data fetching**: TanStack Query on top of generated Connect-ES clients. The cache key is the RPC method + request; mutations invalidate relevant queries.
- **UI primitives**: Tailwind CSS + shadcn/ui (or equivalent — open).
- **Routing**: React Router.
- **Forms**: react-hook-form (or alternative — open).
- The frontend never talks to Gmail or any external service directly; all data flows through the gRPC API.

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
./gradlew bootRun                    # Spring Boot dev server on :8080 (gRPC + Connect)
cd frontend && npm run dev           # Vite dev server on :5173
```

- A separate Google Cloud OAuth client is used for local dev with `http://localhost:5173/oauth/callback` as an authorized redirect URI. Credentials in `application-local.yaml` (gitignored) or `.env.local`.
- `docker-compose.yml` includes Postgres and optionally pgAdmin/Adminer for DB browsing.
- Background jobs run in-process during local dev (no separate worker container).

## Deployment

**GKE Autopilot + Cloud SQL Postgres**, packaged as a **Helm chart**.

Helm chart structure:

```
deploy/helm/tc-overwatch/
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
- **Frontend**: built as static assets, served either by the backend (Spring static handler) or by a separate Nginx/CDN. **Open**: pick one — serving from the backend is simpler for v0.
- **OAuth client (prod)**: separate from local-dev client; redirect URI is the prod ingress hostname.
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
- **OAuth-friendly**: Google's OAuth flow needs a real HTTPS redirect URI; Cloudflare-fronted hostnames work transparently. Register the public hostname as an authorized redirect URI in the Google OAuth client.
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

```
On push to main:
  ┌────────────────────────────────────────────────┐
  │  GitHub Actions (cloud)                         │
  │  1. Checkout, setup Java 21 + Node              │
  │  2. ./gradlew test                              │
  │  3. ./gradlew bootBuildImage  (Spring Boot      │
  │     Cloud Native Buildpacks → OCI image)        │
  │  4. docker push → GHCR with tag = git SHA       │
  │     + 'main' moving tag                         │
  └─────────────────┬───────────────────────────────┘
                    │
              ┌─────┴──────┐
              ▼            ▼
  ┌──────────────┐  ┌──────────────────┐
  │ deploy-unraid│  │ deploy-prod      │
  │ (pilot)      │  │ (later, GCP)     │
  └──────────────┘  └──────────────────┘
```

### Unraid deploy: self-hosted runner

For the homelab path, the key trick is using a **GitHub Actions self-hosted runner** that lives on the Unraid box itself. The runner makes outbound calls to GitHub to fetch jobs — no inbound network exposure required. This is the same model as how Cloudflare Tunnel works for the app.

Setup:

- One `actions/runner` container (or `myoung34/github-runner` image) running on Unraid, labeled `self-hosted, unraid`, registered to the repo.
- Mount the runner's working directory and the compose files onto the runner so it can `docker compose` against the host's Docker socket (or use Docker-in-Docker if isolation matters more).
- Runner uses a fine-scoped PAT or GitHub App credentials with permission only to receive jobs for the repo.

`deploy-unraid` job steps:

```yaml
runs-on: [self-hosted, unraid]
steps:
  - name: Pull new image
    run: docker compose -f docker-compose.unraid.yml pull backend
  - name: Run DB migrations (separate one-shot container)
    run: docker compose -f docker-compose.unraid.yml run --rm migrate
  - name: Restart backend
    run: docker compose -f docker-compose.unraid.yml up -d backend
  - name: Wait for healthcheck
    run: ./scripts/wait-for-healthy.sh backend 60s
  - name: Smoke test (gRPC ping)
    run: ./scripts/smoke.sh
```

Important properties:

- **Migrations run in a separate container** (`migrate` service in compose, using the `tco_migrate` DB role), not on app startup. This makes migration failures a clean deploy failure rather than a silent crash loop.
- **Backend starts only after migrations succeed.** Compose `depends_on` + the `migrate` service's `restart: "no"` + the deploy job's serial steps enforce this.
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

- Same GHCR image, same SHA-tagging.
- GitHub Actions deploy job runs `helm upgrade --install` against the GKE cluster.
- **OIDC token federation** for GCP authentication — GitHub Actions exchanges its OIDC token for short-lived GCP credentials via Workload Identity Federation. **No service account JSON keys stored in GitHub Secrets.** This is the modern equivalent of using Workload Identity inside the cluster.
- Promotion gate: only release tags (not `main`) trigger the GCP deploy.

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
- Migrations declare RLS policies alongside the tables they protect (Flyway / Liquibase migrations include both `CREATE TABLE` and `CREATE POLICY` in the same file).
- Tests use Testcontainers Postgres with the same RLS policies enabled; a test must explicitly set the tenant context to read/write rows, which catches cross-tenant bugs at unit-test time.

### Three Postgres roles, separated by privilege

Some operations legitimately need to cross tenant boundaries (tenant provisioning, admin/operator queries, system-wide maintenance jobs). Rather than weakening the app role, we separate concerns with three distinct DB roles:

| Role | `BYPASSRLS` | Used by | Notes |
|---|---|---|---|
| `tco_app` | No | ~99% of code paths — all per-tenant request handlers and per-tenant jobs | Every query runs with `SET LOCAL app.tenant_id` |
| `tco_admin` | Yes | Tenant lifecycle (create / delete), admin RPCs, cross-tenant reports, system-wide maintenance jobs | Connections acquired via explicit `withAdminConnection { ... }` marker only |
| `tco_migrate` | Superuser (DDL) | Flyway / Liquibase migrations only | Never used by application code at runtime |

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

- **gRPC auth interceptor** runs on every method, default-deny. Method-level authorization rules opt in via annotation / metadata; unauthenticated methods (e.g. the OAuth callback handler) are an explicit allowlist.
- **Session model**: HTTP-only, Secure, SameSite=Lax cookie carries an opaque session token. Backend looks up the session server-side; no JWT verification dance for v0.
- **CSRF**: gRPC-Web / Connect requests require a `Content-Type` and a custom header that browsers won't send cross-origin without preflight; combined with SameSite cookies this is sufficient for v0. The OAuth callback path uses the standard `state` parameter to prevent CSRF on that specific HTTP flow.
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
- gRPC auth interceptor, session cookies, CSRF protection on the OAuth callback
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

- Specific gRPC Spring starter (compat with Spring Boot 4 is the deciding factor)
- US-address parser library
- Fuzzy-match library (string distance / token set ratio)
- Phone normalization library (likely `libphonenumber` since it's industry standard)
- React UI component library specifics (shadcn/ui, Mantine, others)
- Form library specifics
- Test framework choices (JUnit 5 + MockK + Testcontainers are the safe bets for backend; Vitest + React Testing Library for frontend)

## Watch items (Spring Boot 4 specifically)

Spring Boot 4 + Spring Framework 7 are recent enough that **third-party Spring Boot starters may not yet have GA-tagged compatible releases**. The most likely friction points:

- gRPC Spring starters (community-maintained, historically lag Spring Boot majors by 3–6 months)
- Any Spring Security extensions
- JPA-extension libraries

Mitigation: pin to specific Spring Boot 4–compatible versions where they exist; fall back to direct grpc-java integration without a Spring starter if necessary.
