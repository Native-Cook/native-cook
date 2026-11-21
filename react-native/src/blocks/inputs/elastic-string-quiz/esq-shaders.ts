// Elastic String Quiz: SkSL. Colours are written in sRGB; `toOut` re-encodes them for the canvas (Skia's runtime
// effect output is read as Display P3 on iOS, which would oversaturate the plum otherwise).

import { Skia } from '@shopify/react-native-skia';

const COLOR = `
uniform float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(half3 s) {
  if (uP3 < 0.5) return s;
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
`;

/**
 * The plum field: the scene's one light. A radial glow (#752C48 at its centre, #371737 at r ≈ 260, #2B112C at the
 * edges) with a gentle vertical falloff; its centre and gain follow the cords' tension, `uCool` dims and cools it
 * when an answer is rejected. ±1/255 dither and a little static grain per device pixel keep it from banding.
 */
const FIELD = `${COLOR}
uniform float2 uC;
uniform float uGain;
uniform float uCool;
uniform float uPx;
uniform float2 uSpan; // top and bottom of the screen (pt)

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

half4 main(float2 xy) {
  float3 centre = mix(float3(0.459, 0.173, 0.282), float3(0.353, 0.118, 0.227), uCool);
  float3 mid = float3(0.216, 0.090, 0.216);
  float3 edge = float3(0.169, 0.067, 0.173);
  float2 d = (xy - uC) * float2(1.0, 0.86);
  float r = length(d) / 260.0;
  float3 glow = mix(centre, mid, smoothstep(0.0, 1.0, r));
  glow = mix(glow, edge, smoothstep(0.85, 2.0, r));
  // the glow's strength: tension leans and brightens it
  float3 col = edge + (glow - edge) * uGain;
  // vertical falloff to the top and bottom edges
  float v = (xy.y - uSpan.x) / max(uSpan.y - uSpan.x, 1.0);
  float vf = smoothstep(0.0, 0.16, v) * smoothstep(1.0, 0.8, v);
  col = mix(edge, col, mix(0.55, 1.0, vf));
  float2 px = floor(xy * uPx);
  float n1 = hash(px);
  float n2 = hash(px + float2(17.0, 31.0));
  col += (n1 + n2 - 1.0) / 255.0;
  col *= 1.0 + (hash(px * 0.5 + float2(3.0, 7.0)) - 0.5) * 0.03;
  return half4(toOut(half3(clamp(col, 0.0, 1.0))), 1.0);
}
`;

const make = (src: string, name: string) => {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error(`Elastic String Quiz: the ${name} shader failed to compile`);
  return e;
};

export const FIELD_FX = make(FIELD, 'field');
