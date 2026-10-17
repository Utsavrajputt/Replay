/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.preferences

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.gyrolet.mpvrx.ui.components.ColorPicker
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.preferences.components.ThemePreviewCard
import app.gyrolet.mpvrx.ui.player.controls.components.rememberTvInitialFocusRequester
import app.gyrolet.mpvrx.ui.player.controls.components.tvFocusGroup
import app.gyrolet.mpvrx.ui.player.controls.components.tvInitialFocus
import app.gyrolet.mpvrx.ui.theme.CustomThemeDefinition
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class CustomThemeEditorScreen(
  val themeName: String? = null,
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val preferences = koinInject<AppearancePreferences>()
    val serializedThemes by preferences.customTheme.collectAsState()
    val savedThemes = remember(serializedThemes) { CustomThemeDefinition.parseCollection(serializedThemes) }
    val existingTheme = remember(savedThemes, themeName) { savedThemes.firstOrNull { it.name == themeName } }
    val customThemeLabel = stringResource(R.string.pref_appearance_custom_theme_title)
    val defaultThemeName =
      remember(savedThemes, customThemeLabel) {
        generateSequence(1) { it + 1 }
          .map { index -> "$customThemeLabel $index" }
          .first { candidate -> savedThemes.none { it.name.equals(candidate, ignoreCase = true) } }
      }

    var name by rememberSaveable(themeName) { mutableStateOf(existingTheme?.name ?: defaultThemeName) }
    var primaryLight by rememberSaveable(themeName) { mutableStateOf(existingTheme?.primaryLight.toEditorHex("#6750A4")) }
    var primaryDark by rememberSaveable(themeName) { mutableStateOf(existingTheme?.primaryDark.toEditorHex("#D0BCFF")) }
    var secondaryLight by rememberSaveable(themeName) { mutableStateOf(existingTheme?.secondaryLight.toEditorHex("#625B71")) }
    var secondaryDark by rememberSaveable(themeName) { mutableStateOf(existingTheme?.secondaryDark.toEditorHex("#CCC2DC")) }
    var tertiaryLight by rememberSaveable(themeName) { mutableStateOf(existingTheme?.tertiaryLight.toEditorHex("#7D5260")) }
    var tertiaryDark by rememberSaveable(themeName) { mutableStateOf(existingTheme?.tertiaryDark.toEditorHex("#EFB8C8")) }
    var backgroundLight by rememberSaveable(themeName) { mutableStateOf(existingTheme?.backgroundLight.toEditorHex("#FFFBFF")) }
    var backgroundDark by rememberSaveable(themeName) { mutableStateOf(existingTheme?.backgroundDark.toEditorHex("#1C1B1F")) }
    var editorMode by rememberSaveable { mutableStateOf(CustomThemeEditorMode.Light) }
    var showValidationErrors by rememberSaveable { mutableStateOf(false) }
    val editorListState = rememberLazyListState()
    val modeFocusRequester = rememberTvInitialFocusRequester(requestKey = editorMode)

    LaunchedEffect(editorMode) {
      editorListState.scrollToItem(0)
    }

    val parsedPrimaryLight = parseThemeColor(primaryLight)
    val parsedPrimaryDark = parseThemeColor(primaryDark)
    val parsedSecondaryLight = parseThemeColor(secondaryLight)
    val parsedSecondaryDark = parseThemeColor(secondaryDark)
    val parsedTertiaryLight = parseThemeColor(tertiaryLight)
    val parsedTertiaryDark = parseThemeColor(tertiaryDark)
    val parsedBackgroundLight = parseThemeColor(backgroundLight)
    val parsedBackgroundDark = parseThemeColor(backgroundDark)
    val trimmedName = name.trim().replace('|', ' ')
    val duplicateName =
      savedThemes.any { saved ->
        saved.name.equals(trimmedName, ignoreCase = true) && saved.name != themeName
      }
    val lightColorsValid =
      parsedPrimaryLight != null &&
        parsedSecondaryLight != null &&
        parsedTertiaryLight != null &&
        parsedBackgroundLight != null
    val darkColorsValid =
      parsedPrimaryDark != null &&
        parsedSecondaryDark != null &&
        parsedTertiaryDark != null &&
        parsedBackgroundDark != null
    val canSave =
      trimmedName.isNotBlank() &&
        !duplicateName &&
        lightColorsValid &&
        darkColorsValid

    val previewTheme =
      CustomThemeDefinition(
        name = trimmedName.ifBlank { stringResource(R.string.pref_appearance_custom_theme_preview) },
        primaryLight = parsedPrimaryLight ?: Color(0xFF6750A4),
        primaryDark = parsedPrimaryDark ?: Color(0xFFD0BCFF),
        secondaryLight = parsedSecondaryLight ?: Color(0xFF625B71),
        secondaryDark = parsedSecondaryDark ?: Color(0xFFCCC2DC),
        tertiaryLight = parsedTertiaryLight ?: Color(0xFF7D5260),
        tertiaryDark = parsedTertiaryDark ?: Color(0xFFEFB8C8),
        backgroundLight = parsedBackgroundLight ?: Color(0xFFFFFBFF),
        backgroundDark = parsedBackgroundDark ?: Color(0xFF1C1B1F),
      )

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              stringResource(
                if (existingTheme == null) {
                  R.string.pref_appearance_custom_theme_new
                } else {
                  R.string.pref_appearance_custom_theme_edit_title
                },
              ),
            )
          },
          navigationIcon = {
            IconButton(onClick = { backStack.popSafely() }) {
              Icon(Icons.RoundedFilled.ArrowBack, contentDescription = null)
            }
          },
          actions = {
            TextButton(
              onClick = {
                showValidationErrors = true
                if (!canSave) {
                  when {
                    !lightColorsValid -> editorMode = CustomThemeEditorMode.Light
                    !darkColorsValid -> editorMode = CustomThemeEditorMode.Dark
                  }
                  return@TextButton
                }
                val definition =
                  CustomThemeDefinition(
                    name = trimmedName,
                    primaryLight = parsedPrimaryLight,
                    primaryDark = parsedPrimaryDark,
                    secondaryLight = parsedSecondaryLight,
                    secondaryDark = parsedSecondaryDark,
                    tertiaryLight = parsedTertiaryLight,
                    tertiaryDark = parsedTertiaryDark,
                    backgroundLight = parsedBackgroundLight,
                    backgroundDark = parsedBackgroundDark,
                  )
                val updatedThemes =
                  if (existingTheme == null) {
                    savedThemes + definition
                  } else {
                    savedThemes.map { saved -> if (saved.name == existingTheme.name) definition else saved }
                  }
                preferences.customTheme.set(CustomThemeDefinition.serializeCollection(updatedThemes))
                preferences.selectedCustomThemeName.set(definition.name)
                backStack.popSafely()
              },
            ) {
              Text(stringResource(R.string.pref_appearance_custom_theme_save))
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier.fillMaxSize().padding(padding),
      ) {
        PreferenceCard {
          Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = stringResource(R.string.pref_appearance_custom_theme_live_preview),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Row(
              modifier = Modifier.fillMaxWidth().tvFocusGroup(),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
              ThemePreviewCard(
                label = stringResource(R.string.pref_appearance_darkmode_light),
                colorScheme = previewTheme.lightColorScheme(),
                isSelected = editorMode == CustomThemeEditorMode.Light,
                onClick = { editorMode = CustomThemeEditorMode.Light },
                modifier =
                  if (editorMode == CustomThemeEditorMode.Light) {
                    Modifier.tvInitialFocus(modeFocusRequester)
                  } else {
                    Modifier
                  },
              )
              ThemePreviewCard(
                label = stringResource(R.string.pref_appearance_darkmode_dark),
                colorScheme = previewTheme.darkColorScheme(),
                isSelected = editorMode == CustomThemeEditorMode.Dark,
                onClick = { editorMode = CustomThemeEditorMode.Dark },
                modifier =
                  if (editorMode == CustomThemeEditorMode.Dark) {
                    Modifier.tvInitialFocus(modeFocusRequester)
                  } else {
                    Modifier
                  },
              )
            }
          }
        }

        LazyColumn(
          state = editorListState,
          modifier = Modifier.weight(1f).tvFocusGroup(),
          contentPadding = PaddingValues(bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          item {
          PreferenceCard {
            Column(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace("|", "").take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pref_appearance_custom_theme_name)) },
                supportingText = {
                  when {
                    showValidationErrors && trimmedName.isBlank() ->
                      Text(stringResource(R.string.pref_appearance_custom_theme_name_required))
                    showValidationErrors && duplicateName ->
                      Text(stringResource(R.string.pref_appearance_custom_theme_name_duplicate))
                  }
                },
                isError = showValidationErrors && (trimmedName.isBlank() || duplicateName),
                singleLine = true,
              )
            }
          }
        }

          if (editorMode == CustomThemeEditorMode.Light) {
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_light_primary),
                hex = primaryLight,
                onHexChange = { primaryLight = it },
              )
            }
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_light_secondary),
                hex = secondaryLight,
                onHexChange = { secondaryLight = it },
              )
            }
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_light_tertiary),
                hex = tertiaryLight,
                onHexChange = { tertiaryLight = it },
              )
            }
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_light_background),
                hex = backgroundLight,
                onHexChange = { backgroundLight = it },
              )
            }
          } else {
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_dark_primary),
                hex = primaryDark,
                onHexChange = { primaryDark = it },
              )
            }
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_dark_secondary),
                hex = secondaryDark,
                onHexChange = { secondaryDark = it },
              )
            }
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_dark_tertiary),
                hex = tertiaryDark,
                onHexChange = { tertiaryDark = it },
              )
            }
            item {
              ThemeColorEditor(
                title = stringResource(R.string.pref_appearance_custom_theme_dark_background),
                hex = backgroundDark,
                onHexChange = { backgroundDark = it },
              )
            }
          }
        }
      }
    }
  }
}

private enum class CustomThemeEditorMode {
  Light,
  Dark,
}

@Composable
private fun ThemeColorEditor(
  title: String,
  hex: String,
  onHexChange: (String) -> Unit,
) {
  val color = parseThemeColor(hex)
  val argb = color?.toArgb() ?: 0xFF000000.toInt()
  var expanded by rememberSaveable(title) { mutableStateOf(false) }

  PreferenceCard {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { expanded = !expanded },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier
              .size(42.dp)
              .clip(CircleShape)
              .background(color ?: MaterialTheme.colorScheme.errorContainer)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
          Text(
            hex,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          if (expanded) Icons.RoundedFilled.ExpandLess else Icons.RoundedFilled.ExpandMore,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (expanded) {
        ColorPicker(
          color = argb,
          onColorChange = { picked -> onHexChange(picked.toOpaqueHex()) },
        )
      }
    }
  }
}

private fun parseThemeColor(value: String): Color? {
  val normalized = value.trim()
  if (!normalized.matches(Regex("^#[0-9A-Fa-f]{6}$"))) return null
  return runCatching { Color(AndroidColor.parseColor(normalized)) }.getOrNull()
}

private fun Color?.toEditorHex(fallback: String): String = this?.toArgb()?.toOpaqueHex() ?: fallback

private fun Int.toOpaqueHex(): String = "#%06X".format(this and 0x00FFFFFF)

