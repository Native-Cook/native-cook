import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Pressable, type ScrollView, StyleSheet, useWindowDimensions, View } from 'react-native';
import Animated, {
  cancelAnimation, Easing, ReduceMotion, scrollTo, useAnimatedReaction, useAnimatedRef, useAnimatedStyle,
  useDerivedValue, useReducedMotion, useSharedValue, withRepeat, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { PROFILES, SECTIONS } from './content';
import { Hero, SectionRow } from './FeedParts';
import { BackIcon, DownloadIcon, GearIcon, HomeIcon, SearchIcon, SeatIcon, UserIcon } from './icons';
import { feedLayout } from './layout';
import {
  BACK_PRESS, DISSOLVE, DISSOLVE_EASE, END_LABEL_Y, FLICK, HOME_EASE, IDLE_RESUME_MS, LOOP_MS,
  MANUAL_DISSOLVE_MS, PICK, REDUCED_FADE_MS, SCROLL_STEPS,
} from './motion';
import { ProfilePicker } from './ProfilePicker';
import { COLORS, FONT, HOME } from './theme';

const FONTS = {
  [FONT.regular]: require('./assets/fonts/Poppins-Regular.ttf'),
  [FONT.medium]: require('./assets/fonts/Poppins-Medium.ttf'),
  [FONT.semibold]: require('./assets/fonts/Poppins-SemiBold.ttf'),
  [FONT.display]: require('./assets/fonts/Anton-Regular.ttf'),
  [FONT.bebas]: require('./assets/fonts/BebasNeue-Regular.ttf'),
  [FONT.dmserif]: require('./assets/fonts/DMSerifDisplay-Regular.ttf'),
  [FONT.archivo]: require('./assets/fonts/ArchivoBlack-Regular.ttf'),
  [FONT.marcellus]: require('./assets/fonts/Marcellus-Regular.ttf'),
  [FONT.abril]: require('./assets/fonts/AbrilFatface-Regular.ttf'),
  [FONT.italiana]: require('./assets/fonts/Italiana-Regular.ttf'),
};

const NEVER = ReduceMotion.Never;
export type PosterGlowCarouselTabIdentity = 'home' | 'search' | 'cinema' | 'downloads' | 'profile';

const TABS = [
  { id: 'home', label: 'Home', Icon: HomeIcon },
  { id: 'search', label: 'Search', Icon: SearchIcon },
  { id: 'cinema', label: 'Cinema', Icon: SeatIcon },
  { id: 'downloads', label: 'Downloads', Icon: DownloadIcon },
  { id: 'profile', label: 'Profile', Icon: UserIcon },
] satisfies { id: PosterGlowCarouselTabIdentity; label: string; Icon: typeof HomeIcon }[];

type Props = {
  autoPlay?: boolean;
  freezeAt?: number;
  onSettings?: () => void;
  onTabSelected?: (tab: PosterGlowCarouselTabIdentity) => void;
};

function ramp(t: number, at: number, ms: number) {
  'worklet';
  return Math.min(1, Math.max(0, (t - at) / ms));
}

/** Press curve of a scripted tap: in quickly, out a little slower. */
function tap(t: number, at: number, inMs: number, outMs: number) {
  'worklet';
  if (t < at || t > at + inMs + outMs) return 0;
  return t < at + inMs ? ramp(t, at, inMs) : 1 - ramp(t, at + inMs, outMs);
}

export function PosterGlowCarousel({ autoPlay = false, freezeAt, onSettings, onTabSelected }: Props) {
  const [fontsLoaded] = useFonts(FONTS);
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const layout = useMemo(() => feedLayout(SECTIONS), []);
  const tabHeight = HOME.tabBar + insets.bottom;
  // The feed ends where the pin's last frame does: the last label sits END_LABEL_Y from the top.
  const maxScroll = layout.labels[layout.labels.length - 1] - END_LABEL_Y;
  const contentHeight = maxScroll + height;

  const targets = useMemo(() => SCROLL_STEPS.map((step) => (step.to < 0 ? maxScroll : Math.min(maxScroll, step.to))), [maxScroll]);

  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const rowRefs = useRef<(ScrollView | null)[]>([]);
  const frozen = freezeAt !== undefined;
  const scripted = autoPlay && !reduced && !frozen && fontsLoaded;
  const clock = useSharedValue(frozen ? freezeAt : 0);
  const auto = useSharedValue(autoPlay || frozen ? 1 : 0);
  const manualPicker = useSharedValue(1);
  const fadeMs = reduced ? REDUCED_FADE_MS : DISSOLVE.ms;

  const pickerOpacity = useDerivedValue(() => {
    if (auto.get() === 0) return manualPicker.get();
    const t = clock.get();
    const out = DISSOLVE_EASE(ramp(t, DISSOLVE.inAt, fadeMs));
    const back = DISSOLVE_EASE(ramp(t, DISSOLVE.outAt, fadeMs));
    return 1 - out + back;
  });
  const autoPress = useDerivedValue(() => (auto.get() === 1 && !reduced ? tap(clock.get(), PICK.pressAt, PICK.pressIn, PICK.pressOut) : 0));
  const backPress = useDerivedValue(() => (auto.get() === 1 ? tap(clock.get(), BACK_PRESS.at, 80, BACK_PRESS.ms - 80) : 0));

  // The script drives the real ScrollView, so a touch takes over from exactly where it is.
  useAnimatedReaction(() => (auto.get() === 1 && !reduced ? clock.get() : -1), (t) => {
    if (t < 0) return;
    let y = 0;
    let from = 0;
    for (let i = 0; i < SCROLL_STEPS.length; i++) {
      const step = SCROLL_STEPS[i];
      if (t < step.at) break;
      const p = ramp(t, step.at, step.ms);
      const eased = step.ease === 'flick' ? FLICK(p) : HOME_EASE(p);
      y = from + (targets[i] - from) * eased;
      from = targets[i];
    }
    scrollTo(scrollRef, 0, y, false);
  }, [targets, reduced]);

  // Only the visible layer may take touches.
  const [pickerUp, setPickerUp] = useState(true);
  useAnimatedReaction(() => pickerOpacity.get() > 0.5, (up, prev) => {
    if (up !== prev) scheduleOnRN(setPickerUp, up);
  });

  const startClock = useCallback(() => {
    clock.set(0);
    clock.set(withRepeat(withTiming(LOOP_MS, { duration: LOOP_MS, easing: Easing.linear, reduceMotion: NEVER }), -1, false));
    auto.set(1);
  }, [auto, clock]);
  const scriptedRef = useRef(scripted);
  useLayoutEffect(() => {
    scriptedRef.current = scripted;
  }, [scripted]);
  const finishResume = useCallback(() => {
    if (scriptedRef.current) startClock();
  }, [startClock]);

  const idleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const resume = useCallback(() => {
    // Back to the first appearance under the picker, then the script restarts from 0.
    manualPicker.set(withTiming(1, { duration: MANUAL_DISSOLVE_MS, reduceMotion: NEVER }, (done) => {
      if (!done) return;
      scrollTo(scrollRef, 0, 0, false);
      scheduleOnRN(finishResume);
    }));
    rowRefs.current.forEach((row) => row?.scrollTo({ x: 0, animated: false }));
  }, [finishResume, manualPicker, scrollRef]);

  const takeOver = useCallback(() => {
    if (frozen) return;
    if (idleTimer.current) clearTimeout(idleTimer.current);
    idleTimer.current = null;
    if (auto.get() === 1) {
      cancelAnimation(clock);
      manualPicker.set(pickerOpacity.get() > 0.5 ? 1 : 0);
      auto.set(0);
    }
  }, [auto, clock, frozen, manualPicker, pickerOpacity]);

  const scheduleResume = useCallback(() => {
    // Scripted scrolls also end in momentum events; only a hand-driven session re-arms the timer.
    if (!scripted || auto.get() === 1) return;
    if (idleTimer.current) clearTimeout(idleTimer.current);
    idleTimer.current = setTimeout(resume, IDLE_RESUME_MS);
  }, [auto, resume, scripted]);

  useEffect(() => {
    if (!fontsLoaded) return;
    if (idleTimer.current) {
      clearTimeout(idleTimer.current);
      idleTimer.current = null;
    }
    if (frozen) {
      // Dev freeze: hold the script's frame at freezeAt (for pixel comparison with the pin).
      cancelAnimation(clock);
      auto.set(1);
      clock.set(freezeAt);
      return;
    }
    if (scripted) {
      startClock();
    } else {
      const wasAuto = auto.get() === 1;
      cancelAnimation(clock);
      auto.set(0);
      if (reduced && wasAuto) {
        manualPicker.set(1);
        scrollTo(scrollRef, 0, 0, false);
        rowRefs.current.forEach((row) => row?.scrollTo({ x: 0, animated: false }));
      }
    }
    return () => {
      cancelAnimation(clock);
      if (idleTimer.current) clearTimeout(idleTimer.current);
    };
  }, [auto, clock, fontsLoaded, freezeAt, frozen, manualPicker, reduced, scripted, scrollRef, startClock]);

  const pick = useCallback((index: number) => {
    if (index >= PROFILES.length) return; // New Profile has no screen in the pin
    if (auto.get() === 0) Haptics.selectionAsync().catch(() => {});
    manualPicker.set(withTiming(0, { duration: reduced ? REDUCED_FADE_MS : MANUAL_DISSOLVE_MS, reduceMotion: NEVER }));
  }, [auto, manualPicker, reduced]);
  const back = useCallback(() => {
    manualPicker.set(withTiming(1, { duration: reduced ? REDUCED_FADE_MS : MANUAL_DISSOLVE_MS, reduceMotion: NEVER }));
  }, [manualPicker, reduced]);

  const pickerStyle = useAnimatedStyle(() => ({ opacity: pickerOpacity.get() }));
  const backStyle = useAnimatedStyle(() => ({ opacity: 1 - 0.6 * backPress.get() }));

  if (!fontsLoaded) return <View style={[styles.root, { backgroundColor: COLORS.pickerTop }]} />;

  const chromeTop = insets.top + HOME.chromeY - HOME.chromeSize / 2;
  return (
    <View style={styles.root} onTouchStart={takeOver} onTouchEnd={scheduleResume} onTouchCancel={scheduleResume}>
      <StatusBar style="light" hidden={false} />
      <Animated.ScrollView ref={scrollRef} showsVerticalScrollIndicator={false} scrollEventThrottle={16}
        accessibilityElementsHidden={pickerUp}
        importantForAccessibility={pickerUp ? 'no-hide-descendants' : 'auto'}
        onScrollEndDrag={scheduleResume} onMomentumScrollEnd={scheduleResume}
        contentContainerStyle={{ minHeight: contentHeight }}>
        <Hero width={width} />
        {SECTIONS.map((section, i) => (
          <SectionRow key={section.label} section={section} first={i === 0} ref={(r) => { rowRefs.current[i] = r; }} />
        ))}
      </Animated.ScrollView>

      {/* Fixed chrome: the pin keeps these over the feed while it scrolls. */}
      <Animated.View style={[styles.chrome, { top: chromeTop, left: HOME.chromeX - 22 }, backStyle]}
        accessibilityElementsHidden={pickerUp} importantForAccessibility={pickerUp ? 'no-hide-descendants' : 'auto'}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back to profiles" onPress={back} style={styles.hit}>
          <BackIcon size={HOME.chromeSize} color={COLORS.ink} />
        </Pressable>
      </Animated.View>
      <View style={[styles.chrome, { top: chromeTop, left: width - HOME.chromeX - 22 }]}
        accessibilityElementsHidden={pickerUp} importantForAccessibility={pickerUp ? 'no-hide-descendants' : 'auto'}>
        <Pressable accessibilityRole="button" accessibilityLabel="Settings" style={styles.hit}
          disabled={!onSettings} accessibilityState={{ disabled: !onSettings }} onPress={onSettings}>
          <GearIcon size={HOME.chromeSize - 2} color={COLORS.ink} />
        </Pressable>
      </View>

      <View style={[styles.tabBar, { height: tabHeight }]} accessibilityRole="tablist"
        accessibilityElementsHidden={pickerUp} importantForAccessibility={pickerUp ? 'no-hide-descendants' : 'auto'}>
        {TABS.map(({ id, label, Icon }, i) => (
          <Pressable key={id} accessibilityRole="tab" accessibilityLabel={label}
            accessibilityState={{ selected: i === 0, disabled: !onTabSelected }} disabled={!onTabSelected}
            onPress={() => onTabSelected?.(id)}
            style={[styles.tab, { left: (HOME.tabX[i] / 402) * width - 22, top: HOME.tabIconY - 22 }]} hitSlop={2}>
            <Icon size={HOME.tabIcon[i]} color={COLORS.ink} />
          </Pressable>
        ))}
      </View>

      <Animated.View style={[StyleSheet.absoluteFill, pickerStyle]} pointerEvents={pickerUp ? 'auto' : 'none'}
        accessibilityElementsHidden={!pickerUp} importantForAccessibility={!pickerUp ? 'no-hide-descendants' : 'auto'}>
        <ProfilePicker width={width} height={height} autoPress={autoPress} onPick={pick} />
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.bg },
  chrome: { position: 'absolute', width: 44, height: 44, marginTop: -12 },
  hit: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  tabBar: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: COLORS.tabBar },
  tab: { position: 'absolute', width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
});
