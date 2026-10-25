package com.ngocdevv.nativecook.blocks.onboarding.paperplanesteps

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.RoundRect
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Paper Plane Steps — onboarding for "Postcard", an app for slow letters to faraway friends. Each
 step is a sheet of writing paper; Next folds it into a paper plane in four visible folds and
 throws it, and the next sheet slides up. The loop is in PaperPlaneStepsMotion.kt (LOOP = 10 s),
 the folds in PaperPlaneStepsGeometry.kt.

 Touch: drag up on the sheet to fold it (60 dp of drag per fold, the flaps following the finger).
 Let go past the second fold, or flick up, and it finishes folding (3–8 folds a second with the
 flick) and flies (0.6–1.2 s, faster for a harder flick); short of it, it unfolds again. Next
 plays the same at 0.32 s a fold. A tick per finished fold and a light click on the throw. 2 s
 after the last touch the first sheet slides back up (0.8 s) and the loop restarts. Reduced
 motion: no loop; Next cross-fades to the next sheet (the plane is not thrown).
*/

private val Ink = Color(0xFFF4EFE6)
private val InkSoft = Color(236 / 255f, 230 / 255f, 220 / 255f, 0.66f)
private val Accent = Color(0xFFE8835A)
internal val PlaneBackground = Color(0xFF1B2236)

/** What is drawn (from the loop, or from the controller under touch) and the controller itself. */
private class PlaneState {
    var sheet = 0
    var fold = 0f
    var tuck = 0f
    var fly = -1f
    var inc = 1
    var incP = -1f
    var dot = 0f
    var clock = 0f
    var mode = 0 // 0 loop, 1 dragging, 2 controller
    var run = 0 // 0 idle, 1 folding, 2 flying, 4 unfolding, 5 returning
    var rate = 3f
    var flyDur = 1f
    var idle = 0f
    var startFold = 0f
    var grab = false
    var dragY = 0f
    val script = FloatArray(7)
    val frame = mutableFloatStateOf(0f)
    val wake = mutableIntStateOf(0)
    /** The step whose button label shows: changes only when a sheet lands. */
    val step = mutableIntStateOf(0)

    fun applyScript() {
        sheet = script[0].toInt()
        fold = script[1]
        tuck = script[2]
        fly = script[3]
        inc = script[4].toInt()
        incP = script[5]
        dot = script[6]
    }

    /** Hands the loop's frame to the controller, mid-fold or mid-flight as it may be. */
    fun takeOver(auto: Boolean) {
        idle = 0f
        if (mode != 0) return
        val t = clock
        planeScript(if (auto) t else 0f, script)
        val second = t >= 5.2f && t < 9.4f
        rate = if (second) 1 / 0.3f else 1 / 0.45f
        flyDur = if (second) 1.0f else 1.2f
        run = if (script[5] >= 0f || script[3] >= 0f) 2 else if (script[1] > 0f) 1 else 0
        mode = 2
    }
}

private fun step(s: PlaneState, dt: Float, auto: Boolean, feel: (Int) -> Unit) {
    val m = s.mode
    if (m == 0) {
        if (auto) s.clock = (s.clock + dt) % LOOP
        planeScript(if (auto) s.clock else 0f, s.script)
        s.applyScript()
        return
    }
    val r = s.run
    if (r == 1) {
        val before = s.fold
        val next = min(4f, before + dt * s.rate)
        s.fold = next
        if (floor(next) > floor(before)) feel(0)
        if (next >= 4f) {
            s.run = 2
            s.fly = -1f
        }
    } else if (r == 2) {
        s.tuck = min(1f, s.tuck + dt / 0.2f)
        if (s.tuck > 0.6f || s.fly >= 0f) {
            if (s.fly < 0f) feel(1)
            s.fly = min(1f, max(0f, s.fly) + dt / s.flyDur)
        }
        if (s.fly > 0.85f && s.incP < 0f) {
            s.inc = (s.sheet + 1) % PLANE_STEPS
            s.incP = 0f
        }
    } else if (r == 4) {
        s.fold = max(0f, s.fold - dt * 5)
        if (s.fold <= 0f) s.run = 0
    }
    if (s.incP >= 0f) {
        s.incP = min(1f, s.incP + dt / 0.8f)
        if (s.incP >= 1f) {
            val back = s.run == 5
            s.sheet = s.inc
            s.fold = 0f
            s.tuck = 0f
            s.fly = -1f
            s.incP = -1f
            s.run = 0
            s.idle = 0f
            s.step.intValue = s.inc
            if (back) {
                s.mode = 0
                s.clock = 0f
                return
            }
        }
    }
    val target = if (s.incP > 0.1f) s.inc else s.sheet
    s.dot += (target - s.dot) * min(1f, dt * 10)
    if (m == 2 && s.run == 0) {
        s.idle += dt
        if (s.idle > PLANE_IDLE_BEFORE_RETURN) {
            if (s.sheet == 0 && s.fold == 0f) {
                s.mode = 0
                s.clock = 0f
            } else {
                s.run = 5
                s.inc = 0
                s.incP = 0f
            }
        }
    }
}

/** The atlas paint, meshes, shadows and overlays, drawn every frame. */
private class PlanePainter(atlas: Bitmap, private val scale: Float) {
    private val shader = BitmapShader(atlas, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(1 / scale, 1 / scale) })
    }
    val paper = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = this@PlanePainter.shader }
    private val tri = Path()
    private val matrix = Matrix()
    private val src = FloatArray(6)
    private val dst = FloatArray(6)
    private val hw = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (blurOk) maskFilter = BlurMaskFilter(sigmaToRadius(14f), BlurMaskFilter.Blur.NORMAL)
    }
    val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(5f, 8f), 0f)
    }
    val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    val edges = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.argb(71, 90, 70, 48)
    }
    val wing = Paint(Paint.ANTI_ALIAS_FLAG)
    val trailPath = Path()
    val mesh = PlaneMesh()
    val incoming = PlaneMesh()
    val xf = PlaneXf()
    val inXf = PlaneXf()
    val flight = FloatArray(5)
    val pt = FloatArray(2)

    /** Textured triangles: one drawVertices call from Android 10, one affine-mapped triangle each before it. */
    fun drawMesh(c: android.graphics.Canvas, verts: PlaneBuf, texs: PlaneBuf, alpha: Float) {
        if (verts.size < 6) return
        paper.alpha = (pClamp(alpha, 0f, 1f) * 255).roundToInt()
        if (hw) {
            c.drawVertices(android.graphics.Canvas.VertexMode.TRIANGLES, verts.size, verts.data, 0, texs.data, 0, null, 0, null, 0, 0, paper)
            return
        }
        var i = 0
        while (i + 6 <= verts.size) {
            for (k in 0 until 6) {
                src[k] = texs.data[i + k]
                dst[k] = verts.data[i + k]
            }
            if (matrix.setPolyToPoly(src, 0, dst, 0, 3)) {
                matrix.preScale(1 / scale, 1 / scale)
                paper.shader.setLocalMatrix(matrix)
                tri.rewind()
                tri.moveTo(dst[0], dst[1])
                tri.lineTo(dst[2], dst[3])
                tri.lineTo(dst[4], dst[5])
                tri.close()
                c.drawPath(tri, paper)
            }
            i += 6
        }
        paper.shader.setLocalMatrix(Matrix().apply { setScale(1 / scale, 1 / scale) })
    }
}

/**
 * Onboarding for a slow-letters app: each step is a sheet of writing paper that folds into a paper
 * plane in four visible folds and is thrown, while the next sheet slides up.
 *
 * @param autoPlay plays the 10 s demo: fold and throw sheet 1, sheet 2 arrives, fold and throw it, back to 1.
 */
@Composable
fun PaperPlaneSteps(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val state = remember { PlaneState() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = safeTop + 16
        val sheetY = top + 116
        // The sheet is as large as the screen allows between the title and the dots above Next.
        val ctaTop = height - safeBottom - 16 - 56
        val SW = min(min(width - 48, 340f), (ctaTop - 44 - sheetY) / 1.32f).roundToInt().toFloat()
        val SH = (SW * 1.32f).roundToInt().toFloat()
        val cx = width / 2
        val cy = sheetY + SH / 2
        val dotsY = sheetY + SH + 22

        val plan = remember(SW, SH) { makePlanePlan(SW, SH) }
        val flat = remember(SW, SH) { listOf(PlaneFace(floatArrayOf(0f, 0f, SW, 0f, SW, SH, 0f, SH), floatArrayOf(0f, 0f, SW, 0f, SW, SH, 0f, SH), 0, 0)) }
        val scale = min(2f, screen.density)
        val painter by produceState<PlanePainter?>(null, SW, SH, scale) {
            value = withContext(Dispatchers.Default) { PlanePainter(buildPlanePaper(SW, SH, scale), scale) }
        }

        LaunchedEffect(auto) {
            state.clock = 0f
            state.mode = 0
        }
        val feel: (Int) -> Unit = { kind ->
            view.performHapticFeedback(if (kind == 0) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.CONTEXT_CLICK)
        }
        val wake = state.wake.intValue
        LaunchedEffect(auto, wake) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60 else min((now - last) / 1e9f, 1f / 20)
                    last = now
                    step(state, dt, auto, feel)
                    state.frame.floatValue += dt
                }
                if (!auto && state.mode == 0) break
            }
        }

        PlaneBackdrop(Modifier.fillMaxSize())

        val p0 = painter
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            state.frame.floatValue
            val pp = p0 ?: return@Canvas
            scale(density, density, pivot = Offset.Zero) {
                drawIntoCanvas { cv -> drawPlane(cv.nativeCanvas, pp, state, plan, flat, reduced, cx, cy, width, height, sheetY, SW, SH) }
            }
        }

        Column(
            Modifier.offset(x = 24.dp, y = top.dp).width((width - 48).dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(16.dp, 12.dp).graphicsLayer { rotationZ = -8f }.background(Accent, RoundedCornerShape(2.dp)))
                    BasicText("Postcard", style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp))
                }
                BasicText("Skip", style = TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium))
            }
            BasicText(
                "Send notes to faraway friends",
                style = TextStyle(color = Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            )
        }

        // Drag up on the sheet to fold it.
        Box(
            Modifier
                .offset(x = (cx - SW / 2).dp, y = sheetY.dp)
                .size(SW.dp, SH.dp)
                .semantics { contentDescription = "Onboarding letter. Swipe up to fold it into a paper plane and send it." }
                .pointerInput(state, auto, reduced) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            state.takeOver(auto)
                            state.grab = state.run == 0 && !reduced
                            state.wake.intValue += 1
                            if (state.grab) {
                                state.mode = 1
                                state.startFold = state.fold
                                state.dragY = 0f
                            }
                        },
                        onDragEnd = {
                            if (state.grab) {
                                val vy = tracker.calculateVelocity().y / density
                                if (state.fold >= 2f || vy < -900f) {
                                    state.rate = pClamp(-vy / 300, 3f, 8f)
                                    state.flyDur = pClamp(900 / max(500f, -vy), 0.6f, 1.2f)
                                    state.run = 1
                                } else {
                                    state.run = 4
                                }
                                state.grab = false
                                state.mode = 2
                                state.idle = 0f
                            }
                        },
                        onDragCancel = {
                            if (state.grab) {
                                state.run = 4
                                state.grab = false
                                state.mode = 2
                                state.idle = 0f
                            }
                        },
                        onVerticalDrag = { change, amount ->
                            if (state.grab) {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                state.dragY += amount / density
                                val before = state.fold
                                val f = pClamp(state.startFold - state.dragY / 60, 0f, 3.999f)
                                state.fold = f
                                if (floor(f) > floor(before)) feel(0)
                            }
                        },
                    )
                },
        )

        // The step dots, the accent pill sliding between them.
        Box(Modifier.offset(x = (cx - 23).dp, y = dotsY.dp).size(46.dp, 8.dp)) {
            for (i in 0 until 3) {
                Box(Modifier.offset(x = (i * 16).dp).size(8.dp).background(Color(244 / 255f, 239 / 255f, 230 / 255f, 0.25f), RoundedCornerShape(4.dp)))
            }
            Box(
                Modifier
                    .offset(x = (-7).dp)
                    .size(22.dp, 8.dp)
                    .graphicsLayer {
                        state.frame.floatValue
                        translationX = state.dot * 16.dp.toPx()
                    }
                    .background(Accent, RoundedCornerShape(4.dp)),
            )
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 16).dp)
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    val k = if (pressed) 0.97f else 1f
                    scaleX = k
                    scaleY = k
                }
                .cssBoxShadow(Color(40 / 255f, 16 / 255f, 6 / 255f, 0.4f), 1.dp, 2.dp, 28.dp)
                .cssBoxShadow(Color(232 / 255f, 131 / 255f, 90 / 255f, 0.28f), 12.dp, 28.dp, 28.dp)
                .drawBehind {
                    val r = CornerRadius(size.height / 2)
                    val pill = androidx.compose.ui.graphics.Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
                    // The inset highlight along the top edge, then the accent fill one pixel lower.
                    clipPath(pill) {
                        drawRect(Color(255 / 255f, 226 / 255f, 206 / 255f, 0.5f))
                        translate(top = 1.dp.toPx()) { drawRoundRect(Accent, cornerRadius = r) }
                    }
                }
                .clickable(interaction, indication = null, role = Role.Button) {
                    next(state, auto, reduced)
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                if (state.step.intValue == PLANE_STEPS - 1) "Start writing" else "Next",
                style = TextStyle(color = Color(0xFF2A140B), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }
    }
}

/** Next: fold and throw the current sheet (a cross-fade under reduced motion). */
private fun next(s: PlaneState, auto: Boolean, reduced: Boolean) {
    if (s.mode == 0) {
        planeScript(if (auto) s.clock else 0f, s.script)
        if (s.script[1] > 0f || s.script[3] >= 0f || s.script[5] >= 0f) return
        s.mode = 2
    }
    s.idle = 0f
    s.wake.intValue += 1
    if (s.run != 0) return
    if (reduced) {
        s.inc = (s.sheet + 1) % PLANE_STEPS
        s.incP = 0f
        return
    }
    s.rate = 1 / 0.32f
    s.flyDur = 1.0f
    s.run = 1
}

private fun drawPlane(
    c: android.graphics.Canvas,
    pp: PlanePainter,
    s: PlaneState,
    plan: PlanePlan,
    flat: List<PlaneFace>,
    reduced: Boolean,
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    sheetY: Float,
    sw: Float,
    sh: Float,
) {
    val f = s.fly
    // The paper (or the plane) this frame.
    val xf = pp.xf
    if (f >= 0f) {
        planeFlight(f, cx, cy, width, pp.flight)
        xf.set(pp.flight[0], pp.flight[1], sw / 2, sh / 2, 0.62f * cos(pp.flight[4]), pp.flight[2], pp.flight[3], pp.flight[4])
    } else {
        xf.set(cx, cy, sw / 2, sh / 2, 1 - 0.38f * s.tuck, 0f, 1 - 0.14f * s.tuck, 0f)
    }
    val mesh = pp.mesh
    if (f >= 1f) mesh.clear() else planePaperMesh(mesh, plan, plan.final, s.fold, s.sheet, xf)
    val incoming = pp.incoming
    val p = s.incP
    if (p < 0f) {
        incoming.clear()
    } else {
        val e = if (reduced) 1f else pEaseOutBack(p)
        pp.inXf.set(cx, cy + (1 - e) * (height - sheetY + 40), sw / 2, sh / 2, 1f, (1 - e) * -0.07f, 1f, 0f)
        planePaperMesh(incoming, plan, flat, 4f, s.inc, pp.inXf)
    }
    val opacityIn = if (reduced) pClamp(p * 2, 0f, 1f) else 1f
    val opacityPaper = if (reduced && p >= 0f) 1 - pClamp(p * 2, 0f, 1f) else 1f

    // The incoming sheet and its shadow.
    if (!incoming.empty) {
        if (pp.blurOk) {
            pp.shadow.color = android.graphics.Color.argb((0.5f * opacityIn * 255).roundToInt(), 6, 10, 24)
            c.save()
            c.translate(4f, 10f)
            c.drawPath(incoming.sh, pp.shadow)
            c.restore()
        }
        pp.drawMesh(c, incoming.sv, incoming.st, opacityIn)
    }

    // The dashed trail behind the plane.
    if (f > 0.02f) {
        val trail = pp.trailPath
        trail.rewind()
        for (i in 0..28) {
            planeFlight(f * i / 28, cx, cy, width, pp.flight)
            if (i == 0) trail.moveTo(pp.flight[0], pp.flight[1]) else trail.lineTo(pp.flight[0], pp.flight[1])
        }
        val op = 0.6f * (1 - pSeg(f, 0.7f, 1f)) * pSeg(f, 0.02f, 0.12f)
        pp.trail.color = android.graphics.Color.argb((op * 255).roundToInt(), 244, 239, 230)
        c.drawPath(trail, pp.trail)
    }
    if (mesh.empty) return

    // The paper's shadow, lifted with the flight.
    if (pp.blurOk) {
        val alt = if (f >= 0f) sin(PI.toFloat() * min(1f, f * 1.4f)) * 50 else 0f
        val op = if (f >= 0f) 0.5f * (1 - pSeg(f, 0.2f, 0.6f)) else 0.5f
        pp.shadow.color = android.graphics.Color.argb((op * 255).roundToInt(), 6, 10, 24)
        c.save()
        c.translate(4 + alt * 0.4f, 10 + alt)
        c.drawPath(mesh.sh, pp.shadow)
        c.restore()
    }

    val layer = opacityPaper < 0.999f
    if (layer) c.saveLayerAlpha(0f, 0f, width, height, (opacityPaper * 255).roundToInt())
    pp.drawMesh(c, mesh.sv, mesh.st, 1f)
    pp.drawMesh(c, mesh.mv, mesh.mt, 1f)
    // Shading laid over the image: the turning flap by its angle, the flying plane's wings by its bank.
    pp.tint.shader = null
    pp.tint.color = planeTint(mesh.flap)
    c.drawPath(mesh.mpoly, pp.tint)
    // The folded edges, faint, so the stacked layers read.
    c.drawPath(mesh.all, pp.edges)
    if (xf.bank > 0.01f) {
        planeScreenOf(xf, 0f, sh * 0.5f, 0f, pp.pt)
        val fx = pp.pt[0]
        val fy = pp.pt[1]
        planeScreenOf(xf, sw, sh * 0.5f, 0f, pp.pt)
        var tx = pp.pt[0]
        var ty = pp.pt[1]
        if (hypot(tx - fx, ty - fy) < 0.5f) {
            tx = fx + 0.5f
            ty = fy
        }
        val b = xf.bank
        pp.wing.color = android.graphics.Color.BLACK
        pp.wing.shader = LinearGradient(
            fx, fy, tx, ty,
            intArrayOf(
                android.graphics.Color.argb((0.22f * b * 255).roundToInt(), 255, 250, 236),
                android.graphics.Color.argb(0, 255, 250, 236),
                android.graphics.Color.argb((0.34f * b * 255).roundToInt(), 22, 20, 38),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(mesh.all, pp.wing)
    }
    if (layer) c.restore()
}

/** The dusk screen behind the paper: the AGSL backdrop from API 33, gradients below it. */
@Composable
private fun PlaneBackdrop(modifier: Modifier) {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(PLANE_BACKDROP_AGSL) }.getOrNull() else null
    }
    Box(
        modifier.drawWithCache {
            val w = size.width / density
            val h = size.height / density
            val brush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                shader.setFloatUniform("size", w, h)
                ShaderBrush(shader)
            } else {
                null
            }
            val base = Brush.verticalGradient(
                0f to Color(0.082f, 0.106f, 0.188f), 0.5f to Color(0.118f, 0.137f, 0.224f), 1f to Color(0.165f, 0.149f, 0.212f),
                startY = 0f, endY = h,
            )
            val warm = Brush.radialGradient(listOf(Color(0.16f, 0.08f, 0.05f), Color.Transparent), Offset(w * 0.95f, h * 0.08f), w * 0.6f)
            val vignette = Brush.radialGradient(
                0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f),
                center = Offset(w / 2, h / 2), radius = max(w, h) * 0.62f,
            )
            onDrawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    if (brush != null) {
                        drawRect(brush, Offset.Zero, Size(w, h))
                    } else {
                        drawRect(base, Offset.Zero, Size(w, h))
                        drawRect(warm, Offset.Zero, Size(w, h), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                        drawRect(vignette, Offset.Zero, Size(w, h))
                    }
                }
            }
        },
    )
}
