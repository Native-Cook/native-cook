// The reusable piece: a pill button that stretches into a popover on the spot it sits on, and closes back into it.
//
//   const popover = useMorphingPopover({ anchor, size });   // the pill's frame and the open size, in window points
//   <MorphingPopover controller={popover} label="Feedback" labelAt={{ x: 26, y: 26 }} …>
//     <PopoverRise controller={popover} order={0}>…</PopoverRise>
//   </MorphingPopover>
//
// The popover keeps the pill's bottom edge and centre. Width and height are two springs, 50 ms apart, which is
// what makes it look gooey. The label slides to `labelAt` and becomes the field's placeholder.
import type { ReactNode } from 'react';
import { StyleSheet, Text, View, type StyleProp, type ViewProps, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedProps,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import {
  AXIS_STAGGER,
  clamp,
  CONTENT_GATE,
  CONTENT_IN,
  CONTENT_OUT,
  CONTENT_RISE,
  CONTENT_SPAN,
  CONTENT_START,
  DRAG_CLOSE_DISTANCE,
  DRAG_CLOSE_VELOCITY,
  DRAG_FOLLOW,
  DRAG_RUBBER,
  DRAG_SPAN,
  DRAG_UP_RUBBER,
  DRAG_WIDTH_SHARE,
  easeInOutCubic,
  easeOutCubic,
  FOLLOW_LABEL,
  HEIGHT_DAMPING,
  HEIGHT_STIFFNESS,
  mix,
  PLACEHOLDER_ALPHA,
  PRESS_MS,
  PRESS_SCALE,
  RADIUS_OPEN,
  rubber,
  rubberSlope,
  SCRIM,
  seg,
  SUBSTEP,
  THANKS,
  THANKS_PULSE,
  WIDTH_DAMPING,
  WIDTH_STIFFNESS,
} from './motion';

export type Rect = { x: number; y: number; width: number; height: number };
export type Size = { width: number; height: number };
/** 0 = finger down, 1 = a tap ended there, 2 = the touch is over. x and y are in the open popover's coordinates. */
export type InsideTouch = (x: number, y: number, phase: number) => void;

const LABEL_SIZE = 16;
const LABEL_LINE = 20;
const LABEL_GUESS = 76; // until the label has been measured
const CHECK = 18;
const STROKE = 2.2;
// The check's corners in its 18 pt box: start, elbow, tip.
const SHORT = { x: 3.5, y: 9.5, length: 5.66, angle: 45 };
const LONG = { x: 7.5, y: 13.5, length: 11.01, angle: -50.5 };

export type MorphingPopoverOptions = {
  /** The pill's frame in window points. */
  anchor: Rect;
  /** The open popover's size. It keeps the pill's bottom edge and centre. */
  size: Size;
  reducedMotion?: boolean;
};

export type MorphingPopoverController = ReturnType<typeof useMorphingPopover>;

/** The popover's state and its two springs, all on the UI thread. */
export function useMorphingPopover({ anchor, size, reducedMotion = false }: MorphingPopoverOptions) {
  /** 0 = the pill's width / height, 1 = the popover's. Each overshoots on its own spring. */
  const wide = useSharedValue(0);
  const tall = useSharedValue(0);
  const wideV = useSharedValue(0);
  const tallV = useSharedValue(0);
  const goal = useSharedValue(0);
  const wideGoal = useSharedValue(0);
  const tallGoal = useSharedValue(0);
  /** Seconds until the second axis is told about the new goal. */
  const lag = useSharedValue(0);
  /** `tall`, 60 ms late: the label rides on it. */
  const labelFollow = useSharedValue(0);
  /** The content's own clock, 0…1. */
  const content = useSharedValue(0);
  const dragY = useSharedValue(0);
  const dragV = useSharedValue(0);
  const dragging = useSharedValue(false);
  const grabWide = useSharedValue(1);
  const grabTall = useSharedValue(1);
  const grabY = useSharedValue(0);
  const pulled = useSharedValue(0);
  /** Seconds since Send; -1 when no thanks is showing. */
  const thanks = useSharedValue(-1);
  const press = useSharedValue(0);
  /** Bumped by every touch, so an autoPlay script can see it was interrupted. */
  const touches = useSharedValue(0);
  const fingers = useSharedValue(0);

  const touch = () => {
    'worklet';
    touches.set(touches.get() + 1);
  };

  /** Stretches the pill open. False when it already is. */
  const open = () => {
    'worklet';
    if (goal.get() === 1) return false;
    thanks.set(-1);
    goal.set(1);
    wideGoal.set(1);
    lag.set(AXIS_STAGGER);
    return true;
  };

  /** Closes back into the pill. */
  const dismiss = () => {
    'worklet';
    if (goal.get() === 0) return false;
    dragging.set(false);
    goal.set(0);
    tallGoal.set(0);
    lag.set(AXIS_STAGGER);
    return true;
  };

  /** Closes, and the pill says thanks for a moment. */
  const confirm = () => {
    'worklet';
    if (!dismiss()) return false;
    thanks.set(0);
    return true;
  };

  const dragBegin = () => {
    'worklet';
    dragging.set(true);
    grabWide.set(wide.get());
    grabTall.set(tall.get());
    grabY.set(dragY.get());
    pulled.set(0);
    wideV.set(0);
    tallV.set(0);
    dragV.set(0);
  };

  /** The finger has moved y since dragBegin: down squashes the popover toward the pill, up stretches a little. */
  const dragMove = (y: number) => {
    'worklet';
    const down = Math.max(y, 0);
    const up = Math.max(-y, 0);
    const banded = rubber(down, DRAG_RUBBER);
    pulled.set(down);
    tall.set(Math.max(grabTall.get() - banded / DRAG_SPAN, 0));
    wide.set(Math.max(grabWide.get() - (banded / DRAG_SPAN) * DRAG_WIDTH_SHARE, 0));
    dragY.set(grabY.get() + banded * DRAG_FOLLOW - rubber(up, DRAG_UP_RUBBER) * 0.5);
  };

  /** Lets go with the finger's velocity (pt/s). True when the popover closes. */
  const dragEnd = (vy: number) => {
    'worklet';
    if (!dragging.get()) return false;
    dragging.set(false);
    const down = pulled.get();
    const slope = rubberSlope(down, DRAG_RUBBER);
    const speed = down > 0 ? (-vy * slope) / DRAG_SPAN : 0;
    tallV.set(speed);
    wideV.set(speed * DRAG_WIDTH_SHARE);
    dragV.set(vy * slope * DRAG_FOLLOW);
    const closes = vy > DRAG_CLOSE_VELOCITY || down > DRAG_CLOSE_DISTANCE;
    if (closes) {
      goal.set(0);
      tallGoal.set(0);
      lag.set(AXIS_STAGGER);
    }
    return closes;
  };

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;
    const to = goal.get();
    const held = dragging.get();

    // The second axis hears about a new goal 50 ms after the first.
    if (lag.get() > 0) {
      const left = lag.get() - dt;
      lag.set(left);
      if (left <= 0) {
        wideGoal.set(to);
        tallGoal.set(to);
      }
    }

    let w = wide.get();
    let h = tall.get();
    if (!held) {
      const toWide = wideGoal.get();
      const toTall = tallGoal.get();
      let vw = wideV.get();
      let vh = tallV.get();
      let y = dragY.get();
      let vy = dragV.get();
      if (reducedMotion) {
        const k = 1 - Math.exp(-dt * 14);
        w += (toWide - w) * k;
        h += (toTall - h) * k;
        if (Math.abs(toWide - w) < 0.004) w = toWide;
        if (Math.abs(toTall - h) < 0.004) h = toTall;
        vw = 0;
        vh = 0;
        y = 0;
        vy = 0;
      } else {
        const steps = Math.max(1, Math.ceil(dt / SUBSTEP));
        const step = dt / steps;
        for (let i = 0; i < steps; i += 1) {
          vw += (WIDTH_STIFFNESS * (toWide - w) - WIDTH_DAMPING * vw) * step;
          w += vw * step;
          vh += (HEIGHT_STIFFNESS * (toTall - h) - HEIGHT_DAMPING * vh) * step;
          h += vh * step;
          vy += (-HEIGHT_STIFFNESS * y - HEIGHT_DAMPING * vy) * step;
          y += vy * step;
        }
        if (Math.abs(toWide - w) < 0.0004 && Math.abs(vw) < 0.004) {
          w = toWide;
          vw = 0;
        }
        if (Math.abs(toTall - h) < 0.0004 && Math.abs(vh) < 0.004) {
          h = toTall;
          vh = 0;
        }
        if (Math.abs(y) < 0.05 && Math.abs(vy) < 0.5) {
          y = 0;
          vy = 0;
        }
      }
      wide.set(w);
      tall.set(h);
      wideV.set(vw);
      tallV.set(vh);
      dragY.set(y);
      dragV.set(vy);
    }

    const follow = labelFollow.get() + (h - labelFollow.get()) * (1 - Math.exp(-dt / FOLLOW_LABEL));
    labelFollow.set(Math.abs(follow) < 0.0005 && h === 0 ? 0 : follow);
    const showing = to === 1 && !held;
    content.set(clamp(content.get() + (showing ? dt / CONTENT_IN : -dt / CONTENT_OUT), 0, 1));

    const said = thanks.get();
    if (said >= 0) thanks.set(said + dt >= THANKS.end ? -1 : said + dt);
  });

  return {
    anchor,
    size,
    reducedMotion,
    wide,
    tall,
    goal,
    labelFollow,
    content,
    dragY,
    dragging,
    thanks,
    press,
    touches,
    fingers,
    touch,
    open,
    dismiss,
    confirm,
    dragBegin,
    dragMove,
    dragEnd,
  };
}

export type MorphingPopoverProps = {
  controller: MorphingPopoverController;
  /** Fill of the pill and of the popover. */
  color: string;
  /** The pill's label, which becomes the field's placeholder. */
  label: string;
  labelColor: string;
  /** Where the label's top-left corner rests as a placeholder, in the open popover's coordinates. */
  labelAt: { x: number; y: number };
  /** 1 while something is typed, so the placeholder steps aside. */
  labelHidden?: SharedValue<number>;
  /** What the pill says after `controller.confirm()`, next to a check that draws itself. */
  thanksLabel: string;
  accent: string;
  /** Points the popover is pushed up by (a keyboard, for instance). */
  lift?: SharedValue<number>;
  /** Touches inside the open popover, on the UI thread: must be a worklet. */
  onTouchInside?: InsideTouch;
  onOpen?: () => void;
  onDismiss?: () => void;
  accessibilityLabel?: string;
  children: ReactNode;
};

/** Scrim, outside-tap layer and the stretching frame. Put it above the page, as the last child of the screen. */
export function MorphingPopover({
  controller,
  color,
  label,
  labelColor,
  labelAt,
  labelHidden,
  thanksLabel,
  accent,
  lift,
  onTouchInside,
  onOpen,
  onDismiss,
  accessibilityLabel,
  children,
}: MorphingPopoverProps) {
  const { anchor, size, reducedMotion, wide, tall, goal, labelFollow, dragY, dragging, thanks, press, fingers } = controller;
  const { touch, open, dismiss, dragBegin, dragMove, dragEnd } = controller;
  const labelWidth = useSharedValue(LABEL_GUESS);
  const centre = anchor.x + anchor.width / 2;
  const bottom = anchor.y + anchor.height;

  // Both take the shared value's number: an animated style only follows shared values it reads itself,
  // never ones read inside a helper it calls.
  const widthAt = (amount: number) => {
    'worklet';
    return Math.max(anchor.width * 0.9, mix(anchor.width, size.width, amount));
  };
  const heightAt = (amount: number) => {
    'worklet';
    return Math.max(anchor.height * 0.92, mix(anchor.height, size.height, amount));
  };

  const tap = Gesture.Tap()
    .maxDuration(60000)
    .onBegin((event) => {
      touch();
      if (goal.get() === 0) press.set(withTiming(1, { duration: PRESS_MS }));
      else if (onTouchInside) onTouchInside(event.x - (widthAt(wide.get()) - size.width) / 2, event.y - (heightAt(tall.get()) - size.height), 0);
    })
    .onEnd((event, success) => {
      if (!success) return;
      if (goal.get() === 0) {
        if (open() && onOpen) scheduleOnRN(onOpen);
      } else if (onTouchInside) {
        onTouchInside(event.x - (widthAt(wide.get()) - size.width) / 2, event.y - (heightAt(tall.get()) - size.height), 1);
      }
    })
    .onFinalize(() => {
      press.set(withTiming(0, { duration: PRESS_MS }));
      if (onTouchInside) onTouchInside(0, 0, 2);
    });

  const pan = Gesture.Pan()
    .minDistance(6)
    .onBegin(() => {
      fingers.set(fingers.get() + 1);
    })
    .onStart(() => {
      if (goal.get() === 1) dragBegin();
    })
    .onUpdate((event) => {
      if (dragging.get()) dragMove(event.translationY);
    })
    .onEnd((event) => {
      if (!dragging.get()) return;
      if (dragEnd(event.velocityY) && onDismiss) scheduleOnRN(onDismiss);
    })
    .onFinalize(() => {
      fingers.set(Math.max(0, fingers.get() - 1));
      if (dragging.get()) dragEnd(0);
    });

  const outside = Gesture.Tap()
    .maxDuration(60000)
    .onBegin(() => {
      touch();
    })
    .onEnd((_event, success) => {
      if (success && dismiss() && onDismiss) scheduleOnRN(onDismiss);
    });

  const scrim = useAnimatedStyle(() => ({ opacity: SCRIM * clamp(tall.get(), 0, 1) }));
  // Touches outside only count while the popover is out; touches on its content only while it is open.
  const outsideProps = useAnimatedProps<ViewProps>(() => ({ pointerEvents: goal.get() === 1 ? 'auto' : 'none' }));
  const contentProps = useAnimatedProps<ViewProps>(() => ({ pointerEvents: goal.get() === 1 ? 'auto' : 'none' }));

  const frame = useAnimatedStyle(() => {
    const width = widthAt(wide.get());
    const height = heightAt(tall.get());
    const said = thanks.get();
    const swell = said < 0 || reducedMotion ? 0 : Math.sin(Math.PI * easeInOutCubic(seg(said, THANKS.pulse[0], THANKS.pulse[1])));
    return {
      width,
      height,
      transform: [
        { translateX: centre - width / 2 },
        { translateY: bottom - height + dragY.get() - (lift ? lift.get() * clamp(tall.get(), 0, 1) : 0) },
        { scale: (1 - (1 - PRESS_SCALE) * press.get()) * (1 + THANKS_PULSE * swell) },
      ],
    };
  });
  const corners = useAnimatedStyle(() => ({
    borderRadius: Math.min(heightAt(tall.get()) / 2, mix(anchor.height / 2, RADIUS_OPEN, clamp(tall.get(), 0, 1))),
  }));
  // The content does not move with the frame: it stays where it will be, and the frame opens around it.
  const pinned = useAnimatedStyle(() => ({
    transform: [{ translateX: (widthAt(wide.get()) - size.width) / 2 }, { translateY: heightAt(tall.get()) - size.height }],
  }));

  const labelStyle = useAnimatedStyle(() => {
    const open01 = clamp(tall.get(), 0, 1);
    const ride = reducedMotion ? open01 : labelFollow.get();
    const said = thanks.get();
    const afterSend = said < 0 ? 1 : clamp(1 - seg(said, 0, THANKS.labelOut) + seg(said, THANKS.labelIn[0], THANKS.labelIn[1]), 0, 1);
    const covered = (labelHidden ? labelHidden.get() : 0) * seg(open01, CONTENT_GATE[0], CONTENT_GATE[1]);
    return {
      opacity: mix(1, PLACEHOLDER_ALPHA, clamp(ride, 0, 1)) * (1 - covered) * afterSend,
      transform: [
        { translateX: mix(size.width / 2 - labelWidth.get() / 2, labelAt.x, ride) },
        { translateY: mix(size.height - anchor.height / 2 - LABEL_LINE / 2, labelAt.y, ride) },
      ],
    };
  });

  const thanksStyle = useAnimatedStyle(() => {
    const said = thanks.get();
    return { opacity: said < 0 ? 0 : seg(said, THANKS.in[0], THANKS.in[1]) * (1 - seg(said, THANKS.out[0], THANKS.out[1])) };
  });
  const shortStroke = useAnimatedStyle(() => {
    const said = thanks.get();
    const u = said < 0 ? 0 : reducedMotion ? 1 : easeOutCubic(seg(said, THANKS.short[0], THANKS.short[1]));
    return { opacity: u > 0 ? 1 : 0, transform: [{ rotate: `${SHORT.angle}deg` }, { scaleX: Math.max(u, 0.001) }] };
  });
  const longStroke = useAnimatedStyle(() => {
    const said = thanks.get();
    const u = said < 0 ? 0 : reducedMotion ? 1 : easeOutCubic(seg(said, THANKS.long[0], THANKS.long[1]));
    return { opacity: u > 0 ? 1 : 0, transform: [{ rotate: `${LONG.angle}deg` }, { scaleX: Math.max(u, 0.001) }] };
  });

  return (
    <>
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, styles.scrim, scrim]} />
      <GestureDetector gesture={outside}>
        <Animated.View style={StyleSheet.absoluteFill} animatedProps={outsideProps} />
      </GestureDetector>
      <GestureDetector gesture={Gesture.Exclusive(pan, tap)}>
        <Animated.View accessible={false} style={[styles.frame, { backgroundColor: color }, frame, corners]}>
          <Animated.View collapsable={false} style={[StyleSheet.absoluteFill, styles.clip, { backgroundColor: color }, corners]}>
            <Animated.View style={[styles.pinned, { width: size.width, height: size.height }, pinned]} animatedProps={contentProps}>
              {children}
            </Animated.View>
            <Animated.View pointerEvents="none" style={[styles.pinned, { width: size.width, height: size.height }, pinned]}>
              <Animated.Text
                accessibilityRole="button"
                accessibilityLabel={accessibilityLabel ?? label}
                numberOfLines={1}
                onLayout={(event) => labelWidth.set(event.nativeEvent.layout.width)}
                style={[styles.label, { color: labelColor }, labelStyle]}>
                {label}
              </Animated.Text>
              <Animated.View
                style={[
                  styles.thanks,
                  { left: size.width / 2 - anchor.width / 2, top: size.height - anchor.height, width: anchor.width, height: anchor.height },
                  thanksStyle,
                ]}>
                <View style={styles.check}>
                  <Animated.View style={[styles.stroke, { left: SHORT.x, top: SHORT.y - STROKE / 2, width: SHORT.length, backgroundColor: accent }, shortStroke]} />
                  <Animated.View style={[styles.stroke, { left: LONG.x, top: LONG.y - STROKE / 2, width: LONG.length, backgroundColor: accent }, longStroke]} />
                </View>
                <Text style={[styles.thanksLabel, { color: labelColor }]}>{thanksLabel}</Text>
              </Animated.View>
            </Animated.View>
          </Animated.View>
        </Animated.View>
      </GestureDetector>
    </>
  );
}

export type PopoverRiseProps = {
  controller: MorphingPopoverController;
  /** 0, 1, 2: its place in the stagger. */
  order: number;
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
};

/** One part of the popover's content: it fades up once the frame is nearly open, one after another. */
export function PopoverRise({ controller, order, style, children }: PopoverRiseProps) {
  const { reducedMotion, tall, content } = controller;
  const rise = useAnimatedStyle(() => {
    const start = CONTENT_START[order];
    const u = easeOutCubic(seg(content.get(), start, start + CONTENT_SPAN)) * seg(tall.get(), CONTENT_GATE[0], CONTENT_GATE[1]);
    return { opacity: u, transform: [{ translateY: reducedMotion ? 0 : (1 - u) * CONTENT_RISE }] };
  });
  return <Animated.View style={[styles.rise, style, rise]}>{children}</Animated.View>;
}

const styles = StyleSheet.create({
  scrim: { backgroundColor: '#0d1f1d' },
  frame: {
    position: 'absolute',
    left: 0,
    top: 0,
    borderCurve: 'continuous',
    boxShadow: '0 1px 0 rgba(255,255,255,0.10) inset, 0 1px 2px rgba(10,30,28,0.28), 0 14px 32px rgba(10,30,28,0.26)',
  },
  clip: { borderCurve: 'continuous', overflow: 'hidden' },
  pinned: { position: 'absolute', left: 0, top: 0 },
  label: { position: 'absolute', left: 0, top: 0, fontSize: LABEL_SIZE, lineHeight: LABEL_LINE, fontWeight: '600' },
  thanks: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  check: { width: CHECK, height: CHECK },
  stroke: { position: 'absolute', height: STROKE, borderRadius: STROKE / 2, transformOrigin: 'left center' },
  thanksLabel: { fontSize: LABEL_SIZE, lineHeight: LABEL_LINE, fontWeight: '600' },
  rise: { position: 'absolute' },
});
