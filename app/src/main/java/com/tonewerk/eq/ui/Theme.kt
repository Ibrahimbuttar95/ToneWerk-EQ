package com.tonewerk.eq.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFF2A33C)
val InkBg = Color(0xFF0B0C0E)
val InkSurface = Color(0xFF121417)
val InkSurface2 = Color(0xFF1A1D22)
val TextDim = Color(0xFF8F959D)
val Green = Color(0xFF2FBF71)
val Red = Color(0xFFE5484D)

private val Scheme = darkColorScheme(
    primary = Amber, onPrimary = Color(0xFF1A1105),
    primaryContainer = Color(0xFF3A2C12), onPrimaryContainer = Amber,
    secondary = Color(0xFF5B7F93), tertiary = Green,
    background = InkBg, onBackground = Color(0xFFE8E6E1),
    surface = InkSurface, onSurface = Color(0xFFE8E6E1),
    surfaceVariant = InkSurface2, onSurfaceVariant = TextDim,
    outline = Color(0xFF23272D), error = Red
)

@Composable
fun TonewerkTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, content = content)
