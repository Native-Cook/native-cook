// The card's surface in one pass: thermochromic ink over the map.
//
// Heat is a sum of Gaussian blobs (up to 24, each x, y, strength, radius in card points) plus a
// broad "whole card" warmth `glob`, broken up by low noise so the reveal front is blotchy like
// real ink. Cold, the card is dark ink (#1b1f2b) printed with fine guilloché lines; as it warms
// the ink runs through deep violet → magenta → orange → yellow and clears to the map beneath.
// A diagonal gloss band (position and strength from `gloss`), a 1 pt top highlight and a darker
// bottom edge give the plastic its thickness. Rounded corners are cut here (premultiplied
// alpha), so the canvas needs no clip. Colours are sRGB, converted to Display P3 at the end.

export const THERMAL_SKSL = `
uniform shader map;
uniform float2 size;
uniform float4 heat[24];
uniform float glob;
uniform float2 gloss;
uniform float radius;

float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.03 + float2(17.0, 9.0);
    a *= 0.5;
  }
  return v;
}

float sdRoundRect(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Child shaders (the map image) arrive in the working space, Display P3 on iOS: back to sRGB.
float3 toSrgb(float3 p) {
  float3 l = pow(max(p, float3(0.0)), float3(2.2));
  float3 s = float3(1.2249 * l.r - 0.2247 * l.g, -0.0420 * l.r + 1.0419 * l.g, -0.0197 * l.r - 0.0786 * l.g + 1.0979 * l.b);
  return pow(max(s, float3(0.0)), float3(1.0 / 2.2));
}

float3 toP3(float3 s) {
  float3 l = pow(max(s, float3(0.0)), float3(2.2));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g, 0.0332 * l.r + 0.9669 * l.g, 0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return pow(max(p, float3(0.0)), float3(1.0 / 2.2));
}

half4 main(float2 p) {
  float2 c = size * 0.5;
  float sd = sdRoundRect(p - c, c, radius);
  float alpha = clamp(0.5 - sd, 0.0, 1.0);
  if (alpha <= 0.0) return half4(0.0);

  float H = 0.0;
  for (int i = 0; i < 24; i++) {
    float4 h = heat[i];
    if (h.z > 0.001) {
      float2 d = p - h.xy;
      H += h.z * exp(-dot(d, d) / (2.0 * h.w * h.w));
    }
  }
  float2 cq = (p - c) / size;
  H += glob * (1.25 - 0.7 * length(cq));
  H += (fbm(p * 0.03) - 0.5) * 0.16 * clamp(H * 2.0, 0.0, 1.0);
  float t = smoothstep(0.16, 0.95, H);

  // Ink with a printed guilloché: two families of fine wavy rings and a dot screen.
  float g1 = sin(length(p - float2(-0.2 * size.x, 1.1 * size.y)) * 0.42 + sin(p.x * 0.05) * 2.0);
  float g2 = sin(length(p - float2(1.25 * size.x, -0.1 * size.y)) * 0.38 + sin(p.y * 0.045) * 2.0);
  float lines = smoothstep(0.88, 1.0, g1) * 0.55 + smoothstep(0.9, 1.0, g2) * 0.4;
  float3 ink = float3(0.106, 0.122, 0.169);
  ink = mix(ink, float3(0.2, 0.22, 0.3), lines);
  float2 cell = fract(p / 4.0) - 0.5;
  ink *= 1.0 - 0.06 * smoothstep(0.28, 0.12, length(cell));
  ink *= 0.92 + 0.16 * (1.0 - cq.y - 0.5);

  float3 mapc = toSrgb(float3(map.eval(p).rgb));
  float3 col = ink;
  col = mix(col, float3(0.231, 0.114, 0.431), smoothstep(0.0, 0.22, t));
  col = mix(col, float3(0.72, 0.157, 0.478), smoothstep(0.18, 0.42, t));
  col = mix(col, float3(0.94, 0.416, 0.165), smoothstep(0.38, 0.6, t));
  col = mix(col, float3(1.0, 0.816, 0.29), smoothstep(0.56, 0.72, t));
  // The yellow stage is already half clear, as the dye fades.
  col = mix(col, mapc, smoothstep(0.66, 0.9, t) * 0.75 + smoothstep(0.86, 0.98, t) * 0.25);

  // Plastic: gloss band, a broad sheen, grain, a lit top edge and a darker bottom edge.
  float along = (p.x * 0.72 + p.y * 0.38) / size.x;
  float band = exp(-(along - gloss.x) * (along - gloss.x) / 0.003) * gloss.y;
  float sheen = exp(-(along - gloss.x + 0.25) * (along - gloss.x + 0.25) / 0.09) * gloss.y;
  col += float3(1.0, 0.98, 0.95) * (band * 0.14 + sheen * 0.05);
  col *= 1.0 + (hash(floor(p)) - 0.5) * 0.05;
  float rim = smoothstep(1.6, 0.0, abs(sd + 1.0));
  col += float3(1.0) * rim * 0.16 * smoothstep(0.3, -0.5, cq.y);
  col *= 1.0 - rim * 0.3 * smoothstep(-0.2, 0.5, cq.y);

  col = toP3(clamp(col, 0.0, 1.0));
  return half4(half3(col) * half(alpha), half(alpha));
}
`;
