import { Skia } from '@shopify/react-native-skia';

// The same function as the Metal one (StripeSwirlType.metal), in SkSL, in pixels (`scale` turns
// them into points). The layer is a mask: black, with the cover line's words drawn white and
// blurred by 1.6 pt. Each pixel is carried back through the swirl, then the words are drawn there
// as horizontal stripes: strokes 1.7 pt half thick on screen however much the swirl squeezes them
// (overlapping like drawn lines, the lower stripe on top), thinner towards the letter edges, and,
// where the swirl twists hard, broken into capsules coloured from the palette.
// Skia treats RuntimeEffect output as Display P3 on iOS, so colours go through toP3 when p3 = 1.
const FIELD_SKSL = `
uniform shader image;
uniform float scale;
uniform float2 size;
uniform float2 centre;
uniform float twist;
uniform float radius;
uniform float spin;
uniform float p3;
uniform vec4 inkColor;
uniform vec4 backColor;
uniform vec4 c0;
uniform vec4 c1;
uniform vec4 c2;
uniform vec4 c3;
uniform vec4 c4;

float hash1(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float hash2(float2 v) { return fract(sin(dot(v, float2(127.1, 311.7))) * 43758.5453); }
float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

half4 main(float2 fragCoord) {
  float2 p = fragCoord / scale;
  float2 d = p - centre;
  float r = length(d);
  float R = max(radius, 1.0);
  float s = clamp(1.0 - r / R, 0.0, 1.0);
  float theta = twist * s * s + spin * s;
  float slope = r < R ? -(2.0 * twist * s + spin) / R : 0.0;
  float2 rhat = r > 0.001 ? d / r : float2(0.0);
  float cs = cos(theta);
  float sn = sin(theta);
  float2 q = centre + float2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y);
  float2 gy = float2(-sn, cs) - (cs * d.x + sn * d.y) * slope * rhat;
  float2 gx = float2(cs, sn) + (-sn * d.x + cs * d.y) * slope * rhat;
  float g2 = max(dot(gy, gy), 1e-4);
  float g = sqrt(g2);
  float gxl = max(length(gx), 1e-3);

  float2 uv = p / size - 0.5;
  float vig = 1.0 - 0.55 * smoothstep(0.25, 0.85, length(uv * float2(size.x / size.y, 1.0)) * 1.25);
  float grain = (hash2(floor(p * 2.0)) - 0.5) * 0.05;
  float3 col = backColor.rgb * vig + grain * 0.35;

  float period = 6.0;
  float aa = 0.45;
  float colour = smoothstep(0.5, 1.8, abs(theta));
  float broken = max(colour, smoothstep(1.2, 2.0, g));
  float base = floor(q.y / period);
  for (int k = -2; k <= 2; k++) {
    float row = base + float(k);
    float yk = (row + 0.5) * period;
    float dyk = q.y - yk;
    float dist = abs(dyk) / g;
    if (dist <= 1.7 + aa) {
      float2 st = -dyk * gy / g2;
      float sx = q.x + dot(gx, st);
      float m = image.eval(float2(sx, yk) * scale).r;
      float halfT = 1.7 * smoothstep(0.22, 0.85, m);
      if (halfT >= 0.3) {
        float hr = hash1(row + 3.1);
        float len = 22.0 + 26.0 * hr;
        float xs = sx + hr * 97.0;
        float piece = floor(xs / len);
        float xm = (piece + 0.5) * len;
        float ex = max(abs(xs - xm) / gxl - ((0.5 * len - 1.2) / gxl - halfT), 0.0) * broken;
        float e = length(float2(ex, dist)) - halfT;
        float cover = 1.0 - smoothstep(-aa, aa, e);

        float pick = hash2(float2(piece, row));
        float3 tint = c0.rgb;
        if (pick > 0.18) { tint = c1.rgb; }
        if (pick > 0.36) { tint = c2.rgb; }
        if (pick > 0.54) { tint = c3.rgb; }
        if (pick > 0.72) { tint = c4.rgb; }
        if (pick > 0.9) { tint = inkColor.rgb; }
        col = mix(col, mix(inkColor.rgb, tint, colour), cover);
      }
    }
  }
  return half4(half3(p3 > 0.5 ? toP3(col) : col), 1.0);
}
`;

export const fieldEffect = Skia.RuntimeEffect.Make(FIELD_SKSL)!;
