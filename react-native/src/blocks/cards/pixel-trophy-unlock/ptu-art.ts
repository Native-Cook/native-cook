// Pixel Trophy Unlock — the trophies' pixel art, generated once per card into a Skia picture.
//
// Each scene is a grid of square pixels: a sky in dithered bands, a sun or a moon, a skyline of
// mountains, buildings or pines, a ground, and a big emblem in the middle drawn from a small bitmap.
// Nothing is an image file: the scenes are rules, so every card is original and crisp at any size.
import { type SkPicture, Skia } from '@shopify/react-native-skia';

type Scene = {
  sky: [string, string, string];
  sun?: { x: number; y: number; r: number; color: string; glow: string };
  moon?: boolean;
  stars?: boolean;
  skyline: 'mountains' | 'city' | 'pines' | 'track' | 'road' | 'hills';
  land: [string, string];
  emblem: keyof typeof EMBLEMS;
  ink: string;
  shade: string;
};

// 11 × 11 bitmaps: '#' ink, '+' shade, '.' clear
const EMBLEMS = {
  star: ['.....#.....', '.....#.....', '....###....', '....###....', '###########', '.#########.', '..#######..', '..###+###..', '.###...###.', '.##.....##.', '.#.......#.'],
  burst: ['#....#....#', '.#...#...#.', '..#..#..#..', '...#####...', '....###....', '###########', '....###....', '...#####...', '..#..#..#..', '.#...#...#.', '#....#....#'],
  flame: ['.....#.....', '....##.....', '....###....', '...####.#..', '..#######..', '..###+###..', '.###+++###.', '.##+++++##.', '.##++#++##.', '..##+#+##..', '...#####...'],
  shoe: ['...........', '..####.....', '..#++#.....', '..#++#.....', '..#++####..', '..#+++++##.', '.##+++++++#', '.#########+', '.#########.', '..++.++.++.', '...........'],
  ten: ['...........', '.##..####..', '###.##..##.', '.##.##..##.', '.##.##..##.', '.##.##..##.', '.##.##..##.', '.##.##..##.', '####.####..', '...........', '+++++++++++'],
  bolt: ['......###..', '.....###...', '....###....', '...###.....', '..#######..', '.....###...', '....###....', '...###.....', '..###......', '.##........', '##.........'],
  wave: ['...........', '...........', '.###....##.', '##.##..##..', '#...####...', '...........', '.###....##.', '##.##..##..', '#...####...', '...........', '...........'],
  moonrun: ['....###....', '...#.......', '..#........', '..#........', '..#.....#..', '...#...#...', '....###....', '...........', '.#.#.#.#.#.', '...........', '...........'],
} as const;

export const SCENES: Record<string, Scene> = {
  supernova: { sky: ['#160b30', '#b62ad8', '#ffcf7a'], sun: { x: 0.5, y: 0.42, r: 0.2, color: '#fff7e0', glow: '#ff9be8' }, skyline: 'mountains', land: ['#0c5c52', '#22c79a'], emblem: 'burst', ink: '#ffffff', shade: '#ffd27a' },
  superstar: { sky: ['#070c2e', '#14246a', '#2c4aa0'], stars: true, skyline: 'city', land: ['#0b1238', '#1d2c66'], emblem: 'star', ink: '#ffe066', shade: '#ff9d2e' },
  sorcerer: { sky: ['#0e1534', '#3a2f7a', '#7d58c4'], moon: true, stars: true, skyline: 'pines', land: ['#0b1a22', '#17323a'], emblem: 'flame', ink: '#ff8a3c', shade: '#ffd166' },
  stepstar: { sky: ['#ff5d3a', '#ff9a3c', '#ffd65a'], sun: { x: 0.5, y: 0.55, r: 0.24, color: '#fff3c4', glow: '#ffbf6a' }, skyline: 'hills', land: ['#5a2a6e', '#8a3f8f'], emblem: 'shoe', ink: '#ffffff', shade: '#ffd0e6' },
  tenfold: { sky: ['#0f4b5c', '#2c8f99', '#9fe3d1'], skyline: 'track', land: ['#a6402e', '#e0694a'], emblem: 'ten', ink: '#ffffff', shade: '#ffd166' },
  fastest: { sky: ['#1f1238', '#6b2f6e', '#ff8d5c'], sun: { x: 0.7, y: 0.5, r: 0.16, color: '#ffe0a0', glow: '#ff7a4a' }, skyline: 'road', land: ['#232032', '#3a3550'], emblem: 'bolt', ink: '#fff06a', shade: '#ff9d2e' },
  momentum: { sky: ['#0e2a24', '#1f5b44', '#6fc28a'], skyline: 'pines', land: ['#0a1d17', '#163a2c'], emblem: 'wave', ink: '#c8ff6a', shade: '#ffffff' },
  nightrun: { sky: ['#06091c', '#141c4a', '#2a2f72'], moon: true, stars: true, skyline: 'road', land: ['#0c0f24', '#1a1f3e'], emblem: 'moonrun', ink: '#ffffff', shade: '#9fb4ff' },
  hillclimb: { sky: ['#2a1b4f', '#7a4fb0', '#f2a6c8'], sun: { x: 0.3, y: 0.45, r: 0.14, color: '#fff0f5', glow: '#ffc2dc' }, skyline: 'mountains', land: ['#3b1f4f', '#6a3b80'], emblem: 'star', ink: '#ffffff', shade: '#ffd6ec' },
  streak: { sky: ['#3a0d10', '#a3291f', '#ff8a3c'], skyline: 'hills', land: ['#2a0b0e', '#4f1a1c'], emblem: 'flame', ink: '#ffe06a', shade: '#ff6a3c' },
};

const hex = (c: string) => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
const lerp = (a: string, b: string, k: number) => {
  const A = hex(a);
  const B = hex(b);
  return `rgb(${Math.round(A[0] + (B[0] - A[0]) * k)},${Math.round(A[1] + (B[1] - A[1]) * k)},${Math.round(A[2] + (B[2] - A[2]) * k)})`;
};
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
const rnd = (i: number) => {
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
};

/** The scene's colour at pixel (x, y) of a W × H grid. */
const pixel = (s: Scene, x: number, y: number, W: number, H: number): string => {
  const u = (x + 0.5) / W;
  const v = (y + 0.5) / H;
  // ground line and skyline height at this column
  const horizon = 0.66;
  let top = horizon;
  if (s.skyline === 'mountains') top = horizon - 0.16 * Math.abs(Math.sin(u * 7.1 + 0.6)) - 0.08 * Math.abs(Math.sin(u * 13.3));
  else if (s.skyline === 'city') top = horizon - (0.08 + 0.2 * rnd(Math.floor(u * 7) + 3)) * (Math.floor(u * 7) % 2 === 0 ? 1 : 0.7);
  else if (s.skyline === 'pines') top = horizon - 0.18 * Math.max(0, 1 - Math.abs(((u * 6) % 1) - 0.5) * 2.4) - 0.03;
  else if (s.skyline === 'hills') top = horizon - 0.07 - 0.06 * Math.sin(u * 5 + 1);
  else top = horizon - 0.02;
  const d = BAYER[(y % 4) * 4 + (x % 4)] / 16;
  if (v >= horizon) {
    // ground: bands, a track's lanes or a road's centre line
    const g = (v - horizon) / (1 - horizon);
    if (s.skyline === 'track' && (y % 3 === 0)) return '#f6efe6';
    if (s.skyline === 'road' && Math.abs(u - 0.5) < 0.06 * (0.4 + g) && y % 3 !== 0) return '#fff2a8';
    return g + (d - 0.5) * 0.25 > 0.5 ? s.land[1] : s.land[0];
  }
  if (v >= top) {
    // skyline: lit windows in the city, a lighter ridge on mountains
    if (s.skyline === 'city' && x % 2 === 1 && y % 3 === 1 && rnd(x * 31 + y * 7) > 0.45) return '#ffd86a';
    return v - top < 0.025 && s.skyline !== 'city' ? s.land[1] : s.land[0];
  }
  // sun or moon
  if (s.sun) {
    const dx = (u - s.sun.x) * W;
    const dy = (v - s.sun.y) * H;
    const r = s.sun.r * W;
    const dd = Math.hypot(dx, dy);
    if (dd < r) return s.sun.color;
    if (dd < r * 1.5 && d < 1 - (dd - r) / (r * 0.5)) return s.sun.glow;
  }
  if (s.moon) {
    const dx = (u - 0.76) * W;
    const dy = (v - 0.2) * H;
    const r = W * 0.11;
    if (Math.hypot(dx, dy) < r && Math.hypot(dx + r * 0.45, dy - r * 0.2) > r * 0.85) return '#f4f1d0';
  }
  if (s.stars && rnd(x * 17 + y * 131) > 0.965) return '#ffffff';
  // sky in three dithered bands
  const k = v / horizon;
  return k + (d - 0.5) * 0.18 < 0.5 ? lerp(s.sky[0], s.sky[1], Math.min(1, (k + (d - 0.5) * 0.18) * 2)) : lerp(s.sky[1], s.sky[2], Math.min(1, (k - 0.5) * 2 + (d - 0.5) * 0.18));
};

/** A picture of the scene, W × H pixels of `px` points, its emblem centred a little above the middle. */
export const scenePicture = (name: string, W: number, H: number, px: number): SkPicture => {
  const s = SCENES[name];
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, W * px, H * px));
  const p = Skia.Paint();
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      p.setColor(Skia.Color(pixel(s, x, y, W, H)));
      c.drawRect(Skia.XYWHRect(x * px, y * px, px + 0.35, px + 0.35), p);
    }
  }
  const bm = EMBLEMS[s.emblem];
  const ex = Math.floor((W - 11) / 2);
  const ey = Math.floor(H * 0.2);
  // a dark outline first, then the ink
  p.setColor(Skia.Color('rgba(10,8,20,0.85)'));
  for (let j = 0; j < 11; j++)
    for (let i = 0; i < 11; i++)
      if (bm[j][i] !== '.')
        for (const [ox, oy] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ])
          c.drawRect(Skia.XYWHRect((ex + i + ox) * px, (ey + j + oy) * px, px, px), p);
  for (let j = 0; j < 11; j++)
    for (let i = 0; i < 11; i++) {
      const ch = bm[j][i];
      if (ch === '.') continue;
      p.setColor(Skia.Color(ch === '#' ? s.ink : s.shade));
      c.drawRect(Skia.XYWHRect((ex + i) * px, (ey + j) * px, px + 0.35, px + 0.35), p);
    }
  return rec.finishRecordingAsPicture();
};
