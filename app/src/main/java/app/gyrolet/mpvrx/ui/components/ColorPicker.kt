/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object ColorPickerDefaults {
  /** Quick picks shown under every picker: neutrals first, then the hue circle. */
  val swatches: List<Int> = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF1C1B1F, 0xFF9E9E9E,
    0xFFF44336, 0xFFFF5722, 0xFFFF9800, 0xFFFFC107,
    0xFFFFEB3B, 0xFFCDDC39, 0xFF4CAF50, 0xFF009688,
    0xFF00BCD4, 0xFF2196F3, 0xFF3F51B5, 0xFF6750A4,
    0xFF9C27B0, 0xFFE91E63,
  ).map { it.toInt() }
}

private enum class PickerMode { Wheel, Sliders }

/** Hue is kept apart from the colour so it survives passing through black, white and grey. */
@Stable
private class PickerState(initial: Int, private val showAlpha: Boolean) {
  var hue by mutableFloatStateOf(0f)
  var sat by mutableFloatStateOf(0f)
  var value by mutableFloatStateOf(0f)
  var alpha by mutableIntStateOf(255)

  /** The colour as shown and emitted. Kept exact so RGB sliders do not drift through HSV rounding. */
  var current by mutableIntStateOf(initial)
  var lastEmitted: Int = initial

  init {
    load(initial)
  }

  fun load(argb: Int) {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
    if (hsv[2] > 0f) sat = hsv[1]
    if (hsv[1] == 0f) sat = 0f
    value = hsv[2]
    alpha = if (showAlpha) AndroidColor.alpha(argb) else 255
    current = if (showAlpha) argb else (argb or (0xFF shl 24))
    lastEmitted = argb
  }

  fun argb(): Int = AndroidColor.HSVToColor(if (showAlpha) alpha else 255, floatArrayOf(hue, sat, value))

  fun saturatedBright(): Color = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, 1f)))
}

/**
 * Shared colour picker: HSV wheel with brightness (and optional alpha), RGB sliders, hex field and swatches.
 * [color] and [onColorChange] use ARGB ints. With [showAlpha] false the result is always opaque.
 */
@Composable
fun ColorPicker(
  color: Int,
  onColorChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  showAlpha: Boolean = false,
  swatches: List<Int> = ColorPickerDefaults.swatches,
  maxWheelSize: Dp = 280.dp,
) {
  val state = remember(showAlpha) { PickerState(color, showAlpha) }
  var mode by rememberSaveable { mutableStateOf(PickerMode.Wheel) }
  var hexText by remember { mutableStateOf(color.toPickerHex(showAlpha)) }

  // Outside changes (reset, swatch elsewhere, another editor) win over the picker's own state.
  LaunchedEffect(color) {
    if (color != state.lastEmitted) state.load(color)
    if (parsePickerHex(hexText, state.alpha, showAlpha) != color) hexText = color.toPickerHex(showAlpha)
  }

  fun emitHsv() {
    val next = state.argb()
    state.current = next
    state.lastEmitted = next
    hexText = next.toPickerHex(showAlpha)
    onColorChange(next)
  }

  fun emitExact(next: Int) {
    state.load(next)
    hexText = next.toPickerHex(showAlpha)
    onColorChange(next)
  }

  val current = state.current

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ColorSwatch(color = current, size = 48.dp, selected = false)
      OutlinedTextField(
        value = hexText,
        onValueChange = { raw ->
          val digits = raw.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
            .take(if (showAlpha) 8 else 6).uppercase()
          hexText = digits
          parsePickerHex(digits, state.alpha, showAlpha)?.let { emitExact(it) }
        },
        modifier = Modifier.weight(1f),
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.color_picker_hex)) },
        prefix = { Text("#") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
          capitalization = KeyboardCapitalization.Characters,
          keyboardType = KeyboardType.Ascii,
        ),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
        selected = mode == PickerMode.Wheel,
        onClick = { mode = PickerMode.Wheel },
        label = { Text(stringResource(R.string.color_picker_tab_wheel)) },
      )
      FilterChip(
        selected = mode == PickerMode.Sliders,
        onClick = { mode = PickerMode.Sliders },
        label = { Text(stringResource(R.string.color_picker_tab_sliders)) },
      )
    }

    if (mode == PickerMode.Wheel) {
      HueSaturationWheel(
        hue = state.hue,
        sat = state.sat,
        value = state.value,
        thumbColor = Color(current).copy(alpha = 1f),
        enabled = enabled,
        maxSize = maxWheelSize,
        onChange = { h, s ->
          state.hue = h
          state.sat = s
          emitHsv()
        },
        modifier = Modifier.align(Alignment.CenterHorizontally),
      )
      GradientBar(
        label = stringResource(R.string.color_picker_brightness),
        fraction = state.value,
        valueText = "${(state.value * 100).roundToInt()}%",
        brush = Brush.horizontalGradient(listOf(Color.Black, state.saturatedBright())),
        enabled = enabled,
        onFraction = {
          state.value = it
          emitHsv()
        },
      )
      if (showAlpha) {
        GradientBar(
          label = stringResource(R.string.player_sheets_sub_color_alpha),
          fraction = state.alpha / 255f,
          valueText = state.alpha.toString(),
          brush = Brush.horizontalGradient(listOf(Color(current).copy(alpha = 0f), Color(current).copy(alpha = 1f))),
          checker = true,
          enabled = enabled,
          onFraction = {
            state.alpha = (it * 255f).roundToInt()
            emitHsv()
          },
        )
      }
    } else {
      val r = AndroidColor.red(current)
      val g = AndroidColor.green(current)
      val b = AndroidColor.blue(current)
      val a = if (showAlpha) state.alpha else 255
      GradientBar(
        label = stringResource(R.string.player_sheets_sub_color_red),
        fraction = r / 255f,
        valueText = r.toString(),
        brush = Brush.horizontalGradient(listOf(Color(0, g, b), Color(255, g, b))),
        enabled = enabled,
        onFraction = { emitExact(AndroidColor.argb(a, (it * 255f).roundToInt(), g, b)) },
      )
      GradientBar(
        label = stringResource(R.string.player_sheets_sub_color_green),
        fraction = g / 255f,
        valueText = g.toString(),
        brush = Brush.horizontalGradient(listOf(Color(r, 0, b), Color(r, 255, b))),
        enabled = enabled,
        onFraction = { emitExact(AndroidColor.argb(a, r, (it * 255f).roundToInt(), b)) },
      )
      GradientBar(
        label = stringResource(R.string.player_sheets_sub_color_blue),
        fraction = b / 255f,
        valueText = b.toString(),
        brush = Brush.horizontalGradient(listOf(Color(r, g, 0), Color(r, g, 255))),
        enabled = enabled,
        onFraction = { emitExact(AndroidColor.argb(a, r, g, (it * 255f).roundToInt())) },
      )
      if (showAlpha) {
        GradientBar(
          label = stringResource(R.string.player_sheets_sub_color_alpha),
          fraction = a / 255f,
          valueText = a.toString(),
          brush = Brush.horizontalGradient(listOf(Color(r, g, b, 0), Color(r, g, b, 255))),
          checker = true,
          enabled = enabled,
          onFraction = { emitExact(AndroidColor.argb((it * 255f).roundToInt(), r, g, b)) },
        )
      }
    }

    if (swatches.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        swatches.forEach { swatch ->
          val opaque = AndroidColor.rgb(AndroidColor.red(swatch), AndroidColor.green(swatch), AndroidColor.blue(swatch))
          val pick = if (showAlpha) (opaque and 0x00FFFFFF) or (state.alpha shl 24) else opaque
          ColorSwatch(
            color = swatch,
            size = 34.dp,
            selected = (current and 0x00FFFFFF) == (swatch and 0x00FFFFFF),
            enabled = enabled,
            onClick = { emitExact(pick) },
          )
        }
      }
    }
  }
}

@Composable
private fun ColorSwatch(
  color: Int,
  size: Dp,
  selected: Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
      .border(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
      .alpha(if (enabled) 1f else 0.5f),
  ) {
    if (AndroidColor.alpha(color) < 255) CheckerBackground(Modifier.matchParentSize())
    Box(Modifier.matchParentSize().background(Color(color)))
  }
}

@Composable
private fun CheckerBackground(modifier: Modifier = Modifier, cell: Dp = 6.dp) {
  val cellPx = with(LocalDensity.current) { cell.toPx() }
  Canvas(modifier) {
    drawRect(Color(0xFFFFFFFF))
    val columns = (size.width / cellPx).toInt() + 1
    val rows = (size.height / cellPx).toInt() + 1
    for (row in 0 until rows) {
      for (column in 0 until columns) {
        if ((row + column) % 2 == 1) {
          drawRect(Color(0xFFCCCCCC), Offset(column * cellPx, row * cellPx), Size(cellPx, cellPx))
        }
      }
    }
  }
}

@Composable
private fun HueSaturationWheel(
  hue: Float,
  sat: Float,
  value: Float,
  thumbColor: Color,
  enabled: Boolean,
  maxSize: Dp,
  onChange: (hue: Float, sat: Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hueLabel = stringResource(R.string.color_picker_tab_wheel)
  val thumbRadiusPx = with(LocalDensity.current) { 12.dp.toPx() }
  val hueColors = remember {
    (0..12).map { Color(AndroidColor.HSVToColor(floatArrayOf((it * 30f) % 360f, 1f, 1f))) }
  }
  BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val side = if (maxWidth < maxSize) maxWidth else maxSize
    Canvas(
      modifier = Modifier
        .size(side)
        .aspectRatio(1f)
        .alpha(if (enabled) 1f else 0.5f)
        .semantics { contentDescription = hueLabel }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          fun update(position: Offset) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f - thumbRadiusPx
            val dx = position.x - center.x
            val dy = position.y - center.y
            val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            onChange((angle + 360f) % 360f, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
          }
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            update(down.position)
            drag(down.id) {
              it.consume()
              update(it.position)
            }
          }
        },
    ) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val radius = min(size.width, size.height) / 2f - thumbRadiusPx
      drawCircle(Brush.sweepGradient(hueColors, center), radius, center)
      drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius, center)
      drawCircle(Color.Black.copy(alpha = 1f - value), radius, center)
      val rad = Math.toRadians(hue.toDouble())
      val thumb = Offset(center.x + (cos(rad) * radius * sat).toFloat(), center.y + (sin(rad) * radius * sat).toFloat())
      drawCircle(thumbColor, thumbRadiusPx - 2f, thumb)
      drawCircle(Color.White, thumbRadiusPx, thumb, style = Stroke(width = 3f))
      drawCircle(Color.Black.copy(alpha = 0.35f), thumbRadiusPx + 1.5f, thumb, style = Stroke(width = 1.5f))
    }
  }
}

/** A horizontal track painted with [brush], dragged like a slider. Arrow keys nudge it for TV and keyboards. */
@Composable
private fun GradientBar(
  label: String,
  fraction: Float,
  valueText: String,
  brush: Brush,
  enabled: Boolean,
  onFraction: (Float) -> Unit,
  modifier: Modifier = Modifier,
  checker: Boolean = false,
) {
  val scheme = MaterialTheme.colorScheme
  val thumbRadiusPx = with(LocalDensity.current) { 13.dp.toPx() }
  Column(modifier = modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.labelMedium)
      Text(valueText, style = MaterialTheme.typography.labelMedium)
    }
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(32.dp)
        .alpha(if (enabled) 1f else 0.5f)
        .semantics {
          contentDescription = label
          stateDescription = valueText
          progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
          setProgress { target ->
            if (enabled) onFraction(target.coerceIn(0f, 1f))
            enabled
          }
        }
        .focusable(enabled)
        .onKeyEvent { event ->
          if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
          when (event.key) {
            Key.DirectionLeft -> { onFraction((fraction - 1f / 64f).coerceIn(0f, 1f)); true }
            Key.DirectionRight -> { onFraction((fraction + 1f / 64f).coerceIn(0f, 1f)); true }
            else -> false
          }
        }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          fun update(x: Float) {
            val span = (size.width - 2 * thumbRadiusPx).coerceAtLeast(1f)
            onFraction(((x - thumbRadiusPx) / span).coerceIn(0f, 1f))
          }
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            update(down.position.x)
            drag(down.id) {
              it.consume()
              update(it.position.x)
            }
          }
        },
      contentAlignment = Alignment.CenterStart,
    ) {
      Box(
        Modifier
          .fillMaxWidth()
          .height(20.dp)
          .clip(RoundedCornerShape(10.dp))
          .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp)),
      ) {
        if (checker) CheckerBackground(Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(brush))
      }
      Canvas(Modifier.fillMaxWidth().height(32.dp)) {
        val span = (size.width - 2 * thumbRadiusPx).coerceAtLeast(1f)
        val thumb = Offset(thumbRadiusPx + span * fraction.coerceIn(0f, 1f), size.height / 2f)
        drawCircle(Color.White, thumbRadiusPx, thumb)
        drawCircle(Color.Black.copy(alpha = 0.35f), thumbRadiusPx, thumb, style = Stroke(width = 1.5f))
      }
    }
  }
}

internal fun Int.toPickerHex(withAlpha: Boolean): String =
  if (withAlpha) "%08X".format(this) else "%06X".format(this and 0x00FFFFFF)

/** Six digits keep [currentAlpha]; eight digits are AARRGGBB. Anything else is not a colour yet. */
internal fun parsePickerHex(digits: String, currentAlpha: Int, allowAlpha: Boolean): Int? {
  val clean = digits.trim().removePrefix("#")
  return when {
    clean.length == 6 -> clean.toLongOrNull(16)?.let { rgb ->
      ((if (allowAlpha) currentAlpha else 255) shl 24) or rgb.toInt()
    }
    clean.length == 8 && allowAlpha -> clean.toLongOrNull(16)?.toInt()
    else -> null
  }
}
