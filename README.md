# ContextOS — Proactive Phone Orchestrator

[![Platform](https://img.shields.io/badge/Android-12+-brightgreen)](https://developer.android.com/about/versions/12)
[![Language](https://img.shields.io/badge/Kotlin-1.9-blue)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

ContextOS is an intelligent background agent for Android that learns your patterns, monitors your calendar and surroundings, and autonomously takes helpful actions — like silencing your phone before meetings, drafting "running late" messages, or launching navigation — all without you opening the app.

---
🚀 **OpenClaw Project Submission Resources**

📽️ **Demo Video**
https://youtu.be/eOWGswfy5_E

📊 **Presentation (PPT)**
https://drive.google.com/file/d/1atIuPmaSxjf7Hp4q1WHjK2hnNPAJlfOY/view?usp=drive_link

📄 **AI Usage Disclosure Form**
https://docs.google.com/document/d/1bBJrJLVrDFb_3i_MZQCaq8K0KopVKGTx/edit?usp=sharing&ouid=100049560675453548895&rtpof=true&sd=true

✨ Built with ContextOS-powered autonomous intelligence workflows.


## Problem

Modern smartphones are powerful but **reactive**. They wait for you to:
- Manually turn on Do Not Disturb before a meeting
- Open Maps and type in an address
- Compose an "I'm running late" text from scratch
- Remember to charge your phone before a long meeting

Each of these is a tiny cognitive load. Over a day they add up. Over a week they waste hours. Existing solutions (Bixby Routines, Tasker) require manual setup and scripting — they never *learn* what you need.

---

## Solution

ContextOS runs as an **always-on background agent** that:

- **Observes** — Collects sensor data (battery, location, time, calendar) every 15 minutes
- **Understands** — Builds a situation model of your current context (meeting? commuting? free?)
- **Decides** — Evaluates 4 skills to determine if action is needed
- **Acts** — Either executes automatically (based on your preferences) or sends a system notification asking for approval
- **Learns** — Records your approvals/dismissals and adapts future behavior

All processing happens **on-device**. No data leaves your phone.

---

## Skills

| Skill | What It Does | Trigger |
|-------|-------------|---------|
| **Battery Warner** | Notifies about low battery before long meetings | Battery < 20%, meeting > 90 min |
| **Message Drafter** | Drafts "running late" messages to meeting organizers | Travel time > time until meeting |
| **Navigation Launcher** | Offers to open Google Maps to your meeting | Meeting in < 30 min, location set |
| **DND Setter** | Auto-silences phone during meetings | Meeting with 2+ attendees starting |

---

## Architecture

```
┌─────────────────────────────────────────────┐
│              Foreground Service              │
│         (15-min agent loop, START_STICKY)    │
├─────────────────────────────────────────────┤
│  ┌─────────┐  ┌──────────┐  ┌────────────┐  │
│  │ Sensor  │→ │ Situation│→ │   Skill    │  │
│  │Collector│  │  Modeler │  │  Registry  │  │
│  └─────────┘  └──────────┘  └─────┬──────┘  │
│                                    │          │
│  ┌─────────────────────────────────▼────────┐ │
│  │           ActionDispatcher                │ │
│  │  ┌──────────┐  ┌──────────────────────┐  │ │
│  │  │ Action   │  │  NotificationManager │  │ │
│  │  │ Log (DB) │  │  (System Notifs)     │  │ │
│  │  └──────────┘  └──────────────────────┘  │ │
│  └───────────────────────────────────────────┘ │
├─────────────────────────────────────────────┤
│  Fallback Layers:                            │
│  • AlarmManager (exact wakeup every 15 min)  │
│  • WorkManager (persistent periodic task)    │
│  • BootReceiver (restarts on device reboot)  │
└─────────────────────────────────────────────┘
```

### Multi-Module Structure

| Module | Purpose |
|--------|---------|
| `:app` | Jetpack Compose UI (onboarding, dashboard, settings) |
| `:core:service` | Foreground service, agent loop, notification manager |
| `:core:skills` | Individual skill implementations |
| `:core:data` | Room DB, DAOs, repositories, preferences |
| `:core:memory` | Routine/preference/location memory layers |
| `:core:network` | Google API clients (Calendar, Gmail, Drive), OAuth |

---

## Setup

### Prerequisites
- Android Studio Ladybug or later
- JDK 17+
- Android SDK 34+
- A physical device running Android 12+ (emulator lacks sensors)

### Clone & Build
```bash
git clone https://github.com/V4RSH1TH-R3DDY/ContextOS.git
cd ContextOS
./gradlew assembleDebug
```

### Google API Setup (Optional — for calendar/Gmail/Drive sync)
1. Create a project at [Google Cloud Console](https://console.cloud.google.com)
2. Enable Calendar, Gmail, and Drive APIs
3. Configure OAuth consent screen with scopes:
   - `https://www.googleapis.com/auth/calendar`
   - `https://www.googleapis.com/auth/gmail.modify`
   - `https://www.googleapis.com/auth/drive`
4. Generate OAuth 2.0 client credentials for Android
5. Add your SHA-1 certificate fingerprint

> **Without Google APIs**: The app runs fully with skills that don't need calendar data (DND, battery warner). Calendar-dependent skills (navigation, message drafter) remain dormant.

---

## Instructions

### First Run
1. **Install** the APK on your device
2. **Launch** the app — the onboarding wizard walks you through:
   - **Permissions**: Location, Calendar, Notifications (all skippable)
   - **Google Sign-In** (skippable — connects Calendar/Gmail/Drive)
   - **Emergency Contact** (skippable — used by battery warner)
3. **Dashboard** appears — the foreground service starts automatically
4. You'll see a persistent notification: *"ContextOS Agent is running…"*

### Usage
Once running, ContextOS is **fully autonomous**. You never need to open the app:

- **System notifications** appear when the agent takes an action or needs approval
- **Approve/Dismiss** actions from the notification tray
- **Check history** by opening the app → Action Log
- **Adjust behavior** in Settings → per-skill toggles and auto-approve preferences

### Stopping the Agent
- Open the app → tap the power icon in the dashboard header
- Or force-stop from Android Settings → Apps → ContextOS

---

## Building the APK

```bash
# Debug build (no signing required)
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk

# Release build (requires signing config)
./gradlew assembleRelease
# APK at: app/build/outputs/apk/release/app-release.apk
```

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Database | Room (SQLite) |
| Background | Foreground Service, WorkManager, AlarmManager |
| AI/LLM | OpenClaw / Groq API (optional, for message drafting) |
| Google APIs | Calendar v3, Gmail v1, Drive v3 |
| Build | Gradle Kotlin DSL, Version Catalog |
| Min SDK | 31 (Android 12) |
| Target SDK | 34 |

---

## Team

| Role | Responsibility |
|------|---------------|
| Android Development Lead | Core service, Compose UI, skills |
| AI/ML Engineer | OpenClaw agent, situation modeling, memory |
| Backend / API Engineer | Google APIs, OAuth, data sync |
| QA & Product Manager | Testing, UX, documentation |

---

## License

MIT License — see [LICENSE](LICENSE).
