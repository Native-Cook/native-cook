import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import {
  ReduceMotion,
  cancelAnimation,
  useReducedMotion,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Contours } from './Contours';
import { type ToastItem, type ToastMessage, ToastCard } from './ToastCard';

type Props = {
  /** Adds, collapses and swipes away toasts by itself, in a loop, and leaves the page empty. */
  autoPlay?: boolean;
  /** The messages a tap on the page goes through, one after the other. */
  messages?: ToastMessage[];
  /** Most toasts kept at once; the oldest leaves when another arrives. */
  limit?: number;
  /** Called when a toast has left, by swipe or by its x. */
  onDismiss?: (message: ToastMessage) => void;
  /** Called when a toast arrives. */
  onShow?: (message: ToastMessage) => void;
};

const MESSAGES: ToastMessage[] = [
  {
    kind: 'error',
    title: 'Error',
    message: "That password isn't right. Can we help you ",
    link: 'recover your password',
    tail: '?',
  },
  { kind: 'warning', title: 'Warning', message: "Looks like you've exceeded your limit. ", link: 'Need an upgrade?' },
  { kind: 'info', title: 'Info', message: "We've updated a few things. Check the ", link: 'patch notes', tail: '!' },
  { kind: 'success', title: 'Success', message: 'Items added to cart. Yay!' },
];

// One loop, in seconds: four toasts arrive in turn, the pile collapses, then four swipes clear it.
export const LOOP = 10;
const ARRIVE_AT = [0.7, 1.5, 2.4, 3.3];
const COLLAPSE_AT = 4.3;
// Each swipe: when it starts and where it goes (1 right, -1 left).
const SWIPES: [number, 1 | -1][] = [
  [5.5, 1],
  [6.5, -1],
  [7.1, 1],
  [7.7, -1],
];
// The spread is put back this long into the loop, when nothing is on screen.
const RESET_AT = 0.4;
// After a touch the loop waits this long, then starts again from the empty page.
const USER_HOLD = 3;

const COLLAPSE_SPRING = { duration: 500, dampingRatio: 0.9, reduceMotion: ReduceMotion.Never };
const MARGIN = 20;
const PAGE = '#f2f2f4';
const INK = '#111318';
const MUTE = '#6e7380';

/**
 * A page that collects toasts: each one slides in from above with a short shake and pushes the others
 * down, then the pile collapses into a stack and the toasts are swiped away one by one. Every
 * position comes from a toast's place in the list. By hand, tap the page to add one, swipe or tap the
 * x to remove one, long-press the page to collapse or spread the pile.
 */
export function StackingToastFeed({ autoPlay = false, messages = MESSAGES, limit = 5, onDismiss, onShow }: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const [items, setItems] = useState<ToastItem[]>([]);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);
  // 0 spread out, 1 collapsed into a pile.
  const stack = useSharedValue(0);
  const counter = useRef(0);
  const next = useRef(0);
  const collapsed = useRef(false);

  const setPile = (value: 0 | 1) => {
    collapsed.current = value === 1;
    stack.set(reducedMotion ? value : withSpring(value, COLLAPSE_SPRING));
  };

  const push = (message: ToastMessage) => {
    counter.current += 1;
    const id = counter.current;
    onShow?.(message);
    setItems((list) => {
      const kept = list.filter((item) => item.leaving === undefined);
      // The oldest toast makes room by leaving the way a tap on its x would.
      const surplus = kept.length + 1 - limit;
      const leaving = new Set(kept.slice(kept.length - Math.max(surplus, 0)).map((item) => item.id));
      return [{ id, message }, ...list.map((item) => (leaving.has(item.id) ? { ...item, leaving: 0 as const } : item))];
    });
  };

  const leave = useCallback((id: number, direction: -1 | 0 | 1) => {
    setItems((list) => list.map((item) => (item.id === id && item.leaving === undefined ? { ...item, leaving: direction } : item)));
  }, []);

  // The latest list and callback, so that `gone` keeps one identity (a toast's exit depends on it).
  const kept = useRef({ items, onDismiss });
  useEffect(() => {
    kept.current = { items, onDismiss };
  });

  const gone = useCallback((id: number) => {
    const left = kept.current.items.find((item) => item.id === id);
    setItems((list) => list.filter((item) => item.id !== id));
    if (left) kept.current.onDismiss?.(left.message);
  }, []);

  const front = () => items.find((item) => item.leaving === undefined);

  useEffect(
    () => () => {
      cancelAnimation(stack);
    },
    [stack],
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ push, front, leave, setPile });
  useEffect(() => {
    latest.current = { push, front, leave, setPile };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const at = (origin: number, seconds: number, run: () => void) =>
      timers.push(setTimeout(run, origin + seconds * 1000 - Date.now()));
    const cycle = (origin: number) => {
      at(origin, RESET_AT, () => latest.current.setPile(0));
      ARRIVE_AT.forEach((seconds, i) => at(origin, seconds, () => latest.current.push(messages[i % messages.length])));
      at(origin, COLLAPSE_AT, () => latest.current.setPile(1));
      for (const [seconds, direction] of SWIPES) {
        at(origin, seconds, () => {
          const toast = latest.current.front();
          if (toast) latest.current.leave(toast.id, direction);
        });
      }
      at(origin, LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) {
      cycle(Date.now());
    } else {
      // Back to the empty page, then the loop from its first second.
      timers.push(
        setTimeout(() => {
          setItems((list) => list.map((item) => (item.leaving === undefined ? { ...item, leaving: 0 as const } : item)));
          cycle(Date.now() + 800);
        }, USER_HOLD * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, messages]);

  const addByHand = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    push(messages[next.current++ % messages.length]);
  };

  const toggleByHand = () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    setPile(collapsed.current ? 0 : 1);
  };

  const place = new Map<number, number>();
  items.filter((item) => item.leaving === undefined).forEach((item, i) => place.set(item.id, i));

  const toastWidth = width - MARGIN * 2;

  return (
    <GestureHandlerRootView style={styles.screen} onTouchStart={() => autoPlay && setTouches((n) => n + 1)}>
      <StatusBar style="dark" />
      <Contours width={width} height={height} />
      <Pressable style={styles.page} onPress={addByHand} onLongPress={toggleByHand} delayLongPress={450} accessibilityLabel="Page">
        <View style={[styles.content, { paddingTop: insets.top + 4 }]}>
          <View style={styles.header}>
            <View>
              <Text style={styles.greeting}>Good evening, Lena</Text>
              <Text style={styles.title}>Explore</Text>
            </View>
            <View style={styles.cart}>
              <Ionicons name="bag-handle-outline" size={22} color={INK} />
              <View style={styles.badge}>
                <Text style={styles.badgeText}>2</Text>
              </View>
            </View>
          </View>
          <View style={styles.search}>
            <Ionicons name="search" size={17} color={MUTE} />
            <Text style={styles.searchText}>Experiences, cities, tours</Text>
          </View>
          <View style={styles.feature}>
            <Image source={photo(FEATURE, 800, 440)} style={StyleSheet.absoluteFill} contentFit="cover" cachePolicy="memory-disk" transition={0} />
            <LinearGradient colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.55)']} locations={[0.35, 1]} style={StyleSheet.absoluteFill} />
            <View style={styles.featureTag}>
              <Text style={styles.featureTagText}>Trending</Text>
            </View>
            <Text style={styles.featureTitle}>Sunrise balloon ride</Text>
            <Text style={styles.featureMeta}>Cappadocia · from $189</Text>
          </View>
          <View style={styles.sectionRow}>
            <Text style={styles.section}>Popular near you</Text>
            <Text style={styles.seeAll}>See all</Text>
          </View>
          {EXPERIENCES.map((item, i) => (
            <View key={item.title} style={[styles.row, i > 0 && styles.rowRule]}>
              <Image source={photo(item.photo, 160)} style={styles.thumb} contentFit="cover" cachePolicy="memory-disk" transition={0} />
              <View style={styles.rowText}>
                <Text style={styles.rowTitle} numberOfLines={1}>{item.title}</Text>
                <View style={styles.rowMetaLine}>
                  <Ionicons name="star" size={12} color="#f5a623" />
                  <Text style={styles.rowMeta}>{item.meta}</Text>
                </View>
              </View>
              <Text style={styles.price}>{item.price}</Text>
              <View style={styles.add}>
                <Ionicons name="add" size={18} color="#ffffff" />
              </View>
            </View>
          ))}
        </View>
        <Text style={[styles.hint, { bottom: insets.bottom + 20 }]}>Tap to add a toast, hold to stack them</Text>
      </Pressable>

      {items.map((item) => (
        <ToastCard
          key={item.id}
          item={item}
          index={place.get(item.id) ?? 0}
          stack={stack}
          width={toastWidth}
          screenWidth={width}
          reduced={reducedMotion}
          onLeave={leave}
          onGone={gone}
        />
      ))}
    </GestureHandlerRootView>
  );
}

const photo = (id: string, w: number, h = w) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

// The shop page under the toasts: a featured ride and a short list of experiences.
const FEATURE = 'photo-1641128324972-af3212f0f6bd';
const EXPERIENCES = [
  { title: 'Night lights walking tour', meta: '4.9 · 3 hours', price: '$48', photo: 'photo-1540959733332-eab4deabeeaf' },
  { title: 'Five villages by boat', meta: '4.8 · Full day', price: '$96', photo: 'photo-1516483638261-f4dbaf036963' },
  { title: 'Pyramids at first light', meta: '4.9 · 6 hours', price: '$72', photo: 'photo-1503177119275-0aa32b3a9368' },
  { title: 'Lagoon snorkel morning', meta: '4.7 · 4 hours', price: '$64', photo: 'photo-1573843981267-be1999ff37cd' },
];

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  page: { flex: 1 },
  content: { paddingHorizontal: MARGIN },
  header: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' },
  greeting: { color: MUTE, fontSize: 15, lineHeight: 20, fontWeight: '500' },
  title: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1 },
  cart: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 2,
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 8, color: 'rgba(17,19,24,0.08)' }],
  },
  badge: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#ff4d4f',
    borderWidth: 2,
    borderColor: PAGE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: '#ffffff', fontSize: 10, lineHeight: 12, fontWeight: '800' },
  search: {
    height: 44,
    borderRadius: 14,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    marginTop: 16,
    boxShadow: [{ offsetX: 0, offsetY: 1, blurRadius: 3, color: 'rgba(17,19,24,0.06)' }],
  },
  searchText: { color: MUTE, fontSize: 16, lineHeight: 21 },
  feature: {
    height: 196,
    borderRadius: 24,
    borderCurve: 'continuous',
    overflow: 'hidden',
    marginTop: 20,
    padding: 18,
    justifyContent: 'flex-end',
    backgroundColor: '#d9a066',
  },
  featureTag: {
    position: 'absolute',
    top: 16,
    left: 16,
    height: 28,
    paddingHorizontal: 12,
    borderRadius: 14,
    backgroundColor: 'rgba(255,255,255,0.92)',
    justifyContent: 'center',
  },
  featureTagText: { color: INK, fontSize: 13, lineHeight: 17, fontWeight: '700' },
  featureTitle: { color: '#ffffff', fontSize: 22, lineHeight: 27, fontWeight: '800', letterSpacing: -0.5 },
  featureMeta: { color: 'rgba(255,255,255,0.88)', fontSize: 14, lineHeight: 19, marginTop: 2 },
  sectionRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', marginTop: 24, marginBottom: 4 },
  section: { color: INK, fontSize: 20, lineHeight: 25, fontWeight: '800', letterSpacing: -0.4 },
  seeAll: { color: '#2f6bff', fontSize: 15, lineHeight: 20, fontWeight: '600' },
  row: { height: 80, flexDirection: 'row', alignItems: 'center', gap: 12 },
  rowRule: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(60,60,67,0.2)' },
  thumb: { width: 60, height: 60, borderRadius: 14 },
  rowText: { flex: 1 },
  rowTitle: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  rowMetaLine: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 3 },
  rowMeta: { color: MUTE, fontSize: 13, lineHeight: 17 },
  price: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '700' },
  add: { width: 30, height: 30, borderRadius: 15, backgroundColor: INK, alignItems: 'center', justifyContent: 'center' },
  hint: { position: 'absolute', alignSelf: 'center', color: MUTE, fontSize: 13, lineHeight: 18, fontWeight: '500' },
});
