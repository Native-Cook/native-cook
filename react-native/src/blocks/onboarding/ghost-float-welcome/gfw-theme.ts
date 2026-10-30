// Ghost Float Welcome — layout, colour and copy.
//
// Measured on the pin (a 1200 × 1533 px mock-up whose phone screen spans x 294 … 904, y 90 … 1416)
// and mapped to a 402 × 874 pt reference screen: x → (px − 294) · S, y → (px − 90) · S.

export const REF_W = 402;
export const REF_H = 874;
export const S = REF_W / 610;
export const X = (px: number) => (px - 294) * S;
export const Y = (px: number) => (px - 90) * S;

export const COPY = {
  title: 'Welcome to Wisp',
  sub1: 'To get started, create a new wallet or',
  sub2: 'import an existing one.',
  agree: 'I agree to the ',
  terms: 'Terms of Service',
  create: 'Create a new wallet',
  have: 'I already have a wallet',
};

export const C = {
  bg: '#1C1C1C',
  white: '#FFFFFF',
  sub: '#E8E8E8',
  grey: '#9A9A9A',
  lavender: '#AB9FF2',
  pill: '#AD9FF6',
  dot: '#55516A',
  star: '#392D5B',
  ring: '#8A8A8A',
  btnOff: '#67608C',
  btnOffInk: '#17122C',
  btnOn: '#AB9FF2',
  btnOnInk: '#1C1C1C',
  have: '#8E8E8E',
  ghost: '#FFFEFA',
  eye: '#1D1C1A',
};

export const SIZE = { title: 29, sub: 17.6, agree: 17.4, button: 17.4, have: 17.2 };

export const UI = {
  help: { x: X(862), y: Y(196), r: 9.2 },
  titleY: Y(888),
  sub1Y: Y(946),
  sub2Y: Y(981),
  dotsY: Y(1043),
  dots: { pillW: 15.8, h: 5.3, gap: 9.9, n: 6 },
  check: { x: X(379), y: Y(1132), r: 12.5 },
  agreeX: X(418),
  button: { x: X(321), y: Y(1191), w: X(878) - X(321), h: Y(1268) - Y(1191) },
  haveY: Y(1321),
};

/** The floating things: kind, centre (pt), radius (pt), depth (parallax 0 far … 1 near). */
export type Body = { kind: 'lumen' | 'ember' | 'fern' | 'halo' | 'dino' | 'penguin' | 'ghost'; x: number; y: number; r: number; depth: number };

export const BODIES: Body[] = [
  { kind: 'ghost', x: X(600), y: Y(520), r: 41, depth: 1 },
  { kind: 'lumen', x: X(600), y: Y(355), r: X(656) - X(600), depth: 0.8 },
  { kind: 'dino', x: X(350), y: Y(462), r: X(401) - X(350), depth: 0.6 },
  { kind: 'halo', x: X(852), y: Y(504), r: X(894) - X(852), depth: 0.5 },
  { kind: 'ember', x: X(342), y: Y(662), r: X(390) - X(342), depth: 0.7 },
  { kind: 'penguin', x: X(832), y: Y(670), r: X(874) - X(832), depth: 0.55 },
  { kind: 'fern', x: X(595), y: Y(725), r: X(655) - X(595), depth: 0.9 },
];

/** Token colours: face, glyph. */
export const TOKEN = {
  lumen: { face: '#EAEAEA', glyph: '#151515' },
  ember: { face: '#F2662B', glyph: '#FFF2E7' },
  fern: { face: '#29C28C', glyph: '#F4FFF9' },
  halo: { face: '#ADA2F0', glyph: '#FFFFFF' },
};

/** White four-point sparkles riding on tokens: body index, offset (pt), half-size. */
export const GLINTS = [
  { body: 1, dx: X(624) - X(600), dy: Y(318) - Y(355), s: 12 },
  { body: 3, dx: X(876) - X(852), dy: Y(477) - Y(504), s: 9.5 },
  { body: 4, dx: X(370) - X(342), dy: Y(628) - Y(662), s: 9.5 },
  { body: 6, dx: X(552) - X(595), dy: Y(688) - Y(725), s: 10.5 },
];

/** The dark violet stars of the backdrop: centre, half-size, depth. */
export const STARS = [
  { x: X(418), y: Y(272), s: 17, depth: 0.25 },
  { x: X(810), y: Y(325), s: 25, depth: 0.35 },
  { x: X(478), y: Y(588), s: 15, depth: 0.2 },
  { x: X(752), y: Y(605), s: 12, depth: 0.15 },
  { x: X(385), y: Y(758), s: 20, depth: 0.3 },
  { x: X(787), y: Y(800), s: 16, depth: 0.25 },
];

/** Where the floating area ends: the finger stirs above this line. */
export const FIELD_BOTTOM = Y(840);
