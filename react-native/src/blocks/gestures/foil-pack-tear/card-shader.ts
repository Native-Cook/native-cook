// Analytic camera-ray/slab intersection gives perspective and a visible edge without ray marching.
export const CARD_SKSL = `
uniform shader uFace;
uniform shader uBack;
uniform shader uMaps;
uniform float3x3 uRot;
uniform float2 uLight;
uniform float2 uTilt;
uniform float uSheen;
uniform float uLeak;
uniform float uScale;
float sq(float v) { return v * v; }
float3 objectVector(float3 v) { return float3(dot(uRot[0], v), dot(uRot[1], v), dot(uRot[2], v)); }
float dataAt(float2 uv, float tile) {
  return uMaps.eval(float2(clamp(uv.x, 1.0, 749.0) + tile * 750.0, clamp(uv.y, 1.0, 1049.0))).r;
}
float roundCard(float2 q) {
  float2 b = abs(q) - float2(81.0, 117.0);
  return length(max(b, 0.0)) + min(max(b.x, b.y), 0.0) - 9.0;
}
half4 main(float2 xy) {
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
  if (leave < entry || leave < 0.0) { return half4(0.0); }
  float3 hit = eye + ray * entry;
  float rimD = roundCard(hit.xy);
  if (rimD > 0.65) { return half4(0.0); }
  float alpha = smoothstep(0.65, -0.25, rimD);
  bool side = nearT.z < max(nearT.x, nearT.y);
  if (side) {
    float edgeShine = 1.12 + 0.30 * sin(hit.y * 0.023 + uLight.y);
    return half4(half3(float3(1.0, 0.91, 0.68) * alpha * edgeShine), half(alpha));
  }
  bool back = hit.z > 0.0;
  float2 uv = float2((back ? hit.x : -hit.x) / 180.0 + 0.5, hit.y / 252.0 + 0.5) * float2(750.0, 1050.0);
  // Crop the printed face to its gold edge: no second dark plate or outer hairline.
  if (!back) { uv = float2(24.0) + uv * float2(702.0 / 750.0, 1002.0 / 1050.0); }
  float depth = dataAt(uv, 1.0);
  float2 parallax = uTilt.yx * (depth - 0.5) * float2(52.0, 58.0) * smoothstep(-5.0, -12.0, rimD);
  uv = clamp(uv + parallax, float2(1.0), float2(749.0, 1049.0));
  half4 art = back ? uBack.eval(uv) : uFace.eval(uv);
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
  return half4(half3(colour * alpha), half(alpha));
}
`;
