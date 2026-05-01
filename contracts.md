# ContextOS — Shared Architecture Contracts

> **Audience:** Every contributor building features against the ContextOS platform.  
> **Rule:** If code contradicts this document, the code is wrong. Open a PR to update the
> document only when the architecture decision itself changes.

---

## Table of Contents

1. [SituationModel Schema](#1-situationmodel-schema)
2. [Skill Interface Contract](#2-skill-interface-contract)
3. [SkillResult Contract](#3-skillresult-contract)
4. [Room Database Schema](#4-room-database-schema)
5. [Module Dependency Graph](#5-module-dependency-graph)
6. [Naming Conventions](#6-naming-conventions)
7. [Cycle Budget](#7-cycle-budget)

---

## 1. SituationModel Schema

`SituationModel` is the **single source of truth** passed to every `Skill` on each
agent cycle. It is constructed by the `:core:service` layer, serialized to JSON for
logging, and never mutated after construction.

**Package:** `com.contextos.core.data.model`  
**Serialization:** `@kotlinx.serialization.Serializable`

### 1.1 Top-level Fields

| Field | Kotlin Type | Source | Description |
|---|---|---|---|
| `currentTime` | `Long` | System clock | Epoch millis at the moment the cycle begins. Used as the reference timestamp for all relative time calculations within the cycle. |
| `currentLocation` | `LatLng?` | GPS / FusedLocationProvider | Last known WGS-84 coordinate. `null` if location permission is not granted or a fix has not been obtained. |
| `batteryLevel` | `Int` (0–100) | `BatteryManager` | Integer battery percentage. Skills that involve screen-on actions should check this field and suppress when `< 15`. |
| `isCharging` | `Boolean` | `BatteryManager` | `true` if the device is connected to a charger (AC, USB, or wireless). |
| `nextCalendarEvent` | `CalendarEventSummary?` | Calendar cache / Google Calendar API | The single soonest upcoming event. `null` if the calendar is empty or the user has not granted calendar access. |
| `upcomingCalendarEvents` | `List<CalendarEventSummary>` | Calendar cache | All events within the next 8 hours, sorted by `startTime` ascending. Empty list when unavailable. |
| `recentAppUsage` | `List<AppUsageEntry>` | `UsageStatsManager` | Apps the user has interacted with in the past hour, sorted by `usageTimeMs` descending. Requires `PACKAGE_USAGE_STATS` permission. |
| `ambientAudioContext` | `AmbientAudioContext` | Audio classifier | Classification of the acoustic environment. Defaults to `UNKNOWN`. |
| `memorySummary` | `String` | `:core:memory` layer | A short natural-language digest produced by the memory managers (routines, preferences, location labels). Empty string until Phase 3.x. |
| `recommendedActions` | `List<ActionRecommendation>` | Agent scoring layer | Skills that the agent has pre-scored as likely to be approved, sorted by `confidence` descending. Populated during the computation phase. |
| `locationLabel` | `String` | `LocationMemoryManager` | Human-readable label for `currentLocation` (e.g., `"Home"`, `"Office"`, `"Gym"`). Falls back to `"Unknown"`. |
| `wifiSsid` | `String?` | `WifiManager` | SSID of the currently connected Wi-Fi network, or `null` if not connected or permission not granted. |
| `isMobileDataConnected` | `Boolean` | `ConnectivityManager` | `true` if the device has an active mobile data connection. |

### 1.2 Nested Types

#### `LatLng`

| Field | Type | Description |
|---|---|---|
| `latitude` | `Double` | WGS-84 latitude in decimal degrees (-90.0 … 90.0). |
| `longitude` | `Double` | WGS-84 longitude in decimal degrees (-180.0 … 180.0). |

#### `CalendarEventSummary`

| Field | Type | Nullable | Description |
|---|---|---|---|
| `eventId` | `String` | No | Stable Google Calendar event ID. Also used as the primary key in `calendar_event_cache`. |
| `title` | `String` | No | Event title as returned by the Calendar API. |
| `location` | `String?` | Yes | Free-form location string, or `null` if not set. |
| `startTime` | `Long` | No | Epoch millis of the event start. |
| `endTime` | `Long` | No | Epoch millis of the event end. |
| `attendees` | `List<String>` | No | Email addresses of all attendees. Empty list if not available. |
| `meetingLink` | `String?` | Yes | Video-call URL (Google Meet, Zoom, etc.) extracted from the event description, or `null`. |
| `isVirtual` | `Boolean` | No | `true` if a `meetingLink` was found. Derived field — do not set manually. |

#### `AppUsageEntry`

| Field | Type | Description |
|---|---|---|
| `packageName` | `String` | Android package identifier (e.g., `com.google.android.gm`). |
| `appName` | `String` | Human-readable application name. |
| `usageTimeMs` | `Long` | Foreground usage duration in milliseconds over the past hour. |

#### `AmbientAudioContext` (enum)

| Value | Meaning |
|---|---|
| `SILENT` | No significant audio detected; likely quiet environment. |
| `CONVERSATION` | Human speech detected; user may be in a meeting or call. |
| `MUSIC` | Music or structured audio detected. |
| `AMBIENT_NOISE` | General background noise (traffic, crowds, etc.). |
| `UNKNOWN` | Audio classification not available or insufficient data. |

#### `ActionRecommendation`

| Field | Type | Description |
|---|---|---|
| `skillId` | `String` | Stable skill ID matching the `Skill.id` property. |
| `confidence` | `Float` (0.0–1.0) | Agent's estimated confidence that this action will be approved. |
| `reasoning` | `String` | Short human-readable explanation of why this action was recommended. |

---

## 2. Skill Interface Contract

**Package:** `com.contextos.core.skills`  
**Interface:** `com.contextos.core.skills.Skill`

```ContextOS/core/skills/src/main/kotlin/com/contextos/core/skills/Skill.kt#L1-35
package com.contextos.core.skills

import com.contextos.core.data.model.SituationModel

interface Skill {
    val id: String
    val name: String
    val description: String

    fun shouldTrigger(model: SituationModel): Boolean

    suspend fun execute(model: SituationModel): SkillResult
}
```

### 2.1 Rules for `shouldTrigger()`

| Rule | Rationale |
|---|---|
| **Must be pure** | No side effects, no I/O, no mutations. |
| **Must be fast** | The agent calls `shouldTrigger()` on every registered skill every cycle. Any I/O here blocks the entire evaluation pass. |
| **No coroutines** | The function signature is non-suspending intentionally. If you need async data, pre-fetch it into `SituationModel` before the evaluation loop. |
| **No exceptions** | Catch internally; returning `false` is always safe. An uncaught exception aborts the entire cycle for all skills. |
| **Deterministic** | Given the same `SituationModel`, must always return the same result. |

### 2.2 Rules for `execute()`

| Rule | Rationale |
|---|---|
| **Must complete within the cycle budget** | Skills have at most **12 seconds** of wall-clock time (computation phase). Exceed this and the dispatcher will cancel the coroutine. |
| **Must handle cancellation** | Use `kotlinx.coroutines` cooperative cancellation; check `isActive` or prefer `withContext(Dispatchers.IO)` calls which are cancellation-aware. |
| **Must return a `SkillResult`** | Never throw from `execute()`. Wrap exceptions in `SkillResult.Failure`. |
| **No UI thread access** | `execute()` runs on a background dispatcher. Never post to the main thread; communicate results only via `SkillResult`. |

### 2.3 Statelessness Rule

> **Skills must be stateless.** An instance may be reused across many cycles.

- All persistent state goes into **Room** via the `:core:data` repository layer.
- All transient cycle state is passed in via **`SituationModel`**.
- In-memory fields on a `Skill` class are forbidden (except compile-time constants).

### 2.4 Skill Registration

Skills are registered via a **Hilt multi-binding set**. In your skill's DI module:

```ContextOS/core/skills/src/main/kotlin/com/contextos/core/skills/di/SkillsModule.kt#L1-5
// Example binding — replace MySkill with your implementation:
@Binds @IntoSet
abstract fun bindMySkill(impl: MySkill): Skill
```

The `SkillRegistry` collects all bound skills automatically.

---

## 3. SkillResult Contract

**Package:** `com.contextos.core.skills`  
**Sealed class:** `com.contextos.core.skills.SkillResult`

### 3.1 Variants

#### `SkillResult.Success`

```ContextOS/core/skills/src/main/kotlin/com/contextos/core/skills/SkillResult.kt#L8-12
data class Success(
    val description: String,
    val outcome: ActionOutcome = ActionOutcome.SUCCESS,
) : SkillResult()
```

**When to use:** The skill completed its action without requiring user input.

| Field | Description |
|---|---|
| `description` | Short past-tense summary written for the action log (e.g., `"Set DND until 10:00 AM"`). |
| `outcome` | Should be `ActionOutcome.SUCCESS`. Override only in unusual situations. |

---

#### `SkillResult.PendingConfirmation`

```ContextOS/core/skills/src/main/kotlin/com/contextos/core/skills/SkillResult.kt#L14-19
data class PendingConfirmation(
    val description: String,
    val confirmationMessage: String,
    val pendingAction: suspend () -> SkillResult,
) : SkillResult()
```

**When to use:** The action is potentially disruptive and should be shown to the user
before executing (e.g., sending a message, modifying a calendar event).

| Field | Description |
|---|---|
| `description` | What will happen if the user confirms, written in future tense. |
| `confirmationMessage` | Short user-facing prompt shown in the notification or overlay. |
| `pendingAction` | Lambda that performs the actual work when the user confirms. Must also return a `SkillResult` (typically `Success` or `Failure`). |

**Contract:** The dispatcher will call `pendingAction` only after explicit user approval.
Do not put irreversible side effects outside this lambda.

---

#### `SkillResult.Failure`

```ContextOS/core/skills/src/main/kotlin/com/contextos/core/skills/SkillResult.kt#L21-26
data class Failure(
    val description: String,
    val error: Throwable? = null,
    val outcome: ActionOutcome = ActionOutcome.FAILURE,
) : SkillResult()
```

**When to use:** Something went wrong that the skill could not recover from.

| Field | Description |
|---|---|
| `description` | Human-readable failure reason logged to `action_log`. |
| `error` | The originating exception, if any. Used for crash telemetry. |
| `outcome` | Should be `ActionOutcome.FAILURE`. |

---

#### `SkillResult.Skipped`

```ContextOS/core/skills/src/main/kotlin/com/contextos/core/skills/SkillResult.kt#L28-30
data class Skipped(val reason: String) : SkillResult()
```

**When to use:** `shouldTrigger()` returned `true` but by the time `execute()` ran,
the triggering condition had changed (race condition between evaluation and execution).

| Field | Description |
|---|---|
| `reason` | Short explanation for the log (e.g., `"Event already started"`, `"Battery charged above threshold"`). |

---

## 4. Room Database Schema

**Database class:** `com.contextos.core.data.db.ContextOSDatabase`  
**Version:** 1  
**Package:** `com.contextos.core.data.db`

> **Rule:** All queries must be `suspend` functions or return `Flow`. No blocking
> calls on the main thread. All DAO methods are defined in
> `com.contextos.core.data.db.dao`.

---

### 4.1 `action_log`

Entity: `com.contextos.core.data.db.entity.ActionLogEntity`

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `INTEGER` | No (PK, autoincrement) | Surrogate primary key. |
| `timestampMs` | `INTEGER` | No | Epoch millis when the skill executed. |
| `skillId` | `TEXT` | No | Stable skill identifier matching `Skill.id`. |
| `skillName` | `TEXT` | No | Human-readable skill name at time of execution. |
| `description` | `TEXT` | No | Description string from the `SkillResult`. |
| `wasAutoApproved` | `INTEGER` (Boolean) | No | `1` if auto-approved by preference learning; `0` if user manually approved. |
| `userOverride` | `TEXT` | Yes | `"APPROVED"`, `"DISMISSED"`, or `"MODIFIED"`. `null` if auto-approved. |
| `situationSnapshot` | `TEXT` | No | JSON-serialized `SituationModel` at the time of execution. |
| `outcome` | `TEXT` | No | `"SUCCESS"`, `"FAILURE"`, `"PENDING_USER_CONFIRMATION"`, or `"SKIPPED"`. |

---

### 4.2 `user_preferences`

Entity: `com.contextos.core.data.db.entity.UserPreferenceEntity`  
Unique index: `(skill_id)`

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `INTEGER` | No (PK, autoincrement) | Surrogate primary key. |
| `skill_id` | `TEXT` | No | Stable skill identifier. Unique per row. |
| `autoApprove` | `INTEGER` (Boolean) | No | `1` if the user has opted into auto-approval for this skill. |
| `sensitivityLevel` | `INTEGER` | No | Notification sensitivity: `0` = silent, `1` = default, `2` = prominent, `3` = urgent. |

---

### 4.3 `calendar_event_cache`

Entity: `com.contextos.core.data.db.entity.CalendarEventCacheEntity`

| Column | Type | Nullable | Description |
|---|---|---|---|
| `eventId` | `TEXT` | No (PK) | Google Calendar event ID. |
| `title` | `TEXT` | No | Event title. |
| `startTime` | `INTEGER` | No | Epoch millis of event start. |
| `endTime` | `INTEGER` | No | Epoch millis of event end. |
| `location` | `TEXT` | Yes | Free-form location string, or `null`. |
| `attendeesJson` | `TEXT` | No | JSON array of attendee email strings (via `Converters`). |
| `meetingLink` | `TEXT` | Yes | Extracted video call URL, or `null`. |
| `isVirtual` | `INTEGER` (Boolean) | No | `1` if `meetingLink` is non-null. |
| `lastFetched` | `INTEGER` | No | Epoch millis of the last successful API sync. Used for cache invalidation. |

---

### 4.4 `routine_memory`

Entity: `com.contextos.core.data.db.entity.RoutineMemoryEntity`  
Unique index: `(day_of_week, time_slot)`

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `INTEGER` | No (PK, autoincrement) | Surrogate primary key. |
| `day_of_week` | `INTEGER` | No | ISO day-of-week: 1 = Monday … 7 = Sunday. |
| `time_slot` | `TEXT` | No | `"HH:MM"` string quantized to 30-minute blocks (e.g., `"09:00"`, `"09:30"`). |
| `expectedActivity` | `TEXT` | No | Predicted activity label (e.g., `"commuting"`, `"meeting"`, `"gym"`). |
| `confidence` | `REAL` | No | Confidence in [0.0, 1.0]. Entries with `confidence >= 0.7` are considered reliable routines. |
| `observationCount` | `INTEGER` | No | Total number of observations used to compute this entry. |
| `lastObservedMs` | `INTEGER` | No | Epoch millis of the most recent observation. |

---

### 4.5 `preference_memory`

Entity: `com.contextos.core.data.db.entity.PreferenceMemoryEntity`  
Unique index: `(skill_id, context_hash)`

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `INTEGER` | No (PK, autoincrement) | Surrogate primary key. |
| `skill_id` | `TEXT` | No | Stable skill identifier matching `Skill.id`. |
| `context_hash` | `TEXT` | No | SHA-256 hash of the subset of `SituationModel` fields relevant to this skill's trigger condition. |
| `userChoice` | `TEXT` | No | Last recorded choice: `"APPROVED"` or `"DISMISSED"`. |
| `frequency` | `INTEGER` | No | Number of times this (skill, context) combination has been observed. |
| `lastObservedMs` | `INTEGER` | No | Epoch millis of the most recent user interaction. |

---

### 4.6 `location_memory`

Entity: `com.contextos.core.data.db.entity.LocationMemoryEntity`

| Column | Type | Nullable | Description |
|---|---|---|---|
| `latLngHash` | `TEXT` | No (PK) | Truncated geohash or SHA-256 of quantized coordinates used as a cluster key. |
| `centerLatitude` | `REAL` | No | WGS-84 latitude of the cluster centroid. |
| `centerLongitude` | `REAL` | No | WGS-84 longitude of the cluster centroid. |
| `inferredLabel` | `TEXT` | No | Human-readable label: `"Home"`, `"Office"`, `"Gym"`, `"Unknown"`, etc. |
| `visitCount` | `INTEGER` | No | Total number of recorded visits to this cluster. |
| `typicalArrivalTime` | `TEXT` | Yes | `"HH:MM"` string of the most common arrival time, or `null` if unknown. |
| `typicalDepartureTime` | `TEXT` | Yes | `"HH:MM"` string of the most common departure time, or `null` if unknown. |
| `lastVisitedMs` | `INTEGER` | No | Epoch millis of the most recent visit. |

---

## 5. Module Dependency Graph

```ContextOS/contracts.md#L1-1
(see below)
```

```
                          ┌─────────────┐
                          │    :app     │
                          └──────┬──────┘
                                 │ implementation
               ┌─────────────────┼─────────────────┐
               ▼                 ▼                   ▼
       ┌──────────────┐  ┌──────────────┐   ┌──────────────┐
       │ :core:service│  │ :core:skills │   │   (future)   │
       └──────┬───────┘  └──────┬───────┘   └──────────────┘
              │                 │
              │ implementation  │ implementation
              │   ┌─────────────┘
              │   │
              ▼   ▼
       ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
       │ :core:memory │     │ :core:network│     │  (future)    │
       └──────┬───────┘     └──────┬───────┘     └──────────────┘
              │                    │
              │ implementation     │ implementation
              └────────┬───────────┘
                       ▼
              ┌──────────────────┐
              │    :core:data    │
              │  (Room DB, DAOs, │
              │  SituationModel) │
              └──────────────────┘
```

### Module Responsibilities

| Module | Responsibility |
|---|---|
| `:app` | UI layer (Compose screens, ViewModels, Navigation). |
| `:core:service` | `ContextAgentWorker` (WorkManager), cycle orchestration, sensor data collection. |
| `:core:skills` | `Skill` interface, `SkillResult`, `SkillRegistry`, all skill implementations. |
| `:core:memory` | `RoutineMemoryManager`, `PreferenceMemoryManager`, `LocationMemoryManager`. Wraps `:core:data` DAOs with domain logic. |
| `:core:network` | `GoogleAuthManager`, Google API clients (Calendar, Gmail, Drive), `MapsDistanceMatrixClient`, OkHttp/Retrofit setup. |
| `:core:data` | Room database, all entities and DAOs, `SituationModel`, model classes, `DatabaseModule`. |

### Dependency Rules

- **No upward dependencies.** `:core:data` must never import from `:core:memory`,
  `:core:network`, `:core:skills`, or `:core:service`.
- **No lateral dependencies.** `:core:memory` and `:core:network` must not depend
  on each other.
- `:core:skills` may depend only on `:core:data` (for `SituationModel`).
- `:core:service` is the only module that may depend on all other `:core:*` modules.

---

## 6. Naming Conventions

### 6.1 Skill IDs

Skill IDs are the **stable identifier** stored in the database, displayed in logs,
and used to key user preferences. Once assigned, a skill ID must **never change**.

| Rule | Example |
|---|---|
| Format: `snake_case` | `dnd_setter` |
| Descriptive verb + noun | `battery_warner`, `navigation_launcher`, `meeting_preparer` |
| No version suffixes | `dnd_setter` not `dnd_setter_v2` |
| No spaces or hyphens | `calendar_reminder` not `calendar-reminder` |

**Registered skill IDs (current):**

| ID | Skill Name | Phase |
|---|---|---|
| `dnd_setter` | DND Setter | Phase 1.x |
| `battery_warner` | Battery Warner | Phase 1.x |
| `navigation_launcher` | Navigation Launcher | Phase 2.x |
| `meeting_preparer` | Meeting Preparer | Phase 1.5 |

> Add new entries here when you introduce a new skill.

### 6.2 Package Naming

All code in this repository lives under the `com.contextos` root namespace.

| Module | Package root |
|---|---|
| `:app` | `com.contextos` |
| `:core:data` | `com.contextos.core.data` |
| `:core:skills` | `com.contextos.core.skills` |
| `:core:memory` | `com.contextos.core.memory` |
| `:core:network` | `com.contextos.core.network` |
| `:core:service` | `com.contextos.core.service` |

Sub-packages follow the standard Android convention:

- `*.di` — Hilt modules
- `*.db` — Room database and type converters
- `*.db.dao` — Data Access Objects
- `*.db.entity` — Room entities
- `*.model` — Plain Kotlin model/domain classes
- `*.repository` — Repository classes

### 6.3 Database Query Rules

| Rule | Detail |
|---|---|
| All DAO methods must be `suspend` or return `Flow` | Never use `runBlocking` to call a DAO from a non-coroutine context. |
| Never call DAO methods on the main thread | Room will throw a `IllegalStateException` by default. |
| Use `Flow` for reactive UI subscriptions | Use `suspend` for one-shot imperative calls. |
| Prefer `@Upsert` over `@Insert(onConflict = REPLACE)` | `@Upsert` is the Room-idiomatic choice since Room 2.5. |

### 6.4 File Naming

| Artefact | Convention | Example |
|---|---|---|
| Skill implementation | `<Name>Skill.kt` | `DndSetterSkill.kt` |
| Room entity | `<Name>Entity.kt` | `RoutineMemoryEntity.kt` |
| Room DAO | `<Name>Dao.kt` | `RoutineMemoryDao.kt` |
| Hilt module | `<Domain>Module.kt` in `di/` sub-package | `MemoryModule.kt` |
| Repository | `<Name>Repository.kt` | `ActionLogRepository.kt` |
| API client | `<Service>ApiClient.kt` | `CalendarApiClient.kt` |

---

## 7. Cycle Budget

### 7.1 Overview

The ContextOS agent runs on a **15-minute repeating WorkManager task**
(`ContextAgentWorker`). Each cycle is divided into two strictly timed phases:

```
┌───────────────────────────────────────────────────────────────────┐
│                     15-minute cycle (900 s)                       │
│                                                                   │
│  ┌──────────────────────────────┐  ┌──────────────────────────┐  │
│  │  Computation Phase (≤ 12 s)  │  │  Dispatch Phase (≤ 3 s)  │  │
│  │                              │  │                          │  │
│  │  1. Collect sensor data      │  │  5. Show notification /  │  │
│  │  2. Build SituationModel     │  │     overlay to user      │  │
│  │  3. Evaluate all skills      │  │  6. Execute auto-        │  │
│  │     (shouldTrigger)          │  │     approved actions     │  │
│  │  4. Score & rank candidates  │  │  7. Log to action_log    │  │
│  └──────────────────────────────┘  └──────────────────────────┘  │
└───────────────────────────────────────────────────────────────────┘
```

### 7.2 Phase Definitions

#### Computation Phase — 12-second budget

| Step | Owner | Budget |
|---|---|---|
| Sensor data collection (battery, location, audio, Wi-Fi) | `:core:service` | ≤ 3 s |
| Calendar cache refresh (if stale > 5 min) | `:core:network` | ≤ 4 s |
| `SituationModel` construction | `:core:service` | ≤ 1 s |
| `shouldTrigger()` evaluation across all skills | `:core:skills` | ≤ 1 s (pure, no I/O) |
| Confidence scoring and ranking | `:core:service` | ≤ 1 s |
| Memory lookups (routine, preference, location) | `:core:memory` | ≤ 2 s |

> **Total: 12 s max.** The WorkManager `CoroutineWorker` will receive a
> cancellation signal if this phase overruns.

#### Dispatch Phase — 3-second budget

| Step | Budget |
|---|---|
| Fire notification / overlay for top-ranked action | ≤ 1 s |
| Execute auto-approved skill actions | ≤ 1 s |
| Write `ActionLogEntity` to Room | ≤ 1 s |

### 7.3 Rules

| Rule | Consequence of Violation |
|---|---|
| `shouldTrigger()` must be synchronous and < 5 ms per skill | Causes evaluation to exceed 1 s; other skills are delayed. |
| `execute()` must complete within the 12 s computation budget | WorkManager cancels the coroutine; result is logged as `SKIPPED`. |
| No network I/O in `shouldTrigger()` | Blocks entire evaluation pass; forbidden by the interface contract. |
| Cache staleness window: 5 minutes for calendar data | Prevents excessive API quota consumption while keeping data fresh enough. |
| WorkManager `KEEP` policy | Only one concurrent agent cycle may run. |
| Minimum interval: 15 minutes | Android battery optimization floors WorkManager periodic intervals at 15 minutes. |

### 7.4 Cycle Sequence Diagram

```
ContextAgentWorker         SensorCollector       SkillRegistry         ActionDispatcher
       │                         │                     │                      │
       │── collectSensors() ────>│                     │                      │
       │<─ SituationModel ───────│                     │                      │
       │── evaluateAll(model) ───────────────────────>│                      │
       │<─ List<Skill> (triggered) ──────────────────│                      │
       │── scoreAndRank() ───────────────────────────>│                      │
       │<─ ranked candidates ────────────────────────│                      │
       │── dispatch(topCandidate) ──────────────────────────────────────────>│
       │                                              │                      │── notify user
       │                                              │                      │── execute (if auto)
       │<─ SkillResult ─────────────────────────────────────────────────────│
       │── logToRoom(result) ────>│
       │
      END
```

---

*Last updated: ContextOS architecture baseline — Phase 0.x*  
*Maintained by: Platform / Architecture team*
