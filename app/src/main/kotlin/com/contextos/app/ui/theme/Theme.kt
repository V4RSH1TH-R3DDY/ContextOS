package com.contextos.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Dark color scheme ────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = IndigoLight,
    onPrimary            = OnPrimaryDark,
    primaryContainer     = IndigoDark,
    onPrimaryContainer   = OnPrimaryContainerDark,

    secondary            = TealLight,
    onSecondary          = OnSecondaryDark,
    secondaryContainer   = TealDark,
    onSecondaryContainer = TealContainer,

    tertiary             = AmberLight,
    onTertiary           = OnTertiaryDark,
    tertiaryContainer    = AmberTertiary,
    onTertiaryContainer  = AmberContainer,

    error                = ErrorRedLight,
    onError              = ErrorContainer,
    errorContainer       = ErrorContainer,
    onErrorContainer     = ErrorRedLight,

    background           = Surface10,
    onBackground         = Surface90,

    surface              = Surface15,
    onSurface            = Surface80,
    surfaceVariant       = Surface25,
    onSurfaceVariant     = Surface80,

    outline              = Surface30,
    outlineVariant       = Surface20,
    scrim                = Surface10,
    inverseSurface       = Surface90,
    inverseOnSurface     = Surface20,
    inversePrimary       = IndigoDark
)

// ─── Light color scheme ───────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = IndigoDark,
    onPrimary            = OnPrimaryLight,
    primaryContainer     = IndigoContainer,
    onPrimaryContainer   = OnPrimaryContainerLight,

    secondary            = TealDark,
    onSecondary          = OnSecondaryLight,
    secondaryContainer   = TealContainer,
    onSecondaryContainer = TealDark,

    tertiary             = AmberTertiary,
    onTertiary           = OnTertiaryLight,
    tertiaryContainer    = AmberContainer,
    onTertiaryContainer  = OnTertiaryDark,

    error                = ErrorRed,
    onError              = OnPrimaryLight,
    errorContainer       = OnErrorLight,
    onErrorContainer     = ErrorContainer,

    background           = Surface99,
    onBackground         = Surface15,

    surface              = Surface99,
    onSurface            = Surface20,
    surfaceVariant       = Surface95,
    onSurfaceVariant     = Surface25,

    outline              = Surface30,
    outlineVariant       = Surface80,
    scrim                = Surface10,
    inverseSurface       = Surface25,
    inverseOnSurface     = Surface95,
    inversePrimary       = IndigoLight
)

// ─── Theme composable ─────────────────────────────────────────────────────────
@Composable
fun ContextOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (API 31).
    // Set to false to always use the ContextOS brand palette.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme  -> DarkColorScheme
        else       -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ContextOSTypography,
        content     = content
    )
}
