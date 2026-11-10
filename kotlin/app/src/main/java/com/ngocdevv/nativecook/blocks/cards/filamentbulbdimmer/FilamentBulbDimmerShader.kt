package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer

internal object FilamentBulbDimmerShader {
 val SOURCE="""
uniform float4 uview;
uniform float4 uanchor;
uniform float4 ubulb;
uniform float4 uglow;
uniform float4 ulight;
uniform float4 uaccent;
uniform float4 uarc;
uniform float4 ucard;
uniform float4 uchips;
uniform float4 umisc;
const float TAU = 6.2831853;
const float PI = 3.14159265;
// the bulb, in R, down from the socket's top
const float Y_SOCKET = 0.5;
const float Y_NECK = 1.12;
const float Y_GLOBE = 2.74;
const float Y_TOP = 2.16;     // the cage's top ring of hooks
const float Y_BOT = 2.98;     // its bottom ring
const float R_TOP = 0.27;
const float R_BOT = 0.43;
const float R_FIL = 0.012;
const float R_WIRE = 0.0065;
const float GLASS = 0.022;    // the envelope's thickness

float sq(float v) { return v * v; }
float3 lin(float3 c) { return pow(c, float3(2.2)); }

float seg2(float2 p, float2 a, float2 b) {
  float2 pa = p - a; float2 ba = b - a;
  return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
}
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
  return mix(b, a, h) - k * h * (1.0 - h);
}
float sdRound(float2 p, float4 r, float rad) {
  float2 c = r.xy + r.zw * 0.5;
  float2 q = abs(p - c) - r.zw * 0.5 + float2(rad);
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rad;
}

/* ------------------------------------------------------------------ the page and the light on it */

float3 pageBase(float2 p) {
  // the pin's page, #262630, a touch warmer towards the top left
  float3 c = float3(0.149, 0.149, 0.188);
  float ty = 1.0 - smoothstep(0.0, 0.5, p.y / uview.y);
  float tx = 1.0 - smoothstep(0.0, 0.8, p.x / uview.x);
  c = mix(c, float3(0.173, 0.149, 0.188), ty * tx * 0.8);
  return lin(c);
}

float2 filamentAt() {
  float R = uanchor.z;
  return ubulb.xy + float2(sin(ubulb.z), cos(ubulb.z)) * 2.55 * R;
}

// the light falling on the page (and on anything flat on it) at p
float irradiance(float2 p) {
  float R = uanchor.z;
  float zl = uanchor.w;
  float2 d = p - filamentAt();
  float d2 = dot(d, d);
  float e = zl * zl * zl / pow(d2 + zl * zl, 1.5);
  // the socket shades the light upwards a little
  float up = smoothstep(0.5, -1.0, d.y / max(sqrt(d2), 1.0));
  return e * (1.0 - 0.3 * up) + 0.035 * exp(-sqrt(d2) / (6.0 * R));
}

float3 pageLit(float2 p) {
  float3 L = ulight.xyz * uglow.z;
  return pageBase(p) + L * 0.075 * irradiance(p);
}

// what the glass and the brass reflect when they look back at the page: the page and the app on it
float3 pageSeen(float2 p) {
  float3 c = pageLit(p);
  float3 L = ulight.xyz * uglow.z * irradiance(p);
  if (sdRound(p, ucard, 24.0) < 0.0) { c = lin(float3(0.239, 0.22, 0.247)) * (1.0 + 2.0 * L); }
  float4 chip = float4(0.0, uchips.z, uchips.w, umisc.x);
  if (sdRound(p, float4(uchips.x, chip.yzw), 16.0) < 0.0 || sdRound(p, float4(uchips.y, chip.yzw), 16.0) < 0.0) {
    c = lin(float3(0.169, 0.165, 0.196)) * (1.0 + 2.0 * L);
  }
  // the dial's band glows in the accent colour, its disc is white
  float2 q = p - uarc.xy;
  float rm = (uarc.z + uarc.w) * 0.5;
  float bw = (uarc.z - uarc.w) * 0.5;
  float band = 1e5;
  if (q.y < 0.0) { band = abs(length(q) - rm) - bw; } else { band = length(float2(abs(abs(q.x) - rm), max(q.y - umisc.z, 0.0))) - bw; }
  c += uaccent.xyz * ulight.w * 1.6 * (1.0 - smoothstep(-1.0, 1.0, band)) + uaccent.xyz * ulight.w * 0.25 * exp(-max(band, 0.0) / 14.0);
  if (length(q - float2(0.0, -12.0)) < umisc.y) { c = float3(0.85); }
  return c;
}

// the room in front of the screen: dark, a tall soft window up on the left, a cool fill on the right
float3 roomFront(float3 r, float hi) {
  // a dim warm room: brighter overhead, a soft floor bounce
  float3 c = mix(lin(float3(0.1, 0.095, 0.11)), lin(float3(0.34, 0.31, 0.32)), smoothstep(0.3, -0.8, r.y));
  float3 k = normalize(float3(-0.46, -0.62, 0.64));
  float3 u = normalize(cross(k, float3(0.0, 1.0, 0.0)));
  float3 v = cross(u, k);
  float2 a = float2(dot(r, u), dot(r, v)) / max(dot(r, k), 0.05);
  float win = (1.0 - smoothstep(0.15, 0.2, abs(a.x))) * (1.0 - smoothstep(0.27, 0.33, abs(a.y))) * step(0.0, dot(r, k));
  // a tall window in two panes
  c += float3(1.0, 0.97, 0.93) * 16.0 * hi * win * smoothstep(0.012, 0.03, abs(a.x));
  // a broad soft glow round it, and a cool fill on the right
  c += lin(float3(0.5, 0.48, 0.5)) * 0.6 * smoothstep(0.7, 0.97, dot(r, k));
  float3 f = normalize(float3(0.85, -0.05, 0.52));
  c += lin(float3(0.45, 0.5, 0.62)) * 1.2 * smoothstep(0.88, 0.99, dot(r, f));
  return c;
}

float3 env(float3 o, float3 r, float hi) {
  if (r.z < -0.02) {
    float t = -o.z / r.z;
    return pageSeen(o.xy + r.xy * t);
  }
  return roomFront(r, hi);
}

/* ------------------------------------------------------------------ the cord */

float cordDist(float2 p) {
  float2 a = uanchor.xy;
  float2 b = ubulb.xy;
  float2 c = (a + b) * 0.5 + float2(0.0, uglow.x * 2.0);
  float dmin = 1e5;
  float2 prev = a;
  for (int i = 1; i <= 10; i++) {
    float t = float(i) / 10.0;
    float2 q = mix(mix(a, c, t), mix(c, b, t), t);
    dmin = min(dmin, seg2(p, prev, q));
    prev = q;
  }
  return dmin;
}

/* ------------------------------------------------------------------ the bulb, in its own frame (R) */

float glassProfile(float r, float y) {
  float sph = length(float2(r, y - Y_GLOBE)) - 1.0;
  float neck = seg2(float2(r, y), float2(0.0, Y_NECK), float2(0.0, Y_GLOBE - 0.5)) - 0.3;
  return max(smin(sph, neck, 0.45), Y_NECK - 0.02 - y);
}
float glassSdf(float3 p) { return glassProfile(length(p.xz), p.y); }
float3 glassNormal(float3 p) {
  float r = max(length(p.xz), 1e-4);
  float e = 0.002;
  float gr = glassProfile(r + e, p.y) - glassProfile(r - e, p.y);
  float gy = glassProfile(r, p.y + e) - glassProfile(r, p.y - e);
  return normalize(float3(gr * p.x / r, gy, gr * p.z / r));
}

// the socket (0) and the threaded cap (1): x distance, y material
float2 metalSdf(float3 p) {
  float r = length(p.xz);
  float a = atan(p.z, p.x);
  float rs = 0.4;
  rs += 0.007 * smoothstep(0.09, 0.1, p.y) * (1.0 - smoothstep(0.19, 0.2, p.y)) * abs(sin(a * 28.0));
  rs -= 0.014 * (exp(-sq((p.y - 0.33) / 0.012)) + exp(-sq((p.y - 0.42) / 0.012)));
  float2 qs = float2(r - (rs - 0.06), abs(p.y - 0.25) - 0.19);
  float socket = length(max(qs, float2(0.0))) + min(max(qs.x, qs.y), 0.0) - 0.06;
  float th = 1.0 - abs(fract((p.y - Y_SOCKET) * 9.0 + a / TAU) * 2.0 - 1.0);
  float rc = 0.322 + 0.026 * smoothstep(0.12, 0.88, th);
  float2 qc = float2(r - rc, abs(p.y - 0.81) - 0.31);
  float cap = length(max(qc, float2(0.0))) + min(max(qc.x, qc.y), 0.0);
  if (socket < cap) { return float2(socket, 0.0); }
  return float2(cap, 1.0);
}
float3 metalNormal(float3 p) {
  float e = 0.0025;
  float3 k1 = float3(1.0, -1.0, -1.0); float3 k2 = float3(-1.0, -1.0, 1.0); float3 k3 = float3(-1.0, 1.0, -1.0); float3 k4 = float3(1.0, 1.0, 1.0);
  return normalize(k1 * metalSdf(p + k1 * e).x + k2 * metalSdf(p + k2 * e).x + k3 * metalSdf(p + k3 * e).x + k4 * metalSdf(p + k4 * e).x);
}

// the closest approach of a ray (o + t d, |d| = 1) to the segment a-b: (distance, t)
float2 raySeg(float3 o, float3 dir, float3 a, float3 b) {
  float3 ba = b - a; float3 oa = o - a;
  float dd = dot(dir, ba); float bb = dot(ba, ba);
  float od = dot(oa, dir); float ob = dot(oa, ba);
  float u = clamp((ob - od * dd) / max(bb - dd * dd, 1e-6), 0.0, 1.0);
  float t = u * dd - od;
  return float2(length(oa + dir * t - ba * u), t);
}
float3 hookTop(int i) { float a = float(i) * TAU / 8.0; return float3(R_TOP * cos(a), Y_TOP, R_TOP * sin(a)); }
float3 hookBot(int i) { float a = (float(i) + 0.5) * TAU / 8.0; return float3(R_BOT * cos(a), Y_BOT, R_BOT * sin(a)); }
float3 strandA(int k) { if ((k - (k / 2) * 2) == 0) { return hookTop(k / 2); } return hookBot(k / 2); }
float3 strandB(int k) { if ((k - (k / 2) * 2) == 0) { return hookBot(k / 2); } return hookTop((k / 2 + 1) - ((k / 2 + 1) / 8) * 8); }

struct Inside { float3 col; float hit; };

// what a ray sees between t0 and t1 inside the globe: strands, wires, stem, and the glow round them
Inside inside(float3 o, float3 dir, float t0, float t1, float E, float3 Lc, float lum) {
  float tHit = t1;
  int kind = 0;
  float glow = 0.0;
  float near = 1e5;
  for (int k = 0; k < 16; k++) {
    float2 h = raySeg(o, dir, strandA(k), strandB(k));
    if (h.y > t0 && h.y < t1) {
      glow += exp(-h.x / 0.035);
      if (h.x < R_FIL && h.y < tHit) { tHit = h.y; kind = 1; near = h.x; }
    }
  }
  for (int i = 0; i < 8; i++) {
    float a = float(i) * TAU / 8.0;
    float b = (float(i) + 0.5) * TAU / 8.0;
    float2 w1 = raySeg(o, dir, float3(0.05 * cos(a), Y_TOP - 0.03, 0.05 * sin(a)), hookTop(i));
    if (w1.x < R_WIRE && w1.y > t0 && w1.y < tHit) { tHit = w1.y; kind = 2; }
    float2 w2 = raySeg(o, dir, float3(0.05 * cos(b), Y_BOT + 0.02, 0.05 * sin(b)), hookBot(i));
    if (w2.x < R_WIRE && w2.y > t0 && w2.y < tHit) { tHit = w2.y; kind = 2; }
  }
  float3 col = float3(0.0);
  if (kind == 1) {
    // the strand's core: white-hot in its middle when hot
    float core = 1.0 - near / R_FIL;
    col = mix(Lc, float3(1.0, 0.96, 0.9), 0.45 * uglow.y * core) * E * (0.55 + 0.45 * core);
  } else if (kind == 2) {
    float3 pw = o + dir * tHit;
    float hot = exp(-abs(pw.y - (Y_TOP + Y_BOT) * 0.5) * 1.5);
    col = lin(float3(0.22, 0.2, 0.19)) * 0.25 + Lc * (0.4 * lum + 0.05 * E) * hot;
  }
  // the glass stem in the middle, from the neck to the bottom ring: bright along its edges
  float2 st = raySeg(o, dir, float3(0.0, 1.32, 0.0), float3(0.0, Y_BOT + 0.04, 0.0));
  if (st.x < 0.045 && st.y > t0 && st.y < tHit) {
    float c = sqrt(max(1.0 - sq(st.x / 0.045), 0.0));
    float fr = 0.04 + 0.96 * pow(1.0 - c, 5.0);
    col = col * (1.0 - 0.3 * fr) + (Lc * (0.06 * E + 0.6 * lum) + float3(0.2)) * fr;
  }
  col += Lc * E * glow * 0.012;
  Inside r;
  r.col = col;
  r.hit = (kind != 0 ? 1.0 : 0.0);
  return r;
}

// the filament mirrored in the inner face of the glass
float3 mirrored(float3 o, float3 dir, float E, float3 Lc) {
  float md = 1e5;
  for (int k = 0; k < 16; k++) {
    float2 h = raySeg(o, dir, strandA(k), strandB(k));
    if (h.y > 0.01) { md = min(md, h.x); }
  }
  return Lc * E * (0.7 * (1.0 - smoothstep(R_FIL * 0.6, R_FIL * 2.0, md)) + 0.05 * exp(-md / 0.05));
}

struct Hit { float3 col; float a; };

Hit bulb(float2 p) {
  Hit result;
  result.col = float3(0.0);
  result.a = 0.0;
  float R = uanchor.z;
  float zl = uanchor.w;
  float tilt = ubulb.z;
  float ct = cos(tilt); float st = sin(tilt);
  float2 axis2 = float2(st, ct);
  float2 side2 = float2(ct, -st);
  float2 lp = p - ubulb.xy;
  float lx = dot(lp, side2) / R;
  float ly = dot(lp, axis2) / R;
  if (abs(lx) > 1.12 || ly < -0.05 || ly > 3.82) { return result; }

  // world: x right, y down, z towards the viewer; the bulb hangs zl in front of the page
  float cb = cos(ubulb.w); float sb = sin(ubulb.w);
  float3 axis = float3(axis2, 0.0);
  float3 side = float3(side2, 0.0) * cb + float3(0.0, 0.0, 1.0) * sb;
  float3 fwd = float3(side2, 0.0) * -sb + float3(0.0, 0.0, 1.0) * cb;
  float3 H = float3(ubulb.xy, zl);
  float3 centre = H + axis * Y_GLOBE * R;
  float D = 14.0 * R;
  float3 row = float3(centre.xy, centre.z + D);
  float3 rdw = normalize(float3(p - centre.xy, -D));
  float3 toL = row - H;
  float3 o = float3(dot(toL, side), dot(toL, axis), dot(toL, fwd)) / R;
  float3 dir = normalize(float3(dot(rdw, side), dot(rdw, axis), dot(rdw, fwd)));
  // the bounding cylinder round the axis
  float qa = dir.x * dir.x + dir.z * dir.z;
  float qb = 2.0 * (o.x * dir.x + o.z * dir.z);
  float qc = o.x * o.x + o.z * o.z - 1.1 * 1.1;
  float disc = qb * qb - 4.0 * qa * qc;
  if (disc < 0.0) { return result; }
  float sd = sqrt(disc);
  float t = max((-qb - sd) / (2.0 * qa), 0.0);
  float tEnd = (-qb + sd) / (2.0 * qa);
  float mat = -1.0;
  float md = 0.0;
  for (int i = 0; i < 48; i++) {
    float3 q = o + dir * t;
    float2 m = metalSdf(q);
    float g = glassSdf(q);
    float dist = min(m.x, g);
    if (dist < 0.0012) { if (m.x < g) { mat = m.y; } else { mat = 2.0; } md = dist; break; }
    t += max(dist * 0.8, 0.0015);
    if (t > tEnd) { break; }
  }
  if (mat < 0.0) { return result; }

  float3x3 toWorld = float3x3(side, axis, fwd);
  float3 q = o + dir * t;
  float3 qw = H + toWorld * (q * R);
  float3 Lc = ulight.xyz;
  float lum = uglow.z;
  float E = uglow.w;
  float3 fil = float3(0.0, 2.55, 0.0);
  result.a = 1.0;
  if (mat < 1.5) {
    // brass: the cap bright and new, the socket aged; lit by the filament below through the neck
    float3 n = metalNormal(q);
    float3 nw = toWorld * n;
    float3 v = -rdw;
    float3 F0 = (mat > 0.5 ? lin(float3(0.98, 0.8, 0.5)) : lin(float3(0.62, 0.46, 0.3)));
    float3 fr = F0 + (float3(1.0) - F0) * pow(1.0 - max(dot(nw, v), 0.0), 5.0);
    float3 rw = reflect(rdw, nw);
    float3 c = env(qw, rw, 1.0) * fr;
    float3 toF = fil - q;
    float3 lf = normalize(toF);
    float att = 1.0 / (1.0 + sq(length(toF) * 0.9));
    float diff = max(dot(n, lf), 0.0);
    float spec = pow(max(dot(reflect(-lf, n), normalize(float3(dot(v, side), dot(v, axis), dot(v, fwd)))), 0.0), 30.0);
    c += F0 * Lc * (0.03 * E + 0.6 * lum) * diff * att + Lc * (0.04 * E + 0.6 * lum) * spec * att;
    // the threads' grooves stay dark
    c *= 0.72 + 0.28 * smoothstep(0.0, 0.02, metalSdf(q + n * 0.02).x);
    result.col = c;
    return result;
  }

  // glass: reflect the room, see in, see the far side
  float3 n1 = glassNormal(q);
  float3 n1w = toWorld * n1;
  float cos1 = abs(dot(n1w, rdw));
  float F1 = 0.04 + 0.96 * pow(1.0 - cos1, 5.0);
  float3 refl = env(qw, reflect(rdw, n1w), 3.0);
  // through the shell into the bulb, then result through its far wall
  float t2 = t + GLASS * 2.0;
  for (int i = 0; i < 32; i++) {
    float d2 = -(glassSdf(o + dir * t2) + GLASS);
    if (d2 < 0.001) { break; }
    t2 += max(d2 * 0.9, 0.002);
    if (t2 > tEnd + 1.0) { break; }
  }
  float3 q2 = o + dir * t2;
  Inside inn = inside(o, dir, t, t2, E, Lc, lum);
  float3 tint = float3(0.985, 0.965, 0.93);
  float3 c = inn.col;
  if (inn.hit < 0.5) {
    float3 n2 = glassNormal(q2);
    float F2 = 0.04 + 0.96 * pow(1.0 - abs(dot(n2, dir)), 5.0);
    float3 back = mirrored(q2, reflect(dir, n2), E, Lc);
    float3 q2w = H + toWorld * (q2 * R);
    float2 S = q2w.xy + rdw.xy * (q2w.z / -rdw.z);
    float3 behind = pageLit(S) * tint;
    // looking up the neck: the dark cement under the cap
    behind = mix(behind, lin(float3(0.12, 0.09, 0.07)) * (0.2 + Lc * (0.5 * lum)), smoothstep(Y_NECK + 0.35, Y_NECK + 0.05, q2.y));
    c += F2 * back + (1.0 - F2) * behind;
  }
  float3 shell = Lc * lum * (0.012 + 0.16 * pow(1.0 - cos1, 3.0)) + Lc * E * 0.0015;
  result.col = F1 * refl + (1.0 - F1) * tint * c + shell;
  // the cap's rim where it meets the glass catches the light
  return result;
}


half4 main(float2 p) {
 float3 c=pageLit(p);float R=uanchor.z;float lum=uglow.z;float3 Lc=ulight.xyz;
 float cd=cordDist(p);float px=1.0/uview.z;
 c=mix(c,lin(float3(.36,.345,.38))+Lc*lum*.04,(1.0-smoothstep(.62-px,.62+px,cd))*.92);
 Hit b=bulb(p);c=mix(c,b.col,b.a);
 float h=length(p-filamentAt());c+=Lc*lum*.022*R/(h+.32*R)*exp(-h/(7.0*R));
 // Cheap broad glow replaces the six HDR bloom levels on Android.
 c+=Lc*lum*.018*exp(-h/(.65*R));
 float m=max(c.r,max(c.g,c.b));if(m>.75){float m2=.75+.25*(1.0-exp(-(m-.75)/.25));c*=m2/m;c=mix(c,float3(m2),clamp((m-1.0)/6.0,0.0,1.0)*.8);}
 return half4(pow(max(c,float3(0)),float3(1.0/2.2)),1);
}

""".trimIndent()
 val FALLBACK="""

uniform float2 uRes;
uniform float2 uAnchor;     // where the cord leaves the ceiling (pt)
uniform float uCord;        // cord length to the top of the cap (pt)
uniform float uR;           // the bulb's radius (pt)
uniform float uSwing;       // rad
uniform float uPull;        // pt
uniform float uPower;       // 0 off … 1 full (brightness included)
uniform float3 uTint;       // the light's colour at full power
uniform float uPx;

float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float seg(float2 p, float2 a, float2 b) { float2 pa = p - a, ba = b - a; return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0)); }

// the filament's colour for a power: dull red, orange, then the chosen tint, brighter than 1
float3 glowColor(float p) {
  float3 c = mix(float3(0.9, 0.2, 0.02), float3(1.0, 0.5, 0.12), smoothstep(0.0, 0.4, p));
  return mix(c, uTint, smoothstep(0.08, 0.5, p));
}

float3 room(float2 xy, float2 bulbC) {
  // the pin's page: #262630, a touch warmer towards the top left
  float3 col = float3(0.149, 0.149, 0.188);
  col = mix(col, float3(0.173, 0.149, 0.188), (1.0 - smoothstep(0.0, 0.5, xy.y/uRes.y))*(1.0 - smoothstep(0.0, 0.8, xy.x/uRes.x))*0.8);
  float p = uPower;
  float3 light = glowColor(p);
  // a soft cone of light falling from the bulb onto the dial below it
  float2 d = xy - bulbC;
  float down = d.y/uR;
  float cone = smoothstep(0.6, 1.6, down)*(1.0 - smoothstep(2.6, 5.2, down))*(1.0 - smoothstep(0.9 + 0.35*down, 1.4 + 0.5*down, abs(d.x)/uR));
  col += light*p*0.045*cone;
  // the pool round the bulb
  float r = length(d)/uR;
  col += light*p*(0.13*exp(-sq(r/1.6)) + 0.018*exp(-r/4.0));
  return col;
}

half4 main(float2 xy) {
  // the bulb's frame: rotate about the anchor, y down the cord
  float c = cos(uSwing), s = sin(uSwing);
  float2 d0 = xy - uAnchor;
  float2 p = float2(c*d0.x + s*d0.y, -s*d0.x + c*d0.y);
  float R = uR;
  float capTop = uCord + uPull;
  float sockTop = capTop - 0.5*R;
  float capH = 0.62*R;
  float neckY = capTop + capH;
  float2 cen = float2(0.0, neckY + 0.62*R + R);
  float2 bulbScreen = uAnchor + float2(c*cen.x - s*cen.y, s*cen.x + c*cen.y);

  float3 col = room(xy, bulbScreen);

  // the cord
  float cord = max(abs(p.x) - 0.6, max(-p.y, p.y - sockTop));
  if (cord < 1.0) col = mix(col, float3(0.5, 0.48, 0.52), 0.85*(1.0 - smoothstep(-0.7, 0.4, cord)));
  // the socket: aged brass, rounded at the top
  float2 sp = p - float2(0.0, sockTop + 0.25*R);
  float2 sq2 = abs(sp) - float2(0.34*R, 0.19*R);
  float sock = length(max(sq2, float2(0.0))) + min(max(sq2.x, sq2.y), 0.0) - 0.06*R;
  if (sock < 1.0) {
    float u = clamp(sp.x/(0.4*R), -1.0, 1.0);
    float3 aged = mix(float3(0.2, 0.15, 0.08), float3(0.72, 0.56, 0.34), 0.35 + 0.65*pow(1.0 - abs(u + 0.3)/1.3, 2.0));
    col = mix(col, aged, 1.0 - smoothstep(-0.6, 0.6, sock));
  }

  // the cap: a brass screw base with threads, a little dome on top
  float2 cp = p - float2(0.0, capTop + capH*0.5);
  float cap = max(abs(cp.x) - 0.3*R + 0.02*R*sin(cp.y/R*44.0), abs(cp.y) - capH*0.5);
  cap = min(cap, length((p - float2(0.0, capTop + 0.04*R))/float2(0.22*R, 0.1*R)) - 1.0);
  if (cap < 1.0) {
    float u = clamp(cp.x/(0.3*R), -1.0, 1.0);
    float shade = 0.45 + 0.55*pow(1.0 - abs(u + 0.3)/1.3, 2.0);
    float3 brass = mix(float3(0.28, 0.2, 0.09), float3(0.95, 0.78, 0.45), shade);
    brass *= 0.8 + 0.2*sin(cp.y/R*44.0);
    brass += glowColor(uPower)*uPower*0.25*smoothstep(-capH*0.5, capH*0.5, cp.y);
    col = mix(col, brass, 1.0 - smoothstep(-0.6, 0.6, cap));
  }

  // the glass: a round bulb melted into the neck under the cap
  float sph = length(p - cen) - R;
  float neck = seg(p, float2(0.0, neckY), float2(0.0, cen.y - 0.5*R)) - 0.3*R;
  float glass = smin(sph, neck, 0.45*R);
  if (glass < 1.5) {
    float2 q = (p - cen)/R;
    float r = clamp(length(q), 0.0, 1.0);
    float nz = sqrt(max(1.0 - r*r, 0.0));
    // the room behind, magnified towards the middle
    float2 src = xy - (xy - bulbScreen)*0.18*nz;
    float3 g = room(src, bulbScreen)*0.9;
    // the glass fills with warm haze when lit
    float3 lc = glowColor(uPower);
    g += lc*uPower*(0.07 + 0.13*nz);
    // the filament: support wires, then hairpin loops of a squirrel cage
    float2 f = q*0.78;
    float wires = min(seg(f, float2(-0.08, -1.25), float2(-0.32, 0.12)), seg(f, float2(0.08, -1.25), float2(0.32, 0.12)));
    wires = min(wires, seg(f, float2(0.0, -1.25), float2(0.0, 0.35)));
    // four hairpin arches, each as four straight pieces
    float fil = 1e5;
    for (int i = 0; i < 4; i++) {
      float x0 = -0.32 + 0.16*float(i);
      float x1 = x0 + 0.16;
      float2 a0 = float2(x0, 0.12), a1 = float2(x0 + 0.015, -0.18), a2 = float2(x0 + 0.08, -0.3), a3 = float2(x1 - 0.015, -0.18), a4 = float2(x1, 0.12);
      fil = min(fil, min(min(seg(f, a0, a1), seg(f, a1, a2)), min(seg(f, a2, a3), seg(f, a3, a4))));
    }
    float w = 0.012 + 0.006*uPower;
    float3 wireCol = mix(float3(0.25, 0.22, 0.2), lc*0.8, uPower*0.6);
    g = mix(g, wireCol, (1.0 - smoothstep(0.0, 0.012, wires))*0.8);
    float3 filOff = float3(0.35, 0.18, 0.08);
    float core = 1.0 - smoothstep(w*0.4, w, fil);
    g = mix(g, filOff, core*(1.0 - uPower));
    g += mix(lc, float3(1.0, 0.95, 0.85), 0.5*uPower)*uPower*core*3.0 + lc*uPower*1.1*exp(-fil/(0.03 + 0.06*uPower));
    // glass: a tinted rim, two window highlights, a dark outer edge
    float edge = pow(1.0 - nz, 3.0);
    g = mix(g, g*0.6 + float3(0.11, 0.1, 0.095), edge*0.7);
    g += float3(1.0)*0.32*exp(-sq(length(q - float2(-0.48, -0.42))/0.13));
    g += float3(1.0)*0.12*smoothstep(0.06, 0.0, abs(length(q - float2(0.08, 0.08)) - 0.82))*smoothstep(0.2, -0.5, q.x + q.y);
    g += lc*uPower*0.25*edge;
    float a = 1.0 - smoothstep(-0.6, 0.6, glass);
    col = mix(col, g, a);
    // a thin bright line where the glass turns away
    col += float3(0.9, 0.85, 0.8)*(0.08 + 0.25*uPower)*exp(-sq(glass/0.9))*smoothstep(R*0.2, -R*0.6, p.y - cen.y + p.x);
  }

  float3 l = pow(max(col, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  float3 o = pow(max(m, float3(0.0)), float3(1.0/2.2));
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}

""".trimIndent()
 val LIGHT="""

uniform float3 uL;        // filament x, y (pt), depth in front of the page (pt)
uniform float3 uCol;      // the light's colour × output, display-referred
uniform float4 uCard;
uniform float4 uChipL;
uniform float4 uChipR;
uniform float4 uTab;
uniform float4 uRad;      // corner radii: card, chips, tab, -

float sdRR(float2 p, float4 r, float rad) {
  float2 c = r.xy + r.zw*0.5;
  float2 q = abs(p - c) - r.zw*0.5 + rad;
  return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - rad;
}

float3 lit(float2 p, float4 r, float rad, float albedo) {
  float sd = sdRR(p, r, rad);
  if (sd > 0.75) return float3(0.0);
  float e = 0.6;
  float2 g = normalize(float2(sdRR(p + float2(e, 0.0), r, rad) - sdRR(p - float2(e, 0.0), r, rad),
                              sdRR(p + float2(0.0, e), r, rad) - sdRR(p - float2(0.0, e), r, rad)) + 1e-5);
  float cov = clamp(0.5 - sd, 0.0, 1.0);
  float t = clamp(-sd/6.0, 0.0, 1.0);
  float3 n = normalize(float3(g*(1.0 - t)*1.6, 0.25 + t));
  float3 Lv = float3(uL.xy - p, uL.z);
  float d2 = dot(Lv, Lv);
  float3 l = Lv*inversesqrt(d2);
  float att = uL.z*uL.z/d2;
  float diff = max(dot(n, l), 0.0)*att;
  float spec = pow(max(dot(reflect(-l, n), float3(0.0, 0.0, 1.0)), 0.0), 18.0)*sqrt(att)*(1.0 - t);
  // the faces take a little, the bevels facing the bulb most
  return uCol*(albedo*diff*mix(1.0, 0.2, t) + 0.6*spec)*cov;
}

half4 main(float2 p) {
  float3 c = lit(p, uCard, uRad.x, 0.32) + lit(p, uChipL, uRad.y, 0.4) + lit(p, uChipR, uRad.y, 0.4) + lit(p, uTab, uRad.z, 0.25);
  // a pixel of zero alpha is dropped when the canvas is composited: give the light a little coverage
  float a = clamp(max(c.r, max(c.g, c.b))*0.3, 0.0, 1.0);
  return half4(half3(c), half(a));
}

""".trimIndent()
}
