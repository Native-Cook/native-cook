import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AccessibilityInfo, AppState, PixelRatio, Platform, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView, Pressable, ScrollView } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  SensorType,
  ReduceMotion,
  useAnimatedReaction,
  useAnimatedSensor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, Fill, Image as SkiaImage, ImageShader, Shader, Skia, useFonts, type SkImage } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { drawPageAtlas, GLYPH_CELL, glyphAtlas, type SolidMorphPage } from './smo-atlas';
import { arrivalHaptic, contactHaptic, tickHaptic } from './smo-haptics';
import {
  arrivalAt,
  baseAt,
  basePulseAt,
  bobAt,
  clamp,
  ease,
  hoverAt,
  LOOP,
  MOTION,
  pageMix,
  pressAt,
  progressAt,
  rateAt,
  rubber,
  smooth,
  spinRate,
  SPRING,
} from './smo-motion';
import { DEFAULT_PAGES } from './smo-pages';
import { SOLID_SHADER } from './smo-shaders';
import { comp, COPY, makeLayout, ref, sys, type SolidLayout, type SolidMorphTokens, type SolidSpec } from './smo-theme';

export type { SolidMorphPage, GlyphSource } from './smo-atlas';
export type { SolidMorphTokens, SolidSpec } from './smo-theme';
export { LOOP } from './smo-motion';
export type SolidMorphPagerProps = {
  pages?: SolidMorphPage[];
  index?: number;
  defaultIndex?: number;
  progress?: SharedValue<number>;
  onIndexChange?: (index: number) => void;
  onSkip?: () => void;
  onComplete?: () => void;
  actions?: { primary: { label: string; onPress: () => void }; secondary?: { label: string; onPress: () => void } };
  renderFooter?: (theme: SharedValue<number>) => ReactNode;
  hover?: boolean;
  spin?: number;
  conserveVolume?: boolean;
  tokens?: SolidMorphTokens;
  autoPlay?: boolean;
};
const FONT_SOURCES = {
  SmoRegular: [require('./assets/fonts/Outfit-Regular.ttf')],
  SmoSemibold: [require('./assets/fonts/Outfit-SemiBold.ttf')],
};
function useSmoReducedMotion() {
  const initial = useReducedMotion();
  const [reduced, setReduced] = useState(initial);
  useEffect(() => {
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => subscription.remove();
  }, []);
  return reduced;
}
const EFFECT = Skia.RuntimeEffect.Make(SOLID_SHADER);
const IS_IOS = Platform.OS === 'ios';
const DENSITY = PixelRatio.get();
const EMPTY_TOKENS: SolidMorphTokens = {};
function nowMs() {
  'worklet';
  return Date.now();
}
function rgba(hex: string) {
  const c = Skia.Color(hex);
  return [c[0], c[1], c[2], c[3]];
}
function reducedPage(p: SharedValue<number>, visibility: SharedValue<number>, to: number) {
  'worklet';
  cancelAnimation(p);
  cancelAnimation(visibility);
  visibility.set(
    withTiming(0, { duration: MOTION.returnFade / 2, reduceMotion: ReduceMotion.Never }, (finished) => {
      if (finished) {
        p.set(to);
        visibility.set(withTiming(1, { duration: MOTION.returnFade / 2, reduceMotion: ReduceMotion.Never }));
      }
    }),
  );
}
function baseNumber(s?: SolidSpec['base']) {
  return s === 'match' ? 2 : s === 'disc' ? 1 : 0;
}
type StageProps = {
  pages: SolidMorphPage[];
  layout: SolidLayout;
  atlas: SkImage;
  glyphs: SkImage;
  p: SharedValue<number>;
  rate: SharedValue<number>;
  clock: SharedValue<number>;
  cushion: SharedValue<number>;
  tiltX: SharedValue<number>;
  tiltY: SharedValue<number>;
  base: SharedValue<number>;
  lift: SharedValue<number>;
  tokens: SolidMorphTokens;
  hover: boolean;
  spin: number;
  conserveVolume: boolean;
  reduced: boolean;
  objectOnly?: boolean;
  playing: SharedValue<boolean>;
  buttonPress: SharedValue<number[]>;
  reaction: SharedValue<number[]>;
};
function SolidStage({
  pages,
  layout: l,
  atlas,
  glyphs,
  p,
  rate,
  clock,
  cushion,
  tiltX,
  tiltY,
  base,
  lift,
  tokens,
  hover,
  spin,
  conserveVolume,
  reduced,
  objectOnly = false,
  playing,
  buttonPress,
  reaction,
}: StageProps) {
  const specs = useMemo(
    () =>
      pages.map((x) => ({
        sides: x.solid.sides,
        round: x.solid.roundness ?? 0.15,
        split: x.solid.split === 'wedge' ? 1 : 0,
        base: baseNumber(x.solid.base),
        light: x.theme === 'light' ? 1 : 0,
      })),
    [pages],
  );
  const colors = useMemo(
    () => ({
      ink: rgba(tokens.ink ?? ref.color.ink),
      paper: rgba(tokens.paper ?? ref.color.paper),
      clay: rgba(tokens.clay ?? ref.color.clay),
      clayShade: rgba(tokens.clayShade ?? ref.color.clayShade),
      glaze: rgba(tokens.glaze ?? ref.color.glaze),
    }),
    [tokens],
  );
  const uniforms = useDerivedValue(() => {
    const state = pageMix(p.get(), specs.length);
    const a = specs[state.from];
    const b = specs[state.to];
    const t = clock.get();
    // 1.5 turns per interval, endpoints land at their exact pose; relief hides during the fast part.
    const edge = clamp((p.get() - clamp(p.get(), 0, specs.length - 1)) * 4, -1, 1);
    const turn = (state.from + ease(state.t)) * spin + (edge * MOTION.endSpin) / 360;
    const precess = reduced || !hover ? 0 : hoverAt(t);
    return {
      size: [l.W, l.H],
      centre: [l.heroX, l.heroY],
      radius: l.heroRadius,
      progress: p.get(),
      count: specs.length,
      sides: [a.sides, b.sides],
      rounds: [a.round, b.round],
      splits: [a.split, b.split],
      bases: [a.base, b.base],
      themes: [a.light, b.light],
      spin: reduced ? (state.from + Math.round(state.t)) * spin : turn,
      speed: reduced ? 0 : spinRate(p.get(), rate.get(), spin),
      hover: precess,
      bob: reduced ? 0 : (playing.get() ? bobAt(t) : 0) + lift.get(),
      baseTilt: reduced ? 0 : (playing.get() ? baseAt(t) : 0) + base.get(),
      press: reduced ? 0 : cushion.get(),
      lean: reduced ? [0, 0] : [((tiltX.get() + precess * 4 + edge * MOTION.endTilt) * Math.PI) / 180, (tiltY.get() * Math.PI) / 180],

      ...colors,
      render: [conserveVolume ? 1 : 0, IS_IOS ? 1 : 0, objectOnly ? 1 : 0, 1 / Math.max(1, l.heroRadius * DENSITY)],
      footerStack: l.largeText ? 1 : 0,
      buttonPress: buttonPress.get(),
      controls: [l.progressY, l.footerY, l.buttonHeight, l.S],
      copyBand: [l.chipY - 12 * l.S, l.bodyY + l.bodyHeight + 8 * l.S],
      arrival: playing.get() ? arrivalAt(t) : 0,
      reduced: reduced ? 1 : 0,
      reaction: [reaction.get()[0], playing.get() ? basePulseAt(t) : reaction.get()[1]],
    };
  });
  if (!EFFECT) return null;
  return (
    <Canvas style={StyleSheet.absoluteFill} accessible={false}>
      <Fill>
        <Shader source={EFFECT} uniforms={uniforms}>
          <ImageShader image={atlas} fit="fill" rect={{ x: 0, y: 0, width: l.W, height: l.H * pages.length }} />
          <ImageShader image={glyphs} fit="fill" rect={{ x: 0, y: 0, width: GLYPH_CELL, height: GLYPH_CELL * pages.length }} />
        </Shader>
      </Fill>
    </Canvas>
  );
}

export function SolidMorphPager({
  pages = DEFAULT_PAGES,
  index,
  defaultIndex = 0,
  progress,
  onIndexChange,
  onSkip,
  onComplete,
  actions,
  renderFooter,
  hover = true,
  spin = MOTION.spin,
  conserveVolume = true,
  tokens = EMPTY_TOKENS,
  autoPlay = false,
}: SolidMorphPagerProps) {
  const { width, height, fontScale } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useSmoReducedMotion() || !EFFECT || fontScale > comp.accessibility.largeTypeThreshold;
  const fontSources = useMemo(
    () => (tokens.font && tokens.fontAsset ? { ...FONT_SOURCES, [tokens.font]: [tokens.fontAsset] } : FONT_SOURCES),
    [tokens.font, tokens.fontAsset],
  );
  const fonts = useFonts(fontSources);
  const safePages = pages.length ? pages : DEFAULT_PAGES;
  const count = safePages.length;
  const end = count - 1;
  const [foreground, setForeground] = useState(AppState.currentState !== 'background' && AppState.currentState !== 'inactive');
  const initial = clamp(Math.round(index ?? defaultIndex), 0, end);
  const [selected, setSelected] = useState(initial);
  const [fields, setFields] = useState<SkImage | null>(null);
  const l = useMemo(
    () => makeLayout(width, height, insets.top, insets.bottom, fontScale),
    [width, height, insets.top, insets.bottom, fontScale],
  );
  const visibility = useSharedValue(1);
  const stageStyle = useAnimatedStyle(() => ({ opacity: visibility.get() }));
  const ownProgress = useSharedValue(initial);
  const p = progress ?? ownProgress;
  const playing = useSharedValue(autoPlay);
  const pinned = useSharedValue(-1);
  const clock = useSharedValue(0);
  const origin = useSharedValue(-1);
  const pauseAt = useSharedValue(-1);
  const touchedAt = useSharedValue(-1e9);
  const touch = useSharedValue(false);
  const startP = useSharedValue(initial);
  const target = useSharedValue(initial);
  const sampleTime = useSharedValue(0);
  const lastFrame = useSharedValue(0);
  const resetFrom = useSharedValue(-1);
  const resetClock = useSharedValue(0);
  const resetPhase = useSharedValue(0);
  const chainTarget = useSharedValue(initial);
  const horizontal = useSharedValue(false);
  const contactPulse = useSharedValue(0);
  const basePulse = useSharedValue(0);
  const reaction = useDerivedValue(() => [contactPulse.get(), basePulse.get()]);
  const primaryPress = useSharedValue(0);
  const secondaryPress = useSharedValue(0);
  const buttonPress = useDerivedValue(() => [primaryPress.get(), actions && !actions.secondary ? -1 : secondaryPress.get()]);
  const rate = useSharedValue(0);
  const cushion = useSharedValue(0);
  const tiltX = useSharedValue(0);
  const tiltY = useSharedValue(0);
  const base = useSharedValue(0);
  const lift = useSharedValue(0);
  const contact = useSharedValue(false);
  const limit = useSharedValue(false);
  const userActive = useSharedValue(false);
  const lastNotice = useSharedValue(-1e9);
  const notice = useSharedValue(initial);
  const hitObject = useSharedValue(false);
  const active = useSharedValue(true);
  const sensor = useAnimatedSensor(SensorType.ROTATION, { interval: 40 });
  const primaryLabel = actions?.primary.label;
  const secondaryLabel = actions ? (actions.secondary?.label ?? null) : COPY.secondary;
  const atlas = useMemo(
    () => (fonts ? drawPageAtlas(safePages, l, fonts, tokens, primaryLabel, secondaryLabel, !renderFooter) : null),
    [safePages, l, fonts, tokens, primaryLabel, secondaryLabel, renderFooter],
  );
  useEffect(() => {
    let mounted = true;
    void glyphAtlas(safePages)
      .then((v) => {
        if (mounted) setFields(v);
        else v?.dispose();
      })
      .catch(() => {
        if (mounted) setFields(null);
      });
    return () => {
      mounted = false;
    };
  }, [safePages]);
  useEffect(() => {
    playing.set(autoPlay && !reduced && index === undefined && !progress);
    cancelAnimation(visibility);
    visibility.set(1);
    if (reduced) {
      cancelAnimation(p);
      p.set(Math.round(clamp(p.get(), 0, end)));
      clock.set(0);
      rate.set(0);
      cushion.set(0);
      tiltX.set(0);
      tiltY.set(0);
    }
  }, [autoPlay, playing, reduced, index, progress, p, end, clock, rate, cushion, tiltX, tiltY, visibility]);
  useEffect(() => {
    if (index !== undefined) {
      target.set(clamp(index, 0, end));
      if (reduced) reducedPage(p, visibility, clamp(index, 0, end));
      else p.set(withSpring(clamp(index, 0, end), SPRING.lathe));
    }
  }, [index, end, p, target, reduced, visibility]);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => {
      if (s !== 'active' && playing.get()) pauseAt.set(lastFrame.get());
      active.set(s === 'active');
      setForeground(s === 'active');
    });
    return () => sub.remove();
  }, [active, playing, pauseAt, lastFrame]);
  const changed = useCallback(
    (value: number, user: boolean) => {
      setSelected(value);
      onIndexChange?.(value);
      if (user) {
        AccessibilityInfo.announceForAccessibility(`${safePages[value].title.replace('\n', ' ')}. Page ${value + 1} of ${count}`);
      }
    },
    [onIndexChange, safePages, count],
  );
  useAnimatedReaction(
    () => Math.round(clamp(p.get(), 0, end)),
    (v, prev) => {
      if (v === prev) return;
      cancelAnimation(notice);
      const delay = Math.max(0, 100 - (nowMs() - lastNotice.get()));
      notice.set(
        withDelay(
          delay,
          withTiming(v, { duration: 0, reduceMotion: ReduceMotion.Never }, (finished) => {
            if (finished) {
              lastNotice.set(nowMs());
              scheduleOnRN(changed, Math.round(clamp(p.get(), 0, end)), userActive.get() && !playing.get() && pinned.get() < 0);
            }
          }),
          ReduceMotion.Never,
        ),
      );
    },
  );
  useEffect(() => () => cancelAnimation(notice), [notice]);
  useAnimatedReaction(
    () => p.get(),
    (value, previous) => {
      if (playing.get()) return;
      if (previous !== null && userActive.get() && pinned.get() < 0) {
        const forwardReveal = value > previous && Math.floor(value + 0.1) > Math.floor(previous + 0.1);
        const reverseReveal = value < previous && Math.floor(value - 0.1) < Math.floor(previous - 0.1);
        if (forwardReveal || reverseReveal) scheduleOnRN(tickHaptic);
      }
      const time = nowMs();
      if (previous !== null && sampleTime.get() > 0) {
        const velocity = (value - previous) / Math.max(0.008, (time - sampleTime.get()) / 1000);
        rate.set(velocity);
        rate.set(withTiming(0, { duration: MOTION.velocityFade }));
      }
      sampleTime.set(time);
    },
  );
  const go = useCallback(
    (to: number) => {
      const velocity = rate.get();
      playing.set(false);
      pinned.set(-1);
      cancelAnimation(p);
      target.set(clamp(to, 0, end));
      touchedAt.set(nowMs());
      userActive.set(true);
      resetFrom.set(-1);
      sampleTime.set(nowMs());
      if (reduced) reducedPage(p, visibility, clamp(to, 0, end));
      else p.set(withSpring(clamp(to, 0, end), { ...SPRING.lathe, velocity }));
      cushion.set(withSpring(0, SPRING.cushion));
    },
    [p, playing, pinned, target, touchedAt, end, cushion, reduced, resetFrom, userActive, visibility, rate, sampleTime],
  );
  const ready = !!atlas && !!fields && !!EFFECT;
  const frameLoop = useFrameCallback((frame) => {
    if (!active.get() || !ready || reduced) return;
    lastFrame.set(frame.timestamp);
    const pin = pinned.get();
    if (pin >= 0) {
      clock.set(pin);
      p.set((progressAt(pin) * end) / 2);
      rate.set((rateAt(pin) * end) / 2);
      return;
    }
    const now = nowMs();
    if (origin.get() < 0) {
      origin.set(frame.timestamp);
      pauseAt.set(-1);
    }
    if (playing.get()) {
      if (pauseAt.get() >= 0) {
        origin.set(origin.get() + frame.timestamp - pauseAt.get());
        pauseAt.set(-1);
      }
      const time = ((frame.timestamp - origin.get()) / 1000) % LOOP;
      clock.set(time);
      p.set((progressAt(time) * end) / 2);
      rate.set((rateAt(time) * end) / 2);
    } else {
      if (hover || touch.get() || Math.abs(rate.get()) > 0.0001) {
        clock.set((clock.get() + (frame.timeSincePreviousFrame ?? 16.7) / 1000) % LOOP);
      }
      if (pauseAt.get() < 0) pauseAt.set(frame.timestamp);
      if (!touch.get() && autoPlay && index === undefined && !progress && now - touchedAt.get() > MOTION.resumeAfter * 1000) {
        const reset = smooth(MOTION.resumeAfter * 1000, MOTION.touchResume, now - touchedAt.get());
        if (reset < 1) {
          if (resetFrom.get() < 0) {
            userActive.set(false);
            cancelAnimation(p);
            resetFrom.set(p.get());
            resetClock.set(clock.get());
            resetPhase.set(Math.round(clock.get() / (LOOP / 4)) * (LOOP / 4));
            tiltX.set(withTiming(0, { duration: MOTION.resetDuration * 1000 }));
            tiltY.set(withTiming(0, { duration: MOTION.resetDuration * 1000 }));
          }
          const blend = ease(reset);
          p.set(resetFrom.get() * (1 - blend));
          clock.set(resetClock.get() + (resetPhase.get() - resetClock.get()) * blend);
        } else {
          p.set(0);
          cancelAnimation(tiltX);
          cancelAnimation(tiltY);
          tiltX.set(0);
          tiltY.set(0);
          lift.set(0);
          base.set(0);
          cushion.set(0);
          resetFrom.set(-1);
          playing.set(true);
          origin.set(frame.timestamp);
          pauseAt.set(-1);
          clock.set(0);
        }
      }
    }
  });
  useEffect(() => {
    frameLoop.setActive(ready && foreground && !reduced && (autoPlay || hover));
    return () => frameLoop.setActive(false);
  }, [frameLoop, ready, foreground, reduced, autoPlay, hover]);
  useEffect(() => {
    if (!__DEV__) return;
    const api = {
      pin: (t: number) => {
        playing.set(true);
        userActive.set(false);
        cancelAnimation(p);
        pinned.set(t);
        clock.set(t);
        p.set((progressAt(t) * end) / 2);
        rate.set((rateAt(t) * end) / 2);
      },
      at: (t: number) => {
        clock.set(t);
        p.set((progressAt(t) * end) / 2);
        rate.set((rateAt(t) * end) / 2);
        playing.set(true);
        userActive.set(false);
        cancelAnimation(p);
        pinned.set(t);
      },
      play: () => {
        frameLoop.setActive(!reduced);
        pinned.set(-1);
        origin.set(-1);
        pauseAt.set(-1);
        playing.set(true);
      },
      progress: (v: number) => {
        userActive.set(false);
        pinned.set(-1);
        playing.set(false);
        touchedAt.set(1e20);
        clock.set(0);
        cancelAnimation(p);
        p.set(clamp(v, 0, end));
        rate.set(0);
      },
      state: () => ({ progress: p.get(), t: clock.get(), playing: playing.get() }),
    };
    (globalThis as Record<string, unknown>).__smo = api;
    return () => {
      if ((globalThis as Record<string, unknown>).__smo === api) delete (globalThis as Record<string, unknown>).__smo;
    };
  }, [p, pinned, playing, clock, origin, pauseAt, touchedAt, end, rate, userActive, frameLoop, reduced]);
  const pan = useMemo(() => {
    const gesture = Gesture.Pan()
      .minDistance(0)
      .maxPointers(1)
      .hitSlop({ left: -MOTION.edgeGuard })
      .onBegin((e) => {
        const wasPlaying = playing.get();
        if (wasPlaying) {
          lift.set(lift.get() + bobAt(clock.get()));
          base.set(base.get() + baseAt(clock.get()));
        }
        playing.set(false);
        rate.set(withTiming(0, { duration: MOTION.velocityFade }));
        sampleTime.set(nowMs());
        pinned.set(-1);
        touch.set(true);
        touchedAt.set(nowMs());
        cancelAnimation(p);
        cancelAnimation(cushion);
        cancelAnimation(lift);
        cancelAnimation(base);
        cancelAnimation(tiltX);
        cancelAnimation(tiltY);
        startP.set(p.get());
        chainTarget.set(wasPlaying || Math.abs(target.get() - p.get()) > 0.8 ? Math.round(p.get()) : target.get());
        horizontal.set(false);
        resetFrom.set(-1);
        contact.set(false);
        limit.set(false);
        userActive.set(true);
        const isHero = Math.abs(e.x - l.heroX) < l.heroRadius * 1.3 && Math.abs(e.y - l.heroY) < l.heroRadius;
        hitObject.set(isHero);
        if (isHero && !reduced) cushion.set(withSpring(0.23, SPRING.glide));
      })
      .onUpdate((e) => {
        touchedAt.set(nowMs());
        if (reduced) return;
        if (Math.abs(e.translationX) >= MOTION.pressSlop || horizontal.get() || !hitObject.get()) {
          if (!horizontal.get()) cushion.set(withSpring(0, SPRING.glide));
          horizontal.set(true);
          const tracked = e.translationX - (hitObject.get() ? Math.sign(e.translationX) * MOTION.pressSlop : 0);
          const raw = startP.get() - tracked / MOTION.drag / l.S;
          p.set(rubber(raw, end));
          if ((raw < -0.04 || raw > end + 0.04) && !limit.get()) {
            limit.set(true);
            scheduleOnRN(contactHaptic);
          }
        } else {
          const down = Math.max(0, e.translationY);
          const compress = pressAt(down / l.S);
          cushion.set(Math.max(0.23, compress));
          if (compress >= 0.99 && !contact.get()) {
            contact.set(true);
            contactPulse.set(withSequence(withTiming(1, { duration: 1 }), withTiming(0, { duration: 120 })));
            scheduleOnRN(contactHaptic);
          }
        }
        if (hitObject.get()) {
          tiltX.set(clamp((e.x - l.heroX) / l.heroRadius, -1, 1) * MOTION.tiltLimit);
          tiltY.set(clamp((e.y - l.heroY) / l.heroRadius, -1, 1) * MOTION.tiltLimit);
        }
      })
      .onEnd((e) => {
        const velocity = -e.velocityX / MOTION.drag / l.S;
        const value = reduced ? startP.get() - e.translationX / MOTION.drag / l.S : p.get();
        const fast = Math.abs(e.velocityX) > 400 && (horizontal.get() || reduced);
        const to = clamp(
          fast ? Math.round(chainTarget.get()) + Math.sign(velocity) : Math.round(value + velocity * MOTION.projection),
          0,
          end,
        );
        target.set(to);
        if (reduced) reducedPage(p, visibility, to);
        else
          p.set(
            withSpring(to, { ...SPRING.lathe, velocity }, (finished) => {
              if (finished && to !== Math.round(startP.get())) {
                lift.set(withSequence(withTiming(4, { duration: 70 }), withSpring(0, SPRING.cushion)));
                base.set(withDelay(300, withSequence(withTiming(-1.5, { duration: 50 }), withSpring(0, SPRING.base))));
                basePulse.set(withDelay(300, withSequence(withTiming(1, { duration: 1 }), withTiming(0, { duration: 200 }))));
                scheduleOnRN(arrivalHaptic);
              }
            }),
          );
        if (!reduced) {
          cushion.set(withSpring(0, SPRING.cushion));
          lift.set(withSpring(0, SPRING.cushion));
          base.set(withSpring(0, SPRING.base));
          tiltX.set(withSpring(0, { ...SPRING.precession, velocity: e.velocityX * MOTION.precessionVelocity }));
          tiltY.set(withSpring(0, { ...SPRING.precession, velocity: e.velocityY * MOTION.precessionVelocity }));
        }
      })
      .onFinalize((_e, success) => {
        touch.set(false);
        touchedAt.set(nowMs());
        if (!reduced && !success) {
          lift.set(withSpring(0, SPRING.cushion));
          base.set(withSpring(0, SPRING.base));
          cushion.set(withSpring(0, SPRING.cushion));
          tiltX.set(withSpring(0, SPRING.cushion));
          tiltY.set(withSpring(0, SPRING.cushion));
        }
      });
    if (l.largeText) gesture.activeOffsetX([-MOTION.pressSlop, MOTION.pressSlop]).failOffsetY([-12, 12]);
    return gesture;
  }, [
    playing,
    pinned,
    touch,
    touchedAt,
    p,
    cushion,
    startP,
    target,
    contact,
    clock,
    l,
    hitObject,
    reduced,
    end,
    tiltX,
    tiltY,
    lift,
    base,
    horizontal,
    chainTarget,
    resetFrom,
    contactPulse,
    basePulse,
    userActive,
    limit,
    visibility,
    rate,
    sampleTime,
  ]);
  const tap = useMemo(
    () =>
      Gesture.Tap()
        .maxDistance(8)
        .hitSlop({ left: -MOTION.edgeGuard })
        .onEnd((e, success) => {
          if (success && !hitObject.get()) scheduleOnRN(go, Math.round(p.get()) + (e.x > width / 2 ? 1 : -1));
        }),
    [go, p, width, hitObject],
  );
  const gesture = useMemo(() => Gesture.Simultaneous(pan, tap), [pan, tap]);
  // Sensor is optional; the drag is the full simulator stand-in. No expo-sensors dependency.
  const sensorTilt = useDerivedValue(() =>
    reduced || playing.get() || touch.get() || !sensor.isAvailable ? 0 : clamp(sensor.sensor.get().roll * 4, -2, 2),
  );
  const mergedTilt = useDerivedValue(() => tiltX.get() + sensorTilt.get());
  const pageThemes = useMemo(() => safePages.map((page) => (page.theme === 'light' ? 1 : 0)), [safePages]);
  const theme = useDerivedValue(() => {
    const m = pageMix(p.get(), count);
    return pageThemes[m.from] * (1 - m.t) + pageThemes[m.to] * m.t;
  });
  const onPrimary = () => {
    touchedAt.set(nowMs());
    if (actions?.primary) actions.primary.onPress();
    else if (onComplete) onComplete();
    else go(Math.min(end, selected + 1));
  };
  const onSecondary = () => {
    touchedAt.set(nowMs());
    if (actions?.secondary) actions.secondary.onPress();
    else go(0);
  };
  const w =
    secondaryLabel === null || l.largeText ? width - 2 * comp.margin * l.S : (width - 2 * comp.margin * l.S - comp.button.gap * l.S) / 2;
  const current = safePages[selected] ?? safePages[0];
  return (
    <GestureHandlerRootView style={[styles.root, { backgroundColor: tokens.ink ?? sys.surface.page }]}>
      <StatusBar style={current.theme === 'dark' ? 'light' : 'dark'} />
      <ScrollView scrollEnabled={l.H > height} bounces={false} contentContainerStyle={{ height: l.H }}>
        {ready && (
          <Animated.View style={[StyleSheet.absoluteFill, stageStyle]}>
            <SolidStage
              pages={safePages}
              layout={l}
              atlas={atlas}
              glyphs={fields}
              p={p}
              rate={rate}
              clock={clock}
              cushion={cushion}
              tiltX={mergedTilt}
              tiltY={tiltY}
              base={base}
              lift={lift}
              tokens={tokens}
              hover={hover}
              spin={spin}
              conserveVolume={conserveVolume}
              reduced={reduced}
              playing={playing}
              buttonPress={buttonPress}
              reaction={reaction}
            />
          </Animated.View>
        )}
        {!EFFECT && atlas && (
          <Animated.View style={[StyleSheet.absoluteFill, stageStyle]}>
            <Canvas style={StyleSheet.absoluteFill} accessible={false}>
              <Fill color={current.theme === 'dark' ? (tokens.ink ?? sys.surface.page) : (tokens.paper ?? sys.surface.sheet)} />
              <SkiaImage image={atlas} x={0} y={-selected * l.H} width={l.W} height={l.H * count} fit="fill" />
            </Canvas>
          </Animated.View>
        )}
        <GestureDetector gesture={gesture}>
          <View
            style={[StyleSheet.absoluteFill, { bottom: l.H - l.footerY + 12 }]}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel={`${current.title.replace('\n', ' ')}. ${current.body ?? ''}`}
            accessibilityValue={{ min: 1, max: count, now: selected + 1, text: `Page ${selected + 1} of ${count}` }}
            accessibilityActions={[
              { name: 'increment', label: 'Next page' },
              { name: 'decrement', label: 'Previous page' },
            ]}
            onAccessibilityAction={(e) => go(selected + (e.nativeEvent.actionName === 'increment' ? 1 : -1))}
          />
        </GestureDetector>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Skip introduction"
          onPress={() => {
            go(end);
            onSkip?.();
          }}
          style={{
            position: 'absolute',
            top: l.progressY - 18 * l.S,
            right: 8 * l.S,
            width: l.skipWidth,
            height: Math.max(48, 32 * l.S * fontScale),
          }}
        />
        {renderFooter ? (
          <Animated.View style={[{ position: 'absolute', top: l.footerY, left: 0, right: 0 }, stageStyle]}>
            {renderFooter(theme)}
          </Animated.View>
        ) : (
          <>
            <Animated.View
              style={[
                styles.buttonOverlay,
                { left: comp.margin * l.S, top: l.footerY, width: w, height: l.buttonHeight, borderRadius: comp.button.r * l.S },
              ]}
            >
              <Pressable
                style={styles.root}
                accessibilityRole="button"
                accessibilityLabel={actions?.primary.label ?? COPY.primary}
                onPressIn={() => {
                  playing.set(false);
                  rate.set(withTiming(0, { duration: MOTION.velocityFade }));
                  sampleTime.set(nowMs());
                  touchedAt.set(nowMs());
                  primaryPress.set(withSpring(1, SPRING.glide));
                }}
                onPressOut={() => primaryPress.set(withSpring(0, SPRING.glide))}
                onPress={onPrimary}
              />
            </Animated.View>
            {secondaryLabel !== null && (
              <Animated.View
                style={[
                  styles.buttonOverlay,
                  {
                    right: comp.margin * l.S,
                    top: l.largeText ? l.footerY + l.buttonHeight + comp.button.gap * l.S : l.footerY,
                    width: w,
                    height: l.buttonHeight,
                    borderRadius: comp.button.r * l.S,
                  },
                ]}
              >
                <Pressable
                  style={styles.root}
                  accessibilityRole="button"
                  accessibilityLabel={actions?.secondary?.label ?? COPY.secondary}
                  onPressIn={() => {
                    playing.set(false);
                    rate.set(withTiming(0, { duration: MOTION.velocityFade }));
                    sampleTime.set(nowMs());
                    touchedAt.set(nowMs());
                    secondaryPress.set(withSpring(1, SPRING.glide));
                  }}
                  onPressOut={() => secondaryPress.set(withSpring(0, SPRING.glide))}
                  onPress={onSecondary}
                />
              </Animated.View>
            )}
          </>
        )}
      </ScrollView>
    </GestureHandlerRootView>
  );
}

export type SolidMorphProps = { from: SolidSpec; to: SolidSpec; t: SharedValue<number>; size?: number; spin?: number };
/** The live object alone; no WebGPU context and no network required. */
export function SolidMorph({ from, to, t, size = 320, spin = MOTION.spin }: SolidMorphProps) {
  const pages = useMemo(
    () => [
      { ...DEFAULT_PAGES[0], solid: from },
      { ...DEFAULT_PAGES[2], solid: to },
    ],
    [from, to],
  );
  const fonts = useFonts(FONT_SOURCES);
  const [glyphs, setGlyphs] = useState<SkImage | null>(null);
  const layout = useMemo(() => ({ ...makeLayout(size, size, 0, 0), heroX: size / 2, heroY: size / 2, heroRadius: size / 2.7 }), [size]);
  const atlas = useMemo(() => (fonts ? drawPageAtlas(pages, layout, fonts, {}) : null), [fonts, pages, layout]);
  useEffect(() => {
    let active = true;
    void glyphAtlas(pages).then((v) => {
      if (active) setGlyphs(v);
    });
    return () => {
      active = false;
    };
  }, [pages]);
  const zero = useSharedValue(0);
  const stopped = useSharedValue(false);
  const presses = useSharedValue([0, 0]);
  const reduced = useSmoReducedMotion();
  const displayed = useDerivedValue(() => (reduced ? Math.round(t.get()) : t.get()));
  return (
    <View style={{ width: size, height: size }}>
      {atlas && glyphs && (
        <SolidStage
          pages={pages}
          layout={layout}
          atlas={atlas}
          glyphs={glyphs}
          p={displayed}
          rate={zero}
          clock={zero}
          cushion={zero}
          tiltX={zero}
          tiltY={zero}
          base={zero}
          lift={zero}
          tokens={EMPTY_TOKENS}
          hover={false}
          spin={spin}
          conserveVolume
          reduced={reduced}
          playing={stopped}
          buttonPress={presses}
          reaction={presses}
          objectOnly
        />
      )}
    </View>
  );
}
const styles = StyleSheet.create({ root: { flex: 1 }, buttonOverlay: { position: 'absolute', overflow: 'hidden' } });
