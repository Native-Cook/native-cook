package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** React Native `allowFontScaling={false}`: a dp-sized text unit that ignores the user font scale. */
@Composable
@ReadOnlyComposable
fun Float.fixedSp(): TextUnit = (this / LocalDensity.current.fontScale).sp

@Composable
@ReadOnlyComposable
fun Dp.fixedSp(): TextUnit = (value / LocalDensity.current.fontScale).sp

/** Reanimated `interpolate(value, inputs, outputs, Extrapolation.CLAMP)`. */
fun interpolateClamped(value: Float, inputs: FloatArray, outputs: FloatArray): Float {
    if (value <= inputs.first()) return outputs.first()
    if (value >= inputs.last()) return outputs.last()
    for (index in 0 until inputs.size - 1) {
        val start = inputs[index]
        val end = inputs[index + 1]
        if (value in start..end) {
            val t = if (end == start) 0f else (value - start) / (end - start)
            return outputs[index] + (outputs[index + 1] - outputs[index]) * t
        }
    }
    return outputs.last()
}
