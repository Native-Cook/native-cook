#include <metal_stdlib>
using namespace metal;
struct DroneUnfoldConnectRenderer {
float4 uView, uDrone, uState, uFloor;


const float PI = 3.14159265;
float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float3 rx(float3 p, float a) { float c = cos(a), s = sin(a); return float3(p.x, c*p.y - s*p.z, s*p.y + c*p.z); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float3 rz(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x - s*p.y, s*p.x + c*p.y, p.z); }
float box(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float cap(float3 p, float3 a, float3 b, float r) { float3 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h) - r; }
float cyl(float3 p, float r, float h) { float2 d = float2(length(p.xz) - r, abs(p.y) - h); return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)); }

// ids: 1 body, 2 shell, 3 arm, 4 motor, 5 prop, 6 camera glass, 7 status light
struct Hit { float d; float id; };
Hit H(float d, float id) { Hit h; h.d = d; h.id = id; return h; }
Hit nearer(Hit a, Hit b) { return a.d < b.d ? a : b; }

// one arm: hinged at the body's corner, swinging about the vertical (and dipping for the rear pair)
Hit arm(float3 p, float sx, float sz, float u, float spin) {
  float3 hinge = float3(0.32*sx, 0.0, 0.42*sz);
  float folded = sz > 0.0 ? 0.0 : PI;            // the arm lies along the body, pointing back or forward
  float open = sz > 0.0 ? PI*0.25 : PI*0.75;     // … and swings out to the X
  float a = mix(folded, open, u);
  float3 dir = float3(sin(a)*sx, 0.0, cos(a));
  float dip = sz > 0.0 ? 0.0 : -0.06*(1.0 - u);
  float3 tip = hinge + dir*0.78 + float3(0.0, dip + 0.03, 0.0);
  Hit h = H(cap(p, hinge, tip, 0.065), 3.0);
  float3 m = p - tip;
  h = nearer(h, H(cyl(m - float3(0.0, 0.05, 0.0), 0.1, 0.07) - 0.02, 4.0));
  // props: two blades along the arm when still, a thin disc when spinning
  float3 q = m - float3(0.0, 0.15, 0.0);
  float blades = 1e3;
  if (spin < 0.999) {
    float3 bq = ry(q, atan2(dir.x, dir.z) + spin*12.0);
    blades = box(bq, float3(0.04, 0.008, 0.29), 0.008);
  }
  float disc = cyl(q, 0.3, 0.006) - 0.004;
  h = nearer(h, H(mix(blades, disc, smoothstep(0.35, 0.8, spin)), 5.0));
  return h;
}

Hit drone(float3 p) {
  float u = uState.x, spin = uState.y;
  Hit h = H(box(p - float3(0.0, 0.0, 0.0), float3(0.3, 0.13, 0.62), 0.12), 1.0);
  h = nearer(h, H(box(p - float3(0.0, 0.11, -0.05), float3(0.25, 0.06, 0.48), 0.08), 2.0));
  // the gimbal and its camera at the front
  float3 g = p - float3(0.0, -0.08, 0.64);
  h = nearer(h, H(length(g) - 0.11, 1.0));
  h = nearer(h, H(length(g - float3(0.0, -0.01, 0.07)) - 0.06, 6.0));
  // the status light at the back
  h = nearer(h, H(length(p - float3(0.0, 0.04, -0.66)) - 0.035, 7.0));
  h = nearer(h, arm(p, 1.0, 1.0, u, spin));
  h = nearer(h, arm(p, -1.0, 1.0, u, spin));
  h = nearer(h, arm(p, 1.0, -1.0, u, spin));
  h = nearer(h, arm(p, -1.0, -1.0, u, spin));
  return h;
}

float3 toDrone(float3 p) {
  p -= float3(uDrone.x, uDrone.y + 0.2, 0.0);
  p = rz(p, -uDrone.w);
  p = rx(p, -uDrone.z);
  p = ry(p, -uState.z);
  return p;
}
Hit scene(float3 p) { return drone(toDrone(p)); }
float sd(float3 p) { return scene(p).d; }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 render(float2 xy) {
  float ppu = uView.z;
  float2 sp = (xy - float2(uView.x*0.5, uFloor.x))/ppu;
  sp.y = -sp.y;
  float tilt = uFloor.y;
  float3 fwd = normalize(float3(0.0, -sin(tilt), -cos(tilt)));
  float3 up = normalize(float3(0.0, cos(tilt), -sin(tilt)));
  float3 ro = float3(sp.x, 0.0, 0.0) + up*sp.y - fwd*5.0;
  float3 rd = fwd;
  // the floor's shadow (y = 0): softer and smaller as the drone rises
  float tf = -ro.y/rd.y;
  float3 fp = ro + rd*tf;
  float lift = uDrone.y;
  float2 sq2 = (fp.xz - float2(uDrone.x, 0.0))/float2(0.85 + 0.4*uState.x, 0.75);
  float soft = 0.35 + lift*0.6;
  float shadow = 0.55*exp(-dot(sq2, sq2)/(soft*soft))/(1.0 + lift*1.2);
  float px = 1.0/(ppu*uView.w);
  // only march near the drone
  float3 dc = float3(uDrone.x, uDrone.y + 0.2, 0.0);
  float3 oc = ro - dc;
  float bb = dot(oc, rd);
  float cc = dot(oc, oc) - 1.35*1.35;
  float disc = bb*bb - cc;
  if (disc <= 0.0) return half4(0.0, 0.0, 0.0, half(shadow));
  float t = -bb - sqrt(disc);
  float tEnd = -bb + sqrt(disc);
  float best = 1e4, bestT = t;
  bool hit = false;
  for (int i = 0; i < 72; i++) {
    float d = sd(ro + rd*t);
    if (d < best) { best = d; bestT = t; }
    if (d < 0.35*px) { hit = true; break; }
    t += d*0.9;
    if (t > tEnd) break;
  }
  float cover = hit ? 1.0 : 1.0 - smoothstep(0.35*px, 1.4*px, best);
  if (cover <= 0.0) return half4(0.0, 0.0, 0.0, half(shadow));
  float3 p = ro + rd*bestT;
  if (hit) { for (int i = 0; i < 2; i++) p += rd*sd(p); }
  float id = scene(p).id;
  float e = max(0.002, px);
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sd(p + k.xyy*e) + k.yyx*sd(p + k.yyx*e) + k.yxy*sd(p + k.yxy*e) + k.xxx*sd(p + k.xxx*e));
  float ao = 0.0;
  for (int j = 0; j < 3; j++) { float hh = 0.02 + 0.05*float(j); ao += max(0.0, hh - sd(p + n*hh))/hh/(1.0 + float(j)); }
  ao = clamp(1.0 - ao*0.9, 0.45, 1.0);
  float3 V = -rd;
  float3 key = normalize(float3(-0.4, 0.85, 0.45));
  float nl = clamp((dot(n, key) + 0.35)/1.35, 0.0, 1.0);
  float ndv = max(dot(n, V), 0.0);
  float hn = max(dot(n, normalize(key + V)), 0.0);
  float3 base; float gloss = 0.4; float alpha = cover;
  if (id < 1.5) base = float3(0.16, 0.17, 0.19);
  else if (id < 2.5) { base = float3(0.82, 0.84, 0.87); gloss = 0.6; }
  else if (id < 3.5) base = float3(0.2, 0.21, 0.23);
  else if (id < 4.5) { base = float3(0.55, 0.57, 0.6); gloss = 0.9; }
  else if (id < 5.5) {
    base = float3(0.12, 0.12, 0.14);
    // a spinning prop is a pale, see-through disc
    float sp2 = smoothstep(0.35, 0.8, uState.y);
    // streaks of the blades and a bright rim where their tips run
    float3 dq = toDrone(p);
    float ang = atan2(dq.z, dq.x);
    float streak = 0.5 + 0.5*sin(ang*2.0 + uState.w*40.0);
    base = mix(base, float3(0.82, 0.85, 0.92)*(0.75 + 0.25*streak), sp2);
    // the blur is faint inside and brightest where the blade tips run
    alpha *= mix(1.0, 0.16 + 0.1*streak, sp2);
  }
  else if (id < 6.5) { base = float3(0.05, 0.08, 0.14); gloss = 1.0; }
  else base = mix(float3(0.9, 0.3, 0.25), float3(0.25, 0.95, 0.5), uFloor.z);
  float3 col = mix(base*float3(0.55, 0.55, 0.65), base, nl)*(0.55 + 0.5*nl)*ao;
  col += float3(1.0, 0.98, 0.95)*((0.06 + 0.3*gloss)*pow(hn, 24.0) + 0.6*gloss*pow(hn, 160.0))*ao;
  col += float3(0.5, 0.65, 1.0)*0.22*pow(1.0 - ndv, 3.0);
  if (id > 6.5) col += base*0.8;
  float3 ex = max(col - 0.9, 0.0);
  col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
  float outA = alpha + shadow*(1.0 - alpha);
  float3 outC = toP3(clamp(col, 0.0, 1.0))*alpha;
  return half4(half3(outC), half(outA));
}
};
[[ stitchable ]] half4 DroneUnfoldConnectMain(float2 position, half4 color, float4 view, float4 drone, float4 state, float4 floor) {
 return DroneUnfoldConnectRenderer{view, drone, state, floor}.render(position);
}
