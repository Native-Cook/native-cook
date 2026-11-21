// Direct translations of ipc-shaders.ts. The SwiftUI surface uses non-linear sRGB;
// the RN-only Display P3 output compensation is therefore intentionally the identity.
#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
half3 islandPrintCameraToOut(half3 s) { return s; }




float islandPrintCameraBodyHash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

float islandPrintCameraSdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

[[ stitchable ]] half4 islandPrintCameraBody(float2 xy, half4 color, float4 uBody, float uRad, float uBloom, float uLip, float uDip, float uShellFx) {
  float2 b = uBody.zw * 0.5;
  float2 c = uBody.xy + b;
  float r = min(uRad, min(b.x, b.y));
  float2 bb = b + float2(0.0, uDip * 0.5);
  float2 cc = c + float2(0.0, uDip * 0.5);
  float d = islandPrintCameraSdBox(xy - cc, bb, r);

  half4 col = half4(0.0);
  // the shell's moulded plastic: a soft vertical sheen (white 6 % at the top to black 8 % at 420 pt) and a fine grain
  if (uShellFx > 0.5) {
    float sy = clamp(xy.y / 420.0, 0.0, 1.0);
    float wa = 0.06 * (1.0 - smoothstep(0.0, 0.55, sy));
    float ba = 0.08 * smoothstep(0.45, 1.0, sy);
    col = half4(half3(1.0) * wa, wa);
    col = half4(col.rgb * (1.0 - ba), ba + col.a * (1.0 - ba));
    float g = (islandPrintCameraBodyHash(floor(xy * 2.0)) - 0.5) * 0.06;
    float ga = abs(g);
    half3 gc = g > 0.0 ? half3(1.0) : half3(0.0);
    col = half4(gc * ga + col.rgb * (1.0 - ga), ga + col.a * (1.0 - ga));
  }

  // contact shadow under the body, tinted dark red: [0, 6, 14, 0.30]
  float ds = islandPrintCameraSdBox(xy - cc - float2(0.0, 6.0), bb, r);
  float sh = 0.30 * uLip * (1.0 - smoothstep(-4.0, 14.0, ds));
  half3 shc = islandPrintCameraToOut(half3(0.478, 0.059, 0.047));
  col = half4(shc * sh + col.rgb * (1.0 - sh), sh + col.a * (1.0 - sh));

  // the lip: a solid 3 pt bevel of red plastic round the cut-out, lit on its top arc, shaded below, with a thin
  // specular line on its outer top edge so it reads as a raised rim
  float band = 1.0 - smoothstep(2.4, 3.4, d);
  float ny = clamp((xy.y - cc.y) / max(bb.y, 1.0), -1.0, 1.0);
  half3 lit = islandPrintCameraToOut(half3(0.957, 0.329, 0.290));
  half3 shade = islandPrintCameraToOut(half3(0.725, 0.133, 0.118));
  float k = smoothstep(-0.6, 0.6, ny);
  float la = band * 0.95 * clamp(uLip * 1.4, 0.35, 1.0);
  half3 lc = mix(lit, shade, k);
  col = half4(lc * la + col.rgb * (1.0 - la), la + col.a * (1.0 - la));
  float spec = (1.0 - smoothstep(0.0, 0.9, abs(d - 2.6))) * (1.0 - k) * 0.35 * uLip;
  col = half4(half3(1.0) * spec + col.rgb * (1.0 - spec), spec + col.a * (1.0 - spec));

  // white flash bloom on the shell round the body
  float bl = uBloom * exp(-max(d, 0.0) / 40.0) * step(0.0, d);
  col = half4(half3(bl) + col.rgb * (1.0 - bl), bl + col.a * (1.0 - bl));

  // the bezel (anti-aliased over ~1 px)
  float inside = 1.0 - smoothstep(-0.6, 0.6, d);
  half3 bez = islandPrintCameraToOut(half3(0.039, 0.024, 0.027));
  return half4(bez * inside + col.rgb * (1.0 - inside), inside + col.a * (1.0 - inside));
}


[[ stitchable ]] half4 islandPrintCameraDome(float2 xy, half4 color, float2 origin, float2 uC, float uR, float uRise, float uDepth, float uFlood, float3 uDome, float3 uShell, float uDim, float uHalo, float3 uHaloCol, float uRing, float uDot) {
  xy += origin;
  float2 q = xy - uC;
  float rr = length(q) / uR;
  float rise = max(uRise, 0.0);
  float H = (uRing > 0.5 ? 0.02 : 0.22) * 2.0 * uR * rise * (1.0 - 0.8 * uDepth);
  half4 col = half4(0.0);

  // outside: foot shadow (contact + ambient, tinted), AO, LED halo
  float dpt = length(q - float2(0.0, 2.0 * rise)) - uR;
  float sa = rise * (uRing > 0.5 ? 0.0 : 1.0) * (1.0 - 0.6 * uDepth) * (0.30 * (1.0 - smoothstep(-1.0, 3.0, dpt)) + 0.18 * (1.0 - smoothstep(-2.0, 14.0, dpt - 4.0)));
  half3 tint = islandPrintCameraToOut(half3(0.478, 0.059, 0.047));
  col = half4(tint * sa, sa);
  if (uHalo > 0.0) {
    float h = uHalo * clamp(rise * 4.0, 0.0, 1.0) * exp(-max(length(q) - uR, 0.0) / 16.0) * step(uR, length(q));
    half3 hc = islandPrintCameraToOut(half3(uHaloCol));
    col = half4(hc * h + col.rgb * (1.0 - h), h + col.a * (1.0 - h));
  }

  if (rr < 1.0) {
    float s = sqrt(max(1.0 - rr * rr, 1e-4));
    // height h = s * H; gradient dh/dq = -q / (uR^2 s) * H
    float2 g = -q / (uR * uR * s) * H;
    float3 n = normalize(float3(-g.x, -g.y, 1.0));
    float3 L = normalize(float3(0.0, -0.82, 0.57));
    float diff = clamp(dot(n, L), 0.0, 1.0);
    // flood from the top of the dome downwards
    float v = (xy.y - (uC.y - uR)) / (2.0 * uR);
    float fl = 1.0 - smoothstep(uFlood * 1.25 - 0.25, uFlood * 1.25, v);
    float3 base = mix(uShell, uDome, uRing > 0.5 ? 0.0 : fl);
    float3 lit = base * (0.62 + 0.48 * diff);
    // soft highlight 18 % above centre (roughness ~0.45)
    float3 hv = normalize(L + float3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(n, hv), 0.0), 22.0) * 0.32 * clamp(rise, 0.0, 1.0) * (1.0 - 0.3 * uDepth);
    lit += float3(spec);
    // AO at the foot
    lit *= 1.0 - 0.4 * smoothstep(0.75, 1.0, rr) * clamp(rise, 0.0, 1.0);
    lit *= uDim;
    half3 o = islandPrintCameraToOut(half3(clamp(lit, 0.0, 1.0)));
    // engraved ring of the flip button
    if (uRing > 0.5) {
      float ring = 1.0 - smoothstep(0.55, 1.05, abs(length(q) - (uR - 0.9)));
      o = mix(o, islandPrintCameraToOut(half3(0.612, 0.086, 0.071)), ring);
    }
    // a sharp 2 pt specular dot on the LED's glass
    if (uDot > 0.5) {
      float dd = length(q - float2(-0.30, -0.34) * uR);
      float dt = (1.0 - smoothstep(0.6, 1.4, dd)) * 0.75 * clamp(rise, 0.0, 1.0);
      o = mix(o, half3(1.0), dt);
    }
    float a = 1.0 - smoothstep(0.985, 1.0, rr);
    // a flat dome (rise 0) is invisible: the page is the same red
    float vis = clamp(rise * 6.0, 0.0, 1.0);
    a *= vis;
    if (uRing > 0.5) {
      float ring2 = 1.0 - smoothstep(0.55, 1.05, abs(length(q) - (uR - 0.9)));
      a *= ring2;
    }
    col = half4(o * a + col.rgb * (1.0 - a), a + col.a * (1.0 - a));
  }
  return col;
}

float islandPrintCameraHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float islandPrintCameraNoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(islandPrintCameraHash(i), islandPrintCameraHash(i + float2(1, 0)), u.x), mix(islandPrintCameraHash(i + float2(0, 1)), islandPrintCameraHash(i + float2(1, 1)), u.x), u.y);
}

[[ stitchable ]] half4 islandPrintCameraDevelop(float2 xy, half4 color, float uD, float2 uSize) {
  xy += float2(13.0, 22.0);
  half4 ph = color;
  float3 c = float3(ph.rgb);
  float n = (islandPrintCameraNoise(xy / uSize * 4.0) * 0.65 + islandPrintCameraNoise(xy / uSize * 9.0) * 0.35 - 0.5) * 0.16;
  float dd = uD + n;
  // contrast 0.7 -> 1, saturation 0.5 -> 1
  float con = mix(0.7, 1.0, clamp(uD, 0.0, 1.0));
  float sat = mix(0.5, 1.0, clamp(uD, 0.0, 1.0));
  float y = dot(c, float3(0.299, 0.587, 0.114));
  c = mix(float3(y), c, sat);
  c = (c - 0.5) * con + 0.5;
  float3 base = float3(0.878, 0.878, 0.886);
  float3 ghost = mix(float3(0.80,0.87,0.89),float3(0.20,0.33,0.39),1.0-y);
  float3 o = mix(base,ghost,smoothstep(0.0,0.45,dd));
  float kg=smoothstep(0.35,0.80,dd), kb=smoothstep(0.45,0.90,dd), kr=smoothstep(0.55,1.00,dd);
  o=float3(mix(o.r,c.r,kr),mix(o.g,c.g,kg),mix(o.b,c.b,kb));
  return half4(islandPrintCameraToOut(half3(clamp(o, 0.0, 1.0))), 1.0);
}


float islandPrintCameraShellHash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
[[ stitchable ]] half4 islandPrintCameraShell(float2 xy, float uH) {
  float sy = clamp(xy.y / uH, 0.0, 1.0);
  float wa = 0.06 * (1.0 - smoothstep(0.0, 0.4, sy));
  float ba = 0.08 * smoothstep(0.35, 1.0, sy);
  half4 col = half4(half3(1.0) * wa, wa);
  col = half4(col.rgb * (1.0 - ba), ba + col.a * (1.0 - ba));
  float g = (islandPrintCameraShellHash(floor(xy * 2.0)) - 0.5) * 0.06;
  float ga = abs(g);
  half3 gc = g > 0.0 ? half3(1.0) : half3(0.0);
  return half4(gc * ga + col.rgb * (1.0 - ga), ga + col.a * (1.0 - ga));
}