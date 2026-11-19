package com.ngocdevv.nativecook.blocks.media.boomboxnowplaying

import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Boombox Now Playing: a record and its sleeve floating over studio paper above a music player. The record slides out,
// comes to the middle and spins up as the song starts, turns a full 360° about its upright axis, is scrubbed by a
// finger, the volume goes up and back, the song skips and pauses, and the record slides back into its sleeve. A port
// of VinylNowPlaying.tsx: the 3D stage is Filament + gltfio with the block's own vinyl.glb and studio IBL
// (BoomboxNowPlayingStage.kt), the contact shadows a Compose canvas, and one controller (BnpController) is read by the
// stage and the layers for the same vsync. Touch: a finger on the record turns it (rotary scrub, 10 s of song a radian,
// a detent every 15 s), a tap plays or pauses; 2 s after the last touch the scene eases back into the film.

private val INK = Color(0xFF1D1C1A)
private val SECONDARY = Color(0xFF77716A)
private val PAPER_TOP = Color(BnpScene.PAPER_TOP)
private val PAPER_BOTTOM = Color(BnpScene.PAPER_BOTTOM)
private val BAR = Color(0xFFE4572E)
private val SHADOW = Color(58 / 255f, 44 / 255f, 30 / 255f)

/** Share of the screen the 3D stage covers, from the top. */
private const val STAGE = 0.63
/** A finger nearer the record's centre than this (dp) turns nothing. */
private const val SCRATCH_DEAD = 14.0
/** Seconds of song between two detents of the rotary scrub. */
private const val TICK_S = 15.0

private fun now(): Double = System.nanoTime() / 1e9

@Composable
fun BoomboxNowPlaying(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val reduced = remember { !ValueAnimator.areAnimatorsEnabled() }
    val c = remember(autoPlay, reduced) { BnpController(autoPlay, reduced) }
    var frameNs by remember { mutableLongStateOf(System.nanoTime()) }

    LaunchedEffect(c) {
        var lastTick = Int.MIN_VALUE
        while (true) {
            val ns = withFrameNanos { it }
            frameNs = ns
            val f = c.frame(ns / 1e9)
            // a detent every 15 s of song while a finger turns the record
            val tick = floor(f.song / TICK_S).toInt()
            if (lastTick != Int.MIN_VALUE && tick != lastTick && c.dragging) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            lastTick = tick
        }
    }
    fun frame() = c.frame(frameNs / 1e9)
    val playing by remember { derivedStateOf { frame().playing } }
    val elapsed by remember { derivedStateOf { BnpMotion.clock(frame().song) } }
    val remaining by remember { derivedStateOf { "-" + BnpMotion.clock(BnpMotion.TRACK_SECONDS - frame().song) } }

    val localDensity = LocalDensity.current
    val insetTop = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }.value.toDouble()
    val insetBottom = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }.value.toDouble()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to PAPER_TOP, 0.55f to PAPER_BOTTOM, 1f to PAPER_BOTTOM)),
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val s = min(1.1, max(0.9, width / 393))
        val column = min(width - 48 * s, 460.0)
        val stageH = (height * STAGE).roundToInt().toDouble()
        fun d(v: Double): Dp = v.dp

        // the stage: shadows on the paper, the see-through 3D view, the fade into the paper above the player
        Box(
            Modifier
                .width(maxWidth)
                .height(d(stageH))
                .clipToBounds()
                .semantics {
                    contentDescription = "A vinyl record and its sleeve floating in a photo studio. Turn the record with a finger to move through the song, tap to play or pause"
                }
                .pointerInput(c) {
                    val slop = 6.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var panning = false
                        var cx = 0.0
                        var cy = 0.0
                        var reach = 0.0
                        var lastAngle = 0.0
                        var moved = 0f
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            moved = max(moved, (ch.position - down.position).getDistance())
                            if (!panning && moved > slop) {
                                panning = true
                                c.dragBegan(now())
                                // the record's centre where the stage's camera puts it
                                val sc = c.sceneNow()
                                val cam = doubleArrayOf(sc[8], sc[9], sc[10], sc[11], sc[12], sc[13])
                                val b = DoubleArray(8)
                                BnpMotion.basis(cam, b)
                                val q = DoubleArray(3)
                                BnpMotion.project(cam, b, size.width.toDouble(), size.height.toDouble(), sc[0], sc[1], sc[2], q)
                                cx = q[0]; cy = q[1]
                                reach = BnpMotion.R_VINYL * q[2] * 1.25
                                lastAngle = atan2(down.position.y - cy, down.position.x - cx)
                            }
                            if (panning) {
                                val dx = ch.position.x - cx
                                val dy = ch.position.y - cy
                                val a = atan2(dy, dx)
                                val delta = BnpMotion.angleDelta(a, lastAngle)
                                lastAngle = a
                                val r2 = dx * dx + dy * dy
                                val dead = SCRATCH_DEAD * density
                                if (r2 >= dead * dead && r2 <= reach * reach) c.turn(delta)
                                ch.consume()
                            }
                            if (!ch.pressed) break
                        }
                        if (panning) {
                            c.dragEnded()
                        } else if (moved <= 8.dp.toPx()) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            c.toggle(now())
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val f = frame()
                val w = size.width.toDouble()
                val h = size.height.toDouble()
                val o = DoubleArray(6)
                for (k in intArrayOf(1, 0)) {
                    if (!BnpMotion.shadowAt(f.scene, k, w, h, o)) continue
                    translate(o[0].toFloat(), o[1].toFloat()) {
                        rotateRad(o[4].toFloat(), Offset.Zero) {
                            scale(o[2].toFloat(), o[3].toFloat(), Offset.Zero) {
                                drawCircle(
                                    Brush.radialGradient(
                                        0f to SHADOW, 0.45f to SHADOW.copy(alpha = 0.55f), 1f to SHADOW.copy(alpha = 0f),
                                        center = Offset.Zero, radius = 1f,
                                    ),
                                    1f, Offset.Zero, alpha = o[5].toFloat().coerceIn(0f, 1f),
                                )
                            }
                        }
                    }
                }
            }
            VinylStage3D(c, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(d(56 * s))
                    .background(Brush.verticalGradient(listOf(PAPER_BOTTOM.copy(alpha = 0f), PAPER_BOTTOM))),
            )
        }

        // the sheet's grabber and where the music plays from
        Column(Modifier.fillMaxWidth().offset(y = d(insetTop + 6 * s)), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(d(36 * s), d(5 * s)).background(INK.copy(alpha = 0.18f), RoundedCornerShape(d(3 * s))))
            Text(
                "Playing from Night Drive",
                modifier = Modifier.padding(top = d(10 * s)),
                style = TextStyle(color = SECONDARY, fontSize = (13 * s).sp, fontWeight = FontWeight.SemiBold),
            )
        }

        // the player
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = d((width - column) / 2))
                .padding(bottom = d(insetBottom + 14 * s))
                .width(d(column)),
        ) {
            Text(
                "Overpass Lights",
                maxLines = 1,
                style = TextStyle(color = INK, fontSize = (28 * s).sp, lineHeight = (34 * s).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            )
            Row(Modifier.padding(top = d(2 * s)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Juno Vale",
                    maxLines = 1,
                    style = TextStyle(color = SECONDARY, fontSize = (20 * s).sp, lineHeight = (25 * s).sp),
                )
                Box(Modifier.width(d(8 * s)))
                Row(Modifier.height(13.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    for (k in 0 until 3) {
                        Box(
                            Modifier
                                .graphicsLayer {
                                    val f = frame()
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                    scaleY = max(0.15, BnpMotion.barAt(k, f.song, f.level) * (0.4 + 0.6 * f.volume)).toFloat()
                                }
                                .size(3.dp, 13.dp)
                                .background(BAR, RoundedCornerShape(1.5.dp)),
                        )
                    }
                }
            }
            // song position and the times
            Column(Modifier.padding(top = d(14 * s))) {
                PaperSlider(
                    value = { frame().song / BnpMotion.TRACK_SECONDS },
                    onChange = { c.scrub(now(), it) },
                    onEditing = { c.scrubState(it) },
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    label = "Song position",
                )
                Row(Modifier.fillMaxWidth().padding(top = d(4 * s)), horizontalArrangement = Arrangement.SpaceBetween) {
                    val ts = TextStyle(color = SECONDARY, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                    Text(elapsed, modifier = Modifier.width(64.dp), style = ts)
                    Text(remaining, modifier = Modifier.width(64.dp), style = ts.copy(textAlign = TextAlign.End))
                }
            }
            // back, play / pause, forward
            Row(
                Modifier.fillMaxWidth().padding(top = d(8 * s)),
                horizontalArrangement = Arrangement.spacedBy(d(44 * s), Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(56 * s, "Back to the start", { drawSkip(30 * s * density, forward = false) }) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    c.back(now())
                }
                TransportButton(64 * s, if (playing) "Pause" else "Play", { if (playing) drawPause(40 * s * density) else drawPlay(40 * s * density) }) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    c.toggle(now())
                }
                TransportButton(56 * s, "Forward", { drawSkip(30 * s * density, forward = true) }) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    c.forward(now())
                }
            }
            // volume, between a quiet and a loud speaker
            val icon = 18 * s
            Row(Modifier.fillMaxWidth().padding(top = d(8 * s)), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(d(icon))) { drawSpeaker(size.width * 0.8f, waves = 0) }
                PaperSlider(
                    value = { frame().volume },
                    onChange = { c.setVolume(now(), it) },
                    onEditing = {},
                    modifier = Modifier.padding(horizontal = d(10 * s)).weight(1f).height(34.dp),
                    label = "Volume",
                )
                Canvas(Modifier.size(d(icon))) { drawSpeaker(size.width * 0.8f, waves = 3) }
            }
        }
    }
}

@Composable
private fun TransportButton(side: Double, label: String, glyph: DrawScope.() -> Unit, onClick: () -> Unit) {
    Canvas(
        Modifier
            .size(side.dp)
            .semantics { contentDescription = label }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) { glyph() }
}

/** The platform's light slider: a 4 dp track (ink fill) and a round white thumb; drawn from the frame, never recomposed. */
@Composable
private fun PaperSlider(value: () -> Double, onChange: (Double) -> Unit, onEditing: (Boolean) -> Unit, modifier: Modifier, label: String) {
    val thumbR = 13.5.dp
    Canvas(
        modifier
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                val r = thumbR.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onEditing(true)
                    fun at(x: Float) = ((x - r) / (size.width - 2 * r)).toDouble().coerceIn(0.0, 1.0)
                    onChange(at(down.position.x))
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        onChange(at(ch.position.x))
                        ch.consume()
                    }
                    onEditing(false)
                }
            },
    ) {
        val r = thumbR.toPx()
        val cy = size.height / 2
        val v = value().coerceIn(0.0, 1.0).toFloat()
        val x = r + (size.width - 2 * r) * v
        val h = 4.dp.toPx()
        drawRoundRect(INK.copy(alpha = 0.12f), Offset(r, cy - h / 2), Size(size.width - 2 * r, h), CornerRadius(h / 2))
        drawRoundRect(INK, Offset(r, cy - h / 2), Size(max(0f, x - r), h), CornerRadius(h / 2))
        drawCircle(Color.Black.copy(alpha = 0.06f), r + 1.dp.toPx(), Offset(x, cy + 1.5f.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.08f), r + 0.5f.dp.toPx(), Offset(x, cy + 0.5f.dp.toPx()))
        drawCircle(Color.White, r, Offset(x, cy))
    }
}

/** backward.fill / forward.fill: two triangles nose to tail, `side` px tall-ish, centred. */
private fun DrawScope.drawSkip(side: Double, forward: Boolean) {
    val w = side.toFloat()
    val h = w * 0.62f
    val cx = size.width / 2
    val cy = size.height / 2
    val p = Path()
    for (i in 0 until 2) {
        val x0 = cx - w / 2 + i * w / 2
        if (forward) {
            p.moveTo(x0, cy - h / 2); p.lineTo(x0 + w / 2, cy); p.lineTo(x0, cy + h / 2)
        } else {
            p.moveTo(x0 + w / 2, cy - h / 2); p.lineTo(x0, cy); p.lineTo(x0 + w / 2, cy + h / 2)
        }
        p.close()
    }
    drawPath(p, INK)
    drawPath(p, INK, style = Stroke(width = w * 0.06f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun DrawScope.drawPlay(side: Double) {
    val h = side.toFloat() * 0.82f
    val w = h * 0.86f
    val cx = size.width / 2 + w * 0.06f
    val cy = size.height / 2
    val p = Path().apply {
        moveTo(cx - w / 2, cy - h / 2); lineTo(cx + w / 2, cy); lineTo(cx - w / 2, cy + h / 2); close()
    }
    drawPath(p, INK)
    drawPath(p, INK, style = Stroke(width = h * 0.08f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun DrawScope.drawPause(side: Double) {
    val h = side.toFloat() * 0.8f
    val bw = h * 0.26f
    val gap = h * 0.22f
    val cx = size.width / 2
    val cy = size.height / 2
    val r = CornerRadius(bw * 0.25f)
    drawRoundRect(INK, Offset(cx - gap / 2 - bw, cy - h / 2), Size(bw, h), r)
    drawRoundRect(INK, Offset(cx + gap / 2, cy - h / 2), Size(bw, h), r)
}

/** speaker.fill (with `waves` arcs: speaker.wave.3.fill). */
private fun DrawScope.drawSpeaker(side: Float, waves: Int) {
    val h = side
    val cy = size.height / 2
    val x0 = (size.width - (if (waves > 0) h * 1.5f else h * 0.7f)) / 2
    val p = Path().apply {
        moveTo(x0, cy - h * 0.18f); lineTo(x0 + h * 0.22f, cy - h * 0.18f); lineTo(x0 + h * 0.55f, cy - h * 0.45f)
        lineTo(x0 + h * 0.55f, cy + h * 0.45f); lineTo(x0 + h * 0.22f, cy + h * 0.18f); lineTo(x0, cy + h * 0.18f); close()
    }
    drawPath(p, SECONDARY)
    for (i in 1..waves) {
        val r = h * (0.18f + 0.22f * i)
        drawArc(
            SECONDARY, -50f, 100f, false, Offset(x0 + h * 0.5f - r, cy - r), Size(2 * r, 2 * r),
            style = Stroke(width = h * 0.1f, cap = StrokeCap.Round),
        )
    }
}
