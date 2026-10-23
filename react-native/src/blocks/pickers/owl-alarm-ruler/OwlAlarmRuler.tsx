import { Canvas, createPicture, Picture } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { type ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { AccessibilityInfo, type AccessibilityActionEvent, AppState, Image, type StyleProp, StyleSheet, Text, useWindowDimensions, View, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, { cubicBezier, type SharedValue, useAnimatedReaction, useAnimatedStyle, useDerivedValue, useFrameCallback, useReducedMotion, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Line, Path, Polyline } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import {
  AUTO,
  clockNow,
  createEngine,
  dragMove,
  dragStart,
  type Engine,
  type Frame,
  HAPTIC_GAP,
  STARTLE,
  LABEL_GAP,
  LOOP,
  poseOf,
  PRESS,
  pressIn,
  pressOut,
  release,
  REST_FRAME,
  rulerDown,
  setAutoPlay,
  step,
  stepBy,
  timeLabel,
} from './motion';
import { drawBackdrop, drawRuler, type Geo } from './paint';
import { ASCENT, colors, copy, layout, radius, space, type as T, type TextStyleSpec } from './theme';
import { TimeOdometer } from './TimeOdometer';

export { LOOP };

const MASCOT = require('./assets/mascot.png') as number;
const PUPIL = require('./assets/mascot-pupil.png') as number;

type Props = {
  /** Plays the scripted drag, flick, return and button press every LOOP seconds. */
  autoPlay?: boolean;
};

const font = (s: TextStyleSpec) => ({ fontSize: s.size, fontWeight: s.weight, color: s.color, letterSpacing: s.ls }) as const;

function selectionHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.selectionAsync().catch(() => {});
}
function successHaptic() {
  if (process.env.EXPO_OS !== 'web') Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
}

export function OwlAlarmRuler({ autoPlay = false }: Props) {
  const { width: W, height: H } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const top = insets.top;
  const initialReduced = useReducedMotion();
  const [reduced, setReduced] = useState(initialReduced);
  const [busy, setBusy] = useState(true);
  const [foreground, setForeground] = useState(AppState.currentState === 'active');
  const geo: Geo = useMemo(() => ({ W, H, top }), [W, H, top]);
  const spacing = layout.ruler.spacing;

  const engine = useSharedValue<Engine>(createEngine(0, autoPlay, initialReduced));
  const frame = useSharedValue<Frame>(REST_FRAME);
  const still = useSharedValue(initialReduced);
  const lastTick = useSharedValue(0);
  const lastHaptic = useSharedValue(-1);
  const dragEnded = useSharedValue(false);
  /** The tick React shows (button label, a11y value), published at most every LABEL_GAP. */
  const labelTick = useSharedValue(0);
  const labelAt = useSharedValue(-1);

  useEffect(() => {
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => sub.remove();
  }, []);

  const sample = useCallback(
    (now: number) => {
      'worklet';
      const r = step(engine.get(), now);
      engine.set(r.e);
      frame.set(r.f);
      const tick = Math.round(r.f.pos);
      if (tick !== lastTick.get()) {
        if (r.haptic && now - lastHaptic.get() >= HAPTIC_GAP) {
          lastHaptic.set(now);
          scheduleOnRN(selectionHaptic);
        }
        lastTick.set(tick);
      }
      if (tick !== labelTick.get() && now - labelAt.get() >= LABEL_GAP) {
        labelTick.set(tick);
        labelAt.set(now);
      }
      const e = r.e;
      const moving =
        e.mode !== 1 ||
        e.dragging ||
        e.glideAt >= 0 ||
        r.f.startle >= 0 ||
        r.f.press > 0.001 ||
        Math.abs(e.fx) > 0.01 ||
        Math.abs(e.fv) > 0.1 ||
        (e.attention > 0.005 && e.attention < 0.995) ||
        Math.abs(r.f.flip - e.flipTo) > 0.05 ||
        r.f.blink > 0 ||
        (e.autoPlay && !e.still);
      return moving;
    },
    [engine, frame, labelAt, labelTick, lastHaptic, lastTick],
  );

  const onFrame = useCallback(() => {
    'worklet';
    const moving = sample(clockNow());
    if (!moving) scheduleOnRN(setBusy, false);
  }, [sample]);

  const callback = useFrameCallback(onFrame, false);
  useEffect(() => {
    callback.setActive(foreground && busy);
  }, [callback, foreground, busy]);

  const wake = useCallback(() => setBusy(true), []);

  // autoPlay and Reduce Motion: the engine decides what plays.
  useEffect(() => {
    scheduleOnUI((on: boolean, quiet: boolean) => {
      'worklet';
      still.set(quiet);
      engine.set(setAutoPlay(engine.get(), on, quiet, clockNow()));
      sample(clockNow());
      scheduleOnRN(wake);
    }, autoPlay, reduced);
  }, [autoPlay, reduced, engine, still, sample, wake]);

  // Background: restart from the rest frame on return (the loop time is not carried across).
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      const active = state === 'active';
      setForeground(active);
      if (active) {
        scheduleOnUI(() => {
          'worklet';
          const now = clockNow();
          const e = engine.get();
          engine.set({ ...e, autoStart: now, lastClock: now, dragging: false, glideAt: e.mode === AUTO ? -1 : e.glideAt });
          sample(now);
        });
        setBusy(true);
      }
    });
    return () => sub.remove();
  }, [engine, sample]);


  // ─── gestures ───
  const rulerPan = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX([-6, 6])
        .failOffsetY([-14, 14])
        .onBegin(() => {
          const now = clockNow();
          dragEnded.set(false);
          engine.set(rulerDown(engine.get(), now));
          sample(now);
          scheduleOnRN(wake);
        })
        .onStart(() => {
          engine.set(dragStart(engine.get(), clockNow()));
        })
        .onUpdate((ev) => {
          const now = clockNow();
          engine.set(dragMove(engine.get(), now, ev.translationX, ev.velocityX, spacing));
          sample(now);
        })
        .onEnd((ev) => {
          const now = clockNow();
          dragEnded.set(true);
          engine.set(release(engine.get(), now, ev.velocityX, spacing));
          sample(now);
        })
        .onFinalize(() => {
          // A touch that never became a drag (or was cancelled): settle on the nearest tick.
          if (!dragEnded.get()) {
            const now = clockNow();
            engine.set(release(engine.get(), now, 0, spacing));
            sample(now);
          }
          dragEnded.set(false);
        }),
    [dragEnded, engine, sample, spacing, wake],
  );

  const ctaIn = useCallback(() => {
    setBusy(true);
    scheduleOnUI(() => {
      'worklet';
      const now = clockNow();
      engine.set(pressIn(engine.get(), now));
      sample(now);
    });
  }, [engine, sample]);
  const ctaOut = useCallback(() => {
    scheduleOnUI(() => {
      'worklet';
      const now = clockNow();
      engine.set(pressOut(engine.get(), now, false));
      sample(now);
    });
  }, [engine, sample]);
  const ctaPress = useCallback(() => {
    successHaptic();
    scheduleOnUI(() => {
      'worklet';
      const now = clockNow();
      const e = engine.get();
      // A startle in progress plays out; a second press does not restart it mid-air.
      const busyStartle = now - e.startleAt < STARTLE.end;
      engine.set({ ...e, startleAt: e.still || busyStartle ? e.startleAt : now, lastTouch: now });
      sample(now);
    });
  }, [engine, sample]);

  const adjust = useCallback(
    (ev: AccessibilityActionEvent) => {
      const dir = ev.nativeEvent.actionName === 'increment' ? 1 : ev.nativeEvent.actionName === 'decrement' ? -1 : 0;
      if (!dir) return;
      setBusy(true);
      scheduleOnUI(() => {
        'worklet';
        const now = clockNow();
        engine.set(stepBy(engine.get(), now, dir));
        sample(now);
      });
    },
    [engine, sample],
  );

  // ─── per-frame visuals (UI thread) ───
  const pose = useDerivedValue(() => poseOf(frame.get(), still.get()));
  const backdrop = useDerivedValue(() => {
    const p = pose.get();
    return createPicture((canvas) => drawBackdrop(canvas, geo, p.sundial, p.lift));
  });
  const ruler = useDerivedValue(() => {
    const f = frame.get();
    const p = pose.get();
    return createPicture((canvas) => drawRuler(canvas, geo, f.pos, p.needle));
  });

  const mascotStyle = useAnimatedStyle(() => {
    const p = pose.get();
    return { transform: [{ translateY: p.jumpY }, { rotate: `${p.lean}deg` }, { scaleX: p.sx }, { scaleY: p.sy }] };
  });
  const ctaStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS.scale) * frame.get().press }] }));

  // ─── layout ───
  const m = layout.mascot;
  const mascotLeft = W / 2 + m.dx - m.w / 2;
  const back = layout.back;
  const tr = layout.track;
  const ctaW = W - 2 * space.screenInset;
  const ctaTop = H - insets.bottom - space.ctaBottom - layout.cta.h;
  const band = layout.ruler.band;
  const touch = layout.ruler.touch;
  const headFont = font(T.headline);
  const textTop = (base: number, s: TextStyleSpec) => top + base - ASCENT * s.size;

  return (
    <View style={styles.screen}>
      <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
        <Picture picture={backdrop} />
      </Canvas>

      {/* Header: back button and step progress. */}
      <BackButton left={space.backInset} top={top + space.headerTop} />
      <View
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel="Setup progress"
        accessibilityValue={{ min: 0, max: Math.round(tr.w), now: Math.round(tr.fill) }}
        style={[styles.track, { left: (W - tr.w) / 2, top: top + space.headerTop + back.d / 2 - tr.h / 2, width: tr.w, height: tr.h, borderRadius: tr.h / 2 }]}
      >
        <View style={{ width: tr.fill, height: tr.h, borderRadius: tr.h / 2, backgroundColor: colors.ink }} />
      </View>

      {/* The owl. */}
      <Animated.View
        pointerEvents="none"
        accessibilityElementsHidden
        importantForAccessibility="no-hide-descendants"
        style={[styles.abs, { left: mascotLeft, top: top + m.y, width: m.w, height: m.h, transformOrigin: ['50%', `${m.pivotY * 100}%`, 0] }, mascotStyle]}
      >
        <Image source={MASCOT} style={{ width: m.w, height: m.h }} fadeDuration={0} />
        {layout.eyes.map((eye, i) => (
          <Eye key={i} index={i} pose={pose} />
        ))}
      </Animated.View>

      {/* Copy. */}
      <Text allowFontScaling={false} style={[styles.center, headFont, { top: textTop(layout.headline[0].base, T.headline) }]} accessibilityRole="header">
        {copy.line1}
      </Text>
      <Text allowFontScaling={false} style={[styles.center, headFont, { top: textTop(layout.headline[1].base, T.headline) }]}>
        {copy.line2}
      </Text>

      <TimeOdometer frame={frame} top={top} still={reduced} />

      {/* The ruler: drag, flick, tap to settle; adjustable for VoiceOver. */}
      <GestureDetector gesture={rulerPan}>
        <RulerTouch labelTick={labelTick} onAction={adjust} style={[styles.abs, { left: 0, width: W, top: top + touch.top, height: touch.h }]}>
          <Canvas style={[styles.abs, { left: 0, width: W, top: band.top - touch.top, height: band.h }]} pointerEvents="none">
            <Picture picture={ruler} />
          </Canvas>
        </RulerTouch>
      </GestureDetector>

      <Text allowFontScaling={false} style={[styles.center, font(T.caption), { top: textTop(layout.caption.base, T.caption) }]}>
        {copy.caption}
      </Text>

      {/* The button: its label follows the time. */}
      <Animated.View style={[styles.abs, { left: space.screenInset, top: ctaTop, width: ctaW, height: layout.cta.h }, ctaStyle]}>
        <CtaButton labelTick={labelTick} pose={pose} onPressIn={ctaIn} onPressOut={ctaOut} onPress={ctaPress} />
      </Animated.View>
    </View>
  );
}



const EASE_OUT = cubicBezier(0.23, 1, 0.32, 1);

/** Back: a quiet press (0.97, 100 ms in, 200 ms out); the demo has no previous step. */
function BackButton({ left, top }: { left: number; top: number }) {
  const [pressed, setPressed] = useState(false);
  const d = layout.back.d;
  const ba = layout.back.arrow;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={copy.a11yBack}
      hitSlop={(space.hit - d) / 2 + 2}
      onPressIn={() => setPressed(true)}
      onPressOut={() => setPressed(false)}
      style={[styles.abs, { left, top, width: d, height: d }]}
    >
      <Animated.View
        style={[
          styles.backFace,
          {
            width: d,
            height: d,
            borderRadius: d / 2,
            transform: [{ scale: pressed ? PRESS.scale : 1 }],
            transitionProperty: 'transform',
            transitionDuration: (pressed ? PRESS.in : PRESS.out) * 1000,
            transitionTimingFunction: EASE_OUT,
          },
        ]}
      >
        <Svg width={d} height={d} pointerEvents="none">
          <Polyline
            points={`${ba.armX},${d / 2 - ba.armDy} ${ba.tipX},${d / 2} ${ba.armX},${d / 2 + ba.armDy}`}
            stroke={colors.ink}
            strokeWidth={ba.stroke}
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
          />
          <Line x1={ba.tipX} y1={d / 2} x2={ba.endX} y2={d / 2} stroke={colors.ink} strokeWidth={ba.stroke} strokeLinecap="round" />
        </Svg>
      </Animated.View>
    </Pressable>
  );
}

type Pose = ReturnType<typeof poseOf>;

/** The tick's time as React state, for the few views that need text (not per frame). */
function useTickLabel(labelTick: SharedValue<number>) {
  const [label, setLabel] = useState(() => timeLabel(0));
  useAnimatedReaction(
    () => labelTick.get(),
    (tick, prev) => {
      if (tick !== prev) scheduleOnRN(setLabel, timeLabel(tick));
    },
  );
  return label;
}

/** The ruler's touch area; VoiceOver adjusts it a tick at a time. */
function RulerTouch({ labelTick, onAction, style, children }: { labelTick: SharedValue<number>; onAction: (ev: AccessibilityActionEvent) => void; style: StyleProp<ViewStyle>; children: ReactNode }) {
  const label = useTickLabel(labelTick);
  return (
    <View
      style={style}
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={copy.a11yRuler}
      accessibilityHint={copy.a11yRulerHint}
      accessibilityValue={{ text: label }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={onAction}
    >
      {children}
    </View>
  );
}

const ARROW = layout.ctaArrow;
const ARROW_H = ARROW.headH;

/** The button's face: its label follows the time; the arrow nudges after a press. */
function CtaButton({ labelTick, pose, onPressIn, onPressOut, onPress }: { labelTick: SharedValue<number>; pose: SharedValue<Pose>; onPressIn: () => void; onPressOut: () => void; onPress: () => void }) {
  const label = useTickLabel(labelTick);
  const arrowStyle = useAnimatedStyle(() => ({ transform: [{ translateX: pose.get().arrow }] }));
  const a = ARROW;
  const arrowW = a.shaft + a.stroke / 2;
  const arrowH = ARROW_H;
  return (
        <Pressable
          onPressIn={onPressIn}
          onPressOut={onPressOut}
          onPress={onPress}
          accessibilityRole="button"
          accessibilityLabel={`${copy.cta} ${label}`}
          style={[styles.cta, { borderRadius: radius.cta }]}
        >
          <Text allowFontScaling={false} style={[styles.abs, font(T.cta), styles.tabular, { left: space.ctaPadStart, top: layout.cta.base - ASCENT * T.cta.size }]}>
            {`${copy.cta} ${label}`}
          </Text>
          <Animated.View style={[styles.abs, { right: space.ctaPadEnd, top: (layout.cta.h - arrowH) / 2, width: arrowW + a.headW, height: arrowH }, arrowStyle]}>
            <Svg width={arrowW + a.headW} height={arrowH}>
              <Line x1={a.stroke / 2} y1={arrowH / 2} x2={arrowW + a.headW * 0.4} y2={arrowH / 2} stroke={colors.white} strokeWidth={a.stroke} strokeLinecap="round" />
              <Path
                d={`M ${arrowW + a.headR} ${a.headR} L ${arrowW + a.headW - a.headR} ${arrowH / 2} L ${arrowW + a.headR} ${arrowH - a.headR} Z`}
                fill={colors.white}
                stroke={colors.white}
                strokeWidth={2 * a.headR}
                strokeLinejoin="round"
              />
            </Svg>
          </Animated.View>
        </Pressable>
  );
}


/** One eye: the pupil looks around inside the eye white; two lids meet at LID_CLOSE of its height. */
function Eye({ index, pose }: { index: number; pose: SharedValue<Pose> }) {
  const eye = layout.eyes[index];
  const rest = layout.pupil.rest[index];
  const d = layout.pupil.d;
  const r = eye.r;
  const pupil = useAnimatedStyle(() => {
    const p = pose.get();
    return { transform: [{ translateX: p.pupilDX }, { translateY: p.pupilDY }, { scale: p.pupilScale }] };
  });
  const upper = useAnimatedStyle(() => ({ transform: [{ translateY: -2 * r - 1 + (2 * r + 1 - 2 * r * (1 - LID_CLOSE)) * pose.get().blink }] }));
  const lower = useAnimatedStyle(() => ({ transform: [{ translateY: 2 * r + 1 - (2 * r + 1 - 2 * r * LID_CLOSE) * pose.get().blink }] }));
  return (
    <View style={[styles.eye, { left: eye.cx - r, top: eye.cy - r, width: 2 * r, height: 2 * r, borderRadius: r }]}>
      <Animated.Image source={PUPIL} fadeDuration={0} style={[styles.abs, { width: d, height: d, left: r - d / 2 + rest.x, top: r - d / 2 + rest.y }, pupil]} />
      <Animated.View style={[styles.abs, styles.lidUpper, { left: 0, top: 0, width: 2 * r, height: 2 * r }, upper]} />
      <Animated.View style={[styles.abs, styles.lid, { left: 0, top: 0, width: 2 * r, height: 2 * r }, lower]} />
    </View>
  );
}

/** Where the lids meet when the eye is closed (fraction of the eye's height). */
const LID_CLOSE = 0.6;

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.page, overflow: 'hidden' },
  abs: { position: 'absolute' },
  backFace: { backgroundColor: colors.backFill },
  track: { position: 'absolute', backgroundColor: colors.track, overflow: 'hidden' },
  eye: { position: 'absolute', overflow: 'hidden' },
  lid: { backgroundColor: colors.lid },
  lidUpper: { backgroundColor: colors.lid, borderBottomWidth: layout.lash / 3, borderBottomColor: colors.lash },
  center: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  cta: { flex: 1, backgroundColor: colors.cta },
  tabular: { fontVariant: ['tabular-nums'] },
});

