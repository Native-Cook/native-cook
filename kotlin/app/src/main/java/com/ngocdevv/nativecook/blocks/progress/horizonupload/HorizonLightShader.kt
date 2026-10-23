// Exact RN SkSL translation: point coordinates, constants and all light/material terms preserved.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

internal val horizonLightAgsl = """
uniform float4 rest;
uniform float4 slab;
uniform float2 viewport;
uniform float4 well;
uniform float3 bead;
uniform float closePress;
uniform float2 planet;
uniform float reach;
uniform float scale;
uniform float slabScale;
uniform float progress;
uniform float power;
uniform float pulse;
uniform float shimmer;
uniform float flare;
uniform float streak;
uniform float settled;
uniform float dawnBreak;
uniform float sweep;
uniform float sweepStrength;
uniform float opaque;

const float RADIUS = 560.0;
const float RISE = 34.0;
const float CARD_RADIUS = 28.0;
const float WELL_RADIUS = 18.0;
const float3 PAGE = float3(0.01960784, 0.06274510, 0.13333333);
const float3 HAZE = float3(0.08235294, 0.14509804, 0.36078431);
const float3 CORE = float3(0.86666667, 0.89411765, 1.00000000);
const float3 BLOOM = float3(0.54901961, 0.65098039, 0.92941176);
const float3 DEEP = float3(0.19215686, 0.28235294, 0.57254902);
const float3 PANE = float3(0.05098039, 0.07843137, 0.14901961);
const float3 WELL = float3(0.01960784, 0.06274510, 0.12941176);
const float3 LAMP = float3(0.15294118, 0.23921569, 0.62745098);
const float3 PLAIN = float3(0.04313725, 0.07058824, 0.14117647);
const float3 PLAIN_WELL = float3(0.02352941, 0.04313725, 0.09411765);
const float3 DISC = float3(0.20392157, 0.23137255, 0.28627451);

float box(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float2 normal(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
  if (q.x > 0.0 && q.y > 0.0) return s * normalize(q);
  return q.x > q.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

float coverage(float d) { return clamp(0.5 - d * scale, 0.0, 1.0); }
float edge(float d, float width) {
  return (coverage(d) - coverage(d + width)) / max(coverage(d), 0.0001);
}
float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
float bell(float x) { return exp(-x * x); }

// The lamp is reflected on the face, two points above the recess. It is a
// deep-blue source with a broad upward falloff and almost no downward spill.
float4 lamp(float2 p) {
  float2 q = (p - slab.xy) / slabScale - float2(well.x, well.y - well.w - 2.0);
  float halfWidth = mix(slab.z * 0.47, well.z, flare);
  if (abs(q.x) > slab.z || q.y < -55.0 || q.y > 5.0) return float4(0.0);
  float ends = 1.0 - smoothstep(halfWidth * 0.25, halfWidth, abs(q.x));
  float wideEnds = bell(q.x / (halfWidth * 0.82));
  float core = 1.0 - smoothstep(0.45, 1.05, abs(q.y));
  float above = bell(q.y / (q.y < 0.0 ? 20.0 : 3.5));
  float nearLine = bell(q.y / (q.y < 0.0 ? 8.0 : 2.0));
  float3 glow = float3(6.0 * nearLine, 15.0 * above - 2.0 * nearLine,
    54.0 * above - 10.0 * nearLine) / 255.0 * wideEnds;
  float tail = 1.0 - ends;
  float3 line = max(LAMP - PANE, 0.0) * float3(1.0, 1.0 + 0.60 * tail, 1.0 + 0.18 * tail);
  // The narrow line must not cut a dark seam through the wider glow at its ends.
  float lineCover = core * ends;
  float spill = 1.0 - smoothstep(2.0, 5.0, q.y);
  return float4(mix(glow, line, lineCover) * spill * power * (1.0 + 0.35 * pulse) * (1.0 + flare), lineCover);
}

// No material geometry is read here. Moving the slab cannot move the dawn.
float3 field(float2 p, float soften) {
  float3 body = float3(2.0, 6.0, 15.0) / 255.0;
  // Only the upper limb exists. Even a taller viewport cannot reveal a hoop.
  if (p.y >= planet.y) return body;
  float2 radial = p - planet;
  float ring = length(radial) - RADIUS;
  float apexY = rest.y + rest.w - RISE;
  float3 sky = mix(float3(2.0, 7.0, 20.0) / 255.0, PAGE,
    smoothstep(0.0, viewport.y * 0.52, p.y));
  float3 color = mix(body, sky, smoothstep(-1.0, 1.0, ring));
  float arc = abs(atan(radial.x, -radial.y)) * RADIUS;
  // A sparse, still starfield: one faint point in about a third of the
  // 26 pt cells, fading out as it nears the air. It gives the sky a scale
  // and gives the lens something to slide over when the card is dragged.
  if (ring > 6.0) {
    float2 cell = floor(p / 26.0);
    float pick = hash(cell);
    if (pick > 0.66) {
      float2 spot = (cell + 0.2 + 0.6 * float2(hash(cell + 7.3), hash(cell + 3.1))) * 26.0;
      float size = 0.45 + 0.5 * hash(cell + 11.7);
      float2 away = (p - spot) / size;
      float star = exp(-dot(away, away)) * (0.10 + 0.34 * hash(cell + 5.9));
      // Seen through the flat of the pane they would read as dust on it.
      color += float3(0.80, 0.86, 1.0) * star * smoothstep(20.0, 150.0, ring) * (1.0 - min(soften * 2.1, 1.0));
    }
  }
  // A dawn has no front line: the whole horizon brightens from the first
  // moment, strongest behind the card, and the bright span widens until it
  // runs past both edges of the screen.
  float span = reach * (0.22 + 1.25 * progress);
  float lit = exp(-arc * arc / (span * span)) * smoothstep(0.0, 0.045, progress);
  lit = mix(lit, 1.0, 0.55 * settled);
  float strength = power * (1.0 + 0.15 * settled);
  float focus = 1.0 - 0.28 * smoothstep(0.0, reach, arc);
  // One band leaves the apex per chunk. Its envelope has no oscillation:
  // a soft attack, then it fades as its arc-length position moves outward.
  float waveAge = 1.0 - pulse;
  float wave = bell((arc - waveAge * reach) / 34.0) * pulse * smoothstep(0.0, 0.1, waveAge);

  if (ring > 0.0) {
    float quietAir = exp(-ring / 14.0) * (0.012 + 0.055 * power);
    color += HAZE * quietAir;
    if (lit > 0.0001) {
      float drift = 1.0 + 0.04 * sin(arc / 75.0 - shimmer * 6.2831853);
      float airHeight = (46.0 + 84.0 * progress) * (0.35 + 0.65 * sqrt(lit)) * drift * (1.0 + 0.25 * wave);
      // Nested exponential scales make a stratified atmosphere, with no
      // detached fog bank or blur-shaped halo above the planet.
      color += strength * lit * focus * (
        BLOOM * 0.46 * exp(-ring / 9.0) +
        DEEP * 0.80 * exp(-ring / (airHeight * 0.34)) +
        HAZE * 0.70 * exp(-ring / (airHeight * 0.75)) +
        HAZE * 0.30 * exp(-ring / (airHeight * 1.9)));
    }
  } else {
    float inset = -ring;
    if (inset < 180.0) {
      float sheen = exp(-inset / 60.0) * (1.0 - smoothstep(110.0, 170.0, p.y - apexY));
      color += DEEP * 0.045 * power * (0.25 + 0.75 * progress) * sheen;
      color += DEEP * strength * lit * (0.30 * exp(-inset / 5.5) + 0.10 * exp(-inset / 30.0));
    }
  }

  if (abs(ring) < 30.0) {
    float coldLine = 1.0 - smoothstep(0.25 / scale, 0.75 / scale, abs(ring));
    // The limb never quite disappears: asleep, the planet is still there.
    color += CORE * coldLine * (0.05 + 0.09 * power) * (1.0 - lit);
    if (lit > 0.0001) {
      float coreWidth = mix(0.64, 1.45, soften);
      float core = exp(-0.5 * ring * ring / (coreWidth * coreWidth));
      float tight = exp(-0.5 * ring * ring / 25.0);
      float3 warmth = mix(CORE, float3(1.0, 0.98, 0.92), 0.10 * settled);
      // Add against the fully lit limb so a chunk still reaches the visible
      // sides early in an upload, where the widening dawn is still faint.
      color += strength * (lit + 0.55 * wave) * focus * (warmth * 0.85 * core + BLOOM * 0.36 * tight);
    }
  }

  if (progress > 0.0) {
    float sunRadius = 70.0 * (0.55 + 0.45 * sqrt(progress)) * (1.0 + 0.60 * flare) * (1.0 + 0.06 * pulse);
    float2 sunUV = (p - float2(planet.x, apexY)) / sunRadius;
    float sun = exp(-dot(sunUV, sunUV));
    float sunSide = mix(0.04, 1.0, smoothstep(-18.0, 2.0, ring));
    float heat = 0.72 * sqrt(progress) * (1.0 + 0.18 * pulse) * strength;
    color += mix(BLOOM, CORE, 0.45 * sun) * sun * sunSide * heat;
    // The aura is what the eye sees around the card: wider than the pane,
    // flatter than a circle, so the glass is plainly lit from behind.
    float2 auraUV = (p - float2(planet.x, apexY)) / (float2(250.0, 150.0) * (0.6 + 0.4 * progress) * (1.0 + 0.3 * flare));
    float aura = exp(-dot(auraUV, auraUV));
    color += mix(HAZE, DEEP, aura) * aura * sunSide * 0.62 * sqrt(progress) * strength * (1.0 + 0.6 * flare);
    // Under the limb the same light lies on the planet as a long soft
    // glint, so the lower half of the screen is a surface, not a fill.
    if (ring < 0.0) {
      float lane = exp(-0.5 * (p.x - planet.x) * (p.x - planet.x) / (120.0 * 120.0));
      // Brightest at the limb itself. An earlier version faded to nothing
      // there, so the glow peaked 25 pt inside the planet and the limb's
      // core floated above it as a separate hairline once the card rose.
      float fall = exp(ring / 150.0);
      color += DEEP * 0.26 * lane * fall * sqrt(progress) * strength;
    }
  }

  if (flare > 0.0001 && abs(ring) < 50.0) {
    float spread = reach * 1.4 * streak;
    float streakMask = 1.0 - smoothstep(spread - 25.0, spread + 25.0, arc);
    float hotLine = exp(-0.5 * ring * ring / 1.2);
    float flareGlow = bell(ring / (ring > 0.0 ? 18.0 : 6.0));
    color += power * flare * streakMask * (float3(1.0, 0.98, 0.94) * hotLine * 1.25 + BLOOM * flareGlow * 0.95);
  }
  if (dawnBreak > 0.0) {
    // Lifting the pane exposes HDR sunlight that it previously covered.
    // Roll those highlights off so the small diamond has room to shine;
    // the approved uploading field and the dark atmosphere are unchanged.
    float sunPeak = max(color.r, max(color.g, color.b));
    float sunShoulder = 0.55 + 0.30 * (1.0 - exp(-max(sunPeak - 0.55, 0.0) / 0.30));
    color *= mix(1.0, min(1.0, sunShoulder / max(sunPeak, 0.0001)), min(dawnBreak, 1.0));
    float2 diamondUV = p - float2(planet.x, apexY);
    if (abs(diamondUV.x) < 220.0 && abs(diamondUV.y) < 48.0) {
      float diamondDistance = dot(diamondUV, diamondUV);
      float diamondPoint = 2.4 * exp(-diamondDistance / 9.0) + 0.50 * exp(-diamondDistance / 144.0);
      // A short flare, not a line: 220 pt of straight hairline across a
      // curved horizon read as a rendering fault once the card had risen
      // off it. Seventy points with a steep taper stays on the limb.
      float diamondEnds = 1.0 - smoothstep(0.0, 70.0, abs(diamondUV.x));
      diamondEnds *= diamondEnds;
      float diamondStreak = diamondEnds * (0.50 * bell(diamondUV.y / 1.5) + 0.20 * bell(diamondUV.y / 5.0));
      float2 diagonalUV = float2(diamondUV.x + diamondUV.y, diamondUV.y - diamondUV.x) * 0.7071068;
      float diamondRays = 0.12 * (
        bell(diagonalUV.x / 0.75) * (1.0 - smoothstep(3.0, 26.0, abs(diagonalUV.y))) +
        bell(diagonalUV.y / 0.75) * (1.0 - smoothstep(3.0, 26.0, abs(diagonalUV.x))));
      float diamondEnergy = (diamondPoint + diamondStreak + diamondRays) * dawnBreak * power;
      // Use remaining highlight headroom, rather than clipping a wide white
      // patch. The lens and rim sample this same warm, screen-fixed reward.
      color += max(float3(1.0) - color, 0.0) * (1.0 - exp(-float3(1.0, 0.96, 0.88) * diamondEnergy));
    }
  }
  return color;
}

"""
