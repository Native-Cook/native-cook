#include <metal_stdlib>
using namespace metal;

struct MessageBottleShareUniforms {
    float2 uRes;
    float uHorizon;
    float uT;
    float2 uPos;
    float uAngle;
    float uScale;
    float uFloat;
    float uAlpha;
    float3 uSplash;
    float uLoop;
    float uPx;
};


constant float messageBottleShareTAU = 6.2831853;
float messageBottleShareSq(float v) { return v*v; }
float messageBottleShareHash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7)))*43758.5453); }
float messageBottleShareSmin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float messageBottleShareBox(float2 p, float2 b, float r) { float2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float messageBottleShareSeg(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0)); }

float messageBottleShareWaveY(float x, constant MessageBottleShareUniforms &u) {
  float ph = u.uT/u.uLoop*messageBottleShareTAU;
  return 5.0*sin(x*0.018 + ph*2.0) + 3.0*sin(x*0.041 - ph*3.0);
}

float3 messageBottleShareScene(float2 xy, constant MessageBottleShareUniforms &u) {
  float hz = u.uHorizon;
  if (xy.y < hz) {
    float y = xy.y/hz;
    float3 col = mix(float3(0.78, 0.76, 0.9), float3(0.99, 0.84, 0.74), smoothstep(0.1, 1.0, y));
    // the low sun just above the horizon
    float2 sp = xy - float2(u.uRes.x*0.64, hz - 34.0);
    col += float3(1.0, 0.86, 0.66)*(0.35*exp(-dot(sp, sp)/500.0) + 0.14*exp(-length(sp)/80.0));
    return col;
  }
  // the sea: pale at the horizon, deeper towards the shore
  float d = (xy.y - hz)/(u.uRes.y - hz);
  float3 col = mix(mix(float3(0.95, 0.8, 0.78), float3(0.62, 0.62, 0.78), smoothstep(0.0, 0.35, d)), float3(0.24, 0.4, 0.52), smoothstep(0.3, 1.0, d));
  // wave bands that tighten with distance
  float ph = u.uT/u.uLoop*messageBottleShareTAU;
  float z = 1.0/(d + 0.04);
  float band = sin(z*9.0 + xy.x*0.012*d + ph*3.0 + 2.0*sin(xy.x*0.02 + ph));
  col *= 0.98 + 0.025*band*smoothstep(0.0, 0.3, d);
  // the sun's glitter column: small soft sparks that change 24 times a loop (a whole number, so it loops)
  float gx = xy.x - u.uRes.x*0.64;
  float column = exp(-messageBottleShareSq(gx/(14.0 + 110.0*d)));
  col += float3(1.0, 0.88, 0.72)*column*0.22*(1.0 - d*0.5);
  float2 cs = float2(3.0 + 7.0*d, 1.6 + 3.0*d);
  float2 cell = floor(xy/cs);
  float2 lc = fract(xy/cs) - 0.5;
  float flick = floor(fmod(ph*24.0/messageBottleShareTAU + messageBottleShareHash(cell)*24.0, 24.0));
  float spark = step(0.9, messageBottleShareHash(cell + flick*0.37))*exp(-dot(lc*float2(2.2, 3.5), lc*float2(2.2, 3.5))*4.0);
  col += float3(1.0, 0.94, 0.85)*column*spark*0.9*(1.0 - d*0.4);
  // a bottle's landing ripples, flattened by distance
  if (u.uSplash.z >= 0.0 && u.uSplash.z < 3.0) {
    float2 q = (xy - u.uSplash.xy)/float2(1.0, 0.22);
    float r = length(q);
    for (int k = 0; k < 3; k++) {
      float a = u.uSplash.z - float(k)*0.25;
      if (a > 0.0) col += float3(1.0, 0.95, 0.9)*0.45*messageBottleShareSq(1.0 - a/3.0)*exp(-messageBottleShareSq((r - 8.0 - 30.0*a)/(1.5 + a)));
    }
  }
  return col;
}

// the bottle's radius along its length (units)
float messageBottleShareRadiusAt(float x) { return mix(0.32, 0.11, smoothstep(0.3, 0.55, x)); }

half4 messageBottleSharePixel(float2 xy, constant MessageBottleShareUniforms &u) {
  float3 col = messageBottleShareScene(xy, u);
  if (u.uAlpha <= 0.0) { }
  else {
    float c = cos(u.uAngle), s = sin(u.uAngle);
    float2 d0 = (xy - u.uPos)/u.uScale;
    float2 p = float2(c*d0.x + s*d0.y, -s*d0.x + c*d0.y);
    float body = messageBottleShareBox(p - float2(-0.25, 0.0), float2(0.55, 0.32), 0.14);
    float neck = messageBottleShareSeg(p, float2(0.2, 0.0), float2(0.98, 0.0)) - 0.11;
    float glass = messageBottleShareSmin(body, neck, 0.2);
    float lip = messageBottleShareBox(p - float2(0.98, 0.0), float2(0.035, 0.135), 0.03);
    float cork = messageBottleShareBox(p - float2(1.08, 0.0), float2(0.1, 0.1), 0.03);
    float px = 1.0/u.uScale;
    // the water line at the shore (screen space), for the part under it
    float under = u.uFloat*smoothstep(-1.0, 1.0, xy.y - (u.uPos.y + 2.0 + messageBottleShareWaveY(xy.x, u)));
    if (cork < px*2.0) {
      float3 k = mix(float3(0.45, 0.3, 0.18), float3(0.72, 0.52, 0.34), 0.5 + 0.5*clamp(-p.y/0.1, -1.0, 1.0));
      k *= 0.9 + 0.1*messageBottleShareHash(floor(p*60.0));
      k = mix(k, k*float3(0.6, 0.75, 0.8), under*0.6);
      col = mix(col, k, u.uAlpha*(1.0 - smoothstep(-px, px, cork)));
    }
    if (min(glass, lip) < px*2.0) {
      float rl = messageBottleShareRadiusAt(p.x);
      float v = clamp(p.y/rl, -1.0, 1.0);
      float nz = sqrt(max(1.0 - v*v, 0.0));
      // what is behind, read through the glass and the water
      float2 off = float2(0.0, 6.0*under);
      float3 g = messageBottleShareScene(xy + float2(-v*rl*0.5*u.uScale, 0.0) + off, u);
      // the letter inside: a cream roll with red twine
      float roll = messageBottleShareSeg(p, float2(-0.62, 0.0), float2(0.22, 0.0)) - 0.17;
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
      g += float3(1.0)*0.5*exp(-messageBottleShareSq((v + 0.55)/0.14))*smoothstep(0.98, 0.7, p.x);
      g += float3(1.0)*0.18*exp(-messageBottleShareSq((v - 0.6)/0.2));
      g += float3(0.85, 1.0, 0.95)*0.3*smoothstep(0.8, 1.0, abs(v));
      // the lip: thicker glass
      float lipA = 1.0 - smoothstep(-px, px, lip);
      g = mix(g, g*0.8 + float3(0.15, 0.2, 0.18), lipA);
      g = mix(g, g*float3(0.55, 0.72, 0.75), under*0.6);
      col = mix(col, g, u.uAlpha*(1.0 - smoothstep(-px, px, min(glass, lip))));
    }
    // a bright line where the water meets the bottle
    if (u.uFloat > 0.0) {
      float wl = abs(xy.y - (u.uPos.y + 2.0 + messageBottleShareWaveY(xy.x, u)));
      col += float3(1.0, 0.95, 0.9)*0.35*u.uFloat*u.uAlpha*(1.0 - smoothstep(0.0, 1.5, wl))*(1.0 - smoothstep(-px*4.0, 0.0, glass));
    }
  }
  float3 l = pow(max(col, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  float3 o = pow(max(m, float3(0.0)), float3(1.0/2.2));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}

vertex float4 messageBottleShareVertex(uint id [[vertex_id]]) {
    float2 p = float2((id << 1) & 2, id & 2);
    return float4(p * 2.0 - 1.0, 0.0, 1.0);
}
fragment half4 messageBottleShareFragment(float4 position [[position]], constant MessageBottleShareUniforms &u [[buffer(0)]]) {
    return messageBottleSharePixel(position.xy * u.uPx, u);
}
