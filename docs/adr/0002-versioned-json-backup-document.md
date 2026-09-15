# ADR-0002: Versioned JSON backup document owned by BackupRepository

- **Status:** Accepted (2026-09-15)
- **Deciders:** Snake Tracker maintainers
- **Supersedes:** none

## Context

The tracker's whole model lives in one local Room database and there is no way
to move it between devices. Import/export needs a file format that survives app
upgrades, so the format decision has to be recorded before the import (issue
#26), file I/O (issue #27) and UI slices build on top of it.

Two constraints shape the choice:

- Room entities are not a stable wire contract. Their fields follow the schema,
  and the app's DB migration policy is destructive (`fallbackToDestructiveMigration`),
  so exporting entities directly would tie every past backup file to a future
  DB schema.
- Restore must be able to put rows back with their identities intact: feeding,
  shed and weight rows reference `snakeId`, and a feeding can reference a food
  stock item. Renumbering on export would destroy those relationships.

## Decision

1. **A versioned JSON document, not a database dump.** The backup file is one
   `BackupDocument` (`data/backup/BackupDocument.kt`) with a locked
   `schemaVersion` (currently `1`), the exporting `appVersion`, an ISO-8601
   `exportedAt` instant, and five row arrays: `snakes`, `feedings`, `sheds`,
   `weights`, `foodStock`.
2. **Wire DTOs mirror the entities; entities stay out of the format.** Separate
   `@Serializable` row types carry the payload, with `toRow()` / `toEntity()`
   mapping beside them. No Room annotation reaches the file, and no DAO learns
   about JSON.
3. **Ids verbatim, row dates as epoch millis.** Row payloads keep their original
   Room ids and keep date fields as epoch millis (the entity representation), so
   a restore can re-insert rows without remapping foreign keys. Only the
   envelope's `exportedAt` is human-readable ISO-8601.
4. **`BackupRepository` is the only module that knows the format.** It takes
   `AppDatabase`, exposes `exportAll(): String`, and reads all five tables
   inside one Room transaction (`withTransaction`) so a file is a consistent
   snapshot. The existing `Repository` CRUD surface is unchanged; later slices
   (import, file I/O, UI) go through this module rather than learning the
   schema themselves.
5. **Serialization is `kotlinx.serialization` with the compiler plugin**
   (`org.jetbrains.kotlin.plugin.serialization`, same version as the Compose
   compiler plugin, which AGP 9's built-in Kotlin already accepts) plus
   `kotlinx-serialization-json`. Encoding uses `encodeDefaults = true` so every
   field is written even when it matches its default.

## Consequences

- A schema change means a new `schemaVersion` plus a reader that accepts the
  older versions — import (issue #26) must branch on `schemaVersion`, never
  assume the file matches the current entity shape.
- Adding a sixth table means adding a row array to `BackupData` and bumping the
  schema version; old files stay valid because unknown-shape readers are the
  import side's problem, not the writer's.
- Optional fields round-trip as explicit JSON `null` (e.g. `birthDate`,
  `foodStockItemId`), so an import can distinguish "no value" from "absent".
- Export holds a database transaction for the duration of the five reads; the
  document is built in memory before encoding, and file writing (issue #27)
  happens outside the transaction.
- `java.time` (`Clock`, `Instant`) is used for the export timestamp, with the
  clock injectable so tests pin `exportedAt`.

## Alternatives considered

- **Copy the SQLite file:** rejected — a DB dump is bound to the Room schema the
  app reserves the right to destructively migrate, and it is opaque to users.
- **Serialize Room entities directly:** rejected — couples every archived backup
  file to the current entity shape and drags persistence annotations into the
  wire format.
- **CSV per table in a zip:** rejected — five files plus foreign-key coupling by
  hand, for no readability gain over one JSON document.
