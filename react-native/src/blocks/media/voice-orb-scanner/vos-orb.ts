// Voice Orb Scanner — the liquid orb and its droplets, one SkSL shader.
//
// The shape is a metaball field: the orb (its radius wobbling with the voice) plus three droplets, each
// contributing r²/d²; the surface is the field's level 1, so droplets leaving the orb pull a neck of goo
// that thins and snaps. Inside: a swirl of fbm flame (deep violet, magenta, an orange-white core low in
// the orb) seen through glass: a pseudo height 1 - 1/f gives a normal, which lights a lilac fresnel rim
// and a soft specular from the upper left. Outside: a faint bloom that follows the field.
import { Skia } from '@shopify/react-native-skia';

const SKSL = `
uniform float t;     // phase, radians: one turn per loop so the swirl repeats
uniform float4 orb;   // cx, cy, r, wobble 0..1
uniform float4 d0;    // droplet: cx, cy, r, unused
uniform float4 d1;
uniform float4 d2;
uniform float glow;   // extra brightness while listening

float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float vnoise(float2 p) {
  float2 i = floor(p); float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
float fbm(float2 p) {
  float s = 0.0; float a = 0.5;
  for (int i = 0; i < 4; i++) { s += a * vnoise(p); p = p * 2.03 + float2(1.7, 9.2); a *= 0.5; }
  return s;
}
float ball(float2 p, float4 b) {
  float2 q = p - b.xy;
  return b.z * b.z / max(dot(q, q), 1.0);
}
float field(float2 p) {
  float2 q = p - orb.xy;
  float a = atan(q.y, q.x);
  float w = orb.w * (0.05 * sin(5.0 * a + t * 10.0) + 0.035 * sin(9.0 * a - t * 15.0) + 0.025 * sin(3.0 * a + t * 6.0));
  float r = orb.z * (1.0 + w);
  float f = r * r / max(dot(q, q), 1.0);
  return f + ball(p, d0) + ball(p, d1) + ball(p, d2);
}

half4 main(float2 p) {
  float f = field(p);
  float aa = 1.6 / max(orb.z, 1.0) * 6.0;
  float inside = smoothstep(1.0 - aa * 0.5, 1.0 + aa * 0.5, f);
  // bloom outside the surface
  float3 halo = float3(0.66, 0.36, 0.9);
  float ha = smoothstep(0.25, 1.0, f) * (0.3 + 0.2 * glow);
  if (inside <= 0.0) return half4(half3(halo * ha), ha);

  // pseudo height and its normal
  float e = 1.5;
  float hx = (1.0 - 1.0 / max(field(p + float2(e, 0.0)), 1e-3)) - (1.0 - 1.0 / max(field(p - float2(e, 0.0)), 1e-3));
  float hy = (1.0 - 1.0 / max(field(p + float2(0.0, e)), 1e-3)) - (1.0 - 1.0 / max(field(p - float2(0.0, e)), 1e-3));
  float3 n = normalize(float3(-hx * orb.z * 0.18, -hy * orb.z * 0.18, 1.0));

  // the flame, swirling inside
  float2 uv = (p - orb.xy) / orb.z;
  float sw = fbm(uv * 1.15 + 0.7 * float2(cos(t), sin(t)));
  float fl = fbm(uv * 1.9 + float2(sw * 1.4, sw * 1.1) + 0.55 * float2(sin(2.0 * t), cos(t)));
  // violet at the top warming to magenta, flame tongues licking up from an orange-white core
  float g = clamp(uv.y * 0.55 + 0.5 + (fl - 0.5) * 0.7, 0.0, 1.0);
  float3 col = mix(float3(0.24, 0.08, 0.42), float3(0.92, 0.27, 0.66), smoothstep(0.1, 0.75, g));
  float core = exp(-dot(uv - float2(0.0, 0.28), uv - float2(0.0, 0.28)) * 2.4);
  col = mix(col, float3(1.0, 0.56, 0.24), smoothstep(0.25, 0.85, core * (0.75 + 0.5 * fl)));
  col += float3(1.0, 0.93, 0.78) * pow(core, 3.0) * (0.5 + 0.4 * glow);
  // a darker band just inside the glass, then the rim
  float edge = smoothstep(0.55, 0.95, 1.0 - n.z);
  col *= 1.0 - 0.35 * edge;
  // glass: a lilac fresnel rim and a soft highlight from the upper left
  float fr = pow(1.0 - n.z, 1.6);
  col = mix(col, float3(1.0, 0.8, 0.95), fr * 0.85);
  float3 L = normalize(float3(-0.5, -0.6, 0.65));
  float sp = pow(max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0), 28.0);
  col += sp * 0.8;
  col *= 1.0 + 0.18 * glow;
  col = min(col, 1.0);
  float a = inside + ha * (1.0 - inside);
  return half4(half3(col * inside + halo * ha * (1.0 - inside)), a);
}`;

export const orbFx = Skia.RuntimeEffect.Make(SKSL);
if (!orbFx && __DEV__) console.warn('Voice Orb Scanner: the orb shader did not compile');
