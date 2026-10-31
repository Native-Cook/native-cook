// Clay Relight Settings — the five clay icons of the settings list, one column, one shader.
//
// Each row holds a tile of coloured clay (a rounded, slightly domed slab) with a symbol of white
// clay pressed onto its face: a bell, a crescent, a bulb, a battery, a padlock. The view is straight
// on (orthographic, marched along z), so the 3D reads from light alone: the same sun and moon as the
// card above light every tile, the symbol's shadow falls across the tile's face and the tile's shadow
// across the list, and both turn and stretch as the sun goes down. At night each icon's lamp comes on
// in turn (the bell's badge, the crescent, the bulb, the charging bolt, the keyhole) and spills its
// colour over the tile. Premultiplied, sRGB → P3.
export const CRS_ICON_SKSL = `
uniform float4 uStage;  // width pt, height pt, pixel ratio, row pitch pt
uniform float4 uSky;    // m, sun angle, moon angle, night 0 … 1
uniform float4 uGlowA;  // lamps of rows 0 … 3
uniform float uGlowB;   // lamp of row 4
uniform float uDusk;    // 0 … 1
uniform float uTile;    // tile half size pt

const float FACE = 0.2;   // the tile's face (z), in tile units
const float RELIEF = 0.15;

float sq(float v) { return v*v; }
float smin(float a, float b, float k) {
  float h = clamp(0.5 + 0.5*(b - a)/k, 0.0, 1.0);
  return mix(b, a, h) - k*h*(1.0 - h);
}
float rbox(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float seg(float2 p, float2 a, float2 b) {
  float2 pa = p - a, ba = b - a;
  return length(pa - ba*clamp(dot(pa, ba)/dot(ba, ba), 0.0, 1.0));
}
// a 2D shape raised from the tile's face by RELIEF, its edge rounded
float raise(float3 p, float d2, float h) {
  float2 w = float2(d2 + 0.06, abs(p.z - FACE - 0.5*h) - 0.5*h + 0.06);
  return min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.06;
}

// the symbol of each row in 2D (tile units, the tile spans -1 … 1); .y marks the lamp
float2 symbol(float2 p, float row) {
  if (row < 0.5) {
    // bell, with a round badge at its shoulder
    float dome = length(p - float2(0.0, 0.08)) - 0.36;
    float skirt = rbox(p - float2(0.0, -0.18), float2(0.36 + 0.12*clamp(-p.y - 0.05, 0.0, 0.3), 0.2), 0.08);
    float bell = smin(dome, skirt, 0.12);
    bell = min(bell, rbox(p - float2(0.0, -0.39), float2(0.54, 0.07), 0.07));
    bell = min(bell, length(p - float2(0.0, -0.56)) - 0.1);
    bell = min(bell, length(p - float2(0.0, 0.5)) - 0.08);
    float badge = length(p - float2(0.44, 0.44)) - 0.17;
    return float2(min(bell, badge), badge);
  }
  if (row < 1.5) {
    // crescent and a small star
    float c = max(length(p - float2(-0.06, 0.0)) - 0.56, -(length(p - float2(0.24, 0.17)) - 0.48));
    float star = length(p - float2(0.42, -0.36)) - 0.09;
    return float2(min(c, star), min(c, star));
  }
  if (row < 2.5) {
    // bulb: glass, neck, screw base
    float glass = smin(length(p - float2(0.0, 0.17)) - 0.4, rbox(p - float2(0.0, -0.2), float2(0.18, 0.14), 0.08), 0.12);
    float base = rbox(p - float2(0.0, -0.44), float2(0.17, 0.12), 0.05);
    base = min(base, rbox(p - float2(0.0, -0.62), float2(0.1, 0.05), 0.05));
    return float2(min(glass, base), glass);
  }
  if (row < 3.5) {
    // battery with a charging bolt cut into its face (the bolt is the lamp)
    float body = rbox(p - float2(-0.06, 0.0), float2(0.58, 0.34), 0.13);
    body = min(body, rbox(p - float2(0.6, 0.0), float2(0.07, 0.14), 0.05));
    float bolt = min(seg(p, float2(0.06, 0.24), float2(-0.14, -0.02)), seg(p, float2(-0.14, -0.02), float2(0.02, -0.02)));
    bolt = min(bolt, seg(p, float2(0.02, -0.02), float2(-0.16, -0.26))) - 0.055;
    return float2(body, bolt);
  }
  // padlock: shackle and body, the keyhole is the lamp
  float shackle = abs(length(p - float2(0.0, 0.12)) - 0.26) - 0.08;
  shackle = max(shackle, -(p.y - 0.12));
  shackle = min(shackle, max(abs(abs(p.x) - 0.26) - 0.08, abs(p.y - 0.04) - 0.1));
  float body = rbox(p - float2(0.0, -0.24), float2(0.46, 0.34), 0.12);
  float key = min(length(p - float2(0.0, -0.18)) - 0.085, rbox(p - float2(0.0, -0.32), float2(0.035, 0.1), 0.03));
  return float2(min(shackle, body), key);
}

float2 sceneAt(float3 p, float row) {
  // the tile: a rounded slab, its face domed a little
  float2 q = abs(p.xy) - float2(0.88);
  float prof = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.12;
  float dome = 0.04*(1.0 - clamp(dot(p.xy, p.xy)/2.0, 0.0, 1.0));
  float2 w = float2(prof + 0.1, abs(p.z) - FACE - dome + 0.1);
  float tile = min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - 0.1;
  float2 s = symbol(p.xy, row);
  float sym = raise(p, s.x, RELIEF);
  float d = smin(tile, sym, 0.035);
  // the battery's bolt and the padlock's keyhole are pressed into the symbol, not raised
  if (row > 2.5) d = max(d, -max(s.y, FACE + RELIEF - 0.05 - p.z));
  return float2(d, sym < tile ? 1.0 : 0.0);
}

float3 tileColour(float row) {
  if (row < 0.5) return float3(0.96, 0.43, 0.37);
  if (row < 1.5) return float3(0.45, 0.4, 0.93);
  if (row < 2.5) return float3(0.27, 0.57, 0.98);
  if (row < 3.5) return float3(0.27, 0.77, 0.45);
  return float3(0.42, 0.48, 0.6);
}
float3 lampColour(float row) {
  if (row < 0.5) return float3(1.0, 0.36, 0.26);
  if (row < 1.5) return float3(1.0, 0.9, 0.55);
  if (row < 2.5) return float3(1.0, 0.82, 0.42);
  if (row < 3.5) return float3(0.5, 1.0, 0.55);
  return float3(0.55, 0.85, 1.0);
}
float lampOf(float row) {
  if (row < 0.5) return uGlowA.x;
  if (row < 1.5) return uGlowA.y;
  if (row < 2.5) return uGlowA.z;
  if (row < 3.5) return uGlowA.w;
  return uGlowB;
}

float3 toP3(float3 c) {
  float3 l = pow(max(c, float3(0.0)), float3(2.2));
  float3 m = float3(dot(l, float3(0.8225, 0.1774, 0.0)), dot(l, float3(0.0332, 0.9669, 0.0)), dot(l, float3(0.0171, 0.0724, 0.9108)));
  return pow(max(m, float3(0.0)), float3(1.0/2.2));
}

half4 main(float2 xy) {
  float pitch = uStage.w;
  float row = floor(xy.y/pitch);
  if (row > 4.5) return half4(0.0);
  float2 c = float2(0.5*uStage.x, (row + 0.5)*pitch);
  float2 uv = float2(xy.x - c.x, c.y - xy.y)/uTile;
  float px = 1.0/(uTile*uStage.z);

  float night = uSky.w;
  float sunA = uSky.y, moonA = uSky.z;
  float sunE = sin(sunA), moonE = sin(moonA);
  // the same sun as the card: high, the light falls nearly straight on the tiles; low, it grazes them
  float3 Ls = normalize(float3(cos(sunA), 0.55*sunE + 0.25, 0.25 + 0.9*max(sunE, 0.0)));
  float3 Lm = normalize(float3(cos(moonA), 0.55*moonE + 0.25, 0.25 + 0.9*max(moonE, 0.0)));
  float Is = smoothstep(0.12, 0.55, sunE);
  float Im = 0.5*smoothstep(0.0, 0.35, moonE);
  float3 sunCol = mix(float3(1.0, 0.56, 0.3), float3(1.0, 0.96, 0.9), smoothstep(0.42, 0.8, sunE));
  float3 moonCol = float3(0.6, 0.7, 1.0);
  float glow = lampOf(row);
  float3 lampC = lampColour(row);

  // march along z from in front of the face
  float t = 0.0;
  float3 ro = float3(uv, 1.0);
  float2 h = float2(1.0, 0.0);
  bool hit = false;
  float closest = 9.0;
  for (int i = 0; i < 28; i++) {
    h = sceneAt(ro - float3(0.0, 0.0, t), row);
    closest = min(closest, h.x);
    if (h.x < 0.0015) { hit = true; break; }
    t += h.x;
    if (t > 1.6) break;
  }

  // under the tile: its shadow on the list, and the lamp's halo
  float2 drop = -Ls.xy/max(Ls.z, 0.2)*0.32*Is - Lm.xy/max(Lm.z, 0.2)*0.32*(Im/0.5);
  float2 dq = abs(uv - drop) - float2(0.88);
  float shape = length(max(dq, 0.0)) + min(max(dq.x, dq.y), 0.0) - 0.22;
  float soft = 0.18 + 0.25*(1.0 - max(sunE, 0.0))*(1.0 - night);
  float shadowA = (1.0 - smoothstep(-soft, soft, shape))*mix(0.26, 0.5, night)*max(Is, Im/0.5*0.8);
  float2 tq = abs(uv) - float2(0.88);
  float tileD = length(max(tq, 0.0)) + min(max(tq.x, tq.y), 0.0) - 0.22;
  float halo = glow*exp(-max(tileD, 0.0)/0.11)*0.32*night;
  float3 underRGB = lampC*halo;
  float underA = shadowA + halo*0.6;

  if (!hit) {
    float cover = 1.0 - smoothstep(0.0, px*1.2, closest);
    if (cover <= 0.0) return half4(half3(toP3(underRGB/max(underA, 0.0001))*underA), half(underA));
  }
  float3 p = ro - float3(0.0, 0.0, t);
  const float e = 0.0015;
  const float2 k = float2(1.0, -1.0);
  float3 n = normalize(k.xyy*sceneAt(p + k.xyy*e, row).x + k.yyx*sceneAt(p + k.yyx*e, row).x
    + k.yxy*sceneAt(p + k.yxy*e, row).x + k.xxx*sceneAt(p + k.xxx*e, row).x);
  float2 s = symbol(p.xy, row);
  bool isSym = h.y > 0.5 || (s.x < 0.0 && p.z > FACE + 0.02);
  float lampHere = 1.0 - smoothstep(-0.01, 0.02, s.y);
  float3 alb = isSym ? float3(0.98, 0.97, 0.95) : tileColour(row);
  // night: the symbol's lamp lights up, its colour spills over the tile around it
  float3 emit = lampC*lampHere*glow*1.15;
  if (isSym && lampHere > 0.0) alb = mix(alb, lampC, glow*lampHere*0.7);
  float spill = glow*exp(-sq(max(s.y, 0.0))/0.05)*(1.0 - lampHere);

  // light, with shadows marched toward the sun and the moon
  float3 amb = mix(mix(float3(1.05, 0.76, 0.66), float3(0.72, 0.76, 0.84), 1.0 - uDusk), float3(0.16, 0.18, 0.3), night);
  float3 light = amb*(0.5 + 0.3*n.z);
  for (int j = 0; j < 2; j++) {
    float3 L = j == 0 ? Ls : Lm;
    float I = j == 0 ? Is : Im;
    if (I < 0.001) continue;
    float sh = 1.0, st = 0.02;
    for (int i = 0; i < 14; i++) {
      float d = sceneAt(p + n*0.004 + L*st, row).x;
      sh = min(sh, 7.0*d/st);
      st += clamp(d, 0.02, 0.15);
      if (sh < 0.02 || st > 1.2) break;
    }
    float diff = clamp((dot(n, L) + 0.2)/1.2, 0.0, 1.0);
    light += (j == 0 ? sunCol : moonCol)*I*diff*clamp(sh, 0.0, 1.0)*0.62;
  }
  float3 col = alb*light;
  float3 hv = normalize(Ls + float3(0.0, 0.0, 1.0));
  col += sunCol*Is*pow(max(dot(n, hv), 0.0), 40.0)*0.22;
  col += moonCol*Im*pow(max(dot(n, normalize(Lm + float3(0.0, 0.0, 1.0))), 0.0), 40.0)*0.3;
  col += lampC*spill*0.45*night + emit;
  col = clamp(col, 0.0, 1.0);
  float a = hit ? 1.0 : 1.0 - smoothstep(0.0, px*1.2, closest);
  float3 under = underA > 0.0 ? toP3(underRGB/max(underA, 0.0001))*underA : float3(0.0);
  return half4(half3(toP3(col)*a + under*(1.0 - a)), half(a + underA*(1.0 - a)));
}
`;
