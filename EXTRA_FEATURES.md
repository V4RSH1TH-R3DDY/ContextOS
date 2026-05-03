# ContextOS — Extra Features Implementation Plan
### Differentiator Layer | Built on top of README.md Phase 0–8

---

## Overview

This document covers implementation plans for six features that elevate ContextOS from a capable background agent into a product that feels genuinely intelligent, trustworthy, and premium. These phases slot in after the core Phase 5 UI work and run in parallel or sequentially with Phases 6–8.

| Phase | Feature | Duration | Slot |
|-------|---------|----------|------|
| Phase 9 | "Why I Acted" Reasoning Panel | Week 9 (alongside Phase 5) | P2 + P4 |
| Phase 10 | Deep Personalisation Engine | Weeks 8–9 | P2 |
| Phase 11 | Samsung Ecosystem Integration | Weeks 9–10 | P1 + P3 |
| Phase 12 | On-Device Privacy Architecture | Weeks 7–8 (alongside Phase 7) | P2 + P1 |

---

## Phase 9 — "Why I Acted" Reasoning Transparency Panel

**Duration:** Week 9 (runs alongside Phase 5 UI work)  
**Owners:** P2 (reasoning data), P4 (design), P1 (Compose implementation)  
**Goal:** Make OpenClaw's reasoning visible so users trust the app rather than fear it.

> This is a core differentiator. Most AI apps hide their reasoning in the backend. Surfacing it turns ContextOS from a "ChatGPT wrapper" into a product that feels genuinely intelligent.

---

### Phase 9.1 — Reasoning Data Layer

**Owner: P2**

**Context (from README):** The existing `ActionLogEntity` (Phase 1.3) stores a `situationSnapshot` JSON field. The `SituationModeler` (Phase 3.1) already produces a `SituationAnalysis` object with `currentContextLabel`, `urgencyLevel`, `recommendedSkills` (with confidence scores), and `anomalyFlags`. This phase formalises and exposes that data.

- Extend `ActionLogEntity` with a new `reasoningPayload` field (JSON string):
  ```
  ReasoningPayload(
    contextLabel: String,          // e.g. "Pre-meeting commute"
    confidenceScore: Float,        // e.g. 0.92
    reasoningPoints: List<String>, // bullet-point reasons
    anomalyFlags: List<String>,    // anything unusual the agent caught
    dataSourcesUsed: List<String>  // e.g. ["Calendar", "GPS", "RoutineMemory"]
  )
  ```
- Update `ContextAgent` (Phase 1.4) to populate `ReasoningPayload` for every skill execution, not just the triggered ones — include "why this skill did NOT trigger" entries for dismissed candidates (shown in collapsed state only)
- Implement `ReasoningBuilder` — a utility class that converts raw `SituationAnalysis` fields into human-readable bullet strings:
  - `"Meeting in 14 mins"` ← from `nextCalendarEvent.startTime - currentTime`
  - `"User 8 km away"` ← from GPS vs event location distance
  - `"Traffic heavy"` ← from `MapsDistanceMatrixClient` traffic delta
  - `"Historical lateness pattern detected"` ← from `RoutineMemoryEntity` anomaly score
- All `ReasoningPayload` entries are generated on-device — no data leaves the device to produce this output

**Deliverable:** Every `ActionLogEntity` row includes a fully populated `ReasoningPayload` JSON object.

---

### Phase 9.2 — "Why I Acted" UI Panel

**Owner: P1 (implementation) + P4 (design)**

**Context (from README):** Phase 5.2 already defines an expandable detail on each `ActionLogEntry` card showing a plain-text "Why?" string. This phase replaces that plain string with a structured, visually distinct reasoning card.

- Replace the existing plain-text "Why?" expansion in the Action Log (Phase 5.2) with a structured `ReasoningCard` composable:

```
┌─────────────────────────────────────────┐
│  Context Detected: Pre-meeting commute  │
│  Confidence: 92%          ████████░░    │
│                                         │
│  Why I acted:                           │
│  • Meeting in 14 mins                   │
│  • You are 8 km away                   │
│  • Traffic is currently heavy           │
│  • Historical lateness pattern detected │
│                                         │
│  Sources: Calendar · GPS · Your history │
└─────────────────────────────────────────┘
```

- `ReasoningCard` composable elements:
  - Context label in a `SuggestionChip` styled pill (Material 3)
  - Confidence score rendered as a `LinearProgressIndicator` with a numeric label beside it
  - Bullet list of `reasoningPoints` — each prefixed with an icon from the data source (calendar icon, pin icon, waveform icon for audio, history icon for memory)
  - `anomalyFlags` rendered in an amber-tinted callout box if present (e.g. "⚠ Meeting in 8 mins but you appear to be 20 km away")
  - A collapsed-by-default secondary section: "Why other actions were not taken" — shows the top 2 skills that evaluated but did not trigger, with their confidence and the reason they fell short
- Animate the expansion with `AnimatedVisibility` and a smooth height transition
- On the pending approval cards (top of Action Log), show the `ReasoningCard` expanded by default — the user needs context before approving

**Deliverable:** Every action log entry expands to show a structured, readable reasoning card. Pending approval cards show it open by default.

---

### Phase 9.3 — Reasoning Panel in Demo Mode

**Owner: P1**

**Context (from README):** Phase 8.1 defines a `DemoMode` flag with pre-seeded scenario data. This phase ensures the reasoning panel shines during the hackathon demo.

- In `DemoMode`, pre-populate three showcase `ReasoningPayload` entries:
  - **Pre-meeting commute**: confidence 92%, 4 reasoning bullets, 1 anomaly flag
  - **Low battery before long meeting**: confidence 88%, 3 bullets
  - **End-of-day commute home**: confidence 79%, 3 bullets, routine memory sourced
- These entries are injected into the Room DB at demo app startup (alongside the pre-seeded calendar from Phase 8.1)
- Add a subtle animated pulse to the "Why I acted" expand chevron for the first 3 entries in demo mode — draws judges' attention to the feature without being intrusive

**Deliverable:** Demo mode showcases the reasoning panel with compelling pre-built entries that impress judges immediately.

---

## Phase 10 — Deep Personalisation Engine

**Duration:** Weeks 8–9  
**Owners:** P2 (ML + memory), P4 (UX copy)  
**Goal:** ContextOS feels eerily personal — not a generic assistant, but one that has been paying attention.

> After a week of usage, the app should feel like it knows you. "You usually call home around now" or "Mondays at 9:30 are your standup" should feel natural, not creepy.

---

### Phase 10.1 — Personal Routine Detector

**Owner: P2**

**Context (from README):** Phase 3.2 defines `RoutineMemoryManager` which learns patterns after 10+ observations and marks them as high-confidence routines. This phase builds the detection and surfacing layer on top.

- Extend `RoutineMemoryManager` to support **named routine types** beyond meeting detection:
  - `CALL_HOME` — detected when the user initiates a phone call to a saved contact at a consistent time slot
  - `FOCUS_BLOCK` — detected when DND or notification silencing correlates with a specific recurring calendar event
  - `COMMUTE_DEPARTURE` — detected when GPS movement away from "Office" starts at a consistent time
  - `EVENING_WRAP` — detected when screen-off patterns signal end of working day
- Data sources for detection (all on-device):
  - `CallLog.Calls` content provider — read with `READ_CALL_LOG` permission, used only for timing patterns, never content
  - `UsageStatsManager` — detect app-usage patterns that suggest focus (no social apps for 60+ min)
  - `RoutineMemoryEntity` observation count and time slots (already implemented in Phase 3.2)
- After 5 consistent observations of the same routine type + time slot, promote it to a `ConfirmedRoutine` with a flag `shouldSurfaceToUser = true`
- Implement `PersonalRoutineService.getSuggestionsForNow(currentTime, dayOfWeek)` — returns a list of `RoutineSuggestion` objects ranked by recency and confidence

**New DB entity:**
```
ConfirmedRoutineEntity(
  routineType: RoutineType,
  dayOfWeek: Int,
  timeSlotStart: Int,       // minutes since midnight
  timeSlotEnd: Int,
  confidence: Float,
  observationCount: Int,
  lastObserved: Long,
  suggestedAction: String,  // the message to surface to user
  isActive: Boolean
)
```

**Deliverable:** `PersonalRoutineService` returning typed, confirmed routines with human-readable suggested actions after simulated 2-week usage patterns.

---

### Phase 10.2 — Proactive Personal Nudges

**Owner: P2 (trigger logic) + P1 (notification) + P4 (copy)**

**Context (from README):** Phase 3.5 defines `MessageDraftingEngine` for reactive drafts. Phase 5.4 defines notification templates. This phase adds a new trigger pathway for proactive personal nudges that are not tied to a calendar event.

- Implement a new `PersonalNudgeSkill` implementing the `Skill` interface:
  - `shouldTrigger()` — queries `PersonalRoutineService.getSuggestionsForNow()`, returns true if any `ConfirmedRoutine` with confidence ≥ 0.75 matches the current time slot (±5 minutes)
  - `execute()` — selects the highest-confidence routine, calls `MessageDraftingEngine` with the routine context to produce a nudge message, surfaces as a soft notification

- Nudge examples (copy register, signed off by P4):

| Routine Detected | Nudge Message |
|-----------------|---------------|
| `CALL_HOME` at 6:30 PM | "You usually check in with home around now — want me to draft something?" |
| `FOCUS_BLOCK` Monday 9:30 AM | "Mondays at 9:30 are usually your standup. Enabling focus mode." |
| `COMMUTE_DEPARTURE` at 5:45 PM | "Looks like you're heading out. Want me to draft your ETA?" |
| `EVENING_WRAP` at 7:00 PM | "Looks like you're wrapping up. DND is off — want to review any pending actions?" |

- Nudges are **non-invasive by design**: use notification priority `PRIORITY_LOW`, no sound, no vibration
- Implement a nudge frequency cap: maximum 2 personal nudges per day, minimum 3 hours apart
- Add a "Stop suggesting this" action button on every nudge notification — one tap suppresses that `RoutineType` permanently
- Log all nudges in the Action Log with their full `ReasoningPayload` (Phase 9.1) so the user can see why the nudge was sent

**Deliverable:** Contextually personal nudges surface at the right moment, feel natural, and can be suppressed with one tap.

---

### Phase 10.3 — Accelerated Learning UI

**Owner: P4 (design) + P2 (implementation)**

**Context (from README):** Phase 7.4 defines "Day 1 defaults" and notes that first 5 interactions are weighted 3× in preference memory. This phase surfaces that learning process to the user.

- Add a "Learning" section to the dashboard (below the action log feed) visible only in the first 14 days:
  - Shows a simple progress bar: "ContextOS has learned [N] of your routines"
  - Lists confirmed routines as they are discovered: "Learnt: You prefer DND before meetings with 3+ attendees"
  - Each learned routine has a toggle: active/paused
- Implement a "Teach me faster" shortcut in settings: user can manually tag time slots (e.g. "Mondays 9:30 AM = standup") which seeds `ConfirmedRoutineEntity` records directly, skipping the observation requirement
- After 14 days, collapse the learning section into a one-line "Routines learned: 6 · Manage" link in settings

**Deliverable:** Users can see and trust the learning process, reducing the "black box" feeling that causes churn in AI apps.

---

## Phase 11 — Samsung Ecosystem Integration

**Duration:** Weeks 9–10  
**Owners:** P1 (Android integration), P3 (API clients)  
**Goal:** Frame ContextOS not as an Android app but as the intelligence layer for Samsung One UI.

> Don't say "Android app." Say "Future Samsung One UI intelligence layer." Judges stop seeing a student project and start seeing a product roadmap.

---

### Phase 11.1 — Galaxy Watch Integration (Activity Context)

**Owner: P1**

- Integrate Samsung Health SDK or Wear OS `HealthServicesClient` to receive activity data from a paired Galaxy Watch
- Data signals consumed:
  - `ACTIVITY_TYPE` — walking, running, stationary, in_vehicle (used to infer commuting)
  - `HEART_RATE` — elevated HR + stationary = possible call/meeting stress signal
  - `STEPS` — significant step increase = user has started moving (triggers commute detection earlier than GPS alone)
- Implement `WearableDataReceiver` — a `WearableListenerService` that receives updates via the Wearable Data Layer API and writes them to a new `WearableContextEntity` in Room:
  ```
  WearableContextEntity(
    timestamp: Long,
    activityType: String,
    heartRate: Int?,
    stepCountDelta: Int,
    deviceConnected: Boolean
  )
  ```
- Integrate `WearableContextEntity` into `SensorDataCollector` (Phase 1.2) as an additional data source — merge into `RawSensorData`
- In `SituationModeler` prompt (Phase 3.1), add a wearable context line: `"Galaxy Watch: Walking, 94 bpm, 850 steps in last 10 min"` — gives the agent a commute signal before GPS confirms it, improving pre-meeting timing accuracy

**Fallback:** If no Galaxy Watch is paired, `WearableContextEntity` fields are all `null` and the `SensorDataCollector` skips them gracefully (consistent with Phase 1.2's graceful degradation pattern).

**Deliverable:** Galaxy Watch activity data feeding into the situation model, improving commute detection accuracy.

---

### Phase 11.2 — Galaxy Buds Integration (Audio Context)

**Owner: P1**

- Integrate Samsung Accessory SDK to detect paired Galaxy Buds state
- Data signals consumed:
  - `BUDS_IN_EAR` (both / one / neither) — both in ear = user is likely on a call or in a meeting
  - `AMBIENT_SOUND_MODE` — if user has enabled ambient sound, they may be commuting
  - `ACTIVE_NOISE_CANCELLATION` — ANC on = user is in a noisy environment (commuting, café)
- Implement `BudsStateReceiver` — a broadcast receiver for Samsung Accessory Framework events, writes to `BudsContextEntity`:
  ```
  BudsContextEntity(
    timestamp: Long,
    budsInEar: BudsWearState,    // BOTH / ONE / NEITHER
    ancActive: Boolean,
    ambientSoundActive: Boolean,
    deviceConnected: Boolean
  )
  ```
- Map Buds signals to context inferences in `ReasoningBuilder` (Phase 9.1):
  - Both Buds in ear → suppress `dnd_setter` (user may already be managing audio manually)
  - ANC active → add `"Active noise cancellation on (likely commuting)"` to reasoning points
  - Neither Buds in ear during scheduled meeting → anomaly flag: `"Buds removed — user may not be taking this call"`
- In the "Why I Acted" panel (Phase 9.2), show a Buds icon in the `dataSourcesUsed` chip row when Buds contributed to the decision

**Fallback:** If no Galaxy Buds paired, `BudsContextEntity` is all null, audio context falls back to the existing `AudioRecord`-based ambient classifier (Phase 1.2).

**Deliverable:** Galaxy Buds state feeds into the situation model as an additional audio context signal.

---

### Phase 11.3 — SmartThings Home Arrival Integration

**Owner: P3**

- Integrate SmartThings API using Samsung's SmartThings REST API (OAuth 2.0, same flow as Google APIs in Phase 0.2)
- Scope requested: `r:devices:*` (read device states), `r:locations:*` (read location/presence)
- Data signals consumed:
  - SmartThings **Presence Sensor** or **virtual presence** (phone-as-presence): `present` / `not present` at the user's Home location
  - SmartThings **mode**: `Home`, `Away`, `Night`, `Vacation`
- Implement `SmartThingsClient` in `:core:network` (consistent with Phase 0.2's API client architecture):
  - `getPresenceStatus(locationId)` — returns `PresenceStatus(isHome: Boolean, mode: String)`
  - Rate limit budget: poll maximum once every 5 minutes (SmartThings API allows 300 calls/15 min per token)
  - Cache last known presence state in `SharedPreferences` — use cached state if API call fails
- Integration with existing skills:
  - `LocationIntelligenceSkill` (Phase 4.3): supplement GPS-based home detection with SmartThings presence confirmation — higher confidence when both agree
  - `MessageDrafterSkill` (Phase 4.1): when SmartThings confirms user has arrived home, suppress any pending ETA drafts automatically
  - `DndSetterSkill` (Phase 2.1): when SmartThings `mode` switches to `Night`, proactively enable DND without a calendar event trigger (configurable in settings)
- Add a SmartThings connection card to the Settings screen (Phase 5.3) alongside the Google account section:
  - Shows: connected / not connected
  - "Connect SmartThings" deep-links to Samsung account OAuth flow
  - When connected, shows "Home location: [SmartThings location name]"

**Fallback:** If SmartThings is not connected, all home-detection falls back to GPS-based `LocationMemoryManager` (Phase 3.4) — no degradation in core features.

**Deliverable:** SmartThings presence data feeding into location intelligence, with home-arrival triggers that are more reliable than GPS alone.

---

### Phase 11.4 — Ecosystem Roadmap Slide (Demo Asset)

**Owner: P4**

- Create a single "Future Roadmap" slide / in-app screen for the demo that visualises the Samsung ecosystem integration:
  ```
  Galaxy Watch  ──→  Activity context (commuting, heart rate)
  Galaxy Buds   ──→  Audio context (calls, noise environment)
  SmartThings   ──→  Presence detection (home arrival, night mode)
                          │
                          ▼
                  ContextOS Intelligence Layer
                          │
                          ▼
                  Samsung One UI Actions
  ```
- This is shown in the final 30 seconds of the demo script (Phase 8.2) — after demonstrating the working core, pivot to vision:
  - "What you've seen runs today. Here's where it goes."
  - The integration code (Phases 11.1–11.3) provides legitimacy to the roadmap claim — it's not vaporware, it's already architected and partially wired

**Deliverable:** A compelling ecosystem diagram used in the live demo and in ARCHITECTURE.md.

---

## Phase 12 — On-Device Privacy Architecture

**Duration:** Weeks 7–8 (runs alongside Phase 7 Hardening)  
**Owners:** P2 (reasoning pipeline), P1 (storage), P4 (privacy copy)  
**Goal:** Make "on-device first" a genuine technical truth, not just a marketing claim.

> Unlike cloud assistants, ContextOS reasons on-device first. Frame it: "Your life stays on your device. Your intelligence travels with you."

---

### Phase 12.1 — On-Device Reasoning Enforcement

**Owner: P2**

**Context (from README):** Phase 3.1 defines `SituationModeler` using OpenClaw's LLM. Phase 3.5 defines `MessageDraftingEngine`. Both currently call an external LLM API. This phase establishes a clear on-device-first policy with an explicit off-device boundary.

- Implement `InferenceRouter` — a single gatekeeper class that all LLM calls must pass through:
  ```kotlin
  class InferenceRouter {
    fun route(request: InferenceRequest): InferenceTarget {
      // On-device capable: situation classification, routine detection, confidence scoring
      // Off-device (opt-in only): message drafting with personalised tone
      return when (request.type) {
        SITUATION_ANALYSIS -> InferenceTarget.ON_DEVICE
        ROUTINE_DETECTION  -> InferenceTarget.ON_DEVICE
        MESSAGE_DRAFTING   -> if (userConsentedToCloud) InferenceTarget.CLOUD else InferenceTarget.ON_DEVICE_FALLBACK
      }
    }
  }
  ```
- On-device inference uses a quantised model (e.g. Gemma 2B via `MediaPipe LLM Inference API` or a rule-based fallback) for:
  - `SituationModeler` — context classification and skill ranking
  - `RoutineMemoryManager` — pattern detection and confidence scoring
  - `ReasoningBuilder` — constructing human-readable reasoning strings from structured data (no LLM needed — pure template logic)
- Off-device calls (OpenClaw / cloud LLM) are **opt-in only** and limited to `MessageDraftingEngine` where tone quality matters — user is shown a one-time consent prompt: "Better message drafts require sending anonymised context to a cloud model. No personal names or contacts are included."
- Implement `DataMaskingLayer` — wraps every off-device payload, replacing:
  - Contact names → `[CONTACT]`
  - Specific addresses → `[LOCATION]`
  - Meeting titles → semantic category only (e.g. `"client_review"` not `"Acme Q3 Budget Review"`)

**Deliverable:** Clear on-device vs off-device boundary enforced in code. Off-device calls require explicit user consent and go through `DataMaskingLayer`.

---

### Phase 12.2 — On-Device Storage Hardening

**Owner: P1**

**Context (from README):** Phase 7.1 defines a privacy audit verifying encryption at rest using Android Keystore + SQLCipher/EncryptedSharedPreferences. This phase goes further and adds a user-visible data inventory.

- Implement `DataInventoryRepository` — queries all Room tables and returns a structured summary:
  ```
  DataInventory(
    actionLogCount: Int,
    oldestActionLog: Date,
    locationMemoryCount: Int,
    routineMemoryCount: Int,
    calendarCacheCount: Int,
    sizeOnDiskBytes: Long
  )
  ```
- Surface this in the Settings screen (Phase 5.3) Privacy section as a real-time data card:
  ```
  ┌─────────────────────────────────────┐
  │  Your data — stored only here       │
  │  Action history: 847 entries        │
  │  Location patterns: 12 places       │
  │  Routines learned: 6                │
  │  Storage used: 2.1 MB              │
  │                                     │
  │  [Export as CSV]  [Delete all]      │
  └─────────────────────────────────────┘
  ```
- Implement an automatic data retention policy (configurable in settings):
  - Action log entries older than 90 days: auto-deleted
  - Location memory entries with < 3 visits and last visit > 60 days: pruned
  - Runs as a `WorkManager` `PeriodicWorkRequest` every 7 days
- Confirm that the `DataMaskingLayer` (Phase 12.1) strips all identifying fields before any CSV export — export contains anonymised entries only (timestamps, skill names, confidence scores; no location coordinates, contact names, or message content)

**Deliverable:** User can see exactly what data the app holds, with controls to export anonymised history or wipe everything. Auto-retention policies ensure data doesn't accumulate indefinitely.

---

### Phase 12.3 — Privacy-First Demo Narrative

**Owner: P4**

- Extend the demo script (Phase 8.2) with a 30-second privacy segment positioned **before** the ecosystem roadmap slide:
  - Pull up the on-device data card (Phase 12.2) live during the demo
  - Show it populated with the pre-seeded demo data (a realistic count of entries)
  - Say: "Everything you saw — the reasoning, the routines, the context detection — happened entirely on this device. No data left the phone."
  - Tap "Delete all" to show it wipes instantly (use the demo-mode reset function that re-seeds data automatically after 2 seconds, so the demo can continue)
- Prepare a one-liner for judge questions on privacy:
  - "Unlike cloud assistants, ContextOS uses an on-device inference pipeline for all situation analysis and routine detection. Message drafting can optionally use a cloud model with anonymised, masked input — but only with explicit user consent."

**Deliverable:** Privacy narrative woven into the demo script with a live product demonstration of the data card and wipe.

---

## Updated Master Timeline

| Phase | Name | Duration | New? |
|-------|------|----------|------|
| Phase 0 | Foundation & Setup | Week 1 | — |
| Phase 1 | Core Infrastructure | Weeks 2–3 | — |
| Phase 2 | Action Skills (MVP) | Weeks 4–5 | — |
| Phase 3 | Intelligence Layer | Weeks 6–7 | — |
| Phase 4 | Advanced Skills | Week 8 | — |
| Phase 5 | UI & Dashboard | Week 9 | — |
| **Phase 9** | **"Why I Acted" Panel** | **Week 9** | **✦ New** |
| Phase 6 | Integration & QA | Week 10 | — |
| Phase 7 | Hardening & Polish | Week 11 | — |
| **Phase 12** | **On-Device Privacy Architecture** | **Weeks 7–8** | **✦ New** |
| **Phase 10** | **Deep Personalisation Engine** | **Weeks 8–9** | **✦ New** |
| **Phase 11** | **Samsung Ecosystem Integration** | **Weeks 9–10** | **✦ New** |
| Phase 8 | Demo Prep & Wrap-up | Week 12 | — |

---

## Updated Risk Register

| Risk | Likelihood | Impact | Owner | Mitigation |
|------|-----------|--------|-------|------------|
| On-device model too slow for 12s cycle budget (Phase 12.1) | Medium | High | P2 | Use rule-based fallback for situation classification; reserve LLM only for message drafting |
| Samsung Accessory SDK requires physical Galaxy device for testing (Phase 11.2) | High | Medium | P1 | Build against SDK stubs; test on physical hardware in Week 10; mock data in demo mode |
| SmartThings OAuth approval delay (Phase 11.3) | Medium | Medium | P3 | Apply for API access in Week 1 alongside Google Cloud setup; SmartThings connection is additive, not required for core demo |
| Personalisation nudges feel creepy rather than helpful (Phase 10.2) | Medium | High | P4 | UX copy review by full team; user study in demo mode; "Stop suggesting this" must be one tap |
| `ReasoningPayload` increases action log DB size significantly (Phase 9.1) | Low | Low | P1 | JSON is small (~200 bytes per entry); at 50 actions/day, 6-month retention = ~1.8 MB |
| Galaxy Watch SDK not available on all test devices (Phase 11.1) | High | Low | P1 | Graceful null handling already enforced by Phase 1.2 degradation pattern; demo on a paired Watch device |

---

## Updated Success Metrics

| Metric | Target | Phase | Owner |
|--------|--------|-------|-------|
| Reasoning panel renders within 100ms of expansion tap | < 100ms | 9.2 | P1 |
| Nudge accuracy (user acts on nudge) | ≥ 60% of surfaced nudges | 10.2 | P4 |
| On-device inference latency (situation classification) | < 3 seconds | 12.1 | P2 |
| Galaxy Watch commute detection lead time vs GPS-only | +3–5 min earlier | 11.1 | P1 |
| Demo privacy segment — judges rate trust positively | Qualitative | 12.3 | P4 |