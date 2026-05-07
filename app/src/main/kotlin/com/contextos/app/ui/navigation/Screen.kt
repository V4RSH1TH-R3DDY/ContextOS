package com.contextos.app.ui.navigation

sealed class Screen(val route: String) {

    sealed class Onboarding(route: String) : Screen(route) {
        object Welcome          : Onboarding("onboarding/welcome")
        object Permissions      : Onboarding("onboarding/permissions")
        object GoogleSignIn     : Onboarding("onboarding/google_sign_in")
        object EmergencyContact : Onboarding("onboarding/emergency_contact")
    }

    object Dashboard  : Screen("dashboard")
    object Settings   : Screen("settings")
    object ActionLog  : Screen("action_log")

    object ActionDetail : Screen("detail/{logId}") {
        fun createRoute(logId: Long) = "detail/$logId"
        const val ARG_LOG_ID = "logId"
    }
}
