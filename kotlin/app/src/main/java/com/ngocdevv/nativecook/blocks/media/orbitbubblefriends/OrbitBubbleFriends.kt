package com.ngocdevv.nativecook.blocks.media.orbitbubblefriends

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Seconds. Matches the current React Native source, including its updated badge geometry. */
const val LOOP = 7.2
private const val STOP = 1.2
private const val ROLL = 0.75
private const val SLOTS = 18
private const val BIG = 214f
private const val SMALL = 150f
private const val FIRST_GAP = 136f
private const val GAP = 23f
private const val TIP = 80.0
private const val PER = 90.0
private const val HOLD = 2.0
private const val SNAP = 0.35
private val BACKGROUND = Color(0xFF141216)
private val INK = Color(0xFFF5F2EC)

private data class OrbitBubbleFriendsFriend(val name: String, val caption: String, val whenPosted: String)
private val FRIENDS = listOf(
    OrbitBubbleFriendsFriend("Mika", "Biscuit learned to sit. Took a week.", "2 min"),
    OrbitBubbleFriendsFriend("Theo", "Saturday tax: one sunbeam, all of it.", "18 min"),
    OrbitBubbleFriendsFriend("Ana", "Found the noodle bar under the bridge.", "1 h"),
    OrbitBubbleFriendsFriend("Jules", "New leaf. I am unreasonably proud.", "3 h"),
    OrbitBubbleFriendsFriend("Rui", "Out of office until further notice.", "5 h"),
    OrbitBubbleFriendsFriend("Noor", "Painted the bike. Then the wall. Oops.", "8 h"),
)

private fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun positiveMod(x: Double, period: Double) = ((x % period) + period) % period
private fun jsRound(x: Double) = floor(x + 0.5)
private fun distOf(slot: Int, index: Double): Double {
    var distance = (slot - index) % SLOTS
    if (distance < -SLOTS / 2.0) distance += SLOTS
    if (distance >= SLOTS / 2.0) distance -= SLOTS
    return distance
}

private fun indexAt(time: Double): Double {
    val stop = floor(time / STOP)
    val within = time - stop * STOP
    return stop + easeInOutCubic(((within - ROLL) / (STOP - ROLL)).coerceIn(0.0, 1.0))
}

private fun infoOpacity(friend: Int, index: Double): Float {
    var distance = positiveMod(friend - index, FRIENDS.size.toDouble())
    if (distance > FRIENDS.size / 2.0) distance -= FRIENDS.size
    return (1 - (abs(distance) / 0.45).coerceIn(0.0, 1.0)).toFloat()
}

/** The same clock and touch state machine as RN; all frame-dependent reads live in drawing/layers. */
private class OrbitBubbleFriendsMotion {
    var elapsed = 0.0
    var origin = 0.0
    var clock = 0.0
    var index = 0.0
    private var touching = false
    private var manual = false
    private var released = -1.0
    private var grabbed = 0.0
    private var from = 0.0
    private var snapTo = 0.0
    private var lastStep = 0.0

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val time = positiveMod(elapsed - origin, LOOP)
        clock = time
        if (touching) return
        if (manual) {
            val since = elapsed - released
            index = if (since < SNAP) from + (snapTo - from) * easeInOutCubic(since / SNAP) else snapTo
            if (since > SNAP + HOLD) {
                manual = false
                origin = elapsed - positiveMod(snapTo, FRIENDS.size.toDouble()) * STOP
            }
            return
        }
        if (autoPlay) index = indexAt(time)
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = index
        lastStep = jsRound(index)
    }

    fun drag(translationY: Double): Boolean {
        index = grabbed - translationY / PER
        val step = jsRound(index)
        if (step == lastStep) return false
        lastStep = step
        return true
    }

    fun end() {
        touching = false
        from = index
        snapTo = jsRound(index)
        released = elapsed
    }
}

/** Photos decode once. Software-rendered shadow masks avoid unsupported hardware mask filters. */
private class OrbitBubbleFriendsArt(assets: AssetManager) {
    private val photos = listOf("pet-dog", "pet-cat", "food-ramen", "plant", "beach", "bike").map { name ->
        assets.open("orbitbubblefriends/orbitbubblefriends_$name.jpg").use {
            checkNotNull(BitmapFactory.decodeStream(it)).apply { density = Bitmap.DENSITY_NONE }
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val photoShaders = photos.map { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val photoMatrix = Matrix()
    private val rect = RectF()
    private val imageRect = RectF()
    private val order = (0 until SLOTS).toMutableList()
    private val shadows = (0..30).map { step ->
        val near = step * 0.02f
        val w = BIG - (BIG - SMALL) * near
        val h = w * cos(TIP * near * PI / 180).toFloat()
        val bitmap = Bitmap.createBitmap(ceil(w - 12 + 108).toInt(), ceil(h + 108).toInt(), Bitmap.Config.ARGB_8888)
        bitmap.density = Bitmap.DENSITY_NONE
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80000000.toInt()
            maskFilter = BlurMaskFilter((18f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
        }
        NativeCanvas(bitmap).drawOval(54f, 54f, 54f + w - 12f, 54f + h, shadow)
        Triple(bitmap, w - 12, h)
    }

    fun draw(canvas: NativeCanvas, index: Double, time: Double, cx: Float, cy: Float) {
        order.sortWith(compareByDescending<Int> { abs(distOf(it, index)) }.thenBy { it })
        for (slot in order) {
            val distance = distOf(slot, index)
            val a = abs(distance)
            if (a > 8.5) continue
            val near = min(a, 1.0)
            val toward = -sign(distance).toFloat()
            val y = cy + (sign(distance) * (FIRST_GAP * near + GAP * max(0.0, a - 1))).toFloat()
            val breath = if (a < 0.5) 1 + 0.015 * sin(time / LOOP * PI * 12) else 1.0
            val w = ((BIG - (BIG - SMALL) * near) * breath).toFloat()
            val tip = TIP * near * PI / 180
            val h = w * max(0.12, cos(tip)).toFloat()
            val thickness = (9 * sin(tip)).toFloat()
            paint.shader = null
            paint.color = 0xFFD9D4CC.toInt()
            rect.set(cx - w / 2, y - h / 2 + toward * thickness, cx + w / 2, y + h / 2 + toward * thickness)
            canvas.drawOval(rect, paint)
            paint.color = 0x59000000
            canvas.drawOval(rect, paint)
            if (a < 0.6) {
                val (bitmap, shadowW, shadowH) = shadows[jsRound(a / 0.02).toInt().coerceIn(0, 30)]
                val sx = (w - 12) / shadowW
                val sy = h / shadowH
                imageRect.set(
                    cx - w / 2 + 6 - 54 * sx, y - h / 2 + 16 - 54 * sy,
                    cx - w / 2 + 6 + (bitmap.width - 54) * sx, y - h / 2 + 16 + (bitmap.height - 54) * sy,
                )
                imagePaint.shader = null
                canvas.drawBitmap(bitmap, null, imageRect, imagePaint)
            }
            rect.set(cx - w / 2, y - h / 2, cx + w / 2, y + h / 2)
            paint.color = 0xFFF5F2EC.toInt()
            canvas.drawOval(rect, paint)
            imageRect.set(cx - w * 0.44f, y - h * 0.44f, cx + w * 0.44f, y + h * 0.44f)
            // A local bitmap shader gives the photo's ellipse an antialiased edge, like Skia's clip.
            val photo = photos[slot % FRIENDS.size]
            val shader = photoShaders[slot % FRIENDS.size]
            photoMatrix.setScale(imageRect.width() / photo.width, imageRect.height() / photo.height)
            photoMatrix.postTranslate(imageRect.left, imageRect.top)
            shader.setLocalMatrix(photoMatrix)
            imagePaint.shader = shader
            canvas.drawOval(imageRect, imagePaint)
            paint.color = android.graphics.Color.WHITE // Reset alpha before assigning a gradient.
            paint.shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                intArrayOf(0x47FFFFFF, 0x00FFFFFF, 0x1F000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawOval(rect, paint)
            paint.shader = null
            val dim = (((a - 0.3) / 7).coerceIn(0.0, 1.0) * 0.8 * 255).toInt()
            paint.color = (dim shl 24) or 0x000E0D10
            canvas.drawOval(rect, paint)
        }
    }
}

/** Friends' latest photos roll as a tipped coin column: .75 s rest, .45 s cubic roll, six stops. */
@Composable
fun OrbitBubbleFriends(
    modifier: Modifier = Modifier,
    brand: String = "Pinboard",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val reduced = rememberLiveReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val motion = remember { OrbitBubbleFriendsMotion() }
    val revision = remember { mutableIntStateOf(0) }
    val art = remember(context) { OrbitBubbleFriendsArt(context.assets) }
    val platformStyle = remember { PlatformTextStyle(includeFontPadding = false) }

    BlockSystemBars(darkIcons = false)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, reduced) {
        if (!active || reduced) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1e9, auto)
                previous = now
                revision.intValue++
            }
        }
    }

    Box(modifier.fillMaxSize().background(BACKGROUND)) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Friends' photo badges. Drag up or down to roll them." }
                .pointerInput(reduced, density) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        motion.begin()
                        var panning = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val translation = change.position - down.position
                                if (!panning && translation.getDistance() >= 4f * density) panning = true
                                if (panning) {
                                    change.consume()
                                    if (motion.drag(translation.y / density.toDouble())) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    revision.intValue++
                                }
                            }
                        } finally {
                            motion.end()
                        }
                    }
                },
        ) {
            revision.intValue
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val save = native.save()
                native.scale(density, density)
                art.draw(native, motion.index, motion.clock, size.width / density / 2, size.height / density * 0.44f)
                native.restoreToCount(save)
            }
        }

        Row(
            Modifier.padding(start = 24.dp, end = 24.dp, top = insets.calculateTopPadding() + 8.dp).fillMaxWidth().height(34.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(brand, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = INK, letterSpacing = (-0.4).sp, platformStyle = platformStyle))
            Text("6 new", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = INK.copy(alpha = 0.55f), platformStyle = platformStyle))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = insets.calculateBottomPadding() + 14.dp)
                .pointerInput(Unit) {
                    // RN's footer sits above the gesture detector and receives its own touches.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            Box(Modifier.fillMaxWidth().height(76.dp)) {
                FRIENDS.forEachIndexed { friendIndex, friend ->
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer {
                            revision.intValue
                            val opacity = infoOpacity(friendIndex, motion.index)
                            alpha = opacity
                            translationY = 10 * (1 - opacity) * density
                        },
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(friend.name, modifier = Modifier.alignByBaseline(), style = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = INK, letterSpacing = (-0.8).sp, platformStyle = platformStyle))
                            Text(friend.whenPosted, modifier = Modifier.alignByBaseline(), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = INK.copy(alpha = 0.5f), platformStyle = platformStyle))
                        }
                        Text(friend.caption, modifier = Modifier.padding(top = 4.dp), style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, color = INK.copy(alpha = 0.7f), platformStyle = platformStyle))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(INK.copy(alpha = 0.08f), CircleShape).padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Send a reaction…", modifier = Modifier.weight(1f), style = TextStyle(fontSize = 16.sp, color = INK.copy(alpha = 0.5f), platformStyle = platformStyle))
                Box(Modifier.size(44.dp).background(INK, CircleShape), contentAlignment = Alignment.Center) {
                    Text("♥\uFE0E", style = TextStyle(fontSize = 18.sp, color = BACKGROUND, platformStyle = platformStyle))
                }
            }
        }
    }
}
