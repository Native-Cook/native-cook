package com.ngocdevv.nativecook.blocks.pickers.sundialtimepicker

// The whole picker in one full-screen AGSL pass (API 33+): a sky-tinted backdrop that warms when
// the sun is low, the dashed sky arc with the sun handle, the stone dial's drop shadow, the
// limestone dial with its carved hour, half and quarter lines lit by the sun's real direction,
// carved rings, the bronze gnomon and its shadow (the triangle it casts, with a widening penumbra).
// The React Native SkSL unchanged (see sundialShader.ts) except that Android draws in sRGB, so
// there is no P3 conversion. Coordinates are dp: the canvas is scaled by the density first.

internal const val SUNDIAL_AGSL = """
uniform float2 size;
uniform float4 dial;
uniform float4 sun;
uniform float4 handle;
uniform float4 finger;

const float SIN_LAT = 0.6428;
const float TAN_LAT = 0.8391;
const float PI = 3.14159265;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hs(i), hs(i + float2(1.0, 0.0)), f.x), mix(hs(i + float2(0.0, 1.0)), hs(i + float2(1.0, 1.0)), f.x), f.y);
}

float fbm(float2 q) {
  return vn(q) * 0.5 + vn(q * 2.03 + 7.1) * 0.3 + vn(q * 4.1 + 3.3) * 0.2;
}

float3 toP3(float3 c) {
  // Android draws the shader in sRGB: no Display P3 conversion.
  return clamp(c, 0.0, 1.0);
}

float sdSeg(float2 p, float2 a, float2 b) {
  float2 pa = p - a;
  float2 ba = b - a;
  float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h);
}

// Signed distance to a triangle (negative inside).
float sdTri(float2 p, float2 p0, float2 p1, float2 p2) {
  float2 e0 = p1 - p0;
  float2 e1 = p2 - p1;
  float2 e2 = p0 - p2;
  float2 v0 = p - p0;
  float2 v1 = p - p1;
  float2 v2 = p - p2;
  float2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
  float2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
  float2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
  float s = sign(e0.x * e2.y - e0.y * e2.x);
  float2 d = min(min(float2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     float2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     float2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
  return -sqrt(d.x) * sign(d.y);
}

// One carved line along direction dir from the centre: shade (multiplier) of a V groove of
// half-width w between radii r0 and r1, lit by L.
float groove(float2 q, float2 dir, float r0, float r1, float w, float3 L) {
  float along = dot(q, dir);
  float2 perp = float2(-dir.y, dir.x);
  float across = dot(q, perp);
  float cut = (1.0 - smoothstep(w * 0.6, w, abs(across))) * smoothstep(r0, r0 + 1.0, along) * (1.0 - smoothstep(r1 - 1.0, r1, along));
  float side = sign(across);
  float3 n = normalize(float3(-side * perp * 0.85, 0.55));
  float lit = clamp(dot(n, L), 0.0, 1.0);
  return mix(1.0, 0.42 + 0.9 * lit, cut);
}

half4 main(float2 p) {
  float2 c = dial.xy;
  float R = dial.z;
  float u = dial.w;
  float3 S = normalize(float3(sun.x, -sun.y, max(sun.z, 0.06)));
  float alt = S.z;
  float warm = 1.0 - smoothstep(0.22, 0.72, alt);
  float3 light = mix(float3(1.0, 0.98, 0.95), float3(1.0, 0.83, 0.62), warm);

  // backdrop
  float yN = p.y / size.y;
  float3 top = mix(float3(0.80, 0.87, 0.94), float3(0.98, 0.86, 0.74), warm);
  float3 col = mix(top, float3(0.95, 0.93, 0.89), smoothstep(0.05, 0.75, yN));
  col += (hs(p) - 0.5) * 0.02;
  float2 hd = p - handle.xy;
  col += float3(1.0, 0.86, 0.6) * exp(-dot(hd, hd) / (9000.0 * u * u)) * (0.22 + 0.25 * warm);

  // the sky arc: dashed, upper half, between 7 and 17 h
  float2 ax = float2(min(size.x * 0.5 - 22.0, R + 30.0 * u), R + 50.0 * u);
  float2 aq = (p - c) / ax;
  float ag = length(aq);
  float arcD = abs(ag - 1.0) * min(ax.x, ax.y);
  float b = atan(-aq.y, aq.x);
  float inRange = step(PI / 12.0 - 0.01, b) * step(b, PI * 11.0 / 12.0 + 0.01);
  float dash = step(0.0, sin(b * 70.0));
  col = mix(col, float3(0.45, 0.40, 0.36), (1.0 - smoothstep(0.6, 1.4, arcD)) * inRange * dash * 0.45);

  // the dial's drop shadow on the backdrop (cast away from the sun)
  float2 off = -float2(S.x, S.y) / max(alt, 0.25) * 10.0 * u;
  float ds = length(p - c - off) - R;
  col *= 1.0 - 0.32 * (1.0 - smoothstep(-6.0 * u, 18.0 * u, ds));

  float2 q = p - c;
  float r = length(q);
  if (r < R + 1.0) {
    // limestone
    float m = fbm(q / (60.0 * u)) * 0.7 + fbm(q / (9.0 * u)) * 0.3;
    float3 stone = mix(float3(0.80, 0.76, 0.69), float3(0.92, 0.89, 0.83), smoothstep(0.3, 0.7, m));
    stone *= 1.0 - 0.12 * smoothstep(0.55, 1.0, r / R) * fbm(q / (20.0 * u) + 4.0);
    float2 pc = q / (7.0 * u);
    float2 pf = fract(pc) - 0.5;
    float2 pj = float2(hs(floor(pc) + 1.3), hs(floor(pc) + 8.1)) - 0.5;
    float pit = step(0.86, hs(floor(pc))) * (1.0 - smoothstep(0.06, 0.12, length(pf - pj * 0.6)));
    stone *= 1.0 - pit * 0.22;
    float lich = smoothstep(0.68, 0.8, fbm(q / (16.0 * u) + 11.0)) * smoothstep(0.5, 0.95, r / R);
    stone = mix(stone, float3(0.66, 0.69, 0.58), lich * 0.5);
    // the chamfered edge
    float3 n = float3(0.0, 0.0, 1.0);
    float ch = smoothstep(R - 9.0 * u, R, r);
    n = normalize(mix(n, float3(q / max(r, 0.001) * 0.9, 0.45), ch));
    float lit = clamp(dot(n, S), 0.0, 1.0);
    float3 dcol = stone * light * (0.52 + 0.55 * lit);
    // carved rings
    dcol *= 1.0 - 0.32 * (1.0 - smoothstep(0.4 * u, 1.2 * u, abs(r - 0.80 * R)));
    dcol *= 1.0 - 0.28 * (1.0 - smoothstep(0.3 * u, 1.0 * u, abs(r - 0.94 * R)));
    dcol *= 1.0 - 0.32 * (1.0 - smoothstep(0.4 * u, 1.2 * u, abs(r - 0.28 * R)));
    // hour, half and quarter lines (only on the north half)
    float alpha = atan(q.x, -q.y);
    if (abs(alpha) < 1.4) {
      float tp = 12.0 + atan(tan(alpha) / SIN_LAT) * 12.0 / PI;
      float tq = clamp(floor(tp * 4.0 + 0.5) / 4.0, 7.0, 17.0);
      float hq = (tq - 12.0) * PI / 12.0;
      float th = atan(SIN_LAT * tan(hq));
      float2 dir = float2(sin(th), -cos(th));
      float fr = fract(tq);
      float isHour = 1.0 - step(0.01, fr);
      float isHalf = (1.0 - isHour) * (1.0 - step(0.01, abs(fr - 0.5)));
      float r0 = mix(mix(0.755, 0.71, isHalf), 0.30, isHour) * R;
      float w = mix(mix(0.9, 1.2, isHalf), 2.1, isHour) * u;
      dcol *= groove(q, dir, r0, 0.80 * R, w, S);
    }
    // the gnomon's shadow: triangle O, B, T with a widening penumbra
    float g = handle.z;
    float2 B = c + float2(0.0, -g);
    float hg = g * TAN_LAT;
    float2 sh = -float2(S.x, S.y) / max(alt, 0.08) * hg;
    float shl = length(sh);
    sh *= min(1.0, 2.2 * R / max(shl, 0.001));
    float2 T = B + sh;
    float sd = sdTri(p, c, B, T);
    float pen = 0.8 * u + 0.035 * length(p - c);
    float shade = 1.0 - smoothstep(-pen, pen, sd);
    dcol = mix(dcol, dcol * float3(0.50, 0.52, 0.60), shade * 0.85);
    // the gnomon: a bronze plate, lit on the side facing the sun
    float gd = sdSeg(p, c, B);
    float gk = clamp((c.y - p.y) / g, 0.0, 1.0);
    float plate = 1.0 - smoothstep(2.6 * u, 3.6 * u, gd);
    float sideLit = clamp(0.5 + 0.5 * sign(p.x - c.x) * S.x * 1.6, 0.0, 1.0);
    float3 bronze = mix(float3(0.30, 0.21, 0.13), float3(0.70, 0.54, 0.36), sideLit * 0.8 + 0.2 * gk);
    bronze = mix(bronze, float3(0.38, 0.52, 0.44), smoothstep(0.62, 0.8, fbm(p / (5.0 * u))) * 0.4);
    bronze += float3(0.30, 0.24, 0.16) * (1.0 - smoothstep(0.0, 1.0 * u, abs(p.x - c.x))) * light;
    dcol *= 1.0 - 0.3 * (1.0 - smoothstep(3.0 * u, 7.0 * u, gd));
    dcol = mix(dcol, bronze * light, plate);
    // the gnomon's foot boss
    float boss = 1.0 - smoothstep(5.0 * u, 6.0 * u, length(q));
    dcol = mix(dcol, mix(float3(0.42, 0.30, 0.17), float3(0.85, 0.66, 0.42), clamp(dot(normalize(float3(q, 4.0 * u)), S), 0.0, 1.0)) * light, boss);
    col = mix(col, dcol, 1.0 - smoothstep(R - 0.5, R + 0.5, r));
  }

  // the sun handle: a hot core, a glow, and the finger's ring
  float hdl = length(p - handle.xy);
  float3 core = mix(float3(1.0, 0.92, 0.7), float3(1.0, 0.72, 0.4), warm);
  col = mix(col, core, 1.0 - smoothstep(13.0 * u, 14.5 * u, hdl));
  col = mix(col, float3(1.0, 0.99, 0.94), (1.0 - smoothstep(6.0 * u, 9.0 * u, hdl)) * 0.8);
  col += core * exp(-hdl * hdl / (500.0 * u * u)) * 0.35 * step(14.0 * u, hdl);
  float ringF = (1.0 - smoothstep(1.0, 2.2, abs(hdl - 24.0 * u))) * finger.z;
  col = mix(col, float3(0.30, 0.26, 0.22), ringF * 0.5);

  float2 v = p / size - 0.5;
  col *= 1.0 - 0.18 * dot(v, v);
  return half4(half3(toP3(col)), 1.0);
}
"""

