package com.example.c25kbuddy.domain

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.c25kbuddy.data.model.ActivityType
import com.example.c25kbuddy.data.model.Day
import com.example.c25kbuddy.data.model.WorkoutEvent
import com.example.c25kbuddy.data.model.WorkoutSegment
import com.example.c25kbuddy.data.model.WorkoutState
import com.example.c25kbuddy.data.repository.C25KRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Core engine that manages the workout state, timer, and transitions
 */
class WorkoutEngine(
    private val repository: C25KRepository,
    private val vibrator: Vibrator,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TIMER_UPDATE_INTERVAL_MS = 1000L  // Update once per second
        private const val VIBRATION_DURATION_MS = 600L
    }
    
    // Workout state
    private val _state = MutableStateFlow(WorkoutState())
    val state = _state.asStateFlow()
    
    // Events
    private val _events = MutableStateFlow<WorkoutEvent?>(null)
    val events: Flow<WorkoutEvent?> = _events
    
    private var timerJob: Job? = null
    private var lastTickTime = 0L
    
    private var segments: List<WorkoutSegment> = emptyList()
    
    // Track if any segments were actually completed (not just started)
    private var segmentsCompleted = false
    
    /**
     * Start a workout for the given week and day
     */
    suspend fun startWorkout(week: Int, day: Int) {
        // Stop any active timer
        stopTimer()
        
        // Reset tracking variables
        segmentsCompleted = false
        
        // Load the program if needed
        val program = repository.loadProgram()
        
        // Get the day
        val workoutDay = program.getDay(week, day) ?: return
        
        // Get the segments
        segments = repository.createWorkoutSegments(workoutDay)
        if (segments.isEmpty()) return
        
        // Calculate total time
        val totalTime = segments.sumOf { it.durationSeconds }
        
        // Initialize workout state with preparing mode
        _state.value = WorkoutState(
            isActive = true,
            isPaused = false,
            isPreparing = true,
            countdownSeconds = 5, // 5-second countdown
            currentWeek = week,
            currentDay = day,
            currentSegmentIndex = 0,
            currentSegment = segments.firstOrNull(),
            remainingTimeSeconds = segments.firstOrNull()?.durationSeconds ?: 0,
            elapsedTimeSeconds = 0,
            totalTimeSeconds = totalTime,
            totalSegments = segments.size
        )
        
        // Start countdown
        startCountdown()
    }
    
    /**
     * Start the countdown timer before the workout begins
     */
    private fun startCountdown() {
        // Cancel any existing timer
        timerJob?.cancel()
        
        android.util.Log.d("WorkoutEngine", "Starting 5-second countdown")
        
        timerJob = coroutineScope.launch {
            while (isActive && _state.value.countdownSeconds > 0) {
                // Emit countdown tick event
                _events.value = WorkoutEvent.CountdownTick(_state.value.countdownSeconds)
                
                // Wait 1 second
                delay(1000)
                
                // Update countdown
                _state.value = _state.value.copy(
                    countdownSeconds = _state.value.countdownSeconds - 1
                )
            }
            
            // Countdown finished
            if (isActive) {
                // Vibrate to indicate countdown finished
                val vibrationEffect = VibrationEffect.createOneShot(
                    500, // 500ms vibration
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
                
                // Emit countdown finished event
                _events.value = WorkoutEvent.CountdownFinished
                
                // Start the actual workout
                _state.value = _state.value.copy(
                    isPreparing = false,
                    countdownSeconds = 0
                )
                
                // Start the main workout timer
                startTimer()
                
                // Emit segment started event for the first segment
                val firstSegment = segments.firstOrNull() ?: return@launch
                emitSegmentStarted(firstSegment)
            }
        }
    }
    
    /**
     * Pause the current workout
     */
    fun pauseWorkout() {
        if (_state.value.isActive && !_state.value.isPaused) {
            stopTimer()
            _state.value = _state.value.copy(isPaused = true)
            _events.value = WorkoutEvent.WorkoutPaused(_state.value.remainingTimeSeconds)
        }
    }
    
    /**
     * Resume the paused workout
     */
    fun resumeWorkout() {
        if (_state.value.isActive && _state.value.isPaused) {
            _state.value = _state.value.copy(isPaused = false)
            startTimer()
            _events.value = WorkoutEvent.WorkoutResumed(_state.value.remainingTimeSeconds)
        }
    }
    
    /**
     * Stop the workout
     */
    fun stopWorkout() {
        stopTimer()
        // Just set the workout as inactive without emitting completion events
        // This way the workout won't be marked as completed
        _state.value = _state.value.copy(isActive = false, isPaused = false)
        
        // Emit a specific event for workout being abandoned
        _events.value = WorkoutEvent.WorkoutFinished
    }
    
    /**
     * Skip to the next segment
     */
    fun skipToNextSegment() {
        val currentState = _state.value
        val nextSegmentIndex = currentState.currentSegmentIndex + 1
        
        if (nextSegmentIndex < segments.size) {
            val nextSegment = segments[nextSegmentIndex]
            
            // Update state
            _state.value = currentState.copy(
                currentSegmentIndex = nextSegmentIndex,
                currentSegment = nextSegment,
                remainingTimeSeconds = nextSegment.durationSeconds
            )
            
            // Track that a segment was completed
            segmentsCompleted = true
            
            // Emit events
            _events.value = WorkoutEvent.SegmentCompleted(currentState.currentSegment!!)
            emitSegmentStarted(nextSegment)
        } else {
            // This was the last segment, finish workout
            coroutineScope.launch {
                finishWorkout()
            }
        }
    }
    
    /**
     * Mark the current workout as completed
     */
    suspend fun finishWorkout() {
        stopTimer()
        
        // Save progress
        repository.markWorkoutCompleted(_state.value.currentWeek, _state.value.currentDay)
        
        // Emit regular finished event
        _events.value = WorkoutEvent.WorkoutFinished
        
        // If we completed at least one segment, emit the success event to show confetti
        if (segmentsCompleted) {
            _events.value = WorkoutEvent.WorkoutCompletedWithSuccess(
                week = _state.value.currentWeek,
                day = _state.value.currentDay
            )
        }
        
        // Reset state
        _state.value = _state.value.copy(isActive = false, isPaused = false)
    }
    
    /**
     * Start the timer job
     */
    private fun startTimer() {
        // Cancel any existing timer
        timerJob?.cancel()
        
        // Set the current time as the starting point for tracking elapsed time
        lastTickTime = SystemClock.elapsedRealtime()
        
        // Start a new coroutine for timer updates
        timerJob = coroutineScope.launch {
            while (isActive) {
                // Update timer state
                updateTimer()
                
                // Wait a short interval before next update
                delay(TIMER_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop the timer job
     */
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
    
    /**
     * Update the timer state
     */
    private fun updateTimer() {
        val currentTime = SystemClock.elapsedRealtime()
        
        // Calculate elapsed time in seconds (using double for precision)
        val elapsedSeconds = (currentTime - lastTickTime) / 1000.0
        
        // Update the last tick time for the next calculation
        lastTickTime = currentTime
        
        // Skip updates if no time has passed
        if (elapsedSeconds <= 0) return
        
        val currentState = _state.value
        if (!currentState.isActive || currentState.isPaused) return
        
        val currentSegment = currentState.currentSegment ?: return
        
        // Update remaining time (coerce to minimum of 0)
        val remainingTime = (currentState.remainingTimeSeconds - elapsedSeconds).coerceAtLeast(0.0)
        
        // Update elapsed time for the whole workout
        val elapsedTimeTotal = currentState.elapsedTimeSeconds + elapsedSeconds.toInt()
        
        // Update state with new values
        _state.value = currentState.copy(
            remainingTimeSeconds = remainingTime.toInt(),
            elapsedTimeSeconds = elapsedTimeTotal
        )
        
        // Emit timer tick event
        _events.value = WorkoutEvent.TimerTick(
            currentSegment = currentSegment,
            remainingTimeSeconds = remainingTime.toInt(),
            elapsedTimeSeconds = elapsedTimeTotal,
            totalTimeSeconds = currentState.totalTimeSeconds
        )
        
        // Check if segment completed
        if (remainingTime <= 0) {
            // Move to next segment
            val nextSegmentIndex = currentState.currentSegmentIndex + 1
            
            if (nextSegmentIndex < segments.size) {
                val nextSegment = segments[nextSegmentIndex]
                
                // Update state
                _state.value = currentState.copy(
                    currentSegmentIndex = nextSegmentIndex,
                    currentSegment = nextSegment,
                    remainingTimeSeconds = nextSegment.durationSeconds
                )
                
                // Track that a segment was completed
                segmentsCompleted = true
                
                // Emit events
                _events.value = WorkoutEvent.SegmentCompleted(currentSegment)
                emitSegmentStarted(nextSegment)
            } else {
                // This was the last segment, finish workout
                coroutineScope.launch {
                    finishWorkout()
                }
            }
        }
    }
    
    /**
     * Emit segment started event and trigger vibration
     */
    private fun emitSegmentStarted(segment: WorkoutSegment) {
        // Different vibration patterns based on activity type
        val vibrationEffect = when (segment.activityType) {
            ActivityType.RUN -> {
                // Double vibration for RUN
                VibrationEffect.createWaveform(
                    longArrayOf(0, 250, 100, 250),  // timing (0ms delay, 250ms vibrate, 100ms pause, 250ms vibrate)
                    intArrayOf(0, 255, 0, 255),     // amplitudes
                    -1                              // don't repeat
                )
            }
            else -> {
                // Single vibration for WALK (including WARMUP and COOLDOWN)
                VibrationEffect.createOneShot(
                    400,  // 400ms vibration
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            }
        }
        
        // Vibrate
        vibrator.vibrate(vibrationEffect)
        
        // Update state to indicate we're actually running segments
        _state.value = _state.value.copy(hasStartedSegments = true)
        
        // Emit event
        _events.value = WorkoutEvent.SegmentStarted(
            segment = segment,
            remainingTimeSeconds = segment.durationSeconds,
            totalSegments = segments.size
        )
    }
} 