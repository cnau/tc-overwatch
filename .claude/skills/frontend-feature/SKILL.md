---
name: frontend-feature
description: Use when writing or changing React/TypeScript code in frontend/ — adding a component, page, route, form, or custom hook; creating or editing an src/api/<domain>.ts module; handling API errors; picking where state lives; styling with Mantine; or writing frontend tests. Covers the api-module contract, the four state buckets, and the generated-types workflow.
---

# Frontend feature work

Stack rationale (why Mantine, why no global store) is in `docs/architecture.md` § Frontend.
This is how to write code that fits.

**Actually installed**: React 19, TypeScript 5.7, Vite 6, ESLint 9 flat config, Mantine 9
(`@mantine/core` + `@mantine/hooks`), TanStack Query v5, `openapi-typescript`.

**Pinned but not yet installed** — install per-feature when the first real need lands, not
preemptively: React Router v6.4+ (Data Router), react-hook-form + Zod
(`@hookform/resolvers/zod`), Vitest + React Testing Library + user-event + MSW. Update
`package.json` when you add one.

## API modules — the core contract

One file per backend domain in `src/api/`. **`src/api/ping.ts` is the reference implementation**
(16 lines, complete); `src/api/auth.ts` shows a query plus a logout mutation. Read those instead
of working from a template.

Each module exports types re-exported from `@/gen/api` and TanStack Query hooks. Nothing else.

- **`requestJson` is module-private and never re-exported.** It lives in `src/api/http.ts` and
  only the hooks are public surface. A consumer importing the raw fetch function would skip
  TanStack Query's cache, deduplication, retry policy, and the `ApiError` envelope parsing —
  keeping it unexported makes that bypass structurally impossible.
- **Components never call `fetch` directly**, always through `src/api/<domain>.ts`.
- `requestJson` handles `Content-Type`, injects `Authorization: Bearer <token>` when one is
  stored, parses JSON, prepends `appConfig.apiBaseUrl` to leading-slash URLs, and converts the
  backend's `{ code, message, details? }` envelope into a typed `ApiError`.
- Hook naming: **noun-first for queries** (`useFoo`, `useFoos`, `useFooById`), **verb-first for
  mutations** (`useCreateFoo`, `useSendPing`). Avoid the codegen-flavored `useFooMutation`
  suffix — verb-first reads better at the call site and doesn't collide when a resource has
  several mutations.
- Mutations invalidate by query key on success. Keep keys in a `fooKeys` object.

### Error handling

Failed responses throw `ApiError` with `code`, `message`, `status`, and optional `details`.

**Branch on `error.code`, never parse `error.message`** — that's exactly what `code` is for.
Non-envelope failures (proxy, network) arrive as `code: 'HTTP_ERROR'`.

Render failures inline with `<ApiErrorAlert error={query.error} />` from
`@/components/ApiErrorAlert`; override the title via the `title` prop. For code-specific
recovery hints, branch on `error.code` *outside* the alert and render siblings — don't bolt a
slot onto that component.

### Generated types

Types come from `frontend/src/gen/api.d.ts`, generated from the backend's `/v3/api-docs` by
`openapi-typescript`. The file is **committed**, and CI's `api-type-drift` job boots the stack,
regenerates, and diffs — drift fails the build.

Workflow when a backend DTO changes:

1. Change the Kotlin DTO.
2. With the backend running: `npm --prefix frontend run gen-api-types`.
3. Type errors at call sites show you what to fix.
4. Commit the backend change and the regenerated `api.d.ts` **together**.

**Never hand-write a type that has a backend counterpart.** Re-export instead:

```ts
import type { components } from '@/gen/api'
export type Contact = components['schemas']['Contact']
```

**Known wrinkle**: response fields render as **optional** in the spec even when the Kotlin type
is non-null (a springdoc gap). Expect `string | undefined` on response fields and use
`?? defaultValue`, or a non-null assertion where the contract is genuinely clear.

## State — four buckets

1. **Server state → TanStack Query.** The hook *is* the state; never shadow it in `useState`.
2. **URL state → `useSearchParams` / route params.** Filters, tabs, selected row id, pagination,
   open drawer. Linkable, refresh-safe, decouples siblings.
3. **Local component state → `useState` / `useReducer`.** Form drafts, hover, transient flags.
   Reach for `useReducer` when 3+ related values change together.
4. **Cross-tree shared → Context.** Auth user, theme. Not for short-distance prop drilling.

**No global store** — no Redux, Zustand, or Jotai.

## Components

Function components, one per file, default-exported, PascalCase filename. Props destructured at
the signature; annotate the props type directly (no `React.FC`). `ref` is a regular prop in
React 19 — no `forwardRef`. Memoize only when a measurement says to.

**Small contracts, composition over configuration.** Reusability is an outcome of good design,
not something you reach by adding props.

| Tier | Where | Reuse |
|---|---|---|
| Primitives | `src/components/primitives/` | Wide — Mantine wrappers with project defaults |
| Composite | `src/components/<kind>/` | 2–N places — `ApiErrorAlert`, `EmptyState` |
| Domain | `src/components/<domain>/` or co-located | 1–2 places — `TransactionRow` |

A `TransactionRow` used in two places is still domain. Don't over-promote. **Never promote to
`src/components/` for hypothetical future reuse.**

A component with 8 boolean props is a smell. Default to accepting `children`; for multiple
regions use compound components (`<Card.Header>`, dot-namespaced, sharing state via internal
Context — the same shape as Mantine's `Tabs` and `Accordion`) rather than render-prop slots.
When one consumer needs a tweak the other four don't, **wrap locally instead of adding a prop**.

When wrapping Mantine, spread the rest so the underlying API stays reachable
(`({ variant = 'primary', ...rest }) => <MantineButton {...rest} />`). Don't expose internal
state shape — five `useState`s are not five `onChange` props. A primitive that accepts Mantine
`sx` is leaking Mantine.

**Split when**: file > ~200 lines, JSX > 4 levels deep, > 5 unrelated `useState`s, or an obvious
independent sub-component name suggests itself.

**Container vs presentational**, loosely: fetch at the container, let the rendering component take
already-fetched data as props. Makes the renderer testable without mocking transports.

## Routing

`createBrowserRouter(routes)` in `src/router/routes.tsx`, `<RouterProvider />` in `App.tsx`, pages
in `src/pages/` eagerly imported. Use route `loader`s for data required before render — call
`queryClient.fetchQuery` in the loader and the component's `useQuery` gets an instant cache hit.
`<Outlet />` for nested layouts.

**Never use a raw `<a href>` for in-app navigation** — `<Link>` or `useNavigate()`. (A literal
`<a href>` *is* correct for the backend OAuth start endpoint, which is a cross-origin document
navigation, not in-app routing — and it needs the `apiBaseUrl` prefix.)

## Mantine

`@mantine/core` + `@mantine/hooks`, `@mantine/core/styles.css` imported, wrapped in
`<MantineProvider theme={theme}>`, `postcss-preset-mantine` in `postcss.config.cjs`. Theme in
`src/theme/`.

Provider order, outer to inner: `StrictMode → MantineProvider → QueryClientProvider → App`. This
is convention, not correctness — neither consumes the other's context. The order reads as
"visual context cascades, then data plumbing, then components consume both."

- **Reach for Mantine components before writing custom** — Button, TextInput, Select, Modal,
  Drawer, Notification are all there. Don't re-implement a themable primitive.
- **Layout primitives** (`Stack`, `Group`, `Flex`, `Grid`, `SimpleGrid`, `Container`) over raw
  `<div>` when they express the intent.
- `style` prop for one-offs; **CSS Modules** (`Foo.module.css`, co-located) for anything reusable.
- **`rem` units and theme tokens — never hardcode colors, spacing, or font sizes.**
- **Lift to the theme on the third use.** One-off and two-off stay inline; the third occurrence
  of a value is the signal to promote it to `src/theme/index.ts` and reference the token.
- **No second styling system** — no Tailwind, styled-components, emotion, or Sass.

## Forms

react-hook-form for any form beyond 2 fields or with cross-field validation, Zod schemas via
`@hookform/resolvers/zod`. Zod mirrors the backend service-DTO shape, which stays the source of
truth — the backend enforces (Jakarta Bean Validation on the request DTO), the frontend mirrors
for fast feedback.

**Never use `@mantine/form`** — react-hook-form is the project standard.

Mantine inputs wire in via `Controller`; bare `register()` is fine for one or two plain text
inputs. Once the second form lands, extract typed wrappers (`RhfTextInput<T>`, `RhfSelect<T>`) into
`src/components/forms/`: take `name: FieldPath<T>`, pull `control` from `useFormContext`, spread
`field` into the Mantine input, surface `fieldState.error?.message` as `error`. Build the family
as needed; don't pre-fabricate.

## Hooks

Extract a custom hook when stateful logic repeats across 2+ components, or when a component has
too many `useEffect` blocks to follow. **Check `@mantine/hooks` first** —
`useDebouncedValue`, `useDisclosure`, `useClickOutside`, `useElementSize`, `useLocalStorage`,
`useMediaQuery` already exist.

Stable return shape per hook (tuple or object, not mixed). More than 2–3 `useEffect`s in one
component is a smell — the state usually belongs in TanStack Query, a route loader, or an event
handler rather than an effect. Never synchronize derived state in an effect; compute it during
render.

## TypeScript

Strict mode stays on; no JS in `src/`. Beyond what ESLint already enforces:

- **Never `any`** — use `unknown` and narrow. Never `@ts-ignore` / `@ts-nocheck`; surface the
  typing problem instead.
- `type` over `interface` for props and most shapes.
- **Discriminated unions for variant props** — catches invalid prop combinations at compile time.
- `satisfies` for config objects when you want literal-type narrowing.
- `ReactNode` (not `JSX.Element`) for `children`.
- Path alias `@/*` → `src/*` is wired. Use it for everything from `src/`; reserve relative
  imports for same-directory siblings.
- Env vars are `VITE_*` and ship in the bundle — **never put a secret there**. Runtime config
  goes through `/config.js` instead (see `frontend/CLAUDE.md`).

## Accessibility

Mantine's defaults are good; don't undermine them. Every input gets a real `label` (not an
`aria-label` substitute unless layout precludes it). **`ActionIcon` and every icon-only control
takes `aria-label`, always.** Never communicate state by color alone — pair with icon or text.
Don't trap focus, don't set `tabIndex > 0`, don't put `onClick` on a non-button without keyboard
handling (better: use `Button` / `ActionIcon`). Tab through the page when verifying a UI change.

## Error boundaries

Route-level via React Router's `errorElement` — a `RouteErrorFallback` reading `useRouteError()`
renders message plus retry. Component-level boundaries only around genuinely risky isolated
regions (charts, third-party widgets), never blanket. `useSuspenseQuery` pairs well with Suspense
once nested orchestration gets fiddly; for v0, plain `useQuery` with `isPending` / `error` is fine.

## Testing

**No frontend tests exist yet, and Vitest/RTL/MSW are not installed** — there is no `test` script
in `package.json`. Installing them is part of writing the first test. The conventions are decided:

- Co-locate: `Foo.tsx` → `Foo.test.tsx`.
- **Test behavior, not implementation.** Render, interact with `userEvent`, assert via
  `getByRole` / `getByLabelText`. `getByTestId` is a last resort.
- **Mock at the network boundary with MSW.** Intercept `/api/*` and return canned responses.
  **Never `vi.mock` a query or mutation hook** — the hook is part of the contract under test, and
  mocking it means the test passes while the real call is broken.
- Build a `renderWithProviders` helper wrapping `MantineProvider` + `QueryClientProvider`, matching
  the provider stack in `main.tsx`.
- Don't test render-only components or generated code. Snapshot tests are a smell for anything
  that changes often.

## Comments

**Default to no comments; 2 lines or less when you write one.** If it needs three, the code is
the wrong shape — fix it, or lift the explanation into this skill. Never restate a project rule
in a code comment. A comment earns its place only when the *why* is non-obvious.
