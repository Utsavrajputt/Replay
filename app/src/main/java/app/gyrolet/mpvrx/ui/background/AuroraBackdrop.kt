package app.gyrolet.mpvrx.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.gyrolet.mpvrx.ui.celestial.drawCelestialAurora

/** Replay: the Aurora backdrop is Celestial's own ribbon design (see ui/celestial/CelestialEffects.kt). */
internal fun DrawScope.drawAurora(t: Float, tilt: Offset, p: BackdropPalette, path: Path) =
  drawCelestialAurora(t, tilt, p, path)
