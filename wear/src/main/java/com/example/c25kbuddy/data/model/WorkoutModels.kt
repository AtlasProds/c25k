package com.example.c25kbuddy.data.model

import kotlinx.serialization.Serializable

/**
 * Represents the entire C25K program with all weeks
 */
@Serializable
data class C25KProgram(
    val weeks: List<Week>
) {
    fun getDay(weekNumber: Int, dayNumber: Int): Day? {
        return weeks.find { it.week == weekNumber }?.days?.getOrNull(dayNumber - 1)
    }
}

/**
 * Represents a week in the C25K program
 */
@Serializable
data class Week(
    val week: Int,
    val days: List<Day>
) {
    val isCompleted: Boolean
        get() = days.all { it.isCompleted }
}

/**
 * Represents a day in the C25K program
 */
@Serializable
data class Day(
    val segments: List<Int>? = null,
    val segmentsReference: String? = null
) {
    var isCompleted: Boolean = false
    
    // For UI display
    fun getFormattedTitle(): String = "Day ${position + 1}"
    
    // The position in the week (0-indexed)
    var position: Int = 0
}

/**
 * Represents the type of activity in a workout segment
 */
enum class ActivityType {
    WARMUP,
    WALK,
    RUN,
    COOLDOWN;
    
    fun getDisplayName(): String = when(this) {
        WARMUP -> "Warm Up"
        WALK -> "Walk"
        RUN -> "Run"
        COOLDOWN -> "Cool Down"
    }
    
    fun getEmojiIcon(): String = when(this) {
        WARMUP -> "🔥"
        WALK -> "🚶"
        RUN -> "🏃"
        COOLDOWN -> "❄️"
    }
}

/**
 * Represents a segment in a workout with duration and activity type
 */
data class WorkoutSegment(
    val durationSeconds: Int,
    val activityType: ActivityType,
    val position: Int
)

/**
 * Represents a user's progress in the C25K program
 */
@Serializable
data class UserProgress(
    var lastCompletedWeek: Int = 0,
    var lastCompletedDay: Int = 0,
    val completedWorkouts: MutableList<String> = mutableListOf()
) {
    fun getNextWorkout(): Pair<Int, Int> {
        // If we've completed all days in a week, move to the next week
        if (lastCompletedDay >= 3) {
            return Pair(lastCompletedWeek + 1, 1)
        }
        
        // Otherwise move to the next day in the same week
        return Pair(lastCompletedWeek, lastCompletedDay + 1)
    }
    
    fun isWorkoutCompleted(week: Int, day: Int): Boolean {
        return completedWorkouts.contains("$week-$day")
    }
    
    /**
     * Check if a workout is available to be started based on completed workouts
     * A workout is available if:
     * - It's the first workout (Week 1, Day 1)
     * - It's already completed
     * - It's the next workout in sequence after the last completed one
     */
    fun isWorkoutAvailable(week: Int, day: Int): Boolean {
        // First workout is always available
        if (week == 1 && day == 1) return true
        
        // Already completed workouts are available
        if (isWorkoutCompleted(week, day)) return true
        
        // Get the next workout based on last completed
        val (nextWeek, nextDay) = getNextWorkout()
        
        // Check if this is the next workout in sequence
        return week == nextWeek && day == nextDay
    }
    
    fun markWorkoutCompleted(week: Int, day: Int) {
        val key = "$week-$day"
        if (!completedWorkouts.contains(key)) {
            completedWorkouts.add(key)
            
            // Update lastCompletedWeek and lastCompletedDay
            if (week > lastCompletedWeek || (week == lastCompletedWeek && day > lastCompletedDay)) {
                // Only update if this workout is newer than the last completed one
                lastCompletedWeek = week
                lastCompletedDay = day
            }
        }
    }
}

/**
 * Events emitted by the workout engine
 */
sealed class WorkoutEvent {
    data class SegmentStarted(
        val segment: WorkoutSegment,
        val remainingTimeSeconds: Int,
        val totalSegments: Int
    ) : WorkoutEvent()
    
    data class SegmentCompleted(val segment: WorkoutSegment) : WorkoutEvent()
    
    data object WorkoutFinished : WorkoutEvent()
    
    // New event specifically for showing completion success screen
    data class WorkoutCompletedWithSuccess(
        val week: Int,
        val day: Int
    ) : WorkoutEvent()
    
    data class TimerTick(
        val currentSegment: WorkoutSegment,
        val remainingTimeSeconds: Int,
        val elapsedTimeSeconds: Int,
        val totalTimeSeconds: Int
    ) : WorkoutEvent()
    
    data class WorkoutPaused(val remainingTimeSeconds: Int) : WorkoutEvent()
    
    data class WorkoutResumed(val remainingTimeSeconds: Int) : WorkoutEvent()
    
    data class CountdownTick(
        val remainingSeconds: Int
    ) : WorkoutEvent()
    
    data object CountdownFinished : WorkoutEvent()
    
    // New event type for workout errors
    data class WorkoutError(val errorMessage: String) : WorkoutEvent()
}

/**
 * Represents the current state of an active workout
 */
data class WorkoutState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isPreparing: Boolean = false,  // Added for pre-workout countdown
    val countdownSeconds: Int = 0,     // For tracking the pre-workout countdown
    val currentWeek: Int = 1,
    val currentDay: Int = 1,
    val currentSegmentIndex: Int = 0,
    val currentSegment: WorkoutSegment? = null,
    val remainingTimeSeconds: Int = 0,
    val elapsedTimeSeconds: Int = 0,
    val totalTimeSeconds: Int = 0,
    val totalSegments: Int = 0,
    val hasStartedSegments: Boolean = false // Flag to track if workout has actively started segments
) 