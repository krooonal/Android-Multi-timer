package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.PresetTimerRepository
import com.example.ui.TimerDashboard
import com.example.ui.TimerViewModel
import com.example.ui.TimerViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize our shared active timer state orchestrator
    com.example.data.ActiveTimerManager.init(applicationContext)

    // Request notification permissions for Android 13+ (Oreo, Tiramisu +)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
    }

    val database = AppDatabase.getDatabase(applicationContext)
    val repository = PresetTimerRepository(database.presetTimerDao)
    val viewModel: TimerViewModel by viewModels {
      TimerViewModelFactory(repository)
    }

    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          TimerDashboard(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}
