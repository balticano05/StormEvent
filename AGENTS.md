# Project rules

## Git

- **Commit messages must be in English only.** No Russian/Cyrillic text in the subject or body.
- Keep the existing prefix style: `ADD: `, `FIX: `, `DOC: `, `REFACTOR: `, `CHORE: `.
- Subject line is imperative, short, and describes what changed — not the process.
- **Never commit `.env`, `*.env`, `.env.*` files** — they are in `.gitignore`. Secrets (DB passwords, API keys, admin keys) must stay local.

## Docs

- Documentation under `docs/` is written in Russian. This does not apply to commits or to code.

## Null safety

- `mvn verify` runs SpotBugs (`storm.event/config/spotbugs-null.xml`) and **fails the build** on any null-safety finding. Run it before pushing.
- Rule: never dereference a reference that can be null. Carry genuinely optional values as `java.util.Optional` until the null is either rejected or replaced by a default.
- Do not use `Optional` as a field type in entities, DTOs or anything Jackson, Lombok or JDBC binds — use `Optional` at the call site instead.
- Prefer `Optional.ofNullable(x).map(...)` chains over `if (x != null)`.
- Do not annotate nullability with `org.jetbrains.annotations` for SpotBugs' sake: SpotBugs cannot read them. Use `edu.umd.cs.findbugs.annotations.Nullable` (`spotbugs-annotations`, `provided` scope) when a contract must be enforced by the rule.
- Parsers must read scraped markup through `com.workspace.storm.event.parser.Parsers` (`cell`, `text`, `ownText`, `attr`, `toInt`, `toDecimal`) rather than indexing `Elements` or calling `Element.text()` directly.
- JDBC: read UUID columns with `rs.getObject(name, UUID.class)` and nullable numbers with `rs.getObject(name, Integer.class)`; never `UUID.fromString(rs.getString(...))` or casting `rs.getObject`.
