package com.ngocdevv.nativecook.blocks.cards.apexreviewrelief

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min

private const val APEX_REVIEW_RELIEF_LOOP = 8.0
private val ReliefCream = Color(0xFFFFF1EA)
private val ReliefGold = Color(0xFFFFC66D)

private fun apexReviewReliefSmooth(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun apexReviewReliefLoopPose(seconds: Double): Float {
    return apexReviewReliefSmooth(((seconds - 1.4) / 1.6).toFloat()) *
        (1f - apexReviewReliefSmooth(((seconds - 4.8) / 1.6).toFloat()))
}

@Composable
fun ApexReviewRelief(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    BlockSystemBars(darkIcons = false)
    val reduced = rememberLiveReducedMotion()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current
    val pose = remember { mutableFloatStateOf(0f) }
    val loopSeconds = remember { mutableDoubleStateOf(0.0) }
    val revision = remember { mutableIntStateOf(0) }
    val rating = remember { mutableIntStateOf(4) }
    val visible = remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val activeReduced = rememberUpdatedState(reduced)
    var gestureOrigin by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnSeconds by remember { mutableFloatStateOf(0f) }

    fun adjustRelief(value: Float): Boolean {
        if (!reduced && previewTime == null) {
            pose.floatValue = value
            returnFrom = value
            returnSeconds = 0f
            dragging = false
            returning = true
            revision.intValue++
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        return true
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                visible.value = false
            } else if (event == Lifecycle.Event.ON_START) {
                visible.value = true
                pose.floatValue = if (activeReduced.value) 0.7f else 0f
                loopSeconds.doubleValue = 0.0
                dragging = false
                returning = false
                revision.intValue++
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(autoPlay, reduced, previewTime, revision.intValue, visible.value) {
        if (reduced) {
            dragging = false
            returning = false
            loopSeconds.doubleValue = 0.0
            pose.floatValue = 0.7f
            return@LaunchedEffect
        }
        if (previewTime != null) {
            loopSeconds.doubleValue = ((previewTime % APEX_REVIEW_RELIEF_LOOP) + APEX_REVIEW_RELIEF_LOOP) % APEX_REVIEW_RELIEF_LOOP
            pose.floatValue = apexReviewReliefLoopPose(loopSeconds.doubleValue)
            return@LaunchedEffect
        }
        if (!visible.value) return@LaunchedEffect
        if (!dragging && !returning && !autoPlay) {
            pose.floatValue = 0f
            return@LaunchedEffect
        }
        var previous = withFrameNanos { it }
        while (visible.value && !reduced && previewTime == null) {
            val now = withFrameNanos { it }
            val delta = ((now - previous).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()
            previous = now
            if (returning) {
                returnSeconds += delta
                if (returnSeconds <= 2f) {
                    pose.floatValue = returnFrom
                } else {
                    val amount = (returnSeconds - 2f) / 0.45f
                    pose.floatValue = returnFrom * (1f - apexReviewReliefSmooth(amount))
                    if (amount >= 1f) {
                        pose.floatValue = 0f
                        returning = false
                        dragging = false
                        returnSeconds = 0f
                        loopSeconds.doubleValue = 0.0
                        if (!autoPlay) break
                    }
                }
            } else if (!dragging && autoPlay) {
                loopSeconds.doubleValue = (loopSeconds.doubleValue + delta) % APEX_REVIEW_RELIEF_LOOP
                pose.floatValue = apexReviewReliefLoopPose(loopSeconds.doubleValue)
            } else if (!dragging) {
                break
            }
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color(0xFFB91529),
                0.45f to Color(0xFF390911),
                0.8f to Color(0xFF100B10),
                1f to Color(0xFF100B10),
            ),
        ).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val contentWidth = maxWidth
        val contentHeight = maxHeight
        val scale = min(contentWidth.value / 393f, contentHeight.value / 760f)
        Box(
            Modifier
                .align(Alignment.Center)
                .requiredSize(393.dp, 760.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Row(Modifier.offset(x = 30.dp).width(333.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⚑", color = ReliefCream, fontSize = 15.sp)
                Text("APEX / RACE JOURNAL", Modifier.offset(x = 5.dp), color = ReliefCream, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Text("08", Modifier.weight(1f), color = ReliefCream, fontSize = 13.sp, textAlign = TextAlign.End, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Text("Ember Coast", Modifier.offset(y = 59.dp).width(393.dp), color = ReliefCream, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("ROUND 08  ·  SUNDAY SESSION", Modifier.offset(y = 108.dp).width(393.dp), color = ReliefCream.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.7.sp, textAlign = TextAlign.Center)

            Box(
                Modifier
                    .offset(x = 30.dp, y = 132.dp)
                    .size(333.dp, 315.dp)
                    .graphicsLayer {
                        val amount = pose.floatValue
                        rotationX = amount * 14f
                        rotationY = amount * -18f
                        cameraDistance = 12f * density
                    }
                    .semantics {
                        contentDescription = "Ember Coast circuit relief. Drag right to lift the circuit."
                        customActions = listOf(
                            CustomAccessibilityAction("Lift circuit") { adjustRelief(1f) },
                            CustomAccessibilityAction("Rest circuit") { adjustRelief(0f) },
                        )
                    }
                    .pointerInput(reduced, previewTime) {
                        detectDragGestures(
                            onDragStart = {
                                if (!reduced && previewTime == null) {
                                    gestureOrigin = pose.floatValue
                                    dragging = true
                                    returning = false
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    revision.intValue++
                                }
                            },
                            onDrag = { change, amount ->
                                if (!reduced && previewTime == null && dragging) {
                                    change.consume()
                                    gestureOrigin += amount.x / view.resources.displayMetrics.density / 150f
                                    pose.floatValue = gestureOrigin.coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                if (!reduced && previewTime == null && dragging) {
                                    returnFrom = pose.floatValue
                                    returnSeconds = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                            onDragCancel = {
                                if (!reduced && previewTime == null && dragging) {
                                    returnFrom = pose.floatValue
                                    returnSeconds = 0f
                                    returning = true
                                    revision.intValue++
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(220.dp)) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0x40FF4C50), Color.Transparent)),
                        radius = size.minDimension / 2,
                    )
                }
                for (depth in 2 downTo 0) {
                    val shape = RoundedCornerShape(44.dp)
                    Box(
                        Modifier
                            .size(225.dp)
                            .graphicsLayer {
                                val amount = pose.floatValue
                                translationX = depth * (8f + amount * 9f) * density
                                translationY = depth * (11f + amount * 8f) * density
                                rotationZ = -12f + amount * 7f
                            }
                            .shadow(28.dp, shape, clip = false)
                            .background(Brush.linearGradient(listOf(Color(0xFFC52739), Color(0xFF651421))), shape),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRoundRect(Color.White.copy(alpha = if (depth == 0) 0.18f else 0.07f), style = Stroke(1.dp.toPx()), cornerRadius = CornerRadius(44.dp.toPx()))
                        }
                    }
                }
                Box(
                    Modifier
                        .size(225.dp)
                        .graphicsLayer {
                            val amount = pose.floatValue
                            rotationZ = -12f + amount * 7f
                            translationX = -5f * density
                            translationY = (-6f - amount * 9f) * density
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.offset(y = (-30.5).dp).size(145.dp, 100.dp)) {
                        val amount = pose.floatValue
                        val points = listOf(
                            0.05f to 0.82f, 0.28f to 0.12f, 0.48f to 0.12f, 0.45f to 0.43f,
                            0.70f to 0.31f, 0.77f to 0.08f, 0.93f to 0.24f, 0.84f to 0.62f,
                            0.56f to 0.68f, 0.39f to 0.9f, 0.05f to 0.82f,
                        )
                        val route = Path().apply {
                            points.forEachIndexed { index, point ->
                                val location = Offset(point.first * size.width, point.second * size.height)
                                if (index == 0) moveTo(location.x, location.y) else lineTo(location.x, location.y)
                            }
                            close()
                        }
                        val pixelScale = density
                        repeat(9) { step ->
                            withTransform({ translate(0f, -step * (1.1f + amount * 1.2f) * pixelScale) }) {
                                drawPath(route, if (step == 8) Color(0xFFF04E59) else Color(0xFF6A1023), style = Stroke(17.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                        withTransform({ translate(0f, (-8.8f - amount * 9.6f) * pixelScale) }) {
                            drawPath(route, ReliefCream.copy(alpha = 0.25f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    Column(Modifier.offset(y = 47.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("EMBER GP", color = Color(0xFFF2A1A6), fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("CIRCUIT NO. 08", Modifier.offset(y = 7.dp), color = Color(0xFFE58A94), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
                Box(
                    Modifier
                        .offset(x = 96.dp, y = (-83).dp)
                        .graphicsLayer {
                            val amount = pose.floatValue
                            translationY = -amount * 16f * density
                            rotationZ = -16f
                            scaleX = 1f + amount * 0.06f
                            scaleY = scaleX
                        }
                        .shadow(10.dp, RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFD9394C), Color(0xFF670F23))), RoundedCornerShape(12.dp))
                        .semantics { contentDescription = "Five star circuit rating" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("★★★★★", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = ReliefGold, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }

            Row(Modifier.offset(y = 452.dp).width(393.dp).height(56.dp), horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                ApexReviewReliefLaurel(false)
                Text("4.8", color = ReliefCream, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
                ApexReviewReliefLaurel(true)
            }
            Text("A weekend worth remembering.", Modifier.offset(y = 510.dp).width(393.dp), color = ReliefCream.copy(alpha = 0.65f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Row(Modifier.offset(x = 25.dp, y = 548.dp).width(343.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                ApexReviewReliefMetric("129", "RATINGS")
                Box(Modifier.width(1.dp).height(32.dp).background(ReliefCream.copy(alpha = 0.12f)))
                ApexReviewReliefMetric("2.4k", "REVIEWS")
                Box(Modifier.width(1.dp).height(32.dp).background(ReliefCream.copy(alpha = 0.12f)))
                ApexReviewReliefMetric("08", "ROUND")
            }
            Row(
                Modifier.offset(x = 30.dp, y = 615.dp).size(333.dp, 66.dp)
                    .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(40.dp))
                    .border(1.dp, ReliefCream.copy(alpha = 0.12f), RoundedCornerShape(40.dp))
                    .semantics { contentDescription = "Your rating" },
                horizontalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (star in 1..5) {
                    Box(
                        Modifier.size(44.dp, 54.dp)
                            .clickable {
                                rating.intValue = star
                                returnFrom = pose.floatValue
                                returnSeconds = 0f
                                dragging = false
                                returning = true
                                revision.intValue++
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            .semantics {
                                role = Role.Button
                                contentDescription = "Rate $star stars"
                                selected = rating.intValue == star
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("★", color = if (star <= rating.intValue) ReliefGold else ReliefCream.copy(alpha = 0.16f), fontSize = 26.sp)
                    }
                }
            }
            Text("YOUR RATING  /  ${rating.intValue} OF 5", Modifier.offset(y = 693.dp).width(393.dp), color = ReliefCream.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            Text("Drag the circuit. Relive the race.", Modifier.align(Alignment.BottomCenter).height(25.dp), color = ReliefCream.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RowScope.ApexReviewReliefMetric(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, color = ReliefCream, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Text(label, color = ReliefCream.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp)
    }
}

@Composable
private fun ApexReviewReliefLaurel(mirror: Boolean) {
    Canvas(Modifier.size(16.dp, 32.dp).graphicsLayer { scaleX = if (mirror) -1f else 1f }) {
        val unit = density
        val stem = Path().apply { moveTo(14f * unit, 30f * unit); quadraticTo(1f * unit, 19f * unit, 9f * unit, 2f * unit) }
        drawPath(stem, ReliefGold, style = Stroke(1.2f * unit))
        repeat(5) { i ->
            val left = Offset((5f + i * 0.9f) * unit, (6f + i * 4.8f) * unit)
            val right = Offset((10f + i * 0.3f) * unit, (4f + i * 4.8f) * unit)
            withTransform({ rotate(-42f, left) }) { drawOval(ReliefGold, left - Offset(1.8f * unit, 3.5f * unit), androidx.compose.ui.geometry.Size(3.6f * unit, 7f * unit)) }
            withTransform({ rotate(36f, right) }) { drawOval(ReliefGold, right - Offset(1.6f * unit, 3.2f * unit), androidx.compose.ui.geometry.Size(3.2f * unit, 6.4f * unit)) }
        }
    }
}
