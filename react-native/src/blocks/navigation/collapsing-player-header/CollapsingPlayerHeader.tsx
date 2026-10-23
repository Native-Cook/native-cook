import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useEffect, useState } from 'react';
import { type LayoutChangeEvent, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  ReduceMotion,
  useAnimatedProps,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

/** A run of the notes' text; a link is drawn in white. */
export type NoteSpan = { text: string; link?: boolean };

type Props = {
  date?: string;
  show?: string;
  /** The episode's title; a line break is kept. */
  title?: string;
  /** The episode's length and where playback starts, in seconds. */
  duration?: number;
  position?: number;
  notesTitle?: string;
  /** The two tabs over the notes: the first shows them, the second only their links. */
  tabs?: [string, string];
  /** The notes, a list of paragraphs. */
  notes?: NoteSpan[][];
  /** The colour of the cover and of the mark beside the notes' heading. */
  accent?: string;
  /** Scrolls the notes by itself every LOOP seconds: the player folds, the notes run on, and it opens again. */
  autoPlay?: boolean;
  onPlayingChange?: (playing: boolean) => void;
  /** Called with the new position, in seconds, when a skip button is pressed. */
  onSeek?: (position: number) => void;
  onCollapsedChange?: (collapsed: boolean) => void;
  onBookmark?: () => void;
  onCast?: () => void;
  onMore?: () => void;
};

const NOTES: NoteSpan[][] = [
  [
    {
      text: 'Every winter the longest ceremony of the season runs past midnight, and every winter somebody promises to fix it. This year the hosts, the caterers and one very tired stage manager let us follow them from the first rehearsal to the last trophy of the night.',
    },
  ],
  [
    {
      text: 'We count the speeches, time the standing ovations and ask why the technical awards always land after eleven. The answer has less to do with television than with a seating chart nobody has dared to redraw in forty years.',
    },
  ],
  [
    {
      text: 'In this episode: the rehearsal nobody attends, why the envelope is still made of paper, the orchestra’s polite way of ending a speech, and what happens to four thousand flowers at two in the morning.',
    },
  ],
  [{ text: 'Chapters: 00:00 The rehearsal. 06:10 Paper envelopes. 13:45 The seating chart. 21:30 The play-off music. 24:05 Four thousand flowers.' }],
  [
    {
      text: 'This episode was produced by Mara Lindqvist, edited by Tomas Oyelaran, fact-checked by Ines Carvalho, engineered by Dev Raman and hosted by Noor Halvorsen.',
    },
  ],
  [{ text: 'Transcript at ' }, { text: 'fieldnotes.example/longest-night', link: true }],
  [
    { text: 'Support Field Notes Daily by becoming a member today: ' },
    { text: 'fieldnotes.example/members', link: true },
  ],
  [
    {
      text: 'Photo: an empty ballroom on the morning after, with one silver balloon still resting against the ceiling. Photo by Jun Aoki for Field Notes.',
    },
  ],
  [
    {
      text: 'Correction: an earlier version of these notes gave the running time as four hours and twelve minutes. It was four hours and twenty-one, not counting the encore.',
    },
  ],
  [
    {
      text: 'Music in this episode by the Halvard Quartet. Additional reporting by Petra Voss and Sami Okafor. Special thanks to everyone who stayed until the lights came up.',
    },
  ],
  [{ text: 'Field Notes Daily is made in a small studio above a bakery. If an episode smells faintly of cinnamon, that is why. New episodes arrive every weekday morning.' }],
  [{ text: 'Learn more about your ad choices. Visit ' }, { text: 'fieldnotes.example/adchoices', link: true }],
];

const PAGE = '#110E11';
const PAGE_CLEAR = 'rgba(17,14,17,0)';
const INK = '#FFFFFF';
const MUTE = '#8C8A8F';
const BODY = '#B4B2B7';
const RULE = '#3A383C';
const FILL = '#C9C7CC';
const BUTTON = '#2A282C';
const GLYPH_DIM = '#5C5A5F';
const TAB_OFF = '#6C6A6F';
const COVER_INK = '#110E11';

const MARGIN = 24;
// The open player's title block, from the safe area's top.
const DATE_TOP = 44;
const TITLE_SIZE = 40;
const TITLE_LINE = 52;
// The open player's lower half hangs from the notes' heading, which sits this far above the
// screen's bottom: the track and the transport row are this far above the heading's centre.
const HEADING_UP = 100;
const TRACK_ABOVE = 202;
const ROW_ABOVE = 95;
// The folded player, from the safe area's top: the mini row, the heading and the tabs under it.
const MINI_CENTER = 60;
const HEADING_CENTER = 135;
const TABS_BELOW = 31;
const HEADING_LINE = 20;
// From the heading's centre to the first line of the notes, open and folded (the tabs come between).
const NOTES_GAP = 16;
const NOTES_GAP_MINI = 57;
const NOTES_END = 24;
const PARAGRAPH_GAP = 10;
const TRACK = 4;
// In the mini row the track sits a little above the centre, with the times under it.
const TRACK_RISE = 7;
const LABEL_LINE = 16;
const LABEL_DROP = 20;
const LABEL_DROP_MINI = 16;
const LABEL_SHRINK = 11 / 12;
const COVER = 48;
const BIG = 76;
const SMALL = 40;
const SKIP = 30;
const SKIP_GAP = 85;
const HIT = 44;
const MARK_SPACE = 18;
const TAB_HEIGHT = 22;
// Where the track ends in the mini row: after the cover, before the button.
const MINI_LEFT = MARGIN + COVER + 18;
const MINI_RIGHT = MARGIN + SMALL + 24;
// The notes pass under the mini row: page colour down to here, then a fade.
const HEADER = MINI_CENTER + 34;
const HEADER_FADE = 24;

// The fold, as fractions of its travel. The title block leaves 1.2 times as fast as the notes rise.
const TITLE_SPEED = 1.2;
const TITLE_GONE = 0.35;
const SIDES_GONE = 0.15;
const SKIPS_GONE = 0.5;
const SQUEEZE_FROM = 0.3;
const SQUEEZE_TO = 0.8;
const COVER_FROM = 0.4;
// The mark beside the heading goes, then the heading slides into its place.
const MARK_FROM = 0.5;
const MARK_GONE = 0.7;
const SLIDE_FROM = 0.6;
const SLIDE_TO = 0.9;
// The track keeps its distance above the transport row until here, then comes down into it.
const DROP_FROM = 0.6;
// The notes make room under their heading, and the tabs fade into it.
const GAP_FROM = 0.6;
const GAP_TO = 0.9;
const TABS_FROM = 0.85;
// Let go past this much of the travel and the player folds; before it, it opens again.
const SNAP_AT = 0.4;
// Seconds of the release speed that count towards that choice.
const PROJECT = 0.15;

// The snap: a spring with a response of 0.35 s that passes its target by about 4 points.
const OMEGA = (2 * Math.PI) / 0.35;
const ZETA = 0.82;
const SETTLE = 0.7;
// The free scroll after a flick loses speed at this rate a second and stops below REST_SPEED.
const DECAY_RATE = 2;
const REST_SPEED = 4;
const RUBBER = 0.55;
const MORPH = 200;
const REDUCED_FADE = 250;

/** autoPlay: one fold and one return every LOOP seconds. */
export const LOOP = 9;
// A pull that speeds up, then the snap takes over, as when a finger flicks and lets go.
const FOLD_AT = 1;
const FOLD_PULL = 0.3;
const FOLD_TO = 0.42;
// The notes run on by this much, slowly.
const ON_AT = 2;
const ON_FOR = 1.2;
const ON_BY = 120;
// The way back: a pull down through the notes, let go halfway through the fold.
const BACK_AT = 5;
const BACK_PULL = 0.55;
const BACK_TO = 0.5;
/** After a touch autoPlay waits this long before it returns to the open player and starts over. */
const USER_HOLD = 3;

const IDLE = 0;
const AUTO = 1;
const SPRING = 2;
const DECAY = 3;

// The pause bars and the play triangle of the big button, each as two four-cornered shapes
// (x, y pairs: top left, top right, bottom right, bottom left) so one can turn into the other.
const PAUSE = [
  [26.5, 25, 33.5, 25, 33.5, 51, 26.5, 51],
  [42.5, 25, 49.5, 25, 49.5, 51, 42.5, 51],
];
const PLAY = [
  [29, 24, 41, 31, 41, 45, 29, 52],
  [41, 31, 53, 38, 53, 38, 41, 45],
];

// Glyphs in a 24-point box.
const BOOKMARK = 'M7 4.5H17V20L12 16L7 20Z';
// Three hundred degrees of a circle from the top, clockwise; the arrowhead sits at its start.
const SKIP_ARC = 'M12 4.5A8.5 8.5 0 1 1 4.64 8.75';
const SKIP_HEAD = 'M8.2 4.5L12.4 1.6V7.4Z';
const CAST_OUTER = 'M5.07 15.5A8 8 0 1 1 18.93 15.5';
const CAST_INNER = 'M8.1 13.75A4.5 4.5 0 1 1 15.9 13.75';
const CAST_BASE = 'M12 14.5L16.5 20.5H7.5Z';
// The cover's three lines of "text": left edge, top, width.
const COVER_LINES = [
  [7, 10, 22],
  [7, 17, 28],
  [7, 24, 16],
];

function clamp01(t: number) {
  'worklet';
  return Math.min(1, Math.max(0, t));
}

function mix(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function smooth(t: number, from: number, to: number) {
  'worklet';
  const u = clamp01((t - from) / (to - from));
  return u * u * (3 - 2 * u);
}

/** Where a spring released at `from` with `speed` is after `t` seconds on its way to `to`. */
function springAt(t: number, from: number, to: number, speed: number) {
  'worklet';
  const damped = OMEGA * Math.sqrt(1 - ZETA * ZETA);
  const away = from - to;
  const swing = (speed + ZETA * OMEGA * away) / damped;
  return to + Math.exp(-ZETA * OMEGA * t) * (away * Math.cos(damped * t) + swing * Math.sin(damped * t));
}

/** A pull past an end gives less and less. */
function rubber(over: number, room: number) {
  'worklet';
  return (1 - 1 / ((over * RUBBER) / room + 1)) * room;
}

/** The scroll offset autoPlay asks for `t` seconds into a loop; above `travel` before ON_AT is the snap's overshoot. */
function scriptAt(t: number, travel: number, end: number) {
  'worklet';
  const on = Math.min(ON_BY, end - travel);
  if (t < FOLD_AT) return 0;
  if (t < FOLD_AT + FOLD_PULL) {
    const u = (t - FOLD_AT) / FOLD_PULL;
    return FOLD_TO * travel * u * u;
  }
  if (t < ON_AT) {
    const at = t - FOLD_AT - FOLD_PULL;
    return at >= SETTLE ? travel : springAt(at, FOLD_TO * travel, travel, (2 * FOLD_TO * travel) / FOLD_PULL);
  }
  if (t < BACK_AT) return travel + on * smooth(t, ON_AT, ON_AT + ON_FOR);
  const from = travel + on;
  const to = BACK_TO * travel;
  if (t < BACK_AT + BACK_PULL) {
    const u = (t - BACK_AT) / BACK_PULL;
    return from + (to - from) * u * u;
  }
  const at = t - BACK_AT - BACK_PULL;
  return at >= SETTLE ? 0 : springAt(at, to, 0, (2 * (to - from)) / BACK_PULL);
}

function clockText(seconds: number) {
  const whole = Math.max(0, Math.round(seconds));
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}

function tick() {
  if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

function Glyph({ children, size = 24 }: { children: ReactNode; size?: number }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {children}
    </Svg>
  );
}

/** A circular arrow round "15": back when it turns to the left, forward when mirrored. */
function Skip({ forward }: { forward?: boolean }) {
  return (
    <View style={styles.skip}>
      <View style={forward && styles.mirrored}>
        <Glyph size={SKIP}>
          <Path d={SKIP_ARC} stroke={INK} strokeWidth={1.7} strokeLinecap="round" fill="none" />
          <Path d={SKIP_HEAD} fill={INK} />
        </Glyph>
      </View>
      <Text style={styles.skipLabel}>15</Text>
    </View>
  );
}

const AnimatedPath = Animated.createAnimatedComponent(Path);

/**
 * A podcast screen whose top is a full player: date, show, a large title, a progress track and
 * the transport row. Scrolling the notes folds it into a one-line mini player that stays as the
 * header: the track travels up and narrows, the cover grows in at its left, the big button shrinks
 * to its right. Everything is placed from one number, the scroll offset.
 */
export function CollapsingPlayerHeader({
  date = 'Yesterday',
  show = 'Field Notes Daily',
  title = 'The longest night\nof the awards\nseason',
  duration = 1706,
  position = 519,
  notesTitle = 'Episode Notes',
  tabs = ['Description', 'Links'],
  notes = NOTES,
  accent = '#F2E94E',
  autoPlay = false,
  onPlayingChange,
  onSeek,
  onCollapsedChange,
  onBookmark,
  onCast,
  onMore,
}: Props) {
  const reducedMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const window = useWindowDimensions();
  // The screen as laid out; the window's size stands in until then.
  const [laidOut, setLaidOut] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = laidOut ?? window;
  const [notesHeight, setNotesHeight] = useState(0);
  const [playing, setPlaying] = useState(true);
  // The position shown, in whole seconds.
  const [seconds, setSeconds] = useState(position);
  const [tab, setTab] = useState(0);
  const [collapsed, setCollapsed] = useState(false);
  // Something other than playback is in motion and needs the frame clock.
  const [moving, setMoving] = useState(false);

  // Centres of the notes' heading and of the transport row, open and folded.
  const headingOpen = height - HEADING_UP;
  const headingMini = insets.top + HEADING_CENTER;
  const rowOpen = headingOpen - ROW_ABOVE;
  const rowMini = insets.top + MINI_CENTER;
  // The scroll that folds the player, and the offset at which the notes end.
  const travel = headingOpen - headingMini;
  const end = travel + Math.max(0, headingMini + NOTES_GAP_MINI + notesHeight + NOTES_END + insets.bottom - height);

  // The notes' scroll offset: below 0 and past `end` while a pull stretches it.
  const scroll = useSharedValue(0);
  // How far the snap went past the folded player: the whole screen rides up by this.
  const lift = useSharedValue(0);
  const held = useSharedValue(false);
  const grabbed = useSharedValue(0);
  // What moves the offset when no finger does, and the motion it is in.
  const mode = useSharedValue(IDLE);
  const from = useSharedValue(0);
  const to = useSharedValue(0);
  const speed = useSharedValue(0);
  // Times of the frame clock, in milliseconds; a negative one is set on the next frame.
  const since = useSharedValue(-1);
  const origin = useSharedValue(-1);
  const idleAt = useSharedValue(0);
  const lap = useSharedValue(-1);
  // Playback: the position at `baseAt`, running on from there.
  const base = useSharedValue(position);
  const baseAt = useSharedValue(-1);
  const running = useSharedValue(true);
  const shown = useSharedValue(position);
  const awake = useSharedValue(false);
  // 0 is the pause glyph, 1 the play glyph.
  const glyph = useSharedValue(0);
  const fade = useSharedValue(1);

  // How far the player is folded, 0 to 1. Reduced motion shows one layout or the other.
  const progress = useDerivedValue(() => {
    const p = clamp01(scroll.get() / travel);
    return reducedMotion ? (p >= SNAP_AT ? 1 : 0) : p;
  });
  // A pull at the top carries the whole screen down; the snap's overshoot carries it up.
  const shift = useDerivedValue(() => -Math.min(scroll.get(), 0) - lift.get());
  const row = useDerivedValue(() => mix(rowOpen, rowMini, progress.get()) + shift.get());
  const track = useDerivedValue(() => row.get() - mix(TRACK_ABOVE - ROW_ABOVE, TRACK_RISE, smooth(progress.get(), DROP_FROM, 1)));
  const buttonX = useDerivedValue(() => mix(width / 2, width - MARGIN - SMALL / 2, smooth(progress.get(), 0, 1)));

  const changed = (now: boolean) => {
    setCollapsed(now);
    onCollapsedChange?.(now);
  };

  useAnimatedReaction(
    () => progress.get() > 0.5,
    (now, old) => {
      if (old === null || now === old) return;
      scheduleOnRN(changed, now);
      // Reduced motion: the other layout fades in instead of travelling.
      if (reducedMotion) {
        fade.set(0);
        fade.set(withTiming(1, { duration: REDUCED_FADE, reduceMotion: ReduceMotion.Never }));
      }
    },
  );

  /** autoPlay starts over: playback and the tabs are as they were given. */
  const rest = () => {
    setPlaying(true);
    setTab(0);
    glyph.set(0);
  };

  const springTo = (target: number, velocity: number) => {
    'worklet';
    if (reducedMotion) {
      scroll.set(target);
      lift.set(0);
      mode.set(IDLE);
      return;
    }
    from.set(scroll.get() + lift.get());
    to.set(target);
    speed.set(velocity);
    since.set(-1);
    mode.set(SPRING);
  };

  /** The finger left with `velocity` (points a second, positive towards the notes' end). */
  const release = (velocity: number) => {
    'worklet';
    const at = scroll.get();
    if (at < travel) {
      const target = at + velocity * PROJECT > SNAP_AT * travel ? travel : 0;
      if (at === target) mode.set(IDLE);
      else springTo(target, velocity);
    } else if (at > end) {
      springTo(end, 0);
    } else if (Math.abs(velocity) < REST_SPEED) {
      mode.set(IDLE);
    } else {
      from.set(at);
      speed.set(velocity);
      since.set(-1);
      mode.set(DECAY);
    }
  };

  const clock = useFrameCallback((frame) => {
    const now = frame.timestamp;
    if (idleAt.get() < 0) idleAt.set(now);
    if (!held.get()) {
      const kind = mode.get();
      if (kind === AUTO) {
        if (origin.get() < 0) origin.set(now);
        const elapsed = (now - origin.get()) / 1000;
        const n = Math.floor(elapsed / LOOP);
        if (n !== lap.get()) {
          // Every loop plays the same stretch of the episode.
          lap.set(n);
          base.set(position);
          baseAt.set(origin.get() + n * LOOP * 1000);
        }
        const t = elapsed - n * LOOP;
        const value = scriptAt(t, travel, end);
        const riding = t < ON_AT;
        scroll.set(riding ? Math.min(value, travel) : value);
        lift.set(riding ? Math.max(0, value - travel) : 0);
      } else if (kind === SPRING) {
        if (since.get() < 0) since.set(now);
        const t = (now - since.get()) / 1000;
        const value = t >= SETTLE ? to.get() : springAt(t, from.get(), to.get(), speed.get());
        // Past the folded player the notes do not scroll on: the screen rides up instead.
        const riding = to.get() === travel;
        scroll.set(riding ? Math.min(value, travel) : value);
        lift.set(riding ? Math.max(0, value - travel) : 0);
        if (t >= SETTLE) mode.set(IDLE);
      } else if (kind === DECAY) {
        if (since.get() < 0) since.set(now);
        const fall = Math.exp((-DECAY_RATE * (now - since.get())) / 1000);
        const value = from.get() + (speed.get() / DECAY_RATE) * (1 - fall);
        const inside = Math.min(end, Math.max(travel, value));
        scroll.set(inside);
        if (inside !== value || Math.abs(speed.get()) * fall < REST_SPEED) mode.set(IDLE);
      } else if (autoPlay && now - idleAt.get() >= USER_HOLD * 1000) {
        if (scroll.get() !== 0) {
          springTo(0, 0);
        } else {
          running.set(true);
          origin.set(now);
          lap.set(-1);
          mode.set(AUTO);
          scheduleOnRN(rest);
        }
      } else if (!autoPlay && awake.get()) {
        awake.set(false);
        scheduleOnRN(setMoving, false);
      }
    }

    if (baseAt.get() < 0) baseAt.set(now);
    const current = base.get() + (running.get() ? (now - baseAt.get()) / 1000 : 0);
    const second = Math.min(duration, Math.floor(current + 0.001));
    if (second !== shown.get()) {
      shown.set(second);
      scheduleOnRN(setSeconds, second);
    }
  });

  const active = autoPlay || playing || moving;
  useEffect(() => {
    clock.setActive(active);
  }, [clock, active]);

  useEffect(() => {
    if (!autoPlay) return;
    scroll.set(0);
    lift.set(0);
    origin.set(-1);
    lap.set(-1);
    mode.set(AUTO);
    return () => mode.set(IDLE);
  }, [autoPlay, scroll, lift, origin, lap, mode]);

  const pan = Gesture.Pan()
    .activeOffsetY([-8, 8])
    .onBegin(() => {
      // A touch takes over at once, wherever the offset is.
      held.set(true);
      mode.set(IDLE);
      awake.set(true);
      scheduleOnRN(setMoving, true);
    })
    .onStart(() => {
      grabbed.set(Math.min(end, Math.max(0, scroll.get())));
    })
    .onUpdate((event) => {
      const wanted = grabbed.get() - event.translationY;
      lift.set(0);
      scroll.set(wanted < 0 ? -rubber(-wanted, height) : wanted > end ? end + rubber(wanted - end, height) : wanted);
    })
    .onEnd((event) => {
      release(-event.velocityY);
    })
    .onFinalize((_, dragged) => {
      held.set(false);
      idleAt.set(-1);
      // A touch that never became a drag still has to leave the player open or folded.
      if (!dragged && mode.get() === IDLE) release(0);
    });

  const expand = () => {
    setMoving(true);
    awake.set(true);
    springTo(0, 0);
  };

  const toggle = () => {
    const next = !playing;
    tick();
    base.set(seconds);
    baseAt.set(-1);
    running.set(next);
    glyph.set(withTiming(next ? 0 : 1, { duration: MORPH }));
    setPlaying(next);
    onPlayingChange?.(next);
  };

  const skip = (by: number) => {
    const next = Math.min(duration, Math.max(0, seconds + by));
    tick();
    base.set(next);
    baseAt.set(-1);
    onSeek?.(next);
  };

  const choose = (index: number) => {
    if (index === tab) return;
    tick();
    // The other list starts at its top.
    if (scroll.get() > travel) scroll.set(travel);
    setTab(index);
  };

  const fadeStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));
  const titleStyle = useAnimatedStyle(() => {
    const left = 1 - clamp01(progress.get() / TITLE_GONE);
    return { opacity: left * left, transform: [{ translateY: -TITLE_SPEED * progress.get() * travel + shift.get() }] };
  });
  const trackStyle = useAnimatedStyle(() => {
    const p = progress.get();
    const squeeze = smooth(p, SQUEEZE_FROM, SQUEEZE_TO);
    const left = mix(MARGIN, MINI_LEFT, squeeze);
    const right = mix(width - MARGIN, width - MINI_RIGHT, squeeze);
    return {
      transform: [
        { translateX: left },
        { translateY: track.get() - TRACK / 2 },
        { scaleX: (right - left) / (width - MARGIN * 2) },
      ],
    };
  });
  const elapsedStyle = useAnimatedStyle(() => {
    const p = progress.get();
    const y = track.get() + mix(LABEL_DROP, LABEL_DROP_MINI, p) - LABEL_LINE / 2;
    return {
      transform: [
        { translateX: mix(MARGIN, MINI_LEFT, smooth(p, SQUEEZE_FROM, SQUEEZE_TO)) },
        { translateY: y },
        { scale: mix(1, LABEL_SHRINK, p) },
      ],
    };
  });
  const remainingStyle = useAnimatedStyle(() => {
    const p = progress.get();
    const y = track.get() + mix(LABEL_DROP, LABEL_DROP_MINI, p) - LABEL_LINE / 2;
    return {
      transform: [
        { translateX: -mix(MARGIN, MINI_RIGHT, smooth(p, SQUEEZE_FROM, SQUEEZE_TO)) },
        { translateY: y },
        { scale: mix(1, LABEL_SHRINK, p) },
      ],
    };
  });
  const coverStyle = useAnimatedStyle(() => {
    const grown = smooth(progress.get(), COVER_FROM, 1);
    return { opacity: grown, transform: [{ translateY: row.get() - COVER / 2 }, { scale: mix(0.3, 1, grown) }] };
  });
  const buttonStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: buttonX.get() - BIG / 2 },
      { translateY: row.get() - BIG / 2 },
      { scale: mix(1, SMALL / BIG, smooth(progress.get(), 0, 1)) },
    ],
  }));
  const backStyle = useAnimatedStyle(() => {
    const gone = clamp01(progress.get() / SKIPS_GONE);
    return {
      opacity: 1 - gone,
      transform: [
        { translateX: buttonX.get() - SKIP_GAP * mix(1, 0.6, gone) - HIT / 2 },
        { translateY: row.get() - HIT / 2 },
        { scale: mix(1, 0.8, gone) },
      ],
    };
  });
  const forwardStyle = useAnimatedStyle(() => {
    const gone = clamp01(progress.get() / SKIPS_GONE);
    return {
      opacity: 1 - gone,
      transform: [
        { translateX: buttonX.get() + SKIP_GAP * mix(1, 0.6, gone) - HIT / 2 },
        { translateY: row.get() - HIT / 2 },
        { scale: mix(1, 0.8, gone) },
      ],
    };
  });
  const sideStyle = useAnimatedStyle(() => ({
    opacity: 1 - clamp01(progress.get() / SIDES_GONE),
    transform: [{ translateY: row.get() - HIT / 2 }],
  }));
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [
      {
        translateY:
          headingOpen - progress.get() * travel - Math.max(0, scroll.get() - travel) + shift.get() - HEADING_LINE / 2,
      },
    ],
  }));
  const markStyle = useAnimatedStyle(() => ({ opacity: 1 - smooth(progress.get(), MARK_FROM, MARK_GONE) }));
  const headingStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: -MARK_SPACE * smooth(progress.get(), SLIDE_FROM, SLIDE_TO) }],
  }));
  const tabsStyle = useAnimatedStyle(() => ({ opacity: smooth(progress.get(), TABS_FROM, 1) }));
  const notesStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (NOTES_GAP_MINI - NOTES_GAP) * smooth(progress.get(), GAP_FROM, GAP_TO) }],
  }));
  const glyphProps = useAnimatedProps(() => {
    const m = glyph.get();
    let d = '';
    for (let shape = 0; shape < 2; shape++) {
      for (let corner = 0; corner < 8; corner += 2) {
        const x = mix(PAUSE[shape][corner], PLAY[shape][corner], m);
        const y = mix(PAUSE[shape][corner + 1], PLAY[shape][corner + 1], m);
        d += `${corner === 0 ? 'M' : 'L'}${x} ${y}`;
      }
      d += 'Z';
    }
    return { d };
  });

  const onLayout = (event: LayoutChangeEvent) => {
    const { width: w, height: h } = event.nativeEvent.layout;
    setLaidOut({ width: w, height: h });
  };

  // The second tab lists the links of the notes, one to a line.
  const shownNotes = tab === 0 ? notes : notes.flatMap((paragraph) => paragraph.filter((span) => span.link).map((span) => [span]));
  const played = Math.min(1, seconds / Math.max(1, duration));

  return (
    <GestureDetector gesture={pan}>
      <View style={styles.screen} onLayout={onLayout}>
        <StatusBar style="light" />
        <Animated.View style={[styles.fill, fadeStyle]}>
          <Animated.View style={[styles.sheet, sheetStyle]}>
            <View style={styles.heading}>
              <Animated.View style={[styles.mark, { backgroundColor: accent }, markStyle]} />
              <Animated.Text style={[styles.headingLabel, headingStyle]} numberOfLines={1} accessibilityRole="header">
                {notesTitle}
              </Animated.Text>
              <Pressable style={styles.more} hitSlop={12} accessibilityRole="button" accessibilityLabel="More" onPress={onMore}>
                {({ pressed }) => (
                  <View style={pressed && styles.pressed}>
                    <Glyph>
                      <Circle cx={5} cy={12} r={1.6} fill={MUTE} />
                      <Circle cx={12} cy={12} r={1.6} fill={MUTE} />
                      <Circle cx={19} cy={12} r={1.6} fill={MUTE} />
                    </Glyph>
                  </View>
                )}
              </Pressable>
            </View>

            <Animated.View style={[styles.tabs, tabsStyle]} pointerEvents={collapsed ? 'auto' : 'none'}>
              {tabs.map((label, index) => (
                <Pressable
                  key={label}
                  style={[styles.tab, index === tab && styles.tabOn]}
                  hitSlop={8}
                  accessibilityRole="tab"
                  accessibilityState={{ selected: index === tab }}
                  onPress={() => choose(index)}
                >
                  <Text style={[styles.tabLabel, index === tab && styles.tabLabelOn]} numberOfLines={1}>
                    {label.toUpperCase()}
                  </Text>
                </Pressable>
              ))}
            </Animated.View>

            <Animated.View style={[styles.notes, notesStyle]} onLayout={(event) => setNotesHeight(event.nativeEvent.layout.height)}>
              {shownNotes.map((paragraph, index) => (
                <Text key={index} style={[styles.paragraph, index > 0 && styles.paragraphGap]} lineBreakStrategyIOS="standard">
                  {paragraph.map((span, part) => (
                    <Text key={part} style={span.link && styles.link}>
                      {span.text}
                    </Text>
                  ))}
                </Text>
              ))}
            </Animated.View>
          </Animated.View>

          <View style={styles.header} pointerEvents="none">
            <View style={{ height: insets.top + HEADER, backgroundColor: PAGE }} />
            <LinearGradient colors={[PAGE, PAGE_CLEAR]} style={styles.headerFade} />
          </View>

          <Animated.View style={[styles.titles, { top: insets.top + DATE_TOP }, titleStyle]} pointerEvents="none">
            <Text style={styles.date} numberOfLines={1}>
              {date}
            </Text>
            <Text style={styles.show} numberOfLines={1}>
              {show}
            </Text>
            <Text style={styles.title} numberOfLines={3} accessibilityRole="header">
              {title}
            </Text>
          </Animated.View>

          {collapsed && (
            <Pressable
              style={[styles.miniTap, { top: rowMini - COVER / 2 }]}
              accessibilityRole="button"
              accessibilityLabel={`${show}. Open the player`}
              onPress={expand}
            />
          )}

          {/* Nothing from here to the buttons takes a touch, so a tap on the mini row reaches it. */}
          <Animated.View
            style={[styles.track, { width: width - MARGIN * 2 }, trackStyle]}
            pointerEvents="none"
            accessibilityRole="progressbar"
            accessibilityValue={{ min: 0, max: duration, now: seconds }}
          >
            <View style={[styles.played, { width: `${played * 100}%` }]} />
          </Animated.View>
          <Animated.Text style={[styles.time, styles.elapsed, elapsedStyle]} numberOfLines={1} pointerEvents="none">
            {clockText(seconds)}
          </Animated.Text>
          <Animated.Text style={[styles.time, styles.remaining, remainingStyle]} numberOfLines={1} pointerEvents="none">
            -{clockText(duration - seconds)}
          </Animated.Text>

          <Animated.View style={[styles.cover, { backgroundColor: accent }, coverStyle]} pointerEvents="none">
            {COVER_LINES.map(([left, top, length]) => (
              <View key={top} style={[styles.coverLine, { left, top, width: length }]} />
            ))}
          </Animated.View>

          <Animated.View style={[styles.side, styles.bookmark, sideStyle]} pointerEvents={collapsed ? 'none' : 'auto'}>
            <Pressable style={styles.hit} accessibilityRole="button" accessibilityLabel="Bookmark" onPress={onBookmark}>
              {({ pressed }) => (
                <View style={pressed && styles.pressed}>
                  <Glyph>
                    <Path d={BOOKMARK} stroke={GLYPH_DIM} strokeWidth={1.6} strokeLinejoin="round" fill="none" />
                  </Glyph>
                </View>
              )}
            </Pressable>
          </Animated.View>
          <Animated.View style={[styles.side, styles.cast, sideStyle]} pointerEvents={collapsed ? 'none' : 'auto'}>
            <Pressable style={styles.hit} accessibilityRole="button" accessibilityLabel="Play on another device" onPress={onCast}>
              {({ pressed }) => (
                <View style={pressed && styles.pressed}>
                  <Glyph>
                    <Path d={CAST_OUTER} stroke={GLYPH_DIM} strokeWidth={1.6} strokeLinecap="round" fill="none" />
                    <Path d={CAST_INNER} stroke={GLYPH_DIM} strokeWidth={1.6} strokeLinecap="round" fill="none" />
                    <Path d={CAST_BASE} stroke={GLYPH_DIM} strokeWidth={1} strokeLinejoin="round" fill={GLYPH_DIM} />
                  </Glyph>
                </View>
              )}
            </Pressable>
          </Animated.View>

          <Animated.View style={[styles.side, backStyle]} pointerEvents={collapsed ? 'none' : 'auto'}>
            <Pressable style={styles.hit} accessibilityRole="button" accessibilityLabel="Back 15 seconds" onPress={() => skip(-15)}>
              {({ pressed }) => (
                <View style={pressed && styles.pressed}>
                  <Skip />
                </View>
              )}
            </Pressable>
          </Animated.View>
          <Animated.View style={[styles.side, forwardStyle]} pointerEvents={collapsed ? 'none' : 'auto'}>
            <Pressable style={styles.hit} accessibilityRole="button" accessibilityLabel="Forward 15 seconds" onPress={() => skip(15)}>
              {({ pressed }) => (
                <View style={pressed && styles.pressed}>
                  <Skip forward />
                </View>
              )}
            </Pressable>
          </Animated.View>

          <Animated.View style={[styles.button, buttonStyle]}>
            <Pressable
              style={styles.buttonHit}
              hitSlop={collapsed ? 12 : 0}
              accessibilityRole="button"
              accessibilityLabel={playing ? 'Pause' : 'Play'}
              onPress={toggle}
            >
              {({ pressed }) => (
                <View style={[styles.buttonFace, pressed && styles.pressed]}>
                  <Svg width={BIG} height={BIG} viewBox={`0 0 ${BIG} ${BIG}`}>
                    <AnimatedPath animatedProps={glyphProps} fill={INK} stroke={INK} strokeWidth={1.5} strokeLinejoin="round" />
                  </Svg>
                </View>
              )}
            </Pressable>
          </Animated.View>
        </Animated.View>
      </View>
    </GestureDetector>
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
  sheet: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
    top: 0,
  },
  heading: {
    height: HEADING_LINE,
    flexDirection: 'row',
    alignItems: 'center',
  },
  mark: {
    width: 10,
    height: 14,
    borderRadius: 2.5,
    marginRight: MARK_SPACE - 10,
  },
  headingLabel: {
    flex: 1,
    color: INK,
    fontSize: 15,
    lineHeight: HEADING_LINE,
    fontWeight: '600',
    includeFontPadding: false,
  },
  more: {
    width: 24,
    height: 24,
  },
  pressed: {
    opacity: 0.6,
  },
  tabs: {
    position: 'absolute',
    left: 0,
    top: HEADING_LINE / 2 + TABS_BELOW - TAB_HEIGHT / 2,
    flexDirection: 'row',
    alignItems: 'center',
  },
  tab: {
    height: TAB_HEIGHT,
    paddingHorizontal: 9,
    borderRadius: 5,
    justifyContent: 'center',
  },
  tabOn: {
    backgroundColor: BUTTON,
  },
  tabLabel: {
    color: TAB_OFF,
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '600',
    letterSpacing: 0.5,
    includeFontPadding: false,
  },
  tabLabelOn: {
    color: INK,
  },
  notes: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: HEADING_LINE / 2 + NOTES_GAP,
  },
  paragraph: {
    color: BODY,
    fontSize: 14,
    lineHeight: 18,
    includeFontPadding: false,
  },
  paragraphGap: {
    marginTop: PARAGRAPH_GAP,
  },
  link: {
    color: INK,
  },
  header: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
  },
  headerFade: {
    height: HEADER_FADE,
  },
  titles: {
    position: 'absolute',
    left: MARGIN,
    right: MARGIN,
  },
  date: {
    color: MUTE,
    fontSize: 14,
    lineHeight: 18,
    includeFontPadding: false,
  },
  show: {
    marginTop: 2,
    color: INK,
    fontSize: 14,
    lineHeight: 18,
    fontWeight: '600',
    includeFontPadding: false,
  },
  title: {
    marginTop: 6,
    color: INK,
    fontSize: TITLE_SIZE,
    lineHeight: TITLE_LINE,
    fontWeight: '600',
    letterSpacing: -0.6,
    includeFontPadding: false,
  },
  miniTap: {
    position: 'absolute',
    left: MARGIN,
    right: MINI_RIGHT,
    height: COVER,
  },
  track: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: TRACK,
    borderRadius: TRACK / 2,
    backgroundColor: RULE,
    overflow: 'hidden',
    transformOrigin: 'left center',
  },
  played: {
    height: TRACK,
    borderRadius: TRACK / 2,
    backgroundColor: FILL,
  },
  time: {
    position: 'absolute',
    top: 0,
    color: MUTE,
    fontSize: 12,
    lineHeight: LABEL_LINE,
    fontVariant: ['tabular-nums'],
    includeFontPadding: false,
  },
  elapsed: {
    left: 0,
    transformOrigin: 'left center',
  },
  remaining: {
    right: 0,
    transformOrigin: 'right center',
  },
  cover: {
    position: 'absolute',
    left: MARGIN,
    top: 0,
    width: COVER,
    height: COVER,
    borderRadius: 5,
  },
  coverLine: {
    position: 'absolute',
    height: 3.5,
    borderRadius: 1,
    backgroundColor: COVER_INK,
  },
  side: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: HIT,
    height: HIT,
  },
  // The glyphs' outer edges, not their boxes, stand on the margins.
  bookmark: {
    left: MARGIN - 17,
  },
  cast: {
    left: undefined,
    right: MARGIN - 15,
  },
  hit: {
    width: HIT,
    height: HIT,
    alignItems: 'center',
    justifyContent: 'center',
  },
  skip: {
    width: SKIP,
    height: SKIP,
  },
  mirrored: {
    transform: [{ scaleX: -1 }],
  },
  skipLabel: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: (SKIP * 13) / 24 - 6,
    color: INK,
    fontSize: 9.5,
    lineHeight: 12,
    fontWeight: '700',
    textAlign: 'center',
    includeFontPadding: false,
  },
  button: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: BIG,
    height: BIG,
  },
  buttonHit: {
    width: BIG,
    height: BIG,
  },
  buttonFace: {
    width: BIG,
    height: BIG,
    borderRadius: BIG / 2,
    backgroundColor: BUTTON,
  },
});
