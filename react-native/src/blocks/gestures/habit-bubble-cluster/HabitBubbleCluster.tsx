/** A habit tracker's day as a cluster of neon bubbles: hold one to complete it, and the cluster re-packs. */
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import { GestureDetector, GestureHandlerRootView } from 'react-native-gesture-handler';
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { BubbleCanvas } from './BubbleCanvas';
import { BubbleIcons } from './BubbleIcons';
import { ENTITIES } from './bubble-sim';
import { DETAIL } from './motion';
import { CATEGORY, CLUSTER, COLOR, COPY, LAYOUT, SPACE, TYPE } from './theme';
import { useBubbleCluster } from './use-bubble-cluster';

export type HabitBubbleClusterProps = { autoPlay?: boolean };


export function HabitBubbleCluster({ autoPlay = false }: HabitBubbleClusterProps) {
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [size, setSize] = useState({ width: 0, height: 0 });
  const onLayout = useCallback((e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    setSize((p) => (p.width === width && p.height === height ? p : { width, height }));
  }, []);
  // The rectified reference has the same status-bar height as the simulator, so the measured layout applies as is.
  const shift = 0;
  const originX = size.width / 2 + (CLUSTER.x - 201);
  const originY = CLUSTER.y + shift;
  const ready = size.width > 0;
  const points = {
    back: { x: LAYOUT.gutter + LAYOUT.iconButton / 2, y: insets.top + LAYOUT.headerTop + LAYOUT.control / 2 },
    social: { x: LAYOUT.gutter + LAYOUT.segmentWidth / 4, y: insets.top + LAYOUT.headerTop + LAYOUT.control / 2 },
    personal: { x: LAYOUT.gutter + (LAYOUT.segmentWidth * 3) / 4, y: insets.top + LAYOUT.headerTop + LAYOUT.control / 2 },
  };
  const { sim, finger, gesture, count, doneIds, tab, detail, selectTab, toggle, back } = useBubbleCluster({
    originX, originY, width: size.width, height: size.height, points, ready, autoPlay, reduceMotion,
  });
  const detailIn = useSharedValue(0);
  useEffect(() => {
    // Enter on a strong ease-out; leave faster than it came (Emil: asymmetric, system responses snap).
    detailIn.set(withTiming(detail >= 0 ? 1 : 0, { duration: detail >= 0 ? DETAIL.fadeMs : DETAIL.fadeOutMs, easing: Easing.bezier(...DETAIL.ease), reduceMotion: ReduceMotion.Never }));
  }, [detail, detailIn]);
  const detailStyle = useAnimatedStyle(() => ({ opacity: detailIn.get(), transform: [{ translateY: (1 - detailIn.get()) * 24 }] }));
  const homeStyle = useAnimatedStyle(() => ({ opacity: 1 - detailIn.get() }));
  const personal = tab === 1;
  const open = detail >= 0 ? ENTITIES[detail] : null;
  const habits = ENTITIES.filter((e) => e.set === tab && !e.quiet);

  return <GestureHandlerRootView style={styles.root}>
    <StatusBar style="light" />
    <View style={styles.root} onLayout={onLayout}>
      {ready && <GestureDetector gesture={gesture}>
        <View style={StyleSheet.absoluteFill} accessible={false}>
          <BubbleCanvas sim={sim} finger={finger} originX={originX} originY={originY} width={size.width} height={size.height} personal={personal} />
          <BubbleIcons sim={sim} originX={originX} originY={originY} />
        </View>
      </GestureDetector>}
      <Animated.View style={[StyleSheet.absoluteFill, homeStyle]} pointerEvents={open ? 'none' : 'box-none'}>
      <View style={[styles.header, { top: insets.top + LAYOUT.headerTop }]} pointerEvents="box-none">
        <View style={styles.segment} accessibilityRole="tablist">
          {COPY.tabs.map((label, i) => <Pressable key={label} onPress={() => selectTab(i)} style={[styles.segmentItem, tab === i && styles.segmentOn]}
            accessibilityRole="tab" accessibilityState={{ selected: tab === i }} accessibilityLabel={`${label} habits`}>
            <Text style={[TYPE.segment, { color: tab === i ? COLOR.controlInkSelected : COLOR.inkSoft }]}>{label}</Text>
          </Pressable>)}
        </View>
        <View style={styles.headerRight}>
          <View style={styles.iconButton} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            <Ionicons name="navigate" size={17} color={COLOR.ink} />
          </View>
          <View accessibilityLabel="Profile" accessible accessibilityRole="image">
            <Image source={{ uri: COPY.avatar }} style={styles.avatar} contentFit="cover" />
            <View style={styles.badge}><Ionicons name="star" size={9} color={COLOR.ink} /></View>
          </View>
        </View>
      </View>
      <View style={[styles.dateBlock, { top: personal ? size.height * LAYOUT.personalDateY : LAYOUT.dateY + shift }]} pointerEvents="none">
        <Text style={personal ? TYPE.personalDate : TYPE.date}>{personal ? COPY.personalDate : COPY.date}</Text>
        <Text style={personal ? TYPE.personalNow : TYPE.now}>{COPY.now}</Text>
      </View>
      <View style={[styles.discRow, { top: LAYOUT.discY + shift - LAYOUT.disc / 2 }]} pointerEvents="none"
        accessible accessibilityLabel={`${count.done} of ${count.total} habits done today`}>
        <View style={[styles.disc, personal && { backgroundColor: COLOR.discDim }]} />
        <View style={[styles.dot, personal
          ? { left: size.width * LAYOUT.personalDot.x - LAYOUT.dot / 2, top: size.height * LAYOUT.personalDot.y - (LAYOUT.discY + shift - LAYOUT.disc / 2) - LAYOUT.dot / 2 }
          : { left: size.width / 2 + LAYOUT.dotOffset - LAYOUT.dot / 2 }]} />
      </View>
      <View style={[styles.corner, { left: LAYOUT.cornerInset, top: LAYOUT.cornerY + shift - LAYOUT.corner / 2 }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Ionicons name="ellipsis-horizontal" size={20} color={COLOR.ink} />
      </View>
      <View style={[styles.corner, { right: LAYOUT.cornerInset, top: LAYOUT.cornerY + shift - LAYOUT.corner / 2 }]} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <Ionicons name="musical-notes" size={19} color={COLOR.ink} />
      </View>
      </Animated.View>
      {/* Detail view, as in the source: the tapped bubble has moved up; header, balance and description. */}
      <Animated.View style={[StyleSheet.absoluteFill, detailStyle]} pointerEvents={open ? 'box-none' : 'none'}>
        <View style={[styles.header, { top: insets.top + LAYOUT.headerTop }]}>
          <Pressable onPress={back} style={styles.iconButton} accessibilityRole="button" accessibilityLabel="Back" hitSlop={6}>
            <Ionicons name="chevron-back" size={20} color={COLOR.ink} />
          </Pressable>
          <View>
            <Text style={TYPE.category}>{COPY.category}</Text>
            <Text style={TYPE.categoryTitle} accessibilityRole="header">{open ? CATEGORY[open.id] ?? open.name : ''}</Text>
          </View>
          <View style={styles.iconButton} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            <Ionicons name="share-social-outline" size={18} color={COLOR.ink} />
          </View>
        </View>
        <View style={[styles.balance, { top: size.height * LAYOUT.detailBalanceY }]}>
          <Text style={TYPE.balance}>{COPY.balance}</Text>
          <View style={styles.good}><Text style={TYPE.good}>{COPY.good}</Text></View>
        </View>
        <View style={[styles.card, { top: size.height * LAYOUT.detailCardY }]}>
          <Text style={TYPE.sectionTitle}>{COPY.description}</Text>
          <View style={styles.hairline} />
          {COPY.body.map((b) => <Text key={b} style={TYPE.body}>{b}</Text>)}
        </View>
      </Animated.View>
      {/* The accessible path: one button per habit, completing or reopening it without a hold. */}
      <View style={styles.a11y} accessibilityLabel="Habits">
        {habits.map((h) => {
          const done = doneIds.includes(h.id);
          return <Pressable key={h.id} onPress={() => toggle(h.id)} accessibilityRole="button" accessibilityLabel={h.name}
            accessibilityState={{ checked: done }} accessibilityHint={done ? 'Double tap to reopen' : 'Double tap to complete'} style={styles.a11yItem} />;
        })}
      </View>
    </View>
  </GestureHandlerRootView>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: COLOR.bg },
  header: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter - 3, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  segment: { width: LAYOUT.segmentWidth, height: LAYOUT.control, borderRadius: LAYOUT.control / 2, backgroundColor: COLOR.control, flexDirection: 'row', padding: SPACE.xs },
  segmentItem: { flex: 1, borderRadius: (LAYOUT.control - SPACE.m) / 2, alignItems: 'center', justifyContent: 'center' },
  segmentOn: { backgroundColor: COLOR.controlSelected },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: SPACE.x2 },
  iconButton: { width: LAYOUT.iconButton, height: LAYOUT.iconButton, borderRadius: LAYOUT.iconButton / 2, backgroundColor: COLOR.control, alignItems: 'center', justifyContent: 'center' },
  avatar: { width: LAYOUT.iconButton, height: LAYOUT.iconButton, borderRadius: LAYOUT.iconButton / 2, alignItems: 'center', justifyContent: 'center' },
  badge: { position: 'absolute', right: -SPACE.xs, bottom: -SPACE.xs, width: 18, height: 18, borderRadius: 9, backgroundColor: COLOR.badge, borderWidth: SPACE.hair, borderColor: COLOR.bg, alignItems: 'center', justifyContent: 'center' },
  dateBlock: { position: 'absolute', left: 0, right: 0, alignItems: 'center', gap: SPACE.s },
  balance: { position: 'absolute', left: LAYOUT.cornerInset, right: LAYOUT.cornerInset, height: LAYOUT.rowHeight, borderRadius: 14, backgroundColor: COLOR.card, paddingHorizontal: SPACE.xl, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  good: { paddingHorizontal: SPACE.l, height: 26, borderRadius: 13, backgroundColor: COLOR.good, justifyContent: 'center' },
  hairline: { height: StyleSheet.hairlineWidth, backgroundColor: COLOR.hairline, marginTop: -SPACE.s },
  card: { position: 'absolute', left: LAYOUT.cornerInset, right: LAYOUT.cornerInset, bottom: 0, borderTopLeftRadius: 18, borderTopRightRadius: 18, backgroundColor: COLOR.card, padding: SPACE.x3, paddingTop: SPACE.cardTop, gap: SPACE.x3 },
  discRow: { position: 'absolute', left: 0, right: 0, height: LAYOUT.disc, alignItems: 'center' },
  disc: { width: LAYOUT.disc, height: LAYOUT.disc, borderRadius: LAYOUT.disc / 2, backgroundColor: COLOR.disc, alignItems: 'center', justifyContent: 'center' },
  dot: { position: 'absolute', top: (LAYOUT.disc - LAYOUT.dot) / 2, width: LAYOUT.dot, height: LAYOUT.dot, borderRadius: LAYOUT.dot / 2, backgroundColor: COLOR.disc },
  corner: { position: 'absolute', width: LAYOUT.corner, height: LAYOUT.corner, borderRadius: LAYOUT.corner / 2, backgroundColor: COLOR.control, alignItems: 'center', justifyContent: 'center' },
  a11y: { position: 'absolute', left: 0, top: 0, width: 1, height: 1, overflow: 'hidden' },
  a11yItem: { width: 1, height: 1 },
});
