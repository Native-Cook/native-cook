#include <metal_stdlib>
using namespace metal;

float wfp_hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }


[[ stitchable ]] half4 waveFieldPadPage(float2 position, half4 currentColor, float viewScale, float2 uLift, float2 uAmb, float uAmbA, float uPadTop, float uPadBottom) {
  float2 xy = position / viewScale;
  float3 top = float3(0.102f, 0.122f, 0.106f);   // #1A1F1B
  float3 low = float3(0.082f, 0.071f, 0.086f);   // #151216
  float3 col = mix(top, low, smoothstep(uPadTop - 40.0f, uPadBottom, xy.y));
  // green lift: #22302A at 50 %, r 220
  float2 dl = (xy - uLift) / float2(240.0f, 200.0f);
  col = mix(col, float3(0.133f, 0.188f, 0.165f), 0.5f * exp(-dot(dl, dl) * 2.2f));
  // the ember's bleed: #271C0E pulled warmer at its core
  float2 da = (xy - uAmb) / float2(95.0f, 150.0f);
  float g = exp(-dot(da, da) * 1.6f) * uAmbA;
  col = mix(col, float3(0.200f, 0.122f, 0.047f), g * 0.55f);
  // keep the darkest value at #131115
  col = max(col, float3(0.075f, 0.067f, 0.082f));
  float n = (wfp_hash2(floor(xy * 2.0f)) - 0.5f) * 0.03f;
  col += n;
  return half4(float4(col, 1.0f));
}



constant float SIG = 70.0f;
constant float PI = 3.14159265f;

float wfp_sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0f)) + min(max(q.x, q.y), 0.0f) - r;
}

// radial push (xy) and crest height (z) of one ring ripple and its four reflections
float3 wfp_ripple(float2 p, float4 r, float4 pad) {
  float3 acc = float3(0.0f);
  if (r.w <= 0.001f) return acc;
  float rr = 520.0f * r.z;
  for (int i = 0; i < 5; i++) {
    float2 o = r.xy;
    float a = r.w;
    if (i == 1) { o.x = -o.x; a *= 0.55f; }
    if (i == 2) { o.x = 2.0f * pad.z - o.x; a *= 0.55f; }
    if (i == 3) { o.y = -o.y; a *= 0.55f; }
    if (i == 4) { o.y = 2.0f * pad.w - o.y; a *= 0.55f; }
    // a mirror image that falls on the source (a ripple born on a wall) is the same ring: skip it
    if (i > 0 && length(o - r.xy) < 1.0f) a = 0.0f;
    float2 d = p - o;
    float l = length(d) + 0.001f;
    float x = (l - rr) / 12.0f;
    float g = a * exp(-x * x);
    acc += float3(d / l * g, g);
  }
  return acc;
}

// screen point (pad-local) -> sheet point: the dent pushes the sheet away from its centre, peak = depth at r = SIG
float2 wfp_warp(float2 p, float2 dent, float depth, float4 rip0, float4 rip1, float4 pad) {
  float2 d = p - dent;
  float e = exp(-dot(d, d) / (2.0f * SIG * SIG));
  float2 q = p - d * (depth * 1.65f / SIG) * e;
  q -= wfp_ripple(p, rip0, pad).xy + wfp_ripple(p, rip1, pad).xy;
  return q;
}

float wfp_cov(float d, float halfW, float px) { return clamp((halfW - d) / px + 0.5f, 0.0f, 1.0f); }

// the Chladni field at sheet point q: F and its gradient (per pt)
float3 wfp_chladni(float2 q, float4 pad, float2 mode) {
  float2 uv = q / pad.zw;
  float n = mode.x;
  float m = mode.y;
  float cnu = cos(n * PI * uv.x);
  float cmv = cos(m * PI * uv.y);
  float cmu = cos(m * PI * uv.x);
  float cnv = cos(n * PI * uv.y);
  float F = cnu * cmv - cmu * cnv;
  float dFu = -n * PI * sin(n * PI * uv.x) * cmv + m * PI * sin(m * PI * uv.x) * cnv;
  float dFv = -m * PI * cnu * sin(m * PI * uv.y) + n * PI * cmu * sin(n * PI * uv.y);
  return float3(F, dFu / pad.z, dFv / pad.w);
}

// gather: a grain seen d pt from its nodal line was lying d / k pt away (the extra shift capped at uAdv), so as k
// falls from 1 to 0.3f the sand slides onto the lines. Monotone in d and zero on the line: no fold, no seam, and at
// k = 1 (rest) the grain is untouched. Returns the lookup offset (away from the line).
float2 wfp_gather(float2 q, float4 pad, float2 mode, float settle, float advCap) {
  float3 c = wfp_chladni(q, pad, mode);
  float g = max(length(c.yz), 0.012f);
  float d = abs(c.x) / g;
  float k = mix(1.0f, 0.3f, settle);
  // near an antinode the slope's direction spins: hold the sand still there (it is outside every band anyway)
  float sh = min(d * (1.0f / k - 1.0f), advCap) * smoothstep(0.006f, 0.022f, length(c.yz));
  return (c.x / (abs(c.x) + 0.0001f)) * (c.yz / g) * sh;
}

// a ripple born on a wall makes the sand along that wall jump 6 pt inward and back (lookup offset)
float2 wfp_wallHop(float2 p, float4 r, float4 pad) {
  if (r.w <= 0.001f) return float2(0.0f);
  float2 wn = float2(0.0f);
  float wd = 1.0e4f;
  if (abs(r.x - pad.z) < 1.0f) { wn = float2(1.0f, 0.0f); wd = pad.z - p.x; }
  else if (r.x < 1.0f) { wn = float2(-1.0f, 0.0f); wd = p.x; }
  else if (r.y < 1.0f) { wn = float2(0.0f, -1.0f); wd = p.y; }
  else if (abs(r.y - pad.w) < 1.0f) { wn = float2(0.0f, 1.0f); wd = pad.w - p.y; }
  float hk = sin(PI * clamp(r.z / 0.32f, 0.0f, 1.0f));
  return wn * 6.0f * hk * exp(-max(wd, 0.0f) / 26.0f) * exp(-pow((dot(p - r.xy, float2(wn.y, wn.x))) / 110.0f, 2.0f));
}

[[ stitchable ]] half4 waveFieldPadSurface(float2 position, half4 currentColor, float viewScale, float4 uPad, float uRad, float uPx, float2 uPuck, float uPuckR, float2 uDent, float uDepth, float4 uRip0, float4 uRip1, float uSettle, float2 uMode, float uRing, float2 uWall, float2 uScatter, float2 uVel, float uJit, float uCells, float uSmallR, float uLight, float uAdv) {
  float2 xy = position / viewScale;
  float W = uPad.z;
  float H = uPad.w;
  float2 p = xy - uPad.xy;
  float2 mid = float2(W, H) * 0.5f;

  // ---- the plate's outline, bulged where the puck pushes past a wall ----
  float wy = (p.y - uPuck.y) / 56.0f;
  float wx = (p.x - uPuck.x) / 56.0f;
  float gy = exp(-wy * wy);
  float gx = exp(-wx * wx);
  float2 pb = p;
  pb.x -= (p.x > W * 0.5f ? max(uWall.x, 0.0f) : min(uWall.x, 0.0f)) * gy;
  pb.y -= (p.y > H * 0.5f ? max(uWall.y, 0.0f) : min(uWall.y, 0.0f)) * gx;
  float sd = wfp_sdBox(pb - mid, mid, uRad);

  // the plate's drop shadow on the page (outside only)
  float sdS = wfp_sdBox(pb - mid - float2(0.0f, 8.0f), mid, uRad);
  float shA = 0.42f * exp(-max(sdS, 0.0f) / 9.0f);
  if (sd > 0.5f * uPx) {
    return half4(float4(0.0f, 0.0f, 0.0f, shA));
  }

  // ---- the sheet ----
  float e = 0.5f;
  float2 q = wfp_warp(p, uDent, uDepth, uRip0, uRip1, uPad);
  float2 qx = wfp_warp(p + float2(e, 0.0f), uDent, uDepth, uRip0, uRip1, uPad);
  float2 qy = wfp_warp(p + float2(0.0f, e), uDent, uDepth, uRip0, uRip1, uPad);
  float2 gqx = float2(qx.x - q.x, qy.x - q.x) / e; // grad of q.x
  float2 gqy = float2(qx.y - q.y, qy.y - q.y) / e; // grad of q.y
  float det = gqx.x * gqy.y - gqx.y * gqy.x;
  float crest = clamp((wfp_ripple(p, uRip0, uPad).z + wfp_ripple(p, uRip1, uPad).z) / 10.0f, 0.0f, 1.0f);

  // light: the puck, 14 pt above the plate
  float2 toL = uPuck - p;
  float dl = sqrt(dot(toL, toL) + 14.0f * 14.0f);
  float lit = 1.0f / (1.0f + (dl / 110.0f) * (dl / 110.0f));
  float near = 1.0f - smoothstep(60.0f, 220.0f, dl);

  float3 base = mix(float3(0.137f, 0.133f, 0.153f), float3(0.133f, 0.125f, 0.145f), 1.0f - smoothstep(0.0f, 200.0f, length(p - mid)));
  base += 0.012f * (1.0f - p.y / H);
  float3 ember = float3(1.0f, 0.698f, 0.353f);   // #FFB25A
  float3 warm = float3(0.941f, 0.502f, 0.118f);  // #F0801E
  // the warm pool under the puck: #F0801E 22 %, r 160
  float3 col = mix(base, warm, 0.22f * exp(-dot(toL, toL) / (2.0f * 80.0f * 80.0f)) * uLight);
  // the membrane's shading: stretched sheet catches more light (det < 1), compressed less
  col += (ember * 0.4f * lit + float3(0.05f)) * clamp((1.0f - det) * 1.3f, -0.25f, 0.4f);
  // the ripples' lit crest
  col = mix(col, mix(float3(1.0f), ember, lit), 0.3f * crest);

  // ---- engraved grid: 1.5f pt grooves, α 0.10f, lit to 0.6f on the side facing the ember within ~110 pt ----
  float pitchX = W / uCells;
  float pitchY = H / uCells;
  float gx0 = q.x / pitchX;
  float kx = floor(gx0 + 0.5f);
  float sx = (gx0 - kx) * pitchX / max(length(gqx), 0.2f);
  float lineX = kx * pitchX;
  float gy0 = q.y / pitchY;
  float ky = floor(gy0 + 0.5f);
  float sy = (gy0 - ky) * pitchY / max(length(gqy), 0.2f);
  float lineY = ky * pitchY;
  float inX = step(0.5f, kx) * step(kx, uCells - 0.5f);
  float inY = step(0.5f, ky) * step(ky, uCells - 0.5f);
  float cX = wfp_cov(abs(sx), 0.75f, uPx) * inX;
  float cY = wfp_cov(abs(sy), 0.75f, uPx) * inY;
  // the groove's shadow: 0.75f pt beside it on the side away from the ember
  float shX = wfp_cov(abs(sx + sign(uPuck.x - lineX) * 1.1f), 0.4f, uPx) * inX;
  float shY = wfp_cov(abs(sy + sign(uPuck.y - lineY) * 1.1f), 0.4f, uPx) * inY;
  col = mix(col, col * 0.6f, max(shX, shY) * 0.8f);
  float nlX = 0.35f + 0.65f * abs(uPuck.x - lineX) / dl;
  float nlY = 0.35f + 0.65f * abs(uPuck.y - lineY) / dl;
  float aX = cX * (0.10f + 0.5f * lit * nlX * uLight);
  float aY = cY * (0.10f + 0.5f * lit * nlY * uLight);
  float3 litCol = mix(float3(1.0f), ember, smoothstep(0.05f, 0.3f, lit * uLight));
  col = mix(col, litCol, clamp(max(aX, aY), 0.0f, 1.0f));

  // ---- the arcs (screen space: they stay on the puck's rim at every position), 2 pt α 0.28f ----
  float2 cL = float2(0.0f, H * 0.5f);
  float2 cR = float2(W, H * 0.5f);
  float rL = length(uPuck - cL) - uPuckR;
  float rR = length(uPuck - cR) - uPuckR;
  float dLp = length(p - cL);
  float dRp = length(p - cR);
  float arcs = max(wfp_cov(abs(dLp - rL), 1.0f, uPx), wfp_cov(abs(dRp - rR), 1.0f, uPx)) * 0.28f;
  float small = max(wfp_cov(abs(dLp - uSmallR), 1.0f, uPx), wfp_cov(abs(dRp - uSmallR), 1.0f, uPx)) * 0.24f;
  float inside = max(step(dLp, uSmallR), step(dRp, uSmallR)) * 0.03f;
  col = mix(col, float3(1.0f), inside);
  col = mix(col, mix(float3(1.0f), ember, lit * uLight), clamp(max(arcs, small) * (1.0f + lit * 1.5f), 0.0f, 1.0f));

  // ---- sand: conserved, gathered from broad smears onto the nodal lines (never faded in) ----
  float2 rel = p - uPuck;
  float dP = length(rel) + 0.001f;
  // the pulse ring: r 0 -> 420 pt in 500 ms, 44 pt wide; grains hop 3 pt as it passes
  float ringR = 420.0f * (1.0f - pow(1.0f - uRing, 1.6f));
  float rk = (dP - ringR) / 22.0f;
  float ring = uRing > 0.0f ? exp(-rk * rk) * min(uRing / 0.06f, 1.0f) * (1.0f - uRing * uRing) : 0.0f;
  float edge = uRing > 0.0f ? exp(-pow((dP - ringR - 14.0f) / 5.0f, 2.0f)) * min(uRing / 0.06f, 1.0f) * (1.0f - uRing * uRing) : 0.0f;
  col = mix(col, ember, 0.34f * ring);
  col = mix(col, float3(1.0f, 0.86f, 0.62f), 0.38f * edge);
  // a fast puck pulls a wake of sand behind it
  float sp = length(uVel);
  float wake = 0.0f;
  if (sp > 60.0f) {
    float2 dir = uVel / sp;
    float along = -dot(rel, dir);
    float perp = dot(rel, float2(-dir.y, dir.x));
    float L = clamp(sp * 0.09f, 0.0f, 130.0f);
    wake = smoothstep(uPuckR * 0.5f, uPuckR + 6.0f, along) * (1.0f - smoothstep(L * 0.4f, L + uPuckR, along)) * exp(-perp * perp / (2.0f * 13.0f * 13.0f)) * min(sp / 700.0f, 1.0f);
  }
  float2 adv = wfp_gather(q, uPad, uMode, uSettle, uAdv);
  float2 hop = wfp_wallHop(p, uRip0, uPad) + wfp_wallHop(p, uRip1, uPad);
  float2 look = q + uScatter + adv + hop - rel / dP * 3.0f * ring;
  // band in |F| at the grain's resting place: half-width bw, density 0.3f / bw per 3.4f pt cell (the same amount of
  // sand at every settle); the gather compresses it 3.3x more on screen
  float Fl = wfp_chladni(look - uScatter, uPad, uMode).x;
  float bw = mix(0.9f, uAdv > 0.0f ? 0.32f : 0.1f, uSettle);
  float dens = clamp((0.3f / bw) * (1.0f - smoothstep(0.0f, bw, abs(Fl))) + 0.5f * wake, 0.0f, 1.0f);
  // the grain's screen shape: undo the sheet's and the gather's stretch (J = (I + A) Jq) so every grain is a 2 pt dot
  float2 ax = (wfp_gather(q + float2(0.5f, 0.0f), uPad, uMode, uSettle, uAdv) - adv) / 0.5f;
  float2 ay = (wfp_gather(q + float2(0.0f, 0.5f), uPad, uMode, uSettle, uAdv) - adv) / 0.5f;
  float2 c1 = float2(gqx.x, gqy.x);
  float2 c2 = float2(gqx.y, gqy.y);
  float2 j1 = c1 + ax * c1.x + ay * c1.y;
  float2 j2 = c2 + ax * c2.x + ay * c2.y;
  float jd = j1.x * j2.y - j2.x * j1.y;
  float aF = abs(wfp_chladni(q, uPad, uMode).x);
  float shimK = smoothstep(0.25f, 0.8f, aF) * step(0.001f, uAdv);
  // a 3.4f pt cell: a 2 pt grain gathered 3.3x spans 3.3f pt of lookup, so the 3 x 3 neighbours always hold it whole
  float cell = 3.4f;
  float2 b0 = floor(look / cell);
  float speck = 0.0f;
  float tone = 0.0f;
  for (int iy = -1; iy <= 1; iy++) {
    for (int ix = -1; ix <= 1; ix++) {
      float2 ci = b0 + float2(float(ix), float(iy));
      float h = wfp_hash2(ci);
      if (h < dens) {
        float2 jit = float2(wfp_hash2(ci + 17.0f), wfp_hash2(ci + 41.0f)) * 0.6f + 0.2f;
        // shimmer: grains away from the lines jitter +-1.5f pt at 30 Hz
        float2 shim = (float2(wfp_hash2(ci + uJit * 1.37f), wfp_hash2(ci + uJit * 2.11f + 5.0f)) - 0.5f) * 3.0f * shimK;
        float2 ol = (ci + jit) * cell + shim - look;
        float2 os = abs(jd) > 0.02f ? float2(j2.y * ol.x - j2.x * ol.y, -j1.y * ol.x + j1.x * ol.y) / jd : ol;
        float c = wfp_cov(length(os), 1.0f, uPx);
        if (c > speck) { speck = c; tone = wfp_hash2(ci + 7.0f); }
      }
    }
  }
  float3 gold = float3(1.0f, 0.788f, 0.471f);   // #FFC978
  float3 cream = float3(0.965f, 0.886f, 0.745f); // #F6E2BE
  // lines near the ember turn gold as the plate sings
  float3 sand = mix(cream, gold, near * (0.3f + 0.7f * uSettle));
  sand = mix(sand, ember, ring * 0.6f);
  float hopLit = clamp(length(hop) / 6.0f, 0.0f, 1.0f);
  float bright = (mix(0.62f, 0.98f, near) + 0.6f * ring + 0.35f * hopLit) * (0.8f + 0.2f * tone);
  // where the sand piles up (grains per screen area > 1) the pile itself catches the light
  float pile = 0.13f * smoothstep(1.2f, 3.0f, dens * min(abs(jd), 3.4f)) * step(0.001f, uAdv);
  col = mix(col, sand, clamp(pile * bright, 0.0f, 1.0f));
  col = mix(col, sand, clamp(speck * bright, 0.0f, 1.0f));

  // ---- recess: 2 pt inner shadow on top, 1 pt inner highlight at the bottom ----
  float sdT = wfp_sdBox(pb - mid - float2(0.0f, 2.0f), mid, uRad);
  float sdB = wfp_sdBox(pb - mid + float2(0.0f, 1.0f), mid, uRad);
  col = mix(col, float3(0.0f), 0.4f * smoothstep(-1.5f, 0.5f, sdT));
  col = mix(col, float3(1.0f), 0.08f * smoothstep(-0.5f, 0.3f, sdB) * step(H * 0.5f, p.y));

  float a = clamp(0.5f - sd / uPx, 0.0f, 1.0f);
  return half4(float4(col * a, a + shA * (1.0f - a)));
}
