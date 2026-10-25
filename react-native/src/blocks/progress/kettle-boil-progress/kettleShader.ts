// Kettle Boil Progress — the scene in one full-screen pass: a slate kitchen wall, an oak counter,
// and a glass kettle on a charcoal base whose LED ring fills as the water heats.
//
// The kettle is built from silhouettes in its own coordinates (origin where the body meets the
// base, y up is negative, scale k): a tapered body with rounded shoulders, a lid and knob, a C
// handle and a glass spout. Through the glass the wall is seen shifted (a cheap refraction); the
// water fills 68 % of the body, lit blue from below by the LED ring while it is on.
//
// The boil is drawn from `heat` (0 still water … 1 a rolling boil): micro bubbles that cling to
// the floor (a jittered grid), streams of bubbles rising and growing along seeded columns, and a
// surface that rolls. Bubble positions are fract(phase · K + seed) with whole K, so they repeat
// with the loop. Steam above the spout and the lid is fbm, scrolled upward and faded with height.
//
// Portable: fixed loops, no arrays, no dynamic indexing. The output is opaque and converted from
// sRGB to Display P3 (Skia treats runtime-effect output as P3 on iOS).

export const KETTLE_SKSL = `
uniform float2 size;
uniform float4 kettle;
uniform float heat;
uniform float2 ring;
uniform float steam;
uniform float phase;
uniform float lever;

const float TAU = 6.2831853;

float g2(float x) {
  return exp(-x * x);
}

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + float2(1.0, 0.0));
  float c = hash(i + float2(0.0, 1.0));
  float d = hash(i + float2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p = p * 2.03 + float2(17.0, 9.0);
    a *= 0.5;
  }
  return v;
}

float3 toP3(float3 c) {
  float3 l = pow(clamp(c, 0.0, 1.0), float3(2.2));
  float3 m = float3(
    dot(l, float3(0.8225, 0.1774, 0.0)),
    dot(l, float3(0.0332, 0.9669, 0.0)),
    dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(m, float3(1.0 / 2.2));
}

float sdRoundBox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Distance to a segment a-b whose radius tapers from ra to rb.
float sdTaper(float2 p, float2 a, float2 b, float ra, float rb) {
  float2 pa = p - a;
  float2 ba = b - a;
  float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
  return length(pa - ba * h) - mix(ra, rb, h);
}

const float BODY_H = 236.0;

// The body's half-width at height y (negative up), with the rounded foot and shoulders.
float bodyHalf(float y, float k) {
  float h = BODY_H * k;
  float s = clamp(-y / h, 0.0, 1.0);
  float hw = (112.0 - 26.0 * pow(s, 1.4)) * k;
  float rb = 30.0 * k;
  float db = -y;
  if (db < rb) hw = hw - rb + sqrt(max(0.0, rb * rb - (rb - db) * (rb - db)));
  float rt = 22.0 * k;
  float dt = h + y;
  if (dt < rt) hw = hw - rt + sqrt(max(0.0, rt * rt - (rt - dt) * (rt - dt)));
  return hw;
}

// The wall and the counter, without the kettle; lit cool from the LED ring when it is on.
float3 room(float2 p, float2 o, float k, float glowOn) {
  float wallT = clamp(p.y / size.y, 0.0, 1.0);
  float3 c = mix(float3(0.085, 0.12, 0.125), float3(0.15, 0.19, 0.185), wallT);
  // A warm window light from the upper left, falling across the wall.
  c += float3(0.95, 0.75, 0.55) * 0.08 * g2((p.x + p.y * 0.35 - size.x * 0.1) / (size.x * 0.55));
  // Subtle tiled wall: square tiles with soft grout lines.
  float2 tile = abs(fract(p / (46.0 * k)) - 0.5);
  c *= 1.0 - 0.05 * smoothstep(0.465, 0.5, max(tile.x, tile.y));
  float counter = o.y + 52.0 * k;
  if (p.y > counter) {
    float d = (p.y - counter) / (size.y - counter + 1.0);
    // Oak seen at an angle: grain lines run across, closer together toward the wall.
    float gy = (p.y - counter) / k;
    float grain = 0.5 + 0.5 * sin(gy * gy * 0.004 + gy * 0.25 + 2.5 * noise(float2(p.x * 0.01 / k, gy * 0.05)));
    c = mix(float3(0.42, 0.29, 0.19), float3(0.5, 0.36, 0.24), grain * 0.6) * (0.8 - 0.35 * d);
    // The edge of the counter catches the light.
    c += float3(0.6, 0.5, 0.4) * 0.25 * g2((p.y - counter) / (1.5 * k));
    // The kettle's shadow and the LED's pool on the counter.
    float2 sd = float2((p.x - o.x) / (150.0 * k), (p.y - counter) / (40.0 * k));
    c *= 1.0 - 0.5 * exp(-dot(sd, sd) * 1.5);
    c += float3(0.2, 0.5, 1.0) * 0.35 * glowOn * exp(-dot(sd, sd) * 2.0);
  }
  c += float3(0.2, 0.5, 1.0) * 0.08 * glowOn * exp(-length((p - o) / float2(220.0 * k, 120.0 * k)));
  return c;
}

float3 bubble(float2 p, float2 c, float r) {
  float d = length(p - c);
  if (d > r + 1.0) return float3(0.0);
  // A bright rim, a dim core, and a tiny catchlight up and to the left.
  float rim = smoothstep(r * 0.55, r, d) * (1.0 - smoothstep(r, r + 1.0, d));
  float core = (1.0 - smoothstep(0.0, r, d)) * 0.12;
  float spec = g2(length(p - c + float2(r * 0.35, r * 0.4)) / (r * 0.28 + 0.3));
  return float3(rim * 0.55 + core, spec * 0.8, 0.0);
}

half4 main(float2 p) {
  float2 o = kettle.xy;
  float k = kettle.z;
  float2 q = p - o;
  float h = BODY_H * k;
  float glowOn = ring.y;
  float3 col = room(p, o, k, glowOn);

  // The base: a charcoal puck standing on the counter.
  float dBase = sdRoundBox(q - float2(0.0, 26.0 * k), float2(126.0 * k, 26.0 * k), 18.0 * k);
  if (dBase < 1.0) {
    float v = clamp((q.y) / (52.0 * k), 0.0, 1.0);
    float3 bc = mix(float3(0.2, 0.21, 0.22), float3(0.06, 0.065, 0.07), v);
    bc += float3(0.9, 0.9, 0.95) * 0.12 * g2((q.x / (126.0 * k) + 0.45) / 0.18) * (1.0 - v);
    // The LED ring: a band round the puck's top, lit from the front centre outward.
    float ry = 10.0 * k;
    float2 e = float2(q.x / (116.0 * k), (q.y - 6.0 * k) / ry);
    float er = length(e);
    float ang = atan(e.y, e.x);
    float lit = 1.0 - smoothstep(ring.x * 1.66 - 0.06, ring.x * 1.66, abs(ang - 1.5708));
    float band = g2((er - 1.0) / 0.12) * step(0.0, e.y + 0.2);
    float3 led = float3(0.25, 0.62, 1.0);
    bc = mix(bc, float3(0.1, 0.13, 0.17), band * 0.7);
    bc += led * band * lit * (0.45 + 1.1 * glowOn);
    bc += led * 0.18 * glowOn * g2((er - 1.0) / 0.45) * lit;
    col = mix(col, bc, clamp(0.5 - dBase, 0.0, 1.0));
  }

  // The handle: a matte C on the right, behind the body.
  float2 hc = q - float2(bodyHalf(-h * 0.55, k) + 14.0 * k, -h * 0.55);
  float dHandle = abs(sdRoundBox(hc, float2(30.0 * k, h * 0.33), 26.0 * k)) - 8.5 * k;
  if (dHandle < 1.0 && hc.x > -6.0 * k) {
    float3 hcol = float3(0.075, 0.08, 0.085) + float3(0.6, 0.62, 0.65) * 0.22 * g2((hc.x - 26.0 * k) / (5.0 * k));
    col = mix(col, hcol, clamp(0.5 - dHandle, 0.0, 1.0));
  }
  // The switch at the handle's foot: pressed down (lever 1) while it heats, it clicks back up.
  float2 lv = q - float2(bodyHalf(-h * 0.2, k) + 30.0 * k, -h * 0.2 - 6.0 * k + lever * 7.0 * k);
  float dLever = sdRoundBox(lv, float2(9.0 * k, 4.0 * k), 3.0 * k);
  col = mix(col, float3(0.12, 0.13, 0.14) + float3(0.25, 0.62, 1.0) * glowOn * lever * 0.8, clamp(0.5 - dLever, 0.0, 1.0));

  // The spout: a glass tube up and to the left.
  float2 sa = float2(-bodyHalf(-h * 0.72, k) + 12.0 * k, -h * 0.72);
  float2 sb = float2(-bodyHalf(-h * 0.72, k) - 50.0 * k, -h - 4.0 * k);
  float dSpout = sdTaper(q, sa, sb, 17.0 * k, 9.5 * k);

  float hw = bodyHalf(q.y, k);
  float inBody = (q.y < 0.0 && q.y > -h) ? clamp(hw - abs(q.x) + 0.5, 0.0, 1.0) * clamp(-q.y + 0.5, 0.0, 1.0) : 0.0;
  float glassCover = max(inBody, clamp(0.5 - dSpout, 0.0, 1.0));
  if (glassCover > 0.0) {
    bool body = inBody > 0.0;
    float u = body ? clamp(q.x / max(hw, 1.0), -1.0, 1.0) : 0.6;
    // What lies behind, squeezed toward the middle by the curved glass.
    float3 gc = room(p + float2(-u * 10.0 * k, 0.0), o, k, glowOn) * 0.82;
    gc = mix(gc, float3(0.6, 0.8, 0.82), 0.05);
    if (body) {
      float level = -h * 0.68;
      float boil = smoothstep(0.65, 1.0, heat);
      float wave = (1.2 * k * heat + 5.0 * k * boil) * (sin(q.x * 0.06 / k + phase * TAU * 5.0) + 0.6 * sin(q.x * 0.13 / k - phase * TAU * 7.0 + 1.0));
      float surf = level + wave;
      if (q.y > surf) {
        float depth = clamp((q.y - surf) / (-level), 0.0, 1.0);
        float3 water = mix(gc * float3(0.85, 1.0, 1.0), float3(0.2, 0.32, 0.33), 0.25 + 0.2 * depth);
        // The ring lights the water blue from below.
        water += float3(0.15, 0.45, 1.0) * glowOn * (0.12 + 0.5 * depth * depth);
        // A shimmer of heat in the body of the water.
        water += float3(0.7, 0.85, 0.9) * 0.05 * heat * noise(float2(q.x * 0.05 / k, q.y * 0.03 / k - phase * TAU * 4.0));
        // The surface line: a bright meniscus and a little foam when it boils.
        float sl = g2((q.y - surf) / (1.6 * k));
        water += float3(0.8, 0.92, 0.95) * sl * (0.35 + 0.4 * boil);
        // Micro bubbles clinging to the floor.
        float floorY = -8.0 * k;
        if (heat > 0.05 && q.y > floorY - 16.0 * k) {
          float2 cell = floor(float2(q.x, q.y) / (7.0 * k));
          float hh = hash(cell);
          float2 cpos = (cell + 0.5 + (float2(hash(cell + 3.1), hash(cell + 7.7)) - 0.5) * 0.6) * 7.0 * k;
          cpos += float2(sin(phase * TAU * 9.0 + hh * 6.0), cos(phase * TAU * 7.0 + hh * 5.0)) * heat * 0.8 * k;
          float present = step(hh, smoothstep(0.05, 0.45, heat) * 0.55) * step(floorY - 14.0 * k, cpos.y);
          float3 b = bubble(q, cpos, (1.0 + 1.2 * hash(cell + 1.3)) * k);
          water += float3(0.85, 0.95, 1.0) * (b.x + b.y) * present;
        }
        // Streams of rising bubbles.
        float streams = smoothstep(0.3, 0.75, heat);
        if (streams > 0.0) {
          for (int c = 0; c < 7; c++) {
            float fc = float(c);
            float hc0 = hash(float2(fc, 4.0));
            float on = step(hc0, streams * 1.05);
            float xc = (hash(float2(fc, 9.0)) * 1.5 - 0.75) * 92.0 * k;
            float kc = 5.0 + floor(hash(float2(fc, 2.0)) * 4.0);
            for (int j = 0; j < 6; j++) {
              float fj = float(j);
              float v = fract(phase * kc + fj / 6.0 + hc0);
              float by = mix(floorY, surf + 4.0 * k, v * v * (0.6 + 0.4 * v));
              float bx = xc + sin(v * 11.0 + fc * 2.0) * 3.0 * k * v;
              float br = (1.2 + 3.2 * v) * k * (0.7 + 0.3 * streams);
              float3 b = bubble(q, float2(bx, by), br);
              water += float3(0.85, 0.95, 1.0) * (b.x + b.y) * streams * on;
            }
          }
        }
        gc = water;
      } else {
        // Air above the water: a faint mist when it is hot.
        gc += float3(0.8, 0.88, 0.9) * 0.08 * steam * fbm(float2(q.x * 0.03 / k, q.y * 0.03 / k + phase * TAU * 2.0));
      }
      // Glass: a Fresnel edge, two softbox streaks, and a faint top band.
      gc += float3(0.85, 0.92, 0.95) * (pow(abs(u), 8.0) * 0.45);
      float streak = smoothstep(-h * 0.95, -h * 0.85, q.y) * (1.0 - smoothstep(-24.0 * k, -6.0 * k, q.y));
      gc += float3(1.0, 0.98, 0.95) * g2((u + 0.66) / 0.05) * 0.5 * streak;
      gc += float3(0.95, 0.97, 1.0) * g2((u - 0.58) / 0.12) * 0.12 * streak;
      gc = mix(gc, float3(0.75, 0.82, 0.85), 0.35 * smoothstep(0.95, 1.0, abs(u)));
      // The glass foot is thick: a bright line where it sits on the base.
      gc += float3(0.7, 0.85, 1.0) * g2((q.y + 6.0 * k) / (2.0 * k)) * (0.25 + 0.5 * glowOn);
    } else {
      // A thick glass tube: bright walls, a streak along its upper side, a cooler core.
      float inner = clamp(-dSpout / (6.0 * k), 0.0, 1.0);
      gc = mix(gc, float3(0.55, 0.68, 0.7), 0.18);
      gc += float3(0.85, 0.92, 0.95) * (1.0 - inner) * 0.4;
      gc += float3(1.0, 0.98, 0.95) * g2((-dSpout - 3.5 * k) / (1.2 * k)) * 0.35;
    }
    col = mix(col, gc, glassCover);
  }

  // The lid and its knob.
  float topHw = bodyHalf(-h + 0.5, k);
  float dLid = sdRoundBox(q - float2(0.0, -h - 6.0 * k), float2(topHw + 4.0 * k, 9.0 * k), 8.0 * k);
  float dKnob = sdRoundBox(q - float2(0.0, -h - 22.0 * k), float2(20.0 * k, 9.0 * k), 7.0 * k);
  float dTop = min(dLid, dKnob);
  if (dTop < 1.0) {
    float v = clamp((q.y + h + 30.0 * k) / (40.0 * k), 0.0, 1.0);
    float3 lc = mix(float3(0.22, 0.23, 0.25), float3(0.06, 0.065, 0.07), v);
    lc += float3(1.0, 0.95, 0.9) * 0.2 * g2((q.x / (topHw + 4.0 * k) + 0.4) / 0.15);
    col = mix(col, lc, clamp(0.5 - dTop, 0.0, 1.0));
  }

  // Steam: from the spout's tip and from under the lid.
  if (steam > 0.001) {
    float2 tip = sb + float2(-2.0 * k, -6.0 * k);
    float2 s1 = q - tip;
    float up = -s1.y;
    if (up > -6.0 * k) {
      float drift = up * 0.25 - 0.0004 * up * up / k;
      float w = 8.0 * k + up * 0.45;
      float plume = g2((s1.x + drift) / w) * smoothstep(-6.0 * k, 14.0 * k, up) * exp(-up / (170.0 * k));
      float n = fbm(float2(s1.x * 0.016 / k, (s1.y + phase * 420.0 * k) * 0.014 / k));
      col += float3(0.86, 0.9, 0.92) * plume * smoothstep(0.2, 0.7, n) * steam * 1.15;
    }
    float2 s2 = q - float2(10.0 * k, -h - 16.0 * k);
    float up2 = -s2.y;
    if (up2 > 0.0) {
      float plume2 = g2((s2.x - up2 * 0.08) / (12.0 * k + up2 * 0.35)) * exp(-up2 / (110.0 * k)) * smoothstep(0.0, 18.0 * k, up2);
      float n2 = fbm(float2(s2.x * 0.03 / k + 4.0, (s2.y + phase * 360.0 * k) * 0.026 / k));
      col += float3(0.86, 0.9, 0.92) * plume2 * smoothstep(0.3, 0.8, n2) * steam * 0.7;
    }
  }

  // Vignette and grain.
  float2 vc = (p / size - 0.5) * float2(1.1, 1.0);
  col *= 1.0 - 0.5 * dot(vc, vc);
  col += (hash(floor(p * 2.0)) - 0.5) * 0.025;
  return half4(half3(toP3(col)), 1.0);
}
`;
