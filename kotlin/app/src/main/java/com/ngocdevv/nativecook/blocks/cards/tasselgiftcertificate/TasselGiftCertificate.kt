package com.ngocdevv.nativecook.blocks.cards.tasselgiftcertificate

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

private const val TASSEL_LOOP = 8.0
private val TasselInk = Color(0xFF272832)
private val TasselAccent = Color(0xFF731C38)
private fun tasselClamp(value: Double, lo: Double = 0.0, hi: Double = 1.0) = value.coerceIn(lo, hi)
private fun tasselSmooth(value: Double): Double { val u = tasselClamp(value); return u * u * (3.0 - 2.0 * u) }
private fun tasselPose(time: Double): Float = (tasselSmooth((time - 1.4) / 1.8) * (1.0 - tasselSmooth((time - 4.8) / 1.8))).toFloat()
private fun tasselTextPaint(color: Color, size: Float, family: Typeface = Typeface.create("sans-serif", Typeface.NORMAL), align: Paint.Align = Paint.Align.CENTER) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); textSize = size; textAlign = align; typeface = family }

@Composable
fun TasselGiftCertificate(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    val displayDensity = LocalDensity.current
    BlockSystemBars(darkIcons = true)
    val startTime = previewTime?.let { ((it % TASSEL_LOOP) + TASSEL_LOOP) % TASSEL_LOOP } ?: 0.0
    val pose = remember { mutableFloatStateOf(if (reduced) 1f else if (previewTime == null) 0f else tasselPose(startTime)) }
    val clock = remember { mutableDoubleStateOf(startTime) }
    val revision = remember { mutableIntStateOf(0) }
    var observation by remember { mutableIntStateOf(if (reduced || (previewTime != null && tasselPose(startTime) > 0.7f)) 1 else 0) }
    var active by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var touching by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var returning by remember { mutableStateOf(false) }
    var activated by remember { mutableStateOf(false) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var detent by remember { mutableStateOf(false) }
    var returnFrom by remember { mutableFloatStateOf(0f) }
    var returnElapsed by remember { mutableFloatStateOf(0f) }
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
    fun save() {
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
            clock.doubleValue = ((previewTime % TASSEL_LOOP) + TASSEL_LOOP) % TASSEL_LOOP
            pose.floatValue = tasselPose(clock.doubleValue)
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
                        pose.floatValue = if (previewTime == null) 0f else tasselPose(((previewTime % TASSEL_LOOP) + TASSEL_LOOP) % TASSEL_LOOP)
                        returning = false
                        touching = false
                        manual = false
                        returnElapsed = 0f
                        clock.doubleValue = 0.0
                        if (previewTime != null || !autoPlay) break
                    }
                }
            } else if (!touching && autoPlay) {
                clock.doubleValue = (clock.doubleValue + dt) % TASSEL_LOOP
                pose.floatValue = tasselPose(clock.doubleValue)
            } else if (!touching) break
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFFEEE8E1)).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 760f)
        Box(Modifier.align(Alignment.Center).requiredSize(393.dp, 760.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Row(Modifier.offset(x = 28.dp, y = 20.dp).width(337.dp).height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("♙", color = TasselInk, fontSize = 15.sp)
                Text("ATELIER / A SMALL GIFT", Modifier.offset(x = 8.dp), color = TasselInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
                Text("0148", Modifier.weight(1f), color = TasselInk.copy(alpha = 0.55f), fontSize = 10.sp, textAlign = TextAlign.End, fontFamily = FontFamily.Monospace)
            }
            Text("Something\njust for you.", Modifier.offset(y = 79.dp).width(393.dp), color = TasselInk, fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp, lineHeight = 41.sp, textAlign = TextAlign.Center)
            Text("A keepsake for a moment of your own.", Modifier.offset(y = 180.dp).width(393.dp), color = TasselInk.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
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
                                    dragDistance += amount.x / displayDensity.density / scale
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
                        contentDescription = "Tassel Gift Certificate"
                        stateDescription = if (observation == 1) "Tassel fanned" else "Tassel ready to pull"
                        customActions = listOf(
                            CustomAccessibilityAction("Fan tassel") { setPose(1f); true },
                            CustomAccessibilityAction("Reset tassel") { setPose(0f); true },
                        )
                    },
            ) {
                val pixelScale = this.density
                val p = pose.floatValue
                val center = Offset(168f * pixelScale, 190f * pixelScale)
                rotate(-7f + p * 1.5f, center) {
                    drawRoundRect(Color.Black.copy(alpha = 0.13f), Offset(56f * pixelScale, 55f * pixelScale), androidx.compose.ui.geometry.Size(225f * pixelScale, 262f * pixelScale), androidx.compose.ui.geometry.CornerRadius(3f * pixelScale))
                    drawRoundRect(TasselAccent, Offset(56f * pixelScale, 55f * pixelScale), androidx.compose.ui.geometry.Size(225f * pixelScale, 262f * pixelScale), androidx.compose.ui.geometry.CornerRadius(3f * pixelScale))
                }
                rotate(2f + p * 1.5f, center) {
                    drawRoundRect(Color(0xFFFFFCF5), Offset(58f * pixelScale, 75f * pixelScale), androidx.compose.ui.geometry.Size(220f * pixelScale, 233f * pixelScale), androidx.compose.ui.geometry.CornerRadius(3f * pixelScale))
                    drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(58f * pixelScale, 75f * pixelScale), androidx.compose.ui.geometry.Size(220f * pixelScale, 233f * pixelScale), androidx.compose.ui.geometry.CornerRadius(3f * pixelScale), style = Stroke(1f * pixelScale))
                    for (i in 0 until 12) drawCircle(TasselAccent, 4f * pixelScale, Offset((69f + i * 18f) * pixelScale, 304f * pixelScale))
                    val native = drawContext.canvas.nativeCanvas
                    native.drawText("GIFT", 168f * pixelScale, 146f * pixelScale, tasselTextPaint(TasselInk, 36f * pixelScale, Typeface.create("sans-serif", Typeface.BOLD)))
                    native.drawText("FOR A LITTLE MOMENT", 168f * pixelScale, 174f * pixelScale, tasselTextPaint(TasselAccent, 9f * pixelScale, Typeface.create("sans-serif", Typeface.BOLD)))
                    native.drawText("TO     Someone wonderful", 166f * pixelScale, 219f * pixelScale, tasselTextPaint(TasselInk.copy(alpha = 0.7f), 10f * pixelScale))
                    drawLine(TasselInk.copy(alpha = 0.15f), Offset(81f * pixelScale, 235f * pixelScale), Offset(254f * pixelScale, 235f * pixelScale), 1f * pixelScale)
                    native.drawText("A keepsake, chosen with care.", 168f * pixelScale, 266f * pixelScale, tasselTextPaint(TasselInk.copy(alpha = 0.55f), 9f * pixelScale, Typeface.create("sans-serif", Typeface.NORMAL)))
                    native.drawText("CERTIFICATE     0148", 168f * pixelScale, 292f * pixelScale, tasselTextPaint(TasselAccent, 8f * pixelScale))
                }
                drawCircle(Color(0xFFA56C41), 7f * pixelScale, Offset(243f * pixelScale, 82f * pixelScale), style = Stroke(3f * pixelScale))
                val knot = Offset((243f + 40f * p) * pixelScale, (40f - 12f * p) * pixelScale)
                val cord = Path().apply { moveTo(243f * pixelScale, 82f * pixelScale); quadraticTo((268f + 34f * p) * pixelScale, (59f + 12f * p) * pixelScale, knot.x, knot.y) }
                drawPath(cord, TasselAccent, style = Stroke(4f * pixelScale))
                for (i in 0 until 24) {
                    val n = i - 11.5f
                    val end = Offset((243f + 40f * p + n * (1.25f + 1.3f * p)) * pixelScale, (4f + sin(i * 0.7f) * 5f) * pixelScale)
                    val strand = Path().apply { moveTo(knot.x, knot.y); quadraticTo((243f + 40f * p + n * 0.3f + 19f * p) * pixelScale, 19f * pixelScale, end.x, end.y) }
                    drawPath(strand, if (i % 3 == 0) Color(0xFFB64460) else TasselAccent, style = Stroke((if (i % 3 == 0) 1.3f else 0.85f) * pixelScale))
                }
                drawCircle(TasselAccent, 5f * pixelScale, knot)
            }
            Column(Modifier.offset(y = 574.dp).width(393.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (activated) "Saved to your keepsakes" else "A small gesture. A lasting memory.", color = TasselInk, fontSize = 17.sp, fontFamily = FontFamily.Serif)
                Text("THOUGHTFULLY CHOSEN  /  NO. 0148", color = TasselInk.copy(alpha = 0.5f), fontSize = 8.sp, letterSpacing = 1.7.sp)
            }
            Row(
                Modifier.offset(x = 28.dp, y = 653.dp).size(337.dp, 54.dp).clip(RoundedCornerShape(18.dp)).background(TasselAccent)
                    .graphicsLayer { val pressScale = if (pressed && !reduced) 0.97f else 1f; scaleX = pressScale; scaleY = pressScale; alpha = if (pressed) 0.8f else 1f }
                    .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = ::save)
                    .semantics { contentDescription = if (activated) "Saved to keepsakes" else "Save keepsake"; role = Role.Button },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (activated) "✓" else "↗", color = Color.White, fontSize = 15.sp)
                Text(if (activated) "Saved to keepsakes" else "Save keepsake", Modifier.offset(x = 9.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("DRAG SIDEWAYS ON THE TASSEL", Modifier.offset(y = 731.dp).width(393.dp), color = TasselInk.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp, textAlign = TextAlign.Center)
        }
    }
}
