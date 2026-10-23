package com.ngocdevv.nativecook.blocks.pickers.stampdiarycalendar

/**
 * One AGSL effect draws every stamp (API 33+), ported from shaders.ts: the perforated paper, the
 * page curl, the gummed back, the shadow the curl throws, a sheen while it flutters and the
 * postmark ink. Children: `art` (the illustration, in image pixels) and `mark` (the postmark's soft
 * alpha field, in its own pixels). Uniforms are documented in shaders.ts; `view` is
 * (x offset px, y offset px, px per reference point), h0..h2 are the rows of the inverse of the
 * stamp's matrix (reference points to the stamp's centred points) and bounds the half size outside
 * which nothing is drawn. The output is sRGB, so the Display P3 conversions are gone.
 */
internal object StampDiaryCalendarShaderSource {
    const val STAMP = """
uniform shader art;
uniform shader mark;
uniform float4 box;
uniform float4 crop;
uniform float4 holes;
uniform float4 curl;
uniform float4 sheen;
uniform float4 ink;
uniform float4 inkfx;
uniform float4 view;
uniform float3 h0;
uniform float3 h1;
uniform float3 h2;
uniform float2 bounds;

const float PI = 3.14159265;

float hs(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

float vn(float2 q) {
  float2 i = floor(q);
  float2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  float a = hs(i);
  float b = hs(i + float2(1.0, 0.0));
  float c = hs(i + float2(0.0, 1.0));
  float d = hs(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Paper coverage of a point of the flat stamp: the rectangle minus half-round holes on its edges.
float paper(float2 q) {
  float w = box.x;
  float h = box.y;
  float aa = box.z;
  float2 hw = float2(w, h) * 0.5;
  float2 e = hw - abs(q);
  float edge = min(e.x, e.y);
  if (edge < -aa) return 0.0;
  float px = w / holes.x;
  float py = h / holes.y;
  float rad = holes.z * min(px, py);
  float2 t = q + hw;
  float hx = (floor(t.x / px) + 0.5) * px;
  float hy = (floor(t.y / py) + 0.5) * py;
  float d1 = length(float2(t.x - hx, t.y));
  float d2 = length(float2(t.x - hx, t.y - h));
  float d3 = length(float2(t.x, t.y - hy));
  float d4 = length(float2(t.x - w, t.y - hy));
  float dn = min(min(d1, d2), min(d3, d4));
  return smoothstep(-aa, aa, edge) * smoothstep(rad - aa, rad + aa, dn);
}

float4 artAt(float2 q) {
  float2 uv = clamp(q / box.xy + 0.5, 0.0, 1.0);
  return art.eval(crop.xy + uv * crop.zw);
}

// Postmark ink density at a point of the flat stamp (0 = clean paper).
float inkAt(float2 q) {
  if (sheen.w < 0.5) return 0.0;
  float2 d = q - ink.xy;
  float cs = cos(ink.w);
  float sn = sin(ink.w);
  float2 pm = float2(cs * d.x - sn * d.y, sn * d.x + cs * d.y) * ink.z + inkfx.zw;
  float field = mark.eval(pm).a;
  float grain = vn(q * 1.7) * 0.6 + vn(q * 5.3) * 0.4;
  float th = inkfx.y + (grain - 0.5) * 0.22;
  float dens = smoothstep(th - 0.07, th + 0.07, field);
  // The ink reaches out from the ring with a ragged front.
  float reach = inkfx.x * 1.15 * box.x;
  float rr = length(d) + (vn(q * 0.35) - 0.5) * 0.22 * box.x;
  dens *= 1.0 - smoothstep(reach - 0.07 * box.x, reach, rr);
  // A rubber stamp leaves voids where the paper's tooth did not take ink.
  dens *= 0.7 + 0.3 * smoothstep(0.22, 0.62, vn(q * 9.0));
  return dens;
}

// Front of the paper: the art, shaded, with the ink pressed into it.
float3 front(float2 q, float shade) {
  float4 c = artAt(q);
  float3 rgb = c.a > 0.001 ? c.rgb / c.a : float3(0.0);
  float dens = inkAt(q);
  if (dens > 0.0) {
    float3 dry = float3(0.13, 0.14, 0.2);
    float3 wet = float3(0.16, 0.2, 0.38);
    float3 tint = mix(dry, wet, sheen.z);
    rgb = mix(rgb, rgb * tint, dens * 0.93);
    rgb += sheen.z * dens * 0.08 * vn(q * 2.3);
  }
  return rgb * shade;
}

// Back of the paper: warm gummed stock, a little of the picture showing through.
float3 back(float2 q, float shade, float gloss) {
  float3 stock = float3(0.93, 0.905, 0.85);
  float fibre = vn(q * float2(0.9, 2.6)) * 0.05 + vn(q * 7.0) * 0.03;
  float4 c = artAt(q);
  float3 rgb = c.a > 0.001 ? c.rgb / c.a : stock;
  float lum = dot(rgb, float3(0.3, 0.59, 0.11));
  float3 col = stock * (0.95 + fibre) * (0.93 + 0.07 * lum);
  return col * shade + gloss;
}

// Coverage of the lifted paper (rolled or folded back) at a screen point.
float lifted(float2 p) {
  float r = curl.w;
  float x = dot(p, curl.xy) - curl.z;
  if (x > r) return 0.0;
  if (x >= 0.0) {
    float th = asin(clamp(x / r, 0.0, 1.0));
    float a1 = paper(p + curl.xy * (r * (PI - th) - x));
    float a2 = paper(p + curl.xy * (r * th - x));
    return max(a1, a2);
  }
  return paper(p + curl.xy * (PI * r - 2.0 * x));
}

half4 shade(float2 p) {
  float dim = box.w;
  float r = curl.w;
  float3 lightDir = normalize(float3(-0.45, -0.75, 1.1));
  // Flat stamp: one layer.
  if (r <= 0.0) {
    float a = paper(p);
    if (a <= 0.0) return half4(0.0);
    float3 rgb = front(p, 1.0);
    float band = dot(p / box.xy, float2(0.7071, 0.7071)) - sheen.x + 0.5;
    rgb += sheen.y * exp(-band * band / 0.025);
    return half4(rgb * dim * a, a);
  }
  float2 n = curl.xy;
  float x = dot(p, n) - curl.z;
  float lx = dot(lightDir.xy, n);
  float lz = lightDir.z;
  float4 top = float4(0.0);
  float4 under = float4(0.0);
  bool underFlat = false;
  if (x > r) {
    // Beyond the roll: the paper that was here has been lifted away.
  } else if (x >= 0.0) {
    float s = clamp(x / r, 0.0, 1.0);
    float t2 = PI - asin(s);
    float2 q2 = p + n * (r * t2 - x);
    float a2 = paper(q2);
    if (a2 > 0.0) {
      float dif = sin(t2) * lx - cos(t2) * lz;
      float3 hv = normalize(lightDir + float3(0.0, 0.0, 1.0));
      float spec = pow(max(0.0, sin(t2) * dot(hv.xy, n) - cos(t2) * hv.z), 28.0) * 0.35;
      top = float4(back(q2, 0.5 + 0.5 * max(dif, 0.0) / lz, spec), a2);
    }
    float t1 = asin(s);
    float2 q1 = p + n * (r * t1 - x);
    float a1 = paper(q1);
    if (a1 > 0.0) {
      float dif = -sin(t1) * lx + cos(t1) * lz;
      under = float4(front(q1, 0.42 + 0.58 * max(dif, 0.0) / lz), a1);
    }
  } else {
    float2 q3 = p + n * (PI * r - 2.0 * x);
    float a3 = paper(q3);
    if (a3 > 0.0) top = float4(back(q3, 0.97, 0.0), a3);
    float a0 = paper(p);
    if (a0 > 0.0) {
      under = float4(front(p, 1.0), a0);
      underFlat = true;
    }
  }
  // The lifted paper throws a soft shadow down and to the right, onto the stamp and the page.
  float sh = 0.0;
  if (holes.w > 0.0) {
    float2 o = normalize(float2(0.45, 0.75)) * max(r, 2.0) * 0.9;
    sh = (lifted(p - o * 0.35) + lifted(p - o * 0.75) + lifted(p - o * 1.2) + lifted(p - o * 1.7)) * 0.25;
    if (underFlat) sh = max(sh, (1.0 - smoothstep(0.0, 0.7 * r, -x)) * 0.6);
    sh *= holes.w;
  }
  float3 rgb = under.rgb * (1.0 - sh) * under.a;
  float alpha = under.a;
  // Shadow on the page where nothing covers it.
  float pageShadow = sh * 0.75 * (1.0 - under.a);
  alpha += pageShadow;
  // The rolled or folded paper on top.
  rgb = top.rgb * top.a + rgb * (1.0 - top.a);
  alpha = top.a + alpha * (1.0 - top.a);
  return half4(rgb * dim, alpha);
}

// Screen pixel -> point of the stamp, through the inverse of its matrix (rows h0..h2, reference points).
half4 main(float2 fragCoord) {
  float2 ref = (fragCoord - view.xy) / view.z;
  float3 r = float3(ref, 1.0);
  float3 q = float3(dot(h0, r), dot(h1, r), dot(h2, r));
  float2 p = q.xy / q.z;
  if (abs(p.x) > bounds.x || abs(p.y) > bounds.y) return half4(0.0);
  return shade(p);
}
"""

    const val SHADOW = """
uniform float4 view;
uniform float3 h0;
uniform float3 h1;
uniform float3 h2;
uniform float4 box;

// Soft black rectangle (a Gaussian-blurred box, sigma = box.z); box: w, h, sigma, opacity.
half4 main(float2 fragCoord) {
  float2 ref = (fragCoord - view.xy) / view.z;
  float3 r = float3(ref, 1.0);
  float3 q = float3(dot(h0, r), dot(h1, r), dot(h2, r));
  float2 p = q.xy / q.z;
  float sigma = max(box.z, 0.01);
  float2 hw = box.xy * 0.5;
  float2 cdf0 = 1.0 / (1.0 + exp(-1.702 * (p + hw) / sigma));
  float2 cdf1 = 1.0 / (1.0 + exp(-1.702 * (p - hw) / sigma));
  float2 cover = cdf0 - cdf1;
  return half4(0.0, 0.0, 0.0, cover.x * cover.y * box.w);
}
"""
}
