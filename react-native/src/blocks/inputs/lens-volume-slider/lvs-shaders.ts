// Lens Volume Slider: SkSL. One runtime effect draws the whole screen: the photograph (drifting, zooming with the
// volume), the smoked track, the drop of clear glass that refracts the photo (a cylinder along its body, an inverting
// ball lens at its end), the glyph and numeral drawn *under* the glass, the caustic the lens throws ahead, and the mute
// marble. All maths is in sRGB; `toOut` re-encodes the result once at the end (iOS reads runtime-effect output as
// Display P3, which oversaturates sRGB values otherwise).

import { Skia } from '@shopify/react-native-skia';

const GLASS = `
uniform shader photo;    // the sharp plate, canvas coordinates (pt)
uniform shader blurred;  // the same plate blurred once (sigma 14 pt), canvas coordinates
uniform shader atlas;    // white glyph parts and digits, atlas pixels

uniform float uP3;
uniform float uPx;       // one device pixel in pt
uniform float2 uRes;     // canvas size (pt)
uniform float4 uPlate;   // drift x, drift y, scale, photo dim 0..1
uniform float uExpo;     // cap-hit exposure lift 0..1
uniform float4 uTrack;   // x, y, w, h (w includes the cap stretch)
uniform float4 uFill;    // left x, right x, height, end thinning 0..1
uniform float4 uLens;    // the ball's inverted magnification at its centre, lift 0..1, squash, marble 0..1
uniform float4 uDimple;  // x, strength, radius, unused
uniform float4 uLight;   // sweep (-1 off), glint 0..1, flash 0..1, caustic strength
uniform float4 uGlyph;   // arc1, arc2, slash trims 0..1, atlas px per pt
uniform float4 uGlyphBox;// x, y, size, glyph row y in the atlas (pt)
uniform float4 uArc;     // arc centre x, y in glyph-box pt, arc half-span (rad), unused
uniform float4 uSlash;   // slash start x, y, end x, y in glyph-box pt
uniform float4 uRoll;    // units, tens, hundreds roll values, digit count (float)
uniform float4 uNum;     // left x, centre y, cell w, cell h (pt)
uniform float uShow;     // the numeral in its window: 1 shown, 0 slid out below (mute)
uniform float4 uRoom;    // the marble's view of the room: centre x, y, radius (pt), unused

const float BODY_BEND = 8.0;
const float CHROMA = 2.2;
const float FRINGE = 2.75;

float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(half3 s) {
  if (uP3 < 0.5) return s;
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float luma(half3 c) { return dot(float3(c), float3(0.2126, 0.7152, 0.0722)); }
half3 over(half3 base, half3 c, float a) { return mix(base, c, clamp(a, 0.0, 1.0)); }

// ---- the photograph: one transform for the sharp and blurred copies, so every lens stays registered ----
float2 plateXY(float2 xy) {
  float2 c = uRes * 0.5;
  return (xy - c) / uPlate.z + c - uPlate.xy;
}
half3 grade(half3 c) {
  float d = uPlate.w;
  float y = luma(c);
  half3 o = mix(half3(y), c, 1.0 - 0.2 * d);
  return o * (1.0 - 0.2 * d) * (1.0 + 0.06 * uExpo);
}
half3 sharp(float2 xy) { return grade(photo.eval(plateXY(xy)).rgb); }
half3 soft(float2 xy) { return grade(blurred.eval(plateXY(xy)).rgb); }
// the marble sees the room at full exposure, so it stays the brightest thing while the photo dims
half3 room(float2 xy) { return photo.eval(plateXY(xy)).rgb; }

// ---- shapes ----
float sdRound(float2 p, float2 c, float2 b, float r) {
  float2 q = abs(p - c) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// the drop: a horizontal capsule from a (radius r0) to a + (L, 0) (radius r1): iq's uneven capsule laid along x
float sdDrop(float2 p, float2 a, float r0, float r1, float L) {
  if (L < 0.001) return length(p - a) - r0;
  float2 q = float2(abs(p.y - a.y), p.x - a.x);
  float b = (r0 - r1) / L;
  float aa = sqrt(max(1.0 - b * b, 0.0));
  float k = dot(q, float2(-b, aa));
  if (k < 0.0) return length(q) - r0;
  if (k > aa * L) return length(q - float2(0.0, L)) - r1;
  return dot(q, float2(aa, b)) - r0;
}

// ---- the UI under the glass: glyph parts and odometer digits from the atlas (white on clear) ----
float cellA(float2 pt) { return atlas.eval(pt * uGlyph.w).a; }

float digitA(float k, float lx, float ly, float leading) {
  if (k < -0.5 || (leading > 0.5 && k < 0.5)) return 0.0;
  float idx = mod(k, 10.0);
  return cellA(float2(idx * uNum.z + lx, ly));
}
// one odometer slot: R's integer part leaves upwards while the next digit comes up from below
float slotA(float2 p, float slotX, float R, float leading) {
  float DW = uNum.z;
  float DH = uNum.w;
  float lx = p.x - slotX;
  float ly = p.y - (uNum.y - 0.5 * DH);
  float lyd = ly - (1.0 - uShow) * DH;
  if (lx < 0.0 || lx > DW || ly < 0.0 || ly > DH) return 0.0;
  float k = floor(R + 0.0001);
  float f = clamp(R - k, 0.0, 1.0);
  float a = 0.0;
  float y1 = lyd + f * DH;
  if (y1 < DH) a = max(a, digitA(k, lx, y1, leading));
  float y2 = lyd - (1.0 - f) * DH;
  if (y2 > 0.0) a = max(a, digitA(k + 1.0, lx, y2, leading));
  // the window's top and bottom edges are soft by 3 pt, like digits passing under a bezel
  return a * smoothstep(0.0, 3.0, ly) * smoothstep(0.0, 3.0, DH - ly);
}

float uiA(float2 p) {
  float GS = uGlyphBox.z;
  float a = 0.0;
  float2 g = p - uGlyphBox.xy;
  if (g.x > -1.0 && g.y > -1.0 && g.x < GS + 1.0 && g.y < GS + 1.0) {
    float2 gc = clamp(g, float2(0.25), float2(GS - 0.25));
    float rowY = uGlyphBox.w;
    a = cellA(gc + float2(0.0, rowY));
    float2 ar = g - uArc.xy;
    float th = abs(atan(ar.y, ar.x));
    float m1 = uGlyph.x < 0.001 ? 0.0 : 1.0 - smoothstep(uGlyph.x * uArc.z - 0.05, uGlyph.x * uArc.z + 0.05, th);
    float m2 = uGlyph.y < 0.001 ? 0.0 : 1.0 - smoothstep(uGlyph.y * uArc.z - 0.05, uGlyph.y * uArc.z + 0.05, th);
    a = max(a, cellA(gc + float2(GS, rowY)) * m1);
    a = max(a, cellA(gc + float2(2.0 * GS, rowY)) * m2);
    float2 sd = uSlash.zw - uSlash.xy;
    float pr = dot(g - uSlash.xy, sd) / max(dot(sd, sd), 0.001);
    float ms = uGlyph.z < 0.001 ? 0.0 : 1.0 - smoothstep(uGlyph.z - 0.03, uGlyph.z + 0.03, pr);
    a = max(a, cellA(gc + float2(3.0 * GS, rowY)) * ms);
  }
  float DW = uNum.z;
  float n = uRoll.w;
  if (p.x > uNum.x - 2.0 * DW && p.x < uNum.x + 3.0 * DW && abs(p.y - uNum.y) < 0.5 * uNum.w) {
    a = max(a, slotA(p, uNum.x + (n - 1.0) * DW, uRoll.x, 0.0));
    a = max(a, slotA(p, uNum.x + (n - 2.0) * DW, uRoll.y, 1.0));
    a = max(a, slotA(p, uNum.x + (n - 3.0) * DW, uRoll.z, 1.0));
  }
  return a;
}

half4 main(float2 xy) {
  float px = uPx;
  float cy = uTrack.y + 0.5 * uTrack.w;
  half3 col = sharp(xy);
  // nothing but the photo away from the control
  if (abs(xy.y - cy) > 0.5 * uTrack.w + 40.0) return half4(toOut(col), 1.0);

  // ---- the track ----
  float2 tc = float2(uTrack.x + 0.5 * uTrack.z, cy);
  float2 tb = float2(0.5 * uTrack.z, 0.5 * uTrack.w);
  float tr = 0.5 * uTrack.w;
  float dT = sdRound(xy, tc, tb, tr);
  // its contact shadow on the photo: (0, 6) blur 18 α 0.22 and (0, 1) blur 2 α 0.25
  if (dT > 0.0) {
    float s1 = 0.22 * (1.0 - smoothstep(-9.0, 18.0, sdRound(xy - float2(0.0, 6.0), tc, tb, tr)));
    float s2 = 0.25 * (1.0 - smoothstep(-1.0, 2.0, sdRound(xy - float2(0.0, 1.0), tc, tb, tr)));
    col *= half(1.0 - max(s1, s2));
  }

  // ---- the drop's geometry (squashed about its centre into the marble) ----
  float x0 = uFill.x;
  float x1 = uFill.y;
  float hh = 0.5 * uFill.z;
  float r0 = hh;
  float r1 = hh * (1.0 - uFill.w);
  float2 a = float2(x0 + r0, cy);
  float L = max(x1 - r1 - a.x, 0.0);
  float sq = uLens.z;
  float2 dc = float2(0.5 * (x0 + x1), cy);
  float2 p = dc + (xy - dc) / float2(sq, 2.0 - sq);
  float d = sdDrop(p, a, r0, r1, L);
  float lift = uLens.y;
  float marble = uLens.w;
  float2 bc = float2(a.x + L, cy); // centre of the lens cap

  if (dT < 1.5) {
    // frosted, smoked glass: the blurred photo bent 3 pt inwards at the rim, tinted, with 4 % grain
    float2 nT = normalize(float2(sdRound(xy + float2(0.5, 0.0), tc, tb, tr) - sdRound(xy - float2(0.5, 0.0), tc, tb, tr),
                                 sdRound(xy + float2(0.0, 0.5), tc, tb, tr) - sdRound(xy - float2(0.0, 0.5), tc, tb, tr)) + 1e-5);
    float edge = 1.0 - smoothstep(0.0, 12.0, -dT);
    half3 tcol = soft(xy + nT * 3.0 * edge);
    tcol = mix(tcol, half3(0.235, 0.204, 0.192), 0.5);
    tcol += half3(1.0) * half(0.05 * (1.0 - smoothstep(0.0, uTrack.w, xy.y - uTrack.y)));
    tcol += half3((hash2(floor(xy * 3.0)) - 0.5) * 0.04);

    if (d > 0.0) {
      // the drop floats above the smoked glass: its shadow inside the track
      float2 so = mix(float2(2.0, 3.0), float2(3.0, 7.0), lift);
      float sb = mix(8.0, 16.0, lift);
      float sa = mix(0.28, 0.40, lift);
      float ds = sdDrop(dc + (xy - so - dc) / float2(sq, 2.0 - sq), a, r0, r1, L);
      tcol *= half(1.0 - sa * (1.0 - smoothstep(-0.5 * sb, sb, ds)));
      // the caustic: the light the lens bends lands ahead of it as a crescent (brighter over the pale wall); in the
      // marble it focuses into a hot dot 34 pt to its right
      float lum = luma(soft(bc));
      float ca = mix(0.10, 0.34, smoothstep(0.12, 0.45, lum)) * uLight.w;
      float2 rel = xy - bc;
      float rc = r1 + 8.0 + 13.0;
      float th = atan(rel.y, rel.x);
      float cres = exp(-pow((length(rel) - rc) / 6.5, 2.0)) * exp(-pow(th / 0.5, 2.0)) * ca;
      float dotc = 0.5 * exp(-pow(length(xy - float2(bc.x + r1 * sq + 34.0, cy)) / 6.0, 2.0));
      float c = mix(cres, dotc, marble) * smoothstep(0.0, 4.0, d);
      tcol += half3(1.0, 0.97, 0.90) * half(c);
    }

    // the rim: 1.5 pt, lit #9EABB1 α 0.7 on top fading to white α 0.12 below; the cap hit flashes it and spills a 2 pt
    // bright arc onto the right cap
    float rimBand = 1.0 - smoothstep(0.75 - px, 0.75 + px, abs(dT + 0.75));
    float vy = clamp((xy.y - uTrack.y) / uTrack.w, 0.0, 1.0);
    half3 rimC = mix(half3(0.620, 0.671, 0.694), half3(1.0), vy);
    float rimA = mix(0.7, 0.12, vy) + 0.3 * uLight.z;
    tcol = over(tcol, rimC, rimBand * rimA);
    float spill = (1.0 - smoothstep(1.0 - px, 1.0 + px, abs(dT + 2.5))) * smoothstep(tc.x + tb.x - tr, tc.x + tb.x, xy.x);
    tcol = over(tcol, half3(1.0, 0.98, 0.94), spill * 0.85 * uLight.z);

    col = mix(col, tcol, 1.0 - smoothstep(-px, px, dT));
  }

  // ---- the drop ----
  if (d < 1.5) {
    float ax = clamp(p.x, a.x, a.x + L);
    float rad = L > 0.001 ? mix(r0, r1, (ax - a.x) / L) : r0;
    float2 q = p - float2(ax, cy);
    float rho = min(length(q) / rad, 1.0);
    float h = sqrt(max(1.0 - rho * rho, 0.0));
    // the end of the drop is a glass ball (the last 88 pt: the cap's whole disc), fused into the rod of the body.
    // Inside the disc the photo is inverted, ×1.8 at its centre (×2.1 lifted), easing to ×0.85 at the rim (a gentle
    // barrel, so the patch never smears into rings); the body only bends the photo in y
    float2 qc = p - bc;
    float rc = length(qc) / r1;
    float left = smoothstep(0.0, -0.3, qc.x / r1);
    float lensK = 1.0 - smoothstep(0.9, 0.98, rc) * left;
    float rcc = min(rc, 1.0);
    float M = mix(uLens.x, 0.85, rcc * rcc);
    float2 offCap = -qc * (1.0 + 1.0 / M);
    float2 offBody = float2(0.0, -(q.y / rad) * BODY_BEND);
    float2 off = mix(offBody, offCap, lensK);
    // the dimple under the finger: a concave dent that minifies ×0.8, merging into the lens towards the end
    float2 sdm = xy - float2(uDimple.x, cy);
    float dm = length(sdm);
    float wd = uDimple.y * (1.0 - smoothstep(0.55 * uDimple.z, uDimple.z, dm)) * (1.0 - lensK);
    off += sdm * 0.25 * wd;
    // dispersion: red and blue split along the radius, from 1.2 pt mid-ball to 2.5 pt at its rim, so every
    // hard edge the ball inverts carries a fringe
    float2 dirB = q / max(length(q), 0.001);
    float2 dirC = qc / max(length(qc), 0.001);
    float2 ch = mix(dirB * rho * rho * 0.5, dirC * smoothstep(0.0, 0.4, rcc) * (0.5 + 0.5 * rcc * rcc), lensK) * CHROMA;
    half3 refr = half3(sharp(xy + off + ch).r, sharp(xy + off).g, sharp(xy + off - ch).b);
    // where the rod meets the ball the glass is thickest and bends light out of the eye: a dark meniscus ring with a
    // thin lit line on its inner edge (it hides the jump between the upright and the inverted image)
    float ring = left * smoothstep(0.88, 0.935, rc) * (1.0 - smoothstep(0.975, 1.01, rc));
    refr = mix(refr, refr * 0.3 + half3(0.015), ring * 0.7);
    float lit = clamp(dot(dirC, float2(-0.7071, -0.7071)) * 0.5 + 0.5, 0.0, 1.0);
    float line = left * exp(-pow((rc - 0.85) * r1 / 0.9, 2.0));
    refr += half3(1.0) * half(line * mix(0.06, 0.32, lit));
    // the mute marble is a crystal ball: the whole room minified and upside-down inside it (wall below, speaker
    // above), with a little barrel distortion and a stronger split at its rim
    if (marble > 0.001) {
      float2 u = q / rad;
      float2 ro = uRoom.xy - u * uRoom.z * (0.62 + 0.38 * rho * rho);
      float2 rc = u * CHROMA * 1.6 * rho * rho;
      half3 ball = half3(room(ro + rc).r, room(ro).g, room(ro - rc).b);
      refr = mix(refr, ball, marble);
    }

    // the glass: dark neutral in the body (lighter at its left end), nearly clear over the lens
    half3 tint = mix(half3(0.310, 0.325, 0.341), half3(0.243, 0.243, 0.247), smoothstep(x0, x0 + 70.0, xy.x));
    float ta = mix(0.58, 0.14, lensK) * (1.0 - 0.75 * marble);
    half3 g = mix(refr, tint, ta) * half(1.0 + 0.12 * marble);

    // the glyph and numeral sit under the glass, just below its surface: the lens magnifies them upright, ×1.25
    float2 offUI = -qc * 0.2 * lensK;
    float ui = uiA(xy + offUI);
    // a soft shade under the ink, so the glyph reads over the bright room in the marble
    float2 hp = xy + offUI + float2(0.0, 0.8);
    float halo = 0.25 * (uiA(hp + float2(1.6, 0.0)) + uiA(hp - float2(1.6, 0.0)) + uiA(hp + float2(0.0, 1.6)) + uiA(hp - float2(0.0, 1.6)));
    g = mix(g, g * 0.35, halo * 0.2 * (1.0 - marble));
    // in the marble the glyph turns to dark ink: an "off" mark inside a bright crystal ball
    g = over(g, mix(half3(0.875), half3(0.13, 0.135, 0.14), half(marble)), ui);

    // light: Fresnel near the edge, the rim lit from the upper left, the chromatic fringe round the lens
    float fres = 0.04 + 0.96 * pow(1.0 - h, 5.0);
    g += half3(1.0) * half(fres * 0.30);
    // the ball has volume: a little darker towards its rim, the light it gathers pooled inside the lower-right rim,
    // and the key light (upper left) reflected as a soft window on its upper-left shoulder
    float2 uc = qc / r1;
    g *= half(1.0 - 0.16 * lensK * smoothstep(0.55, 1.0, rcc));
    float pool = exp(-pow((rcc - 0.8) / 0.11, 2.0)) * smoothstep(0.1, 0.95, dot(dirC, float2(0.7071, 0.7071)));
    g += half3(1.0, 0.98, 0.93) * half(lensK * pool * 0.16 * (1.0 + 0.6 * marble));
    float2 sp = (uc - float2(-0.38, -0.46)) / float2(0.26, 0.15);
    float spec = exp(-dot(sp, sp)) * lensK;
    g += half3(1.0) * half(spec * (0.20 + 0.12 * lift));
    float2 nD = normalize(float2(sdDrop(p + float2(0.5, 0.0), a, r0, r1, L) - sdDrop(p - float2(0.5, 0.0), a, r0, r1, L),
                                 sdDrop(p + float2(0.0, 0.5), a, r0, r1, L) - sdDrop(p - float2(0.0, 0.5), a, r0, r1, L)) + 1e-5);
    float fb = clamp((-d - 1.5) / FRINGE, 0.0, 1.0);
    float inF = step(-d, 1.5 + FRINGE) * step(1.5, -d);
    float fk = lensK * (0.55 + 0.45 * smoothstep(-0.3, 0.9, nD.x)) * inF;
    g = over(g, half3(0.349, 0.463, 0.569), fk * (1.0 - smoothstep(0.4, 0.62, fb)));
    g = over(g, half3(0.690, 0.541, 0.416), fk * 0.40 * smoothstep(0.45, 0.7, fb) * (1.0 - smoothstep(0.85, 1.0, fb)));
    float light = clamp(dot(nD, float2(-0.7071, -0.7071)) * 0.5 + 0.5, 0.0, 1.0);
    float rimA = min(mix(0.2, 0.75, light) + 0.3 * lift, 1.0);
    float rimBand = 1.0 - smoothstep(0.75 - px, 0.75 + px, abs(d + 0.75));
    g = over(g, half3(0.788, 0.824, 0.839), rimBand * rimA);
    // highlights on the top arc: the release sweep and the resting glint that crawls along it
    float top = smoothstep(0.1, 0.6, -nD.y) * (1.0 - smoothstep(0.75 + px, 3.0, abs(d + 0.75)));
    float span0 = x0 + r0 * 0.6;
    float span1 = x1 - r1 * 0.6;
    float sw = uLight.x < 0.0 ? 0.0 : 0.4 * exp(-pow((xy.x - mix(span0, span1, uLight.x)) / 22.0, 2.0));
    float glFade = smoothstep(0.0, 0.08, min(uLight.y, 1.0 - uLight.y));
    float gl = 0.22 * glFade * exp(-pow((xy.x - mix(span0, span1, uLight.y)) / 10.0, 2.0));
    g += half3(1.0) * half(top * (sw + gl));

    col = mix(col, g, 1.0 - smoothstep(-px, px, d));
  }
  return half4(toOut(col), 1.0);
}
`;

const make = (src: string, name: string) => {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error(`Lens Volume Slider: the ${name} shader failed to compile`);
  return e;
};

export const GLASS_FX = make(GLASS, 'glass');
