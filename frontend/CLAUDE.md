# CLAUDE.md — frontend

React 19 + TypeScript + Vite SPA. **Not a Gradle module** — it has its own `package.json` and
its own deploy pipeline, and the backend never bundles it. Building UI, an API module, or a
form → use the `frontend-feature` skill.

- **Components never call `fetch` directly.** Everything goes through
  `src/api/<domain>.ts`, which wraps the shared `requestJson` helper in TanStack Query hooks.
  `frontend/src/api/ping.ts` is the reference implementation.
- **The Vite proxy** (`vite.config.ts`) forwards `/api/*`, `/oauth2/*`, and `/login/oauth2/*`
  to `localhost:8080`, so dev is same-origin and needs no CORS. Production is cross-origin
  under a shared parent domain; bearer tokens (not cookies) ride on every request.
- **`nginx.conf` returns explicit 404s for `/api/*` and `/oauth2/*`.** Deliberate: the backend
  is on a separate hostname, so a real backend request arriving here means something is
  misconfigured, and failing cleanly beats falling through to the SPA shell. Anything
  backend-bound needs the absolute `apiBaseUrl` prefix — including plain `<a href>` links to
  the OAuth start endpoint.
- **Runtime config comes from `/config.js`, not the bundle.** One image deploys everywhere;
  `docker-entrypoint.d/40-generate-config-js.sh` writes `/config.js` from `$APP_API_BASE_URL`
  at container start. `index.html` loads it as a classic (synchronous) script so
  `window.__APP_CONFIG__` is always populated before the deferred module bundle runs. To add a
  knob: extend `AppConfig` in `src/config.ts`, extend the heredoc in that script, document the
  env var.
- **`src/gen/api.d.ts` is generated and committed.** Regenerate with
  `npm run gen-api-types` (backend must be running); CI's `api-type-drift` job fails on drift.
- **There are no tests yet** — Vitest, RTL, and MSW are pinned but not installed, and there is
  no `test` script. Installing them is part of writing the first test; see the
  `frontend-feature` skill.
- **No CRA, no Webpack, no second styling system.** Vite bundles; Mantine + CSS Modules style.
