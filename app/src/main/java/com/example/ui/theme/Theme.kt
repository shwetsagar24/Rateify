package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = RateifyPrimary,
    secondary = RateifySecondary,
    tertiary = RateifyTertiary,
    background = RateifyDarkBg,
    surface = RateifyDarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = RateifyDarkOnSurface,
    onSurface = RateifyDarkOnSurface,
    surfaceVariant = Color(0xFF221F28), // Sleek charcoal card variant
    onSurfaceVariant = Color(0xFFD6D4DF)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = RateifyPrimary,
    secondary = RateifySecondary,
    tertiary = RateifyTertiary,
    background = RateifyLightBg,
    surface = RateifyLightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = RateifyLightOnSurface,
    onSurface = RateifyLightOnSurface,
    surfaceVariant = Color(0xFFEFECEF),
    onSurfaceVariant = Color(0xFF585564)
  )

data class AppColors(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color
)

val DarkAppColors = AppColors(
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF111118),
    surface2 = Color(0xFF1A1A24),
    surface3 = Color(0xFF222230),
    border = Color(0xFF2A2A3A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0A0B8),
    textTertiary = Color(0xFF606075)
)

val LightAppColors = AppColors(
    background = Color(0xFFF5F5F8),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0F0F5),
    surface3 = Color(0xFFE8E8F0),
    border = Color(0xFFE0E0EA),
    textPrimary = Color(0xFF0A0A0F),
    textSecondary = Color(0xFF606075),
    textTertiary = Color(0xFFA0A0B8)
)

val LocalAppColors = androidx.compose.runtime.staticCompositionLocalOf { DarkAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to preserve Rateify's signature branding colors
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

  val appColors = if (darkTheme) DarkAppColors else LightAppColors

  CompositionLocalProvider(LocalAppColors provides appColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
