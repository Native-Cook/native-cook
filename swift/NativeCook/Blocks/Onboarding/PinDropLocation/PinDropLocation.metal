#include <metal_stdlib>
using namespace metal;
constant float pinDropLocationTAU=6.2831853f;
constant float pinDropLocationPI=3.14159265f;
constant float2 pinDropLocationTIP=float2(0,-0.49f);

// Same 64-byte FrameU layout and premultiplied sRGB output as the WebGPU reference.
struct PinDropLocationUniforms { float4 view; float4 disc; float4 spin; float4 pin; };
struct PinDropLocationSpark { float4 at; };
struct PinDropLocationShader {
  PinDropLocationUniforms F;


float pinDropLocationSq(float v) { return v * v; }
float3 pinDropLocationLin(float3 c) { return pow(c, float3(2.2f)); }
float pinDropLocationHash1(float n) { return fract(sin(n * 91.3458f) * 47453.5453f); }
float pinDropLocationHash2(float2 p) { return fract(sin(dot(p, float2(127.1f, 311.7f))) * 43758.5453f); }
float pinDropLocationNoise2(float2 x) {
  auto i = floor(x); auto f = fract(x); auto u = f * f * (3.0f - 2.0f * f);
  return mix(mix(pinDropLocationHash2(i), pinDropLocationHash2(i + float2(1.0f, 0.0f)), u.x), mix(pinDropLocationHash2(i + float2(0.0f, 1.0f)), pinDropLocationHash2(i + float2(1.0f, 1.0f)), u.x), u.y);
}
float pinDropLocationFbm(float2 p) { return 0.55f * pinDropLocationNoise2(p) + 0.28f * pinDropLocationNoise2(p * 2.1f + 5.3f) + 0.17f * pinDropLocationNoise2(p * 4.3f + 1.7f); }

// the pin's tip, in disc units (y up), and where it stands on the map


/* ------------------------------------------------------------------ the map (uv in radians, y up) */

float3 pinDropLocationMapAt(float2 uv, float2 pinUv) {
  // land and sea; at home the land lies right of the main road, as in the reference
  auto home = exp(-dot(uv, uv) * 1.1f);
  auto field = (pinDropLocationFbm(uv * 1.25f + float2(3.7f, 1.1f)) - 0.47f) * (1.0f - 0.85f * home) + clamp((uv.x - uv.y + 0.03f) * 4.0f, -0.9f, 0.9f) * home;
  auto land = smoothstep(-0.02f, 0.02f, field);
  auto c = mix(pinDropLocationLin(float3(0.16f, 0.13f, 0.27f)), pinDropLocationLin(float3(0.29f, 0.27f, 0.57f)), land);
  // lighter towards the land's lower right, like the light falling on it
  c = mix(c, pinDropLocationLin(float3(0.37f, 0.35f, 0.68f)), land * smoothstep(-0.2f, 0.9f, uv.x - uv.y) * 0.6f);
  // streets on the land: two faint grids at an angle, broken into blocks
  auto g1 = float2(dot(uv, float2(0.87f, 0.5f)), dot(uv, float2(-0.5f, 0.87f))) * 9.0f;
  auto gl = abs(fract(g1) - 0.5f);
  auto blockOn = step(0.35f, pinDropLocationHash2(floor(g1)));
  auto streets = (1.0f - smoothstep(0.015f, 0.04f, min(gl.x, gl.y))) * land * blockOn * 0.1f * (1.0f - home * 0.95f);
  c += pinDropLocationLin(float3(0.6f, 0.55f, 0.9f)) * streets;
  // the main road along the coast, bright
  // at home it runs parallel to the coast, a little way in, as in the reference; elsewhere along the coast
  auto homeRoad = abs(uv.x - uv.y - 0.36f - 0.07f * sin((uv.x + uv.y) * 2.4f)) * 0.7071f;
  auto coastRoad = abs(field + 0.02f) * 0.6f;
  auto road = mix(coastRoad, homeRoad, smoothstep(0.08f, 0.28f, home));
  c = mix(c, pinDropLocationLin(float3(0.79f, 0.76f, 0.95f)), (1.0f - smoothstep(0.012f, 0.026f, road)) * 0.95f);
  // the city's lights, coming on in a ring that runs out from the pin
  auto cell = floor(uv * 26.0f);
  auto h = pinDropLocationHash2(cell);
  if (h > 0.8f && land > 0.5f) {
    auto pp = (cell + 0.5f + (float2(pinDropLocationHash2(cell + 3.1f), pinDropLocationHash2(cell + 7.7f)) - 0.5f) * 0.6f) / 26.0f;
    auto dd = length(uv - pp) * 26.0f;
    auto since = F.pin.z;
    auto on = 0.0f;
    if (since >= 0.0f) { on = F.pin.w * smoothstep(0.0f, 0.15f, since * 0.8f - length(pp - pinUv)); }
    auto tw = 0.75f + 0.25f * sin(F.view.w * pinDropLocationTAU * (0.2f + 0.2f * h) + h * 40.0f);
    c += pinDropLocationLin(float3(1.0f, 0.72f, 0.85f)) * exp(-dd * dd * 18.0f) * on * tw * 1.3f;
  }
  return c;
}

/* ------------------------------------------------------------------ the pin (units of its own height, y up, tip at 0) */

// the classic map pin, revolved about its axis and pressed thin front to back: a head of radius 0.36f
// centred 0.62f above the tip, its sides tangent to the head, and a hole through it
float pinDropLocationSdTear(float2 q) {
  auto cy = 0.62f; auto r = 0.36f;
  auto sa = r / cy; auto ca = sqrt(1.0f - sa * sa);
  auto len = sqrt(cy * cy - r * r);
  auto along = dot(q, float2(sa, ca));
  if (along < 0.0f) { return length(q); }
  if (along < len) { return dot(q, float2(ca, -sa)); }
  return length(q - float2(0.0f, cy)) - r;
}
float pinDropLocationSdPin(float3 p0) {
  auto p = float3(p0.x, p0.y, p0.z * 1.9f);
  auto body = pinDropLocationSdTear(float2(length(p.xz), p.y)) / 1.9f;
  auto hole = length(p0.xy - float2(0.0f, 0.62f)) - 0.135f;
  return max(body, -hole);
}
float3 pinDropLocationPinNormal(float3 p) {
  auto e = 0.002f;
  auto k1 = float3(1.0f, -1.0f, -1.0f); auto k2 = float3(-1.0f, -1.0f, 1.0f); auto k3 = float3(-1.0f, 1.0f, -1.0f); auto k4 = float3(1.0f, 1.0f, 1.0f);
  return normalize(k1 * pinDropLocationSdPin(p + k1 * e) + k2 * pinDropLocationSdPin(p + k2 * e) + k3 * pinDropLocationSdPin(p + k3 * e) + k4 * pinDropLocationSdPin(p + k4 * e));
}

struct PinDropLocationPx { float3 col; float a; };
PinDropLocationPx pinDropLocationOver(PinDropLocationPx base, float3 c, float a) {
  PinDropLocationPx o;
  o.col = c * a + base.col * (1.0f - a);
  o.a = a + base.a * (1.0f - a);
  return o;
}

float3 pinDropLocationPinScreen() {
  // the pin's tip on the screen (pt) and its height (pt)
  auto R = F.disc.z;
  auto tip = F.disc.xy + float2(pinDropLocationTIP.x, -pinDropLocationTIP.y) * R;
  auto drop = (1.0f - F.pin.x) * (tip.y + 120.0f) + F.pin.y * 16.0f * F.pin.x;
  return float3(tip.x, tip.y - drop, R * 1.23f);
}

PinDropLocationPx pinDropLocationShade(float2 s) {
  PinDropLocationPx o;
  o.col = float3(0.0f);
  o.a = 0.0f;
  auto R = F.disc.z;
  auto bend = F.disc.w;
  auto q = (s - F.disc.xy) / (R * (1.0f + 0.08f * bend));
  auto r = length(q);
  // the glow behind the disc
  // an atmosphere round the globe as it bends
  auto glow = exp(-pinDropLocationSq(max(r - 1.0f, 0.0f) / 0.12f)) * 0.55f * bend * step(1.0f, r);
  o = pinDropLocationOver(o, pinDropLocationLin(float3(0.45f, 0.36f, 0.95f)), glow);
  // the map in its disc, flat or wrapped on the globe
  auto zoomK = 1.0f / (1.0f + 0.7f * F.spin.z);
  if (r < 1.0f) {
    auto z = sqrt(max(1.0f - r * r, 0.0f));
    auto ys = float3(q.x, -q.y, z);
    // turn the sphere by the latitude, then read its longitude
    auto cl = cos(F.spin.y); auto sl = sin(F.spin.y);
    auto pr = float3(ys.x, cl * ys.y + sl * ys.z, -sl * ys.y + cl * ys.z);
    auto sphereUv = float2(atan2(pr.x, pr.z) + F.spin.x, asin(clamp(pr.y, -1.0f, 1.0f)));
    auto flatUv = float2(q.x, -q.y) + float2(F.spin.x, F.spin.y);
    auto tipUv = pinDropLocationTIP + float2(F.spin.x, F.spin.y);
    auto uv = mix(flatUv, sphereUv, bend);
    uv = tipUv + (uv - tipUv) * zoomK;
    auto c = pinDropLocationMapAt(uv, tipUv);
    // flat it is lit evenly; round it turns from the light and glows at its rim
    auto n = normalize(mix(float3(0.0f, 0.0f, 1.0f), ys, bend));
    auto L = normalize(float3(-0.5f, 0.6f, 0.65f));
    c *= mix(1.0f, 0.35f + 0.85f * max(dot(n, L), 0.0f), bend);
    c += pinDropLocationLin(float3(0.55f, 0.45f, 1.0f)) * pow(1.0f - z, 3.0f) * 0.9f * bend;
    c += float3(1.0f) * pow(max(dot(reflect(-L, n), float3(0.0f, 0.0f, 1.0f)), 0.0f), 60.0f) * 0.0f * bend;
    // rings running out from where the pin landed
    auto since = F.pin.z;
    if (since >= 0.0f && since < 1.6f) {
      auto tip = F.disc.xy + float2(pinDropLocationTIP.x, -pinDropLocationTIP.y) * R;
      auto dr = length((s - tip) * float2(1.0f, 1.6f));
      for (auto k = 0; k < 2; k++) {
        auto tt = since - float(k) * 0.22f;
        if (tt > 0.0f) {
          auto front = tt * 120.0f;
          c += pinDropLocationLin(float3(1.0f, 0.55f, 0.75f)) * exp(-pinDropLocationSq((dr - front) / (2.0f + 6.0f * tt))) * (1.0f - tt / 1.6f) * 0.8f;
        }
      }
    }
    // the places that rise round the pin once allowed
    if (F.spin.w > 0.0f) {
      for (auto k = 0; k < 3; k++) {
        const float2 positions[3] = {float2(-0.58f, -0.3f), float2(0.6f, 0.25f), float2(0.35f, -0.72f)};
        auto pl = positions[k];
        auto ps = F.disc.xy + float2(pl.x, -pl.y) * R;
        auto up = clamp(F.spin.w * 1.6f - float(k) * 0.25f, 0.0f, 1.0f);
        auto pd = length(s - ps - float2(0.0f, -10.0f * up));
        c = mix(c, pinDropLocationLin(float3(0.98f, 0.95f, 1.0f)), (1.0f - smoothstep(4.5f, 5.5f, pd)) * up);
        c += pinDropLocationLin(float3(0.75f, 0.6f, 1.0f)) * exp(-pd * pd / 120.0f) * up * 0.8f;
        c = mix(c, c * 0.7f, (1.0f - smoothstep(3.0f, 6.0f, length((s - ps) * float2(1.0f, 2.5f))) * up));
      }
    }
    auto a = 1.0f - smoothstep(0.985f, 1.0f, r);
    o = pinDropLocationOver(o, c, a);
  }
  // the pin: its soft shadow on the map, then the pin itself
  auto ps = pinDropLocationPinScreen();
  auto H = ps.z;
  auto tipRest = F.disc.xy + float2(pinDropLocationTIP.x, -pinDropLocationTIP.y) * R;
  auto height = max(tipRest.y - ps.y, 0.0f);
  auto shd = length((s - tipRest - float2(height * 0.15f, 0.0f)) / float2(18.0f + height * 0.12f, 6.0f + height * 0.04f));
  auto sh = 0.5f * exp(-shd * shd * 1.5f) / (1.0f + height / 90.0f) * step(r, 1.0f);
  o = pinDropLocationOver(o, float3(0.0f), sh * o.a);
  auto since = F.pin.z;
  auto sq2 = 0.0f;
  if (since >= 0.0f) { sq2 = 0.13f * exp(-since * 9.0f) * cos(since * 26.0f) * smoothstep(0.0f, 0.03f, since) * (since < 0.5f ? 1.0f : 0.0f); }
  auto local = float2((s.x - ps.x) / (H * (1.0f + sq2)), (ps.y - s.y) / (H * (1.0f - sq2)));
  if (local.x > -0.45f && local.x < 0.45f && local.y > -0.05f && local.y < 1.05f) {
    auto ro = float3(local, 3.0f);
    auto rd = float3(0.0f, 0.0f, -1.0f);
    auto t = 2.0f;
    auto hit = false;
    for (auto i = 0; i < 80; i++) {
      auto dd = pinDropLocationSdPin(ro + rd * t);
      if (dd < 0.0008f) { hit = true; break; }
      t += dd * 0.8f;
      if (t > 4.0f) { break; }
    }
    if (hit) {
      auto p = ro + rd * t;
      auto n = pinDropLocationPinNormal(p);
      auto L = normalize(float3(-0.5f, 0.6f, 0.65f));
      auto base = mix(pinDropLocationLin(float3(1.0f, 0.24f, 0.37f)), pinDropLocationLin(float3(1.0f, 0.42f, 0.6f)), smoothstep(0.1f, 0.95f, p.y));
      auto wrap = clamp((dot(n, L) + 0.3f) / 1.3f, 0.0f, 1.0f);
      auto c = base * (0.45f + 0.6f * wrap);
      // glassy: a bright rim and a hot highlight
      c += pinDropLocationLin(float3(1.0f, 0.7f, 0.8f)) * pow(1.0f - max(n.z, 0.0f), 2.5f) * 0.5f;
      c += float3(1.0f) * pow(max(dot(reflect(-L, n), float3(0.0f, 0.0f, 1.0f)), 0.0f), 50.0f) * 0.6f;
      o = pinDropLocationOver(o, c, 1.0f);
    }
  }
  return o;
}


};
struct PinDropLocationVertex { float4 pos [[position]]; float2 uv; };
vertex PinDropLocationVertex pinDropLocationFullVs(uint vi [[vertex_id]]) {
  float2 uv = float2(float((vi << 1u) & 2u), float(vi & 2u));
  return {float4(uv * float2(2, -2) + float2(-1, 1), 0, 1), uv};
}
float3 pinDropLocationEncode(float3 c) {
  return pow(clamp(c / (float3(1) + max(c - float3(0.85f), float3(0)) * 0.6f), float3(0), float3(1)), float3(1.0f / 2.2f));
}
fragment float4 pinDropLocationMapFs(PinDropLocationVertex v [[stage_in]], constant PinDropLocationUniforms &F [[buffer(0)]]) {
  PinDropLocationShader shader {F};
  auto px = shader.pinDropLocationShade(v.pos.xy / F.view.z);
  if (px.a <= 0) return float4(0);
  return float4(pinDropLocationEncode(px.col / px.a) * px.a, px.a);
}
kernel void pinDropLocationSparksCs(constant PinDropLocationUniforms &F [[buffer(0)]], device PinDropLocationSpark *sparksOut [[buffer(1)]], uint i [[thread_position_in_grid]]) {
  if (i >= 240u) return;
  PinDropLocationShader shader {F};
  float fi = float(i), since = F.pin.z;
  float life = 0.35f + 0.3f * shader.pinDropLocationHash1(fi + 1.0f);
  if (since < 0 || since > life) { sparksOut[i].at = float4(0); return; }
  float R = F.disc.z;
  float2 tip = F.disc.xy + float2(0, 0.49f) * R;
  float a = shader.pinDropLocationHash1(fi + 2.0f) * 6.2831853f;
  float speed = 120.0f + 260.0f * shader.pinDropLocationHash1(fi + 3.0f);
  float k = 1.0f - exp(-since * 6.0f);
  float2 p = tip + float2(cos(a), sin(a) * 0.5f) * speed * k / 6.0f + float2(0, -26.0f * k * shader.pinDropLocationHash1(fi + 4.0f));
  float x = since / life;
  sparksOut[i].at = float4(p, 0.7f + 0.9f * shader.pinDropLocationHash1(fi + 5.0f), (1.0f-x)*(1.0f-x)*smoothstep(0.0f, 0.04f, since));
}
struct PinDropLocationSparkVertex { float4 pos [[position]]; float2 uv; float a; };
vertex PinDropLocationSparkVertex pinDropLocationSparkVs(uint vi [[vertex_id]], uint ii [[instance_id]], constant PinDropLocationUniforms &F [[buffer(0)]], const device PinDropLocationSpark *sparks [[buffer(1)]]) {
  auto s = sparks[ii];
  float2 corner = float2(-1, -1);
  if (vi == 1u || vi == 4u) corner = float2(1, -1);
  if (vi == 2u || vi == 3u) corner = float2(-1, 1);
  if (vi == 5u) corner = float2(1, 1);
  float r = s.at.z * 2.5f;
  if (s.at.w <= 0.001f) r = 0;
  float2 at = s.at.xy + corner * r;
  return {float4(at.x / F.view.x * 2.0f - 1.0f, 1.0f - at.y / F.view.y * 2.0f, 0, 1), corner, s.at.w};
}
fragment float4 pinDropLocationSparkFs(PinDropLocationSparkVertex v [[stage_in]]) {
  float a = clamp(exp(-dot(v.uv,v.uv)*7.0f)*v.a, 0.0f, 1.0f);
  return float4(float3(1.0f, 0.8f, 0.9f)*a, a);
}
