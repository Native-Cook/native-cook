import { Blur, Canvas, ColorMatrix, Group, LinearGradient, Paint, Path, Skia, vec } from '@shopify/react-native-skia';
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
  withTiming,
} from 'react-native-reanimated';

export type LiquidSplitOption = 'circle' | 'triangle';

type Props = {
  label?: string;
  doneLabel?: string;
  /** Controlled state. Leave undefined to let the button keep its own. */
  expanded?: boolean;
  defaultExpanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  /** One of the two round options was tapped while split. */
  onOptionPress?: (option: LiquidSplitOption) => void;
  /** Split and merge by itself with a pause in each state. Tapping still works. */
  autoPlay?: boolean;
};

const HEIGHT = 52;
const PILL_W = 114;
const CIRCLE = 52;
const GAP = 12;
const DONE_W = 104;
const WIDTH = CIRCLE + GAP + CIRCLE + GAP + DONE_W;
// Centres and widths of the three pieces once split: ○, △ and the "Done" pill.
const PIECE_CX = [CIRCLE / 2, CIRCLE + GAP + CIRCLE / 2, WIDTH - DONE_W / 2];
const PIECE_W = [CIRCLE, CIRCLE, DONE_W];
// The canvas overflows the button by this much on every side to fit the blur the goo is made from.
const BLEED = 40;
const SPLIT_MS = 1150;
const AUTO_OPEN_MS = 2000;
const AUTO_CLOSED_MS = 1400;

// Gooey look: blur everything, then turn the soft alpha back into a hard edge. Pieces that are
// close enough share blur, so they stay bridged until they have really moved apart.
const GOO_BLUR = 9;
const GOO_MATRIX = [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 20, -9.5];
const RIM = 1.1;
// Mid-transition the glass whites out and swells by this much.
const INFLATE = 4;

const INK = '#0b0b0d';
const CSS_EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

function seg(s: number, from: number, to: number) {
  'worklet';
  return Math.min(1, Math.max(0, (s - from) / (to - from)));
}
function smooth(u: number) {
  'worklet';
  return u * u * (3 - 2 * u);
}
/** How far the pieces have moved from the single pill (0) to their own places (1). */
function spread(s: number) {
  'worklet';
  const u = seg(s, 0.12, 1);
  return 1 - (1 - u) * (1 - u) * (1 - u);
}
function flash(s: number) {
  'worklet';
  return smooth(seg(s, 0.03, 0.2)) * (1 - smooth(seg(s, 0.45, 0.65)));
}
function pieceCx(index: number, s: number) {
  'worklet';
  return WIDTH / 2 + (PIECE_CX[index] - WIDTH / 2) * spread(s);
}

/** The three pieces as one path. `inset` shrinks them (rim), `dy` drops them (shadow). */
function blob(s: number, inset: number, dy: number) {
  'worklet';
  const m = spread(s);
  const grow = INFLATE * flash(s) - inset;
  const h = HEIGHT + grow * 2;
  const path = Skia.Path.Make();
  for (let i = 0; i < 3; i++) {
    const w = PILL_W + (PIECE_W[i] - PILL_W) * m + grow * 2;
    const x = BLEED + pieceCx(i, s) - w / 2;
    path.addRRect(Skia.RRectXY(Skia.XYWHRect(x, BLEED + dy - grow, w, h), h / 2, h / 2));
  }
  return path;
}

/** Still pieces for the cross-fade used with reduced motion: the pill alone, or the split trio. */
function still(split: boolean, inset: number, dy: number) {
  const h = HEIGHT - inset * 2;
  const path = Skia.Path.Make();
  const add = (cx: number, width: number) => {
    const w = width - inset * 2;
    path.addRRect(Skia.RRectXY(Skia.XYWHRect(BLEED + cx - w / 2, BLEED + dy + inset, w, h), h / 2, h / 2));
  };
  if (split) PIECE_CX.forEach((cx, i) => add(cx, PIECE_W[i]));
  else add(WIDTH / 2, PILL_W);
  return path;
}

const TOP = vec(0, BLEED);
const BOTTOM = vec(0, BLEED + HEIGHT);
const RIM_COLORS = ['#f1f2f5', '#cfd1d6', '#f6f7fa'];
const BODY_COLORS = ['#cbcdd2', '#b9bcc2', '#d5d7dc'];
const GRADIENT_STOPS = [0, 0.55, 1];

function Goo({ s }: { s: SharedValue<number> }) {
  const shadow = useDerivedValue(() => blob(s.get(), 0, 7));
  const outer = useDerivedValue(() => blob(s.get(), 0, 0));
  const inner = useDerivedValue(() => blob(s.get(), RIM, 0));
  const flashOpacity = useDerivedValue(() => flash(s.get()));
  const glowOpacity = useDerivedValue(() => flash(s.get()) * 0.7);

  return (
    <>
      <Group layer={<Paint opacity={0.3}><Blur blur={GOO_BLUR} /></Paint>}>
        <Path path={shadow} color="#1e222c" />
      </Group>
      <Group layer={<Paint opacity={glowOpacity}><Blur blur={12} /></Paint>}>
        <Path path={outer} color="#ffffff" />
      </Group>
      <Group layer={<Paint><Blur blur={GOO_BLUR} /><ColorMatrix matrix={GOO_MATRIX} /></Paint>}>
        <Path path={outer}>
          <LinearGradient start={TOP} end={BOTTOM} colors={RIM_COLORS} positions={GRADIENT_STOPS} />
        </Path>
      </Group>
      <Group layer={<Paint><Blur blur={GOO_BLUR} /><ColorMatrix matrix={GOO_MATRIX} /></Paint>}>
        <Path path={inner}>
          <LinearGradient start={TOP} end={BOTTOM} colors={BODY_COLORS} positions={GRADIENT_STOPS} />
        </Path>
      </Group>
      {/* Faded by an outer layer: opacity on the goo paint itself would be applied before the
          threshold and shrink the shape instead of fading it. */}
      <Group layer={<Paint opacity={flashOpacity} />}>
        <Group layer={<Paint><Blur blur={GOO_BLUR} /><ColorMatrix matrix={GOO_MATRIX} /></Paint>}>
          <Path path={outer} color="#ffffff" />
        </Group>
      </Group>
    </>
  );
}

function Still({ split, opacity }: { split: boolean; opacity: SharedValue<number> }) {
  return (
    // Faded as one layer, so the rim never shows through the body mid-fade.
    <Group layer={<Paint opacity={opacity} />}>
      <Group layer={<Paint opacity={0.3}><Blur blur={GOO_BLUR} /></Paint>}>
        <Path path={still(split, 0, 7)} color="#1e222c" />
      </Group>
      <Path path={still(split, 0, 0)}>
        <LinearGradient start={TOP} end={BOTTOM} colors={RIM_COLORS} positions={GRADIENT_STOPS} />
      </Path>
      <Path path={still(split, RIM, 0)}>
        <LinearGradient start={TOP} end={BOTTOM} colors={BODY_COLORS} positions={GRADIENT_STOPS} />
      </Path>
    </Group>
  );
}

function Surface({ s, reducedMotion }: { s: SharedValue<number>; reducedMotion: boolean }) {
  const cy = BLEED + HEIGHT / 2;
  const content = useDerivedValue(() => seg(s.get(), 0.5, 0.75));
  const closed = useDerivedValue(() => 1 - s.get());
  const circle = useDerivedValue(() => [{ translateX: BLEED + pieceCx(0, s.get()) }, { translateY: cy }]);
  const triangle = useDerivedValue(() => [{ translateX: BLEED + pieceCx(1, s.get()) }, { translateY: cy }]);

  return (
    <Canvas style={{ width: WIDTH + BLEED * 2, height: HEIGHT + BLEED * 2 }}>
      {reducedMotion ? (
        <>
          <Still split={false} opacity={closed} />
          <Still split opacity={s} />
        </>
      ) : (
        <Goo s={s} />
      )}
      <Group opacity={content}>
        <Group transform={circle}>
          <Path path="M-8.5 0a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0Z" style="stroke" strokeWidth={2} color={INK} />
        </Group>
        <Group transform={triangle}>
          <Path path="M0 -8.6L9.2 6.8L-9.2 6.8Z" style="stroke" strokeWidth={2} strokeJoin="round" color={INK} />
        </Group>
      </Group>
    </Canvas>
  );
}

export function LiquidSplitButton({
  label = 'Select',
  doneLabel = 'Done',
  expanded,
  defaultExpanded = false,
  onExpandedChange,
  onOptionPress,
  autoPlay = false,
}: Props) {
  const reducedMotion = useReducedMotion();
  const [inner, setInner] = useState(defaultExpanded);
  const [pressed, setPressed] = useState(false);
  const open = expanded ?? inner;

  // 0 = one "Select" pill, 1 = split into three.
  const s = useSharedValue(open ? 1 : 0);

  useEffect(() => {
    s.set(
      withTiming(open ? 1 : 0, {
        duration: reducedMotion ? 220 : SPLIT_MS,
        easing: Easing.linear,
        // Reanimated skips animations outright under reduced motion; the short cross-fade opts back in.
        reduceMotion: ReduceMotion.Never,
      }),
    );
  }, [open, reducedMotion, s]);

  // Re-armed after every change, so a tap simply restarts the pause.
  useEffect(() => {
    if (!autoPlay || reducedMotion || pressed) return;
    const timer = setTimeout(() => {
      if (expanded === undefined) setInner(!open);
      onExpandedChange?.(!open);
    }, SPLIT_MS + (open ? AUTO_OPEN_MS : AUTO_CLOSED_MS));
    return () => clearTimeout(timer);
  }, [autoPlay, reducedMotion, pressed, open, expanded, onExpandedChange]);

  const setOpen = (next: boolean) => {
    // The touch target that was just tapped is about to be swapped out.
    setPressed(false);
    if (expanded === undefined) setInner(next);
    onExpandedChange?.(next);
  };

  const press = {
    onPressIn: () => {
      setPressed(true);
      if (process.env.EXPO_OS !== 'web') {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }
    },
    onPressOut: () => setPressed(false),
    pressRetentionOffset: 16,
  };

  const labelStyle = useAnimatedStyle(() => ({ opacity: 1 - seg(s.get(), 0.02, 0.16) }));
  const doneStyle = useAnimatedStyle(() => ({ opacity: seg(s.get(), 0.5, 0.75) }));

  return (
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
        <Surface s={s} reducedMotion={reducedMotion} />
      </View>
      <View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={styles.selectSlot}
      >
        <Animated.Text style={[styles.label, labelStyle]} numberOfLines={1}>
          {label}
        </Animated.Text>
      </View>
      <View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={styles.doneSlot}
      >
        <Animated.Text style={[styles.label, doneStyle]} numberOfLines={1}>
          {doneLabel}
        </Animated.Text>
      </View>

      {open ? (
        <>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Circle"
            onPress={() => onOptionPress?.('circle')}
            style={[styles.hit, { left: 0, width: CIRCLE }]}
            {...press}
          />
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Triangle"
            onPress={() => onOptionPress?.('triangle')}
            style={[styles.hit, { left: CIRCLE + GAP, width: CIRCLE }]}
            {...press}
          />
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={doneLabel}
            onPress={() => setOpen(false)}
            style={[styles.hit, { left: WIDTH - DONE_W, width: DONE_W }]}
            {...press}
          />
        </>
      ) : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={label}
          accessibilityState={{ expanded: false }}
          onPress={() => setOpen(true)}
          style={[styles.hit, { left: (WIDTH - PILL_W) / 2, width: PILL_W }]}
          {...press}
        />
      )}
    </Animated.View>
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
  selectSlot: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  doneSlot: {
    position: 'absolute',
    right: 0,
    width: DONE_W,
    height: HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hit: {
    position: 'absolute',
    top: 0,
    height: HEIGHT,
  },
  label: {
    color: INK,
    fontSize: 17,
    fontWeight: '500',
  },
});
