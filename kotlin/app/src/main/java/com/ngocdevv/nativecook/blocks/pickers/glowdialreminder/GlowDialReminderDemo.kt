package com.ngocdevv.nativecook.blocks.pickers.glowdialreminder

import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val GDR_W = 402.0

/** Full-screen reminder setup with a rotary sleep-goal dial and a single molten-light field. */
@Composable
fun GlowDialReminderDemo(autoPlay: Boolean = true) {
    val context = LocalContext.current
    val art = remember(context) { GlowDialReminderArt.load(context) }
    val field = remember { GlowDialReminderField() }
    val reduced = remember { !ValueAnimator.areAnimatorsEnabled() }
    val intent = (context as? Activity)?.intent
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val qaTime = remember { if (debug && intent?.hasExtra("glow-time") == true) intent.getStringExtra("glow-time")?.toDoubleOrNull() else null }
    val qaNeutral = remember { debug && intent?.getBooleanExtra("glow-neutral", false) == true }
    val automatic = autoPlay && !qaNeutral && !reduced
    val bottomInset = WindowInsets.navigationBars.getBottom(LocalDensity.current).toDouble()
    var tracking by remember { mutableStateOf(false) }
    var manualOff by remember { mutableDoubleStateOf(0.0) }
    var clock by remember { mutableDoubleStateOf(0.0) }
    var hours by remember { mutableDoubleStateOf(8.0) }
    var isOn by remember { mutableStateOf(true) }
    var hand by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var lastRelease by remember { mutableDoubleStateOf(0.0) }
    var returnStart by remember { mutableDoubleStateOf(-1.0) }
    var returnFrom by remember { mutableDoubleStateOf(8.0) }

    LaunchedEffect(automatic, reduced, editing, returnStart) {
        if (reduced) { if (!editing) { hours = 8.0; isOn = true }; return@LaunchedEffect }
        var previous = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            val dt = ((now - previous) / 1e9).coerceIn(0.0, .05)
            clock += dt; previous = now
            if (automatic && !editing) {
                val f = GlowDialReminderMotion.frameAt(qaTime ?: clock); hours = f.hours; isOn = f.off < .5
            } else {
                val targetOff = if (isOn) 0.0 else 1.0
                manualOff += (targetOff - manualOff) * (1 - kotlin.math.exp(-dt * 5))
            }
            if (automatic && qaTime == null && editing && !hand) {
                if (returnStart >= 0.0) {
                    val p = ((clock - returnStart) / .6).coerceIn(0.0, 1.0)
                    hours = mix(returnFrom, 8.0, easeOut(p))
                    if (p >= 1.0) { editing = false; returnStart = -1.0; clock = 0.0; isOn = true; tracking = false; manualOff = 0.0 }
                } else if (clock - lastRelease > 2.0) { returnFrom = hours; returnStart = clock }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    Canvas(
        Modifier.fillMaxSize().background(Color(0xFF0A090A)).pointerInput(reduced, bottomInset) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val available = size.height - maxOf(12.0 * density, bottomInset)
                val scale = minOf(size.width.toDouble() / GDR_W, available / 821, (available - 44.0 * density) / 773)
                val left = (size.width - GDR_W * scale) / 2
                val ctaH = maxOf(44.0 * density, 48 * scale)
                val ctaTop = available - 5 * scale - ctaH
                val point = down.position
                val x = (point.x - left) / scale; val y = point.y / scale
                val kind = when {
                    x in 252.0..360.0 && y in 185.0..245.0 -> 1
                    (x - 306.0) * (x - 306.0) + (y - 680.0) * (y - 680.0) < 70.0 * 70.0 -> 2
                    y in 485.0..555.0 -> 3
                    x in 108.0..308.0 && point.y.toDouble() in ctaTop..(ctaTop + ctaH) -> 4
                    else -> 0
                }
                if (kind == 0) return@awaitEachGesture
                if (!editing && automatic) {
                    val shown = GlowDialReminderMotion.frameAt(qaTime ?: clock)
                    hours = shown.hours; isOn = shown.off < .5; manualOff = shown.off
                }
                editing = true; hand = true; returnStart = -1.0
                val startHours = hours; val startX = x; var priorAngle = atan2(y - 680.0, x - 306.0)
                var wasMove = false
                try { while (true) {
                    val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    val nx = (change.position.x - left) / scale; val ny = change.position.y / scale
                    if (change.pressed) {
                        when (kind) {
                            2 -> { val angle = atan2(ny - 680.0, nx - 306.0); val delta = atan2(sin(angle - priorAngle), cos(angle - priorAngle)); hours = (hours + delta * 180.0 / PI / 30.0).coerceIn(4.0, 12.0); priorAngle = angle }
                            3 -> { hours = (startHours - (nx - startX) / 72.0).coerceIn(4.0, 12.0); wasMove = wasMove || abs(nx - startX) > 3.0 }
                            1 -> { wasMove = wasMove || abs(nx - startX) > 3.0; if (wasMove) isOn = nx > 307.0 }
                            4 -> { wasMove = wasMove || abs(nx - startX) > 8.0 || abs(ny - y) > 8.0 }
                        }
                        change.consume()
                    } else {
                        if (kind == 1 && !wasMove) isOn = !isOn
                        if (kind == 4 && !wasMove && change.position.y.toDouble() in ctaTop..(ctaTop + ctaH)) tracking = !tracking
                        if (reduced) manualOff = if (isOn) 0.0 else 1.0
                        break
                    }
                } } finally {
                    hours = (hours * 4).roundToInt() / 4.0
                    hand = false; lastRelease = clock
                }
            }
        },
    ) {
        val available = size.height - maxOf(12.0 * density, bottomInset)
        val scale = minOf(size.width.toDouble() / GDR_W, available / 821, (available - 44.0 * density) / 773)
        val left = (size.width - GDR_W * scale) / 2
        val h = size.height / scale
        val ctaH = maxOf(44.0 * density, 48 * scale)
        val ctaTop = (available - 5 * scale - ctaH) / scale
        val f = GlowDialReminderMotion.frameAt(qaTime ?: clock)
        val manual = editing || !automatic
        val off = if (manual) manualOff else f.off
        val shownHours = if (manual) hours else f.hours
        val shear = if (manual) 0.0 else f.shear
        drawIntoCanvas { c ->
            c.nativeCanvas.save(); c.nativeCanvas.translate(left.toFloat(), 0f); c.nativeCanvas.scale(scale.toFloat(), scale.toFloat())
            art.draw(c.nativeCanvas, field, h, scale.toFloat(), shownHours, off, shear, if (manual) 0.0 else f.ring, clock, tracking, ctaTop, ctaH / scale)
            c.nativeCanvas.restore()
        }
    }
    // Native semantic nodes expose the exact visual hit regions while pointer input remains on the Canvas.
    val availableDp = maxHeight.value - maxOf(12f, (bottomInset / LocalDensity.current.density).toFloat())
    val sceneScale = minOf(maxWidth.value / 402f, availableDp / 821f, (availableDp - 44f) / 773f)
    val sceneLeft = (maxWidth.value - 402f * sceneScale) / 2
    val ctaDp = maxOf(44f, 48f * sceneScale)
    val ctaTopDp = availableDp - 5f * sceneScale - ctaDp
    fun takeOver() {
        if (!editing && automatic) {
            val f = GlowDialReminderMotion.frameAt(qaTime ?: clock)
            hours = f.hours; isOn = f.off < .5; manualOff = f.off
        }
        editing = true; hand = false; returnStart = -1.0; lastRelease = clock
    }
    Box(Modifier.offset((sceneLeft + 262f * sceneScale).dp, (195f * sceneScale).dp)
        .size((90f * sceneScale).dp, (42f * sceneScale).dp).semantics {
            contentDescription = "Reminder"; role = Role.Switch
            stateDescription = if (isOn) "On" else "Off"
            toggleableState = if (isOn) ToggleableState.On else ToggleableState.Off
            onClick { takeOver(); isOn = !isOn; if (reduced) manualOff = if (isOn) 0.0 else 1.0; true }
        })
    Box(Modifier.offset((sceneLeft + 236f * sceneScale).dp, (610f * sceneScale).dp)
        .size((140f * sceneScale).dp).semantics {
            contentDescription = "Sleep goal"; stateDescription = "${(hours * 4).roundToInt() / 4.0} hours"
            progressBarRangeInfo = ProgressBarRangeInfo(hours.toFloat(), 4f..12f, 31)
            setProgress { target -> takeOver(); hours = (target.coerceIn(4f, 12f) * 4).roundToInt() / 4.0; true }
        })
    Box(Modifier.offset((sceneLeft + 20f * sceneScale).dp, (485f * sceneScale).dp)
        .size((362f * sceneScale).dp, (70f * sceneScale).dp).semantics {
            contentDescription = "Duration ruler"; stateDescription = "${(hours * 4).roundToInt() / 4.0} hours"
        })
    Box(Modifier.offset((sceneLeft + 108f * sceneScale).dp, ctaTopDp.dp)
        .size((200f * sceneScale).dp, ctaDp.dp).semantics {
            contentDescription = if (tracking) "Tracking" else "Start tracking"; role = Role.Button
            onClick { takeOver(); tracking = !tracking; true }
        })
    val bedtime = (360 - (hours * 4).roundToInt() * 15 + 1440) % 1440
    val bedtimeHour = ((bedtime / 60 + 11) % 12) + 1
    Box(Modifier.offset((sceneLeft + 166f * sceneScale).dp, (648f * sceneScale).dp)
        .size((36f * sceneScale).dp, (66f * sceneScale).dp).semantics {
            contentDescription = "Bedtime $bedtimeHour:${(bedtime % 60).toString().padStart(2, '0')}"
        })
    }
}

private class GlowDialReminderArt(
    private val regular: Typeface,
    private val medium: Typeface,
    private val doto: Typeface,
    private val grain: Bitmap,
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true; isLinearText = true }
    private val card = RectF(4f, 143f, 398f, 900f)
    private val cardClip = Path()
    private val inset = RectF()

    fun draw(canvas: AndroidCanvas, field: GlowDialReminderField, screenHeight: Double, scale: Float, hours: Double, off: Double, shear: Double, ring: Double, clock: Double, tracking: Boolean, ctaTop: Double, ctaHeight: Double) {
        canvas.drawColor(0xFF0A090A.toInt())
        fill.color = 0xFF1B1B1B.toInt(); canvas.drawCircle(56f, 99f, 24f, fill)
        line.color = 0xFFE5E5E5.toInt(); line.strokeWidth = 3f; canvas.drawLine(67f, 99f, 46f, 99f, line); canvas.drawLine(46f, 99f, 55f, 90f, line); canvas.drawLine(46f, 99f, 55f, 108f, line)
        label(canvas, "Complete setup", 104f, 108f, 24f, 0xFFF2F0F2.toInt(), regular)
        card.bottom = screenHeight.toFloat() + 44f
        canvas.save()
        cardClip.rewind()
        cardClip.addRoundRect(card, 46f, 46f, Path.Direction.CW)
        canvas.clipPath(cardClip)
        field.draw(canvas, card, scale, off, shear, ring)
        fill.alpha = 14
        for (x in -128 until 512 step 128) for (y in 144 until screenHeight.toInt() step 128) canvas.drawBitmap(grain, null, RectF(x.toFloat(), y.toFloat(), (x + 128).toFloat(), (y + 128).toFloat()), fill)
        fill.alpha = 255

        label(canvas, "Reminder", 54f, 222f, 24f, 0xFF56314F.toInt(), regular)
        drawToggle(canvas, off)
        drawNumeral(canvas, hours.roundToInt(), off)
        labelCentered(canvas, if (hours.roundToInt() == 1) "hour" else "hours", 201f, 488f, 15f, if (off > .6) 0xFF56314F.toInt() else 0xFFF6E3F0.toInt(), medium)
        drawRuler(canvas, hours, off)
        drawDetails(canvas, hours, off, clock, tracking, ctaTop, ctaHeight)
        canvas.restore()
    }

    private fun drawToggle(canvas: AndroidCanvas, off: Double) {
        val x = 262f; val y = 195f
        fill.color = if (off > .5) 0xFFA58FA1.toInt() else 0xFF865F7D.toInt(); inset.set(x, y, x + 90, y + 42); canvas.drawRoundRect(inset, 21f, 21f, fill)
        label(canvas, if (off > .5) "Off" else "On", if (off > .5) 304f else 276f, 221f, 13f, 0xFFF3E6F0.toInt(), medium)
        fill.color = 0xFFFCF8FB.toInt(); canvas.drawCircle(if (off > .5) 277f else 333f, 216f, 15f, fill)
    }

    private fun drawNumeral(canvas: AndroidCanvas, value: Int, off: Double) {
        val chars = value.coerceIn(4, 12).toString(); val pitch = 12.04f; val dot = 4.62f
        val start = if (chars.length == 1) 177f else 137f
        chars.forEachIndexed { index, char ->
            val ox = start + index * 70f
            digitDots(char).forEach { (cx, cy) ->
                if (off > .5) {
                    line.color = 0xC756314F.toInt(); line.strokeWidth = 1.35f
                    canvas.drawCircle(ox + cx * pitch, 359.3f + cy * pitch, dot, line)
                } else {
                    fill.color = 0xFFE9C6DF.toInt()
                    canvas.drawCircle(ox + cx * pitch, 359.3f + cy * pitch, dot, fill)
                }
            }
        }
    }

    private fun drawRuler(canvas: AndroidCanvas, hours: Double, off: Double) {
        val fractional = hours - kotlin.math.floor(hours)
        val shift = (hours - 8.0) * 72.0
        line.strokeWidth = 2f
        for (i in -120..120) {
            val x = 201 + i * 6 - shift
            if (x !in 77.0..328.0) continue
            val long = i % 4 == 0
            line.color = if (i in 1..(fractional * 4.0).roundToInt()) 0xFFF2A6CC.toInt() else 0x99F3C3DA.toInt()
            canvas.drawLine(x.toFloat(), 535f, x.toFloat(), if (long) 509f else 519f, line)
        }
        line.color = 0xFFF4E84A.toInt(); line.strokeWidth = 2.5f; canvas.drawLine(201f, 505f, 201f, 539f, line)
        if (off > .5) { line.color = 0x555A1D45; canvas.drawLine(77f, 535f, 328f, 535f, line) }
    }

    private fun drawDetails(canvas: AndroidCanvas, hours: Double, off: Double, clock: Double, tracking: Boolean, ctaTop: Double, ctaHeight: Double) {
        for (i in 0..3) { fill.color = if (i == 0) 0xFFFFFFFF.toInt() else 0x99F1DCEB.toInt(); canvas.drawCircle(187f + i * 9f, 605.5f, if (i == 0) 2.5f else 1.5f, fill) }
        label(canvas, "tue", 53f, 666f, 15f, 0xFF56314F.toInt(), medium)
        label(canvas, "25", 50f, 713f, 22f, 0xFF56314F.toInt(), doto)
        fill.color = 0xFFFCF8FB.toInt(); canvas.drawCircle(135f, 665f, 15f, fill); fill.color = 0xFFE0338A.toInt(); canvas.drawCircle(135f, 703f, 15f, fill)
        val bedtime = (360 - (hours * 4).roundToInt() * 15 + 1440) % 1440
        val hour = ((bedtime / 60 + 11) % 12) + 1
        label(canvas, hour.toString(), 166f, 668f, 16f, 0xFF56314F.toInt(), medium)
        label(canvas, (bedtime % 60).toString().padStart(2, '0'), 166f, 706f, 16f, 0xFF56314F.toInt(), medium)
        drawKnob(canvas, hours, off, clock)
        val ctaY = (ctaTop + ctaHeight / 2).toFloat()
        inset.set(108f, ctaTop.toFloat(), 308f, (ctaTop + ctaHeight).toFloat())
        fill.color = if (tracking) 0xFFB6236A.toInt() else 0xFF80617B.toInt(); canvas.drawRoundRect(inset, (ctaHeight/2).toFloat(), (ctaHeight/2).toFloat(), fill)
        label(canvas, if (tracking) "Tracking" else "Start tracking", 140f, ctaY + 6f, 14f, 0xFFFDF8FC.toInt(), medium)
        fill.color = 0xFFFCF8FB.toInt(); canvas.drawCircle(286f, ctaY, 17f, fill)
        line.color = 0xFF3A2436.toInt(); line.strokeWidth = 2.5f
        if (tracking) {
            canvas.drawLine(280f, ctaY, 284f, ctaY + 4f, line); canvas.drawLine(284f, ctaY + 4f, 292f, ctaY - 5f, line)
        } else {
            canvas.drawLine(280f, ctaY, 292f, ctaY, line)
            canvas.drawLine(286f, ctaY - 6f, 286f, ctaY + 6f, line)
        }
    }

    private fun drawKnob(canvas: AndroidCanvas, hours: Double, off: Double, clock: Double) {
        val cx = 306f; val cy = 680f; val theta = (hours - 8.0) * 30.0 * PI / 180.0
        fill.color = AndroidColor.argb((76 + off * 110).roundToInt(), 90, 29, 69); canvas.drawCircle(cx, cy, (38 + off * 12).toFloat(), fill)
        line.strokeWidth = 2f
        for (i in 0 until 36) {
            val a = i * PI * 2 / 36 + theta; val r0 = 50.0; val r1 = if (i % 3 == 0) 63.0 else 58.0
            line.color = if (off > .4 && i < 14) 0xFFF2A6CC.toInt() else 0xFFF7E9F3.toInt()
            canvas.drawLine((cx + cos(a) * r0).toFloat(), (cy + sin(a) * r0).toFloat(), (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(), line)
        }
        fill.color = 0xFFFCF8FB.toInt(); canvas.drawCircle(cx, cy, 32f, fill)
        line.color = 0xFFE4DDE3.toInt(); line.strokeWidth = 1.5f; canvas.drawCircle(cx, cy, 30.5f, line)
        line.color = if (off > .5) 0xFFE0338A.toInt() else 0xFFC12F6E.toInt(); line.strokeWidth = 3.5f
        canvas.drawLine(cx, cy, (cx + cos(theta - PI / 2) * 22).toFloat(), (cy + sin(theta - PI / 2) * 22).toFloat(), line)
    }

    private fun label(canvas: AndroidCanvas, value: String, x: Float, baseline: Float, size: Float, color: Int, face: Typeface) { text.typeface = face; text.textSize = size; text.color = color; text.textAlign = Paint.Align.LEFT; canvas.drawText(value, x, baseline, text) }
    private fun labelCentered(canvas: AndroidCanvas, value: String, x: Float, baseline: Float, size: Float, color: Int, face: Typeface) { text.typeface = face; text.textSize = size; text.color = color; text.textAlign = Paint.Align.CENTER; canvas.drawText(value, x, baseline, text) }

    private fun digitDots(value: Char): List<Pair<Float, Float>> {
        val segments = mapOf('0' to "abcedf", '1' to "o", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd", '6' to "afgedc", '7' to "abch", '8' to "abcdefg", '9' to "abcdfg")
        val p = mapOf('a' to listOf(0.5f to 0f,1.5f to 0f,2.5f to 0f,3.5f to 0f),'b' to listOf(4f to 1f,4f to 2f),'c' to listOf(4f to 4f,4f to 5f),'d' to listOf(0.5f to 6f,1.5f to 6f,2.5f to 6f,3.5f to 6f),'e' to listOf(0f to 4f,0f to 5f),'f' to listOf(0f to 1f,0f to 2f),'g' to listOf(0.5f to 3f,1.5f to 3f,2.5f to 3f,3.5f to 3f),'h' to listOf(4f to 3f),'o' to listOf(2f to 0f,2f to 1f,2f to 2f,2f to 3f,2f to 4f,2f to 5f,2f to 6f,1f to 1f,1f to 6f,3f to 6f))
        return segments[value].orEmpty().flatMap { p[it].orEmpty() }
    }

    companion object {
        fun load(context: Context): GlowDialReminderArt {
            val assets = context.assets
            fun font(name: String) = Typeface.createFromAsset(assets, "glowdialreminder/glowdialreminder_$name.ttf")
            val grain = assets.open("glowdialreminder/glowdialreminder_gd_grain.png").use { BitmapFactory.decodeStream(it)!! }
            return GlowDialReminderArt(font("Urbanist_Regular"), font("Urbanist_Medium"), font("Doto_Bold"), grain)
        }
    }
}

private fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k
private fun easeOut(k: Double) = 1.0 - (1.0 - k) * (1.0 - k) * (1.0 - k)

@Preview
@Composable
private fun GlowDialReminderDemoPreview() { GlowDialReminderDemo() }
