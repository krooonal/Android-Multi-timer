package com.example.data

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.ui.ActiveTimer
import com.example.ui.TimerStatus
import com.example.ui.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ActiveTimerManager {
    private val _activeTimers = MutableStateFlow<List<ActiveTimer>>(emptyList())
    val activeTimers = _activeTimers.asStateFlow()

    private val managerScope = CoroutineScope(Dispatchers.Default)
    private var tickerJob: Job? = null
    private var appContext: Context? = null

    // Map to keep track of active ringtones per timer ID so they can be stopped on dismiss
    private val activeRingtones = mutableMapOf<String, Ringtone>()

    fun init(context: Context) {
        appContext = context.applicationContext
        startTicker()
    }

    private fun startTicker() {
        if (tickerJob != null) return
        tickerJob = managerScope.launch {
            while (true) {
                delay(500) // update twice per second
                val now = System.currentTimeMillis()
                var updated = false
                _activeTimers.update { currentList ->
                    currentList.map { timer ->
                        if (timer.status == TimerStatus.RUNNING) {
                            val remaining = ((timer.targetEndTimeMillis - now + 500) / 1000).toInt()
                            if (remaining <= 0) {
                                updated = true
                                playAlarmSound(timer.id)
                                timer.copy(remainingSeconds = 0, status = TimerStatus.FINISHED)
                            } else {
                                timer.copy(remainingSeconds = remaining)
                            }
                        } else {
                            timer
                        }
                    }
                }
                if (updated || _activeTimers.value.any { it.status == TimerStatus.RUNNING }) {
                    appContext?.let { ctx ->
                        TimerService.updateService(ctx)
                    }
                }
            }
        }
    }

    private fun playAlarmSound(timerId: String) {
        val currentContext = appContext ?: return
        if (activeRingtones.containsKey(timerId)) return

        try {
            val alert: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(currentContext, alert)
            if (ringtone != null) {
                ringtone.play()
                activeRingtones[timerId] = ringtone

                // Auto stop after 60 seconds of ringing to prevent excessive noise if not dismissed
                Handler(Looper.getMainLooper()).postDelayed({
                    stopAlarmSound(timerId)
                }, 60000L)
            }
        } catch (e: Exception) {
            Log.e("ActiveTimerManager", "Error playing alarm sound", e)
        }
    }

    fun stopAlarmSound(timerId: String) {
        activeRingtones.remove(timerId)?.let { ringtone ->
            try {
                if (ringtone.isPlaying) {
                    ringtone.stop()
                }
            } catch (e: Exception) {
                Log.e("ActiveTimerManager", "Error stopping alarm sound", e)
            }
        }
    }

    fun startCustomTimer(label: String, hours: Int, minutes: Int, seconds: Int) {
        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        if (totalSeconds <= 0) return

        val displayLabel = label.ifBlank {
            val hStr = if (hours > 0) "${hours}m " else ""
            val mStr = if (minutes > 0) "${minutes}m " else ""
            val sStr = if (seconds > 0) "${seconds}s" else ""
            if (hStr.isEmpty() && mStr.isEmpty() && sStr.isEmpty()) "Timer" else (hStr + mStr + sStr).trim()
        }

        val newTimer = ActiveTimer(
            label = displayLabel,
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds,
            status = TimerStatus.RUNNING,
            targetEndTimeMillis = System.currentTimeMillis() + totalSeconds * 1000L
        )

        _activeTimers.update { listOf(newTimer) + it }
        appContext?.let { TimerService.startService(it) }
    }

    fun startTimerFromPreset(preset: PresetTimer) {
        val newTimer = ActiveTimer(
            label = preset.label,
            totalSeconds = preset.durationSeconds,
            remainingSeconds = preset.durationSeconds,
            status = TimerStatus.RUNNING,
            targetEndTimeMillis = System.currentTimeMillis() + preset.durationSeconds * 1000L
        )
        _activeTimers.update { listOf(newTimer) + it }
        appContext?.let { TimerService.startService(it) }
    }

    fun pauseTimer(id: String) {
        _activeTimers.update { currentList ->
            currentList.map { timer ->
                if (timer.id == id && timer.status == TimerStatus.RUNNING) {
                    timer.copy(
                        status = TimerStatus.PAUSED,
                        remainingSeconds = timer.remainingSeconds
                    )
                } else {
                    timer
                }
            }
        }
        appContext?.let { TimerService.updateService(it) }
    }

    fun resumeTimer(id: String) {
        _activeTimers.update { currentList ->
            currentList.map { timer ->
                if (timer.id == id && timer.status == TimerStatus.PAUSED) {
                    timer.copy(
                        status = TimerStatus.RUNNING,
                        targetEndTimeMillis = System.currentTimeMillis() + timer.remainingSeconds * 1000L
                    )
                } else {
                    timer
                }
            }
        }
        appContext?.let { TimerService.startService(it) }
    }

    fun resetTimer(id: String) {
        stopAlarmSound(id)
        _activeTimers.update { currentList ->
            currentList.map { timer ->
                if (timer.id == id) {
                    timer.copy(
                        status = TimerStatus.PAUSED,
                        remainingSeconds = timer.totalSeconds,
                        targetEndTimeMillis = System.currentTimeMillis() + timer.totalSeconds * 1000L
                    )
                } else {
                    timer
                }
            }
        }
        appContext?.let { TimerService.updateService(it) }
    }

    fun removeTimer(id: String) {
        stopAlarmSound(id)
        _activeTimers.update { currentList ->
            currentList.filter { it.id != id }
        }
        appContext?.let { ctx ->
            if (_activeTimers.value.isEmpty()) {
                TimerService.stopService(ctx)
            } else {
                TimerService.updateService(ctx)
            }
        }
    }

    fun addMinutes(id: String, minutes: Int) {
        stopAlarmSound(id)
        _activeTimers.update { currentList ->
            currentList.map { timer ->
                if (timer.id == id) {
                    val additionalSeconds = minutes * 60
                    val newRemaining = timer.remainingSeconds + additionalSeconds
                    val newTotal = timer.totalSeconds + additionalSeconds
                    if (timer.status == TimerStatus.RUNNING) {
                        timer.copy(
                            totalSeconds = newTotal,
                            remainingSeconds = newRemaining,
                            targetEndTimeMillis = timer.targetEndTimeMillis + additionalSeconds * 1000L
                        )
                    } else if (timer.status == TimerStatus.FINISHED) {
                        timer.copy(
                            totalSeconds = additionalSeconds,
                            remainingSeconds = additionalSeconds,
                            status = TimerStatus.RUNNING,
                            targetEndTimeMillis = System.currentTimeMillis() + additionalSeconds * 1000L
                        )
                    } else {
                        timer.copy(
                            totalSeconds = newTotal,
                            remainingSeconds = newRemaining
                        )
                    }
                } else {
                    timer
                }
            }
        }
        appContext?.let { TimerService.startService(it) }
    }
}
