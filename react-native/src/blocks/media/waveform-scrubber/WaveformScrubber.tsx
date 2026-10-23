import {
  Canvas,
  CornerPathEffect,
  Group,
  LinearGradient,
  matchFont,
  Path,
  RadialGradient,
  RoundedRect,
  Skia,
  type SkPath,
  Text as SkiaText,
  vec,
} from '@shopify/react-native-skia';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { LinearGradient as Fade } from 'expo-linear-gradient';
import { useEffect, useMemo, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle, Path as SvgPath } from 'react-native-svg';
import { scheduleOnRN } from 'react-native-worklets';

export type WaveformTrack = {
  title: string;
  artist: string;
  album: string;
  /** Square cover art, also blurred behind the player. */
  cover: string;
  backdrop: string;
  /** Length of the track, in seconds. */
  duration: number;
};

type Props = {
  track?: WaveformTrack;
  /** Plays, scrubs forward and back across the waveform, plays on and pauses, on a loop. */
  autoPlay?: boolean;
  /** Colour of the played bars, the time bubble's text and the play button's ring. */
  accent?: [string, string];
  /** Called with the new position, in seconds, when a scrub is let go. */
  onSeek?: (seconds: number) => void;
};

const unsplash = (id: string, w: number, h: number) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;
const PHOTO = 'photo-1642019341061-f81d61659c62';

const TRACK: WaveformTrack = {
  title: 'Afterglow Avenue',
  artist: 'Juno Vale',
  album: 'Night Transit',
  cover: unsplash(PHOTO, 960, 960),
  // Drawn under a heavy blur, so a small file is enough.
  backdrop: unsplash(PHOTO, 480, 960),
  duration: 240,
};

const FAMILY = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';
const INK = '#f4f6fb';
const BASE_BAR = 'rgba(236, 242, 255, 0.30)';

// The waveform: BARS rounded bars across the screen less SIDE pt each side. A bar is BAR_FILL of
// its slot wide and at most BAR_HALF pt either side of the axis at rest.
const BARS = 60;
const SIDE = 28;
const BAR_FILL = 0.56;
const BAR_HALF = 28;
// Under the finger the bars grow by up to MAGNIFY (as a share of their height) and WIDEN (of their
// width), falling off over LENS_RADIUS pt like a bell curve: a loupe dragged along the track.
const MAGNIFY = 1.15;
const WIDEN = 0.5;
const LENS_RADIUS = 30;
// The canvas: the bubble rides above the bars, the times sit under them.
const AXIS_Y = 116;
const CANVAS_H = 206;
const TIMES_Y = 196;
const BUBBLE_H = 28;
const BUBBLE_GAP = 12;
const TAIL = 6;

// While it plays each bar sways by up to DANCE of its height on two sines of its own, and the
// bars around the playhead throb on every beat at BPM.
const DANCE = 0.14;
const BEAT = 0.22;
const BPM = 104;

// Dragging: the playhead follows the finger through a quick low-pass (FOLLOW per second). On
// release it is thrown THROW s ahead by the finger's speed and lands on the nearest bar's start
// with a spring that overshoots a touch. Past either end it is held back with rubber banding.
const FOLLOW = 32;
const THROW = 0.12;
const SNAP_STIFFNESS = 170;
const SNAP_DAMPING = 19;
const RUBBER = 7;
const SEEK_STEP = 10;
// A selection tick each time a scrub crosses a TICK_EVERY s mark.
const TICK_EVERY = 30;

// The play button and its icon morph.
const BUTTON = 76;
const MORPH = { duration: 420, dampingRatio: 0.68 };
const COVER = { duration: 560, dampingRatio: 0.74 };
const COVER_PAUSED = 0.86;

// autoPlay script (seconds into LOOP). It starts paused at REST_AT s into the track, plays,
// scrubs forward to SCRUB_TO and back to SCRUB_BACK, lets go (the playhead snaps to the start of
// that bar, 44 s), plays on and pauses again exactly at REST_AT, so one period loops cleanly.
const LOOP = 12;
const REST_AT = 47.8;
const PLAY_AT = 0.5;
const GRAB_AT = 3.4;
const FORWARD_END = 5.3;
const BACK_START = 5.7;
const BACK_END = 7.3;
const RELEASE_AT = 7.5;
const PAUSE_AT = 11.3;
const SCRUB_TO = 190;
const SCRUB_BACK = 45.6;
// A touch stops the script for USER_HOLD s, then the player settles back and the loop restarts.
const USER_HOLD = 4;
const SETTLE_LEAD = 0.9;

const EASE_IN_OUT = Easing.bezierFn(0.65, 0, 0.35, 1);

/** Loudness of each bar, 0…1: a quiet intro, two choruses around a dip, and an outro. */
function makeLevels(count: number) {
  const levels: number[] = [];
  for (let i = 0; i < count; i += 1) {
    const t = i / (count - 1);
    const shape =
      0.32 +
      0.5 * Math.exp(-(((t - 0.36) / 0.16) ** 2)) +
      0.58 * Math.exp(-(((t - 0.74) / 0.14) ** 2)) +
      0.18 * Math.sin(t * 9.2) * (1 - t);
    // A fixed hash, so the track looks the same every time.
    const grain = (Math.sin(i * 127.1 + 311.7) * 43758.5453) % 1;
    const jitter = 0.62 + 0.38 * Math.abs(grain);
    levels.push(Math.min(1, Math.max(0.14, shape * jitter * Math.min(1, 0.35 + t * 5))));
  }
  return levels;
}

/** 3:07 for 187 s. */
function clock(seconds: number) {
  'worklet';
  const s = Math.max(0, Math.floor(seconds));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${r < 10 ? '0' : ''}${r}`;
}

/** Distance past an edge, rubber-banded: it gives a little at first and less and less after. */
function rubber(over: number, limit: number) {
  'worklet';
  return limit * (1 - 1 / (1 + over / limit));
}

/** Whether a clock that went from `before` to `now` passed the moment `at`. */
function crossed(before: number, now: number, at: number) {
  'worklet';
  return before < at && now >= at;
}

function lerp(a: number, b: number, t: number) {
  'worklet';
  return a + (b - a) * t;
}

// The icon morph: a play triangle cut in two halves at its middle, and the two bars of pause,
// each four corners in the same order (top left, top right, bottom right, bottom left), centred
// on the button.
const PLAY_LEFT = [-7, -13, 3.5, -7.2, 3.5, 7.2, -7, 13];
const PLAY_RIGHT = [3.5, -7.2, 14, 0, 14, 0, 3.5, 7.2];
const PAUSE_LEFT = [-10, -12, -3, -12, -3, 12, -10, 12];
const PAUSE_RIGHT = [3, -12, 10, -12, 10, 12, 3, 12];

function iconPath(morph: number) {
  'worklet';
  const builder = Skia.PathBuilder.Make();
  const c = BUTTON / 2;
  // The icon dips a little mid-morph, so the halves read as turning over rather than sliding.
  const k = 1 - 0.14 * Math.sin(Math.PI * Math.min(1, Math.max(0, morph)));
  for (let half = 0; half < 2; half += 1) {
    const from = half === 0 ? PLAY_LEFT : PLAY_RIGHT;
    const to = half === 0 ? PAUSE_LEFT : PAUSE_RIGHT;
    for (let p = 0; p < 4; p += 1) {
      const x = c + lerp(from[p * 2], to[p * 2], morph) * k;
      const y = c + lerp(from[p * 2 + 1], to[p * 2 + 1], morph) * k;
      if (p === 0) builder.moveTo(x, y);
      else builder.lineTo(x, y);
    }
    builder.close();
  }
  return builder.build();
}

type Wave = { bars: SkPath; head: SkPath; peak: number };

/**
 * A night-drive player over a blurred neon street: the cover art, the title, and a waveform of
 * sixty rounded bars. Dragging across the waveform scrubs it: the bars under the finger swell like
 * a loupe passing over them, played bars fill with the accent, and a bubble rides above the finger
 * with the time under it. Letting go snaps the playhead to the start of a bar with a spring. The
 * play button morphs between play and pause, and while it plays the bars sway to the beat.
 */
export function WaveformScrubber({ track = TRACK, autoPlay = false, accent = ['#ff9a5c', '#ff3d7f'], onSeek }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [waveWidth, setWaveWidth] = useState(0);
  const font = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: 12, fontWeight: '600' }), []);
  const bubbleFont = useMemo(() => matchFont({ fontFamily: FAMILY, fontSize: 13, fontWeight: '700' }), []);
  const levels = useMemo(() => makeLevels(BARS), []);
  // Every position is a share of the duration: a track without a length must not divide by zero.
  const duration = Number.isFinite(track.duration) && track.duration > 0 ? track.duration : 1;

  const left = SIDE;
  const span = Math.max(1, waveWidth - SIDE * 2);
  const slot = span / BARS;

  // Position in the track, seconds. `playing` is what the button shows.
  const progress = useSharedValue(REST_AT);
  const playing = useSharedValue(false);
  // 0…1 eases toward playing, so the bars start and stop dancing softly.
  const energy = useSharedValue(0);
  const time = useSharedValue(0);
  // The finger on the waveform, in pt, and the loupe that follows it.
  const dragging = useSharedValue(false);
  const fingerX = useSharedValue(0);
  const lensX = useSharedValue(0);
  const lens = useSharedValue(0);
  const scrubSpeed = useSharedValue(0);
  const lastHead = useSharedValue(REST_AT);
  // The snap after a release: a spring on the playhead toward `snapTarget`.
  const snapping = useSharedValue(false);
  const snapTarget = useSharedValue(REST_AT);
  const snapVelocity = useSharedValue(0);
  const morph = useSharedValue(0);
  const cover = useSharedValue(0);
  const nudge = useSharedValue(0);
  const byUser = useSharedValue(false);
  const scripted = useSharedValue(false);
  const loopClock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);

  useEffect(() => {
    looping.set(auto);
    loopClock.set(0);
    hold.set(0);
  }, [auto, hold, loopClock, looping]);

  const impact = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };
  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const seeked = (seconds: number) => onSeek?.(seconds);

  /** Seconds under an x on the waveform, rubber-banded past either end. */
  const timeAt = (x: number) => {
    'worklet';
    const raw = ((x - left) / span) * duration;
    if (raw < 0) return -rubber(-raw, RUBBER);
    if (raw > duration) return duration + rubber(raw - duration, RUBBER);
    return raw;
  };
  const xOf = (seconds: number) => {
    'worklet';
    return left + (seconds / duration) * span;
  };

  const setPlaying = (on: boolean, user: boolean) => {
    'worklet';
    playing.set(on);
    morph.set(reducedMotion ? withTiming(on ? 1 : 0, { duration: 160 }) : withSpring(on ? 1 : 0, MORPH));
    cover.set(reducedMotion ? withTiming(on ? 1 : 0, { duration: 200 }) : withSpring(on ? 1 : 0, COVER));
    if (user) scheduleOnRN(impact);
  };

  const snapTo = (seconds: number, velocity: number) => {
    'worklet';
    snapTarget.set(Math.min(duration, Math.max(0, seconds)));
    snapVelocity.set(velocity);
    snapping.set(true);
  };

  const beginScrub = (x: number, user: boolean) => {
    'worklet';
    byUser.set(user);
    scripted.set(!user);
    snapping.set(false);
    dragging.set(true);
    fingerX.set(x);
    // The loupe opens where the finger lands rather than sliding in from the last scrub.
    if (lens.get() < 0.05) lensX.set(x);
    scrubSpeed.set(0);
    lastHead.set(progress.get());
  };

  const endScrub = (velocityX: number, user: boolean) => {
    'worklet';
    if (!dragging.get()) return;
    dragging.set(false);
    scripted.set(false);
    const throwBy = (velocityX / span) * duration * THROW;
    const landing = Math.min(duration, Math.max(0, timeAt(fingerX.get()) + throwBy));
    // The start of the bar the playhead lands in.
    const barSeconds = duration / BARS;
    const target = Math.round(landing / barSeconds) * barSeconds;
    if (reducedMotion) {
      progress.set(target);
      snapping.set(false);
    } else {
      snapTo(target, scrubSpeed.get());
    }
    if (user) {
      scheduleOnRN(impact);
      scheduleOnRN(seeked, target);
    }
  };

  /** Where the scripted finger is, in seconds of the track, `t` s into the loop. */
  const scriptAt = (t: number, from: number) => {
    'worklet';
    if (t < FORWARD_END) return lerp(from, SCRUB_TO, EASE_IN_OUT((t - GRAB_AT) / (FORWARD_END - GRAB_AT)));
    if (t < BACK_START) return SCRUB_TO + 1.6 * Math.sin(((t - FORWARD_END) / (BACK_START - FORWARD_END)) * Math.PI);
    if (t < BACK_END) return lerp(SCRUB_TO, SCRUB_BACK, EASE_IN_OUT((t - BACK_START) / (BACK_END - BACK_START)));
    return SCRUB_BACK;
  };
  const grabbedAt = useSharedValue(REST_AT);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 64) / 1000;
    if (dt <= 0 || span <= 1) return;
    time.set(time.get() + dt);

    // The script, while nobody is touching.
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0 && looping.get()) {
        // Settle back to the resting state, then start the loop over.
        if (playing.get()) setPlaying(false, false);
        snapTo(REST_AT, 0);
        loopClock.set(-SETTLE_LEAD);
      }
    } else if (looping.get()) {
      const before = loopClock.get();
      let t = before + dt;
      if (t >= LOOP) {
        t -= LOOP;
        // At the seam the player is paused at REST_AT; any drift is cleared without a visible jump.
        if (!playing.get() && !dragging.get() && !snapping.get()) progress.set(REST_AT);
      }
      loopClock.set(t);
      if (crossed(before, t, PLAY_AT) && !playing.get()) setPlaying(true, false);
      if (crossed(before, t, GRAB_AT)) {
        grabbedAt.set(progress.get());
        beginScrub(xOf(progress.get()), false);
      }
      if (scripted.get() && t >= GRAB_AT && t < RELEASE_AT) fingerX.set(xOf(scriptAt(t, grabbedAt.get())));
      if (crossed(before, t, RELEASE_AT) && scripted.get()) endScrub(0, false);
      if (crossed(before, t, PAUSE_AT) && playing.get()) setPlaying(false, false);
    }

    // The playhead.
    const head = progress.get();
    if (dragging.get()) {
      const target = timeAt(fingerX.get());
      progress.set(head + (target - head) * (1 - Math.exp(-dt * FOLLOW)));
      // The playhead's own speed, in s/s, carried into the snap spring on release.
      const speed = (progress.get() - lastHead.get()) / dt;
      scrubSpeed.set(scrubSpeed.get() + (speed - scrubSpeed.get()) * 0.4);
      lastHead.set(progress.get());
    } else if (snapping.get()) {
      if (playing.get()) snapTarget.set(Math.min(duration, snapTarget.get() + dt));
      const v =
        snapVelocity.get() + (SNAP_STIFFNESS * (snapTarget.get() - head) - SNAP_DAMPING * snapVelocity.get()) * dt;
      snapVelocity.set(v);
      progress.set(head + v * dt);
      if (Math.abs(snapTarget.get() - progress.get()) < 0.01 && Math.abs(v) < 0.4) {
        progress.set(snapTarget.get());
        snapping.set(false);
      }
    } else if (playing.get()) {
      const next = head + dt;
      if (next >= duration) {
        progress.set(duration);
        setPlaying(false, false);
      } else {
        progress.set(next);
      }
    }

    const wantEnergy = playing.get() && !reducedMotion ? (dragging.get() ? 0.35 : 1) : 0;
    energy.set(energy.get() + (wantEnergy - energy.get()) * (1 - Math.exp(-dt * 4)));
    const wantLens = dragging.get() ? 1 : 0;
    lens.set(lens.get() + (wantLens - lens.get()) * (1 - Math.exp(-dt * (dragging.get() ? 16 : 9))));
    lensX.set(lensX.get() + (fingerX.get() - lensX.get()) * (1 - Math.exp(-dt * 26)));
  });

  // A tick each time a scrub by hand crosses a half-minute mark: detents on the track.
  useAnimatedReaction(
    () => (dragging.get() && byUser.get() ? Math.floor(Math.min(duration, Math.max(0, progress.get())) / TICK_EVERY) : -1),
    (now, before) => {
      if (now >= 0 && before !== null && before >= 0 && now !== before) scheduleOnRN(tick);
    },
  );

  // Every bar, its loudness swayed by the music and swollen by the loupe, in one path.
  const wave = useDerivedValue<Wave>(() => {
    const builder = Skia.PathBuilder.Make();
    const e = energy.get();
    const now = time.get();
    const loupe = reducedMotion ? 0 : lens.get();
    const lx = lensX.get();
    const headBar = (progress.get() / duration) * BARS;
    const beat = (now * BPM) / 60;
    const pulse = Math.exp(-(beat - Math.floor(beat)) * 5) * e;
    let peak = 0;
    let headHalf = BAR_HALF * 0.4;
    for (let i = 0; i < BARS; i += 1) {
      const cx = left + slot * (i + 0.5);
      const near = Math.exp(-(((cx - lx) / LENS_RADIUS) ** 2)) * loupe;
      const sway =
        DANCE * e * (0.55 * Math.sin(now * (5.1 + (i % 7) * 0.53) + i * 1.7) + 0.45 * Math.sin(now * (3.3 + (i % 5) * 0.71) + i * 0.9));
      const throb = BEAT * pulse * Math.exp(-(((i - headBar) / 5) ** 2));
      const half = Math.max(1.6, BAR_HALF * levels[i] * (1 + sway + throb) * (1 + MAGNIFY * near));
      const w = slot * BAR_FILL * (1 + WIDEN * near);
      builder.addRRect({
        rect: { x: cx - w / 2, y: AXIS_Y - half, width: w, height: half * 2 },
        rx: w / 2,
        ry: w / 2,
      });
      if (near > 0.02) peak = Math.max(peak, half);
      if (Math.abs(i + 0.5 - headBar) < 0.5) headHalf = half;
    }
    const x = xOf(Math.min(duration, Math.max(0, progress.get())));
    const reach = headHalf + 9;
    const head = Skia.PathBuilder.Make()
      .addRRect({ rect: { x: x - 1, y: AXIS_Y - reach, width: 2, height: reach * 2 }, rx: 1, ry: 1 })
      .build();
    return { bars: builder.build(), head, peak };
  });

  const bars = useDerivedValue(() => wave.get().bars);
  const headPath = useDerivedValue(() => wave.get().head);
  const playedClip = useDerivedValue(() => ({
    x: 0,
    y: 0,
    width: Math.max(0, xOf(progress.get())),
    height: CANVAS_H,
  }));
  const headOpacity = useDerivedValue(() => 0.55 + 0.45 * lens.get());
  const glowCenter = useDerivedValue(() => vec(lensX.get(), AXIS_Y));
  const glowOpacity = useDerivedValue(() => lens.get());

  // The bubble: it rides above the tallest bar under the finger and shows the time under it.
  const bubbleText = useDerivedValue(() => clock(Math.min(duration, Math.max(0, progress.get()))));
  const bubbleWidth = useDerivedValue(() => bubbleFont.measureText(bubbleText.get()).width + 22);
  const bubbleX = useDerivedValue(() => {
    const w = bubbleWidth.get();
    const x = Math.min(duration, Math.max(0, progress.get()));
    return Math.min(waveWidth - 8 - w, Math.max(8, xOf(x) - w / 2));
  });
  const bubbleBottom = useDerivedValue(() => AXIS_Y - Math.max(BAR_HALF, wave.get().peak) - BUBBLE_GAP);
  // The bubble shows only over the middle of the loupe's opening, so it pops in and out quickly
  // instead of lingering as a faint pill.
  const bubbleOpacity = useDerivedValue(() => {
    const k = Math.min(1, Math.max(0, (lens.get() - 0.35) / 0.5));
    return k * k * (3 - 2 * k);
  });
  const bubbleOrigin = useDerivedValue(() => vec(xOf(Math.min(duration, Math.max(0, progress.get()))), bubbleBottom.get()));
  const bubbleTransform = useDerivedValue(() => {
    const l = lens.get();
    return [{ translateY: reducedMotion ? 0 : (1 - l) * 10 }, { scale: reducedMotion ? 1 : 0.7 + 0.3 * l }];
  });
  const bubbleRect = useDerivedValue(() => ({
    rect: { x: bubbleX.get(), y: bubbleBottom.get() - TAIL - BUBBLE_H, width: bubbleWidth.get(), height: BUBBLE_H },
    rx: BUBBLE_H / 2,
    ry: BUBBLE_H / 2,
  }));
  const bubbleTail = useDerivedValue(() => {
    const ax = Math.min(
      bubbleX.get() + bubbleWidth.get() - 14,
      Math.max(bubbleX.get() + 14, xOf(Math.min(duration, Math.max(0, progress.get())))),
    );
    const y = bubbleBottom.get();
    return Skia.PathBuilder.Make()
      .moveTo(ax - TAIL - 1, y - TAIL - 0.5)
      .lineTo(ax, y)
      .lineTo(ax + TAIL + 1, y - TAIL - 0.5)
      .close()
      .build();
  });
  const bubbleTextX = useDerivedValue(() => bubbleX.get() + 11);
  const bubbleTextY = useDerivedValue(() => bubbleBottom.get() - TAIL - BUBBLE_H / 2 + 4.6);

  const elapsedText = useDerivedValue(() => clock(Math.min(duration, Math.max(0, progress.get()))));
  const remainingText = useDerivedValue(() => `-${clock(duration - Math.min(duration, Math.max(0, progress.get())))}`);
  const remainingX = useDerivedValue(() => waveWidth - SIDE - font.measureText(remainingText.get()).width);

  const icon = useDerivedValue(() => iconPath(morph.get()));

  const coverStyle = useAnimatedStyle(() => ({
    transform: [{ scale: reducedMotion ? 1 : COVER_PAUSED + (1 - COVER_PAUSED) * cover.get() }],
  }));
  const shadowStyle = useAnimatedStyle(() => ({ opacity: 0.35 + 0.65 * cover.get() }));
  const press = useSharedValue(0);
  const buttonStyle = useAnimatedStyle(() => ({
    transform: [{ scale: 1 - 0.07 * press.get() }],
  }));
  const backStyle = useAnimatedStyle(() => ({ transform: [{ translateX: Math.min(0, nudge.get()) }] }));
  const forwardStyle = useAnimatedStyle(() => ({ transform: [{ translateX: Math.max(0, nudge.get()) }] }));

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    // A scripted scrub in flight is handed over to the finger.
    if (scripted.get()) {
      scripted.set(false);
      dragging.set(false);
    }
  };

  // One finger scrubs: with two, the pan's point is their average and the playhead would jump.
  const scrub = Gesture.Pan()
    .minDistance(0)
    .maxPointers(1)
    .hitSlop({ top: 12, bottom: 12 })
    .onBegin((event) => {
      touched();
      beginScrub(event.x, true);
    })
    .onUpdate((event) => {
      if (!dragging.get() || !byUser.get()) return;
      fingerX.set(event.x);
    })
    .onEnd((event) => {
      if (byUser.get()) endScrub(event.velocityX, true);
    })
    .onFinalize(() => {
      if (byUser.get()) endScrub(0, true);
    });

  const toggle = Gesture.Tap()
    .maxDuration(600)
    .onBegin(() => {
      touched();
      press.set(withTiming(1, { duration: 110 }));
    })
    .onFinalize(() => {
      press.set(withSpring(0, { duration: 320, dampingRatio: 0.6 }));
    })
    .onEnd((_event, success) => {
      if (success) setPlaying(!playing.get(), true);
    });

  const seekBy = (step: number) => {
    'worklet';
    touched();
    const from = snapping.get() ? snapTarget.get() : progress.get();
    if (reducedMotion) progress.set(Math.min(duration, Math.max(0, from + step)));
    else snapTo(from + step, 0);
    nudge.set(withSpring(0, { duration: 380, dampingRatio: 0.5, velocity: step > 0 ? 260 : -260 }));
    scheduleOnRN(impact);
  };
  const back = Gesture.Tap().onEnd((_e, success) => {
    if (success) seekBy(-SEEK_STEP);
  });
  const forward = Gesture.Tap().onEnd((_e, success) => {
    if (success) seekBy(SEEK_STEP);
  });

  const coverSize = size ? Math.min(size.width - 80, size.height * 0.36) : 0;

  return (
    <View
      style={styles.screen}
      onLayout={(event: LayoutChangeEvent) => {
        const { width, height } = event.nativeEvent.layout;
        setSize({ width, height });
      }}
    >
      <Image source={{ uri: track.backdrop }} style={styles.backdrop} contentFit="cover" transition={300} />
      <BlurView intensity={80} tint="dark" style={StyleSheet.absoluteFill} />
      <Fade
        colors={['rgba(6, 10, 18, 0.35)', 'rgba(6, 10, 18, 0.1)', 'rgba(6, 8, 14, 0.78)']}
        locations={[0, 0.4, 1]}
        style={StyleSheet.absoluteFill}
      />

      <View style={[styles.column, { paddingTop: insets.top + 6, paddingBottom: insets.bottom + 14 }]}>
        <View style={styles.topBar}>
          <Svg width={28} height={28} viewBox="0 0 28 28">
            <SvgPath d="M7 11 L14 18 L21 11" stroke={INK} strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
          <View style={styles.topText}>
            <Text style={styles.overline}>PLAYING FROM ALBUM</Text>
            <Text style={styles.album} numberOfLines={1}>
              {track.album}
            </Text>
          </View>
          <Svg width={28} height={28} viewBox="0 0 28 28">
            <Circle cx={7} cy={14} r={2.1} fill={INK} />
            <Circle cx={14} cy={14} r={2.1} fill={INK} />
            <Circle cx={21} cy={14} r={2.1} fill={INK} />
          </Svg>
        </View>

        <View style={styles.coverArea}>
          {coverSize > 0 && (
            <Animated.View style={[{ width: coverSize, height: coverSize }, coverStyle]}>
              <Animated.View style={[styles.coverShadow, shadowStyle]} />
              <Image
                source={{ uri: track.cover }}
                style={[styles.cover, { width: coverSize, height: coverSize }]}
                contentFit="cover"
                transition={300}
                accessibilityLabel={`${track.album} cover art`}
              />
            </Animated.View>
          )}
        </View>

        <View style={styles.meta}>
          <Text style={styles.title} numberOfLines={1}>
            {track.title}
          </Text>
          <Text style={styles.artist} numberOfLines={1}>
            {track.artist}
          </Text>
        </View>

        <GestureDetector gesture={scrub}>
          <View
            style={styles.wave}
            onLayout={(event: LayoutChangeEvent) => setWaveWidth(event.nativeEvent.layout.width)}
            accessible
            accessibilityRole="adjustable"
            accessibilityLabel="Track position"
          >
            {waveWidth > 0 && (
              <Canvas style={StyleSheet.absoluteFill}>
                <Path path={bars} color={BASE_BAR} />
                {/* The loupe lights the bars it passes over. */}
                <Group opacity={glowOpacity}>
                  <Path path={bars}>
                    <RadialGradient c={glowCenter} r={LENS_RADIUS * 2.2} colors={['rgba(255,255,255,0.75)', 'rgba(255,255,255,0)']} />
                  </Path>
                </Group>
                <Group clip={playedClip}>
                  <Path path={bars}>
                    <LinearGradient start={vec(left, 0)} end={vec(left + span, 0)} colors={accent} />
                  </Path>
                </Group>
                <Path path={headPath} color={INK} opacity={headOpacity} />
                <SkiaText x={SIDE} y={TIMES_Y} text={elapsedText} font={font} color="rgba(236, 242, 255, 0.7)" />
                <SkiaText x={remainingX} y={TIMES_Y} text={remainingText} font={font} color="rgba(236, 242, 255, 0.5)" />
                <Group opacity={bubbleOpacity} transform={bubbleTransform} origin={bubbleOrigin}>
                  <RoundedRect rect={bubbleRect} color={INK} />
                  <Path path={bubbleTail} color={INK} />
                  <SkiaText x={bubbleTextX} y={bubbleTextY} text={bubbleText} font={bubbleFont} color={accent[1]} />
                </Group>
              </Canvas>
            )}
          </View>
        </GestureDetector>

        <View style={styles.controls}>
          <GestureDetector gesture={back}>
            <Animated.View style={[styles.seek, backStyle]} accessible accessibilityRole="button" accessibilityLabel="Back 10 seconds">
              <SeekIcon direction={-1} />
            </Animated.View>
          </GestureDetector>
          <GestureDetector gesture={toggle}>
            <Animated.View style={[styles.play, buttonStyle]} accessible accessibilityRole="button" accessibilityLabel="Play or pause">
              <Canvas style={styles.playCanvas}>
                <Path path={icon} color="#0b0d14">
                  <CornerPathEffect r={2.6} />
                </Path>
              </Canvas>
            </Animated.View>
          </GestureDetector>
          <GestureDetector gesture={forward}>
            <Animated.View style={[styles.seek, forwardStyle]} accessible accessibilityRole="button" accessibilityLabel="Forward 10 seconds">
              <SeekIcon direction={1} />
            </Animated.View>
          </GestureDetector>
        </View>

        <Text style={styles.hint}>Drag across the waveform to scrub</Text>
      </View>
    </View>
  );
}

/** Two arrowheads, rounded, pointing back or forward. */
function SeekIcon({ direction }: { direction: 1 | -1 }) {
  const d = 'M4 7.5 Q4 6 5.3 6.8 L14 12.6 Q15 13.3 14 14 L5.3 19.8 Q4 20.6 4 19 Z M15 7.5 Q15 6 16.3 6.8 L25 12.6 Q26 13.3 25 14 L16.3 19.8 Q15 20.6 15 19 Z';
  return (
    <Svg width={30} height={27} viewBox="0 0 30 27" style={{ transform: [{ scaleX: direction }] }}>
      <SvgPath d={d} fill={INK} />
    </Svg>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#05070c',
    overflow: 'hidden',
  },
  backdrop: {
    position: 'absolute',
    top: -40,
    right: -40,
    bottom: -40,
    left: -40,
  },
  column: {
    flex: 1,
  },
  topBar: {
    height: 48,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
  },
  topText: {
    flex: 1,
    alignItems: 'center',
    gap: 1,
  },
  overline: {
    fontFamily: FAMILY,
    fontSize: 10,
    fontWeight: '600',
    letterSpacing: 1.6,
    color: 'rgba(236, 242, 255, 0.55)',
  },
  album: {
    fontFamily: FAMILY,
    fontSize: 14,
    fontWeight: '600',
    color: INK,
  },
  coverArea: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  coverShadow: {
    position: 'absolute',
    top: 18,
    right: 14,
    bottom: -6,
    left: 14,
    borderRadius: 28,
    borderCurve: 'continuous',
    boxShadow: '0 26px 50px rgba(0, 0, 0, 0.6)',
    backgroundColor: 'rgba(0, 0, 0, 0.2)',
  },
  cover: {
    borderRadius: 22,
    borderCurve: 'continuous',
    // Shows if the photo cannot load, so the card never turns into a bare shadow.
    backgroundColor: '#1b1f2c',
  },
  meta: {
    paddingHorizontal: SIDE,
    gap: 2,
    marginTop: 4,
  },
  title: {
    fontFamily: FAMILY,
    fontSize: 25,
    fontWeight: '700',
    letterSpacing: -0.3,
    color: INK,
  },
  artist: {
    fontFamily: FAMILY,
    fontSize: 16,
    fontWeight: '500',
    color: 'rgba(236, 242, 255, 0.62)',
  },
  wave: {
    height: CANVAS_H,
    marginTop: -14,
  },
  controls: {
    height: 92,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 44,
  },
  seek: {
    width: 52,
    height: 52,
    alignItems: 'center',
    justifyContent: 'center',
  },
  play: {
    width: BUTTON,
    height: BUTTON,
    borderRadius: BUTTON / 2,
    backgroundColor: INK,
    boxShadow: '0 10px 30px rgba(0, 0, 0, 0.45)',
  },
  playCanvas: {
    width: BUTTON,
    height: BUTTON,
  },
  hint: {
    textAlign: 'center',
    fontFamily: FAMILY,
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 0.2,
    color: 'rgba(236, 242, 255, 0.4)',
    marginTop: 6,
  },
});
