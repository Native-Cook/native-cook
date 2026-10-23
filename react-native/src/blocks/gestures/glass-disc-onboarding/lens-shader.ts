/**
 * The glass disc as a lens, drawn over the screen. Inside the disc every pixel samples a texture through a
 * ball-lens mapping: the image grows outwards from the centre, turns over at the fold radius and comes back
 * mirrored towards the rim, as the source's glass does. Each colour channel is pushed a little along the radius
 * past the middle of the disc, which gives the rainbow fringes. The texture holds the second screen's text at its
 * place (for the bead and the rewind) and, below it, a lone copy of the headline that the rising lens shows.
 * Outside the disc only a soft contact shadow is drawn; everything else stays transparent.
 */
import { Skia } from '@shopify/react-native-skia';

const source = `
uniform shader tex;
uniform float2 size;
uniform float texScale;
uniform float2 center;
uniform float radius;
// Upgrade: squash and stretch along y (volume kept), a travelling rim glint, a tilt-led highlight, the ring's
// light cast on the backdrop, and dispersion that grows with speed.
uniform float stretch;
uniform float glint;
uniform float glintAng;
uniform float2 light;
uniform float glow;
uniform float spreadBoost;
uniform float alpha;
uniform float2 texOrigin;
uniform float reveal;
uniform float fold;
uniform float foldBack;
uniform float foldShape;
uniform float kY;
uniform float kX;
uniform float squeezeX;
uniform float spread;
uniform float fringeFrom;
uniform float fade;
uniform float mirrorFloor;
uniform float ring;
uniform float ringFrom;
uniform float ringTo;
uniform float rimLight;
uniform float rimWidth;
uniform float shadowAt;
uniform float shadowWidth;
uniform float shadowAlpha;
uniform float beadFrom;
uniform float beadSpan;
uniform float beadFrost;
uniform float domeFrost;
layout(color) uniform half3 bgTL;
layout(color) uniform half3 bgTR;
layout(color) uniform half3 bgBL;
layout(color) uniform half3 bgBR;
layout(color) uniform half3 shadowColor;
layout(color) uniform half3 ringInner;
layout(color) uniform half3 ringCore;
layout(color) uniform half3 ringOuter;
layout(color) uniform half3 ringBlush;

float3 backdrop(float2 p) {
  float2 u = clamp(p / size, 0.0, 1.0);
  return mix(mix(float3(bgTL), float3(bgTR), u.x), mix(float3(bgBL), float3(bgBR), u.x), u.y);
}

// Radial reach of the lens (share of R) for a pixel at radius r (share of R).
float reach(float r) {
  float peak = fold * foldShape / (foldShape + 1.0);
  if (r < fold) return r * (1.0 - pow(r / fold, foldShape) / (foldShape + 1.0));
  float b = r - fold;
  return peak - foldBack * b * b;
}

half4 taken(float2 q) {
  return tex.eval(q * texScale);
}

half4 main(float2 p) {
  if (alpha <= 0.001 || radius <= 0.5) return half4(0.0);
  // A small drop of glass reads frosted and casts a firmer shadow (the bead in the source).
  float small = clamp((beadFrom - radius) / beadSpan, 0.0, 1.0);
  float k = sqrt(1.0 + stretch);
  float2 d = float2((p.x - center.x) * k, (p.y - center.y) / k) / radius;
  float r = length(d);
  float len = r * radius;
  if (r >= 1.0) {
    // A soft contact shadow just outside the glass.
    float t = (len - radius - shadowAt) / shadowWidth;
    // The light comes from above: the shadow sits mostly under the glass.
    float a = shadowAlpha * (1.0 + small) * exp(-t * t) * alpha * (0.5 + smoothstep(-0.4, 1.0, d.y));
    // The iridescent band throws its colour on the backdrop under the glass.
    float g = glow * ring * exp(-pow((r - 1.0) / 0.22, 2.0)) * smoothstep(-0.2, 0.8, d.y) * alpha;
    float3 gc = mix(float3(ringCore), float3(ringOuter), 0.5 + 0.5 * d.x);
    return half4(half3(float3(shadowColor) * a + gc * g), a + g);
  }
  float3 bg = backdrop(p);
  float2 u = r > 0.0001 ? d / r : float2(0.0);
  // The fold sits further out across than up and down, as in the source.
  float f = reach(length(float2(d.x * squeezeX, d.y)));
  float2 off = float2(u.x * f * kX, u.y * f * kY) * radius;
  float fr = clamp((r - fringeFrom) / max(0.001, fold - fringeFrom), 0.0, 1.0);
  float2 sp = u * spread * (1.0 + spreadBoost) * fr;
  // Past the fold the turned-over copy is fainter, as in the source.
  float rs = length(float2(d.x * squeezeX, d.y));
  float vis = reveal * (rs < fold ? 1.0 : clamp(1.0 - (rs - fold) * fade, mirrorFloor, 1.0));
  half4 cr = taken(texOrigin + off + sp);
  half4 cg = taken(texOrigin + off);
  half4 cb = taken(texOrigin + off - sp);
  float3 col = float3(
    bg.r * (1.0 - cr.a * vis) + cr.r * vis,
    bg.g * (1.0 - cg.a * vis) + cg.g * vis,
    bg.b * (1.0 - cb.a * vis) + cb.b * vis);
  // The iridescent band while the dome is pulled (frames 3–11): a crescent inside the lower half, cyan on its
  // inner edge, cobalt core, violet outside fading to blush; the glass below it turns milky.
  float below = smoothstep(-0.12, 0.42, d.y);
  float across = clamp((r - ringFrom) / (ringTo - ringFrom), 0.0, 1.0);
  float band = smoothstep(0.0, 0.18, across) * (1.0 - smoothstep(0.72, 1.0, across)) * below;
  float3 iri = mix(float3(ringInner), float3(ringCore), smoothstep(0.08, 0.42, across));
  iri = mix(iri, float3(ringOuter), smoothstep(0.5, 0.85, across));
  iri = mix(iri, float3(ringBlush), smoothstep(0.82, 1.0, across) * 0.6);
  col = mix(col, float3(1.0), ring * 0.22 * smoothstep(ringTo - 0.05, 1.0, r) * below);
  col = mix(col, iri, clamp(band * ring, 0.0, 1.0));
  // The resting dome is milky; it clears as it is pulled (frames 1 → 12).
  col = mix(col, float3(1.0), domeFrost * (0.55 + 0.45 * smoothstep(0.2, -0.9, d.y)));
  col = mix(col, float3(1.0), beadFrost * small * (0.6 + 0.4 * smoothstep(0.3, -0.8, d.y)));
  // Bright inner rim where the glass edge catches the light, a glint that travels along it, and a soft highlight
  // that follows the device's tilt (or the finger).
  float rimBand = smoothstep(1.0 - rimWidth / radius, 1.0 - 1.5 / radius, r);
  col += rimLight * rimBand;
  float ang = atan(d.y, d.x);
  float da = ang - glintAng;
  da = da - 6.2831853 * floor((da + 3.1415927) / 6.2831853);
  col += glint * 0.32 * exp(-da * da / 0.05) * smoothstep(0.82, 0.99, r);
  float2 hc = float2(-0.36, -0.46) + light * 0.3;
  col += 0.035 * exp(-dot(d - hc, d - hc) / 0.06) * (1.0 - small * 0.5);
  // Antialiased edge, blending into the contact shadow.
  float px = 1.0 / radius;
  float edge = 1.0 - smoothstep(1.0 - 1.5 * px, 1.0, r);
  float t = (len - radius - shadowAt) / shadowWidth;
  float sa = shadowAlpha * (1.0 + small) * exp(-t * t);
  float a = alpha * (edge + (1.0 - edge) * sa);
  float3 outCol = col * edge * alpha + float3(shadowColor) * sa * (1.0 - edge) * alpha;
  return half4(half3(outCol), a);
}
`;

export const lensEffect = Skia.RuntimeEffect.Make(source);

// The same backdrop on its own, under everything.
export const backdropEffect = Skia.RuntimeEffect.Make(`
uniform float2 size;
layout(color) uniform half3 bgTL;
layout(color) uniform half3 bgTR;
layout(color) uniform half3 bgBL;
layout(color) uniform half3 bgBR;
half4 main(float2 p) {
  float2 u = clamp(p / size, 0.0, 1.0);
  return half4(half3(mix(mix(float3(bgTL), float3(bgTR), u.x), mix(float3(bgBL), float3(bgBR), u.x), u.y)), 1.0);
}
`);

export function rgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}
