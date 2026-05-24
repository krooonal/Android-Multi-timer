package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = GeoBluePrimaryDark,
    onPrimary = GeoBlueTextDark,
    primaryContainer = GeoBlueContainerDark,
    onPrimaryContainer = GeoBlueTextDark,
    secondary = GeoFinishedAlarm,
    onSecondary = Color.White,
    tertiary = GeoBluePrimaryDark,
    onTertiary = GeoBlueTextDark,
    background = GeoBgDark,
    onBackground = GeoTextLight,
    surface = GeoCardDark,
    onSurface = GeoTextLight,
    surfaceVariant = GeoCardDark,
    onSurfaceVariant = GeoSubtextDark,
    outline = GeoBorderDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = GeoBluePrimary,
    onPrimary = Color.White,
    primaryContainer = GeoBlueContainer,
    onPrimaryContainer = GeoBlueText,
    secondary = GeoFinishedAlarm,
    onSecondary = Color.White,
    tertiary = GeoBluePrimary,
    onTertiary = Color.White,
    background = GeoBgLight,
    onBackground = GeoTextDark,
    surface = GeoCardLight,
    onSurface = GeoTextDark,
    surfaceVariant = GeoCardLight,
    onSurfaceVariant = GeoSubtext,
    outline = GeoBorderLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
