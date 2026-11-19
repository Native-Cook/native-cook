// The sky behind Soft Weather: a true mesh gradient, one Skia RuntimeEffect pass over the screen.
//
// The mesh is a 4 x 4 grid of control points. Each point has a position (its place on a regular grid
// plus an offset, `off`) and a colour in OKLab (`lab`). Between the points both are interpolated with
// a Catmull-Rom (bicubic) patch surface, so the colour field is smooth across patch borders (C1, no
// creases) and moving one point drags the colours around it.
//
// A fragment shader cannot draw a warped grid directly, so it inverts the warp: for a screen point q it
// looks for the grid coordinate uv with uv + D(uv) = q (D = the interpolated offsets) by two fixed-point
// steps, uv <- q - D(uv). With offsets under ~15 % of the screen two steps land within a fraction of a
// point. The colour is then the bicubic interpolation of the OKLab grid at uv, converted to sRGB.
//
// On top, for the weather: rain on the glass. Drops are seeded per cell and refract the mesh (they
// sample it again through a lens: a drop on a window shows the scene flipped), and faint streaks of
// drizzle fall behind them. `rain` = 0 skips all of it.
//
// Output: Skia treats runtime-effect output as Display P3 on iOS (AGENTS "Mistakes"), so with `wide` = 1
// the linear sRGB result is converted to P3 before encoding. Then a triangular dither of +-1 LSB and a
// static grain: soft gradients never band.

export const WEATHER_N = 4;

export const WEATHER_SKSL = `
uniform float2 size;
uniform float2 off[16];
uniform float3 lab[16];
uniform float rain;     // 0..1
uniform float phase;    // ambient clock / LOOP, 0..1
uniform float wide;     // 1: write Display P3 (iOS)
uniform float dpr;      // pixels per point, for the dither

const int N = 4;
const float G = 3.0;    // N - 1
const float TAU = 6.2831853;

float cr(float x) {
  x = abs(x);
  if (x < 1.0) return (1.5 * x - 2.5) * x * x + 1.0;
  if (x < 2.0) return ((-0.5 * x + 2.5) * x - 4.0) * x + 2.0;
  return 0.0;
}

// Catmull-Rom weight of control index i at grid coordinate g (0..G); the phantom points past the
// edges repeat the edge point, so the weights always sum to 1.
float wt(float g, int i) {
  float w = cr(g - float(i));
  if (i == 0) w += cr(g + 1.0);
  if (i == N - 1) w += cr(g - float(N));
  return w;
}

float2 disp(float2 uv) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[4];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float2 acc = float2(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) { acc += (wx[i] * wy) * off[j * N + i]; }
  }
  return acc;
}

float3 meshLab(float2 uv) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[4];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float3 acc = float3(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) { acc += (wx[i] * wy) * lab[j * N + i]; }
  }
  return acc;
}

// Screen point (0..1) -> OKLab colour of the warped mesh there.
float3 mesh(float2 q) {
  float2 uv = q - disp(q);
  uv = q - disp(uv);
  return meshLab(uv);
}

float3 labToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return float3(
     4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

float3 encode(float3 lin) {
  lin = clamp(lin, 0.0, 1.0);
  if (wide > 0.5) {
    lin = float3(
      dot(lin, float3(0.8225, 0.1774, 0.0)),
      dot(lin, float3(0.0332, 0.9669, 0.0)),
      dot(lin, float3(0.0171, 0.0724, 0.9108)));
  }
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }

// Drops on the glass. Each returns its normalised offset d (|d| < 1 inside) for the nearest drop of
// a layer, and the drop's radius in z; w = presence (0..1).
// A drop is a plano-convex lens: the scene behind shows flipped and squeezed, its top rim is dark and
// its lower half gathers light; a soft shadow sits just under it.
float4 lensDrop(float2 p, float2 c, float r, float sy, float pres) {
  float2 d = (p - c) / float2(r, r * sy);
  return float4(d, r, pres);
}
half4 main(float2 p) {
  float2 q = p / size;
  float3 c = mesh(q);
  float3 lin = labToLinear(c);

  if (rain > 0.002) {
    // drizzle behind the glass: thin streaks falling a little slanted, in loops of the clock
    float slant = p.x + p.y * 0.16;
    float col = floor(slant / 6.0);
    float hc = h21(float2(col, 3.7));
    float speed = 3.0 + floor(hc * 3.0);                 // whole turns per loop: seamless
    float y = p.y / size.y - phase * speed + hc * 7.0;
    float seg = fract(y * 5.0);
    float streak = smoothstep(0.0, 0.04, seg) * (1.0 - smoothstep(0.08, 0.30, seg));
    float across = 1.0 - abs(fract(slant / 6.0) - 0.5) * 2.0;
    streak *= smoothstep(0.55, 1.0, across) * step(0.45, hc);
    lin += float3(streak * 0.055 * rain);

    // drops on the glass: beads that come and go, a few that slide down, and the trail they leave
    float2 cell = float2(30.0, 30.0);
    float2 ci = floor(p / cell);
    float hb = h21(ci);
    float hb2 = h21(ci + 17.3);
    float life = fract(phase * 2.0 + hb * 5.0);
    float vis = smoothstep(0.0, 0.1, life) * (1.0 - smoothstep(0.75, 1.0, life));
    float2 cb = (ci + 0.25 + 0.5 * float2(hb, fract(hb2 * 7.0))) * cell;
    float4 best = lensDrop(p, cb, 2.2 + 3.0 * fract(hb * 13.0), 1.0, vis * step(hb2, 0.6 * rain));

    float colW = 52.0;
    float cx = floor(p.x / colW);
    float hs = h21(float2(cx, 9.1));
    float turns = 1.0 + floor(hs * 2.0);                  // whole turns per loop
    float fy = fract(phase * turns + hs * 3.0);
    float sy = -60.0 + fy * (size.y + 120.0);
    float slide = step(0.3, hs) * smoothstep(0.25, 0.6, rain);
    float sx = (cx + 0.3 + 0.4 * hs) * colW + sin(sy * 0.03 + hs * TAU) * 5.0;
    float4 sd = lensDrop(p, float2(sx, sy), 5.0 + 3.0 * fract(hs * 11.0), 1.18, slide);
    if (sd.w > 0.0 && length(sd.xy) < length(best.xy) + 0.6) best = sd;

    // the trail: small beads in the column above the sliding drop, thinning out with distance
    float ty = floor(p.y / 13.0);
    float ht = h21(float2(cx, ty));
    float tcy = (ty + 0.5) * 13.0;
    float behind = sy - tcy;
    float tx = (cx + 0.3 + 0.4 * hs) * colW + sin(tcy * 0.03 + hs * TAU) * 5.0 + (ht - 0.5) * 4.0;
    float tr = (1.0 + 1.6 * ht) * (1.0 - clamp(behind / 190.0, 0.0, 1.0));
    float tpres = slide * step(ht, 0.6) * step(12.0, behind) * step(behind, 190.0);
    float4 td = lensDrop(p, float2(tx, tcy), max(tr, 0.4), 1.0, tpres);
    if (td.w > 0.0 && length(td.xy) < length(best.xy)) best = td;

    float len = length(best.xy);
    float r = best.z;
    float cover = (1.0 - smoothstep(1.0 - 1.2 / r, 1.0, len)) * best.w;
    // contact shadow just below the drop, outside it
    float2 dsh = best.xy - float2(0.0, 0.32);
    float shade = (1.0 - smoothstep(0.9, 1.35, length(dsh))) * best.w * (1.0 - cover);
    lin *= 1.0 - 0.10 * shade;
    if (cover > 0.001) {
      float2 rq = (p - best.xy * r * 7.0) / size;
      float3 seen = labToLinear(mesh(rq));
      float rim = smoothstep(0.62, 1.0, len);
      float top = clamp(-best.y, 0.0, 1.0);
      float low = clamp(best.y, 0.0, 1.0);
      float3 glass = seen * (1.1 - 0.42 * rim * top) + float3(0.10 * smoothstep(0.35, 0.95, len) * low);
      float2 hl = best.xy - float2(-0.34, -0.44);
      glass += float3(0.75 * exp(-dot(hl, hl) * 38.0));
      lin = mix(lin, glass, cover);
    }
  }

  float3 rgb = encode(lin);
  float2 px = p * dpr;
  float n = ign(px) + ign(px + float2(41.0, 17.0)) - 1.0;   // triangular dither, +-1 LSB
  float g = h21(floor(px)) - 0.5;                             // static grain
  rgb += n / 255.0 + g * 0.022;
  return half4(half3(rgb), 1.0);
}
`;
