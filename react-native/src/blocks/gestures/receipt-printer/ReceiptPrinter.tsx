/**
 * Run receipts: long-press a run, pick "Long receipt", and a printer slot feeds the receipt out in jerks. Drag the
 * paper itself (friction, momentum, rubber band past its ends); at its end the actions come up; "Tear off" draws it
 * back into the slot.
 */
import { useFonts } from 'expo-font';
import { SymbolView, type SFSymbol } from 'expo-symbols';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, {
  Easing, ReduceMotion, cancelAnimation, cubicBezier, interpolateColor, useAnimatedStyle, useFrameCallback, useReducedMotion, useSharedValue,
  withDecay, withDelay, withSequence, withSpring, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { AUTOPLAY, DEBUG_FREEZE_MS, FEED, ISLAND_LEAD_MS, ISLAND_SPRING, LOOP_MS, POLISH, PRESS, QA_SLOWMO, RETRACT, SCREEN, SCROLL, UPGRADE } from './motion';
import { ReceiptPaper } from './ReceiptPaper';
import { RunGrid } from './RunGrid';
import { BAR, CAP_TOP, COLOR, COPY, FONT, GRID, ISLAND, RECEIPT, SLOT, SPACE, TYPE } from './theme';

export type ReceiptPrinterProps = { autoPlay?: boolean };

const never = ReduceMotion.Never;
const EASE_OUT = Easing.bezier(POLISH.easeOut[0], POLISH.easeOut[1], POLISH.easeOut[2], POLISH.easeOut[3]);
const EASE_IN_OUT = Easing.bezier(POLISH.easeInOut[0], POLISH.easeInOut[1], POLISH.easeInOut[2], POLISH.easeInOut[3]);
// The source was measured on a 62 pt status area; everything shifts with the real one.
const SOURCE_TOP = 62;
const GRID_ = 0;
const MENU = 1;
const PRINTING = 2;
const READING = 3;
const RETRACTING = 4;

function buzz(kind: 'tick' | 'open' | 'tear') {
  if (kind === 'tick') void Haptics.selectionAsync().catch(() => {});
  else void Haptics.impactAsync(kind === 'open' ? Haptics.ImpactFeedbackStyle.Medium : Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** Stepped feed: each step is a quick push (ease-out) then a rest, like a thermal printer's stepper motor. */
function stepped(p: number): number {
  'worklet';
  const n = FEED.steps;
  const x = Math.min(1, Math.max(0, p)) * n;
  const k = Math.min(n - 1, Math.floor(x));
  const u = Math.min(1, (x - k) / FEED.push);
  return (k + 1 - Math.pow(1 - u, 3)) / n;
}

const PRESS_CURVE = cubicBezier(POLISH.easeOut[0], POLISH.easeOut[1], POLISH.easeOut[2], POLISH.easeOut[3]);

function BarButton({ icon, label, dark, onPress }: { icon: SFSymbol; label: string; dark?: boolean; onPress?: () => void }) {
  const ink = dark ? COLOR.tagInk : COLOR.buttonInk;
  // Feedback on press-in (scale 0.97, 100 ms), a slightly slower release; a transition, so a quick tap retargets.
  return <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label} style={styles.barHit} pressRetentionOffset={16}>
    {({ pressed }) => <Animated.View style={[styles.barButton, dark ? styles.barDark : styles.barLight, {
      transform: [{ scale: pressed ? POLISH.pressScale : 1 }],
      transitionProperty: 'transform', transitionDuration: pressed ? POLISH.pressInMs : POLISH.pressOutMs, transitionTimingFunction: PRESS_CURVE,
    }]}>
      <SymbolView name={icon} size={BAR.icon} tintColor={ink} weight="bold" style={[styles.barIcon, { top: BAR.iconCap }]} />
      <Text style={[styles.barLabel, { color: ink, top: BAR.labelCap - TYPE.button * CAP_TOP }]}>{label}</Text>
    </Animated.View>}
  </Pressable>;
}

export function ReceiptPrinter({ autoPlay = false }: ReceiptPrinterProps) {
  const [fontsLoaded, fontsFailed] = useFonts({
    [FONT.regular]: require('./assets/JetBrainsMono-Regular.ttf'),
    [FONT.medium]: require('./assets/JetBrainsMono-Medium.ttf'),
    [FONT.bold]: require('./assets/JetBrainsMono-Bold.ttf'),
    [FONT.heavy]: require('./assets/JetBrainsMono-ExtraBold.ttf'),
  });
  const insets = useSafeAreaInsets();
  const { height, width } = useWindowDimensions();
  const reduceMotion = useReducedMotion();
  const shift = insets.top - SOURCE_TOP;
  // The printer is the Dynamic Island (an island device has a top inset of 51 pt or more); the paper leaves its lower edge.
  const islandTop = insets.top >= 51 ? ISLAND.top : insets.top + ISLAND.noIslandGap;
  const paperTop = islandTop + ISLAND.openH - ISLAND.tuck;
  // How far the paper scrolls: its foot rests a little above the action bar (frame 030).
  const maxScroll = Math.max(0, paperTop + RECEIPT.length - (BAR.top + shift - 48));
  // The feed fills the screen; what is below is already printed when the feed stops.
  const feedVisible = height - paperTop;

  const [stage, setStage] = useState(GRID_);
  const phase = useSharedValue(GRID_);
  const pressed = useSharedValue(-1);
  const lift = useSharedValue(0);
  const menuIn = useSharedValue(0);
  const gridIn = useSharedValue(1);
  const slotIn = useSharedValue(0);
  const feed = useSharedValue(0);
  const fed = useSharedValue(0);
  const scroll = useSharedValue(0);
  const barIn = useSharedValue(0);
  const drag = useSharedValue({ start: 0, scripted: false });
  const script = useSharedValue({ time: 0, paused: false, realDown: false, quiet: 0, seg: -1 });
  const clock = useSharedValue(0);
  const morph = useSharedValue(0);
  const morphFrom = useSharedValue(0);
  const develop = useSharedValue(0);
  const cut = useSharedValue(0);
  const held = useSharedValue(0);
  const step = useSharedValue(-1);
  const armed = useSharedValue(0);
  const paperO = useSharedValue(1); // only Reduce Motion fades the paper (its retract is a cross-fade)

  const report = useCallback((s: number) => setStage(s), []);
  const haptic = useCallback((kind: 'tick' | 'open' | 'tear', scripted: boolean) => {
    'worklet';
    if (!scripted) scheduleOnRN(buzz, kind);
  }, []);

  const toStage = useCallback((s: number) => {
    'worklet';
    phase.set(s);
    scheduleOnRN(report, s);
  }, [phase, report]);

  const openMenu = useCallback((i: number, scripted: boolean) => {
    'worklet';
    if (phase.get() !== GRID_) return;
    pressed.set(i);
    lift.set(withSpring(1, { ...PRESS.menuIn, reduceMotion: never }));
    menuIn.set(withSpring(1, { ...PRESS.menuIn, reduceMotion: never }));
    haptic('tick', scripted);
    toStage(MENU);
  }, [haptic, lift, menuIn, phase, pressed, toStage]);

  const closeMenu = useCallback(() => {
    'worklet';
    menuIn.set(withTiming(0, { duration: PRESS.menuOutMs, easing: EASE_OUT, reduceMotion: never }));
    lift.set(withTiming(0, { duration: PRESS.menuOutMs, easing: EASE_OUT, reduceMotion: never }));
    if (phase.get() === MENU) toStage(GRID_);
  }, [lift, menuIn, phase, toStage]);

  /** Grid out, slot in, then the stepped feed. */
  const print = useCallback((scripted: boolean, tile = -1) => {
    'worklet';
    const ph = phase.get();
    if (ph !== GRID_ && ph !== MENU) return;
    // A plain tap names its tile; from the menu, the long-pressed one is already set.
    if (tile >= 0) pressed.set(tile);
    cancelAnimation(paperO);
    paperO.set(1);
    toStage(PRINTING);
    haptic('open', scripted);
    // The picked card becomes the slot.
    morphFrom.set(Math.max(0, pressed.get()));
    morph.set(0);
    if (!reduceMotion) morph.set(withTiming(1, { duration: UPGRADE.morphMs * QA_SLOWMO, easing: EASE_OUT, reduceMotion: never }));
    develop.set(withTiming(1, { duration: UPGRADE.developFadeMs, easing: EASE_OUT, reduceMotion: never }));
    step.set(-1);
    menuIn.set(withTiming(0, { duration: PRESS.menuOutMs, easing: EASE_OUT, reduceMotion: never }));
    lift.set(withTiming(0, { duration: PRESS.menuOutMs, easing: EASE_OUT, reduceMotion: never }));
    gridIn.set(withTiming(0, { duration: SCREEN.gridOutMs, easing: EASE_OUT, reduceMotion: never }));
    slotIn.set(reduceMotion ? 1 : withSpring(1, { ...ISLAND_SPRING, reduceMotion: never }));
    scroll.set(0);
    barIn.set(0);
    fed.set(0);
    feed.set(0);
    if (reduceMotion) {
      feed.set(withTiming(1, { duration: SCROLL.barMs, reduceMotion: never }, () => { fed.set(RECEIPT.length + RECEIPT.edge.depth); develop.set(0); toStage(READING); }));
      return;
    }
    // The paper waits for the island to open, so it never shows wider than the island's mouth.
    feed.set(withDelay(ISLAND_LEAD_MS * QA_SLOWMO, withTiming(1, { duration: FEED.ms * QA_SLOWMO, easing: Easing.linear, reduceMotion: never }, (done) => {
      if (!done) return;
      // The whole roll, torn foot included.
      fed.set(RECEIPT.length + RECEIPT.edge.depth);
      toStage(READING);
      develop.set(withTiming(0, { duration: UPGRADE.developFadeMs, reduceMotion: never }));
      // The cutter: a tug down and a stiff spring back.
      cut.set(withSequence(withTiming(1, { duration: UPGRADE.cutDownMs, easing: Easing.out(Easing.quad), reduceMotion: never }),
        withSpring(0, { ...UPGRADE.cutSpring, reduceMotion: never })));
      haptic('open', scripted);
    })));
  }, [barIn, cut, develop, fed, feed, gridIn, haptic, lift, menuIn, morph, morphFrom, paperO, phase, pressed, reduceMotion, scroll, slotIn, step, toStage]);

  /**
   * The paper is drawn back up into the slot. The list is revealed under it as it rises and the island closes back to
   * the hardware island as the paper's foot slips in, so the hand-over has no empty frame.
   */
  const retract = useCallback((scripted: boolean) => {
    'worklet';
    if (phase.get() !== READING) return;
    toStage(RETRACTING);
    haptic('tear', scripted);
    cancelAnimation(scroll);
    barIn.set(withTiming(0, { duration: SCROLL.barMs, reduceMotion: never }));
    const back = (ok?: boolean) => {
      'worklet';
      if (ok) toStage(GRID_);
    };
    const gone = () => {
      'worklet';
      fed.set(0);
      feed.set(0);
      scroll.set(0);
      paperO.set(1);
    };
    if (reduceMotion) {
      // Nothing travels: the paper fades out over the list as it fades in, then the island snaps shut.
      const fade = { duration: RETRACT.reducedMs, easing: EASE_OUT, reduceMotion: never };
      gridIn.set(withTiming(1, fade, back));
      paperO.set(withTiming(0, fade, (ok) => {
        if (!ok) return;
        gone();
        slotIn.set(0);
      }));
      return;
    }
    // The list fades in under the rising paper, starting a quarter into the pull (the paper still covers the screen).
    gridIn.set(withDelay((UPGRADE.tugMs + RETRACT.gridLeadMs) * QA_SLOWMO,
      withTiming(1, { duration: RETRACT.gridInMs * QA_SLOWMO, easing: EASE_OUT, reduceMotion: never }, back)));
    const s0 = scroll.get();
    scroll.set(withSequence(
      withTiming(s0 - UPGRADE.tug, { duration: UPGRADE.tugMs * QA_SLOWMO, easing: Easing.out(Easing.quad), reduceMotion: never }),
      // Up until the torn foot has just passed under the island: nothing keeps moving out of sight.
      withTiming(RECEIPT.length + RECEIPT.edge.depth, { duration: RETRACT.ms * QA_SLOWMO, easing: EASE_IN_OUT, reduceMotion: never }, (ok) => {
        if (!ok) return;
        gone();
        // The printer closes back into the Dynamic Island while the list finishes fading in.
        slotIn.set(withSpring(0, { ...ISLAND_SPRING, reduceMotion: never }));
      }),
    ));
  }, [barIn, fed, feed, gridIn, haptic, paperO, phase, reduceMotion, scroll, slotIn, toStage]);

  const rubber = (x: number) => {
    'worklet';
    if (x < 0) return -((-x) * SCROLL.rubber) / (1 + (-x) / 400);
    if (x > maxScroll) return maxScroll + ((x - maxScroll) * SCROLL.rubber) / (1 + (x - maxScroll) / 400);
    return x;
  };

  const dragBegin = useCallback((scripted: boolean) => {
    'worklet';
    // Grabbing the paper mid-print finishes the feed and hands the paper to the finger.
    if (phase.get() === PRINTING) {
      cancelAnimation(feed);
      fed.set(RECEIPT.length + RECEIPT.edge.depth);
      develop.set(withTiming(0, { duration: UPGRADE.developFadeMs, reduceMotion: never }));
      toStage(READING);
    }
    if (phase.get() !== READING) return;
    cancelAnimation(scroll);
    drag.set({ start: scroll.get(), scripted });
    held.set(withTiming(1, { duration: UPGRADE.liftMs, reduceMotion: never }));
  }, [develop, drag, fed, feed, held, phase, scroll, toStage]);
  const dragMove = useCallback((dy: number) => {
    'worklet';
    if (phase.get() !== READING) return;
    const s = rubber(drag.get().start - dy);
    scroll.set(s);
    // Crossing the dismiss point arms the release: one tick (finger only) and the light goes full bright.
    const on = s < -SCROLL.dismissBy ? 1 : 0;
    if (on !== armed.get()) {
      armed.set(on);
      if (on) haptic('tick', drag.get().scripted);
    }
  }, [armed, drag, haptic, phase, scroll]); // eslint-disable-line react-hooks/exhaustive-deps
  const dragEnd = useCallback((vy: number) => {
    'worklet';
    held.set(withTiming(0, { duration: UPGRADE.liftMs, reduceMotion: never }));
    armed.set(0);
    if (phase.get() !== READING) return;
    const s = scroll.get();
    // Pulled down past the top far enough (or flung down there): back into the slot.
    if (s < -SCROLL.dismissBy || (s < -30 && vy > SCROLL.dismissVelocity)) {
      retract(drag.get().scripted);
      return;
    }
    scroll.set(withDecay({ velocity: -vy, deceleration: SCROLL.deceleration, clamp: [0, maxScroll], rubberBandEffect: true, rubberBandFactor: SCROLL.rubber, reduceMotion: never }));
  }, [armed, drag, held, maxScroll, phase, retract, scroll]);

  // The action bar follows the scroll position.
  useFrameCallback((frame) => {
    'worklet';
    const dt = Math.min(frame.timeSincePreviousFrame ?? 0, 250);
    clock.set(clock.get() + dt);
    if (phase.get() === PRINTING) {
      fed.set(stepped(feed.get()) * feedVisible);
      // One tick per stepper push, for a finger-started print only.
      const k = Math.floor(feed.get() * FEED.steps);
      if (k !== step.get()) { step.set(k); haptic('tick', script.get().time > 0 && !script.get().paused); }
    }
    const want = phase.get() === READING && scroll.get() > maxScroll - SCROLL.barWithin ? 1 : 0;
    if (want === 1 && barIn.get() < 0.999 && phase.get() === READING) barIn.set(Math.min(1, barIn.get() + dt / SCROLL.barMs));
    if (want === 0 && barIn.get() > 0.001 && phase.get() === READING) barIn.set(Math.max(0, barIn.get() - dt / SCROLL.barMs));
    // Reduce Motion: no demo; a finger still prints and tears.
    if (!autoPlay || reduceMotion) return;
    const sc = script.get();
    if (sc.paused) {
      const quiet = sc.realDown ? 0 : sc.quiet + dt;
      if (quiet >= AUTOPLAY.resumeDelayMs && phase.get() === GRID_) script.set({ time: 0, paused: false, realDown: false, quiet: 0, seg: -1 });
      else script.set({ ...sc, quiet });
      return;
    }
    const t = sc.time;
    const A = AUTOPLAY;
    if (t >= A.pressAt && sc.time - dt < A.pressAt) openMenu(0, true);
    if (t >= A.pickAt && sc.time - dt < A.pickAt) print(true);
    let seg = -1;
    for (let i = 0; i < A.scrolls.length; i++) if (t >= A.scrolls[i].start && t < A.scrolls[i].end) seg = i;
    if (seg !== sc.seg) {
      if (sc.seg >= 0) {
        const p = A.scrolls[sc.seg];
        dragMove(-p.by);
        dragEnd((-p.by / ((p.end - p.start) / 1000)) * 0.4);
      }
      if (seg >= 0) dragBegin(true);
    }
    if (seg >= 0) {
      const p = A.scrolls[seg];
      const u = (t - p.start) / (p.end - p.start);
      dragMove(-p.by * u * u * (3 - 2 * u));
    }
    if (t >= A.tearAt && sc.time - dt < A.tearAt) retract(true);
    let next = DEBUG_FREEZE_MS >= 0 && t >= DEBUG_FREEZE_MS ? t : t + dt / QA_SLOWMO;
    if (next >= LOOP_MS) next -= LOOP_MS;
    script.set({ ...sc, time: next, seg });
  });

  const touch = useCallback((down: boolean) => {
    'worklet';
    const sc = script.get();
    script.set({ ...sc, paused: true, realDown: down, quiet: 0, seg: -1 });
  }, [script]);

  const pan = useMemo(() => Gesture.Pan()
    .onTouchesDown(() => { touch(true); })
    .onBegin(() => { dragBegin(false); })
    .onUpdate((e) => { dragMove(e.translationY); })
    .onEnd((e) => { dragEnd(e.velocityY); })
    .onFinalize(() => { touch(false); }), [dragBegin, dragEnd, dragMove, touch]);

  const gridStyle = useAnimatedStyle(() => ({ opacity: gridIn.get() }));
  const slotStyle = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, slotIn.get())) }));
  // The island's shadow on the paper exists only while there is paper under it.
  const shadeStyle = useAnimatedStyle(() => ({ opacity: fed.get() > 1 ? Math.min(1, Math.max(0, slotIn.get())) : 0 }));
  // The island widens into the printer's mouth and closes back to exactly the island.
  const islandStyle = useAnimatedStyle(() => {
    // Never narrower than the island it covers, even on the spring's last bounce.
    const s = Math.max(0, slotIn.get());
    const w = ISLAND.w + (ISLAND.openW - ISLAND.w) * s;
    const h = ISLAND.h + (ISLAND.openH - ISLAND.h) * s;
    return { width: w, height: h, borderRadius: h / 2, transform: [{ translateX: width / 2 - w / 2 }] };
  });
  // The light rides in the island's right end; shown once the island is mostly open, never on the closed island.
  const ledPlace = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.max(0, (slotIn.get() - 0.6) / 0.4)) }));
  // The menu opens over the card that was pressed (kept on screen) and grows from that card's corner.
  const menuStyle = useAnimatedStyle(() => {
    const i = Math.max(0, pressed.get());
    const cx = GRID.gutter + (i % 3) * (GRID.cardW + GRID.gap);
    const x = Math.min(cx, width - GRID.gutter - GRID.menu.w);
    // Below the pressed tile, clear of it; above it when the tile is too low for the menu to fit under it.
    const tileTop = Math.floor(i / 3) * GRID.rowPitch;
    const below = tileTop + GRID.cardH + SPACE.sm;
    const fits = GRID.menu.y + shift + below + GRID.menu.h < height - insets.bottom - SPACE.lg;
    const dy = fits ? below : tileTop - GRID.menu.h - SPACE.sm;
    const k = menuIn.get();
    return {
      // Gone means gone: under 2 % the menu is not drawn at all.
      opacity: k < 0.02 ? 0 : Math.min(1, k),
      transformOrigin: [cx - x, fits ? 0 : GRID.menu.h, 0],
      transform: [{ translateX: x - GRID.menu.x }, { translateY: dy }, { scale: reduceMotion ? 1 : 0.95 + 0.05 * k }],
    };
  });
  // The reveal: a clip whose foot is the paper's fed length, the paper counter-moved so it stays put.
  const clipStyle = useAnimatedStyle(() => ({ transform: [{ translateY: fed.get() - RECEIPT.length - RECEIPT.edge.depth }] }));
  const paperStyle = useAnimatedStyle(() => {
    // A thin sheet swings a little after each push of the feed, then settles.
    let swing = 0;
    if (phase.get() === PRINTING && !reduceMotion) {
      const x = feed.get() * FEED.steps;
      const since = (x - Math.floor(x)) * (FEED.ms / FEED.steps) / 1000;
      swing = FEED.swing * Math.exp(-FEED.swingDamping * since) * Math.sin(FEED.swingFreq * 2 * Math.PI * since);
    }
    // While read, the slip hangs and sways a little (ambient); the cutter tugs it at the end of the feed.
    const sway = phase.get() === READING && !reduceMotion ? UPGRADE.swayDeg * Math.sin((clock.get() / UPGRADE.swayMs) * 2 * Math.PI) : 0;
    return { transform: [{ translateY: RECEIPT.length + RECEIPT.edge.depth - fed.get() - scroll.get() + cut.get() * UPGRADE.cutDrop }, { rotate: `${swing + sway}deg` }] };
  });
  // The paper's cast shadow moves with it (transform only); the outer clip hides it above the slot.
  const shadowStyle = useAnimatedStyle(() => ({
    // Hidden while nothing is fed: its shadow would otherwise leak under the slot's lip.
    opacity: fed.get() > 1 ? 1 : 0,
    shadowOpacity: UPGRADE.liftShadow[0] + (UPGRADE.liftShadow[1] - UPGRADE.liftShadow[0]) * held.get(),
    transform: [{ translateY: Math.min(fed.get(), RECEIPT.length) - RECEIPT.length - scroll.get() }],
  }));
  // The container transform: the picked card's tile flies to the slot, flattens and goes black, then gives way to it.
  const morphStyle = useAnimatedStyle(() => {
    const i = morphFrom.get();
    const t = morph.get();
    const cx0 = GRID.gutter + (i % 3) * (GRID.cardW + GRID.gap) + GRID.cardW / 2;
    const cy0 = shift + GRID.rowTop + Math.floor(i / 3) * GRID.rowPitch + GRID.cardH / 2;
    const cx1 = width / 2;
    const cy1 = islandTop + ISLAND.openH / 2;
    const sx = 1 + (ISLAND.openW / GRID.cardW - 1) * t;
    const sy = 1 + (ISLAND.openH / GRID.cardH - 1) * t;
    return {
      // Solid while it travels, gone into the slot over the last 30 %; it darkens only as it reaches the island.
      opacity: t > 0 && t < 1 ? Math.min(1, (1 - t) / 0.3) : 0,
      backgroundColor: interpolateColor(t, [0.55, 0.95], [COLOR.cardPressed, ISLAND.color]),
      borderRadius: GRID.radius + (ISLAND.openH / 2 - GRID.radius) * t,
      transform: [{ translateX: cx0 + (cx1 - cx0) * t - GRID.cardW / 2 }, { translateY: cy0 + (cy1 - cy0) * t - GRID.cardH / 2 }, { scaleX: sx }, { scaleY: sy }],
    };
  });
  const paperFade = useAnimatedStyle(() => ({ opacity: paperO.get() }));
  const developStyle = useAnimatedStyle(() => ({ opacity: develop.get() * UPGRADE.developOpacity }));
  const ledStyle = useAnimatedStyle(() => {
    const blink = phase.get() === PRINTING ? 0.5 + 0.5 * Math.sin((clock.get() / 1000) * UPGRADE.ledBlinkHz * 2 * Math.PI) : 0;
    return { opacity: armed.get() ? POLISH.armedLed : phase.get() === PRINTING ? 0.4 + 0.6 * blink : UPGRADE.ledIdle };
  });
  // Pre-blurred glow (a soft-shadowed disc), its opacity following the light; nothing blurs per frame.
  const ledGlowStyle = useAnimatedStyle(() => ({ opacity: armed.get() ? 0.35 : (phase.get() === PRINTING ? 0.35 : 0.12) * (phase.get() === PRINTING ? 0.4 + 0.6 * (0.5 + 0.5 * Math.sin((clock.get() / 1000) * UPGRADE.ledBlinkHz * 2 * Math.PI)) : 1) }));
  // The bar's progress is linear in time (it follows the scroll); shaped here with an ease-out cubic.
  const barStyle = useAnimatedStyle(() => {
    const e = 1 - Math.pow(1 - barIn.get(), 3);
    return { opacity: e, transform: [{ translateY: (1 - e) * SPACE.lg }] };
  });
  const indicatorStyle = useAnimatedStyle(() => {
    // Runs from SPACE.sm under the slot to SPACE.sm above the action bar's row.
    const view = BAR.top + shift - SPACE.sm - (paperTop + SPACE.sm);
    const h = Math.max(36, (view * view) / (RECEIPT.length + 120));
    const p = Math.min(1, Math.max(0, scroll.get() / Math.max(1, maxScroll)));
    // Like a scroll view's indicator: shown once the paper has been moved.
    return { opacity: withTiming(phase.get() === READING && scroll.get() > 2 ? 1 : 0, { duration: 150, easing: EASE_OUT }), height: h, transform: [{ translateY: paperTop + SPACE.sm + p * (view - h) }] };
  });

  const m = GRID.menu;
  // Text laid out before the face loads would keep the fallback's metrics.
  if (!fontsLoaded && !fontsFailed) return <View style={styles.root} />;
  const reading = stage === READING;
  return <GestureHandlerRootView style={styles.root}>
    {/* The open island covers the status bar's corners, as a Live Activity does: hide it while printing. */}
    <StatusBar style="dark" hidden={stage !== GRID_ && stage !== MENU} animated={false} />
    <View style={styles.root}>
      <Animated.View style={[StyleSheet.absoluteFill, gridStyle]} pointerEvents={stage === GRID_ ? 'auto' : 'none'}>
        <RunGrid top={shift} pressed={pressed} lift={lift}
          onPress={(i) => print(false, i)} onLongPress={(i) => openMenu(i, false)} />
      </Animated.View>
      {stage === MENU && <Pressable style={StyleSheet.absoluteFill} onPress={() => closeMenu()} accessibilityLabel="Close menu" />}
      <Animated.View style={[styles.menu, { left: m.x, top: m.y + shift, width: m.w, height: m.h }, menuStyle]} pointerEvents={stage === MENU ? 'auto' : 'none'}>
        {[{ icon: 'doc.plaintext' as SFSymbol, label: COPY.long }, { icon: 'text.alignleft' as SFSymbol, label: COPY.short }].map((it, k) =>
          <Pressable key={it.label} onPress={() => print(false)} accessibilityRole="menuitem" style={({ pressed: down }) => [styles.menuRow, { height: m.rowH }, down && styles.menuRowDown]}>
            <SymbolView name={it.icon} size={15} tintColor={COLOR.ink} weight="medium" style={{ position: 'absolute', left: m.iconX - 7 }} />
            <Text style={[styles.menuText, { left: m.textX }]}>{it.label}</Text>
            {k === 0 && <View style={styles.menuRule} />}
          </Pressable>)}
      </Animated.View>

      {/* The printer: ring behind, paper over its lower lip, the upper lip and slit over the paper. */}
      <Animated.View style={[styles.morph, morphStyle]} pointerEvents="none" />
      <GestureDetector gesture={pan}>
        <Animated.View style={[styles.paperClipOuter, { top: paperTop }, paperFade]} pointerEvents={reading ? 'auto' : 'none'}
          accessible accessibilityRole="scrollbar" accessibilityLabel="Run receipt"
          accessibilityActions={[{ name: 'increment', label: 'Scroll down' }, { name: 'decrement', label: 'Scroll up' }, { name: 'escape', label: COPY.tear }]}
          onAccessibilityAction={(e) => {
            const n = e.nativeEvent.actionName;
            if (n === 'escape') retract(false);
          }}>
          <Animated.View style={[styles.paperShadow, shadowStyle]} />
          <Animated.View style={[styles.paperClip, clipStyle]}>
            <Animated.View style={paperStyle}>
              <ReceiptPaper />
            </Animated.View>
          </Animated.View>
        </Animated.View>
      </GestureDetector>
      <Animated.View style={[StyleSheet.absoluteFill, slotStyle]} pointerEvents="none">
        {/* The island's shadow falls on the paper just under its lower edge. */}
        {/* Fresh print develops: the newest lines come out pale and darken as they travel. */}
        <Animated.View style={[styles.slotShade, { top: paperTop + ISLAND.tuck, height: UPGRADE.developH }, developStyle]}>
          <LinearGradient colors={[COLOR.develop, 'rgba(255,255,255,0)']} style={StyleSheet.absoluteFill} />
        </Animated.View>
        <Animated.View style={[styles.slotShade, { top: paperTop + ISLAND.tuck, height: SLOT.shadowH }, shadeStyle]}>
          <LinearGradient colors={['rgba(0,0,0,0.22)', 'rgba(0,0,0,0)']} style={StyleSheet.absoluteFill} />
        </Animated.View>
      </Animated.View>
      {/* Always drawn: closed, it is the Dynamic Island itself. */}
      <Animated.View style={[styles.island, { top: islandTop }, islandStyle]} pointerEvents="none">
        <Animated.View style={[styles.ledAnchor, ledPlace]}>
          <Animated.View style={[styles.ledGlow, { left: -SLOT.led.glow, top: -SLOT.led.glow }, ledGlowStyle]} />
          <Animated.View style={[styles.led, { left: -SLOT.led.r, top: -SLOT.led.r }, ledStyle]} />
        </Animated.View>
      </Animated.View>
      <Animated.View style={[styles.indicator, indicatorStyle]} pointerEvents="none" />

      <Animated.View style={[styles.bar, { top: BAR.top + shift }, barStyle]} pointerEvents={reading ? 'box-none' : 'none'}>
        <BarButton icon="tray.and.arrow.down" label={COPY.save} dark />
        <BarButton icon="list.bullet.rectangle" label={COPY.tear} onPress={() => retract(false)} />
        <BarButton icon="signature" label={COPY.sign} />
      </Animated.View>
    </View>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.bg },
  menu: {
    position: 'absolute', borderRadius: GRID.menu.radius, backgroundColor: COLOR.menu, paddingVertical: 8,
    shadowColor: COLOR.shadow, shadowOpacity: 0.14, shadowRadius: 22, shadowOffset: { width: 0, height: 10 },
  },
  menuRow: { justifyContent: 'center' },
  // A menu row highlights at once on touch, like the system's (no animation: it is touched many times a day).
  menuRowDown: { backgroundColor: 'rgba(0,0,0,0.06)' },
  menuText: { position: 'absolute', fontFamily: FONT.regular, fontSize: TYPE.menu, color: COLOR.ink },
  menuRule: { position: 'absolute', left: 0, right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(0,0,0,0.08)' },
  island: { position: 'absolute', left: 0, backgroundColor: ISLAND.color },
  // A 1 pt anchor centred on the light, ISLAND.ledInset from the island's right end and halfway down.
  ledAnchor: { position: 'absolute', right: ISLAND.ledInset - 0.5, top: '50%', marginTop: -0.5, width: 1, height: 1 },
  slotShade: { position: 'absolute', left: SLOT.paperX0, width: SLOT.paperX1 - SLOT.paperX0 },
  paperClipOuter: { position: 'absolute', left: 0, right: 0, bottom: 0, overflow: 'hidden' },
  paperShadow: {
    // Stops above the torn foot so the scallops' gaps stay open.
    position: 'absolute', top: 0, height: RECEIPT.length - RECEIPT.edge.depth, left: SLOT.paperX0, width: SLOT.paperX1 - SLOT.paperX0, backgroundColor: COLOR.paper,
    shadowColor: COLOR.shadow, shadowOpacity: 0.16, shadowRadius: 14, shadowOffset: { width: 0, height: 4 },
  },
  paperClip: { position: 'absolute', top: 0, left: SLOT.paperX0, width: SLOT.paperX1 - SLOT.paperX0, height: RECEIPT.length + RECEIPT.edge.depth, overflow: 'hidden' },
  morph: { position: 'absolute', left: 0, top: 0, width: GRID.cardW, height: GRID.cardH, borderRadius: GRID.radius },
  led: { position: 'absolute', width: SLOT.led.r * 2, height: SLOT.led.r * 2, borderRadius: SLOT.led.r, backgroundColor: COLOR.led },
  ledGlow: { position: 'absolute', width: SLOT.led.glow * 2, height: SLOT.led.glow * 2, borderRadius: SLOT.led.glow, backgroundColor: COLOR.led, shadowColor: COLOR.led, shadowOpacity: 0.9, shadowRadius: 6 },
  indicator: { position: 'absolute', right: 3, top: 0, width: 3, borderRadius: 1.5, backgroundColor: COLOR.indicator },
  bar: { position: 'absolute', left: BAR.x, flexDirection: 'row', gap: BAR.gap },
  barHit: { width: BAR.w, height: BAR.h },
  barButton: { width: BAR.w, height: BAR.h, borderRadius: BAR.h / 2, alignItems: 'center' },
  barDark: { backgroundColor: COLOR.buttonDark },
  barLight: { backgroundColor: COLOR.buttonLight, shadowColor: COLOR.shadow, shadowOpacity: 0.08, shadowRadius: 10, shadowOffset: { width: 0, height: 3 } },
  barIcon: { position: 'absolute', width: BAR.icon, height: BAR.icon },
  barLabel: { position: 'absolute', fontFamily: FONT.bold, fontSize: TYPE.button },
});

