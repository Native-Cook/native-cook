import { BlurMask, Canvas, Circle, Group, Oval, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useState } from 'react';
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
  onPress?: () => void;
  /** Downloads by itself over and over, with a pause between runs. */
  autoPlay?: boolean;
};

const WIDTH = 220;
const HEIGHT = 52;
// Width of the pill that announces the result.
const DONE_WIDTH = 170;
const R = HEIGHT / 2;
const KNOB_R = 20.5;
const STROKE = 2.2;
// The progress dot rides the middle of the outline.
const RING_R = R - STROKE / 2;
// The canvas overflows the button by this much on every side to fit the glow and the dot's flight.
const BLEED = 60;
const CY = BLEED + R;
const CX = BLEED + WIDTH / 2;
const AUTO_PAUSE_MS = 1500;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const CYAN = '#02d1fe';
const GREEN = '#00fe8c';

// The whole run is one clock in seconds; every part below is a window on it.
const T_FLASH = [0, 0.2];
const T_RIPPLE = [0, 0.5];
const T_COLLAPSE = [0.08, 0.6];
const T_LABEL_OUT = [0.1, 0.35];
const T_TO_STOP = [0.1, 0.7];
const T_DOT_IN = [0.7, 0.9];
const T_ARC_IN = [0.85, 0.95];
const T_LAP = [0.9, 2.5];
const T_ARC_OUT = [2.5, 2.8];
const T_LAUNCH = [2.5, 2.95];
const T_KNOB_OUT = [2.55, 2.85];
const T_EXPAND = [2.6, 3.1];
const T_FALL = [2.95, 3.45];
const T_IMPACT = 3.45;
const T_DOT_OUT = [3.45, 3.52];
const T_GREEN = [3.45, 3.65];
const T_DONE_IN = [3.5, 3.8];
const T_DONE_OUT = [5.3, 5.5];
const T_CYAN = [5.3, 5.6];
const T_RETURN = [5.3, 5.9];
const T_KNOB_IN = [5.4, 5.75];
const T_TO_ARROW = [5.4, 5.9];
const T_LABEL_IN = [5.55, 5.9];
const TOTAL = 6;

// How high the dot is thrown above the button before it drops in.
const APEX = 44;
const DOT_R = 5.5;

const ARROW = 'M0 -7.5V7.5M-6 1.5L0 7.5L6 1.5';

function seg(t: number, window: number[]) {
  'worklet';
  return Math.min(1, Math.max(0, (t - window[0]) / (window[1] - window[0])));
}
function easeOut(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x) * (1 - x);
}
function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
function easeOutBack(x: number) {
  'worklet';
  const c = 1.7;
  return 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
}

/** Pill width: collapses to a circle, opens into the result pill, then returns to full width. */
function pillWidth(t: number, still: boolean) {
  'worklet';
  if (still) return WIDTH;
  return (
    WIDTH +
    (HEIGHT - WIDTH) * easeInOut(seg(t, T_COLLAPSE)) +
    (DONE_WIDTH - HEIGHT) * easeOutBack(seg(t, T_EXPAND)) +
    (WIDTH - DONE_WIDTH) * easeInOut(seg(t, T_RETURN))
  );
}

/** How far the bottom edge sags: a damped wobble set off by the dot landing. */
function wobble(t: number, still: boolean) {
  'worklet';
  const since = t - T_IMPACT;
  if (still || since <= 0 || since > 1.2) return 0;
  return 7 * Math.exp(-5.5 * since) * Math.sin(16 * since);
}

/** The pill's outline, with the bottom edge bent by `sag`. */
function outline(w: number, sag: number) {
  'worklet';
  const left = CX - w / 2;
  const right = CX + w / 2;
  const top = BLEED;
  const path = Skia.Path.Make();
  path.moveTo(left + R, top);
  path.lineTo(right - R, top);
  path.arcToOval(Skia.XYWHRect(right - HEIGHT, top, HEIGHT, HEIGHT), -90, 180, false);
  path.quadTo(CX, top + HEIGHT + sag * 2, left + R, top + HEIGHT);
  path.arcToOval(Skia.XYWHRect(left, top, HEIGHT, HEIGHT), 90, 180, false);
  path.close();
  return path;
}

/** 0 = download arrow, 1 = stop square. */
function stopMorph(t: number, still: boolean) {
  'worklet';
  return still ? 0 : seg(t, T_TO_STOP) - seg(t, T_TO_ARROW);
}

/** 0…1 round the ring. */
function lap(t: number) {
  'worklet';
  return easeInOut(seg(t, T_LAP));
}

/** Height of the dot above (negative) or below the button's centre line once it has left the ring. */
function flightY(t: number) {
  'worklet';
  const apex = -RING_R - APEX;
  if (t < T_LAUNCH[1]) return -RING_R - APEX * easeOut(seg(t, T_LAUNCH));
  const fall = seg(t, T_FALL);
  return apex + (R - 7 - apex) * fall * fall;
}

function Surface({ t, still }: { t: SharedValue<number>; still: boolean }) {
  const shape = useDerivedValue(() => outline(pillWidth(t.get(), still), wobble(t.get(), still)));
  const green = useDerivedValue(() => seg(t.get(), T_GREEN) - seg(t.get(), T_CYAN));

  // The knob keeps its place at the pill's left end, which is the middle once it is a circle. It
  // fades out there while the result pill opens around it, and comes back at the left end.
  const knobX = useDerivedValue(() => {
    const now = t.get();
    return now > T_EXPAND[0] && now < T_KNOB_IN[0] ? CX : CX - pillWidth(now, still) / 2 + R;
  });
  const knobShown = useDerivedValue(() => (still ? 1 : 1 - seg(t.get(), T_KNOB_OUT) + seg(t.get(), T_KNOB_IN)));
  const knob = useDerivedValue(() => [{ translateX: knobX.get() }, { translateY: CY }, { scale: 0.6 + 0.4 * knobShown.get() }]);
  const flash = useDerivedValue(() => (t.get() > 0 ? 0.8 * (1 - seg(t.get(), T_FLASH)) : 0));

  const arrow = useDerivedValue(() => [{ rotate: stopMorph(t.get(), still) * 0.75 * Math.PI }]);
  const arrowOpacity = useDerivedValue(() => 1 - Math.min(1, stopMorph(t.get(), still) / 0.45));
  const square = useDerivedValue(() => {
    const m = stopMorph(t.get(), still);
    return [{ rotate: (m - 1) * 0.75 * Math.PI }, { scale: 0.6 + 0.4 * m }];
  });
  const squareOpacity = useDerivedValue(() => seg(stopMorph(t.get(), still), [0.3, 0.7]));

  // A white ring spreads from where the knob was pressed.
  const rippleR = useDerivedValue(() => KNOB_R + 14 * easeOut(seg(t.get(), T_RIPPLE)));
  const rippleOpacity = useDerivedValue(() => (still || t.get() <= 0 ? 0 : 0.9 * (1 - seg(t.get(), T_RIPPLE))));

  // Progress: a white dot laps the ring and leaves a white arc behind it.
  const arc = useDerivedValue(() => {
    const path = Skia.Path.Make();
    const sweep = 360 * lap(t.get());
    if (sweep > 0.5) path.addArc(Skia.XYWHRect(CX - RING_R, CY - RING_R, RING_R * 2, RING_R * 2), -90, Math.min(sweep, 359.9));
    return path;
  });
  const arcOpacity = useDerivedValue(() => (still ? 0 : seg(t.get(), T_ARC_IN) * (1 - seg(t.get(), T_ARC_OUT))));

  // Then the dot is thrown off the top, turns green and drops into the opened pill.
  const dot = useDerivedValue(() => {
    const now = t.get();
    const r = still ? 0 : DOT_R * easeOutBack(seg(now, T_DOT_IN)) * (1 - seg(now, T_DOT_OUT));
    if (now <= T_LAP[1]) {
      const a = (-90 + 360 * lap(now)) * (Math.PI / 180);
      return { x: CX + RING_R * Math.cos(a), y: CY + RING_R * Math.sin(a), rx: r, ry: r };
    }
    // Faster means longer and thinner.
    const speed = now < T_LAUNCH[1] ? 1 - seg(now, T_LAUNCH) : seg(now, T_FALL);
    return { x: CX, y: CY + flightY(now), rx: r * (1 - 0.2 * speed), ry: r * (1 + 0.5 * speed) };
  });
  const dotRect = useDerivedValue(() => {
    const d = dot.get();
    return Skia.XYWHRect(d.x - d.rx, d.y - d.ry, d.rx * 2, d.ry * 2);
  });
  const dotGreen = useDerivedValue(() => seg(t.get(), [T_LAUNCH[0], T_LAUNCH[0] + 0.3]));

  return (
    <Canvas style={styles.canvas}>
      <Path path={shape} style="stroke" strokeWidth={6} color="rgba(2,209,254,0.7)">
        <BlurMask blur={7} style="normal" />
      </Path>
      <Path path={shape} color="#2c2e2f" />
      <Path path={shape} style="stroke" strokeWidth={STROKE} color={CYAN} />
      <Path path={shape} style="stroke" strokeWidth={STROKE} color={GREEN} opacity={green} />
      <Path path={arc} style="stroke" strokeWidth={2.8} strokeCap="round" color="#ffffff" opacity={arcOpacity} />

      <Circle cx={BLEED + R} cy={CY} r={rippleR} style="stroke" strokeWidth={2} color="#ffffff" opacity={rippleOpacity} />

      <Group transform={knob} opacity={knobShown}>
        <Circle cx={0} cy={0} r={KNOB_R} color={CYAN} />
        <Circle cx={0} cy={0} r={KNOB_R} color="#ffffff" opacity={flash} />
        <Group transform={arrow} origin={vec(0, 0)}>
          <Path path={ARROW} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#ffffff" opacity={arrowOpacity} />
        </Group>
        <Group transform={square} origin={vec(0, 0)}>
          <RoundedRect x={-5.5} y={-5.5} width={11} height={11} r={1.5} color="#ffffff" opacity={squareOpacity} />
        </Group>
      </Group>

      <Oval rect={dotRect} color="#ffffff" />
      <Oval rect={dotRect} color={GREEN} opacity={dotGreen} />
    </Canvas>
  );
}

/**
 * Neon "download" pill with a round arrow knob. A tap collapses it to a circle around the knob,
 * a white dot laps the outline as progress, is thrown off the top and drops, green, into the pill
 * that opens to say "done!", its outline wobbling from the hit; then it returns.
 */
export function NeonDownloadButton({ label = 'download', doneLabel = 'done!', onPress, autoPlay = false }: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [busy, setBusy] = useState(false);
  const t = useSharedValue(0);

  // One run: start the clock, and go back to idle when it has run out.
  useEffect(() => {
    if (!busy) return;
    // Reanimated drops animations under reduced motion, which would leave the button stuck, so the
    // clock opts back in; the shape changes and the dot are switched off instead.
    const run = (to: number, seconds: number) =>
      withTiming(to, { duration: seconds * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never });
    // The clock ends where it began (label showing), so the rewind at the end is invisible.
    t.set(withSequence(ReduceMotion.Never, run(0, 0), run(TOTAL, TOTAL), run(0, 0)));
    const timer = setTimeout(() => setBusy(false), TOTAL * 1000);
    return () => clearTimeout(timer);
  }, [busy, t]);

  useEffect(() => {
    if (!autoPlay || reducedMotion || busy || pressed) return;
    const auto = setTimeout(() => setBusy(true), AUTO_PAUSE_MS);
    return () => clearTimeout(auto);
  }, [autoPlay, reducedMotion, busy, pressed]);

  // Without motion the label simply gives way to the result when the outline turns green.
  const labelOut = reducedMotion ? T_GREEN : T_LABEL_OUT;
  const slide = reducedMotion ? 0 : 12;
  const labelStyle = useAnimatedStyle(() => {
    const out = seg(t.get(), labelOut);
    const back = seg(t.get(), T_LABEL_IN);
    return {
      opacity: 1 - out + back,
      transform: [{ translateY: slide * (back > 0 ? 1 - easeOut(back) : easeOut(out)) }],
    };
  });
  const doneStyle = useAnimatedStyle(() => {
    const shown = seg(t.get(), T_DONE_IN);
    const out = seg(t.get(), T_DONE_OUT);
    return {
      opacity: shown - out,
      transform: [{ translateY: slide * (1 - easeOut(shown)) + slide * easeOut(out) }],
    };
  });

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
        <View pointerEvents="none" style={styles.surface}>
          <Surface t={t} still={reducedMotion} />
        </View>
        <Animated.Text style={[styles.label, styles.idle, labelStyle]} numberOfLines={1}>
          {label}
        </Animated.Text>
        <Animated.Text style={[styles.label, doneStyle]} numberOfLines={1}>
          {doneLabel}
        </Animated.Text>
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
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  canvas: {
    width: WIDTH + BLEED * 2,
    height: HEIGHT + BLEED * 2,
  },
  label: {
    position: 'absolute',
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '300',
    letterSpacing: 0.2,
  },
  // The idle label is centred in the space to the right of the knob.
  idle: {
    left: HEIGHT - 8,
    right: 0,
    textAlign: 'center',
  },
});
