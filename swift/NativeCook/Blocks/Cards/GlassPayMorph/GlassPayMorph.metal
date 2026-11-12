#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;

// Glass Pay Morph — the reference SkSL's two passes, in reference-screen point space.
// The packed uniforms have exactly the RN draw module's order (77 floats): px/lens,
// amber[8] and red[8] xyz trails, light state, inverse homography, card, fx, fx2, layer.
// Each entry selects its pass, so the final two layer floats are retained but unused.
// viewport = (reference-to-screen scale, screen y translation); no layout is rescaled here.
//
// RN's gamma-2.2 sRGB-to-P3 function is preserved value for value. Skia interprets that
// output as encoded Display P3; SwiftUI colorEffects use sRGB, so the entry points
// adapt that final P3 result to sRGB (the same host adapter as Glass Folder Burst).

namespace GlassPayMorphShader {

// Unlike the built-in, this also defines the reference's reversed frost edges.
inline float sstep(float edge0, float edge1, float x) {
    float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

struct Scene {
    float2 uPx;
    float3 uA[8];
    float3 uR[8];
    float4 uLt;
    float3 uH0;
    float3 uH1;
    float3 uH2;
    float4 uCard;
    float4 uFx;
    float4 uFx2;

    float3 toP3(float3 c) {
      float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
      float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
      return pow(m, float3(1.0 / 2.2));
    }
    float sq(float x) { return x * x; }
    float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
    float smin(float a, float b, float k) {
      float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
      return mix(b, a, h) - k * h * (1.0 - h);
    }

    // a capsule whose radius runs from a.z to b.z (exact "uneven capsule" distance; when one end
    // swallows the other, the larger circle)
    float seg2(float2 p, float3 a, float3 b) {
      float2 q0 = p - a.xy;
      float2 pb = b.xy - a.xy;
      float h = dot(pb, pb);
      float rb0 = b.z - a.z;
      if (h <= rb0 * rb0 + 0.01) {
        return min(length(q0) - a.z, length(p - b.xy) - b.z);
      }
      float2 q = float2(abs(dot(q0, float2(pb.y, -pb.x))), dot(q0, pb)) / h;
      float bb = a.z - b.z;
      float2 c = float2(sqrt(h - bb * bb), bb);
      float k = c.x * q.y - c.y * q.x;
      float m = dot(c, q);
      float n = dot(q, q);
      if (k < 0.0) { return sqrt(h * n) - a.z; }
      if (k > c.x) { return sqrt(h * (n + 1.0 - 2.0 * q.y)) - b.z; }
      return m - a.z;
    }
    float fieldA(float2 p) {
      float d = seg2(p, uA[0], uA[1]);
      for (int i = 1; i < 7; i++) { d = min(d, seg2(p, uA[i], uA[i + 1])); }
      return d;
    }
    float fieldR(float2 p) {
      float d = seg2(p, uR[0], uR[1]);
      for (int i = 1; i < 7; i++) { d = min(d, seg2(p, uR[i], uR[i + 1])); }
      return d;
    }

    float3 amberCol(float2 p) {
      float r = uA[0].z;
      float g = clamp(0.5 + dot(p - uA[0].xy, float2(0.7071, 0.7071)) / (2.0 * r), 0.0, 1.0);
      float3 rest = mix(float3(0.957, 0.733, 0.345), float3(0.933, 0.651, 0.208), g);
      return mix(rest, float3(0.910, 0.624, 0.122), uLt.z);
    }
    float3 redCol(float2 p) {
      float r = uR[0].z;
      float g = clamp(0.5 + dot(p - uR[0].xy, float2(0.7071, 0.7071)) / (2.0 * r), 0.0, 1.0);
      float3 rest = mix(float3(0.898, 0.157, 0.310), float3(0.839, 0.016, 0.200), g);
      float gx = clamp(0.5 + (p.x - uR[0].x) / (2.0 * r), 0.0, 1.0);
      float3 dock = mix(float3(0.839, 0.0, 0.169), float3(0.871, 0.043, 0.227), gx);
      return mix(rest, dock, uLt.z);
    }

    float3 page(float2 p) {
      float3 c = float3(0.106, 0.129, 0.208);
      float hz = 1.0 - sstep(0.0, 460.0, length(p - float2(40.0, 150.0)));
      c = mix(c, float3(0.150, 0.176, 0.255), hz);
      float sh = 1.0 - sstep(0.0, 520.0, length(p - float2(402.0, 874.0)));
      c = mix(c, float3(0.090, 0.102, 0.180), 0.6 * sh);
      return c;
    }

    // Everything behind the glass at q: the page, the light pools, and the lights that are behind
    // (crisp, or frosted where seen through the glass: ins = 1).
    float3 backD(float2 q, float ins, float dA, float dR) {
      float3 c = page(q);
      float pg = uFx2.z * (1.0 + 0.4 * ins);
      float pa = 1.0 - sstep(0.0, 1.2 * uA[0].z, max(dA, 0.0));
      float pr = 1.0 - sstep(0.0, 1.2 * uR[0].z, max(dR, 0.0));
      c += pg * 0.14 * (pa * pa * amberCol(q) + pr * pr * redCol(q));
      // seen through the glass a light loses its edge and some of its strength (the pin's amber reads
      // #CE9B48 40 pt inside, against #F4BB58 beside the glass)
      float S = 48.0;
      float px = uPx.x;
      float cA = mix(clamp(0.5 - dA / px, 0.0, 1.0), 0.8 * sstep(S, -S, dA + 0.3 * S), ins) * uLt.x;
      float cR = mix(clamp(0.5 - dR / px, 0.0, 1.0), 0.86 * sstep(S, -S, dR + 0.3 * S), ins) * uLt.y;
      c = mix(c, redCol(q), cR);
      c = mix(c, amberCol(q), cA);
      return c;
    }
    float3 back(float2 q, float ins) { return backD(q, ins, fieldA(q), fieldR(q)); }

    float2 toLocal(float2 p) {
      float3 v = float3(p, 1.0);
      float3 q = float3(dot(uH0, v), dot(uH1, v), dot(uH2, v));
      return q.xy / q.z;
    }
    float rrect(float2 l, float2 hs, float r) {
      float2 q = abs(l) - hs + r;
      return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    float4 over(float4 dst, float3 c, float a) { return float4(c * a + dst.rgb * (1.0 - a), a + dst.a * (1.0 - a)); }

    // The lights on top of the glass: crisp, fused where they meet (smooth-min), the overlap is the lens.
    // Premultiplied.
    float4 front(float2 p, float dA, float dR) {
      float px = uPx.x;
      float fa = 1.0 - uLt.x;
      float fr = 1.0 - uLt.y;
      float4 o = float4(0.0);
      if (fa + fr <= 0.0) { return o; }
      float covA = clamp(0.5 - dA / px, 0.0, 1.0) * fa;
      float covR = clamp(0.5 - dR / px, 0.0, 1.0) * fr;
      float k = max(uLt.w * fa * fr, 0.001);
      float covU = clamp(0.5 - smin(dA, dR, k) / px, 0.0, 1.0) * fa * fr;
      float fill = max(0.0, covU - max(covA, covR));
      float h = clamp(0.5 + 0.5 * (dR - dA) / max(k, 1.0), 0.0, 1.0);
      float3 colA = amberCol(p);
      float3 colR = redCol(p);
      float3 lens = mix(colR, colA, 0.76);
      float core = sstep(3.0, 14.0, min(-dA, -dR)) * uFx2.w;
      lens = mix(lens, float3(1.0, 0.698, 0.227), clamp(0.35 * core + uFx.x, 0.0, 1.0));
      o = over(o, colR, covR);
      o = over(o, colA, covA * (1.0 - covR));
      o = over(o, lens, covA * covR);
      o = over(o, mix(colR, colA, h), fill);
      return o;
    }

    half4 render(float2 p, bool frontLayer) {
      if (frontLayer) {
        float4 f = front(p, fieldA(p), fieldR(p));
        if (f.a <= 0.0) { return half4(0.0); }
        float3 col = f.rgb / f.a * (1.0 + 0.06 * uFx.y);
        return half4(half3(toP3(col) * f.a), half(f.a));
      }
      float px = uPx.x;
      float2 hs = uCard.xy;
      float rr = min(uCard.z, min(hs.x, hs.y));
      float2 l = toLocal(p);
      float d = rrect(l, hs, rr);
      float d1 = rrect(toLocal(p + float2(px, 0.0)), hs, rr);
      float d2 = rrect(toLocal(p + float2(0.0, px)), hs, rr);
      float2 g = float2(d1 - d, d2 - d) / px;
      float gl = max(length(g), 0.0001);
      float dS = d / gl;        // signed distance to the glass's edge, screen pt (negative inside)
      float2 n = g / gl;        // outward normal, screen
      float ins = clamp(0.5 - dS / px, 0.0, 1.0);

      float dA = fieldA(p);
      float dR = fieldR(p);
      float3 c = backD(p, 0.0, dA, dR);

      if (ins > 0.0) {
        // rim lens: inside a 14 pt band the glass shows what lies further out, so an edge hooks as it
        // crosses the rim; R and B split a little in that band only
        float band = sstep(-14.0, 0.0, dS);
        float disp = 9.0 * band * band * uPx.y;
        float3 cin;
        if (disp > 0.05) {
          float2 q = p + n * disp;
          float ch = 1.2 * band * uPx.y;
          cin.g = back(q, 1.0).g;
          cin.r = back(q + n * ch, 1.0).r;
          cin.b = back(q - n * ch, 1.0).b;
        } else {
          cin = backD(p, 1.0, dA, dR);
        }
        // the glass itself: a little whiteness, a thin highlight inside the top edge, shade inside the bottom
        cin = mix(cin, float3(1.0), uCard.w);
        float inner3 = 1.0 - sstep(0.0, 3.0, -dS);
        float inner4 = 1.0 - sstep(0.0, 4.0, -dS);
        cin = mix(cin, float3(1.0), 0.10 * inner3 * clamp(-n.y, 0.0, 1.0));
        cin = mix(cin, float3(0.0), 0.12 * inner4 * clamp(n.y, 0.0, 1.0));
        // specular band across the printed face as it comes round (right to left, 20 deg from vertical)
        if (uFx.z >= 0.0) {
          float xb = mix(hs.x + 60.0, -hs.x - 60.0, uFx.z);
          float dd = (l.x - xb) * 0.9397 + l.y * 0.3420;
          float sb = 1.0 - sstep(0.0, 20.0, abs(dd));
          cin = mix(cin, float3(1.0), 0.22 * sb * sin(3.14159 * uFx.z));
        }
        // the slab's edge while it is turned: a bright sliver along the receding side
        cin = mix(cin, float3(1.0), 0.7 * uFx2.y * (1.0 - sstep(0.0, 2.5, -dS)) * sstep(0.2, 0.8, abs(n.x)));
        c = mix(c, cin, ins);
      }

      // Fresnel rim: brighter on the lower right and along the top, a slow highlight crawling the top edge
      float rim = exp(-sq((dS + 0.75) / 0.75));
      float crawlX = -hs.x * 0.35 + 12.0 * sin(6.28318 * uFx2.x);
      float crawl = 0.22 * exp(-sq((l.x - crawlX) / 26.0)) * clamp(-n.y, 0.0, 1.0);
      float rimA = 0.26 + 0.34 * clamp(dot(n, float2(0.45, 0.89)), 0.0, 1.0) + 0.12 * clamp(-n.y, 0.0, 1.0) + crawl;
      float glint = uFx.w * exp(-sq(length(l - float2(hs.x - rr * 0.6, -hs.y + rr * 0.6)) / 9.0));
      c = mix(c, float3(1.0), clamp(rim * rimA + glint * 0.8, 0.0, 1.0));

      c *= 1.0 + 0.06 * uFx.y;
      c += (hash(floor(p / px)) - 0.5) * 0.024;
      return half4(half3(toP3(c)), 1.0);
    }

};

inline Scene load(device const float *u) {
    Scene s;
    s.uPx = float2(u[0], u[1]);
    for (int i = 0; i < 8; ++i) {
        int a = 2 + i * 3;
        int r = 26 + i * 3;
        s.uA[i] = float3(u[a], u[a + 1], u[a + 2]);
        s.uR[i] = float3(u[r], u[r + 1], u[r + 2]);
    }
    s.uLt = float4(u[50], u[51], u[52], u[53]);
    s.uH0 = float3(u[54], u[55], u[56]);
    s.uH1 = float3(u[57], u[58], u[59]);
    s.uH2 = float3(u[60], u[61], u[62]);
    s.uCard = float4(u[63], u[64], u[65], u[66]);
    s.uFx = float4(u[67], u[68], u[69], u[70]);
    s.uFx2 = float4(u[71], u[72], u[73], u[74]);
    return s;
}

// Standard Display P3 -> sRGB conversion after the source effect's encoded output.
inline half4 displaySRGB(half4 color) {
    if (color.a <= 0.0h) return half4(0.0h);
    float a = float(color.a);
    float3 c = float3(color.rgb) / a;
    c = select(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), c > 0.04045);
    c = float3(dot(c, float3(1.22474527, -0.22490437, 0.0)),
               dot(c, float3(-0.04205797, 1.04208100, 0.0)),
               dot(c, float3(-0.01964227, -0.07865492, 1.09853719)));
    c = max(c, 0.0);
    c = select(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, c > 0.0031308);
    return half4(half3(c * a), color.a);
}

} // namespace GlassPayMorphShader

[[ stitchable ]] half4 glassPayMorph(
    float2 position, half4 color, float2 viewport,
    device const float *uniforms, int uniformCount
) {
    if (uniformCount < 75) return half4(0.0h);
    GlassPayMorphShader::Scene s = GlassPayMorphShader::load(uniforms);
    float2 p = (position - float2(0.0, viewport.y)) / viewport.x;
    return GlassPayMorphShader::displaySRGB(s.render(p, false));
}

[[ stitchable ]] half4 glassPayMorphFront(
    float2 position, half4 color, float2 viewport,
    device const float *uniforms, int uniformCount
) {
    if (uniformCount < 75) return half4(0.0h);
    GlassPayMorphShader::Scene s = GlassPayMorphShader::load(uniforms);
    float2 p = (position - float2(0.0, viewport.y)) / viewport.x;
    return GlassPayMorphShader::displaySRGB(s.render(p, true));
}
