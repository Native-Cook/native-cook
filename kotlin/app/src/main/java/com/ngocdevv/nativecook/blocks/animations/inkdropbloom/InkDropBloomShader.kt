package com.ngocdevv.nativecook.blocks.animations.inkdropbloom

// The whole scene of Ink Drop Bloom in one full-screen AGSL pass (API 33+): a warm studio wall and
// table, a tall glass tumbler of clear water, the ink plumes in it, the press and a falling drop.
// The React Native SkSL (inkDropShader.ts) with the maths unchanged: pow(x, 2.0) is written as
// pw2(x) (pow of a negative base is undefined on some GPUs) and the Display P3 conversion is
// dropped. Coordinates are dp: the canvas is scaled by the density before the shader is drawn.

internal const val INK_DROP_AGSL = """
uniform float2 size;
uniform float4 glass;   // cx, top, bottom, half width at the top
uniform float4 water;   // surface y, inner floor y, half width at the bottom, wall thickness
uniform float4 d0;      // drop: x, y (entry), age (s, < 0 = none), seed
uniform float4 d1;
uniform float4 d2;
uniform float4 d3;
uniform float3 c0;      // drop colours (sRGB, full strength)
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float4 falling; // a drop in the air: x, y, radius, alpha
uniform float3 fallCol;
uniform float4 press;   // disc y (< 0 = none), rod alpha, squeeze 0..1, ink amount 0..1
uniform float4 stir;    // x, y, angle (rad), radius
uniform float flow;     // seconds, drives the slow churn of the water
uniform float3 tint;    // mean colour of the ink, for the light thrown on the table

const float WALL_TOP = 4.0;

float pw2(float x) { return x * x; }

float hsh(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vnoise(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = hsh(i);
  float b = hsh(i + float2(1.0, 0.0));
  float c = hsh(i + float2(0.0, 1.0));
  float d = hsh(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm4(float2 q) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    s += a * vnoise(q);
    q = float2(q.x * 1.6 - q.y * 1.2, q.x * 1.2 + q.y * 1.6) + 3.1;
    a *= 0.5;
  }
  return s / 0.9375;
}

float fbm3(float2 q) {
  float s = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    s += a * vnoise(q);
    q = float2(q.x * 1.6 - q.y * 1.2, q.x * 1.2 + q.y * 1.6) + 1.7;
    a *= 0.5;
  }
  return s / 0.875;
}

// Half width of the glass's outer wall at height y.
float halfAt(float y) {
  float k = clamp((y - glass.y) / (glass.z - glass.y), 0.0, 1.0);
  return mix(glass.w, water.z + water.w, k);
}

// The room behind the glass: a warm wall, a table whose back edge is a soft horizon, light pooling
// from the upper left, the glass's shadow and the coloured light the water throws on the table.
float3 backdrop(float2 p) {
  float horizon = glass.z - 34.0;
  float2 uv = p / size;
  float3 wallTop = float3(0.925, 0.900, 0.862);
  float3 wallMid = float3(0.895, 0.862, 0.818);
  float3 wallLow = float3(0.858, 0.818, 0.770);
  float3 col = mix(wallTop, wallMid, smoothstep(0.0, 0.5, uv.y));
  col = mix(col, wallLow, smoothstep(0.4, 0.75, p.y / horizon));
  col += float3(0.05, 0.04, 0.03) * exp(-dot(uv - float2(0.2, 0.15), uv - float2(0.2, 0.15)) * 6.0);
  if (p.y > horizon) {
    float k = clamp((p.y - horizon) / (size.y - horizon), 0.0, 1.0);
    float3 tableNear = float3(0.905, 0.875, 0.835);
    float3 tableFar = float3(0.835, 0.795, 0.748);
    col = mix(tableFar, tableNear, smoothstep(0.0, 0.6, k));
    col -= float3(0.05, 0.05, 0.05) * (1.0 - smoothstep(0.0, 6.0, p.y - horizon));
    // The glass's shadow falls back and to the right, a caustic of tinted light beside it.
    float2 s = (p - float2(glass.x + glass.w * 0.55, glass.z + 4.0)) / float2(glass.w * 1.25, 16.0);
    col *= 1.0 - 0.32 * exp(-dot(s, s) * 1.6);
    float2 cc = (p - float2(glass.x + glass.w * 0.9, glass.z + 10.0)) / float2(glass.w * 0.6, 7.0);
    float caustic = exp(-dot(cc, cc) * 2.0);
    col += mix(float3(0.10, 0.09, 0.07), tint * 0.22, 0.6) * caustic;
  }
  col *= 1.0 + (hsh(floor(p * 1.5)) - 0.5) * 0.035;
  return col;
}

// A vortex-ring cap of radius r at q (relative to its centre): wider than tall, its lobes
// curling back up at the sides.
float ring(float2 q, float r) {
  float side = abs(q.x) / (r * 1.3);
  float capY = q.y + r * 0.38 * smoothstep(0.15, 0.9, side);
  float cap = 1.0 - length(float2(q.x / (r * 1.3), capY / (r * 0.7)));
  float lobe = exp(-pw2((side - 0.66) / 0.3));
  float band = clamp(1.0 - abs(capY + r * 0.1) / (r * 0.85), -1.0, 1.0);
  // Outside the lobes this falls to -1, so noise cannot raise specks of ink far from the plume.
  return max(cap, mix(-1.0, band * 0.55, lobe));
}

// One drop's plume density at p (already twisted by the stir).
float plume(float2 p, float4 d, float2 warp) {
  float a = d.z;
  if (a <= 0.0) return 0.0;
  float room = max(water.y - d.y, 1.0);
  float r = 9.0 + 30.0 * sqrt(a);
  // The head sinks fast at first and slows as the ring loses its push.
  float depth = room * 0.62 * (1.0 - exp(-a / 2.0)) + 10.0 * a / (1.0 + a);
  float2 head = float2(d.x, d.y + depth + 6.0);
  float2 q = p - head;
  if (abs(q.x) > r * 2.0 + 40.0 || q.y > r * 2.2 + 40.0 || p.y < d.y - 16.0) return 0.0;
  float bill = 10.0 + 9.0 * a;
  float2 w = (warp - 0.5) * bill;
  float2 qw = q + w;
  float shape = ring(qw, r);
  // The ring breaks into smaller rings that keep sinking: two children, then two grandchildren.
  float kid = smoothstep(0.9, 2.6, a);
  float rk = r * 0.48;
  float drop = r * (0.55 + 0.75 * kid);
  shape = max(shape, mix(-1.0, ring(qw - float2(-r * 0.62, drop), rk), kid));
  shape = max(shape, mix(-1.0, ring(qw - float2(r * 0.58, drop * 0.9), rk), kid));
  float kid2 = smoothstep(2.2, 4.4, a);
  float rg = r * 0.26;
  shape = max(shape, mix(-1.0, ring(qw - float2(-r * 0.9, drop + r * 0.75 * kid2), rg), kid2));
  shape = max(shape, mix(-1.0, ring(qw - float2(r * 0.95, drop * 0.9 + r * 0.7 * kid2), rg), kid2));
  // The stem: a thin column back up to where the drop went in, wavering.
  float t = clamp((p.y - d.y) / max(depth + 6.0, 1.0), 0.0, 1.0);
  float sw = 2.0 + r * 0.13 * t;
  float alongStem = step(d.y - 2.0, p.y) * step(p.y, head.y + 4.0);
  float stem = mix(-1.0, (1.0 - abs(qw.x) / sw) * (0.45 + 0.4 * t), alongStem);
  shape = max(shape, stem);
  // Billows and filaments: fbm in the drop's own frame, scrolling up as the plume sinks.
  float2 nq = (q + w * 1.4) / 20.0 + float2(d.w * 37.0, -a * 0.3);
  float n = fbm4(nq);
  float soft = smoothstep(0.08, 0.7, shape + (n - 0.5) * 0.8);
  float fil = 1.0 - abs(2.0 * fbm3(nq * 1.7 + 4.0) - 1.0);
  fil = pow(fil, 6.0) * smoothstep(0.0, 0.3, soft);
  // Pigment pools at the rim of each blot.
  float rim = smoothstep(0.05, 0.3, soft) * (1.0 - smoothstep(0.3, 0.75, soft));
  float conc = 1.9 / (1.0 + 0.36 * a);
  float haze = 0.22 * smoothstep(0.6, 4.0, a) * exp(-dot(q / float2(r * 2.0, r * 2.8), q / float2(r * 2.0, r * 2.8)));
  return (soft * soft * 0.8 + rim * 0.45 + fil * 0.6) * conc + haze;
}

half4 main(float2 pos) {
  float2 p = pos;
  float3 col = backdrop(p);

  float cx = glass.x;
  float top = glass.y;
  float bot = glass.z;
  float hw = halfAt(p.y);
  float dx = p.x - cx;
  float rimRy = 7.0;
  // Inside the outer silhouette: between the rim ellipse and the base, rounded at the bottom.
  float rimY = top + rimRy * sqrt(max(0.0, 1.0 - (dx / glass.w) * (dx / glass.w)));
  float rimYBack = top - rimRy * sqrt(max(0.0, 1.0 - (dx / glass.w) * (dx / glass.w)));
  float corner = 16.0;
  float u = abs(dx) - (hw - corner);
  float v = p.y - (bot - corner);
  float outer = (u > 0.0 && v > 0.0) ? length(float2(u, v)) - corner : max(abs(dx) - hw, p.y - bot);
  float inGlass = (1.0 - smoothstep(-0.8, 0.8, outer)) * smoothstep(rimYBack - 0.8, rimYBack + 0.8, p.y);

  if (inGlass > 0.0) {
    float wall = water.w;
    float ihw = hw - wall;
    float floorY = water.y;
    float inner = max(abs(dx) - ihw, p.y - floorY);
    float inside = 1.0 - smoothstep(-0.8, 0.8, inner);
    float surfY = water.x + 6.0 * sqrt(max(0.0, 1.0 - (dx / ihw) * (dx / ihw)));
    float inWater = inside * smoothstep(surfY - 0.8, surfY + 0.8, p.y);
    float xn = clamp(dx / hw, -1.0, 1.0);
    float edge = pow(abs(xn), 6.0);

    // What the glass shows of the room: squeezed by the water, a little by the empty glass.
    float squeeze = mix(0.97, 0.66, inWater);
    float2 sp = float2(cx + dx * squeeze, p.y + inWater * 10.0);
    float3 seen = backdrop(sp);
    seen = mix(seen, seen * float3(0.95, 0.99, 1.0) * (1.0 + 0.05 * (1.0 - abs(xn))), inWater);

    // Ink in the water.
    if (inWater > 0.0) {
      float2 q = p;
      float2 sd = q - stir.xy;
      float sf = stir.z * exp(-dot(sd, sd) / max(stir.w * stir.w, 1.0));
      float cs = cos(sf);
      float sn = sin(sf);
      q = stir.xy + float2(cs * sd.x - sn * sd.y, sn * sd.x + cs * sd.y);
      // The press squeezes what is under the disc into the room left below it.
      float keep = 1.0;
      if (press.x > 0.0) {
        float pd = max(press.x, water.x);
        keep = smoothstep(pd - 1.0, pd + 6.0, q.y);
        float span = max(floorY - pd, 1.0);
        q.y = water.x + (q.y - pd) * (floorY - water.x) / span;
        keep *= 1.0 - press.z * press.z;
      }
      float2 warp = float2(fbm3(q / 70.0 + float2(flow * 0.07, 0.0)), fbm3(q / 70.0 + float2(5.2, -flow * 0.09)));
      float3 absorb = float3(0.0);
      float k0 = plume(q, d0, warp);
      float k1 = plume(q, d1, warp);
      float k2 = plume(q, d2, warp);
      float k3 = plume(q, d3, warp);
      absorb += k0 * -log(max(c0, float3(0.02)));
      absorb += k1 * -log(max(c1, float3(0.02)));
      absorb += k2 * -log(max(c2, float3(0.02)));
      absorb += k3 * -log(max(c3, float3(0.02)));
      seen *= exp(-absorb * keep * inWater * 0.9 * press.w);
    }

    // The meniscus: a bright line along the water's front edge, a darker one just under it.
    float men = exp(-pw2((p.y - surfY) / 1.1)) * inside;
    float menBack = exp(-pw2((p.y - (water.x - 6.0 * sqrt(max(0.0, 1.0 - (dx / ihw) * (dx / ihw))))) / 1.0)) * inside;
    seen = mix(seen, float3(0.99, 0.99, 0.985), men * 0.75);
    seen = mix(seen, float3(0.70, 0.72, 0.73), menBack * 0.35);
    float underSurf = smoothstep(0.0, 14.0, p.y - surfY) * (1.0 - smoothstep(14.0, 40.0, p.y - surfY));
    seen *= 1.0 - 0.04 * underSurf * inside;

    // The walls and the base: thick glass, greenish at the edges.
    float inWall = 1.0 - inside;
    float3 glassTint = float3(0.80, 0.86, 0.85);
    seen = mix(seen, seen * glassTint, inWall * (0.35 + 0.5 * edge));
    seen = mix(seen, float3(0.40, 0.45, 0.45), edge * 0.35);
    // Highlight streaks: a broad soft one on the left, a thin sharp one on the right.
    float streakA = exp(-pw2((xn + 0.62) / 0.06)) * smoothstep(top, top + 40.0, p.y) * (1.0 - smoothstep(bot - 60.0, bot, p.y));
    float streakB = exp(-pw2((xn - 0.78) / 0.018)) * smoothstep(top + 20.0, top + 80.0, p.y) * (1.0 - smoothstep(bot - 90.0, bot - 30.0, p.y));
    seen += float3(1.0, 1.0, 0.99) * (streakA * 0.28 + streakB * 0.55);
    // The base: a lens of thick glass with a bright caustic arc and a dark contact line.
    float inBase = smoothstep(floorY - 1.0, floorY + 1.0, p.y);
    float arc = exp(-pw2((p.y - (floorY + 8.0 + 5.0 * xn * xn)) / 1.6)) * (1.0 - edge);
    seen = mix(seen, seen * float3(0.86, 0.92, 0.91), inBase * 0.5);
    seen += mix(float3(0.25, 0.25, 0.23), tint * 0.5, 0.5) * arc * inBase;
    seen *= 1.0 - 0.35 * exp(-pw2((p.y - (bot - 1.5)) / 1.2));

    col = mix(col, seen, inGlass);
  }

  // The rim: a thin bright ellipse, its back half dimmer.
  float rr = (p.y - top) / rimRy;
  float re = abs(length(float2(dx / glass.w, rr)) - 1.0) * glass.w;
  float rim = exp(-re * re / 1.4);
  col = mix(col, float3(0.98, 0.99, 0.985), rim * (p.y > top ? 0.8 : 0.45));

  // The disc of the press and its rod.
  if (press.x > 0.0) {
    float pyy = press.x;
    float phw = halfAt(pyy) - water.w - 1.0;
    float rodHw = 3.2;
    float rodTop = glass.y - 44.0;
    if (abs(dx) < rodHw + 1.0 && p.y < pyy && p.y > rodTop) {
      float k = abs(dx) / rodHw;
      float3 steel = mix(float3(0.80, 0.81, 0.82), float3(0.42, 0.44, 0.46), k * k);
      steel += float3(0.18) * exp(-pw2((dx + 1.2) / 0.7));
      float a = (1.0 - smoothstep(rodHw - 0.6, rodHw + 0.6, abs(dx))) * press.y * smoothstep(rodTop, rodTop + 36.0, p.y);
      col = mix(col, steel, a);
    }
    float e = length(float2(dx / phw, (p.y - pyy) / 6.0));
    if (e < 1.08) {
      float mesh = 0.5 + 0.5 * sin(dx * 2.2) * sin((p.y - pyy) * 5.0);
      float3 discCol = mix(float3(0.55, 0.57, 0.58), float3(0.74, 0.75, 0.76), mesh);
      discCol = mix(discCol, float3(0.95, 0.95, 0.94), exp(-pw2((e - 0.96) / 0.05)));
      float a = 1.0 - smoothstep(1.0, 1.06, e);
      col = mix(col, discCol, a * 0.85 * press.y);
    }
  }

  // A drop in the air: a bead with a highlight, stretched by its speed.
  if (falling.w > 0.0) {
    float2 fq = (p - falling.xy) / float2(falling.z, falling.z * 1.35);
    fq.y += 0.25;
    float fd = length(fq);
    float a = (1.0 - smoothstep(0.9, 1.05, fd)) * falling.w;
    float3 bead = fallCol * (0.75 + 0.25 * (1.0 - fd));
    bead += float3(0.9) * exp(-dot(fq - float2(-0.35, -0.35), fq - float2(-0.35, -0.35)) * 18.0);
    col = mix(col, bead, a);
  }

  // A soft vignette on the warm room, then dither.
  float2 uv = pos / size - 0.5;
  col *= 1.0 - 0.16 * dot(uv, uv);
  col += (hsh(pos) - 0.5) / 255.0;
  return half4(half3((col)), 1.0);
}
"""
