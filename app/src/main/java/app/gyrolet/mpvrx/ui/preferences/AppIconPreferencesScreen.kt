/* SPDX-License-Identifier: AGPL-3.0-or-later */
package app.gyrolet.mpvrx.ui.preferences

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.theme.appPressFeedback
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.theme.AppConnectedShapeTokens
import app.gyrolet.mpvrx.ui.theme.ConnectedLayout
import app.gyrolet.mpvrx.ui.theme.connectedShape
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.navigateTo
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Launcher icon builder: a live preview, a swipeable row of backgrounds and the logos that read on
 * the chosen background. Nothing changes on the launcher until the pick is applied and confirmed.
 */
@Serializable
object AppIconPreferencesScreen : Screen {
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    var current by remember {
      mutableStateOf(runCatching { LauncherIconManager.current(context) }.getOrDefault(LauncherIcon.Default))
    }
    var pending by remember { mutableStateOf(current) }
    val pager = rememberPagerState(initialPage = pending.background.ordinal) { IconBackground.entries.size }
    var confirming by remember { mutableStateOf<LauncherIcon?>(null) }
    val visibleChoice = LauncherIcon.resolve(IconBackground.entries[pager.currentPage], pending.mark)
    val canApply = visibleChoice != current && !pager.isScrollInProgress
    val applyInteractionSource = remember { MutableInteractionSource() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
      current = runCatching { LauncherIconManager.current(context) }.getOrDefault(current)
    }

    Scaffold(
      topBar = {
        SettingsTopAppBar(
          title = stringResource(R.string.pref_app_icon_title),
          onBack = { backstack.popSafely() },
        )
      },
    ) { padding ->
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
          modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(
              start = 16.dp,
              end = 16.dp,
              top = 12.dp,
              bottom = 24.dp,
            ),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          IconBuilder(pending = pending, current = current, pager = pager, onPick = { pending = it })
          Button(
            onClick = {
              // Read the visible page directly: the preview's snapshotFlow may still be catching up.
              // Freeze this choice so a late pager emission cannot change the confirmation target.
              if (!pager.isScrollInProgress) {
                confirming = LauncherIcon.resolve(IconBackground.entries[pager.currentPage], pending.mark)
                  .takeIf { it != current }
              }
            },
            enabled = canApply,
            interactionSource = applyInteractionSource,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
              .appPressFeedback(applyInteractionSource, enabled = canApply),
          ) {
            Text(stringResource(R.string.app_icon_apply), style = MaterialTheme.typography.titleMedium)
          }
          CustomIconSection(
            onCreate = { backstack.navigateTo(CustomIconEditorScreen()) },
            onEdit = { backstack.navigateTo(CustomIconEditorScreen(it)) },
          )
        }
      }
    }

    confirming?.let { target ->
      AlertDialog(
        onDismissRequest = { confirming = null },
        title = { Text(stringResource(R.string.app_icon_confirm_title)) },
        text = { Text(stringResource(R.string.app_icon_confirm_message, iconName(target))) },
        confirmButton = {
          TextButton(
            onClick = {
              confirming = null
              try {
                LauncherIconManager.select(context, target)
                current = target
                pending = target
              } catch (_: Exception) {
                Toast.makeText(context, R.string.app_icon_change_failed, Toast.LENGTH_LONG).show()
                current = runCatching { LauncherIconManager.current(context) }.getOrDefault(current)
              }
            },
          ) { Text(stringResource(R.string.app_icon_confirm_action)) }
        },
        dismissButton = {
          TextButton(onClick = { confirming = null }) { Text(stringResource(android.R.string.cancel)) }
        },
      )
    }
  }
}

/** Name of an icon: its background, plus the logo when that is not the one the background leads with. */
@Composable
private fun iconName(icon: LauncherIcon): String {
  val background = stringResource(icon.background.label)
  if (icon.mark == LauncherIcon.marksFor(icon.background).first()) return background
  return stringResource(R.string.app_icon_name_combined, background, stringResource(icon.mark.label))
}

@Composable
private fun IconBuilder(pending: LauncherIcon, current: LauncherIcon, pager: PagerState, onPick: (LauncherIcon) -> Unit) {
  val backgrounds = IconBackground.entries
  val scope = rememberCoroutineScope()
  val latest by rememberUpdatedState(pending)
  val reduceMotion = AppMotion.shouldReduceMotion()

  // The pager owns the background: a swipe on it or on the preview, or a tap on a swatch, all end up here.
  // A logo that does not read on the new background gives way to that background's own.
  LaunchedEffect(pager) {
    snapshotFlow { pager.currentPage }.collect { page ->
      val background = backgrounds[page]
      if (background != latest.background) onPick(LauncherIcon.resolve(background, latest.mark))
    }
  }

  fun turn(step: Int) {
    val target = (pager.currentPage + step).coerceIn(0, backgrounds.lastIndex)
    scope.launch {
      if (reduceMotion) pager.scrollToPage(target) else pager.animateScrollToPage(target)
    }
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    IconPreviewCard {
      IconPreview(
        background = pending.background,
        mark = pending.mark,
        size = 132.dp,
        modifier = Modifier.pointerInput(Unit) {
          var travelled = 0f
          detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = { if (abs(travelled) > 48.dp.toPx()) turn(if (travelled < 0f) 1 else -1) },
          ) { change, delta ->
            change.consume()
            travelled += delta
          }
        },
      )

      AnimatedContent(
        targetState = iconName(pending),
        transitionSpec = {
          val transition = if (reduceMotion) {
            fadeIn(snap()) togetherWith fadeOut(snap())
          } else {
            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
          }
          transition.using(null)
        },
        label = "iconName",
      ) { name ->
        Text(
          text = name,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      Text(
        text = stringResource(if (pending == current) R.string.app_icon_current else R.string.app_icon_pending),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
          .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val swatch = 56.dp
      val gap = 16.dp
      HorizontalPager(
        state = pager,
        pageSize = PageSize.Fixed(swatch + gap),
        // Centres the current swatch and lets its neighbours peek in from both sides
        contentPadding = PaddingValues(horizontal = (maxWidth - swatch - gap) / 2),
        modifier = Modifier.fillMaxWidth().selectableGroup(),
      ) { page ->
        val background = backgrounds[page]
        val distance = abs(page - pager.currentPage - pager.currentPageOffsetFraction).coerceAtMost(2f)
        val label = stringResource(background.label)
        val selected = background == pending.background
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
              val factor = if (reduceMotion) 1f else 1f - 0.18f * distance
              scaleX = factor
              scaleY = factor
              alpha = 1f - 0.25f * distance
            }
            .clip(RoundedCornerShape(24.dp))
            .selectable(selected = selected, role = Role.RadioButton) {
              scope.launch {
                if (reduceMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page)
              }
            }
            .semantics { contentDescription = label }
            .padding(4.dp)
            .border(
              width = 2.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
              shape = RoundedCornerShape(20.dp),
            )
            .padding(4.dp),
          contentAlignment = Alignment.Center,
        ) {
          IconPreview(background, LauncherIcon.resolve(background, pending.mark).mark, swatch)
        }
      }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SectionLabel(stringResource(R.string.app_icon_section_logo), stringResource(R.string.app_icon_logo_hint))
      // Let labels grow with font scale instead of squeezing five choices into one row.
      val fontScale = LocalDensity.current.fontScale
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / (104.dp * fontScale)).toInt().coerceIn(1, IconMark.entries.size)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AppConnectedShapeTokens.spacing)) {
          IconMark.entries.chunked(columns).forEachIndexed { row, marks ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppConnectedShapeTokens.spacing)) {
              marks.forEachIndexed { column, mark ->
                val icon = LauncherIcon.of(pending.background, mark)
                MarkTile(
                  background = pending.background,
                  mark = mark,
                  selected = pending.mark == mark,
                  enabled = icon != null,
                  onClick = { icon?.let(onPick) },
                  shape = connectedShape(row * columns + column, IconMark.entries.size, ConnectedLayout.Grid, columns),
                  modifier = Modifier.weight(1f),
                )
              }
              repeat(columns - marks.size) { Box(Modifier.weight(1f)) }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String, supportingText: String) {
  Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.semantics { heading() },
    )
    Text(text = supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** A logo choice, drawn on the background it would sit on. A logo that would not read there stays off. */
@Composable
private fun MarkTile(
  background: IconBackground,
  mark: IconMark,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  shape: Shape,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val reduceMotion = AppMotion.shouldReduceMotion()
  val selectionTransition = AppMotion.iconChange(reduceMotion)
  val borderColor by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    animationSpec = if (reduceMotion) snap() else AppMotion.Effect.Color,
    label = "iconLogoSelectionBorder",
  )
  val containerColor by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    animationSpec = if (reduceMotion) snap() else AppMotion.Effect.Color,
    label = "iconLogoSelectionSurface",
  )
  val border = if (selected) {
    BorderStroke(2.dp, borderColor)
  } else {
    BorderStroke(1.dp, borderColor)
  }
  Column(
    modifier = modifier
      .appPressFeedback(interactionSource, enabled, pressedScale = 0.96f)
      .clip(shape)
      .background(containerColor)
      .border(border, shape)
      .selectable(
        selected = selected,
        enabled = enabled,
        role = Role.RadioButton,
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple(),
        onClick = onClick,
      )
      .padding(horizontal = 8.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Box(Modifier.alpha(if (enabled) 1f else 0.35f)) {
        IconPreview(background, mark, 44.dp)
      }
      androidx.compose.animation.AnimatedVisibility(
        visible = selected,
        modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
        enter = selectionTransition.targetContentEnter,
        exit = selectionTransition.initialContentExit,
      ) {
        Icon(
          Icons.RoundedFilled.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Text(
      text = stringResource(mark.label),
      style = MaterialTheme.typography.bodySmall,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = (if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
        .copy(alpha = if (enabled) 1f else 0.5f),
      textAlign = TextAlign.Center,
      minLines = 2,
    )
  }
}

/**
 * An icon drawn from its two layers, so a new background fades in under the logo and a new logo
 * pops in over the background. The corners match what a launcher gives the icon.
 */
@Composable
private fun IconPreview(
  background: IconBackground,
  mark: IconMark,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  val reduceMotion = AppMotion.shouldReduceMotion()
  val fill by animateColorAsState(
    colorResource(background.color),
    if (reduceMotion) snap() else tween(350),
    label = "iconBackground",
  )
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(size * 0.3f))
      .background(fill),
    contentAlignment = Alignment.Center,
  ) {
    AnimatedContent(
      targetState = mark,
      transitionSpec = {
        val transition = if (reduceMotion) {
          fadeIn(snap()) togetherWith fadeOut(snap())
        } else {
          (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f)) +
            fadeIn(tween(200))) togetherWith (scaleOut(targetScale = 1.2f) + fadeOut(tween(150)))
        }
        transition.using(null)
      },
      label = "iconMark",
    ) { shown ->
      MarkLayer(shown, size)
    }
  }
}

/**
 * One adaptive foreground layer. A layer is 108dp with the visible icon in its middle 72dp, so it is
 * drawn 1.5 times [size] and cropped by the parent, as a launcher does. The platform ImageView
 * renders the vector, since the ribbon uses gradients Compose's painter does not handle.
 */
@Composable
private fun MarkLayer(mark: IconMark, size: Dp) {
  AndroidView(
    modifier = Modifier.requiredSize(size * 1.5f),
    factory = { context ->
      ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_XY
        setImageResource(mark.layer)
      }
    },
    update = { it.setImageResource(mark.layer) },
  )
}
