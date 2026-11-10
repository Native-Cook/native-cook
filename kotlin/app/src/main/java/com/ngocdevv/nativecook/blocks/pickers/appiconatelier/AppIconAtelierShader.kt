package com.ngocdevv.nativecook.blocks.pickers.appiconatelier

internal object AppIconAtelierShader {
    val source="""
uniform shader uWall;       // the wallpaper, mapped onto the card rect
uniform float2 uRes;
uniform float4 uCard;       // x, y, w, h (pt)
uniform float uCardR;
uniform float2 uC;          // large pebble: centre (pt)
uniform float uS;           //   half width (pt)
uniform float3 uPose;       //   yaw, pitch, roll
uniform float2 uSquash;     //   x, y scale of a landing
uniform float4 uA0;         //   glass A: tint rgb, density
uniform float4 uA1;         //   glass A: frost, film, glow, -
uniform float4 uB0;
uniform float4 uB1;
uniform float uFront;       //   the liquid front, pebble units
uniform float uDir;         //   +1: B lies right of the front, -1: left
uniform float uMen;         //   meniscus strength
uniform float uPhase;       //   wobble phase of the front (loops)
uniform float2 uFloor;      //   its shadow's centre (pt)
uniform float uShadow;      //   its shadow's strength
uniform float3 uShadowTint;
uniform float2 uSC;         // small pebble (the icon in use): centre
uniform float uSS;          //   half width
uniform float uSOn;         //   0 hidden … 1 shown
uniform float uSPop;        //   a pop when the icon is set
uniform float4 uS0;
uniform float4 uS1;
uniform float uPx;          // pt per pixel

const float IOR = 1.47;
float sq(float v) { return v*v; }
float dot2(float2 v) { return dot(v, v); }

float sdRBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
// the pebble's outline: a squircle a little out of true, like a hand-made thing
float pebble2(float2 p) {
  p += 0.035*float2(sin(2.1*p.y + 0.7), sin(1.7*p.x + 2.0));
  return sdRBox(p, float2(0.93, 0.88), 0.52);
}
float sdPebble(float3 p) {
  const float r = 0.24;
  float h = 0.36 - 0.08*dot(p.xy, p.xy);       // half thickness: the face is domed
  float2 w = float2(pebble2(p.xy) + r, abs(p.z) - h + r);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - r;
}
// iq's heart, tip at the origin, lobes up to y ~ 1
float sdHeart(float2 p) {
  p.x = abs(p.x);
  if (p.y + p.x > 1.0) return sqrt(dot2(p - float2(0.25, 0.75))) - 0.35355339;
  return sqrt(min(dot2(p - float2(0.0, 1.0)), dot2(p - 0.5*max(p.x + p.y, 0.0))))*sign(p.x - p.y);
}
float sdGlyph(float3 p) {
  const float k = 0.66;
  const float r = 0.05;
  float d2 = sdHeart(float2(p.x, p.y + 0.37)/k)*k;
  float2 w = float2(d2 + r, abs(p.z - 0.1) - 0.11 + r);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - r;
}
float3 nPebble(float3 p) {
  const float e = 0.0015;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*sdPebble(p + k.xyy*e) + k.yyx*sdPebble(p + k.yyx*e) + k.yxy*sdPebble(p + k.yxy*e) + k.xxx*sdPebble(p + k.xxx*e));
}
float3 nGlyph(float3 p) {
  const float e = 0.0015;
  float2 k = float2(1.0, -1.0);
  return normalize(k.xyy*sdGlyph(p + k.xyy*e) + k.yyx*sdGlyph(p + k.yyx*e) + k.yxy*sdGlyph(p + k.yxy*e) + k.xxx*sdGlyph(p + k.xxx*e));
}

float3 rx(float3 v, float a) { float c = cos(a), s = sin(a); return float3(v.x, c*v.y - s*v.z, s*v.y + c*v.z); }
float3 ry(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x + s*v.z, v.y, -s*v.x + c*v.z); }
float3 rz(float3 v, float a) { float c = cos(a), s = sin(a); return float3(c*v.x - s*v.y, s*v.x + c*v.y, v.z); }
float3 toWorld(float3 v, float3 pose) { return rz(rx(ry(v, pose.x), pose.y), pose.z); }
float3 toObj(float3 v, float3 pose) { return ry(rx(rz(v, -pose.z), -pose.y), -pose.x); }

// a bright photo studio: soft white walls, a darker floor band (it draws the glass's edges), a big
// soft box high on the left, a strip light on the right, a warm card low on the left
float3 studio(float3 r) {
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
float3 film(float cosi, float3 p) {
  float d = 1.6 + 1.1*cosi + 0.25*sin(3.0*p.x + 2.0*p.y);
  return 0.62 + 0.38*cos(6.2831853*(d + float3(0.0, 0.33, 0.67)));
}

// the paper, the card and its wallpaper, and the two shadows: everything a pebble sees behind it
float3 shadowOn(float3 col, float2 xy, float2 f, float s, float strength, float3 tint) {
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
float3 page(float2 xy) {
  float3 col = mix(float3(0.969, 0.961, 0.984), float3(0.933, 0.918, 0.965), clamp(xy.y/uRes.y, 0.0, 1.0));
  // a faint pool of light on the pedestal
  float g = exp(-dot2((xy - uC - float2(0.0, 0.25*uS))/(uS*float2(2.6, 1.8))));
  col = mix(col, float3(0.985, 0.978, 1.0), 0.6*g);
  float2 cc = uCard.xy + uCard.zw*0.5;
  float dc = sdRBox(xy - cc, uCard.zw*0.5, uCardR);
  if (dc < 1.0) {
    float3 wall = uWall.eval(xy).rgb;
    col = mix(col, wall, clamp(0.5 - dc/max(uPx, 0.2), 0.0, 1.0));
  }
  col = shadowOn(col, xy, uSC + float2(0.0, uSS*1.1), uSS, 0.5*uSOn, uS0.rgb);
  col = shadowOn(col, xy, uFloor, uS, uShadow, uShadowTint);
  return col;
}

// one pebble. c, s: centre and half width (pt); sqz: squash; a*/b*: the two glasses; front, dir,
// men, phase: the liquid front between them
float3 glass(float2 xy, float2 c, float s, float3 pose, float2 sqz, float4 a0, float4 a1, float4 b0, float4 b1,
          float front, float dir, float men, float phase, out float cover) {
  float2 u = (xy - c)/(s*sqz);
  float px = uPx/s;
  float3 ro = toObj(float3(0.0, 0.0, 6.0), pose);
  float3 rdW = normalize(float3(u.x, -u.y, -6.0));
  float3 rd = toObj(rdW, pose);
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
    float d = sdPebble(ro + rd*t);
    if (d < dmin) { dmin = d; tBest = t; }
    if (d < 0.0012) { hit = true; tBest = t; break; }
    t += d*0.8;
    if (t > tEnd) break;
  }
  cover = hit ? 1.0 : 1.0 - smoothstep(0.0, 1.6*px, dmin);
  if (cover <= 0.0) return float3(0.0);

  float3 p = ro + rd*tBest;
  float3 n = nPebble(p);
  float3 nW = toWorld(n, pose);
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
  float3 refl = studio(reflect(rdW, nW));
  refl *= mix(float3(1.0), film(cosi, p), filmK*(0.35 + 0.65*(1.0 - cosi)));
  refl = mix(refl, float3(0.92, 0.9, 0.96) + 0.08*refl, frost*0.6);

  // inside: march to the heart or to the back of the glass
  float3 di = refract(rd, n, 1.0/IOR);
  float tt = 0.012;
  float3 q = p;
  int what = 0; // 1 heart, 2 back
  for (int k = 0; k < 40; k++) {
    q = p + di*tt;
    float dP = -sdPebble(q);
    float dG = sdGlyph(q);
    if (dG < 0.002) { what = 1; break; }
    if (dP < 0.002) { what = 2; break; }
    tt += max(min(dP, dG)*0.85, 0.006);
  }
  float3 absorb = pow(max(tint, float3(0.001)), float3(dens*tt));
  float3 body;
  if (what == 1) {
    // the heart: a white slab, lit by the soft box, its sides tinted by the glass around it
    float3 gn = toWorld(nGlyph(q), pose);
    float key = clamp(dot(gn, normalize(float3(-0.45, 0.65, 0.62))), 0.0, 1.0);
    float3 heart = mix(float3(0.985, 0.975, 1.0), float3(1.0, 0.93, 0.8), glow);
    float3 lit = heart*(0.62 + 0.45*key) + 0.1*tint*(1.0 - key);
    body = lit*mix(absorb, float3(1.0), 0.6 + 0.3*glow) + float3(1.0, 0.86, 0.62)*glow*0.3;
  } else {
    // through the back: refract out and pick up the page behind the pebble
    float3 no = nPebble(q);
    float3 dout = refract(di, -no, IOR);
    float3 seen;
    if (dot(dout, dout) < 1e-4) {
      seen = min(studio(toWorld(reflect(di, -no), pose)), float3(1.0))*0.8;
    } else {
      float3 qW = toWorld(q, pose);
      float3 dW = toWorld(dout, pose);
      float tp = (qW.z + 1.4)/max(-dW.z, 0.25);
      float2 hitP = qW.xy + dW.xy*tp;
      float2 xyB = c + float2(hitP.x, -hitP.y)*s*sqz;
      seen = page(xyB);
      if (frost > 0.01) {
        float o = 0.22*s;
        float3 soft = 0.25*(page(xyB + float2(o, 0.0)) + page(xyB - float2(o, 0.0)) + page(xyB + float2(0.0, o)) + page(xyB - float2(0.0, o)));
        seen = mix(seen, mix(soft, float3(0.95, 0.94, 0.98), 0.45), frost);
      }
    }
    body = seen*absorb + tint*0.05*dens;
  }
  // a little light scattered in the glass itself: frost glows, colour pools
  body = mix(body, mix(float3(0.96, 0.95, 0.99), tint, 0.35), frost*0.35);
  // milky glass with a strong film reads as nacre: the rainbow drifts across the whole face
  if (what != 1) body *= mix(float3(1.0), film(0.25 + 0.6*cosi, p*1.4)*1.1, filmK*frost*0.14);
  float3 col = mix(body, refl, F);
  // the meniscus: a bright curved line where the two glasses meet
  float glint = exp(-sq((p.x - fr)/0.03))*men;
  col += float3(1.0, 0.99, 1.0)*0.6*glint*(0.6 + 0.4*cosi);
  col += film(cosi, p)*0.18*exp(-sq((p.x - fr - 0.07*dir)/0.06))*men;
  return col;
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 main(float2 xy) {
  float3 col = page(xy);
  // the icon in use, in its Home Screen slot
  if (uSOn > 0.001 && dot2(xy - uSC) < sq(uSS*1.7)) {
    float s = uSS*(1.0 + 0.08*uSPop);
    float cv;
    float3 g = glass(xy, uSC, s, float3(-0.06, -0.05, 0.0), float2(1.0), uS0, uS1, uS0, uS1, 9.0, 1.0, 0.0, 0.0, cv);
    col = mix(col, g, cv*uSOn);
  }
  // the large pebble
  if (dot2(xy - uC) < sq(uS*1.75)) {
    float cv;
    float3 g = glass(xy, uC, uS, uPose, uSquash, uA0, uA1, uB0, uB1, uFront, uDir, uMen, uPhase, cv);
    col = mix(col, g, cv);
  }
  // tone: keep the soft box from clipping flat
  col = col/(1.0 + 0.12*max(max(col.r, col.g), col.b) - 0.12);
  float3 o = clamp(col, 0.0, 1.0);
  // a whisper of dither: the large soft gradients of the page band at 8 bits otherwise
  o += (fract(sin(dot(xy, float2(12.9898, 78.233)))*43758.5453) - 0.5)/255.0;
  return half4(half3(o), 1.0);
}
""".trimIndent()
}
