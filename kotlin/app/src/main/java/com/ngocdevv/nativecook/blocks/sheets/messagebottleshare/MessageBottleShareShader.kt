package com.ngocdevv.nativecook.blocks.sheets.messagebottleshare

internal object MessageBottleShareShader {
    const val SOURCE = """
uniform float2 uRes;
uniform float uHorizon;     // pt
uniform float2 uPos;        // the bottle's centre (pt)
uniform float uAngle;       // rad
uniform float uScale;       // pt per unit
uniform float uFloat;       // 1 floating at the shore … 0 in the air
uniform float uAlpha;       // the bottle's opacity (it fades into the distance)
uniform float3 uSplash;     // where a bottle landed (x, y) and the ripple's age in s (< 0 none)
uniform float uT;           // seconds into the loop
uniform float uLoop;
uniform float uPx;

const float TAU = 6.2831853;
float sq(float v) { return v*v; }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7)))*43758.5453); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float box(float2 p, float2 b, float r) { float2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float seg(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0)); }

float waveY(float x) {
  float ph = uT/uLoop*TAU;
  return 5.0*sin(x*0.018 + ph*2.0) + 3.0*sin(x*0.041 - ph*3.0);
}

float3 scene(float2 xy) {
  float hz = uHorizon;
  if (xy.y < hz) {
    float y = xy.y/hz;
    float3 col = mix(float3(0.78, 0.76, 0.9), float3(0.99, 0.84, 0.74), smoothstep(0.1, 1.0, y));
    // the low sun just above the horizon
    float2 sp = xy - float2(uRes.x*0.64, hz - 34.0);
    col += float3(1.0, 0.86, 0.66)*(0.35*exp(-dot(sp, sp)/500.0) + 0.14*exp(-length(sp)/80.0));
    return col;
  }
  // the sea: pale at the horizon, deeper towards the shore
  float d = (xy.y - hz)/(uRes.y - hz);
  float3 col = mix(mix(float3(0.95, 0.8, 0.78), float3(0.62, 0.62, 0.78), smoothstep(0.0, 0.35, d)), float3(0.24, 0.4, 0.52), smoothstep(0.3, 1.0, d));
  // wave bands that tighten with distance
  float ph = uT/uLoop*TAU;
  float z = 1.0/(d + 0.04);
  float band = sin(z*9.0 + xy.x*0.012*d + ph*3.0 + 2.0*sin(xy.x*0.02 + ph));
  col *= 0.98 + 0.025*band*smoothstep(0.0, 0.3, d);
  // the sun's glitter column: small soft sparks that change 24 times a loop (a whole number, so it loops)
  float gx = xy.x - uRes.x*0.64;
  float column = exp(-sq(gx/(14.0 + 110.0*d)));
  col += float3(1.0, 0.88, 0.72)*column*0.22*(1.0 - d*0.5);
  float2 cs = float2(3.0 + 7.0*d, 1.6 + 3.0*d);
  float2 cell = floor(xy/cs);
  float2 lc = fract(xy/cs) - 0.5;
  float flick = floor(mod(ph*24.0/TAU + hash(cell)*24.0, 24.0));
  float spark = step(0.9, hash(cell + flick*0.37))*exp(-dot(lc*float2(2.2, 3.5), lc*float2(2.2, 3.5))*4.0);
  col += float3(1.0, 0.94, 0.85)*column*spark*0.9*(1.0 - d*0.4);
  // a bottle's landing ripples, flattened by distance
  if (uSplash.z >= 0.0 && uSplash.z < 3.0) {
    float2 q = (xy - uSplash.xy)/float2(1.0, 0.22);
    float r = length(q);
    for (int k = 0; k < 3; k++) {
      float a = uSplash.z - float(k)*0.25;
      if (a > 0.0) col += float3(1.0, 0.95, 0.9)*0.45*sq(1.0 - a/3.0)*exp(-sq((r - 8.0 - 30.0*a)/(1.5 + a)));
    }
  }
  return col;
}

// the bottle's radius along its length (units)
float radiusAt(float x) { return mix(0.32, 0.11, smoothstep(0.3, 0.55, x)); }

half4 main(float2 xy) {
  float3 col = scene(xy);
  if (uAlpha <= 0.0) { }
  else {
    float c = cos(uAngle), s = sin(uAngle);
    float2 d0 = (xy - uPos)/uScale;
    float2 p = float2(c*d0.x + s*d0.y, -s*d0.x + c*d0.y);
    float body = box(p - float2(-0.25, 0.0), float2(0.55, 0.32), 0.14);
    float neck = seg(p, float2(0.2, 0.0), float2(0.98, 0.0)) - 0.11;
    float glass = smin(body, neck, 0.2);
    float lip = box(p - float2(0.98, 0.0), float2(0.035, 0.135), 0.03);
    float cork = box(p - float2(1.08, 0.0), float2(0.1, 0.1), 0.03);
    float px = 1.0/uScale;
    // the water line at the shore (screen space), for the part under it
    float under = uFloat*smoothstep(-1.0, 1.0, xy.y - (uPos.y + 2.0 + waveY(xy.x)));
    if (cork < px*2.0) {
      float3 k = mix(float3(0.45, 0.3, 0.18), float3(0.72, 0.52, 0.34), 0.5 + 0.5*clamp(-p.y/0.1, -1.0, 1.0));
      k *= 0.9 + 0.1*hash(floor(p*60.0));
      k = mix(k, k*float3(0.6, 0.75, 0.8), under*0.6);
      col = mix(col, k, uAlpha*(1.0 - smoothstep(-px, px, cork)));
    }
    if (min(glass, lip) < px*2.0) {
      float rl = radiusAt(p.x);
      float u = clamp(p.y/rl, -1.0, 1.0);
      float nz = sqrt(max(1.0 - u*u, 0.0));
      // what is behind, read through the glass and the water
      float2 off = float2(0.0, 6.0*under);
      float3 g = scene(xy + float2(-u*rl*0.5*uScale, 0.0) + off);
      // the letter inside: a cream roll with red twine
      float roll = seg(p, float2(-0.62, 0.0), float2(0.22, 0.0)) - 0.17;
      if (roll < 0.0) {
        float ru = clamp(p.y/0.17, -1.0, 1.0);
        float rn = sqrt(max(1.0 - ru*ru, 0.0));
        float3 paper = float3(0.97, 0.93, 0.83)*(0.62 + 0.38*rn);
        paper *= 0.97 + 0.03*sin(p.y*120.0);
        float twine = 1.0 - smoothstep(0.012, 0.022, abs(p.x + 0.18));
        paper = mix(paper, float3(0.72, 0.18, 0.14)*(0.6 + 0.4*rn), twine);
        g = mix(g, paper, 1.0 - smoothstep(-0.01, 0.0, roll));
      }
      float3 tint = float3(0.6, 0.85, 0.78);
      g *= mix(float3(1.0), tint, 0.55 + 0.45*pow(1.0 - nz, 1.2));
      g *= 1.0 - 0.4*pow(1.0 - nz, 1.6);
      g += float3(1.0)*0.5*exp(-sq((u + 0.55)/0.14))*smoothstep(0.98, 0.7, p.x);
      g += float3(1.0)*0.18*exp(-sq((u - 0.6)/0.2));
      g += float3(0.85, 1.0, 0.95)*0.3*smoothstep(0.8, 1.0, abs(u));
      // the lip: thicker glass
      float lipA = 1.0 - smoothstep(-px, px, lip);
      g = mix(g, g*0.8 + float3(0.15, 0.2, 0.18), lipA);
      g = mix(g, g*float3(0.55, 0.72, 0.75), under*0.6);
      col = mix(col, g, uAlpha*(1.0 - smoothstep(-px, px, min(glass, lip))));
    }
    // a bright line where the water meets the bottle
    if (uFloat > 0.0) {
      float wl = abs(xy.y - (uPos.y + 2.0 + waveY(xy.x)));
      col += float3(1.0, 0.95, 0.9)*0.35*uFloat*uAlpha*(1.0 - smoothstep(0.0, 1.5, wl))*(1.0 - smoothstep(-px*4.0, 0.0, glass));
    }
  }
  col += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(col), 1.0);
}
"""
}
