package com.ngocdevv.nativecook.blocks.media.signalirislibrary

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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min

private const val SIGNAL_IRIS_LOOP = 8.0
private val IrisCream = Color(0xFFFFE7BD)
private val IrisDark = Color(0xFF21130F)

private fun signalIrisSmooth(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun signalIrisPose(time: Double): Float {
    return signalIrisSmooth(((time - 1.4) / 1.8).toFloat()) *
        (1f - signalIrisSmooth(((time - 4.7) / 1.8).toFloat()))
}

@Composable
fun SignalIrisLibrary(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val densityContext = LocalDensity.current
    val irisLabelPaint = remember(densityContext.density, densityContext.fontScale) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = IrisCream.toArgb()
            textSize = with(densityContext) { 14.sp.toPx() }
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val initialTime = previewTime?.let { ((it % SIGNAL_IRIS_LOOP) + SIGNAL_IRIS_LOOP) % SIGNAL_IRIS_LOOP } ?: 0.0
    val pose = remember { mutableFloatStateOf(if (reduced) 1f else if (previewTime == null) 0f else signalIrisPose(initialTime)) }
    val clock = remember { mutableDoubleStateOf(initialTime) }
    val revision = remember { mutableIntStateOf(0) }
    val selected = remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }

    fun interrupt() {
        returnFrom = pose.floatValue
        returnElapsed = 0f
        dragging = false
        returning = true
        revision.intValue++
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun setCover(value: Float) {
        if (reduced) return
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
            dragging = false
            returning = false
            clock.doubleValue = 0.0
            pose.floatValue = 1f
            return@LaunchedEffect
        }
        if (previewTime != null && !dragging && !returning) {
            clock.doubleValue = ((previewTime % SIGNAL_IRIS_LOOP) + SIGNAL_IRIS_LOOP) % SIGNAL_IRIS_LOOP
            pose.floatValue = signalIrisPose(clock.doubleValue)
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
                    pose.floatValue = returnFrom * (1f - signalIrisSmooth(t))
                    if (t >= 1f) {
                        pose.floatValue = if (previewTime == null) 0f else signalIrisPose(((previewTime % SIGNAL_IRIS_LOOP) + SIGNAL_IRIS_LOOP) % SIGNAL_IRIS_LOOP)
                        returning = false
                        dragging = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (previewTime != null || !autoPlay) break
                    }
                }
            } else if (!dragging && autoPlay) {
                clock.doubleValue = (clock.doubleValue + dt) % SIGNAL_IRIS_LOOP
                pose.floatValue = signalIrisPose(clock.doubleValue)
            } else if (!dragging) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(0xFFF64624),
                0.5f to Color(0xFFF64624),
                0.5001f to Color(0xFF571D14),
                1f to Color(0xFF571D14),
            ),
        ).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 28.dp, y = 18.dp).width(337.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("▤", color = IrisCream, fontSize = 15.sp)
                Text("SIGNAL / LIBRARY", Modifier.offset(x = 8.dp), color = IrisCream, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                Text("02", Modifier.weight(1f), color = IrisCream, fontSize = 10.sp, textAlign = TextAlign.End)
            }
            Box(
                Modifier.offset(y = 65.dp).size(393.dp, 330.dp)
                    .pointerInput(reduced, previewTime) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced) {
                                    dragStart = pose.floatValue
                                    dragging = true
                                    returning = false
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && dragging) {
                                    change.consume()
                                    dragStart += amount.y / densityContext.density / 180f
                                    pose.floatValue = dragStart.coerceIn(0f, 1f)
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
                        contentDescription = "New ways to notice, illustrated cover"
                        customActions = listOf(
                            CustomAccessibilityAction("Open listening window") { setCover(1f); true },
                            CustomAccessibilityAction("Close listening window") { setCover(0f); true },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val pixelScale = density
                    val coverWidth = size.width
                    val coverHeight = size.height
                    drawRect(Color(0xFFF64624), size = androidx.compose.ui.geometry.Size(coverWidth, coverHeight))
                    val rules = Path().apply {
                        moveTo(330f * pixelScale, 0f)
                        lineTo(330f * pixelScale, coverHeight)
                        moveTo(0f, 80f * pixelScale)
                        lineTo(330f * pixelScale, 80f * pixelScale)
                        addOval(androidx.compose.ui.geometry.Rect(-57f * pixelScale, 80f * pixelScale, 293f * pixelScale, 430f * pixelScale))
                    }
                    drawPath(rules, IrisCream.copy(alpha = 0.75f), style = Stroke(3f * pixelScale))
                }
                Box(Modifier.offset(x = 46.5.dp, y = 10.dp).size(300.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(300.dp)) {
                        val pixelScale = density
                        val p = pose.floatValue
                        val side = size.width
                        val center = Offset(side / 2f, side / 2f)
                        val radius = 150f * pixelScale * p
                        val mask = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }
                        clipPath(mask) {
                            drawRect(Color(0xFFE7B76A), size = androidx.compose.ui.geometry.Size(side, side))
                            val rayTop = 0f
                            val rayLeft = center.x - 11f * pixelScale
                            val raySize = androidx.compose.ui.geometry.Size(22f * pixelScale, 300f * pixelScale)
                            for (i in 0 until 12) {
                                rotate(p * 25f + i * 30f, pivot = center) {
                                    drawRect(if (i % 2 == 0) Color(0xFF6A2217) else Color(0xFFF64624), topLeft = Offset(rayLeft, rayTop), size = raySize)
                                }
                            }
                            drawCircle(IrisDark, radius = 51f * pixelScale, center = center)
                            val nativeCanvas = drawContext.canvas.nativeCanvas
                            nativeCanvas.drawText("LISTEN", center.x, center.y - 2f * pixelScale, irisLabelPaint)
                            nativeCanvas.drawText("CLOSER", center.x, center.y + 15f * pixelScale, irisLabelPaint)
                        }
                        drawCircle(IrisCream.copy(alpha = p * 0.8f), radius = radius, center = center, style = Stroke(2f * pixelScale))
                    }
                }
                Text(
                    "New ways\nto notice",
                    Modifier.offset(x = 27.dp, y = 204.dp).graphicsLayer {
                        val amount = pose.floatValue
                        translationY = amount * 18f * density
                        alpha = 1f - amount
                    },
                    color = IrisDark,
                    fontSize = 48.sp,
                    lineHeight = 53.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                )
                Box(Modifier.offset(x = 180.5.dp, y = 317.5.dp).size(32.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(IrisCream.copy(alpha = 0.6f)))
            }
            Column(Modifier.offset(x = 28.dp, y = 410.dp).width(337.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("New ways to notice", color = IrisCream, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("A field guide for curious ears.", color = IrisCream.copy(alpha = 0.55f), fontSize = 13.sp)
            }
            SignalIrisEpisode(0, selected.intValue == 0, playing && selected.intValue == 0, Modifier.offset(x = 28.dp, y = 478.dp)) {
                selected.intValue = 0
                playing = true
                interrupt()
            }
            SignalIrisEpisode(1, selected.intValue == 1, playing && selected.intValue == 1, Modifier.offset(x = 28.dp, y = 567.dp)) {
                selected.intValue = 1
                playing = true
                interrupt()
            }
            Row(
                Modifier.offset(x = 22.dp, y = 672.dp).size(349.dp, 58.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(IrisCream.copy(alpha = 0.12f))
                    .border(1.dp, IrisCream.copy(alpha = 0.1f), RoundedCornerShape(22.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalIrisThumbnail(selected.intValue, Modifier.padding(start = 12.dp), size = 36.dp)
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (selected.intValue == 0) "The quiet art of looking" else "Good ideas take the long way", color = IrisCream, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (playing) "Now playing · Signal Radio" else "Paused · Signal Radio", color = IrisCream.copy(alpha = 0.55f), fontSize = 9.sp)
                }
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Button) {
                        playing = !playing
                        interrupt()
                    }.semantics { contentDescription = if (playing) "Pause" else "Play"; role = Role.Button },
                ) {
                    Text(if (playing) "Ⅱ" else "▶", Modifier.align(Alignment.Center), color = IrisCream, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            Text("PULL THE COVER TO LOOK CLOSER", Modifier.offset(y = 741.dp).width(393.dp), color = IrisCream.copy(alpha = 0.4f), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SignalIrisEpisode(episode: Int, selected: Boolean, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val number = if (episode == 0) "01" else "02"
    val title = if (episode == 0) "The quiet art of looking" else "Good ideas take the long way"
    val duration = if (episode == 0) "18 min" else "26 min"
    Row(
        modifier.width(337.dp).height(84.dp).drawBehind {
            val line = 1.dp.toPx()
            drawRect(IrisCream.copy(alpha = 0.1f), topLeft = Offset(0f, size.height - line), size = androidx.compose.ui.geometry.Size(size.width, line))
        }.clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Play $title, $duration"; this.selected = selected },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).height(84.dp), verticalArrangement = Arrangement.Center) {
            Text("EPISODE $number", color = IrisCream.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            Text(title, Modifier.padding(top = 7.dp), color = IrisCream, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (selected && playing) "≋" else "▶", color = IrisCream.copy(alpha = 0.6f), fontSize = 9.sp)
                Text(duration, color = IrisCream.copy(alpha = 0.6f), fontSize = 9.sp)
            }
        }
        SignalIrisThumbnail(episode, size = 64.dp)
    }
}

@Composable
private fun SignalIrisThumbnail(episode: Int, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier.size(size).clip(RoundedCornerShape(if (size == 36.dp) 8.dp else 8.dp))) {
        val pixelScale = density
        val side = size.toPx()
        val sourceSide = 64f
        drawRect(if (episode == 0) Color(0xFFE8BB87) else Color(0xFFE67E69))
        drawCircle(Color(0xFF571D14), radius = side * 0.85f / 2f, center = Offset(side * 0.38f, side * 0.67f), style = Stroke(side * 0.035f))
        val left = side * (43f / sourceSide)
        val barWidth = side * (1.6f / sourceSide)
        val barHeight = side
        if (episode == 0) drawRect(Color(0xFF571D14), topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(barWidth, barHeight))
        else rotate(35f, pivot = Offset(side / 2, side / 2)) {
            val barLeft = left
            val rectSize = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            drawRect(Color(0xFF571D14), topLeft = Offset(barLeft, 0f), size = rectSize)
        }
        drawCircle(Color(0xFF571D14), radius = side * 0.2f / 2f, center = Offset(side * 0.3f, side * 0.31f))
    }
}
