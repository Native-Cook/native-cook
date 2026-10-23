package com.ngocdevv.nativecook.blocks.playground.marblingcomb

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// An ebru palette, ground first: lapis, then bone, madder, ochre, cobalt, sage, rose and ink.
private val DefaultPalette = listOf(
    Color(0xFF16243C), Color(0xFFEFE4CA), Color(0xFFB5432F), Color(0xFFD99A36),
    Color(0xFF3D68AA), Color(0xFF7E9B83), Color(0xFFE1A693), Color(0xFF1D1A17),
)
internal val MarbleGround = Color(0xFF16243C)
private val PaperColor = Color(0xFFF2EBDC)
private val Ink = Color(0xFFF4ECDB)

// Sheet margins, dp from the screen (top and bottom come from the safe area and the controls).
private const val SHEET_SIDE = 18f
private const val SHEET_TOP = 64f
private const val SHEET_BOTTOM = 148f

/**
 * Paper marbling (ebru). Paint dropped on size spreads into rings, pushing every colour already
 * floating outwards so the area of each is kept; a stylus or a comb dragged through the paint pulls
 * it into feathered, combed patterns. Print lays a sheet over the tray and peels it back to show the
 * pattern taken up on its underside, leaving the size clean.
 *
 * @param autoPlay drops rings, combs a nonpareil and prints it, on a loop.
 * @param palette the size (index 0, the ground) and the paints.
 */
@Composable
fun MarblingComb(modifier: Modifier = Modifier, autoPlay: Boolean = false, palette: List<Color> = DefaultPalette) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val scene = remember(reducedMotion, palette) { MarbleScene(auto, reducedMotion, palette) }
    var tool by remember { mutableIntStateOf(0) }
    var ink by remember { mutableIntStateOf(2) }
    var printing by remember { mutableStateOf(false) }

    LaunchedEffect(scene, auto) { scene.engine.setAuto(auto) }
    LaunchedEffect(scene, tool) { scene.engine.selectedTool = tool }
    LaunchedEffect(scene, ink) { scene.engine.ink = ink }

    BoxWithConstraints(modifier.fillMaxSize().background(palette[0])) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()

        LaunchedEffect(scene, width, height) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    // RN's first frame reports no previous frame: 16 ms.
                    val dt = if (last < 0) 0.016 else (now - last) / 1e9
                    last = now
                    val e = scene.engine
                    e.frame(dt, width, height)
                    val ev = e.events
                    if (ev != 0) {
                        e.events = 0
                        if (ev and EVENT_DROPPED != 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (ev and EVENT_STROKED != 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (ev and EVENT_PRESSED != 0) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        if (ev and (EVENT_PRINT_START or EVENT_PRINT_DONE) != 0) printing = e.printT >= 0
                    }
                    scene.frame.intValue += 1
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A tray of marbling paint. Tap to drop paint, hold for rings, drag to comb." }
                .pointerInput(scene) {
                    // Gesture Handler's race of a pan (6 dp), a long press (0.3 s, 12 dp) and a tap
                    // (0.28 s, 10 dp), tracked by hand from one pointer.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density
                        val sx = down.position.x / d
                        val sy = down.position.y / d
                        val e = scene.engine
                        var panning = false
                        var dripping = false
                        var settled = false
                        var lastX = sx
                        var lastY = sy
                        var upTime = down.uptimeMillis
                        while (true) {
                            val waiting = !panning && !dripping && !settled
                            val event = if (waiting) {
                                val held = SystemClock.uptimeMillis() - down.uptimeMillis
                                withTimeoutOrNull(max(1L, 300L - held)) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                dripping = true
                                e.dripStart(sx.toDouble(), sy.toDouble())
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastX = change.position.x / d
                            lastY = change.position.y / d
                            upTime = change.uptimeMillis
                            if (!change.pressed) break
                            val dist = hypot(lastX - sx, lastY - sy)
                            if (dripping) {
                                // A long press that wanders too far ends.
                                if (dist > 12) {
                                    dripping = false
                                    settled = true
                                    e.dripEnd()
                                }
                            } else if (!panning && !settled && dist >= 6) {
                                panning = true
                                e.panStart(sx.toDouble(), sy.toDouble(), lastX.toDouble(), lastY.toDouble())
                            } else if (panning) {
                                e.panMove(lastX.toDouble(), lastY.toDouble())
                            }
                            if (panning) change.consume()
                        }
                        if (panning) {
                            e.panEnd()
                        } else if (dripping) {
                            e.dripEnd()
                        } else if (!settled) {
                            val dist = hypot(lastX - sx, lastY - sy)
                            if (upTime - down.uptimeMillis <= 280 && dist <= 10) e.tap(lastX.toDouble(), lastY.toDouble())
                        }
                    }
                },
        ) {
            scene.frame.intValue
            val sheetTop = safeTop + SHEET_TOP
            val sheetBottom = height.toFloat() - safeBottom - SHEET_BOTTOM
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.scale(density, density)
                scene.draw(nc, width.toFloat(), height.toFloat(), sheetTop, sheetBottom)
                nc.restore()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = (safeTop + 18).dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText(
                "Ebru",
                style = TextStyle(
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f * screen.density), 6f * screen.density),
                ),
            )
            BasicText(
                "Tap to drop · hold for rings · drag to comb",
                style = TextStyle(
                    color = Ink.copy(alpha = 0.78f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f * screen.density), 5f * screen.density),
                ),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = (safeBottom + 16).dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Controls(
                palette = palette,
                ink = ink,
                tool = tool,
                printing = printing,
                onInk = {
                    ink = it
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                onTool = {
                    tool = it
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                onPrint = { scene.engine.printButton() },
            )
        }
    }
}

@Composable
private fun Controls(
    palette: List<Color>,
    ink: Int,
    tool: Int,
    printing: Boolean,
    onInk: (Int) -> Unit,
    onTool: (Int) -> Unit,
    onPrint: () -> Unit,
) {
    Column(
        Modifier.alpha(if (printing) 0.5f else 1f),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            for (index in 1 until palette.size) {
                val on = index == ink
                Box(
                    Modifier
                        .size(30.dp)
                        .border(1.5.dp, if (on) Ink.copy(alpha = 0.9f) else Color.Transparent, CircleShape)
                        .clip(CircleShape)
                        .clickable(
                            enabled = !printing,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                        ) { onInk(index) }
                        .semantics {
                            contentDescription = "Paint $index"
                            selected = on
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .shadow(2.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                            .background(palette[index], CircleShape)
                            .border(
                                1.dp,
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.35f),
                                    0.5f to Color.Transparent,
                                ),
                                CircleShape,
                            ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // The RN bar is frosted glass; Compose has no backdrop blur, so a deeper tint stands in.
            Row(
                Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(14, 20, 32, (0.55f * 255).toInt()))
                    .padding(4.dp),
            ) {
                listOf("Stylus", "Comb").forEachIndexed { k, label ->
                    val on = tool == k
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) Ink.copy(alpha = 0.92f) else Color.Transparent)
                            .clickable(
                                enabled = !printing,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) { onTool(k) }
                            .semantics { selected = on }
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            label,
                            style = TextStyle(
                                color = if (on) MarbleGround else Ink.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
            val source = remember { MutableInteractionSource() }
            val down by source.collectIsPressedAsState()
            Box(
                Modifier
                    .scale(if (down) 0.97f else 1f)
                    .height(44.dp)
                    .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .background(PaperColor, RoundedCornerShape(22.dp))
                    .clickable(enabled = !printing, interactionSource = source, indication = null, role = Role.Button) { onPrint() }
                    .semantics { contentDescription = "Print the pattern" }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "Print",
                    style = TextStyle(color = MarbleGround, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
                )
            }
        }
    }
}

/** The engine and everything drawn from it, kept between frames. */
private class MarbleScene(auto: Boolean, reduced: Boolean, palette: List<Color>) {
    val engine = MarbleEngine(auto = auto, reduced = reduced, paints = palette.size)
    val frame = mutableIntStateOf(0)
    private val shaders = MarbleShaders()

    private val fills = palette.ifEmpty { DefaultPalette }.map { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it.toArgb() } }
    private val rims = palette.ifEmpty { DefaultPalette }.map {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0f
            color = marbleShade(it.toArgb(), 0.62)
            alpha = (0.85f * 255).toInt()
        }
    }

    // Each outline's path, with the outline revision it was built from.
    private val paths = HashMap<Int, android.graphics.Path>()
    private val pathRevs = HashMap<Int, Int>()
    private val spare = ArrayList<android.graphics.Path>()
    // The paint as last drawn, and the revision of the marble it shows; the paint taken up by the sheet.
    private var paint = Picture()
    private var paintRev = -1
    private var print: Picture? = null

    init {
        engine.onTakePrint = {
            engine.marble?.let { record(it) }
            print = paint
            paint = Picture()
            paintRev = -1
        }
    }

    /** The fill and the rim of palette colour [index], wrapped so a short palette never runs out. */
    private fun fillOf(index: Int) = fills[Math.floorMod(index, fills.size)]
    private fun rimOf(index: Int) = rims[Math.floorMod(index, rims.size)]

    /** The outline as a closed, smooth path: quadratic curves through the midpoints of its edges. */
    private fun outline(path: android.graphics.Path, blot: Blot) {
        val xs = blot.xs
        val ys = blot.ys
        val n = blot.n
        path.rewind()
        path.moveTo(((xs[n - 1] + xs[0]) / 2).toFloat(), ((ys[n - 1] + ys[0]) / 2).toFloat())
        for (i in 0 until n) {
            val j = if (i + 1 < n) i + 1 else 0
            path.quadTo(xs[i].toFloat(), ys[i].toFloat(), ((xs[i] + xs[j]) / 2).toFloat(), ((ys[i] + ys[j]) / 2).toFloat())
        }
        path.close()
    }

    /** Records the ground and every outline, oldest first, each with a fine darker rim. */
    private fun record(m: Marble) {
        if (paintRev == m.rev) return
        val live = HashSet<Int>()
        val canvas = paint.beginRecording(m.width.toInt() + 1, m.height.toInt() + 1)
        canvas.drawRect(0f, 0f, m.width.toFloat(), m.height.toFloat(), fillOf(m.ground))
        for (blot in m.blots) {
            if (blot.n < 3) continue
            live.add(blot.id)
            var path = paths[blot.id]
            if (path == null || pathRevs[blot.id] != blot.rev) {
                if (path == null) {
                    path = if (spare.isEmpty()) android.graphics.Path() else spare.removeAt(spare.size - 1)
                    paths[blot.id] = path
                }
                outline(path, blot)
                pathRevs[blot.id] = blot.rev
            }
            canvas.drawPath(path, fillOf(blot.color))
            canvas.drawPath(path, rimOf(blot.color))
        }
        paint.endRecording()
        paintRev = m.rev
        if (paths.size != live.size) {
            val gone = paths.keys.filter { it !in live }
            for (id in gone) {
                paths.remove(id)?.let { spare.add(it) }
                pathRevs.remove(id)
            }
        }
    }

    // Reused paints for the tool and the sheet.
    private val plain = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val layer = Paint()
    private val sheetPath = android.graphics.Path()
    private val restPath = android.graphics.Path()
    private val liftedPath = android.graphics.Path()
    private val flapPath = android.graphics.Path()
    private val mirror = Matrix()
    private val sx = FloatArray(4)
    private val sy = FloatArray(4)
    private val px = FloatArray(8)
    private val py = FloatArray(8)

    /** Sets a blur on [p] like Skia's MakeBlur(sigma): Android takes a radius, sigma = 0.57735·r + 0.5. */
    private fun blur(p: Paint, sigma: Float) {
        p.maskFilter = BlurMaskFilter(max(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
    }

    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, sheetTop: Float, sheetBottom: Float) {
        val e = engine
        val m = e.marble
        if (m == null) {
            canvas.drawRect(0f, 0f, width, height, fills[0])
            return
        }
        record(m)
        canvas.drawPicture(paint)

        // What the last print left behind on the size, fading.
        val left = e.residue
        val taken = print
        if (left > 0 && taken != null) {
            canvas.saveLayerAlpha(0f, 0f, width, height, (RESIDUE * left * 255).toInt())
            canvas.drawPicture(taken)
            canvas.restore()
        }

        // Light on the size: sheen, grain and a vignette. The sheen drifts once per loop, so a
        // recording of one loop joins up.
        shaders.update(width, height, (e.elapsed / LOOP * PI * 2).toFloat())
        canvas.drawRect(0f, 0f, width, height, shaders.water)

        // The tool.
        val a = e.toolAlpha.toFloat()
        if (a > 0.01f) {
            if (e.toolKind == 1) {
                drawComb(canvas, e.toolX.toFloat(), e.toolY.toFloat(), e.combAX, e.combAY, e.combSpacing.toFloat(), e.combSide.toInt(), a)
            } else {
                drawStylus(canvas, e.toolX.toFloat(), e.toolY.toFloat(), a)
            }
        }

        // The sheet.
        val p = e.printT
        if (p >= 0) drawSheet(canvas, p, width, height, sheetTop, sheetBottom)
    }

    private fun argb(a: Float, r: Int, g: Int, b: Int) = android.graphics.Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), r, g, b)

    /** The stylus, a steel pin seen from above, with its shadow and the meniscus round it. */
    private fun drawStylus(canvas: android.graphics.Canvas, x: Float, y: Float, alpha: Float) {
        shadow.color = argb(0.4f * alpha, 0, 0, 0)
        blur(shadow, 3.5f)
        canvas.drawCircle(x + 4, y + 7, 6f, shadow)
        stroke.strokeWidth = 1.2f
        stroke.shader = null
        stroke.color = argb(0.28f * alpha, 255, 255, 255)
        canvas.drawCircle(x, y, 10f, stroke)
        plain.color = android.graphics.Color.BLACK
        plain.shader = RadialGradient(
            x - 2, y - 2, 8f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFB9BEC6.toInt(), 0xFF5D636D.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        plain.alpha = (alpha * 255).toInt()
        canvas.drawCircle(x, y, 5.5f, plain)
        plain.shader = null
    }

    /** The comb: a walnut rod across the tray along its axis, its steel tines in the size, and its shadow. */
    private fun drawComb(canvas: android.graphics.Canvas, x: Float, y: Float, ax: Double, ay: Double, spacing: Float, side: Int, alpha: Float) {
        val reach = spacing * (side + 0.5f)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate((atan2(ay, ax) * 180 / PI).toFloat())
        shadow.color = argb(0.42f * alpha, 0, 0, 0)
        blur(shadow, 5f)
        canvas.drawRect(-reach + 5, -2f, -reach + 5 + reach * 2, 10f, shadow)
        stroke.strokeWidth = 1f
        stroke.shader = null
        stroke.color = argb(0.22f * alpha, 255, 255, 255)
        for (j in -side..side) canvas.drawCircle(j * spacing, 0f, 4.5f, stroke)
        plain.color = android.graphics.Color.BLACK
        plain.shader = LinearGradient(
            0f, -5f, 0f, 5f,
            intArrayOf(0xFF8A5D38.toInt(), 0xFF5B3A1F.toInt(), 0xFF2F1D0E.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        plain.alpha = (alpha * 255).toInt()
        canvas.drawRoundRect(-reach, -9f, reach, -1f, 3f, 3f, plain)
        stroke.color = argb(0.35f * alpha, 255, 228, 190)
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(-reach + 4, -7.6f, reach - 4, -7.6f, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
        // Each tine catches the light on its own upper left.
        for (j in -side..side) {
            val tx = j * spacing
            plain.shader = RadialGradient(tx - 0.6f, -0.6f, 2.6f, 0xFFFFFFFF.toInt(), 0xFF8C929B.toInt(), Shader.TileMode.CLAMP)
            plain.alpha = (alpha * 255).toInt()
            canvas.drawCircle(tx, 0f, 2.2f, plain)
        }
        plain.shader = null
        canvas.restore()
    }

    /**
     * The sheet's rectangle clipped to one side of the fold line: the part already lifted (s < f,
     * where s runs along `d` from the lower right corner) or the part still down. With `mirror`, the
     * lifted part is folded over the line, which is where the flap lies.
     */
    private fun sheetPart(
        out: android.graphics.Path, l: Float, t: Float, r: Float, b: Float, dx: Float, dy: Float, f: Float,
        lifted: Boolean, mirror: Boolean,
    ): Boolean {
        sx[0] = r; sx[1] = l; sx[2] = l; sx[3] = r
        sy[0] = b; sy[1] = b; sy[2] = t; sy[3] = t
        var count = 0
        for (k in 0 until 4) {
            val j = (k + 1) % 4
            val sa = (sx[k] - r) * dx + (sy[k] - b) * dy - f
            val sc = (sx[j] - r) * dx + (sy[j] - b) * dy - f
            val a = if (lifted) -sa else sa
            val c = if (lifted) -sc else sc
            if (a >= 0) {
                px[count] = sx[k]
                py[count] = sy[k]
                count += 1
            }
            if ((a >= 0) != (c >= 0)) {
                val u = a / (a - c)
                px[count] = sx[k] + (sx[j] - sx[k]) * u
                py[count] = sy[k] + (sy[j] - sy[k]) * u
                count += 1
            }
        }
        if (count < 3) return false
        out.rewind()
        for (k in 0 until count) {
            var x = px[k]
            var y = py[k]
            if (mirror) {
                // Reflect across the fold: s' = 2f − s.
                val s = (x - r) * dx + (y - b) * dy
                x += 2 * (f - s) * dx
                y += 2 * (f - s) * dy
            }
            if (k == 0) out.moveTo(x, y) else out.lineTo(x, y)
        }
        out.close()
        return true
    }

    /** The fold's travel along the sheet's diagonal (0…1+) at `p` = 0…1 through the peel, with a pause to show the print. */
    private fun peelAt(p: Double): Double {
        if (p < 0.42) return 0.5 * marbleEaseInOut(p / 0.42)
        if (p < 0.7) return 0.5 + 0.05 * ((p - 0.42) / 0.28)
        val k = (p - 0.7) / 0.3
        return 0.55 + 0.65 * k * k
    }

    /** Fills a path with the paper and its grain. */
    private fun drawPaper(canvas: android.graphics.Canvas, clip: android.graphics.Path, width: Float, height: Float) {
        canvas.save()
        canvas.clipPath(clip)
        plain.shader = null
        plain.color = PaperColor.toArgb()
        canvas.drawRect(0f, 0f, width, height, plain)
        canvas.drawRect(0f, 0f, width, height, shaders.paper)
        canvas.restore()
    }

    private fun drawSheet(canvas: android.graphics.Canvas, p: Double, width: Float, height: Float, t: Float, b: Float) {
        val l = SHEET_SIDE
        val r = width - SHEET_SIDE
        if (p < LAY) {
            // Coming down: from a little above and larger, its shadow tightening as it lands.
            val k = marbleEaseInOut(p / LAY).toFloat()
            val lift = if (engine.reduced) 0f else 1 - k
            val scale = 1 + 0.06f * lift
            val cx = (l + r) / 2
            val cy = (t + b) / 2
            sheetPath.rewind()
            sheetPath.addRoundRect(l, t, r, b, 2f, 2f, android.graphics.Path.Direction.CW)
            canvas.save()
            canvas.translate(cx, cy - 26 * lift)
            canvas.scale(scale, scale)
            canvas.translate(-cx, -cy)
            shadow.color = argb(0.45f * min(1f, k * 2), 0, 0, 0)
            blur(shadow, 2 + 18 * lift)
            canvas.save()
            canvas.translate(0f, 3 + 34 * lift)
            canvas.drawPath(sheetPath, shadow)
            canvas.restore()
            canvas.saveLayerAlpha(null, (min(1f, k * 2.5f) * 255).toInt())
            drawPaper(canvas, sheetPath, width, height)
            canvas.restore()
            canvas.restore()
            return
        }
        val taken = print
        val soak = min(1.0, (p - LAY) / SOAK).toFloat()
        val peel = max(0.0, (p - LAY - SOAK) / PEEL)
        val wx = l - r
        val wy = t - b
        val diag = max(1f, hypot(wx, wy))
        val dx = wx / diag
        val dy = wy / diag
        if (engine.reduced) {
            // No peel: the sheet fades to its print, then away.
            val show = min(1.0, peel * 3).toFloat()
            val gone = max(0.0, (peel - 0.75) / 0.25).toFloat()
            sheetPath.rewind()
            sheetPath.addRect(l, t, r, b, android.graphics.Path.Direction.CW)
            canvas.saveLayerAlpha(null, ((1 - gone) * 255).toInt())
            drawPaper(canvas, sheetPath, width, height)
            if (taken != null) {
                canvas.save()
                canvas.clipPath(sheetPath)
                canvas.saveLayerAlpha(null, (max(SOAK_GHOST.toFloat() * soak, show * 0.92f) * 255).toInt())
                canvas.translate(l + r, 0f)
                canvas.scale(-1f, 1f)
                canvas.drawPicture(taken)
                canvas.restore()
                canvas.drawRect(0f, 0f, width, height, shaders.paper)
                canvas.restore()
            }
            canvas.restore()
            return
        }
        val f = (peelAt(peel) * diag).toFloat()
        // Under the lifting edge, a soft shadow on the size.
        val hasRest = sheetPart(restPath, l, t, r, b, dx, dy, f, lifted = false, mirror = false)
        val hasLifted = sheetPart(liftedPath, l, t, r, b, dx, dy, f, lifted = true, mirror = false)
        val fx = r + dx * f
        val fy = b + dy * f
        if (hasLifted && f > 0) {
            canvas.save()
            canvas.clipPath(liftedPath)
            plain.color = android.graphics.Color.BLACK
            plain.shader = LinearGradient(fx, fy, fx - dx * 46, fy - dy * 46, argb(0.38f, 0, 0, 0), 0, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width, height, plain)
            plain.shader = null
            canvas.restore()
        }
        // The sheet still lying on the size: its back, the paint showing through where it is wet.
        if (hasRest) {
            drawPaper(canvas, restPath, width, height)
            if (taken != null) {
                canvas.save()
                canvas.clipPath(restPath)
                canvas.saveLayerAlpha(null, (SOAK_GHOST.toFloat() * soak * 255).toInt())
                canvas.drawPicture(taken)
                canvas.restore()
                canvas.restore()
            }
        }
        // The flap: the peeled part folded over the fold line, its printed face up.
        if (hasLifted && f > 0) {
            // Reflection across the fold line through F = corner + f·d, normal d.
            val fd = fx * dx + fy * dy
            mirror.setValues(
                floatArrayOf(
                    1 - 2 * dx * dx, -2 * dx * dy, 2 * fd * dx,
                    -2 * dx * dy, 1 - 2 * dy * dy, 2 * fd * dy,
                    0f, 0f, 1f,
                ),
            )
            val flap = if (sheetPart(flapPath, l, t, r, b, dx, dy, f, lifted = true, mirror = true)) flapPath else liftedPath
            // Its shadow on the sheet below.
            shadow.color = argb(0.32f, 40, 26, 10)
            blur(shadow, 7f)
            canvas.save()
            canvas.translate(dx * 7 + 2, dy * 7 + 5)
            canvas.drawPath(flap, shadow)
            canvas.restore()
            canvas.save()
            canvas.clipPath(flap)
            plain.shader = null
            plain.color = PaperColor.toArgb()
            canvas.drawRect(0f, 0f, width, height, plain)
            if (taken != null) {
                canvas.save()
                canvas.concat(mirror)
                canvas.drawPicture(taken)
                canvas.restore()
            }
            // The paper's tooth through the print, and the light rolling over the fold.
            plain.color = argb(0.10f, 242, 235, 220)
            canvas.drawRect(0f, 0f, width, height, plain)
            canvas.drawRect(0f, 0f, width, height, shaders.paper)
            plain.color = android.graphics.Color.BLACK
            plain.shader = LinearGradient(
                fx, fy, fx + dx * 60, fy + dy * 60,
                intArrayOf(argb(0.34f, 255, 255, 255), argb(0.06f, 255, 255, 255), argb(0.12f, 0, 0, 0)),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width, height, plain)
            plain.shader = null
            canvas.restore()
        }
    }
}
