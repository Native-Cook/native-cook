/*
 Warp Coin Welcome — the tunnel, the coin and the glint, drawn with Skia in a 393-wide design space.
 Every worklet here is declared above the worklets that call it.
 */
import { BlendMode, BlurStyle, PaintStyle, Skia, StrokeCap, StrokeJoin, TileMode, type SkCanvas, type SkPaint, type SkShader } from '@shopify/react-native-skia';

import { type Frame, LOOP, REST_SPEED, BOOST, smooth, vanishY } from './warpCoinMotion';

const COUNT = 340;
const FOCAL = 393;
const RADIUS = 134;
const THICKNESS = 30;
const SEGMENTS = 120;

function hash(x: number) {
  const v = Math.sin(x * 12.9898) * 43758.5453;
  return v - Math.floor(v);
}

function rgb(hex: number): number[] {
  return [((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255];
}

/** The streak's hot core: its colour 35 % of the way to white. */
function lighten(hex: number): number[] {
  const out: number[] = [];
  for (const shift of [16, 8, 0]) {
    const v = (hex >> shift) & 255;
    out.push(Math.round(v + (255 - v) * 0.35) / 255);
  }
  return out;
}

function normalize(x: number, y: number, z: number): number[] {
  const l = Math.sqrt(x * x + y * y + z * z);
  return [x / l, y / l, z / l];
}

/** Direction to the light (upper left, in front) and the half vector with the view direction. */
const LIGHT = normalize(-0.35, -0.85, -0.4);
const HALF = normalize(LIGHT[0], LIGHT[1], LIGHT[2] - 1);

/** Per particle: r, phase, size, cos, sin, then colour rgb and core rgb (11 numbers). */
const PARTICLES: number[] = [];
for (let i = 0; i < COUNT; i++) {
  const h1 = hash(i * 1.37 + 0.11);
  const h2 = hash(i * 2.71 + 3.3);
  const h3 = hash(i * 0.913 + 7.7);
  const h4 = hash(i * 5.17 + 1.9);
  const h5 = hash(i * 3.33 + 9.1);
  const hex =
    h4 < 0.4 ? 0x4a66ff : h4 < 0.54 ? 0x8a5cff : h4 < 0.78 ? 0xe8eeff : h4 < 0.87 ? 0x4fd8ff : h4 < 0.95 ? 0xb4ff5a : 0xff5ad8;
  const angle = 2 * Math.PI * h1;
  PARTICLES.push(0.05 + 0.7 * h2, h3, h5, Math.cos(angle), Math.sin(angle), ...rgb(hex), ...lighten(hex));
}

const FACE_HEX = [0xf4f5f7, 0xc9ccd2, 0x9599a1, 0x8a8e96, 0xa3a7ae, 0xd2d5da];
const FACE_RGB: number[] = [];
for (let r = 0; r < 2; r++) for (const hex of FACE_HEX) FACE_RGB.push(...rgb(hex));
FACE_RGB.push(...rgb(0xf4f5f7));
const FACE_STOPS = [0, 0.08, 0.18, 0.25, 0.32, 0.42, 0.5, 0.58, 0.68, 0.75, 0.82, 0.92, 1];

/** The Arcwell mark in unit coordinates: an arch with a bar, like a rounded A. */
const ARCH_POINTS: number[] = [-0.4, 0.48, -0.4, 0.03];
for (let j = 1; j <= 32; j++) {
  const a = Math.PI + (Math.PI * j) / 32;
  ARCH_POINTS.push(0.4 * Math.cos(a), 0.03 + 0.4 * Math.sin(a));
}
ARCH_POINTS.push(0.4, 0.48);

function col(r: number, g: number, b: number, a: number) {
  'worklet';
  return Float32Array.of(r, g, b, Math.min(1, Math.max(0, a)));
}

function hexCol(hex: number, a: number) {
  'worklet';
  return col(((hex >> 16) & 255) / 255, ((hex >> 8) & 255) / 255, (hex & 255) / 255, a);
}

function dot3(ax: number, ay: number, az: number, b: number[]) {
  'worklet';
  return ax * b[0] + ay * b[1] + az * b[2];
}

function faceShader(phi: number): SkShader {
  'worklet';
  const colors = [];
  for (let i = 0; i < 13; i++) colors.push(col(FACE_RGB[i * 3], FACE_RGB[i * 3 + 1], FACE_RGB[i * 3 + 2], 1));
  return Skia.Shader.MakeSweepGradient(0, 0, colors, FACE_STOPS, TileMode.Clamp, Skia.Matrix().rotate(phi));
}

export function markPaths() {
  'worklet';
  const arch = Skia.PathBuilder.Make();
  arch.moveTo(ARCH_POINTS[0], ARCH_POINTS[1]);
  for (let i = 2; i < ARCH_POINTS.length; i += 2) arch.lineTo(ARCH_POINTS[i], ARCH_POINTS[i + 1]);
  const bar = Skia.PathBuilder.Make();
  bar.moveTo(-0.4, 0.19);
  bar.lineTo(0.4, 0.19);
  return { arch: arch.detach(), bar: bar.detach() };
}

function strokeMark(c: SkCanvas, color: Float32Array, shader: SkShader | null) {
  'worklet';
  const paths = markPaths();
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setStyle(PaintStyle.Stroke);
  paint.setColor(color);
  if (shader) paint.setShader(shader);
  paint.setStrokeWidth(0.2);
  paint.setStrokeCap(StrokeCap.Butt);
  paint.setStrokeJoin(StrokeJoin.Round);
  c.drawPath(paths.arch, paint);
  paint.setStrokeWidth(0.12);
  c.drawPath(paths.bar, paint);
}

function streak(
  c: SkCanvas,
  line: SkPaint,
  glow: SkPaint,
  i: number,
  z: number,
  trail: number,
  forward: boolean,
  vpX: number,
  vpY: number,
  warp: number,
  twinkleT: number,
) {
  'worklet';
  const o = i * 11;
  const r = PARTICLES[o];
  const size = PARTICLES[o + 2];
  const tailZ = forward ? Math.min(1.04, z + trail) : Math.max(0.04, z - trail);
  const reach = FOCAL * r;
  const rho = reach / z;
  const rhoTail = reach / tailZ;
  if (Math.min(rho, rhoTail) > 560) return;
  const fade = smooth((1.04 - z) / 0.24);
  const twinkle = 0.78 + 0.22 * Math.sin(twinkleT * (3 + (i % 4)) + 6.28 * size);
  const alpha = Math.min(1, (0.35 + 0.65 * size) * fade * (0.8 + 0.45 * warp) * twinkle);
  if (alpha < 0.01) return;
  let width = Math.max(0.9, (0.5 + 1.4 * size) * (0.4 + 0.6 * (1.04 - z)));
  if (size > 0.94) width *= 2.4;
  const cs = PARTICLES[o + 3];
  const sn = PARTICLES[o + 4];
  const hx = vpX + cs * rho;
  const hy = vpY + sn * rho;
  const tx = vpX + cs * rhoTail;
  const ty = vpY + sn * rhoTail;
  const cr = PARTICLES[o + 5];
  const cg = PARTICLES[o + 6];
  const cb = PARTICLES[o + 7];
  const kr = PARTICLES[o + 8];
  const kg = PARTICLES[o + 9];
  const kb = PARTICLES[o + 10];
  const length = Math.abs(rho - rhoTail);
  if (length > 3) {
    // A soft halo and a core, both fading toward the tail (the vanishing point side).
    line.setColor(col(0, 0, 0, 1));
    line.setStrokeWidth(width * 2.6 + 1.2);
    line.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: tx, y: ty },
        { x: hx, y: hy },
        [col(cr, cg, cb, 0), col(cr, cg, cb, alpha * 0.16)],
        null,
        TileMode.Clamp,
      ),
    );
    c.drawLine(tx, ty, hx, hy, line);
    line.setStrokeWidth(width);
    line.setShader(
      Skia.Shader.MakeLinearGradient({ x: tx, y: ty }, { x: hx, y: hy }, [col(cr, cg, cb, 0), col(kr, kg, kb, alpha)], null, TileMode.Clamp),
    );
    c.drawLine(tx, ty, hx, hy, line);
    line.setShader(null);
  } else {
    if (size > 0.75) {
      glow.setColor(col(cr, cg, cb, alpha * 0.2));
      c.drawCircle(hx, hy, width * 2, glow);
    }
    line.setShader(null);
    line.setStrokeWidth(width);
    line.setColor(col(kr, kg, kb, alpha));
    c.drawLine(tx, ty, hx, hy, line);
  }
}

type Coin = {
  cx: number;
  cy: number;
  z: number;
  scale: number;
  /** Coin axes in camera space: e1 (face x), e2 (face y, down), e3 (face normal, toward the camera at rest). */
  e1: number[];
  e2: number[];
  e3: number[];
  opacity: number;
};

function rotate(vx: number, vy: number, vz: number, twist: number, spin: number, pitch: number, roll: number): number[] {
  'worklet';
  let x = vx;
  let y = vy;
  let z = vz;
  // Twist about the face normal (the z axis before the tilt).
  let nx = x * Math.cos(twist) - y * Math.sin(twist);
  y = x * Math.sin(twist) + y * Math.cos(twist);
  x = nx;
  // Spin about the face's vertical axis.
  nx = x * Math.cos(spin) + z * Math.sin(spin);
  z = -x * Math.sin(spin) + z * Math.cos(spin);
  x = nx;
  // Pitch: the top edge tips away from the camera.
  const ny = y * Math.cos(pitch) + z * Math.sin(pitch);
  z = -y * Math.sin(pitch) + z * Math.cos(pitch);
  y = ny;
  // Roll about the view axis.
  nx = x * Math.cos(roll) - y * Math.sin(roll);
  y = x * Math.sin(roll) + y * Math.cos(roll);
  x = nx;
  return [x, y, z];
}

function coinGeometry(s: Frame, vpX: number, vpY: number): Coin {
  'worklet';
  const p = s.pose;
  const w = (2 * Math.PI * s.phase) / LOOP;
  const y = p.y + s.hover * 3 * Math.sin(w);
  const roll = p.roll + s.hover * 0.03 * Math.sin(w + 1.3);
  const pitch = p.pitch + s.hover * 0.02 * Math.sin(2 * w);
  const z = Math.exp(p.lnZ);
  const scale = 1 / z;
  return {
    cx: vpX + p.x * scale,
    cy: vpY + y * scale - p.hop * scale,
    z,
    scale,
    e1: rotate(1, 0, 0, p.twist, p.spin, pitch, roll),
    e2: rotate(0, 1, 0, p.twist, p.spin, pitch, roll),
    e3: rotate(0, 0, -1, p.twist, p.spin, pitch, roll),
    opacity: 1 - 0.75 * smooth((z - 3) / 6),
  };
}

function drawFace(c: SkCanvas, phi: number, lx: number, ly: number, lambert: number, warp: number) {
  'worklet';
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  fill.setShader(faceShader(phi));
  c.drawCircle(0, 0, 1, fill);
  fill.setShader(null);
  const stroke = Skia.Paint();
  stroke.setAntiAlias(true);
  stroke.setStyle(PaintStyle.Stroke);
  // Lathe lines.
  stroke.setStrokeWidth(0.012);
  for (let j = 0; j < 10; j++) {
    stroke.setColor(j % 2 === 0 ? col(1, 1, 1, 0.06) : col(0, 0, 0, 0.05));
    c.drawCircle(0, 0, 0.12 + 0.085 * j, stroke);
  }
  // Raised lip and the groove inside it.
  stroke.setStrokeWidth(0.1);
  stroke.setColor(col(0, 0, 0, 1));
  stroke.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: lx * 0.95, y: ly * 0.95 },
      { x: -lx * 0.95, y: -ly * 0.95 },
      [col(1, 1, 1, 0.5), col(1, 1, 1, 0), col(0, 0, 0, 0.35)],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawCircle(0, 0, 0.95, stroke);
  stroke.setShader(null);
  stroke.setStrokeWidth(0.014);
  stroke.setColor(col(0, 0, 0, 0.3));
  c.drawCircle(0, 0, 0.888, stroke);
  stroke.setStrokeWidth(0.01);
  stroke.setColor(col(1, 1, 1, 0.35));
  c.drawCircle(0, 0, 0.872, stroke);

  // Embossed mark: a dark copy away from the light, a light copy toward it, then the mark itself.
  const d = 0.026;
  c.save();
  c.translate(-lx * d, -ly * d);
  strokeMark(c, hexCol(0x2a2d33, 0.6), null);
  c.restore();
  c.save();
  c.translate(lx * d, ly * d);
  strokeMark(c, col(1, 1, 1, 0.55), null);
  c.restore();
  strokeMark(c, col(0, 0, 0, 1), faceShader(phi + Math.PI / 2));

  // Shade by how much the face turns from the light, and pick up the warp's blue on the far edge.
  fill.setColor(col(0, 0, 0, 0.5 * (1 - lambert)));
  c.drawCircle(0, 0, 1, fill);
  if (warp > 0.01) {
    fill.setColor(col(0, 0, 0, 1));
    fill.setShader(
      Skia.Shader.MakeRadialGradient({ x: 0, y: -1 }, 1.3, [hexCol(0x6e86ff, 0.3 * warp), hexCol(0x6e86ff, 0)], null, TileMode.Clamp),
    );
    c.drawCircle(0, 0, 1, fill);
    fill.setShader(null);
  }
  stroke.setStrokeWidth(0.012);
  stroke.setColor(col(1, 1, 1, 0.5));
  c.drawCircle(0, 0, 0.995, stroke);
}

function drawCoin(c: SkCanvas, coin: Coin, warp: number) {
  'worklet';
  const k = RADIUS * coin.scale;
  const n = (THICKNESS / 2) * coin.scale;
  const e1 = coin.e1;
  const e2 = coin.e2;
  const e3 = coin.e3;
  const isFront = e3[2] < 0;
  const normal = isFront ? e3 : [-e3[0], -e3[1], -e3[2]];
  if (coin.opacity < 0.999) {
    const layer = Skia.Paint();
    layer.setAlphaf(coin.opacity);
    c.saveLayer(layer);
  } else {
    c.save();
  }

  // Soft ambient shadow under the coin, cut out of the streaks.
  const shadowPath = Skia.PathBuilder.Make();
  shadowPath.addOval(Skia.XYWHRect(-1, -1, 2, 2));
  shadowPath.transform([
    e1[0] * k * 1.1,
    e2[0] * k * 1.1,
    coin.cx - e3[0] * n * 2,
    e1[1] * k * 1.1,
    e2[1] * k * 1.1,
    coin.cy - e3[1] * n * 2 + 10 * coin.scale,
    0,
    0,
    1,
  ]);
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(col(0, 0, 0, 0.5));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, Math.max(0.5, 22 * coin.scale * 0.6), false));
  c.drawPath(shadowPath.detach(), shadow);

  // Reeded rim: one quad per facet on the side facing the camera, lit by the light and its specular.
  const ux = e1[0] * k;
  const uy = e1[1] * k;
  const vx = e2[0] * k;
  const vy = e2[1] * k;
  const nx = e3[0] * n;
  const ny = e3[1] * n;
  const step = (2 * Math.PI) / SEGMENTS;
  const fill = Skia.Paint();
  fill.setAntiAlias(true);
  const quad = Skia.PathBuilder.Make();
  for (let i = 0; i < SEGMENTS; i++) {
    const a0 = i * step;
    const mid = a0 + step / 2;
    const rx = Math.cos(mid) * e1[0] + Math.sin(mid) * e2[0];
    const ry = Math.cos(mid) * e1[1] + Math.sin(mid) * e2[1];
    const rz = Math.cos(mid) * e1[2] + Math.sin(mid) * e2[2];
    if (rz > 0.02) continue;
    const lambert = Math.max(0, dot3(rx, ry, rz, LIGHT));
    const spec = Math.pow(Math.max(0, dot3(rx, ry, rz, HALF)), 24);
    const ridge = i % 2 === 0 ? 1 : 0.74;
    const facing = Math.max(0, -rz);
    const g = Math.min(1, (0.4 + 0.42 * lambert + 0.22 * facing) * ridge * 0.92 + 0.55 * spec);
    const cool = 0.14 * warp * (1 - lambert);
    fill.setColor(col(Math.min(1, g * 0.98 + cool * 0.3), Math.min(1, g * 0.99 + cool * 0.45), Math.min(1, g * 1.02 + cool), 1));
    const b0 = a0 - step * 0.3;
    const b1 = a0 + step * 1.3;
    const p0x = coin.cx + Math.cos(b0) * ux + Math.sin(b0) * vx;
    const p0y = coin.cy + Math.cos(b0) * uy + Math.sin(b0) * vy;
    const p1x = coin.cx + Math.cos(b1) * ux + Math.sin(b1) * vx;
    const p1y = coin.cy + Math.cos(b1) * uy + Math.sin(b1) * vy;
    quad.reset();
    quad.moveTo(p0x + nx, p0y + ny);
    quad.lineTo(p1x + nx, p1y + ny);
    quad.lineTo(p1x - nx, p1y - ny);
    quad.lineTo(p0x - nx, p0y - ny);
    quad.close();
    c.drawPath(quad.detach(), fill);
  }

  // The face, drawn in its own unit-circle coordinates.
  const side = isFront ? 1 : -1;
  const f1 = [side * e1[0], side * e1[1], side * e1[2]];
  // Light and half vector projected on the face: the brushed highlight runs along the half vector.
  const phi = Math.atan2(dot3(e2[0], e2[1], e2[2], HALF), dot3(f1[0], f1[1], f1[2], HALF));
  let lx = dot3(f1[0], f1[1], f1[2], LIGHT);
  let ly = dot3(e2[0], e2[1], e2[2], LIGHT);
  const ll = Math.max(0.0001, Math.sqrt(lx * lx + ly * ly));
  lx /= ll;
  ly /= ll;
  const lambert = Math.max(0, dot3(normal[0], normal[1], normal[2], LIGHT));
  c.save();
  c.concat([f1[0] * k, e2[0] * k, coin.cx + side * nx, f1[1] * k, e2[1] * k, coin.cy + side * ny, 0, 0, 1]);
  drawFace(c, phi, lx, ly, lambert, warp);
  c.restore();
  c.restore();
}

export function drawScene(c: SkCanvas, height: number, s: Frame) {
  'worklet';
  const vpX = 196.5 + s.vanishShift;
  const vpY = vanishY(height);
  const warp = Math.min(1, Math.max(0, (Math.abs(s.speed) - REST_SPEED) / BOOST));
  const fill = Skia.Paint();
  fill.setAntiAlias(true);

  // Backdrop: near black with a deep blue light at the end of the tunnel that brightens with speed.
  fill.setColor(hexCol(0x05060b, 1));
  c.drawRect(Skia.XYWHRect(0, 0, 393, height), fill);
  fill.setShader(
    Skia.Shader.MakeRadialGradient({ x: vpX, y: vpY }, 260, [hexCol(0x1b2a8f, 0.1 + 0.14 * warp), hexCol(0x1b2a8f, 0)], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, 393, height), fill);
  fill.setShader(
    Skia.Shader.MakeRadialGradient({ x: vpX, y: vpY }, 140, [hexCol(0x2b3fd0, 0.03 + 0.05 * warp), hexCol(0x2b3fd0, 0)], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, 393, height), fill);
  fill.setShader(null);

  // Coin placement (weak perspective) and its depth in the tunnel.
  const coin = coinGeometry(s, vpX, vpY);
  const coinDepth = 0.115 * coin.z;
  const splits = coinDepth > 0.2;

  const line = Skia.Paint();
  line.setAntiAlias(true);
  line.setStyle(PaintStyle.Stroke);
  line.setStrokeCap(StrokeCap.Round);
  line.setBlendMode(BlendMode.Plus);
  const glow = Skia.Paint();
  glow.setAntiAlias(true);
  glow.setBlendMode(BlendMode.Plus);
  const front: number[] = [];
  const trail = Math.min(0.85, Math.max(0.006, Math.abs(s.speed) * 0.24));
  const forward = s.speed >= 0;
  const shift = s.travel - Math.floor(s.travel);
  const twinkleT = (2 * Math.PI * s.phase) / LOOP;
  for (let i = 0; i < COUNT; i++) {
    let u = PARTICLES[i * 11 + 1] + shift;
    u -= Math.floor(u);
    const z = 1.04 - u;
    if (splits && z < coinDepth) {
      front.push(i);
      continue;
    }
    streak(c, line, glow, i, z, trail, forward, vpX, vpY, warp, twinkleT);
  }

  // Scrims keep the headline and the buttons legible over the streaks.
  fill.setShader(
    Skia.Shader.MakeLinearGradient({ x: 0, y: 0 }, { x: 0, y: 300 }, [hexCol(0x05060b, 0.78), hexCol(0x05060b, 0)], null, TileMode.Clamp),
  );
  c.drawRect(Skia.XYWHRect(0, 0, 393, 300), fill);
  fill.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: 0, y: height - 230 },
      { x: 0, y: height },
      [hexCol(0x05060b, 0), hexCol(0x05060b, 0.7)],
      null,
      TileMode.Clamp,
    ),
  );
  c.drawRect(Skia.XYWHRect(0, height - 230, 393, 230), fill);
  fill.setShader(null);

  drawCoin(c, coin, warp);

  for (let j = 0; j < front.length; j++) {
    const i = front[j];
    let u = PARTICLES[i * 11 + 1] + shift;
    u -= Math.floor(u);
    streak(c, line, glow, i, 1.04 - u, trail, forward, vpX, vpY, warp, twinkleT);
  }

  if (s.glint > 0.01) {
    const g = s.glint;
    glow.setColor(col(0, 0, 0, 1));
    glow.setShader(
      Skia.Shader.MakeRadialGradient({ x: coin.cx, y: coin.cy }, 16, [col(1, 1, 1, 0.8 * g), hexCol(0x9fb2ff, 0)], null, TileMode.Clamp),
    );
    c.drawCircle(coin.cx, coin.cy, 16, glow);
    glow.setShader(null);
    glow.setColor(col(1, 1, 1, 0.9 * g));
    c.drawOval(Skia.XYWHRect(coin.cx - 34 * g, coin.cy - 1.2, 68 * g, 2.4), glow);
    c.drawOval(Skia.XYWHRect(coin.cx - 1.2, coin.cy - 22 * g, 2.4, 44 * g), glow);
  }
}
