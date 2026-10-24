import { useMemo } from 'react';
import { Circle, Group, Path, Skia } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { SIGNATURE, signatureClamp, signatureCubic, signatureEase } from './BuzzStreaksSignature';
import { makeSignatureCurve, signatureCurveFraction } from './BuzzStreaksSignatureDrawing';

const LIME = new Float32Array([.92, 1, .36, 1]);
const CONDUIT = [[75, 325], [40, 364], [58, 399], [96, 424]];
const ORBIT_TOP = [[96, 424], [96, 379], [263, 379], [263, 424]];
const ORBIT_BOTTOM = [[263, 424], [263, 469], [96, 469], [96, 424]];
const CONDUIT_START = 2.60;
const CONDUIT_END = 2.88;
const ORBIT_START = CONDUIT_END;
const ORBIT_END = 3.60;
const ORBIT_DRAIN = 3.82;

function heroTravel(time: number, start: number, end: number) {
  'worklet';
  return signatureEase(signatureClamp((time - start) / (end - start)), SIGNATURE.easing.travel);
}

function orbitCoordinate(progress: number, axis: 0 | 1) {
  'worklet';
  return progress <= .5
    ? signatureCubic(ORBIT_TOP, progress * 2, axis)
    : signatureCubic(ORBIT_BOTTOM, progress * 2 - 1, axis);
}

function orbitFraction(lengths: number[], progress: number) {
  'worklet';
  // The two mirrored cubic halves have equal length.
  return progress <= .5
    ? .5 * signatureCurveFraction(lengths, progress * 2)
    : .5 + .5 * signatureCurveFraction(lengths, progress * 2 - 1);
}

/** One return-buzz conduit and open reward arc; mount inside the existing stage Canvas. */
export function BuzzStreaksSignatureHero({ clock, foreground = false }: { clock: SharedValue<number>; foreground?: boolean }) {
  const { conduit, conduitLengths, orbit, orbitLengths, backClip, frontClip } = useMemo(() => {
    const conduitCurve = makeSignatureCurve(CONDUIT);
    const top = makeSignatureCurve(ORBIT_TOP);
    const orbitPath = top.path.copy();
    orbitPath.cubicTo(ORBIT_BOTTOM[1][0], ORBIT_BOTTOM[1][1], ORBIT_BOTTOM[2][0], ORBIT_BOTTOM[2][1], ORBIT_BOTTOM[3][0], ORBIT_BOTTOM[3][1]);
    return { conduit: conduitCurve.path, conduitLengths: conduitCurve.lengths,
      orbit: orbitPath, orbitLengths: top.lengths,
      backClip: Skia.XYWHRect(0, 0, 360, 424), frontClip: Skia.XYWHRect(0, 424, 360, 56) };
  }, []);
  const conduitProgress = useDerivedValue(() => heroTravel(clock.get(), CONDUIT_START, CONDUIT_END));
  const conduitEnd = useDerivedValue(() => signatureCurveFraction(conduitLengths, conduitProgress.get()));
  const conduitStart = useDerivedValue(() => signatureCurveFraction(conduitLengths, heroTravel(clock.get(), CONDUIT_START + .08, CONDUIT_END)));
  const conduitOpacity = useDerivedValue(() => {
    const time = clock.get();
    if (time <= CONDUIT_START || time >= CONDUIT_END) return 0;
    return Math.min(1, (time - CONDUIT_START) / .025, (CONDUIT_END - time) / .025);
  });
  const conduitX = useDerivedValue(() => signatureCubic(CONDUIT, conduitProgress.get(), 0));
  const conduitY = useDerivedValue(() => signatureCubic(CONDUIT, conduitProgress.get(), 1));
  const progress = useDerivedValue(() => heroTravel(clock.get(), ORBIT_START, ORBIT_END));
  const end = useDerivedValue(() => orbitFraction(orbitLengths, progress.get()));
  const start = useDerivedValue(() => orbitFraction(orbitLengths, heroTravel(clock.get() - (ORBIT_DRAIN - ORBIT_END), ORBIT_START, ORBIT_END)));
  const opacity = useDerivedValue(() => {
    const time = clock.get();
    if (time <= ORBIT_START || time >= ORBIT_DRAIN) return 0;
    return Math.min(1, (time - ORBIT_START) / .035, (ORBIT_DRAIN - time) / .035);
  });
  const headOpacity = useDerivedValue(() => {
    const time = clock.get();
    if (time <= ORBIT_START || time >= ORBIT_END) return 0;
    return Math.min(1, (time - ORBIT_START) / .025, (ORBIT_END - time) / .025);
  });
  const headX = useDerivedValue(() => orbitCoordinate(progress.get(), 0));
  const headY = useDerivedValue(() => orbitCoordinate(progress.get(), 1));
  return <Group>
    {!foreground && <Group opacity={conduitOpacity}>
      <Path path={conduit} start={conduitStart} end={conduitEnd} style="stroke" strokeWidth={5} strokeCap="round" color={LIME} opacity={.85}/>
      <Path path={conduit} start={conduitStart} end={conduitEnd} style="stroke" strokeWidth={1.4} strokeCap="round" color="white" opacity={.9}/>
      <Circle cx={conduitX} cy={conduitY} r={4} color={LIME}/>
      <Circle cx={conduitX} cy={conduitY} r={1.5} color="white"/>
    </Group>}
    <Group clip={foreground ? frontClip : backClip}>
      <Group opacity={opacity}>
        <Path path={orbit} start={start} end={end} style="stroke" strokeWidth={5} strokeCap="round" color={LIME} opacity={.85}/>
        <Path path={orbit} start={start} end={end} style="stroke" strokeWidth={1.4} strokeCap="round" color="white" opacity={.9}/>
      </Group>
      <Group opacity={headOpacity}>
        <Circle cx={headX} cy={headY} r={4} color={LIME}/>
        <Circle cx={headX} cy={headY} r={1.5} color="white"/>
      </Group>
    </Group>
  </Group>;
}
