package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.math.floor
import kotlin.math.roundToInt

// The page light and widget share an origin from the first layout. The square
// sits 48 points above the safe area's center; the hint is 24 points below it.
@Composable
fun AuraWalletWidgetDemo() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = bars?.isAppearanceLightStatusBars
        val navigation = bars?.isAppearanceLightNavigationBars
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        onDispose {
            if (status != null) bars.isAppearanceLightStatusBars = status
            if (navigation != null) bars.isAppearanceLightNavigationBars = navigation
        }
    }
    val display = LocalDensity.current
    val density = display.density
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val top = insets.getTop(display) / density
    val bottom = insets.getBottom(display) / density
    val reduced = auraReducedMotion()
    val machine = rememberAuraMachine(reduced)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF040116))) {
        val width = auraWidth(maxWidth.value.toDouble())
        val cardX = floor((maxWidth.value - width) / 2)
        val safeHeight = maxHeight.value - top - bottom
        val cardY = floor(top + (safeHeight - width) / 2 - AuraPalette.Page.lift + 0.5)
        AuraPage((maxWidth.value * density).roundToInt(), (maxHeight.value * density).roundToInt(),
            cardX.toFloat(), cardY.toFloat(), width.toFloat(), density)
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f)) {
            AuraWidget(width, machine, Modifier.offset(cardX.dp, cardY.dp))
        }
        AuraHint(machine, Modifier.offset(cardX.dp, (cardY + width + AuraPalette.Page.hintGap).dp).width(width.dp))
    }
}

@Composable
private fun AuraWidget(width: Double, machine: AuraMachine, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val unit = width / AuraPalette.design
    val glass = remember(width, density) { AuraGlass(width, density) }
    val plain = remember { AuraPlainSurface() }
    val portraits = remember(context) { AuraPortraits(context) }
    Box(modifier.size(width.dp).pointerInput(machine) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                for (change in event.changes) {
                    if (change.pressed && !change.previousPressed) machine.touch()
                }
            }
        }
    }) {
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            glass.update(machine.drive)
            if (Build.VERSION.SDK_INT >= 33) {
                drawIntoCanvas { canvas -> glass.draw(canvas.nativeCanvas) }
            } else plain.draw(this, glass.uniforms, machine.drive, size.width)
        }
        Box(Modifier.matchParentSize().graphicsLayer { alpha = machine.drive.shown.value.toFloat() }) {
            AuraBalance(machine, portraits, unit, density)
            AuraActivity(machine, portraits, unit, density)
            AuraLip(machine, portraits, unit, density)
            AuraActions(machine, unit, density)
        }
    }
}
