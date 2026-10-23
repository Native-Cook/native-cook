// Metal source of the lift layer's compositor, compiled at runtime with
// MTLDevice.makeLibrary(source:) so the project builds without the optional
// Metal Toolchain component.
//
// The lift layer's compositing (console-lift.tsx LiftLayer), done on the GPU
// from SceneKit's offscreen render instead of three.js + Skia:
//   1. the mirrored object (scaleY −0.9 about the mirror line) at `mirrored`
//      opacity, then the page-colored fog rect over it (also at `mirrored`);
//   2. the silhouette drop shadow (Skia Shadow shadowOnly: the object's alpha,
//      offset by dy and Gaussian-blurred with sigma `blur`, in black·alpha);
//   3. the object itself.
// Everything is premultiplied and blended in sRGB-encoded values, as Skia
// does on an untagged surface. Each draw carries its own opacity (RN Skia
// Group opacity is inherited by each child's paint, not a layer).
let glowknobLiftCompositorSource = #"""
#include <metal_stdlib>
using namespace metal;

struct CompositeUniforms {
  float2 sizePx;
  float pixelRatio;
  float mirror;      // pt
  float squash;
  float depth;       // pt
  float mirrored;    // reflection + fog opacity
  float glow;        // 1 when the floor mirrors the lifted object
  float shadowDy;    // pt
  float shadowAlpha;
  float shown;
  float shadowScale; // shadow texture px per output px
  float4 fog[4];     // straight rgba stops
  float4 fogAt;
};

struct VOut {
  float4 position [[position]];
  float2 uv;
};

vertex VOut compositeVertex(uint vid [[vertex_id]]) {
  float2 p = float2((vid << 1) & 2, vid & 2);
  VOut o;
  o.position = float4(p * float2(2, -2) + float2(-1, 1), 0, 1);
  o.uv = p;
  return o;
}

static float4 over(float4 src, float4 dst) { return src + dst * (1.0 - src.a); }

static float4 fogAt(constant CompositeUniforms &u, float t) {
  // Skia linear gradient, clamped, interpolated on straight (unpremul) colors.
  float4 c = u.fog[0];
  for (int i = 1; i < 4; i++) {
    if (t >= u.fogAt[i - 1]) {
      float span = max(u.fogAt[i] - u.fogAt[i - 1], 1e-6);
      c = mix(u.fog[i - 1], u.fog[i], saturate((t - u.fogAt[i - 1]) / span));
    }
  }
  if (t >= u.fogAt[3]) c = u.fog[3];
  return c;
}

fragment float4 compositeFragment(VOut in [[stage_in]],
                                  constant CompositeUniforms &u [[buffer(0)]],
                                  texture2d<float> object [[texture(0)]],
                                  texture2d<float> shadow [[texture(1)]]) {
  constexpr sampler linear(filter::linear, address::clamp_to_zero);
  float2 px = in.uv * u.sizePx;
  float2 pt = px / u.pixelRatio;
  float4 dst = float4(0);
  if (u.glow > 0.5 && u.mirrored > 0) {
    // translateY(mirror) scaleY(−SQUASH) translateY(−mirror): screen y ← source y.
    float srcY = u.mirror - (pt.y - u.mirror) / u.squash;
    float4 refl = object.sample(linear, float2(in.uv.x, srcY * u.pixelRatio / u.sizePx.y));
    dst = over(refl * u.mirrored, dst);
    if (pt.y >= u.mirror) {
      float t = (pt.y - u.mirror) / u.depth;
      float4 f = fogAt(u, t);
      float a = f.a * u.mirrored;
      dst = over(float4(f.rgb * a, a), dst);
    }
  }
  float2 shadowUV = float2(in.uv.x, (pt.y - u.shadowDy) * u.pixelRatio / u.sizePx.y);
  float sa = shadow.sample(linear, shadowUV).r * u.shadowAlpha;
  dst = over(float4(0, 0, 0, sa), dst);
  float4 o = object.sample(linear, in.uv);
  dst = over(o, dst);
  return dst * u.shown;
}

// Shadow: the object's alpha, downsampled (box filter) into a smaller
// texture, then a separable Gaussian. A soft shadow of sigma ≥ 5 pt is
// band-limited, so blurring at a quarter of the resolution is equivalent.
kernel void shadowDownsample(texture2d<float, access::read> object [[texture(0)]],
                             texture2d<float, access::write> out [[texture(1)]],
                             constant uint &factor [[buffer(0)]],
                             uint2 gid [[thread_position_in_grid]]) {
  if (gid.x >= out.get_width() || gid.y >= out.get_height()) return;
  float sum = 0;
  uint2 base = gid * factor;
  for (uint y = 0; y < factor; y++) {
    for (uint x = 0; x < factor; x++) {
      uint2 p = min(base + uint2(x, y), uint2(object.get_width() - 1, object.get_height() - 1));
      sum += object.read(p).a;
    }
  }
  out.write(float4(sum / float(factor * factor)), gid);
}

kernel void gaussianBlur(texture2d<float, access::read> src [[texture(0)]],
                         texture2d<float, access::write> dst [[texture(1)]],
                         constant float &sigma [[buffer(0)]],
                         constant uint &horizontal [[buffer(1)]],
                         uint2 gid [[thread_position_in_grid]]) {
  int w = int(src.get_width()), h = int(src.get_height());
  if (int(gid.x) >= w || int(gid.y) >= h) return;
  if (sigma < 0.01) { dst.write(src.read(gid), gid); return; }
  int radius = int(ceil(sigma * 3.0));
  float sum = 0, weights = 0;
  for (int i = -radius; i <= radius; i++) {
    float wgt = exp(-float(i * i) / (2.0 * sigma * sigma));
    int2 p = horizontal ? int2(int(gid.x) + i, int(gid.y)) : int2(int(gid.x), int(gid.y) + i);
    // Decal tiling: outside the image is transparent.
    float v = (p.x < 0 || p.y < 0 || p.x >= w || p.y >= h) ? 0.0 : src.read(uint2(p)).r;
    sum += v * wgt;
    weights += wgt;
  }
  dst.write(float4(sum / weights), gid);
}
"""#
