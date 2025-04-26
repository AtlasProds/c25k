package com.example.c25kbuddy.presentation.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.c25kbuddy.data.model.C25KProgram
import com.example.c25kbuddy.data.model.UserProgress
import com.example.c25kbuddy.data.model.Week
import com.example.c25kbuddy.data.model.WorkoutEvent
import com.example.c25kbuddy.data.model.WorkoutState
import com.example.c25kbuddy.data.repository.C25KRepository
import com.example.c25kbuddy.domain.WorkoutEngine
import com.example.c25kbuddy.service.WorkoutService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WorkoutViewModel(
    application: Application,
    private val repository: C25KRepository
) : AndroidViewModel(application) {
    
    // Program data
    private val _program = MutableStateFlow<C25KProgram?>(null)
    val program = _program.asStateFlow()
    
    // User progress
    val userProgress = repository.userProgress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserProgress()
    )
    
    // Service connection
    private var bound = false
    private var service: WorkoutService? = null
    private var workoutEngine: WorkoutEngine? = null
    
    // Workout state
    private val _workoutState = MutableStateFlow(WorkoutState())
    val workoutState = _workoutState.asStateFlow()
    
    // UI state
    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState = _uiState.asStateFlow()
    
    // Last workout event
    private val _lastEvent = MutableStateFlow<WorkoutEvent?>(null)
    val lastEvent = _lastEvent.asStateFlow()
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            val workoutBinder = binder as WorkoutService.WorkoutBinder
            service = workoutBinder.getService()
            workoutEngine = workoutBinder.getWorkoutEngine()
            bound = true
            
            // Start collecting state from the service
            collectServiceState()
        }
        
        override fun onServiceDisconnected(className: ComponentName) {
            service = null
            workoutEngine = null
            bound = false
        }
    }
    
    init {
        // Load program data
        loadProgram()
        
        // Bind to service if it's running
        bindToService()
        
        // Update UI state based on workout state and user progress
        viewModelScope.launch {
            combine(
                workoutState,
                userProgress
            ) { state, progress ->
                WorkoutUiState(
                    isWorkoutActive = state.isActive,
                    isWorkoutPaused = state.isPaused,
                    currentWeek = state.currentWeek,
                    currentDay = state.currentDay,
                    lastCompletedWeek = progress.lastCompletedWeek,
                    lastCompletedDay = progress.lastCompletedDay
                )
            }.collect { _uiState.value = it }
        }
    }
    
    /**
     * Bind to the workout service
     */
    private fun bindToService() {
        val context = getApplication<Application>()
        val intent = Intent(context, WorkoutService::class.java)
        
        try {
            val bindResult = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            android.util.Log.d("WorkoutViewModel", "Service bind result: $bindResult")
            
            // If binding failed, try again after a short delay
            if (!bindResult) {
                android.util.Log.e("WorkoutViewModel", "Failed to bind to service, retrying...")
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1000)
                    try {
                        val retryResult = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                        android.util.Log.d("WorkoutViewModel", "Service bind retry result: $retryResult")
                    } catch (e: Exception) {
                        android.util.Log.e("WorkoutViewModel", "Error during service bind retry", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutViewModel", "Error binding to service", e)
        }
    }
    
    /**
     * Check if the workout service is running
     */
    private fun isServiceRunning(): Boolean {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (WorkoutService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * Collect state and events from the service
     */
    private fun collectServiceState() {
        val engine = workoutEngine ?: return
        android.util.Log.d("WorkoutViewModel", "Starting to collect service state")
        
        viewModelScope.launch {
            engine.state.collect { state ->
                android.util.Log.d("WorkoutViewModel", "Received state update: remaining=${state.remainingTimeSeconds}, active=${state.isActive}")
                _workoutState.value = state
                
                // Update UI state immediately when workout active state changes
                _uiState.update { it.copy(
                    isWorkoutActive = state.isActive,
                    isWorkoutPaused = state.isPaused,
                    currentWeek = state.currentWeek,
                    currentDay = state.currentDay
                )}
            }
        }
        
        viewModelScope.launch {
            engine.events.collect { event ->
                if (event != null) {
                    android.util.Log.d("WorkoutViewModel", "Received event: ${event::class.simpleName}")
                    _lastEvent.value = event
                    
                    // Handle specific events
                    when (event) {
                        is WorkoutEvent.WorkoutError -> {
                            // Update the UI state with the error message
                            _uiState.update { it.copy(
                                errorMessage = event.errorMessage,
                                permissionError = true
                            )}
                            android.util.Log.e("WorkoutViewModel", "Workout error: ${event.errorMessage}")
                        }
                        
                        is WorkoutEvent.WorkoutFinished -> {
                            // Ensure the UI state reflects that the workout is no longer active
                            _uiState.update { it.copy(
                                isWorkoutActive = false,
                                isWorkoutPaused = false
                            )}
                        }
                        
                        else -> {
                            // No additional handling for other event types
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Load the C25K program
     */
    private fun loadProgram() {
        viewModelScope.launch {
            try {
                val program = repository.loadProgram()
                _program.value = program
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    /**
     * Start a workout for the specified week and day
     */
    fun startWorkout(week: Int, day: Int) {
        val context = getApplication<Application>()
        
        // Clear any previous error state
        _uiState.update { it.copy(
            permissionError = false,
            errorMessage = null
        ) }
        
        // Check if the workout is available to be started
        if (!userProgress.value.isWorkoutAvailable(week, day)) {
            android.util.Log.d("WorkoutViewModel", "Workout not available: Week $week Day $day")
            _uiState.update { it.copy(
                permissionError = true,
                errorMessage = "You need to complete previous workouts first"
            ) }
            return
        }
        
        // Check if we have the required permissions
        if (hasRequiredPermissions(context)) {
            val intent = Intent(context, WorkoutService::class.java).apply {
                action = WorkoutService.ACTION_START_WORKOUT
                putExtra(WorkoutService.EXTRA_WEEK, week)
                putExtra(WorkoutService.EXTRA_DAY, day)
            }
            
            try {
                android.util.Log.d("WorkoutViewModel", "Starting workout service for Week $week Day $day")
                
                // On Android 12+ we should use startForegroundService
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                // Make sure we're bound to the service
                if (!bound) {
                    bindToService()
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error starting workout service", e)
                _uiState.update { it.copy(
                    permissionError = true,
                    errorMessage = "Error starting workout: ${e.message}"
                ) }
            }
        } else {
            // Permissions are missing
            android.util.Log.e("WorkoutViewModel", "Missing required permissions for health tracking")
            _uiState.update { it.copy(
                permissionError = true,
                errorMessage = "Missing required permissions for health tracking"
            ) }
        }
    }
    
    /**
     * Check if we have all required permissions
     */
    private fun hasRequiredPermissions(context: Context): Boolean {
        val activityRecognition = android.Manifest.permission.ACTIVITY_RECOGNITION
        val highSamplingRate = android.Manifest.permission.HIGH_SAMPLING_RATE_SENSORS
        val foregroundServiceHealth = android.Manifest.permission.FOREGROUND_SERVICE_HEALTH
        val foregroundService = android.Manifest.permission.FOREGROUND_SERVICE
        val postNotifications = android.Manifest.permission.POST_NOTIFICATIONS
        
        // Check if we have FOREGROUND_SERVICE_HEALTH permission
        val hasForegroundServiceHealth = android.content.pm.PackageManager.PERMISSION_GRANTED == 
            context.checkSelfPermission(foregroundServiceHealth)
            
        // Check if we have at least one of the other required permissions
        val hasActivityRecognition = android.content.pm.PackageManager.PERMISSION_GRANTED == 
            context.checkSelfPermission(activityRecognition)
        
        val hasHighSamplingRate = android.content.pm.PackageManager.PERMISSION_GRANTED == 
            context.checkSelfPermission(highSamplingRate)
        
        val hasForegroundService = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(foregroundService)
            
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.content.pm.PackageManager.PERMISSION_GRANTED == context.checkSelfPermission(postNotifications)
        } else {
            true // Permission not required before Android 13
        }
            
        return hasForegroundServiceHealth && 
               (hasActivityRecognition || hasHighSamplingRate) && 
               hasForegroundService && 
               hasNotificationPermission
    }
    
    /**
     * Start the next workout based on user progress
     */
    fun startNextWorkout() {
        val progress = userProgress.value
        var (nextWeek, nextDay) = progress.getNextWorkout()
        
        // Fix invalid week value - ensure week is at least 1
        if (nextWeek < 1) {
            nextWeek = 1
            android.util.Log.d("WorkoutViewModel", "Corrected invalid week value to Week $nextWeek Day $nextDay")
        }
        
        android.util.Log.d("WorkoutViewModel", "Starting next workout: Week $nextWeek Day $nextDay")
        
        // Clear any previous error state
        _uiState.update { it.copy(
            permissionError = false,
            errorMessage = null
        ) }
        
        // Check if the workout is available to be started
        if (!userProgress.value.isWorkoutAvailable(nextWeek, nextDay)) {
            android.util.Log.d("WorkoutViewModel", "Next workout not available: Week $nextWeek Day $nextDay")
            _uiState.update { it.copy(
                permissionError = true,
                errorMessage = "You need to complete previous workouts first"
            ) }
            return
        }
        
        val context = getApplication<Application>()
        
        // Check if we have the required permissions
        if (hasRequiredPermissions(context)) {
            android.util.Log.d("WorkoutViewModel", "All permissions granted, starting workout service")
            
            val intent = Intent(context, WorkoutService::class.java).apply {
                action = WorkoutService.ACTION_START_WORKOUT
                putExtra(WorkoutService.EXTRA_WEEK, nextWeek)
                putExtra(WorkoutService.EXTRA_DAY, nextDay)
            }
            
            try {
                android.util.Log.d("WorkoutViewModel", "Starting workout service for Week $nextWeek Day $nextDay")
                
                // On Android 12+ we should use startForegroundService
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                
                android.util.Log.d("WorkoutViewModel", "WorkoutService started successfully")
                
                // Make sure we're bound to the service
                if (!bound) {
                    bindToService()
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "Error starting workout service", e)
                _uiState.update { it.copy(
                    permissionError = true,
                    errorMessage = "Error starting workout: ${e.message}"
                ) }
            }
        } else {
            // Permissions are missing
            android.util.Log.e("WorkoutViewModel", "Missing required permissions for health tracking")
            _uiState.update { it.copy(
                permissionError = true,
                errorMessage = "Missing required permissions for health tracking"
            ) }
        }
    }
    
    /**
     * Pause the current workout
     */
    fun pauseWorkout() {
        val context = getApplication<Application>()
        val intent = Intent(context, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_PAUSE_WORKOUT
        }
        context.startService(intent)
    }
    
    /**
     * Resume the paused workout
     */
    fun resumeWorkout() {
        val context = getApplication<Application>()
        val intent = Intent(context, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_RESUME_WORKOUT
        }
        context.startService(intent)
    }
    
    /**
     * Stop the current workout
     */
    fun stopWorkout() {
        val context = getApplication<Application>()
        val intent = Intent(context, WorkoutService::class.java).apply {
            action = WorkoutService.ACTION_STOP_WORKOUT
        }
        context.startService(intent)
    }
    
    /**
     * Skip to the next segment in the workout
     */
    fun skipToNextSegment() {
        workoutEngine?.skipToNextSegment()
    }
    
    /**
     * Mark the current workout as completed
     */
    fun finishWorkout() {
        viewModelScope.launch {
            workoutEngine?.finishWorkout()
        }
    }
    
    /**
     * Format seconds into MM:SS display format
     */
    fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }
    
    /**
     * Check if a workout is completed
     */
    fun isWorkoutCompleted(week: Int, day: Int): Boolean {
        return userProgress.value.isWorkoutCompleted(week, day)
    }
    
    /**
     * Check if a workout is available to be started
     */
    fun isWorkoutAvailable(week: Int, day: Int): Boolean {
        return userProgress.value.isWorkoutAvailable(week, day)
    }
    
    /**
     * Reset all user progress to start from the beginning
     */
    fun resetAllProgress() {
        viewModelScope.launch {
            val progress = UserProgress(
                lastCompletedWeek = 0,
                lastCompletedDay = 0,
                completedWorkouts = mutableListOf()
            )
            
            // Save the reset progress
            repository.saveUserProgress(progress)
            
            android.util.Log.d("WorkoutViewModel", "Reset all workout progress")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Unbind from service
        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
            bound = false
        }
    }
    
    /**
     * Factory for creating WorkoutViewModel
     */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
                val repository = C25KRepository(application)
                return WorkoutViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * Data class representing the current UI state
 */
data class WorkoutUiState(
    val isWorkoutActive: Boolean = false,
    val isWorkoutPaused: Boolean = false,
    val currentWeek: Int = 1,
    val currentDay: Int = 1,
    val lastCompletedWeek: Int = 0,
    val lastCompletedDay: Int = 0,
    val permissionError: Boolean = false,
    val errorMessage: String? = null
) 