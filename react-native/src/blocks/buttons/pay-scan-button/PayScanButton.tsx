import { BlurMask, Canvas, Group, LinearGradient, Path, RoundedRect, Skia, vec } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cubicBezier,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type Props = {
  label?: string;
  /** Shown once the scan has finished. */
  doneLabel?: string;
  onPress?: () => void;
  /** Called when a run reaches the "complete" state. */
  onComplete?: () => void;
  /** Replays pay → scan → complete by itself with a pause between runs. */
  autoPlay?: boolean;
};

const WIDTH = 180;
const HEIGHT = 60;
// The canvas overflows the button by this much on every side to fit the shadow.
const BLEED = 30;
const AUTOPLAY_PAUSE_MS = 1500;

// Card icon: centre offset from the pill's centre and half-size; it morphs into the scan frame.
const CARD_DX = -29.5;
const CARD_HW = 22.5;
const CARD_HH = 14.5;
const CARD_R = 3.5;
const FRAME_HALF = 13.5;
const FRAME_R = 5;
const FRAME_ARM = 7.5;
const LABEL_LEFT = 97;
const INK = '#f1edff';

// One run, in seconds. `frame(END)` equals `frame(0)`, so runs chain without a reset.
const T_SCAN = 1.45;
const T_SCAN_END = 3.85;
const T_DONE = 4.3;
const T_RETURN = 6.6;
const END = 7.4;

const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);
const TAU = Math.PI * 2;

function seg(t: number, a: number, b: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - a) / (b - a)));
}
function easeOut(x: number) {
  'worklet';
  return 1 - (1 - x) * (1 - x) * (1 - x);
}
function easeInOut(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}

/** Everything the button shows at time `t` of a run. Shared, number for number, with the native ports. */
function frame(t: number, still: boolean) {
  'worklet';
  const away = t < T_RETURN;
  const first = t < 3;
  return {
    // Pill width: shrinks to a circle for the scan, widens again for the result.
    w: first
      ? WIDTH + (HEIGHT - WIDTH) * easeInOut(seg(t, 0.45, 0.95))
      : HEIGHT + (WIDTH - HEIGHT) * easeInOut(seg(t, 4.1, 4.6)),
    // Label wipes: fraction of the text revealed from its left edge.
    pay: first ? 1 - easeOut(seg(t, 0, 0.35)) : easeOut(seg(t, 6.75, 7.15)),
    done: easeOut(seg(t, T_DONE + 0.1, 4.85)) * (1 - easeOut(seg(t, 6.3, 6.65))),
    // Card stripe and chip.
    detail: first ? 1 - seg(t, 0, 0.25) : easeOut(seg(t, 6.95, 7.3)),
    // Card → centred square, then the square's sides open into four brackets.
    morph: away ? easeInOut(seg(t, 0.12, 0.6)) : 0,
    bracket: away ? easeOut(seg(t, 0.9, 1.25)) : 0,
    face: away ? easeOut(seg(t, 1.1, T_SCAN)) : 0,
    icon: away ? 1 - seg(t, 4.0, 4.25) : 1,
    // The card outline draws itself back in at the end.
    outline: away ? 1 : easeInOut(seg(t, T_RETURN, 7.1)),
    scan: seg(t, T_SCAN, T_SCAN + 0.2) * (1 - seg(t, T_SCAN_END - 0.15, T_SCAN_END)),
    scanY: still ? 0 : -Math.cos((TAU * (t - T_SCAN)) / 1.25) * 8,
  };
}

const K = 0.5523;

/** One rounded corner at (px, py) with arms `ax`/`ay` long pointing towards (dx, dy). */
function corner(
  path: ReturnType<typeof Skia.Path.Make>,
  px: number,
  py: number,
  dx: number,
  dy: number,
  ax: number,
  ay: number,
  r: number,
  horizontalFirst: boolean,
) {
  'worklet';
  const c = r * (1 - K);
  if (horizontalFirst) {
    path.moveTo(px + dx * ax, py);
    path.lineTo(px + dx * r, py);
    path.cubicTo(px + dx * c, py, px, py + dy * c, px, py + dy * r);
    path.lineTo(px, py + dy * ay);
  } else {
    path.moveTo(px, py + dy * ay);
    path.lineTo(px, py + dy * r);
    path.cubicTo(px, py + dy * c, px + dx * c, py, px + dx * r, py);
    path.lineTo(px + dx * ax, py);
  }
}

function Surface({ t, still }: { t: SharedValue<number>; still: boolean }) {
  const cx = BLEED + WIDTH / 2;
  const cy = BLEED + HEIGHT / 2;

  const f = useDerivedValue(() => frame(t.get(), still));

  const pillX = useDerivedValue(() => cx - f.get().w / 2);
  const pillW = useDerivedValue(() => f.get().w);
  const shadowX = useDerivedValue(() => cx - f.get().w / 2 + 5);

  // Card outline / scan frame: four corners whose arms meet (closed shape) or pull back (brackets).
  const outline = useDerivedValue(() => {
    const { morph, bracket } = f.get();
    const x = cx + CARD_DX * (1 - morph);
    const hw = CARD_HW + (FRAME_HALF - CARD_HW) * morph;
    const hh = CARD_HH + (FRAME_HALF - CARD_HH) * morph;
    const r = CARD_R + (FRAME_R - CARD_R) * morph;
    const ax = hw + (FRAME_ARM - hw) * bracket;
    const ay = hh + (FRAME_ARM - hh) * bracket;
    const path = Skia.Path.Make();
    corner(path, x + hw, cy - hh, -1, 1, ax, ay, r, true);
    corner(path, x + hw, cy + hh, -1, -1, ax, ay, r, false);
    corner(path, x - hw, cy + hh, 1, -1, ax, ay, r, true);
    corner(path, x - hw, cy - hh, 1, 1, ax, ay, r, false);
    return path;
  });
  const outlineEnd = useDerivedValue(() => f.get().outline);
  const iconOpacity = useDerivedValue(() => f.get().icon);

  const stripe = useDerivedValue(() => {
    const d = f.get().detail;
    const left = cx + CARD_DX - CARD_HW;
    const y = cy - CARD_HH + 9.5;
    const path = Skia.Path.Make();
    path.moveTo(left, y);
    path.lineTo(left + CARD_HW * 2 * d, y);
    return path;
  });
  const detailOpacity = useDerivedValue(() => Math.min(1, f.get().detail * 3));
  const chipOpacity = useDerivedValue(() => seg(f.get().detail, 0.5, 1));

  const faceOpacity = useDerivedValue(() => f.get().face * f.get().icon);
  const faceScale = useDerivedValue(() => [{ scale: 0.6 + 0.4 * f.get().face }]);
  const scanOpacity = useDerivedValue(() => f.get().scan);
  const scanMove = useDerivedValue(() => [{ translateY: f.get().scanY }]);

  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      <RoundedRect x={shadowX} y={BLEED + 8} width={pillW} height={HEIGHT} r={HEIGHT / 2} color="rgba(122,116,214,0.34)">
        <BlurMask blur={9} style="normal" />
      </RoundedRect>
      <RoundedRect x={pillX} y={BLEED} width={pillW} height={HEIGHT} r={HEIGHT / 2}>
        <LinearGradient start={vec(0, BLEED)} end={vec(0, BLEED + HEIGHT)} colors={['#b3a7fa', '#ab9ff6']} />
      </RoundedRect>

      <Group opacity={iconOpacity}>
        <Path
          path={outline}
          end={outlineEnd}
          style="stroke"
          strokeWidth={1.7}
          strokeCap="round"
          strokeJoin="round"
          color={INK}
        />
      </Group>
      <Path path={stripe} style="stroke" strokeWidth={1.7} color={INK} opacity={detailOpacity} />
      <RoundedRect
        x={cx + CARD_DX + CARD_HW - 14.5}
        y={cy + CARD_HH - 11}
        width={9.5}
        height={6}
        r={1.8}
        style="stroke"
        strokeWidth={1.5}
        color={INK}
        opacity={chipOpacity}
      />

      <Group opacity={faceOpacity} transform={faceScale} origin={vec(cx, cy)}>
        <Path
          path={`M${cx - 5.5} ${cy - 4.5}v3.6M${cx + 5.5} ${cy - 4.5}v3.6M${cx - 4} ${cy + 3.6}Q${cx} ${cy + 7.6} ${cx + 4} ${cy + 3.6}`}
          style="stroke"
          strokeWidth={1.7}
          strokeCap="round"
          color={INK}
        />
      </Group>
      <Group transform={scanMove}>
        <Path
          path={`M${cx - 10.5} ${cy}H${cx + 10.5}`}
          style="stroke"
          strokeWidth={1.7}
          strokeCap="round"
          color={INK}
          opacity={scanOpacity}
        />
      </Group>
    </Canvas>
  );
}

/** A label that is wiped in and out from its left edge; `reveal` picks the fraction from a frame. */
function WipeLabel({
  text,
  t,
  still,
  which,
  style,
}: {
  text: string;
  t: SharedValue<number>;
  still: boolean;
  which: 'pay' | 'done';
  style: object;
}) {
  const width = useSharedValue(0);
  // The clip window slides left while the text slides right inside it, so the text stays put.
  const clip = useAnimatedStyle(() => {
    const hidden = (1 - frame(t.get(), still)[which]) * width.get();
    return { transform: [{ translateX: -hidden }] };
  });
  const inner = useAnimatedStyle(() => {
    const hidden = (1 - frame(t.get(), still)[which]) * width.get();
    return { transform: [{ translateX: hidden }] };
  });
  return (
    <Animated.View style={[style, styles.clip, clip]}>
      <Animated.View style={inner} onLayout={(e) => width.set(e.nativeEvent.layout.width)}>
        <Text style={styles.label} numberOfLines={1}>
          {text}
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Lavender "PAY" pill. A tap folds it into a round button where the card icon becomes a face-scan
 * frame, a scan line sweeps the face, then the pill widens again to say "COMPLETE" and resets.
 */
export function PayScanButton({
  label = 'PAY',
  doneLabel = 'COMPLETE',
  onPress,
  onComplete,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [pressed, setPressed] = useState(false);
  const [phase, setPhase] = useState<'idle' | 'scanning' | 'done'>('idle');

  const t = useSharedValue(0);
  const busy = useRef(false);
  const alive = useRef(true);
  const completed = useRef(onComplete);
  useEffect(() => {
    completed.current = onComplete;
  }, [onComplete]);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const run = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;
    t.set(0);
    // The run is a sequence of states, not decoration, so it also plays with reduced motion
    // (where only the sweeping scan line is frozen).
    t.set(withTiming(END, { duration: END * 1000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    setPhase('scanning');
    await sleep(T_DONE * 1000);
    if (alive.current) {
      setPhase('done');
      completed.current?.();
    }
    await sleep((T_RETURN - T_DONE) * 1000);
    if (alive.current) setPhase('idle');
    await sleep((END - T_RETURN) * 1000);
    busy.current = false;
  }, [t]);

  useEffect(() => {
    if (!autoPlay) return;
    let cancelled = false;
    (async () => {
      while (!cancelled) {
        await sleep(AUTOPLAY_PAUSE_MS);
        if (cancelled) return;
        await run();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [autoPlay, run]);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={phase === 'done' ? doneLabel : label}
      accessibilityState={{ busy: phase === 'scanning' }}
      onPress={() => {
        onPress?.();
        run();
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
        <WipeLabel text={label} t={t} still={reducedMotion} which="pay" style={styles.pay} />
        <View pointerEvents="none" style={styles.center}>
          <WipeLabel text={doneLabel} t={t} still={reducedMotion} which="done" style={styles.done} />
        </View>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: WIDTH,
    height: HEIGHT,
    justifyContent: 'center',
  },
  surface: {
    position: 'absolute',
    left: -BLEED,
    top: -BLEED,
  },
  clip: {
    overflow: 'hidden',
  },
  pay: {
    position: 'absolute',
    left: LABEL_LEFT,
  },
  center: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  done: {},
  label: {
    color: INK,
    fontSize: 19,
    fontWeight: '700',
    letterSpacing: 0.6,
  },
});
