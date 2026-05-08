# ContextOS — Proactive Phone Orchestrator

> *An intelligent, privacy-first Android agent that anticipates your needs and acts on your behalf.*

---

## Table of Contents

1. [What is ContextOS?](#1-what-is-contextos)
2. [Architecture Overview](#2-architecture-overview)
3. [The Soul of OpenClaw](#3-the-soul-of-openclaw)
4. [The OpenClaw Orchestration Layer](#4-the-openclaw-orchestration-layer)
5. [The Skill System](#5-the-skill-system)
6. [The Agent Cycle — End to End](#6-the-agent-cycle--end-to-end)
7. [Configuration & Setup](#7-configuration--setup)
8. [Module Reference](#8-module-reference)
9. [Database Schema](#9-database-schema)
10. [Memory System](#10-memory-system)
11. [Privacy Architecture](#11-privacy-architecture)
12. [Samsung Ecosystem Integration](#12-samsung-ecosystem-integration)
13. [API Reference](#13-api-reference)
14. [Testing & Scenarios](#14-testing--scenarios)
15. [Glossary](#15-glossary)

---

## 1. What is ContextOS?

ContextOS is a **proactive phone orchestrator** for Android. It runs as a foreground
service that cycles every 15 minutes, collecting sensor data (battery, location,
calendar, audio, app usage), building a structured `SituationModel`, evaluating
registered **Skills** (action modules), and proactively executing or suggesting
context-appropriate actions — all without requiring manual user setup.

### Core Philosophy

| Principle | Description |
|---|---|
| **Proactive, not reactive** | The phone acts before you ask. |
| **Privacy by design** | Sensitive data stays on-device. Cloud inference is opt-in. |
| **Learn, don't configure** | The system learns routines and preferences over time. |
| **Graceful degradation** | Every LLM feature has a rule-based fallback. |
| **Stateless skills** | All state lives in Room DB. Skills are pure logic. |

### The Big Picture

```
  ┌─────────────────────────────────────────────────────────────────┐
  │                   ContextOS Agent Loop                          │
  │  ┌──────────┐   ┌──────────────┐   ┌──────────┐   ┌─────────┐  │
  │  │ Sensors  │ → │ OpenClaw AI  │ → │ Skills   │ → │ Action  │  │
  │  │ Collect  │   │ Analyze      │   │ Evaluate │   │ Dispatch│  │
  │  └──────────┘   └──────────────┘   └──────────┘   └─────────┘  │
  │       │                │                  │              │       │
  │       ▼                ▼                  ▼              ▼       │
  │  RawSensorData   SituationAnalysis   SkillResult    ActionLog   │
  └─────────────────────────────────────────────────────────────────┘
```

### Key Capabilities

| Capability | Skill | Trigger |
|---|---|---|
| Auto DND before meetings | `dnd_setter` | Event in ≤10 min, 2+ attendees |
| Low battery warning | `battery_warner` | Battery <20%, long meeting ahead |
| Launch navigation | `navigation_launcher` | In-person event in ≤30 min |
| Draft "running late" messages | `message_drafter` | Late for event with location |
| Surface relevant documents | `document_fetcher` | Pre-meeting preparation |
| Adaptive notification mode | `location_intelligence` | Per-location settings |
| Routine-based nudges | `personal_nudge` | Learned daily patterns |

---

## 2. Architecture Overview

### Module Dependency Graph

```
                           ┌─────────────┐
                           │    :app     │  (UI — Compose, Navigation)
                           └──────┬──────┘
                                  │
                ┌─────────────────┼─────────────────┐
                ▼                 ▼                   ▼
        ┌──────────────┐  ┌──────────────┐   ┌──────────────┐
        │ :core:service│  │ :core:skills │   │   (future)   │
        └──────┬───────┘  └──────┬───────┘   └──────────────┘
               │                 │
               │                 │
               ▼                 ▼
        ┌──────────────┐     ┌──────────────┐
        │ :core:memory │     │ :core:network│
        └──────┬───────┘     └──────┬───────┘
               │                    │
               └────────┬───────────┘
                        ▼
               ┌──────────────────┐
               │    :core:data    │
               │  (Room DB, DAOs, │
               │  SituationModel) │
               └──────────────────┘
```

### Module Responsibilities

| Module | Purpose |
|---|---|
| `:app` | Jetpack Compose UI, navigation, ViewModels |
| `:core:service` | Foreground service, agent loop, sensor collection, **OpenClaw** |
| `:core:skills` | `Skill` interface, all skill implementations |
| `:core:memory` | Routine, preference, and location learning |
| `:core:network` | Google APIs (Calendar, Gmail, Drive), Maps, SmartThings |
| `:core:data` | Room database, entities, DAOs, model classes |

### Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material3 |
| DI | Hilt (Dagger) |
| Database | Room (SQLite) with KSP |
| Preferences | DataStore Preferences |
| Background | Foreground Service + WorkManager + AlarmManager |
| Google APIs | NetHttpTransport + GsonFactory |
| LLM | HttpURLConnection to Gemini / Groq REST APIs |
| Serialization | kotlinx.serialization |
| Testing | JUnit, MockK, Robolectric, MockWebServer |

---

## 3. The Soul of OpenClaw

> **OpenClaw is not merely an API client — it is the beating heart of ContextOS,
> the intelligence layer that transforms raw sensor noise into meaningful action.**

### The Philosophy

The name **OpenClaw** evokes a deliberate grip — the firm yet gentle hold of a
predator that has assessed its environment and knows exactly when to strike.
OpenClaw embodies four core values:

#### 1. Awareness Without Intrusion

OpenClaw sees everything it needs to (battery, location, calendar, audio, app
usage) but touches nothing unnecessarily. Its "eyes" are the sensors; its "brain"
is the LLM; its "hands" are the skills. The separation is deliberate:

```
Sensors → OpenClaw (thinks) → Skills (acts)
```

#### 2. Humility in Intelligence

Every LLM call has a rule-based fallback. OpenClaw never assumes its AI will be
available. If Gemini is down, if Groq is rate-limited, if there's no network —
the system degrades gracefully to deterministic logic. This is not a weakness;
it is the **humility of production software**.

```kotlin
// RealOpenClawAgent.kt — every call has a fallback
override suspend fun analyzeSituation(model: SituationModel): SituationAnalysis {
    return try {
        val prompt = promptBuilder.buildSituationPrompt(model)
        val rawResponse = generateSingleTurn(reasoningModel, prompt, temperature = 0.3f)
        parseSituationAnalysis(rawResponse)
    } catch (e: Exception) {
        ruleBasedAnalysis(model)  // ← always safe
    }
}
```

#### 3. Learning as a Virtue

OpenClaw does not just react — it remembers. Through the memory system
(`RoutineMemoryManager`, `PreferenceMemoryManager`, `LocationMemoryManager`),
OpenClaw builds a model of who the user is:

- You leave for work at 8:30 AM on weekdays → *routine learned*
- You always approve the DND Setter before meetings → *preference remembered*
- You spend weekends at "Home" and weekdays at "Office" → *location labelled*

This memory is fed back into the OpenClaw prompt so every cycle becomes more
personalized than the last.

#### 4. Dual-Provider Resilience

OpenClaw speaks two languages — **Gemini** and **Groq** — and auto-detects which
one to use based on the API key prefix:

| Provider | Key Prefix | Endpoint |
|---|---|---|
| Google Gemini | anything else | `generativelanguage.googleapis.com` |
| Groq | `gsk_` | `api.groq.com` |

```kotlin
// build.gradle.kts — auto-detection
val isGroqKey = apiKey.startsWith("gsk_")
val defaultModel = if (isGroqKey) "openai/gpt-oss-120b" else "gemini-2.0-flash"
```

This means OpenClaw is **provider-agnostic by design**. If one provider goes down,
swap the key and the system adapts.

### The Soul in Code

The soul of OpenClaw is most visible in three places:

**1. The Prompt Builder** — where the system's understanding of the user is
translated into language the LLM can understand:

```kotlin
// OpenClawPromptBuilder.kt
fun buildSituationPrompt(model: SituationModel): String {
    return """
        You are ContextOS, a proactive phone assistant.
        Analyze the user's current situation and return a JSON object.

        ## Current State
        - Time: Mon 14:30
        - Location: Office
        - Battery: 73% (charging: false)
        - Audio: SILENT

        ## Calendar (next 8 hours)
        - [1] "Q3 Budget Review"
              starts in 12 min | 4 attendees
              location: Room 4B

        ## Available Skills
        - dnd_setter: enables DND before meetings
        - battery_warner: sends SMS when battery low
        - navigation_launcher: launches Maps navigation

        ## Instructions
        Return ONLY valid JSON matching this schema:
        {
          "currentContextLabel": "...",
          "urgencyLevel": <0-3>,
          "recommendedSkills": [...],
          "anomalyFlags": [...]
        }
    """.trimIndent()
}
```

**2. The Analysis Pipeline** — where sensor data is transformed into structured
insight via `SituationModeler` → `OpenClawAgent` → `SituationAnalysis`:

```kotlin
// SituationModeler.kt — the bridge
class SituationModeler @Inject constructor(
    private val openClawAgent: OpenClawAgent
) {
    suspend fun analyze(model: SituationModel): SituationAnalysis {
        return openClawAgent.analyzeSituation(model)
    }
}
```

**3. The Chat Interface** — where the user can converse with their device's
intelligence, augmented by live Google services context:

```kotlin
// RealOpenClawAgent.chat() — multi-turn conversation
override suspend fun chat(history: List<ChatTurn>): String {
    val augmentedHistory = augmentHistoryWithGoogleContext(history)
    return when (provider) {
        "groq" -> groqClient.generateMultiTurn(model, toGroqMessages(augmentedHistory), ...)
        else -> geminiClient.generateMultiTurn(model, toGeminiContents(augmentedHistory), ...)
    }
}
```

---

## 4. The OpenClaw Orchestration Layer

> **OpenClaw is the AI decision-making engine of ContextOS.** It is the layer
> that receives the `SituationModel` and returns structured analysis, message
> drafts, and conversational responses.

### 4.1 Package Structure

```
com.contextos.core.service.agent.openclaw/
├── OpenClawAgent.kt                    # Interface (the contract)
├── RealOpenClawAgent.kt                # Production implementation
├── MockOpenClawAgent.kt                # Rule-based test double
├── OpenClawPromptBuilder.kt            # Structured prompt construction
├── OpenClawModule.kt                   # Hilt DI module
├── GeminiApiClient.kt                  # Google Gemini REST client
├── GroqApiClient.kt                    # Groq (OpenAI-compatible) client
└── GoogleServicesContextProvider.kt    # Calendar/Gmail/Drive for chat
```

### 4.2 The OpenClawAgent Interface

```kotlin
// OpenClawAgent.kt — the core contract
data class ChatTurn(
    val role: String,    // "user", "model", or "system"
    val content: String,
)

interface OpenClawAgent {
    /** Analyze the current situation and return structured insight. */
    suspend fun analyzeSituation(model: SituationModel): SituationAnalysis

    /** Draft a contextually appropriate message. */
    suspend fun draftMessage(context: DraftingContext): String

    /** Multi-turn conversational chat with Google context augmentation. */
    suspend fun chat(history: List<ChatTurn>): String
}
```

### 4.3 Three Capabilities

#### Capability 1: Situation Analysis

Every 15-minute cycle, OpenClaw receives the `SituationModel` and produces
a `SituationAnalysis`:

```kotlin
// SituationAnalysis.kt — the output of OpenClaw reasoning
data class SituationAnalysis(
    val currentContextLabel: String,          // "Pre-meeting preparation"
    val urgencyLevel: Int,                    // 0-3
    val recommendedSkills: List<ActionRecommendation>,  // ranked by confidence
    val anomalyFlags: List<String>            // unusual observations
)

data class ActionRecommendation(
    val skillId: String,     // "dnd_setter"
    val confidence: Float,   // 0.0 - 1.0
    val reasoning: String    // "Meeting in 8 min, DND is off"
)
```

The flow:

```
SensorDataCollector
    → RawSensorData
    → SituationModelBuilder.build(raw)
    → SituationModel (with calendar, location label, memory, etc.)
    → SituationModeler.analyze(model)
        → OpenClawAgent.analyzeSituation(model)
            → OpenClawPromptBuilder.buildSituationPrompt(model)
            → GeminiApiClient / GroqApiClient
            → parseSituationAnalysis(raw LLM response)
    → SituationAnalysis
    → Stored on SituationModel.analysis
```

**Example output from OpenClaw:**

```json
{
  "currentContextLabel": "Pre-meeting preparation",
  "urgencyLevel": 2,
  "recommendedSkills": [
    {
      "skillId": "dnd_setter",
      "confidence": 0.95,
      "reasoning": "Q3 Budget Review starts in 8 minutes with 4 attendees — DND is currently off"
    },
    {
      "skillId": "document_fetcher",
      "confidence": 0.72,
      "reasoning": "Meeting has agenda documents in Drive that the user may need"
    }
  ],
  "anomalyFlags": [
    "Meeting in 8 minutes but user location does not match event location"
  ]
}
```

#### Capability 2: Message Drafting

When a skill like `message_drafter` needs to send a text, it creates a
`DraftingContext` and delegates to OpenClaw:

```kotlin
// DraftingContext.kt
data class DraftingContext(
    val recipientName: String,          // "Alex"
    val relationship: String,           // "colleague", "friend", "family"
    val reason: String,                 // "Running late"
    val estimatedTimeOfArrival: String?, // "15 min"
    val timeAvailable: String?,          // "2:00 PM"
    val backupNumber: String?            // "+1-555-0123"
)
```

The `OpenClawPromptBuilder` constructs a tone-aware prompt:

```
You are a smart assistant drafting a short, natural SMS message.

## Context
- Recipient: Alex (colleague)
- Reason: Running late
- ETA: 15 min

## Tone Guidelines
- Professional but friendly. No emoji. Brief.

## Instructions
Return ONLY the draft message text. No JSON. No quotes. Under 160 characters.
```

OpenClaw returns a natural-sounding draft like:

> Hey Alex, stuck in traffic — should be there by 15 min. Sorry!

#### Capability 3: Multi-Turn Chat

The chat capability allows users to converse naturally with OpenClaw. It
supports both Gemini and Groq, and **augments the conversation with live
Google services context**:

```kotlin
// GoogleServicesContextProvider.kt — keyword-aware context injection
suspend fun buildForChat(history: List<ChatTurn>): String {
    val latestUserText = history.lastOrNull { it.role == "user" }?.content.orEmpty()
    val lower = latestUserText.lowercase()

    val wantsCalendar = CALENDAR_TERMS.any { lower.contains(it) }
    val wantsGmail = MAIL_TERMS.any { lower.contains(it) }
    val wantsDrive = DRIVE_TERMS.any { lower.contains(it) }

    // Builds a context block from Room cache or live API
    if (wantsCalendar) sections += buildCalendarContext()
    if (wantsGmail) sections += buildGmailContext(latestUserText)
    if (wantsDrive) sections += buildDriveContext(latestUserText)
}
```

**Example interaction:**

```
User: "What's on my calendar today?"
OpenClaw (augmented):
  Google Calendar cached upcoming events:
  1. Q3 Budget Review at Mon 2:00 PM | location: Room 4B | attendees available
  2. Team Standup at Tue 9:30 AM | location: Virtual

  → "You have Q3 Budget Review at 2 PM in Room 4B, and a team standup tomorrow at 9:30 AM."
```

### 4.4 Dual-Provider Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     RealOpenClawAgent                            │
│                                                                  │
│   OPENCLAW_API_KEY                                               │
│        │                                                         │
│        ├── starts with "gsk_"? → GroqApiClient                  │
│        │       └─ POST https://api.groq.com/openai/v1/...      │
│        │       └─ models: openai/gpt-oss-120b (default)         │
│        │                                                         │
│        └── otherwise → GeminiApiClient                          │
│                └─ POST https://generativelanguage.googleapis..  │
│                └─ models: gemini-2.0-flash (default)            │
└──────────────────────────────────────────────────────────────────┘
```

### 4.5 Mock vs. Real

`OpenClawModule` selects the implementation at startup:

```kotlin
@Provides @Singleton
fun provideOpenClawAgent(
    realAgent: RealOpenClawAgent,
    mockAgent: MockOpenClawAgent,
): OpenClawAgent {
    val useMock = BuildConfig.OPENCLAW_USE_MOCK
    val apiKeySet = BuildConfig.OPENCLAW_API_KEY.isNotEmpty()

    return if (useMock || !apiKeySet) {
        mockAgent  // Rule-based, simulated delays (1.5s/1s/800ms)
    } else {
        realAgent  // Real LLM calls
    }
}
```

The `MockOpenClawAgent` uses identical rule-based fallback logic as
`RealOpenClawAgent`, ensuring behavior is consistent whether the LLM is
available or not.

### 4.6 Prompt Builder Details

The `OpenClawPromptBuilder` constructs two types of prompts with strict
token budgets:

| Prompt | Budget | Temperature | Model |
|---|---|---|---|
| Situation Analysis | ≤ 2,000 tokens | 0.3 | `gemini-2.0-flash` |
| Message Drafting | ≤ 800 tokens | 0.7 | `gemini-2.0-flash-lite` |

**Situation prompt structure:**
1. System identity ("You are ContextOS...")
2. Current state (time, location, battery, WiFi, audio)
3. Calendar (up to 3 events, capped for token savings)
4. App usage (top 3 apps in last hour)
5. Learned patterns (from memory system)
6. Available skills (with descriptions)
7. Output format instructions (JSON schema)

**Drafting prompt structure:**
1. System identity ("You are a smart assistant...")
2. Recipient, relationship, reason, ETA
3. Tone guidelines (per relationship type)
4. Output format instructions (plain text, <160 chars)

### 4.7 API Client Details

Both clients use `HttpURLConnection` directly (no Retrofit dependency):

```kotlin
// GeminiApiClient.kt — key excerpt
suspend fun generateMultiTurn(
    model: String,
    contents: List<GeminiContent>,
    temperature: Float = 0.7f,
    maxOutputTokens: Int = 2048,
): String = withContext(Dispatchers.IO) {
    val endpoint = BuildConfig.OPENCLAW_API_ENDPOINT
    val apiKey = BuildConfig.OPENCLAW_API_KEY
    val url = URL("$endpoint/$model:generateContent?key=$apiKey")
    // POST with HttpURLConnection...
}
```

Both clients include:
- Quota exhaustion detection (`RESOURCE_EXHAUSTED` for Gemini)
- Timeout handling (15s connect, 30s read)
- Detailed error logging
- Structured DTOs with kotlinx.serialization

### 4.8 Feature Flags

All OpenClaw behavior is controlled by `BuildConfig` fields from
`local.properties`:

| Flag | Type | Default | Effect |
|---|---|---|---|
| `OPENCLAW_USE_MOCK` | boolean | `false` | Use rule-based mock agent |
| `OPENCLAW_ENABLE_REASONING` | boolean | `true` | Enable LLM for situation analysis |
| `OPENCLAW_ENABLE_DRAFTING` | boolean | `true` | Enable LLM for message drafting |
| `OPENCLAW_API_PROVIDER` | String | auto | `"gemini"` or `"groq"` |
| `OPENCLAW_REASONING_MODEL` | String | `gemini-2.0-flash` | Model for analysis |
| `OPENCLAW_DRAFTING_MODEL` | String | `gemini-2.0-flash-lite` | Model for drafting |
| `OPENCLAW_CHAT_MODEL` | String | `gemini-2.0-flash` | Model for chat |

---

## 5. The Skill System

> **Skills are the "hands" of ContextOS** — pure, stateless modules that inspect
> the `SituationModel` and decide whether to act.

### 5.1 The Skill Interface

```kotlin
// Skill.kt — every skill must implement this
interface Skill {
    val id: String          // Unique, stable identifier (e.g., "dnd_setter")
    val name: String        // Human-readable name
    val description: String // What this skill does

    /** Pure check — no I/O, no side effects, <5ms */
    fun shouldTrigger(model: SituationModel): Boolean

    /** Execute the action — must complete within cycle budget */
    suspend fun execute(model: SituationModel): SkillResult
}
```

### 5.2 Rules for shouldTrigger()

| Rule | Rationale |
|---|---|
| **Must be pure** | No side effects, no I/O, no mutations |
| **Must be fast** | Called on every skill every cycle — <5ms |
| **No coroutines** | Non-suspending by design |
| **No exceptions** | Return `false` instead |
| **Deterministic** | Same model → same result |

### 5.3 Rules for execute()

| Rule | Rationale |
|---|---|
| **Complete within budget** | 12s max for all skills combined |
| **Handle cancellation** | Use cooperative cancellation |
| **Never throw** | Return `SkillResult.Failure` |
| **No UI thread** | Runs on background dispatcher |

### 5.4 SkillResult — The Outcome Contract

```kotlin
sealed class SkillResult {
    data class Success(
        val description: String,
        val outcome: ActionOutcome = ActionOutcome.SUCCESS,
    ) : SkillResult()

    data class PendingConfirmation(
        val description: String,
        val confirmationMessage: String,
        val pendingAction: suspend () -> SkillResult,
    ) : SkillResult()

    data class Failure(
        val description: String,
        val error: Throwable? = null,
        val outcome: ActionOutcome = ActionOutcome.FAILURE,
    ) : SkillResult()

    data class Skipped(val reason: String) : SkillResult()
}
```

### 5.5 Registered Skills

| ID | Class | Phase | What It Does |
|---|---|---|---|
| `system.phase_one_heartbeat` | `PhaseOneHeartbeatSkill` | 1.x | Logs every cycle (stub) |
| `dnd_setter` | `DndSetterSkill` | 2.1 | Auto-enables DND before meetings |
| `battery_warner` | `BatteryWarnerSkill` | 2.2 | SMS emergency contact on low battery |
| `navigation_launcher` | `NavigationLauncherSkill` | 2.3 | Opens Google Maps navigation |
| `message_drafter` | `MessageDrafterSkill` | 4.1 | Drafts running-late messages |
| `document_fetcher` | `DocumentFetcherSkill` | 4.2 | Surfaces Drive/Gmail docs pre-meeting |
| `location_intelligence` | `LocationIntelligenceSkill` | 4.3 | Per-location notification mode |
| `personal_nudge` | `PersonalNudgeSkill` | 10.2 | Proactive routine-based nudges |

### 5.6 Skill Registration (Hilt Multi-Binding)

```kotlin
// SkillsModule.kt — registering skills
@Module
@InstallIn(SingletonComponent::class)
abstract class SkillsModule {
    @Multibinds
    abstract fun bindSkillSet(): Set<Skill>  // empty set fallback

    @Binds @IntoSet
    abstract fun bindDndSetterSkill(skill: DndSetterSkill): Skill

    @Binds @IntoSet
    abstract fun bindBatteryWarnerSkill(skill: BatteryWarnerSkill): Skill

    @Binds @IntoSet
    abstract fun bindNavigationLauncherSkill(skill: NavigationLauncherSkill): Skill

    @Binds @IntoSet
    abstract fun bindMessageDrafterSkill(skill: MessageDrafterSkill): Skill

    @Binds @IntoSet
    abstract fun bindDocumentFetcherSkill(skill: DocumentFetcherSkill): Skill

    @Binds @IntoSet
    abstract fun bindLocationIntelligenceSkill(skill: LocationIntelligenceSkill): Skill

    @Binds @IntoSet
    abstract fun bindPersonalNudgeSkill(skill: PersonalNudgeSkill): Skill
}
```

### 5.7 Writing a Custom Skill

Skills are registered via a single abstract binding. Here is a complete example:

```kotlin
// Skill.kt
interface Skill {
    val id: String
    val name: String
    val description: String
    fun shouldTrigger(model: SituationModel): Boolean
    suspend fun execute(model: SituationModel): SkillResult
}
```

#### Step 1: Implement the Skill

```kotlin
// MeetingPreparerSkill.kt
@Singleton
class MeetingPreparerSkill @Inject constructor(
    private val context: Context,
) : Skill {
    override val id = "meeting_preparer"
    override val name = "Meeting Preparer"
    override val description = "Reminds you to prepare before important meetings."

    override fun shouldTrigger(model: SituationModel): Boolean {
        val event = model.nextCalendarEvent ?: return false
        val minutesUntil = (event.startTime - model.currentTime) / 60_000
        return minutesUntil in 0..15 && event.attendees.size >= 2
    }

    override suspend fun execute(model: SituationModel): SkillResult {
        val event = model.nextCalendarEvent ?: return SkillResult.Skipped("No event")
        return SkillResult.Success(
            description = "Reminded you to prepare for '${event.title}'"
        )
    }
}
```

#### Step 2: Register with Hilt

```kotlin
// In SkillsModule.kt, add:
@Binds @IntoSet
abstract fun bindMeetingPreparerSkill(skill: MeetingPreparerSkill): Skill
```

#### Step 3: Inform OpenClaw

Add the skill to `OpenClawPromptBuilder.buildSituationPrompt()` under
"Available Skills" so the LLM knows it exists:

```kotlin
sb.appendLine("- meeting_preparer: reminds user to prepare before important meetings")
```

### 5.8 Skill Execution Flow (with OpenClaw)

```
ContextAgent.runCycle()
    │
    ├── SensorDataCollector.collect() → RawSensorData
    │
    ├── SituationModelBuilder.build(raw) → SituationModel
    │       │
    │       └── SituationModeler.analyze(model)
    │               └── OpenClawAgent.analyzeSituation(model)  ← OpenClaw!
    │                       └── Returns SituationAnalysis
    │
    ├── For each skill in SkillRegistry.skills:
    │       └── skill.shouldTrigger(model) → true/false
    │
    ├── For each triggered skill:
    │       └── skill.execute(model) → SkillResult
    │
    └── ActionDispatcher.dispatch(skill, result, model)
            ├── SkillResult.Success → log SUCCESS
            ├── SkillResult.PendingConfirmation → if autoApprove: execute; else: log PENDING
            ├── SkillResult.Failure → log FAILURE
            └── SkillResult.Skipped → log SKIPPED
```

### 5.9 Complete Skill Examples

#### DndSetterSkill — Full Implementation

```kotlin
@Singleton
class DndSetterSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actionLogRepository: ActionLogRepository,
    private val userPreferenceDao: UserPreferenceDao,
) : Skill {
    override val id: String = "dnd_setter"
    override val name: String = "DND Setter"
    override val description: String =
        "Auto-enables Do Not Disturb before meetings with 2+ attendees."

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val handler = Handler(Looper.getMainLooper())

    override fun shouldTrigger(model: SituationModel): Boolean {
        val event = model.nextCalendarEvent ?: return false
        if (event.attendees.size < 2) return false

        val minutesUntil = TimeUnit.MILLISECONDS.toMinutes(
            event.startTime - model.currentTime
        )
        if (minutesUntil !in 0..10) return false
        if (notificationManager.currentInterruptionFilter
            == NotificationManager.INTERRUPTION_FILTER_NONE) return false
        if (!hasDndAccess()) return false

        return true
    }

    override suspend fun execute(model: SituationModel): SkillResult {
        val event = model.nextCalendarEvent
            ?: return SkillResult.Skipped("No calendar event found")

        val dismissalsInLast7Days = actionLogRepository.countDismissalsSince(
            skillId = id,
            sinceMs = model.currentTime - 7L * 24 * 60 * 60 * 1000,
        )
        if (dismissalsInLast7Days > 2) {
            return SkillResult.Skipped(
                "User has dismissed this skill multiple times recently"
            )
        }

        return try {
            enableDnd()
            scheduleDisableDnd(event.endTime)

            val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            SkillResult.Success(
                description = "Enabled DND until ${
                    formatter.format(Date(event.endTime))
                } before '${event.title}'",
                outcome = ActionOutcome.SUCCESS,
            )
        } catch (e: SecurityException) {
            SkillResult.Failure(
                description = "DND permission not granted",
                error = e,
                outcome = ActionOutcome.FAILURE,
            )
        }
    }

    private fun hasDndAccess(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted

    private fun enableDnd() {
        if (!hasDndAccess()) throw SecurityException("DND access not granted")
        notificationManager.setInterruptionFilter(
            NotificationManager.INTERRUPTION_FILTER_NONE
        )
    }

    private fun scheduleDisableDnd(eventEndTimeMs: Long) {
        val delayMs = eventEndTimeMs + 5 * 60 * 1000 - System.currentTimeMillis()
        if (delayMs > 0) {
            handler.postDelayed({
                if (hasDndAccess()) {
                    notificationManager.setInterruptionFilter(
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                }
            }, delayMs)
        }
    }
}
```

#### BatteryWarnerSkill — With PendingConfirmation

```kotlin
@Singleton
class BatteryWarnerSkill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val actionLogRepository: ActionLogRepository,
    private val userPreferenceDao: UserPreferenceDao,
) : Skill {
    override val id = "battery_warner"
    override val name = "Battery Warner"
    override val description =
        "Warns your emergency contact when battery is low before a long meeting."

    override fun shouldTrigger(model: SituationModel): Boolean {
        if (model.batteryLevel >= 20) return false
        if (model.isCharging) return false
        val nextEvent = model.nextCalendarEvent ?: return false

        val durationMin = TimeUnit.MILLISECONDS.toMinutes(
            nextEvent.endTime - nextEvent.startTime
        )
        return durationMin >= 90
    }

    override suspend fun execute(model: SituationModel): SkillResult {
        val contact = preferencesManager.emergencyContacts.first().firstOrNull()
        if (contact?.phone.isNullOrEmpty()) {
            return SkillResult.Failure("No emergency contact configured")
        }

        val message = "Hi ${contact!!.name}, my battery is at ${model.batteryLevel}%. " +
            "I have meetings for the next few hours."

        return if (userPreferenceDao.getBySkillId(id)?.autoApprove == true) {
            trySendSms(contact.phone, message, contact.name)
        } else {
            SkillResult.PendingConfirmation(
                description = "Send low-battery warning to ${contact.name}",
                confirmationMessage = message,
                pendingAction = { trySendSms(contact.phone, message, contact.name) },
            )
        }
    }

    private fun trySendSms(phone: String, msg: String, name: String): SkillResult {
        return try {
            val smsManager = context.getSystemService(
                android.telephony.SmsManager::class.java
            )
            smsManager.sendTextMessage(phone, null, msg, null, null)
            SkillResult.Success("Sent battery warning SMS to $name")
        } catch (e: SecurityException) {
            SkillResult.Failure("SMS permission not granted", e)
        }
    }
}
```

---

## 6. The Agent Cycle — End to End

### 6.1 Lifecycle

```
BOOT_COMPLETED
    │
    ▼
BootReceiver
    │
    ├── startService → ContextOSService (Foreground Service)
    │       │
    │       └── Agent Loop (coroutine):
    │               while (service is running):
    │                   runCycle()           ← every 15 minutes
    │                   delay(15 * 60 * 1000)
    │
    └── scheduleWork → AgentCycleWorker (WorkManager fallback)
    └── scheduleAlarm → AlarmManager (Doze mode wakeup)
```

### 6.2 Cycle Sequence

```
╔══════════════════════════════════════════════════════════════════╗
║                    15-MINUTE CYCLE (900s)                       ║
║                                                                ║
║  ┌─── Computation Phase (≤12s) ───────────────────────────┐    ║
║  │  1. Sensor collection              ≤3s                 │    ║
║  │  2. Calendar cache refresh         ≤4s (if stale)      │    ║
║  │  3. SituationModel construction    ≤1s                 │    ║
║  │  4. OpenClaw situation analysis    ≤2s (LLM or rules)  │    ║
║  │  5. shouldTrigger() evaluation     ≤1s                 │    ║
║  │  6. Memory lookups                 ≤2s                 │    ║
║  └─────────────────────────────────────────────────────────┘    ║
║                                                                ║
║  ┌─── Dispatch Phase (≤3s) ───────────────────────────────┐    ║
║  │  7. Notification/overlay display    ≤1s                │    ║
║  │  8. Auto-approved action execution   ≤1s                │    ║
║  │  9. ActionLog writes to Room        ≤1s                │    ║
║  └─────────────────────────────────────────────────────────┘    ║
╚══════════════════════════════════════════════════════════════════╝
```

### 6.3 ContextAgent.runCycle() — Full Walkthrough

```kotlin
// ContextAgent.kt — the central orchestrator
suspend fun runCycle() {
    try {
        withTimeout(12_000) { executeCycle() }
    } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "Cycle exceeded 12s budget")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Cycle failed unexpectedly", e)
    }
}

private suspend fun executeCycle() {
    // 1. Collect sensors
    val raw = sensorCollector.collect()
    // 2. Build situation model (includes OpenClaw analysis)
    val model = modelBuilder.build(raw)
    // 3. Evaluate all skills
    val allSkills = skillRegistry.skills
    val triggeredSkills = mutableListOf<Skill>()

    for (skill in allSkills) {
        if (skill.shouldTrigger(model)) {
            triggeredSkills.add(skill)
        }
    }
    // 4. Execute triggered skills
    for (skill in triggeredSkills) {
        val result = skill.execute(model)
        dispatcher.dispatch(skill, result, model, confidence = 0.85f)
    }
    // 5. Log dismissed skills
    dispatcher.dispatch(skill, SkillResult.Skipped("did not trigger"), model, 0.30f)
    // 6. Record heartbeat
    healthMonitor.recordCycleComplete()
}
```

### 6.4 SituationModelBuilder.build() — Building the Model

```kotlin
// SituationalModelBuilder.kt (simplified)
suspend fun build(raw: RawSensorData): SituationModel {
    val events = calendarDao.getUpcomingEvents(afterMs = System.currentTimeMillis())

    val memorySummary = memorySummaryBuilder.build(
        dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
        timeSlot = currentTimeSlot(),
        latitude = raw.location?.latitude,
        longitude = raw.location?.longitude,
    )

    val model = SituationModel(
        currentTime = System.currentTimeMillis(),
        currentLocation = raw.location,
        batteryLevel = raw.batteryLevel,
        isCharging = raw.isCharging,
        nextCalendarEvent = events.firstOrNull(),
        upcomingCalendarEvents = events,
        recentAppUsage = raw.appUsage,
        ambientAudioContext = raw.audioContext,
        memorySummary = memorySummary,
        locationLabel = locationMemory.getLabelForLocation(raw.location),
        wifiSsid = raw.wifiSsid,
        isMobileDataConnected = raw.isMobileData,
    )

    // Run OpenClaw analysis
    val analysis = situationModeler.analyze(model)
    return model.copy(analysis = analysis)
}
```

### 6.5 Action Dispatcher — Routing Results

```kotlin
// ActionDispatcher.kt
suspend fun dispatch(
    skill: Skill,
    result: SkillResult,
    model: SituationModel,
    confidence: Float,
) {
    val prefs = userPreferenceDao.getBySkillId(skill.id)
    val autoApprove = prefs?.autoApprove ?: false

    when (result) {
        is SkillResult.Success -> {
            log(now, skill, result.description, ActionOutcome.SUCCESS, true, ...)
        }
        is SkillResult.PendingConfirmation -> {
            if (autoApprove) {
                val innerResult = result.pendingAction()
                dispatch(skill, innerResult, model, confidence)  // recurse
            } else {
                log(now, skill, result.confirmationMessage,
                    ActionOutcome.PENDING_USER_CONFIRMATION, false, ...)
            }
        }
        is SkillResult.Failure ->
            log(now, skill, result.description, ActionOutcome.FAILURE, true, ...)
        is SkillResult.Skipped ->
            log(now, skill, result.reason, ActionOutcome.SKIPPED, true, ...)
    }
}
```

---

## 7. Configuration & Setup

### 7.1 Prerequisites

- Android Studio Hedgehog (2023.1.1+) or later
- JDK 17
- Android SDK 34
- A Google Cloud project with the Generative Language API enabled
- (Optional) A Groq API key for the Groq provider

### 7.2 Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/your-org/ContextOS.git
cd ContextOS

# 2. Set up local.properties
cat > local.properties << EOF
sdk.dir=/path/to/Android/Sdk
MAPS_API_KEY=your_maps_api_key
OPENCLAW_API_KEY=your_gemini_or_groq_api_key
OPENCLAW_ENABLE_REASONING=true
OPENCLAW_ENABLE_DRAFTING=true
EOF

# 3. (Optional) Load environment variables
source env.sh

# 4. Build
./gradlew assembleDebug

# 5. Install
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 7.3 Environment Variables (`env.sh`)

```bash
#!/bin/bash
# OpenClaw Environment Variables
export OPENCLAW_API_ENDPOINT="https://generativelanguage.googleapis.com/v1beta/models"
export OPENCLAW_API_KEY="your_gemini_api_key"

# Model selection
export OPENCLAW_REASONING_MODEL="gemini-2.0-flash"
export OPENCLAW_DRAFTING_MODEL="gemini-2.0-flash-lite"

# Feature flags
export OPENCLAW_ENABLE_REASONING=true
export OPENCLAW_ENABLE_DRAFTING=true

echo "✅ OpenClaw environment loaded"
```

### 7.4 `local.properties` Reference

```properties
# Required
sdk.dir=/path/to/Android/Sdk

# Google Maps (for NavigationLauncherSkill, Distance Matrix)
MAPS_API_KEY=AIzaSy...

# OpenClaw — set this to use LLM-powered reasoning
OPENCLAW_API_KEY=AIzaSy...          # Gemini key
# OPENCLAW_API_KEY=gsk_...          # OR Groq key (starts with "gsk_")

# OpenClaw Feature Flags (optional, defaults shown)
OPENCLAW_USE_MOCK=false
OPENCLAW_ENABLE_REASONING=true
OPENCLAW_ENABLE_DRAFTING=true
OPENCLAW_REASONING_MODEL=gemini-2.0-flash
OPENCLAW_DRAFTING_MODEL=gemini-2.0-flash-lite
OPENCLAW_CHAT_MODEL=gemini-2.0-flash
```

### 7.5 Build Configuration

The `:core:service/build.gradle.kts` reads `local.properties` and generates
`BuildConfig` fields:

```kotlin
// :core:service/build.gradle.kts
val apiKey = localProperties.getProperty("OPENCLAW_API_KEY", "")
val isGroqKey = apiKey.startsWith("gsk_")

android {
    defaultConfig {
        buildConfigField("String", "OPENCLAW_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "OPENCLAW_API_ENDPOINT",
            "\"${if (isGroqKey) "https://api.groq.com/openai/v1"
              else "https://generativelanguage.googleapis.com/v1beta/models"}\"")
        buildConfigField("String", "OPENCLAW_API_PROVIDER",
            "\"${if (isGroqKey) "groq" else "gemini"}\"")
        buildConfigField("boolean", "OPENCLAW_ENABLE_REASONING",
            localProperties.getProperty("OPENCLAW_ENABLE_REASONING", "true"))
        buildConfigField("boolean", "OPENCLAW_ENABLE_DRAFTING",
            localProperties.getProperty("OPENCLAW_ENABLE_DRAFTING", "true"))
        buildConfigField("String", "OPENCLAW_REASONING_MODEL",
            "\"${localProperties.getProperty("OPENCLAW_REASONING_MODEL", defaultModel)}\"")
        buildConfigField("String", "OPENCLAW_DRAFTING_MODEL",
            "\"${localProperties.getProperty("OPENCLAW_DRAFTING_MODEL", defaultModel)}\"")
        buildConfigField("String", "OPENCLAW_CHAT_MODEL",
            "\"${localProperties.getProperty("OPENCLAW_CHAT_MODEL", defaultModel)}\"")
        buildConfigField("boolean", "OPENCLAW_USE_MOCK",
            localProperties.getProperty("OPENCLAW_USE_MOCK", "false"))
    }
}
```

### 7.6 Required Android Permissions

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS for location-aware skills |
| `ACCESS_COARSE_LOCATION` | Network-based location fallback |
| `READ_CALENDAR` | Calendar event access |
| `READ_SMS` | SMS drafting context |
| `SEND_SMS` | Battery warning SMS |
| `RECORD_AUDIO` | Ambient audio classification |
| `PACKAGE_USAGE_STATS` | Recent app usage |
| `ACCESS_NOTIFICATION_POLICY` | DND control |
| `POST_NOTIFICATIONS` | User notifications |
| `FOREGROUND_SERVICE` | Background service |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on boot |
| `USE_EXACT_ALARM` | Doze mode wakeup |

---

## 8. Module Reference

### 8.1 `:app` — UI Layer

```
app/src/main/kotlin/com/contextos/app/
├── ContextOSApplication.kt          # @HiltAndroidApp
├── MainActivity.kt                  # Single-activity entry point
└── ui/
    ├── navigation/
    │   ├── Screen.kt                # Route definitions
    │   ├── NavGraph.kt              # Navigation graph
    │   ├── StartDestinationViewModel.kt
    │   └── OnboardingCompleteViewModel.kt
    ├── onboarding/
    │   ├── OnboardingScreen.kt      # Welcome + Google Sign-In
    │   └── GoogleSignInViewModel.kt
    ├── dashboard/
    │   ├── DashboardScreen.kt       # Main dashboard
    │   └── ActionLogScreen.kt       # Action log feed
    ├── settings/
    │   └── SettingsScreen.kt        # Preferences + connections
    ├── detail/
    │   └── ActionDetailScreen.kt    # Detailed action view
    ├── components/
    │   ├── ReasoningCard.kt         # Structured reasoning UI
    │   ├── PrivacyDataCard.kt       # Data inventory
    │   ├── LearningSection.kt       # Routine learning progress
    │   ├── CloudConsentDialog.kt    # Cloud inference consent
    │   ├── EcosystemRoadmapDiagram.kt
    │   └── SmartThingsCard.kt
    ├── theme/
    │   ├── Theme.kt
    │   ├── Color.kt
    │   └── Type.kt
    └── background/
        └── RippleGridBackground.kt
```

### 8.2 `:core:service` — Orchestration Layer

This module contains the **agent loop**, **OpenClaw agent**, **sensor collection**,
and **notification management**. It is the brain of ContextOS.

```
core/service/src/main/kotlin/com/contextos/core/service/
├── ContextOSService.kt              # Foreground service (entry point)
├── ContextOSServiceManager.kt       # Start/stop helpers
├── AgentCycleWorker.kt              # WorkManager periodic worker
├── BootReceiver.kt                  # BOOT_COMPLETED receiver
├── SensorDataCollector.kt           # Battery, GPS, audio, usage, WiFi
├── SituationModelBuilder.kt         # Builds SituationModel from raw data
├── ServiceHealthMonitor.kt          # Uptime + heartbeat tracking
├── ReasoningBuilder.kt              # Human-readable reasoning payloads
├── agent/
│   ├── ContextAgent.kt              # Central orchestrator (agent loop)
│   ├── ActionDispatcher.kt          # Routes SkillResults
│   ├── SituationModeler.kt          # Delegates to OpenClaw
│   ├── MemorySummaryBuilder.kt      # Aggregates memory manager output
│   ├── MessageDraftingEngine.kt     # LLM + fallback drafting
│   └── openclaw/                    # ★ THE OPENCLAW LAYER ★
│       ├── OpenClawAgent.kt         # Interface
│       ├── RealOpenClawAgent.kt     # Production impl (Gemini/Groq)
│       ├── MockOpenClawAgent.kt     # Rule-based fallback
│       ├── OpenClawModule.kt        # Hilt DI (mock vs real)
│       ├── OpenClawPromptBuilder.kt # Structured prompts
│       ├── GeminiApiClient.kt       # Gemini REST client
│       ├── GroqApiClient.kt         # Groq REST client
│       └── GoogleServicesContextProvider.kt
├── notifications/
│   ├── ContextOSNotificationManager.kt  # 5 notification channels
│   └── NotificationActionReceiver.kt
├── samsung/
│   ├── WearableDataReceiver.kt      # Galaxy Watch integration
│   └── BudsStateReceiver.kt         # Galaxy Buds integration
├── privacy/
│   ├── InferenceRouter.kt           # On-device vs cloud routing
│   ├── DataMaskingLayer.kt          # PII masking for cloud calls
│   └── DataRetentionWorker.kt       # Periodic data cleanup
└── di/
    ├── ServiceModule.kt
    └── AgentBindingsModule.kt
```

### 8.3 `:core:skills` — Skill Implementations

```
core/skills/src/main/kotlin/com/contextos/core/skills/
├── Skill.kt                         # Core interface
├── SkillResult.kt                   # Sealed result class
├── SkillRegistry.kt                 # Hilt-injected skill set
├── di/
│   └── SkillsModule.kt              # @IntoSet bindings
├── PhaseOneHeartbeatSkill.kt        # Stub
├── DndSetterSkill.kt                # Auto DND before meetings
├── BatteryWarnerSkill.kt            # Low battery SMS warning
├── NavigationLauncherSkill.kt       # Google Maps navigation
├── MessageDrafterSkill.kt           # Running-late message drafts
├── DocumentFetcherSkill.kt          # Pre-meeting document surfacing
├── LocationIntelligenceSkill.kt     # Per-location notification mode
└── PersonalNudgeSkill.kt            # Routine-based nudges
```

### 8.4 `:core:data` — Database & Models

```
core/data/src/main/kotlin/com/contextos/core/data/
├── db/
│   ├── ContextOSDatabase.kt         # @Database (version 4)
│   ├── dao/
│   │   ├── ActionLogDao.kt
│   │   ├── UserPreferenceDao.kt
│   │   ├── CalendarEventCacheDao.kt
│   │   ├── RoutineMemoryDao.kt
│   │   ├── PreferenceMemoryDao.kt
│   │   ├── LocationMemoryDao.kt
│   │   ├── ConfirmedRoutineDao.kt
│   │   ├── WearableContextDao.kt
│   │   └── BudsContextDao.kt
│   └── entity/                      # Room entities
│       ├── ActionLogEntity.kt
│       ├── UserPreferenceEntity.kt
│       ├── CalendarEventCacheEntity.kt
│       ├── RoutineMemoryEntity.kt
│       ├── PreferenceMemoryEntity.kt
│       ├── LocationMemoryEntity.kt
│       ├── ConfirmedRoutineEntity.kt
│       ├── WearableContextEntity.kt
│       └── BudsContextEntity.kt
├── model/                           # Domain models
│   ├── SituationModel.kt
│   ├── SituationAnalysis.kt
│   ├── RawSensorData.kt
│   ├── ActionLog.kt
│   ├── ReasoningPayload.kt
│   ├── DraftingContext.kt
│   ├── MessageDrafter.kt
│   └── RoutineType.kt
├── repository/
│   ├── ActionLogRepository.kt
│   ├── UserPreferenceRepository.kt
│   └── DataInventoryRepository.kt
├── preferences/
│   └── PreferencesManager.kt
└── di/
    ├── DatabaseModule.kt
    └── DataModule.kt
```

### 8.5 `:core:memory` — Learning System

```
core/memory/src/main/kotlin/com/contextos/core/memory/
├── RoutineMemoryManager.kt          # Weekly pattern learning
├── PreferenceMemoryManager.kt       # Per-skill preference learning
├── LocationMemoryManager.kt         # Place label inference
├── PersonalRoutineService.kt        # Named routine types
└── di/
    └── MemoryModule.kt
```

### 8.6 `:core:network` — API Clients

```
core/network/src/main/kotlin/com/contextos/core/network/
├── GoogleAuthManager.kt             # OAuth 2.0 management
├── CalendarApiClient.kt             # Google Calendar v3
├── GmailApiClient.kt                # Gmail v1
├── DriveApiClient.kt                # Google Drive v3
├── MapsDistanceMatrixClient.kt      # Google Maps Distance Matrix
├── CalendarSyncWorker.kt            # Periodic calendar sync
├── SmartThingsClient.kt             # Samsung SmartThings API
├── NetworkUtils.kt                  # RetryWithBackoff utility
└── di/
    └── NetworkModule.kt
```

---

## 9. Database Schema

### Entity-Relationship Diagram

```
┌─────────────────┐    ┌──────────────────────┐
│   action_log    │    │  user_preferences    │
├─────────────────┤    ├──────────────────────┤
│ id (PK)         │    │ id (PK)              │
│ timestampMs     │    │ skill_id (UNIQUE)    │
│ skillId         │    │ autoApprove          │
│ skillName       │    │ sensitivityLevel     │
│ description     │    └──────────────────────┘
│ wasAutoApproved │
│ userOverride    │    ┌──────────────────────┐
│ situationSnapshot│   │ calendar_event_cache │
│ reasoningPayload │   ├──────────────────────┤
│ outcome         │    │ eventId (PK)         │
└─────────────────┘    │ title                │
                       │ ...                  │
┌─────────────────┐    │ lastFetched          │
│ routine_memory  │    └──────────────────────┘
├─────────────────┤
│ id (PK)         │    ┌──────────────────────┐
│ day_of_week     │    │ preference_memory    │
│ time_slot (UQ)  │    ├──────────────────────┤
│ expectedActivity│    │ id (PK)              │
│ confidence      │    │ skill_id +           │
│ observationCount│    │   context_hash (UQ)  │
│ lastObservedMs  │    │ userChoice           │
└─────────────────┘    │ frequency            │
                       │ lastObservedMs       │
┌─────────────────┐    └──────────────────────┘
│ location_memory │
├─────────────────┤    ┌──────────────────────┐
│ latLngHash (PK) │    │ confirmed_routine    │
│ centerLatitude  │    ├──────────────────────┤
│ centerLongitude │    │ id (PK)              │
│ inferredLabel   │    │ routineType          │
│ visitCount      │    │ day_of_week +        │
│ typicalArrival  │    │   time_slot_start(UQ)│
│ typicalDeparture│    │ confidence           │
│ lastVisitedMs   │    │ observationCount     │
└─────────────────┘    │ lastObserved         │
                       │ suggestedAction      │
┌─────────────────┐    │ isActive             │
│ wearable_context│    └──────────────────────┘
├─────────────────┤
│ id (PK)         │    ┌──────────────────────┐
│ timestamp       │    │ buds_context         │
│ activityType    │    ├──────────────────────┤
│ heartRate       │    │ id (PK)              │
│ stepCountDelta  │    │ timestamp            │
│ deviceConnected │    │ budsInEar            │
└─────────────────┘    │ ancActive            │
                       │ ambientSoundActive   │
                       │ deviceConnected      │
                       └──────────────────────┘
```

### 9.1 `action_log`

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER (PK, auto) | Surrogate primary key |
| `timestampMs` | INTEGER | Epoch millis of execution |
| `skillId` | TEXT | Stable skill identifier |
| `skillName` | TEXT | Human-readable name |
| `description` | TEXT | Description from SkillResult |
| `wasAutoApproved` | INTEGER (bool) | 1 = auto-approved |
| `userOverride` | TEXT? | "APPROVED" / "DISMISSED" / "MODIFIED" |
| `situationSnapshot` | TEXT | JSON-serialized SituationModel |
| `reasoningPayload` | TEXT | JSON-serialized ReasoningPayload |
| `outcome` | TEXT | SUCCESS / FAILURE / PENDING / SKIPPED |

### 9.2 `user_preferences`

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER (PK, auto) | Surrogate primary key |
| `skill_id` | TEXT (UNIQUE) | Skill identifier |
| `autoApprove` | INTEGER (bool) | Auto-execute without asking |
| `sensitivityLevel` | INTEGER | 0=silent .. 3=urgent |

### 9.3 `calendar_event_cache`

| Column | Type | Description |
|---|---|---|
| `eventId` | TEXT (PK) | Google Calendar event ID |
| `title` | TEXT | Event title |
| `startTime` | INTEGER | Epoch millis |
| `endTime` | INTEGER | Epoch millis |
| `location` | TEXT? | Free-form location |
| `attendeesJson` | TEXT | JSON array of emails |
| `meetingLink` | TEXT? | Video call URL |
| `isVirtual` | INTEGER (bool) | Has meeting link? |
| `lastFetched` | INTEGER | Cache timestamp |

### 9.4 `routine_memory`

| Column | Type | Description |
|---|---|---|
| `id` | INTEGER (PK, auto) | Surrogate primary key |
| `day_of_week` | INTEGER | 1=Mon .. 7=Sun |
| `time_slot` | TEXT | "HH:MM" (30-min blocks) |
| `expectedActivity` | TEXT | Predicted activity label |
| `confidence` | REAL | 0.0 - 1.0 |
| `observationCount` | INTEGER | Total observations |
| `lastObservedMs` | INTEGER | Last observation time |

### 9.5 Other Tables

| Table | Entity | Key Columns |
|---|---|---|
| `preference_memory` | `PreferenceMemoryEntity` | skill_id + context_hash (UQ) |
| `location_memory` | `LocationMemoryEntity` | latLngHash (PK) |
| `confirmed_routine` | `ConfirmedRoutineEntity` | day_of_week + time_slot (UQ) |
| `wearable_context` | `WearableContextEntity` | id (PK), timestamp, activityType |
| `buds_context` | `BudsContextEntity` | id (PK), timestamp, budsInEar |

---

## 10. Memory System

> **ContextOS learns, it does not configure.** Three memory managers build a
> model of the user over time, feeding into OpenClaw prompts for personalized
> reasoning.

### 10.1 Routine Memory (`RoutineMemoryManager`)

Learns weekly activity patterns per 30-minute time slot.

- **Confidence ≥ 0.5**: Ready for prediction
- **Confidence ≥ 0.7**: Considered a learned routine
- **Storage**: `routine_memory` table

```kotlin
// RoutineMemoryManager.kt
suspend fun getPredictedActivity(dayOfWeek: Int, timeSlot: String): Pair<String, Float>?
suspend fun getLearnedRoutines(): List<RoutineMemoryEntity>
```

### 10.2 Preference Memory (`PreferenceMemoryManager`)

Learns per-skill, per-context user approval patterns.

- **3+ consistent approvals**: Triggers auto-approve
- **Storage**: `preference_memory` table (skill_id + context_hash unique)

### 10.3 Location Memory (`LocationMemoryManager`)

Learns frequently visited places with semantic labels.

- **5+ visits**: Assigns label ("Home", "Office", "Gym")
- **Storage**: `location_memory` table (geohash clustering)

```kotlin
// LocationMemoryManager.kt
suspend fun getLabelForLocation(latitude: Double, longitude: Double): String
suspend fun getLabelledLocations(): List<LocationMemoryEntity>
```

### 10.4 Memory Summary Builder

Aggregates all three managers into a compact text block for OpenClaw:

```kotlin
// MemorySummaryBuilder.kt
suspend fun build(
    dayOfWeek: Int,
    timeSlot: String,
    latitude: Double?,
    longitude: Double?,
): String {
    val parts = mutableListOf<String>()

    // Routine prediction
    routineMemory.getPredictedActivity(dayOfWeek, timeSlot)?.let {
        parts.add("User typically: ${it.first} at this time (${it.second}% confidence).")
    }

    // Learned routines
    val topRoutines = routineMemory.getLearnedRoutines().take(3)
    parts.add("Learned routines: $topRoutines.")

    // Location label
    if (latitude != null && longitude != null) {
        val label = locationMemory.getLabelForLocation(latitude, longitude)
        if (label != "Unknown") parts.add("Current location: $label.")
    }

    return parts.joinToString(" ")
}
```

This string is injected into `SituationModel.memorySummary` → OpenClaw prompt.

### 10.5 Personal Routine Service

Named routine types with user-facing acceleration ("Teach me faster"):

| Routine Type | Example Trigger |
|---|---|
| `CALL_HOME` | Daily commute departure |
| `FOCUS_BLOCK` | Deep work hours |
| `COMMUTE_DEPARTURE` | Leave for work |
| `EVENING_WRAP` | End-of-day routine |
| `GENERIC` | Catch-all |

---

## 11. Privacy Architecture

> **ContextOS is privacy-first by design.** Sensitive analysis stays on-device.
> Cloud inference requires explicit user consent.

### 11.1 Inference Router

```kotlin
// InferenceRouter.kt
suspend fun route(requestType: InferenceRequestType): InferenceTarget {
    return when (requestType) {
        SITUATION_ANALYSIS  → ON_DEVICE         // Always on-device
        ROUTINE_DETECTION   → ON_DEVICE         // Always on-device
        MESSAGE_DRAFTING    → if (userConsented) CLOUD
                             else ON_DEVICE_FALLBACK
    }
}
```

| Request Type | Default Route | Can be Cloud? |
|---|---|---|
| `SITUATION_ANALYSIS` | On-device | No |
| `ROUTINE_DETECTION` | On-device | No |
| `MESSAGE_DRAFTING` | On-device fallback | Yes (opt-in) |

### 11.2 Data Masking Layer

Before any data leaves the device, PII is replaced with placeholders:

```kotlin
// DataMaskingLayer.kt
fun maskText(input: String): String {
    var masked = input
    masked = EMAIL_PATTERN.replace(masked, "[EMAIL]")     // user@example.com → [EMAIL]
    masked = PHONE_PATTERN.replace(masked, "[PHONE]")     // +1-555-0123 → [PHONE]
    masked = ADDRESS_PATTERN.replace(masked, "[LOCATION]") // 123 Main St → [LOCATION]
    return masked
}

fun maskMeetingTitle(title: String): String = when {
    title.contains("review") → "review_meeting"
    title.contains("standup") → "standup_meeting"
    title.contains("1:1") → "one_on_one"
    else → "general_meeting"
}

fun maskCoordinates(lat: Double, lng: Double): Pair<Double, Double> {
    // Round to ~1km precision (2 decimal places)
    return Pair(Math.round(lat * 100) / 100.0, Math.round(lng * 100) / 100.0)
}
```

### 11.3 Data Retention

| Data Type | Retention | Cleanup |
|---|---|---|
| Action logs | 90 days | `DataRetentionWorker` (weekly) |
| Location history | 60 days (low-visit) | `DataRetentionWorker` (weekly) |
| Preferences | Indefinite | Manual wipe via UI |
| Routine memory | Indefinite (decays) | Confidence decay over time |

### 11.4 User Consent Flow

```
User opens ContextOS
    → Onboarding screen
        → Google Sign-In (optional)
        → Cloud Inference Consent dialog
            → "Allow OpenClaw to use cloud AI for message drafting?"
                → Yes: cloudInferenceConsented = true
                → No: cloudInferenceConsented = false (default)
```

---

## 12. Samsung Ecosystem Integration

> ContextOS is built for the Samsung PRISM Hackathon and integrates with the
> Galaxy ecosystem.

### 12.1 Galaxy Watch Integration

```kotlin
// WearableDataReceiver.kt
// Data signals: activity type, heart rate, step count
// Used for: workout-aware DND, health-conscious scheduling
```

### 12.2 Galaxy Buds Integration

```kotlin
// BudsStateReceiver.kt
// Data signals: buds in ear, ANC active, ambient sound
// Used for: suppressing DND when buds are in, audio-aware context
```

### 12.3 SmartThings Integration

```kotlin
// SmartThingsClient.kt
// Data signals: presence status, home/away mode
// Used for: triggering "Away" routines, home automation
```

**Graceful degradation:** If no Samsung device is paired, each data source
returns `null` and the system continues without interruption.

---

## 13. API Reference

### 13.1 OpenClawAgent Interface

```kotlin
interface OpenClawAgent {
    suspend fun analyzeSituation(model: SituationModel): SituationAnalysis
    suspend fun draftMessage(context: DraftingContext): String
    suspend fun chat(history: List<ChatTurn>): String
}
```

### 13.2 SituationModel

```kotlin
data class SituationModel(
    val currentTime: Long,
    val currentLocation: LatLng?,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val nextCalendarEvent: CalendarEventSummary?,
    val upcomingCalendarEvents: List<CalendarEventSummary>,
    val recentAppUsage: List<AppUsageEntry>,
    val ambientAudioContext: AmbientAudioContext,
    val memorySummary: String,
    val locationLabel: String,
    val wifiSsid: String?,
    val isMobileDataConnected: Boolean,
    val analysis: SituationAnalysis?,
    val wearableSummary: String?,
    val budsAnomalyFlags: List<String>?,
    val smartThingsMode: String?,
)
```

### 13.3 SituationAnalysis

```kotlin
data class SituationAnalysis(
    val currentContextLabel: String,
    val urgencyLevel: Int,              // 0-3
    val recommendedSkills: List<ActionRecommendation>,
    val anomalyFlags: List<String>,
)

data class ActionRecommendation(
    val skillId: String,
    val confidence: Float,              // 0.0-1.0
    val reasoning: String,
)
```

### 13.4 Skill Interface

```kotlin
interface Skill {
    val id: String
    val name: String
    val description: String
    fun shouldTrigger(model: SituationModel): Boolean
    suspend fun execute(model: SituationModel): SkillResult
}
```

### 13.5 SkillResult

```kotlin
sealed class SkillResult {
    data class Success(description: String, outcome: ActionOutcome = SUCCESS)
    data class PendingConfirmation(
        description: String,
        confirmationMessage: String,
        pendingAction: suspend () -> SkillResult,
    )
    data class Failure(description: String, error: Throwable? = null, outcome: ActionOutcome = FAILURE)
    data class Skipped(reason: String)
}
```

### 13.6 GeminiApiClient

```kotlin
class GeminiApiClient @Inject constructor() {
    suspend fun generate(model: String, prompt: String): String
    suspend fun generateMultiTurn(
        model: String,
        contents: List<GeminiContent>,
        temperature: Float = 0.7f,
        maxOutputTokens: Int = 2048,
    ): String
}
```

### 13.7 GroqApiClient

```kotlin
class GroqApiClient @Inject constructor() {
    suspend fun generateMultiTurn(
        model: String,
        messages: List<GroqMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 2048,
    ): String
}
```

### 13.8 Configuration Enums

```kotlin
enum class AmbientAudioContext { SILENT, CONVERSATION, MUSIC, AMBIENT_NOISE, UNKNOWN }
enum class ActionOutcome { SUCCESS, FAILURE, PENDING_USER_CONFIRMATION, SKIPPED }
enum class UserOverride { APPROVED, DISMISSED, MODIFIED }
enum class InferenceRequestType { SITUATION_ANALYSIS, ROUTINE_DETECTION, MESSAGE_DRAFTING }
enum class InferenceTarget { ON_DEVICE, CLOUD, ON_DEVICE_FALLBACK }
```

---

## 14. Testing & Scenarios

### 14.1 Skill Unit Tests

Each skill has a corresponding test class in `:core:skills/src/test/`:

| Test Class | Coverage |
|---|---|
| `DndSetterSkillTest` | Trigger conditions, DND enable/disable, dismissal tracking |
| `BatteryWarnerSkillTest` | Battery thresholds, SMS sending, debounce logic |
| `NavigationLauncherSkillTest` | Location proximity, travel time, intent creation |
| `MessageDrafterSkillTest` | Drafting context, fallback templates |
| `DocumentFetcherSkillTest` | Document query, cache age, API fallback |
| `LocationIntelligenceSkillTest` | Notification mode switching, location matching |

### 14.2 End-to-End Test Scenarios

| ID | Scenario | Skills Triggered | Expected Outcome |
|---|---|---|---|
| TS-01 | Pre-meeting DND 8 min before | `dnd_setter` | DND enabled until meeting end |
| TS-02 | Low battery before 2h meeting | `battery_warner` | SMS sent to emergency contact |
| TS-03 | In-person meeting in 15 min | `navigation_launcher` | Maps navigation launched |
| TS-04 | Running late with traffic | `message_drafter` | Drafted "running late" message |
| TS-05 | Meeting with Drive documents | `document_fetcher` | Document link surfaced |
| TS-06 | Evening at gym location | `location_intelligence` | Silent mode engaged |
| TS-07 | Weekly standup routine | `personal_nudge` | Nudge notification shown |

### 14.3 Service Lifecycle Test

```kotlin
// ContextOSServiceLifecycleTest.kt
class ContextOSServiceLifecycleTest {
    @Test
    fun `service starts and runs agent cycle`() {
        // Verify foreground service starts
        // Verify sensor collection fires
        // Verify skill evaluation runs
        // Verify action log is written
    }

    @Test
    fun `service handles permission denial gracefully`() {
        // Verify no crash when location is denied
        // Verify no crash when calendar is denied
        // Verify graceful fallback for all sensors
    }
}
```

### 14.4 API Client Tests

```kotlin
// MapsDistanceMatrixClientTest.kt
class MapsDistanceMatrixClientTest {
    @Test
    fun `returns travel time for valid request`()
    @Test
    fun `handles API error gracefully`()
    @Test
    fun `caches results within expiry window`()
}
```

---

## 15. Glossary

| Term | Definition |
|---|---|
| **ContextOS** | The proactive Android phone orchestrator |
| **OpenClaw** | The AI/LLM orchestration layer — the intelligence behind ContextOS |
| **SituationModel** | The central data class representing the user's current context |
| **SituationAnalysis** | OpenClaw's structured interpretation of the situation |
| **Skill** | A stateless action module that decides when to act |
| **SkillResult** | The outcome of a skill execution (Success, Pending, Failure, Skipped) |
| **shouldTrigger()** | Pure, fast check that determines if a skill should execute |
| **SkillRegistry** | Hilt-injected collection of all registered skills |
| **ActionDispatcher** | Routes SkillResults to execution + logging |
| **ContextAgent** | Central orchestrator that runs the 15-minute agent cycle |
| **AgentCycleWorker** | WorkManager periodic worker (fallback scheduling) |
| **SituationModeler** | Delegates to OpenClaw for situation analysis |
| **MessageDraftingEngine** | Delegates to OpenClaw for LLM message drafting |
| **InferenceRouter** | Privacy gatekeeper — routes requests on-device or to cloud |
| **DataMaskingLayer** | PII replacement before off-device transmission |
| **MemorySummaryBuilder** | Aggregates learned patterns for OpenClaw prompts |
| **RoutineMemory** | Weekly activity pattern learning (30-min slots) |
| **PreferenceMemory** | Per-skill, per-context approval learning |
| **LocationMemory** | Frequently visited place label inference |
| **RawSensorData** | Unprocessed sensor collection output |
| **ChatTurn** | A single turn in a multi-turn OpenClaw conversation |
| **GoogleServicesContextProvider** | Live Calendar/Gmail/Drive context for chat |
| **SkillResult.PendingConfirmation** | Skill requires user approval before acting |
| **Auto-approve** | User preference to auto-execute a skill without confirmation |
| **Cycle Budget** | 12s computation + 3s dispatch per 15-minute cycle |
| **Graceful Degradation** | Every AI feature has a rule-based fallback |

---

## Appendix A: Data Flow Diagrams

### A.1 Full Agent Cycle

```
Boot/Alarm
    │
    ▼
ContextOSService (Foreground)
    │
    ├── while (running):
    │       │
    │       ▼
    │   ContextAgent.runCycle()
    │       │
    │       ├── withTimeout(12s):
    │       │       │
    │       │       ├── SensorDataCollector.collect()
    │       │       │       ├── BatteryManager → batteryLevel, isCharging
    │       │       │       ├── FusedLocationProvider → LatLng
    │       │       │       ├── AudioRecord → AmbientAudioContext
    │       │       │       ├── UsageStatsManager → app usage list
    │       │       │       ├── WifiManager → wifiSsid
    │       │       │       └── ConnectivityManager → isMobileData
    │       │       │
    │       │       ├── SituationModelBuilder.build(raw)
    │       │       │       ├── CalendarEventCacheDao → upcoming events
    │       │       │       ├── LocationMemoryManager → locationLabel
    │       │       │       ├── MemorySummaryBuilder → memorySummary
    │       │       │       ├── WearableDataReceiver → wearableSummary
    │       │       │       ├── BudsStateReceiver → budsInference
    │       │       │       │
    │       │       │       └── SituationModeler.analyze(model)
    │       │       │               └── ★ OpenClawAgent.analyzeSituation(model) ★
    │       │       │                       ├── OpenClawPromptBuilder.buildSituationPrompt(model)
    │       │       │                       ├── (Gemini or Groq) LLM call
    │       │       │                       └── Returns SituationAnalysis
    │       │       │
    │       │       ├── For each skill ∈ SkillRegistry.skills:
    │       │       │       └── skill.shouldTrigger(model) → true/false
    │       │       │
    │       │       ├── For each triggered skill:
    │       │       │       ├── skill.execute(model) → SkillResult
    │       │       │       └── ActionDispatcher.dispatch(skill, result, model)
    │       │       │               ├── → ActionLogEntity written to Room
    │       │       │               ├── → Notification (if PendingConfirmation)
    │       │       │               └── → Auto-execute (if autoApprove)
    │       │       │
    │       │       └── healthMonitor.recordCycleComplete()
    │       │
    │       └── delay(15 * 60 * 1000)  ← 15-minute interval
    │
    └── onDestroy → cleanup
```

### A.2 OpenClaw Prompt Flow

```
SituationModel
    │
    ▼
OpenClawPromptBuilder.buildSituationPrompt()
    │
    ├── "You are ContextOS, a proactive phone assistant."
    ├── "Analyze the user's current situation and return JSON."
    ├── ## Current State
    │       ├── Time: Mon 14:30
    │       ├── Location: Office
    │       ├── Battery: 73% (charging: false)
    │       ├── WiFi: "Office-Guest"
    │       └── Audio: SILENT
    ├── ## Calendar (next 8 hours)
    │       └── "Q3 Budget Review" in 12 min | 4 attendees
    ├── ## Recent App Usage
    │       └── Gmail (23 min), Chrome (12 min), Calendar (5 min)
    ├── ## Learned Patterns
    │       └── "User typically: meeting at this time (85% confidence)."
    ├── ## Available Skills
    │       ├── dnd_setter: enables DND before meetings
    │       ├── battery_warner: sends SMS when battery low
    │       └── navigation_launcher: launches Maps navigation
    └── ## Instructions
            └── Return ONLY valid JSON → {...}
    │
    ▼
RealOpenClawAgent.generateSingleTurn(model, prompt, temperature=0.3)
    │
    ├── BuildConfig.OPENCLAW_API_PROVIDER == "groq"?
    │       └── GroqApiClient.generateMultiTurn(...)
    │
    └── else?
            └── GeminiApiClient.generateMultiTurn(...)
    │
    ▼
Raw LLM response → parseSituationAnalysis(raw)
    │
    ├── extractJsonBlock(raw)  ← handles ```json fences, bare JSON
    ├── KotlinSerialization JsonObject parsing
    └── Returns SituationAnalysis
```

### A.3 Message Drafting Flow

```
MessageDrafterSkill.execute(model)
    │
    ├── Check: event in ≤15 min with location?
    ├── MapsDistanceMatrixClient → travel time
    ├── travelTime > minutesUntilStart?
    │       └── No → SkillResult.Skipped("Likely to arrive on time")
    │
    ├── Build DraftingContext
    │       ├── recipientName = event.attendees.first()
    │       ├── relationship = "colleague"
    │       ├── reason = "Running late"
    │       └── estimatedTimeOfArrival = travelTimeMinutes
    │
    ├── MessageDraftingEngine.draft(context)
    │       └── OpenClawAgent.draftMessage(context)
    │               ├── OpenClawPromptBuilder.buildDraftingPrompt(context)
    │               ├── LLM call (model, prompt, temperature=0.7)
    │               └── Returns: "Hey Alex, stuck in traffic — should be there by 15. Sorry!"
    │
    └── Check autoApprove?
            ├── Yes → trySendSms(draft, recipientName) → SkillResult.Success
            └── No  → SkillResult.PendingConfirmation(confirmationMessage = draft)
```

---

## Appendix B: Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all unit tests
./gradlew test

# Run all instrumented tests
./gradlew connectedAndroidTest

# Run specific module tests
./gradlew :core:skills:test
./gradlew :core:network:test
./gradlew :core:service:test

# Lint check
./gradlew lint

# Clean build
./gradlew clean && ./gradlew assembleDebug
```

---

## Appendix C: Version Catalog (libs.versions.toml)

Key dependencies:

| Library | Version | Module |
|---|---|---|
| Kotlin | 2.1.0 | All |
| Compose BOM | 2024.12.01 | `:app` |
| Hilt | 2.53.1 | `:app`, `:core:*` |
| Room | 2.6.1 | `:core:data` |
| WorkManager | 2.10.0 | `:core:service` |
| Kotlinx Serialization | 1.7.3 | `:core:$` |
| DataStore | 1.1.1 | `:core:data` |
| Google API Client | 2.6.0 | `:core:network` |
| OkHttp | 4.12.0 | `:core:network` |
| Retrofit | 2.11.0 | `:core:network` |
| MockK | 1.13.13 | Test modules |
| Robolectric | 4.14.1 | Test modules |
| MockWebServer | 4.12.0 | Test modules |

---

## Appendix D: OpenClaw Configuration Cheat Sheet

```properties
# ── AI Provider ──────────────────────────────────────────────────────
# Gemini (default):
OPENCLAW_API_KEY=AIzaSy...
# Groq (prefix with gsk_):
OPENCLAW_API_KEY=gsk_...

# ── Models ───────────────────────────────────────────────────────────
OPENCLAW_REASONING_MODEL=gemini-2.0-flash        # Analysis & confidence
OPENCLAW_DRAFTING_MODEL=gemini-2.0-flash-lite    # Message drafting
OPENCLAW_CHAT_MODEL=gemini-2.0-flash             # Multi-turn chat

# ── Feature Flags ────────────────────────────────────────────────────
OPENCLAW_USE_MOCK=false            # true = rule-based, no API calls
OPENCLAW_ENABLE_REASONING=true      # false = rule-based analysis only
OPENCLAW_ENABLE_DRAFTING=true       # false = template-based drafts only
```

---

> *ContextOS — Your phone, but smarter. Built with ❤️ for the Samsung PRISM Hackathon.*
