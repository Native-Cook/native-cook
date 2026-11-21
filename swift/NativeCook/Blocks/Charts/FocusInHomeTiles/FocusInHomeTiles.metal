#include <metal_stdlib>
#include <SwiftUI/SwiftUI_Metal.h>
using namespace metal;
// Analytic disc-kernel bokeh, cloud front and the single orange from the RN reference.
struct FocusInHomeTilesSCENEField {

float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 s) {
  if (uP3 < 0.5) return half3(s);
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// coverage of a disc kernel of radius r centred at signed distance d from an edge (exact for a straight edge)
float discCov(float d, float r) {
  float u = clamp(d / r, -1.0, 1.0);
  return (acos(u) - u * sqrt(1.0 - u * u)) * 0.3183099;
}


float uPx;       // pt per device pixel
float uGrainK;   // device pixels per pt (grain is per pixel: the sensor stays sharp)
float3 uPaper;
float4 uRect[27];  // x, y, w, h (screen pt)
float4 uPar[27];   // corner radius, circle of confusion (pt), alpha, kind (0 paper, 1 dark, 2 ring)
float4 uCol[27];   // sRGB, fringe sign (−1 in front of focus, +1 behind)

half4 render(float2 xy, texture2d<half> grain) {
  float g = float(grain.sample(sampler(coord::normalized,address::repeat,filter::linear),xy*uGrainK/float2(grain.get_width(),grain.get_height())).r);
  float3 col = uPaper + (g - 0.5);
  for (int i = 0; i < 27; i++) {
    float4 P = uPar[i];
    if (P.z <= 0.0) continue;
    float4 R = uRect[i];
    float2 b = R.zw * 0.5;
    float rad = min(P.x, min(b.x, b.y));
    float d = sdBox(xy - (R.xy + b), b, rad);
    float coc = max(P.y, uPx * 0.6);
    if (d > coc) continue;
    float3 sc = uCol[i].rgb;
    float a = P.z;
    if (P.w > 1.5) {
      // a 1.5 pt hairline ring at 20 %
      float cv = discCov(abs(d + 0.75) - 0.75, max(coc, uPx * 0.6));
      col = mix(col, sc, cv * 0.2 * a);
      continue;
    }
    // a shape thinner than its circle of confusion spreads its light: its peak drops
    float m = min(b.x, b.y);
    float spread = clamp(m / coc, 0.0, 1.0);
    spread = mix(1.0, spread, step(m, coc));
    float defo = smoothstep(1.5, 7.0, coc);
    if (P.w > 0.5) {
      float v = clamp(-d / coc, 0.0, 2.0);
      float rim = exp(-(v - 0.32) * (v - 0.32) * 14.0) * defo;
      sc = sc * (1.0 + 0.25 * rim) + 0.035 * rim;
      float f = uCol[i].w * 1.5 * uPx * defo;
      float cg = discCov(d + f, coc);
      float crb = discCov(d - f, coc);
      col = mix(col, sc, float3(crb, cg, crb) * a * spread);
    } else {
      float cv = discCov(d, coc) * spread;
      // a hairline of edge light along the top of a sharp paper tile (it is a sheet, not a print)
      float top = (1.0 - defo) * (1.0 - smoothstep(0.0, 1.2, abs(d + 0.6))) * (1.0 - smoothstep(-0.9, 0.2, (xy.y - R.y - rad) / max(rad, 1.0)));
      sc = mix(sc, float3(1.0), 0.55 * top * step(20.0, rad));
      col = mix(col, sc, cv * a);
    }
  }
  return half4(toOut(col), 1.0);
}

};
[[ stitchable ]] half4 focusInHomeTilesScene(float2 xy,constant float *p,int count, texture2d<half> grain) { FocusInHomeTilesSCENEField f; f.uP3=p[0]; f.uPx=p[1]; f.uGrainK=p[2]; f.uPaper=float3(p[3],p[4],p[5]); for(int i=0;i<27;i++) f.uRect[i]=float4(p[6+i*4+0],p[6+i*4+1],p[6+i*4+2],p[6+i*4+3]); for(int i=0;i<27;i++) f.uPar[i]=float4(p[114+i*4+0],p[114+i*4+1],p[114+i*4+2],p[114+i*4+3]); for(int i=0;i<27;i++) f.uCol[i]=float4(p[222+i*4+0],p[222+i*4+1],p[222+i*4+2],p[222+i*4+3]); return f.render(xy,grain); }
struct FocusInHomeTilesFRONTField {

float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 s) {
  if (uP3 < 0.5) return half3(s);
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// coverage of a disc kernel of radius r centred at signed distance d from an edge (exact for a straight edge)
float discCov(float d, float r) {
  float u = clamp(d / r, -1.0, 1.0);
  return (acos(u) - u * sqrt(1.0 - u * u)) * 0.3183099;
}

float uFront;
float uDir;
float uT;
float3 uForest;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

half4 render(float2 xy) {
  float diag = 0.35 * xy.x / 402.0 + 0.65 * xy.y / 874.0;
  float n = vnoise(xy / 170.0 + float2(uT * 0.06, 0.0)) * 0.65 + vnoise(xy / 64.0 - float2(0.0, uT * 0.04)) * 0.35;
  float e = uFront - diag - (n - 0.5) * 0.17;
  float m = smoothstep(-0.14, 0.14, e);
  if (uDir < 0.0) m = 1.0 - m;
  float a = 1.0 - m;
  if (a <= 0.0) return half4(0.0);
  // the dark has a little depth: lighter where the paper is about to break through
  float3 c = uForest * (1.0 + 0.16 * smoothstep(0.35, 1.0, m + 0.45 * (1.0 - m) * (1.0 - xy.y / 874.0)));
  return half4(toOut(c) * a, a);
}

};
[[ stitchable ]] half4 focusInHomeTilesFront(float2 xy,constant float *p,int count) { FocusInHomeTilesFRONTField f; f.uP3=p[0]; f.uFront=p[1]; f.uDir=p[2]; f.uT=p[3]; f.uForest=float3(p[4],p[5],p[6]); return f.render(xy); }
struct FocusInHomeTilesORANGEField {

float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 s) {
  if (uP3 < 0.5) return half3(s);
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// coverage of a disc kernel of radius r centred at signed distance d from an edge (exact for a straight edge)
float discCov(float d, float r) {
  float u = clamp(d / r, -1.0, 1.0);
  return (acos(u) - u * sqrt(1.0 - u * u)) * 0.3183099;
}

float uPx;
float4 uBox;    // centre x, y, w, h
float uRad;
float uTheta;
float uFlood;
float uLamp;
float uBreath;  // ±3 pt at 0.3 Hz
float uBlink;   // 0..1
float3 uOr;
float3 uOrL;
float3 uGrey;
float3 uTile;

half4 render(float2 xy) {
  float2 c = uBox.xy;
  float2 b = uBox.zw * 0.5 + uLamp * uBreath;
  float rad = min(uRad + uLamp * uBreath, min(b.x, b.y));
  float d = sdBox(xy - c, b, rad);
  float edge = mix(uPx * 0.7, 1.6, uLamp);
  float cov = 1.0 - smoothstep(-edge, edge, d);
  float size = max(uBox.z, uBox.w);
  float dotK = 1.0 - smoothstep(10.0, 30.0, size);

  // the bar: gradient along an axis that turns with uTheta
  float2 p = (xy - c) / max(b, float2(1.0));
  float ang = 3.14159265 * uTheta;
  float v = p.y * cos(ang) - p.x * sin(ang);
  float g = (v + 1.0) * 0.5;
  float axis = max(mix(2.0 * b.y, 2.0 * b.x, abs(sin(ang))), 1.0);
  float br = uBreath / axis;
  float seedK = clamp(44.0 / axis, 0.0, 1.0);
  float s1 = mix(seedK * 0.7, 0.55, uFlood) + br;
  float s2 = mix(seedK * 1.15, mix(0.78, 1.0, uTheta), uFlood) + br;
  float3 far = mix(uGrey, uTile, uTheta);
  float3 bar = g < s1 ? mix(uOr, uOrL, smoothstep(0.0, s1, g)) : mix(uOrL, far, smoothstep(s1, max(s2, s1 + 0.001), g));
  // it is a capsule: a soft cylindrical swell across its width
  float across = mix(p.x, p.y, abs(sin(ang)));
  bar *= 1.0 + 0.05 * (1.0 - across * across) - 0.03 * smoothstep(0.6, 1.0, abs(across));

  // the lamp: a disc of flat light with the bokeh's bright outer rim and faint onion rings
  float rr = -d;
  float rq = (rr - 3.5) / 3.2;
  float rim = exp(-rq * rq);
  float3 lamp = mix(uOr, uOrL, 0.24) * (0.99 + 0.007 * sin(rr * 0.21));
  // the core is a touch hotter than the field; the rim carries the +25 % gain
  lamp = mix(lamp, mix(uOrL, float3(1.0, 0.9, 0.74), 0.3), 0.12 * (1.0 - smoothstep(0.0, 0.8, length(p))));
  lamp = mix(lamp, mix(uOrL, float3(1.0, 0.88, 0.7), 0.4), 0.3 * rim);
  lamp *= 1.0 + 0.25 * rim;

  float3 col = mix(bar, lamp, uLamp);
  // the live dot: solid orange, blinking between full and 58 %
  float3 dot = uOr * mix(0.58, 1.0, uBlink);
  col = mix(col, dot, dotK);

  // halation on the dark round the lamp; a small glow round the live dot
  float halo = uLamp * 0.16 * exp(-max(d, 0.0) / 22.0) + dotK * (0.18 + 0.22 * uBlink) * exp(-max(d, 0.0) / 4.0);
  halo *= step(0.0, d);
  float3 hc = mix(uOr, uOrL, 0.3);
  float a = cov + halo * (1.0 - cov);
  float3 outc = (col * cov + hc * halo * (1.0 - cov)) / max(a, 1e-4);
  return half4(toOut(clamp(outc, 0.0, 1.0)) * a, a);
}

};
[[ stitchable ]] half4 focusInHomeTilesOrange(float2 xy,constant float *p,int count) { FocusInHomeTilesORANGEField f; f.uP3=p[0]; f.uPx=p[1]; f.uBox=float4(p[2],p[3],p[4],p[5]); f.uRad=p[6]; f.uTheta=p[7]; f.uFlood=p[8]; f.uLamp=p[9]; f.uBreath=p[10]; f.uBlink=p[11]; f.uOr=float3(p[12],p[13],p[14]); f.uOrL=float3(p[15],p[16],p[17]); f.uGrey=float3(p[18],p[19],p[20]); f.uTile=float3(p[21],p[22],p[23]); return f.render(xy); }
