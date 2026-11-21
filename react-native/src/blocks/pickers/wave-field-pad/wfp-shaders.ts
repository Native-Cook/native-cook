// Wave Field Pad runtime shaders target the tgfx sRGB canvas. The grid uses a finite-difference
// Jacobian to keep its line width even as the puck deforms the surface.
const NOISE = `
float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
`;

/**
 * The page: green-black sheet with a green lift behind the wave, the ember's bleed on the side the puck is on and a
 * 3 % grain so the greens survive H.264.
 */
const PAGE = `${NOISE}
uniform float2 uLift;   // green lift centre (pt)
uniform float2 uAmb;    // ember bleed centre (pt)
uniform float uAmbA;
uniform float uPadTop;
uniform float uPadBottom;

half4 main(float2 xy) {
  float3 top = float3(0.102, 0.122, 0.106);   // #1A1F1B
  float3 low = float3(0.082, 0.071, 0.086);   // #151216
  float3 col = mix(top, low, smoothstep(uPadTop - 40.0, uPadBottom, xy.y));
  // green lift: #22302A at 50 %, r 220
  float2 dl = (xy - uLift) / float2(240.0, 200.0);
  col = mix(col, float3(0.133, 0.188, 0.165), 0.5 * exp(-dot(dl, dl) * 2.2));
  // the ember's bleed: #271C0E pulled warmer at its core
  float2 da = (xy - uAmb) / float2(95.0, 150.0);
  float g = exp(-dot(da, da) * 1.6) * uAmbA;
  col = mix(col, float3(0.200, 0.122, 0.047), g * 0.55);
  // keep the darkest value at #131115
  col = max(col, float3(0.075, 0.067, 0.082));
  float n = (hash2(floor(xy * 2.0)) - 0.5) * 0.03;
  col += n;
  return half4(col, 1.0);
}
`;

/**
 * The pad: an engraved, recessed violet-grey plate that behaves like a membrane. The puck's weight dents it (a
 * spring-lagged Gaussian push), ring ripples cross it with a lit crest and reflect once off the walls, the grooves
 * catch the ember on the edge that faces it, and on a hold its sand (conserved, never faded) contracts from broad
 * smears onto the nodal lines of a Chladni figure of mode (n, m). Drawn in page points; the rect is the pad plus a
 * bleed for its shadow and the wall's bulge.
 */
const PAD = `${NOISE}
uniform float4 uPad;     // x, y, w, h (pt)
uniform float uRad;
uniform float uPx;       // one device pixel (pt)
uniform float2 uPuck;    // pad-local (pt)
uniform float uPuckR;    // drawn radius (pt)
uniform float2 uDent;    // lagged dent centre (pad-local pt)
uniform float uDepth;    // dent depth (pt)
uniform float4 uRip0;    // x, y, age (s), amplitude (pt)
uniform float4 uRip1;
uniform float uSettle;
uniform float2 uMode;    // n, m
uniform float uRing;     // payoff pulse ring 0..1
uniform float2 uWall;    // bulge (pt)
uniform float2 uScatter; // grain shift (pt)
uniform float2 uVel;     // puck velocity (pt/s): the sand's wake
uniform float uJit;      // shimmer step (30 Hz)
uniform float uCells;    // grid cells per side
uniform float uSmallR;
uniform float uLight;    // strength of the ember light (halo)
uniform float uAdv;      // cap of the sand's gather onto the lines (pt; 0 under reduced motion)

const float SIG = 70.0;
const float PI = 3.14159265;

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// radial push (xy) and crest height (z) of one ring ripple and its four reflections
float3 ripple(float2 p, float4 r, float4 pad) {
  float3 acc = float3(0.0);
  if (r.w <= 0.001) return acc;
  float rr = 520.0 * r.z;
  for (int i = 0; i < 5; i++) {
    float2 o = r.xy;
    float a = r.w;
    if (i == 1) { o.x = -o.x; a *= 0.55; }
    if (i == 2) { o.x = 2.0 * pad.z - o.x; a *= 0.55; }
    if (i == 3) { o.y = -o.y; a *= 0.55; }
    if (i == 4) { o.y = 2.0 * pad.w - o.y; a *= 0.55; }
    // a mirror image that falls on the source (a ripple born on a wall) is the same ring: skip it
    if (i > 0 && length(o - r.xy) < 1.0) a = 0.0;
    float2 d = p - o;
    float l = length(d) + 0.001;
    float x = (l - rr) / 12.0;
    float g = a * exp(-x * x);
    acc += float3(d / l * g, g);
  }
  return acc;
}

// screen point (pad-local) -> sheet point: the dent pushes the sheet away from its centre, peak = depth at r = SIG
float2 warp(float2 p, float2 dent, float depth, float4 rip0, float4 rip1, float4 pad) {
  float2 d = p - dent;
  float e = exp(-dot(d, d) / (2.0 * SIG * SIG));
  float2 q = p - d * (depth * 1.65 / SIG) * e;
  q -= ripple(p, rip0, pad).xy + ripple(p, rip1, pad).xy;
  return q;
}

float cov(float d, float halfW, float px) { return clamp((halfW - d) / px + 0.5, 0.0, 1.0); }

// the Chladni field at sheet point q: F and its gradient (per pt)
float3 chladni(float2 q, float4 pad, float2 mode) {
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
// falls from 1 to 0.3 the sand slides onto the lines. Monotone in d and zero on the line: no fold, no seam, and at
// k = 1 (rest) the grain is untouched. Returns the lookup offset (away from the line).
float2 gather(float2 q, float4 pad, float2 mode, float settle, float advCap) {
  float3 c = chladni(q, pad, mode);
  float g = max(length(c.yz), 0.012);
  float d = abs(c.x) / g;
  float k = mix(1.0, 0.3, settle);
  // near an antinode the slope's direction spins: hold the sand still there (it is outside every band anyway)
  float sh = min(d * (1.0 / k - 1.0), advCap) * smoothstep(0.006, 0.022, length(c.yz));
  return (c.x / (abs(c.x) + 0.0001)) * (c.yz / g) * sh;
}

// a ripple born on a wall makes the sand along that wall jump 6 pt inward and back (lookup offset)
float2 wallHop(float2 p, float4 r, float4 pad) {
  if (r.w <= 0.001) return float2(0.0);
  float2 wn = float2(0.0);
  float wd = 1.0e4;
  if (abs(r.x - pad.z) < 1.0) { wn = float2(1.0, 0.0); wd = pad.z - p.x; }
  else if (r.x < 1.0) { wn = float2(-1.0, 0.0); wd = p.x; }
  else if (r.y < 1.0) { wn = float2(0.0, -1.0); wd = p.y; }
  else if (abs(r.y - pad.w) < 1.0) { wn = float2(0.0, 1.0); wd = pad.w - p.y; }
  float hk = sin(PI * clamp(r.z / 0.32, 0.0, 1.0));
  return wn * 6.0 * hk * exp(-max(wd, 0.0) / 26.0) * exp(-pow((dot(p - r.xy, float2(wn.y, wn.x))) / 110.0, 2.0));
}

half4 main(float2 xy) {
  float W = uPad.z;
  float H = uPad.w;
  float2 p = xy - uPad.xy;
  float2 mid = float2(W, H) * 0.5;

  // ---- the plate's outline, bulged where the puck pushes past a wall ----
  float wy = (p.y - uPuck.y) / 56.0;
  float wx = (p.x - uPuck.x) / 56.0;
  float gy = exp(-wy * wy);
  float gx = exp(-wx * wx);
  float2 pb = p;
  pb.x -= (p.x > W * 0.5 ? max(uWall.x, 0.0) : min(uWall.x, 0.0)) * gy;
  pb.y -= (p.y > H * 0.5 ? max(uWall.y, 0.0) : min(uWall.y, 0.0)) * gx;
  float sd = sdBox(pb - mid, mid, uRad);

  // the plate's drop shadow on the page (outside only)
  float sdS = sdBox(pb - mid - float2(0.0, 8.0), mid, uRad);
  float shA = 0.42 * exp(-max(sdS, 0.0) / 9.0);
  if (sd > 0.5 * uPx) {
    return half4(0.0, 0.0, 0.0, shA);
  }

  // ---- the sheet ----
  float e = 0.5;
  float2 q = warp(p, uDent, uDepth, uRip0, uRip1, uPad);
  float2 qx = warp(p + float2(e, 0.0), uDent, uDepth, uRip0, uRip1, uPad);
  float2 qy = warp(p + float2(0.0, e), uDent, uDepth, uRip0, uRip1, uPad);
  float2 gqx = float2(qx.x - q.x, qy.x - q.x) / e; // grad of q.x
  float2 gqy = float2(qx.y - q.y, qy.y - q.y) / e; // grad of q.y
  float det = gqx.x * gqy.y - gqx.y * gqy.x;
  float crest = clamp((ripple(p, uRip0, uPad).z + ripple(p, uRip1, uPad).z) / 10.0, 0.0, 1.0);

  // light: the puck, 14 pt above the plate
  float2 toL = uPuck - p;
  float dl = sqrt(dot(toL, toL) + 14.0 * 14.0);
  float lit = 1.0 / (1.0 + (dl / 110.0) * (dl / 110.0));
  float near = 1.0 - smoothstep(60.0, 220.0, dl);

  float3 base = mix(float3(0.137, 0.133, 0.153), float3(0.133, 0.125, 0.145), 1.0 - smoothstep(0.0, 200.0, length(p - mid)));
  base += 0.012 * (1.0 - p.y / H);
  float3 ember = float3(1.0, 0.698, 0.353);   // #FFB25A
  float3 warm = float3(0.941, 0.502, 0.118);  // #F0801E
  // the warm pool under the puck: #F0801E 22 %, r 160
  float3 col = mix(base, warm, 0.22 * exp(-dot(toL, toL) / (2.0 * 80.0 * 80.0)) * uLight);
  // the membrane's shading: stretched sheet catches more light (det < 1), compressed less
  col += (ember * 0.4 * lit + float3(0.05)) * clamp((1.0 - det) * 1.3, -0.25, 0.4);
  // the ripples' lit crest
  col = mix(col, mix(float3(1.0), ember, lit), 0.3 * crest);

  // ---- engraved grid: 1.5 pt grooves, α 0.10, lit to 0.6 on the side facing the ember within ~110 pt ----
  float pitchX = W / uCells;
  float pitchY = H / uCells;
  float gx0 = q.x / pitchX;
  float kx = floor(gx0 + 0.5);
  float sx = (gx0 - kx) * pitchX / max(length(gqx), 0.2);
  float lineX = kx * pitchX;
  float gy0 = q.y / pitchY;
  float ky = floor(gy0 + 0.5);
  float sy = (gy0 - ky) * pitchY / max(length(gqy), 0.2);
  float lineY = ky * pitchY;
  float inX = step(0.5, kx) * step(kx, uCells - 0.5);
  float inY = step(0.5, ky) * step(ky, uCells - 0.5);
  float cX = cov(abs(sx), 0.75, uPx) * inX;
  float cY = cov(abs(sy), 0.75, uPx) * inY;
  // the groove's shadow: 0.75 pt beside it on the side away from the ember
  float shX = cov(abs(sx + sign(uPuck.x - lineX) * 1.1), 0.4, uPx) * inX;
  float shY = cov(abs(sy + sign(uPuck.y - lineY) * 1.1), 0.4, uPx) * inY;
  col = mix(col, col * 0.6, max(shX, shY) * 0.8);
  float nlX = 0.35 + 0.65 * abs(uPuck.x - lineX) / dl;
  float nlY = 0.35 + 0.65 * abs(uPuck.y - lineY) / dl;
  float aX = cX * (0.10 + 0.5 * lit * nlX * uLight);
  float aY = cY * (0.10 + 0.5 * lit * nlY * uLight);
  float3 litCol = mix(float3(1.0), ember, smoothstep(0.05, 0.3, lit * uLight));
  col = mix(col, litCol, clamp(max(aX, aY), 0.0, 1.0));

  // ---- the arcs (screen space: they stay on the puck's rim at every position), 2 pt α 0.28 ----
  float2 cL = float2(0.0, H * 0.5);
  float2 cR = float2(W, H * 0.5);
  float rL = length(uPuck - cL) - uPuckR;
  float rR = length(uPuck - cR) - uPuckR;
  float dLp = length(p - cL);
  float dRp = length(p - cR);
  float arcs = max(cov(abs(dLp - rL), 1.0, uPx), cov(abs(dRp - rR), 1.0, uPx)) * 0.28;
  float small = max(cov(abs(dLp - uSmallR), 1.0, uPx), cov(abs(dRp - uSmallR), 1.0, uPx)) * 0.24;
  float inside = max(step(dLp, uSmallR), step(dRp, uSmallR)) * 0.03;
  col = mix(col, float3(1.0), inside);
  col = mix(col, mix(float3(1.0), ember, lit * uLight), clamp(max(arcs, small) * (1.0 + lit * 1.5), 0.0, 1.0));

  // ---- sand: conserved, gathered from broad smears onto the nodal lines (never faded in) ----
  float2 rel = p - uPuck;
  float dP = length(rel) + 0.001;
  // the pulse ring: r 0 -> 420 pt in 500 ms, 44 pt wide; grains hop 3 pt as it passes
  float ringR = 420.0 * (1.0 - pow(1.0 - uRing, 1.6));
  float rk = (dP - ringR) / 22.0;
  float ring = uRing > 0.0 ? exp(-rk * rk) * min(uRing / 0.06, 1.0) * (1.0 - uRing * uRing) : 0.0;
  float edge = uRing > 0.0 ? exp(-pow((dP - ringR - 14.0) / 5.0, 2.0)) * min(uRing / 0.06, 1.0) * (1.0 - uRing * uRing) : 0.0;
  col = mix(col, ember, 0.34 * ring);
  col = mix(col, float3(1.0, 0.86, 0.62), 0.38 * edge);
  // a fast puck pulls a wake of sand behind it
  float sp = length(uVel);
  float wake = 0.0;
  if (sp > 60.0) {
    float2 dir = uVel / sp;
    float along = -dot(rel, dir);
    float perp = dot(rel, float2(-dir.y, dir.x));
    float L = clamp(sp * 0.09, 0.0, 130.0);
    wake = smoothstep(uPuckR * 0.5, uPuckR + 6.0, along) * (1.0 - smoothstep(L * 0.4, L + uPuckR, along)) * exp(-perp * perp / (2.0 * 13.0 * 13.0)) * min(sp / 700.0, 1.0);
  }
  float2 adv = gather(q, uPad, uMode, uSettle, uAdv);
  float2 hop = wallHop(p, uRip0, uPad) + wallHop(p, uRip1, uPad);
  float2 look = q + uScatter + adv + hop - rel / dP * 3.0 * ring;
  // band in |F| at the grain's resting place: half-width bw, density 0.3 / bw per 3.4 pt cell (the same amount of
  // sand at every settle); the gather compresses it 3.3x more on screen
  float Fl = chladni(look - uScatter, uPad, uMode).x;
  float bw = mix(0.9, uAdv > 0.0 ? 0.32 : 0.1, uSettle);
  float dens = clamp((0.3 / bw) * (1.0 - smoothstep(0.0, bw, abs(Fl))) + 0.5 * wake, 0.0, 1.0);
  // the grain's screen shape: undo the sheet's and the gather's stretch (J = (I + A) Jq) so every grain is a 2 pt dot
  float2 ax = (gather(q + float2(0.5, 0.0), uPad, uMode, uSettle, uAdv) - adv) / 0.5;
  float2 ay = (gather(q + float2(0.0, 0.5), uPad, uMode, uSettle, uAdv) - adv) / 0.5;
  float2 c1 = float2(gqx.x, gqy.x);
  float2 c2 = float2(gqx.y, gqy.y);
  float2 j1 = c1 + ax * c1.x + ay * c1.y;
  float2 j2 = c2 + ax * c2.x + ay * c2.y;
  float jd = j1.x * j2.y - j2.x * j1.y;
  float aF = abs(chladni(q, uPad, uMode).x);
  float shimK = smoothstep(0.25, 0.8, aF) * step(0.001, uAdv);
  // a 3.4 pt cell: a 2 pt grain gathered 3.3x spans 3.3 pt of lookup, so the 3 x 3 neighbours always hold it whole
  float cell = 3.4;
  float2 b0 = floor(look / cell);
  float speck = 0.0;
  float tone = 0.0;
  for (int iy = -1; iy <= 1; iy++) {
    for (int ix = -1; ix <= 1; ix++) {
      float2 ci = b0 + float2(float(ix), float(iy));
      float h = hash2(ci);
      if (h < dens) {
        float2 jit = float2(hash2(ci + 17.0), hash2(ci + 41.0)) * 0.6 + 0.2;
        // shimmer: grains away from the lines jitter +-1.5 pt at 30 Hz
        float2 shim = (float2(hash2(ci + uJit * 1.37), hash2(ci + uJit * 2.11 + 5.0)) - 0.5) * 3.0 * shimK;
        float2 ol = (ci + jit) * cell + shim - look;
        float2 os = abs(jd) > 0.02 ? float2(j2.y * ol.x - j2.x * ol.y, -j1.y * ol.x + j1.x * ol.y) / jd : ol;
        float c = cov(length(os), 1.0, uPx);
        if (c > speck) { speck = c; tone = hash2(ci + 7.0); }
      }
    }
  }
  float3 gold = float3(1.0, 0.788, 0.471);   // #FFC978
  float3 cream = float3(0.965, 0.886, 0.745); // #F6E2BE
  // lines near the ember turn gold as the plate sings
  float3 sand = mix(cream, gold, near * (0.3 + 0.7 * uSettle));
  sand = mix(sand, ember, ring * 0.6);
  float hopLit = clamp(length(hop) / 6.0, 0.0, 1.0);
  float bright = (mix(0.62, 0.98, near) + 0.6 * ring + 0.35 * hopLit) * (0.8 + 0.2 * tone);
  // where the sand piles up (grains per screen area > 1) the pile itself catches the light
  float pile = 0.13 * smoothstep(1.2, 3.0, dens * min(abs(jd), 3.4)) * step(0.001, uAdv);
  col = mix(col, sand, clamp(pile * bright, 0.0, 1.0));
  col = mix(col, sand, clamp(speck * bright, 0.0, 1.0));

  // ---- recess: 2 pt inner shadow on top, 1 pt inner highlight at the bottom ----
  float sdT = sdBox(pb - mid - float2(0.0, 2.0), mid, uRad);
  float sdB = sdBox(pb - mid + float2(0.0, 1.0), mid, uRad);
  col = mix(col, float3(0.0), 0.4 * smoothstep(-1.5, 0.5, sdT));
  col = mix(col, float3(1.0), 0.08 * smoothstep(-0.5, 0.3, sdB) * step(H * 0.5, p.y));

  float a = clamp(0.5 - sd / uPx, 0.0, 1.0);
  return half4(col * a, a + shA * (1.0 - a));
}
`;

export const PAGE_FX = PAGE;
export const PAD_FX = PAD;
