// Vitamin Capsule Dose — the flat hero (Skia), drawn where WebGPU is missing (Expo Go, web) and
// under Reduce Motion: the same three forms in 2D, blended while they morph, softly shaded.
export const VCD_SKSL = `
uniform float2 uC;        // hero centre (pt)
uniform float uU;         // unit (pt)
uniform float3 uShape;    // form a, form b, morph k
uniform float uScale;     // 0 … 1 (taken, then grown back)
uniform float uFade;      // 1 visible

float sq(float v) { return v*v; }
float segd(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0)); }
float form(float2 p, float s) {
  if (s < 0.5) {
    float c = cos(0.785), sn = sin(0.785);
    float2 q = float2(c*p.x + sn*p.y, -sn*p.x + c*p.y);
    return segd(q, float2(-0.56, 0.0), float2(0.56, 0.0)) - 0.44;
  }
  if (s < 1.5) return (length(p/float2(0.8, 0.62)) - 1.0)*0.62;
  float2 q = p - float2(0.0, 0.2);
  float body = length(q) - 0.6;
  float tip = segd(p, float2(0.0, 0.2), float2(0.0, -0.75)) - 0.05 - 0.55*clamp((p.y + 0.75)/0.95, 0.0, 1.0);
  return min(body, tip);
}
half4 main(float2 xy) {
  float sc = max(uScale, 0.02);
  float2 p = (xy - uC)/uU/sc;
  p.y = -p.y;
  float d = mix(form(p, uShape.x), form(p, uShape.y), uShape.z)*sc;
  float a = (1.0 - smoothstep(-0.6/uU, 0.6/uU, d))*uFade;
  if (a <= 0.0) return half4(0.0);
  // which half: blue glass on the capsule's upper right, the drop all light blue, the rest white
  float c = cos(0.785), sn = sin(0.785);
  float blue = 0.0;
  if (uShape.x < 0.5) blue += (1.0 - uShape.z)*step(c*p.x + sn*p.y, 0.0);
  if (uShape.y < 0.5) blue += uShape.z*step(c*p.x + sn*p.y, 0.0);
  float mint = (uShape.x > 0.5 && uShape.x < 1.5 ? 1.0 - uShape.z : 0.0) + (uShape.y > 0.5 && uShape.y < 1.5 ? uShape.z : 0.0);
  float drop = (uShape.x > 1.5 ? 1.0 - uShape.z : 0.0) + (uShape.y > 1.5 ? uShape.z : 0.0);
  float3 col = mix(float3(0.98, 0.985, 0.99), float3(0.15, 0.51, 1.0), blue);
  col = mix(col, float3(0.62, 0.8, 1.0), drop);
  col = mix(col, float3(0.78, 0.92, 0.85), mint);
  // soft light from the upper left, darker towards the lower right edge
  float shade = clamp(0.5 - d*2.0 + 0.35*(p.y - p.x), 0.0, 1.0);
  col *= 0.82 + 0.2*shade;
  col += float3(1.0)*0.35*exp(-sq(length(p - float2(-0.22, 0.25))/0.12));
  return half4(half3(col*a), half(a));
}
`;
