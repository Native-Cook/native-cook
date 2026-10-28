package com.ngocdevv.nativecook.blocks.progress.verdantstarrewards

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val VERDANT_STAR_LOOP = 8.0
private val VerdantAccent = Color(0xFFB2FF52)
private val VerdantInk = Color(0xFFF1F6E8)

private fun verdantStarSmooth(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun verdantStarPose(time: Double): Float {
    return verdantStarSmooth(((time - 1.4) / 1.8).toFloat()) *
        (1f - verdantStarSmooth(((time - 4.8) / 1.8).toFloat()))
}

@Composable
fun VerdantStarRewards(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val displayDensity = LocalDensity.current
    BlockSystemBars(darkIcons = false)
    val initialTime = previewTime?.let { ((it % VERDANT_STAR_LOOP) + VERDANT_STAR_LOOP) % VERDANT_STAR_LOOP } ?: 0.0
    val pose = remember { mutableFloatStateOf(if (reduced) 1f else if (previewTime == null) 0f else verdantStarPose(initialTime)) }
    val expanded by remember { derivedStateOf { pose.floatValue > 0.7f } }
    val clock = remember { mutableDoubleStateOf(initialTime) }
    val revision = remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var manual by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var start by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }
    var detent by remember { mutableStateOf(false) }
    var activated by remember { mutableStateOf(false) }

    fun release() {
        returnFrom = pose.floatValue
        returnElapsed = 0f
        touching = false
        manual = true
        revision.intValue++
    }

    fun accessibilitySet(value: Float) {
        if (reduced) return
        pose.floatValue = value
        returnFrom = value
        returnElapsed = 0f
        touching = false
        manual = true
        revision.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun claim() {
        activated = true
        pose.floatValue = 1f
        returnFrom = 1f
        returnElapsed = 0f
        touching = false
        manual = true
        revision.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    DisposableEffect(lifecycle, reduced) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) active = false
            else if (event == Lifecycle.Event.ON_START) {
                active = true
                pose.floatValue = if (reduced) 1f else 0f
                clock.doubleValue = 0.0
                manual = false
                touching = false
                revision.intValue++
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPlay, reduced, previewTime, revision.intValue, active) {
        if (reduced) {
            clock.doubleValue = 0.0
            pose.floatValue = 1f
            manual = false
            touching = false
            returnElapsed = 0f
            return@LaunchedEffect
        }
        if (previewTime != null && !manual) {
            clock.doubleValue = ((previewTime % VERDANT_STAR_LOOP) + VERDANT_STAR_LOOP) % VERDANT_STAR_LOOP
            pose.floatValue = verdantStarPose(clock.doubleValue)
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        if (!manual && !autoPlay) { pose.floatValue = 0f; clock.doubleValue = 0.0; return@LaunchedEffect }
        var previous = withFrameNanos { it }
        while (active && !reduced && (previewTime == null || manual)) {
            val now = withFrameNanos { it }
            val dt = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
            previous = now
            if (manual && !touching) {
                returnElapsed += dt
                if (returnElapsed <= 2f) pose.floatValue = returnFrom
                else {
                    val t = (returnElapsed - 2f) / 0.45f
                    pose.floatValue = returnFrom * (1f - verdantStarSmooth(t))
                    if (t >= 1f) {
                        pose.floatValue = if (previewTime == null) 0f else verdantStarPose(((previewTime % VERDANT_STAR_LOOP) + VERDANT_STAR_LOOP) % VERDANT_STAR_LOOP)
                        manual = false
                        touching = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (previewTime != null || !autoPlay) break
                    }
                }
            } else if (!manual && autoPlay) {
                clock.doubleValue = (clock.doubleValue + dt) % VERDANT_STAR_LOOP
                pose.floatValue = verdantStarPose(clock.doubleValue)
            } else if (!manual) break
        }
    }

    val labelSize = with(displayDensity) { 12.sp.toPx() }
    val rewardLabelPaint = remember(labelSize) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = VerdantAccent.toArgb()
            textSize = labelSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFF12170D)).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 28.dp, y = 20.dp).width(337.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✧", color = VerdantInk, fontSize = 15.sp)
                Text("HABIT / REWARDS", Modifier.offset(x = 8.dp), color = VerdantInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                Text("DAY 03", Modifier.weight(1f), color = VerdantInk.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
            }
            Text("Good days\nadd up.", Modifier.offset(y = 77.dp).width(393.dp), color = VerdantInk, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, lineHeight = 41.sp, textAlign = TextAlign.Center)
            Text("Your daily reward is ready.", Modifier.offset(y = 180.dp).width(393.dp), color = VerdantInk.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Canvas(
                Modifier.offset(x = 28.dp, y = 220.dp).size(337.dp, 330.dp)
                    .pointerInput(reduced, previewTime, scale) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced) {
                                    start = pose.floatValue
                                    dragDistance = 0f
                                    touching = true
                                    manual = true
                                    detent = false
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && touching) {
                                    change.consume()
                                    dragDistance += -amount.y / displayDensity.density / scale
                                    val next = (start + dragDistance / 180f).coerceIn(0f, 1f)
                                    pose.floatValue = next
                                    if (next > 0.85f && !detent) {
                                        detent = true
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                }
                            },
                            onDragEnd = { if (!reduced && touching) release() },
                            onDragCancel = { if (!reduced && touching) release() },
                        )
                    }
                    .semantics {
                        contentDescription = "Verdant Star Rewards"; stateDescription = if (expanded) "Star assembled" else "Gather the reward star"
                        customActions = listOf(
                            CustomAccessibilityAction("Gather reward star") { accessibilitySet(1f); true },
                            CustomAccessibilityAction("Reset reward star") { accessibilitySet(0f); true },
                        )
                    },
            ) {
                val pixelScale = this.density
                val p = pose.floatValue
                val center = Offset(168.5f * pixelScale, 155f * pixelScale)
                val halo = Path().apply { addOval(androidx.compose.ui.geometry.Rect(38f * pixelScale, 25f * pixelScale, 299f * pixelScale, 286f * pixelScale)) }
                drawPath(
                    halo,
                    Brush.radialGradient(listOf(VerdantAccent.copy(alpha = 0.25f + p * 0.15f), Color.Transparent), center, 132f * pixelScale),
                )
                repeat(3) { ring ->
                    val rx = (289f - ring * 28f) * pixelScale / 2f
                    val ry = (202f - ring * 32f) * pixelScale / 2f
                    val path = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(center.x - rx, center.y - ry, center.x + rx, center.y + ry))
                    }
                    drawPath(path, VerdantAccent.copy(alpha = 0.10f * (1f - p)), style = Stroke(1f * pixelScale))
                }
                val star = verdantStarPath(center, 99f * pixelScale)
                drawPath(
                    star,
                    Brush.linearGradient(
                        listOf(Color(0xFFE3FFA5).copy(alpha = p), VerdantAccent.copy(alpha = p * 0.85f), Color(0xFF347414).copy(alpha = p)),
                        start = Offset(110f * pixelScale, 60f * pixelScale),
                        end = Offset(230f * pixelScale, 250f * pixelScale),
                    ),
                )
                drawPath(star, Color(0xFFE5FFB7).copy(alpha = p * 0.8f), style = Stroke(1.5f * pixelScale))
                for (i in 0 until 72) {
                    val angle = i * 2.399963
                    val target = verdantStarEdgePoint(i / 72.0 * 10.0, pixelScale)
                    val radius = 112 + (i % 7) * 4
                    val sourceX = 168.5 + cos(angle) * radius
                    val sourceY = 155 + sin(angle) * radius * 0.79
                    val q = verdantStarSmooth(min(1f, p * (1.05f + (i % 5) * 0.03f))).toDouble()
                    val curl = sin(q * PI) * 22.0
                    val targetX = target.x.toDouble() / pixelScale
                    val targetY = target.y.toDouble() / pixelScale
                    val x = sourceX + (targetX - sourceX) * q + cos(angle + PI / 2) * curl
                    val y = sourceY + (targetY - sourceY) * q + sin(angle + PI / 2) * curl
                    val beadRadius = (1.4f + (i % 4) * 0.55f) * pixelScale
                    drawCircle(if (i % 3 == 0) Color(0xFFEEFFC6) else VerdantAccent.copy(alpha = 0.65f), beadRadius, Offset(x.toFloat() * pixelScale, y.toFloat() * pixelScale))
                }
                for (i in 0 until 5) {
                    val outer = verdantStarPoint(i * 2, pixelScale)
                    val inner = verdantStarPoint(i * 2 + 1, pixelScale)
                    val facet = Path().apply { moveTo(center.x, center.y); lineTo(outer.x, outer.y); lineTo(inner.x, inner.y); close() }
                    drawPath(facet, Color.White.copy(alpha = (if (i % 2 == 0) 0.24f else 0.06f) * p))
                }
                val canvas = drawContext.canvas.nativeCanvas
                canvas.drawText(if (activated) "REWARD COLLECTED" else "150 POINTS", center.x, 292f * pixelScale, rewardLabelPaint)
            }
            Row(Modifier.offset(x = 28.dp, y = 565.dp).width(337.dp).height(52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                for (i in 0 until 7) {
                    val current = i == 2
                    val claimed = i < 2 || (current && activated)
                    Column(
                        Modifier.width(41.dp).height(52.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (current) VerdantAccent else VerdantInk.copy(alpha = 0.05f)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${i + 1}", color = if (current) Color(0xFF18200E) else VerdantInk.copy(alpha = 0.45f), fontSize = 10.sp)
                        Text(if (claimed) "✓" else "✧", Modifier.offset(y = 3.dp), color = if (current) Color(0xFF18200E) else VerdantInk.copy(alpha = 0.45f), fontSize = 13.sp)
                    }
                }
            }
            Row(
                Modifier.offset(x = 28.dp, y = 653.dp).size(337.dp, 54.dp).clip(RoundedCornerShape(18.dp))
                    .background(VerdantAccent)
                    .clickable(role = Role.Button, onClick = ::claim)
                    .semantics { contentDescription = if (activated) "150 points collected" else "Claim 150 points" },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (activated) "✓" else "↗", color = Color(0xFF18200E), fontSize = 15.sp)
                Text(if (activated) "150 points collected" else "Claim 150 points", Modifier.offset(x = 9.dp), color = Color(0xFF18200E), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("DRAG UPWARD TO GATHER", Modifier.offset(y = 731.dp).width(393.dp), color = VerdantInk.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun verdantStarPoint(index: Int, pixelScale: Float): Offset {
    val angle = index * PI / 5 - PI / 2
    val radius = if (index % 2 == 0) 99.0 else 42.57
    return Offset((168.5 + cos(angle) * radius).toFloat() * pixelScale, (155 + sin(angle) * radius).toFloat() * pixelScale)
}

private fun verdantStarEdgePoint(position: Double, pixelScale: Float): Offset {
    val vertex = floor(position).toInt()
    val mix = (position - vertex).toFloat()
    val a = verdantStarPoint(vertex, pixelScale)
    val b = verdantStarPoint(vertex + 1, pixelScale)
    return Offset(a.x + (b.x - a.x) * mix, a.y + (b.y - a.y) * mix)
}

private fun verdantStarPath(center: Offset, radius: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val angle = i * PI / 5 - PI / 2
        val r = if (i % 2 == 0) radius else radius * 0.43f
        val point = Offset(center.x + cos(angle).toFloat() * r, center.y + sin(angle).toFloat() * r)
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}
