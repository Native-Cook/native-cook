#include <metal_stdlib>
using namespace metal;

// Pixel samplers retain the original raw data and premultiplied artwork conventions.
static float mod(float x, float y) { return x - y * floor(x / y); }
static float2 mod(float2 x, float y) { return x - y * floor(x / y); }
struct FoilPackTearImage {
    texture2d<float> image;
    bool decal;
    float4 eval(float2 p) {
        constexpr sampler linearPixel(coord::pixel, address::clamp_to_edge, filter::linear);
        if (decal && (p.x < 0 || p.y < 0 || p.x >= image.get_width() || p.y >= image.get_height())) return 0;
        return image.sample(linearPixel, p);
    }
};

struct FoilPackTearFoil {

FoilPackTearImage uMaps;
FoilPackTearImage uPrint;
float2 uLight;
float uClock;
float uCalm;
float uFront;
float uLeak;
float uMouth;
float uEdgeY;
float sq(float v) { return v * v; }
float dataAt(float2 uv, float tile) {
  return uMaps.eval(float2(clamp(uv.x, 0.6, 647.4) + tile * 648.0, clamp(uv.y, 0.6, 1127.4))).r;
}
// A sub-point footprint removes needle-width flips without flattening broad folds.
float normalAt(float2 uv, float tile) {
  return dataAt(uv, tile) * 0.40 + (dataAt(uv + float2(1.5, 0.0), tile)
    + dataAt(uv - float2(1.5, 0.0), tile) + dataAt(uv + float2(0.0, 1.5), tile)
    + dataAt(uv - float2(0.0, 1.5), tile)) * 0.15;
}
float decodeNormal(float value) {
  float byteValue = value * 255.0 - 128.0;
  return byteValue / (byteValue >= 0.0 ? 127.0 : 128.0);
}
float3 studio(float3 reflected, float rough) {
  float keyX = -0.42 + uLight.x * 0.72;
  float keyY = -0.16 + uLight.y * 0.40;
  float feather = 0.065 + rough * 0.18;
  float boxA = exp(-sq((reflected.x - keyX) / (0.20 + rough * 0.18))) * (1.0 - smoothstep(0.29, 0.29 + feather, abs(reflected.x - keyX)))
    * (1.0 - smoothstep(0.62, 0.78, abs(reflected.y - keyY)));
  float boxB = exp(-sq((reflected.x - 0.67 - uLight.x * 0.23) / (0.095 + rough * 0.14))) * (1.0 - smoothstep(0.16, 0.16 + feather, abs(reflected.x - 0.67 - uLight.x * 0.23)))
    * (1.0 - smoothstep(0.46, 0.68, abs(reflected.y + 0.12)));
  float horizon = exp(-sq((reflected.y + 0.36 + uLight.y * 0.25) / (0.034 + rough * 0.065)));
  float darkRoom = 0.12 + 0.06 * smoothstep(0.6, -0.7, reflected.y);
  return float3(0.74, 1.0, 0.95) * (darkRoom + boxA * 2.5 + boxB * 1.9)
    + float3(0.46, 0.86, 1.0) * horizon * 0.95;
}
float4 shade(float2 p) {
  if (p.x < 0.0 || p.x > 216.0 || p.y < 0.0 || p.y > 376.0) { return float4(0.0); }
  float2 uv = p * 3.0;
  float coverage = dataAt(uv, 3.0);
  if (coverage < 0.003) { return float4(0.0); }
  float2 bump = float2(decodeNormal(normalAt(uv, 0.0)), decodeNormal(normalAt(uv, 1.0)));
  float elevation = dataAt(uv, 2.0);
  float cavity = dataAt(uv, 4.0);
  float phaseClock = uClock * 6.28318530718 / 13.0;
  float breathe = sin(phaseClock * 4.0) * 0.035 * uCalm;
  bump += float2(sin(p.x * 0.024 + phaseClock * 2.0), cos(p.y * 0.016 - phaseClock * 2.0)) * breathe;
  float frontZone = exp(-sq((p.x - uFront) / 9.0) - sq((p.y - uEdgeY) / 12.0)) * step(0.5, uFront);
  bump += float2(sin((p.x - uFront) * 1.5 + p.y * 0.8), cos(p.y * 1.9)) * frontZone * 0.13;
  float seal = max(1.0 - smoothstep(23.0, 30.0, p.y), smoothstep(348.0, 354.0, p.y));
  float sidePillow = (smoothstep(176.0, 212.0, p.x) - smoothstep(40.0, 4.0, p.x)) * 0.48;
  float sealPillow = (exp(-sq((p.y - 344.0) / 18.0)) - exp(-sq((p.y - 34.0) / 18.0))) * 0.30;
  bump += float2(sidePillow, sealPillow) * (1.0 - seal);
  float3 n = normalize(float3(bump, sqrt(max(0.10, 1.0 - dot(bump, bump)))));
  float3 eye = normalize(float3((108.0 - p.x) / 650.0, (188.0 - p.y) / 650.0, 1.0));
  float3 reflected = reflect(-eye, n);
  float3 lamp = normalize(float3(-0.45 + uLight.x * 1.15, -0.7 + uLight.y * 0.9, 1.05));
  float specular = pow(max(dot(n, normalize(lamp + eye)), 0.0), 180.0);
  float grazing = pow(max(0.0, 1.0 - dot(n, eye)), 2.2);
  float filmPhase = elevation * 8.0 + reflected.x * 5.0 + uLight.x * 1.6;
  float3 tint = mix(float3(0.016, 0.25, 0.21), float3(0.026, 0.42, 0.35), clamp(elevation * 0.9, 0.0, 1.0));
  float interferenceAt = 0.5 + 0.5 * sin(filmPhase);
  float3 interference = mix(float3(0.48, 0.42, 1.0), float3(0.97, 0.84, 0.45), interferenceAt);
  float3 env = studio(reflected, 0.035);
  float3 metal = mix(tint, float3(0.78, 0.97, 0.93), grazing * 0.75);
  float occlusion = 0.78 + 0.22 * cavity;
  float pillowDark = 1.0 - min(0.45, abs(sidePillow) * 0.65 + abs(sealPillow) * 0.4);
  float3 colour = (metal * env + tint * 0.64) * occlusion * pillowDark;
  colour += env * (0.10 + grazing * 0.28);
  colour += float3(0.81, 1.0, 0.95) * specular * 0.76;
  colour += interference * (grazing * 0.50 + seal * 0.14) * (0.25 + env * 0.45);
  // The crimp reflects a narrow horizontal lamp as well as the studio's vertical boxes.
  float sealBand = exp(-sq((mod(p.y, 350.0) - 12.0 - uLight.y * 6.0 - uLight.x * 4.0) / 2.1));
  float grooves = pow(max(0.0, 0.5 + 0.5 * sin(p.x * 3.14159)), 5.0);
  colour *= 1.0 - seal * grooves * 0.24;
  colour += seal * sealBand * (0.24 + 0.76 * (1.0 - grooves)) * float3(0.64, 0.94, 0.79);
  // Crimp relief is already in the normal tile; extra stripes would alias at phone size.
  float4 ink = uPrint.eval(uv);
  float3 inkColour = float3(ink.rgb) / max(float(ink.a), 0.001);
  // Ink follows the same film normals, with a broader, warmer reflection lobe.
  float inkMetal = smoothstep(0.02, 0.25, inkColour.r - inkColour.b * 0.82);
  float3 inkEnv = studio(reflected, 0.32);
  float inkBand = exp(-sq((reflected.x + reflected.y * 0.35 + 0.18 - uLight.x * 0.65) / 0.22));
  float3 printedMetal = inkColour * (0.50 + inkEnv * 0.44) * (0.70 + cavity * 0.30);
  printedMetal += float3(1.0, 0.83, 0.66) * (inkBand * 0.38 + specular * 0.24);
  float3 printed = mix(inkColour * 0.95, printedMetal, inkMetal);
  colour = mix(colour, printed, float(ink.a));
  colour += float3(1.0, 0.93, 0.72) * frontZone * (0.13 + specular) * uLeak;
  float invited = mod(uClock + 0.08, 3.25) / 0.7;
  float invitation = uCalm * step(invited, 1.0) * exp(-sq((p.x / 216.0 - invited) / 0.065) - sq((p.y - 34.0) / 0.8));
  colour += float3(0.73, 1.0, 0.95) * invitation * 0.8;
  float contact = exp(-sq((p.x - uFront - 8.0) / 7.0)) * smoothstep(uEdgeY + 4.0, uEdgeY, p.y) * step(0.5, uFront);
  colour *= 1.0 - contact * 0.62;
  colour = max(colour / (1.0 + colour * 0.22), float3(0.0));
  return float4(float3(colour * coverage), float(coverage));
}

};

struct FoilPackTearCurl {

float uRadius;
float uHeight;
float uRoll;
float uFree;
float2 uLight;
float uLeak;
float sq(float v) { return v * v; }
float3 openEnd(float2 q, float radial, float tooth) {
  float theta = atan2(q.y, q.x);
  // 2.6 turns, relaxing by a quarter turn after detachment. The gaps are recessed darkness.
  float turns = 2.6 - uFree * 0.25;
  float winding = radial * turns - theta / 6.2831853 - uRoll * 0.014;
  float wrapAt = fract(winding);
  float wrapBody = smoothstep(0.60, 0.68, wrapAt) * (1.0 - smoothstep(0.89, 0.97, wrapAt));
  float edge = exp(-sq((wrapAt - 0.91 - tooth * 0.025) / 0.055));
  float coreFade = smoothstep(0.12, 0.29, radial);
  float3 cavity = float3(0.007, 0.022, 0.024);
  float3 teal = float3(0.018, 0.24, 0.18) * (0.52 + 0.48 * (1.0 - q.y));
  float3 cut = float3(0.72, 0.92, 0.85) * (0.64 + 0.30 * cos(theta + uLight.x));
  float3 result = mix(cavity, teal, wrapBody * coreFade);
  result += cut * edge * coreFade;
  // Only a film-thickness outer rim; the area it surrounds remains hollow.
  result += float3(0.64, 0.84, 0.77) * exp(-sq((radial - 0.985) / 0.023));
  return result;
}
float4 shade(float2 p) {
  float along = clamp(p.y / max(uHeight, 1.0), 0.0, 1.0);
  float dent = 0.30 * sin(p.y * 0.49 + uRoll * 0.13) + 0.18 * sin(p.y * 1.27);
  float rx = uRadius * (0.90 + 0.10 * along) + dent;
  float centreX = -uRadius * 0.58 + along * 1.6 + 0.20 * sin(p.y * 0.35);
  float nx = (p.x - centreX) / rx;
  float azimuth = asin(clamp(nx, -1.0, 1.0));
  float cutTooth = 1.0 - abs(fract((p.x + 30.0) / 2.8) * 2.0 - 1.0);
  float topY = 1.4 + cutTooth * 0.75;
  float bottomY = uHeight + cutTooth * (0.85 + 0.3 * sin(p.x * 2.1));
  float body = (1.0 - smoothstep(0.975, 1.025, abs(nx))) * smoothstep(topY - 0.7, topY + 0.6, p.y)
    * (1.0 - smoothstep(bottomY - 0.5, bottomY + 0.6, p.y));
  float crimpWave = sin(azimuth * rx * 3.5 + uRoll * 0.55 + sin(p.y * 0.4) * 0.22);
  float crimp = (1.0 - smoothstep(23.0, 30.0, p.y)) * crimpWave;
  float bent = nx + 0.035 * sin(p.y * 0.43 + nx * 5.0) + crimp * 0.023;
  float streakA = exp(-sq((bent + 0.48 - uLight.x * 0.20) / 0.22));
  float streakB = exp(-sq((bent - 0.28 - uLight.x * 0.16) / 0.18));
  float streakC = exp(-sq((bent - 0.76 + uLight.x * 0.12) / 0.16));
  float3 silver = float3(0.28, 0.36, 0.36) + float3(0.49, 0.54, 0.50) * streakA
    + float3(0.30, 0.40, 0.36) * streakB + float3(0.21, 0.28, 0.27) * streakC;
  silver *= 0.89 + crimp * 0.10 + 0.035 * sin(p.y * 0.61 + nx * 5.0);
  silver += float3(0.24, 0.20, 0.12) * uLeak * exp(-sq((nx + 0.75) / 0.36));
  silver += float3(0.0, 0.07, 0.045) * (1.0 - nx * nx);
  float freeEdge = 0.66 + 0.07 * sin(along * 5.0 + uRoll * 0.07);
  silver *= 1.0 - exp(-sq((nx - freeEdge) / 0.065)) * 0.60;
  silver += float3(0.71, 0.84, 0.77) * exp(-sq((nx - freeEdge - 0.085) / 0.035)) * 0.62;
  float printedLip = exp(-sq((nx - freeEdge - 0.14) / 0.037)) * (0.55 + 0.45 * sin(p.y * 0.55));
  silver = mix(silver, float3(0.018, 0.31, 0.22), printedLip * 0.80);
  float2 topUV = float2((p.x + uRadius * 0.58) / (uRadius * 0.90), (p.y - 1.4) / (uRadius * 0.48));
  float topD = length(topUV);
  float topEnd = 1.0 - smoothstep(0.98, 1.035, topD + cutTooth * 0.028);
  float2 endUV = float2((p.x + uRadius * 0.58 - 1.6) / uRadius, (p.y - uHeight) / (uRadius * 0.23));
  float endD = length(endUV);
  float endRim = exp(-sq((endD - 0.97 - cutTooth * 0.045) / 0.032)) * smoothstep(uHeight - 0.8, uHeight + 1.0, p.y);
  float coverage = max(body, max(topEnd, endRim));
  float3 result = mix(silver, openEnd(topUV, topD, cutTooth), topEnd);
  result = mix(result, float3(0.43, 0.64, 0.53), endRim * 0.7);
  float rampX = (p.x - centreX - rx * 0.65) / 11.0;
  float ramp = (1.0 - uFree) * smoothstep(-0.10, 0.10, rampX) * (1.0 - smoothstep(0.82, 1.0, rampX))
    * smoothstep(1.5, 4.0, p.y) * (1.0 - smoothstep(uHeight - 2.0, uHeight + 1.0, p.y));
  float3 rampColour = mix(float3(0.012, 0.21, 0.16), float3(0.40, 0.76, 0.63), pow(max(0.0, sin(rampX * 3.14159)), 3.0));
  result = mix(rampColour, result, coverage);
  coverage = max(coverage, ramp);
  return float4(float3(result * coverage), float(coverage));
}

};

struct FoilPackTearLip {

FoilPackTearImage uMaps;
float4 uColour;
float4 shade(float2 p) {
  if (p.x < 0.0 || p.x > 216.0) { return float4(0.0); }
  float2 uv = clamp(p * 3.0, float2(0.6), float2(647.4, 1127.4));
  float coverage = uMaps.eval(float2(uv.x + 1944.0, uv.y)).r;
  float alpha = coverage * uColour.a;
  return float4(float3(uColour.rgb * alpha), float(alpha));
}

};

struct FoilPackTearCard {

FoilPackTearImage uFace;
FoilPackTearImage uBack;
FoilPackTearImage uMaps;
float3x3 uRot;
float2 uLight;
float2 uTilt;
float uSheen;
float uLeak;
float uScale;
float sq(float v) { return v * v; }
float3 objectVector(float3 v) { return float3(dot(uRot[0], v), dot(uRot[1], v), dot(uRot[2], v)); }
float dataAt(float2 uv, float tile) {
  return uMaps.eval(float2(clamp(uv.x, 1.0, 749.0) + tile * 750.0, clamp(uv.y, 1.0, 1049.0))).r;
}
float roundCard(float2 q) {
  float2 b = abs(q) - float2(81.0, 117.0);
  return length(max(b, 0.0)) + min(max(b.x, b.y), 0.0) - 9.0;
}
float4 shade(float2 xy) {
  float camera = 900.0 / max(uScale, 0.01);
  float3 eye = objectVector(float3(0.0, 0.0, camera));
  float3 ray = objectVector(normalize(float3(xy, -camera)));
  float3 safeRay = float3(abs(ray.x) < 0.000001 ? 0.000001 : ray.x, abs(ray.y) < 0.000001 ? 0.000001 : ray.y, abs(ray.z) < 0.000001 ? 0.000001 : ray.z);
  float3 bound = float3(90.0, 126.0, 0.60);
  float3 t0 = (-bound - eye) / safeRay;
  float3 t1 = (bound - eye) / safeRay;
  float3 nearT = min(t0, t1);
  float3 farT = max(t0, t1);
  float entry = max(max(nearT.x, nearT.y), nearT.z);
  float leave = min(min(farT.x, farT.y), farT.z);
  if (leave < entry || leave < 0.0) { return float4(0.0); }
  float3 hit = eye + ray * entry;
  float rimD = roundCard(hit.xy);
  if (rimD > 0.65) { return float4(0.0); }
  float alpha = smoothstep(0.65, -0.25, rimD);
  bool side = nearT.z < max(nearT.x, nearT.y);
  if (side) {
    float edgeShine = 1.12 + 0.30 * sin(hit.y * 0.023 + uLight.y);
    return float4(float3(float3(1.0, 0.91, 0.68) * alpha * edgeShine), float(alpha));
  }
  bool back = hit.z > 0.0;
  float2 uv = float2((back ? hit.x : -hit.x) / 180.0 + 0.5, hit.y / 252.0 + 0.5) * float2(750.0, 1050.0);
  // Crop the printed face to its gold edge: no second dark plate or outer hairline.
  if (!back) { uv = float2(24.0) + uv * float2(702.0 / 750.0, 1002.0 / 1050.0); }
  float depth = dataAt(uv, 1.0);
  float2 parallax = uTilt.yx * (depth - 0.5) * float2(52.0, 58.0) * smoothstep(-5.0, -12.0, rimD);
  uv = clamp(uv + parallax, float2(1.0), float2(749.0, 1049.0));
  float4 art = back ? uBack.eval(uv) : uFace.eval(uv);
  float foil = dataAt(uv, back ? 2.0 : 0.0);
  float dx = dataAt(uv + float2(2.0, 0.0), 1.0) - dataAt(uv - float2(2.0, 0.0), 1.0);
  float dy = dataAt(uv + float2(0.0, 2.0), 1.0) - dataAt(uv - float2(0.0, 2.0), 1.0);
  float phase = uv.x * 0.020 + uv.y * 0.010 + uTilt.x * 19.0 + uTilt.y * 24.0 + uLight.x * 2.1 - uLight.y;
  float3 spectrum = 0.5 + 0.5 * cos(phase + float3(0.0, 2.094, 4.189));
  float2 grainCell = floor(uv / 2.4);
  float noise = fract(sin(dot(grainCell, float2(127.1, 311.7))) * 43758.5453);
  float fleck = smoothstep(0.965, 1.0, noise) * pow(max(0.0, sin(phase * 2.0 + noise * 30.0)), 12.0);
  float reflection = exp(-sq((hit.x / 180.0 + hit.y / 600.0 + uTilt.y * 1.8 + uLight.x * 0.19 - 0.25) / 0.12));
  float sheenAt = uv.x / 750.0 + uv.y / 1050.0 * 0.23;
  float sheenBand = exp(-sq((sheenAt - uSheen) / 0.12));
  float3 colour = float3(art.rgb);
  float luminance = dot(colour, float3(0.2126, 0.7152, 0.0722));
  float detail = smoothstep(0.035, 0.25, luminance);
  float movingBand = 0.5 + 0.5 * cos(phase * 0.58 + depth * 7.0);
  float foilBand = pow(movingBand, 6.0);
  if (back) {
    // The engraving, ring and mark catch light independently of the midnight ground.
    float engraving = smoothstep(0.22, 0.60, luminance);
    float engravingBand = exp(-sq((hit.x / 180.0 + hit.y / 340.0 + uTilt.y * 4.8 - uTilt.x * 2.4 + uLight.x * 0.3) / 0.20));
    colour *= 1.0 + detail * 0.12;
    colour += float3(1.0, 0.88, 0.58) * engraving * engravingBand * 0.62;
    colour += (float3(0.90, 0.70, 0.31) * 0.13 + spectrum * (0.045 + reflection * 0.46)) * foil;
    colour += float3(1.0, 0.69, 0.29) * exp(-sq((hit.y - 115.0) / 75.0)) * uLeak * (0.16 + foil * 0.40);
  } else {
    colour += spectrum * foil * detail * (0.08 + foilBand * 0.40);
    colour += (float3(0.65) + spectrum * 0.65) * fleck * foil * detail * 1.2;
    colour += float3(0.76, 0.91, 1.0) * reflection * detail * (0.035 + foil * 0.18);
    colour += float3(1.0, 0.95, 0.82) * sheenBand * detail * (0.10 + foil * 0.32);
    colour += float3(0.7, 0.87, 0.95) * max(0.0, -dx + dy) * 0.65;
    float border = smoothstep(-6.2, -5.4, rimD);
    float brush = 0.94 + 0.06 * sin(uv.y * 2.5 + sin(uv.x * 0.071));
    float goldSpec = exp(-sq((hit.x / 160.0 + hit.y / 310.0 + uTilt.y * 1.9 - uTilt.x * 1.2 + uLight.x * 0.35) / 0.20));
    float3 gold = float3(0.67, 0.41, 0.12) * brush + float3(0.33, 0.31, 0.19) * goldSpec;
    gold += spectrum * goldSpec * 0.16 + float3(0.17, 0.12, 0.05) * sheenBand;
    gold += float3(0.46, 0.39, 0.21) * exp(-sq((rimD + 0.8) / 0.6));
    colour = mix(colour, gold, border);
  }
  return float4(float3(colour * alpha), float(alpha));
}

};

struct FoilPackTearFrame {

FoilPackTearImage uLabel;
float uHeat;
float uRing;
float uTab;
float uClock;
float sq(float v) { return v * v; }
float roundedBox(float2 p, float2 extent, float radius) {
  float2 q = abs(p) - extent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float3 hueAt(float2 p, float hot) {
  float along = clamp((p.x - 48.0) / 580.0 + (p.y - 128.0) / 760.0, 0.0, 1.0);
  float3 cool = mix(float3(0.16, 0.88, 1.0), float3(0.45, 0.24, 1.0), along);
  float3 gold = mix(float3(0.70, 0.95, 1.0), float3(1.0, 0.75, 0.35), clamp(along * 1.8, 0.0, 1.0));
  gold = mix(gold, float3(1.0, 0.60, 0.76), smoothstep(0.78, 1.0, along) * 0.4);
  return mix(cool, gold, hot);
}
float borderTravel(float2 p) {
  // Distance from each tab end, around the bottom corner, up its side, to the top centre.
  float ax = abs(p.x - 201.0);
  if (p.y > 590.0) { return max(0.0, ax - 64.0); }
  if (p.y < 155.0) { return 537.0 + 153.0 - ax; }
  return 89.0 + 607.0 - p.y;
}
float3 glass(float2 p) {
  float d = roundedBox(p - float2(201.0, 374.0), float2(153.0, 246.0), 28.0);
  float tr = borderTravel(p);
  float head = uRing * 690.0;
  float trail = head - tr;
  float comet = (uRing > 0.001 && uRing < 0.999) ? exp(-max(trail, 0.0) / 28.0) * smoothstep(-2.0, 1.5, trail) : 0.0;
  float warmed = uRing > 0.001 && uRing < 0.999 ? smoothstep(-5.0, 5.0, trail) * uHeat : uHeat;
  float3 hue = hueAt(p, max(warmed, uHeat * 0.55));
  float band = smoothstep(0.5, -0.5, d) * smoothstep(-12.6, -11.5, d);
  float outer = exp(-sq(d / 0.85));
  float bevel = exp(-sq((d + 2.2) / 2.8));
  float inner = exp(-sq((d + 11.0) / 0.9));
  float broadGlow = exp(-abs(d + 5.0) / 13.0) * 0.13;
  float3 colour = hue * (broadGlow + band * (0.34 + bevel * 0.42) + outer * 0.32 + inner * 0.36);
  colour += float3(0.80, 0.98, 1.0) * bevel * band * 0.09;
  float pane = smoothstep(-12.0, -13.0, d);
  float diagonal = exp(-sq((p.x + p.y * 0.48 - 230.0) / 94.0));
  colour += pane * (float3(0.023, 0.031, 0.049) + float3(0.027, 0.031, 0.049) * diagonal);
  float innerD = roundedBox(p - float2(201.0, 374.0), float2(133.0, 226.0), 15.0);
  colour += hue * exp(-sq(innerD / 0.52)) * 0.35;
  colour += float3(1.0, 0.97, 0.84) * comet * exp(-abs(d + 6.0) / 3.0) * 2.3;
  float headBloom = (uRing > 0.001 && uRing < 0.999) ? exp(-sq((tr - head) / 8.0) - sq((d + 6.0) / 13.0)) : 0.0;
  colour += float3(1.0, 0.96, 0.81) * headBloom * 0.6;
  // A flared trapezoid fuses into the bottom band; its top has a polished glass bevel.
  float ty = (p.y - 590.0) / 36.0;
  float tw = mix(64.0, 86.0, clamp(ty, 0.0, 1.0));
  float td = max(abs(p.x - 201.0) - tw, max(590.0 - p.y, p.y - 626.0));
  float tab = smoothstep(0.7, -0.5, td);
  float3 tabC = mix(float3(0.032, 0.062, 0.16), float3(0.13, 0.17, 0.37), clamp(ty, 0.0, 1.0));
  tabC = mix(tabC, float3(0.20, 0.14, 0.072), uHeat * 0.75);
  tabC += hue * (exp(-abs(td) * 1.4) * 0.34 + exp(-sq((p.y - 591.0) / 0.8)) * 0.5);
  colour = mix(colour, tabC * (0.8 + uTab * 0.6), tab);
  float2 labelUV = (p - float2(141.0, 601.0)) * 3.0;
  if (labelUV.x >= -3.0 && labelUV.x <= 363.0 && labelUV.y >= -3.0 && labelUV.y <= 45.0) {
    float alpha = uLabel.eval(labelUV).a;
    float halo = (uLabel.eval(labelUV + float2(3.0, 0.0)).a + uLabel.eval(labelUV - float2(3.0, 0.0)).a +
      uLabel.eval(labelUV + float2(0.0, 3.0)).a + uLabel.eval(labelUV - float2(0.0, 3.0)).a) * 0.25;
    float3 ink = mix(float3(0.66, 0.97, 1.0), float3(1.0, 0.71, 0.97), clamp(labelUV.x / 360.0, 0.0, 1.0));
    ink = mix(ink, float3(1.0, 0.94, 0.75), uHeat * 0.72);
    colour += ink * (alpha * (0.66 + uTab * 0.34) + halo * uTab * 0.14);
  }
  return colour;
}
float4 shade(float2 p) {
  float vignette = exp(-sq((p.x - 201.0) / 210.0) - sq((p.y - 370.0) / 440.0));
  float3 page = mix(float3(0.0039, 0.0078, 0.0275), float3(0.0235, 0.0353, 0.0745), vignette);
  float2 grid = abs(mod(p - float2(1.0, 8.0), 40.0) - 20.0);
  float gridInk = max(exp(-sq(grid.x / 0.36)), exp(-sq(grid.y / 0.36))) * (1.0 - smoothstep(280.0, 590.0, p.y));
  page += float3(0.47, 0.59, 1.0) * gridInk * 0.035;
  float floorPool = exp(-sq((p.x - 201.0) / 129.0) - sq((p.y - 660.0) / 29.0));
  page += mix(float3(0.04, 0.10, 0.45), float3(0.38, 0.20, 0.06), uHeat) * floorPool * 0.37;
  if (p.y < 636.0) {
    page += glass(p);
  } else if (p.y < 786.0) {
    float fade = 0.30 * sq(max(0.0, 1.0 - (p.y - 636.0) / 150.0));
    float2 mirrorP = float2(p.x, 1272.0 - p.y);
    page += (glass(mirrorP + float2(1.4, 0.8)) + glass(mirrorP - float2(1.4, 0.8))) * 0.5 * fade;
  }
  return float4(float3(page), 1.0);
}

};

struct FoilPackTearRays {

float uHeat;
float uClock;
float uAge;
float4 shade(float2 p) {
  if (uHeat < 0.001) { return float4(0.0); }
  float2 q = p - float2(201.0, 364.0);
  float radius = length(q);
  float theta = atan2(q.y, q.x);
  float phase = uClock * 6.28318530718 / 13.0;
  float rayA = pow(max(0.0, sin(theta * 13.0 + phase)), 8.0);
  float rayB = pow(max(0.0, sin(theta * 7.0 - phase)), 14.0);
  float paneFade = (1.0 - smoothstep(132.0, 151.0, abs(q.x))) * (1.0 - smoothstep(222.0, 244.0, abs(q.y)));
  float pulse = uAge < 0.0 ? 0.0 : (1.0 - smoothstep(0.05, 0.60, uAge)) * smoothstep(0.0, 0.05, uAge);
  float settling = uAge < 0.0 ? 0.0 : 1.0 - smoothstep(0.15, 1.4, uAge);
  float rays = (rayA + rayB * 0.32) * (0.15 + settling * 0.20) * paneFade * smoothstep(20.0, 120.0, radius);
  float halo = exp(-dot(q / float2(134.0, 183.0), q / float2(134.0, 183.0)) * 0.8) * paneFade * 0.13;
  float flash = exp(-radius * radius / 24000.0) * (1.0 - smoothstep(165.0, 220.0, radius)) * pulse * 1.35;
  float alpha = (rays + halo + flash) * uHeat;
  return float4(float3((float3(1.0, 0.79, 0.43) * (rays + halo) + float3(1.0, 0.95, 0.77) * flash) * uHeat), float(alpha));
}

};

struct FoilPackTearLight {

float uFront;
float uLeak;
float uBurst;
float uClock;
float uEdgeY;
float sq(float v) { return v * v; }
float4 shade(float2 p) {
  if (uLeak < 0.001 && uBurst < 0.001) { return float4(0.0); }
  float span = max(uFront, 1.0);
  float centreX = span * 0.5;
  float upward = uEdgeY - p.y;
  float phase = uClock * 6.28318530718 / 13.0;
  float2 coreQ = float2((p.x - centreX) / (span * 0.55 + 8.0), upward / 14.0);
  float coreR = length(coreQ);
  float bloom = exp(-dot(coreQ, coreQ) * 1.8) * (1.0 - smoothstep(1.0, 1.5, coreR)) * uLeak * 0.30;
  float rise = max(upward, 0.0);
  float coneWidth = span * 0.5 + rise * 0.40 + 12.0;
  float across = (p.x - centreX) / coneWidth;
  float cone = exp(-sq(across) * 2.2) * (1.0 - smoothstep(0.75, 1.15, abs(across)));
  float beamAngle = atan2(p.x - centreX, rise + 24.0);
  float beams = pow(max(0.0, cos(beamAngle * 13.0 + sin(phase) * 0.18)), 6.0);
  beams += pow(max(0.0, cos(beamAngle * 23.0 - sin(phase) * 0.12)), 12.0) * 0.35;
  float shaft = cone * (0.24 + beams * 0.76) * smoothstep(-3.0, 8.0, upward)
    * exp(-rise / 95.0) * (1.0 - smoothstep(100.0, 182.0, rise)) * sqrt(uLeak) * 0.55;
  // An ellipse, rather than a rectangular feather, bounds the wide detachment flash.
  float flashR = length(float2((p.x - centreX) / 160.0, upward / 130.0));
  float flash = exp(-flashR * flashR * 2.4) * (1.0 - smoothstep(0.65, 1.0, flashR)) * uBurst * 0.95;
  float supportR = length(float2((p.x - 108.0) / 290.0, (p.y + 25.0) / 250.0));
  float support = 1.0 - smoothstep(0.78, 0.98, supportR);
  float alpha = (bloom + shaft + flash) * support;
  float3 colour = (float3(1.0, 0.76, 0.36) * shaft + float3(1.0, 0.93, 0.72) * (bloom + flash)) * support;
  return float4(float3(colour), float(alpha));
}

};

struct FoilPackTearMouth {

float uLeak;
float uMouth;
float uEdgeY;
float4 shade(float2 p) {
  float across = clamp(p.x / 216.0, 0.0, 1.0);
  float bowl = sqrt(max(0.0, sin(across * 3.14159265)));
  float lit = (0.30 + 0.65 * bowl) * (0.65 + 0.35 * uMouth);
  float streak = 0.5 + 0.5 * sin(p.x * 0.14 + (p.y - uEdgeY) * 0.9);
  float3 silver = float3(0.26, 0.34, 0.33) * lit + float3(0.10, 0.12, 0.11) * streak * bowl;
  silver += float3(0.08, 0.06, 0.035) * uLeak * bowl;
  return float4(float3(silver), 1.0);
}

};

struct FoilPackTearShadow {

float2 uExtent;
float uStrength;
float4 shade(float2 p) {
  float2 q = abs(p) - uExtent + 9.0;
  float signedD = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 9.0;
  float reach = max(signedD, 0.0);
  float alpha = exp(-reach * reach / 190.0) * (1.0 - smoothstep(23.0, 29.0, reach)) * uStrength;
  return float4(0.0, 0.0, 0.0, float(alpha));
}

};

struct FoilPackTearSpark {

FoilPackTearImage uSpark;
float uSize;
float4 shade(float2 p) {
  float2 uv = p * (96.0 / max(uSize, 0.1)) + 48.0;
  float4 texel = uSpark.eval(uv);
  float glow = pow(max(float(texel.a), 0.0), 0.60);
  float support = 1.0 - smoothstep(42.0, 48.0, max(abs(uv.x - 48.0), abs(uv.y - 48.0)));
  float alpha = glow * support;
  return float4(float3(float3(1.0, 0.93, 0.76) * alpha), float(alpha));
}

};

struct FoilPackTearPose {
    float lightX;
    float lightY;
    float swayX;
    float swayY;
    float calm;
    float packX;
    float packY;
    float packRot;
    float packScale;
    float packOpacity;
    float tearFront;
    float stripFree;
    float stripX;
    float stripY;
    float stripRot;
    float stripOpacity;
    float leak;
    float burst;
    float mouth;
    float cardX;
    float cardY;
    float cardScale;
    float cardFlip;
    float cardTiltX;
    float cardTiltY;
    float cardOpacity;
    float sheen;
    float frameHeat;
    float frameRing;
    float tabGlow;
    float sparkleAge;
    float dust;
};

struct FoilPackTearVertex { float4 position [[position]]; float2 uv; };
vertex FoilPackTearVertex foilPackTearVertex(uint id [[vertex_id]]) {
    float2 uv = float2((id << 1) & 2, id & 2);
    return {float4(uv * float2(2, -2) + float2(-1, 1), 0, 1), uv};
}
static float4 over(float4 base, float4 top) { return top + base * (1 - top.a); }
static float2 unrotate(float2 p, float a) {
    return float2(cos(a) * p.x + sin(a) * p.y, -sin(a) * p.x + cos(a) * p.y);
}
static float tearY(constant float* line, float x) {
    x = clamp(x, 0.0, 216.0);
    int column = min(71, int(floor(x / 3)));
    float fraction = (x - column * 3) / 3;
    float tooth = 1 - abs(fraction * 2 - 1);
    return 34 + mix(line[column], line[column + 1], fraction) + tooth * (1.25 + 0.65 * sin(column * 7.13 + 0.8));
}
static float4 particles(float2 xy, constant float* particles, int start, int end, FoilPackTearImage spark) {
    float4 result = 0;
    for (int i = start; i < end; i++) {
        constant float* p = particles + i * 6;
        if (p[5] < 0.001) continue;
        float2 local = unrotate(xy - float2(p[0], p[1]), p[3]);
        if (max(abs(local.x), abs(local.y)) > p[2] * 0.5) continue;
        FoilPackTearSpark shader = {spark, p[2]};
        result += shader.shade(local) * p[5];
    }
    return result;
}
fragment float4 foilPackTearFragment(FoilPackTearVertex in [[stage_in]],
    constant float* u [[buffer(0)]],
    texture2d<float> packMaps [[texture(0)]], texture2d<float> packPrint [[texture(1)]],
    texture2d<float> cardFace [[texture(2)]], texture2d<float> cardBack [[texture(3)]],
    texture2d<float> cardMaps [[texture(4)]], texture2d<float> tabLabel [[texture(5)]],
    texture2d<float> sparkTex [[texture(6)]]) {
    FoilPackTearPose p;
    p.lightX = u[4];
    p.lightY = u[5];
    p.swayX = u[6];
    p.swayY = u[7];
    p.calm = u[8];
    p.packX = u[9];
    p.packY = u[10];
    p.packRot = u[11];
    p.packScale = u[12];
    p.packOpacity = u[13];
    p.tearFront = u[14];
    p.stripFree = u[15];
    p.stripX = u[16];
    p.stripY = u[17];
    p.stripRot = u[18];
    p.stripOpacity = u[19];
    p.leak = u[20];
    p.burst = u[21];
    p.mouth = u[22];
    p.cardX = u[23];
    p.cardY = u[24];
    p.cardScale = u[25];
    p.cardFlip = u[26];
    p.cardTiltX = u[27];
    p.cardTiltY = u[28];
    p.cardOpacity = u[29];
    p.sheen = u[30];
    p.frameHeat = u[31];
    p.frameRing = u[32];
    p.tabGlow = u[33];
    p.sparkleAge = u[34];
    p.dust = u[35];
    constant float* line = u + 36;
    constant float* sprites = line + 73;

    float clock = u[2];
    float2 size = float2(u[0], u[1]);
    float fit = min(size.x / 402, size.y / 874);
    float2 xy = (in.uv * size - (size - float2(402, 874) * fit) * 0.5) / fit;
    // Inverse of the original homography about the rarity frame's centre.
    float2 delta = xy - float2(201, 374);
    float2 perspective = float2(sin(p.swayY), -sin(p.swayX)) / 900;
    float2 scale = float2(cos(p.swayY), cos(p.swayX));
    float divisor = 1 - dot(perspective, delta / scale);
    xy = float2(201, 374) + delta / scale / divisor;
    FoilPackTearImage maps = {packMaps, false};
    FoilPackTearImage ink = {packPrint, false};
    FoilPackTearImage face = {cardFace, false};
    FoilPackTearImage back = {cardBack, false};
    FoilPackTearImage cardData = {cardMaps, false};
    FoilPackTearImage label = {tabLabel, true};
    FoilPackTearImage spark = {sparkTex, true};
    float2 light = float2(p.lightX, p.lightY);
    FoilPackTearFrame frame = {label, p.frameHeat, p.frameRing, p.tabGlow, clock};
    float4 colour = frame.shade(xy);
    FoilPackTearRays rays = {p.frameHeat, clock, p.sparkleAge};
    if (xy.y >= 108 && xy.y <= 648) colour.rgb += rays.shade(xy).rgb;
    colour.rgb += particles(xy, sprites, 0, 48, spark).rgb;
    float2 local = unrotate(xy - float2(201 + p.packX, 364 + p.packY), p.packRot) / p.packScale + float2(108, 188);
    float edge = tearY(line, local.x);
    float opening = pow(max(0.0, sin(M_PI_F * clamp(local.x, 0.0, 216.0) / 216)), 0.32) * p.mouth;
    float lipY = edge + opening * 2;
    float backY = edge - opening * 5;
    bool isOpening = local.x >= 0 && local.x <= p.tearFront && local.y >= backY && local.y <= lipY;
    if (p.packOpacity > 0.001) {
        FoilPackTearShadow shadow = {float2(104, 178), 0.38};
        colour = over(colour, shadow.shade(local - float2(115, 203)) * p.packOpacity);
        if (isOpening) {
            FoilPackTearMouth mouth = {p.leak, p.mouth, tearY(line, p.tearFront * 0.5)};
            colour = over(colour, mouth.shade(local) * p.packOpacity);
        }
        float backLip = (1 - smoothstep(0.1, 0.55, abs(local.y - backY))) * p.mouth * 0.85 * p.packOpacity;
        if (local.x >= 0 && local.x <= p.tearFront) colour = over(colour, float4(float3(0.808, 0.890, 0.831) * backLip, backLip));
    }
    float4 wrapper = 0;
    float4 curl = 0;
    float frontEdge = tearY(line, p.tearFront);
    FoilPackTearCurl curlShader = {7 + 9 * sqrt(p.tearFront / 216), frontEdge, p.tearFront / 7, p.stripFree,
        float2(p.lightX + sin(p.stripRot) * p.stripFree, p.lightY), p.leak};
    if (p.packOpacity > 0.001) {
        float cut = 1;
        if (p.tearFront > 0.01 && local.x <= p.tearFront) cut = smoothstep(lipY - 0.3, lipY + 0.3, local.y);
        if (p.tearFront <= 0.01 && local.x < 5 && abs(local.y - 34) < 3 * (1 - local.x / 5)) cut = 0;
        if (local.x >= 0 && local.x <= 216 && local.y >= 0 && local.y <= 376 && cut > 0) {
            FoilPackTearFoil foil = {maps, ink, light, clock, p.calm, p.tearFront, p.leak, p.mouth, frontEdge};
            wrapper = foil.shade(local) * cut;
        }
        if (p.tearFront > 0.1 && local.x >= 0 && local.x <= p.tearFront) {
            float coverage = maps.eval(float2(clamp(local.x * 3, 0.6, 647.4) + 1944, clamp(local.y * 3, 0.6, 1127.4))).r;
            float thickness = sqrt(1 + pow((tearY(line, local.x + 0.3) - tearY(line, local.x - 0.3)) / 0.6, 2.0));
            float distance = abs(local.y - lipY) / thickness;
            float inner = (1 - smoothstep(2.6, 3.3, distance)) * cut * 0.14 * coverage;
            wrapper = over(wrapper, float4(float3(0.72, 1, 0.89) * inner, inner));
            float lip = (1 - smoothstep(0.25, 0.65, distance)) * 0.9 * coverage;
            wrapper = over(wrapper, float4(float3(0.89, 0.91, 0.81) * lip, lip));
        }
        if (p.tearFront > 0.1 && p.stripFree < 0.5) {
            float2 c = unrotate(local - float2(p.tearFront, 0), -0.12);
            if (c.x >= -34 && c.x <= 26 && c.y >= -12 && c.y <= 68) curl = curlShader.shade(c) * p.stripOpacity;
        }
        wrapper = over(wrapper, curl) * p.packOpacity;
    }
    if (p.cardFlip > 0.02) colour = over(colour, wrapper);
    if (p.cardOpacity > 0.001) {
        float2 cardXY = (xy - float2(p.cardX, p.cardY)) / p.cardScale;
        FoilPackTearShadow shadow = {float2(90 * abs(cos(p.cardFlip + p.cardTiltY)), 126), 0.32};
        colour = over(colour, shadow.shade(cardXY - float2(0, 8)) * p.cardOpacity);
        if (abs(cardXY.x) <= 140 && abs(cardXY.y) <= 164) {
            float cy = cos(p.cardFlip + p.cardTiltY);
            float sy = sin(p.cardFlip + p.cardTiltY);
            float cx = cos(p.cardTiltX);
            float sx = sin(p.cardTiltX);
            float3x3 rot = float3x3(float3(cy, sy * sx, -sy * cx), float3(0, cx, sx), float3(sy, -cy * sx, cy * cx));
            FoilPackTearCard card = {face, back, cardData, rot, light, float2(p.cardTiltX, p.cardTiltY), p.sheen, p.leak, p.cardScale};
            colour = over(colour, card.shade(cardXY) * p.cardOpacity);
        }
    }
    if (p.cardFlip <= 0.02) colour = over(colour, wrapper);
    if (p.packOpacity > 0.001) {
        FoilPackTearLight glow = {p.tearFront, p.leak * p.packOpacity, p.burst * p.packOpacity, clock, tearY(line, p.tearFront * 0.5)};
        colour.rgb += glow.shade(local).rgb * p.packOpacity * (isOpening ? 0.12 : 1.0);
        if (local.x >= 0 && local.x <= p.tearFront) {
            float distance = abs(local.y - lipY);
            float halo = exp(-distance * distance / 40) * 0.45;
            float coverage = maps.eval(float2(clamp(local.x * 3, 0.6, 647.4) + 1944, clamp(local.y * 3, 0.6, 1127.4))).r;
            float lip = (1 - smoothstep(0.4, 1.0, distance)) * coverage;
            colour.rgb += (float3(1, 0.824, 0.478) * halo + float3(1, 0.965, 0.847) * lip) * p.leak * 0.6 * p.packOpacity;
        }
    }
    if (p.stripFree >= 0.5 && p.stripOpacity > 0.001) {
        float2 c = unrotate(xy - float2(93 + p.tearFront + p.stripX, 176 + p.stripY), -0.12 + p.stripRot);
        c.y /= 0.55 + 0.45 * abs(cos(p.stripRot * 0.7));
        if (c.x >= -34 && c.x <= 26 && c.y >= -12 && c.y <= 68) colour = over(colour, curlShader.shade(c) * p.stripOpacity);
    }
    colour.rgb += particles(xy, sprites, 48, 66, spark).rgb;
    return float4(colour.rgb, 1);
}
