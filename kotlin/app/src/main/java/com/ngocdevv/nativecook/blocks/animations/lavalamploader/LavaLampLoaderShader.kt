package com.ngocdevv.nativecook.blocks.animations.lavalamploader

// Lava Lamp Loader — the whole scene in one full-screen pass: a warm wall lit by the lamp, a
// wooden shelf, and the lamp itself (chrome cap, tapered glass full of glowing liquid, chrome base).
//
// The wax is a metaball field: each blob adds r^2 / d^2 (its vertical axis stretched by `w`, so a
// rising blob grows tall and thin), two big blobs sunk under the glass floor make the warm pool,
// and the surface is where the field crosses 1. The field's analytic gradient gives a normal, so
// the wax is lit from below by the bulb, its thin edges glow (light passes through them), and it
// cools from coral at the bottom to crimson near the cap. The glass is a cylinder of liquid: the
// interior is seen magnified in the middle and squeezed at the edges, with two specular streaks
// and a Fresnel rim.
//
// Coordinates: pt, origin at the canvas corner. Blobs are given relative to the lamp's axis (x)
// and its top (y). Portable: fixed loops, no arrays, no dynamic indexing. The output is opaque and
// clamped to sRGB.
//
// Ported from the React Native SkSL unchanged except for the Display P3 conversion, which Android
// does not need: AGSL is the same dialect (API 33+). Coordinates are dp: the canvas is scaled by
// the density before the shader is drawn.

internal const val LAVA_LAMP_LOADER_AGSL = """
uniform float2 size;
uniform float4 lamp;
uniform float4 b0;
uniform float4 b1;
uniform float4 b2;
uniform float4 b3;
uniform float4 b4;
uniform float4 bf;
uniform float glow;
uniform float phase;

const float CAP_END = 0.13;
const float GLASS_END = 0.70;
// The field level the wax's surface sits on.
const float ISO = 1.3;

float g2(float x) {
  return exp(-x * x);
}

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float3 clampColor(float3 c) {
  return clamp(c, 0.0, 1.0);
}

// One blob's share of the field at q, and its gradient (xy) in .yz.
float3 ball(float2 q, float4 b) {
  if (b.z < 0.5) return float3(0.0);
  float2 d = q - b.xy;
  d.y /= b.w;
  float d2 = dot(d, d) + 0.2 * b.z * b.z;
  float f = b.z * b.z / d2;
  float2 g = -2.0 * f / d2 * float2(d.x, d.y / b.w);
  return float3(f, g);
}

float3 field(float2 q, float k, float floorY) {
  float3 f = ball(q, b0) + ball(q, b1) + ball(q, b2) + ball(q, b3) + ball(q, b4) + ball(q, bf);
  // The pool: two wide blobs sunk under the floor of the glass.
  f += ball(q, float4(-22.0 * k, floorY + 44.0 * k, 60.0 * k, 0.62));
  f += ball(q, float4(27.0 * k, floorY + 52.0 * k, 58.0 * k, 0.62));
  return f;
}

// Half-width of the lamp's silhouette at height y (lamp-local), and which part it is (0 cap,
// 1 glass, 2 base).
float2 profile(float y, float H, float k) {
  float capEnd = H * CAP_END;
  float glassEnd = H * GLASS_END;
  if (y < capEnd) {
    float s = clamp(y / capEnd, 0.0, 1.0);
    // A small rounded crown, then the cone.
    float crown = sqrt(clamp(y / (5.0 * k), 0.0, 1.0));
    return float2((20.0 + 16.0 * s) * k * crown, 0.0);
  }
  if (y < glassEnd) {
    float s = (y - capEnd) / (glassEnd - capEnd);
    return float2((33.0 + 45.0 * pow(s, 1.15)) * k, 1.0);
  }
  float s = clamp((y - glassEnd) / (H - glassEnd), 0.0, 1.0);
  // A narrow collar, then the flared cone.
  float collar = 1.0 - 0.06 * g2((s - 0.06) / 0.035);
  return float2((71.0 + 45.0 * pow(s, 0.9)) * k * collar, 2.0);
}

// Polished chrome: a dark room with two softboxes and the lamp's own glow, mapped by the surface's
// sideways angle u (-1..1) and a height v.
float3 chrome(float u, float v, float warm) {
  float3 c = float3(0.075, 0.06, 0.06);
  c += float3(0.95, 0.9, 0.86) * g2((u + 0.48) / 0.12) * 0.85;
  c += float3(0.7, 0.72, 0.78) * g2((u - 0.42) / 0.2) * 0.32;
  c += float3(1.0, 0.45, 0.2) * warm * (0.35 + 0.35 * (1.0 - abs(u)));
  c *= 0.82 + 0.18 * v;
  // The edge turns away from the lights.
  c *= 1.0 - 0.55 * pow(abs(u), 5.0);
  return c;
}

half4 main(float2 p) {
  float cx = lamp.x;
  float top = lamp.y;
  float H = lamp.z;
  float k = lamp.w;
  float2 q = float2(p.x - cx, p.y - top);
  float capEnd = H * CAP_END;
  float glassEnd = H * GLASS_END;
  float shelfY = H;

  // The wall: warm plum, lit by the lamp around the glass.
  float3 col = mix(float3(0.07, 0.035, 0.05), float3(0.13, 0.055, 0.045), clamp(p.y / size.y, 0.0, 1.0));
  float2 gc = float2(q.x, q.y - H * 0.48);
  float gd = length(gc * float2(1.0, 0.62));
  float flick = 1.0 + 0.025 * sin(phase * 3.0) + 0.015 * sin(phase * 7.0 + 1.3);
  col += float3(1.0, 0.38, 0.16) * (0.36 * exp(-gd * gd / (2.0 * 120.0 * 120.0 * k * k)) + 0.12 * exp(-gd / (260.0 * k))) * glow * flick;

  // The shelf: a walnut ledge with a lit top face and a dark front.
  if (q.y > shelfY) {
    float sy = q.y - shelfY;
    float grain = 0.5 + 0.5 * sin(p.x * 0.09 + sin(p.x * 0.013) * 4.0 + sy * 0.4);
    if (sy < 16.0 * k) {
      float3 wood = mix(float3(0.21, 0.1, 0.06), float3(0.3, 0.15, 0.08), grain);
      float pool = g2(q.x / (150.0 * k)) * g2(sy / (14.0 * k));
      col = wood * (0.55 + 0.25 * sy / (16.0 * k)) + float3(1.0, 0.42, 0.18) * pool * 0.55 * glow;
      // Contact shadow of the base.
      col *= 1.0 - 0.6 * exp(-pow(abs(q.x) / (114.0 * k), 8.0)) * exp(-sy / (3.0 * k));
    } else {
      float3 wood = mix(float3(0.11, 0.05, 0.035), float3(0.15, 0.07, 0.045), grain);
      col = wood * (1.0 - 0.5 * clamp((sy - 16.0 * k) / (60.0 * k), 0.0, 1.0));
      if (sy < 17.5 * k) col += float3(0.5, 0.28, 0.16) * 0.25;
    }
  }

  float2 pr = profile(q.y, H, k);
  float hw = pr.x;
  if (q.y >= 0.0 && q.y <= shelfY && hw > 0.5) {
    float ax = abs(q.x);
    float cover = clamp(hw - ax + 0.5, 0.0, 1.0);
    if (cover > 0.0) {
      float u = clamp(q.x / hw, -1.0, 1.0);
      float3 lc;
      if (pr.y < 0.5) {
        float v = q.y / capEnd;
        lc = chrome(u, v, 0.18 + 0.6 * v * v);
        // A thin bright lip where the cap meets the glass.
        lc += float3(1.0, 0.6, 0.4) * g2((q.y - capEnd + 1.5 * k) / (1.2 * k)) * 0.35;
      } else if (pr.y < 1.5) {
        // Liquid seen through a glass cylinder: magnified in the middle, squeezed at the edges.
        float xi = hw * u * (0.74 + 0.26 * u * u);
        float2 iq = float2(xi, q.y);
        float level = capEnd + 12.0 * k;
        float thick = sqrt(max(0.0, 1.0 - u * u));
        float heat = exp(-(glassEnd - q.y) / (130.0 * k));
        // Amber liquid, brightest just over the bulb and through the thick middle of the glass.
        float3 liquid = mix(float3(0.3, 0.1, 0.035), float3(1.0, 0.66, 0.22), heat * (0.5 + 0.5 * thick));
        liquid += float3(0.35, 0.12, 0.03) * thick * 0.35;
        liquid = mix(liquid, float3(0.4, 0.1, 0.05), 0.3 * (1.0 - thick));
        if (q.y < level) {
          // The air pocket under the cap.
          liquid = mix(float3(0.12, 0.045, 0.04), float3(0.24, 0.1, 0.07), thick);
        }
        float3 f = field(iq, k, glassEnd);
        float gl = length(f.yz) + 1e-4;
        float edge = clamp((f.x - ISO) / gl * 1.2 + 0.5, 0.0, 1.0);
        float3 wax = liquid;
        if (f.x > ISO * 0.6 && q.y > level - 2.0) {
          // A smooth normal: tilted where the field falls off (the edges), flat inside.
          float3 n = normalize(float3(-f.yz / (f.x * f.x * f.x) * 30.0 * k, 1.0));
          float t = smoothstep(capEnd, glassEnd, q.y);
          float3 hot = float3(0.98, 0.2, 0.1);
          float3 cool = float3(0.42, 0.025, 0.1);
          float3 base = mix(cool, hot, t * t * (0.6 + 0.4 * t) + 0.12);
          // Lit from the bulb below: faces turned down glow, faces turned up sit in shade.
          float below = clamp(0.55 + 0.6 * n.y, 0.0, 1.0);
          float rim = 1.0 - n.z;
          wax = base * (0.55 + 0.7 * below * (0.35 + 0.65 * heat + 0.3 * t));
          // Thin edges let the bulb's light through.
          wax += float3(1.0, 0.5, 0.18) * rim * rim * (0.25 + 0.55 * heat);
          // A soft sheen from the room on the upper-left shoulder.
          wax += float3(1.0, 0.82, 0.7) * pow(max(0.0, dot(n, normalize(float3(-0.45, -0.55, 0.7)))), 18.0) * 0.22;
          wax *= 0.8 + 0.2 * thick;
        }
        lc = mix(liquid, wax, q.y > level - 2.0 ? edge : 0.0);
        // The wax's skin bends the light away: a faint darker line where it meets the liquid.
        if (q.y > level - 2.0) lc *= 1.0 - 0.28 * edge * (1.0 - edge) * 4.0;
        if (abs(q.y - level) < 1.2 * k) lc += float3(1.0, 0.6, 0.35) * 0.25 * thick;
        // Glass: two streaks of the softboxes, a Fresnel rim, a darker outline.
        float streakY = smoothstep(capEnd, capEnd + 30.0 * k, q.y) * (1.0 - smoothstep(glassEnd - 30.0 * k, glassEnd, q.y));
        lc += float3(1.0, 0.95, 0.9) * g2((u + 0.6) / 0.055) * 0.42 * streakY;
        lc += float3(1.0, 0.9, 0.85) * g2((u - 0.71) / 0.025) * 0.22 * streakY;
        lc += float3(1.0, 0.55, 0.3) * pow(abs(u), 7.0) * 0.35;
        lc *= 1.0 - 0.45 * smoothstep(0.93, 1.0, abs(u));
      } else {
        float v = (q.y - glassEnd) / (H - glassEnd);
        // The top of the base catches the bulb's light; a groove under the collar.
        lc = chrome(u, 1.0 - v, 0.55 * exp(-v * 4.0) + 0.08);
        lc *= 1.0 - 0.5 * g2((v - 0.06) / 0.02);
        lc += float3(1.0, 0.5, 0.25) * g2((q.y - glassEnd) / (2.0 * k)) * 0.6 * glow;
      }
      col = mix(col, lc, cover);
    }
  }

  // Vignette and grain.
  float2 vc = (p / size - 0.5) * float2(1.2, 1.0);
  col *= 1.0 - 0.55 * dot(vc, vc);
  col += (hash(floor(p * 2.0)) - 0.5) * 0.03;
  return half4(half3(clampColor(col)), 1.0);
}
"""
