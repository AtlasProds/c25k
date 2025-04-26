package com.example.c25kbuddy.util

import android.util.Log

/**
 * Utility class for consistent logging across the application
 */
object DebugLogger {
    private const val TAG_PREFIX = "C25K"
    private var isDebugEnabled = true

    /**
     * Enable or disable debug logging
     */
    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    /**
     * Log a debug message
     */
    fun d(component: String, message: String) {
        if (isDebugEnabled) {
            Log.d("$TAG_PREFIX-$component", message)
        }
    }

    /**
     * Log an error message
     */
    fun e(component: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG_PREFIX-$component", message, throwable)
    }

    /**
     * Log a warning message
     */
    fun w(component: String, message: String) {
        Log.w("$TAG_PREFIX-$component", message)
    }

    /**
     * Log an info message
     */
    fun i(component: String, message: String) {
        Log.i("$TAG_PREFIX-$component", message)
    }

    /**
     * Log the state of a workout
     */
    fun logWorkoutState(component: String, state: com.example.c25kbuddy.data.model.WorkoutState) {
        if (isDebugEnabled) {
            d(component, "Workout State: active=${state.isActive}, " +
                "paused=${state.isPaused}, " +
                "preparing=${state.isPreparing}, " +
                "countdown=${state.countdownSeconds}, " +
                "currentSegment=${state.currentSegment?.activityType}, " +
                "remaining=${state.remainingTimeSeconds}s"
            )
        }
    }
} 