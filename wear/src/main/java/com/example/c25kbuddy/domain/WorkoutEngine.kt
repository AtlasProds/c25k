package com.example.c25kbuddy.domain

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.c25kbuddy.data.model.ActivityType
import com.example.c25kbuddy.data.model.C25KProgram
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
import kotlinx.coroutines.runBlocking

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
    
    // C25K program data - load from repository synchronously to prevent timing issues
    private val program: C25KProgram = runBlocking { repository.loadProgram() }
    
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
    fun startWorkout(week: Int, day: Int): Boolean {
        // Reset state at the beginning
        _state.value = WorkoutState()
        
        android.util.Log.d("WorkoutEngine", "Starting workout for week $week, day $day")
        
        // Find the workout day
        val workoutDay = program.weeks.getOrNull(week - 1)?.days?.getOrNull(day - 1)
        if (workoutDay == null) {
            android.util.Log.e("WorkoutEngine", "Could not find workout day for week $week, day $day")
            return false
        }
        
        // Get segments for the day - convert them to workout segments
        segments = repository.createWorkoutSegments(workoutDay)
        if (segments.isEmpty()) {
            android.util.Log.e("WorkoutEngine", "No segments found for week $week, day $day")
            return false
        }
        
        // Initialize state - explicitly set isPreparing to true before countdown
        _state.value = _state.value.copy(
            isActive = true,
            isPaused = false,
            isPreparing = true,
            countdownSeconds = 5,
            currentWeek = week,
            currentDay = day,
            totalSegments = segments.size
        )
        
        android.util.Log.d("WorkoutEngine", "Initialized workout state: active=${_state.value.isActive}, preparing=${_state.value.isPreparing}")
        
        // Start countdown timer
        android.util.Log.d("WorkoutEngine", "Starting countdown timer")
        startCountdown(5)
        
        // Emit an event that workout has started
        _events.value = WorkoutEvent.WorkoutStarted
        
        return true
    }
    
    /**
     * Start a countdown before beginning the workout
     */
    private fun startCountdown(countdownFrom: Int) {
        android.util.Log.d("WorkoutEngine", "Starting $countdownFrom-second countdown")
        
        // Ensure any previous timer is canceled
        timerJob?.cancel()
        timerJob = null
        lastTickTime = 0L
        
        // Reset countdown completed flag
        var countdownCompleted = false
        
        timerJob = coroutineScope.launch {
            // Start countdown
            for (i in countdownFrom downTo 1) {
                // Update the state with the current countdown second
                _state.value = _state.value.copy(
                    isPreparing = true,
                    countdownSeconds = i
                )
                
                // Emit countdown tick event
                _events.value = WorkoutEvent.CountdownTick(i)
                android.util.Log.d("WorkoutEngine", "Countdown tick: $i")
                delay(TIMER_UPDATE_INTERVAL_MS)
            }
            
            // Prevent multiple completions
            if (!countdownCompleted) {
                countdownCompleted = true
                
                // Vibrate to indicate countdown complete
                val vibrationEffect = VibrationEffect.createOneShot(
                    VIBRATION_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
                
                // Update the state to indicate countdown is finished
                _state.value = _state.value.copy(
                    isPreparing = false,
                    countdownSeconds = 0
                )
                
                // Emit countdown finished event
                _events.value = WorkoutEvent.CountdownFinished
                android.util.Log.d("WorkoutEngine", "Countdown finished")
                
                // Start the actual workout timer
                startTimer()
                
                // Start first segment
                if (segments.isNotEmpty()) {
                    val firstSegment = segments[0]
                    _state.value = _state.value.copy(
                        currentSegmentIndex = 0,
                        currentSegment = firstSegment,
                        remainingTimeSeconds = firstSegment.durationSeconds
                    )
                    
                    // Emit segment started event
                    emitSegmentStarted(firstSegment)
                }
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
     * Stop the workout completely
     */
    fun stopWorkout() {
        android.util.Log.d("WorkoutEngine", "Stopping workout")
        
        // Cancel timer
        timerJob?.cancel()
        timerJob = null

        // Cancel any pending alarms
        timeoutHandler.removeCallbacksAndMessages(null)
        
        // Reset state completely
        _state.value = WorkoutState()
        
        // Emit stop event
        _events.value = WorkoutEvent.WorkoutStopped
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
     * Start the main workout timer
     */
    private fun startTimer() {
        // Cancel any existing timer
        stopTimer()
        
        android.util.Log.d("WorkoutEngine", "Starting workout timer")
        
        // Capture start time for accurate timing
        if (lastTickTime == 0L) {
            lastTickTime = SystemClock.elapsedRealtime()
        }
        
        // Start a new coroutine for timer updates
        timerJob = coroutineScope.launch {
            while (coroutineScope.isActive && _state.value.isActive) {
                // Update timer state
                updateTimer()
                
                // Wait for next tick
                delay(TIMER_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop the timer
     */
    private fun stopTimer() {
        android.util.Log.d("WorkoutEngine", "Stopping timer")
        timerJob?.cancel()
        timerJob = null
    }
    
    /**
     * Update the timer state based on elapsed time
     */
    private fun updateTimer() {
        if (!_state.value.isActive || _state.value.isPaused) {
            return
        }
        
        val currentSegmentIndex = _state.value.currentSegmentIndex
        if (currentSegmentIndex >= segments.size) {
            // No more segments, workout is complete
            completeWorkout()
            return
        }
        
        // Decrement remaining time by 1 each tick (simulated second)
        var remainingSeconds = _state.value.remainingTimeSeconds - 1
        val totalElapsed = _state.value.elapsedTimeSeconds + 1 // Increment total elapsed time by 1 simulated second
        
        if (remainingSeconds <= 0) {
            // Current segment completed
            segmentsCompleted = true
            
            // Emit segment completed event
            emitSegmentCompleted(segments[currentSegmentIndex])
            
            // Move to next segment if available
            val nextSegmentIndex = currentSegmentIndex + 1
            if (nextSegmentIndex < segments.size) {
                // Start next segment
                val nextSegment = segments[nextSegmentIndex]
                
                // Update state with next segment
                _state.value = _state.value.copy(
                    currentSegmentIndex = nextSegmentIndex,
                    currentSegment = nextSegment,
                    remainingTimeSeconds = nextSegment.durationSeconds,
                    elapsedTimeSeconds = totalElapsed
                )
                
                // Emit segment started event
                emitSegmentStarted(nextSegment)
            } else {
                // All segments completed, finish workout
                completeWorkout()
            }
        } else {
            // Update remaining time for current segment
            _state.value = _state.value.copy(
                remainingTimeSeconds = remainingSeconds,
                elapsedTimeSeconds = totalElapsed
            )
            
            // Emit time updated event
            _events.value = WorkoutEvent.TimeUpdated(remainingSeconds)
        }
    }
    
    /**
     * Emit a segment started event and vibrate based on activity type
     */
    private fun emitSegmentStarted(segment: WorkoutSegment) {
        // Emit segment started event with required parameters
        _events.value = WorkoutEvent.SegmentStarted(
            segment = segment,
            remainingTimeSeconds = segment.durationSeconds,
            totalSegments = segments.size
        )
        
        // Vibrate based on activity type
        when (segment.activityType) {
            ActivityType.WARMUP, ActivityType.COOLDOWN -> {
                // Single vibration for warm-up or cool-down
                val vibrationEffect = VibrationEffect.createOneShot(
                    VIBRATION_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
            }
            ActivityType.RUN -> {
                // Double vibration for run
                val timings = longArrayOf(0, VIBRATION_DURATION_MS, 300, VIBRATION_DURATION_MS)
                val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                val vibrationEffect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(vibrationEffect)
            }
            ActivityType.WALK -> {
                // Soft single vibration for walk
                val vibrationEffect = VibrationEffect.createOneShot(
                    VIBRATION_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE / 2
                )
                vibrator.vibrate(vibrationEffect)
            }
        }
    }
    
    /**
     * Emit a segment completed event
     */
    private fun emitSegmentCompleted(segment: WorkoutSegment) {
        _events.value = WorkoutEvent.SegmentCompleted(segment)
    }
    
    /**
     * Complete the entire workout
     */
    private fun completeWorkout() {
        stopTimer()
        
        // Create completed state
        _state.value = _state.value.copy(
            isActive = false,
            isPaused = false,
            remainingTimeSeconds = 0
        )
        
        // Emit completion event
        _events.value = WorkoutEvent.WorkoutFinished
        
        // If segments were completed, also emit success event
        if (segmentsCompleted) {
            _events.value = WorkoutEvent.WorkoutCompletedWithSuccess(
                week = _state.value.currentWeek,
                day = _state.value.currentDay
            )
        }
        
        // Long vibration to indicate workout completed
        val vibrationEffect = VibrationEffect.createOneShot(
            1000, // 1 second vibration
            VibrationEffect.DEFAULT_AMPLITUDE
        )
        vibrator.vibrate(vibrationEffect)
    }

    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
} 