package com.ngocdevv.nativecook.blocks.buttons.mothlinkbloom

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.semantics.selected
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
import kotlin.math.max
import kotlin.math.min

private const val MOTH_LINK_LOOP = 8.0
private val MothAccent = Color(0xFF6655F5)
private val MothInk = Color(0xFF272832)

private fun mothLinkSmooth(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun mothLinkPose(time: Double): Float {
    return mothLinkSmooth(((time - 1.4) / 1.8).toFloat()) *
        (1f - mothLinkSmooth(((time - 4.8) / 1.8).toFloat()))
}

@Composable
fun MothLinkBloom(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current
    BlockSystemBars(darkIcons = true)
    val initialTime = previewTime?.let { ((it % MOTH_LINK_LOOP) + MOTH_LINK_LOOP) % MOTH_LINK_LOOP } ?: 0.0
    val pose = remember { mutableFloatStateOf(if (reduced) 1f else if (previewTime == null) 0f else mothLinkPose(initialTime)) }
    val expanded by remember { derivedStateOf { pose.floatValue > 0.7f } }
    val clock = remember { mutableDoubleStateOf(initialTime) }
    val revision = remember { mutableIntStateOf(0) }
    val selection = remember { mutableIntStateOf(0) }
    var activated by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }
    var detent by remember { mutableStateOf(false) }

    fun interrupt() {
        returnFrom = pose.floatValue
        returnElapsed = 0f
        dragging = false
        returning = true
        revision.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun setMoth(target: Float) {
        if (reduced) return
        pose.floatValue = target
        returnFrom = target
        returnElapsed = 0f
        dragging = false
        returning = true
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
            clock.doubleValue = 0.0
            pose.floatValue = 1f
            dragging = false
            returning = false
            returnElapsed = 0f
            return@LaunchedEffect
        }
        if (previewTime != null && !dragging && !returning) {
            clock.doubleValue = ((previewTime % MOTH_LINK_LOOP) + MOTH_LINK_LOOP) % MOTH_LINK_LOOP
            pose.floatValue = mothLinkPose(clock.doubleValue)
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        if (!dragging && !returning && !autoPlay) { pose.floatValue = 0f; clock.doubleValue = 0.0; return@LaunchedEffect }
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
                    pose.floatValue = returnFrom * (1f - mothLinkSmooth(t))
                    if (t >= 1f) {
                        pose.floatValue = if (previewTime == null) 0f else mothLinkPose(((previewTime % MOTH_LINK_LOOP) + MOTH_LINK_LOOP) % MOTH_LINK_LOOP)
                        returning = false
                        dragging = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (previewTime != null || !autoPlay) break
                    }
                }
            } else if (!dragging && autoPlay) {
                clock.doubleValue = (clock.doubleValue + dt) % MOTH_LINK_LOOP
                pose.floatValue = mothLinkPose(clock.doubleValue)
            } else if (!dragging) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFFF6F5FF)).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 28.dp, y = 20.dp).width(337.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⌘", color = MothInk, fontSize = 15.sp)
                Text("LUMA / PRIVATE LINK", Modifier.offset(x = 8.dp), color = MothInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                Text("PRIVATE", Modifier.weight(1f), color = MothInk.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
            }
            Text("A quieter\nconnection.", Modifier.offset(y = 77.dp).width(393.dp), color = MothInk, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, lineHeight = 41.sp, textAlign = TextAlign.Center)
            Text("One touch. A little more peace.", Modifier.offset(y = 180.dp).width(393.dp), color = MothInk.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
            Canvas(
                Modifier.offset(x = 28.dp, y = 220.dp).size(337.dp, 330.dp)
                    .pointerInput(reduced, previewTime, scale) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced) {
                                    dragStart = pose.floatValue
                                    dragDistance = 0f
                                    detent = false
                                    dragging = true
                                    returning = false
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && dragging) {
                                    change.consume()
                                    dragDistance += -amount.y / density.density / scale
                                    val next = (dragStart + dragDistance / 180f).coerceIn(0f, 1f)
                                    pose.floatValue = next
                                    if (next > 0.85f && !detent) {
                                        detent = true
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                }
                            },
                            onDragEnd = { if (!reduced && dragging) { returnFrom = pose.floatValue; returnElapsed = 0f; dragging = false; returning = true; revision.intValue++ } },
                            onDragCancel = { if (!reduced && dragging) { returnFrom = pose.floatValue; returnElapsed = 0f; dragging = false; returning = true; revision.intValue++ } },
                        )
                    }
                    .semantics {
                        contentDescription = "Moth Link Bloom"; stateDescription = if (expanded) "Wings spread" else "Wings folded"
                        customActions = listOf(
                            CustomAccessibilityAction("Spread wings") { setMoth(1f); true },
                            CustomAccessibilityAction("Fold wings") { setMoth(0f); true },
                        )
                    },
            ) {
                drawMothArtwork(pose.floatValue, activated, density.density)
            }
            Row(Modifier.offset(x = 28.dp, y = 559.dp).width(337.dp).height(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MothServerCard("North / 24 ms", selection.intValue == 0, Modifier.weight(1f)) {
                    selection.intValue = 0
                    interrupt()
                }
                MothServerCard("West / 38 ms", selection.intValue == 1, Modifier.weight(1f)) {
                    selection.intValue = 1
                    interrupt()
                }
            }
            Row(
                Modifier.offset(x = 28.dp, y = 653.dp).size(337.dp, 54.dp).clip(RoundedCornerShape(18.dp)).background(MothAccent)
                    .clickable(role = Role.Button) { activated = !activated; setMoth(if (activated) 1f else 0f) }
                    .semantics { contentDescription = if (activated) "Disconnect" else "Connect"; role = Role.Button },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (activated) "✓" else "↗", color = Color.White, fontSize = 15.sp)
                Text(if (activated) "Disconnect" else "Connect", Modifier.offset(x = 9.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("DRAG UPWARD TO SPREAD WINGS", Modifier.offset(y = 731.dp).width(393.dp), color = MothInk.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMothArtwork(pose: Float, activated: Boolean, pixelScale: Float) {
    val center = Offset(168.5f * pixelScale, 155f * pixelScale)
    val spread = 0.42f + pose * 0.58f
    val haloAlpha = 0.08f + pose * 0.1f
    drawCircle(
        Brush.radialGradient(listOf(MothAccent.copy(alpha = haloAlpha), Color.Transparent), center, 145f * pixelScale),
        radius = (129.5f + pose * 9f) * pixelScale,
        center = center,
    )
    drawOval(Brush.linearGradient(listOf(Color(0xFFA99BFF), MothAccent, Color(0xFF5145DC)), Offset(80f * pixelScale, 50f * pixelScale), Offset(255f * pixelScale, 260f * pixelScale)), topLeft = Offset(63.5f * pixelScale, 50f * pixelScale), size = androidx.compose.ui.geometry.Size(210f * pixelScale, 210f * pixelScale))
    drawOval(Color.White.copy(alpha = 0.65f), topLeft = Offset(63.5f * pixelScale, 50f * pixelScale), size = androidx.compose.ui.geometry.Size(210f * pixelScale, 210f * pixelScale), style = Stroke(1.5f * pixelScale))

    for (side in listOf(-1f, 1f)) {
        val upper = Path().apply {
            moveTo(center.x, center.y)
            cubicTo((168.5f + side * 22f * spread) * pixelScale, 124f * pixelScale, (168.5f + side * 53f * spread) * pixelScale, 109f * pixelScale, (168.5f + side * 70f * spread) * pixelScale, (92f - pose * 4f) * pixelScale)
            cubicTo((168.5f + side * 68f * spread) * pixelScale, 157f * pixelScale, (168.5f + side * 53f * spread) * pixelScale, 170f * pixelScale, (168.5f + side * 25f * spread) * pixelScale, 174f * pixelScale)
            close()
        }
        drawPath(upper, Color.White.copy(alpha = 0.95f))
        val lower = Path().apply {
            moveTo(center.x, 157f * pixelScale)
            quadraticTo((168.5f + side * 58f * spread) * pixelScale, 178f * pixelScale, (168.5f + side * 48f * spread) * pixelScale, 209f * pixelScale)
            quadraticTo((168.5f + side * 7f * spread) * pixelScale, 204f * pixelScale, center.x, 170f * pixelScale)
            close()
        }
        drawPath(lower, Color(0xFFE7E1FF))
        val vein = Path().apply { moveTo(center.x, center.y); lineTo((168.5f + side * 54f * spread) * pixelScale, 114f * pixelScale) }
        drawPath(vein, MothAccent.copy(alpha = 0.25f), style = Stroke(1f * pixelScale))
        val antenna = Path().apply {
            moveTo(center.x, 137f * pixelScale)
            quadraticTo((168.5f + side * 19f) * pixelScale, 134f * pixelScale, (168.5f + side * 18f) * pixelScale, 117f * pixelScale)
        }
        drawPath(antenna, Color.White, style = Stroke(1.5f * pixelScale))
    }
    drawRoundRect(Color.White, topLeft = Offset(165.5f * pixelScale, 133f * pixelScale), size = androidx.compose.ui.geometry.Size(6f * pixelScale, 48f * pixelScale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * pixelScale))
    drawContext.canvas.nativeCanvas.drawText(if (activated) "CONNECTED" else "READY WHEN YOU ARE", center.x, 304f * pixelScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MothAccent.toArgb(); textSize = 10f * pixelScale; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) })
}

@Composable
private fun MothServerCard(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.size(160.dp, 64.dp).clip(RoundedCornerShape(18.dp))
            .background(if (selected) MothAccent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title, ${if (selected) "selected" else "available"}"; role = Role.Button; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, color = if (selected) MothAccent else MothInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(if (selected) "SELECTED" else "AVAILABLE", color = if (selected) MothAccent else MothInk.copy(alpha = 0.55f), fontSize = 8.sp, letterSpacing = 1.2.sp)
    }
}
