/**
 * Canvas: the dark vault with one light pooling from above, a solid brass padlock (cast body seen three-quarter, its
 * brushed face engraved with the maker's mark, a hardened steel shackle) and a nickel key. The light catches the
 * brass on each detent and sweeps across it when the shackle springs.
 */
import {
  BlurMask, Circle, Group, Line, LinearGradient, Oval, Path, RadialGradient, Rect, RoundedRect, Text, useFont, vec,
  type Transforms3d,
} from '@shopify/react-native-skia';
import { useDerivedValue, type DerivedValue, type SharedValue } from 'react-native-reanimated';

import type { Geometry } from './lock-sim';
import type { LockPaths } from './lock-paths';
import { COLORS, KEY, LOCK } from './theme';

export type LockValues = {
  rig: DerivedValue<Transforms3d>; // lift and shrink while the sheet is up
  lock: DerivedValue<Transforms3d>; // bounce and shake
  shackle: DerivedValue<Transforms3d>;
  key: DerivedValue<Transforms3d>;
  turnCos: SharedValue<number>;
  turnSin: SharedValue<number>;
  keyFront: DerivedValue<number>;
  keyBehind: SharedValue<number>;
  holeGlow: SharedValue<number>;
  glint: SharedValue<number>;
  keyShadow: DerivedValue<Transforms3d>;
};

type Props = { g: Geometry; paths: LockPaths; v: LockValues };

const F = LOCK.face;
const H = LOCK.height;
const RING_Y = KEY.blade + KEY.ring / 2 - 2;
const MARK = 'HALDEN';
const SIZE = '40 MM · HARDENED';

export function LockScene({ g, paths, v }: Props) {
  const { w, h } = g;
  const mark = useFont(require('./assets/Inter-SemiBold.ttf'), 13);
  const small = useFont(require('./assets/Inter-Medium.ttf'), 7.5);
  const glintX = useDerivedValue(() => -60 + (F + 120) * v.glint.value);
  const glintO = useDerivedValue(() => (v.glint.value > 0 && v.glint.value < 1 ? Math.sin(Math.PI * v.glint.value) : 0));
  const poolY = g.lockTop + (LOCK.height * g.s) / 2;
  // Tracked capitals, set one letter at a time so the spacing is the engraver's, not the font's.
  const markW = mark ? MARK.split('').reduce((a, c) => a + mark.getTextWidth(c), 0) + (MARK.length - 1) * 4 : 0;

  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h}>
        <LinearGradient start={vec(0, 0)} end={vec(0, h)} colors={[COLORS.bgTop, COLORS.bgBottom]} />
      </Rect>
      <Rect x={0} y={0} width={w} height={h}>
        <RadialGradient c={vec(w / 2, poolY - 40 * g.s)} r={w * 0.85} colors={[COLORS.pool, 'rgba(0,0,0,0)']} />
      </Rect>

      <Group transform={v.rig}>
        {/* Contact shadows on the vault floor. */}
        <Oval x={g.faceX + 6 * g.s} y={g.lockBottom + 30 * g.s} width={(F + 10) * g.s} height={20 * g.s} color={COLORS.shadow}>
          <BlurMask blur={16} style="normal" />
        </Oval>
        <Group transform={v.keyShadow}>
          <Oval x={-24} y={0} width={48} height={10} color={COLORS.shadow} opacity={0.8}>
            <BlurMask blur={8} style="normal" />
          </Oval>
        </Group>

        {/* In the lock the key sits behind the body, which hides the blade; the bow shows below. */}
        <Group opacity={v.keyBehind}>
          <KeyArt v={v} paths={paths} />
        </Group>

        <Group transform={v.lock}>
          {/* Shackle behind the body: its legs go in. Hardened steel, lit from above. */}
          <Group transform={v.shackle}>
            <Path path={paths.shackle} style="stroke" strokeWidth={LOCK.shackle} strokeCap="butt">
              <LinearGradient start={vec(0, -LOCK.shackleRise)} end={vec(F, LOCK.longLeg)} colors={[COLORS.steelHigh, COLORS.steel, COLORS.steelDeep]} positions={[0, 0.45, 1]} />
            </Path>
            <Group transform={[{ translateX: -8 }, { translateY: -4 }]}>
              <Path path={paths.shackle} style="stroke" strokeWidth={3} color={COLORS.steelEdge} opacity={0.8} strokeCap="butt">
                <BlurMask blur={1.2} style="normal" />
              </Path>
            </Group>
            <Group transform={[{ translateX: 9 }, { translateY: 2 }]}>
              <Path path={paths.shackle} style="stroke" strokeWidth={2} color="rgba(20,24,28,0.35)" strokeCap="butt">
                <BlurMask blur={1.5} style="normal" />
              </Path>
            </Group>
          </Group>

          {/* The cast body's side, seen three-quarter: offset copies in the side's own shade. */}
          {SIDE_STEPS.map((k) => (
            <Group key={k} transform={[{ translateX: LOCK.side * k }, { translateY: -LOCK.side * 0.5 * k }]}>
              <Path path={paths.face}>
                <LinearGradient start={vec(0, 0)} end={vec(0, H)} colors={[COLORS.brassSide, COLORS.brassSideDeep]} />
              </Path>
            </Group>
          ))}
          {/* The face: brushed brass, a broad highlight upper left, darker toward the floor. */}
          <Path path={paths.face}>
            <LinearGradient start={vec(0, 0)} end={vec(F * 0.4, H)} colors={[COLORS.brassHigh, COLORS.brass, COLORS.brassDeep]} positions={[0, 0.45, 1]} />
          </Path>
          <Path path={paths.face}>
            <RadialGradient c={vec(F * 0.3, H * 0.22)} r={F * 0.7} colors={['rgba(255,240,200,0.55)', 'rgba(255,240,200,0)']} />
          </Path>
          <Group clip={paths.face}>
            {BRUSH.map((y) => (
              <Line key={y} p1={vec(0, y)} p2={vec(F, y + 0.6)} color={(y / 3) % 2 === 0 ? 'rgba(255,240,205,0.10)' : 'rgba(80,50,12,0.08)'} strokeWidth={0.6} />
            ))}
            <Rect x={glintX} y={-20} width={30} height={H + 40} color={COLORS.glint} opacity={glintO} transform={[{ skewX: -0.35 }]}>
              <BlurMask blur={9} style="normal" />
            </Rect>
          </Group>
          {/* The maker's mark, engraved: the cut in shadow, its lower lip catching light. */}
          {mark && MARK.split('').map((c, i) => {
            const x = F / 2 - markW / 2 + MARK.slice(0, i).split('').reduce((a, d) => a + mark.getTextWidth(d) + 4, 0);
            return (
              <Group key={i}>
                <Text text={c} x={x} y={H * 0.42 + 0.8} font={mark} color={COLORS.engraveLight} />
                <Text text={c} x={x} y={H * 0.42} font={mark} color={COLORS.engrave} />
              </Group>
            );
          })}
          {small && <Text text={SIZE} x={F / 2 - small.getTextWidth(SIZE) / 2} y={H * 0.42 + 16} font={small} color={COLORS.engrave} />}
          {/* Edges: a bright bevel on top, the faint inner chamfer. */}
          <Path path={paths.face} style="stroke" strokeWidth={1.4}>
            <LinearGradient start={vec(0, 0)} end={vec(0, H)} colors={[COLORS.brassEdge, 'rgba(255,236,190,0.15)', 'rgba(60,36,8,0.4)']} />
          </Path>
          <Path path={paths.faceInner} style="stroke" strokeWidth={0.8} color="rgba(255,236,190,0.22)" />

          {/* The keyhole's plug on the underside, warm when the key comes near and as it turns. */}
          <Circle cx={F / 2} cy={H + LOCK.plugHeight / 2} r={LOCK.glowRadius} color={COLORS.glow} opacity={v.holeGlow}>
            <BlurMask blur={16} style="normal" />
          </Circle>
          <RoundedRect x={F / 2 - LOCK.plugWidth / 2} y={H - 3} width={LOCK.plugWidth} height={LOCK.plugHeight + 3} r={4}>
            <LinearGradient start={vec(F / 2 - LOCK.plugWidth / 2, 0)} end={vec(F / 2 + LOCK.plugWidth / 2, 0)} colors={[COLORS.brassDeep, COLORS.plug, COLORS.brassDeep]} />
          </RoundedRect>
          <Rect x={F / 2 - LOCK.slot / 2} y={H + 1} width={LOCK.slot} height={LOCK.plugHeight - 2} color={COLORS.slot} />
        </Group>

        {/* Out of the lock the key is drawn in front of it. */}
        <Group opacity={v.keyFront}>
          <KeyArt v={v} paths={paths} />
        </Group>
      </Group>
    </Group>
  );
}

/** A nickel key: its bow turns about the long axis, the face narrowing to cos θ and its edge showing by sin θ. */
function KeyArt({ v, paths }: { v: LockValues; paths: LockPaths }) {
  const bowFront = useDerivedValue<Transforms3d>(() => [{ translateY: RING_Y }, { scaleX: Math.max(0.02, Math.abs(v.turnCos.value)) }, { translateY: -RING_Y }]);
  const bowBack = useDerivedValue<Transforms3d>(() => [
    { translateX: 7 * v.turnSin.value }, { translateY: RING_Y }, { scaleX: Math.max(0.02, Math.abs(v.turnCos.value)) }, { translateY: -RING_Y },
  ]);
  const bowMid = useDerivedValue<Transforms3d>(() => [
    { translateX: 3.5 * v.turnSin.value }, { translateY: RING_Y }, { scaleX: Math.max(0.02, Math.abs(v.turnCos.value)) }, { translateY: -RING_Y },
  ]);
  const edgeAlpha = useDerivedValue(() => Math.min(1, Math.abs(v.turnSin.value) * 1.6));
  return (
    <Group transform={v.key}>
      <Path path={paths.blade}>
        <LinearGradient start={vec(-KEY.shaft, 0)} end={vec(KEY.shaft + KEY.bitWidth, 0)} colors={[COLORS.steelDeep, COLORS.steelHigh, COLORS.steel, COLORS.steelDeep]} positions={[0, 0.35, 0.6, 1]} />
      </Path>
      <Path path={paths.blade} style="stroke" strokeWidth={0.8} color="rgba(30,34,38,0.5)" />
      <Group opacity={edgeAlpha}>
        <Group transform={bowBack}><Path path={paths.ring} color={COLORS.steelDeep} /></Group>
        <Group transform={bowMid}><Path path={paths.ring} color={COLORS.steel} /></Group>
      </Group>
      <Group transform={bowFront}>
        <Path path={paths.ring}>
          <LinearGradient start={vec(-KEY.ring / 2, RING_Y - KEY.ring / 2)} end={vec(KEY.ring / 2, RING_Y + KEY.ring / 2)} colors={[COLORS.steelHigh, COLORS.steel, COLORS.steelDeep]} />
        </Path>
        <Path path={paths.ring} style="stroke" strokeWidth={1} color={COLORS.steelEdge} opacity={0.7} />
      </Group>
    </Group>
  );
}

// Farthest copy first, so nearer ones cover it.
const SIDE_STEPS = [1, 0.85, 0.7, 0.55, 0.4, 0.25, 0.1];
// Brushing: one fine line every 3 pt across the face.
const BRUSH = Array.from({ length: Math.ceil(H / 3) }, (_, i) => i * 3);
