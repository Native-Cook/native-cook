package com.ngocdevv.nativecook.blocks.inputs.lensvolumeslider

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CANVAS_WIDTH = 402.0
private const val TRACK_X = 24.0
private const val TRACK_W = 354.0
private const val TRACK_H = 104.0
private const val TRACK_FROM_BOTTOM = 181.0
private const val INSET = 8.0
private const val BALL = 88.0

/** Compose port of the Lens Volume Slider. Drag the capsule, tap the speaker to mute, and tap the marble to restore. */
@Composable
fun LensVolumeSliderDemo(autoPlay: Boolean = true) {
    val context = LocalContext.current
    val art = remember(context) { LensVolumeSliderArt.load(context) }
    val refraction = remember { LensVolumeSliderRefraction() }
    val intent = (context as? Activity)?.intent
    val qaTime = remember { if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) && intent?.hasExtra("lens-time") == true) intent.getStringExtra("lens-time")?.toDoubleOrNull() else null }
    val qaNeutral = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) && intent?.getBooleanExtra("lens-neutral", false) == true }
    val automatic = autoPlay && !qaNeutral
    val reducedMotion = rememberLiveReducedMotion()
    var takeover by remember { mutableStateOf<LensVolumeSliderMotion.Frame?>(null) }
    var takeoverClock by remember { mutableDoubleStateOf(0.0) }
    var plateClock by remember { mutableDoubleStateOf(0.0) }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var value by remember { mutableDoubleStateOf(LensVolumeSliderMotion.REST) }
    var lastNonZero by remember { mutableDoubleStateOf(LensVolumeSliderMotion.REST) }
    var fingerDown by remember { mutableStateOf(false) }
    var userDriven by remember { mutableStateOf(false) }
    var lastRelease by remember { mutableDoubleStateOf(0.0) }
    var returnStart by remember { mutableDoubleStateOf(-1.0) }
    var returnFrom by remember { mutableDoubleStateOf(LensVolumeSliderMotion.REST) }

    fun takeOver() {
        if (!userDriven && !reducedMotion && automatic) {
            val shown = LensVolumeSliderMotion.frameAt(qaTime ?: clock)
            value = shown.value
            takeover = shown
            plateClock = qaTime ?: clock
        }
        takeoverClock = clock
        userDriven = true
        returnStart = -1.0
    }

    LaunchedEffect(automatic, reducedMotion, userDriven, returnStart) {
        if (reducedMotion) {
            // Accessibility's animator setting is the source of truth: retain a readable resting control, no clock.
            if (!userDriven) value = LensVolumeSliderMotion.REST
            return@LaunchedEffect
        }
        if (qaTime != null && !userDriven) {
            value = LensVolumeSliderMotion.frameAt(qaTime).value
            return@LaunchedEffect
        }
        var prior = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - prior) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                prior = now
                clock += dt
                if (automatic && !userDriven) value = LensVolumeSliderMotion.frameAt(qaTime ?: clock).value
                if (automatic && qaTime == null && userDriven && !fingerDown) {
                    val idle = clock - lastRelease
                    when {
                        returnStart >= 0.0 -> {
                            val p = ((clock - returnStart) / 0.48).coerceIn(0.0, 1.0)
                            value = lerp(returnFrom, LensVolumeSliderMotion.REST, easeOut(p))
                            if (p >= 1.0) { userDriven = false; returnStart = -1.0; clock = 0.0 }
                        }
                        idle > 2.0 -> { returnFrom = value; returnStart = clock }
                    }
                }
            }
        }
    }

    Canvas(
        modifier = Modifier.fillMaxSize().background(Color.Black).semantics {
            contentDescription = "Speaker volume"
            stateDescription = "${value.roundToInt()} percent"
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..100f, 99)
            setProgress { amount ->
                takeOver()
                value = amount.toDouble().coerceIn(0.0, 100.0)
                if (value > 0.5) lastNonZero = value
                lastRelease = clock
                true
            }
        }.pointerInput(reducedMotion, automatic, qaTime) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                val screenWidth = size.width.toDouble().coerceAtLeast(1.0)
                val scale = screenWidth / CANVAS_WIDTH
                val designY = size.height / scale - TRACK_FROM_BOTTOM
                if (start.y / scale !in designY..(designY + TRACK_H)) return@awaitEachGesture
                takeOver()
                val startingValue = value
                fingerDown = true
                var moved = false
                try { while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    val dx = change.position.x - start.x
                    if (abs(dx) > 5f) moved = true
                    if (change.pressed) {
                        value = (startingValue + dx / scale / 2.5).coerceIn(0.0, 100.0)
                        if (value > 0.5) lastNonZero = value
                        change.consume()
                    } else {
                        if (!moved) {
                            val designX = start.x / scale
                            value = when {
                                startingValue < 0.5 && designX < TRACK_X + BALL + 12.0 -> lastNonZero
                                startingValue >= 0.5 && designX in 40.0..86.0 -> { lastNonZero = startingValue; 0.0 }
                                else -> ((designX - TRACK_X - INSET - BALL / 2.0) / 2.5).coerceIn(0.0, 100.0)
                            }
                            if (value > 0.5) lastNonZero = value
                        }
                        fingerDown = false; lastRelease = clock; break
                    }
                } } finally {
                    // A cancelled pointer never triggers the tap or a release impulse.
                    fingerDown = false; lastRelease = clock
                }
            }
        },
    ) {
        val scale = size.width.toDouble() / CANVAS_WIDTH
        val designHeight = size.height / scale
        val autoFrame = LensVolumeSliderMotion.frameAt(qaTime ?: clock)
        val manual = userDriven || reducedMotion || !automatic
        val muted = if (manual) value < 0.5 else autoFrame.muted
        val adoption = ((clock - takeoverClock) / .18).coerceIn(0.0, 1.0)
        val held = if (fingerDown) 1.0 else 0.0
        val lift = if (manual) lerp(takeover?.lift ?: held, held, adoption) else autoFrame.lift
        val shownValue = if (manual) value else autoFrame.value
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            canvas.save(); canvas.scale(scale.toFloat(), scale.toFloat())
            art.draw(canvas, refraction, size.width.toInt(), size.height.toInt(), designHeight, scale, shownValue, muted, lift, if (manual) (takeover?.flash ?: 0.0) * (1-adoption) else autoFrame.flash, if (manual) .55 else autoFrame.shimmer, if (manual) plateClock else (qaTime ?: clock), if (manual) (takeover?.stretch ?: 0.0) * (1-adoption) else autoFrame.stretch, if (manual) lerp(takeover?.zoom ?: value, value, adoption) else autoFrame.zoom)
            canvas.restore()
        }
    }
}

private class LensVolumeSliderArt(private val plate: Bitmap, private val typeface: Typeface) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isLinearText = true; isSubpixelText = true; this.typeface = this@LensVolumeSliderArt.typeface }
    private val rect = RectF()
    private val lensClip = Path()

    fun draw(canvas: AndroidCanvas, refraction: LensVolumeSliderRefraction, width: Int, height: Int, designHeight: Double, scale: Double, value: Double, muted: Boolean, lift: Double, flash: Double, shimmer: Double, clock: Double, stretch: Double, zoom: Double) {
        val trackY = designHeight - TRACK_FROM_BOTTOM
        val driftX = -26.0 + 8.0 * sin(clock * Math.PI * 2.0 / LensVolumeSliderMotion.LOOP)
        val driftY = 5.0 * sin(clock * Math.PI * 4.0 / LensVolumeSliderMotion.LOOP + 1.0)
        canvas.drawColor(AndroidColor.BLACK)
        drawPlate(canvas, designHeight, driftX, driftY, zoom)

        rect.set(TRACK_X.toFloat(), (trackY + 8).toFloat(), (TRACK_X + TRACK_W).toFloat(), (trackY + TRACK_H + 16).toFloat())
        fill.color = AndroidColor.argb(66, 0, 0, 0); canvas.drawRoundRect(rect, 52f, 52f, fill)
        rect.set(TRACK_X.toFloat(), trackY.toFloat(), (TRACK_X + TRACK_W).toFloat(), (trackY + TRACK_H).toFloat())
        fill.shader = LinearGradient(0f, trackY.toFloat(), 0f, (trackY + TRACK_H).toFloat(), intArrayOf(0xCC3C3431.toInt(), 0xB71A1B1C.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 52f, 52f, fill); fill.shader = null
        stroke.color = 0xB29EABB1.toInt(); stroke.strokeWidth = 1.5f; canvas.drawRoundRect(rect, 52f, 52f, stroke)

        val fillW = (BALL + 2.5 * value + stretch).coerceIn(BALL, 338.0)
        val fillLeft = TRACK_X + INSET
        val endX = fillLeft + fillW - (BALL + 8 * lift) / 2
        val centerY = trackY + TRACK_H / 2.0
        val radius = BALL / 2.0 * (1.0 + lift * 0.09)
        val bodyRight = fillLeft + fillW
        rect.set(fillLeft.toFloat(), (centerY - radius).toFloat(), bodyRight.toFloat(), (centerY + radius).toFloat())
        fill.shader = LinearGradient(fillLeft.toFloat(), (centerY - radius).toFloat(), bodyRight.toFloat(), (centerY + radius).toFloat(), intArrayOf(0x944F5357.toInt(), 0x7A3E3E3F.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), fill); fill.shader = null

        val ballX = endX.toFloat(); val ballY = centerY.toFloat(); val ballR = radius.toFloat()
        fill.shader = RadialGradient(ballX - ballR * .28f, ballY - ballR * .30f, ballR * 1.35f, intArrayOf(0xF0AABAC0.toInt(), 0xAC52636B.toInt(), 0xB322292D.toInt()), floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(ballX, ballY, ballR, fill); fill.shader = null
        stroke.color = 0xFF597691.toInt(); stroke.strokeWidth = 2.2f; canvas.drawCircle(ballX, ballY, ballR - 1f, stroke)
        stroke.color = 0x8FB08A6A.toInt(); stroke.strokeWidth = 1.4f; canvas.drawCircle(ballX, ballY, ballR - 4f, stroke)

        drawUi(canvas, value, muted, trackY)
        if (refraction.usesAgsl) canvas.save()
        if (refraction.usesAgsl) canvas.scale((1.0 / scale).toFloat(), (1.0 / scale).toFloat())
        refraction.draw(
            canvas = canvas, width = width, height = height, centerX = ballX * scale.toFloat(), centerY = ballY * scale.toFloat(), radius = ballR * scale.toFloat(),
            drawSource = { scratch -> scratch.save(); scratch.scale(scale.toFloat(), scale.toFloat()); drawPlate(scratch, designHeight, driftX, driftY, zoom); drawUi(scratch, value, muted, trackY); scratch.restore() },
            drawFallback = { fallback ->
                val restore = fallback.save()
                lensClip.rewind()
                lensClip.addCircle(ballX, ballY, ballR, Path.Direction.CW)
                fallback.clipPath(lensClip)
                fallback.scale(-1.16f, -1.16f, ballX, ballY)
                drawPlate(fallback, designHeight, driftX, driftY, zoom)
                drawUi(fallback, value, muted, trackY)
                fallback.restoreToCount(restore)
            },
        )
        if (refraction.usesAgsl) canvas.restore()
        stroke.color = 0x73FFFFFF; stroke.strokeWidth = 1f; canvas.drawArc(ballX - ballR + 3, ballY - ballR + 3, ballX + ballR - 3, ballY + ballR - 3, 205f, 105f, false, stroke)
        fill.color = AndroidColor.argb((42 + flash * 86).roundToInt().coerceIn(0, 150), 206, 233, 239); canvas.drawOval(ballX + ballR + 8, ballY - 26, ballX + ballR + 17, ballY + 28, fill)
        if (muted) { fill.color = AndroidColor.argb(122, 220, 239, 244); canvas.drawCircle(ballX + ballR + 34, ballY, 9f, fill) }
        if (flash > .01) { stroke.color = AndroidColor.argb((flash * 170).roundToInt(), 232, 246, 248); stroke.strokeWidth = 2.5f; canvas.drawRoundRect(rect, 52f, 52f, stroke) }
        val glintFade = (minOf(shimmer, 1-shimmer) / .08).coerceIn(0.0, 1.0)
        fill.color = AndroidColor.argb((100 * glintFade).roundToInt(), 255, 255, 255); canvas.drawCircle((TRACK_X + 20 + shimmer * (TRACK_W - 40)).toFloat(), (trackY + 7).toFloat(), 2.3f, fill)
    }

    private fun drawPlate(canvas: AndroidCanvas, h: Double, driftX: Double, driftY: Double, zoom: Double) {
        fill.shader = null; fill.color = AndroidColor.WHITE
        val cover = max(CANVAS_WIDTH / plate.width, h / plate.height) * (1.05 + .04 * zoom / 100)
        val w = plate.width * cover; val height = plate.height * cover
        val left = (CANVAS_WIDTH - w) / 2 + driftX; val top = (h - height) / 2 + driftY
        rect.set(left.toFloat(), top.toFloat(), (left+w).toFloat(), (top+height).toFloat())
        canvas.drawBitmap(plate, null, rect, fill)
        // Mirror the adjacent tile so the fixed sideways camera shift never opens a black edge.
        canvas.save(); canvas.translate((2*(left+w)).toFloat(), 0f); canvas.scale(-1f, 1f)
        canvas.drawBitmap(plate, null, rect, fill); canvas.restore()
    }

    private fun drawUi(canvas: AndroidCanvas, value: Double, muted: Boolean, trackY: Double) {
        val cy = (trackY + TRACK_H / 2.0).toFloat(); val color = if (muted) 0xFF242C2F.toInt() else 0xFFDFDFDF.toInt()
        drawSpeaker(canvas, 63f, cy, color, value, muted)
        text.color = color; text.textSize = 28f; text.textAlign = Paint.Align.LEFT
        canvas.drawText(value.roundToInt().toString(), 88f, cy - (text.ascent() + text.descent()) / 2f, text)
    }

    private fun drawSpeaker(canvas: AndroidCanvas, x: Float, y: Float, color: Int, value: Double, muted: Boolean) {
        fill.color = color
        val body = Path().apply { moveTo(x - 15, y - 5); lineTo(x - 9, y - 5); lineTo(x - 2, y - 12); lineTo(x - 2, y + 12); lineTo(x - 9, y + 5); lineTo(x - 15, y + 5); close() }
        canvas.drawPath(body, fill); stroke.color = color; stroke.strokeWidth = 2.1f
        if (!muted && value > .5) canvas.drawArc(x - 5, y - 12, x + 17, y + 12, -55f, 110f, false, stroke)
        if (!muted && value >= 34) canvas.drawArc(x - 8, y - 17, x + 25, y + 17, -55f, 110f, false, stroke)
        if (muted) { stroke.strokeWidth = 2.3f; canvas.drawLine(x - 16, y - 15, x + 15, y + 15, stroke) }
    }

    companion object {
        fun load(context: Context): LensVolumeSliderArt {
            val assets = context.assets
            val plate = assets.open("lensvolumeslider/lensvolumeslider_plate.jpg").use { BitmapFactory.decodeStream(it)!! }
            return LensVolumeSliderArt(plate, Typeface.createFromAsset(assets, "lensvolumeslider/lensvolumeslider_InterDisplay_SemiBold.ttf"))
        }
    }
}

private fun lerp(start: Double, end: Double, t: Double) = start + (end - start) * t
private fun easeOut(t: Double) = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)

@Preview
@Composable
private fun LensVolumeSliderDemoPreview() { LensVolumeSliderDemo() }
