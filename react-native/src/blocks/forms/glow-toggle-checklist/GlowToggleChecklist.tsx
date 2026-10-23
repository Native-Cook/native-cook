import { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { type SharedValue, useAnimatedStyle, useSharedValue } from 'react-native-reanimated';

import { GlowCanvas } from './GlowCanvas';
import type { ChecklistTouch } from './checklist-sim';
import { checklistGeometry, checklistScale, D, LABELS, PAGE, rowCentreY } from './checklist-theme';
import { useChecklistMotion } from './use-checklist-motion';

type TargetProps = {
  control: number; width: number; height: number; k: number;
  open: SharedValue<number>; present: SharedValue<number>; touchClock: SharedValue<number>;
  touches: SharedValue<ChecklistTouch[]>; checked: boolean; expanded: boolean;
  activate: (control: number) => void;
};

function TouchTarget({ control, width, height, k, open, present, touchClock, touches,
  checked, expanded, activate }: TargetProps) {
  const finger = useSharedValue({ id: -1, x: 0, lastX: 0, dx: 0, velocity: 0, time: 0, cancelled: false });
  const isCap = control === -1;
  const targetWidth = (isCap ? D.cap.w : D.cap.w - 2 * D.slab.inset) * k;
  const targetHeight = (isCap ? D.cap.h : D.rowH) * k;
  // Manual activation handles a stationary finger and press-in on the very first touch.
  // No pan-failure fallback can accidentally commit a cancelled gesture.
  const gesture = useMemo(() => Gesture.Manual()
    .onTouchesDown((event, manager) => {
      'worklet';
      if (finger.get().id !== -1 || event.numberOfTouches !== 1) return;
      // Hit testing stays on; a control that is not on screen simply ignores the finger.
      if (present.get() < 1 || (!isCap && open.get() < 0.98)) { manager.fail(); return; }
      const p = event.changedTouches[0];
      if (!p) return;
      finger.set({ id: p.id, x: p.absoluteX, lastX: p.absoluteX, dx: 0, velocity: 0,
        time: touchClock.get(), cancelled: false });
      touches.set([...touches.get(), { control, down: true, dx: 0, velocity: 0, k }]);
      manager.activate();
    })
    .onTouchesMove((event) => {
      'worklet';
      const f = finger.get();
      for (let i = 0; i < event.changedTouches.length; i++) {
        const p = event.changedTouches[i];
        if (p.id !== f.id || f.cancelled) continue;
        const now = touchClock.get();
        const dx = p.absoluteX - f.x;
        const velocity = (p.absoluteX - f.lastX) / Math.max(1 / 240, now - f.time);
        const cancel = isCap && (p.x < -16 || p.x > targetWidth + 16 || p.y < -16 || p.y > targetHeight + 16);
        finger.set({ ...f, dx, lastX: p.absoluteX, time: now, velocity, cancelled: cancel });
        touches.set([...touches.get(), { control, down: true, dx, velocity, k, cancel }]);
      }
    })
    .onTouchesUp((event, manager) => {
      'worklet';
      const f = finger.get();
      for (let i = 0; i < event.changedTouches.length; i++) {
        const p = event.changedTouches[i];
        if (p.id !== f.id) continue;
        const velocity = touchClock.get() - f.time > 0.08 ? 0 : f.velocity;
        const cancel = f.cancelled || (isCap && (p.x < -16 || p.x > targetWidth + 16 || p.y < -16 || p.y > targetHeight + 16));
        touches.set([...touches.get(), { control, down: false, dx: p.absoluteX - f.x, velocity, k, cancel }]);
        finger.set({ ...f, id: -1 });
        manager.end();
      }
    })
    .onTouchesCancelled((_event, manager) => {
      'worklet';
      const f = finger.get();
      if (f.id !== -1) {
        touches.set([...touches.get(), { control, down: false, dx: f.dx, velocity: 0, k, cancel: true }]);
        finger.set({ ...f, id: -1 });
      }
      manager.fail();
    })
    .onFinalize((_event, success) => {
      'worklet';
      const f = finger.get();
      if (!success && f.id !== -1) {
        touches.set([...touches.get(), { control, down: false, dx: f.dx, velocity: 0, k, cancel: true }]);
        finger.set({ ...f, id: -1 });
      }
    }), [control, finger, isCap, k, open, present, targetHeight, targetWidth, touchClock, touches]);

  const position = useAnimatedStyle(() => {
    'worklet';
    const g = checklistGeometry(open.get(), width, height, k);
    return {
      transform: [{ translateX: isCap ? g.cx - g.capHalfW : g.slabLeft },
        { translateY: (isCap ? g.capCy : rowCentreY(control, g.slabBottom, k)) - targetHeight / 2 }],
    };
  });
  return <GestureDetector gesture={gesture}>
    <Animated.View collapsable={false} pointerEvents={isCap || expanded ? 'auto' : 'none'}
      style={[styles.target, { width: targetWidth, height: targetHeight, zIndex: isCap ? 2 : 1 }, position]}
      accessible accessibilityRole={isCap ? 'button' : 'switch'}
      accessibilityLabel={isCap ? 'Success checklist' : LABELS[control][0] + LABELS[control].slice(1).toLowerCase()}
      accessibilityState={isCap ? { expanded } : { checked }}
      accessibilityHint={isCap ? expanded ? 'Closes the checklist' : 'Opens the checklist' : undefined}
      accessibilityElementsHidden={!isCap && !expanded}
      importantForAccessibility={!isCap && !expanded ? 'no-hide-descendants' : 'auto'}
      onAccessibilityTap={() => activate(control)} accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={(event) => { if (event.nativeEvent.actionName === 'activate') activate(control); }} />
  </GestureDetector>;
}

export function GlowToggleChecklist({ autoPlay = false }: { autoPlay?: boolean }) {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const { motion, opacity, touches, touchClock, onReady, canvasReady, accessibility, activate } = useChecklistMotion(autoPlay);
  const { open, present } = motion;
  const k = checklistScale(size.width, size.height);
  const fadeStyle = useAnimatedStyle(() => ({ opacity: opacity.get() }));
  return <GestureHandlerRootView style={styles.root}>
    <View style={styles.root} onLayout={(event) => {
      const { width, height } = event.nativeEvent.layout;
      setSize({ width, height });
    }}>
      {size.width > 0 && size.height > 0 && <Animated.View style={[StyleSheet.absoluteFill, fadeStyle]}>
        <GlowCanvas motion={motion} width={size.width} height={size.height} k={k} onReady={onReady} />
        {canvasReady && [-1, 0, 1, 2, 3].map((control) => <TouchTarget key={control} control={control}
          width={size.width} height={size.height} k={k} open={open} present={present}
          touches={touches} touchClock={touchClock} checked={accessibility.checked[control] ?? false}
          expanded={accessibility.expanded} activate={activate} />)}
      </Animated.View>}
    </View>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: PAGE },
  target: { position: 'absolute', left: 0, top: 0, backgroundColor: 'transparent' },
});
