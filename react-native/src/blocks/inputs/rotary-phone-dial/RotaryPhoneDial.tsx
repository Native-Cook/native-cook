import { Canvas, matchFont, Picture, Skia } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useEffect, useMemo, useRef, useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  FadeOut,
  useDerivedValue,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import {
  CARD_NAME,
  CARD_NUMBER,
  type DialFonts,
  type DialGeometry,
  drawCard,
  drawFinger,
  drawPulses,
  drawStatic,
  drawStop,
  drawWheel,
} from './RotaryPhoneDialDrawing';
import {
  createDial,
  dialCancelScripted,
  dialCardPress,
  dialClear,
  dialDrag,
  dialGrab,
  dialRelease,
  dialScript,
  type DialSim,
  dialStep,
  EVENT_CLEAR,
  EVENT_DIGIT,
  EVENT_STOP,
  EVENT_TICK,
  holeAt,
  LOOP,
  MAX_FRAME,
} from './RotaryPhoneDialSim';

type Props = {
  /** Dials a number, clears it with a press on the card, and starts over. */
  autoPlay?: boolean;
  /** Called with each digit as it is dialled. */
  onDigit?: (digit: number) => void;
};

// Type on iOS: American Typewriter on the strip, DIN Condensed on the plate, Futura on the card.
const IOS = process.env.EXPO_OS === 'ios';
const STRIP_FAMILY = IOS ? 'American Typewriter' : 'monospace';
const PLATE_FAMILY = IOS ? 'DIN Condensed' : 'sans-serif-condensed';
const CARD_FAMILY = IOS ? 'Futura' : 'sans-serif-medium';

// Layout, in points: the strip under the header, the dial below it, the hint at the bottom.
const SIDE = 24;
const HEADER = 54;
const STRIP_H = 74;
const FOOTER = 56;
// The plate's radius: at most this share of the width, and this many points.
const PLATE_SHARE = 0.42;
const PLATE_MAX = 186;
// The wheel and its holes, as shares of the plate and wheel.
const WHEEL = 0.93;
const RING = 0.71;
const HOLE = 0.158;
const HUB = 0.4;
// The strip keeps the last MAX_DIGITS digits, grouped 3 · 3 · 4 like a phone number.
const MAX_DIGITS = 10;
// Long press on the card clears the strip after CLEAR_PRESS ms.
const CLEAR_PRESS = 550;
// After a touch the loop waits this long, then starts over.
const USER_HOLD = 3.5;

const INK = '#201D18';
const PALE = 'rgba(241,232,211,0.62)';

type Digit = { id: number; value: number };

/** A typed digit drops onto the strip: falls a little, lands with a small settle. */
function dropIn() {
  'worklet';
  return {
    initialValues: { opacity: 0, transform: [{ translateY: -22 }, { scale: 1.18 }] },
    animations: {
      opacity: withTiming(1, { duration: 90 }),
      transform: [
        { translateY: withSpring(0, { duration: 420, dampingRatio: 0.55 }) },
        { scale: withSpring(1, { duration: 360, dampingRatio: 0.7 }) },
      ],
    },
  };
}

function fadeIn() {
  'worklet';
  return { initialValues: { opacity: 0 }, animations: { opacity: withTiming(1, { duration: 200 }) } };
}

/** A little typewriter wobble per position, so the digits do not sit on a ruler. */
function jitter(index: number) {
  const a = Math.sin(index * 12.9898 + 4.1) * 43758.5453;
  const b = Math.sin(index * 78.233 + 1.7) * 24634.6345;
  return { rotate: `${((a - Math.floor(a)) - 0.5) * 4}deg`, y: ((b - Math.floor(b)) - 0.5) * 1.6, ink: 0.8 + (a - Math.floor(a)) * 0.2 };
}

export function RotaryPhoneDial({ autoPlay = false, onDigit }: Props) {
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotion();
  const auto = autoPlay && !reducedMotion;
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);
  const [digits, setDigits] = useState<Digit[]>([]);
  const nextId = useRef(0);

  const width = size?.width ?? 0;
  const height = size?.height ?? 0;

  const geo = useMemo<DialGeometry | null>(() => {
    if (width <= 0) return null;
    const stripY = insets.top + HEADER;
    const top = stripY + STRIP_H + 20;
    const bottom = height - insets.bottom - FOOTER;
    // Never below a usable size, even on a very short window.
    const plateR = Math.max(40, Math.min(width * PLATE_SHARE, ((bottom - top) / 2) * 0.86, PLATE_MAX));
    const wheelR = plateR * WHEEL;
    return {
      width,
      height,
      cx: width / 2,
      cy: (top + bottom) / 2,
      plateR,
      wheelR,
      ringR: wheelR * RING,
      holeR: wheelR * HOLE,
      hubR: wheelR * HUB,
      strip: { x: SIDE, y: stripY, w: width - SIDE * 2, h: STRIP_H },
    };
  }, [width, height, insets.top, insets.bottom]);

  const fonts = useMemo<DialFonts | null>(() => {
    if (!geo) return null;
    return {
      digit: matchFont({ fontFamily: PLATE_FAMILY, fontSize: Math.round(geo.holeR * 0.98), fontWeight: 'bold' }),
      letters: matchFont({ fontFamily: CARD_FAMILY, fontSize: 7.5, fontWeight: '700' }),
      cardName: matchFont({ fontFamily: CARD_FAMILY, fontSize: 9, fontWeight: '700' }),
      cardNumber: matchFont({ fontFamily: STRIP_FAMILY, fontSize: Math.round(geo.hubR * 0.36), fontWeight: '600' }),
    };
  }, [geo]);

  // Everything that never moves is recorded once.
  const backdrop = useMemo(() => {
    if (!geo || !fonts) return null;
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, geo.width, geo.height));
    drawStatic(canvas, geo, fonts);
    return recorder.finishRecordingAsPicture();
  }, [geo, fonts]);

  const sim = useSharedValue<DialSim | null>(null);
  const tick = useSharedValue(0);
  const clock = useSharedValue(0);
  const looping = useSharedValue(auto);
  const hold = useSharedValue(0);
  // The scripted finger: [hole, angle, shown].
  const finger = useSharedValue<number[]>([-1, 0, 0]);
  // How visible the pulse dots are.
  const pulseShown = useSharedValue(0);
  const cardHeld = useSharedValue(false);

  useEffect(() => {
    looping.set(auto);
    clock.set(0);
    hold.set(0);
  }, [auto, clock, hold, looping]);

  const addDigit = (value: number, byUser: boolean) => {
    const id = nextId.current;
    nextId.current += 1;
    setDigits((prev) => [...prev, { id, value }].slice(-MAX_DIGITS));
    if (byUser) onDigit?.(value);
  };
  const clearDigits = () => setDigits([]);
  const feel = (events: number) => {
    if (process.env.EXPO_OS === 'web') return;
    if (events & EVENT_CLEAR) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
    else if (events & EVENT_STOP) Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    else if (events & EVENT_TICK) Haptics.selectionAsync().catch(() => {});
  };

  useFrameCallback((frame) => {
    // The script and the mechanism share one clock, capped so a slow frame cannot skip ahead.
    const dt = Math.min((frame.timeSincePreviousFrame ?? 16) / 1000, MAX_FRAME);
    let s = sim.get();
    if (!s) {
      s = createDial(reducedMotion);
      sim.set(s);
    }
    let shownFinger = [-1, 0, 0];
    // A finger resting in a hole keeps the script away.
    if (s.touch !== -1 && !s.scripted) hold.set(USER_HOLD);
    if (hold.get() > 0) {
      hold.set(hold.get() - dt);
      if (hold.get() <= 0) clock.set(0);
    } else if (looping.get()) {
      const before = clock.get();
      const now = (before + dt) % LOOP;
      clock.set(now);
      shownFinger = dialScript(s, before, now);
    }
    finger.set(shownFinger);
    dialStep(s, dt);
    const showing = s.dialling || (s.mode === 1 && s.reached) ? 1 : 0;
    pulseShown.set(pulseShown.get() + (showing - pulseShown.get()) * (1 - Math.exp(-dt * (showing ? 14 : 5))));
    if (s.events !== 0) {
      const byUser = s.eventsByUser;
      if (s.events & EVENT_DIGIT) scheduleOnRN(addDigit, s.digit, byUser);
      if (s.events & EVENT_CLEAR) scheduleOnRN(clearDigits);
      if (byUser) scheduleOnRN(feel, s.events);
      s.events = 0;
      s.eventsByUser = false;
    }
    tick.set(tick.get() + 1);
  });

  const touched = () => {
    'worklet';
    hold.set(USER_HOLD);
    const s = sim.get();
    if (s) dialCancelScripted(s);
  };

  const cx = geo?.cx ?? 0;
  const cy = geo?.cy ?? 0;
  const ringR = geo?.ringR ?? 0;
  const holeR = geo?.holeR ?? 0;
  const hubR = geo?.hubR ?? 0;

  // The wheel only moves with a finger in a hole: anywhere else the touch is not for it.
  const wheel = Gesture.Manual()
    .onTouchesDown((event, manager) => {
      const s = sim.get();
      const t = event.changedTouches[0];
      if (!s || !t) {
        manager.fail();
        return;
      }
      // A second finger while one already turns the wheel is ignored (failing here would drop the
      // first finger mid-turn and leave the wheel held).
      if (s.touch !== -1 && !s.scripted) return;
      const dx = t.x - cx;
      const dy = t.y - cy;
      const angle = (Math.atan2(dy, dx) * 180) / Math.PI;
      const hole = holeAt(s, angle, Math.hypot(dx, dy), holeR, ringR);
      if (hole < 0) {
        manager.fail();
        return;
      }
      touched();
      dialGrab(s, t.id, hole, angle, false);
      manager.activate();
    })
    .onTouchesMove((event) => {
      const s = sim.get();
      if (!s) return;
      hold.set(USER_HOLD);
      for (const t of event.changedTouches) {
        dialDrag(s, t.id, (Math.atan2(t.y - cy, t.x - cx) * 180) / Math.PI);
      }
    })
    .onTouchesUp((event, manager) => {
      const s = sim.get();
      if (s) for (const t of event.changedTouches) dialRelease(s, t.id);
      // Only the finger in the hole ends the turn; another one lifting does not.
      if (!s || s.touch === -1 || s.scripted) manager.end();
    })
    .onTouchesCancelled((event, manager) => {
      const s = sim.get();
      if (s) for (const t of event.changedTouches) dialRelease(s, t.id);
      manager.end();
    })
    .onFinalize(() => {
      // However the gesture ended, a real finger never stays in a hole after it.
      const s = sim.get();
      if (s && s.touch !== -1 && !s.scripted) dialRelease(s, s.touch);
    });

  // Holding the number card clears the strip.
  const card = Gesture.LongPress()
    .minDuration(CLEAR_PRESS)
    .maxDistance(14)
    .onBegin((event) => {
      const inside = Math.hypot(event.x - cx, event.y - cy) < hubR;
      cardHeld.set(inside);
      const s = sim.get();
      if (!inside || !s) return;
      touched();
      dialCardPress(s, true);
    })
    .onStart(() => {
      const s = sim.get();
      if (!cardHeld.get() || !s) return;
      dialClear(s, true);
      dialCardPress(s, false);
    })
    .onFinalize(() => {
      const s = sim.get();
      if (s && cardHeld.get()) dialCardPress(s, false);
      cardHeld.set(false);
    });

  const picture = useDerivedValue(() => {
    tick.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, Math.max(1, width), Math.max(1, height)));
    const s = sim.get();
    if (geo && fonts && s) {
      drawWheel(canvas, geo, s.phi);
      drawStop(canvas, geo, s.kick);
      drawCard(canvas, geo, fonts, s.card);
      const f = finger.get();
      if (f[0] >= 0) drawFinger(canvas, geo, f[1], f[2]);
      drawPulses(canvas, geo, s.pulses, pulseShown.get());
    }
    return recorder.finishRecordingAsPicture();
  });

  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setSize({ width: w, height: h });
  };

  return (
    <View style={styles.root} onLayout={onLayout}>
      <GestureDetector gesture={Gesture.Simultaneous(wheel, card)}>
        <View
          style={StyleSheet.absoluteFill}
          accessible
          accessibilityLabel={`Rotary dial. Number dialled: ${digits.map((d) => d.value).join(' ') || 'none'}. Drag a hole to the stop to dial; hold the card to clear.`}
        >
          {geo && backdrop ? (
            <Canvas style={StyleSheet.absoluteFill}>
              <Picture picture={backdrop} />
              <Picture picture={picture} />
            </Canvas>
          ) : null}
        </View>
      </GestureDetector>
      {geo ? (
        <>
          <View pointerEvents="none" style={[styles.header, { top: insets.top + 16 }]}>
            <Text style={styles.kicker}>ROTARY DIAL</Text>
            <Text style={styles.title}>
              {CARD_NAME.charAt(0) + CARD_NAME.slice(1).toLowerCase()} {CARD_NUMBER}
            </Text>
          </View>
          <View
            pointerEvents="none"
            style={[styles.strip, { left: geo.strip.x + 18, top: geo.strip.y, height: geo.strip.h, right: geo.strip.x + 104 }]}
          >
            {digits.map((d, i) => {
              const j = jitter(d.id);
              return (
                <Animated.View
                  key={d.id}
                  entering={reducedMotion ? fadeIn : dropIn}
                  exiting={FadeOut.duration(220)}
                  style={{ marginLeft: i === 3 || i === 6 ? 12 : 0 }}
                >
                  <Text style={[styles.digit, { color: `rgba(32,29,24,${j.ink})`, transform: [{ translateY: j.y }, { rotate: j.rotate }] }]}>
                    {d.value}
                  </Text>
                </Animated.View>
              );
            })}
          </View>
          <Text pointerEvents="none" style={[styles.hint, { bottom: insets.bottom + 24 }]}>
            Turn a hole to the stop · Hold the card to clear
          </Text>
        </>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#163E33' },
  header: { position: 'absolute', left: SIDE, right: SIDE, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  kicker: { color: PALE, fontSize: 11, fontWeight: '700', letterSpacing: 3 },
  title: { color: PALE, fontSize: 13, fontWeight: '400', fontFamily: STRIP_FAMILY },
  strip: { position: 'absolute', flexDirection: 'row', alignItems: 'center', gap: 3 },
  digit: { color: INK, fontSize: 28, fontFamily: STRIP_FAMILY, fontWeight: '600', fontVariant: ['tabular-nums'] },
  hint: {
    position: 'absolute',
    left: SIDE,
    right: SIDE,
    textAlign: 'center',
    color: PALE,
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 0.3,
  },
});
