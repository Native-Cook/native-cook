/** Canvas: lilac room, the clay gumball machine, its pile of gumballs, the knob, the capsule and the bag. */
import {
  BlurMask, Circle, Group, LinearGradient, Oval, Path, RadialGradient, Rect, RoundedRect, Skia, vec,
  type SkPath, type SkRect, type Transforms3d,
} from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import { ballColor, type Geometry } from './gumball-sim';
import { BAG, CAPSULE, COLORS, MACHINE } from './theme';

export type GumballValues = {
  balls: SharedValue<number[]>; // x, y pairs
  knob: DerivedValue<Transforms3d>;
  capsule: DerivedValue<Transforms3d>;
  capsuleOpacity: SharedValue<number>;
  seamRy: SharedValue<number>; // the capsule's seam as an ellipse: vertical radius (signed)
  flip: SharedValue<number>; // 1 when the white half faces down
  inDoor: SharedValue<number>;
  shellTop: DerivedValue<Transforms3d>;
  shellBottom: DerivedValue<Transforms3d>;
  shellOpacity: SharedValue<number>;
  burst: SharedValue<number>;
  scrim: SharedValue<number>;
  bag: DerivedValue<Transforms3d>;
};

type Props = { g: Geometry; radii: number[]; v: GumballValues };

const CX = 201;
const R = CAPSULE.r;

function basePath(): SkPath {
  const b = Skia.PathBuilder.Make();
  const t = MACHINE.baseTopWidth / 2;
  const bo = MACHINE.baseBottomWidth / 2;
  const r = MACHINE.baseRadius;
  b.moveTo(CX, MACHINE.baseTop);
  b.arcToTangent(CX + t, MACHINE.baseTop, CX + bo, MACHINE.baseBottom, r * 0.6);
  b.arcToTangent(CX + bo, MACHINE.baseBottom, CX - bo, MACHINE.baseBottom, r);
  b.arcToTangent(CX - bo, MACHINE.baseBottom, CX - t, MACHINE.baseTop, r);
  b.arcToTangent(CX - t, MACHINE.baseTop, CX, MACHINE.baseTop, r * 0.6);
  b.close();
  return b.build();
}

function wavesPath(): SkPath {
  // Wide diagonal waves as in the source's striped base.
  const b = Skia.PathBuilder.Make();
  for (let k = -3; k < 6; k++) {
    const x0 = CX - 160 + k * 44;
    b.moveTo(x0, MACHINE.baseTop - 20);
    for (let i = 1; i <= 12; i++) {
      const y = MACHINE.baseTop - 20 + i * 20;
      b.lineTo(x0 + i * 14 + Math.sin(i * 0.9) * 8, y);
    }
  }
  return b.build();
}

function doorPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  const w = MACHINE.doorWidth / 2;
  const top = MACHINE.doorTop;
  const bottom = top + MACHINE.doorHeight;
  const r = MACHINE.doorRadius;
  b.moveTo(CX - w, bottom - 8);
  b.lineTo(CX - w, top + r);
  b.arcToTangent(CX - w, top, CX, top, r);
  b.arcToTangent(CX + w, top, CX + w, top + r, r);
  b.lineTo(CX + w, bottom - 8);
  b.arcToTangent(CX + w, bottom, CX, bottom, 8);
  b.arcToTangent(CX - w, bottom, CX - w, bottom - 8, 8);
  b.close();
  return b.build();
}

function lidPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  const w = MACHINE.lidWidth / 2;
  const top = MACHINE.lidTop + 10;
  const bottom = MACHINE.lidTop + MACHINE.lidHeight;
  b.moveTo(CX - w, bottom);
  b.cubicTo(CX - w + 4, top + 6, CX - w * 0.4, top, CX, top);
  b.cubicTo(CX + w * 0.4, top, CX + w - 4, top + 6, CX + w, bottom);
  b.close();
  return b.build();
}

function globeClip(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.addCircle(CX, MACHINE.globeY, MACHINE.globeR);
  const cut = Skia.PathBuilder.Make();
  cut.addRect({ x: 0, y: MACHINE.globeY - MACHINE.globeR - 10, width: 402, height: MACHINE.collarY + 4 - (MACHINE.globeY - MACHINE.globeR - 10) });
  return Skia.Path.MakeFromOp(b.build(), cut.build(), 1 /* intersect */) ?? b.build();
}

function bagPath(): SkPath {
  const b = Skia.PathBuilder.Make();
  const s = BAG.size;
  b.addRRect({ rect: { x: -s / 2, y: -s / 2 + 8, width: s, height: s - 8 }, rx: 12, ry: 12 });
  return b.build();
}

function handlePath(): SkPath {
  const b = Skia.PathBuilder.Make();
  b.moveTo(-9, -BAG.size / 2 + 10);
  b.cubicTo(-9, -BAG.size / 2 - 4, 9, -BAG.size / 2 - 4, 9, -BAG.size / 2 + 10);
  return b.build();
}

export function GumballScene({ g, radii, v }: Props) {
  const { w, h, s, oy } = g;
  const paths = useMemo(() => ({
    base: basePath(), waves: wavesPath(), door: doorPath(), lid: lidPath(), globe: globeClip(), bag: bagPath(), handle: handlePath(),
  }), []);
  const layout: Transforms3d = [{ translateY: oy }, { scale: s }];
  const tableY = oy + MACHINE.tableY * s;
  const frontY = oy + MACHINE.tableFrontY * s;
  const notInDoor = useDerivedValue(() => 1 - v.inDoor.value);

  return (
    <Group>
      <Rect x={0} y={0} width={w} height={tableY}>
        <LinearGradient start={vec(0, 0)} end={vec(0, tableY)} colors={[COLORS.wallTop, COLORS.wallBottom]} />
      </Rect>
      {/* Two soft diagonal beams of window light, as in the source. */}
      <Group transform={[{ translateX: w * 0.1 }, { rotate: -0.5 }]}>
        <Rect x={-w * 0.2} y={-h * 0.2} width={w * 0.22} height={h * 1.2}>
          <LinearGradient start={vec(-w * 0.2, 0)} end={vec(w * 0.02, 0)} colors={[COLORS.beamFar, COLORS.beam, COLORS.beamFar]} />
        </Rect>
        <Rect x={w * 0.18} y={-h * 0.2} width={w * 0.14} height={h * 1.2}>
          <LinearGradient start={vec(w * 0.18, 0)} end={vec(w * 0.32, 0)} colors={[COLORS.beamFar, COLORS.beam, COLORS.beamFar]} />
        </Rect>
      </Group>
      <Rect x={0} y={tableY} width={w} height={frontY - tableY}>
        <LinearGradient start={vec(0, tableY)} end={vec(0, frontY)} colors={[COLORS.wallBottom, COLORS.tableTop]} />
      </Rect>
      <Rect x={0} y={frontY} width={w} height={h - frontY} color={COLORS.tableFront} />
      <Rect x={0} y={frontY} width={w} height={1.5 * s} color={COLORS.tableEdge} />

      <Group transform={layout}>
        <Oval x={CX - 140} y={MACHINE.baseBottom - 12} width={280} height={30} color={COLORS.shadow}>
          <BlurMask blur={12} style="normal" />
        </Oval>
        {/* Loose gumballs on the table, as the source's scattered fruit. */}
        {LOOSE.map((b) => (
          <Group key={`${b.x}`}>
            <Oval x={b.x - b.r} y={b.y + b.r - 5} width={b.r * 2} height={10} color={COLORS.shadow}>
              <BlurMask blur={4} style="normal" />
            </Oval>
            <Circle cx={b.x} cy={b.y} r={b.r}>
              <RadialGradient c={vec(b.x - b.r * 0.35, b.y - b.r * 0.4)} r={b.r * 1.4} colors={[lighten(b.c), b.c, darken(b.c)]} positions={[0, 0.45, 1]} />
            </Circle>
            <Circle cx={b.x - b.r * 0.38} cy={b.y - b.r * 0.42} r={b.r * 0.18} color="rgba(255,255,255,0.8)" />
          </Group>
        ))}

        <Path path={paths.base}>
          <LinearGradient start={vec(CX - 112, 0)} end={vec(CX + 112, 0)} colors={[COLORS.bodyDeep, COLORS.body, COLORS.bodyLight, COLORS.body, COLORS.bodyDeep]} positions={[0, 0.25, 0.45, 0.7, 1]} />
        </Path>
        <Group clip={paths.base}>
          <Path path={paths.waves} style="stroke" strokeWidth={16} color={COLORS.wave} strokeCap="round" strokeJoin="round" />
        </Group>
        <Path path={paths.base} style="stroke" strokeWidth={1.5} color="rgba(255,255,255,0.35)" />

        <RoundedRect x={CX - MACHINE.plateSize / 2} y={MACHINE.plateY - MACHINE.plateSize / 2} width={MACHINE.plateSize} height={MACHINE.plateSize} r={MACHINE.plateRadius}>
          <LinearGradient start={vec(0, MACHINE.plateY - 36)} end={vec(0, MACHINE.plateY + 36)} colors={[COLORS.bodyLight, COLORS.plate]} />
        </RoundedRect>
        <Path path={paths.door}>
          <LinearGradient start={vec(0, MACHINE.doorTop)} end={vec(0, MACHINE.doorTop + MACHINE.doorHeight)} colors={[COLORS.doorDeep, COLORS.door]} />
        </Path>
        <Path path={paths.door} style="stroke" strokeWidth={3} color="rgba(60,20,90,0.18)" />

        <RoundedRect x={CX - MACHINE.collarWidth / 2} y={MACHINE.collarY} width={MACHINE.collarWidth} height={MACHINE.collarHeight} r={MACHINE.collarHeight / 2}>
          <LinearGradient start={vec(CX - 102, 0)} end={vec(CX + 102, 0)} colors={[COLORS.bodyDeep, COLORS.bodyLight, COLORS.body]} />
        </RoundedRect>

        {/* Globe: faint tint, then the balls, then shade and reflections on the glass. */}
        <Path path={paths.globe} color={COLORS.glassTint} />
      </Group>

      <Group>
        {radii.map((r, i) => <Ball key={i} index={i} r={r} balls={v.balls} />)}
      </Group>

      <Group transform={layout}>
        <Group clip={paths.globe}>
          <Rect x={CX - MACHINE.globeR} y={MACHINE.globeY} width={MACHINE.globeR * 2} height={MACHINE.globeR}>
            <LinearGradient start={vec(0, MACHINE.globeY)} end={vec(0, MACHINE.collarY)} colors={['rgba(120,70,150,0)', COLORS.glassShade]} />
          </Rect>
          <Path path={ARC_BIG} style="stroke" strokeWidth={10} color={COLORS.reflect} strokeCap="round">
            <BlurMask blur={4} style="normal" />
          </Path>
          <Path path={ARC_SMALL} style="stroke" strokeWidth={4} color={COLORS.reflect} strokeCap="round" />
        </Group>
        <Circle cx={CX} cy={MACHINE.globeY} r={MACHINE.globeR} style="stroke" strokeWidth={2.5} color={COLORS.glassRim} opacity={0.85} />

        <Path path={paths.lid}>
          <LinearGradient start={vec(CX - 94, 0)} end={vec(CX + 94, 0)} colors={[COLORS.bodyDeep, COLORS.bodyLight, COLORS.body, COLORS.bodyDeep]} positions={[0, 0.35, 0.7, 1]} />
        </Path>
        <RoundedRect x={CX - MACHINE.lidWidth / 2 - 4} y={MACHINE.lidTop + MACHINE.lidHeight - 8} width={MACHINE.lidWidth + 8} height={12} r={6}>
          <LinearGradient start={vec(CX - 98, 0)} end={vec(CX + 98, 0)} colors={[COLORS.bodyDeep, COLORS.bodyLight, COLORS.body]} />
        </RoundedRect>
        <Circle cx={CX} cy={MACHINE.lidTop + 4} r={MACHINE.lidKnob}>
          <RadialGradient c={vec(CX - 4, MACHINE.lidTop)} r={MACHINE.lidKnob * 1.4} colors={[COLORS.bodyLight, COLORS.body, COLORS.bodyDeep]} />
        </Circle>
      </Group>

      {/* The knob turns with the crank. */}
      <Group transform={v.knob}>
        <Circle cx={0} cy={2} r={MACHINE.knobR + 1} color="rgba(80,30,100,0.25)">
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Circle cx={0} cy={0} r={MACHINE.knobR}>
          <RadialGradient c={vec(-7, -8)} r={MACHINE.knobR * 1.5} colors={[COLORS.knobLight, COLORS.knob, COLORS.knobDeep]} positions={[0, 0.5, 1]} />
        </Circle>
        <RoundedRect x={-MACHINE.slotWidth / 2} y={-MACHINE.slotHeight / 2} width={MACHINE.slotWidth} height={MACHINE.slotHeight} r={MACHINE.slotHeight / 2} color={COLORS.slot} />
        <RoundedRect x={-MACHINE.slotWidth / 2 + 2} y={-MACHINE.slotHeight / 2 + 1} width={MACHINE.slotWidth - 4} height={2} r={1} color="rgba(255,230,180,0.6)" />
      </Group>

      <Rect x={0} y={0} width={w} height={h} color={COLORS.scrim} opacity={v.scrim} />

      {/* Capsule: clipped to the door while it is inside, then rolling out toward the viewer. */}
      <Group transform={layout}>
        {/* In the door the capsule is clipped by the opening (a static clip, shown by opacity); out of it, it is free. */}
        <Group clip={paths.door} opacity={v.inDoor}>
          <CapsuleLayer v={v} oy={oy} s={s} />
        </Group>
        <Group opacity={notInDoor}>
          <CapsuleLayer v={v} oy={oy} s={s} />
        </Group>
      </Group>

      <Group transform={v.bag}>
        <Path path={paths.bag}>
          <LinearGradient start={vec(0, -BAG.size / 2)} end={vec(0, BAG.size / 2)} colors={[COLORS.bagLight, COLORS.bag]} />
        </Path>
        <Path path={paths.handle} style="stroke" strokeWidth={3.5} color={COLORS.bag} strokeCap="round" />
        <Circle cx={0} cy={6} r={5} color="rgba(255,255,255,0.75)" />
      </Group>
    </Group>
  );
}

/** The capsule and its burst, in screen space (the caller's group is in machine space). */
function CapsuleLayer({ v, oy, s }: { v: GumballValues; oy: number; s: number }) {
  return (
    <Group transform={[{ translateY: -oy / s }, { scale: 1 / s }]}>
      <Group opacity={v.burst}>
        <Group transform={v.shellBottom}>
          <Circle cx={0} cy={0} r={R * 3} color="rgba(255,255,255,0.9)">
            <BlurMask blur={20} style="normal" />
          </Circle>
        </Group>
      </Group>
      <Group transform={v.capsule} opacity={v.capsuleOpacity}>
        <Oval x={-R} y={R - 3} width={R * 2} height={7} color={COLORS.shadow}>
          <BlurMask blur={3} style="normal" />
        </Oval>
        <CapsuleBody v={v} />
      </Group>
      <Group opacity={v.shellOpacity}>
        <Group transform={v.shellTop}>
          <Path path={HALF_TOP} color={COLORS.capsuleTop} />
          <Path path={HALF_TOP} style="stroke" strokeWidth={1.2} color={COLORS.capsuleEdge} />
        </Group>
        <Group transform={v.shellBottom}>
          <Path path={HALF_BOTTOM}>
            <RadialGradient c={vec(-6, 2)} r={R * 1.4} colors={[COLORS.capsule, COLORS.capsuleDeep]} />
          </Path>
        </Group>
      </Group>
    </Group>
  );
}

/** The closed capsule: a pink sphere with a white translucent hemisphere whose seam rolls as it moves. */
function CapsuleBody({ v }: { v: GumballValues }) {
  const seam = useDerivedValue<SkRect>(() => {
    const ry = Math.max(0.5, Math.abs(v.seamRy.value));
    return { x: -R, y: -ry, width: R * 2, height: ry * 2 };
  });
  const front = useDerivedValue(() => (v.seamRy.value >= 0 ? 1 : 0));
  const back = useDerivedValue(() => 1 - front.value);
  const top = useDerivedValue(() => (v.flip.value === 1 ? COLORS.capsule : COLORS.capsuleTop));
  const bottom = useDerivedValue(() => (v.flip.value === 1 ? COLORS.capsuleTop : COLORS.capsule));
  // The band between the seam's line and its front half belongs to the hemisphere on the other side.
  const capT = useDerivedValue(() => (v.seamRy.value >= 0 ? top.value : bottom.value));
  return (
    <Group>
      <Group clip={DISC}>
        <Rect x={-R} y={-R} width={R * 2} height={R} color={top} />
        <Rect x={-R} y={0} width={R * 2} height={R} color={bottom} />
        {/* Static clips with switched opacity: animated clip paths are not redrawn reliably. */}
        <Group clip={LOWER_HALF} opacity={front}>
          <Oval rect={seam} color={capT} />
        </Group>
        <Group clip={UPPER_HALF} opacity={back}>
          <Oval rect={seam} color={capT} />
        </Group>
      </Group>
      <Circle cx={0} cy={0} r={R} style="stroke" strokeWidth={1.2} color={COLORS.capsuleEdge} />
      <Circle cx={-R * 0.4} cy={-R * 0.45} r={R * 0.2} color="rgba(255,255,255,0.85)" />
    </Group>
  );
}

function Ball({ index, r, balls }: { index: number; r: number; balls: SharedValue<number[]> }) {
  const t = useDerivedValue<Transforms3d>(() => [{ translateX: balls.value[index * 2] ?? -100 }, { translateY: balls.value[index * 2 + 1] ?? -100 }]);
  const c = ballColor(index);
  return (
    <Group transform={t}>
      <Circle cx={0} cy={0} r={r}>
        <RadialGradient c={vec(-r * 0.35, -r * 0.4)} r={r * 1.45} colors={[lighten(c), c, darken(c)]} positions={[0, 0.45, 1]} />
      </Circle>
      <Circle cx={-r * 0.38} cy={-r * 0.42} r={r * 0.17} color="rgba(255,255,255,0.75)" />
    </Group>
  );
}

function mix(hex: string, to: number, k: number) {
  const n = parseInt(hex.slice(1), 16);
  const ch = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((c) => Math.round(c + (to - c) * k));
  return `rgb(${ch[0]}, ${ch[1]}, ${ch[2]})`;
}
function lighten(hex: string) {
  return mix(hex, 255, 0.45);
}
function darken(hex: string) {
  return mix(hex, 40, 0.35);
}

const LOOSE = [
  { x: 56, y: 668, r: 20, c: '#FF9E7A' },
  { x: 92, y: 690, r: 15, c: '#5B3B6E' },
  { x: 330, y: 662, r: 17, c: '#E8547A' },
  { x: 356, y: 690, r: 21, c: '#FFD66B' },
];

function arc(cx: number, cy: number, r: number, from: number, sweep: number): SkPath {
  const b = Skia.PathBuilder.Make();
  b.addArc({ x: cx - r, y: cy - r, width: r * 2, height: r * 2 }, from, sweep);
  return b.build();
}
const ARC_BIG = arc(CX, MACHINE.globeY, MACHINE.globeR - 18, 200, 55);
const ARC_SMALL = arc(CX, MACHINE.globeY, MACHINE.globeR - 18, 262, 10);

function half(top: boolean): SkPath {
  const b = Skia.PathBuilder.Make();
  b.addArc({ x: -R, y: -R, width: R * 2, height: R * 2 }, top ? 180 : 0, 180);
  b.close();
  return b.build();
}
const HALF_TOP = half(true);
const HALF_BOTTOM = half(false);
const DISC = (() => {
  const b = Skia.PathBuilder.Make();
  b.addCircle(0, 0, R);
  return b.build();
})();
const UPPER_HALF = (() => {
  const b = Skia.PathBuilder.Make();
  b.addRect({ x: -R, y: -R, width: R * 2, height: R });
  return b.build();
})();
const LOWER_HALF = (() => {
  const b = Skia.PathBuilder.Make();
  b.addRect({ x: -R, y: 0, width: R * 2, height: R });
  return b.build();
})();

