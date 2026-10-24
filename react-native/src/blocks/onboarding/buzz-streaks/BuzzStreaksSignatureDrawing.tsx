import { useMemo } from 'react';
import { Group, LinearGradient, Path, RoundedRect, Skia, type Transforms3d } from '@shopify/react-native-skia';
import { useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { SIGNATURE, signatureClamp, signatureCubic, signatureCubicDerivative, signaturePulse, signatureRelayAlpha, signatureTravel, type SignaturePortal, type SignatureRelay } from './BuzzStreaksSignature';
import { actingPivot, actingPose, actingTransformPoint } from './BuzzStreaksActing';

const HEAD = new Float32Array([.92, 1, .36, 1]);
const LIP = [new Float32Array([.95, 1, .62, 0]), new Float32Array([.95, 1, .62, 1]), new Float32Array([.95, 1, .62, 0])];
const UNDERSIDE: Transforms3d = [{ translateY: 2 }];
const RELAY_TAIL = .20;
const ACTING_RIG: { characters: Record<'orange' | 'pink', { key: number[][] }> } = require('./assets/rig.json');

/** Fixed launch/arrival anchors are mapped through the same actor matrix once. */
function characterRelay(relay: SignatureRelay, index: number): SignatureRelay {
  function point(kind: 'orange' | 'pink', time: number, xy: number[]) {
    const rows = ACTING_RIG.characters[kind].key;
    const frame = Math.max(0, Math.min(rows.length - 1, time * 60));
    const i = Math.floor(frame), j = Math.min(rows.length - 1, i + 1);
    const key = rows[i].map((value, axis) => value + (rows[j][axis] - value) * (frame - i));
    const value = actingTransformPoint({ x: xy[0], y: xy[1] }, actingPose(time, kind), actingPivot(time, kind, key));
    return [value.x, value.y];
  }
  return { ...relay, points: [
    point(index === 0 ? 'orange' : 'pink', relay.start, relay.points[0]),
    relay.points[1], relay.points[2],
    point(index === 0 ? 'pink' : 'orange', relay.end, relay.points[3]),
  ] };
}

/** Mount-time curve and length lookup keep retained path trimming aligned with its cubic head. */
export function makeSignatureCurve(points: number[][]) {
  const path = Skia.Path.Make();
  path.moveTo(points[0][0], points[0][1]);
  path.cubicTo(points[1][0], points[1][1], points[2][0], points[2][1], points[3][0], points[3][1]);
  const lengths = [0];
  let previousX = points[0][0], previousY = points[0][1], length = 0;
  for (let index = 1; index <= 128; index++) {
    const x = signatureCubic(points, index / 128, 0), y = signatureCubic(points, index / 128, 1);
    length += Math.hypot(x - previousX, y - previousY);
    lengths.push(length);
    previousX = x; previousY = y;
  }
  return { path, lengths: lengths.map(value => value / length) };
}

export function signatureCurveFraction(lengths: number[], progress: number) {
  'worklet';
  const frame = signatureClamp(progress) * (lengths.length - 1), index = Math.floor(frame);
  const next = Math.min(index + 1, lengths.length - 1);
  return lengths[index] + (lengths[next] - lengths[index]) * (frame - index);
}

function RelayPacket({ clock, relay, compact = false }: { clock: SharedValue<number>; relay: SignatureRelay; compact?: boolean }) {
  const { path, lengths } = useMemo(() => makeSignatureCurve(relay.points), [relay]);
  const tail = compact ? .09 : RELAY_TAIL;
  const progress = useDerivedValue(() => signatureTravel(clock.get(), relay));
  const start = useDerivedValue(() => signatureCurveFraction(lengths, signatureTravel(clock.get() - tail, relay)));
  const end = useDerivedValue(() => signatureCurveFraction(lengths, progress.get()));
  const ribbonOpacity = useDerivedValue(() => {
    const time = clock.get();
    if (time <= relay.start || time >= relay.end + tail) return 0;
    return Math.min(1, (time - relay.start) / .035, (relay.end + tail - time) / .035);
  });
  const headOpacity = useDerivedValue(() => signatureRelayAlpha(clock.get(), relay, true));
  const transform = useDerivedValue<Transforms3d>(() => {
    const p = progress.get();
    return [{ translateX: signatureCubic(relay.points, p, 0) },
      { translateY: signatureCubic(relay.points, p, 1) },
      { rotate: Math.atan2(signatureCubicDerivative(relay.points, p, 1), signatureCubicDerivative(relay.points, p, 0)) }];
  });
  return <Group>
    <Group opacity={ribbonOpacity}>
      {!compact && <Group transform={UNDERSIDE}><Path path={path} start={start} end={end} style="stroke" strokeWidth={10} strokeCap="round" color="#451566" opacity={.65}/></Group>}
      <Path path={path} start={start} end={end} style="stroke" strokeWidth={compact ? 3.5 : 8} strokeCap="round" color={HEAD} opacity={.85}/>
      <Path path={path} start={start} end={end} style="stroke" strokeWidth={compact ? 1 : 2} strokeCap="round" color="white" opacity={.9}/>
    </Group>
    <Group transform={transform} opacity={headOpacity}>
      {!compact && <RoundedRect x={-9} y={-3} width={18} height={10} r={5} color="#451566" opacity={.65}/>}
      <RoundedRect x={compact ? -4 : -9} y={compact ? -2.5 : -5} width={compact ? 8 : 18} height={compact ? 5 : 10} r={compact ? 2.5 : 5} color={HEAD}/>
      <RoundedRect x={compact ? -2 : -6} y={compact ? -.6 : -1} width={compact ? 4 : 12} height={compact ? 1.2 : 2} r={1} color="white"/>
    </Group>
  </Group>;
}

/** Two retained light ribbons draw behind the source mascots and drain after each arrival. */
export function BuzzStreaksRelayPackets({ clock, characterEnabled = false }: { clock: SharedValue<number>; characterEnabled?: boolean }) {
  const relays = useMemo(() => characterEnabled ? SIGNATURE.relay.map(characterRelay) : SIGNATURE.relay, [characterEnabled]);
  return <Group>{relays.map((relay, index) => <RelayPacket key={index} clock={clock} relay={relay} compact={characterEnabled}/>)}</Group>;
}

function PortalLip({ clock, portal }: { clock: SharedValue<number>; portal: SignaturePortal }) {
  const { length, width, alpha, duration } = SIGNATURE.lip;
  const opacity = useDerivedValue(() => alpha * signaturePulse(clock.get(), portal.start, portal.start + duration));
  return <RoundedRect x={portal.lipX - length / 2} y={.7 - width / 2} width={length} height={width} r={width / 2} opacity={opacity}>
    <LinearGradient start={{ x: portal.lipX - length / 2, y: .7 }} end={{ x: portal.lipX + length / 2, y: .7 }} colors={LIP}/>
  </RoundedRect>;
}

/** Card-local, outside the centered ribbon, using the tutorial's existing canvas. */
export function BuzzStreaksPortalLips({ clock }: { clock: SharedValue<number> }) {
  return <Group>{SIGNATURE.portal.map(portal => <PortalLip key={portal.id} clock={clock} portal={portal}/>)}</Group>;
}
