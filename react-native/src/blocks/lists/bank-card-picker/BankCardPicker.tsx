import { useFonts } from 'expo-font';
import * as Haptics from 'expo-haptics';
import { BlurView } from 'expo-blur';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, AppState, Platform, Pressable, ScrollView, Share, StyleSheet, Text, View, useWindowDimensions, type GestureResponderEvent } from 'react-native';
import Animated, {
  FadeIn,
  FadeInDown,
  FadeOut,
  ReduceMotion,
  cancelAnimation,
  type FrameInfo,
  type SharedValue,
  useAnimatedStyle,
  useFrameCallback,
  useReducedMotion,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { CardArt } from './CardArt';
import { HeroField } from './HeroField';
import { DESIGNS, type Design, PLANS, PRODUCT } from './designs';
import { BackGlyph, CheckGlyph, FlagGlyph, ShareGlyph } from './Icons';
import {
  createBankCardLifecycleController, createForegroundReturnGate, runPausedAction,
  type BankCardLifecycleController, type ForegroundReturnGate,
} from './lifecycle';
import { CHECK, EASE_OUT, LOOP_MS, PILL, PRESS, RESUME_AFTER_MS, RING, SCRIPT, SWAP, TEXT_SWAP, pickerStateAt } from './motion';
import { C, CARD, CTA, FONT, HERO, INFO, NAV, SEG, STACK, SUB, THUMBS, TITLE } from './theme';

export type BankCardSelection = {
  designId: string;
  designLabel: string;
  planId: string;
  planLabel: string;
};

type SelectionHandler = (selection: BankCardSelection) => void | Promise<void>;
type Props = {
  autoPlay?: boolean;
  freezeAt?: number;
  onBack?: () => void;
  onShare?: SelectionHandler;
  onGetCard?: SelectionHandler;
};
const NEVER = ReduceMotion.Never;
const CUES = SCRIPT;

// labels that change leave fast; the new one arrives once the old one is gone (no double state)
const enterText = FadeInDown.delay(TEXT_SWAP.out).duration(TEXT_SWAP.in).easing(EASE_OUT).withInitialValues({ opacity: 0, transform: [{ translateY: -TEXT_SWAP.rise }] });
const exitText = FadeOut.duration(TEXT_SWAP.out);

export function BankCardPicker(props: Props) {
  return <BankCardPickerSurface key={props.freezeAt === undefined ? 'live' : `freeze-${props.freezeAt}`} {...props} />;
}

function BankCardPickerSurface({ autoPlay = false, freezeAt, onBack, onShare, onGetCard }: Props) {
  'use no memo';
  const [loaded] = useFonts({
    [FONT.regular]: require('./assets/fonts/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/fonts/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/fonts/Inter-SemiBold.ttf'),
  });
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const frozen = freezeAt !== undefined;
  const dy = insets.top - 62; // measured on a phone with a 62 pt top inset
  const frozenState = pickerStateAt(freezeAt ?? 0);

  // Two card slots; the design shown in front is the one `swap` points at (0 → A, 1 → B).
  const [slotA, setSlotA] = useState(frozen ? frozenState.slotA : 0);
  const [slotB, setSlotB] = useState(frozen ? frozenState.slotB : 0);
  const [design, setDesign] = useState(frozen ? frozenState.design : 0);
  const [plan, setPlan] = useState(frozen ? frozenState.plan : 0);
  const swap = useSharedValue(frozen ? frozenState.swap : 0);
  const ring = useSharedValue(frozen ? frozenState.ring : 0); // selected design, animated
  const pill = useSharedValue(frozen ? frozenState.pill : 0); // selected plan, animated
  const ctaPress = useSharedValue(0);

  const selectDesign = (i: number, byFinger: boolean) => {
    if (i === design) return;
    const toB = swap.get() < 0.5;
    if (toB) setSlotB(i);
    else setSlotA(i);
    setDesign(i);
    const target = toB ? 1 : 0;
    swap.set(reduceMotion ? withTiming(target, { duration: 200, reduceMotion: NEVER }) : withTiming(target, { duration: SWAP.duration, easing: SWAP.easing }));
    ring.set(reduceMotion ? i : withSpring(i, RING));
    if (byFinger) Haptics.selectionAsync();
  };
  const selectPlan = (i: number, byFinger: boolean) => {
    if (i === plan) return;
    setPlan(i);
    pill.set(reduceMotion ? withTiming(i, { duration: 160, reduceMotion: NEVER }) : withSpring(i, PILL));
    if (byFinger) Haptics.selectionAsync();
  };

  // ---- self-playing loop: React state changes only at the script's cues ----
  const clock = useSharedValue(freezeAt ?? 0);
  const playing = useSharedValue(false);
  const lastCue = useSharedValue(-1);
  // the frame worklet calls back through a ref, so it always reaches this render's handlers
  const api = useRef({ selectDesign, selectPlan });
  useEffect(() => {
    api.current = { selectDesign, selectPlan };
  });
  const runCue = useRef((k: number) => {
    const c = CUES[k];
    if (c.act === 'design') api.current.selectDesign(c.to, false);
    else api.current.selectPlan(c.to, false);
  }).current;
  const frameWorklet = useCallback((f: FrameInfo) => {
    'worklet';
    if (!playing.get()) return;
    const t = clock.get() + (f.timeSincePreviousFrame ?? 16);
    const wrapped = t >= LOOP_MS ? t - LOOP_MS : t;
    if (wrapped < clock.get()) lastCue.set(-1);
    clock.set(wrapped);
    const next = lastCue.get() + 1;
    if (next < CUES.length && wrapped >= CUES[next].at) {
      lastCue.set(next);
      scheduleOnRN(runCue, next);
    }
  }, [clock, lastCue, playing, runCue]);
  const frameDriver = useFrameCallback(frameWorklet, false);
  const foregroundGates = useRef(new Set<ForegroundReturnGate>());
  const confirmationAction = useRef<number | null>(null);
  const pauseFrameDriver = useCallback(() => {
    playing.set(false);
    frameDriver.setActive(false);
  }, [frameDriver, playing]);
  const resetState = useCallback((state = pickerStateAt(0)) => {
    cancelAnimation(swap);
    cancelAnimation(ring);
    cancelAnimation(pill);
    cancelAnimation(ctaPress);
    cancelAnimation(clock);
    setSlotA(state.slotA);
    setSlotB(state.slotB);
    setDesign(state.design);
    setPlan(state.plan);
    swap.set(state.swap);
    ring.set(state.ring);
    pill.set(state.pill);
    ctaPress.set(0);
    clock.set(0);
    lastCue.set(-1);
  }, [clock, ctaPress, lastCue, pill, ring, swap]);
  const startFromRest = useCallback(() => {
    resetState(pickerStateAt(0));
    clock.set(0);
    playing.set(true);
    frameDriver.setActive(true);
  }, [clock, frameDriver, playing, resetState]);
  const lifecycle: BankCardLifecycleController = useMemo(
    () => createBankCardLifecycleController(
      { pause: pauseFrameDriver, startFromRest },
      RESUME_AFTER_MS,
    ),
    [pauseFrameDriver, startFromRest],
  );
  useEffect(() => {
    const gates = foregroundGates.current;
    lifecycle.activate();
    return () => {
      lifecycle.dispose();
      for (const gate of gates) gate.cancel();
      gates.clear();
    };
  }, [lifecycle]);
  useEffect(() => {
    lifecycle.updateFlags({ autoPlay, reducedMotion: reduceMotion, frozen });
  }, [autoPlay, frozen, lifecycle, reduceMotion]);

  const onTouchStart = (event: GestureResponderEvent) => {
    lifecycle.beginContacts(event.nativeEvent.changedTouches.length);
  };
  const onTouchEnd = (event: GestureResponderEvent) => {
    lifecycle.endContacts(event.nativeEvent.changedTouches.length);
  };

  const selection = (): BankCardSelection => ({
    designId: DESIGNS[design].id,
    designLabel: DESIGNS[design].label,
    planId: PLANS[plan].id,
    planLabel: PLANS[plan].label,
  });
  const shareSelection = async () => {
    const value = selection();
    if (onShare) {
      await runPausedAction(() => lifecycle.beginAction(), (token) => lifecycle.endAction(token), () => onShare(value));
      return;
    }
    const gate = Platform.OS === 'android' ? createForegroundReturnGate(AppState) : null;
    if (gate) foregroundGates.current.add(gate);
    try {
      await runPausedAction(() => lifecycle.beginAction(), (token) => lifecycle.endAction(token), async () => {
        await Share.share({ message: `Bloom Card preview\nDesign: ${value.designLabel}\nPlan: ${value.planLabel}` });
        await gate?.promise;
      });
    } finally {
      if (gate) foregroundGates.current.delete(gate);
      gate?.cancel();
    }
  };
  const confirmSelection = async () => {
    const value = selection();
    if (onGetCard) {
      await runPausedAction(() => lifecycle.beginAction(), (token) => lifecycle.endAction(token), () => onGetCard(value));
      return;
    }
    const token = lifecycle.beginAction();
    confirmationAction.current = token;
    const finish = () => {
      lifecycle.endAction(token);
      if (confirmationAction.current === token) confirmationAction.current = null;
    };
    Alert.alert(
      'Card selected',
      `${value.designLabel}\n${value.planLabel}`,
      [{ text: 'Done', onPress: finish }],
      { cancelable: true, onDismiss: finish },
    );
  };


  const ctaStyle = useAnimatedStyle(() => ({ transform: [{ scale: 1 - (1 - PRESS.scale) * ctaPress.get() }] }));
  const segW = width - SEG.side * 2;
  const halfW = (segW - SEG.inset * 2) / 2;
  const pillStyle = useAnimatedStyle(() => ({ transform: [{ translateX: pill.get() * halfW }] }));
  const basicOn = useAnimatedStyle(() => ({ opacity: 1 - pill.get() }));
  const basicOff = useAnimatedStyle(() => ({ opacity: pill.get() }));
  const primeOn = useAnimatedStyle(() => ({ opacity: pill.get() }));
  const primeOff = useAnimatedStyle(() => ({ opacity: 1 - pill.get() }));

  if (!loaded) return <View style={[styles.root, { backgroundColor: C.heroTop }]} />;
  const d = DESIGNS[design];
  const p = PLANS[plan];

  return (
    <View pointerEvents={frozen ? 'none' : 'auto'} style={styles.root} onTouchStart={onTouchStart} onTouchEnd={onTouchEnd} onTouchCancel={onTouchEnd}>
      <StatusBar style="light" />

      {/* hero */}
      <View style={[styles.hero, { height: HERO.height + dy }]}>
        <HeroField width={width} height={HERO.height + dy} />

        <View style={[styles.stage, { top: dy }]} pointerEvents="none">
          {Array.from({ length: STACK.count }, (_, j) => STACK.count - j).map((k) => (
            <Plate key={k} k={k} />
          ))}
          <CardSlot design={DESIGNS[slotA]} q={swap} invert />
          <CardSlot design={DESIGNS[slotB]} q={swap} />
        </View>

        <Pressable disabled={!onBack} onPress={onBack} style={[styles.nav, { left: NAV.backLeft - (NAV.hit - NAV.back.w) / 2, top: NAV.y + dy - NAV.hit / 2 }]} accessibilityRole="button" accessibilityLabel="Back"
          accessibilityHint={!onBack ? 'Use the system back gesture to leave this preview' : undefined} accessibilityState={{ disabled: !onBack }} pressRetentionOffset={16}>
          <BackGlyph />
        </Pressable>
        <Pressable onPress={() => { shareSelection().catch(() => {}); }} style={[styles.nav, { right: NAV.shareRight - (NAV.hit - NAV.share.w) / 2, top: NAV.y + dy - NAV.hit / 2 }]} accessibilityRole="button" accessibilityLabel="Share selected card" pressRetentionOffset={16}>
          <ShareGlyph />
        </Pressable>

        <Text style={[styles.title, { top: TITLE.y + dy - 18 }]} accessibilityRole="header">
          {PRODUCT}
        </Text>
        <Animated.View key={d.id} entering={reduceMotion ? FadeIn.duration(160) : enterText} exiting={exitText} style={[styles.subRow, { top: SUB.y + dy - 12 }]}>
          <Text style={styles.sub}>{`Design by ${d.designer}`}</Text>
          <View style={styles.pricePill}>
            <Text style={styles.price}>{d.price}</Text>
          </View>
        </Animated.View>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={[styles.thumbs, { top: THUMBS.y + dy - THUMBS.size / 2 - 6 }]}
          contentContainerStyle={styles.thumbsContent}
        >
          <Ring ring={ring} />
          {DESIGNS.map((x, i) => (
            <Thumb key={x.id} design={x} index={i} ring={ring} selected={i === design} onPress={() => selectDesign(i, true)} />
          ))}
        </ScrollView>
      </View>

      {/* plan switch */}
      <View style={[styles.seg, { top: SEG.top + dy, left: SEG.side, width: segW }]} accessibilityRole="tablist">
        <Animated.View style={[styles.segPill, { width: halfW }, pillStyle]} />
        {PLANS.map((x, i) => (
          <Pressable
            key={x.id}
            style={[styles.segItem, { width: halfW }]}
            onPress={() => selectPlan(i, true)}
            pressRetentionOffset={16}
            accessibilityRole="tab"
            accessibilityLabel={x.label}
            accessibilityState={{ selected: i === plan }}
          >
            <View>
              <Animated.Text style={[styles.segLabel, { color: C.grey }, i === 0 ? basicOff : primeOff]}>{x.label}</Animated.Text>
              <Animated.Text style={[styles.segLabel, styles.stack, { color: C.white }, i === 0 ? basicOn : primeOn]}>{x.label}</Animated.Text>
            </View>
            {i === 1 ? (
              <View style={styles.flag}>
                <FlagGlyph size={SEG.flag} />
              </View>
            ) : null}
          </Pressable>
        ))}
      </View>

      {/* what the plan includes */}
      <View style={[styles.info, { top: INFO.top + dy, left: INFO.side, right: INFO.side }]}>
        <View style={[styles.row, { top: INFO.row1 - INFO.top }]}>
          <Text style={styles.label}>{'Card\nmaintenance'}</Text>
          <Animated.View key={`fee-${p.id}`} entering={enterText} exiting={exitText} style={styles.valueCol}>
            <Text style={styles.value}>{p.fee}</Text>
            <Text style={styles.unit}>{p.feeUnit}</Text>
          </Animated.View>
        </View>
        <View style={[styles.divider, { top: INFO.divider - INFO.top }]} />
        <View style={[styles.row, { top: INFO.row2 - INFO.top }]}>
          <Text style={styles.label}>{'Cashback\ncategories'}</Text>
          <Animated.View key={`cat-${p.id}`} entering={enterText} exiting={exitText} style={styles.valueCol}>
            <Text style={styles.value}>{p.categories}</Text>
          </Animated.View>
        </View>
        {/* the next row, which the pin shows only blurred under the button's frosted bar */}
        <View style={[styles.divider, { top: INFO.divider2 - INFO.top }]} />
        <View style={[styles.row, { top: INFO.row3 - INFO.top }]}>
          <Text style={styles.label}>Transfers to any bank</Text>
          <Text style={styles.value}>Free</Text>
        </View>
      </View>

      {/* the list fades under the button, as in the pin */}
      <View style={[styles.bar, { top: CTA.barTop + dy }]} pointerEvents="none">
        <BlurView intensity={45} tint="light" style={StyleSheet.absoluteFill} />
        <View style={[StyleSheet.absoluteFill, styles.barTint]} />
      </View>
      <Animated.View style={[styles.cta, { top: CTA.top + dy }, ctaStyle]}>
        <Pressable
          style={styles.ctaHit}
          pressRetentionOffset={16}
          onPressIn={() => ctaPress.set(withTiming(1, { duration: PRESS.in, easing: EASE_OUT, reduceMotion: NEVER }))}
          onPressOut={() => ctaPress.set(withTiming(0, { duration: PRESS.out, easing: EASE_OUT, reduceMotion: NEVER }))}
          onPress={() => {
            Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
            confirmSelection().catch(() => {});
          }}
          accessibilityRole="button"
          accessibilityLabel="Get the card"
          accessibilityHint={`Select ${d.label} with the ${p.label} plan`}
        >
          <Text style={styles.ctaLabel}>Get the card</Text>
        </Pressable>
      </Animated.View>
    </View>
  );
}

// the pose of a layer k plates behind the front, in the stage's coordinates
function plateTransform(k: number) {
  'worklet';
  return [{ translateX: STACK.dx * k }, { translateY: STACK.dy * k }, { skewY: `${CARD.skewY}deg` }, { scaleX: CARD.scaleX }];
}

function Plate({ k }: { k: number }) {
  // deeper plates take on the teal of the back of the stack
  const t = (k - 1) / (STACK.count - 1);
  const mix = C.plateFront.map((v, i) => Math.round(v + (C.plateBack[i] - v) * t));
  const body = `rgb(${mix[0]}, ${mix[1]}, ${mix[2]})`;
  return (
    <View style={[styles.card, styles.plate, { backgroundColor: body, opacity: STACK.alpha, transform: plateTransform(k) }]}>
      {/* lit top edge, and the front card's shadow on the lower part */}
      <LinearGradient colors={[C.plateEdge, 'rgba(236, 255, 244, 0)', 'rgba(10, 60, 48, 0)', C.plateShade]} locations={[0, 0.1, 0.55, 1]} style={StyleSheet.absoluteFill} />
    </View>
  );
}

// A front-card slot. q = how close it is to the front (1) from the first plate (0).
function CardSlot({ design, q, invert }: { design: Design; q: SharedValue<number>; invert?: boolean }) {
  const style = useAnimatedStyle(() => {
    const v = invert ? 1 - q.get() : q.get();
    const k = 1 - v;
    return {
      opacity: Math.max(0, Math.min(1, (v - 0.25) / 0.5)),
      zIndex: v > 0.5 ? 2 : 1,
      transform: plateTransform(k),
    };
  });
  return (
    <Animated.View style={[styles.card, styles.front, style]}>
      <CardArt design={design} />
    </Animated.View>
  );
}

function Ring({ ring }: { ring: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: ring.get() * THUMBS.step }] }));
  return <Animated.View style={[styles.ring, style]} pointerEvents="none" />;
}

function Thumb({ design, index, ring, selected, onPress }: { design: Design; index: number; ring: SharedValue<number>; selected: boolean; onPress: () => void }) {
  const press = useSharedValue(0);
  const tick = useAnimatedStyle(() => {
    const on = Math.max(0, 1 - Math.abs(ring.get() - index) * 2);
    return { opacity: on, transform: [{ scale: CHECK.from + (1 - CHECK.from) * on }] };
  });
  // the selected thumbnail shrinks inside the ring (its own box), the others fill it
  const pressStyle = useAnimatedStyle(() => {
    const on = Math.max(0, 1 - Math.abs(ring.get() - index));
    const sel = 1 - ((THUMBS.inset * 2) / THUMBS.size) * on;
    return { transform: [{ scale: sel * (1 - (1 - PRESS.scale) * press.get()) }] };
  });
  return (
    <Pressable
      onPress={onPress}
      onPressIn={() => press.set(withTiming(1, { duration: PRESS.in, easing: EASE_OUT, reduceMotion: NEVER }))}
      onPressOut={() => press.set(withTiming(0, { duration: PRESS.out, easing: EASE_OUT, reduceMotion: NEVER }))}
      pressRetentionOffset={16}
      accessibilityRole="button"
      accessibilityLabel={`${design.label}, design by ${design.designer}, ${design.price}`}
      accessibilityState={{ selected }}
      style={styles.thumbSlot}
    >
      <Animated.View style={[styles.thumb, pressStyle]}>
        <Image source={design.thumb} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
        <View style={styles.thumbBezel} />
        <Animated.View style={[styles.check, tick]}>
          <CheckGlyph />
        </Animated.View>
      </Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.page },
  hero: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    borderBottomLeftRadius: HERO.radius,
    borderBottomRightRadius: HERO.radius,
    borderCurve: 'continuous',
    overflow: 'hidden',
  },
  stage: { position: 'absolute', left: CARD.cx - CARD.w / 2, width: CARD.w, height: CARD.h, marginTop: CARD.cy - CARD.h / 2 },
  card: { position: 'absolute', left: 0, top: 0, width: CARD.w, height: CARD.h, borderRadius: CARD.radius, borderCurve: 'continuous', overflow: 'hidden' },
  plate: { borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(220, 255, 240, 0.35)' },
  // the card's shadow falls on the plates below it (dark green there in the pin)
  front: { shadowColor: '#032a18', shadowOpacity: 0.6, shadowRadius: 14, shadowOffset: { width: -4, height: 18 } },
  nav: { position: 'absolute', width: NAV.hit, height: NAV.hit, alignItems: 'center', justifyContent: 'center' },
  title: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontFamily: FONT.semibold, fontSize: TITLE.size, lineHeight: 36, letterSpacing: TITLE.track, color: C.white },
  subRow: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: SUB.pillGap },
  sub: { fontFamily: FONT.regular, fontSize: SUB.size, lineHeight: 24, color: C.white },
  pricePill: { height: SUB.pillH, borderRadius: SUB.pillH / 2, paddingHorizontal: SUB.pillPad, backgroundColor: C.lime, justifyContent: 'center' },
  price: { fontFamily: FONT.medium, fontSize: SUB.pillSize, fontVariant: ['tabular-nums'], color: C.ink },
  thumbs: { position: 'absolute', left: 0, right: 0, height: THUMBS.size + 12 },
  thumbsContent: { paddingLeft: THUMBS.left, paddingRight: 24, paddingTop: 6, gap: THUMBS.step - THUMBS.size },
  thumbSlot: { width: THUMBS.size, height: THUMBS.size },
  thumb: { width: THUMBS.size, height: THUMBS.size, borderRadius: THUMBS.radius, borderCurve: 'continuous', overflow: 'hidden' },
  thumbBezel: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    borderRadius: THUMBS.radius,
    borderCurve: 'continuous',
    borderWidth: THUMBS.inner,
    borderColor: 'rgba(16, 40, 44, 0.35)',
  },
  check: { position: 'absolute', left: (THUMBS.size - 30) / 2, top: (THUMBS.size - 24) / 2 },
  ring: {
    position: 'absolute',
    left: THUMBS.left,
    top: 6,
    width: THUMBS.size,
    height: THUMBS.size,
    borderRadius: THUMBS.radius,
    borderCurve: 'continuous',
    borderWidth: THUMBS.ring,
    borderColor: C.lime,
  },
  seg: { position: 'absolute', height: SEG.h, borderRadius: SEG.radius, borderCurve: 'continuous', backgroundColor: C.white, flexDirection: 'row', padding: SEG.inset },
  segPill: { position: 'absolute', left: SEG.inset, top: SEG.inset, bottom: SEG.inset, borderRadius: SEG.pillRadius, borderCurve: 'continuous', backgroundColor: C.dark },
  segItem: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  segLabel: { fontFamily: FONT.semibold, fontSize: SEG.size },
  stack: { position: 'absolute', left: 0, top: 0 },
  flag: { marginTop: 1 },
  info: { position: 'absolute', bottom: -40, borderTopLeftRadius: INFO.radius, borderTopRightRadius: INFO.radius, borderCurve: 'continuous', backgroundColor: C.white },
  row: { position: 'absolute', left: INFO.pad, right: INFO.pad, flexDirection: 'row', justifyContent: 'space-between' },
  label: { fontFamily: FONT.regular, fontSize: INFO.labelSize, lineHeight: INFO.labelLine, color: C.ink },
  valueCol: { alignItems: 'flex-end' },
  value: { fontFamily: FONT.semibold, fontSize: INFO.valueSize, lineHeight: 28, fontVariant: ['tabular-nums'], color: C.ink },
  unit: { fontFamily: FONT.medium, fontSize: INFO.unitSize, lineHeight: 20, color: C.ink },
  divider: { position: 'absolute', left: INFO.pad, right: INFO.pad, height: StyleSheet.hairlineWidth, backgroundColor: C.hairline },
  bar: { position: 'absolute', left: 0, right: 0, bottom: 0, borderTopLeftRadius: CTA.barRadius, borderTopRightRadius: CTA.barRadius, borderCurve: 'continuous', overflow: 'hidden' },
  barTint: { backgroundColor: 'rgba(248, 248, 248, 0.55)' },
  cta: { position: 'absolute', left: CTA.side, right: CTA.side, height: CTA.h, borderRadius: CTA.radius, borderCurve: 'continuous', backgroundColor: C.dark },
  ctaHit: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  ctaLabel: { fontFamily: FONT.medium, fontSize: CTA.size, color: C.white },
});
