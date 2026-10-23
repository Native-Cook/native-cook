/// The flame inside one pill, ported line by line from the SkSL in
/// `flare-shader.ts` (flare-notifications.md §3.4).
///
/// The flame is 32 soft elliptical Gaussian blobs, keyframed on the CPU
/// (`FlareBlobFrame.swift`) into three uniform arrays. Colors are blended in
/// OKLab by weight, coverage is 1 - exp(-total weight), and the result is
/// mixed over the pill body in sRGB.
///
/// Metal Shading Language, kept as source: `FlareRenderer` compiles it at
/// launch, so the build does not need Xcode's optional Metal Toolchain (which
/// a `.metal` file in the target would). `flareVertex` / `flareFragment` are a
/// plain render pass for `FlareSurfaceView` (a CAMetalLayer).
///
/// Coordinates: `xy` is the fragment's position in points of the pill (pixel
/// centers, origin top-left), as Skia passes local coordinates to the SkSL
/// `main`.
enum FlareShader {
  static let source = """
#include <metal_stdlib>
using namespace metal;

#define BLOB_COUNT 32

namespace flame {

static float hash(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

static float3 oklabToSrgb(float3 c) {
  float l_ = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m_ = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s_ = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  float l = l_ * l_ * l_;
  float m = m_ * m_ * m_;
  float s = s_ * s_ * s_;
  float3 lin = float3(
     4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
  lin = clamp(lin, 0.0, 1.0);
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

// SkSL `half4 main(float2 xy)`. `geo` and `col` hold BLOB_COUNT float4s and
// `rot` BLOB_COUNT float2s, flattened (the render pass hands over
// plain float pointers). The pointer type is a template parameter, so the
// same body would also serve the `device` arrays of a `[[ stitchable ]]`
// function for SwiftUI's `.colorEffect`.
template <typename Floats>
static half4 shade(float2 xy, float2 size, float stretch, float heat, float cool, float ember,
                   float guard, float3 body, Floats geo, Floats col, Floats rot) {
  float h = size.y;
  float u = (size.x - xy.x) / h;
  float v = xy.y / h;
  // Half-LSB dither: long fades into the body otherwise step in 8-bit bands.
  float dither = (hash(xy) - 0.5) / 255.0;
  float k = max(stretch, 0.0);
  if (u > 2.9 * max(k, 1.0) + 0.4) return half4(half3(body + dither), 1.0);

  float total = 0.0;
  float3 acc = float3(0.0);
  float spread = mix(1.0, k, 0.5);
  for (int i = 0; i < BLOB_COUNT; i++) {
    float4 g = float4(geo[i * 4], geo[i * 4 + 1], geo[i * 4 + 2], geo[i * 4 + 3]);
    float4 ci = float4(col[i * 4], col[i * 4 + 1], col[i * 4 + 2], col[i * 4 + 3]);
    float2 d = float2(u - g.x * k, v - g.y);
    float2 r = float2(rot[i * 2], rot[i * 2 + 1]);
    float x = (d.x * r.x - d.y * r.y) / (g.z * spread);
    float y = (d.x * r.y + d.y * r.x) / g.w;
    float w = ci.w * exp(-0.5 * (x * x + y * y));
    total += w;
    acc += w * ci.xyz;
  }
  float3 lab = acc / (total + 1e-4);
  float cover = (1.0 - exp(-total)) * clamp(k * 2.5, 0.0, 1.0);

  // Ignition flash: the cap burns towards white as it catches.
  float rim = 1.0 - smoothstep(0.0, 0.9, u);
  float wh = clamp(max(heat - 1.0, 0.0) * 0.45 * rim * clamp(k, 0.0, 1.0), 0.0, 1.0);
  lab.x += wh * (0.995 - lab.x);
  lab.yz *= 1.0 - wh * 0.85;
  // Read: the fire cools to an ember, the brightest part at the cap. The
  // flame is cut back softly rather than squeezed: squeezing piles the dark
  // trailing blobs onto the cap and the blend turns to mauve mud, and so
  // does draining chroma or coverage on the dark pill.
  float reachU = mix(6.0, ember, cool);
  cover *= 1.0 - smoothstep(reachU - 0.4, reachU + 0.4, u);
  lab.x *= 1.0 - 0.05 * cool;
  // Keep the dye faint under the copy; the reference leaves ~0.45 h clear.
  cover *= mix(0.12, 1.0, smoothstep(guard - 0.15 * h, guard + 0.35 * h, xy.x));

  float3 c = mix(body, oklabToSrgb(lab), cover);
  return half4(half3(c + dither), 1.0);
}

}  // namespace flame

// The render pass behind FlareSurfaceView: one triangle covering the pill's
// drawable, every fragment shaded at its position in points.

struct FlareVertexOut {
  float4 position [[position]];
};

vertex FlareVertexOut flareVertex(uint vid [[vertex_id]]) {
  float2 p = float2(float((vid << 1) & 2), float(vid & 2));
  FlareVertexOut out;
  out.position = float4(p * 2.0 - 1.0, 0.0, 1.0);
  return out;
}

// params: size.x, size.y, stretch, heat, cool, ember, guard, body.r, body.g,
// body.b, pixels per point.
fragment half4 flareFragment(FlareVertexOut in [[stage_in]],
                             constant float *params [[buffer(0)]],
                             constant float *geo [[buffer(1)]],
                             constant float *col [[buffer(2)]],
                             constant float *rot [[buffer(3)]]) {
  float2 xy = in.position.xy / params[10];
  return flame::shade(xy, float2(params[0], params[1]), params[2], params[3], params[4], params[5],
                      params[6], float3(params[7], params[8], params[9]), geo, col, rot);
}
"""
}
