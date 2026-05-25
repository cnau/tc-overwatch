# CLAUDE.md — frontend

@../docs/claude/react.md

## Module overview

`frontend/` is the React 19 + TypeScript + Vite single-page app. It is **not** a Gradle module — it lives alongside the Gradle root with its own `package.json` and its own deploy pipeline (per `docs/architecture.md` § Frontend deployment). The backend never bundles it.

The app talks to the backend through typed `fetch` wrappers under `src/api/<domain>.ts` — one file per backend domain. TanStack Query hooks wrap the fetch calls. Components never call `fetch` directly.

## Commands

```
cd frontend
npm install                # first time
npm run dev                # Vite dev server on :5173
npm run build              # type-check + production bundle into dist/
npm run lint               # ESLint
npm run preview            # preview the production build locally
docker build -t tc-overwatch-frontend:dev -f frontend/Dockerfile .   # runtime image (Nginx + bundle)
```

Run from the repo root with `npm --prefix frontend run <script>` when chaining with backend commands.

## Production runtime

The deployable artifact is `ghcr.io/cnau/tc-overwatch-frontend` — a small Nginx image serving the Vite bundle. Built and pushed by CI (`build-and-push-frontend`) on every push to `main`; `frontend/Dockerfile` is multi-stage (`node:22-alpine` builder → `nginx:1.27-alpine` runtime). `frontend/nginx.conf` configures: SPA fallback to `index.html`, immutable 1-year cache on Vite's content-hashed assets, no-cache on the entry document, and explicit 404s on `/api/*` + `/oauth2/*` (the backend lives on a separate hostname; if a real backend request reaches this Nginx, something's misconfigured and we'd rather fail cleanly than fall through to the SPA shell).

## Module-specific notes

- **Vite proxy** in `vite.config.ts` forwards `/api/*`, `/oauth2/*`, and `/login/oauth2/*` to backend `localhost:8080`. Same-origin in the browser during dev — no CORS needed locally. Production is cross-origin under a shared parent domain; bearer tokens (not cookies) ride on every request, so no cross-origin cookie machinery is needed.
- **Stack pinned in `docs/architecture.md` § Frontend** — the full pinned set: **TanStack Query v5+, Mantine v8+ (core + hooks; sub-packages per-feature), React Router v6.4+ with the Data Router, react-hook-form + Zod, Vitest + React Testing Library + MSW for HTTP mocking**. Install per-feature, not preemptively. Pin versions in `package.json` and update this file when you add one. See `docs/claude/react.md` for the patterns to follow.
- **Path alias** `@/*` → `src/*` is wired (`tsconfig.app.json` `paths` + `vite.config.ts` `resolve.alias` + `@types/node`). Use it for everything imported from `src/`.
- **ESLint flat config** lives in `eslint.config.js`. Lint failures break CI (the scaffold-era `continue-on-error: true` flag was removed once the first clean pass landed).
- **No CRA, no Webpack config files.** Vite handles bundling; don't introduce a custom webpack config or eject anything.

## What lives in this module vs. backend

- **UI, routing, forms, client-side state** — here.
- **Email parsing, classification, label application** — backend (it's where Gmail tokens live).
- **Validation** — both layers. Backend enforces (Jakarta Bean Validation on the request DTO); frontend mirrors the backend rules (Zod) for fast feedback. Backend is the source of truth.
- **Auth flow** — frontend links `<a href="/oauth2/authorization/google">` (via Vite proxy) to start OAuth. Backend handles Google handshake, mints our HS256 session JWT, redirects to SPA root with the token in a URL fragment (`#token=…`). The SPA's `useOAuthTokenBridge` hook reads the fragment on mount, stores the token in localStorage, and clears the fragment via `history.replaceState`. Google's access/refresh tokens stay server-side; the SPA only ever sees our session JWT.
