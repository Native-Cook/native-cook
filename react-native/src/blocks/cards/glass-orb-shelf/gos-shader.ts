// Glass Orb Shelf — the Featured orb: a large ball of glass at the top of the shelf, holding a clay
// scene for the app in front.
// The ball is shaded analytically (Fresnel reflection of a studio, a specular point, a caustic in its
// lower inside, a thin dark edge); a ray refracted at its surface marches a small scene of signed
// distance fields inside it. The scenes are modelled as clay — chunky rounded forms, matte colour,
// wrapped light, ambient occlusion and a soft sheen — like the Icon Morph Sheet's: a book, a globe,
// a film camera, a bowl in front of a receipt. uScene (0…3) blends neighbouring scenes: the clay
// melts from one into the next (a blend of their distances that swells in the middle). Output is
// premultiplied and converted from sRGB to Display P3.
export const GOS_SKSL = `
uniform float2 uCentre;
uniform float uRadius;
uniform float uPixelRatio;
uniform float uScene;
uniform float uSpin;
uniform float2 uTilt;
uniform float uPop;
uniform float uTime;
uniform float3 uPage;

const float PI = 3.14159265359;
struct Hit { float d; float3 c; };

float sq(float v) { return v*v; }
float3 rotY(float3 p, float a) { float c=cos(a), s=sin(a); return float3(c*p.x+s*p.z, p.y, -s*p.x+c*p.z); }
float3 rotX(float3 p, float a) { float c=cos(a), s=sin(a); return float3(p.x, c*p.y-s*p.z, s*p.y+c*p.z); }
float3 rotZ(float3 p, float a) { float c=cos(a), s=sin(a); return float3(c*p.x-s*p.y, s*p.x+c*p.y, p.z); }
float box(float3 p, float3 b, float r) { float3 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,max(q.y,q.z)),0.0)-r; }
float cyl(float3 p, float r, float h, float rr) { float2 d = float2(length(p.xy) - r + rr, abs(p.z) - h + rr); return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - rr; }
float smin(float a, float b, float k) { float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0); return mix(b,a,h)-k*h*(1.0-h); }
float hash3(float3 p) { p=fract(p*0.3183099+0.1); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float noise3(float3 x) {
  float3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
  return mix(mix(mix(hash3(i),hash3(i+float3(1,0,0)),f.x), mix(hash3(i+float3(0,1,0)),hash3(i+float3(1,1,0)),f.x),f.y),
             mix(mix(hash3(i+float3(0,0,1)),hash3(i+float3(1,0,1)),f.x), mix(hash3(i+float3(0,1,1)),hash3(i+float3(1,1,1)),f.x),f.y),f.z);
}
Hit hit(float d, float3 c) { Hit h; h.d=d; h.c=c; return h; }
// clay joins: a soft union that carries the colour of the nearer part
Hit join(Hit a, Hit b, float k) {
  float h = clamp(0.5 + 0.5*(b.d - a.d)/k, 0.0, 1.0);
  Hit o; o.d = mix(b.d, a.d, h) - k*h*(1.0 - h); o.c = mix(b.c, a.c, h); return o;
}

// ---- scene 0: an open book, chunky covers and page blocks, a ribbon
Hit book(float3 p) {
  p = rotX(p - float3(0.0, -0.08, 0.0), -0.95);
  p = rotY(p, 0.25*sin(uSpin));
  float3 green = float3(0.16, 0.62, 0.38);
  float3 cream = float3(0.98, 0.9, 0.74);
  float3 l = rotZ(p - float3(-0.02, -0.16, 0.0), 0.24);
  float3 r = rotZ(p - float3(0.02, -0.16, 0.0), -0.24);
  Hit h = hit(box(l - float3(-0.34, -0.05, 0.0), float3(0.36, 0.05, 0.44), 0.05), green);
  h = join(h, hit(box(r - float3(0.34, -0.05, 0.0), float3(0.36, 0.05, 0.44), 0.05), green), 0.06);
  // the pages bulge up a little toward the spine
  float bulgeL = 0.05*(1.0 - sq((l.x + 0.32)/0.34));
  float bulgeR = 0.05*(1.0 - sq((r.x - 0.32)/0.34));
  h = join(h, hit(box(l - float3(-0.32, 0.06 + bulgeL, 0.0), float3(0.31, 0.07, 0.4), 0.06), cream), 0.03);
  h = join(h, hit(box(r - float3(0.32, 0.06 + bulgeR, 0.0), float3(0.31, 0.07, 0.4), 0.06), cream*0.97), 0.03);
  // a ribbon falling out of the spine
  float3 q = p - float3(0.04, -0.12, 0.36);
  float rib = box(rotZ(q, 0.1), float3(0.045, 0.2, 0.025), 0.02);
  h = join(h, hit(rib, float3(0.93, 0.33, 0.3)), 0.02);
  return h;
}
// ---- scene 1: a clay globe on a stand, its continents raised
Hit globe(float3 p) {
  float3 c0 = p - float3(0.0, 0.12, 0.0);
  float3 q = rotY(rotZ(c0, 0.4), uSpin);
  float n = noise3(q*1.7 + 3.1)*0.7 + noise3(q*4.3)*0.3;
  float land = smoothstep(0.38, 0.46, n);
  float d = length(c0) - 0.56 - 0.035*land;
  float3 sea = float3(0.22, 0.52, 0.95);
  float3 c = mix(sea, mix(float3(0.42, 0.78, 0.38), float3(0.95, 0.82, 0.5), smoothstep(0.66, 0.78, n)), land);
  Hit h = hit(d*0.9, c);
  // the meridian ring and the foot
  float3 m = rotZ(c0, 0.4);
  float ring = length(float2(length(m.xy) - 0.68, m.z)) - 0.035;
  ring = max(ring, -(m.x + 0.05));
  h = join(h, hit(ring, float3(0.95, 0.72, 0.3)), 0.02);
  float stem = length(float2(length(p.xz), max(abs(p.y + 0.66) - 0.08, 0.0))) - 0.05;
  float foot = box(p - float3(0.0, -0.8, 0.0), float3(0.3, 0.05, 0.3), 0.05);
  h = join(h, hit(min(stem, foot), float3(0.95, 0.72, 0.3)), 0.06);
  return h;
}
// ---- scene 2: a chunky film camera
Hit camera(float3 p) {
  float3 q = rotY(p, -0.5 + 0.2*sin(uSpin));
  Hit h = hit(box(q - float3(0.0, -0.05, 0.0), float3(0.56, 0.33, 0.24), 0.12), float3(0.18, 0.66, 0.72));
  h = join(h, hit(box(q - float3(0.0, -0.06, 0.0), float3(0.58, 0.14, 0.255), 0.06), float3(0.96, 0.88, 0.72)), 0.02);
  float3 lq = q - float3(0.0, -0.06, 0.26);
  h = join(h, hit(cyl(lq, 0.24, 0.12, 0.06), float3(0.2, 0.22, 0.26)), 0.04);
  h = join(h, hit(cyl(lq - float3(0.0, 0.0, 0.1), 0.15, 0.04, 0.03), float3(0.16, 0.3, 0.55)), 0.01);
  h = join(h, hit(length(q - float3(0.36, 0.34, 0.0)) - 0.09, float3(0.95, 0.36, 0.3)), 0.04);
  h = join(h, hit(box(q - float3(-0.3, 0.33, 0.02), float3(0.13, 0.06, 0.1), 0.05), float3(0.96, 0.88, 0.72)), 0.04);
  return h;
}
// ---- scene 3: a bowl of tomatoes and greens in front of a receipt
Hit bowl(float3 p) {
  float3 q = p - float3(0.0, -0.18, 0.12);
  float outer = length(q) - 0.5;
  float inner = length(q - float3(0.0, 0.05, 0.0)) - 0.44;
  float b = max(max(outer, -inner), q.y - 0.04);
  b = min(b, length(float2(length(q.xz) - 0.47, q.y - 0.04)) - 0.035);
  Hit h = hit(b, float3(0.98, 0.95, 0.9));
  h = join(h, hit(length(q - float3(-0.16, 0.15, 0.06)) - 0.19, float3(0.95, 0.27, 0.2)), 0.03);
  h = join(h, hit(length(q - float3(0.17, 0.17, -0.04)) - 0.17, float3(0.97, 0.36, 0.22)), 0.03);
  h = join(h, hit(length((q - float3(0.04, 0.17, 0.16))*float3(1.0, 1.8, 1.4)) - 0.15, float3(0.36, 0.76, 0.34)), 0.04);
  h = join(h, hit(length((q - float3(-0.22, 0.2, -0.14))*float3(1.5, 1.8, 1.0)) - 0.14, float3(0.3, 0.68, 0.3)), 0.04);
  float3 r = rotZ(p - float3(0.18, 0.08, -0.42), 0.16);
  float receipt = box(r, float3(0.26, 0.44, 0.03), 0.03);
  float lines = step(0.55, fract(r.y*7.0))*step(abs(r.x), 0.17)*step(r.y, 0.28);
  h = join(h, hit(receipt, mix(float3(0.99, 0.98, 0.96), float3(0.62, 0.62, 0.66), lines*0.6)), 0.02);
  return h;
}
Hit scene(float3 p, float id) {
  float k = mod(id, 4.0);
  if (k < 0.5) return book(p);
  if (k < 1.5) return globe(p);
  if (k < 2.5) return camera(p);
  return bowl(p);
}
float3 water(float id, float y) {
  float k = mod(id, 4.0);
  float3 a; float3 b;
  if (k < 0.5) { a = float3(0.05, 0.22, 0.16); b = float3(0.24, 0.55, 0.42); }
  else if (k < 1.5) { a = float3(0.05, 0.12, 0.32); b = float3(0.26, 0.45, 0.82); }
  else if (k < 2.5) { a = float3(0.05, 0.22, 0.27); b = float3(0.26, 0.6, 0.64); }
  else { a = float3(0.24, 0.1, 0.1); b = float3(0.62, 0.34, 0.3); }
  return mix(a, b, smoothstep(-0.9, 0.9, y));
}
float3 waterAt(float s, float y) {
  float a = floor(s);
  return mix(water(a, y), water(a + 1.0, y), smoothstep(0.0, 1.0, s - a));
}
Hit inside(float3 p) {
  float a = floor(uScene), w = uScene - a;
  // the scenes are modelled to fill a unit ball; drawn at 0.84 so the glass magnifies them to its edge
  const float S = 0.84;
  float3 q = rotX(rotY(p, uTilt.x), uTilt.y)/S;
  Hit A = scene(q, a);
  A.d *= S;
  if (w < 0.001) return A;
  Hit B = scene(q, a + 1.0);
  B.d *= S;
  if (w > 0.999) return B;
  // the clay melts: the blend swells in the middle so the two forms flow through one lump
  float m = smoothstep(0.0, 1.0, w);
  Hit h; h.d = mix(A.d, B.d, m) - 0.16*sin(PI*w); h.c = mix(A.c, B.c, m);
  return h;
}
float insideD(float3 p) { return inside(p).d; }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
half4 finish(float3 rgb, float a) {
  if (a <= 0.0) return half4(0.0);
  float3 s = toP3(rgb/a);
  return half4(half3(s*a), half(a));
}

half4 main(float2 xy) {
  float pop = uPop;
  float2 q = (xy - uCentre)/uRadius;
  q.x /= 1.0 + 0.05*pop; q.y /= 1.0 - 0.05*pop;
  float r2 = dot(q, q);
  // light through the orb lands on the page under it, in the colour of its water
  float glowA = exp(-sq(q.x/0.7) - sq((q.y - 1.04)/0.12))*0.5 + exp(-sq(q.x/0.95) - sq((q.y - 1.0)/0.22))*0.2;
  float3 glowC = waterAt(uScene, 0.6)*1.1;
  float edge = 1.0/(uRadius*uPixelRatio);
  float cover = 1.0 - smoothstep(1.0 - 1.5*edge, 1.0 + 0.5*edge, sqrt(r2));
  if (cover <= 0.0) return finish(glowC*glowA, glowA);
  float rr = min(r2, 0.9999);
  float3 n = float3(q.x, -q.y, sqrt(1.0 - rr));
  float3 v = float3(0.0, 0.0, -1.0);
  float3 t = refract(v, n, 1.0/1.45);
  float3 p = n*0.999;
  float3 col = waterAt(uScene, -t.y*0.5 + p.y*0.5);
  float tt = 0.0;
  bool found = false;
  for (int i = 0; i < 64; i++) {
    float3 s = p + t*tt;
    if (dot(s, s) > 1.02) break;
    float d = insideD(s);
    if (d < 0.0018) { found = true; break; }
    tt += max(d*0.8, 0.004);
  }
  if (found) {
    float3 s = p + t*tt;
    Hit h = inside(s);
    const float e = 0.003;
    float3 nn = normalize(float3(insideD(s + float3(e,0,0)) - insideD(s - float3(e,0,0)),
      insideD(s + float3(0,e,0)) - insideD(s - float3(0,e,0)), insideD(s + float3(0,0,e)) - insideD(s - float3(0,0,e))));
    // clay: wrapped key light, a cool fill, ambient occlusion, a soft sheen and a cool rim
    float ao = 1.0;
    for (int i = 0; i < 3; i++) {
      float hh = 0.03 + 0.06*float(i);
      ao -= max(0.0, hh - insideD(s + nn*hh))*(1.5/(1.0 + float(i)));
    }
    ao = clamp(ao, 0.5, 1.0);
    float3 L = normalize(float3(-0.55, 0.75, 0.6));
    float wrap = clamp((dot(nn, L) + 0.35)/1.35, 0.0, 1.0);
    float fill = max(dot(nn, normalize(float3(0.7, 0.1, 0.5))), 0.0);
    float3 shade = mix(h.c*float3(0.62, 0.55, 0.66), h.c, wrap);
    float3 lit = shade*(0.55 + 0.55*wrap)*ao + h.c*float3(0.1, 0.13, 0.2)*fill;
    float sheen = pow(max(dot(nn, normalize(L - t)), 0.0), 22.0);
    lit += float3(1.0, 0.96, 0.9)*sheen*0.22*ao;
    float rim = pow(1.0 - max(dot(nn, -t), 0.0), 3.0);
    lit += float3(0.55, 0.65, 0.95)*rim*0.18;
    col = mix(lit, col, smoothstep(0.8, 2.0, tt)*0.35);
  }
  // the inner bottom catches focused light
  float caustic = smoothstep(0.55, 0.98, -n.y)*smoothstep(0.6, 0.95, sqrt(rr));
  col += waterAt(uScene, 1.0)*0.5*caustic;
  // the rim bends the dark page behind it
  float rimZ = smoothstep(0.86, 0.99, sqrt(rr));
  col = mix(col, uPage + 0.04, rimZ*0.35);
  // reflection of a studio: a softbox above, a window on the right, strong at grazing angles
  float3 refl = reflect(v, n);
  float softbox = smoothstep(0.35, 0.9, refl.y)*smoothstep(-0.9, 0.2, -abs(refl.x + 0.25));
  float window = smoothstep(0.7, 0.95, refl.x)*smoothstep(-0.2, 0.3, refl.y);
  float F = 0.04 + 0.7*pow(1.0 - n.z, 3.0);
  float3 env = float3(0.85, 0.88, 0.95)*(0.15 + 0.85*softbox) + float3(0.9, 0.95, 1.0)*0.45*window;
  col = mix(col, env, F);
  col += float3(1.0)*0.75*smoothstep(0.75, 0.95, softbox)*(0.3 + 0.7*F);
  float hl = exp(-sq((q.x + 0.42)/0.1) - sq((q.y + 0.54)/0.065));
  col += float3(1.0)*0.9*hl;
  col *= 1.0 - 0.22*smoothstep(0.93, 1.0, sqrt(rr));
  col = clamp(col, 0.0, 1.0);
  float a = cover + glowA*(1.0 - cover);
  return finish(col*cover + glowC*glowA*(1.0 - cover), a);
}
`;
