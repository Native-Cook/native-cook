import { Canvas, Circle, Oval, Path, RoundedRect } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CLOSE_AT, CYCLE, EYE_FROM, EYE_SPEED, FADE, FIELD, Field, LOOP, MORPH, OPEN_AT, USER_HOLD } from './Field';

type Props = {
  title?: string;
  subtitle?: string;
  primaryLabel?: string;
  secondaryLabel?: string;
  /** The legal note reads: `legal`, `termsLabel`, `legalAnd`, `privacyLabel`. */
  legal?: string;
  termsLabel?: string;
  legalAnd?: string;
  privacyLabel?: string;
  background?: string;
  /** The strokes, the title and the first button. */
  ink?: string;
  /** The subtitle and the legal note. */
  mute?: string;
  /** The second button. */
  surface?: string;
  /** Opens and closes the field by itself, twice every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when the field starts to open (true) or to close (false). */
  onToggle?: (open: boolean) => void;
  onPrimary?: () => void;
  onSecondary?: () => void;
  onTerms?: () => void;
  onPrivacy?: () => void;
};

const BACKGROUND = '#000000';
const INK = '#FFFFFF';
const MUTE = '#8E8E93';
const SURFACE = '#2C2C2E';

const NEVER = ReduceMotion.Never;
// The finger's pull comes on fast and lets go slowly, both on a strong ease-out.
const PULL_IN = 150;
const PULL_OUT = 450;
const PULL_EASE = Easing.bezier(0.23, 1, 0.32, 1);
// A finger that moves less than this has tapped.
const SLOP = 10;
// As long as anyone will watch: the eye keeps circling while the field is open.
const FOREVER = 3600;

const MARK = 24;
const MARK_TOP = 7;
const SIDE = 24;
const TITLE_LINE = 56;
const SUBTITLE_GAP = 10;
const SUBTITLE_LINE = 20;
const BUTTONS_GAP = 43;
const BUTTON = 56;
const BUTTON_GAP = 10;
const LEGAL_GAP = 28;
const LEGAL_LINE = 16;
// The foot's height, from the title's top to the last line of the legal note.
const FOOT = TITLE_LINE + SUBTITLE_GAP + SUBTITLE_LINE + BUTTONS_GAP + BUTTON * 2 + BUTTON_GAP + LEGAL_GAP + LEGAL_LINE * 2;
// The field's centre sits a little above the middle of the room between the mark and the title,
// and the field shrinks when that room is less than this.
const FIELD_AT = 0.435;
const FIELD_ROOM = 310;
const PRESS_SCALE = 0.97;
const PRESS_MS = 130;

const FLAP = 'M3.2 6.6L10 11.4L16.8 6.6';

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

type PillProps = { label: string; color: string; ink: string; onPress: () => void; children?: ReactNode };

function Pill({ label, color, ink, onPress, children }: PillProps) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label}>
      {({ pressed }) => (
        <Animated.View
          style={[
            styles.pill,
            { backgroundColor: color, transform: [{ scale: pressed ? PRESS_SCALE : 1 }], transitionProperty: 'transform', transitionDuration: PRESS_MS },
          ]}
        >
          {children}
          <Text style={[styles.pillLabel, { color: ink }]} numberOfLines={1}>
            {label}
          </Text>
        </Animated.View>
      )}
    </Pressable>
  );
}

/**
 * A black welcome screen whose hero is a field of 324 short white strokes. Packed, they are a
 * small shimmering square; opening, the square is torn into a pinwheel and settles as a vortex
 * of rings round a wandering eye, and closing runs the same way back. A tap on the field opens
 * or closes it, and a finger on it pushes the strokes away and turns them like filings.
 */
export function FlowFieldWelcome({
  title = 'Welcome',
  subtitle = 'Your journey starts from here',
  primaryLabel = 'Continue with Phone',
  secondaryLabel = 'Continue with Email',
  legal = 'By pressing on “Continue with...” you agree\nto our ',
  termsLabel = 'Terms of Service',
  legalAnd = ' and ',
  privacyLabel = 'Privacy Policy',
  background = BACKGROUND,
  ink = INK,
  mute = MUTE,
  surface = SURFACE,
  autoPlay = false,
  onToggle,
  onPrimary,
  onSecondary,
  onTerms,
  onPrivacy,
}: Props) {
  const still = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;

  const [open, setOpen] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // A finger is on the field: the loop waits.
  const [holding, setHolding] = useState(false);

  // How far the field has opened, at a steady pace; the field eases it per stroke.
  const progress = useSharedValue(0);
  const opening = useSharedValue(false);
  const eye = useSharedValue(EYE_FROM);
  // Seconds, round and round, for the twitch of the packed strokes.
  const clock = useSharedValue(0);
  const finger = useSharedValue({ x: 0, y: 0 });
  const pull = useSharedValue(0);

  useEffect(() => {
    if (still) return;
    clock.set(0);
    clock.set(withRepeat(withTiming(LOOP, { duration: LOOP * 1000, easing: Easing.linear, reduceMotion: NEVER }), -1, false, undefined, NEVER));
    return () => cancelAnimation(clock);
  }, [clock, still]);

  useEffect(
    () => () => {
      cancelAnimation(progress);
      cancelAnimation(eye);
      cancelAnimation(pull);
    },
    [progress, eye, pull],
  );

  /** Opens the field into the vortex, or packs it again, from wherever it is. */
  const morph = (next: boolean) => {
    setOpen(next);
    onToggle?.(next);
    opening.set(next);
    const to = next ? 1 : 0;
    if (still) {
      progress.set(withTiming(to, { duration: FADE * 1000, easing: Easing.linear, reduceMotion: NEVER }));
      return;
    }
    const left = Math.abs(to - progress.get());
    progress.set(
      withTiming(to, { duration: MORPH * 1000 * left, easing: Easing.linear, reduceMotion: NEVER }, (finished) => {
        // Packed again: the eye goes back to where it starts.
        if (finished && !next) eye.set(EYE_FROM);
      }),
    );
    if (next) eye.set(withTiming(eye.get() + EYE_SPEED * FOREVER, { duration: FOREVER * 1000, easing: Easing.linear, reduceMotion: NEVER }));
    else cancelAnimation(eye);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ morph, open });
  useEffect(() => {
    latest.current = { morph, open };
  });

  useEffect(() => {
    if (!autoPlay || holding) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      after(origin + OPEN_AT * 1000 - Date.now(), () => latest.current.morph(true));
      after(origin + CLOSE_AT * 1000 - Date.now(), () => latest.current.morph(false));
      after(origin + CYCLE * 1000 - Date.now(), () => cycle(origin + CYCLE * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else
      after(USER_HOLD * 1000, () => {
        // The loop starts over from the packed field.
        const wait = latest.current.open ? MORPH * 1000 : 0;
        if (latest.current.open) latest.current.morph(false);
        after(wait, () => cycle(Date.now()));
      });
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, holding]);

  /** A touch takes over from the autoPlay loop, which starts again from the packed field a while later. */
  const touched = () => setTouches((n) => n + 1);

  const toggle = () => {
    touched();
    tap();
    morph(!open);
  };

  const released = () => {
    setHolding(false);
    touched();
  };

  const markBottom = insets.top + MARK_TOP + MARK;
  const bottom = Math.max(insets.bottom, 20);
  const titleTop = size.height - bottom - FOOT;
  const room = titleTop - markBottom;
  const fieldScale = Math.min(1, room / FIELD_ROOM);
  const fieldSize = FIELD * fieldScale;
  const fieldY = markBottom + room * FIELD_AT;

  const magnet = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      finger.set({ x: (event.x - fieldSize / 2) / fieldScale, y: (event.y - fieldSize / 2) / fieldScale });
      pull.set(withTiming(1, { duration: PULL_IN, easing: PULL_EASE, reduceMotion: NEVER }));
      scheduleOnRN(setHolding, true);
    })
    .onUpdate((event) => {
      finger.set({ x: (event.x - fieldSize / 2) / fieldScale, y: (event.y - fieldSize / 2) / fieldScale });
    })
    .onFinalize(() => {
      pull.set(withTiming(0, { duration: PULL_OUT, easing: PULL_EASE, reduceMotion: NEVER }));
      scheduleOnRN(released);
    });

  const press = Gesture.Tap().maxDistance(SLOP).onEnd(() => {
    scheduleOnRN(toggle);
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  return (
    <View style={[styles.screen, { backgroundColor: background }]} onLayout={onLayout}>
      <StatusBar style="light" />

      <Canvas style={[styles.mark, { top: insets.top + MARK_TOP }]} pointerEvents="none">
        <Circle cx={12} cy={12} r={12} color={ink} />
        <Circle cx={12} cy={9.4} r={3.1} color={background} />
        <Oval x={5.8} y={15.2} width={12.4} height={9} color={background} />
      </Canvas>

      <GestureDetector gesture={Gesture.Simultaneous(magnet, press)}>
        <View
          style={[styles.field, { top: fieldY - fieldSize / 2, width: fieldSize, height: fieldSize }]}
          accessible
          accessibilityRole="button"
          accessibilityLabel="Pattern"
          accessibilityState={{ expanded: open }}
          onAccessibilityTap={toggle}
        >
          <Field progress={progress} opening={opening} clock={clock} eye={eye} finger={finger} pull={pull} still={still} scale={fieldScale} color={ink} />
        </View>
      </GestureDetector>

      <View style={[styles.foot, { paddingBottom: bottom }]} pointerEvents="box-none">
        <Text style={[styles.title, { color: ink }]} numberOfLines={1} adjustsFontSizeToFit>
          {title}
        </Text>
        <Text style={[styles.subtitle, { color: mute }]} numberOfLines={1}>
          {subtitle}
        </Text>
        <View style={styles.buttons}>
          <Pill
            label={primaryLabel}
            color={ink}
            ink={background}
            onPress={() => {
              tap();
              onPrimary?.();
            }}
          />
          <Pill
            label={secondaryLabel}
            color={surface}
            ink={ink}
            onPress={() => {
              tap();
              onSecondary?.();
            }}
          >
            <Canvas style={styles.envelope}>
              <RoundedRect x={2} y={4.5} width={16} height={11} r={2.5} style="stroke" strokeWidth={1.6} color={ink} />
              <Path path={FLAP} style="stroke" strokeWidth={1.6} strokeCap="round" strokeJoin="round" color={ink} />
            </Canvas>
          </Pill>
        </View>
        <Text style={[styles.legal, { color: mute }]}>
          {legal}
          <Text style={[styles.link, { color: ink }]} onPress={onTerms} accessibilityRole="link">
            {termsLabel}
          </Text>
          {legalAnd}
          <Text style={[styles.link, { color: ink }]} onPress={onPrivacy} accessibilityRole="link">
            {privacyLabel}
          </Text>
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  mark: {
    position: 'absolute',
    alignSelf: 'center',
    width: MARK,
    height: MARK,
  },
  field: {
    position: 'absolute',
    alignSelf: 'center',
  },
  foot: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: SIDE,
  },
  title: {
    height: TITLE_LINE,
    fontSize: 46,
    lineHeight: TITLE_LINE,
    fontWeight: '600',
    letterSpacing: -1,
    textAlign: 'center',
    includeFontPadding: false,
  },
  subtitle: {
    marginTop: SUBTITLE_GAP,
    fontSize: 15,
    lineHeight: SUBTITLE_LINE,
    textAlign: 'center',
    includeFontPadding: false,
  },
  buttons: {
    marginTop: BUTTONS_GAP,
    gap: BUTTON_GAP,
  },
  pill: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pillLabel: {
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '600',
    includeFontPadding: false,
  },
  envelope: {
    width: 20,
    height: 20,
    marginRight: 8,
  },
  legal: {
    marginTop: LEGAL_GAP,
    minHeight: LEGAL_LINE * 2,
    fontSize: 11,
    lineHeight: LEGAL_LINE,
    textAlign: 'center',
    includeFontPadding: false,
  },
  link: {
    textDecorationLine: 'underline',
  },
});
