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
 *  - CelestialHeaderBackground()           -- code-drawn About header backdrop (live when on, frozen when off)
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
  val containerCornerRadius: (Float) -> Dp,
)

@Composable
fun celestialFabColors(): CelestialFabColors {
  val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
  val primaryContainer = MaterialTheme.colorScheme.primaryContainer
  return CelestialFabColors(
    containerColor = { progress -> lerp(surfaceContainerHigh, primaryContainer, progress) },
    containerCornerRadius = { _ -> 28.dp },
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
 * Code-drawn backdrop for the About header card: deep dark base, one large glowing light
 * sweep rising to the right with parallel hairlines, and a few glowing dots (drawn like the old header image, scaled to cover the card). Everything is
 * tinted from the active theme. Live (breathing sweep, pulsing glow, twinkling dots) when the
 * toggle is on, the same scene frozen when it is off.
 */
@Composable
fun CelestialHeaderBackground(modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  val primary = cs.primary
  val tertiary = cs.tertiary
  if (isCelestialEffectsEnabled()) {
    val transition = rememberInfiniteTransition(label = "celestial_header")
    val drift by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 9000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "celestial_header_drift",
    )
    val glowPulse by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 5000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "celestial_header_glow",
    )
    val twinkle by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2400),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "celestial_header_twinkle",
    )
    Canvas(modifier = modifier) {
      drawHeaderScene(primary, tertiary, drift, glowPulse, twinkle)
    }
  } else {
    Canvas(modifier = modifier) {
      drawHeaderScene(primary, tertiary, 0.5f, 0.5f, 0.5f)
    }
  }
}

private const val HEADER_SCENE_W = 853f
private const val HEADER_SCENE_H = 1115f

// Center line of the sweep, traced from the reference artwork (scene px).
private val HeaderRidge =
  listOf(
    -40f to 775f, 80f to 739f, 200f to 702f, 320f to 650f, 440f to 567f,
    560f to 458f, 680f to 334f, 800f to 229f, 920f to 160f,
  )

// x, y, radius (scene px)
private val HeaderDots =
  listOf(
    Triple(587f, 238f, 3.2f), Triple(790f, 397f, 3.6f), Triple(707f, 541f, 2.6f),
    Triple(533f, 628f, 2.2f), Triple(508f, 628f, 1.5f), Triple(172f, 830f, 1.8f),
  )

/** Ridge points pushed `off0` px vertically at the left end and `off1` px at the right end. */
private fun headerPoints(off0: Float, off1: Float, sway: Float): List<Offset> =
  HeaderRidge.map { (x, y) -> Offset(x, y + sway + off0 + (off1 - off0) * ((x + 40f) / 960f)) }

/** Smooth curve through [points]; the path's current point must already be `points.first()`. */
private fun Path.splineThrough(points: List<Offset>) {
  for (i in 0 until points.lastIndex) {
    val p0 = points[maxOf(i - 1, 0)]
    val p1 = points[i]
    val p2 = points[i + 1]
    val p3 = points[minOf(i + 2, points.lastIndex)]
    cubicTo(
      p1.x + (p2.x - p0.x) / 6f,
      p1.y + (p2.y - p0.y) / 6f,
      p2.x - (p3.x - p1.x) / 6f,
      p2.y - (p3.y - p1.y) / 6f,
      p2.x,
      p2.y,
    )
  }
}

private fun headerLine(points: List<Offset>) =
  Path().apply {
    moveTo(points.first().x, points.first().y)
    splineThrough(points)
  }

private fun headerBand(upper: List<Offset>, lower: List<Offset>) =
  Path().apply {
    moveTo(upper.first().x, upper.first().y)
    splineThrough(upper)
    lineTo(lower.last().x, lower.last().y)
    splineThrough(lower.asReversed())
    close()
  }

/**
 * The scene is designed on an 853x1115 canvas and scaled to cover the card (center-cropped),
 * so the sweep keeps its curve no matter how tall the header becomes.
 */
private fun DrawScope.drawHeaderScene(
  primary: Color,
  tertiary: Color,
  drift: Float,
  glowPulse: Float,
  twinkle: Float,
) {
  drawRect(color = lerp(Color.Black, primary, 0.13f), size = size)
  val fit = maxOf(size.width / HEADER_SCENE_W, size.height / HEADER_SCENE_H)
  val dx = (size.width - HEADER_SCENE_W * fit) / 2f
  val dy = (size.height - HEADER_SCENE_H * fit) / 2f
  translate(left = dx, top = dy) {
    scale(scaleX = fit, scaleY = fit, pivot = Offset.Zero) {
      drawHeaderSweep(primary, tertiary, drift, glowPulse, twinkle)
    }
  }
}

private fun DrawScope.drawHeaderSweep(
  primary: Color,
  tertiary: Color,
  drift: Float,
  glowPulse: Float,
  twinkle: Float,
) {
  val core = lerp(primary, Color.White, 0.40f)
  val violet = lerp(primary, tertiary, 0.35f)

  // Blue haze on the left, like the artwork's soft background glow.
  drawCircle(
    brush =
      Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.34f), Color.Transparent),
        center = Offset(150f, 300f),
        radius = 480f,
      ),
    radius = 480f,
    center = Offset(150f, 300f),
  )

  val sway = 14f * (drift - 0.5f)
  fun line(off0: Float, off1: Float) = headerPoints(off0, off1, sway)

  // Alpha envelopes along x: soft entry from the left, strongest on the right.
  fun env(color: Color, alpha: Float) =
    Brush.horizontalGradient(
      0f to Color.Transparent,
      0.25f to color.copy(alpha = alpha * 0.25f),
      0.60f to color.copy(alpha = alpha),
      1f to color.copy(alpha = alpha * 0.75f),
      startX = 0f,
      endX = HEADER_SCENE_W,
    )
  fun ramp(color: Color, alpha: Float) =
    Brush.horizontalGradient(
      0f to Color.Transparent,
      0.18f to color.copy(alpha = alpha * 0.12f),
      0.42f to color.copy(alpha = alpha * 0.45f),
      0.62f to color.copy(alpha = alpha),
      1f to color.copy(alpha = alpha),
      startX = 0f,
      endX = HEADER_SCENE_W,
    )
  val peak = 0.35f + 0.60f * drift
  fun shimmer(color: Color, alpha: Float) =
    Brush.horizontalGradient(
      0f to Color.Transparent,
      (peak - 0.25f) to Color.Transparent,
      peak to color.copy(alpha = alpha),
      minOf(peak + 0.25f, 0.99f) to Color.Transparent,
      1f to Color.Transparent,
      startX = 0f,
      endX = HEADER_SCENE_W,
    )

  // Wide halo: many faint layers so the glow falls off smoothly instead of in steps.
  val breathe = 0.92f + 0.16f * glowPulse
  for (k in 8 downTo 1) {
    val f = k / 8f * breathe
    drawPath(headerBand(line(-60f * f, -150f * f), line(60f * f, 150f * f)), env(primary, 0.085f))
  }
  // Brighter glow hugging the core, a little heavier on the lower side.
  for (k in 5 downTo 1) {
    val f = k / 5f * breathe
    drawPath(headerBand(line(-12f * f, -20f * f), line(26f * f, 64f * f)), env(violet, 0.20f))
  }

  // The single main line: soft body, crisp core, and a light that travels along it.
  drawPath(headerLine(line(0f, 0f)), ramp(core, 0.70f), style = Stroke(width = 10f, cap = StrokeCap.Round))
  drawPath(headerLine(line(0f, 0f)), ramp(core, 0.80f + 0.20f * glowPulse), style = Stroke(width = 3.6f, cap = StrokeCap.Round))
  drawPath(headerLine(line(0f, 0f)), shimmer(lerp(core, Color.White, 0.5f), 0.55f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))

  // Everything else just fades out: thin, faint lines that open up toward the right.
  drawPath(headerLine(line(-34f, -62f)), env(core, 0.50f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
  drawPath(headerLine(line(30f, 80f)), env(violet, 0.45f), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
  drawPath(headerLine(line(60f, 177f)), env(primary, 0.38f), style = Stroke(width = 2f, cap = StrokeCap.Round))
  drawPath(headerLine(line(-80f, -120f)), env(primary, 0.20f), style = Stroke(width = 1.8f, cap = StrokeCap.Round))

  HeaderDots.forEachIndexed { index, (x, y, r) ->
    val phase = (twinkle + index * 0.23f) % 1f
    val a = (0.35f + 0.65f * sin(phase * Math.PI).toFloat()).coerceIn(0f, 1f)
    drawCircle(color = core.copy(alpha = 0.20f * a), radius = r * 3.4f, center = Offset(x, y))
    drawCircle(color = core.copy(alpha = a), radius = r, center = Offset(x, y))
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
