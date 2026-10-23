import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  ReduceMotion,
  type SharedValue,
  cancelAnimation,
  interpolateColor,
  useAnimatedKeyboard,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { FACE_SIZE, Mood } from './Mood';
import { RatingStar, STARS_WIDTH, STAR_GAP, STAR_SIZE } from './RatingStar';
import { Wave } from './Wave';

type Props = {
  /** Runs the whole dialog by itself, in a loop, and leaves the page as it was. */
  autoPlay?: boolean;
  title?: string;
  subtitle?: string;
  commentLabel?: string;
  placeholder?: string;
  cancelLabel?: string;
  submitLabel?: string;
  /** What the autoPlay run types in the box. */
  sampleComment?: string;
  /** Called with the stars (1 to 5) and the comment when Submit is pressed. */
  onSubmit?: (rating: number, comment: string) => void;
  onCancel?: () => void;
};

// The timeline of one loop, in seconds: the dialog pops in, the stars appear, a finger runs along
// them, the comment is typed, Submit is pressed and the dialog leaves in a burst of hearts.
export const LOOP = 10;
const OPEN_AT = 0.8;
const RATE_AT = [2.4, 2.9, 3.4, 3.9, 4.4];
const FOCUS_AT = 4.8;
const TYPE_AT = 4.9;
const TYPE_EVERY = 0.08;
const PRESS_AT = 6.2;
const SUBMIT_AT = 6.3;
// After a touch the loop waits this long, then starts again from the page.
const USER_HOLD = 3;

const OPEN_SPRING = { duration: 450, dampingRatio: 0.75, reduceMotion: ReduceMotion.Never };
const CLOSE_MS = 350;
const FADE_MS = 200;
const CONTENT_AFTER = 700;
const CONTENT_MS = 700;
const FACE_MS = 150;
const MOOD_MS = 250;
const SQUASH_MS = 70;
const BURST_MS = 800;
const SUBMIT_COLOR_MS = 250;
const SHRINK = 0.9;
const POP_FROM = 0.85;
const POP_RISE = 24;
const SCRIM_OPACITY = 0.6;

// The face for each number of stars (0 means none yet): the grin is the face at rest.
const FACE_OF = [3, 0, 1, 2, 3, 4];
// The header's field and the wave over it, for none yet and for one to five stars.
const FIELD = ['#f7d774', '#f29c9c', '#f5be8c', '#f7d774', '#c9e49a', '#9ae3b5'];
const WAVE = ['#fbe59a', '#f7b9b9', '#f8d3ae', '#fbe59a', '#dcf0b8', '#bdf0cf'];

const DIALOG_WIDTH = 336;
const DIALOG_HEIGHT = 490;
const DIALOG_RADIUS = 32;
const PADDING = 20;
const BOX_HEIGHT = 84;
const BUTTON_HEIGHT = 48;
const SCRIM = '#3c4350';
const BLUE = '#4aa3df';
const SUBMIT_ON = '#2f7de1';
const SUBMIT_OFF = '#8a8a8a';
const INK = '#111318';
const MUTE = '#6e7380';
const MAX_COMMENT = 140;

// Six hearts leave the emoji: sideways reach, rise and size.
const HEARTS = [
  { dx: -64, rise: 96, size: 16 },
  { dx: -38, rise: 130, size: 20 },
  { dx: -12, rise: 104, size: 14 },
  { dx: 14, rise: 138, size: 18 },
  { dx: 40, rise: 100, size: 22 },
  { dx: 66, rise: 120, size: 15 },
];

const stage = (value: number, start: number, length: number) => {
  'worklet';
  return Math.min(1, Math.max(0, (value - start) / length));
};

const backOut = (p: number) => {
  'worklet';
  return 1 + 2.70158 * (p - 1) ** 3 + 1.70158 * (p - 1) ** 2;
};

/**
 * A rating dialog over a page: an emoji that changes with the stars (a frown to heart eyes) under a
 * wave that goes from red to green, a comment box and Submit. Stars light up under a finger run along
 * them. Submit sends the dialog away in a burst of hearts, and the "Rate the app" button on the page
 * brings it back.
 */
export function EmojiRatingPopup({
  autoPlay = false,
  title = 'How would you rate the overall user experience of our app?',
  subtitle = 'Do you find the app easy to use?',
  commentLabel = 'Can you tell us more?',
  placeholder = "We'd love to hear more!",
  cancelLabel = 'Cancel',
  submitLabel = 'Submit',
  sampleComment = 'Great app!',
  onSubmit,
  onCancel,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const keyboard = useAnimatedKeyboard();
  const [stars, setStars] = useState(0);
  const [comment, setComment] = useState('');
  const [focused, setFocused] = useState(false);
  const [typing, setTyping] = useState(false);
  const [pressed, setPressed] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  // Changes on every touch, which restarts the autoPlay loop behind a pause.
  const [touches, setTouches] = useState(0);

  const dialogWidth = Math.min(DIALOG_WIDTH, width - 48);
  const top = Math.max(insets.top + 12, (height - DIALOG_HEIGHT) / 2);
  const rating = useSharedValue(0);
  const face = useSharedValue(FACE_OF[0]);
  const mood = useSharedValue(0);
  const squash = useSharedValue(1);
  const bounce = useSharedValue(1);
  // 0 is the page alone, 1 the dialog in place; the spring may pass 1.
  const open = useSharedValue(0);
  const out = useSharedValue(0);
  const content = useSharedValue(0);
  const burst = useSharedValue(0);
  const active = useSharedValue(0);
  const ready = useSharedValue(0);

  const canSubmit = stars > 0 && comment.trim().length > 0;

  useEffect(() => {
    ready.set(withTiming(canSubmit ? 1 : 0, { duration: SUBMIT_COLOR_MS, reduceMotion: ReduceMotion.Never }));
  }, [canSubmit, ready]);

  useEffect(() => {
    active.set(withTiming(focused || typing ? 1 : 0, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
  }, [focused, typing, active]);

  const setRating = (value: number, byHand: boolean) => {
    if (value === rating.get()) return;
    if (byHand) Haptics.selectionAsync().catch(() => {});
    rating.set(value);
    setStars(value);
    face.set(withTiming(FACE_OF[value], { duration: FACE_MS, reduceMotion: ReduceMotion.Never }));
    mood.set(withTiming(value, { duration: MOOD_MS, easing: Easing.out(Easing.quad), reduceMotion: ReduceMotion.Never }));
    if (!reducedMotion) {
      squash.set(
        withSequence(
          withTiming(0.88, { duration: SQUASH_MS, reduceMotion: ReduceMotion.Never }),
          withSpring(1, { duration: 220, dampingRatio: 0.5, reduceMotion: ReduceMotion.Never }),
        ),
      );
    }
  };

  const openDialog = () => {
    setIsOpen(true);
    burst.set(0);
    content.set(0);
    out.set(0);
    if (reducedMotion) {
      open.set(withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
      content.set(withTiming(1, { duration: FADE_MS, reduceMotion: ReduceMotion.Never }));
      return;
    }
    open.set(withSpring(1, OPEN_SPRING));
    content.set(withDelay(CONTENT_AFTER, withTiming(1, { duration: CONTENT_MS, easing: Easing.linear, reduceMotion: ReduceMotion.Never })));
    bounce.set(
      withDelay(
        100,
        withSequence(
          withTiming(1.2, { duration: 160, reduceMotion: ReduceMotion.Never }),
          withSpring(1, { duration: 300, dampingRatio: 0.5, reduceMotion: ReduceMotion.Never }),
        ),
      ),
    );
  };

  /** The page is back: the next opening starts from a blank dialog. */
  const reset = () => {
    open.set(0);
    out.set(0);
    content.set(0);
    rating.set(0);
    face.set(FACE_OF[0]);
    mood.set(0);
    setStars(0);
    setComment('');
    setFocused(false);
    setTyping(false);
    setIsOpen(false);
  };

  const dismiss = () => {
    out.set(
      withTiming(1, { duration: reducedMotion ? FADE_MS : CLOSE_MS, easing: Easing.in(Easing.cubic), reduceMotion: ReduceMotion.Never }, (finished) => {
        if (finished) scheduleOnRN(reset);
      }),
    );
  };

  const submit = () => {
    if (!canSubmit) return;
    onSubmit?.(stars, comment);
    if (!reducedMotion) burst.set(withTiming(1, { duration: BURST_MS, easing: Easing.out(Easing.quad), reduceMotion: ReduceMotion.Never }));
    dismiss();
  };

  const cancel = () => {
    onCancel?.();
    dismiss();
  };

  const rest = () => {
    if (isOpen) dismiss();
  };

  useEffect(
    () => () => {
      for (const value of [open, out, content, burst, face, mood, squash, bounce, active, ready]) cancelAnimation(value);
    },
    [open, out, content, burst, face, mood, squash, bounce, active, ready],
  );

  // The loop reads the latest state when its timers fire.
  const latest = useRef({ openDialog, setRating, submit, rest });
  useEffect(() => {
    latest.current = { openDialog, setRating, submit, rest };
  });

  useEffect(() => {
    if (!autoPlay) return;
    const timers: ReturnType<typeof setTimeout>[] = [];
    const at = (origin: number, seconds: number, run: () => void) =>
      timers.push(setTimeout(run, origin + seconds * 1000 - Date.now()));
    const cycle = (origin: number) => {
      at(origin, OPEN_AT, () => latest.current.openDialog());
      RATE_AT.forEach((seconds, i) => at(origin, seconds, () => latest.current.setRating(i + 1, false)));
      at(origin, FOCUS_AT, () => setTyping(true));
      for (let i = 0; i < sampleComment.length; i++) {
        at(origin, TYPE_AT + i * TYPE_EVERY, () => setComment(sampleComment.slice(0, i + 1)));
      }
      at(origin, PRESS_AT, () => setPressed(true));
      at(origin, SUBMIT_AT, () => {
        setPressed(false);
        latest.current.submit();
      });
      at(origin, LOOP, () => cycle(origin + LOOP * 1000));
    };
    if (touches === 0) {
      cycle(Date.now());
    } else {
      timers.push(
        setTimeout(() => {
          setPressed(false);
          setTyping(false);
          latest.current.rest();
          cycle(Date.now() + 700);
        }, USER_HOLD * 1000),
      );
    }
    return () => timers.forEach(clearTimeout);
  }, [autoPlay, touches, sampleComment]);

  const pick = (value: number) => setRating(value, true);
  const run = Gesture.Pan()
    .minDistance(0)
    .onBegin((event) => {
      const place = Math.floor(event.x / (STAR_SIZE + STAR_GAP)) + 1;
      scheduleOnRN(pick, Math.min(5, Math.max(1, place)));
    })
    .onUpdate((event) => {
      const place = Math.floor(event.x / (STAR_SIZE + STAR_GAP)) + 1;
      scheduleOnRN(pick, Math.min(5, Math.max(1, place)));
    });

  const scrimStyle = useAnimatedStyle(() => ({
    opacity: SCRIM_OPACITY * Math.min(1, Math.max(0, open.get())) * (1 - out.get()),
  }));
  const dialogStyle = useAnimatedStyle(() => {
    const o = open.get();
    const lift = keyboard.height.get();
    const room = Math.max(0, top + DIALOG_HEIGHT - (height - lift) + 16);
    if (reducedMotion) return { opacity: Math.min(1, Math.max(0, o)) * (1 - out.get()), transform: [{ translateY: -room }] };
    return {
      opacity: Math.min(1, o * 3) * (1 - out.get()),
      transform: [
        { translateY: (1 - o) * POP_RISE - room },
        { scale: (POP_FROM + (1 - POP_FROM) * o) * (1 - (1 - SHRINK) * out.get()) },
      ],
    };
  });
  const introStyle = useAnimatedStyle(() => {
    const p = stage(content.get(), 0, 0.5);
    return { opacity: p, transform: [{ translateY: reducedMotion ? 0 : (1 - p) * 8 }] };
  });
  const faceStyle = useAnimatedStyle(() => ({
    transform: [{ scale: bounce.get() }, { scaleY: squash.get() }, { scaleX: 2 - squash.get() }],
  }));
  const boxStyle = useAnimatedStyle(() => ({
    borderColor: interpolateColor(active.get(), [0, 1], ['#c9d6e4', BLUE], 'RGB', { gamma: 1 }),
  }));
  const submitStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(ready.get(), [0, 1], [SUBMIT_OFF, SUBMIT_ON], 'RGB', { gamma: 1 }),
    transform: [{ scale: pressed ? 0.96 : 1 }],
  }));

  return (
    <GestureHandlerRootView style={styles.screen} onTouchStart={() => autoPlay && setTouches((n) => n + 1)}>
      <StatusBar style="dark" />
      <View style={[styles.page, { paddingTop: insets.top + 4 }]} accessibilityElementsHidden={isOpen}>
        <View style={styles.pageHeader}>
          <Text style={styles.pageTitle}>Discover</Text>
          <Pressable style={styles.rate} onPress={openDialog} accessibilityRole="button">
            <Ionicons name="star" size={15} color="#ffbe2e" />
            <Text style={styles.rateText}>Rate the app</Text>
          </Pressable>
        </View>
        <View style={styles.stories}>
          {STORIES.map((story, i) => (
            <View key={story.name} style={styles.story}>
              <View style={[styles.storyRing, i === 0 && styles.storyRingSeen]}>
                <Image source={photo(story.photo, 120)} style={styles.storyPhoto} cachePolicy="memory-disk" transition={0} />
              </View>
              <Text style={styles.storyName} numberOfLines={1}>{story.name}</Text>
            </View>
          ))}
        </View>
        {POSTS.map((post) => (
          <View key={post.name} style={styles.post}>
            <View style={styles.postHead}>
              <Image source={photo(post.avatar, 96)} style={styles.avatar} cachePolicy="memory-disk" transition={0} />
              <View style={styles.postWho}>
                <Text style={styles.postName}>{post.name}</Text>
                <Text style={styles.postMeta}>{post.meta}</Text>
              </View>
              <Ionicons name="ellipsis-horizontal" size={20} color={MUTE} />
            </View>
            <Text style={styles.postText}>{post.text}</Text>
            <Image source={photo(post.photo, 720, 440)} style={styles.postPhoto} contentFit="cover" cachePolicy="memory-disk" transition={0} />
            <View style={styles.actions}>
              <Ionicons name="heart" size={20} color="#ff4d6a" />
              <Text style={styles.actionText}>{post.likes}</Text>
              <Ionicons name="chatbubble-outline" size={19} color={INK} style={styles.actionGap} />
              <Text style={styles.actionText}>{post.comments}</Text>
              <View style={styles.flex} />
              <Ionicons name="bookmark-outline" size={19} color={INK} />
            </View>
          </View>
        ))}
      </View>

      <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: SCRIM }, scrimStyle]} pointerEvents="none" />

      {isOpen && (
        <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
          <Animated.View style={[styles.dialogShadow, { top, width: dialogWidth, left: (width - dialogWidth) / 2 }, dialogStyle]}>
            <View style={styles.dialog}>
              <Wave width={dialogWidth} mood={mood} field={FIELD} wave={WAVE} />
              <Animated.View style={[styles.face, { left: dialogWidth / 2 - FACE_SIZE / 2 }, faceStyle]}>
                <Mood face={face} />
              </Animated.View>

              <Animated.View style={[styles.copy, introStyle]}>
                <Text style={styles.title}>{title}</Text>
                <Text style={styles.subtitle}>{subtitle}</Text>
              </Animated.View>

              <GestureDetector gesture={run}>
                <View style={[styles.stars, { left: (dialogWidth - STARS_WIDTH(5)) / 2 }]}>
                  {[0, 1, 2, 3, 4].map((index) => (
                    <StarSlot key={index} index={index} rating={rating} content={content} reduced={reducedMotion} />
                  ))}
                </View>
              </GestureDetector>

              <Animated.View style={[styles.lower, { width: dialogWidth - PADDING * 2 }, introStyle]}>
                <Text style={styles.commentLabel}>{commentLabel}</Text>
                <Animated.View style={[styles.box, boxStyle]}>
                  <TextInput
                    style={styles.input}
                    value={comment}
                    onChangeText={(text) => setComment(text.slice(0, MAX_COMMENT))}
                    onFocus={() => setFocused(true)}
                    onBlur={() => setFocused(false)}
                    placeholder={placeholder}
                    placeholderTextColor="#999999"
                    multiline
                    accessibilityLabel={commentLabel}
                  />
                </Animated.View>
                <View style={styles.buttons}>
                  <Pressable style={[styles.button, styles.cancel]} onPress={cancel} accessibilityRole="button">
                    <Text style={styles.cancelText}>{cancelLabel}</Text>
                  </Pressable>
                  <Animated.View style={[styles.button, submitStyle]}>
                    <Pressable
                      style={styles.submitHit}
                      onPress={submit}
                      disabled={!canSubmit}
                      accessibilityRole="button"
                      accessibilityState={{ disabled: !canSubmit }}
                    >
                      <Text style={styles.submitText}>{submitLabel}</Text>
                    </Pressable>
                  </Animated.View>
                </View>
              </Animated.View>
            </View>
          </Animated.View>
        </View>
      )}

      {/* Outside the dialog, so the hearts outlive it: the burst is longer than the dialog's exit. */}
      <HeartBurst progress={burst} x={width / 2} y={top + 52 + FACE_SIZE / 2} />
    </GestureHandlerRootView>
  );
}

function StarSlot({
  index,
  rating,
  content,
  reduced,
}: {
  index: number;
  rating: SharedValue<number>;
  content: SharedValue<number>;
  reduced: boolean;
}) {
  // Stars pop in one after another, 0.06 s apart, while the title fades up.
  const appear = useDerivedValue(() => {
    if (reduced) return content.get() > 0 ? 1 : 0;
    return backOut(stage(content.get() * (CONTENT_MS / 1000), 0.1 + index * 0.06, 0.35));
  });
  return (
    <View style={{ marginLeft: index === 0 ? 0 : STAR_GAP }}>
      <RatingStar index={index} rating={rating} appear={appear} reduced={reduced} />
    </View>
  );
}

function HeartBurst({ progress, x, y }: { progress: SharedValue<number>; x: number; y: number }) {
  return (
    <>
      {HEARTS.map((heart, i) => (
        <Heart key={i} heart={heart} progress={progress} x={x} y={y} />
      ))}
    </>
  );
}

function Heart({
  heart,
  progress,
  x,
  y,
}: {
  heart: (typeof HEARTS)[number];
  progress: SharedValue<number>;
  x: number;
  y: number;
}) {
  const style = useAnimatedStyle(() => {
    const p = progress.get();
    return {
      opacity: p <= 0 ? 0 : 1 - stage(p, 0.55, 0.45),
      transform: [
        { translateX: heart.dx * p },
        { translateY: -heart.rise * p },
        { scale: 0.5 + 0.5 * stage(p, 0, 0.3) },
      ],
    };
  });
  return (
    <Animated.View pointerEvents="none" style={[styles.heart, { left: x - heart.size / 2, top: y - heart.size / 2 }, style]}>
      <Ionicons name="heart" size={heart.size} color="#ff4d6a" />
    </Animated.View>
  );
}

const photo = (id: string, w: number, h = w) => `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

// The page behind the dialog: a row of stories and a feed of travel posts.
const STORIES = [
  { name: 'You', photo: 'photo-1544005313-94ddf0286df2' },
  { name: 'Lena', photo: 'photo-1517841905240-472988babdf9' },
  { name: 'Marco', photo: 'photo-1531891437562-4301cf35b7e4' },
  { name: 'Aiko', photo: 'photo-1487412720507-e7ab37603c6f' },
  { name: 'Sam', photo: 'photo-1535713875002-d1d0cf377fde' },
];
const POSTS = [
  {
    name: 'Lena Fischer',
    meta: 'Oia, Santorini · 2h',
    text: 'Golden hour from the cliffs. Worth every one of those steps.',
    avatar: 'photo-1517841905240-472988babdf9',
    photo: 'photo-1570077188670-e3a8d69ac5ff',
    likes: '1.2k',
    comments: '84',
  },
  {
    name: 'Marco Bellini',
    meta: 'Manarola, Italy · 5h',
    text: 'Five villages, one train pass and far too much focaccia.',
    avatar: 'photo-1531891437562-4301cf35b7e4',
    photo: 'photo-1516483638261-f4dbaf036963',
    likes: '962',
    comments: '41',
  },
];

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f3f4f7' },
  page: { flex: 1, paddingHorizontal: 20, backgroundColor: '#f3f4f7' },
  pageHeader: { height: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  pageTitle: { color: INK, fontSize: 34, lineHeight: 40, fontWeight: '800', letterSpacing: -1 },
  rate: {
    height: 36,
    paddingHorizontal: 14,
    borderRadius: 18,
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    boxShadow: [{ offsetX: 0, offsetY: 2, blurRadius: 8, color: 'rgba(17,19,24,0.08)' }],
  },
  rateText: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '600', letterSpacing: -0.2 },
  stories: { flexDirection: 'row', justifyContent: 'space-between', marginTop: 16, marginBottom: 16 },
  story: { width: 64, alignItems: 'center' },
  storyRing: { width: 64, height: 64, borderRadius: 32, borderWidth: 2.5, borderColor: '#ff7a59', padding: 3 },
  storyRingSeen: { borderColor: '#d5d8de' },
  storyPhoto: { flex: 1, borderRadius: 26 },
  storyName: { color: INK, fontSize: 12, lineHeight: 16, fontWeight: '500', marginTop: 6 },
  post: {
    borderRadius: 26,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    padding: 16,
    marginBottom: 12,
    boxShadow: [
      { offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(17,19,24,0.04)' },
      { offsetX: 0, offsetY: 8, blurRadius: 24, color: 'rgba(17,19,24,0.06)' },
    ],
  },
  postHead: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: { width: 40, height: 40, borderRadius: 20 },
  postWho: { flex: 1 },
  postName: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '700', letterSpacing: -0.2 },
  postMeta: { color: MUTE, fontSize: 13, lineHeight: 17 },
  postText: { color: INK, fontSize: 15, lineHeight: 21, marginTop: 12 },
  postPhoto: { height: 188, borderRadius: 18, marginTop: 12 },
  actions: { flexDirection: 'row', alignItems: 'center', marginTop: 12, gap: 6 },
  actionText: { color: INK, fontSize: 14, lineHeight: 18, fontWeight: '600' },
  actionGap: { marginLeft: 12 },
  flex: { flex: 1 },

  dialogShadow: {
    position: 'absolute',
    height: DIALOG_HEIGHT,
    borderRadius: DIALOG_RADIUS,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    boxShadow: [
      { offsetX: 0, offsetY: 4, blurRadius: 10, color: 'rgba(14,18,28,0.12)' },
      { offsetX: 0, offsetY: 24, blurRadius: 56, color: 'rgba(14,18,28,0.32)' },
    ],
  },
  dialog: { flex: 1, borderRadius: DIALOG_RADIUS, borderCurve: 'continuous', overflow: 'hidden' },
  face: { position: 'absolute', top: 52, width: FACE_SIZE, height: FACE_SIZE },
  copy: { position: 'absolute', top: 146, left: 24, right: 24, alignItems: 'center' },
  title: { color: INK, fontSize: 19, lineHeight: 25, fontWeight: '700', letterSpacing: -0.4, textAlign: 'center' },
  subtitle: { color: MUTE, fontSize: 15, lineHeight: 20, marginTop: 6, textAlign: 'center' },
  stars: { position: 'absolute', top: 228, height: 48, flexDirection: 'row', alignItems: 'center' },
  lower: { position: 'absolute', top: 290, left: PADDING },
  commentLabel: { color: INK, fontSize: 15, lineHeight: 20, fontWeight: '600', letterSpacing: -0.2, marginBottom: 8 },
  box: {
    height: BOX_HEIGHT,
    borderRadius: 14,
    borderCurve: 'continuous',
    borderWidth: 1.5,
    backgroundColor: '#f7f9fc',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  input: { flex: 1, padding: 0, color: INK, fontSize: 15, lineHeight: 20, textAlignVertical: 'top' },
  buttons: { flexDirection: 'row', gap: 12, marginTop: 16 },
  button: { flex: 1, height: BUTTON_HEIGHT, borderRadius: 14, borderCurve: 'continuous', overflow: 'hidden' },
  cancel: { borderWidth: 1.5, borderColor: BLUE, alignItems: 'center', justifyContent: 'center' },
  cancelText: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  submitHit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  submitText: { color: '#ffffff', fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  heart: { position: 'absolute' },
});
