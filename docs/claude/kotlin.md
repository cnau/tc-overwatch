# Kotlin Conventions

Shared guidance for Kotlin code. Imported by `server/CLAUDE.md` (and any future Kotlin module).

## Tech baseline

- Kotlin 2.2.x
- JDK 21+ (Spring Boot 4 minimum); local toolchain currently 23 per scaffold notes
- Compiler args: `-Xjsr305=strict` (treat JSR-305 nullability annotations as errors)

## Code style

- **ktlint + detekt** enforced in CI. Run locally before pushing:
  ```
  ./gradlew :server:ktlintCheck :server:detekt
  ./gradlew :server:ktlintFormat   # auto-fix
  ```
- Prefer imports over inline fully-qualified names.
- Files end with a trailing newline.
- Use the `compilerOptions { ... }` DSL in Gradle (Kotlin 2.x); the older `kotlinOptions { ... }` is deprecated.

## Idioms

- **Data classes** for DTOs. No setters; use `copy()` for mutation-by-derivation.
- **Constructor injection** everywhere — no `@Autowired` field injection, no `lateinit var` for dependencies.
- **Immutable by default**: `val` over `var`, `List` / `Map` over their mutable variants in public APIs. Reach for `MutableList` only inside a function body where the build pattern is genuinely clearer.
- **Null safety**: model nullability in types, not via `!!`. A `!!` is a code smell — if the value is guaranteed non-null, return type should say so; if it isn't, handle the null case explicitly.
- **Scope functions** (`let`, `also`, `apply`, `run`, `with`) — use them where they clarify intent, not as a habit. `apply` for builder-style configuration; `let` for null-safe transforms; `also` for side effects in a chain.
- **Extension functions** when they make a call site read more naturally. Don't extend types you don't own with project-specific behavior — wrap, don't extend.
- **`when` exhaustiveness** for sealed types and enums. The compiler enforces it when `when` is used as an expression; prefer that form when you want the check.

## Visibility

- Default to `internal` for module-private APIs when the Kotlin compiler will let you. Where a public class consumes an internal type as a constructor parameter (e.g. DAOs taking entities — see scaffold notes), enforcement falls back to code review. Don't fight the compiler; document the boundary in the relevant `CLAUDE.md`.

## Coroutines

Not introduced in v0. Spring's `@Async`, `@Scheduled`, and JPA transactional boundaries are blocking by design. If a future feature genuinely needs structured concurrency, surface the case before pulling `kotlinx.coroutines` in — it interacts with `@Transactional` propagation in non-obvious ways.

## Anti-patterns to avoid

- `!!` operators in production code (test fixtures may use them sparingly when the test setup guarantees non-null).
- `lateinit var` on dependency-injected fields — use constructor injection.
- Top-level mutable global state (`var` at file or `object` level).
- Hand-rolling builders when a data class + `copy()` suffices.
- Using `Any?` to dodge type design — make the type explicit.
