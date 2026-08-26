/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.celestial

/*
 * Everything Replay's "Celestial effects" toggle controls lives in this one
 * file: aurora ribbons background, FAB glow border + container styling, top
 * bar tinting, and the nav bar glass look. Every other file only ever calls
 * these entry points (usually one line), never inlines this logic. Keeping
 * it isolated here means an upstream merge almost never touches this file,
 * and any file that DOES call into it only has a one- or two-line diff to
 * resolve if upstream reshapes it.
 *
 * Entry points other files use:
 *  - CelestialBackground()                 -- ribbons/glow, call once per screen body
 *  - Modifier.celestialBorder(shape)        -- chain onto any FAB's modifier
 *  - celestialFabColors()                   -- containerColor + containerCornerRadius for ToggleFloatingActionButton
 *  - celestialTitleColor() / celestialIconColor() -- BrowserTopBar tinting
 *  - celestialNavBarContainerColor() / celestialNavBarBorderColor() -- bottom nav glass
 *  - CelestialEffectsToggle()               -- the Settings switch itself
 *  - isCelestialEffectsEnabled()            -- raw boolean, for anything not covered above
 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.preferences.components.SwitchPreference
import org.koin.compose.koinInject
import kotlin.math.sin

/** Raw toggle state. Prefer the more specific helpers below where possible. */
@Composable
fun isCelestialEffectsEnabled(): Boolean {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showCelestialEffects by appearancePreferences.showCelestialEffects.collectAsState()
  return showCelestialEffects
}

/**
 * Chain onto any FAB's modifier to add the celestial glow border when the
 * toggle is on, or leave the modifier untouched when it's off.
 *
 * Usage: `Modifier.someExistingChain().celestialBorder()`
 */
@Composable
fun Modifier.celestialBorder(shape: Shape = CircleShape): Modifier {
  if (!isCelestialEffectsEnabled()) return this
  return this.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), shape)
}

/** containerColor + containerCornerRadius pair for ToggleFloatingActionButton. */
data class CelestialFabColors(
  val containerColor: (Float) -> Color,
  val containerCornerRadius: () -> Dp,
)

@Composable
fun celestialFabColors(): CelestialFabColors {
  val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
  val primaryContainer = MaterialTheme.colorScheme.primaryContainer
  return CelestialFabColors(
    containerColor = { progress -> lerp(surfaceContainerHigh, primaryContainer, progress) },
    containerCornerRadius = { 28.dp },
  )
}

/** Muted title tint for BrowserTopBar; falls back to plain onSurface when off. */
@Composable
fun celestialTitleColor(): Color =
  if (isCelestialEffectsEnabled()) {
    lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, 0.4f)
  } else {
    MaterialTheme.colorScheme.onSurface
  }

/** Muted icon tint for BrowserTopBar; falls back to plain onSurface when off. */
@Composable
fun celestialIconColor(): Color =
  if (isCelestialEffectsEnabled()) {
    MaterialTheme.colorScheme.onSurfaceVariant
  } else {
    MaterialTheme.colorScheme.onSurface
  }

/** Bottom nav bar background: translucent glass when on, solid when off. */
@Composable
fun celestialNavBarContainerColor(): Color =
  if (isCelestialEffectsEnabled()) {
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh
  }

/** Bottom nav bar border: celestial-tinted glow when on, plain outline when off. */
@Composable
fun celestialNavBarBorderColor(): Color =
  if (isCelestialEffectsEnabled()) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
  } else {
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
  }

/**
 * The "Celestial effects" switch itself. Drop this into any preferences
 * screen's LazyColumn/Column - it reads its own state and strings.
 */
@Composable
fun CelestialEffectsToggle() {
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showCelestialEffects by appearancePreferences.showCelestialEffects.collectAsState()
  SwitchPreference(
    value = showCelestialEffects,
    onValueChange = appearancePreferences.showCelestialEffects::set,
    title = { Text(text = stringResource(id = R.string.pref_celestial_effects_title)) },
    summary = {
      Text(
        text = stringResource(id = R.string.pref_celestial_effects_summary),
        color = MaterialTheme.colorScheme.outline,
      )
    },
  )
}

/**
 * Call once per screen body (e.g. right inside the content Box, before the
 * rest of the screen's children) to draw the aurora ribbons when the
 * toggle is on. Does nothing when the toggle is off.
 */
@Composable
fun CelestialBackground() {
  if (isCelestialEffectsEnabled()) {
    CelestialFolderListBackground()
  }
}

/**
 * Premium drifting aurora ribbons + soft glow behind a Browser tab's list content.
 * Renders unconditionally - prefer calling [CelestialBackground] instead, which
 * checks the toggle for you.
 */
@Composable
private fun CelestialFolderListBackground() {
  val cs = MaterialTheme.colorScheme
  val transition = rememberInfiniteTransition(label = "celestial_folder_bg")
  val drift by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 12000, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "celestial_folder_drift",
  )
  val glowPulse by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 6000, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "celestial_folder_glow",
  )
  val twinkle by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec =
      infiniteRepeatable(
        animation = tween(durationMillis = 2600),
        repeatMode = RepeatMode.Reverse,
      ),
    label = "celestial_folder_twinkle",
  )
  val primary = cs.primary
  val secondary = cs.secondary
  val tertiary = cs.tertiary
  val sparklePositions =
    remember {
      listOf(
        0.72f to 0.30f, 0.85f to 0.48f, 0.60f to 0.58f, 0.90f to 0.20f, 0.50f to 0.42f,
        0.20f to 0.22f, 0.32f to 0.62f, 0.12f to 0.45f, 0.78f to 0.65f, 0.42f to 0.15f,
      )
    }

  Canvas(modifier = Modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    val shift = h * 0.05f * (drift - 0.5f)

    // Soft ambient glow, like a distant light source easing the pure-black canvas
    // into a richer, more premium depth without ever reading as "flat blue".
    val glowRadius = w * (0.75f + 0.10f * glowPulse)
    drawRect(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              primary.copy(alpha = 0.10f),
              tertiary.copy(alpha = 0.05f),
              Color.Transparent,
            ),
          center = Offset(w * 0.82f, h * 0.06f),
          radius = glowRadius,
        ),
      size = size,
    )
    drawRect(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              secondary.copy(alpha = 0.06f),
              Color.Transparent,
            ),
          center = Offset(w * 0.10f, h * 0.85f),
          radius = w * 0.7f,
        ),
      size = size,
    )

    fun ribbon(
      color: Color,
      yStart: Float,
      yEnd: Float,
      alpha: Float,
      strokeWidth: Float,
      glow: Boolean = false,
    ) {
      val path =
        Path().apply {
          moveTo(-w * 0.1f, h * yStart + shift)
          cubicTo(
            w * 0.35f,
            h * (yStart - 0.10f) + shift,
            w * 0.65f,
            h * (yEnd + 0.10f) - shift,
            w * 1.1f,
            h * yEnd - shift,
          )
        }
      val gradientBrush =
        Brush.linearGradient(
          colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
          start = Offset(0f, h * yStart),
          end = Offset(w, h * yEnd),
        )
      // Wide, low-alpha underlay first for a soft glow halo, then a crisp core stroke.
      if (glow) {
        drawPath(
          path = path,
          brush = gradientBrush,
          style = Stroke(width = strokeWidth * 3.2f, cap = StrokeCap.Round),
          alpha = 0.35f,
        )
      }
      drawPath(
        path = path,
        brush = gradientBrush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )
    }

    ribbon(primary, yStart = 0.30f, yEnd = 0.50f, alpha = 0.34f, strokeWidth = 5.dp.toPx(), glow = true)
    ribbon(tertiary, yStart = 0.38f, yEnd = 0.58f, alpha = 0.22f, strokeWidth = 9.dp.toPx(), glow = true)
    ribbon(secondary, yStart = 0.24f, yEnd = 0.40f, alpha = 0.16f, strokeWidth = 3.dp.toPx())
    ribbon(primary, yStart = 0.55f, yEnd = 0.72f, alpha = 0.10f, strokeWidth = 6.dp.toPx())

    sparklePositions.forEachIndexed { index, (fx, fy) ->
      val phase = (twinkle + index * 0.19f) % 1f
      val twinkleAlpha = 0.12f + 0.42f * sin(phase * Math.PI).toFloat()
      drawCircle(
        color = Color.White.copy(alpha = twinkleAlpha.coerceIn(0f, 1f)),
        radius = 1.4.dp.toPx(),
        center = Offset(w * fx, h * fy),
      )
    }
  }
}
