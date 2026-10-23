import { horizonLayout, horizonSize as S, horizonTone as T } from "./horizon-palette";
import { HERO, part } from "./horizon-state";

// Share geometry and the completion clock with the proof renderer. Distances
// stay in points; only grain and hairline coverage use the device scale.
const DAWN_MARGIN = 20;

export function horizonLightGeometry(width: number, card: { x: number; y: number; width: number }, scale: number, height: number) {
  const { panel, close } = horizonLayout(card.width);
  const radius = S.horizonRadius;
  const rest = [card.x + card.width / 2, card.y + S.cardHeight / 2, card.width / 2, S.cardHeight / 2];
  return {
    // The scene is anchored to this resting rectangle. Only slab is displaced.
    rest,
    slab: [...rest],
    viewport: [width, height],
    well: [panel.x + panel.width / 2 - card.width / 2, panel.y + panel.height / 2 - S.cardHeight / 2, panel.width / 2, panel.height / 2],
    bead: [close.x - card.width / 2, close.y - S.cardHeight / 2, close.radius],
    planet: [width / 2, card.y + S.cardHeight - S.horizonRise + radius],
    reach: radius * Math.asin(Math.min(width / (2 * radius), 1)) + DAWN_MARGIN,
    scale,
  };
}

export function horizonLightFrame(progress: number, power: number, pulse: number, crest: number, done: number, animated: boolean) {
  "worklet";
  const c = animated ? crest : 0;
  const inhale = part(c, ...HERO.inhale);
  const flareAt = part(c, ...HERO.flare);
  const bell = flareAt > 0 && flareAt < 1 ? Math.sin(Math.PI * flareAt) : 0;
  const sweepAt = part(c, ...HERO.sweep);
  const sweep = sweepAt * sweepAt * (3 - 2 * sweepAt);
  const settled = Math.min(Math.max(done, 0), 1) * (animated ? part(c, ...HERO.settle) : 1);
  const diamondAt = part(flareAt, 0, 0.5);
  const diamondRise = diamondAt * diamondAt * (3 - 2 * diamondAt);
  const diamondPeak = (HERO.flare[0] + HERO.flare[1]) / 2;
  // The reward persists while done, but cannot leak into a cancelled/reset upload.
  const celebrating = c > 0 && c < HERO.settle[1];
  const dawnBreak = animated
    ? (celebrating || done > 0 ? diamondRise * (1 + 0.5 * (1 - part(c, diamondPeak, HERO.settle[1]))) : 0)
    : Math.min(Math.max(done, 0), 1);
  return {
    progress: Math.max(Math.min(Math.max(progress, 0), 1), settled),
    power: Math.max(power, 0) * (1 - 0.1 * inhale * (1 - flareAt)),
    pulse: Math.min(Math.max(pulse, 0), 1),
    flare: bell * bell,
    streak: 1 - (1 - flareAt) * (1 - flareAt) * (1 - flareAt),
    settled,
    dawnBreak,
    sweep,
    sweepStrength: part(sweepAt, 0, 0.12) * (1 - part(sweepAt, 0.84, 1)),
  };
}

const rgb = (hex: string) => `float3(${[1, 3, 5].map((i) => (parseInt(hex.slice(i, i + 2), 16) / 255).toFixed(8)).join(", ")})`;
const plainEdgeAlpha = Number(T.plainEdge.match(/,\s*([\d.]+)\)$/)?.[1] ?? 0.12);

// One screen-fixed dawn, seen through a moving lens. The face takes one
// field sample, the bevel takes two more for its chromatic split, and the
// edge takes one outside sample. The well's lower wall and the small bead
// sample locally too. No sampling loops or offscreen blur layers.
export const HORIZON_LIGHT = `
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

const float RADIUS = ${S.horizonRadius.toFixed(1)};
const float RISE = ${S.horizonRise.toFixed(1)};
const float CARD_RADIUS = ${S.cardRadius.toFixed(1)};
const float WELL_RADIUS = ${S.panelRadius.toFixed(1)};
const float3 PAGE = ${rgb(T.page)};
const float3 HAZE = ${rgb(T.haze)};
const float3 CORE = ${rgb(T.core)};
const float3 BLOOM = ${rgb(T.bloom)};
const float3 DEEP = ${rgb(T.deep)};
const float3 PANE = ${rgb(T.pane)};
const float3 WELL = ${rgb(T.well)};
const float3 LAMP = ${rgb(T.lamp)};
const float3 PLAIN = ${rgb(T.plain)};
const float3 PLAIN_WELL = ${rgb(T.plainWell)};
const float3 DISC = ${rgb(T.disc)};

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

half4 main(float2 p) {
  float2 local = (p - slab.xy) / slabScale;
  float d = box(local, slab.zw, CARD_RADIUS) * slabScale;
  float cover = coverage(d);
  float grain = hash(floor(p * scale)) - 0.5;

  if (cover <= 0.0) {
    float3 pageColor = field(p, 0.0);
    if (d < 70.0) {
      float gap = box(local - float2(0.0, 10.0 / slabScale), slab.zw, CARD_RADIUS) * slabScale;
      float shadow = exp(-0.5 * max(gap, 0.0) * max(gap, 0.0) / (14.0 * 14.0));
      // The contact shadow grounds the glass, but cannot extinguish the
      // exposed emitter itself. Its halo fades back into the normal shadow.
      float2 emitterUV = (p - float2(planet.x, rest.y + rest.w - RISE)) / 12.0;
      float emitter = min(dawnBreak, 1.0) * exp(-dot(emitterUV, emitterUV));
      // The card shades the dark sky, not the light: dimming the bloom
      // around the limb left its white core standing alone.
      float glowing = smoothstep(0.10, 0.45, max(pageColor.r, max(pageColor.g, pageColor.b)));
      pageColor *= 1.0 - 0.35 * shadow * (1.0 - emitter) * (1.0 - glowing);
    }
    return half4(clamp(pageColor + grain / 255.0, 0.0, 1.0), 1.0);
  }

  float wd = box(local - well.xy, well.zw, WELL_RADIUS) * slabScale;
  float wc = coverage(wd);
  float4 lampLight = lamp(p);
  float beadPress = clamp(closePress, 0.0, 1.0);
  float beadRadius = bead.z * (1.0 - 0.08 * beadPress);
  float2 beadUV = local - bead.xy;
  float beadDistance = length(beadUV);
  float bd = (beadDistance - beadRadius) * slabScale;
  float bc = coverage(bd);
  if (opaque > 0.5) {
    float3 solid = mix(PLAIN, float3(1.0), ${plainEdgeAlpha.toFixed(4)} * edge(d, slabScale));
    solid = mix(solid, PLAIN_WELL, wc);
    solid += lampLight.rgb * (1.0 - wc);
    float3 plainBead = mix(DISC, float3(1.0), 0.18 * edge(bd, slabScale));
    solid = mix(solid, plainBead, bc);
    if (cover < 1.0) solid = mix(field(p, 0.0), solid, cover);
    return half4(clamp(solid, 0.0, 1.0), 1.0);
  }

  float depth = max(-d / slabScale, 0.0);
  float bevel = 1.0 - clamp(depth / 16.0, 0.0, 1.0);
  float2 n = normal(local, slab.zw, CARD_RADIUS);
  float bend = cover >= 1.0 ? 26.0 * (1.0 - sqrt(max(1.0 - bevel * bevel, 0.0))) * slabScale : 0.0;
  float softness = 0.45 * (1.0 - bevel);
  float3 behind = field(p + n * bend, softness);
  if (bend >= 0.125) {
    behind.r = field(p + n * bend * 1.045, softness).r;
    behind.b = field(p + n * bend * 0.955, softness).b;
  }
  // A clear tint preserves the field's structure. Only the brightest energy
  // compresses, keeping labels readable without frosting away the limb.
  float peak = max(behind.r, max(behind.g, behind.b));
  float compress = 1.0 / (1.0 + 0.45 * max(peak - 0.45, 0.0));
  float3 transmitted = behind * float3(0.62, 0.70, 0.88) * compress;
  float3 glass = transmitted + float3(0.020, 0.022, 0.032);
  // Adapt the dynamic range rather than adding frost over bright content.
  // This also protects copy when a drag brings it closer to the sun.
  float faceEnergy = dot(glass * glass, float3(0.2126, 0.7152, 0.0722));
  float legibility = inversesqrt(max(1.0, faceEnergy / 0.19));
  transmitted *= legibility;
  glass *= legibility;

  float3 edgeFlux = float3(0.0);
  if (depth < 20.0) {
    // One additional field sample just beyond the nearest rim. This light
    // changes as the pane moves; there are no painted corner pools.
    float2 outsidePoint = p + n * (max(-d, 0.0) + 3.0);
    edgeFlux = max(field(outsidePoint, 0.0) - PAGE, 0.0);
    float edgePeak = max(edgeFlux.r, max(edgeFlux.g, edgeFlux.b));
    edgeFlux /= 1.0 + 0.65 * min(dawnBreak, 1.0) * max(edgePeak - 0.6, 0.0);
    glass += edgeFlux * (0.58 * exp(-depth / 7.0) + 0.35 * edge(d, 1.0 / scale));
  }

  float facing = dot(n, float2(-0.7071068, -0.7071068));
  float upper = max(facing, 0.0);
  float lower = max(-facing, 0.0);
  upper *= upper; upper *= upper; upper *= upper;
  lower *= lower; lower *= lower; lower *= lower;
  float specular = (0.12 + 0.43 * upper + 0.13 * lower) * (1.0 + flare);
  glass += float3(specular) * (edge(d, 0.8 * slabScale) + 0.055 * exp(-depth / 3.0));
  glass *= 1.0 - 0.035 * bell((depth - 15.0) / 2.0);

  if (wc > 0.0) {
    float wellDepth = max(-wd / slabScale, 0.0);
    float2 wn = normal(local - well.xy, well.zw, WELL_RADIUS);
    float bottomFacing = max(wn.y, 0.0);
    float3 recess = WELL + transmitted * 0.30;
    // The ceiling overhang shades the top, while the rounded lower wall
    // collects light from just beyond its edge. Both belong to the recess.
    recess *= 1.0 - 0.42 * max(-wn.y, 0.0) * (1.0 - smoothstep(0.0, 7.0, wellDepth));
    if (bottomFacing > 0.0 && wellDepth < 18.0) {
      float2 wellOutside = p + wn * (wellDepth + 2.0) * slabScale;
      float3 wellFlux = max(field(wellOutside, 0.35) - PAGE, 0.0);
      float wellPeak = max(wellFlux.r, max(wellFlux.g, wellFlux.b));
      wellFlux /= 1.0 + wellPeak;
      float innerLip = bell((wellDepth - 1.15) / 0.55);
      float innerGlow = bell(wellDepth / 8.0) * (1.0 - smoothstep(14.0, 18.0, wellDepth));
      recess += bottomFacing * (wellFlux * (0.16 * innerGlow + 0.32 * innerLip) + BLOOM * 0.06 * innerLip);
    }
    // A dark outer seam and a lighter return just inside it turn the edge.
    recess *= 1.0 - 0.36 * edge(wd, 0.65 * slabScale);
    recess += float3(0.022, 0.032, 0.055) * bell((wellDepth - 1.1) / 0.4) * (1.0 - 0.5 * bottomFacing);
    glass = mix(glass, recess, wc);
  }

  // Localized dimming under the narrow line retains its deep-blue identity
  // even when the sun is visible through the glass beneath it.
  glass = mix(glass, PANE, lampLight.a * (1.0 - wc));
  glass += lampLight.rgb * (1.0 - wc * 0.35);

  if (sweepStrength > 0.0) {
    float bandCenter = mix(slab.w + 65.0, -slab.w - 65.0, sweep);
    float band = bell((local.y - bandCenter + local.x * 0.2125566) / 35.0);
    float response = (0.16 + 0.38 * bevel * bevel + 0.65 * edge(d, slabScale)) * (1.0 - 0.70 * wc);
    glass += mix(BLOOM, CORE, 0.85) * band * sweepStrength * power * response;
  }
  if (beadDistance < beadRadius + 20.0) {
    float beadShadowGap = max(length(beadUV - float2(0.0, 2.0)) - beadRadius, 0.0);
    float beadShadow = 0.30 * exp(-0.5 * beadShadowGap * beadShadowGap / 36.0);
    float3 beadPane = glass;
    glass *= 1.0 - beadShadow;
    if (bc > 0.0) {
      float beadDepth = max(beadRadius - beadDistance, 0.0);
      float2 beadNormal = beadUV / max(beadDistance, 0.0001);
      float beadFacing = dot(beadNormal, float2(-0.7071068, -0.7071068));
      float beadUpper = max(beadFacing, 0.0);
      float beadLower = max(-beadFacing, 0.0);
      // Sample a slightly smaller patch of the pane underneath this convex
      // lens. Highlights stay on the perimeter, clear of the 12 pt glyph.
      float2 beadSample = slab.xy + (bead.xy + beadUV * 0.90) * slabScale;
      float3 beadField = field(beadSample, 0.45);
      float3 beadBody = beadPane + (beadField - behind) * float3(0.62, 0.70, 0.88) * compress * legibility;
      beadBody += float3(0.05 + 0.025 * beadPress);
      beadBody *= 1.0 - 0.28 * beadLower * bell((beadDepth - 1.8) / 2.4);
      float crescent = bell((beadDepth - 0.9) / mix(1.1, 2.2, beadPress));
      beadBody += float3(0.82, 0.90, 1.0) * mix(0.48, 0.32, beadPress) * beadUpper * beadUpper * crescent;
      beadBody += float3(0.10 + 0.17 * beadUpper) * edge(bd, 0.65 * slabScale);
      if (beadDepth < 7.0 && beadNormal.y > 0.0) {
        float2 beadOutside = slab.xy + (bead.xy + beadNormal * (beadRadius + 3.0)) * slabScale;
        float3 beadFlux = max(field(beadOutside, 0.35) - PAGE, 0.0);
        beadFlux /= 1.0 + max(beadFlux.r, max(beadFlux.g, beadFlux.b));
        beadBody += beadFlux * float3(0.75, 0.88, 1.0) * 0.20 * beadNormal.y * bell(beadDepth / 3.0);
      }
      glass = mix(glass, beadBody, bc);
    }
  }
  float3 color = mix(behind, glass, cover);
  color += grain * (1.2 / 255.0);
  return half4(clamp(color, 0.0, 1.0), 1.0);
}
`;
