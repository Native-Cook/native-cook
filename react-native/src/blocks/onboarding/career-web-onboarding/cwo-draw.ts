// Career Web Onboarding — drawing, every frame, in the 402 × 874 reference space (scaled to the
// screen's width, standing on its bottom edge).

import { ClipOp, FilterMode, MipmapMode, PaintStyle, type SkCanvas, type SkFont, type SkImage, type SkPoint, type SkTypeface, Skia, StrokeCap, StrokeJoin, TileMode } from '@shopify/react-native-skia';

import { LINKS, linkOn, type Frame } from './cwo-motion';
import { ARROW, BUTTON, C, COPY, MARK, NODES, POPS, REF_W, ROPE_W, SIZE, TITLE } from './cwo-theme';

export type Label = { font: SkFont; glyphs: number[]; pos: SkPoint[]; width: number; size: number };

export function makeLabel(face: SkTypeface, str: string, size: number): Label {
  const font = Skia.Font(face, size);
  const ids = font.getGlyphIDs(str);
  const widths = font.getGlyphWidths(ids);
  const pos: SkPoint[] = [];
  let x = 0;
  for (let i = 0; i < ids.length; i++) {
    pos.push({ x, y: 0 });
    x += widths[i];
  }
  return { font, glyphs: ids, pos, width: x, size };
}

export function drawLabel(c: SkCanvas, l: Label, x: number, cy: number, ax: number, paint: ReturnType<typeof Skia.Paint>) {
  'worklet';
  c.drawGlyphs(l.glyphs, l.pos, x - l.width * ax, cy + l.size * 0.36, l.font, paint);
}

export type Res = {
  lines: Label[];
  dream: Label;
  roles: (Label[] | null)[];
  cta: Label;
  people: SkImage[];
};

export function makeRes(f: { semi: SkTypeface; medium: SkTypeface }, people: SkImage[]): Res {
  // the title is sized so its first line is as wide as the pin's
  const probe = makeLabel(f.semi, COPY.lines[0], 100);
  const size = (100 * TITLE.width0) / probe.width;
  return {
    lines: COPY.lines.map((s) => makeLabel(f.semi, s, size)),
    dream: makeLabel(f.semi, COPY.dream, size),
    roles: NODES.map((n) => (n.label ? n.label.map((s) => makeLabel(f.medium, s, SIZE.role)) : null)),
    cta: makeLabel(f.semi, COPY.cta, SIZE.cta),
    people,
  };
}

export type Geo = { w: number; h: number; s: number; dy: number };
export type Live = {
  px: number[]; // node centres (pt)
  py: number[];
  grow: number[]; // links grown per person
  vib: number[]; // per link swing (pt)
  hold: number; // node under the finger
  press: number;
  shoot: number; // button arrow shot 0 … 1 (−1 none)
  pick: number[]; // ring per person 0 … 1 (−1 none)
};

function hex(h: string, a = 1) {
  'worklet';
  const n = parseInt(h.slice(1), 16);
  return Float32Array.of(((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255, a);
}

function drawTitle(c: SkCanvas, r: Res, f: Frame) {
  'worklet';
  const white = Skia.Paint();
  white.setAntiAlias(true);
  white.setColor(hex(C.white));
  for (let i = 0; i < 3; i++) drawLabel(c, r.lines[i], TITLE.x, TITLE.lines[i], 0, white);
  const dx = TITLE.x + r.lines[2].width;
  // "Dream" in white, then the marker band and the dark word clipped to where the marker has been
  drawLabel(c, r.dream, dx + 8, TITLE.lines[2], 0, white);
  if (f.mark <= 0.002) return;
  const m = MARK;
  const xe = m.x0 + (m.x1 - m.x0) * f.mark;
  const k = f.mark;
  const band = Skia.Path.Make();
  band.moveTo(m.x0, m.top0);
  band.lineTo(xe, m.top0 + (m.top1 - m.top0) * k);
  band.lineTo(xe + 3, (m.top0 + (m.top1 - m.top0) * k + m.bot0 + (m.bot1 - m.bot0) * k) / 2); // the felt tip's soft end
  band.lineTo(xe, m.bot0 + (m.bot1 - m.bot0) * k);
  band.lineTo(m.x0, m.bot0);
  band.close();
  const hp = Skia.Paint();
  hp.setAntiAlias(true);
  hp.setColor(hex(C.highlight));
  c.drawPath(band, hp);
  c.save();
  c.clipPath(band, ClipOp.Intersect, true);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(hex(C.ink));
  drawLabel(c, r.dream, dx + 8, TITLE.lines[2], 0, ink);
  c.restore();
}

function drawArrow(c: SkCanvas, f: Frame) {
  'worklet';
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStyle(PaintStyle.Stroke);
  st.setStrokeWidth(2.4);
  st.setStrokeCap(StrokeCap.Round);
  st.setStrokeJoin(StrokeJoin.Round);
  st.setColor(hex(C.white));
  if (f.arrow > 0.003) {
    const p = Skia.Path.Make();
    p.moveTo(ARROW.a.x, ARROW.a.y);
    p.quadTo(ARROW.c.x, ARROW.c.y, ARROW.b.x, ARROW.b.y);
    if (f.arrow < 0.999) p.trim(0, f.arrow, false);
    c.drawPath(p, st);
  }
  if (f.arrow > 0.97) {
    // the head arrives with a little pop
    const k = 1 + 0.35 * f.head;
    const b = ARROW.b;
    const tx = b.x - ARROW.c.x;
    const ty = b.y - ARROW.c.y;
    const tl = Math.hypot(tx, ty);
    const ux = tx / tl;
    const uy = ty / tl;
    const hl = ARROW.head * k;
    const h = Skia.Path.Make();
    const s = Math.sin(0.55);
    const co = Math.cos(0.55);
    h.moveTo(b.x - hl * (ux * co - uy * s), b.y - hl * (uy * co + ux * s));
    h.lineTo(b.x, b.y);
    h.lineTo(b.x - hl * (ux * co + uy * s), b.y - hl * (uy * co - ux * s));
    c.drawPath(h, st);
  }
  // the pop marks
  const k = 1 + 0.45 * f.pops;
  for (let i = 0; i < POPS.length; i++) {
    const p = POPS[i];
    const mx = (p.x0 + p.x1) / 2;
    const my = (p.y0 + p.y1) / 2;
    c.drawLine(mx + (p.x0 - mx) * k, my + (p.y0 - my) * k, mx + (p.x1 - mx) * k, my + (p.y1 - my) * k, st);
  }
}

function drawLinks(c: SkCanvas, live: Live, homeLen: number[], t: number) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(PaintStyle.Stroke);
  p.setStrokeCap(StrokeCap.Round);
  p.setColor(hex(C.rope));
  for (let l = 0; l < LINKS.length; l++) {
    const on = linkOn(l, live.grow);
    if (on <= 0.005) continue;
    const a = LINKS[l][0];
    const b = LINKS[l][1];
    const ax = live.px[a];
    const ay = live.py[a];
    // a growing link reaches out from the person toward the role
    const bx = ax + (live.px[b] - ax) * on;
    const by = ay + (live.py[b] - ay) * on;
    const dx = bx - ax;
    const dy = by - ay;
    const len = Math.hypot(dx, dy) || 1;
    const full = Math.hypot(live.px[b] - ax, live.py[b] - ay);
    // stretched it thins, slack it thickens a little (keeps its volume)
    const w = ROPE_W * Math.max(0.5, Math.min(1.2, Math.sqrt(homeLen[l] / full)));
    const nx = -dy / len;
    const ny = dx / len;
    const swing = live.vib[l];
    const path = Skia.Path.Make();
    path.moveTo(ax, ay);
    path.quadTo((ax + bx) / 2 + nx * swing, (ay + by) / 2 + ny * swing, bx, by);
    p.setStrokeWidth(w);
    c.drawPath(path, p);
    // once connected, a soft violet signal runs along the link toward the role, every 3 s
    if (on >= 0.999) {
      const u = (t / 3 + l * 0.29) % 1;
      const qx = (ax + bx) / 2 + nx * swing;
      const qy = (ay + by) / 2 + ny * swing;
      const sx = (1 - u) * (1 - u) * ax + 2 * (1 - u) * u * qx + u * u * bx;
      const sy = (1 - u) * (1 - u) * ay + 2 * (1 - u) * u * qy + u * u * by;
      const sa = Math.sin(Math.PI * u);
      const sg = Skia.Paint();
      sg.setAntiAlias(true);
      sg.setShader(Skia.Shader.MakeRadialGradient({ x: sx, y: sy }, 9, [hex(C.highlight, 0.9 * sa), hex(C.highlight, 0)], null, TileMode.Clamp));
      c.drawCircle(sx, sy, 9, sg);
    }
    // the growing tip is a little bead
    if (on < 0.999) {
      const bead = Skia.Paint();
      bead.setAntiAlias(true);
      bead.setColor(hex(C.rope));
      c.drawCircle(bx, by, w * 0.85, bead);
    }
  }
}

function drawNodes(c: SkCanvas, r: Res, live: Live) {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(hex(C.ink));
  let person = 0;
  for (let i = 0; i < NODES.length; i++) {
    const n = NODES[i];
    const x = live.px[i];
    const y = live.py[i];
    const held = i === live.hold ? 1 : 0;
    c.save();
    c.translate(x, y);
    c.scale(1 + 0.06 * held, 1 + 0.06 * held);
    if (n.kind === 'person') {
      const ring = live.pick[person];
      if (ring >= 0) {
        const rp = Skia.Paint();
        rp.setAntiAlias(true);
        rp.setStyle(PaintStyle.Stroke);
        rp.setStrokeWidth(3 * (1 - ring) + 0.5);
        rp.setColor(hex(C.ring, 1 - ring));
        c.drawCircle(0, 0, n.r + 4 + 16 * ring, rp);
      }
      p.setColor(hex(n.bg ?? '#FFFFFF'));
      c.drawCircle(0, 0, n.r, p);
      const img = r.people[person];
      const clip = Skia.Path.Make();
      clip.addCircle(0, 0, n.r);
      c.save();
      c.clipPath(clip, ClipOp.Intersect, true);
      c.drawImageRectOptions(img, Skia.XYWHRect(0, 0, img.width(), img.height()), Skia.XYWHRect(-n.r, -n.r, n.r * 2, n.r * 2), FilterMode.Linear, MipmapMode.Linear, null);
      c.restore();
      person++;
    } else {
      p.setColor(hex(C.white));
      c.drawCircle(0, 0, n.r, p);
      const lab = r.roles[i];
      if (lab) {
        drawLabel(c, lab[0], 0, -9.5, 0.5, ink);
        drawLabel(c, lab[1], 0, 9.5, 0.5, ink);
      }
    }
    c.restore();
  }
}

function drawButton(c: SkCanvas, r: Res, f: Frame, live: Live) {
  'worklet';
  const b = BUTTON;
  const s = 1 - 0.04 * live.press;
  c.save();
  c.translate(b.x + b.w / 2, b.y + b.h / 2);
  c.scale(s, s);
  c.translate(-(b.x + b.w / 2), -(b.y + b.h / 2));
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setColor(hex(C.white));
  c.drawRRect(Skia.RRectXY(Skia.XYWHRect(b.x, b.y, b.w, b.h), b.h / 2, b.h / 2), p);
  const ink = Skia.Paint();
  ink.setAntiAlias(true);
  ink.setColor(hex(C.ink));
  drawLabel(c, r.cta, b.x + (b.w - b.dot.r * 2 - 10) / 2 + 4, b.y + b.h / 2, 0.5, ink);
  const d = b.dot;
  c.drawCircle(d.x + 2 * f.nudge, d.y, d.r * (1 + 0.04 * f.nudge), ink);
  // the arrow: nudges forward; when tapped it shoots out to the right and slides back in from the left
  const clip = Skia.Path.Make();
  clip.addCircle(d.x + 2 * f.nudge, d.y, d.r - 2);
  c.save();
  c.clipPath(clip, ClipOp.Intersect, true);
  let ox = 5 * f.nudge;
  if (live.shoot >= 0) ox = live.shoot < 0.5 ? 40 * (live.shoot / 0.5) * (live.shoot / 0.5) : -40 * (1 - (live.shoot - 0.5) / 0.5) ** 2;
  const st = Skia.Paint();
  st.setAntiAlias(true);
  st.setStyle(PaintStyle.Stroke);
  st.setStrokeWidth(2.2);
  st.setStrokeCap(StrokeCap.Round);
  st.setStrokeJoin(StrokeJoin.Round);
  st.setColor(hex(C.white));
  const ar = Skia.Path.Make();
  ar.moveTo(d.x - 8 + ox, d.y);
  ar.lineTo(d.x + 8 + ox, d.y);
  ar.moveTo(d.x + 2.5 + ox, d.y - 5.5);
  ar.lineTo(d.x + 8 + ox, d.y);
  ar.lineTo(d.x + 2.5 + ox, d.y + 5.5);
  c.drawPath(ar, st);
  c.restore();
  c.restore();
}

export function drawScene(c: SkCanvas, r: Res, g: Geo, f: Frame, live: Live, homeLen: number[]) {
  'worklet';
  c.drawColor(hex(C.bg));
  c.save();
  c.translate(0, g.dy);
  c.scale(g.s, g.s);
  // a faint grid on the backdrop, as in the pin
  const grid = Skia.Paint();
  grid.setColor(Float32Array.of(1, 1, 1, 0.035));
  grid.setStrokeWidth(1);
  for (let x = 12; x < REF_W; x += 26) c.drawLine(x, -g.dy / g.s, x, 900, grid);
  for (let y = 10; y < 900; y += 26) c.drawLine(0, y, REF_W, y, grid);
  drawTitle(c, r, f);
  drawArrow(c, f);
  drawLinks(c, live, homeLen, f.t);
  drawNodes(c, r, live);
  drawButton(c, r, f, live);
  c.restore();
}
