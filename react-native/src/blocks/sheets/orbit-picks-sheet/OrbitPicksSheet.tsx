import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { Badge, INDIGO } from './Badge';
import { DISCS, Disc, Ring } from './Orbit';
import { PageBehind } from './PageBehind';
import { type Profile, ProfileCard } from './Profiles';
import { type Step, useAutoPlay } from './use-auto-play';
import {
  BADGE_IN,
  BADGE_POP,
  BADGE_UP,
  BADGE_UP_FOR,
  CAPTION_IN,
  CAPTION_OUT,
  END,
  RING_IN,
  RING_OUT,
  RESULT_FOR,
  RESULT_IN,
  easeOut,
  lerp,
  pop,
  ramp,
} from './motion';

type Props = {
  /** The people the sheet goes through; swipe the card sideways for the next one. */
  profiles?: Profile[];
  /** Plays the whole interaction by itself, over and over, and gives way to a touch. */
  autoPlay?: boolean;
  title?: string;
  banner?: string;
  bannerCaption?: string;
  loadingLabel?: string;
  resultTitle?: string;
  resultCaption?: string;
  chatLabel?: string;
  onChat?: (profile: Profile) => void;
  /** Called with the place of the profile on show whenever a swipe settles. */
  onProfileChange?: (index: number) => void;
};

const portrait = (id: string) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=240&h=240&q=80`;

const PROFILES: Profile[] = [
  {
    name: 'Alex',
    rating: '5.0',
    details: '32 · he/him',
    likes: 'Rugby',
    match: 80,
    skin: '#f1c7a3',
    hair: '#6a3f26',
    shirt: '#2f5fb8',
    photo: portrait('1506794778202-cad84cf45f1d'),
    tags: ['Coffee', 'Hiking', 'Jazz'],
  },
  {
    name: 'Taylor',
    rating: '5.0',
    details: '29 · they/them',
    likes: 'Cycling',
    match: 76,
    skin: '#e8b48a',
    hair: '#1c1c24',
    shirt: '#7a2430',
    glasses: true,
    photo: portrait('1531891437562-4301cf35b7e4'),
    tags: ['Film', 'Vinyl', 'Ramen'],
  },
  {
    name: 'Morgan',
    rating: '4.9',
    details: '27 · she/her',
    likes: 'Climbing',
    match: 71,
    skin: '#c98f66',
    hair: '#3a2118',
    shirt: '#2b8a6e',
    photo: portrait('1487412720507-e7ab37603c6f'),
    tags: ['Snow', 'Dogs', 'Pop'],
  },
];

/** The autoPlay loop, in seconds from its start. */
export const LOOP = 9.5;
const TAP_AT = 0.8;
const TAP_FOR = 0.15;
const SWIPE_AT = 7;
const CLOSE_AT = 8.1;
const REST_AT = 8.7;
// Seconds a touch keeps the loop away.
const USER_HOLD = 3;

const SCRIM = 0.55;
const EDGE = '#8f7bef';
const SHEET_RADIUS = 28;
// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
const OVERSHOOT = 40;
const LOADER_Y = 290;
const BADGE_SIZE = 72;
const BADGE_SMALL = 40;
const BADGE_TOP_Y = 48;
const HALO = 132;
const CAPTION_Y = LOADER_Y + 88;
const RESULT_TITLE_Y = 80;
const PAGER_TOP = 148;
const PAGER_HEIGHT = 432;
const DOTS_GAP = 6;
const FINGER = 32;
const PAGE_SCALE = 0.94;
const PAGE_RADIUS = 24;
const PRESS_SCALE = 0.97;
const DISMISS_DRAG = 120;
const DISMISS_VELOCITY = 900;

const RISE_SPRING = { damping: 18, stiffness: 170, mass: 1 };
const DROP_MS = 400;
const DROP_EASING = Easing.bezier(0.5, 0, 1, 1);
const SWIPE_MS = 480;
const FINGER_MS = 520;
const SNAP_MS = 250;
const FADE_MS = 200;

/**
 * A discover page with a full-height sheet over it. Tapping the banner raises the sheet while the
 * page recedes behind it; a badge pops in, a ring draws round it and a dozen icon discs spiral in
 * and orbit it, until they are pulled into the badge, which rises and shrinks to become the
 * heading of a card of people to swipe through. One clock, in seconds since the tap, drives all
 * of it, so the loader plays the same way every time.
 */
export function OrbitPicksSheet({
  profiles = PROFILES,
  autoPlay = false,
  title = 'Discover',
  banner = "Today's Top Picks",
  bannerCaption = 'Your personalized daily matches are ready to see!',
  loadingLabel = 'Doing some magic...',
  resultTitle = "Today's Top Picks",
  resultCaption = 'Three people picked for you today',
  chatLabel = 'Chat',
  onChat,
  onProfileChange,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const sheetHeight = height - insets.top;
  const centre = width / 2;

  // 0 hidden, 1 up; the clock in seconds since the tap; the card on show, as a place in the list.
  const sheet = useSharedValue(0);
  const drag = useSharedValue(0);
  const clock = useSharedValue(0);
  const page = useSharedValue(0);
  const pageFrom = useSharedValue(0);
  const finger = useSharedValue(0);
  const bannerScale = useSharedValue(1);

  const [isOpen, setIsOpen] = useState(false);
  const [touches, setTouches] = useState(0);
  const touched = () => setTouches((n) => n + 1);
  // Whether the sheet is up, for the timers, which would read a stale state.
  const up = useSharedValue(false);

  const open = () => {
    if (up.get()) return;
    up.set(true);
    setIsOpen(true);
    clock.set(0);
    if (reducedMotion) {
      sheet.set(withTiming(1, { duration: FADE_MS }));
      clock.set(END);
      return;
    }
    sheet.set(withSpring(1, RISE_SPRING));
    clock.set(withTiming(END, { duration: END * 1000, easing: Easing.linear }));
  };

  const close = () => {
    if (!up.get()) return;
    up.set(false);
    setIsOpen(false);
    sheet.set(
      withTiming(0, { duration: reducedMotion ? FADE_MS : DROP_MS, easing: DROP_EASING }, (finished) => {
        if (!finished) return;
        // Out of sight now: the next opening starts from the beginning.
        clock.set(0);
        page.set(0);
        drag.set(0);
      }),
    );
  };

  /** Everything back as it was at the start, with no animation. */
  const rest = () => {
    for (const value of [sheet, drag, clock, page, pageFrom, finger]) {
      cancelAnimation(value);
      value.set(0);
    }
    bannerScale.set(1);
    up.set(false);
    setIsOpen(false);
  };

  const tapBanner = (down: boolean) => bannerScale.set(withTiming(down ? PRESS_SCALE : 1, { duration: down ? 80 : 140 }));

  /** What a finger does by itself: drags the card to the next profile. */
  const swipe = () => {
    finger.set(0);
    if (!reducedMotion) finger.set(withTiming(1, { duration: FINGER_MS, easing: Easing.linear }));
    page.set(withTiming(1, { duration: reducedMotion ? FADE_MS : SWIPE_MS, easing: Easing.out(Easing.cubic) }));
  };

  const steps: Step[] = [
    { at: TAP_AT, run: () => tapBanner(true) },
    {
      at: TAP_AT + TAP_FOR,
      run: () => {
        tapBanner(false);
        open();
      },
    },
    { at: SWIPE_AT, run: swipe },
    { at: CLOSE_AT, run: close },
    { at: REST_AT, run: rest },
  ];
  useAutoPlay({ autoPlay, loop: LOOP, steps, rest, touches, hold: USER_HOLD });

  const settled = (index: number) => onProfileChange?.(index);
  const last = profiles.length - 1;

  const pager = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .onStart(() => {
      pageFrom.set(page.get());
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      page.set(Math.min(last, Math.max(0, pageFrom.get() - event.translationX / width)));
    })
    .onEnd((event) => {
      // A flick carries the card on to the next one; a slow drag settles on the nearer one.
      const target = Math.min(last, Math.max(0, Math.round(page.get() - event.velocityX / width / 2)));
      page.set(withTiming(target, { duration: SNAP_MS, easing: Easing.out(Easing.cubic) }));
      scheduleOnRN(settled, target);
    });

  const dismiss = Gesture.Pan()
    .activeOffsetY([-12, 12])
    .failOffsetX([-24, 24])
    .onStart(() => {
      scheduleOnRN(touched);
    })
    .onUpdate((event) => {
      drag.set(Math.max(0, event.translationY));
    })
    .onEnd((event) => {
      if (drag.get() > DISMISS_DRAG || event.velocityY > DISMISS_VELOCITY) {
        // From here the sheet is where the finger left it.
        sheet.set(1 - drag.get() / sheetHeight);
        drag.set(0);
        scheduleOnRN(close);
      } else drag.set(withSpring(0, RISE_SPRING));
    });

  const recede = useAnimatedStyle(() => ({
    borderRadius: lerp(0, PAGE_RADIUS, Math.min(1, sheet.get())),
    transform: [{ scale: lerp(1, PAGE_SCALE, Math.min(1, sheet.get())) }],
  }));
  const scrim = useAnimatedStyle(() => ({ opacity: SCRIM * Math.min(1, sheet.get()) * (1 - ramp(drag.get(), 0, sheetHeight)) }));
  const bannerStyle = useAnimatedStyle(() => ({ transform: [{ scale: bannerScale.get() }] }));
  const sheetStyle = useAnimatedStyle(() => ({ transform: [{ translateY: (1 - sheet.get()) * (sheetHeight + OVERSHOOT) + drag.get() }] }));
  const badgeStyle = useAnimatedStyle(() => {
    const time = clock.get();
    const up = easeOut(ramp(time, BADGE_UP, BADGE_UP + BADGE_UP_FOR));
    return {
      transform: [
        { translateY: lerp(0, BADGE_TOP_Y - LOADER_Y, up) },
        { scale: pop(ramp(time, BADGE_IN, BADGE_IN + BADGE_POP)) * lerp(1, BADGE_SMALL / BADGE_SIZE, up) },
      ],
    };
  });
  const haloStyle = useAnimatedStyle(() => {
    const t = clock.get();
    const on = ramp(t, RING_IN - 0.2, RING_IN + 0.3) * (1 - ramp(t, RING_OUT, RING_OUT + 0.4));
    return { opacity: on, transform: [{ scale: lerp(0.6, 1, on) }] };
  });
  const captionStyle = useAnimatedStyle(() => ({ opacity: ramp(clock.get(), CAPTION_IN, CAPTION_IN + 0.4) * (1 - ramp(clock.get(), CAPTION_OUT, CAPTION_OUT + 0.3)) }));
  const resultStyle = useAnimatedStyle(() => {
    const t = easeOut(ramp(clock.get(), RESULT_IN, RESULT_IN + RESULT_FOR));
    return { opacity: t, transform: [{ translateY: lerp(40, 0, t) }] };
  });
  const fingerStyle = useAnimatedStyle(() => {
    const f = finger.get();
    return {
      opacity: ramp(f, 0, 0.12) * (1 - ramp(f, 0.8, 1)),
      transform: [{ translateX: lerp(70, -60, easeOut(f)) }, { scale: lerp(1, 0.85, ramp(f, 0, 0.2)) }],
    };
  });

  return (
    <GestureHandlerRootView style={styles.screen}>
      <StatusBar style={isOpen ? 'light' : 'dark'} />

      <Animated.View style={[styles.page, recede]}>
        <PageBehind
          top={insets.top}
          bottom={insets.bottom}
          title={title}
          banner={banner}
          bannerCaption={bannerCaption}
          bannerStyle={bannerStyle}
          onBanner={() => {
            touched();
            open();
          }}
        />
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.scrim, scrim]} />
      </Animated.View>

      <GestureDetector gesture={dismiss}>
        <Animated.View
          pointerEvents={isOpen ? 'auto' : 'none'}
          style={[styles.sheet, { top: insets.top, height: sheetHeight + OVERSHOOT }, sheetStyle]}
        >
          <View style={styles.edge} />
          <View style={styles.clip}>
            <View style={styles.grabber} />
            <Animated.View style={[styles.caption, { top: CAPTION_Y }, captionStyle]}>
              <Text style={styles.captionText}>{loadingLabel}</Text>
            </Animated.View>

            <Animated.View pointerEvents="none" style={[styles.halo, { left: centre - HALO / 2, top: LOADER_Y - HALO / 2 }, haloStyle]} />
            {DISCS.map((spec, index) => (
              <Disc key={spec.icon} spec={spec} index={index} clock={clock} cx={centre} cy={LOADER_Y} />
            ))}
            <Ring clock={clock} cx={centre} cy={LOADER_Y} />

            <Animated.View style={[styles.badge, { left: centre - BADGE_SIZE / 2, top: LOADER_Y - BADGE_SIZE / 2 }, badgeStyle]}>
              <Badge size={BADGE_SIZE} />
            </Animated.View>

            <Animated.View style={[StyleSheet.absoluteFill, resultStyle]} pointerEvents={isOpen ? 'auto' : 'none'}>
              <Text style={[styles.resultTitle, { top: RESULT_TITLE_Y }]}>{resultTitle}</Text>
              <Text style={[styles.resultCaption, { top: RESULT_TITLE_Y + 34 }]}>{resultCaption}</Text>
              <GestureDetector gesture={pager}>
                <View style={[styles.pager, { top: PAGER_TOP }]}>
                  {profiles.map((profile, index) => (
                    <Page key={profile.name} index={index} page={page} width={width}>
                      <ProfileCard profile={profile} chatLabel={chatLabel} onChat={() => onChat?.(profile)} />
                    </Page>
                  ))}
                  <Animated.View pointerEvents="none" style={[styles.finger, { left: centre - FINGER / 2 }, fingerStyle]} />
                </View>
              </GestureDetector>
              <View style={[styles.dots, { top: PAGER_TOP + PAGER_HEIGHT + 20 }]}>
                {profiles.map((profile, index) => (
                  <Dot key={profile.name} index={index} page={page} />
                ))}
              </View>
              <View style={[styles.refresh, { top: PAGER_TOP + PAGER_HEIGHT + 44 }]}>
                <Ionicons name="time-outline" size={14} color="#9a9aab" />
                <Text style={styles.refreshText}>New picks in 14 h</Text>
              </View>
            </Animated.View>
          </View>
        </Animated.View>
      </GestureDetector>
    </GestureHandlerRootView>
  );
}

/** One card of the pager, laid side by side with the others and slid by the page position. */
function Page({ index, page, width, children }: { index: number; page: SharedValue<number>; width: number; children: React.ReactNode }) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: (index - page.get()) * width }] }));
  return <Animated.View style={[styles.card, { width }, style]}>{children}</Animated.View>;
}

/** A page dot that stretches while its card is the one on show. */
function Dot({ index, page }: { index: number; page: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const near = 1 - Math.min(1, Math.abs(page.get() - index));
    return { width: lerp(6, 16, near), opacity: lerp(0.25, 1, near) };
  });
  return <Animated.View style={[styles.dot, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: INDIGO },
  page: { ...StyleSheet.absoluteFill, overflow: 'hidden' },
  scrim: { backgroundColor: INDIGO },
  sheet: { position: 'absolute', left: 0, right: 0 },
  edge: { position: 'absolute', left: 18, right: 18, top: -10, height: 60, borderRadius: SHEET_RADIUS, backgroundColor: EDGE },
  clip: { flex: 1, borderTopLeftRadius: SHEET_RADIUS, borderTopRightRadius: SHEET_RADIUS, backgroundColor: '#ffffff', overflow: 'hidden' },
  grabber: { position: 'absolute', top: 8, alignSelf: 'center', width: 40, height: 5, borderRadius: 2.5, backgroundColor: '#e2e2ea' },
  badge: { position: 'absolute', width: BADGE_SIZE, height: BADGE_SIZE },
  caption: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  captionText: { fontSize: 16, lineHeight: 21, fontWeight: '600', color: '#6b6b7b' },
  halo: { position: 'absolute', width: HALO, height: HALO, borderRadius: HALO / 2, backgroundColor: '#efebfd' },
  resultTitle: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 24, lineHeight: 30, fontWeight: '800', letterSpacing: -0.6, color: '#14141f' },
  resultCaption: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 15, lineHeight: 20, fontWeight: '500', color: '#7b7b8c' },
  refresh: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 5 },
  refreshText: { fontSize: 13, lineHeight: 17, fontWeight: '600', color: '#9a9aab' },
  pager: { position: 'absolute', left: 0, right: 0, height: PAGER_HEIGHT },
  card: { position: 'absolute', left: 0, top: 0, bottom: 0 },
  finger: { position: 'absolute', top: 220, width: FINGER, height: FINGER, borderRadius: FINGER / 2, borderWidth: 2, borderColor: 'rgba(20,20,31,0.3)', backgroundColor: 'rgba(255,255,255,0.7)' },
  dots: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: DOTS_GAP },
  dot: { height: 6, borderRadius: 3, backgroundColor: INDIGO },
});
