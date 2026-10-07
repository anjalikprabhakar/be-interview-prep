---
name: db-migration
description: Create a safe Flyway database migration for a schema change (new table, column, index, constraint, data backfill). Use when an API change needs a schema change or the user asks for a migration.
argument-hint: <description of schema change>
---

# DB migration

Change: `$ARGUMENTS`

1. Find the migration folder (default `src/main/resources/db/migration`) and the highest existing `V<n>__` number. If Flyway isn't in `pom.xml`, tell the user and offer to add `flyway-core` (plus the DB-specific module).
2. Create `V<n+1>__<snake_case_description>.sql`. **Never modify an applied migration.**
3. Safety rules:
   - Add new columns as nullable or with a default, so you don't lock or break existing rows.
   - Make renames or drops in two steps (expand → migrate code → contract in a later ticket), and call this out.
   - Name constraints and indexes explicitly: `fk_<table>_<ref>`, `uk_<table>_<cols>`, `idx_<table>_<cols>`.
   - Add indexes on foreign keys and on columns used in lookups or filters.
   - Keep data backfills in a separate migration from DDL where you can.
4. Update the matching JPA entity so it matches exactly (column names, nullability, lengths).
5. Verify with `./mvnw -q verify`. If there are `@DataJpaTest`/Testcontainers tests, they exercise the migration.
6. Report the SQL, any rollback notes, and whether it's backward compatible with the currently deployed code.
