import * as Haptics from "expo-haptics";
import { LinearGradient } from "expo-linear-gradient";
import { StatusBar } from "expo-status-bar";
import { SymbolView } from "expo-symbols";
import { useCallback, useEffect } from "react";
import { StyleSheet, Text, TextInput, useWindowDimensions, View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue, withSpring, withTiming } from "react-native-reanimated";
import type { SharedValue } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { scheduleOnRN } from "react-native-worklets";

import { useReducedMotionPreference } from "@/shared/useReducedMotionPreference";

import {
  G_THROW,
  ghostAt,
  liftAt,
  LOOP,
  paperAlpha,
  PHASE,
  REDUCED_T,
  sendLitAt,
  stageAlpha,
  THROW_MAX,
  typedCount,
  wordsAlpha,
} from "./planeMotion";
import { APP, BG, BG_TOP, BUBBLE_OUT, CONTACT, CONTACT_INITIALS, FIELD, FIELD_EDGE, INK, MESSAGE, MUTE, PAPER, PAPER_INK, SHEET_L, SHEET_W } from "./planeScene";
import { PlaneShadow } from "./PlaneShadow";
import { PlaneStage3D } from "./PlaneStage3D";
import { BUBBLE_H, GUTTER, PlaneThread, RECEIPT_H } from "./PlaneThread";

Animated.addWhitelistedNativeProps({ text: true });
const AnimatedTextInput = Animated.createAnimatedComponent(TextInput);

/** The composer: the field's height and radius, the send button's size, the gap between them, the row's bottom margin. */
const FIELD_H = 40;
const FIELD_R = 20;
const SEND = 36;
const SEND_GAP = 8;
const ROW_BOTTOM = 10;
/** The field's text inset (the 2D paper's words start at the same place). */
const TEXT_X = 14;
const TEXT_LINE = 21;
/** The thread rests this far above the composer; the sheet hangs this far above it. */
const THREAD_GAP = 16;
const SHEET_GAP = 44;
/** The sheet's width on screen, and its corner radius as flat paper. */
const SHEET_PT_MAX = 200;
const PAPER_R = 1.5;
const DOT = 44;
/** A drag up on the send button: how far it follows (pt), and what counts as a throw. */
const PULL_MAX = 84;
const THROW_PT = 28;
const THROW_V = 500;
const THROW_V_FULL = 3000;
/** Frames between two publications of the geometry to the Filament thread. */
const GEO_BEAT = 20;
/** A frame that arrives later than this (ms) is a stall: the film's clock does not count it. */
const STALL_MS = 100;
const FRAME_MS = 1000 / 60;

function sendTap(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function landTap(): void {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Soft);
}

/** Publishes the film's clock for the Filament thread (only when it changes): [1 while it runs, the UI frame time
 * (ms) of its t = 0]. */
function syncAnchor(anchor: SharedValue<number[]>, playing: number, start: number): void {
  "worklet";
  const a = anchor.get();
  if (a[0] !== playing || a[1] !== start) anchor.set([playing, start]);
}

/** The geometry with another throw size (a new array: the Filament thread gets it through the bridge). */
function withThrow(g: number[], amp: number): number[] {
  "worklet";
  return [g[0], g[1], g[2], g[3], g[4], g[5], g[6], amp];
}

export type PaperPlaneSendProps = {
  autoPlay?: boolean;
};

export function PaperPlaneSend({ autoPlay = true }: PaperPlaneSendProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = !reduced && autoPlay;

  // the composer, the sheet above it, and the place the new message takes in the thread
  const fieldY = height - insets.bottom - ROW_BOTTOM - FIELD_H;
  const fieldW = width - GUTTER * 2 - SEND - SEND_GAP;
  const sendX = width - GUTTER - SEND;
  const sendY = fieldY + (FIELD_H - SEND) / 2;
  const sheetPt = Math.min(SHEET_PT_MAX, width * 0.5);
  const sheetH = (sheetPt * SHEET_W) / SHEET_L;
  const sheetCx = width / 2;
  const sheetCy = fieldY - SHEET_GAP - sheetH / 2;
  const threadBottom = fieldY - THREAD_GAP;
  const slotCy = threadBottom - RECEIPT_H - BUBBLE_H / 2;

  const t = useSharedValue(reduced ? REDUCED_T : 0);
  const anchor = useSharedValue<number[]>([0, -1]);
  const slotW = useSharedValue(width * 0.66);
  const geo = useSharedValue<number[]>([width, height, sheetCx, sheetCy, width - GUTTER - width * 0.33, slotCy, sheetPt, 1]);
  const loopStart = useSharedValue(-1);
  const running = useSharedValue(film);
  const ready = useSharedValue(false);
  const nowMs = useSharedValue(-1);
  const beat = useSharedValue(0);
  /** A finger on the send button: pressed 0..1, and how far it is pulled up (pt, negative). */
  const pressed = useSharedValue(0);
  const pull = useSharedValue(0);

  useEffect(() => {
    geo.set([width, height, sheetCx, sheetCy, width - GUTTER - slotW.get() / 2, slotCy, sheetPt, 1]);
  }, [geo, height, sheetCx, sheetCy, sheetPt, slotCy, slotW, width]);
  const onSlotWidth = useCallback(
    (w: number) => {
      slotW.set(w);
      geo.set([width, height, sheetCx, sheetCy, width - GUTTER - w / 2, slotCy, sheetPt, 1]);
    },
    [geo, height, sheetCx, sheetCy, sheetPt, slotCy, slotW, width],
  );

  useFrameCallback((frame) => {
    "worklet";
    const now = frame.timestamp;
    const gap = nowMs.get() < 0 ? 0 : now - nowMs.get();
    nowMs.set(now);
    // The Filament thread hears of the geometry through a listener that is added a moment after mount, so a value set
    // before that (the first layout) would never arrive: publish it again a few times a second (a new array).
    beat.set(beat.get() + 1);
    if (beat.get() % GEO_BEAT === 0) {
      const g0 = geo.get();
      geo.set(withThrow(g0, g0[G_THROW]));
    }
    if (reduced) {
      // the message delivered, still
      if (t.get() !== REDUCED_T) t.set(REDUCED_T);
      syncAnchor(anchor, 0, -1);
      return;
    }
    if (!ready.get() || !running.get()) {
      if (t.get() !== 0) t.set(0);
      syncAnchor(anchor, 0, -1);
      loopStart.set(-1);
      return;
    }
    if (loopStart.get() < 0) {
      loopStart.set(now);
      // every start of the film publishes the geometry again
      geo.set(withThrow(geo.get(), geo.get()[G_THROW]));
    } else if (gap > STALL_MS) {
      // the UI thread was held up (the first haptic, a model still loading, a debugger): the film picks up where it
      // was instead of skipping what the stall covered (the fold used to be lost this way)
      loopStart.set(loopStart.get() + gap - FRAME_MS);
    }
    let el = (now - loopStart.get()) / 1000;
    if (el >= LOOP) {
      // the loop's seam: a throw's bigger arc is over; without autoplay the film stops here
      loopStart.set(loopStart.get() + LOOP * 1000);
      el -= LOOP;
      // the geometry goes out again at every seam
      geo.set(withThrow(geo.get(), 1));
      if (!film) {
        running.set(false);
        t.set(0);
        syncAnchor(anchor, 0, -1);
        loopStart.set(-1);
        return;
      }
    }
    syncAnchor(anchor, 1, loopStart.get());
    t.set(el);
  });

  // haptics: the send, and the message landing
  useAnimatedReaction(
    () => t.get(),
    (cur, prev) => {
      if (prev == null || reduced) return;
      if (prev < PHASE.tap && cur >= PHASE.tap && cur < PHASE.lift) scheduleOnRN(sendTap);
      if (prev < PHASE.pop && cur >= PHASE.pop) scheduleOnRN(landTap);
    },
  );

  /** A finger sends: the clock jumps to the moment the message leaves the field. `amp` is the throw's size. */
  const sendNow = useCallback(
    (amp: number) => {
      "worklet";
      if (reduced || !ready.get() || nowMs.get() < 0) return;
      const tt = t.get();
      // already on its way (or the receipt is still up): let it finish
      if (running.get() && tt >= PHASE.lift && tt < PHASE.resetEnd) return;
      geo.set(withThrow(geo.get(), amp));
      loopStart.set(nowMs.get() - PHASE.lift * 1000);
      running.set(true);
      scheduleOnRN(sendTap);
    },
    [geo, loopStart, nowMs, ready, reduced, running, t],
  );

  const tap = Gesture.Tap()
    .maxDuration(400)
    .onBegin(() => {
      "worklet";
      pressed.set(withTiming(1, { duration: 90 }));
    })
    .onEnd((_e, success) => {
      "worklet";
      if (success) sendNow(1);
    })
    .onFinalize(() => {
      "worklet";
      pressed.set(withTiming(0, { duration: 160 }));
    });

  const pan = Gesture.Pan()
    .minDistance(8)
    .onUpdate((e) => {
      "worklet";
      // the button follows the finger up, rubber-banded
      const up = e.translationY < 0 ? -e.translationY : 0;
      pull.set(-PULL_MAX * (1 - 1 / (up / PULL_MAX + 1)));
    })
    .onEnd((e) => {
      "worklet";
      if (e.translationY < -THROW_PT || e.velocityY < -THROW_V) {
        // a throw: the harder the flick, the bigger (and so the faster) the flight
        const k = Math.min(1, Math.max(0, (-e.velocityY - THROW_V) / (THROW_V_FULL - THROW_V)));
        sendNow(1 + (THROW_MAX - 1) * k);
      }
      pull.set(withSpring(0, { mass: 1, stiffness: 260, damping: 15, velocity: e.velocityY }));
    })
    .onFinalize(() => {
      "worklet";
      pressed.set(withTiming(0, { duration: 160 }));
    });

  const gesture = Gesture.Race(pan, tap);

  const onReady = useCallback(() => {
    ready.set(true);
  }, [ready]);

  // the field: the message types itself into the value (never focused, so no keyboard)
  const typed = useAnimatedProps(() => {
    const v = MESSAGE.slice(0, typedCount(t.get()));
    return { text: v, defaultValue: v } as never;
  });
  const placeholder = useAnimatedStyle(() => ({ opacity: typedCount(t.get()) === 0 ? 1 : 0 }));

  const sendStyle = useAnimatedStyle(() => {
    const g = [0, 0];
    ghostAt(t.get(), g);
    const p = Math.max(pressed.get(), g[1]);
    return { transform: [{ translateY: pull.get() }, { scale: 1 - 0.1 * p }] };
  });
  const sendLit = useAnimatedStyle(() => ({ opacity: sendLitAt(t.get()) }));
  const dot = useAnimatedStyle(() => {
    const g = [0, 0];
    ghostAt(t.get(), g);
    return { opacity: g[0] * 0.9, transform: [{ scale: 1 - 0.16 * g[1] }] };
  });

  // the 2D paper: from the field's rectangle to the sheet's (size and corners change for 0.4 s, on the UI thread)
  const paper = useAnimatedStyle(() => {
    const tt = t.get();
    const k = liftAt(tt);
    const w = fieldW + (sheetPt - fieldW) * k;
    const h = FIELD_H + (sheetH - FIELD_H) * k;
    const cx = GUTTER + fieldW / 2 + (sheetCx - GUTTER - fieldW / 2) * k;
    const cy = fieldY + FIELD_H / 2 + (sheetCy - fieldY - FIELD_H / 2) * k;
    return {
      opacity: paperAlpha(tt),
      left: cx - w / 2,
      top: cy - h / 2,
      width: w,
      height: h,
      borderRadius: FIELD_R + (PAPER_R - FIELD_R) * k,
      shadowOpacity: 0.1 + 0.3 * k,
      shadowRadius: 4 + 14 * k,
    };
  });
  const words = useAnimatedStyle(() => ({ opacity: wordsAlpha(t.get()) }));
  const stage = useAnimatedStyle(() => ({ opacity: stageAlpha(t.get()) }));

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <LinearGradient colors={[BG_TOP, BG]} pointerEvents="none" style={StyleSheet.absoluteFill} />

      <View pointerEvents="none" style={[styles.header, { top: insets.top + 6 }]}>
        <LinearGradient colors={["#F2B36B", "#E0764A", "#B9483C"]} end={{ x: 1, y: 1 }} start={{ x: 0, y: 0 }} style={styles.avatar}>
          <Text style={styles.initials}>{CONTACT_INITIALS}</Text>
        </LinearGradient>
        <View style={styles.who}>
          <Text style={styles.name}>{CONTACT}</Text>
          <Text style={styles.status}>Active now</Text>
        </View>
        <Text style={styles.app}>{APP}</Text>
      </View>
      <View pointerEvents="none" style={[styles.rule, { top: insets.top + 56 }]} />

      <PlaneThread bottom={height - threadBottom} onSlotWidth={onSlotWidth} t={t} />

      {/* the composer */}
      <View pointerEvents="none" style={[styles.field, { left: GUTTER, top: fieldY, width: fieldW }]}>
        <Animated.Text style={[styles.placeholder, placeholder]}>Message</Animated.Text>
        <AnimatedTextInput animatedProps={typed} editable={false} style={styles.input} underlineColorAndroid="transparent" />
      </View>
      <GestureDetector gesture={gesture}>
        <Animated.View
          accessibilityHint="Sends the message as a paper plane; drag up to throw it"
          accessibilityLabel="Send"
          accessibilityRole="button"
          hitSlop={10}
          style={[styles.send, { left: sendX, top: sendY }, sendStyle]}
        >
          <Animated.View style={[styles.sendLit, sendLit]} />
          <SymbolView fallback={<Text style={styles.arrow}>↑</Text>} name="arrow.up" size={17} tintColor="#FFFFFF" weight="bold" />
        </Animated.View>
      </GestureDetector>

      {/* the folding paper's shadow on the chat: under the paper, 2D and 3D */}
      <PlaneShadow geo={geo} height={height} t={t} width={width} />

      {/* the message as flat paper, until the 3D sheet takes over */}
      <Animated.View pointerEvents="none" style={[styles.paper, paper]}>
        <Animated.Text numberOfLines={1} style={[styles.words, words]}>
          {MESSAGE}
        </Animated.Text>
      </Animated.View>

      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, stage]}>
        <PlaneStage3D anchor={anchor} geo={geo} height={height} onReady={onReady} t={t} width={width} />
      </Animated.View>

      <Animated.View pointerEvents="none" style={[styles.dot, { left: sendX + SEND / 2 - DOT / 2, top: sendY + SEND / 2 - DOT / 2 }, dot]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BG },
  header: { position: "absolute", left: GUTTER, right: GUTTER, height: 44, flexDirection: "row", alignItems: "center" },
  avatar: { width: 36, height: 36, borderRadius: 18, alignItems: "center", justifyContent: "center" },
  initials: { fontSize: 14, fontWeight: "700", color: "#FFF7EE" },
  who: { marginLeft: 10, flex: 1 },
  name: { fontSize: 16, lineHeight: 20, fontWeight: "600", color: INK },
  status: { fontSize: 13, lineHeight: 17, color: MUTE },
  app: { fontSize: 13, fontWeight: "600", color: INK, opacity: 0.8 },
  rule: { position: "absolute", left: 0, right: 0, height: StyleSheet.hairlineWidth, backgroundColor: "rgba(255,255,255,0.08)" },
  field: {
    position: "absolute",
    height: FIELD_H,
    borderRadius: FIELD_R,
    backgroundColor: FIELD,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: FIELD_EDGE,
    justifyContent: "center",
  },
  placeholder: { position: "absolute", left: TEXT_X, fontSize: 16, lineHeight: TEXT_LINE, color: MUTE },
  input: { height: FIELD_H, paddingHorizontal: TEXT_X, paddingVertical: 0, fontSize: 16, color: INK },
  send: {
    position: "absolute",
    width: SEND,
    height: SEND,
    borderRadius: SEND / 2,
    backgroundColor: "#363B46",
    alignItems: "center",
    justifyContent: "center",
    // the button's contact shadow
    shadowColor: "#000000",
    shadowOpacity: 0.35,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
  },
  sendLit: { position: "absolute", left: 0, top: 0, right: 0, bottom: 0, borderRadius: SEND / 2, backgroundColor: BUBBLE_OUT, borderWidth: StyleSheet.hairlineWidth, borderColor: "rgba(255,255,255,0.28)" },
  arrow: { fontSize: 18, fontWeight: "700", color: "#FFFFFF" },
  paper: {
    position: "absolute",
    backgroundColor: PAPER,
    shadowColor: "#000000",
    shadowOffset: { width: 4, height: 12 },
  },
  words: { position: "absolute", left: TEXT_X, top: (FIELD_H - TEXT_LINE) / 2, fontSize: 16, lineHeight: TEXT_LINE, color: PAPER_INK },
  dot: {
    position: "absolute",
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: "rgba(255,255,255,0.3)",
    borderWidth: 1.5,
    borderColor: "rgba(255,255,255,0.66)",
  },
});
