package com.ngocdevv.nativecook.blocks.pickers.louvreventthermostat

// Same ray-traced material as the RN reference. Native surfaces consume sRGB (uP3 = 0).
internal object LouvreVentThermostatShaders {
    val PAGE = """

uniform float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 s) {
  s = clamp(s, 0.0, 1.0);
  if (uP3 < 0.5) return half3(s);
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

uniform float3 uEdgeL;
uniform float3 uEdgeR;
uniform float uGlowH;
uniform float3 uG0;
uniform float3 uG1;
uniform float3 uG2;
uniform float uGA;
uniform float3 uHalo;
uniform float4 uFrame;
uniform float uConv;
uniform float3 uDot;
uniform float uDotA;
uniform float uDotAmp;

float dotField(float2 xy, float x0, float cols, float edgeSide) {
  // grid of 2 pt dots, pitch 15, rows from y 262 to 712
  float cx = (xy.x - x0) / 15.0;
  float cy = (xy.y - 262.0) / 15.0;
  if (cx < -0.5 || cx > cols - 0.5 || cy < -0.5 || cy > 30.5) return 0.0;
  float2 c = float2(x0 + 15.0 * floor(cx + 0.5), 262.0 + 15.0 * floor(cy + 0.5));
  float d = length(xy - c);
  float a = 1.0 - smoothstep(0.55, 1.35, d);
  // brighter toward the screen edge (0.5 → 0.9)
  float ci = floor(cx + 0.5) / max(cols - 1.0, 1.0);
  float toEdge = edgeSide > 0.0 ? ci : 1.0 - ci;
  float base = mix(0.5, 0.9, clamp(toEdge, 0.0, 1.0));
  // the convection wave: 90 pt wavelength, moving up as the phase grows
  float w = 0.5 + 0.5 * sin(6.2831853 * (c.y / 90.0 + uConv));
  // fade the field's ends so it reads as air, not a grid block
  float ends = smoothstep(262.0, 330.0, c.y) * (1.0 - smoothstep(640.0, 712.0, c.y));
  return a * base * mix(1.0 - uDotAmp * 0.6, 1.0, w) * mix(0.55, 1.0, ends);
}

half4 main(float2 xy) {
  float3 col = mix(float3(0.051, 0.004, 0.004), float3(0.086, 0.004, 0.008), smoothstep(0.0, 460.0, xy.y));

  // side edge light, y 120–760, 30 pt deep
  float envL = smoothstep(90.0, 260.0, xy.y) * (1.0 - smoothstep(600.0, 800.0, xy.y));
  float envR = smoothstep(80.0, 200.0, xy.y) * (1.0 - smoothstep(520.0, 780.0, xy.y));
  col = mix(col, uEdgeL, exp(-xy.x / 22.0) * envL);
  col = mix(col, uEdgeR, exp(-(402.0 - xy.x) / 22.0) * envR);

  // warm halo round the frame, stronger low
  float2 fb = uFrame.zw * 0.5;
  float df = sdBox(xy - uFrame.xy - fb, fb, 26.0);
  float low = mix(0.35, 1.0, smoothstep(uFrame.y, uFrame.y + uFrame.w, xy.y));
  col += uHalo * exp(-max(df, 0.0) / 22.0) * low * step(0.0, df);

  // dot fields
  float dl = dotField(xy, 12.0, 3.0, -1.0);
  float dr = dotField(xy, 372.0, 2.0, 1.0);
  float da = clamp((dl + dr) * uDotA * 1.25, 0.0, 1.0);
  col = mix(col, uDot, da);

  // the heat glow from the bottom edge, centred left of middle
  float2 q = float2((xy.x - 150.0) / 290.0, (888.0 - xy.y) / max(uGlowH, 1.0));
  float w = exp(-2.4 * dot(q, q));
  float3 g = w > 0.62 ? mix(uG1, uG0, (w - 0.62) / 0.38) : w > 0.24 ? mix(uG2, uG1, (w - 0.24) / 0.38) : uG2;
  float ga = uGA * smoothstep(0.02, 0.32, w);
  col = mix(col, g, ga);

  // grain (4 %, heavier in the glow) and dither
  float n = hash(floor(xy * 1.5)) - 0.5;
  col *= 1.0 + n * (0.04 + 0.04 * ga);
  col += (hash(xy + 17.0) - 0.5) / 255.0;
  return half4(toOut(col), 1.0);
}

""".trimIndent()
    val VENT = """

uniform float uP3;
float lin(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }
float enc(float c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
half3 toOut(float3 s) {
  s = clamp(s, 0.0, 1.0);
  if (uP3 < 0.5) return half3(s);
  float3 l = float3(lin(s.r), lin(s.g), lin(s.b));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return half3(enc(p.r), enc(p.g), enc(p.b));
}
float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float sdBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

uniform float4 uFrame;
uniform float4 uVent;
uniform float uLineY;
uniform float uS0;
uniform float uK0;
uniform float2 uBl[26];
uniform float3 uHeat;   // chamber light colour (sRGB)
uniform float uI;       // chamber intensity
uniform float uElem;    // element bars 0..1
uniform float uCore;    // white-hot core of the gaps 0..1
uniform float uBarOff;  // element bars' offset (0.55 x the blades' scroll)
uniform float uPh;      // loop phase (rad)
uniform float uPilot;   // pilot ember line alpha

const float PITCH = 20.8;
const float HC = 9.5;    // half chord
const float HT = 0.75;   // half thickness
const float EZ = 430.0;  // eye distance in front of the pivot plane
const float FZ = 10.0;   // the front lip's plane
const float BZ = -58.0;  // the chamber's back wall
const float LIP = 6.0;   // width of the front lip at each side

// per fragment: the seven blades around it (tilt, glint), the centre index, the walls' x and the eye
float2 gB[7];
float gKc;
float gX0;
float gX1;
float3 gEye;
float3 gHc;

float yOf(float k) { return uLineY - (k - uS0) * PITCH; }

// ray o + l r in (Y, Z) against the slab of a blade (pivot y, tilt th): (l, face, s). face 1 = the front face,
// 2 = the back face, 3 = the top (front) edge, 4 = the bottom (back) edge; s runs -HC (back edge) .. HC (top edge)
float3 slab(float2 o, float2 r, float yk, float th) {
  float a = radians(th);
  float2 d = float2(-cos(a), sin(a));
  float2 n = float2(sin(a), cos(a));
  float2 q = o - float2(yk, 0.0);
  float os = dot(d, q);
  float ow = dot(n, q);
  float rs = dot(d, r);
  float rw = dot(n, r);
  rs = abs(rs) < 1e-5 ? 1e-5 : rs;
  rw = abs(rw) < 1e-5 ? 1e-5 : rw;
  float s1 = (-HC - os) / rs;
  float s2 = (HC - os) / rs;
  float w1 = (-HT - ow) / rw;
  float w2 = (HT - ow) / rw;
  float sn = min(s1, s2);
  float wn = min(w1, w2);
  float tn = max(sn, wn);
  float tf = min(max(s1, s2), max(w1, w2));
  if (tn >= tf || tn <= 1e-4) return float3(-1.0, 0.0, 0.0);
  float face = wn > sn ? (rw < 0.0 ? 1.0 : 2.0) : (rs < 0.0 ? 3.0 : 4.0);
  return float3(tn, face, os + tn * rs);
}

// nearest hit inside the housing: (l, kind, s, m) with kind 0 = out through the front, 1 = blade m, 2 = side wall,
// 3 = the chamber; face of the blade hit in fc
float4 trace(float3 o, float3 r, float skip, float walls, out float fc) {
  float best = 1e9;
  float kind = 0.0;
  float sh = 0.0;
  float mh = -1.0;
  fc = 0.0;
  for (int m = 0; m < 7; m++) {
    if (float(m) != skip) {
      float3 h = slab(o.yz, r.yz, yOf(gKc + float(m) - 3.0), gB[m].x);
      if (h.x > 0.0 && h.x < best) { best = h.x; kind = 1.0; sh = h.z; mh = float(m); fc = h.y; }
    }
  }
  float lw = r.x < -1e-5 ? (gX0 - o.x) / r.x : (r.x > 1e-5 ? (gX1 - o.x) / r.x : 1e9);
  if (walls > 0.5 && lw > 1e-4 && lw < best) { best = lw; kind = 2.0; }
  float lb = r.z < -1e-5 ? (BZ - o.z) / r.z : 1e9;
  if (lb > 1e-4 && lb < best) { best = lb; kind = 3.0; }
  float lf = r.z > 1e-5 ? (FZ - o.z) / r.z : 1e9;
  if (lf > 1e-4 && lf < best) { best = lf; kind = 0.0; }
  return float4(best, kind, sh, mh);
}

float3 chamber(float2 xy) {
  float yb = xy.y - uBarOff;
  float m = yb - 70.0 * floor(yb / 70.0);
  float db = abs(m - 35.0);
  float core = 1.0 - smoothstep(2.4, 3.4, db);
  float halo = exp(-db / 11.0);
  // convection: a slow rolling shimmer (one turn a loop) plus a faster flicker (seven turns)
  float n = 0.5 + 0.5 * sin(xy.x * 0.045 + xy.y * 0.07 - uPh + 0.8 * sin(xy.y * 0.031 + 7.0 * uPh));
  float3 c = gHc * (0.85 + 1.1 * halo * uElem) * (0.84 + 0.32 * n);
  c = mix(c, float3(1.0, 0.95, 0.84), core * uElem * 0.95);
  // light past full scale burns toward white instead of clipping to a saturated yellow
  float mx = max(c.r, max(c.g, c.b));
  return mx > 1.0 ? mix(c / mx, float3(1.0, 0.97, 0.9), clamp((mx - 1.0) * 0.8, 0.0, 0.85)) : c;
}

// what a glossy surface sees when its reflection leaves the vent: the dark room, a soft top light, the key (a soft
// box above and in front) and the heat glow on the page below
float3 room(float3 R) {
  float3 c = float3(0.025, 0.008, 0.006);
  float front = smoothstep(-0.25, 0.45, R.z);
  // the ceiling of the room: a broad soft box, brightest straight up
  c += float3(0.36, 0.35, 0.34) * smoothstep(0.1, 0.85, -R.y) * front;
  // the key: a narrow strip light above and in front, the bright band that slides down each face
  float3 L = normalize(float3(-0.12, -0.62, 0.78));
  c += float3(1.0, 0.97, 0.93) * pow(max(dot(R, L), 0.0), 70.0) * 1.6;
  // the heat glow on the page below
  c += gHc * 0.45 * smoothstep(0.05, 0.85, R.y) * front;
  return c;
}

float2 bladeN(float th, float face) {
  float a = radians(th);
  float2 d = float2(-cos(a), sin(a));
  float2 n = float2(sin(a), cos(a));
  return face < 1.5 ? n : (face < 2.5 ? -n : (face < 3.5 ? d : -d));
}

// a blade seen in a reflection: the faces turned to the chamber burn with its light, the rest is dark steel
float3 bladeSeen(float th, float face) {
  float2 nh = bladeN(th, face);
  float open = abs(sin(radians(th)));
  float lit = clamp(-nh.y * 1.15 + 0.2, 0.0, 1.0) * mix(0.35, 1.0, open);
  return float3(0.03) + gHc * 0.85 * lit;
}

// a point on a side wall (Y, Z): a blade's end, its pivot boss, or the wall lit by the chamber through the channel
float3 wallAt(float2 p) {
  for (int m = 1; m < 6; m++) {
    float yk = yOf(gKc + float(m) - 3.0);
    float a = radians(gB[m].x);
    float2 q = p - float2(yk, 0.0);
    float s = dot(float2(-cos(a), sin(a)), q);
    float w = dot(float2(sin(a), cos(a)), q);
    if (length(q) < 2.6) return mix(float3(0.2), float3(0.42), smoothstep(1.2, -1.2, q.x)) * (1.0 - smoothstep(2.0, 2.6, length(q)) * 0.6);
    if (abs(s) <= HC + 0.2 && abs(w) <= HT + 0.6) {
      // the slanted end of a blade: steel, its upper edge catching the light
      float e = smoothstep(HT + 0.6, HT - 0.4, abs(w));
      return mix(float3(0.03), float3(0.15) + gHc * 0.25 * abs(sin(a)), e * (0.6 + 0.4 * step(0.0, -w)));
    }
  }
  // shadow test straight back toward the chamber, two rays for a soft edge
  float vis = 0.0;
  for (int i = 0; i < 2; i++) {
    float2 o = p + float2(float(i) * 2.4 - 1.2, 0.0);
    float blocked = 0.0;
    for (int m = 1; m < 6; m++) {
      float3 h = slab(o, float2(0.0, -1.0), yOf(gKc + float(m) - 3.0), gB[m].x);
      if (h.x > 0.0 && h.x < p.y - BZ) blocked = 1.0;
    }
    vis += 0.5 * (1.0 - blocked);
  }
  // p.y is the depth: the wall is brighter toward the chamber
  float dz = clamp((FZ - p.y) / (FZ - BZ), 0.0, 1.0);
  return float3(0.045, 0.035, 0.035) + gHc * (0.1 + 0.95 * vis) * mix(0.5, 1.15, dz);
}

float3 shade(float2 xy) {
  float3 o = gEye;
  float3 r = float3(xy.x - gEye.x, xy.y - gEye.y, -EZ);
  // the front lip: rays that meet the plane z = FZ outside the opening
  float lF = (FZ - o.z) / r.z;
  float xF = o.x + lF * r.x;
  if (xF < gX0 || xF > gX1) {
    float dl = xF < gX0 ? gX0 - xF : xF - gX1;
    float3 c = mix(float3(0.075), float3(0.05), smoothstep(0.0, LIP, dl));
    // the chamber's light leaking round the blade ends onto the lip's inner edge
    c += gHc * 0.3 * exp(-dl / 3.0);
    // a pivot rivet for every blade, in the middle of the lip
    float yF = o.y + lF * r.y;
    float kk = floor(uS0 + (uLineY - yF) / PITCH + 0.5);
    float2 pq = float2(dl - LIP * 0.5, yF - yOf(kk));
    float pin = 1.0 - smoothstep(1.3, 2.0, length(pq));
    c = mix(c, mix(float3(0.13), float3(0.34), smoothstep(1.3, -1.3, pq.y)), pin);
    return c;
  }
  float fc;
  float4 h = trace(o, r, -1.0, 1.0, fc);
  float3 p = o + h.x * r;
  float3 dir = normalize(r);
  if (h.y < 0.5) return room(dir);
  if (h.y > 2.5) {
    // the chamber through a gap. How far the ray passes from the nearest blade edge sets how much of the chamber
    // it sees: the gap darkens toward the blades and burns white-hot in its middle when the vent is open
    float3 c = chamber(p.xy);
    float dmin = 1e3;
    float2 o2 = o.yz;
    float2 r2 = normalize(r.yz);
    for (int m = 1; m < 6; m++) {
      float yk = yOf(gKc + float(m) - 3.0);
      float a = radians(gB[m].x);
      float2 dd = float2(-cos(a), sin(a)) * HC;
      for (int e = 0; e < 2; e++) {
        float2 ep = float2(yk, 0.0) + (e == 0 ? dd : -dd) - o2;
        float along = dot(ep, r2);
        dmin = min(dmin, length(ep - along * r2));
      }
    }
    c *= mix(0.45, 1.0, smoothstep(0.3, 2.6, dmin));
    float coreK = uCore * smoothstep(2.0, 4.8, dmin);
    c = mix(c, float3(1.0, 0.97, 0.88), coreK * 0.92);
    return min(c, float3(1.0));
  }
  if (h.y > 1.5) return wallAt(p.yz);

  // a blade
  float m = h.w;
  float2 bl = gB[3];
  for (int i = 0; i < 7; i++) { if (float(i) == m) bl = gB[i]; }
  float th = bl.x;
  float a = radians(th);
  float open = abs(sin(a));
  float2 d = float2(-cos(a), sin(a));
  float sN = clamp(h.z / HC, -1.0, 1.0);
  float fall = mix(1.0, 0.55, smoothstep(0.0, 240.0, abs(xy.y - uLineY)));
  float2 n2 = bladeN(th, fc);
  if (fc < 1.5) {
    // curved section: the normal turns 20° each way across the chord, then rolls over the top edge
    n2 = normalize(n2 + tan(radians(32.0 * sN)) * d);
    float roll = smoothstep(1.0 - 1.5 / HC, 1.0, sN);
    n2 = normalize(mix(n2, d, roll * 0.8));
  }
  float3 N = float3(0.0, n2);
  float3 R = reflect(dir, N);
  float cv = clamp(dot(-dir, N), 0.0, 1.0);
  float fr = 0.55 + 0.45 * pow(1.0 - cv, 5.0);
  float fr2;
  // (the reflection ignores the side walls: their mirror image read as a smear on the faces)
  float4 hr = trace(p + N * 0.05, R, m, 0.0, fr2);
  float3 Lr;
  if (hr.y < 0.5) {
    Lr = room(R) * fall;
  } else if (hr.y < 1.5) {
    float2 br = gB[3];
    for (int i = 0; i < 7; i++) { if (float(i) == hr.w) br = gB[i]; }
    Lr = bladeSeen(br.x, fr2);
  } else if (hr.y < 2.5) {
    float3 q = p + N * 0.05 + hr.x * R;
    Lr = wallAt(q.yz) * 0.8;
  } else {
    float3 q = p + N * 0.05 + hr.x * R;
    Lr = chamber(q.xy);
  }
  float3 col;
  if (fc < 2.5) {
    // dark brushed steel, #2A2A2A at the top edge to #161616 at the bottom
    // dark brushed steel (#2A2A2A at the top edge to #161616 at the bottom) under a clear, glossy finish: what the
    // face shows is mostly what it mirrors, the room above or the burning channel below
    float3 alb = mix(float3(0.086), float3(0.165), (sN + 1.0) * 0.5);
    alb *= 1.0 + (hash(float2(floor(p.x / 38.0), floor(h.z * 2.0))) - 0.5) * 0.06;
    float3 dif = alb * 0.8 * mix(0.8, 1.0, fall);
    // light bouncing off the hot face of the blade below, strongest deep in the channel (the back edge)
    float deep = smoothstep(0.5, -1.0, sN);
    dif += gHc * 0.14 * open * deep;
    col = dif + Lr * fr * 0.62;
    // light pouring out of the gap under the blade blooms over its back edge
    float toBack = (sN + 1.0) * HC * abs(cos(a));
    col += gHc * (0.3 * uCore + 0.15) * open * exp(-toBack / 2.6);
    // a crisp top edge, 1.5 pt: never darker than #8A8A8A where it rolls toward the key light
    float edge = smoothstep(1.0 - 1.6 / HC, 1.0 - 0.9 / HC, sN);
    col = mix(col, max(col, float3(0.541) * mix(0.8, 1.05, fall)), edge);
    // the back edge sits in the channel: a thin dark crease
    col *= mix(0.3, 1.0, smoothstep(-1.0, -1.0 + 0.8 / HC, sN));
  } else if (fc < 3.5) {
    // the top edge's thickness: catches the key light
    col = max(Lr * 0.9 + float3(0.12), float3(0.541) * mix(0.8, 1.05, fall)) + gHc * 0.35 * open;
  } else {
    // the bottom edge faces the chamber: a hot lip
    col = gHc * (0.5 + 0.7 * open) + float3(0.04);
  }
  // the glint of the blade crossing the line
  // the glint: a hot-white line running down the face as the blade seats (and as it crosses the line)
  float gy = mix(0.95, -0.6, bl.y);
  col += float3(1.0, 0.96, 0.9) * bl.y * (exp(-pow((sN - gy) * HC * abs(cos(a)) / 0.9, 2.0)) * 0.9 + 0.12);
  return col;
}

half4 main(float2 xy) {
  float2 fb = uFrame.zw * 0.5;
  float dOut = sdBox(xy - uFrame.xy - fb, fb, 26.0);
  if (dOut > 1.0) return half4(0.0);
  float aOut = 1.0 - smoothstep(-0.5, 0.5, dOut);
  float2 vb = uVent.zw * 0.5;
  float dV = sdBox(xy - uVent.xy - vb, vb, 18.0);
  gHc = uHeat * uI;
  float low = mix(0.45, 1.0, smoothstep(uVent.y, uVent.y + uVent.w, xy.y));

  // ---- frame band (outer frame to panel): near-black, 1 px rim, the chamber's light spilling onto its inner edge
  float3 band = float3(0.043, 0.008, 0.004);
  band += gHc * 0.5 * exp(-max(dV, 0.0) / 14.0) * low;
  float rim = 1.0 - smoothstep(0.3, 1.3, abs(dOut + 0.6));
  band = mix(band, float3(0.353, 0.227, 0.212) * mix(0.8, 1.25, low), rim * 0.9);
  // the pilot: a thin ember line under the panel, always burning
  float py = uVent.y + uVent.w + 4.0;
  float pl = (1.0 - smoothstep(0.4, 1.1, abs(xy.y - py))) * smoothstep(140.0, 170.0, xy.x) * (1.0 - smoothstep(232.0, 262.0, xy.x));
  band = mix(band, float3(0.95, 0.42, 0.16), pl * uPilot);
  band += float3(0.95, 0.42, 0.16) * uPilot * 0.35 * exp(-abs(xy.y - py) / 2.5) * smoothstep(120.0, 170.0, xy.x) * (1.0 - smoothstep(232.0, 282.0, xy.x));

  float3 col;
  if (dV > 0.6) {
    col = band;
  } else {
    gX0 = uVent.x + LIP;
    gX1 = uVent.x + uVent.z - LIP;
    gEye = float3(uVent.x + uVent.z * 0.5, uLineY, EZ);
    // the seven blades round this fragment
    gKc = floor(uS0 + (uLineY - xy.y) / PITCH + 0.5);
    float i0 = gKc - uK0 - 3.0;
    for (int m = 0; m < 7; m++) { gB[m] = float2(0.0); }
    for (int j = 0; j < 26; j++) {
      float dj = float(j) - i0;
      for (int m = 0; m < 7; m++) { if (abs(dj - float(m)) < 0.5) gB[m] = uBl[j]; }
    }
    // two samples a pixel across the blades' edges
    float3 op = 0.5 * (shade(xy + float2(0.0, -0.09)) + shade(xy + float2(0.0, 0.09)));
    col = op;
    // the panel's ends darken a little, its 1 px inner rim
    col *= mix(0.72, 1.0, smoothstep(0.0, 36.0, -dV));
    float inner = 1.0 - smoothstep(0.2, 1.1, abs(dV + 0.6));
    col = mix(col, float3(0.165, 0.141, 0.141), inner * 0.9);
    col = mix(col, band, smoothstep(-0.2, 0.6, dV));
  }
  col += (hash(xy + 31.0) - 0.5) / 255.0;
  return half4(toOut(col) * aOut, aOut);
}

""".trimIndent()
}
