package com.ngocdevv.nativecook.blocks.cards.foldedmapcard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Folded Map Card — "Ridge Line Trail" in a hiking app: the offline map is a paper map folded in
 thirds. It unfolds in 3D (each panel turning on its crease, shaded by its angle), then the route
 draws itself on, the summit pin drops and the elevation profile rises. The loop and the fold
 geometry are in FoldedMapCardMotion.kt (LOOP = 8 s).

 Touch: drag sideways on the map to fold or unfold it continuously (two panel widths of drag =
 fully open, rubber-banded past both ends); released, panel 1 springs to the nearer state with
 the finger's velocity (ω 11, ζ 0.72) and panel 2 follows on a softer spring (ω 22, ζ 0.8). A tap
 folds or unfolds it. Light haptic when the map lands open or shut. 2 s after the last touch it
 refolds in 0.8 s and the loop restarts. Reduced motion: no loop; the map unfolds without
 overshoot; everything else fades.
*/

private const val PAD = 20f
private val Accent = Color(0xFFE0643F)
private val Ink = Color(0xFFEEF1E8)
private val InkSoft = Color(226 / 255f, 234 / 255f, 222 / 255f, 0.62f)
private val Kicker = Color(0xFFA9C79A)
internal val FoldedMapBackground = Color(0xFF13221A)

/** Every value the frame loop and the gestures share; [frame] is the one state the drawing reads. */
private class FoldedMapState {
    var clock = 0f
    var mode = 0 // 0 loop, 1 dragging, 2 released, 3 returning
    var u1 = 0f
    var u2 = 0f
    var u1V = 0f
    var u2V = 0f
    var goal = 0f
    var route = 0f
    var pinP = 0f
    var rise = 0f
    var shown = 0f // seconds since the map lay open, while touched
    var idle = 0f
    var ret = 0f
    var fromU = 0f
    var startU = 0f
    var dragX = 0f
    val frame = mutableFloatStateOf(0f)
    val wake = mutableIntStateOf(0)
    val script = FloatArray(5)

    fun takeOver() {
        if (mode == 0) {
            u1V = 0f
            u2V = 0f
            shown = if (route > 0.99f) 1.2f else route
        }
        idle = 0f
    }
}

private fun rubber(v: Float): Float = when {
    v < 0f -> v / (1 - v * 4) * 0.4f
    v > 1f -> 1 + (v - 1) / (1 + (v - 1) * 4) * 0.4f
    else -> v
}

/** The per-frame drawing tools, made once per layout. */
private class FoldedMapPainter(private val art: FoldedMapArt, private val pin: Bitmap) {
    val frames = FloatArray(9)
    val profile: FloatArray get() = art.profile
    private val matrix = Matrix()
    private val src = FloatArray(8)
    private val dst = FloatArray(8)
    private val quad = android.graphics.Path()
    private val shadow = android.graphics.Path()
    private val routePath = android.graphics.Path()
    private val pt = FloatArray(2)
    private val mapShader = BitmapShader(art.image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = mapShader }
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(128, 2, 10, 6)
        maskFilter = BlurMaskFilter(sigmaToRadius(12f), BlurMaskFilter.Blur.NORMAL)
    }
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(115, 2, 10, 6)
        maskFilter = BlurMaskFilter(sigmaToRadius(2.5f), BlurMaskFilter.Blur.NORMAL)
    }
    private val pinShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(sigmaToRadius(1.5f), BlurMaskFilter.Blur.NORMAL)
    }
    private val routeUnder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.argb(230, 250, 244, 228)
    }
    private val routeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.rgb(0xE0, 0x64, 0x3F)
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pinRect = RectF()
    private val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun draw(c: android.graphics.Canvas, card: FoldedMapCardGeometry, MW: Float, u1: Float, u2: Float, route: Float, pinP: Float, reduced: Boolean) {
        foldedMapPanels(card, u1, u2, frames)
        val pw = card.pw
        val f1 = FOLDED_MAP_FOLD_MAX * (1 - u1)
        val f2 = FOLDED_MAP_FOLD_MAX * (1 - u2)

        // The map's shadow on the table: each panel's footprint, pushed out by its height.
        if (blurOk) {
            shadow.reset()
            var maxX = pw
            for (k in 0 until 3) {
                val ox = frames[k * 3]
                val oz = frames[k * 3 + 1]
                val psi = frames[k * 3 + 2]
                val x1 = ox + pw * cos(psi)
                val z1 = oz - pw * sin(psi)
                val a = min(ox, x1)
                val b = max(ox, x1)
                val lift = max(-oz, -z1)
                val l = card.x + a + 3 + lift * 0.08f
                val t = card.y + 6 + lift * 0.12f
                shadow.addRect(l, t, l + max(2f, b - a), t + card.h, android.graphics.Path.Direction.CW)
                maxX = max(maxX, x1)
            }
            c.drawPath(shadow, shadowPaint)
            c.drawRoundRect(card.x, card.y + 1.5f, card.x + maxX, card.y + 1.5f + card.h, 1f, 1f, contactPaint)
        }

        val cx = card.w / 2
        val cy = card.h / 2
        val s = art.scale
        for (k in 0 until 3) {
            val ox = frames[k * 3]
            val oz = frames[k * 3 + 1]
            val psi = frames[k * 3 + 2]
            val front = cos(psi) > 0f
            val base = foldedMapShade(psi, front)
            val leftFold = if (k == 1) f1 else if (k == 2) f2 else 0f
            val rightFold = if (k == 0) f1 else if (k == 1) f2 else 0f
            // Corners: (0, 0), (pw, 0), (pw, h), (0, h) of the panel.
            for (corner in 0 until 4) {
                val lx = if (corner == 1 || corner == 2) pw else 0f
                val my = if (corner >= 2) card.h else 0f
                val X = ox + lx * cos(psi)
                val Z = oz - lx * sin(psi)
                val sc = FOLDED_MAP_PERSPECTIVE / (FOLDED_MAP_PERSPECTIVE + Z)
                dst[corner * 2] = card.x + cx + (X - cx) * sc
                dst[corner * 2 + 1] = card.y + cy + (my - cy) * sc
                val tx = if (front) k * pw + lx else MW + pw - lx
                src[corner * 2] = tx * s
                src[corner * 2 + 1] = my * s
            }
            quad.reset()
            quad.moveTo(dst[0], dst[1])
            quad.lineTo(dst[2], dst[3])
            quad.lineTo(dst[4], dst[5])
            quad.lineTo(dst[6], dst[7])
            quad.close()
            if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                mapShader.setLocalMatrix(matrix)
                c.drawPath(quad, mapPaint)
            }
            // The shading across the panel: its face's brightness, darker toward a closing crease.
            val cl = foldedMapCrease(leftFold)
            val cr = foldedMapCrease(rightFold)
            val fromX = (dst[0] + dst[6]) / 2
            val fromY = (dst[1] + dst[7]) / 2
            var toX = (dst[2] + dst[4]) / 2
            var toY = (dst[3] + dst[5]) / 2
            // A panel seen edge-on has both ends together; keep the gradient defined.
            if (abs(toX - fromX) < 0.5f) {
                toX = fromX + 0.5f
                toY = fromY
            }
            tintPaint.color = android.graphics.Color.BLACK
            tintPaint.shader = LinearGradient(
                fromX, fromY, toX, toY,
                intArrayOf(
                    foldedMapTint(base * (1 - cl)),
                    foldedMapTint(base * (1 - cl * 0.25f)),
                    foldedMapTint(base * (1 - cr * 0.25f)),
                    foldedMapTint(base * (1 - cr)),
                ),
                floatArrayOf(0f, 0.12f, 0.88f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawPath(quad, tintPaint)
        }

        // The route through the folds, drawn up to its progress.
        if (route > 0.001f) {
            val pts = art.route
            val n = pts.size / 2
            val upto = floor(route * (n - 1)).toInt()
            routePath.reset()
            for (i in 0..upto) {
                foldedMapProject(card, frames, pts[i * 2], pts[i * 2 + 1], pt, 0)
                if (i == 0) routePath.moveTo(pt[0], pt[1]) else routePath.lineTo(pt[0], pt[1])
            }
            val op = fmClamp(route * 6, 0f, 1f)
            val layer = op < 0.999f
            if (layer) c.saveLayerAlpha(card.x - 10, card.y - 10, card.x + card.w + 10, card.y + card.h + 10, (op * 255).roundToInt())
            c.drawPath(routePath, routeUnder)
            c.drawPath(routePath, routeLine)
            if (layer) c.restore()
        }

        // The summit pin.
        if (pinP > 0f) {
            val pinOpacity = if (reduced) pinP else fmClamp(pinP * 5, 0f, 1f)
            val sx = card.x + art.route[art.route.size - 2]
            val sy = card.y + art.route[art.route.size - 1]
            val d = foldedMapPinDrop(pinP)
            if (blurOk) {
                val shadowOpacity = 1 - max(0f, d) / 30
                pinShadowPaint.color = android.graphics.Color.argb((0.35f * shadowOpacity * pinOpacity * 255).roundToInt().coerceIn(0, 255), 40, 20, 10)
                c.drawRoundRect(sx - 6, sy - 2, sx + 6, sy + 2, 2f, 2f, pinShadowPaint)
            }
            val sc = if (d < 0f) 0.6f else 1f
            val ty = sy - max(0f, d)
            pinRect.set(sx + FOLDED_MAP_PIN_LEFT * sc, ty + FOLDED_MAP_PIN_TOP * sc, sx + (FOLDED_MAP_PIN_LEFT + 40) * sc, ty + (FOLDED_MAP_PIN_TOP + 46) * sc)
            pinPaint.alpha = (pinOpacity * 255).roundToInt().coerceIn(0, 255)
            c.drawBitmap(pin, null, pinRect, pinPaint)
        }
    }
}

/**
 * The offline paper map of a trail, folded in thirds; it unfolds in 3D, then the route draws on,
 * the summit pin drops and the elevation profile rises.
 *
 * @param autoPlay plays the 8 s demo: unfold, draw the route, drop the pin, refold.
 */
@Composable
fun FoldedMapCard(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { FoldedMapState() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = safeTop + 16
        val MW = width - PAD * 2
        val pw = MW / 3
        val MH = fmClamp(height * 0.36f, 196f, 300f).roundToInt().toFloat()
        val mapY = top + 132
        val profY = mapY + MH + 28
        // The profile card takes the room down to the button.
        val ctaTop = height - safeBottom - 16 - 56
        val profileH = fmClamp(ctaTop - 20 - profY - 50, 56f, 120f).roundToInt().toFloat()
        val card = remember(mapY, MW, MH, pw) { FoldedMapCardGeometry(PAD, mapY, MW, MH, pw) }

        val scale = min(3f, screen.density)
        val painter by produceState<FoldedMapPainter?>(null, MW, MH, scale) {
            value = withContext(Dispatchers.Default) {
                FoldedMapPainter(buildFoldedMap(MW, MH, pw, scale), buildFoldedMapPin(scale))
            }
        }
        val profileW = MW - 32

        LaunchedEffect(auto) {
            state.clock = 0f
            state.mode = 0
        }

        val wake = state.wake.intValue
        LaunchedEffect(auto, reduced, wake) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60 else min((now - last) / 1e9f, 1f / 20)
                    last = now
                    step(state, dt, auto, reduced) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                    state.frame.floatValue += dt
                }
                if (!auto && state.mode == 0) break
            }
        }

        // The backdrop never changes: drawn once into the layer, outside the per-frame canvas.
        FoldedMapBackdrop(Modifier.fillMaxSize())

        val barBrush = remember(profY, profileH) {
            Brush.verticalGradient(
                0f to Color(0xFFF08A5D), 0.5f to Accent, 1f to Color(0xFF9C3B26),
                startY = profY + 30, endY = profY + 30 + profileH,
            )
        }
        val art = painter
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            state.frame.floatValue
            val d = density
            scale(d, d, pivot = Offset.Zero) {
                // Profile card.
                drawRoundRect(Color(232 / 255f, 240 / 255f, 226 / 255f, 0.05f), Offset(PAD, profY), Size(MW, profileH + 50), CornerRadius(20f))
                val r = state.rise
                if (art != null && r > 0f) {
                    val bw = profileW / FOLDED_MAP_PROFILE_N
                    val opacity = if (reduced) r else 1f
                    val prof = art.profile
                    for (i in 0 until FOLDED_MAP_PROFILE_N) {
                        val k = if (reduced) 1f else fmEaseOut(fmClamp(r * 1.6f - i.toFloat() / FOLDED_MAP_PROFILE_N * 0.6f, 0f, 1f))
                        val h = (8 + prof[i] * (profileH - 8)) * k
                        if (h < 0.5f) continue
                        drawRoundRect(
                            barBrush,
                            Offset(PAD + 16 + i * bw + 1, profY + 30 + profileH - h),
                            Size(bw - 2.2f, h),
                            CornerRadius(1.5f),
                            alpha = opacity,
                        )
                    }
                }
                if (art != null) {
                    val u2 = if (reduced) state.u1 else state.u2
                    drawIntoCanvas { art.draw(it.nativeCanvas, card, MW, state.u1, u2, state.route, state.pinP, reduced) }
                }
            }
        }

        Column(
            Modifier
                .offset(x = PAD.dp, y = top.dp)
                .width((width - PAD * 2).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("SAVED OFFLINE · ALDER VALLEY", style = TextStyle(color = Kicker, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
            BasicText(
                "Ridge Line Trail",
                style = TextStyle(color = Ink, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf("14.2 km" to "Distance", "820 m" to "Ascent", "5 h 10 min" to "Moving time").forEach { (v, l) ->
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        BasicText(v, style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                        BasicText(l, style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        Column(
            Modifier
                .offset(x = (PAD + pw + 22).dp, y = (mapY + 12).dp)
                .width((width - PAD - (PAD + pw + 22)).dp)
                .graphicsLayer {
                    state.frame.floatValue
                    alpha = 1 - fmClamp(state.u1 * 2.4f, 0f, 1f)
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("PAPER MAP", style = TextStyle(color = Kicker, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
            BasicText("Sheet 7 · 1:25 000", style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            BasicText(
                "Waterproof print, contours every 20 m, huts and water sources marked.",
                style = TextStyle(color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp),
            )
            BasicText(
                "Drag to unfold  ›",
                modifier = Modifier.padding(top = 8.dp),
                style = TextStyle(color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
        }

        // The touch area over the map.
        Box(
            Modifier
                .offset(x = PAD.dp, y = mapY.dp)
                .size(MW.dp, MH.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Folded paper map of the trail. Tap or drag sideways to unfold it."
                }
                .pointerInput(state, pw) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            state.takeOver()
                            state.mode = 1
                            state.startU = state.u1
                            state.dragX = 0f
                            state.wake.intValue += 1
                        },
                        onDragEnd = {
                            state.goal = if (state.u1 + state.u1V * 0.25f > 0.5f) 1f else 0f
                            state.mode = 2
                            state.idle = 0f
                        },
                        onDragCancel = {
                            state.mode = 2
                            state.idle = 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            state.dragX += amount / density
                            state.u1 = rubber(state.startU + state.dragX / (2 * pw))
                            state.u1V = tracker.calculateVelocity().x / density / (2 * pw)
                        },
                    )
                }
                .pointerInput(state) {
                    detectTapGestures {
                        state.takeOver()
                        state.goal = if (state.u1 > 0.5f) 0f else 1f
                        state.mode = 2
                        state.wake.intValue += 1
                    }
                },
        )

        Row(
            Modifier
                .offset(x = (PAD + 16).dp, y = (profY + 12).dp)
                .width((MW - 32).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText("Elevation", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            BasicText(
                "1 394 m → 2 214 m",
                modifier = Modifier.graphicsLayer {
                    state.frame.floatValue
                    alpha = fmClamp(state.rise * 3, 0f, 1f)
                },
                style = TextStyle(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
        }

        Row(
            Modifier
                .offset(x = (PAD + 16).dp, y = (profY + 30 + profileH + 4).dp)
                .width((MW - 32).dp)
                .graphicsLayer {
                    state.frame.floatValue
                    alpha = fmClamp(state.rise * 3, 0f, 1f)
                },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val tick = TextStyle(color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
            BasicText("0 km", style = tick)
            BasicText("Larch Hut · 6.1 km", style = tick)
            BasicText("14.2 km", style = tick)
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = PAD.dp, end = PAD.dp, bottom = (safeBottom + 16).dp)
                .fillMaxWidth()
                .height(56.dp)
                .cssBoxShadow(Color(60 / 255f, 20 / 255f, 8 / 255f, 0.4f), 1.dp, 2.dp, 28.dp)
                .cssBoxShadow(Color(224 / 255f, 100 / 255f, 63 / 255f, 0.28f), 12.dp, 28.dp, 28.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    val pill = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r))
                    }
                    // The inset highlight along the top edge: the pill in a light tint, then the
                    // accent fill one pixel lower, inside the pill.
                    clipPath(pill) {
                        drawRect(Color(255 / 255f, 220 / 255f, 200 / 255f, 0.45f))
                        translate(top = 1.dp.toPx()) { drawRoundRect(Accent, cornerRadius = r) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Start route", style = TextStyle(color = Color(0xFFFFF4EC), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
        }
    }
}

/** One frame of the loop, the springs and the hand-back after a touch. */
private fun step(s: FoldedMapState, dt: Float, auto: Boolean, reduced: Boolean, feel: () -> Unit) {
    val m = s.mode
    if (m == 0) {
        if (!auto) {
            s.u1 = 0f; s.u2 = 0f; s.route = 0f; s.pinP = 0f; s.rise = 0f
            return
        }
        val t = (s.clock + dt) % LOOP
        s.clock = t
        foldedMapScript(t, s.script)
        s.u1 = s.script[0]
        s.u2 = s.script[1]
        s.route = s.script[2]
        s.pinP = s.script[3]
        s.rise = s.script[4]
        return
    }
    val before = s.u1
    if (m == 2) {
        val w = 11f
        val z = if (reduced) 1f else 0.72f
        val a = -w * w * (s.u1 - s.goal) - 2 * z * w * s.u1V
        s.u1V += a * dt
        s.u1 += s.u1V * dt
        if (abs(s.u1 - s.goal) < 0.004f && abs(s.u1V) < 0.02f) s.idle += dt
        if ((s.goal == 1f && before < 0.99f && s.u1 >= 0.99f) || (s.goal == 0f && before > 0.01f && s.u1 <= 0.01f)) feel()
        if (s.idle > FOLDED_MAP_IDLE_BEFORE_RETURN) {
            s.mode = 3
            s.ret = 0f
            s.fromU = s.u1
        }
    } else if (m == 3) {
        val r = min(1f, s.ret + dt / FOLDED_MAP_RETURN_FOR)
        s.ret = r
        s.u1 = s.fromU * (1 - fmEaseInOut(r))
        if (r >= 1f) {
            s.mode = 0
            s.clock = 0f
            s.u1 = 0f
            s.u2 = 0f
            s.shown = 0f
            return
        }
    }
    // Panel 2 trails panel 1.
    val w2 = 22f
    val a2 = -w2 * w2 * (s.u2 - s.u1) - 2 * 0.8f * w2 * s.u2V
    s.u2V += a2 * dt
    s.u2 += s.u2V * dt
    if (s.mode == 3) s.u2 += (s.u1 - s.u2) * s.ret
    // Route, pin and profile while the map lies open; they leave four times faster.
    val open = s.u1 > 0.96f && s.u2 > 0.96f && s.mode != 3
    s.shown = if (open) s.shown + dt else max(0f, s.shown - dt * 4)
    val e = s.shown
    s.route = fmEaseInOut(e / 1.0f)
    s.pinP = fmClamp((e - 0.8f) / 0.4f, 0f, 1f)
    s.rise = fmClamp((e - 0.2f) / 0.8f, 0f, 1f)
}

/** The forest-dark screen behind the map: the AGSL backdrop from API 33, gradients below it. */
@Composable
private fun FoldedMapBackdrop(modifier: Modifier) {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(FOLDED_MAP_BACKDROP_AGSL) }.getOrNull() else null
    }
    Box(
        modifier.drawWithCache {
            val w = size.width / density
            val h = size.height / density
            val fallback = listOf(
                Brush.verticalGradient(
                    0f to Color(0.105f, 0.170f, 0.135f), 0.45f to Color(0.080f, 0.140f, 0.108f), 1f to Color(0.050f, 0.090f, 0.070f),
                    startY = 0f, endY = h,
                ),
                Brush.radialGradient(
                    listOf(Color(0.05f, 0.06f, 0.04f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.4f), radius = w * 0.6f,
                ),
                Brush.radialGradient(
                    0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f),
                    center = Offset(w / 2, h / 2), radius = max(w, h) * 0.62f,
                ),
            )
            val brush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                shader.setFloatUniform("size", w, h)
                ShaderBrush(shader)
            } else {
                null
            }
            onDrawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    if (brush != null) {
                        drawRect(brush, Offset.Zero, Size(w, h))
                    } else {
                        drawRect(fallback[0], Offset.Zero, Size(w, h))
                        drawRect(fallback[1], Offset.Zero, Size(w, h), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                        drawRect(fallback[2], Offset.Zero, Size(w, h))
                    }
                }
            }
        },
    )
}
