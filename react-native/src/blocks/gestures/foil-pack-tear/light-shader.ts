// Compact radial supports extinguish every additive effect before its drawing bounds.
export const LIGHT_SKSL = `
uniform float uFront;
uniform float uLeak;
uniform float uBurst;
uniform float uClock;
uniform float uEdgeY;
float sq(float v) { return v * v; }
half4 main(float2 p) {
  if (uLeak < 0.001 && uBurst < 0.001) { return half4(0.0); }
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
  float beamAngle = atan(p.x - centreX, rise + 24.0);
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
  return half4(half3(colour), half(alpha));
}
`;

// The wall is drawn behind the card; the front lip stays in front until the flip.
export const MOUTH_SKSL = `
uniform float uLeak;
uniform float uMouth;
uniform float uEdgeY;
half4 main(float2 p) {
  float across = clamp(p.x / 216.0, 0.0, 1.0);
  float bowl = sqrt(max(0.0, sin(across * 3.14159265)));
  float lit = (0.30 + 0.65 * bowl) * (0.65 + 0.35 * uMouth);
  float streak = 0.5 + 0.5 * sin(p.x * 0.14 + (p.y - uEdgeY) * 0.9);
  float3 silver = float3(0.26, 0.34, 0.33) * lit + float3(0.10, 0.12, 0.11) * streak * bowl;
  silver += float3(0.08, 0.06, 0.035) * uLeak * bowl;
  return half4(half3(silver), 1.0);
}
`;

// Analytic blurred silhouette: no outline and no saveLayer or rectangular cutoff.
export const SHADOW_SKSL = `
uniform float2 uExtent;
uniform float uStrength;
half4 main(float2 p) {
  float2 q = abs(p) - uExtent + 9.0;
  float signedD = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 9.0;
  float reach = max(signedD, 0.0);
  float alpha = exp(-reach * reach / 190.0) * (1.0 - smoothstep(23.0, 29.0, reach)) * uStrength;
  return half4(0.0, 0.0, 0.0, half(alpha));
}
`;

// Every mote and burst ray is sampled from the shipping glint, with no solid dot overlay.
export const SPARK_SKSL = `
uniform shader uSpark;
uniform float uSize;
half4 main(float2 p) {
  float2 uv = p * (96.0 / max(uSize, 0.1)) + 48.0;
  half4 texel = uSpark.eval(uv);
  float glow = pow(max(float(texel.a), 0.0), 0.60);
  float support = 1.0 - smoothstep(42.0, 48.0, max(abs(uv.x - 48.0), abs(uv.y - 48.0)));
  float alpha = glow * support;
  return half4(half3(float3(1.0, 0.93, 0.76) * alpha), half(alpha));
}
`;
