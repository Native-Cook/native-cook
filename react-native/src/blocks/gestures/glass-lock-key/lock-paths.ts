/** Static paths of the padlock (origin: the face's top-left) and the key (origin: its tip), built once. */
import { FillType, Skia, type SkPath } from '@shopify/react-native-skia';

import { KEY, LOCK } from './theme';

export type LockPaths = { face: SkPath; faceInner: SkPath; shackle: SkPath; ring: SkPath; blade: SkPath };

function facePath(inset: number): SkPath {
  const F = LOCK.face - inset * 2;
  const H = LOCK.height - inset * 2;
  const R = Math.max(4, LOCK.radius - inset);
  const b = Skia.PathBuilder.Make();
  // Flat top with round corners, a round belly below, as the source's padlock.
  b.moveTo(inset + R, inset);
  b.lineTo(inset + F - R, inset);
  b.quadTo(inset + F, inset, inset + F, inset + R);
  b.lineTo(inset + F, inset + H * 0.6);
  b.cubicTo(inset + F, inset + H * 0.9, inset + F * 0.78, inset + H, inset + F / 2, inset + H);
  b.cubicTo(inset + F * 0.22, inset + H, inset, inset + H * 0.9, inset, inset + H * 0.6);
  b.lineTo(inset, inset + R);
  b.quadTo(inset, inset, inset + R, inset);
  b.close();
  return b.build();
}

/** The shackle's centreline: a short free leg on the left, the long hinged leg on the right. */
function shacklePath(): SkPath {
  const half = LOCK.opening / 2 + LOCK.shackle / 2;
  const cx = LOCK.face / 2;
  const top = -LOCK.shackleRise + LOCK.shackle / 2;
  const arcY = top + half;
  const b = Skia.PathBuilder.Make();
  b.moveTo(cx - half, LOCK.shortLeg);
  b.lineTo(cx - half, arcY);
  b.arcToOval({ x: cx - half, y: top, width: half * 2, height: half * 2 }, 180, 180, false);
  b.lineTo(cx + half, LOCK.longLeg);
  return b.build();
}

function ringPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  const cy = KEY.blade + KEY.ring / 2 - 2;
  b.addCircle(0, cy, KEY.ring / 2);
  b.addCircle(0, cy, KEY.ring / 2 - KEY.rim);
  b.setFillType(FillType.EvenOdd);
  return b.build();
}

function bladePath(): SkPath {
  const b = Skia.PathBuilder.Make();
  const w = KEY.shaft / 2;
  // Shaft with a rounded tip, the bit with two notches, and the collar against the bow.
  b.moveTo(-w, KEY.blade);
  b.lineTo(-w, 4);
  b.quadTo(-w, 0, 0, 0);
  b.quadTo(w, 0, w, 4);
  b.lineTo(w, KEY.bitFromTip);
  b.lineTo(w + KEY.bitWidth, KEY.bitFromTip);
  b.lineTo(w + KEY.bitWidth, KEY.bitFromTip + 9);
  b.lineTo(w + KEY.bitWidth - 6, KEY.bitFromTip + 9);
  b.lineTo(w + KEY.bitWidth - 6, KEY.bitFromTip + 15);
  b.lineTo(w + KEY.bitWidth, KEY.bitFromTip + 15);
  b.lineTo(w + KEY.bitWidth, KEY.bitFromTip + KEY.bitHeight);
  b.lineTo(w, KEY.bitFromTip + KEY.bitHeight);
  b.lineTo(w, KEY.blade);
  b.close();
  b.addRRect({ rect: { x: -KEY.collar / 2, y: KEY.blade - 8, width: KEY.collar, height: 8 }, rx: 3, ry: 3 });
  return b.build();
}

export function makeLockPaths(): LockPaths {
  return { face: facePath(0), faceInner: facePath(5), shackle: shacklePath(), ring: ringPath(), blade: bladePath() };
}
