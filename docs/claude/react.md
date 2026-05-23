# React Conventions

Shared frontend guidance. Imported by `frontend/CLAUDE.md`.

## Tech baseline

Installed today (scaffold):

- **React 19** + **TypeScript 5.7** (strict)
- **Vite 6** for dev server, build, and bundling
- **ESLint 9** (flat config) with `@eslint/js`, `typescript-eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`

Pinned stack (per `docs/architecture.md` § Frontend) — install per-feature, don't preemptively `npm install` everything:

- **Connect-ES** (`@connectrpc/connect-web` + `@connectrpc/connect`) — the only path that calls the backend. Generated clients live in `src/gen/` (Buf output; never hand-edit).
- **TanStack Query** (`@tanstack/react-query`) — server state cache layered over Connect-ES. Cache keys: RPC method + request.
- **Mantine v7+** — UI components. Core packages: `@mantine/core`, `@mantine/hooks`. Optional packages (`@mantine/dates`, `@mantine/notifications`, `@mantine/modals`, `@mantine/dropzone`, `@mantine/charts`) land as features need them.
- **React Router v6+** (`react-router-dom`) with the Data Router (`createBrowserRouter`).
- **react-hook-form** + **Zod** + `@hookform/resolvers/zod` — forms and validation.
- **Vitest** + **React Testing Library** + **@testing-library/user-event** — testing.

When you add a library, pin its version in `package.json` and update `frontend/CLAUDE.md`.

## TypeScript-first

This is a TypeScript-only codebase. No JavaScript source files in `src/`.

- **Never `any`.** For external/unknown shapes (third-party callbacks, raw API responses before generated clients are in place), use `unknown` and narrow with type guards.
- **Never `@ts-nocheck` or `@ts-ignore`.** Both produce a visual rename with zero protection against contract drift. If a file genuinely can't be typed cleanly in one pass, surface it; don't silently suppress.
- `tsconfig.json` strict mode stays on. If you find yourself wanting to disable a strict-mode flag to make code compile, the code is the problem, not the flag.

## Clean, reusable, modular

- **Single responsibility** — a component renders, a hook encapsulates behavior, a query owns one piece of server state. Don't mix.
- **Small files** — if a component is hard to read, split it.
- **Pure where possible** — render functions and selectors should be pure. Side effects go in hooks or mutation handlers.
- **Reuse before reinvent** — check `src/components/`, `src/hooks/`, `src/lib/` before writing anything generic.
- **Co-locate** — tests, styles, and small helpers live next to the component that uses them. Promote to a shared location only when a second consumer appears.

## Directory structure (target)

```
frontend/src/
├── gen/           # Buf-generated Connect-ES clients (don't edit)
├── api/           # Hand-written wrappers around generated clients (TanStack Query hooks)
├── pages/         # Top-level route components
├── components/    # Reusable UI, organized by domain or kind
├── hooks/         # Custom hooks
├── lib/           # Pure utilities, formatters, type guards
├── router/        # Route definitions (createBrowserRouter config)
└── theme/         # Mantine theme object; CSS module globals
```

The scaffold currently ships with only `App.tsx` + `main.tsx`. Build out the tree as features land, not preemptively.

## State management

Three layers — pick the right one:

1. **TanStack Query** (`src/api/`) — for anything from the backend. Cache key = RPC method + request. Mutations invalidate relevant queries by query key. Never call a generated Connect client directly from a component — wrap in a `useFoo` query hook.
2. **React local state** (`useState`, `useReducer`) — for everything client-side that lives within one component's tree.
3. **React Context** — only when a value is genuinely shared across an unrelated subtree (auth user, theme). Don't reach for it for short-distance prop drilling.

No global store (no Redux, no Zustand, no Jotai). Server state belongs in TanStack Query; everything else is either local or context.

## Connect-ES + TanStack Query pattern

```ts
// src/api/email.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { createConnectTransport } from '@connectrpc/connect-web'
import { createPromiseClient } from '@connectrpc/connect'
import { EmailService } from '@/gen/com/tcoverwatch/email/v1/email_service_connect'

const transport = createConnectTransport({ baseUrl: '/rpc', credentials: 'include' })
const client = createPromiseClient(EmailService, transport)

export const emailKeys = {
  all: ['email'] as const,
  list: (params: ListParams) => [...emailKeys.all, 'list', params] as const,
}

export function useEmailList(params: ListParams) {
  return useQuery({
    queryKey: emailKeys.list(params),
    queryFn: () => client.list(params),
  })
}

export function useLabelEmail() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: LabelRequest) => client.label(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: emailKeys.all }),
  })
}
```

- `credentials: 'include'` so session cookies attach on every call (cross-origin in prod — see `architecture.md` § CORS).
- Vite proxy maps `/rpc/*` → backend gRPC in local dev (`vite.config.ts`).

## Components

- **Function components only.**
- **One component per file**, default-exported (`PascalCase` filename matching the component).
- **Props destructured at the top.** No `props.foo` scattered through the body.
- **Lift state only as far as it needs to go.** Local state stays local.
- **Memoize when measurements show it matters** — not by default. `React.memo`, `useMemo`, `useCallback` are tools, not habits.

## Routing

- **React Router v6+** with the Data Router. The single `createBrowserRouter(routes)` config lives in `src/router/routes.tsx`; `App.tsx` wraps it with `<RouterProvider />`.
- Page components live in `src/pages/` (`DashboardPage.tsx`, `TransactionDetailsPage.tsx`, etc.); routes import them eagerly. Lazy-load only when bundle size measurements justify it.
- Use route `loader`s for data that must be present before render (e.g. the dashboard's initial fetch). Loaders sit naturally next to TanStack Query — call `queryClient.fetchQuery` from a loader to populate the cache, then the component reads via `useQuery` and gets an instant hit.
- Use `<Outlet />` for nested layouts (e.g. the dashboard shell wrapping individual transaction views).
- Navigation: `<Link>` and `useNavigate()` — never use raw `<a href>` for in-app routes.

## Styling — Mantine

- **Setup**: install `@mantine/core` + `@mantine/hooks`, import `@mantine/core/styles.css` once at the app root, wrap the tree in `<MantineProvider theme={theme}>`. Theme object lives in `src/theme/`. PostCSS preset with `postcss-preset-mantine` is required for Mantine's mixins (`mantine-light-dark`, `rem`, etc.) — add it to `postcss.config.cjs` per Mantine's setup guide.
- **Components first** — Mantine's `Button`, `TextInput`, `Select`, `Modal`, `Drawer`, `Notification`, etc. cover most UI needs. Reach for these before writing a custom component.
- **Layout** — use Mantine's layout primitives (`Stack`, `Group`, `Flex`, `Grid`, `SimpleGrid`, `Container`). Don't use raw `<div>` for layout when a named primitive expresses the intent.
- **`style` prop** for one-off styling on Mantine components: `style={{ marginTop: 16 }}` is fine for small tweaks. For anything reusable or non-trivial, write a **CSS Module** (`Foo.module.css`) co-located with the component.
- **`rem` units** — Mantine uses `rem` throughout for accessibility. Match that convention; don't hardcode `px` for spacing or font sizes.
- **Theming** — colors, spacing scale, breakpoints, and component defaults live in the `MantineThemeOverride` object in `src/theme/`. Don't hardcode colors in components — reference theme tokens (`var(--mantine-color-blue-6)` in CSS, or `theme.colors.blue[6]` in TS).
- **No additional styling systems.** Don't add Tailwind, styled-components, emotion, or Sass alongside Mantine — pick one. Mantine is the pick.

## Forms

- **react-hook-form** for any form with more than two fields or any cross-field validation.
- **Validation: Zod schemas** wired via `@hookform/resolvers/zod`. Service-DTO shape is the source of truth; the Zod schema is the client-side mirror.
- **Do not use `@mantine/form`.** Mantine ships its own form package but we use react-hook-form because (a) it works with any UI library and (b) the Zod-as-source-of-truth pattern is cleaner end-to-end. Mantine inputs integrate with react-hook-form via `Controller`:

  ```tsx
  import { Controller, useForm } from 'react-hook-form'
  import { zodResolver } from '@hookform/resolvers/zod'
  import { TextInput } from '@mantine/core'

  const schema = z.object({ name: z.string().min(1) })
  type FormData = z.infer<typeof schema>

  function ContactForm() {
    const { control, handleSubmit } = useForm<FormData>({ resolver: zodResolver(schema) })
    return (
      <form onSubmit={handleSubmit(onSubmit)}>
        <Controller
          name="name"
          control={control}
          render={({ field, fieldState }) => (
            <TextInput {...field} label="Name" error={fieldState.error?.message} />
          )}
        />
      </form>
    )
  }
  ```

- For simple controlled inputs (1-2 fields, no validation), `register()` is shorter than `Controller`. Reach for `Controller` whenever the Mantine component needs custom `value`/`onChange` wiring or has a non-`input` API (e.g. `Select`, `DateInput`, `MultiSelect`).

## Hooks

- **Custom hooks** when stateful logic is reused across two or more components, or when a component body has too many `useEffect` blocks to follow.
- Name them `useXxx`, return a stable shape (object or tuple).
- Don't wrap a single line of state in a custom hook — that's noise, not abstraction.

## Testing

- **Vitest** + **React Testing Library**.
- Test files live next to source: `Foo.tsx` → `Foo.test.tsx`.
- Test behavior, not implementation: render, interact via `userEvent`, assert on what the user sees.
- Mock the generated Connect client at the module boundary, not individual TanStack Query hooks.
- Don't test trivial code (pure render-only components, simple selectors).

## File conventions

- **Components**: `PascalCase.tsx`, default export.
- **Pages**: `XxxPage.tsx` in `src/pages/`.
- **Hooks**: `useXxx.ts` in `src/hooks/`.
- **API wrappers**: `src/api/<domain>.ts` exporting query keys + hooks.
- **Constants**: `SCREAMING_SNAKE_CASE` exports.

## Imports

- **Intended convention**: absolute imports via `@/*` alias (e.g. `import { useEmailList } from '@/api/email'`). The alias requires `paths` in `tsconfig.app.json` *and* a matching `resolve.alias` in `vite.config.ts` *and* `@types/node` for the typical `fileURLToPath` setup — wire all three when the first hand-written import lands. Until then, relative imports are fine for the small scaffold tree.
- **Generated code**: once codegen runs, import from the generated module paths (e.g. `@/gen/...` after the alias is set up). Never copy generated types into hand-written files.

## Environment variables

- Prefixed `VITE_*` (Vite convention). Files: `.env`, `.env.local` (gitignored), `.env.production`.
- Client-side env vars ship in the bundle — never put true secrets here. OAuth client ID is fine (it's public by design); API keys for paid third-party services are not.

## Anti-patterns to avoid

- Fetching data in `useEffect` when a TanStack Query hook would do.
- Storing server state in React Context or `useState` (use TanStack Query).
- Calling generated Connect clients directly from components — go through `src/api/`.
- Raw `fetch` / `axios` to the backend — the only path is Connect.
- `any` or `@ts-nocheck` to silence type errors.
- Adding a new state library (Redux, Zustand, Jotai) — re-evaluate why TanStack Query + local state isn't enough first.
- Importing from `src/gen/` in test files — mock at the Connect client boundary.
- Mixing Mantine with Tailwind, styled-components, emotion, or another styling system — pick one stack and stay in it.
- Using `@mantine/form` instead of react-hook-form + Zod — see Forms above.
- Re-implementing a Mantine primitive (custom `Button`, custom `Modal`) when the Mantine one works with a `theme` override.
- Hardcoding colors / spacing / font sizes in component files when theme tokens or `rem` units exist.
