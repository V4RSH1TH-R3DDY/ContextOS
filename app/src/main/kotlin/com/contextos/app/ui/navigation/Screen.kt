package com.contextos.app.ui.navigation

/**
 * Navigation destinations for the ContextOS app.
 * Sealed hierarchy mirrors the UX screen inventory from Phase 0.4.
 */
sealed class Screen(val route: String) {

    // ─── Onboarding flow ─────────────────────────────────────────────────────
    sealed class Onboarding(route: String) : Screen(route) {
        object Welcome         : Onboarding("onboarding/welcome")
        object Permissions     : Onboarding("onboarding/permissions")
        object GoogleSignIn    : Onboarding("onboarding/google_sign_in")
        object EmergencyContact: Onboarding("onboarding/emergency_contact")
    }

    // ─── Main app ─────────────────────────────────────────────────────────────
    object Dashboard : Screen("dashboard")
    object Settings  : Screen("settings")

    // ─── Detail ───────────────────────────────────────────────────────────────
    object ActionDetail : Screen("detail/{logId}") {
        fun createRoute(logId: Long) = "detail/$logId"
        const val ARG_LOG_ID = "logId"
    }
}
