// Zodiac Jelly Dish — the liquid-glass slab, one SkSL shader. Output is premultiplied; colours are
// written in sRGB, as the canvas expects (a P3 conversion at the end made them pale on top of the display's own).
//
// The slab is a rounded box whose outline wobbles (whole sine cycles per loop) and which a finger
// can pull: points near the grab point are displaced toward the pull, so the glass stretches like
// jelly. Inside, pastel jelly blobs drift; the bevel bends what is behind it (refraction from the
// outline's gradient), catches a moving specular light and turns silvery at grazing angles. The
// window in the middle shows the dish, behind its own thick rim. The label lives on the glass
// surface (it deforms with it but is not refracted).

export const SLAB_SKSL = `
uniform shader dish;
uniform shader label;
uniform float2 center;
uniform float2 hs;
uniform float corner;
uniform float bevel;
uniform float4 win;      // window x, y, w, h (relative to the centre)
uniform float winR;
uniform float t;
uniform float loop;
uniform float2 grab;     // grab point (relative)
uniform float2 pull;     // pull vector
uniform float squash;
uniform float swirl;     // extra turns of the jelly (0…1 = one whole turn)
uniform float2 light;    // light direction in the plane (normalised-ish)
uniform float2 dishSize; // image size in px
uniform float labelScale; // label texture px per pt
uniform float2 labelOrigin; // label texture's (0,0) relative to the centre
uniform float pulse;     // a flash over the glass (0…1)

const float TAU = 6.2831853;


float sdBox(vec2 p, vec2 b, float r) {
  vec2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// the jelly's deformation: undo the pull and the squash to find where a point came from
vec2 undeform(vec2 p) {
  float s = squash;
  p.y = p.y / (1.0 - s) - s * hs.y * 0.5;
  p.x = p.x * (1.0 - s * 0.55);
  vec2 d = p - grab;
  float w = exp(-dot(d, d) / (2.0 * 150.0 * 150.0));
  return p - pull * w * 1.15;
}

float wobble(vec2 p) {
  float a = atan(p.y, p.x);
  float ph = TAU * t / loop;
  return 10.0 * sin(3.0 * a + ph + 0.6) + 7.0 * sin(5.0 * a - 2.0 * ph + 1.3) + 5.0 * sin(2.0 * a + 3.0 * ph + 0.4) + 3.0 * sin(7.0 * a + ph * 4.0);
}

float slab(vec2 p) {
  vec2 q = undeform(p);
  return sdBox(q, hs, corner) - wobble(q);
}

float hole(vec2 p) {
  vec2 q = undeform(p);
  vec2 c = win.xy + win.zw * 0.5;
  return sdBox(q - c, win.zw * 0.5, winR) + 2.0 * sin(3.0 * atan(q.y - c.y, q.x - c.x) + TAU * t / loop);
}

vec3 jelly(vec2 q) {
  // gummy pieces of colour in a milky, silvery glass
  float ph = TAU * (t / loop + swirl);
  vec3 col = vec3(0.80, 0.81, 0.88) + 0.06 * sin(q.y * 0.03 + ph);
  vec3 cs[4];
  cs[0] = vec3(1.0, 0.45, 0.74);
  cs[1] = vec3(0.40, 0.70, 1.0);
  cs[2] = vec3(1.0, 0.62, 0.30);
  cs[3] = vec3(0.50, 0.88, 0.62);
  for (int i = 0; i < 8; i++) {
    float fi = float(i);
    vec2 c = vec2(cos(ph * (1.0 + mod(fi, 2.0)) + fi * 2.1) * (60.0 + 9.0 * fi),
                  sin(ph * (1.0 + mod(fi, 3.0) * 0.0) + fi * 1.37) * (150.0 - 6.0 * fi));
    vec2 w = q + vec2(sin(q.y * 0.045 + ph + fi), cos(q.x * 0.05 - ph + fi * 0.7)) * 22.0;
    vec2 dd = (w - c) / vec2(74.0 + 8.0 * mod(fi, 3.0), 92.0 - 6.0 * mod(fi, 4.0));
    float f = exp(-dot(dd, dd));
    float body = smoothstep(0.08, 0.75, f);
    vec3 cc = i == 0 || i == 4 ? cs[0] : i == 1 || i == 5 ? cs[1] : i == 2 || i == 6 ? cs[2] : cs[3];
    col = mix(col, cc, body * 0.72);
    col += vec3(1.0) * smoothstep(0.8, 0.97, f) * 0.18;
  }
  return col;
}

half4 main(vec2 xy) {
  vec2 p = xy - center;
  float d = slab(p);
  if (d > 1.5) return half4(0.0);
  // gradient of the outline: the bevel's normal in the plane
  float e = 0.75;
  vec2 g = vec2(slab(p + vec2(e, 0.0)) - slab(p - vec2(e, 0.0)), slab(p + vec2(0.0, e)) - slab(p - vec2(0.0, e))) / (2.0 * e);
  float gl = max(length(g), 1e-4);
  g /= gl;
  // bevel profile: a quarter circle, flat inside
  float k = clamp(-d / bevel, 0.0, 1.0);
  float slope = (1.0 - k) / sqrt(max(1.0 - (1.0 - k) * (1.0 - k), 0.02));
  slope = min(slope, 4.0);
  vec3 n = normalize(vec3(-g * slope, 1.0));

  vec2 q = undeform(p);
  // the window: the dish behind its own rim
  float dh = hole(p);
  vec3 col;
  float edgeH = 0.0;
  if (dh < 0.0) {
    vec2 c = win.xy + win.zw * 0.5;
    float kh = clamp(-dh / 12.0, 0.0, 1.0);
    vec2 gh = vec2(hole(p + vec2(e, 0.0)) - hole(p - vec2(e, 0.0)), hole(p + vec2(0.0, e)) - hole(p - vec2(0.0, e))) / (2.0 * e);
    vec2 refr = -normalize(gh + 1e-4) * (1.0 - kh) * 7.0;
    vec2 uv = (q + refr - win.xy) / win.zw;
    // cover-fit the photo into the window
    float ia = dishSize.x / dishSize.y;
    float wa = win.z / win.w;
    vec2 s = ia > wa ? vec2(wa / ia, 1.0) : vec2(1.0, ia / wa);
    vec2 iuv = (uv - 0.5) * s * 0.74 + vec2(0.5, 0.47);
    col = dish.eval(iuv * dishSize).rgb;
    col *= 0.86 + 0.14 * kh;
    edgeH = (1.0 - kh) * (1.0 - kh);
  } else {
    // refracted jelly under the bevel
    vec2 r = q - g * slope * 16.0;
    col = jelly(r);
    // the window's thick rim, seen from the glass side: bright and silvery
    float rim = 1.0 - smoothstep(0.0, 18.0, dh);
    col = mix(col, vec3(0.95, 0.96, 1.0), rim * 0.7);
    col = mix(col, vec3(0.2, 0.2, 0.3), exp(-pow((dh - 7.0) / 2.2, 2.0)) * 0.55);
    // chromatic fringe along the outer bevel
    float fr = (1.0 - k);
    col.r += fr * 0.06 * g.x;
    col.b -= fr * 0.06 * g.x;
  }
  // fresnel: grazing angles turn to silver-white
  float fres = pow(1.0 - n.z, 1.6);
  col = mix(col, vec3(0.94, 0.95, 1.0), clamp(fres * 1.25, 0.0, 0.85));
  // thick glass: a dark refracted ring inside the bevel, a bright line just inside the edge
  float ring = exp(-pow((d + bevel * 0.5) / 4.5, 2.0));
  col = mix(col, vec3(0.16, 0.16, 0.24), ring * 0.5);
  float edgeLine = exp(-pow((d + 3.5) / 2.2, 2.0));
  col = mix(col, vec3(1.0), edgeLine * 0.75);
  // studio strips reflected in the bevel, sliding with the light
  float strips = smoothstep(0.62, 0.92, sin(n.x * 6.0 + n.y * 3.5 + light.x * 3.0) * 0.5 + 0.5);
  col = mix(col, vec3(1.0), strips * (1.0 - k) * 0.8);
  // specular highlights from a moving light, and a second soft one
  vec3 L = normalize(vec3(light, 0.9));
  vec3 H = normalize(L + vec3(0.0, 0.0, 1.0));
  float spec = pow(max(dot(n, H), 0.0), 60.0) * (1.0 - k * 0.6);
  float spec2 = pow(max(dot(n, normalize(vec3(-light * 0.6, 1.0))), 0.0), 8.0) * (1.0 - k) * 0.25;
  col += vec3(1.0) * (spec * 0.9 + spec2);
  col += vec3(1.0) * edgeH * 0.5;
  // a soft inner shadow line just inside the outline, so the slab has a thickness
  float lip = smoothstep(-3.0, -1.0, d) * (1.0 - smoothstep(-1.0, 1.0, d));
  col = mix(col, vec3(0.42, 0.42, 0.55), lip * 0.6);
  col += vec3(pulse * 0.25 * (1.0 - k * 0.5));
  // the label on the surface
  half4 lab = label.eval((q - labelOrigin) * labelScale);
  col = col * (1.0 - lab.a) + lab.rgb;
  float a = 1.0 - smoothstep(-0.5, 1.5, d);
  // the glass lets a little of the dark page through at the very edge
  a *= mix(0.86, 1.0, smoothstep(0.0, 0.25, k));
  return half4(half3(clamp(col, 0.0, 1.0) * a), a);
}`;
