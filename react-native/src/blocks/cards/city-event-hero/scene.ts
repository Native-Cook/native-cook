/**
 * The scene shader: draws the photo as a spatial scene and everything that happens to it.
 *
 *   1. Layers. Four baked layers (made once from a depth map, see the notes): the back plate (the photo
 *      with the tower and the near towers painted out, `fill`), whose parallax grows with its depth
 *      (`backDepth`), the tower (`towerMask`) and the near towers (`nearMask`). A camera offset `cam`
 *      (photo px) and dolly `cam.z` move each layer by its share p. At rest (cam = 0) it is the photo.
 *   2. Sundial: the sun breaks through, warms the photo, lights the clouds near it, glints across the
 *      tower's glass (a cylinder's specular band), rims the sun-side edges (from the depth gradient), and
 *      the tower's shadow is cast on the ground plane of a pinhole camera fitted to the photo.
 *   3. Night (round 2): the grade, the window lights behind a front, their bloom.
 *   4. LED façade: the tower read as a cylinder, one LED row per floor; a colour field, a boot scan, a
 *      message and a date in a 5 × 7 font (`glyphs`), the viewer's portrait (`avatar`) wrapped round the
 *      tower; its light spills on the haze and the city.
 *   5. Fireworks: each burst lights the scene in its own colour, falling off in 3D (depth), the glass
 *      catching it most; each leaves smoke that drifts up and fades.
 *
 * Skia evaluates runtime effects in the screen's colour space (Display P3 on iPhone), so the photo is
 * converted to sRGB first and the result back to P3; every constant is sRGB (`theme`). Grey maps are
 * grey, which survives the conversion untouched.
 *
 * `mode` 1 draws only the tower and near layers (premultiplied by their masks): drawn again over the
 * hour title, it puts the title between the sky and the tower.
 */
import { Skia } from '@shopify/react-native-skia';

import { DEPTH_SCALE, FIRE, LED, NIGHT, SPACE, TOWER, VIEW_CAM } from './motion';
import { night as N, scene as S } from './theme';

const v3 = (c: readonly number[]) => `float3(${c.map((x) => x.toFixed(4)).join(', ')})`;
const f = (x: number) => x.toFixed(4);

/** Glyphs of `assets/glyphs.png`, left to right. */
export const GLYPHS = ' 0123456789AEHJNORSTUY';
const glyph = (ch: string) => Math.max(0, GLYPHS.indexOf(ch));

/** `float name(float k)`: the glyph index of character k of `text` (if-chain, no arrays in SkSL). */
function lookup(name: string, text: string) {
  const lines = [...text].map((ch, i) => `  if (k < ${i}.5) return ${glyph(ch)}.0;`);
  return `float ${name}(float k) {\n${lines.join('\n')}\n  return 0.0;\n}`;
}

/** The date page: two digits (a uniform), then J A N. */
const DATE_TAIL = 'JAN';

/** Pinhole camera fitted to the photo: horizon, the tower's foot at depth zb, its height in camera heights. */
const ZB = VIEW_CAM.f / (TOWER.baseY - VIEW_CAM.horizon);
const XB = ((TOWER.tipX - VIEW_CAM.cx) * ZB) / VIEW_CAM.f;
const HT = 1 + (VIEW_CAM.horizon - TOWER.tipY) / (TOWER.baseY - VIEW_CAM.horizon);
const WB = (46 * ZB) / VIEW_CAM.f;

export const SCENE_SKSL = `
uniform shader photo;
uniform shader fill;
uniform shader towerMask;
uniform shader nearMask;
uniform shader backDepth;
uniform shader depthMap;
uniform shader lightMap;
uniform shader keyMap;
uniform shader bloomMap;
uniform shader bloomKeyMap;
uniform shader facade;
uniform shader glyphs;
uniform shader avatar;
uniform float mode;   // 0 = whole scene, 1 = tower and near layers only (over the title)
uniform float night;
uniform float3 geo;   // photo left, photo top (dp), dp per photo px
uniform float3 cam;   // camera offset x, y (photo px), dolly
uniform float4 sun;   // azimuth, elevation (rad), amount, -
uniform float3 f0;    // bursts: x, y (dp), light amount
uniform float3 f1;
uniform float3 f2;
uniform float3 c0;    // burst colours
uniform float3 c1;
uniform float3 c2;
uniform float3 smoke; // seconds since each burst (< 0 = none)
uniform float3 flare; // spire tip x, y (dp), amount
uniform float4 ledA;  // on, scan front (rows), page (-1 none), page's bottom row
uniform float4 ledB;  // letters lit, day number, portrait y, radius (photo px)
uniform float4 ledC;  // portrait visible, white flash, field phase (rows), spill
uniform float4 ledD;  // dissolve, smoke strength, -, -
uniform float drift;

float3 toSrgb(float3 p) {
  float3 l = pow(max(p, float3(0.0)), float3(2.2));
  float3 s = float3(1.2249 * l.r - 0.2247 * l.g,
                    -0.0420 * l.r + 1.0419 * l.g,
                    -0.0197 * l.r - 0.0786 * l.g + 1.0979 * l.b);
  return pow(max(s, float3(0.0)), float3(1.0 / 2.2));
}
float3 toP3(float3 s) {
  float3 l = pow(max(s, float3(0.0)), float3(2.2));
  float3 p = float3(0.8225 * l.r + 0.1774 * l.g,
                    0.0332 * l.r + 0.9669 * l.g,
                    0.0171 * l.r + 0.0724 * l.g + 0.9108 * l.b);
  return pow(max(p, float3(0.0)), float3(1.0 / 2.2));
}
float hash21(float2 p) {
  return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}
float vnoise(float2 p) {
  float2 i = floor(p);
  float2 u = fract(p);
  u = u * u * (3.0 - 2.0 * u);
  return mix(mix(hash21(i), hash21(i + float2(1.0, 0.0)), u.x), mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), u.x), u.y);
}
float sq(float x) { return x * x; }
float2 dp(float2 q) { return geo.xy + q * geo.z; }
float gateAt(float k, float front) {
  return smoothstep(k - ${f(NIGHT.soft)}, k, front) * step(0.01, k);
}
${LED.words.map((w, i) => lookup(`word${i}`, w)).join('\n')}
${lookup('dateTail', `  ${DATE_TAIL}`)}

// Where a layer with parallax share p shows its content at screen photo point P.
float2 layerUv(float2 P, float p) {
  float zoom = cam.z * ${f(SPACE.dolly)} + abs(cam.x) * ${f(SPACE.coverX)} + abs(cam.y) * ${f(SPACE.coverY)};
  float2 F = float2(${f(SPACE.focusX)}, ${f(SPACE.focusY)});
  return F + (P - F - cam.xy * p) / (1.0 + zoom * p);
}

// Glyph index of letter k on page p (the words, then the date).
float pageGlyph(float p, float k) {
${LED.words.map((_, i) => `  if (p < ${i}.5) return word${i}(k);`).join('\n')}
  float tens = floor(ledB.y / 10.0);
  if (k < 0.5) return 1.0 + tens;
  if (k < 1.5) return 1.0 + ledB.y - 10.0 * tens;
  return dateTail(k);
}

// One LED cell of the façade: colour (already including the panel's brightness).
float3 ledContent(float col, float row) {
  float b = (col + 0.5) / ${f(LED.cols)};
  // The boot scan: nothing above its front, a bright edge at it.
  if (row > ledA.y) return float3(0.0);
  // Colour field behind the message: waves travelling up the tower.
  float w = 0.5 + 0.5 * sin(6.2832 * (row - ledC.z) / ${f(LED.fieldWave)} + 2.2 * b);
  float3 c = mix(${v3(S.fieldA)}, ${v3(S.fieldB)}, w) * ${f(S.ledField)} * (0.8 + 0.4 * hash21(float2(col, row)));
  c += ${v3(S.ledScan)} * exp(-sq((ledA.y - row) / 6.0)) * step(ledA.y, 9999.0);
  // A page of upright letters stacked top to bottom.
  if (ledA.z > -0.5) {
    float p = ledA.z;
    float date = step(${f(LED.words.length - 0.5)}, p);
    float n = date > 0.5 ? 5.0 : (p < 0.5 ? ${f(LED.words[0].length)} : (p < 1.5 ? ${f(LED.words[1].length)} : ${f(LED.words[2].length)}));
    float h = n * ${f(LED.slot)} - ${f(LED.slot - LED.glyphRows * 7)} + date * ${f(LED.dateGap)};
    float yy = h - 1.0 - (row - ledA.w);
    if (date > 0.5 && yy >= ${f(2 * LED.slot)}) yy -= ${f(LED.dateGap)};
    float k = floor(yy / ${f(LED.slot)});
    float inner = yy - k * ${f(LED.slot)};
    float gc = floor((col - ${f(LED.colsFrom)}) / ${f(LED.glyphCols)});
    if (yy >= 0.0 && k < n && inner < ${f(LED.glyphRows * 7)} && gc >= 0.0 && gc < 5.0) {
      float g = pageGlyph(p, k);
      float bit = glyphs.eval(float2(g * 5.0 + gc + 0.5, floor(inner / ${f(LED.glyphRows)}) + 0.5)).r;
      float age = (ledB.x - k) * ${f(LED.stagger)};
      float lit = step(0.0, age);
      float3 tc = mix(${v3(S.ledHot)}, ${v3(S.ledText)}, smoothstep(0.0, ${f(LED.letterFlash)}, age));
      c = mix(c, tc * (1.0 + 0.8 * exp(-max(age, 0.0) / 0.08)), bit * lit);
    }
  }
  // The viewer's portrait, wrapped round the tower, in a gold ring.
  if (ledC.x > 0.5 && ledB.w > 0.3) {
    float y = ${f(TOWER.baseY)} - (row + 0.5) * ${f(LED.row)};
    float hw = facade.eval(dp(float2(${f(LED.mid)}, y))).r * 64.0;
    float x = ((col + 0.5) / ${f(LED.cols)} - 0.5) * 3.14159 * hw;
    float2 d = float2(x, y - ledB.z) / ledB.w;
    float r = length(d);
    if (r < 1.0) {
      float3 face = toSrgb(float3(avatar.eval((d * 0.5 + 0.5) * 180.0).rgb));
      c = mix(face * 1.2, ${v3(S.ledRing)} * 1.2, smoothstep(0.8, 0.88, r));
    }
  }
  return c;
}

half4 main(float2 xy) {
  float k = geo.z;
  float2 P = (xy - geo.xy) / k;

  // 1. Layers.
  float moving = step(0.001, abs(cam.x) + abs(cam.y) + cam.z);
  float2 uvB = P;
  float2 uvT = P;
  float2 uvN = P;
  float3 c = float3(0.0);
  float aT = 0.0;
  float aN = 0.0;
  if (moving > 0.5) {
    float pb = ${f(SPACE.far)} + ${f(SPACE.perDepth * DEPTH_SCALE)} * backDepth.eval(xy).r;
    uvB = layerUv(P, pb);
    uvT = layerUv(P, ${f(SPACE.tower)});
    uvN = layerUv(P, ${f(SPACE.near)});
    float hole = smoothstep(0.0, 0.35, max(towerMask.eval(dp(uvB)).r, nearMask.eval(dp(uvB)).r));
    c = mix(toSrgb(float3(photo.eval(dp(uvB)).rgb)), toSrgb(float3(fill.eval(dp(uvB)).rgb)), hole);
    aT = towerMask.eval(dp(uvT)).r;
    aN = nearMask.eval(dp(uvN)).r;
    if (aT > 0.0) c = mix(c, toSrgb(float3(photo.eval(dp(uvT)).rgb)), aT);
    if (aN > 0.0) c = mix(c, toSrgb(float3(photo.eval(dp(uvN)).rgb)), aN);
  } else {
    c = toSrgb(float3(photo.eval(xy).rgb));
    aT = towerMask.eval(xy).r;
    aN = nearMask.eval(xy).r;
  }
  if (mode > 0.5 && max(aT, aN) < 0.002) return half4(0.0);
  float2 uv = aN > 0.5 ? uvN : (aT > 0.5 ? uvT : uvB);
  float2 at = dp(uv);
  float lum = dot(c, float3(0.299, 0.587, 0.114));
  float key = keyMap.eval(at).r;
  float obj = max(smoothstep(0.0, 0.04, key), max(aT, aN));

  // 2. Sundial.
  if (sun.z > 0.0) {
    float az = sun.x;
    float el = sun.y;
    float3 L = float3(sin(az) * cos(el), sin(el), cos(az) * cos(el));
    float3 sc = mix(${v3(S.sunLow)}, ${v3(S.sunHigh)}, smoothstep(0.05, 0.6, el));
    float3 ray = normalize(float3((uv.x - ${f(VIEW_CAM.cx)}) / ${f(VIEW_CAM.f)}, -(uv.y - ${f(VIEW_CAM.horizon)}) / ${f(VIEW_CAM.f)}, 1.0));
    float ca = dot(ray, L);
    float glow = 0.9 * exp((ca - 1.0) / 0.05) + 0.35 * exp((ca - 1.0) / 0.45);
    float low = 1.0 - smoothstep(0.0, 0.45, el);
    float sunlit = 1.0;
    // The tower's shadow on the ground plane (the city behind the near towers).
    if (uvB.y > ${f(VIEW_CAM.horizon + 2)} && uvB.y < ${f(TOWER.baseY)} && aN < 0.5 && aT < 0.5) {
      float Z = ${f(VIEW_CAM.f)} / (uvB.y - ${f(VIEW_CAM.horizon)});
      float2 rel = float2((uvB.x - ${f(VIEW_CAM.cx)}) * Z / ${f(VIEW_CAM.f)} - ${f(XB)}, Z - ${f(ZB)});
      float2 d = float2(-sin(az), -cos(az));
      float len = ${f(HT)} / tan(max(el, 0.03));
      float along = dot(rel, d);
      float across = abs(rel.x * d.y - rel.y * d.x);
      float w = ${f(WB)} * clamp(1.0 - along / len, 0.0, 1.0);
      float inside = smoothstep(0.0, 0.3, along) * smoothstep(w + 0.03 + 0.05 * along, w * 0.3, across) * smoothstep(len, len * 0.7, along);
      sunlit = 1.0 - inside;
    }
    // Sunlit surfaces warm and brighten; the shadow keeps the overcast's cool light, a little darker.
    float3 warmed = c * mix(float3(1.0), sc * 1.2, ${f(S.warm)}) * (1.0 + ${f(S.lift)} * (0.5 + 0.5 * obj));
    float3 shaded = c * (1.0 - ${f(S.shadow)} * 0.5) * ${v3(S.shadowTint)};
    c = mix(c, mix(shaded, warmed, sunlit), sun.z);
    c += sc * sun.z * glow * (0.2 + smoothstep(0.3, 0.75, lum)) * ${f(S.cloudLit)} * (1.0 - 0.7 * obj);
    c += ${v3(S.sunLow)} * sun.z * low * ${f(S.horizonGlow)} * exp(-sq((uv.y - ${f(VIEW_CAM.horizon - 40)}) / 170.0)) * (0.35 + lum) * (1.0 - 0.6 * obj);
    c += float3(1.0, 0.95, 0.85) * sun.z * smoothstep(0.99988, 0.99996, ca) * (1.0 - obj);
    // Glass: the tower as a cylinder, a specular band where it mirrors the sun.
    if (aT > 0.0) {
      float hw = max(facade.eval(dp(float2(${f(LED.mid)}, uvT.y))).r * 64.0, 0.5);
      float s = clamp((uvT.x - ${f(LED.mid)}) / hw, -0.999, 0.999);
      float th = asin(s);
      float3 n = float3(sin(th), 0.0, -cos(th));
      float3 h = normalize(L + float3(0.0, 0.0, -1.0));
      float spec = pow(max(dot(n, h), 0.0), ${f(S.glintPower)});
      float diff = max(dot(n, L), 0.0);
      c += sc * sun.z * aT * (spec * ${f(S.glint)} + diff * 0.22);
    }
    // Edges facing the sun catch it (depth steps up into a building on its sun side).
    float gx = depthMap.eval(at + float2(3.0 * k, 0.0)).r - depthMap.eval(at - float2(3.0 * k, 0.0)).r;
    float side = L.x < 0.0 ? 1.0 : -1.0;
    c += sc * sun.z * clamp(gx * side * 18.0, 0.0, 1.0) * abs(L.x) * ${f(S.rim)} * (1.0 - aT);
  }

  // 3. Night (round 2).
  float g = smoothstep(0.0, ${f(NIGHT.grade)}, night);
  float3 o = c;
  if (night > 0.0) {
    float front = -0.05 + 1.1 * smoothstep(${f(NIGHT.frontFrom)}, 1.0, night);
    float up = smoothstep(${f(N.skyFrom)}, ${f(N.zenithY)}, uv.y);
    float3 sky = mix(${v3(N.horizon)}, ${v3(N.zenith)}, up) * clamp(0.15 + 2.4 * (lum - 0.22), 0.08, 1.6);
    sky += ${v3(N.cityGlow)} * exp(-sq((uv.y - ${f(N.horizonY)}) / 150.0)) * clamp(0.4 + 1.6 * (lum - 0.25), 0.2, 1.2) * smoothstep(0.3, 1.0, night);
    float3 nightCol = mix(sky, c * ${v3(N.buildings)}, obj);
    float3 after = ${v3(N.afterglow)} * exp(-sq((uv.y - ${f(N.horizonY - 10)}) / 160.0)) * sin(3.14159 * g) * (1.0 - obj);
    o = mix(c, nightCol, g) + after;
    float lights = lightMap.eval(at).r;
    float gate = gateAt(key, front);
    float edge = exp(-sq((front - key) / ${f(NIGHT.edge)}));
    float warm = step(hash21(floor(uv / float2(${f(N.cell[0])}, ${f(N.cell[1])}))), ${f(N.warmShare)});
    float3 col = mix(${v3(N.cool)}, ${v3(N.warm)}, warm);
    float3 Lw = col * lights * gate * (1.0 + ${f(N.edgeGain)} * edge) * ${f(N.lightGain)};
    float3 B = ${v3(N.bloom)} * bloomMap.eval(at).r * gateAt(bloomKeyMap.eval(at).r, front) * ${f(N.bloomGain)};
    o = 1.0 - (1.0 - o) * (1.0 - clamp(Lw + B, 0.0, 1.0));
  }

  // 4. LED façade and its light.
  if (ledA.x > 0.0) {
    float hwq = facade.eval(dp(float2(${f(LED.mid)}, uvT.y))).r * 64.0;
    if (aT > 0.0 && hwq > 0.5) {
      float s = clamp((uvT.x - ${f(LED.mid)}) / hwq, -0.999, 0.999);
      float th = asin(s);
      float yRef = uvT.y + (${f(LED.eye)} - uvT.y) * ${f(LED.sag)} * (1.0 - s * s);
      float fl = (${f(TOWER.baseY)} - yRef) / ${f(LED.row)};
      float cu = (th / 3.14159 + 0.5) * ${f(LED.cols)};
      float col = floor(cu);
      float row = floor(fl);
      float cellW = hwq * (3.14159 / ${f(LED.cols)}) * max(cos(th), 0.05);
      float2 m = abs(float2(fract(cu) - 0.5, fract(fl) - 0.5)) * float2(cellW, ${f(LED.row)});
      float px = 0.6 / max(1.0 + cam.z * ${f(SPACE.dolly)}, 1.0);
      float dotx = smoothstep(cellW * 0.4 + px, cellW * 0.4 - px, m.x);
      float doty = smoothstep(${f(LED.row * 0.38)} + px, ${f(LED.row * 0.38)} - px, m.y);
      // Every second row is a floor slab: a darker scanline.
      float slab = 1.0 - 0.35 * step(0.5, fract(row * 0.5));
      float dotv = mix(dotx * doty, 0.55, smoothstep(2.2, 1.0, cellW)) * slab;
      float3 e = ledContent(col, row);
      // Cells go dark at random as the panel dissolves.
      e *= step(ledD.x, hash21(float2(col * 1.7, row)) * 0.999);
      float3 lit = e * (dotv * ${f(S.ledGain)} + 0.3) + ${v3(S.ledHot)} * ledC.y * 0.6 * dotv;
      o = mix(o, o * ${f(S.ledOff)}, ledA.x * aT) + lit * ledA.x * aT;
    }
    // Spill: a halo around the tower and its light on the haze and the facing buildings.
    float hwh = max(hwq, 0.0);
    float dEdge = max(abs(uvT.x - ${f(LED.mid)}) - hwh, 0.0) + max(${f(TOWER.tipY)} - uvT.y, 0.0) * 2.0;
    float3 sp = mix(${v3(S.spill)}, ${v3(S.spillText)}, step(-0.5, ledA.z) * 0.7);
    float halo = (0.35 * exp(-dEdge / 8.0) + 0.65 * exp(-dEdge / 55.0)) * ${f(S.halo)};
    float wash = exp(-dEdge / 170.0) * mix(0.35 + 0.9 * lum, 0.5, obj) * 0.35;
    o += sp * ledC.w * (halo + wash) * (1.0 - aT);
  }

  // 5. Fireworks: light in the burst's colour, falling off in 3D; smoke.
  if (f0.z + f1.z + f2.z > 0.0) {
    float dep = depthMap.eval(at).r * ${f(DEPTH_SCALE)};
    float dz = (dep - 1.3) * ${f(FIRE.depthDp)};
    float sky = clamp(2.2 * (lum - 0.25), 0.0, 1.0) * ${f(FIRE.flashGain)} * (1.0 - 0.6 * obj) + 0.05;
    float surf = mix(sky, 0.35 + 0.5 * lum + 0.9 * aT, obj);
    float R2 = sq(${f(FIRE.flashR)} * k);
    o += c0 * f0.z * surf * exp(-(sq(xy.x - f0.x) + sq(xy.y - f0.y) + dz * dz) / R2);
    o += c1 * f1.z * surf * exp(-(sq(xy.x - f1.x) + sq(xy.y - f1.y) + dz * dz) / R2);
    o += c2 * f2.z * surf * exp(-(sq(xy.x - f2.x) + sq(xy.y - f2.y) + dz * dz) / R2);
  }
  float t2 = sq(xy.x - flare.x) + sq(xy.y - flare.y);
  o += ${v3(N.flare)} * flare.z * (exp(-t2 / sq(${f(N.flareCore)} * k)) + 0.35 * exp(-t2 / sq(${f(N.flareHalo)} * k)));
  if (smoke.x > 0.0 || smoke.y > 0.0 || smoke.z > 0.0) {
    float3 tb = smoke;
    for (int i = 0; i < 3; i++) {
      float tt = i == 0 ? tb.x : (i == 1 ? tb.y : tb.z);
      if (tt > 0.0) {
        float2 ce = i == 0 ? f0.xy : (i == 1 ? f1.xy : f2.xy);
        ce.y -= ${f(FIRE.smokeRise)} * tt;
        float r = ${f(FIRE.smokeR)} * (0.45 + 0.55 * (1.0 - exp(-tt / 0.6)));
        float d2 = (sq(xy.x - ce.x) + sq(xy.y - ce.y)) / (r * r);
        if (d2 < 2.5) {
          float2 q = (xy - ce) / 26.0 + float2(drift * 0.15 + float(i) * 5.3, tt * 0.4);
          float n = 0.65 * vnoise(q) + 0.35 * vnoise(q * 2.3 + 7.3);
          float dens = smoothstep(0.35, 0.75, n) * exp(-d2) * smoothstep(0.0, ${f(FIRE.smokeIn)}, tt) * exp(-tt / ${f(FIRE.smokeTau)}) * ledD.y;
          float3 lit = ${v3(S.smoke)} + (c0 * f0.z + c1 * f1.z + c2 * f2.z) * 0.35 + ${v3(N.cityGlow)} * 0.3;
          o = mix(o, lit, dens * ${f(S.smokeAlpha)} * (1.0 - aT * 0.5));
        }
      }
    }
  }

  float3 res = toP3(clamp(o, 0.0, 1.0));
  if (mode > 0.5) {
    float a = max(aT, aN);
    return half4(half3(res * a), a);
  }
  return half4(half3(res), 1.0);
}
`;

export const SCENE_EFFECT = Skia.RuntimeEffect.Make(SCENE_SKSL);
