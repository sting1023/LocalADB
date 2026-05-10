package com.sting.localadb.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FF00),
    secondary = Color(0xFF00CCFF),
    tertiary = Color(0xFFFF6600),
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFCCCCCC),
    onSurface = Color(0xFFCCCCCC),
    error = Color(0xFFFF4444),
    onError = Color.White
)

@Composable
fun LocalADBTheme(
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = 0xFF1E1E1E.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}