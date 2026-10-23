package com.ngocdevv.nativecook.blocks.charts.skyscreentime

import android.content.Context
import android.graphics.BitmapFactory
import android.view.accessibility.AccessibilityManager
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val Sky = Color(0xFF2E70E9)
private val Navy = Color(0xFF1E325B)
private val Axis = Color(0xFF8A8C94)
private val Ink = Color(0xFF000000)
private val Green = Color(0xFF57BF71)
private val Neutral = Color(0xFF858796)
private val Amber = Color(0xFFF0B13F)
private val CloudBlue = Color(0xFF3988DE)
// An assignable family lets Compose resolve bold and semibold weights correctly.
private val SkyFontFamily = FontFamily.SansSerif

private data class SkyScreenTimeLabels(
    val title: String,
    val selectedLabel: String,
    val selectedIndex: Int?,
    val percentages: List<Int>,
    val focusSeconds: Int,
)

private fun skyScreenTimeLabels(frame: SkyScreenTimeFrame, day: Int): SkyScreenTimeLabels {
    val data = frame.selectedIndex?.let { SkyScreenTimeData.hours(day)[it] } ?: SkyScreenTimeData.total(day)
    val divisor = max(0.01, data.minutes)
    return SkyScreenTimeLabels(
        title = frame.title,
        selectedLabel = frame.selectedIndex?.let(SkyScreenTimeData::timeLabel) ?: "Quality",
        selectedIndex = frame.selectedIndex,
        percentages = listOf(data.productive, data.neutral, data.distracted).map { (it / divisor * 100).roundToInt() },
        focusSeconds = frame.focusSeconds,
    )
}

private fun skyScreenTimeNow(): Double = SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0

@Composable
fun SkyScreenTime(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val reducedMotion = rememberReducedMotion()
    val systemAccessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager }
    var touchExploration by remember(systemAccessibility) { mutableStateOf(systemAccessibility?.isTouchExplorationEnabled() == true) }
    DisposableEffect(systemAccessibility) {
        val manager = systemAccessibility
        if (manager == null) return@DisposableEffect onDispose {}
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled -> touchExploration = enabled }
        manager.addTouchExplorationStateChangeListener(listener)
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    var fewerEffects by remember { mutableStateOf(false) }
    val quiet = reducedMotion || touchExploration || fewerEffects

    val motion = remember { SkyScreenTimeMotion() }
    val initial = remember { motion.frame(skyScreenTimeNow()) }
    val liveFrame = remember { mutableStateOf(initial) }
    val labels = remember { mutableStateOf(skyScreenTimeLabels(initial, motion.day)) }
    var cadence by remember { mutableStateOf(SkyScreenTimeMotion.Cadence.STOPPED) }
    var active by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var initialized by remember { mutableStateOf(false) }
    var previousAutoPlay by remember { mutableStateOf(autoPlay) }
    var previousQuiet by remember { mutableStateOf(quiet) }
    var wakeRevision by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var day by remember { mutableIntStateOf(0) }
    val skyImage = remember(context) { context.assets.open("skyscreentime/skyscreentime_sky.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }
    val cloudImage = remember(context) { context.assets.open("skyscreentime/skyscreentime_clouds-near.png").use { BitmapFactory.decodeStream(it).asImageBitmap() } }

    fun wake() { wakeRevision += 1 }
    fun updateLabels(frame: SkyScreenTimeFrame) {
        val next = skyScreenTimeLabels(frame, motion.day)
        if (labels.value != next) labels.value = next
    }
    fun publish(at: Double) {
        val frame = motion.frame(at)
        if (liveFrame.value != frame) liveFrame.value = frame
        updateLabels(frame)
        val nextCadence = motion.cadence(at)
        if (cadence != nextCadence) cadence = nextCadence
    }

    LaunchedEffect(autoPlay, quiet) {
        val at = skyScreenTimeNow()
        if (!initialized) {
            motion.appear(autoPlay = autoPlay, quiet = quiet, at = at)
            motion.setActive(active, at)
            initialized = true
        } else {
            if (previousAutoPlay != autoPlay) motion.setAutoPlay(autoPlay, at)
            if (previousQuiet != quiet) motion.setQuiet(quiet, at)
            previousAutoPlay = autoPlay
            previousQuiet = quiet
        }
        publish(at)
        wake()
    }

    DisposableEffect(lifecycleOwner, motion) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    active = true
                    motion.setActive(true, skyScreenTimeNow())
                    publish(skyScreenTimeNow())
                    wake()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    active = false
                    touching = false
                    motion.setActive(false, skyScreenTimeNow())
                    publish(skyScreenTimeNow())
                    wake()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(wakeRevision, cadence, active, motion) {
        if (!initialized) return@LaunchedEffect
        if (!active) return@LaunchedEffect
        if (cadence == SkyScreenTimeMotion.Cadence.STOPPED) {
            publish(skyScreenTimeNow())
            return@LaunchedEffect
        }
        do {
            val at = skyScreenTimeNow()
            publish(at)
            if (cadence == SkyScreenTimeMotion.Cadence.ANIMATION) {
                withFrameNanos { }
            } else {
                delay(1_000L)
            }
        } while (active && cadence != SkyScreenTimeMotion.Cadence.STOPPED)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Sky)) {
        val scale = maxWidth.value / 402f
        val pxScale = scale * density.density
        val safeTop = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
        val safeBottom = with(density) { androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(this).toDp() }
        val viewportHeight = maxHeight - safeTop - safeBottom
        val desiredHeight = (812f + if (expanded) 94f else 0f).dp * scale
        val contentHeight = if (viewportHeight > desiredHeight) viewportHeight else desiredHeight
        val expandedFraction by animateFloatAsState(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = if (quiet) snap() else spring(stiffness = 520f, dampingRatio = 0.82f),
            label = "screen-time-details",
        )

        SkyScreenTimeAtmosphere(
            frame = liveFrame,
            sky = skyImage,
            clouds = cloudImage,
            pxScale = pxScale,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeight)
                .offset(y = safeTop)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(contentHeight)) {
                SkyScreenTimeHeader(
                    scale = scale,
                    day = day,
                    fewerEffects = fewerEffects,
                    onDay = { selected ->
                        motion.chooseDay(selected, skyScreenTimeNow())
                        day = selected
                        publish(skyScreenTimeNow())
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        wake()
                    },
                    onReset = {
                        motion.reset(skyScreenTimeNow())
                        publish(skyScreenTimeNow())
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        wake()
                    },
                    onFewerEffects = { fewerEffects = it },
                    onClear = {
                        motion.reset(skyScreenTimeNow())
                        publish(skyScreenTimeNow())
                        wake()
                    },
                )

                SkyScreenTimeHero(frame = liveFrame, labels = labels, scale = scale)

                SkyScreenTimeFaces(labels = labels, scale = scale)

                Row(
                    modifier = Modifier.offset(x = 193.dp * scale, y = 288.dp * scale).size(16.dp * scale, 6.dp * scale),
                    horizontalArrangement = Arrangement.spacedBy(5.dp * scale),
                ) {
                    Spacer(Modifier.weight(1f).fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                    Spacer(Modifier.weight(1f).fillMaxSize().clip(CircleShape).background(Color.White))
                }

                SkyScreenTimeStatistics(
                    frame = liveFrame,
                    labels = labels,
                    day = day,
                    scale = scale,
                    pxScale = pxScale,
                    expandedFraction = expandedFraction,
                    expanded = expanded,
                    onToggleDetails = {
                        expanded = !expanded
                    },
                    onScrubStart = {
                        motion.beginScrub(skyScreenTimeNow())
                        touching = true
                        wake()
                    },
                    onScrub = { xPx ->
                        motion.scrub((xPx / pxScale - 6.0) / (292.0 / 24.0))
                        publish(skyScreenTimeNow())
                    },
                    onScrubEnd = {
                        if (touching) {
                            touching = false
                            motion.endScrub(skyScreenTimeNow())
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            publish(skyScreenTimeNow())
                            wake()
                        }
                    },
                    onAdjust = { increment ->
                        val selected = labels.value.selectedIndex ?: 0
                        motion.beginScrub(skyScreenTimeNow())
                        motion.scrub((selected + if (increment) 1 else -1).toDouble())
                        motion.endScrub(skyScreenTimeNow())
                        publish(skyScreenTimeNow())
                        wake()
                    },
                )

                SkyScreenTimeFocusButton(
                    labels = labels,
                    scale = scale,
                    top = 646.dp * scale + 94.dp * scale * expandedFraction,
                    onClick = {
                        motion.toggleFocus(skyScreenTimeNow())
                        publish(skyScreenTimeNow())
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        wake()
                    },
                )
            }
        }

        SkyScreenTimeNavigation(
            scale = scale,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun SkyScreenTimeAtmosphere(
    frame: State<SkyScreenTimeFrame>,
    sky: ImageBitmap,
    clouds: ImageBitmap,
    pxScale: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val state = frame.value
        val storm = state.storm.toFloat()
        val imageScale = max(size.width / sky.width, size.height / sky.height)
        val imageWidth = sky.width * imageScale
        val imageHeight = sky.height * imageScale
        val filter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - storm * 0.65f) })
        drawImage(
            image = sky,
            dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - imageWidth) / 2).toInt(), ((size.height - imageHeight) / 2).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(imageWidth.toInt(), imageHeight.toInt()),
            colorFilter = filter,
        )
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to Sky.copy(alpha = 0.55f), (80f / 250f) to Sky.copy(alpha = 0.55f), 1f to Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.26f),
                radius = 250f * pxScale,
            ),
            alpha = 1f - storm * 0.3f,
        )
        val targetW = 430f * pxScale
        val targetH = 500f * pxScale
        val cloudScale = min(targetW / clouds.width, targetH / clouds.height)
        val cloudW = clouds.width * cloudScale
        val cloudH = clouds.height * cloudScale
        val cloudX = (size.width - cloudW) / 2 + (-25f + storm * 30f) * pxScale
        val cloudY = 30f * pxScale + storm * 30f * pxScale
        drawImage(
            image = clouds,
            dstOffset = androidx.compose.ui.unit.IntOffset(cloudX.toInt(), cloudY.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(cloudW.toInt(), cloudH.toInt()),
            alpha = storm * 0.85f,
        )
        drawRect(Navy.copy(alpha = storm * 0.30f))
    }
}

@Composable
private fun SkyScreenTimeHeader(
    scale: Float,
    day: Int,
    fewerEffects: Boolean,
    onDay: (Int) -> Unit,
    onReset: () -> Unit,
    onFewerEffects: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var dayMenu by remember { mutableStateOf(false) }
    var optionsMenu by remember { mutableStateOf(false) }
    Box(Modifier.offset(x = 20.dp * scale, y = 10.dp * scale).size(362.dp * scale, 44.dp * scale)) {
        Canvas(Modifier.align(Alignment.CenterStart).size(90.dp * scale, 35.dp * scale).semantics { contentDescription = "Moru" }) {
            val px = size.width / 90f
            val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                textSize = 31f * px
                textAlign = AndroidPaint.Align.CENTER
                style = AndroidPaint.Style.STROKE
                strokeWidth = 2.8f * px
                strokeJoin = AndroidPaint.Join.ROUND
                color = android.graphics.Color.WHITE
                letterSpacing = 0.02f
            }
            drawContext.canvas.nativeCanvas.drawText("MORU", size.width / 2, size.height * 0.79f, paint)
            paint.style = AndroidPaint.Style.FILL
            paint.color = android.graphics.Color.argb(30, 255, 255, 255)
            drawContext.canvas.nativeCanvas.drawText("MORU", size.width / 2, size.height * 0.79f, paint)
        }
        Box(Modifier.align(Alignment.Center).width(106.dp * scale).height(44.dp * scale)) {
            Row(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).clickable { dayMenu = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(if (day == 0) "Today" else "Yesterday", color = Color.White, fontSize = 15.sp * scale, fontWeight = FontWeight.Bold, fontFamily = SkyFontFamily)
                Spacer(Modifier.width(7.dp * scale))
                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp * scale), tint = Color.White)
            }
            DropdownMenu(expanded = dayMenu, onDismissRequest = { dayMenu = false }) {
                DropdownMenuItem(text = { Text("Today") }, onClick = { dayMenu = false; onDay(0) })
                DropdownMenuItem(text = { Text("Yesterday") }, onClick = { dayMenu = false; onDay(1) })
            }
        }
        SkyScreenTimeCircleAction(
            modifier = Modifier.align(Alignment.Center).offset(x = 105.dp * scale),
            icon = { Icon(Icons.Filled.Refresh, null, Modifier.size(22.dp * scale), tint = Color.White) },
            label = "Reset daily overview",
            onClick = onReset,
            scale = scale,
        )
        Box(Modifier.align(Alignment.Center).offset(x = 158.dp * scale)) {
            SkyScreenTimeCircleAction(
                icon = { Icon(Icons.Filled.MoreHoriz, null, Modifier.size(22.dp * scale), tint = Color.White) },
                label = "Screen time options",
                onClick = { optionsMenu = true },
                scale = scale,
            )
            DropdownMenu(expanded = optionsMenu, onDismissRequest = { optionsMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Reduce effects") },
                    trailingIcon = { Switch(checked = fewerEffects, onCheckedChange = null) },
                    onClick = { optionsMenu = false; onFewerEffects(!fewerEffects) },
                )
                DropdownMenuItem(text = { Text("Clear selected hour") }, onClick = { optionsMenu = false; onClear() })
            }
        }
    }
}

@Composable
private fun SkyScreenTimeCircleAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    scale: Float,
) {
    Box(
        modifier = modifier.size(44.dp * scale).clip(CircleShape)
            .background(Color.Blue.copy(alpha = 0.10f))
            .border(1.dp * scale, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun SkyScreenTimeHero(frame: State<SkyScreenTimeFrame>, labels: State<SkyScreenTimeLabels>, scale: Float) {
    Text(
        text = labels.value.selectedLabel,
        modifier = Modifier.offset(y = 118.dp * scale).fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = Color.White,
        fontSize = 18.sp * scale,
        fontWeight = FontWeight.SemiBold,
        fontFamily = SkyFontFamily,
    )
    Box(
        Modifier.offset(y = 140.dp * scale).fillMaxWidth().height(71.dp * scale)
            .clearAndSetSemantics { contentDescription = labels.value.title },
        contentAlignment = Alignment.Center,
    ) {
        val titles = listOf("Productive", "Balanced", "Distracted")
        titles.forEachIndexed { index, title ->
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp * scale).graphicsLayer {
                    alpha = frame.value.titleOpacities[index].toFloat()
                }.clearAndSetSemantics {},
                textAlign = TextAlign.Center,
                color = Color.White,
                fontSize = 58.sp * scale,
                lineHeight = 71.sp * scale,
                letterSpacing = (-2f * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SkyFontFamily,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun SkyScreenTimeFaces(labels: State<SkyScreenTimeLabels>, scale: Float) {
    Row(
        modifier = Modifier.offset(x = 124.dp * scale, y = 215.dp * scale).width(165.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(11.dp * scale),
    ) {
        repeat(3) { index ->
            Column(
                modifier = Modifier.width(44.dp * scale).semantics(mergeDescendants = true) {
                    contentDescription = "${listOf("Productive", "Neutral", "Distracted")[index]}, ${labels.value.percentages[index]} percent"
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp * scale),
            ) {
                SkyScreenTimeFace(index, scale, Modifier.size(28.dp * scale))
                Text(
                    text = "${labels.value.percentages[index]}%",
                    color = Color.White,
                    fontSize = 16.sp * scale,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SkyFontFamily,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun SkyScreenTimeFace(kind: Int, scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 28f
        val shape = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(0.5f * s, 0.5f * s, size.width - 0.5f * s, size.height - 0.5f * s), CornerRadius(8f * s)))
        }
        drawPath(shape, if (kind == 0) Color(0xFF60CA6A) else Color.White.copy(alpha = 0.04f))
        drawPath(shape, Color.White.copy(alpha = if (kind == 0) 0.15f else 0.45f), style = Stroke(s))
        if (kind != 0) {
            clipPath(shape) {
                drawRect(if (kind == 1) Color(0xFF8A98A9) else Amber, topLeft = Offset(0f, 26f * s), size = Size(28f * s, 2f * s))
            }
        }
        val face = Path().apply {
            listOf(9f, 19f).forEach { x -> moveTo(x * s, 8f * s); lineTo(x * s, 10f * s) }
            moveTo(14f * s, 9f * s); lineTo(14f * s, 14f * s); quadraticTo(14f * s, 16f * s, 12f * s, 15f * s)
            moveTo(9f * s, (if (kind == 2) 21f else 19f) * s)
            quadraticTo(14f * s, (if (kind == 0) 24f else if (kind == 1) 19f else 15f) * s, 19f * s, (if (kind == 2) 21f else 19f) * s)
        }
        drawPath(face, Color.White, style = Stroke(width = 1.7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun SkyScreenTimeStatistics(
    frame: State<SkyScreenTimeFrame>,
    labels: State<SkyScreenTimeLabels>,
    day: Int,
    scale: Float,
    pxScale: Float,
    expandedFraction: Float,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onAdjust: (Boolean) -> Unit,
) {
    val total = remember(day) { SkyScreenTimeData.total(day) }
    val hourData = remember(day) { SkyScreenTimeData.hours(day) }
    val cardHeight = (268f + 94f * expandedFraction).dp * scale
    Box(
        Modifier.offset(x = 20.dp * scale, y = 357.dp * scale)
            .width(362.dp * scale).height(cardHeight).clip(RoundedCornerShape(32.dp * scale)).background(Color.White),
    ) {
        Row(Modifier.offset(x = 13.dp * scale, y = 27.dp * scale).width(336.dp * scale), verticalAlignment = Alignment.Top) {
            val titles = listOf("Screen time", "Pickups", "Notifications")
            val values = listOf(SkyScreenTimeData.duration(total.minutes), total.pickups.toString(), total.notifications.toString())
            repeat(3) { index ->
                Column(
                    modifier = Modifier.width(111.dp * scale).semantics(mergeDescendants = true) {
                        contentDescription = "${titles[index]}, ${values[index]}"
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp * scale),
                ) {
                    Text(titles[index], color = Color(0xFF888995), fontSize = 12.sp * scale, lineHeight = 15.sp * scale, fontWeight = FontWeight.Bold, fontFamily = SkyFontFamily, maxLines = 1)
                    Text(values[index], color = Color.Black, fontSize = 20.sp * scale, lineHeight = 24.sp * scale, fontWeight = FontWeight.Bold, fontFamily = SkyFontFamily, maxLines = 1)
                }
                if (index != 2) Spacer(Modifier.padding(top = 2.dp * scale).width(2.dp * scale).height(16.dp * scale).background(Color(0xFFE4E4E8)))
            }
        }
        Canvas(
            Modifier.offset(x = 17.dp * scale, y = 89.dp * scale)
                .size(330.dp * scale, 126.dp * scale)
                .pointerInput(scale, day) {
                    detectDragGestures(
                        onDragStart = { offset -> onScrubStart(); onScrub(offset.x) },
                        onDrag = { change, _ -> change.consume(); onScrub(change.position.x) },
                        onDragEnd = onScrubEnd,
                        onDragCancel = onScrubEnd,
                    )
                }
                .pointerInput(scale, day) {
                    detectTapGestures { position ->
                        onScrubStart()
                        onScrub(position.x)
                        onScrubEnd()
                    }
                }
                .semantics {
                    contentDescription = "Hourly screen time"
                    stateDescription = labels.value.selectedIndex?.let { index ->
                        "${SkyScreenTimeData.timeLabel(index)}, ${SkyScreenTimeData.duration(hourData[index].minutes)}"
                    } ?: "Daily overview"
                    customActions = listOf(
                        CustomAccessibilityAction("Previous hour") { onAdjust(false); true },
                        CustomAccessibilityAction("Next hour") { onAdjust(true); true },
                    )
                },
        ) {
            val s = pxScale
            val stride = 292f / 24f
            val topPad = 14f * s
            val plotHeight = 86f * s
            val colors = listOf(Neutral, Green, Amber)
            val state = frame.value
            val tracks = Color(0xFFF5F5F7)
            val rules = Color(0xFFE0E1E5)
            for (index in 0 until 24) {
                val x = (index * stride + 2f) * s
                val track = Rect(x, topPad, x + 8f * s, topPad + plotHeight)
                drawRoundRect(tracks, track.topLeft, track.size, CornerRadius(3f * s))
                val hour = hourData[index]
                val height = min(30.0, hour.minutes).toFloat() / 30f * plotHeight
                if (height <= 0f) continue
                val rect = Rect(x, topPad + plotHeight - height, x + 8f * s, topPad + plotHeight)
                val opacity = if (state.selection > 0.05 && abs(index - state.hour) > 0.65) (1.0 - 0.65 * state.selection).toFloat() else 1f
                val barClip = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(3f * s))) }
                clipPath(barClip) {
                    var bottom = topPad + plotHeight
                    listOf(hour.neutral, hour.productive, hour.distracted).forEachIndexed { colorIndex, minutes ->
                        val segment = minutes.toFloat() / 30f * plotHeight
                        drawRect(colors[colorIndex].copy(alpha = opacity), Offset(x, bottom - segment), Size(8f * s, segment))
                        bottom -= segment
                    }
                }
            }
            listOf(0f, 43f, 86f).forEach { y ->
                drawLine(rules, Offset(0f, topPad + y * s), Offset(292f * s, topPad + y * s), 0.65f * s)
            }
            val native = drawContext.canvas.nativeCanvas
            val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = Axis.toArgb()
                textSize = 10f * s
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textAlign = AndroidPaint.Align.LEFT
            }
            val verticalCenterOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
            listOf("30min", "15min", "0s").forEachIndexed { index, label ->
                native.drawText(label, 297f * s, topPad + index * 43f * s + verticalCenterOffset, textPaint)
            }
            listOf("12 AM", "6 AM", "12 PM", "6 PM").forEachIndexed { index, label ->
                val x = index * 73f * s
                drawLine(Color(0xFFD3D5DC), Offset(x, topPad + 86f * s), Offset(x, topPad + 102f * s), 0.7f * s)
                native.drawText(label, x + 3f * s, topPad + 97f * s + verticalCenterOffset, textPaint)
            }
            if (state.selection > 0.001) {
                val markerX = (state.hour.toFloat() * stride + 6f) * s
                drawLine(
                    CloudBlue.copy(alpha = state.selection.toFloat()),
                    Offset(markerX, topPad - 8f * s),
                    Offset(markerX, topPad + 86f * s),
                    strokeWidth = 1.2f * s,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s)),
                )
                drawCircle(CloudBlue.copy(alpha = state.selection.toFloat()), 3f * s, Offset(markerX, topPad - 12f * s))
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = 223.dp * scale).size(80.dp * scale, 44.dp * scale)
                .clip(RoundedCornerShape(22.dp)).clickable(onClick = onToggleDetails),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide hourly details" else "Show hourly details",
                modifier = Modifier.size(20.dp * scale),
                tint = Color(0xFFC9CBD0),
            )
        }
        if (expandedFraction > 0f) {
            Column(
                Modifier.offset(x = 22.dp * scale, y = 280.dp * scale).width(318.dp * scale)
                    .graphicsLayer { alpha = expandedFraction },
                verticalArrangement = Arrangement.spacedBy(7.dp * scale),
            ) {
                val index = labels.value.selectedIndex
                Text(
                    text = index?.let { "${SkyScreenTimeData.timeLabel(it)} activity" } ?: "Your day at a glance",
                    fontSize = 15.sp * scale,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                )
                Text(
                    text = index?.let { selected ->
                        val hour = hourData[selected]
                        "${SkyScreenTimeData.duration(hour.minutes)} on screen · ${hour.pickups} pickups · ${hour.notifications} notifications"
                    } ?: "Drag across the chart to see how each hour felt.",
                    fontSize = 12.sp * scale,
                    color = Color(0xFF757682),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SkyScreenTimeFocusButton(labels: State<SkyScreenTimeLabels>, scale: Float, top: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val seconds = labels.value.focusSeconds
    val label = if (seconds > 0) "End focus · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "Start focusing"
    Row(
        modifier = Modifier.offset(x = 20.dp * scale, y = top).size(362.dp * scale, 61.dp * scale)
            .clip(RoundedCornerShape(31.dp * scale))
            .background(Brush.horizontalGradient(colorStops = arrayOf(
                0f to Color(0xFF4388D0), 0.8f to Color(0xFF508FD9), 1f to Color(0xFFC7E4FA),
            )))
            .border(1.dp * scale, Color.White.copy(alpha = 0.45f), RoundedCornerShape(31.dp * scale))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label; stateDescription = if (seconds > 0) "Focus timer active" else "Focus timer inactive" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (seconds > 0) Icons.Filled.Stop else Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(21.dp * scale),
            tint = Color.White,
        )
        Spacer(Modifier.width(8.dp * scale))
        Text(label, color = Color.White, fontSize = 18.sp * scale, fontWeight = FontWeight.Bold, fontFamily = SkyFontFamily, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SkyScreenTimeNavigation(scale: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.width(362.dp * scale).height(64.dp * scale)
            .shadow(15.dp * scale, RoundedCornerShape(32.dp * scale), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(32.dp * scale))
            .background(Color.White.copy(alpha = 0.78f))
            .border(1.2.dp * scale, Color.White.copy(alpha = 0.9f), RoundedCornerShape(32.dp * scale))
            .padding(4.dp * scale),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tabs = listOf("Home", "Rules", "Squads", "Profile")
        tabs.forEachIndexed { index, label ->
            Column(
                modifier = Modifier.weight(1f).height(56.dp * scale).clip(RoundedCornerShape(28.dp * scale))
                    .background(if (index == 0) Color.Black.copy(alpha = 0.09f) else Color.Transparent)
                    .semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val icon = when (index) {
                    0 -> Icons.Filled.Home
                    1 -> Icons.Outlined.Lock
                    2 -> Icons.Filled.Handshake
                    else -> Icons.Outlined.Person
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(23.dp * scale), tint = Color.Black)
                Spacer(Modifier.height(4.dp * scale))
                Text(label, color = Color.Black, fontSize = 11.sp * scale, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
