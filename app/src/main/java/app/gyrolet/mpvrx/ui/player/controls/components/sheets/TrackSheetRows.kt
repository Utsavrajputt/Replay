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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.TrackNode
import app.gyrolet.mpvrx.ui.theme.spacing

@Composable
fun AddTrackRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {},
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .heightIn(min = 40.dp)
        .padding(horizontal = 16.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      Icons.RoundedFilled.Add,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium,
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
