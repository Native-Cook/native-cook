// Friend Radar Map — the map and the friends' bitmap portraits, generated once into Skia pictures.
//
// The map is a street grid turned 18°, two avenues across it and a ring road, with a few street
// names. A portrait is a head-and-shoulders field (head, hair, glasses, shoulders, a lit side)
// thresholded through a 4 × 4 Bayer matrix into three greens, like a bitmap print; nothing is a
// photo or an image file.
import { type SkFont, type SkPicture, Skia } from '@shopify/react-native-skia';

export const WORLD = { w: 1100, h: 1500 } as const;

export const FRIENDS = [
  { name: 'ANASTASIA', street: 'IS ON BERSON STREET NOW', sooner: 'MEET HER 3 MIN SOONER', x: 430, y: 560, hair: 'bob', glasses: true, skew: 0.16 },
  { name: 'IGOR', street: 'IS AT KILN AVENUE NOW', sooner: 'MEET HIM 5 MIN SOONER', x: 760, y: 880, hair: 'short', glasses: false, skew: -0.12 },
  { name: 'DASHA', street: 'IS BY PIER ROAD NOW', sooner: 'MEET HER 2 MIN SOONER', x: 330, y: 1080, hair: 'bun', glasses: false, skew: 0.08 },
] as const;

const rnd = (i: number) => {
  const s = Math.sin(i * 91.3458) * 47453.5453;
  return s - Math.floor(s);
};

export const mapPicture = (label: SkFont | null): SkPicture => {
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, WORLD.w, WORLD.h));
  const bg = Skia.Paint();
  bg.setColor(Skia.Color('#101012'));
  c.drawRect(Skia.XYWHRect(0, 0, WORLD.w, WORLD.h), bg);
  const st = (wd: number, col: string) => {
    const p = Skia.Paint();
    p.setAntiAlias(true);
    p.setStyle(1);
    p.setStrokeWidth(wd);
    p.setStrokeCap(1);
    p.setStrokeJoin(1);
    p.setColor(Skia.Color(col));
    return p;
  };
  const minor = st(3, '#26262b');
  const major = st(8, '#38383f');
  c.save();
  c.translate(WORLD.w / 2, WORLD.h / 2);
  c.rotate(18, 0, 0);
  for (let i = -9; i <= 9; i++) {
    const x = i * 92 + (rnd(i) - 0.5) * 30;
    const pb = Skia.PathBuilder.Make();
    pb.moveTo(x, -1100);
    pb.cubicTo(x + 40 * (rnd(i + 3) - 0.5), -300, x - 40 * (rnd(i + 7) - 0.5), 300, x + 20, 1100);
    c.drawPath(pb.build(), i % 4 === 0 ? major : minor);
  }
  for (let j = -12; j <= 12; j++) {
    const y = j * 86 + (rnd(j + 40) - 0.5) * 26;
    const pb = Skia.PathBuilder.Make();
    pb.moveTo(-1000, y);
    pb.cubicTo(-300, y + 30 * (rnd(j + 50) - 0.5), 300, y - 30 * (rnd(j + 60) - 0.5), 1000, y + 10);
    c.drawPath(pb.build(), j % 5 === 0 ? major : minor);
  }
  c.restore();
  // two avenues and a ring road
  const ave = st(10, '#3d3d45');
  const a1 = Skia.PathBuilder.Make();
  a1.moveTo(-50, 300);
  a1.cubicTo(400, 520, 700, 760, 1150, 1300);
  c.drawPath(a1.build(), ave);
  const a2 = Skia.PathBuilder.Make();
  a2.moveTo(1150, 200);
  a2.cubicTo(800, 600, 500, 900, -50, 1250);
  c.drawPath(a2.build(), ave);
  c.drawCircle(560, 780, 360, st(6, '#2f2f36'));
  if (label) {
    const lp = Skia.Paint();
    lp.setAntiAlias(true);
    lp.setColor(Skia.Color('#5a5a62'));
    const names = [
      ['Berson St', 380, 610, 18],
      ['Kiln Ave', 700, 820, -42],
      ['Pier Rd', 250, 1120, 18],
      ['Mill Ln', 820, 420, 18],
      ['Ring Rd', 220, 700, -70],
      ['Dock St', 600, 1240, 18],
    ] as const;
    for (const [n, x, y, a] of names) {
      c.save();
      c.translate(x, y);
      c.rotate(a, 0, 0);
      c.drawText(n, 0, 0, lp, label);
      c.restore();
    }
  }
  return rec.finishRecordingAsPicture();
};

const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];

/** A bitmap portrait: W × H pixels of px points, three greens through ordered dither. */
export const portraitPicture = (i: number, W: number, H: number, px: number): SkPicture => {
  const f = FRIENDS[i];
  const rec = Skia.PictureRecorder();
  const c = rec.beginRecording(Skia.XYWHRect(0, 0, W * px, H * px));
  const cols = ['#0a1c08', '#4f9a2e', '#d9ff9e'];
  const p = Skia.Paint();
  const sm = (e0: number, e1: number, x: number) => {
    const k = Math.min(1, Math.max(0, (x - e0) / (e1 - e0)));
    return k * k * (3 - 2 * k);
  };
  const ell = (u: number, v: number, cx: number, cy: number, rx: number, ry: number) => ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2;
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      const u = (x + 0.5) / W - 0.5;
      const v = (y + 0.5) / H;
      // background, lighter at the top
      let L = 0.12 + 0.22 * (1 - v);
      // shoulders and neck
      if (ell(u, v, 0, 1.08, 0.47, 0.34) < 1) L = 0.26 + 0.28 * sm(0.3, -0.3, u);
      if (Math.abs(u) < 0.075 && v > 0.6 && v < 0.84) L = 0.5 - 0.25 * sm(-0.02, 0.07, u);
      // the face, lit from the left
      const face = ell(u, v, 0, 0.42, 0.19, 0.25);
      if (face < 1) {
        L = 0.92 - 0.55 * sm(-0.12, 0.2, u) - 0.12 * sm(0.45, 0.65, v);
        if (ell(u, v, 0.025, 0.5, 0.025, 0.04) < 1) L -= 0.25; // nose shadow
        if (Math.abs(v - 0.575) < 0.012 && Math.abs(u) < 0.055) L = 0.22; // mouth
        if (f.glasses) {
          if ((ell(u, v, -0.075, 0.41, 0.07, 0.04) < 1 || ell(u, v, 0.075, 0.41, 0.07, 0.04) < 1) || (Math.abs(v - 0.4) < 0.01 && Math.abs(u) < 0.03)) L = 0.03;
          if (ell(u, v, -0.095, 0.4, 0.015, 0.012) < 1) L = 0.95;
        } else if (ell(u, v, -0.072, 0.41, 0.026, 0.018) < 1 || ell(u, v, 0.072, 0.41, 0.026, 0.018) < 1) L = 0.06; // eyes
        if (!f.glasses && Math.abs(v - 0.36) < 0.01 && Math.abs(Math.abs(u) - 0.075) < 0.04) L = 0.15; // brows
      }
      // hair
      const crown = ell(u, v, 0, 0.36, 0.22, 0.3) < 1 && v < 0.3 - 0.06 * Math.cos(u * 9);
      if (f.hair === 'bob' && (crown || (Math.abs(u) > 0.15 && Math.abs(u) < 0.25 && v > 0.2 && v < 0.6 && face > 0.75))) L = 0.07 + 0.25 * sm(0.18, 0.12, Math.abs(u + 0.06)) * (v < 0.3 ? 1 : 0);
      if (f.hair === 'short' && ell(u, v, 0, 0.36, 0.21, 0.28) < 1 && v < 0.24) L = 0.1;
      if (f.hair === 'bun' && (ell(u, v, 0, 0.1, 0.085, 0.07) < 1 || (ell(u, v, 0, 0.36, 0.21, 0.28) < 1 && v < 0.26))) L = 0.09 + 0.2 * sm(0.0, -0.1, u);
      const d = BAYER[(y % 4) * 4 + (x % 4)] / 16;
      const q = L * 2 + d - 0.5;
      p.setColor(Skia.Color(q < 0.5 ? cols[0] : q < 1.5 ? cols[1] : cols[2]));
      c.drawRect(Skia.XYWHRect(x * px, y * px, px + 0.35, px + 0.35), p);
    }
  }
  return rec.finishRecordingAsPicture();
};
