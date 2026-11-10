#include <metal_stdlib>
using namespace metal;
// Value-for-value translation of gpu/swc-wgsl.ts. All colours are encoded sRGB.
constant float sapphireWatchCrownPI = 3.14159265;
constant float sapphireWatchCrownTAU = 6.2831853;
constant float sapphireWatchCrownLAY = 0.72;
constant float sapphireWatchCrownTIP = 0.32;
constant float sapphireWatchCrownRC = 84.0;
constant float sapphireWatchCrownTHICK = 6.0;
struct SapphireWatchCrownUniforms { float4 view, watch, hands, metal; };
struct SapphireWatchCrownPx { float3 col; float a; };
struct SapphireWatchCrownShader {
  SapphireWatchCrownUniforms F;


float sapphireWatchCrownSq(float v) { return v * v; }
float3 sapphireWatchCrownLin(float3 c) { return pow(c, float3(2.2)); }
float2x2 sapphireWatchCrownRot(float a) { auto c = cos(a); auto s = sin(a); return float2x2(c, s, -s, c); }
float sapphireWatchCrownHash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float sapphireWatchCrownSdBox2(float2 p, float2 b) { auto q = abs(p) - b; return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0); }
float sapphireWatchCrownSdSeg(float2 p, float2 a, float2 b) { auto pa = p - a; auto ba = b - a; auto h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
float sapphireWatchCrownSmin(float a, float b, float k) { auto h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }

/* ------------------------------------------------------------------ the watch's frame
 world: pt, origin at the case's centre, x right, y up, z to the eye. local: the watch's own, 12 o'clock
 at +y, the dial facing +z. It lies turned on its disc (12 o'clock to the upper left), tipped a little
 towards the lower right so its side shows, and the flip turns it about its 12–6 axis. */



float3 sapphireWatchCrownToLocal(float3 v) {
  auto q = v;
  auto xy = sapphireWatchCrownRot(-sapphireWatchCrownLAY) * q.xy; q = float3(xy, q.z);
  // tip about the watch's 12–6 axis a little, so the case's side shows at 3 o'clock
  auto xz = sapphireWatchCrownRot(sapphireWatchCrownTIP + F.watch.w * sapphireWatchCrownPI) * q.xz; q = float3(xz.x, q.y, xz.y);
  auto yz = sapphireWatchCrownRot(0.22) * q.yz; q = float3(q.x, yz.x, yz.y);
  return q;
}
float3 sapphireWatchCrownToWorld(float3 v) {
  auto q = v;
  auto yz = sapphireWatchCrownRot(-0.22) * q.yz; q = float3(q.x, yz.x, yz.y);
  auto xz = sapphireWatchCrownRot(-(sapphireWatchCrownTIP + F.watch.w * sapphireWatchCrownPI)) * q.xz; q = float3(xz.x, q.y, xz.y);
  auto xy = sapphireWatchCrownRot(sapphireWatchCrownLAY) * q.xy; q = float3(xy, q.z);
  return q;
}

/* ------------------------------------------------------------------ the shape (local, pt) */



// 1 case, 2 crown, 3 strap, 4 crystal/dial face, 5 back
float2 sapphireWatchCrownSdWatch(float3 p) {
  // the case: a flat drum with a rolled edge
  auto r = length(p.xy);
  auto dc = float2(r - (sapphireWatchCrownRC - 5.0), abs(p.z) - (sapphireWatchCrownTHICK - 5.0));
  auto dd = min(max(dc.x, dc.y), 0.0) + length(max(dc, float2(0.0))) - 5.0;
  auto m = 1.0;
  // the lugs: four horns at 11, 1, 5 and 7, joined smoothly
  for (int i = 0; i < 4; i++) {
    auto sx = select(-1.0, 1.0, i % 2 == 1);
    auto sy = select(-1.0, 1.0, i / 2 == 1);
    auto lp = p - float3(sx * 30.0, sy * 82.0, -1.0);
    auto lug = length(max(abs(lp) - float3(4.0, 12.0, 3.0), float3(0.0))) - 2.5;
    dd = sapphireWatchCrownSmin(dd, lug, 4.0);
  }
  // the crown at 3 o'clock, knurled
  auto cp = p - float3(sapphireWatchCrownRC + 4.0, 0.0, 0.0);
  auto ang = atan2(cp.z, cp.y) + F.hands.y;
  auto knurl = 0.45 * sin(ang * 18.0);
  auto crown = max(length(cp.yz) - 7.0 - knurl, abs(cp.x) - 5.0);
  if (crown < dd) { dd = crown; m = 2.0; }
  // the strap: two bands from the lugs, curving away behind
  auto ay = abs(p.y);
  if (ay > 70.0) {
    auto bend = sapphireWatchCrownSq(max(ay - 88.0, 0.0)) * 0.012;
    auto sp = float3(p.x, ay - 150.0, p.z + 2.0 + bend);
    auto strap = length(max(abs(sp) - float3(25.0, 62.0, 2.2), float3(0.0))) - 1.6;
    if (strap < dd) { dd = strap; m = 3.0; }
  }
  return float2(dd, m);
}
float3 sapphireWatchCrownWatchN(float3 p) {
  auto e = 0.12;
  auto k1 = float3(1.0, -1.0, -1.0); auto k2 = float3(-1.0, -1.0, 1.0); auto k3 = float3(-1.0, 1.0, -1.0); auto k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * sapphireWatchCrownSdWatch(p + k1 * e).x + k2 * sapphireWatchCrownSdWatch(p + k2 * e).x + k3 * sapphireWatchCrownSdWatch(p + k3 * e).x + k4 * sapphireWatchCrownSdWatch(p + k4 * e).x);
}

/* ------------------------------------------------------------------ light and metals */

float3 sapphireWatchCrownStudio(float3 r) {
  // a product studio on black: a big soft box above left, a strip to the right, black elsewhere
  auto c = sapphireWatchCrownLin(float3(0.03));
  c += float3(1.0, 0.98, 0.95) * 3.0 * smoothstep(0.75, 0.95, dot(r, normalize(float3(-0.55, 0.65, 0.52))));
  c += float3(1.0) * 1.2 * smoothstep(0.9, 0.98, dot(r, normalize(float3(0.85, -0.1, 0.5))));
  c += float3(0.6, 0.62, 0.66) * 0.35 * smoothstep(-0.2, 0.8, r.y) * smoothstep(-0.3, 0.6, r.z);
  return c;
}
float3 sapphireWatchCrownMetalColor(float i) {
  if (i < 0.5) { return sapphireWatchCrownLin(float3(0.2, 0.2, 0.22)); }
  if (i < 1.5) { return sapphireWatchCrownLin(float3(0.93, 0.7, 0.58)); }
  if (i < 2.5) { return sapphireWatchCrownLin(float3(0.98, 0.79, 0.42)); }
  return sapphireWatchCrownLin(float3(0.9, 0.91, 0.93));
}
float3 sapphireWatchCrownCaseMetal() { return mix(sapphireWatchCrownMetalColor(F.hands.z), sapphireWatchCrownMetalColor(F.hands.w), F.metal.x); }

/* ------------------------------------------------------------------ the dial (local xy, pt) */

float sapphireWatchCrownHand(float2 q, float ang, float len, float w) {
  // a lance: widest a third of the way out, a point at the tip, a short tail
  // Rotate the point onto +y so the hands wind clockwise from 10:08.
  auto p = sapphireWatchCrownRot(ang) * q;
  auto t = clamp(p.y / len, -0.18, 1.0);
  auto halfWidth = w * select(smoothstep(-0.18, 0.0, t) * 0.6, (1.0 - t) * 1.3 * smoothstep(0.0, 0.3, t) + 0.25 * (1.0 - t), t > 0.0);
  return max(abs(p.x) - halfWidth, max(p.y - len, -p.y - len * 0.18));
}
// Seven-segment figures; bits 0...6 select segments a...g, matching the WGSL masks.
float sapphireWatchCrownSeg7(float2 p, uint n) {
  const uint masks[10] = {0x3Fu, 0x06u, 0x5Bu, 0x4Fu, 0x66u, 0x6Du, 0x7Du, 0x07u, 0x7Fu, 0x6Fu};
  auto m = masks[n];
  auto w = 1.3;
  auto h = 2.3;
  auto g = 0.35;
  auto d = 1e9;
  if ((m & 1u) != 0u) { d = min(d, sapphireWatchCrownSdSeg(p, float2(-w + g, h), float2(w - g, h))); }
  if ((m & 2u) != 0u) { d = min(d, sapphireWatchCrownSdSeg(p, float2(w, h - g), float2(w, g))); }
  if ((m & 4u) != 0u) { d = min(d, sapphireWatchCrownSdSeg(p, float2(w, -g), float2(w, -h + g))); }
  if ((m & 8u) != 0u) { d = min(d, sapphireWatchCrownSdSeg(p, float2(-w + g, -h), float2(w - g, -h))); }
  if ((m & 16u) != 0u) { d = min(d, sapphireWatchCrownSdSeg(p, float2(-w, -g), float2(-w, -h + g))); }
  if ((m & 32u) != 0u) { d = min(d, sapphireWatchCrownSdSeg(p, float2(-w, h - g), float2(-w, g))); }
  if ((m & 64u) != 0u) { d = min(d, sapphireWatchCrownSdSeg(p, float2(-w + g, 0.0), float2(w - g, 0.0))); }
  return d - 0.42;
}
float3 sapphireWatchCrownDial(float2 q) {
  auto r = length(q);
  auto th = atan2(q.x, q.y); // clockwise from 12
  // mother-of-pearl: white with a faint shifting shimmer
  auto c = sapphireWatchCrownLin(float3(0.94, 0.93, 0.9)) * (0.9 + 0.1 * sin(q.x * 0.12 + sin(q.y * 0.09) * 2.0));
  c = mix(c, c * sapphireWatchCrownLin(float3(0.98, 0.95, 1.0)), 0.5 + 0.5 * sin(th * 3.0 + r * 0.05));
  // the date ring: 31 ticks, every other one longer, and a thin circle
  auto n = 31.0;
  auto a = th / sapphireWatchCrownTAU * n;
  auto f = abs(fract(a + 0.5) - 0.5) / n * sapphireWatchCrownTAU * r;
  auto tick = (1.0 - smoothstep(0.35, 0.8, f)) * step(60.0, r) * step(r, select(66.0, 69.0, fract(floor(a + 0.5) * 0.5) < 0.25));
  c = mix(c, sapphireWatchCrownLin(float3(0.18, 0.18, 0.2)), tick * 0.85);
  c = mix(c, sapphireWatchCrownLin(float3(0.5, 0.42, 0.36)), exp(-sapphireWatchCrownSq((r - 58.0) / 0.5)) * 0.8);
  // applied indices: twelve small gold studs
  auto ia = th / sapphireWatchCrownTAU * 12.0;
  auto ip = sapphireWatchCrownRot(-floor(ia + 0.5) * sapphireWatchCrownTAU / 12.0) * q;
  auto stud = length(ip - float2(0.0, 50.0)) - 2.3;
  c = mix(c, sapphireWatchCrownCaseMetal() * 1.2, 1.0 - smoothstep(-0.5, 0.5, stud));
  // the date windows below 12 and the moon at 4–5
  auto win = sapphireWatchCrownSdBox2(q - float2(0.0, 26.0), float2(13.0, 4.2));
  c = mix(c, sapphireWatchCrownLin(float3(0.98)), (1.0 - smoothstep(-0.5, 0.5, win)) * 0.9);
  // The date, 14, as two seven-segment figures.
  for (int k = 0; k < 2; k++) {
    auto dq = q - float2(-2.6 + float(k) * 5.2, 26.0);
    auto digit = sapphireWatchCrownSeg7(dq, k == 0 ? 1u : 4u);
    c = mix(c, sapphireWatchCrownLin(float3(0.15)), 1.0 - smoothstep(0.15, 0.55, digit));
  }
  c = mix(c, sapphireWatchCrownLin(float3(0.55, 0.5, 0.45)), exp(-sapphireWatchCrownSq(win / 0.5)) * 0.8);
  auto mq = q - float2(22.0, -26.0);
  auto moonWin = length(mq) - 13.0;
  if (moonWin < 0.0) {
    auto sky = sapphireWatchCrownLin(float3(0.08, 0.12, 0.3));
    auto st = sapphireWatchCrownHash2(floor(mq * 0.8));
    sky += float3(1.0) * step(0.93, st) * 0.6;
    auto moon = length(mq - float2(-3.0, 1.0)) - 6.5;
    sky = mix(sky, sapphireWatchCrownLin(float3(0.95, 0.85, 0.6)), 1.0 - smoothstep(-0.5, 0.5, moon));
    c = mix(c, sky, 1.0 - smoothstep(-0.6, 0.0, moonWin));
  }
  c = mix(c, sapphireWatchCrownCaseMetal(), exp(-sapphireWatchCrownSq(moonWin / 0.7)) * 0.9);
  // the maker's mark: a small crowned A above the windows
  auto mk = q - float2(0.0, 38.0);
  auto mark = min(min(sapphireWatchCrownSdSeg(mk, float2(-3.0, -3.0), float2(0.0, 3.5)), sapphireWatchCrownSdSeg(mk, float2(3.0, -3.0), float2(0.0, 3.5))), sapphireWatchCrownSdSeg(mk, float2(-1.6, -0.6), float2(1.6, -0.6)));
  c = mix(c, sapphireWatchCrownLin(float3(0.2, 0.2, 0.25)), 1.0 - smoothstep(0.4, 0.9, mark));
  // the hands, blued steel, with their shadows on the dial
  auto hrs = F.hands.x;
  auto ha = hrs / 12.0 * sapphireWatchCrownTAU;
  auto ma = fract(hrs) * sapphireWatchCrownTAU;
  auto sh = float2(1.6, -1.6);
  auto shadow = min(sapphireWatchCrownHand(q - sh, ha, 34.0, 3.2), sapphireWatchCrownHand(q - sh, ma, 50.0, 2.6));
  c *= 1.0 - 0.25 * (1.0 - smoothstep(-1.0, 2.0, shadow));
  auto hd = min(sapphireWatchCrownHand(q, ha, 34.0, 3.2), sapphireWatchCrownHand(q, ma, 50.0, 2.6));
  auto blue = sapphireWatchCrownLin(float3(0.12, 0.2, 0.5)) * (0.7 + 0.6 * smoothstep(-3.0, 0.0, hd));
  c = mix(c, blue, 1.0 - smoothstep(-0.5, 0.5, hd));
  // the pin
  c = mix(c, sapphireWatchCrownCaseMetal() * 1.1, 1.0 - smoothstep(2.2, 3.0, r));
  return c;
}

/* ------------------------------------------------------------------ the movement (local xy, pt, seen from behind) */

float sapphireWatchCrownGear(float2 q, float2 c, float rr, float teeth, float ang) {
  auto p = q - c;
  auto th = atan2(p.y, p.x) + ang;
  auto toothed = length(p) - rr - 1.3 * smoothstep(-0.35, 0.35, sin(th * teeth));
  // spokes: a gear is cut away between five arms
  auto inner = length(p) - rr * 0.72;
  auto arms = abs(sin(th * 2.5)) * length(p) - 2.2;
  auto cut = max(inner, arms);
  return max(toothed, -max(cut, -(length(p) - rr * 0.25)));
}
float3 sapphireWatchCrownMovement(float2 q0) {
  auto q = float2(-q0.x, q0.y); // seen from behind: mirrored
  auto tt = F.metal.y;
  auto r = length(q);
  // the main plate: grey with perlage (overlapping circular grains)
  auto cell = floor(q / 5.0);
  auto fq = fract(q / 5.0) - 0.5;
  auto c = sapphireWatchCrownLin(float3(0.3, 0.31, 0.34)) * (0.8 + 0.2 * smoothstep(0.0, 0.5, length(fq)) + 0.05 * sapphireWatchCrownHash2(cell));
  // bridges with Geneva stripes, and their bevels lit
  auto br1 = sapphireWatchCrownSdBox2(sapphireWatchCrownRot(0.5) * (q - float2(18.0, 18.0)), float2(34.0, 12.0)) - 4.0;
  auto br2 = sapphireWatchCrownSdBox2(sapphireWatchCrownRot(-0.8) * (q - float2(-16.0, 24.0)), float2(20.0, 9.0)) - 4.0;
  auto bridges = min(br1, br2);
  if (bridges < 0.0) {
    auto stripe = 0.85 + 0.15 * sin(dot(q, float2(0.7, 0.7)) * 0.8);
    c = sapphireWatchCrownLin(float3(0.82, 0.83, 0.85)) * stripe;
  }
  c += float3(1.0) * 0.35 * exp(-sapphireWatchCrownSq(bridges / 0.9));
  // the gear train: five wheels, each a whole number of turns per ten seconds
  auto g1 = sapphireWatchCrownGear(q, float2(-6.0, -4.0), 21.0, 48.0, tt * sapphireWatchCrownTAU * 0.1);
  auto g2 = sapphireWatchCrownGear(q, float2(27.0, -16.0), 13.0, 30.0, -tt * sapphireWatchCrownTAU * 0.2);
  auto g3 = sapphireWatchCrownGear(q, float2(38.0, 14.0), 10.0, 24.0, tt * sapphireWatchCrownTAU * 0.5);
  auto g4 = sapphireWatchCrownGear(q, float2(10.0, 38.0), 9.0, 20.0, -tt * sapphireWatchCrownTAU * 0.3);
  auto g5 = sapphireWatchCrownGear(q, float2(-34.0, 18.0), 12.0, 28.0, tt * sapphireWatchCrownTAU * 0.2);
  auto gears = min(min(g1, g2), min(min(g3, g4), g5));
  c = mix(c, sapphireWatchCrownLin(float3(0.92, 0.74, 0.45)) * (0.75 + 0.25 * sin(r * 0.6)), 1.0 - smoothstep(-0.4, 0.4, gears));
  c += float3(1.0, 0.9, 0.7) * 0.3 * exp(-sapphireWatchCrownSq(gears / 0.5));
  // jewels at the arbors
  for (int i = 0; i < 5; i++) {
    auto jc = array<float2, 5>{float2(-6.0, -4.0), float2(27.0, -16.0), float2(38.0, 14.0), float2(10.0, 38.0), float2(-34.0, 18.0)}[i];
    auto j = length(q - jc);
    c = mix(c, sapphireWatchCrownLin(float3(0.75, 0.05, 0.12)) * (0.6 + 0.8 * exp(-sapphireWatchCrownSq(j - 0.6))), 1.0 - smoothstep(1.6, 2.2, j));
  }
  // the balance: a brass wheel with three spokes and timing screws round its rim, swinging four
  // times a second over a blued hairspring that breathes with it, a jewel at its staff
  auto bc = q - float2(-30.0, -36.0);
  auto swing = sin(tt * sapphireWatchCrownTAU * 4.0) * 3.2;
  auto hsr = length(bc);
  auto spiral = abs(fract((hsr - atan2(bc.y, bc.x) / sapphireWatchCrownTAU * 1.4 + swing * 0.05) / 1.4) - 0.5);
  c = mix(c, sapphireWatchCrownLin(float3(0.22, 0.32, 0.62)), (1.0 - smoothstep(0.1, 0.2, spiral)) * step(hsr, 13.0) * step(2.5, hsr) * 0.9);
  auto bp = sapphireWatchCrownRot(swing) * bc;
  auto br = length(bp);
  auto ba = atan2(bp.y, bp.x);
  auto ring = abs(br - 16.0) - 1.5;
  auto spoke = abs(fract(ba / sapphireWatchCrownTAU * 3.0 + 0.25) - 0.5) * sapphireWatchCrownTAU / 3.0 * br;
  auto arms = max(spoke - 0.9, br - 16.0);
  auto sk = floor(ba / (sapphireWatchCrownTAU / 10.0) + 0.5) * (sapphireWatchCrownTAU / 10.0);
  auto screws = length(bp - 16.6 * float2(cos(sk), sin(sk))) - 1.5;
  auto bal = min(min(ring, arms), screws);
  auto brass = sapphireWatchCrownLin(float3(0.93, 0.78, 0.48)) * (0.8 + 0.3 * smoothstep(-1.5, 1.5, bp.x - bp.y));
  c = mix(c, brass, 1.0 - smoothstep(-0.4, 0.4, bal));
  c += float3(1.0, 0.92, 0.75) * 0.25 * exp(-sapphireWatchCrownSq(bal / 0.5));
  c = mix(c, sapphireWatchCrownLin(float3(0.75, 0.05, 0.12)), 1.0 - smoothstep(1.4, 2.0, br));
  // the rotor: halfWidth a disc of gold, going round once in ten seconds, engraved at its rim
  auto ra = atan2(q.y, q.x) - tt * sapphireWatchCrownTAU * 0.1;
  auto rotorA = cos(ra) > 0.0;
  if (rotorA && r > 44.0 && r < 62.0) {
    auto eng = 0.9 + 0.1 * sin(r * 2.0);
    c = mix(c, sapphireWatchCrownCaseMetal() * eng * 1.1, 0.92);
  }
  c += float3(1.0) * 0.25 * exp(-sapphireWatchCrownSq((r - 44.0) / 0.6)) * select(0.0, 1.0, rotorA);
  return c;
}



SapphireWatchCrownPx sapphireWatchCrownWatchPx(float2 s) {
  SapphireWatchCrownPx o;
  o.col = float3(0.0);
  o.a = 0.0;
  auto w = float3(s.x - F.watch.x, F.watch.y - s.y, 0.0) * (sapphireWatchCrownRC / F.watch.z);
  if (length(w.xy) > 230.0) { return o; }
  auto ro = sapphireWatchCrownToLocal(float3(w.xy, 300.0));
  auto rd = sapphireWatchCrownToLocal(float3(0.0, 0.0, -1.0));
  auto t = 160.0;
  auto hit = false;
  auto m = 0.0;
  for (int i = 0; i < 90; i++) {
    auto h = sapphireWatchCrownSdWatch(ro + rd * t);
    if (h.x < 0.04) { hit = true; m = h.y; break; }
    t += h.x * 0.9;
    if (t > 460.0) { break; }
  }
  // a soft shadow of the watch on its disc: the case narrowing as it turns over, and the strap
  if (!hit) {
    auto q = sapphireWatchCrownRot(-sapphireWatchCrownLAY) * (w.xy + float2(-10.0, 12.0));
    auto wide = sapphireWatchCrownRC * max(abs(cos((sapphireWatchCrownTIP + F.watch.w * sapphireWatchCrownPI))), 0.18) + 4.0;
    auto caseS = length(q / float2(wide / sapphireWatchCrownRC, 1.0)) - sapphireWatchCrownRC;
    auto strapS = sapphireWatchCrownSdBox2(q, float2(25.0 * max(abs(cos((sapphireWatchCrownTIP + F.watch.w * sapphireWatchCrownPI))), 0.3), 200.0));
    auto sd = min(caseS, strapS);
    o.col = float3(0.0);
    o.a = (1.0 - smoothstep(-8.0, 26.0, sd)) * 0.55;
    return o;
  }
  auto p = ro + rd * t;
  auto nl = sapphireWatchCrownWatchN(p);
  auto n = sapphireWatchCrownToWorld(nl);
  auto refl = sapphireWatchCrownStudio(reflect(float3(0.0, 0.0, -1.0), n));
  auto c = float3(0.0);
  auto r = length(p.xy);
  if (m < 1.5 && nl.z > 0.85 && r < sapphireWatchCrownRC - 9.0) {
    // the dial under a domed crystal: the crystal's reflection over it, brightest at its rim
    auto fr = 0.04 + 0.5 * pow(r / (sapphireWatchCrownRC - 9.0), 4.0);
    c = sapphireWatchCrownDial(p.xy) * (0.85 + 0.15 * n.z) * (1.0 - fr) + refl * fr * 0.6;
    c += float3(1.0) * 0.35 * exp(-sapphireWatchCrownSq((r - (sapphireWatchCrownRC - 10.0)) / 0.8));
  } else if (m < 1.5 && nl.z < -0.85 && r < sapphireWatchCrownRC - 14.0) {
    // the sapphire back on the movement
    auto fr = 0.04 + 0.4 * pow(r / (sapphireWatchCrownRC - 14.0), 5.0);
    c = sapphireWatchCrownMovement(p.xy) * (1.0 - fr) + refl * fr * 0.7;
    c += float3(1.0) * 0.3 * exp(-sapphireWatchCrownSq((r - (sapphireWatchCrownRC - 15.0)) / 0.8));
  } else if (m > 2.5) {
    // leather: dark, a faint grain, a soft sheen
    auto grain = sapphireWatchCrownHash2(floor(p.xy * 1.4)) * 0.06;
    c = sapphireWatchCrownLin(float3(0.07, 0.07, 0.075)) * (0.6 + 0.4 * max(n.z, 0.0) + grain) + refl * 0.06;
    // stitches along its edges
    auto st = abs(abs(p.x) - 21.5);
    c += float3(0.15) * (1.0 - smoothstep(0.3, 0.7, st)) * step(0.5, fract(abs(p.y) * 0.25));
  } else {
    // polished metal: the case, its bezel, lugs and crown
    auto base = sapphireWatchCrownCaseMetal();
    c = base * refl * 0.95 + base * 0.06;
    if (nl.z < -0.85) { c *= 0.85; }
  }
  o.col = c;
  o.a = 1.0;
  return o;
}

float3 sapphireWatchCrownEncode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }


};
struct SapphireWatchCrownVertex { float4 position [[position]]; float2 uv; };
vertex SapphireWatchCrownVertex sapphireWatchCrownVertex(uint vi [[vertex_id]]) {
  float2 uv = float2(float((vi << 1u) & 2u), float(vi & 2u));
  return {float4(uv * float2(2.0, -2.0) + float2(-1.0, 1.0), 0.0, 1.0), uv};
}
fragment float4 sapphireWatchCrownFragment(SapphireWatchCrownVertex v [[stage_in]], constant SapphireWatchCrownUniforms& F [[buffer(0)]]) {
  SapphireWatchCrownShader s { F };
  auto px = s.sapphireWatchCrownWatchPx(v.position.xy / F.view.z);
  if (px.a <= 0.0001) return float4(0.0);
  if (px.a < 0.999) return float4(px.col * px.a, px.a);
  return float4(s.sapphireWatchCrownEncode(px.col), 1.0);
}
