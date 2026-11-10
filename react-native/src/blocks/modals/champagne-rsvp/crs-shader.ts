// Champagne RSVP — a blush page and two champagne flutes in one Skia shader.
//
// Each flute is a solid of revolution (a tulip bowl on a thin stem and a round foot), cast in clear
// glass, half full of champagne, and turned about its foot (uRot). Rays are marched with an
// orthographic camera; through the bowl a fixed-step march gathers the wine's gold (Beer–Lambert) and
// the streams of bubbles rising from the bottom of the bowl. When the glasses clink (uClink: the age
// of the impact) a ring of light flashes where they touch and a burst of bubbles flies up out of both
// glasses (uBurst: its age), each bubble a pure function of its seed and the age. sRGB → Display P3.
export const CRS_SKSL = `
uniform float2 uRes;
uniform float2 uL;          // left flute's foot (pt)
uniform float2 uR;          // right flute's foot (pt)
uniform float uS;           // pt per unit
uniform float2 uRot;        // left and right flutes' lean, rad (positive leans right)
uniform float uClink;       // age of the clink, 0 … 1 (0 none)
uniform float2 uClinkAt;    // where the rims touched (pt)
uniform float uBurst;       // age of the burst, 0 … 1 (0 none)
uniform float uPhase;       // bubbles rising
uniform float uPx;

const float BOWL_Y0 = 1.25;
const float BOWL_Y1 = 2.65;
const float LEVEL = 2.05;
float sq(float v) { return v*v; }
float dot2(float2 v) { return dot(v, v); }
float hash(float n) { return fract(sin(n*12.9898)*43758.5453); }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }

// the bowl's outer radius at a height: a tulip, narrow at the base, widest two thirds up, closing a little at the rim
float bowlR(float y) {
  float k = clamp((y - BOWL_Y0)/(BOWL_Y1 - BOWL_Y0), 0.0, 1.0);
  return 0.06 + 0.36*sin(k*2.6)*(1.0 - 0.15*k) + 0.05*k;
}
float sdFlute(float3 p) {
  float r = length(p.xz);
  // bowl: a thin shell of glass
  float bowl = (r - bowlR(p.y))*0.8;
  bowl = max(bowl, max(BOWL_Y0 - p.y, p.y - BOWL_Y1));
  float inner = max((r - bowlR(p.y) + 0.035)*0.8, BOWL_Y0 + 0.12 - p.y);
  bowl = max(bowl, -inner);
  // stem and foot
  float stem = max(r - 0.028, max(0.06 - p.y, p.y - BOWL_Y0 - 0.05));
  float foot = max(length(float2(r, (p.y - 0.03)*4.0)) - 0.42, -p.y);
  return smin(min(bowl, stem), foot, 0.05);
}
bool inWine(float3 p) {
  float r = length(p.xz);
  return p.y > BOWL_Y0 + 0.12 && p.y < LEVEL && r < bowlR(p.y) - 0.035;
}

float3 studio(float3 r) {
  float3 c = mix(float3(0.7, 0.62, 0.6), float3(0.98, 0.95, 0.94), smoothstep(-0.1, 0.8, r.y));
  c = mix(float3(0.45, 0.38, 0.36), c, smoothstep(-0.5, 0.0, r.y));
  c += float3(1.0)*10.0*smoothstep(0.88, 0.9, r.x)*(1.0 - smoothstep(0.5, 0.6, abs(r.y)));
  c += float3(1.0)*7.0*smoothstep(0.88, 0.9, -r.x)*(1.0 - smoothstep(0.5, 0.6, abs(r.y - 0.1)));
  c += float3(1.0, 0.95, 0.9)*5.0*smoothstep(0.93, 0.96, r.y);
  return c;
}
float3 page(float2 xy) {
  float3 col = mix(float3(0.98, 0.95, 0.94), float3(0.95, 0.9, 0.89), clamp(xy.y/uRes.y, 0.0, 1.0));
  float2 mid = (uL + uR)*0.5;
  col = mix(col, float3(0.995, 0.98, 0.975), 0.5*exp(-dot2((xy - mid - float2(0.0, -1.6*uS))/(uS*float2(2.6, 2.2)))));
  // the feet's shadows, and the wine's gold light on the cloth
  for (int k = 0; k < 2; k++) {
    float2 f = k == 0 ? uL : uR;
    float2 q = (xy - f - float2(0.0, 0.02*uS))/(uS*float2(0.55, 0.07));
    col *= 1.0 - 0.3*exp(-dot(q, q)*1.4);
    col += float3(1.0, 0.8, 0.4)*0.06*exp(-dot(q*float2(0.7, 0.8), q*float2(0.7, 0.8)));
  }
  // the clink: a ring of light where the rims touched
  if (uClink > 0.0 && uClink < 1.0) {
    float d = length(xy - uClinkAt);
    float r = uS*(0.05 + 0.9*uClink);
    col += float3(1.0, 0.95, 0.85)*0.32*sq(1.0 - uClink)*exp(-sq((d - r)/(1.0 + 3.0*uClink)));
  }
  return col;
}

float3 flute(float2 xy, float2 foot, float rot, out float cover) {
  float2 u = (xy - foot)/uS;
  u.y = -u.y;
  // lean about the foot
  float c = cos(rot), s = sin(rot);
  float2 v = float2(c*u.x - s*u.y, s*u.x + c*u.y);
  float px = uPx/uS;
  float3 ro = float3(v.x, v.y, 3.0);
  float3 rd = float3(0.0, 0.0, -1.0);
  float t = 1.5;
  float dmin = 1e3;
  float tBest = t;
  for (int i = 0; i < 56; i++) {
    float d = sdFlute(ro + rd*t);
    if (d < dmin) { dmin = d; tBest = t; }
    if (d < 0.0008) break;
    t += d*0.85;
    if (t > 4.5) break;
  }
  cover = 1.0 - smoothstep(0.0, 1.3*px, dmin);
  if (cover <= 0.0) return float3(0.0);
  float3 p = ro + rd*tBest;
  const float e = 0.001;
  float2 kk = float2(1.0, -1.0);
  float3 n = normalize(kk.xyy*sdFlute(p + kk.xyy*e) + kk.yyx*sdFlute(p + kk.yyx*e) + kk.yxy*sdFlute(p + kk.yxy*e) + kk.xxx*sdFlute(p + kk.xxx*e));
  // world normal (undo the lean for the lights)
  float cr = cos(rot), sr = sin(rot);
  float3 nW = float3(cr*n.x + sr*n.y, -sr*n.x + cr*n.y, n.z);
  float cosi = clamp(-dot(rd, n), 0.0, 1.0);
  float F = 0.04 + 0.96*pow(1.0 - cosi, 5.0);
  float3 refl = studio(reflect(rd, nW));
  // through the glass and the wine
  float3 di = refract(rd, n, 1.0/1.5);
  float3 trans = float3(1.0);
  float3 glow = float3(0.0);
  float3 q = p;
  float st = 0.03;
  for (int j = 0; j < 36; j++) {
    q += di*st;
    if (length(q.xz) > 0.55) break;
    if (inWine(q)) {
      float3 gold = float3(0.97, 0.78, 0.38);
      trans *= exp(-(1.0 - gold)*3.6*st);
      glow += gold*trans*0.02;
      // bubbles: streams rising from a few points at the bottom of the bowl
      for (int b = 0; b < 3; b++) {
        float fb = float(b);
        float2 src = float2(cos(fb*2.4)*0.06, sin(fb*2.4)*0.06);
        // whole cycles per loop (6, 7, 8) so the bubbles are where they started when the loop ends
        float rise = fract(q.y*2.2 - uPhase*(1.0 + fb/6.0) + hash(fb + 1.0));
        float bub = length(q.xz - src - float2(0.01*sin(q.y*30.0 + fb), 0.0)) - 0.012*(0.6 + 0.4*rise);
        glow += float3(1.0)*trans*0.25*(1.0 - smoothstep(0.0, 0.006, bub))*step(0.55, rise);
      }
    }
    // the meniscus line
    if (length(q.xz) < bowlR(LEVEL)) glow += float3(1.0, 0.95, 0.85)*trans*0.05*exp(-sq((q.y - LEVEL)/0.012));
  }
  float3 body = page(xy)*trans + glow;
  return mix(body, refl, F);
}

// the burst: bubbles flying up out of both glasses after the clink
float3 burst(float2 xy, float3 col) {
  if (uBurst <= 0.0 || uBurst >= 1.0) return col;
  for (int k = 0; k < 2; k++) {
    float2 f = k == 0 ? uL : uR;
    float rot = k == 0 ? uRot.x : uRot.y;
    float2 rim = f + float2(sin(rot), -cos(rot))*uS*2.6;
    for (int i = 0; i < 14; i++) {
      float fi = float(i) + float(k)*31.0;
      float vx = (hash(fi) - 0.5)*1.6 + (k == 0 ? -0.2 : 0.2);
      float vy = 1.6 + 1.8*hash(fi + 3.3);
      float age = uBurst*(0.7 + 0.3*hash(fi + 7.7));
      float2 pos = rim + uS*float2(vx*age, -(vy*age - 0.9*age*age));
      float r = uS*(0.035 + 0.05*hash(fi + 5.1))*(1.0 - 0.4*age);
      float d = length(xy - pos);
      float ring = (1.0 - smoothstep(0.0, 1.2, abs(d - r)))*0.7 + (1.0 - smoothstep(r*0.2, r*0.5, length(xy - pos + r*0.35)))*0.6;
      col = mix(col, float3(0.98, 0.86, 0.6), clamp(ring, 0.0, 1.0)*(1.0 - age)*0.9);
    }
  }
  return col;
}

half4 main(float2 xy) {
  float3 col = page(xy);
  for (int k = 0; k < 2; k++) {
    float2 f = k == 0 ? uL : uR;
    float2 u = (xy - f)/uS;
    if (abs(u.x) < 1.4 && u.y < 0.2 && u.y > -2.9) {
      float cv;
      float3 g = flute(xy, f, k == 0 ? uRot.x : uRot.y, cv);
      col = mix(col, g, cv);
    }
  }
  col = burst(xy, col);
  col = col/(1.0 + 0.12*max(max(col.r, col.g), col.b) - 0.12);
  float3 l = pow(max(col, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  float3 o = pow(max(m, float3(0.0)), float3(1.0/2.2));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}
`;
