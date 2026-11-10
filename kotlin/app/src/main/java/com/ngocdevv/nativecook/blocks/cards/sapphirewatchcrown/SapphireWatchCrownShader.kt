package com.ngocdevv.nativecook.blocks.cards.sapphirewatchcrown

internal object SapphireWatchCrownShader {
    val source = """
uniform float4 uView;
uniform float4 uWatch;
uniform float4 uHands;
uniform float4 uMetal;
struct Px { float3 col; float a; };
float choose(float a, float b, bool c) { return c ? b : a; }
const float PI = 3.14159265;
const float TAU = 6.2831853;
float sq(float v) { return v * v; }
float3 lin(float3 c) { return pow(c, float3(2.2)); }
float2x2 rot(float a) { float c = cos(a); float s = sin(a); return float2x2(c, s, -s, c); }
float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float sdBox2(float2 p, float2 b) { float2 q = abs(p) - b; return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0); }
float sdSeg(float2 p, float2 a, float2 b) { float2 pa = p - a; float2 ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }

/* ------------------------------------------------------------------ the watch's frame
 world: pt, origin at the case's centre, x right, y up, z to the eye. local: the watch's own, 12 o'clock
 at +y, the dial facing +z. It lies turned on its disc (12 o'clock to the upper left), tipped a little
 towards the lower right so its side shows, and the flip turns it about its 12–6 axis. */

const float LAY = 0.72;
const float TIP = 0.32;
float3 toLocal(float3 v) {
  float3 q = v;
  float2 xy = rot(-LAY) * q.xy; q = float3(xy, q.z);
  // tip about the watch's 12–6 axis a little, so the case's side shows at 3 o'clock
  float2 xz = rot(TIP + uWatch.w * PI) * q.xz; q = float3(xz.x, q.y, xz.y);
  float2 yz = rot(0.22) * q.yz; q = float3(q.x, yz.x, yz.y);
  return q;
}
float3 toWorld(float3 v) {
  float3 q = v;
  float2 yz = rot(-0.22) * q.yz; q = float3(q.x, yz.x, yz.y);
  float2 xz = rot(-(TIP + uWatch.w * PI)) * q.xz; q = float3(xz.x, q.y, xz.y);
  float2 xy = rot(LAY) * q.xy; q = float3(xy, q.z);
  return q;
}

/* ------------------------------------------------------------------ the shape (local, pt) */

const float RC = 84.0;
const float THICK = 6.0;
// 1 case, 2 crown, 3 strap, 4 crystal/dial face, 5 back
float2 sdWatch(float3 p) {
  // the case: a flat drum with a rolled edge
  float r = length(p.xy);
  float2 dc = float2(r - (RC - 5.0), abs(p.z) - (THICK - 5.0));
  float dd = min(max(dc.x, dc.y), 0.0) + length(max(dc, float2(0.0))) - 5.0;
  float m = 1.0;
  // the lugs: four horns at 11, 1, 5 and 7, joined smoothly
  for (int i = 0; i < 4; i++) {
    float sx = choose(-1.0, 1.0, i == 1 || i == 3);
    float sy = choose(-1.0, 1.0, i >= 2);
    float3 lp = p - float3(sx * 30.0, sy * 82.0, -1.0);
    float lug = length(max(abs(lp) - float3(4.0, 12.0, 3.0), float3(0.0))) - 2.5;
    dd = smin(dd, lug, 4.0);
  }
  // the crown at 3 o'clock, knurled
  float3 cp = p - float3(RC + 4.0, 0.0, 0.0);
  float ang = atan(cp.z, cp.y) + uHands.y;
  float knurl = 0.45 * sin(ang * 18.0);
  float crown = max(length(cp.yz) - 7.0 - knurl, abs(cp.x) - 5.0);
  if (crown < dd) { dd = crown; m = 2.0; }
  // the strap: two bands from the lugs, curving away behind
  float ay = abs(p.y);
  if (ay > 70.0) {
    float bend = sq(max(ay - 88.0, 0.0)) * 0.012;
    float3 sp = float3(p.x, ay - 150.0, p.z + 2.0 + bend);
    float strap = length(max(abs(sp) - float3(25.0, 62.0, 2.2), float3(0.0))) - 1.6;
    if (strap < dd) { dd = strap; m = 3.0; }
  }
  return float2(dd, m);
}
float3 watchN(float3 p) {
  float e = 0.12;
  float3 k1 = float3(1.0, -1.0, -1.0); float3 k2 = float3(-1.0, -1.0, 1.0); float3 k3 = float3(-1.0, 1.0, -1.0); float3 k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * sdWatch(p + k1 * e).x + k2 * sdWatch(p + k2 * e).x + k3 * sdWatch(p + k3 * e).x + k4 * sdWatch(p + k4 * e).x);
}

/* ------------------------------------------------------------------ light and metals */

float3 studio(float3 r) {
  // a product studio on black: a big soft box above left, a strip to the right, black elsewhere
  float3 c = lin(float3(0.03));
  c += float3(1.0, 0.98, 0.95) * 3.0 * smoothstep(0.75, 0.95, dot(r, normalize(float3(-0.55, 0.65, 0.52))));
  c += float3(1.0) * 1.2 * smoothstep(0.9, 0.98, dot(r, normalize(float3(0.85, -0.1, 0.5))));
  c += float3(0.6, 0.62, 0.66) * 0.35 * smoothstep(-0.2, 0.8, r.y) * smoothstep(-0.3, 0.6, r.z);
  return c;
}
float3 metalColor(float i) {
  if (i < 0.5) { return lin(float3(0.2, 0.2, 0.22)); }
  if (i < 1.5) { return lin(float3(0.93, 0.7, 0.58)); }
  if (i < 2.5) { return lin(float3(0.98, 0.79, 0.42)); }
  return lin(float3(0.9, 0.91, 0.93));
}
float3 caseMetal() { return mix(metalColor(uHands.z), metalColor(uHands.w), uMetal.x); }

/* ------------------------------------------------------------------ the dial (local xy, pt) */

float hand(float2 q, float ang, float len, float w) {
  // a lance: widest a third of the way out, a point at the tip, a short tail
  // Rotate the point onto +y so the hands wind clockwise from 10:08.
  float2 p = rot(ang) * q;
  float t = clamp(p.y / len, -0.18, 1.0);
  float halfWidth = w * choose(smoothstep(-0.18, 0.0, t) * 0.6, (1.0 - t) * 1.3 * smoothstep(0.0, 0.3, t) + 0.25 * (1.0 - t), t > 0.0);
  return max(abs(p.x) - halfWidth, max(p.y - len, -p.y - len * 0.18));
}
// Same masks as WGSL, with arithmetic bit tests for AGSL's ES2 runtime.
float seg7(float2 p, int n) {
  float m = n == 0 ? 63.0 : n == 1 ? 6.0 : n == 2 ? 91.0 : n == 3 ? 79.0 : n == 4 ? 102.0 : n == 5 ? 109.0 : n == 6 ? 125.0 : n == 7 ? 7.0 : n == 8 ? 127.0 : 111.0;
  float w = 1.3;
  float h = 2.3;
  float g = 0.35;
  float d = 1e9;
  if (mod(m, 2.0) > 0.5) { d = min(d, sdSeg(p, float2(-w + g, h), float2(w - g, h))); }
  if (mod(floor(m / 2.0), 2.0) > 0.5) { d = min(d, sdSeg(p, float2(w, h - g), float2(w, g))); }
  if (mod(floor(m / 4.0), 2.0) > 0.5) { d = min(d, sdSeg(p, float2(w, -g), float2(w, -h + g))); }
  if (mod(floor(m / 8.0), 2.0) > 0.5) { d = min(d, sdSeg(p, float2(-w + g, -h), float2(w - g, -h))); }
  if (mod(floor(m / 16.0), 2.0) > 0.5) { d = min(d, sdSeg(p, float2(-w, -g), float2(-w, -h + g))); }
  if (mod(floor(m / 32.0), 2.0) > 0.5) { d = min(d, sdSeg(p, float2(-w, h - g), float2(-w, g))); }
  if (mod(floor(m / 64.0), 2.0) > 0.5) { d = min(d, sdSeg(p, float2(-w + g, 0.0), float2(w - g, 0.0))); }
  return d - 0.42;
}
float3 dial(float2 q) {
  float r = length(q);
  float th = atan(q.x, q.y); // clockwise from 12
  // mother-of-pearl: white with a faint shifting shimmer
  float3 c = lin(float3(0.94, 0.93, 0.9)) * (0.9 + 0.1 * sin(q.x * 0.12 + sin(q.y * 0.09) * 2.0));
  c = mix(c, c * lin(float3(0.98, 0.95, 1.0)), 0.5 + 0.5 * sin(th * 3.0 + r * 0.05));
  // the date ring: 31 ticks, every other one longer, and a thin circle
  float n = 31.0;
  float a = th / TAU * n;
  float f = abs(fract(a + 0.5) - 0.5) / n * TAU * r;
  float tick = (1.0 - smoothstep(0.35, 0.8, f)) * step(60.0, r) * step(r, choose(66.0, 69.0, fract(floor(a + 0.5) * 0.5) < 0.25));
  c = mix(c, lin(float3(0.18, 0.18, 0.2)), tick * 0.85);
  c = mix(c, lin(float3(0.5, 0.42, 0.36)), exp(-sq((r - 58.0) / 0.5)) * 0.8);
  // applied indices: twelve small gold studs
  float ia = th / TAU * 12.0;
  float2 ip = rot(-floor(ia + 0.5) * TAU / 12.0) * q;
  float stud = length(ip - float2(0.0, 50.0)) - 2.3;
  c = mix(c, caseMetal() * 1.2, 1.0 - smoothstep(-0.5, 0.5, stud));
  // the date windows below 12 and the moon at 4–5
  float win = sdBox2(q - float2(0.0, 26.0), float2(13.0, 4.2));
  c = mix(c, lin(float3(0.98)), (1.0 - smoothstep(-0.5, 0.5, win)) * 0.9);
  // The date, 14, as two seven-segment figures.
  for (int k = 0; k < 2; k++) {
    float2 dq = q - float2(-2.6 + float(k) * 5.2, 26.0);
    float digit = seg7(dq, k == 0 ? 1 : 4);
    c = mix(c, lin(float3(0.15)), 1.0 - smoothstep(0.15, 0.55, digit));
  }
  c = mix(c, lin(float3(0.55, 0.5, 0.45)), exp(-sq(win / 0.5)) * 0.8);
  float2 mq = q - float2(22.0, -26.0);
  float moonWin = length(mq) - 13.0;
  if (moonWin < 0.0) {
    float3 sky = lin(float3(0.08, 0.12, 0.3));
    float st = hash2(floor(mq * 0.8));
    sky += float3(1.0) * step(0.93, st) * 0.6;
    float moon = length(mq - float2(-3.0, 1.0)) - 6.5;
    sky = mix(sky, lin(float3(0.95, 0.85, 0.6)), 1.0 - smoothstep(-0.5, 0.5, moon));
    c = mix(c, sky, 1.0 - smoothstep(-0.6, 0.0, moonWin));
  }
  c = mix(c, caseMetal(), exp(-sq(moonWin / 0.7)) * 0.9);
  // the maker's mark: a small crowned A above the windows
  float2 mk = q - float2(0.0, 38.0);
  float mark = min(min(sdSeg(mk, float2(-3.0, -3.0), float2(0.0, 3.5)), sdSeg(mk, float2(3.0, -3.0), float2(0.0, 3.5))), sdSeg(mk, float2(-1.6, -0.6), float2(1.6, -0.6)));
  c = mix(c, lin(float3(0.2, 0.2, 0.25)), 1.0 - smoothstep(0.4, 0.9, mark));
  // the hands, blued steel, with their shadows on the dial
  float hrs = uHands.x;
  float ha = hrs / 12.0 * TAU;
  float ma = fract(hrs) * TAU;
  float2 sh = float2(1.6, -1.6);
  float shadow = min(hand(q - sh, ha, 34.0, 3.2), hand(q - sh, ma, 50.0, 2.6));
  c *= 1.0 - 0.25 * (1.0 - smoothstep(-1.0, 2.0, shadow));
  float hd = min(hand(q, ha, 34.0, 3.2), hand(q, ma, 50.0, 2.6));
  float3 blue = lin(float3(0.12, 0.2, 0.5)) * (0.7 + 0.6 * smoothstep(-3.0, 0.0, hd));
  c = mix(c, blue, 1.0 - smoothstep(-0.5, 0.5, hd));
  // the pin
  c = mix(c, caseMetal() * 1.1, 1.0 - smoothstep(2.2, 3.0, r));
  return c;
}

/* ------------------------------------------------------------------ the movement (local xy, pt, seen from behind) */

float gear(float2 q, float2 c, float rr, float teeth, float ang) {
  float2 p = q - c;
  float th = atan(p.y, p.x) + ang;
  float toothed = length(p) - rr - 1.3 * smoothstep(-0.35, 0.35, sin(th * teeth));
  // spokes: a gear is cut away between five arms
  float inner = length(p) - rr * 0.72;
  float arms = abs(sin(th * 2.5)) * length(p) - 2.2;
  float cut = max(inner, arms);
  return max(toothed, -max(cut, -(length(p) - rr * 0.25)));
}
float3 movement(float2 q0) {
  float2 q = float2(-q0.x, q0.y); // seen from behind: mirrored
  float tt = uMetal.y;
  float r = length(q);
  // the main plate: grey with perlage (overlapping circular grains)
  float2 cell = floor(q / 5.0);
  float2 fq = fract(q / 5.0) - 0.5;
  float3 c = lin(float3(0.3, 0.31, 0.34)) * (0.8 + 0.2 * smoothstep(0.0, 0.5, length(fq)) + 0.05 * hash2(cell));
  // bridges with Geneva stripes, and their bevels lit
  float br1 = sdBox2(rot(0.5) * (q - float2(18.0, 18.0)), float2(34.0, 12.0)) - 4.0;
  float br2 = sdBox2(rot(-0.8) * (q - float2(-16.0, 24.0)), float2(20.0, 9.0)) - 4.0;
  float bridges = min(br1, br2);
  if (bridges < 0.0) {
    float stripe = 0.85 + 0.15 * sin(dot(q, float2(0.7, 0.7)) * 0.8);
    c = lin(float3(0.82, 0.83, 0.85)) * stripe;
  }
  c += float3(1.0) * 0.35 * exp(-sq(bridges / 0.9));
  // the gear train: five wheels, each a whole number of turns per ten seconds
  float g1 = gear(q, float2(-6.0, -4.0), 21.0, 48.0, tt * TAU * 0.1);
  float g2 = gear(q, float2(27.0, -16.0), 13.0, 30.0, -tt * TAU * 0.2);
  float g3 = gear(q, float2(38.0, 14.0), 10.0, 24.0, tt * TAU * 0.5);
  float g4 = gear(q, float2(10.0, 38.0), 9.0, 20.0, -tt * TAU * 0.3);
  float g5 = gear(q, float2(-34.0, 18.0), 12.0, 28.0, tt * TAU * 0.2);
  float gears = min(min(g1, g2), min(min(g3, g4), g5));
  c = mix(c, lin(float3(0.92, 0.74, 0.45)) * (0.75 + 0.25 * sin(r * 0.6)), 1.0 - smoothstep(-0.4, 0.4, gears));
  c += float3(1.0, 0.9, 0.7) * 0.3 * exp(-sq(gears / 0.5));
  // jewels at the arbors
  for (int i = 0; i < 5; i++) {
    float2 jc = (i == 0 ? float2(-6.0, -4.0) : i == 1 ? float2(27.0, -16.0) : i == 2 ? float2(38.0, 14.0) : i == 3 ? float2(10.0, 38.0) : float2(-34.0, 18.0));
    float j = length(q - jc);
    c = mix(c, lin(float3(0.75, 0.05, 0.12)) * (0.6 + 0.8 * exp(-sq(j - 0.6))), 1.0 - smoothstep(1.6, 2.2, j));
  }
  // the balance: a brass wheel with three spokes and timing screws round its rim, swinging four
  // times a second over a blued hairspring that breathes with it, a jewel at its staff
  float2 bc = q - float2(-30.0, -36.0);
  float swing = sin(tt * TAU * 4.0) * 3.2;
  float hsr = length(bc);
  float spiral = abs(fract((hsr - atan(bc.y, bc.x) / TAU * 1.4 + swing * 0.05) / 1.4) - 0.5);
  c = mix(c, lin(float3(0.22, 0.32, 0.62)), (1.0 - smoothstep(0.1, 0.2, spiral)) * step(hsr, 13.0) * step(2.5, hsr) * 0.9);
  float2 bp = rot(swing) * bc;
  float br = length(bp);
  float ba = atan(bp.y, bp.x);
  float ring = abs(br - 16.0) - 1.5;
  float spoke = abs(fract(ba / TAU * 3.0 + 0.25) - 0.5) * TAU / 3.0 * br;
  float arms = max(spoke - 0.9, br - 16.0);
  float sk = floor(ba / (TAU / 10.0) + 0.5) * (TAU / 10.0);
  float screws = length(bp - 16.6 * float2(cos(sk), sin(sk))) - 1.5;
  float bal = min(min(ring, arms), screws);
  float3 brass = lin(float3(0.93, 0.78, 0.48)) * (0.8 + 0.3 * smoothstep(-1.5, 1.5, bp.x - bp.y));
  c = mix(c, brass, 1.0 - smoothstep(-0.4, 0.4, bal));
  c += float3(1.0, 0.92, 0.75) * 0.25 * exp(-sq(bal / 0.5));
  c = mix(c, lin(float3(0.75, 0.05, 0.12)), 1.0 - smoothstep(1.4, 2.0, br));
  // the rotor: halfWidth a disc of gold, going round once in ten seconds, engraved at its rim
  float ra = atan(q.y, q.x) - tt * TAU * 0.1;
  bool rotorA = cos(ra) > 0.0;
  if (rotorA && r > 44.0 && r < 62.0) {
    float eng = 0.9 + 0.1 * sin(r * 2.0);
    c = mix(c, caseMetal() * eng * 1.1, 0.92);
  }
  c += float3(1.0) * 0.25 * exp(-sq((r - 44.0) / 0.6)) * choose(0.0, 1.0, rotorA);
  return c;
}



Px watchPx(float2 s) {
  Px o;
  o.col = float3(0.0);
  o.a = 0.0;
  float3 w = float3(s.x - uWatch.x, uWatch.y - s.y, 0.0) * (RC / uWatch.z);
  if (length(w.xy) > 230.0) { return o; }
  float3 ro = toLocal(float3(w.xy, 300.0));
  float3 rd = toLocal(float3(0.0, 0.0, -1.0));
  float t = 160.0;
  bool hit = false;
  float m = 0.0;
  for (int i = 0; i < 64; i++) {
    float2 h = sdWatch(ro + rd * t);
    if (h.x < 0.04) { hit = true; m = h.y; break; }
    t += h.x * 0.9;
    if (t > 460.0) { break; }
  }
  // a soft shadow of the watch on its disc: the case narrowing as it turns over, and the strap
  if (!hit) {
    float2 q = rot(-LAY) * (w.xy + float2(-10.0, 12.0));
    float wide = RC * max(abs(cos((TIP + uWatch.w * PI))), 0.18) + 4.0;
    float caseS = length(q / float2(wide / RC, 1.0)) - RC;
    float strapS = sdBox2(q, float2(25.0 * max(abs(cos((TIP + uWatch.w * PI))), 0.3), 200.0));
    float sd = min(caseS, strapS);
    o.col = float3(0.0);
    o.a = (1.0 - smoothstep(-8.0, 26.0, sd)) * 0.55;
    return o;
  }
  float3 p = ro + rd * t;
  float3 nl = watchN(p);
  float3 n = toWorld(nl);
  float3 refl = studio(reflect(float3(0.0, 0.0, -1.0), n));
  float3 c = float3(0.0);
  float r = length(p.xy);
  if (m < 1.5 && nl.z > 0.85 && r < RC - 9.0) {
    // the dial under a domed crystal: the crystal's reflection over it, brightest at its rim
    float fr = 0.04 + 0.5 * pow(r / (RC - 9.0), 4.0);
    c = dial(p.xy) * (0.85 + 0.15 * n.z) * (1.0 - fr) + refl * fr * 0.6;
    c += float3(1.0) * 0.35 * exp(-sq((r - (RC - 10.0)) / 0.8));
  } else if (m < 1.5 && nl.z < -0.85 && r < RC - 14.0) {
    // the sapphire back on the movement
    float fr = 0.04 + 0.4 * pow(r / (RC - 14.0), 5.0);
    c = movement(p.xy) * (1.0 - fr) + refl * fr * 0.7;
    c += float3(1.0) * 0.3 * exp(-sq((r - (RC - 15.0)) / 0.8));
  } else if (m > 2.5) {
    // leather: dark, a faint grain, a soft sheen
    float grain = hash2(floor(p.xy * 1.4)) * 0.06;
    c = lin(float3(0.07, 0.07, 0.075)) * (0.6 + 0.4 * max(n.z, 0.0) + grain) + refl * 0.06;
    // stitches along its edges
    float st = abs(abs(p.x) - 21.5);
    c += float3(0.15) * (1.0 - smoothstep(0.3, 0.7, st)) * step(0.5, fract(abs(p.y) * 0.25));
  } else {
    // polished metal: the case, its bezel, lugs and crown
    float3 base = caseMetal();
    c = base * refl * 0.95 + base * 0.06;
    if (nl.z < -0.85) { c *= 0.85; }
  }
  o.col = c;
  o.a = 1.0;
  return o;
}

float3 encode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }


half4 main(float2 s) {
  Px px = watchPx(s);
  return half4(px.a < 0.999 ? px.col * px.a : encode(px.col), px.a);
}
""".trimIndent()
}
