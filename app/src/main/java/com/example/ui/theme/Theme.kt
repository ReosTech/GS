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

private val DarkColorScheme =
  darkColorScheme(
    primary = MinimalPrimaryDark,
    onPrimary = MinimalBgDark,
    primaryContainer = MinimalPrimaryContainerDark,
    onPrimaryContainer = MinimalOnPrimaryContainerDark,
    background = MinimalBgDark,
    surface = MinimalSurfaceDark,
    surfaceContainer = MinimalSurfaceContainerDark,
    onBackground = MinimalTextDark,
    onSurface = MinimalTextDark,
    onSurfaceVariant = MinimalSubtextDark,
    outline = MinimalBorderDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MinimalPrimary,
    onPrimary = MinimalOnPrimary,
    primaryContainer = MinimalPrimaryContainer,
    onPrimaryContainer = MinimalOnPrimaryContainer,
    background = MinimalBgLight,
    surface = MinimalBgLight,
    surfaceContainer = MinimalSurfaceContainer,
    onBackground = MinimalTextLight,
    onSurface = MinimalTextLight,
    onSurfaceVariant = MinimalSubtextLight,
    outline = MinimalBorder
  )

private object NoIndication : androidx.compose.foundation.IndicationNodeFactory {
  override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode {
    return object : androidx.compose.ui.Modifier.Node() {}
  }
  override fun hashCode(): Int = -1
  override fun equals(other: Any?): Boolean = other === this
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReosTechTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
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

  MaterialTheme(colorScheme = colorScheme, typography = Typography) {
    androidx.compose.runtime.CompositionLocalProvider(
      androidx.compose.material3.LocalRippleConfiguration provides null,
      androidx.compose.foundation.LocalIndication provides NoIndication,
      content = content
    )
  }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) = ReosTechTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
