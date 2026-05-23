# React Conventions

Operational guidance for the React/TypeScript frontend. Stack pinned in `architecture.md` § Frontend.

Installed (scaffold): React 19, TypeScript 5.7, Vite 6, ESLint 9 flat config.

Pinned, install per-feature: Connect-ES + **Connect-Query** (primary path; auto-generates query/mutation hooks per RPC), TanStack Query v5+, Mantine v8+ (`@mantine/core` + `@mantine/hooks`; sub-packages per-feature), React Router v6.4+ Data Router, react-hook-form + Zod (`@hookform/resolvers/zod`), Vitest + RTL + user-event + MSW or `createRouterTransport` for transport-level mocking.

## TypeScript

TypeScript only. No JS in `src/`. Strict mode stays on.

- Never `any` — use `unknown` and narrow.
- Never `@ts-nocheck` / `@ts-ignore` — surface the typing problem instead.
- No `React.FC` / `React.FunctionComponent` — annotate the props type directly.
- `type` over `interface` for component props and most shapes.
- Discriminated unions for variant props (mutually exclusive shapes — catches invalid combos at compile time).
- `satisfies` for config-like objects when you need the literal-type narrowing.
- `ReactNode` (not `JSX.Element`) for `children`.

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

Four buckets, pick the right one:

1. **Server state → Connect-Query / TanStack Query.** Anything from the backend. The hook *is* the state — don't shadow it in `useState`.
2. **URL state → `useSearchParams` / route params.** Filters, tabs, selected row id, pagination, open drawer. Linkable, refresh-safe, decouples siblings.
3. **Local component state → `useState` / `useReducer`.** Form drafts, hover, transient UI flags. Use `useReducer` when 3+ related values change together (wizards, multi-flag form state — model as a sealed-state reducer).
4. **Cross-tree shared → React Context.** Auth user, theme, transport. Not for short-distance prop drilling.

No global store (no Redux, Zustand, Jotai).

## Connect-Query

Primary backend-call path: import the generated `useQuery` / `useMutation` from `@/gen/.../<service>-<Service>_connectquery` and pass the generated RPC reference + params. Cache keys are managed automatically; invalidate via `<rpc>.getQueryKey()`.

```ts
const { data, isPending, error } = useQuery(list, { /* params */ })

const labelEmail = useMutation(label, {
  onSuccess: () => queryClient.invalidateQueries({ queryKey: list.getQueryKey() }),
})
```

App wraps in `<TransportProvider transport={transport}><QueryClientProvider client={queryClient}>...</...>...</...>`. Transport: `createConnectTransport({ baseUrl: '/rpc', credentials: 'include' })` — `credentials: 'include'` attaches the session cookie (cross-origin in prod, see `architecture.md` § CORS). Vite proxy maps `/rpc/*` → backend in dev.

**Manual fallback**: only when Connect-Query can't express the case (streaming, custom cache shapes). Wrap `createPromiseClient` in a `useFoo` hook in `src/api/<domain>.ts`. Components never call generated clients directly.

## Components

Function components, one per file, default-exported, PascalCase filename. Destructure props at the signature. `ref` is a regular prop in React 19 (no `forwardRef`). Memoize only when measurements show it matters.

### Modular component design

Default mindset: **small contracts, composition over configuration.** Reusability is the *outcome* of good design — don't pursue it by adding props.

**Three tiers, different reuse expectations:**

| Tier | Where | Reuse | Example |
|---|---|---|---|
| Primitives | `src/components/primitives/` | Wide | `AppButton`, `Money` (Mantine wrappers w/ project defaults) |
| Composite | `src/components/<kind>/` | 2–N places | `StatCard`, `EmptyState`, `ConfirmDialog` |
| Domain | `src/components/<domain>/` or co-located | 1–2 places | `TransactionRow`, `EmailTriagePanel` |

A `TransactionRow` reused in two places is still domain, not a primitive. Don't over-promote.

**Composition over configuration.** A component with 8 boolean props is a smell. Use `children` / compound components instead:

```tsx
<Card>
  <Card.Header><Title>Foo</Title></Card.Header>
  <Card.Body>...</Card.Body>
  <Card.Footer><Button>Save</Button></Card.Footer>
</Card>
```

- Default to accepting `children`.
- Multiple regions → compound components (`<Foo.X>`, Mantine-style) over render-prop slots.
- One consumer needs a tweak that the other four don't → wrap locally, don't add a prop.

**Compound components**: dot-namespaced sub-components sharing state via internal Context. This is how Mantine's `Tabs` / `Accordion` / `Menu` work — follow the same shape.

**Pass-through with rest spread** when wrapping Mantine — keep the underlying API accessible:

```tsx
function AppButton({ variant = 'primary', ...rest }: AppButtonProps) {
  return <MantineButton color={variant === 'danger' ? 'red' : 'blue'} {...rest} />
}
```

**Stable, minimal APIs.** Start with the props you actually need. Don't expose internal state shape (5 `useState` hooks ≠ 5 `onChange` props). No leaky abstractions — a primitive that takes Mantine `sx` is leaking Mantine.

**Split when**: file > ~200 lines, JSX > 4 levels deep, > 5 unrelated `useState`s, or a clear independent sub-component name suggests itself.

**Container vs presentational** as a loose convention: data-fetching (queries, route loaders) at the container; rendering component takes already-fetched data as props. Makes the renderer trivially testable without mocking transports.

## Routing

`createBrowserRouter(routes)` in `src/router/routes.tsx`; `<RouterProvider />` in `App.tsx`. Pages in `src/pages/` eagerly imported. Use route `loader`s for data that must be present before render — call `queryClient.fetchQuery` from a loader and the component reads via `useQuery` with an instant cache hit. `<Outlet />` for nested layouts. `<Link>` / `useNavigate()` for in-app navigation — never raw `<a href>`.

## Mantine

Setup per Mantine's guide: `@mantine/core` + `@mantine/hooks`, import `@mantine/core/styles.css`, wrap in `<MantineProvider theme={theme}>`, add `postcss-preset-mantine` to `postcss.config.cjs`. Theme lives in `src/theme/`.

- **Components first** — Mantine covers Button/TextInput/Select/Modal/Drawer/Notification/etc. Reach for these before writing custom.
- **Layout primitives** (`Stack`, `Group`, `Flex`, `Grid`, `SimpleGrid`, `Container`) — use them over raw `<div>` when they express the intent.
- **`style` prop** for one-off tweaks; **CSS Modules** (`Foo.module.css`, co-located) for anything reusable.
- **`rem` units**, theme tokens — never hardcode colors / spacing / font sizes.
- **No additional styling systems.** No Tailwind, styled-components, emotion, Sass.

## Forms

- react-hook-form for any form > 2 fields or with cross-field validation. Zod schemas via `@hookform/resolvers/zod` — Zod mirrors the backend service-DTO shape, which is the source of truth.
- **Never use `@mantine/form`** — react-hook-form is the project standard.
- Mantine inputs wire to RHF via `Controller`. `register()` is fine for 1-2 plain text fields; `Controller` for anything with non-`input` API (`Select`, `DateInput`, `MultiSelect`, etc.).

**Reusable field wrappers** in `src/components/forms/` once the second form lands — extract typed `RhfTextInput<T>`, `RhfSelect<T>`, etc. that hide the `Controller` boilerplate. The wrapper takes `name: FieldPath<T>`, pulls `control` from `useFormContext`, spreads `field` into the Mantine input, and surfaces `fieldState.error?.message` as `error`. Build out the family as you need each; don't pre-fabricate.

## Hooks

- Extract a custom hook when stateful logic repeats across 2+ components or a component has too many `useEffect` blocks to follow.
- Check `@mantine/hooks` first — `useDebouncedValue`, `useDisclosure`, `useClickOutside`, `useElementSize`, `useLocalStorage`, `useMediaQuery`, and others are already there.
- Naming: `useXxx` for accessors, `useXxxMutation` for mutations (matches Connect-Query).
- Stable return shape per hook (tuple or object, not mixed).
- > 2-3 `useEffect`s in one component is a smell — usually means state belongs in TanStack Query / route loaders / event handlers, not effects.

## Accessibility

Mantine has solid defaults; don't undermine them.

- Every input has a `label` — don't substitute `aria-label` unless the layout precludes a visible label.
- `ActionIcon` (and any icon-only control) takes `aria-label`. Always set it.
- Never communicate state with color alone — pair with an icon or text.
- Don't trap focus, don't set `tabIndex > 0`, don't put `onClick` on a non-button without keyboard handlers (better: just use `Button` / `ActionIcon`).
- Tab through the page when verifying a UI change.

## Error boundaries and Suspense

- **Route-level error boundaries** via React Router's `errorElement`. A `RouteErrorFallback` reads `useRouteError()` and renders message + retry.
- **Component-level error boundaries** (`react-error-boundary` or DIY) around isolated risky regions (charts, third-party widgets). Specific, not blanket.
- **Suspense + `useSuspenseQuery`** pairs cleanly for declarative loading states once nested orchestration gets fiddly. For v0, plain `useQuery` with `isPending` / `error` checks is fine.

## Testing

- Test files co-located: `Foo.tsx` → `Foo.test.tsx`.
- Test behavior, not implementation. Render, interact via `userEvent`, assert via `getByRole` / `getByLabelText`. `getByTestId` is last resort.
- **Mock at the transport boundary.** Either MSW (HTTP interception, good for realistic network behavior) or `createRouterTransport` from `@connectrpc/connect` (typed handlers, faster for unit tests). **Never `vi.mock` generated Connect-Query hooks** — it bypasses the contract.
- Build a `renderWithProviders` helper that wraps in `TransportProvider` + `QueryClientProvider`.
- Don't test trivial render-only components or generated code. Snapshot tests are a smell for anything that changes often.

## Conventions

- Files: `PascalCase.tsx` for components (default export), `useXxx.ts` for hooks, `XxxPage.tsx` for pages, `src/api/<domain>.ts` for API wrappers.
- Constants: `SCREAMING_SNAKE_CASE`.
- Path alias `@/*` → `src/*` is the *target* but not yet wired (needs `tsconfig.app.json` paths + `vite.config.ts` `resolve.alias` + `@types/node`). Until then, relative imports are fine in the small scaffold tree.
- Env vars prefixed `VITE_*`; ship in the bundle so never put real secrets there.

## Anti-patterns

- Fetching in `useEffect` when a Connect-Query hook works.
- Server state in Context or `useState`.
- Components calling generated Connect clients directly — go through `src/api/`.
- Raw `fetch` / `axios` to the backend.
- `any` / `@ts-nocheck` / `@ts-ignore` / `React.FC`.
- New state libraries (Redux, Zustand, Jotai).
- `vi.mock` on Connect-Query hooks (mock the transport).
- Mixing Mantine with Tailwind / styled-components / emotion / Sass.
- `@mantine/form` (use react-hook-form).
- Re-implementing a Mantine primitive that's themable.
- Hardcoded colors / spacing / `px` — use theme tokens + `rem`.
- 8+ boolean props on a shared component — redesign as composition.
- Promoting to `src/components/` for hypothetical future reuse.
- Prop drilling > 2 levels.
- Memoizing by reflex.
- Effects synchronizing derived state (compute during render).
- `ActionIcon` without `aria-label`.
