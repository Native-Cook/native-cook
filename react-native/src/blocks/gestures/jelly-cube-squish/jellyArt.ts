import { BlendMode, ClipOp, type SkCanvas, type SkFont, Skia, VertexMode } from '@shopify/react-native-skia';

import { type Deform, FACE_COUNT, HALF, N, TO_EYE, TO_LIGHT, type V3, clamp, deform, project } from './jellyMath';
import { FLAVOURS, LOOP } from './jellyMotion';
import { fill, grain, linear, radial, rgba, stroke, textWidth } from './jellyPaint';

/*
 The jelly's art. Baked once: the dessert bar backdrop (a blush wall, a marble counter) and the
 ceramic plate. Per frame: the cube's contact shadow and coloured caustic on the plate, its far
 faces, an inner glow and suspended bubbles, its near faces (colour-only meshes lit per vertex:
 diffuse, a denser core, a Fresnel rim) and an additive specular mesh, then the demo finger and
 the flavour's name.
 */

export type Geo = { w: number; h: number; cx: number; cy: number; nameBase: number; chipsTop: number; ctaTop: number };

export function makeGeo(w: number, h: number, top: number, bottom: number): Geo {
  const ctaTop = h - Math.max(bottom, 16) - 8 - 56;
  const chipsTop = ctaTop - 16 - 44;
  const nameBase = chipsTop - 22;
  // The cube's foot sits so the cube centres between the header and the name.
  const cy = top + 112 + (nameBase - 70 - (top + 112)) * 0.66;
  return { w, h, cx: w / 2, cy, nameBase, chipsTop, ctaTop };
}

function planeMatrix(g: Geo, x: number, y: number, z: number) {
  'worklet';
  const p = project(g.cx, g.cy, [x, y, z]);
  const px = project(g.cx, g.cy, [x + 1, y, z]);
  const py = project(g.cx, g.cy, [x, y + 1, z]);
  return Skia.Matrix([px[0] - p[0], py[0] - p[0], p[0], px[1] - p[1], py[1] - p[1], p[1], 0, 0, 1]);
}

export function drawBackdrop(canvas: SkCanvas, g: Geo) {
  const foot = project(g.cx, g.cy, [0, 0, 0]);
  const horizon = foot[1] - 70;
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, horizon), linear(0, 0, 0, horizon, [rgba(0xf6e6e1), rgba(0xf1d9d3), rgba(0xe9cbc4)], [0, 0.6, 1]));
  canvas.drawRect(Skia.XYWHRect(0, 0, g.w, horizon), radial(g.cx, horizon - 120, g.w * 0.8, [rgba(0xfff6f2, 0.7), rgba(0xfff6f2, 0)], [0, 1]));
  // Marble counter: pale stone with soft grey veins, lit from the top.
  canvas.drawRect(Skia.XYWHRect(0, horizon, g.w, g.h - horizon), linear(0, horizon, 0, g.h, [rgba(0xf3eeea), rgba(0xe7e0db), rgba(0xd6cdc7)], [0, 0.4, 1]));
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(0, horizon, g.w, g.h - horizon), ClipOp.Intersect, true);
  const vein = Skia.Paint();
  vein.setShader(Skia.Shader.MakeTurbulence(0.004, 0.02, 3, 17, 0, 0));
  vein.setAlphaf(0.18);
  vein.setBlendMode(BlendMode.Multiply);
  canvas.drawRect(Skia.XYWHRect(0, horizon, g.w, g.h - horizon), vein);
  canvas.restore();
  canvas.drawLine(0, horizon, g.w, horizon, stroke(0xffffff, 1.2, 0.8));
  canvas.drawRect(Skia.XYWHRect(0, horizon, g.w, 24), linear(0, horizon, 0, horizon + 24, [rgba(0x8a6a62, 0.18), rgba(0x8a6a62, 0)], null));
  grain(canvas, 0, 0, g.w, g.h, 0.08, 9);
  // The plate: a shallow ceramic dish.
  canvas.save();
  canvas.concat(planeMatrix(g, 6, -10, 0));
  canvas.drawCircle(0, 0, 140, fill(0x6a4a44, 0.22, 16));
  canvas.restore();
  canvas.save();
  canvas.concat(planeMatrix(g, 0, 0, 0));
  canvas.drawCircle(0, 0, 134, radial(-30, -40, 170, [rgba(0xffffff), rgba(0xf7f3f0), rgba(0xe2dad5)], [0, 0.6, 1]));
  canvas.drawCircle(0, 0, 112, stroke(0xd7cec9, 2, 0.7));
  canvas.drawCircle(0, 0, 110, radial(0, 0, 112, [rgba(0xfdfbfa), rgba(0xf1ece8), rgba(0xe6dfda)], [0, 0.85, 1]));
  canvas.drawCircle(0, 0, 131, stroke(0xffffff, 1.5, 0.9));
  canvas.restore();
}

export type Mesh = { rest: number[]; idx: number[] };

function mixRGB(a: number[], b: number[], t: number) {
  'worklet';
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

/** The jelly's colour at a rest point while a new flavour fills from the plate up. */
function colourAt(colour: number[], x: number, y: number, z: number) {
  'worklet';
  const from = FLAVOURS[colour[0]].rgb;
  const to = FLAVOURS[colour[1]].rgb;
  if (colour[0] === colour[1] || colour[2] <= 0) return from;
  if (colour[2] >= 1) return to;
  const front = -24 + colour[2] * (2 * HALF + 48);
  const wave = 7 * Math.sin(x / 15 + colour[2] * 9) + 5 * Math.sin(y / 19 - colour[2] * 6);
  const k = clamp((front + wave - z + 12) / 24, 0, 1);
  return mixRGB(from, to, k * k * (3 - 2 * k));
}

/** Draws the whole cube (shadow, far faces, inside, near faces, gloss). */
export function drawJelly(canvas: SkCanvas, g: Geo, mesh: Mesh, d: Deform, colour: number[], time: number) {
  'worklet';
  const per = (N + 1) * (N + 1);
  const total = per * FACE_COUNT;
  const world: number[] = new Array(total * 3);
  const screen: { x: number; y: number }[] = new Array(total);
  for (let i = 0; i < total; i += 1) {
    const p = deform(d, mesh.rest[i * 3], mesh.rest[i * 3 + 1], mesh.rest[i * 3 + 2]);
    world[i * 3] = p[0];
    world[i * 3 + 1] = p[1];
    world[i * 3 + 2] = p[2];
    const s = project(g.cx, g.cy, p);
    screen[i] = { x: s[0], y: s[1] };
  }
  const mid = colourAt(colour, 0, 0, HALF);
  // Contact shadow and the light that came through the jelly, on the plate.
  const bulge = 1 + Math.max(0, d.sq) * 0.6;
  canvas.save();
  canvas.concat(planeMatrix(g, 10, -8, 0));
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(-HALF * bulge - 6, -HALF * bulge - 6, (HALF * bulge + 6) * 2, (HALF * bulge + 6) * 2), 34, 34), fill(0x5a2a2a, 0.32, 12));
  canvas.restore();
  canvas.save();
  canvas.concat(planeMatrix(g, 58, 70, 0));
  const caustic = radial(0, 0, 96, [Float32Array.of(mid[0], mid[1], mid[2], 0.55), Float32Array.of(mid[0], mid[1], mid[2], 0.18), Float32Array.of(mid[0], mid[1], mid[2], 0)], [0, 0.5, 1]);
  caustic.setBlendMode(BlendMode.Multiply);
  canvas.drawCircle(0, 0, 96, caustic);
  const hot = radial(-10, -10, 40, [Float32Array.of(1, 1, 1, 0.35), Float32Array.of(1, 1, 1, 0)], null);
  canvas.drawCircle(-10, -10, 40, hot);
  canvas.restore();

  // Per-vertex shading, faces split by which way they face.
  const nearPos: { x: number; y: number }[] = [];
  const nearCol: Float32Array[] = [];
  const nearIdx: number[] = [];
  const farPos: { x: number; y: number }[] = [];
  const farCol: Float32Array[] = [];
  const farIdx: number[] = [];
  const specCol: Float32Array[] = [];
  const hx = TO_LIGHT[0] + TO_EYE[0];
  const hy = TO_LIGHT[1] + TO_EYE[1];
  const hz = TO_LIGHT[2] + TO_EYE[2];
  const hl = Math.hypot(hx, hy, hz);
  for (let f = 0; f < FACE_COUNT; f += 1) {
    const base = f * per;
    const normals: V3[] = [];
    let facing = 0;
    for (let j = 0; j <= N; j += 1) {
      for (let i = 0; i <= N; i += 1) {
        const a = base + j * (N + 1) + Math.min(i + 1, N);
        const b = base + j * (N + 1) + Math.max(i - 1, 0);
        const c = base + Math.min(j + 1, N) * (N + 1) + i;
        const e = base + Math.max(j - 1, 0) * (N + 1) + i;
        const ux = world[a * 3] - world[b * 3];
        const uy = world[a * 3 + 1] - world[b * 3 + 1];
        const uz = world[a * 3 + 2] - world[b * 3 + 2];
        const vx = world[c * 3] - world[e * 3];
        const vy = world[c * 3 + 1] - world[e * 3 + 1];
        const vz = world[c * 3 + 2] - world[e * 3 + 2];
        let nx = uy * vz - uz * vy;
        let ny = uz * vx - ux * vz;
        let nz = ux * vy - uy * vx;
        const l = Math.hypot(nx, ny, nz) || 1;
        nx /= l;
        ny /= l;
        nz /= l;
        // Point outward: away from the cube's axis.
        const k = base + j * (N + 1) + i;
        const ox = world[k * 3];
        const oy = world[k * 3 + 1];
        const oz = world[k * 3 + 2] - HALF;
        if (nx * ox + ny * oy + nz * oz < 0) {
          nx = -nx;
          ny = -ny;
          nz = -nz;
        }
        normals.push([nx, ny, nz]);
        facing += nx * TO_EYE[0] + ny * TO_EYE[1] + nz * TO_EYE[2];
      }
    }
    const near = facing > 0;
    const pos = near ? nearPos : farPos;
    const col = near ? nearCol : farCol;
    const idx = near ? nearIdx : farIdx;
    const offset = pos.length;
    for (let j = 0; j <= N; j += 1) {
      for (let i = 0; i <= N; i += 1) {
        const k = base + j * (N + 1) + i;
        const n = normals[j * (N + 1) + i];
        pos.push(screen[k]);
        const rgb = colourAt(colour, mesh.rest[k * 3], mesh.rest[k * 3 + 1], mesh.rest[k * 3 + 2]);
        const diff = Math.max(0, n[0] * TO_LIGHT[0] + n[1] * TO_LIGHT[1] + n[2] * TO_LIGHT[2]);
        const view = Math.max(0, n[0] * TO_EYE[0] + n[1] * TO_EYE[1] + n[2] * TO_EYE[2]);
        if (near) {
          // Looking straight in, the jelly is thinner and glows; at grazing angles it is deep and
          // saturated, with a white Fresnel sheen on top.
          const fres = Math.pow(1 - view, 3);
          const lit = 0.42 + 0.38 * diff + 0.3 * view;
          col.push(Float32Array.of(Math.min(1, rgb[0] * lit + 0.42 * fres + 0.03), Math.min(1, rgb[1] * lit + 0.42 * fres + 0.03), Math.min(1, rgb[2] * lit + 0.42 * fres + 0.04), 0.58 + 0.34 * fres + 0.08 * (1 - diff)));
          const spec = Math.pow(Math.max(0, (n[0] * hx + n[1] * hy + n[2] * hz) / hl), 70) * 1.4;
          specCol.push(Float32Array.of(spec, spec, spec, Math.min(1, spec)));
        } else {
          col.push(Float32Array.of(rgb[0] * 0.7, rgb[1] * 0.62, rgb[2] * 0.66, 0.5));
        }
      }
    }
    for (let j = 0; j < N; j += 1) {
      for (let i = 0; i < N; i += 1) {
        const a = offset + j * (N + 1) + i;
        idx.push(a, a + 1, a + N + 1, a + 1, a + N + 2, a + N + 1);
      }
    }
  }
  const plain = Skia.Paint();
  plain.setAntiAlias(true);
  if (farIdx.length) canvas.drawVertices(Skia.MakeVertices(VertexMode.Triangles, farPos, null, farCol, farIdx), BlendMode.Dst, plain);
  // Inside: a soft lighter core and a few suspended bubbles drifting on whole-loop sines.
  const core = project(g.cx, g.cy, deform(d, 0, 0, HALF));
  const glow = radial(core[0], core[1] - 10, HALF * 1.1 * core[2], [Float32Array.of(Math.min(1, mid[0] + 0.3), Math.min(1, mid[1] + 0.3), Math.min(1, mid[2] + 0.3), 0.55), Float32Array.of(mid[0], mid[1], mid[2], 0)], null);
  canvas.drawCircle(core[0], core[1] - 10, HALF * 1.1 * core[2], glow);
  for (let b = 0; b < 9; b += 1) {
    const hx2 = Math.sin(b * 12.9898 + 4.1) * 43758.5453;
    const r1 = hx2 - Math.floor(hx2);
    const hy2 = Math.sin(b * 78.233 + 1.7) * 43758.5453;
    const r2 = hy2 - Math.floor(hy2);
    const hz2 = Math.sin(b * 39.425 + 9.2) * 43758.5453;
    const r3 = hz2 - Math.floor(hz2);
    const drift = 3 * Math.sin((2 * Math.PI * (1 + (b % 2)) * time) / LOOP + b);
    const p = project(g.cx, g.cy, deform(d, (r1 - 0.5) * HALF * 1.2, (r2 - 0.5) * HALF * 1.2, 26 + r3 * (2 * HALF - 52) + drift));
    const r = (1.6 + 3.4 * ((b * 7) % 5) / 4) * p[2];
    canvas.drawCircle(p[0], p[1], r, fill(0xffffff, 0.16));
    canvas.drawCircle(p[0], p[1], r, stroke(0xffffff, 0.8, 0.5));
    canvas.drawCircle(p[0] - r * 0.35, p[1] - r * 0.35, r * 0.3, fill(0xffffff, 0.8));
  }
  if (nearIdx.length) {
    canvas.drawVertices(Skia.MakeVertices(VertexMode.Triangles, nearPos, null, nearCol, nearIdx), BlendMode.Dst, plain);
    const add = Skia.Paint();
    add.setAntiAlias(true);
    add.setBlendMode(BlendMode.Plus);
    canvas.drawVertices(Skia.MakeVertices(VertexMode.Triangles, nearPos, null, specCol, nearIdx), BlendMode.Dst, add);
  }
  // A window reflected in the top face and a streak down the front: they ride the deformation.
  const win = Skia.PathBuilder.Make();
  const winPts: number[][] = [
    [-0.62, -0.2],
    [-0.1, 0.05],
    [-0.18, 0.62],
    [-0.66, 0.4],
  ];
  for (let i = 0; i < 4; i += 1) {
    const p = project(g.cx, g.cy, deform(d, winPts[i][0] * HALF * 0.8, winPts[i][1] * HALF * 0.8, 2 * HALF));
    if (i === 0) win.moveTo(p[0], p[1]);
    else win.lineTo(p[0], p[1]);
  }
  canvas.drawPath(win.close().build(), fill(0xffffff, 0.32, 3));
  const streak = Skia.PathBuilder.Make();
  for (let i = 0; i <= 8; i += 1) {
    const p = project(g.cx, g.cy, deform(d, -HALF * 0.62, -HALF, HALF * (0.32 + (i / 8) * 1.4)));
    if (i === 0) streak.moveTo(p[0], p[1]);
    else streak.lineTo(p[0], p[1]);
  }
  canvas.drawPath(streak.build(), stroke(0xffffff, 6, 0.22, 3));
  canvas.drawPath(streak.build(), stroke(0xffffff, 1.2, 0.35));
}

/** The demo finger: a soft translucent fingertip. */
export function drawFinger(canvas: SkCanvas, g: Geo, f: number[]) {
  'worklet';
  if (f[3] <= 0.01) return;
  const p = project(g.cx, g.cy, [f[0], f[1], f[2]]);
  canvas.drawCircle(p[0], p[1] + 3, 22, fill(0x3a1a1a, 0.18 * f[3], 6));
  canvas.drawCircle(p[0], p[1], 20, fill(0xffffff, 0.42 * f[3]));
  canvas.drawCircle(p[0], p[1], 20, stroke(0xffffff, 1.5, 0.85 * f[3]));
}

/** "Strawberry Wobble" rolling to the next flavour's name, and the price. */
export function drawName(canvas: SkCanvas, g: Geo, fonts: { name: SkFont; price: SkFont; sub: SkFont }, colour: number[]) {
  'worklet';
  const roll = colour[3];
  const names = [FLAVOURS[colour[0]].name, FLAVOURS[colour[1]].name];
  canvas.save();
  canvas.clipRect(Skia.XYWHRect(0, g.nameBase - 30, g.w, 40), ClipOp.Intersect, true);
  for (let j = 0; j < 2; j += 1) {
    const a = 1 - Math.min(1, Math.abs(j - roll));
    if (a <= 0.01) continue;
    canvas.drawText(`${names[j]} Wobble`, 24, g.nameBase + (j - roll) * 28, fill(0x3a1f22, a), fonts.name);
  }
  canvas.restore();
  const price = '$6.50';
  canvas.drawText(price, g.w - 24 - textWidth(fonts.price, price), g.nameBase, fill(0x3a1f22), fonts.price);
}
