package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

// Numerical expressions and uniforms match the source SkSL.
internal const val CURL_AGSL = """
uniform float uRadius;
uniform float uHeight;
uniform float uRoll;
uniform float uFree;
uniform float2 uLight;
uniform float uLeak;
float sq(float v) { return v * v; }
float3 openEnd(float2 q, float radial, float tooth) {
  float theta = atan(q.y, q.x);
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
half4 main(float2 p) {
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
  return half4(half3(result * coverage), half(coverage));
}
"""
