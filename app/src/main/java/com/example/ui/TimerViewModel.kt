package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ActiveTimerManager
import com.example.data.PresetTimer
import com.example.data.PresetTimerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class TimerStatus {
    RUNNING,
    PAUSED,
    FINISHED
}

data class ActiveTimer(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val status: TimerStatus,
    val targetEndTimeMillis: Long = 0L
)

class TimerViewModel(private val repository: PresetTimerRepository) : ViewModel() {

    val presets: StateFlow<List<PresetTimer>> = repository.allPresets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeTimers: StateFlow<List<ActiveTimer>> = ActiveTimerManager.activeTimers

    fun startCustomTimer(label: String, hours: Int, minutes: Int, seconds: Int, saveAsPreset: Boolean) {
        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        if (totalSeconds <= 0) return

        val displayLabel = label.ifBlank {
            val hStr = if (hours > 0) "${hours}h " else ""
            val mStr = if (minutes > 0) "${minutes}m " else ""
            val sStr = if (seconds > 0) "${seconds}s" else ""
            if (hStr.isEmpty() && mStr.isEmpty() && sStr.isEmpty()) "Timer" else (hStr + mStr + sStr).trim()
        }

        ActiveTimerManager.startCustomTimer(label, hours, minutes, seconds)

        if (saveAsPreset) {
            viewModelScope.launch {
                repository.insertPreset(
                    PresetTimer(
                        label = displayLabel,
                        durationSeconds = totalSeconds
                    )
                )
            }
        }
    }

    fun startTimerFromPreset(preset: PresetTimer) {
        ActiveTimerManager.startTimerFromPreset(preset)
    }

    fun pauseTimer(id: String) {
        ActiveTimerManager.pauseTimer(id)
    }

    fun resumeTimer(id: String) {
        ActiveTimerManager.resumeTimer(id)
    }

    fun resetTimer(id: String) {
        ActiveTimerManager.resetTimer(id)
    }

    fun removeTimer(id: String) {
        ActiveTimerManager.removeTimer(id)
    }

    fun addMinutes(id: String, minutes: Int) {
        ActiveTimerManager.addMinutes(id, minutes)
    }

    fun deletePreset(preset: PresetTimer) {
        viewModelScope.launch {
            repository.deletePreset(preset)
        }
    }

    fun savePreset(label: String, totalSeconds: Int) {
        if (totalSeconds <= 0 || label.isBlank()) return
        viewModelScope.launch {
            repository.insertPreset(PresetTimer(label = label, durationSeconds = totalSeconds))
        }
    }
}

class TimerViewModelFactory(private val repository: PresetTimerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
