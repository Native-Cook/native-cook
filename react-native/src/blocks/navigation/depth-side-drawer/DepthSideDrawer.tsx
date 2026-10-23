import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, type ViewStyle, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { ShopPage } from './ShopPage';

export type DrawerItem = {
  id: string;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
};

type Props = {
  /** The menu's destinations; the page starts on the first one. */
  items?: DrawerItem[];
  name?: string;
  place?: string;
  /** The address of the round picture next to the name. */
  photo?: string;
  logoutLabel?: string;
  /** The menu's colour, which also marks the lit tab of the page. */
  menuColor?: string;
  /** Opens the menu and picks destinations by itself, back at the start every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (item: DrawerItem) => void;
  onLogout?: () => void;
};

const ITEMS: DrawerItem[] = [
  { id: 'home', label: 'Home', icon: 'home-outline' },
  { id: 'categories', label: 'Categories', icon: 'grid-outline' },
  { id: 'cart', label: 'My Cart', icon: 'bag-outline' },
  { id: 'wishlist', label: 'Wish List', icon: 'heart-outline' },
  { id: 'orders', label: 'Track Order', icon: 'cube-outline' },
  { id: 'account', label: 'Account', icon: 'person-outline' },
  { id: 'settings', label: 'Settings', icon: 'settings-outline' },
];
const PHOTO = 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=208&h=208&q=80';

const MENU = '#52349F';
const PAGE = '#FAFCFE';
const SHADOW = 'rgba(30,15,77,0.35)';
const HAIRLINE = 'rgba(255,255,255,0.14)';
const MARK = 'rgba(255,255,255,0.12)';
const FAINT = 'rgba(255,255,255,0.6)';

// The open page is a card scaled about the middle of its left edge, which stops PEEK points
// short of the screen's right edge, and never over the menu's labels.
const PEEK = 131;
const OPEN_MIN = 262;
const SCALE = 0.78;
// Degrees about the card's left edge: its right edge goes away, like a door swung aside.
const TILT = 14;
const PERSPECTIVE = 900;
const LIFT = -12;
const RADIUS = 30;
// The cards behind the page: how small, how far further left and how faint each one ends, and
// the power of the page's progress it follows, which makes it trail.
const GHOSTS = [
  { scale: 0.72, back: 18, alpha: 0.18, trail: 1.4 },
  { scale: 0.66, back: 34, alpha: 0.09, trail: 1.8 },
];
const OPEN_SPRING = { mass: 1, stiffness: 140, damping: 17 };
const CLOSE_SPRING = { mass: 1, stiffness: 220, damping: 28 };
// Reduced motion: the page only slides, on a short curve.
const SLIDE = 250;

// The menu's rows come in one after another, in seconds from the moment the page starts to move.
const ROW_DELAY = 0.1;
const ROW_GAP = 0.035;
const ROW_IN = 0.35;
const ROW_SHIFT = 24;
const ROWS_OUT = 0.15;
// The profile, a hairline, the items, a hairline, log out.
const rowsFor = (items: number) => items + 4;
const revealFor = (items: number) => ROW_DELAY + (rowsFor(items) - 1) * ROW_GAP + ROW_IN;
// A chosen row is marked, the page changes behind the menu, and then the drawer closes.
const MARK_IN = 150;
const CHOICE_HOLD = 0.4;

// A drag opens the drawer when it starts in this band on the left of the closed page.
const EDGE = 80;
const FLING = 300;

const ROW = 44;
const PROFILE_LEAD = 155;
const PROFILE_LEAD_MIN = 56;
// From the top of the profile to the bottom of log out with seven items, plus a margin.
const MENU_BODY = 504;
const USER_HOLD = 3;
const LOOP = 9;
// The loop that plays by itself: when each state is due, in seconds from the start of a loop.
const SCRIPT: { at: number; open?: boolean; choose?: number }[] = [
  { at: 0.8, open: true },
  { at: 3.0, choose: 3 },
  { at: 3.4, open: false },
  { at: 5.4, open: true },
  { at: 7.2, choose: 0 },
  { at: 7.6, open: false },
];

const NEVER = ReduceMotion.Never;

function easeOut(t: number) {
  'worklet';
  const c = Math.min(1, Math.max(0, t));
  return 1 - (1 - c) ** 3;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** One line of the menu: it waits its turn, then slides in from the left while it fades in. */
function Row({
  order,
  reveal,
  stagger,
  leave,
  still,
  style,
  children,
}: {
  order: number;
  reveal: number;
  stagger: SharedValue<number>;
  leave: SharedValue<number>;
  still: boolean;
  style: ViewStyle;
  children: ReactNode;
}) {
  const animated = useAnimatedStyle(() => {
    const shown = easeOut((stagger.get() * reveal - ROW_DELAY - order * ROW_GAP) / ROW_IN) * (1 - leave.get());
    return { opacity: shown, transform: [{ translateX: still ? 0 : -ROW_SHIFT * (1 - shown) }] };
  });
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

/**
 * A side menu with depth. The menu fills the screen underneath; opening it swings the page aside
 * as a rounded card, scaled down and turned about its left edge, with two faint cards trailing
 * behind it, while the menu's rows come in one after another. Choosing a row changes the page
 * behind the menu and closes the drawer.
 */
export function DepthSideDrawer({
  items = ITEMS,
  name = 'Mara Ellis',
  place = 'Portland',
  photo = PHOTO,
  logoutLabel = 'Logout',
  menuColor = MENU,
  autoPlay = false,
  onSelect,
  onLogout,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [open, setOpen] = useState(false);
  const [destination, setDestination] = useState(0);
  // The status bar is over the menu once the page is more than half way aside.
  const [overMenu, setOverMenu] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // A row was chosen by hand: the drawer closes a moment later unless another touch comes first.
  const [chosen, setChosen] = useState(false);

  // 0 with the page in place, 1 with the drawer open; a spring may pass 1 a little.
  const progress = useSharedValue(0);
  // The clock of the rows coming in, and their leaving together.
  const stagger = useSharedValue(0);
  const leave = useSharedValue(0);
  // Where a drag started, and whether it started where a drag may move the page.
  const from = useSharedValue(0);
  const dragging = useSharedValue(false);

  const openX = Math.max(OPEN_MIN, size.width - PEEK);
  const reveal = revealFor(items.length);
  const lead = Math.min(PROFILE_LEAD, Math.max(PROFILE_LEAD_MIN, size.height - insets.top - insets.bottom - MENU_BODY));
  const profileTop = insets.top + lead;
  const rowsTop = profileTop + 96;

  const slide = (to: number) => {
    progress.set(
      reducedMotion
        ? withTiming(to, { duration: SLIDE, easing: Easing.out(Easing.cubic), reduceMotion: NEVER })
        : withSpring(to, to === 1 ? OPEN_SPRING : CLOSE_SPRING),
    );
  };

  const showRows = () => {
    cancelAnimation(stagger);
    if (reducedMotion) {
      // Together, with a short fade.
      stagger.set(1);
      leave.set(1);
      leave.set(withTiming(0, { duration: ROWS_OUT * 1000, reduceMotion: NEVER }));
    } else {
      leave.set(0);
      stagger.set(0);
      stagger.set(withTiming(1, { duration: reveal * 1000, easing: Easing.linear }));
    }
  };

  const hideRows = () => leave.set(withTiming(1, { duration: ROWS_OUT * 1000, reduceMotion: NEVER }));

  const setDrawer = (to: boolean) => {
    if (to === open) return;
    setOpen(to);
    if (to) showRows();
    else hideRows();
    slide(to ? 1 : 0);
  };

  const go = (index: number) => {
    setDestination(index);
    onSelect?.(items[index]);
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setChosen(false);
    setTouches((n) => n + 1);
  };

  const press = (to: boolean) => {
    touched();
    tick();
    setDrawer(to);
  };

  const choose = (index: number) => {
    touched();
    tick();
    go(index);
    setChosen(true);
  };

  const dragStarted = () => {
    touched();
    if (!open) showRows();
  };

  const dragEnded = (to: boolean) => {
    if (to !== open) tick();
    setOpen(to);
    if (!to) hideRows();
    slide(to ? 1 : 0);
  };

  // The latest of everything the loop's timers call.
  const latest = useRef({ setDrawer, go });
  useEffect(() => {
    latest.current = { setDrawer, go };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      // Each step sets the state that is due at its time, whatever the steps before it did.
      for (const step of SCRIPT) {
        after(origin + step.at * 1000 - Date.now(), () => {
          if (step.choose !== undefined) latest.current.go(step.choose);
          else latest.current.setDrawer(step.open === true);
        });
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) cycle(Date.now());
    else {
      after(USER_HOLD * 1000, () => {
        // Back to rest; the drawer has closed by the time the loop first opens it.
        latest.current.setDrawer(false);
        setDestination(0);
        cycle(Date.now());
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  useEffect(() => {
    if (!chosen) return;
    const timer = setTimeout(() => {
      setChosen(false);
      latest.current.setDrawer(false);
    }, CHOICE_HOLD * 1000);
    return () => clearTimeout(timer);
  }, [chosen, touches]);

  useAnimatedReaction(
    () => progress.get() > 0.5,
    (now, old) => {
      if (now !== old) scheduleOnRN(setOverMenu, now);
    },
  );

  const pan = Gesture.Pan()
    .activeOffsetX([-12, 12])
    .failOffsetY([-16, 16])
    .onBegin((event) => {
      // Closed, the page is dragged from its left edge; open, by the card.
      dragging.set(progress.get() < 0.5 ? event.x < EDGE : event.x > openX - GHOSTS[1].back);
    })
    .onStart(() => {
      if (!dragging.get()) return;
      cancelAnimation(progress);
      from.set(progress.get());
      scheduleOnRN(dragStarted);
    })
    .onUpdate((event) => {
      if (!dragging.get()) return;
      progress.set(Math.min(1, Math.max(0, from.get() + event.translationX / openX)));
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      const to = event.velocityX > FLING ? true : event.velocityX < -FLING ? false : progress.get() > 0.5;
      scheduleOnRN(dragEnded, to);
    });

  const pageStyle = useAnimatedStyle(() => {
    const p = progress.get();
    const radius = RADIUS * Math.min(1, Math.max(0, p));
    if (reducedMotion) return { borderRadius: radius, transform: [{ translateX: openX * p }] };
    return {
      borderRadius: radius,
      transform: [
        { translateX: openX * p },
        { translateY: LIFT * p },
        { perspective: PERSPECTIVE },
        { rotateY: `${TILT * p}deg` },
        { scale: 1 - (1 - SCALE) * p },
      ],
    };
  });
  const shadowStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, progress.get())) }));

  // The progress is read by the styles themselves: a shared value read only inside a helper is
  // not one the style follows, and the ghosts would move only when the screen renders.
  const ghostStyle = (ghost: (typeof GHOSTS)[number], p: number) => {
    'worklet';
    const g = Math.max(0, p) ** ghost.trail;
    return {
      // Nothing of a ghost shows behind the closed page, so it is not painted there.
      opacity: reducedMotion ? 0 : ghost.alpha * Math.min(1, Math.max(0, p * 5)),
      transform: [
        { translateX: (openX - ghost.back) * g },
        { translateY: LIFT * g },
        { perspective: PERSPECTIVE },
        { rotateY: `${TILT * g}deg` },
        { scale: 1 - (1 - ghost.scale) * g },
      ],
    };
  };
  const nearGhost = useAnimatedStyle(() => ghostStyle(GHOSTS[0], progress.get()));
  const farGhost = useAnimatedStyle(() => ghostStyle(GHOSTS[1], progress.get()));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const row = { reveal, stagger, leave, still: reducedMotion };
  const current = items[destination] ?? items[0];

  return (
    <GestureDetector gesture={pan}>
      <View style={[styles.screen, { backgroundColor: menuColor }]} onLayout={onLayout}>
        <StatusBar style={overMenu ? 'light' : 'dark'} />

        <View
          style={styles.fill}
          pointerEvents={open ? 'auto' : 'none'}
          accessibilityElementsHidden={!open}
          importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}
        >
          <Row order={0} {...row} style={{ ...styles.close, top: insets.top - 4 }}>
            <Pressable style={styles.closeButton} onPress={() => press(false)} accessibilityRole="button" accessibilityLabel="Close menu">
              <View style={[styles.stroke, styles.strokeDown]} />
              <View style={[styles.stroke, styles.strokeUp]} />
            </Pressable>
          </Row>

          <Row order={0} {...row} style={{ ...styles.profile, top: profileTop }}>
            <View style={styles.photo}>
              <Image source={{ uri: photo }} style={StyleSheet.absoluteFill} contentFit="cover" />
            </View>
            <View>
              <Text style={styles.name} numberOfLines={1}>
                {name}
              </Text>
              <Text style={styles.place} numberOfLines={1}>
                {place}
              </Text>
            </View>
          </Row>

          <Row order={1} {...row} style={{ ...styles.hairline, top: profileTop + 76 }}>
            {null}
          </Row>

          {items.map((item, index) => (
            <Row key={item.id} order={index + 2} {...row} style={{ ...styles.row, top: rowsTop + index * ROW }}>
              <Pressable
                style={styles.rowButton}
                onPress={() => choose(index)}
                accessibilityRole="button"
                accessibilityState={{ selected: index === destination }}
              >
                <Animated.View
                  style={[styles.mark, { opacity: index === destination ? 1 : 0, transitionProperty: 'opacity', transitionDuration: MARK_IN }]}
                />
                <Ionicons name={item.icon} size={20} color="#ffffff" />
                <Text style={styles.label} numberOfLines={1}>
                  {item.label}
                </Text>
              </Pressable>
            </Row>
          ))}

          <Row order={items.length + 2} {...row} style={{ ...styles.hairline, top: rowsTop + items.length * ROW + 20 }}>
            {null}
          </Row>

          <Row order={items.length + 3} {...row} style={{ ...styles.row, top: rowsTop + items.length * ROW + 40 }}>
            <Pressable
              style={styles.rowButton}
              accessibilityRole="button"
              onPress={() => {
                touched();
                tick();
                onLogout?.();
              }}
            >
              <Ionicons name="log-out-outline" size={20} color="#ffffff" />
              <Text style={styles.label} numberOfLines={1}>
                {logoutLabel}
              </Text>
            </Pressable>
          </Row>
        </View>

        <Animated.View pointerEvents="none" style={[styles.fill, styles.card, styles.ghost, farGhost]} />
        <Animated.View pointerEvents="none" style={[styles.fill, styles.card, styles.ghost, nearGhost]} />
        <Animated.View pointerEvents="none" style={[styles.fill, styles.card, styles.shadow, pageStyle, shadowStyle]} />

        {/* One layer, so that the turned page shows no seams between its parts. */}
        <Animated.View
          style={[styles.fill, styles.card, styles.page, pageStyle]}
          pointerEvents={open ? 'none' : 'auto'}
          accessibilityElementsHidden={open}
          importantForAccessibility={open ? 'no-hide-descendants' : 'auto'}
          shouldRasterizeIOS
          renderToHardwareTextureAndroid
        >
          <ShopPage
            title={current.label}
            index={destination}
            active={current.id}
            accent={menuColor}
            insets={insets}
            onMenu={() => press(true)}
            onTab={(id) => {
              const index = items.findIndex((item) => item.id === id);
              touched();
              tick();
              if (index >= 0) go(index);
            }}
          />
        </Animated.View>

        {open && (
          <Pressable
            style={[styles.back, { left: openX - GHOSTS[1].back }]}
            onPress={() => press(false)}
            accessibilityRole="button"
            accessibilityLabel="Close menu"
          />
        )}
      </View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  card: {
    transformOrigin: 'left center',
    borderCurve: 'continuous',
  },
  ghost: {
    borderRadius: RADIUS,
    backgroundColor: '#ffffff',
  },
  shadow: {
    boxShadow: [{ offsetX: -12, offsetY: 16, blurRadius: 40, color: SHADOW }],
  },
  page: {
    overflow: 'hidden',
    backgroundColor: PAGE,
  },
  back: {
    position: 'absolute',
    right: 0,
    top: 0,
    bottom: 0,
  },
  close: {
    position: 'absolute',
    left: 11,
  },
  closeButton: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stroke: {
    position: 'absolute',
    width: 24,
    height: 2,
    borderRadius: 1,
    backgroundColor: '#ffffff',
  },
  strokeDown: {
    transform: [{ rotate: '45deg' }],
  },
  strokeUp: {
    transform: [{ rotate: '-45deg' }],
  },
  profile: {
    position: 'absolute',
    left: 24,
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  photo: {
    width: 52,
    height: 52,
    borderRadius: 26,
    overflow: 'hidden',
    backgroundColor: 'rgba(255,255,255,0.2)',
  },
  name: {
    color: '#ffffff',
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '600',
    includeFontPadding: false,
  },
  place: {
    marginTop: 2,
    color: FAINT,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  hairline: {
    position: 'absolute',
    left: 24,
    width: 200,
    height: 1,
    backgroundColor: HAIRLINE,
  },
  row: {
    position: 'absolute',
    left: 12,
    width: 216,
    height: ROW,
  },
  rowButton: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 14,
    gap: 16,
  },
  mark: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 2,
    bottom: 2,
    borderRadius: 20,
    backgroundColor: MARK,
  },
  label: {
    color: '#ffffff',
    fontSize: 16,
    lineHeight: 21,
    includeFontPadding: false,
  },
});
