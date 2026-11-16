// Object Sign-In Dissolve — one runtime effect draws the pear: a signed-distance model marched with an orthographic
// camera, so the object is a real volume that turns under the finger and is cut by a plane that belongs to the *screen*,
// not to the pear. Everything the pin does to its fruit falls out of that one plane:
//   below the plane   skin, lit by one key (upper left) + a small aubergine kicker + a Fresnel rim
//   above the plane   a shell of scan lines in world Y (magenta, yellow within 14 pt of the plane), the far wall of the
//                     pear showing through at 35 %, a faint fill so the form still reads
//   at the plane      a 3 pt seam (cream core, yellow edges) that also lights the skin just below it
// The plane is `uPlane + tan(uRoll) * x + tan(uPitchP) * z` (world, pt, y down, relative to the pear's centre), so the
// seam banks with the finger's motion and runs across the curved surface, not round it.
// The theme wipe is a disc (uWipe: centre x, y in canvas pt, radius): inside it the pear is lit for the light page.
//
// Uniforms (set per frame by the component from the timeline / the finger):
//   uC float2 pear centre, pt (canvas coords)    uAa float pt per device pixel      uP3 float 1 = convert sRGB to P3
//   uYaw float rad (turn about the pear's axis)  uScale float 1 … 1.02 (touch-down)  uKey float rad (key azimuth drift)
//   uPlane float pt  uRoll float rad  uPitchP float rad  uSeam float 0…1 (seam strength)  uTime float s (scan drift)
//   uWipe float3 (cx, cy, radius)                uLight float 0 … 1 (whole-object theme mix, Reduce Motion cross-fade)
//   uShell float3 / uSeamC float3 / uSeamCore float3  sRGB colours        uLine float2 (pitch, width) pt
// No SkSL reserved names as identifiers. Helpers are declared above their callers.

export const PEAR_SKSL = `
uniform float2 uC;
uniform float uAa;
uniform float uP3;
uniform float uYaw;
uniform float uScale;
uniform float uKey;
uniform float uPlane;
uniform float uRoll;
uniform float uPitchP;
uniform float uSeam;
uniform float uTime;
uniform float3 uWipe;
uniform float uLight;
uniform float3 uShell;
uniform float3 uSeamC;
uniform float3 uSeamCore;
uniform float2 uLine;

const float LEAN = 0.1047;   // 6 deg: the stalk leans to the right
const float TILT = -0.14;     // the top tips toward the viewer, so the calyx dimple and stalk read
const float R1 = 78.0;       // bulb radius, pt
const float R2 = 33.0;       // shoulder radius
const float HH = 198.0;      // distance between the two sphere centres
const float YB = 76.0;       // bulb centre below the pear's centre

float hash3(float3 p) {
  p = fract(p * 0.1031);
  p += dot(p, p.zyx + 31.32);
  return fract((p.x + p.y) * p.z);
}
float vnoise(float3 x) {
  float3 i = floor(x);
  float3 f = fract(x);
  f = f * f * (3.0 - 2.0 * f);
  float a = mix(hash3(i), hash3(i + float3(1.0, 0.0, 0.0)), f.x);
  float b = mix(hash3(i + float3(0.0, 1.0, 0.0)), hash3(i + float3(1.0, 1.0, 0.0)), f.x);
  float c = mix(hash3(i + float3(0.0, 0.0, 1.0)), hash3(i + float3(1.0, 0.0, 1.0)), f.x);
  float d = mix(hash3(i + float3(0.0, 1.0, 1.0)), hash3(i + float3(1.0, 1.0, 1.0)), f.x);
  return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}
float fbm3(float3 p) {
  return 0.5 * vnoise(p) + 0.25 * vnoise(p * 2.03 + 7.1) + 0.125 * vnoise(p * 4.1 + 3.3);
}

float sdRoundCone(float3 p, float r1, float r2, float h) {
  float2 q = float2(length(p.xz), p.y);
  float b = (r1 - r2) / h;
  float a = sqrt(1.0 - b * b);
  float k = dot(q, float2(-b, a));
  if (k < 0.0) return length(q) - r1;
  if (k > a * h) return length(q - float2(0.0, h)) - r2;
  return dot(q, float2(a, b)) - r1;
}
float sdSeg(float3 p, float3 a, float3 b, float ra, float rb) {
  float3 pa = p - a;
  float3 ba = b - a;
  float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h) - mix(ra, rb, h);
}
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float smax(float a, float b, float k) {
  float h = clamp(0.5 - 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) + k * h * (1.0 - h);
}

// body in object space (y down, centre of the pear at the origin)
float bodyD(float3 po) {
  float3 v = float3(po.x, YB - po.y, po.z);
  // neck bends sideways a little, waist pinched a little: a pear, not an egg
  v.x -= 0.00022 * v.y * v.y;
  float wq = (v.y - 118.0) / 60.0;
  float waist = exp(-wq * wq);
  v.xz *= 1.0 + 0.15 * waist;
  float d = sdRoundCone(v, R1, R2, HH);
  // calyx dimple at the bottom, a shallow bowl round the stalk at the top
  d = smax(d, -(length(v - float3(0.0, -R1 - 1.0, 0.0)) - 9.0), 5.0);
  d = smax(d, -(length(v - float3(-1.5, HH + R2 + 2.5, 0.0)) - 8.0), 4.0);
  d += 1.1 * (vnoise(po * 0.045) - 0.5);
  return d * 0.8;
}
float stalkD(float3 po) {
  float d1 = sdSeg(po, float3(0.0, -146.0, 0.0), float3(7.0, -170.0, 0.0), 3.6, 2.9);
  float d2 = sdSeg(po, float3(7.0, -170.0, 0.0), float3(18.0, -190.0, 0.0), 2.9, 2.3);
  return smin(d1, d2, 2.0);
}
float mapD(float3 po) {
  return min(bodyD(po), stalkD(po));
}

float3 nrm(float3 po) {
  float2 e = float2(0.7, -0.7);
  return normalize(e.xyy * mapD(po + e.xyy) + e.yyx * mapD(po + e.yyx) + e.yxy * mapD(po + e.yxy) + e.xxx * mapD(po + e.xxx));
}

float3 toLin(float3 c) { return pow(clamp(c, 0.0, 1.0), float3(2.2)); }

float3 skin(float3 po, float stalk, float3 n, float3 nw, float lt) {
  float n1 = fbm3(po * 0.03 + 11.0);
  float n2 = fbm3(po * 0.11 + 4.0);
  float up = clamp((-po.y + 150.0) / 300.0, 0.0, 1.0);          // 0 at the top, 1 at the base -> inverted below
  float top = clamp((po.y * -1.0 - 20.0) / 150.0, 0.0, 1.0);
  float bot = clamp((po.y - 100.0) / 60.0, 0.0, 1.0);
  float3 green = float3(0.470, 0.620, 0.190);
  float3 ochre = float3(0.780, 0.700, 0.270);
  float ripe = clamp(0.38 + 0.28 * (n1 - 0.5) * 2.0 + 0.30 * up + 0.30 * (nw.x * -0.4 + nw.y * -0.3), 0.0, 1.0);
  float3 base = mix(green, ochre, smoothstep(0.15, 0.95, ripe));
  // russet: netted brown round the stalk and the calyx, a few fine patches on the cheek (ridged noise = a web)
  float web = 1.0 - abs(2.0 * vnoise(po * 0.16 + 2.0) - 1.0);
  float rn = fbm3(po * 0.05 + 21.0);
  float rus = smoothstep(0.50, 0.95, 0.60 * top + 0.40 * bot + 0.60 * rn * (0.55 + 0.45 * web) + 0.06 * web - 0.12);
  float3 russet = float3(0.520, 0.340, 0.160) * (0.8 + 0.4 * n2);
  base = mix(base, russet, rus * 0.75);
  // lenticels: tiny russet freckles, two scales, denser toward the shoulder
  float3 g = po / 5.4;
  float3 gi = floor(g);
  float3 gf = fract(g) - 0.5;
  float hh = hash3(gi);
  float3 off = (float3(hash3(gi + 3.1), hash3(gi + 7.7), hash3(gi + 1.3)) - 0.5) * 0.5;
  float fd = length(gf - off) * 5.4;
  float fr = (0.55 + 0.7 * hash3(gi + 9.0));
  float spot = step(0.42, hh) * (1.0 - smoothstep(fr * 0.5, fr * 0.5 + 0.55, fd));
  base = mix(base, float3(0.36, 0.22, 0.10), spot * 0.62);
  float3 g2 = po / 12.5;
  float3 gi2 = floor(g2);
  float3 gf2 = fract(g2) - 0.5;
  float3 off2 = (float3(hash3(gi2 + 5.1), hash3(gi2 + 2.7), hash3(gi2 + 8.3)) - 0.5) * 0.4;
  float spot2 = step(0.55, hash3(gi2 + 0.7)) * (1.0 - smoothstep(0.8, 1.9, length(gf2 - off2) * 12.5));
  base = mix(base, float3(0.55, 0.40, 0.17), spot2 * 0.35);
  // the stalk is woody brown
  base = mix(base, float3(0.33, 0.21, 0.11) * (0.8 + 0.4 * n2), stalk);
  return base;
}

// the shell's scan lines: lines of constant world Y as soft tubes of light (a lighter top third, a darker underside), each
// with a faint halo, so the shell reads as light and not as a stripe texture. widen thickens them toward the rim.
float scanLine(float wy, float widen, out float tube, out float halo, out float lid) {
  float pitch = uLine.x;
  float u = (wy + uTime) / pitch;
  lid = floor(u);
  float dd = (fract(u) - 0.5) * pitch;
  float hw = uLine.y * 0.5 * widen;
  tube = clamp(dd / hw, -1.0, 1.0);
  float g = abs(dd) / (hw * 2.6);
  halo = exp(-g * g);
  return 1.0 - smoothstep(hw * 0.25, hw + uAa * 0.9, abs(dd));
}

float3 toP3(float3 lc) {
  float3 l3 = pow(lc, float3(2.2));
  float3 p3 = float3(0.8225 * l3.r + 0.1774 * l3.g, 0.0332 * l3.r + 0.9669 * l3.g, 0.0171 * l3.r + 0.0724 * l3.g + 0.8962 * l3.b);
  return pow(clamp(p3, 0.0, 1.0), float3(1.0 / 2.2));
}

float4 main(float2 xy) {
  float2 q = xy - uC;
  if (abs(q.x) > 110.0 * uScale || q.y < -215.0 * uScale || q.y > 175.0 * uScale) return float4(0.0);

  // object <- world: undo the lean and the tilt, then the pear's own turn (all about its centre)
  float cy = cos(uYaw);
  float sy = sin(uYaw);
  float cl = cos(LEAN);
  float sl = sin(LEAN);
  float ct = cos(TILT);
  float st = sin(TILT);
  float3x3 Ryi = float3x3(cy, 0.0, -sy, 0.0, 1.0, 0.0, sy, 0.0, cy);       // inverse of a turn by uYaw
  float3x3 Rxi = float3x3(1.0, 0.0, 0.0, 0.0, ct, -st, 0.0, st, ct);       // inverse of a tilt by TILT (x axis)
  float3x3 Rzi = float3x3(cl, -sl, 0.0, sl, cl, 0.0, 0.0, 0.0, 1.0);       // inverse of a lean by LEAN (z axis)
  float3x3 Minv = Ryi * (Rxi * Rzi);
  float inv = 1.0 / uScale;

  // march
  float t = 0.0;
  float dmin = 1e5;
  float3 pm = float3(0.0);
  float hit = 0.0;
  for (int i = 0; i < 100; i++) {
    float3 pw = float3(q.x, q.y, 130.0 - t);
    float3 po = Minv * (pw * inv);
    float d = mapD(po) * uScale;
    if (d < dmin) { dmin = d; pm = pw; }
    if (d < 0.02) { hit = 1.0; pm = pw; break; }
    t += d * 0.9;
    if (t > 270.0) break;
  }
  float cover = 1.0 - smoothstep(0.0, uAa * 1.25, dmin);
  float sS = uLine.x / 5.0;
  // the shell's neon bloom just outside its silhouette (premultiplied, light adds): strongest at the cut, fading with height,
  // gone below the cut. Edge pixels get it too, weighted by what the body does not cover, so no dark hairline sits between them.
  float4 bloomOut = float4(0.0);
  if (uSeam > 0.001 && dmin < 20.0 * sS && cover < 0.999) {
    float dph = pm.y - (uPlane + tan(uRoll) * pm.x + tan(uPitchP) * pm.z);
    float above = 1.0 - smoothstep(-6.0 * sS, 10.0 * sS, dph);
    float hot = exp(-max(-dph, 0.0) / (70.0 * sS));
    float gl = exp(-dmin / (5.5 * sS)) * above * uSeam * (0.28 + 0.52 * hot) * (1.0 - cover);
    float3 gc = mix(uShell, uSeamC, 0.75 * hot * hot);
    if (uP3 > 0.5) gc = toP3(gc);
    bloomOut = float4(gc * gl, gl * 0.55);
  }
  if (cover <= 0.001) return bloomOut;

  float3 pw = pm;
  float3 po = Minv * (pw * inv);
  float3 n = nrm(po);
  // normal back to world (Minv is a rotation: its transpose is the inverse; apply by dot with the columns)
  float3 nw = float3(dot(n, Minv[0]), dot(n, Minv[1]), dot(n, Minv[2]));
  float stalk = 1.0 - smoothstep(-0.4, 0.8, stalkD(po) - bodyD(po));

  // theme at this pixel: the wipe's disc is the light page
  float wd = length(xy - uWipe.xy) - uWipe.z;
  float lt = max(1.0 - smoothstep(-0.8, 0.8, wd), uLight);

  // one key, upper left; a kicker from the lower right in the page's aubergine; sky and bounce
  float ka = uKey;
  float3 Lk = normalize(float3(-0.52 * cos(ka) + 0.62 * sin(ka), -0.60, 0.62 * cos(ka) + 0.52 * sin(ka)));
  float3 V = float3(0.0, 0.0, 1.0);
  float3 H = normalize(Lk + V);
  float3 keyC = mix(float3(1.00, 0.92, 0.78), float3(1.00, 0.96, 0.88), lt) * mix(2.1, 1.5, lt);
  float3 ambHi = mix(float3(0.17, 0.12, 0.24), float3(0.95, 0.88, 0.76), lt) * mix(0.34, 0.42, lt);
  float3 ambLo = mix(float3(0.06, 0.045, 0.10), float3(0.74, 0.64, 0.50), lt) * mix(0.30, 0.38, lt);
  float3 kick = mix(float3(0.23, 0.165, 0.335), float3(0.80, 0.62, 0.50), lt) * mix(0.34, 0.20, lt);
  float3 Lk2 = normalize(float3(0.72, 0.52, 0.30));

  float3 alb = toLin(skin(po, stalk, n, nw, lt));
  float nl = dot(nw, Lk);
  float wrap = clamp((nl + 0.10) / 1.10, 0.0, 1.0);
  wrap = wrap * wrap * (3.0 - 2.0 * wrap) * 0.5 + wrap * 0.5;
  float hemi = 0.5 - 0.5 * nw.y;           // y down: up-facing normals have nw.y < 0
  float3 amb = mix(ambLo, ambHi, hemi);
  float nh = max(dot(nw, H), 0.0);
  float fres = pow(1.0 - max(dot(nw, V), 0.0), 4.0);
  float roughNoise = 0.5 + 0.5 * vnoise(po * 0.09);
  float spec = pow(nh, mix(22.0, 60.0, roughNoise)) * mix(0.62, 0.30, roughNoise) * (0.6 + 0.8 * fres);
  // occlusion: under the shoulder and in the dimples
  float ao = clamp(0.5 + 0.5 * (mapD(po + n * 9.0) / 9.0), 0.0, 1.0) * clamp(0.6 + 0.4 * (mapD(po + n * 26.0) / 26.0), 0.0, 1.0);
  float3 lin = alb * (keyC * wrap * 1.0 + amb * ao + kick * max(dot(nw, Lk2), 0.0));
  lin += keyC * spec * (0.5 + 0.5 * wrap) * (1.0 - stalk * 0.7);
  lin += mix(float3(0.30, 0.20, 0.42), float3(0.9, 0.7, 0.55), lt) * fres * 0.16 * (1.0 - lt * 0.5);

  // the plane: banked, so the seam runs across the curved surface
  float dp = pw.y - (uPlane + tan(uRoll) * pw.x + tan(uPitchP) * pw.z);
  float sol = smoothstep(-1.1, 1.1, dp);
  // the seam lights the skin just below it
  float below = max(dp, 0.0);
  lin += toLin(uSeamC) * 0.35 * uSeam * exp(-below / 18.0) * sol;

  float3 solid = lin / (1.0 + 0.16 * lin);
  float3 col = pow(solid, float3(1.0 / 2.2));
  float alpha = 1.0;

  if (sol < 0.999 && uSeam > 0.001) {
    float up = max(-dp, 0.0);                                           // distance above the cut, pt
    float heat = exp(-up / (46.0 * sS));                                // 1 at the cut, cooling with height
    float nearC = exp(-up / (9.0 * sS));
    float rim = pow(1.0 - max(dot(nw, V), 0.0), 2.0);
    float tube;
    float halo;
    float lid;
    float ln = scanLine(pw.y, 1.0 + 0.9 * rim, tube, halo, lid);
    // the dissolve frontier: just above the cut the lines break into fragments that thin out with height
    float hv = vnoise(float3(pw.x / (10.0 * sS), lid * 3.1, 5.0));
    float frontier = 0.12 + 0.34 * exp(-up / (34.0 * sS));
    float keep = smoothstep(frontier - 0.07, frontier + 0.07, hv);
    // colour: violet high up, magenta mid, the seam's yellow at the cut; brighter at the rim
    float3 cool = uShell * float3(0.72, 0.42, 1.0);
    float3 sh = mix(cool, uShell, smoothstep(0.0, 0.7, heat + 0.5 * rim));
    sh = mix(sh, uSeamC, clamp(nearC * 0.85 + 0.3 * heat * heat, 0.0, 1.0));
    float shade = 1.0 - 0.22 * tube;                                    // lighter top third, darker underside
    float lit = 0.55 + 0.75 * wrap;                                     // the shell is lit by the same key: the form still reads
    float3 front = sh * shade * (0.75 + 0.5 * lit) * (1.0 + 0.9 * rim) * (0.85 + 0.6 * heat);
    float fillA = 0.10 + 0.30 * rim + 0.14 * heat + 0.2 * lt;
    float3 fillC = mix(uShell * 0.45, sh, 0.5);
    float la = clamp(ln * keep + halo * 0.26 * (0.4 + 0.6 * keep), 0.0, 1.0);
    float fa = la + fillA * (1.0 - la);
    float3 fc = (front * la + fillC * fillA * (1.0 - la)) / max(fa, 0.001);
    // far wall: march inside from the front hit; its lines sit half a pitch off the front ones, so they read as depth
    float tb = t + 4.0;
    float dB = 1e5;
    float3 pb = pw;
    for (int j = 0; j < 40; j++) {
      float3 pwb = float3(q.x, q.y, 130.0 - tb);
      float3 pob = Minv * (pwb * inv);
      float db = -mapD(pob) * uScale;
      if (db < 0.12) { pb = pwb; dB = 0.0; break; }
      tb += db * 0.9;
      if (tb > 290.0) break;
    }
    float tube2;
    float halo2;
    float lid2;
    float ln2 = scanLine(pb.y + uLine.x * 0.5, 1.0, tube2, halo2, lid2);
    float haveB = 1.0 - smoothstep(0.0, 0.5, dB);
    float dpb = pb.y - (uPlane + tan(uRoll) * pb.x + tan(uPitchP) * pb.z);
    float backOn = haveB * (1.0 - smoothstep(-1.1, 1.1, dpb));
    float3 backC = cool * 0.62 * (1.0 - 0.2 * tube2);
    float ba = (ln2 * 0.42 + halo2 * 0.1 + 0.05) * backOn;
    // front over back
    float oa = fa + ba * (1.0 - fa);
    float3 oc = (fc * fa + backC * ba * (1.0 - fa)) / max(oa, 0.001);
    // seam: a 3 pt line, cream core and yellow edges, with a soft bloom to both sides
    float sm = (1.0 - smoothstep(0.6, 1.6, abs(dp))) * uSeam;
    float core = 1.0 - smoothstep(0.0, 0.9, abs(dp));
    float3 sc = mix(uSeamC, uSeamCore, core);
    float bloom = exp(-abs(dp) / (4.5 * sS)) * 0.34 * uSeam;
    oc = mix(oc, uSeamC, bloom * (1.0 - sm));
    oa = max(oa, bloom * 0.55);
    oc = mix(oc, sc, sm);
    oa = max(oa, sm);
    // blend: solid below, shell above
    float aOut = mix(oa, 1.0, sol);
    float3 cOut = (oc * oa * (1.0 - sol) + col * sol) / max(aOut, 0.001);
    col = cOut;
    alpha = aOut;
  }

  float3 lc = clamp(col, 0.0, 1.0);
  if (uP3 > 0.5) lc = toP3(lc);
  float a = alpha * cover;
  return float4(lc * a, a) + bloomOut * (1.0 - a);
}
`;
