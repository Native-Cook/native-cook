import { BlendMode, ClipOp, PaintStyle, Skia, StrokeCap, StrokeJoin, type SkCanvas, type SkFont, TileMode } from '@shopify/react-native-skia';

import {
  BUILDINGS,
  type Box,
  CARS,
  COLS,
  HORIZON,
  LAMPS,
  LANE_Y,
  LOOP,
  type Layout,
  ROWS,
  type State,
  chevron,
  chipsY,
  clamp01,
  exposure,
  gallery,
  hash,
  isoAt,
  leftCapsule,
  leftToggle,
  rest,
  rightCapsule,
  rightToggle,
  shutterAt,
  shutterCenter,
  smoothstep,
  viewfinder,
} from './model';

/*
 Drawing for Dot Matrix Camera Pad (a port of DotMatrixCameraPadDraw in DotMatrixCameraPad.swift).
 Every function runs on the UI thread.
 */

function rgb(r: number, g: number, b: number, a = 1) {
  'worklet';
  const f = (x: number) => Math.min(1, Math.max(0, x));
  return Float32Array.of(f(r), f(g), f(b), f(a));
}

function fill(color: Float32Array) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(color);
  return p;
}

function stroke(color: Float32Array, width: number, round = true) {
  'worklet';
  const p = fill(color);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeWidth(width);
  if (round) {
    p.setStrokeCap(StrokeCap.Round);
    p.setStrokeJoin(StrokeJoin.Round);
  }
  return p;
}

function vertical(top: number, bottom: number, colors: Float32Array[], pos: number[] | null = null) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setShader(Skia.Shader.MakeLinearGradient({ x: 0, y: top }, { x: 0, y: bottom }, colors, pos, TileMode.Clamp));
  return p;
}

function rrect(b: Box, r: number) {
  'worklet';
  return Skia.RRectXY(Skia.XYWHRect(b.x, b.y, b.w, b.h), r, r);
}

function brackets(canvas: SkCanvas, r: Box) {
  'worklet';
  const i = 12;
  const a = 18;
  const p = Skia.Path.Make();
  const corners = [
    [r.x + i, r.y + i, 1, 1],
    [r.x + r.w - i, r.y + i, -1, 1],
    [r.x + i, r.y + r.h - i, 1, -1],
    [r.x + r.w - i, r.y + r.h - i, -1, -1],
  ];
  for (const [x, y, sx, sy] of corners) {
    p.moveTo(x, y + sy * a);
    p.lineTo(x, y);
    p.lineTo(x + sx * a, y);
  }
  canvas.drawPath(p, stroke(rgb(1, 1, 1, 0.85), 2));
}

/** The module's body: a rubber capsule, turning into dark glass as it becomes the pad. */
function capsule(canvas: SkCanvas, r: Box, corner: number, glass: number) {
  'worklet';
  const rr = rrect(r, corner);
  canvas.drawRRect(rr, vertical(r.y, r.y + r.h, [rgb(0.07, 0.07, 0.08), rgb(0.17, 0.17, 0.18), rgb(0.07, 0.07, 0.08)], [0, 0.5, 1]));
  if (glass > 0.01) {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: r.x, y: r.y },
        { x: r.x + r.w, y: r.y + r.h },
        [rgb(0.2, 0.17, 0.14, glass), rgb(0.06, 0.06, 0.07, glass)],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawRRect(rr, p);
  }
  canvas.drawRRect(rr, stroke(rgb(1, 1, 1, 0.08), 1, false));
}

/** Ridges on a drum: 8 pt apart along the cylinder, foreshortened and fading towards its rim. */
function drum(canvas: SkCanvas, r: Box, offset: number, amber: boolean, alpha: number) {
  'worklet';
  const radius = (r.h / 2) * 1.05;
  const base = Math.floor(offset);
  const midY = r.y + r.h / 2;
  for (let k = base - 9; k <= base + 9; k++) {
    const y = (k - offset) * 8;
    const theta = y / radius;
    if (Math.abs(theta) > 1.45) continue;
    const c = Math.cos(theta);
    const yy = midY + Math.sin(theta) * radius * 0.92;
    const major = k % 5 === 0;
    const inset = major ? 8 : 12;
    canvas.drawLine(
      r.x + inset,
      yy,
      r.x + r.w - inset,
      yy,
      stroke(rgb(1, 1, 1, alpha * (major ? 0.3 + 0.6 * c : 0.12 + 0.42 * c)), (major ? 1.6 : 1.2) * (0.4 + 0.6 * c)),
    );
  }
  const color = amber ? rgb(0.96, 0.75, 0.16, alpha) : rgb(1, 1, 1, 0.8 * alpha);
  canvas.drawLine(r.x + 7, midY, r.x + r.w - 7, midY, stroke(color, 2.2));
  // A sheen along the drum's top.
  canvas.drawRRect(rrect({ x: r.x + 3, y: r.y + 3, w: r.w - 6, h: r.h - 6 }, 12), vertical(r.y, midY, [rgb(1, 1, 1, 0.07 * alpha), rgb(1, 1, 1, 0)]));
}

/** The dot matrix: 11 columns of shutter speeds by 8 rows of ISO. */
function pad(canvas: SkCanvas, r: Box, s: State) {
  'worklet';
  const reveal = clamp01((s.morph - 0.5) * 2);
  const ix = r.x + 10;
  const iy = r.y + 10;
  const iw = r.w - 20;
  const ih = r.h - 20;
  const sx = iw / (COLS - 1);
  const sy = ih / (ROWS - 1);
  const hx = ix + s.px * iw;
  const hy = iy + ih - s.py * ih;
  const ci = Math.round(s.px * 10);
  const ri = Math.round(s.py * 7);
  const glow = s.glow;
  if (glow > 0.01) {
    const g = Skia.Paint();
    g.setShader(
      Skia.Shader.MakeRadialGradient({ x: hx, y: hy }, 46, [rgb(0.98, 0.45, 0.1, 0.45 * glow * reveal), rgb(0.98, 0.45, 0.1, 0)], null, TileMode.Clamp),
    );
    canvas.drawCircle(hx, hy, 46, g);
  }
  const dot = fill(rgb(1, 1, 1));
  for (let c = 0; c < COLS; c++) {
    // Columns ripple in from the left.
    const a = smoothstep(0, 1, reveal * 1.6 - (c / (COLS - 1)) * 0.6);
    if (a < 0.01) continue;
    for (let rr = 0; rr < ROWS; rr++) {
      const x = ix + c * sx;
      const y = iy + ih - rr * sy;
      const d2 = (x - hx) * (x - hx) + (y - hy) * (y - hy);
      const g = Math.exp(-d2 / (2 * 16 * 16)) * glow;
      const line = c === ci || rr === ri ? 0.16 + 0.3 * glow : 0;
      let radius = 1.3 + 2.6 * g + (line > 0 ? 0.3 : 0);
      let color = rgb(0.96, 0.6, 0.18, a * Math.min(1, 0.2 + line + 0.8 * g));
      if (c === ci && rr === ri) {
        radius = 3.2 + 0.7 * glow;
        color = rgb(1, 0.86, 0.58, a * (0.75 + 0.25 * glow));
      }
      dot.setColor(color);
      canvas.drawCircle(x, y, radius, dot);
    }
  }
  if (glow > 0.01) canvas.drawCircle(hx, hy, 15, stroke(rgb(1, 1, 1, 0.28 * glow * reveal), 1.5, false));
}

function toggle(canvas: SkCanvas, r: Box, knob: number, font: SkFont) {
  'worklet';
  canvas.drawRRect(rrect(r, 13), fill(rgb(0.11, 0.11, 0.12)));
  const k = clamp01(knob);
  const cx = r.x + r.w / 2;
  canvas.drawCircle(cx, r.y + 15 + 28 * k, 11, fill(rgb(0.94, 0.29, 0.07)));
  const letters = ['A', 'M'];
  for (let i = 0; i < 2; i++) {
    const on = i === 0 ? 1 - k : k;
    const width = font.measureText(letters[i]).width;
    // The caps' middle sits on the line: a cap is about 0.7 of the size tall.
    canvas.drawText(letters[i], cx - width / 2, r.y + 15 + 28 * i + font.getSize() * 0.36, fill(rgb(1, 1, 1, 0.5 + 0.5 * on)), font);
  }
}

function shutter(canvas: SkCanvas, c: { x: number; y: number }, press: number) {
  'worklet';
  const k = 1 - 0.08 * press;
  canvas.save();
  canvas.translate(c.x, c.y);
  canvas.scale(k, k);
  canvas.drawRRect(rrect({ x: -40.5, y: -30.5, w: 81, h: 61 }, 23), stroke(rgb(1, 1, 1), 3, false));
  const body = rrect({ x: -35, y: -25, w: 70, h: 50 }, 17);
  canvas.drawRRect(body, vertical(-25, 25, [rgb(1, 0.38, 0.14), rgb(0.88, 0.22, 0.05)]));
  canvas.drawRRect(rrect({ x: -27, y: -21, w: 54, h: 16 }, 8), vertical(-21, -5, [rgb(1, 1, 1, 0.28), rgb(1, 1, 1, 0)]));
  canvas.drawRRect(body, fill(rgb(0, 0, 0, 0.18 * press)));
  canvas.restore();
}

// MARK: The night street

function street(canvas: SkCanvas, r: Box, s: State, clock: number) {
  'worklet';
  const w = r.w;
  const h = r.h;
  const X = (u: number) => r.x + u * w;
  const Y = (v: number) => r.y + v * h;
  const R = (u: number, v: number, uw: number, vh: number) => Skia.XYWHRect(X(u), Y(v), uw * w, vh * h);
  const shutterS = shutterAt(s.px);
  const iso = isoAt(s.py);
  const full = R(-0.4, -0.4, 1.8, 1.8);
  const cx = r.x + w / 2;
  const cy = r.y + h / 2;

  canvas.save();
  canvas.translate(cx, cy);
  canvas.scale(s.zoom, s.zoom);
  canvas.translate(-cx, -cy);
  // Sky, a glow over the city.
  canvas.drawRect(full, vertical(Y(0), Y(HORIZON), [rgb(0.04, 0.05, 0.13), rgb(0.12, 0.08, 0.2), rgb(0.3, 0.14, 0.22)]));
  // Buildings with lit windows.
  const win = fill(rgb(1, 1, 1));
  for (let b = 0; b < BUILDINGS.length; b++) {
    const [x0, bw, bh] = BUILDINGS[b];
    const top = HORIZON - bh;
    canvas.drawRect(R(x0, top, bw, bh + 0.05), fill(rgb(0.05, 0.05, 0.1)));
    let i = 0;
    for (let wy = top + 0.03; wy < HORIZON - 0.03; wy += 0.046) {
      let j = 0;
      for (let wx = x0 + 0.018; wx < x0 + bw - 0.025; wx += 0.034) {
        const hsh = hash(b, i, j);
        if (hsh > 0.58) {
          const cool = hash(j, b, i) > 0.82;
          win.setColor(cool ? rgb(0.65, 0.82, 1, 0.75) : rgb(1, 0.76, 0.42, 0.55 + 0.4 * hsh));
          canvas.drawRect(R(wx, wy, 0.018, 0.022), win);
        }
        j += 1;
      }
      i += 1;
    }
  }
  // Pavement and road.
  canvas.drawRect(R(-0.4, HORIZON, 1.8, 0.8), fill(rgb(0.06, 0.06, 0.08)));
  canvas.drawRect(R(-0.4, HORIZON + 0.035, 1.8, 0.008), fill(rgb(0.18, 0.16, 0.2)));
  const dashP = fill(rgb(0.55, 0.52, 0.45, 0.45));
  for (let dash = -0.4; dash < 1.4; dash += 0.12) canvas.drawRect(R(dash, 0.806, 0.06, 0.006), dashP);
  // Street lamps: poles, bokeh, and their streaks on the wet road.
  for (const [lx, ly] of LAMPS) {
    canvas.drawRect(R(lx - 0.003, ly, 0.006, HORIZON + 0.04 - ly), fill(rgb(0.12, 0.11, 0.15)));
    canvas.drawRect(R(lx - 0.03, HORIZON + 0.05, 0.06, 0.3), vertical(Y(HORIZON + 0.05), Y(HORIZON + 0.35), [rgb(1, 0.7, 0.36, 0.22), rgb(1, 0.7, 0.36, 0)]));
    const glowR = 0.075 * w;
    const g = Skia.Paint();
    g.setShader(Skia.Shader.MakeRadialGradient({ x: X(lx), y: Y(ly) }, glowR, [rgb(1, 0.74, 0.4, 0.65), rgb(1, 0.6, 0.3, 0)], null, TileMode.Clamp));
    canvas.drawCircle(X(lx), Y(ly), glowR, g);
    canvas.drawCircle(X(lx), Y(ly), 3, fill(rgb(1, 0.95, 0.82)));
  }
  // Cars, their lights dragged into trails by slow shutters.
  const trail0 = shutterS * 14;
  for (const [lane, n, phase] of CARS) {
    const dir = lane === 0 ? 1 : -1;
    let f = ((n * clock) / LOOP + phase) % 1;
    if (f < 0) f += 1;
    const x = lane === 0 ? -0.2 + 1.4 * f : 1.2 - 1.4 * f;
    const y = LANE_Y[lane];
    // Each car's trail is its speed times the shutter time (exaggerated 14 ×).
    const trail = Math.min(0.8, (trail0 * n * 1.4) / LOOP);
    const ghost = 1 - 0.75 * smoothstep(0.02, 0.2, shutterS);
    const bodyLen = 0.16;
    const back = x - dir * bodyLen;
    const left = Math.min(x, back);
    canvas.drawRRect(Skia.RRectXY(R(left, y - 0.034, bodyLen, 0.036), 5, 5), fill(rgb(0.12, 0.12, 0.16, ghost)));
    canvas.drawRRect(Skia.RRectXY(R(left + (dir > 0 ? 0.035 : 0.045), y - 0.062, bodyLen - 0.08, 0.032), 6, 6), fill(rgb(0.1, 0.1, 0.14, ghost)));
    canvas.drawRRect(Skia.RRectXY(R(left + (dir > 0 ? 0.042 : 0.052), y - 0.056, bodyLen - 0.094, 0.02), 3, 3), fill(rgb(0.3, 0.3, 0.38, 0.6 * ghost)));
    // Headlight at the front (white), tail light at the back (red).
    const lights = [
      [x, 1, 0.95, 0.85, 3.4],
      [back, 1, 0.16, 0.1, 3.0],
    ];
    for (const [lx, cr, cg, cb, width] of lights) {
      const start = { x: X(lx), y: Y(y - 0.012) };
      const end = { x: X(lx - dir * trail), y: Y(y - 0.012) };
      for (const [mul, a] of [
        [3, 0.28],
        [1, 0.95],
      ]) {
        const p = stroke(rgb(1, 1, 1), width * mul);
        if (Math.abs(end.x - start.x) > 0.5) {
          p.setShader(Skia.Shader.MakeLinearGradient(end, start, [rgb(cr, cg, cb, 0), rgb(cr, cg, cb, a)], null, TileMode.Clamp));
        } else {
          p.setColor(rgb(cr, cg, cb, a));
        }
        canvas.drawLine(end.x, end.y, start.x, start.y, p);
      }
    }
  }
  // Exposure: darken below the resting brightness, lift above it.
  const e = Math.min(3, Math.max(-3, exposure(s)));
  if (e < 0) {
    canvas.drawRect(full, fill(rgb(0, 0, 0, 1 - Math.pow(2, e * 0.8))));
  } else if (e > 0) {
    const k = 1 - Math.pow(2, -e * 0.8);
    const add = fill(rgb(0.42 * k, 0.36 * k, 0.4 * k));
    add.setBlendMode(BlendMode.Plus);
    canvas.drawRect(full, add);
  }
  // Warmth.
  const t = Math.min(1, Math.max(-1, (s.warmth - 5200) / 2600));
  if (t > 0) canvas.drawRect(full, fill(rgb(1, 0.55, 0.2, 0.16 * t)));
  if (t < 0) canvas.drawRect(full, fill(rgb(0.25, 0.5, 1, 0.16 * -t)));
  // Grain, a hash of the frame number (24 a second).
  const count = Math.floor((iso / 12800) * 700);
  const frame = Math.floor(clock * 24) % 240;
  const light = fill(rgb(1, 1, 1, 0.36));
  const dark = fill(rgb(0, 0, 0, 0.45));
  for (let i = 0; i < count; i++) {
    const gx = hash(frame, i, 7);
    const gy = hash(i, frame, 3);
    canvas.drawRect(Skia.XYWHRect(X(gx), Y(gy), 1.8, 1.8), hash(frame, 11, i) > 0.5 ? light : dark);
  }
  canvas.restore();
  // Vignette and the shot's blink.
  const v = Skia.Paint();
  v.setShader(Skia.Shader.MakeRadialGradient({ x: cx, y: cy }, h * 0.8, [rgb(0, 0, 0, 0), rgb(0, 0, 0, 0.45)], [0.375, 1], TileMode.Clamp));
  canvas.drawRect(Skia.XYWHRect(r.x, r.y, w, h), v);
  if (s.flash > 0.001) canvas.drawRect(Skia.XYWHRect(r.x, r.y, w, h), fill(rgb(0, 0, 0, s.flash)));
}

export function drawScreen(canvas: SkCanvas, l: Layout, s: State, clock: number, font: SkFont) {
  'worklet';
  canvas.drawRect(Skia.XYWHRect(0, 0, l.w, l.h), fill(rgb(0, 0, 0)));
  // Viewfinder.
  const vf = viewfinder(l);
  canvas.save();
  canvas.clipRRect(rrect(vf, 22), ClipOp.Intersect, true);
  street(canvas, vf, s, clock);
  canvas.restore();
  brackets(canvas, vf);
  // Behind the lens chips.
  canvas.drawRRect(rrect({ x: l.w / 2 - 122, y: chipsY(l) - 22, w: 244, h: 44 }, 22), fill(rgb(0, 0, 0, 0.32)));

  // Left module.
  const cap = leftCapsule(l, s.morph);
  const corner = 15 + 3 * clamp01(s.morph);
  const wheelA = 1 - smoothstep(0, 0.5, s.morph);
  capsule(canvas, cap, corner, s.morph);
  if (wheelA > 0.01) drum(canvas, cap, s.ev * 10, true, wheelA);
  if (s.morph > 0.5) {
    canvas.save();
    canvas.clipRRect(rrect(cap, corner), ClipOp.Intersect, true);
    pad(canvas, cap, s);
    canvas.restore();
  }
  toggle(canvas, leftToggle(l, s.morph), s.knob, font);
  // Right module.
  const rc = rightCapsule(l);
  capsule(canvas, rc, 15, 0);
  drum(canvas, rc, (s.warmth - 5200) / 100, s.knobR > 0.5, 1);
  toggle(canvas, rightToggle(l), s.knobR, font);

  // Bottom row.
  shutter(canvas, shutterCenter(l), s.press);
  const gr = gallery(l);
  canvas.save();
  canvas.translate(gr.x + gr.w / 2, gr.y + gr.h / 2);
  canvas.scale(s.pop, s.pop);
  canvas.translate(-(gr.x + gr.w / 2), -(gr.y + gr.h / 2));
  canvas.save();
  canvas.clipRRect(rrect(gr, 10), ClipOp.Intersect, true);
  street(canvas, { x: gr.x - 6, y: gr.y - 10, w: 60, h: 72 }, rest(), 0);
  canvas.restore();
  canvas.drawRRect(rrect(gr, 10), stroke(rgb(1, 1, 1, 0.18), 1, false));
  canvas.restore();
  const cr = chevron(l);
  const cx = cr.x + cr.w / 2;
  const cy = cr.y + cr.h / 2;
  canvas.drawCircle(cx, cy, cr.w / 2, fill(rgb(0.11, 0.11, 0.12)));
  const chev = Skia.Path.Make();
  chev.moveTo(cx - 7, cy + 3);
  chev.lineTo(cx, cy - 4);
  chev.lineTo(cx + 7, cy + 3);
  canvas.drawPath(chev, stroke(rgb(1, 1, 1), 2.2));
}
