import { useFonts } from 'expo-font';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  cancelAnimation,
  type FrameInfo,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';

import { ITEMS, type Item } from './items';
import { LOGO_H, LOGO_W, Logo } from './Logo';
import { LOOP_MS, PROJECT_S, RESUME_MS, RUBBER, SNAP, carouselAt, outroAt } from './motion';
import { card, colors, fonts, logo, outro } from './theme';

const NEVER = ReduceMotion.Never;
const LAST = ITEMS.length - 1;
// plain number arrays for the worklets (they copy what they capture)
const XS = ITEMS.map((it) => it.x);
const GL = ITEMS.map((it) => it.gap[0]);
const GR = ITEMS.map((it) => it.gap[1]);

// The centre of the card row for a continuous index: each card rests where the pin shows it.
function restCentre(s: number) {
  'worklet';
  const i = Math.max(0, Math.min(LAST, Math.floor(s)));
  const j = Math.min(LAST, i + 1);
  const u = s - i;
  return XS[i] + (XS[j] - XS[i]) * u + card.width / 2;
}

function CardSlot({
  item,
  index,
  clock,
  drag,
}: {
  item: Item;
  index: number;
  clock: SharedValue<number>;
  drag: SharedValue<number>;
}) {
  const style = useAnimatedStyle(() => {
    const t = clock.get();
    const s = carouselAt(t) + drag.get();
    const p = index - s;
    const a = Math.min(Math.abs(p), 1);
    const scale = 1 - (1 - card.sideScale) * a;
    // neighbours sit at the distance the pin shows for the card at the centre, blended while moving
    const i0 = Math.max(0, Math.min(LAST, Math.floor(s)));
    const i1 = Math.min(LAST, i0 + 1);
    const u = s - i0;
    const gl = GL[i0] + (GL[i1] - GL[i0]) * u;
    const gr = GR[i0] + (GR[i1] - GR[i0]) * u;
    const x = restCentre(s) + p * (p < 0 ? gl : gr);
    const o = outroAt(t);
    // the whole row draws in and collapses into the screen's centre at the end
    const gx = 201 + (x - 201) * o.groupScale;
    return {
      opacity: Math.abs(p) > 2.2 ? 0 : o.groupOpacity,
      zIndex: Math.round(10 - Math.abs(p) * 3),
      transform: [
        { translateX: gx - card.width / 2 },
        { translateY: card.centerY - card.height / 2 },
        { scale: scale * o.groupScale },
      ],
    };
  });
  const t = item.t;
  return (
    <Animated.View style={[styles.card, style]} pointerEvents="none" accessibilityLabel={item.title}>
      <Image
        source={item.image}
        style={[styles.image, { left: item.img.l, top: item.img.t, width: item.img.w, height: item.img.h }]}
        contentFit="fill"
      />
      <Text
        numberOfLines={1}
        style={[
          styles.title,
          {
            fontSize: t.size,
            lineHeight: t.size,
            top: t.top - t.size * TITLE_ASCENT,
            transform: [{ translateX: t.cx - card.width / 2 }, { scaleX: t.sx }],
          },
        ]}
      >
        {item.title}
      </Text>
    </Animated.View>
  );
}
// gap between a Text's box top and its glyph tops, as a share of the font size (Lilita One)
const TITLE_ASCENT = 0.16;

// A 1 pt speck that flickers while autoplaying, so the simulator's recorder keeps streaming
// through still stretches (it stamps frames late after a fully still screen). Nobody can see it.
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
type Props = { autoPlay?: boolean; freezeAt?: number };

export function MenuProductCarousel({ autoPlay = false, freezeAt }: Props) {
  'use no memo';
  const { width } = useWindowDimensions();
  const [loaded] = useFonts({
    [fonts.title]: require('./assets/fonts/LilitaOne-Regular.ttf'),
    [fonts.word]: require('./assets/fonts/Anton-Regular.ttf'),
    [fonts.script]: require('./assets/fonts/KaushanScript-Regular.ttf'),
    [fonts.ui]: require('./assets/fonts/Montserrat-SemiBold.ttf'),
  });
  const frozen = freezeAt !== undefined;
  const reduced = useReducedMotion();
  const scripted = autoPlay && !reduced && !frozen && loaded;

  // ---- one clock: every beat is a pure function of it (motion.ts) ----
  const clock = useSharedValue(frozen ? freezeAt : 0);
  const start = useSharedValue(-1);
  const running = useSharedValue(false);
  const drag = useSharedValue(0);
  const dragStart = useSharedValue(0);
  const resumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const frameStep = useCallback((f: FrameInfo) => {
    'worklet';
    if (!running.get()) return;
    if (start.get() < 0) start.set(f.timestamp);
    clock.set((f.timestamp - start.get()) % LOOP_MS);
  }, [clock, running, start]);
  const frameDriver = useFrameCallback(frameStep, false);
  useEffect(() => {
    if (resumeTimer.current) {
      clearTimeout(resumeTimer.current);
      resumeTimer.current = null;
    }
    cancelAnimation(drag);
    if (frozen) {
      drag.set(0);
      dragStart.set(0);
      clock.set(freezeAt);
      running.set(false);
      frameDriver.setActive(false);
      return;
    }
    if (!scripted) {
      if (reduced) {
        const target = Math.max(0, Math.min(LAST, Math.round(carouselAt(clock.get()) + drag.get())));
        clock.set(0);
        const offset = target - carouselAt(0);
        drag.set(offset);
        dragStart.set(offset);
      } else {
        dragStart.set(drag.get());
      }
      running.set(false);
      frameDriver.setActive(false);
      return;
    }
    drag.set(0);
    dragStart.set(0);
    start.set(-1);
    running.set(true);
    frameDriver.setActive(true);
    return () => {
      running.set(false);
      frameDriver.setActive(false);
    };
  }, [clock, drag, dragStart, frameDriver, freezeAt, frozen, reduced, running, scripted, start]);

  // a touch holds the loop where it is and hands the row to the finger; 3.5 s after the last
  // touch the loop starts over
  useEffect(
    () => () => {
      if (resumeTimer.current) clearTimeout(resumeTimer.current);
    },
    [],
  );
  const onTouchStart = useCallback(() => {
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    running.set(false);
    frameDriver.setActive(false);
  }, [frameDriver, running]);
  const onTouchEnd = useCallback(() => {
    if (!scripted) return;
    if (resumeTimer.current) clearTimeout(resumeTimer.current);
    resumeTimer.current = setTimeout(() => {
      cancelAnimation(drag);
      drag.set(0);
      dragStart.set(0);
      start.set(-1);
      running.set(true);
      frameDriver.setActive(true);
    }, RESUME_MS);
  }, [drag, dragStart, frameDriver, running, scripted, start]);

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .maxPointers(1)
        .activeOffsetX([-8, 8])
        .onBegin(() => {
          cancelAnimation(drag);
          dragStart.set(drag.get());
        })
        .onUpdate((e) => {
          const base = carouselAt(clock.get());
          let s = base + dragStart.get() - e.translationX / card.pitch;
          if (s < 0) s *= RUBBER;
          if (s > LAST) s = LAST + (s - LAST) * RUBBER;
          drag.set(s - base);
        })
        .onEnd((e) => {
          const base = carouselAt(clock.get());
          const v = -e.velocityX / card.pitch;
          const target = Math.max(0, Math.min(LAST, Math.round(base + drag.get() + v * PROJECT_S)));
          drag.set(reduced ? target - base : withSpring(target - base, { ...SNAP, velocity: v }));
        }),
    [clock, drag, dragStart, reduced],
  );

  const topLogoStyle = useAnimatedStyle(() => ({ opacity: outroAt(clock.get()).topLogo }));
  const endLogoStyle = useAnimatedStyle(() => {
    const e = outroAt(clock.get()).endLogo;
    return { opacity: e > 0 ? 1 : 0, transform: [{ scale: Math.max(e, 0.02) * logo.endScale }] };
  });
  const dontStyle = useAnimatedStyle(() => ({ opacity: outroAt(clock.get()).dont }));
  const wordsStyle = useAnimatedStyle(() => {
    const o = outroAt(clock.get());
    return { opacity: o.wordsShown, transform: [{ translateX: o.wordsX }] };
  });

  if (!loaded) return <View style={styles.screen} />;
  const endCx = logo.endLeft + (LOGO_W * logo.endScale) / 2;
  const endCy = logo.endTop + (LOGO_H * logo.endScale) / 2;

  return (
    <View style={styles.screen} onTouchStart={frozen ? undefined : onTouchStart} onTouchEnd={onTouchEnd}>
      <StatusBar style="dark" />
      <Image source={require('./assets/topo-bg.png')} style={StyleSheet.absoluteFill} contentFit="cover" />
      {scripted && <RecorderPulse />}

      <Animated.View style={[styles.abs, { left: logo.left, top: logo.top }, topLogoStyle]}>
        <Logo />
      </Animated.View>

      <GestureDetector gesture={pan}>
        <View style={StyleSheet.absoluteFill} accessible accessibilityLabel="Menu: swipe through the dishes">
          {ITEMS.map((item, i) => (
            <CardSlot key={item.id} item={item} index={i} clock={clock} drag={drag} />
          ))}
        </View>
      </GestureDetector>

      <Animated.View style={[styles.abs, styles.full, { top: outro.dont.top }, dontStyle]} pointerEvents="none">
        <Text style={[styles.dont, { width }]}>Don’t wait</Text>
      </Animated.View>
      <Animated.View style={[styles.abs, styles.full, wordsStyle]} pointerEvents="none">
        <Text
          style={[
            styles.words,
            { top: outro.take.top - outro.take.size * WORD_ASCENT, right: width - outro.take.right },
          ]}
        >
          TAKE A
        </Text>
        <Text
          style={[
            styles.words,
            { top: outro.bite.top - outro.bite.size * WORD_ASCENT, right: width - outro.bite.right },
          ]}
        >
          BITE
        </Text>
      </Animated.View>

      <Animated.View
        style={[styles.abs, { left: endCx - LOGO_W / 2, top: endCy - LOGO_H / 2 }, endLogoStyle]}
        pointerEvents="none"
      >
        <Logo />
      </Animated.View>
    </View>
  );
}
// gap between an Anton line's box top and its cap tops, as a share of the size
const WORD_ASCENT = 0.12;

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg, overflow: 'hidden' },
  abs: { position: 'absolute' },
  full: { left: 0, right: 0 },
  pulse: { position: 'absolute', right: 0, bottom: 0, width: 1, height: 1, backgroundColor: '#FDD4BB' },
  card: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: card.width,
    height: card.height,
    borderRadius: card.radius,
    borderCurve: 'continuous',
    backgroundColor: colors.card,
    boxShadow: card.shadow,
  },
  image: { position: 'absolute' },
  title: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: fonts.title,
    color: colors.title,
    textShadowColor: 'rgba(255,255,255,0.35)',
    textShadowRadius: 6,
  },
  dont: { textAlign: 'center', fontFamily: fonts.ui, fontSize: outro.dont.size, color: colors.ink },
  words: {
    position: 'absolute',
    fontFamily: fonts.word,
    fontSize: 98.5,
    lineHeight: 110,
    color: colors.bite,
    transform: [{ scaleX: 1.27 }],
  },
});
