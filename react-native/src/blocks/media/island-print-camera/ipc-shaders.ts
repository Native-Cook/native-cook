// Island Print Camera: SkSL. Colours are written in sRGB; `toOut` re-encodes them for the canvas (Skia's runtime
// effect output is read as Display P3 on iOS, which oversaturates sRGB values otherwise).

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
 * The island that becomes the camera body: one rounded box SDF gives the black bezel, the raised lip of red plastic
 * round it (lit on top, shaded below), its contact shadow on the shell and the flash bloom. Transparent elsewhere,
 * so the red page shows through.
 */
const BODY = `${COLOR}
uniform float4 uBody;   // x, y, w, h (pt)
uniform float uRad;
uniform float uBloom;
uniform float uLip;     // 0..1 how raised the lip is
uniform float uDip;     // the slot's lower edge dip (pt)
uniform float uShellFx; // 1: draw the shell's plastic sheen and grain (header canvas), 0: body only (overlay)

float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }

float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 xy) {
  float2 b = uBody.zw * 0.5;
  float2 c = uBody.xy + b;
  float r = min(uRad, min(b.x, b.y));
  float2 bb = b + float2(0.0, uDip * 0.5);
  float2 cc = c + float2(0.0, uDip * 0.5);
  float d = sdBox(xy - cc, bb, r);

  half4 col = half4(0.0);
  // the shell's moulded plastic: a soft vertical sheen (white 6 % at the top to black 8 % at 420 pt) and a fine grain
  if (uShellFx > 0.5) {
    float sy = clamp(xy.y / 420.0, 0.0, 1.0);
    float wa = 0.06 * (1.0 - smoothstep(0.0, 0.55, sy));
    float ba = 0.08 * smoothstep(0.45, 1.0, sy);
    col = half4(half3(1.0) * wa, wa);
    col = half4(col.rgb * (1.0 - ba), ba + col.a * (1.0 - ba));
    float g = (hash2(floor(xy * 2.0)) - 0.5) * 0.06;
    float ga = abs(g);
    half3 gc = g > 0.0 ? half3(1.0) : half3(0.0);
    col = half4(gc * ga + col.rgb * (1.0 - ga), ga + col.a * (1.0 - ga));
  }

  // contact shadow under the body, tinted dark red: [0, 6, 14, 0.30]
  float ds = sdBox(xy - cc - float2(0.0, 6.0), bb, r);
  float sh = 0.30 * uLip * (1.0 - smoothstep(-4.0, 14.0, ds));
  half3 shc = toOut(half3(0.478, 0.059, 0.047));
  col = half4(shc * sh + col.rgb * (1.0 - sh), sh + col.a * (1.0 - sh));

  // the lip: a solid 3 pt bevel of red plastic round the cut-out, lit on its top arc, shaded below, with a thin
  // specular line on its outer top edge so it reads as a raised rim
  float band = 1.0 - smoothstep(2.4, 3.4, d);
  float ny = clamp((xy.y - cc.y) / max(bb.y, 1.0), -1.0, 1.0);
  half3 lit = toOut(half3(0.957, 0.329, 0.290));
  half3 shade = toOut(half3(0.725, 0.133, 0.118));
  float k = smoothstep(-0.6, 0.6, ny);
  float la = band * 0.95 * clamp(uLip * 1.4, 0.35, 1.0);
  half3 lc = mix(lit, shade, k);
  col = half4(lc * la + col.rgb * (1.0 - la), la + col.a * (1.0 - la));
  float spec = (1.0 - smoothstep(0.0, 0.9, abs(d - 2.6))) * (1.0 - k) * 0.35 * uLip;
  col = half4(half3(1.0) * spec + col.rgb * (1.0 - spec), spec + col.a * (1.0 - spec));

  // white flash bloom on the shell round the body
  float bl = uBloom * exp(-max(d, 0.0) / 40.0) * step(0.0, d);
  col = half4(half3(bl) + col.rgb * (1.0 - bl), bl + col.a * (1.0 - bl));

  // the bezel (anti-aliased over ~1 px)
  float inside = 1.0 - smoothstep(-0.6, 0.6, d);
  half3 bez = toOut(half3(0.039, 0.024, 0.027));
  return half4(bez * inside + col.rgb * (1.0 - inside), inside + col.a * (1.0 - inside));
}
`;

/**
 * A dome of soft plastic that rises out of the red shell: a spherical height field lit from above (the design
 * system's light, el 55°). Its colour floods down from the top, so it is red plastic until the colour arrives;
 * `uRing` draws the flip button's engraved ring instead of a coloured cap.
 */
const DOME = `${COLOR}
uniform float2 uC;
uniform float uR;
uniform float uRise;
uniform float uDepth;
uniform float uFlood;
uniform float3 uDome;
uniform float3 uShell;
uniform float uDim;
uniform float uHalo;
uniform float3 uHaloCol;
uniform float uRing;
uniform float uDot;

half4 main(float2 xy) {
  float2 q = xy - uC;
  float rr = length(q) / uR;
  float rise = max(uRise, 0.0);
  float H = (uRing > 0.5 ? 0.02 : 0.22) * 2.0 * uR * rise * (1.0 - 0.8 * uDepth);
  half4 col = half4(0.0);

  // outside: foot shadow (contact + ambient, tinted), AO, LED halo
  float dpt = length(q - float2(0.0, 2.0 * rise)) - uR;
  float sa = rise * (uRing > 0.5 ? 0.0 : 1.0) * (1.0 - 0.6 * uDepth) * (0.30 * (1.0 - smoothstep(-1.0, 3.0, dpt)) + 0.18 * (1.0 - smoothstep(-2.0, 14.0, dpt - 4.0)));
  half3 tint = toOut(half3(0.478, 0.059, 0.047));
  col = half4(tint * sa, sa);
  if (uHalo > 0.0) {
    float h = uHalo * clamp(rise * 4.0, 0.0, 1.0) * exp(-max(length(q) - uR, 0.0) / 16.0) * step(uR, length(q));
    half3 hc = toOut(half3(uHaloCol));
    col = half4(hc * h + col.rgb * (1.0 - h), h + col.a * (1.0 - h));
  }

  if (rr < 1.0) {
    float s = sqrt(max(1.0 - rr * rr, 1e-4));
    // height h = s * H; gradient dh/dq = -q / (uR^2 s) * H
    float2 g = -q / (uR * uR * s) * H;
    float3 n = normalize(float3(-g.x, -g.y, 1.0));
    float3 L = normalize(float3(0.0, -0.82, 0.57));
    float diff = clamp(dot(n, L), 0.0, 1.0);
    // flood from the top of the dome downwards
    float v = (xy.y - (uC.y - uR)) / (2.0 * uR);
    float fl = 1.0 - smoothstep(uFlood * 1.25 - 0.25, uFlood * 1.25, v);
    float3 base = mix(uShell, uDome, uRing > 0.5 ? 0.0 : fl);
    float3 lit = base * (0.62 + 0.48 * diff);
    // soft highlight 18 % above centre (roughness ~0.45)
    float3 hv = normalize(L + float3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(n, hv), 0.0), 22.0) * 0.32 * clamp(rise, 0.0, 1.0) * (1.0 - 0.3 * uDepth);
    lit += float3(spec);
    // AO at the foot
    lit *= 1.0 - 0.4 * smoothstep(0.75, 1.0, rr) * clamp(rise, 0.0, 1.0);
    lit *= uDim;
    half3 o = toOut(half3(clamp(lit, 0.0, 1.0)));
    // engraved ring of the flip button
    if (uRing > 0.5) {
      float ring = 1.0 - smoothstep(0.55, 1.05, abs(length(q) - (uR - 0.9)));
      o = mix(o, toOut(half3(0.612, 0.086, 0.071)), ring);
    }
    // a sharp 2 pt specular dot on the LED's glass
    if (uDot > 0.5) {
      float dd = length(q - float2(-0.30, -0.34) * uR);
      float dt = (1.0 - smoothstep(0.6, 1.4, dd)) * 0.75 * clamp(rise, 0.0, 1.0);
      o = mix(o, half3(1.0), dt);
    }
    float a = 1.0 - smoothstep(0.985, 1.0, rr);
    // a flat dome (rise 0) is invisible: the page is the same red
    float vis = clamp(rise * 6.0, 0.0, 1.0);
    a *= vis;
    if (uRing > 0.5) {
      float ring2 = 1.0 - smoothstep(0.55, 1.05, abs(length(q) - (uR - 0.9)));
      a *= ring2;
    }
    col = half4(o * a + col.rgb * (1.0 - a), a + col.a * (1.0 - a));
  }
  return col;
}
`;

/**
 * Instant-film development: the picture comes up dye layer by dye layer (cyan/blue first, then green, then red),
 * unevenly (value noise), from a pale grey base; contrast and saturation arrive with it.
 */
const DEVELOP = `${COLOR}
uniform shader uPhoto;
uniform float uD;
uniform float2 uSize;

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}

half4 main(float2 xy) {
  half4 ph = uPhoto.eval(xy);
  float3 c = float3(ph.rgb);
  float n = (vnoise(xy / uSize * 4.0) * 0.65 + vnoise(xy / uSize * 9.0) * 0.35 - 0.5) * 0.16;
  float dd = uD + n;
  // contrast 0.7 -> 1, saturation 0.5 -> 1
  float con = mix(0.7, 1.0, clamp(uD, 0.0, 1.0));
  float sat = mix(0.5, 1.0, clamp(uD, 0.0, 1.0));
  float y = dot(c, float3(0.299, 0.587, 0.114));
  c = mix(float3(y), c, sat);
  c = (c - 0.5) * con + 0.5;
  float3 base = float3(0.878, 0.878, 0.886);
  // the cyan dye comes first: a pale cyan-grey ghost of the picture (dark parts deep teal), then the magenta and
  // yellow layers bring the colour, red last
  float3 ghost = mix(float3(0.80, 0.87, 0.89), float3(0.20, 0.33, 0.39), 1.0 - y);
  float3 o = mix(base, ghost, smoothstep(0.0, 0.45, dd));
  float kg = smoothstep(0.35, 0.80, dd);
  float kb = smoothstep(0.45, 0.90, dd);
  float kr = smoothstep(0.55, 1.00, dd);
  o = float3(mix(o.r, c.r, kr), mix(o.g, c.g, kg), mix(o.b, c.b, kb));
  return half4(toOut(half3(clamp(o, 0.0, 1.0))), 1.0);
}
`;

/** The shell's moulded plastic over the whole screen: a soft vertical sheen and a fine grain. */
const SHELL = `
uniform float uH;
float hash2(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
half4 main(float2 xy) {
  float sy = clamp(xy.y / uH, 0.0, 1.0);
  float wa = 0.06 * (1.0 - smoothstep(0.0, 0.4, sy));
  float ba = 0.08 * smoothstep(0.35, 1.0, sy);
  half4 col = half4(half3(1.0) * wa, wa);
  col = half4(col.rgb * (1.0 - ba), ba + col.a * (1.0 - ba));
  float g = (hash2(floor(xy * 2.0)) - 0.5) * 0.06;
  float ga = abs(g);
  half3 gc = g > 0.0 ? half3(1.0) : half3(0.0);
  return half4(gc * ga + col.rgb * (1.0 - ga), ga + col.a * (1.0 - ga));
}
`;

const make = (src: string, name: string) => {
  const e = Skia.RuntimeEffect.Make(src);
  if (!e) throw new Error(`Island Print Camera: the ${name} shader failed to compile`);
  return e;
};

export const BODY_FX = make(BODY, 'body');
export const DOME_FX = make(DOME, 'dome');
export const DEVELOP_FX = make(DEVELOP, 'develop');
export const SHELL_FX = make(SHELL, 'shell');
