package com.example.presentation.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.preferences.ThemePreferences

data class AppColorScheme(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceItem: Color,
    val surfaceBorder: Color,
    val primary: Color,
    val primaryDark: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val green: Color = Color(0xFF00E676),
    val amber: Color = Color(0xFFFF9100),
    val blue: Color = Color(0xFF2979FF)
)

val LocalAppColors = staticCompositionLocalOf {
    AppColorScheme(
        isDark = true,
        background = Color(0xFF06070B),
        surface = Color(0xFF0E131F),
        surfaceItem = Color(0xFF0A0F1A),
        surfaceBorder = Color(0xFF1E283C),
        primary = Color(0xFFFF1E27),
        primaryDark = Color(0xFFD50000),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF94A3B8),
        textMuted = Color(0xFF64748B)
    )
}

object AppTheme {
    val colors: AppColorScheme
        @Composable
        get() = LocalAppColors.current
}

// Dynamic properties delegating to current AppTheme.colors
val ObsidianBg: Color @Composable get() = AppTheme.colors.background
val SurfaceDark: Color @Composable get() = AppTheme.colors.surface
val SurfaceItem: Color @Composable get() = AppTheme.colors.surfaceItem
val SurfaceBorder: Color @Composable get() = AppTheme.colors.surfaceBorder

val NeonRed: Color @Composable get() = AppTheme.colors.primary
val NeonRedDark: Color @Composable get() = AppTheme.colors.primaryDark
val NeonGreen: Color = Color(0xFF00E676)
val NeonAmber: Color = Color(0xFFFF9100)
val NeonBlue: Color = Color(0xFF2979FF)
val NeonPurple: Color = Color(0xFF7C4DFF)

val TextPrimary: Color @Composable get() = AppTheme.colors.textPrimary
val TextSecondary: Color @Composable get() = AppTheme.colors.textSecondary
val TextMuted: Color @Composable get() = AppTheme.colors.textMuted

@Composable
fun WifiCardTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val prefs = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val themePref = prefs.getString("theme", "dark") ?: "dark"
    val isDark = when (themePref) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }

    val primaryKey = ThemePreferences.getPrimaryColorSync(context)
    val (primary, primaryDark) = when (primaryKey.lowercase()) {
        "blue" -> Pair(Color(0xFF2979FF), Color(0xFF1565C0))
        "purple" -> Pair(Color(0xFF7C4DFF), Color(0xFF512DA8))
        "yellow" -> Pair(Color(0xFFFFB300), Color(0xFFF57F17))
        "green" -> Pair(Color(0xFF00E676), Color(0xFF2E7D32))
        else -> Pair(Color(0xFFFF1E27), Color(0xFFD50000))
    }

    val appColorScheme = if (isDark) {
        AppColorScheme(
            isDark = true,
            background = Color(0xFF06070B),
            surface = Color(0xFF0E131F),
            surfaceItem = Color(0xFF0A0F1A),
            surfaceBorder = Color(0xFF1E283C),
            primary = primary,
            primaryDark = primaryDark,
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFF94A3B8),
            textMuted = Color(0xFF64748B)
        )
    } else {
        AppColorScheme(
            isDark = false,
            background = Color(0xFFF1F5F9),
            surface = Color(0xFFFFFFFF),
            surfaceItem = Color(0xFFF8FAFC),
            surfaceBorder = Color(0xFFCBD5E1),
            primary = primary,
            primaryDark = primaryDark,
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            textMuted = Color(0xFF64748B)
        )
    }

    val materialColorScheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = NeonGreen,
            background = Color(0xFF06070B),
            surface = Color(0xFF0E131F),
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = NeonGreen,
            background = Color(0xFFF1F5F9),
            surface = Color(0xFFFFFFFF),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A)
        )
    }

    CompositionLocalProvider(LocalAppColors provides appColorScheme) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
