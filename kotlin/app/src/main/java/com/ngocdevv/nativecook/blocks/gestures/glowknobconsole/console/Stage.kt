// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.graphics.Paint
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Perf
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.Shared
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Drawing units below the specimen box available to the floor layers.
private const val FLOOR = 210.0
// Glass floor: the mirror line sits just under the housing; the reflection is
// squashed slightly for perspective, softened a touch and faded with depth.
const val GAP = 6.0
const val SQUASH = 0.9
const val DEPTH = 0.7
// Visibility of the reflection from the glass edge down: strong at contact,
// then an eased falloff to nothing. Applied as a page-colored "fog" over the
// mirrored device (opacity = 1 - visibility).
val VISIBLE = doubleArrayOf(0.42, 0.2, 0.06, 0.0)
val VISIBLE_AT = floatArrayOf(0f, 0.25f, 0.55f, 1f)

/** The page fog colors for a page tone (white dimmed by the ambience). */
fun fogColors(tone: Double): List<SkiaCompat.C> {
    val c = jsRound(255 * tone).toInt() / 255f
    return VISIBLE.map { v -> SkiaCompat.C(c, c, c, (1 - v).toFloat()) }
}

// Brightness is a "world" setting: its level dims the page around the
// device (≤10% black at 0), whichever channel is being edited.
@Composable
fun StageAmbient(control: TactileControl, fx: Effects, modifier: Modifier = Modifier) {
    val on = fx.ambient && !control.reduced
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (on) ((1 - max(0.0, min(1.0, control.brightness.get()))) * 0.1).toFloat() else 0f
            }
            .background(Color.Black),
    )
}

/**
 * The surface under the device: a glass floor that mirrors the live device
 * (same light, knob and spill, drawn by DeviceArt), and the colored pool
 * where the light falls on it.
 */
@Composable
fun StageFloor(control: TactileControl, fx: Effects, tilt: Shared<Tilt>, scale: Float, modifier: Modifier = Modifier) {
    Perf.recomposed("StageFloor")
    val density = LocalDensity.current.density
    val painter = remember { DeviceArtPainter() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val blur26 = remember { SkiaCompat.blur(26f) }
    // The fog must match the page as seen, including the brightness ambience.
    val dimmed = fx.ambient && !control.reduced
    val bottom = Specimen.top + G.height
    val mirror = bottom + GAP / 2
    val depth = G.height * SQUASH * DEPTH
    val s = scale * density
    Box(
        modifier
            .fillMaxWidth()
            .height(((Specimen.height + FLOOR) * scale).dp)
            .clipToBounds()
            .clearAndSetSemantics {},
    ) {
        if (fx.reflection) {
            // Group layer={<Paint><Blur blur={1.2} /></Paint>}: σ 1.2 units, decal.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val r = SkiaCompat.renderEffectRadius(1.2f * s)
                        renderEffect = BlurEffect(r, r, TileMode.Decal)
                        clip = true
                    },
            ) {
                val state = ArtState.of(control, tilt.get())
                drawIntoCanvas { canvas ->
                    val c = canvas.nativeCanvas
                    c.save()
                    c.scale(s, s)
                    c.translate(0f, mirror.toFloat())
                    c.scale(1f, -SQUASH.toFloat())
                    c.translate(0f, (-mirror).toFloat())
                    c.translate(0f, Specimen.top.toFloat())
                    Perf.section("Stage:reflectionArt") { painter.draw(c, state, fx, baked = true, pxPerUnit = s) }
                    c.restore()
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val extent = G.Track.width * control.fill()
            val tip = tipColor(control.lightMix())
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(s, s)
                if (fx.reflection) {
                    val dim = if (dimmed) (1 - max(0.0, min(1.0, control.brightness.get()))) * 0.1 else 0.0
                    paint.reset(); paint.isAntiAlias = true
                    paint.shader = SkiaCompat.linear(0f, mirror.toFloat(), 0f, (mirror + depth).toFloat(), fogColors(1 - dim), VISIBLE_AT)
                    c.drawRect(0f, mirror.toFloat(), 1000f, (mirror + G.height).toFloat(), paint)
                    // The glass edge: a hairline catching the light where the
                    // device meets its reflection, brightest in the middle.
                    paint.reset(); paint.isAntiAlias = true
                    paint.shader = SkiaCompat.linear(
                        120f, 0f, 880f, 0f,
                        listOf(SkiaCompat.rgba("#FFFFFF00"), SkiaCompat.rgba("#FFFFFFB0"), SkiaCompat.rgba("#FFFFFF00")),
                    )
                    c.drawRect(120f, (mirror - 0.6).toFloat(), 880f, (mirror + 0.6).toFloat(), paint)
                }
                if (fx.spill) {
                    val pool = control.lightAlpha() * min(1.0, extent / 8) * (0.18 + 0.32 * control.fill())
                    val poolW = 160 + 300 * control.fill()
                    val poolX = G.Track.x + extent * 0.7 - poolW / 2
                    if (pool > 0) {
                        paint.reset(); paint.isAntiAlias = true
                        paint.setColor(tip.toLong())
                        paint.alpha = (pool.coerceIn(0.0, 1.0) * tip.a * 255).roundToInt()
                        paint.maskFilter = blur26
                        c.drawOval(RectF(poolX.toFloat(), (bottom + 6).toFloat(), (poolX + poolW).toFloat(), (bottom + 6 + 44).toFloat()), paint)
                    }
                }
                c.restore()
            }
        }
    }
}
