import { Canvas, Path, Picture, Skia, createPicture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  cancelAnimation,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { HORIZON, LOOP, paintScene } from './Scene';

export { LOOP };

type Props = {
  brand?: string;
  /** Two lines, split with a line break. */
  title?: string;
  continueLabel?: string;
  signInLabel?: string;
  /** The small print: what comes before the two links, the links, and the word between them. */
  legal?: string;
  termsLabel?: string;
  legalJoin?: string;
  privacyLabel?: string;
  /** Travels through the scene by itself, one round every LOOP seconds. Off, the scene stays where a drag leaves it. */
  autoPlay?: boolean;
  onContinue?: () => void;
  onSignIn?: () => void;
  onTerms?: () => void;
  onPrivacy?: () => void;
};

const SHEET = '#fafafa';
const INK = '#1c1c1e';
const MUTE = '#8e8e93';
const BUTTON_FILL = '#111111';

const SHEET_SIDE = 22;
const SHEET_HEIGHT = 315;
const SHEET_RADIUS = 20;
const BOTTOM = 27;
const INSET = 24;
const BUTTON = 49;
const PRESS_SCALE = 0.98;
const PRESS = 120;
// A shield in a 16 × 16 box and a thin arrow in an 18 × 12 box.
const SHIELD = 'M2 2.6L8 1L14 2.6L14 8Q14 12.6 8 15Q2 12.6 2 8Z';
const ARROW = 'M1 6L17 6M12.5 1.5L17 6L12.5 10.5';

// A drag of this many points up moves the camera on by one round.
const DRAG = 500;
// The fastest a release may throw the camera, in rounds a second, and how fast a throw eases
// into the camera's own pace (per second).
const THROW_MAX = 1.5;
const EASE = 3;
// The clock the camera runs on, in seconds.
const CLOCK = 1e6;
const NEVER = ReduceMotion.Never;

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/**
 * A sign-in sheet under a pale scene the camera travels through without end: into a stone archway,
 * up to the bar chart behind it until two green bars tower over the sheet, and on through them to
 * the next archway. A drag up or down over the scene moves the camera by hand.
 */
export function ArchwayZoomWelcome({
  brand = 'VERRA',
  title = 'Smarter financial moves\nawait',
  continueLabel = 'Continue with Passkey',
  signInLabel = 'Sign in with your Verra Account',
  legal = 'by proceeding, you agree to our ',
  termsLabel = 'terms of use',
  legalJoin = ' & ',
  privacyLabel = 'privacy policy',
  autoPlay = false,
  onContinue,
  onSignIn,
  onTerms,
  onPrivacy,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;

  // The camera's own pace, in rounds a second.
  const rate = autoPlay && !reducedMotion ? 1 / LOOP : 0;
  const pace = useSharedValue(rate);
  // Seconds since the screen appeared.
  const clock = useSharedValue(0);
  // Where the camera was last let go: how many rounds in, when, and how fast it was thrown.
  const from = useSharedValue({ rounds: 0, at: 0, speed: rate });
  // The rounds while a finger holds the scene, and what they were when it took it.
  const hold = useSharedValue<number | null>(null);
  const grabbed = useSharedValue(0);
  const pressed = useSharedValue(0);

  const bottom = Math.max(insets.bottom, 12) + BOTTOM;
  const sheetTop = size.height - bottom - SHEET_HEIGHT;

  // The rounds travelled: a throw eases into the camera's pace, so the speed never jumps.
  const rounds = useDerivedValue(() => {
    const held = hold.get();
    if (held !== null) return held;
    const start = from.get();
    const t = clock.get() - start.at;
    return start.rounds + pace.get() * t + ((start.speed - pace.get()) / EASE) * (1 - Math.exp(-EASE * t));
  });

  useEffect(() => {
    if (reducedMotion) return;
    clock.set(withTiming(CLOCK, { duration: CLOCK * 1000, easing: Easing.linear, reduceMotion: NEVER }));
    return () => {
      cancelAnimation(clock);
      clock.set(0);
      from.set({ rounds: 0, at: 0, speed: 0 });
    };
  }, [clock, from, reducedMotion]);

  useEffect(() => {
    // A change of pace starts from where the camera is.
    from.set({ rounds: rounds.get(), at: clock.get(), speed: pace.get() });
    pace.set(rate);
  }, [clock, from, pace, rate, rounds]);

  const pan = Gesture.Pan()
    .enabled(!reducedMotion)
    .onBegin(() => {
      grabbed.set(rounds.get());
      hold.set(rounds.get());
    })
    .onUpdate((event) => {
      // Up is forwards.
      hold.set(grabbed.get() - event.translationY / DRAG);
    })
    .onFinalize((event, success) => {
      const speed = success ? Math.max(-THROW_MAX, Math.min(THROW_MAX, -event.velocityY / DRAG)) : pace.get();
      from.set({ rounds: hold.get() ?? rounds.get(), at: clock.get(), speed });
      hold.set(null);
    });

  const picture = useDerivedValue(() => {
    const travelled = rounds.get();
    const tau = (travelled - Math.floor(travelled)) * LOOP;
    return createPicture((canvas) => {
      'worklet';
      paintScene(canvas, size.width, size.height, sheetTop - HORIZON, tau);
    }, Skia.XYWHRect(0, 0, size.width, size.height));
  });

  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS_SCALE) * pressed.get() }] }));
  const press = (down: boolean) => pressed.set(withTiming(down ? 1 : 0, { duration: PRESS, reduceMotion: NEVER }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />

      <GestureDetector gesture={pan}>
        <View style={styles.fill} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Canvas style={styles.fill}>
            <Picture picture={picture} />
          </Canvas>
        </View>
      </GestureDetector>

      <View style={[styles.sheet, { left: SHEET_SIDE, right: SHEET_SIDE, bottom }]}>
        <Text style={styles.brand} numberOfLines={1}>
          {brand}
        </Text>
        <Text style={styles.title} numberOfLines={2}>
          {title}
        </Text>
        <Animated.View style={[styles.buttonSlot, pressStyle]}>
          <Pressable
            style={styles.button}
            accessibilityRole="button"
            accessibilityLabel={continueLabel}
            onPressIn={() => press(true)}
            onPressOut={() => press(false)}
            onPress={() => {
              tick();
              onContinue?.();
            }}
          >
            <Canvas style={styles.shield}>
              <Path path={SHIELD} style="stroke" strokeWidth={1.3} strokeJoin="round" color="#ffffff" />
            </Canvas>
            <Text style={styles.buttonLabel} numberOfLines={1}>
              {continueLabel}
            </Text>
            <Canvas style={styles.arrow}>
              <Path path={ARROW} style="stroke" strokeWidth={1} strokeCap="round" strokeJoin="round" color="#ffffff" />
            </Canvas>
          </Pressable>
        </Animated.View>
        <Pressable style={styles.link} accessibilityRole="button" hitSlop={8} onPress={onSignIn}>
          {({ pressed: down }) => (
            <Text style={[styles.linkLabel, { opacity: down ? 0.5 : 1 }]} numberOfLines={1}>
              {signInLabel}
            </Text>
          )}
        </Pressable>
        <Text style={styles.legal} numberOfLines={1}>
          {legal}
          <Text style={styles.underlined} accessibilityRole="link" onPress={onTerms}>
            {termsLabel}
          </Text>
          {legalJoin}
          <Text style={styles.underlined} accessibilityRole="link" onPress={onPrivacy}>
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
    backgroundColor: '#fefefe',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  sheet: {
    position: 'absolute',
    height: SHEET_HEIGHT,
    borderRadius: SHEET_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: SHEET,
    alignItems: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 10, blurRadius: 30, color: 'rgba(0,0,0,0.1)' }],
  },
  brand: {
    marginTop: 35,
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '700',
    letterSpacing: 2.5,
    // The spacing after the last letter would push the word off centre.
    marginRight: -2.5,
    includeFontPadding: false,
  },
  title: {
    marginTop: 21,
    color: INK,
    fontSize: 21,
    lineHeight: 28,
    fontWeight: '600',
    textAlign: 'center',
    includeFontPadding: false,
  },
  buttonSlot: {
    marginTop: 25,
    alignSelf: 'stretch',
    marginHorizontal: INSET,
  },
  button: {
    height: BUTTON,
    borderRadius: 2,
    backgroundColor: BUTTON_FILL,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  shield: {
    width: 16,
    height: 16,
    marginRight: 10,
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  arrow: {
    width: 18,
    height: 12,
    marginLeft: 10,
  },
  link: {
    marginTop: 24,
  },
  linkLabel: {
    color: INK,
    fontSize: 12.5,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  legal: {
    marginTop: 30,
    color: MUTE,
    fontSize: 8,
    lineHeight: 12,
    includeFontPadding: false,
  },
  underlined: {
    textDecorationLine: 'underline',
  },
});
