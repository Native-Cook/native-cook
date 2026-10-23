import { Feather } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { type ComponentProps, type ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  cubicBezier,
  interpolateColor,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

type IconName = ComponentProps<typeof Feather>['name'];

export type TabItem = { label: string; icon: IconName };

/** An action of the grid: an icon, or a short text such as "Aa" in its place. */
export type Action = { label: string; icon?: IconName; glyph?: string };

export type Theme = {
  dark: boolean;
  page: string;
  /** Titles and icons. */
  ink: string;
  card: string;
  /** The placeholder shapes on a card. */
  shape: string;
  bar: string;
  highlight: string;
  panel: string;
  cell: string;
  label: string;
};

export const LIGHT: Theme = {
  dark: false,
  page: '#F3F3F3',
  ink: '#111111',
  card: '#FBFBFB',
  shape: '#E9E9E9',
  bar: '#FBFBFB',
  highlight: '#EDEDED',
  panel: '#FAFAFA',
  cell: '#EFEFEF',
  label: '#8A8A8E',
};

export const DARK: Theme = {
  dark: true,
  page: '#0E0D10',
  ink: '#FFFFFF',
  card: '#1A1A1D',
  shape: '#2A2A2E',
  bar: '#232425',
  highlight: '#343437',
  panel: '#1C1C1F',
  cell: '#2A2A2D',
  label: '#8A8A8E',
};

type Props = {
  tabs?: TabItem[];
  /** The actions of the grid, four to a row. */
  actions?: Action[];
  theme?: Theme;
  /** The tab that is selected at first, and the one autoPlay comes back to. */
  initialTab?: number;
  /** Goes through the tabs and opens the grid by itself every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (index: number) => void;
  /** Called when the grid opens (true) and closes (false). */
  onToggle?: (open: boolean) => void;
  onAction?: (index: number) => void;
};

const TABS: TabItem[] = [
  { label: 'Home', icon: 'home' },
  { label: 'Inbox', icon: 'inbox' },
  { label: 'Alerts', icon: 'bell' },
  { label: 'Apps', icon: 'layers' },
];

const ACTIONS: Action[] = [
  { label: 'Trim', icon: 'scissors' },
  { label: 'Crop', icon: 'crop' },
  { label: 'Enhance', icon: 'zap' },
  { label: 'Text', glyph: 'Aa' },
  { label: 'Audio', icon: 'music' },
  { label: 'Speed', icon: 'fast-forward' },
  { label: 'Duplicate', icon: 'copy' },
  { label: 'Undo', icon: 'corner-up-left' },
  { label: 'Share', icon: 'share' },
  { label: 'Save', icon: 'bookmark' },
  { label: 'Delete', icon: 'trash-2' },
];

// The heights of the placeholder cards on each tab's page, so that a new tab is a new page.
const PAGES = [
  [120, 72, 72, 72],
  [72, 72, 72, 72, 72],
  [72, 120, 72, 72],
  [120, 120, 72],
];

/** The period of the autoPlay loop, in seconds, and what it presses at each moment: a tab, or the plus (null). */
export const LOOP = 10;
const STEPS: { at: number; tab: number | null }[] = [
  { at: 1.0, tab: 1 },
  { at: 2.0, tab: 2 },
  { at: 3.0, tab: 3 },
  { at: 4.2, tab: 0 },
  { at: 5.4, tab: null },
  { at: 8.4, tab: null },
];
// After a touch the loop waits this long, returns to rest and starts over.
const USER_HOLD = 3;

const PAGE_MARGIN = 20;
const TITLE_TOP = 12;
const TITLE_HEIGHT = 22;
const TITLE_GAP = 20;
// The bottom row: the bar, a gap and the round plus button, between these side margins.
const MARGIN = 24;
const GAP = 8;
const BAR_HEIGHT = 48;
const PLUS = 48;
const PLUS_ARM = 16;
const PLUS_STROKE = 1.8;
// The row's bottom edge above the bottom of the screen. A bottom inset taller than a gesture bar's
// is a row of buttons, which the row stays clear of.
const BAR_BOTTOM = 24;
const GESTURE_INSET = 34;
const BUTTONS_CLEARANCE = 8;
const ICON = 22;
// The capsule behind the selected tab's icon, and how far the first and the last sit from the bar's ends.
const HIGHLIGHT_WIDTH = 60;
const HIGHLIGHT_HEIGHT = 40;
const HIGHLIGHT_INSET = 4;
const HIGHLIGHT_MIN = 36;
// The grid: four columns of 44 point buttons with a label under each.
const COLUMNS = 4;
const PAD = 16;
const PAD_BOTTOM = 14;
const CELL = 44;
const ROW = 60;
const ROW_GAP = 14;
const PANEL_RADIUS = 26;
const SHADOW = 'rgba(0,0,0,0.06)';

// The highlight's two ends each follow a spring to the new tab. The end in front takes the fast
// one, so the capsule is about a third wider in mid-flight between neighbours.
const FAST = { mass: 1, stiffness: 520, damping: 38, reduceMotion: ReduceMotion.Never };
const SLOW = { mass: 1, stiffness: 230, damping: 26, reduceMotion: ReduceMotion.Never };
// Seconds. It lifts while it moves: up in LIFT_UP, back in LIFT_DOWN.
const LIFT = 0.08;
const LIFT_UP = 0.14;
const LIFT_DOWN = 0.24;
// The page: the title cross-fades, the cards also slide.
const PAGE_FADE = 0.3;
const PAGE_SLIDE = 12;
// The bar stretches into the panel on one spring and shrinks back on a firmer one.
const OPEN = { mass: 1, stiffness: 130, damping: 20, reduceMotion: ReduceMotion.Never };
const CLOSE = { mass: 1, stiffness: 200, damping: 24, reduceMotion: ReduceMotion.Never };
// The tab icons are gone by this share of the way.
const TABS_GONE = 0.5;
// The plus turns to a cross on a spring that overshoots, and swells for a moment as the grid opens.
const TURN = { mass: 1, stiffness: 300, damping: 18, reduceMotion: ReduceMotion.Never };
const TURN_DEGREES = 45;
const SWELL = 1.25;
const SWELL_UP = 0.16;
const SWELL_DOWN = 0.26;
// The rows of the grid come in from the bottom one, each fading in while it grows, and leave together.
const CELLS_AFTER = 0.12;
const STAGGER = 0.02;
const CELLS_IN = 0.3;
const CELLS_OUT = 0.15;
const CELLS_SCALE = 0.9;
const PRESS_SCALE = 0.94;
// Without motion everything cross-fades in this time and the shapes jump.
const STILL_FADE = 0.2;

// A cubic ease-out, for the timed changes and for the transitions.
const EASE = Easing.bezier(0.33, 1, 0.68, 1);
const CSS_EASE = cubicBezier(0.33, 1, 0.68, 1);

function ease(seconds: number) {
  return { duration: seconds * 1000, easing: EASE, reduceMotion: ReduceMotion.Never };
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

function tap() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** One row of the grid: it fades in and grows when the grid opens, `after` seconds behind the shape. */
function Reveal({ shown, after, still, children }: { shown: boolean; after: number; still: boolean; children: ReactNode }) {
  const progress = useSharedValue(0);
  useEffect(() => {
    if (!shown) progress.set(withTiming(0, ease(still ? STILL_FADE : CELLS_OUT)));
    else if (still) progress.set(withTiming(1, ease(STILL_FADE)));
    else progress.set(withDelay(after * 1000, withTiming(1, ease(CELLS_IN)), ReduceMotion.Never));
  }, [shown, after, still, progress]);
  const animated = useAnimatedStyle(() => ({
    opacity: progress.get(),
    transform: [{ scale: still ? 1 : CELLS_SCALE + (1 - CELLS_SCALE) * progress.get() }],
  }));
  return <Animated.View style={[styles.row, animated]}>{children}</Animated.View>;
}

/**
 * A page with a floating tab bar and a round plus button next to it. The highlight behind the
 * selected tab's icon slides to a new tab stretched, its front end first. The plus turns into a
 * cross while the bar itself stretches upward into a panel holding a grid of actions; pressing
 * an action, the cross or the page shrinks it back.
 */
export function ActionGridTabBar({ tabs = TABS, actions = ACTIONS, theme = LIGHT, initialTab = 0, autoPlay = false, onSelect, onToggle, onAction }: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  const [selected, setSelected] = useState(initialTab);
  const [open, setOpen] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const barWidth = window.width - MARGIN * 2 - GAP - PLUS;
  const rows = Math.ceil(actions.length / COLUMNS);
  const panelHeight = PAD + rows * ROW + (rows - 1) * ROW_GAP + PAD_BOTTOM;
  /** The centre of a tab from the bar's left edge. */
  const tabX = (index: number) => {
    const first = HIGHLIGHT_INSET + HIGHLIGHT_WIDTH / 2;
    return first + (index * (barWidth - first * 2)) / Math.max(1, tabs.length - 1);
  };

  // Where the highlight's left and right ends are, as the centre each would give it.
  const left = useSharedValue(tabX(initialTab));
  const right = useSharedValue(tabX(initialTab));
  const lift = useSharedValue(0);
  // How far the bar is stretched into the panel, 0 to 1.
  const stretch = useSharedValue(0);
  const turn = useSharedValue(0);
  const swell = useSharedValue(1);

  const select = (index: number, byHand: boolean) => {
    if (open || index === selected) return;
    // The loop that plays by itself is silent.
    if (byHand) tick();
    const x = tabX(index);
    const forward = index > selected;
    if (reducedMotion) {
      left.set(x);
      right.set(x);
    } else {
      left.set(withSpring(x, forward ? SLOW : FAST));
      right.set(withSpring(x, forward ? FAST : SLOW));
      lift.set(withSequence(withTiming(1, ease(LIFT_UP)), withTiming(0, ease(LIFT_DOWN))));
    }
    setSelected(index);
    onSelect?.(index);
  };

  const toggle = (byHand: boolean) => {
    const next = !open;
    if (byHand) tap();
    setOpen(next);
    if (reducedMotion) {
      stretch.set(next ? 1 : 0);
      turn.set(withTiming(next ? TURN_DEGREES : 0, ease(STILL_FADE)));
    } else {
      stretch.set(withSpring(next ? 1 : 0, next ? OPEN : CLOSE));
      turn.set(withSpring(next ? TURN_DEGREES : 0, TURN));
      if (next) swell.set(withSequence(withTiming(SWELL, ease(SWELL_UP)), withTiming(1, ease(SWELL_DOWN))));
    }
    onToggle?.(next);
  };

  const rest = () => {
    if (open) toggle(false);
    else select(initialTab, false);
  };

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => setTouches((n) => n + 1);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ select, toggle, rest, open, selected, initialTab });
  useEffect(() => {
    latest.current = { select, toggle, rest, open, selected, initialTab };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    // Every step is timed from the loop's origin, so a late timer does not push the next one back.
    const cycle = (origin: number) => {
      STEPS.forEach((step) =>
        after(origin + step.at * 1000 - Date.now(), () => (step.tab === null ? latest.current.toggle(false) : latest.current.select(step.tab, false))),
      );
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    // Rest is the grid closed on the first tab: one step at a time, as the grid hides the tabs.
    const settle = () => {
      const now = latest.current;
      if (!now.open && now.selected === now.initialTab) return cycle(Date.now());
      now.rest();
      after(100, settle);
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, settle);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  // The bar and the panel are one shape: its height, its corners and its fill follow the stretch.
  const shapeStyle = useAnimatedStyle(() => ({
    height: BAR_HEIGHT + (panelHeight - BAR_HEIGHT) * stretch.get(),
    borderRadius: BAR_HEIGHT / 2 + (PANEL_RADIUS - BAR_HEIGHT / 2) * stretch.get(),
    backgroundColor: interpolateColor(stretch.get(), [0, 1], [theme.bar, theme.panel]),
  }));
  const tabsStyle = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, Math.max(0, stretch.get() / TABS_GONE)) }));
  const highlightStyle = useAnimatedStyle(() => ({
    left: Math.min(left.get(), right.get()) - HIGHLIGHT_WIDTH / 2,
    width: Math.max(HIGHLIGHT_MIN, Math.abs(right.get() - left.get()) + HIGHLIGHT_WIDTH),
    transform: [{ scale: 1 + LIFT * lift.get() }],
  }));
  const liftStyle = useAnimatedStyle(() => ({ opacity: lift.get() }));
  const plusStyle = useAnimatedStyle(() => ({ transform: [{ scale: swell.get() }] }));
  const crossStyle = useAnimatedStyle(() => ({ transform: [{ rotate: `${turn.get()}deg` }] }));

  const bottom = insets.bottom > GESTURE_INSET ? insets.bottom + BUTTONS_CLEARANCE : BAR_BOTTOM;
  const pageFade = { transitionDuration: (reducedMotion ? STILL_FADE : PAGE_FADE) * 1000, transitionTimingFunction: CSS_EASE };

  return (
    <View style={[styles.screen, { backgroundColor: theme.page }]}>
      <StatusBar style={theme.dark ? 'light' : 'dark'} />

      {tabs.map((tab, t) => {
        const shown = t === selected;
        const aside = reducedMotion || shown ? 0 : t < selected ? -PAGE_SLIDE : PAGE_SLIDE;
        return (
          <View
            key={t}
            pointerEvents="none"
            accessibilityElementsHidden={!shown}
            importantForAccessibility={shown ? 'auto' : 'no-hide-descendants'}
            style={[styles.fill, { paddingTop: insets.top + TITLE_TOP }]}
          >
            <Animated.Text
              style={[styles.title, { color: theme.ink, opacity: shown ? 1 : 0, transitionProperty: 'opacity' }, pageFade]}
              numberOfLines={1}
              accessibilityRole="header"
            >
              {tab.label}
            </Animated.Text>
            <Animated.View style={[styles.cards, { opacity: shown ? 1 : 0, transform: [{ translateX: aside }], transitionProperty: ['opacity', 'transform'] }, pageFade]}>
              {PAGES[t % PAGES.length].map((height, c) => (
                <View key={c} style={[styles.card, { height, backgroundColor: theme.card }]}>
                  <View style={[styles.cardDot, { backgroundColor: theme.shape }]} />
                  <View style={styles.cardLines}>
                    <View style={[styles.cardLine, { width: 120, backgroundColor: theme.shape }]} />
                    <View style={[styles.cardLine, { width: 76, height: 6, backgroundColor: theme.shape }]} />
                  </View>
                </View>
              ))}
            </Animated.View>
          </View>
        );
      })}

      {open && (
        <Pressable
          style={styles.fill}
          accessibilityLabel="Close"
          onPress={() => {
            touched();
            toggle(true);
          }}
        />
      )}

      <Animated.View style={[styles.shape, { left: MARGIN, bottom, width: barWidth }, shapeStyle]}>
        <Animated.View pointerEvents={open ? 'none' : 'auto'} accessibilityElementsHidden={open} style={[styles.tabs, tabsStyle]} accessibilityRole="tablist">
          <Animated.View style={[styles.highlight, styles.highlightLift, { backgroundColor: theme.highlight }, highlightStyle, liftStyle]} />
          <Animated.View style={[styles.highlight, { backgroundColor: theme.highlight }, highlightStyle]} />
          {tabs.map((tab, t) => (
            <Pressable
              key={t}
              style={[styles.tab, { left: tabX(t) - HIGHLIGHT_WIDTH / 2 }]}
              accessibilityRole="tab"
              accessibilityLabel={tab.label}
              accessibilityState={{ selected: t === selected }}
              onPress={() => {
                touched();
                select(t, true);
              }}
            >
              <Feather name={tab.icon} size={ICON} color={theme.ink} />
            </Pressable>
          ))}
        </Animated.View>

        <View pointerEvents={open ? 'auto' : 'none'} accessibilityElementsHidden={!open} style={[styles.grid, { height: panelHeight }]}>
          {Array.from({ length: rows }, (_, r) => (
            // The bottom row comes first.
            <Reveal key={r} shown={open} after={CELLS_AFTER + (rows - 1 - r) * STAGGER} still={reducedMotion}>
              {Array.from({ length: COLUMNS }, (__, c) => {
                const index = r * COLUMNS + c;
                const action = actions[index];
                if (!action) return <View key={c} style={styles.cellSlot} />;
                return (
                  <Pressable
                    key={c}
                    style={styles.cellSlot}
                    accessibilityRole="button"
                    accessibilityLabel={action.label}
                    onPress={() => {
                      touched();
                      onAction?.(index);
                      toggle(true);
                    }}
                  >
                    {({ pressed }) => (
                      <>
                        <Animated.View
                          style={[
                            styles.cell,
                            { backgroundColor: theme.cell, transform: [{ scale: pressed ? PRESS_SCALE : 1 }], transitionProperty: 'transform', transitionDuration: 120 },
                          ]}
                        >
                          {action.icon ? (
                            <Feather name={action.icon} size={ICON} color={theme.ink} />
                          ) : (
                            <Text style={[styles.glyph, { color: theme.ink }]}>{action.glyph}</Text>
                          )}
                        </Animated.View>
                        <Text style={[styles.cellLabel, { color: theme.label }]} numberOfLines={1}>
                          {action.label}
                        </Text>
                      </>
                    )}
                  </Pressable>
                );
              })}
            </Reveal>
          ))}
        </View>
      </Animated.View>

      <Animated.View style={[styles.plus, { right: MARGIN, bottom, backgroundColor: theme.bar }, plusStyle]}>
        <Pressable
          style={styles.plusPress}
          accessibilityRole="button"
          accessibilityLabel={open ? 'Close' : 'Actions'}
          accessibilityState={{ expanded: open }}
          onPress={() => {
            touched();
            toggle(true);
          }}
        >
          <Animated.View style={[styles.cross, crossStyle]}>
            <View style={[styles.arm, { backgroundColor: theme.ink }]} />
            <View style={[styles.arm, styles.armUp, { backgroundColor: theme.ink }]} />
          </Animated.View>
        </Pressable>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  title: {
    height: TITLE_HEIGHT,
    fontSize: 17,
    lineHeight: TITLE_HEIGHT,
    fontWeight: '600',
    textAlign: 'center',
    includeFontPadding: false,
  },
  cards: {
    marginTop: TITLE_GAP,
    paddingHorizontal: PAGE_MARGIN,
    gap: 12,
  },
  card: {
    borderRadius: 20,
    borderCurve: 'continuous',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 18,
    gap: 14,
  },
  cardDot: {
    width: 36,
    height: 36,
    borderRadius: 18,
  },
  cardLines: {
    gap: 8,
  },
  cardLine: {
    height: 8,
    borderRadius: 4,
  },
  shape: {
    position: 'absolute',
    overflow: 'hidden',
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 10, color: SHADOW }],
  },
  tabs: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    height: BAR_HEIGHT,
  },
  highlight: {
    position: 'absolute',
    top: (BAR_HEIGHT - HIGHLIGHT_HEIGHT) / 2,
    height: HIGHLIGHT_HEIGHT,
    borderRadius: HIGHLIGHT_HEIGHT / 2,
  },
  // The same capsule with a wider shadow, shown while the highlight is lifted.
  highlightLift: {
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 16, color: 'rgba(0,0,0,0.12)' }],
  },
  tab: {
    position: 'absolute',
    top: 0,
    width: HIGHLIGHT_WIDTH,
    height: BAR_HEIGHT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // The grid keeps its place: the shape's top edge uncovers it.
  grid: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    paddingTop: PAD,
    paddingHorizontal: PAD,
    gap: ROW_GAP,
  },
  row: {
    height: ROW,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  cellSlot: {
    width: CELL,
    height: ROW,
    alignItems: 'center',
  },
  cell: {
    width: CELL,
    height: CELL,
    borderRadius: 12,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  glyph: {
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '500',
    includeFontPadding: false,
  },
  // Wider than its cell, so a long label is not cut.
  cellLabel: {
    width: CELL + 20,
    marginTop: 4,
    fontSize: 10,
    lineHeight: 12,
    textAlign: 'center',
    includeFontPadding: false,
  },
  plus: {
    position: 'absolute',
    width: PLUS,
    height: PLUS,
    borderRadius: PLUS / 2,
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 10, color: SHADOW }],
  },
  plusPress: {
    width: PLUS,
    height: PLUS,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cross: {
    width: PLUS_ARM,
    height: PLUS_ARM,
    alignItems: 'center',
    justifyContent: 'center',
  },
  arm: {
    position: 'absolute',
    width: PLUS_ARM,
    height: PLUS_STROKE,
    borderRadius: PLUS_STROKE / 2,
  },
  armUp: {
    transform: [{ rotate: '90deg' }],
  },
});
