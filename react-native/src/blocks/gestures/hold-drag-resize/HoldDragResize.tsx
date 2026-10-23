/**
 * Make room for deep work: Thursday's plan, at night, in Liquid Glass. Hold a block and drag its edge: the end clicks
 * from quarter hour to quarter hour, the tasks after it are shoved later as one chain and stop hard against the next
 * meeting; drag back and they spring home one after another. The whole screen is one Skia picture drawn from one
 * model (scene.ts, model.ts) over a page made once at mount (backdrop.ts).
 */
import { Canvas, Picture, createPicture, useTypeface } from '@shopify/react-native-skia';
import { useMemo } from 'react';
import { PixelRatio, StyleSheet, View, useWindowDimensions } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useDerivedValue, useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { makeBackdrop } from './backdrop';
import { DAY_DATE, DAY_TITLE, KIND, PLAN, clock } from './day';
import { drawScene, makeScene, type Faces, type SceneCtx } from './scene';
import { COLOR, makeLayout, yOf, type Layout } from './theme';
import { useDayResize } from './use-day-resize';

export type HoldDragResizeProps = { autoPlay?: boolean };

export function HoldDragResize({ autoPlay = false }: HoldDragResizeProps) {
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const reduced = useReducedMotion();
  const regular = useTypeface(require('./assets/Inter-Regular.ttf'));
  const medium = useTypeface(require('./assets/Inter-Medium.ttf'));
  const semibold = useTypeface(require('./assets/Inter-SemiBold.ttf'));
  const mono = useTypeface(require('./assets/JetBrainsMono-Medium.ttf'));
  const monoBold = useTypeface(require('./assets/JetBrainsMono-Bold.ttf'));
  if (!regular || !medium || !semibold || !mono || !monoBold) return <View style={styles.root} />;
  const lay = makeLayout(width, height, insets.top, insets.bottom);
  // The model's geometry and Reduce Motion are fixed per mount.
  return (
    <Screen key={`${width}x${height}-${insets.top}-${reduced}`} lay={lay} faces={{ regular, medium, semibold, mono, monoBold }}
      reduced={reduced} autoPlay={autoPlay} />
  );
}

function Screen({ lay, faces, reduced, autoPlay }: { lay: Layout; faces: Faces; reduced: boolean; autoPlay: boolean }) {
  // Reduce Motion: the plan at rest, no demo; a finger still resizes.
  const { model, gesture, ui, adjust, touchStart, touchEnd } = useDayResize({ lay, autoPlay: autoPlay && !reduced, reduced });
  const ctx: SceneCtx | null = useMemo(() => {
    const back = makeBackdrop(lay, PixelRatio.get());
    return back ? makeScene(lay, faces, back) : null;
  }, [lay, faces]);
  const picture = useDerivedValue(() => {
    const m = model.get();
    return createPicture((canvas) => {
      if (ctx) drawScene(canvas, ctx, m);
    });
  });

  return (
    <GestureHandlerRootView style={styles.root}>
      <View style={styles.root} onTouchStart={touchStart} onTouchEnd={touchEnd} onTouchCancel={touchEnd}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
        <GestureDetector gesture={gesture}>
          <View style={StyleSheet.absoluteFill} collapsable={false}>
            <View accessible accessibilityRole="header" accessibilityLabel={`${DAY_TITLE}, ${DAY_DATE}`}
              style={[styles.a11y, { top: lay.top, left: 16, width: 200, height: 68 }]} />
            {PLAN.map((item, i) => {
              const s = ui.starts[i];
              const e = ui.ends[i];
              const top = yOf(lay, s);
              const movable = item.kind !== KIND.meeting;
              return (
                <View
                  key={item.id}
                  accessible
                  accessibilityRole={movable ? 'adjustable' : 'text'}
                  accessibilityLabel={`${item.title}, ${clock(s)} to ${clock(e)}${item.note && item.kind === KIND.meeting ? `, ${item.note}` : ''}`}
                  accessibilityHint={movable ? 'Hold and drag its edge to change how long it takes. Swipe up or down to move its end by a quarter hour.' : 'A meeting; it does not move.'}
                  accessibilityActions={movable ? [{ name: 'increment' }, { name: 'decrement' }] : undefined}
                  onAccessibilityAction={(ev) => adjust(i, ev.nativeEvent.actionName === 'increment' ? 1 : -1)}
                  style={[styles.a11y, { top, left: lay.cardX, width: lay.cardW, height: yOf(lay, e) - top }]}
                />
              );
            })}
          </View>
        </GestureDetector>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.page },
  a11y: { position: 'absolute' },
});
