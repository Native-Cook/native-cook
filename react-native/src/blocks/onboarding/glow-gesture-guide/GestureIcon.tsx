import { Canvas, Circle, Group, Path, Skia } from '@shopify/react-native-skia';
import { type SharedValue, useDerivedValue } from 'react-native-reanimated';

export type GestureKind = 'tap' | 'hold' | 'edge' | 'swipe';

// Drawn in a 32 × 32 box: a pointing hand, and what each gesture adds around it.
const HAND =
  'M11 18V10.5C11 9.4 11.9 8.5 13 8.5C14.1 8.5 15 9.4 15 10.5V16.5' +
  'M15 15.5C15 14.6 15.7 13.9 16.6 13.9C17.5 13.9 18.2 14.6 18.2 15.5V17' +
  'M18.2 16.3C18.2 15.4 18.9 14.7 19.8 14.7C20.7 14.7 21.4 15.4 21.4 16.3V17.5' +
  'M21.4 17.2C21.4 16.3 22.1 15.6 23 15.6C23.9 15.6 24.6 16.3 24.6 17.2V22C24.6 26.4 21.6 29 17.6 29H16.4' +
  'C14 29 12.4 28 11.1 26.3L7.6 21.7C6.9 20.8 7.1 19.6 7.9 19C8.7 18.4 9.8 18.5 10.4 19.3L11 20.1V18';
const LEFT = 'M7.5 4L4.5 7L7.5 10';
const RIGHT = 'M18.5 4L21.5 7L18.5 10';
const BOX = 32;
const LINE = 1.5;
// The fingertip, the centre of the dots, arcs and ripples.
const TIP = { x: 13, y: 10.5 };
const DOTS = [-160, -125, -90, -55, -20];
const DOT_R = 5.5;
const ARCS = [4.8, 7.6];
const ARC_FROM = -160;
const ARC_TO = -20;
const EDGE_X = 3.5;
const RIPPLES = [0, 1];
// Each gesture's little performance, in seconds after its row is chosen.
export const PLAY_FOR = 1.3;
const PLAY_AT = 0.2;

const handPath = Skia.Path.MakeFromSVGString(HAND)!;
const leftPath = Skia.Path.MakeFromSVGString(LEFT)!;
const rightPath = Skia.Path.MakeFromSVGString(RIGHT)!;

function arcPath(r: number) {
  const path = Skia.PathBuilder.Make();
  const steps = 16;
  for (let k = 0; k <= steps; k++) {
    const a = ((ARC_FROM + ((ARC_TO - ARC_FROM) * k) / steps) * Math.PI) / 180;
    const x = TIP.x + r * Math.cos(a);
    const y = TIP.y + r * Math.sin(a);
    if (k === 0) path.moveTo(x, y);
    else path.lineTo(x, y);
  }
  return path.build();
}

const innerArc = arcPath(ARCS[0]);
const outerArc = arcPath(ARCS[1]);

function bump(s: number, length: number) {
  'worklet';
  return s >= 0 && s <= length ? Math.sin((Math.PI * s) / length) : 0;
}

/** A wave starting at `at`: 0 → 1 over `length`, or -1 outside it. */
function wave(t: number, at: number, length: number) {
  'worklet';
  const u = (t - at) / length;
  return u >= 0 && u <= 1 ? u : -1;
}

/** A gesture's icon at `size` points; `since` is the seconds since its row was chosen. */
export function GestureIcon({ kind, size, since, color }: { kind: GestureKind; size: number; since: SharedValue<number>; color: string }) {
  const scale = size / BOX;
  // Tap: two ripples from the fingertip.
  const ripple0 = useDerivedValue(() => wave(since.get(), PLAY_AT, 0.6));
  const ripple1 = useDerivedValue(() => wave(since.get(), PLAY_AT + 0.45, 0.6));
  const r0 = useDerivedValue(() => 2 + 8 * Math.max(0, ripple0.get()));
  const r1 = useDerivedValue(() => 2 + 8 * Math.max(0, ripple1.get()));
  const a0 = useDerivedValue(() => (ripple0.get() < 0 ? 0 : 0.9 * (1 - ripple0.get()) ** 2));
  const a1 = useDerivedValue(() => (ripple1.get() < 0 ? 0 : 0.9 * (1 - ripple1.get()) ** 2));
  // Hold: two arcs leave the outer one and fade.
  const hold0 = useDerivedValue(() => wave(since.get(), PLAY_AT, 0.7));
  const hold1 = useDerivedValue(() => wave(since.get(), PLAY_AT + 0.5, 0.7));
  const holdT0 = useDerivedValue(() => {
    const s = (ARCS[1] + 5 * Math.max(0, hold0.get())) / ARCS[1];
    return [{ translateX: TIP.x }, { translateY: TIP.y }, { scale: s }, { translateX: -TIP.x }, { translateY: -TIP.y }];
  });
  const holdT1 = useDerivedValue(() => {
    const s = (ARCS[1] + 5 * Math.max(0, hold1.get())) / ARCS[1];
    return [{ translateX: TIP.x }, { translateY: TIP.y }, { scale: s }, { translateX: -TIP.x }, { translateY: -TIP.y }];
  });
  const holdW0 = useDerivedValue(() => (LINE * ARCS[1]) / (ARCS[1] + 5 * Math.max(0, hold0.get())));
  const holdW1 = useDerivedValue(() => (LINE * ARCS[1]) / (ARCS[1] + 5 * Math.max(0, hold1.get())));
  const holdA0 = useDerivedValue(() => (hold0.get() < 0 ? 0 : 0.9 * (1 - hold0.get())));
  const holdA1 = useDerivedValue(() => (hold1.get() < 0 ? 0 : 0.9 * (1 - hold1.get())));
  // Edge: the bar flashes twice and the hand nudges towards it.
  const flash = useDerivedValue(() => {
    const u = wave(since.get(), PLAY_AT, 0.8);
    return u < 0 ? 0 : 0.5 - 0.5 * Math.cos(4 * Math.PI * u);
  });
  const barOpacity = useDerivedValue(() => 1 - 0.7 * flash.get());
  const handShift = useDerivedValue(() => [{ translateX: kind === 'edge' ? 3 - 2 * flash.get() : 0 }]);
  // Swipe: the left chevron nudges left, then the right one right.
  const leftShift = useDerivedValue(() => [{ translateX: -3 * bump(since.get() - PLAY_AT, 0.45) }]);
  const rightShift = useDerivedValue(() => [{ translateX: 3 * bump(since.get() - PLAY_AT - 0.45, 0.45) }]);

  const stroke = { style: 'stroke' as const, strokeWidth: LINE, strokeCap: 'round' as const, strokeJoin: 'round' as const, color };

  return (
    <Canvas style={{ width: size, height: size }}>
      <Group transform={[{ scale }]}>
        <Group transform={handShift}>
          <Path path={handPath} {...stroke} />
          {kind === 'tap' &&
            DOTS.map((deg) => (
              <Circle
                key={deg}
                cx={TIP.x + DOT_R * Math.cos((deg * Math.PI) / 180)}
                cy={TIP.y + DOT_R * Math.sin((deg * Math.PI) / 180)}
                r={0.8}
                color={color}
              />
            ))}
          {kind === 'edge' &&
            DOTS.slice(0, 3).map((deg) => (
              <Circle
                key={deg}
                cx={TIP.x + DOT_R * Math.cos((deg * Math.PI) / 180)}
                cy={TIP.y + DOT_R * Math.sin((deg * Math.PI) / 180)}
                r={0.8}
                color={color}
              />
            ))}
        </Group>
        {kind === 'tap' &&
          RIPPLES.map((k) => (
            <Circle
              key={k}
              cx={TIP.x}
              cy={TIP.y - 1}
              r={k === 0 ? r0 : r1}
              opacity={k === 0 ? a0 : a1}
              style="stroke"
              strokeWidth={1.2}
              color={color}
            />
          ))}
        {kind === 'hold' && (
          <>
            <Path path={innerArc} {...stroke} />
            <Path path={outerArc} {...stroke} />
            <Group transform={holdT0} opacity={holdA0}>
              <Path path={outerArc} {...stroke} strokeWidth={holdW0} />
            </Group>
            <Group transform={holdT1} opacity={holdA1}>
              <Path path={outerArc} {...stroke} strokeWidth={holdW1} />
            </Group>
          </>
        )}
        {kind === 'edge' && (
          <Group opacity={barOpacity}>
            <Path path={`M${EDGE_X} 5V29`} {...stroke} />
          </Group>
        )}
        {kind === 'swipe' && (
          <>
            <Group transform={leftShift}>
              <Path path={leftPath} {...stroke} />
            </Group>
            <Group transform={rightShift}>
              <Path path={rightPath} {...stroke} />
            </Group>
          </>
        )}
      </Group>
    </Canvas>
  );
}
