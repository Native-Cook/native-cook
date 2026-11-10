package com.ngocdevv.nativecook.blocks.modals.pearlshellpremium

internal object PearlShellPremiumShader {
    val source = """
uniform float2 uRes;
uniform float2 uC;          // hinge-line centre of the clam (pt)
uniform float uS;
uniform float uYaw;
uniform float uOpen;        // the top valve's angle, rad
uniform float uGlow;        // 0 … 1, the pearl's light
uniform float uLift;        // the pearl rising out of the shell, units
uniform float uSpin;        // the pearl's sheen turning
uniform float uPhase;       // caustics
uniform float uPx;

const float3 TINT = float3(0.93, 0.94, 0.99);
const float3 HINGE = float3(0.0, 0.0, -0.78);
float sq(float v) { return v*v; }
float dot2(float2 v) { return dot(v, v); }

// a valve: half an ellipsoid, open side up (y >= 0 cut), ribs fanning from the hinge
float sdValve(float3 p) {
  float3 r = float3(1.0, 0.34, 0.86);
  float3 q = p/r;
  float k0 = length(q);
  float k1 = length(q/r);
  float d = k0*(k0 - 1.0)/k1;
  // ribs: radial grooves around the hinge
  float a = atan(p.x, p.z - HINGE.z);
  d += 0.018*cos(a*15.0)*smoothstep(0.1, 0.6, length(p.xz - HINGE.xz));
  // a shell, not a solid: hollow it out
  d = abs(d + 0.05) - 0.05;
  // keep the half under the opening plane, with a scalloped rim
  float rim = p.y - 0.015*cos(a*15.0);
  return max(d, rim);
}
float3 rx(float3 v, float a) { float c = cos(a), s = sin(a); return float3(v.x, c*v.y - s*v.z, s*v.y + c*v.z); }
float3 ry(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x + s*v.z, v.y, -s*v.x + c*v.z); }
float sdBottom(float3 p) { return sdValve(p); }
float sdTop(float3 p) {
  // the top valve is the bottom one flipped, turned open about the hinge
  float3 q = rx(p - HINGE, uOpen) + HINGE;
  q.y = -q.y;
  return sdValve(q);
}
float3 pearlC() { return float3(0.0, 0.08 + uLift, 0.05); }
const float PEARL_R = 0.27;
float sdShell(float3 p) { return min(sdBottom(p), sdTop(p)); }
float3 nShell(float3 p) {
  const float e = 0.0015;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*sdShell(p + k.xyy*e) + k.yyx*sdShell(p + k.yyx*e) + k.yxy*sdShell(p + k.yxy*e) + k.xxx*sdShell(p + k.xxx*e));
}

// the view: from in front and above
float3 toObj(float3 v) { return ry(rx(v, -0.62), -uYaw); }
float3 toWorld(float3 v) { return rx(ry(v, uYaw), 0.62); }

float3 sea(float3 r) {
  float3 c = mix(float3(0.03, 0.05, 0.1), float3(0.12, 0.2, 0.32), smoothstep(-0.3, 0.9, r.y));
  c += float3(0.7, 0.85, 1.0)*6.0*smoothstep(0.93, 0.96, dot(r, normalize(float3(-0.3, 0.9, 0.3))));
  c += float3(0.6, 0.8, 1.0)*3.0*smoothstep(0.86, 0.9, r.x)*(1.0 - smoothstep(0.3, 0.45, abs(r.y - 0.2)));
  c += float3(0.9, 0.85, 1.0)*2.0*pow(max(dot(r, normalize(float3(-0.2, 0.4, 0.9))), 0.0), 12.0);
  return c;
}
float3 film(float cosi, float3 p) {
  float d = 1.4 + 0.9*cosi + 0.2*sin(2.5*p.x + 2.0*p.z);
  return 0.6 + 0.4*cos(6.2831853*(d + float3(0.0, 0.33, 0.67)));
}

float caustic(float2 q) {
  float c = 0.0;
  c += sin(q.x*0.045 + uPhase) + sin(q.y*0.05 - uPhase*0.8);
  c += sin((q.x + q.y)*0.035 + uPhase*1.3) + sin((q.x - q.y)*0.04 - uPhase*0.6);
  return pow(clamp(0.5 + 0.18*c, 0.0, 1.0), 4.0);
}
float3 page(float2 xy) {
  float3 col = mix(float3(0.035, 0.06, 0.12), float3(0.06, 0.1, 0.17), clamp(xy.y/uRes.y, 0.0, 1.0));
  // sand under the clam, lit by caustics and by the pearl
  float2 q = (xy - uC - float2(0.0, 0.25*uS))/(uS*float2(2.4, 1.0));
  float sand = exp(-dot(q, q)*1.2);
  col += float3(0.08, 0.11, 0.16)*sand + float3(0.2, 0.3, 0.38)*caustic(xy)*sand*0.35;
  col += float3(0.95, 0.88, 1.0)*0.35*uGlow*exp(-dot2((xy - uC)/(uS*1.6)));
  // the clam's shadow
  float2 qs = (xy - uC - float2(0.0, 0.42*uS))/(uS*float2(1.1, 0.22));
  col *= 1.0 - 0.6*exp(-dot(qs, qs)*1.6);
  return col;
}

float3 render(float2 xy, out float cover) {
  float2 u = (xy - uC)/uS;
  float px = uPx/uS;
  float3 ro = toObj(float3(u.x, -u.y, 6.0));
  float3 rd = toObj(float3(0.0, 0.0, -1.0));
  float t = 3.0;
  float dmin = 1e3;
  float tBest = t;
  int what = 0;
  for (int i = 0; i < 80; i++) {
    float3 p = ro + rd*t;
    float ds = sdShell(p);
    float dp = length(p - pearlC()) - PEARL_R;
    float d = min(ds, dp);
    if (d < dmin) { dmin = d; tBest = t; what = ds < dp ? 1 : 2; }
    if (d < 0.001) break;
    t += d*0.8;
    if (t > 9.0) break;
  }
  cover = 1.0 - smoothstep(0.0, 1.6*px, dmin);
  if (cover <= 0.0) return float3(0.0);
  float3 p = ro + rd*tBest;
  float3 vW = float3(0.0, 0.0, -1.0);
  if (what == 2) {
    // the pearl: nacre, a soft key, a thin-film sheen that turns, its own light
    float3 n = normalize(p - pearlC());
    float3 nS = ry(n, uSpin);
    float3 nW = toWorld(n);
    float cosi = clamp(dot(-rd, n), 0.0, 1.0);
    float key = clamp(dot(nW, normalize(float3(-0.4, 0.8, 0.45))), 0.0, 1.0);
    float3 base = float3(0.94, 0.92, 0.97)*(0.5 + 0.55*key);
    float3 sheen = film(cosi, nS*2.0);
    float3 col = base*mix(float3(1.0), sheen*1.1, 0.18) + min(sea(reflect(vW, nW)), float3(2.0))*0.25*pow(1.0 - cosi, 2.0);
    col += float3(1.0, 0.96, 1.0)*0.45*uGlow*(0.4 + 0.6*cosi);
    return col;
  }
  float3 n = nShell(p);
  float3 nW = toWorld(n);
  float cosi = clamp(abs(dot(rd, n)), 0.0, 1.0);
  float F = 0.04 + 0.96*pow(1.0 - cosi, 5.0);
  float3 refl = sea(reflect(vW, nW));
  // frosted pearly glass: milky, iridescent, lit from the pearl inside
  float3 nacre = film(cosi, p*1.6);
  float wrap = clamp(dot(nW, normalize(float3(-0.3, 0.9, 0.3)))*0.5 + 0.5, 0.0, 1.0);
  float3 body = mix(float3(0.32, 0.38, 0.5), float3(0.86, 0.88, 0.96), wrap)*TINT;
  body *= mix(float3(1.0), nacre*1.08, 0.2);
  float inner = exp(-sq(length(p - pearlC())/0.9));
  body += float3(1.0, 0.95, 1.0)*0.5*uGlow*inner;
  body = mix(body, page(xy)*1.2, 0.18);
  return mix(body, refl, F*0.8);
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

uniform float uDensity;
half4 main(float2 pixel) {
  float2 xy = pixel/uDensity;
  float3 col = page(xy);
  float2 u = (xy - uC)/uS;
  if (abs(u.x) < 1.3 && u.y > -2.0 && u.y < 1.1) {
    float cv;
    float3 g = render(xy, cv);
    col = mix(col, g, cv);
  }
  col = col/(1.0 + 0.12*max(max(col.r, col.g), col.b) - 0.12);
  float3 o = clamp(col, 0.0, 1.0);
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}
""".trimIndent()
}
