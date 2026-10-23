/// Direct SkSL → Metal translation; point-space constants and compositing are unchanged.
enum HorizonSceneShader {
  static let source = """

float4 rest;
float4 slab;
float2 viewport;
float4 well;
float3 bead;
float closePress;
float2 planet;
float reach;
float scale;
float slabScale;
float progress;
float power;
float pulse;
float shimmer;
float flare;
float streak;
float settled;
float dawnBreak;
float sweep;
float sweepStrength;
float opaque;

constant static constexpr float RADIUS = 560.0;
constant static constexpr float RISE = 34.0;
constant static constexpr float CARD_RADIUS = 28.0;
constant static constexpr float WELL_RADIUS = 18.0;
constant static constexpr float3 PAGE = float3(0.01960784, 0.06274510, 0.13333333);
constant static constexpr float3 HAZE = float3(0.08235294, 0.14509804, 0.36078431);
constant static constexpr float3 CORE = float3(0.86666667, 0.89411765, 1.00000000);
constant static constexpr float3 BLOOM = float3(0.54901961, 0.65098039, 0.92941176);
constant static constexpr float3 DEEP = float3(0.19215686, 0.28235294, 0.57254902);
constant static constexpr float3 PANE = float3(0.05098039, 0.07843137, 0.14901961);
constant static constexpr float3 WELL = float3(0.01960784, 0.06274510, 0.12941176);
constant static constexpr float3 LAMP = float3(0.15294118, 0.23921569, 0.62745098);
constant static constexpr float3 PLAIN = float3(0.04313725, 0.07058824, 0.14117647);
constant static constexpr float3 PLAIN_WELL = float3(0.02352941, 0.04313725, 0.09411765);
constant static constexpr float3 DISC = float3(0.20392157, 0.23137255, 0.28627451);

float box(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

float2 normal(float2 p, float2 halfSize, float radius) {
  float2 q = abs(p) - halfSize + radius;
  float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
  if (q.x > 0.0 && q.y > 0.0) return s * normalize(q);
  return q.x > q.y ? float2(s.x, 0.0) : float2(0.0, s.y);
}

float coverage(float d) { return clamp(0.5 - d * scale, 0.0, 1.0); }
float edge(float d, float width) {
  return (coverage(d) - coverage(d + width)) / max(coverage(d), 0.0001);
}
float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
float bell(float x) { return exp(-x * x); }

// The lamp is reflected on the face, two points above the recess. It is a
// deep-blue source with a broad upward falloff and almost no downward spill.
float4 lamp(float2 p) {
  float2 q = (p - slab.xy) / slabScale - float2(well.x, well.y - well.w - 2.0);
  float halfWidth = mix(slab.z * 0.47, well.z, flare);
  if (abs(q.x) > slab.z || q.y < -55.0 || q.y > 5.0) return float4(0.0);
  float ends = 1.0 - smoothstep(halfWidth * 0.25, halfWidth, abs(q.x));
  float wideEnds = bell(q.x / (halfWidth * 0.82));
  float core = 1.0 - smoothstep(0.45, 1.05, abs(q.y));
  float above = bell(q.y / (q.y < 0.0 ? 20.0 : 3.5));
  float nearLine = bell(q.y / (q.y < 0.0 ? 8.0 : 2.0));
  float3 glow = float3(6.0 * nearLine, 15.0 * above - 2.0 * nearLine,
    54.0 * above - 10.0 * nearLine) / 255.0 * wideEnds;
  float tail = 1.0 - ends;
  float3 line = max(LAMP - PANE, 0.0) * float3(1.0, 1.0 + 0.60 * tail, 1.0 + 0.18 * tail);
  // The narrow line must not cut a dark seam through the wider glow at its ends.
  float lineCover = core * ends;
  float spill = 1.0 - smoothstep(2.0, 5.0, q.y);
  return float4(mix(glow, line, lineCover) * spill * power * (1.0 + 0.35 * pulse) * (1.0 + flare), lineCover);
}


"""
}
