package com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardArt.CRAYON_HALF
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardArt.CRAYON_LEFT
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardArt.CRAYON_RIGHT
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardArt.RELIEF_BAKE
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardArt.SHADOW_MARGIN
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.CANVAS_H
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.CANVAS_W
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.CANVAS_Y
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.CELL
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.DESIGN_H
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.DESIGN_W
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.GX
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.GY
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.LOOP
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PAPER_H
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PAPER_W
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PAPER_X
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PAPER_Y
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PLAQUE_H
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PLAQUE_W
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PLAQUE_X
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.PLAQUE_Y
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.REST_ANGLE
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.REST_X
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.REST_Y
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.autoBadge
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.autoGrid
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.autoPeel
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.crayonAt
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.pack
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.revealed
import com.ngocdevv.nativecook.blocks.cards.crayonrubbingcard.CrayonRubbingCardMotion.rubSegment
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Crayon Rubbing Card — a port of crayon-rubbing-card (React Native) and CrayonRubbingCard.swift.
 A museum's rubbing card: a sheet of paper over a bronze plaque; a wax crayon scrubs it and the
 hidden relief (a heron among reeds, a low sun, an inscription) appears where the paper is pushed
 up, all in one AGSL shader that reads a baked height image and a coarse rubbed-field grid.
 LOOP = 9 s: the crayon is picked up 0–0.35 s, scrubs a 14-stroke zig-zag to 3.4 s and circles the
 heron to 4.0 s, is laid down by 4.55 s; "Collected" pops 4.0–4.6 s; the sheet peels to a fresh one
 7.3–8.75 s. Touch: rub with a finger (the crayon follows just above-left of it); 70 % revealed
 collects the plaque; 2 s after the last touch the sheet peels and the loop restarts.
 Reduced motion: the finished rubbing (the loop's frame at 5 s); nothing plays.
 */

private const val IDLE_MS = 2000L
private const val DIGIT_H = 34f
private val Cream = Color(0xFFF1EADF)
private val Muted = Color(0x99F1EADF)
private val Gold = Color(0xFFE2B66E)
private val Backdrop = Color(0xFF211D19)
private const val STILL_T = 5f
private val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val InOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

private class CrayonRub(val reduced: Boolean) {
    var t by mutableFloatStateOf(if (reduced) STILL_T else 0f)
    var owned by mutableStateOf(false)
    var returning by mutableStateOf(false)
    var touching = false
    val grid = FloatArray(GX * GY)
    val user = FloatArray(GX * GY)
    val cx = Animatable(REST_X)
    val cy = Animatable(REST_Y)
    val angle = Animatable(REST_ANGLE)
    val contact = Animatable(0f)
    val peel = Animatable(0f)
    val badge = Animatable(0f)
    val pct = Animatable(0f)
    var grain = -0.6f
    /** Bumped whenever the field's image changes; read by the hero's draw. */
    var version by mutableIntStateOf(0)
    var revealPct by mutableIntStateOf(0)
    var reveal by mutableFloatStateOf(0f)
    var idleJob: Job? = null
    private var lastSum = -1f
    private val pose = FloatArray(4)
    private val ahead = FloatArray(4)

    // The field's images: two grey ones in turn (the shader reads the newest), one alpha mask for the fallback.
    val greyPixels = IntArray(GX * GY)
    val alphaPixels = IntArray(GX * GY)
    val greys = Array(2) { Bitmap.createBitmap(GX, GY, Bitmap.Config.ARGB_8888) }
    var current = 0
    val alphaMask: Bitmap = Bitmap.createBitmap(GX, GY, Bitmap.Config.ARGB_8888)
    var alphaImage: ImageBitmap = alphaMask.asImageBitmap()

    /** Crayon tip, angle and contact; peel and badge — from the hand when owned, else from the loop. */
    fun crayon(out: FloatArray) {
        if (owned) {
            out[0] = cx.value; out[1] = cy.value; out[2] = angle.value; out[3] = contact.value
        } else {
            crayonAt(t, out)
        }
    }

    fun peelNow() = if (owned) peel.value else autoPeel(t)
    fun badgeNow() = if (owned) badge.value else autoBadge(t)

    /** The loop's field and grain at its time. */
    fun followLoop() {
        autoGrid(t, grid)
        crayonAt(t, pose)
        if (pose[3] > 0f) {
            crayonAt(min(t + 1f / 60f, LOOP - 1e-3f), ahead)
            grain = atan2(ahead[1] - pose[1], ahead[0] - pose[0])
        }
    }

    /** Rebuilds the field's image only when it changed; returns true when it did. */
    fun refresh(weights: FloatArray, total: Float): Boolean {
        var sum = 0f
        for (v in grid) sum += v
        if (sum == lastSum) return false
        lastSum = sum
        pack(grid, greyPixels, alphaPixels)
        current = 1 - current
        greys[current].setPixels(greyPixels, 0, GX, 0, 0, GX, GY)
        alphaMask.setPixels(alphaPixels, 0, GX, 0, 0, GX, GY)
        alphaImage = alphaMask.asImageBitmap()
        reveal = revealed(grid, weights, total)
        revealPct = min(99, (reveal * 100f).roundToInt())
        version++
        return true
    }
}

private fun CoroutineScope.now(block: suspend CoroutineScope.() -> Unit) = launch(start = CoroutineStart.UNDISPATCHED, block = block)

/**
 * A museum's crayon-rubbing card: rub the paper over a bronze plaque and the relief appears in wax.
 *
 * @param autoPlay scrubs the plaque until the heron shows, collects it and peels the sheet, every [LOOP] seconds.
 */
@Composable
fun CrayonRubbingCard(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val screen = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val rub = remember(reduced) { CrayonRub(reduced) }
    var art by remember { mutableStateOf<CrayonRubbingArtSet?>(null) }
    var wax by remember { mutableIntStateOf(0) }
    val appear = remember { Animatable(0f) }

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(CRAYON_RUB_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val images = remember(art) {
        art?.let { a ->
            CrayonImages(
                relief = a.relief.asImageBitmap(), crayons = a.crayons.map { it.asImageBitmap() },
                near = a.shadowNear.asImageBitmap(), far = a.shadowFar.asImageBitmap(), felt = a.felt.asImageBitmap(), scale = a.scale,
            )
        }
    }

    /** Points the shader's `rubbed` input at the newest field image. */
    fun bindField() {
        if (shader == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val field = BitmapShader(rub.greys[rub.current], android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        field.filterMode = BitmapShader.FILTER_MODE_LINEAR
        field.setLocalMatrix(Matrix().apply { setScale(CELL, CELL); postTranslate(PAPER_X, PAPER_Y) })
        shader.setInputShader("rubbed", field)
    }

    fun refresh() {
        val a = art ?: return
        if (rub.refresh(a.weights, a.total)) bindField()
    }

    LaunchedEffect(Unit) {
        val baked = withContext(Dispatchers.Default) { CrayonRubbingCardArt.bake(screen.density) }
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val relief = BitmapShader(baked.relief, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            relief.filterMode = BitmapShader.FILTER_MODE_LINEAR
            relief.setLocalMatrix(Matrix().apply { setScale(1f / RELIEF_BAKE, 1f / RELIEF_BAKE); postTranslate(PAPER_X, PAPER_Y) })
            shader.setInputShader("relief", relief)
            shader.setFloatUniform("plaque", PLAQUE_X, PLAQUE_Y, PLAQUE_W, PLAQUE_H)
            shader.setFloatUniform("paper", PAPER_X, PAPER_Y, PAPER_W, PAPER_H)
        }
        art = baked
        rub.followLoop()
        refresh()
        bindField()
        appear.animateTo(1f, tween(260))
    }

    // The loop clock, while nothing else owns the screen and the art is ready.
    LaunchedEffect(rub, auto) {
        snapshotFlow { auto && !rub.owned && art != null }.collectLatest { run ->
            if (!run) return@collectLatest
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0f else min((now - last) / 1e9f, 1f / 20f)
                    last = now
                    rub.t = (rub.t + dt) % LOOP
                    rub.followLoop()
                    refresh()
                }
            }
        }
    }

    // The percentage rests on whole numbers: each change springs the counter to the new value.
    LaunchedEffect(rub) {
        var before = -1
        snapshotFlow { rub.revealPct }.collectLatest { now ->
            if (before >= 0 && rub.owned && now / 10 > before / 10) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            before = now
            rub.pct.animateTo(now.toFloat(), spring(dampingRatio = 0.745f, stiffness = 180f))
        }
    }
    // Rubbing it far enough by hand collects the plaque.
    LaunchedEffect(rub) {
        var was = false
        snapshotFlow { rub.owned && !rub.returning && rub.reveal > 0.7f }.collect { on ->
            if (on && !was) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
                )
                launch { rub.badge.animateTo(1f, spring(dampingRatio = 0.335f, stiffness = 180f)) }
            }
            was = on
        }
    }

    /** A finger lands on the paper: the demo stops and the finger carries on from what is on screen. */
    fun takeOver() {
        rub.touching = true
        rub.idleJob?.cancel()
        rub.idleJob = null
        if (rub.owned && !rub.returning) return
        val c = FloatArray(4)
        rub.crayon(c)
        val fresh = rub.peelNow() > 0.001f
        val badge = rub.badgeNow()
        for (i in rub.user.indices) rub.user[i] = if (fresh) 0f else rub.grid[i]
        scope.now {
            rub.cx.snapTo(c[0]); rub.cy.snapTo(c[1]); rub.angle.snapTo(c[2]); rub.contact.snapTo(c[3])
            rub.peel.snapTo(0f); rub.badge.snapTo(badge)
        }
        rub.returning = false
        rub.owned = true
    }

    fun letGo() {
        rub.touching = false
        scope.launch { rub.contact.animateTo(0f, tween(180, easing = InOutQuad)) }
        rub.idleJob?.cancel()
        rub.idleJob = scope.launch {
            delay(IDLE_MS)
            // Back to rest within 0.9 s: the sheet peels away to a fresh one, the crayon goes home.
            rub.returning = true
            val glide = tween<Float>(800, easing = InOut)
            listOf(
                launch { rub.cx.animateTo(REST_X, glide) },
                launch { rub.cy.animateTo(REST_Y, glide) },
                launch { rub.angle.animateTo(REST_ANGLE, glide) },
                launch { rub.badge.animateTo(0f, tween(300, easing = InOutQuad)) },
                launch { rub.peel.animateTo(1f, tween(850, easing = InOut)) },
            ).joinAll()
            rub.user.fill(0f)
            rub.peel.snapTo(0f)
            rub.returning = false
            rub.t = 0f
            rub.owned = false
            if (!auto) {
                rub.followLoop()
                refresh()
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Backdrop)
            .clipToBounds(),
    ) {
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / screen.density
        val bottom = insets.getBottom(screen) / screen.density
        val roomW = maxWidth.value
        val roomH = maxHeight.value - top - bottom
        val k = min(min(roomW / DESIGN_W, roomH / DESIGN_H), 1.15f)
        val ox = (roomW - DESIGN_W * k) / 2f
        val oy = top + max(0f, (roomH - DESIGN_H * k) / 2f)

        // The museum table: warm grey felt with grain, a pool of gallery light, a vignette.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val base = Brush.linearGradient(
                        0f to Color(0xFF2E2925), 0.5f to Color(0xFF24201C), 1f to Color(0xFF191613),
                        start = Offset.Zero, end = Offset(w * 0.3f, h),
                    )
                    val light = Brush.radialGradient(
                        0f to Color(0x29FFE2B4), 0.5f to Color(0x0DFFE2B4), 1f to Color(0x00FFE2B4),
                        center = Offset((ox + 196f * k) * density, (oy + 330f * k) * density), radius = 380f * k * density,
                    )
                    val vignette = Brush.radialGradient(
                        0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0x99060403),
                        center = Offset(w / 2f, h * 0.45f), radius = h * 0.75f,
                    )
                    val felt = images?.let { ShaderBrush(ImageShader(it.felt, TileMode.Repeated, TileMode.Repeated)) }
                    onDrawBehind {
                        drawRect(base)
                        if (felt != null) drawRect(felt, alpha = appear.value)
                        drawRect(light)
                        drawRect(vignette)
                    }
                },
        )

        Box(
            Modifier
                .layout { measurable, constraints ->
                    val p = measurable.measure(Constraints.fixed((DESIGN_W * density).toInt(), (DESIGN_H * density).toInt()))
                    layout(constraints.maxWidth, constraints.maxHeight) { p.place((ox * density).toInt(), (oy * density).toInt()) }
                }
                .graphicsLayer {
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            Column(
                Modifier
                    .offset(24.dp, 8.dp)
                    .width((DESIGN_W - 48f).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BasicText("RUBBINGS", style = TextStyle(color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
                BasicText(
                    "Hall C, plaque 14",
                    style = TextStyle(color = Cream, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
                )
                BasicText("Cast bronze, 1911 · Riverside Gate", style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }

            // The hero: plaque, paper, wax and peel in one shader, the crayon over it.
            Box(
                Modifier
                    .offset(0.dp, CANVAS_Y.dp)
                    .size(CANVAS_W.dp, CANVAS_H.dp),
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "A sheet of paper over a bronze plaque. Rub it with the crayon to reveal the relief." }
                        .pointerInput(rub) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (rub.returning) return@awaitEachGesture
                                takeOver()
                                // The crayon's working edge sits just above and left of the finger.
                                var lx = down.position.x / density - 12f
                                var ly = down.position.y / density - 16f
                                val s = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)
                                val sx = lx
                                val sy = ly
                                scope.launch { rub.cx.animateTo(sx, s) }
                                scope.launch { rub.cy.animateTo(sy, s) }
                                scope.launch { rub.angle.animateTo(-34f, tween(160, easing = InOutQuad)) }
                                scope.launch { rub.contact.animateTo(1f, tween(120, easing = InOutQuad)) }
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (change.position == change.previousPosition) continue
                                    change.consume()
                                    if (!rub.owned || rub.returning) continue
                                    val x = change.position.x / density - 12f
                                    val y = change.position.y / density - 16f
                                    val dx = x - lx
                                    val dy = y - ly
                                    val turn = if (dx * dx + dy * dy > 1f) -34f + (dx * 0.4f).coerceIn(-8f, 8f) else null
                                    if (turn != null) rub.grain = atan2(dy, dx)
                                    scope.now {
                                        rub.cx.snapTo(x)
                                        rub.cy.snapTo(y)
                                        if (turn != null) rub.angle.snapTo(turn)
                                    }
                                    rubSegment(rub.user, lx - PAPER_X, ly - PAPER_Y, x - PAPER_X, y - PAPER_Y)
                                    rub.user.copyInto(rub.grid)
                                    refresh()
                                    lx = x
                                    ly = y
                                }
                                letGo()
                            }
                        },
                ) {
                    rub.version
                    val im = images
                    val c = FloatArray(4)
                    rub.crayon(c)
                    val peel = rub.peelNow()
                    val w = CrayonRubbingWaxes[wax]
                    scale(density, density, pivot = Offset.Zero) {
                        if (im == null || appear.value < 1f) drawPlaceholder(1f - appear.value)
                        if (im != null) {
                            if (shader != null && brush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                shader.setFloatUniform("peel", peel)
                                shader.setFloatUniform("wax", w.r, w.g, w.b)
                                shader.setFloatUniform("grainAngle", rub.grain)
                                drawRect(brush, Offset.Zero, Size(CANVAS_W, CANVAS_H), alpha = appear.value)
                            } else {
                                drawFallback(im, rub.alphaImage, w, peel, appear.value)
                            }
                            drawCrayon(im, wax, c[0], c[1], c[2], c[3], appear.value)
                        }
                    }
                }

                CrayonBadge(rub, Modifier.align(Alignment.TopEnd).padding(end = 14.dp))
            }

            CrayonPanel(rub, wax) { i ->
                wax = i
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }

            val press = remember { MutableInteractionSource() }
            val down by press.collectIsPressedAsState()
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier
                    .offset(16.dp, 698.dp)
                    .size((DESIGN_W - 32f).dp, 52.dp)
                    .scale(if (down) 0.97f else 1f)
                    .cssBoxShadow(Color(0x73000000), 1.dp, 2.dp, 26.dp)
                    .cssBoxShadow(Color(0x47C9913F), 10.dp, 24.dp, 26.dp)
                    .background(Brush.verticalGradient(0f to Color(0xFFF2CF8E), 0.55f to Gold, 1f to Color(0xFFC9913F)), shape)
                    .drawWithContent {
                        drawContent()
                        drawTopHighlight(26.dp.toPx())
                    }
                    .semantics { role = Role.Button }
                    .clickable(press, null) {},
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Save to my rubbings", style = TextStyle(color = Color(0xFF2A1A08), fontSize = 17.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

private class CrayonImages(
    val relief: ImageBitmap,
    val crayons: List<ImageBitmap>,
    val near: ImageBitmap,
    val far: ImageBitmap,
    val felt: ImageBitmap,
    val scale: Float,
)

private fun DrawScope.drawImageAt(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float, filter: ColorFilter? = null, blend: BlendMode = BlendMode.SrcOver) {
    if (alpha <= 0f) return
    withTransform({
        translate(x, y)
        scale(w / img.width, h / img.height, pivot = Offset.Zero)
    }) {
        drawImage(img, alpha = alpha.coerceIn(0f, 1f), colorFilter = filter, blendMode = blend)
    }
}

/** Before the art is baked: the plaque and the blank paper in flat colours. */
private fun DrawScope.drawPlaceholder(alpha: Float) {
    if (alpha <= 0f) return
    drawRoundRect(Color(0xFF6B4A2A), Offset(PLAQUE_X, PLAQUE_Y), Size(PLAQUE_W, PLAQUE_H), alpha = alpha)
    drawRect(Color(0xFFEFE8DB), Offset(PAPER_X, PAPER_Y), Size(PAPER_W, PAPER_H), alpha = alpha)
    drawRoundRect(Color(0xFF2C3160), Offset(REST_X + 8f, REST_Y - 9f), Size(88f, 18f), CornerRadius(4f), alpha = alpha)
}

// Relief luminance to alpha: all of it (for the faint emboss), or its raised part only (smoothstep 0.26–0.58, as the wax).
private val ReliefAlpha = ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f))
private val RaisedAlpha = ColorMatrix(
    floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 3.125f, 0f, 0f, 0f, -0.8125f * 255f),
)

/**
 * Below Android 13: the plaque in gradients with a stepped soft shadow, the paper with a faint emboss,
 * and the wax as the raised relief tinted in the wax colour and masked by the rubbed field; the peel
 * fades the old sheet out instead of folding it.
 */
private fun DrawScope.drawFallback(im: CrayonImages, field: ImageBitmap, wax: CrayonRubbingWax, peel: Float, appear: Float) {
    for (i in 0 until 5) {
        val grow = 4f + i * 4f
        drawRoundRect(
            Color.Black, Offset(PLAQUE_X - grow / 2f, PLAQUE_Y + 7f - grow / 2f), Size(PLAQUE_W + grow, PLAQUE_H + grow),
            CornerRadius(grow), alpha = 0.08f * appear,
        )
    }
    drawRect(
        Brush.linearGradient(
            0f to Color(0xFFA3733D), 0.5f to Color(0xFF7A5230), 1f to Color(0xFF5A3B1F),
            start = Offset(PLAQUE_X, PLAQUE_Y), end = Offset(PLAQUE_X + PLAQUE_W, PLAQUE_Y + PLAQUE_H),
        ),
        Offset(PLAQUE_X, PLAQUE_Y), Size(PLAQUE_W, PLAQUE_H), alpha = appear,
    )
    drawRect(Color(0x55FFE6B4), Offset(PLAQUE_X, PLAQUE_Y), Size(PLAQUE_W, PLAQUE_H), style = Stroke(2f), alpha = appear)
    drawRect(Color(0x59000000), Offset(PAPER_X + 1f, PAPER_Y + 2.5f), Size(PAPER_W, PAPER_H), alpha = appear)
    drawRect(Color(0xFFEFE8DB), Offset(PAPER_X, PAPER_Y), Size(PAPER_W, PAPER_H), alpha = appear)
    drawImageAt(im.relief, PAPER_X, PAPER_Y, PAPER_W, PAPER_H, 0.07f * appear, ColorFilter.colorMatrix(ReliefAlpha))
    val old = (1f - peel) * appear
    if (old <= 0f) return
    val bounds = Rect(PAPER_X, PAPER_Y, PAPER_X + PAPER_W, PAPER_Y + PAPER_H)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint().apply { alpha = old })
        val tint = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, wax.r * 255f, 0f, 0f, 0f, 0f, wax.g * 255f, 0f, 0f, 0f, 0f, wax.b * 255f,
                3.125f * 0.9f, 0f, 0f, 0f, -0.8125f * 255f * 0.9f,
            ),
        )
        drawImageAt(im.relief, PAPER_X, PAPER_Y, PAPER_W, PAPER_H, 1f, ColorFilter.colorMatrix(tint))
        drawImageAt(field, PAPER_X, PAPER_Y, GX * CELL, GY * CELL, 1f, blend = BlendMode.DstIn)
        canvas.restore()
    }
}

/** The crayon: its shadow (tight while it presses, wider and softer when lifted), then the stick. */
private fun DrawScope.drawCrayon(im: CrayonImages, wax: Int, x: Float, y: Float, angle: Float, contact: Float, appear: Float) {
    val up = 1f - contact
    withTransform({
        translate(x, y)
        rotate(angle, Offset.Zero)
    }) {
        val a = (0.5f - 0.18f * up) * appear
        val m = SHADOW_MARGIN
        val sx = 4f + 7f * up - m
        val sy = -9f + 3f + 12f * up - m
        val u = up.coerceIn(0f, 1f)
        drawImageAt(im.near, sx, sy, 92f + m * 2, 18f + m * 2, a * (1f - u))
        drawImageAt(im.far, sx, sy, 92f + m * 2, 18f + m * 2, a * u)
        drawImageAt(im.crayons[wax], CRAYON_LEFT, -CRAYON_HALF, CRAYON_RIGHT - CRAYON_LEFT, CRAYON_HALF * 2, appear)
    }
}

private fun DrawScope.drawTopHighlight(r: Float) {
    val y = 1.dp.toPx() / 2f
    drawLine(
        Brush.horizontalGradient(0f to Color.Transparent, 0.12f to Color(0x99FFF6DC), 0.88f to Color(0x99FFF6DC), 1f to Color.Transparent),
        Offset(r * 0.5f, y), Offset(size.width - r * 0.5f, y), strokeWidth = 1.dp.toPx(),
    )
}

@Composable
private fun CrayonBadge(rub: CrayonRub, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .graphicsLayer {
                val b = rub.badgeNow()
                alpha = min(1f, max(0f, b * 1.4f))
                scaleX = 0.86f + 0.14f * b
                scaleY = 0.86f + 0.14f * b
                rotationZ = -4f * b
            }
            .height(44.dp)
            .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 22.dp)
            .cssBoxShadow(Color(0x59000000), 10.dp, 24.dp, 22.dp)
            .background(Gold, shape)
            .drawWithContent {
                drawContent()
                drawTopHighlight(22.dp.toPx())
            }
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(16.dp)) {
            val k = size.width / 16f
            val p = Path().apply {
                moveTo(3.5f * k, 8.4f * k)
                lineTo(6.6f * k, 11.4f * k)
                lineTo(12.6f * k, 4.8f * k)
            }
            drawPath(p, Color(0xFF2A1A08), style = Stroke(2.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Column {
            BasicText("Collected", style = TextStyle(color = Color(0xFF2A1A08), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
            BasicText(
                "No. 14 of 40",
                style = TextStyle(color = Color(0xB32A1A08), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
            )
        }
    }
}

@Composable
private fun CrayonDigitStrip(offset: () -> Float) {
    Box(
        Modifier
            .size(17.dp, DIGIT_H.dp)
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -offset() * DIGIT_H * density },
        ) {
            for (d in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) {
                Box(Modifier.size(17.dp, DIGIT_H.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        d,
                        style = TextStyle(
                            color = Cream, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum",
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CrayonPanel(rub: CrayonRub, wax: Int, pick: (Int) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .offset(16.dp, 612.dp)
            .size((DESIGN_W - 32f).dp, 72.dp)
            .cssBoxShadow(Color(0x59000000), 1.dp, 2.dp, 20.dp)
            .cssBoxShadow(Color(0x4D000000), 12.dp, 28.dp, 20.dp)
            .background(Color(0x0FF1EADF), shape)
            .border(0.5.dp, Color(0x29F1EADF), shape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val label = TextStyle(color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText("REVEALED", style = label)
            Row(Modifier.height(DIGIT_H.dp), verticalAlignment = Alignment.Bottom) {
                CrayonDigitStrip {
                    val v = rub.pct.value
                    val ones = v - 10f * floor(v / 10f)
                    floor(v / 10f) + max(0f, ones - 9f)
                }
                CrayonDigitStrip {
                    val v = rub.pct.value
                    v - 10f * floor(v / 10f)
                }
                BasicText(
                    "%",
                    Modifier.padding(start = 2.dp, bottom = 5.dp),
                    style = TextStyle(color = Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x1FF1EADF), RoundedCornerShape(2.dp))
                    .clipToBounds()
                    .crayonBarFill { max(0.001f, rub.pct.value / 100f) },
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("WAX", style = label)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonRubbingWaxes.forEachIndexed { i, w ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .border(1.5.dp, if (wax == i) Gold else Color.Transparent, CircleShape)
                            .padding(3.dp)
                            .background(Brush.verticalGradient(w.tones.map { Color(it) }), RoundedCornerShape(12.dp))
                            .semantics {
                                role = Role.Button
                                selected = wax == i
                                contentDescription = "${w.name.lowercase()} wax"
                            }
                            .clickable { pick(i) },
                    )
                }
            }
        }
    }
}

/** The progress bar's gold fill, scaled from the left by [fraction], drawn without recomposing. */
private fun Modifier.crayonBarFill(fraction: () -> Float) = drawWithContent {
    drawContent()
    drawRoundRect(Gold, Offset.Zero, Size(size.width * fraction(), size.height), CornerRadius(2.dp.toPx()))
}
