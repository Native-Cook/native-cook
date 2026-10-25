/*
 Smoke Signal Toast in one full-screen pass: a night sky over a pine treeline, a campfire on the
 ground, and smoke that rises from it and gathers into the words of a message.

 Time-periodic noise: fbm whose cells repeat every PER cells in y (hash on wrapped cell
 coordinates), scrolled up by a whole number of periods per loop, so flames and smoke rise
 continuously and the frame at t = LOOP is the frame at 0.

 Fire: a teardrop envelope eaten by rising noise, coloured along a temperature ramp (white-yellow
 core, orange, deep red), over two crossed logs with glowing ends inside a ring of stones; sparks
 rise and fade. Its light pools on the ground and catches the trees' edges.

 Smoke: a faint wisp column always; a puff that leaves the fire and rises; and the message. The
 message is a mask baked once (sharp + blurred copies, child shaders). It is sampled through a
 displacement that starts large (scattered, from below) and shrinks to zero as it forms, then
 grows sideways with the wind as it disperses. Smoke is lit warm from below by the fire.

 Skia treats RuntimeEffect output as Display P3 on iOS: colours go out through toP3.
*/
export const SMOKE_SKSL = `
uniform shader mask;
uniform shader haze;
uniform float2 size;
uniform float4 fire;   // x, y (base of the flames), flare, scale
uniform float4 puff;   // x, y, radius, density
uniform float4 text;   // formed 0..1, scatter (pt), rise (pt), alpha
uniform float4 wind;   // direction x, y, amount 0..1, 0
uniform float phase;   // loop clock 0..1
uniform float treeY;   // where the treeline stands

const float PER = 8.0;

float hsh(float2 q) { return fract(sin(dot(q, float2(127.1, 311.7))) * 43758.5453); }

// Value noise whose cells repeat every per cells in y.
float pnoise(float2 q, float per) {
  float2 i = floor(q);
  float2 f = fract(q);
  float2 u = f * f * (3.0 - 2.0 * f);
  float y0 = mod(i.y, per);
  float y1 = mod(i.y + 1.0, per);
  float a = hsh(float2(i.x, y0));
  float b = hsh(float2(i.x + 1.0, y0));
  float c = hsh(float2(i.x, y1));
  float d = hsh(float2(i.x + 1.0, y1));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float pfbm(float2 q) {
  float s = 0.5 * pnoise(q, PER);
  s += 0.25 * pnoise(q * 2.0 + float2(3.1, 0.0), PER * 2.0);
  s += 0.125 * pnoise(q * 4.0 + float2(7.7, 0.0), PER * 4.0);
  return s / 0.875;
}

float vnoise(float2 q) { return pnoise(q, 4096.0); }

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float3 sky(float2 p) {
  float2 uv = p / size;
  float3 col = mix(float3(0.035, 0.05, 0.10), float3(0.08, 0.085, 0.15), smoothstep(0.0, 0.7, uv.y));
  col = mix(col, float3(0.16, 0.11, 0.12), smoothstep(0.55, 0.85, uv.y) * 0.6);
  // Stars, twinkling at whole cycles per loop.
  float2 cell = floor(p / 7.0);
  float h = hsh(cell);
  if (h > 0.965) {
    float2 c = (cell + 0.5 + (float2(hsh(cell + 1.3), hsh(cell + 2.9)) - 0.5) * 0.7) * 7.0;
    float tw = 0.6 + 0.4 * sin(6.2831853 * (phase * (1.0 + floor(h * 40.0) - 38.0 * floor(h * 40.0 / 38.0)) + h * 9.0));
    float sz = 0.5 + 0.7 * hsh(cell + 7.0);
    col += float3(0.85, 0.88, 1.0) * exp(-dot(p - c, p - c) / (sz * sz)) * tw * (1.0 - smoothstep(0.3, 0.62, uv.y)) * (h - 0.965) * 22.0;
  }
  return col;
}

half4 main(float2 pos) {
  float2 p = pos;
  float3 col = sky(p);
  float flare = fire.z;
  float S = fire.w;
  float2 fb = fire.xy;
  float glowR = 220.0 * S * (0.85 + 0.25 * flare);

  // Treeline: pines of seeded heights, darkest, their edges lit a little by the fire.
  float colW = 22.0;
  float ci = floor(p.x / colW);
  float tallest = 0.0;
  for (int k = -1; k <= 1; k++) {
    float id = ci + float(k);
    float h = 40.0 + 70.0 * hsh(float2(id, 4.0));
    float cx = (id + 0.5 + (hsh(float2(id, 8.0)) - 0.5) * 0.6) * colW;
    float w = 16.0 + 10.0 * hsh(float2(id, 2.0));
    float top = treeY - h;
    float rise = clamp((p.y - top) / h, 0.0, 1.0);
    // Tiers of branches: a saw-tooth widening downward.
    float tier = fract(rise * 4.0);
    float half_ = w * (0.25 + 0.75 * rise) * (0.7 + 0.3 * tier);
    if (p.y > top && abs(p.x - cx) < half_) tallest = 1.0;
  }
  float ground = step(treeY, p.y);
  float3 trees = float3(0.022, 0.035, 0.042);
  float lit = exp(-length(p - fb) / glowR);
  trees += float3(0.20, 0.08, 0.02) * lit * 0.6;
  col = mix(col, trees, max(tallest, ground));
  // The ground: dark earth, a warm pool of firelight.
  if (p.y > treeY) {
    float3 earth = float3(0.03, 0.028, 0.032) * (0.85 + 0.3 * vnoise(p / 5.0));
    float2 gq = (p - fb) / float2(glowR * 0.8, glowR * 0.22);
    earth += float3(0.42, 0.17, 0.05) * exp(-dot(gq, gq) * 1.4) * (0.75 + 0.35 * flare);
    earth += float3(0.10, 0.04, 0.012) * exp(-dot(gq, gq) * 0.25);
    col = earth;
  }
  // Firelight in the air.
  col += float3(0.30, 0.12, 0.03) * exp(-length(p - fb + float2(0.0, 30.0 * S)) / (glowR * 0.35)) * (0.5 + 0.4 * flare);

  // Stones and logs.
  float2 lp = (p - fb) / S;
  for (int i = 0; i < 7; i++) {
    float a = 3.14159 * (0.05 + 0.9 * float(i) / 6.0);
    float2 sc = float2(cos(a) * 46.0, 6.0 + sin(a) * 9.0);
    float2 sq = (lp - sc) / float2(10.0, 7.0);
    float sd = dot(sq, sq);
    if (sd < 1.0) {
      float sh = 0.45 + 0.55 * (1.0 - sd) * (0.5 - sq.y * 0.5);
      col = float3(0.16, 0.15, 0.15) * sh + float3(0.30, 0.12, 0.03) * (1.0 - sq.y) * 0.25;
    }
  }
  for (int i = 0; i < 2; i++) {
    float a = i == 0 ? 0.32 : -0.32;
    float2 dir = float2(cos(a), sin(a));
    float along = dot(lp - float2(0.0, 2.0), dir);
    float across = dot(lp - float2(0.0, 2.0), float2(-dir.y, dir.x));
    if (abs(along) < 36.0 && abs(across) < 5.5) {
      float bark = 0.5 + 0.5 * vnoise(float2(along * 0.6, across * 1.5));
      float3 wood = mix(float3(0.10, 0.06, 0.04), float3(0.22, 0.13, 0.08), bark) * (0.6 + 0.4 * (1.0 - abs(across) / 5.5));
      float ember = smoothstep(10.0, 0.0, abs(along)) * (0.6 + 0.4 * pnoise(float2(along * 0.3, phase * 32.0), PER * 4.0));
      wood += float3(1.0, 0.36, 0.06) * ember * 0.9 * flare;
      col = wood;
    }
  }

  // Flames: a teardrop eaten by rising noise.
  float2 fq = (p - fb) / (S * float2(26.0, 74.0 * (0.9 + 0.2 * flare)));
  if (fq.y < 0.25 && fq.y > -1.4 && abs(fq.x) < 1.4) {
    float up = clamp(-fq.y, 0.0, 1.2);
    float env = (1.0 - abs(fq.x) / max(0.05, pow(max(1.0 - up, 0.0), 0.7) * 0.95)) * smoothstep(0.25, -0.05, fq.y);
    float2 nq = float2((p.x - fb.x) / (9.0 * S), (p.y - fb.y) / (11.0 * S) + phase * PER * 7.0);
    float n = pfbm(nq);
    float heat = env * 1.25 - (n - 0.35) * 0.9 - up * 0.55;
    heat *= 0.85 + 0.25 * flare;
    float3 flame = mix(float3(0.70, 0.12, 0.03), float3(1.0, 0.52, 0.12), smoothstep(0.05, 0.35, heat));
    flame = mix(flame, float3(1.0, 0.92, 0.62), smoothstep(0.45, 0.8, heat));
    float a = smoothstep(0.0, 0.12, heat);
    col = mix(col, flame, a);
    col += float3(0.25, 0.08, 0.0) * smoothstep(-0.2, 0.1, heat) * (1.0 - a);
  }
  // Sparks.
  for (int i = 0; i < 12; i++) {
    float fi = float(i);
    float h1 = hsh(float2(fi, 1.0));
    float h2 = hsh(float2(fi, 2.0));
    float speed = 2.0 + floor(h1 * 3.0);
    float life = fract(phase * speed + h2);
    float2 sp = fb + float2((h1 - 0.5) * 30.0 * S + sin(life * 9.0 + fi) * 10.0 * life, -20.0 * S - life * 150.0 * S);
    float d = length(p - sp);
    float fade = (1.0 - life) * smoothstep(0.0, 0.08, life);
    col += float3(1.0, 0.6, 0.2) * exp(-d * d / 1.6) * fade * 1.2;
  }

  // Smoke. A faint wisp column from the fire, swaying.
  float rise = fb.y - p.y;
  float3 smokeLit = mix(float3(0.62, 0.62, 0.68), float3(0.95, 0.66, 0.42), exp(-max(rise, 0.0) / 160.0) * 0.6);
  if (rise > 20.0 * S && rise < 330.0) {
    float sway = sin(rise * 0.018 + phase * 6.2831853 * 2.0) * (6.0 + rise * 0.05);
    float w = 8.0 + rise * 0.16;
    float2 wq = float2((p.x - fb.x - sway) / 16.0, p.y / 16.0 + phase * PER * 5.0);
    float n = pfbm(wq);
    float col_ = exp(-pow((p.x - fb.x - sway) / w, 2.0));
    float wisp = col_ * smoothstep(0.35, 0.8, n) * (1.0 - smoothstep(120.0, 330.0, rise)) * 0.28;
    col = mix(col, smokeLit, wisp);
  }
  // The puff on its way up.
  if (puff.w > 0.001) {
    float2 pq = (p - puff.xy) / (puff.z * float2(1.25, 0.8));
    // A trail back down toward the fire under the cloud.
    float trail = exp(-pow((p.x - mix(puff.x, fb.x, clamp((p.y - puff.y) / max(fb.y - puff.y, 1.0), 0.0, 1.0))) / (10.0 + puff.z * 0.18), 2.0));
    trail *= step(puff.y, p.y) * step(p.y, fb.y - 20.0 * S) * 0.55;
    float pd = dot(pq, pq);
    if (pd < 3.0 || trail > 0.02) {
      float2 wq = float2(pq.x * 2.2 + 9.0, pq.y * 2.2 + phase * PER * 3.0);
      float2 warp = float2(pfbm(wq), pfbm(wq + float2(5.0, 0.0)));
      float n = pfbm(wq + warp * 1.6);
      float body = 1.0 - pd * 0.75;
      float dens = smoothstep(0.1, 0.8, max(body, trail * 1.6 - 0.62) + (n - 0.5) * 1.1) * puff.w;
      float3 pc = smokeLit * (0.8 + 0.35 * n);
      col = mix(col, pc, clamp(dens, 0.0, 1.0) * 0.72);
    }
  }
  // The message: the mask seen through a displacement that settles as it forms and tears with
  // the wind as it goes.
  if (text.w > 0.001) {
    float2 nq = p / 34.0;
    float2 jitter = float2(vnoise(nq + 3.0), vnoise(nq + 11.0)) - 0.5;
    float gust = wind.z;
    float2 disp = jitter * text.y + float2(0.0, text.z);
    float shear = 0.6 + 0.8 * vnoise(float2(p.y / 26.0, 2.0)) + 0.4 * vnoise(p / 18.0);
    disp -= wind.xy * gust * 170.0 * shear;
    float2 curl = float2(vnoise(p / 14.0 + 7.0), vnoise(p / 14.0 + 19.0)) - 0.5;
    disp += (jitter * 70.0 + curl * 40.0) * gust;
    // Even when held still the words keep drifting a little, like smoke that has not settled.
    float2 drift = float2(pfbm(float2(p.x / 16.0, p.y / 16.0 + phase * PER * 2.0)), pfbm(float2(p.x / 16.0 + 6.0, p.y / 16.0 + phase * PER * 3.0))) - 0.5;
    float2 sp = p + disp + drift * 3.0;
    float sharp = mask.eval(sp).a;
    float soft = haze.eval(sp).a;
    float wide = haze.eval(sp + float2(0.0, 6.0) + drift * 10.0).a;
    float crisp = text.x * (1.0 - gust);
    // At its clearest the stroke is 60 % sharp, 40 % blurred: soft edges, still readable.
    float core = mix(soft * 1.1, mix(sharp, soft * 1.1, 0.4), crisp);
    // Wisps along the strokes: drifting fbm thins and thickens them.
    float wisp = pfbm(float2(p.x / 7.0 + drift.x * 2.0, p.y / 7.0 + phase * PER * 4.0));
    float dens = core * (0.45 + 0.8 * wisp);
    float halo = (soft * 0.22 + wide * 0.18) * (0.5 + 0.5 * crisp);
    // Warm grey, lit by the fire from below, never pure white.
    float3 smoke = mix(smokeLit, float3(0.86, 0.82, 0.76), crisp * 0.8) * (0.88 + 0.18 * wisp);
    float a = clamp(dens * 0.92 + halo, 0.0, 0.95) * text.w * (1.0 - gust * gust * 0.85);
    col = mix(col, smoke, a);
  }

  float2 uv = pos / size - 0.5;
  col *= 1.0 - 0.35 * dot(uv * float2(1.1, 0.9), uv * float2(1.1, 0.9));
  col += (hsh(pos) - 0.5) / 255.0;
  return half4(half3(toP3(col)), 1.0);
}
`;
