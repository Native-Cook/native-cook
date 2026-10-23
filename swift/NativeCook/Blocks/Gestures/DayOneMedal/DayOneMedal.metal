#include <metal_stdlib>
using namespace metal;

struct DayOneMedalUniforms {
    float4 viewport, centre, motion, strike;
    float4 rot0, rot1, rot2, light;
    float4 forge, metal, metalNext, leaf;
};
struct DayOneMedalParticle {
    float4 position, velocity, shape, fall, travel;
};
struct DayOneMedalVertex {
    float4 position [[position]];
    float2 uv;
};
struct DayOneMedalHit {
    float4 colour [[color(0)]];
    float depth [[depth(any)]];
};
vertex DayOneMedalVertex dayOneMedalFullscreen(uint id [[vertex_id]]) {
    float2 uv = float2(float((id << 1u) & 2u), float(id & 2u));
    return {float4(uv * float2(2, -2) + float2(-1, 1), 0, 1), uv};
}

struct DayOneMedalSurface {
    constant DayOneMedalUniforms &f;
    texture2d<float> uFront, uFrontNext, uBack;
    sampler linear;


const float A = 0.5352;      // half-width over half-height: 190 by 355
const float H = 0.045;       // half-thickness
const float BEVEL = 0.016;
const float CAM = 6.9;       // a long lens: about 17 degrees top to bottom
const float RELIEF = 0.04;

// A rotation's inverse is its transpose; the columns also describe the projected outline.
float3 toObject(float3 v) {
  return float3(dot(float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz)[0], v), dot(float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz)[1], v), dot(float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz)[2], v));
}

float3 toWorld(float3 v) {
  return float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz) * v;
}

// The whole group of camera-side lamps leans together, at most 55 degrees in any direction.
float3 leanLamp(float3 v) {
  float amount = length(f.light.xy);
  if (amount < 0.00001) { return v; }
  float3 axis = float3(-f.light.xy.y, f.light.xy.x, 0.0) / amount;
  float ang = min(amount, 1.0) * 0.959931;
  return v * cos(ang) + cross(axis, v) * sin(ang) + axis * dot(axis, v) * (1.0 - cos(ang));
}

float squared(float value) {
  return value * value;
}

float stadium(float2 p) {
  float2 q = float2(p.x, max(abs(p.y) - (1.0 - A), 0.0));
  return length(q) - A;
}

float2 faceUV(float3 p) {
  float2 uv = float2(p.x / (2.0 * A) + 0.5, 0.5 - p.y * 0.5);
  // The back is read from behind, so its picture runs the other way.
  if (p.z < 0.0) { uv.x = 1.0 - uv.x; }
  // Kept a pixel inside the tile, so a tap never reaches into its neighbour.
  return clamp(uv * float2(512,956), float2(1.0), float2(512,956) - float2(1.0));
}

// Endpoint branches keep a settled medal at one tap per tile, regardless of its milestone.
float frontTap(float2 uv, float tile) {
  // Zero means unwritten space, not an earlier time. Interpolating it reveals a future outline.
  if (tile > 4.5) { uv = floor(uv) + 0.5; }
  float2 at = float2(uv.x + tile * float2(512,956).x, uv.y);
  if (f.forge.z < 0.5) { return uFront.sample(linear, (at) / float2(3072,956), level(0)).r; }
  return uFrontNext.sample(linear, (at) / float2(3072,956), level(0)).r;
}

float tap(float2 uv, float tile, bool front) {
  if (front) { return frontTap(uv, tile); }
  if (tile > 4.5) { uv = floor(uv) + 0.5; }
  return uBack.sample(linear, (float2(uv.x + tile * float2(512,956).x, uv.y)) / float2(3072,956), level(0)).r;
}

float tintNear(float2 uv, float2 off) {
  return frontTap(clamp(uv + off, float2(1.0), float2(512,956) - float2(1.0)), 2.0);
}

// A small clean patch has the same forging as the field. Reusing its grain avoids a polished
// numeral-shaped hole when the stamp is absent; no extra image or data channel is needed.
float2 fieldUV(float2 uv, bool front) {
  return front ? float2(180.0 + fmod(uv.x, 150.0), 750.0 + fmod(uv.y, 65.0))
               : float2(90.0 + fmod(uv.x, 80.0), 600.0 + fmod(uv.y, 220.0));
}

float existence(float order, bool front) {
  if (order < 0.001) { return 1.0; }
  if (front) {
    float formed = 1.0 - pow(1.0 - clamp(f.forge.y, 0.0, 1.0), 3.0);
    // Separate halves and a short blank interval forbid overlapping numerals, even in the skirt.
    if (f.forge.z > 0.0 && f.forge.z < 1.0) {
      float level = f.forge.z < 0.5 ? 1.08 * (1.0 - f.forge.z / 0.46)
                                     : 1.08 * (f.forge.z - 0.54) / 0.46;
      formed *= smoothstep(order - 0.04, order + 0.04, level);
    }
    return formed;
  }
  if (f.forge.w >= 1.0) { return 1.0; }
  if (f.forge.w <= 0.0) { return 0.0; }
  return smoothstep(order - 0.02, order, f.forge.w);
}

float heatGrain(float2 uv) {
  return sin(uv.x * 0.061 + sin(uv.y * 0.047) * 2.0) * 0.024
       + sin(uv.x * 0.19 - uv.y * 0.081) * 0.012;
}

float chargeAt(float order, float grit) {
  if (order < 0.001 || f.forge.x <= 0.0) { return 0.0; }
  float uneven = (grit - 0.56) * 0.65;
  return smoothstep(-0.10, 0.10, f.forge.x - order + uneven);
}

float chargeNear(float2 uv, float2 off) {
  float2 at = clamp(uv + off, float2(1.0), float2(512,956) - float2(1.0));
  return chargeAt(frontTap(at, 5.0), 0.56 + heatGrain(at));
}

float slab(float3 p, float lift) {
  float2 w = float2(stadium(p.xy) + BEVEL, abs(p.z) - H - lift + BEVEL);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - BEVEL;
}

// The first march stays at one height tap. Misses leave before any writing or morph taps.
float restMap(float3 p) {
  float2 uv = faceUV(p) + float2(4.0 * float2(512,956).x, 0.0);
  float b;
  if (p.z < 0.0) { b = uBack.sample(linear, (uv) / float2(3072,956), level(0)).r; }
  else if (f.forge.z >= 0.5) { b = uFrontNext.sample(linear, (uv) / float2(3072,956), level(0)).r; }
  else { b = uFront.sample(linear, (uv) / float2(3072,956), level(0)).r; }
  float inside = smoothstep(-0.012, -0.05, stadium(p.xy));
  return slab(p, (b - 0.25) * RELIEF * inside);
}

float map(float3 p) {
  float2 uv = faceUV(p);
  bool front = p.z >= 0.0;
  float b = tap(uv, 4.0, front);
  if ((front && (f.forge.y < 1.0 || (f.forge.z > 0.0 && f.forge.z < 1.0))) || (!front && f.forge.w < 1.0)) {
    float order = tap(uv, 5.0, front);
    float made = existence(order, front);
    if (made < 1.0) { b = mix(tap(fieldUV(uv, front), 4.0, front), b, made); }
  }
  // The relief dies away before the outline, which keeps the bevel clean.
  float inside = smoothstep(-0.012, -0.05, stadium(p.xy));
  return slab(p, (b - 0.25) * RELIEF * inside);
}

float3 slabNormal(float3 p) {
  const float e = 0.002;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy * slab(p + k.xyy * e, 0.0) + k.yyx * slab(p + k.yyx * e, 0.0) +
                   k.yxy * slab(p + k.yxy * e, 0.0) + k.xxx * slab(p + k.xxx * e, 0.0));
}

// The room, as the reference lights it: a wide soft source at the camera that gives each face its
// resting tone, a large soft box up and to the left, a strip overhead for the bevels, a narrow
// edge light behind on the right, and a small warm lamp low on the left that the faces only catch
// near the end of their sway, which is the gold's flash. Rougher surfaces see each spread wider.
float lamp(float3 d, float3 towards, float size) {
  return exp((dot(d, towards) - 1.0) / (size * size * 0.5));
}

float3 room(float3 d, float rough, float brushed) {
  float fill = lamp(d, normalize(float3(0.06, 0.20, 0.98)), mix(0.48, 0.98, rough));
  float key = lamp(d, leanLamp(normalize(float3(-0.52, 0.60, 0.61))), mix(0.16, 0.80, rough));
  // Brushed metal smears a highlight across its grain: on the gold the low lamp is a band, wide
  // from side to side and tight from top to bottom, that slides over the numeral as it tips.
  float3 low = leanLamp(normalize(float3(-0.10, -0.17, 0.98)));
  float2 off = d.xy - low.xy;
  float band = exp(-off.x * off.x / 0.20 - off.y * off.y / 0.0042) * step(0.0, d.z);
  float flash = mix(lamp(d, low, mix(0.085, 0.42, rough)), band, brushed);
  float rim = lamp(d, normalize(float3(0.86 + f.light.xy.x * 1.8, 0.12, -0.42)), mix(0.14, 0.50, rough));
  float stripWidth = 0.10 + rough * 0.45;
  float strip = exp(-squared((d.y - 0.80) / stripWidth)) * smoothstep(-0.25, 0.45, d.z);
  float sky = mix(0.006, 0.03, d.y * 0.5 + 0.5);
  return float3(1.0, 0.975, 0.94) * fill * 0.62 / (0.6 + rough * 1.6)
       + float3(1.0, 0.965, 0.91) * key * (2.3 + f.light.z) / (0.6 + rough * 1.6)
       + float3(1.0, 0.88, 0.66) * flash * mix(1.7 / (0.5 + rough * 2.4), 2.6, brushed)
       + float3(1.0, 0.98, 0.95) * strip * 0.22 / (0.7 + rough * 1.4)
       + float3(0.86, 0.91, 1.0) * rim * 0.30 / (0.7 + rough * 1.2)
       + float3(sky);
}

DayOneMedalHit shade(float2 xy) {
  float2 q = (xy - f.centre.xy) / f.viewport.z;
  if (length(q) > 1.9) { return {float4(0), 0.99}; }

  // Project the stadium's spine and disk, with thickness keeping the halo smooth edge-on.
  // The disk's foreshortened axis is the projected face normal, even when the spine rolls flat.
  float2 projected = float2(q.x, -q.y);
  float2 spine = float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz)[1].xy * (1.0 - A);
  float along = clamp(dot(projected, spine) / max(dot(spine, spine), 0.00001), -1.0, 1.0);
  float2 radial = projected - spine * along;
  float2 shortAxis = float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz)[2].xy;
  float shortLength = length(shortAxis);
  shortAxis = shortLength > 0.00001 ? shortAxis / shortLength : float2(1.0, 0.0);
  float narrow = A * abs(float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz)[2].z) + H * shortLength;
  float2 radialAxes = float2(dot(radial, shortAxis), dot(radial, float2(-shortAxis.y, shortAxis.x)));
  float radialLength = length(radialAxes);
  float ellipseRadius = radialLength / max(length(radialAxes / float2(max(narrow, H), A)), 0.00001);
  float away = radialLength - ellipseRadius;
  if (radialLength < 0.00001) { away = -narrow; }
  float warmth = max(f.strike.x, f.forge.x * 0.22);
  float glow = exp(-max(away, 0.0) * mix(11.0, 5.2, warmth)) * (1.0 - smoothstep(1.4, 1.9, length(q)))
    * max(f.rot2.z, 0.0) * (0.23 + warmth * 0.55);
  float haloAlpha = min(glow, 0.85);
  float3 halo = mix(float3(1.25, 0.25, 0.016), float3(2.0, 0.85, 0.25), warmth) * haloAlpha;
  // The source uses its quieter display-encoded surface for the slot, flight and Reduce Motion.
  bool direct = f.leaf.w > 0.5;
  if (direct) {
    float reach = exp(-max(away, 0.0) * mix(10.0, 5.2, warmth));
    float fadeOut = 1.0 - smoothstep(1.4, 1.9, length(q));
    float sideLight = 1.0 + 0.85 * dot(projected / max(length(projected), 0.1), f.light.xy);
    glow = reach * fadeOut * max(f.rot2.z, 0.0) * (0.07 + warmth) * sideLight;
    halo = mix(float3(1.0, 0.50, 0.12), float3(1.0, 0.80, 0.52), warmth) * glow;
    haloAlpha = min(glow * 0.25, 1.0);
  }

  float3 eye = float3(0.0, 0.0, CAM);
  float3 ray = normalize(float3(q.x, -q.y, 0.0) - eye);
  float3 ro = toObject(eye);
  float3 rd = toObject(ray);

  // A sphere around the rotated slab bounds its perspective projection, including the relief.
  if (length(q) > 1.18) { return {float4(halo, haloAlpha), 0.99}; }

  float t = CAM - 1.12;
  float nearest = 1.0;
  bool hit = false;
  float3 p = ro;
  for (int i = 0; i < 44; i++) {
    p = ro + rd * t;
    float d = restMap(p);
    nearest = min(nearest, d);
    if (d < 0.0007) { hit = true; break; }
    t += d * 0.86;
    if (t > CAM + 1.12) { break; }
  }

  // One pixel in object units, for the silhouette's edge.
  float px = f.viewport.w / f.viewport.z;
  float edge = hit ? 1.0 : 1.0 - smoothstep(0.0, px * 1.25, nearest);

  if (edge <= 0.0) { return {float4(halo, haloAlpha), 0.99}; }

  // Relief changes are small relative to the slab. Correct a found surface along the same ray,
  // in either direction, instead of multiplying texture reads throughout the empty-space march.
  bool changing = p.z >= 0.0 ? (f.forge.y < 1.0 || (f.forge.z > 0.0 && f.forge.z < 1.0)) : f.forge.w < 1.0;
  if (hit && changing) {
    for (int k = 0; k < 10; k++) {
      float correction = map(p);
      if (abs(correction) < 0.00015) { break; }
      t += correction * 0.86;
      p = ro + rd * t;
    }
  }

  float3 n = slabNormal(p);
  float2 uv = faceUV(p);
  float facing = smoothstep(0.55, 0.92, abs(n.z)) * smoothstep(-0.006, -0.035, stadium(p.xy));
  bool front = p.z >= 0.0;

  float2 tilt = float2(tap(uv, 0.0, front), tap(uv, 1.0, front)) * 2.0 - 1.0;
  float3 nm = float3(tilt, sqrt(max(1.0 - dot(tilt, tilt), 0.0)));
  float tint = tap(uv, 2.0, front);
  float grit = tap(uv, 3.0, front);
  float ink = tint;
  float order = 0.0;
  if ((front && (f.forge.y < 1.0 || f.forge.x > 0.0 || (f.forge.z > 0.0 && f.forge.z < 1.0))) || (!front && f.forge.w < 1.0)) {
    order = tap(uv, 5.0, front);
  }
  float made = existence(order, front);
  if (made < 1.0) {
    float2 clean = fieldUV(uv, front);
    float2 grainTilt = float2(tap(clean, 0.0, front), tap(clean, 1.0, front)) * 2.0 - 1.0;
    float3 grainNormal = float3(grainTilt, sqrt(max(1.0 - dot(grainTilt, grainTilt), 0.0)));
    nm = normalize(mix(grainNormal, nm, made));
    grit = mix(tap(clean, 3.0, front), grit, made);
    tint *= made;
  }
  float moltenAmount = 0.0;
  if (front && f.forge.z > 0.0 && f.forge.z < 1.0) {
    moltenAmount = sin(3.141593 * f.forge.z) * f.forge.y;
    nm.xy *= 1.0 - 0.5 * moltenAmount * made;
    nm = normalize(nm);
  }
  float3 detail = front ? nm : float3(-nm.x, nm.y, -nm.z);
  // Off the gold the relief is shaded at half strength: the rim reads as a shoulder, not a drawn line.
  detail.xy *= front ? mix(direct ? 0.5 : 0.85, 1.0, tint) : 1.0;
  detail = normalize(detail);
  n = normalize(mix(n, detail, facing));

  // Materials. The edge is the front's iron.
  float3 iron = float3(0.024, 0.024, 0.027);
  float3 gold = f.forge.z < 0.5 ? f.metal.xyz : f.metalNext.xyz;
  float3 satin = float3(0.062, 0.062, 0.066);
  float3 f0 = iron;
  float rough = 0.46;
  if (front) {
    f0 = mix(iron, gold, tint * facing);
    rough = mix(rough, grit, facing);
  } else {
    f0 = mix(iron, satin + float3(0.24) * tint, facing);
    rough = mix(rough, grit, facing);
  }

  float3 nw = toWorld(n);
  float3 view = -ray;
  float nv = clamp(dot(nw, view), 0.0, 1.0);
  float3 fresnel = f0 + (float3(1.0) - f0) * pow(1.0 - nv, 5.0) * (1.0 - rough * 0.7);
  float3 colour = fresnel * room(reflect(ray, nw), rough, front ? tint * facing : 0.0);
  // The lights hang above it: the foot of the medal sits in less of them than its head.
  colour *= mix(0.52, 1.0, smoothstep(-1.05, 0.55, toWorld(p).y));
  // What little the metal scatters, so shadowed faces are not pure black.
  float3 keyDir = leanLamp(normalize(float3(-0.52, 0.60, 0.61)));
  colour += f0 * 0.08 * (0.25 + max(dot(nw, keyDir), 0.0));
  // A broad bounce remains as the camera fill leans away, preserving the iron at the extremes.
  colour += f0 * 0.16 * min(length(f.light.xy), 1.0);

  // A travelling softbox has a spatial falloff as well as an angular reflection: its long
  // horizontal highlight sweeps the numeral, while the opposed side lamps trade the rim.
  float stripeY = -0.12 + f.light.xy.y * 0.40;
  float fingerBand = exp(-squared((toWorld(p).y - stripeY) / 0.14));
  float rimSide = smoothstep(0.32, 0.53, abs(p.x));
  colour += facing * tint * gold * fingerBand * (0.45 + 0.7 * length(f.light.xy));
  colour += float3(0.065, 0.059, 0.048) * rimSide
    * (0.25 + 1.75 * max(0.0, sign(p.x) * f.light.xy.x));

  // A gentle shoulder: the flash may clip towards white, the resting tones are left as they are.
  // A band of light crosses the face as it lands, brighter on the gold.
  float band = exp(-squared((p.x + p.y * 0.32 - f.strike.y) / 0.085));
  colour += facing * band * float3(1.0, 0.96, 0.88) * (0.22 + 1.5 * (front ? tint : 0.25));

  // Struck: the numeral glows from red through orange to near white, and lights the iron round it.
  if (front && f.strike.x > 0.001) {
    float3 ember = mix(float3(1.0, 0.26, 0.03), float3(1.0, 0.88, 0.66), f.strike.x * f.strike.x);
    colour += tint * facing * f.strike.x * ember * (0.7 + (direct ? 6.0 * f.strike.x : 24.0 * f.strike.x * f.strike.x * f.strike.x));
    float around = (tintNear(uv, float2(16.0, 0.0)) + tintNear(uv, float2(-16.0, 0.0)) +
                    tintNear(uv, float2(0.0, 16.0)) + tintNear(uv, float2(0.0, -16.0)) +
                    tintNear(uv, float2(11.0, 11.0)) + tintNear(uv, float2(-11.0, 11.0)) +
                    tintNear(uv, float2(11.0, -11.0)) + tintNear(uv, float2(-11.0, -11.0))) / 8.0;
    colour += (1.0 - tint) * facing * around * f.strike.x * float3(1.0, 0.34, 0.05) * 1.6;
  }

  if (front && f.forge.x > 0.0) {
    float drifted = order + heatGrain(uv);
    float climbed = chargeAt(order, 0.56 - heatGrain(uv));
    float behind = f.forge.x - drifted;
    float crest = exp(-squared(behind / 0.14));
    float breathing = 0.92 + 0.08 * sin(f.forge.x * 28.0 + grit * 31.0);
    float3 warmMetal = mix(float3(0.55, 0.012, 0.001), float3(1.0, 0.23, 0.015), exp(-max(behind, 0.0) * 5.0));
    warmMetal = mix(warmMetal, float3(1.0, 0.87, 0.65), crest);
    colour += facing * climbed * warmMetal * (0.65 + crest * 1.7) * breathing * ink;
    float beside = (chargeNear(uv, float2(14.0, 0.0)) + chargeNear(uv, float2(-14.0, 0.0)) +
                    chargeNear(uv, float2(0.0, 14.0)) + chargeNear(uv, float2(0.0, -14.0))) * 0.25;
    colour += facing * (1.0 - climbed) * beside * float3(0.025, 0.004, 0.0005);
  }

  if (moltenAmount > 0.0) {
    // The blank interval retains diffuse heat, never either numeral's outline.
    float bath = exp(-squared(squared(p.x / 0.28)) - squared(squared((p.y - 0.04) / 0.62)));
    colour += facing * moltenAmount * bath * float3(0.48, 0.07, 0.007);
    colour += facing * tint * moltenAmount * float3(3.8, 2.0, 0.65);
  }

  if (!front && f.forge.w > 0.0 && f.forge.w < 1.0 && order > 0.001) {
    float age = f.forge.w - order;
    float pointHeat = exp(-squared(age / 0.012));
    float tailHeat = smoothstep(-0.02, 0.0, age) * (1.0 - smoothstep(0.0, 0.06, age));
    // Dim in the dilation skirt, so the hot point still resolves to a stroke at phone size.
    float stroke = 0.18 + tint * 0.82;
    colour += facing * stroke * (pointHeat * float3(5.0, 2.4, 0.65) + tailHeat * float3(0.9, 0.16, 0.018));
  }

  if (direct) {
    colour = colour / (1.0 + colour * 0.22);
    colour = pow(max(colour, float3(0.0)), float3(0.4545));
  }
  return {float4(colour * edge + halo * (1.0 - edge), edge + haloAlpha * (1.0 - edge)), (CAM - toWorld(p).z) / 12.0};
}
};
fragment DayOneMedalHit dayOneMedalSurface(DayOneMedalVertex in [[stage_in]],
    constant DayOneMedalUniforms &f [[buffer(0)]],
    texture2d<float> front [[texture(0)]], texture2d<float> next [[texture(1)]],
    texture2d<float> back [[texture(2)]], sampler linear [[sampler(0)]]) {
    DayOneMedalSurface surface{f, front, next, back, linear};
    return surface.shade(in.position.xy);
}

constant float A = 0.5352;
constant float H = 0.045;
constant float CAM = 6.9;
float2 dayOneMedalTurn(float2 v, float a) {
    return float2(cos(a)*v.x-sin(a)*v.y, sin(a)*v.x+cos(a)*v.y);
}
float3 dayOneMedalToObject(constant DayOneMedalUniforms &f, float3 v) {
    return float3(dot(f.rot0.xyz,v),dot(f.rot1.xyz,v),dot(f.rot2.xyz,v));
}
float3 dayOneMedalToWorld(constant DayOneMedalUniforms &f, float3 v) {
    return float3x3(f.rot0.xyz,f.rot1.xyz,f.rot2.xyz)*v;
}
float dayOneMedalStadium(float2 p) {
    return length(float2(p.x,max(abs(p.y)-(1.0-A),0.0)))-A;
}
float dayOneMedalLamp(float3 direction, float3 towards, float size) {
    return exp((dot(direction,towards)-1.0)/(size*size*0.5));
}
kernel void dayOneMedalSimulate(uint3 gid [[thread_position_in_grid]], constant DayOneMedalUniforms &f [[buffer(0)]], device DayOneMedalParticle *particles [[buffer(1)]]) {

  auto id = gid.x;
  if (id >= uint(f.centre.w)) { return; }
  auto leaf = particles[id];
  auto dt = f.centre.z;
  if (dt == 0.0 && f.strike.z == 0.0) { return; }
  auto clock = f.motion.w;
  auto old = leaf.position.xyz;
  auto p = old;
  auto radius = max(length(p.xz), 0.001);
  // A broad intake feeds a narrow orbit. The nearest leaves acquire the most momentum.
  auto proximity = exp(-radius * radius / 5.0 - p.y * p.y / 5.0);
  auto spin = f.motion.z;
  auto air = leaf.velocity.w * exp(-dt / 1.2);
  air = clamp(air + spin * proximity * dt * 6.5, -24.0, 24.0);
  auto capture = smoothstep(1.2, 8.0, abs(air));
  auto powered = smoothstep(0.0, 6.0, abs(spin));
  auto radial = p.xz / radius;
  // Slightly tilted helical lanes keep the front/back orbit readable under projection.
  // This changes actual positions, not a painted screen-space halo.
  auto orbitRadius = 1.03 + 0.30 * leaf.position.w;
  auto inward = (orbitRadius - radius) * 4.0 * powered;
  auto release = 0.55 * (1.0 - powered);
  p = float3(p.xz.x + radial.x * (inward + release) * capture * dt, p.y,
    p.xz.y + radial.y * (inward + release) * capture * dt);
  auto orbit = dayOneMedalTurn(p.zx, air * dt / (0.65 + radius * 0.4));
  p = float3(orbit.y, p.y, orbit.x);
  auto tiltChange = 0.27 * (p.x - old.x) + 0.42 * (p.z - old.z);
  p.y += tiltChange * capture;
  auto lane = floor(leaf.position.w * 3.0) - 1.0;
  auto laneY = lane * 0.72 + 0.27 * p.x + 0.42 * p.z + 0.17 * sin(leaf.shape.z);
  p.y += (laneY - p.y) * (1.0 - exp(-dt * 4.5 * capture * powered));
  auto drift = leaf.fall.y * leaf.fall.z * cos(clock * leaf.fall.z + leaf.shape.z);
  // A rising heat edge launches a shell. Holding/cooling heat never adds momentum.
  auto kick = leaf.velocity.xyz * exp(-dt / 0.48);
  auto fromCentre = p + float3(0.0001);
  kick += normalize(fromCentre) * f.strike.z * 9.0
    * exp(-dot(p, p) / 4.0);
  auto rain = float3(drift, -leaf.fall.x, 0.0) * (1.0 - capture * 0.94 * powered);
  p += (rain + kick) * dt;
  auto previousLocal = dayOneMedalToObject(f, old);
  auto local = dayOneMedalToObject(f, p);
  auto clearance = max(leaf.shape.x, leaf.shape.y) * 0.65 + 0.015;
  auto thickness = H + 0.035 + clearance;
  auto crossed = previousLocal.z * local.z < 0.0;
  auto fraction = clamp(abs(previousLocal.z) / max(abs(local.z - previousLocal.z), 0.00001), 0.0, 1.0);
  auto crossing = mix(previousLocal, local, fraction);
  auto sweptHit = crossed && dayOneMedalStadium(crossing.xy) < clearance;
  if ((abs(local.z) < thickness && dayOneMedalStadium(local.xy) < clearance) || sweptHit) {
    if (sweptHit) { local = float3(crossing.xy, local.z); }
    auto cap = float2(0.0, clamp(local.y, -(1.0 - A), 1.0 - A));
    auto outward = local.xy - cap;
    if (length(outward) < 0.0001) { outward = float2(select(-1.0, 1.0, leaf.position.w > 0.5), 0.0); }
    outward = normalize(outward);
    auto side = select(-1.0, 1.0, local.x + (leaf.position.w - 0.5) * 0.01 >= 0.0);
    outward = dayOneMedalTurn(outward, -side * dt * 0.65);
    local = float3(cap + outward * (A + clearance), local.z);
    p = dayOneMedalToWorld(f, local);
  }
  if (abs(p.z) > 2.4) { p.z = clamp(p.z, -2.4, 2.4); kick.z = 0.0; }
  // Record travel before wrapping, so crossing a canvas edge never makes a giant streak.
  auto travel = (p - old) / max(dt, 0.0001);
  auto perspective = (CAM - p.z) / CAM;
  auto bottom = -(f.viewport.y - f.centre.y) / f.viewport.z * perspective - 0.22;
  auto top = f.centre.y / f.viewport.z * perspective + 0.22;
  if (p.y < bottom) { p.y += top - bottom; }
  auto span = f.viewport.x / f.viewport.z * perspective * 1.15;
  auto offset = (f.viewport.x * 0.5 - f.centre.x) / f.viewport.z * perspective;
  if (p.x > offset + span * 0.5) { p.x -= span; }
  if (p.x < offset - span * 0.5) { p.x += span; }
  leaf.position = float4(p, leaf.position.w);
  leaf.velocity = float4(kick, air);
  leaf.travel = float4(travel, capture);
  particles[id] = leaf;
}
struct DayOneMedalLeafVertex {
    float4 position [[position]];
    float2 uv;
    float3 radiance;
    float softness, opacity, bokeh, seed, trail;
    float2 projected;
};
vertex DayOneMedalLeafVertex dayOneMedalLeafVertex(uint vertexIndex [[vertex_id]], uint instanceIndex [[instance_id]],
    constant DayOneMedalUniforms &f [[buffer(0)]], device const DayOneMedalParticle *particles [[buffer(1)]]) {

  auto leaf = particles[instanceIndex];
  auto clock = f.motion.w;
  auto corners = array<float2, 6>{float2(-1.0,-1.0), float2(1.0,-1.0), float2(-1.0,1.0),
    float2(-1.0,1.0), float2(1.0,-1.0), float2(1.0,1.0)};
  auto corner = corners[vertexIndex % 6u];
  auto piece = vertexIndex / 6u;
  auto isTrail = piece < 2u;
  auto rotation = leaf.shape.z + leaf.shape.w * clock;
  auto tumble = leaf.position.w * 6.283185 + leaf.fall.w * clock;
  auto twist = leaf.shape.z * 1.7 + clock * 0.43;
  auto axisU = float3(cos(rotation), sin(rotation), 0.0);
  auto axisV = float3(-sin(rotation) * cos(tumble), cos(rotation) * cos(tumble), sin(tumble));
  auto normalBase = cross(axisU, axisV);
  auto u = axisU * cos(twist) + normalBase * sin(twist);
  auto v = axisV;
  auto n = normalize(cross(u, v));
  auto centre = leaf.position.xyz;
  auto w = (CAM - centre.z) / CAM;
  auto centreScreen = float2(centre.x, -centre.y) * f.viewport.z / w;
  auto bokeh = select(0.0, smoothstep(0.95, 1.65, centre.z), instanceIndex < 4u);
  // Project the true tumbling plane. Defocused foreground pieces expand into low-alpha discs.
  auto irregular = corner * float2(1.0 - 0.12 * leaf.position.w * corner.y,
    1.0 + 0.09 * sin(leaf.shape.z) * corner.x);
  auto local = (u * irregular.x * leaf.shape.x + v * irregular.y * leaf.shape.y) * 0.5;
  auto offset = float2(local.x, -local.y) * f.viewport.z / w;
  auto discRadius = leaf.shape.x * f.viewport.z / w * 1.15;
  offset = mix(offset, corner * discRadius, bokeh);
  // Two capped shutter segments curve along actual travel; the final quad is sharp foil.
  auto worldVelocity = leaf.travel.xyz;
  auto projectedVelocity = float2(worldVelocity.x + centre.x * worldVelocity.z / (CAM - centre.z),
    -worldVelocity.y - centre.y * worldVelocity.z / (CAM - centre.z)) * f.viewport.z / w;
  auto speed = length(projectedVelocity);
  auto direction = projectedVelocity / max(speed, 0.001);
  auto shutter = min(0.016, 1.5 * leaf.shape.x * f.viewport.z / w / max(speed, 0.001))
    * max(leaf.travel.w, smoothstep(0.5, 2.0, length(leaf.velocity.xyz)));
  auto blur = speed * shutter;
  auto trail = 0.0;
  auto depth = centre.z + local.z * (1.0 - bokeh);
  if (isTrail) {
    trail = (float(piece) + (corner.x + 1.0) * 0.5) * 0.5;
    auto age = shutter * trail;
    auto omega = leaf.velocity.w / (0.65 + length(centre.xz) * 0.4);
    auto acceleration = -omega * omega * float3(centre.x, 0.27 * centre.x + 0.42 * centre.z, centre.z);
    auto previous = centre - worldVelocity * age + acceleration * age * age * 0.5;
    auto previousW = (CAM - previous.z) / CAM;
    auto previousScreen = float2(previous.x, -previous.y) * f.viewport.z / previousW;
    auto halfWidth = leaf.shape.y * f.viewport.z * 0.40 / w;
    offset = previousScreen - centreScreen + float2(-direction.y, direction.x) * corner.y
      * halfWidth * (1.0 - 0.30 * trail);
    depth = previous.z;
  }
  auto centreY = f.centre.y + centreScreen.y;
  auto below = smoothstep(f.leaf.x - 24.0 * f.viewport.w,
    f.leaf.x, centreY);
  auto header = 1.0 - smoothstep(f.leaf.y + 48.0 * f.viewport.w,
    f.leaf.y + 80.0 * f.viewport.w, centreY);
  auto chip = smoothstep(f.viewport.x - 84.0 * f.viewport.w,
    f.viewport.x - 64.0 * f.viewport.w, f.centre.x + centreScreen.x)
    * (1.0 - smoothstep(f.leaf.y + 125.0 * f.viewport.w,
      f.leaf.y + 145.0 * f.viewport.w, centreY));
  auto quietArea = max(below, max(header, chip));
  offset *= mix(1.0, 0.38, below);
  auto screen = f.centre.xy + centreScreen + offset;
  auto ndc = screen / f.viewport.xy * float2(2.0, -2.0) + float2(-1.0, 1.0);
  auto viewDirection = normalize(float3(0.0, 0.0, CAM) - centre);
  auto reflected = reflect(-viewDirection, n);
  auto keyDirection = normalize(float3(-0.52, 0.60, 0.61));
  auto key = dayOneMedalLamp(reflected, keyDirection, 0.150);
  auto low = dayOneMedalLamp(reflected, normalize(float3(-0.10, -0.17, 0.98)), 0.130);
  auto diffuse = abs(dot(n, keyDirection));
  auto gold = mix(float3(0.58, 0.245, 0.045), float3(0.92, 0.64, 0.30), leaf.position.w);
  auto colour = gold * (0.35 + pow(diffuse, 0.65) * 1.10)
    + float3(1.0, 0.89, 0.66) * (key * 24.0 + low * 28.0);
  auto heat = f.strike.x;
  auto centreLight = heat * heat * 5.0 / (0.30 + dot(centre, centre))
    * (0.2 + 0.8 * abs(dot(n, normalize(-centre + float3(0.0001)))));
  auto bright = colour + float3(1.0, 0.72, 0.30) * centreLight;
  auto lit = mix(bright, min(bright, float3(0.12, 0.065, 0.025)), quietArea);
  auto softness = mix(0.035, 0.12, smoothstep(0.35, 1.7, abs(centre.z)));
  auto projected = (screen - f.centre.xy) / f.viewport.z;
  auto speck = smoothstep(1.5, 3.0, leaf.shape.x * f.viewport.z / f.viewport.w);
  auto opacity = mix(0.30, 0.92, speck) * mix(1.0, 0.16, bokeh);
  opacity *= mix(1.0, 0.18, quietArea);
  opacity *= smoothstep(f.leaf.y, f.leaf.y + 16.0 * f.viewport.w, screen.y);
  if (isTrail) { opacity *= 0.30 * smoothstep(0.5, 3.0, blur / f.viewport.w) * (1.0 - bokeh); }
  return {float4(ndc * w, (CAM - depth) / 12.0 * w, w), corner, lit, softness, opacity, bokeh, leaf.position.w, trail, projected};
}
fragment float4 dayOneMedalLeaf(DayOneMedalLeafVertex in [[stage_in]]) {

  // Mostly rectangular torn foil: unequal cut corners, never a capsule silhouette.
  auto uv = in.uv;
  auto cut = abs(uv.x + uv.y * (0.78 + in.seed * 0.24)) / 1.78;
  auto rectangle = max(max(abs(uv.x), abs(uv.y)), cut);
  auto edge = mix(rectangle, length(uv), in.bokeh);
  auto aa = max(fwidth(edge), mix(in.softness, 0.70, in.bokeh));
  auto quiet = in.projected / float2(0.58, 1.06);
  auto quietSquared = quiet * quiet;
  auto subject = exp(-dot(quietSquared, quietSquared));
  auto tail = 1.0 - smoothstep(0.05, 1.0, in.trail) * 0.94;
  auto foil = 1.0 - smoothstep(1.0 - aa, 1.0, edge);
  auto gaussian = exp(-dot(uv, uv) * 4.5) * (1.0 - smoothstep(0.7, 1.0, length(uv)));
  // Absolutely no foreground bokeh over the face; ordinary foil fades before the numeral.
  auto faceClear = smoothstep(0.08, 0.60, 1.0 - subject);
  auto alpha = select(foil, gaussian, in.bokeh > 0.0) * in.opacity * faceClear * tail;
  auto fold = 0.91 + 0.09 * smoothstep(-0.15, 0.15, uv.x + uv.y * 0.35);
  auto radiance = in.radiance * fold;
  // Scale every channel together: the foil keeps its hue below the SDR shoulder.
  // Excess energy goes to a separate, blurred light target, never the sharp quad.
  auto peak = max(radiance.r, max(radiance.g, radiance.b));
  auto core = radiance * min(1.0, 0.72 / max(peak, 0.0001));
  return float4(core * alpha, alpha);}
fragment float4 dayOneMedalLeafBloom(DayOneMedalLeafVertex in [[stage_in]]) {

  // Mostly rectangular torn foil: unequal cut corners, never a capsule silhouette.
  auto uv = in.uv;
  auto cut = abs(uv.x + uv.y * (0.78 + in.seed * 0.24)) / 1.78;
  auto rectangle = max(max(abs(uv.x), abs(uv.y)), cut);
  auto edge = mix(rectangle, length(uv), in.bokeh);
  auto aa = max(fwidth(edge), mix(in.softness, 0.70, in.bokeh));
  auto quiet = in.projected / float2(0.58, 1.06);
  auto quietSquared = quiet * quiet;
  auto subject = exp(-dot(quietSquared, quietSquared));
  auto tail = 1.0 - smoothstep(0.05, 1.0, in.trail) * 0.94;
  auto foil = 1.0 - smoothstep(1.0 - aa, 1.0, edge);
  auto gaussian = exp(-dot(uv, uv) * 4.5) * (1.0 - smoothstep(0.7, 1.0, length(uv)));
  // Absolutely no foreground bokeh over the face; ordinary foil fades before the numeral.
  auto faceClear = smoothstep(0.08, 0.60, 1.0 - subject);
  auto alpha = select(foil, gaussian, in.bokeh > 0.0) * in.opacity * faceClear * tail;
  auto fold = 0.91 + 0.09 * smoothstep(-0.15, 0.15, uv.x + uv.y * 0.35);
  auto radiance = in.radiance * fold;
  // Scale every channel together: the foil keeps its hue below the SDR shoulder.
  // Excess energy goes to a separate, blurred light target, never the sharp quad.
  auto peak = max(radiance.r, max(radiance.g, radiance.b));
  auto core = radiance * min(1.0, 0.72 / max(peak, 0.0001));
  auto excess = max(radiance - core, float3(0.0));
 return float4(excess * alpha * gaussian, 0.0);}
struct DayOneMedalStrikeVertex {
    float4 position [[position]];
    float2 uv;
    float energy, ring, radius, warmth, screenY;
};
vertex DayOneMedalStrikeVertex dayOneMedalStrikeVertex(uint vertexIndex [[vertex_id]], uint instanceIndex [[instance_id]], constant DayOneMedalUniforms &f [[buffer(0)]]) {

  auto corners = array<float2, 6>{float2(-1.0,-1.0), float2(1.0,-1.0), float2(-1.0,1.0),
    float2(-1.0,1.0), float2(1.0,-1.0), float2(1.0,1.0)};
  auto corner = corners[vertexIndex];
  auto age = f.strike.w;
  auto id = float(instanceIndex);
  auto seed = fract(sin(id * 127.1 + 19.7) * 43758.5453);
  auto seed2 = fract(sin(id * 311.7 + 71.3) * 22578.1459);
  auto t = max(0.0, age - seed * 0.06);
  auto life = 0.45 + seed2 * 0.8;
  auto energy = pow(max(0.0, 1.0 - t / life), 2.0);
  if (age < 0.0 || age < seed * 0.06 || age > life + 0.06) { energy = 0.0; }
  auto angle = id * 2.399963 + seed * 0.7;
  auto direction = float2(cos(angle), sin(angle));
  auto speed = (420.0 + 980.0 * seed2 * seed2) / 160.0;
  auto run = speed * (1.0 - exp(-3.4 * t)) / 3.4;
  auto velocity = direction * speed * exp(-3.4 * t) + float2(0.0, 3.25 * t);
  auto along = normalize(velocity);
  auto across = float2(-along.y, along.x);
  auto p = direction * run + float2(0.0, 0.10 + 1.625 * t * t);
  p += along * corner.x * (length(velocity) * 0.014 + 0.003)
    + across * corner.y * (0.0028 + seed * 0.0035);
  auto ring = 0.0;
  auto radius = 1.0;
  auto depth = 0.54;
  if (instanceIndex == 0u) {
    ring = 1.0;
    auto k = clamp(age / 0.75, 0.0, 1.0);
    radius = (70.0 + 250.0 * (1.0 - pow(1.0 - k, 3.0))) / 160.0;
    p = corner * (radius + 0.06) + float2(0.0, 0.10);
    energy = select(0.0, 0.6 * pow(1.0 - k, 2.0), age >= 0.0 && age < 0.75);
    depth = 0.98;
  }
  auto screen = f.centre.xy + p * f.viewport.z;
  auto ndc = screen / f.viewport.xy * float2(2.0, -2.0) + float2(-1.0, 1.0);
  return {float4(ndc, depth, 1.0), corner, energy, ring, radius, clamp(t / life, 0.0, 1.0), screen.y};
}
fragment float4 dayOneMedalStrike(DayOneMedalStrikeVertex in [[stage_in]], constant DayOneMedalUniforms &f [[buffer(0)]]) {

  auto edge = (1.0 - smoothstep(0.45, 1.0, abs(in.uv.y)))
    * (1.0 - smoothstep(0.65, 1.0, abs(in.uv.x)));
  if (in.ring > 0.5) {
    auto distance = abs(length(in.uv) * (in.radius + 0.06) - in.radius);
    edge = exp(-distance * distance / 0.00018);
  }
  auto stage = (1.0 - smoothstep(f.leaf.x - 24.0 * f.viewport.w,
    f.leaf.x, in.screenY)) * smoothstep(f.leaf.y + 48.0 * f.viewport.w,
    f.leaf.y + 80.0 * f.viewport.w, in.screenY);
  auto energy = in.energy * edge * stage;
  auto colour = mix(float3(1.0, 0.87, 0.59), float3(1.0, 0.16, 0.015), in.warmth);
  auto alpha = energy * 0.65;
  return float4(colour * energy * 7.0, alpha);
}

float4 dayOneMedalTent(texture2d<float> lower, sampler linear, float2 uv, float2 stepSize) {
    float4 colour = 0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float weight = float((2-abs(x))*(2-abs(y)))/16.0;
            colour += lower.sample(linear,uv+float2(x,y)*stepSize)*weight;
        }
    }
    return colour;
}
fragment float4 dayOneMedalPost(DayOneMedalVertex in [[stage_in]],
    texture2d<float> source [[texture(0)]], texture2d<float> lower [[texture(1)]],
    sampler linear [[sampler(0)]], constant int &mode [[buffer(0)]]) {
    float2 uv = in.uv;
    float2 size = float2(source.get_width(),source.get_height());
    float2 lowerSize = float2(lower.get_width(),lower.get_height());
    if (mode <= 1) {
        float3 colour = 0;
        for (int y=-1; y<=1; y+=2) {
            for (int x=-1; x<=1; x+=2) {
                float2 at=uv+float2(x,y)*0.5/size;
                float3 sampleColour=source.sample(linear,at).rgb;
                if (mode == 0) {
                    float peak=max(sampleColour.r,max(sampleColour.g,sampleColour.b));
                    float knee=pow(clamp(peak-0.55,0.0,0.9),2.0)/1.8;
                    float gain=max(peak-1.0,knee)/max(peak,0.0001);
                    colour+=(sampleColour*gain+lower.sample(linear,at).rgb)*0.25;
                } else { colour+=sampleColour*0.25; }
            }
        }
        return float4(colour,0);
    }
    if (mode == 2 || mode == 3) {
        float3 colour=0;
        float weightSum=0;
        for (int i=-32;i<=32;i++) {
            float weight=exp(-float(i*i)/288.0);
            float2 offset=(mode==2?float2(i,0):float2(0,i))/size;
            colour+=source.sample(linear,uv+offset).rgb*weight;
            weightSum+=weight;
        }
        return float4(colour/weightSum,0);
    }
    if (mode == 4) { return source.sample(linear,uv)*0.35+dayOneMedalTent(lower,linear,uv,2.5/lowerSize)*0.65; }
    float4 scene=source.sample(linear,uv);
    float3 halo=dayOneMedalTent(lower,linear,uv,1.0/lowerSize).rgb*0.15;
    float3 haloDisplay=clamp(pow(halo/(1.0+halo*0.22),float3(0.4545)),0.0,1.0);
    float haloAlpha=max(haloDisplay.r,max(haloDisplay.g,haloDisplay.b));
    float alpha=scene.a+haloAlpha*(1.0-scene.a);
    if (alpha<0.000001) { return float4(0); }
    float3 radiance=scene.rgb/max(scene.a,0.000001);
    float3 display=clamp(pow(max(radiance/(1.0+radiance*0.22),0.0),float3(0.4545)),0.0,1.0);
    return float4(display*scene.a+haloDisplay*(1.0-scene.a),alpha);
}

// The same 100 ms source-over handoff from the flight surface to the HDR scene.
fragment float4 dayOneMedalCrossfade(DayOneMedalVertex in [[stage_in]],
    texture2d<float> direct [[texture(0)]], texture2d<float> hdr [[texture(1)]],
    sampler linear [[sampler(0)]], constant float &amount [[buffer(0)]]) {
    float4 lower = direct.sample(linear, in.uv) * (1.0 - amount);
    float4 upper = hdr.sample(linear, in.uv) * amount;
    return upper + lower * (1.0 - upper.a);
}
