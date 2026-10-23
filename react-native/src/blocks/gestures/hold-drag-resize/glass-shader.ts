/**
 * Liquid Glass for one rounded box: it shows the frosted page under it, bent at the rim the way a thick lens bends
 * light (the page magnifies inward over the last 12 pt, the red and blue a hair apart), tinted by what the box is, lit
 * by a rim highlight from above and a fainter one from below, and lit from inside where a finger presses it.
 *
 * `bg` is the page already frosted (blurred once at mount), in screen points. Everything else is per box.
 */
import { Skia } from '@shopify/react-native-skia';

const SKSL = `
uniform shader bg;
uniform float4 uRect;   // x, y, w, h (pt)
uniform float uRadius;
uniform float4 uTint;   // linear rgb, strength
uniform float uThick;   // 0 flat .. 1 lifted: how far the rim bends the page
uniform float3 uGlow;   // finger x, y, amount
uniform float uAlpha;

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 xy) {
  float2 b = uRect.zw * 0.5;
  float2 p = xy - (uRect.xy + b);
  float r = min(uRadius, min(b.x, b.y));
  float d = sdBox(p, b, r);
  float cov = 1.0 - smoothstep(-0.5, 0.5, d);
  if (cov <= 0.0) { return half4(0.0); }

  float e = 0.5;
  float2 n = float2(sdBox(p + float2(e, 0.0), b, r) - sdBox(p - float2(e, 0.0), b, r),
                    sdBox(p + float2(0.0, e), b, r) - sdBox(p - float2(0.0, e), b, r));
  n = n / max(length(n), 0.0001);

  // Refraction: strongest on the rim, gone 12 pt in; the whole box magnifies the page a little.
  float bevel = min(12.0, min(b.x, b.y));
  float t = clamp(-d / bevel, 0.0, 1.0);
  float lens = (1.0 - t) * (1.0 - t);
  float bend = 7.0 + 7.0 * uThick;
  float2 s = xy - n * lens * bend - p * 0.025;
  float spread = lens * (1.2 + uThick);
  float3 col = float3(bg.eval(s - n * spread).r, bg.eval(s).g, bg.eval(s + n * spread).b);

  // Frost: lifted a little so text on any glass reads; the tint is light the glass gives off, not paint on it, so a
  // coloured block glows instead of going muddy over a dark page.
  col = col * 0.8 + 0.03;
  col = mix(col, uTint.rgb, uTint.a * 0.55) + uTint.rgb * uTint.a * 0.32;

  // Light from above: a crisp hairline on the upper rim, a fainter one below, and the light the rim bends inward, a
  // soft band a few points wide that is brightest where the hairline is.
  float rim = 1.0 - smoothstep(0.0, 1.2, -d);
  float band = (1.0 - smoothstep(0.0, 7.0, -d)) * lens;
  float up = pow(max(dot(n, float2(-0.34, -0.94)), 0.0), 1.4);
  float down = pow(max(dot(n, float2(0.3, 0.95)), 0.0), 2.0);
  float lit = 0.12 + 0.6 * up + 0.24 * down;
  col += rim * lit * (0.9 + 0.3 * uThick);
  col += band * lit * 0.16 * (1.0 + uThick);
  col += mix(uTint.rgb, float3(1.0), 0.5) * rim * uTint.a * 0.6;
  col += (1.0 - smoothstep(-b.y, -b.y + min(28.0, b.y), p.y)) * 0.03;
  // The inner edge darkens a touch, so the glass has a thickness.
  col *= 1.0 - 0.1 * smoothstep(1.6, 5.0, -d) * (1.0 - smoothstep(5.0, 10.0, -d));

  // A finger lights the glass from inside.
  float2 f = xy - uGlow.xy;
  col += uGlow.z * 0.16 * exp(-dot(f, f) / (2.0 * 46.0 * 46.0));

  float a = cov * uAlpha;
  return half4(half3(col * a), half(a));
}
`;

const effect = Skia.RuntimeEffect.Make(SKSL);
if (!effect) throw new Error('hold-drag-resize: glass shader failed to compile');
export const GLASS_EFFECT = effect;
