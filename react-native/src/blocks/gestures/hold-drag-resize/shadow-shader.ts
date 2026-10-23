/**
 * Every card's shadow in one pass: the exact Gaussian blur of a rounded box, integrated in closed form along x (erf)
 * and sampled four times along y, for two lights. An ambient layer stays tight under the card; a key layer from
 * above drops and softens as the card rises. Elevation (pt) is the only input, so pressing in, lifting and settling
 * all come out of one number per card.
 */
import { Skia } from '@shopify/react-native-skia';

export const SHADOW_SLOTS = 8;

const SKSL = `
uniform float4 uRect[8];  // x, y, w, h in pt
uniform float4 uParam[8]; // elevation pt, opacity, corner radius, unused
uniform float uCount;
uniform float3 uShade;

float2 erf2(float2 x) {
  float2 s = sign(x);
  float2 a = abs(x);
  float2 r = 1.0 + (0.278393 + (0.230389 + 0.078108 * (a * a)) * a) * a;
  r *= r;
  return s - s / (r * r);
}

float gauss(float x, float sigma) {
  return exp(-(x * x) / (2.0 * sigma * sigma)) / (2.50662827 * sigma);
}

// Coverage at p (from the box centre) of a box with half size hs and corner r, blurred by sigma.
float boxShadow(float2 p, float2 hs, float r, float sigma) {
  float lo = clamp(-3.0 * sigma, p.y - hs.y, p.y + hs.y);
  float hi = clamp(3.0 * sigma, p.y - hs.y, p.y + hs.y);
  float stepY = (hi - lo) / 4.0;
  float y = lo + stepY * 0.5;
  float v = 0.0;
  for (int i = 0; i < 4; i++) {
    float py = p.y - y;
    float d = min(hs.y - r - abs(py), 0.0);
    float curved = hs.x - r + sqrt(max(0.0, r * r - d * d));
    float2 span = 0.5 + 0.5 * erf2((p.x + float2(-curved, curved)) * (0.70710678 / sigma));
    v += (span.y - span.x) * gauss(y, sigma) * stepY;
    y += stepY;
  }
  return v;
}

half4 main(float2 xy) {
  float keep = 1.0;
  for (int i = 0; i < 8; i++) {
    if (float(i) < uCount) {
      float4 R = uRect[i];
      float4 P = uParam[i];
      float e = P.x;
      float2 hs = R.zw * 0.5;
      float2 p = xy - (R.xy + hs);
      float sigK = 0.5 + 1.0 * e;
      float dyK = 0.4 + 0.75 * e;
      float reach = 3.0 * sigK + dyK + 2.0;
      if (abs(p.x) < hs.x + reach && abs(p.y) < hs.y + reach) {
        float r = min(P.z, min(hs.x, hs.y));
        // Ambient: hugs the card, barely moves with height.
        float sigA = 0.6 + 0.35 * e;
        float aA = 0.075 * boxShadow(p - float2(0.0, 0.3 + 0.15 * e), hs, r, sigA);
        // Key light from above: drops, softens and pulls in a little as the card rises.
        float2 hsK = max(hs - float2(0.35 * e, 0.2 * e), float2(1.0));
        float aK = min(0.06 + 0.016 * e, 0.24) * boxShadow(p - float2(0.0, dyK), hsK, min(r, min(hsK.x, hsK.y)), sigK);
        keep *= (1.0 - aA * P.y) * (1.0 - aK * P.y);
      }
    }
  }
  float alpha = 1.0 - keep;
  return half4(half3(uShade) * alpha, alpha);
}
`;

const effect = Skia.RuntimeEffect.Make(SKSL);
if (!effect) throw new Error('hold-drag-resize: shadow shader failed to compile');
export const SHADOW_EFFECT = effect;
