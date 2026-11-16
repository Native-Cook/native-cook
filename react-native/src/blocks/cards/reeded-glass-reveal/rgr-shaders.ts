// Reeded Glass Reveal — the two SkSL passes. Written so Metal [[stitchable]] and AGSL are line-for-line
// translations: no SkSL-only intrinsics, uniform arrays indexed only inside a constant loop, no pow() of a
// negative base, no GPU-dependent hash for anything that has to match between stacks (per-flute randoms come in
// as uSeed).
//
// GLASS (the whole screen): 22 flutes of reeded glass over the portrait. Canvas points in, colour out.
//   portrait / portraitSoft  children: the photo, and the same photo defocused once (sigma 2.6 x 2.0 pt, no
//                            geometry baked in). The lens refracts the soft one; depth 0 shows the sharp one.
//   uImg      float4  image rect x, y, w, h (pt)
//   uEdge     float[23] flute edges, pt: not a ruler's pitch (15.5 to 21 pt); the seams also wander with y
//   uSeed     float[22] per-flute random 0..1 (glint phase, lens size)
//   uDepth    float[22] flute depth: 1 = deep reeded glass (the pin), 0 = flat clear glass; springs overshoot to
//                       about -0.054 (concave) and 1.054; the leading flute of the front swells to 1.25 first
//   uMag      float   mean depth 0..1 -> magnification 1 + uMagAmt * uMag about uMagC
//   uKappa    float   how far a flute reads across the scene, in half-widths. A flute is a wide-angle cylinder
//                     lens: scene x = c + hw * kappa * tan(tau u) / tan(tau), so the middle of a flute is magnified,
//                     its flanks are squeezed into slivers, and neighbours repeat each other's features
//   uTau      float   profile steepness (flank / crest magnification is about 3.4 at 1.0)
//   uSlice    float   per-flute lateral offset of the view, half-widths (the sliced look)
//   uSpread   float   sideways spread of a deep flute about the face
//   uSag      float   0..0.3 vertical stretch of the scene at the seams, about the face's centre line (0: none)
//   uBlur     float   pt, screen-space softness of the lens image (five binomial taps)
//   uEdgeK    float   how much further the outer flutes read (1 + uEdgeK * (distance from the face / half width)^2)
//   uChroma   float   pt, red / blue split where the depth changes quickly from flute to flute
//   uGlintW   float   pt, half width of a glint; uSeam pt, half width of the dark seam hairline
//   uLight    float   -1..1 where the key light sits: glints slide sideways across every flute with it
//   uSpec     float   glint strength 0..1
//   uRun      float   0..1 progress of the soft glint that runs down three flutes at rest; <0: off
//   uRunRib   float3  the three flutes
//   uS        float   layout scale (screen width / 402)
//   uFlatMix  float   -1: live optics; 0..1: Reduce Motion opacity crossfade of fixed deep/flat images
//
// CAPSULE: the black glass capsule. Its ends refract the portrait 34 pt outward (the pale wall becomes the grey
// ends of the pin), its body is a dark vertical gradient lifted around the white button, a 1 pt highlight on top
// that the front's specular travels along.
//   portrait, uImg as above; uRect float4 (x, y, w, h); uR corner radius; uBtn float2 button centre; uBtnR radius;
//   uThumb float4 the card thumbnail's rect (it lights the glass around it, as the button does);
//   uTop, uLow, uEnd half3 colours, already converted from sRGB to Display P3 (srgbToP3 below); uS;
//   uFront float x of the front, 60 ms late (pt; far outside when there is none); uEnds float2 the glass depth at the
//   capsule's two ends (the ends sharpen as the front passes).

export const GLASS_SKSL = `
uniform shader portrait;
uniform shader portraitSoft;
uniform float4 uImg;
uniform float uEdge[23];
uniform float uSeed[22];
uniform float uDepth[22];
uniform float uMag;
uniform float uMagAmt;
uniform float2 uMagC;
uniform float uS;
uniform float uFlatMix;
uniform float uKappa;
uniform float uTau;
uniform float uSlice;
uniform float uSpread;
uniform float uSag;
uniform float uBlur;
uniform float uEdgeK;
uniform float uChroma;
uniform float uGlintW;
uniform float uSeam;
uniform float uLight;
uniform float uSpec;
uniform float uRun;
uniform float3 uRunRib;

float sq(float x) {
  return x * x;
}

float hash1(float n) {
  return fract(sin(n * 12.9898) * 43758.5453);
}

float vnoise(float y, float seed) {
  float i = floor(y);
  float f = y - i;
  float a = hash1(i + seed * 17.0);
  float b = hash1(i + 1.0 + seed * 17.0);
  float s = f * f * (3.0 - 2.0 * f);
  return mix(a, b, s);
}

float2 clampImg(float2 p) {
  return clamp(p, uImg.xy + 0.5, uImg.xy + uImg.zw - 0.5);
}

half4 img(float2 p) {
  return portrait.eval(clampImg(p));
}

half3 softAt(float2 p) {
  return portraitSoft.eval(clampImg(p)).rgb;
}

// the seams wander a little as they run down the glass: each inner seam is its own smooth curve (a wander keyed to
// screen x jumped wherever x crossed a pitch boundary and printed a stair-step through the flute)
float wander(float yl, float k) {
  return 0.55 * sin(yl / 83.0 + k * 1.7) + 0.3 * sin(yl / 37.0 + k * 3.1);
}

half4 glass(float2 xy, float fixedDepth) {
  float yl = xy.y / uS;
  float xw = xy.x;
  int idx = 0;
  for (int k = 1; k < 22; k++) {
    if (xw >= uEdge[k] + uS * wander(yl, float(k))) { idx = k; }
  }
  float e0 = 0.0;
  float e1 = 1.0;
  float a = 1.0;
  float aL = -9.0;
  float aR = -9.0;
  float sd = 0.5;
  for (int k = 0; k < 22; k++) {
    if (k == idx) { e0 = uEdge[k]; e1 = uEdge[k + 1]; a = uDepth[k]; sd = uSeed[k]; }
    if (k == idx - 1) { aL = uDepth[k]; }
    if (k == idx + 1) { aR = uDepth[k]; }
  }
  if (idx > 0) { e0 += uS * wander(yl, float(idx)); }
  if (idx < 21) { e1 += uS * wander(yl, float(idx + 1)); }
  if (aL < -8.0) { aL = a; }
  if (aR < -8.0) { aR = a; }
  if (fixedDepth >= 0.0) { a = fixedDepth; aL = fixedDepth; aR = fixedDepth; }
  float hw = 0.5 * (e1 - e0);
  float cx = e0 + hw;
  float u = clamp((xw - cx) / hw, -1.0, 1.0);
  float r1 = fract(sd * 7.13 + 0.17);
  float r2 = fract(sd * 13.71 + 0.53);
  float r3 = fract(sd * 29.3 + 0.81);
  float da = clamp(a, 0.0, 1.0);
  float al = clamp(a, -0.3, 1.3);

  // One cylinder lens per flute. Only x refracts (sag aside), so a feature keeps its scanline.
  float mag = 1.0 + uMagAmt * clamp(fixedDepth >= 0.0 ? fixedDepth : uMag, 0.0, 1.0);
  float sx0 = 1.0 / (mag * (1.0 + uSpread * al));
  float cp = uMagC.x + (cx - uMagC.x) * sx0;
  float hW = hw * sx0;
  float ph = uTau * u;
  float t1 = sin(uTau) / cos(uTau);
  float tt = sin(ph) / cos(ph) / t1;
  float dtt = uTau / (cos(ph) * cos(ph) * t1);
  // thicker glass towards the edges: the outer flutes read further across the scene and pick up the ears as thin slivers
  float edgeD = abs(cx - uMagC.x) / (0.5 * uImg.z);
  float kap = uKappa * (0.92 + 0.16 * r1) * (1.0 + uEdgeK * edgeD * edgeD);
  float lensX = cp + hW * (kap * tt + uSlice * (2.0 * r2 - 1.0));
  float sx = mix(xy.x, lensX, al);
  float foot = abs(mix(1.0, sx0 * kap * dtt, al));
  // five taps, 1.4 pt apart on the SCREEN: the squeeze at the flanks keeps the same soft edge as the crest
  float sp = clamp(uBlur * foot, 0.3, 14.0) * uS;
  // near the seams a flute pulls the scene away from the face's centre line: hairlines climb into cusps, dark necks run down
  float sy = uMagC.y + (xy.y - uMagC.y) * (1.0 - uSag * al * pow(abs(u), 4.0) * (0.6 + 0.8 * r3));
  float2 p = float2(sx, sy);

  half3 soft = (softAt(p - float2(2.0 * sp, 0.0)) + 4.0 * softAt(p - float2(sp, 0.0)) + 6.0 * softAt(p) +
                4.0 * softAt(p + float2(sp, 0.0)) + softAt(p + float2(2.0 * sp, 0.0))) * 0.0625;
  float off = uChroma * uS * smoothstep(0.2, 0.6, abs(aR - aL));
  if (off > 0.05) {
    soft.r = softAt(p + float2(off * foot, 0.0)).r;
    soft.b = softAt(p - float2(off * foot, 0.0)).b;
  }
  half3 col = mix(portrait.eval(clampImg(p)).rgb, soft, smoothstep(0.0, 0.5, da));

  float lum = dot(col, half3(0.299, 0.587, 0.114));
  // Where the lens and the straight view are both the pale wall, keep the wall flat (no curtain pleats from its vignette);
  // the wall seen through a flank next to the face stays as it is.
  half3 straight = softAt(xy);
  float wallBoth = smoothstep(0.66, 0.80, lum) * smoothstep(0.66, 0.80, dot(straight, half3(0.299, 0.587, 0.114)));
  col = mix(col, straight, wallBoth * 0.85 * da);
  lum = dot(col, half3(0.299, 0.587, 0.114));
  // The pale wall keeps its exposure; the subject is greyer with near-black facial shadows.
  float subject = 1.0 - smoothstep(0.56, 0.78, lum);
  half3 neutral = mix(col, half3(lum), 0.32);
  half3 toned = max(half3(0.012), (neutral - half3(0.28)) * 1.5 + half3(0.30 - 0.08 * (1.0 - smoothstep(0.1, 0.3, lum))));
  col = mix(col, toned, da * subject * (1.0 - smoothstep(0.40, 0.60, lum)));

  // A rounded flute: soft dark troughs at the seams, a broad lift where the key light lands on its left flank.
  float flank = smoothstep(0.45, 1.0, abs(u));
  float sub = 0.05 + 0.95 * subject;
  col *= 1.0 - da * (0.04 + 0.26 * flank) * sub;
  col += half3(0.05 * da * exp(-sq((u + 0.42) / 0.36)) * subject);

  // Glints. The key glint wanders about the lit crest and slides with the light; a second, wider-wandering one
  // crosses it (the pin's X shapes); a bright hairline rides the lit seam. Each is lit along only part of its length.
  float ed = (1.0 - abs(u)) * hw;
  float lam = (220.0 + 160.0 * r1) * uS;
  float ug = -0.34 + 0.30 * uLight + 0.12 * sin(6.2832 * xy.y / lam + 6.2832 * r2) + 0.10 * (vnoise(yl / 83.0, r3 * 5.0) - 0.5);
  float gs = max(uGlintW * uS, 0.05);
  float g1 = exp(-sq((u - ug) * hw / gs));
  // one softbox above the glass: the crest glints are strongest across the face and fade towards the top and the capsule
  float box = 0.30 + 0.70 * exp(-sq((yl - 250.0) / 170.0));
  float lit = (0.35 + 0.65 * r3) * (0.55 + 0.45 * vnoise(yl / 95.0 + r2 * 3.0, r1 * 9.0 + 1.0));
  // a second glint now and then crosses the flute on a slant (the pin's cheek and hairline strokes)
  float lam2 = (110.0 + 70.0 * r2) * uS;
  float ug2 = 0.80 * sin(6.2832 * xy.y / lam2 + 6.2832 * r3);
  float g2 = exp(-sq((u - ug2) * hw / gs));
  float lit2 = smoothstep(0.62, 0.86, vnoise(yl / 55.0 + r3 * 4.0, r2 * 7.0 + 2.0));
  float seamL = exp(-sq((ed - 1.4 * uS) / (0.5 * uS))) * smoothstep(0.1, -0.3, u);
  float litS = smoothstep(0.38, 0.78, vnoise(yl / 45.0 + r1 * 5.0, r3 * 3.0 + 4.0));
  float gl = clamp(g1 * lit * box + 0.5 * g2 * lit2 * box + 0.8 * seamL * litS * box, 0.0, 1.0);
  half3 glintCol = half3(0.95, 0.95, 0.98);
  col = mix(col, glintCol, gl * (0.25 + 0.75 * subject) * 0.95 * da * uSpec);
  col *= 1.0 - 0.85 * exp(-sq(ed / (uSeam * uS))) * da * (0.3 + 0.7 * subject);

  // The front: the flute that is half down carries a 2 pt white specular. (clamped to 0..1 depth: no flash on the overshoot)
  float fs = exp(-sq((a - 0.5) / 0.2)) * step(0.0, a) * step(a, 1.0);
  col = mix(col, half3(1.0), 0.8 * fs * exp(-sq((u + 0.30) * hw / uS)) * uSpec);

  // At rest a soft glint runs down three flutes, once a loop.
  float isRun = 0.0;
  if (abs(float(idx) - uRunRib.x) < 0.5 || abs(float(idx) - uRunRib.y) < 0.5 || abs(float(idx) - uRunRib.z) < 0.5) { isRun = 1.0; }
  float y0 = mix(-40.0, 470.0, clamp(uRun, 0.0, 1.0)) * uS;
  float pulse = exp(-sq((xy.y - y0) / (55.0 * uS))) * step(0.0, uRun) * sin(3.1416 * clamp(uRun, 0.0, 1.0));
  col = mix(col, glintCol, isRun * pulse * exp(-sq((u - ug) * hw / (1.1 * uS))) * 0.55 * (0.4 + 0.6 * subject) * da);
  return half4(col, 1.0);
}

half4 main(float2 xy) {
  if (uFlatMix < 0.0) { return glass(xy, -1.0); }
  // No moving refraction, magnification or spring in Reduce Motion. Only opacity changes.
  return mix(glass(xy, 1.0), img(xy), clamp(uFlatMix, 0.0, 1.0));
}
`;

export const CAPSULE_SKSL = `
uniform shader portrait;
uniform float4 uImg;
uniform float4 uRect;
uniform float uR;
uniform float2 uBtn;
uniform float uBtnR;
uniform float4 uThumb;
uniform half3 uTop;
uniform half3 uLow;
uniform half3 uEnd;
uniform float uS;
uniform float uFront;
uniform float2 uEnds;

float sq(float x) {
  return x * x;
}

half4 img(float2 p, float4 r) {
  float2 q = clamp(p, r.xy + 0.5, r.xy + r.zw - 0.5);
  return portrait.eval(q);
}

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 xy) {
  float2 b = 0.5 * uRect.zw;
  float2 p = xy - (uRect.xy + b);
  float d = sdBox(p, b, uR);
  if (d > 1.0) { return half4(0.0); }
  float e = 0.5;
  float2 g = float2(sdBox(p + float2(e, 0.0), b, uR) - sdBox(p - float2(e, 0.0), b, uR),
                    sdBox(p + float2(0.0, e), b, uR) - sdBox(p - float2(0.0, e), b, uR));
  float2 nrm = g / max(length(g), 0.0001);
  float inside = -d;

  // body: a dark vertical fall, lifted where the white button lights the glass
  float ty = clamp((xy.y - uRect.y) / uRect.w, 0.0, 1.0);
  half3 col = mix(uTop, uLow, smoothstep(0.1, 0.95, ty));
  // the white button and the frosted card light the glass around them
  float dB = max(length(xy - uBtn) - uBtnR, 0.0) / (16.0 * uS);
  float dT = max(sdBox(xy - (uThumb.xy + 0.5 * uThumb.zw), 0.5 * uThumb.zw, 4.0 * uS), 0.0) / (20.0 * uS);
  col += half3(0.14 * exp(-dB * dB) + 0.13 * exp(-dT * dT));

  // thick ends: the glass looks 34 pt outward (the wall turns the pin's grey), a broad haze from each end
  // and a crisp rim along the curved edge
  // the ends read the glass above them: as the front passes (depth -> 0) they refract less and blur less
  float dEnd = clamp(mix(uEnds.x, uEnds.y, clamp((xy.x - uRect.x) / uRect.z, 0.0, 1.0)), 0.0, 1.0);
  float2 sp = xy + nrm * (14.0 + 20.0 * dEnd) * uS;
  half3 seen = half3(0.0);
  for (int j = 0; j < 5; j++) {
    seen += img(sp + float2(0.0, (float(j) - 2.0) * (2.5 + 4.5 * dEnd) * uS), uImg).rgb;
  }
  seen = seen / 5.0;
  half3 rimCol = mix(uEnd, seen * 0.76, 0.45);
  float endD = b.x - abs(p.x);
  float reach = mix(40.0, 14.0, smoothstep(0.2, 1.0, ty)) * uS; // the haze reaches further along the top
  float haze = exp(-endD / reach);
  col = mix(col, rimCol, 0.92 * haze);
  float rim = exp(-inside / (9.0 * uS)) * pow(abs(nrm.x), 1.2);
  col = mix(col, rimCol, 0.85 * rim);

  // a 1 pt highlight along the top edge, a darker lip along the bottom
  float lip = 1.0 - smoothstep(0.0, 1.2 * uS, inside);
  // one light across both glasses: the front's 2 pt specular continues here, a 40 pt stretch of the highlight 0.30 -> 0.75
  float lit = 0.30 + 0.45 * exp(-sq((xy.x - uFront) / (14.0 * uS)));
  col += half3(lit * lip * smoothstep(0.2, 0.9, -nrm.y));
  col *= 1.0 - 0.25 * lip * smoothstep(0.2, 0.9, nrm.y);

  float alpha = 1.0 - smoothstep(-0.35, 0.35, d);
  return half4(col * alpha, alpha);
}
`;

/** Literal sRGB → target components. ImageShader samples already use Skia's target space: do not convert twice. */
export function srgbToP3(hex: string, targetP3 = true): [number, number, number] {
  const v = hex.replace('#', '');
  const c = [0, 2, 4].map((i) => parseInt(v.slice(i, i + 2), 16) / 255);
  if (!targetP3) return [c[0], c[1], c[2]];
  const lin = c.map((x) => (x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4)));
  const m = [
    [0.8225, 0.1774, 0.0],
    [0.0332, 0.9669, 0.0],
    [0.0171, 0.0724, 0.9108],
  ];
  const p = m.map((row) => row[0] * lin[0] + row[1] * lin[1] + row[2] * lin[2]);
  const enc = p.map((x) => (x <= 0.0031308 ? x * 12.92 : 1.055 * Math.pow(x, 1 / 2.4) - 0.055));
  return [enc[0], enc[1], enc[2]];
}
