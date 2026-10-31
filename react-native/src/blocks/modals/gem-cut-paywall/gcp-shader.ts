// Gem Paywall — a rough stone cut into a round brilliant, ray-traced exactly.
//
// The gem is a convex solid: a pebble (an ellipsoid) intersected with the 73 half-spaces of a round
// brilliant (table, 8 bezels, 8 stars, 16 upper girdle facets, 16 girdle sides, 8 pavilion mains,
// 16 lower girdle facets). A ray meets a convex solid where it enters the last half-space and leaves
// the first one, so every hit is exact: crisp facets, no marching. Cutting is literal: each facet
// plane starts tangent to the pebble and slides in to its place as uCut grows (table first, then the
// mains and the girdle, then the small facets), so polished windows open on a frosted stone until
// nothing of the pebble is left. Inside, the ray bounces by total internal reflection (up to four
// times) and leaves with dispersion (one refraction per channel), which is where the fire comes
// from. Studio of strip lights fixed in the world, so the facets flash as the gem turns. Around it:
// a violet halo and the rainbow caustic the gem throws on the floor. Premultiplied, sRGB → P3.
export const GCP_SKSL = `
uniform float2 uC;          // gem centre, pt
uniform float uS;           // girdle radius, pt
uniform float uCut;         // 0 pebble … 0.5 single cut … 1 brilliant
uniform float uYaw;         // turn about the gem's axis
uniform float uPitch;       // tilt towards the viewer
uniform float uSpark;       // 0 … 1, a flare of light right after a tier lands
uniform float2 uFloor;      // floor caustic centre, pt
uniform float uPixelRatio;

const float PI = 3.14159265;
const float IOR = 2.42;
const float GY = 0.02;      // girdle half thickness
float sq(float v) { return v*v; }
float3 rx(float3 v, float a) { float c=cos(a), s=sin(a); return float3(v.x, c*v.y - s*v.z, s*v.y + c*v.z); }
float3 ry(float3 v, float a) { float c=cos(a), s=sin(a); return float3(c*v.x + s*v.z, v.y, -s*v.x + c*v.z); }
float3 toWorld(float3 v) { return rx(ry(v, uYaw), uPitch); }
float3 toObj(float3 v) { return ry(rx(v, -uPitch), -uYaw); }

// the pebble, which grows out of the way at the end so no frost is left on the brilliant
float3 eC() { return float3(0.0, -0.27, 0.0); }
float3 eR() { return float3(1.18, 0.74, 1.08)*(1.0 + 0.3*smoothstep(0.62, 1.0, uCut)); }
float support(float3 n) { float3 r = eR(); return length(n*r) + dot(n, eC()); }

// a facet plane dot(n, p) = d, slid out to the pebble's tangent when prog = 0
void clip(float3 n, float d, float prog, float3 ro, float3 rd,
          inout float tIn, inout float tOut, inout float3 nIn, inout float3 nOut, inout float fIn, inout float fOut) {
  float dd = mix(support(n) + 0.004, d, prog);
  float den = dot(n, rd);
  float dist = dd - dot(n, ro);
  if (abs(den) < 1e-5) { if (dist < 0.0) { tIn = 1e4; } return; }
  float t = dist/den;
  if (den < 0.0) { if (t > tIn) { tIn = t; nIn = n; fIn = 0.0; } }
  else { if (t < tOut) { tOut = t; nOut = n; fOut = 0.0; } }
}

// enter and leave the solid along ro + t·rd (object space); f* = 1 where that surface is frosted pebble
bool solid(float3 ro, float3 rd, out float tIn, out float tOut, out float3 nIn, out float3 nOut, out float fIn, out float fOut) {
  tIn = -1e4; tOut = 1e4; nIn = float3(0.0, 1.0, 0.0); nOut = float3(0.0, -1.0, 0.0); fIn = 1.0; fOut = 1.0;
  // pebble
  float3 r = eR();
  float3 o = (ro - eC())/r, d = rd/r;
  float a = dot(d, d), b = dot(o, d), c = dot(o, o) - 1.0;
  float disc = b*b - a*c;
  if (disc < 0.0) { tIn = 1e4; tOut = -1e4; return false; }
  float sd = sqrt(disc);
  tIn = (-b - sd)/a; tOut = (-b + sd)/a;
  nIn = normalize((o + d*tIn)/r); nOut = normalize((o + d*tOut)/r);
  // order of the cut: table, then mains and girdle, then the small facets
  float pT = smoothstep(0.0, 0.32, uCut);
  float pM = smoothstep(0.1, 0.5, uCut);
  float pG = smoothstep(0.22, 0.55, uCut);
  float pS = smoothstep(0.52, 0.96, uCut);
  // proportions (girdle radius 1): crown 34.5°, pavilion 40.75°, table 57 %
  const float cA = 0.8241, sA = 0.5664;     // cos, sin 34.5°
  const float cB = 0.7575, sB = 0.6528;     // 40.75°
  const float cS = 0.9298, sS = 0.3681;     // star 21.6°
  const float cU = 0.7314, sU = 0.6820;     // upper girdle 43°
  const float cL = 0.7193, sL = 0.6947;     // lower girdle 44°
  const float yT = 0.316;
  float rTab = 1.0 - (yT - GY)*cA/sA;       // where a bezel meets the table
  clip(float3(0.0, 1.0, 0.0), yT, pT, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
  float2 u = float2(1.0, 0.0);
  const float c45 = 0.70710678;
  const float c22 = 0.92387953, s22 = 0.38268343;
  const float c11 = 0.98078528, s11 = 0.19509032;
  for (int i = 0; i < 8; i++) {
    float2 h = float2(u.x*c22 - u.y*s22, u.x*s22 + u.y*c22);   // half way to the next bezel
    float2 q1 = float2(u.x*c11 - u.y*s11, u.x*s11 + u.y*c11);  // a quarter either side
    float2 q2 = float2(u.x*c11 + u.y*s11, -u.x*s11 + u.y*c11);
    clip(float3(sA*u.x, cA, sA*u.y), sA + cA*GY, pM, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(sB*u.x, -cB, sB*u.y), sB + cB*GY, pM, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(u.x, 0.0, u.y), 1.0, pG, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(h.x, 0.0, h.y), 1.0, pG, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(sS*h.x, cS, sS*h.y), sS*rTab*c22 + cS*yT, pS, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(sU*q1.x, cU, sU*q1.y), sU*0.985 + cU*GY, pS, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(sU*q2.x, cU, sU*q2.y), sU*0.985 + cU*GY, pS, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(sL*q1.x, -cL, sL*q1.y), sL*0.985 + cL*GY, pS, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    clip(float3(sL*q2.x, -cL, sL*q2.y), sL*0.985 + cL*GY, pS, ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
    u = float2(u.x*c45 - u.y*c45, u.x*c45 + u.y*c45);
  }
  return tIn < tOut;
}

// the studio, in world space: black, a long soft box overhead, two strips at the sides, a ring
// light by the camera, a warm and a cool card low down
float3 studio(float3 r) {
  float3 c = float3(0.035, 0.03, 0.06) + float3(0.09, 0.06, 0.16)*smoothstep(-0.4, 0.6, r.y);
  float top = smoothstep(0.62, 0.7, r.y)*(1.0 - smoothstep(0.16, 0.24, abs(r.x)));
  c += float3(1.0)*2.6*top;
  float side = smoothstep(0.8, 0.86, abs(r.x))*(1.0 - smoothstep(0.32, 0.4, abs(r.y + 0.05)));
  c += mix(float3(1.0, 0.92, 0.85), float3(0.85, 0.92, 1.0), step(0.0, r.x))*2.0*side;
  float ring = smoothstep(0.955, 0.965, dot(r, normalize(float3(0.15, 0.35, 1.0))))*(1.0 - smoothstep(0.985, 0.992, dot(r, normalize(float3(0.15, 0.35, 1.0)))));
  c += float3(1.0)*2.2*ring;
  c += float3(1.0, 0.55, 0.85)*0.9*smoothstep(0.75, 0.95, dot(r, normalize(float3(-0.7, -0.45, 0.55))));
  c += float3(0.45, 0.7, 1.0)*0.9*smoothstep(0.75, 0.95, dot(r, normalize(float3(0.75, -0.4, 0.5))));
  return c;
}
// the same room seen through frosted glass
float3 studioSoft(float3 r) {
  float3 c = float3(0.16, 0.14, 0.24) + float3(0.42, 0.38, 0.55)*smoothstep(-0.6, 1.0, r.y);
  c += float3(0.4, 0.38, 0.45)*smoothstep(0.2, 1.0, dot(r, normalize(float3(0.15, 0.35, 1.0))));
  c += float3(0.35, 0.18, 0.32)*smoothstep(0.0, 1.0, dot(r, normalize(float3(-0.7, -0.45, 0.55))));
  c += float3(0.14, 0.24, 0.38)*smoothstep(0.0, 1.0, dot(r, normalize(float3(0.75, -0.4, 0.5))));
  return c;
}

float fresnel(float cosi) { const float F0 = 0.172; return F0 + (1.0 - F0)*pow(1.0 - clamp(cosi, 0.0, 1.0), 5.0); }

// light that leaves the gem after entering at p along d (object space)
float3 inside(float3 p, float3 d) {
  float3 col = float3(0.0);
  float w = 1.0;
  float spread = 0.045 + 0.03*uCut;
  for (int k = 0; k < 4; k++) {
    float tIn, tOut, fIn, fOut; float3 nIn, nOut;
    solid(p - d*0.5, d, tIn, tOut, nIn, nOut, fIn, fOut);
    float3 q = p + d*(tOut - 0.5);
    float3 n = nOut;
    if (fOut > 0.5) {
      // frosted: the light is scattered, the room comes through soft and milky
      col += w*studioSoft(toWorld(d))*float3(0.92, 0.9, 1.0);
      return col;
    }
    float3 g = refract(d, -n, IOR);
    if (dot(g, g) < 1e-4) { d = reflect(d, -n); p = q; continue; }
    float F = fresnel(dot(-d, -n));
    float3 rr = refract(d, -n, IOR - spread);
    float3 bb = refract(d, -n, IOR + spread);
    float3 e;
    e.r = studio(toWorld(dot(rr, rr) > 1e-4 ? rr : g)).r;
    e.g = studio(toWorld(g)).g;
    e.b = studio(toWorld(dot(bb, bb) > 1e-4 ? bb : reflect(d, -n))).b;
    col += w*(1.0 - F)*e;
    w *= F;
    d = reflect(d, -n); p = q;
  }
  return col + w*studio(toWorld(d))*0.6;
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
half4 premul(float3 rgb, float a) {
  if (a <= 0.0) return half4(0.0);
  return half4(half3(toP3(clamp(rgb, 0.0, 1.0))*a), half(a));
}
float3 hue(float h) { return clamp(abs(fract(h + float3(0.0, 0.6667, 0.3333))*6.0 - 3.0) - 1.0, 0.0, 1.0); }

half4 main(float2 xy) {
  float2 s = (xy - uC)/uS;
  float px = 1.0/(uS*uPixelRatio);
  // around the gem: a violet halo, and the floor lit by the gem's caustic
  float halo = exp(-sq(length(s*float2(1.0, 1.15))/1.55))*(0.35 + 0.25*uCut + 0.25*uSpark);
  float3 bg = float3(0.32, 0.16, 0.62)*halo;
  float bgA = clamp(halo*0.9, 0.0, 1.0);
  float2 f = (xy - uFloor)/uS*float2(1.0, 3.2);
  float fr = length(f);
  float fa = atan(f.y, f.x);
  float rays = pow(0.5 + 0.5*cos(8.0*(fa - uYaw)), 6.0) + 0.6*pow(0.5 + 0.5*cos(16.0*(fa + uYaw*0.5) + 1.3), 10.0);
  float caustic = rays*smoothstep(1.25, 0.25, fr)*smoothstep(0.0, 0.35, fr)*sq(uCut)*(0.55 + 0.6*uSpark);
  float3 cc = hue(fa/(2.0*PI)*2.0 + fr*0.9 - uYaw*0.3)*caustic*1.4 + float3(0.9, 0.85, 1.0)*0.25*smoothstep(1.0, 0.0, fr)*sq(uCut);
  bg += cc;
  bgA = clamp(bgA + max(cc.r, max(cc.g, cc.b)), 0.0, 1.0);

  if (dot(s, s) > 2.4) return premul(bg/max(bgA, 1e-4), bgA);
  float3 ro = toObj(float3(0.0, 0.0, 6.0)) + float3(0.0, -0.28, 0.0);
  float3 rdW = normalize(float3(s.x, -s.y, -6.0));
  float3 rd = toObj(rdW);
  float tIn, tOut, fIn, fOut; float3 nIn, nOut;
  bool hit = solid(ro, rd, tIn, tOut, nIn, nOut, fIn, fOut);
  float cover = clamp((tOut - tIn)/(2.2*px) , 0.0, 1.0);
  if (!hit || cover <= 0.0) return premul(bg/max(bgA, 1e-4), bgA);

  float3 p = ro + rd*tIn;
  float3 n = nIn;
  float3 nW = toWorld(n);
  float cosi = max(dot(-rd, n), 0.0);
  float3 col;
  if (fIn > 0.5) {
    // the frosted pebble, like sea glass: milky and thicker (deeper violet) towards the bottom,
    // the room glowing through it, a soft sheen of the overhead box, a cool rim, a fine frost
    float3 sc = studioSoft(reflect(rdW, nW));
    float wrap = clamp(dot(nW, normalize(float3(-0.3, 0.8, 0.5)))*0.5 + 0.5, 0.0, 1.0);
    float3 milk = mix(float3(0.36, 0.3, 0.6), float3(0.8, 0.78, 0.92), wrap);
    float3 through = inside(p, refract(rd, n, 1.0/IOR));
    col = milk*0.62 + sc*0.18 + through*0.32;
    col += float3(0.75, 0.8, 1.0)*0.55*pow(1.0 - cosi, 2.5);
    float3 rw = reflect(rdW, nW);
    col += float3(1.0)*0.55*smoothstep(0.35, 0.85, rw.y)*(1.0 - smoothstep(0.1, 0.6, abs(rw.x)));
    col += float3(1.0, 0.7, 0.9)*0.25*smoothstep(0.5, 0.95, dot(rw, normalize(float3(-0.7, -0.45, 0.55))));
    float3 g3 = floor(p*95.0);
    float grain = fract(sin(dot(g3, float3(12.9898, 78.233, 37.719)))*43758.5453);
    col *= 0.95 + 0.1*grain;
  } else {
    float F = fresnel(cosi);
    float3 refl = studio(reflect(rdW, nW));
    float3 body = inside(p, refract(rd, n, 1.0/IOR));
    // until the mains are in, the stone is still cloudy inside: a fresh window shows milky light
    float cloud = 0.8*(1.0 - smoothstep(0.12, 0.5, uCut));
    float3 milkGlow = mix(float3(0.42, 0.36, 0.66), float3(0.86, 0.84, 0.97), clamp(nW.y*0.5 + 0.5, 0.0, 1.0));
    body = mix(body, milkGlow, cloud);
    col = mix(body, refl, F);
  }
  col *= 1.0 + 0.35*uSpark;
  // tone: keep highlights from clipping flat
  col = col/(1.0 + 0.18*max(col.r, max(col.g, col.b)));
  float3 outC = mix(bg/max(bgA, 1e-4)*bgA, col, cover);
  float outA = mix(bgA, 1.0, cover);
  return premul(outC/max(outA, 1e-4), outA);
}
`;
