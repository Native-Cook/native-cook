// PeekPreview: long-press an item and it lifts into a preview card; swipe up and the card becomes the page.
// Reusable: this file knows nothing about articles. It owns the state, the gestures, the frame that morphs
// between the three rects, the blurred backdrop and the quick actions. What is drawn inside the frame is the
// caller's, driven by the same shared values (see PeekPopPreview.tsx).
import { BlurView } from 'expo-blur';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View, type ViewProps } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withDecay,
  withDelay,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  ACTION_AT,
  ACTION_RISE,
  clamp,
  CLOSE_FOLLOW,
  CLOSE_SPRING,
  dampingOf,
  DISMISS_PULL,
  DISMISS_VELOCITY,
  easeOutCubic,
  mix,
  mix3,
  PANEL_RISE,
  PANEL_SCALE,
  PEEK_SPRING,
  POP_COMMIT,
  POP_DOWN_RUBBER,
  POP_DOWN_SPAN,
  POP_SPRING,
  POP_UP_SPAN,
  POP_VELOCITY,
  PRESS,
  PRESS_RELEASE,
  PRESS_SCALE,
  RADIUS_CARD,
  RADIUS_ROW,
  REDUCED_MS,
  REVEAL_IN,
  REVEAL_OUT,
  REVEAL_SPAN,
  rubber,
  rubberSlope,
  SCENE_BLUR,
  SCENE_DIM,
  SCENE_SCALE,
  SCROLL_RUBBER,
  seg,
  SINK_CLOSE,
  SINK_RUBBER,
  SINK_SPAN,
  SINK_VELOCITY,
  stiffnessOf,
} from './motion';

export type Rect = { x: number; y: number; width: number; height: number };
/** Where an item rests, where its preview floats and what the open page fills. */
export type PeekGeometry = { rows: Rect[]; card: Rect; full: Rect };

export type PeekState = {
  /** The item that is out, or -1. */
  active: SharedValue<number>;
  press: SharedValue<number>;
  peek: SharedValue<number>;
  sink: SharedValue<number>;
  pop: SharedValue<number>;
  reveal: SharedValue<number>;
  scroll: SharedValue<number>;
  /** How far the open page can scroll; the caller sets it from its content. */
  maxScroll: SharedValue<number>;
  /** 1 while the preview is meant to be up (drives `reveal`). */
  wants: SharedValue<number>;
  /** Fingers down on a trigger or the frame. */
  held: SharedValue<number>;
  /** Counts touches, so a caller's autoplay can tell a finger arrived. */
  touches: SharedValue<number>;
  /** 1 while a script writes the values above; the first touch clears it. */
  scripted: SharedValue<number>;
};

/** What a release decided. */
export const STAYED = 0;
export const POPPED = 1;
export const CLOSED = 2;

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

function drive(target: number, spring: { response: number; ratio: number }, velocity: number, reduced: boolean) {
  'worklet';
  if (reduced) return withTiming(target, { duration: REDUCED_MS });
  return withSpring(target, { stiffness: stiffnessOf(spring.response), damping: dampingOf(spring.response, spring.ratio), mass: 1, velocity });
}

/** The row's preview comes up (after the long press). */
export function peekOpen(s: PeekState, reduced: boolean) {
  'worklet';
  s.wants.set(1);
  s.sink.set(0);
  s.pop.set(0);
  s.peek.set(drive(1, PEEK_SPRING, 0, reduced));
  s.press.set(withDelay(PRESS_RELEASE[0] * 1000, withTiming(0, { duration: (PRESS_RELEASE[1] - PRESS_RELEASE[0]) * 1000 })));
}

/** The preview settles where it floats. */
export function peekStay(s: PeekState, reduced: boolean) {
  'worklet';
  s.wants.set(1);
  s.peek.set(drive(1, PEEK_SPRING, 0, reduced));
  s.sink.set(drive(0, PEEK_SPRING, 0, reduced));
  s.pop.set(drive(0, CLOSE_SPRING, 0, reduced));
}

/** The preview becomes the page. `velocity` is pop per second. */
export function peekPop(s: PeekState, velocity: number, reduced: boolean) {
  'worklet';
  s.wants.set(1);
  s.press.set(withTiming(0, { duration: 120 }));
  s.sink.set(drive(0, PEEK_SPRING, 0, reduced));
  s.peek.set(drive(1, PEEK_SPRING, 0, reduced));
  s.pop.set(drive(1, POP_SPRING, velocity, reduced));
}

/** Everything goes back into the row, along the path it came. `velocity` is per second, negative toward the row. */
export function peekClose(s: PeekState, velocity: number, reduced: boolean) {
  'worklet';
  s.wants.set(0);
  const fromPage = s.pop.get() > 0.2;
  s.press.set(withTiming(0, { duration: 120 }));
  s.scroll.set(withTiming(0, { duration: 260 }));
  s.sink.set(drive(0, CLOSE_SPRING, 0, reduced));
  s.pop.set(drive(0, CLOSE_SPRING, fromPage ? velocity : 0, reduced));
  if (fromPage && !reduced) s.peek.set(withDelay(CLOSE_FOLLOW * 1000, drive(0, CLOSE_SPRING, 0, reduced)));
  else s.peek.set(drive(0, CLOSE_SPRING, fromPage ? 0 : velocity, reduced));
}

/** A script was driving and a finger arrived: carry on from wherever it was to the nearest resting state. */
export function peekSettle(s: PeekState, reduced: boolean) {
  'worklet';
  if (s.active.get() < 0) return;
  if (s.pop.get() > 0.5) peekPop(s, 0, reduced);
  else if (s.peek.get() - s.sink.get() > 0.5) peekStay(s, reduced);
  else peekClose(s, 0, reduced);
}

/** Call from every gesture's onBegin. */
export function peekTouch(s: PeekState, reduced: boolean) {
  'worklet';
  s.touches.set(s.touches.get() + 1);
  if (s.scripted.get() === 1) {
    s.scripted.set(0);
    peekSettle(s, reduced);
  }
}

/** While peeking, the finger's vertical travel: up pops, down sinks the preview back toward its row. */
export function peekDrag(s: PeekState, travel: number) {
  'worklet';
  if (travel < 0) {
    s.sink.set(0);
    s.pop.set(Math.min(-travel / POP_UP_SPAN, 1.02));
  } else {
    s.pop.set(0);
    s.sink.set(rubber(travel, SINK_RUBBER) / SINK_SPAN);
  }
}

export function peekRelease(s: PeekState, travel: number, velocity: number, reduced: boolean) {
  'worklet';
  if (s.pop.get() > POP_COMMIT || (travel < -12 && velocity < -POP_VELOCITY)) {
    peekPop(s, -velocity / POP_UP_SPAN, reduced);
    return POPPED;
  }
  if (travel > SINK_CLOSE || velocity > SINK_VELOCITY) {
    peekClose(s, (-velocity * rubberSlope(Math.max(travel, 0), SINK_RUBBER)) / SINK_SPAN, reduced);
    return CLOSED;
  }
  peekStay(s, reduced);
  return STAYED;
}

/** On the open page: the finger scrolls it, and past the top pulls the page back toward the preview. */
export function pageDrag(s: PeekState, scrollAtGrab: number, travel: number) {
  'worklet';
  const max = s.maxScroll.get();
  const want = scrollAtGrab - travel;
  if (want >= 0) {
    s.scroll.set(want <= max ? want : max + rubber(want - max, SCROLL_RUBBER));
    s.pop.set(1);
  } else {
    s.scroll.set(0);
    s.pop.set(1 - rubber(-want, POP_DOWN_RUBBER) / POP_DOWN_SPAN);
  }
}

export function pageRelease(s: PeekState, scrollAtGrab: number, travel: number, velocity: number, reduced: boolean) {
  'worklet';
  const max = s.maxScroll.get();
  const want = scrollAtGrab - travel;
  if (want < 0) {
    if (-want > DISMISS_PULL || velocity > DISMISS_VELOCITY) {
      peekClose(s, (-velocity * rubberSlope(-want, POP_DOWN_RUBBER)) / POP_DOWN_SPAN, reduced);
      return CLOSED;
    }
    s.pop.set(drive(1, POP_SPRING, 0, reduced));
    return STAYED;
  }
  if (want > max) s.scroll.set(drive(max, CLOSE_SPRING, 0, reduced));
  else if (max > 0) s.scroll.set(withDecay({ velocity: -velocity, clamp: [0, max] }));
  return STAYED;
}

/** The state, and the two things that tick by themselves: the reveal clock and the reset once all is back. */
export function usePeekPreview(): PeekState {
  const active = useSharedValue(-1);
  const press = useSharedValue(0);
  const peek = useSharedValue(0);
  const sink = useSharedValue(0);
  const pop = useSharedValue(0);
  const reveal = useSharedValue(0);
  const scroll = useSharedValue(0);
  const maxScroll = useSharedValue(0);
  const wants = useSharedValue(0);
  const held = useSharedValue(0);
  const touches = useSharedValue(0);
  const scripted = useSharedValue(0);

  useFrameCallback((frame) => {
    if (scripted.get() === 1) return;
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    const now = reveal.get();
    const up = wants.get() === 1 && sink.get() < 0.08;
    const next = up ? Math.min(1, now + dt / REVEAL_IN) : Math.max(0, now - dt / REVEAL_OUT);
    if (next !== now) reveal.set(next);
    if (active.get() >= 0 && held.get() === 0 && wants.get() === 0 && peek.get() < 0.002 && pop.get() < 0.002 && press.get() < 0.002) {
      active.set(-1);
      press.set(0);
      peek.set(0);
      sink.set(0);
      pop.set(0);
      scroll.set(0);
    }
  });

  return { active, press, peek, sink, pop, reveal, scroll, maxScroll, wants, held, touches, scripted };
}

/** Everything behind the preview: it sinks back a little as the preview comes up. */
export function PeekScene({ state, children }: { state: PeekState; children: ReactNode }) {
  const { peek, sink } = state;
  const sunk = useAnimatedStyle(() => ({ transform: [{ scale: mix(1, SCENE_SCALE, clamp(peek.get() - sink.get(), 0, 1)) }] }));
  return <Animated.View style={[StyleSheet.absoluteFill, sunk]}>{children}</Animated.View>;
}

/**
 * An item that can be peeked. Press and hold: it swells, then its preview comes up under the finger; keep the
 * finger down and swipe up to pop, or pull down to let it fall back. A tap opens the page directly.
 */
export function PeekTrigger({
  state,
  index,
  rect,
  reducedMotion,
  onPeek,
  onPop,
  onClose,
  accessibilityLabel,
  children,
}: {
  state: PeekState;
  index: number;
  rect: Rect;
  reducedMotion: boolean;
  onPeek?: () => void;
  onPop?: () => void;
  onClose?: () => void;
  accessibilityLabel?: string;
  children: ReactNode;
}) {
  const { active, press, peek, sink, pop, reveal, wants, held } = state;
  const peekHaptic = onPeek ?? noop;
  const popHaptic = onPop ?? noop;
  const closeHaptic = onClose ?? noop;

  const pan = Gesture.Pan()
    .activateAfterLongPress(PRESS * 1000)
    .maxPointers(1)
    .onBegin(() => {
      peekTouch(state, reducedMotion);
      held.set(held.get() + 1);
      if (active.get() !== index) {
        // Another item was still on its way home: it is home now.
        peek.set(0);
        sink.set(0);
        pop.set(0);
        reveal.set(0);
      }
      active.set(index);
      wants.set(0);
      press.set(withTiming(1, { duration: PRESS * 1000, easing: Easing.out(Easing.cubic) }));
    })
    .onStart(() => {
      peekOpen(state, reducedMotion);
      scheduleOnRN(peekHaptic);
    })
    .onUpdate((event) => {
      peekDrag(state, event.translationY);
    })
    .onEnd((event) => {
      const result = peekRelease(state, event.translationY, event.velocityY, reducedMotion);
      if (result === POPPED) scheduleOnRN(popHaptic);
      else if (result === CLOSED) scheduleOnRN(closeHaptic);
    })
    .onFinalize((_event, success) => {
      held.set(Math.max(0, held.get() - 1));
      if (!success && wants.get() === 0) press.set(withTiming(0, { duration: 140 }));
    });
  const tap = Gesture.Tap()
    .maxDuration(PRESS * 1000 - 40)
    .onEnd((_event, success) => {
      if (!success) return;
      active.set(index);
      peekPop(state, 0, reducedMotion);
      scheduleOnRN(popHaptic);
    });
  const gesture = Gesture.Simultaneous(pan, tap);

  // While this item is out, the frame is drawing it.
  const hidden = useAnimatedStyle(() => ({ opacity: active.get() === index ? 0 : 1 }));

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        accessible
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        accessibilityHint="Opens the article. Touch and hold for a preview."
        style={[{ position: 'absolute', left: rect.x, top: rect.y, width: rect.width, height: rect.height }, hidden]}>
        {children}
      </Animated.View>
    </GestureDetector>
  );
}

function noop() {}

/** Blur, dim and a tap-to-close layer between the scene and the preview. */
export function PeekBackdrop({ state, reducedMotion, tint = '#14110d', onClose }: { state: PeekState; reducedMotion: boolean; tint?: string; onClose?: () => void }) {
  const { active, peek, sink, pop } = state;
  const closeHaptic = onClose ?? noop;
  // Nothing behind the open page can be seen, so the blur is let go once it fills the screen.
  const blurProps = useAnimatedProps(() => ({ intensity: SCENE_BLUR * clamp(peek.get() - sink.get(), 0, 1) * (1 - seg(pop.get(), 0.9, 1)) }));
  const dim = useAnimatedStyle(() => ({ opacity: SCENE_DIM * clamp(peek.get() - sink.get(), 0, 1) }));
  const touchProps = useAnimatedProps<ViewProps>(() => ({
    pointerEvents: active.get() >= 0 && (peek.get() - sink.get() > 0.3 || pop.get() > 0.3) ? 'auto' : 'none',
  }));
  const tap = Gesture.Tap()
    .onBegin(() => {
      peekTouch(state, reducedMotion);
    })
    .onEnd((_event, success) => {
      if (!success) return;
      peekClose(state, 0, reducedMotion);
      scheduleOnRN(closeHaptic);
    });
  return (
    <>
      <AnimatedBlurView pointerEvents="none" tint="light" style={StyleSheet.absoluteFill} animatedProps={blurProps} />
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: tint }, dim]} />
      <GestureDetector gesture={tap}>
        <Animated.View style={StyleSheet.absoluteFill} animatedProps={touchProps} accessibilityRole="button" accessibilityLabel="Close preview" />
      </GestureDetector>
    </>
  );
}

/**
 * The frame: one rounded, shadowed box that is the row, then the preview card, then the page. Its children are
 * clipped to it. Drag it: while peeking, up pops and down falls back; on the page it scrolls, and a pull down
 * from the top sends the page back to its row.
 */
export function PeekFrame({
  state,
  geometry,
  reducedMotion,
  backgroundColor,
  restShadow,
  liftShadow,
  onPop,
  onClose,
  children,
}: {
  state: PeekState;
  geometry: PeekGeometry;
  reducedMotion: boolean;
  backgroundColor: string;
  /** The row's own shadow, and the one that fades in as it lifts (CSS box-shadow strings). */
  restShadow: string;
  liftShadow: string;
  onPop?: () => void;
  onClose?: () => void;
  children: ReactNode;
}) {
  const { active, press, peek, sink, pop, scroll, held } = state;
  const { rows, card, full } = geometry;
  const popHaptic = onPop ?? noop;
  const closeHaptic = onClose ?? noop;
  const onPage = useSharedValue(0);
  const scrollAtGrab = useSharedValue(0);

  const pan = Gesture.Pan()
    .activeOffsetY([-6, 6])
    .maxPointers(1)
    .onBegin(() => {
      peekTouch(state, reducedMotion);
      held.set(held.get() + 1);
      onPage.set(pop.get() > 0.5 ? 1 : 0);
      cancelAnimation(scroll);
      scrollAtGrab.set(scroll.get());
    })
    .onUpdate((event) => {
      if (onPage.get() === 1) pageDrag(state, scrollAtGrab.get(), event.translationY);
      else peekDrag(state, event.translationY);
    })
    .onEnd((event) => {
      const result =
        onPage.get() === 1
          ? pageRelease(state, scrollAtGrab.get(), event.translationY, event.velocityY, reducedMotion)
          : peekRelease(state, event.translationY, event.velocityY, reducedMotion);
      if (result === POPPED) scheduleOnRN(popHaptic);
      else if (result === CLOSED) scheduleOnRN(closeHaptic);
    })
    .onFinalize(() => {
      held.set(Math.max(0, held.get() - 1));
    });
  // A tap on the preview opens it.
  const tap = Gesture.Tap().onEnd((_event, success) => {
    if (!success || pop.get() > 0.5 || peek.get() - sink.get() < 0.5) return;
    peekPop(state, 0, reducedMotion);
    scheduleOnRN(popHaptic);
  });
  const gesture = Gesture.Exclusive(pan, tap);

  const frame = useAnimatedStyle(() => {
    const index = active.get();
    const row = rows[Math.max(index, 0)] ?? card;
    const pk = peek.get() - sink.get();
    const pp = pop.get();
    return {
      opacity: index < 0 ? 0 : 1,
      width: Math.max(1, mix3(row.width, card.width, full.width, pk, pp)),
      height: Math.max(1, mix3(row.height, card.height, full.height, pk, pp)),
      transform: [
        { translateX: mix3(row.x, card.x, full.x, pk, pp) },
        { translateY: mix3(row.y, card.y, full.y, pk, pp) },
        // The long press swells the row, content and all, about its centre.
        { scale: 1 + PRESS_SCALE * press.get() },
      ],
    };
  });
  const corners = useAnimatedStyle(() => ({
    borderRadius: Math.max(0, mix3(RADIUS_ROW, RADIUS_CARD, 0, clamp(peek.get() - sink.get(), 0, 1), clamp(pop.get(), 0, 1))),
  }));
  // Lift: the far shadow grows with the press and the peek, and is not needed once the page fills the screen.
  const lifted = useAnimatedStyle(() => ({
    opacity: clamp(Math.max(press.get(), peek.get() - sink.get()), 0, 1) * (1 - seg(pop.get(), 0.7, 1)),
  }));
  const touchProps = useAnimatedProps<ViewProps>(() => ({
    pointerEvents: active.get() >= 0 && (peek.get() - sink.get() > 0.3 || pop.get() > 0.3) ? 'auto' : 'none',
  }));

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View style={[styles.frame, frame]} animatedProps={touchProps}>
        <Animated.View style={[StyleSheet.absoluteFill, styles.curve, { backgroundColor, boxShadow: liftShadow }, corners, lifted]} />
        <Animated.View style={[StyleSheet.absoluteFill, styles.curve, { backgroundColor, boxShadow: restShadow }, corners]} />
        <Animated.View style={[StyleSheet.absoluteFill, styles.curve, styles.clip, { backgroundColor }, corners]}>{children}</Animated.View>
      </Animated.View>
    </GestureDetector>
  );
}

export type PeekAction = { key: string; label: string; symbol: SymbolViewProps['name']; destructive?: boolean };

/** The quick actions under the preview: a panel that rises in, its rows a beat apart. */
export function PeekActions({
  state,
  anchor,
  width,
  actions,
  rowHeight = 46,
  ink = '#1A1B1E',
  danger = '#D23A2E',
  backgroundColor = '#FDFCF9',
  onAction,
}: {
  state: PeekState;
  /** Top-left corner of the panel. */
  anchor: { x: number; y: number };
  width: number;
  actions: readonly PeekAction[];
  rowHeight?: number;
  ink?: string;
  danger?: string;
  backgroundColor?: string;
  onAction: (key: string) => void;
}) {
  const { active, peek, sink, pop, reveal } = state;
  const panel = useAnimatedStyle(() => {
    const pk = clamp(peek.get() - sink.get(), 0, 1);
    const gone = seg(pop.get(), 0, 0.35);
    const u = easeOutCubic(seg(reveal.get(), ACTION_AT[0] - 0.12, ACTION_AT[0] - 0.12 + REVEAL_SPAN)) * seg(pk, 0.5, 0.85);
    return {
      opacity: active.get() < 0 ? 0 : u * (1 - gone),
      transform: [{ translateY: -PANEL_RISE * (1 - u) + 36 * gone }, { scale: mix(PANEL_SCALE, 1, u) }],
    };
  });
  const touchProps = useAnimatedProps<ViewProps>(() => ({
    pointerEvents: active.get() >= 0 && reveal.get() > 0.9 && pop.get() < 0.1 ? 'auto' : 'none',
  }));
  return (
    <Animated.View
      style={[styles.panel, { left: anchor.x, top: anchor.y, width, backgroundColor, transformOrigin: 'left top' }, panel]}
      animatedProps={touchProps}>
      {actions.map((action, i) => (
        <ActionRow key={action.key} action={action} index={i} height={rowHeight} tint={action.destructive ? danger : ink} reveal={reveal} onPress={() => onAction(action.key)} />
      ))}
    </Animated.View>
  );
}

function ActionRow({ action, index, height, tint, reveal, onPress }: { action: PeekAction; index: number; height: number; tint: string; reveal: SharedValue<number>; onPress: () => void }) {
  const start = ACTION_AT[Math.min(index, ACTION_AT.length - 1)];
  const rise = useAnimatedStyle(() => {
    const u = easeOutCubic(seg(reveal.get(), start, start + REVEAL_SPAN));
    return { opacity: u, transform: [{ translateY: ACTION_RISE * (1 - u) }] };
  });
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={action.label} style={({ pressed }) => [index > 0 ? styles.actionLine : null, pressed ? styles.actionPressed : null]}>
      <Animated.View style={[styles.action, { height }, rise]}>
        <Text style={[styles.actionLabel, { color: tint }]}>{action.label}</Text>
        <SymbolView name={action.symbol} size={19} weight="medium" tintColor={tint} fallback={<View style={[styles.actionDot, { backgroundColor: tint }]} />} />
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  frame: { position: 'absolute', left: 0, top: 0 },
  curve: { borderCurve: 'continuous' },
  clip: { overflow: 'hidden' },
  panel: {
    position: 'absolute',
    borderRadius: 16,
    borderCurve: 'continuous',
    overflow: 'hidden',
    boxShadow: '0 1px 3px rgba(20,17,13,0.14), 0 18px 40px rgba(20,17,13,0.22)',
  },
  action: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingLeft: 16, paddingRight: 14 },
  actionLine: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: 'rgba(26,27,30,0.12)' },
  actionPressed: { backgroundColor: 'rgba(26,27,30,0.07)' },
  actionLabel: { fontSize: 17, fontWeight: '400', letterSpacing: -0.3 },
  actionDot: { width: 8, height: 8, borderRadius: 4 },
});
