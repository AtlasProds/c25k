package com.example.c25kbuddy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.example.c25kbuddy.R
import com.example.c25kbuddy.data.model.ActivityType
import com.example.c25kbuddy.data.model.WorkoutEvent
import com.example.c25kbuddy.data.model.WorkoutState
import com.example.c25kbuddy.data.repository.C25KRepository
import com.example.c25kbuddy.domain.WorkoutEngine
import com.example.c25kbuddy.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

@AndroidEntryPoint
class WorkoutService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "workout_channel"
        private const val WAKE_LOCK_VERIFICATION_INTERVAL_MS = 30000L  // Check wake lock every 30 seconds
        
        const val ACTION_START_WORKOUT = "com.example.c25kbuddy.START_WORKOUT"
        const val ACTION_STOP_WORKOUT = "com.example.c25kbuddy.STOP_WORKOUT"
        const val ACTION_PAUSE_WORKOUT = "com.example.c25kbuddy.PAUSE_WORKOUT"
        const val ACTION_RESUME_WORKOUT = "com.example.c25kbuddy.RESUME_WORKOUT"
        
        const val EXTRA_WEEK = "week"
        const val EXTRA_DAY = "day"
    }
    
    // Use var for serviceScope so it can be reassigned
    private var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLockVerificationJob: Job? = null
    
    // Initialize repository in onCreate, but declare it as lateinit
    private lateinit var repository: C25KRepository
    private lateinit var workoutEngine: WorkoutEngine
    private lateinit var wakeLock: PowerManager.WakeLock
    
    // Current state
    private var currentWeek: Int = 1
    private var currentDay: Int = 1
    
    // Binder for activity connection
    private val binder = WorkoutBinder()
    
    inner class WorkoutBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
        fun getWorkoutEngine(): WorkoutEngine = workoutEngine
    }
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("WorkoutService", "onCreate - initializing service")
        
        // Initialize repository and workout engine
        repository = C25KRepository(applicationContext)
        
        // Get vibrator service
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        workoutEngine = WorkoutEngine(repository, vibrator, serviceScope)
        android.util.Log.d("WorkoutService", "WorkoutEngine initialized")
        
        // Create wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "C25KBuddy:WorkoutWakeLock"
        )
        
        // Create notification channel
        createNotificationChannel()
        
        // Set up state and event collectors
        setupStateCollectors()
        
        android.util.Log.d("WorkoutService", "Service initialization complete")
    }
    
    // Add flag to track deliberate service shutdown
    private var isShuttingDown = false
    
    // Instead of overriding stopSelf, create helper methods
    private fun markShutdownAndStop() {
        isShuttingDown = true
        android.util.Log.d("WorkoutService", "Marked as intentional shutdown before stopSelf()")
        super.stopSelf()
    }
    
    private fun markShutdownAndStop(startId: Int) {
        isShuttingDown = true
        android.util.Log.d("WorkoutService", "Marked as intentional shutdown before stopSelf(startId)")
        super.stopSelf(startId)
    }
    
    // Update references to stopSelf() to use the new helper method
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("WorkoutService", "onStartCommand: ${intent?.action}")
        
        // Create the notification channel if not already created
        createNotificationChannel()
        
        // Handle the intent action
        when (intent?.action) {
            ACTION_START_WORKOUT -> {
                try {
                    val week = intent.getIntExtra(EXTRA_WEEK, 1)
                    val day = intent.getIntExtra(EXTRA_DAY, 1)
                    
                    android.util.Log.d("WorkoutService", "Starting workout for Week $week Day $day")
                    
                    // If already active, stop current workout first
                    if (workoutEngine.state.value.isActive) {
                        android.util.Log.d("WorkoutService", "Stopping existing workout before starting new one")
                        workoutEngine.stopWorkout()
                    }
                    
                    // Create and show notification immediately to avoid ANR
                    val notification = createNotification()
                    startForeground(NOTIFICATION_ID, notification)
                    
                    // Acquire wake lock to keep the device from sleeping during workout
                    acquireWakeLock()
                    
                    // Load workout data for the specified week and day
                    // Use the service scope to ensure clean startup
                    serviceScope.launch {
                        try {
                            android.util.Log.d("WorkoutService", "Starting workout in service scope")
                            startWorkout(week, day)
                        } catch (e: Exception) {
                            android.util.Log.e("WorkoutService", "Error in workout start coroutine", e)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WorkoutService", "Error starting workout", e)
                    markShutdownAndStop()
                }
            }
            ACTION_STOP_WORKOUT -> {
                android.util.Log.d("WorkoutService", "Stopping workout from explicit stop command")
                
                try {
                    // Stop the workout engine first
                    workoutEngine.stopWorkout()
                    
                    // Release any resources
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                        android.util.Log.d("WorkoutService", "Wake lock released")
                    }
                    
                    // Cancel all coroutines
                    serviceScope.cancel()
                    
                    // Create a new scope for any cleanup operations
                    serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                    android.util.Log.d("WorkoutService", "Service scope cancelled and recreated for future use")
                    
                    // Remove the foreground notification
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    android.util.Log.d("WorkoutService", "Foreground service stopped")
                    
                    // Stop the service completely
                    markShutdownAndStop()
                    android.util.Log.d("WorkoutService", "Service stopping via markShutdownAndStop()")
                } catch (e: Exception) {
                    android.util.Log.e("WorkoutService", "Error stopping workout", e)
                    // Ensure service is still stopped even if there's an error
                    markShutdownAndStop()
                }
            }
            ACTION_PAUSE_WORKOUT -> {
                android.util.Log.d("WorkoutService", "Pausing workout")
                workoutEngine.pauseWorkout()
                updateNotification()
            }
            ACTION_RESUME_WORKOUT -> {
                android.util.Log.d("WorkoutService", "Resuming workout")
                workoutEngine.resumeWorkout()
                updateNotification()
            }
            null -> {
                android.util.Log.w("WorkoutService", "Service started with null intent, ensuring foreground status")
                // Show a notification even with null intent to keep service in foreground
                startForeground(NOTIFICATION_ID, createNotification())
            }
        }
        
        // Return START_STICKY to ensure service is restarted if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        android.util.Log.d("WorkoutService", "onDestroy called - shutting down completely")
        
        val wasActive = workoutEngine.state.value.isActive
        
        try {
            // Stop any active workout
            if (workoutEngine.state.value.isActive) {
                android.util.Log.d("WorkoutService", "Stopping active workout in onDestroy")
                workoutEngine.stopWorkout()
            }
            
            // Cancel wake lock verification
            wakeLockVerificationJob?.cancel()
            wakeLockVerificationJob = null
            
            // Release wake lock
            if (wakeLock.isHeld) {
                wakeLock.release()
                android.util.Log.d("WorkoutService", "Wake lock released in onDestroy")
            }
            
            // Remove notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            
            // Cancel all coroutines
            serviceScope.cancel()
            android.util.Log.d("WorkoutService", "Service scope cancelled in onDestroy")
            
            // If the workout was active, we might want to restart the service
            // This can happen if the system killed our service but the workout should continue
            if (wasActive) {
                android.util.Log.d("WorkoutService", "Workout was active during service destruction - this may be unexpected")
                
                // Consider restarting the service if this was an unexpected shutdown
                // Don't restart if we're in the process of explicit shutdown (e.g., stopSelf was called)
                if (!isShuttingDown) {
                    android.util.Log.d("WorkoutService", "Attempting to restart service after unexpected shutdown")
                    val intent = Intent(this, WorkoutService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutService", "Error in onDestroy", e)
        } finally {
            super.onDestroy()
            android.util.Log.d("WorkoutService", "Service destroyed")
        }
    }
    
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val state = workoutEngine.state.value
        val title = "Week ${currentWeek} Day ${currentDay}"
        val content = buildNotificationContent(state)
        
        // Create notification builder
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        
        // Add pause/resume action if workout is active
        if (state.isActive) {
            val actionIntent = Intent(this, WorkoutService::class.java).apply {
                action = if (state.isPaused) ACTION_RESUME_WORKOUT else ACTION_PAUSE_WORKOUT
            }
            
            val actionPendingIntent = PendingIntent.getService(
                this, 1, actionIntent, PendingIntent.FLAG_IMMUTABLE
            )
            
            builder.addAction(
                0,
                if (state.isPaused) "Resume" else "Pause",
                actionPendingIntent
            )
        }
        
        return builder.build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Workout"
            val descriptionText = "C25K Workout progress"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotificationContent(state: WorkoutState): String {
        if (!state.isActive) {
            return "Workout not active"
        }
        
        val segment = state.currentSegment ?: return "Starting workout..."
        val activity = segment.activityType.getDisplayName()
        val timeRemaining = formatTime(state.remainingTimeSeconds)
        
        return if (state.isPaused) {
            "Paused - $activity ($timeRemaining)"
        } else {
            "$activity - $timeRemaining remaining"
        }
    }
    
    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return "%02d:%02d".format(minutes, remainingSeconds)
    }
    
    private fun startWorkout(week: Int, day: Int) {
        currentWeek = week
        currentDay = day
        
        android.util.Log.d("WorkoutService", "Starting workout with week=$week, day=$day in serviceScope=${serviceScope.hashCode()}")
        
        // Show notification immediately to avoid ANR
        val initialNotification = createInitialNotification(week, day)
        startForeground(NOTIFICATION_ID, initialNotification)
        android.util.Log.d("WorkoutService", "Foreground service started with initial notification")
        
        // Start wake lock verification
        startWakeLockVerification()
        
        serviceScope.launch {
            try {
                // Give the service a moment to fully initialize
                delay(100)
                
                // Start workout and capture result
                android.util.Log.d("WorkoutService", "Calling workoutEngine.startWorkout()")
                workoutEngine.startWorkout(week, day)
                
                // Check if the workout actually started
                delay(100) // Brief delay to allow state to update
                
                if (workoutEngine.state.value.isActive) {
                    android.util.Log.d("WorkoutService", "Workout started successfully: Week $week Day $day, active=${workoutEngine.state.value.isActive}")
                    // Update notification with current state
                    updateNotification()
                } else {
                    android.util.Log.e("WorkoutService", "Workout failed to start for Week $week Day $day, state=${workoutEngine.state.value}")
                    // Try one more time
                    android.util.Log.d("WorkoutService", "Attempting to start workout again")
                    delay(500)
                    workoutEngine.startWorkout(week, day)
                    
                    delay(100)
                    if (workoutEngine.state.value.isActive) {
                        android.util.Log.d("WorkoutService", "Second attempt: Workout started successfully")
                        updateNotification()
                    } else {
                        android.util.Log.e("WorkoutService", "Second attempt: Workout still failed to start")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutService", "Error in startWorkout", e)
            }
        }
    }
    
    private fun createInitialNotification(week: Int, day: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, 
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Week $week Day $day")
            .setContentText("Starting workout...")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }
    
    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            // 3 hours max - typical workout shouldn't exceed this
            wakeLock.acquire(3 * 60 * 60 * 1000L)
            android.util.Log.d("WorkoutService", "Wake lock acquired")
        }
    }
    
    // Move collectors to a separate method so we can re-establish them after resetting
    private fun setupStateCollectors() {
        // Collect workout events to update notification
        collectWorkoutEvents()
        
        // Collect state changes to update notification
        serviceScope.launch {
            workoutEngine.state.collect { state ->
                android.util.Log.d("WorkoutService", "State update: active=${state.isActive}, segment=${state.currentSegmentIndex}, time=${state.remainingTimeSeconds}")
                if (state.isActive) {
                    updateNotification()
                }
            }
        }
    }
    
    private fun collectWorkoutEvents() {
        serviceScope.launch {
            workoutEngine.events.collect { event ->
                android.util.Log.d("WorkoutService", "Received workout event: $event")
                
                when (event) {
                    is WorkoutEvent.SegmentStarted -> updateNotification()
                    is WorkoutEvent.WorkoutFinished -> markShutdownAndStop()
                    is WorkoutEvent.WorkoutStarted -> {
                        android.util.Log.d("WorkoutService", "Workout started event received, updating notification")
                        updateNotification()
                    }
                    is WorkoutEvent.WorkoutPaused, 
                    is WorkoutEvent.WorkoutResumed,
                    is WorkoutEvent.SegmentCompleted,
                    is WorkoutEvent.CountdownFinished -> {
                        updateNotification()
                    }
                    else -> {
                        // No action needed for other events
                    }
                }
            }
        }
    }

    /**
     * Stop the current workout without stopping the service
     * Called by the WorkoutViewModel to stop the workout
     */
    fun stopWorkout() {
        android.util.Log.d("WorkoutService", "Stopping workout from ViewModel call")
        
        try {
            // Stop the workout engine first
            workoutEngine.stopWorkout()
            
            // Release wake lock if held
            if (wakeLock.isHeld) {
                wakeLock.release()
                android.util.Log.d("WorkoutService", "Wake lock released")
            }
            
            // Stop foreground service but leave notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            
            android.util.Log.d("WorkoutService", "Workout stopped but service remains active")
        } catch (e: Exception) {
            android.util.Log.e("WorkoutService", "Error stopping workout", e)
        }
    }
    
    private fun startWakeLockVerification() {
        wakeLockVerificationJob?.cancel()
        wakeLockVerificationJob = serviceScope.launch {
            while (workoutEngine.state.value.isActive) {
                ensureWakeLock()
                delay(WAKE_LOCK_VERIFICATION_INTERVAL_MS)
            }
        }
    }
    
    private fun ensureWakeLock() {
        if (!wakeLock.isHeld) {
            android.util.Log.w("WorkoutService", "Wake lock was released, re-acquiring")
            acquireWakeLock()
        }
    }
} 