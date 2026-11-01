package com.ngocdevv.nativecook.blocks.gestures.evolveholdmorph

internal object EvolveHoldMorphShaders {
 val PET = """
uniform float2 c;
uniform float S;
uniform float yaw;
uniform float fa;     // form at m = 0 (0 egg, 1 chick, 2 bird)
uniform float fb;     // form at m = 1
uniform float m;
uniform float sq;     // squash: + flattens
uniform float charge; // 0..1 inner glow
uniform float blink;  // 0..1 eyelids
uniform float px;

float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float sdEll(float3 p, float3 r) {
  float k0 = length(p / r);
  float k1 = length(p / (r * r));
  return k0 * (k0 - 1.0) / max(k1, 0.0001);
}
float sdCone(float3 p, float3 a, float3 b, float ra, float rb) {
  float3 pa = p - a; float3 ba = b - a;
  float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h) - mix(ra, rb, h);
}

// ids: 0 shell/body, 1 spots (egg), 2 beak, 3 eye, 4 wing, 5 crest/tail, 6 belly
float2 egg(float3 p) {
  float d = sdEll(p - float3(0.0, 0.02, 0.0), float3(0.62, 0.8, 0.62));
  return float2(d, 0.0);
}
float2 chick(float3 p) {
  float b = length(p - float3(0.0, -0.2, 0.0)) - 0.6;
  float h = length(p - float3(0.0, 0.42, 0.08)) - 0.38;
  float d = smin(b, h, 0.18);
  float2 r = float2(d, 0.0);
  float bk = sdCone(p, float3(0.0, 0.4, 0.42), float3(0.0, 0.36, 0.62), 0.1, 0.01);
  if (bk < r.x) r = float2(bk, 2.0);
  float e = min(length(p - float3(0.15, 0.52, 0.36)) - 0.06, length(p - float3(-0.15, 0.52, 0.36)) - 0.06);
  if (e < r.x) r = float2(e, 3.0);
  float w = min(sdEll(p - float3(0.56, -0.18, 0.02), float3(0.1, 0.24, 0.2)), sdEll(p - float3(-0.56, -0.18, 0.02), float3(0.1, 0.24, 0.2)));
  if (w < r.x) r = float2(w, 4.0);
  float t = sdCone(p, float3(0.0, 0.76, 0.0), float3(0.06, 0.92, -0.06), 0.06, 0.02);
  if (t < r.x) r = float2(t, 5.0);
  return r;
}
float2 bird(float3 p) {
  float b = sdEll(p - float3(0.0, -0.18, -0.04), float3(0.5, 0.58, 0.5));
  float h = length(p - float3(0.0, 0.46, 0.14)) - 0.34;
  float d = smin(b, h, 0.2);
  float2 r = float2(d, 0.0);
  float bel = sdEll(p - float3(0.0, -0.2, 0.2), float3(0.36, 0.42, 0.34));
  if (bel < r.x + 0.012) r = float2(min(bel, r.x), 6.0);
  float bk = sdCone(p, float3(0.0, 0.44, 0.44), float3(0.0, 0.38, 0.7), 0.09, 0.008);
  if (bk < r.x) r = float2(bk, 2.0);
  float e = min(length(p - float3(0.14, 0.54, 0.4)) - 0.055, length(p - float3(-0.14, 0.54, 0.4)) - 0.055);
  if (e < r.x) r = float2(e, 3.0);
  // wings: wide flattened ellipsoids raised from the shoulders
  float3 wl = p - float3(0.5, 0.0, -0.08);
  float3 wr = p - float3(-0.5, 0.0, -0.08);
  float w = min(sdEll(float3(wl.x * 0.9 - wl.y * 0.45, wl.y * 0.9 + wl.x * 0.45, wl.z), float3(0.36, 0.13, 0.28)),
                sdEll(float3(wr.x * 0.9 + wr.y * 0.45, wr.y * 0.9 - wr.x * 0.45, wr.z), float3(0.36, 0.13, 0.28)));
  if (w < r.x) r = float2(w, 4.0);
  // crest of three feathers, a fanned tail
  float cr = 9.0;
  for (int i = 0; i < 3; i++) {
    float fi = float(i) - 1.0;
    cr = min(cr, sdCone(p, float3(fi * 0.08, 0.72, 0.06), float3(fi * 0.16, 0.98, -0.06 - abs(fi) * 0.04), 0.07, 0.015));
  }
  for (int i = 0; i < 3; i++) {
    float fi = float(i) - 1.0;
    cr = min(cr, sdCone(p, float3(fi * 0.06, -0.5, -0.4), float3(fi * 0.22, -0.66, -0.82), 0.08, 0.03));
  }
  if (cr < r.x) r = float2(cr, 5.0);
  return r;
}
float2 form(float3 p, float f) {
  if (f < 0.5) return egg(p);
  if (f < 1.5) return chick(p);
  return bird(p);
}
float3 objSpace(float3 p) {
  float co = cos(yaw); float s = sin(yaw);
  p = float3(co * p.x + s * p.z, p.y, -s * p.x + co * p.z);
  p.y += 0.82;            // squash about the feet
  p.y /= (1.0 - sq);
  p.xz /= (1.0 + sq * 0.5);
  p.y -= 0.82;
  return p;
}
float2 scene(float3 p) {
  float3 q = objSpace(p);
  float k = min(1.0 - sq, 1.0 + sq * 0.5);
  float2 a = form(q, fa);
  if (m < 0.002) return float2(a.x * k, a.y);
  float2 b = form(q, fb);
  if (m > 0.998) return float2(b.x * k, b.y);
  return float2(mix(a.x, b.x, m) * k, m < 0.5 ? a.y : b.y);
}
float3 colourOf(float f, float id, float3 q) {
  if (f < 0.5) {
    float3 shell = float3(1.0, 0.93, 0.78);
    float spots = smoothstep(0.1, 0.07, length(fract(float2(atan(q.z, q.x) * 1.1, q.y * 2.2)) - 0.5));
    return mix(shell, float3(0.98, 0.72, 0.62), spots * 0.9);
  }
  if (id > 1.5 && id < 2.5) return float3(1.0, 0.56, 0.18);
  if (id > 2.5 && id < 3.5) return float3(0.12, 0.1, 0.14);
  if (f < 1.5) {
    if (id > 3.5 && id < 4.5) return float3(1.0, 0.76, 0.22);
    return float3(1.0, 0.86, 0.3);
  }
  if (id > 3.5 && id < 4.5) return float3(0.16, 0.5, 0.86);
  if (id > 4.5 && id < 5.5) return float3(0.98, 0.42, 0.52);
  if (id > 5.5) return float3(0.86, 0.95, 1.0);
  return float3(0.26, 0.66, 0.96);
}
float3 normalAt(float3 p) {
  float2 e = float2(0.0025, -0.0025);
  return normalize(e.xyy * scene(p + e.xyy).x + e.yyx * scene(p + e.yyx).x +
                   e.yxy * scene(p + e.yxy).x + e.xxx * scene(p + e.xxx).x);
}
half4 main(float2 xy) {
  float2 uv = (xy - c) / S;
  uv.y = -uv.y;
  float3 ro = float3(0.0, 0.62, 4.6);
  float3 fw = normalize(float3(0.0, 0.05, 0.0) - ro);
  float3 rt = normalize(cross(fw, float3(0.0, 1.0, 0.0)));
  float3 up = cross(rt, fw);
  float3 rd = normalize(fw * 4.0 + rt * uv.x + up * uv.y);
  float bb = dot(ro, rd);
  float cc = dot(ro, ro) - 1.35 * 1.35;
  if (bb * bb - cc < 0.0) return half4(0.0);
  float tt = max(0.0, -bb - sqrt(bb * bb - cc));
  float tmax = -bb + sqrt(bb * bb - cc);
  float dmin = 9.0;
  float id = -1.0;
  float3 p = ro;
  for (int i = 0; i < 64; i++) {
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
    return half4(half3(1.0, 0.95, 0.8) * a * 0.3, a * 0.3);
  }
  float3 n = normalAt(p);
  float3 q = objSpace(p);
  float3 ca = colourOf(fa, form(q, fa).y, q);
  float3 base = ca;
  if (m > 0.002) base = mix(ca, colourOf(fb, form(q, fb).y, q), smoothstep(0.2, 0.8, m));
  // closed eyelids: the eye takes the body colour
  if (id > 2.5 && id < 3.5) base = mix(base, colourOf(m > 0.5 ? fb : fa, 0.0, q), blink);
  float3 L = normalize(float3(-0.5, 0.8, 0.7));
  float dif = clamp(dot(n, L) * 0.6 + 0.4, 0.0, 1.0);
  float sky = 0.5 + 0.5 * n.y;
  float rim = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 2.6);
  float ao = clamp(scene(p + n * 0.1).x / 0.1, 0.0, 1.0);
  ao = 0.55 + 0.45 * ao;
  float3 col = base * (0.36 + 0.8 * dif) * ao + base * float3(0.3, 0.32, 0.28) * sky * 0.3;
  col += float3(1.0, 0.92, 0.6) * rim * (0.25 + 0.9 * charge);
  col += float3(1.0, 0.85, 0.4) * charge * 0.18;
  col += float3(1.0) * pow(max(dot(reflect(-L, n), -rd), 0.0), 30.0) * 0.38 * ao;
  col = float3(1.0) - exp(-col * 1.55);
  return half4(half3(col), 1.0);
}""".trimIndent()
}
