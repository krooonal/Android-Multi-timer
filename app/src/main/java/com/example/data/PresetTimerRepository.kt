package com.example.data

import kotlinx.coroutines.flow.Flow

class PresetTimerRepository(private val dao: PresetTimerDao) {
    val allPresets: Flow<List<PresetTimer>> = dao.getAllPresets()

    suspend fun insertPreset(preset: PresetTimer) {
        dao.insertPreset(preset)
    }

    suspend fun deletePreset(preset: PresetTimer) {
        dao.deletePreset(preset)
    }
}
