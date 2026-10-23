package com.ngocdevv.nativecook.blocks.lists.glassfolderburst

/** Original shader maths with P3/sRGB conversion only at the Android render boundary. */
internal object GlassFolderBurstShaders {
    val back = """
float3 linearChannels(float3 c) {
  return mix(c / 12.92, pow((max(c, 0.0) + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c));
}
float3 encodedChannels(float3 c) {
  c = max(c, 0.0);
  return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}
half4 p3ToSrgb(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(1.224745*v.r - 0.224904*v.g, -0.042058*v.r + 1.042081*v.g,
    -0.019642*v.r - 0.078655*v.g + 1.098537*v.b);
  return half4(clamp(encodedChannels(rgb), 0.0, 1.0) * c.a, c.a);
}
half4 srgbToP3(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(0.822592*v.r + 0.177534*v.g, 0.033200*v.r + 0.966784*v.g,
    0.017085*v.r + 0.072396*v.g + 0.910302*v.b);
  return half4(encodedChannels(rgb) * c.a, c.a);
}

uniform float2 center;
uniform float2 stretch;
uniform float opened;
uniform float flow;
half4 backSurface(float2 q, float opened, float flow, float frost) {
  float2 b = abs(q) - float2(46.0, 36.0);
  float d = length(max(b, 0.0)) + min(max(b.x, b.y), 0.0) - 16.0;
  float h = clamp((q.y + 52.0) / 104.0, 0.0, 1.0);
  float3 col = mix(float3(0.075, 0.225, 0.155), float3(0.024, 0.082, 0.055), h) * 0.9;
  col *= 1.0 - opened * 0.36 * smoothstep(-48.0, -18.0, q.y);
  col += float3(0.11, 0.67, 0.35) * flow * exp(-(q.y + 22.0) * (q.y + 22.0) / 320.0) * 0.46;
  col += float3(0.65, 0.91, 0.75) * exp(-d * d / (0.5 + 2.0 * frost * frost)) * sqrt(0.5 / (0.5 + 2.0 * frost * frost)) * mix(0.50, 0.18, h);
  col += float3(0.20, 0.48, 0.32) * exp(-(q.y + 49.0) * (q.y + 49.0) / 2.0) * 0.09;
  float aa = 0.4 + 1.7 * frost;
  float alpha = 1.0 - smoothstep(-aa, aa, d);
  return half4(col * alpha, alpha);
}

half4 sourceMain(float2 p) { return backSurface((p - center) / stretch, opened, flow, 0.0); }

half4 main(float2 p) { return p3ToSrgb(sourceMain(p)); }
""".trimIndent()

    val orb = """
float3 linearChannels(float3 c) {
  return mix(c / 12.92, pow((max(c, 0.0) + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c));
}
float3 encodedChannels(float3 c) {
  c = max(c, 0.0);
  return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}
half4 p3ToSrgb(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(1.224745*v.r - 0.224904*v.g, -0.042058*v.r + 1.042081*v.g,
    -0.019642*v.r - 0.078655*v.g + 1.098537*v.b);
  return half4(clamp(encodedChannels(rgb), 0.0, 1.0) * c.a, c.a);
}
half4 srgbToP3(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(0.822592*v.r + 0.177534*v.g, 0.033200*v.r + 0.966784*v.g,
    0.017085*v.r + 0.072396*v.g + 0.910302*v.b);
  return half4(encodedChannels(rgb) * c.a, c.a);
}

uniform float2 center;
uniform float radius;
uniform float glow;
uniform float beat;
uniform float mark;
uniform float time;
half4 orbSurface(float2 p, float2 center, float radius, float glow, float beat, float mark, float time, float frost) {
  float2 q = (p - center) / max(0.01, radius);
  float rr = length(q);
  float aa = (0.7 + 1.7 * frost) / max(1.0, radius);
  float disc = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, rr);
  float h = clamp(length(q - float2(-0.32, -0.42)) / 1.55, 0.0, 1.0);
  float3 col = mix(float3(0.714, 1.0, 0.839), float3(0.157, 0.878, 0.486), smoothstep(0.0, 0.7, h));
  col = mix(col, float3(0.039, 0.478, 0.263), smoothstep(0.80, 1.02, rr));
  col += float3(0.16, 0.30, 0.21) * exp(-(rr - 0.96) * (rr - 0.96) / (0.0008 + 2.0 * frost * frost / max(1.0, radius * radius)))
    * sqrt(0.0008 / (0.0008 + 2.0 * frost * frost / max(1.0, radius * radius))) * clamp(-q.y, 0.0, 1.0);
  float bars = 0.0;
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    float bh = i == 0 ? 0.50 : (i == 1 ? 0.95 : (i == 2 ? 0.70 : 0.40));
    bh *= 1.0 + 0.25 * beat * sin(time * 6.2831853 * (2.0 + fi) / 10.0 + fi * 1.8);
    float2 bp = abs(q - float2((fi - 1.5) * 0.28, 0.0)) - float2(0.0, max(0.08, bh * 0.5) - 0.08);
    float bd = length(max(bp, 0.0)) + min(max(bp.x, bp.y), 0.0) - 0.08;
    bars = max(bars, 1.0 - smoothstep(-aa, aa, bd));
  }
  col = mix(col, float3(0.016, 0.078, 0.043), bars * mark * 0.92);
  float halo = exp(-rr * rr * 1.35) * glow * 0.34 * (1.0 - smoothstep(1.8, 2.2, rr));
  float alpha = disc + (1.0 - disc) * halo;
  float3 rgb = clamp(col, 0.0, 1.0) * disc + float3(0.07, 0.85, 0.39) * halo * (1.0 - disc);
  return half4(rgb, alpha);
}

half4 sourceMain(float2 p) { return orbSurface(p, center, radius, glow, beat, mark, time, 0.0); }

half4 main(float2 p) { return p3ToSrgb(sourceMain(p)); }
""".trimIndent()

    val background = """
float3 linearChannels(float3 c) {
  return mix(c / 12.92, pow((max(c, 0.0) + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c));
}
float3 encodedChannels(float3 c) {
  c = max(c, 0.0);
  return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}
half4 p3ToSrgb(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(1.224745*v.r - 0.224904*v.g, -0.042058*v.r + 1.042081*v.g,
    -0.019642*v.r - 0.078655*v.g + 1.098537*v.b);
  return half4(clamp(encodedChannels(rgb), 0.0, 1.0) * c.a, c.a);
}
half4 srgbToP3(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(0.822592*v.r + 0.177534*v.g, 0.033200*v.r + 0.966784*v.g,
    0.017085*v.r + 0.072396*v.g + 0.910302*v.b);
  return half4(encodedChannels(rgb) * c.a, c.a);
}

uniform float2 size;
uniform float2 center;
uniform float scale;
uniform float flow;
uniform float time;
uniform float2 bloomCenter;
uniform float bloom;
uniform float2 caustic;
float fog(float2 p, float2 c, float2 r) { float2 q = (p-c)/r; return exp(-dot(q,q)*2.0); }
half4 sourceMain(float2 p) {
  float a = time * 6.2831853 / 10.0;
  float b = time * 6.2831853 / 5.0;
  float2 c1 = size * float2(0.24 + sin(a)*0.17, 0.71 + cos(a)*0.12);
  float2 c2 = size * float2(0.77 + cos(b)*0.16, 0.86 + sin(b)*0.10);
  float f = fog(p,c1,size*float2(0.7,0.34))*0.22 + fog(p,c2,size*float2(0.6,0.28))*0.14;
  float floorLight = fog(p, center + float2(0.0,58.0*scale), float2(84.0,6.0)*scale)*(0.06+flow*0.10);
  floorLight += fog(p, float2(caustic.x, center.y+58.0*scale), float2(46.0,4.0)*scale) * caustic.y * 0.16;
  float burst = fog(p,bloomCenter,float2(150.0))*bloom*0.32;
  float3 col = float3(0.055,0.361,0.220)*f + float3(0.08,0.65,0.31)*(floorLight+burst);
  float noise = fract(sin(dot(floor(p*2.0),float2(127.1,311.7)))*43758.5453)-0.5;
  col += noise * 0.02 * clamp((f+floorLight+burst)*3.0,0.0,1.0);
  return half4(max(col,0.0),1.0);
}

half4 main(float2 p) { return p3ToSrgb(sourceMain(p)); }
""".trimIndent()

    val glass = """
float3 linearChannels(float3 c) {
  return mix(c / 12.92, pow((max(c, 0.0) + 0.055) / 1.055, float3(2.4)), step(float3(0.04045), c));
}
float3 encodedChannels(float3 c) {
  c = max(c, 0.0);
  return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}
half4 p3ToSrgb(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(1.224745*v.r - 0.224904*v.g, -0.042058*v.r + 1.042081*v.g,
    -0.019642*v.r - 0.078655*v.g + 1.098537*v.b);
  return half4(clamp(encodedChannels(rgb), 0.0, 1.0) * c.a, c.a);
}
half4 srgbToP3(half4 c) {
  float3 v = linearChannels(c.rgb / max(c.a, 0.00001));
  float3 rgb = float3(0.822592*v.r + 0.177534*v.g, 0.033200*v.r + 0.966784*v.g,
    0.017085*v.r + 0.072396*v.g + 0.910302*v.b);
  return half4(encodedChannels(rgb) * c.a, c.a);
}

uniform shader image;

uniform float2 center;
uniform float2 stretch;
uniform float opened;
uniform float pressed;
uniform float flow;
uniform float time;
uniform float pd;
uniform float4 orb;
uniform float orbBeat;
uniform float orbMark;

float hash21(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}
float topAt(float x) {
  float startX = -28.0 - 3.0 * opened;
  float u = clamp((x - startX) / (-6.0 - startX), 0.0, 1.0);
  return -46.0 + 12.0 * opened + 12.0 * u * u * (3.0 - 2.0 * u);
}
// Distance to the smoothly joined body/tab. The top field's zero contour is the
// path's cubic, avoiding the spurious ridge an overlapping pair of strokes makes.
float panelField(float2 q) {
  float wide = 6.0 * opened;
  float base = -34.0 + 12.0 * opened;
  float startX = -28.0 - wide * 0.5;
  float u = clamp((q.x - startX) / (-6.0 - startX), 0.0, 1.0);
  float slope = 72.0 * u * (1.0 - u) / (-6.0 - startX);
  float upper = (topAt(q.x) - q.y) / sqrt(1.0 + slope * slope);
  float leftW = 66.0 + wide * clamp((36.0 - q.y) / (36.0 - base), 0.0, 1.0);
  float rightW = 66.0 + wide * clamp((36.0 - q.y) / (24.0 - base), 0.0, 1.0);
  float result = max(max(-q.x - leftW, q.x - rightW), max(upper, q.y - 52.0));
  if (q.x < -54.0 - wide && q.y < base) {
    result = length(q - float2(-54.0 - wide, base)) - 12.0;
  }
  if (q.x > 54.0 + wide && q.y < base + 12.0) {
    result = length(q - float2(54.0 + wide, base + 12.0)) - 12.0;
  }
  if (abs(q.x) > 50.0 && q.y > 36.0) {
    result = length(float2(abs(q.x) - 50.0, q.y - 36.0)) - 16.0;
  }
  return result;
}
float2 localPoint(float2 p) { return (p - center) / stretch; }
float2 panelNormal(float2 q) {
  float2 g = float2(panelField(q + float2(0.18, 0.0)) - panelField(q - float2(0.18, 0.0)),
    panelField(q + float2(0.0, 0.18)) - panelField(q - float2(0.0, 0.18)));
  return g / max(length(g), 0.0001);
}
// Broad 14 pt lens, with most of its 22 pt travel compressed into the outer 5 pt.
float bevelWeight(float depth) { return 1.0 - smoothstep(0.0, 14.0, depth); }
float bendAmount(float depth) {
  float broad = bevelWeight(depth);
  return 22.0 * (0.28 * broad * broad + 0.72 * (1.0 - smoothstep(0.0, 5.0, depth)));
}
float storedDisc() {
  float2 oq = localPoint(orb.xy);
  float rr = max(0.01, orb.z / stretch.y);
  float covered = smoothstep(-rr, rr, oq.y - topAt(oq.x));
  covered *= 1.0 - smoothstep(60.0, 66.0 + rr, abs(oq.x));
  covered *= 1.0 - smoothstep(52.0, 52.0 + rr, oq.y);
  return covered * orb.w * smoothstep(0.0, 0.1, orb.z);
}
float3 glassFinish(float3 scene, float2 q, float d, float2 n) {
  float depth = max(0.0, -d);
  float topDepth = max(0.0, q.y - topAt(q.x));
  float heightFade = clamp((q.y + 46.0) / 98.0, 0.0, 1.0);
  float3 body = mix(float3(0.075, 0.29, 0.19), float3(0.02, 0.08, 0.05), heightFade);
  float3 col = scene * float3(0.72, 1.0, 0.86) * 0.80 + body * 0.32;
  // Uniform across the mouth, including the entire level lip. No horizontal hotspot.
  float inwardGlow = exp(-topDepth / 11.0) * flow;
  col += mix(float3(0.157, 0.878, 0.486), float3(0.549, 1.0, 0.753), exp(-topDepth / 2.5)) * inwardGlow * 0.28;
  float key = clamp(dot(n, normalize(float2(-0.58, -0.82))), 0.0, 1.0);
  float topLight = smoothstep(0.12, 0.65, -n.y);
  float bottomLight = smoothstep(0.1, 0.85, n.y);
  float edgeLight = mix(0.22, 1.05, topLight);
  edgeLight = mix(edgeLight, 0.45, bottomLight);
  // A 3.5 pt rounded bevel separates the thin external reflection and inner line.
  float bevelBand = exp(-((depth - 2.2) * (depth - 2.2)) / 3.1);
  col *= 1.0 - bevelBand * (0.22 - 0.12 * key);
  col += float3(0.65, 0.90, 0.77) * bevelBand * (0.035 + 0.13 * key);
  float innerLine = exp(-((depth - 5.0) * (depth - 5.0)) / 0.45);
  col += float3(0.30, 0.72, 0.49) * innerLine * (0.035 + 0.025 * key);
  // Light gathered by the lower lens: concentrated under the stored disc, broad in flow.
  float underDisc = (q.x - localPoint(orb.xy).x) / 31.0;
  float causticSource = storedDisc() * exp(-underDisc * underDisc * 0.5) + flow * 0.85;
  float cornerBounce = smoothstep(38.0, 54.0, abs(q.x)) * smoothstep(28.0, 40.0, q.y);
  causticSource = max(causticSource, cornerBounce * (0.12 + 0.28 * storedDisc() + 0.4 * flow));
  float bottomBand = (depth - mix(6.5, 2.8, cornerBounce)) / 2.3;
  float caustic = exp(-bottomBand * bottomBand) * smoothstep(0.0, 0.65, n.y);
  col += float3(0.15, 0.95, 0.46) * caustic * causticSource * 0.32;
  // Both reflections slide along the top-left bevel, never across the tab junction.
  float drift = opened * 2.0 + pressed * 1.5 + sin(time * 6.2831853 / 5.0) * 1.5;
  float2 sheenQ = (q - float2(-57.0 + drift, -40.0 + 12.0 * opened)) / float2(36.0, 31.0);
  col += float3(0.90, 1.0, 0.96) * 0.12 * exp(-dot(sheenQ, sheenQ) * 1.3);
  // Arc length along the left side, round corner, then tab plateau (~40 pt total).
  float2 corner = float2(-54.0 - 6.0 * opened, -34.0 + 12.0 * opened);
  float2 cq = q - corner;
  float arcPos = cq.y > 0.0 ? -cq.y : (cq.x > 0.0 ? 14.92 + cq.x : atan(-cq.y, -cq.x) * 9.5);
  float arcWindow = smoothstep(-12.0 + drift, -3.0 + drift, arcPos)
    * (1.0 - smoothstep(19.0 + drift, 28.0 + drift, arcPos));
  float highlightDepth = (depth - 2.5) / 0.9;
  float cornerGate = (1.0 - smoothstep(-27.0, -23.0, q.x)) * (1.0 - smoothstep(0.0, 8.0, q.y));
  col += float3(0.90, 1.0, 0.96) * 0.45 * exp(-highlightDepth * highlightDepth) * arcWindow * cornerGate;
  col += (hash21(floor(q * 3.0)) - 0.5) * 0.016;
  // Reflection owns the outermost point, preventing a refracted disc hotspot at the step.
  float rim = exp(-((depth - 0.35) * (depth - 0.35)) / 0.25);
  col = mix(col, float3(0.80, 1.0, 0.90) * edgeLight, rim);
  return clamp(col, 0.0, 1.0);
}

half4 sourceMain(float2 frag) {
  float2 point = frag / pd; // DEVICE PIXELS -> CANVAS POINTS (only conversion).
  float2 q = localPoint(point);
  float d = panelField(q);
  float2 n = panelNormal(q);
  float depth = max(0.0, -d);
  float bevel = bevelWeight(depth);
  float2 bend = n * bendAmount(depth) * stretch;
  float2 magnified = center + (point - center) / 1.04;
  float blurR = mix(3.0, 1.2, bevel) * sqrt(stretch.x * stretch.y);
  float3 sum = float3(0.0);
  // 28 Gaussian spiral taps: 3 pt central sigma, 1.2 pt at the rim.
  // A fixed panel-space rotation breaks up the finite-sample pattern into frost.
  for (int i = 0; i < 28; i++) {
    float fi = float(i) + 0.5;
    float a = fi * 2.39996323 + hash21(floor(q * 3.0)) * 0.35;
    float2 offset = float2(cos(a), sin(a)) * sqrt(-2.0 * log(1.0 - fi / 28.0)) * blurR;
    float2 at = magnified + offset;
    half4 c = srgbToP3(image.eval((at + bend) * pd));
    if (bevel > 0.01) {
      c.r = srgbToP3(image.eval((at + bend * 0.94) * pd)).r;
      c.b = srgbToP3(image.eval((at + bend * 1.06) * pd)).b;
    }
    sum += c.rgb;
  }
  // The scene is an opaque black page; sampled premultiplied RGB is opaque here.
  return half4(glassFinish(sum / 28.0, q, d, n), 1.0);
}

half4 main(float2 p) { return p3ToSrgb(sourceMain(p)); }
""".trimIndent()

}
