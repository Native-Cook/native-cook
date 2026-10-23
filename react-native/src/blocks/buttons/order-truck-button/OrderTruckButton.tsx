import { Canvas, Group, LinearGradient, Path, RoundedRect, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  doneLabel?: string;
  /** Called when the order is placed (on tap, before the truck sets off). */
  onPress?: () => void;
  /** Place an order by itself every few seconds. Tapping still works while idle. */
  autoPlay?: boolean;
};

const WIDTH = 212;
const HEIGHT = 56;
const AUTO_PAUSE_MS = 1300;

// The truck, seen from above and drawn with its cargo box's rear-left corner at the origin.
const BOX_W = 52;
const BOX_H = 37;
const CAB_X = BOX_W + 1;
const CAB_W = 21;
const TRUCK_W = CAB_X + CAB_W;
const DOOR = BOX_H / 2;
const DOOR_SWING = 115;
const PARCEL = 19;
const DASH = 7;
const DASH_GAP = 5.5;
// How fast the road runs under the truck, in points per second.
const ROAD_SPEED = 48;

// Where the truck's rear sits along the pill.
const X_OFF = WIDTH + 8;
const X_REST = (WIDTH - TRUCK_W) / 2;
const X_AHEAD = 120;
const X_BACK = 15;
const PARCEL_OFF = -PARCEL - 3;
const PARCEL_REST = 16;

// The whole story is a function of one clock (seconds since the tap).
const T_LABEL_OUT = 0.25;
const T_ENTER = [0.15, 1.15];
const T_PARCEL_IN = [0.2, 0.55];
const T_DOORS_OPEN = [0.45, 0.9];
const T_LOAD = [1.4, 2.0];
const T_DOORS_CLOSE = [2.0, 2.75];
const T_AHEAD = [3.2, 3.9];
const T_LIGHTS = [3.2, 3.5];
const T_ROAD_IN = [3.3, 3.7];
const T_DRIFT = [4.2, 6.1];
const T_EXIT = [6.1, 6.7];
const T_ROAD_OUT = [6.45, 6.85];
const T_DONE_IN = [7.0, 7.3];
const T_CHECK = [7.35, 7.65];
const T_DONE_OUT = [9.0, 9.25];
const T_LABEL_IN = [9.25, 9.6];
const TOTAL = 9.6;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function seg(t: number, range: number[]) {
  'worklet';
  return Math.min(1, Math.max(0, (t - range[0]) / (range[1] - range[0])));
}
function easeOut(u: number) {
  'worklet';
  return 1 - (1 - u) * (1 - u) * (1 - u);
}
function easeIn(u: number) {
  'worklet';
  return u * u * u;
}
function easeInOut(u: number) {
  'worklet';
  return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
}
// Sets off gently, then spends most of the trip slowing down.
function easeDrift(u: number) {
  'worklet';
  const s = u * u * (3 - 2 * u);
  return 1 - (1 - s) * (1 - s);
}

function truckX(t: number) {
  'worklet';
  return (
    X_OFF +
    (X_REST - X_OFF) * easeOut(seg(t, T_ENTER)) +
    (X_AHEAD - X_REST) * easeInOut(seg(t, T_AHEAD)) +
    (X_BACK - X_AHEAD) * easeDrift(seg(t, T_DRIFT)) +
    (X_OFF - X_BACK) * easeIn(seg(t, T_EXIT))
  );
}

// One row of dashes, a little longer than the pill so it can scroll by a whole period.
const ROAD = (() => {
  let d = '';
  for (let x = 0; x < WIDTH + (DASH + DASH_GAP) * 2; x += DASH + DASH_GAP) d += `M${x} 0h${DASH}`;
  return d;
})();

function Scene({ t, still }: { t: SharedValue<number>; still: boolean }) {
  const cy = HEIGHT / 2;
  const top = -BOX_H / 2;
  const bottom = BOX_H / 2;
  const clip = useMemo(() => rrect(rect(0, 0, WIDTH, HEIGHT), HEIGHT / 2, HEIGHT / 2), []);

  const truck = useDerivedValue(() => [{ translateX: truckX(t.get()) }, { translateY: cy }]);
  const doors = useDerivedValue(
    () => (DOOR_SWING * Math.PI) / 180 * (easeOut(seg(t.get(), T_DOORS_OPEN)) - easeInOut(seg(t.get(), T_DOORS_CLOSE))),
  );
  const topDoor = useDerivedValue(() => [{ rotate: doors.get() }]);
  const bottomDoor = useDerivedValue(() => [{ rotate: -doors.get() }]);
  // Shut, the doors are simply the box's rear edge.
  const doorOpacity = useDerivedValue(() => Math.min(1, doors.get() * 12));
  const lights = useDerivedValue(() => seg(t.get(), T_LIGHTS));

  const parcel = useDerivedValue(() => {
    const time = t.get();
    const x =
      PARCEL_OFF +
      (PARCEL_REST - PARCEL_OFF) * easeOut(seg(time, T_PARCEL_IN)) +
      (X_REST + 8 - PARCEL_REST) * easeIn(seg(time, T_LOAD));
    return [{ translateX: x }, { translateY: cy - PARCEL / 2 }];
  });
  // Once it is under the cargo box it stays hidden, or the truck would drive off and leave it behind.
  const parcelOpacity = useDerivedValue(() => (t.get() > T_PARCEL_IN[0] && t.get() < T_LOAD[1] ? 1 : 0));

  const roadShift = useDerivedValue(() => [
    { translateX: -((Math.max(0, t.get() - T_ROAD_IN[0]) * ROAD_SPEED) % (DASH + DASH_GAP)) },
    { translateY: cy },
  ]);
  const roadOpacity = useDerivedValue(() => seg(t.get(), T_ROAD_IN) - seg(t.get(), T_ROAD_OUT));

  return (
    <Canvas style={{ width: WIDTH, height: HEIGHT }}>
      <RoundedRect x={0} y={0} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color="#1c212b" />
      {!still && (
        <Group clip={clip}>
          <Group transform={roadShift} opacity={roadOpacity}>
            <Path path={ROAD} style="stroke" strokeWidth={1.4} color="rgba(255,255,255,0.92)" />
          </Group>

          <Group transform={parcel} opacity={parcelOpacity}>
            <RoundedRect x={0} y={0} width={PARCEL} height={PARCEL} r={2.5}>
              <LinearGradient start={vec(0, 0)} end={vec(0, PARCEL)} colors={['#edd09a', '#dcb777']} />
            </RoundedRect>
            <Path path={`M0 ${PARCEL / 2}h${PARCEL}`} style="stroke" strokeWidth={1.2} color="#c9a05c" />
          </Group>

          <Group transform={truck}>
            {/* Headlight beams, behind the cab. */}
            <Group opacity={lights}>
              <Path path={`M${TRUCK_W} ${top + 5}L${TRUCK_W + 41} ${top - 9}L${TRUCK_W + 41} ${top + 15}L${TRUCK_W} ${top + 9}Z`}>
                <LinearGradient
                  start={vec(TRUCK_W, 0)}
                  end={vec(TRUCK_W + 41, 0)}
                  colors={['rgba(244,214,84,0.8)', 'rgba(214,200,130,0.16)']}
                />
              </Path>
              <Path path={`M${TRUCK_W} ${bottom - 5}L${TRUCK_W + 41} ${bottom + 9}L${TRUCK_W + 41} ${bottom - 15}L${TRUCK_W} ${bottom - 9}Z`}>
                <LinearGradient
                  start={vec(TRUCK_W, 0)}
                  end={vec(TRUCK_W + 41, 0)}
                  colors={['rgba(244,214,84,0.8)', 'rgba(214,200,130,0.16)']}
                />
              </Path>
            </Group>

            {/* Cab: blue shell, lighter roof slab, dark windscreen with two reflections. */}
            <Path
              path={`M${CAB_X} ${top + 0.5}H${CAB_X + 11}Q${TRUCK_W} ${top + 0.5} ${TRUCK_W} ${top + 11}V${bottom - 11}Q${TRUCK_W} ${bottom - 0.5} ${CAB_X + 11} ${bottom - 0.5}H${CAB_X}Z`}
              color="#2b5df5"
            />
            <Path
              path={`M${CAB_X + 6.5} ${top + 3.5}H${CAB_X + 10.5}Q${TRUCK_W - 3.5} ${top + 4} ${TRUCK_W - 3.5} ${top + 12}V${bottom - 12}Q${TRUCK_W - 3.5} ${bottom - 4} ${CAB_X + 10.5} ${bottom - 3.5}H${CAB_X + 6.5}Z`}
              color="#1c212b"
            />
            <Path
              path={`M${CAB_X + 8} ${-3}L${CAB_X + 15.5} ${-9}M${CAB_X + 8} ${1.5}L${CAB_X + 15.5} ${-4.5}`}
              style="stroke"
              strokeWidth={1.3}
              color="rgba(255,255,255,0.2)"
            />
            <RoundedRect x={CAB_X} y={top + 0.5} width={6.5} height={BOX_H - 1} r={1.2} color="#7096f8" />
            <RoundedRect x={TRUCK_W - 1.2} y={top + 3} width={2.6} height={7} r={0.8} color="#f2d24e" />
            <RoundedRect x={TRUCK_W - 1.2} y={bottom - 10} width={2.6} height={7} r={0.8} color="#f2d24e" />

            <RoundedRect x={0} y={top} width={BOX_W} height={BOX_H} r={2.5}>
              <LinearGradient start={vec(0, top)} end={vec(0, bottom)} colors={['#f7f9fd', '#cdd6ea']} />
            </RoundedRect>

            {/* Rear doors, hinged on the box's back corners. Closed, they lie along its rear edge. */}
            <Group opacity={doorOpacity}>
              <Group transform={topDoor} origin={vec(0, top)}>
                <Path path={`M0 ${top}v${DOOR}`} style="stroke" strokeWidth={1.3} strokeCap="round" color="#ffffff" />
              </Group>
              <Group transform={bottomDoor} origin={vec(0, bottom)}>
                <Path path={`M0 ${bottom}v${-DOOR}`} style="stroke" strokeWidth={1.3} strokeCap="round" color="#ffffff" />
              </Group>
            </Group>
          </Group>
        </Group>
      )}
    </Canvas>
  );
}

function Check({ t }: { t: SharedValue<number> }) {
  const end = useDerivedValue(() => easeOut(seg(t.get(), T_CHECK)));
  return (
    <Canvas style={styles.check}>
      <Path
        path="M2.5 8.5L6.2 12L13.5 4.2"
        style="stroke"
        strokeWidth={1.8}
        strokeCap="round"
        strokeJoin="round"
        color="#2fcf8e"
        start={0}
        end={end}
      />
    </Canvas>
  );
}

export function OrderTruckButton({
  label = 'Complete Order',
  doneLabel = 'Order Placed',
  onPress,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [busy, setBusy] = useState(false);
  const t = useSharedValue(0);

  // One run: start the clock, and go back to idle when it has run out. Keeping both in one effect
  // means the clock and `busy` can never drift apart.
  useEffect(() => {
    if (!busy) return;
    // Reduced motion skips the delivery: the label simply cross-fades to the confirmation and back.
    const skipFrom = T_DONE_IN[0] - 0.1;
    // Reanimated skips animations outright under reduced motion, which would swallow the
    // confirmation too, so the clock always opts back in.
    const run = (to: number, seconds: number) =>
      withTiming(to, { duration: seconds * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never });
    const steps = reducedMotion
      ? [run(T_LABEL_OUT, T_LABEL_OUT), run(skipFrom, 0), run(TOTAL, TOTAL - skipFrom)]
      : [run(TOTAL, TOTAL)];
    // The clock ends where it began (idle label showing), so the rewind at the end is invisible.
    t.set(withSequence(ReduceMotion.Never, run(0, 0), ...steps, run(0, 0)));
    const length = reducedMotion ? T_LABEL_OUT + TOTAL - skipFrom : TOTAL;
    const timer = setTimeout(() => setBusy(false), length * 1000);
    return () => clearTimeout(timer);
  }, [busy, reducedMotion, t]);

  useEffect(() => {
    if (!autoPlay || reducedMotion || busy || pressed) return;
    const auto = setTimeout(() => setBusy(true), AUTO_PAUSE_MS);
    return () => clearTimeout(auto);
  }, [autoPlay, reducedMotion, busy, pressed]);

  const labelStyle = useAnimatedStyle(() => ({
    opacity: 1 - seg(t.get(), [0, T_LABEL_OUT]) + seg(t.get(), T_LABEL_IN),
  }));
  const doneStyle = useAnimatedStyle(() => ({
    opacity: seg(t.get(), T_DONE_IN) - seg(t.get(), T_DONE_OUT),
  }));

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ busy }}
      onPress={() => {
        if (busy) return;
        onPress?.();
        setBusy(true);
      }}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      pressRetentionOffset={16}
    >
      <Animated.View
        style={[
          styles.button,
          {
            transform: [{ scale: pressed ? 0.97 : 1 }],
            transitionProperty: 'transform',
            transitionDuration: 130,
            transitionTimingFunction: CSS_EASE_OUT,
          },
        ]}
      >
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Scene t={t} still={reducedMotion} />
        </View>
        <Animated.Text style={[styles.label, labelStyle]} numberOfLines={1}>
          {label}
        </Animated.Text>
        <Animated.View pointerEvents="none" style={[styles.done, doneStyle]}>
          <Animated.Text style={styles.label} numberOfLines={1}>
            {doneLabel}
          </Animated.Text>
          <Check t={t} />
        </Animated.View>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '500',
  },
  done: {
    ...StyleSheet.absoluteFill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  check: {
    width: 16,
    height: 16,
  },
});
