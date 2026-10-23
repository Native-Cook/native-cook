/**
 * Gift Slot Drop: a wallet's welcome gift. Swipe through four gift cards, pull one down into the slot in the glass
 * shelf; it goes in, the slot reads it, and the activated gift rises back out and turns over to its code. The screen is
 * one Skia picture drawn from the slot's shared values (scene.ts) over a page made once at mount (backdrop.ts); views
 * on top only take touches and speak to VoiceOver.
 */
import { Canvas, Picture, createPicture, useTypeface } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { useCallback, useMemo, useState } from 'react';
import { PixelRatio, Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnUI } from 'react-native-worklets';

import { makeBackdrop } from './backdrop';
import { drawScene, makeScene, type Faces, type SceneCtx } from './scene';
import { COLOR, GIFTS, LAYOUT, START_INDEX, makeLayout, type Layout } from './theme';
import { PHASE, useGiftSlot, type Haptic } from './use-gift-slot';

export type GiftSlotDropProps = { autoPlay?: boolean; onShop?: (giftId: string) => void };

const HAPTIC: Record<Haptic, () => void> = {
  select: () => { void Haptics.selectionAsync().catch(() => {}); },
  armed: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); },
  insert: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {}); },
  success: () => { void Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {}); },
  light: () => { void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); },
};

export function GiftSlotDrop({ autoPlay = false, onShop }: GiftSlotDropProps) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const regular = useTypeface(require('./assets/Inter-Regular.ttf'));
  const medium = useTypeface(require('./assets/Inter-Medium.ttf'));
  const semibold = useTypeface(require('./assets/Inter-SemiBold.ttf'));
  const mono = useTypeface(require('./assets/JetBrainsMono-Medium.ttf'));
  if (!regular || !medium || !semibold || !mono) return <View style={styles.root} />;
  const lay = makeLayout(width, height, insets.top, insets.bottom);
  // Reduce Motion: no demo; a finger still swipes and drops a gift in.
  return (
    <Stage key={`${width}x${height}-${insets.top}-${reduced}`} lay={lay} faces={{ regular, medium, semibold, mono }}
      reduced={reduced} autoPlay={autoPlay && !reduced} onShop={onShop} />
  );
}

type StageProps = { lay: Layout; faces: Faces; reduced: boolean; autoPlay: boolean; onShop?: (id: string) => void };

function Stage({ lay, faces, reduced, autoPlay, onShop }: StageProps) {
  const [phase, setPhase] = useState<number>(PHASE.choosing);
  const [index, setIndex] = useState(START_INDEX);
  const onHaptic = useCallback((k: Haptic) => HAPTIC[k](), []);
  const onArmed = useCallback(() => {}, []);
  const { values: v, input } = useGiftSlot(lay, autoPlay, reduced, { onPhase: setPhase, onArmed, onIndex: setIndex, onHaptic });
  const gift = GIFTS[index];

  const ctx: SceneCtx | null = useMemo(() => {
    const back = makeBackdrop(lay, PixelRatio.get());
    return back ? makeScene(lay, faces, back) : null;
  }, [lay, faces]);
  const picture = useDerivedValue(() => {
    const f = {
      now: v.clock.get(), offset: v.offset.get(), pullX: v.pullX.get(), pullY: v.pullY.get(), lift: v.lift.get(),
      phase: v.phase.get(), armed: v.armed.get(), titleAt: v.titleAt.get(), titleWas: v.titleWas.get(),
      releasedAt: v.releasedAt.get(), releasedFrom: v.releasedFrom.get(), closeAt: v.closeAt.get(),
      selected: v.selected.get(), ghost: v.ghost.get(), reduced,
    };
    return createPicture((canvas) => {
      if (ctx) drawScene(canvas, ctx, f);
    });
  });

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
  const offer = phase === PHASE.offer;
  const bw = lay.width - 2 * LAYOUT.edge;

  return (
    <GestureHandlerRootView style={styles.root}>
      <GestureDetector gesture={pan}>
        <View style={styles.root} collapsable={false}>
          <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
            <Picture picture={picture} />
          </Canvas>
          <View accessible accessibilityRole="header" accessibilityLabel={offer ? 'Your gift is ready' : 'Choose your welcome gift'}
            style={[styles.a11y, { top: lay.top + 76, left: 40, right: 40, height: 76 }]} />
          <View pointerEvents="none" accessible accessibilityRole="adjustable"
            accessibilityLabel={`${gift.shop}, ${gift.kind}: ${gift.offer} ${gift.line}, ${gift.cap}. Gift ${index + 1} of ${GIFTS.length}`}
            accessibilityHint="Swipe up or down to change gift. Double tap to activate it."
            accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }, { name: 'activate' }]}
            onAccessibilityAction={(e) => {
              const name = e.nativeEvent.actionName;
              if (name === 'activate') ui(() => { 'worklet'; input.activate(); });
              else ui(() => { 'worklet'; input.step(name === 'increment' ? 1 : -1); });
            }}
            accessibilityElementsHidden={!choosing}
            style={[styles.a11y, { left: lay.cx - LAYOUT.cardW / 2, top: lay.cardY - LAYOUT.cardH / 2, width: LAYOUT.cardW, height: LAYOUT.cardH }]} />
        </View>
      </GestureDetector>
      <Pressable style={[styles.a11y, { top: lay.top, left: LAYOUT.edge, width: 44, height: 44 }]} hitSlop={8}
        accessibilityRole="button" accessibilityLabel={offer ? 'Done' : 'Back'}
        onPress={() => { if (offer) ui(() => { 'worklet'; input.close(); }); }} />
      {offer && (
        <Pressable style={[styles.a11y, { top: lay.buttonY, left: LAYOUT.edge, width: bw, height: LAYOUT.buttonH }]}
          accessibilityRole="button" accessibilityLabel={gift.action}
          accessibilityHint={`Your code is ${gift.code.split('').join(' ')}`}
          onPress={() => (onShop ? onShop(gift.id) : ui(() => { 'worklet'; input.close(); }))} />
      )}
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.page },
  a11y: { position: 'absolute' },
});
