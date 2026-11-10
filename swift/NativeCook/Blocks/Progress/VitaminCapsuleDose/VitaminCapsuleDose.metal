#include <metal_stdlib>
using namespace metal;
// Direct translation of the TypeGPU/WGSL frame layout and all three passes.
struct VitaminCapsuleDoseUniforms {
 float4 view, hero, shape, rot0, rot1, rot2, fx, goal, open, phys, floor, aux;
};
struct VitaminCapsuleDoseBead { float4 at, tint; };
struct VitaminCapsuleDoseShader {
 constant VitaminCapsuleDoseUniforms &F;
 device VitaminCapsuleDoseBead *beadsOut;
 device float4 *pearls;
 threadgroup float2 *wp;
const float TAU = 6.2831853;
float vitaminCapsuleDoseSq(float v) { return v * v; }
float3 vitaminCapsuleDoseLin(float3 c) { return pow(c, float3(2.2)); }
float vitaminCapsuleDoseHash3(float3 p) { return fract(sin(dot(p, float3(127.1, 311.7, 74.7))) * 43758.5453); }
float vitaminCapsuleDoseHash1(float n) { return fract(sin(n * 91.3458) * 47453.5453); }
float vitaminCapsuleDoseNoise3(float3 x) {
  auto i = floor(x); auto f = fract(x); auto u = f * f * (3.0 - 2.0 * f);
  return mix(mix(mix(vitaminCapsuleDoseHash3(i), vitaminCapsuleDoseHash3(i + float3(1, 0, 0)), u.x), mix(vitaminCapsuleDoseHash3(i + float3(0, 1, 0)), vitaminCapsuleDoseHash3(i + float3(1, 1, 0)), u.x), u.y),
             mix(mix(vitaminCapsuleDoseHash3(i + float3(0, 0, 1)), vitaminCapsuleDoseHash3(i + float3(1, 0, 1)), u.x), mix(vitaminCapsuleDoseHash3(i + float3(0, 1, 1)), vitaminCapsuleDoseHash3(i + float3(1, 1, 1)), u.x), u.y), u.z);
}
float vitaminCapsuleDoseFbm(float3 p) { return 0.6 * vitaminCapsuleDoseNoise3(p) + 0.3 * vitaminCapsuleDoseNoise3(p * 2.03 + 7.1) + 0.1 * vitaminCapsuleDoseNoise3(p * 4.1 + 3.3); }

/* ------------------------------------------------------------------ the three forms (units of U, y up) */

float vitaminCapsuleDoseCapsuleField(float3 p) { return length(p - float3(clamp(p.x, -0.56, 0.56), 0.0, 0.0)) - 0.44; }
// the blue half (x < 0) stays; the white cap is pulled off along the axis and twisted about it; once
// apart, both are shells, so the blue cup shows its inside
float2 vitaminCapsuleDoseSdHalves(float3 p) {
  auto sep = F.open.x;
  auto shell = smoothstep(0.22, 0.4, sep);
  auto db = max(vitaminCapsuleDoseCapsuleField(p), p.x);
  db = mix(db, max(abs(vitaminCapsuleDoseCapsuleField(p)) - 0.035, p.x), shell);
  auto q = p - float3(sep, 0.0, 0.0);
  auto c = cos(F.open.y); auto sn = sin(F.open.y);
  q = float3(q.x, c * q.y - sn * q.z, sn * q.y + c * q.z);
  auto dw = max(vitaminCapsuleDoseCapsuleField(q), -q.x);
  dw = mix(dw, max(abs(vitaminCapsuleDoseCapsuleField(q)) - 0.035, -q.x), shell);
  dw += 0.01 * exp(-vitaminCapsuleDoseSq(q.x / 0.012)) * (1.0 - shell);
  return float2(min(db, dw), select(1.0, 0.0, dw < db));
}
float vitaminCapsuleDoseSdCapsule(float3 p) { return vitaminCapsuleDoseSdHalves(p).x; }
float vitaminCapsuleDoseSdTablet(float3 p) {
  auto q = float2(length(p.xz) - 0.64, abs(p.y) - 0.07);
  auto d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - 0.16;
  d += 0.034 * exp(-vitaminCapsuleDoseSq(p.x / 0.03)) * smoothstep(0.12, 0.2, p.y);   // the score line on top
  return d;
}
float vitaminCapsuleDoseSdRoundCone(float3 p, float3 a, float3 b, float r1, float r2) {
  auto ba = b - a; auto l2 = dot(ba, ba); auto rr = r1 - r2; auto a2 = l2 - rr * rr; auto il2 = 1.0 / l2;
  auto pa = p - a; auto y = dot(pa, ba); auto z = y - l2;
  auto xv = pa * l2 - ba * y; auto x2 = dot(xv, xv);
  auto y2 = y * y * l2; auto z2 = z * z * l2;
  auto k = sign(rr) * rr * rr * x2;
  if (sign(z) * a2 * z2 > k) { return sqrt(x2 + z2) * il2 - r2; }
  if (sign(y) * a2 * y2 < k) { return sqrt(x2 + y2) * il2 - r1; }
  return (sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;
}
float vitaminCapsuleDoseSdDrop(float3 p) {
  auto w = 0.012 * sin(p.y * 7.0 + F.view.w * 3.0) * (1.0 - F.shape.w * (1.0 - F.shape.w) * 0.0);
  return vitaminCapsuleDoseSdRoundCone(p, float3(0.0, -0.22, 0.0), float3(0.0, 0.72, 0.0), 0.6, 0.05) + w;
}
// a form's own vitaminCapsuleDosePose inside the morph: the capsule tilted 40°, the tablet tipped to show its face
float3 vitaminCapsuleDosePose(float3 p, float s) {
  auto q = p;
  if (s < 0.5) {
    auto c = cos(-F.open.w); auto sn = sin(-F.open.w);
    q = float3(c * p.x - sn * p.y, sn * p.x + c * p.y, p.z);
  } else if (s < 1.5) {
    auto c = cos(-1.05); auto sn = sin(-1.05);
    q = float3(p.x, c * p.y - sn * p.z, sn * p.y + c * p.z);
    auto c2 = cos(0.35); auto s2 = sin(0.35);
    q = float3(c2 * q.x - s2 * q.z, q.y, s2 * q.x + c2 * q.z);
  }
  return q;
}
float vitaminCapsuleDoseSdForm(float3 p, float s) {
  auto q = vitaminCapsuleDosePose(p, s);
  if (s < 0.5) { return vitaminCapsuleDoseSdCapsule(q); }
  if (s < 1.5) { return vitaminCapsuleDoseSdTablet(q); }
  return vitaminCapsuleDoseSdDrop(q);
}
float vitaminCapsuleDoseSdObj(float3 p) {
  auto sc = max(F.fx.y, 0.02);
  auto q = p / sc;
  auto k = F.shape.w;
  // mid-morph it swells a little, like something soft changing its mind
  auto d = mix(vitaminCapsuleDoseSdForm(q, F.shape.y), vitaminCapsuleDoseSdForm(q, F.shape.z), k) - 0.05 * sin(k * 3.14159);
  return d * sc;
}
float3 vitaminCapsuleDoseNormalAt(float3 p) {
  auto e = 0.0015;
  auto k1 = float3(1.0, -1.0, -1.0); auto k2 = float3(-1.0, -1.0, 1.0); auto k3 = float3(-1.0, 1.0, -1.0); auto k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * vitaminCapsuleDoseSdObj(p + k1 * e) + k2 * vitaminCapsuleDoseSdObj(p + k2 * e) + k3 * vitaminCapsuleDoseSdObj(p + k3 * e) + k4 * vitaminCapsuleDoseSdObj(p + k4 * e));
}
// what each form is made of at p: (blue glass, clear liquid) weights; the rest is white
float2 vitaminCapsuleDoseMaterial(float3 p) {
  auto sc = max(F.fx.y, 0.02);
  auto q = p / sc;
  auto w = float2(0.0);
  for (auto i = 0; i < 2; i++) {
    auto s = select(F.shape.y, F.shape.z, i == 1);
    auto wt = select(1.0 - F.shape.w, F.shape.w, i == 1);
    auto m = float2(0.0);
    if (s < 0.5) { m = float2(vitaminCapsuleDoseSdHalves(vitaminCapsuleDosePose(q, 0.0)).y, 0.0); }
    else if (s > 1.5) { m = float2(0.0, 1.0); }
    w += m * wt;
  }
  return w;
}

/* ------------------------------------------------------------------ the world round it (view space, y up) */

float2 vitaminCapsuleDoseToScreen(float2 v) { return F.hero.xy + float2(v.x, -v.y) * F.hero.z; }
float2 vitaminCapsuleDoseToView(float2 s) { return float2(s.x - F.hero.x, F.hero.y - s.y) / F.hero.z; }

// the white sheet vitaminCapsuleDoseBehind the object, and the soft shadow it casts on it
float vitaminCapsuleDoseGroundShadow(float2 s) {
  auto c = F.hero.xy + float2(4.0, F.hero.w);
  auto q = (s - c) / (float2(0.95, 0.075) * F.hero.z * max(F.fx.y, 0.02));
  return 0.42 * exp(-dot(q, q) * 1.6) * (1.0 - F.fx.x);
}
float3 vitaminCapsuleDoseBehind(float2 s) {
  return mix(float3(1.0), vitaminCapsuleDoseLin(float3(0.66, 0.79, 0.86)), vitaminCapsuleDoseGroundShadow(s));
}
// a bright vitaminCapsuleDoseStudio: a big soft light above and to the left, the blue room round it
float3 vitaminCapsuleDoseStudio(float3 r) {
  auto c = mix(vitaminCapsuleDoseLin(float3(0.5, 0.66, 0.92)), vitaminCapsuleDoseLin(float3(0.92, 0.95, 1.0)), smoothstep(-0.6, 0.8, r.y));
  auto k = normalize(float3(-0.5, 0.75, 0.45));
  c += float3(1.0, 0.99, 0.97) * 5.0 * smoothstep(0.9, 0.97, dot(r, k));
  auto f = normalize(float3(0.8, 0.1, 0.6));
  c += float3(0.9, 0.95, 1.0) * 1.2 * smoothstep(0.88, 0.98, dot(r, f));
  // the disc below bounces blue
  c = mix(c, vitaminCapsuleDoseLin(float3(0.36, 0.58, 0.96)), smoothstep(-0.2, -0.8, r.y) * 0.6);
  return c;
}

struct VitaminCapsuleDosePx { float3 col; float a; };

VitaminCapsuleDosePx vitaminCapsuleDoseShade(float2 sxy) {
  VitaminCapsuleDosePx o;
  o.col = float3(0.0);
  o.a = 0.0;
  auto v = vitaminCapsuleDoseToView(sxy);
  if (dot(v, v) > 2.4 * 2.4) { return o; }
  auto M = float3x3(F.rot0.xyz, F.rot1.xyz, F.rot2.xyz);   // object → view
  auto Mi = transpose(M);
  // a near-orthographic camera in front of the screen
  auto ro = Mi * float3(0.0, 0.0, 12.0);
  auto rd = Mi * normalize(float3(v, -12.0));
  auto t = 10.2;
  auto hit = false;
  for (auto i = 0; i < 80; i++) {
    auto dd = vitaminCapsuleDoseSdObj(ro + rd * t);
    if (dd < 0.0008) { hit = true; break; }
    t += dd * 0.85;
    if (t > 14.0) { break; }
  }
  // the soft shadow on the sheet under the object
  auto sh = vitaminCapsuleDoseGroundShadow(sxy);
  o.col = vitaminCapsuleDoseLin(float3(0.66, 0.79, 0.86)) * sh;
  o.a = sh;
  if (!hit) { return o; }
  auto p = ro + rd * t;
  // the dissolve: a noise field on the surface, burnt away from its lowest values
  auto nz = vitaminCapsuleDoseFbm(p * 3.2);
  auto edge = F.fx.x * 1.6 - 0.15;
  if (F.fx.x > 0.0 && nz < edge) { return o; }
  auto n = vitaminCapsuleDoseNormalAt(p);
  auto nv = M * n;
  auto vv = float3(0.0, 0.0, 1.0);
  auto rdv = M * rd;
  auto L = normalize(float3(-0.5, 0.75, 0.55));
  auto mat = vitaminCapsuleDoseMaterial(p);
  auto cosv = max(dot(nv, vv), 0.0);
  auto fr = 0.04 + 0.96 * pow(1.0 - cosv, 5.0);
  auto refl = vitaminCapsuleDoseStudio(reflect(rdv, nv));
  auto spec = pow(max(dot(reflect(-L, nv), vv), 0.0), 70.0);
  // white: porcelain on the capsule, chalk on the tablet (rougher, speckled)
  auto wrap = clamp((dot(nv, L) + 0.35) / 1.35, 0.0, 1.0);
  auto chalk = select(0.0, 1.0, F.shape.z > 0.5 && F.shape.z < 1.5) * F.shape.w + select(0.0, 1.0, F.shape.y > 0.5 && F.shape.y < 1.5) * (1.0 - F.shape.w);
  auto speck = 1.0 - 0.06 * chalk * vitaminCapsuleDoseHash3(floor(p * 140.0));
  auto white = vitaminCapsuleDoseLin(mix(float3(0.97, 0.975, 0.985), float3(0.74, 0.9, 0.81), chalk)) * (0.42 + 0.62 * wrap) * speck;
  white = mix(white, white * vitaminCapsuleDoseLin(float3(0.8, 0.88, 1.0)), smoothstep(0.1, -0.6, dot(nv, L)) * 0.6);
  white += refl * mix(0.07, 0.03, chalk) + float3(1.0) * spec * mix(0.55, 0.08, chalk);
  // glass and liquid: through the object to its far side, absorbing on the way
  auto tin = 0.0;
  auto pin = p - n * 0.003;
  auto rin = refract(rd, n, 1.0 / mix(1.5, 1.33, mat.y));
  for (auto i = 0; i < 40; i++) {
    auto dd = -vitaminCapsuleDoseSdObj(pin + rin * tin);
    if (dd < 0.001) { break; }
    tin += max(dd * 0.9, 0.004);
    if (tin > 3.0) { break; }
  }
  auto pex = pin + rin * tin;
  auto nex = vitaminCapsuleDoseNormalAt(pex);
  auto rex = refract(rin, -nex, mix(1.5, 1.33, mat.y));
  if (dot(rex, rex) < 0.01) { rex = reflect(rin, -nex); }
  auto exv = M * pex;
  auto rexv = M * rex;
  auto seen = vitaminCapsuleDoseBehind(vitaminCapsuleDoseToScreen(exv.xy + rexv.xy * (0.6 + exv.z) / max(-rexv.z, 0.25)));
  auto blueAbs = exp(-float3(2.2, 0.75, 0.04) * tin * 1.7);
  auto clearAbs = exp(-float3(1.1, 0.42, 0.06) * tin * 1.3);
  auto through = seen * mix(blueAbs, clearAbs, mat.y);
  // an open, hollow half is thin glass: give it the capsule's blue all the same
  auto hollow = smoothstep(0.22, 0.4, F.open.x) * mat.x;
  through = mix(through, through * vitaminCapsuleDoseLin(float3(0.3, 0.6, 1.0)) * 1.15, hollow);
  // vitamin beads in the blue half: small glossy spheres
  if (mat.x > 0.5 && F.open.z < 0.5) {
    auto bt = 1e5;
    auto bn = float3(0.0);
    auto sc = max(F.fx.y, 0.02);
    for (auto i = 0; i < 9; i++) {
      auto fi = float(i);
      // inside the blue half, in the capsule's own frame
      auto lq = float3(-0.1 - 0.55 * vitaminCapsuleDoseHash1(fi + 1.0), (vitaminCapsuleDoseHash1(fi + 2.0) - 0.5) * 0.52, (vitaminCapsuleDoseHash1(fi + 3.0) - 0.5) * 0.52);
      auto c = sin(F.open.w); auto cs = cos(F.open.w);
      auto bc = float3(cs * lq.x - c * lq.y, c * lq.x + cs * lq.y, lq.z) * sc;
      auto br = (0.045 + 0.025 * vitaminCapsuleDoseHash1(fi + 4.0)) * sc;
      auto oc = pin - bc;
      auto bb = dot(oc, rin); auto cc = dot(oc, oc) - br * br; auto h = bb * bb - cc;
      if (h > 0.0) {
        auto tt = -bb - sqrt(h);
        if (tt > 0.0 && tt < tin && tt < bt) { bt = tt; bn = normalize(pin + rin * tt - bc); }
      }
    }
    if (bt < 1e4) {
      auto bnv = M * bn;
      auto bl = vitaminCapsuleDoseLin(float3(1.0, 0.8, 0.32)) * (0.5 + 0.7 * clamp(dot(bnv, L), 0.0, 1.0)) + float3(1.0) * pow(max(dot(reflect(-L, bnv), vv), 0.0), 40.0) * 0.9;
      through = mix(through, bl * mix(float3(1.0), blueAbs, 0.35), 0.95);
    }
  }
  // the drop gathers light into a bright caustic low in its body
  auto caustic = vitaminCapsuleDoseLin(float3(0.85, 0.95, 1.0)) * mat.y * 0.5 * pow(max(dot(nv, normalize(float3(0.4, -0.7, 0.6))), 0.0), 5.0);
  auto glass = fr * refl + (1.0 - fr) * (through + caustic) + float3(1.0) * spec * 1.4;
  glass += float3(0.6, 0.8, 1.0) * pow(1.0 - cosv, 3.0) * 0.12;
  // the liquid's edge darkens like a lens, so the drop reads on white
  glass = mix(glass, glass * vitaminCapsuleDoseLin(float3(0.55, 0.72, 0.92)), mat.y * pow(1.0 - cosv, 1.6) * 0.8);
  auto gw = clamp(mat.x + mat.y, 0.0, 1.0);
  auto col = mix(white, glass, gw);
  // the burning edge of the dissolve, and the held object warming as the ring fills
  if (F.fx.x > 0.0) { col += float3(0.7, 0.9, 1.0) * (1.0 - smoothstep(0.0, 0.06, nz - edge)) * 1.6; }
  col += float3(0.5, 0.75, 1.0) * F.fx.z * 0.08 * (1.0 - cosv);
  o.col = col;
  o.a = 1.0;
  return o;
}

float3 vitaminCapsuleDoseEncode(float3 c) { return pow(clamp(c / (float3(1.0) + max(c - float3(0.85), float3(0.0)) * 0.6), float3(0.0), float3(1.0)), float3(1.0 / 2.2)); }

void vitaminCapsuleDoseBeadsCs(uint3 gid) {
  auto i = gid.x;
  if (i >= 860u) { return; }
  auto fi = float(i);
  auto M = float3x3(F.rot0.xyz, F.rot1.xyz, F.rot2.xyz);
  // a point on the object's surface: start in its box, then walk onto the field
  auto p = (float3(vitaminCapsuleDoseHash1(fi + 1.0), vitaminCapsuleDoseHash1(fi + 2.0), vitaminCapsuleDoseHash1(fi + 3.0)) - 0.5) * float3(2.2, 1.6, 1.4);
  for (auto k = 0; k < 6; k++) { p -= vitaminCapsuleDoseNormalAt(p) * vitaminCapsuleDoseSdObj(p); }
  auto nz = vitaminCapsuleDoseFbm(p * 3.2);
  // it leaves when the dissolve reaches it
  auto leave = nz + 0.15;
  auto fly = clamp((F.fx.x * 1.6 - leave) / 0.55, 0.0, 1.0);
  VitaminCapsuleDoseBead b;
  if (F.fx.x <= 0.0 || fly <= 0.0 || fly >= 1.0) {
    b.at = float4(0.0, 0.0, 0.0, 0.0);
    b.tint = float4(0.0);
    beadsOut[i] = b;
    return;
  }
  auto pv = M * p;
  auto a = vitaminCapsuleDoseToScreen(pv.xy);
  auto tgt = F.goal.xy + (float2(vitaminCapsuleDoseHash1(fi + 7.0), vitaminCapsuleDoseHash1(fi + 8.0)) - 0.5) * 6.0;
  auto side = (vitaminCapsuleDoseHash1(fi + 5.0) - 0.5) * 140.0;
  auto ctrl = mix(a, tgt, 0.45) + float2(-60.0 + side * 0.4, -30.0 + side * 0.3);
  auto e = fly * fly * (3.0 - 2.0 * fly);
  auto q = mix(mix(a, ctrl, e), mix(ctrl, tgt, e), e);
  auto mat = vitaminCapsuleDoseMaterial(p);
  auto tint = mix(vitaminCapsuleDoseLin(float3(0.97, 0.98, 1.0)), vitaminCapsuleDoseLin(float3(0.15, 0.5, 1.0)), clamp(mat.x + mat.y * 0.6, 0.0, 1.0));
  auto r = (1.3 + 1.6 * vitaminCapsuleDoseHash1(fi + 9.0)) * (1.0 - 0.6 * e);
  auto alpha = smoothstep(0.0, 0.06, fly) * (1.0 - smoothstep(0.9, 1.0, fly));
  b.at = float4(q, r, alpha);
  b.tint = float4(tint, 0.0);
  beadsOut[i] = b;
}

float4 vitaminCapsuleDoseMouth() {
  auto ax = float2(cos(F.open.w), -sin(F.open.w));
  return float4(F.hero.xy + ax * 0.08 * F.hero.z, ax);
}

void vitaminCapsuleDosePearlsCs(uint li) {
  auto i = li;
  auto n = 40u;
  auto live = i < n;
  auto fi = float(i);
  auto r = F.floor.w;
  auto mode = F.phys.y;
  auto p = float2(0.0);
  auto v = float2(0.0);
  auto st = float4(0.0); // alive, age, seed, -
  if (live) {
    auto a = pearls[i * 2u];
    p = a.xy; v = a.zw;
    st = pearls[i * 2u + 1u];
    if (mode < 0.5) { st.x = 0.0; }
    if (mode > 0.5 && mode < 1.5) {
      // poured from the cup one after another
      auto m = vitaminCapsuleDoseMouth();
      p = m.xy + (float2(vitaminCapsuleDoseHash1(fi + 1.0), vitaminCapsuleDoseHash1(fi + 2.0)) - 0.5) * 10.0;
      v = m.zw * (120.0 + 120.0 * vitaminCapsuleDoseHash1(fi + 3.0)) + float2((vitaminCapsuleDoseHash1(fi + 4.0) - 0.5) * 90.0, -60.0 * vitaminCapsuleDoseHash1(fi + 5.0));
      st = float4(1.0, -0.6 * vitaminCapsuleDoseHash1(fi + 6.0), vitaminCapsuleDoseHash1(fi + 7.0), 0.0);
    }
  }
  auto dt = F.phys.x / 3.0;
  for (auto k = 0; k < 3; k++) {
    if (live && st.x > 0.5) {
      st.y += dt;
      if (st.y < 0.0) {
        // still in the cup: waiting its turn at the vitaminCapsuleDoseMouth
        p = vitaminCapsuleDoseMouth().xy;
      } else if (mode > 2.5) {
        // drawn home: back into the cup, or to the check
        auto goal = select(vitaminCapsuleDoseMouth().xy, F.goal.xy, mode > 3.5);
        auto late = st.z * 0.5;
        if (st.w < late) { st.w += dt; v += float2(0.0, -260.0) * dt; }
        else {
          auto to = goal - p;
          v += (to * 90.0 - v * 13.0) * dt;
          if (length(to) < 7.0) { st.x = 0.0; }
        }
      } else {
        v.y += 1500.0 * dt;
        v *= 0.999;
        // the finger stirs them
        if (F.aux.z > 0.5) {
          auto fd = p - F.phys.zw;
          auto fl = length(fd);
          if (fl < 30.0) { p += normalize(fd + float2(0.001)) * (30.0 - fl) * 0.5; v += F.aux.xy / max(F.phys.x, 0.004) * 0.25 * (1.0 - fl / 30.0); }
        }
      }
      p += v * dt;
      if (mode < 2.5) {
        // the sheet, and the edges of the screen
        if (p.y > F.floor.x - r) { p.y = F.floor.x - r; if (v.y > 0.0) { v.y = -v.y * 0.32; } v.x *= 0.96; }
        if (p.x < F.floor.y + r) { p.x = F.floor.y + r; v.x = abs(v.x) * 0.4; }
        if (p.x > F.floor.z - r) { p.x = F.floor.z - r; v.x = -abs(v.x) * 0.4; }
      }
    }
    wp[i] = select(float2(-1e4, -1e4 - fi * 40.0), p, live && st.x > 0.5 && st.y >= 0.0 && mode < 2.5);
    threadgroup_barrier(mem_flags::mem_threadgroup);
    if (live && st.x > 0.5 && st.y >= 0.0 && mode < 2.5) {
      for (auto j = 0u; j < n; j++) {
        if (j == i) { continue; }
        auto d = p - wp[j];
        auto l = length(d);
        if (l < 2.0 * r && l > 0.0001) {
          auto nn = d / l;
          p += nn * (2.0 * r - l) * 0.5;
          auto vn = dot(v, nn);
          if (vn < 0.0) { v -= nn * vn * 0.6; }
        }
      }
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
  }
  if (live) {
    pearls[i * 2u] = float4(p, v);
    pearls[i * 2u + 1u] = st;
    VitaminCapsuleDoseBead b;
    auto shown = st.x > 0.5 && st.y >= 0.0;
    b.at = float4(p, r, select(0.0, 1.0, shown));
    b.tint = float4(vitaminCapsuleDoseLin(float3(1.0, 0.8, 0.32)), 1.0);
    beadsOut[860u + i] = b;
  }
}


};
struct VitaminCapsuleDoseVOut { float4 pos [[position]]; float2 uv; };
vertex VitaminCapsuleDoseVOut vitaminCapsuleDoseFullVs(uint vi [[vertex_id]]) {
 float2 uv=float2(float((vi<<1u)&2u),float(vi&2u));
 return {float4(uv*float2(2,-2)+float2(-1,1),0,1),uv};
}
fragment float4 vitaminCapsuleDoseHeroFs(VitaminCapsuleDoseVOut v [[stage_in]], constant VitaminCapsuleDoseUniforms &F [[buffer(0)]]) {
 VitaminCapsuleDoseShader s{F,nullptr,nullptr,nullptr};
 auto px=s.vitaminCapsuleDoseShade(v.pos.xy/F.view.z);
 if(px.a<=0) return float4(0);
 return float4(s.vitaminCapsuleDoseEncode(px.col/px.a)*px.a,px.a);
}
kernel void vitaminCapsuleDoseBeadsCs(uint3 gid [[thread_position_in_grid]], constant VitaminCapsuleDoseUniforms &F [[buffer(0)]], device VitaminCapsuleDoseBead *beads [[buffer(1)]]) {
 VitaminCapsuleDoseShader s{F,beads,nullptr,nullptr}; s.vitaminCapsuleDoseBeadsCs(gid);
}
kernel void vitaminCapsuleDosePearlsCs(uint li [[thread_index_in_threadgroup]], constant VitaminCapsuleDoseUniforms &F [[buffer(0)]], device VitaminCapsuleDoseBead *beads [[buffer(1)]], device float4 *pearls [[buffer(3)]]) {
 threadgroup float2 wp[64];
 VitaminCapsuleDoseShader s{F,beads,pearls,wp}; s.vitaminCapsuleDosePearlsCs(li);
}
struct VitaminCapsuleDoseBOut { float4 pos [[position]]; float2 uv; float3 col; float a; };
vertex VitaminCapsuleDoseBOut vitaminCapsuleDoseBeadVs(uint vi [[vertex_id]], uint ii [[instance_id]], constant VitaminCapsuleDoseUniforms &F [[buffer(0)]], device const VitaminCapsuleDoseBead *beads [[buffer(2)]]) {
 auto b=beads[ii]; float2 corner=float2(-1,-1);
 if(vi==1u || vi==4u) corner=float2(1,-1);
 if(vi==2u || vi==3u) corner=float2(-1,1);
 if(vi==5u) corner=float2(1,1);
 float r=b.at.z*1.15; if(b.at.w<=0.001) r=0;
 float2 at=b.at.xy+corner*r;
 return {float4(at.x/F.view.x*2-1,1-at.y/F.view.y*2,0,1),corner*1.15,b.tint.xyz,b.at.w};
}
fragment float4 vitaminCapsuleDoseBeadFs(VitaminCapsuleDoseBOut v [[stage_in]], constant VitaminCapsuleDoseUniforms &F [[buffer(0)]]) {
 float r2=dot(v.uv,v.uv); float cov=1-smoothstep(0.8,1.0,r2);
 if(cov<=0) return float4(0);
 float3 n=float3(v.uv,sqrt(max(1-r2,0.0)));
 float3 L=normalize(float3(-0.5,0.75,0.55));
 float rim=pow(1-n.z,2.0);
 float3 c=v.col*(0.5+0.6*max(dot(n,L),0.0))+v.col*rim*0.5+float3(1)*pow(max(dot(reflect(-L,n),float3(0,0,1)),0.0),40.0)*0.9;
 float a=cov*v.a;
 VitaminCapsuleDoseShader s{F,nullptr,nullptr,nullptr};
 return float4(s.vitaminCapsuleDoseEncode(c)*a,a);
}
