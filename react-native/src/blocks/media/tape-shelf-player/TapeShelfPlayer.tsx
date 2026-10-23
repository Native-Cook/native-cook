import { Canvas, Picture, Skia, TileMode, matchFont, useFont } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import { type LayoutChangeEvent, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  type Fonts,
  type Geo,
  INK,
  MAGENTA,
  PAGE,
  TAPES,
  cell,
  drawFlyingCassette,
  drawPlayer,
  drawSleeve,
  drawTransportIcon,
  makeGeo,
  makeSleeves,
} from './art';
import {
  EJECT_AT,
  EJECT_LENGTH,
  INSERT_AT,
  INSERT_LENGTH,
  LOOP,
  LOOP_TAPE,
  POWER_ON_AT,
  type Pose,
  ejectPose,
  insertPose,
  loopPose,
  ramp,
  restPose,
} from './motion';

/*
 Tape Shelf Player — a shelf of home-video tapes under a retro player: a tape slides out of its
 sleeve, flies into the slot and the little CRT lights up with the memory. A port of the SwiftUI
 block (TapeShelfPlayer.swift); the numbers are the same.

 Look: a warm off-white page. At the top a light grey shelf band carries the player: a dark
 rounded box (0.52 × the screen width, 188 pt tall) with a curved-glass CRT, a cassette slot, an
 LED and three buttons. Below, "Collections" and a 3 × 3 grid of VHS sleeves (invented brands,
 a rainbow band, a white sticker with a handwritten title in Gochi Hand), each holding its
 cassette. A magenta "Play something old" pill floats at the bottom; while a tape plays, a black
 transport bar (eject, rewind, pause, fast-forward, shuffle) takes its place.

 autoPlay timeline (LOOP = 10 s, motion.ts), with the middle tape "Sunset pier":
   0.0 – 0.8    rest; the screen blinks INSERT TAPE once a second.
   0.8 – 1.05   the sleeve lifts (5 % larger, deeper shadow); 0.95 – 1.4 the cassette slides out to
                its right (ease-in-out); 1.4 – 2.15 it flies on an arc to the slot, turning from
                upright to level, growing 12 % mid-flight and shrinking to the slot's width; the
                sleeve settles back.
   2.15 – 2.45  it squashes flat into the slot and is gone; 2.45 the player jolts (3 pt, a damped
                sine) and the LED turns green.
   2.5 – 2.85   the CRT powers on (a bright line opening into the picture); the transport bar
                rises (2.6 – 3.0) as the pill sinks.
   2.85 – 6.6   the home video plays: a sky and a sun over water with drifting glints, a pier,
                a rolling tracking band, the camcorder's "PLAY ▶" and date stamp.
   6.6 – 8.8    eject: the eject key dips, the picture collapses to a line and a dot, the bar
                sinks and the pill returns, the cassette pops out of the slot, flies back and
                slides into its sleeve.
   8.8 – 10.0   rest: the same frame as 0.0.

 Touch: tap a tape (or the pill, for the next one) to play it; tap eject or the player to eject.
 A touch takes over from whatever is on screen, the loop included: tapping another tape sends the
 one in the player home first (ejected once it is in, turned back mid-flight before that) and then
 inserts the tapped one; tapping a tape that is on its way home turns it round. Pause freezes the
 picture. 2 s after the last touch (and once a tape that was tapped has played for 2 s) the tape
 is ejected and the loop starts again. Reduced motion: no loop and no flight; the screen and the bars cross-fade in 0.3 s and the
 picture holds still.
 */

const HAND = require('./assets/GochiHand-Regular.ttf');
const MONO = Platform.select({ ios: 'Menlo', default: 'monospace' });
// Skia matches families, not PostScript names: weight 900 of Helvetica Neue is its Condensed Black.
const CONDENSED = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif-condensed' });
const SANS = Platform.select({ ios: '.AppleSystemUIFont', default: 'sans-serif' });
const NAMES = ['Eject', 'Rewind', 'Pause', 'Fast forward', 'Shuffle'];
/** A tape that was tapped plays at least this long before it is sent home on its own. */
const MIN_PLAY = 2;
/** Sequence kinds: inserting (then playing) a tape, ejecting it, or turning it back before it was in. */
const EJECT = 0;
const INSERT = 1;
const BACK = 2;

type Motion = {
  now: SharedValue<number>;
  clock: SharedValue<number>;
  autoStart: SharedValue<number>;
  /**
   * The sequence under way, or []: [since, kind (INSERT, EJECT or BACK), tape, the seconds of video
   * played (EJECT) or the insert time it runs back from (BACK), a tape to insert once this one is home or −1].
   */
  seq: SharedValue<number[]>;
  /** The picture held by pause, or −1. */
  frozen: SharedValue<number>;
  nextTape: SharedValue<number>;
  busyUntil: SharedValue<number>;
  resumeAt: SharedValue<number>;
  /** Which transport key or the pill (5) is held, or −1. */
  pressed: SharedValue<number>;
};

function tactile(kind: 'light' | 'heavy' | 'tick') {
  if (kind === 'tick') Haptics.selectionAsync().catch(() => {});
  else Haptics.impactAsync(kind === 'heavy' ? Haptics.ImpactFeedbackStyle.Heavy : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** How long a tape going home takes; −1 while one goes in or plays. */
function outLength(s: number[], reduced: boolean) {
  'worklet';
  if (s[1] === INSERT) return -1;
  if (reduced) return 0.3;
  return s[1] === EJECT ? EJECT_LENGTH : s[3];
}

/** The sequence at a time: a queued tape starts going in the moment the previous one is home. */
function resolved(s: number[], at: number, reduced: boolean): number[] {
  'worklet';
  if (s.length !== 5 || s[4] < 0) return s;
  const len = outLength(s, reduced);
  if (len < 0 || at - s[0] < len) return s;
  return [s[0] + len, INSERT, s[4], 0, -1];
}

/** The pose at a time, and the clock for blinking: [pose, clock]. */
function poseAt(m: Motion, at: number, reduced: boolean): [Pose, number] {
  'worklet';
  const start = m.autoStart.get();
  if (start >= 0 && at >= start) {
    const t = (at - start) % LOOP;
    return [loopPose(t), t];
  }
  const clock = at % 1000;
  const s = resolved(m.seq.get(), at, reduced);
  if (s.length !== 5) return [restPose(), clock];
  const e = at - s[0];
  let p: Pose;
  if (reduced) {
    p = restPose();
    p.tape = s[2];
    const f = ramp(0, 0.3, e);
    const on = s[1] === INSERT ? f : 1 - f;
    p.screen = on;
    p.transport = on;
    p.pill = 1 - on;
    p.led = on > 0.5 ? 1 : 0;
    p.video = 2.4;
  } else if (s[1] === INSERT) {
    p = insertPose(e, s[2]);
  } else if (s[1] === EJECT) {
    p = e < EJECT_LENGTH ? ejectPose(e, s[2], s[3]) : restPose();
  } else {
    p = insertPose(Math.max(0, s[3] - e), s[2]);
  }
  const frozen = m.frozen.get();
  if (frozen >= 0 && s[1] === INSERT && !reduced) p.video = frozen;
  return [p, clock];
}

/** Turns whatever is on screen (the loop included) into the hand-driven state. */
function takeOver(m: Motion, now: number, reduced: boolean) {
  'worklet';
  m.resumeAt.set(-1);
  const start = m.autoStart.get();
  if (start >= 0) {
    m.autoStart.set(-1);
    if (now >= start) {
      const t = (now - start) % LOOP;
      if (t >= INSERT_AT && t < EJECT_AT) m.seq.set([now - (t - INSERT_AT), INSERT, LOOP_TAPE, 0, -1]);
      else if (t >= EJECT_AT && t < EJECT_AT + EJECT_LENGTH) {
        m.seq.set([now - (t - EJECT_AT), EJECT, LOOP_TAPE, EJECT_AT - INSERT_AT - POWER_ON_AT, -1]);
      } else m.seq.set([]);
      m.frozen.set(-1);
      return;
    }
  }
  const s = m.seq.get();
  const r = resolved(s, now, reduced);
  if (r !== s) m.seq.set(r);
}

/**
 * Sends the tape that is going in or playing back to its sleeve: ejected once it is in, turned back the way it came
 * before that. False when no tape is going in or playing.
 */
function stop(m: Motion, now: number, reduced: boolean) {
  'worklet';
  const s = m.seq.get();
  if (s.length !== 5 || s[1] !== INSERT) return false;
  const e = now - s[0];
  const frozen = m.frozen.get();
  if (reduced || e >= INSERT_LENGTH) m.seq.set([now, EJECT, s[2], frozen >= 0 ? frozen : Math.max(0, e - POWER_ON_AT), -1]);
  else m.seq.set([now, BACK, s[2], e, -1]);
  m.frozen.set(-1);
  return true;
}

/** Sets the tape to insert once the one in the sequence is home. */
function queue(m: Motion, tape: number) {
  'worklet';
  const s = m.seq.get();
  if (s.length === 5) m.seq.set([s[0], s[1], s[2], s[3], tape]);
}

export function TapeShelfPlayer({ autoPlay = false }: { autoPlay?: boolean }) {
  const reduced = useReducedMotion();
  const insets = useSafeAreaInsets();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [playing, setPlaying] = useState(false);
  const g = useMemo<Geo | null>(() => (size.width > 0 ? makeGeo(size.width, size.height, insets.top, insets.bottom) : null), [size, insets.top, insets.bottom]);
  const hand = useFont(HAND, g ? g.cellW * 0.15 : 12);
  const handSmall = useFont(HAND, g ? g.cassetteT * 0.2 : 12);
  const fonts = useMemo<Fonts | null>(() => {
    if (!g || !hand || !handSmall) return null;
    return {
      hand,
      handSmall,
      brand: matchFont({ fontFamily: CONDENSED, fontSize: g.cellW * 0.16, fontWeight: '900' }),
      small: matchFont({ fontFamily: SANS, fontSize: g.cellW * 0.075, fontWeight: '700' }),
      badge: matchFont({ fontFamily: SANS, fontSize: g.cellW * 0.06, fontWeight: '800' }),
      mono9: matchFont({ fontFamily: MONO, fontSize: 9, fontWeight: '600' }),
      mono8: matchFont({ fontFamily: MONO, fontSize: 8, fontWeight: '700' }),
    };
  }, [g, hand, handSmall]);
  const sleeves = useMemo(() => (g && fonts ? makeSleeves(g, fonts) : null), [g, fonts]);

  const m: Motion = {
    now: useSharedValue(-1),
    clock: useSharedValue(0),
    autoStart: useSharedValue(-1),
    seq: useSharedValue<number[]>([]),
    frozen: useSharedValue(-1),
    nextTape: useSharedValue(LOOP_TAPE + 1),
    busyUntil: useSharedValue(-1),
    resumeAt: useSharedValue(-1),
    pressed: useSharedValue(-1),
  };

  useFrameCallback((info) => {
    const now = info.timestamp / 1000;
    if (m.now.get() < 0 && autoPlay && !reduced) m.autoStart.set(now);
    m.now.set(now);
    if (!reduced || now < m.busyUntil.get()) m.clock.set(now);
    // 2 s after the last touch (and once a tape that was tapped has played MIN_PLAY seconds): the tape is sent
    // home, then the loop starts at rest.
    const resumeAt = m.resumeAt.get();
    if (resumeAt >= 0 && now >= resumeAt) {
      const cur = m.seq.get();
      const s = resolved(cur, now, reduced);
      if (s !== cur) m.seq.set(s);
      let ready = -1;
      if (s.length === 5) {
        if (s[1] === INSERT) ready = s[0] + INSERT_LENGTH + MIN_PLAY;
        else if (s[4] >= 0) ready = s[0] + outLength(s, reduced) + INSERT_LENGTH + MIN_PLAY;
      }
      if (now < ready) return; // let the tape seat and play first
      m.resumeAt.set(-1);
      stop(m, now, reduced);
      const after = m.seq.get();
      const len = after.length === 5 ? outLength(after, reduced) : -1;
      m.autoStart.set(len >= 0 ? Math.max(now, after[0] + len) : now);
    }
  });

  const pose = useDerivedValue(() => poseAt(m, m.clock.get(), reduced));

  useAnimatedReaction(
    () => {
      if (m.autoStart.get() >= 0) return pose.get()[0].screen > 0.5;
      const s = resolved(m.seq.get(), m.now.get(), reduced);
      return s.length === 5 && s[1] === INSERT;
    },
    (now, previous) => {
      if (now !== previous) scheduleOnRN(setPlaying, now);
    },
  );

  const play = (tape: number) => {
    'worklet';
    const now = m.now.get();
    takeOver(m, now, reduced);
    m.nextTape.set((tape + 1) % TAPES.length);
    scheduleOnRN(tactile, 'light');
    const s = m.seq.get();
    const len = s.length === 5 ? outLength(s, reduced) : -1;
    if (len >= 0 && now - s[0] < len) {
      // A tape is on its way home.
      if (s[1] === BACK && s[2] === tape) {
        // The tapped one: turn it round.
        m.seq.set([now - (s[3] - (now - s[0])), INSERT, tape, 0, -1]);
      } else {
        queue(m, tape);
      }
    } else if (s.length === 5 && s[1] === INSERT) {
      if (s[2] !== tape) {
        // Another tape: send this one home, then insert the tapped one.
        stop(m, now, reduced);
        queue(m, tape);
      } else if (now - s[0] >= INSERT_LENGTH) {
        // The one that plays: eject it.
        stop(m, now, reduced);
      }
    } else {
      m.seq.set([now, INSERT, tape, 0, -1]);
      m.frozen.set(-1);
    }
    if (reduced) m.busyUntil.set(now + 0.7);
    if (autoPlay && !reduced) m.resumeAt.set(now + 2);
  };

  const eject = () => {
    'worklet';
    const now = m.now.get();
    takeOver(m, now, reduced);
    if (stop(m, now, reduced)) {
      scheduleOnRN(tactile, 'light');
      if (reduced) m.busyUntil.set(now + 0.7);
    } else {
      const s = m.seq.get();
      if (s.length === 5 && s[4] >= 0) {
        // Nothing in the player yet: forget the tape that was waiting.
        queue(m, -1);
        scheduleOnRN(tactile, 'tick');
      }
    }
    if (autoPlay && !reduced) m.resumeAt.set(now + 2);
  };

  const togglePause = () => {
    'worklet';
    const now = m.now.get();
    takeOver(m, now, reduced);
    const s = m.seq.get();
    if (s.length !== 5 || s[1] !== INSERT) {
      if (autoPlay && !reduced) m.resumeAt.set(now + 2);
      return;
    }
    const frozen = m.frozen.get();
    if (frozen >= 0) {
      // Carry on from the frozen frame.
      m.seq.set([now - (frozen + POWER_ON_AT), INSERT, s[2], 0, -1]);
      m.frozen.set(-1);
    } else {
      m.frozen.set(Math.max(0, now - s[0] - POWER_ON_AT));
    }
    scheduleOnRN(tactile, 'tick');
    if (autoPlay && !reduced) m.resumeAt.set(now + 2);
  };

  // The clunk as the tape seats.
  useAnimatedReaction(
    () => pose.get()[0].led > 0.5 && m.autoStart.get() < 0,
    (on, previous) => {
      if (on && previous === false && !reduced) scheduleOnRN(tactile, 'heavy');
    },
  );

  const tap = useMemo(() => {
    if (!g) return Gesture.Tap();
    return Gesture.Tap().onEnd((e) => {
      const b = g.body;
      if (e.x >= b.x - 8 && e.x <= b.x + b.w + 8 && e.y >= b.y - 8 && e.y <= b.y + b.h + 8) {
        eject();
        return;
      }
      for (let i = 0; i < TAPES.length; i++) {
        const r = cell(g, i);
        if (e.x >= r.x && e.x <= r.x + r.w && e.y >= r.y && e.y <= r.y + r.h) {
          play(i);
          return;
        }
      }
    });
    // The shared values are stable; the gesture only depends on these.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [g, reduced, autoPlay]);

  const picture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, size.width, size.height));
    if (!g || !fonts || !sleeves) return recorder.finishRecordingAsPicture();
    const [p, clock] = pose.get();
    // The shelf band behind the player.
    const band = Skia.Paint();
    band.setShader(
      Skia.Shader.MakeLinearGradient({ x: 0, y: g.shelfTop }, { x: 0, y: g.shelfBottom }, [Skia.Color('#E9E8E4'), Skia.Color('#DCDBD6')], null, TileMode.Clamp),
    );
    canvas.drawRect(Skia.XYWHRect(0, g.shelfTop, g.w, g.shelfBottom - g.shelfTop), band);
    const ledge = Skia.Paint();
    ledge.setColor(Skia.Color('#CFCEC9'));
    canvas.drawRect(Skia.XYWHRect(0, g.shelfBottom - 6, g.w, 6), ledge);
    const drop = Skia.Paint();
    drop.setShader(
      Skia.Shader.MakeLinearGradient(
        { x: 0, y: g.shelfBottom },
        { x: 0, y: g.shelfBottom + 10 },
        [Float32Array.of(0, 0, 0, 0.12), Float32Array.of(0, 0, 0, 0)],
        null,
        TileMode.Clamp,
      ),
    );
    canvas.drawRect(Skia.XYWHRect(0, g.shelfBottom, g.w, 10), drop);
    drawPlayer(canvas, g, p, clock, reduced, fonts);

    const picked = p.tape;
    const flying = p.flight > 0.0001 || p.squash > 0;
    for (let i = 0; i < TAPES.length; i++) if (i !== picked) drawSleeve(canvas, sleeves[i], cell(g, i), 0);
    if (!flying) drawFlyingCassette(canvas, p, g, fonts);
    drawSleeve(canvas, sleeves[picked], cell(g, picked), p.lift);
    if (flying) drawFlyingCassette(canvas, p, g, fonts);
    return recorder.finishRecordingAsPicture();
  });

  // The transport bar, drawn so the eject key can dip with the loop.
  const transportPicture = useDerivedValue(() => {
    const recorder = Skia.PictureRecorder();
    const width = g ? g.w - 48 : 0;
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, 60));
    if (!g) return recorder.finishRecordingAsPicture();
    const p = pose.get()[0];
    const pressed = m.pressed.get();
    const paused = m.frozen.get() >= 0 && m.autoStart.get() < 0;
    const slot = (width - 16) / 5;
    for (let i = 0; i < 5; i++) {
      const cx = 8 + slot * i + slot / 2;
      const ep = i === 0 ? p.ejectPress : 0;
      const sc = (pressed === i ? 0.94 : 1) * (1 - 0.12 * ep);
      canvas.save();
      canvas.translate(cx, 30);
      canvas.scale(sc, sc);
      canvas.translate(-cx, -30);
      if (i === 0) {
        const ring = Skia.Paint();
        ring.setAntiAlias(true);
        ring.setColor(Float32Array.of(1, 1, 1, 0.08 + 0.3 * ep));
        canvas.drawCircle(cx, 30, 22, ring);
      }
      drawTransportIcon(canvas, i, paused, cx, 30, i === 0 ? 1 : 0.85);
      canvas.restore();
    }
    return recorder.finishRecordingAsPicture();
  });

  const pillStyle = useAnimatedStyle(() => {
    const pill = pose.get()[0].pill;
    return { opacity: pill, transform: [{ translateY: 24 * (1 - pill) }, { scale: m.pressed.get() === 5 ? 0.94 : 1 }] };
  });
  const transportStyle = useAnimatedStyle(() => {
    const tr = pose.get()[0].transport;
    return { opacity: Math.min(1, tr * 3), transform: [{ translateY: (1 - tr) * (90 + insets.bottom) }] };
  });

  const press = (i: number) => {
    scheduleOnUI(() => {
      'worklet';
      const p = poseAt(m, m.now.get(), reduced)[0];
      if (i === 5) {
        if (p.pill > 0.5) play(m.nextTape.get());
        return;
      }
      if (p.transport <= 0.5) return;
      if (i === 0) eject();
      else if (i === 2) togglePause();
      else if (i === 4) play(m.nextTape.get());
      else scheduleOnRN(tactile, 'tick');
    });
  };
  const setPressed = (i: number) => {
    scheduleOnUI(() => {
      'worklet';
      m.pressed.set(i);
      m.clock.set(m.now.get());
    });
  };

  const onLayout = (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width !== size.width || height !== size.height) setSize({ width, height });
  };

  return (
    <View style={styles.screen} onLayout={onLayout}>
      <StatusBar style="dark" />
      {g && fonts && sleeves ? (
        <>
          <GestureDetector gesture={tap}>
            <View style={StyleSheet.absoluteFill} accessible accessibilityLabel={playing ? 'Tape player, playing' : 'Tape player, no tape'}>
              <Canvas style={StyleSheet.absoluteFill}>
                <Picture picture={picture} />
              </Canvas>
            </View>
          </GestureDetector>

          <View pointerEvents="none" style={[styles.row, styles.firstBaseline, { top: g.top + 10, width: g.w - 48 }]}>
            <Text style={styles.header}>Home Tapes</Text>
            <View style={styles.dots}>
              {[0, 1, 2].map((i) => (
                <View key={i} style={styles.dot} />
              ))}
            </View>
          </View>
          <View pointerEvents="none" style={[styles.row, styles.baseline, { top: g.shelfBottom + 16, width: g.w - 48 }]}>
            <Text style={styles.collections}>Collections</Text>
            <Text style={styles.count}>9 tapes</Text>
          </View>

          {/* A fade so the bottom row tucks under the controls. */}
          <LinearGradient pointerEvents="none" colors={['rgba(244,242,238,0)', 'rgba(244,242,238,0.96)']} style={[styles.fade, { top: g.h - 120 }]} />

          <Animated.View style={[styles.pillWrap, { top: g.pillY, left: (g.w - 210) / 2 }, pillStyle]}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Play something old"
              onPressIn={() => setPressed(5)}
              onPressOut={() => setPressed(-1)}
              onPress={() => press(5)}
              style={styles.pill}
            >
              <Text style={styles.pillText}>Play something old</Text>
            </Pressable>
          </Animated.View>

          <Animated.View style={[styles.transport, { top: g.transportY, width: g.w - 48 }, transportStyle]}>
            <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
              <Picture picture={transportPicture} />
            </Canvas>
            <View style={styles.keys}>
              {NAMES.map((name, i) => (
                <Pressable
                  key={name}
                  accessibilityRole="button"
                  accessibilityLabel={name}
                  onPressIn={() => setPressed(i)}
                  onPressOut={() => setPressed(-1)}
                  onPress={() => press(i)}
                  style={styles.key}
                />
              ))}
            </View>
          </Animated.View>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  row: { position: 'absolute', left: 24, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  baseline: { alignItems: 'flex-end' },
  // SwiftUI's .firstTextBaseline: the dots sit on the title's baseline.
  firstBaseline: { alignItems: 'baseline' },
  header: { fontSize: 17, fontWeight: '600', color: INK },
  dots: { flexDirection: 'row', gap: 3 },
  dot: { width: 5, height: 5, borderRadius: 2.5, backgroundColor: MAGENTA },
  collections: { fontSize: 22, fontWeight: '800', letterSpacing: -0.4, color: INK },
  count: { fontSize: 13, fontWeight: '500', color: 'rgba(20,20,20,0.45)', marginBottom: 3 },
  fade: { position: 'absolute', left: 0, right: 0, height: 120 },
  pillWrap: {
    position: 'absolute',
    width: 210,
    height: 44,
    borderRadius: 22,
    shadowColor: MAGENTA,
    shadowOpacity: 0.35,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  pill: { flex: 1, borderRadius: 22, backgroundColor: MAGENTA, alignItems: 'center', justifyContent: 'center' },
  pillText: { fontSize: 15, fontWeight: '600', color: '#FFFFFF' },
  transport: {
    position: 'absolute',
    left: 24,
    height: 60,
    borderRadius: 22,
    backgroundColor: '#141416',
    shadowColor: '#000000',
    shadowOpacity: 0.25,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 10,
  },
  keys: { flex: 1, flexDirection: 'row', paddingHorizontal: 8 },
  key: { flex: 1 },
});
