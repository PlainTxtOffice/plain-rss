package com.plaintxtoffice.plainrss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RssOrange = Color(0xFFE0521A)
val RssOrangeLight = Color(0xFFF08A3C)
val RssSlate = Color(0xFF2B2D31)
val RssCream = Color(0xFFF4F1EA)

private val LightColors = lightColorScheme(
  primary = RssOrange,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFFFDBD0),
  onPrimaryContainer = Color(0xFF3A0B00),
  secondary = Color(0xFF5C5F66),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE1E2E8),
  onSecondaryContainer = Color(0xFF191C20),
  background = Color(0xFFFAF9F6),
  onBackground = Color(0xFF1B1B1D),
  surface = Color(0xFFFAF9F6),
  onSurface = Color(0xFF1B1B1D),
  surfaceVariant = Color(0xFFECEAE4),
  onSurfaceVariant = Color(0xFF46464A),
)

private val DarkColors = darkColorScheme(
  primary = RssOrangeLight,
  onPrimary = Color(0xFF4A1800),
  primaryContainer = Color(0xFF6B2700),
  onPrimaryContainer = Color(0xFFFFDBD0),
  secondary = Color(0xFFC4C6D0),
  onSecondary = Color(0xFF2D3038),
  secondaryContainer = Color(0xFF43474E),
  onSecondaryContainer = Color(0xFFE0E2EC),
  background = Color(0xFF16171A),
  onBackground = RssCream,
  surface = Color(0xFF1E2024),
  onSurface = RssCream,
  surfaceVariant = RssSlate,
  onSurfaceVariant = Color(0xFFC7C6CA),
)

@Composable
fun PlainRssTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColors else LightColors
  MaterialTheme(
    colorScheme = colorScheme,
    content = content,
  )
}
