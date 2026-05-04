package com.contextos.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ColorScheme = darkColorScheme(
    primary            = Accent,
    onPrimary          = Color.Black,
    primaryContainer   = AccentDim,
    onPrimaryContainer = TextPrimary,
    secondary          = TextSecondary,
    onSecondary        = Background,
    tertiary           = TextTertiary,
    onTertiary         = Background,
    error              = WarningAmber,
    onError            = Background,
    background         = Background,
    onBackground       = TextPrimary,
    surface            = SurfaceBg,
    onSurface          = TextPrimary,
    surfaceVariant     = SurfaceHover,
    onSurfaceVariant   = TextSecondary,
    outline            = BorderLight,
    outlineVariant     = Border,
    scrim              = Color.Black,
    inverseSurface     = TextPrimary,
    inverseOnSurface   = SurfaceBg,
    inversePrimary     = AccentDim,
)

@Composable
fun ContextOSTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = ColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ContextOSTypography,
        content     = content,
    )
}
