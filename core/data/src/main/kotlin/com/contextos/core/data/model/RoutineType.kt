package com.contextos.core.data.model

/**
 * Named routine types that the personalisation engine can detect and surface.
 *
 * Phase 10.1 — Personal Routine Detector
 */
enum class RoutineType(val displayName: String, val description: String) {
    /** User initiates a phone call to a saved contact at a consistent time slot. */
    CALL_HOME("Call Home", "Regular phone calls to a specific contact"),

    /** DND/notification silencing correlates with a specific recurring calendar event. */
    FOCUS_BLOCK("Focus Block", "Recurring deep work or standup sessions"),

    /** GPS movement away from "Office" starts at a consistent time. */
    COMMUTE_DEPARTURE("Commute Departure", "Consistent departure time from office"),

    /** Screen-off patterns signal end of working day. */
    EVENING_WRAP("Evening Wrap", "End-of-day wind-down pattern"),

    /** Generic learned routine that doesn't fit into named categories. */
    GENERIC("Routine", "Learned behavioral pattern"),
}

/**
 * Nudge message templates for each routine type.
 *
 * Phase 10.2 — Proactive Personal Nudges
 */
object NudgeMessages {
    fun getMessageForRoutine(type: RoutineType, context: String = ""): String {
        return when (type) {
            RoutineType.CALL_HOME ->
                "You usually check in with home around now — want me to draft something?"
            RoutineType.FOCUS_BLOCK ->
                "Mondays at 9:30 are usually your standup. Enabling focus mode."
            RoutineType.COMMUTE_DEPARTURE ->
                "Looks like you're heading out. Want me to draft your ETA?"
            RoutineType.EVENING_WRAP ->
                "Looks like you're wrapping up. DND is off — want to review any pending actions?"
            RoutineType.GENERIC ->
                if (context.isNotEmpty()) context else "Time for your regular routine."
        }
    }
}
