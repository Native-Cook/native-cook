// Storm Cloud Weather — the clay weather icon, ray-marched in one SkSL shader.
//
// The scene is signed distances: seven puffs smooth-unioned into a cloud, a crescent moon (a sphere
// minus a sphere, squashed into a thick disc) or a sun (a sphere), a zig-zag bolt of three tapering
// capsules, and four falling drops (stretched spheres). Each pixel's ray is sphere-traced from a
// camera that turns about the vertical axis; the hit is shaded like soft clay (wrapped key light,
// sky fill, rim light, ambient occlusion from the distance field). The closest approach of a missed
// ray gives the silhouette its antialiasing. Every parameter of the weather is a uniform, so the
// states morph into each other.
import { Skia } from '@shopify/react-native-skia';

const SKSL = `
uniform float2 c;      // centre, px
uniform float S;       // px per scene unit
uniform float yaw;
uniform float t;       // seconds (drops fall with it)
uniform float3 cloud;  // cloud colour
uniform float moon;    // 0..1 size of the moon
uniform float sun;     // 0..1 size of the sun
uniform float bolt;    // 0..1 size of the bolt
uniform float rain;    // 0..1 size of the drops
uniform float flash;   // 0..1 the bolt's light
uniform float puff;    // 0 storm cloud (wide, low), 1 fair cloud (rounder)

float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float sph(float3 p, float3 o, float r) { return length(p - o) - r; }
float cap(float3 p, float3 a, float3 b, float ra, float rb) {
  float3 pa = p - a; float3 ba = b - a;
  float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h) - mix(ra, rb, h);
}

// returns distance; id: 0 cloud, 1 moon/sun, 2 bolt, 3 drop
float2 scene(float3 p) {
  float s = mix(1.0, 0.86, puff);
  p.y -= 0.05;
  float d = sph(p, float3(-0.62, -0.05, 0.0) * s, 0.48 * s);
  d = smin(d, sph(p, float3(-0.18, 0.28, 0.05) * s, 0.62 * s), 0.18);
  d = smin(d, sph(p, float3(0.42, 0.12, 0.0) * s, 0.55 * s), 0.18);
  d = smin(d, sph(p, float3(0.86, -0.12, 0.02) * s, 0.4 * s), 0.18);
  d = smin(d, sph(p, float3(0.1, -0.28, 0.25) * s, 0.5 * s), 0.2);
  d = smin(d, sph(p, float3(-0.35, -0.3, 0.22) * s, 0.42 * s), 0.2);
  d = smin(d, sph(p, float3(0.55, -0.32, 0.2) * s, 0.4 * s), 0.2);
  float2 res = float2(d, 0.0);
  // moon: a thick crescent up and to the right, behind the cloud
  if (moon > 0.01) {
    // a puffy crescent above the cloud: a 2D crescent extruded and rounded, opening to the right
    float3 q = p - float3(0.52, 0.95, -0.3);
    float r = 0.42 * moon;
    float c2 = max(length(q.xy) - r, -(length(q.xy - float2(0.2, 0.12) * moon) - r * 0.84));
    float th = 0.07 * moon;
    float2 w2 = float2(c2 + 0.06 * moon, abs(q.z) - th);
    float m = min(max(w2.x, w2.y), 0.0) + length(max(w2, 0.0)) - 0.07 * moon;
    if (m < res.x) res = float2(m, 1.0);
  }
  if (sun > 0.01) {
    float m = sph(p, float3(0.62, 0.72, -0.4), 0.42 * sun);
    if (m < res.x) res = float2(m, 1.5);
  }
  if (bolt > 0.01) {
    float b = bolt;
    float3 o = float3(0.12, -0.52, 0.36);
    float m = cap(p, o, o + float3(-0.24, -0.4, 0.0) * b, 0.085 * b, 0.07 * b);
    m = min(m, cap(p, o + float3(-0.26, -0.37, 0.0) * b, o + float3(0.06, -0.44, 0.0) * b, 0.065 * b, 0.065 * b));
    m = min(m, cap(p, o + float3(0.04, -0.43, 0.0) * b, o + float3(-0.16, -1.0, 0.0) * b, 0.065 * b, 0.012 * b));
    if (m < res.x) res = float2(m, 2.0);
  }
  if (rain > 0.01) {
    for (int i = 0; i < 4; i++) {
      float fi = float(i);
      float x = -0.62 + fi * 0.42 + (fi == 2.0 ? 0.08 : 0.0);
      float k = fract(t * 0.7777778 + fi * 0.37); // 7 falls per 9 s loop
      float y = -0.6 - k * 0.75;
      float sz = rain * 0.09 * smoothstep(0.0, 0.15, k) * (1.0 - smoothstep(0.75, 1.0, k));
      if (sz > 0.002) {
        float3 q = p - float3(x, y, 0.3 - fi * 0.05);
        q.y *= 0.62;
        float m = (length(q) - sz) * 0.7;
        if (m < res.x) res = float2(m, 3.0);
      }
    }
  }
  return res;
}

float3 normalAt(float3 p) {
  float e = 0.002;
  return normalize(float3(
    scene(p + float3(e, 0, 0)).x - scene(p - float3(e, 0, 0)).x,
    scene(p + float3(0, e, 0)).x - scene(p - float3(0, e, 0)).x,
    scene(p + float3(0, 0, e)).x - scene(p - float3(0, 0, e)).x));
}

half4 main(float2 xy) {
  float2 uv = (xy - c) / S;
  uv.y = -uv.y;
  // a camera 4.2 units away, turned about the vertical axis
  float cy = cos(yaw); float sy = sin(yaw);
  float3 ro = float3(4.2 * sy, 0.05, 4.2 * cy);
  float3 fw = normalize(-ro);
  float3 rt = normalize(cross(fw, float3(0, 1, 0)));
  float3 up = cross(rt, fw);
  float3 rd = normalize(fw * 3.6 + rt * uv.x + up * uv.y);
  // quick reject outside the bounding sphere
  float bb = dot(ro, rd);
  float cc = dot(ro, ro) - 2.3 * 2.3;
  if (bb * bb - cc < 0.0) return half4(0.0);
  float tt = max(0.0, -bb - sqrt(max(bb * bb - cc, 0.0)));
  float dmin = 9.0;
  float id = -1.0;
  float3 p = ro;
  for (int i = 0; i < 64; i++) {
    p = ro + rd * tt;
    float2 h = scene(p);
    dmin = min(dmin, h.x);
    if (h.x < 0.0015) { id = h.y; break; }
    tt += h.x;
    if (tt > 8.0) break;
  }
  float px = 1.0 / S;
  if (id < 0.0) {
    // antialiased edge from the closest approach
    float a = 1.0 - smoothstep(0.0, px * 1.5, dmin);
    if (a <= 0.0) return half4(0.0);
    return half4(half3(0.12, 0.14, 0.2) * a, a * 0.6);
  }
  float3 n = normalAt(p);
  float3 L = normalize(float3(-0.55, 0.75, 0.55));
  float dif = clamp(dot(n, L) * 0.6 + 0.4, 0.0, 1.0);
  float sky = 0.5 + 0.5 * n.y;
  float rim = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 2.5);
  float ao = clamp(scene(p + n * 0.12).x / 0.12, 0.0, 1.0);
  ao = 0.55 + 0.45 * ao;
  float3 base;
  float3 col;
  if (id < 0.5) {
    base = cloud;
    col = base * (0.35 + 0.75 * dif) * ao + float3(0.25, 0.3, 0.45) * sky * 0.18 + rim * float3(0.5, 0.55, 0.75) * 0.35;
    // the bolt's light on the underside
    col += float3(1.0, 0.7, 0.25) * flash * clamp(-n.y, 0.0, 1.0) * 0.6;
    // a glossy clay highlight
    col += float3(1.0) * pow(max(dot(reflect(-L, n), -rd), 0.0), 18.0) * 0.5;
  } else if (id < 1.2) {
    base = float3(0.42, 0.36, 0.98);
    col = base * (0.45 + 0.7 * dif) * ao + rim * float3(0.75, 0.7, 1.0) * 0.5;
    col += float3(1.0) * pow(max(dot(reflect(-L, n), -rd), 0.0), 30.0) * 0.45;
  } else if (id < 1.8) {
    base = float3(1.0, 0.78, 0.25);
    col = base * (0.7 + 0.45 * dif) + rim * float3(1.0, 0.6, 0.2) * 0.4;
  } else if (id < 2.5) {
    base = mix(float3(1.0, 0.72, 0.12), float3(1.0, 0.86, 0.3), clamp(p.y + 1.2, 0.0, 1.0));
    col = base * (0.6 + 0.5 * dif) + float3(1.0, 0.9, 0.5) * flash * 0.7 + rim * 0.2;
  } else {
    base = float3(0.25, 0.62, 1.0);
    col = base * (0.5 + 0.6 * dif) + float3(0.8, 0.9, 1.0) * pow(max(dot(reflect(-L, n), -rd), 0.0), 20.0) * 0.8;
  }
  col = float3(1.0) - exp(-col * 1.25);
  return half4(half3(col), 1.0);
}`;

export const sceneFx = Skia.RuntimeEffect.Make(SKSL);
if (!sceneFx && __DEV__) console.warn('Storm Cloud Weather: the scene shader did not compile');

export type Weather = {
  cloud: [number, number, number];
  moon: number;
  sun: number;
  bolt: number;
  rain: number;
  puff: number;
};

export const STATES: Weather[] = [
  { cloud: [0.21, 0.22, 0.26], moon: 1, sun: 0, bolt: 1, rain: 1, puff: 0 }, // thunderstorm, night
  { cloud: [0.86, 0.89, 0.95], moon: 0, sun: 0, bolt: 0, rain: 1, puff: 0.6 }, // showers
  { cloud: [0.95, 0.96, 0.99], moon: 0, sun: 1, bolt: 0, rain: 0, puff: 1 }, // partly sunny
];

export const COPY = [
  { label: 'Now', temp: 26, wind: 14, humidity: 82, sky: 'Thunderstorm' },
  { label: '2:00', temp: 28, wind: 9, humidity: 74, sky: 'Showers' },
  { label: '4:00', temp: 34, wind: 6, humidity: 52, sky: 'Partly sunny' },
];
