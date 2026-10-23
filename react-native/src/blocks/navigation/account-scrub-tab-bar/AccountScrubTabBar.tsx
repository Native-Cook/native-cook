import { Blur, Canvas, Group, Paint, Path, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  LayoutAnimationConfig,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export type Message = { sender: string; subject: string; time: string };

export type Account = {
  address: string;
  /** The badge and the pictures of its senders, as #RRGGBB. */
  color: string;
  messages: Message[];
};

export type TabIcon = 'home' | 'search' | 'tray' | 'compose';

export type Tab = { title: string; icon: TabIcon };

type Props = {
  /** The accounts the badge scrubs through, first to last. */
  accounts?: Account[];
  /** The entry after the last account, which shows every account's mail. */
  allLabel?: string;
  /** The items before the badge; the first one is open. */
  tabs?: Tab[];
  /** Holds the badge and scrubs through the accounts by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called with the place of the account under the slot; `accounts.length` is "All accounts". */
  onSelectAccount?: (index: number) => void;
  onSelectTab?: (index: number) => void;
};

const ACCOUNTS: Account[] = [
  {
    address: 'nora.lind@inbox.example',
    color: '#F5B5E6',
    messages: [
      { sender: 'Ida Holm', subject: 'Proofs for Friday', time: '9:41' },
      { sender: 'Billing', subject: 'Your receipt for October', time: '9:05' },
      { sender: 'Marek Dvorak', subject: 'Dinner on Saturday?', time: '8:15' },
      { sender: 'Trail Club', subject: 'Route notes for the weekend', time: 'Yesterday' },
      { sender: 'Lena Ortiz', subject: 'Photos from the lake', time: 'Yesterday' },
      { sender: 'Library', subject: 'Your hold is ready', time: 'Mon' },
      { sender: 'Sam Whitlow', subject: 'Re: spare keys', time: 'Sun' },
    ],
  },
  {
    address: 'nora@lind.studio',
    color: '#F2D675',
    messages: [
      { sender: 'Jonas Brandt', subject: 'Logo, round two', time: '10:02' },
      { sender: 'Print Room', subject: 'Quote for 200 posters', time: '9:20' },
      { sender: 'Aiko Tanabe', subject: 'Invoice 0142', time: '8:40' },
      { sender: 'Studio Notes', subject: 'Shared folder updated', time: 'Yesterday' },
      { sender: 'Petra Novak', subject: 'Type samples attached', time: 'Tue' },
      { sender: 'Hale & Finch', subject: 'Contract, signed copy', time: 'Mon' },
      { sender: 'Workshop', subject: 'Seats for November', time: 'Sun' },
    ],
  },
  {
    address: 'team@oakline.example',
    color: '#62E6C3',
    messages: [
      { sender: 'Deploy Bot', subject: 'Build 418 passed', time: '10:14' },
      { sender: 'Rhea Malik', subject: 'Sprint review moved', time: '9:36' },
      { sender: 'Support', subject: 'Three new tickets', time: '8:58' },
      { sender: 'Tomas Reyes', subject: 'Design handoff', time: 'Yesterday' },
      { sender: 'Calendar', subject: 'Planning, Thursday 10:00', time: 'Yesterday' },
      { sender: 'Noor Haddad', subject: 'Budget sheet v3', time: 'Tue' },
      { sender: 'Oakline HR', subject: 'Holiday schedule', time: 'Mon' },
    ],
  },
];

const TABS: Tab[] = [
  { title: 'Inbox', icon: 'home' },
  { title: 'Search', icon: 'search' },
  { title: 'Archive', icon: 'tray' },
  { title: 'Drafts', icon: 'compose' },
];

const PAGE = '#0A0A0A';
const BAR = '#1A1A1A';
const INK = '#FFFFFF';
const MUTE = '#8E8E93';
const FAINT = '#636366';
const HAIRLINE = '#1C1C1E';
const ICON = '#B4B4B8';
const ADDRESS = '#D0D0D4';
// How much of an address is left one place away from the slot.
const ADDRESS_AWAY = 0.44;

const MARGIN = 20;
const TITLE_TOP = 14;
const ROW = 72;
const ROWS = 7;
const AVATAR = 40;
const BAR_MARGIN = 16;
const BAR_HEIGHT = 56;
const BAR_RADIUS = 24;
// From the bottom of the screen, where the home indicator leaves room for it.
const BAR_BOTTOM = 30;
const ICON_BOX = 22;
const STROKE = 1.8;
// The open tab's lines are this much heavier.
const STROKE_ON = 1.35;
const BADGE = 22;
// The badge under the finger.
const SWELL = 1.25;
const DOT = 13;
// How far apart two accounts are in the column of badges and in the column of addresses: a
// neighbouring badge is outside the bar, a neighbouring address is cut in half by its edge.
const BADGE_PITCH = 44;
const ADDRESS_PITCH = 28;
const ADDRESS_GAP = 20;
const ADDRESS_SLIDE = 12;
// Finger travel that moves the wheel by one account.
const STEP = 34;
// How far past its ends the wheel can be pulled, in accounts.
const RUBBER = 0.8;
const ICON_BLUR = 10;
const ICON_FADE = 0.15;
const FINGER = 44;
const FINGER_OPACITY = 0.45;

export const LOOP = 10;
// Seconds a press on the badge lasts before it becomes a scrub.
const HOLD = 0.25;
const OPEN_IN = 0.25;
const CLOSE_AFTER = 0.25;
const CLOSE_IN = 0.3;
const PAGE_FADE = 0.15;
const SNAP = { mass: 1, stiffness: 300, damping: 26 };
// The fades are part of how the bar works, so they play under reduced motion too.
const NEVER = { reduceMotion: ReduceMotion.Never };
const FADE_IN = FadeIn.duration(PAGE_FADE * 1000).reduceMotion(ReduceMotion.Never);
const FADE_OUT = FadeOut.duration(PAGE_FADE * 1000).reduceMotion(ReduceMotion.Never);
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
// The loop starts again this long after the last touch.
const USER_HOLD = 3;
// The finger of the autoPlay loop: when it lands on the badge, how many accounts it has dragged
// to since (up the screen is later in the list), and when it lifts. Each drag takes MOVE seconds.
const MOVE = 0.4;
const FINGER_IN = 0.15;
const FINGER_OUT = 0.2;
const SCRIPT: { at: number; finger: 'down' | 'up' | number }[] = [
  { at: 0.6, finger: 'down' },
  { at: 1.1, finger: 1 },
  { at: 1.8, finger: 2 },
  { at: 2.5, finger: 3 },
  { at: 3.5, finger: 2 },
  { at: 4.2, finger: 1 },
  { at: 5.0, finger: 'up' },
  { at: 6.3, finger: 'down' },
  { at: 6.8, finger: 1 },
  { at: 7.5, finger: 0 },
  { at: 8.1, finger: -1 },
  { at: 8.9, finger: 'up' },
];

// Icons in a 22-point box. A body is an outline that fills when its tab is open; an inner line
// lies on a body and is cut out of the filled one; a gap clears the bar's colour under a line.
type Part = { kind: 'body' | 'line' | 'inner' | 'gap'; d: string; width?: number };
const ICONS: Record<TabIcon, Part[]> = {
  home: [{ kind: 'body', d: 'M3.6 9.9 L11 3.4 L18.4 9.9 L18.4 17.6 Q18.4 18.9 17.1 18.9 L4.9 18.9 Q3.6 18.9 3.6 17.6 Z' }],
  search: [
    { kind: 'line', d: 'M16.2 9.9 C16.2 13.38 13.38 16.2 9.9 16.2 C6.42 16.2 3.6 13.38 3.6 9.9 C3.6 6.42 6.42 3.6 9.9 3.6 C13.38 3.6 16.2 6.42 16.2 9.9 Z' },
    { kind: 'line', d: 'M14.6 14.6 L19 19' },
  ],
  tray: [
    { kind: 'body', d: 'M6.2 3.6 L15.8 3.6 Q18.8 3.6 18.8 6.6 L18.8 15.4 Q18.8 18.4 15.8 18.4 L6.2 18.4 Q3.2 18.4 3.2 15.4 L3.2 6.6 Q3.2 3.6 6.2 3.6 Z' },
    { kind: 'inner', d: 'M3.2 11.6 L7.4 11.6 Q7.9 14.6 11 14.6 Q14.1 14.6 14.6 11.6 L18.8 11.6' },
  ],
  compose: [
    { kind: 'body', d: 'M6.2 5.6 L13.4 5.6 Q16.4 5.6 16.4 8.6 L16.4 15.8 Q16.4 18.8 13.4 18.8 L6.2 18.8 Q3.2 18.8 3.2 15.8 L3.2 8.6 Q3.2 5.6 6.2 5.6 Z' },
    { kind: 'gap', d: 'M10 12 L17.8 4.2', width: 6.4 },
    { kind: 'line', d: 'M10 12 L17.8 4.2', width: 2.6 },
  ],
};
const ICON_PATHS = Object.fromEntries(
  Object.entries(ICONS).map(([name, parts]) => [name, parts.map((part) => ({ ...part, path: Skia.Path.MakeFromSVGString(part.d)! }))]),
) as Record<TabIcon, (Part & { path: ReturnType<typeof Skia.Path.Make> })[]>;

/** Where the wheel stands for a finger that asks for `raw`: held back more and more past its ends. */
function rubber(raw: number, last: number) {
  'worklet';
  const over = raw < 0 ? -raw : raw > last ? raw - last : 0;
  const give = (1 - 1 / ((over * 0.55) / RUBBER + 1)) * RUBBER;
  return raw < 0 ? -give : raw > last ? last + give : raw;
}

/** The rows of an entry: an account's own mail, or every account's in turn for the last entry. */
function rowsFor(accounts: Account[], entry: number, tab: number) {
  const own = (account: Account) => account.messages.map((message) => ({ ...message, color: account.color }));
  let rows = entry < accounts.length ? own(accounts[entry]) : [];
  if (entry >= accounts.length) {
    const lists = accounts.map(own).reverse();
    const longest = Math.max(0, ...lists.map((list) => list.length));
    for (let i = 0; i < longest; i++) for (const list of lists) if (list[i]) rows.push(list[i]);
  }
  // A stand-in for the other tabs' own screens: the same mail, starting further down.
  const from = rows.length ? (tab * 2) % rows.length : 0;
  rows = [...rows.slice(from), ...rows.slice(0, from)];
  return rows.slice(0, ROWS);
}

function Icon({ name, active }: { name: TabIcon; active: boolean }) {
  const ink = active ? INK : ICON;
  return (
    <>
      {ICON_PATHS[name].map((part, i) => {
        const stroke = { style: 'stroke', strokeCap: 'round', strokeJoin: 'round' } as const;
        if (part.kind === 'gap') return <Path key={i} path={part.path} {...stroke} strokeWidth={part.width} color={BAR} />;
        if (part.kind === 'inner') return <Path key={i} path={part.path} {...stroke} strokeWidth={STROKE} color={active ? BAR : ink} />;
        if (part.kind === 'line') {
          return <Path key={i} path={part.path} {...stroke} strokeWidth={(part.width ?? STROKE) * (active ? STROKE_ON : 1)} color={ink} />;
        }
        return (
          <Group key={i}>
            {active && <Path path={part.path} color={ink} />}
            <Path path={part.path} {...stroke} strokeWidth={STROKE} color={ink} />
          </Group>
        );
      })}
    </>
  );
}

type Wheel = { index: number; wheel: SharedValue<number>; open: SharedValue<number>; still: boolean };

/** One line of the wheel of addresses: it rides past the slot, bright only while it is in it. */
function Address({ label, right, index, wheel, open, still }: Wheel & { label: string; right: number }) {
  const style = useAnimatedStyle(() => {
    const away = Math.min(1, Math.abs(index - wheel.get()));
    if (still) return { opacity: open.get() * (1 - away), transform: [{ translateX: 0 }, { translateY: 0 }] };
    return {
      opacity: open.get() * (1 - (1 - ADDRESS_AWAY) * away),
      transform: [{ translateX: (1 - open.get()) * ADDRESS_SLIDE }, { translateY: (index - wheel.get()) * ADDRESS_PITCH }],
    };
  });
  return (
    <Animated.Text style={[styles.address, { right }, style]} numberOfLines={1}>
      {label}
    </Animated.Text>
  );
}

/** One badge of the column: a number on the account's colour, or a dot for each account. */
function Badge({ colors, index, wheel, open, still }: Wheel & { colors: string[] }) {
  const style = useAnimatedStyle(() => {
    const scale = 1 + (SWELL - 1) * open.get();
    if (still) return { opacity: 1 - Math.min(1, Math.abs(index - wheel.get())), transform: [{ translateY: 0 }, { scale }] };
    return { opacity: 1, transform: [{ translateY: (index - wheel.get()) * BADGE_PITCH }, { scale }] };
  });
  return (
    <Animated.View style={[styles.badge, style]}>
      {colors.length === 1 ? (
        <View style={[styles.disc, { backgroundColor: colors[0] }]}>
          <Text style={styles.number}>{index + 1}</Text>
        </View>
      ) : (
        colors.map((color, i) => {
          // The dots step down to the right, each over the one before.
          const at = i / (colors.length - 1);
          return <View key={i} style={[styles.dot, { backgroundColor: color, left: at * (BADGE - DOT + 2) - 1, top: 1.5 + at * (BADGE - DOT - 3) }]} />;
        })
      )}
    </Animated.View>
  );
}

/**
 * A mail screen whose tab bar ends in an account badge. Holding the badge turns the whole bar into
 * an account switcher: the icons blur away, the addresses stand in a wheel beside the badge, and
 * dragging up or down scrubs through them while the page follows. A tap on the badge steps to the
 * next account.
 */
export function AccountScrubTabBar({
  accounts = ACCOUNTS,
  allLabel = 'All accounts',
  tabs = TABS,
  autoPlay = false,
  onSelectAccount,
  onSelectTab,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [tab, setTab] = useState(0);
  // The entry under the slot: an account, or `accounts.length` for all of them.
  const [entry, setEntry] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const last = accounts.length;
  const labels = [...accounts.map((account) => account.address), allLabel];

  // 0 as a tab bar, 1 as the account switcher.
  const open = useSharedValue(0);
  // Where the columns of badges and addresses stand, in entries; whole numbers at rest.
  const wheel = useSharedValue(0);
  // The entry nearest the slot, which the page shows.
  const target = useSharedValue(0);
  // The entry the wheel stood on when the hold began.
  const anchor = useSharedValue(0);
  const held = useSharedValue(false);
  // The finger the autoPlay loop draws: down on the badge, how far it has dragged, how visible.
  const auto = useSharedValue(false);
  const finger = useSharedValue(0);
  const shown = useSharedValue(0);
  // The autoPlay loop makes no haptics.
  const silent = useSharedValue(autoPlay);

  const engage = () => {
    'worklet';
    cancelAnimation(wheel);
    anchor.set(target.get());
    held.set(true);
    open.set(withTiming(1, { duration: OPEN_IN * 1000, easing: EASE_OUT, ...NEVER }));
  };

  const scrub = (dy: number) => {
    'worklet';
    const raw = anchor.get() - dy / STEP;
    const nearest = Math.min(last, Math.max(0, Math.round(raw)));
    if (!reducedMotion) wheel.set(rubber(raw, last));
    // Without movement the badge and the address change by a short fade, one step at a time.
    else if (nearest !== target.get()) wheel.set(withTiming(nearest, { duration: PAGE_FADE * 1000, easing: Easing.linear, ...NEVER }));
    target.set(nearest);
  };

  const release = () => {
    'worklet';
    held.set(false);
    wheel.set(reducedMotion ? withTiming(target.get(), { duration: PAGE_FADE * 1000, ...NEVER }) : withSpring(target.get(), { ...SNAP, ...NEVER }));
    open.set(withDelay(CLOSE_AFTER * 1000, withTiming(0, { duration: CLOSE_IN * 1000, easing: EASE_OUT, ...NEVER }), ReduceMotion.Never));
  };

  const select = (index: number) => {
    setEntry(index);
    if (!silent.get() && process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    onSelectAccount?.(index);
  };

  useAnimatedReaction(
    () => target.get(),
    (now, old) => {
      if (old !== null && now !== old) scheduleOnRN(select, now);
    },
  );

  // The drawn finger scrubs through the same code as a real one.
  useAnimatedReaction(
    () => finger.get(),
    (dy, old) => {
      if (auto.get() && old !== null && dy !== old) scrub(dy);
    },
  );

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    silent.set(false);
    if (auto.get()) {
      auto.set(false);
      cancelAnimation(finger);
      release();
    }
    shown.set(withTiming(0, { duration: FINGER_OUT * 1000, ...NEVER }));
    setTouches((n) => n + 1);
  };

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  /** A plain tap on the badge: the next entry, and round to the first after the last. */
  const step = () => {
    const next = (target.get() + 1) % (last + 1);
    target.set(next);
    wheel.set(reducedMotion ? withTiming(next, { duration: PAGE_FADE * 1000, ...NEVER }) : withSpring(next, { ...SNAP, ...NEVER }));
  };

  const press = (index: number) => {
    touched();
    // The icons are not there to be pressed while the bar is the switcher.
    if (open.get() > 0.5 || index === tab) return;
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
    setTab(index);
    onSelectTab?.(index);
  };

  // The finger the autoPlay loop draws does what a real one does, through the same functions.
  const script = {
    down: () => {
      finger.set(0);
      shown.set(withTiming(1, { duration: FINGER_IN * 1000, ...NEVER }));
    },
    hold: () => {
      engage();
      auto.set(true);
    },
    drag: (to: number) => {
      finger.set(withTiming(-to * STEP, { duration: MOVE * 1000, easing: EASE_IN_OUT, ...NEVER }));
    },
    up: () => {
      auto.set(false);
      release();
      shown.set(withTiming(0, { duration: FINGER_OUT * 1000, ...NEVER }));
    },
    holding: () => held.get(),
    /** Back to the first account and the first tab, where the loop starts. */
    reset: () => {
      silent.set(true);
      setTab(0);
      target.set(0);
      wheel.set(withSpring(0, { ...SNAP, ...NEVER }));
    },
  };
  // The loop reads the latest state when its timers fire.
  const latest = useRef(script);
  useEffect(() => {
    latest.current = script;
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      for (const { at, finger: to } of SCRIPT) {
        if (to === 'down') {
          after(origin + at * 1000 - Date.now(), () => latest.current.down());
          after(origin + (at + HOLD) * 1000 - Date.now(), () => latest.current.hold());
        } else if (to === 'up') after(origin + at * 1000 - Date.now(), () => latest.current.up());
        else after(origin + at * 1000 - Date.now(), () => latest.current.drag(to));
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      // A finger that is still holding the badge starts the wait again when it lifts.
      if (latest.current.holding()) return;
      latest.current.reset();
      cycle(Date.now());
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const hold = Gesture.Pan()
    .activateAfterLongPress(HOLD * 1000)
    .onBegin(() => {
      scheduleOnRN(touched);
    })
    .onStart(() => {
      // From here on the drag is the bar's, whatever lies under the finger.
      engage();
      scheduleOnRN(tick);
    })
    .onChange((event) => {
      scrub(event.translationY);
    })
    .onFinalize(() => {
      if (held.get()) release();
      scheduleOnRN(touched);
    });
  const tap = Gesture.Tap()
    .maxDuration(HOLD * 1000)
    .onEnd((_event, success) => {
      if (success) scheduleOnRN(step);
    });
  const gesture = Gesture.Exclusive(hold, tap);

  const bottom = Math.max(BAR_BOTTOM, insets.bottom - 4);
  const barWidth = size.width - BAR_MARGIN * 2;
  const slot = barWidth / (tabs.length + 1);
  const addressRight = slot / 2 + (BADGE * SWELL) / 2 + ADDRESS_GAP;

  const iconBlur = useDerivedValue(() => (reducedMotion ? 0 : ICON_BLUR * open.get()));
  const iconOpacity = useDerivedValue(() => 1 - (reducedMotion ? 1 : 1 - ICON_FADE) * open.get());
  const fingerStyle = useAnimatedStyle(() => ({
    opacity: FINGER_OPACITY * shown.get(),
    transform: [{ translateY: finger.get() }, { scale: 1.25 - 0.25 * shown.get() }],
  }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const rows = rowsFor(accounts, entry, tab);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="light" />

      <View style={[styles.page, { top: insets.top + TITLE_TOP }]}>
        {/* A new tab or account fades in over the old one. */}
        <LayoutAnimationConfig skipEntering>
          <Animated.Text key={tab} style={styles.title} numberOfLines={1} entering={FADE_IN} exiting={FADE_OUT}>
            {tabs[tab].title}
          </Animated.Text>
          <Animated.View
            key={`${tab}-${entry}`}
            style={styles.mail}
            entering={FADE_IN}
            exiting={FADE_OUT}
          >
            <Text style={styles.account} numberOfLines={1}>
              {labels[entry]}
            </Text>
            {rows.map((row, i) => (
              <View key={i} style={styles.row}>
                <View style={[styles.avatar, { backgroundColor: `${row.color}40` }]}>
                  <Text style={[styles.initial, { color: row.color }]}>{row.sender.slice(0, 1)}</Text>
                </View>
                <View style={styles.rowText}>
                  <View style={styles.rowTop}>
                    <Text style={styles.sender} numberOfLines={1}>
                      {row.sender}
                    </Text>
                    <Text style={styles.time}>{row.time}</Text>
                  </View>
                  <Text style={styles.subject} numberOfLines={1}>
                    {row.subject}
                  </Text>
                </View>
              </View>
            ))}
          </Animated.View>
        </LayoutAnimationConfig>
      </View>

      <View style={[styles.bar, { left: BAR_MARGIN, width: barWidth, bottom }]}>
        <Canvas style={styles.fill}>
          <Group
            layer={
              <Paint opacity={iconOpacity}>
                <Blur blur={iconBlur} />
              </Paint>
            }
          >
            {tabs.map((item, i) => (
              <Group key={i} transform={[{ translateX: slot * (i + 0.5) - ICON_BOX / 2 }, { translateY: (BAR_HEIGHT - ICON_BOX) / 2 }]}>
                <Icon name={item.icon} active={i === tab} />
              </Group>
            ))}
          </Group>
        </Canvas>
        {tabs.map((item, i) => (
          <Pressable
            key={i}
            style={[styles.slot, { left: slot * i, width: slot }]}
            accessibilityRole="tab"
            accessibilityLabel={item.title}
            accessibilityState={{ selected: i === tab }}
            onPress={() => press(i)}
          />
        ))}

        {labels.map((label, i) => (
          <Address key={i} label={label} right={addressRight} index={i} wheel={wheel} open={open} still={reducedMotion} />
        ))}

        <GestureDetector gesture={gesture}>
          <View
            style={[styles.slot, { left: slot * tabs.length, width: slot }]}
            accessible
            accessibilityRole="button"
            accessibilityLabel="Account"
            accessibilityValue={{ text: labels[entry] }}
            accessibilityHint="Tap for the next account, or hold and drag to pick one"
          >
            {labels.map((_label, i) => (
              <Badge
                key={i}
                colors={i < last ? [accounts[i].color] : accounts.slice(0, 3).map((account) => account.color).reverse()}
                index={i}
                wheel={wheel}
                open={open}
                still={reducedMotion}
              />
            ))}
          </View>
        </GestureDetector>
      </View>

      {autoPlay && (
        <Animated.View
          pointerEvents="none"
          style={[styles.finger, { left: BAR_MARGIN + barWidth - slot / 2 - FINGER / 2, bottom: bottom + (BAR_HEIGHT - FINGER) / 2 }, fingerStyle]}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  page: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    overflow: 'hidden',
  },
  title: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    top: 0,
    color: INK,
    fontSize: 34,
    lineHeight: 41,
    fontWeight: '700',
    includeFontPadding: false,
  },
  mail: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 45,
  },
  account: {
    marginHorizontal: MARGIN,
    marginBottom: 16,
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
    includeFontPadding: false,
  },
  row: {
    height: ROW,
    paddingHorizontal: MARGIN,
    flexDirection: 'row',
    alignItems: 'center',
  },
  avatar: {
    width: AVATAR,
    height: AVATAR,
    borderRadius: AVATAR / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  initial: {
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '600',
    includeFontPadding: false,
  },
  rowText: {
    flex: 1,
    height: ROW,
    marginLeft: 12,
    justifyContent: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: HAIRLINE,
  },
  rowTop: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  sender: {
    flex: 1,
    color: INK,
    fontSize: 16,
    lineHeight: 21,
    fontWeight: '600',
    includeFontPadding: false,
  },
  time: {
    marginLeft: 8,
    color: FAINT,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  subject: {
    marginTop: 2,
    color: MUTE,
    fontSize: 15,
    lineHeight: 20,
    includeFontPadding: false,
  },
  bar: {
    position: 'absolute',
    height: BAR_HEIGHT,
    borderRadius: BAR_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: BAR,
    overflow: 'hidden',
  },
  slot: {
    position: 'absolute',
    top: 0,
    height: BAR_HEIGHT,
  },
  address: {
    position: 'absolute',
    top: (BAR_HEIGHT - 20) / 2,
    color: ADDRESS,
    fontSize: 15,
    lineHeight: 20,
    includeFontPadding: false,
  },
  badge: {
    position: 'absolute',
    left: '50%',
    marginLeft: -BADGE / 2,
    top: (BAR_HEIGHT - BADGE) / 2,
    width: BADGE,
    height: BADGE,
  },
  disc: {
    width: BADGE,
    height: BADGE,
    borderRadius: BADGE / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  number: {
    color: BAR,
    fontSize: 12,
    lineHeight: 15,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  dot: {
    position: 'absolute',
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    borderWidth: 1.5,
    borderColor: BAR,
  },
  finger: {
    position: 'absolute',
    width: FINGER,
    height: FINGER,
    borderRadius: FINGER / 2,
    backgroundColor: '#FFFFFF',
  },
});
