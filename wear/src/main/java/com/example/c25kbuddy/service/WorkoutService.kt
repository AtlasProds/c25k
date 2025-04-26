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

@AndroidEntryPoint
class WorkoutService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "workout_channel"
        
        const val ACTION_START_WORKOUT = "com.example.c25kbuddy.START_WORKOUT"
        const val ACTION_STOP_WORKOUT = "com.example.c25kbuddy.STOP_WORKOUT"
        const val ACTION_PAUSE_WORKOUT = "com.example.c25kbuddy.PAUSE_WORKOUT"
        const val ACTION_RESUME_WORKOUT = "com.example.c25kbuddy.RESUME_WORKOUT"
        
        const val EXTRA_WEEK = "week"
        const val EXTRA_DAY = "day"
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
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
        android.util.Log.d("WorkoutService", "onCreate")
        
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
        
        // Create wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "C25KBuddy:WorkoutWakeLock"
        )
        
        // Create notification channel
        createNotificationChannel()
        
        // Collect workout events to update notification
        serviceScope.launch {
            workoutEngine.events.collect { event ->
                event?.let {
                    android.util.Log.d("WorkoutService", "Event received: ${it::class.java.simpleName}")
                    when (it) {
                        is WorkoutEvent.SegmentStarted -> updateNotification()
                        is WorkoutEvent.WorkoutFinished -> stopSelf()
                        else -> {} // Ignore other events
                    }
                }
            }
        }
        
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
                    startWorkout(week, day)
                } catch (e: Exception) {
                    android.util.Log.e("WorkoutService", "Error starting workout", e)
                    stopSelf()
                }
            }
            ACTION_STOP_WORKOUT -> {
                android.util.Log.d("WorkoutService", "Stopping workout")
                workoutEngine.stopWorkout()
                stopSelf()
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
                android.util.Log.w("WorkoutService", "Service started with null intent")
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Release wake lock
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        // Cancel all coroutines
        serviceScope.cancel()
    }
    
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
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
        
        // To ensure state is properly synchronized,
        // we add a slight delay before starting the workout
        serviceScope.launch {
            try {
                // First, ensure we have a notification showing
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
                
                // Give the service a moment to fully initialize
                delay(500)
                
                workoutEngine.startWorkout(week, day)
                
                // Check if the workout actually started successfully by examining the state
                if (workoutEngine.state.value.isActive) {
                    android.util.Log.d("WorkoutService", "Workout started successfully: Week $week Day $day")
                } else {
                    android.util.Log.e("WorkoutService", "Workout failed to start for Week $week Day $day")
                    stopSelf() // Stop the service since we couldn't start the workout
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutService", "Error starting workout in engine", e)
                stopSelf() // Stop the service since there was an error
            }
        }
    }
    
    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            // 3 hours max - typical workout shouldn't exceed this
            wakeLock.acquire(3 * 60 * 60 * 1000L)
            android.util.Log.d("WorkoutService", "Wake lock acquired")
        }
    }
} 