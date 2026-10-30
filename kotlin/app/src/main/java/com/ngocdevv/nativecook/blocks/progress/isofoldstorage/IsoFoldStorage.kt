package com.ngocdevv.nativecook.blocks.progress.isofoldstorage

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// A number-for-number port of the React Native storage-plan screen. The block's stage s
// controls length = 32 + 116 min(s, 1), height = 32 + 116 max(0, s - 1), depth = 32 dp.
// (x,y,z) projects to (ox + .866(x-y), oy + .5(x+y)-z). The hard red floor shadow
// is swept by (-.18 height, .9 height). Keep the rise's overshoot in the geometry.
//
// LOOP = 8 s: 0-1 Lite; 1-2.2 extrude to Plus (ease-out back, k=1.4); 2.2-3 hold;
// 3-4.2 rise to Max (same back curve); 4.2-5.6 hold; 5.6-6.6 fold to Plus;
// 6.6-7.4 retract to Lite (both cubic in/out); 7.4-8 rest, equal to t=0.
// Pan: 120 dp/stage, range [-.15,2.15], snap .35 s, hold 2 s, return .8 s.
// Reduced motion stops the clock at Plus, matching the reference's stage=1.
// Archivo Expanded Black: brand 14, title 32/37, size 54/62 dp. Archivo SemiBold:
// subtitle 15/21, price 16/21, options 15 and action 17 dp, from the bundled OFL fonts.

/** Seconds. */
const val LOOP = 8f
private const val UNIT = 32f
private const val GROW = 116f
private const val SNAP = .35f
private const val HOLD = 2f
private const val RETURN = .8f

private val BACKGROUND = Color(0xFFF2A3A3)
private val INK = Color(0xFF4A0F14)
private val SOFT = Color(0xFF8C3B40)
private val LEFT = Color(0xFFF6E6E6)
private val RIGHT = Color(0xFFEBCFCF)
private val SHADOW = Color(0xFFE3262E)
private val TRACK = Color(0xFFE68B8D)

private class IsoFoldStoragePlan(val name: String, val size: String, val price: String)
private val PLANS = listOf(
    IsoFoldStoragePlan("Lite", "50 GB", "Free forever"),
    IsoFoldStoragePlan("Plus", "200 GB", "\$2.99 a month"),
    IsoFoldStoragePlan("Max", "2 TB", "\$9.99 a month"),
)

private fun segment(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(x: Float): Float {
    val u = x.coerceIn(0f, 1f)
    return u * u * (3 - 2 * u)
}
private fun cubic(x: Float): Float {
    val u = -2 * x + 2
    return if (x < .5f) 4 * x * x * x else 1 - u * u * u / 2
}
private fun back(x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    val u = x - 1
    return 1 + 2.4f * u * u * u + 1.4f * u * u
}
private fun stageAt(t: Float): Float = when {
    t < 3f -> back(segment(t, 1f, 2.2f))
    t < 5.6f -> 1 + back(segment(t, 3f, 4.2f))
    t < 6.6f -> 2 - cubic(segment(t, 5.6f, 6.6f))
    else -> 1 - cubic(segment(t, 6.6f, 7.4f))
}

/** Only the clock changes every frame; consumers read it in drawing or layer blocks. */
private class IsoFoldStorageMotion {
    var elapsed by mutableLongStateOf(0L)
    private var origin by mutableLongStateOf(0L)
    var manual by mutableStateOf(false)
        private set
    private var touching by mutableStateOf(false)
    private var dragged by mutableFloatStateOf(0f)
    private var released by mutableLongStateOf(0L)
    private var from by mutableFloatStateOf(0f)
    private var snapTo by mutableFloatStateOf(0f)

    fun stage(autoPlay: Boolean, reduced: Boolean): Float {
        if (reduced) return 1f
        if (!manual) return if (autoPlay) stageAt(((elapsed - origin) / 1e9 % LOOP).toFloat()) else 0f
        if (touching) return dragged
        val since = (elapsed - released) / 1e9f
        return when {
            since < SNAP -> from + (snapTo - from) * cubic(since / SNAP)
            since < SNAP + HOLD -> snapTo
            since < SNAP + HOLD + RETURN -> snapTo * (1 - cubic((since - SNAP - HOLD) / RETURN))
            else -> 0f
        }
    }

    fun advance(delta: Long) {
        elapsed += delta
        if (manual && !touching && (elapsed - released) / 1e9 >= SNAP + HOLD + RETURN) {
            origin = elapsed
            manual = false
        }
    }

    fun begin(s: Float) {
        dragged = s
        touching = true
        manual = true
    }

    fun drag(s: Float) { dragged = s }

    fun release() {
        from = dragged
        snapTo = dragged.coerceIn(0f, 2f).roundToInt().toFloat()
        released = elapsed
        touching = false
    }
}

private fun DrawScope.quad(points: List<Offset>, color: Color) {
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBlock(ox: Float, oy: Float, stage: Float) {
    val length = UNIT + GROW * min(stage, 1f)
    val height = UNIT + GROW * max(0f, stage - 1)
    val depth = UNIT
    fun project(x: Float, y: Float, z: Float = 0f) = Offset(ox + .866f * (x - y), oy + .5f * (x + y) - z)
    val floor = listOf(Offset.Zero, Offset(length, 0f), Offset(length, depth), Offset(0f, depth))
    val a = floor.map { project(it.x, it.y) }
    val b = floor.map { project(it.x - .18f * height, it.y + .9f * height) }
    quad(a, SHADOW)
    quad(b, SHADOW)
    for (i in 0..3) {
        val j = (i + 1) % 4
        quad(listOf(a[i], a[j], b[j], b[i]), SHADOW)
    }
    quad(listOf(project(0f, depth), project(length, depth), project(length, depth, height), project(0f, depth, height)), LEFT)
    quad(listOf(project(length, 0f), project(length, depth), project(length, depth, height), project(length, 0f, height)), RIGHT)
    quad(listOf(project(0f, 0f, height), project(length, 0f, height), project(length, depth, height), project(0f, depth, height)), Color.White)
}

private fun type(family: FontFamily, size: Int, line: Int? = null, color: Color = INK, tracking: Int = 0) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line?.sp ?: TextUnit.Unspecified,
    color = color,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
fun IsoFoldStorage(
    modifier: Modifier = Modifier,
    title: String = "More room for\nyour memories",
    subtitle: String = "Vault keeps every photo, file and note in sync.",
    action: String = "Continue",
    autoPlay: Boolean = false,
) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val currentReduced by rememberUpdatedState(reduced)
    val currentAuto by rememberUpdatedState(autoPlay)
    val motion = remember { IsoFoldStorageMotion() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val black = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
            context.assets, "isofoldstorage/isofoldstorage_ArchivoExpanded-Black.ttf",
        )))
    }
    val semibold = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(
            context.assets, "isofoldstorage/isofoldstorage_Archivo-SemiBold.ttf",
        )))
    }
    val density = LocalDensity.current.density
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = safe.calculateTopPadding()
    val safeBottom = safe.calculateBottomPadding()

    LaunchedEffect(lifecycle, autoPlay, reduced, motion.manual) {
        if (reduced || (!autoPlay && !motion.manual)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                if (previous != 0L) motion.advance(now - previous)
                previous = now
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val segment = (width - 56f) / 3
        val footerTop = height - safeBottom.value - 130f

        Box(
            Modifier.fillMaxSize().pointerInput(width, height, footerTop, reduced) {
                if (currentReduced) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // The reference places the footer outside its pan detector.
                    val downX = down.position.x / density
                    if (down.position.y / density >= footerTop && downX >= 24 && downX <= width - 24) return@awaitEachGesture
                    val grabbed = motion.stage(currentAuto, currentReduced)
                    motion.begin(grabbed)
                    var lastStep = grabbed.roundToInt()
                    var activated = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val translation = change.position - down.position
                            if (!activated && translation.getDistance() >= 4f * density) activated = true
                            if (activated) {
                                change.consume()
                                val s = (grabbed + translation.x / density / 120f).coerceIn(-.15f, 2.15f)
                                motion.drag(s)
                                val step = s.coerceIn(0f, 2f).roundToInt()
                                if (step != lastStep) {
                                    lastStep = step
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            }
                        }
                    } finally {
                        motion.release()
                    }
                }
            },
        ) {
            Canvas(
                Modifier.fillMaxSize().semantics {
                    contentDescription = "Storage block. Drag sideways to change the plan."
                },
            ) {
                val stage = motion.stage(currentAuto, currentReduced)
                scale(density, density, Offset.Zero) { drawBlock(width / 2 - 34, height * .45f, stage) }
            }

            Column(Modifier.offset(x = 24.dp, y = safeTop + 16.dp).width((width - 48).dp)) {
                // RN rounds Archivo's natural 15.232 pt line box up to 16 pt.
                Text("VAULT", Modifier.height(16.dp), style = type(black, 14, tracking = 2))
                Text(title, Modifier.padding(top = 16.dp), style = type(black, 32, 37, tracking = -1))
                Text(subtitle, Modifier.padding(top = 10.dp), style = type(semibold, 15, 21, color = SOFT))
            }

            Box(Modifier.offset(y = (height * .645f).dp).fillMaxWidth().height(86.dp)) {
                PLANS.forEachIndexed { k, plan ->
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer {
                            val s = motion.stage(currentAuto, currentReduced)
                            alpha = 1 - smooth(abs(s - k) / .5f)
                            translationY = (s - k) * -14f * density
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(plan.size, style = type(black, 54, 62, tracking = -2))
                        Text(plan.price, Modifier.padding(top = 2.dp), style = type(semibold, 16, 21, SOFT))
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = safeBottom + 16.dp)) {
            Box(Modifier.fillMaxWidth().height(48.dp).background(TRACK, RoundedCornerShape(24.dp))) {
                Box(
                    Modifier.offset(x = 4.dp, y = 4.dp).width(segment.dp).height(40.dp)
                        .graphicsLayer { translationX = motion.stage(currentAuto, currentReduced).coerceIn(0f, 2f) * segment * density }
                        .background(Color.White, RoundedCornerShape(20.dp)),
                )
                Row(Modifier.padding(4.dp)) {
                    PLANS.forEach { plan ->
                        Box(Modifier.width(segment.dp).height(40.dp), contentAlignment = Alignment.Center) {
                            Text(plan.name, style = type(semibold, 15))
                        }
                    }
                }
            }
            Box(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(54.dp)
                    .background(INK, RoundedCornerShape(27.dp)).semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = type(semibold, 17, color = Color.White))
            }
        }
    }
}
