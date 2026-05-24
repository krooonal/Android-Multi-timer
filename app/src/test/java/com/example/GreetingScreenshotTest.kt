package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.PresetTimerRepository
import com.example.ui.TimerDashboard
import com.example.ui.TimerViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val repository = PresetTimerRepository(database.presetTimerDao)
    val viewModel = TimerViewModel(repository)

    // Pre-populate simulated timers for high-visibility visual screenshots
    viewModel.startCustomTimer("Pasta Countdown", 0, 10, 0, false)
    viewModel.startCustomTimer("Tea Steeping", 0, 3, 0, false)
    
    // Pause the second timer to display the beautiful amber indicator alongside the cyan active one
    val activeList = viewModel.activeTimers.value
    if (activeList.size >= 2) {
      viewModel.pauseTimer(activeList[1].id)
    }

    composeTestRule.setContent {
      MyApplicationTheme {
        TimerDashboard(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    
    // Clean up
    database.close()
  }
}
