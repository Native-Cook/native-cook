import { PressScale } from '@/shared/PressScale';
import { ProgressiveBlurViewport } from '@/shared/SkiaProgressiveBlur';
import { useMotionPreference } from '@/shared/useMotionPreference';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';
import { BlurView } from 'expo-blur';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Keyboard, KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions, type GestureResponderEvent } from 'react-native';
import Animated, { Easing, FadeIn, FadeInDown, LinearTransition, ZoomIn, cancelAnimation, interpolate, useAnimatedReaction, useAnimatedRef, useAnimatedScrollHandler, useAnimatedStyle, useDerivedValue, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

export const LOOP = 39;
const AnimatedPressable = Animated.createAnimatedComponent(Pressable);
const OUT = Easing.bezier(0.23, 1, 0.32, 1);
const OUT_FN = Easing.bezier(0.23, 1, 0.32, 1).factory();
const BOOK = require('./assets/glass-book-blue.webp');
const BOOK_GREEN = require('./assets/orb-book-green.webp');
const LENS_NEUTRAL = require('./assets/glass-lens-neutral.webp');
const LENS_GREEN = require('./assets/glass-lens-green.webp');
const GLOBE = require('./assets/glass-globe.png');
const PORTRAIT = require('./assets/glass-portrait.png');
const RECIPE = require('./assets/orb-recipe.webp');
const EVERYTHING = require('./assets/cover-everything.webp');
const AUTHOR = require('./assets/author.webp');
// Fictional friends who already joined; generated photographs, see assets/PROMPTS.md.
const AVATARS = [require('./assets/avatar-1.webp'), require('./assets/avatar-2.webp'), require('./assets/avatar-3.webp')];
const ICONS = [require('./assets/icon-1.webp'), require('./assets/icon-2.webp'), require('./assets/icon-3.webp'), require('./assets/icon-4.webp'), require('./assets/icon-5.webp'), require('./assets/icon-6.webp'), require('./assets/icon-7.webp'), require('./assets/icon-8.webp')];
const FEATURES = [
  { title: 'Non-fiction\nsummaries', name: 'Non-fiction summaries', body: 'This app gives you detailed outlines of popular non-fiction books to help you learn various topics easily.', art: BOOK_GREEN, ink: '#0B4A2E' },
  { title: 'Places to\nremember', name: 'Places to remember', body: 'Keep track of places you have been and dream of visiting with beautiful cards.', art: GLOBE, ink: '#0D3A73' },
  { title: 'Portrait\nstudio', name: 'Portrait studio', body: 'Turn your photos into hand-drawn illustrated portraits in a few seconds.', art: PORTRAIT, ink: '#0B5A5A' },
  { title: 'Pantry\nrecipes', name: 'Pantry recipes', body: 'Snap your fridge and get recipes you can make tonight.', art: RECIPE, ink: '#7A3B12' },
];
const CATEGORIES = ['Lifestyle', 'Health & Fitness', 'Mindfulness', 'Cooking', 'Learning'];
const APPS = [
  { icon: 0, name: 'Weight room tracker', handle: '@kaito', body: 'Track your workouts, log sets, and make every session count.' },
  { icon: 1, name: 'Clear air today', handle: '@priya', body: 'Get real-time air quality updates for the streets you walk.' },
  { icon: 2, name: 'Stretch break coach', handle: '@dani', body: 'Get daily stretch routines tailored to your day.' },
  { icon: 3, name: 'Interval timer', handle: '@unai', body: 'Accurate intervals for runs, rows and rests.' },
  { icon: 4, name: 'Rep counter', handle: '@noor', body: 'Log your lifts, sets, and weights effortlessly.' },
];
const RECENT = [
  { icon: 5, name: 'Daily spend tracker', handle: '@moonlit', body: 'Quickly log and manage your daily expenses with ease.' },
  { icon: 6, name: 'Fridge planner', handle: '@awa', body: 'Plan meals around what is already waiting in your fridge.' },
  { icon: 7, name: 'Liner notes', handle: '@kasra', body: 'Keep a note on every album you fall in love with.' },
  { icon: 3, name: 'Night sky log', handle: '@ines', body: 'Note the stars you found and the clouds that hid them.' },
  { icon: 0, name: 'Plant days', handle: '@theo', body: 'Gentle reminders for every pot on the windowsill.' },
];
const TOPICS = ['Family', 'Productivity', 'Mindfulness', 'Career', 'Leadership', 'Communication', 'Psychology', 'Parenting', 'Marketing', 'Design', 'Art'];
// A first pick widens the list, as the reference does, so the sheet suggests neighbouring ideas.
const MORE_TOPICS = ['Personal development', 'Entrepreneurship', 'Financial literacy', 'Emotional intelligence', 'Innovation'];
type Screen = 'explore' | 'loading' | 'library' | 'detail';
type BookId = 'rituals' | 'noticing';
type CardFrame = { x: number; y: number; width: number; height: number };
type ReturnOrigin = { kind: 'feature'; index: number } | { kind: 'recent' } | { kind: 'plus' };
type PendingOperation = { deadline: number };
type OpeningOperation = PendingOperation & { startedAt: number; duration: number };
type GenerationOperation = PendingOperation & { topics: string[]; notifyHost: boolean; coverDelay: number };
const BOOKS = {
  rituals: { title: 'Small Rituals', author: 'Mira Wells', blurb: 'A practical guide to building good habits, breaking bad ones, and mastering the small, consistent actions that lead to remarkable results.', summary: '“Small Rituals” is a gentle, practical guide to changing a day by changing its smallest moments. Mira Wells shows how a walk without your phone, a page before breakfast or one promise you can keep quietly adds up, why the repeatable step matters more than the impressive one, and how to shape your rooms, your evenings and your calendar so the good choice becomes the easy one. It draws on habit research, design and plain everyday experience to make lasting change feel lighter, kinder and entirely within reach.', lessons: ['Start with a moment you already have: tie each new ritual to something you do every day, so it never needs a reminder.', 'Make the next step small enough to repeat, even on a tired evening; consistency carries further than intensity.', 'Shape the room, not just the will: put the book on the pillow and the phone in another room.', 'Notice what feels better, not just what gets done, and let that feeling choose what you keep.'] },
  noticing: { title: 'A Brief Tour of Almost Everything', author: 'Theo Marsh', blurb: 'A curious walk through science, from the first stars to the dinosaurs and the people who worked it all out.', summary: 'A familiar street still has something to show you. Look for a new color, listen for a quiet sound, or ask a better question. This short read explores how paying attention can turn an ordinary day into a small discovery.', lessons: ['Look at a familiar place with fresh eyes.', 'Leave a little space for the unexpected.', 'Keep one small observation from each day.'] },
};

// Book covers. Small Rituals is typographic (set in code); the tour uses an illustrated plate with its title set in code.
function Cover({ book, size }: { book: BookId; size: 'row' | 'detail' }) {
  const large = size === 'detail';
  if (book === 'noticing') return <View style={[styles.cover, large ? styles.coverDetail : styles.coverRow]}>
    <Image source={EVERYTHING} style={StyleSheet.absoluteFill} contentFit="cover" />
    <View style={styles.coverTopBand}><Text allowFontScaling={false} style={[styles.tourAuthor, large && { fontSize: 13 }]}>THEO MARSH</Text><Text allowFontScaling={false} style={[styles.tourTitle, large && { fontSize: 22, lineHeight: 24 }]}>{'A BRIEF TOUR OF\nALMOST\nEVERYTHING'}</Text></View>
  </View>;
  return <View style={[styles.cover, large ? styles.coverDetail : styles.coverRow, styles.ritualsFrame]}>
    <View style={styles.ritualsPage}>
      <Text allowFontScaling={false} style={[styles.ritualsTag, large && { fontSize: 11, lineHeight: 14 }]}>{'Small Steps,\nLasting Change'}</Text>
      <Text allowFontScaling={false} style={[styles.ritualsTitle, large && { fontSize: 44, lineHeight: 44 }]}>{'Small\nRituals'}</Text>
      <Text allowFontScaling={false} style={[styles.ritualsSub, large && { fontSize: 7 }]}>A gentle way to build good days</Text>
      <Text allowFontScaling={false} style={[styles.ritualsAuthor, large && { fontSize: 15 }]}>Mira Wells</Text>
      {large && <View style={styles.ritualsBadge}><Text allowFontScaling={false} style={styles.ritualsBadgeText}>{'NEW\nSAMPLE\nREAD'}</Text></View>}
    </View>
  </View>;
}

function LoadingLens({ green = false }: { green?: boolean }) {
  return <Image source={green ? LENS_GREEN : LENS_NEUTRAL} style={{ width: 148, height: 148, opacity: green ? 1 : 0.65 }} contentFit="contain" />;
}

export function OpeningCollection({ operation, title, reduced, onCancel, previewProgress }: { operation: OpeningOperation | null; title: string; reduced: boolean; onCancel: () => void; previewProgress?: number }) {
  const insets = useSafeAreaInsets();
  const { height, fontScale } = useWindowDimensions();
  const progress = useSharedValue(previewProgress ?? 0);
  useEffect(() => {
    if (previewProgress !== undefined) { cancelAnimation(progress); progress.set(previewProgress); return; }
    if (!operation) return;
    const elapsed = Math.max(0, Date.now() - operation.startedAt);
    progress.set(reduced ? 1 : Math.min(1, elapsed / operation.duration));
    if (!reduced) progress.set(withTiming(1, { duration: Math.max(0, operation.duration - elapsed), easing: Easing.linear }));
    return () => cancelAnimation(progress);
  }, [operation, previewProgress, progress, reduced]);
  // Source f669 changes the empty neutral lens to green; the book then resolves.
  const bookAlpha = useDerivedValue(() => reduced ? 1 : interpolate(progress.get() * 2.75, [1.4667, 1.5, 1.6, 1.75, 2], [0, 0.1, 0.45, 0.85, 1], 'clamp'));
  const shell = useAnimatedStyle(() => ({ opacity: reduced || progress.get() * 2.75 >= 1.4 ? 0 : 1 }));
  const green = useAnimatedStyle(() => ({ opacity: reduced || progress.get() * 2.75 < 1.4 ? 0 : 1 - bookAlpha.get() }));
  const icon = useAnimatedStyle(() => ({ opacity: bookAlpha.get() }));
  const titleStyle = useAnimatedStyle(() => ({ opacity: reduced || progress.get() * 2.75 >= 1.4 ? 1 : 0 }));
  return <LinearGradient colors={['#FAFAFA', '#E9E9E9']} style={styles.loader}>
    <Pressable accessibilityRole="button" accessibilityLabel="Cancel opening and return to Explore" onPress={onCancel} style={[styles.roundButton, { position: 'absolute', left: 16, top: insets.top + 8 }]}><Ionicons name="chevron-back" size={21} color="#363C34" /></Pressable>
    <View style={{ position: 'absolute', top: fontScale > 1.4 ? insets.top + 64 : height * 0.36, left: 20, right: 20, alignItems: 'center' }}>
      <View style={{ width: 148, height: 148 }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Animated.View style={[StyleSheet.absoluteFill, shell]}><LoadingLens /></Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, green]}><LoadingLens green /></Animated.View>
        <Animated.View style={[StyleSheet.absoluteFill, icon]}><Image source={BOOK} style={{ width: 148, height: 148 }} contentFit="contain" /></Animated.View>
      </View>
      <Animated.Text style={[styles.loaderTitle, { marginTop: 16, fontSize: 19, textAlign: 'center' }, titleStyle]}>{title}</Animated.Text>
      <Text accessibilityLiveRegion="polite" style={[styles.loaderCopy, { marginTop: 6, fontSize: 13, textAlign: 'center' }]}>Loading…</Text>
    </View>
  </LinearGradient>;
}

function Button({ children, onPress, disabled, dark = false, secondary = false, pill = false, icon, reduced = false, busy = false }: { children: string; onPress: () => void; disabled?: boolean; dark?: boolean; secondary?: boolean; pill?: boolean; icon?: 'heart' | 'heart-outline' | 'add' | 'shuffle' | 'arrow-down'; reduced?: boolean; busy?: boolean }) {
  const ink = secondary ? '#262626' : '#FFFFFF';
  return <PressScale accessibilityRole="button" accessibilityLabel={children} accessibilityState={{ disabled: !!disabled, busy }} disabled={disabled} reduced={reduced} onPress={onPress} style={[styles.button, dark && styles.buttonDark, secondary && styles.buttonSecondary, pill && styles.buttonPill, (disabled || busy) && styles.buttonBusy]}>
    {icon && <Animated.View key={icon} entering={icon === 'heart' && !reduced ? ZoomIn.springify().damping(11).stiffness(300) : undefined}><Ionicons name={icon} size={icon === 'add' ? 18 : 17} color={icon === 'heart' ? '#D9534F' : ink} /></Animated.View>}
    <Text style={[styles.buttonLabel, { color: ink }, (disabled || busy) && { color: '#FFFFFFD9' }]}>{children}</Text>
  </PressScale>;
}

// A tiny "working" mark: three soft dots, as the reference shows beside its status line.
function Working() {
  return <View style={styles.working}>{[0, 1, 2].map(i => <View key={i} style={[styles.workingDot, { opacity: 1 - i * 0.22 }]} />)}</View>;
}

function AppRow({ app, onPress }: { app: typeof APPS[number]; onPress: () => void }) {
  return <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.appRow, pressed && { opacity: 0.6 }]}>
    <Image source={ICONS[app.icon]} style={styles.appIcon} contentFit="cover" />
    <View style={{ flex: 1 }}><Text style={styles.appName} numberOfLines={1}>{app.name}</Text><Text style={styles.appHandle}>{app.handle}</Text><Text style={styles.appDescription} numberOfLines={2}>{app.body}</Text></View>
  </Pressable>;
}

export function FolioFeatureLibrary({ autoPlay = false, onBookCreated }: { autoPlay?: boolean; onBookCreated?: (topics: string[]) => void }) {
  const insets = useSafeAreaInsets();
  const { width, height, fontScale } = useWindowDimensions();
  const reduced = useMotionPreference();
  const [screen, setScreen] = useState<Screen>('explore');
  const screenRef = useRef<Screen>('explore');
  const [topicsOpen, setTopicsOpen] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [customTopic, setCustomTopic] = useState('');
  const [topicInputActive, setTopicInputActive] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generated, setGenerated] = useState(false);
  const [coverReady, setCoverReady] = useState(false);
  const [detailReady, setDetailReady] = useState(false);
  const [saved, setSaved] = useState(false);
  const [, setReading] = useState(false);
  const [selectedBookId, setSelectedBookId] = useState<BookId>('noticing');
  const [bookmarks, setBookmarks] = useState<BookId[]>([]);
  const [manual, setManual] = useState(false);
  const [foreground, setForeground] = useState(true);
  const [activeCategory, setActiveCategory] = useState('Health & Fitness');
  const [addingTopic, setAddingTopic] = useState(false);
  const [authorReady, setAuthorReady] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [feature, setFeature] = useState(0);
  const [introSize, setIntroSize] = useState(310);
  const [introAccessible, setIntroAccessible] = useState(true);
  const [openingOperation, setOpeningOperation] = useState<OpeningOperation | null>(null);
  const [generationOperation, setGenerationOperation] = useState<GenerationOperation | null>(null);
  const [coverOperation, setCoverOperation] = useState<PendingOperation | null>(null);
  const [detailOperation, setDetailOperation] = useState<PendingOperation | null>(null);
  const root = useRef<View>(null);
  const featureNodes = useRef<(View | null)[]>([]);
  const recentCard = useRef<View>(null);
  const plusButton = useRef<View>(null);
  const returnOrigin = useRef<ReturnOrigin>({ kind: 'feature', index: 0 });
  const originRequest = useRef(0);
  const originTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const outer = useRef<ScrollView>(null);
  const carousel = useRef<ScrollView>(null);
  const detail = useRef<ScrollView>(null);
  const topicScroll = useRef<ScrollView>(null);
  const topicInputFocused = useRef(false);
  const library = useAnimatedRef<ScrollView>();
  const resume = useRef<ReturnType<typeof setTimeout> | null>(null);
  const headerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mounted = useRef(true);
  const foregroundRef = useRef(true);
  const userOwned = useRef(false);
  const touching = useRef(false);
  const dragging = useRef(new Set<string>());
  const momentum = useRef(new Set<string>());
  const appProgress = useSharedValue(0);
  const detailProgress = useSharedValue(0);
  const shimmer = useSharedValue(0);
  const scrollY = useSharedValue(0);
  const introHeight = useSharedValue(310);
  const headerCollapse = useSharedValue(0);
  const collapsing = useSharedValue(false);
  const cardWidth = Math.min(248, width * 0.63);
  const [originFrame, setOriginFrame] = useState<CardFrame>({ x: 16, y: insets.top + 78, width: cardWidth, height: 292 });
  const origin = useSharedValue<CardFrame>({ x: 16, y: insets.top + 78, width: cardWidth, height: 292 });
  const queueResume = useCallback(() => {
    if (resume.current) clearTimeout(resume.current);
    resume.current = null;
    if (!autoPlay || reduced || !foreground || notice !== null || topicsOpen || screen !== 'explore' || userOwned.current || touching.current || dragging.current.size || momentum.current.size) return;
    resume.current = setTimeout(() => {
      resume.current = null;
      if (mounted.current && !userOwned.current && !touching.current && !dragging.current.size && !momentum.current.size) setManual(false);
    }, 2000);
  }, [autoPlay, foreground, notice, reduced, screen, topicsOpen]);
  const interrupt = useCallback(() => {
    setManual(true);
    if (resume.current) clearTimeout(resume.current);
    resume.current = null;
    if (headerTimer.current) { clearTimeout(headerTimer.current); headerTimer.current = null; }
    if (originTimeout.current) { clearTimeout(originTimeout.current); originTimeout.current = null; }
    originRequest.current++;
    cancelAnimation(headerCollapse); collapsing.set(false);
    // Once a person uses a library, later demo cycles must never reset its data.
    if (screen !== 'explore') userOwned.current = true;
    queueResume();
  }, [collapsing, headerCollapse, queueResume, screen]);
  const touchBegin = () => { touching.current = true; interrupt(); };
  const touchEnd = (event: GestureResponderEvent) => { touching.current = event.nativeEvent.touches.length > 0; queueResume(); };
  const scrollBegin = (key: string, isMomentum = false) => {
    // Native programmatic demo scrolling also emits momentum callbacks.
    if (isMomentum && !manual && !dragging.current.has(key) && !touching.current) return;
    (isMomentum ? momentum : dragging).current.add(key); interrupt();
  };
  const scrollEnd = (key: string, isMomentum = false) => {
    (isMomentum ? momentum : dragging).current.delete(key); queueResume();
  };
  const resolveReturnNode = useCallback(() => {
    const trigger = returnOrigin.current;
    return trigger.kind === 'recent' ? recentCard.current : trigger.kind === 'plus' ? plusButton.current : featureNodes.current[trigger.index] ?? null;
  }, []);
  const captureOrigin = useCallback((node: View | null, done: () => void) => {
    if (originTimeout.current) { clearTimeout(originTimeout.current); originTimeout.current = null; }
    const request = ++originRequest.current;
    if (!node || !root.current) { done(); return; }
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      clearTimeout(timeout);
      if (originTimeout.current === timeout) originTimeout.current = null;
      if (mounted.current && request === originRequest.current) done();
    };
    // A disconnected native host can omit its measurement callback. Closing must
    // still finish using the last known frame if a type-scale remount races it.
    const timeout = setTimeout(finish, 120);
    originTimeout.current = timeout;
    try { root.current.measureInWindow((rootX, rootY) => {
      if (finished || request !== originRequest.current) return;
      try { node.measureInWindow((x, y, measuredWidth, measuredHeight) => {
        if (finished || !mounted.current || request !== originRequest.current) return;
        if (measuredWidth > 0 && measuredHeight > 0) {
          const frame = { x: x - rootX, y: y - rootY, width: measuredWidth, height: measuredHeight };
          origin.set(frame); setOriginFrame(frame);
        }
        finish();
      });
      } catch { finish(); }
    }); } catch { finish(); }
  }, [origin]);
  const moveTo = useCallback((next: Screen) => {
    if (next !== 'explore' && resume.current) { clearTimeout(resume.current); resume.current = null; }
    screenRef.current = next; setScreen(next);
    const instant = reduced || !foregroundRef.current;
    appProgress.set(instant ? (next === 'explore' ? 0 : 1) : withTiming(next === 'explore' ? 0 : 1, { duration: next === 'explore' ? 220 : 280, easing: OUT }));
    detailProgress.set(instant ? (next === 'detail' ? 1 : 0) : withTiming(next === 'detail' ? 1 : 0, { duration: 260, easing: OUT }));
  }, [appProgress, detailProgress, reduced]);
  const collapseHeader = useCallback(() => {
    if (!foregroundRef.current || !mounted.current || touching.current || dragging.current.size || momentum.current.size) return;
    collapsing.set(true);
    headerCollapse.set(reduced ? 1 : withTiming(1, { duration: 1000, easing: OUT }));
  }, [collapsing, headerCollapse, reduced]);
  const beginOpening = useCallback((duration: number) => {
    const startedAt = Date.now();
    setOpeningOperation({ startedAt, duration, deadline: startedAt + duration });
    moveTo('loading');
  }, [moveTo]);
  const beginDetail = useCallback((book: BookId, duration: number) => {
    setSelectedBookId(book); setDetailReady(false); setReading(false);
    setDetailOperation({ deadline: Date.now() + duration });
    detail.current?.scrollTo({ y: 0, animated: false }); moveTo('detail');
  }, [moveTo]);
  const beginGeneration = useCallback((topics: string[], duration: number, coverDelay: number, notifyHost: boolean) => {
    setGenerating(true);
    setGenerationOperation({ deadline: Date.now() + duration, topics: [...topics], coverDelay, notifyHost });
  }, []);
  const openFeature = (index: number, node?: View | null) => {
    interrupt(); setOpeningOperation(null); setDetailOperation(null);
    if (index !== 0) { setFeature(index); setNotice(index === 1 ? 'Keep a little list of places that spark your curiosity. Start with the coast, a forest path, or a city you have never seen.' : 'A small portrait can become a different way to remember a moment. This original illustration is a preview from the collection.'); return; }
    returnOrigin.current = node && node === recentCard.current ? { kind: 'recent' } : node && node === plusButton.current ? { kind: 'plus' } : { kind: 'feature', index };
    userOwned.current = true;
    if (resume.current) { clearTimeout(resume.current); resume.current = null; }
    setFeature(index); scrollY.set(0); headerCollapse.set(0);
    captureOrigin(node ?? featureNodes.current[index], () => beginOpening(reduced ? 150 : 900));
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  };
  const openDetail = (book: BookId) => {
    interrupt(); beginDetail(book, reduced ? 120 : 650);
  };
  const closeLibrary = () => {
    interrupt(); setOpeningOperation(null); setDetailOperation(null);
    captureOrigin(resolveReturnNode(), () => moveTo('explore'));
  };
  const toggleBookmark = () => {
    interrupt(); setBookmarks(current => current.includes(selectedBookId) ? current.filter(book => book !== selectedBookId) : [...current, selectedBookId]);
    void Haptics.selectionAsync();
  };
  const generate = () => {
    if (selected.length === 0 || generating) return;
    Keyboard.dismiss(); setTopicInputActive(false);
    interrupt(); beginGeneration(selected, reduced ? 250 : 1600, reduced ? 120 : 700, true);
  };
  const closeTopics = () => { Keyboard.dismiss(); setTopicInputActive(false); interrupt(); setGenerationOperation(null); setTopicsOpen(false); setGenerating(false); };
  // These operations belong to the visible state, not the disposable demo timeline.
  // Their deadlines survive touch handoff, foreground changes and unrelated rerenders.
  useEffect(() => {
    if (!openingOperation) return;
    const timer = setTimeout(() => {
      setOpeningOperation(null); moveTo('library');
      headerTimer.current = setTimeout(() => { headerTimer.current = null; collapseHeader(); }, Math.max(0, openingOperation.deadline + 1000 - Date.now()));
    }, Math.max(0, openingOperation.deadline - Date.now()));
    return () => clearTimeout(timer);
  }, [collapseHeader, moveTo, openingOperation]);
  useEffect(() => {
    if (!generationOperation) return;
    const timer = setTimeout(() => {
      setGenerationOperation(null); setGenerated(true); setCoverReady(false); setGenerating(false); setTopicsOpen(false);
      setCoverOperation({ deadline: generationOperation.deadline + generationOperation.coverDelay });
      if (generationOperation.notifyHost) { onBookCreated?.(generationOperation.topics); void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); }
    }, Math.max(0, generationOperation.deadline - Date.now()));
    return () => clearTimeout(timer);
  }, [generationOperation, onBookCreated]);
  useEffect(() => {
    if (!coverOperation) return;
    const timer = setTimeout(() => { setCoverOperation(null); setCoverReady(true); }, Math.max(0, coverOperation.deadline - Date.now()));
    return () => clearTimeout(timer);
  }, [coverOperation]);
  // The author's portrait arrives a little after the text, as images do.
  useEffect(() => {
    const timer = setTimeout(() => setAuthorReady(detailReady), detailReady && !reduced ? 4400 : 0);
    return () => clearTimeout(timer);
  }, [detailReady, reduced]);
  useEffect(() => {
    if (!detailOperation) return;
    const timer = setTimeout(() => { setDetailOperation(null); setDetailReady(true); }, Math.max(0, detailOperation.deadline - Date.now()));
    return () => clearTimeout(timer);
  }, [detailOperation]);
  const onLibraryScroll = useAnimatedScrollHandler(event => { scrollY.set(event.contentOffset.y); });
  // The app header collapses once by itself (demo), or as soon as the library is scrolled.
  const collapse = useDerivedValue(() => Math.max(headerCollapse.get(), Math.min(1, Math.max(0, scrollY.get() / 90))));
  useAnimatedReaction(() => collapse.get() < 0.5, (visible, previous) => {
    if (visible !== previous) scheduleOnRN(setIntroAccessible, visible);
  });
  useEffect(() => {
    if (!topicsOpen) return;
    const keyboard = Keyboard.addListener('keyboardDidShow', () => {
      if (topicInputFocused.current) topicScroll.current?.scrollToEnd({ animated: !reduced });
    });
    return () => keyboard.remove();
  }, [reduced, topicsOpen]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', state => {
      foregroundRef.current = state === 'active';
      setForeground(state === 'active');
      if (state !== 'active') {
        touching.current = false; dragging.current.clear(); momentum.current.clear(); originRequest.current++;
        if (headerTimer.current) { clearTimeout(headerTimer.current); headerTimer.current = null; }
        if (originTimeout.current) { clearTimeout(originTimeout.current); originTimeout.current = null; }
        cancelAnimation(appProgress); cancelAnimation(detailProgress); cancelAnimation(headerCollapse); collapsing.set(false);
      } else {
        appProgress.set(screenRef.current === 'explore' ? 0 : 1);
        detailProgress.set(screenRef.current === 'detail' ? 1 : 0);
      }
    });
    return () => sub.remove();
  }, [appProgress, collapsing, detailProgress, headerCollapse]);
  useEffect(() => {
    if (manual) queueResume();
    return () => { if (resume.current) clearTimeout(resume.current); };
  }, [manual, queueResume]);
  useEffect(() => {
    if (!reduced && foreground && (generating || screen === 'loading' || (generated && !coverReady))) shimmer.set(withRepeat(withTiming(1, { duration: 900, easing: Easing.linear }), -1));
    else { cancelAnimation(shimmer); shimmer.set(0); }
    return () => cancelAnimation(shimmer);
  }, [coverReady, foreground, generated, generating, reduced, screen, shimmer]);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; if (resume.current) clearTimeout(resume.current); if (headerTimer.current) clearTimeout(headerTimer.current); if (originTimeout.current) clearTimeout(originTimeout.current); cancelAnimation(appProgress); cancelAnimation(detailProgress); cancelAnimation(headerCollapse); };
  }, [appProgress, detailProgress, headerCollapse]);
  useEffect(() => {
    if (!autoPlay || manual || reduced || !foreground || notice !== null || userOwned.current) return;
    const timeline = new Set<ReturnType<typeof setTimeout>>();
    const at = (seconds: number, action: () => void) => {
      const timer = setTimeout(() => { timeline.delete(timer); if (mounted.current) action(); }, seconds * 1000);
      timeline.add(timer);
    };
    const cycle = () => {
      returnOrigin.current = { kind: 'feature', index: 0 }; originRequest.current++;
      if (originTimeout.current) { clearTimeout(originTimeout.current); originTimeout.current = null; }
      screenRef.current = 'explore';
      setOpeningOperation(null); setGenerationOperation(null); setCoverOperation(null); setDetailOperation(null);
      setScreen('explore'); setFeature(0); setGenerated(false); setCoverReady(false); setSelected([]); setTopicsOpen(false); setGenerating(false); setSaved(false); setBookmarks([]); setSelectedBookId('noticing'); setReading(false); setDetailReady(false); appProgress.set(0); detailProgress.set(0); scrollY.set(0); collapsing.set(false); headerCollapse.set(0);
      outer.current?.scrollTo({ y: 0, animated: false }); carousel.current?.scrollTo({ x: 0, animated: false });
      at(0.5, () => carousel.current?.scrollTo({ x: cardWidth + 12, animated: true }));
      at(1.5, () => carousel.current?.scrollTo({ x: (cardWidth + 12) * 2, animated: true }));
      at(2.5, () => carousel.current?.scrollTo({ x: 0, animated: true }));
      at(3.5, () => outer.current?.scrollTo({ y: 240, animated: true }));
      at(5.5, () => outer.current?.scrollTo({ y: 480, animated: true }));
      at(6.6, () => outer.current?.scrollTo({ y: 900, animated: true }));
      at(8, () => outer.current?.scrollTo({ y: 0, animated: true }));
      at(9.75, () => captureOrigin(featureNodes.current[0], () => beginOpening(2750)));
      at(16, () => setTopicsOpen(true));
      at(17.25, () => setSelected(['Productivity']));
      at(18.25, () => beginGeneration(['Productivity'], 4250, 1500, false));
      at(26.5, () => beginDetail('rituals', 2000));
      // The reading pass of the reference: a short lift, a long read down to the author, back to the top.
      at(29.8, () => detail.current?.scrollTo({ y: 170, animated: true }));
      at(30.6, () => detail.current?.scrollTo({ y: 720, animated: true }));
      at(31.8, () => detail.current?.scrollTo({ y: 960, animated: true }));
      at(32.8, () => detail.current?.scrollToEnd({ animated: true }));
      at(33.8, () => detail.current?.scrollTo({ y: 170, animated: true }));
      at(34.6, () => detail.current?.scrollTo({ y: 0, animated: true }));
      at(35.5, () => moveTo('library'));
      at(36.75, () => captureOrigin(resolveReturnNode(), () => moveTo('explore')));
      at(LOOP, cycle);
    };
    cycle();
    return () => { timeline.forEach(clearTimeout); timeline.clear(); cancelAnimation(headerCollapse); collapsing.set(false); };
  }, [appProgress, autoPlay, beginDetail, beginGeneration, beginOpening, captureOrigin, cardWidth, collapsing, detailProgress, foreground, headerCollapse, manual, moveTo, notice, reduced, resolveReturnNode, scrollY]);

  const appStyle = useAnimatedStyle(() => {
    const p = appProgress.get();
    const frame = origin.get();
    return {
      opacity: reduced ? p : interpolate(p, [0, 0.025, 1], [0, 1, 1]),
      transform: [
        { translateX: reduced ? 0 : (frame.x + frame.width / 2 - width / 2) * (1 - p) },
        { translateY: reduced ? 0 : (frame.y + frame.height / 2 - height / 2) * (1 - p) },
        { scaleX: reduced ? 1 : frame.width / width + (1 - frame.width / width) * p },
        { scaleY: reduced ? 1 : frame.height / height + (1 - frame.height / height) * p },
      ],
    };
  });
  const appContentStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 : interpolate(appProgress.get(), [0.35, 0.75], [0, 1], 'clamp') }));
  const cardBridgeStyle = useAnimatedStyle(() => {
    const p = appProgress.get();
    const frame = origin.get();
    return {
      opacity: reduced ? 0 : interpolate(p, [0, 0.025, 0.18, 0.6, 1], [0, 1, 1, 0, 0]),
      transform: [{ translateX: (width / 2 - frame.x - frame.width / 2) * p }, { translateY: (height / 2 - frame.y - frame.height / 2) * p }, { scale: 1 + p * 0.35 }],
    };
  });
  const detailStyle = useAnimatedStyle(() => ({ opacity: reduced ? detailProgress.get() : 1, transform: [{ translateX: reduced ? 0 : (1 - detailProgress.get()) * width }] }));
  const libraryStyle = useAnimatedStyle(() => ({ transform: [{ translateX: reduced ? 0 : -detailProgress.get() * width * 0.22 }] }));
  // Header morph: the full app card shrinks into a small tile around its orb, which stays where it is;
  // the library list rides up behind it.
  const collapsedTile = 76;
  const tileTop = insets.top - 6;
  const headerCard = useAnimatedStyle(() => {
    const k = reduced ? (collapse.get() > 0.5 ? 1 : 0) : OUT_FN(collapse.get());
    const w = (width - 20) + (collapsedTile - (width - 20)) * k;
    return { width: w, left: (width - w) / 2, height: introHeight.get() + (collapsedTile - introHeight.get()) * k, top: tileTop * k, borderRadius: 30 - 8 * k };
  });
  const headerOrb = useAnimatedStyle(() => {
    const k = reduced ? (collapse.get() > 0.5 ? 1 : 0) : OUT_FN(collapse.get());
    const w = (width - 20) + (collapsedTile - (width - 20)) * k;
    return { left: (w - 60) / 2, top: insets.top + 2 - tileTop * k };
  });
  const headerContent = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, collapse.get() * 2.6), transform: [{ translateY: -14 * collapse.get() }] }));
  const listShift = useAnimatedStyle(() => ({ transform: [{ translateY: -(introHeight.get() - (tileTop + collapsedTile)) * (reduced ? (headerCollapse.get() > 0.5 ? 1 : 0) : OUT_FN(headerCollapse.get())) }] }));
  const shimmerStyle = useAnimatedStyle(() => ({ opacity: reduced ? 0 : 0.5, transform: [{ translateX: interpolate(shimmer.get(), [0, 1], [-100, 160]) }] }));
  const libraryVisible = screen === 'library' && !topicsOpen && notice === null;
  const detailVisible = screen === 'detail' && !topicsOpen && notice === null;
  const exploreVisible = screen === 'explore' && notice === null;
  const selectedBook = BOOKS[selectedBookId];
  const bookmarked = bookmarks.includes(selectedBookId);

  const statusText = generating || (generated && !coverReady) ? 'Creating content' : screen === 'detail' && !authorReady ? (detailReady ? 'Searching images' : 'Creating content') : null;
  const topicList = selected.length > 0 ? [...TOPICS, ...MORE_TOPICS] : TOPICS;
  const addTopic = () => { const value = customTopic.trim(); if (value && !selected.includes(value)) setSelected(items => [...items, value]); setCustomTopic(''); setAddingTopic(false); };

  return <View key={`type-${fontScale}`} ref={root} style={styles.screen} onTouchStart={touchBegin} onTouchEnd={touchEnd} onTouchCancel={() => { touching.current = false; queueResume(); }}>
    <StatusBar style="dark" />
    <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={28} enabled={exploreVisible}><ScrollView ref={outer} onScrollBeginDrag={() => scrollBegin('outer')} onScrollEndDrag={() => scrollEnd('outer')} onMomentumScrollBegin={() => scrollBegin('outer', true)} onMomentumScrollEnd={() => scrollEnd('outer', true)} showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingTop: insets.top - 14, paddingBottom: insets.bottom + 110 }} pointerEvents={exploreVisible ? 'auto' : 'none'} accessibilityElementsHidden={!exploreVisible} importantForAccessibility={exploreVisible ? 'auto' : 'no-hide-descendants'}>
      <Text maxFontSizeMultiplier={1.7} style={styles.sectionTitle}>Featured</Text>
      <ScrollView ref={carousel} horizontal showsHorizontalScrollIndicator={false} snapToInterval={cardWidth + 14} decelerationRate="fast" contentContainerStyle={styles.featureTrack} onScrollBeginDrag={() => scrollBegin('carousel')} onScrollEndDrag={() => scrollEnd('carousel')} onMomentumScrollBegin={() => scrollBegin('carousel', true)} onMomentumScrollEnd={() => scrollEnd('carousel', true)}>
        {FEATURES.map((item, index) => <PressScale scale={0.98} reduced={reduced} ref={(node: View | null) => { featureNodes.current[index] = node; }} key={item.name} accessibilityRole="button" accessibilityLabel={`Open ${item.name}`} onPress={() => openFeature(index)} style={[styles.featureCard, { width: cardWidth }]}><Image source={item.art} style={styles.featureArt} contentFit="contain" /><Text maxFontSizeMultiplier={1.5} style={[styles.featureTitle, { color: item.ink }]}>{item.title}</Text><Text style={styles.featureBody} numberOfLines={3}>{item.body}</Text></PressScale>)}
      </ScrollView>
      <Text maxFontSizeMultiplier={1.7} style={styles.sectionTitle}>Invites</Text>
      <PressScale scale={0.98} reduced={reduced} accessibilityRole="button" onPress={() => { interrupt(); setNotice('Your invitation is ready. Explore any collection to make it yours.'); }} style={styles.invite}><View style={{ flex: 1 }}><Text maxFontSizeMultiplier={1.6} style={styles.inviteTitle}>You’ve got 1 Invite!</Text><Text style={styles.inviteBody}>Great apps start with great people. Invite friends with taste who can help improve Folio.</Text></View><View style={styles.avatarCluster}>{AVATARS.map((source, i) => <Image key={i} source={source} style={[styles.avatar, [styles.avatarA, styles.avatarB, styles.avatarC][i]]} contentFit="cover" accessible={false} />)}<View style={styles.avatarPlus}><Ionicons name="add" size={12} color="#FFFFFF" /></View></View></PressScale>
      <View style={styles.sectionRow}><Text maxFontSizeMultiplier={1.7} style={[styles.sectionTitle, { marginBottom: 0, marginTop: 0 }]}>Popular</Text><View style={styles.chevronCircle}><Ionicons name="chevron-forward" size={16} color="#3A3A3A" /></View></View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.categories} onScrollBeginDrag={() => scrollBegin('categories')} onScrollEndDrag={() => scrollEnd('categories')} onMomentumScrollBegin={() => scrollBegin('categories', true)} onMomentumScrollEnd={() => scrollEnd('categories', true)}>{CATEGORIES.map(category => <Pressable accessibilityRole="tab" accessibilityState={{ selected: activeCategory === category }} key={category} onPress={() => { interrupt(); setActiveCategory(category); void Haptics.selectionAsync(); }} style={[styles.category, activeCategory === category && styles.categoryActive]}><Text style={[styles.categoryLabel, activeCategory === category && { color: '#FFFFFF' }]}>{category}</Text></Pressable>)}</ScrollView>
      <View style={styles.appRows}>{APPS.filter((_, i) => activeCategory === 'Health & Fitness' || (i + CATEGORIES.indexOf(activeCategory)) % 2 === 0).map(app => <AppRow key={app.name} app={app} onPress={() => { interrupt(); setNotice(`${app.name}: ${app.body}`); }} />)}</View>
      <View style={[styles.sectionRow, { marginTop: 14 }]}><Text maxFontSizeMultiplier={1.7} style={[styles.sectionTitle, { marginBottom: 0, marginTop: 0 }]}>Recently added</Text><View style={styles.chevronCircle}><Ionicons name="chevron-forward" size={16} color="#3A3A3A" /></View></View>
      <View ref={recentCard} style={styles.appRows}>{RECENT.map(app => <AppRow key={app.name} app={app} onPress={() => openFeature(0, recentCard.current)} />)}</View>
    </ScrollView></ProgressiveBlurViewport>
    <View pointerEvents={exploreVisible ? 'auto' : 'none'} accessibilityElementsHidden={!exploreVisible} importantForAccessibility={exploreVisible ? 'auto' : 'no-hide-descendants'} style={[styles.floatingNav, { bottom: insets.bottom - 14, opacity: screen === 'explore' ? 1 : 0 }]}>
      <View style={styles.navPill}><BlurView intensity={40} tint="light" style={StyleSheet.absoluteFill} /><Pressable accessibilityRole="button" onPress={() => { interrupt(); outer.current?.scrollTo({ y: 0, animated: !reduced }); }} style={styles.navItem}><LinearGradient pointerEvents="none" colors={['#9FD8FF', '#D9F0FF00']} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={styles.navGlow} /><Text style={styles.navInactive}>Home</Text></Pressable><View style={[styles.navItem, styles.navSelected]}><Text style={styles.navLabel}>Explore</Text></View></View>
      <Pressable ref={plusButton} accessibilityRole="button" accessibilityLabel="Create a collection" onPress={() => openFeature(0, plusButton.current)} style={styles.plus}><Ionicons name="add" size={26} color="#FFFFFF" /></Pressable>
    </View>

    <Animated.View style={[styles.appSurface, appStyle]} pointerEvents={screen === 'explore' || topicsOpen || notice !== null ? 'none' : 'auto'} accessibilityElementsHidden={screen === 'explore' || topicsOpen || notice !== null} importantForAccessibility={screen === 'explore' || topicsOpen || notice !== null ? 'no-hide-descendants' : 'auto'}>
      <Animated.View style={[{ flex: 1 }, appContentStyle]}>
      {screen === 'loading' ? <OpeningCollection operation={openingOperation} title={FEATURES[feature].name} reduced={reduced} onCancel={closeLibrary} /> : <>
        <Animated.View style={[{ flex: 1 }, libraryStyle]} pointerEvents={libraryVisible ? 'auto' : 'none'} accessibilityElementsHidden={!libraryVisible} importantForAccessibility={libraryVisible ? 'auto' : 'no-hide-descendants'}>
        <Animated.View style={[{ flex: 1 }, listShift]}>
        <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={28} enabled={libraryVisible}><Animated.ScrollView ref={library} onScroll={onLibraryScroll} scrollEventThrottle={16} showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingTop: introSize + 18, paddingHorizontal: 18, paddingBottom: 130 + insets.bottom, minHeight: height + introSize }}>
          <View style={styles.filterRow}><Text style={styles.filterLabel}>FILTER LIBRARY</Text><View style={styles.allBooks}><Text style={styles.allBooksText}>All Books</Text><Ionicons name="chevron-down" size={15} color="#2A2A2A" /></View></View>
          {generated && <Animated.View entering={reduced ? undefined : FadeInDown.duration(420).easing(OUT).withInitialValues({ opacity: 0, transform: [{ translateY: -14 }] })}><PressScale scale={0.98} reduced={reduced} accessibilityRole="button" accessibilityLabel="Read Small Rituals" onPress={() => openDetail('rituals')} style={styles.bookCard}>{coverReady ? <Animated.View entering={reduced ? undefined : FadeIn.duration(360)}><Cover book="rituals" size="row" /></Animated.View> : <View style={styles.coverPlaceholder}><Animated.View style={[styles.shimmer, shimmerStyle]}><LinearGradient colors={['#FFFFFF00', '#FFFFFFCC', '#FFFFFF00']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 0 }} style={StyleSheet.absoluteFill} /></Animated.View></View>}<View style={styles.bookCopy}><Text style={styles.bookName}>Small Rituals</Text><Text style={styles.bookMeta}>Mira Wells</Text><Text style={styles.bookNote} numberOfLines={3}>{BOOKS.rituals.blurb}</Text></View></PressScale></Animated.View>}
          <Animated.View layout={reduced ? undefined : LinearTransition.duration(320).easing(OUT)}><PressScale scale={0.98} reduced={reduced} accessibilityRole="button" accessibilityLabel="Read A Brief Tour of Almost Everything" onPress={() => openDetail('noticing')} style={styles.bookCard}><Cover book="noticing" size="row" /><View style={styles.bookCopy}><Text style={styles.bookName} numberOfLines={2}>A Brief Tour of Almost Everything</Text><Text style={styles.bookMeta}>Theo Marsh</Text><Text style={styles.bookNote} numberOfLines={3}>{BOOKS.noticing.blurb}</Text></View></PressScale></Animated.View>
        </Animated.ScrollView></ProgressiveBlurViewport>
        </Animated.View>
        <Animated.View pointerEvents="box-none" style={[styles.headerCard, headerCard]}>
          <Animated.View style={[styles.headerOrb, headerOrb]}><Image source={BOOK} style={{ width: 60, height: 60 }} contentFit="contain" /></Animated.View>
          <Animated.View pointerEvents={introAccessible && libraryVisible ? 'auto' : 'none'} accessibilityElementsHidden={!introAccessible} importantForAccessibility={introAccessible ? 'auto' : 'no-hide-descendants'} onLayout={event => { const h = Math.ceil(event.nativeEvent.layout.height); if (Math.abs(h - introHeight.get()) > 1) { introHeight.set(h); setIntroSize(h); } }} style={[styles.headerContent, { paddingTop: insets.top + 76, width: width - 20 }, headerContent]}>
            <View style={styles.chipRow}><View style={styles.chip}><Image source={AVATARS[0]} style={styles.chipAvatar} contentFit="cover" /><Text style={styles.chipText}>mirawells</Text></View><View style={styles.chip}><Ionicons name="globe-outline" size={15} color="#2A2A2A" /><Text style={styles.chipText}>Public</Text></View></View>
            <Text style={styles.libraryTitle}>{FEATURES[feature].name}</Text>
            <Text style={styles.libraryBody}>{FEATURES[feature].body}</Text>
            <View style={styles.utilityRow}><Button reduced={reduced} dark icon="shuffle" onPress={() => { interrupt(); setTopicsOpen(true); }}>Remix</Button><Button reduced={reduced} dark icon="arrow-down" onPress={() => { interrupt(); setSaved(value => !value); }}>{saved ? 'Added' : 'Get'}</Button></View>
            <View style={styles.grabber} />
          </Animated.View>
        </Animated.View>
        <View pointerEvents="box-none" style={[styles.appBar, { paddingTop: insets.top + 8 }]}>
          <Pressable accessibilityRole="button" accessibilityLabel="Back to Explore" onPress={closeLibrary} style={styles.circleButton}><Ionicons name="chevron-back" size={22} color="#1F1F1F" /></Pressable>
          {statusText ? <Animated.View entering={FadeIn.duration(200)} style={styles.statusPill}><Working /><Text style={styles.statusText}>{statusText}</Text></Animated.View> : <View />}
          <Pressable accessibilityRole="button" accessibilityLabel="Share collection" onPress={() => { interrupt(); setNotice('A personal shelf of original sample stories. Generated books stay in this session when you go back.'); }} style={styles.circleButton}><Ionicons name="share-outline" size={20} color="#1F1F1F" /></Pressable>
        </View>
        <View style={[styles.libraryFooter, { paddingBottom: insets.bottom + 14 }]} pointerEvents="box-none"><Button reduced={reduced} pill icon="add" onPress={() => { interrupt(); setTopicsOpen(true); }}>Generate New</Button></View>
        </Animated.View>
      </>}
      <Animated.View style={[styles.detailSurface, detailStyle]} pointerEvents={detailVisible ? 'auto' : 'none'} accessibilityElementsHidden={!detailVisible} importantForAccessibility={detailVisible ? 'auto' : 'no-hide-descendants'}>
        <ProgressiveBlurViewport style={{ flex: 1 }} tint="light" edgeSize={28} enabled={detailVisible}><ScrollView ref={detail} contentContainerStyle={{ paddingTop: insets.top + 64, paddingHorizontal: 18, paddingBottom: insets.bottom + 40 }} showsVerticalScrollIndicator={false}>
          <View style={styles.detailCover}><Cover book={selectedBookId} size="detail" /></View>
          <Text style={styles.detailTitle}>{selectedBook.title}</Text><Text style={styles.detailAuthor}>{selectedBook.author}</Text>
          <View style={styles.detailActions}><Button reduced={reduced} secondary icon={bookmarked ? 'heart' : 'heart-outline'} onPress={toggleBookmark}>{bookmarked ? 'Saved to Favorites' : 'Save to Favorites'}</Button><Button reduced={reduced} onPress={() => { interrupt(); setDetailOperation(null); setReading(true); setDetailReady(true); detail.current?.scrollTo({ y: 560, animated: !reduced }); }}>Read</Button></View>
          <View style={styles.section}><Text style={styles.contentTitle}>What’s it about?</Text>{(detailReady || reduced) && <Animated.Text entering={reduced ? undefined : FadeIn.duration(320)} style={styles.paragraph}>{selectedBook.summary}</Animated.Text>}</View>
          <View style={styles.section}><Text style={styles.contentTitle}>What you’ll learn</Text>{(detailReady || reduced) && <Animated.View entering={reduced ? undefined : FadeIn.duration(320).delay(1000)}>{selectedBook.lessons.map(line => <View key={line} style={styles.bulletRow}><View style={styles.blueDot} /><Text style={[styles.paragraph, { flex: 1, marginTop: 0 }]}>{line}</Text></View>)}</Animated.View>}</View>
          <View style={styles.section}><Text style={styles.contentTitle}>Meet the author</Text>{(detailReady || reduced) && <Animated.View entering={reduced ? undefined : FadeIn.duration(320).delay(2400)} style={styles.authorRow}><View style={styles.authorAvatar}>{authorReady && <Animated.View entering={reduced ? undefined : FadeIn.duration(400)} style={StyleSheet.absoluteFill}><Image source={AUTHOR} style={StyleSheet.absoluteFill} contentFit="cover" /></Animated.View>}</View><View style={{ flex: 1 }}><Text style={styles.paragraph}><Text style={{ fontWeight: '700', color: '#1F1F1F' }}>{selectedBook.author}</Text> is a writer and designer who studies attention, routines and the small things that shape a day.</Text><Text style={styles.paragraph}>Her essays on everyday habits are read in classrooms, studios and kitchens, and are known for turning research into steps anyone can try tonight.</Text><Text style={styles.paragraph}>Her work draws on behavioral science and years of designing calm, useful products, but is written in a warm, plain style.</Text><Text style={styles.paragraph}>This original sample is included locally, so your reading stays available offline.</Text>{authorReady && <View style={styles.sourcePill}><View style={styles.sourceDot}><Text style={styles.sourceDotText}>{selectedBook.author.split(' ').map(w => w[0]).join('')}</Text></View><Text style={styles.sourceText}>1 SOURCE</Text></View>}</View></Animated.View>}</View>
        </ScrollView></ProgressiveBlurViewport>
        <View pointerEvents="box-none" style={[styles.appBar, { paddingTop: insets.top + 8 }]}>
          <Pressable accessibilityRole="button" accessibilityLabel="Back to library" onPress={() => { interrupt(); setDetailOperation(null); setReading(false); moveTo('library'); }} style={styles.circleButton}><Ionicons name="chevron-back" size={22} color="#1F1F1F" /></Pressable>
          {statusText ? <View style={styles.statusPill}><Working /><Text style={styles.statusText}>{statusText}</Text></View> : <View />}
          <Pressable accessibilityRole="button" accessibilityLabel={bookmarked ? 'Remove from favorites' : 'Save to favorites'} onPress={toggleBookmark} style={styles.circleButton}><Ionicons name="chatbubble-outline" size={19} color="#1F1F1F" /></Pressable>
        </View>
      </Animated.View>
      </Animated.View>
    </Animated.View>
    <Animated.View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.featureCard, { position: 'absolute', left: originFrame.x, top: originFrame.y, width: originFrame.width, height: originFrame.height, overflow: 'hidden' }, cardBridgeStyle]}><Image source={FEATURES[feature].art} style={styles.featureArt} contentFit="contain" /><Text maxFontSizeMultiplier={1.5} style={[styles.featureTitle, { color: FEATURES[feature].ink }]}>{FEATURES[feature].title}</Text><Text style={styles.featureBody} numberOfLines={3}>{FEATURES[feature].body}</Text></Animated.View>

    <Modal visible={topicsOpen} transparent animationType={reduced ? 'fade' : 'slide'} onRequestClose={closeTopics}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <View style={styles.sheetBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} accessibilityRole="button" accessibilityLabel="Dismiss topic selection" onPress={closeTopics} />
          <View style={[styles.sheet, { height: height - Math.max(insets.top + 3, 40) }]}>
            <ProgressiveBlurViewport style={{ flexShrink: 1, flexGrow: 1 }} tint="light" edgeSize={28} enabled={topicsOpen && !topicInputActive}><ScrollView ref={topicScroll} style={{ flexShrink: 1 }} contentContainerStyle={{ paddingHorizontal: 18, paddingTop: 14, paddingBottom: 24 }} keyboardShouldPersistTaps="handled" keyboardDismissMode={Platform.OS === 'ios' ? 'interactive' : 'on-drag'} showsVerticalScrollIndicator={false}>
              <Text style={styles.sheetTitle} accessibilityRole="header">Select Topics</Text>
              <Text style={styles.sheetCopy}>Choose topics for your book summary, or select none for a random book</Text>
              <View style={styles.topicWrap}>
                {[...topicList, ...selected.filter(item => !topicList.includes(item))].map(topic => <AnimatedPressable key={topic} entering={MORE_TOPICS.includes(topic) && !reduced ? FadeIn.duration(260).delay(MORE_TOPICS.indexOf(topic) * 40) : undefined} layout={reduced ? undefined : LinearTransition.duration(240).easing(OUT)} accessibilityRole="checkbox" accessibilityState={{ checked: selected.includes(topic), disabled: generating }} disabled={generating} onPress={() => { interrupt(); setSelected(value => value.includes(topic) ? value.filter(item => item !== topic) : [...value, topic]); void Haptics.selectionAsync(); }} style={[styles.topic, selected.includes(topic) && styles.topicSelected]}><Text style={[styles.topicText, selected.includes(topic) && { color: '#FFFFFF' }]}>{topic}</Text>{selected.includes(topic) && <Animated.View entering={reduced ? undefined : ZoomIn.springify().damping(14).stiffness(320)}><Ionicons name="checkmark-circle-outline" size={17} color="#FFFFFF" /></Animated.View>}</AnimatedPressable>)}
                <Animated.View layout={reduced ? undefined : LinearTransition.duration(240).easing(OUT)}>{addingTopic ? <TextInput autoFocus accessibilityLabel="Add your own topic" editable={!generating} value={customTopic} onFocus={() => { topicInputFocused.current = true; setTopicInputActive(true); interrupt(); topicScroll.current?.scrollToEnd({ animated: !reduced }); }} onBlur={() => { topicInputFocused.current = false; setTopicInputActive(false); if (!customTopic.trim()) setAddingTopic(false); }} onChangeText={setCustomTopic} placeholder="Your topic" placeholderTextColor="#7DA3E8" style={[styles.addTopic, styles.addTopicInput]} maxLength={32} returnKeyType="done" onSubmitEditing={addTopic} /> : <Pressable accessibilityRole="button" accessibilityLabel="Add your own topic" disabled={generating} onPress={() => { interrupt(); setAddingTopic(true); }} style={styles.addTopic}><Ionicons name="add" size={17} color="#1E6FEF" /><Text style={styles.addTopicText}>Add yours</Text></Pressable>}</Animated.View>
              </View>
            </ScrollView></ProgressiveBlurViewport>
            <View style={styles.topicBand}><Text accessibilityLiveRegion="polite" style={styles.topicCount}>{selected.length === 0 ? 'Random book summary' : `${selected.length} ${selected.length === 1 ? 'topic' : 'topics'} selected`}</Text></View>
            <View style={{ paddingHorizontal: 18, paddingTop: 14, paddingBottom: insets.bottom + 10 }}><Button reduced={reduced} busy={generating} disabled={generating} onPress={generate}>{generating ? 'Generating…' : 'Generate'}</Button></View>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
    <Modal visible={notice !== null} transparent animationType="fade" onRequestClose={() => setNotice(null)}><View style={styles.noticeBackdrop}><View style={[styles.noticeCard, { maxHeight: height - insets.top - 48 }]}><ProgressiveBlurViewport style={{ flexShrink: 1 }} tint="light" edgeSize={28} enabled={notice !== null}><ScrollView><Image source={FEATURES[feature].art} style={{ width: 116, height: 116, alignSelf: 'center' }} contentFit="contain" /><Text style={styles.noticeTitle}>Made for a little curiosity.</Text><Text style={styles.paragraph}>{notice}</Text></ScrollView></ProgressiveBlurViewport><Button reduced={reduced} onPress={() => setNotice(null)}>Got it</Button></View></View></Modal>
  </View>;
}

const SHADOW = '0px 1px 2px rgba(0,0,0,0.04), 0px 10px 28px -8px rgba(0,0,0,0.12)';
const SERIF = Platform.OS === 'ios' ? 'Georgia' : 'serif';
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EFEFEF' },
  sectionTitle: { fontSize: 22, fontWeight: '600', color: '#111111', paddingHorizontal: 19, marginBottom: 14, marginTop: 22, letterSpacing: -0.4 },
  sectionRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingRight: 19, marginTop: 26, marginBottom: 14 },
  chevronCircle: { width: 34, height: 34, borderRadius: 17, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center', boxShadow: '0px 2px 8px rgba(0,0,0,0.08)' },
  featureTrack: { paddingHorizontal: 19, gap: 14, paddingBottom: 14 },
  featureCard: { height: 330, borderRadius: 28, backgroundColor: '#FFFFFF', paddingHorizontal: 18, paddingTop: 20, alignItems: 'center', boxShadow: SHADOW },
  featureArt: { width: 172, height: 172 },
  featureTitle: { fontSize: 20, lineHeight: 23, fontWeight: '600', textAlign: 'center', marginTop: 6, letterSpacing: -0.4 },
  featureBody: { color: '#555555', fontSize: 14, lineHeight: 17, textAlign: 'center', marginTop: 10 },
  invite: { marginHorizontal: 19, paddingVertical: 26, paddingLeft: 22, paddingRight: 14, borderRadius: 26, backgroundColor: '#FFFFFF', flexDirection: 'row', alignItems: 'center', gap: 8, boxShadow: SHADOW },
  inviteTitle: { fontSize: 20, fontWeight: '600', color: '#111111', letterSpacing: -0.4 },
  inviteBody: { fontSize: 13, lineHeight: 16, color: '#3A3A3A', marginTop: 10 },
  avatarCluster: { width: 92, height: 76 },
  avatar: { position: 'absolute', borderRadius: 999, borderWidth: 2, borderColor: '#FFFFFF', backgroundColor: '#E4E4E4' },
  avatarA: { width: 40, height: 40, left: 0, top: 6 }, avatarB: { width: 46, height: 46, left: 34, top: 0 }, avatarC: { width: 44, height: 44, left: 16, top: 32 },
  avatarPlus: { position: 'absolute', left: 50, top: 54, width: 20, height: 20, borderRadius: 10, backgroundColor: '#8A8794', borderWidth: 2, borderColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center' },
  categories: { paddingHorizontal: 19, gap: 8, paddingBottom: 14 },
  category: { minHeight: 36, paddingHorizontal: 12, borderRadius: 18, backgroundColor: '#FAFAFA', justifyContent: 'center', boxShadow: '0px 1px 3px rgba(0,0,0,0.06)' },
  categoryActive: { backgroundColor: '#050505' },
  categoryLabel: { color: '#111111', fontSize: 14, fontWeight: '500' },
  appRows: { paddingHorizontal: 19 },
  appRow: { flexDirection: 'row', alignItems: 'center', gap: 14, minHeight: 79 },
  appIcon: { width: 64, height: 64, borderRadius: 32 },
  appName: { fontSize: 16, fontWeight: '500', color: '#111111' },
  appHandle: { fontSize: 13, color: '#555555', marginTop: 1 },
  appDescription: { fontSize: 14, lineHeight: 17, color: '#555555', marginTop: 3 },
  floatingNav: { position: 'absolute', left: 20, right: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  navPill: { flexDirection: 'row', padding: 4, backgroundColor: '#FFFFFFB8', borderRadius: 30, overflow: 'hidden', borderWidth: 1, borderColor: '#FFFFFFE6', boxShadow: '0px 10px 30px -8px rgba(0,0,0,0.22)' },
  navItem: { minWidth: 92, height: 52, alignItems: 'center', justifyContent: 'center', borderRadius: 26, overflow: 'hidden' },
  navGlow: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 40, borderRadius: 26 },
  navSelected: { backgroundColor: '#FFFFFF', boxShadow: '0px 2px 8px rgba(0,0,0,0.10)' },
  navLabel: { color: '#111111', fontSize: 15, fontWeight: '500' }, navInactive: { color: '#333333', fontSize: 15, fontWeight: '400' },
  plus: { width: 56, height: 56, borderRadius: 28, backgroundColor: '#333333', alignItems: 'center', justifyContent: 'center', boxShadow: '0px 10px 22px -8px rgba(0,0,0,0.5)' },
  appSurface: { ...StyleSheet.absoluteFill, backgroundColor: '#F6F6F6', overflow: 'hidden' },
  loader: { flex: 1 }, loaderTitle: { color: '#111111', fontWeight: '600', letterSpacing: -0.3 }, loaderCopy: { color: '#8A8A8A' },
  appBar: { position: 'absolute', top: 0, left: 0, right: 0, paddingHorizontal: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  circleButton: { width: 46, height: 46, borderRadius: 23, backgroundColor: '#FFFFFF', alignItems: 'center', justifyContent: 'center', boxShadow: '0px 2px 10px rgba(0,0,0,0.08)' },
  roundButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  statusPill: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 10, height: 30 },
  statusText: { fontSize: 15, color: '#1F1F1F', fontWeight: '500' },
  working: { flexDirection: 'row' }, workingDot: { width: 9, height: 9, borderRadius: 5, backgroundColor: '#4A4A4A', marginLeft: -2 },
  headerCard: { position: 'absolute', backgroundColor: '#FFFFFF', overflow: 'hidden', boxShadow: SHADOW },
  headerOrb: { position: 'absolute', width: 60, height: 60 },
  headerContent: { position: 'absolute', top: 0, left: 0, alignItems: 'center', paddingHorizontal: 18, paddingBottom: 8 },
  chipRow: { flexDirection: 'row', gap: 10 },
  chip: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 30, paddingHorizontal: 9, borderRadius: 15, backgroundColor: '#F5F5F5' },
  chipAvatar: { width: 18, height: 18, borderRadius: 9 }, chipText: { fontSize: 13, color: '#1F1F1F', fontWeight: '500' },
  libraryTitle: { fontSize: 20, fontWeight: '600', color: '#111111', marginTop: 18, letterSpacing: -0.3, textAlign: 'center' },
  libraryBody: { fontSize: 13.5, lineHeight: 17, color: '#3A3A3A', textAlign: 'center', marginTop: 8 },
  utilityRow: { flexDirection: 'row', gap: 6, marginTop: 60, alignSelf: 'stretch' },
  grabber: { width: 56, height: 4, borderRadius: 2, backgroundColor: '#E4E4E4', marginTop: 8 },
  filterRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 2, marginBottom: 18 },
  filterLabel: { fontSize: 12, color: '#7A7A7A', letterSpacing: 0.8, fontWeight: '500' },
  allBooks: { flexDirection: 'row', alignItems: 'center', gap: 6, height: 32, paddingHorizontal: 14, borderRadius: 16, backgroundColor: '#FFFFFF', boxShadow: '0px 1px 4px rgba(0,0,0,0.06)' },
  allBooksText: { fontSize: 14, color: '#1F1F1F', fontWeight: '500' },
  bookCard: { flexDirection: 'row', gap: 12, padding: 12, borderRadius: 20, backgroundColor: '#FFFFFF', marginBottom: 12, alignItems: 'flex-start', boxShadow: SHADOW },
  bookCopy: { flex: 1, paddingTop: 4 }, bookName: { fontSize: 16, lineHeight: 20, fontWeight: '500', color: '#111111' }, bookMeta: { fontSize: 13, color: '#555555', marginTop: 3 }, bookNote: { fontSize: 13, lineHeight: 16, color: '#555555', marginTop: 10 },
  cover: { overflow: 'hidden', borderRadius: 4 },
  coverRow: { width: 74, height: 105 },
  coverDetail: { width: 190, height: 268, borderRadius: 8, boxShadow: '0px 14px 26px -10px rgba(0,0,0,0.45)' },
  coverPlaceholder: { width: 74, height: 105, borderRadius: 6, backgroundColor: '#D6D6D6', overflow: 'hidden' }, shimmer: { position: 'absolute', top: 0, bottom: 0, width: 60 },
  coverTopBand: { position: 'absolute', left: 0, right: 0, top: 0, paddingHorizontal: 6, paddingTop: 6, alignItems: 'center' },
  tourAuthor: { fontSize: 6, fontWeight: '800', color: '#F2C94C', letterSpacing: 0.6 }, tourTitle: { fontSize: 8.5, lineHeight: 9.5, fontWeight: '900', color: '#F5E9C8', textAlign: 'center', marginTop: 2, fontFamily: SERIF },
  ritualsFrame: { backgroundColor: '#161616', padding: 3 },
  ritualsPage: { flex: 1, backgroundColor: '#F4EEDF', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 6, paddingHorizontal: 3 },
  ritualsTag: { fontSize: 5, lineHeight: 6.5, fontWeight: '700', color: '#2A2A2A', textAlign: 'center' },
  ritualsTitle: { fontSize: 17, lineHeight: 17, color: '#D18A55', fontFamily: SERIF, textAlign: 'center', letterSpacing: -0.5 },
  ritualsSub: { fontSize: 3.5, color: '#3A3A3A', textAlign: 'center' },
  ritualsAuthor: { fontSize: 7, fontWeight: '700', color: '#1F1F1F', letterSpacing: 0.3 },
  ritualsBadge: { position: 'absolute', right: -6, top: -6, width: 46, height: 46, borderRadius: 23, backgroundColor: '#F3B53F', alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: '#F7D27A' },
  ritualsBadgeText: { fontSize: 6.5, lineHeight: 7.5, fontWeight: '800', color: '#3A2A10', textAlign: 'center' },
  libraryFooter: { position: 'absolute', bottom: 0, left: 0, right: 0, alignItems: 'center' },
  button: { minHeight: 52, maxWidth: '100%', flexShrink: 1, flexGrow: 1, flexDirection: 'row', gap: 8, backgroundColor: '#13775E', borderRadius: 26, paddingHorizontal: 22, alignItems: 'center', justifyContent: 'center', boxShadow: '0px 1px 0px rgba(255,255,255,0.16) inset, 0px 10px 22px -12px rgba(16,90,70,0.9)' },
  buttonDark: { backgroundColor: '#2E2E2E', minHeight: 48, borderRadius: 24, boxShadow: 'none' },
  buttonSecondary: { backgroundColor: '#FFFFFF', borderWidth: StyleSheet.hairlineWidth, borderColor: '#DADADA', boxShadow: '0px 1px 3px rgba(0,0,0,0.05)', minHeight: 48 },
  buttonPill: { flexGrow: 0, width: 252, paddingHorizontal: 18 },
  buttonBusy: { backgroundColor: '#7FBFAE', boxShadow: 'none' },
  buttonLabel: { fontSize: 16, fontWeight: '500', textAlign: 'center' },
  detailSurface: { ...StyleSheet.absoluteFill, backgroundColor: '#F6F6F6' },
  detailCover: { alignItems: 'center', marginTop: 6 },
  detailTitle: { fontSize: 27, lineHeight: 33, color: '#111111', textAlign: 'center', fontFamily: SERIF, marginTop: 22 },
  detailAuthor: { color: '#555555', fontSize: 17, textAlign: 'center', marginTop: 4, fontStyle: 'italic', fontFamily: SERIF },
  detailActions: { gap: 14, marginTop: 22, marginBottom: 30 },
  section: { backgroundColor: '#FFFFFF', borderRadius: 22, padding: 18, minHeight: 96, marginBottom: 14, boxShadow: '0px 1px 3px rgba(0,0,0,0.04)' },
  contentTitle: { fontSize: 17, fontWeight: '600', color: '#111111', marginBottom: 4 },
  paragraph: { fontSize: 15, lineHeight: 22, color: '#2A2A2A', marginTop: 12 },
  bulletRow: { flexDirection: 'row', gap: 12, marginTop: 12 }, blueDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: '#1E6FEF', marginTop: 8 },
  authorRow: { flexDirection: 'row', gap: 14, alignItems: 'flex-start' },
  authorAvatar: { width: 60, height: 60, borderRadius: 30, backgroundColor: '#D4D4D4', overflow: 'hidden', marginTop: 12 },
  sourcePill: { flexDirection: 'row', alignItems: 'center', gap: 8, alignSelf: 'flex-start', height: 34, paddingHorizontal: 8, paddingRight: 14, borderRadius: 17, borderWidth: 1, borderColor: '#E2E2E2', marginTop: 14 },
  sourceDot: { width: 20, height: 20, borderRadius: 10, backgroundColor: '#111111', alignItems: 'center', justifyContent: 'center' }, sourceDotText: { color: '#FFFFFF', fontSize: 8, fontWeight: '700' },
  sourceText: { fontSize: 12, color: '#6A6A6A', letterSpacing: 0.8, fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace' },
  sheetBackdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0,0,0,0.22)' },
  sheet: { backgroundColor: '#F6F6F6', borderTopLeftRadius: 30, borderTopRightRadius: 30, overflow: 'hidden' },
  sheetTitle: { fontSize: 20, fontWeight: '600', color: '#111111', letterSpacing: -0.3 },
  sheetCopy: { fontSize: 13, lineHeight: 17, color: '#555555', marginTop: 6, marginBottom: 30 },
  topicWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, rowGap: 10 },
  topic: { height: 40, paddingHorizontal: 15, borderRadius: 20, backgroundColor: '#F2F2F4', flexDirection: 'row', alignItems: 'center', gap: 6, boxShadow: '0px 2px 6px rgba(0,0,0,0.08), 0px 0px 0px 1px rgba(255,255,255,0.9) inset' },
  topicSelected: { backgroundColor: '#1E6FEF', boxShadow: '0px 4px 12px -4px rgba(30,111,239,0.7)' },
  topicText: { fontSize: 15, color: '#1F1F1F', fontWeight: '500' },
  addTopic: { height: 40, paddingHorizontal: 15, borderRadius: 20, borderWidth: 1.5, borderColor: '#1E6FEF', borderStyle: 'dashed', flexDirection: 'row', alignItems: 'center', gap: 6 },
  addTopicInput: { minWidth: 140, fontSize: 15, color: '#1E6FEF' },
  addTopicText: { fontSize: 15, color: '#1E6FEF', fontWeight: '500' },
  topicBand: { backgroundColor: '#EFEDF5', paddingVertical: 14, alignItems: 'center' },
  topicCount: { fontSize: 13, color: '#6A6A6A' },
  noticeBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.3)', justifyContent: 'center', padding: 24 },
  noticeCard: { backgroundColor: '#FFFFFF', borderRadius: 28, padding: 22, gap: 16 },
  noticeTitle: { fontSize: 20, fontWeight: '600', color: '#111111', textAlign: 'center', marginTop: 8 },
});
