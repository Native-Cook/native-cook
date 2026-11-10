#include <metal_stdlib>
using namespace metal;
struct AppIconAtelierUniforms { float4 data[29]; };
struct AppIconAtelierVertex { float4 position [[position]]; };
vertex AppIconAtelierVertex appIconAtelierVertex(uint id [[vertex_id]]) {
  float2 p = float2((id << 1) & 2, id & 2);
  return {float4(p * 2.0 - 1.0, 0.0, 1.0)};
}




























constant float IOR = 1.47;
float appIconAtelierSq(float v) { return v*v; }
float appIconAtelierDot2(float2 v) { return dot(v, v); }

float appIconAtelierSdRBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// the pebble's outline: a squircle a little out of true, like a hand-made thing
float appIconAtelierPebble2(float2 p) {
  p += 0.035*float2(sin(2.1*p.y + 0.7), sin(1.7*p.x + 2.0));
  return appIconAtelierSdRBox(p, float2(0.93, 0.88), 0.52);
}
float appIconAtelierSdPebble(float3 p) {
  const float r = 0.24;
  float h = 0.36 - 0.08*dot(p.xy, p.xy);       // half thickness: the face is domed
  float2 w = float2(appIconAtelierPebble2(p.xy) + r, abs(p.z) - h + r);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - r;
}
// iq's heart, tip at the origin, lobes up to y ~ 1
float appIconAtelierSdHeart(float2 p) {
  p.x = abs(p.x);
  if (p.y + p.x > 1.0) return sqrt(appIconAtelierDot2(p - float2(0.25, 0.75))) - 0.35355339;
  return sqrt(min(appIconAtelierDot2(p - float2(0.0, 1.0)), appIconAtelierDot2(p - 0.5*max(p.x + p.y, 0.0))))*sign(p.x - p.y);
}
float appIconAtelierSdGlyph(float3 p) {
  const float k = 0.66;
  const float r = 0.05;
  float d2 = appIconAtelierSdHeart(float2(p.x, p.y + 0.37)/k)*k;
  float2 w = float2(d2 + r, abs(p.z - 0.1) - 0.11 + r);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - r;
}
float3 appIconAtelierNPebble(float3 p) {
  const float e = 0.0015;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*appIconAtelierSdPebble(p + k.xyy*e) + k.yyx*appIconAtelierSdPebble(p + k.yyx*e) + k.yxy*appIconAtelierSdPebble(p + k.yxy*e) + k.xxx*appIconAtelierSdPebble(p + k.xxx*e));
}
float3 appIconAtelierNGlyph(float3 p) {
  const float e = 0.0015;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*appIconAtelierSdGlyph(p + k.xyy*e) + k.yyx*appIconAtelierSdGlyph(p + k.yyx*e) + k.yxy*appIconAtelierSdGlyph(p + k.yxy*e) + k.xxx*appIconAtelierSdGlyph(p + k.xxx*e));
}

float3 appIconAtelierRx(float3 v, float a) { float c = cos(a), s = sin(a); return float3(v.x, c*v.y - s*v.z, s*v.y + c*v.z); }
float3 appIconAtelierRy(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x + s*v.z, v.y, -s*v.x + c*v.z); }
float3 appIconAtelierRz(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x - s*v.y, s*v.x + c*v.y, v.z); }
float3 appIconAtelierToWorld(float3 v, float3 pose) { return appIconAtelierRz(appIconAtelierRx(appIconAtelierRy(v, pose.x), pose.y), pose.z); }
float3 appIconAtelierToObj(float3 v, float3 pose) { return appIconAtelierRy(appIconAtelierRx(appIconAtelierRz(v, -pose.z), -pose.y), -pose.x); }

// a bright photo studio: soft white walls, a darker floor band (it draws the glass's edges), a big
// soft box high on the left, a strip light on the right, a warm card low on the left
float3 appIconAtelierStudio(float3 r) {
  float3 c = mix(float3(0.6, 0.56, 0.74), float3(0.9, 0.88, 0.97), smoothstep(-0.1, 0.8, r.y));
  c = mix(float3(0.26, 0.2, 0.44), c, smoothstep(-0.55, 0.02, r.y));
  float box = smoothstep(0.93, 0.95, dot(r, normalize(float3(-0.45, 0.85, 0.22))));
  c += float3(1.0)*14.0*box;
  float top = smoothstep(0.7, 0.74, r.y)*(1.0 - smoothstep(0.3, 0.36, abs(r.x + 0.1)));
  c += float3(1.0)*6.0*top;
  float strip = smoothstep(0.84, 0.87, r.x)*(1.0 - smoothstep(0.3, 0.38, abs(r.y - 0.1)));
  c += float3(1.0)*9.0*strip;
  c += float3(1.0, 0.82, 0.9)*0.35*smoothstep(0.7, 0.9, dot(r, normalize(float3(-0.8, -0.3, 0.5))));
  // a big diffuser in front, high on the left: the soft sheen of the domed face, with its bright core
  float3 fd = normalize(float3(-0.32, 0.46, 0.83));
  c += float3(5.0, 4.9, 5.4)*pow(max(dot(r, fd), 0.0), 16.0);
  return c;
}
// a thin film on the glass: the colour of a soap bubble, shifting with the viewing angle
float3 appIconAtelierFilm(float cosi, float3 p) {
  float d = 1.6 + 1.1*cosi + 0.25*sin(3.0*p.x + 2.0*p.y);
  return 0.62 + 0.38*cos(6.2831853*(d + float3(0.0, 0.33, 0.67)));
}

// the paper, the card and its wallpaper, and the two shadows: everything a pebble sees behind it
float3 appIconAtelierShadowOn(float3 col, float2 xy, float2 f, float s, float strength, float3 tint) {
  if (strength <= 0.001) return col;
  float2 q = (xy - f)/float2(s*1.05, s*0.17);
  float a = exp(-dot(q, q)*1.6)*strength;
  // a coloured shadow: the glass lets its own colour through, brightest in the middle (a caustic)
  float3 dark = mix(float3(0.66, 0.62, 0.76), tint*0.85 + 0.08, 0.65);
  col *= mix(float3(1.0), dark, a*0.85);
  float2 qc = (xy - f - float2(-0.08*s, -0.02*s))/float2(s*0.42, s*0.07);
  col += tint*0.22*exp(-dot(qc, qc)*2.0)*strength;
  return col;
}
float3 appIconAtelierPage(float2 xy, constant AppIconAtelierUniforms &U, texture2d<float> uWall) {
  float3 col = mix(float3(0.969, 0.961, 0.984), float3(0.933, 0.918, 0.965), clamp(xy.y/U.data[0].xy.y, 0.0, 1.0));
  // a faint pool of light on the pedestal
  float g = exp(-appIconAtelierDot2((xy - U.data[3].xy - float2(0.0, 0.25*U.data[4].x))/(U.data[4].x*float2(2.6, 1.8))));
  col = mix(col, float3(0.985, 0.978, 1.0), 0.6*g);
  float2 cc = U.data[1].xyzw.xy + U.data[1].xyzw.zw*0.5;
  float dc = appIconAtelierSdRBox(xy - cc, U.data[1].xyzw.zw*0.5, U.data[2].x);
  if (dc < 1.0) {
    float2 imageSize = float2(uWall.get_width(), uWall.get_height());
    float imageScale = max(U.data[1].z/imageSize.x, U.data[1].w/imageSize.y);
    float2 fitted = imageSize*imageScale;
    float2 uv = (xy - U.data[1].xy - (U.data[1].zw-fitted)*0.5)/fitted;
    constexpr sampler samp(coord::normalized, address::clamp_to_edge, filter::linear);
    float3 wall = uWall.sample(samp, uv).rgb;
    col = mix(col, wall, clamp(0.5 - dc/max(U.data[24].x, 0.2), 0.0, 1.0));
  }
  col = appIconAtelierShadowOn(col, xy, U.data[18].xy + float2(0.0, U.data[19].x*1.1), U.data[19].x, 0.5*U.data[20].x, U.data[22].xyzw.rgb);
  col = appIconAtelierShadowOn(col, xy, U.data[15].xy, U.data[4].x, U.data[16].x, U.data[17].xyz);
  return col;
}

// one pebble. c, s: centre and half width (pt); sqz: squash; a*/b*: the two glasses; front, dir,
// men, phase: the liquid front between them
float3 appIconAtelierGlass(float2 xy, float2 c, float s, float3 pose, float2 sqz, float4 a0, float4 a1, float4 b0, float4 b1,
          float front, float dir, float men, float phase, thread float &cover, constant AppIconAtelierUniforms &U, texture2d<float> uWall) {
  float2 u = (xy - c)/(s*sqz);
  float px = U.data[24].x/s;
  float3 ro = appIconAtelierToObj(float3(0.0, 0.0, 6.0), pose);
  float3 rdW = normalize(float3(u.x, -u.y, -6.0));
  float3 rd = appIconAtelierToObj(rdW, pose);
  // bounding sphere
  float b = dot(ro, rd);
  float disc = b*b - (dot(ro, ro) - 2.3);
  cover = 0.0;
  if (disc < 0.0) return float3(0.0);
  float sd = sqrt(disc);
  float t = -b - sd;
  float tEnd = -b + sd;
  float dmin = 1e3;
  float tBest = t;
  bool hit = false;
  for (int i = 0; i < 64; i++) {
    float d = appIconAtelierSdPebble(ro + rd*t);
    if (d < dmin) { dmin = d; tBest = t; }
    if (d < 0.0012) { hit = true; tBest = t; break; }
    t += d*0.8;
    if (t > tEnd) break;
  }
  cover = hit ? 1.0 : 1.0 - smoothstep(0.0, 1.6*px, dmin);
  if (cover <= 0.0) return float3(0.0);

  float3 p = ro + rd*tBest;
  float3 n = appIconAtelierNPebble(p);
  float3 nW = appIconAtelierToWorld(n, pose);
  float cosi = clamp(dot(-rd, n), 0.0, 1.0);

  // which glass is here: A or B, split by a wavy liquid front
  float fr = front + 0.06*sin(5.5*p.y + phase) + 0.03*sin(11.0*p.y - 1.7*phase);
  float m = smoothstep(-0.05, 0.05, (p.x - fr)*dir);
  float4 g0 = mix(a0, b0, m);
  float4 g1 = mix(a1, b1, m);
  float3 tint = g0.rgb;
  float dens = g0.a;
  float frost = g1.x;
  float filmK = g1.y;
  float glow = g1.z;

  // the surface: Fresnel reflection of the studio, a soap-film rainbow towards the rim
  float F = 0.04 + 0.96*pow(1.0 - cosi, 5.0);
  float3 refl = appIconAtelierStudio(reflect(rdW, nW));
  refl *= mix(float3(1.0), appIconAtelierFilm(cosi, p), filmK*(0.35 + 0.65*(1.0 - cosi)));
  refl = mix(refl, float3(0.92, 0.9, 0.96) + 0.08*refl, frost*0.6);

  // inside: march to the heart or to the back of the glass
  float3 di = refract(rd, n, 1.0/IOR);
  float tt = 0.012;
  float3 q = p;
  int what = 0; // 1 heart, 2 back
  for (int k = 0; k < 40; k++) {
    q = p + di*tt;
    float dP = -appIconAtelierSdPebble(q);
    float dG = appIconAtelierSdGlyph(q);
    if (dG < 0.002) { what = 1; break; }
    if (dP < 0.002) { what = 2; break; }
    tt += max(min(dP, dG)*0.85, 0.006);
  }
  float3 absorb = pow(max(tint, float3(0.001)), float3(dens*tt));
  float3 body;
  if (what == 1) {
    // the heart: a white slab, lit by the soft box, its sides tinted by the glass around it
    float3 gn = appIconAtelierToWorld(appIconAtelierNGlyph(q), pose);
    float key = clamp(dot(gn, normalize(float3(-0.45, 0.65, 0.62))), 0.0, 1.0);
    float3 heart = mix(float3(0.985, 0.975, 1.0), float3(1.0, 0.93, 0.8), glow);
    float3 lit = heart*(0.62 + 0.45*key) + 0.1*tint*(1.0 - key);
    body = lit*mix(absorb, float3(1.0), 0.6 + 0.3*glow) + float3(1.0, 0.86, 0.62)*glow*0.3;
  } else {
    // through the back: refract out and pick up the page behind the pebble
    float3 no = appIconAtelierNPebble(q);
    float3 dout = refract(di, -no, IOR);
    float3 seen;
    if (dot(dout, dout) < 1e-4) {
      seen = min(appIconAtelierStudio(appIconAtelierToWorld(reflect(di, -no), pose)), float3(1.0))*0.8;
    } else {
      float3 qW = appIconAtelierToWorld(q, pose);
      float3 dW = appIconAtelierToWorld(dout, pose);
      float tp = (qW.z + 1.4)/max(-dW.z, 0.25);
      float2 hitP = qW.xy + dW.xy*tp;
      float2 xyB = c + float2(hitP.x, -hitP.y)*s*sqz;
      seen = appIconAtelierPage(xyB, U, uWall);
      if (frost > 0.01) {
        float o = 0.22*s;
        float3 soft = 0.25*(appIconAtelierPage(xyB + float2(o, 0.0), U, uWall) + appIconAtelierPage(xyB - float2(o, 0.0), U, uWall) + appIconAtelierPage(xyB + float2(0.0, o), U, uWall) + appIconAtelierPage(xyB - float2(0.0, o), U, uWall));
        seen = mix(seen, mix(soft, float3(0.95, 0.94, 0.98), 0.45), frost);
      }
    }
    body = seen*absorb + tint*0.05*dens;
  }
  // a little light scattered in the glass itself: frost glows, colour pools
  body = mix(body, mix(float3(0.96, 0.95, 0.99), tint, 0.35), frost*0.35);
  // milky glass with a strong film reads as nacre: the rainbow drifts across the whole face
  if (what != 1) body *= mix(float3(1.0), appIconAtelierFilm(0.25 + 0.6*cosi, p*1.4)*1.1, filmK*frost*0.14);
  float3 col = mix(body, refl, F);
  // the meniscus: a bright curved line where the two glasses meet
  float glint = exp(-appIconAtelierSq((p.x - fr)/0.03))*men;
  col += float3(1.0, 0.99, 1.0)*0.6*glint*(0.6 + 0.4*cosi);
  col += appIconAtelierFilm(cosi, p)*0.18*exp(-appIconAtelierSq((p.x - fr - 0.07*dir)/0.06))*men;
  return col;
}

float3 appIconAtelierToP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

fragment half4 appIconAtelierFragment(AppIconAtelierVertex in [[stage_in]], constant AppIconAtelierUniforms &U [[buffer(0)]], texture2d<float> uWall [[texture(0)]]) {
  float2 xy = in.position.xy/U.data[28].x;
  float3 col = appIconAtelierPage(xy, U, uWall);
  // the icon in use, in its Home Screen slot
  if (U.data[20].x > 0.001 && appIconAtelierDot2(xy - U.data[18].xy) < appIconAtelierSq(U.data[19].x*1.7)) {
    float s = U.data[19].x*(1.0 + 0.08*U.data[21].x);
    float cv;
    float3 g = appIconAtelierGlass(xy, U.data[18].xy, s, float3(-0.06, -0.05, 0.0), float2(1.0), U.data[22].xyzw, U.data[23].xyzw, U.data[22].xyzw, U.data[23].xyzw, 9.0, 1.0, 0.0, 0.0, cv, U, uWall);
    col = mix(col, g, cv*U.data[20].x);
  }
  // the large pebble
  if (appIconAtelierDot2(xy - U.data[3].xy) < appIconAtelierSq(U.data[4].x*1.75)) {
    float cv;
    float3 g = appIconAtelierGlass(xy, U.data[3].xy, U.data[4].x, U.data[5].xyz, U.data[6].xy, U.data[7].xyzw, U.data[8].xyzw, U.data[9].xyzw, U.data[10].xyzw, U.data[11].x, U.data[12].x, U.data[13].x, U.data[14].x, cv, U, uWall);
    col = mix(col, g, cv);
  }
  // tone: keep the soft box from clipping flat
  col = col/(1.0 + 0.12*max(max(col.r, col.g), col.b) - 0.12);
  float3 o = appIconAtelierToP3(clamp(col, 0.0, 1.0));
  // a whisper of dither: the large soft gradients of the page band at 8 bits otherwise
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}
