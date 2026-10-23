package com.ngocdevv.nativecook.blocks.lists.stackedcolourcards

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.isActive

private const val LOOP_MS = 7_200L

/** A projected signpost whose deterministic autoplay heading can be taken over by one finger. */
@Composable
fun StackedColourCards(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val density = LocalDensity.current
    val view = LocalView.current
    val motion = remember { StackedColourCardsMotion() }
    val painter = remember { StackedColourCardsPainter() }
    val frame = remember { mutableLongStateOf(System.nanoTime()) }
    var manualTicker by remember { mutableIntStateOf(0) }

    LaunchedEffect(motion, autoPlay, reducedMotion, manualTicker) {
        if (reducedMotion || (!autoPlay && manualTicker == 0)) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { now ->
                frame.longValue = now
                if (!autoPlay && motion.decayHasStopped(now)) return@withFrameNanos
            }
            if (!autoPlay && motion.decayHasStopped(frame.longValue)) break
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(StackedColourCardsPalette.BackgroundTop)
            .clipToBounds(),
    ) {
        val width = maxWidth.value
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(motion, autoPlay, reducedMotion, density.density) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x / density.density
                        val startedAt = System.nanoTime()
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        motion.begin(
                            x = startX,
                            heading = motion.heading(startedAt, autoPlay, reducedMotion),
                        )
                        frame.longValue = startedAt
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                        var ended = false
                        while (!ended) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val x = change.position.x / density.density
                            if (!change.pressed) {
                                val velocity = tracker.calculateVelocity().x / density.density
                                val endedAt = System.nanoTime()
                                motion.end(endedAt, velocity, reducedMotion)
                                frame.longValue = endedAt
                                if (!reducedMotion) manualTicker += 1
                                ended = true
                            } else {
                                change.consume()
                                motion.change(x)
                                frame.longValue = System.nanoTime()
                            }
                        }
                        if (!ended) {
                            val endedAt = System.nanoTime()
                            motion.end(endedAt, 0f, reducedMotion)
                            frame.longValue = endedAt
                            if (!reducedMotion) manualTicker += 1
                        }
                    }
                },
        ) {
            val now = frame.longValue
            val heading = motion.heading(now, autoPlay, reducedMotion)
            val logicalWidth = size.width / density.density
            val logicalHeight = size.height / density.density
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { painter.draw(it.nativeCanvas, logicalWidth, logicalHeight, heading) }
            }
        }

        StackedColourCardsData.Signs.filter { it.named }.forEach { sign ->
            val label = sign.km?.let { "${sign.name}, $it kilometres away" } ?: sign.name
            Box(
                Modifier
                    .offset(x = (width / 2f - 110f).dp, y = (sign.y - sign.height / 2f).dp)
                    .width(220.dp)
                    .height(sign.height.dp)
                    .semantics { contentDescription = label },
            )
        }
    }
}

private object StackedColourCardsPalette {
    val BackgroundTop = Color(0xFF112F26)
}

private data class StackedColourCardsSign(
    val name: String,
    val named: Boolean = true,
    val nameDx: Float = 0f,
    val km: Int? = null,
    val color: Int,
    val ink: Int = AndroidColor.WHITE,
    val heading: Float,
    val y: Float,
    val x: Float,
    val length: Float,
    val tail: Float,
    val height: Float,
    val depth: Float,
    val tip: Float,
)

private object StackedColourCardsData {
    const val BackgroundTop = 0xFF112F26.toInt()
    const val BackgroundMiddle = 0xFF123A30.toInt()
    const val BackgroundBottom = 0xFF0E5BC7.toInt()
    const val Pitch = 16f
    const val Focal = 930f
    const val ShadeFace = 1f
    const val ShadeTop = 1.12f
    const val ShadeBottom = 0.62f
    const val ShadeEnd = 0.8f
    const val ShadeSide = 0.1f
    const val NameSize = 28f
    const val KmSize = 9f
    const val KmInset = 28f

    val Signs = listOf(
        StackedColourCardsSign("Jason", km = 23, color = 0xFF74B6A0.toInt(), heading = 8f, y = 24f, x = 0f, length = 292f, tail = 0.575f, height = 34f, depth = 20f, tip = 26f),
        StackedColourCardsSign("Kenji", named = false, color = 0xFFE64D50.toInt(), heading = 64f, y = 86f, x = -41f, length = 138f, tail = 0.515f, height = 38f, depth = 18f, tip = 20f),
        StackedColourCardsSign("Dylan", nameDx = 12f, km = 46, color = 0xFFDC5C2A.toInt(), ink = 0xFFF6C39D.toInt(), heading = 3f, y = 128f, x = 4f, length = 270f, tail = 0.515f, height = 51f, depth = 12.5f, tip = 32f),
        StackedColourCardsSign("Priya", named = false, color = 0xFFDBB66D.toInt(), ink = 0xFFC9A85A.toInt(), heading = 8f, y = 175f, x = -26f, length = 150f, tail = 0.5f, height = 71f, depth = 27f, tip = 0f),
        StackedColourCardsSign("Logan", named = false, color = 0xFFED977F.toInt(), heading = 37f, y = 234.5f, x = -24f, length = 132f, tail = 0.489f, height = 55f, depth = 10.5f, tip = 0f),
        StackedColourCardsSign("Mason", named = false, color = 0xFFE16228.toInt(), heading = 47f, y = 284.5f, x = -16f, length = 142f, tail = 0.402f, height = 46f, depth = 10.5f, tip = 20f),
        StackedColourCardsSign("Noah", nameDx = 13f, color = 0xFFEFA280.toInt(), ink = 0xFFE8915F.toInt(), heading = 8f, y = 343f, x = 5f, length = 269f, tail = 0.51f, height = 57f, depth = 15.5f, tip = 30f),
        StackedColourCardsSign("Tomás", named = false, color = 0xFF1051CD.toInt(), heading = 2.5f, y = 390f, x = -5f, length = 104f, tail = 0.577f, height = 27f, depth = 31.5f, tip = 0f),
        StackedColourCardsSign("Caleb", nameDx = -25f, km = 23, color = 0xFF0A65DB.toInt(), heading = 168f, y = 434.5f, x = 5f, length = 196f, tail = 0.28f, height = 65f, depth = 10.5f, tip = 32f),
        StackedColourCardsSign("Amara", named = false, color = 0xFFB0C9D9.toInt(), heading = 11f, y = 482.5f, x = -12f, length = 102f, tail = 0.492f, height = 42f, depth = 34f, tip = 0f),
        StackedColourCardsSign("Sam", nameDx = 4f, color = 0xFFF0AA88.toInt(), ink = 0xFFE8955F.toInt(), heading = 4f, y = 540f, x = 1f, length = 232f, tail = 0.48f, height = 62f, depth = 18f, tip = 28f),
        StackedColourCardsSign("Lena", named = false, color = 0xFFA4B44E.toInt(), heading = 23f, y = 615f, x = 8f, length = 132f, tail = 0.485f, height = 93f, depth = 32f, tip = 20f),
        StackedColourCardsSign("Jack", nameDx = 22f, color = 0xFFF2918A.toInt(), ink = 0xFFF7C0B8.toInt(), heading = 13f, y = 699f, x = -1f, length = 228f, tail = 0.48f, height = 68f, depth = 12f, tip = 0f),
        StackedColourCardsSign("Owen", nameDx = -2f, color = 0xFFF3A986.toInt(), ink = 0xFFE99A6A.toInt(), heading = -8f, y = 765.5f, x = 0f, length = 170f, tail = 0.5f, height = 75f, depth = 18f, tip = 0f),
        StackedColourCardsSign("Ryan", nameDx = 45f, km = 21, color = 0xFFEC6647.toInt(), ink = 0xFFF6C0A0.toInt(), heading = -3f, y = 839.5f, x = 0f, length = 214f, tail = 0.45f, height = 72f, depth = 14f, tip = 28f),
    )
}

private class StackedColourCardsMotion {
    private enum class Phase { Automatic, Dragging, Released, Idle }

    private var phase = Phase.Automatic
    private var loopStart = System.nanoTime()
    private var dragStartX = 0f
    private var dragStartHeading = 0f
    private var manualHeading = 0f
    private var releaseHeading = 0f
    private var releaseVelocity = 0f
    private var releaseTime = System.nanoTime()

    fun begin(x: Float, heading: Float) {
        phase = Phase.Dragging
        dragStartX = x
        dragStartHeading = heading
        manualHeading = heading
    }

    fun change(x: Float) {
        if (phase != Phase.Dragging) return
        manualHeading = dragStartHeading - (x - dragStartX) * DegreesPerPoint
    }

    fun end(now: Long, velocityX: Float, reducedMotion: Boolean) {
        releaseHeading = manualHeading
        releaseVelocity = -velocityX * DegreesPerPoint
        releaseTime = now
        phase = if (reducedMotion) Phase.Idle else Phase.Released
    }

    fun decayHasStopped(now: Long): Boolean {
        if (phase != Phase.Released) return true
        return abs(decayVelocity(ageSeconds(now))) < 1f
    }

    fun heading(now: Long, autoPlay: Boolean, reducedMotion: Boolean): Float = when (phase) {
        Phase.Dragging, Phase.Idle -> manualHeading
        Phase.Automatic -> {
            if (!autoPlay || reducedMotion) 0f else headingAt(loopSeconds(now))
        }
        Phase.Released -> {
            if (reducedMotion) {
                manualHeading
            } else {
                val age = ageSeconds(now)
                if (!autoPlay || age < ResumeAfter) {
                    releaseHeading + decayDistance(age)
                } else {
                    val returnFrom = normalised(releaseHeading + decayDistance(ResumeAfter))
                    if (age < ResumeAfter + ReturnDuration) {
                        val k = ((age - ResumeAfter) / ReturnDuration).coerceIn(0f, 1f)
                        val smooth = k * k * (3f - 2f * k)
                        returnFrom * (1f - smooth)
                    } else {
                        headingAt((age - ResumeAfter - ReturnDuration) % LoopSeconds)
                    }
                }
            }
        }
    }

    private fun loopSeconds(now: Long): Float = ((now - loopStart).coerceAtLeast(0L) / 1e9).toFloat() % LoopSeconds

    private fun ageSeconds(now: Long): Float = ((now - releaseTime).coerceAtLeast(0L) / 1e9).toFloat()

    private fun decayVelocity(age: Float): Float {
        val lambda = (1f - Deceleration) * SlopeFactor * 1_000f
        return releaseVelocity * exp(-lambda * age)
    }

    private fun decayDistance(age: Float): Float {
        val lambda = (1f - Deceleration) * SlopeFactor * 1_000f
        return if (lambda > 0f) releaseVelocity * (1f - exp(-lambda * age)) / lambda else releaseVelocity * age
    }

    private fun normalised(value: Float): Float = ((value % 360f) + 540f) % 360f - 180f

    private fun headingAt(time: Float): Float {
        var index = 0
        while (index < HeadingKeys.lastIndex - 1 && HeadingKeys[index + 1].first <= time) index += 1
        val (t1, v1) = HeadingKeys[index]
        val (t2, v2) = HeadingKeys[index + 1]
        val v0 = if (index > 0) HeadingKeys[index - 1].second else HeadingKeys[HeadingKeys.lastIndex - 1].second
        val v3 = if (index + 2 < HeadingKeys.size) HeadingKeys[index + 2].second else HeadingKeys[1].second
        val u = ((time - t1) / (t2 - t1)).coerceIn(0f, 1f)
        val u2 = u * u
        val u3 = u2 * u
        return 0.5f * (2f * v1 + (-v0 + v2) * u + (2f * v0 - 5f * v1 + 4f * v2 - v3) * u2 + (-v0 + 3f * v1 - 3f * v2 + v3) * u3)
    }

    private companion object {
        const val LoopSeconds = LOOP_MS / 1_000f
        const val ResumeAfter = 3.5f
        const val ReturnDuration = 0.7f
        const val DegreesPerPoint = 0.45f
        const val Deceleration = 0.994f
        const val SlopeFactor = 0.1f
        val HeadingKeys = listOf(
            0f to 0f,
            0.6f to -11f,
            1.2f to -23f,
            1.8f to -35f,
            2.4f to -18f,
            3f to -7f,
            3.6f to 0f,
            4.2f to 3f,
            4.8f to -17f,
            5.4f to -3f,
            6f to -8f,
            6.6f to -15f,
            7.2f to 0f,
        )
    }
}

private class StackedColourCardsPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val name = textPaint(StackedColourCardsData.NameSize)
    private val km = textPaint(StackedColourCardsData.KmSize)
    private val matrix = Matrix()
    private val background = Paint(Paint.ANTI_ALIAS_FLAG)
    private var backgroundHeight = -1f

    fun draw(canvas: AndroidCanvas, width: Float, height: Float, heading: Float) {
        if (height != backgroundHeight) {
            background.shader = LinearGradient(
                0f,
                0f,
                0f,
                height,
                intArrayOf(StackedColourCardsData.BackgroundTop, StackedColourCardsData.BackgroundMiddle, StackedColourCardsData.BackgroundBottom),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
            backgroundHeight = height
        }
        canvas.drawRect(0f, 0f, width, height, background)
        for (sign in StackedColourCardsData.Signs.asReversed()) drawBoard(canvas, sign, width / 2f, heading)
    }

    private fun drawBoard(canvas: AndroidCanvas, sign: StackedColourCardsSign, centreX: Float, heading: Float) {
        val halfHeight = sign.height / 2f
        val halfDepth = sign.depth / 2f
        val tailX = -sign.length * sign.tail
        val pointX = sign.length - sign.length * sign.tail
        val shoulderX = pointX - sign.tip
        val faceWidth = shoulderX - tailX
        val tipLength = hypot(sign.tip, halfHeight)
        val originX = centreX + sign.x
        val yaw = sign.heading + heading

        fun p(x: Float, y: Float, z: Float) = project(x, y, z, yaw, originX, sign.y)

        val front = facing(0f, 0f, 1f, yaw) > 0f
        val z = if (front) halfDepth else -halfDepth
        val face = path(p(tailX, halfHeight, z), p(shoulderX, halfHeight, z), p(pointX, 0f, z), p(shoulderX, -halfHeight, z), p(tailX, -halfHeight, z))
        val top = if (facing(0f, 1f, 0f, yaw) > 0f) {
            path(p(tailX, halfHeight, -halfDepth), p(shoulderX, halfHeight, -halfDepth), p(shoulderX, halfHeight, halfDepth), p(tailX, halfHeight, halfDepth))
        } else null
        val end = if (facing(-1f, 0f, 0f, yaw) > 0f) {
            path(p(tailX, halfHeight, -halfDepth), p(tailX, halfHeight, halfDepth), p(tailX, -halfHeight, halfDepth), p(tailX, -halfHeight, -halfDepth))
        } else null
        var tipUpper: Path? = null
        var tipLower: Path? = null
        if (sign.tip > 0f) {
            if (facing(halfHeight / tipLength, sign.tip / tipLength, 0f, yaw) > 0f) {
                tipUpper = path(p(shoulderX, halfHeight, -halfDepth), p(pointX, 0f, -halfDepth), p(pointX, 0f, halfDepth), p(shoulderX, halfHeight, halfDepth))
            }
            if (facing(halfHeight / tipLength, -sign.tip / tipLength, 0f, yaw) > 0f) {
                tipLower = path(p(shoulderX, -halfHeight, -halfDepth), p(pointX, 0f, -halfDepth), p(pointX, 0f, halfDepth), p(shoulderX, -halfHeight, halfDepth))
            }
        } else if (facing(1f, 0f, 0f, yaw) > 0f) {
            tipLower = path(p(pointX, halfHeight, -halfDepth), p(pointX, halfHeight, halfDepth), p(pointX, -halfHeight, halfDepth), p(pointX, -halfHeight, -halfDepth))
        }

        draw(canvas, top, shade(sign.color, StackedColourCardsData.ShadeTop))
        draw(canvas, end, shade(sign.color, StackedColourCardsData.ShadeEnd))
        draw(canvas, tipLower, shade(sign.color, if (sign.tip > 0f) 0.74f else StackedColourCardsData.ShadeEnd))
        draw(canvas, tipUpper, shade(sign.color, 0.92f))
        draw(canvas, face, sign.color)

        val a = if (front) tailX else shoulderX
        val b = if (front) shoulderX else tailX
        val p0 = p(a, halfHeight, z)
        val p1 = p(b, halfHeight, z)
        val p2 = p(b, -halfHeight, z)
        val p3 = p(a, -halfHeight, z)
        rectToQuad(matrix, faceWidth, sign.height, p0, p1, p2, p3)
        val opacity = ((abs(facing(0f, 0f, 1f, yaw)) - 0.18f) / 0.22f).coerceIn(0f, 1f)
        val kmText = sign.km?.let { "$it km" } ?: ""
        val nameWidth = name.measureText(sign.name)
        val kmWidth = if (kmText.isEmpty()) 0f else km.measureText(kmText)
        val nameY = halfHeight + StackedColourCardsData.NameSize * 0.36f - if (kmText.isEmpty()) 0f else 2f

        canvas.save()
        canvas.concat(matrix)
        name.color = sign.ink
        name.alpha = (opacity * 255f).roundToInt()
        km.color = sign.ink
        km.alpha = name.alpha
        if (sign.named) canvas.drawText(sign.name, (faceWidth - nameWidth) / 2f + sign.nameDx, nameY, name)
        if (kmText.isNotEmpty()) {
            val kmX = if (front) faceWidth - StackedColourCardsData.KmInset - kmWidth else StackedColourCardsData.KmInset
            canvas.drawText(kmText, kmX, sign.height - 6f, km)
        }
        canvas.restore()
    }

    private fun draw(canvas: AndroidCanvas, path: Path?, color: Int) {
        if (path == null) return
        fill.shader = null
        fill.alpha = 255
        fill.color = color
        canvas.drawPath(path, fill)
    }

    private fun project(x: Float, y: Float, z: Float, yaw: Float, centreX: Float, centreY: Float): PointF {
        val cosine = cos(yaw * Radians)
        val sine = sin(yaw * Radians)
        val projectedX = x * cosine + z * sine
        val depth = -x * sine + z * cosine
        val projectedY = y * CosPitch - depth * SinPitch
        val pitchedDepth = depth * CosPitch + y * SinPitch
        val scale = StackedColourCardsData.Focal / (StackedColourCardsData.Focal - pitchedDepth)
        return PointF(centreX + projectedX * scale, centreY - projectedY * scale)
    }

    private fun facing(nx: Float, ny: Float, nz: Float, yaw: Float): Float {
        val depth = -nx * sin(yaw * Radians) + nz * cos(yaw * Radians)
        return depth * CosPitch + ny * SinPitch
    }

    private fun rectToQuad(matrix: Matrix, width: Float, height: Float, p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
        val dx1 = p1.x - p2.x
        val dx2 = p3.x - p2.x
        val dx3 = p0.x - p1.x + p2.x - p3.x
        val dy1 = p1.y - p2.y
        val dy2 = p3.y - p2.y
        val dy3 = p0.y - p1.y + p2.y - p3.y
        var g = 0f
        var h = 0f
        if (abs(dx3) > 1e-6f || abs(dy3) > 1e-6f) {
            val determinant = dx1 * dy2 - dx2 * dy1
            if (abs(determinant) > 1e-9f) {
                g = (dx3 * dy2 - dx2 * dy3) / determinant
                h = (dx1 * dy3 - dx3 * dy1) / determinant
            }
        }
        matrix.setValues(
            floatArrayOf(
                (p1.x - p0.x + g * p1.x) / width,
                (p3.x - p0.x + h * p3.x) / height,
                p0.x,
                (p1.y - p0.y + g * p1.y) / width,
                (p3.y - p0.y + h * p3.y) / height,
                p0.y,
                g / width,
                h / height,
                1f,
            ),
        )
    }

    private fun path(first: PointF, vararg rest: PointF): Path = Path().apply {
        moveTo(first.x, first.y)
        rest.forEach { lineTo(it.x, it.y) }
        close()
    }

    private fun shade(color: Int, factor: Float): Int {
        fun component(value: Int): Int {
            val changed = if (factor >= 1f) value + (255 - value) * (factor - 1f) else value * factor
            return changed.roundToInt().coerceIn(0, 255)
        }
        return AndroidColor.rgb(component(AndroidColor.red(color)), component(AndroidColor.green(color)), component(AndroidColor.blue(color)))
    }

    private fun textPaint(size: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 500, false)
        } else {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        textSize = size
        isSubpixelText = true
    }

    private companion object {
        const val Radians = (PI / 180).toFloat()
        val SinPitch = sin(StackedColourCardsData.Pitch * Radians)
        val CosPitch = cos(StackedColourCardsData.Pitch * Radians)
    }
}
