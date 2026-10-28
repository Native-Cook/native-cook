package com.ngocdevv.nativecook.blocks.onboarding.lampwishcompanion

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val LAMP_WISH_LOOP = 8.0
private val WishInk = Color(0xFF6A3D28)
private val WishCream = Color(0xFFFFF8E8)

private fun lampWishSmooth(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return u * u * (3f - 2f * u)
}

private fun lampWishLoopPose(time: Double): Float {
    return lampWishSmooth(((time - 1.5) / 1.7).toFloat()) *
        (1f - lampWishSmooth(((time - 4.7) / 1.8).toFloat()))
}

@Composable
fun LampWishCompanion(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current
    val pixelScale = LocalDensity.current.density
    val pose = remember { mutableFloatStateOf(0f) }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val revision = remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var wished by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }
    var thresholdHapticSent by remember { mutableStateOf(false) }

    fun makeWish() {
        wished = true
        if (!reduced && previewTime == null) {
            returnFrom = 1f
            pose.floatValue = 1f
            returnElapsed = 0f
            dragging = false
            returning = true
            revision.intValue++
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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
            pose.floatValue = 0f
            return@LaunchedEffect
        }
        if (previewTime != null) {
            clock.doubleValue = ((previewTime % LAMP_WISH_LOOP) + LAMP_WISH_LOOP) % LAMP_WISH_LOOP
            pose.floatValue = lampWishLoopPose(clock.doubleValue)
            return@LaunchedEffect
        }
        if (!active) return@LaunchedEffect
        if (!dragging && !returning && !autoPlay) {
            pose.floatValue = 0f
            return@LaunchedEffect
        }
        var previous = withFrameNanos { it }
        while (active && !reduced && previewTime == null) {
            val now = withFrameNanos { it }
            val delta = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
            previous = now
            if (returning) {
                returnElapsed += delta
                if (returnElapsed <= 2f) pose.floatValue = returnFrom
                else {
                    val t = (returnElapsed - 2f) / 0.45f
                    pose.floatValue = returnFrom * (1f - lampWishSmooth(t))
                    if (t >= 1f) {
                        pose.floatValue = 0f
                        returning = false
                        dragging = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (!autoPlay) break
                    }
                }
            } else if (!dragging && autoPlay) {
                clock.doubleValue = (clock.doubleValue + delta) % LAMP_WISH_LOOP
                pose.floatValue = lampWishLoopPose(clock.doubleValue)
            } else if (!dragging) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .background(Color(0xFFF2D49B))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            LampWishVillage()
            Row(Modifier.offset(x = 28.dp, y = 22.dp).width(337.dp).height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("DAILY LITTLE WONDER", color = WishInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("01 / 07", Modifier.weight(1f), color = WishInk, fontSize = 11.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
            }
            Box(
                Modifier.offset(x = 86.5.dp, y = 175.dp).size(220.dp, 240.dp)
                    .graphicsLayer {
                        val amount = pose.floatValue
                        val bounce = sin(amount * 2f * PI.toFloat())
                        translationY = -30f * amount * pixelScale
                        rotationZ = -3f + amount * 6f
                        scaleX = 1f + 0.09f * bounce
                        scaleY = 1f - 0.09f * bounce
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) { drawLampWishCat(pose.floatValue) }
            }
            Text("A little wish,\njust for you.", Modifier.offset(x = 26.5.dp, y = 420.dp).width(340.dp), color = WishInk, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                if (wished) "A bright little moment is yours." else "Meet Miso. Every day holds a surprise.",
                Modifier.offset(x = 21.5.dp, y = 501.dp).width(350.dp), color = WishInk, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            Box(
                Modifier.offset(x = 109.dp, y = 535.dp).size(175.dp, 120.dp)
                    .graphicsLayer { rotationZ = sin(pose.floatValue * PI.toFloat()) * -8f }
                    .pointerInput(reduced, previewTime) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced && previewTime == null) {
                                    dragStart = pose.floatValue
                                    dragDistance = 0f
                                    thresholdHapticSent = false
                                    dragging = true
                                    returning = false
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && previewTime == null && dragging) {
                                    change.consume()
                                    dragDistance += amount.x / pixelScale
                                    val next = (dragStart + kotlin.math.abs(dragDistance) / 120f).coerceIn(0f, 1f)
                                    pose.floatValue = next
                                    if (next > 0.8f && !thresholdHapticSent) {
                                        thresholdHapticSent = true
                                        wished = true
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (!reduced && previewTime == null && dragging) {
                                    returnFrom = pose.floatValue
                                    returnElapsed = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                            onDragCancel = {
                                if (!reduced && previewTime == null && dragging) {
                                    returnFrom = pose.floatValue
                                    returnElapsed = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                        )
                    }
                    .semantics {
                        contentDescription = "Wish lamp. Rub horizontally to make a wish."
                        customActions = listOf(CustomAccessibilityAction("Make a wish") { makeWish(); true })
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) { drawLampWishLamp(pose.floatValue) }
            }
            Row(
                Modifier.offset(x = 44.dp, y = 671.dp).size(305.dp, 54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(WishCream)
                    .clickable(role = Role.Button, onClick = ::makeWish)
                    .semantics { contentDescription = if (wished) "Wish made" else "Rub to make a wish"; role = Role.Button },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("✧", color = WishInk, fontSize = 18.sp)
                Text(if (wished) "Wish made" else "Rub to make a wish", Modifier.offset(x = 9.dp), color = WishInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text("ONE SMALL MOMENT. ALL YOURS.", Modifier.offset(y = 740.dp).width(393.dp), color = WishInk.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LampWishVillage() {
    Canvas(Modifier.offset(y = 55.dp).size(393.dp, 300.dp)) {
        drawRect(Color(0xFFFAF6CC), size = androidx.compose.ui.geometry.Size(size.width, 240.dp.toPx()))
        repeat(8) { i ->
            val x = (i * 59f - 20f).dp.toPx()
            drawOval(Color(0xFFA5C878), topLeft = androidx.compose.ui.geometry.Offset(x, (140 - (i % 3) * 15).dp.toPx()), size = androidx.compose.ui.geometry.Size(82.dp.toPx(), 100.dp.toPx()))
        }
        drawRoundRect(Color(0xFFC98D4A), topLeft = androidx.compose.ui.geometry.Offset(32.dp.toPx(), 88.dp.toPx()), size = androidx.compose.ui.geometry.Size(95.dp.toPx(), 146.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        drawRect(Color(0xFFA5DFD9), topLeft = androidx.compose.ui.geometry.Offset(42.dp.toPx(), 104.dp.toPx()), size = androidx.compose.ui.geometry.Size(75.dp.toPx(), 45.dp.toPx()))
        drawRect(Color(0xFFF4C147), topLeft = androidx.compose.ui.geometry.Offset(28.dp.toPx(), 156.dp.toPx()), size = androidx.compose.ui.geometry.Size(104.dp.toPx(), 17.dp.toPx()))
        repeat(5) { i -> drawRect(Color(0xFFB87435), topLeft = androidx.compose.ui.geometry.Offset((28 + i * 21).dp.toPx(), 156.dp.toPx()), size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 17.dp.toPx())) }
        drawRoundRect(Color(0xFF9DCED4), topLeft = androidx.compose.ui.geometry.Offset(282.dp.toPx(), 44.dp.toPx()), size = androidx.compose.ui.geometry.Size(62.dp.toPx(), 190.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
        repeat(8) { i -> drawRoundRect(Color(0xFFC3E5DC), topLeft = androidx.compose.ui.geometry.Offset(290.dp.toPx(), (64 + i * 20).dp.toPx()), size = androidx.compose.ui.geometry.Size(46.dp.toPx(), 9.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())) }
        val roof = Path().apply { moveTo(270.dp.toPx(), 48.dp.toPx()); lineTo(313.dp.toPx(), 4.dp.toPx()); lineTo(355.dp.toPx(), 48.dp.toPx()); close() }
        drawPath(roof, Color(0xFFD9E4DE))
        drawRect(WishCream, topLeft = androidx.compose.ui.geometry.Offset(0f, 229.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 22.dp.toPx()))
        repeat(7) { i -> drawRoundRect(Color(0xFFE9C589), topLeft = androidx.compose.ui.geometry.Offset(((i * 79) % 370).dp.toPx(), (265 + (i % 2) * 18).dp.toPx()), size = androidx.compose.ui.geometry.Size(17.dp.toPx(), 6.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())) }
    }
    Text("COFFEE", Modifier.offset(x = 52.dp, y = 120.dp).width(56.dp), color = Color(0xFF805B65), fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLampWishCat(pose: Float) {
    val cream = WishCream
    val orange = Color(0xFFFFAF30)
    val d = density
    fun oval(color: Color, cx: Float, cy: Float, width: Float, height: Float) {
        drawOval(color, topLeft = androidx.compose.ui.geometry.Offset((cx - width / 2).dp.toPx(), (cy - height / 2).dp.toPx()), size = androidx.compose.ui.geometry.Size(width.dp.toPx(), height.dp.toPx()))
    }
    fun round(color: Color, cx: Float, cy: Float, width: Float, height: Float, degrees: Float = 0f) {
        withTransform({ rotate(degrees, androidx.compose.ui.geometry.Offset(cx * d, cy * d)) }) {
            drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset((cx - width / 2).dp.toPx(), (cy - height / 2).dp.toPx()), size = androidx.compose.ui.geometry.Size(width.dp.toPx(), height.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(min(width, height).dp.toPx() / 2))
        }
    }
    oval(WishInk.copy(alpha = 0.1f), 110f, 232f, 145f, 22f)
    round(orange, 190f, 158f, 85f, 36f, -35f - pose * 28f)
    round(cream, 221f, 137f, 29f, 36f, -35f - pose * 28f)
    round(cream, 72f, 211f, 38f, 64f, 12f + pose * 18f)
    round(cream, 149f, 211f, 38f, 64f, -12f - pose * 18f)
    oval(orange, 110f, 159f, 146f, 145f)
    oval(Color(0xFFFFE1AB), 110f, 164f, 76f, 88f)
    round(cream, 35f, 155f, 36f, 60f, 35f + pose * 35f)
    round(cream, 185f, 155f, 36f, 60f, -35f - pose * 35f)
    round(orange, 52f, 29f, 65f, 76f, -22f)
    round(orange, 168f, 29f, 65f, 76f, 22f)
    oval(cream, 52f, 22f, 29f, 43f)
    oval(cream, 168f, 22f, 29f, 43f)
    oval(orange, 110f, 80f, 190f, 148f)
    oval(cream, 110f, 117f, 180f, 89f)
    round(Color(0xFFF58B28), 83f, 18f, 18f, 28f, -15f)
    round(Color(0xFFF58B28), 110f, 18f, 18f, 28f)
    round(Color(0xFFF58B28), 137f, 18f, 18f, 28f, 15f)
    round(WishInk, 83.5f, 75.5f, 13f, 15f - pose * 8f)
    round(WishInk, 136.5f, 75.5f, 13f, 15f - pose * 8f)
    oval(Color(0xFFE37854), 110f, 92f, 18f, 12f)
    oval(Color(0xFFB8482D), 110f, 112f, 37f + pose * 10f, 20f + pose * 9f)
    round(Color(0xFF78D6DB), 110f, 153f, 120f, 15f)
    oval(Color(0xFFA5EBE9), 110f, 156f, 28f, 28f)
    oval(Color.White.copy(alpha = 0.7f), 104f, 150f, 7f, 7f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLampWishLamp(pose: Float) {
    fun oval(color: Color, cx: Float, cy: Float, width: Float, height: Float, stroke: Float = 0f) {
        val top = androidx.compose.ui.geometry.Offset((cx - width / 2).dp.toPx(), (cy - height / 2).dp.toPx())
        val size = androidx.compose.ui.geometry.Size(width.dp.toPx(), height.dp.toPx())
        if (stroke > 0f) drawOval(color, topLeft = top, size = size, style = Stroke(stroke.dp.toPx())) else drawOval(color, topLeft = top, size = size)
    }
    oval(WishInk.copy(alpha = 0.1f), 87.5f, 106f, 170f, 28f)
    for (i in 0 until 8) {
        val angle = i * PI / 4
        val x = 87.5f + cos(angle).toFloat() * (50f + pose * 40f)
        val y = 40f + sin(angle).toFloat() * (30f + pose * 42f)
        val size = 10f + (i % 3) * 4f
        val radius = size.dp.toPx() / 2
        val centerX = x.dp.toPx()
        val centerY = y.dp.toPx()
        val sparkle = Path().apply {
            moveTo(centerX, centerY - radius)
            lineTo(centerX + radius * 0.2f, centerY - radius * 0.2f)
            lineTo(centerX + radius, centerY)
            lineTo(centerX + radius * 0.2f, centerY + radius * 0.2f)
            lineTo(centerX, centerY + radius)
            lineTo(centerX - radius * 0.2f, centerY + radius * 0.2f)
            lineTo(centerX - radius, centerY)
            lineTo(centerX - radius * 0.2f, centerY - radius * 0.2f)
            close()
        }
        drawPath(sparkle, WishCream.copy(alpha = pose))
    }
    oval(Color(0xFFE7C93E), 30.5f, 52f, 45f, 45f, 10f)
    drawRoundRect(Color(0xFFE7C93E), topLeft = androidx.compose.ui.geometry.Offset(56.5.dp.toPx(), 93.5.dp.toPx()), size = androidx.compose.ui.geometry.Size(62.dp.toPx(), 13.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.5.dp.toPx()))
    oval(Color(0xFFFFE64F), 87.5f, 60f, 105f, 66f)
    val handle = Path().apply { moveTo(113.dp.toPx(), 45.dp.toPx()); quadraticTo(148.dp.toPx(), 58.dp.toPx(), 165.dp.toPx(), 40.dp.toPx()); lineTo(170.dp.toPx(), 48.dp.toPx()); quadraticTo(152.dp.toPx(), 72.dp.toPx(), 123.dp.toPx(), 75.dp.toPx()); close() }
    drawPath(handle, Color(0xFFFFE64F))
    oval(Color(0xFFFFF18B), 87.5f, 29f, 50f, 15f)
    drawRoundRect(Color(0xFF78D6DB), topLeft = androidx.compose.ui.geometry.Offset(79.5.dp.toPx(), 8.5.dp.toPx()), size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 21.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
    val heart = Path().apply { moveTo(87.5.dp.toPx(), 65.dp.toPx()); cubicTo(70.dp.toPx(), 46.dp.toPx(), 57.dp.toPx(), 68.dp.toPx(), 87.5.dp.toPx(), 90.dp.toPx()); cubicTo(118.dp.toPx(), 68.dp.toPx(), 105.dp.toPx(), 46.dp.toPx(), 87.5.dp.toPx(), 65.dp.toPx()); close() }
    drawPath(heart, Color(0xFF78D6DB))
}
