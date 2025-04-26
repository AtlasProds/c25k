package com.example.c25kbuddy.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.c25kbuddy.data.model.ActivityType
import com.example.c25kbuddy.presentation.theme.Accent
import com.example.c25kbuddy.presentation.theme.CooldownColor
import com.example.c25kbuddy.presentation.theme.Completed
import com.example.c25kbuddy.presentation.theme.InProgress
import com.example.c25kbuddy.presentation.theme.RunColor
import com.example.c25kbuddy.presentation.theme.Upcoming
import com.example.c25kbuddy.presentation.theme.WalkColor
import com.example.c25kbuddy.presentation.theme.WarmupColor

/**
 * Get the display text for activity type (RUN or WALK)
 */
@Composable
fun getActivityTypeText(activityType: ActivityType): String {
    return when (activityType) {
        ActivityType.RUN -> "RUN"
        ActivityType.WALK -> "WALK"
        ActivityType.WARMUP -> "WARMUP"
        ActivityType.COOLDOWN -> "COOLDOWN"
    }
}

/**
 * Large timer display with color accent for the workout screen
 */
@Composable
fun LargeTimer(
    seconds: Int,
    color: Color,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    val timeText = "%02d:%02d".format(minutes, remainingSeconds)
    
    Box(
        modifier = modifier
            .padding(vertical = 12.dp)
            .size(140.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        // Pulsating effect when not paused
        val alpha = if (!isPaused) {
            val infiniteTransition = rememberInfiniteTransition()
            infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            ).value
        } else {
            0.8f
        }
        
        // Inner circle
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha * 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = timeText,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    textAlign = TextAlign.Center
                )
                
                if (isPaused) {
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
    }
}

/**
 * Animated activity emoji
 */
@Composable
fun ActivityEmoji(
    activityType: ActivityType,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Text(
        text = activityType.getEmojiIcon(),
        fontSize = 28.sp,
        modifier = modifier
            .scale(scale)
            .alpha(alpha),
        textAlign = TextAlign.Center
    )
}

/**
 * Control button row for workout optimized for Galaxy Watch touch targets
 */
@Composable
fun WorkoutControls(
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Pause/Resume button - larger touch target
        Button(
            onClick = onPauseResume,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isPaused) Accent else InProgress
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Text(
                text = if (isPaused) "▶️" else "⏸️",
                fontSize = 24.sp
            )
        }
        
        // Skip button - larger touch target
        Button(
            onClick = onSkip,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Upcoming
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Text(
                text = "⏭️",
                fontSize = 24.sp
            )
        }
        
        // Stop button - larger touch target
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = RunColor
            ),
            modifier = Modifier.size(56.dp)
        ) {
            Text(
                text = "⏹️",
                fontSize = 24.sp
            )
        }
    }
}

/**
 * Progress indicator showing segment position with improved visual style
 */
@Composable
fun SegmentProgress(
    currentSegment: Int, 
    totalSegments: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Segment $currentSegment of $totalSegments",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Visual segment progress indicator
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 1..totalSegments) {
                val color = when {
                    i < currentSegment -> Completed.copy(alpha = 0.6f)
                    i == currentSegment -> InProgress
                    else -> MaterialTheme.colors.surface.copy(alpha = 0.4f)
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}

/**
 * Workout tile for selecting a day, optimized for Galaxy Watch circular display
 */
@Composable
fun WorkoutDayTile(
    weekNumber: Int,
    dayNumber: Int,
    isCompleted: Boolean,
    isNext: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isCompleted -> Completed
        isNext -> Upcoming
        !isAvailable -> MaterialTheme.colors.surface.copy(alpha = 0.5f)
        else -> MaterialTheme.colors.surface
    }
    
    // Create a circular button style chip
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .height(56.dp),
        enabled = isAvailable
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Status indicator (left)
            if (isCompleted || isNext || !isAvailable) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> Completed.copy(alpha = 0.3f)
                                isNext -> Upcoming.copy(alpha = 0.3f)
                                !isAvailable -> MaterialTheme.colors.surface.copy(alpha = 0.4f)
                                else -> MaterialTheme.colors.surface.copy(alpha = 0.3f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Text(text = "✓", fontSize = 20.sp)
                    } else if (isNext) {
                        Text(text = "▶", fontSize = 18.sp)
                    } else if (!isAvailable) {
                        Text(text = "🔒", fontSize = 18.sp)
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Day info (center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Day $dayNumber",
                    style = MaterialTheme.typography.body1.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = if (!isAvailable) MaterialTheme.colors.onSurface.copy(alpha = 0.5f) else MaterialTheme.colors.onSurface
                )
                Text(
                    text = "Week $weekNumber",
                    style = MaterialTheme.typography.body2.copy(
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = if (!isAvailable) MaterialTheme.colors.onSurface.copy(alpha = 0.5f) else MaterialTheme.colors.onSurface
                )
            }
        }
    }
}

/**
 * Get appropriate color for activity type
 */
@Composable
fun getColorForActivityType(activityType: ActivityType): Color {
    return when (activityType) {
        ActivityType.WARMUP -> WarmupColor
        ActivityType.RUN -> RunColor
        ActivityType.WALK -> WalkColor
        ActivityType.COOLDOWN -> CooldownColor
    }
}

/**
 * Extension function to get emoji icon for each activity type
 */
fun ActivityType.getEmojiIcon(): String {
    return when (this) {
        ActivityType.WARMUP -> "🔥"
        ActivityType.WALK -> "🚶"
        ActivityType.RUN -> "🏃"
        ActivityType.COOLDOWN -> "❄️"
    }
} 