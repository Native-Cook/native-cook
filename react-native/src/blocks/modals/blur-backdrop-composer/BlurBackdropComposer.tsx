import Ionicons from '@expo/vector-icons/Ionicons';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, type StyleProp, StyleSheet, Text, View, type ViewStyle, useWindowDimensions } from 'react-native';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { BACKDROPS, Backdrop, PAGE_PHOTOS, type Scene } from './Backdrops';

type Props = {
  /** Opens and closes the composer by itself, three times a loop, and leaves the page as it was. */
  autoPlay?: boolean;
  title?: string;
  createLabel?: string;
  placeholder?: string;
  /** Called with the name when Create (or the keyboard's done key) is pressed. */
  onCreate?: (name: string) => void;
  onCancel?: () => void;
};

// One loop: the page at rest, then three openings, each with its own photo behind the composer.
const REST = 0.8;
const OPENING = 2.7;
const OPENINGS = 3;
export const LOOP = 9;
// Inside an opening: the sheet rises, the name is typed, the sheet drops.
const TYPE_AT = 0.6;
const TYPE_EVERY = 0.09;
const CLOSE_AT = 1.75;
const NAMES = ['Tokyo', 'Maldives', 'Swiss Alps'];
// After a touch the loop waits this long, then starts again from the page.
const USER_HOLD = 3;

const OPEN_SPRING = { duration: 450, dampingRatio: 0.9, reduceMotion: ReduceMotion.Never };
const CLOSE_MS = 350;
const FADE_MS = 200;
// The cards fade in and rise once the sheet is up: when they start, how long all of them take,
// the gap between two, and how far each rises.
const CARDS_AFTER = 300;
const CARDS_MS = 600;
const CARD_STAGGER = 0.1;
const CARD_RISE = 14;
// The photo drifts from this scale to the next while the composer is up.
const DRIFT_FROM = 1.05;
const DRIFT_TO = 1.1;
const DRIFT_SECONDS = 6;

const SCENES: Scene[] = [0, 1, 2];
const ACCENT = '#e8643a';
const INK = '#111114';
const MUTE = '#7c7c83';
const FIELD = '#f1f1f4';
const BUTTON = '#1f1f22';

const MARGIN = 20;
const SEARCH_HEIGHT = 38;
const HERO_HEIGHT = 176;
const GLASS_RADIUS = 22;
const KEY_GAP = 6;
const KEY_HEIGHT = 44;
const KEY_ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'];
const SUGGESTIONS = ['I', 'The', 'I’m'];
const MAX_NAME = 22;

const GLASS = 'rgba(255,255,255,0.2)';
const GLASS_EDGE = 'rgba(255,255,255,0.32)';
const GLASS_TOP = 'rgba(255,255,255,0.55)';
const GLASS_ICON = 'rgba(255,255,255,0.24)';

const stage = (progress: number, index: number) => {
  'worklet';
  const p = Math.min(1, Math.max(0, (progress - index * CARD_STAGGER) / (1 - 2 * CARD_STAGGER)));
  return 1 - (1 - p) * (1 - p) * (1 - p);
};

/**
 * A trips page with a "Create a Trip" button. The button raises a full-screen composer over a
 * blurred photo that changes on every opening: the name field, three frosted cards and a keyboard
 * drawn here, so nothing ever opens the system keyboard. The keys work by touch.
 */
export function BlurBackdropComposer({
  autoPlay = false,
  title = 'My Trips',
  createLabel = 'Create a Trip',
  placeholder = 'Trip name',
  onCreate,
  onCancel,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [scene, setScene] = useState<Scene>(0);
  const [name, setName] = useState('');
  const [lit, setLit] = useState<string | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  // 0 is the page alone, 1 the composer fully up.
  const sheet = useSharedValue(0);
  const cards = useSharedValue(0);
  // Seconds since the composer opened; drives the drift and the caret.
  const clock = useSharedValue(0);
  const opened = useRef(0);

  useEffect(() => {
    Image.prefetch([...SCENES.map((s) => BACKDROPS[s]), PAGE_PHOTOS.hero, ...PAGE_PHOTOS.collage, PAGE_PHOTOS.avatar]).catch(() => {});
  }, []);

  const open = (index: number) => {
    setScene(SCENES[index % SCENES.length]);
    setName('');
    setIsOpen(true);
    cards.set(0);
    clock.set(0);
    cards.set(withDelay(CARDS_AFTER, withTiming(1, { duration: CARDS_MS, easing: Easing.linear, reduceMotion: ReduceMotion.Never })));
    clock.set(withTiming(DRIFT_SECONDS * 4, { duration: DRIFT_SECONDS * 4000, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
    sheet.set(
      reducedMotion
        ? withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never })
        : withSpring(1, OPEN_SPRING),
    );
  };

  const close = () => {
    sheet.set(
      withTiming(
        0,
        { duration: reducedMotion ? FADE_MS : CLOSE_MS, easing: Easing.in(Easing.cubic), reduceMotion: ReduceMotion.Never },
        (finished) => {
          if (finished) scheduleOnRN(setIsOpen, false);
        },
      ),
    );
  };

  useEffect(() => () => {
    cancelAnimation(sheet);
    cancelAnimation(cards);
    cancelAnimation(clock);
  }, [sheet, cards, clock]);

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ open, close });
  useEffect(() => {
    latest.current = { open, close };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const at = (origin: number, seconds: number, run: () => void) =>
      timers.push(setTimeout(run, origin + seconds * 1000 - Date.now()));
    const cycle = (origin: number) => {
      for (let k = 0; k < OPENINGS; k++) {
        const start = REST + k * OPENING;
        at(origin, start, () => latest.current.open(k));
        const word = NAMES[k];
        for (let i = 0; i < word.length; i++) {
          at(origin, start + TYPE_AT + i * TYPE_EVERY, () => {
            setName(word.slice(0, i + 1));
            setLit(word[i].toUpperCase());
          });
          at(origin, start + TYPE_AT + i * TYPE_EVERY + 0.07, () => setLit(null));
        }
        at(origin, start + CLOSE_AT, () => latest.current.close());
      }
      at(origin, LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) {
      cycle(Date.now());
    } else {
      // Back to the page, then the loop from its first second.
      timers.push(
        setTimeout(() => {
          latest.current.close();
          setLit(null);
          cycle(Date.now() + 400);
        }, USER_HOLD * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches]);

  const openByHand = () => {
    latest.current.open(opened.current++);
  };

  const type = (char: string) => {
    Haptics.selectionAsync().catch(() => {});
    setName((current) => {
      if (char === 'back') return current.slice(0, -1);
      if (current.length >= MAX_NAME) return current;
      if (char === ' ') return current.length > 0 && !current.endsWith(' ') ? `${current} ` : current;
      return current + (current.length === 0 ? char.toUpperCase() : char);
    });
  };

  const create = () => {
    onCreate?.(name);
    close();
  };

  const cancel = () => {
    onCancel?.();
    close();
  };

  const sheetStyle = useAnimatedStyle(() => {
    const p = sheet.get();
    if (reducedMotion) return { opacity: p, transform: [{ translateY: 0 }] };
    return { opacity: 1, transform: [{ translateY: (1 - Math.min(p, 1)) * height }] };
  });
  const driftStyle = useAnimatedStyle(() => {
    const t = Math.min(clock.get() / DRIFT_SECONDS, 1);
    return { transform: [{ scale: reducedMotion ? 1 : DRIFT_FROM + (DRIFT_TO - DRIFT_FROM) * t }] };
  });
  const caretStyle = useAnimatedStyle(() => ({ opacity: Math.floor(clock.get() / 0.5) % 2 === 0 ? 1 : 0 }));
  // The name field settles a beat before the cards.
  const fieldStyle = useAnimatedStyle(() => {
    const p = reducedMotion ? 1 : Math.min(1, Math.max(0, (sheet.get() - 0.4) / 0.6));
    return { opacity: p, transform: [{ translateY: (1 - p) * 10 }] };
  });

  const contentWidth = width - MARGIN * 2;
  const keyWidth = (width - KEY_GAP * 11) / 10;
  const collageCard = Math.round(contentWidth * 0.36);

  return (
    <View style={styles.screen} onTouchStart={() => autoPlay && setTouches((n) => n + 1)}>
      <StatusBar style={isOpen ? 'light' : 'dark'} />

      <View style={[styles.page, { paddingTop: insets.top + 2 }]} accessibilityElementsHidden={isOpen}>
        <View style={styles.topRow}>
          <View style={styles.avatarRing}>
            <Image source={PAGE_PHOTOS.avatar} style={styles.avatar} cachePolicy="memory-disk" transition={0} />
          </View>
          <View style={styles.topIcons}>
            <Ionicons name="search" size={22} color={ACCENT} />
            <Ionicons name="add" size={30} color={ACCENT} />
          </View>
        </View>
        <View style={styles.search}>
          <Ionicons name="search" size={16} color={MUTE} />
          <Text style={styles.searchText}>Search trips, places</Text>
        </View>
        <Text style={styles.title}>{title}</Text>

        <View style={styles.heroShadow}>
          <View style={styles.hero}>
            <Image source={PAGE_PHOTOS.hero} style={StyleSheet.absoluteFill} contentFit="cover" cachePolicy="memory-disk" transition={0} />
            <LinearGradient
              colors={['rgba(10,16,40,0.55)', 'rgba(10,16,40,0.15)', 'rgba(10,16,40,0)']}
              locations={[0, 0.55, 1]}
              start={{ x: 0, y: 0 }}
              end={{ x: 0.7, y: 1 }}
              style={StyleSheet.absoluteFill}
            />
            <Text style={styles.heroTitle}>Welcome to Wander!</Text>
            <Text style={styles.heroText}>Learn how to plan every detail of your trip in one place.</Text>
            <View style={styles.heroClose}>
              <Ionicons name="close" size={15} color="#ffffff" />
            </View>
            <View style={styles.seeHow}>
              <Text style={styles.seeHowText}>See How</Text>
            </View>
          </View>
        </View>

        <View style={[styles.collage, { width: contentWidth }]}>
          {COLLAGE.map((card, i) => (
            <View
              key={i}
              style={[
                styles.collageShadow,
                {
                  width: collageCard,
                  height: collageCard * 0.76,
                  left: card.x * contentWidth - collageCard / 2,
                  top: card.y,
                  zIndex: card.z,
                  transform: [{ rotate: `${card.angle}deg` }],
                },
              ]}
            >
              <Image source={PAGE_PHOTOS.collage[i]} style={styles.collagePhoto} contentFit="cover" cachePolicy="memory-disk" transition={0} />
            </View>
          ))}
        </View>
        <Text style={styles.welcome}>Welcome, Traveler!</Text>
        <Text style={styles.welcomeText}>Create your first trip and start planning your next adventure.</Text>
        <Pressable onPress={openByHand} accessibilityRole="button" style={({ pressed }) => [styles.create, pressed && styles.pressed]}>
          <Ionicons name="add" size={20} color="#ffffff" />
          <Text style={styles.createText}>{createLabel}</Text>
        </Pressable>
      </View>

      <View style={[styles.tabBar, { paddingBottom: insets.bottom }]} accessibilityElementsHidden={isOpen}>
        {TABS.map((tab, i) => (
          <View key={tab.label} style={styles.tab}>
            <Ionicons name={tab.icon} size={24} color={i === 0 ? ACCENT : '#9a9aa1'} />
            <Text style={[styles.tabLabel, i === 0 && { color: ACCENT }]}>{tab.label}</Text>
          </View>
        ))}
      </View>

      <Animated.View style={[StyleSheet.absoluteFill, sheetStyle]} pointerEvents={isOpen ? 'auto' : 'none'}>
        <View style={styles.sheet}>
          <Animated.View style={[StyleSheet.absoluteFill, driftStyle]}>
            <Backdrop scene={scene} width={width} height={height} />
          </Animated.View>
          <LinearGradient
            colors={['rgba(0,0,0,0.34)', 'rgba(0,0,0,0.08)', 'rgba(0,0,0,0.12)']}
            locations={[0, 0.45, 1]}
            style={StyleSheet.absoluteFill}
          />
          <View style={[StyleSheet.absoluteFill, styles.veil]} />

          <View style={[styles.nav, { top: insets.top + 4 }]}>
            <Pressable onPress={cancel} hitSlop={12} accessibilityRole="button">
              <Text style={styles.cancel}>Cancel</Text>
            </Pressable>
            <Pressable onPress={create} accessibilityRole="button" style={({ pressed }) => [styles.createPill, pressed && styles.pressed]}>
              <Text style={styles.createPillText}>Create</Text>
            </Pressable>
          </View>

          <Animated.View style={[styles.nameRow, { top: insets.top + 64 }, fieldStyle]}>
            {name.length === 0 && <Animated.View style={[styles.caret, caretStyle]} />}
            <Text style={[styles.name, name.length === 0 && styles.placeholder]} numberOfLines={1}>
              {name.length === 0 ? placeholder : name}
            </Text>
            {name.length > 0 && <Animated.View style={[styles.caret, caretStyle]} />}
          </Animated.View>

          <Reveal cards={cards} index={0} reduced={reducedMotion} style={[styles.glass, { top: insets.top + 134 }]}>
            <CardHead icon="calendar-clear" label="Itinerary" />
            <View style={styles.dates}>
              <View>
                <Text style={styles.small}>Starts</Text>
                <Text style={styles.big}>Set Dates</Text>
              </View>
              <Ionicons name="arrow-forward" size={18} color="rgba(255,255,255,0.9)" />
              <View style={styles.right}>
                <Text style={styles.small}>Ends</Text>
                <Text style={styles.big}>Set Dates</Text>
              </View>
            </View>
          </Reveal>
          <View style={[styles.pair, { top: insets.top + 250 }]}>
            <Reveal cards={cards} index={1} reduced={reducedMotion} style={styles.glassHalf}>
              <CardHead icon="globe-outline" />
              <View>
                <Text style={styles.big}>Timezone</Text>
                <Text style={styles.small}>Bengaluru, India</Text>
              </View>
            </Reveal>
            <Reveal cards={cards} index={2} reduced={reducedMotion} style={styles.glassHalf}>
              <CardHead icon="image" />
              <View>
                <Text style={styles.big}>Choose Image</Text>
                <Text style={styles.small}>From your photos</Text>
              </View>
            </Reveal>
          </View>

          <View style={[styles.keyboard, { paddingBottom: Math.max(insets.bottom - 18, 6) }]}>
            <View style={styles.suggestions}>
              {SUGGESTIONS.map((word, i) => (
                <View key={word} style={styles.suggestion}>
                  {i > 0 && <View style={styles.suggestionRule} />}
                  <Text style={styles.suggestionText}>{word}</Text>
                </View>
              ))}
            </View>
            <View style={styles.keys}>
              {KEY_ROWS.map((row, r) => (
                <View key={row} style={[styles.keyRow, r === 1 && { paddingHorizontal: keyWidth / 2 }]}>
                  {r === 2 && (
                    <View style={[styles.key, styles.keyDark, { width: keyWidth * 1.3, marginRight: keyWidth * 0.2 }]}>
                      <Ionicons name="arrow-up-outline" size={22} color="#000000" />
                    </View>
                  )}
                  {row.split('').map((char) => (
                    <Pressable
                      key={char}
                      onPress={() => type(char)}
                      style={[styles.key, { width: keyWidth }]}
                      accessibilityLabel={char}
                    >
                      <Text style={styles.keyText}>{char.toUpperCase()}</Text>
                      {lit === char.toUpperCase() && (
                        <View pointerEvents="none" style={[styles.callout, { width: keyWidth + 18, left: -9 }]}>
                          <Text style={styles.calloutText}>{char.toUpperCase()}</Text>
                        </View>
                      )}
                    </Pressable>
                  ))}
                  {r === 2 && (
                    <Pressable
                      onPress={() => type('back')}
                      style={[styles.key, styles.keyDark, { width: keyWidth * 1.3, marginLeft: keyWidth * 0.2 }]}
                      accessibilityLabel="Delete"
                    >
                      <Ionicons name="backspace-outline" size={22} color="#000000" />
                    </Pressable>
                  )}
                </View>
              ))}
              <View style={styles.keyRow}>
                <View style={[styles.key, styles.keyDark, { width: keyWidth * 2.3 }]}>
                  <Text style={styles.keyMeta}>123</Text>
                </View>
                <Pressable onPress={() => type(' ')} style={[styles.key, { flex: 1 }, lit === ' ' && styles.keyLit]} accessibilityLabel="Space">
                  <Text style={styles.keyMeta}>space</Text>
                </Pressable>
                <Pressable onPress={create} style={[styles.key, styles.keyDone, { width: keyWidth * 2.3 }]} accessibilityLabel="Done">
                  <Text style={styles.keyDoneText}>done</Text>
                </Pressable>
              </View>
            </View>
            <View style={styles.keyboardFoot}>
              <Ionicons name="happy-outline" size={26} color="#4b4b52" />
              <Ionicons name="mic-outline" size={26} color="#4b4b52" />
            </View>
          </View>
        </View>
      </Animated.View>
    </View>
  );
}

const TABS: { label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { label: 'Trips', icon: 'airplane' },
  { label: 'Explore', icon: 'compass-outline' },
  { label: 'Saved', icon: 'bookmark-outline' },
  { label: 'Profile', icon: 'person-circle-outline' },
];

// Where the collage's tilted cards sit: centre as a fraction of the width, top in points, tilt, stacking.
const COLLAGE = [
  { x: 0.2, y: 34, angle: -14, z: 1 },
  { x: 0.8, y: 30, angle: 12, z: 1 },
  { x: 0.5, y: 8, angle: -3, z: 2 },
];

function CardHead({ icon, label }: { icon: keyof typeof Ionicons.glyphMap; label?: string }) {
  return (
    <View style={styles.cardHead}>
      <View style={styles.cardIcon}>
        <Ionicons name={icon} size={15} color="#ffffff" />
      </View>
      {label && <Text style={styles.cardLabel}>{label}</Text>}
    </View>
  );
}

/** A frosted card that fades in and rises into place once the sheet is up. */
function Reveal({
  cards,
  index,
  reduced,
  style,
  children,
}: {
  cards: SharedValue<number>;
  index: number;
  reduced: boolean;
  style: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  const animated = useAnimatedStyle(() => {
    const p = reduced ? (cards.get() > 0 ? 1 : 0) : stage(cards.get(), index);
    return { opacity: p, transform: [{ translateY: reduced ? 0 : (1 - p) * CARD_RISE }, { scale: reduced ? 1 : 0.97 + 0.03 * p }] };
  });
  return <Animated.View style={[style, animated]}>{children}</Animated.View>;
}

const glass: ViewStyle = {
  borderRadius: GLASS_RADIUS,
  borderCurve: 'continuous',
  backgroundColor: GLASS,
  borderWidth: 1,
  borderColor: GLASS_EDGE,
  borderTopColor: GLASS_TOP,
  padding: 16,
};

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#ffffff' },
  page: { flex: 1, paddingHorizontal: MARGIN, backgroundColor: '#ffffff' },
  topRow: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  avatarRing: { width: 34, height: 34, borderRadius: 17, borderWidth: 2, borderColor: ACCENT, padding: 2 },
  avatar: { flex: 1, borderRadius: 13 },
  topIcons: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  search: {
    height: SEARCH_HEIGHT,
    borderRadius: 12,
    borderCurve: 'continuous',
    backgroundColor: FIELD,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    marginTop: 8,
  },
  searchText: { color: MUTE, fontSize: 17, lineHeight: 22, letterSpacing: -0.3 },
  title: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1, marginTop: 20, marginBottom: 16 },
  heroShadow: {
    height: HERO_HEIGHT,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    shadowColor: '#0b1a2a',
    shadowOpacity: 0.18,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 10 },
    elevation: 8,
  },
  hero: { flex: 1, borderRadius: 24, borderCurve: 'continuous', overflow: 'hidden', padding: 18, backgroundColor: '#6d7fa8' },
  heroTitle: { color: '#ffffff', fontSize: 21, lineHeight: 26, fontWeight: '800', letterSpacing: -0.5 },
  heroText: { color: 'rgba(255,255,255,0.88)', fontSize: 13, lineHeight: 18, marginTop: 4, width: '62%' },
  heroClose: {
    position: 'absolute',
    top: 16,
    right: 16,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(0,0,0,0.28)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  seeHow: {
    position: 'absolute',
    left: 18,
    bottom: 18,
    height: 34,
    paddingHorizontal: 16,
    borderRadius: 17,
    backgroundColor: '#ffffff',
    justifyContent: 'center',
  },
  seeHowText: { color: ACCENT, fontSize: 14, lineHeight: 18, fontWeight: '700' },
  collage: { height: 158, marginTop: 20 },
  collageShadow: {
    position: 'absolute',
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    padding: 4,
    shadowColor: '#1a2230',
    shadowOpacity: 0.2,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 8 },
    elevation: 6,
  },
  collagePhoto: { flex: 1, borderRadius: 14 },
  welcome: { color: INK, fontSize: 22, lineHeight: 28, fontWeight: '800', letterSpacing: -0.5, textAlign: 'center', marginTop: 4 },
  welcomeText: { color: MUTE, fontSize: 15, lineHeight: 21, textAlign: 'center', marginTop: 6, marginHorizontal: 32 },
  create: {
    alignSelf: 'center',
    height: 52,
    paddingHorizontal: 28,
    borderRadius: 26,
    backgroundColor: BUTTON,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    marginTop: 20,
    shadowColor: '#000000',
    shadowOpacity: 0.22,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 6,
  },
  createText: { color: '#ffffff', fontSize: 17, lineHeight: 22, fontWeight: '600', letterSpacing: -0.2 },
  pressed: { opacity: 0.8 },
  tabBar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    paddingTop: 8,
    backgroundColor: 'rgba(255,255,255,0.96)',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(60,60,67,0.2)',
  },
  tab: { flex: 1, alignItems: 'center', gap: 2 },
  tabLabel: { color: '#9a9aa1', fontSize: 11, lineHeight: 14, fontWeight: '600' },

  sheet: { flex: 1, overflow: 'hidden', backgroundColor: '#20323a' },
  veil: { backgroundColor: 'rgba(255,255,255,0.06)' },
  nav: { position: 'absolute', left: MARGIN, right: MARGIN, height: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cancel: { color: '#ffffff', fontSize: 17, lineHeight: 22, fontWeight: '500' },
  createPill: {
    height: 36,
    paddingHorizontal: 18,
    borderRadius: 18,
    backgroundColor: '#ffffff',
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOpacity: 0.16,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
  },
  createPillText: { color: ACCENT, fontSize: 16, lineHeight: 20, fontWeight: '700' },
  nameRow: { position: 'absolute', left: MARGIN, right: MARGIN, height: 52, flexDirection: 'row', alignItems: 'center' },
  name: { color: '#ffffff', fontSize: 36, lineHeight: 44, fontWeight: '700', letterSpacing: -0.9 },
  placeholder: { color: 'rgba(255,255,255,0.55)' },
  caret: { width: 3, height: 38, borderRadius: 1.5, backgroundColor: ACCENT, marginHorizontal: 2 },
  glass: { ...glass, position: 'absolute', left: MARGIN, right: MARGIN, height: 104 },
  pair: { position: 'absolute', left: MARGIN, right: MARGIN, height: 112, flexDirection: 'row', gap: 12 },
  glassHalf: { ...glass, flex: 1, justifyContent: 'space-between' },
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  cardIcon: { width: 28, height: 28, borderRadius: 9, borderCurve: 'continuous', backgroundColor: GLASS_ICON, alignItems: 'center', justifyContent: 'center' },
  cardLabel: { color: 'rgba(255,255,255,0.92)', fontSize: 15, lineHeight: 20, fontWeight: '600', letterSpacing: -0.2 },
  dates: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', marginTop: 10 },
  right: { alignItems: 'flex-end' },
  small: { color: 'rgba(255,255,255,0.72)', fontSize: 13, lineHeight: 17 },
  big: { color: '#ffffff', fontSize: 17, lineHeight: 22, fontWeight: '700', letterSpacing: -0.3 },

  keyboard: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: '#d2d4da' },
  suggestions: { height: 44, flexDirection: 'row', alignItems: 'center' },
  suggestion: { flex: 1, height: 44, alignItems: 'center', justifyContent: 'center' },
  suggestionRule: { position: 'absolute', left: 0, top: 12, bottom: 12, width: 1, backgroundColor: 'rgba(0,0,0,0.14)' },
  suggestionText: { color: '#111114', fontSize: 17, lineHeight: 22 },
  keys: { paddingHorizontal: KEY_GAP / 2, gap: 12 },
  keyRow: { flexDirection: 'row', justifyContent: 'center', gap: KEY_GAP },
  key: {
    height: KEY_HEIGHT,
    borderRadius: 8,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOpacity: 0.3,
    shadowRadius: 0,
    shadowOffset: { width: 0, height: 1 },
  },
  keyLit: { backgroundColor: '#b9bcc4' },
  keyDark: { backgroundColor: '#abafb9' },
  keyDone: { backgroundColor: '#0a7aff' },
  keyText: { color: '#000000', fontSize: 23, lineHeight: 28 },
  keyMeta: { color: '#000000', fontSize: 16, lineHeight: 20 },
  keyDoneText: { color: '#ffffff', fontSize: 16, lineHeight: 20, fontWeight: '600' },
  callout: {
    position: 'absolute',
    bottom: KEY_HEIGHT - 2,
    height: 58,
    borderRadius: 10,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000000',
    shadowOpacity: 0.28,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 1 },
  },
  calloutText: { color: '#000000', fontSize: 32, lineHeight: 38 },
  keyboardFoot: { height: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 26, marginTop: 4 },
});
