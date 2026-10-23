// One page pass combines beveled glass, a faded reflection and the moving rarity ring.
export const FRAME_SKSL = `
uniform shader uLabel;
uniform float uHeat;
uniform float uRing;
uniform float uTab;
uniform float uClock;
float sq(float v) { return v * v; }
float roundedBox(float2 p, float2 extent, float radius) {
  float2 q = abs(p) - extent + radius;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
float3 hueAt(float2 p, float hot) {
  float along = clamp((p.x - 48.0) / 580.0 + (p.y - 128.0) / 760.0, 0.0, 1.0);
  float3 cool = mix(float3(0.16, 0.88, 1.0), float3(0.45, 0.24, 1.0), along);
  float3 gold = mix(float3(0.70, 0.95, 1.0), float3(1.0, 0.75, 0.35), clamp(along * 1.8, 0.0, 1.0));
  gold = mix(gold, float3(1.0, 0.60, 0.76), smoothstep(0.78, 1.0, along) * 0.4);
  return mix(cool, gold, hot);
}
float borderTravel(float2 p) {
  // Distance from each tab end, around the bottom corner, up its side, to the top centre.
  float ax = abs(p.x - 201.0);
  if (p.y > 590.0) { return max(0.0, ax - 64.0); }
  if (p.y < 155.0) { return 537.0 + 153.0 - ax; }
  return 89.0 + 607.0 - p.y;
}
float3 glass(float2 p) {
  float d = roundedBox(p - float2(201.0, 374.0), float2(153.0, 246.0), 28.0);
  float tr = borderTravel(p);
  float head = uRing * 690.0;
  float trail = head - tr;
  float comet = (uRing > 0.001 && uRing < 0.999) ? exp(-max(trail, 0.0) / 28.0) * smoothstep(-2.0, 1.5, trail) : 0.0;
  float warmed = uRing > 0.001 && uRing < 0.999 ? smoothstep(-5.0, 5.0, trail) * uHeat : uHeat;
  float3 hue = hueAt(p, max(warmed, uHeat * 0.55));
  float band = smoothstep(0.5, -0.5, d) * smoothstep(-12.6, -11.5, d);
  float outer = exp(-sq(d / 0.85));
  float bevel = exp(-sq((d + 2.2) / 2.8));
  float inner = exp(-sq((d + 11.0) / 0.9));
  float broadGlow = exp(-abs(d + 5.0) / 13.0) * 0.13;
  float3 colour = hue * (broadGlow + band * (0.34 + bevel * 0.42) + outer * 0.32 + inner * 0.36);
  colour += float3(0.80, 0.98, 1.0) * bevel * band * 0.09;
  float pane = smoothstep(-12.0, -13.0, d);
  float diagonal = exp(-sq((p.x + p.y * 0.48 - 230.0) / 94.0));
  colour += pane * (float3(0.023, 0.031, 0.049) + float3(0.027, 0.031, 0.049) * diagonal);
  float innerD = roundedBox(p - float2(201.0, 374.0), float2(133.0, 226.0), 15.0);
  colour += hue * exp(-sq(innerD / 0.52)) * 0.35;
  colour += float3(1.0, 0.97, 0.84) * comet * exp(-abs(d + 6.0) / 3.0) * 2.3;
  float headBloom = (uRing > 0.001 && uRing < 0.999) ? exp(-sq((tr - head) / 8.0) - sq((d + 6.0) / 13.0)) : 0.0;
  colour += float3(1.0, 0.96, 0.81) * headBloom * 0.6;
  // A flared trapezoid fuses into the bottom band; its top has a polished glass bevel.
  float ty = (p.y - 590.0) / 36.0;
  float tw = mix(64.0, 86.0, clamp(ty, 0.0, 1.0));
  float td = max(abs(p.x - 201.0) - tw, max(590.0 - p.y, p.y - 626.0));
  float tab = smoothstep(0.7, -0.5, td);
  float3 tabC = mix(float3(0.032, 0.062, 0.16), float3(0.13, 0.17, 0.37), clamp(ty, 0.0, 1.0));
  tabC = mix(tabC, float3(0.20, 0.14, 0.072), uHeat * 0.75);
  tabC += hue * (exp(-abs(td) * 1.4) * 0.34 + exp(-sq((p.y - 591.0) / 0.8)) * 0.5);
  colour = mix(colour, tabC * (0.8 + uTab * 0.6), tab);
  float2 labelUV = (p - float2(141.0, 601.0)) * 3.0;
  if (labelUV.x >= -3.0 && labelUV.x <= 363.0 && labelUV.y >= -3.0 && labelUV.y <= 45.0) {
    float alpha = uLabel.eval(labelUV).a;
    float halo = (uLabel.eval(labelUV + float2(3.0, 0.0)).a + uLabel.eval(labelUV - float2(3.0, 0.0)).a +
      uLabel.eval(labelUV + float2(0.0, 3.0)).a + uLabel.eval(labelUV - float2(0.0, 3.0)).a) * 0.25;
    float3 ink = mix(float3(0.66, 0.97, 1.0), float3(1.0, 0.71, 0.97), clamp(labelUV.x / 360.0, 0.0, 1.0));
    ink = mix(ink, float3(1.0, 0.94, 0.75), uHeat * 0.72);
    colour += ink * (alpha * (0.66 + uTab * 0.34) + halo * uTab * 0.14);
  }
  return colour;
}
half4 main(float2 p) {
  float vignette = exp(-sq((p.x - 201.0) / 210.0) - sq((p.y - 370.0) / 440.0));
  float3 page = mix(float3(0.0039, 0.0078, 0.0275), float3(0.0235, 0.0353, 0.0745), vignette);
  float2 grid = abs(mod(p - float2(1.0, 8.0), 40.0) - 20.0);
  float gridInk = max(exp(-sq(grid.x / 0.36)), exp(-sq(grid.y / 0.36))) * (1.0 - smoothstep(280.0, 590.0, p.y));
  page += float3(0.47, 0.59, 1.0) * gridInk * 0.035;
  float floorPool = exp(-sq((p.x - 201.0) / 129.0) - sq((p.y - 660.0) / 29.0));
  page += mix(float3(0.04, 0.10, 0.45), float3(0.38, 0.20, 0.06), uHeat) * floorPool * 0.37;
  if (p.y < 636.0) {
    page += glass(p);
  } else if (p.y < 786.0) {
    float fade = 0.30 * sq(max(0.0, 1.0 - (p.y - 636.0) / 150.0));
    float2 mirrorP = float2(p.x, 1272.0 - p.y);
    page += (glass(mirrorP + float2(1.4, 0.8)) + glass(mirrorP - float2(1.4, 0.8))) * 0.5 * fade;
  }
  return half4(half3(page), 1.0);
}
`;

export const RAYS_SKSL = `
uniform float uHeat;
uniform float uClock;
uniform float uAge;
half4 main(float2 p) {
  if (uHeat < 0.001) { return half4(0.0); }
  float2 q = p - float2(201.0, 364.0);
  float radius = length(q);
  float theta = atan(q.y, q.x);
  float phase = uClock * 6.28318530718 / 13.0;
  float rayA = pow(max(0.0, sin(theta * 13.0 + phase)), 8.0);
  float rayB = pow(max(0.0, sin(theta * 7.0 - phase)), 14.0);
  float paneFade = (1.0 - smoothstep(132.0, 151.0, abs(q.x))) * (1.0 - smoothstep(222.0, 244.0, abs(q.y)));
  float pulse = uAge < 0.0 ? 0.0 : (1.0 - smoothstep(0.05, 0.60, uAge)) * smoothstep(0.0, 0.05, uAge);
  float settling = uAge < 0.0 ? 0.0 : 1.0 - smoothstep(0.15, 1.4, uAge);
  float rays = (rayA + rayB * 0.32) * (0.15 + settling * 0.20) * paneFade * smoothstep(20.0, 120.0, radius);
  float halo = exp(-dot(q / float2(134.0, 183.0), q / float2(134.0, 183.0)) * 0.8) * paneFade * 0.13;
  float flash = exp(-radius * radius / 24000.0) * (1.0 - smoothstep(165.0, 220.0, radius)) * pulse * 1.35;
  float alpha = (rays + halo + flash) * uHeat;
  return half4(half3((float3(1.0, 0.79, 0.43) * (rays + halo) + float3(1.0, 0.95, 0.77) * flash) * uHeat), half(alpha));
}
`;
