package com.ngocdevv.nativecook.blocks.onboarding.explodedchipshield

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.onboarding.explodedchipshield.EcsArt.background
import com.ngocdevv.nativecook.blocks.onboarding.explodedchipshield.EcsArt.callouts
import com.ngocdevv.nativecook.blocks.onboarding.explodedchipshield.EcsArt.stack
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.hypot

/*
 Exploded Chip Shield — the security page of the onboarding of Ferrin, an invented password vault
 (a port of ExplodedChipShield.swift).

 Look: a white, light-grey premium screen. An isometric brushed-metal chip with an embossed shield
 and keyhole sits above "Every layer, encrypted.", page dots and a Continue button.
 Hero: the chip explodes into six floating layers (ExplodedChipShieldArt.kt): base plate, mirror-rim
 tray, glowing teal core with a raised disc, frosted glass ring, clear glass sheet printed with a
 circuit, and the lid. They separate with a stagger, hover with a slow bob while the view sways a
 few degrees (the highlights travel), and three callouts draw out of their layers. Then the stack
 collapses, the lid lands last with a small settle bump.

 autoPlay: LOOP = 7 s (EcsModel.timeline); frame 7 s equals frame 0.
 Touch: drag up on the chip to open it (the layers follow the finger), sideways to tilt the view;
 release springs to the nearest state, a tap toggles. 2 s after the last touch it springs closed and
 the loop starts again.
 Reduced motion: no loop; the open stack with its callouts shows still, a tap crossfades it closed.
*/

private class EcsHolder {
    var frame = EcsFrame()
    var shown = 1.0
}

@Composable
fun ExplodedChipShield(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val m = EcsModel
    val reduced = rememberLiveReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screen) / screen.density
    BlockSystemBars(darkIcons = true)
    val driver = remember(auto, reduced) { EcsDriver() }
    val holder = remember { EcsHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val unit = remember { Density(1f, 1f) }
    val titles = remember(measurer) {
        m.callouts.map {
            measurer.measure(it.title, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ecsColor(m.INK)), density = unit)
        }
    }
    val captions = remember(measurer) {
        m.callouts.map {
            measurer.measure(it.caption, TextStyle(fontSize = 12.sp, color = ecsColor(m.CAPTION)), density = unit)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(ecsColor(m.BG_BOTTOM)).clipToBounds()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val g = EcsArt.Geo(w, h, safeTop.toDouble(), h - safeBottom - m.TEXT_BLOCK)

        LaunchedEffect(driver, auto, reduced, wake.intValue) {
            while (true) {
                val idle = withFrameNanos { n ->
                    val now = n / 1e9
                    holder.frame = driver.frame(now, auto, reduced)
                    holder.shown = if (reduced) driver.shownAmount(now) else 1.0
                    tick.intValue += 1
                    !auto && !driver.busy(now, reduced)
                }
                if (idle) break
            }
        }

        fun now() = System.nanoTime() / 1e9
        fun kick() {
            wake.intValue += 1
        }
        fun feel() {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Ferrin security chip: alloy shell, cipher mesh and key enclave."
                    onClick("Open or close the layers") {
                        val t = now()
                        if (reduced) {
                            driver.toggleReduced(t)
                        } else {
                            driver.begin(t, auto)
                            driver.end(0.0, true, t)
                        }
                        kick()
                        true
                    }
                }
                .pointerInput(driver, g.textTop, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val y0 = down.position.y / d
                        val inside = y0 > g.top + 50 && y0 < g.textTop
                        if (!inside) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        if (!reduced) driver.begin(now(), auto)
                        kick()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            last = c.position
                            tracker.addPosition(c.uptimeMillis, c.position)
                            val dx = ((c.position.x - down.position.x) / d).toDouble()
                            val dy = ((c.position.y - down.position.y) / d).toDouble()
                            if (!reduced && hypot(dx, dy) > 6) driver.drag(dx, dy, now())
                            if (!c.pressed) break
                            c.consume()
                        }
                        val moved = hypot((last.x - down.position.x) / d, (last.y - down.position.y) / d)
                        val t = now()
                        if (reduced) {
                            if (moved < 10) {
                                driver.toggleReduced(t)
                                feel()
                            }
                        } else {
                            val vy = (tracker.calculateVelocity().y / d).toDouble()
                            if (driver.end(vy, moved < 8, t)) feel()
                        }
                        kick()
                    }
                },
        ) {
            tick.intValue
            val f = holder.frame
            scale(density, density, pivot = Offset.Zero) {
                if (reduced) {
                    val closed = m.manual(0.0, 0.0, 0.0)
                    background(g, closed)
                    layer(1 - holder.shown, w, h) { stack(g, closed) }
                    layer(holder.shown, w, h) {
                        background(g, f)
                        stack(g, f)
                        callouts(g, f, titles, captions)
                    }
                } else {
                    background(g, f)
                    stack(g, f)
                    callouts(g, f, titles, captions)
                }
            }
        }

        EcsHeader(Modifier.offset(y = (safeTop + 10).dp))
        EcsCopy(Modifier.offset(y = g.textTop.dp))
        EcsFooter(Modifier.offset(y = (h - safeBottom - 16 - 54 - 34).dp)) { feel() }
    }
}

private val layerPaint = Paint()

private inline fun DrawScope.layer(alpha: Double, w: Double, h: Double, block: DrawScope.() -> Unit) {
    if (alpha <= 0.001) return
    layerPaint.alpha = alpha.toFloat().coerceIn(0f, 1f)
    drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w.toFloat(), h.toFloat()), layerPaint) }
    block()
    drawIntoCanvas { it.restore() }
}

@Composable
private fun EcsHeader(modifier: Modifier) {
    val m = EcsModel
    Row(
        modifier.fillMaxWidth().height(32.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp, 21.dp)) {
            val p = EcsArt.shield(size.width.toDouble(), size.height.toDouble()).apply {
                addPath(EcsArt.keyhole(size.width.toDouble(), size.height.toDouble()))
                fillType = PathFillType.EvenOdd
            }
            translate(size.width / 2, size.height / 2) { drawPath(p, ecsColor(m.INK)) }
        }
        Spacer(Modifier.width(8.dp))
        Text(m.BRAND, color = ecsColor(m.INK), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)
        Spacer(Modifier.weight(1f))
        Text(m.SKIP, color = ecsColor(m.GREY), fontSize = 16.sp)
    }
}

@Composable
private fun EcsCopy(modifier: Modifier) {
    val m = EcsModel
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(m.EYEBROW, color = ecsColor(m.TEAL), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
        Text(
            m.TITLE, color = ecsColor(m.INK), fontSize = 34.sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp, lineHeight = 39.sp, modifier = Modifier.padding(top = 10.dp),
        )
        Text(m.BODY, color = ecsColor(m.GREY), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun EcsFooter(modifier: Modifier, onTap: () -> Unit) {
    val m = EcsModel
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(ecsColor(m.DOT_OFF), CircleShape))
            Box(Modifier.size(20.dp, 6.dp).background(ecsColor(m.INK), CircleShape))
            Box(Modifier.size(6.dp).background(ecsColor(m.DOT_OFF), CircleShape))
        }
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(54.dp)
                .scale(if (pressed) 0.97f else 1f)
                .shadow(12.dp, CircleShape, ambientColor = ecsColor(0x14171B, 0.3), spotColor = ecsColor(0x14171B, 0.3))
                .background(ecsColor(m.INK), CircleShape)
                .clickable(interactionSource = press, indication = null) { onTap() },
            contentAlignment = Alignment.Center,
        ) {
            Text(m.CTA, color = ecsColor(0xFFFFFF), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
