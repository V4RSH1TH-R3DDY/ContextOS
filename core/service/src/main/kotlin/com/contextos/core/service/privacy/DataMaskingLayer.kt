package com.contextos.core.service.privacy

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps every off-device (cloud) payload, replacing personally identifiable
 * information with safe placeholders before data leaves the device.
 *
 * Replacements:
 *   - Contact names → [CONTACT]
 *   - Specific addresses → [LOCATION]
 *   - Meeting titles → semantic category only (e.g. "client_review" not "Acme Q3 Budget Review")
 *   - Phone numbers → [PHONE]
 *   - Email addresses → [EMAIL]
 *
 * Phase 12.1 — On-Device Privacy Architecture
 */
@Singleton
class DataMaskingLayer @Inject constructor() {

    /**
     * Masks a free-form text string, replacing PII patterns with safe placeholders.
     */
    fun maskText(input: String): String {
        var masked = input

        // Mask email addresses
        masked = EMAIL_PATTERN.replace(masked, "[EMAIL]")

        // Mask phone numbers (various formats)
        masked = PHONE_PATTERN.replace(masked, "[PHONE]")

        // Mask street addresses (number + street name patterns)
        masked = ADDRESS_PATTERN.replace(masked, "[LOCATION]")

        // Mask proper names that appear after common salutations
        masked = SALUTATION_NAME_PATTERN.replace(masked) { match ->
            "${match.groupValues[1]}[CONTACT]"
        }

        Log.d(TAG, "Masked text (${input.length} → ${masked.length} chars)")
        return masked
    }

    /**
     * Masks contact names in a text string given a list of known contact names.
     */
    fun maskContactNames(input: String, contactNames: List<String>): String {
        var masked = input
        contactNames.forEach { name ->
            if (name.isNotBlank() && name.length > 1) {
                masked = masked.replace(name, "[CONTACT]", ignoreCase = true)
            }
        }
        return masked
    }

    /**
     * Masks a meeting title by extracting its semantic category.
     */
    fun maskMeetingTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("standup") || lower.contains("stand-up") -> "standup_meeting"
            lower.contains("review") -> "review_meeting"
            lower.contains("1:1") || lower.contains("1-1") || lower.contains("one on one") -> "one_on_one"
            lower.contains("sprint") -> "sprint_ceremony"
            lower.contains("retro") -> "retrospective"
            lower.contains("planning") -> "planning_session"
            lower.contains("sync") -> "sync_meeting"
            lower.contains("lunch") || lower.contains("dinner") || lower.contains("breakfast") -> "meal_event"
            lower.contains("interview") -> "interview"
            lower.contains("training") || lower.contains("workshop") -> "training_session"
            lower.contains("presentation") || lower.contains("demo") -> "presentation"
            lower.contains("all hands") || lower.contains("all-hands") -> "all_hands"
            lower.contains("client") || lower.contains("customer") -> "client_meeting"
            lower.contains("team") -> "team_meeting"
            else -> "general_meeting"
        }
    }

    /**
     * Masks location coordinates to a coarser granularity (city-level precision).
     * Rounds to ~1km precision by truncating to 2 decimal places.
     */
    fun maskCoordinates(lat: Double, lng: Double): Pair<Double, Double> {
        val maskedLat = Math.round(lat * 100.0) / 100.0
        val maskedLng = Math.round(lng * 100.0) / 100.0
        return Pair(maskedLat, maskedLng)
    }

    /**
     * Masks a complete payload map for off-device transmission.
     * Removes sensitive fields and masks remaining PII.
     */
    fun maskPayload(payload: Map<String, String>): Map<String, String> {
        val sensitiveKeys = setOf(
            "contact_name", "phone_number", "email", "address",
            "home_address", "work_address", "full_name",
        )
        return payload.mapValues { (key, value) ->
            when {
                key in sensitiveKeys -> "[REDACTED]"
                else -> maskText(value)
            }
        }
    }

    companion object {
        private const val TAG = "DataMaskingLayer"

        private val EMAIL_PATTERN = Regex(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
        )

        private val PHONE_PATTERN = Regex(
            "(?:\\+\\d{1,3}[\\s-]?)?(?:\\(\\d{1,4}\\)[\\s-]?)?\\d{3,4}[\\s.-]?\\d{3,4}[\\s.-]?\\d{0,4}"
        )

        private val ADDRESS_PATTERN = Regex(
            "\\d{1,5}\\s+[A-Z][a-zA-Z]+\\s+(?:St|Street|Ave|Avenue|Blvd|Boulevard|Dr|Drive|Rd|Road|Ln|Lane|Way|Ct|Court|Pl|Place)\\b",
            RegexOption.IGNORE_CASE,
        )

        private val SALUTATION_NAME_PATTERN = Regex(
            "(Hey |Hi |Dear |Hello |Mr\\.? |Mrs\\.? |Ms\\.? |Dr\\.? )([A-Z][a-z]+(?:\\s[A-Z][a-z]+)?)"
        )
    }
}
