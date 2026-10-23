package com.ngocdevv.nativecook.blocks.playground.ferrofluidblob

// The whole scene in one full-screen pass: a dark slate table, a porcelain dish, and the
// ferrofluid in it, all one height field seen from above at an angle. The React Native SkSL,
// unchanged: AGSL is the same dialect (API 33+). See FerrofluidBlobShader.ts for the description.
//
// The fluid's height at a ground point is a flat level, a smooth heap under each magnet, and a
// hexagonal lattice of sharp cones centred on each magnet whose heights follow the magnet's field.
// Each pixel marches its view ray down through the height field in fixed steps, refines the hit by
// bisection, takes the normal from central differences, and is lit by a studio reflected with
// Fresnel. Coordinates are dp: the canvas is scaled by the density before the shader is drawn.

internal const val FERRO_AGSL = """
uniform float2 center;
uniform float2 size;
uniform float dishR;
uniform float2 cam;
uniform float4 magA;
uniform float4 magB;
uniform float4 lean;

const float SPACING = 23.0;
const float SPIKE_H = 62.0;
const float SPIKE_SHARP = 2.1;
const float TIP_ROUND = 0.16;
const float CROWN_R = 46.0;
const float SPIKE_ON = 0.2;
const float MOUND_H = 17.0;
const float MOUND_R = 52.0;
const float DIMPLE_R = 32.0;
const float LEVEL = 6.0;
const float RIM_H = 16.0;
const float REACH = 120.0;
const float H_MAX = 170.0;
const float H_LOW = 18.0;
const float CEIL = 620.0;

// Offset of p from the nearest point of a hexagonal lattice through the origin.
float2 hexOffset(float2 p) {
  float2 r = float2(SPACING, SPACING * 1.7320508);
  float2 h = r * 0.5;
  float2 a = mod(p, r) - h;
  float2 b = mod(p - h, r) - h;
  return dot(a, a) < dot(b, b) ? a : b;
}

// One magnet's crown at ground point g: x = the smooth heap, y = the spikes.
float2 crown(float2 g, float4 m, float2 ln) {
  float2 d = g - m.xy;
  float r2 = dot(d, d);
  float heap = MOUND_H * m.z * exp(-r2 / (2.0 * MOUND_R * MOUND_R));
  // While melting, the spikes overshoot below the surface: a brief dimple.
  heap += min(m.w, 0.0) * 14.0 * exp(-r2 / (2.0 * DIMPLE_R * DIMPLE_R));
  float spikes = 0.0;
  float amp = max(m.w, 0.0);
  if (amp > 0.002 && r2 < REACH * REACH) {
    float2 q = hexOffset(d);
    float2 c = d - q;
    float dc = length(c);
    float field = amp * exp(-dc * dc / (2.0 * CROWN_R * CROWN_R));
    float tall = SPIKE_H * max(0.0, field - SPIKE_ON) / (1.0 - SPIKE_ON);
    // The tips lean in toward the magnet and back against its motion.
    float2 inward = dc > 0.5 ? -c / dc * min(dc, 70.0) * 0.08 : float2(0.0);
    float foot = SPACING * 0.5;
    // Kept under 0.6 of the foot so the sheared cone never folds over itself.
    float2 tip = (inward + ln) * min(amp, 1.0);
    float tipLen = length(tip);
    if (tipLen > foot * 0.6) tip *= foot * 0.6 / tipLen;
    float up = clamp(1.0 - length(q) / foot, 0.0, 1.0);
    // Distance from the axis, rounded off over TIP_ROUND of the foot so the very tip is a
    // small dome the march cannot step past.
    float r = length(q - tip * up) / foot;
    r = sqrt(r * r + TIP_ROUND * TIP_ROUND) - TIP_ROUND;
    spikes = tall * pow(max(0.0, 1.0 - r), SPIKE_SHARP);
  }
  return float2(heap, spikes);
}

// The dish in cross-section: a flat floor, a wall rising to a rounded lip, an outer foot.
float dishProfile(float r) {
  float inner = smoothstep(dishR - 44.0, dishR - 6.0, r);
  float outer = 1.0 - smoothstep(dishR - 2.0, dishR + 12.0, r);
  return RIM_H * min(inner, outer);
}

// x = the height of the surface at ground point g, y = how far the fluid stands above the dish
// there (positive where the fluid is on top).
float2 surface(float2 g) {
  float r = length(g);
  float dish = dishProfile(r);
  float fluid = -1.0;
  if (r < dishR - 10.0) {
    float inner = 1.0 - smoothstep(dishR - 110.0, dishR - 40.0, r);
    float2 a = crown(g, magA, lean.xy);
    float2 b = crown(g, magB, lean.zw);
    fluid = LEVEL + (a.x + b.x + max(a.y, b.y)) * inner;
  }
  return float2(max(fluid, dish), fluid - dish);
}

// The ground point a screen pixel sees at height h.
float2 ground(float2 p, float h) {
  return float2(p.x - center.x, (p.y - center.y + h * cam.y) / cam.x);
}

// Whether the ray's footprint on the ground (from y0 to y1) passes near a magnet's crown.
bool nearCrown(float4 m, float x, float y0, float y1) {
  if (m.z < 0.01 && abs(m.w) < 0.002) return false;
  float dx = x - m.x;
  float dy = clamp(m.y, y0, y1) - m.y;
  float reach = REACH + SPACING;
  return dx * dx + dy * dy < reach * reach;
}

float panel(float2 p, float2 c, float2 ext, float soft) {
  float2 b = abs(p - c) - ext;
  return 1.0 - smoothstep(-soft, soft * 0.3, max(b.x, b.y));
}

// The studio seen from ground point o (height oh) in mirror direction r (z up): a dark room,
// lights hung on a ceiling CEIL pt up, and a dim band of wall all round.
float3 studio(float2 o, float oh, float3 r) {
  float up = r.z;
  float3 col = float3(0.008, 0.008, 0.009);
  // The room's walls, faint, brightest at the horizon, with three tall lights standing on the
  // floor: one behind the viewer to the left (it puts a streak down the front of every spike),
  // a cool one on the far left, a warm one behind on the right.
  col += float3(0.08, 0.077, 0.074) * exp(-abs(up - 0.06) * 8.0);
  float az = atan(r.y, r.x);
  float tall = exp(-(up - 0.12) * (up - 0.12) / 0.06);
  col += float3(1.0, 0.97, 0.92) * exp(-(az - 1.95) * (az - 1.95) / 0.03) * tall * 1.8;
  col += float3(0.6, 0.75, 1.0) * exp(-(abs(az) - 3.0) * (abs(az) - 3.0) / 0.05) * tall * 1.1;
  col += float3(1.0, 0.7, 0.45) * exp(-(az + 0.5) * (az + 0.5) / 0.04) * tall * 0.9;
  if (up > 0.03) {
    float2 c = o + r.xy * ((CEIL - oh) / up);
    // A large softbox up and behind the dish, brighter in its middle.
    float2 bc = (c - float2(-40.0, -1000.0)) / float2(280.0, 80.0);
    col += float3(1.0, 0.96, 0.9) * 2.6 * exp(-dot(bc, bc) * dot(bc, bc));
    // A tall cool strip light on the left.
    col += float3(0.6, 0.76, 1.0) * panel(c, float2(-560.0, -200.0), float2(40.0, 520.0), 30.0) * 2.6;
    // A warm kicker low on the right and toward the viewer.
    col += float3(1.0, 0.68, 0.4) * panel(c, float2(620.0, 260.0), float2(180.0, 60.0), 50.0) * 1.6;
  }
  return col;
}

float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 p) {
  float gx = p.x - center.x;
  float y0 = (p.y - center.y) / cam.x;
  float slope = cam.y / cam.x;

  // How high the surface can be along this ray: the dish and the flat fluid, or a crown.
  float top = H_LOW;
  if (nearCrown(magA, gx, y0, y0 + H_MAX * slope) || nearCrown(magB, gx, y0, y0 + H_MAX * slope)) top = H_MAX;
  float dyD = clamp(0.0, y0, y0 + top * slope);
  bool overDish = gx * gx + dyD * dyD < (dishR + 14.0) * (dishR + 14.0);

  // March down from the top: 64 steps through a crown, 16 over flat fluid.
  float hit = 0.0;
  if (overDish) {
    float dh = top / (top > H_LOW ? 64.0 : 16.0);
    for (int i = 1; i <= 64; i++) {
      float h = top - dh * float(i);
      if (surface(ground(p, h)).x >= h) {
        float lo = h;
        float hi = h + dh;
        for (int j = 0; j < 6; j++) {
          float mid = (lo + hi) * 0.5;
          if (surface(ground(p, mid)).x >= mid) lo = mid; else hi = mid;
        }
        hit = lo;
        break;
      }
    }
  }

  float2 g = ground(p, hit);
  float r = length(g);
  float2 s = surface(g);
  float e = 0.5;
  float hx = surface(g + float2(e, 0.0)).x - surface(g - float2(e, 0.0)).x;
  float hy = surface(g + float2(0.0, e)).x - surface(g - float2(0.0, e)).x;
  float3 n = normalize(float3(-hx / (2.0 * e), -hy / (2.0 * e), 1.0));
  float3 d = float3(0.0, -cam.y, -cam.x);
  float3 rf = reflect(d, n);
  float ndv = clamp(dot(n, -d), 0.0, 1.0);
  float3 key = normalize(float3(-0.35, -0.75, 0.6));
  float diff = max(dot(n, key), 0.0);
  // A pool of light on the table around the dish.
  float pool = exp(-dot(g, g) / (2.0 * dishR * dishR * 1.4));

  float fres = 0.04 + 0.96 * pow(1.0 - ndv, 5.0);
  float3 col;
  if (r < dishR + 14.0) {
    // Porcelain: warm white, soft diffuse, a thin glaze; shaded down by the fluid's edge.
    float3 albedo = float3(0.8, 0.77, 0.72);
    float ao = mix(0.3, 1.0, smoothstep(dishR - 42.0, dishR - 20.0, r));
    col = albedo * (0.05 + 0.85 * diff * (0.35 + 0.65 * pool)) * ao + studio(g, s.x, rf) * fres * 0.8;
    col += float3(1.0, 0.97, 0.92) * pow(max(dot(rf, key), 0.0), 70.0) * 0.5 * ao;
  } else {
    // Slate: dark, lit by the pool, with the dish's soft shadow falling toward the viewer.
    float3 albedo = float3(0.032, 0.032, 0.036);
    float far = smoothstep(-dishR * 3.4, -dishR * 0.9, g.y);
    float shadowD = length(g - float2(18.0, 34.0));
    float shadow = 1.0 - 0.8 * (1.0 - smoothstep(dishR - 10.0, dishR + 60.0, shadowD));
    float contact = 1.0 - 0.7 * (1.0 - smoothstep(dishR + 8.0, dishR + 26.0, r));
    col = albedo * (0.1 + 1.4 * pool) * shadow * contact * (0.2 + 0.8 * far);
    float grain = hash(floor(g * 1.5)) - 0.5;
    col *= 1.0 + grain * 0.18;
  }
  // Ferrofluid: near-black, a mirror at grazing angles, darker down between the spikes. It is
  // blended over the porcelain across half a point of height, so the waterline is smooth.
  float wet = smoothstep(-0.5, 0.5, s.y);
  if (wet > 0.0) {
    float rise = clamp((s.x - LEVEL) / 22.0, 0.0, 1.0);
    float crowd = clamp(max(magA.w, magB.w), 0.0, 1.0);
    float deep = clamp(1.0 - crowd * (1.0 - rise) * (1.0 - n.z * n.z), 0.35, 1.0);
    float3 fluid = float3(0.004, 0.004, 0.006) * (0.3 + diff) + studio(g, s.x, rf) * fres * deep;
    fluid += float3(1.0, 0.97, 0.92) * pow(max(dot(rf, key), 0.0), 260.0) * 3.0;
    col = mix(col, fluid, wet);
  }

  // Gentle tone curve, vignette, then to display gamma; a little dither against banding.
  float2 uv = p / size - 0.5;
  col *= 1.0 - 1.1 * dot(uv * float2(0.9, 0.75), uv * float2(0.9, 0.75));
  col = col / (1.0 + col * 0.55) * 1.1;
  col = pow(max(col, float3(0.0)), float3(1.0 / 2.2));
  col += (hash(p) - 0.5) / 255.0;
  return half4(half3(col), 1.0);
}
"""
