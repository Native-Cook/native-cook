package com.ngocdevv.nativecook.blocks.charts.treeringchart

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Tree Ring Chart — "Heartwood", a savings app that shows seven years of saving as the cross-section
 of a cut tree, ported from TreeRingChart.tsx. The slice is one AGSL shader (TreeRingChartShader.kt;
 concentric circles below Android 13), the dashed guides, the pool of light and the leader are
 Canvas drawing, the labels are text.

 autoPlay (LOOP = 9 s, TreeRingChartMotion.kt):
   0.0 – 1.4   rest: the full slice, 2021 selected.
   1.4 – 2.5   the rings shrink back to the pith over a faint ghost, outermost first.
   2.5 – 6.4   they regrow year by year with a back-out swell; the newest ring is selected.
   6.4 – 7.4   the selection travels back from 2025 to 2021 and the glow returns.
   7.4 – 9.0   rest.

 Touch: drag across the slice from the pith outward: it grows to the finger (spring 260 ms, no
 bounce), the ring under the finger is selected (a tick per ring); releasing completes that ring
 (spring 450 ms, damping 0.7). The year row scrubs the selection too. 2 s after the last touch the
 slice eases back to rest (800 ms) and the loop starts again. Reduced motion: the rest frame, no
 loop; touches still select, without springs.
 */

private val Ink = Color(0xFFF4E7D4)
private val Muted = Color(0xFFF4E7D4).copy(alpha = 0.58f)
private val AmberC = Color(0xFFF2B86A)
internal val TreeBackground = Color(0xFF1B130E)
private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private const val RETURN_MS = 800
private const val IDLE_MS = 2000L

/** Reanimated's duration-based spring as a Compose spring: stiffness from the period. */
private fun treeSpring(ms: Int, damping: Float): AnimationSpec<Float> {
    val w = 2.0 * PI / (ms / 1000.0)
    return spring(dampingRatio = damping, stiffness = (w * w).toFloat())
}

/** The loop clock, the finger's state and the pose every layer reads. */
private class TreeState(auto: Boolean) {
    val t = mutableFloatStateOf(0f)
    var manual by mutableStateOf(!auto)
    val s = Animatable(RING_COUNT.toFloat())
    val sel = Animatable(REST_SEL.toFloat())
    var picked = -1
    var resume: Job? = null
    private val pose = TreePose()

    /** Reads state: call only inside draw / graphicsLayer lambdas. */
    fun pose(): TreePose = if (manual) treeTouchPose(s.value, sel.value, pose) else treeLoopPose(t.floatValue, pose)
}

@Composable
fun TreeRingChart(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = remember(auto) { TreeState(auto) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(TREE_RING_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val grain = remember { treeGrainTile() }

    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var geo by remember { mutableStateOf<TreeGeo?>(null) }

    LaunchedEffect(state, auto, state.manual) {
        if (!auto || state.manual) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.05f)
                last = now
                state.t.floatValue = (state.t.floatValue + dt) % LOOP
            }
        }
    }

    fun backToRest() {
        scope.launch { state.s.animateTo(RING_COUNT.toFloat(), tween(RETURN_MS, easing = EaseInOut)) }
        scope.launch {
            val r = state.sel.animateTo(REST_SEL.toFloat(), tween(RETURN_MS, easing = EaseInOut))
            if (r.endReason == AnimationEndReason.Finished && auto) {
                state.t.floatValue = 0f
                state.manual = false
                state.picked = -1
            }
        }
    }

    fun takeOver() {
        state.resume?.cancel()
        state.resume = null
        if (!state.manual) {
            val p = treeLoopPose(state.t.floatValue, TreePose())
            val grown = treeGrownOf(p)
            val sel = p.sel
            state.manual = true
            scope.launch { state.s.snapTo(grown) }
            scope.launch { state.sel.snapTo(sel) }
        }
    }

    fun pick(target: Int) {
        if (target == state.picked) return
        state.picked = target
        scope.launch {
            state.sel.animateTo(target.toFloat(), if (reduced) tween(180) else treeSpring(380, 0.82f))
        }
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun followSlice(x: Float, y: Float) {
        val g = geo ?: return
        val reach = treeSOfRadius(hypot(x - (g.cx - g.ox), y - (g.cy - g.oy)), g.pith, g.wood)
        scope.launch { if (reduced) state.s.snapTo(reach) else state.s.animateTo(reach, treeSpring(260, 1f)) }
        pick(max(0, min(RING_COUNT - 1, floor(reach).toInt())))
    }

    fun scheduleResume() {
        if (!auto) return
        state.resume?.cancel()
        state.resume = scope.launch {
            delay(IDLE_MS)
            state.resume = null
            backToRest()
        }
    }

    fun release() {
        val target = max(0, state.picked).toFloat()
        scope.launch {
            if (reduced) state.s.snapTo(target + 1f) else state.s.animateTo(min(RING_COUNT.toFloat(), target + 1f), treeSpring(450, 0.7f))
        }
        scheduleResume()
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(TreeBackground)
            .onGloballyPositioned { root = it },
    ) {
        val screenW = maxWidth.value
        val rowW = screenW - 48f
        val step = rowW / RING_COUNT

        fun followRow(x: Float) {
            scope.launch { if (reduced) state.s.snapTo(RING_COUNT.toFloat()) else state.s.animateTo(RING_COUNT.toFloat(), treeSpring(450, 0.85f)) }
            pick(max(0, min(RING_COUNT - 1, floor(x / step).toInt())))
        }

        // The screen's gradient, its grain and the static guides: redrawn only when the hero moves.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val gradient = Brush.verticalGradient(
                        0f to Color(0xFF2C1E15),
                        0.55f to Color(0xFF21160F),
                        1f to Color(0xFF150E0A),
                    )
                    val grainBrush = ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
                    val g = geo
                    onDrawBehind {
                        drawRect(gradient)
                        drawRect(grainBrush, alpha = 0.06f)
                        val d = density.density
                        scale(d, d, pivot = Offset.Zero) { drawTreeBackdrop(g, size.width / d, size.height / d) }
                    }
                },
        )

        // The slice and the leader: the only layer that redraws per frame.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    val g = geo ?: return@drawBehind
                    val p = state.pose()
                    val radii = treeRingRadii(p, g.pith, g.wood, FloatArray(RING_COUNT))
                    val grownShare = treeClamp01((radii[RING_COUNT - 1] - g.pith) / (g.wood - g.pith))
                    val barkW = g.bark * (0.55f + 0.45f * grownShare)
                    val d = density.density
                    scale(d, d, pivot = Offset.Zero) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                            shader.setFloatUniform("center", g.cx, g.cy)
                            shader.setFloatUniform("rA", radii[0], radii[1], radii[2], radii[3])
                            shader.setFloatUniform("rB", radii[4], radii[5], radii[6], g.pith)
                            shader.setFloatUniform("look", p.sel, p.glow, barkW, g.unit)
                            shader.setFloatUniform("full", g.wood, g.bark)
                            drawRect(brush, Offset(g.ox, g.oy), Size(g.w, g.h))
                        } else {
                            drawTreeFallback(g, radii, p, barkW)
                        }
                        drawTreeLeader(g, treeSelectedMid(radii, g.pith, p.sel), p.glow)
                    }
                },
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 10).dp, bottom = (safeBottom + 12).dp),
        ) {
            // Header.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("HEARTWOOD", style = TextStyle(color = AmberC, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
                    Box(
                        Modifier
                            .height(28.dp)
                            .background(Color(0xFFFFECD2).copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                            .border(Dp.Hairline, Color(0xFFFFECD2).copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("Savings · 2019 – 2025", style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                    }
                }
                BasicText(
                    "7 years of saving",
                    Modifier.padding(top = 4.dp),
                    style = TextStyle(color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TreeOdometer(state, digits = 5, prefix = "$", height = 24.dp, style = TextStyle(color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    BasicText("put away, one ring a year", style = TextStyle(color = Muted, fontSize = 14.sp))
                }
            }

            // The hero: the slice is drawn behind, this box takes the finger. It spans the screen's width.
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 300.dp)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val b = 24.dp.roundToPx()
                        val pl = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * b, maxWidth = constraints.maxWidth + 2 * b))
                        layout(constraints.maxWidth, pl.height) { pl.place(-b, 0) }
                    }
                    .onGloballyPositioned { c ->
                        val r = root ?: return@onGloballyPositioned
                        if (!r.isAttached || !c.isAttached) return@onGloballyPositioned
                        val o = r.localPositionOf(c, Offset.Zero)
                        val d = density.density
                        val ox = o.x / d
                        val oy = o.y / d
                        val w = c.size.width / d
                        val h = c.size.height / d
                        val old = geo
                        if (old == null || old.ox != ox || old.oy != oy || old.w != w || old.h != h) geo = TreeGeo(ox, oy, w, h)
                    }
                    .semantics { contentDescription = "Tree ring chart of yearly savings. Drag from the centre outward to pick a year." }
                    .pointerInput(auto, reduced) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d = density.density
                            takeOver()
                            followSlice(down.position.x / d, down.position.y / d)
                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    if (ch.position != ch.previousPosition) followSlice(ch.position.x / d, ch.position.y / d)
                                    ch.consume()
                                }
                            } finally {
                                release()
                            }
                        }
                    },
            )

            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TreeRollStack(state, TreeLabels, 32.dp, TextStyle(color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum"))
                TreeRollStack(state, TreeDeltas, 18.dp, TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
            }

            // The year row: scrubs the selection.
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(rowW.dp)
                    .height(76.dp)
                    .semantics { contentDescription = "Years" }
                    .pointerInput(auto, reduced, step) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d = density.density
                            takeOver()
                            followRow(down.position.x / d)
                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    if (ch.position != ch.previousPosition) followRow(ch.position.x / d)
                                    ch.consume()
                                }
                            } finally {
                                scheduleResume()
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .padding(start = 3.dp)
                        .width((step - 6f).dp)
                        .fillMaxHeight()
                        .graphicsLayer { translationX = state.pose().sel * step * density.density }
                        .background(AmberC.copy(alpha = 0.13f), RoundedCornerShape(14.dp))
                        .border(1.dp, AmberC.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                )
                val maxAmount = TreeAmounts.max()
                Row(Modifier.fillMaxSize()) {
                    for (i in 0 until RING_COUNT) {
                        Column(
                            Modifier.width(step.dp).fillMaxHeight().padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                        ) {
                            Box(
                                Modifier
                                    .width(14.dp)
                                    .height((8f + 30f * TreeAmounts[i] / maxAmount).dp)
                                    .background(Color(0xFFE2B276).copy(alpha = 0.42f), RoundedCornerShape(5.dp)),
                            )
                            BasicText("${TreeYears[i]}", style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }

            // The stats card.
            Row(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .treeCardShadow()
                    .background(Color(0xFFFFEED6).copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                    .border(Dp.Hairline, Color(0xFFFFEED6).copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(vertical = 14.dp, horizontal = 16.dp),
            ) {
                TreeStat("Best year", "2025 · $9,380")
                Box(Modifier.padding(horizontal = 12.dp).width(Dp.Hairline).fillMaxHeight().background(Color(0xFFFFEED6).copy(alpha = 0.14f)))
                TreeStat("Average", "$6,379 a year")
                Box(Modifier.padding(horizontal = 12.dp).width(Dp.Hairline).fillMaxHeight().background(Color(0xFFFFEED6).copy(alpha = 0.14f)))
                TreeStat("Next ring", "Goal $10,000")
            }
        }
    }
}

private fun Modifier.treeCardShadow(): Modifier = this
    .cssBoxShadow(Color.Black.copy(alpha = 0.3f), offsetY = 1.dp, blur = 2.dp, radius = 20.dp)
    .cssBoxShadow(Color.Black.copy(alpha = 0.25f), offsetY = 12.dp, blur = 32.dp, radius = 20.dp)

@Composable
private fun androidx.compose.foundation.layout.RowScope.TreeStat(label: String, value: String) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(label, style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp))
        BasicText(value, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

/**
 * One line of a list, chosen by the continuous selection: each line rolls inside a clipped box
 * (the old one goes up and fades, the new one comes in from below). Only graphicsLayer reads move.
 */
@Composable
private fun TreeRollStack(state: TreeState, items: List<String>, height: Dp, style: TextStyle) {
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        items.forEachIndexed { i, text ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .graphicsLayer {
                        val d = i - state.pose().sel
                        alpha = max(0f, 1f - abs(d) * 1.15f)
                        translationY = d * height.toPx()
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text, style = style.copy(textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    }
}

/** A whole-number odometer with thousands separators: `digits` columns after `prefix`. */
@Composable
private fun TreeOdometer(state: TreeState, digits: Int, prefix: String, height: Dp, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TreeOdoCell(prefix, height, style)
        for (k in digits - 1 downTo 0) {
            Box(Modifier.height(height).clipToBounds()) {
                Column(
                    Modifier
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .graphicsLayer {
                            // This column turns with the lower digits' carry and rests on a whole
                            // digit whenever the value is whole.
                            val v = max(0f, treeTotalOf(state.pose()))
                            val scaledV = v / Math.pow(10.0, k.toDouble()).toFloat()
                            val whole = floor(scaledV) % 10f
                            val frac = scaledV - floor(scaledV)
                            val lower = if (k == 0) frac else max(0f, frac * 10f - 9f)
                            translationY = -(whole + lower) * height.toPx()
                        },
                ) {
                    for (dgt in 0..10) TreeOdoCell("${dgt % 10}", height, style)
                }
            }
            if (k % 3 == 0 && k > 0) TreeOdoCell(",", height, style)
        }
    }
}

@Composable
private fun TreeOdoCell(text: String, height: Dp, style: TextStyle) {
    Box(Modifier.height(height), contentAlignment = Alignment.Center) { BasicText(text, style = style, maxLines = 1) }
}

