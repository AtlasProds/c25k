package com.example.c25kbuddy.presentation.screens

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.c25kbuddy.data.model.ActivityType
import com.example.c25kbuddy.data.model.WorkoutEvent
import com.example.c25kbuddy.data.model.WorkoutSegment
import com.example.c25kbuddy.data.model.WorkoutState
import com.example.c25kbuddy.presentation.components.ActivityEmoji
import com.example.c25kbuddy.presentation.components.LargeTimer
import com.example.c25kbuddy.presentation.components.SegmentProgress
import com.example.c25kbuddy.presentation.components.WorkoutControls
import com.example.c25kbuddy.presentation.components.getActivityTypeText
import com.example.c25kbuddy.presentation.components.getColorForActivityType
import com.example.c25kbuddy.presentation.theme.Completed
import com.example.c25kbuddy.presentation.viewmodel.WorkoutViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onNavigateUp: () -> Unit
) {
    val workoutState by viewModel.workoutState.collectAsState()
    val lastEvent by viewModel.lastEvent.collectAsState()
    
    // Track whether the control panel is showing (via left swipe)
    var controlPanelOffset by remember { mutableStateOf(1000f) } // Start offscreen
    val controlPanelVisible = controlPanelOffset < 500f
    
    // Track whether to show the controls screen
    var showControlsScreen by remember { mutableStateOf(false) }
    
    // Track if we should show confirmation dialog
    var showStopConfirmation by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    
    // Handle back button using BackHandler from the accompanist library
    BackHandler(enabled = true) {
        Log.d("WorkoutScreen", "Back pressed, showing exit confirmation")
        showExitConfirmation = true
    }
    
    // When any WorkoutFinished event is received, navigate back immediately
    LaunchedEffect(lastEvent) {
        when (lastEvent) {
            is WorkoutEvent.WorkoutFinished -> {
                // Only navigate away if the workout is confirmed to be not active
                // This prevents navigating back if a new workout was just started
                if (!workoutState.isActive) {
                    Log.d("WorkoutScreen", "Workout finished event received and workout is not active, navigating back")
                    onNavigateUp()
                } else {
                    Log.d("WorkoutScreen", "Ignoring WorkoutFinished event because workout is active")
                }
            }
            is WorkoutEvent.WorkoutStarted -> {
                Log.d("WorkoutScreen", "Workout started event received")
                // No need to navigate away since we're already on the workout screen
                // This event is just to confirm the workout has officially started
            }
            else -> {
                // Other events don't require navigation changes
            }
        }
    }
    
    // Monitor if workout is active and navigate back if not
    LaunchedEffect(workoutState.isActive, workoutState.isPreparing, workoutState.countdownSeconds) {
        android.util.Log.d("WorkoutScreen", "State changed: active=${workoutState.isActive}, preparing=${workoutState.isPreparing}, countdown=${workoutState.countdownSeconds}")
        
        // If workout is active or in countdown state, stay on screen
        if (workoutState.isActive || workoutState.isPreparing || workoutState.countdownSeconds > 0) {
            android.util.Log.d("WorkoutScreen", "Workout is active or in countdown, staying on screen")
            return@LaunchedEffect
        }
        
        // If we reach here, workout is not active and not in preparation phase
        // Give a short grace period to allow state to update
        delay(500)
        
        // Re-check state after delay
        if (viewModel.workoutState.value.isActive || 
            viewModel.workoutState.value.isPreparing || 
            viewModel.workoutState.value.countdownSeconds > 0) {
            android.util.Log.d("WorkoutScreen", "State became active/preparing after delay, staying on screen")
            return@LaunchedEffect
        }
        
        // Check if we just received a start event
        if (lastEvent is WorkoutEvent.WorkoutStarted) {
            android.util.Log.d("WorkoutScreen", "Found WorkoutStarted event, waiting longer")
            delay(1000)
            
            if (viewModel.workoutState.value.isActive || 
                viewModel.workoutState.value.isPreparing) {
                android.util.Log.d("WorkoutScreen", "Workout became active after event check, staying on screen")
                return@LaunchedEffect
            }
        }
        
        // If we reach here, workout is truly not active
        android.util.Log.d("WorkoutScreen", "Workout is not active after all checks, navigating back")
        onNavigateUp()
    }
    
    // Show confirmation dialog if needed
    if (showStopConfirmation) {
        Dialog(
            onDismissRequest = { showStopConfirmation = false }
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Complete workout?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                                
                Button(
                    onClick = {
                        viewModel.finishWorkout()
                        showStopConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Completed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        "Complete",
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        showStopConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        "Cancel",
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    // Show exit confirmation dialog
    if (showExitConfirmation) {
        Dialog(
            onDismissRequest = { showExitConfirmation = false }
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colors.surface)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Exit workout?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Text(
                    text = "You can restart later.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                
                Button(
                    onClick = {
                        viewModel.stopWorkout()
                        showExitConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Red.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        "Exit",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        showExitConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        "Cancel",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    // Show either the controls screen or the main workout screen
    if (showControlsScreen) {
        WorkoutControlsScreen(
            isPaused = workoutState.isPaused,
            onPauseResume = {
                if (workoutState.isPaused) {
                    viewModel.resumeWorkout()
                } else {
                    viewModel.pauseWorkout()
                }
                showControlsScreen = false
            },
            onStop = {
                showExitConfirmation = true
                showControlsScreen = false
            },
            onComplete = {
                showStopConfirmation = true
                showControlsScreen = false
            },
            onBack = {
                showControlsScreen = false
            }
        )
    } else {
        // Main workout screen with drag gesture support
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // If drag ended and panel is more than halfway visible, keep it open
                            // otherwise close it
                            controlPanelOffset = if (controlPanelOffset < 500f) 0f else 1000f
                        },
                        onDragStart = { },
                        onDragCancel = { },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // Positive drag amount means swiping right (opening panel)
                            // Negative drag amount means swiping left (closing panel)
                            controlPanelOffset = (controlPanelOffset - dragAmount).coerceIn(0f, 1000f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Always show the time at the top
            TimeText(modifier = Modifier.align(Alignment.TopCenter))
            
            // Show countdown if in preparing state
            if (workoutState.isPreparing) {
                CountdownScreen(countdownSeconds = workoutState.countdownSeconds)
            } 
            // Show main workout UI
            else {
                WorkoutMainScreen(
                    workoutState = workoutState,
                    viewModel = viewModel,
                    onShowStopConfirmation = { showStopConfirmation = true },
                    onShowExitConfirmation = { showExitConfirmation = true },
                    onShowControls = { showControlsScreen = true }
                )
            }
            
            // Control panel overlay (slides in from right)
            ControlPanel(
                isPaused = workoutState.isPaused,
                onPauseResume = {
                    if (workoutState.isPaused) {
                        viewModel.resumeWorkout()
                    } else {
                        viewModel.pauseWorkout()
                    }
                    controlPanelOffset = 1000f // Close the panel
                },
                onShowExitConfirmation = {
                    showExitConfirmation = true
                    controlPanelOffset = 1000f // Close the panel
                },
                onShowCompleteConfirmation = {
                    showStopConfirmation = true
                    controlPanelOffset = 1000f // Close the panel
                },
                offset = controlPanelOffset.roundToInt()
            )
        }
    }
}

@Composable
fun ControlPanel(
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onShowExitConfirmation: () -> Unit,
    onShowCompleteConfirmation: () -> Unit,
    offset: Int
) {
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offset, 0) }
            .background(MaterialTheme.colors.background.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Workout Controls",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Pause/Resume Button
            Button(
                onClick = onPauseResume,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isPaused) 
                        MaterialTheme.colors.primary 
                    else MaterialTheme.colors.primaryVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = if (isPaused) "Resume Workout" else "Pause Workout",
                    fontSize = 14.sp
                )
            }
            
            // Complete Workout Button
            Button(
                onClick = onShowCompleteConfirmation,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Completed
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Complete Workout",
                    fontSize = 14.sp
                )
            }
            
            // Exit Button
            Button(
                onClick = onShowExitConfirmation,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Exit Without Saving",
                    fontSize = 14.sp
                )
            }
            
            Text(
                text = "← Swipe left to return",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun CountdownScreen(countdownSeconds: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Add padding at the top to push everything down
        Spacer(modifier = Modifier.height(30.dp))
        
        Text(
            text = "Get Ready!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            // Circular progress indicator
            CircularProgressIndicator(
                progress = countdownSeconds / 5f,
                modifier = Modifier.fillMaxSize(),
                indicatorColor = MaterialTheme.colors.primary,
                trackColor = MaterialTheme.colors.surface,
                strokeWidth = 8.dp
            )
            
            // Countdown number
            Text(
                text = "$countdownSeconds",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Starting workout soon...",
            fontSize = 16.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WorkoutMainScreen(
    workoutState: WorkoutState,
    viewModel: WorkoutViewModel,
    onShowStopConfirmation: () -> Unit,
    onShowExitConfirmation: () -> Unit,
    onShowControls: () -> Unit  // New parameter for navigation to controls
) {
    val segment = workoutState.currentSegment ?: return
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header section - top part
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 26.dp)
        ) {
            // Week/Day header
            Text(
                text = "Week ${workoutState.currentWeek} Day ${workoutState.currentDay}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Segment progress
            SegmentProgress(
                currentSegment = workoutState.currentSegmentIndex + 1,
                totalSegments = workoutState.totalSegments,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        
        // Center timer section - optimized for circular screen
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(130.dp)
                .clip(CircleShape)
                .background(getColorForActivityType(segment.activityType).copy(alpha = 0.2f))
                // Make the entire timer area clickable to navigate to controls screen
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // No visual indication on click
                ) {
                    onShowControls()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Activity emoji
                Text(
                    text = segment.activityType.getEmojiIcon(),
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
                
                // Bold activity type
                Text(
                    text = segment.activityType.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = getColorForActivityType(segment.activityType),
                    textAlign = TextAlign.Center
                )
                
                // Timer
                Text(
                    text = viewModel.formatTime(workoutState.remainingTimeSeconds),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = getColorForActivityType(segment.activityType),
                    textAlign = TextAlign.Center
                )
                
                if (workoutState.isPaused) {
                    Text(
                        text = "PAUSED",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Bottom controls section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            // Controls - optimized for watch
            WorkoutControls(
                isPaused = workoutState.isPaused,
                onPauseResume = {
                    if (workoutState.isPaused) {
                        viewModel.resumeWorkout()
                    } else {
                        viewModel.pauseWorkout()
                    }
                },
                onSkip = { viewModel.skipToNextSegment() },
                onStop = onShowExitConfirmation
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Total time display
            Text(
                text = "Total: ${viewModel.formatTime(workoutState.elapsedTimeSeconds)} / " +
                       "${viewModel.formatTime(workoutState.totalTimeSeconds)}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun WorkoutControlsScreen(
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        // Always show the time at the top
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // First row: Exit and Pause/Resume
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Stop Button - using stop emoji as requested
                ControlButton(
                    onClick = onStop,
                    emoji = "🛑",  // Changed back to stop sign emoji
                    label = "Stop",
                    backgroundColor = Color.Red.copy(alpha = 0.8f)
                )
                
                // Pause/Resume Button
                ControlButton(
                    onClick = onPauseResume,
                    emoji = if (isPaused) "▶️" else "⏸️",
                    label = if (isPaused) "Resume" else "Pause",
                    backgroundColor = if (isPaused) 
                        Color.Green.copy(alpha = 0.8f) 
                    else Color.Yellow.copy(alpha = 0.8f)
                )
            }
            
            // Second row: Complete and Back
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Complete button - new button for marking as done
                ControlButton(
                    onClick = onComplete,
                    emoji = "✅",
                    label = "Complete",
                    backgroundColor = Color.Green.copy(alpha = 0.8f)
                )
                
                // Back Button
                ControlButton(
                    onClick = onBack,
                    emoji = "⬅️",
                    label = "Back",
                    backgroundColor = Color.Gray.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun ControlButton(
    onClick: () -> Unit,
    emoji: String,
    label: String,
    backgroundColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
} 