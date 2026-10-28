package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Anatomy in source-art pixels: where each move leaves the body and where it aims.
 * Charizard: the base of the drawn tail flame (embers leave its tip). Blastoise: the centre of each
 * cannon's mouth, aimed down its barrel. Bulbasaur: the middle of the bulb's crown, and the bulb.
 */
private class Anchor(val a: FloatArray, val b: FloatArray, val aimA: FloatArray, val aimB: FloatArray)

private val ANCHORS = listOf(
    Anchor(floatArrayOf(452f, 402f), floatArrayOf(452f, 402f), floatArrayOf(0f, -1f), floatArrayOf(0f, -1f)),
    Anchor(floatArrayOf(99f, 154f), floatArrayOf(409f, 196f), floatArrayOf(-0.67f, -0.74f), floatArrayOf(1f, -0.06f)),
    Anchor(floatArrayOf(377f, 152f), floatArrayOf(352f, 215f), floatArrayOf(0f, -1f), floatArrayOf(0f, -1f)),
)
private const val ART = 512
private val NO_LEAVES = FloatArray(LEAVES * 4)

/** True while the move's effect clock runs, and this layer has something to draw. */
private fun visible(motion: HabitatMotion, layer: Int, particlesRun: Boolean): Boolean {
    val secs = motion.fx.value
    return secs > 0f && secs < FX_S && (layer == 1 || (motion.powerIndex == 2 && !particlesRun))
}

/**
 * A signature move and everything it throws. `layer` 1 is in front of the creatures; layer 0 is
 * behind them, for the leaves that fall on the far side of Bulbasaur. Transparent and still outside
 * a move's effect clock. `particlesRun` is true while the particle world draws what the move throws;
 * this layer then draws only what stays attached.
 */
@Composable
internal fun HabitatSignature(motion: HabitatMotion, layer: Int, layout: HabitatLayout, particlesRun: Boolean) {
    if (!supportsAgsl()) return
    val density = LocalDensity.current.density
    val shader = remember { SignatureShader() }
    Box(
        Modifier
            .size(layout.width.dp, layout.height.dp)
            .drawBehind {
                motion.observe()
                if (Build.VERSION.SDK_INT < 33 || !visible(motion, layer, particlesRun)) return@drawBehind
                shader.apply(motion, layer, layout, particlesRun)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(density, density)
                    native.drawRect(0f, 0f, layout.width, layout.height, shader.paint)
                    native.restore()
                }
            },
    )
}

@androidx.annotation.RequiresApi(33)
private class SignatureShader {
    private val runtime = RuntimeShader(SIGNATURE_AGSL)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.shader = runtime }

    fun apply(motion: HabitatMotion, layer: Int, layout: HabitatLayout, particlesRun: Boolean) {
        val width = layout.width
        val size = layout.size
        val unit = size / 344f
        val drift = EXCHANGE_DRIFT * width
        val secs = motion.fx.value
        val index = motion.powerIndex
        val anchor = ANCHORS[index]
        val state = creaturePose(index, motion)
        val exchange = state.exchange
        val body = BodyFrame(
            index = index, size = size, pad = layout.pad, left = layout.left, top = layout.top, heroTop = layout.heroTop, width = width,
            screenHeight = layout.screenHeight, scale = layout.scale, exchange = exchange, driftPx = drift, pose = state.pose,
        )
        // Attached effects follow the posed body; their aim turns with it.
        val a = bodyPoint(body, anchor.a[0], anchor.a[1])
        val b = bodyPoint(body, anchor.b[0], anchor.b[1])
        val ta = bodyPoint(body, anchor.a[0] + anchor.aimA[0] * 24, anchor.a[1] + anchor.aimA[1] * 24)
        val tb = bodyPoint(body, anchor.b[0] + anchor.aimB[0] * 24, anchor.b[1] + anchor.aimB[1] * 24)
        // Particles are born at the resting body and travel with its world.
        val k = size / ART
        val shift = exchange.drift * drift
        val emitAx = layout.left + anchor.a[0] * k + shift
        val emitAy = layout.heroTop + layout.top + anchor.a[1] * k
        val emitBx = layout.left + anchor.b[0] * k + shift
        val emitBy = layout.heroTop + layout.top + anchor.b[1] * k
        val floorY = layout.heroTop + layout.top + RIGS[index].feet * k
        val leaves = if (index == 2 && !particlesRun) leafUniforms(secs, emitAx, emitAy, floorY, unit) else (NO_LEAVES to NO_LEAVES)
        runtime.setFloatUniform("resolution", layout.width, layout.height)
        runtime.setFloatUniform("secs", secs)
        runtime.setFloatUniform("element", index.toFloat())
        runtime.setFloatUniform("layer", layer.toFloat())
        runtime.setFloatUniform("anchorA", a[0], a[1])
        runtime.setFloatUniform("anchorB", b[0], b[1])
        runtime.setFloatUniform("aimA", ta[0] - a[0], ta[1] - a[1])
        runtime.setFloatUniform("aimB", tb[0] - b[0], tb[1] - b[1])
        runtime.setFloatUniform("emitA", emitAx, emitAy)
        runtime.setFloatUniform("emitB", emitBx, emitBy)
        runtime.setFloatUniform("floorY", floorY)
        runtime.setFloatUniform("unit", unit)
        runtime.setFloatUniform("fadeTop", layout.heroTop * 0.55f)
        runtime.setFloatUniform("screen", width, layout.screenHeight)
        runtime.setFloatUniform("front", exchange.f)
        runtime.setFloatUniform("phase", motion.frontPhase)
        runtime.setFloatUniform("role", exchange.role.toFloat())
        runtime.setFloatUniform("incoming", ((floorToInt(wrapPosition(motion.position)) + 1) % 3).toFloat())
        runtime.setFloatUniform("particles", if (particlesRun) 0f else 1f)
        runtime.setFloatUniform("leaf", leaves.first)
        runtime.setFloatUniform("leafState", leaves.second)
    }
}
