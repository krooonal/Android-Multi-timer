package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetTimerDao {
    @Query("SELECT * FROM preset_timers ORDER BY createdAt DESC")
    fun getAllPresets(): Flow<List<PresetTimer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetTimer)

    @Delete
    suspend fun deletePreset(preset: PresetTimer)
}
