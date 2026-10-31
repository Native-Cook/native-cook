package com.ngocdevv.nativecook.blocks.modals.glasstilesfuse

internal object GlassTilesFuseShader {
    const val SOURCE = """
uniform float4 uWhite;   // centre x, centre y, size, angle
uniform float4 uGlass;   // centre x, centre y, size, angle
uniform float uFuse;
uniform float uSeam;     // -1 … 1, the light running across the fused tile
uniform float uCheck;    // 0 … 1, the connected chip between the tiles
uniform float uDensity;
uniform float uPixelRatio;
uniform float uLift;     // 0 … 1, the glass tile lifted by a finger

const float3 SHEET = float3(0.078, 0.078, 0.078);
float sq(float v) { return v*v; }
float2 rot(float2 p, float a) { float c=cos(a), s=sin(a); return float2(c*p.x+s*p.y, -s*p.x+c*p.y); }
float rbox(float2 p, float2 b, float r) { float2 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r; }
float smin(float a, float b, float k) { if (k <= 0.0) return min(a, b); float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0); return mix(b,a,h)-k*h*(1.0-h); }
float segD(float2 p, float2 a, float2 b) {
  float2 v = p - a, w = b - a;
  return length(v - w*clamp(dot(v, w)/dot(w, w), 0.0, 1.0));
}
float tileD(float2 p, float4 t) {
  float2 q = rot(p - t.xy, t.w);
  return rbox(q, float2(0.5*t.z), 0.27*t.z);
}
// our app's mark: four dots in a diamond
float markD(float2 p, float4 t) {
  float2 q = rot(p - t.xy, t.w)/t.z;
  float r = 0.105;
  float d = length(q - float2(-0.16, -0.05)) - r;
  d = min(d, length(q - float2(0.07, -0.13)) - r);
  d = min(d, length(q - float2(-0.07, 0.14)) - r);
  d = min(d, length(q - float2(0.16, 0.06)) - r);
  return d*t.z;
}
// the mail service's mark on the glass: an envelope chevron in a warm-to-cool gradient
float3 mailMark(float2 p, float4 t, out float a) {
  float2 q = rot(p - t.xy, t.w)/t.z;
  float2 e = float2(abs(q.x), q.y);
  float body = rbox(q - float2(0.0, 0.02), float2(0.27, 0.19), 0.05);
  float edge = abs(body) - 0.035;
  float2 v = e - float2(0.0, -0.01);
  float chev = abs(dot(v, normalize(float2(0.62, 1.0)))) - 0.035;
  chev = max(chev, max(e.x - 0.25, -(q.y + 0.17)));
  chev = max(chev, q.y - 0.02);
  float d = min(edge, chev);
  a = 1.0 - smoothstep(-0.006, 0.006, d);
  float u = clamp(q.x/0.6 + 0.5, 0.0, 1.0);
  return mix(mix(float3(1.0, 0.36, 0.30), float3(1.0, 0.70, 0.22), smoothstep(0.0, 0.5, u)), float3(0.42, 0.40, 1.0), smoothstep(0.45, 1.0, u));
}
// everything under the glass
float3 below(float2 p) {
  float3 c = SHEET;
  float dw = tileD(p, uWhite);
  // soft shadow of both tiles on the sheet
  float sh = exp(-max(tileD(p - float2(0.0, 10.0), uWhite), 0.0)/16.0)*0.55;
  sh = max(sh, exp(-max(tileD(p - float2(0.0, 12.0 + 8.0*uLift), uGlass), 0.0)/(18.0 + 10.0*uLift))*0.5);
  c *= 1.0 - sh;
  float wa = 1.0 - smoothstep(-0.5, 0.5, dw);
  // white tile: matte, lit from the top
  float2 q = rot(p - uWhite.xy, uWhite.w)/uWhite.z;
  float3 white = mix(float3(0.99), float3(0.86, 0.86, 0.85), smoothstep(-0.5, 0.6, q.y));
  white *= 1.0 - 0.08*smoothstep(-6.0, 0.0, dw);
  float dm = markD(p, uWhite);
  float3 dot3 = mix(float3(0.02), float3(0.16), smoothstep(-0.12*uWhite.z, 0.0, dm));
  white = mix(white, dot3, 1.0 - smoothstep(-0.5, 0.5, dm));
  return mix(c, white, wa);
}
float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 main(float2 pixelPosition) {
  float2 p = pixelPosition / uDensity;
  float3 col = below(p);
  float dg = tileD(p, uGlass);
  float dw = tileD(p, uWhite);
  // the glass shape: its own tile, flowing over the white tile as it fuses
  float k = 60.0*uFuse;
  float G = mix(dg, smin(dg, dw - 1.0, k), smoothstep(0.0, 0.25, uFuse));
  float glass = 1.0 - smoothstep(-0.6, 0.6, G);
  if (glass > 0.0) {
    // gradient of the shape: the lens normal at the bevel
    const float e = 0.75;
    float2 grad = float2(
      mix(tileD(p + float2(e, 0.0), uGlass), smin(tileD(p + float2(e, 0.0), uGlass), tileD(p + float2(e, 0.0), uWhite) - 1.0, k), smoothstep(0.0, 0.25, uFuse)) - G,
      mix(tileD(p + float2(0.0, e), uGlass), smin(tileD(p + float2(0.0, e), uGlass), tileD(p + float2(0.0, e), uWhite) - 1.0, k), smoothstep(0.0, 0.25, uFuse)) - G)/e;
    float bevel = 16.0;
    float h = clamp(-G/bevel, 0.0, 1.0);
    float bend = sq(1.0 - h);
    float2 disp = -grad*bend*22.0;
    // dispersion at the edge, and a slight frost from four taps
    float3 seen;
    seen.r = below(p + disp*1.08).r;
    seen.g = below(p + disp).g;
    seen.b = below(p + disp*0.92).b;
    float3 frost = (below(p + disp + float2(2.5, 1.0)) + below(p + disp + float2(-2.0, 2.0)) + below(p + disp + float2(1.0, -2.5)))/3.0;
    seen = mix(seen, frost, 0.35);
    // tinted smoky glass, lighter where it is thin
    float3 tint = float3(0.20, 0.20, 0.22);
    float3 g = mix(seen, tint, 0.26 + 0.22*h);
    // the mail mark sits on the glass tile itself
    float ma;
    float3 mark = mailMark(p, uGlass, ma);
    g = mix(g, mark, ma*(1.0 - 0.6*smoothstep(0.5, 1.0, uFuse)));
    // rim light: bright on the edges facing the light (upper left), a thin dark line outside
    float2 n = normalize(grad + 0.0001);
    float rim = smoothstep(-3.5, -0.5, G)*(1.0 - smoothstep(-0.5, 0.6, G));
    g += float3(1.0)*rim*(0.25 + 0.55*max(dot(n, normalize(float2(-0.6, -0.8))), 0.0));
    g += float3(1.0)*0.06*bend;
    // a diagonal light running across the fused tile
    float2 c0 = 0.5*(uGlass.xy + uWhite.xy);
    float band = exp(-sq(((p.x - c0.x) + (p.y - c0.y))/(uGlass.z*1.4) - uSeam*1.2)/0.012);
    g += float3(1.0, 0.98, 0.95)*0.55*band*smoothstep(0.3, 1.0, uFuse);
    col = mix(col, g, glass);
  }
  // the connected chip: a green dot with a check, joined to both tiles by a hairline
  if (uCheck > 0.0) {
    float2 mid = 0.5*(uWhite.xy + uGlass.xy);
    float r = 15.0*uCheck;
    float dc = length(p - mid) - r;
    float lineD = abs(p.y - mid.y) - 0.75;
    float span = abs(p.x - mid.x) - 0.5*abs(uGlass.x - uWhite.x);
    float line = (1.0 - smoothstep(-0.5, 0.5, lineD))*(1.0 - smoothstep(-0.5, 0.5, span))*smoothstep(0.5, 1.0, uCheck);
    line *= step(0.0, min(dw, dg));
    col = mix(col, float3(0.30), line*0.8);
    float2 q = (p - mid)/max(r, 0.001);
    float a1 = segD(q, float2(-0.3, 0.02), float2(-0.08, 0.24)) - 0.085;
    float a2 = segD(q, float2(-0.08, 0.24), float2(0.32, -0.2)) - 0.085;
    float ck = 1.0 - smoothstep(-0.03, 0.03, min(a1, a2));
    float disc = 1.0 - smoothstep(-0.6, 0.6, dc);
    float3 chip = mix(float3(0.20, 0.78, 0.42), float3(1.0), ck*step(0.5, uCheck));
    col = mix(col, chip, disc);
  }
  return half4(half3(toP3(clamp(col, 0.0, 1.0))), 1.0);
}
"""
}
