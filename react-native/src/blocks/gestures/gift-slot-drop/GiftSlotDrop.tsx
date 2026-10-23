import type { ReactNode } from 'react';
/**
 * Gift Slot Drop: swipe through welcome gifts, drag one down into the glowing slot, and the activated
 * offer prints back out. The stage is one Skia canvas; words and buttons are views on top of it.
 */
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { Image, Pressable, StyleSheet, Text, View, useWindowDimensions, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { ReduceMotion, cubicBezier, useAnimatedStyle, useReducedMotion, withDelay, withTiming } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';
import { scheduleOnUI } from 'react-native-worklets';

import { GIFT_BOX } from './gift-assets';
import { GiftScene } from './GiftScene';
import { BUTTON_PRESS, PRINT, REDUCED_MS } from './motion';
import { COLORS, GIFTS, LAYOUT, START_INDEX, SPACE } from './theme';
import { PHASE, useGiftSlot, type Haptic } from './use-gift-slot';

export type GiftSlotDropProps = { autoPlay?: boolean; onShop?: (giftId: string) => void };

// A geometric sans like the source's; Avenir Next is the closest system face on iOS.
const FONT = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : undefined;

const HAPTIC: Record<Haptic, () => void> = {
  select: () => { void Haptics.selectionAsync(); },
  armed: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); },
  insert: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy); },
  success: () => { void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success); },
  light: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); },
};

/** The glossy 3D gift box from the source, generated as artwork. */
function GiftIcon({ size = LAYOUT.giftSize }: { size?: number }) {
  return <Image source={GIFT_BOX} style={{ width: size, height: size }} accessibilityIgnoresInvertColors />;
}

export function GiftSlotDrop({ autoPlay = false, onShop }: GiftSlotDropProps) {
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  return <Stage key={`${width}x${height}-${reduced}`} width={width} height={height} reduced={reduced} autoPlay={autoPlay} onShop={onShop} />;
}

function Stage({ width, height, reduced, autoPlay, onShop }: { width: number; height: number; reduced: boolean; autoPlay: boolean; onShop?: (id: string) => void }) {
  const [phase, setPhase] = useState<number>(PHASE.choosing);
  const [armed, setArmed] = useState(false);
  const [index, setIndex] = useState(START_INDEX);
  const onHaptic = useCallback((k: Haptic) => HAPTIC[k](), []);
  const { values: v, input } = useGiftSlot(width, autoPlay, reduced, { onPhase: setPhase, onArmed: setArmed, onIndex: setIndex, onHaptic });
  const gift = GIFTS[index];

  const pan = useMemo(() => Gesture.Pan().minDistance(0).maxPointers(1)
    .onBegin((e) => { input.takeOver(); input.down(e.absoluteX, e.absoluteY, false); })
    .onUpdate((e) => { input.move(e.absoluteX, e.absoluteY); })
    .onFinalize((e) => { input.up(e.velocityX, e.velocityY); input.letGo(); }), [input]);
  const ui = useCallback((fn: () => void) => scheduleOnUI(() => {
    'worklet';
    input.takeOver();
    fn();
    input.letGo();
  }), [input]);

  const choosing = phase === PHASE.choosing || phase === PHASE.pulling || phase === PHASE.swiping;
  const activating = phase === PHASE.inserting || phase === PHASE.activating;
  const offer = phase === PHASE.offer;
  const fade = (on: boolean, delay = 0) => {
    'worklet';
    return withDelay(on ? delay : 0, withTiming(on ? 1 : 0, { duration: 220, reduceMotion: ReduceMotion.Never }));
  };
  const chooseStyle = useAnimatedStyle(() => ({ opacity: fade(choosing && !armed) }), [choosing, armed]);
  const armedStyle = useAnimatedStyle(() => ({ opacity: fade(choosing && armed) }), [choosing, armed]);
  const activeStyle = useAnimatedStyle(() => ({ opacity: fade(activating, reduced ? 0 : 200) }), [activating]);
  const topStyle = useAnimatedStyle(() => ({ opacity: fade(!offer) }), [offer]);
  const hintStyle = useAnimatedStyle(() => ({ opacity: fade(phase === PHASE.choosing || phase === PHASE.swiping) }), [phase]);
  // How-to and the button rise in once the printed offer has risen.
  const detailDelay = reduced ? REDUCED_MS : PRINT.feeds * (PRINT.feedMs + PRINT.feedGap) + PRINT.detailsDelay;
  const detail = (k: number) => {
    'worklet';
    const delay = offer ? detailDelay + k * PRINT.stagger : 0;
    return {
      opacity: fade(offer, detailDelay + k * PRINT.stagger),
      transform: [{ translateY: withDelay(delay, withTiming(offer || reduced ? 0 : 12, { duration: 320 })) }],
    };
  };
  const detail0 = useAnimatedStyle(() => detail(0), [offer]);
  const detail1 = useAnimatedStyle(() => detail(1), [offer]);
  const detail2 = useAnimatedStyle(() => detail(2), [offer]);
  const detail3 = useAnimatedStyle(() => detail(3), [offer]);

  return (
    <GestureHandlerRootView style={styles.root}>
      <GestureDetector gesture={pan}>
        <View style={styles.root} collapsable={false}>
          <GiftScene v={v} width={width} height={height} index={index} reduced={reduced} />
          <View pointerEvents="none" accessible accessibilityRole="adjustable"
            accessibilityLabel={`${gift.name}, ${gift.headline} ${gift.line} ${gift.cap}. Gift ${index + 1} of ${GIFTS.length}`}
            accessibilityHint="Swipe up or down to change gift. Double tap to activate it."
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }]}
            onAccessibilityAction={(e) => {
              const name = e.nativeEvent.actionName;
              if (name === 'activate') ui(() => { 'worklet'; input.activate(); });
              else ui(() => { 'worklet'; input.step(name === 'increment' ? 1 : -1); });
            }}
            accessibilityElementsHidden={!choosing}
            style={[styles.cardA11y, { left: (width - LAYOUT.cardW) / 2, top: LAYOUT.cardY - LAYOUT.cardH / 2 }]} />
        </View>
      </GestureDetector>

      <Animated.View pointerEvents="none" style={[styles.header, { top: LAYOUT.headerTop }, topStyle]}>
        <Svg width={20} height={20} viewBox="0 0 20 20">
          <Path d="M12.5 4 L6.5 10 L12.5 16" stroke={COLORS.ink} strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </Svg>
        <Text style={styles.headerText}>T&Cs</Text>
      </Animated.View>
      <Animated.View pointerEvents="none" style={[styles.centre, { top: LAYOUT.giftY - LAYOUT.giftSize / 2, left: LAYOUT.giftDx, right: -LAYOUT.giftDx }, topStyle]}>
        <GiftIcon />
      </Animated.View>
      <View pointerEvents="none" style={[styles.centre, { top: LAYOUT.titleY - LAYOUT.titleLine }]}>
        <Animated.Text style={[styles.title, chooseStyle]} accessibilityRole="header">Choose your{'\n'}welcome gift</Animated.Text>
        <Animated.Text style={[styles.title, styles.titleYellow, styles.overlay, armedStyle]} accessibilityLiveRegion="polite">
          Release to{'\n'}activate offer!
        </Animated.Text>
      </View>
      <Animated.View pointerEvents="none" style={[styles.centre, { top: LAYOUT.cardY - 70 }, activeStyle]}>
        <GiftIcon size={32} />
        <Text style={[styles.title, styles.titleYellow, { marginTop: 12 }]} accessibilityLiveRegion="polite">
          Activating the{'\n'}offer for you…
        </Text>
      </Animated.View>
      <Animated.Text pointerEvents="none" style={[styles.hint, { top: LAYOUT.hintY - 9 }, hintStyle]}>Drag down to activate offer</Animated.Text>

      {offer && (
        <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
          <Animated.View style={[styles.header, { top: LAYOUT.offerHeaderTop }, detail0]}>
            <PressScale onPress={() => ui(() => { 'worklet'; input.close(); })} hitSlop={12} accessibilityRole="button" accessibilityLabel="Done">
              <Svg width={20} height={20} viewBox="0 0 20 20">
                <Path d="M5 5 L15 15 M15 5 L5 15" stroke={COLORS.ink} strokeWidth={2} strokeLinecap="round" />
              </Svg>
            </PressScale>
            <Text style={styles.headerText}>Help</Text>
          </Animated.View>
          <View style={[styles.details, { top: LAYOUT.howTop }]}>
            <Animated.Text style={[styles.howTitle, detail1]} accessibilityRole="header">How to avail cashback?</Animated.Text>
            <Animated.View style={[styles.stepRow, detail2]}>
              <Text style={styles.stepNum}>1</Text>
              <Text style={styles.stepText}>{gift.use}</Text>
            </Animated.View>
          </View>
          <Animated.View style={[styles.buttonWrap, { top: LAYOUT.buttonTop, left: (width - LAYOUT.buttonW) / 2, width: LAYOUT.buttonW }, detail3]}>
            <PressScale style={styles.button} accessibilityRole="button" accessibilityLabel={`Order from ${gift.name}`}
              onPress={() => (onShop ? onShop(gift.id) : ui(() => { 'worklet'; input.close(); }))}>
              <Text style={styles.buttonText}>Order from {gift.name}</Text>
            </PressScale>
          </Animated.View>
        </View>
      )}
    </GestureHandlerRootView>
  );
}

// Press feedback on the screen's buttons: scale 0.97 on press-in (100 ms), back on release (160 ms), strong ease-out.
function PressScale({ children, style, outer, ...rest }: PressableProps & { style?: StyleProp<ViewStyle>; outer?: StyleProp<ViewStyle> }) {
  return (
    <Pressable {...rest} style={outer}>
      {({ pressed }) => (
        <Animated.View style={[style, {
          transform: [{ scale: pressed ? BUTTON_PRESS.scale : 1 }],
          transitionProperty: 'transform',
          transitionDuration: pressed ? BUTTON_PRESS.inMs : BUTTON_PRESS.outMs,
          transitionTimingFunction: cubicBezier(0.23, 1, 0.32, 1),
        }]}>
          {children as ReactNode}
        </Animated.View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLORS.stageEdge },
  cardA11y: { position: 'absolute', width: LAYOUT.cardW, height: LAYOUT.cardH },
  header: { position: 'absolute', left: LAYOUT.headerLeft, right: LAYOUT.headerRight, height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headerText: { color: COLORS.ink, fontSize: 14, fontFamily: FONT, fontWeight: '700' },
  centre: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  title: { color: COLORS.ink, fontSize: LAYOUT.titleSize, lineHeight: LAYOUT.titleLine, fontFamily: FONT, fontWeight: '700', textAlign: 'center', letterSpacing: 0 },
  titleYellow: { color: COLORS.yellow },
  overlay: { position: 'absolute', top: 0 },
  hint: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: COLORS.hint, fontSize: 17.8, fontFamily: FONT, fontWeight: '600' },
  details: { position: 'absolute', left: SPACE.gutter, right: SPACE.xxl, gap: SPACE.xl },
  howTitle: { color: COLORS.yellow, fontSize: 17.3, fontFamily: FONT, fontWeight: '700' },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: SPACE.md },
  stepNum: { width: 34, color: COLORS.ink, fontSize: 40, fontWeight: '200' },
  stepText: { maxWidth: 250, color: COLORS.ink, fontSize: 18.4, lineHeight: 24, fontFamily: FONT, fontWeight: '600' },
  buttonWrap: { position: 'absolute' },
  button: { height: LAYOUT.buttonH, borderRadius: LAYOUT.buttonH / 2, backgroundColor: COLORS.button, alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: COLORS.buttonInk, fontSize: 16, fontFamily: FONT, fontWeight: '600' },
});

