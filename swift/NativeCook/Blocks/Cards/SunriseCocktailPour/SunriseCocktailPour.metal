#include <metal_stdlib>
using namespace metal;
struct SunriseCocktailPourShader {

float2 uRes;
float2 uC;          // glass centre (pt)
float uS;           // pt per unit
float uShift;       // the glass slid sideways, units
float uLevel;       // liquid top, units (base of the cavity at −0.95)
float uRed;         // grenadine settled, 0 … 1
float uRise;        // how far the red gradient rises, 0 … 1
float uMix;         // stirred into one colour, 0 … 1
float uStream;      // the pour stream, 0 … 1 (0 none)
float uSwirl;       // the ice turning while stirred, rad
float uPhase;
float uPx;

const float R_OUT = 0.62;
const float R_IN = 0.54;
const float TOP = 1.1;
const float BOTTOM = -1.2;
const float FLOOR = -0.95;
float sunriseCocktailPourSq(float v) { return v*v; }
float sunriseCocktailPourDot2(float2 v) { return dot(v, v); }
float3 sunriseCocktailPourRx(float3 v, float a) { float c = cos(a), s = sin(a); return float3(v.x, c*v.y - s*v.z, s*v.y + c*v.z); }

float sunriseCocktailPourSdCylinder(float3 p, float r, float y0, float y1) {
  float2 d = float2(length(p.xz) - r, max(y0 - p.y, p.y - y1));
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}
float sunriseCocktailPourSdGlass(float3 p) {
  float outer = sunriseCocktailPourSdCylinder(p, R_OUT, BOTTOM, TOP) - 0.02;
  float inner = sunriseCocktailPourSdCylinder(p, R_IN, FLOOR, TOP + 0.5);
  return max(outer, -inner);
}
float sunriseCocktailPourSdIce(float3 p, float i) {
  float a = uSwirl*(0.8 + 0.3*i) + i*2.1;
  float3 c = float3(cos(a)*0.24, uLevel - 0.12 - 0.05*i + 0.015*sin(uPhase + i*1.7), sin(a)*0.2);
  float3 q = p - c;
  float ca = cos(a + i), sa = sin(a + i);
  q = float3(ca*q.x + sa*q.z, q.y, -sa*q.x + ca*q.z);
  q = sunriseCocktailPourRx(q, 0.4 + 0.3*i);
  float3 b = abs(q) - float3(0.15) + 0.04;
  return length(max(b, 0.0)) + min(max(b.x, max(b.y, b.z)), 0.0) - 0.04;
}
float sunriseCocktailPourSdIces(float3 p) { return min(sunriseCocktailPourSdIce(p, 0.0), min(sunriseCocktailPourSdIce(p, 1.0), sunriseCocktailPourSdIce(p, 2.0))); }

// the colour of the drink at a height, before and after stirring
float3 sunriseCocktailPourDrinkAt(float y) {
  float3 orange = float3(1.0, 0.68, 0.22);
  float3 red = float3(0.85, 0.12, 0.2);
  float3 coral = float3(1.0, 0.56, 0.36);
  // the grenadine settles in a layer at the bottom and its colour climbs as a gradient
  float h = (y - FLOOR)/max(uLevel - FLOOR, 0.01);
  float layer = uRed*(1.0 - smoothstep(0.1 + 0.18*uRise, 0.3 + 0.42*uRise, h));
  float3 c = mix(orange, red, layer);
  return mix(c, coral, uMix);
}

float3 sunriseCocktailPourPage(float2 xy) {
  float3 col = mix(float3(0.06, 0.05, 0.045), float3(0.12, 0.095, 0.075), exp(-sunriseCocktailPourDot2((xy - uC)/(uS*float2(2.4, 2.6)))));
  // the bar top: a polished line where the glass stands, its reflection
  float barY = uC.y + 1.24*uS;
  col = mix(col, float3(0.09, 0.07, 0.055), smoothstep(barY - 1.0, barY + 1.0, xy.y));
  col += float3(0.5, 0.35, 0.2)*0.12*exp(-sunriseCocktailPourSq((xy.y - barY)/2.0));
  // the drink's coloured light on the bar
  float2 q = (xy - float2(uC.x + uShift*uS, barY + 0.05*uS))/(uS*float2(0.9, 0.08));
  col += sunriseCocktailPourDrinkAt(-0.6)*0.18*exp(-dot(q, q)*1.5);
  return col;
}
float3 sunriseCocktailPourStudio(float3 r) {
  float3 c = mix(float3(0.03, 0.025, 0.02), float3(0.12, 0.1, 0.08), smoothstep(-0.2, 0.8, r.y));
  c += float3(1.0, 0.92, 0.8)*8.0*smoothstep(0.88, 0.9, r.x)*(1.0 - smoothstep(0.5, 0.6, abs(r.y)));
  c += float3(0.9, 0.95, 1.0)*5.0*smoothstep(0.88, 0.9, -r.x)*(1.0 - smoothstep(0.5, 0.6, abs(r.y - 0.1)));
  c += float3(1.0, 0.9, 0.75)*4.0*smoothstep(0.93, 0.96, r.y);
  return c;
}

float3 sunriseCocktailPourRender(float2 xy, thread float &cover) {
  float2 u = (xy - uC)/uS - float2(uShift, 0.0);
  float px = uPx/uS;
  // a slight look from above
  float3 ro = sunriseCocktailPourRx(float3(u.x, -u.y, 4.0), -0.12);
  float3 rd = sunriseCocktailPourRx(float3(0.0, 0.0, -1.0), -0.12);
  float t = 2.0;
  float dmin = 1e3;
  float tBest = t;
  for (int i = 0; i < 64; i++) {
    float d = sunriseCocktailPourSdGlass(ro + rd*t);
    if (d < dmin) { dmin = d; tBest = t; }
    if (d < 0.001) break;
    t += d*0.85;
    if (t > 7.0) break;
  }
  // the stream of grenadine falling into the glass from above
  float streamCov = 0.0;
  if (uStream > 0.0) {
    float sx = u.x - 0.08;
    float top = -2.6;
    float bottom = mix(top, -uLevel, clamp(uStream*3.0, 0.0, 1.0));
    float end = mix(top, -uLevel, clamp((uStream - 0.7)*3.3, 0.0, 1.0));
    if (u.y > end && u.y < bottom + 0.02) streamCov = 1.0 - smoothstep(0.012, 0.022, abs(sx + 0.006*sin(u.y*20.0 + uPhase*3.0)));
  }
  cover = max(1.0 - smoothstep(0.0, 1.4*px, dmin), streamCov);
  if (cover <= 0.0) return float3(0.0);
  if (dmin > 0.002 && streamCov > 0.0) return float3(0.75, 0.08, 0.15)*1.2;
  float3 p = ro + rd*tBest;
  const float e = 0.0015;
  float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sunriseCocktailPourSdGlass(p + k.xyy*e) + k.yyx*sunriseCocktailPourSdGlass(p + k.yyx*e) + k.yxy*sunriseCocktailPourSdGlass(p + k.yxy*e) + k.xxx*sunriseCocktailPourSdGlass(p + k.xxx*e));
  float cosi = clamp(-dot(rd, n), 0.0, 1.0);
  float F = 0.04 + 0.96*pow(1.0 - cosi, 5.0);
  float3 refl = sunriseCocktailPourStudio(reflect(rd, n));
  // through glass, drink and ice in fixed steps
  float3 di = refract(rd, n, 1.0/1.5);
  float3 trans = float3(1.0);
  float3 glow = float3(0.0);
  float3 q = p;
  float st = 0.04;
  for (int j = 0; j < 46; j++) {
    q += di*st;
    if (sunriseCocktailPourSdGlass(q) > 0.02 && length(q.xz) > R_OUT) break;
    float3 dens = float3(0.04);
    bool inCavity = length(q.xz) < R_IN && q.y > FLOOR;
    if (inCavity && q.y < uLevel) {
      float di2 = sunriseCocktailPourSdIces(q);
      if (di2 < 0.0) {
        // ice: clear, with bright frosted edges
        dens = float3(0.08, 0.06, 0.04);
        glow += float3(1.0)*trans*(0.03 + 0.12*smoothstep(-0.03, 0.0, di2));
      } else {
        float3 c = sunriseCocktailPourDrinkAt(q.y);
        dens = (1.0 - c)*4.0;
        glow += c*trans*0.03;
        // fine bubbles rising in the drink
        float b = fract(sin(dot(floor(q*float3(40.0, 40.0, 40.0) + float3(0.0, -uPhase*(3.0/3.14159265), 0.0)), float3(12.9898, 78.233, 37.719)))*43758.5453);
        glow += float3(1.0)*trans*0.05*step(0.9993, b);
      }
    } else if (inCavity && sunriseCocktailPourSdIces(q) < 0.0) {
      dens = float3(0.06);
      glow += float3(1.0)*trans*0.04;
    }
    // the meniscus line
    if (inCavity) glow += float3(1.0, 0.9, 0.75)*trans*0.06*exp(-sunriseCocktailPourSq((q.y - uLevel)/0.015));
    trans *= exp(-dens*st);
  }
  float3 back = sunriseCocktailPourPage(xy + float2(0.0, 0.0))*0.6;
  float3 body = back*trans + glow;
  return mix(body, refl, F);
}

float3 sunriseCocktailPourToP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 sunriseCocktailPourShade(float2 xy) {
  float3 col = sunriseCocktailPourPage(xy);
  float2 u = (xy - uC)/uS - float2(uShift, 0.0);
  if (abs(u.x) < 0.8 && u.y > -2.8 && u.y < 1.35) {
    float cv;
    float3 g = sunriseCocktailPourRender(xy, cv);
    col = mix(col, g, cv);
  }
  col = col/(1.0 + 0.12*max(max(col.r, col.g), col.b) - 0.12);
  float3 o = sunriseCocktailPourToP3(clamp(col, 0.0, 1.0));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}
};
struct SunriseCocktailPourVertex { float4 position [[position]]; };
vertex SunriseCocktailPourVertex sunriseCocktailPourVertex(uint id [[vertex_id]]) {
  float2 p = id == 0 ? float2(-1,-1) : (id == 1 ? float2(3,-1) : float2(-1,3));
  return {float4(p,0,1)};
}
fragment half4 sunriseCocktailPourFragment(SunriseCocktailPourVertex in [[stage_in]], constant float4 *v [[buffer(0)]]) {
  SunriseCocktailPourShader s;
  s.uRes=v[0].xy; s.uC=v[0].zw;
  s.uS=v[1].x; s.uShift=v[1].y; s.uLevel=v[1].z; s.uRed=v[1].w;
  s.uRise=v[2].x; s.uMix=v[2].y; s.uStream=v[2].z; s.uSwirl=v[2].w;
  s.uPhase=v[3].x; s.uPx=v[3].y;
  return s.sunriseCocktailPourShade(in.position.xy*s.uPx);
}
