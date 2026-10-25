/*
 Drawer Cabinet Sheet — layout, the items, and the art drawn once:
 - the desk: warm paper, a green felt mat with stitching, a notebook and a mug;
 - the drawer (the sheet): an oak front with a brass bar pull, and behind it the box — birch floor,
   oak walls, walnut dividers, a navy felt pen tray, inner shadows — in one image;
 - the cabinet's top board the drawer slides out from under;
 - an atlas of the supplies (pens, a marker, a pencil, paper clips, binder clips, an eraser, tape,
   sticky notes) and one of their soft shadows.
*/
import { BlurStyle, ClipOp, PaintStyle, Skia, StrokeCap, TileMode, matchFont, type SkCanvas, type SkImage, type SkPaint } from '@shopify/react-native-skia';
import { Platform } from 'react-native';

import type { ItemSpring } from './drawerMotion';

export const INK = { ink: '#2A1E14', soft: 'rgba(42,30,20,0.62)', faint: 'rgba(42,30,20,0.42)', paper: '#EFE7DA' };

/** Front panel depth, interior depth, wall width (pt). */
export const PANEL = 60;
export const DEPTH = 384;
export const WALL = 10;
export const SHADOW_PAD = 30;

type Kind = 'pen' | 'marker' | 'pencil' | 'clip' | 'binder' | 'eraser' | 'tape' | 'notes';
export type Item = { kind: Kind; name: string; w: number; h: number; x: number; y: number; rot: number; color: string; spring: ItemSpring };

const PEN: ItemSpring = { slack: 9, bounce: 0.3, w: 14, z: 0.35, turn: 0.04 };
const CLIP: ItemSpring = { slack: 14, bounce: 0.5, w: 10, z: 0.25, turn: 0.25 };
const BINDER: ItemSpring = { slack: 10, bounce: 0.35, w: 12, z: 0.3, turn: 0.15 };
const HEAVY: ItemSpring = { slack: 7, bounce: 0.15, w: 17, z: 0.55, turn: 0.04 };
const TAPE: ItemSpring = { slack: 12, bounce: 0.25, w: 11, z: 0.3, turn: 0.03 };

/** The supplies at their places, in interior coordinates (x from the left wall, y from the front panel). */
export function makeItems(Wi: number): Item[] {
  const pw = Math.min(250, Wi - 70);
  const cx = Wi / 2;
  const L = Wi * 0.52;
  const clips: Item[] = [
    [0.18, 152, 0.4, '#B8BEC4'], [0.42, 146, -0.7, '#F07C67'], [0.28, 182, 1.2, '#B8BEC4'],
    [0.62, 176, 0.1, '#6CC4A8'], [0.2, 218, -0.3, '#F2C14E'], [0.55, 222, 0.9, '#B8BEC4'],
  ].map(([fx, y, r, c]) => ({ kind: 'clip' as Kind, name: 'Paper clip', w: 30, h: 11, x: L * (fx as number), y: y as number, rot: r as number, color: c as string, spring: CLIP }));
  return [
    { kind: 'pen', name: 'Blue fineliner', w: pw, h: 15, x: cx - 6, y: 30, rot: -0.02, color: '#2F5BD8', spring: PEN },
    { kind: 'pen', name: 'Black gel pen', w: pw - 14, h: 15, x: cx + 4, y: 60, rot: 0.025, color: '#22252B', spring: PEN },
    { kind: 'marker', name: 'Coral marker', w: pw - 40, h: 19, x: cx - 10, y: 91, rot: -0.015, color: '#E8573F', spring: PEN },
    ...clips,
    { kind: 'eraser', name: 'Eraser', w: 58, h: 26, x: Wi * 0.66, y: 156, rot: 0.18, color: '#F3A6B2', spring: HEAVY },
    { kind: 'binder', name: 'Binder clip', w: 28, h: 24, x: Wi * 0.87, y: 160, rot: -0.35, color: '#2A2C31', spring: BINDER },
    { kind: 'binder', name: 'Binder clip', w: 28, h: 24, x: Wi * 0.9, y: 204, rot: 0.5, color: '#2A2C31', spring: BINDER },
    { kind: 'pencil', name: 'Pencil', w: Math.min(124, Wi * 0.4), h: 11, x: Wi * 0.71, y: 222, rot: -0.06, color: '#F2B632', spring: PEN },
    { kind: 'tape', name: 'Tape', w: 76, h: 76, x: Wi * 0.25, y: 315, rot: 0, color: '#E9C9A0', spring: TAPE },
    { kind: 'notes', name: 'Sticky notes', w: 88, h: 88, x: Wi * 0.7, y: 314, rot: 0.07, color: '#F9DC6A', spring: HEAVY },
  ];
}

export type Layout = {
  W: number;
  H: number;
  top: number;
  boardY: number;
  half: number;
  full: number;
  sheetX: number;
  sheetW: number;
  /** The interior's left edge on screen and its width. */
  ix: number;
  iw: number;
  matY: number;
};

export function makeLayout(W: number, H: number, top: number, bottom: number): Layout {
  const boardY = H - bottom - 64;
  const full = Math.max(220, Math.min(DEPTH, boardY - PANEL - (top + 236)));
  const sheetX = 12;
  const sheetW = W - 24;
  return { W, H, top, boardY, half: Math.round(full * 0.6), full, sheetX, sheetW, ix: sheetX + WALL, iw: sheetW - WALL * 2, matY: top + 104 };
}

function paint(color: string, alpha = 1): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(Skia.Color(color));
  if (alpha < 1) p.setAlphaf(alpha);
  return p;
}

function linear(x0: number, y0: number, x1: number, y1: number, colors: string[], pos: number[] | null): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: x0, y: y0 }, { x: x1, y: y1 }, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function radial(x: number, y: number, r: number, colors: string[], pos: number[] | null): SkPaint {
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeRadialGradient({ x, y }, r, colors.map((c) => Skia.Color(c)), pos, TileMode.Clamp));
  return p;
}

function blur(p: SkPaint, sigma: number) {
  p.setMaskFilter(Skia.MaskFilter.MakeBlur(BlurStyle.Normal, sigma, true));
  return p;
}

function stroke(p: SkPaint, w: number) {
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(w);
  p.setStrokeCap(StrokeCap.Round);
  return p;
}

function rr(x: number, y: number, w: number, h: number, r: number) {
  return Skia.RRectXY(Skia.XYWHRect(x, y, w, h), r, r);
}

function hash(n: number) {
  const v = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return v - Math.floor(v);
}

function snapshot(w: number, h: number, px: number, draw: (c: SkCanvas) => void): SkImage | null {
  const W = Math.ceil(w * px);
  const H = Math.ceil(h * px);
  const surface = Skia.Surface.MakeOffscreen(W, H) ?? Skia.Surface.Make(W, H);
  if (!surface) return null;
  const c = surface.getCanvas();
  c.scale(px, px);
  draw(c);
  surface.flush();
  const img = surface.makeImageSnapshot();
  return img.makeNonTextureImage() ?? img;
}

/** Wood: a three-stop base and long, slightly wavy grain lines. */
function wood(c: SkCanvas, x: number, y: number, w: number, h: number, base: string[], grain: string, seed: number, along: 'x' | 'y' = 'x') {
  c.drawRect(Skia.XYWHRect(x, y, w, h), along === 'x' ? linear(0, y, 0, y + h, base, [0, 0.5, 1]) : linear(x, 0, x + w, 0, base, [0, 0.5, 1]));
  const n = Math.round((along === 'x' ? h : w) / 2.2);
  for (let i = 0; i < n; i++) {
    const b = Skia.PathBuilder.Make();
    const o = (i + hash(i + seed) * 0.8) * 2.2;
    const amp = 0.6 + hash(i * 3 + seed) * 1.6;
    const len = along === 'x' ? w : h;
    for (let s = 0; s <= len; s += 12) {
      const d = o + Math.sin(s * 0.02 + i * 0.7 + seed) * amp;
      if (along === 'x') {
        if (s === 0) b.moveTo(x + s, y + d); else b.lineTo(x + s, y + d);
      } else if (s === 0) b.moveTo(x + d, y + s);
      else b.lineTo(x + d, y + s);
    }
    c.drawPath(b.build(), stroke(paint(grain, 0.05 + hash(i * 7 + seed) * 0.12), 0.6));
  }
}

/** The desk, the mat and what lies on it, the size of the screen. */
export function bakeDesk(L: Layout, px: number) {
  const { W, H, matY } = L;
  return snapshot(W, H, px, (c) => {
    c.drawRect(Skia.XYWHRect(0, 0, W, H), linear(0, 0, 0, H, ['#F3ECE1', '#EDE3D4', '#E4D7C4'], [0, 0.5, 1]));
    for (let i = 0; i < 4000; i++) c.drawRect(Skia.XYWHRect(hash(i * 1.3) * W, hash(i * 2.9) * H, 0.7, 0.7), paint(hash(i * 5.1) > 0.5 ? '#FFFFFF' : '#6B5238', 0.05));
    const mx = 16;
    const mw = W - 32;
    const mh = L.boardY - PANEL - 14 - matY;
    c.drawRRect(rr(mx, matY + 10, mw, mh, 22), blur(paint('#3B2A18', 0.22), 14));
    c.drawRRect(rr(mx, matY + 1, mw, mh, 22), blur(paint('#3B2A18', 0.3), 1.5));
    c.drawRRect(rr(mx, matY, mw, mh, 22), linear(0, matY, 0, matY + mh, ['#3E6152', '#2F5042', '#27443A'], [0, 0.5, 1]));
    for (let i = 0; i < 9000; i++) c.drawRect(Skia.XYWHRect(mx + hash(i * 1.7) * mw, matY + hash(i * 2.3) * mh, 0.8, 0.8), paint(hash(i * 3.3) > 0.5 ? '#9FC2B1' : '#13261F', 0.09));
    const stitch = stroke(paint('#B9D3C4', 0.5), 1);
    stitch.setPathEffect(Skia.PathEffect.MakeDash([5, 4], 0));
    c.drawRRect(rr(mx + 8, matY + 8, mw - 16, mh - 16, 16), stitch);
    const label = matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: 10, fontWeight: 'bold' });
    c.drawText('DESK', mx + 20, matY + 28, paint('#CFE3D8', 0.55), label);
    // A notebook (cover, elastic band, page edges) low on the mat, a mug of coffee beside it.
    // A clear ruler along the top, a to-do card and a succulent: the desk is in use.
    const ry = matY + 36;
    c.drawRRect(rr(mx + 40, ry + 4, 210, 26, 4), blur(paint('#0B1712', 0.25), 4));
    c.drawRRect(rr(mx + 40, ry, 210, 26, 4), paint('#DDEFF2', 0.32));
    c.drawRRect(rr(mx + 40, ry, 210, 26, 4), stroke(paint('#FFFFFF', 0.55), 0.8));
    for (let i = 0; i <= 40; i++) c.drawLine(mx + 46 + i * 5, ry, mx + 46 + i * 5, ry + (i % 5 === 0 ? 9 : 5), stroke(paint('#14261F', 0.55), 0.6));
    const cy0 = matY + 196;
    c.save();
    c.rotate(3, mx + 100, cy0 + 46);
    c.drawRect(Skia.XYWHRect(mx + 32, cy0 + 6, 136, 94), blur(paint('#0B1712', 0.35), 6));
    c.drawRect(Skia.XYWHRect(mx + 30, cy0, 136, 94), linear(0, cy0, 0, cy0 + 94, ['#FBF7EE', '#F3ECDD'], null));
    c.drawRect(Skia.XYWHRect(mx + 30, cy0 + 22, 136, 1), paint('#E07A6A', 0.6));
    c.drawText('TODAY', mx + 40, cy0 + 16, paint('#7A6A58'), label);
    for (let i = 0; i < 3; i++) {
      const ly = cy0 + 40 + i * 18;
      c.drawRect(Skia.XYWHRect(mx + 40, ly - 6, 8, 8), stroke(paint('#5A6E9A', 0.8), 1));
      c.drawPath(Skia.PathBuilder.Make().moveTo(mx + 56, ly).quadTo(mx + 80 + i * 6, ly - 4, mx + 104 + i * 10, ly).quadTo(mx + 120, ly + 2, mx + 136 - i * 8, ly - 1).build(), stroke(paint('#3B5BA8', 0.7), 1.2));
    }
    c.restore();
    const px0 = W - 74;
    const py0 = cy0 + 40;
    c.drawCircle(px0 + 4, py0 + 7, 32, blur(paint('#0B1712', 0.4), 8));
    c.drawCircle(px0, py0, 30, linear(px0 - 30, 0, px0 + 30, 0, ['#B9643C', '#D98457', '#9A4E2C'], [0, 0.4, 1]));
    c.drawCircle(px0, py0, 25, paint('#4A3426'));
    for (let i = 0; i < 9; i++) {
      const a = (i / 9) * Math.PI * 2;
      const lx = px0 + Math.cos(a) * 11;
      const ly = py0 + Math.sin(a) * 11;
      c.save();
      c.rotate((a * 180) / Math.PI, lx, ly);
      c.drawOval(Skia.XYWHRect(lx - 11, ly - 5.5, 22, 11), linear(lx - 11, 0, lx + 11, 0, ['#5E9C6E', '#8CCB94', '#4C8A5C'], [0, 0.6, 1]));
      c.restore();
    }
    c.drawCircle(px0, py0, 6, radial(px0, py0, 6, ['#B6E3B4', '#6FAF7A'], null));
    const nx = mx + 26;
    const ny = matY + mh - 236;
    c.save();
    c.rotate(-4, nx + 80, ny + 100);
    c.drawRRect(rr(nx + 4, ny + 10, 164, 214, 10), blur(paint('#0B1712', 0.45), 10));
    c.drawRRect(rr(nx + 2, ny + 3, 164, 212, 9), paint('#F4EEE2'));
    c.drawRRect(rr(nx, ny, 164, 212, 9), linear(nx, ny, nx + 164, ny + 212, ['#2D3E63', '#22314F', '#1A2640'], [0, 0.5, 1]));
    c.drawRect(Skia.XYWHRect(nx + 132, ny, 8, 212), linear(nx + 132, 0, nx + 140, 0, ['#9B2F2A', '#C9453C', '#8A2925'], [0, 0.5, 1]));
    c.drawText('IDEAS · 2026', nx + 16, ny + 32, paint('#D9C9A3', 0.8), label);
    c.restore();
    const gx = W - 92;
    const gy = ny + 70;
    c.drawCircle(gx + 4, gy + 8, 36, blur(paint('#0B1712', 0.4), 9));
    c.drawCircle(gx, gy, 34, linear(gx - 34, 0, gx + 34, 0, ['#E9E4DA', '#FFFFFF', '#CFC7B9'], [0, 0.4, 1]));
    c.drawCircle(gx, gy, 27, radial(gx - 6, gy - 6, 30, ['#7A4A2A', '#4B2A16', '#2E190D'], [0, 0.6, 1]));
    c.drawOval(Skia.XYWHRect(gx - 14, gy - 16, 14, 7), blur(paint('#FFFFFF', 0.35), 2));
    c.drawRRect(rr(gx + 32, gy - 6, 18, 12, 6), stroke(paint('#E2DBCF'), 5));
  });
}

/** The drawer: front panel and box, with room above for its shadow on the desk. */
export function bakeSheet(L: Layout, px: number) {
  const w = L.sheetW;
  const h = SHADOW_PAD + PANEL + DEPTH;
  return snapshot(w, h, px, (c) => {
    const y0 = SHADOW_PAD;
    c.drawRRect(rr(4, y0 - 10, w - 8, PANEL + 30, 14), blur(paint('#3B2A18', 0.3), 12));
    // The box: walls, birch floor, the navy felt pen tray, walnut dividers, inner shadows.
    const iy = y0 + PANEL;
    const iw = w - WALL * 2;
    wood(c, 0, iy, w, DEPTH, ['#A87B4D', '#94693F', '#7E5733'], '#3E2612', 3);
    wood(c, WALL, iy, iw, DEPTH, ['#EAD7B6', '#E2CCA6', '#D8BF95'], '#9A7448', 7);
    c.drawRRect(rr(WALL + 6, iy + 4, iw - 12, 110, 8), linear(0, iy, 0, iy + 114, ['#2B3452', '#323C5E', '#283050'], [0, 0.5, 1]));
    for (let i = 0; i < 2200; i++) c.drawRect(Skia.XYWHRect(WALL + 6 + hash(i * 1.1) * (iw - 12), iy + 4 + hash(i * 2.7) * 110, 0.8, 0.8), paint(hash(i * 3.9) > 0.5 ? '#8C97C2' : '#141A2E', 0.12));
    const div = (x: number, y: number, dw: number, dh: number) => {
      c.drawRect(Skia.XYWHRect(x, y + 3, dw, dh), blur(paint('#3A240F', 0.35), 3));
      c.drawRect(Skia.XYWHRect(x, y, dw, dh), dw > dh ? linear(0, y, 0, y + dh, ['#8A5A33', '#6B4426', '#4E301A'], [0, 0.4, 1]) : linear(x, 0, x + dw, 0, ['#8A5A33', '#6B4426', '#4E301A'], [0, 0.4, 1]));
      c.drawRect(Skia.XYWHRect(x, y, dw, 1), paint('#C79A6A', 0.7));
    };
    div(WALL, iy + 118, iw, 10);
    div(WALL, iy + 246, iw, 10);
    div(WALL + iw * 0.52, iy + 128, 10, 118);
    div(WALL + iw * 0.5 + 6, iy + 256, 10, 118);
    div(0, iy + DEPTH - 10, w, 10);
    // Shade along the walls and under the front panel's lip.
    c.drawRect(Skia.XYWHRect(WALL, iy, 14, DEPTH), linear(WALL, 0, WALL + 14, 0, ['rgba(60,36,14,0.35)', 'rgba(60,36,14,0)'], null));
    c.drawRect(Skia.XYWHRect(w - WALL - 14, iy, 14, DEPTH), linear(w - WALL - 14, 0, w - WALL, 0, ['rgba(60,36,14,0)', 'rgba(60,36,14,0.35)'], null));
    c.drawRect(Skia.XYWHRect(0, iy, w, 22), linear(0, iy, 0, iy + 22, ['rgba(40,24,10,0.45)', 'rgba(40,24,10,0)'], null));
    // The front: an oak plank with a bevelled top edge, a brass label holder and the bar pull.
    c.save();
    c.clipRRect(rr(0, y0, w, PANEL, 12), ClipOp.Intersect, true);
    wood(c, 0, y0, w, PANEL, ['#C48F5A', '#B07B48', '#93633A'], '#4A2C12', 11);
    c.restore();
    c.drawRRect(rr(0.5, y0 + 0.5, w - 1, PANEL - 1, 12), stroke(paint('#F1C894', 0.5), 1));
    c.drawRect(Skia.XYWHRect(8, y0 + PANEL - 3, w - 16, 3), paint('#5A3618', 0.35));
    const cx = w / 2;
    const py = y0 + PANEL / 2;
    c.drawRRect(rr(cx - 66, py + 3, 132, 16, 8), blur(paint('#3A2108', 0.5), 4));
    for (const sx of [cx - 56, cx + 56]) c.drawCircle(sx, py, 6.5, radial(sx - 2, py - 2, 8, ['#FFF0C2', '#D7A85A', '#8A5E22'], [0, 0.5, 1]));
    c.drawRRect(rr(cx - 64, py - 6, 128, 12, 6), linear(0, py - 6, 0, py + 6, ['#FBE3A2', '#E0B25E', '#B4843A', '#7E561C'], [0, 0.35, 0.7, 1]));
    c.drawRRect(rr(cx - 58, py - 4.5, 116, 2, 1), paint('#FFFFFF', 0.55));
    const lx = 26;
    c.drawRRect(rr(lx, py - 11, 70, 22, 4), linear(0, py - 11, 0, py + 11, ['#F2D08A', '#B8893E'], null));
    c.drawRect(Skia.XYWHRect(lx + 3, py - 8, 64, 16), paint('#F7F1E4'));
    const f = matchFont({ fontFamily: Platform.select({ ios: 'Menlo', default: 'monospace' }), fontSize: 8, fontWeight: 'bold' });
    c.drawText('SUPPLIES', lx + 13, py + 3, paint('#4A3A2A'), f);
  });
}

/** The cabinet's top board, from its edge to the bottom of the screen. */
export function bakeBoard(L: Layout, px: number) {
  const w = L.W;
  const h = L.H - L.boardY + 20;
  return snapshot(w, h, px, (c) => {
    c.drawRect(Skia.XYWHRect(0, 0, w, 20), linear(0, 0, 0, 20, ['rgba(40,22,8,0)', 'rgba(40,22,8,0.28)'], null));
    wood(c, 0, 20, w, h - 20, ['#B98552', '#A47142', '#8A5C34'], '#4A2C12', 23);
    c.drawRect(Skia.XYWHRect(0, 20, w, 2.5), paint('#2A1708'));
    c.drawRect(Skia.XYWHRect(0, 22.5, w, 1.2), paint('#F3CF9A', 0.8));
    c.drawRect(Skia.XYWHRect(0, 23.7, w, 10), linear(0, 23.7, 0, 34, ['rgba(255,226,180,0.25)', 'rgba(255,226,180,0)'], null));
  });
}

/** Where each item's sprite sits in the atlas (pt, before px), packed in rows. */
export function atlasCells(items: Item[], width: number) {
  const pad = 12;
  const cells: { x: number; y: number; w: number; h: number }[] = [];
  let x = 0;
  let y = 0;
  let row = 0;
  for (const it of items) {
    const cw = it.w + pad * 2;
    const chh = it.h + pad * 2;
    if (x + cw > width) {
      x = 0;
      y += row;
      row = 0;
    }
    cells.push({ x, y, w: cw, h: chh });
    x += cw;
    row = Math.max(row, chh);
  }
  return { cells, height: y + row, pad };
}

function drawItem(c: SkCanvas, it: Item, x: number, y: number) {
  const { w, h } = it;
  switch (it.kind) {
    case 'pen':
    case 'marker': {
      const cap = it.kind === 'marker' ? w * 0.3 : w * 0.26;
      c.drawRRect(rr(x, y, w, h, h / 2), linear(0, y, 0, y + h, ['#FFFFFF', '#E9E6E0', '#BDB7AD'], [0, 0.35, 1]));
      c.drawRRect(rr(x + w - cap, y - 0.5, cap, h + 1, h / 2), linear(0, y, 0, y + h, [it.color, it.color, '#0E0E14'], [0, 0.45, 1]));
      c.drawRect(Skia.XYWHRect(x + w - cap - 10, y + h * 0.18, 26, 2.4), paint('#C9CED4'));
      c.drawRect(Skia.XYWHRect(x + w - cap * 0.8, y + h * 0.5 - 1.2, cap * 0.75, 2.4), paint('#FFFFFF', 0.25));
      const tip = Skia.PathBuilder.Make().moveTo(x + 2, y + h / 2).lineTo(x + 14, y + 1.5).lineTo(x + 14, y + h - 1.5).close().build();
      c.drawPath(tip, linear(0, y, 0, y + h, ['#5A5A60', '#2A2A30'], null));
      c.drawRect(Skia.XYWHRect(x + 14, y + h * 0.5 - 0.8, w * 0.5, 1.6), paint(it.color, 0.55));
      c.drawRect(Skia.XYWHRect(x + h / 2, y + 2.5, w - h, 2), paint('#FFFFFF', 0.7));
      break;
    }
    case 'pencil': {
      c.drawRect(Skia.XYWHRect(x + 14, y, w - 30, h), linear(0, y, 0, y + h, ['#FFD66E', '#F2B632', '#C98A16'], [0, 0.5, 1]));
      c.drawRect(Skia.XYWHRect(x + 14, y + h / 3, w - 30, 0.8), paint('#9A6A10', 0.5));
      const cone = Skia.PathBuilder.Make().moveTo(x, y + h / 2).lineTo(x + 14, y).lineTo(x + 14, y + h).close().build();
      c.drawPath(cone, paint('#E8C9A0'));
      c.drawPath(Skia.PathBuilder.Make().moveTo(x, y + h / 2).lineTo(x + 5, y + h * 0.32).lineTo(x + 5, y + h * 0.68).close().build(), paint('#3A3A40'));
      c.drawRect(Skia.XYWHRect(x + w - 16, y, 7, h), linear(0, y, 0, y + h, ['#E6E8EA', '#A8ADB2'], null));
      c.drawRRect(rr(x + w - 10, y, 10, h, 3), linear(0, y, 0, y + h, ['#F7B5BE', '#D97E8C'], null));
      break;
    }
    case 'clip': {
      const p = Skia.PathBuilder.Make();
      const r1 = h / 2;
      p.moveTo(x + w - 6, y + h - 2.5);
      p.lineTo(x + r1, y + h - 2.5);
      p.arcToTangent(x + 1, y + h - 2.5, x + 1, y + h / 2, r1 - 1.5);
      p.arcToTangent(x + 1, y + 1, x + r1, y + 1, r1 - 1.5);
      p.lineTo(x + w - r1, y + 1);
      p.arcToTangent(x + w - 1, y + 1, x + w - 1, y + h / 2, r1 - 1.5);
      p.arcToTangent(x + w - 1, y + h - 1, x + w - r1, y + h - 1, r1 - 1.5);
      p.lineTo(x + 7, y + h - 1);
      c.drawPath(p.build(), stroke(linear(0, y, 0, y + h, ['#FFFFFF', it.color, '#5E646A'], [0, 0.45, 1]), 1.6));
      break;
    }
    case 'binder': {
      c.drawPath(Skia.PathBuilder.Make().moveTo(x + 2, y + h).lineTo(x + w - 2, y + h).lineTo(x + w - 6, y + h * 0.45).lineTo(x + 6, y + h * 0.45).close().build(), linear(0, y + h * 0.45, 0, y + h, ['#4A4C52', '#1E2024'], null));
      c.drawRect(Skia.XYWHRect(x + 6, y + h * 0.45, w - 12, 1), paint('#8A8E96'));
      c.drawPath(Skia.PathBuilder.Make().moveTo(x + 8, y + h * 0.5).lineTo(x + 6, y + 1).lineTo(x + w - 6, y + 1).lineTo(x + w - 8, y + h * 0.5).build(), stroke(paint('#D6DADE'), 1.6));
      break;
    }
    case 'eraser': {
      c.drawRRect(rr(x, y, w, h, 5), linear(0, y, 0, y + h, ['#FFD6DC', it.color, '#D9808E'], [0, 0.5, 1]));
      c.drawRect(Skia.XYWHRect(x + w * 0.38, y, w * 0.62, h), linear(0, y, 0, y + h, ['#FFFFFF', '#E8EEF4', '#B9C4CF'], [0, 0.5, 1]));
      c.drawRect(Skia.XYWHRect(x + w * 0.38, y, 1.2, h), paint('#8E9AA6', 0.6));
      const f = matchFont({ fontFamily: Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' }), fontSize: 8, fontWeight: 'bold' });
      c.drawText('SOFT', x + w * 0.5, y + h / 2 + 3, paint('#2F5BD8'), f);
      break;
    }
    case 'tape': {
      const cx = x + w / 2;
      const cy = y + h / 2;
      c.drawCircle(cx, cy, w / 2, radial(cx - 8, cy - 8, w * 0.6, ['#FBE8CC', '#E9C9A0', '#C69A68'], [0, 0.6, 1]));
      for (let i = 0; i < 9; i++) c.drawCircle(cx, cy, w / 2 - 3 - i * 2.2, stroke(paint('#FFFFFF', 0.08 + (i % 2) * 0.06), 0.6));
      c.drawCircle(cx, cy, w * 0.3, linear(cx - 20, 0, cx + 20, 0, ['#B48A5C', '#D7B184', '#A47B4E'], [0, 0.5, 1]));
      c.drawCircle(cx, cy, w * 0.25, paint('#E2CCA6'));
      c.drawCircle(cx, cy, w * 0.25, stroke(paint('#6E4C2A', 0.35), 1.2));
      break;
    }
    case 'notes': {
      for (let k = 3; k >= 1; k--) c.drawRect(Skia.XYWHRect(x + k * 0.8, y + k * 1.2, w, h), paint(k % 2 ? '#E3C24E' : '#EFD25E'));
      c.drawRect(Skia.XYWHRect(x, y, w, h), linear(x, y, x + w, y + h, ['#FFEB8A', it.color, '#EFCB4A'], [0, 0.5, 1]));
      const pen = stroke(paint('#3B5BA8', 0.75), 1.3);
      c.drawPath(Skia.PathBuilder.Make().moveTo(x + 12, y + 26).quadTo(x + 30, y + 18, x + 44, y + 26).quadTo(x + 58, y + 32, x + 72, y + 24).build(), pen);
      c.drawPath(Skia.PathBuilder.Make().moveTo(x + 12, y + 44).quadTo(x + 26, y + 38, x + 40, y + 45).build(), pen);
      c.drawPath(Skia.PathBuilder.Make().moveTo(x + w - 18, y + h).lineTo(x + w, y + h - 18).lineTo(x + w, y + h).close().build(), paint('#C9A63A', 0.6));
      break;
    }
  }
}

/** The supplies' sprites, and their shadows (same cells, blurred silhouettes). */
export function bakeItems(items: Item[], px: number, width: number) {
  const { cells, height, pad } = atlasCells(items, width);
  const faces = snapshot(width, height, px, (c) => items.forEach((it, i) => drawItem(c, it, cells[i].x + pad, cells[i].y + pad)));
  const shadows = snapshot(width, height, px, (c) => {
    items.forEach((it, i) => {
      const x = cells[i].x + pad;
      const y = cells[i].y + pad;
      const r = it.kind === 'tape' ? it.w / 2 : Math.min(it.h / 2, 6);
      c.drawRRect(rr(x, y, it.w, it.h, r), blur(paint('#2A1806', it.kind === 'clip' ? 0.3 : 0.42), it.kind === 'clip' ? 1.5 : 3));
    });
  });
  return { faces, shadows, cells };
}
