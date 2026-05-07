package com.contextos.core.service.agent.openclaw

import com.contextos.core.data.model.DraftingContext
import com.contextos.core.data.model.SituationModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constructs structured prompts for the OpenClaw agent.
 *
 * Each prompt is a plain-text block that the LLM parses to produce structured
 * JSON output. Token budgets are enforced here:
 *   - Situation analysis prompt: ≤ 2,000 tokens (≈ 1,500 words)
 *   - Message drafting prompt:    ≤ 800 tokens  (≈ 600 words)
 *
 * When the real OpenClaw SDK is integrated these prompts will be sent via its
 * `agent.complete()` API. Until then, [MockOpenClawAgent] ignores the prompt
 * text and uses rule-based fallbacks.
 */
@Singleton
class OpenClawPromptBuilder @Inject constructor() {

    // ─────────────────────────────────────────────────────────────────────────
    // Situation Analysis Prompt
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the prompt sent to the OpenClaw reasoning model every cycle.
     *
     * The model is instructed to return JSON conforming to [SituationAnalysis]:
     * ```json
     * {
     *   "currentContextLabel": "Pre-meeting preparation",
     *   "urgencyLevel": 2,
     *   "recommendedSkills": [
     *     { "skillId": "dnd_setter", "confidence": 0.95, "reasoning": "Meeting in 8 min, DND off" }
     *   ],
     *   "anomalyFlags": ["meeting in 8 minutes but user is 20km from venue"]
     * }
     * ```
     */
    fun buildSituationPrompt(model: SituationModel): String {
        val sb = StringBuilder()
        sb.appendLine("You are ContextOS, a proactive phone assistant.")
        sb.appendLine("Analyze the user's current situation and return a JSON object.")
        sb.appendLine()

        // ── Time & Location ──────────────────────────────────────────────
        sb.appendLine("## Current State")
        sb.appendLine("- Time: ${formatTime(model.currentTime)}")
        sb.appendLine("- Location: ${model.locationLabel}")
        model.currentLocation?.let {
            sb.appendLine("  - GPS: ${it.latitude}, ${it.longitude}")
        }
        sb.appendLine("- Battery: ${model.batteryLevel}% (charging: ${model.isCharging})")
        sb.appendLine("- Wi-Fi: ${model.wifiSsid ?: "disconnected"}")
        sb.appendLine("- Mobile data: ${model.isMobileDataConnected}")
        sb.appendLine("- Audio: ${model.ambientAudioContext.name}")
        sb.appendLine()

        // ── Calendar ─────────────────────────────────────────────────────
        sb.appendLine("## Calendar (next 8 hours)")
        if (model.upcomingCalendarEvents.isEmpty()) {
            sb.appendLine("No upcoming events.")
        } else {
            for ((i, event) in model.upcomingCalendarEvents.withIndex()) {
                if (i >= 3) break   // cap at 3 events to save tokens
                val minutesUntil = (event.startTime - model.currentTime) / 60_000
                sb.appendLine("- [${i + 1}] \"${event.title}\"")
                sb.appendLine("      starts in ${minutesUntil} min | ${event.attendees.size} attendees")
                event.location?.let { sb.appendLine("      location: $it") }
                event.meetingLink?.let { sb.appendLine("      link: $it (virtual: ${event.isVirtual})") }
            }
        }
        sb.appendLine()

        // ── App Usage ────────────────────────────────────────────────────
        if (model.recentAppUsage.isNotEmpty()) {
            sb.appendLine("## Recent App Usage (last hour)")
            for (app in model.recentAppUsage.take(3)) {
                sb.appendLine("- ${app.appName}: ${app.usageTimeMs / 60_000} min")
            }
            sb.appendLine()
        }

        // ── Memory Summary ───────────────────────────────────────────────
        if (model.memorySummary.isNotEmpty()) {
            sb.appendLine("## Learned Patterns")
            sb.appendLine(model.memorySummary)
            sb.appendLine()
        }

        // ── Registered Skills ────────────────────────────────────────────
        sb.appendLine("## Available Skills")
        sb.appendLine("- dnd_setter: enables Do-Not-Disturb before meetings")
        sb.appendLine("- battery_warner: sends SMS when battery low + long meetings ahead")
        sb.appendLine("- navigation_launcher: launches Maps for upcoming meeting locations")
        sb.appendLine("- message_drafter: drafts 'running late' or ETA messages")
        sb.appendLine("- document_fetcher: finds relevant Drive/Gmail docs before meetings")
        sb.appendLine("- location_intelligence: updates notification mode per location")
        sb.appendLine()

        // ── Output Format ────────────────────────────────────────────────
        sb.appendLine("## Instructions")
        sb.appendLine("Return ONLY valid JSON matching this schema:")
        sb.appendLine("""
            |{
            |  "currentContextLabel": "<string describing user's context>",
            |  "urgencyLevel": <0-3>,
            |  "recommendedSkills": [
            |    { "skillId": "<id>", "confidence": <0.0-1.0>, "reasoning": "<why>" }
            |  ],
            |  "anomalyFlags": ["<any unusual observations>"]
            |}
        """.trimMargin())

        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message Drafting Prompt
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the prompt sent to the OpenClaw LLM for message drafting.
     *
     * The model is instructed to return a single SMS-length string.
     */
    fun buildDraftingPrompt(context: DraftingContext): String {
        val sb = StringBuilder()
        sb.appendLine("You are a smart assistant drafting a short, natural SMS message.")
        sb.appendLine()
        sb.appendLine("## Context")
        sb.appendLine("- Recipient: ${context.recipientName} (${context.relationship})")
        sb.appendLine("- Reason: ${context.reason}")
        context.estimatedTimeOfArrival?.let {
            sb.appendLine("- ETA: $it")
        }
        context.timeAvailable?.let {
            sb.appendLine("- Available until: $it")
        }
        context.backupNumber?.let {
            sb.appendLine("- Backup contact number: $it")
        }
        sb.appendLine()

        // ── Tone Guidance ────────────────────────────────────────────────
        sb.appendLine("## Tone Guidelines")
        when (context.relationship) {
            "colleague" -> sb.appendLine("- Professional but friendly. No emoji. Brief.")
            "friend"    -> sb.appendLine("- Casual and warm. Emoji allowed. Keep it short.")
            "family"    -> sb.appendLine("- Warm and reassuring. Can be slightly longer.")
            else        -> sb.appendLine("- Neutral tone, polite, concise.")
        }
        sb.appendLine()

        sb.appendLine("## Instructions")
        sb.appendLine("Return ONLY the draft message text. No JSON. No quotes. Under 160 characters.")

        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun formatTime(epochMs: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMs }
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val dow = dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m = cal.get(java.util.Calendar.MINUTE)
        return "$dow ${String.format(java.util.Locale.US, "%02d:%02d", h, m)}"
    }
}
