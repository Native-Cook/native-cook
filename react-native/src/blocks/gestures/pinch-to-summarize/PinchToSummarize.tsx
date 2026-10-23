/**
 * Pinch an article to distill it: the sentences the summary is made from light up under the fingers, then fly
 * into the summary they became. Pinch out, close, or tap a point to read the original again.
 */
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useState } from 'react';
import { StyleSheet, View, type GestureResponderEvent, type LayoutChangeEvent } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ArticlePage } from './ArticlePage';
import { ChipLayer } from './Chips';
import { DepthLabel } from './DepthLabel';
import type { KeyGeom, Stage } from './geometry';
import { SummaryField, SummaryLight } from './SummaryCanvas';
import { layoutLevel, summaryTop, type Level, type SummaryLines } from './summary-layout';
import { SummaryLayer, SummaryMeasure } from './SummaryLayer';
import { COLOR } from './theme';
import { useSummaryMotion } from './use-summary-motion';

export type PinchToSummarizeProps = { autoPlay?: boolean };

const NO_KEYS: KeyGeom[] = [];

export function PinchToSummarize({ autoPlay = false }: PinchToSummarizeProps) {
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [lines, setLines] = useState<SummaryLines | null>(null);
  const [keys, setKeys] = useState<KeyGeom[] | null>(null);
  const [contentHeight, setContentHeight] = useState(0);
  const [level, setLevel] = useState<Level>(0);
  const [scrollLocked, setScrollLocked] = useState(false);
  const stage = useMemo<Stage>(() => ({ width: size.width, height: size.height, insetTop: insets.top }), [insets.top, size.height, size.width]);
  const layouts = useMemo(() => ([0, 1, 2] as Level[]).map((l) => layoutLevel(lines ?? { leads: [], bodies: [] }, size.width, l)), [lines, size.width]);
  const maxScroll = Math.max(0, contentHeight - size.height);
  const ready = size.width > 0 && lines !== null && keys !== null && contentHeight > 0;
  const { values, gesture, mode, summarize, showArticle, onTouch } = useSummaryMotion({
    // Reduce Motion: no demo; a pinch still summarizes.
    stage, ready, autoPlay: autoPlay && !reduceMotion, reduceMotion, keys: keys ?? NO_KEYS, maxScroll, layouts, lockScroll: setScrollLocked, onLevel: setLevel,
  });
  const native = useMemo(() => Gesture.Native(), []);
  const pinch = useMemo(() => gesture.simultaneousWithExternalGesture(native), [gesture, native]);
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((prev) => (prev.width === width && prev.height === height ? prev : { width, height }));
  }, []);
  const close = useCallback(() => showArticle(-1), [showArticle]);
  // Fingers on the screen, so a press under a pinch neither lights up nor lifts the summary.
  const down = useCallback((e: GestureResponderEvent) => onTouch(true, e.nativeEvent.touches.length), [onTouch]);
  const up = useCallback((e: GestureResponderEvent) => onTouch(false, e.nativeEvent.touches.length), [onTouch]);

  return <GestureHandlerRootView style={styles.root}>
    <StatusBar style={mode === 'reading' ? 'dark' : 'light'} animated />
    <GestureDetector gesture={pinch}>
      <View style={styles.root} onLayout={onLayout} onTouchStart={down} onTouchEnd={up}
        onTouchCancel={up}>
        {size.width > 0 && <>
          <SummaryField stage={stage} values={values} />
          <ArticlePage stage={stage} insetBottom={insets.bottom} values={values} mode={mode} reduceMotion={reduceMotion} scrollLocked={scrollLocked}
            native={native} keys={keys} onKeys={setKeys} onContentHeight={setContentHeight} onSummarize={summarize} onShowArticle={close} />
          <SummaryLayer stage={stage} insetBottom={insets.bottom} values={values} mode={mode} reduceMotion={reduceMotion}
            layout={layouts[level]} onClose={close} onSource={showArticle} />
          <DepthLabel stage={stage} insetBottom={insets.bottom} values={values} />
          {keys && !reduceMotion && <ChipLayer keys={keys} layout={layouts[level]} top={summaryTop()} values={values} />}
          <SummaryLight stage={stage} values={values} />
          {!lines && <SummaryMeasure width={size.width} onLines={setLines} />}
        </>}
      </View>
    </GestureDetector>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: COLOR.page } });
