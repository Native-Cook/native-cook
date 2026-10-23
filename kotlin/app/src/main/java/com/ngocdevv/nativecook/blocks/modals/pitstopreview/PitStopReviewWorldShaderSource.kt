package com.ngocdevv.nativecook.blocks.modals.pitstopreview

/** Android AGSL port of world-shader.ts. Android's RuntimeShader already outputs in surface sRGB. */
internal object PitStopReviewWorldShaderSource {
    val AGSL = """
        uniform float2 res;
        uniform float4 w0;
        uniform float4 w1;

        float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }
        float h1(float n) { return fract(sin(n * 91.345 + 47.853) * 43758.5453); }
        float vn(float2 q) {
          float2 i = floor(q);
          float2 f = fract(q);
          f = f * f * (3.0 - 2.0 * f);
          float a = hs(i);
          float b = hs(i + float2(1.0, 0.0));
          float c = hs(i + float2(0.0, 1.0));
          float d = hs(i + float2(1.0, 1.0));
          return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }
        float laneBand(float y, float c, float hw, float k, float soft) {
          float e = abs(y - c) - hw;
          return k * (1.0 - smoothstep(-soft, soft + 0.6, e));
        }
        float paintBar(float wx, float len, float x0, float width) {
          float lo = max(wx - len, x0);
          float hi = min(wx, x0 + width);
          return max(0.0, hi - lo) / max(len, 1.0);
        }

        half4 main(float2 xy) {
          float worldOff = w0.x;
          float len = max(w0.y, 0.0);
          float hz = w0.z;
          float lines = w0.w;
          float hazeX = w1.x;
          float time = w1.y;
          float paint = w1.z;
          float speedy = clamp(len / 40.0, 0.0, 1.0);

          float2 p = xy;
          float2 hazeCenter = float2(hazeX - 34.0, 82.0);
          float2 hazeDelta = (p - hazeCenter) / float2(78.0, 38.0);
          float hazeMask = (1.0 - smoothstep(0.35, 1.0, length(hazeDelta))) * hz;
          if (hazeMask > 0.001) {
            float nx = vn(float2(p.x * 0.11 + time * 2.0, p.y * 0.16 - time * 9.0)) - 0.5;
            float ny = vn(float2(p.x * 0.13 - 7.3, p.y * 0.12 - time * 7.0)) - 0.5;
            p += float2(nx, ny) * 7.0 * hazeMask;
          }
          float wx = p.x + worldOff;
          float y = p.y;

          float sharpGrain = vn(float2(wx * 1.1, y * 1.1)) * 0.55 + vn(float2(wx * 0.28, y * 0.28)) * 0.45;
          float streakGrain = vn(float2(3.1, y * 0.62)) * 0.55 + vn(float2(7.7, y * 0.17)) * 0.45;
          float grain = mix(sharpGrain, streakGrain, speedy);
          float lum = 0.012 + (grain - 0.5) * mix(0.085, 0.025, speedy);

          float soft = mix(0.35, 1.6, speedy);
          float lanes = laneBand(y, 28.0, 2.6, 0.33, soft)
              + laneBand(y, 58.0, 0.6, 0.04, soft)
              + laneBand(y, 64.0, 0.8, 0.07, soft)
              + laneBand(y, 117.5, 2.2, 0.15, soft)
              + laneBand(y, 127.5, 1.8, 0.13, soft)
              + laneBand(y, 140.0, 0.8, 0.11, soft)
              + laneBand(y, 151.0, 3.2, 0.14, soft)
              + laneBand(y, 160.0, 0.6, 0.08, soft);
          lum += lanes * mix(1.35, 1.0, speedy);

          float boxRear = paintBar(wx, len, -2950.0, 3.0) * step(31.0, y) * (1.0 - step(149.0, y));
          float boxFront = paintBar(wx, len, -2636.0, 3.0) * step(31.0, y) * (1.0 - step(149.0, y));
          float stopLine = paintBar(wx, len, -2650.0, 18.0) * laneBand(y, 82.0, 1.2, 1.0, mix(0.3, 2.0, speedy));
          lum += (boxRear + boxFront) * 0.30 * paint + stopLine * 0.18 * paint;

          if (lines > 0.001) {
            float row = floor(y / 1.6);
            float pick = step(0.9, h1(row));
            float segment = vn(float2(xy.x * 0.0045 + time * 16.0 * (0.7 + h1(row + 3.0)), row * 3.7));
            lum += pick * smoothstep(0.55, 0.8, segment) * lines * (0.25 + 0.45 * h1(row + 9.0));
          }

          float envelope = smoothstep(0.0, 16.0, xy.y) * (1.0 - smoothstep(172.0, 214.0, xy.y));
          float alpha = clamp(lum, 0.0, 1.0) * envelope;
          return half4(half3(alpha), half(alpha));
        }
    """.trimIndent()
}
