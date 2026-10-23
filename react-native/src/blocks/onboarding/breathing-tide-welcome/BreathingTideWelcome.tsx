import { Canvas, Path, Picture, Skia, createPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { paintPicture, paintTide } from './paint';
import {
  AT_C,
  AT_LOW,
  AT_TOP,
  BACK_AT,
  END,
  ENTER_AT,
  EXHALE,
  INHALE,
  LOOP,
  PANEL,
  PANEL_SLIDE,
  PRESS_AT,
  SPLASH,
  TICK_AT,
  TICK_DAMPING,
  TICK_STIFFNESS,
  TIDE,
  USER_HOLD,
  inAlpha,
  logoAlpha,
  outAlpha,
  welcome,
} from './tide';

type Props = {
  /** The name next to the dot on the splash. */
  wordmark?: string;
  inhaleLabel?: string;
  exhaleLabel?: string;
  title?: string;
  subtitle?: string;
  /** The line next to the checkbox, in turns plain and as a link. */
  consent?: string[];
  primaryLabel?: string;
  secondaryLabel?: string;
  /** The tide and the yellow panel, as 0xRRGGBB. */
  tideColor?: number;
  panelColor?: number;
  /** Takes one breath, shows the welcome and comes back to the splash by itself, every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when the breath is complete and the welcome shows. */
  onBreath?: () => void;
  onAgree?: (agreed: boolean) => void;
  onPrimary?: () => void;
  onSecondary?: () => void;
};

const CONSENT = ['I agree to Stillwater’s ', 'Terms & Conditions', ' and acknowledge the ', 'Privacy Policy', '.'];

const PAPER = '#FEFEFE';
const DOT = '#FD6703';
const INK = '#232323';
const TEXT = '#313131';
const SOFT = '#585858';
const BLUE = '#136EFB';
const BLUSH = '#FDF6F6';

/** The splash's logo, as a fraction of the screen's height. */
const LOGO_AT = 0.47;
const LOGO_DOT = 58;
const LOGO_GAP = 14;
// Where things are, in points under the top safe area…
const WORD_Y = 17;
const PICTURE_Y = 131;
const CREST_Y = 289;
/** The white area's edge is this much lower at the screen's edges than at its crest. */
const CREST_DROP = 32;
const TITLE_BELOW = 72;
const SUBTITLE_BELOW = 114;
// …and above the bottom safe area.
const SECONDARY_UP = 43;
const PRIMARY_UP = 110;
const CONSENT_UP = 182;
const BUTTON = 50;
const BUTTON_MARGIN = 26.5;
const CONSENT_MARGIN = 48;
const BOX = 18;
/** A tick in an 18 × 18 box. */
const TICK = Skia.Path.MakeFromSVGString('M4.5 9.3L7.8 12.5L13.6 5.9')!;

const ROUNDED = Platform.select({ ios: 'ui-rounded', default: undefined });
const LINEAR = { easing: Easing.linear, reduceMotion: ReduceMotion.Never };

function hex(color: number) {
  return `#${color.toString(16).padStart(6, '0')}`;
}

/** The splash has rested: the logo fades and the tide comes. `late`: how long ago this was due. */
function enter(scene: SharedValue<number>, late: number) {
  'worklet';
  if (scene.get() !== 0) return;
  scene.set(withTiming(AT_LOW, { duration: Math.max(0, AT_LOW - late) * 1000, ...LINEAR }));
}

/**
 * A finger goes down: the breath runs to its end and on to the welcome, unless it is let go before
 * the top. The loop says where the scene is `due` to be: its press may run in the same frame as
 * the step before it, when the tide has not moved yet, and must not be lost for that.
 */
function pressIn(scene: SharedValue<number>, late: number, due = 0) {
  'worklet';
  const s = Math.max(scene.get(), due);
  if (s <= 0 || s > AT_TOP) return;
  scene.set(withTiming(AT_C, { duration: Math.max(0, AT_C - s - late) * 1000, ...LINEAR }));
}

/** Let go before the top: the tide sinks back, at the pace of an exhale, and waits. */
function letGo(scene: SharedValue<number>) {
  'worklet';
  const s = scene.get();
  if (s <= 0 || s >= AT_TOP) return;
  const duration = s < AT_LOW ? AT_LOW - s : ((s - AT_LOW) * EXHALE) / INHALE;
  scene.set(withTiming(AT_LOW, { duration: duration * 1000, ...LINEAR }));
}

/** A run that was cut short (the screen went away, or was refreshed) leaves the scene between two rests: back to the splash. */
function settle(scene: SharedValue<number>) {
  'worklet';
  const s = scene.get();
  if (s !== 0 && s !== AT_LOW && s !== AT_C) scene.set(0);
}

/** From the welcome, or from the waiting tide, back to the splash. */
function goBack(scene: SharedValue<number>, late: number) {
  'worklet';
  const s = scene.get();
  if (s === AT_LOW) return scene.set(withTiming(0, { duration: AT_LOW * 1000, ...LINEAR }));
  if (s !== AT_C) return;
  scene.set(
    withTiming(END, { duration: Math.max(0, END - AT_C - late) * 1000, ...LINEAR }, (finished) => {
      // The end of the way back is the splash at rest.
      if (finished) scene.set(0);
    }),
  );
}

/**
 * A first launch that starts with a breath. The splash gives way to an orange tide with a
 * sleeping face on it; holding the screen makes it rise ("Breathe in"), and at the top it sinks
 * again by itself, slower ("Breathe out"). After that one breath the welcome fades in: a picture
 * on a yellow panel, a consent box and two buttons.
 */
export function BreathingTideWelcome({
  wordmark = 'stillwater',
  inhaleLabel = 'Breathe in',
  exhaleLabel = 'Breathe out',
  title = 'Welcome to Stillwater',
  subtitle = 'Support for all of life’s moments',
  consent = CONSENT,
  primaryLabel = 'Create an account',
  secondaryLabel = 'Log in',
  tideColor = TIDE,
  panelColor = PANEL,
  autoPlay = false,
  onBreath,
  onAgree,
  onPrimary,
  onSecondary,
}: Props) {
  const calm = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  // Where the scene is: 0 on the splash, 1 at the low tide, 2 on the welcome, 3 on the way.
  const [place, setPlace] = useState(0);
  const [agreed, setAgreed] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // The breath in progress: whose it is, for the haptic at its top and for a touch that interrupts.
  const hold = useRef<'hand' | 'auto' | null>(null);

  const scene = useSharedValue(0);
  // Seconds since the block appeared, for the ornaments.
  const clock = useSharedValue(0);
  // The checkbox: 0 empty, 1 ticked, with a bounce on the way.
  const tick = useSharedValue(0);

  // The welcome is there to be seen. Only then does the clock go and is its picture painted anew:
  // a canvas that is redrawn while nothing on the screen changes makes the UI thread wait a second
  // for a drawable, and the steps of the loop that were due meanwhile come late or not at all.
  const seen = useDerivedValue(() => welcome(scene.get()) > 0);

  useFrameCallback((frame) => {
    if (seen.get()) clock.set(frame.timeSinceFirstFrame / 1000);
  }, !calm);

  useEffect(() => {
    scheduleOnUI(settle, scene);
    return () => cancelAnimation(scene);
  }, [scene]);

  const topped = () => {
    if (hold.current === 'hand' && process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    hold.current = null;
  };
  const arrived = (now: number) => {
    setPlace(now);
    if (now === 2) onBreath?.();
    // The box is empty again by the time the splash is back.
    if (now === 0) setAgreed(false);
  };
  useAnimatedReaction(
    () => scene.get() >= AT_TOP,
    (now, old) => {
      if (now && old === false) scheduleOnRN(topped);
    },
  );
  useAnimatedReaction(
    () => (scene.get() === 0 ? 0 : scene.get() === AT_LOW ? 1 : scene.get() === AT_C ? 2 : 3),
    (now, old) => {
      if (now !== old) scheduleOnRN(arrived, now);
    },
  );

  useEffect(() => {
    tick.set(withSpring(agreed ? 1 : 0, { mass: 1, stiffness: TICK_STIFFNESS, damping: TICK_DAMPING }));
  }, [agreed, tick]);

  const press = (by: 'hand' | 'auto', late = 0) => {
    hold.current = by;
    scheduleOnUI(pressIn, scene, late, by === 'auto' ? AT_LOW : 0);
  };
  const release = () => {
    hold.current = null;
    scheduleOnUI(letGo, scene);
  };
  const agree = (by: 'hand' | 'auto') => {
    if (by === 'hand' && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    setAgreed(!agreed);
    onAgree?.(!agreed);
  };

  /** A touch takes over from the autoPlay loop, which starts again from the splash a while later. */
  const touched = () => {
    if (hold.current === 'auto') release();
    setTouches((n) => n + 1);
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ press, agree, place, agreed });
  useEffect(() => {
    latest.current = { press, agree, place, agreed };
  });

  useEffect(() => {
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, step: () => void) => timers.push(setTimeout(step, ms));
    // Every step is due at a fixed time after the loop began, and is told how late it runs.
    const cycle = (origin: number) => {
      const at = (seconds: number, step: (late: number) => void) => {
        const due = origin + seconds * 1000;
        after(due - Date.now(), () => step(Math.max(0, Date.now() - due) / 1000));
      };
      at(ENTER_AT, (late) => scheduleOnUI(enter, scene, late));
      at(PRESS_AT, (late) => latest.current.press('auto', late));
      at(TICK_AT, () => {
        if (!latest.current.agreed) latest.current.agree('auto');
      });
      at(BACK_AT, (late) => scheduleOnUI(goBack, scene, late));
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    // After a touch: USER_HOLD seconds of rest, then back to the splash, which the loop starts from.
    const rest = (quiet: number, returning: boolean) => {
      const now = latest.current.place;
      if (returning) return now === 0 ? cycle(Date.now()) : after(100, () => rest(0, true));
      if (now === 3) return after(100, () => rest(0, false));
      if (quiet < USER_HOLD * 1000) return after(100, () => rest(quiet + 100, false));
      if (now === 0) return cycle(Date.now());
      scheduleOnUI(goBack, scene, 0);
      after(100, () => rest(0, true));
    };
    // By hand the splash gives way by itself, and the tide waits for a finger.
    if (!autoPlay) after(SPLASH * 1000, () => scheduleOnUI(enter, scene, 0));
    else if (touches === 0) cycle(Date.now());
    else after(100, () => rest(100, false));
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, scene]);

  const bottom = Math.max(insets.bottom, 16);
  const cx = width / 2;
  const crestY = insets.top + CREST_Y;
  const pictureY = insets.top + PICTURE_Y;

  // The white area under the yellow panel has an arched top edge, like the tide.
  const sheet = useMemo(
    () =>
      Skia.PathBuilder.Make()
        .moveTo(0, crestY + CREST_DROP)
        .quadTo(cx, crestY - CREST_DROP, width, crestY + CREST_DROP)
        .lineTo(width, height)
        .lineTo(0, height)
        .close()
        .build(),
    [cx, crestY, width, height],
  );

  const tide = useDerivedValue(() => {
    const s = scene.get();
    return createPicture((canvas) => paintTide(canvas, s, calm, width, height, tideColor), { width, height });
  });
  const picture = useDerivedValue(() => {
    // The ornaments only float while the welcome is there to be seen.
    const time = calm || !seen.get() ? null : clock.get();
    return createPicture((canvas) => paintPicture(canvas, time, cx, pictureY), { width, height });
  });

  const logoStyle = useAnimatedStyle(() => ({ opacity: logoAlpha(scene.get()) }));
  const inStyle = useAnimatedStyle(() => ({ opacity: inAlpha(scene.get()) }));
  const outStyle = useAnimatedStyle(() => ({ opacity: outAlpha(scene.get()) }));
  const welcomeStyle = useAnimatedStyle(() => ({ opacity: welcome(scene.get()) }));
  const panelStyle = useAnimatedStyle(() => {
    const s = scene.get();
    return { transform: [{ translateY: calm || s > AT_C ? 0 : -PANEL_SLIDE * (1 - welcome(s)) }] };
  });
  const boxStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, tick.get())) }));
  const tickStyle = useAnimatedStyle(() => ({ transform: [{ scale: Math.max(0, tick.get()) }] }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const tap = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  const atWelcome = place === 2;

  return (
    <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style="dark" />

      <Animated.View pointerEvents="none" style={[styles.logo, { top: height * LOGO_AT - LOGO_DOT / 2 }, logoStyle]}>
        <View style={styles.logoDot} />
        <Text style={styles.wordmark} numberOfLines={1}>
          {wordmark}
        </Text>
      </Animated.View>

      <View pointerEvents="none" style={styles.fill}>
        <Canvas style={styles.fill}>
          <Picture picture={tide} />
        </Canvas>
      </View>

      <Animated.Text pointerEvents="none" style={[styles.word, { top: insets.top + WORD_Y - 16 }, inStyle]} numberOfLines={1}>
        {inhaleLabel}
      </Animated.Text>
      <Animated.Text pointerEvents="none" style={[styles.word, { top: insets.top + WORD_Y - 16 }, outStyle]} numberOfLines={1}>
        {exhaleLabel}
      </Animated.Text>

      {/* Anywhere on the screen takes the breath. */}
      <Pressable
        disabled={atWelcome}
        style={styles.fill}
        accessibilityRole="button"
        accessibilityLabel={`${inhaleLabel}. Press and hold`}
        accessibilityActions={[{ name: 'activate' }]}
        onAccessibilityAction={() => press('hand')}
        onPressIn={() => press('hand')}
        onPressOut={release}
      />

      <Animated.View
        pointerEvents={atWelcome ? 'auto' : 'none'}
        accessibilityElementsHidden={!atWelcome}
        importantForAccessibility={atWelcome ? 'auto' : 'no-hide-descendants'}
        style={[styles.fill, welcomeStyle]}
      >
        <Animated.View style={[styles.panel, { height: crestY + CREST_DROP + PANEL_SLIDE * 2, backgroundColor: hex(panelColor) }, panelStyle]} />
        <View pointerEvents="none" style={styles.fill}>
          <Canvas style={styles.fill}>
            <Path path={sheet} color={PAPER} />
          </Canvas>
        </View>
        <Animated.View pointerEvents="none" style={[styles.fill, panelStyle]}>
          <Canvas style={styles.fill}>
            <Picture picture={picture} />
          </Canvas>
        </Animated.View>

        <Text style={[styles.title, { top: crestY + TITLE_BELOW - 16 }]} numberOfLines={1}>
          {title}
        </Text>
        <Text style={[styles.subtitle, { top: crestY + SUBTITLE_BELOW - 11 }]} numberOfLines={1}>
          {subtitle}
        </Text>

        <Pressable
          style={[styles.consent, { top: height - bottom - CONSENT_UP - 22 }]}
          hitSlop={8}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: agreed }}
          onPress={() => agree('hand')}
        >
          <View style={styles.box}>
            <Animated.View style={[styles.boxFill, boxStyle]} />
            <Animated.View style={tickStyle}>
              <Canvas style={styles.tick}>
                <Path path={TICK} style="stroke" strokeWidth={2} strokeCap="round" strokeJoin="round" color="#FFFFFF" />
              </Canvas>
            </Animated.View>
          </View>
          <Text style={styles.consentText}>
            {consent.map((part, index) => (
              <Text key={index} style={index % 2 === 1 ? styles.link : undefined}>
                {part}
              </Text>
            ))}
          </Text>
        </Pressable>

        <Pressable
          disabled={!agreed}
          style={[styles.buttonSlot, { top: height - bottom - PRIMARY_UP - BUTTON / 2 }]}
          accessibilityRole="button"
          accessibilityState={{ disabled: !agreed }}
          onPress={() => {
            tap();
            onPrimary?.();
          }}
        >
          {({ pressed }) => (
            <Animated.View
              style={[styles.button, styles.primary, { opacity: !agreed ? 0.4 : pressed ? 0.8 : 1, transitionProperty: 'opacity', transitionDuration: 200 }]}
            >
              <Text style={[styles.buttonLabel, styles.primaryLabel]} numberOfLines={1}>
                {primaryLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
        <Pressable
          style={[styles.buttonSlot, { top: height - bottom - SECONDARY_UP - BUTTON / 2 }]}
          accessibilityRole="button"
          onPress={() => {
            tap();
            onSecondary?.();
          }}
        >
          {({ pressed }) => (
            <Animated.View style={[styles.button, styles.secondary, { opacity: pressed ? 0.7 : 1, transitionProperty: 'opacity', transitionDuration: 120 }]}>
              <Text style={styles.buttonLabel} numberOfLines={1}>
                {secondaryLabel}
              </Text>
            </Animated.View>
          )}
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAPER,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  logo: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: LOGO_DOT,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoDot: {
    width: LOGO_DOT,
    height: LOGO_DOT,
    borderRadius: LOGO_DOT / 2,
    marginRight: LOGO_GAP,
    backgroundColor: DOT,
  },
  wordmark: {
    color: INK,
    fontFamily: ROUNDED,
    fontSize: 34,
    lineHeight: 42,
    fontWeight: '500',
    includeFontPadding: false,
  },
  word: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: TEXT,
    fontFamily: ROUNDED,
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '600',
    includeFontPadding: false,
  },
  panel: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: -PANEL_SLIDE,
  },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: TEXT,
    fontSize: 26,
    lineHeight: 32,
    fontWeight: '600',
    includeFontPadding: false,
  },
  subtitle: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    color: SOFT,
    fontSize: 16,
    lineHeight: 22,
    includeFontPadding: false,
  },
  consent: {
    position: 'absolute',
    left: CONSENT_MARGIN,
    right: CONSENT_MARGIN,
    height: 44,
    flexDirection: 'row',
    alignItems: 'center',
  },
  box: {
    width: BOX,
    height: BOX,
    marginRight: 12,
    borderRadius: 4,
    borderWidth: 2,
    borderColor: TEXT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  boxFill: {
    position: 'absolute',
    left: -2,
    top: -2,
    width: BOX,
    height: BOX,
    borderRadius: 4,
    backgroundColor: TEXT,
  },
  tick: {
    width: BOX,
    height: BOX,
  },
  consentText: {
    flex: 1,
    color: TEXT,
    fontSize: 11,
    lineHeight: 16,
    includeFontPadding: false,
  },
  link: {
    color: BLUE,
    textDecorationLine: 'underline',
  },
  buttonSlot: {
    position: 'absolute',
    left: BUTTON_MARGIN,
    right: BUTTON_MARGIN,
    height: BUTTON,
  },
  button: {
    height: BUTTON,
    borderRadius: BUTTON / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primary: {
    backgroundColor: BLUE,
  },
  secondary: {
    backgroundColor: BLUSH,
    boxShadow: [{ offsetX: 0, offsetY: 4, blurRadius: 12, color: 'rgba(0,0,0,0.08)' }],
  },
  buttonLabel: {
    color: TEXT,
    fontSize: 15,
    lineHeight: 20,
    fontWeight: '600',
    includeFontPadding: false,
  },
  primaryLabel: {
    color: '#FFFFFF',
  },
});
