/**
 * The glass dome and the magnifier it becomes: inside the disc the map under the first screen shows enlarged, bent
 * inward over the last 18 pt of the rim the way a thick lens bends it (red and blue a hair apart there), with a crisp
 * rim of light from above and a soft shadow just outside. As it opens out the enlargement and the rim fade to nothing,
 * so the disc grown past the screen's corners is the map itself.
 */
import { Skia } from '@shopify/react-native-skia';

const SKSL = `
uniform shader map;
uniform float2 center;
uniform float radius;
uniform float mag;
uniform float rim;
uniform float lift;
uniform float2 look; // where the lens looks, from its own centre: the dome looks up at the middle of the map

half4 main(float2 p) {
  float2 d = p - center;
  float r = length(d);
  float edge = radius - r;
  float2 u = d / max(r, 0.001);
  if (edge < 0.0) {
    // A soft shadow under the glass, mostly below it (light from above).
    float o = -edge;
    float a = rim * lift * 0.16 * exp(-(o / 9.0) * (o / 9.0)) * (0.35 + 0.65 * smoothstep(-0.5, 1.0, u.y));
    return half4(half3(0.11, 0.17, 0.13) * a, a);
  }
  float t = clamp(edge / 18.0, 0.0, 1.0);
  float band = (1.0 - t) * (1.0 - t) * rim;
  float2 q = center + look + d / mag - u * band * 20.0;
  float spread = band * 2.2;
  float3 col = float3(map.eval(q - u * spread).r, map.eval(q).g, map.eval(q + u * spread).b);
  // Light: a crisp hairline on the upper rim, a faint one below, and the light the rim bends inward.
  float hair = (1.0 - smoothstep(0.0, 1.4, edge)) * rim;
  float up = pow(max(dot(u, float2(-0.32, -0.95)), 0.0), 1.4);
  float down = pow(max(dot(u, float2(0.25, 0.97)), 0.0), 2.0);
  col = mix(col, float3(1.0), hair * (0.55 + 0.4 * up));
  col += band * 0.1 * up;
  // The far side of the rim, away from the light, darkens: the glass has a thickness.
  col *= 1.0 - 0.14 * band * (1.0 - up) * (0.4 + 0.6 * down);
  float cov = smoothstep(-0.6, 0.6, edge);
  return half4(half3(col * cov), half(cov));
}
`;

const effect = Skia.RuntimeEffect.Make(SKSL);
if (!effect) throw new Error('glass-disc-onboarding: lens shader failed to compile');
export const LENS_EFFECT = effect;
