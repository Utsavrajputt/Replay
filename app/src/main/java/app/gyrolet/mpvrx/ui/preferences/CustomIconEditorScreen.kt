/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.graphics.PorterDuff
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.components.AppSlider
import app.gyrolet.mpvrx.ui.components.ColorPicker
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.util.UUID
import kotlin.math.roundToInt

private enum class IconColorTarget { Start, End, Logo }

/** Builds a launcher icon from any colours: solid or gradient backdrop, any ribbon colour. */
@Serializable
data class CustomIconEditorScreen(
  val iconId: String? = null,
) : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()
    val serialized by preferences.customLauncherIcons.collectAsState()
    val saved = remember(serialized) { CustomLauncherIcons.parse(serialized) }
    val existing = remember(iconId) { saved.firstOrNull { it.id == iconId } }
    val seed = existing ?: CustomLauncherIcon.BlackPreset
    val defaultName = stringResource(R.string.custom_icon_default_name)

    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "$defaultName ${saved.size + 1}") }
    var bgStart by rememberSaveable { mutableIntStateOf(seed.bgStart) }
    var bgEnd by rememberSaveable { mutableIntStateOf(seed.bgEnd) }
    var gradient by rememberSaveable { mutableStateOf(seed.gradient) }
    var radial by rememberSaveable { mutableStateOf(seed.radial) }
    var angle by rememberSaveable { mutableIntStateOf(seed.angle) }
    var logo by rememberSaveable { mutableIntStateOf(seed.logo) }
    var target by rememberSaveable { mutableStateOf(IconColorTarget.Start) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val activeTarget = if (!gradient && target == IconColorTarget.End) IconColorTarget.Start else target
    val draft = CustomLauncherIcon(
      id = id,
      name = name.trim().ifBlank { defaultName },
      bgStart = bgStart,
      bgEnd = bgEnd,
      gradient = gradient,
      radial = radial,
      angle = angle,
      logo = logo,
    )

    fun applyPreset(preset: CustomLauncherIcon) {
      bgStart = preset.bgStart
      bgEnd = preset.bgEnd
      gradient = preset.gradient
      radial = preset.radial
      angle = preset.angle
      logo = preset.logo
    }

    fun save() {
      preferences.customLauncherIcons.set(CustomLauncherIcons.serialize(CustomLauncherIcons.upsert(saved, draft)))
      CustomLauncherIcons.refreshPinned(context, draft)
    }

    Scaffold(
      topBar = {
        SettingsTopAppBar(
          title = stringResource(
            if (existing == null) R.string.custom_icon_editor_new else R.string.custom_icon_editor_edit,
          ),
          onBack = { backStack.popSafely() },
        )
      },
    ) { padding ->
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          IconPreviewCard {
            CustomIconPreview(draft, 132.dp)
            Text(
              text = draft.name,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }

          EditorSection(stringResource(R.string.custom_icon_presets)) {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              CustomLauncherIcon.presets.forEach { preset ->
                CustomIconPreview(preset, 56.dp, Modifier.clickable { applyPreset(preset) })
              }
            }
          }

          EditorSection(stringResource(R.string.custom_icon_section_background)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = !gradient,
                onClick = { gradient = false },
                label = { Text(stringResource(R.string.custom_icon_bg_solid)) },
              )
              FilterChip(
                selected = gradient,
                onClick = { gradient = true },
                label = { Text(stringResource(R.string.custom_icon_bg_gradient)) },
              )
              if (gradient) {
                FilterChip(
                  selected = !radial,
                  onClick = { radial = false },
                  label = { Text(stringResource(R.string.custom_icon_gradient_linear)) },
                )
                FilterChip(
                  selected = radial,
                  onClick = { radial = true },
                  label = { Text(stringResource(R.string.custom_icon_gradient_radial)) },
                )
              }
            }
            if (gradient) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (!radial) {
                  Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                      Text(stringResource(R.string.custom_icon_angle), style = MaterialTheme.typography.labelMedium)
                      Text("$angle°", style = MaterialTheme.typography.labelMedium)
                    }
                    AppSlider(
                      value = angle.toFloat(),
                      onValueChange = { angle = it.roundToInt().coerceIn(0, 360) },
                      valueRange = 0f..360f,
                      modifier = Modifier.fillMaxWidth(),
                    )
                  }
                } else {
                  Box(Modifier.weight(1f))
                }
                IconButton(onClick = {
                  val swap = bgStart
                  bgStart = bgEnd
                  bgEnd = swap
                }) {
                  Icon(Icons.RoundedFilled.SwapVert, stringResource(R.string.custom_icon_swap))
                }
              }
            }
          }

          EditorSection(stringResource(R.string.custom_icon_section_colors)) {
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              TargetChip(
                selected = activeTarget == IconColorTarget.Start,
                color = bgStart,
                label = stringResource(
                  if (gradient) R.string.custom_icon_target_start else R.string.custom_icon_target_background,
                ),
                onClick = { target = IconColorTarget.Start },
              )
              if (gradient) {
                TargetChip(
                  selected = activeTarget == IconColorTarget.End,
                  color = bgEnd,
                  label = stringResource(R.string.custom_icon_target_end),
                  onClick = { target = IconColorTarget.End },
                )
              }
              TargetChip(
                selected = activeTarget == IconColorTarget.Logo,
                color = logo,
                label = stringResource(R.string.custom_icon_target_logo),
                onClick = { target = IconColorTarget.Logo },
              )
            }
            ColorPicker(
              color = when (activeTarget) {
                IconColorTarget.Start -> bgStart
                IconColorTarget.End -> bgEnd
                IconColorTarget.Logo -> logo
              },
              onColorChange = { picked ->
                val opaque = picked or (0xFF shl 24)
                when (activeTarget) {
                  IconColorTarget.Start -> bgStart = opaque
                  IconColorTarget.End -> bgEnd = opaque
                  IconColorTarget.Logo -> logo = opaque
                }
              },
            )
          }

          OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(30) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.custom_icon_name)) },
            singleLine = true,
          )

          Text(
            text = stringResource(R.string.custom_icon_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Button(
            onClick = {
              save()
              if (CustomLauncherIcons.pin(context, draft)) {
                Toast.makeText(context, R.string.custom_icon_pin_requested, Toast.LENGTH_LONG).show()
              } else {
                Toast.makeText(context, R.string.custom_icon_pin_unsupported, Toast.LENGTH_LONG).show()
              }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
          ) { Text(stringResource(R.string.custom_icon_add_home), style = MaterialTheme.typography.titleMedium) }

          OutlinedButton(
            onClick = {
              save()
              backStack.popSafely()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
          ) { Text(stringResource(R.string.custom_icon_save), style = MaterialTheme.typography.titleMedium) }

          if (existing != null) {
            TextButton(
              onClick = { confirmDelete = true },
              modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.custom_icon_delete), color = MaterialTheme.colorScheme.error) }
          }
        }
      }
    }

    if (confirmDelete) {
      AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.custom_icon_delete_title)) },
        text = { Text(stringResource(R.string.custom_icon_delete_message, draft.name)) },
        confirmButton = {
          TextButton(
            onClick = {
              confirmDelete = false
              preferences.customLauncherIcons.set(CustomLauncherIcons.serialize(saved.filterNot { it.id == id }))
              backStack.popSafely()
            },
          ) { Text(stringResource(R.string.custom_icon_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
          TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
        },
      )
    }
  }
}

/** Saved custom icons plus a "new" tile, shown under the preset builder on the App icon screen. */
@Composable
internal fun CustomIconSection(onCreate: () -> Unit, onEdit: (String) -> Unit) {
  val preferences = koinInject<AppearancePreferences>()
  val serialized by preferences.customLauncherIcons.collectAsState()
  val icons = remember(serialized) { CustomLauncherIcons.parse(serialized) }
  EditorSection(
    title = stringResource(R.string.custom_icon_section_title),
    supporting = stringResource(R.string.custom_icon_section_hint),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      val newLabel = stringResource(R.string.custom_icon_new)
      Column(
        modifier = Modifier.widthIn(max = 72.dp).clickable(onClick = onCreate),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .semantics { contentDescription = newLabel },
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.RoundedFilled.Add, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(newLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
      }
      icons.forEach { icon ->
        Column(
          modifier = Modifier.widthIn(max = 72.dp).clickable { onEdit(icon.id) },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          CustomIconPreview(icon, 56.dp)
          Text(icon.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun EditorSection(title: String, supporting: String? = null, content: @Composable () -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
      )
      if (supporting != null) {
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
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

/** The icon as a launcher would show it: backdrop, then the ribbon in its colour. */
@Composable
internal fun CustomIconPreview(icon: CustomLauncherIcon, size: Dp, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(size * 0.3f))
      .drawBehind { drawRect(icon.brush(this.size.width)) },
    contentAlignment = Alignment.Center,
  ) {
    // The ribbon layer is 108dp with the visible part in its middle 72dp, so it is drawn 1.5x and cropped.
    AndroidView(
      modifier = Modifier.requiredSize(size * 1.5f),
      factory = { context ->
        ImageView(context).apply {
          scaleType = ImageView.ScaleType.FIT_XY
          setImageResource(R.drawable.ic_launcher_white_foreground)
          setColorFilter(icon.logo, PorterDuff.Mode.SRC_IN)
        }
      },
      update = { it.setColorFilter(icon.logo, PorterDuff.Mode.SRC_IN) },
    )
  }
}
