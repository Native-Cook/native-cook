package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

// Shader equations and uniform names preserved from the React Native source.
internal const val CONTROLS_AGSL = """
uniform float2 uOrigin;
uniform float2 uSize;
uniform float uRadius;
uniform float uYaw;
uniform float uLamp;
uniform float uFlash;
uniform float uPress;
uniform float uSheen;
uniform float uGem;
uniform float2 uPressPoint;
float roundedBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float octagon(float2 p, float r) {
  float2 q = abs(p);
  return max(max(q.x, q.y), (q.x + q.y) * 0.70710678) - r;
}
float engraving(float2 p) {
  float2 a = float2((p.x + p.y) * 0.70710678, (p.x - p.y) * 0.70710678);
  return min(length(float2(max(abs(a.x) - 5.5, 0.0), a.y)),
    length(float2(a.x, max(abs(a.y) - 5.5, 0.0)))) - 1.5;
}
float star(float2 p, float strength) {
  float core = exp(-dot(p, p) / 0.28);
  float arms = max(exp(-abs(p.x) * 5.5 - abs(p.y) * 0.8),
    exp(-abs(p.y) * 5.5 - abs(p.x) * 0.8));
  return (core + arms * 0.65) * strength;
}
half4 main(float2 xy) {
  float2 p = xy - uOrigin;
  float2 q = p - uSize * 0.5;
  float d = uGem > 0.5 ? octagon(q, 17.2) : roundedBox(q, uSize * 0.5 - 0.8, uRadius);
  float cover = 1.0 - smoothstep(-0.35, 0.35, d);
  float v = clamp(p.y / max(uSize.y, 1.0), 0.0, 1.0);
  float h = p.x / max(uSize.x, 1.0);
  // Premultiplied pigments retain the floor/cloth behind the crystal instead of painting it black.
  float upperMix = smoothstep(0.0, 0.45, v);
  float lowerMix = smoothstep(0.45, 1.0, v);
  float alpha = mix(mix(0.22, 0.16, upperMix), 0.60, lowerMix);
  float3 pigment = mix(mix(float3(150,170,255) / 255.0 * 0.22,
    float3(46,60,140) / 255.0 * 0.16, upperMix), float3(6,8,26) / 255.0 * 0.60, lowerMix);
  float glowX = (h - 0.39 - 0.065 * sin(uYaw)) / 0.32;
  float glowY = (v - 0.40) / 0.44;
  pigment += exp(-glowX * glowX - glowY * glowY) * float3(0.018, 0.025, 0.070);
  float band = exp(-max(p.y - 2.0, 0.0) / 4.8) * 0.10;
  pigment += band * float3(0.72, 0.82, 1.0);
  float rim = 1.0 - smoothstep(0.6, 1.65, abs(d + 0.85));
  float upper = 1.0 - smoothstep(0.03, 0.70, v);
  float hot = exp(-((h - 0.52) * (h - 0.52)) / 0.065);
  float bevel = rim * upper * (0.18 + 0.62 * hot);
  // Twelve-point end facets with an inner cut face; a second line makes thickness legible.
  float edgeX = min(p.x, uSize.x - p.x);
  float cut = (1.0 - smoothstep(11.5, 12.5, edgeX)) * smoothstep(1.0, 4.5, -d);
  pigment += cut * float3(0.044, 0.058, 0.10) * (0.35 + 0.65 * (1.0 - v));
  pigment += exp(-abs(edgeX - 12.0) * 2.6) * float3(0.05, 0.073, 0.12) * smoothstep(3.0, 10.0, p.y) * (1.0 - v);
  float lip = exp(-abs(d + 0.9) * 3.0) * smoothstep(0.80, 0.98, v);
  pigment *= 1.0 - lip * 0.65;
  float cyan = exp(-abs(d + 2.3) * 3.0) * smoothstep(0.82, 0.98, v) * 0.22;
  pigment += cyan * float3(0.25, 0.64, 0.83);
  if (uGem > 0.5) {
    float ax = abs(q.x), ay = abs(q.y);
    float diagonal = (ax + ay) * 0.70710678;
    float2 normal = diagonal > max(ax, ay) ? sign(q) * 0.70710678
      : ax > ay ? float2(sign(q.x), 0.0) : float2(0.0, sign(q.y));
    float lit = max(0.0, dot(normal, float2(-0.6, -0.8)));
    float facets = smoothstep(-3.5, -2.7, d);
    pigment = mix(pigment + float3(0.019,0.027,0.060),
      float3(0.024,0.038,0.070) + lit * float3(0.18,0.23,0.32), facets);
    alpha = 0.68;
    bevel = rim * (0.045 + lit * 0.66);
    pigment += exp(-abs(d + 3.2) * 3.5) * (0.03 + lit * 0.13) * float3(0.55,0.76,1.0);
  }
  pigment = pigment * (1.0 - bevel) + bevel * float3(0.88, 0.94, 1.0);
  alpha = alpha + (1.0 - alpha) * bevel;
  float2 pressDelta = (p - uPressPoint * uSize) / (uGem > 0.5 ? float2(14.0) : float2(55.0, 25.0));
  float spot = exp(-dot(pressDelta, pressDelta)) * max(0.0, uPress);
  pigment += spot * float3(0.08,0.11,0.19);
  float sheenX = h + v * 0.17 - (uSheen * 1.5 - 0.25);
  float sheen = uSheen < 0.0 ? 0.0 : exp(-sheenX * sheenX / 0.004);
  pigment += sheen * float3(0.065,0.09,0.14);
  if (uGem > 0.5) {
    float frost = 1.0 - smoothstep(-0.22, 0.22, engraving(q));
    float wall = (1.0 - smoothstep(-0.2, 0.2, engraving(q - float2(0.6,0.7)))) * (1.0 - frost);
    float inset = (1.0 - smoothstep(-0.2, 0.5, engraving(q + float2(0.45,0.5)))) * (1.0 - frost);
    pigment *= 1.0 - inset * 0.80;
    pigment = mix(pigment, float3(0.73,0.86,1.0), wall * 0.85);
    // PALETTE.frost, with a subdued centre so the luminous cut wall remains visible.
    pigment = mix(pigment, float3(244,248,255) / 255.0 * (0.82 + v * 0.08), frost);
    alpha = max(alpha, max(frost, wall));
  }
  // Analytic floor shadow and caustic extend outside the bar's silhouette; no blur layer allocation.
  float floorY = p.y - uSize.y - 6.0;
  float floorX = q.x / max(uSize.x * 0.46, 1.0);
  float shadow = uGem > 0.5 ? 0.0 : exp(-floorX * floorX * 1.5 - floorY * floorY / 17.0) * 0.40;
  float causticX = (h - 0.46) / 0.29;
  float causticY = (p.y - uSize.y - 3.5) / 2.3;
  float caustic = uGem > 0.5 ? 0.0 : exp(-causticX * causticX - causticY * causticY) * 0.07 * (0.6 + uLamp * 0.4);
  float3 rgb = float3(0.32,0.52,1.0) * caustic;
  float outAlpha = shadow + caustic * (1.0 - shadow);
  rgb = pigment * cover + rgb * (1.0 - alpha * cover);
  outAlpha = alpha * cover + outAlpha * (1.0 - alpha * cover);
  float2 glintPoint = uGem > 0.5 ? float2(7.0,3.1) : float2(uSize.x * (0.60 + 0.25 * sin(uYaw)),1.0);
  float glint = star(p - glintPoint, uGem > 0.5 ? 0.15 + uFlash * 0.65 : 0.18 + uLamp * 0.24 + uFlash * 0.58);
  rgb += glint * float3(0.79,0.90,1.0);
  outAlpha = max(outAlpha, min(glint,1.0));
  // Additive highlights still obey premultiplied-alpha requirements at the silhouette.
  outAlpha = max(outAlpha, min(1.0, max(rgb.r,max(rgb.g,rgb.b))));
  return half4(half3(min(rgb,float3(outAlpha))), half(outAlpha));
}"""
