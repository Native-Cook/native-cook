/**
 * The room and the receiver, in one canvas: a dark wall with a warm pool of light, the coiled cord (its far half-turns
 * in shadow, its near ones catching the light) and the ivory bakelite handset, shaded and glossed by the same light
 * from the upper left, with a soft shadow on the wall behind it. The call screen's type and buttons sit on top as views.
 */
import {
  BlurMask, Circle, Group, LinearGradient, Path, RadialGradient, Rect, Skia, vec, type SkPath, type Transforms3d,
} from '@shopify/react-native-skia';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import type { Geometry } from './call-sim';
import { COLORS, CORD, HANDSET } from './theme';

export type CallValues = {
  coilBack: DerivedValue<SkPath>;
  coilFront: DerivedValue<SkPath>;
  handset: DerivedValue<Transforms3d>;
  shadow: DerivedValue<Transforms3d>;
  lightX: SharedValue<number>;
  lightY: SharedValue<number>;
  conn: SharedValue<number>;
};

/** The receiver's outline: two round cups joined by a handle that narrows to its waist, one smooth path. */
function outline(): SkPath {
  const { cupR: r, topCupY: t, bottomCupY: b, waist: w } = HANDSET;
  const a = (40 * Math.PI) / 180;
  const ex = r * Math.cos(a);
  const ey = r * Math.sin(a);
  const mid = (t + b) / 2;
  const p = Skia.PathBuilder.Make();
  p.moveTo(ex, t + ey);
  p.cubicTo(w + 1, t + ey + 14, w, mid - 22, w, mid);
  p.cubicTo(w, mid + 22, w + 1, b - ey - 14, ex, b - ey);
  p.arcToOval(Skia.XYWHRect(-r, b - r, 2 * r, 2 * r), -40, 260, false);
  p.cubicTo(-w - 1, b - ey - 14, -w, mid + 22, -w, mid);
  p.cubicTo(-w, mid - 22, -w - 1, t + ey + 14, -ex, t + ey);
  p.arcToOval(Skia.XYWHRect(-r, t - r, 2 * r, 2 * r), 140, 260, false);
  p.close();
  return p.build();
}

/** The gloss: a long soft streak down the handle's lit side. */
function gloss(): SkPath {
  const { topCupY: t, bottomCupY: b, waist: w } = HANDSET;
  const p = Skia.PathBuilder.Make();
  const mid = (t + b) / 2;
  p.moveTo(-w + 4, t + 34);
  p.cubicTo(-w + 2.5, mid - 20, -w + 2.5, mid + 20, -w + 4, b - 34);
  p.cubicTo(-w + 6.5, mid + 20, -w + 6.5, mid - 20, -w + 4, t + 34);
  p.close();
  return p.build();
}

/** A crescent of light on each cup's upper left, stroked. */
function crescents(): SkPath {
  const { cupR: r, topCupY: t, bottomCupY: b } = HANDSET;
  const p = Skia.PathBuilder.Make();
  for (const cy of [t, b]) p.addArc(Skia.XYWHRect(-r + 3.5, cy - r + 3.5, 2 * r - 7, 2 * r - 7), 200, 58);
  return p.build();
}

/** The earpiece's holes (bottom cup) and the mouthpiece's (top cup): rings of small dark dots. */
function holes(): SkPath {
  const p = Skia.PathBuilder.Make();
  const ring = (cy: number, rr: number, n: number, d: number, rot = 0) => {
    for (let k = 0; k < n; k++) {
      const a = rot + (2 * Math.PI * k) / n;
      p.addCircle(Math.cos(a) * rr, cy + Math.sin(a) * rr, d);
    }
  };
  p.addCircle(0, HANDSET.topCupY, 1.6);
  ring(HANDSET.topCupY, 6, 8, 1.3);
  ring(HANDSET.topCupY, 11.5, 14, 1.3, 0.2);
  p.addCircle(0, HANDSET.bottomCupY, 2.2);
  ring(HANDSET.bottomCupY, 8, 6, 1.9, 0.5);
  return p.build();
}

const OUTLINE = outline();
const GLOSS = gloss();
const CRESCENTS = crescents();
const HOLES = holes();

function Cup({ cy }: { cy: number }) {
  const r = HANDSET.inner;
  return (
    <Group>
      {/* The recessed face: darker toward the light (its rim shades it), lighter on the far side. */}
      <Circle cx={0} cy={cy} r={r}>
        <LinearGradient start={vec(-r, cy - r)} end={vec(r, cy + r)} colors={[COLORS.cupLo, COLORS.ivory, COLORS.cupHi]} positions={[0, 0.55, 1]} />
      </Circle>
      <Circle cx={0} cy={cy} r={r} style="stroke" strokeWidth={1.2} color={COLORS.ivoryEdge} />
      <Circle cx={0} cy={cy} r={r + 1.4} style="stroke" strokeWidth={1.4}>
        <LinearGradient start={vec(-r, cy - r)} end={vec(r, cy + r)} colors={['rgba(255,255,255,0.9)', 'rgba(255,255,255,0)', 'rgba(255,255,255,0.25)']} positions={[0, 0.5, 1]} />
      </Circle>
    </Group>
  );
}

export function CallScene({ g, v }: { g: Geometry; v: CallValues }) {
  const { w, h, s } = g;
  const lightC = useDerivedValue(() => vec(v.lightX.value - 40 * s, v.lightY.value - 80 * s));
  const lightColors = useDerivedValue(() => {
    const k = v.conn.value;
    // In a call the pool of light cools a little; it is the same lamp.
    const a = 0.2 - 0.06 * k;
    return [`rgba(255,${Math.round(214 + 20 * k)},${Math.round(170 + 30 * k)},${a})`, COLORS.lightEdge];
  });
  const { cupR: r, topCupY: t, bottomCupY: b } = HANDSET;
  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h}>
        <LinearGradient start={vec(0, 0)} end={vec(0, h)} colors={[COLORS.pageTop, COLORS.page, COLORS.pageBottom]} positions={[0, 0.5, 1]} />
      </Rect>
      <Rect x={0} y={0} width={w} height={h}>
        <RadialGradient c={lightC} r={300 * s} colors={lightColors} />
      </Rect>

      {/* The receiver's shadow on the wall behind it: down and to the right, soft. */}
      <Group transform={v.shadow}>
        <Path path={OUTLINE} color={COLORS.shadow}>
          <BlurMask blur={14} style="normal" />
        </Path>
      </Group>

      {/* The cord: the far half-turns first, then the near ones over them, each a shaded tube. */}
      <Path path={v.coilBack} style="stroke" strokeWidth={CORD.tube * s} strokeCap="round" strokeJoin="round" color={COLORS.cordBack} />
      <Path path={v.coilFront} style="stroke" strokeWidth={(CORD.tube + 0.8) * s} strokeCap="round" strokeJoin="round" color={COLORS.cordEdge} />
      <Path path={v.coilFront} style="stroke" strokeWidth={CORD.tube * s} strokeCap="round" strokeJoin="round" color={COLORS.cordFront} />
      <Group transform={[{ translateX: -0.9 * s }, { translateY: -0.9 * s }]}>
        <Path path={v.coilFront} style="stroke" strokeWidth={1.1 * s} strokeCap="round" color={COLORS.cordHi} />
      </Group>

      <Group transform={v.handset}>
        {/* Ivory bakelite, lit from the upper left. */}
        <Path path={OUTLINE}>
          <LinearGradient start={vec(-r, 0)} end={vec(r, b + r)} colors={[COLORS.ivoryHi, COLORS.ivory, COLORS.ivoryMid, COLORS.ivoryLo]} positions={[0, 0.3, 0.7, 1]} />
        </Path>
        {/* The far edge turns away from the light. */}
        <Path path={OUTLINE} style="stroke" strokeWidth={6}>
          <LinearGradient start={vec(-r, 0)} end={vec(r, 0)} colors={['rgba(120,96,60,0)', 'rgba(120,96,60,0)', 'rgba(110,88,56,0.35)']} positions={[0, 0.55, 1]} />
          <BlurMask blur={3} style="inner" />
        </Path>
        <Path path={OUTLINE} style="stroke" strokeWidth={1} color={COLORS.ivoryEdge} />
        <Cup cy={t} />
        <Cup cy={b} />
        <Path path={HOLES} color={COLORS.hole} />
        <Path path={GLOSS} color={COLORS.specular}>
          <BlurMask blur={2.2} style="normal" />
        </Path>
        <Path path={CRESCENTS} style="stroke" strokeWidth={3} strokeCap="round" color={COLORS.specular}>
          <BlurMask blur={1.6} style="normal" />
        </Path>
      </Group>
    </Group>
  );
}
