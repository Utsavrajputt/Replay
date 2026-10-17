/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.graphics.PorterDuff
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.components.AppSlider
import app.gyrolet.mpvrx.ui.components.ColorPicker
import app.gyrolet.mpvrx.ui.components.themedSegmentedButtonColors
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import kotlin.math.roundToInt

private enum class IconEditorTab { Background, Logo }

private enum class BackgroundTarget { Start, End }

/**
 * Editor shown on the App icon screen when "Custom" is picked. Two tabs, like Video/Audio elsewhere:
 * Background (solid or gradient) and Logo (shape and colour). Saved icons sit above and load into the draft.
 */
@Composable
internal fun CustomIconEditor(
  draft: CustomLauncherIcon,
  onDraftChange: (CustomLauncherIcon) -> Unit,
  saved: List<CustomLauncherIcon>,
  onSave: (CustomLauncherIcon) -> Unit,
  onDelete: (CustomLauncherIcon) -> Unit,
  onNew: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var tab by rememberSaveable { mutableStateOf(IconEditorTab.Background) }
  var target by rememberSaveable { mutableStateOf(BackgroundTarget.Start) }
  val activeTarget = if (!draft.gradient) BackgroundTarget.Start else target
  val isSaved = saved.any { it.id == draft.id }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    EditorSection(stringResource(R.string.custom_icon_saved)) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(
            modifier = Modifier.clickable(onClick = onNew),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Box(
              modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(Icons.RoundedFilled.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
              stringResource(R.string.custom_icon_new),
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              modifier = Modifier.size(width = 64.dp, height = 18.dp),
            )
          }
          saved.forEach { icon ->
            val selected = icon.id == draft.id
            Column(
              modifier = Modifier.clickable { onDraftChange(icon) },
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              CustomIconPreview(
                icon,
                52.dp,
                if (selected) {
                  Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                  Modifier
                },
              )
              Text(
                icon.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.size(width = 64.dp, height = 18.dp),
              )
            }
          }
        }
    }

    EditorSection(stringResource(R.string.custom_icon_presets)) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CustomLauncherIcon.presets.forEach { preset ->
          CustomIconPreview(
            preset,
            52.dp,
            Modifier.clickable {
              onDraftChange(
                draft.copy(
                  bgStart = preset.bgStart,
                  bgEnd = preset.bgEnd,
                  gradient = preset.gradient,
                  radial = preset.radial,
                  angle = preset.angle,
                  logo = preset.logo,
                ),
              )
            },
          )
        }
      }
    }

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      IconEditorTab.entries.forEachIndexed { index, entry ->
        SegmentedButton(
          selected = tab == entry,
          onClick = { tab = entry },
          shape = SegmentedButtonDefaults.itemShape(index, IconEditorTab.entries.size),
          colors = themedSegmentedButtonColors(),
        ) {
          Text(
            stringResource(
              if (entry == IconEditorTab.Background) R.string.custom_icon_tab_background else R.string.custom_icon_tab_logo,
            ),
          )
        }
      }
    }

    if (tab == IconEditorTab.Background) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = !draft.gradient,
          onClick = { onDraftChange(draft.copy(gradient = false)) },
          label = { Text(stringResource(R.string.custom_icon_bg_solid)) },
        )
        FilterChip(
          selected = draft.gradient,
          onClick = { onDraftChange(draft.copy(gradient = true)) },
          label = { Text(stringResource(R.string.custom_icon_bg_gradient)) },
        )
        if (draft.gradient) {
          FilterChip(
            selected = !draft.radial,
            onClick = { onDraftChange(draft.copy(radial = false)) },
            label = { Text(stringResource(R.string.custom_icon_gradient_linear)) },
          )
          FilterChip(
            selected = draft.radial,
            onClick = { onDraftChange(draft.copy(radial = true)) },
            label = { Text(stringResource(R.string.custom_icon_gradient_radial)) },
          )
        }
      }

      if (draft.gradient) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!draft.radial) {
            Column(Modifier.weight(1f)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.custom_icon_angle), style = MaterialTheme.typography.labelMedium)
                Text("${draft.angle}°", style = MaterialTheme.typography.labelMedium)
              }
              AppSlider(
                value = draft.angle.toFloat(),
                onValueChange = { onDraftChange(draft.copy(angle = it.roundToInt().coerceIn(0, 360))) },
                valueRange = 0f..360f,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          } else {
            Box(Modifier.weight(1f))
          }
          IconButton(onClick = { onDraftChange(draft.copy(bgStart = draft.bgEnd, bgEnd = draft.bgStart)) }) {
            Icon(Icons.RoundedFilled.SwapVert, stringResource(R.string.custom_icon_swap))
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TargetChip(
            selected = activeTarget == BackgroundTarget.Start,
            color = draft.bgStart,
            label = stringResource(R.string.custom_icon_target_start),
            onClick = { target = BackgroundTarget.Start },
          )
          TargetChip(
            selected = activeTarget == BackgroundTarget.End,
            color = draft.bgEnd,
            label = stringResource(R.string.custom_icon_target_end),
            onClick = { target = BackgroundTarget.End },
          )
        }
      }

      ColorPicker(
        color = if (activeTarget == BackgroundTarget.Start) draft.bgStart else draft.bgEnd,
        onColorChange = { picked ->
          val opaque = picked or (0xFF shl 24)
          onDraftChange(
            if (activeTarget == BackgroundTarget.Start) draft.copy(bgStart = opaque) else draft.copy(bgEnd = opaque),
          )
        },
      )
    } else {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = !draft.classic,
          onClick = { onDraftChange(draft.copy(classic = false)) },
          label = { Text(stringResource(R.string.custom_icon_logo_ribbon)) },
        )
        FilterChip(
          selected = draft.classic,
          onClick = { onDraftChange(draft.copy(classic = true)) },
          label = { Text(stringResource(R.string.app_icon_logo_classic)) },
        )
      }
      ColorPicker(
        color = draft.logo,
        onColorChange = { picked -> onDraftChange(draft.copy(logo = picked or (0xFF shl 24))) },
      )
    }

    OutlinedTextField(
      value = draft.name,
      onValueChange = { onDraftChange(draft.copy(name = it.take(30))) },
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(R.string.custom_icon_name)) },
      singleLine = true,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(onClick = { onSave(draft) }) {
        Text(stringResource(if (isSaved) R.string.custom_icon_update else R.string.custom_icon_save))
      }
      if (isSaved) {
        TextButton(onClick = { onDelete(draft) }) {
          Text(stringResource(R.string.custom_icon_delete), color = MaterialTheme.colorScheme.error)
        }
      }
    }

    Text(
      text = stringResource(R.string.custom_icon_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
    )
    content()
  }
}

@Composable
private fun TargetChip(selected: Boolean, color: Int, label: String, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    leadingIcon = {
      Box(
        Modifier
          .size(18.dp)
          .clip(CircleShape)
          .background(Color(color))
          .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
      )
    },
  )
}

/** The icon as a launcher would show it: backdrop, then the logo in its colour. */
@Composable
internal fun CustomIconPreview(icon: CustomLauncherIcon, size: Dp, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(size * 0.3f))
      .drawBehind { drawRect(icon.brush(this.size.width)) },
    contentAlignment = Alignment.Center,
  ) {
    // The logo layer is 108dp with the visible part in its middle 72dp, so it is drawn 1.5x and cropped.
    AndroidView(
      modifier = Modifier.requiredSize(size * 1.5f),
      factory = { context ->
        ImageView(context).apply {
          scaleType = ImageView.ScaleType.FIT_XY
          setImageResource(icon.logoRes)
          setColorFilter(icon.logo, PorterDuff.Mode.SRC_IN)
        }
      },
      update = {
        it.setImageResource(icon.logoRes)
        it.setColorFilter(icon.logo, PorterDuff.Mode.SRC_IN)
      },
    )
  }
}
