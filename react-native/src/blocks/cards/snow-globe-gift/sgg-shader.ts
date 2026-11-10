// Snow Globe Gift — the page and a glass snow globe in one Skia shader.
//
// The globe is a sphere of glass filled with water (exact ray–sphere intersection, refraction going in
// and out), standing on a gold base. Inside: a small gift box with a red ribbon on a mound of snow, and
// 40 flakes. Each flake has a resting place on the mound and an orbit; the swirl mixes between them
// (uSwirl), so a settled globe does not depend on how far the snow has turned, and the loop stays exact.
// Flakes are drawn as soft discs where the refracted ray passes close to them. The lid of the box can
// pop (uLid). 56 flakes. The whole globe wobbles about its base (uWobble). sRGB → Display P3.
export const SGG_SKSL = `
uniform float2 uRes;
uniform float2 uC;          // globe centre (pt)
uniform float uS;           // globe radius (pt)
uniform float uWobble;      // tilt about the base, rad
uniform float uSwirl;       // 0 settled … 1 all in the water
uniform float uSpin;        // how far the water has turned, rad
uniform float uLid;         // 0 shut … 1 popped
uniform float uGlow;        // the gift's glow when opened
uniform float uPx;

float sq(float v) { return v*v; }
float dot2(float2 v) { return dot(v, v); }
float hash(float n) { return fract(sin(n*12.9898)*43758.5453); }
float sdBox(float3 p, float3 b, float r) {
  float3 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}
float3 rz(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x - s*v.y, s*v.x + c*v.y, v.z); }
float3 rx(float3 v, float a) { float c = cos(a), s = sin(a); return float3(v.x, c*v.y - s*v.z, s*v.y + c*v.z); }

// the scene inside, in globe units (centre at the origin)
float sdMound(float3 p) { return p.y + 0.62 - 0.08*exp(-dot(p.xz, p.xz)*3.0); }
float sdGift(float3 p) { return sdBox(p - float3(0.0, -0.36, 0.0), float3(0.24, 0.2, 0.24), 0.03); }
float sdLid(float3 p) {
  float3 q = p - float3(0.0, -0.13 + 0.32*uLid, 0.0);
  q = rz(q - float3(0.26, 0.0, 0.0), -0.5*uLid) + float3(0.26, 0.0, 0.0);
  return sdBox(q, float3(0.27, 0.05, 0.27), 0.02);
}
float sdInside(float3 p) { return min(sdMound(p), min(sdGift(p), sdLid(p))); }

float3 flakePos(float i) {
  float a = hash(i), b = hash(i + 7.1), c = hash(i + 3.3);
  float3 rest = float3((a - 0.5)*1.2, -0.6 + 0.06*b, (b - 0.5)*1.0);
  float ang = a*6.2831853 + uSpin*(0.6 + 0.8*c);
  float rad = 0.15 + 0.62*b;
  float3 orbit = float3(cos(ang)*rad, -0.5 + 1.15*c + 0.12*sin(uSpin*1.7 + i), sin(ang)*rad*0.8);
  float k = smoothstep(0.0, 1.0, clamp(uSwirl*1.3 - c*0.3, 0.0, 1.0));
  return mix(rest, orbit, k);
}

float3 studio(float3 r) {
  float3 c = mix(float3(0.64, 0.6, 0.6), float3(0.95, 0.93, 0.93), smoothstep(-0.1, 0.8, r.y));
  c = mix(float3(0.38, 0.33, 0.33), c, smoothstep(-0.55, 0.02, r.y));
  c += float3(1.0)*7.0*smoothstep(0.95, 0.965, dot(r, normalize(float3(-0.45, 0.8, 0.4))));
  c += float3(1.0)*8.0*smoothstep(0.86, 0.89, r.x)*(1.0 - smoothstep(0.35, 0.45, abs(r.y - 0.1)));
  c += float3(3.5)*pow(max(dot(r, normalize(float3(-0.3, 0.4, 0.86))), 0.0), 20.0);
  return c;
}
float3 page(float2 xy) {
  float3 col = mix(float3(0.972, 0.945, 0.937), float3(0.945, 0.91, 0.9), clamp(xy.y/uRes.y, 0.0, 1.0));
  float g = exp(-dot2((xy - uC)/(uS*float2(2.2, 1.9))));
  col = mix(col, float3(0.99, 0.975, 0.97), 0.6*g);
  col += float3(1.0, 0.85, 0.6)*0.18*uGlow*exp(-dot2((xy - uC)/(uS*1.5)));
  float2 q = (xy - uC - float2(0.0, 1.32*uS))/float2(uS*1.1, uS*0.12);
  col *= 1.0 - 0.32*exp(-dot(q, q)*1.6);
  return col;
}

// shading of what the refracted ray meets inside the water
float3 insideColor(float3 ro, float3 rd, float tMax, out bool hitSomething) {
  float t = 0.0;
  hitSomething = false;
  float3 p = ro;
  for (int i = 0; i < 48; i++) {
    p = ro + rd*t;
    float d = sdInside(p);
    if (d < 0.002) { hitSomething = true; break; }
    t += d*0.9;
    if (t > tMax) break;
  }
  float3 col = float3(0.0);
  if (hitSomething) {
    const float e = 0.002;
    float dm = sdMound(p), dg = sdGift(p), dl = sdLid(p);
    float2 k = float2(1.0, -1.0);
    float3 n = normalize(k.xyy*sdInside(p + k.xyy*e) + k.yyx*sdInside(p + k.yyx*e) + k.yxy*sdInside(p + k.yxy*e) + k.xxx*sdInside(p + k.xxx*e));
    float key = clamp(dot(n, normalize(float3(-0.4, 0.8, 0.45))), 0.0, 1.0);
    float3 alb;
    if (dm < dg && dm < dl) alb = float3(0.97, 0.97, 1.0);
    else {
      // gold paper with a red ribbon crossing the box and the lid
      alb = float3(0.93, 0.74, 0.42);
      float rib = min(abs(p.x), abs(p.z));
      alb = mix(alb, float3(0.82, 0.16, 0.2), 1.0 - smoothstep(0.035, 0.045, rib));
    }
    col = alb*(0.45 + 0.6*key);
    col += float3(1.0, 0.85, 0.55)*0.5*uGlow*exp(-dot2(p.xz)*6.0)*step(dg, dm);
  }
  // flakes along the ray
  float snow = 0.0;
  float tEnd = hitSomething ? t : tMax;
  for (int i = 0; i < 56; i++) {
    float3 f = flakePos(float(i));
    float s = clamp(dot(f - ro, rd), 0.0, tEnd);
    float d = length(ro + rd*s - f);
    float r = 0.018 + 0.016*hash(float(i) + 9.7);
    snow += 0.9*(1.0 - smoothstep(r*0.55, r, d));
  }
  col = hitSomething ? col : float3(0.0);
  return mix(col, float3(1.0), clamp(snow, 0.0, 1.0));
}

float3 render(float2 xy, out float cover) {
  float2 u = (xy - uC)/uS;
  float px = uPx/uS;
  // the globe wobbles about its base (y = -1.3)
  float3 ro = float3(u.x, -u.y, 5.0);
  float3 rd = float3(0.0, 0.0, -1.0);
  ro = rz(ro + float3(0.0, 1.3, 0.0), -uWobble) - float3(0.0, 1.3, 0.0);
  rd = rz(rd, -uWobble);
  ro = rx(ro, 0.12);
  rd = rx(rd, 0.12);
  cover = 0.0;
  // the base: a gold drum under the sphere
  float3 col = float3(0.0);
  float b = dot(ro, rd);
  float c = dot(ro, ro) - 1.0;
  float disc = b*b - c;
  // base by marching a capped cone
  float tb = 0.0; float db = 1e3; bool baseHit = false; float3 pb = ro;
  for (int i = 0; i < 40; i++) {
    pb = ro + rd*tb;
    float2 q = float2(length(pb.xz), pb.y + 1.12);
    float r = 0.78 + 0.12*(q.y);
    float2 dd = abs(float2(q.x, q.y)) - float2(r, 0.2);
    float d = min(max(dd.x, dd.y), 0.0) + length(max(dd, 0.0)) - 0.02;
    db = min(db, d);
    if (d < 0.001) { baseHit = true; break; }
    tb += d*0.9;
    if (tb > 10.0) break;
  }
  float tSphere = disc > 0.0 ? -b - sqrt(disc) : 1e4;
  float covS = disc > 0.0 ? 1.0 : 0.0;
  // anti-aliased silhouette of the sphere
  float edge = length(cross(-ro, rd)) - 1.0;
  covS = 1.0 - smoothstep(-0.5*px, 1.0*px, edge);
  float covB = 1.0 - smoothstep(0.0, 1.5*px, db);
  if (covS <= 0.0 && covB <= 0.0) return float3(0.0);
  if (baseHit && (disc <= 0.0 || tb < tSphere)) {
    float3 p = pb;
    float3 n = normalize(float3(pb.x, 0.15*length(pb.xz), pb.z));
    if (abs(pb.y + 1.12) > 0.19) n = float3(0.0, sign(pb.y + 1.12), 0.0);
    float3 r = reflect(rd, n);
    float3 env = min(studio(r), float3(3.0));
    cover = covB;
    return float3(0.86, 0.66, 0.36)*(0.2 + 0.8*env)*(0.96 + 0.04*sin(atan(p.z, p.x)*90.0));
  }
  if (disc <= 0.0 && covS <= 0.0) return float3(0.0);
  float sd = sqrt(max(disc, 0.0));
  float t0 = -b - sd;
  float3 p = ro + rd*t0;
  float3 n = normalize(p);
  float cosi = clamp(dot(-rd, n), 0.0, 1.0);
  float F = 0.04 + 0.96*pow(1.0 - cosi, 5.0);
  float3 refl = studio(reflect(rd, n));
  // into the water (one interface: thin glass, water inside)
  float3 di = refract(rd, n, 1.0/1.33);
  float tMax = -2.0*dot(p, di);
  bool hit;
  float3 inner = insideColor(p + di*0.001, di, tMax, hit);
  float3 body;
  if (hit) body = inner;
  else {
    float3 q = p + di*tMax;
    float3 no = normalize(q);
    float3 dout = refract(di, -no, 1.33);
    if (dot(dout, dout) < 1e-4) dout = reflect(di, -no);
    float tp = (q.z + 1.5)/max(-dout.z, 0.25);
    float2 hp = q.xy + dout.xy*tp;
    float3 seen = page(uC + float2(hp.x, -hp.y)*uS);
    float a = clamp(length(inner), 0.0, 1.0);
    // the water: a cool, slightly deeper view of the room, so the snow reads against it
    body = mix(seen*float3(0.8, 0.85, 0.93), float3(1.0), a);
  }
  col = mix(body, refl, F);
  cover = covS;
  return col;
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 main(float2 xy) {
  float3 col = page(xy);
  float2 u = (xy - uC)/uS;
  if (abs(u.x) < 1.35 && u.y > -1.35 && u.y < 1.6) {
    float cv;
    float3 g = render(xy, cv);
    col = mix(col, g, cv);
  }
  col = col/(1.0 + 0.12*max(max(col.r, col.g), col.b) - 0.12);
  float3 o = toP3(clamp(col, 0.0, 1.0));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}
`;
