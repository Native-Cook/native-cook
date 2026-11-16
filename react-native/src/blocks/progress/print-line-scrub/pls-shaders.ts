// Print Line Scrub — one image compositor, shared by the stage and capsule lens.
// Two registered RGBA renders preserve the sculpt's detail; only the material cut,
// heat, section and subtle projective tilt are evaluated per pixel. No ray marching.
import { ref } from './pls-theme';

/** sRGB hex → `half3(r, g, b)` literal. */
function c3(hex: string) {
  const v = hex.replace('#', '');
  const c = [0, 2, 4].map((i) => (parseInt(v.slice(i, i + 2), 16) / 255).toFixed(4));
  return `float3(${c[0]}, ${c[1]}, ${c[2]})`;
}

const C = ref.color;

export const SCENE_SKSL = `
uniform shader uSolid;
uniform shader uGhost;
uniform float2 uO;
uniform float uU;
uniform float2 uCam;
uniform float4 uFigure;
uniform float2 uImage;
uniform float4 uSpanA;
uniform float4 uSpanB;
uniform float4 uSpanC;
uniform float uYaw;
uniform float uH;
uniform float uBand;
uniform float uHeat;
uniform float uBead;
uniform float uTint;
uniform float uPx;
uniform float uP3;
uniform float uGlass;
uniform float4 uLens;
uniform float uLensR;
uniform float uLensK;
uniform float uRefr;
const float3 GROUND = ${c3(C.ground)};
const float3 GROUND_HI = ${c3(C.groundHi)};
const float3 GRID = ${c3(C.grid)};
const float3 ROSE = ${c3(C.rose)};
const float3 FIL = ${c3(C.filament)};
const float3 FIL_SH = ${c3(C.filamentShadow)};
const float3 FIL_LI = ${c3(C.filamentLight)};
const float3 HEAT_HOT = ${c3(C.heatHot)};
const float3 HEAT_MID = ${c3(C.heatMid)};
const float3 SHADOW = ${c3(C.glassShadow)};
float3 toP3(float3 c, float on) {
  if (on < 0.5) return c;
  float3 lin = pow(max(c, float3(0.0)), float3(2.2));
  float3 p3 = float3(
    dot(lin, float3(0.8225, 0.1774, 0.0)),
    dot(lin, float3(0.0332, 0.9669, 0.0)),
    dot(lin, float3(0.0171, 0.0724, 0.9108)));
  return mix(c, pow(clamp(p3, 0.0, 1.0), float3(1.0 / 2.2)), on);
}

// distance to the nearest edge of a hexagonal lattice (unit cell), 0.5 at the cell's centre
float hexEdge(float2 uv) {
  float2 r = float2(1.0, 1.7320508);
  float2 h = r * 0.5;
  float2 a = mod(uv, r) - h;
  float2 b = mod(uv - h, r) - h;
  float2 g = dot(a, a) < dot(b, b) ? a : b;
  float2 p = abs(g);
  float c = max(dot(p, float2(0.5, 0.8660254)), p.x);
  return 0.5 - c;
}

// the ground: a faint perspective grid, the lighter floor, the rose bounce and the baked contact shadow
float3 ground(float2 xy, float2 o, float u, float tint) {
  float fy = clamp((xy.y - 150.0) / 700.0, 0.0, 1.0);
  float3 col = mix(GROUND, GROUND_HI, smoothstep(0.35, 1.0, fy));
  // grid: horizontals spread toward the viewer, verticals fan out from a point far above
  float gy = (xy.y - 120.0);
  float hz = gy / (14.0 + gy * 0.035);
  float hl = abs(fract(hz) - 0.5);
  float2 vp = float2(o.x, -1600.0);
  float ang = (xy.x - vp.x) / (xy.y - vp.y);
  float vl = abs(fract(ang * 7.0 * 1.0) - 0.5);
  float aaH = 0.035 + 0.0;
  float line = 1.0 - smoothstep(0.012, 0.012 + aaH * 0.5, min(hl, vl * (xy.y - vp.y) * 0.0042));
  col = mix(col, GRID, line * 0.55 * smoothstep(40.0, 260.0, xy.y));
  // bounce: rose light on the floor round the base
  float2 q = (xy - float2(o.x, o.y + 22.0)) / float2(u * 0.62, u * 0.2);
  float rose = exp(-dot(q, q) * 1.1);
  col = mix(col, ROSE, rose * (0.42 + 0.5 * tint));
  // baked contact shadow under the base
  float2 s = (xy - float2(o.x + 6.0, o.y + 36.0)) / float2(u * 0.37, u * 0.1);
  col = mix(col, SHADOW * 0.55, 0.3 * exp(-dot(s, s) * 1.6));
  return col;
}

float sdRound(float2 p, float2 hb, float r) {
  float2 q = abs(p) - hb + r;
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}


// Each disjoint silhouette span gets its own ellipse: the leg/arm gaps stay open.
// span = source-UV left/right; the transform is shared with both texture lookups.
float4 section(float2 xy, float2 span, float cutY, float row, float4 fig,
    float tilt, float yaw, float heat, float bead, float unit) {
  if (abs(xy.y - cutY) > 13.0 * unit) return float4(0.0);
  float scale = cos(yaw) * (1.0 + tilt * (row - 0.5) * 0.2);
  float cx = fig.x + (0.5 + ((span.x + span.y) * 0.5 - 0.5) * scale
      + tilt * (row - 0.5) * 0.22) * fig.z;
  float rx = (span.y - span.x) * 0.5 * fig.z * scale;
  if (rx < 1.0 || row <= 0.0 || row >= 1.0) return float4(0.0);
  float ry = min(12.0 * unit, rx * 0.24);
  float2 q = (xy - float2(cx, cutY)) / float2(rx, max(ry, 0.7));
  float r = length(q);
  float aa = 0.7 / max(ry, 0.7);
  float alpha = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, r);
  alpha *= 1.0 - smoothstep(rx - 0.4, rx + 0.4, abs(xy.x - cx));
  if (alpha < 0.001) return float4(0.0);
  float depth = max(0.0, (1.0 - r) * rx);
  float strand1 = 1.0 - smoothstep(0.5, 1.1, abs(depth - 1.0 * unit));
  float strand2 = 1.0 - smoothstep(0.5, 1.1, abs(depth - 2.8 * unit));
  float skin = max(strand1, strand2);
  float2 cell = float2(xy.x - cx, (xy.y - cutY) * rx / max(ry, 0.7)) / (9.0 * unit);
  float edgeTop = hexEdge(cell);
  float edgeFloor = hexEdge(cell + float2(0.2, 0.45));
  float wall = 1.0 - smoothstep(0.055, 0.14, edgeTop);
  float side = 1.0 - smoothstep(0.055, 0.14, edgeFloor);
  float3 cells = mix(FIL_SH * 0.38, FIL * 0.65, side);
  cells = mix(cells, FIL_LI * 0.9, wall);
  float3 col = mix(mix(FIL_SH * 0.5, FIL_LI, skin), cells,
      smoothstep(3.5 * unit, 4.5 * unit, depth));
  float hot = exp(-depth / ((4.5 + 4.0 * heat) * unit));
  col = mix(col, mix(HEAT_MID, HEAT_HOT, hot * hot), hot * (0.72 + 0.25 * heat));
  float angle = atan(q.y, q.x);
  float d = atan(sin(angle - bead), cos(angle - bead));
  col += float3(1.0, 0.86, 0.55) * exp(-d * d / 0.015) * exp(-depth / (2.0 * unit)) * 0.8;
  return float4(col, alpha);
}

half4 main(float2 xyIn) {
  float2 xy = xyIn;
  if (uGlass > 0.5) {
    // the capsule is a pill-shaped lens: magnify a little about its centre, bend the lookup along the rim's normal
    float2 pc = xyIn - uLens.xy;
    float sd = sdRound(pc, uLens.zw, uLensR);
    float2 e = float2(1.5, 0.0);
    float2 gr = float2(sdRound(pc + e.xy, uLens.zw, uLensR) - sdRound(pc - e.xy, uLens.zw, uLensR),
      sdRound(pc + e.yx, uLens.zw, uLensR) - sdRound(pc - e.yx, uLens.zw, uLensR));
    float2 nrm = gr / max(length(gr), 0.0001);
    // the bend is confined to the outer rim and grows like a lens' edge (cubic), so the middle stays clear and the rim reads
    // as a refracting edge, not as a smear
    float rimK = 1.0 - smoothstep(0.0, 11.0, -sd);
    xy = uLens.xy + pc / uLensK - nrm * uRefr * rimK * rimK * rimK;
  }

  float3 col = ground(xy, uO, uU, uTint);
  float cutY = uO.y - uU * uCam.x * uH;
  float row = (cutY - uFigure.y) / uFigure.w;
  float tilt = sin(uYaw);
  // A row-preserving homography: trapezoid perspective + shear, with exactly
  // the same source row at the cut for the textures and the section lookup.
  float2 uv = (xy - uFigure.xy) / uFigure.zw;
  float scale = cos(uYaw) * (1.0 + tilt * (uv.y - 0.5) * 0.2);
  uv.x = (uv.x - 0.5 - tilt * (uv.y - 0.5) * 0.22) / scale + 0.5;
  if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
    float below = smoothstep(-0.45, 0.45, xy.y - cutY);
    // Only one material texture read away from the single antialiased seam.
    half4 tex = xy.y < cutY ? uGhost.eval(uv * uImage) : uSolid.eval(uv * uImage);
    float alpha = float(tex.a);
    float3 rgb = float3(tex.rgb) / max(alpha, 0.001);
    float lightShift = 1.0 + tilt * (0.5 - uv.x) * 0.9;
    rgb *= lightShift;
    if (below > 0.5) {
      float layerVis = 1.0 - smoothstep(0.38, 0.7, 480.0 / (uFigure.w * uPx));
      rgb *= 1.0 - 0.045 * layerVis * (0.5 + 0.5 * cos(uv.y * 480.0 * 6.2831853));
      float k = clamp(1.0 - (xy.y - cutY) / max(uBand, 1.0), 0.0, 1.0);
      rgb = mix(rgb, mix(HEAT_MID, HEAT_HOT, k * k), k * k * (0.3 + 0.45 * uHeat));
    } else {
      alpha *= 0.72;
    }
    col = mix(col, rgb, alpha);
  }
  // Six bounded ellipses at most, no texture-space or ray-marching loops.
  float unit = uU * uCam.x / 500.0;
  float4 cap = section(xy, uSpanA.xy, cutY, row, uFigure, tilt, uYaw, uHeat, uBead, unit);
  float4 b = section(xy, uSpanA.zw, cutY, row, uFigure, tilt, uYaw, uHeat, uBead, unit);
  if (b.a > cap.a) cap = b;
  b = section(xy, uSpanB.xy, cutY, row, uFigure, tilt, uYaw, uHeat, uBead, unit);
  if (b.a > cap.a) cap = b;
  b = section(xy, uSpanB.zw, cutY, row, uFigure, tilt, uYaw, uHeat, uBead, unit);
  if (b.a > cap.a) cap = b;
  b = section(xy, uSpanC.xy, cutY, row, uFigure, tilt, uYaw, uHeat, uBead, unit);
  if (b.a > cap.a) cap = b;
  b = section(xy, uSpanC.zw, cutY, row, uFigure, tilt, uYaw, uHeat, uBead, unit);
  if (b.a > cap.a) cap = b;
  col = mix(col, cap.rgb, cap.a);
  return half4(half3(toP3(col, uP3)), 1.0);
}
`;

// BAR_SKSL — the progress bar made of matter: solid yellow at the left that breaks into dots toward the pill, then loose
// dots beyond it. Dots have a lag proportional to their index, so a fast scrub fans them into a tail.
//   uOrigin float2 screen position of the bar box's top-left; uS screen pt per canvas pt (the shader works in canvas pt)
//   uSize  float2  bar box (canvas pt)
//   uFill  float   x of the pill's centre in the box
//   uOver  float   dots' overshoot past the pill, pt (signed)
//   uTail  float   signed lag of the loose dots, pt (positive: they trail to the left)
//   uFlash float   0..1 the arrival flash running left to right (−1 off)
//   uP3    float   1: convert sRGB → Display P3
export const BAR_SKSL = `
uniform float2 uOrigin;
uniform float uS;
uniform float2 uSize;
uniform float uFill;
uniform float uOver;
uniform float uTail;
uniform float uFlash;
uniform float uP3;
const float3 YELLOW = ${c3(C.yellow)};
const float3 FLASH = ${c3(C.yellowFlash)};

float h21(float2 p) {
  float3 q = fract(float3(p.x, p.y, p.x) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float sdRoundBar(float2 p, float2 hb, float r) {
  float2 q = abs(p) - hb + r;
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}
float3 toP3(float3 c, float on) {
  if (on < 0.5) return c;
  float3 lin = pow(max(c, float3(0.0)), float3(2.2));
  float3 p3 = float3(
    dot(lin, float3(0.8225, 0.1774, 0.0)),
    dot(lin, float3(0.0332, 0.9669, 0.0)),
    dot(lin, float3(0.0171, 0.0724, 0.9108)));
  return mix(c, pow(clamp(p3, 0.0, 1.0), float3(1.0 / 2.2)), on);
}

half4 main(float2 xyAbs) {
  float2 xy = (xyAbs - uOrigin) / uS;
  float pitch = ${ref.dots.pitch.toFixed(1)};
  float row = floor(xy.y / pitch);
  float off = 0.5 * pitch * mod(row, 2.0);
  float cy = (row + 0.5) * pitch;
  float fill = uFill + uOver;
  float cov = 0.0;
  float flashK = 0.0;
  float base = floor((xy.x - off) / pitch);
  for (int k = -5; k <= 5; k++) {
    float ci = base + float(k);
    float cx0 = (ci + 0.5) * pitch + off;
    float hh = h21(float2(ci, row));
    float h2 = h21(float2(ci + 17.0, row + 3.0));
    // dense at the left, looser toward the pill, loose beyond it
    float t1 = smoothstep(fill - 118.0, fill + 6.0, cx0);
    float tail = 1.0 - smoothstep(fill + 4.0, fill + 4.0 + ${ref.dots.loose.toFixed(1)}, cx0);
    float keep = mix(1.0, 0.34, t1) * tail;
    float present = 1.0 - smoothstep(keep - 0.1, keep, hh * 0.9);    // a dot shrinks away and grows back, none pops; keep 1 = all present
    float w = smoothstep(fill - 130.0, fill + 20.0, cx0);
    float cx = cx0 - uTail * w * (0.3 + 0.7 * h2);
    // dots near the bar's rounded end shrink away toward the edge (a halftone fade), instead of being cut whole or half-transparent
    float inside = 1.0 - smoothstep(-5.0, 0.0, sdRoundBar(float2(cx0, cy) - uSize * 0.5, uSize * 0.5, uSize.y * 0.5));
    float r = mix(3.5, ${ref.dots.r.toFixed(2)}, t1) * (0.82 + 0.36 * h2) * (0.35 + 0.65 * present) * (0.3 + 0.7 * inside);
    float d = length(xy - float2(cx, cy));
    float aa = 0.34 / max(uS, 0.5);                                    // about one device pixel at 3x
    float c = (1.0 - smoothstep(r - aa, r + aa, d)) * present * smoothstep(0.0, 0.25, inside);
    if (c > cov) {
      cov = c;
      float fp = mix(-40.0, fill + 6.0, clamp(uFlash, 0.0, 1.0));
      flashK = uFlash >= 0.0 ? (1.0 - smoothstep(0.0, 34.0, abs(cx0 - fp))) * (1.0 - clamp(abs(uFlash - 0.5) * 0.0, 0.0, 1.0)) : 0.0;
    }
  }
  float3 col = mix(YELLOW, FLASH, flashK);
  col = toP3(col, uP3);
  return half4(half3(col * cov), half(cov));
}
`;
