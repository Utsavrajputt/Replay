package app.gyrolet.mpvrx.ui.browser.fab

import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Detects a long press on the initial pointer pass. A normal tap is left untouched (the FAB's own click
 * handler still runs); once the long-press timeout elapses the press is consumed so the FAB does not also
 * register a click when the finger lifts.
 */
@Composable
fun Modifier.fabLongPress(onLongPress: () -> Unit): Modifier {
  val latest = rememberUpdatedState(onLongPress)
  val haptics = LocalHapticFeedback.current
  val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
  return pointerInput(timeout) {
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      val finishedEarly =
        withTimeoutOrNull(timeout) {
          waitForUpOrCancellation(pass = PointerEventPass.Initial)
          true
        }
      if (finishedEarly == null) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        latest.value()
        // Swallow the rest of this press so the FAB doesn't also treat it as a tap.
        do {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
      }
    }
  }
}
