package com.ngocdevv.nativecook.blocks.lists.clayrelightsettings

internal object ClayRelightSettingsShader {
    val HERO = """
uniform float uDensity;

uniform float4 uStage;  // width pt, height pt, pixel ratio, cloud phase (radians)
uniform float4 uSky;    // m, sun angle, moon angle, night 0 … 1
uniform float4 uLamps;  // dusk 0 … 1, windows 0 … 1, lamp post 0 … 1, twinkle phase
uniform float4 uLive;   // the finger on the sky: x pt, y pt, active 0 … 1, unused

const float PI = 3.14159265;
const float3 RO = float3(0.0, 0.8, 6.0);
const float3 AIM = float3(0.05, 0.6, 0.0);
const float HALF_W = 1.75;          // scene units across half the card at the aim's depth
const float3 ARC = float3(0.0, -0.2, -6.5);
const float2 ARC_R = float2(3.2, 2.05);
const float SUN_R = 0.36;
const float MOON_R = 0.27;
const float3 HOUSE = float3(0.38, 0.0, 0.25);
const float3 TREE = float3(-0.98, 0.0, 0.05);
const float3 TREE2 = float3(1.42, 0.0, 0.62);
const float3 POST = float3(-0.28, 0.0, 0.95);

float sq(float v) { return v*v; }
float hash(float2 p) { float3 v = fract(float3(p.x, p.y, p.x)*0.1031); v += dot(v, v.yzx + 33.33); return fract((v.x + v.y)*v.z); }
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0);
  return mix(b, a, h) - k*h*(1.0 - h);
}
float sdBox(float3 p, float3 b, float r) {
  float3 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}
float sdCapsule(float3 p, float h, float r) { p.y -= clamp(p.y, 0.0, h); return length(p) - r; }
float sdEllipsoid(float3 p, float3 r) {
  float k0 = length(p/r), k1 = length(p/(r*r));
  return k0*(k0 - 1.0)/k1;
}

float ground(float x, float z) {
  float near = 0.06*sin(1.4*x + 0.6) + 0.04*sin(2.3*x - 0.9 + 1.1*z) - 0.03*z;
  float ridge = 0.62*exp(-sq((z + 2.8)/1.1))*(0.75 + 0.25*sin(0.8*x + 1.3));
  float far = 0.95*exp(-sq((z + 5.0)/1.5))*(0.8 + 0.2*sin(0.6*x - 0.4));
  return near + ridge + far;
}

// the house: a box with a pitched roof and a chimney; base pressed into the hill
float house(float3 p) {
  float3 q = p - float3(HOUSE.x, ground(HOUSE.x, HOUSE.z) - 0.02, HOUSE.z);
  float body = sdBox(q - float3(0.0, 0.16, 0.0), float3(0.30, 0.24, 0.24), 0.04);
  // roof: a triangle (base 0.84 wide, 0.3 tall) drawn along z, its edges rounded
  float3 r = q - float3(0.0, 0.40, 0.0);
  float2 w = float2(abs(r.x), r.y);
  float tri = max(dot(w, float2(0.58, 0.81)) - 0.243, -r.y);
  float roof = max(tri, abs(r.z) - 0.29) - 0.03;
  float chimney = sdBox(q - float3(0.17, 0.62, -0.06), float3(0.05, 0.1, 0.05), 0.02);
  return min(min(body, roof), chimney);
}
float tree(float3 p, float3 at, float s) {
  float3 q = (p - float3(at.x, ground(at.x, at.z) - 0.02, at.z))/s;
  float trunk = sdCapsule(q, 0.4, 0.05);
  float crown = smin(length(q - float3(0.0, 0.58, 0.0)) - 0.27, length(q - float3(0.17, 0.46, 0.05)) - 0.2, 0.08);
  crown = smin(crown, length(q - float3(-0.16, 0.44, 0.02)) - 0.19, 0.08);
  return min(trunk, crown)*s;
}
float post(float3 p) {
  float3 q = p - float3(POST.x, ground(POST.x, POST.z) - 0.02, POST.z);
  float pole = sdCapsule(q, 0.42, 0.018);
  float lampHead = length(q - float3(0.0, 0.46, 0.0)) - 0.055;
  return min(pole, lampHead);
}
float cloud(float3 p, float3 at) {
  float3 q = p - at;
  float d = length(q - float3(0.0, 0.04, 0.0)) - 0.22;
  d = smin(d, length(q - float3(0.24, -0.04, 0.02)) - 0.16, 0.08);
  d = smin(d, length(q - float3(-0.24, -0.05, 0.0)) - 0.15, 0.08);
  d = smin(d, sdEllipsoid(q - float3(0.0, -0.1, 0.0), float3(0.44, 0.1, 0.18)), 0.08);
  return d;
}
float3 cloudAt(float i) {
  float drift = sin(uStage.w + i*2.1)*0.18;
  return i < 0.5 ? float3(-1.25 + drift, 1.62, -3.4) : float3(1.2 + drift, 1.95, -4.4);
}

// distance and material: 1 ground, 2 house, 3 trees, 4 lamp post, 5 clouds
float2 scene(float3 p, bool clouds) {
  float g = (p.y - ground(p.x, p.z))*0.7;
  float2 hit = float2(g, 1.0);
  if (length(p - float3(HOUSE.x, 0.4, HOUSE.z)) < 0.8 + g) {
    float h = house(p);
    float j = smin(g, h, 0.025);
    hit = float2(j, h < g ? 2.0 : 1.0);
  }
  if (length(p - float3(TREE.x, 0.45, TREE.z)) < 0.8 + hit.x) {
    float t = tree(p, TREE, 1.0);
    if (t < hit.x + 0.03) hit = float2(smin(hit.x, t, 0.03), t < hit.x ? 3.0 : hit.y);
  }
  if (length(p - float3(TREE2.x, 0.3, TREE2.z)) < 0.6 + hit.x) {
    float t = tree(p, TREE2, 0.62);
    if (t < hit.x + 0.03) hit = float2(smin(hit.x, t, 0.03), t < hit.x ? 3.0 : hit.y);
  }
  if (length(p - float3(POST.x, 0.3, POST.z)) < 0.5 + hit.x) {
    float l = post(p);
    if (l < hit.x) hit = float2(l, 4.0);
  }
  if (clouds && p.y > 1.0) {
    float c = min(cloud(p, cloudAt(0.0)), cloud(p, cloudAt(1.0)));
    if (c < hit.x) hit = float2(c, 5.0);
  }
  return hit;
}
float3 normalAt(float3 p) {
  const float e = 0.002;
  const float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*scene(p + k.xyy*e, true).x + k.yyx*scene(p + k.yyx*e, true).x
    + k.yxy*scene(p + k.yxy*e, true).x + k.xxx*scene(p + k.xxx*e, true).x);
}
float softShadow(float3 p, float3 l) {
  float res = 1.0, t = 0.03;
  for (int i = 0; i < 24; i++) {
    float d = scene(p + l*t, false).x;
    res = min(res, 9.0*d/t);
    t += clamp(d, 0.03, 0.35);
    if (res < 0.02 || t > 4.0) break;
  }
  return clamp(res, 0.0, 1.0);
}

float3 arcPoint(float a) { return ARC + float3(cos(a)*ARC_R.x, sin(a)*ARC_R.y, 0.0); }
float sphereHit(float3 ro, float3 rd, float3 c, float r) {
  float3 oc = ro - c;
  float b = dot(oc, rd);
  float h = b*b - dot(oc, oc) + r*r;
  return h < 0.0 ? -1.0 : -b - sqrt(h);
}

float3 skyColour(float v) {
  // v: 0 at the top of the card … 1 at its bottom
  float day = 1.0 - uLamps.x;
  float3 dayTop = float3(0.47, 0.67, 0.93), dayLow = float3(0.80, 0.89, 0.97);
  float3 duskTop = float3(0.45, 0.39, 0.68), duskLow = float3(1.0, 0.63, 0.43);
  float3 nightTop = float3(0.035, 0.05, 0.13), nightLow = float3(0.13, 0.15, 0.32);
  float k = smoothstep(0.0, 0.62, v);
  float3 c = mix(mix(duskTop, duskLow, k), mix(dayTop, dayLow, k), day*(1.0 - uSky.w));
  return mix(c, mix(nightTop, nightLow, k), uSky.w);
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 main(float2 pixelPosition) {
  float2 xy = pixelPosition / uDensity;
  float W = uStage.x, H = uStage.y;
  float F = (0.5*W)*length(AIM - RO)/HALF_W;
  float3 fwd = normalize(AIM - RO);
  float3 right = normalize(cross(fwd, float3(0.0, 1.0, 0.0)));
  float3 up = cross(right, fwd);
  float2 s = float2(xy.x - 0.5*W, 0.5*H - xy.y);
  float3 rd = normalize(fwd*F + right*s.x + up*s.y);
  float night = uSky.w;
  float duskK = uLamps.x;

  float sunA = uSky.y, moonA = uSky.z;
  float sunE = sin(sunA), moonE = sin(moonA);
  float3 sunP = arcPoint(sunA), moonP = arcPoint(moonA);
  float grab = uLive.z;

  // the lights the clay sees: the sun (warm, orange near the hill), then the moon (cool)
  float3 Ls = normalize(float3(cos(sunA), sunE + 0.08, 0.55));
  float3 Lm = normalize(float3(cos(moonA), moonE + 0.08, 0.55));
  float Is = smoothstep(0.3, 0.62, sunE);
  float Im = 0.42*smoothstep(0.0, 0.35, moonE);
  float3 sunCol = mix(float3(1.0, 0.56, 0.3), float3(1.0, 0.95, 0.86), smoothstep(0.42, 0.8, sunE));
  float3 moonCol = float3(0.55, 0.66, 1.0);

  // march
  float t = 0.0;
  float2 hit = float2(1.0, 0.0);
  bool found = false;
  // above the ridges only the clouds can be met: skip the march for rays that miss them
  bool open = rd.y > 0.03 && sphereHit(RO, rd, cloudAt(0.0), 0.62) < 0.0 && sphereHit(RO, rd, cloudAt(1.0), 0.62) < 0.0;
  for (int i = 0; i < 90; i++) {
    if (open) break;
    float3 p = RO + rd*t;
    hit = scene(p, true);
    if (hit.x < 0.0012*t) { found = true; break; }
    t += hit.x*0.85;
    if (t > 20.0 || (p.y > 2.4 && rd.y > 0.0)) break;
  }
  // grazing rays along a ridge can run out of steps just short of it: that is still the ridge
  if (!found && !open && t < 20.0 && hit.x < 0.02*t) found = true;
  float tSun = sphereHit(RO, rd, sunP, SUN_R*(1.0 + 0.08*grab));
  float tMoon = sphereHit(RO, rd, moonP, MOON_R);
  float v = xy.y/H;
  float3 col;

  if (!found || (tSun > 0.0 && tSun < t) || (tMoon > 0.0 && tMoon < t)) {
    col = skyColour(v);
    // stars, only in the night sky
    float2 cell = floor(xy/9.0);
    float hs = hash(cell);
    if (hs > 0.9 && night > 0.0) {
      float2 c = (cell + 0.5 + 0.3*(float2(hash(cell + 7.1), hash(cell + 3.3)) - 0.5))*9.0;
      float tw = 0.6 + 0.4*sin(uLamps.w*(1.0 + floor(hs*20.0 - 18.0)) + hs*40.0);
      col += float3(0.9, 0.92, 1.0)*exp(-dot(xy - c, xy - c)/0.9)*night*tw*(1.0 - smoothstep(0.35, 0.7, v));
    }
    // the sun's halo and disc
    float3 toSun = normalize(sunP - RO);
    float glow = pow(max(dot(rd, toSun), 0.0), 900.0);
    float halo = pow(max(dot(rd, toSun), 0.0), 60.0);
    col += sunCol*(0.38*halo*(0.6 + 0.9*duskK + 0.5*grab) + 0.3*glow)*smoothstep(0.15, 0.45, sunE);
    float3 toMoon = normalize(moonP - RO);
    col += float3(0.55, 0.62, 0.9)*0.35*pow(max(dot(rd, toMoon), 0.0), 120.0)*night;
    if (tSun > 0.0 && (!found || tSun < t) && (tMoon < 0.0 || tSun < tMoon)) {
      float3 n = normalize(RO + rd*tSun - sunP);
      float rim = 1.0 - max(dot(n, -rd), 0.0);
      float3 disc = mix(float3(1.0, 0.93, 0.62), float3(1.0, 0.55, 0.22), duskK);
      col = disc*(1.05 - 0.25*rim) + float3(1.0, 0.98, 0.9)*pow(max(dot(n, normalize(float3(-0.4, 0.6, 1.0))), 0.0), 18.0)*0.4;
    } else if (tMoon > 0.0 && (!found || tMoon < t)) {
      float3 n = normalize(RO + rd*tMoon - moonP);
      float lit = clamp(dot(n, normalize(float3(-0.55, 0.35, 0.75))), 0.0, 1.0);
      float2 q = n.xy;
      float crater = 0.1*(1.0 - smoothstep(0.1, 0.16, length(q - float2(-0.25, 0.2))))
        + 0.08*(1.0 - smoothstep(0.06, 0.11, length(q - float2(0.28, -0.1))))
        + 0.07*(1.0 - smoothstep(0.05, 0.09, length(q - float2(-0.05, -0.38))));
      float3 rock = float3(0.93, 0.93, 0.97) - crater;
      col = mix(col, rock*(0.28 + 0.85*lit), smoothstep(0.0, 0.25, moonE + 0.25));
    }
  } else {
    float3 p = RO + rd*t;
    float3 n = normalAt(p);
    float id = hit.y;
    float3 alb;
    float gloss = 0.25;
    float emit = 0.0;
    float3 emitCol = float3(1.0, 0.78, 0.4);
    if (id < 1.5) {
      float far = smoothstep(-1.3, -2.3, p.z);
      float wave = 0.025*sin(6.0*p.x + 2.0*p.z)*sin(4.0*p.z);
      alb = mix(float3(0.6, 0.8, 0.46) + wave, float3(0.4, 0.66, 0.55), far);
      alb = mix(alb, float3(0.6, 0.64, 0.8), smoothstep(-3.8, -4.6, p.z));
      // a path to the door
      float path = abs(p.x - HOUSE.x - 0.06*sin(3.0*p.z)) - 0.07;
      alb = mix(alb, float3(0.91, 0.8, 0.62), (1.0 - smoothstep(0.0, 0.02, path))*step(HOUSE.z + 0.22, p.z)*step(p.z, 1.6));
      gloss = 0.1;
    } else if (id < 2.5) {
      float3 q = p - float3(HOUSE.x, ground(HOUSE.x, HOUSE.z) - 0.02, HOUSE.z);
      alb = float3(0.97, 0.89, 0.76);
      if (q.y > 0.39) alb = float3(0.87, 0.42, 0.33);
      if (q.y > 0.5 && abs(q.x - 0.17) < 0.075 && abs(q.z + 0.06) < 0.075) alb = float3(0.72, 0.36, 0.3);
      // the front: two windows and a door
      if (q.z > 0.215 && q.y < 0.405) {
        float wd = max(abs(q.x + 0.14) - 0.055, abs(q.y - 0.24) - 0.06);
        float wd2 = max(abs(q.x - 0.14) - 0.055, abs(q.y - 0.24) - 0.06);
        float dd = max(abs(q.x - 0.0) - 0.05, abs(q.y - 0.1) - 0.1);
        float frame = min(wd, wd2);
        if (frame < 0.0) {
          alb = mix(float3(0.42, 0.56, 0.7), float3(1.0, 0.8, 0.45), uLamps.y);
          emit = uLamps.y;
          gloss = 0.7;
          float bar = min(abs(q.x + 0.14), abs(q.x - 0.14));
          if (bar < 0.007 || abs(q.y - 0.24) < 0.007) { alb = float3(0.97, 0.89, 0.76); emit = 0.0; }
        } else if (frame < 0.016) {
          alb = float3(0.98, 0.95, 0.9);
        }
        if (dd < 0.0) { alb = float3(0.55, 0.35, 0.24); emit = 0.0; }
      }
      if (q.z < -0.215 || abs(q.x) > 0.29) gloss = 0.2;
    } else if (id < 3.5) {
      float3 at = abs(p.x - TREE.x) < 0.8 ? TREE : TREE2;
      float s = abs(p.x - TREE.x) < 0.8 ? 1.0 : 0.62;
      float yy = (p.y - ground(at.x, at.z))/s;
      alb = yy < 0.32 ? float3(0.55, 0.38, 0.26) : float3(0.36, 0.66, 0.36) + 0.06*sin(18.0*p.y + 9.0*p.x);
      gloss = 0.2;
    } else if (id < 4.5) {
      float3 q = p - float3(POST.x, ground(POST.x, POST.z) - 0.02, POST.z);
      if (q.y > 0.4) {
        alb = mix(float3(0.95, 0.93, 0.88), float3(1.0, 0.85, 0.55), uLamps.z);
        emit = uLamps.z*1.2;
      } else {
        alb = float3(0.27, 0.29, 0.36);
        gloss = 0.5;
      }
    } else {
      alb = float3(0.98, 0.98, 1.0);
      gloss = 0.05;
    }

    // light
    float ao = 1.0;
    for (int i = 0; i < 3; i++) {
      float h = 0.03 + 0.07*float(i);
      ao -= max(0.0, h - scene(p + n*h, false).x)*(1.4/(1.0 + float(i)));
    }
    ao = clamp(ao, 0.45, 1.0);
    float3 amb = mix(mix(float3(0.95, 0.68, 0.7), float3(0.66, 0.74, 0.86), 1.0 - duskK), float3(0.13, 0.16, 0.31), night);
    float3 lightSum = amb*(0.55 + 0.45*n.y)*ao*0.62;
    if (Is > 0.001) {
      float d = clamp((dot(n, Ls) + 0.25)/1.25, 0.0, 1.0);
      lightSum += sunCol*Is*d*softShadow(p + n*0.01, Ls)*0.72;
    }
    if (Im > 0.001) {
      float d = clamp((dot(n, Lm) + 0.25)/1.25, 0.0, 1.0);
      lightSum += moonCol*Im*d*softShadow(p + n*0.01, Lm);
    }
    // warm light from the windows and the lamp on the grass and walls in front of them
    float3 wp = float3(HOUSE.x, ground(HOUSE.x, HOUSE.z) + 0.22, HOUSE.z + 0.45);
    float3 lp = float3(POST.x, ground(POST.x, POST.z) + 0.44, POST.z);
    float3 dw = wp - p, dl = lp - p;
    lightSum += float3(1.0, 0.72, 0.38)*uLamps.y*0.9*exp(-dot(dw, dw)/0.18)*clamp(dot(n, normalize(dw)) + 0.3, 0.0, 1.0);
    lightSum += float3(1.0, 0.75, 0.42)*uLamps.z*1.1*exp(-dot(dl, dl)/0.12)*clamp(dot(n, normalize(dl)) + 0.3, 0.0, 1.0);
    col = alb*lightSum;
    // soft clay sheen
    float3 hv = normalize(Ls - rd);
    col += sunCol*Is*pow(max(dot(n, hv), 0.0), 24.0)*0.18*gloss*ao;
    col += moonCol*Im*pow(max(dot(n, normalize(Lm - rd)), 0.0), 24.0)*0.25*gloss*ao;
    float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
    if (id > 1.5) col += mix(float3(0.9, 0.95, 1.0), float3(0.4, 0.5, 0.9), night)*rim*0.08*ao;
    col += emitCol*emit*0.95;
    // haze on the far ridges
    col = mix(col, skyColour(0.62), smoothstep(7.0, 14.0, t)*0.3);
  }
  // the card's lower edge fades into its band (cubic stop, no visible ramp)
  float edge = smoothstep(0.86, 1.0, v);
  col = mix(col, col*0.94, edge*edge*edge);
  return half4(half3(toP3(clamp(col, 0.0, 1.0))), 1.0);
}

    """.trimIndent()
    val ICON = """
uniform float uDensity;

uniform float4 uStage;  // width pt, height pt, pixel ratio, row pitch pt
uniform float4 uSky;    // m, sun angle, moon angle, night 0 … 1
uniform float4 uGlowA;  // lamps of rows 0 … 3
uniform float uGlowB;   // lamp of row 4
uniform float uDusk;    // 0 … 1
uniform float uTile;    // tile half size pt

const float FACE = 0.2;   // the tile's face (z), in tile units
const float RELIEF = 0.15;

float sq(float v) { return v*v; }
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0);
  return mix(b, a, h) - k*h*(1.0 - h);
}
float rbox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float seg(float2 p, float2 a, float2 b) {
  float2 pa = p - a, ba = b - a;
  return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0));
}
// a 2D shape raised from the tile's face by RELIEF, its edge rounded
float raise(float3 p, float d2, float h) {
  float2 w = float2(d2 + 0.06, abs(p.z - FACE - 0.5*h) - 0.5*h + 0.06);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.06;
}

// the symbol of each row in 2D (tile units, the tile spans -1 … 1); .y marks the lamp
float2 symbol(float2 p, float row) {
  if (row < 0.5) {
    // bell, with a round badge at its shoulder
    float dome = length(p - float2(0.0, 0.08)) - 0.36;
    float skirt = rbox(p - float2(0.0, -0.18), float2(0.36 + 0.12*clamp(-p.y - 0.05, 0.0, 0.3), 0.2), 0.08);
    float bell = smin(dome, skirt, 0.12);
    bell = min(bell, rbox(p - float2(0.0, -0.39), float2(0.54, 0.07), 0.07));
    bell = min(bell, length(p - float2(0.0, -0.56)) - 0.1);
    bell = min(bell, length(p - float2(0.0, 0.5)) - 0.08);
    float badge = length(p - float2(0.44, 0.44)) - 0.17;
    return float2(min(bell, badge), badge);
  }
  if (row < 1.5) {
    // crescent and a small star
    float c = max(length(p - float2(-0.06, 0.0)) - 0.56, -(length(p - float2(0.24, 0.17)) - 0.48));
    float star = length(p - float2(0.42, -0.36)) - 0.09;
    return float2(min(c, star), min(c, star));
  }
  if (row < 2.5) {
    // bulb: glass, neck, screw base
    float glass = smin(length(p - float2(0.0, 0.17)) - 0.4, rbox(p - float2(0.0, -0.2), float2(0.18, 0.14), 0.08), 0.12);
    float base = rbox(p - float2(0.0, -0.44), float2(0.17, 0.12), 0.05);
    base = min(base, rbox(p - float2(0.0, -0.62), float2(0.1, 0.05), 0.05));
    return float2(min(glass, base), glass);
  }
  if (row < 3.5) {
    // battery with a charging bolt cut into its face (the bolt is the lamp)
    float body = rbox(p - float2(-0.06, 0.0), float2(0.58, 0.34), 0.13);
    body = min(body, rbox(p - float2(0.6, 0.0), float2(0.07, 0.14), 0.05));
    float bolt = min(seg(p, float2(0.06, 0.24), float2(-0.14, -0.02)), seg(p, float2(-0.14, -0.02), float2(0.02, -0.02)));
    bolt = min(bolt, seg(p, float2(0.02, -0.02), float2(-0.16, -0.26))) - 0.055;
    return float2(body, bolt);
  }
  // padlock: shackle and body, the keyhole is the lamp
  float shackle = abs(length(p - float2(0.0, 0.12)) - 0.26) - 0.08;
  shackle = max(shackle, -(p.y - 0.12));
  shackle = min(shackle, max(abs(abs(p.x) - 0.26) - 0.08, abs(p.y - 0.04) - 0.1));
  float body = rbox(p - float2(0.0, -0.24), float2(0.46, 0.34), 0.12);
  float key = min(length(p - float2(0.0, -0.18)) - 0.085, rbox(p - float2(0.0, -0.32), float2(0.035, 0.1), 0.03));
  return float2(min(shackle, body), key);
}

float2 sceneAt(float3 p, float row) {
  // the tile: a rounded slab, its face domed a little
  float2 q = abs(p.xy) - float2(0.88);
  float prof = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.12;
  float dome = 0.04*(1.0 - clamp(dot(p.xy, p.xy)/2.0, 0.0, 1.0));
  float2 w = float2(prof + 0.1, abs(p.z) - FACE - dome + 0.1);
  float tile = min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.1;
  float2 s = symbol(p.xy, row);
  float sym = raise(p, s.x, RELIEF);
  float d = smin(tile, sym, 0.035);
  // the battery's bolt and the padlock's keyhole are pressed into the symbol, not raised
  if (row > 2.5) d = max(d, -max(s.y, FACE + RELIEF - 0.05 - p.z));
  return float2(d, sym < tile ? 1.0 : 0.0);
}

float3 tileColour(float row) {
  if (row < 0.5) return float3(0.96, 0.43, 0.37);
  if (row < 1.5) return float3(0.45, 0.4, 0.93);
  if (row < 2.5) return float3(0.27, 0.57, 0.98);
  if (row < 3.5) return float3(0.27, 0.77, 0.45);
  return float3(0.42, 0.48, 0.6);
}
float3 lampColour(float row) {
  if (row < 0.5) return float3(1.0, 0.36, 0.26);
  if (row < 1.5) return float3(1.0, 0.9, 0.55);
  if (row < 2.5) return float3(1.0, 0.82, 0.42);
  if (row < 3.5) return float3(0.5, 1.0, 0.55);
  return float3(0.55, 0.85, 1.0);
}
float lampOf(float row) {
  if (row < 0.5) return uGlowA.x;
  if (row < 1.5) return uGlowA.y;
  if (row < 2.5) return uGlowA.z;
  if (row < 3.5) return uGlowA.w;
  return uGlowB;
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 main(float2 pixelPosition) {
  float2 xy = pixelPosition / uDensity;
  float pitch = uStage.w;
  float row = floor(xy.y/pitch);
  if (row > 4.5) return half4(0.0);
  float2 c = float2(0.5*uStage.x, (row + 0.5)*pitch);
  float2 uv = float2(xy.x - c.x, c.y - xy.y)/uTile;
  float px = 1.0/(uTile*uStage.z);

  float night = uSky.w;
  float sunA = uSky.y, moonA = uSky.z;
  float sunE = sin(sunA), moonE = sin(moonA);
  // the same sun as the card: high, the light falls nearly straight on the tiles; low, it grazes them
  float3 Ls = normalize(float3(cos(sunA), 0.55*sunE + 0.25, 0.25 + 0.9*max(sunE, 0.0)));
  float3 Lm = normalize(float3(cos(moonA), 0.55*moonE + 0.25, 0.25 + 0.9*max(moonE, 0.0)));
  float Is = smoothstep(0.12, 0.55, sunE);
  float Im = 0.5*smoothstep(0.0, 0.35, moonE);
  float3 sunCol = mix(float3(1.0, 0.56, 0.3), float3(1.0, 0.96, 0.9), smoothstep(0.42, 0.8, sunE));
  float3 moonCol = float3(0.6, 0.7, 1.0);
  float glow = lampOf(row);
  float3 lampC = lampColour(row);

  // march along z from in front of the face
  float t = 0.0;
  float3 ro = float3(uv, 1.0);
  float2 h = float2(1.0, 0.0);
  bool hit = false;
  float closest = 9.0;
  for (int i = 0; i < 28; i++) {
    h = sceneAt(ro - float3(0.0, 0.0, t), row);
    closest = min(closest, h.x);
    if (h.x < 0.0015) { hit = true; break; }
    t += h.x;
    if (t > 1.6) break;
  }

  // under the tile: its shadow on the list, and the lamp's halo
  float2 drop = -Ls.xy/max(Ls.z, 0.2)*0.32*Is - Lm.xy/max(Lm.z, 0.2)*0.32*(Im/0.5);
  float2 dq = abs(uv - drop) - float2(0.88);
  float shape = length(max(dq, 0.0)) + min(max(dq.x, dq.y), 0.0) - 0.22;
  float soft = 0.18 + 0.25*(1.0 - max(sunE, 0.0))*(1.0 - night);
  float shadowA = (1.0 - smoothstep(-soft, soft, shape))*mix(0.26, 0.5, night)*max(Is, Im/0.5*0.8);
  float2 tq = abs(uv) - float2(0.88);
  float tileD = length(max(tq, 0.0)) + min(max(tq.x, tq.y), 0.0) - 0.22;
  float halo = glow*exp(-max(tileD, 0.0)/0.11)*0.32*night;
  float3 underRGB = lampC*halo;
  float underA = shadowA + halo*0.6;

  if (!hit) {
    float cover = 1.0 - smoothstep(0.0, px*1.2, closest);
    if (cover <= 0.0) return half4(half3(toP3(underRGB/max(underA, 0.0001))*underA), half(underA));
  }
  float3 p = ro - float3(0.0, 0.0, t);
  const float e = 0.0015;
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sceneAt(p + k.xyy*e, row).x + k.yyx*sceneAt(p + k.yyx*e, row).x
    + k.yxy*sceneAt(p + k.yxy*e, row).x + k.xxx*sceneAt(p + k.xxx*e, row).x);
  float2 s = symbol(p.xy, row);
  bool isSym = h.y > 0.5 || (s.x < 0.0 && p.z > FACE + 0.02);
  float lampHere = 1.0 - smoothstep(-0.01, 0.02, s.y);
  float3 alb = isSym ? float3(0.98, 0.97, 0.95) : tileColour(row);
  // night: the symbol's lamp lights up, its colour spills over the tile around it
  float3 emit = lampC*lampHere*glow*1.15;
  if (isSym && lampHere > 0.0) alb = mix(alb, lampC, glow*lampHere*0.7);
  float spill = glow*exp(-sq(max(s.y, 0.0))/0.05)*(1.0 - lampHere);

  // light, with shadows marched toward the sun and the moon
  float3 amb = mix(mix(float3(1.05, 0.76, 0.66), float3(0.72, 0.76, 0.84), 1.0 - uDusk), float3(0.16, 0.18, 0.3), night);
  float3 light = amb*(0.5 + 0.3*n.z);
  for (int j = 0; j < 2; j++) {
    float3 L = j == 0 ? Ls : Lm;
    float I = j == 0 ? Is : Im;
    if (I < 0.001) continue;
    float sh = 1.0, st = 0.02;
    for (int i = 0; i < 14; i++) {
      float d = sceneAt(p + n*0.004 + L*st, row).x;
      sh = min(sh, 7.0*d/st);
      st += clamp(d, 0.02, 0.15);
      if (sh < 0.02 || st > 1.2) break;
    }
    float diff = clamp((dot(n, L) + 0.2)/1.2, 0.0, 1.0);
    light += (j == 0 ? sunCol : moonCol)*I*diff*clamp(sh, 0.0, 1.0)*0.62;
  }
  float3 col = alb*light;
  float3 hv = normalize(Ls + float3(0.0, 0.0, 1.0));
  col += sunCol*Is*pow(max(dot(n, hv), 0.0), 40.0)*0.22;
  col += moonCol*Im*pow(max(dot(n, normalize(Lm + float3(0.0, 0.0, 1.0))), 0.0), 40.0)*0.3;
  col += lampC*spill*0.45*night + emit;
  col = clamp(col, 0.0, 1.0);
  float a = hit ? 1.0 : 1.0 - smoothstep(0.0, px*1.2, closest);
  float3 under = underA > 0.0 ? toP3(underRGB/max(underA, 0.0001))*underA : float3(0.0);
  return half4(half3(toP3(col)*a + under*(1.0 - a)), half(a + underA*(1.0 - a)));
}

    """.trimIndent()
}
