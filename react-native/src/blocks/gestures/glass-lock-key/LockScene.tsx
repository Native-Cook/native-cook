/** Canvas: pale grey ground, frosted glass padlock with its shackle, and the glass key. */
import {
  BlurMask, Circle, Group, LinearGradient, Oval, Path, RadialGradient, Rect, RoundedRect, vec,
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

export function LockScene({ g, paths, v }: Props) {
  const { w, h } = g;
  const glintX = useDerivedValue(() => -60 + (F + 120) * v.glint.value);
  const glintO = useDerivedValue(() => (v.glint.value > 0 && v.glint.value < 1 ? Math.sin(Math.PI * v.glint.value) : 0));

  return (
    <Group>
      <Rect x={0} y={0} width={w} height={h}>
        <LinearGradient start={vec(0, 0)} end={vec(0, h)} colors={[COLORS.bgTop, COLORS.bgBottom]} />
      </Rect>

      <Group transform={v.rig}>
        {/* Soft contact shadows on the grey. */}
        <Oval x={g.faceX + 10 * g.s} y={g.lockBottom + 26 * g.s} width={(F - 20) * g.s} height={18 * g.s} color={COLORS.shadow}>
          <BlurMask blur={14} style="normal" />
        </Oval>
        <Group transform={v.keyShadow}>
          <Oval x={-22} y={0} width={44} height={10} color={COLORS.shadow} opacity={0.7}>
            <BlurMask blur={8} style="normal" />
          </Oval>
        </Group>

        {/* In the lock it is drawn behind the opaque body, which hides the blade; the bow shows below. */}
        <Group opacity={v.keyBehind}>
          <KeyArt v={v} paths={paths} />
        </Group>

        <Group transform={v.lock}>
          {/* Shackle behind the body: its legs go in. */}
          <Group transform={v.shackle}>
            <Path path={paths.shackle} style="stroke" strokeWidth={LOCK.shackle}>
              <LinearGradient start={vec(0, -LOCK.shackleRise)} end={vec(F, 0)} colors={[COLORS.glass, COLORS.glassDeep]} />
            </Path>
            <Path path={paths.shackle} style="stroke" strokeWidth={LOCK.shackle - 10} color={COLORS.glass} opacity={0.5} />
            <Group transform={[{ translateX: -9 }, { translateY: -3 }]}>
              <Path path={paths.shackle} style="stroke" strokeWidth={2.2} color={COLORS.glassEdge} opacity={0.75} strokeCap="round">
                <BlurMask blur={0.8} style="normal" />
              </Path>
            </Group>
          </Group>

          {/* Body: a solid side face (the source's 3/4 view) built from offset copies, then the frosted face. */}
          {SIDE_STEPS.map((k) => (
            <Group key={k} transform={[{ translateX: LOCK.side * k }, { translateY: -LOCK.side * 0.5 * k }]}>
              <Path path={paths.face}>
                <LinearGradient start={vec(0, 0)} end={vec(0, H)} colors={[COLORS.side, COLORS.sideDeep]} />
              </Path>
            </Group>
          ))}
          <Path path={paths.face}>
            <LinearGradient start={vec(0, 0)} end={vec(0, H)} colors={[COLORS.bodyTop, COLORS.bodyBottom]} />
          </Path>
          <Path path={paths.face}>
            <RadialGradient c={vec(F * 0.34, H * 0.3)} r={F * 0.85} colors={[COLORS.bodyLight, COLORS.bodyLightFar]} />
          </Path>
          <Group clip={paths.face}>
            <Rect x={glintX} y={-20} width={26} height={H + 40} color={COLORS.glint} opacity={glintO} transform={[{ skewX: -0.35 }]}>
              <BlurMask blur={8} style="normal" />
            </Rect>
          </Group>
          <Path path={paths.face} style="stroke" strokeWidth={1.6}>
            <LinearGradient start={vec(0, 0)} end={vec(F, H)} colors={[COLORS.rim, COLORS.rimSoft, 'rgba(255,255,255,0.1)']} />
          </Path>
          <Path path={paths.faceInner} style="stroke" strokeWidth={1} color={COLORS.rimSoft} opacity={0.6} />

          {/* The keyhole's plug on the underside, glowing when the key comes near. */}
          <Circle cx={F / 2} cy={H + LOCK.plugHeight / 2} r={LOCK.glowRadius} color={COLORS.glow} opacity={v.holeGlow}>
            <BlurMask blur={16} style="normal" />
          </Circle>
          <RoundedRect x={F / 2 - LOCK.plugWidth / 2} y={H - 3} width={LOCK.plugWidth} height={LOCK.plugHeight + 3} r={4} color={COLORS.plug} />
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

// Farthest copy first, so nearer ones cover it.
function KeyArt({ v, paths }: { v: LockValues; paths: LockPaths }) {
  // The bow turns about the key's long axis: the face narrows to cos θ and its back edge shows by sin θ.
  const bowFront = useDerivedValue<Transforms3d>(() => [{ translateY: RING_Y }, { scaleX: Math.max(0.02, Math.abs(v.turnCos.value)) }, { translateY: -RING_Y }]);
  const bowBack = useDerivedValue<Transforms3d>(() => [
    { translateX: 8 * v.turnSin.value }, { translateY: RING_Y }, { scaleX: Math.max(0.02, Math.abs(v.turnCos.value)) }, { translateY: -RING_Y },
  ]);
  const bowMid = useDerivedValue<Transforms3d>(() => [
    { translateX: 4 * v.turnSin.value }, { translateY: RING_Y }, { scaleX: Math.max(0.02, Math.abs(v.turnCos.value)) }, { translateY: -RING_Y },
  ]);
  const edgeAlpha = useDerivedValue(() => Math.min(1, Math.abs(v.turnSin.value) * 1.6));
  return (
    <Group transform={v.key}>
      <Path path={paths.blade}>
        <LinearGradient start={vec(-KEY.shaft, 0)} end={vec(KEY.shaft + KEY.bitWidth, 0)} colors={[COLORS.glassDeep, COLORS.glass, COLORS.glassDeep]} />
      </Path>
      <Path path={paths.blade} style="stroke" strokeWidth={1} color={COLORS.glassEdge} opacity={0.7} />
      <Group opacity={edgeAlpha}>
        <Group transform={bowBack}>
          <Path path={paths.ring} color={COLORS.glassDeep} />
        </Group>
        <Group transform={bowMid}>
          <Path path={paths.ring} color={COLORS.glassDeep} />
        </Group>
      </Group>
      <Group transform={bowFront}>
        <Path path={paths.ring}>
          <LinearGradient start={vec(-KEY.ring / 2, RING_Y - KEY.ring / 2)} end={vec(KEY.ring / 2, RING_Y + KEY.ring / 2)} colors={[COLORS.glass, COLORS.glassDeep]} />
        </Path>
        <Path path={paths.ring} style="stroke" strokeWidth={1.3} color={COLORS.glassEdge} opacity={0.85} />
      </Group>
    </Group>
  );
}

const SIDE_STEPS = [1, 0.85, 0.7, 0.55, 0.4, 0.25, 0.1];
