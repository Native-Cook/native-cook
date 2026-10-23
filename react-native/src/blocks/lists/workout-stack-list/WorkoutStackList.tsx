import { BlurView } from 'expo-blur';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  interpolate,
  type FrameInfo,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

import { ClassCardView } from './ClassCardView';
import { CATEGORIES, CROSSFIT, DAY_8, DAY_9, DIM_DAYS, DOTS, HERO, WEEKS } from './data';
import { BACK_ZOOM, DECK_DRAG, easeOut, easePin, PRESS_IN_MS, PRESS_OUT_MS, PRESS_SCALE, REDUCED_FADE_MS, RESUME_AFTER_MS, TOUCH } from './motion';
import { sampleScene, seg } from './script';
import { COLORS, DECK, DETAIL, FONT, FUTURA, HEADER, TABBAR } from './theme';
import { useReduceTransparency } from './use-reduce-transparency';
import { useResume } from './use-resume';

/** The pin's deck sits 1.4 pt left of the screen centre. */
const DECK_SHIFT = -1.4;
const LETTERS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const ME = require('./assets/coach-2.jpg');

/** Deck geometry for a card at distance u from the front. */
function deckPose(u: number) {
  'worklet';
  return {
    top: interpolate(u, DECK.u, DECK.top, 'clamp'),
    scale: interpolate(u, DECK.u, DECK.scale, 'clamp'),
    title: interpolate(u, DECK.u, DECK.title, 'clamp'),
    titleScale: interpolate(u, DECK.u, DECK.titleScale, 'clamp'),
    opacity: interpolate(u, DECK.u, DECK.opacity, 'clamp'),
  };
}

export function WorkoutStackList({ autoPlay = false, freezeAt }: { autoPlay?: boolean; freezeAt?: number }) {
  const { width } = useWindowDimensions();
  const reduced = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const scripted = autoPlay && !reduced && !frozen;
  const solid = useReduceTransparency();
  const [loaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
  });

  const deck = useSharedValue(0);
  const open = useSharedValue(0);
  const leave = useSharedValue(0);
  const scroll = useSharedValue(0);
  const month = useSharedValue(0);
  const day = useSharedValue(0);
  const cat = useSharedValue(CROSSFIT);
  const mode = useSharedValue(1);
  const loopStart = useSharedValue(-1);
  const start = useSharedValue(0);

  const applyScene = useCallback((time: number) => {
    'worklet';
    const scene = sampleScene(time, CROSSFIT);
    deck.set(scene.deck);
    open.set(scene.open);
    leave.set(scene.leave);
    scroll.set(scene.scroll);
    month.set(scene.month);
    day.set(scene.day);
    cat.set(scene.cat);
  }, [cat, day, deck, leave, month, open, scroll]);
  const frameStep = useCallback((info: FrameInfo) => {
    'worklet';
    if (mode.get() !== 0) return;
    if (loopStart.get() < 0) loopStart.set(info.timestamp);
    applyScene(info.timestamp - loopStart.get());
  }, [applyScene, loopStart, mode]);
  const frameDriver = useFrameCallback(frameStep, false);
  useEffect(() => {
    frameDriver.setActive(false);
    mode.set(1);
    loopStart.set(-1);
    if (frozen) {
      applyScene(freezeAt);
      return;
    }
    if (reduced) {
      for (const value of [deck, open, leave, scroll, month, day]) cancelAnimation(value);
      applyScene(0);
      return;
    }
    if (scripted) {
      mode.set(0);
      frameDriver.setActive(true);
    }
    return () => frameDriver.setActive(false);
  }, [applyScene, day, deck, frameDriver, freezeAt, frozen, leave, loopStart, mode, month, open, reduced, scripted, scroll]);

  const [catIndex, setCatIndex] = useState(CROSSFIT);
  const [inDetail, setInDetail] = useState(false);
  useAnimatedReaction(
    () => cat.get(),
    (v, was) => {
      if (v !== was) scheduleOnRN(setCatIndex, v);
    },
  );
  useAnimatedReaction(
    () => open.get() > 0.5 && leave.get() < 0.5,
    (on, was) => {
      if (on !== was) scheduleOnRN(setInDetail, on);
    },
  );

  const toRest = useCallback(() => {
    const ms = TOUCH.backMs;
    leave.set(
      withTiming(1, { duration: open.get() > 0 ? ms : 1, easing: easePin }, (done) => {
        if (!done) return;
        open.set(0);
        leave.set(0);
        scroll.set(0);
        month.set(0);
        day.set(0);
        loopStart.set(-1);
        mode.set(0);
      }),
    );
    deck.set(withTiming(0, { duration: ms, easing: easePin }));
  }, [day, deck, leave, loopStart, mode, month, open, scroll]);
  const { hold, release } = useResume(scripted, RESUME_AFTER_MS, toRest);
  const grab = useCallback(() => {
    mode.set(1);
    hold();
  }, [hold, mode]);
  const light = useCallback(() => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  }, []);

  // ---- touches -----------------------------------------------------------------------------------------------
  const deckPan = Gesture.Pan()
    .maxPointers(1)
    .activeOffsetY([-10, 10])
    .onBegin(() => {
      scheduleOnRN(grab);
      cancelAnimation(deck);
      start.set(deck.get());
    })
    .onUpdate((e) => {
      const max = CATEGORIES.length / 2;
      let d = start.get() - e.translationY / DECK_DRAG;
      if (d < 0) d *= 0.3;
      if (d > max) d = max + (d - max) * 0.3;
      deck.set(d);
    })
    .onEnd((e) => {
      const max = CATEGORIES.length / 2;
      const projected = deck.get() - (e.velocityY * 0.2) / DECK_DRAG;
      const target = Math.max(0, Math.min(max, Math.round(projected)));
      deck.set(reduced ? target : withSpring(target, { ...TOUCH.scrollSnap, velocity: -e.velocityY / DECK_DRAG }));
      if (target !== Math.round(start.get())) scheduleOnRN(light);
    })
    .onFinalize(() => scheduleOnRN(release));

  const openCategory = (i: number) => {
    grab();
    cat.set(i);
    scroll.set(0);
    month.set(0);
    day.set(0);
    leave.set(0);
    open.set(withTiming(1, { duration: reduced ? REDUCED_FADE_MS : TOUCH.openMs, easing: easePin }));
    light();
    release();
  };
  const back = () => {
    grab();
    leave.set(
      withTiming(1, { duration: reduced ? REDUCED_FADE_MS : TOUCH.backMs, easing: easePin }, (done) => {
        if (!done) return;
        open.set(0);
        leave.set(0);
        scroll.set(0);
        month.set(0);
        day.set(0);
      }),
    );
    release();
  };
  const detailPan = Gesture.Pan()
    .maxPointers(1)
    .activeOffsetY([-10, 10])
    .onBegin(() => {
      scheduleOnRN(grab);
      cancelAnimation(scroll);
      cancelAnimation(month);
      start.set(scroll.get());
    })
    .onUpdate((e) => {
      if (month.get() > 0.5) return;
      let s = start.get() - e.translationY / DETAIL.week.collapsedShift;
      if (s < 0) s *= 0.3;
      if (s > 1) s = 1 + (s - 1) * 0.3;
      scroll.set(s);
    })
    .onEnd((e) => {
      if (month.get() > 0.5) {
        // a pull up on the month closes it
        if (e.translationY < -30 || e.velocityY < -500) month.set(reduced ? 0 : withSpring(0, TOUCH.scrollSnap));
        return;
      }
      if (scroll.get() >= 0.98 && (e.translationY > 30 || e.velocityY > 500) && start.get() >= 0.98) {
        // pulling down at the top of the list opens the month
        scroll.set(reduced ? 1 : withSpring(1, TOUCH.scrollSnap));
        month.set(reduced ? 1 : withSpring(1, { ...TOUCH.scrollSnap, velocity: e.velocityY / 400 }));
        scheduleOnRN(light);
        return;
      }
      const projected = scroll.get() - (e.velocityY * 0.2) / DETAIL.week.collapsedShift;
      const target = projected > 0.5 ? 1 : 0;
      scroll.set(reduced ? target : withSpring(target, { ...TOUCH.scrollSnap, velocity: -e.velocityY / DETAIL.week.collapsedShift }));
    })
    .onFinalize(() => scheduleOnRN(release));
  const pickDay = (d: number) => {
    grab();
    day.set(withTiming(d === 9 ? 1 : 0, { duration: reduced ? REDUCED_FADE_MS : TOUCH.dayMs, easing: easePin }));
    Haptics.selectionAsync().catch(() => {});
    release();
  };

  // ---- styles ------------------------------------------------------------------------------------------------
  const listStyle = useAnimatedStyle(() => {
    const l = leave.get();
    if (l > 0) {
      return { opacity: l, transform: [{ scale: reduced ? 1 : interpolate(l, [0, 1], [BACK_ZOOM, 1]) }] };
    }
    return { opacity: 1 - seg(open.get(), [0.25, 0.75]), transform: [{ scale: 1 }] };
  });
  const detailStyle = useAnimatedStyle(() => ({ opacity: open.get() > 0 ? 1 - leave.get() : 0 }));

  if (!loaded) return <View style={styles.screen} />;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <Animated.View style={[StyleSheet.absoluteFill, listStyle]} pointerEvents={inDetail ? 'none' : 'auto'}>
        <GestureDetector gesture={deckPan}>
          <View style={StyleSheet.absoluteFill}>
            {CATEGORIES.map((c, i) => (
              <DeckCard key={c.name} index={i} deck={deck} open={open} cat={cat} width={width} onPress={() => openCategory(i)} />
            ))}
          </View>
        </GestureDetector>
        <Image source={ME} style={styles.me} accessibilityLabel="Your profile" />
        <Bell style={styles.bell} />
        <TabBar solid={solid} />
      </Animated.View>

      <Animated.View style={[StyleSheet.absoluteFill, detailStyle]} pointerEvents={inDetail ? 'auto' : 'none'}>
        <GestureDetector gesture={detailPan}>
          <View style={StyleSheet.absoluteFill}>
            <Detail open={open} scroll={scroll} month={month} day={day} catIndex={catIndex} reduced={reduced} width={width} onDay={pickDay} />
          </View>
        </GestureDetector>
        <DetailHeader month={month} open={open} onBack={back} />
      </Animated.View>
    </View>
  );
}

function DeckCard({
  index,
  deck,
  open,
  cat,
  width,
  onPress,
}: {
  index: number;
  deck: SharedValue<number>;
  open: SharedValue<number>;
  cat: SharedValue<number>;
  width: number;
  onPress: () => void;
}) {
  const c = CATEGORIES[index];
  const press = useSharedValue(1);
  const style = useAnimatedStyle(() => {
    const u = index - deck.get();
    const p = deckPose(u);
    const hidden = open.get() > 0 && cat.get() === index;
    return {
      opacity: hidden ? 0 : p.opacity,
      zIndex: Math.round(100 - u * 10),
      transform: [{ translateY: p.top }, { scale: p.scale * press.get() }],
    };
  });
  // cards further back sit in the shadow of the ones in front, as on the pin
  const fogStyle = useAnimatedStyle(() => ({ opacity: interpolate(index - deck.get(), DECK.u, DECK.fog, 'clamp') }));
  const titleStyle = useAnimatedStyle(() => {
    const p = deckPose(index - deck.get());
    return {
      transform: [
        { translateY: p.title / p.scale - DECK.titleAscent * DECK.titleSize * p.titleScale },
        { scale: p.titleScale },
      ],
    };
  });
  return (
    <Animated.View style={[styles.deckCard, { left: (width - DECK.w) / 2 + DECK_SHIFT }, style]}>
      <Pressable
        style={StyleSheet.absoluteFill}
        onPress={onPress}
        onPressIn={() => press.set(withTiming(PRESS_SCALE, { duration: PRESS_IN_MS, easing: easeOut }))}
        onPressOut={() => press.set(withTiming(1, { duration: PRESS_OUT_MS, easing: easeOut }))}
        accessibilityRole="button"
        accessibilityLabel={c.name}
      >
        <View style={[styles.deckFace, { backgroundColor: c.color }]}>
          <Image source={c.photo} style={styles.deckPhoto} contentFit="cover" />
          <Animated.View style={[StyleSheet.absoluteFill, styles.shade, fogStyle]} />
          <Animated.Text style={[styles.deckTitle, titleStyle]}>{c.name}</Animated.Text>
        </View>
      </Pressable>
    </Animated.View>
  );
}

/** The pin's floating tab bar: light frosted glass over the front card, dark filled icons. */
function TabBar({ solid }: { solid: boolean }) {
  const T = TABBAR;
  const at = (cx: number, size: number) => ({ position: 'absolute' as const, left: cx - T.left - size / 2, top: T.iconY - T.top - size / 2 });
  return (
    <View style={styles.tabbar}>
      {solid ? <View style={[StyleSheet.absoluteFill, styles.tabSolid]} /> : <BlurView intensity={T.blur} tint="light" style={StyleSheet.absoluteFill} />}
      <View style={[StyleSheet.absoluteFill, styles.tabWash]} />
      <Svg style={at(T.x[0], T.icon)} width={T.icon} height={T.icon} viewBox="0 0 24 24" accessibilityLabel="Home">
        <Path d="M12 2.6 1.6 11.2h3v10.2h5.6v-6.2h3.6v6.2h5.6V11.2h3z" fill={T.ink} />
      </Svg>
      <View style={[styles.tabDot, { left: T.x[0] - T.left - 2.4, top: T.dotY - T.top - 2.4 }]} />
      <Svg style={at(T.x[1], T.icon)} width={T.icon} height={T.icon} viewBox="0 0 24 24" accessibilityLabel="Schedule">
        <Path
          d="M7 1.8v2.4M17 1.8v2.4M4.6 3.8h14.8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4.6a2 2 0 0 1-2-2v-14a2 2 0 0 1 2-2z"
          stroke={T.ink}
          strokeWidth={2.2}
          fill="none"
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        <Rect x={2.6} y={4} width={18.8} height={4.6} fill={T.ink} />
        <Rect x={6} y={11.6} width={12} height={2.4} rx={1} fill={T.ink} />
        <Rect x={6} y={15.8} width={8} height={2.4} rx={1} fill={T.ink} />
      </Svg>
      <View style={[styles.plus, { left: T.x[2] - T.left - T.plus / 2, top: T.iconY - T.top - T.plus / 2 }]}>
        <Svg width={T.plusIcon} height={T.plusIcon} viewBox="0 0 18 18">
          <Path d="M9 1.5v15M1.5 9h15" stroke={T.plusInk} strokeWidth={2.6} strokeLinecap="round" />
        </Svg>
      </View>
      <Svg style={at(T.x[3], T.icon)} width={T.icon} height={T.icon} viewBox="0 0 24 24" accessibilityLabel="Workouts">
        <Path d="M14.6 1.4 5.2 13.4h6.2l-2 9.2 9.4-12.2h-6.2z" fill={T.ink} />
      </Svg>
      <Svg style={at(T.x[4], T.icon + 2)} width={T.icon + 2} height={T.icon + 2} viewBox="0 0 26 26" accessibilityLabel="Community">
        <Path d="M13 13 5 5M13 13l8-8M13 13l-9 3M13 13l5 8" stroke={T.ink} strokeWidth={2.2} strokeLinecap="round" />
        <Circle cx={13} cy={13} r={4.6} fill={T.ink} />
        <Circle cx={13} cy={13} r={1.8} fill={T.wash} />
        <Circle cx={4.6} cy={4.6} r={2.8} fill={T.ink} />
        <Circle cx={21.4} cy={4.6} r={2.8} fill={T.ink} />
        <Circle cx={3.2} cy={16.4} r={2.4} fill={T.ink} />
        <Circle cx={18.6} cy={21.6} r={2.8} fill={T.ink} />
      </Svg>
    </View>
  );
}

function Bell({ style }: { style: object }) {
  return (
    <View style={style} accessibilityLabel="Notifications">
      <Svg width={HEADER.icon} height={HEADER.icon} viewBox="0 0 24 24">
        <Path d="M12 3a6 6 0 0 0-6 6v4l-2 3h16l-2-3V9a6 6 0 0 0-6-6zM9.5 19a2.5 2.5 0 0 0 5 0z" fill={COLORS.white} />
        <Circle cx={18} cy={5} r={3.4} fill={COLORS.red} />
      </Svg>
    </View>
  );
}

function DetailHeader({ month, open, onBack }: { month: SharedValue<number>; open: SharedValue<number>; onBack: () => void }) {
  const titleStyle = useAnimatedStyle(() => ({ opacity: month.get() }));
  const iconsStyle = useAnimatedStyle(() => ({ opacity: seg(open.get(), [0.5, 1]) }));
  const press = useSharedValue(1);
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: press.get() }] }));
  return (
    <Animated.View style={[styles.header, iconsStyle]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Back"
        hitSlop={12}
        onPressIn={() => press.set(withTiming(PRESS_SCALE, { duration: PRESS_IN_MS, easing: easeOut }))}
        onPressOut={() => press.set(withTiming(1, { duration: PRESS_OUT_MS, easing: easeOut }))}
        onPress={onBack}
      >
        <Animated.View style={pressStyle}>
          <Svg width={HEADER.icon} height={HEADER.icon} viewBox="0 0 24 24">
            <Path d="M15 5 8 12l7 7" stroke={COLORS.white} strokeWidth={2.4} fill="none" strokeLinecap="round" strokeLinejoin="round" />
          </Svg>
        </Animated.View>
      </Pressable>
      <Animated.Text style={[styles.month, titleStyle]}>April</Animated.Text>
      <Bell style={{}} />
    </Animated.View>
  );
}

function Detail({
  open,
  scroll,
  month,
  day,
  catIndex,
  reduced,
  width,
  onDay,
}: {
  open: SharedValue<number>;
  scroll: SharedValue<number>;
  month: SharedValue<number>;
  day: SharedValue<number>;
  catIndex: number;
  reduced: boolean;
  width: number;
  onDay: (d: number) => void;
}) {
  const c = CATEGORIES[catIndex];
  const shift = DETAIL.week.collapsedShift;
  const fromCard = deckPose(2);
  const cardW = DECK.w * fromCard.scale;
  const cardH = DECK.h * fromCard.scale;

  // The tapped card grows into the hero; its photo cross-fades to the portrait.
  const hero = useAnimatedStyle(() => {
    const o = reduced ? 1 : open.get();
    const s = scroll.get();
    return {
      left: interpolate(o, [0, 1], [(width - cardW) / 2, 0]),
      top: interpolate(o, [0, 1], [fromCard.top, 0]) - DETAIL.hero.scrollAway * s,
      width: interpolate(o, [0, 1], [cardW, width]),
      height: interpolate(o, [0, 1], [cardH, DETAIL.hero.h]),
      borderRadius: interpolate(o, [0, 1], [DECK.radius * fromCard.scale, 0]),
      opacity: reduced ? open.get() : 1,
    };
  });
  const heroPhoto = useAnimatedStyle(() => ({ opacity: seg(open.get(), [0.2, 0.8]) }));
  const title = useAnimatedStyle(() => {
    const o = reduced ? 1 : open.get();
    return {
      opacity: reduced ? open.get() : 1,
      transform: [
        { translateX: interpolate(o, [0, 1], [width / 2 - 80, 0]) },
        { translateY: interpolate(o, [0, 1], [fromCard.top + 84.3 - DETAIL.title.inkTop, 0]) - DETAIL.hero.scrollAway * scroll.get() },
        { scale: interpolate(o, [0, 1], [(DECK.titleSize * fromCard.scale) / DETAIL.title.size, 1]) },
      ],
    };
  });
  const content = useAnimatedStyle(() => {
    const v = seg(open.get(), [0.45, 1]);
    return {
      opacity: v,
      transform: [{ translateY: shift * (1 - scroll.get()) + (reduced ? 0 : 12 * (1 - v)) }],
    };
  });
  // the class list scrolls further than the calendar: the live card stays and the others slide up over it
  const list = useAnimatedStyle(() => {
    const v = seg(open.get(), [0.45, 1]);
    return { opacity: v, transform: [{ translateY: reduced ? 0 : 12 * (1 - v) }] };
  });

  return (
    <View style={StyleSheet.absoluteFill}>
      <Animated.View style={[styles.hero, hero]}>
        <Image source={c.photo} style={StyleSheet.absoluteFill} contentFit="cover" />
        <Animated.View style={[StyleSheet.absoluteFill, heroPhoto]}>
          <Image source={catIndex === CROSSFIT ? HERO : c.photo} style={StyleSheet.absoluteFill} contentFit="cover" />
          <LinearGradient colors={['rgba(12,18,23,0)', 'rgba(12,18,23,0.85)']} locations={[0.55, 1]} style={StyleSheet.absoluteFill} />
        </Animated.View>
      </Animated.View>
      <Animated.Text style={[styles.detailTitle, title]}>{c.name}</Animated.Text>

      <Animated.View style={[StyleSheet.absoluteFill, content]}>
        <Calendar month={month} day={day} onDay={onDay} />
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, list]} pointerEvents="none">
        <Cards month={month} day={day} scroll={scroll} width={width} />
      </Animated.View>
    </View>
  );
}

function Calendar({ month, day, onDay }: { month: SharedValue<number>; day: SharedValue<number>; onDay: (d: number) => void }) {
  const cell = (402 - DETAIL.week.side * 2) / 7;
  const sel = useAnimatedStyle(() => ({ transform: [{ translateX: cell * day.get() }] }));
  const rows = useAnimatedStyle(() => ({ opacity: month.get(), transform: [{ translateY: -12 * (1 - month.get()) }] }));
  const grabber = useAnimatedStyle(() => ({ transform: [{ translateY: DETAIL.monthRow * 3.3 * month.get() }] }));
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
      {LETTERS.map((l, i) => (
        <Text key={i} style={[styles.letter, { left: DETAIL.week.side + cell * i, width: cell, top: DETAIL.week.letters - 10 }, i === 2 && styles.letterOn]}>
          {l}
        </Text>
      ))}
      <Animated.View style={[styles.selected, { left: DETAIL.week.side + cell * 2 + (cell - DETAIL.week.selected) / 2 }, sel]} />
      {WEEKS.map((w, r) => {
        const row = (
          <View key={r} style={[styles.weekRow, { top: DETAIL.week.numbers - 12 + DETAIL.monthRow * r }]} pointerEvents="box-none">
            {w.map((d, i) =>
              d === 0 ? (
                <View key={i} style={{ width: cell }} />
              ) : (
                <Pressable key={i} style={[styles.dayCell, { width: cell }]} onPress={() => onDay(d)} accessibilityRole="button" accessibilityLabel={`April ${d}`}>
                  <Text style={[styles.dayText, DIM_DAYS.includes(d) && styles.dayDim]}>{d}</Text>
                  <View style={styles.dots}>
                    {(DOTS[d] ?? '').split('').map((k, j) => (
                      <View key={j} style={[styles.dot, { backgroundColor: k === 'g' ? COLORS.dotGreen : COLORS.dotRed }]} />
                    ))}
                  </View>
                </Pressable>
              ),
            )}
          </View>
        );
        return r === 0 ? row : (
          <Animated.View key={r} style={[StyleSheet.absoluteFill, rows]} pointerEvents="box-none">
            {row}
          </Animated.View>
        );
      })}
      <Animated.View style={[styles.grabber, grabber]} />
    </View>
  );
}

function Cards({ month, day, scroll, width }: { month: SharedValue<number>; day: SharedValue<number>; scroll: SharedValue<number>; width: number }) {
  const label = useAnimatedStyle(() => ({
    opacity: 1 - month.get(),
    transform: [{ translateY: DETAIL.listScroll * (1 - scroll.get()) }],
  }));
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <Animated.Text style={[styles.section, label]}>Live Class Schedule</Animated.Text>
      {[DAY_8, DAY_9].map((set, s) =>
        set.map((card, i) => <PlacedCard key={card.key} index={i} set={s} month={month} day={day} scroll={scroll} width={width} card={card} />),
      )}
    </View>
  );
}

const LIST_TOPS = [DETAIL.list.liveTop, DETAIL.list.quizTop, DETAIL.list.mondayTop, DETAIL.list.morningTop];
const HEIGHTS = [DETAIL.cards.liveH, DETAIL.cards.quizH, DETAIL.cards.mondayH, DETAIL.cards.morningH];

function PlacedCard({
  index,
  set,
  month,
  day,
  scroll,
  width,
  card,
}: {
  index: number;
  set: number;
  month: SharedValue<number>;
  day: SharedValue<number>;
  scroll: SharedValue<number>;
  width: number;
  card: (typeof DAY_8)[number];
}) {
  const style = useAnimatedStyle(() => {
    const m = month.get();
    const sc = scroll.get();
    // before the scroll every card sits in a plain list; scrolled, the live card stays tucked behind the next one
    const listScale = index === 0 ? interpolate(sc, [0, 1], [1, DETAIL.list.liveScale]) : 1;
    const listTop = index === 0 ? interpolate(sc, [0, 1], [DETAIL.list.liveRest, LIST_TOPS[0]]) : LIST_TOPS[index] + DETAIL.listScroll * (1 - sc);
    const top = interpolate(m, [0, 1], [listTop, DETAIL.stack.tops[index]]);
    const scale = interpolate(m, [0, 1], [listScale, DETAIL.stack.scales[index]]);
    // day 9's cards arrive from the right while day 8's leave to the left
    const d = day.get();
    const x = set === 0 ? -width * d : width * (1 - d);
    const visible = set === 0 ? d < 1 : d > 0;
    return {
      opacity: visible ? 1 : 0,
      zIndex: index,
      transform: [{ translateX: x }, { translateY: top + (HEIGHTS[index] * (scale - 1)) / 2 }, { scale }],
    };
  });
  return (
    <Animated.View style={[styles.placed, style]}>
      <ClassCardView card={card} height={HEIGHTS[index]} />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: COLORS.bg, overflow: 'hidden' },
  me: { position: 'absolute', top: HEADER.top, left: HEADER.side, width: HEADER.avatar, height: HEADER.avatar, borderRadius: HEADER.avatar / 2 },
  bell: { position: 'absolute', top: HEADER.top, right: HEADER.side },
  deckCard: { position: 'absolute', top: 0, width: DECK.w, height: DECK.h, transformOrigin: 'top center' },
  shade: { backgroundColor: '#000000' },
  deckPhoto: {
    position: 'absolute',
    top: DECK.photo.top,
    left: (DECK.w - DECK.w * DECK.photo.zoom) / 2,
    width: DECK.w * DECK.photo.zoom,
    height: ((DECK.w * DECK.photo.zoom) * 990) / 1173,
  },
  deckFace: { flex: 1, borderRadius: DECK.radius, borderCurve: 'continuous', overflow: 'hidden', alignItems: 'center' },
  rim: { position: 'absolute', top: 0, left: 0, right: 0, height: 20 },
  deckTitle: { position: 'absolute', top: 0, fontFamily: FUTURA, fontSize: DECK.titleSize, color: COLORS.white, transformOrigin: 'top center' },
  tabbar: {
    position: 'absolute',
    top: TABBAR.top,
    left: TABBAR.left,
    width: TABBAR.w,
    height: TABBAR.h,
    borderRadius: TABBAR.radius,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  tabWash: { backgroundColor: TABBAR.wash },
  tabDot: { position: 'absolute', width: 4.8, height: 4.8, borderRadius: 2.4, backgroundColor: TABBAR.ink },
  tabSolid: { backgroundColor: TABBAR.solid },
  plus: {
    position: 'absolute',
    width: TABBAR.plus,
    height: TABBAR.plus,
    borderRadius: TABBAR.plus / 2,
    backgroundColor: COLORS.white,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOpacity: 0.16,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
  },
  header: { position: 'absolute', top: HEADER.top, left: HEADER.side, right: HEADER.side, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  month: { fontFamily: FONT.semibold, fontSize: 20, color: COLORS.white },
  hero: { position: 'absolute', overflow: 'hidden', backgroundColor: COLORS.bg },
  detailTitle: {
    position: 'absolute',
    top: DETAIL.title.inkTop - DECK.titleAscent * DETAIL.title.size,
    left: DETAIL.title.left,
    fontFamily: FUTURA,
    fontSize: DETAIL.title.size,
    letterSpacing: DETAIL.title.tracking,
    color: COLORS.white,
    transformOrigin: 'left top',
  },
  letter: { position: 'absolute', textAlign: 'center', fontFamily: FONT.medium, fontSize: DETAIL.week.letterSize, color: COLORS.dim },
  letterOn: { color: COLORS.white },
  selected: {
    position: 'absolute',
    top: DETAIL.week.numbers - DETAIL.week.selected / 2,
    width: DETAIL.week.selected,
    height: DETAIL.week.selected,
    borderRadius: DETAIL.week.radius,
    backgroundColor: COLORS.red,
  },
  weekRow: { position: 'absolute', left: DETAIL.week.side, flexDirection: 'row' },
  dayCell: { height: 34, alignItems: 'center', justifyContent: 'flex-start', paddingTop: 2 },
  dayText: { fontFamily: FONT.semibold, fontSize: DETAIL.week.numberSize, color: COLORS.white, fontVariant: ['tabular-nums'] },
  dayDim: { color: COLORS.dim },
  dots: { flexDirection: 'row', gap: 3, marginTop: 1 },
  dot: { width: 4, height: 4, borderRadius: 2 },
  grabber: { position: 'absolute', top: DETAIL.grabber.top, alignSelf: 'center', width: DETAIL.grabber.w, height: 3, borderRadius: 1.5, backgroundColor: COLORS.grabber },
  section: { position: 'absolute', top: DETAIL.list.labelTop, left: DETAIL.list.side, fontFamily: FONT.semibold, fontSize: 15, color: COLORS.label },
  placed: { position: 'absolute', top: 0, left: DETAIL.list.side },
});
