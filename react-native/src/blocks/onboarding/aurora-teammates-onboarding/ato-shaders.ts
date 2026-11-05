// Aurora Teammates Onboarding — the aurora, one runtime shader drawn by tgfx (SkSL subset: helpers
// above main, one name per statement, constant loop bounds, rising smoothstep edges only; uniforms
// are not in scope in the helpers, so they are passed in).
//
// A light sits in the sheet's notch. Soft curtains rise from it and fan out as they climb (blue-violet
// on the left, the page's colour on the right), a white-cyan glow spreads along the edge, and a
// curtain hangs on the right: green and tall on page 1, violet-magenta lower on page 2, teal on page 3
// (placed and coloured from the pin). The top of the screen stays deep navy. Every motion term is a
// whole number of turns of uPhase (one turn per loop), so the loop has no seam. uFlare (0..1) is the
// light catching the glass as it goes in and comes out. uTilt (pt) is the phone's tilt: the curtains
// slide against it, further the higher they are, so the glass in front reads as nearer.

export const AURORA = `
uniform float2 uSize;
uniform float2 uCore;
uniform float uPhase;
uniform float3 uW;
uniform float uFlare;
uniform float2 uTilt;

float3 pal(float3 w, float3 a, float3 b, float3 c) {
  return a * w.x + b * w.y + c * w.z;
}

float pal1(float3 w, float a, float b, float c) {
  return a * w.x + b * w.y + c * w.z;
}

float bands(float x, float ph) {
  float s = 0.0;
  s += 0.46 * sin(x * 5.0 + 0.6 + 0.40 * sin(ph));
  s += 0.30 * sin(x * 9.0 - 1.2 + 0.55 * cos(ph * 2.0));
  s += 0.18 * sin(x * 16.0 + 2.4 - 0.70 * sin(ph));
  s += 0.10 * sin(x * 27.0 - 0.3 + 0.90 * cos(ph * 3.0));
  return s;
}

float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 pos) {
  float W = uSize.x;
  float H = uSize.y;
  float2 d = pos - uCore;
  float2 uv = pos / uSize;
  float up = max(-d.y, 0.0) / H;

  // deep navy, darker in the top corners
  float3 col = mix(float3(0.047, 0.055, 0.265), float3(0.052, 0.060, 0.300), smoothstep(0.0, 0.6, uv.y));
  float corner = smoothstep(0.3, 1.0, abs(uv.x - 0.5) * 2.0) * (1.0 - smoothstep(0.05, 0.5, uv.y));
  col *= 1.0 - 0.45 * corner;

  float3 leftC = pal(uW, float3(0.26, 0.29, 0.58), float3(0.25, 0.26, 0.56), float3(0.23, 0.29, 0.60));
  float3 rightC = pal(uW, float3(0.24, 0.50, 0.38), float3(0.46, 0.20, 0.56), float3(0.12, 0.50, 0.56));
  float3 curtainC = pal(uW, float3(0.12, 0.38, 0.15), float3(0.46, 0.09, 0.40), float3(0.06, 0.40, 0.46));
  float3 coreC = pal(uW, float3(0.86, 0.99, 0.94), float3(0.98, 0.95, 1.00), float3(0.88, 0.96, 1.00));

  // soft curtains fanning up out of the notch
  float fanX = (d.x - uTilt.x * (0.4 + 3.0 * up)) / (W * (0.42 + 1.4 * up));
  float b = clamp(0.5 + 0.5 * bands(fanX * 2.2, uPhase), 0.0, 1.0);
  b = b * sqrt(b);
  float rise = exp(-up / (0.13 + 0.03 * uFlare));
  float wide = exp(-fanX * fanX * 1.6);
  float side = clamp(d.x / (W * 0.55), -1.0, 1.0) * 0.5 + 0.5;
  float3 veil = mix(mix(leftC, rightC, side), coreC * 0.85, 0.45 * exp(-up / 0.07));
  col += veil * (0.30 + 1.05 * b) * rise * wide * (1.0 + 0.35 * uFlare);

  // the page's curtain on the right
  float kx = pal1(uW, 0.95, 0.92, 0.90) * W;
  float ky = pal1(uW, 0.17, 0.32, 0.36) * H;
  float sx = pal1(uW, 0.16, 0.15, 0.20) * W;
  float sy = pal1(uW, 0.15, 0.13, 0.12) * H;
  float2 bg = pos - uTilt;
  float cx = (bg.x - kx) / sx;
  float cy = (bg.y - ky) / sy;
  float folds = 0.75 + 0.25 * sin(bg.x * 0.06 + 0.9 * sin(uPhase * 2.0));
  col += curtainC * exp(-(cx * cx + cy * cy)) * folds * 1.15;

  // a cool glow low on the left
  float lx = (bg.x - W * 0.06) / (W * 0.30);
  float ly = (bg.y - uCore.y + H * 0.12) / (H * 0.11);
  col += leftC * exp(-(lx * lx + ly * ly)) * 0.38;

  // the light: a wide bright band along the edge and a hot centre
  float ex = d.x / (W * (0.35 + 0.05 * uFlare));
  float ey = d.y / (H * (0.072 + 0.014 * uFlare));
  float core = exp(-(ex * ex + ey * ey));
  float hot = exp(-length(float2(d.x * 0.6, d.y)) / (H * (0.026 + 0.02 * uFlare)));
  col += coreC * (core * (1.1 + 0.3 * uFlare) + hot * (0.8 + 0.9 * uFlare));

  col += (hash(pos) - 0.5) / 255.0;
  return half4(clamp(col, 0.0, 1.0), 1.0);
}
`;
