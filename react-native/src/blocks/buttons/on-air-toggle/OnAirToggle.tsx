import { BlendMode, Canvas, ClipOp, PaintStyle, Picture, Skia, StrokeCap, TileMode, matchFont, type SkCanvas, type SkColor, type SkFont, type SkRRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { AppState, Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// The "Studio" screen of an invented podcast app, Murmur. One large machined-aluminium pill switch
// sits on a softly lit floor: a brushed rim around a deep recessed channel, and a knob that is a
// round red glass lens in a turned metal bezel. Off, the lens is dark smoked red and the room is
// white. Switched on, the knob slides across with weight (a spring that bumps the end stop), the
// lens flickers and ignites into a glowing ON AIR lamp with a red bloom, and the whole room fades
// to near-black so only the metal edges catch the light. A status line, an on-air timer and a
// level meter answer it. Everything is drawn in code with gradients; no images, no fonts.
//
// Layout (pt, any width; centred on x): switch 300 x 148 (radius 74) with an 18 pt side wall,
// centred at y = 48 % of the height; rim 16; channel 268 x 116 (radius 58); knob bezel 104
// (6 pt gap, travel 152); lens 68. Three floor contour lines 56 / 128 / 214 pt outside the body.
//
// autoPlay timeline (LOOP = 6 s), pos 0 = knob left / off, 1 = right / on:
//   0.00-0.80  rest, off (light room)
//   0.80-0.92  anticipation: knob is pressed (press 0 -> 1) and nudges 3 % left (ease-out quad)
//   0.92-1.70  spring slide -0.03 -> 1 (damping ratio 0.72, response 0.5 s: ~4 % bump at the stop)
//   0.92-1.12  press releases (ease-out quad)
//   1.10-1.70  the lamp ignites: flicker keys 0 / .55 / .15 / .75 / .40 then ease-out to 1
//   1.15-1.85  the room fades light -> dark (ease-in-out cubic)
//   1.70-4.00  on air: lamp breathes +-5 % (5 cycles per loop), meter dances, timer counts from 1.30
//   3.90-4.00  anticipation: pressed, nudges 3 % right
//   4.00-4.78  spring slide 1.03 -> 0
//   4.10-4.50  the lamp dies (ease-in-out cubic); 4.15-4.85 the room fades back to light
//   4.85-6.00  rest, identical to t = 0
// Tap toggles; dragging moves the knob (rubber band past the ends) and previews the light, release
// snaps to the nearer side with a light haptic; 2 s after it settles the loop resumes from that side.
// Reduced motion: no loop, no slide, no flicker, no breathing; a tap cross-fades the knob, lamp and
// room to the other state in 0.3 s.

/** Seconds. */
export const LOOP = 6;

// Switch geometry (pt).
const TW = 300;
const TH = 148;
const R = TH / 2;
const DEPTH = 18;
const RIM = 16;
const CW = TW - 2 * RIM;
const CH = TH - 2 * RIM;
const CR = CH / 2;
const GAP = 6;
const KNOB = CH - 2 * GAP;
const KR = KNOB / 2;
const KNOB_SIDE = 7;
const TRAVEL = CW - 2 * GAP - KNOB;
const LENS = 34;
const CONTOURS = [56, 128, 214];
const BARS = 32;
const BAR_W = 3;
const BAR_GAP = 4;
const BAR_MAX = 30;
const CENTER_Y = 0.48;

// Gesture timing (s).
const HOLD = 2;
const SETTLE = 0.93;
const FADE = 0.3;

type RGB = [number, number, number];
const INK_LIGHT: RGB = [0x1d, 0x1a, 0x1a];
const INK_DARK: RGB = [0xf3, 0xee, 0xea];
const ROOM_LIGHT: RGB = [0xf4, 0xf1, 0xed];
const ROOM_DARK: RGB = [0x0e, 0x0a, 0x0b];
const RED: RGB = [0xff, 0x4b, 0x3a];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function seg(t: number, a: number, b: number) {
  'worklet';
  return clamp01((t - a) / (b - a));
}
function easeOutQuad(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x);
}
function easeOutCubic(x: number) {
  'worklet';
  return 1 - Math.pow(1 - x, 3);
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function mixN(a: number, b: number, u: number) {
  'worklet';
  return a + (b - a) * u;
}
function hash(i: number) {
  'worklet';
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}
/** RGB(A) 0-255 + alpha 0-1 -> Skia colour. */
function col(r: number, g: number, b: number, a = 1): SkColor {
  'worklet';
  return Float32Array.of(r / 255, g / 255, b / 255, a);
}
/** Mix two hex colours (light room -> dark room) by u, with alpha. */
function mixHex(a: number, b: number, u: number, alpha = 1): SkColor {
  'worklet';
  const r = ((a >> 16) & 255) * (1 - u) + ((b >> 16) & 255) * u;
  const g = ((a >> 8) & 255) * (1 - u) + ((b >> 8) & 255) * u;
  const bl = (a & 255) * (1 - u) + (b & 255) * u;
  return col(r, g, bl, alpha);
}
/** Text ink follows the room but flips quickly around the middle, so it never sits grey on grey. */
function inkMix(room: number) {
  'worklet';
  const u = clamp01((room - 0.4) / 0.2);
  return u * u * (3 - 2 * u);
}
function mixRGB(a: RGB, b: RGB, u: number) {
  'worklet';
  return `rgb(${Math.round(mixN(a[0], b[0], u))}, ${Math.round(mixN(a[1], b[1], u))}, ${Math.round(mixN(a[2], b[2], u))})`;
}

/** Unit step response of a spring (damping ratio 0.72, response 0.5 s), forced to exactly 1 by 0.78 s. */
function springUnit(s: number) {
  'worklet';
  if (s <= 0) return 0;
  const zeta = 0.72;
  const w = (2 * Math.PI) / 0.5;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  const dev = Math.exp(-zeta * w * s) * (Math.cos(wd * s) + ((zeta * w) / wd) * Math.sin(wd * s));
  const tail = 1 - easeInOutCubic(seg(s, 0.6, 0.78));
  return 1 - dev * tail;
}

/** The lamp's ignition, 0 -> 1 over 0.6 s with two flickers. */
function ignite(u: number) {
  'worklet';
  if (u <= 0) return 0;
  if (u < 0.06) return 0.55 * (u / 0.06);
  if (u < 0.12) return mixN(0.55, 0.15, (u - 0.06) / 0.06);
  if (u < 0.18) return mixN(0.15, 0.75, (u - 0.12) / 0.06);
  if (u < 0.24) return mixN(0.75, 0.4, (u - 0.18) / 0.06);
  return mixN(0.4, 1, easeOutCubic(seg(u, 0.24, 0.6)));
}

type State = { pos: number; glow: number; room: number; press: number; timer: number };

/** Everything the switch shows, `t` s into the loop. */
function loopAt(t: number): State {
  'worklet';
  let pos = 0;
  if (t >= 0.8 && t < 0.92) pos = -0.03 * easeOutQuad(seg(t, 0.8, 0.92));
  else if (t >= 0.92 && t < 3.9) pos = -0.03 + 1.03 * springUnit(t - 0.92);
  else if (t >= 3.9 && t < 4.0) pos = 1 + 0.03 * easeOutQuad(seg(t, 3.9, 4.0));
  else if (t >= 4.0) pos = 1.03 - 1.03 * springUnit(t - 4.0);
  const pressA = easeOutQuad(seg(t, 0.8, 0.92)) * (1 - easeOutQuad(seg(t, 0.92, 1.12)));
  const pressB = easeOutQuad(seg(t, 3.9, 4.0)) * (1 - easeOutQuad(seg(t, 4.0, 4.2)));
  const breathe = seg(t, 1.7, 2.0) * (1 - seg(t, 3.7, 4.0));
  const pulse = 1 + 0.05 * breathe * Math.sin((2 * Math.PI * 5 * t) / LOOP);
  const glow = ignite(t - 1.1) * (1 - easeInOutCubic(seg(t, 4.1, 4.5))) * pulse;
  const room = easeInOutCubic(seg(t, 1.15, 1.85)) * (1 - easeInOutCubic(seg(t, 4.15, 4.85)));
  const timer = room > 0.5 ? Math.max(0, t - 1.3) : 0;
  return { pos, glow, room, press: pressA + pressB, timer };
}

/** Level (0-1) of meter bar i at time T. Periodic in LOOP. */
function barLevel(i: number, T: number) {
  'worklet';
  const h = hash(i + 1);
  const env = 0.35 + 0.65 * Math.sin((Math.PI * (i + 0.5)) / BARS);
  const v =
    0.5 +
    0.22 * Math.sin(2 * Math.PI * ((7 * T) / LOOP + h)) +
    0.16 * Math.sin(2 * Math.PI * ((11 * T) / LOOP + 2.3 * h + 0.21 * i)) +
    0.12 * Math.sin(2 * Math.PI * ((17 * T) / LOOP + 0.37 * i));
  return clamp01(v) * env;
}

function rrect(x: number, y: number, w: number, h: number, r: number): SkRRect {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}
function fillPaint() {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  return p;
}
function strokePaint(width: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  return p;
}
function vGrad(y0: number, y1: number, colors: SkColor[], pos: number[] | null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient({ x: 0, y: y0 }, { x: 0, y: y1 }, colors, pos, TileMode.Clamp);
}
function hGrad(x0: number, x1: number, colors: SkColor[], pos: number[] | null) {
  'worklet';
  return Skia.Shader.MakeLinearGradient({ x: x0, y: 0 }, { x: x1, y: 0 }, colors, pos, TileMode.Clamp);
}
function radial(x: number, y: number, r: number, colors: SkColor[], pos: number[] | null) {
  'worklet';
  return Skia.Shader.MakeRadialGradient({ x, y }, r, colors, pos, TileMode.Clamp);
}
/** Five-stop gradient colours mixed between the light-room and dark-room materials. */
function stops(light: number[], dark: number[], u: number, alpha = 1) {
  'worklet';
  const out: SkColor[] = [];
  for (let i = 0; i < light.length; i++) out.push(mixHex(light[i], dark[i], u, alpha));
  return out;
}
function textWidth(font: SkFont, text: string, tracking: number) {
  'worklet';
  const widths = font.getGlyphWidths(font.getGlyphIDs(text));
  let w = 0;
  for (let i = 0; i < widths.length; i++) w += widths[i];
  return w + tracking * (text.length - 1);
}
function drawTracked(c: SkCanvas, font: SkFont, text: string, cx: number, baseline: number, tracking: number, color: SkColor, blur = 0) {
  'worklet';
  const ids = font.getGlyphIDs(text);
  const widths = font.getGlyphWidths(ids);
  let x = cx - textWidth(font, text, tracking) / 2;
  const p = fillPaint();
  p.setColor(color);
  if (blur > 0) p.setMaskFilter(Skia.MaskFilter.MakeBlur(1, blur, true));
  for (let i = 0; i < text.length; i++) {
    c.drawText(text[i], x, baseline, p, font);
    x += widths[i] + tracking;
  }
}
function pad2(n: number) {
  'worklet';
  return n < 10 ? `0${n}` : `${n}`;
}
function timerText(sec: number) {
  'worklet';
  const cs = Math.floor(sec * 100);
  const m = Math.floor(cs / 6000);
  const s = Math.floor(cs / 100) % 60;
  return `${pad2(m)}:${pad2(s)}.${pad2(cs % 100)}`;
}

type Fonts = { lens: SkFont; timer: SkFont };

/** The knob (bezel + lens) at x, drawn with opacity `alpha`. */
function drawKnob(c: SkCanvas, kx: number, cy: number, glow: number, room: number, press: number, alpha: number, fonts: Fonts) {
  'worklet';
  const g = clamp01(glow);
  const side = KNOB_SIDE * (1 - 0.55 * press);
  const ky = cy - 3 + 3 * press;
  if (alpha < 1) {
    const layer = Skia.Paint();
    layer.setAlphaf(alpha);
    c.saveLayer(layer);
  } else {
    c.save();
  }

  // Shadow on the channel floor.
  const sh = fillPaint();
  sh.setColor(col(0, 0, 0, 0.55 - 0.15 * press));
  sh.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 9 - 3 * press, true));
  c.drawOval(Skia.XYWHRect(kx - KR + 6, ky - KR + 14 - 4 * press, KNOB, KNOB - 8), sh);

  // Cylinder side of the bezel.
  const sidePaint = fillPaint();
  sidePaint.setShader(hGrad(kx - KR, kx + KR, stops([0x7c7e81, 0xc9cbcd, 0x8a8c8f], [0x141213, 0x3a3335, 0x161415], room), [0, 0.45, 1]));
  c.drawCircle(kx, ky + side, KR, sidePaint);

  // Turned face: anisotropic sweep of light and dark bands.
  const face = fillPaint();
  face.setShader(
    Skia.Shader.MakeSweepGradient(
      kx,
      ky,
      stops(
        [0xf4f4f2, 0xa3a5a8, 0xedeeee, 0x8d8f92, 0xf4f4f2, 0xa9abae, 0xe3e4e5, 0x909295, 0xf4f4f2],
        [0x5a5254, 0x262223, 0x4b4446, 0x1e1b1c, 0x5a5254, 0x2a2526, 0x463f41, 0x1f1c1d, 0x5a5254],
        room,
      ),
      null,
      TileMode.Clamp,
    ),
  );
  c.drawCircle(kx, ky, KR, face);
  const light = fillPaint();
  light.setShader(vGrad(ky - KR, ky + KR, [col(255, 255, 255, 0.35), col(255, 255, 255, 0), col(0, 0, 0, 0.18)], [0, 0.5, 1]));
  c.drawCircle(kx, ky, KR, light);
  const edge = strokePaint(1.5);
  edge.setShader(vGrad(ky - KR, ky, [col(255, 255, 255, 0.9 - 0.2 * room), col(255, 255, 255, 0)], null));
  c.drawCircle(kx, ky, KR - 0.75, edge);

  // The lamp tints its own bezel.
  if (g > 0.001) {
    const tint = fillPaint();
    tint.setBlendMode(BlendMode.Plus);
    tint.setShader(radial(kx, ky, KR, [col(255, 60, 40, 0), col(255, 60, 40, 0.55 * g), col(255, 60, 40, 0.08 * g)], [0.62, 0.7, 1]));
    c.drawCircle(kx, ky, KR, tint);
  }

  // Chamfer down to the lens well, then the well.
  const chamferDark = strokePaint(2);
  chamferDark.setColor(col(0, 0, 0, 0.28));
  c.drawCircle(kx, ky - 0.5, LENS + 5, chamferDark);
  const chamferLight = strokePaint(1.2);
  chamferLight.setColor(col(255, 255, 255, 0.55 - 0.35 * room));
  c.drawCircle(kx, ky + 1, LENS + 5.5, chamferLight);
  const well = fillPaint();
  well.setColor(col(18, 7, 8));
  c.drawCircle(kx, ky, LENS + 2, well);

  // Smoked red glass.
  const off = fillPaint();
  off.setShader(radial(kx - 6, ky - 8, LENS + 8, [mixHex(0x7a2224, 0x6a1a1c, room), mixHex(0x4e1013, 0x3e0b0e, room), mixHex(0x250709, 0x1a0405, room)], [0, 0.5, 1]));
  c.drawCircle(kx, ky, LENS, off);
  if (g > 0.001) {
    const on = fillPaint();
    on.setAlphaf(g);
    on.setShader(radial(kx, ky - 2, LENS + 2, [col(255, 138, 92), col(255, 59, 43), col(211, 15, 28), col(126, 7, 16)], [0, 0.42, 0.78, 1]));
    c.drawCircle(kx, ky, LENS, on);
  }
  // Fresnel rings.
  for (let i = 0; i < 4; i++) {
    const r = 9 + 7 * i;
    const ring = strokePaint(1);
    ring.setColor(col(255, 200, 190, 0.1 + 0.16 * g));
    c.drawCircle(kx, ky, r, ring);
    const ringDark = strokePaint(1);
    ringDark.setColor(col(40, 0, 4, 0.18));
    c.drawCircle(kx, ky, r + 1, ringDark);
  }
  // ON AIR, lit through the glass.
  if (g > 0.01) drawTracked(c, fonts.lens, 'ON AIR', kx, ky + 3.6, 1.4, col(255, 120, 90, 0.9 * g), 3);
  drawTracked(c, fonts.lens, 'ON AIR', kx, ky + 3.6, 1.4, col(mixN(255, 255, g), mixN(170, 246, g), mixN(160, 232, g), mixN(0.22, 0.96, g)));
  // Caustic at the bottom, dome highlight and glint at the top.
  const caustic = fillPaint();
  caustic.setBlendMode(BlendMode.Plus);
  caustic.setShader(radial(kx + 8, ky + 16, 18, [col(255, 120, 100, 0.28 + 0.3 * g), col(255, 120, 100, 0)], null));
  c.drawCircle(kx, ky, LENS, caustic);
  c.save();
  c.translate(kx - 9, ky - 15);
  c.rotate(-25, 0, 0);
  const dome = fillPaint();
  dome.setShader(vGrad(-8, 8, [col(255, 255, 255, 0.5 - 0.15 * g), col(255, 255, 255, 0)], null));
  c.drawOval(Skia.XYWHRect(-17, -8, 34, 16), dome);
  c.restore();
  const glint = fillPaint();
  glint.setColor(col(255, 255, 255, 0.85));
  c.drawCircle(kx - 15, ky - 15, 2.5, glint);
  c.restore();
}

/** The whole scene for one frame. */
function drawScene(c: SkCanvas, W: number, H: number, s: State, cross: number, T: number, reduced: boolean, fonts: Fonts) {
  'worklet';
  const cx = W / 2;
  const cy = H * CENTER_Y;
  const x0 = cx - TW / 2;
  const y0 = cy - TH / 2;
  const room = clamp01(s.room);
  const g = clamp01(s.glow);
  const kxAt = (p: number) => {
    'worklet';
    return cx - TRAVEL / 2 + TRAVEL * p;
  };
  const kx = kxAt(s.pos);

  // Room: a lit backdrop that fades to near-black.
  const bg = fillPaint();
  bg.setShader(radial(cx, cy - 80, Math.max(W, H) * 0.9, stops([0xfbfaf8, 0xeeebe6, 0xdcd7d0], [0x2a1a1c, 0x120c0e, 0x060405], room), [0, 0.55, 1]));
  c.drawRect(Skia.XYWHRect(0, 0, W, H), bg);
  if (g > 0.001) {
    const pool = fillPaint();
    pool.setShader(radial(kx, cy + 30, 300, [col(214, 28, 36, 0.3 * g * room), col(214, 28, 36, 0.1 * g * room), col(214, 28, 36, 0)], [0, 0.45, 1]));
    c.drawRect(Skia.XYWHRect(0, 0, W, H), pool);
  }

  // Floor contour lines around the body.
  for (let i = 0; i < CONTOURS.length; i++) {
    const o = CONTOURS[i];
    const line = strokePaint(1);
    line.setColor(room < 0.5 ? col(40, 32, 30, 0.2 * (1 - room * 2) + 0.0) : col(255, 235, 230, 0.15 * (room * 2 - 1)));
    c.drawRRect(rrect(x0 - o, y0 - o * 0.82, TW + 2 * o, TH + DEPTH + 2 * o * 0.82, R + o), line);
  }

  // Two shadows: soft ambient and tight contact.
  const ambient = fillPaint();
  ambient.setColor(col(40, 28, 26, 0.22 + 0.25 * room));
  ambient.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 22, true));
  c.drawRRect(rrect(x0 + 8, y0 + 22, TW - 16, TH + DEPTH, R), ambient);
  const contact = fillPaint();
  contact.setColor(col(20, 14, 12, 0.35));
  contact.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 3, true));
  c.drawRRect(rrect(x0 + 2, y0 + 3, TW - 4, TH + DEPTH, R), contact);

  // Side wall of the machined body.
  const body = rrect(x0, y0, TW, TH + DEPTH, R);
  const wall = fillPaint();
  wall.setShader(hGrad(x0, x0 + TW, stops([0x8d8f92, 0xc4c6c8, 0xe1e2e3, 0xb9bbbe, 0x808285], [0x121112, 0x2a2627, 0x3a3436, 0x2a2627, 0x121112], room), [0, 0.18, 0.5, 0.82, 1]));
  c.drawRRect(body, wall);
  const wallShade = fillPaint();
  wallShade.setShader(vGrad(y0 + TH * 0.5, y0 + TH + DEPTH, [col(0, 0, 0, 0), col(0, 0, 0, 0.3)], null));
  c.drawRRect(body, wallShade);

  // Top face (the rim), brushed and turned.
  const top = rrect(x0, y0, TW, TH, R);
  const chan = rrect(cx - CW / 2, cy - CH / 2, CW, CH, CR);
  const face = fillPaint();
  face.setShader(vGrad(y0, y0 + TH, stops([0xfdfdfc, 0xe3e4e5, 0xc3c5c8, 0xedeeef, 0xa6a8ab], [0x4a4446, 0x2b2728, 0x1a1718, 0x2e2829, 0x141213], room), [0, 0.3, 0.55, 0.8, 1]));
  c.drawRRect(top, face);
  const sheen = fillPaint();
  sheen.setAlphaf(1 - 0.5 * room);
  sheen.setShader(
    Skia.Shader.MakeSweepGradient(
      cx,
      cy,
      [col(255, 255, 255, 0), col(255, 255, 255, 0.35), col(255, 255, 255, 0), col(0, 0, 0, 0.12), col(255, 255, 255, 0), col(255, 255, 255, 0.3), col(255, 255, 255, 0), col(0, 0, 0, 0.12), col(255, 255, 255, 0)],
      null,
      TileMode.Clamp,
    ),
  );
  c.save();
  c.clipRRect(chan, ClipOp.Difference, true);
  c.drawRRect(top, sheen);
  c.restore();
  // Two lathe grooves.
  for (let i = 0; i < 2; i++) {
    const inset = 5 + 5 * i;
    const dark = strokePaint(1);
    dark.setColor(col(0, 0, 0, 0.16 + 0.2 * room));
    c.drawRRect(rrect(x0 + inset, y0 + inset, TW - 2 * inset, TH - 2 * inset, R - inset), dark);
    const lit = strokePaint(1);
    lit.setColor(col(255, 255, 255, 0.55 - 0.43 * room));
    c.drawRRect(rrect(x0 + inset, y0 + inset + 0.8, TW - 2 * inset, TH - 2 * inset, R - inset), lit);
  }
  // Edge highlight; in the dark the lamp rims the metal red.
  const hi = strokePaint(1.5);
  hi.setShader(vGrad(y0, y0 + TH * 0.5, [col(255, 255, 255, 0.95 - 0.25 * room), col(255, 255, 255, 0)], null));
  c.drawRRect(rrect(x0 + 0.75, y0 + 0.75, TW - 1.5, TH - 1.5, R - 0.75), hi);
  if (g > 0.001) {
    const rimLight = strokePaint(2);
    rimLight.setBlendMode(BlendMode.Plus);
    rimLight.setShader(radial(kx, cy, 210, [col(255, 70, 50, 0.85 * g * (0.4 + 0.6 * room)), col(255, 70, 50, 0)], null));
    c.drawRRect(rrect(x0 + 1, y0 + 1, TW - 2, TH - 2, R - 1), rimLight);
    // The side wall's lower edge picks up the same light, following its curve.
    c.save();
    c.clipRect(Skia.XYWHRect(x0 - 4, y0 + TH, TW + 8, DEPTH + 4), ClipOp.Intersect, true);
    c.drawRRect(rrect(x0 + 1, y0 + 1, TW - 2, TH + DEPTH - 2, R - 1), rimLight);
    c.restore();
  }

  // The recessed channel.
  const floor = fillPaint();
  floor.setShader(vGrad(cy - CH / 2, cy + CH / 2, stops([0x2e2f32, 0x3a3b3f, 0x5a5c60], [0x070607, 0x0d0b0c, 0x1f1a1b], room), [0, 0.5, 1]));
  c.drawRRect(chan, floor);
  c.save();
  c.clipRRect(chan, 1, true);
  const slot = strokePaint(4);
  slot.setStrokeCap(StrokeCap.Round);
  slot.setColor(col(0, 0, 0, 0.35));
  c.drawLine(cx - TRAVEL / 2, cy + 2, cx + TRAVEL / 2, cy + 2, slot);
  const slotLit = strokePaint(1);
  slotLit.setColor(col(255, 255, 255, 0.08));
  c.drawLine(cx - TRAVEL / 2, cy + 4.5, cx + TRAVEL / 2, cy + 4.5, slotLit);
  if (g > 0.001) {
    const spill = fillPaint();
    spill.setBlendMode(BlendMode.Plus);
    spill.setShader(radial(kx, cy, 120, [col(255, 50, 40, 0.38 * g), col(255, 50, 40, 0)], null));
    c.drawRRect(chan, spill);
  }
  // Knob(s): a cross-fade between the two sides under reduced motion.
  if (reduced) {
    if (cross < 0.999) drawKnob(c, kxAt(0), cy, 0, room, 0, 1 - cross, fonts);
    if (cross > 0.001) drawKnob(c, kxAt(1), cy, 1, room, 0, cross, fonts);
  } else {
    drawKnob(c, kx, cy, s.glow, room, s.press, 1, fonts);
  }
  const inner = strokePaint(14);
  inner.setColor(col(0, 0, 0, 0.6));
  inner.setMaskFilter(Skia.MaskFilter.MakeBlur(1, 6, true));
  c.drawRRect(rrect(cx - CW / 2 - 7, cy - CH / 2 - 7 + 5, CW + 14, CH + 14, CR + 7), inner);
  c.restore();
  const lip = strokePaint(1.2);
  lip.setShader(vGrad(cy - CH / 2, cy + CH / 2, [col(255, 255, 255, 0), col(255, 255, 255, 0), col(255, 255, 255, 0.5 - 0.3 * room)], [0, 0.55, 1]));
  c.drawRRect(rrect(cx - CW / 2 + 0.6, cy - CH / 2 + 0.6, CW - 1.2, CH - 1.2, CR - 0.6), lip);

  // Bloom: the lamp lights the air around it.
  if (g > 0.001) {
    const lx = reduced ? kxAt(cross) : kx;
    const ly = cy - 3;
    const bloom = fillPaint();
    bloom.setBlendMode(BlendMode.Plus);
    bloom.setShader(radial(lx, ly, 130, [col(255, 64, 48, 0.5), col(255, 40, 40, 0.2), col(255, 30, 30, 0.05), col(255, 30, 30, 0)], [0, 0.35, 0.7, 1]));
    bloom.setAlphaf(clamp01(s.glow * (0.35 + 0.65 * room)));
    c.drawCircle(lx, ly, 130, bloom);
    const halo = fillPaint();
    halo.setBlendMode(BlendMode.Plus);
    halo.setShader(radial(lx, ly, LENS + 16, [col(255, 140, 100, 0.55 * g), col(255, 120, 90, 0.35 * g), col(255, 100, 80, 0)], [0, 0.66, 1]));
    c.drawCircle(lx, ly, LENS + 16, halo);
  }

  // Supporting UI: on-air timer and level meter.
  const statusY = y0 + TH + DEPTH + 52;
  const ink = room < 0.5 ? INK_LIGHT : INK_DARK;
  const inkAlpha = room < 0.5 ? 1 - room * 2 : room * 2 - 1;
  const timerColor = col(mixN(ink[0], RED[0], room * g), mixN(ink[1], RED[1], room * g), mixN(ink[2], RED[2], room * g), (0.35 + 0.65 * room) * (0.4 + 0.6 * inkAlpha));
  drawTracked(c, fonts.timer, timerText(s.timer), cx, statusY + 52, 1, timerColor);
  const meterW = BARS * BAR_W + (BARS - 1) * BAR_GAP;
  const mx = cx - meterW / 2;
  const my = statusY + 104;
  for (let i = 0; i < BARS; i++) {
    const level = reduced ? barLevel(i, 0) : barLevel(i, T);
    const h = 3 + (BAR_MAX - 3) * level * room;
    const bar = fillPaint();
    bar.setColor(col(mixN(ink[0], RED[0], room), mixN(ink[1], RED[1], room), mixN(ink[2], RED[2], room), mixN(0.2, 0.55 + 0.45 * level, room) * (0.5 + 0.5 * inkAlpha)));
    c.drawRRect(rrect(mx + i * (BAR_W + BAR_GAP), my - h / 2, BAR_W, h, BAR_W / 2), bar);
  }
}

type Props = {
  /** Flips the switch by itself every LOOP seconds. */
  autoPlay?: boolean;
};

export function OnAirToggle({ autoPlay = false }: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [appActive, setAppActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setAppActive(state === 'active'));
    return () => sub.remove();
  }, []);
  const fonts = useMemo<Fonts>(
    () => ({
      lens: matchFont({ fontFamily: Platform.OS === 'ios' ? 'Helvetica' : 'sans-serif', fontSize: 10, fontWeight: '700' }),
      timer: matchFont({ fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace', fontSize: 30, fontWeight: '400' }),
    }),
    [],
  );

  const cy = height * CENTER_Y;
  const statusTop = cy + TH / 2 + DEPTH + 30;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const pos = useSharedValue(0);
  const glow = useSharedValue(0);
  const room = useSharedValue(0);
  const press = useSharedValue(0);
  const timer = useSharedValue(0);
  const meterT = useSharedValue(0);
  const cross = useSharedValue(0);
  const mode = useSharedValue(0); // 0 loop, 1 dragging, 2 settling after a touch
  const released = useSharedValue(0);
  const target = useSharedValue(0);
  const from = useSharedValue({ pos: 0, glow: 0, room: 0, cross: 0 });
  const onSince = useSharedValue(0);
  const timerOffset = useSharedValue(0);
  const grab = useSharedValue(0);
  const dragLit = useSharedValue(0); // the room was dark on the previous drag frame

  const frame = useFrameCallback((info) => {
    const dt = Math.min(0.05, (info.timeSincePreviousFrame ?? 0) / 1000);
    const now = elapsed.get() + dt;
    elapsed.set(now);
    meterT.set(now - origin.get());

    if (reduced) {
      // No loop: a tap cross-fades to the other side in FADE s.
      const u = mode.get() === 2 ? clamp01((now - released.get()) / FADE) : 1;
      const v = mixN(from.get().cross, target.get(), u);
      cross.set(v);
      pos.set(v);
      glow.set(v);
      room.set(v);
      press.set(0);
      timer.set(v > 0.5 ? now - onSince.get() : 0);
      return;
    }

    if (mode.get() === 1) {
      // Dragging: the light previews where the knob is.
      const k = Math.min(1, dt * 10);
      glow.set(mixN(glow.get(), easeInOutCubic(seg(pos.get(), 0.5, 0.95)), k));
      room.set(mixN(room.get(), easeInOutCubic(seg(pos.get(), 0.45, 1)), k));
      press.set(mixN(press.get(), 0.6, k));
      // Dragged from off into the dark: the clock starts now.
      const lit = room.get() > 0.5;
      if (lit && dragLit.get() === 0) onSince.set(now);
      dragLit.set(lit ? 1 : 0);
      timer.set(room.get() > 0.5 ? now - onSince.get() : 0);
      return;
    }

    if (mode.get() === 2) {
      const sTime = now - released.get();
      const f = from.get();
      const to = target.get();
      pos.set(f.pos + (to - f.pos) * springUnit(sTime));
      press.set(press.get() * Math.max(0, 1 - dt * 8));
      if (to === 1) {
        glow.set(f.glow + (1 - f.glow) * ignite(sTime - 0.18));
        room.set(f.room + (1 - f.room) * easeInOutCubic(seg(sTime, 0.23, 0.93)));
      } else {
        glow.set(f.glow * (1 - easeInOutCubic(seg(sTime, 0.1, 0.5))));
        room.set(f.room * (1 - easeInOutCubic(seg(sTime, 0.15, 0.85))));
      }
      timer.set(room.get() > 0.5 ? now - onSince.get() : 0);
      if (sTime > SETTLE + HOLD && autoPlay) {
        // Pick the loop up from the rest on this side.
        const resumeAt = to === 1 ? 1.85 : 4.85;
        origin.set(now - resumeAt);
        timerOffset.set(to === 1 ? timer.get() - (resumeAt - 1.3) : 0);
        mode.set(0);
      }
      return;
    }

    if (!autoPlay) {
      pos.set(0);
      glow.set(0);
      room.set(0);
      press.set(0);
      timer.set(0);
      return;
    }
    const s = loopAt((((now - origin.get()) % LOOP) + LOOP) % LOOP);
    if (s.room < 0.5) timerOffset.set(0);
    pos.set(s.pos);
    glow.set(s.glow);
    room.set(s.room);
    press.set(s.press);
    timer.set(s.timer > 0 ? s.timer + timerOffset.get() : 0);
  }, false);
  useEffect(() => {
    frame.setActive(appActive);
    return () => frame.setActive(false);
  }, [appActive, frame]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, height));
    drawScene(
      canvas,
      width,
      height,
      { pos: pos.get(), glow: glow.get(), room: room.get(), press: press.get(), timer: timer.get() },
      cross.get(),
      meterT.get(),
      reduced,
      fonts,
    );
    return recorder.finishRecordingAsPicture();
  }, [width, height, reduced, fonts]);

  const [dark, setDark] = useState(false);
  useAnimatedReaction(
    () => room.get() > 0.5,
    (now, prev) => {
      if (now !== prev) scheduleOnRN(setDark, now);
    },
  );

  const inkStyle = useAnimatedStyle(() => ({ color: mixRGB(INK_LIGHT, INK_DARK, inkMix(room.get())) }));
  const onStyle = useAnimatedStyle(() => ({ opacity: clamp01((room.get() - 0.5) * 3) }));
  const offStyle = useAnimatedStyle(() => ({ opacity: clamp01((0.5 - room.get()) * 3) }));
  const dotStyle = useAnimatedStyle(() => {
    const g = clamp01(glow.get());
    return {
      backgroundColor: mixRGB([0xb5, 0xae, 0xa8], RED, g),
      shadowOpacity: 0.9 * g,
    };
  });
  const cardStyle = useAnimatedStyle(() => ({
    backgroundColor: `rgba(255, 255, 255, ${mixN(0.55, 0.06, room.get())})`,
    borderColor: room.get() > 0.5 ? `rgba(255,255,255,${0.1 * room.get()})` : `rgba(29,26,26,${0.08 * (1 - room.get())})`,
  }));
  const screenStyle = useAnimatedStyle(() => ({ backgroundColor: mixRGB(ROOM_LIGHT, ROOM_DARK, room.get()) }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const settleTo = (to: number) => {
    'worklet';
    const now = elapsed.get();
    const wasOn = room.get() > 0.5;
    from.set({ pos: pos.get(), glow: glow.get(), room: room.get(), cross: cross.get() });
    target.set(to);
    released.set(now);
    if (to === 1 && !wasOn) onSince.set(now + (reduced ? 0 : 0.2));
    if (to === 1 && wasOn) onSince.set(now - timer.get());
    mode.set(2);
    scheduleOnRN(haptic);
  };
  const tap = Gesture.Tap().onEnd(() => {
    if (reduced) {
      settleTo(cross.get() > 0.5 ? 0 : 1);
      return;
    }
    settleTo(pos.get() > 0.5 ? 0 : 1);
  });
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(6)
    .onStart(() => {
      grab.set(pos.get());
      if (room.get() > 0.5) onSince.set(elapsed.get() - timer.get());
      dragLit.set(room.get() > 0.5 ? 1 : 0);
      mode.set(1);
    })
    .onUpdate((e) => {
      const raw = grab.get() + e.translationX / TRAVEL;
      // Rubber band past the ends.
      const over = raw > 1 ? raw - 1 : raw < 0 ? raw : 0;
      const band = over === 0 ? 0 : (Math.sign(over) * (Math.abs(over) * 0.35)) / (1 + Math.abs(over) * 3);
      pos.set(over === 0 ? raw : (raw > 1 ? 1 : 0) + band);
    })
    .onEnd(() => settleTo(pos.get() > 0.5 ? 1 : 0));

  return (
    <Animated.View style={[styles.screen, screenStyle]}>
      <StatusBar style={dark ? 'light' : 'dark'} />
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={picture} />
      </Canvas>

      <View style={[styles.head, { top: insets.top + 8 }]} pointerEvents="none">
        <View style={styles.row}>
          <Animated.Text style={[styles.brand, inkStyle]}>Murmur</Animated.Text>
          <Animated.Text style={[styles.meta, inkStyle]}>Studio A · 48 kHz</Animated.Text>
        </View>
        <Animated.Text style={[styles.overline, inkStyle]}>EPISODE 48 · TUESDAY</Animated.Text>
        <Animated.Text style={[styles.title, inkStyle]}>The Quiet Hours</Animated.Text>
        <Animated.Text style={[styles.sub, inkStyle]}>Ada Kole with two guests from the night shift</Animated.Text>
      </View>

      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <View
          style={[styles.hit, { left: width / 2 - TW / 2 - 12, top: cy - TH / 2 - 16, width: TW + 24, height: TH + DEPTH + 32 }]}
          accessible
          accessibilityRole="switch"
          accessibilityLabel="On air"
          accessibilityState={{ checked: dark }}
        />
      </GestureDetector>

      <View style={[styles.status, { top: statusTop }]} pointerEvents="none">
        <Animated.View style={[styles.dot, dotStyle]} />
        <View>
          <Animated.Text style={[styles.state, inkStyle, offStyle]}>Off air</Animated.Text>
          <Animated.Text style={[styles.state, styles.stateOver, inkStyle, onStyle]}>On air</Animated.Text>
        </View>
      </View>

      <View style={[styles.footer, { paddingBottom: insets.bottom + 14 }]} pointerEvents="none">
        <Animated.View style={[styles.card, cardStyle]}>
          <View>
            <Animated.Text style={[styles.cardTitle, inkStyle]}>Mic 1 · Ribbon</Animated.Text>
            <Animated.Text style={[styles.cardMeta, inkStyle]}>Input −12 dB</Animated.Text>
          </View>
          <View style={styles.cardRight}>
            <Animated.Text style={[styles.cardTitle, inkStyle]}>Guest line</Animated.Text>
            <Animated.Text style={[styles.cardMeta, inkStyle]}>2 waiting</Animated.Text>
          </View>
        </Animated.View>
        <Animated.Text style={[styles.foot, inkStyle]}>Tap the switch or slide the lamp to go live.</Animated.Text>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  head: { position: 'absolute', left: 24, right: 24 },
  row: { height: 32, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { fontSize: 20, fontWeight: '800', letterSpacing: -0.4 },
  meta: { fontSize: 13, fontWeight: '600', opacity: 0.6 },
  overline: { marginTop: 22, fontSize: 12, fontWeight: '700', letterSpacing: 2, opacity: 0.5 },
  title: { marginTop: 6, fontSize: 34, lineHeight: 40, fontWeight: '700', letterSpacing: -0.8 },
  sub: { marginTop: 4, fontSize: 15, lineHeight: 20, opacity: 0.62 },
  hit: { position: 'absolute' },
  status: { position: 'absolute', left: 0, right: 0, height: 24, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  dot: { width: 8, height: 8, borderRadius: 4, shadowColor: '#FF4B3A', shadowRadius: 6, shadowOffset: { width: 0, height: 0 } },
  state: { fontSize: 17, fontWeight: '600', letterSpacing: 0.2 },
  stateOver: { position: 'absolute', left: 0 },
  footer: { position: 'absolute', left: 20, right: 20, bottom: 0 },
  card: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 16, paddingHorizontal: 18, borderRadius: 22, borderWidth: StyleSheet.hairlineWidth },
  cardRight: { alignItems: 'flex-end' },
  cardTitle: { fontSize: 16, fontWeight: '700' },
  cardMeta: { marginTop: 3, fontSize: 14, opacity: 0.62 },
  foot: { marginTop: 12, textAlign: 'center', fontSize: 13, opacity: 0.5 },
});
