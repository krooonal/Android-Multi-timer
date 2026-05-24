package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset_timers")
data class PresetTimer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val durationSeconds: Int,
    val createdAt: Long = System.currentTimeMillis()
)
