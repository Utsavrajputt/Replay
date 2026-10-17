/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.gyrolet.mpvrx.MainActivity
import app.gyrolet.mpvrx.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * A launcher icon with any colours: a solid or gradient backdrop under the ribbon logo in any colour.
 * Colours are ARGB ints. Android launcher icons are fixed at build time, so these are drawn at runtime and
 * handed to the launcher as a pinned shortcut (see [CustomLauncherIcons.pin]).
 */
@Serializable
internal data class CustomLauncherIcon(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val bgStart: Int = 0xFF000000.toInt(),
  val bgEnd: Int = 0xFF6750A4.toInt(),
  val gradient: Boolean = false,
  val radial: Boolean = false,
  /** Direction of a linear gradient in degrees: 0 runs left to right, 90 top to bottom. */
  val angle: Int = 135,
  val logo: Int = 0xFFFFFFFF.toInt(),
  /** False draws the ribbon, true the original classic mark. Both are single-colour shapes tinted by [logo]. */
  val classic: Boolean = false,
) {
  val logoRes: Int get() = if (classic) R.drawable.ic_launcher_classic_foreground else R.drawable.ic_launcher_white_foreground

  /** Compose brush for previews, filling a box of [size] px. */
  fun brush(size: Float): Brush {
    if (!gradient) return Brush.linearGradient(listOf(Color(bgStart), Color(bgStart)))
    if (radial) {
      return Brush.radialGradient(listOf(Color(bgStart), Color(bgEnd)), Offset(size / 2f, size / 2f), size * 0.75f)
    }
    val (from, to) = linearEnds(size, angle)
    return Brush.linearGradient(listOf(Color(bgStart), Color(bgEnd)), from, to)
  }

  companion object {
    /** Black with a white ribbon, ready to recolour. */
    val BlackPreset = CustomLauncherIcon(name = "", bgStart = 0xFF000000.toInt(), logo = 0xFFFFFFFF.toInt())

    /** White with a black ribbon. */
    val WhitePreset = CustomLauncherIcon(name = "", bgStart = 0xFFFFFFFF.toInt(), logo = 0xFF000000.toInt())

    val presets: List<CustomLauncherIcon> = listOf(
      BlackPreset,
      WhitePreset,
      CustomLauncherIcon(name = "", bgStart = 0xFF1C1B1F.toInt(), logo = 0xFFD0BCFF.toInt()),
      CustomLauncherIcon(
        name = "", bgStart = 0xFF6750A4.toInt(), bgEnd = 0xFF2A1B5E.toInt(), gradient = true, angle = 135,
        logo = 0xFFFFFFFF.toInt(),
      ),
      CustomLauncherIcon(
        name = "", bgStart = 0xFFFF9A44.toInt(), bgEnd = 0xFFE91E63.toInt(), gradient = true, angle = 120,
        logo = 0xFFFFFFFF.toInt(),
      ),
      CustomLauncherIcon(
        name = "", bgStart = 0xFF00C6FF.toInt(), bgEnd = 0xFF0072FF.toInt(), gradient = true, angle = 135,
        logo = 0xFFFFFFFF.toInt(),
      ),
      CustomLauncherIcon(
        name = "", bgStart = 0xFF43E97B.toInt(), bgEnd = 0xFF0B5D3B.toInt(), gradient = true, radial = true,
        logo = 0xFF000000.toInt(),
      ),
    )
  }
}

/** Start and end points of a linear gradient across a square of [size], through its centre at [angle] degrees. */
private fun linearEnds(size: Float, angle: Int): Pair<Offset, Offset> {
  val rad = Math.toRadians(angle.toDouble())
  val half = size / 2f
  val dx = (cos(rad) * half).toFloat()
  val dy = (sin(rad) * half).toFloat()
  return Offset(half - dx, half - dy) to Offset(half + dx, half + dy)
}

internal object CustomLauncherIcons {
  private val json = Json { ignoreUnknownKeys = true }

  fun parse(serialized: String): List<CustomLauncherIcon> =
    if (serialized.isBlank()) emptyList() else runCatching { json.decodeFromString<List<CustomLauncherIcon>>(serialized) }
      .getOrDefault(emptyList())

  fun serialize(icons: List<CustomLauncherIcon>): String = json.encodeToString(icons)

  fun upsert(icons: List<CustomLauncherIcon>, icon: CustomLauncherIcon): List<CustomLauncherIcon> =
    if (icons.any { it.id == icon.id }) icons.map { if (it.id == icon.id) icon else it } else icons + icon

  /**
   * Draws the icon as a full-bleed 108dp adaptive layer (4 px per dp): the launcher masks it to its own shape and
   * keeps the middle 72dp, which is where the ribbon vector is already sized to sit.
   */
  fun render(context: Context, icon: CustomLauncherIcon, sizePx: Int = 432): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val size = sizePx.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = when {
      !icon.gradient -> null
      icon.radial -> RadialGradient(size / 2f, size / 2f, size * 0.75f, icon.bgStart, icon.bgEnd, Shader.TileMode.CLAMP)
      else -> {
        val (from, to) = linearEnds(size, icon.angle)
        LinearGradient(from.x, from.y, to.x, to.y, icon.bgStart, icon.bgEnd, Shader.TileMode.CLAMP)
      }
    }
    if (!icon.gradient) paint.color = icon.bgStart
    canvas.drawRect(0f, 0f, size, size, paint)

    ContextCompat.getDrawable(context, icon.logoRes)?.mutate()?.let { logo ->
      logo.setTint(icon.logo)
      logo.setBounds(0, 0, sizePx, sizePx)
      logo.draw(canvas)
    }
    return bitmap
  }

  fun isPinSupported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

  /**
   * Asks the launcher to put the icon on the home screen. The same id updates a shortcut that is already pinned,
   * so editing an icon and pinning again recolours it in place. Returns false when the launcher cannot pin.
   */
  fun pin(context: Context, icon: CustomLauncherIcon): Boolean {
    if (!isPinSupported(context)) return false
    val label = icon.name.ifBlank { context.getString(R.string.app_name) }
    val launch = Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java)
    val shortcut = ShortcutInfoCompat.Builder(context, "custom_icon_${icon.id}")
      .setShortLabel(label)
      .setLongLabel(label)
      .setIcon(IconCompat.createWithAdaptiveBitmap(render(context, icon)))
      .setIntent(launch)
      .build()
    return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
  }

  /** Recolours an already-pinned shortcut without asking again. Quietly does nothing when it is not pinned. */
  fun refreshPinned(context: Context, icon: CustomLauncherIcon) {
    val id = "custom_icon_${icon.id}"
    val label = icon.name.ifBlank { context.getString(R.string.app_name) }
    runCatching {
      val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
      if (pinned.none { it.id == id }) return
      val shortcut = ShortcutInfoCompat.Builder(context, id)
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(IconCompat.createWithAdaptiveBitmap(render(context, icon)))
        .setIntent(Intent(Intent.ACTION_MAIN).setClass(context, MainActivity::class.java))
        .build()
      ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
    }
  }
}
