package com.ngocdevv.nativecook.blocks.onboarding.jigsawstepswelcome

import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The insets animate for a moment while the demo hides the status bar: bakes keyed on them wait this long to settle. */
private const val SETTLE_MS = 120L

/*
 Jigsaw Steps Welcome — onboarding for a family-photo app ("Piecemeal"). A port of
 JigsawStepsWelcome.tsx (the reference) and JigsawStepsWelcome.swift; the numbers are the same. A
 beach photo is cut into twelve jigsaw pieces scattered under the board; each onboarding step
 snaps four of them home (corners, outer middles, middle row), the cut lines fade, and the photo
 comes alive with a slow push-in. The text and dots above the button roll to each step. Timeline
 in JigsawStepsWelcomeMotion.kt (LOOP = 10 s); outlines and layout in JigsawStepsWelcomeArt.kt.

 Touch: pick up any piece (it lifts, its shadow grows, its tilt straightens on a spring) and drag
 it; let go within 35 % of a cell of its home and it snaps in (spring ω 13 rad/s, damping ratio
 0.7, a light haptic, a light running round its edges); anywhere else it stays where it was
 dropped. The text follows how many pieces are home; with all twelve home the cut lines fade.
 2 s after the last touch the pieces spring back to the pile and the loop restarts.
 Reduced motion: the finished photo and the last step, still; pieces can still be dragged.

 Per frame: per piece three pre-blurred silhouettes, the brown edge, the photo filled into its
 outline with a bitmap shader, and its strokes inside a path clip; the whole photo on top; the
 text and button labels move in layers.
 */

private val JigsawInk = Color(0xFF2A221C)
private val JigsawMute = Color(0xFF2A221C).copy(alpha = 0.6f)
private val JigsawAccent = Color(0xFFD4643A)
private const val HOLD_AFTER_TOUCH = 2f
private const val SLOTS = 4
private const val K = 170f
private val DAMP = 2f * 0.7f * sqrt(K)
private const val POSE = 8
private const val SOFT_K = 1.5f

private class JigsawText(val title: String, val body: String)

private val jigsawTexts = listOf(
    JigsawText("Your family, in pieces", "Photos scattered across phones, drawers and old albums."),
    JigsawText("Gather every photo", "Bring in your camera roll, scanned prints and shared links."),
    JigsawText("Invite the whole family", "Everyone adds the moments only they were there for."),
    JigsawText("See the whole picture", "Piecemeal puts it all together, year by year."),
)

private class JigsawController(val L: JigsawLayout, val auto: Boolean, val reduced: Boolean) {
    var frame by mutableFloatStateOf(0f)
    var wake by mutableIntStateOf(0)

    var clock = 0f
    var now = 0f
    /** 0 = autoPlay, 1 = the finger's, 2 = springing back to the pile. */
    var mode = 0
    var lastTouch = 0f
    /** Per piece: x, y, rotation, scale, lift, edge light, groove, on board. */
    val poses = FloatArray(JigsawMotion.COUNT * POSE)
    val slots = intArrayOf(-1, -1, -1, -1)
    var roll = 0f
    val whole = floatArrayOf(0f, 1f, 0f)
    /** Manual state per piece: x, y, rot, vx, vy, vrot, lift, flash, target x, y, rot, on board. */
    val M = FloatArray(JigsawMotion.COUNT * 12)
    var held = -1
    var top = -1
    var grabDX = 0f
    var grabDY = 0f
    var fingerX = 0f
    var fingerY = 0f
    var returnAt = 0f
    private val tmp = FloatArray(7)

    init { autoFrame() }

    fun needsFrames() = auto || mode != 0

    private fun autoFrame() {
        val t = if (reduced) 7.2f else clock
        for (i in 0 until JigsawMotion.COUNT) {
            JigsawMotion.piece(i, t, L.home[i], L.scatter[i], tmp, 0)
            val o = i * POSE
            for (k in 0 until 7) poses[o + k] = tmp[k]
            poses[o + 7] = if (tmp[6] > 0f || (t > 6.4f && t < 8.2f)) 1f else 0f
        }
        for (j in 0 until SLOTS) slots[j] = if (reduced) -1 else JigsawMotion.slotPiece(j, t)
        roll = JigsawMotion.textRoll(t)
        JigsawMotion.whole(t, whole)
    }

    fun tick(dt0: Float, view: View) {
        val dt = min(0.034f, dt0)
        now += dt
        if (mode == 0) {
            if (auto) clock = (clock + dt) % JigsawMotion.LOOP
            autoFrame()
            frame = now
            return
        }
        if (mode == 1 && held < 0 && now - lastTouch > HOLD_AFTER_TOUCH) {
            mode = 2; returnAt = now; held = -1; top = -1
        }
        if (mode == 2 && now - returnAt > 1.2f) {
            clock = 0f; mode = 0
            autoFrame()
            frame = now
            return
        }
        val st = M
        var home = 0
        for (i in 0 until JigsawMotion.COUNT) {
            val o = i * 12
            if (mode == 2) {
                st[o + 8] = L.scatter[i][0]; st[o + 9] = L.scatter[i][1]; st[o + 10] = L.scatter[i][2]; st[o + 11] = 0f
            }
            val isHeld = held == i
            if (isHeld) {
                st[o] = fingerX - grabDX; st[o + 1] = fingerY - grabDY; st[o + 3] = 0f; st[o + 4] = 0f
            } else {
                st[o + 3] += (K * (st[o + 8] - st[o]) - DAMP * st[o + 3]) * dt
                st[o + 4] += (K * (st[o + 9] - st[o + 1]) - DAMP * st[o + 4]) * dt
                st[o] += st[o + 3] * dt
                st[o + 1] += st[o + 4] * dt
            }
            val rotTarget = if (isHeld) 0f else st[o + 10]
            st[o + 5] += (K * 0.6f * (rotTarget - st[o + 2]) - DAMP * 0.8f * st[o + 5]) * dt
            st[o + 2] += st[o + 5] * dt
            val liftTarget = if (isHeld || (mode == 2 && now - returnAt < 0.6f)) 1f else 0f
            st[o + 6] += (liftTarget - st[o + 6]) * min(1f, dt * (if (mode == 2) 4f else 12f))
            st[o + 7] = max(0f, st[o + 7] - dt / 0.35f)
            if (st[o + 11] > 0f) home += 1
            val p = i * POSE
            poses[p] = st[o]; poses[p + 1] = st[o + 1]; poses[p + 2] = st[o + 2]
            poses[p + 3] = 1f + 0.07f * st[o + 6]; poses[p + 4] = st[o + 6]; poses[p + 5] = st[o + 7]
            poses[p + 6] = st[o + 11]; poses[p + 7] = st[o + 11]
        }
        // With every piece home the cut lines fade (the whole photo fades in over them).
        if (home == JigsawMotion.COUNT) for (i in 0 until JigsawMotion.COUNT) poses[i * POSE + 6] = 0f
        val tp = top
        slots[0] = if (tp >= 0 && (held == tp || st[tp * 12 + 6] > 0.02f)) tp else -1
        slots[1] = -1; slots[2] = -1; slots[3] = -1
        val target = if (home == JigsawMotion.COUNT) 3f else floor(home / 4f)
        roll += (target - roll) * min(1f, dt * 8f)
        whole[0] += ((if (home == JigsawMotion.COUNT) 1f else 0f) - whole[0]) * min(1f, dt * 3f)
        whole[1] = 1f
        whole[2] = 0f
        frame = now
    }

    /** Is the point inside piece i where it is drawn now? */
    fun hits(i: Int, x: Float, y: Float, art: JigsawArt): Boolean {
        val o = i * POSE
        val dx = x - poses[o]
        val dy = y - poses[o + 1]
        val c = cos(-poses[o + 2])
        val s = sin(-poses[o + 2])
        val lx = (dx * c - dy * s) / poses[o + 3] + L.home[i][0]
        val ly = (dx * s + dy * c) / poses[o + 3] + L.home[i][1]
        return art.regions[i].contains((lx * 4f).toInt(), (ly * 4f).toInt())
    }

    fun begin(x: Float, y: Float, art: JigsawArt): Boolean {
        var hit = -1
        for (j in 0 until SLOTS) if (hit < 0 && slots[j] >= 0 && hits(slots[j], x, y, art)) hit = slots[j]
        var i = JigsawMotion.COUNT - 1
        while (i >= 0 && hit < 0) { if (hits(i, x, y, art)) hit = i; i-- }
        if (hit < 0) return false
        if (mode != 1) {
            // Take over from wherever the pieces are drawn now.
            for (k in 0 until JigsawMotion.COUNT) {
                val o = k * POSE
                val m = k * 12
                M[m] = poses[o]; M[m + 1] = poses[o + 1]; M[m + 2] = poses[o + 2]
                M[m + 3] = 0f; M[m + 4] = 0f; M[m + 5] = 0f
                M[m + 6] = poses[o + 4]; M[m + 7] = 0f
                M[m + 8] = poses[o]; M[m + 9] = poses[o + 1]; M[m + 10] = poses[o + 2]; M[m + 11] = poses[o + 7]
            }
            mode = 1
        }
        held = hit
        top = hit
        grabDX = x - poses[hit * POSE]
        grabDY = y - poses[hit * POSE + 1]
        fingerX = x
        fingerY = y
        lastTouch = now
        M[hit * 12 + 11] = 0f
        return true
    }

    fun move(x: Float, y: Float) {
        if (held < 0) return
        fingerX = x; fingerY = y; lastTouch = now
    }

    fun end(x: Float, y: Float, vx: Float, vy: Float, view: View) {
        val i = held
        if (i < 0) return
        held = -1
        lastTouch = now
        val o = i * 12
        val px = x - grabDX
        val py = y - grabDY
        M[o + 3] = vx * 0.25f
        M[o + 4] = vy * 0.25f
        if (hypot(px - L.home[i][0], py - L.home[i][1]) < L.cellW * 0.35f) {
            M[o + 8] = L.home[i][0]; M[o + 9] = L.home[i][1]; M[o + 10] = 0f; M[o + 11] = 1f; M[o + 7] = 1f
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } else {
            M[o + 8] = px; M[o + 9] = py; M[o + 10] = M[o + 2]
        }
    }
}

private class JigsawPainter(val L: JigsawLayout, val art: JigsawArt, val paths: List<Path>) {
    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = jigsawHex(0x8C6F55) }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(art.photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(L.board.width() / art.photo.width, L.board.height() / art.photo.height)
                postTranslate(L.board.left, L.board.top)
            })
        }
    }
    private val bevel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.6f; color = jigsawArgb(255, 250, 240, 0.32f) }
    private val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; color = jigsawArgb(64, 40, 22) }
    private val flash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = jigsawArgb(255, 226, 170) }
    private val gleam = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val boardClip = Path().apply { addRoundRect(L.board, 4f, 4f, Path.Direction.CW) }
    private val dst = RectF()

    private fun soft(n: android.graphics.Canvas, s: JigsawSoft, dy: Float, alpha: Float) {
        if (alpha <= 0.002f) return
        bmp.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        dst.set(s.x, s.y + dy, s.x + s.bitmap.width / SOFT_K, s.y + dy + s.bitmap.height / SOFT_K)
        n.drawBitmap(s.bitmap, null, dst, bmp)
        bmp.alpha = 255
    }

    private fun piece(n: android.graphics.Canvas, c: JigsawController, i: Int) {
        val o = i * POSE
        val p = c.poses
        val h = L.home[i]
        n.save()
        n.translate(p[o], p[o + 1])
        n.rotate(Math.toDegrees(p[o + 2].toDouble()).toFloat())
        n.scale(p[o + 3], p[o + 3])
        n.translate(-h[0], -h[1])
        val lift = p[o + 4]
        val off = 1f - p[o + 7]
        val amb = (0.16f + 0.16f * lift) * off
        val k = lift.coerceIn(0f, 1f)
        soft(n, art.soft3[i], 3f + 14f * lift, amb * (1f - k))
        soft(n, art.soft12[i], 3f + 14f * lift, amb * k)
        soft(n, art.contact[i], 1.2f, 0.32f * (1f - min(1f, lift * 1.4f)) * off)
        val path = paths[i]
        if (off > 0.002f) {
            edge.alpha = (off * 255f).toInt().coerceIn(0, 255)
            n.save(); n.translate(0f, 1.6f); n.drawPath(path, edge); n.restore()
        }
        n.drawPath(path, face)
        n.save()
        n.clipPath(path)
        n.drawPath(path, bevel)
        val g = p[o + 6] * 0.55f
        if (g > 0.002f) { groove.alpha = (g * 255f).toInt(); n.drawPath(path, groove) }
        val f = p[o + 5]
        if (f > 0.002f) { flash.alpha = (f * 255f).toInt(); n.drawPath(path, flash) }
        n.restore()
        n.restore()
    }

    fun draw(n: android.graphics.Canvas, c: JigsawController) {
        n.drawBitmap(art.paper, null, RectF(0f, 0f, L.W, L.H), bmp)
        for (i in 0 until JigsawMotion.COUNT) if (i !in c.slots) piece(n, c, i)
        val w = c.whole
        if (w[0] > 0.002f) {
            val B = L.board
            n.save()
            n.clipPath(boardClip)
            n.saveLayerAlpha(B.left, B.top, B.right, B.bottom, (w[0] * 255f).toInt().coerceIn(0, 255))
            n.save()
            n.scale(w[1], w[1], B.left + B.width() * 0.62f, B.top + B.height() * 0.45f)
            n.drawBitmap(art.photo, null, B, bmp)
            n.restore()
            val ga = sin(PI.toFloat() * w[2])
            if (ga > 0.002f) {
                n.save()
                n.translate((w[2] * 1.6f - 0.3f) * B.width(), 0f)
                gleam.shader = LinearGradient(
                    B.left - B.width() * 0.3f, B.top, B.left + B.width() * 0.3f, B.top + B.height() * 0.4f,
                    intArrayOf(jigsawArgb(255, 220, 170, 0f), jigsawArgb(255, 220, 170, 0.28f), jigsawArgb(255, 220, 170, 0f)), null, Shader.TileMode.CLAMP,
                )
                gleam.alpha = (ga * 255f).toInt()
                n.drawRect(B.left - B.width() * 0.3f, B.top, B.left + B.width() * 0.3f, B.bottom, gleam)
                n.restore()
            }
            n.restore()
            n.restore()
        }
        for (j in 0 until SLOTS) { val i = c.slots[j]; if (i >= 0) piece(n, c, i) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JigsawStepsWelcome(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF2E9DC)).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = max(
            WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value,
            WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding().value,
        )
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val W = maxWidth.value
        val H = maxHeight.value
        val L = remember(W, H, top, bottom) { JigsawLayout(W, H, top, bottom) }
        val paths = remember(L) { jigsawPiecePaths(L) }
        val art by produceState<JigsawArt?>(null, L) {
            delay(SETTLE_MS)
            value = withContext(Dispatchers.Default) { jigsawBake(context, L, paths, min(3f, density)) }
        }
        val c = remember(L, auto, reduced) { JigsawController(L, auto, reduced) }
        val ready = art != null

        LaunchedEffect(c, ready, c.wake) {
            if (!ready) return@LaunchedEffect
            var last = -1L
            do {
                withFrameNanos { n ->
                    val dt = if (last < 0) 0f else min(0.05f, (n - last) / 1e9f)
                    last = n
                    c.tick(dt, view)
                }
            } while (c.needsFrames())
        }

        val a = art
        if (a != null) {
            val painter = remember(a) { JigsawPainter(L, a, paths) }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "A beach photo cut into jigsaw pieces. Drag a piece to its place on the board." }
                    .pointerInput(c, a) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!c.begin(down.position.x / density, down.position.y / density, a)) return@awaitEachGesture
                            c.wake++
                            val vt = VelocityTracker()
                            vt.addPosition(down.uptimeMillis, down.position)
                            var last = down.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                vt.addPosition(ch.uptimeMillis, ch.position)
                                last = ch.position
                                if (!ch.pressed) break
                                ch.consume()
                                c.move(ch.position.x / density, ch.position.y / density)
                            }
                            val vel = vt.calculateVelocity()
                            c.end(last.x / density, last.y / density, vel.x / density, vel.y / density, view)
                        }
                    },
            ) {
                c.frame
                drawIntoCanvas { cv ->
                    val n = cv.nativeCanvas
                    n.save()
                    n.scale(density, density)
                    painter.draw(n, c)
                    n.restore()
                }
            }
        }

        // Top bar: the mark and name, Skip.
        Row(
            Modifier.offset(x = 20.dp, y = (top + 8f).dp).width((W - 40f).dp).height(JIGSAW_TOP_BAR.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JigsawMark()
            BasicText("Piecemeal", Modifier.padding(start = 8.dp), style = TextStyle(color = JigsawInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp))
            Spacer(Modifier.weight(1f))
            BasicText("Skip", Modifier.semantics { role = Role.Button }.padding(8.dp), style = TextStyle(color = JigsawMute, fontSize = 16.sp, fontWeight = FontWeight.Medium))
        }

        // The step's dots and text, rolling.
        Column(Modifier.offset(x = 24.dp, y = L.textY.dp).width((W - 48f).dp).height(JIGSAW_TEXT_H.dp)) {
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                c.frame
                var x = 0f
                val d = density
                for (k in 0 until 3) {
                    val on = JigsawMotion.smoothstep(k + 0.5f, k + 1f, c.roll)
                    val w = (8f + 16f * on) * d
                    drawRoundRect(JigsawAccent, Offset(x, 0f), Size(w, 8f * d), CornerRadius(4f * d), alpha = 0.3f + 0.7f * on)
                    x += w + 6f * d
                }
            }
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height((JIGSAW_TEXT_H - 22f).dp).clipToBounds()) {
                // Unbounded, or the column is measured to the window's height and the later steps get none.
                Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).graphicsLayer { c.frame; translationY = -c.roll * JIGSAW_TEXT_H * density }) {
                    for (t in jigsawTexts) {
                        Column(Modifier.height(JIGSAW_TEXT_H.dp)) {
                            BasicText(t.title, style = TextStyle(color = JigsawInk, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
                            BasicText(t.body, Modifier.padding(top = 6.dp), style = TextStyle(color = JigsawMute, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }

        JigsawCta(Modifier.offset(x = 20.dp, y = L.ctaY.dp).width((W - 40f).dp)) { c.frame; c.roll }
    }
}

@Composable
private fun JigsawMark() {
    val path = remember {
        PathParser().parsePathString("M3 3h6.2c-.9-2.6 4.5-2.6 3.6 0H19v6.2c2.6-.9 2.6 4.5 0 3.6V19h-6.2c.9 2.6-4.5 2.6-3.6 0H3v-6.2c2.6.9 2.6-4.5 0-3.6z").toPath()
    }
    Canvas(Modifier.size(22.dp)) {
        val k = size.width / 22f
        scale(k, k, pivot = Offset.Zero) { drawPath(path, JigsawAccent) }
    }
}

@Composable
private fun JigsawCta(modifier: Modifier, roll: () -> Float) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(130), label = "cta")
    val density = LocalDensity.current.density
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .cssBoxShadow(Color(120 / 255f, 50 / 255f, 20 / 255f, 0.3f), 1.dp, 2.dp, 26.dp)
            .cssBoxShadow(Color(212 / 255f, 100 / 255f, 58 / 255f, 0.28f), 10.dp, 22.dp, 26.dp)
            .height(JIGSAW_CTA_H.dp)
            .background(JigsawAccent, RoundedCornerShape(26.dp))
            .drawWithContent {
                drawContent()
                val r = 26.dp.toPx()
                clipPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }) {
                    drawRect(Color.White.copy(alpha = 0.3f), Offset.Zero, Size(size.width, 1.dp.toPx()))
                }
            }
            .semantics { role = Role.Button }
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }) },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.height(22.dp).fillMaxWidth().clipToBounds()) {
            Column(
                Modifier.fillMaxWidth().graphicsLayer { translationY = -JigsawMotion.smoothstep(2.4f, 2.9f, roll()) * 22f * density },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                val style = TextStyle(color = Color(0xFFFFF8F0), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                BasicText("Continue", Modifier.height(22.dp), style = style)
                BasicText("Start your album", Modifier.height(22.dp), style = style)
            }
        }
    }
}
