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
```

Run from the repo root with `npm --prefix frontend run <script>` when chaining with backend commands.

## Module-specific notes

- **Vite proxy** in `vite.config.ts` forwards `/api/*` and `/oauth/*` to backend `localhost:8080`. Same-origin in the browser during dev — no CORS needed locally. Production uses cross-origin cookies under a shared parent domain; see `docs/architecture.md` § CORS.
- **Stack pinned in `docs/architecture.md` § Frontend** — the full pinned set: **TanStack Query v5+, Mantine v8+ (core + hooks; sub-packages per-feature), React Router v6.4+ with the Data Router, react-hook-form + Zod, Vitest + React Testing Library + MSW for HTTP mocking**. Install per-feature, not preemptively. Pin versions in `package.json` and update this file when you add one. See `docs/claude/react.md` for the patterns to follow.
- **Path alias** `@/*` → `src/*` is wired (`tsconfig.app.json` `paths` + `vite.config.ts` `resolve.alias` + `@types/node`). Use it for everything imported from `src/`.
- **ESLint flat config** lives in `eslint.config.js`. The CI lint step currently uses `continue-on-error: true` (per architecture scaffold notes) — remove that flag after the first clean lint pass.
- **No CRA, no Webpack config files.** Vite handles bundling; don't introduce a custom webpack config or eject anything.

## What lives in this module vs. backend

- **UI, routing, forms, client-side state** — here.
- **Email parsing, classification, label application** — backend (it's where Gmail tokens live).
- **Validation** — both layers. Backend enforces (Jakarta Bean Validation on the request DTO); frontend mirrors the backend rules (Zod) for fast feedback. Backend is the source of truth.
- **Auth flow** — frontend redirects to backend `/oauth/...`, backend issues the session cookie; frontend never sees the access/refresh tokens.
