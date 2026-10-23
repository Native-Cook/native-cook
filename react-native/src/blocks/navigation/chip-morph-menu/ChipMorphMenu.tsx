import { MaterialIcons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Pressable, ScrollView, type StyleProp, StyleSheet, Text, View, type ViewStyle, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  FadeIn,
  FadeOut,
  ReduceMotion,
  interpolateColor,
  useAnimatedScrollHandler,
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

type IconName = keyof typeof MaterialIcons.glyphMap;

export type Topic = { label: string; emoji: string };

export type Category = {
  name: string;
  icon: IconName;
  /** How many members are online in it. */
  members: number;
  topics: Topic[];
};

export type Person = { name: string; photo: string; interest: string };

type Props = {
  /** The chips; each opens into a card of its topics. */
  categories?: Category[];
  /** The profile cards shown while no topic is chosen. */
  people?: Person[];
  /** The profile cards shown for a chosen topic, which is written on them. */
  matches?: Person[];
  /** Photos of the row at the bottom. */
  takes?: string[];
  title?: string;
  takesTitle?: string;
  /** Who the line under the header says came online. */
  online?: string;
  /** Opens a chip, picks a topic, opens another and clears the choice by itself every LOOP seconds. */
  autoPlay?: boolean;
  /** Called when a topic is picked, and with null when the dark chip is tapped to clear it. */
  onSelect?: (category: Category, topic: Topic | null) => void;
};

const photo = (id: string, width: number, height: number) =>
  `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${width}&h=${height}&q=80`;

const CATEGORIES: Category[] = [
  {
    name: 'Career',
    icon: 'work',
    members: 12,
    topics: [
      { label: 'Speak with confidence', emoji: '🎤' },
      { label: 'Plan your next move', emoji: '🧭' },
      { label: 'Get ahead at work', emoji: '🏆' },
      { label: 'Side projects', emoji: '💻' },
    ],
  },
  {
    name: 'Wellness',
    icon: 'spa',
    members: 11,
    topics: [
      { label: 'Sleep better', emoji: '🌙' },
      { label: 'Skincare routine', emoji: '🍃' },
      { label: 'Manage stress', emoji: '🌿' },
      { label: 'Fitness & nutrition', emoji: '🍎' },
    ],
  },
  {
    name: 'Travel',
    icon: 'send',
    members: 7,
    topics: [
      { label: 'Weekend escapes', emoji: '⛺' },
      { label: 'Travel on a budget', emoji: '🎟' },
      { label: 'Going solo', emoji: '🎒' },
      { label: 'Food to fly for', emoji: '🍜' },
    ],
  },
  {
    name: 'Money',
    icon: 'monetization-on',
    members: 9,
    topics: [
      { label: 'Budget basics', emoji: '💰' },
      { label: 'Save for a trip', emoji: '🏦' },
      { label: 'Investing 101', emoji: '📈' },
      { label: 'Split the bills', emoji: '🧾' },
    ],
  },
];

const PEOPLE: Person[] = [
  { name: 'Noor Haddad', photo: photo('1544005313-94ddf0286df2', 360, 360), interest: 'Meeting new people' },
  { name: 'Leo Marsh', photo: photo('1506794778202-cad84cf45f1d', 360, 360), interest: 'Weekend escapes' },
  { name: 'Mina Okoye', photo: photo('1487412720507-e7ab37603c6f', 360, 360), interest: 'Sleep better' },
];

const MATCHES: Person[] = [
  { name: 'Jonas Reed', photo: photo('1535713875002-d1d0cf377fde', 360, 360), interest: '' },
  { name: 'Clara Wynn', photo: photo('1517841905240-472988babdf9', 360, 360), interest: '' },
  { name: 'Theo Barnes', photo: photo('1531891437562-4301cf35b7e4', 360, 360), interest: '' },
];

const TAKES = [
  photo('1522202176988-66273c2fd55f', 324, 600),
  photo('1521572267360-ee0c2909d518', 324, 600),
  photo('1504593811423-6dd665756598', 324, 600),
  photo('1526772662000-3f88f10405ff', 324, 600),
];

const PAGE = '#FFFFFF';
const INK = '#1C1C1E';
const MUTE = '#8E8E93';
const SOFT = '#F4F4F4';
const PANEL = '#F6F6F6';
const RULE = '#EFEFEF';
const GREEN = '#2DBE6C';
const PALE = '#DDF7E8';
const PALE_EDGE = '#BFEFD3';
// The little faces of the members line, and of the people in the header.
const FACES = ['#F2B8A0', '#A9C7F5', '#BFE3B4'];
const FACE_ONLINE = '#F5C9A8';
const FACE_YOU = '#C9C2F0';

const MARGIN = 20;
// The page, from the safe area's top.
const HEADER_TOP = 14;
const LIVE = 40;
const ROUND = 38;
const ONLINE_TOP = 87;
const CHIPS_TOP = 144;
const HEADING_TOP = 217;
const PEOPLE_TOP = 263;
const TAKES_TOP = 585;
const THUMBS_TOP = 629;
// A chip: its glyph, its label and a chevron.
const CHIP = 38;
const CHIP_GAP = 10;
const CHIP_PAD = 12;
const CHIP_END = 10;
const GLYPH = 18;
const LABEL_GAP = 6;
const CHEVRON = 16;
const CHEVRON_GAP = 4;
const TITLE_LINE = 20;
// What a chip is wider than its glyph and label by.
const TITLE_TRIM = CHIP_PAD + CHEVRON_GAP + CHEVRON + CHIP_END;
const PERSON_WIDTH = 247;
const PERSON_HEIGHT = 302;
const PERSON_GAP = 14;
const PORTRAIT = 118;
const RING = 4;
const THUMB_WIDTH = 108;
const THUMB_HEIGHT = 200;
const THUMB_GAP = 10;
const TABS_WIDTH = 184;
const TABS_HEIGHT = 54;
// The card a chip opens into, and where its parts sit from its top.
const CARD_WIDTH = 340;
const CARD_HEIGHT = 388;
const CARD_TOP = 152;
const CARD_RADIUS = 28;
const TITLE_AT = 38;
const TITLE_GROWN = 17 / 14;
const MEMBERS_AT = 82;
const TOPICS_AT = 144;
const TOPIC_STEP = 58;
const TOPIC = 40;

// The chip's frame springs to the card's and back: response 0.4 s, about 3% past its target.
const SPRING = { mass: 1, stiffness: 247, damping: 23.4 };
// The page under an open card.
const COVER = 0.72;
const COVER_FOR = 300;
// The members line and the topics come up one after another, and leave together.
const RISE_AFTER = 150;
const RISE_STEP = 60;
const RISE_FOR = 400;
const RISE_BY = 10;
const RISE_EASE = Easing.bezier(0.23, 1, 0.32, 1);
const LEAVE_FOR = 120;
const CHEVRON_GONE = 0.3;
// The card is white but for the last part of its way back, where it takes the chip's colour.
const TINT_BY = 0.4;
// The heading and the profile cards change with the chosen topic.
const SWAP = 250;
const PRESS = 0.95;

/** autoPlay: Career, a topic, Wellness and out again, then the choice is cleared. */
export const LOOP = 10;
const OPEN_AT = 0.8;
const PRESS_AT = 2.45;
const PICK_AT = 2.6;
const PICKED = 2;
const AGAIN_AT = 4.4;
const OUT_AT = 6.2;
const CLEAR_AT = 7.4;
/** After a touch autoPlay waits this long, and for the card to be closed, before it starts over. */
const USER_HOLD = 3;

function mix(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}

/** A part of the card that comes up after the card has started to open: `order` sets its turn. */
function Rise({ shown, order, style, children }: { shown: boolean; order: number; style?: StyleProp<ViewStyle>; children: ReactNode }) {
  const risen = useSharedValue(0);

  useEffect(() => {
    risen.set(
      shown
        ? withDelay(RISE_AFTER + order * RISE_STEP, withTiming(1, { duration: RISE_FOR, easing: RISE_EASE }))
        : withTiming(0, { duration: LEAVE_FOR }),
    );
  }, [shown, order, risen]);

  const risenStyle = useAnimatedStyle(() => ({
    opacity: risen.get(),
    transform: [{ translateY: RISE_BY * (1 - risen.get()) }],
  }));

  return <Animated.View style={[style, risenStyle]}>{children}</Animated.View>;
}

function Chip({
  category,
  dark,
  hidden,
  onLayout,
  onPress,
}: {
  category: Category;
  dark: boolean;
  hidden: boolean;
  onLayout: (event: LayoutChangeEvent) => void;
  onPress: () => void;
}) {
  const ink = dark ? '#FFFFFF' : INK;
  return (
    <Pressable
      onLayout={onLayout}
      onPress={onPress}
      style={[styles.chip, dark && styles.chipDark, hidden && styles.hidden]}
      accessibilityRole="button"
      accessibilityLabel={dark ? `${category.name}. Clear the topic` : `${category.name}. Show topics`}
      accessibilityState={{ selected: dark }}
    >
      <MaterialIcons name={category.icon} size={GLYPH} color={ink} />
      <Text style={[styles.chipLabel, { color: ink }]} numberOfLines={1}>
        {category.name}
      </Text>
      <MaterialIcons name="keyboard-arrow-down" size={CHEVRON} color={dark ? '#FFFFFF' : MUTE} style={styles.chevron} />
    </Pressable>
  );
}

/**
 * A community page with a row of category chips. A chip does not drop a menu: it swells into a
 * white card in the middle of the screen, over the darkened page, with its glyph and label as the
 * card's title and the category's topics under it. A topic, or a tap outside, sends the card back
 * into the chip; a picked topic turns the chip dark and changes the heading and the profile cards.
 */
export function ChipMorphMenu({
  categories = CATEGORIES,
  people = PEOPLE,
  matches = MATCHES,
  takes = TAKES,
  title = 'For your first chat',
  takesTitle = 'Member Takes',
  online = 'Asha Verma',
  autoPlay = false,
  onSelect,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width } = laidOut ?? window;
  // The chip whose card is on the screen, opening, open or closing.
  const [active, setActive] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  // The topic that was picked, and the chip it belongs to.
  const [choice, setChoice] = useState<{ category: number; topic: number } | null>(null);
  const [tab, setTab] = useState(0);
  // The topic held down by the autoPlay loop.
  const [autoPressed, setAutoPressed] = useState<number | null>(null);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // Where each chip sits in its row, before the row's scroll.
  const frames = useRef<({ x: number; width: number } | undefined)[]>([]);
  const chips = useRef<Animated.ScrollView>(null);
  const cards = useRef<ScrollView>(null);
  const scrolled = useSharedValue(0);
  const chipX = useSharedValue(0);
  const chipWidth = useSharedValue(0);
  // 0 is the chip, 1 the card.
  const morph = useSharedValue(0);
  const cover = useSharedValue(0);

  const cardWidth = Math.min(CARD_WIDTH, width - MARGIN * 2);
  const cardX = (width - cardWidth) / 2;
  const cardY = insets.top + CARD_TOP;
  const chipY = insets.top + CHIPS_TOP;
  const category = active === null ? null : categories[active];
  // The card goes back into a dark chip when its topic has just been picked.
  const dark = active !== null && choice?.category === active;
  const chosen = choice === null ? null : categories[choice.category].topics[choice.topic];

  const closed = () => setActive(null);

  const show = (index: number) => {
    const frame = frames.current[index];
    if (!frame) return;
    chipX.set(frame.x);
    chipWidth.set(frame.width);
    setActive(index);
    setOpen(true);
    cover.set(withTiming(1, { duration: COVER_FOR, reduceMotion: ReduceMotion.Never }));
    morph.set(withSpring(1, SPRING));
  };

  const close = () => {
    setOpen(false);
    // Reduced motion: the card does not travel, it fades with the cover.
    cover.set(
      withTiming(0, { duration: COVER_FOR, reduceMotion: ReduceMotion.Never }, (finished) => {
        if (finished && reducedMotion) scheduleOnRN(closed);
      }),
    );
    morph.set(
      withSpring(0, SPRING, (finished) => {
        if (finished && !reducedMotion) scheduleOnRN(closed);
      }),
    );
  };

  const choose = (index: number, topic: number | null) => {
    setChoice(topic === null ? null : { category: index, topic });
    onSelect?.(categories[index], topic === null ? null : categories[index].topics[topic]);
  };

  const pressChip = (index: number) => {
    // A card on its way back does not hold up the next one.
    if (active !== null && open) return;
    tick();
    if (choice?.category === index) choose(index, null);
    else show(index);
  };

  const pressTopic = (topic: number) => {
    if (active === null || !open) return;
    tick();
    choose(active, topic);
    close();
  };

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ show, close, choose, active });
  useEffect(() => {
    latest.current = { show, close, choose, active };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const after = (ms: number, run: () => void) => timers.push(setTimeout(run, ms));
    const cycle = (origin: number) => {
      const at = (seconds: number, run: () => void) => after(origin + seconds * 1000 - Date.now(), run);
      at(OPEN_AT, () => latest.current.show(0));
      at(PRESS_AT, () => setAutoPressed(PICKED));
      at(PICK_AT, () => {
        setAutoPressed(null);
        latest.current.choose(0, PICKED);
        latest.current.close();
      });
      at(AGAIN_AT, () => latest.current.show(1));
      at(OUT_AT, () => latest.current.close());
      at(CLEAR_AT, () => latest.current.choose(0, null));
      at(LOOP, () => cycle(origin + LOOP * 1000));
    };
    const rest = () => {
      if (latest.current.active !== null) return after(100, rest);
      // The loop starts over from the page as it was given.
      setChoice(null);
      setTab(0);
      chips.current?.scrollTo({ x: 0, animated: true });
      cards.current?.scrollTo({ x: 0, animated: true });
      cycle(Date.now());
    };
    if (touches === 0) {
      cycle(Date.now());
    } else {
      after(USER_HOLD * 1000, () => {
        if (latest.current.active !== null) latest.current.close();
        rest();
      });
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
  const touched = () => {
    setAutoPressed(null);
    setTouches((n) => n + 1);
  };

  const onScroll = useAnimatedScrollHandler((event) => {
    scrolled.set(event.contentOffset.x);
  });

  // Reduced motion keeps the card in its place and fades it.
  const grown = useDerivedValue(() => (reducedMotion ? 1 : morph.get()));
  const left = useDerivedValue(() => mix(chipX.get() - scrolled.get(), cardX, grown.get()));
  const top = useDerivedValue(() => mix(chipY, cardY, grown.get()));

  const frameStyle = useAnimatedStyle(() => {
    const g = grown.get();
    return {
      left: left.get(),
      top: top.get(),
      width: mix(chipWidth.get(), cardWidth, g),
      height: mix(CHIP, CARD_HEIGHT, g),
      borderRadius: mix(CHIP / 2, CARD_RADIUS, clamp01(g)),
      backgroundColor: interpolateColor(clamp01(g / TINT_BY), [0, 1], [dark ? INK : SOFT, '#FFFFFF']),
      shadowOpacity: 0.3 * clamp01(g),
      elevation: 16 * clamp01(g),
      opacity: reducedMotion ? cover.get() : 1,
    };
  });
  const clipStyle = useAnimatedStyle(() => ({ borderRadius: mix(CHIP / 2, CARD_RADIUS, clamp01(grown.get())) }));
  // The card's parts keep their places on the screen while its frame grows round them.
  const insideStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: cardX - left.get() }, { translateY: cardY - top.get() }],
  }));
  const titleStyle = useAnimatedStyle(() => {
    const g = grown.get();
    const scale = mix(1, TITLE_GROWN, g);
    const titleWidth = chipWidth.get() - TITLE_TRIM;
    return {
      opacity: reducedMotion ? cover.get() : 1,
      transform: [
        { translateX: mix(chipX.get() - scrolled.get() + CHIP_PAD, cardX + cardWidth / 2 - (titleWidth * scale) / 2, g) },
        { translateY: mix(chipY + CHIP / 2, cardY + TITLE_AT, g) - TITLE_LINE / 2 },
        { scale },
      ],
    };
  });
  const chevronStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(grown.get() / CHEVRON_GONE) }));
  const lightStyle = useAnimatedStyle(() => ({ opacity: 1 - clamp01(grown.get() / TINT_BY) }));
  const coverStyle = useAnimatedStyle(() => ({ opacity: COVER * cover.get() }));

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  const shownPeople = chosen ? matches.map((person) => ({ ...person, interest: chosen.label })) : people;
  const heading = chosen ? chosen.label : title;
  const tabsBottom = Math.max(insets.bottom, 16);

  return (
    <View style={styles.screen} onLayout={onLayout} onTouchStart={touched}>
      <StatusBar style="dark" />

      <View style={[styles.header, { top: insets.top + HEADER_TOP }]}>
        <View style={styles.live}>
          <View style={styles.logo}>
            <View style={styles.logoDot} />
          </View>
          <View style={styles.liveDot} />
          <Text style={styles.liveLabel}>LIVE NOW</Text>
        </View>
        <View style={styles.headerEnd}>
          <View style={styles.round}>
            <MaterialIcons name="inbox" size={20} color={INK} />
          </View>
          <View style={[styles.round, { backgroundColor: FACE_YOU }]}>
            <Text style={styles.initials}>YO</Text>
          </View>
        </View>
      </View>

      <View style={[styles.online, { top: insets.top + ONLINE_TOP }]}>
        <View style={styles.onlineFace}>
          <Text style={styles.onlineInitial}>{online.slice(0, 1)}</Text>
        </View>
        <Text style={styles.onlineName} numberOfLines={1}>
          {online}
        </Text>
        <View style={styles.onlineDot} />
        <Text style={styles.onlineNote}>came online</Text>
      </View>

      <Animated.ScrollView
        ref={chips}
        horizontal
        showsHorizontalScrollIndicator={false}
        onScroll={onScroll}
        scrollEventThrottle={16}
        style={[styles.chips, { top: chipY }]}
        contentContainerStyle={styles.chipsContent}
      >
        <Pressable style={styles.filter} accessibilityRole="button" accessibilityLabel="Filters">
          {({ pressed }) => <MaterialIcons name="filter-list" size={20} color={INK} style={pressed && styles.pressed} />}
        </Pressable>
        {categories.map((item, index) => (
          <Chip
            key={item.name}
            category={item}
            dark={choice?.category === index && active !== index}
            hidden={active === index}
            onLayout={(event) => {
              frames.current[index] = { x: event.nativeEvent.layout.x, width: event.nativeEvent.layout.width };
            }}
            onPress={() => pressChip(index)}
          />
        ))}
      </Animated.ScrollView>

      <View style={[styles.headingBox, { top: insets.top + HEADING_TOP }]}>
        <Animated.Text
          key={heading}
          style={styles.heading}
          numberOfLines={1}
          accessibilityRole="header"
          entering={FadeIn.duration(SWAP)}
          exiting={FadeOut.duration(SWAP)}
        >
          {heading}
        </Animated.Text>
      </View>

      <View style={[styles.peopleBox, { top: insets.top + PEOPLE_TOP }]}>
        <Animated.View key={heading} style={styles.fill} entering={FadeIn.duration(SWAP)} exiting={FadeOut.duration(SWAP)}>
          <ScrollView ref={cards} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.peopleContent}>
            {shownPeople.map((person) => (
              <View key={person.name} style={styles.person}>
                <View style={styles.portraitRing}>
                  <Image source={{ uri: person.photo }} style={styles.portrait} contentFit="cover" transition={200} />
                </View>
                <Text style={styles.personName} numberOfLines={1}>
                  {person.name}
                </Text>
                <Text style={styles.personNote} numberOfLines={1}>
                  Because you are interested in
                </Text>
                <Text style={styles.personInterest} numberOfLines={1}>
                  {person.interest}
                </Text>
                <Pressable accessibilityRole="button" accessibilityLabel={`Chat with ${person.name}`}>
                  {({ pressed }) => (
                    <View style={[styles.chat, pressed && styles.pressed]}>
                      <View style={styles.chatDot} />
                      <Text style={styles.chatLabel}>Chat</Text>
                    </View>
                  )}
                </Pressable>
              </View>
            ))}
          </ScrollView>
        </Animated.View>
      </View>

      <Text style={[styles.heading, styles.takesTitle, { top: insets.top + TAKES_TOP }]} numberOfLines={1} accessibilityRole="header">
        {takesTitle}
      </Text>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={[styles.takes, { top: insets.top + THUMBS_TOP }]}
        contentContainerStyle={styles.takesContent}
      >
        {takes.map((uri, index) => (
          <View key={uri} style={styles.thumb}>
            <Image source={{ uri }} style={styles.fill} contentFit="cover" transition={200} />
            {index === 0 && (
              <View style={styles.tag}>
                <Text style={styles.tagLabel}>NEW</Text>
              </View>
            )}
          </View>
        ))}
      </ScrollView>

      <View style={[styles.tabs, { bottom: tabsBottom, left: (width - TABS_WIDTH) / 2 }]}>
        {(['Home', 'Chats'] as const).map((label, index) => (
          <Pressable
            key={label}
            style={styles.tab}
            accessibilityRole="tab"
            accessibilityState={{ selected: tab === index }}
            onPress={() => {
              tick();
              setTab(index);
            }}
          >
            <MaterialIcons name={index === 0 ? 'home' : 'chat-bubble-outline'} size={20} color={tab === index ? INK : MUTE} />
            <Text style={[styles.tabLabel, { color: tab === index ? INK : MUTE }]}>{label}</Text>
          </Pressable>
        ))}
      </View>

      {/* The cover lies on everything, the tab capsule too; a tap on it closes the card. */}
      <Animated.View style={[styles.fill, styles.cover, coverStyle]} pointerEvents={open ? 'auto' : 'none'}>
        <Pressable style={styles.fill} accessibilityRole="button" accessibilityLabel="Close" onPress={close} />
      </Animated.View>

      {category !== null && (
        <>
          <Animated.View style={[styles.card, frameStyle]} accessibilityViewIsModal={open}>
            <Animated.View style={[styles.fill, styles.clip, clipStyle]}>
              <Animated.View style={[{ width: cardWidth, height: CARD_HEIGHT }, insideStyle]} pointerEvents={open ? 'auto' : 'none'}>
                <Rise shown={open} order={0} style={styles.members}>
                  <View style={styles.faces}>
                    {FACES.map((color, index) => (
                      <View key={color} style={[styles.face, { backgroundColor: color, marginLeft: index === 0 ? 0 : -6 }]} />
                    ))}
                  </View>
                  <Text style={styles.membersCount}>{category.members} Members</Text>
                  <Text style={styles.membersNote}> online</Text>
                </Rise>
                {category.topics.map((topic, index) => (
                  <Rise key={topic.label} shown={open} order={index + 1} style={[styles.topicSlot, { top: TOPICS_AT + index * TOPIC_STEP - TOPIC / 2 }]}>
                    <Pressable accessibilityRole="button" onPress={() => pressTopic(index)}>
                      {({ pressed }) => (
                        <Animated.View
                          style={[
                            styles.topic,
                            {
                              transform: [{ scale: pressed || autoPressed === index ? PRESS : 1 }],
                              transitionProperty: 'transform',
                              transitionDuration: 100,
                            },
                          ]}
                        >
                          <Text style={styles.topicEmoji}>{topic.emoji}</Text>
                          <Text style={styles.topicLabel} numberOfLines={1}>
                            {topic.label}
                          </Text>
                        </Animated.View>
                      )}
                    </Pressable>
                  </Rise>
                ))}
              </Animated.View>
            </Animated.View>
          </Animated.View>

          {/* The chip's glyph and label are the card's title: one row that travels and grows. */}
          <Animated.View style={[styles.title, titleStyle]} pointerEvents="none">
            <MaterialIcons name={category.icon} size={GLYPH} color={INK} />
            <Text style={styles.chipLabel} numberOfLines={1}>
              {category.name}
            </Text>
            <Animated.View style={chevronStyle}>
              <MaterialIcons name="keyboard-arrow-down" size={CHEVRON} color={MUTE} style={styles.chevron} />
            </Animated.View>
            {dark && (
              // Over a dark chip the row is white, and turns to ink as the chip turns to the card.
              <Animated.View style={[styles.fill, styles.titleRow, lightStyle]}>
                <MaterialIcons name={category.icon} size={GLYPH} color="#FFFFFF" />
                <Text style={[styles.chipLabel, styles.light]} numberOfLines={1}>
                  {category.name}
                </Text>
                <MaterialIcons name="keyboard-arrow-down" size={CHEVRON} color="#FFFFFF" style={styles.chevron} />
              </Animated.View>
            )}
          </Animated.View>
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: PAGE,
    overflow: 'hidden',
  },
  fill: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
  },
  hidden: {
    opacity: 0,
  },
  pressed: {
    opacity: 0.6,
  },
  header: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: LIVE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  live: {
    height: LIVE,
    paddingLeft: 12,
    paddingRight: 16,
    borderRadius: LIVE / 2,
    borderWidth: 1,
    borderColor: PALE_EDGE,
    backgroundColor: PALE,
    flexDirection: 'row',
    alignItems: 'center',
  },
  logo: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: INK,
    alignItems: 'center',
    justifyContent: 'center',
  },
  logoDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: INK,
  },
  liveDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    marginLeft: 12,
    marginRight: 6,
    backgroundColor: GREEN,
  },
  liveLabel: {
    color: GREEN,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
    letterSpacing: 1,
    includeFontPadding: false,
  },
  headerEnd: {
    flexDirection: 'row',
    gap: 12,
  },
  round: {
    width: ROUND,
    height: ROUND,
    borderRadius: ROUND / 2,
    backgroundColor: SOFT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  initials: {
    color: INK,
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '600',
    includeFontPadding: false,
  },
  online: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: 20,
    flexDirection: 'row',
    alignItems: 'center',
  },
  onlineFace: {
    width: 18,
    height: 18,
    borderRadius: 9,
    marginRight: 8,
    backgroundColor: FACE_ONLINE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  onlineInitial: {
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '700',
    includeFontPadding: false,
  },
  onlineName: {
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  onlineDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginLeft: 8,
    marginRight: 5,
    backgroundColor: GREEN,
  },
  onlineNote: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  chips: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: CHIP,
    flexGrow: 0,
  },
  chipsContent: {
    paddingHorizontal: MARGIN,
    gap: CHIP_GAP,
  },
  filter: {
    width: CHIP,
    height: CHIP,
    borderRadius: CHIP / 2,
    backgroundColor: SOFT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chip: {
    height: CHIP,
    paddingLeft: CHIP_PAD,
    paddingRight: CHIP_END,
    borderRadius: CHIP / 2,
    backgroundColor: SOFT,
    flexDirection: 'row',
    alignItems: 'center',
  },
  chipDark: {
    backgroundColor: INK,
  },
  chipLabel: {
    marginLeft: LABEL_GAP,
    color: INK,
    fontSize: 14,
    lineHeight: TITLE_LINE,
    fontWeight: '500',
    includeFontPadding: false,
  },
  light: {
    color: '#FFFFFF',
  },
  chevron: {
    marginLeft: CHEVRON_GAP,
  },
  headingBox: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    height: 24,
  },
  heading: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    color: INK,
    fontSize: 18,
    lineHeight: 24,
    fontWeight: '600',
    letterSpacing: -0.2,
    includeFontPadding: false,
  },
  peopleBox: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: PERSON_HEIGHT,
  },
  peopleContent: {
    paddingHorizontal: MARGIN,
    gap: PERSON_GAP,
  },
  person: {
    width: PERSON_WIDTH,
    height: PERSON_HEIGHT,
    borderRadius: 30,
    borderCurve: 'continuous',
    backgroundColor: PANEL,
    alignItems: 'center',
  },
  portraitRing: {
    width: PORTRAIT + RING * 2,
    height: PORTRAIT + RING * 2,
    marginTop: 25,
    borderRadius: PORTRAIT / 2 + RING,
    backgroundColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 20, color: 'rgba(0,0,0,0.14)' }],
  },
  portrait: {
    width: PORTRAIT,
    height: PORTRAIT,
    borderRadius: PORTRAIT / 2,
    backgroundColor: RULE,
  },
  personName: {
    marginTop: 18,
    color: INK,
    fontSize: 16,
    lineHeight: 22,
    fontWeight: '600',
    includeFontPadding: false,
  },
  personNote: {
    marginTop: 9,
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  personInterest: {
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  chat: {
    height: 30,
    marginTop: 15,
    paddingHorizontal: 16,
    borderRadius: 15,
    backgroundColor: '#FFFFFF',
    flexDirection: 'row',
    alignItems: 'center',
  },
  chatDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 8,
    backgroundColor: GREEN,
  },
  chatLabel: {
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  takesTitle: {
    left: MARGIN,
    right: MARGIN,
  },
  takes: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: THUMB_HEIGHT,
    flexGrow: 0,
  },
  takesContent: {
    paddingHorizontal: MARGIN,
    gap: THUMB_GAP,
  },
  thumb: {
    width: THUMB_WIDTH,
    height: THUMB_HEIGHT,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: SOFT,
    overflow: 'hidden',
  },
  tag: {
    position: 'absolute',
    left: 10,
    top: 10,
    height: 18,
    paddingHorizontal: 7,
    borderRadius: 9,
    backgroundColor: PALE,
    justifyContent: 'center',
  },
  tagLabel: {
    color: INK,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '700',
    letterSpacing: 0.4,
    includeFontPadding: false,
  },
  tabs: {
    position: 'absolute',
    width: TABS_WIDTH,
    height: TABS_HEIGHT,
    borderRadius: TABS_HEIGHT / 2,
    backgroundColor: '#FFFFFF',
    flexDirection: 'row',
    boxShadow: [{ offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(0,0,0,0.14)' }],
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabLabel: {
    marginTop: 2,
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '600',
    includeFontPadding: false,
  },
  cover: {
    backgroundColor: '#000000',
  },
  card: {
    position: 'absolute',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 20 },
    shadowRadius: 25,
  },
  clip: {
    overflow: 'hidden',
  },
  members: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: MEMBERS_AT - 10,
    height: 20,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  faces: {
    flexDirection: 'row',
    marginRight: 8,
  },
  face: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 1.5,
    borderColor: '#FFFFFF',
  },
  membersCount: {
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  membersNote: {
    color: MUTE,
    fontSize: 13,
    lineHeight: 18,
    includeFontPadding: false,
  },
  topicSlot: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: TOPIC,
    alignItems: 'center',
  },
  topic: {
    height: TOPIC,
    paddingHorizontal: 14,
    borderRadius: TOPIC / 2,
    borderWidth: 1,
    borderColor: RULE,
    backgroundColor: '#FFFFFF',
    flexDirection: 'row',
    alignItems: 'center',
    boxShadow: [{ offsetX: 0, offsetY: 4, blurRadius: 12, color: 'rgba(0,0,0,0.08)' }],
  },
  topicEmoji: {
    marginRight: 8,
    fontSize: 16,
    lineHeight: 20,
    includeFontPadding: false,
  },
  topicLabel: {
    color: INK,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
    includeFontPadding: false,
  },
  title: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: TITLE_LINE,
    flexDirection: 'row',
    alignItems: 'center',
    transformOrigin: 'left center',
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
});
