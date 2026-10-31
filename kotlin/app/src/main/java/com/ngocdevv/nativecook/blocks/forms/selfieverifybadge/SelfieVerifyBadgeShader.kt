package com.ngocdevv.nativecook.blocks.forms.selfieverifybadge

internal object SelfieVerifyBadgeShader {
    val BUST = """

uniform float2 uCentre;
uniform float uUnit;
uniform float uYaw;
uniform float uAlpha;
uniform float uScan;     // scan line height in object units, -9 when off
uniform float uPixelRatio;
uniform float uDetect;   // 0 … 1, the mesh turns lime when the face is found
uniform float uCollapse; // 0 … 1, the mesh spins and gathers into the badge's place

float sq(float v) { return v*v; }
float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
half4 premul(float3 rgb, float a) {
  if (a <= 0.0) return half4(0.0);
  return half4(half3(toP3(clamp(rgb, 0.0, 1.0))*a), half(a));
}
float hash2(float2 p) { float3 v = fract(float3(p.x, p.y, p.x)*0.1031); v += dot(v, v.yzx + 33.33); return fract((v.x + v.y)*v.z); }

float3 ry(float3 p, float a) { float c=cos(a), s=sin(a); return float3(c*p.x+s*p.z, p.y, -s*p.x+c*p.z); }
float ell(float3 p, float3 r) { float k0 = length(p/r); float k1 = length(p/(r*r)); return k0*(k0 - 1.0)/k1; }
float smin(float a, float b, float k) { float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0); return mix(b,a,h)-k*h*(1.0-h); }
float smax(float a, float b, float k) { return -smin(-a, -b, k); }
// a face, the way a face scanner sees it: skull, cheeks, jaw and chin, brow, nose, eye hollows, lips
float face(float3 p) {
  float d = ell(p - float3(0.0, 0.08, -0.02), float3(0.34, 0.45, 0.38));
  d = smin(d, ell(float3(abs(p.x) - 0.15, p.y + 0.02, p.z - 0.2), float3(0.13, 0.13, 0.14)), 0.1);
  d = smin(d, ell(p - float3(0.0, -0.27, 0.1), float3(0.21, 0.18, 0.25)), 0.13);
  d = smin(d, ell(p - float3(0.0, 0.19, 0.27), float3(0.27, 0.07, 0.11)), 0.06);
  d = smin(d, ell(p - float3(0.0, 0.0, 0.37), float3(0.065, 0.16, 0.14)), 0.05);
  d = smin(d, ell(p - float3(0.0, -0.11, 0.42), float3(0.07, 0.045, 0.06)), 0.04);
  d = smax(d, -(length(float3(abs(p.x) - 0.13, p.y - 0.08, p.z - 0.38)) - 0.1), 0.05);
  d = smin(d, ell(p - float3(0.0, -0.215, 0.34), float3(0.11, 0.04, 0.06)), 0.035);
  // the mesh stops under the chin, like a scan
  return max(d, -p.y - 0.5);
}
float sceneD(float3 p, float sc, float yaw) { return face(ry(p/sc, yaw))*sc; }
uniform float uDensity;
half4 main(float2 pixelPosition) {
  float2 xy = pixelPosition / uDensity;
  float c = uCollapse;
  float sc = mix(1.0, 0.3, smoothstep(0.0, 1.0, c));
  float yaw = uYaw + 6.2831853*smoothstep(0.0, 1.0, c);
  float2 s = (xy - uCentre)/uUnit;
  float D = 6.0;
  float3 ro = float3(0.0, 0.0, D);
  float3 rd = normalize(float3(s.x, -s.y, -D));
  float t = D - 0.8;
  bool hit = false;
  float closest = 9.0;
  for (int i = 0; i < 64; i++) {
    float d = sceneD(ro + rd*t, sc, yaw);
    closest = min(closest, d);
    if (d < 0.0012) { hit = true; break; }
    t += d*0.85;
    if (t > D + 0.8) break;
  }
  float3 lime = float3(0.66, 0.96, 0.24);
  float3 ink = mix(float3(0.9, 0.92, 0.95), lime, uDetect);
  float pix = 1.0/(uUnit*uPixelRatio);
  // the scan line in front of the face
  float scanA = 0.0;
  if (uScan > -5.0) scanA = exp(-sq((-s.y - uScan)/0.012))*0.9 + exp(-sq((-s.y - uScan - 0.06)/0.06))*0.18*step(-s.y, uScan + 0.12);
  scanA *= step(abs(s.x), 0.46);
  // a faint halo round the head
  float halo = exp(-max(closest, 0.0)/(0.05*sc))*0.22*(1.0 - smoothstep(0.0, 0.004, -closest));
  if (!hit) {
    float a = clamp(scanA + halo*(0.4 + 0.6*uDetect), 0.0, 1.0);
    return premul(mix(ink, lime, scanA), a*uAlpha);
  }
  float3 p = ro + rd*t;
  const float e = 0.0015;
  float3 n = normalize(float3(sceneD(p + float3(e,0,0), sc, yaw) - sceneD(p - float3(e,0,0), sc, yaw),
    sceneD(p + float3(0,e,0), sc, yaw) - sceneD(p - float3(0,e,0), sc, yaw),
    sceneD(p + float3(0,0,e), sc, yaw) - sceneD(p - float3(0,0,e), sc, yaw)));
  // the mesh lives on the face: a lattice in its own angles, so it turns with the head
  float3 q = ry(p/sc, yaw) - float3(0.0, 0.0, -0.05);
  float lon = atan(q.x, q.z);
  float lat = q.y;
  float2 g = float2(lon*11.0, lat*26.0);
  float2 f = fract(g) - 0.5;
  float facing = clamp(dot(n, -rd), 0.0, 1.0);
  float w = 0.5*pix*26.0/sc/max(facing, 0.25);
  float dotD = length(f*float2(1.0, 1.0));
  float dots = 1.0 - smoothstep(0.08, 0.08 + w*2.0, dotD);
  float lines = 1.0 - smoothstep(0.0, w*1.2, min(min(abs(f.x), abs(f.y)), abs(f.x - f.y)*0.7071));
  // the back of the head and grazing parts fade, so the face reads in depth
  // a mask, as face scanners draw it: the face is lit, the back of the head is not drawn
  float front = smoothstep(-0.05, 0.45, q.z/0.39);
  // lit from the upper left, so the brow, the nose and the cheekbones read through the mesh
  float lit = 0.12 + 1.25*pow(clamp(dot(n, normalize(float3(-0.75, 0.5, 0.55))), 0.0, 1.0), 1.5);
  float glow = (dots*(1.25 + 1.2*c) + lines*0.4)*lit;
  float3 col = ink*glow;
  float fill = (0.06 + 0.16*pow(1.0 - facing, 2.0))*(0.6 + 0.6*lit);
  float a = clamp(fill + glow*0.9, 0.0, 1.0)*front;
  col = mix(col/max(a, 0.001), lime, scanA);
  a = max(a, scanA);
  return premul(col, a*uAlpha);
}

    """.trimIndent()
    val CUSHION = """

uniform float2 uCentre;
uniform float uSize;     // half width of the shape, pt
uniform float uShape;    // 0 rounded square … 1 scalloped flower
uniform float uPuff;     // 0 a thin outline … 1 an inflated cushion
uniform float uBadge;    // badge radius, pt
uniform float uYaw;      // badge flip
uniform float uCheck;    // 0 … 1 check drawn
uniform float uPixelRatio;

float sq(float v) { return v*v; }
float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}
half4 premul(float3 rgb, float a) {
  if (a <= 0.0) return half4(0.0);
  return half4(half3(toP3(clamp(rgb, 0.0, 1.0))*a), half(a));
}
float hash2(float2 p) { float3 v = fract(float3(p.x, p.y, p.x)*0.1031); v += dot(v, v.yzx + 33.33); return fract((v.x + v.y)*v.z); }

float rbox(float2 p, float2 b, float r) { float2 q=abs(p)-b+r; return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r; }
float flower(float2 q, float lobes, float amp) {
  float a = atan(q.y, q.x);
  return (length(q) - (1.0 + amp*cos(lobes*a)))*0.92;
}
float shapeD(float2 p) {
  float2 q = (p - uCentre)/uSize;
  float sqr = rbox(q, float2(1.0), 0.16);
  float flo = flower(q*0.95, 8.0, 0.09);
  return mix(sqr, flo, uShape)*uSize;
}
float segD(float2 p, float2 a, float2 b) { float2 v=p-a, w=b-a; return length(v - w*clamp(dot(v,w)/dot(w,w), 0.0, 1.0)); }
uniform float uDensity;
half4 main(float2 pixelPosition) {
  float2 xy = pixelPosition / uDensity;
  float d = shapeD(xy);
  float pix = 1.0/uPixelRatio;
  float3 col = float3(0.0);
  float a = 0.0;
  // the cushion: height grows from the edge in, so the outline reads as a soft inflated form
  float depth = mix(2.0, min(0.5*uSize, 70.0), uPuff);
  float h = clamp(-d/depth, 0.0, 1.0);
  float inside = 1.0 - smoothstep(-pix, pix, d);
  float outline = (1.0 - smoothstep(0.0, 1.5, abs(d + 1.0)))*(1.0 - uPuff);
  if (inside > 0.0 || outline > 0.0) {
    float e = 1.5;
    float hx = clamp(-shapeD(xy + float2(e, 0.0))/depth, 0.0, 1.0);
    float hy = clamp(-shapeD(xy + float2(0.0, e))/depth, 0.0, 1.0);
    float slope = 18.0*uPuff;
    float3 n = normalize(float3(-(sqrt(hx) - sqrt(h))*slope, (sqrt(hy) - sqrt(h))*slope, 1.0));
    float3 L = normalize(float3(-0.45, 0.6, 0.66));
    float diff = clamp(dot(n, L), 0.0, 1.0);
    float3 white = mix(float3(0.74, 0.74, 0.84), float3(0.995, 0.995, 1.0), smoothstep(0.15, 0.64, diff));
    float grain = (hash2(floor(xy*uPixelRatio)) - 0.5)*0.09*uPuff*(1.0 - 0.7*diff)*(1.0 - h*0.8);
    float3 c = white + grain;
    float fill = inside*smoothstep(0.0, 0.25, uPuff);
    col = mix(float3(1.0), c, smoothstep(0.0, 0.3, uPuff));
    a = max(fill, outline*0.9);
  }
  // the badge flips in on top
  if (uBadge > 0.5) {
    float2 q = (xy - uCentre)/uBadge;
    float c = cos(uYaw);
    float side = sin(uYaw);
    float sx = max(abs(c), 0.035);
    float2 fq = float2(q.x/sx, q.y);
    float face = flower(fq, 8.0, 0.08)*sx;
    float thick = 0.16*abs(side);
    float2 eq = float2((q.x + sign(side)*thick)/sx, q.y);
    float edge = min(face, flower(eq, 8.0, 0.08)*sx);
    float edgeA = 1.0 - smoothstep(-pix/uBadge, pix/uBadge, edge);
    float faceA = 1.0 - smoothstep(-pix/uBadge, pix/uBadge, face);
    float3 bc = float3(0.11, 0.11, 0.12);
    float gloss = smoothstep(0.6, -0.6, fq.x + fq.y)*0.12;
    float3 facec = float3(0.19, 0.19, 0.2) + gloss;
    float tick = min(segD(fq, float2(-0.34, 0.02), float2(-0.1, 0.26)), segD(fq, float2(-0.1, 0.26), float2(0.36, -0.22)));
    float along = clamp((fq.x + 0.34)/0.7, 0.0, 1.0);
    float ck = (1.0 - smoothstep(0.075, 0.105, tick))*step(along, uCheck*1.02)*step(0.0, c);
    facec = mix(facec, float3(0.86, 0.86, 0.88), ck);
    float3 bcol = mix(bc, facec, faceA);
    float ba = max(edgeA, faceA);
    // soft shadow under the badge on the cushion
    float shadow = exp(-sq(length(q - float2(0.0, 0.12))/1.15))*0.18*smoothstep(0.0, 0.4, uPuff);
    col = mix(col*(1.0 - shadow), bcol, ba);
    a = max(a, ba);
  }
  return premul(col, a);
}

    """.trimIndent()
}
