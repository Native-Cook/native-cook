// The reusable piece: a frame that grows out of whatever was tapped and falls back into it.
//
//   const dialog = useMorphingDialog({ target });           // where the open dialog sits, in window points
//   <MorphingScene controller={dialog}>…the page…</MorphingScene>
//   <MorphingTrigger controller={dialog} index={i} rect={rectOfThisCard}>…the card…</MorphingTrigger>
//   <MorphingDialog controller={dialog}>…what is inside the frame…</MorphingDialog>
//
// Nothing here knows what the content is. The children read the controller's shared values
// (progress, titleFollow, content …) in their own animated styles to place themselves.
import { BlurView } from 'expo-blur';
import type { ReactNode } from 'react';
import { StyleSheet, type StyleProp, type ViewProps, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  clamp,
  CONTENT_IN,
  CONTENT_OUT,
  DAMPING,
  DRAG_CLOSE_DISTANCE,
  DRAG_CLOSE_VELOCITY,
  DRAG_FOLLOW,
  DRAG_RUBBER,
  DRAG_SIDE_RUBBER,
  DRAG_SPAN,
  DRAG_UP_RUBBER,
  FOLLOW_SUBTITLE,
  FOLLOW_TITLE,
  mix,
  PRESS_MS,
  PRESS_SCALE,
  RADIUS_CARD,
  RADIUS_DIALOG,
  rubber,
  rubberSlope,
  SCENE_BLUR,
  SCENE_DIM,
  SCENE_SCALE,
  STIFFNESS,
  SUBSTEP,
} from './motion';

export type Rect = { x: number; y: number; width: number; height: number };

const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);
const NOWHERE: Rect = { x: 0, y: 0, width: 1, height: 1 };
const HIT_SLOP = 8;

export type MorphingDialogOptions = {
  /** Where the open dialog sits, in window points. */
  target: Rect;
  /** Crossfade in place instead of morphing. */
  reducedMotion?: boolean;
};

export type MorphingDialogController = ReturnType<typeof useMorphingDialog>;

/** The morph's state and its spring, all on the UI thread. */
export function useMorphingDialog({ target, reducedMotion = false }: MorphingDialogOptions) {
  /** 0 = the trigger's rect, 1 = the target rect. Overshoots a little on its spring. */
  const progress = useSharedValue(0);
  const velocity = useSharedValue(0);
  const goal = useSharedValue(0);
  /** Index of the trigger the frame came from; -1 while nothing is open. */
  const active = useSharedValue(-1);
  const source = useSharedValue<Rect>(NOWHERE);
  /** progress, 45 ms and 75 ms late: follow-through for the parts that ride in the frame. */
  const titleFollow = useSharedValue(0);
  const subtitleFollow = useSharedValue(0);
  /** The detail content's own clock, 0…1. */
  const content = useSharedValue(0);
  /** The frame's offset under the finger, with its own velocity for the way home. */
  const dragX = useSharedValue(0);
  const dragY = useSharedValue(0);
  const dragVX = useSharedValue(0);
  const dragVY = useSharedValue(0);
  const dragging = useSharedValue(false);
  const grabProgress = useSharedValue(1);
  const grabX = useSharedValue(0);
  const grabY = useSharedValue(0);
  const pulled = useSharedValue(0);
  /** Press feedback: which trigger, how far in (0…1), and the same for the two buttons inside. */
  const pressed = useSharedValue(-1);
  const pressAmount = useSharedValue(0);
  const closePress = useSharedValue(0);
  const actionPress = useSharedValue(0);
  /** Bumped by every touch, so an autoPlay script can see it was interrupted. */
  const touches = useSharedValue(0);
  const fingers = useSharedValue(0);

  const touch = () => {
    'worklet';
    touches.set(touches.get() + 1);
  };

  const pressIn = (index: number) => {
    'worklet';
    pressed.set(index);
    pressAmount.set(withTiming(1, { duration: PRESS_MS }));
  };

  const pressOut = () => {
    'worklet';
    pressAmount.set(withTiming(0, { duration: PRESS_MS }));
  };

  /** Grows the frame out of `rect`. False while another trigger's dialog is still on screen. */
  const open = (index: number, rect: Rect) => {
    'worklet';
    const current = active.get();
    if (current >= 0 && current !== index) return false;
    if (current < 0) {
      source.set(rect);
      progress.set(0);
      velocity.set(0);
      titleFollow.set(0);
      subtitleFollow.set(0);
      content.set(0);
      dragX.set(0);
      dragY.set(0);
      dragVX.set(0);
      dragVY.set(0);
    }
    active.set(index);
    goal.set(1);
    return true;
  };

  /** Sends the frame back to its trigger, keeping whatever speed it has plus `kick` (progress per second). */
  const close = (kick: number) => {
    'worklet';
    if (active.get() < 0) return;
    dragging.set(false);
    velocity.set(velocity.get() + kick);
    goal.set(0);
  };

  /** Catches the frame wherever it is, mid-spring included. */
  const dragBegin = () => {
    'worklet';
    dragging.set(true);
    grabProgress.set(progress.get());
    grabX.set(dragX.get());
    grabY.set(dragY.get());
    pulled.set(0);
    velocity.set(0);
    dragVX.set(0);
    dragVY.set(0);
  };

  /** The finger has moved (x, y) since dragBegin: down shrinks toward the trigger, the rest stretches. */
  const dragMove = (x: number, y: number) => {
    'worklet';
    const down = Math.max(y, 0);
    const up = Math.max(-y, 0);
    const banded = rubber(down, DRAG_RUBBER);
    pulled.set(down);
    progress.set(Math.max(grabProgress.get() - banded / DRAG_SPAN, 0));
    dragY.set(grabY.get() + banded * DRAG_FOLLOW - rubber(up, DRAG_UP_RUBBER) * DRAG_FOLLOW);
    dragX.set(grabX.get() + Math.sign(x) * rubber(Math.abs(x), DRAG_SIDE_RUBBER) * DRAG_FOLLOW);
  };

  /** Lets go with the finger's velocity (pt/s). True when the dialog closes. */
  const dragEnd = (vx: number, vy: number) => {
    'worklet';
    if (!dragging.get()) return false;
    dragging.set(false);
    const down = pulled.get();
    const slope = rubberSlope(down, DRAG_RUBBER);
    // The finger's speed becomes the springs' starting speed.
    velocity.set(vy > 0 || down > 0 ? (-vy * slope) / DRAG_SPAN : 0);
    dragVY.set(vy * slope * DRAG_FOLLOW);
    dragVX.set(vx * rubberSlope(Math.abs(dragX.get() - grabX.get()), DRAG_SIDE_RUBBER) * DRAG_FOLLOW);
    const closes = vy > DRAG_CLOSE_VELOCITY || down > DRAG_CLOSE_DISTANCE;
    goal.set(closes ? 0 : 1);
    return closes;
  };

  useFrameCallback((frame) => {
    if (active.get() < 0) return;
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;
    const to = goal.get();
    let x = progress.get();
    const held = dragging.get();

    if (!held) {
      let v = velocity.get();
      let ox = dragX.get();
      let oy = dragY.get();
      let vx = dragVX.get();
      let vy = dragVY.get();
      if (reducedMotion) {
        const k = 1 - Math.exp(-dt * 14);
        x += (to - x) * k;
        ox = 0;
        oy = 0;
        v = 0;
        vx = 0;
        vy = 0;
        if (Math.abs(to - x) < 0.004) x = to;
      } else {
        const steps = Math.max(1, Math.ceil(dt / SUBSTEP));
        const h = dt / steps;
        for (let i = 0; i < steps; i += 1) {
          v += (STIFFNESS * (to - x) - DAMPING * v) * h;
          x += v * h;
          vx += (-STIFFNESS * ox - DAMPING * vx) * h;
          ox += vx * h;
          vy += (-STIFFNESS * oy - DAMPING * vy) * h;
          oy += vy * h;
        }
        if (Math.abs(to - x) < 0.0004 && Math.abs(v) < 0.004) {
          x = to;
          v = 0;
        }
        if (Math.abs(ox) < 0.05 && Math.abs(vx) < 0.5) {
          ox = 0;
          vx = 0;
        }
        if (Math.abs(oy) < 0.05 && Math.abs(vy) < 0.5) {
          oy = 0;
          vy = 0;
        }
      }
      progress.set(x);
      velocity.set(v);
      dragX.set(ox);
      dragY.set(oy);
      dragVX.set(vx);
      dragVY.set(vy);
    }

    // Follow-through and the content's clock run whether a finger holds the frame or not.
    const title = titleFollow.get() + (x - titleFollow.get()) * (1 - Math.exp(-dt / FOLLOW_TITLE));
    const subtitle = subtitleFollow.get() + (x - subtitleFollow.get()) * (1 - Math.exp(-dt / FOLLOW_SUBTITLE));
    titleFollow.set(title);
    subtitleFollow.set(subtitle);
    const showing = to === 1 && !held;
    content.set(clamp(content.get() + (showing ? dt / CONTENT_IN : -dt / CONTENT_OUT), 0, 1));

    // Home: the trigger takes over again.
    if (to === 0 && !held && x === 0 && Math.abs(title) < 0.003 && Math.abs(subtitle) < 0.003 && dragX.get() === 0 && dragY.get() === 0) {
      titleFollow.set(0);
      subtitleFollow.set(0);
      content.set(0);
      active.set(-1);
    }
  });

  return {
    target,
    reducedMotion,
    progress,
    goal,
    active,
    source,
    titleFollow,
    subtitleFollow,
    content,
    dragX,
    dragY,
    dragging,
    pressed,
    pressAmount,
    closePress,
    actionPress,
    touches,
    fingers,
    touch,
    pressIn,
    pressOut,
    open,
    close,
    dragBegin,
    dragMove,
    dragEnd,
  };
}

/** The page behind the dialog: it sinks back a little while the dialog is open. */
export function MorphingScene({ controller, style, children }: { controller: MorphingDialogController; style?: StyleProp<ViewStyle>; children: ReactNode }) {
  const { progress, reducedMotion } = controller;
  const sink = useAnimatedStyle(() => ({
    transform: [{ scale: reducedMotion ? 1 : mix(1, SCENE_SCALE, clamp(progress.get(), 0, 1)) }],
  }));
  return <Animated.View style={[StyleSheet.absoluteFill, style, sink]}>{children}</Animated.View>;
}

export type MorphingTriggerProps = {
  controller: MorphingDialogController;
  index: number;
  /** This trigger's frame in window points: the dialog grows out of it and returns to it. */
  rect: Rect;
  onOpen?: (index: number) => void;
  accessibilityLabel?: string;
  children: ReactNode;
};

/** What is tapped. It hands its place to the dialog's frame while the dialog is out. */
export function MorphingTrigger({ controller, index, rect, onOpen, accessibilityLabel, children }: MorphingTriggerProps) {
  const { active, pressed, pressAmount, fingers, reducedMotion, touch, pressIn, pressOut, open } = controller;

  const tap = Gesture.Tap()
    .maxDuration(60000)
    .onBegin(() => {
      touch();
      fingers.set(fingers.get() + 1);
      if (active.get() < 0) pressIn(index);
    })
    .onEnd((_event, success) => {
      if (!success) return;
      if (open(index, rect) && onOpen) scheduleOnRN(onOpen, index);
    })
    .onFinalize(() => {
      fingers.set(Math.max(0, fingers.get() - 1));
      pressOut();
    });

  const place = useAnimatedStyle(() => ({
    opacity: !reducedMotion && active.get() === index ? 0 : 1,
    transform: [{ scale: pressed.get() === index ? 1 - (1 - PRESS_SCALE) * pressAmount.get() : 1 }],
  }));

  return (
    <GestureDetector gesture={tap}>
      <Animated.View
        accessible
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        style={[styles.trigger, { left: rect.x, top: rect.y, width: rect.width, height: rect.height }, place]}>
        {children}
      </Animated.View>
    </GestureDetector>
  );
}

export type MorphingDialogProps = {
  controller: MorphingDialogController;
  /** The frame's fill, the same as the trigger's so the hand-over cannot be seen. */
  backgroundColor: string;
  /** Radii of the trigger and of the open dialog. */
  radius?: { from: number; to: number };
  /** The close button's place inside the open dialog (relative to `target`). A tap outside closes too. */
  closeHit?: Rect;
  /** The main button's place inside the open dialog (relative to `target`). */
  actionHit?: Rect;
  onClose?: () => void;
  onAction?: () => void;
  children: ReactNode;
};

/** Backdrop, touch layer and the morphing frame. Put it above the scene, as the last child of the screen. */
export function MorphingDialog({ controller, backgroundColor, radius, closeHit, actionHit, onClose, onAction, children }: MorphingDialogProps) {
  const { target, reducedMotion, progress, goal, active, source, dragX, dragY, dragging, pressAmount, closePress, actionPress, fingers } = controller;
  const { touch, close, dragBegin, dragMove, dragEnd } = controller;
  const fromRadius = radius?.from ?? RADIUS_CARD;
  const toRadius = radius?.to ?? RADIUS_DIALOG;

  const within = (x: number, y: number, hit: Rect | undefined, slop: number) => {
    'worklet';
    if (!hit) return false;
    const left = target.x + hit.x - slop;
    const top = target.y + hit.y - slop;
    return x >= left && x <= left + hit.width + slop * 2 && y >= top && y <= top + hit.height + slop * 2;
  };

  const inFrame = (x: number, y: number) => {
    'worklet';
    return x >= target.x && x <= target.x + target.width && y >= target.y && y <= target.y + target.height;
  };

  const pan = Gesture.Pan()
    .minDistance(6)
    .onBegin(() => {
      touch();
      fingers.set(fingers.get() + 1);
    })
    .onStart((event) => {
      if (goal.get() === 1 && inFrame(event.x - event.translationX, event.y - event.translationY)) dragBegin();
    })
    .onUpdate((event) => {
      if (dragging.get()) dragMove(event.translationX, event.translationY);
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      if (dragEnd(event.velocityX, event.velocityY) && onClose) scheduleOnRN(onClose);
    })
    .onFinalize(() => {
      fingers.set(Math.max(0, fingers.get() - 1));
      if (dragging.get()) dragEnd(0, 0);
    });

  const tap = Gesture.Tap()
    .maxDuration(60000)
    .onBegin((event) => {
      if (goal.get() !== 1) return;
      if (within(event.x, event.y, closeHit, HIT_SLOP)) closePress.set(withTiming(1, { duration: PRESS_MS }));
      else if (within(event.x, event.y, actionHit, 0)) actionPress.set(withTiming(1, { duration: PRESS_MS }));
    })
    .onEnd((event, success) => {
      if (!success || goal.get() !== 1) return;
      if (within(event.x, event.y, closeHit, HIT_SLOP) || !inFrame(event.x, event.y)) {
        close(0);
        if (onClose) scheduleOnRN(onClose);
      } else if (within(event.x, event.y, actionHit, 0) && onAction) {
        scheduleOnRN(onAction);
      }
    })
    .onFinalize(() => {
      closePress.set(withTiming(0, { duration: PRESS_MS }));
      actionPress.set(withTiming(0, { duration: PRESS_MS }));
    });

  const gesture = Gesture.Exclusive(pan, tap);

  const blurProps = useAnimatedProps(() => ({ intensity: SCENE_BLUR * clamp(progress.get(), 0, 1) }));
  const dim = useAnimatedStyle(() => ({ opacity: SCENE_DIM * clamp(progress.get(), 0, 1) }));
  // The touch layer only exists while a dialog is out; otherwise touches reach the triggers.
  const touchProps = useAnimatedProps<ViewProps>(() => ({ pointerEvents: active.get() >= 0 ? 'auto' : 'none' }));

  const frame = useAnimatedStyle(() => {
    const from = source.get();
    const amount = progress.get();
    const q = reducedMotion ? 1 : amount;
    return {
      opacity: active.get() < 0 ? 0 : reducedMotion ? clamp(amount, 0, 1) : 1,
      width: Math.max(1, mix(from.width, target.width, q)),
      height: Math.max(1, mix(from.height, target.height, q)),
      transform: [
        { translateX: mix(from.x, target.x, q) + dragX.get() },
        { translateY: mix(from.y, target.y, q) + dragY.get() },
        { scale: 1 - (1 - PRESS_SCALE) * pressAmount.get() },
      ],
    };
  });
  const corners = useAnimatedStyle(() => ({
    borderRadius: mix(fromRadius, toRadius, reducedMotion ? 1 : clamp(progress.get(), 0, 1)),
  }));
  const lifted = useAnimatedStyle(() => ({ opacity: reducedMotion ? 1 : clamp(progress.get(), 0, 1) }));

  return (
    <>
      <AnimatedBlurView pointerEvents="none" tint="light" style={StyleSheet.absoluteFill} animatedProps={blurProps} />
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.dim, dim]} />
      <GestureDetector gesture={gesture}>
        <Animated.View style={StyleSheet.absoluteFill} animatedProps={touchProps}>
          <Animated.View style={[styles.frame, frame]}>
            {/* Two shadows that cross over: the card's stays, the lifted dialog's fades in. */}
            <Animated.View style={[StyleSheet.absoluteFill, styles.shadowFar, { backgroundColor }, corners, lifted]} />
            <Animated.View style={[StyleSheet.absoluteFill, styles.shadowNear, { backgroundColor }, corners]} />
            <Animated.View style={[StyleSheet.absoluteFill, styles.clip, { backgroundColor }, corners]}>{children}</Animated.View>
          </Animated.View>
        </Animated.View>
      </GestureDetector>
    </>
  );
}

/** Shows its children only while trigger `index` is the one that is open (several items can share one frame). */
export function MorphingLayer({ controller, index, style, children }: { controller: MorphingDialogController; index: number; style?: StyleProp<ViewStyle>; children: ReactNode }) {
  const { active } = controller;
  const visible = useAnimatedStyle(() => ({ opacity: active.get() === index ? 1 : 0 }));
  return (
    <Animated.View pointerEvents="none" style={[style, visible]}>
      {children}
    </Animated.View>
  );
}

/** The trigger's resting shadow; exported so a card and the frame share it. */
export const TRIGGER_SHADOW = '0 1px 2px rgba(58,40,24,0.10), 0 8px 20px rgba(58,40,24,0.08)';

const styles = StyleSheet.create({
  trigger: { position: 'absolute' },
  dim: { backgroundColor: '#1a130c' },
  frame: { position: 'absolute', left: 0, top: 0 },
  shadowNear: { borderCurve: 'continuous', boxShadow: TRIGGER_SHADOW },
  shadowFar: { borderCurve: 'continuous', boxShadow: '0 2px 6px rgba(40,26,14,0.16), 0 28px 56px rgba(40,26,14,0.30)' },
  clip: { borderCurve: 'continuous', overflow: 'hidden' },
});
