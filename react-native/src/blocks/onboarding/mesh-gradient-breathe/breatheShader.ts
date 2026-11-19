// The light behind Breathe Mesh: a true mesh gradient that inhales and exhales, and the glass orb
// that guides the breath, in one Skia RuntimeEffect pass.
//
// The mesh is a 5 x 5 grid of control points, each with a position (a regular grid plus an offset,
// `off`) and an OKLab colour (`lab`), interpolated with a Catmull-Rom (bicubic) patch surface: smooth
// across patch borders, and moving a point drags its colour with it. The shader inverts the warp for
// each pixel (uv <- q - D(uv), two fixed-point steps) and interpolates the colour grid at uv.
//
// The orb is a glass sphere over that light: inside it the mesh is sampled again through a lens
// (magnified towards the centre), with a fresnel rim, a soft inner shadow at the bottom, a specular
// highlight and a contact glow under it. No second layer, no blur: one pass.
//
// Output: Display P3 on iOS (`wide` = 1; Skia treats runtime-effect output as P3 there), then a
// triangular dither and a static grain so the soft fields never band.

export const BREATHE_SKSL = `
uniform float2 size;
uniform float2 off[25];
uniform float3 lab[25];
uniform float3 orb;     // centre x, y and radius, in points
uniform float lift;     // 0..1: the orb's rim and glow, with the breath
uniform float wide;     // 1: write Display P3 (iOS)
uniform float dpr;      // pixels per point, for the dither

const int N = 5;
const float G = 4.0;    // N - 1

float cr(float x) {
  x = abs(x);
  if (x < 1.0) return (1.5 * x - 2.5) * x * x + 1.0;
  if (x < 2.0) return ((-0.5 * x + 2.5) * x - 4.0) * x + 2.0;
  return 0.0;
}

// Catmull-Rom weight of control index i at grid coordinate g (0..G); the phantom points past the
// edges repeat the edge point, so the weights always sum to 1.
float wt(float g, int i) {
  float w = cr(g - float(i));
  if (i == 0) w += cr(g + 1.0);
  if (i == N - 1) w += cr(g - float(N));
  return w;
}

float2 disp(float2 uv) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[5];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float2 acc = float2(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) { acc += (wx[i] * wy) * off[j * N + i]; }
  }
  return acc;
}

float3 meshLab(float2 uv) {
  float2 g = clamp(uv, 0.0, 1.0) * G;
  float wx[5];
  for (int i = 0; i < N; i++) { wx[i] = wt(g.x, i); }
  float3 acc = float3(0.0);
  for (int j = 0; j < N; j++) {
    float wy = wt(g.y, j);
    for (int i = 0; i < N; i++) { acc += (wx[i] * wy) * lab[j * N + i]; }
  }
  return acc;
}

// Screen point (0..1) -> OKLab colour of the warped mesh there.
float3 mesh(float2 q) {
  float2 uv = q - disp(q);
  uv = q - disp(uv);
  return meshLab(uv);
}

float3 labToLinear(float3 c) {
  float l = c.x + 0.3963377774 * c.y + 0.2158037573 * c.z;
  float m = c.x - 0.1055613458 * c.y - 0.0638541728 * c.z;
  float s = c.x - 0.0894841775 * c.y - 1.2914855480 * c.z;
  l = l * l * l; m = m * m * m; s = s * s * s;
  return float3(
     4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
}

float3 encode(float3 lin) {
  lin = clamp(lin, 0.0, 1.0);
  if (wide > 0.5) {
    lin = float3(
      dot(lin, float3(0.8225, 0.1774, 0.0)),
      dot(lin, float3(0.0332, 0.9669, 0.0)),
      dot(lin, float3(0.0171, 0.0724, 0.9108)));
  }
  float3 lo = lin * 12.92;
  float3 hi = 1.055 * pow(lin, float3(1.0 / 2.4)) - 0.055;
  return mix(lo, hi, step(float3(0.0031308), lin));
}

float h21(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float ign(float2 p) { return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715)))); }


half4 main(float2 p) {
  float2 q = p / size;
  float3 lin = labToLinear(mesh(q));

  float2 d = (p - orb.xy) / orb.z;
  float len = length(d);
  // the orb's soft shadow on the light, a little below it
  float2 ds = (p - orb.xy - float2(0.0, orb.z * 0.18)) / (orb.z * 1.25);
  float sh = 1.0 - smoothstep(0.55, 1.15, length(ds));
  lin *= 1.0 - 0.07 * sh;
  // and the light it gathers around itself
  lin += float3(0.05 + 0.05 * lift) * exp(-max(len - 1.0, 0.0) * 5.0) * step(1.0, len);

  if (len < 1.02) {
    float z = sqrt(max(1.0 - len * len, 0.0));
    // a sphere magnifies its middle: sample closer to the centre, and from a little above
    float2 sp = orb.xy + (p - orb.xy) * (0.42 + 0.38 * len * len) - float2(0.0, orb.z * 0.10);
    float3 seen = labToLinear(mesh(sp / size));
    float fres = pow(1.0 - z, 3.0);
    float3 glass = seen * (1.04 + 0.05 * lift);
    glass = mix(glass, float3(1.0), fres * (0.30 + 0.20 * lift));          // bright rim
    glass *= 1.0 - 0.10 * smoothstep(0.2, 1.0, d.y) * smoothstep(0.4, 1.0, len); // inner shadow, bottom
    float2 hl = d - float2(-0.36, -0.46);
    glass += float3(0.42 * exp(-dot(hl, hl) * 20.0));                        // key highlight
    float2 hl2 = d - float2(0.42, 0.52);
    glass += float3(0.10 * exp(-dot(hl2, hl2) * 30.0));                      // bounce from below
    float aa = 1.0 - smoothstep(1.0 - 1.5 / orb.z, 1.0, len);
    lin = mix(lin, glass, aa);
  }

  float3 rgb = encode(lin);
  float2 px = p * dpr;
  float n = ign(px) + ign(px + float2(41.0, 17.0)) - 1.0;   // triangular dither, +-1 LSB
  float g = h21(floor(px)) - 0.5;                             // static grain
  rgb += n / 255.0 + g * 0.02;
  return half4(half3(rgb), 1.0);
}
`;
