# AI Disclosure — ContextOS

This document describes how artificial intelligence tools and models were used in the development and operation of ContextOS.

---

## 1. AI-Assisted Development

The following AI tools were used during the software engineering process:

### Code Generation & Editing
- **Claude (Anthropic)** — Used via the OpenCode CLI agent to:
  - Generate boilerplate code for Android components (services, ViewModels, Compose screens)
  - Implement skill logic (trigger conditions, execution flows)
  - Write Room database entities, DAOs, and migrations
  - Generate UI code for onboarding, dashboard, and settings screens
  - Refactor and debug existing code across all modules
  - Create project documentation (README, implementation plan)
- **GitHub Copilot** — Inline code completion during manual development sessions

### Debugging & Problem Solving
- **Claude** — Used to:
  - Diagnose build failures and resolve dependency conflicts
  - Identify logical errors in skill evaluation (`shouldTrigger` / `execute` chains)
  - Fix OAuth consent screen issues and permission handling
  - Trace notification delivery and action dispatch flows

---

## 2. On-Device AI Models

ContextOS integrates with **OpenClaw** (via **Groq API**) for LLM-powered features:

| Feature | AI Model | Where It Runs | What It Does |
|---------|----------|---------------|-------------|
| Situation Analysis | OpenClaw (Groq-hosted LLM) | Cloud API call from device | Interprets sensor data and recommends actions |
| Message Drafting | OpenClaw (Groq-hosted LLM) | Cloud API call from device | Generates natural-language "running late" and battery warning messages |

### Data Privacy
- LLM prompts are constructed from **on-device sensor data** (battery level, calendar events, location)
- No personally identifiable information is sent beyond what is necessary for the prompt
- The user can **disable all AI features** in Settings, falling back to rule-only behavior
- API keys are stored in `local.properties` and excluded from version control

---

## 3. Scope & Limitations

- **All non-LLM logic** (skill triggers, notification dispatch, database operations, UI rendering) is hand-written Kotlin code, not AI-generated
- **AI-generated code was reviewed** by the team before committing
- The OpenClaw / Groq integration is **optional** — the app functions in rule-only mode without it
- No user data is used to train or fine-tune any AI model

---

## 4. Ethical Considerations

- **User consent**: All permissions are requested during onboarding with clear explanations
- **Transparency**: The action log shows every decision the agent makes, including "Why?" reasoning
- **Control**: Users can disable any skill, toggle auto-approve, or stop the agent entirely
- **On-device priority**: Where possible, processing is done locally (Room DB, rule evaluation) to minimize data exposure

---

*Last updated: May 2026*
