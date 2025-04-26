package com.example.c25kbuddy.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.c25kbuddy.data.model.C25KProgram
import com.example.c25kbuddy.presentation.components.WorkoutDayTile
import com.example.c25kbuddy.presentation.theme.Accent
import com.example.c25kbuddy.presentation.theme.Upcoming
import com.example.c25kbuddy.presentation.viewmodel.WorkoutViewModel

@Composable
fun HomeScreen(
    viewModel: WorkoutViewModel,
    onNavigateToWorkout: () -> Unit
) {
    val program by viewModel.program.collectAsState()
    val userProgress by viewModel.userProgress.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // Track if reset confirmation dialog should be shown
    var showResetConfirmation by remember { mutableStateOf(false) }
    
    // Track if we attempted to start a workout
    var startingWorkout by remember { mutableStateOf(false) }
    
    // Observe the uiState for changes to navigate
    LaunchedEffect(uiState, startingWorkout) {
        if (startingWorkout && !uiState.permissionError) {
            // Navigate to workout screen
            onNavigateToWorkout()
            android.util.Log.d("HomeScreen", "Navigating to workout screen after successful start")
            startingWorkout = false
        } else if (startingWorkout && uiState.permissionError) {
            // Error occurred, don't navigate
            android.util.Log.e("HomeScreen", "Error starting workout: ${uiState.errorMessage}")
            startingWorkout = false
        }
    }
    
    val nextWeek: Int
    val nextDay: Int
    
    if (userProgress.lastCompletedWeek == 0 && userProgress.lastCompletedDay == 0) {
        // First time user - start with Week 1, Day 1
        nextWeek = 1
        nextDay = 1
    } else {
        val pair = userProgress.getNextWorkout()
        nextWeek = pair.first
        nextDay = pair.second
    }
    
    // Show reset confirmation dialog if needed
    if (showResetConfirmation) {
        Dialog(
            onDismissRequest = { showResetConfirmation = false }
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Reset Progress?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = "This will reset all your workout progress. You'll start again from Week 1 Day 1. Continue?",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                
                Button(
                    onClick = {
                        // Reset all progress
                        viewModel.resetAllProgress()
                        android.util.Log.d("HomeScreen", "Reset all workout progress")
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        "Yes, Reset All",
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        showResetConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        "Cancel",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
    
    // Main screen container with circular background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        // Always show the time at the top, but don't let it overlap content
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Add extra top padding to avoid overlapping with the system time
                .padding(top = 36.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // App title with more prominence
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(120.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        // Add debug log
                        android.util.Log.d("HomeScreen", "Starting workout: Week $nextWeek Day $nextDay")
                        
                        // Mark that we're starting a workout
                        startingWorkout = true
                        
                        // Use startNextWorkout() instead of passing parameters manually
                        viewModel.startNextWorkout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Accent
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "START",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "W${nextWeek}D${nextDay}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // Hidden debug button to set progress
            Button(
                onClick = {
                    // Show confirmation dialog
                    showResetConfirmation = true
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface
                ),
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(30.dp)
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "Reset Progress",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Show error message if permissions are missing
            if (uiState.permissionError) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Missing required permissions",
                        color = androidx.compose.ui.graphics.Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    Button(
                        onClick = {
                            // This will trigger permission request again when user goes to app settings
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null)
                            )
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Open Settings",
                            fontSize = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Main workout start button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Show workout stats text
                Text(
                    text = "Week $nextWeek Day $nextDay",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Program weeks and days - enhanced for better scrolling
            if (program != null) {
                ProgramList(
                    program = program!!,
                    currentProgress = userProgress,
                    nextWeek = nextWeek,
                    nextDay = nextDay,
                    onDaySelected = { week, day ->
                        // Add debug log
                        android.util.Log.d("HomeScreen", "Day selected from program list: Week $week Day $day")
                        
                        // Mark that we're starting a workout
                        startingWorkout = true
                        
                        // Start the workout
                        viewModel.startWorkout(week, day)
                    }
                )
            }
        }
    }
}

@Composable
fun ProgramList(
    program: C25KProgram,
    currentProgress: com.example.c25kbuddy.data.model.UserProgress,
    nextWeek: Int,
    nextDay: Int,
    onDaySelected: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Show all weeks in the program with enhanced visuals
        for (weekData in program.weeks) {
            val week = weekData.week
            
            // Week header with circular indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$week",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Week $week",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }
            }
            
            // Days with enhanced styling
            weekData.days.forEachIndexed { index, _ ->
                val dayNumber = index + 1
                val isCompleted = currentProgress.isWorkoutCompleted(week, dayNumber)
                val isNext = week == nextWeek && dayNumber == nextDay
                val isAvailable = currentProgress.isWorkoutAvailable(week, dayNumber)
                
                WorkoutDayTile(
                    weekNumber = week,
                    dayNumber = dayNumber,
                    isCompleted = isCompleted,
                    isNext = isNext,
                    isAvailable = isAvailable,
                    onClick = { onDaySelected(week, dayNumber) }
                )
            }
        }
    }
} 