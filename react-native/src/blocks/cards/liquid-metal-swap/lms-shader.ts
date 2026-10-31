// Liquid Metal Swap — a chrome coin that sinks into a pool of mercury and casts the other coin.
//
// Ray-marched signed distances in a world measured in points (y up, floor at y = 0). The coin is a
// rounded cylinder with a milled edge, a raised rim and its token's mark in relief; the pool is a
// flattened ellipsoid with a wobbling shore and ripples. The coin sinks into the pool, which grows
// round it and joins it by a smooth union, so the metal reads as one liquid; the other coin rises out
// of the same pool. Everything under the floor is cut away.
//
// Chrome reads as chrome because of what it reflects: a studio with a sharp horizon, a hard-edged
// softbox overhead and two strip lights, sampled once per channel at slightly different heights, so
// every highlight edge carries a thin fringe. A soft grey gradient (the first try) reads as cast iron.
// The studio turns with uLight, which the finger drags. Premultiplied, sRGB → P3.
export const LMS_SKSL = `
uniform float4 uStage;   // width pt, height pt, pixel ratio, time s
uniform float4 uCoin;    // mark (0 prism, 1 spark), sink 0 … 1, soft 0 … 1, yaw
uniform float4 uPool;    // radius pt, height pt, wobble 0 … 1, drops 0 … 1
uniform float2 uLight;   // studio turn, studio lift
uniform float3 uTint;    // the metal's reflectance at normal incidence: silver … gold
uniform float3 uBg;      // page colour

const float PI = 3.14159265;
const float R = 78.0;    // coin radius
const float T = 8.5;     // coin half thickness
const float3 TARGET = float3(0.0, 64.0, 0.0);
const float DIST = 560.0;
const float FOCAL = 740.0; // pt: the coin, at the target, is drawn 740/560 of its size
const float PITCH = 0.2;

float sq(float v) { return v*v; }
float smin(float a, float b, float k) {
  if (k <= 0.0) return min(a, b);
  float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0);
  return mix(b, a, h) - k*h*(1.0 - h);
}
float2 rot(float2 p, float a) { float c = cos(a), s = sin(a); return float2(c*p.x - s*p.y, s*p.x + c*p.y); }
float3 ry(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x + s*v.z, v.y, -s*v.x + c*v.z); }

float sdRoundCyl(float3 p, float r, float h, float rr) {
  float2 d = float2(length(p.xy) - r + rr, abs(p.z) - h + rr);
  return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - rr;
}
// a 2D shape raised to |z| < h, its edge rounded by rr
float extrude(float3 p, float d2, float h, float rr) {
  float2 w = float2(d2 + rr, abs(p.z) - h + rr);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - rr;
}
float sdTri(float2 p, float r) {
  const float k = 1.7320508;
  p.x = abs(p.x) - r;
  p.y = p.y + r/k;
  if (p.x + k*p.y > 0.0) p = float2(p.x - k*p.y, -k*p.x - p.y)/2.0;
  p.x -= clamp(p.x, -2.0*r, 0.0);
  return -length(p)*sign(p.y);
}
float sdRhombus(float2 p, float2 b) {
  p = abs(p);
  float h = clamp(((b.x - 2.0*p.x)*b.x - (b.y - 2.0*p.y)*b.y)/dot(b, b), -1.0, 1.0);
  float d = length(p - 0.5*b*float2(1.0 - h, 1.0 + h));
  return d*sign(p.x*b.y + p.y*b.x - b.x*b.y);
}
// the token's mark, 2D, pt
float mark(float2 p, float kind) {
  if (kind < 0.5) {
    float tri = abs(sdTri(p + float2(0.0, 4.0), 25.0)) - 4.2;
    return min(tri, length(p + float2(0.0, 1.0)) - 5.5);
  }
  float a = sdRhombus(p, float2(8.5, 33.0));
  float b = sdRhombus(p, float2(33.0, 8.5));
  float c = sdRhombus(rot(p, 0.785398), float2(4.0, 18.0));
  float e = sdRhombus(rot(p, -0.785398), float2(4.0, 18.0));
  return min(min(a, b), min(c, e)) - 0.6;
}

float coinY() { return R + 1.0 - uCoin.y*(2.0*R + 26.0); }

float coin(float3 p) {
  float3 q = p - float3(0.0, coinY(), 0.0);
  q = ry(q, uCoin.w);
  float soft = uCoin.z;
  float ang = atan(q.y, q.x);
  // the field domes a little, so the face does not mirror one flat colour
  q.z = sign(q.z)*max(abs(q.z) - 3.6*(1.0 - soft)*(1.0 - sq(min(length(q.xy)/R, 1.0))), 0.0);
  float edge = R - 0.4 + 0.4*sin(ang*96.0)*(1.0 - soft);
  float d = sdRoundCyl(q, edge, T, mix(2.6, 9.0, soft));
  float relief = 1.7*(1.0 - soft);
  if (relief > 0.02) {
    float ring = abs(length(q.xy) - (R - 9.5)) - 3.0;
    d = smin(d, extrude(q, ring, T + relief, 0.9), 0.8);
    d = smin(d, extrude(q, mark(q.xy, uCoin.x), T + relief, 0.9), 0.8);
  }
  return d;
}

float pool(float3 p) {
  float rp = uPool.x;
  if (rp < 1.0) return 1e4;
  float t = uStage.w;
  float wob = uPool.z;
  float ang = atan(p.z, p.x);
  float rw = rp*(1.0 + wob*(0.05*sin(3.0*ang + t*1.7) + 0.03*sin(5.0*ang - t*2.3)));
  float r = length(p.xz);
  float y = p.y - uPool.y*0.22*wob*sin(r*0.11 - t*7.0)*exp(-r/max(rp, 1.0));
  float3 e = float3(rw, uPool.y, rw*0.78);
  float3 s = float3(p.x, y, p.z);
  float k0 = length(s/e);
  float k1 = length(s/(e*e));
  return k0*(k0 - 1.0)/max(k1, 1e-4);
}

float drops(float3 p) {
  float amt = uPool.w;
  if (amt < 0.01) return 1e4;
  float d = 1e4;
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    float a = fi*1.7 + 0.6;
    float ph = clamp(amt*1.25 - fi*0.08, 0.0, 1.0);
    float h = 58.0*sin(PI*ph)*(0.7 + 0.3*fract(fi*0.61));
    float rr = uPool.x*(0.55 + 0.25*fract(fi*0.37));
    float3 c = float3(cos(a)*rr, h + 4.0, sin(a)*rr*0.8);
    d = min(d, length(p - c) - (3.5 + 2.5*sin(PI*ph)));
  }
  return d;
}

float scene(float3 p) {
  float liquid = clamp(uPool.x/60.0, 0.0, 1.0);
  float d = smin(coin(p), pool(p), 24.0*liquid);
  d = smin(d, drops(p), 10.0);
  return max(d, -p.y);
}

float3 normalAt(float3 p) {
  const float e = 0.35;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*scene(p + k.xyy*e) + k.yyx*scene(p + k.yyx*e) + k.yxy*scene(p + k.yxy*e) + k.xxx*scene(p + k.xxx*e));
}

// the studio the chrome reflects
float3 env(float3 r) {
  r = ry(r, uLight.x);
  float y = r.y + uLight.y;
  float az = atan(r.x, -r.z);
  float3 sky = mix(float3(0.30, 0.32, 0.37), float3(0.80, 0.83, 0.90), smoothstep(0.0, 0.85, y));
  sky += 2.2*smoothstep(0.80, 0.83, y);
  float s1 = smoothstep(0.15, 0.11, abs(az - 1.95))*smoothstep(0.03, 0.08, y)*smoothstep(0.72, 0.64, y);
  float s2 = smoothstep(0.10, 0.07, abs(az + 1.25))*smoothstep(0.05, 0.10, y)*smoothstep(0.60, 0.52, y);
  sky += 1.7*s1 + 1.1*s2;
  sky += float3(1.0, 0.86, 0.70)*0.55*exp(-sq((y - 0.04)/0.008));
  float3 ground = mix(float3(0.035, 0.038, 0.05), float3(0.12, 0.125, 0.14), smoothstep(-0.35, 0.0, y));
  float3 c = mix(ground, sky, smoothstep(-0.006, 0.006, y));
  // a white card standing in front of the coin, as in a product shot: it lights the faces, and its
  // hard top edge crosses the domed field as a line of light
  float card = smoothstep(1.3, 0.9, abs(az))*smoothstep(-0.62, -0.42, y);
  float top = smoothstep(-0.195, -0.185, y);
  float3 face = mix(mix(float3(0.10, 0.11, 0.13), float3(0.34, 0.35, 0.39), smoothstep(-0.36, -0.19, y)),
                    float3(0.92, 0.94, 0.98)*mix(1.0, 0.7, smoothstep(-0.19, -0.02, y)), top);
  c = mix(c, face, card);
  return c;
}

float3 chrome(float3 p, float3 rd) {
  float3 n = normalAt(p);
  float3 r = reflect(rd, n);
  float lift = 0.007;
  float3 c = float3(env(r + float3(0.0, lift, 0.0)).r, env(r).g, env(r - float3(0.0, lift, 0.0)).b);
  float f = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 5.0);
  float3 F = mix(uTint, float3(1.0), f);
  float ao = clamp(0.35 + 0.65*scene(p + n*7.0)/7.0, 0.0, 1.0);
  ao *= clamp(0.55 + 0.45*p.y/14.0, 0.0, 1.0) + (1.0 - clamp(0.55 + 0.45*p.y/14.0, 0.0, 1.0))*0.4;
  return c*F*ao;
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
float3 tone(float3 c) { return 1.0 - exp(-c*1.15); }

half4 main(float2 xy) {
  float W = uStage.x, H = uStage.y;
  float2 uv = float2(xy.x - 0.5*W, 0.5*H - xy.y);
  float3 fwd = float3(0.0, -sin(PITCH), cos(PITCH));
  float3 ro = TARGET - fwd*DIST;
  float3 right = float3(1.0, 0.0, 0.0);
  float3 up = cross(fwd, right);
  float3 rd = normalize(fwd*FOCAL + right*uv.x + up*uv.y);

  // backdrop: the page, a soft pool of light on the floor
  float tFloor = rd.y < 0.0 ? -ro.y/rd.y : 1e5;
  float3 bg = uBg;
  if (tFloor < 1e5) {
    float3 fp = ro + rd*tFloor;
    float spot = exp(-sq(length(fp.xz*float2(1.0, 1.6))/260.0))*smoothstep(H, H - 48.0, xy.y);
    bg = uBg + float3(0.10, 0.105, 0.12)*spot;
    // contact shadow from the metal
    float occ = clamp(scene(fp + float3(0.0, 10.0, 0.0))/10.0, 0.0, 1.0);
    float occ2 = clamp(scene(fp + float3(0.0, 30.0, 0.0))/30.0, 0.0, 1.0);
    bg *= mix(0.45, 1.0, occ)*mix(0.75, 1.0, occ2);
  }

  // march only inside the box the metal can reach
  float3 bmin = float3(-175.0, 0.0, -175.0), bmax = float3(175.0, 180.0, 175.0);
  float3 inv = 1.0/rd;
  float3 ta = (bmin - ro)*inv, tb = (bmax - ro)*inv;
  float3 tlo = min(ta, tb), thi = max(ta, tb);
  float t0 = max(max(tlo.x, tlo.y), tlo.z), t1 = min(min(thi.x, thi.y), thi.z);
  float t = max(t0, 0.0);
  float tEnd = min(t1, tFloor);
  float dmin = 1e5;
  float tmin = t;
  bool hit = false;
  if (t0 < t1) {
    for (int i = 0; i < 80; i++) {
      float3 p = ro + rd*t;
      float d = scene(p);
      if (d < dmin) { dmin = d; tmin = t; }
      if (d < 0.08) { hit = true; break; }
      t += d*0.9;
      if (t > tEnd) break;
    }
  }
  float3 col = bg;
  float pxw = 1.0/uStage.z*(tmin/FOCAL)*1.4;
  if (hit) {
    col = tone(chrome(ro + rd*t, rd));
  } else if (dmin < pxw) {
    float a = 1.0 - dmin/pxw;
    col = mix(bg, tone(chrome(ro + rd*tmin, rd)), a);
  }
  return half4(half3(toP3(clamp(col, 0.0, 1.0))), 1.0);
}
`;
