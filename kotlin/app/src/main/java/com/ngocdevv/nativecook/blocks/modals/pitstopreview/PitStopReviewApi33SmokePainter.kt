package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

@RequiresApi(33)
internal class PitStopReviewApi33SmokePainter {
    private val shader = RuntimeShader(SOURCE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@PitStopReviewApi33SmokePainter.shader }
    private val uniforms = FloatArray(PitStopReviewMotion.PUFF_SLOTS * 4)

    fun draw(canvas: Canvas, puffs: PitStopReviewMotion.PackedPuffs) {
        if (puffs.count == 0) return
        puffs.data.indices.forEach { index -> uniforms[index] = puffs.data[index].toFloat() }
        shader.setFloatUniform("puffs", uniforms)
        paint.alpha = 255
        canvas.drawRect(
            puffs.box[0].toFloat(),
            puffs.box[1].toFloat(),
            puffs.box[2].toFloat(),
            puffs.box[3].toFloat(),
            paint,
        )
    }

    private companion object {
        val SOURCE = """
            uniform float4 puffs[32];
            float hashSmoke(float2 q) {
              return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453);
            }
            float noiseSmoke(float2 q) {
              float2 i = floor(q);
              float2 f = fract(q);
              f = f * f * (3.0 - 2.0 * f);
              return mix(
                mix(hashSmoke(i), hashSmoke(i + float2(1.0, 0.0)), f.x),
                mix(hashSmoke(i + float2(0.0, 1.0)), hashSmoke(i + float2(1.0, 1.0)), f.x),
                f.y
              );
            }
            half4 main(float2 point) {
              float sum = 0.0;
              for (int i = 0; i < 32; i++) {
                float4 puff = puffs[i];
                if (puff.w > 0.002) {
                  float distanceToPuff = length(point - puff.xy) / max(puff.z, 0.5);
                  if (distanceToPuff < 1.0) {
                    float wisp = 0.75 + 0.5 * noiseSmoke(point * 0.09 + puff.xy * 0.013);
                    float fade = 1.0 - distanceToPuff;
                    sum += puff.w * fade * fade * (3.0 - 2.0 * fade) * wisp;
                  }
                }
              }
              float alpha = 1.0 - exp(-sum * 1.6);
              return half4(half3(0.91, 0.90, 0.93) * half(alpha), half(alpha));
            }
        """.trimIndent()
    }
}
