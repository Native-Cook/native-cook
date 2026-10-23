import { BlurMask, Canvas, Circle, Group, LinearGradient, Path, RoundedRect, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef, useState } from 'react';
import { type GestureResponderEvent, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  type SharedValue,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  /** Controlled count. Leave undefined to let the button keep its own. */
  value?: number;
  onValueChange?: (value: number) => void;
  /** Plays the reference's demo by itself: a ghost fingertip taps, drags and resets the counter. */
  autoPlay?: boolean;
  accessibilityLabel?: string;
};

const WIDTH = 164;
const HEIGHT = 64;
const KNOB_R = 26;
// Distance of the − and + glyphs from the pill's ends.
const GLYPH_INSET = 33;
const GLYPH_ARM = 8.5;
const FLASH_R = 15;
// How far the knob can be dragged sideways (±1) and pulled down (reset).
const MAX_X = 48;
const MAX_Y = 38;
// The whole pill gives a little when the knob is pulled down.
const PILL_GIVE = 7;
const BALL_R = 10;
// The canvas overflows the button by this much on every side to fit the pulled knob, its shadow and the ball.
const BLEED = 64;
const MIN = 0;
const MAX = 99;
const DRAG_SLOP = 6;
const COMMIT = 0.6;
const GHOST_RESUME_MS = 3000;

const CX = WIDTH / 2;
const CY = HEIGHT / 2;

const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.45, 0, 0.55, 1);
// Overshoots a little: the knob snaps back like it is on a spring.
const EASE_BACK = Easing.bezier(0.34, 1.56, 0.64, 1);

const MINUS = `M${-GLYPH_ARM} 0H${GLYPH_ARM}`;
const PLUS = `${MINUS}M0 ${-GLYPH_ARM}V${GLYPH_ARM}`;
const CROSS = 'M-6 -6L6 6M6 -6L-6 6';

type Motion = {
  kx: SharedValue<number>;
  ky: SharedValue<number>;
  press: SharedValue<number>;
  flashMinus: SharedValue<number>;
  flashPlus: SharedValue<number>;
  ballX: SharedValue<number>;
  ballY: SharedValue<number>;
  ballAlpha: SharedValue<number>;
  ballScale: SharedValue<number>;
};

function Surface({ kx, ky, press, flashMinus, flashPlus, ballX, ballY, ballAlpha, ballScale }: Motion) {
  const x = BLEED;
  const y = BLEED;
  const cx = x + CX;
  const cy = y + CY;

  // -1..1 sideways, 0..1 downwards.
  const give = useDerivedValue(() => [{ translateY: (PILL_GIVE * ky.get()) / MAX_Y }]);
  const dark = useDerivedValue(() => Math.max(Math.abs(kx.get()) / MAX_X, ky.get() / MAX_Y));
  // The glyph the knob moves away from dims, the one it approaches lights up; both go when pulling
  // down. A tapped glyph turns dark on its white flash.
  const minusDim = useDerivedValue(() => 1 - 0.75 * Math.max(kx.get() / MAX_X, 0) - ky.get() / MAX_Y);
  const plusDim = useDerivedValue(() => 1 - 0.75 * Math.max(-kx.get() / MAX_X, 0) - ky.get() / MAX_Y);
  const minusLit = useDerivedValue(() => Math.max(-kx.get() / MAX_X, 0));
  const plusLit = useDerivedValue(() => Math.max(kx.get() / MAX_X, 0));
  const cross = useDerivedValue(() => ky.get() / MAX_Y);

  const knob = useDerivedValue(() => [{ translateX: kx.get() }, { translateY: ky.get() }]);
  const knobScale = useDerivedValue(() => [{ scale: 1 - 0.04 * press.get() }]);
  const lift = useDerivedValue(() => 0.55 * press.get());

  const ball = useDerivedValue(() => [
    { translateX: x + ballX.get() },
    { translateY: y + ballY.get() },
    { scale: ballScale.get() },
  ]);

  return (
    <Canvas style={styles.canvas}>
      <Group transform={give}>
        <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color="#282828" />
        <RoundedRect x={x} y={y} width={WIDTH} height={HEIGHT} r={HEIGHT / 2} color="#1e1e1e" opacity={dark} />

        <Circle cx={x + GLYPH_INSET} cy={cy} r={FLASH_R} color="#ffffff" opacity={flashMinus} />
        <Circle cx={x + WIDTH - GLYPH_INSET} cy={cy} r={FLASH_R} color="#ffffff" opacity={flashPlus} />

        <Group transform={[{ translateX: x + GLYPH_INSET }, { translateY: cy }]} style="stroke" strokeWidth={1.5}>
          <Path path={MINUS} color="#7a7a7a" opacity={minusDim} />
          <Path path={MINUS} color="#f2f2f2" opacity={minusLit} />
          <Path path={MINUS} color="#4a4a4a" opacity={flashMinus} />
        </Group>
        <Group transform={[{ translateX: x + WIDTH - GLYPH_INSET }, { translateY: cy }]} style="stroke" strokeWidth={1.5}>
          <Path path={PLUS} color="#7a7a7a" opacity={plusDim} />
          <Path path={PLUS} color="#f2f2f2" opacity={plusLit} />
          <Path path={PLUS} color="#4a4a4a" opacity={flashPlus} />
        </Group>
        <Group transform={[{ translateX: cx }, { translateY: cy }]} style="stroke" strokeWidth={1.5}>
          <Path path={CROSS} color="#9a9a9a" opacity={cross} />
        </Group>
      </Group>

      <Group transform={knob}>
        {/* A tight shadow at rest and a wide soft one while the knob is held. */}
        <Circle cx={cx} cy={cy + 14} r={KNOB_R} color="#000000" opacity={lift}>
          <BlurMask blur={14} style="normal" />
        </Circle>
        <Circle cx={cx} cy={cy + 2} r={KNOB_R} color="rgba(0,0,0,0.4)">
          <BlurMask blur={3} style="normal" />
        </Circle>
        <Group transform={knobScale} origin={vec(cx, cy)}>
          <Circle cx={cx} cy={cy} r={KNOB_R} color="#3b3b3b" />
        </Group>
      </Group>

      <Group transform={ball} opacity={ballAlpha}>
        <Circle cx={0} cy={0} r={BALL_R}>
          <LinearGradient
            start={vec(-3, -BALL_R)}
            end={vec(3, BALL_R)}
            colors={['rgba(178,178,178,0.9)', 'rgba(84,84,84,0.9)']}
          />
        </Circle>
        <Circle cx={0} cy={0} r={BALL_R - 0.5} style="stroke" strokeWidth={1} color="rgba(255,255,255,0.22)" />
      </Group>
    </Canvas>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Stepper pill "−  0  +" whose number sits on a draggable knob. Tap − or + (or the knob) to count,
 * drag the knob sideways and let go for ±1, pull it down and let go to reset.
 */
export function TallyCounterButton({
  value,
  onValueChange,
  autoPlay = false,
  accessibilityLabel = 'Counter',
}: Props) {
  const reducedMotion = useReducedMotion();
  const [inner, setInner] = useState(0);
  const count = value ?? inner;

  const kx = useSharedValue(0);
  const ky = useSharedValue(0);
  const press = useSharedValue(0);
  const flashMinus = useSharedValue(0);
  const flashPlus = useSharedValue(0);
  const ballX = useSharedValue(CX + 4);
  const ballY = useSharedValue(HEIGHT + 60);
  const ballAlpha = useSharedValue(0);
  const ballScale = useSharedValue(1);

  // Everything the gesture and the ghost script need, kept in refs so both can stay stable.
  const latest = useRef({ count, controlled: value !== undefined, onValueChange });
  useEffect(() => {
    latest.current = { count, controlled: value !== undefined, onValueChange };
  }, [count, value, onValueChange]);

  const setCount = useCallback((next: number) => {
    const clamped = Math.max(MIN, Math.min(MAX, next));
    if (clamped === latest.current.count) return;
    latest.current.count = clamped;
    if (!latest.current.controlled) setInner(clamped);
    latest.current.onValueChange?.(clamped);
  }, []);

  const pressKnob = useCallback(
    (down: boolean) => press.set(withTiming(down ? 1 : 0, { duration: down ? 130 : 260, easing: EASE_OUT })),
    [press],
  );
  const releaseKnob = useCallback(() => {
    kx.set(withTiming(0, { duration: 380, easing: EASE_BACK }));
    ky.set(withTiming(0, { duration: 380, easing: EASE_BACK }));
    pressKnob(false);
  }, [kx, ky, pressKnob]);
  const flash = useCallback(
    (side: 'minus' | 'plus', down: boolean) =>
      (side === 'minus' ? flashMinus : flashPlus).set(
        withTiming(down ? 0.92 : 0, { duration: down ? 90 : 380, easing: EASE_OUT }),
      ),
    [flashMinus, flashPlus],
  );

  // --- Ghost fingertip: replays the reference's demo while autoPlay is on. ---
  const ghost = useRef({ cancelled: true });
  const [ghostRun, setGhostRun] = useState(0);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (!autoPlay || reducedMotion) return;
    const run = { cancelled: false };
    ghost.current = run;

    const wait = async (ms: number) => {
      await sleep(ms);
      if (run.cancelled) throw run;
    };
    const moveBall = (x: number, y: number, ms: number) => {
      ballX.set(withTiming(x, { duration: ms, easing: EASE_IN_OUT }));
      ballY.set(withTiming(y, { duration: ms, easing: EASE_IN_OUT }));
    };
    const touch = (down: boolean) => ballScale.set(withTiming(down ? 0.82 : 1, { duration: 120, easing: EASE_OUT }));
    const tapKnob = async () => {
      touch(true);
      pressKnob(true);
      await wait(170);
      setCount(latest.current.count + 1);
      touch(false);
      pressKnob(false);
      await wait(360);
    };
    const tapSide = async (side: 'minus' | 'plus') => {
      moveBall(side === 'minus' ? GLYPH_INSET + 4 : WIDTH - GLYPH_INSET + 4, CY + 5, 420);
      await wait(520);
      for (let i = 0; i < 2; i++) {
        touch(true);
        flash(side, true);
        await wait(170);
        setCount(latest.current.count + (side === 'minus' ? -1 : 1));
        touch(false);
        flash(side, false);
        await wait(420);
      }
    };
    const drag = async (dx: number, dy: number) => {
      moveBall(CX + 4, CY + 6, 420);
      await wait(480);
      touch(true);
      pressKnob(true);
      await wait(160);
      kx.set(withTiming(dx, { duration: 420, easing: EASE_OUT }));
      ky.set(withTiming(dy, { duration: 420, easing: EASE_OUT }));
      ballX.set(withTiming(CX + 4 + dx, { duration: 420, easing: EASE_OUT }));
      ballY.set(withTiming(CY + 6 + dy, { duration: 420, easing: EASE_OUT }));
      await wait(700);
      setCount(dy > 0 ? MIN : latest.current.count + Math.sign(dx));
      touch(false);
      releaseKnob();
      await wait(240);
    };

    (async () => {
      try {
        while (true) {
          await wait(900);
          ballX.set(CX + 4);
          ballY.set(HEIGHT + 60);
          ballAlpha.set(withTiming(1, { duration: 250 }));
          moveBall(CX + 4, CY + 6, 560);
          await wait(660);
          await tapKnob();
          await tapKnob();
          await drag(MAX_X, 0);
          await drag(MAX_X, 0);
          await drag(-MAX_X, 0);
          await tapSide('plus');
          await tapSide('minus');
          await drag(0, MAX_Y);
          moveBall(CX + 4, HEIGHT + 60, 520);
          ballAlpha.set(withTiming(0, { duration: 420 }));
          await wait(1100);
        }
      } catch {
        // Cancelled: the user took over or the button went away.
      }
    })();

    return () => {
      // Let go of whatever the ghost was holding.
      run.cancelled = true;
      ballAlpha.set(withTiming(0, { duration: 160 }));
      ballScale.set(1);
      releaseKnob();
      flash('minus', false);
      flash('plus', false);
    };
  }, [autoPlay, reducedMotion, ghostRun, ballX, ballY, ballAlpha, ballScale, kx, ky, pressKnob, releaseKnob, flash, setCount]);

  useEffect(
    () => () => {
      if (resumeTimer.current) clearTimeout(resumeTimer.current);
    },
    [],
  );

  // --- Touch: one responder for the whole pill, split into knob / − / + by where the finger lands. ---
  const touch = useRef<{ zone: 'knob' | 'minus' | 'plus'; axis: 'x' | 'y' | null; x: number; y: number }>({
    zone: 'knob',
    axis: null,
    x: 0,
    y: 0,
  });

  const onGrant = (e: GestureResponderEvent) => {
    // Take over from the ghost, wherever it was.
    ghost.current.cancelled = true;
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    ballAlpha.set(withTiming(0, { duration: 160 }));
    flash('minus', false);
    flash('plus', false);

    const { locationX, locationY, pageX, pageY } = e.nativeEvent;
    if (Math.hypot(locationX - CX, locationY - CY) <= KNOB_R + 4) {
      touch.current = { zone: 'knob', axis: null, x: pageX, y: pageY };
      pressKnob(true);
    } else {
      const zone = locationX < CX ? 'minus' : 'plus';
      touch.current = { zone, axis: null, x: pageX, y: pageY };
      releaseKnob();
      flash(zone, true);
    }
    if (process.env.EXPO_OS !== 'web') {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    }
  };

  const onMove = (e: GestureResponderEvent) => {
    const t = touch.current;
    if (t.zone !== 'knob') return;
    const dx = e.nativeEvent.pageX - t.x;
    const dy = e.nativeEvent.pageY - t.y;
    if (t.axis === null) {
      if (Math.max(Math.abs(dx), Math.abs(dy)) < DRAG_SLOP) return;
      t.axis = Math.abs(dx) >= Math.abs(dy) ? 'x' : 'y';
    }
    if (t.axis === 'x') kx.set(Math.max(-MAX_X, Math.min(MAX_X, dx)));
    else ky.set(Math.max(0, Math.min(MAX_Y, dy)));
  };

  const onEnd = (commit: boolean) => {
    const { zone, axis } = touch.current;
    if (zone === 'knob') {
      if (commit) {
        if (axis === null) setCount(latest.current.count + 1);
        else if (axis === 'x' && Math.abs(kx.get()) > MAX_X * COMMIT) setCount(latest.current.count + Math.sign(kx.get()));
        else if (axis === 'y' && ky.get() > MAX_Y * COMMIT) setCount(MIN);
      }
      releaseKnob();
    } else {
      if (commit) setCount(latest.current.count + (zone === 'minus' ? -1 : 1));
      flash(zone, false);
    }
    resumeTimer.current = setTimeout(() => setGhostRun((n) => n + 1), GHOST_RESUME_MS);
  };

  const numberStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: kx.get() }, { translateY: ky.get() }, { scale: 1 - 0.04 * press.get() }],
  }));

  return (
    <View
      onStartShouldSetResponder={() => true}
      onResponderTerminationRequest={() => false}
      onResponderGrant={onGrant}
      onResponderMove={onMove}
      onResponderRelease={() => onEnd(true)}
      onResponderTerminate={() => onEnd(false)}
      style={styles.button}
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={{ text: String(count) }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={(e) => setCount(count + (e.nativeEvent.actionName === 'increment' ? 1 : -1))}
    >
      <View pointerEvents="none" style={styles.surface}>
        <Surface
          kx={kx}
          ky={ky}
          press={press}
          flashMinus={flashMinus}
          flashPlus={flashPlus}
          ballX={ballX}
          ballY={ballY}
          ballAlpha={ballAlpha}
          ballScale={ballScale}
        />
      </View>
      <Animated.View pointerEvents="none" style={[styles.number, numberStyle]}>
        <Text style={styles.digits} numberOfLines={1}>
          {count}
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
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
  number: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  digits: {
    color: '#ffffff',
    fontSize: 24,
    fontWeight: '500',
    fontVariant: ['tabular-nums'],
  },
});
