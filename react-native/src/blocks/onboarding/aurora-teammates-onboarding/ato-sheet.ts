// Aurora Teammates Onboarding — the sheet's numbers: its outline as SVG path data (a tgfx string slot,
// re-parsed on the render thread), and where each title word, subtitle, dot and the button stand in a
// frame. All worklets, all pure functions of the scene (ato-motion.ts).

import { measureText } from 'react-native-tgfx';

import { PAGE_COPY, type Token, TOKENS } from './ato-content';
import { clamp01, GL, mix, smooth, spring } from './ato-motion';
import { DOTS, type Layout, PAGES } from './ato-theme';

export const TITLE_FAMILY = 'ATO Display';
export const TEXT_FAMILY = 'ATO Inter';

/** Per token: width, and per page its left edge and baseline below the sheet's top (-1 = absent). */
export type TextLayout = {
  width: number[];
  x: number[][];
  y: number[][];
  line: number[][];
  /** per page, per subtitle line: its left x (centred by measurement) */
  subX: number[][];
};

/** Measured once the fonts are in: tgfx lays the line out exactly as <Text> will draw it. */
export function layoutTitle(L: Layout): TextLayout {
  const style = { fontFamily: TITLE_FAMILY, fontSize: L.titleSize, fontWeight: 700 as const, letterSpacing: L.titleTracking };
  const width: number[] = [];
  const x: number[][] = [];
  const y: number[][] = [];
  const line: number[][] = [];
  for (const t of TOKENS) {
    width.push(measureText(t, style).width);
    x.push([-1, -1, -1]);
    y.push([-1, -1, -1]);
    line.push([0, 0, 0]);
  }
  PAGE_COPY.forEach((page, p) => {
    page.lines.forEach((tokens, l) => {
      const text = tokens.join(' ');
      const left = L.cx - measureText(text, style).width / 2;
      let prefix = '';
      for (const t of tokens) {
        const i = TOKENS.indexOf(t as Token);
        x[i][p] = left + (prefix ? measureText(prefix, style).width : 0);
        y[i][p] = L.titleTop + L.titleCap + l * L.titleStep;
        line[i][p] = l;
        prefix += `${t} `;
      }
    });
  });
  const subStyle = { fontFamily: TEXT_FAMILY, fontSize: L.subtitleSize, fontWeight: 400 as const };
  const subX = PAGE_COPY.map((page) => page.subtitle.map((t) => L.cx - measureText(t, subStyle).width / 2));
  return { width, x, y, line, subX };
}

function n(v: number) {
  'worklet';
  return Math.round(v * 100) / 100;
}

/** The sheet's outline: flat top with the notch (centred on `cx`, which a dragging finger moves),
 * rounded shoulders, down past the bottom (closed), or only its top edge (for the rim light). */
export function sheetPathD(top: number, dip: number, cx: number, L: Layout, closed: boolean) {
  'worklet';
  const r = L.sheetCorner;
  const k = L.notch;
  let d = closed ? `M0 ${n(L.H + 2)} L0 ${n(top + r)} ` : `M0 ${n(top + r)} `;
  d += `Q0 ${n(top)} ${n(r)} ${n(top)} L${n(cx - k.half)} ${n(top)} `;
  d += `C${n(cx - k.half + k.c1)} ${n(top)} ${n(cx - k.c2)} ${n(top + dip)} ${n(cx)} ${n(top + dip)} `;
  d += `C${n(cx + k.c2)} ${n(top + dip)} ${n(cx + k.half - k.c1)} ${n(top)} ${n(cx + k.half)} ${n(top)} `;
  d += `L${n(L.W - r)} ${n(top)} Q${n(L.W)} ${n(top)} ${n(L.W)} ${n(top + r)}`;
  if (closed) d += ` L${n(L.W)} ${n(L.H + 2)} Z`;
  return d;
}

const PILL_POINTS = 160;

/**
 * The Next pill's outline, centred on 0,0: a capsule whose edge ripples like a soft blob while it is
 * pressed (`amp` pt along the normal; two slow waves running round the edge at phase `ph`). At amp 0
 * it is the plain capsule (160 points: the arcs stay within 0.1 pt of a circle).
 */
export function pillPathD(w: number, h: number, amp: number, ph: number) {
  'worklet';
  const r = h / 2;
  const ls = w - h;
  const per = 2 * ls + 2 * Math.PI * r;
  let d = '';
  for (let k = 0; k < PILL_POINTS; k++) {
    const s = (k / PILL_POINTS) * per;
    let x = 0;
    let y = 0;
    let nx = 0;
    let ny = 0;
    if (s < ls) {
      // top edge, left to right
      x = -ls / 2 + s;
      y = -r;
      ny = -1;
    } else if (s < ls + Math.PI * r) {
      const a = -Math.PI / 2 + (s - ls) / r;
      nx = Math.cos(a);
      ny = Math.sin(a);
      x = ls / 2 + r * nx;
      y = r * ny;
    } else if (s < 2 * ls + Math.PI * r) {
      x = ls / 2 - (s - ls - Math.PI * r);
      y = r;
      ny = 1;
    } else {
      const a = Math.PI / 2 + (s - 2 * ls - Math.PI * r) / r;
      nx = Math.cos(a);
      ny = Math.sin(a);
      x = -ls / 2 + r * nx;
      y = r * ny;
    }
    const t = (2 * Math.PI * s) / per;
    const off = amp * (0.62 * Math.sin(3 * t + ph) + 0.38 * Math.sin(5 * t - 1.3 * ph));
    d += `${k === 0 ? 'M' : 'L'}${n(x + nx * off)} ${n(y + ny * off)} `;
  }
  return `${d}Z`;
}

/** Title token i: x, baseline y, opacity, blur. Shared words glide; the rest blur out and in, line by line. */
export function tokenState(T: TextLayout, i: number, sc: number[], reduced: boolean, out: number[]) {
  'worklet';
  const from = sc[GL.from];
  const to = sc[GL.to];
  const u = sc[GL.u];
  const top = sc[GL.sheetTop];
  const xs = T.x[i];
  const ys = T.y[i];
  const inA = xs[from] >= 0;
  const inB = xs[to] >= 0;
  if (u <= 0 || (!inA && !inB)) {
    out[0] = inA ? xs[from] : 0;
    out[1] = inA ? top + ys[from] : 0;
    out[2] = inA && u <= 0 ? 1 : 0;
    out[3] = 0;
  } else if (inA && inB) {
    const g = reduced ? smooth(0, 1, u) : spring(clamp01((u - 0.16) / 0.6), 0.8, 10);
    out[0] = mix(xs[from], xs[to], g);
    out[1] = top + mix(ys[from], ys[to], g);
    out[2] = 1;
    out[3] = 0;
  } else if (inA) {
    const q = reduced ? smooth(0, 0.45, u) : smooth(0, 0.22, u - 0.03 * T.line[i][from]);
    out[0] = xs[from];
    out[1] = top + ys[from] - (reduced ? 0 : 10 * q);
    out[2] = 1 - q;
    out[3] = reduced ? 0 : 7 * q;
  } else {
    const q = reduced ? smooth(0.5, 1, u) : clamp01((u - 0.26 - 0.07 * T.line[i][to]) / 0.36);
    const e = reduced ? q : spring(q, 0.8, 10);
    out[0] = xs[to];
    out[1] = top + ys[to] + (reduced ? 0 : 12 * (1 - e));
    out[2] = reduced ? q : smooth(0, 0.7, q);
    out[3] = reduced ? 0 : 7 * (1 - smooth(0, 0.8, q));
  }
}

/** Glint over a rainbow word as it lands: x of the band's left edge (token space) and its strength. */
export function glintState(T: TextLayout, i: number, sc: number[], reduced: boolean, out: number[]) {
  'worklet';
  const u = sc[GL.u];
  const here = T.x[i][sc[GL.to]] >= 0 && u > 0 && !reduced;
  const g = here ? clamp01((u - 0.56) / 0.4) : 0;
  out[0] = -0.4 * T.width[i] + 1.8 * T.width[i] * g;
  out[1] = here ? Math.sin(Math.PI * g) : 0;
}

/** Subtitle of page p: baseline y, opacity, blur. */
export function subtitleState(p: number, sc: number[], L: Layout, reduced: boolean, out: number[]) {
  'worklet';
  const from = sc[GL.from];
  const to = sc[GL.to];
  const u = sc[GL.u];
  const base = sc[GL.sheetTop] + L.subtitleBase[p];
  if (p === from) {
    const q = u <= 0 ? 0 : reduced ? smooth(0, 0.45, u) : smooth(0.02, 0.3, u);
    out[0] = base - (reduced ? 0 : 6 * q);
    out[1] = 1 - q;
    out[2] = reduced ? 0 : 5 * q;
  } else if (p === to && u > 0) {
    const q = reduced ? smooth(0.5, 1, u) : clamp01((u - 0.42) / 0.38);
    out[0] = base + (reduced ? 0 : 8 * (1 - spring(q, 0.8, 10)));
    out[1] = reduced ? q : smooth(0, 0.8, q);
    out[2] = reduced ? 0 : 5 * (1 - q);
  } else {
    out[0] = base;
    out[1] = 0;
    out[2] = 0;
  }
}

function weightOf(i: number, pos: number) {
  'worklet';
  const d = Math.abs(pos - i);
  return d >= 1 ? 0 : 1 - d;
}

/** Dot i: left x within the track, width, height, and how "current" it is (0..1). */
export function dotState(i: number, sc: number[], L: Layout, out: number[]) {
  'worklet';
  const D = L.dots;
  const pos = sc[GL.dotPos];
  let x = D.pad;
  for (let k = 0; k < i; k++) x += D.dot + (D.pillW - D.dot) * weightOf(k, pos) + D.gap;
  const w = weightOf(i, pos);
  out[0] = x;
  out[1] = D.dot + (D.pillW - D.dot) * w;
  out[2] = D.dot + (D.pillH - D.dot) * w;
  out[3] = w;
}

export function trackWidth(L: Layout) {
  const D = L.dots;
  return 2 * D.pad + DOTS * D.dot + (D.pillW - D.dot) + (DOTS - 1) * D.gap;
}

export const SUBTITLES = PAGE_COPY.map((p) => p.subtitle);
export const PAGE_COUNT = PAGES;
