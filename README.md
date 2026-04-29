# ContextOS — Comprehensive Implementation Plan
### Proactive Phone Orchestrator | Samsung PRISM Hackathon

---

## Team Roster

| ID | Role | Primary Responsibility |
|----|------|------------------------|
| **P1** | Android Development Lead | Core service architecture, Android APIs, Jetpack Compose UI |
| **P2** | AI/ML Engineer | OpenClaw agent, situation modeling, LLM integration, memory layers |
| **P3** | Backend / API Integration Engineer | Google APIs (Calendar, Gmail, Drive), OAuth, data sync |
| **P4** | QA & Testing / Product Manager | Testing strategy, UX design, documentation, scenario validation |

---

## Master Timeline Overview

| Phase | Name | Duration | Focus |
|-------|------|----------|-------|
| Phase 0 | Foundation & Setup | Week 1 | Environment, architecture, shared contracts |
| Phase 1 | Core Infrastructure | Weeks 2–3 | Foreground service, agent loop, DB schema |
| Phase 2 | Action Skills (MVP) | Weeks 4–5 | DND, navigation, battery warner |
| Phase 3 | Intelligence Layer | Weeks 6–7 | Memory system, situation modeling, LLM drafting |
| Phase 4 | Advanced Skills | Week 8 | Document fetcher, message drafter, location intelligence |
| Phase 5 | UI & Dashboard | Week 9 | Action log, settings, onboarding |
| Phase 6 | Integration & QA | Week 10 | End-to-end testing, 20 scenario validation |
| Phase 7 | Hardening & Polish | Week 11 | Battery optimization, privacy audit, edge cases |
| Phase 8 | Demo Prep & Wrap-up | Week 12 | Hackathon demo, documentation, final build |

---

## Phase 0 — Foundation & Setup
**Duration:** Week 1  
**Goal:** Align the team on architecture, tooling, and shared contracts before a single line of production code is written.

---

### Phase 0.1 — Project Bootstrapping

**Owner: P1**

- Create the Android project in Android Studio using Kotlin DSL Gradle
- Configure `minSdk = 31` (Android 12), `targetSdk = 34`
- Set up multi-module structure:
  - `:app` — UI layer (Jetpack Compose)
  - `:core:service` — Foreground Service + OpenClaw loop
  - `:core:data` — Room DB, SharedPreferences, repositories
  - `:core:skills` — individual action skill files
  - `:core:memory` — memory layer implementations
  - `:core:network` — Google API clients
- Configure dependency injection (Hilt)
- Set up version catalog (`libs.versions.toml`) for all dependencies
- Configure ProGuard / R8 rules
- Push initial skeleton to shared Git repository (GitHub)

**Deliverable:** Runnable empty project with module structure in place.

---

### Phase 0.2 — Google API Credentials & OAuth Setup

**Owner: P3**

- Create a Google Cloud Console project
- Enable APIs: Calendar v3, Gmail v1, Drive v3
- Configure OAuth 2.0 consent screen (scopes: `calendar.readonly`, `gmail.readonly`, `drive.readonly`)
- Generate OAuth credentials for Android (SHA-1 fingerprint)
- Implement `GoogleAuthManager` — handles sign-in, token refresh, and secure token storage using Android Keystore
- Write API client wrappers for each Google service with retry logic and exponential backoff
- Document API rate limits (Calendar: 1M queries/day, Gmail: 1B units/day, Drive: 1B units/day) and design call budgets per 15-minute cycle

**Deliverable:** `GoogleAuthManager` + three API client stubs that can make authenticated test calls.

---

### Phase 0.3 — Architecture & Interface Contracts

**Owner: P2, with review from all**

- Define the `SituationModel` data class — the central schema passed between agent and skills:
  ```
  SituationModel(
    currentTime, currentLocation, batteryLevel, isCharging,
    nextCalendarEvent (title, location, startTime, attendees, meetingLink),
    recentAppUsage, ambientAudioContext, memorySummary,
    recommendedActions: List<ActionRecommendation>
  )
  ```
- Define the `Skill` interface:
  ```
  interface Skill {
    val id: String
    val name: String
    fun shouldTrigger(model: SituationModel): Boolean
    suspend fun execute(model: SituationModel): SkillResult
  }
  ```
- Define `SkillResult`, `ActionLog` entry, `UserPreference` schemas
- Define all Room DB entity schemas (see Phase 1.3)
- Publish contracts in a shared `contracts.md` doc so all team members build against the same interfaces

**Deliverable:** Shared contracts document + Kotlin interface/data class stubs committed to repo.

---

### Phase 0.4 — UX Wireframes & Screen Inventory

**Owner: P4**

- Design low-fidelity wireframes for all screens:
  1. **Onboarding flow** (permissions, Google sign-in, emergency contact setup)
  2. **Main Dashboard / Action Log** (chronological feed of ContextOS actions)
  3. **Settings screen** (skill toggles, auto-approve vs confirm, nudge sensitivity)
  4. **Emergency contact configurator**
  5. **Manual override detail screen** (expand any log entry to see why an action was taken)
- Define the 20 test scenarios that QA will validate in Phase 6
- Create a user journey map for "The Busy Professional," "The Commuter," and "The Traveler" personas

**Deliverable:** Wireframe set (Figma or equivalent), test scenario registry, user journey maps.

---

## Phase 1 — Core Infrastructure
**Duration:** Weeks 2–3  
**Goal:** The heartbeat of ContextOS — a persistent background service that wakes every 15 minutes, gathers sensor data, and is ready to run skills.

---

### Phase 1.1 — Android Foreground Service

**Owner: P1**

- Implement `ContextOSService` extending `Service` as an Android Foreground Service
- Implement persistent notification (importance: `IMPORTANCE_LOW`) with a "ContextOS is running" message — required for Foreground Services on Android 12+
- Implement `WorkManager`-based periodic trigger (15-minute cycle) as a fallback for battery-optimized devices, combined with an `AlarmManager` exact alarm for precision
- Handle the Android battery optimizer: request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission with clear user explanation
- Implement service lifecycle management: auto-restart on kill via `START_STICKY`
- Register service in `AndroidManifest.xml` with all required permissions:
  - `FOREGROUND_SERVICE`
  - `ACCESS_FINE_LOCATION`
  - `READ_CALENDAR`
  - `RECEIVE_SMS` / `SEND_SMS`
  - `RECORD_AUDIO` (for ambient audio classification)
  - `POST_NOTIFICATIONS` (Android 13+)
- Implement a `ServiceHealthMonitor` that reports service uptime to the action log
- Write unit tests for service start/stop/restart lifecycle

**Deliverable:** A persistent service that wakes every 15 minutes and logs a heartbeat to Logcat.

---

### Phase 1.2 — Sensor Data Aggregator

**Owner: P1**

- Implement `SensorDataCollector` that gathers all inputs in one pass per cycle:
  - **Battery**: `BatteryManager` — level, charging state, estimated time to empty
  - **Location**: `FusedLocationProviderClient` — lat/lng with accuracy, last known location as fallback
  - **Time**: current time, day of week, time-since-last-unlock
  - **App Usage**: `UsageStatsManager` — foreground app in last 30 minutes
  - **Audio context**: ambient noise level via `AudioRecord` (1-second sample) — used to detect if user is in a meeting/call
  - **Connectivity**: WiFi SSID (location proxy for home/office detection), mobile data state
- All collection must be non-blocking (coroutines with `withContext(Dispatchers.IO)`)
- Implement graceful degradation: if any sensor is unavailable, skip it and mark as `null` in the model

**Deliverable:** `SensorDataCollector` returning a populated `RawSensorData` object within 2 seconds.

---

### Phase 1.3 — Room Database Schema

**Owner: P1 (schema) + P2 (memory-specific tables)**

**P1 owns:**
- `ActionLogEntity` — timestamp, skillId, description, wasAutoApproved, userOverride, situationSnapshot (JSON)
- `UserPreferenceEntity` — skillId, autoApprove (bool), sensitivityLevel (0–3)
- `CalendarEventCacheEntity` — eventId, title, startTime, endTime, location, attendees, meetingLink, lastFetched

**P2 owns:**
- `RoutineMemoryEntity` — dayOfWeek, timeSlot, expectedActivity, confidence, observationCount
- `PreferenceMemoryEntity` — skillId, contextHash, userChoice, frequency
- `LocationMemoryEntity` — latLngHash, inferredLabel (Home/Office/Gym/Unknown), visitCount, typicalArrivalTime, typicalDepartureTime

- Implement all DAOs with coroutine-aware queries
- Configure Room migration strategy (version 1 with future migration paths)
- Implement `DatabaseModule` (Hilt) providing singleton DB instance

**Deliverable:** Room DB compiling with all entities, DAOs, and type converters for complex types.

---

### Phase 1.4 — OpenClaw Agent Bootstrap

**Owner: P2**

- Integrate OpenClaw agent framework as a dependency
- Implement `ContextAgent` — the central orchestrator that:
  1. Receives `RawSensorData` from the sensor collector
  2. Queries calendar cache and memory layers (from DB)
  3. Constructs the `SituationModel`
  4. Iterates over all registered `Skill` instances, calling `shouldTrigger()`
  5. For triggered skills, calls `execute()` and handles `SkillResult`
  6. Writes all decisions to the `ActionLog`
- Implement `SkillRegistry` — a Hilt-provided list of all skill implementations
- Implement `ActionDispatcher` — routes approved `SkillResult` objects to execution (direct auto-execute or queue for user confirmation based on `UserPreferenceEntity`)
- Total cycle time budget: under 12 seconds (leaving 3 seconds for action dispatch within the <15s target)

**Deliverable:** `ContextAgent` running the full loop with stub skills, writing mock action log entries.

---

### Phase 1.5 — Calendar Data Sync

**Owner: P3**

- Implement `CalendarSyncWorker` — runs at service startup and every 30 minutes
- Fetches next 8 hours of calendar events using Google Calendar API
- Parses event details: title, description (for Zoom/Meet links via regex), location, attendees list
- Implements smart meeting link extraction: regex patterns for Zoom (`zoom.us/j/`), Google Meet (`meet.google.com/`), Teams (`teams.microsoft.com/`)
- Caches results in `CalendarEventCacheEntity`
- Implements incremental sync using `syncToken` to avoid re-fetching unchanged events
- Rate limit budget: maximum 4 Calendar API calls per hour

**Deliverable:** Calendar data available in Room DB, refreshing in background, no more than 2-minute stale data during normal operation.

---

## Phase 2 — Action Skills (MVP Set)
**Duration:** Weeks 4–5  
**Goal:** The first three skills that prove the core loop works end-to-end.

---

### Phase 2.1 — `dnd_setter.skill`

**Owner: P1**

- Implement `DndSetterSkill` implementing the `Skill` interface
- `shouldTrigger()` logic:
  - Next calendar event starts within 10 minutes
  - Event has 2+ attendees (i.e., it's a real meeting, not a personal reminder)
  - DND is not already enabled
  - User hasn't overridden this skill more than twice in the last 7 days
- `execute()` logic:
  - Request `ACCESS_NOTIFICATION_POLICY` permission (required for DND on Android)
  - Use `NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_NONE)` to enable DND
  - Schedule a re-enable after meeting end time + 5-minute buffer using a delayed `Handler` post
  - Return `SkillResult.Success` with human-readable description for the action log
- Handle the case where permission is not granted: surface a one-time prompt to the user via notification

**Deliverable:** DND auto-enables/disables around calendar meetings.

---

### Phase 2.2 — `battery_warner.skill`

**Owner: P1**

- Implement `BatteryWarnerSkill`
- `shouldTrigger()` logic:
  - Battery below 20% AND not charging
  - Next calendar event is longer than 90 minutes OR the next 3 hours have 2+ events
  - Has not triggered in the last 2 hours (debounce)
- `execute()` logic:
  - Fetch configured emergency contact from `SharedPreferences`
  - Compose a pre-drafted SMS: "Hi [name], my phone battery is at [X]%. I have meetings for the next [Y] hours — might be unreachable if it dies."
  - Show user a confirmation notification with "Send Now" and "Dismiss" actions
  - If auto-approved: use `SmsManager.sendTextMessage()` directly
  - Log result with battery level, contact name, send status
- Edge case: if no emergency contact configured, surface a setup prompt instead

**Deliverable:** Battery warning SMS triggered correctly in low-battery + upcoming-meeting scenarios.

---

### Phase 2.3 — `navigation_launcher.skill`

**Owner: P1**

- Implement `NavigationLauncherSkill`
- `shouldTrigger()` logic:
  - Next calendar event has a non-empty location field
  - Event starts within 30 minutes
  - Current GPS location is not already at the event location (within 500m radius)
  - It is not a virtual/online meeting (no Zoom/Meet link in the location)
- `execute()` logic:
  - Parse event location string (could be an address or a place name)
  - Construct Google Maps navigation `Intent`: `google.navigation:q=[location]&mode=d`
  - Auto-launch if auto-approve is on, otherwise send a notification with "Start Navigation" CTA
  - Optionally fetch estimated travel time using Maps Distance Matrix API (P3 provides the client)
  - Include estimated arrival in the action log entry

**Deliverable:** Navigation auto-launches to calendar event locations with correct timing.

---

### Phase 2.4 — Google Maps Distance Matrix Client

**Owner: P3**

- Implement `MapsDistanceMatrixClient` using the Maps Platform REST API
- Input: origin (lat/lng), destination (address string), departure time
- Output: estimated duration in traffic, estimated arrival time
- Cache results for 10 minutes (traffic doesn't change faster than that)
- Used by `NavigationLauncherSkill` and `MessageDrafterSkill`
- Implement fallback: if API call fails, return `null` and skill proceeds without travel time

**Deliverable:** `getEstimatedTravelTime()` function used by navigation skill.

---

## Phase 3 — Intelligence Layer
**Duration:** Weeks 6–7  
**Goal:** ContextOS stops being a simple rule-engine and starts being genuinely intelligent — learning your patterns and generating contextually appropriate language.

---

### Phase 3.1 — Situation Modeler (OpenClaw Integration)

**Owner: P2**

- Implement `SituationModeler` using OpenClaw's structured reasoning capabilities
- Every 15-minute cycle, constructs a structured prompt containing:
  - Current time, day, location label (from location memory)
  - Next 3 calendar events with details
  - Battery level and charging state
  - Recent app usage
  - Last 5 action log entries (to avoid repetitive suggestions)
  - Relevant routine memory entries
- OpenClaw returns a `SituationAnalysis` object containing:
  - `currentContextLabel`: "Pre-meeting preparation", "Commuting", "At office", "Free time", etc.
  - `urgencyLevel`: 0–3
  - `recommendedSkills`: list of skill IDs with confidence scores and reasoning
  - `anomalyFlags`: anything unusual (e.g., "meeting in 8 minutes but user is 20km away")
- Use OpenClaw's tool-calling capability to allow the agent to query the DB mid-reasoning (for memory lookups)
- Implement token budget management: cap prompt at 2,000 tokens, response at 500 tokens

**Deliverable:** `SituationModeler` producing reliable `SituationAnalysis` objects that correctly identify the 20 test scenarios.

---

### Phase 3.2 — Routine Memory System

**Owner: P2**

- Implement `RoutineMemoryManager`
- After each completed cycle, record:
  - Day of week + time slot (quantized to 30-minute blocks)
  - What activity was detected (meeting, commuting, at gym, etc.)
  - Which skills were approved/dismissed
- After 10+ observations of the same pattern (e.g., "Monday 9:30 AM → standup meeting"), mark it as a learned routine with high confidence
- `RoutineMemoryManager.getPredictedActivity(dayOfWeek, timeSlot)` — returns predicted activity label + confidence
- Integrate into `SituationModeler` prompt so the agent can say "user typically has a standup now"
- Implement a weekly routine summary that can be surfaced in the app dashboard

**Deliverable:** System learns and recalls weekly behavioral patterns after 2 weeks of simulated usage.

---

### Phase 3.3 — Preference Memory System

**Owner: P2**

- Implement `PreferenceMemoryManager`
- Every time a user overrides an action (approves something that wasn't auto-approved, or dismisses something that was), record:
  - Skill ID
  - Context hash (a fingerprint of the situation: battery %, time of day bucket, meeting type)
  - User choice (approved / dismissed)
- After 3+ consistent overrides in the same context, update `UserPreferenceEntity.autoApprove` automatically
- Expose `shouldAutoApprove(skillId, contextHash)` which combines explicit settings and learned preferences
- Implement preference reset: user can clear learned preferences per skill from the settings screen

**Deliverable:** System adapts auto-approve thresholds based on observed user behavior.

---

### Phase 3.4 — Location Memory System

**Owner: P2**

- Implement `LocationMemoryManager`
- On every cycle, record current lat/lng + time
- Clustering algorithm (simple radius-based): if user has visited a lat/lng cluster 5+ times, create a `LocationMemoryEntity`
- Label inference:
  - Cluster with most visits between 9 AM–6 PM on weekdays → "Office"
  - Cluster with most visits between 8 PM–8 AM → "Home"
  - Cluster visited once per week on same day → "Gym", "Weekly meeting", etc.
  - Others → "Client Site [N]", "Frequent Location [N]"
- `LocationMemoryManager.getLabelForLocation(latLng)` — returns human-readable label
- Used in situation model prompts ("User is at Office") and action log ("Started navigation to Home")

**Deliverable:** Location labels appearing in action log entries after sufficient visits.

---

### Phase 3.5 — LLM Message Drafting Foundation

**Owner: P2**

- Implement `MessageDraftingEngine` using OpenClaw's LLM capabilities
- Input: `DraftingContext` (relationship to recipient, reason for message, available context from situation model)
- Output: a short, natural-sounding draft SMS/message
- Draft tone adapts to context:
  - "Running late" → "Hey [name], stuck in traffic — should be there by [ETA]. Sorry!"
  - Battery warning → "My phone's dying, in meetings until [time] — call [backup number] if urgent"
  - "On my way" → "Leaving now, ETA is about [N] minutes"
- Implement a draft review card in the UI: shows draft, allows one-tap send or quick edit
- Never auto-send messages without user confirmation (hardcoded rule, not a setting)

**Deliverable:** Context-appropriate message drafts generated within 3 seconds.

---

## Phase 4 — Advanced Skills
**Duration:** Week 8  
**Goal:** The remaining two skills that complete the full feature set.

---

### Phase 4.1 — `message_drafter.skill`

**Owner: P1 (skill logic) + P2 (LLM integration)**

- Implement `MessageDrafterSkill`
- `shouldTrigger()` logic:
  - Next event starts within 15 minutes AND user's location suggests they won't arrive on time (travel time > remaining time), OR
  - A "commute home" routine is detected and a configured contact is set for ETA updates
- `execute()` logic (P1):
  - Determine recipient from situation (partner for ETA, meeting organizer for "running late")
  - Collect context: who, why, estimated time, location
  - Call `MessageDraftingEngine.draft(context)` (P2's component)
  - Surface draft as an interactive notification with "Send," "Edit," "Dismiss" actions
  - On "Send": use `SmsManager` or `Intent.ACTION_SENDTO` depending on auto-approve setting
  - On "Edit": deep-link into a minimal compose screen in the app

**Deliverable:** "Running late" drafts surface at the right time with correct recipient and content.

---

### Phase 4.2 — `document_fetcher.skill`

**Owner: P3**

- Implement `DocumentFetcherSkill`
- `shouldTrigger()` logic:
  - Next meeting starts within 20 minutes
  - Meeting title contains keywords (regex: "review," "presentation," "demo," "sync," "Q[1-4]")
  - Documents have not already been fetched for this event
- `execute()` logic:
  - Extract keywords from meeting title and description
  - Search Gmail (subject contains keywords, date within last 7 days) using Gmail API `users.messages.list`
  - Search Drive (name contains keywords, modified within last 14 days) using Drive API `files.list`
  - De-duplicate results, rank by recency and title relevance
  - Create deep links (Drive/Gmail URLs) for top 3 results
  - Surface as a notification: "Found 2 files for your Q3 Review meeting. [Open Drive File] [Open Email]"
  - Write file names + links to action log

**Deliverable:** Relevant meeting documents surface as tappable links before meetings.

---

### Phase 4.3 — `location_intelligence.skill`

**Owner: P2**

- Implement `LocationIntelligenceSkill`
- This skill is passive — it doesn't surface a user-facing action, but it updates the location memory and adjusts system behavior
- `shouldTrigger()` — always true (runs every cycle)
- `execute()` logic:
  - Record current location in memory (Phase 3.4)
  - If user is at a new/unrecognized location: set notification mode to "Priority Only" (gentler than full DND but filters noise)
  - If user has arrived at "Home": re-enable all notifications, disable any active DND
  - If user has arrived at "Office": apply "office mode" — reduce notification volume, enable vibrate-only for non-urgent apps
  - Log location transitions: "Arrived at Office (8:47 AM)"

**Deliverable:** Notification behavior adapts silently based on location context.

---

## Phase 5 — UI & Dashboard
**Duration:** Week 9  
**Goal:** A polished, trust-building UI built in Jetpack Compose.

---

### Phase 5.1 — Onboarding Flow

**Owner: P4 (design) + P1 (implementation)**

- Screen 1: Value proposition ("Your phone acts before you ask")
- Screen 2: Permissions walkthrough — explain each permission clearly:
  - Location: "To know when you're heading to meetings"
  - Calendar: "To prepare for your next event"
  - Notifications: "To show you what we've done"
  - SMS: "Only for messages you approve"
  - DND access: "To silence your phone before meetings"
- Screen 3: Google sign-in for Calendar/Gmail/Drive
- Screen 4: Emergency contact setup (name + phone number)
- Screen 5: "ContextOS is now active" confirmation with a sample action log entry
- Implement permission state tracking — app handles partial permissions gracefully (skills that need missing permissions are disabled with an explanation)

**Deliverable:** Complete onboarding flow that handles all permission states.

---

### Phase 5.2 — Action Log Dashboard

**Owner: P1 (implementation) + P4 (design)**

- Main screen: a live `LazyColumn` feed of `ActionLogEntry` items
- Each entry card shows:
  - Skill icon + name
  - Timestamp
  - Human-readable description ("Enabled DND before your 2 PM Client Review")
  - Status badge: `Auto-executed` / `Awaiting approval` / `Dismissed by you` / `Failed`
  - Expandable detail: "Why? Battery was at 73%. Meeting had 4 attendees. DND was not active."
- Sticky header for date groups ("Today," "Yesterday")
- Pull-to-refresh
- Empty state: "ContextOS is watching. Your first action will appear here soon."
- Pending action cards appear at top with "Approve" and "Dismiss" buttons — large tap targets for quick interaction
- Implement real-time updates via `Flow` from Room DB — no manual refresh needed

**Deliverable:** Action log screen rendering real data from the DB with live updates.

---

### Phase 5.3 — Settings Screen

**Owner: P1 (implementation) + P4 (design)**

- Section: **Skills** — toggle each skill on/off individually with a one-line description
- Section: **Auto-Approve** — for each skill: "Always ask," "Auto-approve," or "Learn from me"
- Section: **Nudge Sensitivity** — slider (Low / Medium / High) that adjusts how confidently the agent must be before triggering a skill
- Section: **Emergency Contact** — edit/change contact
- Section: **Google Account** — shows connected account, sign-out option
- Section: **Learned Preferences** — show a summary of learned routines and patterns; button to reset per-skill
- Section: **Privacy** — link to on-device data summary, button to clear all memory, export action log as CSV

**Deliverable:** Fully functional settings screen wired to `SharedPreferences` and Room DB.

---

### Phase 5.4 — System Notification Templates

**Owner: P1**

- Design all notification types as reusable `NotificationCompat.Builder` templates:
  - **Info** (auto-executed action): no actions, dismissible
  - **Approval request**: "Approve" and "Dismiss" actions — these use `PendingIntent` that update the DB and dispatch the skill without opening the app
  - **Message draft**: "Send," "Edit," "Dismiss"
  - **Document ready**: tappable links to files
  - **Battery warning**: "Send warning" / "Skip"
- Implement a `NotificationManager` wrapper that handles channel creation, deduplication, and priority assignment

**Deliverable:** All notification types rendering correctly across Android 12–14.

---

## Phase 6 — Integration & QA
**Duration:** Week 10  
**Goal:** End-to-end validation across all 20 defined scenarios.

---

### Phase 6.1 — 20-Scenario Test Suite

**Owner: P4**

Define and execute all 20 scenarios from the test scenario registry (defined in Phase 0.4). Each scenario specifies:
- Input state (battery level, time, calendar events, location, recent app usage)
- Expected triggered skills
- Expected action log output
- Expected notification(s)

Sample scenario breakdown:

| # | Scenario | Expected Skills | Pass Criteria |
|---|----------|-----------------|---------------|
| 1 | Meeting in 8 min, DND off | `dnd_setter` | DND enabled, log entry written |
| 2 | Battery 17%, 2-hr meeting in 45 min | `battery_warner` | Draft SMS surfaced |
| 3 | Leaving office at 6:30 PM | `navigation_launcher`, `message_drafter` | Nav intent fired, ETA draft shown |
| 4 | Meeting with Drive attachment keywords | `document_fetcher` | 1+ file links in notification |
| 5 | Arriving at new location | `location_intelligence` | Notification mode adjusted |
| 6 | DND already on before meeting | `dnd_setter` | Skill does NOT re-trigger |
| 7 | Virtual meeting (Zoom link, no location) | `navigation_launcher` | Skill does NOT trigger |
| 8 | User dismissed DND 3x in a row | preference memory | Auto-approve for DND disabled |
| 9 | Battery at 60%, short meeting | `battery_warner` | Skill does NOT trigger |
| 10 | First use, no memory data | all skills | Falls back to rule-based defaults |
| 11–20 | [Additional edge cases and persona scenarios] | varies | Per-scenario pass criteria |

- Write automated instrumentation tests using `Espresso` for UI flows
- Write unit tests for all `shouldTrigger()` logic using mock `SituationModel` objects
- Write integration tests for Google API clients using mock HTTP responses (MockWebServer)

**Deliverable:** 95%+ pass rate across all 20 scenarios, documented in a test report.

---

### Phase 6.2 — Battery & Performance Profiling

**Owner: P4 (testing) + P1 (optimization)**

- Use Android Battery Historian to profile drain over a simulated 8-hour workday
- Measure: baseline drain (phone idle) vs. ContextOS active drain
- Target: `< 5%` additional overhead
- Profile memory usage: target `< 50 MB` heap during normal operation
- Profile cycle duration: target `< 12 seconds` end-to-end per cycle
- Identify and fix any wakelock abuse or unnecessary I/O
- Test graceful degradation: force-kill the service and verify it auto-restarts within 2 minutes

**Deliverable:** Battery overhead documented and confirmed below 5% target.

---

### Phase 6.3 — Google API Integration Testing

**Owner: P3**

- Test Calendar sync against real events with edge cases:
  - All-day events (should not trigger navigation)
  - Recurring events
  - Events with no location
  - Events cancelled after sync
- Test Gmail search with various keyword patterns
- Test Drive search with files shared by others (not owned)
- Test OAuth token expiry and refresh flow
- Test behavior when Google account is signed out mid-session
- Verify rate limit handling: confirm exponential backoff fires correctly under simulated 429 responses

**Deliverable:** All Google API integrations passing edge-case test suite.

---

## Phase 7 — Hardening & Polish
**Duration:** Week 11  
**Goal:** Production-quality reliability, privacy audit, and UX polish.

---

### Phase 7.1 — Privacy & Security Audit

**Owner: P4 (audit) + P3 (fixes)**

- Verify all sensitive data fields (location, contact name, email content) are encrypted at rest using Android Keystore + Room encryption (SQLCipher or EncryptedSharedPreferences)
- Verify no personal data leaves the device (block external network calls from the core data module in tests)
- Verify that disabling a skill immediately stops data collection for that skill's dependencies
- Verify action log export (CSV) strips any sensitive content before writing to external storage
- Review all permission usage against the minimum necessary principle
- Confirm GDPR data deletion: "Clear all data" button genuinely deletes all Room tables and SharedPreferences

**Deliverable:** Privacy audit checklist completed with all issues resolved.

---

### Phase 7.2 — Edge Case Hardening

**Owner: P1**

- Handle airplane mode / no network: skills that require network (document_fetcher, message_drafter LLM) degrade gracefully, others continue
- Handle no GPS signal: location skills use last known location with a staleness warning
- Handle no calendar events: agent runs in "monitoring mode," no meeting-prep skills trigger
- Handle rapid calendar changes (meeting added < 5 min before start): trigger an immediate out-of-cycle check
- Handle multiple simultaneous meetings (back-to-back): process the nearest one only, queue the next
- Handle app uninstall of Google services: detect absence and surface a clear error

**Deliverable:** App handles all identified edge cases without crashing or producing nonsensical actions.

---

### Phase 7.3 — UX Polish & Accessibility

**Owner: P4**

- Review all screens against Material Design 3 guidelines
- Ensure all interactive elements meet 48dp minimum touch target
- Add content descriptions to all icons for TalkBack (accessibility)
- Implement dark mode support in Jetpack Compose (all colors via `MaterialTheme.colorScheme`)
- Add subtle animations: action log items slide in, pending cards pulse gently
- Review notification copy for brevity and clarity — all under 60 characters for the title line
- Final review of all permission explanations in onboarding — must be clear to a non-technical user

**Deliverable:** App passes accessibility scan, renders correctly in dark mode, notifications are concise.

---

### Phase 7.4 — Cold Start Optimization

**Owner: P2**

- Implement "Day 1 defaults" — sensible rule-based behavior before memory is populated:
  - Standard work hours assumption (9 AM–6 PM) for office mode
  - DND enabled for any meeting with 2+ attendees (confidence: 100%)
  - Navigation offered for any meeting with a location field
- Implement accelerated learning: first 5 user interactions with skills are weighted 3× in preference memory
- Implement a "quick setup" screen (accessible from settings) where user can pre-configure:
  - Work start/end times
  - Home and office locations (manual input)
  - Commute days
- This pre-seeding creates initial `RoutineMemoryEntity` and `LocationMemoryEntity` records, so the system is personalized from day one

**Deliverable:** Fresh install provides genuinely useful behavior on day 1 without requiring 2 weeks of learning.

---

## Phase 8 — Demo Prep & Wrap-up
**Duration:** Week 12  
**Goal:** A compelling, stable hackathon demo and complete documentation.

---

### Phase 8.1 — Demo Build Preparation

**Owner: P1**

- Create a `DemoMode` flag in BuildConfig that enables:
  - Compressed time (1 minute = 15 minutes for demo purposes)
  - Pre-seeded scenario data (a fake calendar with meetings, a pre-loaded location memory)
  - Accelerated memory learning (patterns learned after 2 observations instead of 10)
- Build a signed release APK with `minifyEnabled = true` and R8 optimization
- Test the release build on at least 2 physical Android 12+ devices (including a Samsung Galaxy device for Samsung PRISM context)

**Deliverable:** Signed release APK running smoothly on demo hardware.

---

### Phase 8.2 — Demo Script & Scenario Walkthrough

**Owner: P4**

Write a 5-minute live demo script covering:

1. **The Busy Professional scenario** (9:50 AM):
   - Show calendar: "Client Review in 10 mins"
   - Show ContextOS action log: DND enabled, Q3 deck fetched, Zoom opened
   - Show the "Why?" expansion: agent reasoning visible
2. **The Commuter scenario** (6:30 PM):
   - Simulate leaving office
   - Show navigation launching, ETA draft surfacing
3. **Settings & Control** (30 seconds):
   - Show skill toggles, auto-approve settings, action log history
4. **Privacy story** (15 seconds):
   - "Everything runs on your device. No data leaves your phone."

Prepare answers for likely judge questions:
- "How does it differ from Bixby Routines?" → Routines require manual setup; ContextOS learns and acts autonomously
- "What about battery?" → <5% overhead, show Battery Historian screenshot
- "Privacy concerns?" → On-device processing, encrypted storage, full user control, GDPR compliant

**Deliverable:** Rehearsed demo script with backup slides for any live demo failures.

---

### Phase 8.3 — Technical Documentation

**Owner: P3 + P4**

- **README.md**: Setup instructions, architecture overview, how to build and run
- **ARCHITECTURE.md**: Module dependency diagram, data flow diagram, 15-minute cycle flowchart
- **SKILLS.md**: Each skill documented with trigger conditions, execute logic, and test coverage
- **PRIVACY.md**: Complete data inventory (what data is collected, where stored, how to delete), GDPR compliance statement
- **API_SETUP.md**: Step-by-step Google Cloud Console setup guide for reviewers who want to run the project themselves
- Inline code documentation (KDoc) for all public interfaces, data classes, and non-obvious logic

**Deliverable:** Complete documentation set committed to repository.

---

### Phase 8.4 — Final Team Review & Sign-off

**Owner: All (P1, P2, P3, P4)**

- Final regression test run on all 20 scenarios — record pass/fail
- Sign-off checklist:
  - [ ] App installs cleanly on a fresh device
  - [ ] Onboarding completes successfully with all permissions granted
  - [ ] All 6 skills trigger correctly in demo mode
  - [ ] Action log displays all events
  - [ ] Settings persist across app restarts
  - [ ] Memory data persists across device reboots
  - [ ] Battery overhead confirmed below 5% on test device
  - [ ] No crashes in 30-minute continuous demo run
  - [ ] Documentation complete
  - [ ] Release APK signed and filed

---

## Dependency Map

```
Phase 0 (all) → Phase 1 (all) → Phase 2 (P1) → Phase 3 (P2) → Phase 4 → Phase 5 → Phase 6 → Phase 7 → Phase 8
                     ↑                               ↑
              Phase 1.5 (P3)              Phase 3.5 (P2 + P3)
              Calendar Sync               LLM + Gmail/Drive
```

Critical path: **P1's Foreground Service (Phase 1.1) blocks everything.** This must be complete before any skill work begins.

---

## Risk Register

| Risk | Likelihood | Impact | Owner | Mitigation |
|------|-----------|--------|-------|------------|
| Android kills background service | High | High | P1 | Foreground Service + WorkManager fallback |
| Google API rate limits hit during testing | Medium | Medium | P3 | Caching + mock responses for most tests |
| OpenClaw situation model low accuracy | Medium | High | P2 | Fallback to rule-based defaults if confidence < 0.7 |
| Cold start — app useless until patterns learned | High | Medium | P2 | Pre-configured defaults + quick setup screen |
| Battery overhead exceeds 5% | Medium | High | P1 | Profile early (Phase 6.2), optimize cycle aggressively |
| Location clustering produces wrong labels | Medium | Medium | P2 | Manual override in settings; "Office" and "Home" pinnable |
| Gmail/Drive search returns irrelevant files | Medium | Low | P3 | Multi-keyword AND logic, recency weighting |

---

## Success Metrics Tracking

| Metric | Target | How Measured | Owner |
|--------|--------|--------------|-------|
| Situation Detection Accuracy | ≥ 95% | 20-scenario test suite pass rate | P4 |
| Action Latency | < 3 seconds | Cycle timing logs in development build | P1 |
| Battery Overhead | < 5% | Android Battery Historian 8-hour soak test | P4 |
| Action Approval Rate | ≥ 80% | Simulated user study in demo mode | P4 |
| Service Uptime | ≥ 99% over 24h | `ServiceHealthMonitor` logs | P1 |
| Cold Start Time to First Useful Action | < 15 minutes | Manual test on fresh install | P4 |
