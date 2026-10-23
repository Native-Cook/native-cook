import { BlurMask, Canvas, Group, LinearGradient, RoundedRect, rect, rrect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import {
  Easing,
  ReduceMotion,
  type SharedValue,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** Controlled value. Leave undefined to let the switch keep its own state. */
  value?: boolean;
  defaultValue?: boolean;
  onValueChange?: (value: boolean) => void;
  /** Accessibility label; the switch has no visible text. */
  label?: string;
  /** Flip back and forth by itself with a pause between flips. Tapping still works. */
  autoPlay?: boolean;
};

const WIDTH = 90;
const HEIGHT = 48;
const THUMB = 42;
const PAD = (HEIGHT - THUMB) / 2;
const TRAVEL = WIDTH - PAD * 2 - THUMB;
// The canvas overflows the switch by this much on every side to fit its soft shadow.
const BLEED = 24;
const TRAVEL_MS = 420;
const AUTO_PAUSE_MS = 1500;
// Mid-travel the thumb is this much wider (and a little flatter), like a soft pad being dragged.
const STRETCH = 0.2;
const FLATTEN = 0.07;
// While a finger is down the thumb spreads this far towards the other side.
const PRESS_SPREAD = 6;
// The thumb's top face: a slightly smaller disc pushed up, leaving a bevel along the bottom.
const FACE_INSET = 2.2;
const FACE_LIFT = 1.5;

const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);

function Surface({
  p,
  press,
  squish,
}: {
  p: SharedValue<number>;
  press: SharedValue<number>;
  /** 0 freezes the stretch for reduced motion. */
  squish: number;
}) {
  const x = BLEED;
  const y = BLEED;
  const w = WIDTH;
  const h = HEIGHT;
  const r = h / 2;
  const cy = y + h / 2;

  const clip = useMemo(() => rrect(rect(x, y, w, h), r, r), [x, y, w, h, r]);

  // Thumb box: travels with `p`, stretches around the middle of the trip and spreads away from
  // the wall it is resting against while pressed.
  const tw = useDerivedValue(
    () => THUMB * (1 + squish * STRETCH * Math.sin(Math.PI * p.get())) + PRESS_SPREAD * press.get(),
  );
  const th = useDerivedValue(() => THUMB * (1 - squish * FLATTEN * Math.sin(Math.PI * p.get())));
  const tx = useDerivedValue(
    () => x + PAD + THUMB / 2 + TRAVEL * p.get() + PRESS_SPREAD * press.get() * (0.5 - p.get()) - tw.get() / 2,
  );
  const ty = useDerivedValue(() => cy - th.get() / 2);
  const tr = useDerivedValue(() => th.get() / 2);

  const shadowY = useDerivedValue(() => ty.get() + 3);
  const faceX = useDerivedValue(() => tx.get() + FACE_INSET);
  const faceY = useDerivedValue(() => ty.get() + FACE_INSET - FACE_LIFT);
  const faceW = useDerivedValue(() => tw.get() - FACE_INSET * 2);
  const faceH = useDerivedValue(() => th.get() - FACE_INSET * 2);
  const faceR = useDerivedValue(() => (th.get() - FACE_INSET * 2) / 2);

  return (
    <Canvas style={{ width: w + BLEED * 2, height: h + BLEED * 2 }}>
      {/* The switch sits in a shallow well: a soft shadow below, a light lip on the lower edge. */}
      <RoundedRect x={x} y={y + 5} width={w} height={h} r={r} color="rgba(148,154,180,0.3)">
        <BlurMask blur={7} style="normal" />
      </RoundedRect>
      <RoundedRect x={x + 0.5} y={y + 1.2} width={w} height={h} r={r} color="rgba(255,255,255,0.9)">
        <BlurMask blur={0.8} style="normal" />
      </RoundedRect>

      <RoundedRect x={x} y={y} width={w} height={h} r={r} color="#d3d7e1" />
      <Group opacity={p}>
        <RoundedRect x={x} y={y} width={w} height={h} r={r} color="#82b56e" />
      </Group>

      <Group clip={clip}>
        {/* Inner shadow: a blurred ring pushed down-right so only its top-left band falls inside. */}
        <RoundedRect
          x={x - 3 + 1.2}
          y={y - 3 + 2.4}
          width={w + 6}
          height={h + 6}
          r={r + 3}
          style="stroke"
          strokeWidth={6}
          color="rgba(74,78,108,0.5)"
        >
          <BlurMask blur={2.6} style="normal" />
        </RoundedRect>
        <Group opacity={p}>
          <RoundedRect
            x={x - 3 + 1.2}
            y={y - 3 + 2.4}
            width={w + 6}
            height={h + 6}
            r={r + 3}
            style="stroke"
            strokeWidth={6}
            color="rgba(44,86,40,0.6)"
          >
            <BlurMask blur={2.6} style="normal" />
          </RoundedRect>
        </Group>
        <RoundedRect x={tx} y={shadowY} width={tw} height={th} r={tr} color="rgba(28,36,48,0.34)">
          <BlurMask blur={3.2} style="normal" />
        </RoundedRect>
      </Group>

      <RoundedRect x={tx} y={ty} width={tw} height={th} r={tr}>
        <LinearGradient start={vec(0, y + PAD)} end={vec(0, y + h - PAD)} colors={['#f7f7f6', '#e6e6e6', '#cfcfd1']} positions={[0, 0.6, 1]} />
      </RoundedRect>
      <RoundedRect x={faceX} y={faceY} width={faceW} height={faceH} r={faceR}>
        <LinearGradient start={vec(0, y + PAD)} end={vec(0, y + h - PAD)} colors={['#f8f8f7', '#ececeb']} />
      </RoundedRect>
    </Canvas>
  );
}

export function SquishToggle({
  value,
  defaultValue = false,
  onValueChange,
  label = 'Toggle',
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [inner, setInner] = useState(defaultValue);
  const [pressed, setPressed] = useState(false);
  const on = value ?? inner;

  const p = useSharedValue(on ? 1 : 0);
  const press = useSharedValue(0);

  useEffect(() => {
    p.set(
      withTiming(on ? 1 : 0, {
        duration: reducedMotion ? 160 : TRAVEL_MS,
        easing: EASE_IN_OUT,
        // Reanimated skips animations outright under reduced motion; the short fade opts back in.
        reduceMotion: ReduceMotion.Never,
      }),
    );
  }, [on, reducedMotion, p]);

  useEffect(() => {
    press.set(withTiming(pressed && !reducedMotion ? 1 : 0, { duration: pressed ? 130 : 260, easing: EASE_OUT }));
  }, [pressed, reducedMotion, press]);

  // Re-armed after every flip, so a tap simply restarts the pause.
  useEffect(() => {
    if (!autoPlay || reducedMotion || pressed) return;
    const timer = setTimeout(() => {
      if (value === undefined) setInner(!on);
      onValueChange?.(!on);
    }, TRAVEL_MS + AUTO_PAUSE_MS);
    return () => clearTimeout(timer);
  }, [autoPlay, reducedMotion, pressed, on, value, onValueChange]);

  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityLabel={label}
      accessibilityState={{ checked: on }}
      onPress={() => {
        if (value === undefined) setInner(!on);
        onValueChange?.(!on);
      }}
      onPressIn={() => {
        setPressed(true);
        if (process.env.EXPO_OS !== 'web') {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
        }
      }}
      onPressOut={() => setPressed(false)}
      pressRetentionOffset={16}
      style={styles.switch}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface p={p} press={press} squish={reducedMotion ? 0 : 1} />
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  switch: {
    width: WIDTH,
    height: HEIGHT,
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
});
