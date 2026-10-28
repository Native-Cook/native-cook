package com.ngocdevv.nativecook.blocks.cards.pearljeweldrawer

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import kotlin.math.min
import kotlin.math.sin

private const val PEARL_LOOP = 8.0
private val PearlInk = Color(0xFF272832)
private val PearlAccent = Color(0xFF7C2544)
private val SilverBrush = Brush.linearGradient(listOf(Color(0xFFFEFEFA), Color(0xFFB7BAB5), Color(0xFFECEDE7)), Offset(65f, 70f), Offset(272f, 305f))
private fun pearlClamp(value: Double, lo: Double = 0.0, hi: Double = 1.0) = value.coerceIn(lo, hi)
private fun pearlSmooth(value: Double): Double { val u = pearlClamp(value); return u * u * (3.0 - 2.0 * u) }
private fun pearlPose(time: Double): Float = (pearlSmooth((time - 1.4) / 1.8) * (1.0 - pearlSmooth((time - 4.8) / 1.8))).toFloat()
private fun pearlTextPaint(color: Color, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = size; textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

@Composable
fun PearlJewelDrawer(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val displayDensity = LocalDensity.current
    BlockSystemBars(darkIcons = true)
    val startTime = previewTime?.let { ((it % PEARL_LOOP) + PEARL_LOOP) % PEARL_LOOP } ?: 0.0
    val pose = remember { mutableFloatStateOf(if (reduced) 1f else if (previewTime == null) 0f else pearlPose(startTime)) }
    val clock = remember { mutableDoubleStateOf(startTime) }
    val revision = remember { mutableIntStateOf(0) }
    var observation by remember { mutableIntStateOf(if (reduced || (previewTime != null && pearlPose(startTime) > 0.7f)) 1 else 0) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var touching by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var activated by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }
    var detent by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    fun release() {
        touching = false
        returnFrom = pose.floatValue
        returnElapsed = 0f
        returning = true
        manual = true
        revision.intValue++
    }
    fun setPose(value: Float) {
        if (reduced) return
        pose.floatValue = value.coerceIn(0f, 1f)
        returnFrom = pose.floatValue
        returnElapsed = 0f
        touching = false
        manual = true
        returning = true
        revision.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun open() {
        activated = true
        if (!reduced) {
            pose.floatValue = 1f
            returnFrom = 1f
            returnElapsed = 0f
            touching = false
            manual = true
            returning = true
            revision.intValue++
        }
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
                returning = false
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
            observation = 1
            manual = false
            touching = false
            returning = false
            returnElapsed = 0f
            return@LaunchedEffect
        }
        if (previewTime != null && !manual) {
            clock.doubleValue = ((previewTime % PEARL_LOOP) + PEARL_LOOP) % PEARL_LOOP
            pose.floatValue = pearlPose(clock.doubleValue)
            observation = if (pose.floatValue > 0.7f) 1 else 0
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        if (!manual && !autoPlay) { pose.floatValue = 0f; clock.doubleValue = 0.0; return@LaunchedEffect }
        var previous = withFrameNanos { it }
        while (active && !reduced && (previewTime == null || manual)) {
            val now = withFrameNanos { it }
            val dt = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
            previous = now
            val nextObservation = if (pose.floatValue > 0.7f) 1 else 0
            if (nextObservation != observation) observation = nextObservation
            if (returning) {
                returnElapsed += dt
                if (returnElapsed <= 2f) pose.floatValue = returnFrom
                else {
                    val t = ((returnElapsed - 2f) / 0.45f).coerceIn(0f, 1f)
                    pose.floatValue = returnFrom * (1f - (t * t * (3f - 2f * t)))
                    if (t >= 1f) {
                        pose.floatValue = if (previewTime == null) 0f else pearlPose(((previewTime % PEARL_LOOP) + PEARL_LOOP) % PEARL_LOOP)
                        returning = false
                        touching = false
                        manual = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (previewTime != null || !autoPlay) break
                    }
                }
            } else if (!touching && autoPlay) {
                clock.doubleValue = (clock.doubleValue + dt) % PEARL_LOOP
                pose.floatValue = pearlPose(clock.doubleValue)
            } else if (!touching) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFFECEBE6)).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 28.dp, y = 20.dp).width(337.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("▣", color = PearlInk, fontSize = 15.sp)
                Text("FORME / LITTLE TREASURES", Modifier.offset(x = 8.dp), color = PearlInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                Text("SILVER", Modifier.weight(1f), color = PearlInk.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
            }
            Text("Keep the\nlittle things.", Modifier.offset(y = 79.dp).width(393.dp), color = PearlInk, fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, lineHeight = 41.sp, textAlign = TextAlign.Center)
            Text("A quiet place for your favorites.", Modifier.offset(y = 180.dp).width(393.dp), color = PearlInk.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Canvas(
                Modifier.offset(x = 28.dp, y = 220.dp).size(337.dp, 330.dp)
                    .pointerInput(reduced, previewTime, scale) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced) {
                                    dragStart = pose.floatValue
                                    dragDistance = 0f
                                    detent = false
                                    touching = true
                                    returning = false
                                    manual = true
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && touching) {
                                    change.consume()
                                    dragDistance += -amount.y / displayDensity.density / scale
                                    pose.floatValue = (dragStart + dragDistance / 180f).coerceIn(0f, 1f)
                                    if (pose.floatValue > 0.85f && !detent) {
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
                        contentDescription = "Pearl Jewel Drawer"
                        stateDescription = if (observation == 1) "Three silver treasures revealed" else "Lid closed"
                        customActions = listOf(
                            CustomAccessibilityAction("Open keepsake") { setPose(1f); true },
                            CustomAccessibilityAction("Close keepsake") { setPose(0f); true },
                        )
                    },
            ) {
                val pixelScale = this.density
                val p = pose.floatValue
                val silver = Brush.linearGradient(listOf(Color(0xFFFEFEFA), Color(0xFFB7BAB5), Color(0xFFECEDE7)), Offset(65f * pixelScale, 70f * pixelScale), Offset(272f * pixelScale, 305f * pixelScale))
                val base = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(65f * pixelScale, 61f * pixelScale, 272f * pixelScale, 319f * pixelScale), androidx.compose.ui.geometry.CornerRadius(32f * pixelScale))) }
                translate(30.3f * pixelScale, 49f * pixelScale) {
                    scale(0.82f, 0.82f, Offset.Zero) {
                        drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(65f * pixelScale, 68f * pixelScale), androidx.compose.ui.geometry.Size(207f * pixelScale, 258f * pixelScale), androidx.compose.ui.geometry.CornerRadius(32f * pixelScale))
                        drawRoundRect(silver, Offset(65f * pixelScale, 61f * pixelScale), androidx.compose.ui.geometry.Size(207f * pixelScale, 258f * pixelScale), androidx.compose.ui.geometry.CornerRadius(32f * pixelScale))
                        drawRoundRect(Color.White.copy(alpha = 0.95f), Offset(65f * pixelScale, 61f * pixelScale), androidx.compose.ui.geometry.Size(207f * pixelScale, 258f * pixelScale), androidx.compose.ui.geometry.CornerRadius(32f * pixelScale), style = Stroke(1.5f * pixelScale))
                        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF565A53), Color(0xFF858B7F)), Offset(76f * pixelScale, 72f * pixelScale), Offset(261f * pixelScale, 306f * pixelScale)), Offset(76f * pixelScale, 72f * pixelScale), androidx.compose.ui.geometry.Size(185f * pixelScale, 234f * pixelScale), androidx.compose.ui.geometry.CornerRadius(25f * pixelScale))
                        translate(0f, -5f * p * pixelScale) {
                            val hoop = Path().apply { addOval(androidx.compose.ui.geometry.Rect(105f * pixelScale, 254f * pixelScale, 149f * pixelScale, 298f * pixelScale)) }
                            drawPath(hoop, Color(0xFFDFE2DA), style = Stroke(3f * pixelScale))
                            drawPath(hoop, Color.White.copy(alpha = 0.7f), style = Stroke(1f * pixelScale))
                            val pendant = Path().apply { addOval(androidx.compose.ui.geometry.Rect(164f * pixelScale, 222f * pixelScale, 202f * pixelScale, 260f * pixelScale)) }
                            drawPath(pendant, Color(0xFFECEEE4), style = Stroke(3f * pixelScale))
                            for (i in 0 until 16) {
                                val angle = i * 2.0 * PI / 16.0
                                val x = (183.0 + cos(angle) * 26.0).toFloat() * pixelScale
                                val y = (241.0 + sin(angle) * 26.0).toFloat() * pixelScale
                                rotate((angle * 180.0 / PI).toFloat(), Offset(x, y)) {
                                    drawOval(silver, Offset(x - 5f * pixelScale, y - 2f * pixelScale), androidx.compose.ui.geometry.Size(10f * pixelScale, 4f * pixelScale))
                                }
                            }
                            val studBrush = Brush.radialGradient(listOf(Color.White, Color(0xFFBEC3BA), Color(0xFF737A70)), Offset(212f * pixelScale, 279f * pixelScale), 9f * pixelScale)
                            drawCircle(studBrush, 6f * pixelScale, Offset(212f * pixelScale, 279f * pixelScale))
                            drawCircle(studBrush, 6f * pixelScale, Offset(231f * pixelScale, 287f * pixelScale))
                        }
                        translate(0f, -108f * p * pixelScale) {
                            rotate(4f * p, Offset(168.5f * pixelScale, 190f * pixelScale)) {
                                drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(67f * pixelScale, 66f * pixelScale), androidx.compose.ui.geometry.Size(207f * pixelScale, 258f * pixelScale), androidx.compose.ui.geometry.CornerRadius(32f * pixelScale))
                                drawRoundRect(silver, Offset(65f * pixelScale, 61f * pixelScale), androidx.compose.ui.geometry.Size(207f * pixelScale, 258f * pixelScale), androidx.compose.ui.geometry.CornerRadius(32f * pixelScale))
                                clipPath(base) {
                                    for (i in 0 until 53) {
                                        val y = (63f + i * 5f) * pixelScale
                                        drawLine(Color.White.copy(alpha = if (i % 2 == 0) 0.12f else 0.04f), Offset(65f * pixelScale, y), Offset(272f * pixelScale, y + 3f * pixelScale), 0.7f * pixelScale)
                                    }
                                }
                                drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(65f * pixelScale, 61f * pixelScale), androidx.compose.ui.geometry.Size(207f * pixelScale, 258f * pixelScale), androidx.compose.ui.geometry.CornerRadius(32f * pixelScale), style = Stroke(1.5f * pixelScale))
                                drawCircle(PearlAccent, 38f * pixelScale, Offset(168.5f * pixelScale, 190f * pixelScale))
                                drawCircle(Color(0xFF4D172A), 17f * pixelScale, Offset(168.5f * pixelScale, 190f * pixelScale), style = Stroke(6f * pixelScale))
                                drawContext.canvas.nativeCanvas.drawText("FORME", 168.5f * pixelScale, 263f * pixelScale, pearlTextPaint(PearlInk.copy(alpha = 0.6f), 9f * pixelScale))
                            }
                        }
                    }
                }
            }
            Row(Modifier.offset(x = 70.dp, y = 562.dp).width(253.dp).height(58.dp), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("03", color = PearlInk, fontSize = 22.sp, fontFamily = FontFamily.Serif)
                    Text("TREASURES", color = PearlInk.copy(alpha = 0.55f), fontSize = 8.sp, letterSpacing = 1.5.sp)
                }
                Box(Modifier.width(1.dp).height(33.dp).background(PearlInk.copy(alpha = 0.15f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Silver", color = PearlInk, fontSize = 22.sp, fontFamily = FontFamily.Serif)
                    Text("COLLECTION", color = PearlInk.copy(alpha = 0.55f), fontSize = 8.sp, letterSpacing = 1.5.sp)
                }
            }
            Row(
                Modifier.offset(x = 28.dp, y = 653.dp).size(337.dp, 54.dp).clip(RoundedCornerShape(18.dp)).background(PearlAccent)
                    .graphicsLayer { val s = if (pressed && !reduced) 0.97f else 1f; scaleX = s; scaleY = s; alpha = if (pressed) 0.8f else 1f }
                    .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = ::open)
                    .semantics { contentDescription = if (activated) "Reveal collection" else "Open keepsake"; role = Role.Button },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (activated) "✓" else "↗", color = Color.White, fontSize = 15.sp)
                Text(if (activated) "Reveal collection" else "Open keepsake", Modifier.offset(x = 9.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("DRAG THE LID UPWARD", Modifier.offset(y = 731.dp).width(393.dp), color = PearlInk.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp, textAlign = TextAlign.Center)
        }
    }
}
