// Tiny World Picker — the four worlds, ray-marched in one SkSL shader.
//
// Every world is a small signed-distance scene of toy-clay solids: a cloud planet (a sphere wrapped
// in a tilted ring of smooth-unioned puffs), a racing planet (a sphere with a banked asphalt loop and
// three trees), a burger (stacked rounded discs, a wavy lettuce frill, sesame seeds on the dome) and
// an old arena (a ring wall with two tiers of arches cut by angular repetition, a ruined side and a
// sand floor on a rock island). Picking a world blends two of them: the distance fields are mixed by
// the morph amount, so a sphere can open a hole and become a ring without any mesh. The object turns
// about its vertical axis (yaw) under a camera pitched 20° down; a squash term gives it jelly weight.
// The hit is shaded like glossy clay: wrapped key light, sky fill, a cyan rim from behind, ambient
// occlusion from the field, one specular lobe. Outside the object the shader is transparent, so the
// page's stars show through. Output is premultiplied.
import { Skia } from "@shopify/react-native-skia";

export const WORLD_SKSL = `
uniform float2 c;      // centre of the world, px
uniform float S;       // px per scene unit
uniform float yaw;     // turn about the vertical axis, radians
uniform float wa;      // world shown at m = 0 (0 cloud, 1 kart, 2 burger, 3 arena)
uniform float wb;      // world shown at m = 1
uniform float m;       // morph amount 0..1
uniform float sq;      // squash: + flattens, - stretches
uniform float glow;    // 0..1 extra rim light while the world is held
uniform float px;      // scene units per device pixel (antialiasing)
uniform float tm;      // loop time, seconds (the kart drives with it)

const float PI = 3.14159265;
const float3 RB_D = float3(0.5774, 0.5774, 0.5774);   // the rainbow's base direction (normalised)
const float3 RB_T = float3(0.7071, -0.7071, 0.0);     // along the arc
const float3 RB_U = float3(0.4082, 0.4082, -0.8165);  // across the ribbon

float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float sdBox2(float2 p, float2 b) {
  float2 d = abs(p) - b;
  return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}
// vertical rounded cylinder: radius r, half height h, edge radius e
float sdDisc(float3 p, float r, float h, float e) {
  float2 d = float2(length(p.xz) - r + e, abs(p.y) - h + e);
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - e;
}
float sdBox3(float3 p, float3 b) {
  float3 d = abs(p) - b;
  return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0);
}
float sdEll(float3 p, float3 r) {
  float k0 = length(p / r);
  float k1 = length(p / (r * r));
  return k0 * (k0 - 1.0) / max(k1, 0.0001);
}
float3 rotX(float3 p, float a) { float s = sin(a); float co = cos(a); return float3(p.x, co * p.y - s * p.z, s * p.y + co * p.z); }
float3 rotZ(float3 p, float a) { float s = sin(a); float co = cos(a); return float3(co * p.x - s * p.y, s * p.x + co * p.y, p.z); }

// ---- world 0: cloud nest --------------------------------------------------------------------
float2 cloudWorld(float3 p) {
  float planet = length(p) - 0.68;
  float cl = 9.0;
  if (length(p) < 1.25) {
    // five toy clouds around the planet, each a big puff with two side puffs and a top puff
    for (int k = 0; k < 7; k++) {
      float fk = float(k);
      float a = k < 5 ? fk * 1.2566 + 0.4 : (k == 5 ? 2.2 : 5.0);
      float y = k == 0 ? 0.42 : (k == 1 ? -0.12 : (k == 2 ? 0.18 : (k == 3 ? -0.38 : (k == 4 ? 0.05 : (k == 5 ? 1.3 : 1.1)))));
      float3 d = normalize(float3(cos(a), y, sin(a)));
      float3 t = normalize(cross(d, float3(0.0, 1.0, 0.0)));
      float3 u = cross(t, d);
      float sz = k == 1 ? 1.25 : (k == 3 ? 0.9 : (k > 4 ? 0.95 : 1.1));
      float3 c0 = d * 0.74;
      float c = length(p - c0) - 0.17 * sz;
      c = smin(c, length(p - c0 - t * 0.17 * sz + u * 0.02) - 0.12 * sz, 0.07);
      c = smin(c, length(p - c0 + t * 0.18 * sz + u * 0.01) - 0.13 * sz, 0.07);
      c = smin(c, length(p - c0 - t * 0.06 * sz - u * 0.1 * sz + d * 0.03) - 0.13 * sz, 0.07);
      cl = min(cl, c);
    }
  }
  // a rainbow ribbon arching out of the planet between two clouds
  float3 rq = p - RB_D * 0.47;
  float2 rb = float2(length(float2(dot(rq, RB_T), dot(rq, RB_D))) - 0.37, dot(rq, RB_U));
  float bow = max(sdBox2(rb, float2(0.075, 0.022)) - 0.012, -dot(rq, RB_D));
  if (bow < cl && bow < planet) return float2(bow, 1.6);
  if (cl < planet) return float2(cl, 1.0);
  return float2(planet, 0.0);
}

// ---- world 1: kart loop ---------------------------------------------------------------------
float2 kartWorld(float3 p) {
  float planet = length(p) - 0.7;
  float3 q = rotX(rotZ(p, 0.42), 0.28);
  float2 band = float2(length(q.xz) - 0.72, q.y);
  float track = sdBox2(band, float2(0.06, 0.16)) - 0.03;
  float2 res = float2(planet, 2.0);
  if (track < res.x) res = float2(track, 3.0);
  // five toy trees: a trunk and a round crown each, standing on the surface
  float tr = 9.0;
  float tk = 9.0;
  for (int i = 0; i < 5; i++) {
    float3 n = i == 0 ? float3(-0.56, 0.74, 0.37) : (i == 1 ? float3(0.63, 0.66, -0.41) :
      (i == 2 ? float3(0.12, 0.42, 0.9) : (i == 3 ? float3(-0.2, 0.9, -0.38) : float3(-0.82, 0.2, -0.5))));
    n = normalize(n);
    float sz = i == 0 ? 1.0 : (i == 3 ? 0.7 : 0.82);
    // a round crown of two puffs on a short trunk
    float3 side = normalize(cross(n, float3(0.0, 0.0, 1.0)));
    float cr = length(p - n * (0.84 + 0.1 * sz)) - 0.14 * sz;
    cr = smin(cr, length(p - n * (0.8 + 0.06 * sz) - side * 0.08 * sz) - 0.1 * sz, 0.05);
    tr = min(tr, cr);
    float3 pb = p - n * 0.64;
    float hb = clamp(dot(pb, n), 0.0, 0.12 * sz + 0.04);
    tk = min(tk, length(pb - n * hb) - 0.035);
  }
  if (tk < res.x) res = float2(tk, 4.5);
  if (tr < res.x) res = float2(tr, 4.0);
  // the kart: two laps of the loop every 9 s, riding the outside of the band
  float th = tm * 1.3962634;
  float3 rdir = float3(cos(th), 0.0, sin(th));
  float3 lq = q - rdir * 0.875;
  if (length(lq) < 0.26) {
    float3 tg = float3(-sin(th), 0.0, cos(th));
    float3 l = float3(dot(lq, tg), lq.y, dot(lq, rdir));
    float body = sdBox3(l, float3(0.1, 0.055, 0.022)) - 0.022;
    float2 kr = float2(body, 14.0);
    float cab = sdBox3(l - float3(-0.025, 0.0, 0.045), float3(0.04, 0.04, 0.02)) - 0.018;
    if (cab < kr.x) kr = float2(cab, 14.6);
    float wh = 9.0;
    for (int j = 0; j < 4; j++) {
      float wx = j < 2 ? 0.068 : -0.068;
      float wy = (j == 0 || j == 2) ? 0.07 : -0.07;
      float2 c2 = float2(length(float2(l.x - wx, l.z + 0.035)) - 0.032, abs(l.y - wy) - 0.017);
      wh = min(wh, min(max(c2.x, c2.y), 0.0) + length(max(c2, 0.0)) - 0.004);
    }
    if (wh < kr.x) kr = float2(wh, 15.0);
    if (kr.x < res.x) res = kr;
  }
  return res;
}

// ---- world 2: bun summit (a burger) ---------------------------------------------------------
float2 burgerWorld(float3 p) {
  p.y += 0.06;
  float2 res = float2(sdDisc(p - float3(0.0, -0.56, 0.0), 0.74, 0.14, 0.12), 5.0); // bottom bun
  float d = sdDisc(p - float3(0.0, -0.34, 0.0), 0.8, 0.1, 0.09);                    // patty
  d += 0.012 * sin(p.x * 31.0) * sin(p.z * 27.0);
  if (d < res.x) res = float2(d, 6.0);
  // cheese: a square slice turned 45°, its corners hanging over the patty
  float3 cq = p - float3(0.0, -0.22, 0.0);
  float2 cr = float2(cq.x + cq.z, cq.z - cq.x) * 0.7071;
  float sag = 0.05 * smoothstep(0.55, 0.85, max(abs(cr.x), abs(cr.y)));
  cq.y += sag;
  d = max(sdBox2(cr, float2(0.66, 0.66)) - 0.02, abs(cq.y) - 0.025);
  if (d < res.x) res = float2(d, 7.0);
  // lettuce: a thin disc whose rim waves up and down
  float3 lq = p - float3(0.0, -0.13, 0.0);
  float ang = atan(lq.z, lq.x);
  lq.y += 0.035 * sin(ang * 13.0) * smoothstep(0.55, 0.88, length(lq.xz));
  d = sdDisc(lq, 0.88, 0.035, 0.03);
  if (d < res.x) res = float2(d, 8.0);
  d = sdDisc(p - float3(0.0, -0.05, 0.0), 0.72, 0.045, 0.04);                       // tomato
  if (d < res.x) res = float2(d, 9.0);
  // top bun: a dome
  float3 tq = p - float3(0.0, 0.0, 0.0);
  d = max(sdEll(tq, float3(0.8, 0.66, 0.8)), -tq.y + 0.01);
  d = smin(d, sdDisc(tq - float3(0.0, 0.04, 0.0), 0.79, 0.04, 0.035), 0.05);
  if (d < res.x) res = float2(d, 5.5);
  if (p.y > 0.2 && d < 0.12) {
    float s = 9.0;
    for (int i = 0; i < 9; i++) {
      float fi = float(i);
      float a = fi * 2.399 + 0.3;
      float el = 0.35 + 0.42 * fract(fi * 0.618);
      float3 sp = p - float3(cos(a) * cos(el) * 0.8, sin(el) * 0.66, sin(a) * cos(el) * 0.8);
      s = min(s, sdEll(sp, float3(0.035, 0.03, 0.035)));
    }
    if (s < res.x) res = float2(s, 10.0);
  }
  return res;
}

// ---- world 3: old arena ---------------------------------------------------------------------
float2 arenaWorld(float3 p) {
  p /= 0.86; // the arena is drawn 14% smaller than modelled
  float2 res = float2(sdEll(p - float3(0.0, -0.4, 0.0), float3(0.94, 0.26, 0.94)), 13.0); // island
  float r = length(p.xz);
  float ang = atan(p.z, p.x);
  float wall = sdBox2(float2(r - 0.7, p.y), float2(0.13, 0.34)) - 0.02;
  // one side of the wall has fallen: a ragged top that drops toward the back right
  float ruin = 0.36 - 0.2 * smoothstep(0.35, 0.95, cos(ang - 2.4)) + 0.025 * sin(ang * 23.0) * smoothstep(0.3, 0.6, cos(ang - 2.4));
  wall = max(wall, (p.y - ruin) * 0.8);
  // arches on the outer face, two tiers, 18 around
  float sec = 2.0 * PI / 18.0;
  float la = mod(ang + sec * 0.5, sec) - sec * 0.5;
  float tx = r * sin(la);
  float arch = 9.0;
  for (int k = 0; k < 2; k++) {
    float y0 = k == 0 ? -0.16 : 0.12;
    float2 a2 = float2(tx, p.y - y0);
    float hole = min(sdBox2(a2 + float2(0.0, 0.03), float2(0.055, 0.06)), length(a2 - float2(0.0, 0.03)) - 0.055);
    arch = min(arch, hole);
  }
  arch = max(arch, abs(r - 0.79) - 0.07);
  wall = max(wall, -arch);
  if (wall < res.x) res = float2(wall, 11.0);
  float fl = sdDisc(p - float3(0.0, -0.24, 0.0), 0.6, 0.03, 0.02);
  if (fl < res.x) res = float2(fl, 12.0);
  return float2(res.x * 0.86, res.y);
}

float2 world(float3 p, float w) {
  if (w < 0.5) return cloudWorld(p);
  if (w < 1.5) return kartWorld(p);
  if (w < 2.5) return burgerWorld(p);
  return arenaWorld(p);
}

float3 objSpace(float3 p) {
  float co = cos(yaw); float s = sin(yaw);
  p = float3(co * p.x + s * p.z, p.y, -s * p.x + co * p.z);
  p.y /= (1.0 - sq);
  p.xz /= (1.0 + sq * 0.5);
  return p;
}

float2 scene(float3 p) {
  float3 q = objSpace(p);
  float k = min(1.0 - sq, 1.0 + sq * 0.5);
  float2 a = world(q, wa);
  if (m < 0.002) return float2(a.x * k, a.y);
  float2 b = world(q, wb);
  if (m > 0.998) return float2(b.x * k, b.y);
  return float2(mix(a.x, b.x, m) * k, m < 0.5 ? a.y : b.y);
}

float3 colourOf(float id, float3 q) {
  if (id < 0.5) {
    float band = 0.5 + 0.5 * sin(q.y * 9.0 + sin(q.x * 4.0) * 1.2);
    float lat = smoothstep(-0.7, 0.7, q.y);
    return mix(mix(float3(0.1, 0.55, 0.78), float3(0.2, 0.78, 0.86), lat), float3(0.42, 0.9, 0.9), band * 0.35);
  }
  if (id < 1.5) return float3(1.0, 1.0, 1.0);
  if (id < 1.8) {
    float3 rq = q - RB_D * 0.47;
    float r = length(float2(dot(rq, RB_T), dot(rq, RB_D))) - 0.37;
    float k = clamp((r + 0.075) / 0.15, 0.0, 0.999) * 5.0;
    if (k < 1.0) return float3(0.62, 0.42, 0.95);
    if (k < 2.0) return float3(0.3, 0.6, 1.0);
    if (k < 3.0) return float3(0.36, 0.84, 0.42);
    if (k < 4.0) return float3(1.0, 0.82, 0.25);
    return float3(1.0, 0.38, 0.36);
  }
  if (id < 2.5) {
    float g = 0.5 + 0.5 * sin(q.x * 7.0 + q.z * 5.0);
    return mix(float3(0.45, 0.76, 0.2), float3(0.58, 0.84, 0.28), g);
  }
  if (id < 3.5) {
    float3 t = rotX(rotZ(q, 0.42), 0.28);
    float a = atan(t.z, t.x);
    float3 col = float3(0.2, 0.21, 0.25);
    float dash = step(0.5, fract(a * 4.0 / PI)) * (1.0 - smoothstep(0.012, 0.022, abs(t.y)));
    col = mix(col, float3(0.98, 0.95, 0.85), dash);
    float curb = smoothstep(0.125, 0.135, abs(t.y));
    float stripe = step(0.5, fract(a * 18.0 / PI));
    col = mix(col, mix(float3(0.93, 0.22, 0.2), float3(0.98, 0.97, 0.95), stripe), curb);
    return col;
  }
  if (id < 4.25) return float3(0.2, 0.6, 0.3);
  if (id < 4.75) return float3(0.55, 0.34, 0.2);
  if (id < 5.25) return float3(0.89, 0.58, 0.27);
  if (id < 5.75) {
    float top = smoothstep(0.1, 0.6, q.y + 0.06);
    return mix(float3(0.91, 0.56, 0.24), float3(0.98, 0.72, 0.35), top);
  }
  if (id < 6.5) return float3(0.42, 0.22, 0.13);
  if (id < 7.5) return float3(1.0, 0.76, 0.18);
  if (id < 8.5) return float3(0.47, 0.78, 0.25);
  if (id < 9.5) return float3(0.91, 0.26, 0.21);
  if (id < 10.5) return float3(1.0, 0.95, 0.82);
  if (id > 13.5) {
    if (id < 14.3) return float3(0.93, 0.24, 0.22);
    if (id < 14.8) return float3(0.95, 0.96, 1.0);
    return float3(0.12, 0.12, 0.14);
  }
  if (id > 10.5) q /= 0.86;
  if (id < 11.5) {
    float course = step(0.5, fract(q.y * 18.0));
    return mix(float3(0.86, 0.68, 0.48), float3(0.8, 0.61, 0.42), course * 0.6);
  }
  if (id < 12.5) return float3(0.95, 0.84, 0.62);
  float grass = smoothstep(-0.24, -0.17, q.y);
  return mix(float3(0.62, 0.45, 0.33), float3(0.5, 0.74, 0.3), grass);
}

float3 normalAt(float3 p) {
  float2 e = float2(0.0025, -0.0025);
  return normalize(e.xyy * scene(p + e.xyy).x + e.yyx * scene(p + e.yyx).x +
                   e.yxy * scene(p + e.yxy).x + e.xxx * scene(p + e.xxx).x);
}

half4 main(float2 xy) {
  float2 uv = (xy - c) / S;
  uv.y = -uv.y;
  // camera 5 units out, pitched 20 degrees down
  float3 ro = float3(0.0, 1.71, 4.7);
  float3 fw = normalize(-ro);
  float3 rt = normalize(cross(fw, float3(0.0, 1.0, 0.0)));
  float3 up = cross(rt, fw);
  float3 rd = normalize(fw * 4.0 + rt * uv.x + up * uv.y);
  float bb = dot(ro, rd);
  float cc = dot(ro, ro) - 1.45 * 1.45;
  if (bb * bb - cc < 0.0) return half4(0.0);
  float tt = max(0.0, -bb - sqrt(max(bb * bb - cc, 0.0)));
  float tmax = -bb + sqrt(max(bb * bb - cc, 0.0));
  float dmin = 9.0;
  float id = -1.0;
  float3 p = ro;
  for (int i = 0; i < 72; i++) {
    p = ro + rd * tt;
    float2 h = scene(p);
    dmin = min(dmin, h.x);
    if (h.x < 0.0012) { id = h.y; break; }
    tt += h.x * 0.85;
    if (tt > tmax) break;
  }
  if (id < 0.0) {
    float a = 1.0 - smoothstep(0.0, px * 1.4, dmin);
    if (a <= 0.0) return half4(0.0);
    return half4(half3(0.55, 0.85, 0.95) * a * 0.35, a * 0.35);
  }
  float3 n = normalAt(p);
  float3 q = objSpace(p);
  float3 base;
  if (m < 0.002) base = colourOf(world(q, wa).y, q);
  else if (m > 0.998) base = colourOf(world(q, wb).y, q);
  else base = mix(colourOf(world(q, wa).y, q), colourOf(world(q, wb).y, q), smoothstep(0.25, 0.75, m));
  float3 L = normalize(float3(-0.55, 0.8, 0.6));
  float dif = clamp(dot(n, L) * 0.65 + 0.35, 0.0, 1.0);
  float sky = 0.5 + 0.5 * n.y;
  float rim = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 3.0);
  float ao = clamp(scene(p + n * 0.1).x / 0.1, 0.0, 1.0);
  ao = 0.5 + 0.5 * ao;
  float soft = step(0.5, id) * (1.0 - step(1.5, id)); // clouds scatter light
  dif = mix(dif, clamp(dot(n, L) * 0.38 + 0.7, 0.0, 1.0), soft);
  ao = mix(ao, 0.8 + 0.2 * ao, soft);
  float3 col = base * (0.28 + 0.85 * dif) * ao;
  col += base * float3(0.25, 0.42, 0.5) * sky * 0.25;
  float back = clamp(dot(n, normalize(float3(0.6, 0.2, -0.8))), 0.0, 1.0);
  col += float3(0.45, 0.9, 1.0) * (rim * (0.45 + 0.6 * glow) + back * 0.12);
  col += float3(1.0) * pow(max(dot(reflect(-L, n), -rd), 0.0), 26.0) * 0.42 * ao;
  col = float3(1.0) - exp(-col * 1.65);
  return half4(half3(col), 1.0);
}`;

export const worldFx = Skia.RuntimeEffect.Make(WORLD_SKSL);
if (!worldFx && __DEV__)
  console.warn("Tiny World Picker: the world shader did not compile");
