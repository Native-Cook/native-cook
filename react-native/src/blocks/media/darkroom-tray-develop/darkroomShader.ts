// Darkroom Tray Develop — the scene in one full-screen pass: a dark wet bench, a ribbed plastic
// developing tray with a pair of bamboo tongs on its rim, a fibre print under a few millimetres of
// liquid, all lit by a red safelight that switches to white room light at the end.
//
// The print: the photo (a child shader) becomes a black-and-white print. Each point's target
// density is 1 − luminance; it shows once the local development (the tray's development minus a
// seeded fluid-front lag, so it arrives unevenly, from the side the wave comes from) passes the
// point's lightness: shadows come first, then the midtones, the highlights last.
//
// The liquid: a sheet whose surface tilts with the rocking (`rock.x`, −1 … 1) and carries a
// travelling wave (`rock.y` its height). Where the tilt lifts the surface above the floor the print
// is wet (glossy, refracted by the surface's slope, a moving reflection of the lamp); where it
// drains, the print is matte and a bright meniscus line runs along the liquid's edge.
//
// Two sheets can be in the tray: sheet A (development `devA`, offset `slide.x`) and a fresh blank
// sheet B (offset `slide.y`) sliding in from the left when A slides out to the right.
//
// Portable: fixed loops, no arrays, no dynamic indexing. The output is opaque and converted from
// sRGB to Display P3 (Skia treats runtime-effect output as P3 on iOS).

export const DARKROOM_SKSL = `
uniform shader photo;
uniform float2 imgSize;
uniform float2 size;
uniform float4 tray;
uniform float4 sheet;
uniform float devA;
uniform float devB;
uniform float2 slide;
uniform float2 rock;
uniform float wphase;
uniform float light;
uniform float stopBath;

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
  for (int i = 0; i < 3; i++) {
    v += a * noise(p);
    p = p * 2.1 + float2(13.0, 7.0);
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

// A sheet of paper at offset dx: x = cover (0 … 1), yz = its uv (0 … 1).
float3 paper(float2 p, float dx) {
  float2 c = sheet.xy + float2(dx, 0.0);
  // A slight turn, so it does not sit square in the tray.
  float a = -0.026 + dx * 0.0004;
  float2 d = p - c;
  float2 r = float2(d.x * cos(a) + d.y * sin(a), -d.x * sin(a) + d.y * cos(a));
  float dd = sdRoundBox(r, sheet.zw * 0.5, 2.0);
  float cover = clamp(0.5 - dd, 0.0, 1.0);
  return float3(cover, r / sheet.zw + 0.5);
}

// The print's tone (white light) at uv for development dev.
float3 print(float2 uv, float dev) {
  // An 8 mm white border, then the image.
  float2 bw = float2(0.06, 0.05);
  float2 iuv = (uv - bw) / (1.0 - 2.0 * bw);
  // Fibre paper with a visible tooth.
  float tooth = noise(uv * float2(260.0, 325.0)) * 0.6 + noise(uv * float2(90.0, 30.0)) * 0.4;
  float3 paperWhite = float3(0.95, 0.94, 0.915) * (0.86 + 0.14 * tooth);
  if (iuv.x < 0.0 || iuv.y < 0.0 || iuv.x > 1.0 || iuv.y > 1.0) return paperWhite;
  float3 rgb = photo.eval(iuv * imgSize).rgb;
  float lum = dot(rgb, float3(0.3, 0.59, 0.11));
  // A print's curve: a little more contrast, deep blacks.
  lum = clamp((lum - 0.06) * 1.18, 0.0, 1.0);
  float target = 1.0 - lum;
  // The fluid front: development arrives unevenly, from the side the wave starts on.
  float lag = 0.55 * fbm(iuv * float2(3.0, 3.6) + 2.0) + 0.3 * iuv.x;
  float local = clamp(dev - lag * 0.22 * (1.0 - dev), 0.0, 1.0);
  // A point shows once its darkness passes a threshold that falls as it develops, and the blacks
  // keep deepening until the end.
  float th = 1.0 - local * 1.25;
  float shown = target * smoothstep(th - 0.1, th + 0.25, target) * (0.35 + 0.65 * local);
  // The latent image: a faint ghost of the shadows from the first moments in the developer.
  // It covers the whole image (midtones too), strong enough to read under the red light.
  shown = max(shown, target * (0.22 + 0.24 * target) * smoothstep(0.0, 0.14, dev));
  float3 ink = float3(0.06, 0.055, 0.05);
  return mix(paperWhite, ink, shown);
}

half4 main(float2 p) {
  float3 redLight = float3(1.0, 0.17, 0.11);
  float3 room = float3(1.0, 0.985, 0.965);
  float3 lightCol = mix(redLight * 0.78, room, light);

  // The bench: wet black laminate, a pool of light from the lamp above.
  float2 lampAt = float2(size.x * 0.5, -size.y * 0.12);
  float bl = exp(-length((p - lampAt) / float2(size.x * 0.75, size.y * 0.75)) * 1.6);
  float3 col = float3(0.05, 0.048, 0.046) + float3(0.32, 0.3, 0.29) * bl;
  col *= 0.9 + 0.1 * fbm(p * 0.02);

  // The tray: a dark plastic shell with a raised rim and a ribbed floor.
  float2 tc = tray.xy + tray.zw * 0.5;
  float2 tq = p - tc;
  float dOuter = sdRoundBox(tq, tray.zw * 0.5, 26.0);
  float dInner = sdRoundBox(tq, tray.zw * 0.5 - 14.0, 16.0);
  // Its shadow on the bench (two: contact and soft).
  float sh = 1.0 - 0.55 * smoothstep(30.0, -4.0, sdRoundBox(tq - float2(0.0, 10.0), tray.zw * 0.5, 26.0)) - 0.25 * smoothstep(2.0, -1.0, dOuter + 2.0);
  col *= clamp(sh, 0.25, 1.0);
  if (dOuter < 0.5) {
    float3 rim = float3(0.3, 0.3, 0.31);
    // The rim's top catches the lamp along its upper inside edge.
    float edgeLit = g2((dInner - 6.0) / 4.0);
    rim += float3(0.5, 0.5, 0.5) * edgeLit * (0.5 - 0.5 * tq.y / tray.w) ;
    rim *= 0.85 + 0.15 * smoothstep(-14.0, 0.0, dOuter);
    col = mix(col, rim, clamp(0.5 - dOuter, 0.0, 1.0));
  }
  if (dInner < 0.5) {
    // The floor, with ribs that stop paper sticking to it.
    float rib = smoothstep(0.32, 0.5, abs(fract((p.y - tray.y) / 18.0) - 0.5));
    float3 floorC = float3(0.3, 0.3, 0.31) * (1.0 - 0.55 * rib) + float3(0.08) * g2((fract((p.y - tray.y) / 18.0) - 0.2) / 0.06);
    // The inner wall shadows the floor's edge.
    floorC *= 0.55 + 0.45 * smoothstep(0.0, 22.0, -dInner);

    // The liquid's surface: tilted by the rocking, with a wave running across.
    float u = tq.x / (tray.z * 0.5);
    float v = tq.y / (tray.w * 0.5);
    float wave = rock.y * sin(u * 4.2 - wphase) * (0.75 + 0.25 * cos(v * 2.0 + wphase * 0.5));
    float surf = 7.0 - rock.x * 10.0 * u + 4.0 * wave;
    float slope = -rock.x * 10.0 / (tray.z * 0.5) + 4.0 * rock.y * cos(u * 4.2 - wphase) * 4.2 / (tray.z * 0.5);
    float wet = smoothstep(-0.6, 0.6, surf);
    // Refraction: under the liquid the floor and the paper are seen shifted by the slope.
    float2 rp = p + float2(slope * 140.0 * wet, 0.0);

    float3 under = floorC;
    float3 a = paper(rp, slide.x);
    if (a.x > 0.0) under = mix(under, print(a.yz, devA), a.x);
    float3 b = paper(rp, slide.y);
    if (b.x > 0.0) under = mix(under, print(b.yz, devB), b.x);
    // Paper casts a faint shadow on the floor.
    float3 sa = paper(rp + float2(-2.0, -3.0), slide.x);
    under *= 1.0 - 0.25 * sa.x * (1.0 - a.x);

    // The liquid: developer is clear, the stop bath faintly amber.
    float3 liquidTint = mix(float3(0.96, 0.98, 1.0), float3(1.0, 0.86, 0.55), stopBath);
    float3 wetC = under * liquidTint * 0.92;
    // A moving reflection of the lamp, and glints on the crests of the wave.
    // The lamp's reflection: a long soft streak that slides with the surface's slope.
    float2 refl = float2(tc.x - slope * 900.0, tray.y + tray.w * 0.2);
    float2 rd = (p - refl) / float2(120.0, 34.0);
    wetC += float3(1.0, 0.98, 0.95) * 0.16 * exp(-dot(rd, rd) * 1.4);
    // A broad sheen band across the liquid, sliding with the tilt.
    float band = (p.x - tc.x + slope * 1400.0 + (p.y - tc.y) * 0.35) / (tray.z * 0.16);
    wetC += float3(1.0, 0.97, 0.94) * 0.2 * g2(band) * (0.8 + 0.2 * noise(p * 0.04));
    // Ripples catch the light: thin bright bands along the wave's crests, broken by noise, and a
    // faint static shimmer so the liquid reads even when it is still.
    float crest = sin(u * 4.2 - wphase + 0.6 * noise(float2(v * 3.0, u * 2.0)));
    wetC += float3(1.0) * (0.03 + 0.22 * rock.y) * smoothstep(0.82, 0.98, crest) * (0.6 + 0.4 * noise(p * 0.05));
    wetC += float3(1.0) * 0.035 * smoothstep(0.62, 0.9, noise(p * float2(0.035, 0.09) + float2(wphase * 0.3, 0.0)));
    // The dry print is matte and a touch lighter; the liquid's edge is a bright meniscus.
    float3 dryC = under * 1.04;
    float3 inside = mix(dryC, wetC, wet);
    inside += float3(0.9, 0.9, 0.9) * 0.3 * g2(surf / 0.7) * step(0.02, abs(rock.x) + rock.y);
    // The meniscus where the liquid climbs the tray's walls, rippling with the wave.
    float menisc = g2((dInner + 2.5 + 1.2 * wave) / 1.6);
    inside += float3(1.0, 0.98, 0.95) * 0.28 * menisc;
    col = mix(col, inside, clamp(0.5 - dInner, 0.0, 1.0));
  }

  // The tongs: two bamboo strips resting on the rim at the top right.
  float2 tg = p - float2(tray.x + tray.z - 58.0, tray.y + 26.0);
  float ta = 0.62;
  float2 tr = float2(tg.x * cos(ta) + tg.y * sin(ta), -tg.x * sin(ta) + tg.y * cos(ta));
  float dT1 = sdRoundBox(tr - float2(0.0, -5.0), float2(66.0, 4.5), 4.0);
  float dT2 = sdRoundBox(tr - float2(0.0, 5.0), float2(66.0, 4.5), 4.0);
  float dT = min(dT1, dT2);
  float tShadow = smoothstep(8.0, -2.0, min(sdRoundBox(tr - float2(4.0, 3.0), float2(66.0, 4.5), 4.0), sdRoundBox(tr - float2(4.0, 13.0), float2(66.0, 4.5), 4.0)));
  col *= 1.0 - 0.35 * tShadow * clamp(dT, 0.0, 1.0);
  if (dT < 0.5) {
    float3 bam = mix(float3(0.62, 0.46, 0.28), float3(0.74, 0.58, 0.38), 0.5 + 0.5 * sin(tr.x * 0.5 + noise(tr * 0.3) * 3.0));
    bam *= 0.75 + 0.25 * smoothstep(-4.0, 0.0, -abs(tr.y) + 9.0);
    bam += float3(0.2, 0.18, 0.15) * g2((tr.y + 8.0) / 1.5) + float3(0.2, 0.18, 0.15) * g2((tr.y - 2.0) / 1.5);
    col = mix(col, bam, clamp(0.5 - dT, 0.0, 1.0));
  }

  col *= lightCol;
  // The safelight's own glow above, gone when the room light is on.
  col += redLight * 0.12 * (1.0 - light) * exp(-length((p - lampAt) / float2(size.x * 0.6, size.y * 0.3)) * 2.0);
  // Vignette and grain.
  float2 vc = (p / size - 0.5) * float2(1.15, 1.0);
  col *= 1.0 - (0.62 - 0.22 * light) * dot(vc, vc);
  col += (hash(floor(p * 2.0)) - 0.5) * 0.03 * (0.6 + 0.4 * light);
  return half4(half3(toP3(col)), 1.0);
}
`;
