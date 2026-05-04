package com.contextos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.contextos.app.ui.navigation.ContextOSNavGraph
import com.contextos.app.ui.navigation.StartDestinationViewModel
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
                val viewModel: StartDestinationViewModel = viewModel()
                val startDestination by viewModel.startDestination.collectAsState()

                ContextOSNavGraph(
                    navController = navController,
                    startDestination = startDestination,
                )
            }
        }
    }
}
