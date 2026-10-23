/**
 * Holographic foil printed on the poster, drawn as an overlay above the poster artwork.
 *
 * The poster is read back as a child shader (a snapshot of the artwork) only to find where the
 * ink is: the green title ink becomes spot holographic foil with a light emboss, the gold script
 * becomes warm foil, and the paper carries a faint holographic laminate. Colour comes from
 * thin-film interference (the film thickness drifts slowly across the sheet, the optical path
 * changes with the angle), the grain from tiny diffraction facets that each catch the light at
 * their own angle, and a soft light streak slides with the tilt.
 *
 * Uniforms (all in the canvas's own points):
 *   res   canvas size
 *   foil  x = tilt about X (deg), y = tilt about Y (deg), z = amount 0…1, w = points per poster unit
 *
 * At amount 0 the shader returns transparent, so the resting poster is untouched.
 * Skia treats RuntimeEffect output as Display P3 on iOS: colours are converted from sRGB at the end.
 * Identifiers avoid WGSL builtin and reserved names (no `distance`, `smooth`, `select`, …).
 */
export const FOIL_SKSL = `
uniform shader poster;
uniform float2 res;
uniform float4 foil;

const float TAU = 6.2831853;
const float DEG = 0.01745329;
const float3 LUMA = float3(0.2126, 0.7152, 0.0722);

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

float3 thinfilm(float opd) {
  return 0.5 + 0.5 * cos(TAU * opd / float3(650.0, 532.0, 450.0));
}

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float greenink(float2 q) {
  float4 t = poster.eval(q);
  float3 c = t.a > 0.004 ? t.rgb / t.a : float3(1.0);
  float lu = dot(c, LUMA);
  return smoothstep(0.035, 0.09, c.g - 0.5 * (c.r + c.b)) * (1.0 - smoothstep(0.42, 0.64, lu)) * t.a;
}

float4 over(float4 dst, float3 rgb, float a) {
  return float4(rgb * a + dst.rgb * (1.0 - a), a + dst.a * (1.0 - a));
}

half4 main(float2 p) {
  float amt = foil.z;
  if (amt <= 0.002) return half4(0.0);
  float4 tex = poster.eval(p);
  if (tex.a < 0.01) return half4(0.0);
  float3 c = tex.rgb / tex.a;
  float lu = dot(c, LUMA);
  float sc = foil.w;

  float ink = greenink(p);
  float gold = smoothstep(0.15, 0.26, c.r - c.b) * smoothstep(0.32, 0.48, lu) * (1.0 - smoothstep(0.86, 0.95, lu));
  float paper = smoothstep(0.80, 0.93, lu);

  // Geometry in poster units (the poster is 250 units wide), centre at 0.
  float2 o = (p - 0.5 * res) / sc;
  float tx = foil.x * DEG;
  float ty = foil.y * DEG;
  float3 nrm = normalize(float3(sin(ty), -sin(tx), 1.0));
  float3 eye = normalize(float3(-o, 650.0));
  float3 lgt = normalize(float3(-0.30, -0.70, 1.0));
  float3 hv = normalize(lgt + eye);
  float2 slope = nrm.xy - hv.xy;

  // Thin-film colour: a slowly drifting film thickness plus the angle term.
  float flow = 0.85 * vn(o * 0.016 + float2(3.1, 7.7)) + 0.30 * vn(o * 0.055 + float2(11.0, 2.0));
  float phase = dot(slope, float2(0.55, 0.84)) * 7.5 + flow;
  float3 film = thinfilm(400.0 + 470.0 * phase);

  // Light that slides with the tilt (perpendicular to a 14° axis): a broad gloss and a tight glint.
  float k = dot(o / 125.0, float2(0.97, 0.24)) - (sin(ty) * 0.97 - sin(tx) * 0.24) * 5.2 + 0.15;
  float sheen = exp(-k * k / 0.10);
  float glint = exp(-k * k / 0.010);
  // What the mirror-like foil reflects: soft studio bands that move with the angle.
  float env = 0.5 + 0.5 * sin(dot(o, float2(0.020, 0.008)) * 6.0 + dot(slope, float2(1.0, 0.6)) * 16.0 + flow * 2.0);
  float refl = 0.16 + 0.34 * env * env + 0.85 * sheen + 0.8 * glint;

  // Diffraction grain: facets about 0.7 units wide, each with its own groove angle; denser on foil.
  float foilmask = max(ink, gold);
  float2 gp = o / 0.7;
  float2 id = floor(gp);
  float2 f = fract(gp) - 0.5;
  float r1 = hs(id);
  float r2 = hs(id + 17.31);
  float r3 = hs(id + 41.73);
  float2 groove = float2(cos(r1 * TAU), sin(r1 * TAU));
  float tw = pow(max(0.0, cos(dot(slope, groove) * 70.0 + r2 * TAU)), 24.0);
  float sparkle = tw * step(mix(0.72, 0.18, foilmask), r3) * exp(-dot(f, f) * 8.0) * (0.6 + 1.0 * sheen);
  float3 sparkcol = mix(float3(1.0), thinfilm(420.0 + 600.0 * fract(r2 + phase * 0.35)), 0.55);

  float4 acc = float4(0.0);

  // Varnished paper: only a faint gloss where the light is.
  acc = over(acc, mix(float3(1.0), film, 0.30), paper * amt * (0.01 + 0.06 * sheen));

  // Spot holographic foil on the green title ink: green lacquer over a mirror that flashes the spectrum.
  float3 deep = float3(0.06, 0.25, 0.19) * (0.75 + 0.55 * refl);
  float3 holo = mix(float3(0.82, 0.85, 0.84), pow(film, float3(0.8)), 0.80) * (0.42 + 0.78 * refl);
  float lit = clamp((0.40 + 0.50 * env + 0.70 * sheen) * amt, 0.0, 0.95);
  float3 inkcol = deep * (1.0 - lit) + holo * lit + float3(glint * 0.40);
  acc = over(acc, inkcol, ink * amt * 0.96);

  // Gold foil on the script: warm metal, a touch of film at the glint.
  float3 goldcol = float3(0.84, 0.64, 0.33) * (0.50 + 0.70 * refl) + film * 0.10 * sheen + float3(1.0, 0.94, 0.78) * glint * 0.45;
  acc = over(acc, goldcol, gold * amt * 0.92);

  // Emboss on the title: light from the upper left lifts the letters off the paper.
  float2 ld = normalize(float2(-0.35, -0.80)) * 0.55 * sc;
  float edge = greenink(p + ld) - greenink(p - ld);
  float emb = smoothstep(0.0, 0.35, amt);
  acc = over(acc, float3(1.0), max(-edge, 0.0) * 0.55 * emb * ink);
  acc = over(acc, float3(0.02, 0.07, 0.05), max(edge, 0.0) * 0.32 * emb * (1.0 - ink));

  // Grain on top: bright on the foil, only a trace on the paper under the light and on the photographs.
  float grainmask = 0.12 * paper * sheen + 0.20 * (1.0 - paper) * (1.0 - foilmask) + 1.2 * foilmask;
  acc = over(acc, sparkcol, clamp(sparkle * amt * grainmask, 0.0, 1.0));

  float3 rgb = acc.a > 0.0001 ? acc.rgb / acc.a : float3(0.0);
  float3 outc = toP3(rgb);
  return half4(outc * acc.a * tex.a, acc.a * tex.a);
}
`;

/** Foil amount and shading angles for a frame of the pass (pure, also used by the ports). */
export function foilUniform(tiltX: number, tiltY: number, flipAngle: number, scale: number): [number, number, number, number] {
  'worklet';
  // A turn lights the foil as it goes edge-on; only a fifth of it reaches the shading so the
  // colours sweep instead of strobing.
  const turn = Math.min(90, Math.max(0, flipAngle));
  const shadeY = tiltY + turn * 0.2;
  const tilt = Math.min(1, Math.hypot(tiltX / 6, tiltY / 7.5));
  const lit = Math.min(1, tilt + Math.sin((turn * Math.PI) / 180) * 0.8);
  const amount = lit * lit * (3 - 2 * lit);
  return [tiltX, shadeY, amount, scale];
}
