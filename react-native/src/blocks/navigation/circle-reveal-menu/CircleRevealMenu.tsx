import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  interpolateColor,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export type Section = {
  /** The item of the menu. */
  title: string;
  /** The two lines at the top of its page. */
  headline: string;
  line: string;
  /** The captions under the photos: the current project and the next. */
  captions: [string, string];
};

type Props = {
  sections?: Section[];
  /** Three photos for the row on the page; each section starts the row with another one. */
  photos?: string[];
  /** The two actions at the bottom of the menu. */
  actions?: [string, string];
  menuColor?: string;
  /** Opens the menu, picks an item and closes it by itself every LOOP seconds. */
  autoPlay?: boolean;
  onSelect?: (index: number) => void;
  onAction?: (index: number) => void;
};

const SECTIONS: Section[] = [
  { title: 'Works', headline: 'Smart & clean design', line: 'That’s Norde.studio', captions: ['Architects concept', 'Weekend Sprint'] },
  { title: 'Process', headline: 'How we work', line: 'Four steps, no surprises', captions: ['Discover and define', 'Design and build'] },
  { title: 'About', headline: 'A small studio', line: 'Six people in Tallinn', captions: ['The team', 'The workshop'] },
  { title: 'Contact', headline: 'Say hello', line: 'hello@norde.studio', captions: ['New projects', 'Open roles'] },
];

// Black and white, as the page asks for, and twice the size of the widest place in the row.
const photo = (id: string) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=374&h=320&q=80&sat=-100`;
const PHOTOS = [photo('photo-1496442226666-8d4d0e62e6e9'), photo('photo-1513635269975-59663e0ac1ad'), photo('photo-1540959733332-eab4deabeeaf')];

const PAPER = '#FFFFFF';
const INK = '#111111';
const GREY = '#9A9A9A';
const SLAB = '#B9B4AE';
const TILES = ['#1F1E1C', '#3A3835'];
const MENU = '#124A3B';
const MUTED = '#5E8C7C';
const HAIRLINE = 'rgba(255,255,255,0.1)';

const MARGIN = 24;
// The top bar under the status bar, and the menu button's lines in it.
const BAR_TOP = 8;
const BAR = 44;
const LINE_WIDTH = 18;
const LINE = 2;
const LINE_GAP = 4.5;
// The button's centre from the right edge.
const BUTTON_INSET = 33;
const LOGO = 24;
// Everything hangs from the bar's top, at these distances on a screen with DESIGN points under
// the bar; a shorter screen draws them closer together.
const DESIGN = 804;
const HEADLINE_TOP = 117;
const PHOTOS_TOP = 271;
const PHOTO_HEIGHT = 160;
const PHOTO_WIDTHS = [96, 110, 187];
const PHOTO_GAP = 8;
const CAPTIONS_TOP = 603;
const SLAB_TOP = 690;
const ITEMS_TOP = 190;
const ITEM_PITCH = 84;
const ITEM_LEFT = 30;
const STRIP = 64;
// The least bottom inset the strip is drawn for.
const STRIP_INSET = 34;
// The page under the circle is this much smaller and lower.
const PAGE_SHRINK = 0.04;
const PAGE_SINK = 12;
const RISE = 16;
// The logo turns white while this much of the circle's edge passes over it.
const EDGE = 24;

export const LOOP = 9;
// Opening: the circle, and then the items one after another, the strip last.
const CIRCLE_IN = 0.55;
const LIST_AFTER = 0.35;
const LIST_IN = 0.55;
const STAGGER = 0.07;
const ITEM_IN = 0.3;
const STRIP_AFTER = 0.33;
// Closing: the items in reverse, and then the circle.
const LIST_OUT = 0.4;
const CIRCLE_AFTER = 0.25;
const CIRCLE_OUT = 0.45;
// A chosen item turns white, and the menu closes a moment later.
const SWAP = 0.2;
const CHOSEN_HOLD = 0.4;
const EASE_IN_OUT = Easing.bezier(0.65, 0, 0.35, 1);
// A straight mix of two colours: the default, corrected for gamma, is too light half way.
const PLAIN = { gamma: 1 };
// The fades are part of how the menu works, so they play under reduced motion too.
const NEVER = { reduceMotion: ReduceMotion.Never };
// The loop starts again this long after the last touch.
const USER_HOLD = 3;
// What the autoPlay loop presses: the button, and an item of the open menu.
const SCRIPT: { at: number; choose?: number }[] = [{ at: 0.8 }, { at: 3.0, choose: 1 }, { at: 5.6 }, { at: 7.4, choose: 0 }];

function easeOut(t: number) {
  'worklet';
  const u = 1 - Math.min(1, Math.max(0, t));
  return 1 - u * u * u;
}

type ItemProps = {
  label: string;
  index: number;
  list: SharedValue<number>;
  section: SharedValue<number>;
  previous: SharedValue<number>;
  swap: SharedValue<number>;
  still: boolean;
  disabled: boolean;
  selected: boolean;
  onPress: () => void;
};

function Item({ label, index, list, section, previous, swap, still, disabled, selected, onPress }: ItemProps) {
  const style = useAnimatedStyle(() => {
    const shown = easeOut((list.get() * LIST_IN - index * STAGGER) / ITEM_IN);
    // The chosen item turns white as the one before it turns muted.
    const white = section.get() === index ? swap.get() : previous.get() === index ? 1 - swap.get() : 0;
    return { opacity: shown, color: interpolateColor(white, [0, 1], [MUTED, '#FFFFFF'], 'RGB', PLAIN), transform: [{ translateY: still ? 0 : (1 - shown) * RISE }] };
  });
  return (
    <Pressable style={styles.item} disabled={disabled} accessibilityRole="menuitem" accessibilityState={{ selected }} onPress={onPress}>
      <Animated.Text style={[styles.itemLabel, style]} numberOfLines={1}>
        {label}
      </Animated.Text>
    </Pressable>
  );
}

/**
 * A studio page with a menu button at its top right. The menu is a dark green sheet that grows
 * out of the button as a circle until it covers the screen, while the button's three lines turn
 * into a cross; the items then rise in one after another. Choosing one closes the menu onto that
 * section's page.
 */
export function CircleRevealMenu({
  sections = SECTIONS,
  photos = PHOTOS,
  actions = ['Call', 'Mail'],
  menuColor = MENU,
  autoPlay = false,
  onSelect,
  onAction,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const size = laidOut ?? window;
  const [open, setOpen] = useState(false);
  const [current, setCurrent] = useState(0);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // How much of the way the circle has grown, and how far the list of items has come in.
  const circle = useSharedValue(0);
  const list = useSharedValue(0);
  // The chosen section, the one before it, and how far the colours have changed over.
  const section = useSharedValue(0);
  const previous = useSharedValue(0);
  const swap = useSharedValue(1);
  // The autoPlay loop makes no haptics.
  const silent = useSharedValue(autoPlay);
  const closing = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const top = insets.top + BAR_TOP;
  const under = (offset: number) => top + offset * Math.min(1, (size.height - top) / DESIGN);
  // The circle grows from the button's centre until it has the farthest corner.
  const button = { x: size.width - BUTTON_INSET, y: top + BAR / 2 };
  const reach = Math.hypot(Math.max(button.x, size.width - button.x), Math.max(button.y, size.height - button.y));
  const logoAway = button.x - (MARGIN + LOGO / 2);

  const show = () => {
    clearTimeout(closing.current);
    setOpen(true);
    circle.set(withTiming(1, { duration: CIRCLE_IN * 1000, easing: EASE_IN_OUT, ...NEVER }));
    list.set(withDelay(LIST_AFTER * 1000, withTiming(1, { duration: LIST_IN * 1000, easing: Easing.linear, ...NEVER }), ReduceMotion.Never));
  };

  const hide = () => {
    clearTimeout(closing.current);
    setOpen(false);
    list.set(withTiming(0, { duration: LIST_OUT * 1000, easing: Easing.linear, ...NEVER }));
    circle.set(withDelay(CIRCLE_AFTER * 1000, withTiming(0, { duration: CIRCLE_OUT * 1000, easing: EASE_IN_OUT, ...NEVER }), ReduceMotion.Never));
  };

  /** An item of the menu: it turns white, and the menu closes onto its page a moment later. */
  const choose = (index: number) => {
    if (index !== current) {
      previous.set(section.get());
      section.set(index);
      swap.set(0);
      swap.set(withTiming(1, { duration: SWAP * 1000, easing: Easing.linear, ...NEVER }));
      setCurrent(index);
      onSelect?.(index);
    }
    clearTimeout(closing.current);
    closing.current = setTimeout(hide, CHOSEN_HOLD * 1000);
  };

  useEffect(() => () => clearTimeout(closing.current), []);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    silent.set(false);
    setTouches((n) => n + 1);
  };

  const tick = () => {
    if (!silent.get() && process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // The loop presses what a finger presses, through the same functions.
  const script = {
    toggle: () => (open ? hide() : show()),
    choose,
    /** Back to the first section with the menu closed, where the loop starts. */
    reset: () => {
      silent.set(true);
      if (open) hide();
      if (current !== 0) {
        previous.set(section.get());
        section.set(0);
        setCurrent(0);
      }
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
      for (const step of SCRIPT) {
        after(origin + step.at * 1000 - Date.now(), () => (step.choose === undefined ? latest.current.toggle() : latest.current.choose(step.choose)));
      }
      after(origin + LOOP * 1000 - Date.now(), () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      latest.current.reset();
      // The menu has to be gone before the loop presses the button again.
      after((LIST_OUT + CIRCLE_OUT) * 1000, () => cycle(Date.now()));
    };
    if (touches === 0) cycle(Date.now());
    else after(USER_HOLD * 1000, rest);
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  // The radius of the circle, in points.
  const radius = useDerivedValue(() => reach * circle.get());
  const discStyle = useAnimatedStyle(() =>
    // Without movement the sheet fades in whole.
    reducedMotion ? { opacity: circle.get(), transform: [{ scale: 1 }] } : { opacity: circle.get() > 0 ? 1 : 0, transform: [{ scale: Math.max(circle.get(), 0.0001) }] },
  );
  const pageStyle = useAnimatedStyle(() => ({
    transform: reducedMotion ? [{ translateY: 0 }, { scale: 1 }] : [{ translateY: PAGE_SINK * circle.get() }, { scale: 1 - PAGE_SHRINK * circle.get() }],
  }));
  const stripStyle = useAnimatedStyle(() => ({ opacity: easeOut((list.get() * LIST_IN - STRIP_AFTER) / (LIST_IN - STRIP_AFTER)) }));

  // The button's lines and the logo turn white when the circle's edge has passed them.
  const overButton = useDerivedValue(() => (reducedMotion ? circle.get() : Math.min(1, radius.get() / (LINE_WIDTH / 2 + 5))));
  const overLogo = useDerivedValue(() => (reducedMotion ? circle.get() : Math.min(1, Math.max(0, (radius.get() - (logoAway - EDGE / 2)) / EDGE))));
  const logoStyle = useAnimatedStyle(() => ({ backgroundColor: interpolateColor(overLogo.get(), [0, 1], [INK, '#FFFFFF'], 'RGB', PLAIN) }));
  // The three lines become a cross: the outer two turn and meet in the middle, the middle one goes.
  const cross = useDerivedValue(() => (reducedMotion ? (circle.get() > 0.5 ? 1 : 0) : Math.min(1, circle.get() * 2.5)));
  const ink = useDerivedValue(() => interpolateColor(overButton.get(), [0, 1], [INK, '#FFFFFF'], 'RGB', PLAIN));
  const upper = useAnimatedStyle(() => ({
    backgroundColor: ink.get(),
    transform: [{ translateY: -(LINE_GAP + LINE) * (1 - cross.get()) }, { rotate: `${45 * cross.get()}deg` }],
  }));
  const middle = useAnimatedStyle(() => ({ backgroundColor: ink.get(), opacity: 1 - cross.get() }));
  const lower = useAnimatedStyle(() => ({
    backgroundColor: ink.get(),
    transform: [{ translateY: (LINE_GAP + LINE) * (1 - cross.get()) }, { rotate: `${-45 * cross.get()}deg` }],
  }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setLaidOut({ width, height });
  };

  const now = sections[Math.min(current, sections.length - 1)];
  const stripInset = Math.max(0, insets.bottom - STRIP_INSET);

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style={open ? 'light' : 'dark'} />

      <Animated.View style={[StyleSheet.absoluteFill, pageStyle]} accessibilityElementsHidden={open} importantForAccessibility={open ? 'no-hide-descendants' : 'auto'}>
        <View style={[styles.headline, { top: under(HEADLINE_TOP) }]}>
          <Text style={[styles.headlineText, styles.bold]} numberOfLines={1}>
            {now.headline}
          </Text>
          <Text style={styles.headlineText} numberOfLines={1}>
            {now.line}
          </Text>
        </View>
        {/* The row runs off the right edge. */}
        <View style={[styles.photos, { top: under(PHOTOS_TOP) }]}>
          {PHOTO_WIDTHS.map((width, i) => (
            <Image key={i} source={{ uri: photos[(i + current) % photos.length] }} style={{ width, height: PHOTO_HEIGHT, backgroundColor: '#E4E2DE' }} contentFit="cover" transition={200} />
          ))}
        </View>
        <View style={[styles.captions, { top: under(CAPTIONS_TOP) }]}>
          <View>
            <Text style={styles.caption}>{now.captions[0]}</Text>
            <View style={styles.dot} />
          </View>
          <Text style={[styles.caption, styles.next]}>{now.captions[1]}</Text>
        </View>
        <View style={[styles.slab, { top: under(SLAB_TOP) }]}>
          <View style={[styles.tile, { width: 72, backgroundColor: TILES[0] }]} />
          <View style={[styles.tile, { width: 60, backgroundColor: TILES[1] }]} />
        </View>
      </Animated.View>

      <Animated.View
        pointerEvents="none"
        style={[styles.disc, { left: button.x - reach, top: button.y - reach, width: reach * 2, height: reach * 2, borderRadius: reach, backgroundColor: menuColor }, discStyle]}
      />

      <View style={StyleSheet.absoluteFill} pointerEvents={open ? 'box-none' : 'none'} accessibilityElementsHidden={!open} importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}>
        <View style={[styles.items, { top: under(ITEMS_TOP) }]}>
          {sections.map((item, i) => (
            <Item
              key={i}
              label={item.title}
              index={i}
              list={list}
              section={section}
              previous={previous}
              swap={swap}
              still={reducedMotion}
              disabled={!open}
              selected={i === current}
              onPress={() => {
                touched();
                tick();
                choose(i);
              }}
            />
          ))}
        </View>
        <Animated.View style={[styles.strip, { height: STRIP + stripInset, paddingBottom: stripInset }, stripStyle]}>
          {actions.map((label, i) => (
            <Pressable
              key={i}
              style={[styles.action, i > 0 && styles.divided]}
              disabled={!open}
              accessibilityRole="button"
              onPress={() => {
                touched();
                tick();
                onAction?.(i);
              }}
            >
              <Text style={styles.actionLabel}>{label}</Text>
            </Pressable>
          ))}
        </Animated.View>
      </View>

      <View style={[styles.bar, { top }]} pointerEvents="box-none">
        <View style={styles.logo} accessibilityLabel="Norde.studio">
          <Animated.View style={[styles.stroke, logoStyle]} />
          <Animated.View style={[styles.stroke, styles.second, logoStyle]} />
        </View>
        <Pressable
          style={styles.button}
          accessibilityRole="button"
          accessibilityLabel={open ? 'Close menu' : 'Menu'}
          onPress={() => {
            touched();
            tick();
            if (open) hide();
            else show();
          }}
        >
          <Animated.View style={[styles.line, upper]} />
          <Animated.View style={[styles.line, middle]} />
          <Animated.View style={[styles.line, lower]} />
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAPER,
    overflow: 'hidden',
  },
  headline: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
  },
  headlineText: {
    color: INK,
    fontSize: 30,
    lineHeight: 36,
    includeFontPadding: false,
  },
  bold: {
    fontWeight: '700',
  },
  photos: {
    position: 'absolute',
    left: 0,
    flexDirection: 'row',
    gap: PHOTO_GAP,
  },
  captions: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  caption: {
    color: INK,
    fontSize: 15,
    lineHeight: 19,
    includeFontPadding: false,
  },
  next: {
    color: GREY,
  },
  dot: {
    marginTop: 6,
    width: 4,
    height: 4,
    borderRadius: 2,
    backgroundColor: INK,
    alignSelf: 'center',
  },
  slab: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    bottom: 0,
    paddingLeft: 28,
    paddingTop: 36,
    flexDirection: 'row',
    gap: 8,
    backgroundColor: SLAB,
  },
  tile: {
    height: 56,
  },
  disc: {
    position: 'absolute',
  },
  items: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
  item: {
    height: ITEM_PITCH,
    paddingLeft: ITEM_LEFT,
    justifyContent: 'center',
  },
  itemLabel: {
    fontSize: 50,
    lineHeight: 60,
    includeFontPadding: false,
  },
  strip: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    borderTopWidth: 1,
    borderTopColor: HAIRLINE,
  },
  action: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  divided: {
    borderLeftWidth: 1,
    borderLeftColor: HAIRLINE,
  },
  actionLabel: {
    color: '#FFFFFF',
    fontSize: 15,
    lineHeight: 19,
    fontWeight: '500',
    includeFontPadding: false,
  },
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: BAR,
  },
  logo: {
    position: 'absolute',
    left: MARGIN,
    top: (BAR - LOGO) / 2,
    width: LOGO,
    height: LOGO,
  },
  // Two slanted strokes side by side.
  stroke: {
    position: 'absolute',
    left: 4,
    top: 0,
    width: 6,
    height: LOGO,
    transform: [{ rotate: '20deg' }],
  },
  second: {
    left: 14.5,
  },
  button: {
    position: 'absolute',
    right: BUTTON_INSET - BAR / 2,
    top: 0,
    width: BAR,
    height: BAR,
    alignItems: 'center',
    justifyContent: 'center',
  },
  line: {
    position: 'absolute',
    width: LINE_WIDTH,
    height: LINE,
    borderRadius: LINE / 2,
  },
});
