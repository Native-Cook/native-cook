/** Continuous blank-header material; rigid product UI is drawn above it. */

export type ElasticFoodCardsSurfaceUniforms = {
  anchor: [number, number];
  amplitude: number;
  fanWidth: number;
  tailY: number;
  contact: number;
};

export const ELASTIC_FOOD_CARDS_SURFACE_SKSL = `
uniform float2 anchor;
uniform float amplitude;
uniform float fanWidth;
uniform float tailY;
uniform float contact;

float2 surfaceSmooth(float a, float b, float x) {
  float u = clamp((x - a) / (b - a), 0.0, 1.0);
  float derivative = (x > a && x < b)
    ? 6.0 * u * (1.0 - u) / (b - a) : 0.0;
  return float2(u * u * (3.0 - 2.0 * u), derivative);
}

float3 surfaceGaussian(float d, float2 dd, float width, float2 dw) {
  float s = d / width;
  float value = exp(-0.5 * s * s);
  float2 ds = dd / width - d * dw / (width * width);
  return float3(value, -value * s * ds);
}

// Returns height, d(height)/dx, d(height)/dy and occlusion valley weight.
float4 surfaceRay(float2 position, float2 canonicalEnd, float bendValue,
                  float weight, float widthScale, float strength) {
  float2 end = float2(
    anchor.x + (canonicalEnd.x - 190.0) * widthScale * 1.2,
    canonicalEnd.y * tailY / 250.0
  );
  float2 D = end - anchor;
  float lengthSquared = max(dot(D, D), 1.0);
  float inverseLength = 1.0 / sqrt(lengthSquared);
  float2 N = float2(-D.y, D.x) * inverseLength;
  float2 r = position - anchor;
  float rawT = dot(r, D) / lengthSquared;
  float t = clamp(rawT, 0.0, 1.0);
  if (rawT <= 0.0 || rawT >= 1.0) return float4(0.0);
  float2 dt = D / lengthSquared;
  float bend = bendValue * widthScale;
  float d = dot(r, N) - bend * 4.0 * t * (1.0 - t);
  float2 dd = N - bend * 4.0 * (1.0 - 2.0 * t) * dt;
  float width = 2.4 + 4.5 * t;
  float shoulderWidth = 5.0 + 6.0 * t;
  float3 narrow = surfaceGaussian(d, dd, width, 4.5 * dt);
  float3 broad = surfaceGaussian(d, dd, shoulderWidth, 6.0 * dt);
  float profile = 0.55 * broad.x - narrow.x;
  float2 profileGradient = 0.55 * broad.yz - narrow.yz;
  float envelope = 16.0 * t * t * (1.0 - t) * (1.0 - t);
  float envelopeDerivative = 32.0 * t * (1.0 - t) * (1.0 - 2.0 * t);
  float gain = strength * weight;
  return float4(
    gain * envelope * profile,
    gain * (envelope * profileGradient + profile * envelopeDerivative * dt),
    weight * envelope * narrow.x
  );
}

// Input is intended encoded sRGB. Skia's destination can be P3 or sRGB.
// Decode explicitly, then use the SkSL intrinsic for the destination gamut.
float3 surfaceSrgbToLinear(float3 encoded) {
  float3 lo = encoded / 12.92;
  float3 hi = pow((encoded + 0.055) / 1.055, float3(2.4));
  return mix(lo, hi, step(float3(0.04045), encoded));
}

half4 surfaceOutput(float factor) {
  float3 encoded = clamp(float3(223.0, 223.0, 221.0) / 255.0 * factor, 0.0, 1.0);
  return half4(fromLinearSrgb(surfaceSrgbToLinear(encoded)), 1.0);
}

half4 main(float2 position) {
  if (amplitude <= 0.0 || fanWidth <= 0.0) return surfaceOutput(1.0);
  float2 left = surfaceSmooth(0.0, 14.0, position.x);
  float2 right = surfaceSmooth(379.0, 393.0, position.x);
  float2 top = surfaceSmooth(40.0, 56.0, position.y);
  float2 bottom = surfaceSmooth(tailY - 40.0, tailY + 8.0, position.y);
  float bx = left.x * (1.0 - right.x);
  float by = top.x * (1.0 - bottom.x);
  float boundary = bx * by;
  if (boundary <= 0.0) return surfaceOutput(1.0);
  float2 boundaryGradient = float2(
    (left.y * (1.0 - right.x) - left.x * right.y) * by,
    (top.y * (1.0 - bottom.x) - top.x * bottom.y) * bx
  );
  float widthScale = clamp(fanWidth, 0.0, 378.0) / 378.0;
  float fanGain = surfaceSmooth(0.0, 24.0, fanWidth).x;
  float strength = clamp(amplitude, 0.0, 24.0) * fanGain;
  float4 field = float4(0.0);
  // Explicit calls avoid version-sensitive SkSL constant-array constructors.
  field += surfaceRay(position, float2(8.0, 32.0), -8.0, 0.68, widthScale, strength);
  field += surfaceRay(position, float2(25.0, 110.0), 13.0, 0.82, widthScale, strength);
  field += surfaceRay(position, float2(67.0, 185.0), -11.0, 0.92, widthScale, strength);
  field += surfaceRay(position, float2(94.0, 222.0), 16.0, 0.78, widthScale, strength);
  field += surfaceRay(position, float2(167.0, 250.0), -6.0, 1.0, widthScale, strength);
  field += surfaceRay(position, float2(235.0, 248.0), 9.0, 0.91, widthScale, strength);
  field += surfaceRay(position, float2(308.0, 221.0), -15.0, 0.83, widthScale, strength);
  field += surfaceRay(position, float2(332.0, 183.0), 10.0, 0.95, widthScale, strength);
  field += surfaceRay(position, float2(364.0, 112.0), -13.0, 0.76, widthScale, strength);
  field += surfaceRay(position, float2(388.0, 30.0), 7.0, 0.65, widthScale, strength);

  float2 heightGradient = boundary * field.yz + field.x * boundaryGradient;
  float3 normal = normalize(float3(-heightGradient.x, -heightGradient.y, 1.0));
  float3 light = normalize(float3(-0.4, -0.6, 0.7));
  float diffuse = max(0.0, dot(normal, light));
  float flatLighting = 0.72 + 0.28 * light.z;
  float lighting = (0.72 + 0.28 * diffuse) / flatLighting;
  float ao = min(0.18, 0.055 * (strength / 24.0) * boundary * field.w
    * (0.3 + 0.7 * clamp(contact, 0.0, 1.0)));
  float3 halfVector = normalize(light + float3(0.0, 0.0, 1.0));
  float specular = 0.035 * max(0.0,
    pow(max(0.0, dot(normal, halfVector)), 8.0) - pow(halfVector.z, 8.0));
  float factor = lighting * (1.0 - ao) + specular;
  return surfaceOutput(factor);
}
`;
