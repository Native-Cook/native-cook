package com.ngocdevv.nativecook.blocks.gestures.foilpacktear

// Numerical expressions and uniforms match the source SkSL.
internal const val FOIL_AGSL = """
uniform shader uMaps;
uniform shader uPrint;
uniform float2 uLight;
uniform float uClock;
uniform float uCalm;
uniform float uFront;
uniform float uLeak;
uniform float uMouth;
uniform float uEdgeY;
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
half4 main(float2 p) {
  if (p.x < 0.0 || p.x > 216.0 || p.y < 0.0 || p.y > 376.0) { return half4(0.0); }
  float2 uv = p * 3.0;
  float coverage = dataAt(uv, 3.0);
  if (coverage < 0.003) { return half4(0.0); }
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
  half4 ink = uPrint.eval(uv);
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
  return half4(half3(colour * coverage), half(coverage));
}
"""
