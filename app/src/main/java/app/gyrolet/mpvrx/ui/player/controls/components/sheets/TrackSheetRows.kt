/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.player.controls.components.sheets

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.TrackNode
import app.gyrolet.mpvrx.ui.player.controls.components.tvFocusHighlight
import app.gyrolet.mpvrx.ui.theme.spacing
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.liquidglass.LocalPlayerGlassSurfaceActive

/**
 * Shared Material 3 Expressive state colors for player selection sheets.
 * No background panel behind whole option groups; each selectable row owns its surface.
 * A frosted sheet keeps its existing transparency without stacking another blur layer.
 */
@Composable
internal fun playerSheetSelectionColor(
  selected: Boolean,
  frosted: Boolean = LocalPlayerGlassSurfaceActive.current,
): Color {
  // Plain (non-expressive) selection: transparent rows, a soft primary tint when selected.
  val colors = MaterialTheme.colorScheme
  return if (selected) colors.primary.copy(alpha = 0.16f) else Color.Transparent
}

/** Plain rounded row shape (replaces the expressive connected-card corners inside player sheets). */
@Composable
internal fun rememberPlainSheetShape(
  index: Int = 0,
  itemCount: Int = 1,
  layout: app.gyrolet.mpvrx.ui.theme.ConnectedLayout = app.gyrolet.mpvrx.ui.theme.ConnectedLayout.Vertical,
): Shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

/** Every player option gets its own animated Material 3 state surface. */
@Composable
internal fun animatedPlayerSheetSelectionColorState(
  selected: Boolean,
  frosted: Boolean = LocalPlayerGlassSurfaceActive.current,
  label: String = "playerOptionSelection",
): State<Color> {
  val reduceMotion = AppMotion.playerReducedMotion()
  return animateColorAsState(
    targetValue = playerSheetSelectionColor(selected, frosted),
    animationSpec = if (reduceMotion) snap() else AppMotion.Effect.Color,
    label = label,
  )
}

@Composable
internal fun animatedPlayerSheetSelectionColor(
  selected: Boolean,
  frosted: Boolean = LocalPlayerGlassSurfaceActive.current,
  label: String = "playerOptionSelection",
): Color = animatedPlayerSheetSelectionColorState(selected, frosted, label).value

/**
 * Draws the animated selection background directly in the draw phase,
 * avoiding recomposition of children during selection color animations.
 */
@Composable
internal fun Modifier.animatedPlayerSheetBackground(
  selected: Boolean,
  shape: Shape = MaterialTheme.shapes.medium,
  frosted: Boolean = LocalPlayerGlassSurfaceActive.current,
  label: String = "playerOptionSelection",
): Modifier {
  val colorState = animatedPlayerSheetSelectionColorState(selected, frosted, label)
  return this
    .clip(shape)
    .drawBehind {
      drawRect(colorState.value)
    }
}

data class TrackBadge(
  val text: String,
  val emphasized: Boolean = false,
  /** Status flags (Default, Forced, External...) stay small chips; everything else is joined in one line. */
  val flag: Boolean = false,
)

/**
 * Compact one-line track details: "ENG · AAC · mono · 192 kbps" followed by small status chips
 * (Default / Forced / External ...). Replaces the old wrapping chip row.
 */
@Composable
fun TrackBadgeFlow(
  badges: List<TrackBadge>,
  selected: Boolean,
  modifier: Modifier = Modifier,
) {
  if (badges.isEmpty()) return
  val (chips, info) = badges.partition { it.emphasized || it.flag }
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (info.isNotEmpty()) {
      Text(
        text = info.joinToString(" · ") { it.text },
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
    }
    chips.forEach { badge ->
      val containerColor =
        when {
          badge.emphasized -> MaterialTheme.colorScheme.primary
          selected -> MaterialTheme.colorScheme.primaryContainer
          else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
      val contentColor =
        when {
          badge.emphasized -> MaterialTheme.colorScheme.onPrimary
          selected -> MaterialTheme.colorScheme.onPrimaryContainer
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
      Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor,
        contentColor = contentColor,
      ) {
        Text(
          text = badge.text,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddTrackRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = MaterialTheme.shapes.medium,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .tvFocusHighlight(shape, focusedScale = 1.01f)
        .clip(shape)
        .background(playerSheetSelectionColor(false))
        .clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 56.dp)
        .padding(horizontal = 20.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      Icons.RoundedFilled.Add,
      contentDescription = null,
      modifier = Modifier.size(24.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyLargeEmphasized,
      modifier = Modifier.weight(1f),
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions()
    }
  }
}

@Composable
fun getTrackTitle(track: TrackNode): String {
  val title = track.effectiveTitle
  val lang = track.effectiveLang
  val hasTitle = !title.isNullOrBlank()
  val hasLang = !lang.isNullOrBlank()

  if (track.isSubtitle && track.external == true) {
    val fileName =
      track.externalFilename
        ?.takeUnless { it.startsWith("fd://") || it.startsWith("content://") }
        ?.let { Uri.decode(it).substringAfterLast('/').substringAfterLast('\\') }
        ?.takeIf { it.isNotBlank() }
    val externalTitle = title?.takeIf { it.isNotBlank() } ?: fileName
    if (externalTitle != null) {
      return if (hasLang) "$externalTitle ($lang)" else externalTitle
    }
  }

  return when {
    hasTitle && hasLang ->
      stringResource(
        R.string.player_sheets_track_title_w_lang,
        track.id,
        title,
        lang,
      )
    hasTitle -> stringResource(R.string.player_sheets_track_title_wo_lang, track.id, title)
    hasLang -> stringResource(R.string.player_sheets_track_lang_wo_title, track.id, lang)
    !track.codecDesc.isNullOrBlank() -> stringResource(R.string.player_sheets_track_title_wo_lang, track.id, track.codecDesc)
    track.isSubtitle -> stringResource(R.string.player_sheets_chapter_title_substitute_subtitle, track.id)
    track.isAudio -> stringResource(R.string.player_sheets_chapter_title_substitute_audio, track.id)
    else -> ""
  }
}
