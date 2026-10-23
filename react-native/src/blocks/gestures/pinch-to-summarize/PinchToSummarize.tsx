/** Pinch an article to collapse it into an AI summary; pinch out (or close) to read the original again. */
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useRef, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent, type ScrollView } from 'react-native';
import { Gesture, GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ArticlePage } from './ArticlePage';
import type { Stage } from './geometry';
import { SummaryField, SummaryLight } from './SummaryCanvas';
import { layoutRows, revealDuration, SummaryLayer, SummaryMeasure, type SummaryLines } from './SummaryLayer';
import { COLOR } from './theme';
import { useSummaryMotion } from './use-summary-motion';

export type PinchToSummarizeProps = { autoPlay?: boolean };

export function PinchToSummarize({ autoPlay = false }: PinchToSummarizeProps) {
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const [lines, setLines] = useState<SummaryLines | null>(null);
  const [scrollLocked, setScrollLocked] = useState(false);
  const scrollRef = useRef<ScrollView>(null);
  const stage = useMemo<Stage>(() => ({ width: size.width, height: size.height, insetTop: insets.top }), [insets.top, size.height, size.width]);
  const rows = useMemo(() => (lines ? layoutRows(lines, size.width) : []), [lines, size.width]);
  const revealTotal = revealDuration(rows.length);
  const ready = size.width > 0 && lines !== null;
  const { values, gesture, mode, summarize, showArticle, onTouch, summaryText } = useSummaryMotion({
    stage, ready, autoPlay, reduceMotion, revealTotal, lockScroll: setScrollLocked,
  });
  const native = useMemo(() => Gesture.Native(), []);
  const pinch = useMemo(() => gesture.simultaneousWithExternalGesture(native), [gesture, native]);
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((prev) => (prev.width === width && prev.height === height ? prev : { width, height }));
  }, []);

  return <GestureHandlerRootView style={styles.root}>
    <StatusBar style={mode === 'reading' ? 'dark' : 'light'} animated />
    <GestureDetector gesture={pinch}>
      <View style={styles.root} onLayout={onLayout} onTouchStart={() => onTouch(true)} onTouchEnd={() => onTouch(false)}
        onTouchCancel={() => onTouch(false)}>
        {size.width > 0 && <>
          <SummaryField stage={stage} values={values} />
          <ArticlePage stage={stage} insetBottom={insets.bottom} values={values} mode={mode} scrollLocked={scrollLocked}
            native={native} scrollRef={scrollRef} onSummarize={summarize} onShowArticle={showArticle} />
          <SummaryLayer stage={stage} insetBottom={insets.bottom} values={values} mode={mode} reduceMotion={reduceMotion}
            rows={rows} revealTotal={revealTotal} onClose={showArticle} summaryText={summaryText} />
          <SummaryLight stage={stage} values={values} />
          {!lines && <SummaryMeasure width={size.width} onLines={setLines} />}
        </>}
      </View>
    </GestureDetector>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({ root: { flex: 1, backgroundColor: COLOR.page } });
