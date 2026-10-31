package com.ngocdevv.nativecook.blocks.gestures.parkassistscrub

// Coordinates are converted from physical pixels to dp by uDensity.
// RuntimeShader outputs sRGB: omit the RN iOS Display P3 encoding at the output only.
internal const val PARK_ASSIST_SCRUB_AGSL = """
uniform float uDensity;

uniform float4 uView;   // stage width, height (pt), points per metre, pixel ratio
uniform float4 uCar;    // car x, y (m), heading (rad, 0 = nose up the screen), wheel turn (rad)
uniform float4 uAssist; // gap to the wall (m), arcs shown 0…1, brake lights 0…1, hazard flash 0…1
uniform float4 uCam;    // camera centre x, y (m), tilt (rad), -

const float PI = 3.14159265;
float sq(float v) { return v*v; }
float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0); return mix(b, a, h) - k*h*(1.0 - h); }
float box(float3 p, float3 e, float r) { float3 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r; }
float rrect(float2 p, float2 e, float r) { float2 q = abs(p) - e + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float2 r2(float2 p, float a) { float c = cos(a), s = sin(a); return float2(c*p.x - s*p.y, s*p.x + c*p.y); }
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7)))*43758.5453); }

const float BAY_W = 2.7;
const float WALL_Y = -3.3;

struct Hit { float d; float id; };  // id: 0 ground, 1 our body, 2 glass, 3 wheel, 4 grey body, 5 wall, 6 lights
Hit H(float d, float id) { Hit h; h.d = d; h.id = id; return h; }
Hit nearer(Hit a, Hit b) { return a.d < b.d ? a : b; }

// a car in its own space (x right, y forward, z up); grey = 1 for the parked neighbours
Hit car(float3 p, float grey, float wheel) {
  float body = box(p - float3(0.0, 0.0, 0.55), float3(0.92, 2.15, 0.36), 0.3);
  float cabin = box(p - float3(0.0, -0.2, 1.05), float3(0.76, 1.15, 0.3), 0.3);
  Hit h = H(smin(body, max(cabin, 0.9 - p.z), 0.08), grey > 0.5 ? 4.0 : 1.0);
  // the glass band round the cabin
  if (cabin < body && p.z > 1.0) h.id = 2.0;
  float3 q = float3(abs(p.x), p.y, p.z);
  // wheels: front pair steer
  float3 wf = q - float3(0.9, 1.35, 0.36);
  float2 wfs = r2(wf.xy, -wheel*sign(p.x));
  float3 wfr = float3(wfs, wf.z);
  float wfd = length(float2(length(wfr.yz) - 0.33, 0.0)) ;
  wfd = max(length(wfr.yz) - 0.35, abs(wfr.x) - 0.13);
  float3 wr = q - float3(0.9, -1.35, 0.36);
  float wrd = max(length(wr.yz) - 0.35, abs(wr.x) - 0.13);
  h = nearer(h, H(min(wfd, wrd) - 0.02, 3.0));
  // lights: two front, two rear
  float lf = box(q - float3(0.62, 2.18, 0.62), float3(0.22, 0.04, 0.07), 0.04);
  float lr = box(q - float3(0.62, -2.18, 0.66), float3(0.24, 0.04, 0.06), 0.04);
  h = nearer(h, H(min(lf, lr), grey > 0.5 ? 4.0 : 6.0));
  return h;
}

float3 toCar(float3 p, float2 at, float hd) { float2 q = r2(p.xy - at, -hd); return float3(q, p.z); }

Hit scene(float3 p) {
  Hit h = H(p.z, 0.0);
  // the wall closing the bays
  h = nearer(h, H(box(p - float3(0.0, WALL_Y - 0.25, 0.4), float3(12.0, 0.25, 0.4), 0.08), 5.0));
  // our car
  float3 q = toCar(p, uCar.xy, uCar.z);
  if (length(q.xy) < 3.0) h = nearer(h, car(q, 0.0, uCar.w));
  else h = nearer(h, H(length(q.xy) - 2.6, 1.0));
  // the neighbours
  float3 a = p - float3(-BAY_W, -0.75, 0.0), b = p - float3(BAY_W, -0.55, 0.0);
  if (length(a.xy) < 3.0) h = nearer(h, car(a, 1.0, 0.0)); else h = nearer(h, H(length(a.xy) - 2.6, 4.0));
  if (length(b.xy) < 3.0) h = nearer(h, car(b, 1.0, 0.0)); else h = nearer(h, H(length(b.xy) - 2.6, 4.0));
  return h;
}
float sd(float3 p) { return scene(p).d; }

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

// the ground: asphalt, painted bays and the assist arcs behind the car
float3 ground(float2 g) {
  float n = hash(floor(g*24.0))*0.5 + hash(floor(g*7.0))*0.5;
  float3 col = float3(0.13, 0.135, 0.15)*(0.92 + 0.12*n);
  // bay lines: between the bays, and the line along the front of them
  float lines = 1e3;
  for (int i = -2; i <= 2; i++) {
    float x = (float(i) + 0.5)*BAY_W;
    lines = min(lines, max(abs(g.x - x) - 0.06, max(g.y - 2.4, WALL_Y - g.y)));
  }
  float paint = 1.0 - smoothstep(0.0, 0.03, lines);
  col = mix(col, float3(0.86, 0.86, 0.82), paint*0.85);
  // the assist arcs, drawn in the car's space behind the bumper
  float2 c = r2(g - uCar.xy, -uCar.z);
  float2 rear = c - float2(0.0, -2.2);
  float ang = atan(rear.x, -rear.y);
  float rr = length(rear);
  float gap = uAssist.x;
  float warn = clamp(1.0 - gap/1.2, 0.0, 1.0);
  float3 hot = mix(float3(0.3, 0.95, 0.5), mix(float3(1.0, 0.8, 0.2), float3(1.0, 0.22, 0.2), smoothstep(0.45, 0.85, warn)), smoothstep(0.1, 0.45, warn));
  if (rear.y < 0.0 && abs(ang) < 0.85 && g.y > WALL_Y) {
    for (int k = 0; k < 4; k++) {
      float rk = 0.4 + 0.4*float(k);
      // the nearer the wall, the more arcs light, from the outside in
      float on = smoothstep(0.0, 0.08, warn*4.2 - float(3 - k)) ;
      float band = 1.0 - smoothstep(0.04, 0.09, abs(rr - rk));
      float fade = 1.0 - smoothstep(0.55, 0.85, abs(ang));
      col = mix(col, hot, band*fade*(0.25 + 0.75*on)*uAssist.y);
    }
    col += hot*0.25*exp(-rr*rr*2.0)*warn*uAssist.y*(1.0 - smoothstep(0.6, 0.85, abs(ang)));
  }
  return col;
}

// soft contact shadows of the three cars and the wall on the ground
float shadowAt(float2 g) {
  float2 c = r2(g - uCar.xy - float2(0.15, -0.15), -uCar.z);
  float s = 1.0 - smoothstep(-0.35, 0.45, rrect(c, float2(1.0, 2.25), 0.6));
  float2 a = g - float2(-BAY_W + 0.15, -0.9), b = g - float2(BAY_W + 0.15, -0.7);
  s = max(s, 1.0 - smoothstep(-0.35, 0.45, rrect(a, float2(1.0, 2.25), 0.6)));
  s = max(s, 1.0 - smoothstep(-0.35, 0.45, rrect(b, float2(1.0, 2.25), 0.6)));
  s = max(s, (1.0 - smoothstep(0.0, 0.6, g.y - WALL_Y))*0.7);
  return s;
}

half4 main(float2 pixel) {
  float2 xy = pixel / uDensity;
  float ppm = uView.z;
  // screen → a point on the camera's image plane, in metres
  float2 sp = (xy - uView.xy*0.5)/ppm;
  sp.y = -sp.y;
  float tilt = uCam.z;
  float3 fwd = float3(0.0, sin(tilt), -cos(tilt));
  float3 up = float3(0.0, cos(tilt), sin(tilt));
  float3 right = float3(1.0, 0.0, 0.0);
  float3 cameraCentre = float3(uCam.xy, 0.0);
  float3 ro = cameraCentre - fwd*20.0 + right*sp.x + up*sp.y;
  float3 rd = fwd;
  // start at the slab of space where anything stands (z 0…1.6)
  float t0 = (ro.z - 1.7)/(-rd.z);
  float t1 = (ro.z + 0.001)/(-rd.z);
  float t = t0;
  float px = 1.0/(ppm*uView.w);
  Hit h = H(1e3, 0.0);
  bool hit = false;
  for (int i = 0; i < 72; i++) {
    float3 p = ro + rd*t;
    h = scene(p);
    if (h.d < 0.4*px) { hit = true; break; }
    t += h.d*0.95;
    if (t > t1 + 0.01) break;
  }
  if (!hit) { t = t1; h = H(0.0, 0.0); }
  float3 p = ro + rd*t;
  float3 col;
  if (h.id < 0.5) {
    col = ground(p.xy)*(1.0 - 0.55*shadowAt(p.xy));
  } else {
    float e = max(0.004, px);
    const float2 k = float2(1.0, -1.0);
    float3 n = normalize(k.xyy*sd(p + k.xyy*e) + k.yyx*sd(p + k.yyx*e) + k.yxy*sd(p + k.yxy*e) + k.xxx*sd(p + k.xxx*e));
    float ao = 0.0;
    for (int j = 0; j < 3; j++) { float hh = 0.06 + 0.12*float(j); ao += max(0.0, hh - sd(p + n*hh))/hh/(1.0 + float(j)); }
    ao = clamp(1.0 - ao*0.8, 0.4, 1.0);
    float3 key = normalize(float3(-0.45, 0.35, 0.82));
    float3 V = -rd;
    float nl = clamp((dot(n, key) + 0.35)/1.35, 0.0, 1.0);
    float3 base;
    float gloss = 0.35;
    if (h.id < 1.5) base = float3(1.0, 0.52, 0.2);
    else if (h.id < 2.5) { base = float3(0.16, 0.2, 0.26); gloss = 1.0; }
    else if (h.id < 3.5) base = float3(0.09, 0.09, 0.1);
    else if (h.id < 4.5) base = float3(0.62, 0.63, 0.67);
    else if (h.id < 5.5) base = float3(0.42, 0.42, 0.45);
    else base = float3(0.9, 0.9, 0.85);
    col = mix(base*float3(0.55, 0.5, 0.65), base, nl)*(0.6 + 0.45*nl)*ao;
    float3 Hh = normalize(key + V);
    col += float3(1.0, 0.97, 0.92)*(0.06 + 0.3*gloss)*pow(max(dot(n, Hh), 0.0), 24.0 + 60.0*gloss)*ao;
    col += float3(0.5, 0.6, 0.9)*0.18*pow(1.0 - max(dot(n, V), 0.0), 3.0);
    // our car's lights: rear brake lights, and both pairs blinking amber for the hazard flash
    if (h.id > 5.5) {
      float3 q = toCar(p, uCar.xy, uCar.z);
      float rearL = step(q.y, 0.0);
      col = mix(col, float3(1.0, 0.15, 0.12)*1.3, rearL*uAssist.z);
      col = mix(col, float3(1.0, 0.65, 0.15)*1.4, uAssist.w);
    }
  }
  // the brake lights' glow on the ground behind the car
  float2 c = r2(p.xy - uCar.xy, -uCar.z);
  col += float3(1.0, 0.15, 0.1)*0.35*uAssist.z*exp(-sq((abs(c.x) - 0.62)/0.35) - sq((c.y + 2.6)/0.4))*step(p.z, 0.01);
  col += float3(1.0, 0.6, 0.15)*0.3*uAssist.w*exp(-sq((abs(c.x) - 0.62)/0.4) - sq((abs(c.y) - 2.6)/0.45))*step(p.z, 0.01);
  float3 ex = max(col - 0.9, 0.0);
  col = min(col, float3(0.9)) + ex/(1.0 + ex/0.1);
  return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
}

"""
