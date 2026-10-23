import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { ARTICLES, HABITS, LABEL, type ArticleKey } from './content';
import { DepthActor, WarmLayers, useLayerImages, type ActorFrame } from './DepthActor';
import {
  DEPTH,
  DISMISS,
  LOOP,
  PRESS_IN_MS,
  PRESS_SCALE,
  READER,
  RESUME_AFTER,
  SPRING,
  T,
  clamp01,
  easeOut,
  lerp,
  liftOf,
  pose,
  rubber,
  seg,
  separationOf,
  smooth,
  tiltOf,
  tipPose,
} from './motion';
import { Rail } from './Rail';
import { colors, layout, radius, REF_SAFE_TOP, REF_WIDTH, space, type } from './theme';
import { Tip, type TipFrame } from './Tip';

export { LOOP };

type Section = 'insights' | 'longevity' | 'browse';
type ViewFrame = { press: number; g: number; driftTime: number; drift: number };

const FLATS: Record<ArticleKey, number> = {
  morning: require('./assets/ForYouInsights-morning.jpg'),
  food: require('./assets/ForYouInsights-food.jpg'),
  run: require('./assets/ForYouInsights-run.jpg'),
};

const GLASS = isLiquidGlassAvailable();
const WARM_SECONDS = 1;
const EASE_IN_OUT = Easing.bezier(0.77, 0, 0.175, 1);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);


function selection(): void {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function lightImpact(): void {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

export function ForYouInsights({ autoPlay = false }: { autoPlay?: boolean }) {
  const insets = useSafeAreaInsets();
  const { width: screenWidth } = useWindowDimensions();
  const k = screenWidth / REF_WIDTH;
  const safeTop = insets.top;
  const y = useCallback((ref: number) => safeTop + (ref - REF_SAFE_TOP) * k, [safeTop, k]);
  const reduced = useReducedMotion();
  const images = useLayerImages();

  const [section, setSection] = useState<Section>('insights');
  const [article, setArticle] = useState(0);
  const [readerLive, setReaderLive] = useState(false);
  const [openNonce, setOpenNonce] = useState(0);

  const auto = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const resumeOn = useSharedValue(autoPlay && !reduced ? 1 : 0);
  const quiet = useSharedValue(reduced ? 1 : 0);
  const origin = useSharedValue(-1);
  const now = useSharedValue(0);
  const lastTouch = useSharedValue(0);
  const touching = useSharedValue(0);
  const closing = useSharedValue(0);
  const scrolledBack = useSharedValue(0);
  const onInsights = useSharedValue(1);
  const scrollY = useSharedValue(0);
  /** 1 during the first second after mount: the actor draws once over its row (identical pixels) so its first flight starts warm. */
  const warm = useSharedValue(1);
  const mountedAt = useSharedValue(-1);
  const rowBase = useSharedValue<number>(layout.rowTop);
  const actorIndex = useSharedValue(0);
  const mG = useSharedValue(0);
  const mPress = useSharedValue(0);
  const mStreamStart = useSharedValue(-100);
  const mDriftStart = useSharedValue(0);
  const mDrift = useSharedValue(0);
  const touchX = useSharedValue(0);
  const touchY = useSharedValue(0);
  const dragY = useSharedValue(0);
  const tip = useSharedValue<TipFrame>({ ghost: 1, stream: 1, caret: 0, shimmer: -1 });
  const view = useSharedValue<ViewFrame>({ press: 0, g: 0, driftTime: 0, drift: 0 });
  const scrollRef = useAnimatedRef<Animated.ScrollView>();

  useEffect(() => {
    quiet.set(reduced ? 1 : 0);
    const on = autoPlay && !reduced ? 1 : 0;
    resumeOn.set(on);
    if (!on) auto.set(0);
  }, [autoPlay, auto, quiet, reduced, resumeOn]);

  /** Writes the frame only when it changed, so a resting screen redraws nothing. */
  const publish = useCallback((ghost: number, stream: number, caret: number, shimmer: number, press: number, g: number, driftTime: number, drift: number) => {
    'worklet';
    const a = tip.get();
    if (a.ghost !== ghost || a.stream !== stream || a.caret !== caret || a.shimmer !== shimmer) tip.set({ ghost, stream, caret, shimmer });
    const b = view.get();
    const moving = drift > 0 && b.driftTime !== driftTime;
    if (b.press !== press || b.g !== g || b.drift !== drift || moving) view.set({ press, g, driftTime, drift });
  }, [tip, view]);

  // ——— the one clock: the loop in autoPlay, the touch-driven values otherwise ———
  useFrameCallback((info) => {
    'worklet';
    const t = info.timestamp / 1000;
    now.set(t);
    if (mountedAt.get() < 0) mountedAt.set(t);
    if (warm.get() === 1 && t - mountedAt.get() > WARM_SECONDS) warm.set(0);
    if (auto.get() === 1) {
      if (origin.get() < 0) origin.set(t);
      const p = pose(t - origin.get());
      publish(p.tip.ghost, p.tip.stream, p.tip.caret, p.tip.shimmer, p.open.press, p.open.g, p.open.driftTime, p.open.drift);
      return;
    }
    const tp = tipPose(t - mStreamStart.get());
    publish(tp.ghost, tp.stream, tp.caret, tp.shimmer, mPress.get(), mG.get(), t - mDriftStart.get(), mDrift.get());
    if (resumeOn.get() !== 1 || touching.get() === 1 || onInsights.get() !== 1) return;
    if (t - lastTouch.get() < RESUME_AFTER) return;
    if (mG.get() > 0.001) {
      if (closing.get() === 0) {
        closing.set(1);
        mDrift.set(withTiming(0, { duration: 260 }));
        touchX.set(withSpring(0, SPRING.tiltBack));
        touchY.set(withSpring(0, SPRING.tiltBack));
        dragY.set(withSpring(0, SPRING.dragBack));
        mG.set(withSpring(0, SPRING.touchClose));
      }
      return;
    }
    if (scrollY.get() > 2) {
      if (scrolledBack.get() === 0) {
        scrolledBack.set(1);
        scrollTo(scrollRef, 0, 0, true);
      }
      return;
    }
    if (tp.active || mPress.get() > 0.001) return;
    closing.set(0);
    scrolledBack.set(0);
    actorIndex.set(0);
    rowBase.set(layout.rowTop);
    origin.set(t);
    auto.set(1);
    scheduleOnRN(setArticle, 0);
  });

  /** A touch takes over from the loop at exactly the frame it shows. */
  const takeOver = useCallback(() => {
    'worklet';
    const t = now.get();
    lastTouch.set(t);
    closing.set(0);
    if (auto.get() !== 1) return;
    auto.set(0);
    const loopT = (((t - origin.get()) % LOOP) + LOOP) % LOOP;
    const p = pose(loopT);
    mStreamStart.set(p.tip.active ? t - (loopT - T.dimStart) : -100);
    mPress.set(p.open.press);
    mPress.set(withTiming(0, { duration: 200 }));
    mDriftStart.set(t - p.open.driftTime);
    mG.set(p.open.g);
    if (p.open.g > 0.001) {
      mG.set(withSpring(p.open.opening ? 1 : 0, p.open.opening ? SPRING.touchOpen : SPRING.touchClose));
      mDrift.set(p.open.drift);
      if (p.open.opening) mDrift.set(withTiming(1, { duration: 500 }));
    }
  }, [auto, closing, lastTouch, mDrift, mDriftStart, mG, mPress, mStreamStart, now, origin]);

  const touchEnd = useCallback(() => {
    'worklet';
    touching.set(0);
    lastTouch.set(now.get());
  }, [lastTouch, now, touching]);

  const openOnUI = useCallback(() => {
    'worklet';
    takeOver();
    if (quiet.get() === 1) {
      mG.set(withTiming(1, { duration: 200 }));
      return;
    }
    mDriftStart.set(now.get() + 0.4);
    mDrift.set(0);
    mDrift.set(withDelay(400, withTiming(1, { duration: 550, easing: EASE_IN_OUT })));
    mG.set(withSpring(1, SPRING.touchOpen));
  }, [mDrift, mDriftStart, mG, now, quiet, takeOver]);

  const closeOnUI = useCallback(() => {
    'worklet';
    takeOver();
    mDrift.set(withTiming(0, { duration: 260 }));
    touchX.set(withSpring(0, SPRING.tiltBack));
    touchY.set(withSpring(0, SPRING.tiltBack));
    dragY.set(withSpring(0, SPRING.dragBack));
    mG.set(quiet.get() === 1 ? withTiming(0, { duration: 200 }) : withSpring(0, SPRING.touchClose));
  }, [dragY, mDrift, mG, quiet, takeOver, touchX, touchY]);

  useEffect(() => {
    if (openNonce > 0) scheduleOnUI(openOnUI);
  }, [openNonce, openOnUI]);

  const beginOpen = useCallback((index: number) => {
    selection();
    setArticle(index);
    setOpenNonce((n) => n + 1);
  }, []);

  /** The UI thread decides: a tap while a card is in flight or open merges into it (ignored). */
  const openArticle = useCallback((index: number) => {
    if (readerLive) return;
    const base = section === 'browse' ? layout.browseRowTop : layout.rowTop;
    scheduleOnUI((i: number, b: number) => {
      'worklet';
      takeOver();
      if (mG.get() > 0.001) return;
      actorIndex.set(i);
      rowBase.set(b);
      scheduleOnRN(beginOpen, i);
    }, index, base);
  }, [actorIndex, beginOpen, mG, readerLive, rowBase, section, takeOver]);

  const pressRow = useCallback((index: number, down: boolean) => {
    scheduleOnUI((i: number, isDown: boolean) => {
      'worklet';
      if (isDown) {
        takeOver();
        if (mG.get() > 0.001) return;
        actorIndex.set(i);
        mPress.set(withTiming(1, { duration: PRESS_IN_MS, easing: EASE_OUT }));
      } else mPress.set(withTiming(0, { duration: 200, easing: EASE_OUT }));
    }, index, down);
  }, [actorIndex, mG, mPress, takeOver]);

  const closeReader = useCallback(() => {
    selection();
    scheduleOnUI(closeOnUI);
  }, [closeOnUI]);

  const regenerate = useCallback(() => {
    if (reduced) return;
    selection();
    scheduleOnUI(() => {
      'worklet';
      takeOver();
      if (!tipPose(now.get() - mStreamStart.get()).active) mStreamStart.set(now.get());
    });
  }, [mStreamStart, now, reduced, takeOver]);

  const selectSection = useCallback((next: Section) => {
    if (next === section || readerLive) return;
    selection();
    setSection(next);
    const insights = next === 'insights' ? 1 : 0;
    scheduleOnUI((on: number) => {
      'worklet';
      takeOver();
      onInsights.set(on);
      scrollTo(scrollRef, 0, 0, false);
    }, insights);
  }, [onInsights, readerLive, scrollRef, section, takeOver]);

  useAnimatedReaction(
    () => auto.get() === 0 && view.get().g > 0.98,
    (live, previous) => {
      if (live !== previous) scheduleOnRN(setReaderLive, live);
    },
  );

  const onScroll = useAnimatedScrollHandler((event) => {
    scrollY.set(event.contentOffset.y);
  });

  // ——— geometry of the moving card, one object per frame ———
  const heroX = layout.heroInset * k;
  const heroY = y(layout.heroTop);
  const heroW = screenWidth - heroX * 2;
  const heroH = layout.heroHeight * k;
  const actor = useDerivedValue<ActorFrame>(() => {
    const v = view.get();
    const q = quiet.get() === 1;
    const g = v.g;
    const i = actorIndex.get();
    const srcX = layout.rowImageX * k;
    const srcY = safeTop + (rowBase.get() + layout.rowPitch * i - REF_SAFE_TOP) * k - scrollY.get();
    const srcS = layout.rowImageSize * k;
    const m = q ? 1 : g;
    const tilt = tiltOf(g, v.driftTime, v.drift);
    const tx = touchX.get() / (DEPTH.touchTiltRange * k);
    const ty = touchY.get() / (DEPTH.touchTiltRange * k);
    const d = dragY.get();
    const press = 1 - (1 - PRESS_SCALE) * v.press * (1 - smooth(seg(g, 0, 0.5)));
    const flight = 1 + DEPTH.liftScale * Math.sin(Math.PI * clamp01(g));
    const pull = 1 - DISMISS.scaleLoss * clamp01(d / (DISMISS.distance * k * 2));
    return {
      visible: q ? clamp01(g) : g > 0.0005 || warm.get() === 1 ? 1 : 0,
      x: lerp(srcX, heroX, m),
      y: lerp(srcY, heroY, m) + d,
      w: lerp(srcS, heroW, m),
      h: lerp(srcS, heroH, m),
      r: lerp(radius.image, radius.hero, clamp01(m)) * k,
      scale: q ? 1 : press * flight * pull,
      tiltX: q ? 0 : tilt.x - Math.tanh(ty) * DEPTH.touchTiltX,
      tiltY: q ? 0 : tilt.y + Math.tanh(tx) * DEPTH.touchTiltY,
      lift: q ? 0 : liftOf(g),
      sep: q ? 0 : separationOf(g),
    };
  });

  // ——— reader gestures ———
  const pan = useMemo(() => Gesture.Pan()
    .enabled(readerLive)
    .minDistance(4)
    .onBegin(() => {
      touching.set(1);
      takeOver();
    })
    .onUpdate((e) => {
      touchX.set(e.translationX);
      touchY.set(e.translationY);
      dragY.set(rubber(e.translationY / k) * k);
    })
    .onEnd((e) => {
      const dismiss = e.translationY > DISMISS.distance * k || e.velocityY > DISMISS.velocity;
      if (dismiss) {
        scheduleOnRN(lightImpact);
        closeOnUI();
        return;
      }
      touchX.set(withSpring(0, SPRING.tiltBack));
      touchY.set(withSpring(0, SPRING.tiltBack));
      dragY.set(withSpring(0, SPRING.dragBack));
    })
    .onFinalize(touchEnd), [closeOnUI, dragY, k, readerLive, takeOver, touchEnd, touchX, touchY, touching]);

  // ——— animated styles ———
  const feedStyle = useAnimatedStyle(() => {
    const g = view.get().g;
    if (quiet.get() === 1) return { transform: [{ scale: 1 }] };
    return { transform: [{ scale: lerp(1, READER.feedScale, smooth(seg(g, 0.05, 0.8))) }] };
  });
  const backdropStyle = useAnimatedStyle(() => {
    const g = view.get().g;
    return { opacity: quiet.get() === 1 ? clamp01(g) : smooth(seg(g, READER.backdrop[0], READER.backdrop[1])) };
  });
  const tabStyle = useAnimatedStyle(() => {
    const g = view.get().g;
    if (quiet.get() === 1) return { opacity: 1 - clamp01(g), transform: [{ translateY: 0 }] };
    return { opacity: g > READER.tabHidden ? 0 : 1, transform: [{ translateY: READER.tabDrop * k * easeOut(seg(g, READER.tabOut[0], READER.tabOut[1])) }] };
  });
  const closeStyle = useAnimatedStyle(() => {
    const g = view.get().g;
    const p = seg(g, READER.closeIn[0], READER.closeIn[1]);
    return {
      opacity: smooth(p),
      transform: [{ translateY: dragY.get() }, { scale: quiet.get() === 1 ? 1 : lerp(READER.closeScaleFrom, 1, easeOut(p)) }],
    };
  });

  const current = ARTICLES[article];
  const feedHeight = y(layout.rowTop + layout.rowPitch * ARTICLES.length) + (layout.tabBarHeight + space.screen) * k + insets.bottom;

  return (
    <GestureHandlerRootView
      style={styles.root}
      onTouchStart={() => scheduleOnUI(() => {
        'worklet';
        touching.set(1);
        takeOver();
      })}
      onTouchEnd={() => scheduleOnUI(touchEnd)}
      onTouchCancel={() => scheduleOnUI(touchEnd)}
    >
      <StatusBar style="dark" />
      <Animated.View style={[StyleSheet.absoluteFill, feedStyle]}>
        <Animated.ScrollView
          ref={scrollRef}
          onScroll={onScroll}
          scrollEventThrottle={16}
          scrollEnabled={!readerLive}
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{ width: screenWidth, height: feedHeight }}
          accessibilityElementsHidden={readerLive}
          importantForAccessibility={readerLive ? 'no-hide-descendants' : 'auto'}
        >
          <SectionLayer visible={section === 'insights'}>
            <Rail k={k} y={y} />
            <View style={[styles.divider, { top: y(layout.railDivider), left: 0, width: screenWidth }]} />
            <Heading k={k} y={y} title="For You" />
            <View style={[styles.label, { left: (layout.column + layout.labelInset) * k, top: y(layout.labelTop), height: layout.labelHeight * k, gap: layout.labelIconGap * k }]}>
              <Ionicons name="person" size={layout.labelIcon * k} color={colors.pink} />
              <Text style={{ color: colors.pink, fontSize: type.label.size * k, fontWeight: type.label.weight, letterSpacing: type.label.tracking * k }}>{LABEL}</Text>
            </View>
            <Pressable
              onPress={regenerate}
              accessibilityRole="button"
              accessibilityLabel="Write this insight again"
              style={[styles.ellipsisHit, { left: (layout.ellipsisCenter.x - 22) * k, top: y(layout.ellipsisCenter.y) - 22 * k, width: 44 * k, height: 44 * k }]}
            >
              <View style={[styles.ellipsis, { width: layout.ellipsisSize * k, height: layout.ellipsisSize * k, borderRadius: radius.ellipsis * k }]}>
                <Ionicons name="ellipsis-horizontal" size={layout.ellipsisGlyph * k} color={colors.ink} />
              </View>
            </Pressable>
            <Tip k={k} top={y(layout.tipTop)} frame={tip} />
            <View style={[styles.divider, { top: y(layout.tipDivider), left: layout.column * k, width: layout.columnWidth * k }]} />
            <Rows k={k} y={y} base={layout.rowTop} view={view} actorIndex={actorIndex} pressRow={pressRow} openArticle={openArticle} />
          </SectionLayer>
          <SectionLayer visible={section === 'longevity'}>
            <Heading k={k} y={y} title="Longevity" />
            <Text style={[styles.subhead, { left: layout.column * k, top: y(layout.labelTop), fontSize: type.sectionSubhead.size * k }]}>Small habits that support a steadier day</Text>
            <Habits k={k} y={y} />
          </SectionLayer>
          <SectionLayer visible={section === 'browse'}>
            <Heading k={k} y={y} title="Browse" />
            <Text style={[styles.subhead, { left: layout.column * k, top: y(layout.labelTop), fontSize: type.sectionSubhead.size * k }]}>Guides for everyday routines</Text>
            <Rows k={k} y={y} base={layout.browseRowTop} view={view} actorIndex={actorIndex} pressRow={pressRow} openArticle={openArticle} />
          </SectionLayer>
        </Animated.ScrollView>
      </Animated.View>
      <View pointerEvents="none" style={[styles.statusCover, { height: safeTop }]} />
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.backdrop, backdropStyle]} />
      <DepthActor images={images} actorIndex={actorIndex} warm={warm} actor={actor} />
      <WarmLayers images={images} />
      {ARTICLES.map((item, index) => (
        <Reader
          key={item.key}
          article={index}
          k={k}
          top={heroY + heroH + layout.readerTitleGap * k}
          bottomInset={insets.bottom}
          view={view}
          quiet={quiet}
          dragY={dragY}
          actorIndex={actorIndex}
          live={readerLive && article === index}
        />
      ))}
      <GestureDetector gesture={pan}>
        <View
          pointerEvents={readerLive ? 'auto' : 'none'}
          accessible={false}
          style={{ position: 'absolute', left: heroX, top: heroY, width: heroW, height: heroH }}
        />
      </GestureDetector>
      <Animated.View
        pointerEvents={readerLive ? 'box-none' : 'none'}
        style={[styles.close, { left: heroX + layout.closeInset * k - 4 * k, top: heroY + layout.closeInset * k - 4 * k, width: (layout.closeSize + 8) * k, height: (layout.closeSize + 8) * k }, closeStyle]}
      >
        <Pressable onPress={closeReader} accessibilityRole="button" accessibilityLabel={`Close ${current.title}`} style={styles.closeHit}>
          <View style={[styles.closeDisc, { width: layout.closeSize * k, height: layout.closeSize * k, borderRadius: radius.close * k }]}>
            <Ionicons name="close" size={18 * k} color={colors.ink} />
          </View>
        </Pressable>
      </Animated.View>
      <Animated.View pointerEvents={readerLive ? 'none' : 'box-none'} style={[StyleSheet.absoluteFill, tabStyle]}>
        <TabBar k={k} top={y(layout.tabBarTop)} screenWidth={screenWidth} section={section} onSelect={selectSection} />
      </Animated.View>
    </GestureHandlerRootView>
  );
}

/** Sections stay mounted and switch by opacity, so a tab change mounts nothing. */
function SectionLayer({ visible, children }: { visible: boolean; children: React.ReactNode }) {
  return (
    <View
      pointerEvents={visible ? 'box-none' : 'none'}
      accessibilityElementsHidden={!visible}
      importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
      style={[StyleSheet.absoluteFill, { opacity: visible ? 1 : 0 }]}
    >
      {children}
    </View>
  );
}

function Heading({ k, y, title }: { k: number; y: (ref: number) => number; title: string }) {
  return (
    <>
      <Text style={[styles.heading, { left: layout.column * k, top: y(layout.headingTop), fontSize: type.heading.size * k, letterSpacing: type.heading.tracking * k }]} accessibilityRole="header">
        {title}
      </Text>
      <View style={[styles.divider, { top: y(layout.headingDivider), left: layout.column * k, width: layout.columnWidth * k }]} />
    </>
  );
}

function Rows({ k, y, base, view, actorIndex, pressRow, openArticle }: {
  k: number; y: (ref: number) => number; base: number; view: SharedValue<ViewFrame>; actorIndex: SharedValue<number>;
  pressRow: (index: number, down: boolean) => void; openArticle: (index: number) => void;
}) {
  return (
    <>
      {ARTICLES.map((item, index) => (
        <ArticleRow
          key={item.key}
          index={index}
          top={base + layout.rowPitch * index}
          k={k}
          y={y}
          view={view}
          actorIndex={actorIndex}
          onPressIn={() => pressRow(index, true)}
          onPressOut={() => pressRow(index, false)}
          onPress={() => openArticle(index)}
        />
      ))}
    </>
  );
}

function ArticleRow({
  index, top, k, y, view, actorIndex, onPressIn, onPressOut, onPress,
}: {
  index: number; top: number; k: number; y: (ref: number) => number;
  view: SharedValue<ViewFrame>; actorIndex: SharedValue<number>;
  onPressIn: () => void; onPressOut: () => void; onPress: () => void;
}) {
  const item = ARTICLES[index];
  const imageStyle = useAnimatedStyle(() => {
    const v = view.get();
    const mine = actorIndex.get() === index;
    return {
      opacity: mine && v.g > READER.handOff ? 0 : 1,
      transform: [{ scale: mine ? 1 - (1 - PRESS_SCALE) * v.press * (1 - smooth(seg(v.g, 0, 0.5))) : 1 }],
    };
  });
  const titleStyle = useAnimatedStyle(() => {
    const v = view.get();
    const p = actorIndex.get() === index ? smooth(seg(v.g, READER.rowTitleOut[0], READER.rowTitleOut[1])) : 0;
    return { opacity: 1 - p, transform: [{ translateY: -READER.rowTitleRise * k * p }] };
  });
  const size = layout.rowImageSize * k;
  return (
    <>
      <Animated.Text
        style={[styles.rowTitle, titleStyle, { left: layout.column * k, top: y(top), width: layout.rowTextWidth * k, fontSize: type.rowTitle.size * k, lineHeight: type.rowTitle.line * k, letterSpacing: type.rowTitle.tracking * k }]}
        numberOfLines={3}
      >
        {item.title}
      </Animated.Text>
      <Text
        style={[styles.rowSummary, { left: layout.column * k, top: y(top + layout.rowSummaryOffset), width: layout.rowTextWidth * k, fontSize: type.rowSummary.size * k, lineHeight: type.rowSummary.line * k, letterSpacing: type.rowSummary.tracking * k }]}
        numberOfLines={3}
      >
        {item.summary}
      </Text>
      <View style={[styles.meta, { left: layout.column * k, top: y(top + layout.rowMetaOffset), gap: layout.rowMetaGap * k }]}>
        <Ionicons name="time" size={layout.rowMetaIcon * k} color={colors.meta} />
        <Text style={{ color: colors.meta, fontSize: type.rowMeta.size * k, fontWeight: type.rowMeta.weight }}>{item.readTime}</Text>
      </View>
      <Animated.View style={[styles.rowImage, imageStyle, { left: layout.rowImageX * k, top: y(top), width: size, height: size, borderRadius: radius.image * k }]}>
        <Image source={FLATS[item.key]} style={{ width: size, height: size }} accessible={false} />
      </Animated.View>
      <Pressable
        onPressIn={onPressIn}
        onPressOut={onPressOut}
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={`${item.title}. ${item.summary} ${item.readTime}.`}
        style={{ position: 'absolute', left: layout.column * k, top: y(top - space.md), width: layout.columnWidth * k, height: layout.rowPitch * k }}
      />
      <View style={[styles.divider, { top: y(top + layout.rowDividerOffset), left: layout.column * k, width: layout.columnWidth * k }]} />
    </>
  );
}

function Habits({ k, y }: { k: number; y: (ref: number) => number }) {
  return (
    <>
      {HABITS.map((habit, index) => (
        <View key={habit.title} style={[styles.habit, { left: layout.column * k, top: y(layout.habitTop + layout.habitPitch * index), width: layout.columnWidth * k, height: layout.habitPitch * k, gap: space.md * k, paddingTop: space.md * k }]}>
          <View style={[styles.habitIcon, { width: layout.habitIcon * k, height: layout.habitIcon * k, borderRadius: radius.habitIcon * k }]}>
            <Ionicons name={habit.icon} size={22 * k} color={habit.color} />
          </View>
          <View style={{ flex: 1, gap: space.xs * k }}>
            <Text style={{ color: colors.ink, fontSize: type.habitTitle.size * k, fontWeight: type.habitTitle.weight, letterSpacing: type.habitTitle.tracking * k }}>{habit.title}</Text>
            <Text style={{ color: colors.secondary, fontSize: type.habitBody.size * k, lineHeight: type.habitBody.line * k }} numberOfLines={3}>{habit.body}</Text>
          </View>
          <View style={[styles.divider, { left: 0, bottom: 0, width: layout.columnWidth * k }]} />
        </View>
      ))}
    </>
  );
}

function Reader({
  article, k, top, bottomInset, view, quiet, dragY, actorIndex, live,
}: {
  article: number; k: number; top: number; bottomInset: number;
  view: SharedValue<ViewFrame>; quiet: SharedValue<number>; dragY: SharedValue<number>;
  actorIndex: SharedValue<number>; live: boolean;
}) {
  const item = ARTICLES[article];
  const blocks = [
    <Text key="title" style={{ color: colors.ink, fontSize: type.readerTitle.size * k, lineHeight: type.readerTitle.line * k, fontWeight: type.readerTitle.weight, letterSpacing: type.readerTitle.tracking * k }} accessibilityRole="header">
      {item.title}
    </Text>,
    <View key="meta" style={{ flexDirection: 'row', alignItems: 'center', gap: space.xs * k, marginTop: layout.readerMetaGap * k }}>
      <Ionicons name="time" size={13 * k} color={colors.pink} />
      <Text style={{ color: colors.pink, fontSize: type.readerMeta.size * k, fontWeight: type.readerMeta.weight, letterSpacing: type.readerMeta.tracking * k }}>
        {`${item.readTime.toUpperCase()} · ${LABEL}`}
      </Text>
    </View>,
    <Text key="lead" style={{ marginTop: space.lg * k, color: colors.secondary, fontSize: type.readerLead.size * k, lineHeight: type.readerLead.line * k, fontWeight: type.readerLead.weight, letterSpacing: type.readerLead.tracking * k }}>
      {item.summary}
    </Text>,
    ...item.sections.map((part) => (
      <View key={part.heading} style={{ marginTop: layout.readerSectionGap * k, gap: layout.readerParagraphGap * k }}>
        <Text style={{ color: colors.ink, fontSize: type.readerHeading.size * k, lineHeight: type.readerHeading.line * k, fontWeight: type.readerHeading.weight, letterSpacing: type.readerHeading.tracking * k }}>{part.heading}</Text>
        <Text style={{ color: colors.secondary, fontSize: type.readerBody.size * k, lineHeight: type.readerBody.line * k, letterSpacing: type.readerBody.tracking * k }}>{part.body}</Text>
      </View>
    )),
  ];
  // All three readers stay mounted; the UI thread shows the one whose card is flying (no React work on tap).
  const containerStyle = useAnimatedStyle(() => ({ opacity: actorIndex.get() === article ? 1 : 0, transform: [{ translateY: dragY.get() }] }));
  return (
    <Animated.View
      pointerEvents={live ? 'auto' : 'none'}
      accessibilityElementsHidden={!live}
      importantForAccessibility={live ? 'auto' : 'no-hide-descendants'}
      style={[styles.reader, { top }, containerStyle]}
    >
      <Animated.ScrollView
        scrollEnabled={live}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: layout.readerTextInset * k, paddingBottom: (layout.readerBottom * k) + bottomInset }}
      >
        {blocks.map((block, index) => (
          <ReaderBlock key={index} index={index} k={k} view={view} quiet={quiet}>{block}</ReaderBlock>
        ))}
      </Animated.ScrollView>
    </Animated.View>
  );
}

function ReaderBlock({ index, k, view, quiet, children }: {
  index: number; k: number; view: SharedValue<ViewFrame>; quiet: SharedValue<number>; children: React.ReactNode;
}) {
  const style = useAnimatedStyle(() => {
    const g = view.get().g;
    if (quiet.get() === 1) return { opacity: clamp01(g), transform: [{ translateY: 0 }] };
    const range = index === 0 ? READER.titleIn : READER.bodyIn;
    const delay = index === 0 ? 0 : (index - 1) * READER.bodyStagger;
    const p = seg(g, range[0] + delay, range[1] + delay);
    const rise = index === 0 ? READER.titleRise : READER.bodyRise;
    const grow = index === 0 ? lerp(READER.titleScaleFrom, 1, easeOut(p)) : 1;
    return { opacity: smooth(p), transform: [{ translateY: (1 - easeOut(p)) * rise * k }, { scale: grow }] };
  });
  return <Animated.View style={style}>{children}</Animated.View>;
}

const TABS: readonly { section: Section; label: string; icon: React.ComponentProps<typeof Ionicons>['name'] }[] = [
  { section: 'insights', label: 'Insights', icon: 'heart' },
  { section: 'longevity', label: 'Longevity', icon: 'aperture' },
  { section: 'browse', label: 'Browse', icon: 'grid' },
];

/** Liquid Glass tab bar; the selection lens slides on a spring and stretches while it travels. */
function TabBar({ k, top, screenWidth, section, onSelect }: {
  k: number; top: number; screenWidth: number; section: Section; onSelect: (section: Section) => void;
}) {
  const index = TABS.findIndex((tab) => tab.section === section);
  const lens = useSharedValue(index);
  useEffect(() => {
    lens.set(withSpring(index, SPRING.lens));
  }, [index, lens]);
  const width = layout.tabBarWidth * k;
  const height = layout.tabBarHeight * k;
  const left = (screenWidth - width) / 2;
  const pitch = layout.tabPitch * k;
  const pillW = layout.tabPillWidth * k;
  const pillH = layout.tabPillHeight * k;
  const lensStyle = useAnimatedStyle(() => {
    const at = lens.get();
    const travel = clamp01(Math.abs(at - Math.round(at)) * 2);
    return {
      transform: [
        { translateX: (at - 1) * pitch },
        { scaleX: 1 + 0.16 * travel },
        { scaleY: 1 - 0.06 * travel },
      ],
    };
  });
  return (
    <View style={[styles.tabBar, { left, top, width, height, borderRadius: radius.tabBar * k }]}>
      {GLASS ? (
        <GlassView glassEffectStyle="regular" tintColor={colors.tabGlassTint} style={[StyleSheet.absoluteFill, { borderRadius: radius.tabBar * k }]} />
      ) : (
        <>
          <BlurView intensity={40} tint="light" style={[StyleSheet.absoluteFill, { borderRadius: radius.tabBar * k }]} />
          <View style={[StyleSheet.absoluteFill, { borderRadius: radius.tabBar * k, backgroundColor: colors.tabFallback }]} />
        </>
      )}
      <Animated.View
        pointerEvents="none"
        style={[styles.lens, { left: width / 2 - pillW / 2, top: (height - pillH) / 2, width: pillW, height: pillH, borderRadius: radius.tabPill * k }, lensStyle]}
      />
      {TABS.map((tab, i) => {
        const selected = tab.section === section;
        const tint = selected ? colors.blue : colors.ink;
        return (
          <Pressable
            key={tab.section}
            onPress={() => onSelect(tab.section)}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            accessibilityLabel={tab.label}
            style={[styles.tab, { left: width / 2 + (i - 1) * pitch - pillW / 2, width: pillW, height }]}
          >
            <View style={{ position: 'absolute', top: layout.tabIconCenter * k - layout.tabIcon * k / 2 }}>
              <Ionicons name={tab.icon} size={layout.tabIcon * k} color={tint} />
            </View>
            <Text style={{ position: 'absolute', top: layout.tabLabelCenter * k - type.tab.size * k * 0.62, color: tint, fontSize: type.tab.size * k, fontWeight: type.tab.weight }}>
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.page, overflow: 'hidden' },
  divider: { position: 'absolute', height: layout.divider, backgroundColor: colors.separator },
  heading: { position: 'absolute', color: colors.ink, fontWeight: type.heading.weight },
  label: { position: 'absolute', flexDirection: 'row', alignItems: 'center' },
  subhead: { position: 'absolute', color: colors.secondary, fontWeight: type.sectionSubhead.weight },
  ellipsisHit: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  ellipsis: { alignItems: 'center', justifyContent: 'center', backgroundColor: colors.ellipsisFill },
  rowTitle: { position: 'absolute', color: colors.ink, fontWeight: type.rowTitle.weight },
  rowSummary: { position: 'absolute', color: colors.tertiary, fontWeight: type.rowSummary.weight },
  meta: { position: 'absolute', flexDirection: 'row', alignItems: 'center' },
  rowImage: { position: 'absolute', overflow: 'hidden' },
  habit: { position: 'absolute', flexDirection: 'row', alignItems: 'flex-start' },
  habitIcon: { alignItems: 'center', justifyContent: 'center', backgroundColor: colors.white },
  statusCover: { position: 'absolute', left: 0, right: 0, top: 0, backgroundColor: colors.page },
  backdrop: { backgroundColor: colors.page },
  reader: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  close: { position: 'absolute' },
  closeHit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  // A solid frosted disc: a live glass over the drifting hero re-rendered every frame.
  closeDisc: { alignItems: 'center', justifyContent: 'center', backgroundColor: colors.closeDisc, borderWidth: StyleSheet.hairlineWidth, borderColor: colors.tabEdge, shadowColor: colors.shadow, shadowOpacity: 0.12, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
  tabBar: {
    position: 'absolute',
    shadowColor: colors.shadow,
    shadowOpacity: 0.12,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
  },
  lens: { position: 'absolute', backgroundColor: colors.pillFill, borderWidth: StyleSheet.hairlineWidth, borderColor: colors.pillEdge },
  tab: { position: 'absolute', top: 0, alignItems: 'center' },
});
