package com.ngocdevv.nativecook.blocks.pickers.iconerascrubber

/** AGSL translation of ies-shader.ts. Coordinates are in the dp-space canvas below the density transform. */
internal val ICON_ERA_SCRUBBER_AGSL = """
uniform float4 uObj;    // icon centre x, y (pt), half size (pt), pixel ratio
uniform float4 uEra;    // era 0…3, yaw, pitch, the lights' phase (rad, one turn per loop)
uniform float4 uPage;   // page width, height (pt), glow of the lights 0…1, -

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float dot2(float3 v) { return dot(v, v); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float rrect(float2 p, float2 e, float r) { float2 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float box(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float cap(float3 p, float3 a, float3 b, float r) { float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h) - r; }
float tri(float3 p, float3 a, float3 b, float3 c) {
  float3 ba = b - a, pa = p - a, cb = c - b, pb = p - b, ac = a - c, pc = p - c;
  float3 nor = cross(ba, ac);
  float s = sign(dot(cross(ba, nor), pa)) + sign(dot(cross(cb, nor), pb)) + sign(dot(cross(ac, nor), pc));
  float d;
  if (s < 2.0) d = min(min(dot2(ba*clamp(dot(ba, pa)/dot2(ba), 0.0, 1.0) - pa), dot2(cb*clamp(dot(cb, pb)/dot2(cb), 0.0, 1.0) - pb)), dot2(ac*clamp(dot(ac, pc)/dot2(ac), 0.0, 1.0) - pc));
  else d = dot(nor, pa)*dot(nor, pa)/dot2(nor);
  return sqrt(d);
}

// how much of each era is present at e (they cross over between neighbours)
float4 weights(float e) {
  float w0 = 1.0 - smoothstep(0.0, 1.0, clamp(e, 0.0, 1.0));
  float w3 = smoothstep(2.0, 3.0, e);
  float w1 = 1.0 - w0 - smoothstep(1.0, 2.0, e);
  w1 = max(w1, 0.0);
  float w2 = max(1.0 - w0 - w1 - w3, 0.0);
  return float4(w0, w1, w2, w3);
}

// the slab: corner radius, half thickness and bevel per era
float3 slabOf(float4 w) {
  return float3(dot(w, float4(0.34, 0.44, 0.46, 0.46)), dot(w, float4(0.1, 0.02, 0.12, 0.15)), dot(w, float4(0.08, 0.0, 0.11, 0.15)));
}

float slab(float3 p, float3 s) {
  float d2 = rrect(p.xy, float2(1.0), s.x);
  float b = max(s.z, 0.004);
  float2 q = float2(d2 + b, abs(p.z) - s.y + b);
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - b;
}

// the symbol on the face: an envelope that folds into a paper plane (fold 0…1)
float symbol(float3 p, float zf, float fold, float printed) {
  float h = mix(0.04, 0.012, printed); // a flat era's symbol is printed, not raised
  float env = box(p - float3(0.0, -0.02, zf + h), float3(0.6, 0.41, h), min(0.03, h));
  // the flap's folds stand up a little
  float3 fa = float3(-0.58, 0.39, zf + 2.0*h), fb = float3(0.0, -0.02, zf + 2.0*h), fc = float3(0.58, 0.39, zf + 2.0*h);
  float folds = min(cap(p, fa, fb, 0.022), cap(p, fb, fc, 0.022));
  env = smin(env, folds, 0.02);
  if (fold <= 0.001) return env;
  // the plane: two wings meeting on a crease, lifted off the face
  float3 N = float3(0.6, 0.42, zf + 0.16), A = float3(-0.62, 0.0, zf + 0.2), C = float3(-0.16, -0.12, zf + 0.1), B = float3(-0.36, -0.58, zf + 0.04);
  float plane = min(tri(p, N, A, C), tri(p, N, C, B)) - 0.022;
  if (fold >= 0.999) return plane;
  return mix(env, plane, fold) - 0.06*sin(PI*fold);
}

float zFace(float3 s) { return s.y; }

float sceneD(float3 p, float4 w, float3 s, float fold, out float which) {
  float t = slab(p, s);
  float m = symbol(p, zFace(s) - 0.004, fold, w.y);
  which = m < t ? 1.0 : 0.0;
  return min(t, m);
}
float sd(float3 p, float4 w, float3 s, float fold) { float k; return sceneD(p, w, s, fold, k); }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

// the page: a dark room with two soft lights drifting slowly
float3 page(float2 xy) {
  float2 u = xy/uPage.xy;
  float t = uEra.w;
  float2 a = float2(0.22 + 0.04*sin(t), 0.3 + 0.03*cos(t));
  float2 b = float2(0.8 + 0.03*cos(t + 2.0), 0.52 + 0.04*sin(t + 1.0));
  float ar = uPage.x/uPage.y;
  float da = length((u - a)*float2(ar, 1.0)), db = length((u - b)*float2(ar, 1.0));
  float3 c = float3(0.035, 0.037, 0.06);
  c += float3(1.0, 0.45, 0.2)*0.45*exp(-sq(da/0.24))*uPage.z;
  c += float3(0.2, 0.6, 1.0)*0.45*exp(-sq(db/0.26))*uPage.z;
  return c;
}

half4 main(float2 xy) {
  float3 bg = page(xy);
  float S = uObj.z;
  float2 uv = (xy - uObj.xy)/S;
  uv.y = -uv.y;
  float e = clamp(uEra.x, 0.0, 3.0);
  float4 w = weights(e);
  float3 s = slabOf(w);
  float fold = smoothstep(2.15, 2.85, e);
  // a soft shadow under the icon (none for the flat era)
  float sh = (1.0 - smoothstep(-0.2, 0.55, rrect(uv - float2(0.04, -0.12), float2(0.98), 0.5)))*(0.5 - 0.4*w.y);
  bg *= 1.0 - sh*0.7;
  if (max(abs(uv.x), abs(uv.y)) > 1.35) return half4(half3(toP3(bg)), 1.0);
  float pxo = 1.0/(uObj.w*S);
  float yaw = uEra.y, pitch = uEra.z;
  float best = 1e4, bestZ = 0.6;
  bool hit = false;
  float z = 0.6;
  for (int i = 0; i < 48; i++) {
    float3 p = ry(rx(float3(uv, z), pitch), yaw);
    float d = sd(p, w, s, fold);
    if (d < best) { best = d; bestZ = z; }
    if (d < 0.3*pxo) { hit = true; break; }
    z -= max(d*0.9, 0.35*pxo);
    if (z < -0.5) break;
  }
  float cover = hit ? 1.0 : 1.0 - smoothstep(0.3*pxo, 1.3*pxo, best);
  if (cover <= 0.0) return half4(half3(toP3(bg)), 1.0);
  float3 p = ry(rx(float3(uv, bestZ), pitch), yaw);
  float which;
  sceneD(p, w, s, fold, which);
  float ee = max(0.003, 1.0*pxo);
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sd(p + k.xyy*ee, w, s, fold) + k.yyx*sd(p + k.yyx*ee, w, s, fold) + k.yxy*sd(p + k.yxy*ee, w, s, fold) + k.xxx*sd(p + k.xxx*ee, w, s, fold));
  float3 V = ry(rx(float3(0.0, 0.0, 1.0), pitch), yaw);
  float3 key = normalize(float3(-0.45, 0.7, 0.8));
  float nl = clamp((dot(n, key) + 0.35)/1.35, 0.0, 1.0);
  float ndv = max(dot(n, V), 0.0);
  float3 H = normalize(key + V);
  float hn = max(dot(n, H), 0.0);
  float3 col;
  if (which < 0.5) {
    // the tile
    float gy = 0.5 + 0.5*p.y;
    float3 glossy = mix(float3(0.03, 0.22, 0.7), float3(0.25, 0.58, 1.0), gy)*(0.55 + 0.6*nl);
    float arc = smoothstep(0.02, -0.02, p.y - (0.12 + 0.35*p.x*p.x))*0.0 + step(0.1 + 0.32*p.x*p.x, p.y);
    glossy = mix(glossy, float3(1.0), 0.32*arc*smoothstep(1.0, 0.2, p.y));
    glossy += float3(1.0)*0.7*pow(hn, 50.0);
    float3 flatC = mix(float3(0.07, 0.45, 1.0), float3(0.3, 0.75, 1.0), gy);
    float3 soft = mix(float3(0.1, 0.42, 0.98), float3(0.38, 0.72, 1.0), gy)*(0.7 + 0.42*nl) + float3(1.0)*0.25*pow(hn, 24.0);
    // glass: the room's lights bent through it, a cool tint, a bright rim
    float2 bend = n.xy*S*0.9 + uv*S*0.12;
    float3 through = page(xy - float2(bend.x, -bend.y))*1.6 + float3(0.06, 0.1, 0.18);
    float3 glass = mix(through, float3(0.8, 0.9, 1.0), 0.12) + float3(0.8, 0.9, 1.0)*pow(1.0 - ndv, 2.5)*0.8 + float3(1.0)*0.8*pow(hn, 90.0);
    col = glossy*w.x + flatC*w.y + soft*w.z + glass*w.w;
  } else {
    // the symbol
    float2 q = p.xy - float2(0.0, -0.02);
    float edge = rrect(q, float2(0.6, 0.41), 0.03);
    // airmail stripes round the glossy era's envelope, and a red stamp
    float band = step(-0.075, edge);
    float stripe = step(0.5, fract((q.x + q.y)*5.0));
    float3 paper = mix(float3(0.99, 0.96, 0.88), mix(float3(0.85, 0.15, 0.18), float3(0.15, 0.3, 0.8), stripe), band*(1.0 - fold));
    float stamp = step(rrect(q - float2(0.38, 0.2), float2(0.1, 0.12), 0.02), 0.0)*(1.0 - fold);
    paper = mix(paper, float3(0.9, 0.25, 0.2), stamp*0.9);
    float3 glossyS = paper*(0.6 + 0.5*nl) + float3(1.0)*0.4*pow(hn, 40.0);
    float3 flatS = float3(1.0);
    float3 softS = float3(1.0)*(0.75 + 0.3*nl) + float3(1.0)*0.15*pow(hn, 30.0);
    float3 glassS = float3(0.92, 0.96, 1.0)*(0.72 + 0.3*nl) + float3(0.7, 0.85, 1.0)*pow(1.0 - ndv, 2.0)*0.5 + float3(1.0)*0.6*pow(hn, 80.0);
    col = glossyS*w.x + flatS*w.y + softS*w.z + glassS*w.w;
  }
  float3 ex = max(col - 0.9, 0.0);
  col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
  col = mix(bg, col, cover);
  return half4(half3(toP3(clamp(col, 0.0, 1.0))), 1.0);
}
""".trimIndent()
