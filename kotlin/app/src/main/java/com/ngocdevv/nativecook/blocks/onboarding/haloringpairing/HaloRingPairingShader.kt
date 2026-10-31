package com.ngocdevv.nativecook.blocks.onboarding.haloringpairing

// A literal SkSL → AGSL port; coordinates and uniforms are in density-independent points.
internal const val HaloRingPairingShader = """

uniform float uDensity;
uniform float4 uRing;   // centre x, y (pt), unit size (pt), pixel ratio
uniform float4 uState;  // condensed 0…1, yaw (spin), pitch (tilt), ring scale (the size)
uniform float4 uLive;   // phase (rad, one turn per loop), search ripples 0…1, opacity, -

const float PI = 3.14159265;
const float R = 0.8;
float sq(float v) { return v*v; }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float rrect(float2 p, float2 e, float r) { float2 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }

// world → the ring's own space (its axis along z)
float3 toRing(float3 p) { return rx(ry(p, -uState.y), -uState.z)/uState.w; }

float bandSection() { return mix(0.06, 1.0, smoothstep(0.2, 1.0, uState.x)); }

// the band, and its distance to the centre line (for the glow)
float band(float3 q, out float core) {
  float r = length(q.xy) - R;
  core = length(float2(r, q.z));
  float s = bandSection();
  float d = rrect(float2(r, q.z), float2(0.075, 0.18)*s, 0.06*s);
  // three sensor domes on the inside, at the bottom of the ring
  float a = atan(q.y, q.x);
  float da = a + PI*0.5;
  float bump = 1e3;
  for (int i = -1; i <= 1; i++) {
    float aa = float(i)*0.22;
    float3 c = float3(cos(aa - PI*0.5), sin(aa - PI*0.5), 0.0)*(R - 0.075*s);
    bump = min(bump, length(q - c) - 0.045*s);
  }
  return min(d, bump);
}
float sceneD(float3 p, out float core) { float d = band(toRing(p), core); return d*uState.w*0.9; }
float sd(float3 p) { float c; return sceneD(p, c); }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

// a soft studio seen in the metal: a bright overhead softbox, a warm card on the left, a dim floor
float3 studio(float3 r) {
  float top = smoothstep(0.35, 0.75, r.y)*(1.0 - smoothstep(0.92, 1.0, r.y));
  float card = exp(-sq((r.x + 0.75)/0.18))*smoothstep(-0.3, 0.3, r.y);
  float rim = exp(-sq((r.x - 0.85)/0.12));
  // a dark horizon between floor and sky gives the metal its contrast
  float dark = exp(-sq((r.y - 0.12)/0.14));
  return (float3(0.07, 0.075, 0.08) + float3(0.85)*top + float3(1.0, 0.82, 0.62)*0.55*card + float3(0.55, 1.0, 0.75)*0.3*rim)*(1.0 - 0.75*dark);
}

half4 main(float2 pixel) {
  float2 xy = pixel / uDensity;
  float S = uRing.z;
  float2 uv = (xy - uRing.xy)/S;
  uv.y = -uv.y;
  float c = uState.x;
  float3 acc = float3(0.0);
  float alpha = 0.0;
  float t = uLive.x;
  // search ripples across the ring's plane: intersect the view ray with the plane, measure the radius
  float3 nrm = ry(rx(float3(0.0, 0.0, 1.0), uState.z), uState.y);
  if (uLive.y > 0.0 && abs(nrm.z) > 0.05) {
    float zp = -(nrm.x*uv.x + nrm.y*uv.y)/nrm.z;
    float rho = length(toRing(float3(uv, zp)).xy)*uState.w;
    for (int k = 0; k < 3; k++) {
      float u = fract(t*1.5/(2.0*PI) + float(k)/3.0);
      float rk = R + u*1.4;
      float g = exp(-sq((rho - rk)/0.035))*(1.0 - u)*(1.0 - u)*uLive.y;
      acc += float3(0.35, 1.0, 0.6)*g*0.9;
      alpha += g*0.9;
    }
  }
  if (dot(uv, uv) > 2.6*2.6) return half4(half3(toP3(min(acc, 1.0))), half(min(alpha, 1.0)))*half(uLive.z);
  float pxo = 1.0/(uRing.w*S);
  float z = 1.4, best = 1e4, bestZ = z, minCore = 1e4;
  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float core;
    float d = sceneD(float3(uv, z), core);
    minCore = min(minCore, core*uState.w);
    if (d < best) { best = d; bestZ = z; }
    if (d < 0.3*pxo) { hit = true; break; }
    z -= max(d*0.85, 0.35*pxo);
    if (z < -1.4) break;
  }
  // the light of the search: a glowing core that fades as the metal condenses, with a flash at the end
  float flash = exp(-sq((c - 0.72)/0.12));
  float glowAmt = (1.0 - smoothstep(0.55, 0.95, c))*0.9 + flash*0.6;
  float glow = exp(-minCore*minCore/0.006)*glowAmt + exp(-minCore/0.08)*0.25*glowAmt;
  acc += float3(0.4, 1.0, 0.62)*glow;
  alpha += glow;
  float cover = hit ? 1.0 : 1.0 - smoothstep(0.3*pxo, 1.3*pxo, best);
  cover *= smoothstep(0.15, 0.4, c);
  if (cover > 0.0) {
    float3 p = float3(uv, bestZ);
    if (hit) { for (int i = 0; i < 2; i++) p.z -= sd(p); }
    float e = max(0.002, 1.0*pxo);
    const float2 kk = float2(1.0, -1.0);
    float3 n = normalize(kk.xyy*sd(p + kk.xyy*e) + kk.yyx*sd(p + kk.yyx*e) + kk.yxy*sd(p + kk.yxy*e) + kk.xxx*sd(p + kk.xxx*e));
    float3 q = toRing(p);
    float3 V = float3(0.0, 0.0, 1.0);
    float3 rf = reflect(-V, n);
    // inside or outside the band?
    float3 radial = normalize(float3(q.xy, 0.0));
    float3 nl = rx(ry(n, -uState.y), -uState.z);
    float inside = smoothstep(0.2, -0.4, dot(nl, radial));
    float ndv = max(dot(n, V), 0.0);
    float fr = 0.25 + 0.75*pow(1.0 - ndv, 4.0);
    // titanium: brushed along the band, a circumferential streak
    float3 tang = ry(rx(normalize(float3(-q.y, q.x, 0.0)), uState.z), uState.y);
    float3 key = normalize(float3(-0.3, 0.8, 0.6));
    float3 H = normalize(key + V);
    float th = dot(tang, H);
    float aniso = pow(sqrt(max(1.0 - th*th, 0.0)), 70.0);
    float3 ti = float3(0.5, 0.5, 0.53)*(0.16 + 0.45*max(dot(n, key), 0.0)) + studio(rf)*mix(0.45, 0.8, fr) + float3(1.0, 0.98, 0.95)*aniso*0.6;
    // the black inner coating, glossy, with the green light among the sensors
    float3 blk = float3(0.035, 0.037, 0.04) + studio(rf)*0.18*fr;
    float a = atan(q.y, q.x) + PI*0.5;
    float led = exp(-sq(a/0.05) - sq(q.z/0.05))*(0.6 + 0.4*sin(t*4.0));
    blk += float3(0.25, 1.0, 0.5)*led*1.4;
    float3 col = mix(ti, blk, inside);
    // the last of the light runs over the metal as it condenses
    col += float3(0.5, 1.0, 0.7)*flash*0.45*(1.0 - inside);
    float3 ex = max(col - 0.9, 0.0);
    col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
    acc = col*cover + acc*(1.0 - cover);
    alpha = cover + alpha*(1.0 - cover);
  }
  float o = uLive.z;
  return half4(half3(toP3(clamp(acc, 0.0, 1.0)))*half(o), half(min(alpha, 1.0)*o));
}

"""
