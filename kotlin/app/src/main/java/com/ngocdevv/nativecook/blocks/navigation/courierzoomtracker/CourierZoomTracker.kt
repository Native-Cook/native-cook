package com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.ngocdevv.nativecook.blocks.navigation.courierzoomtracker.CourierMotion as M
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The film's period in seconds (RN `LOOP`, Swift `CourierZoomTracker.loop`). */
const val COURIER_ZOOM_TRACKER_LOOP = M.LOOP

private val Ink = Color(0xFF16181D)
private val Mute = Color(0xFF6E727B)
private val Body = Color(0xFF3C4049)
private val Blue = Color(0xFF2F6BFF)
private val Green = Color(0xFF1F9D55)
private val ShadowInk = Color(0xFF0A1A40)
private val Land = Color(CourierCity.Palette.LAND)

/**
 * A 19-second film of a parcel tracker. The map frames the courier's van and the customer's door together, so it zooms
 * in by itself as the van gets closer: the whole city, the district, the street (where it turns north-up and leans into
 * 3D, buildings raised from their footprints), and for the last 100 m right in to the front door, where the van parks
 * and the panel says Arrived. A ghost finger drags the map to look ahead and taps locate to follow the van again, then
 * taps Call ("Calling Marco…") and Message (a quick reply is sent); the camera pulls back out to the city.
 *
 * Touch: drag and pinch the map to take over; locate follows the van again; Call and Message open the system
 * confirmation dialogs. 2 s after the last touch the screen eases back to the poster and the film starts again.
 * Remove animations (or `autoPlay` off) shows the poster and leaves the map to the finger.
 */
@Composable
fun CourierZoomTracker(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = true)
    val reduce = rememberLiveReducedMotion()
    val film = autoPlay && !reduce
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()
    val view = LocalView.current
    val ctl = remember { CourierController() }
    ctl.film = film
    ctl.reduce = reduce
    ctl.view = view
    val renderer = remember { CourierMapRenderer() }
    DisposableEffect(renderer) { onDispose { renderer.release() } }

    // build the city before the clock starts
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { CourierCity.prepare() }
        val now = System.nanoTime()
        ctl.nanos.longValue = now
        ctl.touch.origin = now / 1e9
        ctl.ready = true
    }
    val running = ctl.ready && (film || ctl.awake)
    LaunchedEffect(running) {
        if (running) while (true) withFrameNanos { ctl.nanos.longValue = it }
    }
    LaunchedEffect(ctl.awake) {
        while (ctl.awake) {
            delay(1000)
            val quiet = ctl.now() - ctl.touch.lastActivity > 2.5
            if (quiet && !ctl.touch.panning && !ctl.touch.pinching) ctl.awake = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Land)) {
        val g = remember(maxWidth, maxHeight, top, bottom) {
            CourierGeometry(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottom)
        }
        ctl.g = g

        Canvas(Modifier.fillMaxSize()) {
            if (!ctl.ready) return@Canvas
            val f = ctl.frame()
            val cam = M.screenCam(f.cam, g.lay)
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                renderer.draw(nc, cam, g.lay, f.vanD, f.vanA, f.travEnd, f.travA, f.doorPulse)
                nc.restore()
            }
        }
        CourierPins(ctl, g)
        Box(
            Modifier
                .size(g.width.dp, g.panelTop.dp)
                .pointerInput(ctl) { courierMapGestures(ctl) },
        )
        CourierToast(ctl, g, calling = false)
        CourierToast(ctl, g, calling = true)
        CourierLocate(ctl, g)
        CourierPanel(ctl, g)
        Canvas(Modifier.fillMaxSize()) {
            if (!ctl.ready) return@Canvas
            val f = ctl.frame()
            val fg = M.filmFinger(f.t, g.lay, g.buttons)
            val vis = fg.vis * M.clamp01(f.live)
            if (fg.kind != 0 && vis >= 0.004) {
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.scale(density.density, density.density)
                    CourierMapRenderer.finger(nc, ctl.fingerPaint, fg.x, fg.y, vis, fg.press)
                    nc.restore()
                }
            }
        }
    }

    when (ctl.dialog) {
        1 -> AlertDialog(
            onDismissRequest = ctl::onDismiss,
            title = { Text("Call Marco?") },
            confirmButton = { TextButton(onClick = ctl::onConfirmCall) { Text("Call $COURIER_PHONE") } },
            dismissButton = { TextButton(onClick = ctl::onDismiss) { Text("Cancel") } },
        )
        2 -> AlertDialog(
            onDismissRequest = ctl::onDismiss,
            title = { Text("Quick message to Marco") },
            text = {
                Column {
                    for (r in COURIER_QUICK_REPLIES) {
                        TextButton(onClick = { ctl.onSend(r) }) { Text(r, fontSize = 16.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = ctl::onDismiss) { Text("Cancel") } },
        )
    }
}

/** Holds the touch state, the frame clock and the screen geometry; everything a frame or a handler reads. */
internal class CourierController {
    val touch = CourierTouch()
    var g = CourierGeometry(411.0, 914.0, 24.0, 24.0)
    var film = true
    var reduce = false
    var view: View? = null
    val nanos = mutableLongStateOf(System.nanoTime())
    var ready by mutableStateOf(false)
    var awake by mutableStateOf(false)
    private var dialogState = mutableIntStateOf(0)
    var dialog: Int
        get() = dialogState.intValue
        set(v) {
            dialogState.intValue = v
            touch.dialog = v
        }
    val fingerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    private var cacheAt = Long.MIN_VALUE
    private var cacheG: CourierGeometry? = null
    private var cacheRev = -1
    private var rev = 0
    private var cached: CourierFrame? = null

    fun now() = System.nanoTime() / 1e9

    /** The frame at the clock's time (reads the clock state, so call it only from draw / layer lambdas). */
    fun frame(): CourierFrame {
        val n = nanos.longValue
        val c = cached
        if (c != null && n == cacheAt && g === cacheG && rev == cacheRev) return c
        val f = touch.frame(n / 1e9, film, g)
        cached = f
        cacheAt = n
        cacheG = g
        cacheRev = rev
        return f
    }

    private fun changed() {
        rev++
        if (!film) {
            awake = true
            nanos.longValue = System.nanoTime()
        }
    }

    private fun haptic(success: Boolean) {
        val v = view ?: return
        val c = if (success) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        v.performHapticFeedback(c)
    }

    fun onCall() {
        touch.pause(now(), film, g)
        haptic(false)
        dialog = 1
        changed()
    }

    fun onMessage() {
        touch.pause(now(), film, g)
        haptic(false)
        dialog = 2
        changed()
    }

    fun onDismiss() {
        if (dialog == 0) return
        touch.pause(now(), film, g)
        dialog = 0
        changed()
    }

    fun onConfirmCall() {
        val now = now()
        touch.pause(now, film, g)
        haptic(true)
        dialog = 0
        touch.calling = CourierPulse(touch.calling.at(now), now, 1.4, 0.3)
        changed()
    }

    fun onSend(text: String) {
        val now = now()
        touch.pause(now, film, g)
        haptic(true)
        dialog = 0
        touch.sent = text
        touch.toast = CourierPulse(touch.toast.at(now), now, 1.6, 0.4)
        changed()
    }

    /** Locate: the camera glides back onto the van and follows it again. */
    fun onLocate() {
        val now = now()
        touch.pause(now, film, g)
        haptic(false)
        val target = M.framing(touch.vanD, g.lay)
        touch.camAnim = CourierCamAnim(touch.camera(now), target, now, if (reduce) 0.2 else 0.8)
        touch.decay = null
        touch.follow = true
        touch.lastActivity = now
        changed()
    }

    private fun startTouch(now: Double) {
        touch.pause(now, film, g)
        touch.follow = false
        if (dialog != 0) dialog = 0
    }

    fun panStart() {
        val now = now()
        startTouch(now)
        touch.panning = true
        changed()
    }

    fun panBy(dx: Double, dy: Double) {
        val now = now()
        val c = touch.camera(now)
        val cam = M.screenCam(c, g.lay)
        val ax = cam.worldX(g.lay.fx, g.lay.fy)
        val ay = cam.worldY(g.lay.fx, g.lay.fy)
        val bx = cam.worldX(g.lay.fx + dx, g.lay.fy + dy)
        val by = cam.worldY(g.lay.fx + dx, g.lay.fy + dy)
        touch.setCamera(
            c.copy(
                x = rubber(c.x - (bx - ax), CourierCity.CITY_X0, CourierCity.CITY_X1, 300.0),
                y = rubber(c.y - (by - ay), CourierCity.CITY_Y0, CourierCity.CITY_Y1, 300.0),
            ),
        )
        touch.lastActivity = now
        changed()
    }

    fun pinchBy(change: Double, fx: Double, fy: Double) {
        val now = now()
        if (!touch.pinching) {
            touch.pinching = true
            if (!touch.panning) startTouch(now)
        }
        val c = touch.camera(now)
        val cam = M.screenCam(c, g.lay)
        val wx = cam.worldX(fx, fy)
        val wy = cam.worldY(fx, fy)
        val z1 = rubber(c.z + log2(max(change, 0.01)), LZ_MIN, LZ_MAX, 0.4)
        val k = 2.0.pow(c.z - z1)
        touch.setCamera(c.copy(x = wx - (wx - c.x) * k, y = wy - (wy - c.y) * k, z = z1))
        touch.lastActivity = now
        changed()
    }

    fun gestureEnd(vx: Double, vy: Double) {
        val now = now()
        val pinched = touch.pinching
        touch.panning = false
        touch.pinching = false
        val c = touch.camera(now)
        if (pinched && (c.z < LZ_MIN || c.z > LZ_MAX)) {
            touch.camAnim = CourierCamAnim(c, c.copy(z = min(max(c.z, LZ_MIN), LZ_MAX)), now, 0.3)
        } else if (!pinched) {
            val cam = M.screenCam(c, g.lay)
            val ax = cam.worldX(g.lay.fx, g.lay.fy)
            val ay = cam.worldY(g.lay.fx, g.lay.fy)
            val bx = cam.worldX(g.lay.fx + vx, g.lay.fy + vy)
            val by = cam.worldY(g.lay.fx + vx, g.lay.fy + vy)
            touch.decay = CourierDecay(c.x, c.y, -(bx - ax), -(by - ay), now)
        }
        touch.lastActivity = now
        changed()
    }

    companion object {
        private val LZ_MIN = log2(M.sMin)
        private val LZ_MAX = log2(M.sMax * 1.2)

        private fun rubber(v: Double, lo: Double, hi: Double, band: Double): Double {
            if (v < lo) {
                val o = lo - v
                return lo - o / (1 + o / band)
            }
            if (v > hi) {
                val o = v - hi
                return hi + o / (1 + o / band)
            }
            return v
        }
    }
}

/** One-finger drag pans (with a fling), two fingers pinch about their centre; both take the map over. */
private suspend fun PointerInputScope.courierMapGestures(ctl: CourierController) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker()
        var trackedId = first.id
        tracker.addPosition(first.uptimeMillis, first.position)
        var started = false
        var travel = 0f
        var multi = false
        val d = density
        while (true) {
            val ev = awaitPointerEvent()
            val pressed = ev.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size > 1) multi = true
            val pan = ev.calculatePan()
            val zoom = ev.calculateZoom()
            if (!started) {
                travel += pan.getDistance()
                if (travel > 2 * d || zoom != 1f) {
                    started = true
                    ctl.panStart()
                }
            }
            if (started) {
                if (pan != Offset.Zero) ctl.panBy(pan.x / d.toDouble(), pan.y / d.toDouble())
                if (pressed.size > 1 && zoom != 1f) {
                    val cen = ev.calculateCentroid(useCurrent = true)
                    ctl.pinchBy(zoom.toDouble(), cen.x / d.toDouble(), cen.y / d.toDouble())
                }
            }
            val tracked = pressed.firstOrNull { it.id == trackedId } ?: pressed.first().also {
                trackedId = it.id
                tracker.resetTracking()
            }
            tracker.addPosition(tracked.uptimeMillis, tracked.position)
            ev.changes.forEach { if (it.positionChanged()) it.consume() }
        }
        if (started) {
            val v = if (multi) androidx.compose.ui.unit.Velocity.Zero else tracker.calculateVelocity()
            ctl.gestureEnd(v.x / d.toDouble(), v.y / d.toDouble())
        }
    }
}

// pins

@Composable
private fun CourierPins(ctl: CourierController, g: CourierGeometry) {
    val labelShadow = Shadow(Color.White.copy(alpha = 0.95f), Offset.Zero, 4f)
    // the hub: a dark rounded square with a box, "Hub" to its left
    Box(
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            if (!ctl.ready) {
                alpha = 0f
                return@graphicsLayer
            }
            val cam = M.screenCam(ctl.frame().cam, g.lay)
            val x = cam.sx(CourierCity.HUB_X, CourierCity.HUB_Y)
            val y = cam.sy(CourierCity.HUB_X, CourierCity.HUB_Y)
            alpha = (1 - M.smooth01((cam.s - 0.5) / 0.6)).toFloat()
            translationX = ((x - 43) * density).toFloat()
            translationY = ((y + 1) * density).toFloat()
        },
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Ink).border(2.dp, Color.White, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CourierIcon(CourierGlyph.Box, Color.White, 13.dp, under = Ink)
        }
        Box(Modifier.offset(x = (-104).dp, y = 5.dp).width(100.dp), contentAlignment = Alignment.TopEnd) {
            Text(
                "Hub", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, softWrap = false,
                style = TextStyle(shadow = labelShadow),
            )
        }
    }
    // Home: a blue disc with a house on a short white stem, "Home" to its right
    Box(
        Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            if (!ctl.ready) {
                alpha = 0f
                return@graphicsLayer
            }
            val cam = M.screenCam(ctl.frame().cam, g.lay)
            val wy = (CourierCity.HOME_Y0 + CourierCity.HOME_Y1) / 2
            val x = cam.sx(CourierCity.DOOR_X, wy)
            val y = cam.sy(CourierCity.DOOR_X, wy)
            val fade = 1 - M.smooth01((cam.s - 1.2) / 1.1)
            alpha = fade.toFloat()
            val sc = (0.85 + 0.15 * fade).toFloat()
            scaleX = sc
            scaleY = sc
            transformOrigin = TransformOrigin(18 * density / size.width, 21.5f * density / size.height)
            translationX = ((x - 18) * density).toFloat()
            translationY = ((y - 44) * density).toFloat()
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(36.dp)
                    .courierShadow(ShadowInk.copy(alpha = 0.25f), 4.dp, 3.dp, 18.dp)
                    .clip(CircleShape)
                    .background(Blue)
                    .border(3.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CourierIcon(CourierGlyph.House, Color.White, 16.dp)
            }
            Box(Modifier.size(3.dp, 7.dp).background(Color.White, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)))
        }
        Text(
            "Home", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, softWrap = false,
            style = TextStyle(shadow = labelShadow),
            modifier = Modifier.offset(x = 42.dp, y = 9.dp),
        )
    }
}

// overlays

@Composable
private fun CourierToast(ctl: CourierController, g: CourierGeometry, calling: Boolean) {
    val sent by remember(ctl) { derivedStateOf { if (ctl.ready) ctl.frame().sent else COURIER_QUICK_REPLIES[0] } }
    Row(
        Modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val k = if (ctl.ready) M.clamp01(if (calling) ctl.frame().calling else ctl.frame().toast) else 0.0
                alpha = k.toFloat()
                val sc = (0.96 + 0.04 * k).toFloat()
                scaleX = sc
                scaleY = sc
                translationX = (g.width / 2 * density - size.width / 2).toFloat()
                translationY = ((g.panelTop - 36 + 10 * (1 - k)) * density - size.height / 2).toFloat()
            }
            .courierShadow(ShadowInk.copy(alpha = 0.16f), 6.dp, 4.dp, 20.dp)
            .background(Color.White, CircleShape)
            .height(40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (calling) {
            CourierIcon(CourierGlyph.Phone, Blue, 17.dp)
            Text("Calling Marco…", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        } else {
            CourierIcon(CourierGlyph.Check, Green, 17.dp, under = Color.White)
            Text("Sent · “$sent”", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun CourierLocate(ctl: CourierController, g: CourierGeometry) {
    val following by remember(ctl) { derivedStateOf { !ctl.ready || ctl.frame().follow } }
    val d = 54.0
    Box(
        Modifier
            .offset((g.locateX - d / 2).dp, (g.locateY - d / 2).dp)
            .size(d.dp)
            .courierShadow(ShadowInk.copy(alpha = 0.16f), 5.dp, 3.dp, 27.dp)
            .clip(CircleShape)
            .background(Color(0xF7FFFFFF.toInt()))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(), onClick = ctl::onLocate)
            .semantics { contentDescription = "Follow the van" },
        contentAlignment = Alignment.Center,
    ) {
        CourierIcon(if (following) CourierGlyph.Locate else CourierGlyph.LocateOutline, Blue, 22.dp)
    }
}

// the panel

@Composable
private fun CourierPanel(ctl: CourierController, g: CourierGeometry) {
    val eta by remember(ctl) { derivedStateOf { M.etaText(CourierCity.routeLen - (if (ctl.ready) ctl.frame().vanD else 0.0)) } }
    val dist by remember(ctl) { derivedStateOf { M.distanceText(CourierCity.routeLen - (if (ctl.ready) ctl.frame().vanD else 0.0)) } }
    val tnum = TextStyle(fontFeatureSettings = "tnum")
    val panelShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    Box(
        Modifier
            .offset(g.panelLeft.dp, g.panelTop.dp)
            .size(g.panelW.dp, (COURIER_PANEL_H + g.bottomInset).dp)
            .courierShadow(ShadowInk.copy(alpha = 0.14f), 9.dp, (-4).dp, 30.dp, topOnly = true)
            .background(Color.White, panelShape)
            .padding(horizontal = COURIER_PANEL_PAD.dp),
    ) {
        Column {
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(84.dp)) {
                Column(
                    Modifier.graphicsLayer {
                        val f = if (ctl.ready) ctl.frame() else null
                        val a = f?.arrived ?: 0.0
                        alpha = ((1 - a) * (1 - (f?.dip ?: 0.0))).toFloat()
                        translationY = (-8 * a * density).toFloat()
                    },
                ) {
                    Line(18) { Text("Arriving in", color = Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    Line(42) {
                        Text(eta, color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, style = tnum, softWrap = false)
                    }
                    Line(20) { Text(dist, color = Body, fontSize = 15.sp, style = tnum, softWrap = false) }
                }
                Column(
                    Modifier.graphicsLayer {
                        val f = if (ctl.ready) ctl.frame() else null
                        val a = f?.arrived ?: 0.0
                        alpha = (a * (1 - (f?.dip ?: 0.0))).toFloat()
                        translationY = (8 * (1 - a) * density).toFloat()
                    },
                ) {
                    Line(18) { Text("Order 4821 · 2 parcels", color = Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    Line(42) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CourierIcon(CourierGlyph.Check, Green, 28.dp, under = Color.White)
                            Text("Arrived", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, softWrap = false)
                        }
                    }
                    Line(20) { Text("Marco is at your door", color = Body, fontSize = 15.sp, softWrap = false) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height((1 / LocalDensity.current.density).dp).background(Body.copy(alpha = 0.18f)))
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Color(0xFFE8EEFC), CircleShape), contentAlignment = Alignment.Center) {
                    Text("MR", color = Blue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                    Line(22) {
                        Text("Marco Ruiz", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Line(18) {
                        Text("Blue van · KX24 TRL", color = Mute, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Call and Message: large tinted circles (about 54 dp) centred 76 dp apart on the 108 dp slot
                Box(Modifier.size((COURIER_ACTION * 2 + COURIER_ACTION_GAP).dp, COURIER_ACTION.dp)) {
                    RoundAction(CourierGlyph.Phone, "Call Marco", 16.0, ctl::onCall)
                    RoundAction(CourierGlyph.Message, "Message Marco", 92.0, ctl::onMessage)
                }
            }
        }
    }
}

@Composable
private fun Line(height: Int, content: @Composable () -> Unit) {
    Box(Modifier.height(height.dp), contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
private fun RoundAction(glyph: CourierGlyph, label: String, cx: Double, onClick: () -> Unit) {
    val d = 54.0
    Box(
        Modifier
            .offset((cx - d / 2).dp, 0.dp) // requiredSize centres the 54 dp circle on the 48 dp row
            .requiredSize(d.dp)
            .clip(CircleShape)
            .background(Color(0xFFDDE6FF))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(), onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CourierIcon(glyph, Blue, 22.dp)
    }
}
