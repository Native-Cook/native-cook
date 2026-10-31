package com.ngocdevv.nativecook.blocks.cards.tapridepass

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * AGSL thermal paper: warm fibres, a faint sheen, darkened edges and lower curl shading. The shader
 * receives local paper dimensions in dp; pre-33 devices use the existing flat-paper fallback.
 */
@RequiresApi(33)
internal class TapRidePassPaperRuntime {
    private val paper =
        RuntimeShader(
            """
            uniform float2 uSize;
            half4 main(float2 localPosition) {
                localPosition -= uSize / 2.0;
                float2 normalizedPosition = localPosition / uSize + 0.5;
                float fineGrain =
                    fract(sin(dot(floor(localPosition * 2.0), float2(12.9898, 78.233))) * 43758.5453);
                float fibreGrain =
                    fract(sin(dot(floor(localPosition * float2(0.5, 3.0)), float2(39.346, 11.135))) * 24634.6345);
                half3 paperColor =
                    half3(0.960, 0.945, 0.915) - half3(0.045, 0.040, 0.034) * half(normalizedPosition.y);
                paperColor += half((fineGrain - 0.5) * 0.026 + (fibreGrain - 0.5) * 0.012);
                float sheen =
                    exp(-pow((normalizedPosition.x * 0.6 + normalizedPosition.y * 0.4 - 0.42) / 0.18, 2.0));
                paperColor += half3(0.03, 0.03, 0.028) * half(sheen);
                float2 edgeDistances = min(normalizedPosition, 1.0 - normalizedPosition) * uSize;
                float edgeShade = 1.0 - smoothstep(0.0, 6.0, min(edgeDistances.x, edgeDistances.y));
                paperColor -= half3(0.06, 0.055, 0.05) * half(edgeShade);
                paperColor -= half3(0.025, 0.024, 0.022) * half(smoothstep(0.55, 1.0, normalizedPosition.y));
                return half4(paperColor, 1.0);
            }
        """
        )

    /** Updates local paper dimensions in dp and reuses the compiled shader. */
    fun shader(width: Double, height: Double): RuntimeShader {
        paper.setFloatUniform("uSize", width.toFloat(), height.toFloat())
        return paper
    }
}
