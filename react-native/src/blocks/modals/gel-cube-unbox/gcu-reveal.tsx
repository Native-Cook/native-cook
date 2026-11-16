import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, findNodeHandle, ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector, Pressable } from 'react-native-gesture-handler';
import Animated, {
  cancelAnimation,
  interpolateColor,
  type SharedValue,
  useAnimatedReaction,
  useAnimatedStyle,
  useDerivedValue,
  useReducedMotion,
  useSharedValue,
  withDelay,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Canvas, Fill, Shader, Skia } from '@shopify/react-native-skia';
import { scheduleOnRN } from 'react-native-worklets';

import { useGelFeedback } from './gcu-haptics';
import { angleAt, clamp01, contentAt, coreAt, fanAt, flashAt, lightAt, MOTION, progressAt, rubber, SPRING } from './gcu-motion';
import { useGcuPlayback } from './gcu-playback';
import { GEL_SKSL } from './gcu-shaders';
import { comp, COPY, makeLayout, paletteOf, sys } from './gcu-theme';

export type LightBoxCopy = { action: string; chip?: string; sheetTitle?: string; sheetBody?: string };
export type LightBoxRevealProps = {
  tint?: string;
  open?: boolean;
  defaultOpen?: boolean;
  progress?: SharedValue<number>;
  onOpenChange?: (open: boolean) => void;
  onProgress?: (progress: number) => void;
  title?: string;
  subtitle?: string;
  sealed?: LightBoxCopy;
  opened?: LightBoxCopy;
  onAction?: (state: 'sealed' | 'open') => void;
  onClose?: () => void;
  size?: number;
  lidWeight?: number;
  tokens?: { font?: string; ink?: string; mute?: string; glassFill?: number; glassStroke?: number; radius?: number };
  children?: ReactNode;
  autoPlay?: boolean;
  /** Muted by default; never plays during autoplay. */
  sound?: boolean;
};

const EFFECT = Skia.RuntimeEffect.Make(GEL_SKSL);
const FONT_SOURCES = { GcuInter: require('./assets/fonts/Inter-Regular.ttf'), GcuInterMedium: require('./assets/fonts/Inter-Medium.ttf') };

/** Reusable analytic gel reveal. Shared progress is writable by gestures; boolean open remains parent-controlled. */
export function LightBoxReveal({
  tint = sys.color.tint,
  open,
  defaultOpen = false,
  progress: suppliedProgress,
  onOpenChange,
  onProgress,
  title = COPY.title,
  subtitle = COPY.subtitle,
  sealed = COPY.sealed,
  opened = COPY.opened,
  onAction,
  onClose,
  size = comp.cube.size,
  lidWeight = SPRING.lid.mass,
  tokens,
  children,
  autoPlay = false,
  sound = false,
}: LightBoxRevealProps) {
  const window = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const [bounds, setBounds] = useState({ width: window.width, height: window.height });
  const [loaded, fontError] = useFonts(FONT_SOURCES);
  const [ready, setReady] = useState(false);
  useEffect(() => {
    if (!loaded && !fontError) return;
    let second = 0;
    const first = requestAnimationFrame(() => {
      second = requestAnimationFrame(() => setReady(true));
    });
    return () => {
      cancelAnimationFrame(first);
      cancelAnimationFrame(second);
    };
  }, [loaded, fontError]);
  const reduced = useReducedMotion();
  const feedback = useGelFeedback(sound);
  const showClose = onClose !== undefined;
  const customSheet = children !== undefined && children !== null;
  const controlFlags = (showClose ? 1 : 0) + (customSheet ? 0 : 2);
  const controlled = open !== undefined;
  const external = suppliedProgress !== undefined;
  const filmEnabled = autoPlay && !controlled && !external && !reduced && ready;
  const L = makeLayout(bounds.width, bounds.height, insets.top, insets.bottom);
  const S = L.S;
  const ox = L.ox,
    oy = L.oy;
  const largeType = window.fontScale > comp.largeTypeThreshold;
  const pal = useMemo(() => paletteOf(tint), [tint]);
  const localProgress = useSharedValue(open || defaultOpen ? 1 : 0);
  const p = suppliedProgress ?? localProgress;
  const t = useSharedValue(0);
  const mode = useSharedValue(filmEnabled && !defaultOpen ? 0 : 1); // 0 film, 1 touch/static, 2 pinned
  const manualCore = useSharedValue(1);
  const sealOffset = useSharedValue(0);
  const start = useSharedValue(0);
  const dragging = useSharedValue(false);
  const sealBroken = useSharedValue(false);
  const crossed = useSharedValue(false);
  const closing = useSharedValue(false);
  const closeLead = useSharedValue(0);
  const flash = useSharedValue(0);
  const lastCallback = useSharedValue(0);
  const lastHaptic = useSharedValue(0);
  const pressed = useSharedValue(false);
  const [semanticOpen, setSemanticOpen] = useState(open ?? defaultOpen);
  const sheet = useRef<View>(null);
  const { stopFilm, afterTouch, restart } = useGcuPlayback(filmEnabled, defaultOpen, p, t, mode, closing, closeLead);

  const commit = useCallback((next: boolean) => onOpenChange?.(next), [onOpenChange]);
  const reportProgress = useCallback((value: number) => onProgress?.(value), [onProgress]);

  useEffect(
    () => () => {
      cancelAnimation(manualCore);
      cancelAnimation(sealOffset);
      cancelAnimation(flash);
    },
    [manualCore, sealOffset, flash],
  );

  useEffect(() => {
    if (!controlled) return;
    mode.set(1);
    cancelAnimation(p);
    p.set(reduced ? withTiming(open ? 1 : 0, { duration: MOTION.fadeMs }) : withSpring(open ? 1 : 0, { ...SPRING.lid, mass: lidWeight }));
  }, [controlled, lidWeight, mode, open, p, reduced]);

  useAnimatedReaction(
    () => p.get() + sealOffset.get(),
    (value, previous) => {
      const now = Date.now();
      const isOpen = value >= 0.8;
      if (previous === null || isOpen !== previous >= 0.8) scheduleOnRN(setSemanticOpen, isOpen);
      if (onProgress && now - lastCallback.get() >= MOTION.callbackMs) {
        lastCallback.set(now);
        scheduleOnRN(reportProgress, clamp01(value));
      }
      if (mode.get() === 1 && closing.get() && previous !== null && previous > MOTION.hairline && value <= MOTION.hairline) {
        flash.set(
          withSequence(withTiming(MOTION.flashGain, { duration: 0 }), withDelay(MOTION.flash * 1000, withTiming(0, { duration: 0 }))),
        );
      }
      if (mode.get() === 1 && closing.get() && value <= MOTION.contact && previous !== null && previous > MOTION.contact) {
        closing.set(false);
        if (now - lastHaptic.get() >= MOTION.cueInterval) {
          lastHaptic.set(now);
          scheduleOnRN(feedback, 'close');
        }
      }
    },
  );

  useEffect(() => {
    if (!__DEV__) return;
    const hook = {
      pin: (time: number) => {
        stopFilm();
        cancelAnimation(p);
        mode.set(2);
        t.set(time);
        sealOffset.set(0);
        p.set(progressAt(time));
      },
      at: (time: number) => {
        hook.pin(time);
      },
      play: () => {
        cancelAnimation(p);
        sealOffset.set(0);
        restart();
      },
      progress: (value: number) => {
        stopFilm();
        cancelAnimation(p);
        mode.set(1);
        sealOffset.set(0);
        manualCore.set(1);
        p.set(clamp01(value));
      },
      state: () => ({ t: t.get(), progress: p.get(), mode: mode.get(), reduced }),
    };
    (globalThis as Record<string, unknown>).__gcu = hook;
    return () => {
      if ((globalThis as Record<string, unknown>).__gcu === hook) delete (globalThis as Record<string, unknown>).__gcu;
    };
  }, [manualCore, mode, p, reduced, restart, sealOffset, stopFilm, t]);

  const toggle = useCallback(() => {
    stopFilm();
    const current = p.get() + sealOffset.get();
    const next = current < MOTION.threshold;
    cancelAnimation(p);
    mode.set(1);
    sealOffset.set(0);
    manualCore.set(1);
    closing.set(!next);
    closeLead.set(next ? 0 : MOTION.fanLead);
    feedback(next ? 'seal' : 'grab');
    p.set(
      reduced
        ? withTiming(controlled ? (open ? 1 : 0) : next ? 1 : 0, { duration: MOTION.fadeMs })
        : withSpring(controlled ? (open ? 1 : 0) : next ? 1 : 0, { ...SPRING.lid, mass: lidWeight }),
    );
    commit(next);
    afterTouch();
  }, [afterTouch, closeLead, closing, commit, controlled, feedback, lidWeight, manualCore, mode, open, p, reduced, sealOffset, stopFilm]);

  const handleBegin = useCallback(() => {
    'worklet';
    const wasFilm = mode.get() !== 1;
    cancelAnimation(p);
    cancelAnimation(sealOffset);
    const current = p.get() + sealOffset.get();
    p.set(current);
    sealOffset.set(0);
    start.set(current);
    mode.set(1);
    dragging.set(false);
    closing.set(false);
    closeLead.set(0);
    flash.set(0);
    sealBroken.set(current > MOTION.hairline);
    crossed.set(current > MOTION.threshold);
    if (wasFilm) manualCore.set(coreAt(t.get()));
    manualCore.set(withSpring(MOTION.pressCore, SPRING.core));
    lastHaptic.set(Date.now());
    scheduleOnRN(stopFilm);
    scheduleOnRN(feedback, 'grab');
  }, [closeLead, closing, crossed, dragging, feedback, flash, lastHaptic, manualCore, mode, p, sealBroken, sealOffset, start, stopFilm, t]);
  const handleUpdate = useCallback(
    (event: { translationY: number }) => {
      'worklet';
      const distance = -event.translationY / S;
      dragging.set(Math.abs(distance) >= MOTION.seal);
      if (!sealBroken.get() && distance < MOTION.seal && start.get() < MOTION.hairline) {
        p.set(rubber(start.get() + (distance * MOTION.resistance) / MOTION.drag));
        return;
      }
      if (!sealBroken.get() && distance >= MOTION.seal) {
        sealBroken.set(true);
        sealOffset.set((-MOTION.seal * (1 - MOTION.resistance)) / MOTION.drag);
        sealOffset.set(withSpring(0, SPRING.seal));
        if (Date.now() - lastHaptic.get() >= MOTION.cueInterval) {
          lastHaptic.set(Date.now());
          scheduleOnRN(feedback, 'seal');
        }
      }
      const next = rubber(start.get() + distance / MOTION.drag);
      p.set(next);
      if (!crossed.get() && next >= MOTION.threshold) {
        crossed.set(true);
        if (Date.now() - lastHaptic.get() >= MOTION.cueInterval) {
          lastHaptic.set(Date.now());
          scheduleOnRN(feedback, 'threshold');
        }
      }
    },
    [S, crossed, dragging, feedback, lastHaptic, p, sealBroken, sealOffset, start],
  );
  const handleEnd = useCallback(
    (event: { velocityY: number }) => {
      'worklet';
      manualCore.set(withSpring(1, SPRING.core));
      if (!dragging.get()) {
        p.set(withSpring(controlled ? (open ? 1 : 0) : start.get(), SPRING.close));
        scheduleOnRN(afterTouch);
        return;
      }
      const velocity = -event.velocityY / S / MOTION.drag;
      const next = p.get() + velocity * MOTION.projection > MOTION.threshold;
      const target = controlled ? (open ? 1 : 0) : next ? 1 : 0;
      closing.set(target === 0);
      closeLead.set(target === 0 ? MOTION.fanLead * Math.max(1, Math.abs(velocity)) : 0);
      closeLead.set(withTiming(0, { duration: MOTION.reset }));
      if (target === 1 && -event.velocityY / S > MOTION.flick) {
        p.set(
          withSequence(
            withSpring(MOTION.overshoot, { ...SPRING.flick, mass: lidWeight, velocity, overshootClamping: true }),
            withSpring(1, { ...SPRING.flick, mass: lidWeight, overshootClamping: true }),
          ),
        );
      } else p.set(withSpring(target, { ...(target ? SPRING.lid : SPRING.close), mass: lidWeight, velocity }));
      scheduleOnRN(commit, next);
      scheduleOnRN(afterTouch);
    },
    [S, afterTouch, closeLead, closing, commit, controlled, dragging, lidWeight, manualCore, open, p, start],
  );
  const handleFinalize = useCallback(
    (_event: unknown, success: boolean) => {
      'worklet';
      manualCore.set(withSpring(1, SPRING.core));
      if (!success) {
        p.set(withSpring(controlled ? (open ? 1 : 0) : start.get(), SPRING.close));
        scheduleOnRN(afterTouch);
      }
    },
    [afterTouch, controlled, manualCore, open, p, start],
  );
  const pan = useMemo(
    () =>
      Gesture.Pan()
        .minDistance(0)
        .maxPointers(1)
        .enabled(!reduced)
        .onBegin(handleBegin)
        .onUpdate(handleUpdate)
        .onEnd(handleEnd)
        .onFinalize(handleFinalize),
    [handleBegin, handleUpdate, handleEnd, handleFinalize, reduced],
  );

  const uniforms = useDerivedValue(() => {
    const progress = p.get() + sealOffset.get();
    const timeline = mode.get() !== 1;
    const visual = reduced ? 0 : progress;
    return {
      uFrame: [ox, oy, S, controlFlags],
      uShape: [
        Math.max(0.4, size / comp.cube.size),
        tokens?.glassFill ?? sys.material.glassFill,
        tokens?.glassStroke ?? sys.material.glassStroke,
        tokens?.radius ?? sys.radius.sheet,
      ],
      uState: [
        visual,
        reduced ? 1 + clamp01(progress) * 0.25 : timeline ? coreAt(t.get()) : manualCore.get(),
        reduced ? 0 : timeline ? fanAt(t.get()) : Math.max(0, visual - closeLead.get()),
        timeline ? coreAt(t.get() - MOTION.causticLag) : manualCore.get(),
      ],
      uOptics: [reduced ? 0 : angleAt(progress), reduced ? 0 : timeline ? flashAt(t.get()) : flash.get(), 1, sys.material.grain],
      uTint: pal.tint,
      uPage: pal.page,
      uFloor: pal.floor,
      uEdge: pal.edge,
      uCore: pal.core,
      uFan: pal.fan,
      uUnder: pal.underside,
    };
  });
  const closedStyle = useAnimatedStyle(() => ({ opacity: 1 - (reduced ? clamp01(p.get()) : contentAt(p.get() + sealOffset.get())) }));
  const openedStyle = useAnimatedStyle(() => ({ opacity: reduced ? clamp01(p.get()) : contentAt(p.get() + sealOffset.get()) }));
  const titleStyle = useAnimatedStyle(() => ({
    color: interpolateColor(lightAt(p.get(), 0.1), [0, 1], [sys.color.titleRest, sys.color.ink]),
    textShadowColor: `rgba(${comp.titleShadow.rgb},${comp.titleShadow.alpha * lightAt(p.get(), MOTION.light.headline)})`,
    textShadowRadius: comp.titleShadow.radius * S,
    textShadowOffset: { width: 0, height: comp.titleShadow.y * S },
  }));
  const subtitleLightStyle = useAnimatedStyle(() => ({
    color: interpolateColor(lightAt(p.get(), 0.16), [0, 1], [tokens?.mute ?? sys.color.mute, sys.color.ink]),
  }));
  const actionInkStyle = useAnimatedStyle(() => ({
    color: interpolateColor(lightAt(reduced ? 0 : p.get(), 0.28), [0, 1], [tokens?.ink ?? sys.color.ink, sys.color.litInk]),
  }));
  const chipInkStyle = useAnimatedStyle(() => ({
    color: interpolateColor(lightAt(reduced ? 0 : p.get(), MOTION.light.chip), [0, 1], [tokens?.ink ?? sys.color.ink, sys.color.litInk]),
  }));
  const sheetInkStyle = useAnimatedStyle(() => ({
    color: interpolateColor(
      lightAt(reduced ? 0 : p.get(), MOTION.light.sheetTitle),
      [0, 1],
      [tokens?.ink ?? sys.color.ink, sys.color.litInk],
    ),
  }));
  const sheetMuteStyle = useAnimatedStyle(() => ({
    color: interpolateColor(
      lightAt(reduced ? 0 : p.get(), MOTION.light.sheetBody),
      [0, 1],
      [tokens?.mute ?? sys.color.mute, sys.color.litMute],
    ),
  }));
  const actionStyle = useAnimatedStyle(() => ({ transform: [{ scale: reduced ? 1 : pressed.get() ? comp.press : 1 }] }));
  const fontFamily = tokens?.font ?? (loaded ? sys.type.display.family : undefined);
  const ink = tokens?.ink ?? sys.color.ink;
  const mute = tokens?.mute ?? sys.color.mute;
  const textStyle = {
    fontFamily,
    color: ink,
    includeFontPadding: false,
    textAlign: 'center' as const,
    letterSpacing: sys.type.display.tracking * S,
  };
  const box = (r: { x: number; y: number; w: number; h: number }) => ({
    position: 'absolute' as const,
    left: L.x(r.x),
    top: L.y(r.y),
    width: r.w * S,
    minHeight: r.h * S,
  });
  const sheetLabel = semanticOpen ? opened : sealed;
  const focusSheet = () => {
    const handle = findNodeHandle(sheet.current);
    if (handle) AccessibilityInfo.setAccessibilityFocus(handle);
  };
  const pressActionIn = () => {
    pressed.set(true);
    stopFilm();
    cancelAnimation(p);
    const current = p.get() + sealOffset.get();
    if (mode.get() !== 1) manualCore.set(coreAt(t.get()));
    p.set(current);
    sealOffset.set(0);
    mode.set(1);
  };
  const pressActionOut = () => {
    pressed.set(false);
    afterTouch();
  };
  const act = () => {
    if (!semanticOpen) toggle();
    else {
      focusSheet();
      afterTouch();
    }
    onAction?.(semanticOpen ? 'open' : 'sealed');
  };
  const bodyPair = (
    key: 'chip' | 'sheetTitle' | 'sheetBody',
    sizePt: number,
    linePt: number,
    r: { x: number; y: number; w: number; h: number },
    color = ink,
  ) => (
    <View pointerEvents="none" style={box(r)} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <Animated.Text
        style={[
          textStyle,
          { color, fontSize: sizePt * S, lineHeight: linePt * S },
          key === 'sheetBody' ? sheetMuteStyle : key === 'chip' ? chipInkStyle : sheetInkStyle,
          closedStyle,
        ]}
      >
        {sealed[key]}
      </Animated.Text>
      <Animated.Text
        style={[
          styles.overlaidText,
          textStyle,
          { color, fontSize: sizePt * S, lineHeight: linePt * S },
          key === 'sheetBody' ? sheetMuteStyle : key === 'chip' ? chipInkStyle : sheetInkStyle,
          openedStyle,
        ]}
      >
        {opened[key]}
      </Animated.Text>
    </View>
  );
  const action = (flow = false) => (
    <Animated.View style={[flow ? styles.flowAction : box(comp.pill), actionStyle]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={sheetLabel.action}
        onPress={act}
        onPressIn={pressActionIn}
        onPressOut={pressActionOut}
        style={[styles.fill, { minHeight: comp.minHit }]}
      >
        {flow ? (
          <Text style={[textStyle, styles.flowActionLabel]}>{sheetLabel.action}</Text>
        ) : (
          <View style={styles.center} pointerEvents="none">
            <Animated.Text style={[textStyle, { fontSize: sys.type.action.size * S }, actionInkStyle, closedStyle]}>
              {sealed.action}
            </Animated.Text>
            <Animated.Text style={[styles.overlaidAction, textStyle, { fontSize: sys.type.action.size * S }, actionInkStyle, openedStyle]}>
              {opened.action}
            </Animated.Text>
          </View>
        )}
      </Pressable>
    </Animated.View>
  );

  return (
    <View
      style={[styles.screen, { backgroundColor: tint }]}
      onLayout={(event) => {
        const { width, height } = event.nativeEvent.layout;
        setBounds((old) => (old.width === width && old.height === height ? old : { width, height }));
      }}
    >
      <StatusBar style="light" />
      <View pointerEvents="none" style={StyleSheet.absoluteFill} accessible={false} accessibilityElementsHidden>
        {EFFECT && (
          <Canvas style={styles.fill}>
            <Fill>
              <Shader source={EFFECT} uniforms={uniforms} />
            </Fill>
          </Canvas>
        )}
      </View>
      {ready &&
        (largeType ? (
          <ScrollView
            contentContainerStyle={{
              paddingTop: Math.max(insets.top, L.y(comp.touch.y + comp.touch.h) + sys.space[6]),
              paddingBottom: insets.bottom + sys.space[6],
              paddingHorizontal: sys.space[6],
            }}
          >
            <View
              style={[
                styles.flowBackdrop,
                {
                  borderRadius: sys.radius.sheet,
                  backgroundColor: `rgba(${pal.page.map((v) => Math.round(v * 255)).join(',')},${comp.flow.alpha})`,
                },
              ]}
            >
              <Text accessibilityRole="header" style={[textStyle, styles.flowTitle]}>
                {title}
              </Text>
              <Text style={[textStyle, styles.flowBody, { color: mute }]}>{subtitle}</Text>
              {action(true)}
              <View
                ref={sheet}
                accessible={!customSheet}
                accessibilityLabel={
                  customSheet ? undefined : [sheetLabel.chip, sheetLabel.sheetTitle, sheetLabel.sheetBody].filter(Boolean).join('. ')
                }
              >
                {children ?? (
                  <>
                    <Text style={[textStyle, styles.flowChip]}>{sheetLabel.chip}</Text>
                    <Text style={[textStyle, styles.flowTitle]}>{sheetLabel.sheetTitle}</Text>
                    <Text style={[textStyle, styles.flowBody, { color: mute }]}>{sheetLabel.sheetBody}</Text>
                  </>
                )}
              </View>
            </View>
          </ScrollView>
        ) : (
          <>
            <Animated.Text
              accessibilityRole="header"
              style={[
                box(comp.title),
                textStyle,
                { fontSize: sys.type.display.size * S, lineHeight: sys.type.display.lineHeight * S },
                titleStyle,
              ]}
            >
              {title}
            </Animated.Text>
            <Animated.Text
              style={[
                box(comp.subtitle),
                textStyle,
                { color: mute, fontSize: sys.type.body.size * S, lineHeight: sys.type.body.lineHeight * S },
                subtitleLightStyle,
              ]}
            >
              {subtitle}
            </Animated.Text>
            {action()}
            <View
              ref={sheet}
              accessible={!customSheet}
              accessibilityLabel={
                customSheet ? undefined : [sheetLabel.chip, sheetLabel.sheetTitle, sheetLabel.sheetBody].filter(Boolean).join('. ')
              }
              style={box(comp.sheet)}
            />
            {customSheet ? (
              <View style={[box(comp.sheetBody), { top: L.y(comp.chip.y) }]}>{children}</View>
            ) : (
              <>
                {bodyPair('chip', sys.type.chip.size, 20, { ...comp.chip, x: comp.chip.x + 12, w: comp.chip.w - 12, y: comp.chip.y + 7 })}
                {bodyPair('sheetTitle', sys.type.display.size, 32, comp.sheetTitle)}
                {bodyPair('sheetBody', sys.type.body.size, 23, comp.sheetBody, mute)}
              </>
            )}
          </>
        ))}
      <GestureDetector gesture={pan}>
        <Animated.View
          collapsable={false}
          accessible
          accessibilityRole="button"
          accessibilityLabel={semanticOpen ? 'Close your Lumen Box' : 'Open your Lumen Box'}
          accessibilityHint="Drag upward to lift the lid, or activate to open."
          accessibilityState={{ expanded: semanticOpen }}
          onAccessibilityTap={toggle}
          style={box(comp.touch)}
        >
          {reduced && <Pressable accessibilityElementsHidden onPress={toggle} style={styles.fill} />}
        </Animated.View>
      </GestureDetector>
      {onClose && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Close reward"
          onPress={onClose}
          style={[box(comp.close), { width: Math.max(comp.minHit, comp.close.w * S), height: Math.max(comp.minHit, comp.close.h * S) }]}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, overflow: 'hidden' },
  fill: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  overlaidText: { position: 'absolute', top: 0, left: 0, right: 0 },
  overlaidAction: { position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  flowBackdrop: { padding: comp.flow.padding, gap: comp.flow.gap },
  flowTitle: { fontSize: comp.flow.title, lineHeight: comp.flow.titleLine },
  flowBody: { fontSize: comp.flow.body, lineHeight: comp.flow.bodyLine },
  flowChip: { fontSize: comp.flow.chip, lineHeight: comp.flow.chipLine, marginBottom: sys.space[3] },
  flowAction: {
    minHeight: comp.flow.actionHeight,
    borderRadius: comp.flow.actionRadius,
    backgroundColor: sys.color.flowGlass,
    justifyContent: 'center',
    marginVertical: sys.space[2],
  },
  flowActionLabel: { fontSize: comp.flow.action, padding: sys.space[3] },
});
