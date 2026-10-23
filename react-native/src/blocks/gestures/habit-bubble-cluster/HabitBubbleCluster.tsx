/**
 * A habit tracker's day as a cluster of glass bubbles, sized by how much each habit matters. Hold one: liquid rises
 * inside it while a ring of light closes round it; full, it stays filled under a check, a ring of light leaves it and
 * the cluster re-packs round its smaller size. Drag a bubble to shove its neighbours, tap one to read its name, tap a
 * done one to reopen it. The chrome is real glass (blur) over the canvas.
 */
import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import { useFonts } from 'expo-font';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useState, type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Circle } from 'react-native-svg';

import { BubbleCanvas } from './BubbleCanvas';
import { BubbleIcons } from './BubbleIcons';
import { ENTITIES } from './bubble-sim';
import { CLUSTER, COLOR, COPY, FONT, LAYOUT, SPACE, TYPE } from './theme';
import { useBubbleCluster } from './use-bubble-cluster';

export type HabitBubbleClusterProps = { autoPlay?: boolean };

export function HabitBubbleCluster({ autoPlay = false }: HabitBubbleClusterProps) {
  const [loaded, failed] = useFonts({
    [FONT.regular]: require('./assets/Inter-Regular.ttf'),
    [FONT.medium]: require('./assets/Inter-Medium.ttf'),
    [FONT.semibold]: require('./assets/Inter-SemiBold.ttf'),
  });
  if (!loaded && !failed) return <View style={styles.root} />;
  return <Screen autoPlay={autoPlay} />;
}

/** A glass surface for the chrome: the canvas blurred behind it, a hairline rim. */
function Glass({ style, children }: { style: object; children?: ReactNode }) {
  return (
    <View style={[styles.glass, style]}>
      <BlurView intensity={28} tint="dark" style={StyleSheet.absoluteFill} />
      <View style={[StyleSheet.absoluteFill, styles.glassTint]} />
      {children}
    </View>
  );
}

function Screen({ autoPlay }: { autoPlay: boolean }) {
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((p) => (p.width === width && p.height === height ? p : { width, height }));
  }, []);
  // The cluster was laid out on a 402 pt screen; it stays centred on wider or narrower ones.
  const originX = size.width / 2 + (CLUSTER.x - 201);
  const originY = CLUSTER.y + (insets.top - 62);
  const ready = size.width > 0;
  const headerY = insets.top + SPACE.xs;
  const points = {
    social: { x: LAYOUT.gutter + LAYOUT.segmentWidth / 4, y: headerY + LAYOUT.control / 2 },
    personal: { x: LAYOUT.gutter + (LAYOUT.segmentWidth * 3) / 4, y: headerY + LAYOUT.control / 2 },
  };
  const { sim, finger, label, gesture, count, doneIds, tab, selectTab, toggle } = useBubbleCluster({
    originX, originY, points, ready, autoPlay: autoPlay && !reduceMotion, reduceMotion,
  });
  const habits = ENTITIES.filter((e) => e.set === tab && !e.quiet);
  const bottomY = size.height - Math.max(insets.bottom, LAYOUT.gutter) - SPACE.s - LAYOUT.pill;
  const share = count.total > 0 ? count.done / count.total : 0;
  const ring = 2 * Math.PI * 8;

  return (
    <GestureHandlerRootView style={styles.root}>
      <StatusBar style="light" />
      <View style={styles.root} onLayout={onLayout}>
        {ready && (
          <GestureDetector gesture={gesture}>
            <View style={StyleSheet.absoluteFill} accessible={false}>
              <BubbleCanvas sim={sim} finger={finger} label={label} originX={originX} originY={originY} width={size.width} height={size.height} reduced={reduceMotion} />
              <BubbleIcons sim={sim} originX={originX} originY={originY} />
            </View>
          </GestureDetector>
        )}
        <View style={[styles.header, { top: headerY }]} pointerEvents="box-none">
          <Glass style={styles.segment}>
            <View style={styles.segmentRow} accessibilityRole="tablist">
              {COPY.tabs.map((name, i) => (
                <Pressable key={name} onPress={() => selectTab(i)} style={[styles.segmentItem, tab === i && styles.segmentOn]}
                  accessibilityRole="tab" accessibilityState={{ selected: tab === i }} accessibilityLabel={`${name} habits`}>
                  <Text style={[TYPE.segment, { color: tab === i ? COLOR.selectedInk : COLOR.inkSoft }]}>{name}</Text>
                </Pressable>
              ))}
            </View>
          </Glass>
          <Glass style={styles.round}>
            <Text style={TYPE.monogram} accessibilityLabel="Profile, Ana Morales">{COPY.monogram}</Text>
          </Glass>
        </View>
        <View style={[styles.dateBlock, { top: originY + LAYOUT.dateDrop }]} pointerEvents="none">
          <Text style={TYPE.date} accessibilityRole="header">{COPY.date}</Text>
          <Text style={TYPE.now}>{COPY.now}</Text>
        </View>
        <View style={[styles.bottom, { top: bottomY }]} pointerEvents="box-none">
          <Glass style={styles.round}>
            <Ionicons name="calendar-clear-outline" size={19} color={COLOR.ink} accessibilityLabel="Calendar" />
          </Glass>
          <Glass style={styles.pill}>
            <View style={styles.pillRow} accessible accessibilityLabel={`${count.done} of ${count.total} habits done today`}>
              <Svg width={20} height={20} viewBox="0 0 20 20">
                <Circle cx={10} cy={10} r={8} stroke={COLOR.hairline} strokeWidth={2.5} fill="none" />
                <Circle cx={10} cy={10} r={8} stroke={COLOR.ink} strokeWidth={2.5} fill="none" strokeLinecap="round"
                  strokeDasharray={`${ring * share} ${ring}`} transform="rotate(-90 10 10)" />
              </Svg>
              <Text style={TYPE.count}>{count.done} of {count.total}</Text>
              <Text style={TYPE.countMute}>done today</Text>
            </View>
          </Glass>
          <Glass style={styles.round}>
            <Ionicons name="add" size={22} color={COLOR.ink} accessibilityLabel="New habit" />
          </Glass>
        </View>
        {/* The accessible path: one button per habit, completing or reopening it without a hold. */}
        <View style={styles.a11y} accessibilityLabel="Habits">
          {habits.map((h) => {
            const done = doneIds.includes(h.id);
            return (
              <Pressable key={h.id} onPress={() => toggle(h.id)} accessibilityRole="button" accessibilityLabel={h.name}
                accessibilityState={{ checked: done }} accessibilityHint={done ? 'Double tap to reopen' : 'Double tap to mark it done'} style={styles.a11yItem} />
            );
          })}
        </View>
      </View>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.bg },
  glass: { overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth, borderColor: 'rgba(255,255,255,0.16)' },
  glassTint: { backgroundColor: 'rgba(255,255,255,0.04)' },
  header: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, height: LAYOUT.control, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  segment: { width: LAYOUT.segmentWidth, height: LAYOUT.control, borderRadius: LAYOUT.control / 2 },
  segmentRow: { flex: 1, flexDirection: 'row', padding: SPACE.xs },
  segmentItem: { flex: 1, borderRadius: (LAYOUT.control - 2 * SPACE.xs) / 2, alignItems: 'center', justifyContent: 'center' },
  segmentOn: { backgroundColor: COLOR.selected },
  round: { width: LAYOUT.control, height: LAYOUT.control, borderRadius: LAYOUT.control / 2, alignItems: 'center', justifyContent: 'center' },
  dateBlock: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: SPACE.xs },
  bottom: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, height: LAYOUT.pill, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  pill: { height: LAYOUT.pill, borderRadius: LAYOUT.pill / 2, paddingHorizontal: SPACE.l, justifyContent: 'center' },
  pillRow: { flexDirection: 'row', alignItems: 'center', gap: SPACE.s },
  a11y: { position: 'absolute', left: 0, top: 0, width: 1, height: 1, overflow: 'hidden' },
  a11yItem: { width: 1, height: 1 },
});
