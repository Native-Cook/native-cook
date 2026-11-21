import { Skia } from '@shopify/react-native-skia';

// One field, one emitter. Coordinates are the 402 × 874 reference canvas, in points.
//
// The pool is a sheared light field. The knob pumps its light through the vein (a quadratic from the knob to the pool's
// centre) with a bead per detent, the drain and the bloom pour through that same vein, a finger on the pool dents the
// field, and where the pool has gone a stain is left. The vein's geometry is the one in `gdr-theme.ts` (G.vein).
export const GLOW_SOURCE = `
uniform shader grain;
uniform float uP3;
uniform float2 center;
uniform float radius;
uniform float shear;
uniform float amount;
uniform float ring;
uniform float ringAlpha;
uniform float halo;
uniform float haloAlpha;
uniform float vein;
uniform float2 plug;
uniform float plugAlpha;
uniform float stain;
uniform float3 dent;
uniform float4 fring;
uniform float3 b0;
uniform float3 b1;
uniform float3 b2;
uniform float3 b3;
uniform float3 b4;
uniform float3 b5;
uniform float3 surface;
uniform float3 c0;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float3 c4;

float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 s) {
  if (uP3 < 0.5) return half3(s);
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225*l.r+0.1774*l.g, 0.0332*l.r+0.9669*l.g, 0.0171*l.r+0.0724*l.g+0.9108*l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float hash(float2 p) { return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }
float noise(float2 p) {
  float2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
  return mix(mix(hash(i),hash(i+float2(1,0)),f.x),mix(hash(i+float2(0,1)),hash(i+float2(1,1)),f.x),f.y);
}
float3 ramp(float r) {
  if(r<0.28) return mix(c0,c1,smoothstep(0.05,0.28,r));
  if(r<0.45) return mix(c1,c2,smoothstep(0.28,0.45,r));
  if(r<0.62) return mix(c2,c3,smoothstep(0.45,0.62,r));
  if(r<0.82) return mix(c3,c4,smoothstep(0.62,0.82,r));
  return mix(c4,surface,smoothstep(0.82,1.0,r));
}
// the vein: x = distance to it, y = how far along (0 at the knob, 1 at the pool), from a 16-piece polyline
float2 vq(float u) {
  float m = 1.0 - u;
  return m*m*float2(306.0,680.0) + 2.0*m*u*float2(322.0,548.0) + u*u*float2(201.0,400.0);
}
float2 veinAt(float2 xy) {
  float best = 1e6;
  float bu = 0.0;
  float2 a = vq(0.0);
  for (int i = 1; i <= 16; i++) {
    float u1 = float(i) / 16.0;
    float2 b = vq(u1);
    float2 ba = b - a;
    float2 pa = xy - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    float d = length(pa - ba * h);
    if (d < best) { best = d; bu = (float(i - 1) + h) / 16.0; }
    a = b;
  }
  return float2(best, bu);
}
float bead(float2 xy, float3 b) {
  float2 d = xy - b.xy;
  return b.z * exp(-dot(d, d) / 162.0);
}
half4 main(float2 xy0) {
  float2 xy = xy0;
  // a finger on the pool dents the field: content is pushed away from it (σ 54 pt, up to 16 pt)
  float2 fd = xy0 - dent.xy;
  float fw = exp(-dot(fd, fd) / 5832.0);
  xy = xy0 - fd / 54.0 * 16.0 * fw * dent.z;

  float2 p = (xy - center) / float2(170.0, 236.0) / max(radius, 0.0001);
  // the core is larger than the replica's ramp (136 x 184 pt instead of 108 x 160), so the 1.4x numeral sits on the dark
  float r0 = length(p);
  p *= mix(float2(0.794, 0.87), float2(1.0), smoothstep(0.25, 0.75, r0));
  float r = length(p), a = atan(p.y, p.x);
  // Rotate the inner domain more than the outer domain; modulated radius makes the spiral visible.
  float turn = shear * 3.2 * exp(-r * 1.25);
  float2 q = float2(cos(a - turn), sin(a - turn)) * r;
  float n = (noise(q * 3.4 + float2(8.1, 3.2)) - 0.5) * 0.075 + (noise(q * 7.1) - 0.5) * 0.035;
  float ripple = 0.035 * sin(a * 3.0 + 0.8) + 0.025 * cos(a * 5.0 - 1.1);
  float arms = 0.19 * abs(shear) * sin(2.0 * (a - turn) + r * 3.0) * smoothstep(0.12, 0.4, r);
  float field = r * (1.0 + n + ripple + arms);
  float3 col = mix(surface, ramp(field), amount);
  float wave = exp(-pow((field - ring) / 0.065, 2.0)) * ringAlpha * 0.08 * amount;
  col = mix(col, c3, wave);

  // where the pool was
  float2 sp = (xy0 - float2(201.0, 400.0)) / float2(75.0, 104.0);
  col = mix(col, float3(0.722, 0.561, 0.690), stain * exp(-dot(sp, sp) * 1.5));

  // the vein
  if (xy0.x > 160.0 && xy0.x < 352.0 && xy0.y > 372.0 && xy0.y < 708.0) {
    float2 vd = veinAt(xy0);
    float shape = 1.0 - smoothstep(9.0, 22.0, vd.x);
    float fadeEnd = 1.0 - smoothstep(0.84, 1.0, vd.y);
    col = mix(col, mix(c2, c3, smoothstep(9.0, 22.0, vd.x)), vein * shape * fadeEnd);
    // the pour: pigment between its head and its tail, 16 pt wide
    float within = smoothstep(plug.x - 0.03, plug.x + 0.01, vd.y) * (1.0 - smoothstep(plug.y - 0.01, plug.y + 0.03, vd.y));
    float wide = 1.0 - smoothstep(7.0, 12.0, vd.x);
    col = mix(col, mix(c1, c2, smoothstep(0.0, 9.0, vd.x)), plugAlpha * wide * within);
  }
  // beads of light, one per detent
  float3 bc = float3(0.949, 0.651, 0.800);
  float bsum = clamp(bead(xy0, b0) + bead(xy0, b1) + bead(xy0, b2) + bead(xy0, b3) + bead(xy0, b4) + bead(xy0, b5), 0.0, 1.0);
  col = mix(col, bc, bsum * 0.92);

  // the knob holds the same pigment after the pool drains, with no second moving emitter
  // at rest a plum shadow just past the cap; holding the light it widens to Ø 96 and turns the pool's magenta
  float hd = length(xy0 - float2(306.0, 680.0));
  float hr = mix(41.0, 50.0, halo);
  float ha = (1.0 - smoothstep(hr * 0.5, hr, hd)) * haloAlpha;
  col = mix(col, mix(float3(0.353, 0.114, 0.271), mix(c2, c3, 0.35), halo), ha);

  // the ring a release sends from the finger
  float fr = abs(length(xy0 - fring.xy) - fring.z);
  col = mix(col, float3(1.0), 0.14 * exp(-pow(fr / 7.0, 2.0)) * fring.w);

  float g = grain.eval(xy0).r - 0.5;
  float3 overlay = mix(2.0 * col * (g + 0.5), 1.0 - 2.0 * (1.0 - col) * (0.5 - g), step(float3(0.5), col));
  col = mix(col, overlay, 0.05);
  col = clamp(col + g * (2.0 / 255.0) * (1.0 - smoothstep(0.2, 0.45, field)) * amount, 0.0, 1.0);
  return half4(toOut(col), 1.0);
}
`;

export const GLOW_FX = Skia.RuntimeEffect.Make(GLOW_SOURCE);
if (!GLOW_FX) throw new Error('Glow Dial Reminder: invalid glow shader');
