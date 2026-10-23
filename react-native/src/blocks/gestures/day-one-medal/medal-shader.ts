// The medal is drawn by one fragment shader: a thin stadium-shaped slab, ray-marched as a signed
// distance field, with its relief (the numeral, the rim, the lettering) pushed out of each face
// by a height map and shaded with a normal map baked from the same model. Nothing is a sprite, so
// it turns to any angle at any size and the light is recomputed on every frame.
//
// Object space: y up, z out of the front face, the medal's half-height is 1.
// One texture per face, a greyscale strip of six tiles side by side: the normal's x, the normal's
// y, the tint (the gold on the front, the engraved lettering on the back), the roughness and the
// height and writing order. Height: 0.25 is the field; a full step of 1.0 is 0.04 units. Grey
// data matters: a wide-gamut canvas (every recent iPhone) colour-converts image shaders, which bleeds
// the channels of a coloured data texture into each other; neutral greys pass through untouched.
export const MEDAL_SKSL = `
uniform shader uFront;
uniform shader uFrontNext;
uniform shader uBack;
uniform float2 uCentre;
uniform float uHalf;
uniform float3x3 uRot;
uniform float2 uTile;
uniform float2 uLight;
uniform float uGlint;
uniform float uHeat;   // 0..1: the numeral, struck and still glowing
uniform float uCharge;
uniform float uStamp;
uniform float uMorph;
uniform float3 uMetal;
uniform float3 uMetalNext;
uniform float uEngrave;
uniform float uSweep;  // where the band of light that crosses a face on landing is; past 1 it is gone

const float A = 0.5352;      // half-width over half-height: 190 by 355
const float H = 0.045;       // half-thickness
const float BEVEL = 0.016;
const float CAM = 6.9;       // a long lens: about 17 degrees top to bottom
const float RELIEF = 0.04;

// A rotation's inverse is its transpose; the columns also describe the projected outline.
float3 toObject(float3 v) {
  return float3(dot(uRot[0], v), dot(uRot[1], v), dot(uRot[2], v));
}

float3 toWorld(float3 v) {
  return uRot * v;
}

// The whole group of camera-side lamps leans together, at most 55 degrees in any direction.
float3 leanLamp(float3 v) {
  float amount = length(uLight);
  if (amount < 0.00001) { return v; }
  float3 axis = float3(-uLight.y, uLight.x, 0.0) / amount;
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
  return clamp(uv * uTile, float2(1.0), uTile - float2(1.0));
}

// Endpoint branches keep a settled medal at one tap per tile, regardless of its milestone.
float frontTap(float2 uv, float tile) {
  // Zero means unwritten space, not an earlier time. Interpolating it reveals a future outline.
  if (tile > 4.5) { uv = floor(uv) + 0.5; }
  float2 at = float2(uv.x + tile * uTile.x, uv.y);
  if (uMorph < 0.5) { return uFront.eval(at).r; }
  return uFrontNext.eval(at).r;
}

float tap(float2 uv, float tile, bool front) {
  if (front) { return frontTap(uv, tile); }
  if (tile > 4.5) { uv = floor(uv) + 0.5; }
  return uBack.eval(float2(uv.x + tile * uTile.x, uv.y)).r;
}

float tintNear(float2 uv, float2 off) {
  return frontTap(clamp(uv + off, float2(1.0), uTile - float2(1.0)), 2.0);
}

// A small clean patch has the same forging as the field. Reusing its grain avoids a polished
// numeral-shaped hole when the stamp is absent; no extra image or data channel is needed.
float2 fieldUV(float2 uv, bool front) {
  return front ? float2(180.0 + mod(uv.x, 150.0), 750.0 + mod(uv.y, 65.0))
               : float2(90.0 + mod(uv.x, 80.0), 600.0 + mod(uv.y, 220.0));
}

float existence(float order, bool front) {
  if (order < 0.001) { return 1.0; }
  if (front) {
    float formed = 1.0 - pow(1.0 - clamp(uStamp, 0.0, 1.0), 3.0);
    // Separate halves and a short blank interval forbid overlapping numerals, even in the skirt.
    if (uMorph > 0.0 && uMorph < 1.0) {
      float level = uMorph < 0.5 ? 1.08 * (1.0 - uMorph / 0.46)
                                     : 1.08 * (uMorph - 0.54) / 0.46;
      formed *= smoothstep(order - 0.04, order + 0.04, level);
    }
    return formed;
  }
  if (uEngrave >= 1.0) { return 1.0; }
  if (uEngrave <= 0.0) { return 0.0; }
  return smoothstep(order - 0.02, order, uEngrave);
}

float heatGrain(float2 uv) {
  return sin(uv.x * 0.061 + sin(uv.y * 0.047) * 2.0) * 0.024
       + sin(uv.x * 0.19 - uv.y * 0.081) * 0.012;
}

float chargeAt(float order, float grit) {
  if (order < 0.001 || uCharge <= 0.0) { return 0.0; }
  float uneven = (grit - 0.56) * 0.65;
  return smoothstep(-0.10, 0.10, uCharge - order + uneven);
}

float chargeNear(float2 uv, float2 off) {
  float2 at = clamp(uv + off, float2(1.0), uTile - float2(1.0));
  return chargeAt(frontTap(at, 5.0), 0.56 + heatGrain(at));
}

float slab(float3 p, float lift) {
  float2 w = float2(stadium(p.xy) + BEVEL, abs(p.z) - H - lift + BEVEL);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - BEVEL;
}

// The first march stays at one height tap. Misses leave before any writing or morph taps.
float restMap(float3 p) {
  float2 uv = faceUV(p) + float2(4.0 * uTile.x, 0.0);
  float b;
  if (p.z < 0.0) { b = uBack.eval(uv).r; }
  else if (uMorph >= 0.5) { b = uFrontNext.eval(uv).r; }
  else { b = uFront.eval(uv).r; }
  float inside = smoothstep(-0.012, -0.05, stadium(p.xy));
  return slab(p, (b - 0.25) * RELIEF * inside);
}

float map(float3 p) {
  float2 uv = faceUV(p);
  bool front = p.z >= 0.0;
  float b = tap(uv, 4.0, front);
  if ((front && (uStamp < 1.0 || (uMorph > 0.0 && uMorph < 1.0))) || (!front && uEngrave < 1.0)) {
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
  float rim = lamp(d, normalize(float3(0.86 + uLight.x * 1.8, 0.12, -0.42)), mix(0.14, 0.50, rough));
  float stripWidth = 0.10 + rough * 0.45;
  float strip = exp(-squared((d.y - 0.80) / stripWidth)) * smoothstep(-0.25, 0.45, d.z);
  float sky = mix(0.006, 0.03, d.y * 0.5 + 0.5);
  return float3(1.0, 0.975, 0.94) * fill * 0.62 / (0.6 + rough * 1.6)
       + float3(1.0, 0.965, 0.91) * key * (2.3 + uGlint) / (0.6 + rough * 1.6)
       + float3(1.0, 0.88, 0.66) * flash * mix(1.7 / (0.5 + rough * 2.4), 2.6, brushed)
       + float3(1.0, 0.98, 0.95) * strip * 0.22 / (0.7 + rough * 1.4)
       + float3(0.86, 0.91, 1.0) * rim * 0.30 / (0.7 + rough * 1.2)
       + float3(sky);
}

half4 main(float2 xy) {
  float2 q = (xy - uCentre) / uHalf;
  if (length(q) > 1.9) { return half4(0.0); }

  // Project the stadium's spine and disk, with thickness keeping the halo smooth edge-on.
  // The disk's foreshortened axis is the projected face normal, even when the spine rolls flat.
  float2 projected = float2(q.x, -q.y);
  float2 spine = uRot[1].xy * (1.0 - A);
  float along = clamp(dot(projected, spine) / max(dot(spine, spine), 0.00001), -1.0, 1.0);
  float2 radial = projected - spine * along;
  float2 shortAxis = uRot[2].xy;
  float shortLength = length(shortAxis);
  shortAxis = shortLength > 0.00001 ? shortAxis / shortLength : float2(1.0, 0.0);
  float narrow = A * abs(uRot[2].z) + H * shortLength;
  float2 radialAxes = float2(dot(radial, shortAxis), dot(radial, float2(-shortAxis.y, shortAxis.x)));
  float radialLength = length(radialAxes);
  float ellipseRadius = radialLength / max(length(radialAxes / float2(max(narrow, H), A)), 0.00001);
  float away = radialLength - ellipseRadius;
  if (radialLength < 0.00001) { away = -narrow; }
  float warm = max(uHeat, uCharge * 0.22);
  float reach = exp(-max(away, 0.0) * mix(10.0, 5.2, warm));
  float fadeOut = smoothstep(1.9, 1.4, length(q));
  float sideLight = 1.0 + 0.85 * dot(projected / max(length(projected), 0.1), uLight);
  float glow = reach * fadeOut * max(uRot[2].z, 0.0) * (0.07 + warm) * sideLight;
  float3 halo = mix(float3(1.0, 0.50, 0.12), float3(1.0, 0.80, 0.52), warm) * glow;
  float haloAlpha = min(glow * 0.25, 1.0);

  float3 eye = float3(0.0, 0.0, CAM);
  float3 ray = normalize(float3(q.x, -q.y, 0.0) - eye);
  float3 ro = toObject(eye);
  float3 rd = toObject(ray);

  // A sphere around the rotated slab bounds its perspective projection, including the relief.
  if (length(q) > 1.18) { return half4(half3(halo), half(haloAlpha)); }

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
  float px = 1.0 / uHalf;
  float edge = hit ? 1.0 : 1.0 - smoothstep(0.0, px * 1.25, nearest);

  if (edge <= 0.0) { return half4(half3(halo), half(haloAlpha)); }

  // Relief changes are small relative to the slab. Correct a found surface along the same ray,
  // in either direction, instead of multiplying texture reads throughout the empty-space march.
  bool changing = p.z >= 0.0 ? (uStamp < 1.0 || (uMorph > 0.0 && uMorph < 1.0)) : uEngrave < 1.0;
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
  if ((front && (uStamp < 1.0 || uCharge > 0.0 || (uMorph > 0.0 && uMorph < 1.0))) || (!front && uEngrave < 1.0)) {
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
  if (front && uMorph > 0.0 && uMorph < 1.0) {
    moltenAmount = sin(3.141593 * uMorph) * uStamp;
    nm.xy *= 1.0 - 0.5 * moltenAmount * made;
    nm = normalize(nm);
  }
  float3 detail = front ? nm : float3(-nm.x, nm.y, -nm.z);
  // Off the gold the relief is shaded at half strength: the rim reads as a shoulder, not a drawn line.
  detail.xy *= front ? mix(0.5, 1.0, tint) : 1.0;
  detail = normalize(detail);
  n = normalize(mix(n, detail, facing));

  // Materials. The edge is the front's iron.
  float3 iron = float3(0.024, 0.024, 0.027);
  float3 gold = uMorph < 0.5 ? uMetal : uMetalNext;
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
  colour += f0 * 0.16 * min(length(uLight), 1.0);

  // A travelling softbox has a spatial falloff as well as an angular reflection: its long
  // horizontal highlight sweeps the numeral, while the opposed side lamps trade the rim.
  float stripeY = -0.12 + uLight.y * 0.40;
  float fingerBand = exp(-squared((toWorld(p).y - stripeY) / 0.14));
  float rimSide = smoothstep(0.32, 0.53, abs(p.x));
  colour += facing * tint * gold * fingerBand * (0.45 + 0.7 * length(uLight));
  colour += float3(0.065, 0.059, 0.048) * rimSide
    * (0.25 + 1.75 * max(0.0, sign(p.x) * uLight.x));

  // A gentle shoulder: the flash may clip towards white, the resting tones are left as they are.
  // A band of light crosses the face as it lands, brighter on the gold.
  float band = exp(-squared((p.x + p.y * 0.32 - uSweep) / 0.085));
  colour += facing * band * float3(1.0, 0.96, 0.88) * (0.22 + 1.5 * (front ? tint : 0.25));

  // Struck: the numeral glows from red through orange to near white, and lights the iron round it.
  if (front && uHeat > 0.001) {
    float3 ember = mix(float3(1.0, 0.26, 0.03), float3(1.0, 0.88, 0.66), uHeat * uHeat);
    colour += tint * facing * uHeat * ember * (0.7 + 6.0 * uHeat);
    float around = (tintNear(uv, float2(16.0, 0.0)) + tintNear(uv, float2(-16.0, 0.0)) +
                    tintNear(uv, float2(0.0, 16.0)) + tintNear(uv, float2(0.0, -16.0)) +
                    tintNear(uv, float2(11.0, 11.0)) + tintNear(uv, float2(-11.0, 11.0)) +
                    tintNear(uv, float2(11.0, -11.0)) + tintNear(uv, float2(-11.0, -11.0))) / 8.0;
    colour += (1.0 - tint) * facing * around * uHeat * float3(1.0, 0.34, 0.05) * 1.6;
  }

  if (front && uCharge > 0.0) {
    float drifted = order + heatGrain(uv);
    float climbed = chargeAt(order, 0.56 - heatGrain(uv));
    float behind = uCharge - drifted;
    float crest = exp(-squared(behind / 0.14));
    float breathing = 0.92 + 0.08 * sin(uCharge * 28.0 + grit * 31.0);
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

  if (!front && uEngrave > 0.0 && uEngrave < 1.0 && order > 0.001) {
    float age = uEngrave - order;
    float pointHeat = exp(-squared(age / 0.012));
    float tailHeat = smoothstep(-0.02, 0.0, age) * (1.0 - smoothstep(0.0, 0.06, age));
    // Dim in the dilation skirt, so the hot point still resolves to a stroke at phone size.
    float stroke = 0.18 + tint * 0.82;
    colour += facing * stroke * (pointHeat * float3(5.0, 2.4, 0.65) + tailHeat * float3(0.9, 0.16, 0.018));
  }

  colour = colour / (1.0 + colour * 0.22);
  colour = pow(max(colour, float3(0.0)), float3(0.4545));
  return half4(half3(colour * edge + halo * (1.0 - edge)), half(edge + haloAlpha * (1.0 - edge)));
}
`;
