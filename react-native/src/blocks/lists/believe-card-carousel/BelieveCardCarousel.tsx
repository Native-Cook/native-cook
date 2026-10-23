import { Blur, Canvas, Group, Text as SkText, useFont } from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Extrapolation,
  ReduceMotion,
  cancelAnimation,
  interpolate,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';

import { Card } from './Card';
import { CARDS, type CardData } from './cards';
import { BrandMark } from './BrandMark';
import { HOLD_MS, LOOP_MS, MORPH, PRESS, PROJECT_S, RESUME_MS, RUBBER, SNAP, beatAt, cardAt } from './motion';
import { card, colors, cta, fonts, row, splash, title } from './theme';

const NEVER = ReduceMotion.Never;
const PITCH = 225; // pt of drag per card
const LINE1 = 'Believe in';
const WORDS = ['Someone', 'Something'] as const;

type CardSlotProps = { data: CardData; index: number; clock: SharedValue<number>; scroll: SharedValue<number> };

function CardSlot({ data, index, clock, scroll }: CardSlotProps) {
  const style = useAnimatedStyle(() => {
    const p = index - 1 - scroll.get();
    const x = interpolate(p, [-1, 0, 1], row.x, Extrapolation.EXTEND);
    const y = interpolate(p, [-1, 0, 1], row.y, Extrapolation.CLAMP);
    const s = interpolate(p, [-1, 0, 1], row.scale, Extrapolation.CLAMP);
    const r = interpolate(p, [-1, 0, 1], row.rotate, Extrapolation.CLAMP);
    const c = cardAt(clock.get(), index);
    return {
      opacity: c.shown,
      zIndex: Math.round(10 - Math.abs(p) * 4),
      transform: [
        { translateX: x - card.width / 2 },
        { translateY: y + c.rise + row.shiftTitle * (1 - c.lift) },
        { rotate: `${r}deg` },
        { scale: s * c.scale },
      ],
    };
  });
  return (
    <Animated.View style={[styles.slot, style]} pointerEvents="none">
      <Card
        data={data}
        textShift={row.textShift[index]}
        iconShift={row.iconShift[index]}
        textDy={row.textDy[index]}
        inPerspective={index === 1}
      />
    </Animated.View>
  );
}

function PressButton({ label, primary, onPress }: { label: string; primary?: boolean; onPress?: () => void }) {
  const press = useSharedValue(0);
  const style = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS.scale) * press.get() }] }));
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !onPress }}
      disabled={!onPress}
      onPress={onPress}
      onPressIn={() => {
        press.set(withTiming(1, { duration: PRESS.ms }));
        if (primary && onPress) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
      }}
      onPressOut={() => press.set(withTiming(0, { duration: PRESS.ms }))}
    >
      <Animated.View style={[styles.button, { backgroundColor: primary ? colors.cta : colors.second }, style]}>
        <Text style={[styles.buttonText, { color: primary ? colors.ctaInk : colors.ink }]}>{label}</Text>
      </Animated.View>
    </Pressable>
  );
}

// A 1 pt speck one level off the page colour that flickers while autoplaying. The simulator's
// recorder stamps frames late after a fully still screen (the splash), which garbles the site
// video's timing; this keeps it streaming. Nobody can see it.
function RecorderPulse() {
  const v = useSharedValue(0);
  useEffect(() => {
    v.set(withRepeat(withTiming(1, { duration: 50, reduceMotion: NEVER }), -1, true));
    return () => cancelAnimation(v);
  }, [v]);
  const style = useAnimatedStyle(() => ({ opacity: v.get() }));
  return <Animated.View style={[styles.pulse, style]} pointerEvents="none" />;
}

// `freezeAt` (dev only): hold the loop still at that time, for pixel comparisons with the pin.
type Props = {
  autoPlay?: boolean;
  freezeAt?: number;
  onGetStarted?: () => void;
  onSignIn?: () => void;
};

export function BelieveCardCarousel({ autoPlay = false, freezeAt, onGetStarted, onSignIn }: Props) {
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [textLoaded] = useFonts({
    [fonts.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [fonts.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [fonts.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
    [fonts.extrabold]: require('./assets/fonts/Inter-ExtraBold.ttf'),
  });
  const font = useFont(require('./assets/fonts/InterDisplay-Bold.ttf'), title.size);
  const frozen = freezeAt !== undefined;

  // ---- one clock: every beat is a pure function of it (motion.ts) ----
  const clock = useSharedValue(frozen ? freezeAt : autoPlay ? 0 : HOLD_MS);
  const start = useSharedValue(-1);
  const running = useSharedValue(false);
  const holding = useSharedValue(false); // a finger is on: play on to the buttons, then hold
  const scroll = useSharedValue(0);
  const dragStart = useSharedValue(0);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const frameDriver = useFrameCallback((f) => {
    if (!running.get()) return;
    if (start.get() < 0) start.set(f.timestamp);
    let t = (f.timestamp - start.get()) % LOOP_MS;
    if (holding.get() && t >= HOLD_MS) {
      t = HOLD_MS;
      start.set(f.timestamp - HOLD_MS);
    }
    clock.set(t);
  }, false);
  useEffect(() => {
    if (resumeTimer.current) {
      clearTimeout(resumeTimer.current);
      resumeTimer.current = null;
    }
    if (frozen) {
      cancelAnimation(scroll);
      scroll.set(0);
      clock.set(freezeAt);
      holding.set(false);
      running.set(false);
      frameDriver.setActive(false);
      return;
    }
    if (reduced || !autoPlay || !font || !textLoaded) {
      cancelAnimation(scroll);
      if (reduced) scroll.set(Math.max(-1, Math.min(1, Math.round(scroll.get()))));
      clock.set(HOLD_MS);
      holding.set(false);
      running.set(false);
      frameDriver.setActive(false);
      return;
    }
    cancelAnimation(scroll);
    scroll.set(0);
    clock.set(0);
    start.set(-1);
    holding.set(false);
    running.set(true);
    frameDriver.setActive(true);
    return () => {
      running.set(false);
      frameDriver.setActive(false);
    };
  }, [autoPlay, clock, frameDriver, freezeAt, frozen, font, holding, reduced, running, scroll, start, textLoaded]);

  useEffect(
    () => () => {
      if (resumeTimer.current) clearTimeout(resumeTimer.current);
    },
    [],
  );
  const onTouchStart = useCallback(() => {
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    holding.set(autoPlay && !frozen && !reduced);
  }, [autoPlay, frozen, holding, reduced]);
  // 3.5 s after the last touch the loop starts over (the pin cuts straight to the splash)
  const onTouchEnd = useCallback(() => {
    if (!autoPlay || frozen || reduced) return;
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    resumeTimer.current = setTimeout(() => {
      if (!autoPlay || frozen || reduced) return;
      holding.set(false);
      start.set(-1);
      scroll.set(0);
      running.set(true);
      frameDriver.setActive(true);
    }, RESUME_MS);
  }, [autoPlay, frameDriver, frozen, holding, reduced, running, start, scroll]);

  // ---- the row scrolls sideways under the finger ----
  const pan = useMemo(
    () =>
      Gesture.Pan()
        .enabled(!frozen)
        .maxPointers(1)
        .activeOffsetX([-8, 8])
        .failOffsetY([-16, 16])
        .onBegin(() => {
          cancelAnimation(scroll);
          dragStart.set(scroll.get());
        })
        .onUpdate((e) => {
          let s = dragStart.get() - e.translationX / PITCH;
          if (s < -1) s = -1 + (s + 1) * RUBBER;
          if (s > 1) s = 1 + (s - 1) * RUBBER;
          scroll.set(s);
        })
        .onEnd((e) => {
          const v = -e.velocityX / PITCH;
          // where the row would coast to: a flick is enough
          const target = Math.max(-1, Math.min(1, Math.round(scroll.get() + v * PROJECT_S)));
          scroll.set(reduced ? target : withSpring(target, { ...SNAP, velocity: v }));
        }),
    [dragStart, frozen, reduced, scroll],
  );

  // ---- derived drawing values ----
  const wordBlur = useDerivedValue(() => MORPH.blur * Math.sin(Math.PI * beatAt(clock.get()).morph));
  const titleOpacity = useDerivedValue(() => {
    const b = beatAt(clock.get());
    return b.titleIn * b.titleOut;
  });
  const y1 = useDerivedValue(() => title.line1 + beatAt(clock.get()).line1Drop);
  const y2 = useDerivedValue(() => title.line2 + beatAt(clock.get()).line2Drop);
  const oldWord = useDerivedValue(() => 1 - beatAt(clock.get()).morph);
  const newWord = useDerivedValue(() => beatAt(clock.get()).morph);

  const shift = row.shiftTitle;
  const titleStyle = useAnimatedStyle(() => {
    const b = beatAt(clock.get());
    // the fading headline travels at half the row's speed (measured on the pin)
    return { transform: [{ translateY: -shift * row.titleTravel * b.lift }] };
  });
  const markStyle = useAnimatedStyle(() => ({ opacity: beatAt(clock.get()).mark }));
  const buttonsStyle = useAnimatedStyle(() => {
    const b = beatAt(clock.get());
    return { opacity: b.buttonsIn, transform: [{ translateY: b.buttonsY }] };
  });

  const x1 = font ? (width - font.getTextWidth(LINE1)) / 2 : 0;
  const xa = font ? (width - font.getTextWidth(WORDS[0])) / 2 : 0;
  const xb = font ? (width - font.getTextWidth(WORDS[1])) / 2 : 0;

  // text is laid out only once its faces are registered (iOS keeps a fallback otherwise)
  if (!textLoaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen} onTouchStart={frozen ? undefined : onTouchStart} onTouchEnd={onTouchEnd}>
      <StatusBar style="dark" />
      {autoPlay && !frozen && !reduced && <RecorderPulse />}

      <Animated.View style={[styles.splash, { left: width / 2 - splash.width / 2 }, markStyle]} pointerEvents="none">
        <BrandMark size={splash.width} color={colors.splashMark} />
      </Animated.View>

      <Animated.View
        style={[styles.title, titleStyle]}
        accessible
        accessibilityRole="header"
        accessibilityLabel="Believe in Something"
      >
        <Canvas style={{ width, height: title.height }}>
          {font && (
            <Group opacity={titleOpacity}>
              <SkText x={x1} y={y1} text={LINE1} font={font} color={colors.ink} />
              <Group opacity={oldWord}>
                <SkText x={xa} y={y2} text={WORDS[0]} font={font} color={colors.ink}>
                  <Blur blur={wordBlur} />
                </SkText>
              </Group>
              <Group opacity={newWord}>
                <SkText x={xb} y={y2} text={WORDS[1]} font={font} color={colors.ink}>
                  <Blur blur={wordBlur} />
                </SkText>
              </Group>
            </Group>
          )}
        </Canvas>
      </Animated.View>

      <GestureDetector gesture={pan}>
        <Animated.View
          style={styles.row}
          accessible
          accessibilityLabel="Projects you can believe in: Halo, Beehive, aro"
        >
          {CARDS.map((c, i) => (
            <CardSlot key={c.id} data={c} index={i} clock={clock} scroll={scroll} />
          ))}
        </Animated.View>
      </GestureDetector>

      <Animated.View style={[styles.cta, buttonsStyle]}>
        <PressButton label="Get Started" primary onPress={onGetStarted} />
        <PressButton label="Already have an account" onPress={onSignIn} />
        <Text style={styles.legal}>{'By continuing, you agree to accept our\nTerms of Use and Privacy Policy.'}</Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, overflow: 'hidden' },
  pulse: { position: 'absolute', right: 0, bottom: 0, width: 1, height: 1, backgroundColor: '#FBFBFB' },
  splash: { position: 'absolute', top: splash.centerY - splash.height / 2 },
  title: { position: 'absolute', left: 0, top: title.top },
  row: { position: 'absolute', left: 0, right: 0, top: row.topCta, height: card.height + 40 },
  slot: { position: 'absolute', left: 0, top: 0 },
  cta: { position: 'absolute', left: cta.side, right: cta.side, top: cta.top, gap: cta.gap },
  button: { height: cta.height, borderRadius: cta.height / 2, alignItems: 'center', justifyContent: 'center' },
  buttonText: { fontSize: 17.7, fontFamily: fonts.semibold, letterSpacing: -0.2 },
  legal: {
    marginTop: 12,
    textAlign: 'center',
    fontSize: 11.3,
    lineHeight: 13.3,
    fontFamily: fonts.regular,
    color: colors.legal,
  },
});
