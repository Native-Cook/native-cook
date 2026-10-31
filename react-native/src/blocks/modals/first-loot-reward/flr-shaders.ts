// First Loot Reward — SkSL. Runtime shaders return premultiplied colour; colours are written in
// sRGB, as the canvas expects (a P3 conversion here made them pale on top of the display's own).

/**
 * The rays behind the badge: fourteen wedges from its centre, lit ones a purple-tinted black,
 * fading out toward the bottom of the screen. They turn two wedges per loop (so the loop closes)
 * and lean with the tilt. u: cx, cy, turn (0…1 per loop), alpha, tiltX, tiltY, waveR, waveA.
 */
export const RAYS_SKSL = `
uniform float cx; uniform float cy; uniform float turn; uniform float alpha;
uniform float tx; uniform float ty;
uniform float waveR; uniform float waveA;
half4 main(vec2 xy) {
  vec2 d = xy - vec2(cx - tx * 10.0, cy - ty * 10.0);
  float r = length(d);
  float a = atan(d.y, d.x) / 6.2831853 + 0.5;
  float f = fract(a * 14.0 + turn * 2.0);
  float edge = 0.6 / max(r * 6.2831853 / 14.0, 1.0);
  float lit = smoothstep(0.42 - edge, 0.42 + edge, f) * (1.0 - smoothstep(0.98 - edge, 0.98 + edge, f));
  float fall = (1.0 - smoothstep(150.0, 470.0, r)) * smoothstep(40.0, 120.0, r);
  // the burst's shockwave lights the wedges as it passes
  float wave = exp(-pow((r - waveR) / 46.0, 2.0)) * waveA;
  float k = lit * (fall + wave * 1.6) * alpha;
  vec3 page = vec3(0.035, 0.035, 0.035);
  vec3 ray = vec3(0.06, 0.048, 0.082);
  vec3 col = mix(page, ray, k);
  return half4(half3(col), 1.0);
}`;
