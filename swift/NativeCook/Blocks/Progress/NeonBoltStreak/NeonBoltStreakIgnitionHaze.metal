#include <metal_stdlib>
using namespace metal;

constant float2 nbsRight[] = {
  {-5.89,28}, {-4.5,27.67}, {-3.34,26.83}, {17.14,4.96}, {17.91,2.01}, {15.86,-0.25},
  {7.36,-3.22}, {6.29,-4.11}, {6.06,-5.49}, {9.61,-23.96}, {7.79,-27.64}, {7.77,-27.64}
};
constant float2 nbsLeft[] = {
  {-9.23,26.18}, {-9.7,24.83}, {-9.63,23.41}, {-5.54,7.02}, {-5.75,5.43}, {-6.98,4.4},
  {-15.82,1.43}, {-17.9,-0.8}, {-17.17,-3.75}, {3.75,-26.88}, {7.77,-27.64}
};

float nbsPackedValue(int index, float4 first, float4 second) {
  return index < 4 ? first[index] : second[index - 4];
}

float3 nbsEncodeLight(float3 value) {
  return mix(value * 12.92f, 1.055f * pow(value, float3(1.0f / 2.4f)) - 0.055f,
             step(float3(0.0031308f), value));
}

// Same arc-length chains as RN: the broad halo follows the active tube, not a line between its lower and upper tips.
float nbsLitDistance(float2 p, constant float2 *points, int count, float fill) {
  float total = 0;
  for (int i = 0; i < count - 1; i++) total += length(points[i + 1] - points[i]);
  float remaining = clamp(fill, 0.0f, 1.0f) * total;
  float nearest = 1e4f;
  for (int i = 0; i < count - 1; i++) {
    float2 a = points[i], ab = points[i + 1] - a;
    float segmentLength = max(length(ab), 1e-3f);
    if (remaining > 0) {
      float t = clamp(dot(p - a, ab) / dot(ab, ab), 0.0f, min(remaining / segmentLength, 1.0f));
      nearest = min(nearest, length(p - a - t * ab));
    }
    remaining -= segmentLength;
  }
  return nearest;
}

[[ stitchable ]] half4 neonBoltStreakIgnitionHaze(
  float2 position, half4 currentColor, float viewScale, float verticalOffset, float jolt,
  float4 headsA, float4 headsB, float4 fillsA, float4 fillsB,
  float4 lightsA, float4 lightsB, float4 offsetsA, float4 offsetsB
) {
  float2 p = position / viewScale - float2(0, verticalOffset + jolt);
  float strongest = 0;
  for (int index = 0; index < 7; index++) {
    float head = nbsPackedValue(index, headsA, headsB);
    float light = nbsPackedValue(index, lightsA, lightsB);
    float fill = nbsPackedValue(index, fillsA, fillsB);
    if (head <= 0.001f || light <= 0.001f || fill <= 0) continue;
    float centre = 16 + (370.0f / 7) * (float(index) + 0.5f);
    float2 q = p - float2(centre, 629 + nbsPackedValue(index, offsetsA, offsetsB));
    float far = length(max(abs(q) - float2(18,28), 0.0f));
    float dl = min(nbsLitDistance(q, nbsRight, 12, fill), nbsLitDistance(q, nbsLeft, 11, fill));
    float haze = 0.065f * smoothstep(4.0f,9.0f,dl) * exp(-dl / 42.0f);
    strongest = max(strongest, haze * (1 - smoothstep(80.0f,110.0f,far)) * light * head);
  }
  float edge = min(min(p.x + 32.0f, 434.0f - p.x), min(p.y - 512.0f, 776.0f - p.y));
  strongest *= smoothstep(0.0f, 30.0f, edge);
  const float3 base = float3(0.008568f, 0.009134f, 0.010960f);
  float3 delta = nbsEncodeLight(base + strongest * float3(0.9131f, 0.5520f, 0)) - nbsEncodeLight(base);
  return half4(float4(delta, max(delta.r, delta.g)));
}
