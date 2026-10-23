package com.ngocdevv.nativecook.blocks.forms.glowtogglechecklist

/** Match the reference's P3 emission on Android's sRGB capture without a full-screen extra pass. */
internal object GlowChecklistOutputColour {
    const val AGSL = """
        float linearChannel(float n) {
            return n <= 0.04045 ? n / 12.92 : pow((n + 0.055) / 1.055, 2.4);
        }
        float encodedChannel(float n) {
            n = max(0.0, n);
            return n <= 0.0031308 ? n * 12.92 : 1.055 * pow(n, 1.0 / 2.4) - 0.055;
        }
        half4 outputColour(float4 pixel) {
            float3 c = pixel.rgb / max(pixel.a, 0.00001);
            float3 l = float3(linearChannel(c.r), linearChannel(c.g), linearChannel(c.b));
            float3 s = float3(
                1.224745 * l.r - 0.224904 * l.g,
                -0.042058 * l.r + 1.042081 * l.g,
                -0.019642 * l.r - 0.078655 * l.g + 1.098537 * l.b);
            float3 rgb = clamp(float3(encodedChannel(s.r), encodedChannel(s.g), encodedChannel(s.b)), 0.0, 1.0);
            return half4(rgb * pixel.a, pixel.a);
        }
    """
}
