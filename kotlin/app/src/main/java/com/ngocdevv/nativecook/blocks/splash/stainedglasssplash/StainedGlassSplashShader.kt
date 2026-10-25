package com.ngocdevv.nativecook.blocks.splash.stainedglasssplash

// Stained Glass Splash — the chapel in one full-screen pass: an ashlar wall, a rose window, beams
// of coloured light with dust in them, and a flagstone floor where the window's colours land.
//
// The window is built in polar coordinates around its centre (r in units of its radius R):
//   r < 0.16       the hub, the app's mark: a gold rose with a ruby heart
//   0.16 – 0.5     eight pointed petals (ruby and amber) on cobalt
//   0.5 – 0.8      sixteen bays, each with a round foil (emerald and amber) on violet and cobalt
//   0.8 – 0.94     thirty-two small border panes
//   0.94 – 1.04    the stone frame; stone rings between the zones, lead cames everywhere else.
// Each pane has its own colour, a thickness that varies (noise), seeded bubbles, and a key that
// says when it lights: left to right across the window, the outer panes before the inner ones, the
// hub last. `sweep` = (from, to, beam): panes whose key lies in [from, to] are lit, and the sun's
// beam (a brighter band) sits at `beam`. `keyMix` blends the key from that order (1) to plain left
// to right (0), which is how a finger sweeping the sun lights them.
//
// The floor receives the window: a stretched, softened copy of the lit panes, shifted against the
// beam. `flood` (0 … 1) floods everything with warm light from the window into the home screen's
// cream background, where the window stays as a faint watermark.
//
// Portable: fixed loops, no arrays, no dynamic indexing. The output is opaque and clamped to
// sRGB.
//
// Ported from the React Native SkSL unchanged except for the Display P3 conversion, which Android
// does not need: AGSL is the same dialect (API 33+). Coordinates are dp: the canvas is scaled by
// the density before the shader is drawn.

internal const val STAINED_GLASS_SPLASH_AGSL = """
uniform float2 size;
uniform float3 win;
uniform float3 sweep;
uniform float keyMix;
uniform float flood;
uniform float phase;

const float TAU = 6.2831853;

float g2(float x) {
  return exp(-x * x);
}

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float3 clampColor(float3 c) {
  return clamp(c, 0.0, 1.0);
}

const float3 RUBY = float3(0.78, 0.07, 0.14);
const float3 COBALT = float3(0.07, 0.2, 0.72);
const float3 AMBER = float3(0.98, 0.62, 0.12);
const float3 EMERALD = float3(0.08, 0.56, 0.32);
const float3 VIOLET = float3(0.42, 0.14, 0.62);
const float3 GOLD = float3(1.0, 0.8, 0.3);

float3 pick4(float i) {
  float m = mod(i, 4.0);
  return m < 0.5 ? RUBY : m < 1.5 ? COBALT : m < 2.5 ? AMBER : EMERALD;
}

// The pane under point d (pt from the window's centre): rgb = its glass colour, a = the distance
// in pt to the nearest lead came or stone (negative on the stone frame). info carries the pane's
// centre (normalised x across the window) and its ring (0 hub … 1 border) for the lighting key,
// and a per-pane seed.
float4 pane(float2 d, float R, out float3 info) {
  float r = length(d) / R;
  float a = atan(d.y, d.x) / TAU + 0.5;
  float3 c = GOLD;
  float e = 1.0;
  float ring = 1.0;
  float cang = 0.0;
  float crad = 0.0;
  float seed = 0.0;
  if (r < 0.16) {
    // The hub: a five-petalled gold rose with a ruby heart.
    float pa = fract(a * 5.0) - 0.5;
    float petal = 0.16 * (0.62 + 0.38 * cos(pa * TAU));
    c = r < 0.055 ? RUBY : (r < petal ? GOLD : AMBER * 0.85);
    e = min(abs(r - 0.055), r > 0.06 ? abs(r - petal) * 0.8 : 1.0);
    ring = 0.0;
    seed = r < 0.055 ? 1.0 : r < petal ? 2.0 + floor(a * 5.0) : 9.0;
  } else if (r < 0.5) {
    float s = floor(a * 8.0);
    float la = fract(a * 8.0) - 0.5;
    float w = 0.44 * pow(max(0.0, sin(3.1416 * (r - 0.16) / 0.34)), 0.55);
    bool petal = abs(la) < w * 0.5;
    c = petal ? (mod(s, 2.0) < 0.5 ? RUBY : AMBER) : COBALT;
    e = min(abs(abs(la) - w * 0.5) * TAU * r / 8.0, petal ? 1.0 : (0.5 - abs(la)) * TAU * r / 8.0 + 0.004);
    ring = 0.35;
    cang = (s + 0.5) / 8.0;
    crad = 0.33;
    seed = 20.0 + s + (petal ? 0.0 : 8.0);
  } else if (r < 0.8) {
    float s = floor(a * 16.0);
    float la = fract(a * 16.0) - 0.5;
    float2 lp = float2(la * TAU * 0.65 / 16.0, r - 0.65);
    float fr = length(lp);
    bool foil = fr < 0.095;
    c = foil ? (mod(s, 2.0) < 0.5 ? EMERALD : AMBER) : (mod(s, 2.0) < 0.5 ? VIOLET : COBALT);
    e = min(abs(fr - 0.095), (0.5 - abs(la)) * TAU * r / 16.0);
    ring = 0.7;
    cang = (s + 0.5) / 16.0;
    crad = 0.65;
    seed = 40.0 + s + (foil ? 0.0 : 16.0);
  } else if (r < 0.94) {
    float s = floor(a * 32.0);
    float la = fract(a * 32.0) - 0.5;
    c = pick4(s);
    e = (0.5 - abs(la)) * TAU * r / 32.0;
    ring = 1.0;
    cang = (s + 0.5) / 32.0;
    crad = 0.87;
    seed = 80.0 + s;
  } else {
    info = float3(0.5, 1.0, 0.0);
    return float4(c, -1.0);
  }
  float dist = e * R;
  // Stone rings between the zones are thicker than the lead.
  dist = min(dist, min(abs(r - 0.5), abs(r - 0.8)) * R - 1.3);
  dist = min(dist, abs(r - 0.16) * R - 0.5);
  dist = min(dist, (0.94 - r) * R);
  float ang = (cang - 0.5) * TAU;
  info = float3(0.5 + 0.5 * crad * cos(ang), ring, seed);
  return float4(c, dist);
}

float keyOf(float3 info) {
  // Each pane lights a little before or after its neighbours.
  float jitter = (hash(float2(info.z, 3.7)) - 0.5) * 0.08;
  return mix(info.x, info.x * 0.72 + (1.0 - info.y) * 0.4 - 0.02 + jitter, keyMix);
}

// Light through the window at d: the glass colour times how lit its pane is; leadOut = the
// distance to the nearest came in pt (negative on the stone frame).
float3 transmit(float2 d, float R, bool detail, out float leadOut) {
  float3 info;
  float4 pc = pane(d, R, info);
  leadOut = pc.a;
  float key = keyOf(info);
  float lit = smoothstep(sweep.x - 0.02, sweep.x + 0.06, key) * (1.0 - smoothstep(sweep.y - 0.06, sweep.y + 0.02, key));
  float x = 0.5 + 0.5 * d.x / R;
  float beam = g2((x - sweep.z) / 0.16);
  // Unlit panes keep about a quarter of their glow from the dusk outside; the beam warms its band
  // a little even before a pane lights.
  float glow = 0.24 + 0.1 * beam * (1.0 - lit) + lit * (0.8 + 0.6 * beam);
  float3 c = pc.rgb;
  if (detail) {
    // Uneven thickness (mottled, streaky), darker toward the cames, a few seeded bubbles.
    float th = 0.62 + 0.55 * noise(d * 0.06 + info.z * 7.0) + 0.18 * noise(d * float2(0.25, 0.05) + info.z);
    th *= 0.55 + 0.45 * smoothstep(0.0, 9.0, pc.a);
    // Light hot-spots in the middle of a pane, as if the sun were just behind it.
    th += 0.25 * smoothstep(6.0, 22.0, pc.a) * lit;
    float2 cell = floor(d / 6.0);
    float bh = hash(cell + info.z * 1.7);
    float2 bc = (cell + 0.5 + (float2(hash(cell + 3.0), hash(cell + 5.0)) - 0.5) * 0.5) * 6.0;
    float bubble = step(0.88, bh) * (1.0 - smoothstep(0.5, 1.1, length(d - bc)));
    c = c * th + float3(0.9) * bubble * 0.3 * lit;
    // A hint of pale-yellow in the lightest glass, as real pot-metal glass has.
    c = mix(c, c + float3(0.12, 0.1, 0.02), 0.25 * lit);
  }
  return c * glow;
}

half4 main(float2 p) {
  float2 wc = win.xy;
  float R = win.z;
  float2 d = p - wc;
  float r = length(d) / R;
  float floorTop = wc.y + R * 1.92;

  // The wall: ashlar courses of warm grey stone, dim.
  float row = floor(p.y / 30.0);
  float2 bq = float2(p.x / 58.0 + mod(row, 2.0) * 0.5, p.y / 30.0);
  float2 bf = fract(bq);
  float mortar = smoothstep(0.0, 0.03, min(bf.x, 1.0 - bf.x) * 1.9) * smoothstep(0.0, 0.05, min(bf.y, 1.0 - bf.y));
  float stoneN = noise(p * 0.08) * 0.5 + noise(p * 0.3) * 0.25 + hash(floor(bq)) * 0.25;
  float3 col = float3(0.13, 0.115, 0.1) * (0.75 + 0.35 * stoneN) * (0.55 + 0.45 * mortar);
  // The wall is lit by the window around it and by the beams.
  float lightAll = clamp((sweep.y - sweep.x) * 0.9, 0.0, 1.0);
  col *= 0.7 + 0.9 * lightAll * exp(-length(d) / (R * 1.7));

  // The floor: flagstones in perspective, catching the window's colours.
  if (p.y > floorTop) {
    float z = (p.y - floorTop) / (size.y - floorTop);
    float persp = 0.3 + 0.7 * z;
    float fx = (p.x - size.x * 0.5) / persp;
    float fz = 1.0 / (0.25 + z) * 60.0;
    float2 fg = float2(fx / 90.0, fz / 22.0);
    float2 ff = fract(fg + float2(mod(floor(fg.y), 2.0) * 0.5, 0.0));
    float grout = smoothstep(0.0, 0.04, min(ff.x, 1.0 - ff.x)) * smoothstep(0.0, 0.06, min(ff.y, 1.0 - ff.y));
    float3 fl = float3(0.15, 0.13, 0.115) * (0.7 + 0.35 * noise(float2(fx, fz) * 0.05)) * (0.6 + 0.4 * grout);
    fl *= 0.45 + 0.55 * z;
    // The window's light on the floor: the panes, stretched and blurred, shifted from the sun.
    float2 floorPatch = float2((p.x - size.x * 0.5 + (sweep.z - 0.5) * 150.0) / (persp * 1.05), (z - 0.52) * R * 2.6);
    float lp;
    float3 tl = transmit(floorPatch, R * 0.95, false, lp);
    // Light through air is soft: the colours bleed into each other and the edge fades out.
    tl = mix(tl, float3(0.5, 0.36, 0.3) * lightAll * 0.9, 0.35) * (0.75 + 0.25 * smoothstep(0.0, 3.0, lp));
    float inPatch = 1.0 - smoothstep(0.5, 1.05, length(floorPatch) / (R * 0.95));
    fl += tl * inPatch * 0.85 * (0.6 + 0.4 * z);
    col = fl;
  }

  // Beams: soft shafts from the window down to the floor, with dust drifting in them.
  float beamY = clamp((p.y - wc.y) / (floorTop + (size.y - floorTop) * 0.5 - wc.y), 0.0, 1.0);
  if (p.y > wc.y && r > 0.9) {
    float axis = wc.x - (sweep.z - 0.5) * 150.0 * beamY;
    float halfW = R * (0.9 + 0.25 * beamY);
    float shaft = 1.0 - smoothstep(halfW * 0.6, halfW, abs(p.x - axis));
    float2 cell = floor(p / 13.0);
    float h = hash(cell);
    float2 mp = (cell + 0.2 + 0.6 * float2(hash(cell + 2.3), hash(cell + 8.1))) * 13.0 + float2(sin(phase * TAU * 2.0 + h * 9.0), cos(phase * TAU + h * 7.0)) * 3.0;
    float mote = step(0.86, h) * (1.0 - smoothstep(0.4, 1.4, length(p - mp)));
    col += float3(1.0, 0.82, 0.6) * shaft * lightAll * (0.1 * (1.0 - beamY * 0.5) + mote * 0.4);
  }

  // The window itself.
  if (r < 1.04) {
    float lead;
    float3 tc = transmit(d, R, true, lead);
    if (lead < 0.0) {
      // The stone frame: a moulded ring, lit on its upper edge.
      float m = (r - 0.94) / 0.1;
      float3 st = float3(0.2, 0.18, 0.16) * (0.7 + 0.3 * noise(p * 0.2)) * (0.75 + 0.25 * sin(m * 3.1416));
      st += float3(0.3, 0.26, 0.2) * smoothstep(0.2, 1.0, -d.y / (R * 1.04)) * g2((m - 0.35) / 0.2) * (0.3 + 0.7 * lightAll);
      st *= 1.0 - smoothstep(0.9, 1.0, m) * 0.6;
      col = mix(col, st, clamp((1.04 - r) * R, 0.0, 1.0));
    } else {
      // Lead cames: dark, rounded, with a faint highlight along their middle.
      float cover = 1.0 - smoothstep(0.7, 1.9, lead);
      float3 came = float3(0.03, 0.028, 0.026) + float3(0.16, 0.15, 0.14) * g2(lead / 0.6) * (0.4 + 0.6 * lightAll);
      // Glow bleeds over the edge of the came from bright glass.
      col = mix(tc + tc * 0.15, came + tc * 0.12, cover);
    }
  }

  // Flood: warm light pours out of the window into the home screen's cream.
  if (flood > 0.0) {
    float reach = flood * 1.45 * max(size.x, size.y);
    float fl = 1.0 - smoothstep(reach * 0.55, reach, length(d));
    float3 cream = mix(float3(0.98, 0.94, 0.86), float3(0.95, 0.88, 0.76), clamp(p.y / size.y, 0.0, 1.0));
    // The window as a faint watermark in the cream.
    float lw;
    float3 wm = transmit(d, R, false, lw);
    cream = mix(cream, cream * (0.86 + 0.14 * normalize(wm + 0.001)), 0.5 * (1.0 - smoothstep(0.95, 1.05, r)));
    cream *= 1.0 - 0.06 * (1.0 - smoothstep(0.7, 1.9, abs(lw))) * (1.0 - smoothstep(0.95, 1.05, r));
    float3 flash = float3(1.0, 0.93, 0.78);
    float3 target = mix(flash, cream, smoothstep(0.35, 1.0, flood));
    col = mix(col, target, fl * smoothstep(0.0, 0.35, flood));
  }

  // Vignette and grain.
  float2 vc = (p / size - 0.5) * float2(1.2, 1.0);
  col *= 1.0 - (0.6 - 0.45 * flood) * dot(vc, vc);
  col += (hash(floor(p * 2.0)) - 0.5) * 0.025;
  return half4(half3(clampColor(col)), 1.0);
}
"""
