package com.ngocdevv.nativecook.blocks.animations.superchargecard

// Original SkSL equations, with the layout constants expanded.
internal object SuperchargeShader {
    const val scene =
        """
uniform shader paperStrip;
uniform shader treeStrip;
uniform float2 resolution;
uniform float3 frame;
uniform float uClock;
uniform float4 uCard;
uniform float uSink;
uniform float uEnergy;
uniform float uGlow;
uniform float uGlyph;
uniform float uGlyphScale;
uniform float uRing;
uniform float uRingLit;
uniform float uDim;
uniform float uTree;
uniform float uTreeGlow;
uniform float uPulse;
uniform float uShock;
uniform float2 uShaft;
uniform float3 uTouch;
uniform float uShake;

const float2 pivot = float2(201.0000, 458.0000);
const float2 glassOrigin = float2(65.0000, 288.0000);
const float2 glassSize = float2(272.0000, 334.0000);
const float glassRadius = 18.0000;
const float2 boltCentre = float2(214.0000, 312.0000);
const float boltRound = 3.0000;
const float2 ringCentre = float2(268.0000, 466.0000);
const float ringRadius = 30.0000;
const float ringStroke = 4.5000;
const float paperTop = 448.0000;
const float paperBottom = 628.0000;
const float notchStart = 198.0000;
const float notchIn = 220.0000;
const float notchOut = 312.0000;
const float notchEnd = 328.0000;
const float notchDepth = 18.0000;
const float2 paperOrigin = float2(59.0000, 442.0000);
const float2 paperTile = float2(864.0000, 576.0000);
const float paperScale = 3.0000;
const float2 treeOrigin = float2(65.0000, 288.0000);
const float2 treeTile = float2(544.0000, 668.0000);
const float treeScale = 2.0000;
const float3 gold = float3(1.0, 0.81, 0.11);
const float3 hot = float3(1.0, 0.96, 0.68);

float sq(float a) { return a * a; }
float hash21(float2 p) {
  float3 q = fract(float3(p.xyx) * 0.1031);
  q += dot(q, q.yzx + 33.33);
  return fract((q.x + q.y) * q.z);
}
float roundedBox(float2 p, float2 size, float r) {
  float2 v = abs(p) - size * 0.5 + r;
  return length(max(v, 0.0)) + min(max(v.x, v.y), 0.0) - r;
}
float3 boltField(float2 p) {
  float2 bolt[7];
  bolt[0] = float2(240.0000, 220.0000);
  bolt[1] = float2(222.0000, 273.0000);
  bolt[2] = float2(281.0000, 271.0000);
  bolt[3] = float2(156.0000, 421.0000);
  bolt[4] = float2(193.0000, 320.0000);
  bolt[5] = float2(149.0000, 327.0000);
  bolt[6] = bolt[0];
  float nearest = dot(p - bolt[0], p - bolt[0]);
  float winding = 1.0;
  float2 nearestFoot = p - bolt[0];
  for (int i = 0; i < 6; i++) {
    float2 edge = bolt[i + 1] - bolt[i];
    float2 v = p - bolt[i];
    float2 foot = v - edge * clamp(dot(v, edge) / max(dot(edge, edge), 0.001), 0.0, 1.0);
    if (dot(foot, foot) < nearest) { nearest = dot(foot, foot); nearestFoot = foot; }
    bool a = p.y >= bolt[i].y;
    bool b = p.y < bolt[i + 1].y;
    bool c = edge.x * v.y > edge.y * v.x;
    if ((a && b && c) || (!a && !b && !c)) { winding = -winding; }
  }
  return float3(winding * sqrt(max(nearest, 0.0)) - boltRound,
    nearestFoot * winding / max(sqrt(max(nearest, 0.0)), 0.001));
}
float segmentD(float2 p, float2 a, float2 b) {
  float2 v = b - a;
  return length(p - a - v * clamp(dot(p - a, v) / max(dot(v, v), 0.001), 0.0, 1.0));
}
float paperTap(float2 uv, float tile) {
  float2 at = clamp(uv, float2(0.5), paperTile - 0.5);
  return paperStrip.eval(at + float2(tile * paperTile.x, 0.0)).r;
}
float treeTap(float2 uv, float tile) {
  float2 at = clamp(uv, float2(0.5), treeTile - 0.5);
  return treeStrip.eval(at + float2(tile * treeTile.x, 0.0)).r;
}
float sleeveEdge(float x) {
  return paperTop + notchDepth * smoothstep(notchStart, notchIn, x)
    * (1.0 - smoothstep(notchOut, notchEnd, x));
}
float3 finish(float3 rgb, float2 xy) {
  rgb = 1.0 - exp(-max(rgb, float3(0.0)));
  return clamp(rgb + (hash21(floor(xy)) - 0.5) * (3.0 / 255.0), 0.0, 1.0);
}

half4 main(float2 xy) {
  float2 design = (xy - frame.xy) / max(frame.z, 0.001);
  float co = cos(uCard.z), si = sin(uCard.z);
  float2 moved = design - pivot - uCard.xy;
  float2 p = float2(co * moved.x + si * moved.y, -si * moved.x + co * moved.y)
    / max(uCard.w, 0.1) + pivot;
  float2 g = p - float2(0.0, uSink);
  float aa = 0.65 / max(frame.z * uCard.w, 0.1);
  float dim = clamp(uDim, 0.0, 1.0);
  float ambient = max(clamp(uGlow, 0.0, 1.0), clamp(uEnergy, 0.0, 1.0)) * dim;
  float lamp = max(uGlyph, 0.0);
  float fullEmission = sq(max(lamp - 0.30, 0.0) / 0.70);
  // A low live lamp stays luminous. Dimming scales radiance, never its ignition threshold.
  float emission = (fullEmission + 0.08 * smoothstep(0.15, 0.30, lamp)
    * (1.0 - min(fullEmission, 1.0))) * dim;
  float smoked = 1.0 - smoothstep(0.12, 0.20, lamp * dim);
  float2 airAt = design - float2(pivot.x, glassOrigin.y + 60.0) - uCard.xy * 0.25;
  float air = exp(-dot(airAt, airAt) / (2.0 * sq(220.0)));
  float vignette = 1.0 - 0.50 * smoothstep(0.15, 0.78, length((xy / max(resolution, float2(1.0))) - 0.5));
  float3 colour = float3(0.024, 0.025, 0.029) + air * float3(0.017, 0.019, 0.024);
  colour += (float3(0.11, 0.106, 0.095) + float3(0.065, 0.047, 0.010) * air) * ambient;
  colour *= vignette;
  float contact = exp(-sq((p.x - pivot.x) / 118.0) - sq((p.y - paperBottom - 7.0) / 23.0));
  colour *= 1.0 - contact * 0.86;
  float shaftLight = uShaft.y * exp(-0.5 * sq((design.x - uShaft.x) / 70.0));
  colour += hot * shaftLight * 0.21 * (1.0 - smoothstep(0.0, glassOrigin.y + 50.0, design.y));

  // Empty air skips the polygon and every texture read. The fade is already imperceptible here.
  if (uShock <= 0.0 && (p.y < 20.0 || p.y > paperBottom + 6.0 || abs(p.x - pivot.x) > 340.0)) {
    return half4(half3(finish(colour, xy)), 1.0);
  }

  float glassD = roundedBox(g - glassOrigin - glassSize * 0.5, glassSize, glassRadius);
  float glassMask = 1.0 - smoothstep(-aa, aa, glassD);
  float glyphScale = max(uGlyphScale, 0.1);
  float2 boltAt = (g - boltCentre) / glyphScale + boltCentre;
  float3 bolt = boltField(boltAt);
  float boltD = bolt.x * glyphScale;
  float boltOutside = max(boltD, 0.0);
  float ringRadial = length(p - ringCentre);
  float ringD = abs(ringRadial - ringRadius) - ringStroke * 0.5;
  float ringOutside = max(ringD, 0.0);
  float shockRadius = 30.0 + 300.0 * (1.0 - sq(1.0 - clamp(uShock, 0.0, 1.0)));
  float shockD = abs(ringRadial - shockRadius);
  float shockOn = step(0.0001, uShock) * sq(1.0 - clamp(uShock, 0.0, 1.0));
  float shockWave = exp(-sq(shockD / 10.8)) * shockOn * dim;

  if (glassMask > 0.0) {
    float rise = mix(paperTop, glassOrigin.y, clamp(uEnergy, 0.0, 1.0));
    float wobble = (sin(g.x * 0.057 + uClock * 3.6) * 0.72
      + sin(g.x * 0.119 - uClock * 2.7) * 0.28
      + sin(g.x * 0.31 + uClock * 4.1) * sin(g.x * 0.083 - uClock) * 0.12)
      * (1.2 + min(uShake, 8.0) * 0.45);
    float sideGap = min(g.x - glassOrigin.x, glassOrigin.x + glassSize.x - g.x);
    float surfaceY = rise + wobble - 2.0 * exp(-max(sideGap, 0.0) / 3.0);
    float below = g.y - surfaceY;
    float filled = smoothstep(-1.5, 1.5, below) * step(0.001, uEnergy);
    filled = mix(filled, 1.0, smoothstep(0.975, 1.0, uEnergy));
    float lit = mix(clamp(uGlow, 0.0, 1.0), 1.0, filled) * dim;
    float surfaceOn = smoothstep(0.0, 0.035, uEnergy)
      * (1.0 - smoothstep(0.94, 1.0, uEnergy)) * (1.0 - clamp(uGlow, 0.0, 1.0)) * dim;
    float meniscus = exp(-sq(below / 1.8)) * surfaceOn;
    float depth = mix(1.0, 0.63, smoothstep(glassOrigin.y, paperTop, g.y));
    float3 glass = mix(float3(0.085, 0.080, 0.061), float3(0.63, 0.55, 0.17) * depth, lit);
    glass.g *= 1.0 + 0.06 * smoothstep(paperTop, paperBottom, g.y);
    glass += colour * 0.12;
    float sheen = exp(-sq((g.x + g.y * 0.44 - 272.0) / 48.0));
    glass += mix(float3(0.040, 0.044, 0.050), float3(0.028, 0.031, 0.035), lit)
      * sheen * (1.0 - 0.7 * lit);
    float innerBand = exp(-sq((glassD + 2.4) / 1.4));
    glass *= 1.0 - 0.26 * innerBand;
    float edgeRim = exp(-sq(glassD / 0.60));
    float topBias = 0.12 + 0.88 * exp(-max(g.y - glassOrigin.y, 0.0) / 25.0);
    glass += mix(float3(0.28, 0.30, 0.34), float3(0.90, 0.73, 0.19), lit)
      * edgeRim * topBias;
    glass += hot * edgeRim * topBias * shaftLight * 1.2;
    float2 dotCell = mod(g - glassOrigin + 4.0, 8.0) - 4.0;
    float dotRadius = mix(0.70, 1.0, surfaceOn * filled);
    float dots = 1.0 - smoothstep(dotRadius - 0.25, dotRadius + 0.25, length(dotCell));
    float dotVar = 0.70 + 0.30 * hash21(floor((g - glassOrigin + 4.0) / 8.0));
    glass += hot * dots * dotVar * (0.013 + 0.075 * lit + 0.12 * surfaceOn * filled + 0.8 * shockWave);
    // A warm reservoir under the surface, with light scattering into the empty vessel above.
    glass += gold * surfaceOn * (0.34 * exp(-max(below, 0.0) / 24.0) * filled
      + 0.20 * exp(-max(-below, 0.0) / 18.0) * (1.0 - filled));
    // Keep the surface and its scatter strictly inside the vessel, including its AA fringe.
    float surfaceClip = 1.0 - smoothstep(-1.5 * aa, 0.0, glassD);
    glass += (float3(2.4, 2.15, 1.10) * meniscus
      + hot * exp(-sq(below / 6.0)) * surfaceOn * 0.36) * surfaceClip;

    // Column-seeded speeds keep each mote continuous; one candidate per cell, no particle loop.
    float column = floor(g.x / 18.0);
    float speed = 12.0 + 21.0 * hash21(float2(column, 17.0));
    float2 moteGrid = float2(g.x, g.y + uClock * speed);
    float2 moteCell = floor(moteGrid / 18.0);
    float seed = hash21(moteCell);
    float2 moteAt = mod(moteGrid, 18.0) - float2(3.0 + seed * 12.0, 9.0);
    float moteRadius = 0.8 + 0.8 * hash21(moteCell + 71.0);
    float moteSq = dot(moteAt, moteAt);
    float mote = (1.6 * exp(-moteSq / sq(moteRadius))
      + 0.16 * exp(-moteSq / sq(moteRadius * 2.4))) * step(0.70, seed);
    float pop = 1.0 + 1.6 * exp(-sq(below / 3.0));
    glass += hot * mote * surfaceOn * smoothstep(-2.0, 1.0, below)
      * smoothstep(-3.0, 8.0, paperTop - g.y) * pop;

    if (uTreeGlow > 0.001 && uTree > 0.0) {
      float2 tuv = (g - treeOrigin) * treeScale;
      float core = treeTap(tuv, 0.0), halo = treeTap(tuv, 1.0), arrival = treeTap(tuv, 2.0);
      float grown = smoothstep(-0.014, 0.014, uTree - arrival);
      if (uTree >= 0.999) { grown = 1.0; }
      float front = exp(-sq((arrival - uTree + 0.025) / 0.030)) * (1.0 - step(0.999, uTree));
      float pulseBand = uPulse < 0.0 ? 0.0 : exp(-sq((arrival - uPulse) / 0.04));
      glass += hot * (core + 0.35 * halo) * grown
        * (uTreeGlow * (0.36 + 1.84 * smoothstep(0.3, 1.6, uTreeGlow)) * (1.0 + front) + pulseBand * 1.2) * dim;
    }
    float2 touchOffset = p - uTouch.xy;
    float touchSq = dot(touchOffset, touchOffset);
    glass += hot * uTouch.z * dim * (1.5 * exp(-touchSq / sq(16.0)) + 0.25 * exp(-touchSq / sq(46.0)));
    float sleeveGap = sleeveEdge(p.x) - p.y;
    glass *= 1.0 - 0.53 * exp(-abs(sleeveGap) / 6.5);
    colour = mix(colour, glass, glassMask);
  }

  // The distance field also supplies the bevel normal in the same six-edge pass.
  float boltMask = 1.0 - smoothstep(-aa, aa, boltD);
  float boltRim = exp(-sq(boltD / 0.70));
  float pillow = exp(-max(-boltD, 0.0) / 4.5);
  float key = max(dot(bolt.yz, float2(-0.6, -0.8)), 0.0);
  float shade = max(dot(bolt.yz, float2(0.6, 0.8)), 0.0);
  float3 boltBody = float3(0.062, 0.047, 0.033) * (1.0 - 0.48 * shade * pillow);
  boltBody += float3(0.17, 0.17, 0.16) * pillow * key;
  boltBody += float3(0.07, 0.085, 0.11) * boltRim;
  // Thin amber transmits the scene; the upper-left bevel catches the cool room softbox.
  float thin = exp(-max(-boltD, 0.0) / 6.0);
  boltBody += smoked * (colour * thin * 0.24 + float3(0.105, 0.050, 0.014) * thin
    + float3(0.46, 0.49, 0.54) * key * exp(-sq((boltD + 0.9) / 0.6)));
  boltBody += gold * ambient * (0.055 + 0.30 * pillow) * (0.6 + 0.4 * shade);
  float2 spineTop = mix(float2(240.0000, 220.0000), boltCentre, 0.18);
  float2 spineLeft = mix(float2(149.0000, 327.0000), boltCentre, 0.70);
  float2 spineRight = mix(float2(281.0000, 271.0000), boltCentre, 0.65);
  float2 spineRoot = mix(float2(156.0000, 421.0000), boltCentre, 0.15);
  float filamentD = min(segmentD(boltAt, spineTop, spineLeft),
    min(segmentD(boltAt, spineLeft, spineRight), segmentD(boltAt, spineRight, spineRoot)));
  float ember = lamp * (0.65 + 3.0 * smoothstep(0.15, 0.4, lamp) + 2.6 * smoked) * dim;
  boltBody += float3(0.90, 0.39, 0.085) * ember
    * (exp(-sq(filamentD / 0.65)) + 0.19 * exp(-filamentD / 3.2));
  // Pale core to pale edge: no saturated outline stroke around the lamp.
  boltBody += float3(6.2, 6.1, 2.35) * emission;
  colour = mix(colour, boltBody, boltMask);
  float boltReach = 1.0 - smoothstep(120.0, 180.0, boltOutside);
  float haloBias = 0.58 + 0.42 * (1.0 - smoothstep(boltCentre.y - 20.0, boltCentre.y + 100.0, g.y));
  haloBias *= mix(0.70, 1.0, smoothstep(boltCentre.y - 110.0, boltCentre.y - 45.0, g.y));
  haloBias *= 0.90 + 0.10 * smoothstep(boltCentre.x - 65.0, boltCentre.x + 60.0, g.x);
  colour += emission * boltReach * haloBias * (1.0 - 0.64 * glassMask * (1.0 - boltMask)) * (hot * 1.10 * exp(-boltOutside / 1.3)
    + gold * 0.20 * exp(-boltOutside / 5.0) + gold * 1.35 * exp(-boltOutside / 34.0));
  colour += float3(0.18, 0.14, 0.065) * emission * (1.0 - glassMask) * (1.0 - boltMask)
    * exp(-sq((g.x - boltCentre.x - 47.0) / 37.0) - sq((g.y - boltCentre.y + 55.0) / 40.0));
  colour += hot * boltRim * shaftLight * 0.5;

  float2 puv = (p - paperOrigin) * paperScale;
  if (puv.x >= 0.0 && puv.y >= 0.0 && puv.x <= paperTile.x && puv.y <= paperTile.y) {
    float coverage = paperTap(puv, 0.0);
    if (coverage > 0.0) {
      // Four height taps, two texels apart; +Z keeps normalization safe even on a bevel.
      float hx = paperTap(puv + float2(1.0, 0.0), 1.0) - paperTap(puv - float2(1.0, 0.0), 1.0);
      float hy = paperTap(puv + float2(0.0, 1.0), 1.0) - paperTap(puv - float2(0.0, 1.0), 1.0);
      float relief = paperTap(puv, 1.0);
      float3 normal = normalize(float3(-hx * 3.6, -hy * 3.6, 1.0));
      float down = clamp((p.y - paperTop) / (paperBottom - paperTop), 0.0, 1.0);
      float3 paper = mix(float3(1.30, 1.36, 1.44), float3(0.68, 0.73, 0.80), down);
      paper *= 0.98 + (relief - 0.5) * 0.18 + normal.y * -0.27;
      float softbox = exp(-down * 1.5) * (1.0 - smoothstep(0.48, 1.0, down));
      paper += ambient * (float3(0.90, 0.89, 0.045) * softbox + float3(0.10, 0.13, 0.11) * down)
        * max(dot(normal, float3(0.0, -0.35, 0.93675)), 0.0);
      paper += ambient * float3(0.36, 0.39, 0.10) * exp(-sq((down - 0.50) / 0.24));
      float ringLight = exp(-sq(ringRadial / 45.0)) * max(uRingLit, uRing * 0.42) * dim;
      float fromGlass = exp(-max(p.y - sleeveEdge(p.x), 0.0) / 50.0) * ambient;
      float fromBolt = exp(-boltOutside / 48.0) * emission;
      float3 toRing = normalize(float3(ringCentre - p, 45.0));
      float3 toBolt = normalize(float3(boltCentre - p, 80.0));
      paper += hot * (fromGlass * 0.12 * max(dot(normal, float3(0.0, -0.6, 0.8)), 0.0)
        + fromBolt * 0.55 * max(dot(normal, toBolt), 0.0));
      paper += float3(2.5, 2.5, -0.28) * ringLight * max(dot(normal, toRing), 0.0);
      float topBevel = exp(-sq((p.y - sleeveEdge(p.x) - 0.8) / 1.5));
      paper += hot * (shaftLight * 0.95 * exp(-down * 2.4)
        + topBevel * (0.10 + 0.8 * shaftLight + 0.6 * shockWave));
      float seatD = abs(length(p - ringCentre - float2(1.3, 3.0)) - ringRadius);
      paper *= 1.0 - 0.32 * exp(-sq(seatD / 3.8)) * (1.0 - clamp(uRingLit * dim, 0.0, 1.0));
      float ink = paperTap(puv, 2.0);
      paper *= mix(float3(0.060, 0.073, 0.083), float3(1.0), ink);
      colour = mix(colour, paper, coverage);
    }
  }

  float tubeMask = 1.0 - smoothstep(-aa, aa, ringD);
  float angle = mod(atan(p.x - ringCentre.x, ringCentre.y - p.y) + 6.283185, 6.283185);
  float gauge = (1.0 - smoothstep(uRing * 6.283185 - 0.02, uRing * 6.283185 + 0.02, angle)) * step(0.001, uRing);
  if (uRing >= 0.999) { gauge = 1.0; }
  float2 head = ringCentre + ringRadius * float2(sin(uRing * 6.283185), -cos(uRing * 6.283185));
  float headD = length(p - head);
  float capD = min(headD, length(p - ringCentre + float2(0.0, ringRadius))) - ringStroke * 0.5;
  float arcD = gauge > 0.5 ? ringD : capD;
  float arcOutside = max(arcD, 0.0);
  float arcOn = step(0.001, uRing) * 0.95 * dim;
  float ringLit = uRingLit * dim;
  float ringPower = max(ringLit, arcOn * (1.0 - smoothstep(-aa, aa, arcD)));
  float groove = exp(-sq(ringD / 3.0));
  colour *= 1.0 - groove * 0.30 * (1.0 - min(ringPower, 1.0));
  float tubeKey = max(dot((p - ringCentre) / max(ringRadial, 0.001), float2(-0.6, -0.8)), 0.0);
  float tubeRim = exp(-sq((ringD + 0.5) / 0.65));
  float tubeCore = 0.50 + 0.50 * exp(-sq((ringRadial - ringRadius) / 1.1));
  float3 tube = float3(0.075, 0.080, 0.069) + float3(0.15, 0.17, 0.18) * tubeKey * tubeRim;
  float3 tubeEmission = mix(float3(3.6, 2.65, 0.38), float3(4.8, 4.4, 1.5),
    clamp(uRingLit, 0.0, 1.0));
  tube += tubeEmission * tubeCore * ringPower;
  colour = mix(colour, tube, tubeMask);
  float ringReach = 1.0 - smoothstep(80.0, 120.0, ringOutside);
  float arcReach = 1.0 - smoothstep(80.0, 120.0, arcOutside);
  colour += hot * 0.42 * max(ringLit * ringReach * exp(-ringOutside / 1.5), arcOn * arcReach * exp(-arcOutside / 1.5))
    + gold * 0.68 * max(ringLit * ringReach * exp(-ringOutside / 7.5), arcOn * arcReach * exp(-arcOutside / 7.5))
    + gold * 0.48 * max(ringLit * ringReach * exp(-ringOutside / 26.0), arcOn * arcReach * exp(-arcOutside / 26.0));
  colour += hot * (3.8 * exp(-headD / 2.0) + 0.40 * exp(-headD / 10.0)) * dim
    * step(0.001, uRing) * (1.0 - smoothstep(0.96, 1.0, uRing)) * (1.0 - min(uRingLit, 1.0));
  colour += hot * 0.16 * shockWave;
  return half4(half3(finish(colour, xy)), 1.0);
}
"""
    const val flash =
        """
uniform float2 centre;
uniform float scale;
uniform float flash;
half4 main(float2 xy) {
  float strength = clamp(flash, 0.0, 1.0);
  float radius = (220.0 + 900.0 * strength * strength) * max(scale, 0.001);
  float2 offset = (xy - centre) / radius;
  float alpha = strength * exp(-dot(offset, offset));
  return half4(half3(1.0, 0.992157, 0.949020) * alpha, alpha);
}
"""
}
