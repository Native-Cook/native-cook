// The frosted window pane over the whole screen.
//
// FROST_TEX_SKSL is baked once at mount on an offscreen GPU surface, into two grey images (grey
// survives the sRGB → P3 working-space conversion unchanged): mode 0 paints the ice — thin,
// crisp frost feathers (a spine, side branches toward the tip, fine barbs on the branches) rooted
// along the four edges and growing inward, overlapping into lace at the edges and corners and
// fading out of the middle third, plus tiny sparkles; mode 1 paints the haze density (cloudy
// fbm, thicker at the edges), which also shapes the front along which frost
// grows back. The app under the glass is snapshotted and blurred separately (FrostWipeAlert.tsx);
// until that lands, a stand-in of its colours (a night gradient and the alert card's light block)
// is used, so the very first frame is already frosted.
//
// FROST_SKSL runs every frame. Frosted glass = the blurred app pulled toward a cold white by the
// haze, with the ice on top in bright white. Wiped glass is transparent, so the real views show
// sharp. The wipe is a trail of up to 32 points in time order (x, y, brush radius, regrow
// progress q); a negative radius starts a new stroke. Each point and the segment back to the one
// before it clear a soft capsule, with a wet edge (a darker film and a thin bright line). Frost
// grows back as q rises from below 0 (fully clear) to above 1.2: a pixel refreezes once q passes
// its brush coverage nudged by the haze and ice, so the front creeps in from the wipe's edges.
// Water droplets (x, head y, trail length, radius) are clear lenses with a bright highlight and
// caustic; the track behind them is half cleared. Colours are sRGB; the app snapshot is brought
// back from the working space (P3 on iOS) first, and the result goes out through toP3.

const NOISE = `
float hash(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p = p * 2.07 + float2(13.1, 7.7);
    a *= 0.5;
  }
  return v;
}
`;

export const FROST_TEX_SKSL = `
uniform float2 size;
uniform float mode;
${NOISE}
float seg(float2 p, float2 a, float2 d, float len) {
  float2 q = p - a;
  return length(q - d * clamp(dot(q, d), 0.0, len));
}

// Distance to one frost feather: a spine from s along d, len pt long; side branches every
// 11 pt on both sides at about 62° toward the tip, shorter toward the tip; fine barbs every
// 2.6 pt along the nearest branch.
float feather(float2 p, float2 s, float2 d, float len) {
  float2 q = p - s;
  float t = dot(q, d);
  float best = seg(p, s, d, len);
  float reach = len * 0.3 + 4.0;
  float2 n = float2(-d.y, d.x);
  if (t < -reach || t > len + 4.0 || abs(dot(q, n)) > reach) return best;
  float side = dot(q, n) >= 0.0 ? 1.0 : -1.0;
  float2 b = normalize(d * 0.47 + n * side * 0.88);
  float sp = 11.0;
  float k0 = floor(t / sp);
  float bestB = 10000.0;
  float2 bo = s;
  float bl = 0.0;
  for (int j = 0; j < 3; j++) {
    float k = k0 - float(j);
    if (k >= 1.0 && k * sp < len) {
      float2 o = s + d * (k * sp);
      float l = (len - k * sp) * 0.3 + 3.0;
      float dist = seg(p, o, b, l);
      if (dist < bestB) {
        bestB = dist;
        bo = o;
        bl = l;
      }
    }
  }
  best = min(best, bestB);
  if (bl > 0.0 && bestB < 6.0) {
    // Barbs off that branch, both sides, angled toward its tip.
    float2 bq = p - bo;
    float tb = dot(bq, b);
    float2 bn = float2(-b.y, b.x);
    float bs = dot(bq, bn) >= 0.0 ? 1.0 : -1.0;
    float2 c = normalize(b * 0.7 + bn * bs * 0.71);
    float m0 = floor(tb / 2.6);
    for (int j = 0; j < 2; j++) {
      float m = m0 - float(j);
      if (m >= 1.0 && m * 2.6 < bl) {
        best = min(best, seg(p, bo + b * (m * 2.6), c, min(3.6, (bl - m * 2.6) * 0.5 + 0.8)));
      }
    }
  }
  return best;
}

// Feathers rooted along one edge (origin o, direction along it tan, inward normal inw), a root
// every 17 pt with a little jitter; longer near the corners, pointing inward and leaning away
// from the nearest corner.
float edgeFrost(float2 p, float2 o, float2 tan, float2 inw, float edgeLen, float seed) {
  float along = dot(p - o, tan);
  float depth = dot(p - o, inw);
  if (depth > 210.0) return 10000.0;
  float spacing = 17.0;
  float k0 = floor(along / spacing);
  float best = 10000.0;
  for (int j = -7; j <= 7; j++) {
    float k = k0 + float(j);
    float x = (k + hash(float2(k, seed))) * spacing;
    if (x >= -10.0 && x <= edgeLen + 10.0) {
      float h1 = hash(float2(k, seed + 1.0));
      float h2 = hash(float2(k, seed + 2.0));
      float cornerness = 1.0 - smoothstep(0.0, 90.0, min(x, edgeLen - x));
      float len = mix(55.0, 150.0, h1 * h1) + cornerness * 60.0;
      float lean = (h2 - 0.5) * 1.1 + cornerness * (x < edgeLen * 0.5 ? 0.7 : -0.7);
      float2 dir = normalize(inw * cos(lean) + tan * sin(lean));
      float2 root = o + tan * x - inw * 2.0;
      best = min(best, feather(p, root, dir, len));
    }
  }
  return best;
}

half4 main(float2 p) {
  float2 e = min(p, size - p);
  float near = min(e.x, e.y);
  float edgeN = near / min(size.x, size.y);
  if (mode > 0.5) {
    float body = fbm(p * 0.012 + fbm(p * 0.02) * 1.6);
    float edge = 1.0 - smoothstep(0.0, 0.3, edgeN);
    return half4(half3(clamp(0.2 + body * 0.45 + edge * 0.4, 0.0, 1.0)), 1.0);
  }
  float d = 10000.0;
  if (e.x < 210.0) {
    if (p.x < size.x * 0.5) d = min(d, edgeFrost(p, float2(0.0, 0.0), float2(0.0, 1.0), float2(1.0, 0.0), size.y, 3.0));
    else d = min(d, edgeFrost(p, float2(size.x, 0.0), float2(0.0, 1.0), float2(-1.0, 0.0), size.y, 7.0));
  }
  if (e.y < 210.0) {
    if (p.y < size.y * 0.5) d = min(d, edgeFrost(p, float2(0.0, 0.0), float2(1.0, 0.0), float2(0.0, 1.0), size.x, 11.0));
    else d = min(d, edgeFrost(p, float2(0.0, size.y), float2(1.0, 0.0), float2(0.0, -1.0), size.x, 13.0));
  }
  // Thin crisp lines (about 1 pt), stronger toward the edges, gone in the middle third.
  float fade = 1.0 - smoothstep(0.16, 0.34, edgeN);
  float ice = smoothstep(0.6, 0.1, d) * (0.55 + 0.45 * (1.0 - smoothstep(0.0, 0.2, edgeN))) * fade;
  // Sparkles: tiny points, more of them near the edges.
  float2 gc = floor(p / 5.0);
  float2 go = (gc + float2(hash(gc + 21.0), hash(gc + 22.0))) * 5.0;
  float spark = step(1.0 - (0.012 + 0.03 * fade), hash(gc + 23.0)) * smoothstep(0.8, 0.0, length(p - go));
  return half4(half3(clamp(max(ice, spark), 0.0, 1.0)), 1.0);
}
`;

export const FROST_SKSL = `
uniform shader iceTex;
uniform shader hazeTex;
uniform shader content;
uniform float2 size;
uniform float ready;
uniform float4 card;
uniform float4 wipe[32];
uniform float4 drops[8];

float segDist(float2 p, float2 a, float2 b) {
  float2 ab = b - a;
  float h = clamp(dot(p - a, ab) / max(dot(ab, ab), 0.0001), 0.0, 1.0);
  return length(p - a - ab * h);
}

float sdRoundRect(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

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

// The blurred app; before its snapshot lands, its colours: a night gradient and the light card.
float3 behind(float2 p) {
  if (ready > 0.5) return toSrgb(float3(content.eval(clamp(p, float2(0.5), size - 0.5)).rgb));
  float3 sky = mix(float3(0.03, 0.06, 0.1), float3(0.1, 0.17, 0.24), p.y / size.y);
  float c = 1.0 - smoothstep(-14.0, 14.0, sdRoundRect(p - card.xy - card.zw * 0.5, card.zw * 0.5, 28.0));
  return mix(sky, float3(0.9, 0.93, 0.95), c * step(1.0, card.z));
}

half4 main(float2 p) {
  float ice = float(iceTex.eval(p).r);
  float haze = float(hazeTex.eval(p).r);
  // The regrowth front follows the smooth haze only (the thin ice lines would cut clear lines).
  float grow = haze * 0.8;

  // Clearness: the best of every wiped capsule, each refreezing as its q rises.
  float clear = 0.0;
  float4 prev = float4(0.0);
  for (int i = 0; i < 32; i++) {
    float4 w = wipe[i];
    float r = abs(w.z);
    float4 before = prev;
    prev = w;
    if (r > 0.5) {
      float d = length(p - w.xy);
      if (w.z > 0.0 && abs(before.z) > 0.5) d = min(d, segDist(p, before.xy, w.xy));
      float cover = 1.0 - smoothstep(r * 0.55, r, d);
      if (cover > 0.0) {
        float c = smoothstep(w.w - 0.08, w.w + 0.08, cover * 0.9 + grow - 0.3);
        clear = max(clear, c * smoothstep(0.0, 0.35, cover));
      }
    }
  }

  // Droplets: a half-cleared track behind each, and the clear drop with its light.
  float dropClear = 0.0;
  float light = 0.0;
  float wet = 0.0;
  float4 lens = float4(0.0);
  for (int k = 0; k < 8; k++) {
    float4 dr = drops[k];
    if (dr.w > 0.3) {
      float2 head = dr.xy;
      float track = segDist(p, head - float2(0.0, dr.z), head);
      // The track is wet glass: less haze and no ice, still frosted and bright, never cleared.
      float tw = 1.0 - smoothstep(dr.w * 0.25, dr.w * 0.75, track);
      wet = max(wet, tw);
      light = max(light, smoothstep(1.2, 0.2, abs(p.x - head.x + dr.w * 0.35)) * tw * 0.18 * step(p.y, head.y));
      float2 q = (p - head) / float2(dr.w, dr.w * 1.2);
      float r = length(q);
      if (r < 1.15) {
        dropClear = max(dropClear, smoothstep(1.0, 0.8, r));
        float glint = smoothstep(0.32, 0.04, length(q - float2(-0.3, -0.4)));
        float caustic = smoothstep(0.25, 0.0, abs(r - 0.72)) * smoothstep(0.1, 0.7, q.y);
        float rim = smoothstep(0.75, 0.95, r) * smoothstep(1.15, 0.98, r);
        light = max(light, glint * 0.95 + caustic * 0.55 + rim * 0.3);
        // The drop is a small lens: it shows the scene behind it flipped and brightened, with a
        // cool tint toward its edge (never a dark line).
        float m = smoothstep(1.0, 0.85, r);
        if (m > lens.a) {
          float3 seen = min(behind(head - q * dr.w * 0.9) * 1.15 + 0.06, float3(1.0));
          seen = mix(seen, float3(0.62, 0.74, 0.88), smoothstep(0.5, 0.95, r) * 0.45);
          lens = float4(seen, m * 0.6);
        }
      }
    }
  }

  float frost = 1.0 - clear;
  float3 col = mix(behind(p), float3(0.86, 0.91, 0.96), (0.46 + 0.32 * haze) * (1.0 - wet * 0.25));
  col += float3(0.93, 0.97, 1.0) * ice * 0.85 * (1.0 - wet);
  // The wet edge of a wipe: a darker film and a thin bright line just inside the frost.
  float edge = clear * frost * 4.0;
  col = col * (1.0 - edge * 0.25) + float3(0.95, 0.98, 1.0) * pow(edge, 4.0) * 0.35;
  col = clamp(col, 0.0, 1.0);

  float alpha = frost * (1.0 - dropClear);
  float3 pre = col * alpha;
  pre = pre * (1.0 - lens.a) + lens.rgb * lens.a;
  alpha = alpha * (1.0 - lens.a) + lens.a;
  // Light from the droplets, added over whatever shows through them.
  pre += float3(0.97, 0.99, 1.0) * light;
  alpha = clamp(alpha + light, 0.0, 1.0);
  float3 outc = alpha > 0.0 ? toP3(clamp(pre / alpha, 0.0, 1.0)) : float3(0.0);
  return half4(half3(outc * alpha), half(alpha));
}
`;
