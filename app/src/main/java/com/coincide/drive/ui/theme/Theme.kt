package com.coincide.drive.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DrivePrimaryLight,
    onPrimary = DriveOnPrimaryLight,
    primaryContainer = DrivePrimaryContainerLight,
    onPrimaryContainer = DriveOnPrimaryContainerLight,
    secondary = DriveSecondaryLight,
    onSecondary = DriveOnSecondaryLight,
    secondaryContainer = DriveSecondaryContainerLight,
    onSecondaryContainer = DriveOnSecondaryContainerLight,
    tertiary = DriveTertiaryLight,
    onTertiary = DriveOnTertiaryLight,
    tertiaryContainer = DriveTertiaryContainerLight,
    onTertiaryContainer = DriveOnTertiaryContainerLight,
    background = DriveBackgroundLight,
    onBackground = DriveOnBackgroundLight,
    surface = DriveSurfaceLight,
    onSurface = DriveOnSurfaceLight,
    surfaceVariant = DriveSurfaceVariantLight,
    onSurfaceVariant = DriveOnSurfaceVariantLight,
    outline = DriveOutlineLight,
    outlineVariant = DriveOutlineVariantLight,
    surfaceContainer = DriveSurfaceContainerLight,
    surfaceContainerLow = DriveSurfaceContainerLowLight,
    surfaceContainerHigh = DriveSurfaceContainerHighLight,
    error = DriveErrorRed,
    errorContainer = DriveErrorRedContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DrivePrimaryDark,
    onPrimary = DriveOnPrimaryDark,
    primaryContainer = DrivePrimaryContainerDark,
    onPrimaryContainer = DriveOnPrimaryContainerDark,
    secondary = DriveSecondaryDark,
    onSecondary = DriveOnSecondaryDark,
    secondaryContainer = DriveSecondaryContainerDark,
    onSecondaryContainer = DriveOnSecondaryContainerDark,
    tertiary = DriveTertiaryDark,
    onTertiary = DriveOnTertiaryDark,
    tertiaryContainer = DriveTertiaryContainerDark,
    onTertiaryContainer = DriveOnTertiaryContainerDark,
    background = DriveBackgroundDark,
    onBackground = DriveOnBackgroundDark,
    surface = DriveSurfaceDark,
    onSurface = DriveOnSurfaceDark,
    surfaceVariant = DriveSurfaceVariantDark,
    onSurfaceVariant = DriveOnSurfaceVariantDark,
    outline = DriveOutlineDark,
    outlineVariant = DriveOutlineVariantDark,
    surfaceContainer = DriveSurfaceContainerDark,
    surfaceContainerLow = DriveSurfaceContainerLowDark,
    surfaceContainerHigh = DriveSurfaceContainerHighDark,
    error = DriveErrorRed,
    errorContainer = DriveErrorRedContainer
)

/**
 * Coincide Drive Material 3 Application Theme
 * Configured in clean Light Mode across all screens.
 */
@Composable
fun CoincideDriveTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color(0xFFF4F8FC).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CoincideTypography,
        shapes = CoincideShapes,
        content = content
    )
}

