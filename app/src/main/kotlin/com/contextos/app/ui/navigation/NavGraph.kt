package com.contextos.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.contextos.app.ui.dashboard.DashboardScreen
import com.contextos.app.ui.detail.ActionDetailScreen
import com.contextos.app.ui.onboarding.EmergencyContactScreen
import com.contextos.app.ui.onboarding.GoogleSignInScreen
import com.contextos.app.ui.onboarding.PermissionsScreen
import com.contextos.app.ui.onboarding.WelcomeScreen
import com.contextos.app.ui.settings.SettingsScreen

@Composable
fun ContextOSNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.Welcome.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {

        composable(Screen.Onboarding.Welcome.route) {
            WelcomeScreen(
                onNext = { navController.navigate(Screen.Onboarding.Permissions.route) }
            )
        }

        composable(Screen.Onboarding.Permissions.route) {
            PermissionsScreen(
                onNext = { navController.navigate(Screen.Onboarding.GoogleSignIn.route) }
            )
        }

        composable(Screen.Onboarding.GoogleSignIn.route) {
            GoogleSignInScreen(
                onSignIn = { navController.navigate(Screen.Onboarding.EmergencyContact.route) },
                onSkip   = { navController.navigate(Screen.Onboarding.EmergencyContact.route) },
            )
        }

        composable(Screen.Onboarding.EmergencyContact.route) {
            val onboardingViewModel: OnboardingCompleteViewModel = hiltViewModel()
            EmergencyContactScreen(
                onNext = {
                    onboardingViewModel.markOnboardingComplete()
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.Welcome.route) { inclusive = true }
                    }
                },
                onSaveContact = { name, phone, relationship ->
                    onboardingViewModel.saveEmergencyContact(name, phone, relationship)
                },
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onActionClick = { logId ->
                    navController.navigate(Screen.ActionDetail.createRoute(logId))
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ActionDetail.route,
            arguments = listOf(
                navArgument(Screen.ActionDetail.ARG_LOG_ID) { type = NavType.LongType }
            ),
        ) { backStackEntry ->
            val logId = backStackEntry.arguments?.getLong(Screen.ActionDetail.ARG_LOG_ID) ?: -1L
            ActionDetailScreen(
                logId = logId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
