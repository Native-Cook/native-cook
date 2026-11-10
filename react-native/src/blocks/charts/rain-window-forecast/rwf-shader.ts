// Rain Window Forecast — two Skia shaders.
//
// RWF_SKSL draws the page of the reference (a navy night with a warm glow behind the forecast) and
// the weather icons, the large one and the four in the hourly cards: a sun (or a moon) behind a cloud
// of frosted glass that lets the sun's warmth through where they overlap, with a bright rim; under it
// glossy blue raindrops that slide down and fade, a whole number of times per loop. Each icon is given
// as centre, scale and three weights: sun, moon, rain.
//
// RWF_WET draws rain on the phone's own glass, over everything: drops in columns that run down with a
// stick-slip pace and leave trails, beads of condensation between them, a white catch light and a dark
// rim each; a finger's wipe (16 points of x, y, age) clears them, and they gather again. It is
// transparent where there is no drop.
export const RWF_SKSL = `
uniform float2 uRes;
uniform float uIcons[24];   // 4 card icons × (cx, cy, scale, sun, moon, rain)
uniform float uHero[6];     // the large icon: cx, cy, scale, sun, moon, rain
uniform float uT;           // seconds into the loop
uniform float uLoop;
uniform float uGlow;        // the warm glow behind the forecast, 0 … 1 (it cools at night)
uniform float uMode;        // 0: the page, the tab bar and the large icon; 1: only the card icons, on clear
uniform float4 uTab;        // the tab bar: x, y, w, h

float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float sdSeg(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0)); }
float sdRound(float2 p, float2 c, float2 h, float r) { float2 q = abs(p - c) - h + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }

float3 page(float2 xy) {
  // in the reference's points
  float2 p = xy*float2(402.0/uRes.x, 874.0/uRes.y);
  float3 col = mix(float3(0.082, 0.086, 0.157), float3(0.047, 0.051, 0.122), smoothstep(400.0, 760.0, p.y));
  // a soft grey light on the left, a warm glow on the right behind the forecast
  float2 c = (p - float2(70.0, 330.0))/float2(150.0, 140.0);
  col = mix(col, float3(0.235, 0.215, 0.235), 0.62*exp(-dot(c, c)));
  float2 g = (p - float2(300.0, 300.0))/float2(150.0, 175.0);
  col = mix(col, float3(0.34, 0.235, 0.13), 0.85*exp(-dot(g, g))*uGlow);
  return col;
}

// the cloud's distance field in icon units (1 unit = 1 pt for the large icon at scale 1, centred on
// the middle of the cloud)
float cloudD(float2 p) {
  float d = length(p - float2(-51.0, 16.0)) - 36.0;
  d = smin(d, length(p - float2(9.0, 0.0)) - 55.0, 6.0);
  d = smin(d, length(p - float2(57.0, 27.0)) - 32.0, 6.0);
  d = smin(d, sdRound(p, float2(0.0, 35.0), float2(90.5, 19.0), 18.0), 6.0);
  return d;
}

// one icon: premultiplied colour and coverage
float4 icon(float2 xy, float cx, float cy, float s, float sun, float moon, float rain) {
  float2 p = (xy - float2(cx, cy))/s;
  if (s <= 0.0 || abs(p.x) > 130.0 || p.y < -120.0 || p.y > 110.0) return float4(0.0);
  float3 col = float3(0.0);
  float a = 0.0;
  float px = 1.0/s;
  // the sun behind the cloud
  float2 sc = float2(2.0, -46.0);
  float sd = length(p - sc) - 62.8;
  if (sun > 0.0) {
    float halo = exp(-max(sd, 0.0)/9.0)*0.12*sun;
    col += float3(1.0, 0.55, 0.25)*halo; a = max(a, halo*0.5);
    float sa = (1.0 - smoothstep(-px, px, sd))*sun;
    float2 q = (p - sc)/62.8;
    float3 s0 = mix(float3(0.97, 0.6, 0.37), float3(1.0, 0.85, 0.6), smoothstep(-0.4, 0.9, q.x*0.6 - q.y*0.8));
    s0 *= 1.0 - 0.1*smoothstep(0.6, 1.0, length(q));
    col = mix(col, s0, sa); a = max(a, sa);
  }
  // or a crescent moon
  if (moon > 0.0) {
    float2 mc = float2(40.0, -58.0);
    float md = max(length(p - mc) - 34.0, -(length(p - mc - float2(-16.0, -12.0)) - 31.0));
    float ma = (1.0 - smoothstep(-px, px, md))*moon;
    float3 m0 = mix(float3(0.97, 0.7, 0.38), float3(1.0, 0.86, 0.58), smoothstep(-30.0, 30.0, -p.y + p.x*0.3));
    col = mix(col, m0, ma); a = max(a, ma);
  }
  // the cloud, frosted glass
  float cd = cloudD(p);
  float ca = 1.0 - smoothstep(-px, px, cd);
  if (ca > 0.0) {
    float depth = clamp(-cd/26.0, 0.0, 1.0);
    float3 c = mix(float3(0.83, 0.86, 0.92), float3(0.968, 0.975, 0.988), smoothstep(-0.2, 1.0, depth*0.7 + (40.0 - p.y)/120.0));
    c *= 1.0 - 0.06*smoothstep(10.0, 54.0, p.y) - 0.04*smoothstep(-20.0, -90.0, p.x);
    // the sun's warmth through the glass where they overlap
    if (sun > 0.0) c = mix(c, c*float3(1.03, 0.97, 0.9), sun*(1.0 - smoothstep(-6.0, 10.0, sd))*0.5);
    // a bright rim where the glass turns away
    c += float3(1.0)*0.1*(1.0 - smoothstep(0.0, 4.0, -cd))*smoothstep(30.0, -60.0, p.y);
    col = mix(col, c, ca); a = max(a, ca);
  }
  // the raindrops: glossy blue capsules sliding down and fading, whole cycles per loop
  if (rain > 0.0) {
    for (int i = 0; i < 5; i++) {
      float2 a0; float2 a1; float r;
      if (i == 0) { a0 = float2(-19.0, 20.0); a1 = a0; r = 7.7; }
      else if (i == 1) { a0 = float2(15.5, 7.7); a1 = float2(7.8, 22.7); r = 7.7; }
      else if (i == 2) { a0 = float2(-26.2, 39.0); a1 = float2(-32.9, 61.0); r = 9.0; }
      else if (i == 3) { a0 = float2(3.1, 37.7); a1 = float2(-7.9, 72.7); r = 9.5; }
      else { a0 = float2(30.5, 36.0); a1 = float2(20.5, 61.0); r = 9.5; }
      // heavier rain falls faster; the loop always holds whole laps
      float laps = 2.0 + float(i == 3 ? 1 : 0) + floor(rain*1.99);
      float f = fract(uT/uLoop*laps + 0.15);
      float2 dir = normalize(float2(-0.3, 1.0));
      float2 off = dir*(-6.0 + 40.0*f);
      float alpha = smoothstep(0.0, 0.15, f)*(1.0 - smoothstep(0.6, 1.0, f))*min(1.0, rain*1.6);
      float dd = sdSeg(p, a0 + off, a1 + off) - r;
      float glow = exp(-max(dd, 0.0)/5.0)*0.25*alpha;
      col += float3(0.05, 0.4, 1.0)*glow*(1.0 - a);
      float da = (1.0 - smoothstep(-px, px, dd))*alpha;
      if (da > 0.0) {
        float2 n2 = (p - (a0 + off))/max(r, 1.0);
        float3 dc = mix(float3(0.0, 0.42, 0.95), float3(0.15, 0.6, 1.0), smoothstep(1.0, -1.0, n2.x + n2.y*0.3));
        dc += float3(0.55, 0.8, 1.0)*0.5*exp(-sq((dd + r*0.55)/(r*0.25)))*smoothstep(0.0, -1.0, n2.x);
        col = mix(col, dc, da); a = max(a, da);
      }
    }
  }
  return float4(col, a);
}

half4 main(float2 xy) {
  if (uMode > 0.5) {
    // the card icons, each with a faint light behind it, over the cards
    float3 col = float3(0.0);
    float a = 0.0;
    for (int i = 0; i < 4; i++) {
      float2 c0 = float2(uIcons[i*6], uIcons[i*6 + 1]);
      float g = 0.07*exp(-dot(xy - c0, xy - c0)/(28.0*28.0));
      col = col*(1.0 - g) + float3(1.0)*g; a = a + g*(1.0 - a);
      float4 c = icon(xy, c0.x, c0.y, uIcons[i*6 + 2], uIcons[i*6 + 3], uIcons[i*6 + 4], uIcons[i*6 + 5]);
      col = col*(1.0 - clamp(c.a, 0.0, 1.0)) + c.rgb; a = a + c.a*(1.0 - a);
    }
    return half4(half3(col), half(clamp(a, 0.0, 1.0)));
  }
  float3 col = page(xy);
  // the tab bar: a rounded slab, lit from where its first icon sits
  float2 tq = abs(xy - (uTab.xy + uTab.zw*0.5)) - uTab.zw*0.5 + uTab.w*0.5;
  float td = length(max(tq, 0.0)) + min(max(tq.x, tq.y), 0.0) - uTab.w*0.5;
  if (td < 1.0) {
    float2 hl = (xy - float2(uTab.x + uTab.w*0.72, uTab.y + uTab.w*0.45))/float2(uTab.w*1.4, uTab.w*0.9);
    float3 tc = float3(0.118, 0.125, 0.2) + float3(0.07, 0.072, 0.09)*exp(-dot(hl, hl)*1.5);
    col = mix(col, tc, 1.0 - smoothstep(-0.6, 0.6, td));
  }
  float4 h = icon(xy, uHero[0], uHero[1], uHero[2], uHero[3], uHero[4], uHero[5]);
  col = col*(1.0 - clamp(h.a, 0.0, 1.0)) + h.rgb;
  col += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(col), 1.0);
}
`;

export const RWF_WET = `
uniform float2 uRes;
uniform float uT;
uniform float uLoop;
uniform float uWet;        // how much rain is on the glass, 0 … 1
uniform float uWipe[48];   // 16 × (x, y, age); age < 0 unused or a break between strokes
uniform float uWipeR;

float sq(float v) { return v*v; }
float h1(float n) { return fract(sin(n*91.3458)*47453.5453); }

float wiped(float2 xy) {
  float c = 0.0;
  for (int i = 0; i < 15; i++) {
    float a0 = uWipe[i*3 + 2];
    float a1 = uWipe[i*3 + 5];
    if (a0 >= 0.0 && a1 >= 0.0 && min(a0, a1) < 6.0) {
      float2 p0 = float2(uWipe[i*3], uWipe[i*3 + 1]);
      float2 p1 = float2(uWipe[i*3 + 3], uWipe[i*3 + 4]);
      float2 pa = xy - p0;
      float2 ba = p1 - p0;
      float k = clamp(dot(pa, ba)/max(dot(ba, ba), 1e-3), 0.0, 1.0);
      float d = length(pa - ba*k);
      float age = mix(a0, a1, k);
      // the rain gathers again over about four seconds
      float back = smoothstep(1.0, 4.5, age);
      c = max(c, (1.0 - smoothstep(uWipeR*0.6, uWipeR, d))*(1.0 - back));
    }
  }
  return c;
}

// one column of running drops: normal in xy, mask in z, trail in w
float4 column(float2 xy, float cw, float seed, float size) {
  float ci = floor(xy.x/cw);
  float4 o = float4(0.0);
  for (int k = 0; k < 2; k++) {
    float id = ci*7.13 + float(k)*3.71 + seed;
    if (h1(id) < 1.0 - 0.75*uWet) continue;
    float laps = 1.0 + floor(h1(id + 1.0)*2.0);
    float span = uRes.y + 160.0;
    float u = fract(h1(id + 2.0) - uT/uLoop*laps);
    float y = (1.0 - u)*span - 80.0;
    y += 22.0*sin(y*0.045 + id);
    float x = (ci + 0.25 + 0.5*h1(id + 3.0))*cw + 2.0*sin(y*0.08 + id);
    float r = size*(0.75 + 0.5*h1(id + 4.0));
    float2 d = (xy - float2(x, y))/float2(r, r*1.25);
    float l = length(d);
    if (l < 1.0) o.xyz = float3(d, 1.0 - smoothstep(0.82, 1.0, l));
    float above = y - xy.y;
    if (above > 0.0 && above < 140.0) {
      float tx = x + 2.0*sin(xy.y*0.08 + id);
      float wdt = r*0.5*(1.0 - above/140.0);
      o.w = max(o.w, (1.0 - smoothstep(wdt*0.6, wdt + 0.6, abs(xy.x - tx)))*(1.0 - above/140.0));
    }
  }
  return o;
}

half4 main(float2 xy) {
  if (uWet <= 0.001) return half4(0.0);
  float clear = wiped(xy);
  float4 a = column(xy, 38.0, 0.0, 7.0);
  float4 b = column(xy + float2(19.0, 0.0), 38.0, 19.0, 4.5);
  float4 dr = a.z > 0.0 ? a : b;
  float m = dr.z*uWet*(1.0 - clear);
  float trail = max(a.w, b.w)*uWet*(1.0 - clear)*0.16;
  // beads of condensation between the runs
  float2 g = xy/12.0;
  float2 gi = floor(g);
  float br = (0.08 + 0.22*sq(h1(gi.x*13.1 + gi.y*7.7)))*step(1.0 - 0.6*uWet, h1(gi.x*3.3 + gi.y*11.9));
  // jittered inside its cell, so the beads never read as a grid
  float2 bp = fract(g) - (0.25 + 0.5*float2(h1(gi.x*5.1 + gi.y*2.3), h1(gi.x*1.7 + gi.y*8.9)));
  float bl = length(bp)/max(br, 0.001);
  float bead = (1.0 - smoothstep(0.6, 1.0, bl))*uWet*(1.0 - clear)*0.32;
  if (m <= 0.0 && bead <= 0.0 && trail <= 0.0) return half4(0.0);
  float3 c = float3(0.0);
  float alpha = 0.0;
  if (m > 0.0) {
    float2 n = dr.xy;
    float rim = smoothstep(0.55, 1.0, length(n));
    float catchL = exp(-sq(length(n - float2(-0.35, -0.42))/0.2));
    float3 dcol = mix(float3(0.85, 0.9, 1.0), float3(0.02, 0.03, 0.08), rim*0.8*smoothstep(-0.2, 0.8, n.y));
    dcol += float3(1.0)*0.9*catchL;
    c = dcol;
    alpha = m*clamp(0.28 + 0.6*rim + 0.6*catchL, 0.0, 1.0);
  }
  float bh = exp(-sq(length(bp/max(br, 0.001) - float2(-0.3, -0.35))/0.3));
  c = mix(c, float3(0.9, 0.93, 1.0), (1.0 - alpha)*min(1.0, bead*(0.35 + bh)));
  alpha = max(alpha, bead*(0.25 + 0.5*bh));
  // a trail is a wet streak: a faint sheen, not a line
  c = mix(c, float3(0.75, 0.8, 0.9), (1.0 - alpha)*(trail > alpha ? 1.0 : 0.0));
  alpha = max(alpha, trail*0.7);
  return half4(half3(c*alpha), half(clamp(alpha, 0.0, 1.0)));
}
`;
