// Cash Handoff Onboarding — the two hands and the stack of notes, drawn in Skia. Helpers above callers.
//
// A hand is flat illustration: a forearm, a palm, four fingers of two segments and a thumb, each a
// thick round-capped stroke over a slightly thicker dark outline. Its pose is one number, curl (0 open,
// 1 closed), which bends every finger joint, so a hand can close on the notes.
import { type SkCanvas, type SkFont, Skia } from '@shopify/react-native-skia';

const SKIN = '#8a5636';
const SHADE = '#6a3f26';
const LINE = '#2a170c';

const stroke = (w: number, col: string) => {
  'worklet';
  const p = Skia.Paint();
  p.setAntiAlias(true);
  p.setStyle(1);
  p.setStrokeWidth(w);
  p.setStrokeCap(1);
  p.setStrokeJoin(1);
  p.setColor(Skia.Color(col));
  return p;
};

/** A two-segment finger from (x, y) along angle a (radians), each joint bending by `bend`. */
const finger = (c: SkCanvas, x: number, y: number, a: number, l1: number, l2: number, bend: number, w: number, col: string) => {
  'worklet';
  const x1 = x + Math.cos(a) * l1;
  const y1 = y + Math.sin(a) * l1;
  const a2 = a + bend;
  const x2 = x1 + Math.cos(a2) * l2;
  const y2 = y1 + Math.sin(a2) * l2;
  const pb = Skia.PathBuilder.Make();
  pb.moveTo(x, y);
  pb.lineTo(x1, y1);
  pb.lineTo(x2, y2);
  const p = pb.build();
  c.drawPath(p, stroke(w + 3, LINE));
  c.drawPath(p, stroke(w, col));
};

/**
 * A hand reaching in, wrist at (x, y), pointing along `dir` (1 right, -1 left). `curl` closes the
 * fingers; `up` is 1 for a palm turned up (fingers curl upwards) and 0 for a hand seen from the back
 * with the fingers curling down. `layer` 0 draws the arm and palm, 1 the fingers (drawn over the notes).
 */
export const drawHand = (c: SkCanvas, x: number, y: number, dir: number, curl: number, up: number, layer: number) => {
  'worklet';
  c.save();
  c.translate(x, y);
  c.scale(dir, 1);
  const s = up > 0.5 ? -1 : 1; // which way the fingers curl
  if (layer === 0) {
    // forearm in from off screen, then the palm
    const arm = Skia.PathBuilder.Make();
    arm.moveTo(-260, -20);
    arm.lineTo(0, -22);
    arm.lineTo(0, 22);
    arm.lineTo(-260, 26);
    arm.close();
    const ap = Skia.Paint();
    ap.setAntiAlias(true);
    ap.setColor(Skia.Color(SKIN));
    const outline = stroke(3, LINE);
    const armPath = arm.build();
    c.drawPath(armPath, ap);
    c.drawPath(armPath, outline);
    const palm = Skia.RRectXY(Skia.XYWHRect(-6, -26, 52, 50), 20, 20);
    c.drawRRect(palm, ap);
    c.drawRRect(palm, outline);
    // a crease or knuckle shade
    const sh = Skia.Paint();
    sh.setAntiAlias(true);
    sh.setColor(Skia.Color(SHADE));
    c.drawOval(Skia.XYWHRect(8, up > 0.5 ? -8 : 2, 30, 12), sh);
    // the thumb
    const ta = (up > 0.5 ? -0.9 : 0.9) + curl * (up > 0.5 ? 0.5 : -0.5);
    finger(c, 14, up > 0.5 ? -18 : 18, ta, 20, 15, s * curl * 0.9, 13, SKIN);
  } else {
    // four fingers from the knuckles, the outer ones shorter
    for (let i = 0; i < 4; i++) {
      const fy = -18 + i * 12;
      const len = i === 0 || i === 3 ? 0.85 : 1;
      const base = (i - 1.5) * 0.06 + s * curl * 1.1;
      finger(c, 42, fy, base, 22 * len, 18 * len, s * curl * 1.4, 12, i % 2 === 0 ? SKIN : '#93603e');
    }
  }
  c.restore();
};

/** A stack of three banknotes centred on (x, y), rotated by rot (degrees), fanned by `fan`. */
export const drawCash = (c: SkCanvas, x: number, y: number, rot: number, fan: number, font: SkFont | null) => {
  'worklet';
  c.save();
  c.translate(x, y);
  c.rotate(rot, 0, 0);
  for (let i = 0; i < 3; i++) {
    c.save();
    c.rotate((i - 1) * (6 + 10 * fan), 0, 0);
    c.translate((i - 1) * 3, (i - 1) * -3);
    const W = 92;
    const H = 46;
    const bill = Skia.Paint();
    bill.setAntiAlias(true);
    bill.setColor(Skia.Color(i === 2 ? '#9bd48a' : '#86c574'));
    const rr = Skia.RRectXY(Skia.XYWHRect(-W / 2, -H / 2, W, H), 5, 5);
    c.drawRRect(rr, bill);
    c.drawRRect(rr, stroke(2.6, '#2f5a26'));
    const inner = stroke(1.4, '#4f8a42');
    c.drawRRect(Skia.RRectXY(Skia.XYWHRect(-W / 2 + 6, -H / 2 + 6, W - 12, H - 12), 3, 3), inner);
    if (i === 2) {
      const disc = Skia.Paint();
      disc.setAntiAlias(true);
      disc.setColor(Skia.Color('#c7ecb8'));
      c.drawCircle(0, 0, 13, disc);
      c.drawCircle(0, 0, 13, stroke(1.6, '#2f5a26'));
      if (font) {
        const t = Skia.Paint();
        t.setAntiAlias(true);
        t.setColor(Skia.Color('#2f5a26'));
        c.drawText('$', -5, 6, t, font);
      }
      c.drawCircle(-W / 2 + 14, 0, 4, disc);
      c.drawCircle(W / 2 - 14, 0, 4, disc);
    }
    c.restore();
  }
  c.restore();
};
