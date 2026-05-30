package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  lightColorScheme(
    primary = CourtGreen,
    secondary = CourtGold,
    tertiary = LightCourtGreen,
    background = DeepCharcoal,
    surface = DarkEmerald,
    onPrimary = Color.White,
    onSecondary = WarmWhite,
    onBackground = WarmWhite,
    onSurface = WarmWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CourtGreen,
    secondary = CourtGold,
    tertiary = LightCourtGreen,
    background = DeepCharcoal,
    surface = DarkEmerald,
    onPrimary = Color.White,
    onSecondary = WarmWhite,
    onBackground = WarmWhite,
    onSurface = WarmWhite
  )


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors to force the royal courtroom judicial brand identity
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
