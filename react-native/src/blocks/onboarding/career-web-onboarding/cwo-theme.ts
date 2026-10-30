// Career Web Onboarding — layout, colour and copy.
//
// Measured on the pin's first phone (a 1000 × 750 px mock-up of three phones; the first one's
// screen spans x 56 … 307, y 143 … 689) and mapped to a 402 × 874 pt reference screen:
// x → (px − 56) · S, y → (px − 143) · S.

export const REF_W = 402;
export const REF_H = 874;
export const S = REF_W / 251;
export const X = (px: number) => (px - 56) * S;
export const Y = (px: number) => (px - 143) * S;

export const COPY = {
  lines: ['Build Your', 'Future, Build', 'Your '],
  dream: 'Dream',
  cta: 'Get Started !',
};

export const C = {
  bg: '#101011',
  grid: 'rgba(255, 255, 255, 0.035)',
  white: '#FFFFFF',
  ink: '#121214',
  highlight: '#C8BAFF',
  rope: '#FFFFFF',
  ring: '#C8BAFF',
};

export const TITLE = { x: X(68), lines: [Y(214), Y(252), Y(290)], width0: X(213) - X(68) };

/** The marker behind "Dream": a slightly skewed band (its right side taller). */
export const MARK = { x0: X(140), x1: X(247), top0: Y(271), top1: Y(266), bot0: Y(308), bot1: Y(312) };

/** The hand-drawn arrow from the title down to the web. */
export const ARROW = { a: { x: X(272), y: Y(316) }, c: { x: X(284), y: Y(350) }, b: { x: X(250), y: Y(378) }, head: 13 };

/** Three little "pop" marks above the first portrait. */
export const POPS = [
  { x0: X(99), y0: Y(342), x1: X(104), y1: Y(354) },
  { x0: X(114), y0: Y(338), x1: X(111), y1: Y(351) },
  { x0: X(89), y0: Y(358), x1: X(99), y1: Y(363) },
];

export type Node = { kind: 'person' | 'role'; x: number; y: number; r: number; label?: [string, string]; img?: 'a' | 'b' | 'c'; bg?: string };

const R = 51;
/** The web: three people and four roles (index order is used by the links below). */
export const NODES: Node[] = [
  { kind: 'person', x: X(126), y: Y(398), r: R, img: 'a', bg: '#DFF5E1' },
  { kind: 'person', x: X(186), y: Y(483), r: R, img: 'b', bg: '#F7D5B5' },
  { kind: 'person', x: X(238), y: Y(560), r: R, img: 'c', bg: '#CEC2F7' },
  { kind: 'role', x: X(207), y: Y(415), r: R, label: ['UX/UI', 'Designer'] },
  { kind: 'role', x: X(104), y: Y(483), r: R, label: ['Product', 'Designer'] },
  { kind: 'role', x: X(258), y: Y(488), r: R, label: ['Software', 'Engineer'] },
  { kind: 'role', x: X(155), y: Y(555), r: R, label: ['Flutter', 'Developer'] },
];

/** The pin's two white bars, always there: UX/UI ↔ Product, Software ↔ Flutter. */
export const BARS: [number, number][] = [
  [3, 4],
  [5, 6],
];

/** The links each person grows when chosen (person → roles that fit them). */
export const FITS: number[][] = [
  [3, 4],
  [4, 5],
  [5, 6],
];

export const ROPE_W = 12.8;

export const BUTTON = { x: X(82), y: Y(625), w: X(288) - X(82), h: Y(663) - Y(625), dot: { x: X(266), y: Y(644), r: 27 } };

export const SIZE = { role: 15.5, cta: 17 };
