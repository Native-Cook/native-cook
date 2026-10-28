package com.ngocdevv.nativecook.blocks.onboarding.fighterprofiletour

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

// Port of CommitShimmer.tsx. The SkSL is translated line by line to AGSL (API 33+).
private const val SOURCE = """
uniform float2 resolution;
uniform float sweep;
uniform float strength;
half4 main(float2 position) {
  float2 uv = position / resolution;
  float beam = 1.0 - smoothstep(0.0, 0.11, abs(uv.x + uv.y * 0.13 - sweep));
  float alpha = beam * strength * 0.38;
  return half4(alpha, alpha * 0.98, alpha * 0.80, alpha);
}"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ShimmerShader {
    private val shader = RuntimeShader(SOURCE)
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, sweep: Float, strength: Float) {
        shader.setFloatUniform("resolution", scope.size.width, scope.size.height)
        shader.setFloatUniform("sweep", sweep)
        shader.setFloatUniform("strength", strength)
        scope.drawRect(brush)
    }
}

/** Below API 33 the same beam is a horizontal gradient band: same width, strength and colour. */
private fun DrawScope.drawShimmerFallback(sweep: Float, strength: Float) {
    val alpha = 0.38f * strength
    // The shader's beam leans with y (uv.y * 0.13); the band is centred on its mean lean.
    val centre = sweep - 0.065f
    val clear = Color(1f, 0.98f, 0.80f, 0f)
    val peak = Color(1f, 0.98f, 0.80f, alpha)
    val stops = arrayOf(
        (centre - 0.11f).coerceIn(0f, 1f) to clear,
        (centre - 0.055f).coerceIn(0f, 1f) to peak.copy(alpha = alpha * 0.5f),
        centre.coerceIn(0f, 1f) to peak,
        (centre + 0.055f).coerceIn(0f, 1f) to peak.copy(alpha = alpha * 0.5f),
        (centre + 0.11f).coerceIn(0f, 1f) to clear,
    )
    drawRect(Brush.horizontalGradient(colorStops = stops, startX = 0f, endX = size.width))
}

/**
 * The commit shimmer drawn over the Continue button. [sweep] runs from -0.15 to 1.2 and is read in the
 * draw phase only; [strength] is 1, or 0 under Reduced Motion.
 */
@Composable
internal fun CommitShimmer(sweep: () -> Float, strength: () -> Float, modifier: Modifier = Modifier) {
    val agsl = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ShimmerShader() else null }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val s = sweep()
                val k = strength()
                if (k <= 0f || s < -0.3f || s > 1.4f) return@drawBehind
                if (agsl != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    agsl.draw(this, s, k)
                } else {
                    drawShimmerFallback(s, k)
                }
            },
    )
}
