package com.ngocdevv.nativecook.blocks.lists.menuproductcarousel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The pin's own, frame-measured period in seconds. */
const val LOOP = 15.267
private const val LOOP_MS = 15_267f
private const val RESUME_MS = 3_500L
private const val HOLD_MS = 9_300f

private const val PX = .6828f
private const val CROP = 44.8f

private fun px(value: Float) = value * PX
private fun pxX(value: Float) = value * PX - CROP

private object MenuProductCarouselColors {
    val Background = 0xFFFED5BC.toInt()
    val Card = 0xFF7F4B27.toInt()
    val Brand = 0xFF6E3C1E.toInt()
    val Ink = 0xFF111111.toInt()
    val Title = 0xFFFFFFFF.toInt()
    val Bite = 0xFF7A4524.toInt()
    val Shadow = 0x614A2610
}

private object MenuProductCarouselLayout {
    const val CardWidth = 360f * PX
    const val CardHeight = 477f * PX
    const val CardCenterY = 563.5f * PX
    const val CardRadius = 22f * PX
    const val SideScale = .778f
    const val CardPitch = 266f
    const val TitleAscent = .16f
    const val WordAscent = .12f

    const val LogoWidth = 157f * PX
    const val LogoHeight = 114f * PX
    const val LogoLeft = 280f * PX - CROP
    const val LogoTop = 62f * PX
    const val EndLogoLeft = 235f * PX - CROP
    const val EndLogoTop = 494f * PX
    const val EndLogoScale = 1.56f

    const val DontTop = 424f * PX - 8f
    const val DontSize = 52f
    const val TakeRight = 589f * PX - CROP
    const val TakeTop = 535f * PX
    const val BiteRight = 588f * PX - CROP
    const val BiteTop = 668f * PX
}

private data class MenuProductCarouselFrame(val left: Float, val top: Float, val width: Float, val height: Float)

private data class MenuProductCarouselTitle(val size: Float, val scaleX: Float, val centerX: Float, val top: Float)

private data class MenuProductCarouselItem(
    val id: String,
    val title: String,
    val image: Int,
    val x: Float,
    val titleFrame: MenuProductCarouselTitle,
    val imageFrame: MenuProductCarouselFrame,
    val leftGap: Float,
    val rightGap: Float,
)

private fun menuProductCarouselItem(
    id: String,
    title: String,
    image: Int,
    cardX: Float,
    titleSize: Float,
    titleScaleX: Float,
    titleCenterX: Float,
    titleTop: Float,
    imageFrame: MenuProductCarouselFrame,
    gaps: Pair<Float, Float>,
) = MenuProductCarouselItem(
    id = id,
    title = title,
    image = image,
    x = pxX(cardX),
    titleFrame = MenuProductCarouselTitle(titleSize, titleScaleX, px(titleCenterX), px(titleTop)),
    imageFrame = imageFrame,
    leftGap = gaps.first,
    rightGap = gaps.second,
)

private val MenuProductCarouselItems = listOf(
    menuProductCarouselItem("coldrink-0", "Coldrink", R.drawable.menuproductcarousel_coldrink, 178f, 56.5f, .819f, 176.5f, 57.3f, MenuProductCarouselFrame(1.5f, 41.8f, 220.8f, 249.6f), 292f to 267.4f),
    menuProductCarouselItem("coffee", "Coffee", R.drawable.menuproductcarousel_coffee, 187f, 65.9f, .78f, 170f, 59.3f, MenuProductCarouselFrame(42.2f, 102.5f, 163.1f, 184.3f), 258.9f to 272.6f),
    menuProductCarouselItem("burger", "Burger", R.drawable.menuproductcarousel_burger, 167f, 54f, .89f, 179.3f, 62.8f, MenuProductCarouselFrame(28.3f, 86.7f, 167.1f, 188.9f), 274.3f to 281.8f),
    menuProductCarouselItem("fries", "French Fries", R.drawable.menuproductcarousel_fries, 167f, 46.4f, .856f, 174.5f, 77.3f, MenuProductCarouselFrame(16.8f, 55f, 225.2f, 254.6f), 284.5f to 283.1f),
    menuProductCarouselItem("pizza", "Pizza", R.drawable.menuproductcarousel_pizza, 189f, 56.4f, .823f, 171f, 72.3f, MenuProductCarouselFrame(30.1f, 78.9f, 184.1f, 208.1f), 285.9f to 290f),
    menuProductCarouselItem("coldrink", "Coldrink", R.drawable.menuproductcarousel_coldrink, 178f, 56.5f, .819f, 176.5f, 57.3f, MenuProductCarouselFrame(1.5f, 41.8f, 220.8f, 249.6f), 292f to 267.4f),
    menuProductCarouselItem("pasta", "Pasta", R.drawable.menuproductcarousel_pasta, 159f, 46.9f, 1.025f, 166.7f, 107.3f, MenuProductCarouselFrame(19.2f, 78f, 180.2f, 203.7f), 269.5f to 264.7f),
    menuProductCarouselItem("taco", "Taco", R.drawable.menuproductcarousel_taco, 191f, 66.1f, .956f, 164.2f, 85.8f, MenuProductCarouselFrame(30.1f, 83.7f, 176.3f, 199.3f), 267.4f to 257.9f),
    menuProductCarouselItem("sandwich", "Sandwich", R.drawable.menuproductcarousel_sandwich, 165f, 54.3f, .831f, 170.2f, 93.3f, MenuProductCarouselFrame(21.9f, 82.7f, 186.4f, 210.7f), 260.6f to 264.7f),
    menuProductCarouselItem("coldrink-2", "Coldrink", R.drawable.menuproductcarousel_coldrink, 178f, 56.5f, .819f, 176.5f, 57.3f, MenuProductCarouselFrame(1.5f, 41.8f, 220.8f, 249.6f), 292f to 267.4f),
)

private object MenuProductCarouselMotionSpec {
    val Shifts = floatArrayOf(367f, 1_733f, 2_900f, 4_067f, 5_300f, 6_500f, 7_900f)
    val ShiftCurve = floatArrayOf(0f, .024f, .09f, .19f, .32f, .46f, .6f, .73f, .85f, .94f, .99f, 1f)
    val GroupScale = arrayOf(9_420f to 1f, 9_670f to .83f, 9_830f to .95f, 10_030f to .04f)
    val EndLogo = arrayOf(
        12_667f to .02f, 12_700f to .16f, 12_767f to .34f, 12_833f to .59f,
        12_900f to .91f, 12_967f to .9f, 13_033f to .85f, 13_100f to .85f,
        13_167f to .92f, 13_233f to .98f, 13_367f to 1f,
    )
    const val WordsFrom = -477f
    const val WordsOutBy = -614f
}

private data class MenuProductCarouselOutro(
    val groupScale: Float,
    val groupOpacity: Float,
    val dont: Float,
    val wordsX: Float,
    val wordsShown: Float,
    val topLogo: Float,
    val endLogo: Float,
)

private fun menuClamp01(value: Float) = value.coerceIn(0f, 1f)
private fun menuSeg(value: Float, from: Float, to: Float) = menuClamp01((value - from) / (to - from))
private fun menuQuadOut(value: Float): Float = 1f - (1f - menuClamp01(value)) * (1f - menuClamp01(value))
private fun menuQuadIn(value: Float): Float = menuClamp01(value) * menuClamp01(value)

private fun menuKeys(time: Float, keys: Array<Pair<Float, Float>>): Float {
    if (time <= keys.first().first) return keys.first().second
    for (index in 1 until keys.size) {
        if (time <= keys[index].first) {
            val previous = keys[index - 1]
            val next = keys[index]
            val fraction = (time - previous.first) / (next.first - previous.first)
            return previous.second + (next.second - previous.second) * fraction
        }
    }
    return keys.last().second
}

private fun menuShiftProgress(delta: Float): Float {
    if (delta <= 0f) return 0f
    val position = delta / (1_000f / 30f)
    val index = floor(position).toInt()
    if (index >= MenuProductCarouselMotionSpec.ShiftCurve.lastIndex) return 1f
    val values = MenuProductCarouselMotionSpec.ShiftCurve
    return values[index] + (values[index + 1] - values[index]) * (position - index)
}

private fun menuCarouselAt(time: Float): Float {
    var value = 1f
    MenuProductCarouselMotionSpec.Shifts.forEach { value += menuShiftProgress(time - it) }
    return min(value, MenuProductCarouselItems.lastIndex.toFloat())
}

private fun menuOutroAt(time: Float): MenuProductCarouselOutro {
    val groupScale = menuKeys(time, MenuProductCarouselMotionSpec.GroupScale)
    val wordsIn = menuSeg(time, 10_433f, 11_033f)
    val wordsOut = menuSeg(time, 12_233f, 12_566f)
    return MenuProductCarouselOutro(
        groupScale = groupScale,
        groupOpacity = 1f - menuSeg(time, 9_930f, 10_030f),
        dont = menuSeg(time, 10_170f, 10_370f) * (1f - menuSeg(time, 12_370f, 12_500f)),
        wordsX = MenuProductCarouselMotionSpec.WordsFrom * (1f - menuQuadOut(wordsIn)) + MenuProductCarouselMotionSpec.WordsOutBy * menuQuadIn(wordsOut),
        wordsShown = if (time >= 10_433f && time < 12_566f) 1f else 0f,
        topLogo = 1f - menuSeg(time, 12_230f, 12_370f),
        endLogo = if (time >= 12_667f) menuKeys(time, MenuProductCarouselMotionSpec.EndLogo) else 0f,
    )
}

private fun menuRestCenter(index: Float): Float {
    val first = floor(index).toInt().coerceIn(0, MenuProductCarouselItems.lastIndex)
    val second = min(first + 1, MenuProductCarouselItems.lastIndex)
    val fraction = index - first
    return MenuProductCarouselItems[first].x + (MenuProductCarouselItems[second].x - MenuProductCarouselItems[first].x) * fraction + MenuProductCarouselLayout.CardWidth / 2f
}

@Stable
private class MenuProductCarouselMotion(autoPlay: Boolean, frozenAt: Float?, reducedMotion: Boolean) {
    private val drawsRecorderPulse = autoPlay && !reducedMotion && frozenAt == null
    var frameMs by mutableFloatStateOf(frozenAt ?: 0f)
    var recorderPulseMs by mutableFloatStateOf(0f)
    var running by mutableStateOf(autoPlay && !reducedMotion && frozenAt == null)
    var startNanos by mutableLongStateOf(-1L)
    var drag by mutableFloatStateOf(0f)
    var dragStart by mutableFloatStateOf(0f)
    var snapFrom by mutableFloatStateOf(0f)
    var snapTo by mutableFloatStateOf(0f)
    var snapAtNanos by mutableLongStateOf(-1L)
    var snapVelocity by mutableFloatStateOf(0f)
    var snapStiffness by mutableFloatStateOf(0f)
    var snapInitialEnergy by mutableFloatStateOf(0f)
    var resumeAtNanos by mutableLongStateOf(-1L)

    fun advance(now: Long) {
        if (drawsRecorderPulse) recorderPulseMs = ((now / 1_000_000L) % 100L).toFloat()
        if (!running && resumeAtNanos >= 0L && now >= resumeAtNanos) restart()
        if (running) {
            if (startNanos < 0L) startNanos = now
            frameMs = ((now - startNanos) / 1_000_000f) % LOOP_MS
        }
        if (snapAtNanos >= 0L) {
            val elapsed = (now - snapAtNanos) / 1_000_000f
            val frame = menuReanimatedSpring(
                snapFrom,
                snapTo,
                snapVelocity,
                snapStiffness,
                snapInitialEnergy,
                elapsed,
            )
            drag = frame.position
            if (frame.finished) {
                drag = snapTo
                snapAtNanos = -1L
            }
        }
    }

    fun stopForTouch() {
        running = false
        snapAtNanos = -1L
        resumeAtNanos = -1L
        dragStart = drag
    }

    fun updateDrag(translationX: Float) {
        val base = menuCarouselAt(frameMs)
        var proposed = base + dragStart - translationX / MenuProductCarouselLayout.CardPitch
        if (proposed < 0f) proposed *= .3f
        if (proposed > MenuProductCarouselItems.lastIndex) proposed = MenuProductCarouselItems.lastIndex + (proposed - MenuProductCarouselItems.lastIndex) * .3f
        drag = proposed - base
    }

    fun finishDrag(velocityX: Float, reduceMotion: Boolean) {
        val base = menuCarouselAt(frameMs)
        val configuredVelocity = -velocityX / MenuProductCarouselLayout.CardPitch
        var velocity = configuredVelocity
        val target = (base + drag + configuredVelocity * .15f).roundToInt().coerceIn(0, MenuProductCarouselItems.lastIndex).toFloat()
        snapFrom = drag
        snapTo = target - base
        if ((snapTo > snapFrom && velocity < 0f) || (snapTo < snapFrom && velocity > 0f)) velocity = 0f
        if (reduceMotion) {
            drag = snapTo
            snapAtNanos = -1L
            return
        }
        snapVelocity = velocity
        snapStiffness = menuReanimatedSpringStiffness(snapFrom - snapTo, velocity)
        snapInitialEnergy = menuSpringEnergy(snapFrom - snapTo, configuredVelocity, snapStiffness)
        snapAtNanos = System.nanoTime()
    }

    fun scheduleRestart() {
        resumeAtNanos = System.nanoTime() + RESUME_MS * 1_000_000L
    }

    private fun restart() {
        drag = 0f
        startNanos = -1L
        resumeAtNanos = -1L
        running = true
    }
}

private const val MENU_SPRING_MASS = 4.0
private const val MENU_SPRING_DAMPING_RATIO = .8
private const val MENU_SPRING_ENERGY_THRESHOLD = 6e-9
private const val MENU_SPRING_SETTLING_SECONDS = .6 // 400 ms perceptual duration × Reanimated's 1.5 coefficient

private data class MenuProductCarouselSpringFrame(val position: Float, val finished: Boolean)

private fun menuSpringEnergy(displacement: Float, velocity: Float, stiffness: Float): Float {
    val x = displacement.toDouble()
    val v = velocity.toDouble()
    return (.5 * stiffness.toDouble() * x * x + .5 * MENU_SPRING_MASS * v * v).toFloat()
}

// Direct port of Reanimated 4.5.1 calculateNewStiffnessToMatchDuration.
private fun menuReanimatedSpringStiffness(displacement: Float, velocity: Float): Float {
    val x0 = displacement.toDouble()
    val v0 = velocity.toDouble()

    fun energyDifference(stiffness: Double): Double {
        val omega = sqrt(stiffness / MENU_SPRING_MASS) * MENU_SPRING_DAMPING_RATIO
        val envelope = exp(-omega * MENU_SPRING_SETTLING_SECONDS)
        val position = (x0 + (v0 + x0 * omega) * MENU_SPRING_SETTLING_SECONDS) * envelope
        val speed = position * -omega + (v0 + x0 * omega) * envelope
        val initial = .5 * stiffness * x0 * x0 + .5 * MENU_SPRING_MASS * v0 * v0
        if (initial == 0.0) return -MENU_SPRING_ENERGY_THRESHOLD
        val settled = .5 * stiffness * position * position + .5 * MENU_SPRING_MASS * speed * speed
        return settled / initial - MENU_SPRING_ENERGY_THRESHOLD
    }

    var low = Math.ulp(1.0)
    var high = 8_000.0
    val direction = if (energyDifference(high) >= energyDifference(low)) 1.0 else -1.0
    var current = (low + high) / 2.0
    repeat(100) {
        val value = energyDifference(current)
        if (abs(value) <= MENU_SPRING_ENERGY_THRESHOLD * 1e-3) return current.toFloat()
        if (value * direction < 0.0) low = current else high = current
        current = (low + high) / 2.0
    }
    return current.toFloat()
}

// Direct port of Reanimated's underDampedSpringCalculations and energy termination.
private fun menuReanimatedSpring(
    from: Float,
    to: Float,
    velocity: Float,
    stiffness: Float,
    initialEnergy: Float,
    elapsedMs: Float,
): MenuProductCarouselSpringFrame {
    if (initialEnergy == 0f) return MenuProductCarouselSpringFrame(to, true)
    val time = elapsedMs / 1_000.0
    val displacement = (from - to).toDouble()
    val omega0 = sqrt(stiffness / MENU_SPRING_MASS)
    val omega1 = omega0 * sqrt(1.0 - MENU_SPRING_DAMPING_RATIO * MENU_SPRING_DAMPING_RATIO)
    val envelope = exp(-MENU_SPRING_DAMPING_RATIO * omega0 * time)
    val sin = sin(omega1 * time)
    val cos = cos(omega1 * time)
    val fragment = envelope * (
        sin * ((velocity + MENU_SPRING_DAMPING_RATIO * omega0 * displacement) / omega1) + displacement * cos
    )
    val currentVelocity = -MENU_SPRING_DAMPING_RATIO * omega0 * fragment + envelope * (
        cos * (velocity + MENU_SPRING_DAMPING_RATIO * omega0 * displacement) - omega1 * displacement * sin
    )
    val position = to + fragment
    val energy = .5 * stiffness * fragment * fragment + .5 * MENU_SPRING_MASS * currentVelocity * currentVelocity
    val finished = energy / initialEnergy.toDouble() <= MENU_SPRING_ENERGY_THRESHOLD
    return MenuProductCarouselSpringFrame(if (finished) to else position.toFloat(), finished)
}

private class MenuProductCarouselAssets(context: Context) {
    val background = decode(context, R.drawable.menuproductcarousel_topo_bg)
    val food = mapOf(
        R.drawable.menuproductcarousel_coffee to decode(context, R.drawable.menuproductcarousel_coffee),
        R.drawable.menuproductcarousel_coldrink to decode(context, R.drawable.menuproductcarousel_coldrink),
        R.drawable.menuproductcarousel_burger to decode(context, R.drawable.menuproductcarousel_burger),
        R.drawable.menuproductcarousel_fries to decode(context, R.drawable.menuproductcarousel_fries),
        R.drawable.menuproductcarousel_pizza to decode(context, R.drawable.menuproductcarousel_pizza),
        R.drawable.menuproductcarousel_pasta to decode(context, R.drawable.menuproductcarousel_pasta),
        R.drawable.menuproductcarousel_taco to decode(context, R.drawable.menuproductcarousel_taco),
        R.drawable.menuproductcarousel_sandwich to decode(context, R.drawable.menuproductcarousel_sandwich),
    )
    val lilita = font(context, R.font.menuproductcarousel_lilitaone_regular)
    val anton = font(context, R.font.menuproductcarousel_anton_regular)
    val kaushan = font(context, R.font.menuproductcarousel_kaushanscript_regular)
    val montserrat = font(context, R.font.menuproductcarousel_montserrat_semibold)

    private fun decode(context: Context, id: Int): Bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, id))
    private fun font(context: Context, id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT
}

/**
 * Direct Jetpack Compose port of the approved React Native menu carousel.
 *
 * [freezeAt] is a millisecond helper for frame-by-frame comparison with the source video.
 */
@Composable
fun MenuProductCarousel(autoPlay: Boolean = false, freezeAt: Float? = null) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reduceMotion = rememberReducedMotion()
    val assets = remember(context) { MenuProductCarouselAssets(context) }
    val motion = remember(autoPlay, freezeAt, reduceMotion) { MenuProductCarouselMotion(autoPlay, freezeAt, reduceMotion) }
    val frozen = freezeAt != null

    LaunchedEffect(motion, frozen, reduceMotion) {
        while (!frozen && !reduceMotion) {
            withFrameNanos { motion.advance(it) }
        }
    }

    // React Native's StatusBar style="dark" maps to dark status-bar icons on this light screen.
    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(MenuProductCarouselColors.Background))
            .clipToBounds()
            .semantics {
                contentDescription = "Menu: swipe through the dishes"
                role = Role.Image
            }
            .pointerInput(motion, autoPlay, frozen, density) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (frozen) return@awaitEachGesture
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    motion.stopForTouch()
                    var firstX = down.position.x
                    var dragged = false
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.firstOrNull() ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (abs(change.position.x - firstX) > 8f * density.density) dragged = true
                        if (dragged) {
                            motion.updateDrag((change.position.x - firstX) / density.density)
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    if (dragged && !cancelled) {
                        motion.finishDrag(tracker.calculateVelocity().x / density.density, reduceMotion)
                    }
                    if (autoPlay && !reduceMotion) motion.scheduleRestart()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val frame = motion.frameMs
            val drag = motion.drag
            val recorderPulse = if (autoPlay && !reduceMotion && !frozen) motion.recorderPulseMs else -1f
            drawIntoCanvas { composeCanvas ->
                val canvas = composeCanvas.nativeCanvas
                canvas.save()
                canvas.scale(density.density, density.density)
                val width = size.width / density.density
                val height = size.height / density.density
                MenuProductCarouselRenderer.draw(canvas, width, height, frame, drag, recorderPulse, assets)
                canvas.restore()
            }
        }
    }
}

private object MenuProductCarouselRenderer {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val path = Path()

    fun draw(canvas: AndroidCanvas, width: Float, height: Float, time: Float, drag: Float, recorderPulseMs: Float, assets: MenuProductCarouselAssets) {
        drawBackground(canvas, width, height, assets.background)
        drawRecorderPulse(canvas, width, height, recorderPulseMs)
        val outro = menuOutroAt(time)
        drawLogo(canvas, MenuProductCarouselLayout.LogoLeft, MenuProductCarouselLayout.LogoTop, 1f, outro.topLogo, assets)
        drawCards(canvas, time, drag, outro, assets)
        drawOutroWords(canvas, width, outro, assets)
        val endCenterX = MenuProductCarouselLayout.EndLogoLeft + MenuProductCarouselLayout.LogoWidth * MenuProductCarouselLayout.EndLogoScale / 2f
        val endCenterY = MenuProductCarouselLayout.EndLogoTop + MenuProductCarouselLayout.LogoHeight * MenuProductCarouselLayout.EndLogoScale / 2f
        val endScale = max(outro.endLogo, .02f) * MenuProductCarouselLayout.EndLogoScale
        drawLogo(canvas, endCenterX - MenuProductCarouselLayout.LogoWidth / 2f, endCenterY - MenuProductCarouselLayout.LogoHeight / 2f, endScale, if (outro.endLogo > 0f) 1f else 0f, assets)
    }

    private fun drawBackground(canvas: AndroidCanvas, width: Float, height: Float, bitmap: Bitmap) {
        fill.color = MenuProductCarouselColors.Background
        fill.alpha = 255
        canvas.drawColor(MenuProductCarouselColors.Background)
        val scale = max(width / bitmap.width, height / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        canvas.drawBitmap(bitmap, null, RectF((width - drawWidth) / 2f, (height - drawHeight) / 2f, (width + drawWidth) / 2f, (height + drawHeight) / 2f), fill)
    }

    // Matches RN's 1 pt recorder keep-alive: a 50 ms timing repeated in reverse.
    private fun drawRecorderPulse(canvas: AndroidCanvas, width: Float, height: Float, pulseMs: Float) {
        if (pulseMs < 0f) return
        val half = (pulseMs / 50f).coerceIn(0f, 2f)
        val opacity = if (half <= 1f) half else 2f - half
        fill.style = Paint.Style.FILL
        fill.color = 0xFFFDD4BB.toInt()
        fill.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRect(width - 1f, height - 1f, width, height, fill)
    }

    private fun drawCards(canvas: AndroidCanvas, time: Float, drag: Float, outro: MenuProductCarouselOutro, assets: MenuProductCarouselAssets) {
        val centered = menuCarouselAt(time) + drag
        val first = floor(centered).toInt().coerceIn(0, MenuProductCarouselItems.lastIndex)
        val second = min(first + 1, MenuProductCarouselItems.lastIndex)
        val fraction = centered - first
        val leftGap = MenuProductCarouselItems[first].leftGap + (MenuProductCarouselItems[second].leftGap - MenuProductCarouselItems[first].leftGap) * fraction
        val rightGap = MenuProductCarouselItems[first].rightGap + (MenuProductCarouselItems[second].rightGap - MenuProductCarouselItems[first].rightGap) * fraction
        MenuProductCarouselItems.indices
            .sortedBy { index -> (10f - abs(index - centered) * 3f).roundToInt() }
            .forEach { index ->
                val position = index - centered
                if (abs(position) > 2.2f) return@forEach
                val scale = 1f - (1f - MenuProductCarouselLayout.SideScale) * min(abs(position), 1f)
                val center = menuRestCenter(centered) + position * if (position < 0f) leftGap else rightGap
                val groupCenter = 201f + (center - 201f) * outro.groupScale
                canvas.save()
                canvas.translate(groupCenter, MenuProductCarouselLayout.CardCenterY)
                canvas.scale(scale * outro.groupScale, scale * outro.groupScale)
                canvas.translate(-MenuProductCarouselLayout.CardWidth / 2f, -MenuProductCarouselLayout.CardHeight / 2f)
                drawCard(canvas, MenuProductCarouselItems[index], outro.groupOpacity, assets)
                canvas.restore()
            }
    }

    private fun drawCard(canvas: AndroidCanvas, item: MenuProductCarouselItem, alpha: Float, assets: MenuProductCarouselAssets) {
        fill.style = Paint.Style.FILL
        fill.color = MenuProductCarouselColors.Card
        fill.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            fill.setShadowLayer(16f, 8f, 10f, MenuProductCarouselColors.Shadow)
        } else {
            // Hardware Canvas cannot blur geometric shadow layers on API 26-27.
            val ambientAlpha = (0x29 * alpha).roundToInt().coerceIn(0, 255)
            val contactAlpha = (0x40 * alpha).roundToInt().coerceIn(0, 255)
            fill.color = (ambientAlpha shl 24) or 0x004A2610
            canvas.drawRoundRect(8f, 10f, MenuProductCarouselLayout.CardWidth + 8f, MenuProductCarouselLayout.CardHeight + 10f, MenuProductCarouselLayout.CardRadius, MenuProductCarouselLayout.CardRadius, fill)
            fill.color = (contactAlpha shl 24) or 0x004A2610
            canvas.drawRoundRect(1f, 2f, MenuProductCarouselLayout.CardWidth + 1f, MenuProductCarouselLayout.CardHeight + 2f, MenuProductCarouselLayout.CardRadius, MenuProductCarouselLayout.CardRadius, fill)
            fill.color = MenuProductCarouselColors.Card
            fill.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        }
        canvas.drawRoundRect(0f, 0f, MenuProductCarouselLayout.CardWidth, MenuProductCarouselLayout.CardHeight, MenuProductCarouselLayout.CardRadius, MenuProductCarouselLayout.CardRadius, fill)
        fill.clearShadowLayer()
        val frame = item.imageFrame
        canvas.drawBitmap(requireNotNull(assets.food[item.image]), null, RectF(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height), fill)
        drawTopText(
            canvas,
            item.title,
            item.titleFrame.centerX,
            item.titleFrame.top - item.titleFrame.size * MenuProductCarouselLayout.TitleAscent,
            item.titleFrame.size,
            item.titleFrame.scaleX,
            MenuProductCarouselColors.Title,
            alpha,
            assets.lilita,
            shadowRadius = 6f,
            shadowColor = 0x59FFFFFF,
        )
    }

    private fun drawOutroWords(canvas: AndroidCanvas, width: Float, outro: MenuProductCarouselOutro, assets: MenuProductCarouselAssets) {
        if (outro.dont > 0f) drawTopText(canvas, "Don’t wait", width / 2f, MenuProductCarouselLayout.DontTop, MenuProductCarouselLayout.DontSize, 1f, MenuProductCarouselColors.Ink, outro.dont, assets.montserrat)
        if (outro.wordsShown > 0f) {
            drawRightTopText(canvas, "TAKE A", MenuProductCarouselLayout.TakeRight + outro.wordsX, MenuProductCarouselLayout.TakeTop - 92f * MenuProductCarouselLayout.WordAscent, 98.5f, 1.27f, MenuProductCarouselColors.Bite, outro.wordsShown, assets.anton)
            drawRightTopText(canvas, "BITE", MenuProductCarouselLayout.BiteRight + outro.wordsX, MenuProductCarouselLayout.BiteTop - 92f * MenuProductCarouselLayout.WordAscent, 98.5f, 1.27f, MenuProductCarouselColors.Bite, outro.wordsShown, assets.anton)
        }
    }

    private fun drawLogo(canvas: AndroidCanvas, left: Float, top: Float, scale: Float, alpha: Float, assets: MenuProductCarouselAssets) {
        if (alpha <= 0f) return
        canvas.save()
        canvas.translate(left + MenuProductCarouselLayout.LogoWidth / 2f, top + MenuProductCarouselLayout.LogoHeight / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-MenuProductCarouselLayout.LogoWidth / 2f, -MenuProductCarouselLayout.LogoHeight / 2f)
        drawTopText(canvas, "Cafe & Restaurant", 0f, px(48f), 6.2f, 1f, MenuProductCarouselColors.Brand, alpha, assets.kaushan, centered = false)
        drawTopText(canvas, "COFFEE", 0f, px(60f), 24f, 1f, MenuProductCarouselColors.Brand, alpha, assets.lilita, centered = false, letterSpacing = .2f / 24f)
        drawTopText(canvas, "TALES", 0f, px(87f), 24f, 1f, MenuProductCarouselColors.Brand, alpha, assets.lilita, centered = false, letterSpacing = .2f / 24f)

        fill.style = Paint.Style.FILL
        fill.color = MenuProductCarouselColors.Brand
        fill.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        path.reset()
        path.moveTo(px(108f), px(42f))
        path.cubicTo(px(98f), px(32f), px(116f), px(24f), px(106f), px(14f))
        path.cubicTo(px(100f), px(8f), px(106f), px(3f), px(110f), 0f)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 1.1f
        fill.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path, fill)
        path.reset()
        path.moveTo(px(114f), px(44f))
        path.cubicTo(px(106f), px(36f), px(120f), px(28f), px(112f), px(20f))
        canvas.drawPath(path, fill)
        fill.style = Paint.Style.FILL
        path.reset()
        path.moveTo(px(108f), px(52f)); path.lineTo(px(142f), px(52f)); path.lineTo(px(132f), px(88f)); path.quadTo(px(125f), px(94f), px(118f), px(88f)); path.close()
        canvas.drawPath(path, fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 2f
        path.reset(); path.moveTo(px(140f), px(58f)); path.cubicTo(px(156f), px(56f), px(156f), px(76f), px(134f), px(80f)); canvas.drawPath(path, fill)
        fill.strokeWidth = 1.4f
        canvas.drawOval(px(126f) - px(26f), px(97f) - px(4f), px(126f) + px(26f), px(97f) + px(4f), fill)
        fill.style = Paint.Style.FILL
        path.reset()
        path.moveTo(px(95f), px(96f)); path.lineTo(px(97f), px(101f)); path.lineTo(px(102f), px(103f)); path.lineTo(px(97f), px(105f)); path.lineTo(px(95f), px(110f)); path.lineTo(px(93f), px(105f)); path.lineTo(px(88f), px(103f)); path.lineTo(px(93f), px(101f)); path.close()
        canvas.drawPath(path, fill)
        canvas.restore()
    }

    private fun drawTopText(
        canvas: AndroidCanvas,
        value: String,
        x: Float,
        top: Float,
        size: Float,
        scaleX: Float,
        color: Int,
        alpha: Float,
        typeface: Typeface,
        centered: Boolean = true,
        letterSpacing: Float = 0f,
        shadowRadius: Float = 0f,
        shadowColor: Int = 0,
    ) {
        text.typeface = typeface
        text.textSize = size
        text.textScaleX = 1f
        text.letterSpacing = letterSpacing
        text.color = color
        text.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        text.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        if (shadowRadius > 0f) text.setShadowLayer(shadowRadius, 0f, 0f, shadowColor) else text.clearShadowLayer()
        val baseline = top - text.fontMetrics.ascent
        canvas.save()
        if (scaleX != 1f) canvas.scale(scaleX, 1f, x, top)
        canvas.drawText(value, x, baseline, text)
        canvas.restore()
    }

    private fun drawRightTopText(canvas: AndroidCanvas, value: String, right: Float, top: Float, size: Float, scaleX: Float, color: Int, alpha: Float, typeface: Typeface) {
        text.typeface = typeface
        text.textSize = size
        text.textScaleX = 1f
        text.letterSpacing = 0f
        text.clearShadowLayer()
        text.color = color
        text.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        text.textAlign = Paint.Align.LEFT
        val width = text.measureText(value)
        val center = right - width / 2f
        val baseline = top - text.fontMetrics.ascent
        canvas.save()
        canvas.scale(scaleX, 1f, center, top)
        canvas.drawText(value, right - width, baseline, text)
        canvas.restore()
    }
}
