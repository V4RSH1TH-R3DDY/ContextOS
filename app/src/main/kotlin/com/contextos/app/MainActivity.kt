package com.contextos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.contextos.app.ui.navigation.ContextOSNavGraph
import com.contextos.app.ui.navigation.Screen
import com.contextos.app.ui.theme.ContextOSTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContextOSTheme {
                val navController = rememberNavController()

                // TODO (Phase 5.1): Replace with a real first-run check backed by DataStore.
                //   • First run  → startDestination = Screen.Onboarding.Welcome.route
                //   • Returning  → startDestination = Screen.Dashboard.route
                ContextOSNavGraph(
                    navController    = navController,
                    startDestination = Screen.Onboarding.Welcome.route,
                )
            }
        }
    }
}
