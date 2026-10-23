/** The article as a real, scrollable, accessible page. It is transformed (never re-laid out) into the card. */
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import type { RefObject } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { GestureDetector, type NativeGesture } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';

import { breathing, clamp01, pageFrame, type Stage } from './geometry';
import { TIMING } from './motion';
import { COLOR, COPY, LAYOUT, TYPE } from './theme';
import type { Mode, SummaryValues } from './use-summary-motion';

const hero = require('./assets/hero.jpg');

type Props = {
  stage: Stage;
  insetBottom: number;
  values: SummaryValues;
  mode: Mode;
  scrollLocked: boolean;
  native: NativeGesture;
  scrollRef: RefObject<ScrollView | null>;
  onSummarize: () => void;
  onShowArticle: () => void;
};

/** An invented masthead mark at the weight of the source's: two heavy interlocking rings. */
function MeridianMark() {
  return <Svg width={34} height={22} viewBox="0 0 34 22">
    <Circle cx={11} cy={11} r={8.5} stroke={COLOR.ink} strokeWidth={3.4} fill="none" />
    <Circle cx={23} cy={11} r={8.5} stroke={COLOR.ink} strokeWidth={3.4} fill="none" />
    <Path d="M17 4.6a8.5 8.5 0 0 1 0 12.8" stroke={COLOR.page} strokeWidth={1.6} fill="none" />
  </Svg>;
}

export function ArticlePage({ stage, insetBottom, values, mode, scrollLocked, native, scrollRef, onSummarize, onShowArticle }: Props) {
  const { p, lift, driftX, driftY, pageAlpha } = values;
  const panelWidth = stage.width - LAYOUT.panelInset * 2;
  const clip = useAnimatedStyle(() => {
    const f = pageFrame(stage, p.get(), lift.get(), driftX.get(), driftY.get(), breathing(values.think.get(), values.clock.get(), TIMING.breathe, TIMING.breathePeriod));
    return {
      height: f.clipHeight,
      borderRadius: f.radius / f.scale,
      opacity: pageAlpha.get(),
      transform: [{ translateX: f.x }, { translateY: f.y }, { scale: f.scale }],
    };
  });
  const inner = useAnimatedStyle(() => ({ transform: [{ translateY: -pageFrame(stage, p.get(), 0, 0, 0).trim }] }));
  const chrome = useAnimatedStyle(() => ({ opacity: 1 - clamp01(p.get() / TIMING.toolbarFadeSpan) }));
  const reading = mode === 'reading';

  return <Animated.View style={[styles.clip, { width: stage.width }, clip]}
    accessibilityElementsHidden={!reading} importantForAccessibility={reading ? 'auto' : 'no-hide-descendants'}
    accessibilityActions={[{ name: 'summarize', label: 'Summarize article' }]}
    onAccessibilityAction={(e) => { if (e.nativeEvent.actionName === 'summarize') onSummarize(); }}>
    <Animated.View style={[styles.inner, { width: stage.width, height: stage.height }, inner]}>
      <GestureDetector gesture={native}>
        <ScrollView ref={scrollRef} scrollEnabled={!scrollLocked} showsVerticalScrollIndicator={false}
          contentContainerStyle={{ paddingTop: stage.insetTop, paddingBottom: LAYOUT.toolbarHeight + insetBottom + 32 }}>
          <View style={styles.masthead}>
            <View style={styles.brand} accessibilityRole="header" accessibilityLabel="Meridian">
              <MeridianMark />
              <Text style={TYPE.wordmark}>{COPY.wordmark}</Text>
            </View>
            <View style={styles.avatar} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
              <Ionicons name="person" size={14} color={COLOR.inkSoft} />
            </View>
          </View>
          <View style={[styles.panel, { width: panelWidth }]}>
            <View style={styles.pill}><Text style={TYPE.pill}>{COPY.kicker}</Text></View>
            <Text style={[TYPE.headline, styles.headline]} accessibilityRole="header">{COPY.headline}</Text>
            <Text style={[TYPE.dek, styles.dek]}>{COPY.dek}</Text>
            <Image source={hero} style={{ width: panelWidth, height: panelWidth * LAYOUT.photoAspect, marginTop: LAYOUT.photoGap }}
              contentFit="cover" accessibilityLabel="A woman in the Halo One visor waves at the camera" />
            <View style={styles.dot} />
            <Text style={TYPE.byline}>{COPY.byline}</Text>
            <Text style={[TYPE.byline, styles.date]}>{COPY.date}</Text>
          </View>
          <View style={styles.body}>
            <View style={styles.sectionRow}>
              <Text style={TYPE.section}>{COPY.section}</Text>
              <View style={styles.sectionRule} />
            </View>
            {COPY.body.map((paragraph, i) => <Text key={i} style={[TYPE.body, styles.paragraph]}>
              {i === 0 ? <Text style={styles.leadIn}>{paragraph.split(' ').slice(0, 3).join(' ').toUpperCase()}</Text> : null}
              {i === 0 ? ` ${paragraph.split(' ').slice(3).join(' ')}` : paragraph}
            </Text>)}
          </View>
        </ScrollView>
      </GestureDetector>
      <Animated.View style={[styles.toolbarFade, { height: LAYOUT.toolbarHeight + insetBottom + LAYOUT.toolbarFade, width: stage.width }, chrome]} pointerEvents="none">
        <LinearGradient colors={[COLOR.pageClear, COLOR.page]} locations={[0, 0.45]} style={StyleSheet.absoluteFill} />
      </Animated.View>
      <Animated.View style={[styles.toolbar, { bottom: LAYOUT.toolbarCenterFromBottom - LAYOUT.toolbarHeight / 2, width: stage.width }, chrome]} pointerEvents={reading ? 'box-none' : 'none'}>
        {/* As in the source: a small stacked app icon, a grey "+" pill and a round chevron. */}
        <View style={styles.stack} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <View style={[styles.stackCard, styles.stackBack]} />
          <LinearGradient colors={[...COLOR.stackIcon]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.stackCard} />
        </View>
        <Pressable onPress={onSummarize} style={({ pressed }) => [styles.plus, pressed && styles.pressed]} accessibilityRole="button" accessibilityLabel="Summarize article"
          accessibilityHint="Or pinch the page with two fingers" hitSlop={8}>
          <Ionicons name="add" size={20} color={COLOR.toolbarInk} />
        </Pressable>
        <View style={styles.round} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <Ionicons name="chevron-up" size={16} color={COLOR.toolbarInk} />
        </View>
      </Animated.View>
    </Animated.View>
    {!reading && <Pressable style={StyleSheet.absoluteFill} onPress={onShowArticle} accessibilityRole="button"
      accessibilityLabel="Show the full article" />}
  </Animated.View>;
}

const styles = StyleSheet.create({
  clip: { position: 'absolute', left: 0, top: 0, overflow: 'hidden', backgroundColor: COLOR.page, transformOrigin: 'top left' },
  inner: { position: 'absolute', left: 0, top: 0, backgroundColor: COLOR.page },
  masthead: { height: LAYOUT.mastheadHeight, paddingHorizontal: LAYOUT.gutter, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brand: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  avatar: { width: 28, height: 28, borderRadius: 14, backgroundColor: COLOR.toolbar, alignItems: 'center', justifyContent: 'center' },
  panel: { alignSelf: 'center', backgroundColor: COLOR.panel, paddingTop: LAYOUT.panelPadTop, paddingBottom: LAYOUT.panelPadBottom, alignItems: 'center', borderRadius: LAYOUT.panelRadius, overflow: 'hidden' },
  pill: { height: LAYOUT.pillHeight, paddingHorizontal: LAYOUT.pillPadX, borderRadius: 8, backgroundColor: COLOR.pill, justifyContent: 'center' },
  headline: { marginTop: LAYOUT.pillGap, paddingHorizontal: LAYOUT.panelPadX },
  dek: { marginTop: LAYOUT.dekGap, paddingHorizontal: LAYOUT.panelPadX },
  dot: { width: 4, height: 4, borderRadius: 2, backgroundColor: COLOR.panelInkSoft, marginTop: LAYOUT.dotGap, marginBottom: LAYOUT.bylineGap },
  date: { marginTop: 4 },
  body: { paddingHorizontal: LAYOUT.panelPadX, paddingTop: LAYOUT.bodyGap },
  sectionRow: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 16 },
  sectionRule: { flex: 1, height: StyleSheet.hairlineWidth, backgroundColor: COLOR.rule },
  paragraph: { marginBottom: 16 },
  leadIn: { fontWeight: '700', letterSpacing: 0.6 },
  toolbarFade: { position: 'absolute', left: 0, bottom: 0 },
  toolbar: { position: 'absolute', left: 0, height: LAYOUT.toolbarHeight, paddingHorizontal: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  stack: { width: LAYOUT.toolbarRound, height: LAYOUT.toolbarRound, justifyContent: 'center', alignItems: 'center' },
  stackCard: { position: 'absolute', width: 22, height: 22, borderRadius: 6, transform: [{ rotate: '-8deg' }] },
  stackBack: { backgroundColor: COLOR.toolbarInk, opacity: 0.25, transform: [{ rotate: '8deg' }, { translateX: 3 }] },
  plus: { width: LAYOUT.toolbarPill.width, height: LAYOUT.toolbarPill.height, borderRadius: LAYOUT.toolbarPill.height / 2, backgroundColor: COLOR.toolbar, alignItems: 'center', justifyContent: 'center' },
  // Press feedback within a frame: 0.97 (animate-expo §7).
  pressed: { transform: [{ scale: TIMING.pressScale }] },
  round: { width: LAYOUT.toolbarRound, height: LAYOUT.toolbarRound, borderRadius: LAYOUT.toolbarRound / 2, backgroundColor: COLOR.toolbar, alignItems: 'center', justifyContent: 'center' },
  toolIcon: { width: LAYOUT.toolbarIcon, height: LAYOUT.toolbarIcon, borderRadius: LAYOUT.toolbarIcon / 2, backgroundColor: COLOR.toolbar, alignItems: 'center', justifyContent: 'center' },
  summarize: { height: LAYOUT.toolbarPillHeight + 8, paddingHorizontal: 20, borderRadius: 22, backgroundColor: COLOR.toolbar, flexDirection: 'row', alignItems: 'center', gap: 8 },
});
