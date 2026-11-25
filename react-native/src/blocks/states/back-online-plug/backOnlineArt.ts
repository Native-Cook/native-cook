import {
  BlendMode,
  BlurStyle,
  FilterMode,
  MipmapMode,
  PaintStyle,
  Skia,
  StrokeCap,
  TileMode,
} from '@shopify/react-native-skia';
import type { SkCanvas, SkFont, SkImage } from '@shopify/react-native-skia';

import { REST_DX, REST_DY } from './backOnlineMotion';
import type { Frame, RGB } from './backOnlineMotion';

/*
 Back Online Plug — drawing. Design space is 393 × 852 pt. On another screen the layout is centred
 (x + ox) and vertical positions scale by height / 852 (`fy`); the plug and the socket keep their
 sizes and move as one group anchored at the socket's centre.

 One camera for both objects: the socket plate faces us on the wall, the plug is seen from its round
 back. Blur radii of the reference are in pixels of its 3× canvas, hence the sigmas of r / 3 pt.
*/

export const PLUG = require('./assets/plug-back.webp');
export const SOCKET = require('./assets/socket-front.webp');
export const BARLOW = require('./assets/BarlowCondensed-ExtraBold.ttf');
export const INTER_MEDIUM = require('./assets/Inter-Medium.ttf');
export const INTER_SEMIBOLD = require('./assets/Inter-SemiBold.ttf');

export const TITLE_SIZE = 78;

/** The socket: plate centre and size; the plug face's diameter at rest depth 0. */
const SOCKET_X = 196.5;
const SOCKET_Y = 560;
const PLATE = 210;
const PLATE_H = (210 * 639) / 640;
const PLUG_FACE = 138;
/** plug-back.webp: 419 × 723 px, the face's centre at (209.5, 209.5), the cable 54 px wide at x 208.5. */
const PLUG_ASPECT = 723 / 419;
const CABLE_W = 54 / 419;
const CABLE_X = 208.5 / 419;

const TITLE_OFF = ['STILL', 'OFF', 'LINE.'];
const TITLE_ON = ['BACK', 'ON', 'LINE.'];
const TITLE_BASELINES = [150, 224, 298];
const TITLE_TRACKING = 4;
const LINE_OFF = 'Plug back in to sync 3 drafts.';
const LINE_ON = 'Your 3 drafts are syncing now.';

export type Fonts = {
  title: SkFont;
  line: SkFont;
  button: SkFont;
  chip: SkFont;
};

export type Layout = {
  /** Horizontal offset of the 393-pt design. */
  ox: number;
  /** Vertical scale of positions (height / 852). */
  fy: number;
  /** The socket's centre on screen. */
  sx: number;
  sy: number;
  height: number;
};

export function makeLayout(width: number, height: number): Layout {
  const fy = height / 852;
  const ox = (width - 393) / 2;
  return { ox, fy, sx: ox + SOCKET_X, sy: SOCKET_Y * fy, height };
}

export function rgbColor(c: RGB, k: number, alpha = 1) {
  'worklet';
  const r = Math.min(255, Math.round(c[0] * k));
  const g = Math.min(255, Math.round(c[1] * k));
  const b = Math.min(255, Math.round(c[2] * k));
  return Skia.Color(`rgba(${r},${g},${b},${alpha})`);
}

/** The plug's face centre and its face diameter on screen. */
export function plugGeometry(L: Layout, frame: Frame): [number, number, number] {
  'worklet';
  const s = 1 + 0.42 * Math.max(frame.z, 0) + 0.3 * Math.min(frame.z, 0);
  const cx = L.sx + REST_DX * frame.a + 4 * frame.sway;
  const cy = L.sy + REST_DY * frame.a + 2 * frame.sway;
  return [cx, cy, PLUG_FACE * s];
}

/** Wall: a radial gradient centred on the socket, radius 620 (from 20), wall colour ×1.18 → ×0.78. */
export function drawWall(canvas: SkCanvas, L: Layout, wall: RGB, width: number, height: number) {
  'worklet';
  const paint = Skia.Paint();
  paint.setShader(
    Skia.Shader.MakeRadialGradient(
      { x: L.sx, y: L.sy },
      620,
      [rgbColor(wall, 1.18), rgbColor(wall, 0.78)],
      [20 / 620, 1],
      TileMode.Clamp,
    ),
  );
  canvas.drawRect(Skia.XYWHRect(0, 0, width, height), paint);
}

/** The plate's soft shadow on the wall, then the plate. */
export function drawSocket(canvas: SkCanvas, L: Layout, socket: SkImage | null) {
  'worklet';
  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color('rgba(0,0,0,0.28)'));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, 10 / 3, true));
  const x = L.sx - PLATE / 2;
  const y = L.sy - PLATE / 2;
  canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(x + 6, y + 10, PLATE, PLATE), 18, 18), shadow);
  if (!socket) return;
  canvas.drawImageRectOptions(
    socket,
    Skia.XYWHRect(0, 0, socket.width(), socket.height()),
    Skia.XYWHRect(x, y, PLATE, PLATE_H),
    FilterMode.Linear,
    MipmapMode.Linear,
    null,
  );
}

/**
 The plug: its shadow (wide and soft while it is out toward us, tight once seated), the cable from
 the image's bottom edge to the screen's bottom, then the image.
*/
export function drawPlug(canvas: SkCanvas, L: Layout, plug: SkImage | null, frame: Frame) {
  'worklet';
  const [cx, cy, face] = plugGeometry(L, frame);
  const h = face * PLUG_ASPECT;
  const top = cy - face / 2;
  const depth = Math.max(frame.z, 0);

  const shadow = Skia.Paint();
  shadow.setAntiAlias(true);
  shadow.setColor(Skia.Color(`rgba(0,0,0,${0.45 - 0.2 * depth})`));
  shadow.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, (3 + 16 * depth) / 3, true));
  canvas.drawCircle(cx + 4 + 18 * depth, cy + 6 + 28 * depth, (face / 2) * 0.98, shadow);

  const cw = face * CABLE_W;
  const ccx = cx - face / 2 + face * CABLE_X;
  const cable = Skia.Paint();
  cable.setShader(
    Skia.Shader.MakeLinearGradient(
      { x: ccx - cw / 2, y: 0 },
      { x: ccx + cw / 2, y: 0 },
      [Skia.Color('#D9D9D7'), Skia.Color('#F4F4F2'), Skia.Color('#BDBDBB')],
      [0, 0.35, 1],
      TileMode.Clamp,
    ),
  );
  const cableTop = top + h - 4;
  if (cableTop < L.height) canvas.drawRect(Skia.XYWHRect(ccx - cw / 2, cableTop, cw, L.height - cableTop), cable);

  if (!plug) return;
  canvas.drawImageRectOptions(
    plug,
    Skia.XYWHRect(0, 0, plug.width(), plug.height()),
    Skia.XYWHRect(cx - face / 2, top, face, h),
    FilterMode.Linear,
    MipmapMode.Linear,
    null,
  );
}

/** Ring spark around the plug's rim as it seats: an expanding ring and ten rays, added to the light. */
export function drawSpark(canvas: SkCanvas, L: Layout, frame: Frame) {
  'worklet';
  const sp = frame.spark;
  if (sp <= 0 || sp >= 1) return;
  const [cx, cy, face] = plugGeometry(L, frame);
  const ring = Skia.Paint();
  ring.setAntiAlias(true);
  ring.setBlendMode(BlendMode.Plus);
  ring.setStyle(PaintStyle.Stroke);
  ring.setStrokeWidth(3 * (1 - sp) + 0.5);
  ring.setColor(Skia.Color(`rgba(255,246,176,${0.9 * (1 - sp)})`));
  canvas.drawCircle(cx, cy, face / 2 + 4 + 22 * sp, ring);

  const ray = Skia.Paint();
  ray.setAntiAlias(true);
  ray.setBlendMode(BlendMode.Plus);
  ray.setStyle(PaintStyle.Stroke);
  ray.setStrokeWidth(2);
  ray.setStrokeCap(StrokeCap.Round);
  ray.setColor(Skia.Color(`rgba(255,246,176,${1 - sp})`));
  const r0 = face / 2 + 6 + 16 * sp;
  const r1 = r0 + 14 * (1 - sp) + 3;
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * 6.283 + 0.2;
    canvas.drawLine(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0, cx + Math.cos(a) * r1, cy + Math.sin(a) * r1, ray);
  }
}

/** Text with tracking, from glyph widths (no rounding per glyph). */
export function drawTracked(
  canvas: SkCanvas,
  font: SkFont,
  text: string,
  x: number,
  y: number,
  tracking: number,
  color: string,
) {
  'worklet';
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  const positions = [];
  let cx = 0;
  for (let i = 0; i < ids.length; i++) {
    positions.push({ x: cx, y: 0 });
    cx += widths[i] + tracking;
  }
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  paint.setColor(Skia.Color(color));
  canvas.drawGlyphs(ids, positions, x, y, font, paint);
}

export function textWidth(font: SkFont, text: string) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w;
}

/**
 The title flips line by line: each line's flip takes 40 % of the span, 0.3 apart. The old line
 squashes onto its baseline and fades, then the new one grows from it.
*/
export function drawTitle(canvas: SkCanvas, L: Layout, font: SkFont, flip: number) {
  'worklet';
  for (let i = 0; i < 3; i++) {
    const u = Math.min(1, Math.max(0, flip * 1.6 - i * 0.3));
    const showOn = u >= 0.5;
    const a = Math.abs(u - 0.5) * 2;
    if (a <= 0) continue;
    canvas.save();
    canvas.translate(L.ox + 30, TITLE_BASELINES[i] * L.fy);
    canvas.scale(1, Math.max(0.05, a));
    drawTracked(canvas, font, showOn ? TITLE_ON[i] : TITLE_OFF[i], 0, 0, TITLE_TRACKING, `rgba(255,255,255,${a})`);
    canvas.restore();
  }
}

/** The line under the title, then the Try again button or the Syncing chip (drawn over the cable). */
export function drawCopy(canvas: SkCanvas, L: Layout, fonts: Fonts, frame: Frame) {
  'worklet';
  const online = frame.flip >= 0.5;
  // The line's ink changes together with its text, at the title flip's midpoint.
  const color = online ? '#A6D7B6' : '#A9B5AE';
  drawTracked(canvas, fonts.line, online ? LINE_ON : LINE_OFF, L.ox + 32, 350 * L.fy, 0, color);

  const y = 772 * L.fy;
  const paint = Skia.Paint();
  paint.setAntiAlias(true);
  if (online) {
    paint.setColor(Skia.Color('rgba(255,255,255,0.14)'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + 32, y, 160, 44), 22, 22), paint);
    drawTracked(canvas, fonts.chip, `Syncing ${frame.chip} of 3`, L.ox + 54, y + 28, 0, '#FFFFFF');
  } else {
    paint.setColor(Skia.Color('#FFFFFF'));
    canvas.drawRRect(Skia.RRectXY(Skia.XYWHRect(L.ox + 32, y, 329, 58), 29, 29), paint);
    const label = 'Try again';
    const w = textWidth(fonts.button, label);
    drawTracked(canvas, fonts.button, label, L.ox + 196.5 - w / 2, y + 35, 0, '#1F3A2A');
  }
}

/** Is a point on the plug's face (with 16 pt of slack)? */
export function plugHit(L: Layout, frame: Frame, x: number, y: number) {
  'worklet';
  const [cx, cy, face] = plugGeometry(L, frame);
  return Math.hypot(x - cx, y - cy) <= face / 2 + 16;
}

export function buttonHit(L: Layout, x: number, y: number) {
  'worklet';
  const top = 772 * L.fy;
  return x >= L.ox + 32 && x <= L.ox + 361 && y >= top - 6 && y <= top + 64;
}
