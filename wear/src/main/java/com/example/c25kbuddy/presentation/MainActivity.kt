/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.c25kbuddy.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.c25kbuddy.presentation.screens.HomeScreen
import com.example.c25kbuddy.presentation.screens.WorkoutScreen
import com.example.c25kbuddy.presentation.theme.C25KBuddyTheme
import com.example.c25kbuddy.presentation.viewmodel.WorkoutViewModel

class MainActivity : ComponentActivity() {
    
    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.HIGH_SAMPLING_RATE_SENSORS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 34) { // Android 14 (U)
            add(Manifest.permission.FOREGROUND_SERVICE_HEALTH)
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all required permissions are granted
        val allGranted = permissions.entries.all { it.value }
        
        if (!allGranted) {
            // Some permissions were denied - you might want to show a message or handle this case
            // For simplicity in this example, we'll just check again next time the app starts
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)
        
        // Check and request permissions
        checkAndRequestPermissions()

        setContent {
            C25KBuddyTheme {
                C25KApp()
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }
}

/**
 * Main composable for the C25K app
 */
@Composable
fun C25KApp() {
    // Create view model
    val viewModel: WorkoutViewModel = viewModel(
        factory = WorkoutViewModel.Factory(
            application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
        )
    )
    
    // Track current screen
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    
    // Handle navigation
    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToWorkout = {
                    currentScreen = Screen.Workout
                }
            )
        }
        
        is Screen.Workout -> {
            WorkoutScreen(
                viewModel = viewModel,
                onNavigateUp = {
                    currentScreen = Screen.Home
                }
            )
        }
    }
}

/**
 * Sealed class representing app screens
 */
sealed class Screen {
    object Home : Screen()
    object Workout : Screen()
}