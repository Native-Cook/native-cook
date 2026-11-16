import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, AppState, PixelRatio, Platform, ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  SensorType,
  useAnimatedReaction,
  useAnimatedSensor,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  ReduceMotion,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';
import { Blur, Canvas, Fill, Group, ImageShader, Paint, Shader, Skia } from '@shopify/react-native-skia';
import Svg, { Path } from 'react-native-svg';
import { createAtlas } from './ers-atlas';
import { cue } from './ers-haptics';
import {
  clamp,
  copyShift,
  digitArrival,
  GESTURE,
  highlightPass,
  landingAt,
  LOOP,
  pageAlpha,
  peekShift,
  smooth,
  SPRING,
  storyAt,
  touchTime,
} from './ers-motion';
import { EXTRUSION_SKSL } from './ers-shaders';
import { comp, COPY, makeLayout, ref, REF, rgb, sys } from './ers-theme';
import type { ExtrusionPage, ExtrusionStoryProps } from './ers-types';

function MotionAim({ value, held }: { value: SharedValue<{ x: number; y: number }>; held: SharedValue<boolean> }) {
  const device = useAnimatedSensor(SensorType.ROTATION, { interval: 32 });
  const rotation = device.sensor;
  const baseline = useSharedValue<{ roll: number; pitch: number } | null>(null);
  useAnimatedReaction(
    () => ({ roll: rotation.get().roll, pitch: rotation.get().pitch }),
    (v, previous) => {
      if (!previous || !held.get()) return;
      const b = baseline.get() ?? v;
      if (baseline.get() === null) baseline.set(b);
      value.set({ x: clamp(v.roll - b.roll, -1, 1) * GESTURE.steerAngle, y: clamp(v.pitch - b.pitch, -1, 1) * GESTURE.steerAngle });
    },
  );
  useEffect(
    () => () => {
      value.set({ x: 0, y: 0 });
    },
    [value],
  );
  return null;
}

function RollingDigit({
  digit,
  order,
  page,
  count,
  progress,
  size,
  family,
  color,
  reduced,
}: {
  digit: string;
  order: number;
  page: number;
  count: number;
  progress: SharedValue<number>;
  size: number;
  family: string;
  color: string;
  reduced: boolean;
}) {
  const [measured, setMeasured] = useState<number | undefined>();
  const { fontScale } = useWindowDimensions();
  const row = size * 1.06 * fontScale;
  const motion = useAnimatedStyle(() => ({
    transform: [{ translateY: reduced ? -row : -row * digitArrival(progress.get(), page, count, order) }],
  }));
  return (
    <View style={{ height: row, width: measured, overflow: 'hidden' }}>
      <Text
        onLayout={(e) => setMeasured(e.nativeEvent.layout.width)}
        style={{ position: 'absolute', opacity: 0, fontFamily: family, fontSize: size, fontVariant: ['tabular-nums'] }}
      >
        {digit}
      </Text>
      <Animated.View style={motion}>
        <Text
          style={{ fontFamily: family, fontSize: size, lineHeight: size * 1.06, color, fontVariant: ['tabular-nums'], textAlign: 'center' }}
        >
          {(Number(digit) + 9) % 10}
        </Text>
        <Text
          style={{ fontFamily: family, fontSize: size, lineHeight: size * 1.06, color, fontVariant: ['tabular-nums'], textAlign: 'center' }}
        >
          {digit}
        </Text>
      </Animated.View>
    </View>
  );
}

function PageCopy({
  page,
  pageIndex,
  count,
  progress,
  age,
  width,
  y,
  scale,
  largeType,
  reduced,
  isActive,
  tokens,
}: {
  page: ExtrusionPage;
  pageIndex: number;
  count: number;
  progress: SharedValue<number>;
  age: SharedValue<number>;
  width: number;
  y: number;
  scale: number;
  largeType: boolean;
  reduced: boolean;
  isActive: boolean;
  tokens: ExtrusionStoryProps['tokens'];
}) {
  const active = useDerivedValue(() => {
    if (!reduced) return pageAlpha(progress.get(), pageIndex, count);
    const p = ((progress.get() % count) + count) % count,
      i = Math.floor(p),
      q = p - i;
    return pageIndex === i ? 1 - q : pageIndex === (i + 1) % count ? q : 0;
  });
  const animated = useAnimatedStyle(() => {
    const a = active.get(),
      t = age.get();
    // The leaving copy is gone before the next page's colour arrives: a faint headline left over a dark page read as a ghost.
    const o = reduced ? a : Math.min(1, Math.max(0, (a - 0.2) / 0.8));
    return { opacity: o * o * (3 - 2 * o), transform: [{ translateY: reduced ? 0 : copyShift(a, t, !!page.stat) * scale }] };
  });
  const color = useAnimatedStyle(() => {
    const t = age.get(),
      pass = reduced ? 0 : highlightPass(t);
    const base = rgb(tokens?.headline ?? sys.ink.primary),
      light = rgb(ref.color.lightInk);
    return {
      color: `rgb(${Math.round((base[0] + (light[0] - base[0]) * pass) * 255)},${Math.round((base[1] + (light[1] - base[1]) * pass) * 255)},${Math.round((base[2] + (light[2] - base[2]) * pass) * 255)})`,
    };
  });
  return (
    <Animated.View
      pointerEvents={isActive ? 'box-none' : 'none'}
      accessibilityElementsHidden={!isActive}
      importantForAccessibility={isActive ? 'auto' : 'no-hide-descendants'}
      style={[{ position: 'absolute', left: ref.space.edge * scale, top: y, width: width - 2 * ref.space.edge * scale }, animated]}
    >
      <ScrollView
        pointerEvents={largeType ? 'auto' : 'none'}
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        scrollEnabled={largeType}
        style={largeType ? { maxHeight: 340 * scale } : undefined}
      >
        {page.stat ? (
          <View style={{ flexDirection: 'row', justifyContent: 'center' }}>
            {String(page.stat.value)
              .split('')
              .map((digit, order) => (
                <RollingDigit
                  key={order}
                  digit={digit}
                  order={order}
                  page={pageIndex}
                  count={count}
                  progress={progress}
                  size={78 * scale}
                  family={tokens?.displayFont ?? ref.type.display}
                  color={tokens?.headline ?? sys.ink.primary}
                  reduced={reduced}
                />
              ))}
          </View>
        ) : null}
        <Animated.Text
          style={[
            {
              fontFamily: tokens?.displayFont ?? ref.type.display,
              fontSize: (page.stat ? 32 : ref.type.size) * scale,
              lineHeight: (page.stat ? 34 : ref.type.line) * scale,
              textAlign: 'center',
              letterSpacing: ref.type.tracking * scale,
            },
            color,
          ]}
        >
          {page.headline}
        </Animated.Text>
        {page.caption ? (
          <Text
            style={{
              fontFamily: tokens?.captionFont ?? ref.type.body,
              fontSize: ref.type.caption * scale,
              lineHeight: 19 * scale,
              textAlign: 'center',
              marginTop: 22 * scale,
              color: sys.ink.secondary,
            }}
          >
            {page.caption}
          </Text>
        ) : null}
        {page.stat?.label ? (
          <Text style={{ fontFamily: tokens?.captionFont ?? ref.type.body, color: sys.ink.secondary, textAlign: 'center' }}>
            {page.stat.label}
          </Text>
        ) : null}
      </ScrollView>
      {page.render?.(active)}
    </Animated.View>
  );
}

function peekTitle(page: ExtrusionPage | undefined) {
  if (!page) return '';
  const headline = page.headline.replace(/\n/g, ' ');
  return page.stat ? `${page.stat.value} ${headline}` : headline;
}

/** The next story's card peeks under this one: its name rides on it and cross-morphs with the page. */
function PeekLabel({
  pageIndex,
  count,
  progress,
  age,
  reduced,
  height,
  scale,
  prefix,
  title,
  tokens,
}: {
  pageIndex: number;
  count: number;
  progress: SharedValue<number>;
  age: SharedValue<number>;
  reduced: boolean;
  height: number;
  scale: number;
  prefix: string;
  title: string;
  tokens: ExtrusionStoryProps['tokens'];
}) {
  const motion = useAnimatedStyle(() => {
    const a = pageAlpha(progress.get(), pageIndex, count);
    return {
      opacity: a,
      transform: [{ translateY: reduced ? 0 : peekShift(progress.get(), pageIndex, count, age.get()) * scale }],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.peekRow, { height, paddingHorizontal: ref.space.edge * scale }, motion]}>
      <Text
        numberOfLines={1}
        allowFontScaling={false}
        style={{
          fontFamily: tokens?.captionFont ?? ref.type.body,
          fontSize: 13 * scale,
          color: ref.color.peekMute,
          marginRight: 8 * scale,
        }}
      >
        {prefix}
      </Text>
      <Text
        numberOfLines={1}
        allowFontScaling={false}
        style={{
          flex: 1,
          fontFamily: tokens?.displayFont ?? ref.type.display,
          fontSize: 15 * scale,
          letterSpacing: -0.2 * scale,
          color: ref.color.peekInk,
        }}
      >
        {title}
      </Text>
      <Svg width={16 * scale} height={16 * scale} viewBox="0 0 16 16">
        <Path
          d="M6 3.5L10.5 8L6 12.5"
          stroke={ref.color.peekInk}
          strokeWidth={1.8}
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />
      </Svg>
    </Animated.View>
  );
}

/** Data-driven swept SDF stories; all GPU work is Skia, with no WebGPU dependency. */
export function ExtrusionStory({
  pages,
  index,
  defaultIndex = 0,
  progress: externalProgress,
  onIndexChange,
  onComplete,
  autoAdvanceMs,
  loft = true,
  steer = true,
  header,
  tokens,
  autoPlay = false,
}: ExtrusionStoryProps) {
  const { width, height, fontScale } = useWindowDimensions(),
    insets = useSafeAreaInsets();
  const initialReduce = useReducedMotion();
  const [reduce, setReduce] = useState(initialReduce);
  const [screenReader, setScreenReader] = useState(false),
    [paused, setPaused] = useState(false),
    [foreground, setForeground] = useState(true);
  const [activePage, setActivePage] = useState(clamp(index ?? defaultIndex, 0, Math.max(0, pages.length - 1)));
  const [fontsLoaded, fontError] = useFonts({
    [ref.type.display]: require('./assets/fonts/Onest-ExtraBold.ttf'),
    [ref.type.body]: require('./assets/fonts/Onest-Regular.ttf'),
  });
  const count = pages.length;
  const ownProgress = useSharedValue(clamp(index ?? defaultIndex, 0, Math.max(0, count - 1))),
    progress = externalProgress ?? ownProgress;
  const clock = useSharedValue(0),
    elapsed = useSharedValue(0),
    age = useSharedValue(10),
    mode = useSharedValue(0);
  const held = useSharedValue(false),
    downAt = useSharedValue(0),
    lastTouch = useSharedValue(-1e9);
  const origin = useSharedValue(0),
    intent = useSharedValue(false),
    pin = useSharedValue(-1);
  const steerX = useSharedValue(0),
    steerY = useSharedValue(0),
    speed = useSharedValue(0);
  const limit = useSharedValue(0),
    landing = useSharedValue(-1e9),
    wallCue = useSharedValue(false),
    landedPage = useSharedValue(-1);
  const [interacting, setInteracting] = useState(false);
  const layout = makeLayout(width, height, insets.top, insets.bottom);
  const atlas = useMemo(() => (count ? createAtlas(pages) : null), [count, pages]);
  const effect = useMemo(() => Skia.RuntimeEffect.Make(EXTRUSION_SKSL), []);
  const palette = useMemo(
    () => pages.map((p) => ({ a: rgb(p.field[0]), b: rgb(p.field[1]), face: rgb(p.face), dir: p.direction ?? [0.43, 0.9] })),
    [pages],
  );
  const canDraw = (fontsLoaded || !!fontError) && !!atlas && !!effect;
  const nativeP3 = Platform.OS === 'ios' ? 1 : 0,
    pixelRatio = PixelRatio.get();
  const tiltOffset = useSharedValue({ x: 0, y: 0 });
  const animatedEnabled = !reduce && !screenReader && foreground && !paused;
  const playEnabled =
    count > 0 && canDraw && (autoPlay || autoAdvanceMs != null) && index === undefined && !externalProgress && animatedEnabled;

  useEffect(() => {
    void AccessibilityInfo.isScreenReaderEnabled().then(setScreenReader);
    const a = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    const b = AppState.addEventListener('change', (state) => setForeground(state === 'active'));
    const c = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduce);
    return () => {
      a.remove();
      b.remove();
      c.remove();
    };
  }, []);
  useEffect(() => {
    if (index !== undefined) {
      mode.set(1);
      progress.set(
        reduce
          ? withTiming(clamp(index, 0, count - 1), { duration: 250, reduceMotion: ReduceMotion.Never })
          : withSpring(clamp(index, 0, count - 1), SPRING.page),
      );
    }
  }, [index, count, mode, progress, reduce]);
  useEffect(() => {
    if (reduce) {
      cancelAnimation(progress);
      progress.set(Math.round(progress.get()));
      age.set(10);
    }
  }, [reduce, progress, age]);
  const emitIndex = useCallback(
    (i: number) => {
      setActivePage(i);
      onIndexChange?.(i);
    },
    [onIndexChange],
  );
  const landed = useCallback(() => cue('landed'), []);
  useAnimatedReaction(
    () => Math.round(((progress.get() % Math.max(1, count)) + Math.max(1, count)) % Math.max(1, count)) % Math.max(1, count),
    (i, previous) => {
      if (i !== previous && count) scheduleOnRN(emitIndex, i);
    },
    [count],
  );
  useAnimatedReaction(
    () => Math.abs(progress.get() - Math.round(progress.get())) < 0.002 && !held.get(),
    (isLanded, wasLanded) => {
      if (isLanded && wasLanded === false && mode.get() === 1 && landedPage.get() !== Math.round(progress.get())) {
        landedPage.set(Math.round(progress.get()));
        landing.set(clock.get());
        scheduleOnRN(landed);
      }
    },
  );
  const frame = useFrameCallback((f) => {
    const dt = Math.min(f.timeSincePreviousFrame ?? 16.67, 50);
    clock.set(clock.get() + dt);
    if (pin.get() >= 0) {
      progress.set(storyAt(pin.get(), count));
      elapsed.set(pin.get());
      age.set(landingAt(pin.get()));
      return;
    }
    if (!animatedEnabled) return;
    if (mode.get() === 0 && playEnabled) {
      elapsed.set(elapsed.get() + dt / 1000);
      if (autoAdvanceMs !== undefined && autoAdvanceMs !== null) {
        const period = Math.max(1200, autoAdvanceMs),
          t = (elapsed.get() * 1000) / period;
        progress.set(Math.min(count - 1, Math.floor(t) + smooth(0.55, 1, t % 1)));
        age.set(((t % 1) * period) / 1000);
        if (t >= count) {
          mode.set(4);
          scheduleOnRN(setPaused, true);
          if (onComplete) scheduleOnRN(onComplete);
        }
      } else if (autoAdvanceMs !== null) {
        progress.set(storyAt(elapsed.get(), count));
        age.set(landingAt(elapsed.get()));
      }
    } else {
      age.set((clock.get() - landing.get()) / 1000);
      if (!held.get() && !playEnabled && clock.get() - lastTouch.get() > GESTURE.resume && mode.get() === 1)
        scheduleOnRN(setInteracting, false);
      if (!held.get() && clock.get() - lastTouch.get() > GESTURE.resume && playEnabled && mode.get() === 1) {
        mode.set(3);
        progress.set(
          withSpring(Math.ceil(progress.get() / count) * count, SPRING.glide, (finished) => {
            if (finished) {
              mode.set(0);
              elapsed.set(0);
              progress.set(0);
              age.set(10);
              scheduleOnRN(setInteracting, false);
            }
          }),
        );
      }
    }
  }, false);
  useEffect(() => {
    frame.setActive(foreground && !reduce && !paused && !screenReader && (playEnabled || interacting));
    return () => frame.setActive(false);
  }, [frame, foreground, reduce, paused, screenReader, playEnabled, interacting]);
  useEffect(() => {
    if (!__DEV__) return;
    const at = (t: number) => {
      mode.set(2);
      pin.set(t);
      progress.set(storyAt(t, count));
      elapsed.set(t);
      age.set(landingAt(t));
    };
    const hook = {
      pin: at,
      at,
      progress: (p: number) => {
        pin.set(-1);
        mode.set(2);
        progress.set(p);
      },
      play: () => {
        pin.set(-1);
        mode.set(0);
        elapsed.set(0);
        progress.set(0);
      },
      state: () => ({ progress: progress.get(), t: elapsed.get(), page: activePage }),
    };
    (globalThis as Record<string, unknown>).__ers = hook;
    return () => {
      if ((globalThis as Record<string, unknown>).__ers === hook) delete (globalThis as Record<string, unknown>).__ers;
    };
  }, [activePage, age, count, elapsed, mode, pin, progress]);
  const changePage = useCallback(
    (delta: number) => {
      if (!count) return;
      pin.set(-1);
      mode.set(1);
      lastTouch.set(clock.get());
      setInteracting(true);
      const current = Math.round(((progress.get() % count) + count) % count),
        target = clamp(current + delta, 0, count - 1);
      if (target === current && delta > 0) {
        onComplete?.();
        return;
      }
      progress.set(reduce ? withTiming(target, { duration: 250, reduceMotion: ReduceMotion.Never }) : withSpring(target, SPRING.page));
      cue('grab');
    },
    [clock, count, lastTouch, mode, onComplete, pin, progress, reduce],
  );
  const advance = useCallback(() => {
    if (!count) return;
    const current = Math.round(((progress.get() % count) + count) % count);
    if (current < count - 1) {
      changePage(1);
      return;
    }
    pin.set(-1);
    mode.set(1);
    lastTouch.set(clock.get());
    setInteracting(true);
    cue('grab');
    if (reduce) {
      progress.set(withTiming(0, { duration: 250, reduceMotion: ReduceMotion.Never }));
      return;
    }
    progress.set(current);
    progress.set(
      withSpring(count, SPRING.glide, (finished) => {
        if (finished) progress.set(0);
      }),
    );
  }, [changePage, clock, count, lastTouch, mode, pin, progress, reduce]);
  const pan = useMemo(
    () =>
      Gesture.Pan()
        .enabled(count > 0 && canDraw)
        .minDistance(0)
        .maxPointers(1)
        .onBegin(() => {
          cancelAnimation(progress);
          cancelAnimation(steerX);
          cancelAnimation(steerY);
          cancelAnimation(tiltOffset);
          pin.set(-1);
          mode.set(1);
          held.set(true);
          downAt.set(touchTime());
          lastTouch.set(clock.get());
          const p = ((progress.get() % count) + count) % count;
          progress.set(p);
          origin.set(p);
          intent.set(false);
          wallCue.set(false);
          landedPage.set(Math.round(p));
          scheduleOnRN(setInteracting, true);
          scheduleOnRN(cue, 'grab');
        })
        .onUpdate((e) => {
          const dx = e.translationX / layout.s,
            dy = e.translationY / layout.s;
          if (Math.abs(dx) >= GESTURE.intent) intent.set(true);
          if (intent.get()) {
            const raw = origin.get() - dx / GESTURE.distance,
              clamped = clamp(raw, 0, count - 1),
              overscroll = raw - clamped;
            limit.set((0.3 * GESTURE.rubber * Math.abs(overscroll)) / (1 + GESTURE.rubber * Math.abs(overscroll)));
            if (!reduce) progress.set(clamped);
            speed.set(Math.abs(e.velocityX / layout.s / GESTURE.distance));
            if (raw % 1 > 0.55 && !wallCue.get()) {
              wallCue.set(true);
              scheduleOnRN(cue, 'wall');
            }
          } else if (steer && !reduce) {
            steerX.set(clamp(dx / GESTURE.steerDistance, -1, 1) * GESTURE.steerAngle);
            steerY.set(clamp(dy / GESTURE.steerDistance, -1, 1) * GESTURE.steerAngle);
          }
        })
        .onEnd((e) => {
          const duration = touchTime() - downAt.get(),
            velocity = -e.velocityX / layout.s / GESTURE.distance;
          let target = Math.round(progress.get());
          if (intent.get()) {
            const delta = -e.translationX / layout.s / GESTURE.distance;
            target = reduce
              ? Math.round(origin.get()) +
                (Math.abs(delta) > GESTURE.threshold || Math.abs(e.velocityX / layout.s) > GESTURE.velocity ? Math.sign(delta) : 0)
              : Math.round(progress.get() + velocity * GESTURE.projection);
            if (!reduce && (Math.abs(delta) > GESTURE.threshold || Math.abs(e.velocityX / layout.s) > GESTURE.velocity)) {
              const direction = Math.abs(e.velocityX / layout.s) > GESTURE.velocity ? Math.sign(velocity) : Math.sign(delta);
              target = Math.round(origin.get()) + direction;
            }
          } else if (duration < GESTURE.hold) target = Math.round(origin.get()) + (e.x > width / 2 ? 1 : -1);
          target = clamp(target, 0, count - 1);
          if (target === count - 1 && origin.get() > count - 1 - 0.01 && e.x > width / 2 && onComplete) scheduleOnRN(onComplete);
          progress.set(
            reduce
              ? withTiming(target, { duration: 250, reduceMotion: ReduceMotion.Never })
              : withSpring(target, { ...SPRING.page, velocity }),
          );
        })
        .onFinalize(() => {
          held.set(false);
          lastTouch.set(clock.get());
          speed.set(0);
          steerX.set(withSpring(0, SPRING.steer));
          steerY.set(withSpring(0, SPRING.steer));
          tiltOffset.set(withSpring({ x: 0, y: 0 }, SPRING.steer));
          limit.set(withSpring(0, SPRING.page));
        }),
    [
      clock,
      count,
      canDraw,
      downAt,
      held,
      intent,
      lastTouch,
      landedPage,
      layout.s,
      limit,
      mode,
      onComplete,
      origin,
      pin,
      progress,
      reduce,
      speed,
      steer,
      steerX,
      steerY,
      tiltOffset,
      wallCue,
      width,
    ],
  );
  const uniforms = useDerivedValue(() => {
    const p = ((progress.get() % Math.max(1, count)) + Math.max(1, count)) % Math.max(1, count),
      i = Math.floor(p),
      n = (i + 1) % Math.max(1, count);
    const a = palette[i],
      b = palette[n],
      after = palette[(n + 1) % Math.max(1, count)];

    return {
      size: [width, height],
      center: [ref.hero.x, layout.heroY / layout.s],
      direction: a?.dir ?? [0.43, 0.9],
      nextDirection: b?.dir ?? [0.43, 0.9],
      q: p - i,
      reduced: reduce ? 1 : 0,
      current: i,
      next: n,
      count,
      fieldA: a?.a ?? [0, 0, 0],
      fieldB: a?.b ?? [0, 0, 0],
      nextA: b?.a ?? [0, 0, 0],
      nextB: b?.b ?? [0, 0, 0],
      afterB: after?.b ?? [0, 0, 0],
      faceA: a?.face ?? [0, 0, 0],
      faceB: b?.face ?? [0, 0, 0],
      steer: [steerX.get() + (steer && !reduce ? tiltOffset.get().x : 0), steerY.get() + (steer && !reduce ? tiltOffset.get().y : 0)],
      phase: reduce ? 0 : (elapsed.get() % LOOP) / LOOP,
      age: age.get(),
      steps: speed.get() > 2 ? 40 : 64,
      loft: loft ? 1 : 0,
      p3: nativeP3,
      px: REF.w / width / pixelRatio,
      cardBottom: layout.cardBottom / layout.s + comp.card.peek - (tokens?.peek ?? comp.card.peek),
      radius: tokens?.radius ?? comp.card.radius,
      peek: tokens?.peek ?? comp.card.peek,
      stretch: limit.get(),
      bloom: 0,
    };
  });
  const bloomUniforms = useDerivedValue(() => ({ ...uniforms.get(), size: [width / 4, height / 4], bloom: 1 }));
  const ready = (fontsLoaded || !!fontError) && atlas && effect,
    page = pages[activePage];
  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      {interacting && steer && !reduce && foreground ? <MotionAim value={tiltOffset} held={held} /> : null}
      <GestureDetector gesture={pan}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityRole="adjustable"
          accessibilityLabel={
            page ? [page.stat?.value, page.headline.replace(/\n/g, ' '), page.caption].filter(Boolean).join('. ') : 'No stories yet'
          }
          accessibilityHint="Swipe left for the next page. Hold and drag to aim the light."
          accessibilityValue={{ text: `Page ${activePage + 1} of ${Math.max(1, count)}` }}
          accessibilityActions={[
            { name: 'increment', label: COPY.next },
            { name: 'decrement', label: COPY.previous },
          ]}
          onAccessibilityAction={(e) => {
            if (e.nativeEvent.actionName === 'increment') changePage(1);
            else if (e.nativeEvent.actionName === 'decrement') changePage(-1);
          }}
        >
          {ready ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Fill>
                <Shader source={effect} uniforms={uniforms}>
                  <ImageShader image={atlas} tx="clamp" ty="clamp" fit="none" />
                </Shader>
              </Fill>
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      {ready && !reduce ? (
        <View pointerEvents="none" style={StyleSheet.absoluteFill}>
          <Canvas style={{ width: width / 4, height: height / 4, transform: [{ scale: 4 }], transformOrigin: [0, 0, 0], opacity: 0.16 }}>
            <Group
              layer={
                <Paint>
                  <Blur blur={2} />
                </Paint>
              }
            >
              <Fill>
                <Shader source={effect} uniforms={bloomUniforms}>
                  <ImageShader image={atlas} tx="clamp" ty="clamp" fit="none" />
                </Shader>
              </Fill>
            </Group>
          </Canvas>
        </View>
      ) : null}
      {ready
        ? pages.map((p, i) => (
            <PageCopy
              key={i}
              page={p}
              pageIndex={i}
              count={count}
              progress={progress}
              age={age}
              width={width}
              y={p.stat && fontScale < 1.4 ? layout.titleY + 170 * layout.s : layout.titleY}
              scale={layout.s}
              largeType={fontScale >= 1.4}
              reduced={reduce}
              isActive={activePage === i}
              tokens={tokens}
            />
          ))
        : null}
      {ready && count > 1 ? (
        <Pressable
          onPress={advance}
          accessibilityRole="button"
          accessibilityLabel={`${activePage === count - 1 ? COPY.replay : COPY.upNext}: ${peekTitle(pages[(activePage + 1) % count])}`}
          style={[
            styles.peek,
            { top: layout.cardBottom + ref.space.gap * layout.s, height: height - layout.cardBottom - ref.space.gap * layout.s },
          ]}
        >
          {pages.map((p, i) => (
            <PeekLabel
              key={i}
              pageIndex={i}
              count={count}
              progress={progress}
              age={age}
              reduced={reduce}
              height={layout.peekRow}
              scale={layout.s}
              prefix={i === count - 1 ? COPY.replay : COPY.upNext}
              title={peekTitle(pages[(i + 1) % count])}
              tokens={tokens}
            />
          ))}
        </Pressable>
      ) : null}
      <View style={[styles.header, { top: layout.headerY, paddingHorizontal: ref.space.edge * layout.s }]}>
        {header?.onClose ? (
          <Pressable
            onPress={header.onClose}
            accessibilityRole="button"
            accessibilityLabel={COPY.close}
            style={({ pressed }) => [styles.target, { opacity: pressed ? 0.55 : 1, transform: [{ translateX: -12 * layout.s }] }]}
          >
            <Svg width={20} height={20} viewBox="0 0 20 20">
              <Path d="M3 3L17 17M17 3L3 17" stroke={sys.ink.primary} strokeWidth={1.8} strokeLinecap="round" />
            </Svg>
          </Pressable>
        ) : (
          <View style={styles.target} />
        )}
        <View pointerEvents="none" style={styles.brand}>
          {header?.brand ?? (
            <View style={styles.wordmark}>
              <Text
                allowFontScaling={false}
                style={{ fontFamily: ref.type.display, fontSize: ref.type.brand * layout.s, color: sys.ink.primary }}
              >
                {COPY.brand}
              </Text>
              <Svg width={24 * layout.s} height={24 * layout.s} viewBox="0 0 24 24">
                <Path
                  d="M12 1L13.8 8.2L20 4L15.8 10.2L23 12L15.8 13.8L20 20L13.8 15.8L12 23L10.2 15.8L4 20L8.2 13.8L1 12L8.2 10.2L4 4L10.2 8.2Z"
                  fill={sys.ink.primary}
                />
              </Svg>
              <Text
                allowFontScaling={false}
                style={{ fontFamily: ref.type.display, fontSize: ref.type.brand * layout.s, color: sys.ink.primary }}
              >
                Music
              </Text>
            </View>
          )}
        </View>
        {header?.showCount !== false ? (
          <Text allowFontScaling={false} style={{ color: sys.ink.primary, fontFamily: ref.type.body, fontSize: ref.type.count * layout.s }}>
            {activePage + 1} / {count}
          </Text>
        ) : null}
      </View>
      {autoPlay || autoAdvanceMs != null ? (
        <Pressable
          accessibilityRole="button"
          disabled={reduce || screenReader}
          accessibilityLabel={
            reduce || screenReader ? 'Animated playback disabled by accessibility settings' : paused ? 'Play recap' : 'Pause recap'
          }
          accessibilityState={{ selected: paused, disabled: reduce || screenReader }}
          onPress={() => setPaused((p) => !p)}
          style={({ pressed }) => [
            styles.play,
            { top: layout.headerY + 44, right: ref.space.edge * layout.s, opacity: pressed ? 0.55 : 1 },
          ]}
        >
          <Svg width={20} height={20} viewBox="0 0 20 20">
            <Path d={paused || reduce || screenReader ? 'M5 3L17 10L5 17Z' : 'M5 3H7V17H5ZM13 3H15V17H13Z'} fill={sys.ink.primary} />
          </Svg>
        </Pressable>
      ) : null}
      {!count ? <Text style={styles.empty}>Your next chapter starts here.</Text> : null}
    </View>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: sys.surface.page },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  target: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  wordmark: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  brand: { position: 'absolute', left: 64, right: 64, alignItems: 'center' },
  play: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  peek: { position: 'absolute', left: 0, right: 0 },
  peekRow: { position: 'absolute', left: 0, right: 0, top: 0, flexDirection: 'row', alignItems: 'center' },
  empty: { color: sys.ink.primary, textAlign: 'center', marginTop: 220, fontSize: 20 },
});
