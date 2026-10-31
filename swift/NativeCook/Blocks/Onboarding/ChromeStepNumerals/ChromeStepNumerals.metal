#include <metal_stdlib>
using namespace metal;
constant float ChromeStepNumeralsPI = 3.14159265;
constant float ChromeStepNumeralsTube = 0.23;

static float ChromeStepNumeralsMod(float x, float y) { return x - y * floor(x / y); }
struct ChromeStepNumeralsShader {

float4 uView;   // centre x, y (pt), unit size (pt), pixel ratio
float4 uStep;   // numeral 0…2 (1, 2, 3; continuous between), turn (rad), lean (rad), bob (units)
float4 uLight;  // studio turn (rad), floor y (pt), -, -



float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float3 ry(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x + s*p.z, p.y, -s*p.x + c*p.z); }
float3 rz(float3 p, float a) { float c = cos(a), s = sin(a); return float3(c*p.x - s*p.y, s*p.x + c*p.y, p.z); }
float seg2(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0); return length(pa - ba*h); }
// an arc of radius r round c, from angle a1 counter-clockwise to a0 (radians)
float arc2(float2 p, float2 c, float r, float a1, float a0) {
  float2 q = p - c;
  float t = ChromeStepNumeralsMod(atan2(q.y, q.x) - a1, 2.0*ChromeStepNumeralsPI);
  float span = ChromeStepNumeralsMod(a0 - a1, 2.0*ChromeStepNumeralsPI);
  if (t <= span) return abs(length(q) - r);
  float2 e0 = c + r*float2(cos(a1), sin(a1));
  float2 e1 = c + r*float2(cos(a0), sin(a0));
  return min(length(p - e0), length(p - e1));
}
// a stroke (2D distance d2) inflated into a tube, a little fatter in the middle of the depth
float tube(float d2, float z) { return length(float2(d2, z*1.08)) - ChromeStepNumeralsTube; }

float one(float3 p) {
  float2 q = p.xy;
  float d = seg2(q, float2(0.1, -0.92), float2(0.1, 0.9));
  float d2 = seg2(q, float2(0.1, 0.9), float2(-0.38, 0.55));
  return smin(tube(d, p.z), tube(d2, p.z), 0.08);
}
float two(float3 p) {
  float2 q = p.xy;
  float a = arc2(q, float2(0.0, 0.42), 0.46, -0.75, 2.75);
  float2 e = float2(0.0, 0.42) + 0.46*float2(cos(-0.75), sin(-0.75));
  float b = seg2(q, e, float2(-0.5, -0.92));
  float c = seg2(q, float2(-0.5, -0.92), float2(0.55, -0.92));
  return smin(smin(tube(a, p.z), tube(b, p.z), 0.1), tube(c, p.z), 0.1);
}
float three(float3 p) {
  float2 q = p.xy;
  float a = arc2(q, float2(-0.02, 0.48), 0.42, -ChromeStepNumeralsPI*0.5, 2.6);
  float b = arc2(q, float2(0.0, -0.42), 0.5, -2.6, ChromeStepNumeralsPI*0.5);
  return smin(tube(a, p.z), tube(b, p.z), 0.1);
}
float numeral(float3 p, float k) {
  if (k < 0.5) return one(p);
  if (k < 1.5) return two(p);
  return three(p);
}

float3 toNum(float3 p) {
  p.y -= uStep.w;
  p = rz(p, uStep.z);
  p = ry(p, -uStep.y);
  return p;
}

float sd(float3 p) {
  float3 q = toNum(p);
  float r = clamp(uStep.x, 0.0, 2.0);
  float a = min(floor(r), 1.0);
  float w = smoothstep(0.1, 0.9, r - a);
  if (w < 0.001) return numeral(q, a)*0.9;
  if (w > 0.999) return numeral(q, a + 1.0)*0.9;
  return (mix(numeral(q, a), numeral(q, a + 1.0), w) - 0.16*sin(ChromeStepNumeralsPI*w))*0.9;
}

// the warm studio the chrome reflects
float3 env(float3 r) {
  r = ry(r, uLight.x);
  float y = r.y;
  float az = atan2(r.x, -r.z);
  float3 sky = mix(float3(0.42, 0.42, 0.44), float3(0.92, 0.92, 0.94), smoothstep(0.0, 0.8, y));
  sky += 1.6*smoothstep(0.78, 0.82, y);
  sky += 1.4*smoothstep(0.16, 0.11, abs(az - 1.9))*smoothstep(0.05, 0.1, y)*smoothstep(0.7, 0.6, y);
  sky += 0.9*smoothstep(0.1, 0.07, abs(az + 1.3))*smoothstep(0.08, 0.12, y)*smoothstep(0.55, 0.48, y);
  float3 ground = mix(float3(0.06, 0.055, 0.05), float3(0.2, 0.19, 0.18), smoothstep(-0.4, 0.0, y));
  float3 c = mix(ground, sky, smoothstep(-0.008, 0.008, y));
  // an amber card in front, lighting the faces with the warm reflections of the reference
  float card = smoothstep(1.2, 0.85, abs(az))*smoothstep(-0.65, -0.45, y)*smoothstep(-0.05, -0.15, y);
  c = mix(c, float3(1.0, 0.6, 0.2)*1.15, card*0.95);
  return c;
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 render(float2 xy) {
  float S = uView.z;
  float2 uv = (xy - uView.xy)/S;
  uv.y = -uv.y;
  // the floor shadow under the balloon
  float2 sh = (xy - float2(uView.x, uLight.y))/(S*float2(0.75, 0.12));
  float shadow = 0.28*exp(-dot(sh, sh))/(1.0 + max(uStep.w, 0.0)*2.0);
  float b2 = 1.55*1.55 - dot(uv*float2(1.0, 0.75), uv*float2(1.0, 0.75));
  if (b2 <= 0.0) return half4(0.0, 0.0, 0.0, half(shadow));
  float pxo = 1.0/(uView.w*S);
  float z = 1.0, best = 1e4, bestZ = z;
  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float d = sd(float3(uv, z));
    if (d < best) { best = d; bestZ = z; }
    if (d < 0.3*pxo) { hit = true; break; }
    z -= max(d*0.9, 0.35*pxo);
    if (z < -1.0) break;
  }
  float cover = hit ? 1.0 : 1.0 - smoothstep(0.3*pxo, 1.3*pxo, best);
  if (cover <= 0.0) return half4(0.0, 0.0, 0.0, half(shadow));
  float3 p = float3(uv, bestZ);
  if (hit) { for (int i = 0; i < 2; i++) p.z -= sd(p); }
  float e = max(0.002, pxo);
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sd(p + k.xyy*e) + k.yyx*sd(p + k.yyx*e) + k.yxy*sd(p + k.yxy*e) + k.xxx*sd(p + k.xxx*e));
  float3 rd = float3(0.0, 0.0, -1.0);
  float3 r = reflect(rd, n);
  float lift = 0.008;
  float3 c = float3(env(r + float3(0.0, lift, 0.0)).r, env(r).g, env(r - float3(0.0, lift, 0.0)).b);
  float f = pow(1.0 - clamp(dot(n, -rd), 0.0, 1.0), 5.0);
  c *= mix(float3(0.95, 0.93, 0.9), float3(1.0), f);
  // where the tube folds into itself it darkens
  float ao = clamp(0.4 + 0.6*sd(p + n*0.12)/0.12, 0.0, 1.0);
  c *= 0.55 + 0.45*ao;
  float3 ex = max(c - 0.92, float3(0.0));
  c = min(c, float3(0.92)) + ex/(1.0 + ex/0.08);
  float a = cover;
  float outA = a + shadow*(1.0 - a);
  return half4(half3(toP3(clamp(c, float3(0.0), float3(1.0)))*a), half(outA));
}

};

[[ stitchable ]] half4 ChromeStepNumeralsMain(float2 position, float4 view, float4 step, float4 light) {
  ChromeStepNumeralsShader scene;
  scene.uView = view; scene.uStep = step; scene.uLight = light;
  return scene.render(position);
}
