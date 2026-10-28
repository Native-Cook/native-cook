package com.ngocdevv.nativecook.blocks.progress.vitalitypearlgauge

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val VITALITY_PEARL_LOOP = 8.0
private val GaugeCream = Color(0xFFF0F5E4)
private val GaugeMint = Color(0xFFA4E2B5)
private val GaugeLavender = Color(0xFFCBB6EF)

private fun vitalityPearlSmooth(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun vitalityPearlPose(time: Double): Float {
    return vitalityPearlSmooth(((time - 1.5) / 1.8).toFloat()) *
        (1f - vitalityPearlSmooth(((time - 4.7) / 1.8).toFloat()))
}

@Composable
fun VitalityPearlGauge(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val densityContext = LocalDensity.current
    val initialPhase = previewTime?.let { ((it % VITALITY_PEARL_LOOP) + VITALITY_PEARL_LOOP) % VITALITY_PEARL_LOOP } ?: 0.0
    val pose = remember { mutableFloatStateOf(if (reduced) 0.5f else if (previewTime == null) 0f else vitalityPearlPose(initialPhase)) }
    val clock = remember { mutableDoubleStateOf(initialPhase) }
    val revision = remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }
    var crossed by remember { mutableStateOf(false) }

    fun accessibleSet(value: Float) {
        if (reduced) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            return
        }
        pose.floatValue = value
        returnFrom = value
        returnElapsed = 0f
        dragging = false
        returning = true
        revision.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) active = false
            else if (event == Lifecycle.Event.ON_START) {
                active = true
                pose.floatValue = 0f
                clock.doubleValue = 0.0
                dragging = false
                returning = false
                revision.intValue++
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPlay, reduced, previewTime, revision.intValue, active) {
        if (reduced) {
            dragging = false
            returning = false
            clock.doubleValue = 0.0
            pose.floatValue = 0.5f
            return@LaunchedEffect
        }
        if (previewTime != null && !dragging && !returning) {
            clock.doubleValue = ((previewTime % VITALITY_PEARL_LOOP) + VITALITY_PEARL_LOOP) % VITALITY_PEARL_LOOP
            pose.floatValue = vitalityPearlPose(clock.doubleValue)
            return@LaunchedEffect
        }
        if (!active || (!dragging && !returning && !autoPlay)) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (active && !reduced && (previewTime == null || dragging || returning)) {
            val now = withFrameNanos { it }
            val dt = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
            previous = now
            if (returning) {
                returnElapsed += dt
                if (returnElapsed <= 2f) pose.floatValue = returnFrom
                else {
                    val t = (returnElapsed - 2f) / 0.45f
                    pose.floatValue = returnFrom * (1f - vitalityPearlSmooth(t))
                    if (t >= 1f) {
                        pose.floatValue = if (previewTime == null) 0f else vitalityPearlPose(((previewTime % VITALITY_PEARL_LOOP) + VITALITY_PEARL_LOOP) % VITALITY_PEARL_LOOP)
                        returning = false
                        dragging = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (previewTime != null || !autoPlay) break
                    }
                }
            } else if (!dragging && autoPlay) {
                clock.doubleValue = (clock.doubleValue + dt) % VITALITY_PEARL_LOOP
                pose.floatValue = vitalityPearlPose(clock.doubleValue)
            } else if (!dragging) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1C5546), Color(0xFF102B22), Color(0xFF101D16))))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 28.dp, y = 18.dp).width(337.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("☼", color = GaugeCream, fontSize = 14.sp)
                Text("DAILY RECOVERY", Modifier.offset(x = 8.dp), color = GaugeCream, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                Text("TODAY", Modifier.weight(1f), color = GaugeCream, fontSize = 10.sp, textAlign = TextAlign.End)
            }
            Row(Modifier.offset(x = 149.dp, y = 77.5.dp).width(95.dp).height(3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 5) Box(Modifier.width(if (i == 1) 35.dp else 9.dp).height(3.dp).clip(CircleShape).background(GaugeCream.copy(alpha = if (i < 2) 0.65f else 0.14f)))
            }
            Text("Your energy,\ncoming into focus.", Modifier.offset(x = 24.dp, y = 109.dp).width(345.dp), color = GaugeCream, fontSize = 32.sp, lineHeight = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
            Box(
                Modifier.offset(x = 118.5.dp, y = 252.dp).size(156.dp)
                    .graphicsLayer { translationY = -8f * sin(pose.floatValue * PI.toFloat()) * density }
                    .shadow(24.dp, CircleShape, clip = false),
            ) {
                Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
                    val pixelScale = density
                    val side = size.width
                    val center = Offset(side / 2, side / 2)
                    val p = pose.floatValue
                    val centerX = side * (0.35f + p * 0.25f)
                    val centerY = side * 0.35f
                    val pearlBrush = Brush.radialGradient(
                        0.2f to Color(0x1A8ED9BA),
                        0.72f to Color(0x4D5C9F86),
                        0.97f to Color(0xB3C8EED0),
                        1f to Color(0x33629D83),
                        center = Offset(centerX, centerY),
                        radius = side * 0.6f,
                    )
                    drawCircle(pearlBrush, radius = side / 2, center = center)
                    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, side, side)) }
                    clipPath(clip) {
                        repeat(6) { i ->
                            val y = i * size.height / 5
                            val path = Path().apply {
                                moveTo(0f, y)
                                quadraticTo(size.width * 0.5f, y + (i - 2.5f) * 15f * pixelScale + p * 18f * pixelScale, size.width, y - 12f * pixelScale + p * 24f * pixelScale)
                            }
                            drawPath(path, Color(0x08A4E2B5), style = Stroke(8f * pixelScale))
                        }
                    }
                    val highX = side * (0.26f + p * 0.35f)
                    val highY = side * 0.23f
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), Offset(highX, highY), side * 0.2f), radius = side * 0.2f, center = Offset(highX, highY))
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0x14CBB6EF), Color.Transparent), Offset(side * 0.72f, side * 0.76f), side * 0.17f),
                        radius = side * 0.17f,
                        center = Offset(side * 0.72f, side * 0.76f),
                    )
                    drawCircle(Brush.linearGradient(listOf(GaugeCream.copy(alpha = 0.55f), GaugeCream.copy(alpha = 0.02f), GaugeMint.copy(alpha = 0.45f))), radius = side / 2, center = center, style = Stroke(1f * pixelScale))
                }
                Text("82", Modifier.align(Alignment.Center), color = GaugeCream, fontSize = 72.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
            }
            Text("READY FOR YOUR DAY", Modifier.offset(y = 434.dp).width(393.dp), color = GaugeCream.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.5.sp, textAlign = TextAlign.Center)
            Box(
                Modifier.offset(x = 20.dp, y = 467.dp).size(353.dp, 126.dp)
                    .pointerInput(reduced, previewTime) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced) {
                                    dragStart = pose.floatValue
                                    dragDistance = 0f
                                    crossed = false
                                    dragging = true
                                    returning = false
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && dragging) {
                                    change.consume()
                                    dragDistance += amount.x / densityContext.density
                                    val next = (dragStart + dragDistance / 180f).coerceIn(0f, 1f)
                                    pose.floatValue = next
                                    if (!crossed && (dragStart - 0.5f) * (next - 0.5f) <= 0f) {
                                        crossed = true
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (!reduced && dragging) {
                                    returnFrom = pose.floatValue
                                    returnElapsed = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                            onDragCancel = {
                                if (!reduced && dragging) {
                                    returnFrom = pose.floatValue
                                    returnElapsed = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                        )
                    }
                    .semantics {
                        contentDescription = "Recovery balance"
                        stateDescription = when { pose.floatValue < 0.33f -> "Lower balance, recovery index 82"; pose.floatValue > 0.67f -> "Higher balance, recovery index 82"; else -> "Centered balance, recovery index 82" }
                        customActions = listOf(
                            CustomAccessibilityAction("Increase balance") { accessibleSet(1f); true },
                            CustomAccessibilityAction("Lower balance") { accessibleSet(0f); true },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val pixelScale = density
                    val p = pose.floatValue
                    val u = 0.35f + 0.3f * p
                    val x = 15f + 323f * u
                    val y = 75f - 160f * u * (1f - u)
                    val base = Path().apply { moveTo(15f * pixelScale, 75f * pixelScale); quadraticTo(176.5f * pixelScale, -5f * pixelScale, 338f * pixelScale, 75f * pixelScale) }
                    drawPath(base, GaugeCream.copy(alpha = 0.12f), style = Stroke(7f * pixelScale, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    val activePath = Path()
                    for (i in 0..50) {
                        val t = 0.12f + (u - 0.12f) * i / 50f
                        val px = (15f + 323f * t) * pixelScale
                        val py = (75f - 160f * t * (1f - t)) * pixelScale
                        if (i == 0) activePath.moveTo(px, py) else activePath.lineTo(px, py)
                    }
                    drawPath(activePath, Brush.linearGradient(listOf(GaugeMint, GaugeLavender), start = Offset(40f * pixelScale, 40f * pixelScale), end = Offset(x * pixelScale, y * pixelScale)), style = Stroke(10f * pixelScale, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawCircle(GaugeCream, radius = 3.5f * pixelScale, center = Offset(176.5f * pixelScale, 35.5f * pixelScale))
                }
                Box(
                    Modifier.align(Alignment.Center).size(46.dp)
                        .graphicsLayer {
                            val u = 0.35f + 0.3f * pose.floatValue
                            val x = 15f + 323f * u
                            val y = 75f - 160f * u * (1f - u)
                            translationX = (x - 176.5f) * density
                            translationY = (y - 63f) * density
                        }
                        .shadow(12.dp, CircleShape, clip = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Brush.radialGradient(listOf(Color(0xD9C8EED0), Color(0x6B5C9F86), Color(0xBFC8EED0), Color(0x4D629D83)), center = Offset(size.width * 0.35f, size.height * 0.35f), radius = size.width * 0.7f), radius = size.width / 2)
                        drawCircle(GaugeCream.copy(alpha = 0.42f), radius = size.width / 2, style = Stroke(0.7.dp.toPx()))
                    }
                    Text("+6", color = GaugeCream, fontSize = 18.sp, fontFamily = FontFamily.Serif)
                }
                Text("LOWER", Modifier.offset(x = 7.dp, y = 91.dp).graphicsLayer { rotationZ = -20f }, color = GaugeCream.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 1.sp)
                Text("HIGHER", Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 91.dp).graphicsLayer { rotationZ = 20f }, color = GaugeCream.copy(alpha = 0.4f), fontSize = 9.sp, letterSpacing = 1.sp)
                Text("CURRENT", Modifier.align(Alignment.TopCenter).offset(y = 66.dp), color = GaugeCream.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 1.3.sp)
            }
            Row(Modifier.offset(x = 28.dp, y = 610.dp).width(337.dp).height(70.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                VitalityPearlStat("☾", "REST", "7h 42m")
                VitalityPearlStat("❧", "BALANCE", "Steady")
            }
            Text("Drag to find your balance.", Modifier.offset(y = 711.dp).width(393.dp), color = GaugeCream.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Center)
            Text("A MOMENT FOR YOURSELF", Modifier.offset(y = 742.dp).width(393.dp), color = GaugeCream.copy(alpha = 0.35f), fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RowScope.VitalityPearlStat(symbol: String, title: String, value: String) {
    Column(
        Modifier.weight(1f).height(70.dp).clip(RoundedCornerShape(20.dp))
            .background(GaugeCream.copy(alpha = 0.035f))
            .border(1.dp, GaugeCream.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(symbol, color = GaugeCream.copy(alpha = 0.5f), fontSize = 12.sp)
            Text(title, color = GaugeCream.copy(alpha = 0.5f), fontSize = 9.sp, letterSpacing = 1.3.sp)
        }
        Text(value, color = GaugeCream, fontSize = 22.sp, fontFamily = FontFamily.Serif)
    }
}
