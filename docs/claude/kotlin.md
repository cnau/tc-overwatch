# Kotlin Conventions

Shared guidance for Kotlin code. Imported by `server/CLAUDE.md` (and any future Kotlin module).

Kotlin 2.2.x, JDK 21+ (local toolchain 23 per scaffold), `-Xjsr305=strict`.

## Code style

ktlint + detekt enforced in CI: `./gradlew :server:ktlintCheck :server:detekt`; auto-fix with `:server:ktlintFormat`. Files end with a trailing newline. Gradle uses `compilerOptions { }` (Kotlin 2.x); older `kotlinOptions { }` is deprecated.

## Comments

**Default to no comments. When you do write one, keep it to 2 lines or less.** If it needs three lines, the code is the wrong shape — fix it, or lift the explanation to the relevant rule file in `docs/claude/`. Restating a project rule in a code comment is forbidden; the rule docs (`spring-boot.md`, `liquibase.md`, this file) are the source of truth. A comment earns its place only when the **why** is genuinely non-obvious — a hidden constraint, a workaround for a specific bug, an invariant a reader would otherwise miss. Don't explain what the code does; identifiers do that.

## Idioms

- **Data classes** for DTOs. No setters; use `copy()` for derivation. Don't add behavior to data classes — keep them dumb.
- **Constructor injection** everywhere — no `@Autowired` field injection, no `lateinit var` for dependencies.
- **Immutable by default**: `val` over `var`, `List` / `Map` over their mutable variants in public APIs. Reach for `MutableList` only inside a function body where the build pattern is genuinely clearer.
- **Null safety**: model nullability in types, not via `!!`. A `!!` is a code smell — if the value is guaranteed non-null, the return type should say so; if it isn't, handle the null case explicitly.
- **Sealed classes / sealed interfaces** for modeling discriminated states (success/failure, loaded/loading/error, an enum-like type that needs per-variant data). The compiler enforces `when` exhaustiveness when the result is used as an expression. Prefer sealed types over throwing exceptions for *expected* alternative outcomes.
- **Scope functions** (`let`, `also`, `apply`, `run`, `with`) — use them where they clarify intent, not as a habit. `apply` for builder-style configuration; `let` for null-safe transforms; `also` for side effects in a chain.
- **Extension functions** when they make a call site read more naturally. Don't extend types you don't own with project-specific behavior — wrap, don't extend.
- **`when` exhaustiveness** for sealed types and enums. The compiler enforces it when `when` is used as an expression; prefer that form when you want the check.
- **`value class`** (formerly inline class) for type-safe primitive wrappers — `value class TenantId(val value: UUID)` makes "tenant id" a distinct type from "any old UUID" at compile time with zero runtime cost. Use sparingly; only where a primitive being passed to the wrong parameter would actually be a bug.
- **`Result<T>` / `runCatching`** — useful for *expected* failure paths at API boundaries (parsing, external calls). Don't use them for control flow inside the service layer where exceptions are clearer.

## Visibility

- Default to `internal` for module-private APIs when the Kotlin compiler will let you. Where a public class consumes an internal type as a constructor parameter (e.g. DAOs taking entities — see scaffold notes), enforcement falls back to code review. Don't fight the compiler; document the boundary in the relevant `CLAUDE.md`.

## Coroutines

v0 is uniformly blocking (Spring MVC → service → JDBC). Don't introduce `kotlinx.coroutines` — Spring 6+ supports them well, but mixing suspending controllers with blocking JPA creates surprise edges. If a real fan-out need appears, surface it explicitly.

## Anti-patterns

- `!!` in production (test fixtures sparingly OK when setup guarantees non-null).
- Top-level mutable global state (`var` at file or `object` level).
- Hand-rolled builders when `copy()` suffices.
- `Any?` to dodge type design.
