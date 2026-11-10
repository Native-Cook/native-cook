#include <metal_stdlib>
using namespace metal;
// FrameU and Puff have the same 80/32 byte layouts as the TypeGPU schemas.
struct SmokedClocheRevealUniforms { float4 view; float4 bowl; float4 pose; float4 finger; float4 aux; };
struct SmokedClocheRevealPuff { float4 at; float4 st; };
struct SmokedClocheRevealSmokeOut { float4 pos [[position]]; float2 uv; float a; };
struct SmokedClocheRevealFullOut { float4 pos [[position]]; float2 uv; };
constant float smokedClocheRevealTau = 6.2831853;
float smokedClocheRevealSq(float v) { return v * v; }
float3 smokedClocheRevealLin(float3 c) { return pow(c, float3(2.2)); }
float smokedClocheRevealHash1(float n) { return fract(sin(n * 91.3458) * 47453.5453); }
float smokedClocheRevealHash2(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float smokedClocheRevealNoise2(float2 x) {
  const auto i = floor(x); const auto f = fract(x); const auto u = f * f * (3.0 - 2.0 * f);
  return mix(mix(smokedClocheRevealHash2(i), smokedClocheRevealHash2(i + float2(1.0, 0.0)), u.x), mix(smokedClocheRevealHash2(i + float2(0.0, 1.0)), smokedClocheRevealHash2(i + float2(1.0, 1.0)), u.x), u.y);
}
float2 smokedClocheRevealCurl(float2 p, float t) {
  const auto e = 0.5;
  const auto q = p + float2(t * 0.6, -t * 0.4);
  const auto n1 = smokedClocheRevealNoise2(q + float2(0.0, e)) - smokedClocheRevealNoise2(q - float2(0.0, e));
  const auto n2 = smokedClocheRevealNoise2(q + float2(e, 0.0)) - smokedClocheRevealNoise2(q - float2(e, 0.0));
  const auto q2 = p * 2.1 + float2(-t * 0.9, t * 0.7) + 17.0;
  const auto m1 = smokedClocheRevealNoise2(q2 + float2(0.0, e)) - smokedClocheRevealNoise2(q2 - float2(0.0, e));
  const auto m2 = smokedClocheRevealNoise2(q2 + float2(e, 0.0)) - smokedClocheRevealNoise2(q2 - float2(e, 0.0));
  return (float2(n1, -n2) + 0.5 * float2(m1, -m2)) / (2.0 * e);
}
kernel void smokedClocheRevealSmokeCs(uint3 gid [[thread_position_in_grid]], constant SmokedClocheRevealUniforms &F [[buffer(0)]], device SmokedClocheRevealPuff *puffsOut [[buffer(1)]]) {
  const auto i = gid.x;
  if (i >= 3072u) { return; }
  const auto fi = float(i);
  auto p = puffsOut[i];
  const auto c = F.bowl.xy;
  const auto R = F.bowl.w;
  const auto dt = min(F.pose.w, 1.0 / 30.0);
  const auto mode = F.pose.z;
  if (mode > 2.5 || p.st.z == 0.0) {
    // born inside the cloche, fading in
    const auto a = smokedClocheRevealHash1(fi + 1.0) * smokedClocheRevealTau;
    const auto r = sqrt(smokedClocheRevealHash1(fi + 2.0)) * R * 0.88;
    p.at = float4(c + float2(cos(a), sin(a)) * r, 0.0, 0.0);
    p.st = float4(0.0, 14.0 + 16.0 * smokedClocheRevealHash1(fi + 3.0), 0.001 + smokedClocheRevealHash1(fi + 4.0), 0.0);
  }
  p.st.w += dt;
  auto pos = p.at.xy;
  auto vel = p.at.zw;
  // the swirl, and a slow turn about the cloche's middle
  const auto rel = pos - c;
  const auto flow = smokedClocheRevealCurl(pos * 0.012, F.view.w * 0.25 + p.st.z * 3.0) * 26.0 + float2(-rel.y, rel.x) / max(R, 1.0) * 14.0;
  vel += (flow - vel) * min(1.0, dt * 2.5);
  if (mode < 0.5 || mode > 1.5) {
    // held by the rim
    const auto rl = length(rel);
    if (rl > R * 0.9) { vel -= rel / max(rl, 1.0) * (rl - R * 0.9) * 12.0 * dt * 10.0; }
    p.st.x = min(1.0, p.st.x + dt / 0.9);
  } else {
    // the rim lifts: out from under it, swelling and thinning as the smoke rises to the eye
    const auto lift = F.pose.x;
    const auto rl = max(length(rel), 1.0);
    vel += rel / rl * (40.0 + 200.0 * lift) * smoothstep(0.45, 1.0, rl / R) * dt * 3.0;
    p.st.y += dt * (6.0 + 12.0 * lift);
    // thinning as it rises, and fast once it is out past the rim
    p.st.x = max(0.0, p.st.x - dt * ((0.14 + 0.22 * lift) * (0.6 + 0.8 * p.st.z) + 2.2 * smoothstep(R, 1.35 * R, rl)));
  }
  // the finger drags it along and curls it round itself
  if (F.aux.x > 0.5) {
    const auto fd = pos - F.finger.xy;
    const auto fl = length(fd);
    if (fl < 60.0) {
      const auto k = 1.0 - fl / 60.0;
      vel += F.finger.zw / max(dt, 0.004) * 0.6 * k * k * dt * 8.0;
      vel += float2(-fd.y, fd.x) / max(fl, 1.0) * 60.0 * k;
    }
  }
  pos += vel * dt;
  p.at = float4(pos, vel);
  puffsOut[i] = p;
}
vertex SmokedClocheRevealSmokeOut smokedClocheRevealSmokeVs(uint vi [[vertex_id]], uint ii [[instance_id]], constant SmokedClocheRevealUniforms &F [[buffer(0)]], device const SmokedClocheRevealPuff *puffs [[buffer(1)]]) {
  const auto p = puffs[ii];
  auto corner = float2(-1.0, -1.0);
  if (vi == 1u || vi == 4u) { corner = float2(1.0, -1.0); }
  if (vi == 2u || vi == 3u) { corner = float2(-1.0, 1.0); }
  if (vi == 5u) { corner = float2(1.0, 1.0); }
  auto r = p.st.y;
  if (p.st.x <= 0.002) { r = 0.0; }
  const auto at = p.at.xy + corner * r;
  SmokedClocheRevealSmokeOut o;
  o.pos = float4(at.x / F.view.x * 2.0 - 1.0, 1.0 - at.y / F.view.y * 2.0, 0.0, 1.0);
  o.uv = corner;
  o.a = p.st.x;
  return o;
}
fragment float4 smokedClocheRevealSmokeFs(SmokedClocheRevealSmokeOut v [[stage_in]]) {
  const auto r2 = dot(v.uv, v.uv);
  const auto a = exp(-r2 * 3.2) * (1.0 - smoothstep(0.8, 1.0, r2)) * v.a * 0.05;
  const auto c = float3(0.95, 0.93, 0.9);
  return float4(pow(c, float3(1.0 / 2.2)) * a, a);
}
vertex SmokedClocheRevealFullOut smokedClocheRevealFullVs(uint vi [[vertex_id]]) {
  const auto uv = float2(float((vi << 1u) & 2u), float(vi & 2u));
  SmokedClocheRevealFullOut o;
  o.pos = float4(uv * float2(2.0, -2.0) + float2(-1.0, 1.0), 0.0, 1.0);
  o.uv = uv;
  return o;
}
struct SmokedClocheRevealShader {
 constant SmokedClocheRevealUniforms &F; texture2d<float> dish; sampler samp;
float4 smokedClocheRevealDishAt(float2 s) {
  const auto uv = (s - F.bowl.xy) / (2.0 * F.bowl.z) + 0.5;
  if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) { return float4(0.0); }
  return dish.sample(samp, uv, level(0.0));
}
float3 smokedClocheRevealStudio(float3 r) {
  auto c = mix(smokedClocheRevealLin(float3(0.55, 0.52, 0.5)), smokedClocheRevealLin(float3(0.95, 0.94, 0.93)), smoothstep(-0.4, 0.8, r.y));
  const auto k = normalize(float3(-0.5, 0.62, 0.6));
  const auto u = normalize(cross(k, float3(0.0, 0.0, 1.0)));
  const auto w = cross(u, k);
  const auto a = float2(dot(r, u), dot(r, w)) / max(dot(r, k), 0.05);
  c += float3(1.0, 0.99, 0.97) * 3.0 * (1.0 - smoothstep(0.16, 0.22, abs(a.x))) * (1.0 - smoothstep(0.3, 0.36, abs(a.y))) * step(0.0, dot(r, k)) * smoothstep(0.012, 0.03, abs(a.x));
  const auto f = normalize(float3(0.7, -0.35, 0.6));
  c += float3(1.0, 0.92, 0.82) * 0.7 * smoothstep(0.9, 0.98, dot(r, f));
  return c;
}
float3 smokedClocheRevealEncode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }
float4 smokedClocheRevealClocheFs(SmokedClocheRevealFullOut v) {
  const auto s = v.pos.xy / F.view.z;
  const auto lift = F.pose.x;
  // RN deliberately disables WebGPU for Reduce Motion. Draw that same quiet
  // presentation in Metal, without installing a fallback view or advancing smoke.
  if (F.aux.y > 0.5) {
    float distance = length(s-F.bowl.xy);
    float aa = 1.0/F.view.z;
    float fill = 0.18*(1.0-smoothstep(F.bowl.w-aa*0.5,F.bowl.w+aa*0.5,distance));
    float stroke = 0.7*(1.0-smoothstep(1.5-aa*0.5,1.5+aa*0.5,abs(distance-F.bowl.w)));
    float alpha = (stroke+fill*(1.0-stroke))*(1.0-lift);
    float4 result = float4(float3(alpha),alpha);
    float radius = F.bowl.w*0.92;
    float smoke = mix(0.7,0.25,clamp(distance/radius,0.0,1.0))*0.6;
    smoke *= F.pose.z > 0.5 && F.pose.z < 1.5 ? 1.0-lift : 1.0;
    smoke *= 1.0-smoothstep(radius-aa*0.5,radius+aa*0.5,distance);
    return float4(float3(240.0,238.0,234.0)/255.0*smoke,smoke)+result*(1.0-smoke);
  }
  const auto fade = 1.0 - smoothstep(0.5, 0.98, lift);
  if (fade <= 0.0) { return float4(0.0); }
  // rising towards the eye: larger, softer
  const auto scale = 1.0 + 0.35 * lift * lift;
  const auto R = F.bowl.w * scale;
  const auto c = F.bowl.xy + float2(0.0, -18.0 * lift);
  const auto q = (s - c) / R;
  const auto r = length(q);
  if (r > 1.02) { return float4(0.0); }
  const auto h = sqrt(max(1.0 - r * r, 0.0));
  const auto n = normalize(float3(q.x, -q.y, h + 0.0001));
  // the dish through the dome: magnified in the middle, pulled in towards the rim, a little hazy
  const auto mag = 1.0 - 0.08 * h - 0.06 * (1.0 - h);
  const auto src = F.bowl.xy + (s - F.bowl.xy) * mag - float2(q.x, q.y) * 10.0 * (1.0 - h) * (1.0 - h);
  // softer as it rises: eight taps on a ring turned per pixel, so the blur has no pattern
  const auto blur = 0.6 + 6.0 * lift;
  const auto rot = fract(sin(dot(s, float2(12.9898, 78.233))) * 43758.5453) * smokedClocheRevealTau;
  auto seen = float4(0.0);
  for (auto k = 0; k < 8; k++) {
    const auto a = rot + float(k) * smokedClocheRevealTau / 8.0;
    seen += smokedClocheRevealDishAt(src + float2(cos(a), sin(a)) * blur * (0.5 + 0.5 * float(k % 2)));
  }
  seen *= 0.125;
  // where the dish's photo is transparent (past the bowl) the dome shows the table, warm grey
  const auto table = float4(pow(float3(0.86, 0.83, 0.8), float3(2.2)), 1.0);
  const auto under = float4(pow(seen.rgb, float3(2.2)), seen.a) * seen.a + table * (1.0 - seen.a);
  const auto cosv = h;
  const auto fr = 0.04 + 0.96 * pow(1.0 - cosv, 5.0);
  const auto refl = smokedClocheRevealStudio(reflect(float3(0.0, 0.0, -1.0), n));
  auto col = under.rgb * (0.94 + 0.06 * h) * (1.0 - fr) + refl * fr;
  // the rim: a thick rolled edge of glass, bright on its inner line
  const auto rim = smoothstep(0.9, 0.97, r) * (1.0 - smoothstep(0.985, 1.02, r));
  col = mix(col, col * 0.82 + refl * 0.25, rim);
  col += float3(1.0) * 0.35 * exp(-smokedClocheRevealSq((r - 0.965) / 0.008));
  // the knob: a small glass ball on top
  const auto kq = (s - c) / (0.11 * R);
  const auto kr = length(kq);
  if (kr < 1.0) {
    const auto kh = sqrt(1.0 - kr * kr);
    const auto kn = normalize(float3(kq.x, -kq.y, kh));
    const auto kf = 0.04 + 0.96 * pow(1.0 - kh, 5.0);
    const auto kref = smokedClocheRevealStudio(reflect(float3(0.0, 0.0, -1.0), kn));
    const auto kunder = smokedClocheRevealDishAt(c + (s - c) * -0.6).rgb;
    col = mix(col, pow(kunder, float3(2.2)) * 0.85 * (1.0 - kf) + kref * kf + float3(1.0) * pow(max(dot(reflect(-normalize(float3(-0.5, 0.62, 0.6)), kn), float3(0.0, 0.0, 1.0)), 0.0), 60.0), 1.0 - smoothstep(0.92, 1.0, kr));
  } else {
    // the knob's soft shadow on the glass
    col *= 1.0 - 0.18 * exp(-smokedClocheRevealSq((kr - 1.0) / 0.5)) * smoothstep(-0.2, 0.6, dot(kq, float2(0.6, 0.8)));
  }
  const auto a = (1.0 - smoothstep(0.995, 1.02, r)) * fade;
  return float4(smokedClocheRevealEncode(col) * a, a);
}
};
fragment float4 smokedClocheRevealClocheFs(SmokedClocheRevealFullOut v [[stage_in]], constant SmokedClocheRevealUniforms &F [[buffer(0)]], texture2d<float> dish [[texture(0)]], sampler samp [[sampler(0)]]) { SmokedClocheRevealShader shader{F,dish,samp}; return shader.smokedClocheRevealClocheFs(v); }
