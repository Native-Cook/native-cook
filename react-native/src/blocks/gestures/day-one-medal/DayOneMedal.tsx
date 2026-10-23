import { useFonts } from 'expo-font';
import { type ReactNode, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  cubicBezier, Easing, FadeIn, FadeOut, ReduceMotion, useAnimatedStyle, useDerivedValue,
  useReducedMotion, useSharedValue, withDelay, withSpring, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { createMedalDemo, type DemoEvent } from './medal-demo';
import engraving from './assets/medal-engrave-path.json';
import { MedalCanvas } from './MedalCanvas';
import { MedalRenderer } from './MedalRenderer';
import { MedalConfetti } from './MedalConfetti';
import { MEDAL_ICONS } from './medal-icons';
import { medalRotation } from './medal-orientation';
import { MedalRibbon } from './MedalRibbon';
import { MedalSparks } from './MedalSparks';
import { MedalTitle } from './MedalTitle';
import { D, S, T, tone, medalLayout } from './medal-theme';
import { type MedalMoment, useMedalMotion } from './use-medal-motion';

type Props = { autoPlay?: boolean; renderer?: 'auto' | 'gpu' | 'skia' };
const wide = { black: 'DayOneMedal-Black', regular: 'DayOneMedal-Regular' } as const;
const ICON = {
  // A medal on its ribbon, and a capsule: both drawn for this block on a 24 pt grid.
  medal: [
    'M4 2H9L12 8.2L9 10.2Z',
    'M15 2H20L15 10.2L12 8.2Z',
    'M12 8.5A7 7 0 1 1 12 22.5A7 7 0 1 1 12 8.5Z M12 11.1L10.7 14L7.5 14.3L9.9 16.5L9.2 19.7L12 18.1L14.8 19.7L14.1 16.5L16.5 14.3L13.3 14Z',
  ],
  capsule: [
    'M12 2A7 7 0 0 1 19 9V15A7 7 0 0 1 5 15V9A7 7 0 0 1 12 2Z M12 7.9L10.7 10.6L7.7 11L9.9 13.1L9.4 16.1L12 14.7L14.6 16.1L14.1 13.1L16.3 11L13.3 10.6Z',
  ],
  close: ['M7.4 6L12 10.6L16.6 6L18 7.4L13.4 12L18 16.6L16.6 18L12 13.4L7.4 18L6 16.6L10.6 12L6 7.4Z'],
} as const;


const SHELF = [
  { title: 'Goals', slots: ['calendar', 'flame'] },
  { title: 'Special', slots: ['laurel', 'earned'] },
  { title: 'Cities', slots: ['pagoda', 'tower', 'bridge', 'lantern', 'gate', 'skyline', 'lighthouse'] },
] as const;
const DAYS = [1, 7, 30];
const COPY = [
  "Starting is the hardest part. Let's get going.\nDay 1!",
  'A full week. The habit is taking shape.\nDay 7!',
  'Thirty days. This is who you are now.\nDay 30!',
];
const SHEET_EASE = Easing.bezier(...T.sheetCurve);
const INITIAL: MedalMoment = { forged: false, worn: false, day: 0, engraved: false };

function Glyph({ paths, size, colour }: { paths: readonly string[]; size: number; colour: string }) {
  return <Svg width={size} height={size} viewBox="0 0 24 24">
    {paths.map((d) => <Path key={d} d={d} fill={colour} fillRule="evenodd" />)}
  </Svg>;
}

function Squeeze({ label, onPress, style, children, disabled = false }: {
  label: string; onPress: () => void; style: object | object[]; children: ReactNode; disabled?: boolean;
}) {
  const [down, setDown] = useState(false);
  return <Pressable accessibilityRole="button" accessibilityLabel={label}
    accessibilityState={{ disabled }} disabled={disabled} hitSlop={6} pressRetentionOffset={16}
    onPressIn={() => setDown(true)} onPressOut={() => setDown(false)} onPress={onPress}>
    <Animated.View style={[style, {
      transform: [{ scale: down && !disabled ? S.press : 1 }],
      transitionProperty: 'transform', transitionDuration: T.press,
      transitionTimingFunction: cubicBezier(...T.uiCurve),
    }]}>{children}</Animated.View>
  </Pressable>;
}

/** The slot and flight use the same slab, shader and half-height: no bitmap outline handoff. */
function MedalThumbnail({ width, height, day, forged }: {
  width: number; height: number; day: number; forged: boolean;
}) {
  const centre = useDerivedValue(() => ({ x: width / 2, y: height / 2 }));
  const half = useDerivedValue(() => 210 * Math.min(width / 240, height / 448) / 1.009);
  const rotation = useSharedValue(medalRotation(0, 0, 0));
  const light = useSharedValue({ x: 0, y: 0 });
  const zero = useSharedValue(0);
  const sweep = useSharedValue(1.15);
  const stamp = useDerivedValue<number>(() => forged ? 1 : 0);
  return <MedalCanvas width={width} height={height} centre={centre} half={half} rotation={rotation}
    light={light} glint={zero} heat={zero} charge={zero} stamp={stamp} morph={zero}
    sweep={sweep} engrave={zero} from={day} to={day} />;
}

/** One medal crosses the grid, the sheet and the ribbon without changing drawing surfaces. */
export function DayOneMedal({ autoPlay = false, renderer = 'auto' }: Props) {
  const window = useWindowDimensions();
  const [viewport, setViewport] = useState<{ width: number; height: number } | null>(null);
  const { width, height } = viewport ?? window;
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const [fontsLoaded, fontsFailed] = useFonts({
    [wide.black]: require('./assets/ArchivoExpanded-Black.ttf'),
    [wide.regular]: require('./assets/ArchivoExpanded-Regular.ttf'),
  });
  const sheetTop = insets.top + D.sheetGap;
  const sheetHeight = height - sheetTop;
  const k = Math.min(1, width / D.width, sheetHeight / D.sheetHeight);
  const layout = medalLayout(k);
  const gridK = Math.min(1, width / D.width, (height - insets.top - insets.bottom - D.grid.bar - 64) / 684);
  const backShift = insets.top + D.backGap - height * (1 - D.backScale) / 2;
  const stageY = D.medal.centre * k;
  const stageHalf = D.medal.height * k / 2;
  const gestureTop = (D.header.centre + 26) * k;
  const pivot = useMemo(() => ({ x: width / 2, y: -40 }), [width]);
  const [open, setOpen] = useState(false);
  const [landed, setLanded] = useState(false);
  const [canvasReady, setCanvasReady] = useState(false);
  const [rendererPrepared, setRendererPrepared] = useState(false);
  const gpuDriving = useSharedValue(false);
  const presented = useSharedValue(0);
  const [moment, setMoment] = useState(INITIAL);
  const [gpuActive, setGpuActive] = useState(false);
  const headerAlpha = useSharedValue(1);
  const [hintLater, setHintLater] = useState(false);
  const openRef = useRef(false);
  const pendingSlot = useRef<{ x: number; y: number; half: number } | null>(null);
  const rootRef = useRef<View>(null);
  const thumbRef = useRef<View>(null);
  const present = useSharedValue(0);
  const visible = useSharedValue(0);
  const drag = useSharedValue(0);
  const active = useSharedValue(false);
  const slotRect = useSharedValue({ x: width / 2, y: height / 2, half: 40 });
  const wake = useSharedValue(0);
  const wakeShare = useSharedValue(0);
  const restartOnClose = useRef(false);
  const timeline = useRef<ReturnType<typeof createMedalDemo> | null>(null);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const runRef = useRef<() => void>(() => {});
  const clearTimers = useCallback(() => {
    restartOnClose.current = false;
    timeline.current?.stop();
    timers.current.forEach(clearTimeout);
    timers.current = [];
  }, []);
  const plays = autoPlay && !reduced;
  const touched = useCallback(() => {
    if (!plays) return;
    clearTimers();
    timers.current.push(setTimeout(() => runRef.current(), T.resume));
  }, [clearTimers, plays]);
  const changed = useCallback((next: MedalMoment) => {
    setMoment(next);
    if (!next.forged) setHintLater(false);
  }, []);
  const demoEvent = useCallback((event: DemoEvent) => timeline.current?.signal(event), []);
  const motion = useMedalMotion({
    reduced, stage: { centre: { x: width / 2, y: stageY }, half: stageHalf }, pivot,
    engravePath: engraving.points, onTouch: touched, onChange: changed, onDemoEvent: demoEvent, active, gpuDriving, presented,
    touchOrigin: { x: 0, y: gestureTop },
  });
  // Only serialisable values enter the closures below; motion also contains a native gesture.
  const { rotation: pose, offset, fade, heat, pressed, clock, struck, worn, spot, spitting,
    enterUI, reset, flip, lap, wear, nextDay, demo } = motion;
  const sheetY = useDerivedValue(() => {
    'worklet';
    return reduced ? sheetTop : sheetTop + (1 - present.get()) * sheetHeight + drag.get();
  });
  const centre = useDerivedValue(() => {
    'worklet';
    const p = reduced ? 1 : present.get();
    const slot = slotRect.get();
    const retreat = 1 - (1 - D.backScale) * p;
    const sx = width / 2 + (slot.x - width / 2) * retreat;
    const sy = height / 2 + (slot.y - height / 2) * retreat + backShift * p;
    const displacement = offset.get();
    return {
      x: sx + (width / 2 - sx) * p + displacement.x * p,
      y: sy + (sheetTop + stageY - sy) * p - Math.sin(Math.PI * p) * 48 * k
        + displacement.y * p + drag.get() * p,
    };
  });
  const half = useDerivedValue(() => {
    'worklet';
    const p = reduced ? 1 : present.get();
    const small = slotRect.get().half * (1 - (1 - D.backScale) * p);
    const punch = reduced ? 1 : (1 - (1 - S.press) * pressed.get()) * (1 + S.strike.punch * heat.get());
    return (small + (stageHalf - small) * p) * (1 + (punch - 1) * p);
  });
  const rotation = useDerivedValue(() => {
    'worklet';
    const p = reduced ? 1 : present.get();
    const matrix = pose.get();
    if (p >= 1) return matrix;
    const yaw = Math.atan2(-matrix[2], matrix[8]);
    const pitch = Math.asin(Math.max(-1, Math.min(1, matrix[5])));
    const roll = Math.atan2(-matrix[3], matrix[4]);
    return medalRotation(Math.PI * 2 * p + yaw * p, pitch * p, roll * p);
  });
  const attachment = useDerivedValue(() => {
    'worklet';
    const at = centre.get();
    const matrix = rotation.get();
    return { x: at.x + half.get() * matrix[3], y: at.y - half.get() * matrix[4] - sheetY.get() };
  });
  const ribbonWorn = useDerivedValue(() => {
    'worklet';
    return worn.get() * present.get();
  });
  const sparkCentre = useDerivedValue(() => {
    'worklet';
    return { x: centre.get().x, y: centre.get().y - sheetY.get() };
  });
  const sparkSpot = useDerivedValue(() => {
    'worklet';
    const source = spot.get();
    const displacement = offset.get();
    const ratio = half.get() / stageHalf;
    return { x: sparkCentre.get().x + (source.x - width / 2 - displacement.x) * ratio,
      y: sparkCentre.get().y + (source.y - stageY - displacement.y) * ratio, on: source.on };
  });
  const since = useDerivedValue(() => {
    'worklet';
    return Math.max(0, clock.get() - struck.get());
  });

  useEffect(() => {
    headerAlpha.set(withTiming(moment.worn ? 0 : 1, { duration: 200, reduceMotion: ReduceMotion.Never }));
  }, [headerAlpha, moment.worn]);
  const headerStyle = useAnimatedStyle(() => {
    'worklet';
    return { opacity: headerAlpha.get() };
  });
  useEffect(() => {
    wake.set(withTiming(moment.forged ? 1 : 0, { duration: 200, reduceMotion: ReduceMotion.Never }));
    wakeShare.set(withDelay(moment.forged ? 60 : 0, withTiming(moment.forged ? 1 : 0, { duration: 200, reduceMotion: ReduceMotion.Never })));
  }, [moment.forged, wake, wakeShare]);
  useEffect(() => {
    if (!moment.forged || !open) return;
    const timer = setTimeout(() => setHintLater(true), T.hint);
    return () => clearTimeout(timer);
  }, [moment.forged, open]);

  const arrived = useCallback(() => { setLanded(true); demoEvent('arrived'); }, [demoEvent]);
  const arrivalUI = useCallback((finished?: boolean) => {
    'worklet';
    if (finished) { active.set(true); scheduleOnRN(arrived); }
  }, [active, arrived]);
  const beginOpening = useCallback((slot: { x: number; y: number; half: number }) => {
    'worklet';
    // One UI transaction: no RN work, measurements or renderer setup between
    // replacing the thumbnail and starting the 500 ms flight.
    slotRect.set(slot);
    active.set(false);
    enterUI();
    drag.set(0);
    present.set(0);
    present.set(withTiming(1, { duration: reduced ? 160 : T.present,
      easing: SHEET_EASE, reduceMotion: ReduceMotion.Never }, arrivalUI));
    visible.set(1);
  }, [active, arrivalUI, drag, enterUI, present, reduced, slotRect, visible]);
  useLayoutEffect(() => {
    // Commit the sheet's native views before transferring ownership to UI.
    if (!open || !pendingSlot.current) return;
    const slot = pendingSlot.current;
    pendingSlot.current = null;
    scheduleOnUI(beginOpening, slot);
  }, [beginOpening, open]);
  const closed = useCallback(() => {
    openRef.current = false;
    setOpen(false);
    setLanded(false);
    if (restartOnClose.current) { clearTimers(); timeline.current?.start(); }
    else demoEvent('closed');
  }, [clearTimers, demoEvent]);
  const show = useCallback(() => {
    if (openRef.current || !canvasReady || !(fontsLoaded || fontsFailed)) return;
    // Measure the actual image box relative to the same native root as the full-window canvas.
    // Its transparent guard is 14 px at either end of a 448 px image.
    thumbRef.current?.measureLayout(rootRef.current!, (x, y, w, h) => {
      if (openRef.current) return;
      const fit = Math.min(w / 240, h / 448);
      if (w <= 0 || h <= 0) return;
      pendingSlot.current = { x: x + w / 2, y: y + h / 2, half: 210 * fit / 1.009 };
      openRef.current = true;
      setOpen(true);
      setLanded(false);
      setHintLater(false);
    });
  }, [canvasReady, fontsFailed, fontsLoaded]);
  const hide = useCallback(() => {
    if (!openRef.current) return;
    setLanded(false);
    active.set(false);
    present.set(withTiming(0, { duration: reduced ? 140 : T.dismiss,
      easing: SHEET_EASE, reduceMotion: ReduceMotion.Never }, (finished) => {
      'worklet';
      if (finished) {
        visible.set(0);
        scheduleOnRN(closed);
      }
    }));
  }, [active, closed, present, reduced, visible]);

  useEffect(() => {
    const sequence = createMedalDemo({ reset, open: show, forge: demo.forge, turn: demo.drift,
      flick: demo.flick, wear, next: () => nextDay(1), close: hide });
    timeline.current = sequence;
    runRef.current = () => {
      clearTimers();
      if (openRef.current) {
        restartOnClose.current = true;
        hide();
        // If dismissal never completes, stop; do not reset a still-visible medal.
        timers.current.push(setTimeout(clearTimers, 15000));
      } else sequence.start();
    };
    return () => sequence.stop();
  }, [clearTimers, demo, hide, nextDay, reset, show, wear]);
  const ready = fontsLoaded || fontsFailed;
  useEffect(() => {
    if (plays && ready && canvasReady && rendererPrepared) runRef.current();
    return clearTimers;
  }, [canvasReady, clearTimers, plays, ready, rendererPrepared]);

  const grabChangeUI = useCallback((translationY: number) => {
    'worklet';
    if (!reduced) drag.set(translationY > 0 ? translationY : translationY * 0.12);
  }, [drag, reduced]);
  const grabEndUI = useCallback((translationY: number, velocityY: number) => {
    'worklet';
    if (translationY > sheetHeight * 0.3 || velocityY > 700) {
      scheduleOnRN(hide);
      drag.set(withTiming(0, { duration: reduced ? 0 : T.dismiss }));
    } else drag.set(withSpring(0, { duration: 300, dampingRatio: 1, velocity: velocityY }));
  }, [drag, hide, reduced, sheetHeight]);
  /* eslint-disable react-hooks/refs */
  const grab = useMemo(() => Gesture.Pan().activeOffsetY(8).failOffsetX([-24, 24])
    .onBegin(() => { 'worklet'; scheduleOnRN(touched); })
    .onChange(event => { 'worklet'; grabChangeUI(event.translationY); })
    .onEnd(event => { 'worklet'; grabEndUI(event.translationY, event.velocityY); }),
  [grabChangeUI, grabEndUI, touched]);
  /* eslint-enable react-hooks/refs */
  const backStyle = useAnimatedStyle(() => {
    'worklet';
    const p = reduced ? 0 : present.get();
    return { borderRadius: D.sheetRadius * p,
      transform: [{ translateY: backShift * p }, { scale: 1 - (1 - D.backScale) * p }] };
  });
  const thumbStyle = useAnimatedStyle(() => {
    'worklet';
    return { opacity: 1 - visible.get() };
  });
  const veilStyle = useAnimatedStyle(() => {
    'worklet';
    return { opacity: (reduced ? 0.9 : 0.5) * present.get() };
  });
  const sheetStyle = useAnimatedStyle(() => {
    'worklet';
    return ({ opacity: reduced ? present.get() : 1,
    transform: [{ translateY: sheetY.get() - sheetTop }] });
  });
  // Header dragging uses the same clipped Skia layers as a shared-element flight.
  // The full-window GPU leaf must not float above a sheet that is being pulled down.
  const gpuPresent = useDerivedValue(() => drag.get() === 0 ? present.get() : 0);
  const medalStyle = useAnimatedStyle(() => {
    'worklet';
    return { opacity: visible.get() * fade.get() * (reduced ? present.get() : 1) };
  });
  const titleStyle = useAnimatedStyle(() => {
    'worklet';
    return { opacity: 0.35 + 0.65 * wake.get() };
  });
  const wearStyle = useAnimatedStyle(() => {
    'worklet';
    return { opacity: 0.35 + 0.65 * wake.get() };
  });
  const shareStyle = useAnimatedStyle(() => {
    'worklet';
    return { opacity: 0.35 + 0.65 * wakeShare.get() };
  });
  // Reserve a sub-pixel tolerance so Yoga never wraps an exactly full row.
  const slotWidth = (D.width - 2 * D.grid.inset - 3 * D.grid.gap) * gridK / 4 - 0.01;
  const slot = { width: slotWidth, height: slotWidth * D.grid.slot.height / D.grid.slot.width };
  const hint = !moment.forged ? 'Hold to forge' : moment.worn ? 'Drag to swing' :
    hintLater ? (moment.day === 2 ? 'Swipe down for the previous milestone' : 'Swipe up for the next milestone') : 'Drag to spin. Flick it hard.';
  const activate = () => { touched(); if (moment.forged) flip(); else demo.forge(); };
  return <View ref={rootRef} collapsable={false} style={styles.root} onTouchStart={touched}
    onLayout={({ nativeEvent: { layout: bounds } }) => {
      if (bounds.width > 0 && bounds.height > 0) setViewport(previous =>
        previous?.width === bounds.width && previous.height === bounds.height ? previous :
          { width: bounds.width, height: bounds.height });
    }}>
    <Animated.View style={[styles.back, backStyle]} accessibilityElementsHidden={open}
      importantForAccessibility={open ? 'no-hide-descendants' : 'auto'}>
      <View style={[styles.bar, { marginTop: insets.top, height: D.grid.bar }]}>
        <View style={styles.close}><Glyph paths={ICON.close} size={14} colour={tone.page} /></View>
        <Text style={[styles.barTitle, { fontSize: D.grid.title * gridK }]}>Collectibles</Text>
      </View>
      {SHELF.map((shelf) => <View key={shelf.title}
        style={{ paddingHorizontal: (width - D.width * gridK) / 2 + D.grid.inset * gridK, marginTop: 18 * gridK }}>
        <Text style={[styles.shelfTitle, { fontSize: D.grid.section * gridK, lineHeight: 20 * gridK }]}>{shelf.title}</Text>
        <View style={[styles.shelf, { gap: D.grid.gap * gridK, marginTop: 14 * gridK }]}>
          {shelf.slots.map((name) => name === 'earned' ?
            <Squeeze key={name} label={`Day ${DAYS[moment.day]} medal, ${moment.forged ? 'forged' : 'ready to forge'}. Opens the achievement.`}
              onPress={() => { touched(); show(); }} disabled={!canvasReady || !ready} style={[styles.slot, styles.earned, slot]}>
              <View ref={thumbRef} collapsable={false} style={{ width: slot.width * 0.78, height: slot.height * 0.9 }}>
                <Animated.View style={[StyleSheet.absoluteFill, thumbStyle]}>
                  <MedalThumbnail width={slot.width * 0.78} height={slot.height * 0.9}
                    day={moment.day} forged={moment.forged} />
                </Animated.View>
              </View>
            </Squeeze> :
            <View key={name} style={[styles.slot, slot]} accessible accessibilityLabel={`${name}, not earned yet`}>
              <Glyph paths={MEDAL_ICONS[name]} size={22} colour={tone.locked} />
            </View>)}
        </View>
      </View>)}
      <Text style={[styles.hint, { bottom: insets.bottom + 18 }]}>{moment.forged ? 'Tap your medal.' : 'Tap the medal to begin.'}</Text>
    </Animated.View>
    <Animated.View style={[styles.veil, veilStyle]} pointerEvents="none" />
    <Animated.View style={[styles.sheet, { top: sheetTop, height: sheetHeight + 80 }, sheetStyle]} pointerEvents="none">
      {moment.forged && !gpuActive && <MedalConfetti width={width} height={sheetHeight} time={since} layer="far" scale={k} still={reduced} />}
    </Animated.View>
    <Animated.View style={[styles.foreground, { zIndex: 3, top: sheetTop, height: sheetHeight, overflow: 'hidden' }, sheetStyle]}
      pointerEvents="none">
      <MedalRibbon width={width} height={sheetHeight} pivot={pivot} attach={attachment}
        worn={ribbonWorn} clock={clock} scale={k} reduced={reduced} />
    </Animated.View>
    <Animated.View collapsable={false} style={[StyleSheet.absoluteFill, { zIndex: 4 }, medalStyle]} pointerEvents="none" accessibilityElementsHidden>
      <MedalRenderer onPrepared={setRendererPrepared} gpuDriving={gpuDriving} presented={presented} visible={visible} present={gpuPresent} settled={motion.settled} renderer={renderer} reduced={reduced} onRenderer={setGpuActive}
        stageHalf={stageHalf} stageCentreY={sheetTop + stageY}
        clock={clock} struck={struck} spin={motion.spin} sheetTop={sheetTop} stageBottom={sheetTop + layout.titleTop - 16}
        width={width} height={height} centre={centre} half={half} rotation={rotation}
        light={motion.light} glint={motion.glint} heat={heat} charge={motion.charge} stamp={motion.stamp}
        morph={motion.morph} sweep={motion.sweep} engrave={motion.engrave} from={motion.from} to={motion.to}
        onReady={setCanvasReady} />
    </Animated.View>
    <Animated.View style={[styles.foreground, { top: sheetTop, height: sheetHeight + 80 }, sheetStyle]}
      pointerEvents={open ? 'box-none' : 'none'} accessibilityViewIsModal={open}
      accessibilityElementsHidden={!open} importantForAccessibility={open ? 'auto' : 'no-hide-descendants'}>
      <GestureDetector gesture={motion.gesture}>
        <View accessible accessibilityRole="adjustable" pointerEvents={landed ? 'auto' : 'none'}
          accessibilityLabel={`Day ${DAYS[moment.day]} medal, ${moment.forged ?
            `${moment.worn ? 'worn' : 'forged'}, ${moment.engraved ? 'back engraved' : 'back not engraved'}` :
            'not forged yet. Double tap and hold to forge.'}`}
          accessibilityHint={moment.forged ? 'Double tap to flip. Swipe up or down to change milestone.' :
            'Double tap to forge, or hold a finger on the medal.'}
          accessibilityValue={{ min: 1, max: 3, now: moment.day + 1, text: `Day ${DAYS[moment.day]}` }}
          accessibilityActions={[{ name: 'activate' }, { name: 'increment' }, { name: 'decrement' }]}
          onAccessibilityAction={(event) => {
            touched();
            if (event.nativeEvent.actionName === 'activate') activate();
            else nextDay(event.nativeEvent.actionName === 'increment' ? 1 : -1);
          }}
          style={{ position: 'absolute', top: gestureTop, width, height: 430 * k }} />
      </GestureDetector>
      <GestureDetector gesture={grab}>
        <View style={[styles.header, { height: gestureTop }]}>
          <Animated.View style={[styles.headerFade, headerStyle, { top: (D.header.centre - 11) * k }]}>
            <Text style={[styles.headerTitle, { fontSize: D.header.size * k }]}>Achievement</Text>
          </Animated.View>
        </View>
      </GestureDetector>
      <View style={{ position: 'absolute', right: D.inset * k, top: (D.header.centre - 12) * k }}>
        <Squeeze label="Done" onPress={() => { touched(); hide(); }} style={styles.done}>
          <Text style={[styles.doneLabel, { fontSize: D.header.size * k }]}>Done</Text>
        </Squeeze>
      </View>
      <View style={{ position: 'absolute', right: D.inset * k, top: D.chip.top * k }}>
        <Squeeze label="Turn the medal over" onPress={() => { touched(); flip(); }} disabled={!moment.forged || !landed}
          style={[styles.chip, { opacity: moment.forged ? 1 : 0.35, width: D.chip.size * k,
            height: D.chip.size * k, borderRadius: D.chip.radius * k }]}>
          <Glyph paths={ICON.capsule} size={D.chip.icon * k} colour={tone.ink} />
        </Squeeze>
      </View>
      <Animated.View style={[{ position: 'absolute', top: layout.titleTop, width,
        height: D.title.size * 1.7 * k }, titleStyle]} accessible accessibilityRole="header"
        accessibilityLabel={`DAY ${DAYS[moment.day]}`}>
        <MedalTitle width={width} scale={k} day={DAYS[moment.day]} />
      </Animated.View>
      <Animated.Text numberOfLines={2}
        style={[styles.body, { top: layout.bodyTop, height: layout.bodyHeight, fontSize: D.body.size * k,
          lineHeight: layout.bodyHeight / 2, paddingHorizontal: 24 * k }]}>
        {moment.forged ? COPY[moment.day] : 'Hold the medal to forge it.'}
      </Animated.Text>
      <View style={{ position: 'absolute', top: layout.dotsTop, width, flexDirection: 'row', justifyContent: 'center', gap: 5 }}>
        {DAYS.map((day, i) => <View key={day} style={{ width: 4, height: 4, borderRadius: 2,
          backgroundColor: i === moment.day ? tone.gold : tone.locked }} />)}
      </View>
      <Animated.Text key={hint} entering={FadeIn.duration(160).reduceMotion(ReduceMotion.Never)} exiting={FadeOut.duration(160).reduceMotion(ReduceMotion.Never)}
        style={[styles.hint, { top: layout.hintTop, height: layout.hintHeight, fontSize: 13 * k, lineHeight: layout.hintHeight }]}>{hint}</Animated.Text>
      <Animated.View style={[{ position: 'absolute', left: D.inset * k, right: D.inset * k, top: layout.wearTop }, wearStyle]}>
        <Squeeze label={moment.worn ? 'Take it off' : 'Wear your medal'} onPress={() => { touched(); wear(); }}
          disabled={!moment.forged || !landed} style={[styles.button, styles.quiet, {
            height: D.button.height * k, borderRadius: D.button.radius * k, gap: D.button.gap * k }]}>
          <Glyph paths={ICON.medal} size={D.button.icon * k} colour={tone.ink} />
          <View style={{ width: 150 * k, height: 24 * k, justifyContent: 'center' }}>
            <Animated.Text key={String(moment.worn)} entering={FadeIn.duration(160).reduceMotion(ReduceMotion.Never)} exiting={FadeOut.duration(160).reduceMotion(ReduceMotion.Never)}
              style={[styles.buttonLabel, { position: 'absolute', fontSize: D.button.label * k }]}>
              {moment.worn ? 'Take It Off' : 'Wear Your Medal'}
            </Animated.Text>
          </View>
        </Squeeze>
      </Animated.View>
      <Animated.View style={[{ position: 'absolute', left: D.inset * k, right: D.inset * k, top: layout.shareTop }, shareStyle]}>
        <Squeeze label="Share" onPress={() => { touched(); lap(); }} disabled={!moment.forged || !landed}
          style={[styles.button, styles.loud, { height: D.button.height * k, borderRadius: D.button.radius * k }]}>
          <Text style={[styles.buttonLabel, { fontSize: D.button.label * k, color: tone.page }]}>Share</Text>
        </Squeeze>
      </Animated.View>
      {moment.forged && !gpuActive && <MedalConfetti width={width} height={sheetHeight} time={since} layer="near" scale={k} still={reduced} />}
      {!reduced && open && <MedalSparks burst={!gpuActive} width={width} height={sheetHeight} centre={sparkCentre} scale={k}
        clock={clock} struck={struck} spitting={spitting} spot={sparkSpot} />}
    </Animated.View>
  </View>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: tone.page, overflow: 'hidden' },
  back: { flex: 1, zIndex: 0, backgroundColor: tone.page, overflow: 'hidden' },
  bar: { alignItems: 'center', justifyContent: 'center' },
  close: {
    position: 'absolute',
    left: 16,
    width: 24,
    height: 24,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: tone.ink,
  },
  barTitle: { color: tone.ink, fontFamily: wide.regular },
  shelfTitle: { color: tone.ink, fontFamily: wide.regular },
  shelf: { flexDirection: 'row', flexWrap: 'wrap' },
  slot: {
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 999,
    borderWidth: StyleSheet.hairlineWidth * 2,
    borderColor: tone.hairline,
    backgroundColor: tone.slot,
  },
  earned: { borderColor: '#3A3526' },
  hint: { position: 'absolute', left: 20, right: 20, color: tone.hint, fontSize: 13, lineHeight: 18, textAlign: 'center' },
  veil: { ...StyleSheet.absoluteFill, zIndex: 1, backgroundColor: '#000000' },
  foreground: { position: 'absolute', left: 0, right: 0, zIndex: 5, overflow: 'hidden', borderTopLeftRadius: D.sheetRadius, borderTopRightRadius: D.sheetRadius },
  sheet: {
    zIndex: 2,
    position: 'absolute',
    left: 0,
    right: 0,
    overflow: 'hidden',
    backgroundColor: tone.sheet,
    borderTopLeftRadius: D.sheetRadius,
    borderTopRightRadius: D.sheetRadius,
  },
  header: { position: 'absolute', left: 0, right: 0, top: 0, alignItems: 'center' },
  headerFade: { position: 'absolute', left: 0, right: 0 },
  headerTitle: { width: '100%', textAlign: 'center', color: tone.dim, fontFamily: wide.regular },
  done: { paddingVertical: 2, paddingLeft: 12 },
  doneLabel: { color: tone.ink, fontWeight: '600' },
  chip: { alignItems: 'center', justifyContent: 'center', backgroundColor: tone.chip },
  body: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: tone.ink },
  button: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  quiet: { backgroundColor: tone.quiet },
  loud: { backgroundColor: tone.ink },
  buttonLabel: { color: tone.ink, fontWeight: '600' },
});
