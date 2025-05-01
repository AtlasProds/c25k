package com.example.c25kbuddy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.c25kbuddy.data.model.ActivityType
import com.example.c25kbuddy.data.model.C25KProgram
import com.example.c25kbuddy.data.model.Day
import com.example.c25kbuddy.data.model.UserProgress
import com.example.c25kbuddy.data.model.WorkoutSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "c25k_preferences")

private val USER_PROGRESS_KEY = stringPreferencesKey("user_progress")

class C25KRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    
    // Cache for segment references
    private val segmentCache = mutableMapOf<String, List<Int>>()
    
    // Load the C25K program from the JSON asset
    suspend fun loadProgram(): C25KProgram {
        return try {
            val jsonString = context.assets.open("c25k_schedule.json").bufferedReader().use { it.readText() }
            val program = json.decodeFromString<C25KProgram>(jsonString)
            
            // Process "same_as_day_1" references
            processReferences(program)
            
            program
        } catch (e: IOException) {
            throw IllegalStateException("Failed to load C25K program", e)
        }
    }
    
    // Get user progress as a Flow
    val userProgress: Flow<UserProgress> = context.dataStore.data.map { preferences ->
        val progressJson = preferences[USER_PROGRESS_KEY]
        if (progressJson != null) {
            try {
                json.decodeFromString(progressJson)
            } catch (e: Exception) {
                UserProgress()
            }
        } else {
            UserProgress()
        }
    }
    
    // Save user progress
    suspend fun saveUserProgress(progress: UserProgress) {
        context.dataStore.edit { preferences ->
            val progressJson = json.encodeToString(progress)
            preferences[USER_PROGRESS_KEY] = progressJson
        }
    }
    
    // Mark a workout as completed
    suspend fun markWorkoutCompleted(week: Int, day: Int) {
        try {
            android.util.Log.d("WorkoutEngine", "Finished workout for week=$week day=$day")
            context.dataStore.edit { preferences ->
                val progressJson = preferences[USER_PROGRESS_KEY]
                val progress = if (progressJson != null) {
                    try {
                        json.decodeFromString<UserProgress>(progressJson)
                    } catch (e: Exception) {
                        android.util.Log.e("WorkoutEngine", "Failed to decode progress JSON, resetting to default", e)
                        UserProgress()
                    }
                } else {
                    UserProgress()
                }

                // Ensure we have a valid progress object before marking completion
                val updatedProgress = progress.copy().apply {
                    markWorkoutCompleted(week, day)
                }
                
                try {
                    preferences[USER_PROGRESS_KEY] = json.encodeToString(updatedProgress)
                } catch (e: Exception) {
                    android.util.Log.e("C25KRepository", "Failed to save updated progress", e)
                    // Try to save at least the week/day completion
                    preferences[USER_PROGRESS_KEY] = json.encodeToString(UserProgress().apply {
                        markWorkoutCompleted(week, day)
                    })
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "C25KRepository",
                "Error marking workout completed (week=$week, day=$day)",
                e
            )
            // Try one last time with a fresh progress object
            try {
                context.dataStore.edit { preferences ->
                    preferences[USER_PROGRESS_KEY] = json.encodeToString(UserProgress().apply {
                        markWorkoutCompleted(week, day)
                    })
                }
            } catch (e: Exception) {
                android.util.Log.e("C25KRepository", "Failed to save workout completion after all retries", e)
            }
        }
    }

    // Convert day segments to workout segments with activity types
    fun createWorkoutSegments(day: Day): List<WorkoutSegment> {
        // Get the actual segments, either from the day directly or from cache if it's a reference
        val segments = getSegments(day) ?: return emptyList()
        if (segments.isEmpty()) return emptyList()
        
        return segments.mapIndexed { index, durationSeconds ->
            val activityType = when {
                index == 0 -> ActivityType.WARMUP
                index == segments.size - 1 -> ActivityType.COOLDOWN
                index % 2 == 1 -> ActivityType.RUN
                else -> ActivityType.WALK
            }
            
            WorkoutSegment(
                durationSeconds = durationSeconds,
                activityType = activityType,
                position = index
            )
        }
    }
    
    // Get segments for a day, handling references
    private fun getSegments(day: Day): List<Int>? {
        if (day.segments != null) {
            return day.segments
        }
        
        // If this is a reference, look up in cache
        return if (day.segmentsReference == "same_as_day_1") {
            segmentCache["week_${day.position}_day_1"]
        } else {
            null
        }
    }
    
    // Private helper to process "same_as_day_1" references
    private fun processReferences(program: C25KProgram) {
        program.weeks.forEach { week ->
            // Get day 1 segments and cache them by week
            val day1 = week.days.firstOrNull()
            if (day1 != null && day1.segments != null) {
                segmentCache["week_${week.week}_day_1"] = day1.segments
            }
            
            // Set the position for each day
            week.days.forEachIndexed { index, day ->
                day.position = index
            }
        }
    }

    // Clear the segment cache
    fun clearCache() {
        segmentCache.clear()
    }
} 