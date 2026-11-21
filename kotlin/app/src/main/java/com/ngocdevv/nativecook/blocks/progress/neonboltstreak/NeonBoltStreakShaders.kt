package com.ngocdevv.nativecook.blocks.progress.neonboltstreak

internal object NeonBoltStreakShaders {
    const val BACKDROP = """
        uniform float2 uSize;
        uniform float uTime;
        half4 main(float2 p) {
          float2 uv = p / uSize;
          float vignette = 1.0 - 0.22 * dot(uv - .5, uv - .5);
          float grain = fract(sin(dot(floor(p), float2(12.9898,78.233))) * 43758.5453) - .5;
          float pulse = .012 * sin(6.2831853 * uTime / 9.5);
          return half4(float3(.149 + grain*.018 + pulse, .153 + grain*.018 + pulse, .169 + grain*.018) * vignette, 1.0);
        }
    """
    val HAZE: String = buildString {
        append("""
            uniform float uLit[7];
            uniform float uFill[7];
            uniform float uHead[7];
            float segD(float2 p, float2 a, float2 b, float maximum) {
                if (maximum <= 0.0) return 10000.0;
                float2 ab = b - a;
                float h = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, min(maximum, 1.0));
                return length(p - a - ab * h);
            }
            float litDistance(float2 q, float f) {
                float distance = 10000.0;
        """)
        for (chain in NeonBoltStreakTube.chains) {
            val lengths = chain.asList().zipWithNext { a, b -> kotlin.math.hypot(b[0] - a[0], b[1] - a[1]) }
            val total = lengths.sum()
            var walked = 0f
            for (i in lengths.indices) {
                val a = chain[i]; val b = chain[i + 1]
                append("distance = min(distance, segD(q, float2(${a[0]}, ${a[1]}), float2(${b[0]}, ${b[1]}), (f * $total - $walked) / ${lengths[i]}));\n")
                walked += lengths[i]
            }
        }
        append("""
                return distance;
            }
            float3 encode(float3 value) {
                return mix(value * 12.92, 1.055 * pow(max(value, float3(0.0)), float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), value));
            }
            half4 main(float2 p) {
                float strongest = 0.0;
                for (int k = 0; k < 7; k++) {
                    if (uHead[k] <= 0.001 || uFill[k] <= 0.0) continue;
                    float2 q = p - float2(16.0 + 370.0 / 7.0 * (float(k) + 0.5), 629.0);
                    float far = length(max(abs(q) - float2(18.0, 28.0), 0.0));
                    float dl = litDistance(q, min(uFill[k], 1.0));
                    float gain = uLit[k] * smoothstep(0.0, 0.3, uFill[k]) * (1.0 - smoothstep(80.0, 110.0, far));
                    strongest = max(strongest, 0.065 * smoothstep(4.0, 9.0, dl) * exp(-dl / 42.0) * gain * uHead[k]);
                }
                float edge = min(min(p.x + 32.0, 434.0 - p.x), min(p.y - 512.0, 776.0 - p.y));
                strongest *= smoothstep(0.0, 30.0, edge);
                // Convert the linear-light lift to a display-space addition on the dark panel.
                float3 base = float3(0.008568, 0.009134, 0.010960);
                float3 delta = encode(base + strongest * float3(0.9131, 0.5520, 0.0)) - encode(base);
                return half4(delta, 1.0);
            }
        """)
    }
}
