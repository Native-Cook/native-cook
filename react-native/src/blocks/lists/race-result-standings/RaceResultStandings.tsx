import {
  Blend,
  BlurMask,
  Canvas,
  Circle,
  FilterMode,
  Group,
  Image as SkiaImage,
  LinearGradient as SkiaGradient,
  MipmapMode,
  Path,
  Rect,
  RoundedRect,
  Skia,
  Text as SkiaText,
  useFont,
  useImage,
  vec,
  type SkFont,
  type SkPath,
} from '@shopify/react-native-skia';
import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { AccessibilityInfo, AppState, StyleSheet, Text, useWindowDimensions, View, type TextStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { Award, BackArrow, ChangeBadge, Chevron, LiveryMark, Stopwatch, Wheel, type BadgeKind } from './Icons';
import {
  CAR,
  carAlpha,
  carLaps,
  carLateral,
  clamp01,
  cometAlpha,
  finishFlash,
  FINISH_FLASH,
  headerDim,
  leaderPace,
  lerp,
  lightOn,
  LIGHTS,
  lightsHousing,
  MAP,
  mapOpen,
  RACE,
  REPLAY,
  replayPoints,
  replayPlace,
  REPLAY_FLAGGED,
  replayRow,
  TAG,
  tagAlpha,
  TRAIL,
  DRAG,
  easeOut,
  FL_ROLL,
  FLASH,
  frameAt,
  LAP,
  LAP_END,
  GLOW_LAG,
  lapPlace,
  lineFlash,
  LOOP,
  podiumProgress,
  pointsProgress,
  PRESS,
  REDUCED_FADE,
  RESUME_AFTER,
  ROW,
  rowProgress,
  rowSpeed,
  seg,
  SETTLE,
  SWITCH,
  TAIL,
  towards,
  WINDOWS,
  wordProgress,
} from './motion';
import { LINE_CANVAS, LINE_POINTS, LINE_XY } from './track-line';
import { ASCENT, backdrop, carShade, colors, copy, drivers, FONT, icons, layout, REF_TOP, REF_W, sessions, SLOT_OF, space, teams } from './theme';

export { LOOP };

type Props = {
  /** Replays the race on the circuit every LOOP seconds; touch takes over, and two seconds after it the loop resumes. */
  autoPlay?: boolean;
};

const TRACK_IMAGE = require('./assets/track.png');
/** The same Blender render at 9× (810 × 870 px), drawn once the circuit is enlarged. */
const TRACK_LARGE = require('./assets/track-large.png');
const LAPS_DRAWN = 4; // the cars' trails trim a path of four laps, so a trail never wraps
const AUTO_MODE = 0;
const MANUAL = 1;
const LINE = 1.219; // Montserrat line box, × font size
const ROW_ANCHOR = layout.rows.firstRule - space.rowPitch; // reference y of row 0's block
const ROW_MID = (layout.rows.nameBaseline + layout.rows.teamBaseline) / 2 - 6;
const SLOTS = [0, 1, 2, 3, 4, 5];
const LIGHT_SLOTS = [0, 1, 2, 3, 4];
const HEADER_DIM = 0.95; // the header copy keeps 5 % while the circuit is out (it shows through the track's hollow)
const REPLAY_CANVAS_BOTTOM = 372; // reference y: the open circuit and its overshoot stay above this
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const PODIUM_PAD = 4; // pt of room above and below a livery mark inside its wipe window
const PODIUM_CODE_ROOM = 30; // pt kept for the three-letter code after its x
const BADGE_SLIDE = 3; // pt the badge drifts as it flips
/** The comet, in pt: tail core width, head radius, glow radius and blur. */
const COMET = { head: 3, glow: 8, glowBlur: 5, glowStroke: 5, glowStrokeBlur: 3 } as const;

export function RaceResultStandings({ autoPlay = false }: Props) {
  // Text laid out before its font is registered keeps the fallback face on iOS: mount nothing until then.
  const [loaded] = useFonts({
    [FONT.medium]: require('./assets/Montserrat-Medium.ttf'),
    [FONT.semibold]: require('./assets/Montserrat-SemiBold.ttf'),
    [FONT.bold]: require('./assets/Montserrat-Bold.ttf'),
    [FONT.extrabold]: require('./assets/Montserrat-ExtraBold.ttf'),
  });
  return <View style={styles.root}>{loaded ? <Standings autoPlay={autoPlay} /> : null}</View>;
}

function Standings({ autoPlay }: { autoPlay: boolean }) {
  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const S = W / REF_W;
  const X = (x: number) => x * S;
  const Y = (y: number) => insets.top + (y - REF_TOP) * S;
  const textTop = (baseline: number, size: number) => Y(baseline) - ASCENT * size * S;

  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced);
  const [screenReader, setScreenReader] = useState(false);
  const [shown, setShown] = useState(0);
  const [words, setWords] = useState<[number, number]>([0, 0]);
  const still = reduced || screenReader;

  // The whole screen is a function of these.
  const from = useSharedValue(0);
  const u = useSharedValue(0);
  const lap = useSharedValue(0);
  const flFrom = useSharedValue(0);
  const flU = useSharedValue(0);
  const press = useSharedValue(0);
  const rp = useSharedValue(0); // race replay clock (s), 0 = off
  const pendingReplay = useSharedValue(false);
  const trackPress = useSharedValue(0);
  const scrubBlocked = useSharedValue(false);
  const backPress = useSharedValue(0);
  const stretch = useSharedValue(0);
  const fade = useSharedValue(1);
  // The clock and who owns it.
  const mode = useSharedValue(autoPlay && !initialReduced ? AUTO_MODE : MANUAL);
  const time = useSharedValue(0);
  const last = useSharedValue(-1);
  const now = useSharedValue(0);
  const touchedAt = useSharedValue(0);
  const dragging = useSharedValue(false);
  const switching = useSharedValue(false);
  const u0 = useSharedValue(0);
  const foreground = useSharedValue(AppState.currentState === 'active');

  useEffect(() => {
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => sub.remove();
  }, []);
  useEffect(() => {
    let mounted = true;
    void AccessibilityInfo.isScreenReaderEnabled().then((on) => {
      if (mounted) setScreenReader(on);
    });
    const sub = AccessibilityInfo.addEventListener('screenReaderChanged', setScreenReader);
    return () => {
      mounted = false;
      sub.remove();
    };
  }, []);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      foreground.set(state === 'active');
      last.set(-1);
    });
    return () => sub.remove();
  }, [foreground, last]);

  // Reduced motion or a screen reader: the rest frame, still; switches are a 200 ms cross-fade.
  useEffect(() => {
    if (!still) {
      if (autoPlay) {
        mode.set(AUTO_MODE);
        time.set(0);
      }
      return;
    }
    mode.set(MANUAL);
    cancelAnimation(u);
    cancelAnimation(lap);
    cancelAnimation(flU);
    cancelAnimation(rp);
    rp.set(0);
    pendingReplay.set(false);
    const session = u.get() >= 0.5 ? 1 - from.get() : from.get();
    from.set(session);
    u.set(0);
    lap.set(0);
    flFrom.set(session);
    flU.set(0);
    press.set(0);
  }, [still, autoPlay, mode, time, u, lap, flU, from, flFrom, press, rp, pendingReplay]);

  const tick = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
  };
  const lightsOut = () => {
    if (process.env.EXPO_OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
  };

  // ---- Control, all on the UI thread. Helpers come before their callers. ----
  const displayed = () => {
    'worklet';
    return u.get() >= 0.5 ? 1 - from.get() : from.get();
  };
  const rollFastest = () => {
    'worklet';
    if (flU.get() >= 1) {
      flFrom.set(1 - flFrom.get());
      flU.set(0);
    }
    if (flFrom.get() === displayed() || lap.get() > 0) return;
    flU.set(
      withTiming(1, { duration: (1 - flU.get()) * FL_ROLL * 1000, easing: Easing.linear }, (done) => {
        if (!done) return;
        flFrom.set(1 - flFrom.get());
        flU.set(0);
      }),
    );
  };
  const runLap = () => {
    'worklet';
    const start = lap.get() > 0 && lap.get() < LAP_END ? lap.get() : 0.0001;
    lap.set(start);
    lap.set(
      withTiming(LAP_END, { duration: (LAP_END - start) * LAP * 1000, easing: Easing.linear }, (done) => {
        if (!done) return;
        lap.set(0);
        rollFastest();
      }),
    );
  };
  /** Play the race replay from where its clock is (a fresh start from 0). */
  const startReplay = () => {
    'worklet';
    if (lap.get() > 0) {
      cancelAnimation(lap);
      lap.set(0);
      rollFastest();
    }
    const start = rp.get() > 0 ? rp.get() : 0.0001;
    rp.set(start);
    rp.set(
      withTiming(REPLAY.end, { duration: (REPLAY.end - start) * 1000, easing: Easing.linear }, (done) => {
        if (done) rp.set(0);
      }),
    );
  };
  /** Play the rest of the switch (to = 1) or back to where it started (to = 0) on the switch's own clock. */
  const settle = (to: number) => {
    'worklet';
    switching.set(true);
    const left = Math.abs(to - u.get());
    u.set(
      withTiming(to, { duration: left * SWITCH * 1000, easing: Easing.linear }, (done) => {
        if (!done) return;
        switching.set(false);
        if (to === 1) {
          from.set(1 - from.get());
          u.set(0);
          runLap();
        }
      }),
    );
  };
  const toggle = (byHand: boolean) => {
    'worklet';
    if (u.get() >= 1) {
      from.set(1 - from.get());
      u.set(0);
    }
    const target = 1 - displayed();
    settle(target === 1 - from.get() ? 1 : 0);
    if (byHand) scheduleOnRN(tick);
  };
  /**
   * A touch stops the loop where it is and hands the replay to its own clock. autoPlay only runs the replay
   * (`frameAt`): the switch, the comet lap and the fastest-lap roll are at rest in it, so only `rp` carries over.
   */
  const takeover = () => {
    'worklet';
    touchedAt.set(now.get());
    if (mode.get() !== AUTO_MODE) return;
    mode.set(MANUAL);
    const f = frameAt(time.get());
    press.set(0);
    rp.set(f.rp);
    if (f.rp > 0) startReplay();
  };
  const fadeTo = (session: number) => {
    'worklet';
    fade.set(
      withTiming(0, { duration: REDUCED_FADE, easing: EASE_OUT }, (done) => {
        if (!done) return;
        from.set(session);
        u.set(0);
        flFrom.set(session);
        flU.set(0);
        fade.set(withTiming(1, { duration: REDUCED_FADE, easing: EASE_OUT }));
      }),
    );
  };

  useFrameCallback((info) => {
    'worklet';
    if (!foreground.get()) return;
    const stamp = info.timestamp;
    now.set(stamp);
    const before = last.get();
    last.set(stamp);
    if (still) return;
    if (mode.get() === AUTO_MODE) {
      const dt = before < 0 ? 0 : Math.min(0.1, (stamp - before) / 1000);
      const t = (time.get() + dt) % LOOP;
      time.set(t);
      const f = frameAt(t);
      if (f.from !== from.get()) from.set(f.from);
      if (f.u !== u.get()) u.set(f.u);
      if (f.lap !== lap.get()) lap.set(f.lap);
      if (f.flFrom !== flFrom.get()) flFrom.set(f.flFrom);
      if (f.flU !== flU.get()) flU.set(f.flU);
      if (f.press !== press.get()) press.set(f.press);
      if (f.rp !== rp.get()) rp.set(f.rp);
      return;
    }
    // A replay asked for while Sprint was shown starts once the drive back to Race is done.
    if (pendingReplay.get() && !switching.get() && displayed() === 0 && u.get() === 0) {
      pendingReplay.set(false);
      startReplay();
    }
    if (!autoPlay || dragging.get() || stamp - touchedAt.get() < RESUME_AFTER) return;
    // Two seconds after the last touch: drive back to Race, let the lap and the fastest lap finish, then loop from 0.
    if (switching.get() || lap.get() > 0 || rp.get() > 0 || pendingReplay.get() || (flU.get() > 0 && flU.get() < 1)) return;
    if (displayed() !== 0) {
      toggle(false);
      return;
    }
    const fl = flU.get() >= 1 ? 1 - flFrom.get() : flFrom.get();
    if (fl !== 0) {
      rollFastest();
      return;
    }
    from.set(0);
    u.set(0);
    flFrom.set(0);
    flU.set(0);
    time.set(0);
    mode.set(AUTO_MODE);
  });

  useAnimatedReaction(
    // Read the shared values here, not through displayed(): the reaction subscribes to what its own closure reads.
    () => (u.get() >= 0.5 ? 1 - from.get() : from.get()),
    (next, prev) => {
      if (next !== prev) scheduleOnRN(setShown, next);
    },
  );

  // Lights out: one light impact when the replay was started by hand.
  useAnimatedReaction(
    () => rp.get() >= REPLAY.go,
    (out, before) => {
      if (out && before === false && mode.get() === MANUAL) scheduleOnRN(lightsOut);
    },
  );

  // ---- Gestures ----
  const span = W * DRAG.span;
  const titleTap = Gesture.Tap()
    .maxDuration(800)
    .onBegin(() => {
      takeover();
      if (!still) press.set(withTiming(1, { duration: PRESS.down * 1000, easing: EASE_OUT }));
    })
    .onEnd((_e, success) => {
      if (!success || rp.get() > 0 || pendingReplay.get()) return;
      if (still) {
        fadeTo(1 - displayed());
        scheduleOnRN(tick);
      } else toggle(true);
    })
    .onFinalize(() => {
      touchedAt.set(now.get());
      press.set(withTiming(0, { duration: PRESS.up * 1000, easing: EASE_OUT }));
    });
  const backTap = Gesture.Tap()
    .onBegin(() => {
      takeover();
      if (!still) backPress.set(withTiming(1, { duration: PRESS.down * 1000, easing: EASE_OUT }));
    })
    .onFinalize(() => {
      touchedAt.set(now.get());
      backPress.set(withTiming(0, { duration: PRESS.up * 1000, easing: EASE_OUT }));
    });
  // Swipe left to switch: a swipe to the right stays the system's back gesture.
  const scrub = Gesture.Pan()
    .enabled(!still)
    .activeOffsetX(-DRAG.activeOffset)
    .failOffsetX(DRAG.failOffset)
    .failOffsetY([-DRAG.failOffset, DRAG.failOffset])
    .onBegin(() => {
      takeover();
    })
    .onStart(() => {
      scrubBlocked.set(rp.get() > 0 || pendingReplay.get());
      if (scrubBlocked.get()) return;
      dragging.set(true);
      cancelAnimation(u);
      switching.set(false);
      if (u.get() >= 1) {
        from.set(1 - from.get());
        u.set(0);
      }
      u0.set(u.get());
    })
    .onUpdate((e) => {
      if (scrubBlocked.get()) return;
      const raw = u0.get() + Math.max(0, -e.translationX) / span;
      if (raw <= 1) {
        u.set(raw);
        stretch.set(0);
      } else {
        u.set(1);
        const over = (raw - 1) * span;
        stretch.set(-(over * DRAG.rubber) / (1 + over / DRAG.rubberFalloff));
      }
    })
    .onEnd((e) => {
      if (scrubBlocked.get()) return;
      stretch.set(withSpring(0, SETTLE));
      const flick = -e.velocityX > DRAG.commitVelocity;
      if (u.get() >= DRAG.commitAt || flick) {
        if (u.get() < 1) scheduleOnRN(tick);
        settle(1);
      } else settle(0);
    })
    .onFinalize(() => {
      dragging.set(false);
      touchedAt.set(now.get());
    });

  // Tap the circuit: replay the race (from Race results; from Sprint it drives back to Race first).
  const trackTap = Gesture.Tap()
    .enabled(!still)
    .onBegin(() => {
      takeover();
      if (rp.get() <= 0) trackPress.set(withTiming(1, { duration: PRESS.down * 1000, easing: EASE_OUT }));
    })
    .onEnd((_e, success) => {
      if (!success || rp.get() > 0 || pendingReplay.get()) return;
      // No haptic here: the circuit lifts at once, and the one haptic of a replay is the lights going out.
      if (switching.get() || displayed() !== 0 || u.get() > 0) {
        pendingReplay.set(true);
        if (!switching.get() && displayed() !== 0) toggle(false);
        else if (!switching.get()) settle(from.get() === 0 ? 0 : 1);
        return;
      }
      startReplay();
    })
    .onFinalize(() => {
      touchedAt.set(now.get());
      trackPress.set(withTiming(0, { duration: PRESS.up * 1000, easing: EASE_OUT }));
    });

  const onAccessibleSwitch = () => {
    scheduleOnUI(() => {
      'worklet';
      takeover();
      if (still) fadeTo(1 - displayed());
      else toggle(false);
    });
  };

  // ---- Backdrop, track and comet (Skia) ----
  const track = useImage(TRACK_IMAGE);
  const trackLarge = useImage(TRACK_LARGE);
  const tagFont = useFont(require('./assets/Montserrat-ExtraBold.ttf'), TAG.size * S);
  const tl = layout.track;
  const comet = useMemo(() => {
    // Blender's centreline, scaled from canvas px to screen pt. Index = 3D arc length (constant speed);
    // `cum` maps it to the 2D length the path trims use.
    const k = (tl.w * S) / LINE_CANVAS.w;
    const xs: number[] = [];
    const ys: number[] = [];
    for (let i = 0; i <= LINE_POINTS; i++) {
      const j = (i % LINE_POINTS) * 2;
      xs.push(LINE_XY[j] * k);
      ys.push(LINE_XY[j + 1] * k);
    }
    const path = Skia.Path.Make();
    path.moveTo(xs[0], ys[0]);
    const cum = [0];
    for (let i = 1; i <= LINE_POINTS; i++) {
      path.lineTo(xs[i], ys[i]);
      cum.push(cum[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]));
    }
    // The cars' path: the same line four times over, so a trail is one trim and never wraps.
    const laps = Skia.Path.Make();
    laps.moveTo(xs[0], ys[0]);
    for (let lapN = 0; lapN < LAPS_DRAWN; lapN++) {
      for (let i = 1; i <= LINE_POINTS; i++) laps.lineTo(xs[i], ys[i]);
    }
    return { path, laps, xs, ys, cum, total: cum[LINE_POINTS] };
  }, [S, tl.w]);
  const { xs, ys, cum, total } = comet;
  const N = LINE_POINTS;
  const head = (place: number) => {
    'worklet';
    const f = place * N;
    const i = Math.min(N - 1, Math.floor(f));
    const k = f - i;
    return { x: xs[i] + (xs[i + 1] - xs[i]) * k, y: ys[i] + (ys[i + 1] - ys[i]) * k };
  };
  const trim = (place: number) => {
    'worklet';
    const f = place * N;
    const i = Math.min(N - 1, Math.floor(f));
    return (cum[i] + (cum[i + 1] - cum[i]) * (f - i)) / total;
  };
  const cometOpacity = useDerivedValue(() => cometAlpha(lap.get()));
  const tailEnd = useDerivedValue(() => trim(lapPlace(lap.get())));
  const tail0 = useDerivedValue(() => trim(lapPlace(lap.get() - TAIL[0].lag)));
  const tail1 = useDerivedValue(() => trim(lapPlace(lap.get() - TAIL[1].lag)));
  const tail2 = useDerivedValue(() => trim(lapPlace(lap.get() - TAIL[2].lag)));
  const tail3 = useDerivedValue(() => trim(lapPlace(lap.get() - TAIL[3].lag)));
  const tail4 = useDerivedValue(() => trim(lapPlace(lap.get() - TAIL[4].lag)));
  const glowStart = useDerivedValue(() => trim(lapPlace(lap.get() - GLOW_LAG)));
  const headX = useDerivedValue(() => head(lapPlace(lap.get())).x);
  const headY = useDerivedValue(() => head(lapPlace(lap.get())).y);
  const flashR = useDerivedValue(() => (FLASH.from + (FLASH.to - FLASH.from) * easeOut(lineFlash(lap.get()))) * S);
  const flashOpacity = useDerivedValue(() => {
    const p = lineFlash(lap.get());
    return p <= 0 ? 0 : 1 - p;
  });

  // ---- The race replay: the circuit flies out and becomes a live map ----
  const half = { x: (tl.w * S) / 2, y: (tl.h * S) / 2 };
  const flight = { x0: X(MAP.from.x), y0: Y(MAP.from.y), x1: X(MAP.to.x), y1: Y(MAP.to.y) };
  const mapM = useDerivedValue(() => mapOpen(rp.get()));
  const mapScale = useDerivedValue(() => (1 + (MAP.scale - 1) * mapM.get()) * (1 - (1 - MAP.press) * trackPress.get()));
  const mapTransform = useDerivedValue(() => {
    const m = mapM.get();
    const tilt = Math.sin(Math.PI * clamp01(m)) * MAP.tilt * (Math.PI / 180);
    return [
      { translateX: lerp(flight.x0, flight.x1, m) },
      { translateY: lerp(flight.y0, flight.y1, m) },
      { perspective: MAP.perspective },
      { rotateX: tilt },
      { scale: mapScale.get() },
      { translateX: -half.x },
      { translateY: -half.y },
    ];
  });
  const smallOpacity = useDerivedValue(() => 1 - clamp01(((MAP.scale - 1) * mapM.get()) / MAP.hiResFrom));
  const largeOpacity = useDerivedValue(() => (mapM.get() > 0.0001 ? 1 : 0));
  const finishR = useDerivedValue(() => {
    const p = finishFlash(rp.get());
    return ((FINISH_FLASH.from + (FINISH_FLASH.to - FINISH_FLASH.from) * easeOut(p)) * S) / mapScale.get();
  });
  const finishStroke = useDerivedValue(() => (FINISH_FLASH.stroke * S) / mapScale.get());
  const finishOpacity = useDerivedValue(() => {
    const p = finishFlash(rp.get());
    return p <= 0 ? 0 : 1 - p;
  });
  const housing = useDerivedValue(() => lightsHousing(rp.get()));
  const geo = useMemo(() => ({ xs, ys, cum, total, half, flight }), [xs, ys, cum, total, half.x, half.y, flight.x0, flight.y0, flight.x1, flight.y1]); // eslint-disable-line react-hooks/exhaustive-deps

  const bgStops = useMemo(() => {
    const centre = backdrop.centre;
    const positions = centre.map(([y]) => clamp01(Y(y) / H));
    const cols = centre.map(([, v]) => `rgb(${v},${v},${v})`);
    positions.push(1);
    cols.push(colors.base);
    const edgePos = backdrop.edge.map(([y]) => clamp01(Y(y) / H));
    const edgeCols = backdrop.edge.map(([, v]) => `rgba(255,255,255,${Math.min(1, (v * 1.2) / 255).toFixed(4)})`);
    edgePos.push(1);
    edgeCols.push('rgba(255,255,255,0)');
    const across = backdrop.edgeProfile.map((a) => `rgba(255,255,255,${a})`);
    return { positions, cols, edgePos, edgeCols, across };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [H, insets.top, S]);

  // ---- Animated styles ----
  const contentStyle = useAnimatedStyle(() => ({ opacity: fade.get() }));
  const headerStyle = useAnimatedStyle(() => ({ opacity: 1 - HEADER_DIM * headerDim(rp.get()) }));
  const backStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS.scale) * backPress.get() }] }));
  const titleStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS.scale) * press.get() }] }));
  const delta = words[1] - words[0];
  const wordShift = useAnimatedStyle(() => ({ transform: [{ translateX: (-delta / 2) * towards(from.get(), wordProgress(u.get())) }] }));
  const restShift = useAnimatedStyle(() => ({ transform: [{ translateX: (delta / 2) * towards(from.get(), wordProgress(u.get())) }] }));
  const roll = (size: number) => {
    'worklet';
    return ROW.roll * size * S;
  };
  const raceWord = useAnimatedStyle(() => {
    const q = towards(from.get(), wordProgress(u.get()));
    return { opacity: 1 - q, transform: [{ translateY: -roll(layout.title.size) * q }] };
  });
  const sprintWord = useAnimatedStyle(() => {
    const q = towards(from.get(), wordProgress(u.get()));
    return { opacity: q, transform: [{ translateY: roll(layout.title.size) * (1 - q) }] };
  });
  const chevronUp = useAnimatedStyle(() => ({ transform: [{ translateY: -PRESS.chevronOpen * S * press.get() }] }));
  const chevronDown = useAnimatedStyle(() => ({ transform: [{ translateY: PRESS.chevronOpen * S * press.get() }] }));
  const flRace = useAnimatedStyle(() => {
    const q = towards(flFrom.get(), easeOut(flU.get()));
    return { opacity: 1 - q, transform: [{ translateY: -roll(layout.stats.valueSize) * q }] };
  });
  const flSprint = useAnimatedStyle(() => {
    const q = towards(flFrom.get(), easeOut(flU.get()));
    return { opacity: q, transform: [{ translateY: roll(layout.stats.valueSize) * (1 - q) }] };
  });

  // ---- Small text helpers ----
  const font = (family: string, size: number, color: string = colors.ink): TextStyle => ({ fontFamily: family, fontSize: size * S, color });
  const label = (text: string, x: number, baseline: number, family: string, size: number, color?: string) => (
    <Text allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(family, size, color), { left: X(x), top: textTop(baseline, size) }]}>
      {text}
    </Text>
  );
  const BOX = 120 * S;

  const ti = layout.title;
  const titleBox = { left: X(ti.x - 12), top: Y(ti.baseline) - ASCENT * ti.size * S - 8 * S };
  const titleH = Math.max(space.hit * S, LINE * ti.size * S + 16 * S);
  const tb = layout.back;
  const st = layout.stats;
  const rw = layout.rows;

  return (
    <View style={styles.fill}>
      {/* Backdrop, track and the comet that laps it. */}
      <Canvas style={{ position: 'absolute', left: 0, top: 0, width: W, height: H }}>
        <Rect x={0} y={0} width={W} height={H}>
          <SkiaGradient start={vec(0, 0)} end={vec(0, H)} positions={bgStops.positions} colors={bgStops.cols} />
        </Rect>
        <Rect x={0} y={0} width={W} height={H}>
          <Blend mode="dstIn">
            <SkiaGradient start={vec(0, 0)} end={vec(W, 0)} colors={bgStops.across} />
            <SkiaGradient start={vec(0, 0)} end={vec(0, H)} positions={bgStops.edgePos} colors={bgStops.edgeCols} />
          </Blend>
        </Rect>
      </Canvas>

      <GestureDetector gesture={scrub}>
        <Animated.View style={[styles.fill, contentStyle]}>
          {/* Back */}
          <GestureDetector gesture={backTap}>
            <Animated.View
              accessible
              accessibilityRole="button"
              accessibilityLabel="Back"
              style={[
                styles.back,
                { left: X(tb.cx - tb.d / 2), top: Y(tb.cy - tb.d / 2), width: tb.d * S, height: tb.d * S, borderRadius: (tb.d / 2) * S, borderWidth: tb.ring * S },
                backStyle,
              ]}
            >
              <BackArrow s={S} size={tb.d - 2 * tb.ring} />
            </Animated.View>
          </GestureDetector>

          {/* Title: "Race Results ⇅" — the switch */}
          <GestureDetector gesture={titleTap}>
            <Animated.View
              accessible
              accessibilityRole="button"
              accessibilityLabel={`${sessions[shown].word} results`}
              accessibilityHint={`Switches to ${sessions[1 - shown].word} results`}
              onAccessibilityTap={onAccessibleSwitch}
              style={[styles.abs, { left: titleBox.left, top: titleBox.top, width: X(layout.sort.x + layout.sort.w + 12) - titleBox.left, height: titleH }, titleStyle]}
            >
              <Animated.View style={[styles.abs, { left: X(ti.x) - titleBox.left, top: 0, width: Math.max(words[0], words[1]) + 4 * S, height: LINE * ti.size * S + 16 * S, overflow: 'hidden' }, wordShift]}>
                <Animated.Text allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.extrabold, ti.size), { left: 0, top: 8 * S }, raceWord]}>
                  {sessions[0].word}
                </Animated.Text>
                <Animated.Text allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.extrabold, ti.size), { left: 0, top: 8 * S }, sprintWord]}>
                  {sessions[1].word}
                </Animated.Text>
              </Animated.View>
              <Animated.View style={[styles.abs, { left: 0, top: 0, right: 0, bottom: 0 }, restShift]} pointerEvents="none">
                <Text allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.extrabold, ti.size), { left: X(ti.x) - titleBox.left + words[0], top: 8 * S }]}>
                  {copy.results}
                </Text>
                <Animated.View style={[styles.abs, { left: X(layout.sort.x) - titleBox.left, top: Y(layout.sort.y) - titleBox.top }, chevronUp]}>
                  <Chevron s={S} up stroke={layout.sort.stroke} />
                </Animated.View>
                <Animated.View style={[styles.abs, { left: X(layout.sort.x) - titleBox.left, top: Y(layout.sort.y + layout.sort.h - 4.95) - titleBox.top }, chevronDown]}>
                  <Chevron s={S} up={false} stroke={layout.sort.stroke} />
                </Animated.View>
              </Animated.View>
            </Animated.View>
          </GestureDetector>
          {/* Hidden: the two session words, measured once so the title can re-centre. */}
          <View style={styles.measure} pointerEvents="none">
            {sessions.map((s, i) => (
              <Text
                key={s.word}
                allowFontScaling={false}
                style={font(FONT.extrabold, ti.size)}
                onLayout={(e) => {
                  const w = e.nativeEvent.layout.width;
                  setWords((prev) => (prev[i] === w ? prev : (i === 0 ? [w, prev[1]] : [prev[0], w])));
                }}
              >
                {s.word}
              </Text>
            ))}
          </View>

          {/* The header copy dims while the circuit is out over it. */}
          <Animated.View style={[styles.fill, headerStyle]} pointerEvents="box-none">
          {label(copy.round, layout.round.x, layout.round.baseline, FONT.semibold, layout.round.size, colors.muted)}
          {label(copy.event, layout.event.x, layout.event.baseline, FONT.extrabold, layout.event.size)}

          {/* Podium livery: each slot repaints with a wipe */}
          {[0, 1, 2].flatMap((k) =>
            [0, 1].map((j) => <PodiumUnit key={`${k}${j}`} slot={k} session={j} hidden={j !== shown} from={from} u={u} S={S} X={X} Y={Y} textTop={textTop} font={font} />),
          )}

          {/* Stats */}
          <View style={[styles.abs, { left: X(icons.stopwatch.cx - 12), top: Y(icons.stopwatch.cy - 13.6) }]}>
            <Stopwatch s={S} />
          </View>
          <View style={[styles.abs, { left: X(st.lap.x), top: textTop(st.lap.baseline, st.valueSize), width: X(REF_W - st.lap.x - space.rowInset), height: LINE * st.valueSize * S, overflow: 'hidden' }]}>
            <Animated.Text accessibilityElementsHidden={shown !== 0} importantForAccessibility={shown !== 0 ? 'no' : 'auto'} allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.semibold, st.valueSize), { left: 0, top: 0 }, flRace]}>
              {sessions[0].fastestLap}
            </Animated.Text>
            <Animated.Text accessibilityElementsHidden={shown !== 1} importantForAccessibility={shown !== 1 ? 'no' : 'auto'} allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.semibold, st.valueSize), { left: 0, top: 0 }, flSprint]}>
              {sessions[1].fastestLap}
            </Animated.Text>
          </View>
          {label(copy.lapLabel, st.lap.labelX, st.lap.baseline + space.labelGap, FONT.medium, st.labelSize)}
          <View style={[styles.abs, { left: X(icons.award.cx - 12), top: Y(icons.award.cy - 9.8) }]}>
            <Award s={S} />
          </View>
          {label(copy.dayValue, st.day.x, st.day.baseline, FONT.semibold, st.valueSize)}
          {label(copy.dayLabel, st.day.labelX, st.day.baseline + space.labelGap, FONT.medium, st.labelSize)}
          <View style={[styles.abs, { left: X(icons.wheel.cx - 12), top: Y(icons.wheel.cy - 12) }]}>
            <Wheel s={S} />
          </View>
          {label(copy.pitValue, st.pit.x, st.pit.baseline, FONT.semibold, st.valueSize)}
          {label(copy.pitLabel, st.pit.labelX, st.pit.baseline + space.labelGap, FONT.medium, st.labelSize)}
          </Animated.View>

          <View style={[styles.rule, { left: X(space.headerRuleInset), width: X(REF_W - 2 * space.headerRuleInset), top: Y(layout.headerRule.y) - StyleSheet.hairlineWidth / 2 }]} />

          {/* Standings: positions and points stay in their slots; drivers drive between them. */}
          {SLOTS.map((k) => (
            <View key={`slot${k}`} pointerEvents="none" style={StyleSheet.absoluteFill}>
              {label(`0${k + 1}`, rw.numberX, rw.numberBaseline + k * space.rowPitch, FONT.bold, rw.numberSize)}
              <ReplayPoints slot={k} rp={rp} S={S}>
                <SlotPoints slot={k} from={from} u={u} S={S} left={X(rw.pointsRight) - BOX} width={BOX} top={textTop(rw.pointsBaseline + k * space.rowPitch, rw.pointsSize)} font={font} />
                <Text allowFontScaling={false} style={[styles.abs, styles.right, font(FONT.semibold, rw.ptsSize), { left: X(rw.ptsRight) - BOX, width: BOX, top: textTop(rw.ptsBaseline + k * space.rowPitch, rw.ptsSize) }]}>
                  {copy.pts}
                </Text>
              </ReplayPoints>
              <View style={[styles.rule, { left: X(space.rowInset), width: X(REF_W - 2 * space.rowInset), top: Y(rw.firstRule + k * space.rowPitch) - StyleSheet.hairlineWidth / 2 }]} />
            </View>
          ))}
          {/* Mounted in the shown session's order, so a screen reader reads the standings top to bottom. */}
          {rowOrder(shown).map((i) => (
            <DriverRow key={`driver${i}`} index={i} shown={shown} from={from} u={u} rp={rp} stretch={stretch} S={S} X={X} Y={Y} font={font} />
          ))}

          {/* The circuit is a button: tap it to replay the race. */}
          <GestureDetector gesture={trackTap}>
            <View
              accessible={!still}
              accessibilityElementsHidden={still}
              accessibilityRole="button"
              accessibilityLabel="Replay the race"
              style={[styles.abs, { left: X(tl.x), top: Y(tl.y), width: tl.w * S, height: tl.h * S }]}
            />
          </GestureDetector>
        </Animated.View>
      </GestureDetector>

      {/* The circuit (with the comet and, during a replay, the race), above the copy it flies over. */}
      <View pointerEvents="none" style={[styles.abs, { left: 0, top: 0, width: W, height: Y(REPLAY_CANVAS_BOTTOM) }]}>
        <Canvas style={{ width: W, height: Y(REPLAY_CANVAS_BOTTOM) }}>
          <Group transform={mapTransform}>
            {trackLarge ? (
              <SkiaImage image={trackLarge} x={0} y={0} width={tl.w * S} height={tl.h * S} fit="fill" opacity={largeOpacity} sampling={{ filter: FilterMode.Linear, mipmap: MipmapMode.Linear }} />
            ) : null}
            {track ? <SkiaImage image={track} x={0} y={0} width={tl.w * S} height={tl.h * S} fit="fill" opacity={smallOpacity} /> : null}
            <Group opacity={cometOpacity}>
              <Path path={comet.path} start={glowStart} end={tailEnd} style="stroke" strokeWidth={COMET.glowStroke * S} strokeCap="round" color={colors.cometGlow} opacity={0.4}>
                <BlurMask blur={COMET.glowStrokeBlur * S} style="normal" />
              </Path>
              {[tail4, tail3, tail2, tail1, tail0].map((start, i) => {
                const t = TAIL[TAIL.length - 1 - i];
                return <Path key={i} path={comet.path} start={start} end={tailEnd} style="stroke" strokeWidth={t.width * S} strokeCap="round" color={colors.comet} opacity={t.alpha} />;
              })}
              <Circle cx={headX} cy={headY} r={COMET.glow * S} color={colors.cometGlow} opacity={0.65}>
                <BlurMask blur={COMET.glowBlur * S} style="normal" />
              </Circle>
              <Circle cx={headX} cy={headY} r={COMET.head * S} color={colors.comet} />
            </Group>
            <Group opacity={flashOpacity}>
              <Circle cx={xs[0]} cy={ys[0]} r={flashR} style="stroke" strokeWidth={FLASH.stroke * S} color={colors.cometGlow} />
            </Group>
            {/* The race: trails under every car, then the cars. */}
            {drivers.map((_, i) => (
              <CarTrail key={`trail${i}`} index={i} rp={rp} laps={comet.laps} geo={geo} scale={mapScale} S={S} />
            ))}
            {drivers.map((_, i) => (
              <CarDot key={`car${i}`} index={i} rp={rp} geo={geo} scale={mapScale} S={S} />
            ))}
            <Group opacity={finishOpacity}>
              <Circle cx={xs[0]} cy={ys[0]} r={finishR} style="stroke" strokeWidth={finishStroke} color={colors.cometGlow} />
            </Group>
          </Group>
          {/* Overtake tags, in screen space (the circuit is flat and still while they show). */}
          {tagFont
            ? drivers.map((_, i) => <CarTag key={`tag${i}`} index={i} rp={rp} geo={geo} font={tagFont} S={S} />)
            : null}
          {/* Start lights */}
          <Group opacity={housing}>
            <RoundedRect
              x={X(LIGHTS.cx - LIGHTS.pill.w / 2)}
              y={Y(LIGHTS.cy - LIGHTS.pill.h / 2)}
              width={LIGHTS.pill.w * S}
              height={LIGHTS.pill.h * S}
              r={(LIGHTS.pill.h / 2) * S}
              color={colors.plate}
            />
            <RoundedRect
              x={X(LIGHTS.cx - LIGHTS.pill.w / 2)}
              y={Y(LIGHTS.cy - LIGHTS.pill.h / 2)}
              width={LIGHTS.pill.w * S}
              height={LIGHTS.pill.h * S}
              r={(LIGHTS.pill.h / 2) * S}
              style="stroke"
              strokeWidth={StyleSheet.hairlineWidth}
              color={colors.plateEdge}
            />
            {LIGHT_SLOTS.map((k) => (
              <StartLight key={k} k={k} rp={rp} cx={X(LIGHTS.cx + (k - (LIGHTS.count - 1) / 2) * LIGHTS.pitch)} cy={Y(LIGHTS.cy)} S={S} />
            ))}
          </Group>
        </Canvas>
      </View>
    </View>
  );
}

type Geo = {
  xs: number[];
  ys: number[];
  cum: number[];
  total: number;
  half: { x: number; y: number };
  flight: { x0: number; y0: number; x1: number; y1: number };
};

/** Where a car is on the circuit (local pt of the rest-size track), with its sideways offset. */
function carPoint(geo: Geo, laps: number, side: number) {
  'worklet';
  const N = LINE_POINTS;
  const f = (((laps % 1) + 1) % 1) * N;
  const i = Math.min(N - 1, Math.floor(f));
  const k = f - i;
  const x = geo.xs[i] + (geo.xs[i + 1] - geo.xs[i]) * k;
  const y = geo.ys[i] + (geo.ys[i + 1] - geo.ys[i]) * k;
  const tx = geo.xs[i + 1] - geo.xs[i];
  const ty = geo.ys[i + 1] - geo.ys[i];
  const len = Math.hypot(tx, ty) || 1;
  return { x: x - (ty / len) * side, y: y + (tx / len) * side };
}

/** A trim position on the four-lap path for a distance in laps (+1 so the grid, behind the line, is on it). */
function lapTrim(geo: Geo, laps: number) {
  'worklet';
  const x = Math.max(0, Math.min(LAPS_DRAWN - 0.0001, laps + 1));
  const whole = Math.floor(x);
  const f = (x - whole) * LINE_POINTS;
  const i = Math.min(LINE_POINTS - 1, Math.floor(f));
  const along = geo.cum[i] + (geo.cum[i + 1] - geo.cum[i]) * (f - i);
  return (whole * geo.total + along) / (LAPS_DRAWN * geo.total);
}

function CarTrail({ index, rp, laps, geo, scale, S }: {
  index: number; rp: SharedValue<number>; laps: SkPath; geo: Geo; scale: SharedValue<number>; S: number;
}) {
  const color = carShade[index].fill;
  const alpha = useDerivedValue(() => carAlpha(rp.get()));
  const end = useDerivedValue(() => lapTrim(geo, carLaps(index, rp.get())));
  const lag = (part: number) => {
    'worklet';
    const r = rp.get();
    return lapTrim(geo, carLaps(index, r) - RACE.trailLag * part * leaderPace(r));
  };
  const s0 = useDerivedValue(() => lag(TRAIL[0].part));
  const s1 = useDerivedValue(() => lag(TRAIL[1].part));
  const s2 = useDerivedValue(() => lag(TRAIL[2].part));
  const w0 = useDerivedValue(() => (TRAIL[0].width * S) / scale.get());
  const w1 = useDerivedValue(() => (TRAIL[1].width * S) / scale.get());
  const w2 = useDerivedValue(() => (TRAIL[2].width * S) / scale.get());
  return (
    <Group opacity={alpha}>
      <Path path={laps} start={s0} end={end} style="stroke" strokeWidth={w0} strokeCap="round" color={color} opacity={TRAIL[0].alpha} />
      <Path path={laps} start={s1} end={end} style="stroke" strokeWidth={w1} strokeCap="round" color={color} opacity={TRAIL[1].alpha} />
      <Path path={laps} start={s2} end={end} style="stroke" strokeWidth={w2} strokeCap="round" color={color} opacity={TRAIL[2].alpha} />
    </Group>
  );
}

function CarDot({ index, rp, geo, scale, S }: { index: number; rp: SharedValue<number>; geo: Geo; scale: SharedValue<number>; S: number }) {
  const color = carShade[index].fill;
  const alpha = useDerivedValue(() => carAlpha(rp.get()));
  const at = useDerivedValue(() => {
    const r = rp.get();
    return carPoint(geo, carLaps(index, r), carLateral(index, r) * S);
  });
  const cx = useDerivedValue(() => at.get().x);
  const cy = useDerivedValue(() => at.get().y);
  const glow = useDerivedValue(() => (CAR.glow * S) / scale.get());
  const blur = useDerivedValue(() => (CAR.glowBlur * S) / scale.get());
  const core = useDerivedValue(() => (CAR.core * S) / scale.get());
  const centre = useDerivedValue(() => (CAR.centre * S) / scale.get());
  return (
    <Group opacity={alpha}>
      <Circle cx={cx} cy={cy} r={glow} color={color} opacity={CAR.glowAlpha}>
        <BlurMask blur={blur} style="normal" />
      </Circle>
      <Circle cx={cx} cy={cy} r={core} color={color} />
      <Circle cx={cx} cy={cy} r={centre} color={colors.ink} />
    </Group>
  );
}

function CarTag({ index, rp, geo, font, S }: { index: number; rp: SharedValue<number>; geo: Geo; font: SkFont; S: number }) {
  const driver = drivers[index];
  const w = font.getTextWidth(driver.code) + 2 * TAG.padX * S;
  const h = TAG.h * S;
  const alpha = useDerivedValue(() => tagAlpha(index, rp.get()) * carAlpha(rp.get()));
  // The circuit is fully open (scale MAP.scale, no tilt) whenever a tag shows.
  const at = useDerivedValue(() => {
    const r = rp.get();
    const p = carPoint(geo, carLaps(index, r), carLateral(index, r) * S);
    return {
      x: geo.flight.x1 + (p.x - geo.half.x) * MAP.scale,
      y: geo.flight.y1 + (p.y - geo.half.y) * MAP.scale,
    };
  });
  const rx = useDerivedValue(() => at.get().x - w / 2);
  // The tag rises into place as it appears.
  const lift = useDerivedValue(() => (TAG.above + TAG.rise * (1 - alpha.get())) * S);
  const ry = useDerivedValue(() => at.get().y - lift.get() - h);
  const tx = useDerivedValue(() => at.get().x - w / 2 + TAG.padX * S);
  const ty = useDerivedValue(() => at.get().y - lift.get() - h / 2 + TAG.size * S * 0.36);
  return (
    <Group opacity={alpha}>
      <RoundedRect x={rx} y={ry} width={w} height={h} r={TAG.radius * S} color={carShade[index].fill} />
      <SkiaText x={tx} y={ty} text={driver.code} font={font} color={carShade[index].ink} />
    </Group>
  );
}

function StartLight({ k, rp, cx, cy, S }: { k: number; rp: SharedValue<number>; cx: number; cy: number; S: number }) {
  const on = useDerivedValue(() => lightOn(k, rp.get()));
  const glow = useDerivedValue(() => on.get() * LIGHTS.glow.alpha);
  return (
    <Group>
      <Circle cx={cx} cy={cy} r={LIGHTS.r * S} color={colors.lightOff} />
      <Circle cx={cx} cy={cy} r={LIGHTS.r * LIGHTS.glow.radius * S} color={colors.award} opacity={glow}>
        <BlurMask blur={LIGHTS.glow.blur * S} style="normal" />
      </Circle>
      <Circle cx={cx} cy={cy} r={LIGHTS.r * S} color={colors.award} opacity={on} />
    </Group>
  );
}

type Font = (family: string, size: number, color?: string) => TextStyle;

function PodiumUnit({ slot, session, hidden, from, u, S, X, Y, textTop, font }: {
  slot: number; session: number; hidden: boolean; from: SharedValue<number>; u: SharedValue<number>; S: number;
  X: (x: number) => number; Y: (y: number) => number; textTop: (b: number, s: number) => number; font: Font;
}) {
  const pod = layout.podium;
  const left = X(pod.markRight[slot] - pod.markW);
  const unitW = (pod.codeX[slot] - (pod.markRight[slot] - pod.markW) + PODIUM_CODE_ROOM) * S;
  const top = Y(pod.markY) - PODIUM_PAD * S;
  const height = (pod.markH + 2 * PODIUM_PAD) * S;
  // A wipe: the window slides one way while its content slides back, so the livery repaints in place.
  const shift = useDerivedValue(() => {
    const q = towards(from.get(), podiumProgress(u.get(), slot));
    return session === 0 ? q * unitW : -(1 - q) * unitW;
  });
  const windowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: shift.get() }] }));
  const contentStyle = useAnimatedStyle(() => ({ transform: [{ translateX: -shift.get() }] }));
  const driver = drivers[sessions[session].order[slot]];
  return (
    <Animated.View
      pointerEvents="none"
      accessibilityElementsHidden={hidden}
      importantForAccessibility={hidden ? 'no-hide-descendants' : 'auto'}
      style={[styles.abs, { left, top, width: unitW, height, overflow: 'hidden' }, windowStyle]}
    >
      <Animated.View style={[styles.abs, { left: 0, top: 0, width: unitW, height }, contentStyle]}>
        <View style={[styles.abs, { left: 0, top: PODIUM_PAD * S }]}>
          <LiveryMark s={S} kind={driver.mark} color={teams[driver.team].mark} id={`mark${slot}${session}`} />
        </View>
        <Text allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.semibold, pod.size), { left: X(pod.codeX[slot]) - left, top: textTop(pod.baseline, pod.size) - top }]}>
          {driver.code}
        </Text>
      </Animated.View>
    </Animated.View>
  );
}

function ReplayPoints({ slot, rp, S, children }: { slot: number; rp: SharedValue<number>; S: number; children: ReactNode }) {
  const dist = ROW.roll * layout.rows.pointsSize * S;
  // Hidden while the race runs, rolled up into place as the slot's car takes the flag.
  const style = useAnimatedStyle(() => {
    const q = replayPoints(slot, rp.get());
    return { opacity: q.alpha, transform: [{ translateY: dist * q.rise }] };
  });
  return <Animated.View style={[styles.fill, style]}>{children}</Animated.View>;
}

function SlotPoints({ slot, from, u, S, left, width, top, font }: {
  slot: number; from: SharedValue<number>; u: SharedValue<number>; S: number; left: number; width: number; top: number; font: Font;
}) {
  const rw = layout.rows;
  const size = rw.pointsSize;
  const h = LINE * size * S;
  const dist = ROW.roll * size * S;
  // Fewer points roll downwards (the new value comes from above), more roll upwards.
  const dir = sessions[1].points[slot] < sessions[0].points[slot] ? 1 : -1;
  const race = useAnimatedStyle(() => {
    const q = towards(from.get(), pointsProgress(u.get(), slot));
    return { opacity: 1 - q, transform: [{ translateY: dir * dist * q }] };
  });
  const sprint = useAnimatedStyle(() => {
    const q = towards(from.get(), pointsProgress(u.get(), slot));
    return { opacity: q, transform: [{ translateY: -dir * dist * (1 - q) }] };
  });
  return (
    <View style={[styles.abs, { left, top, width, height: h, overflow: 'hidden' }]}>
      <Animated.Text allowFontScaling={false} style={[styles.abs, styles.right, font(FONT.extrabold, size), { left: 0, top: 0, width }, race]}>
        {sessions[0].points[slot]}
      </Animated.Text>
      <Animated.Text allowFontScaling={false} style={[styles.abs, styles.right, font(FONT.extrabold, size), { left: 0, top: 0, width }, sprint]}>
        {sessions[1].points[slot]}
      </Animated.Text>
    </View>
  );
}

function badgeKind(delta: number): BadgeKind {
  return delta > 0 ? 'up' : delta < 0 ? 'down' : 'same';
}

/** Where a row is heading, from either the session switch or the race replay. */
function rowMotion(index: number, from: number, u: number, rp: number) {
  'worklet';
  if (rp > 0) {
    const q = replayRow(index, rp);
    return { y: q.y, speed: q.speed * space.rowPitch, bump: q.bump, rising: q.dir < 0, moving: q.dir !== 0 };
  }
  const a = SLOT_OF[from][index];
  const b = SLOT_OF[1 - from][index];
  const e = rowProgress(u, b);
  const moving = a !== b;
  return {
    y: a + (b - a) * e,
    speed: rowSpeed(u, b) * (b - a) * space.rowPitch, // pt/s, negative = up
    bump: moving ? Math.sin(Math.PI * clamp01(e)) : 0,
    rising: b < a,
    moving,
  };
}

/** Driver indices top to bottom in a session. */
function rowOrder(shown: number) {
  return drivers.map((_, i) => i).sort((a, b) => SLOT_OF[shown][a] - SLOT_OF[shown][b]);
}

function DriverRow({ index, shown, from, u, rp, stretch, S, X, Y, font }: {
  index: number; shown: number; from: SharedValue<number>; u: SharedValue<number>; rp: SharedValue<number>; stretch: SharedValue<number>; S: number;
  X: (x: number) => number; Y: (y: number) => number; font: Font;
}) {
  const rw = layout.rows;
  const driver = drivers[index];
  const team = teams[driver.team];
  const pitch = space.rowPitch * S;
  const rel = (baseline: number, size: number) => (baseline - ROW_ANCHOR) * S - ASCENT * size * S;
  const mid = (ROW_MID - ROW_ANCHOR) * S;
  const trailH = rw.trailMax * S;
  const plateBox = { left: X(rw.plate.x), top: rw.plate.inset * S, width: rw.plate.w * S, height: pitch - 2 * rw.plate.inset * S, borderRadius: rw.plate.radius * S };

  const block = useAnimatedStyle(() => {
    const m = rowMotion(index, from.get(), u.get(), rp.get());
    const { moving, rising, bump, speed } = m;
    const y = m.y * pitch;
    const arc = moving ? bump * (rising ? ROW.laneRise : ROW.laneFall) * S : 0;
    const lean = Math.max(-1, Math.min(1, speed / ROW.leanSpeed)) * ROW.leanMax;
    const dim = !rising ? ROW.dimFall * bump : 0;
    const scale = rising ? 1 + ROW.liftRise * bump : 1 - ROW.sinkFall * bump;
    return {
      opacity: 1 - dim,
      zIndex: rising ? 3 : moving ? 1 : 2,
      transform: [{ translateX: arc + stretch.get() }, { translateY: y }, { rotate: `${lean}deg` }, { scale }],
    };
  });
  // An overtaker rides on a plate the colour of the list: invisible at rest, it hides the driver it passes
  // and lightens a little with the lift, like a card raised off the table.
  const plate = useAnimatedStyle(() => ({ opacity: rowMotion(index, from.get(), u.get(), rp.get()).rising ? 1 : 0 }));
  const plateLight = useAnimatedStyle(() => {
    const m = rowMotion(index, from.get(), u.get(), rp.get());
    return { opacity: m.rising ? m.bump : 0 };
  });
  const trail = useAnimatedStyle(() => {
    const speed = rowMotion(index, from.get(), u.get(), rp.get()).speed;
    const len = Math.min(rw.trailMax, Math.abs(speed) * ROW.trailPerSpeed);
    const alpha = ROW.trailAlpha * clamp01(Math.abs(speed) / ROW.trailAlphaSpeed);
    // Moving up, the trail hangs below the driver; moving down, it flips above.
    const k = (speed < 0 ? 1 : -1) * Math.max(0.001, len / rw.trailMax);
    return { opacity: alpha, transform: [{ scaleY: k }] };
  });
  // The label follows the replay: this row re-renders only when its own place changes or the flag falls
  // (a few times per replay), never per frame and never the whole screen.
  const [racing, setRacing] = useState(-1);
  useAnimatedReaction(
    () => replayPlace(index, rp.get()),
    (next, prev) => {
      if (next !== prev) scheduleOnRN(setRacing, next);
    },
  );
  const slot = racing < 0 ? SLOT_OF[shown][index] : racing % REPLAY_FLAGGED;
  const points = sessions[shown].points[slot];
  // Before the flag the points are hidden on screen, so the label gives the running place only.
  const flagged = racing < 0 || racing >= REPLAY_FLAGGED;
  const label = `${slot + 1}. ${driver.name}, ${team.name}${flagged ? `, ${points} points` : ', racing'}`;

  return (
    <Animated.View
      accessible
      accessibilityLabel={label}
      style={[styles.abs, { left: 0, top: Y(ROW_ANCHOR), width: X(REF_W), height: pitch, transformOrigin: [X(rw.nameX), mid, 0] }, block]}
    >
      <Animated.View style={[styles.plate, plateBox, plate]} />
      <Animated.View style={[styles.plate, styles.plateLit, plateBox, plateLight]} />
      <Animated.View style={[styles.abs, { left: X(rw.trailX), top: mid, width: rw.trailW * S, height: trailH, transformOrigin: 'top' }, trail]}>
        <LinearGradient colors={[team.text, `${team.text}00`]} style={StyleSheet.absoluteFill} />
      </Animated.View>
      <Text allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.bold, rw.nameSize), { left: X(rw.nameX), top: rel(rw.nameBaseline, rw.nameSize) }]}>
        {driver.name}
      </Text>
      <Text allowFontScaling={false} numberOfLines={1} style={[styles.abs, font(FONT.semibold, rw.teamSize, team.text), { left: X(rw.teamX), top: rel(rw.teamBaseline, rw.teamSize) }]}>
        {team.name}
      </Text>
      {[0, 1].map((j) => (
        <Badge key={j} index={index} session={j} from={from} u={u} S={S} X={X} font={font} />
      ))}
    </Animated.View>
  );
}

function Badge({ index, session, from, u, S, X, font }: {
  index: number; session: number; from: SharedValue<number>; u: SharedValue<number>; S: number; X: (x: number) => number; font: Font;
}) {
  const rw = layout.rows;
  const delta = sessions[session].delta[index];
  // The places-gained badge rides with its driver and flips to the new count halfway along the drive.
  const style = useAnimatedStyle(() => {
    const f = from.get();
    const e = rowProgress(u.get(), SLOT_OF[1 - f][index]);
    const q = towards(f, easeOut(seg(e, WINDOWS.badge.from, WINDOWS.badge.to)));
    const show = session === 0 ? 1 - q : q;
    return { opacity: show, transform: [{ translateY: (1 - show) * BADGE_SLIDE * S * (session === 0 ? -1 : 1) }] };
  });
  return (
    <Animated.View style={[styles.fill, style]} pointerEvents="none">
      <View style={[styles.abs, { left: X(rw.badge.x), top: (rw.badge.y - ROW_ANCHOR) * S }]}>
        <ChangeBadge s={S} kind={badgeKind(delta)} />
      </View>
      <Text allowFontScaling={false} style={[styles.abs, font(FONT.semibold, rw.digitSize), { left: X(rw.digitX), top: (rw.digitBaseline - ROW_ANCHOR) * S - ASCENT * rw.digitSize * S }]}>
        {Math.abs(delta)}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.base },
  fill: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0 },
  abs: { position: 'absolute' },
  right: { textAlign: 'right' },
  back: { position: 'absolute', backgroundColor: colors.backFill, borderColor: colors.backRing, alignItems: 'center', justifyContent: 'center' },
  rule: { position: 'absolute', height: StyleSheet.hairlineWidth, backgroundColor: colors.hairline },
  plate: { position: 'absolute', backgroundColor: colors.base },
  plateLit: { backgroundColor: colors.plate, borderWidth: StyleSheet.hairlineWidth, borderColor: colors.plateEdge },
  measure: { position: 'absolute', left: -1000, top: 0, opacity: 0, flexDirection: 'row', alignItems: 'flex-start' },
});
