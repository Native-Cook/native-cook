package com.ngocdevv.nativecook.blocks.media.tornphotodelete

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Torn Photo Delete — a day's instant photo you delete by tearing it in two. A port of the SwiftUI block
 (TornPhotoDelete.swift); the numbers are the same.

 Look: a pale page (#EFEFF1, a soft pink glow behind the camera button at the lower right). Header: a trash
 glyph at the left, "Selected Day" over "Thursday, 2 July" in the middle. The print: a cream card
 (#F7EEDD → #EFE3CC, corner 3 dp, soft shadow) 85 % of the width (at most 340 dp), a 5:6 photo inset
 16 dp with a faint glare, and "02/07/2026" in a 13 sp mono in its 72 dp bottom margin; centred at 47 %
 of the height. A hint under it, "Drag across the print to tear it". When the print is gone, a dark toast
 "Photo deleted · Undo" rises above the camera button and "Nothing kept for this day" shows where it was.

 The tear (geometry in TornPhotoDeleteModel.kt): a jagged cut runs from the edge the finger started on
 towards the finger; the two flaps shear apart into a wedge, showing the white paper core along the cut.
 Released under 70 % of the way across, the flaps close (critically damped, ω = 16 /s); past it, the cut
 finishes and the halves fall away, spinning outwards.

 autoPlay: LOOP = 9 s, timeline in TornPhotoDeleteModel.kt. A small grey disc shows the scripted finger.
 Touch: drag from anywhere on the print (the cut starts on the nearest edge); a tick every 28 dp of tearing,
 a heavier one when it gives. Undo on the toast brings the print back. 2 s after the last touch the print
 comes back if it was torn and the loop starts again.
 Reduced motion (animations off): no loop. The cut still follows the finger; a finished tear fades the
 print out instead of dropping it, and it fades back in.
 */

private const val PHOTO_ASSET = "tornphotodelete/tornphotodelete_road.jpg"

private fun nowSeconds() = System.nanoTime() / 1e9

private enum class TornGesture { TEAR, UNDO, NONE }

/** Sizes in dp, the same as the Swift Layout. */
private class TornPhotoDeleteLayout(val width: Double, val height: Double, val top: Double, bottomInset: Double) {
    val cardW = min(width * 0.85, 340.0)
    val photoH = (cardW - 32) * 1.2
    val cardH = 16 + photoH + 72
    val cardX = (width - cardW) / 2
    val cardY = height * 0.47 - cardH / 2
    val bottom = max(bottomInset, 20.0)
    val toastX = (width - 264) / 2
    val toastY = height - bottom - 128
    val undoX = toastX + 264 - 84
    val fabX = width - 24 - 28
    val fabY = height - bottom - 44

    fun inUndo(x: Double, y: Double) = x >= undoX - 8 && x <= toastX + 264 + 8 && y >= toastY - 8 && y <= toastY + 48 + 8
}

private class TornPhotoDeleteController(val autoPlay: Boolean, val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart by mutableStateOf<Double?>(null)
    var quietUntil by mutableStateOf<Double?>(null)
    var once by mutableStateOf(false)
    var tear by mutableStateOf<TornPhotoDeleteTear?>(null)
    var span = 0.0
    var edgeX = 0.0
    var edgeY = 0.0
    var hasEdge = false
    var healing by mutableStateOf<Pair<TornPhotoDeleteTear, Double>?>(null)
    var committed by mutableStateOf<Pair<TornPhotoDeleteTear, Double>?>(null)
    var gesture: TornGesture? = null
    var ticks = 0
    var active by mutableStateOf(false)
    var resume: Job? = null

    val running get() = (autoStart != null && !reduced) || active

    fun scene(at: Double, l: TornPhotoDeleteLayout): TornPhotoDeleteScene {
        val E = TornPhotoDeleteEase
        val start = autoStart
        if (start != null && at >= start) {
            var t = at - start
            if (once) t = min(t, TornPhotoDeleteTimeline.LOOP)
            val e = t % TornPhotoDeleteTimeline.LOOP
            val quiet = quietUntil?.let { at < it } ?: false
            val s = TornPhotoDeleteTimeline.scene(
                if (once && t >= TornPhotoDeleteTimeline.LOOP) TornPhotoDeleteTimeline.LOOP else e,
                l.cardX, l.cardY, l.cardW, l.cardH, l.undoX + 42, l.toastY + 24, quiet,
            )
            if (reduced) { s.cardScale = 1.0; s.cardLift = 0.0 }
            return s
        }
        val s = TornPhotoDeleteScene()
        val c = committed
        if (c != null) {
            val f = at - c.second
            var tr = c.first
            if (reduced) {
                s.cardAlpha = 1 - E.smoothstep(0.0, 0.3, f)
                tr = tr.copy(fall = null)
            } else {
                tr = tr.copy(fall = f)
                if (f >= 1.2) s.cardAlpha = 0.0
            }
            if (s.cardAlpha > 0) s.tear = tr
            s.toast = if (reduced) E.smoothstep(0.35, 0.65, f) else E.outBack((f - 0.35) / 0.4)
            s.empty = E.smoothstep(0.55, 0.95, f)
            s.hint = 1 - E.smoothstep(0.0, 0.3, f)
            return s
        }
        val t = tear
        val h = healing
        if (t != null && t.length > 0) {
            s.tear = t
        } else if (h != null) {
            val open = if (reduced) 0.0 else E.heal(h.first.open, at - h.second)
            if (open > 0) s.tear = h.first.copy(open = open)
        }
        return s
    }
}

@Composable
fun TornPhotoDelete(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val photo = remember { context.assets.open(PHOTO_ASSET).use { BitmapFactory.decodeStream(it) } }
    val c = remember(reduced) { TornPhotoDeleteController(autoPlay, reduced) }
    val scope = rememberCoroutineScope()
    val painter = remember { TornPhotoDeletePainter(photo) }
    TornPhotoDeleteSystemBars()

    LaunchedEffect(c) { if (autoPlay && !reduced) c.autoStart = nowSeconds() }
    LaunchedEffect(c, c.running) {
        while (c.running) withFrameNanos { c.frame = it / 1e9 }
    }

    fun tick(heavy: Boolean) {
        val k = when {
            !heavy -> HapticFeedbackConstants.CLOCK_TICK
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(k)
    }

    fun scheduleIdle(ms: Long) {
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(ms)
            c.active = false
        }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(2000)
            val now = nowSeconds()
            if (c.committed != null) {
                c.committed = null
                c.autoStart = now - TornPhotoDeleteTimeline.TOAST_OUT
                c.quietUntil = now + TornPhotoDeleteTimeline.LOOP
                c.once = !autoPlay || reduced
            } else if (autoPlay && !reduced) {
                c.tear = null
                c.healing = null
                c.autoStart = now
            }
            delay(2000)
            c.active = false
        }
    }

    fun commit(now: Double) {
        val t = c.tear ?: return
        if (c.gesture != TornGesture.TEAR) return
        c.gesture = TornGesture.NONE
        val len = c.span * 1.06
        c.committed = t.copy(length = len, open = TornPhotoDeleteTear.opening(len)) to now
        c.tear = null
        c.healing = null
        tick(true)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val l = remember(maxWidth, maxHeight, top, bottomInset) {
            TornPhotoDeleteLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottomInset)
        }
        val grey = Color(0xFF8C8C94)

        // The page: ground, glow, handle, camera button.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFFEFEFF1))
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)),
                center = Offset(w / 2, h * 0.42f), radius = w * 0.8f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFFF8CA8).copy(alpha = 0.42f), Color(0xFFFF8CA8).copy(alpha = 0f)),
                center = Offset((l.fabX * density).toFloat(), (l.fabY * density).toFloat()), radius = 120 * density))
            val hy = ((l.height - l.bottom - 14) * density).toFloat()
            val chevron = androidx.compose.ui.graphics.Path().apply {
                moveTo(w / 2 - 9 * density, hy - 3 * density)
                lineTo(w / 2, hy + 2 * density)
                lineTo(w / 2 + 9 * density, hy - 3 * density)
            }
            drawPath(chevron, Color(0xFFB3B3BA), style = Stroke(2 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Box(
            Modifier
                .offset(x = (l.fabX - 28).dp, y = (l.fabY - 28).dp)
                .size(56.dp)
                .shadow(12.dp, CircleShape, ambientColor = Color(0xFFFF5C82), spotColor = Color(0xFFFF5C82))
                .background(Brush.verticalGradient(listOf(Color(0xFFFF8FA8), Color(0xFFFF5C82))), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TornGlyph(TornGlyphKind.CAMERA, Color.White, 1.8f, Modifier.size(24.dp))
        }

        // Header.
        Box(Modifier.fillMaxWidth().height(44.dp).offset(y = (l.top + 6).dp)) {
            TornGlyph(TornGlyphKind.TRASH, grey, 1.6f, Modifier.padding(start = 24.dp).size(22.dp).align(Alignment.CenterStart))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("Selected Day", style = TextStyle(color = Color(0xFF54545C), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                BasicText("Thursday, 2 July", style = TextStyle(color = grey, fontSize = 12.sp))
            }
        }

        // Empty note, where the print was.
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (l.cardY + l.cardH / 2 - 50).dp)
                .graphicsLayer { alpha = c.scene(c.frame, l).empty.toFloat() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TornGlyph(TornGlyphKind.PHOTO, Color(0xFFB3B3BA), 1.6f, Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            BasicText("Nothing kept for this day", style = TextStyle(color = Color(0xFF73737A), fontSize = 15.sp, fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(10.dp))
            BasicText("Take today's photo with the camera.", style = TextStyle(color = Color(0xFF999AA1), fontSize = 13.sp))
        }

        // Hint.
        BasicText(
            "Drag across the print to tear it",
            Modifier
                .fillMaxWidth()
                .offset(y = (l.cardY + l.cardH + 22).dp)
                .graphicsLayer { alpha = c.scene(c.frame, l).hint.toFloat().coerceIn(0f, 1f) },
            style = TextStyle(color = Color(0xFF8F8F96), fontSize = 13.sp, textAlign = TextAlign.Center),
        )

        // The print, and the touch.
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Photo of 2 July 2026: a road through red rocks" }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val sx = down.position.x / density
                        val sy = down.position.y / density
                        val now0 = nowSeconds()
                        c.resume?.cancel()
                        c.active = true
                        val current = c.scene(now0, l)
                        c.gesture = TornGesture.NONE
                        if (current.toast > 0.5 && l.inUndo(sx.toDouble(), sy.toDouble())) {
                            c.gesture = TornGesture.UNDO
                        } else {
                            val resting = current.cardAlpha >= 1 && current.cardScale >= 0.999 && current.tear?.fall == null && c.committed == null
                            val inCard = sx >= l.cardX - 30 && sx <= l.cardX + l.cardW + 30 && sy >= l.cardY - 30 && sy <= l.cardY + l.cardH + 30
                            if (resting && inCard) {
                                c.gesture = TornGesture.TEAR
                                c.autoStart = null
                                c.quietUntil = null
                                c.once = false
                                c.healing = current.tear?.let { it to now0 }
                                c.tear = null
                                c.ticks = 0
                                // The nearest point on the card's edge.
                                val px = min(max(sx.toDouble(), l.cardX), l.cardX + l.cardW) - l.cardX
                                val py = min(max(sy.toDouble(), l.cardY), l.cardY + l.cardH) - l.cardY
                                val d = listOf(px, l.cardW - px, py, l.cardH - py)
                                when (d.indexOf(d.min())) {
                                    0 -> { c.edgeX = 0.0; c.edgeY = py }
                                    1 -> { c.edgeX = l.cardW; c.edgeY = py }
                                    2 -> { c.edgeX = px; c.edgeY = 0.0 }
                                    else -> { c.edgeX = px; c.edgeY = l.cardH }
                                }
                                c.hasEdge = true
                            }
                        }
                        var lastX = sx.toDouble()
                        var lastY = sy.toDouble()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            lastX = change.position.x / density.toDouble()
                            lastY = change.position.y / density.toDouble()
                            if (c.gesture != TornGesture.TEAR || !c.hasEdge) continue
                            change.consume()
                            val px = lastX - l.cardX
                            val py = lastY - l.cardY
                            var t = c.tear
                            if (t == null) {
                                // Wait until the finger has moved into the card before choosing the direction.
                                val dx = px - c.edgeX
                                val dy = py - c.edgeY
                                if (hypot(dx, dy) <= 10) continue
                                val angle = Math.toDegrees(atan2(dy, dx))
                                val s = TornPhotoDeleteTear.span(c.edgeX, c.edgeY, angle, l.cardW, l.cardH)
                                if (s <= 40) continue
                                c.span = s
                                t = TornPhotoDeleteTear(c.edgeX, c.edgeY, angle, 0.0, 0.0, 101 + ((System.currentTimeMillis() / 1000) % 97).toInt())
                            }
                            val len = min(c.span * 1.06, max(0.0, (px - c.edgeX) * t.ux + (py - c.edgeY) * t.uy))
                            val k = (len / 28).toInt()
                            if (k > c.ticks) tick(false)
                            c.ticks = k
                            c.tear = t.copy(length = len, open = TornPhotoDeleteTear.opening(len))
                            if (len >= c.span) commit(nowSeconds())
                        }
                        val now = nowSeconds()
                        when (c.gesture) {
                            TornGesture.UNDO -> if (l.inUndo(lastX, lastY)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                c.committed = null
                                c.tear = null
                                c.healing = null
                                c.autoStart = now - TornPhotoDeleteTimeline.UNDO_PRESS
                                c.quietUntil = now + TornPhotoDeleteTimeline.LOOP
                                c.once = !autoPlay || reduced
                                scheduleIdle(2500)
                            } else {
                                scheduleResume()
                            }
                            TornGesture.TEAR -> {
                                val t = c.tear
                                if (t != null && t.length > 0) {
                                    if (t.length >= c.span * 0.7) {
                                        commit(now)
                                    } else {
                                        c.healing = t to now
                                        c.tear = null
                                    }
                                }
                                scheduleResume()
                            }
                            else -> scheduleResume()
                        }
                        c.gesture = null
                        c.hasEdge = false
                    }
                },
        ) {
            val s = c.scene(c.frame, l)
            scale(density, density, pivot = Offset.Zero) { painter.draw(this, s, l) }
        }

        // Toast.
        Row(
            Modifier
                .offset(x = l.toastX.dp, y = l.toastY.dp)
                .graphicsLayer {
                    val s = c.scene(c.frame, l)
                    translationY = if (reduced) 0f else ((1 - s.toast) * 90 * density).toFloat()
                    alpha = (s.toast * 1.5).toFloat().coerceIn(0f, 1f)
                }
                .size(264.dp, 48.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(CircleShape)
                .background(Color(0xFF1C1C1F)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(18.dp))
            TornGlyph(TornGlyphKind.TRASH, Color.White.copy(alpha = 0.7f), 1.5f, Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            BasicText("Photo deleted", style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 15.sp, fontWeight = FontWeight.Medium))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.14f)))
            Box(
                Modifier
                    .width(83.dp)
                    .fillMaxHeight()
                    .drawBehind { drawRect(Color.White.copy(alpha = (0.14 * c.scene(c.frame, l).undoPress).toFloat())) }
                    .graphicsLayer {
                        val p = c.scene(c.frame, l).undoPress.toFloat()
                        scaleX = 1 - 0.06f * p
                        scaleY = 1 - 0.06f * p
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Undo", style = TextStyle(color = Color(0xFFFF9EB3), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }

        // The scripted finger.
        Canvas(Modifier.fillMaxSize()) {
            val s = c.scene(c.frame, l)
            if (s.dotAlpha <= 0) return@Canvas
            val r = (20 * (1.25 - 0.25 * s.dotPress) * density).toFloat()
            val center = Offset((s.dotX * density).toFloat(), (s.dotY * density).toFloat())
            val a = s.dotAlpha.toFloat()
            drawCircle(Color(0xFF59595F).copy(alpha = 0.28f * a), r, center)
            drawCircle(Color.White.copy(alpha = 0.85f * a), r - 0.75f * density, center, style = Stroke(1.5f * density))
        }
    }
}

/** Draws the print (whole, or as torn pieces) in dp, through android.graphics. */
private class TornPhotoDeletePainter(val photo: Bitmap) {
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2E8D4.toInt() }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glare = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = android.graphics.Color.argb(31, 0, 0, 0)
    }
    private val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.rgb(41, 38, 36)
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.MONOSPACE, 500, false) else Typeface.MONOSPACE
    }
    private var shaderFor = -1.0

    fun draw(scope: DrawScope, s: TornPhotoDeleteScene, l: TornPhotoDeleteLayout) {
        if (s.cardAlpha <= 0) return
        val canvas = scope.drawContext.canvas.nativeCanvas
        val w = l.cardW.toFloat()
        val h = l.cardH.toFloat()
        if (shaderFor != l.cardW) {
            shaderFor = l.cardW
            paper.shader = LinearGradient(0f, 0f, w, h, 0xFFF7EEDD.toInt(), 0xFFEFE3CC.toInt(), Shader.TileMode.CLAMP)
            val p = RectF(16f, 16f, w - 16f, (16 + l.photoH).toFloat())
            glare.shader = LinearGradient(p.left, p.top, p.right, p.bottom,
                intArrayOf(0x00FFFFFF, 0x21FFFFFF, 0x00FFFFFF), floatArrayOf(0.25f, 0.42f, 0.6f), Shader.TileMode.CLAMP)
        }
        shadow.setShadowLayer(14f, 0f, 8f, android.graphics.Color.argb(41, 0, 0, 0))
        val alpha = (s.cardAlpha * 255).toInt()
        val save = canvas.saveLayerAlpha(null, alpha)
        canvas.translate((l.cardX + w / 2).toFloat(), (l.cardY + h / 2 - s.cardLift).toFloat())
        canvas.scale(s.cardScale.toFloat(), s.cardScale.toFloat())
        canvas.translate(-w / 2, -h / 2)

        val card = RectF(0f, 0f, w, h)
        val round = Path().apply { addRoundRect(card, 3f, 3f, Path.Direction.CW) }
        // The shadow is cast by the piece's own outline (region ∩ card), not cut off by the region.
        fun shadowOf(m: android.graphics.Matrix?, clip: Path?) {
            val shape = if (clip != null) Path().apply { op(clip, round, Path.Op.INTERSECT) } else round
            canvas.save()
            if (m != null) canvas.concat(m)
            canvas.drawPath(shape, shadow)
            canvas.restore()
        }
        fun piece(m: android.graphics.Matrix?, clip: Path?, corePath: Path?, coreAlpha: Double) {
            canvas.save()
            if (m != null) canvas.concat(m)
            if (clip != null) canvas.clipPath(clip)
            drawCard(canvas, l)
            if (corePath != null) {
                canvas.clipPath(round)
                core.color = android.graphics.Color.argb((coreAlpha * 255).toInt(), 254, 251, 246)
                canvas.drawPath(corePath, core)
            }
            canvas.restore()
        }

        val t = s.tear
        if (t == null || t.length <= 0) {
            shadowOf(null, null)
            piece(null, null, null, 1.0)
        } else {
            val cut = t.cut()
            val coreAlpha = if (t.fall != null) 1.0 else TornPhotoDeleteEase.smoothstep(0.0, 0.02, t.open)
            val sides = doubleArrayOf(1.0, -1.0)
            // Shadows of every piece first, then the pieces, so no piece's shadow falls on its neighbour.
            if (t.fall == null) shadowOf(null, t.rest())
            for (side in sides) shadowOf(t.transform(side, l.cardW, l.cardH), t.region(side, cut))
            if (t.fall == null) piece(null, t.rest(), null, 1.0)
            for (side in sides) {
                piece(t.transform(side, l.cardW, l.cardH), t.region(side, cut), if (coreAlpha > 0.01) t.core(side, cut) else null, coreAlpha)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawCard(canvas: android.graphics.Canvas, l: TornPhotoDeleteLayout) {
        val w = l.cardW.toFloat()
        val h = l.cardH.toFloat()
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 3f, 3f, paper)
        // The photo, filled into its 5:6 window.
        val p = RectF(16f, 16f, w - 16f, (16 + l.photoH).toFloat())
        canvas.save()
        canvas.clipRect(p)
        val scale = max(p.width() / photo.width, p.height() / photo.height)
        val iw = photo.width * scale
        val ih = photo.height * scale
        canvas.drawBitmap(photo, null, RectF(p.centerX() - iw / 2, p.centerY() - ih / 2, p.centerX() + iw / 2, p.centerY() + ih / 2), image)
        canvas.drawRect(p, glare)
        canvas.restore()
        canvas.drawRect(RectF(p.left + 0.25f, p.top + 0.25f, p.right - 0.25f, p.bottom - 0.25f), line)
        val fm = date.fontMetrics
        canvas.drawText("02/07/2026", w / 2, p.bottom + 36 - (fm.ascent + fm.descent) / 2, date)
    }
}

private enum class TornGlyphKind { TRASH, CAMERA, PHOTO }

/** Line glyphs on a 24 × 24 grid, scaled to the size. */
@Composable
private fun TornGlyph(kind: TornGlyphKind, color: Color, width: Float, modifier: Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 24
        val path = androidx.compose.ui.graphics.Path()
        fun m(x: Float, y: Float) = path.moveTo(x * k, y * k)
        fun ln(x: Float, y: Float) = path.lineTo(x * k, y * k)
        when (kind) {
            TornGlyphKind.TRASH -> {
                m(4f, 6.5f); ln(20f, 6.5f)
                m(9.5f, 6.5f); ln(9.5f, 4f); ln(14.5f, 4f); ln(14.5f, 6.5f)
                m(6f, 6.5f); ln(7f, 20f); ln(17f, 20f); ln(18f, 6.5f)
                m(10.2f, 10f); ln(10.5f, 16.5f)
                m(13.8f, 10f); ln(13.5f, 16.5f)
            }
            TornGlyphKind.CAMERA -> {
                m(3f, 8.5f); ln(3f, 18.5f); ln(21f, 18.5f); ln(21f, 8.5f)
                ln(16.5f, 8.5f); ln(15f, 5.5f); ln(9f, 5.5f); ln(7.5f, 8.5f); path.close()
                path.addOval(androidx.compose.ui.geometry.Rect(8.5f * k, 9.5f * k, 15.5f * k, 16.5f * k))
            }
            TornGlyphKind.PHOTO -> {
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(3f * k, 4f * k, 21f * k, 20f * k, 2.5f * k, 2.5f * k))
                m(3.5f, 17f); ln(9f, 11.5f); ln(13.5f, 16f); ln(16f, 13.5f); ln(20.5f, 18f)
                path.addOval(androidx.compose.ui.geometry.Rect(14f * k, 7f * k, 17f * k, 10f * k))
            }
        }
        drawPath(path, color, style = Stroke(width * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The page is light, so the system bars get dark icons while the block is open. */
@Composable
private fun TornPhotoDeleteSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
