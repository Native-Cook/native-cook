import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { AppState, Platform, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

// A ticket for a typography seminar whose poster is a Swiss grid that keeps rearranging itself:
// two columns of panels in black, white and signal red, each holding one word set huge and
// turned (upright, sideways, upside down) or the small print. Every two seconds the left column
// slides up one panel and the right one slides down, so the words meet in new pairs. Drag the
// poster up or down to slide the columns yourself.
//
// Each column is a strip of six panels; the window shows two. Panels 4 and 5 repeat panels 0 and
// 1, so after four steps the poster is back where it started. The right strip is read from the
// bottom and moves the other way, 80 ms behind the left.
//
// autoPlay timeline (LOOP = 8 s): four steps of 2 s. In each, hold 1.3 s, then slide one panel
// over 0.7 s with ease-in-out cubic. step(t) = k + ease(seg(t - 2k, 1.3, 2.0)), k = floor(t / 2).
// Dragging: the columns follow the finger (one panel per panel height); release snaps to the
// nearest step over 0.35 s, it stays 2 s, then the loop carries on from that step.

/** Seconds. */
export const LOOP = 8;
const STEP = 2;
const MOVE_START = 1.3;
const LAG = 0.08;
const STEPS = 4;
const HOLD = 2;
const SNAP = 0.35;

const C = {
  paper: '#EFEDE6',
  black: '#0B0B0B',
  white: '#F6F5F0',
  red: '#E8261C',
  ink: '#0B0B0B',
  soft: '#6B6A66',
};

const FAMILY = Platform.select({ ios: 'Helvetica Neue', default: 'sans-serif' });

type Panel = {
  bg: string;
  fg: string;
  word: string;
  /** Degrees: 0, 90, -90 or 180. */
  turn: number;
  size: number;
  /** Where the word sits in the (turned) panel. */
  place: 'start' | 'end';
};

const LEFT: Panel[] = [
  { bg: C.black, fg: C.white, word: 'Typo', turn: 0, size: 76, place: 'end' },
  { bg: C.white, fg: C.black, word: 'Week', turn: -90, size: 80, place: 'end' },
  { bg: C.red, fg: C.white, word: 'Seminar', turn: 90, size: 60, place: 'start' },
  { bg: C.black, fg: C.white, word: '2026', turn: 180, size: 74, place: 'end' },
];
const RIGHT: Panel[] = [
  { bg: C.white, fg: C.black, word: 'in\nMotion', turn: 0, size: 46, place: 'start' },
  { bg: C.red, fg: C.white, word: 'Monday\n13:30–16:30\nRoom H4.07', turn: 0, size: 19, place: 'start' },
  { bg: C.black, fg: C.white, word: 'Typo', turn: 90, size: 76, place: 'end' },
  { bg: C.white, fg: C.black, word: 'Week', turn: 180, size: 76, place: 'start' },
];
// Six panels each: the first two again at the end.
const LEFT_STRIP = [...LEFT, LEFT[0], LEFT[1]];
const RIGHT_STRIP = [...RIGHT, RIGHT[0], RIGHT[1]];

function clamp01(x: number) {
  'worklet';
  return Math.min(1, Math.max(0, x));
}
function easeInOutCubic(x: number) {
  'worklet';
  return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
}
/** The step (fractional while sliding), `t` seconds into the loop. */
function stepAt(t: number) {
  'worklet';
  const u = ((t % LOOP) + LOOP) % LOOP;
  const k = Math.floor(u / STEP);
  return k + easeInOutCubic(clamp01((u - k * STEP - MOVE_START) / (STEP - MOVE_START)));
}
function wrapStep(s: number) {
  'worklet';
  return ((s % STEPS) + STEPS) % STEPS;
}

function PanelView({ panel, width, height }: { panel: Panel; width: number; height: number }) {
  const sideways = panel.turn === 90 || panel.turn === -90;
  const w = sideways ? height : width;
  const h = sideways ? width : height;
  return (
    <View style={{ width, height, backgroundColor: panel.bg, overflow: 'hidden' }}>
      <View
        style={{
          position: 'absolute',
          width: w,
          height: h,
          left: (width - w) / 2,
          top: (height - h) / 2,
          padding: 10,
          justifyContent: panel.place === 'end' ? 'flex-end' : 'flex-start',
          transform: [{ rotate: `${panel.turn}deg` }],
        }}
      >
        <Text
          style={{
            fontFamily: FAMILY,
            fontWeight: panel.size > 30 ? '500' : '600',
            fontSize: panel.size,
            lineHeight: panel.size * (panel.size > 30 ? 0.95 : 1.2),
            letterSpacing: panel.size > 30 ? -panel.size * 0.04 : 0,
            color: panel.fg,
          }}
          numberOfLines={panel.word.split('\n').length}
          adjustsFontSizeToFit
        >
          {panel.word}
        </Text>
      </View>
    </View>
  );
}

type Props = {
  title?: string;
  when?: string;
  action?: string;
  /** Rearranges the poster by itself, every LOOP seconds. */
  autoPlay?: boolean;
};

export function SwissPosterShuffle({
  title = 'Typo Week 2026 · Seminar',
  when = 'Monday, 13:30–16:30 · Room H4.07',
  action = 'Save ticket',
  autoPlay = false,
}: Props) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const [active, setActive] = useState(AppState.currentState === 'active');
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => setActive(state === 'active'));
    return () => sub.remove();
  }, []);

  const posterTop = insets.top + 8;
  const posterHeight = Math.round(height * 0.68 - posterTop);
  const panelH = posterHeight / 2;
  const colW = width / 2;

  const elapsed = useSharedValue(0);
  const origin = useSharedValue(0);
  const left = useSharedValue(0);
  const right = useSharedValue(0);
  const touching = useSharedValue(false);
  const manual = useSharedValue(false);
  const released = useSharedValue(-1);
  const grabbed = useSharedValue(0);
  const from = useSharedValue(0);
  const snapTo = useSharedValue(0);
  const lastStep = useSharedValue(0);

  const frame = useFrameCallback((info) => {
    const dt = (info.timeSincePreviousFrame ?? 0) / 1000;
    elapsed.set(elapsed.get() + dt);
    const now = elapsed.get();
    if (touching.get()) return;
    if (manual.get()) {
      const since = now - released.get();
      const s = since < SNAP ? from.get() + (snapTo.get() - from.get()) * easeInOutCubic(since / SNAP) : snapTo.get();
      left.set(s);
      right.set(s);
      if (since > SNAP + HOLD) {
        manual.set(false);
        origin.set(now - wrapStep(snapTo.get()) * STEP);
      }
      return;
    }
    if (!autoPlay) return;
    const t = now - origin.get();
    left.set(stepAt(t));
    right.set(stepAt(t - LAG));
  }, false);
  useEffect(() => {
    frame.setActive(active && !reduced);
    return () => frame.setActive(false);
  }, [active, reduced, frame]);

  const leftStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -wrapStep(left.get()) * panelH }] }));
  // The right strip is read from the bottom: at step 0 it shows its first two panels at the
  // bottom of the strip, and each step moves it down one panel.
  const rightStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -(STEPS - wrapStep(right.get())) * panelH }] }));

  const haptic = () => {
    if (process.env.EXPO_OS === 'web') return;
    Haptics.selectionAsync().catch(() => {});
  };
  const pan = Gesture.Pan()
    .enabled(!reduced)
    .minDistance(4)
    .onBegin(() => {
      touching.set(true);
      manual.set(true);
      grabbed.set(left.get());
      right.set(left.get());
      lastStep.set(Math.round(left.get()));
    })
    .onUpdate((e) => {
      const s = grabbed.get() - e.translationY / panelH;
      left.set(s);
      right.set(s);
      if (Math.round(s) !== lastStep.get()) {
        lastStep.set(Math.round(s));
        scheduleOnRN(haptic);
      }
    })
    .onFinalize(() => {
      touching.set(false);
      from.set(left.get());
      snapTo.set(Math.round(left.get()));
      released.set(elapsed.get());
    });

  // Bottom-up order for the right strip: panel i sits at row (5 - i).
  const rightRows = [...RIGHT_STRIP].reverse();

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <GestureDetector gesture={pan}>
        <View style={[styles.poster, { top: posterTop, height: posterHeight }]} accessible accessibilityLabel="Typo Week poster. Drag to rearrange.">
          <Animated.View style={[{ position: 'absolute', left: 0, top: 0, width: colW }, leftStyle]}>
            {LEFT_STRIP.map((p, i) => (
              <PanelView key={`l${i}`} panel={p} width={colW} height={panelH} />
            ))}
          </Animated.View>
          <Animated.View style={[{ position: 'absolute', left: colW, top: 0, width: colW }, rightStyle]}>
            {rightRows.map((p, i) => (
              <PanelView key={`r${i}`} panel={p} width={colW} height={panelH} />
            ))}
          </Animated.View>
        </View>
      </GestureDetector>

      <View style={[styles.ticket, { paddingBottom: insets.bottom + 16 }]}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.when}>{when}</Text>
        <View style={styles.button} accessible accessibilityRole="button">
          <Text style={styles.action}>{action}</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: C.paper },
  poster: { position: 'absolute', left: 0, right: 0, overflow: 'hidden', backgroundColor: C.black },
  ticket: { position: 'absolute', left: 24, right: 24, bottom: 0 },
  title: { fontFamily: FAMILY, fontSize: 24, lineHeight: 30, fontWeight: '600', color: C.ink, letterSpacing: -0.4 },
  when: { marginTop: 6, fontFamily: FAMILY, fontSize: 15, lineHeight: 20, color: C.soft },
  button: { marginTop: 18, height: 54, borderRadius: 4, backgroundColor: C.ink, alignItems: 'center', justifyContent: 'center' },
  action: { fontFamily: FAMILY, fontSize: 17, fontWeight: '600', color: '#FFFFFF' },
});
