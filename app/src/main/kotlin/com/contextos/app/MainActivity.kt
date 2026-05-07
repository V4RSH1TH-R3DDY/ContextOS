package com.contextos.app

import android.graphics.Color
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
import androidx.compose.foundation.layout.fillMaxSize
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.setBackgroundColor(Color.parseColor("#050508"))

        setContent {
            ContextOSTheme {
                val navController = rememberNavController()
                val viewModel: StartDestinationViewModel = viewModel()
                val startDestination by viewModel.startDestination.collectAsState()

                if (startDestination == null) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                } else {
                    ContextOSNavGraph(
                        navController = navController,
                        startDestination = startDestination!!,
                    )
                }
            }
        }
    }
}
