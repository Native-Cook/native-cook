package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import kotlin.math.cos
import kotlin.math.sin

// The three lamps move on different ellipses and screen over one another. Their geometry
// is constant across a frame, so calculate it once before the shader samples the field.
@RequiresApi(33)
internal class ReferralLight {
    private val key = FloatArray(3)
    private val second = FloatArray(3)
    private val deep = FloatArray(3)
    private var lastScroll = Double.NaN
    private val lamp = FloatArray(3)
    private val pair = FloatArray(2)

    fun update(
        glassShader: RuntimeShader,
        state: ReferralState,
        width: Float,
        height: Float,
        lensY: Float,
    ) {
        if (state.scroll.value != lastScroll) {
            lastScroll = state.scroll.value
            ReferralFriends.rgb(ReferralFriends.lamps, lastScroll, key)
            ReferralFriends.rgb(ReferralFriends.second, lastScroll, second)
            ReferralFriends.rgb(ReferralFriends.deep, lastScroll, deep)
        }
        val angle = 6.2831853 * state.phase.value
        val grow = 0.72 + 0.28 * state.railArrival
        val swell = state.glow.value
        val flare = state.flash.value
        uniform(
            glassShader,
            "lampCenter1",
            width / 2 + 16 * cos(angle).toFloat(),
            lensY + 9 * sin(angle).toFloat(),
        )
        uniform(
            glassShader,
            "lampCenter2",
            0.08f * width + 22 * sin(angle + 1).toFloat(),
            height + 4 + 14 * cos(angle + 1).toFloat(),
        )
        uniform(
            glassShader,
            "lampCenter3",
            0.95f * width + 18 * cos(angle + 2.4).toFloat(),
            -6 + 13 * sin(2 * angle + 0.6).toFloat(),
        )
        uniform(
            glassShader,
            "lampInverse1",
            (1 / (0.40 * width * grow * (1 + 0.14 * swell))).toFloat(),
            (1 / (0.24 * width * grow * (1 + 0.14 * swell))).toFloat(),
        )
        uniform(
            glassShader,
            "lampInverse2",
            (1 / (0.33 * width * grow)).toFloat(),
            (1 / (0.30 * width * grow)).toFloat(),
        )
        uniform(
            glassShader,
            "lampInverse3",
            (1 / (0.31 * width * grow)).toFloat(),
            (1 / (0.28 * width * grow)).toFloat(),
        )
        val luminance = key[0] * 0.299 + key[1] * 0.587 + key[2] * 0.114
        for (i in 0..2) {
            lamp[i] =
                (
                    (luminance + (key[i] - luminance) * 1.6).coerceIn(0.0, 1.0) *
                        (0.70 + 0.13 * swell + 0.10 * flare)
                ).toFloat()
        }
        ReferralShaderUniforms.set(glassShader, "lampColor1", lamp)
        for (i in 0..2) lamp[i] = (second[i] * (0.86 + 0.14 * flare)).toFloat()
        ReferralShaderUniforms.set(glassShader, "lampColor2", lamp)
        for (i in 0..2) lamp[i] = (deep[i] * (0.80 + 0.14 * flare)).toFloat()
        ReferralShaderUniforms.set(glassShader, "lampColor3", lamp)
        val turn = 0.16 * sin(angle)
        uniform(
            glassShader,
            "highlightFrom",
            (-0.56 * cos(turn) + 0.83 * sin(turn)).toFloat(),
            (-0.56 * sin(turn) - 0.83 * cos(turn)).toFloat(),
        )
    }

    private fun uniform(
        shader: RuntimeShader,
        name: String,
        x: Float,
        y: Float,
    ) {
        pair[0] = x
        pair[1] = y
        ReferralShaderUniforms.set(shader, name, pair)
    }
}
