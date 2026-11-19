package com.ngocdevv.nativecook.blocks.onboarding.visorbootonboarding

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The studio behind the see-through Filament view (VisorBackdrop.tsx), one shader pass: a blue-grey sweep lit from
 * above the helmet, a soft pool of light round the plinth, the plinth's contact shadow on the floor (projected with
 * the stage camera, so it stays under the plinth as the camera moves), a vignette and grain. React Native converts
 * its sRGB result to Display P3 only because Skia reads shader output as P3 on iOS; Android reads it as sRGB, so the
 * colour is written as is.
 *
 * Below API 33 (no AGSL) the same layers are drawn with gradients: the sweep as a radial gradient, the pool and the
 * contact shadow as radial gradients squashed to the plinth's ellipse, and the vignette; no grain.
 */
private const val VISOR_BOOT_STUDIO = """
uniform float2 res;
uniform float2 c;
uniform float2 rad;
uniform float sweep;
uniform float pt;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

half4 main(float2 p) {
  float2 uv = p / res;
  float2 d = (p - float2(res.x * 0.5, res.y * 0.3)) / res.y;
  float r = length(d * float2(1.25, 1.0));
  float3 centre = float3(0.157, 0.188, 0.227);
  float3 mid = float3(0.086, 0.106, 0.129);
  float3 edge = float3(0.035, 0.043, 0.055);
  float3 col = mix(centre, mid, smoothstep(0.0, 0.42, r));
  col = mix(col, edge, smoothstep(0.38, 0.95, r));
  col += float3(0.012, 0.009, 0.006) * smoothstep(0.75, 0.35, abs(uv.y - 0.62) * 2.0);
  float2 e = (p - c) / max(rad * 2.6, float2(1.0));
  float pool = exp(-dot(e, e) * 1.6);
  col += float3(0.05, 0.056, 0.064) * pool * (1.0 + 0.8 * sweep);
  float2 s = (p - c) / max(rad, float2(1.0));
  float sr = length(s);
  float shadow = 0.82 * (1.0 - smoothstep(0.85, 1.45, sr)) + 0.25 * exp(-sr * sr * 0.45);
  col *= 1.0 - clamp(shadow, 0.0, 0.9);
  float v = length((uv - 0.5) * float2(1.0, 0.85));
  col *= 1.0 - 0.38 * smoothstep(0.35, 0.85, v);
  col += (hash(floor(p / pt)) - 0.5) * 0.018;
  return half4(half3(col), 1.0);
}
"""

/** Where the plinth's base is on screen (dp) and its half-axes, for the backdrop's shadow and pool. */
internal class VisorBootFloor(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val sweep: Float)

internal fun visorBootFloor(s: VisorBootState, w: Double, h: Double, plinthShown: Boolean = true): VisorBootFloor {
    val c = VisorBootMotion.cameraAt(s)
    val b = VisorBootMotion.basis(c, w)
    val o = DoubleArray(3)
    val ok = VisorBootMotion.project(c, b, w, h, VisorBootV3(0.0, 0.0, 0.0), o)
    val rx = if (ok && plinthShown) VisorBootMotion.PLINTH_R * b.focal / o[2] else 0.0
    val dist = sqrt(c.eye.x * c.eye.x + c.eye.y * c.eye.y + c.eye.z * c.eye.z).takeIf { it > 0 } ?: 1.0
    val ry = rx * max(0.08, abs(c.eye.y) / dist)
    val sweep = VisorBootMotion.clamp01(VisorBootMotion.blend(VisorBootMotion.filmSweepLevel(s.t), 0.0, s.m))
    return VisorBootFloor(o[0].toFloat(), o[1].toFloat(), rx.toFloat(), ry.toFloat(), sweep.toFloat())
}

internal class VisorBootBackdropPainter {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(VISOR_BOOT_STUDIO) else null

    /** Draws the studio for floor `f` (dp) on a scope whose size is in pixels; `density` is px per dp. */
    fun draw(scope: DrawScope, f: VisorBootFloor, density: Float) {
        if (Build.VERSION.SDK_INT >= 33) drawShader(scope, f, density) else drawFallback(scope, f, density)
    }

    @RequiresApi(33)
    private fun drawShader(scope: DrawScope, f: VisorBootFloor, density: Float) {
        val sh = shader as RuntimeShader
        sh.setFloatUniform("res", scope.size.width, scope.size.height)
        sh.setFloatUniform("c", f.cx * density, f.cy * density)
        sh.setFloatUniform("rad", f.rx * density, f.ry * density)
        sh.setFloatUniform("sweep", f.sweep)
        sh.setFloatUniform("pt", density)
        scope.drawRect(ShaderBrush(sh))
    }

    private fun drawFallback(scope: DrawScope, f: VisorBootFloor, density: Float) = with(scope) {
        val w = size.width
        val h = size.height
        // the sweep: centre -> mid by r 0.42, -> edge by r ~0.95 (r in screen heights, x stretched 1.25)
        drawRect(Color(0xFF090B0E))
        scale(1f / 1.25f, 1f, Offset(w * 0.5f, h * 0.3f)) {
            drawRect(
                Brush.radialGradient(
                    0f to Color(0xFF282F3A), 0.42f to Color(0xFF161B21), 0.95f to Color(0xFF090B0E),
                    center = Offset(w * 0.5f, h * 0.3f), radius = h,
                ),
                topLeft = Offset(-w, 0f), size = androidx.compose.ui.geometry.Size(w * 3, h),
            )
        }
        val cx = f.cx * density
        val cy = f.cy * density
        val rx = max(1f, f.rx * density)
        val ry = max(1f, f.ry * density)
        // pool of light and contact shadow, as circles squashed to the plinth's ellipse
        scale(1f, ry / rx, Offset(cx, cy)) {
            val pool = 0.12f * (1f + 0.8f * f.sweep)
            drawCircle(
                Brush.radialGradient(listOf(Color(0.22f, 0.24f, 0.27f, pool), Color.Transparent), Offset(cx, cy), rx * 2.6f * 1.3f),
                radius = rx * 2.6f * 1.3f, center = Offset(cx, cy),
            )
            drawCircle(
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.88f), 0.6f to Color.Black.copy(alpha = 0.82f),
                    1f to Color.Black.copy(alpha = 0f), center = Offset(cx, cy), radius = rx * 1.45f,
                ),
                radius = rx * 1.45f, center = Offset(cx, cy),
            )
        }
        // vignette
        drawRect(
            Brush.radialGradient(
                0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.38f),
                center = Offset(w / 2, h / 2), radius = h * 0.85f / 0.85f * 0.6f,
            ),
        )
    }
}
