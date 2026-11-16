// Beam Control — the capsule's inside, one runtime effect drawn into the capsule's (clipped) path.
//
// Polar coordinates round the lens L (design pt): `up` is the height above it, φ the angle from straight up measured
// from the edge of a 14 pt aperture (the glyph's head), r the distance.
//   cone     a filled cone of light, full across its width (≥ 85 % of the axis value out to 0.8 θ), ending in a narrow
//            angular window. Dispersion is a colour shift of that edge: the red window is 4.5 % × spread wider than the
//            green one and the blue window 4.5 % × spread narrower, so the fringe can never outshine the inside and
//            closes into the white core as the beam focuses (it is ∝ spread)
//   along    a falloff 1 / (1 + (r / r0)^p) whose length scales with the reach R, cut softly just past R
//   gain     the same flux through a smaller solid angle: narrow burns hotter (uniform uGain, from opticsOf)
//   haze     the broad scatter round the cone, and the light the beam throws onto the glass (read through the capsule's
//            distance field)
//   pencil   a collimated pencil: a gaussian across the axis that starts at the lens (5 pt) and widens with the
//            half-angle (σ = 5 + up · tan θ), brightness monotonic from the lens, a halo over the first 60 pt
//   landing  where the pencil reaches the end of its throw it lands as a spot on the ceiling (46 × 10 pt)
//   lens     a white-hot core and a halo at L, scaled by the lens heat (it glows on 0.14 s after switching off)
//   glass    a vertical gradient (the theme's colour), a 1 px rim all round, a magenta wallpaper reflection on the lower
//            left edge, ±1/255 dither against banding on OLED
//   after    the retinal afterimage of a held pencil: a dark lilac stripe that fades out of the fan as it opens
// Light is accumulated in display space and tone-mapped with 1 − e^(−x), so the narrow core reaches white without
// clipping to a flat bar; on iOS everything is then converted from sRGB to Display P3 (uP3 = 1), the space Skia hands
// to the screen there.
//
// Uniforms (all driven per frame by the component from `lookAt(t)` / the finger, through `opticsOf`; the order is the
// order of the offline harness's array):
//   uLens    float2  lens, px                      uS      float  px per design pt (W / 402)
//   uB       float   brightness 0 … 1              uHot    float  lens heat 0 … 1
//   uTheta   float   half-angle, rad (0.02 … 0.72) uReach  float  reach R, design pt (22 … 215)
//   uGain    float   core gain 1 … 7.5             uWide   float  spread 0 (pencil) … 1 (clamped)
//   uCap     float4  capsule x, y, w, h (px)       uCapR   float  capsule corner radius (px)
//   uShow    float   content opacity 0 … 1 (the capsule's morph)
//   uPhase   float   loop phase 0 … 1 (heat shimmer, whole cycles)   uStill float 1 under Reduce Motion
//   uGlass   float3  glass colour (the middle of the gradient, 0 … 1)
//   uLight   float3  the light's colour at the lens (0 … 1)
//   uFringe  float   dispersion strength, 1 = the pin
//   uAfter   float   afterimage 0 … 1              uAfterReach float  its length, design pt
//   uP3      float   1 = convert to Display P3 (iOS)
// No SkSL reserved names (half, flat, distance, target) as identifiers.

export const BEAM_SKSL = `
uniform float2 uLens;
uniform float uS;
uniform float uB;
uniform float uHot;
uniform float uTheta;
uniform float uReach;
uniform float uGain;
uniform float uWide;
uniform float4 uCap;
uniform float uCapR;
uniform float uShow;
uniform float uPhase;
uniform float uStill;
uniform float3 uGlass;
uniform float3 uLight;
uniform float uFringe;
uniform float uAfter;
uniform float uAfterReach;
uniform float uP3;

float sq(float x) { return x * x; }

float hash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }

float sdRound(float2 p, float2 c, float2 hs, float rr) {
  float2 q = abs(p - c) - hs + float2(rr, rr);
  return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - rr;
}

float3 coneWindow(float a, float3 th3, float sIn, float sOut) {
  return float3(
    1.0 - smoothstep(th3.x - sIn, th3.x + sOut, a),
    1.0 - smoothstep(th3.y - sIn, th3.y + sOut, a),
    1.0 - smoothstep(th3.z - sIn, th3.z + sOut, a));
}

half4 main(float2 p) {
  // the black glass body
  float yy = clamp((p.y - uCap.y) / max(uCap.w, 1.0), 0.0, 1.0);
  float3 col = mix(uGlass * 0.08, uGlass, smoothstep(0.08, 0.55, yy));
  col = mix(col, uGlass * 0.89, smoothstep(0.62, 1.0, yy));

  float2 cc = float2(uCap.x + uCap.z * 0.5, uCap.y + uCap.w * 0.5);
  float sdPx = sdRound(p, cc, float2(uCap.z, uCap.w) * 0.5, uCapR);
  float sd = sdPx / uS; // design pt, negative inside

  // polar round the lens, from the edge of a 14 pt aperture
  float2 d = (p - uLens) / uS;
  float up = -d.y;
  float r = length(d);
  float ap = 7.0 * (1.0 - smoothstep(0.0, 40.0, up));
  float lat = max(abs(d.x) - ap, 0.0);
  // the cone starts at the lens, softly, and its base is a rounded cap (higher at the sides), not a flat cut
  float a = mix(1.5708, atan(lat, max(up, 0.001)), smoothstep(-5.0, 3.0, up - 4.0 * min(sq(d.x / 10.0), 1.0)));
  float R = max(uReach, 1.0);
  float w = clamp(uWide, 0.0, 1.0);
  float th = uTheta;
  float shimmer = 1.0 + 0.03 * (1.0 - w) * sin(6.2831853 * 6.0 * uPhase) * (1.0 - uStill);
  float energy = uB * pow(uGain, 0.45) * shimmer;
  float3 lc = uLight / max(max(max(uLight.r, uLight.g), uLight.b), 0.001);

  // Filled angular window. All channels share the same radial falloff; dispersion can only REMOVE energy at the edge.
  float sIn = radians(1.5 + 3.5 * w);
  float sOut = radians(1.0 + 2.0 * w);
  float dispersion = 0.045 * w * uFringe;
  float3 angles = th * float3(1.0 + dispersion, 1.0, 1.0 - dispersion);
  float3 angular = coneWindow(a, angles, sIn, sOut);
  float r0 = 26.0 + 0.3 * R;
  float fall = 1.0 / (1.0 + pow(r / r0, 1.6));
  float cut = 1.0 - smoothstep(0.75 * R, 1.05 * R, r);
  // the cone fades in over its first 12 pt, from the lens glow, so its base is a soft cap and never a visible cut
  float baseFade = smoothstep(-1.0, 12.0, up - 4.0 * min(sq(d.x / 10.0), 1.0));
  float3 cone = angular * fall * cut * baseFade * energy * 1.25;

  // The collimated pencil begins at the aperture, never a spindle detached above it. Its on-axis energy decreases
  // monotonically from 1 to .35 over R; the small halo is confined to its first 60 pt.
  float pw = 1.0 - smoothstep(0.0, 0.25, w);
  float sigma = 5.0 + max(up, 0.0) * tan(th);
  float pen = exp(-sq(abs(d.x) / sigma)) * smoothstep(-3.0, 3.0, up);
  float penLen = mix(1.0, 0.35, clamp(up / R, 0.0, 1.0));
  float penCut = 1.0 - smoothstep(0.98 * R, 1.1 * R, up);
  float halo60 = exp(-sq(r / 60.0));
  float pencil = (pen * penLen * penCut * 1.5 + halo60 * 0.08) * energy;
  float3 light = mix(cone, float3(pencil), pw);

  // Lens heat keeps the meaningful 140 ms afterglow; its light is attached to the aperture.
  float hot = uHot * mix(1.0, 1.35, clamp((uGain - 1.0) / 6.5, 0.0, 1.0));
  light += float3(exp(-sq(r / (6.0 + 2.0 * hot))) * hot * 2.0);

  // A 46 x 10 pt landing spot at the pencil's ceiling plane, safely BELOW the island.
  float land = smoothstep(190.0, 212.0, R) * pw * uB;
  float2 sp = float2(d.x / 23.0, (up - R) / 5.0);
  float spot = exp(-dot(sp, sp)) * land * 0.55;
  float3 tint = mix(lc, float3(213.0, 209.0, 242.0) / 255.0, 0.45 * smoothstep(0.0, 0.6 * R, r));
  float3 lit = (1.0 - exp(-light * uShow)) * tint;
  lit = 1.0 - (1.0 - lit) * (1.0 - float3(233.0, 229.0, 246.0) / 255.0 * spot * uShow);

  // light thrown onto the inner walls, only where the cone points
  float toward = exp(-sq(a / (th + 0.5))) * uB * pow(uGain, 0.25) / (1.0 + sq(r / 190.0));
  float edge = exp(min(sd, 0.0) / 18.0);
  float3 walls = float3(0.788, 0.769, 0.839) * toward * (edge * mix(0.2, 1.0, w) * 0.9 + 0.03) * 0.1;

  col = col + walls * uShow;
  col = 1.0 - (1.0 - col) * (1.0 - lit); // the light over the glass

  // the afterimage of a held pencil, over what is lit
  float2 q = float2(d.x / 9.0, 0.0);
  float stripe = exp(-dot(q, q)) * smoothstep(0.0, 24.0, up) * (1.0 - smoothstep(0.8 * uAfterReach, uAfterReach, up));
  float lumOut = dot(col, float3(0.3333, 0.3333, 0.3333));
  col = mix(col, float3(42.0, 36.0, 64.0) / 255.0, 0.18 * uAfter * stripe * smoothstep(0.03, 0.3, w) * smoothstep(0.05, 0.3, lumOut));

  // glass edge: 1 px all round (6 %, 9 % on the top third), the wallpaper's magenta on the lower left
  float inner = 1.0 - smoothstep(0.0, 1.3, -sdPx);
  float rimOn = smoothstep(0.3, 0.9, uShow) * step(0.0, -sdPx);
  // The exact squircle rim is stroked at one physical pixel by the canvas, not approximated with this rounded SDF.
  float2 nrm = normalize(p - cc + float2(0.001, 0.001));
  float ll = sq(clamp(dot(nrm, float2(-0.7071, 0.7071)), 0.0, 1.0));
  col += float3(0.78, 0.4, 0.61) * inner * rimOn * ll * 0.08;

  // dither
  col += (hash2(floor(p * 3.0)) - 0.5) / 255.0; // per device pixel at @3x

  float3 outc = clamp(col, 0.0, 1.0);
  float3 lin = pow(outc, float3(2.2));
  float3 p3 = float3(0.8225 * lin.r + 0.1774 * lin.g, 0.0332 * lin.r + 0.9669 * lin.g, 0.0171 * lin.r + 0.0724 * lin.g + 0.9108 * lin.b);
  float3 enc = pow(p3, float3(1.0 / 2.2));
  return half4(half3(mix(outc, enc, uP3)), 1.0);
}
`;
