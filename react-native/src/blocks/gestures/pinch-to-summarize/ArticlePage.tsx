/**
 * The article as a real, scrollable, accessible page. It is transformed (never re-laid out) into the card. Its key
 * sentences are drawn by an overlay at their measured lines, so the highlighter can sweep behind them while the rest
 * of the page washes out, and so each can leave the page as a chip and come back.
 */
import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useRef } from 'react';
import { Pressable, StyleSheet, Text, View, type LayoutChangeEvent, type NativeSyntheticEvent, type TextLayoutEventData } from 'react-native';
import { GestureDetector, type NativeGesture } from 'react-native-gesture-handler';
import Animated, {
  scrollTo, useAnimatedReaction, useAnimatedRef, useAnimatedScrollHandler, useAnimatedStyle,
} from 'react-native-reanimated';
import Svg, { Circle, Path } from 'react-native-svg';

import { clamp01, mix, pageFrame, smooth, type KeyGeom, type KeyLine, type Stage } from './geometry';
import { MARK, TIMING } from './motion';
import { PressScale } from './PressScale';
import { homeSwap, pulse } from './summary-layout';
import { COLOR, COPY, LAYOUT, TYPE } from './theme';
import type { Mode, SummaryValues } from './use-summary-motion';

const hero = require('./assets/hero.jpg');

// The paragraphs that open with a key sentence, in order (key sentence i lives in paragraph KEY_PARAS[i]).
export const KEY_PARAS = COPY.body.map((para, i) => (para.key ? i : -1)).filter((i) => i >= 0);

type Props = {
  stage: Stage;
  insetBottom: number;
  values: SummaryValues;
  mode: Mode;
  reduceMotion: boolean;
  scrollLocked: boolean;
  native: NativeGesture;
  keys: KeyGeom[] | null;
  onKeys: (keys: KeyGeom[]) => void;
  onContentHeight: (height: number) => void;
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

/** A line of a key sentence: the bold lead-in (first line of the first paragraph only), then the words. */
export function KeyLineText({ line, leadIn, style }: { line: KeyLine; leadIn: number; style: object }) {
  return <Text numberOfLines={1} style={[TYPE.body, style, { left: line.x, top: line.y, width: line.width + 8 }]}>
    {leadIn > 0 ? <Text style={styles.leadIn}>{line.text.slice(0, leadIn)}</Text> : null}
    {leadIn > 0 ? line.text.slice(leadIn) : line.text}
  </Text>;
}

type BandProps = { line: KeyLine; before: number; total: number; index: number; values: SummaryValues; reduced: boolean };

/** One line's marker band. It grows left → right as the sweep passes this line (an absolute, childless view). */
function BandLine({ line, before, total, index, values, reduced }: BandProps) {
  // Shared values are taken out of `values` before the worklet: a worklet that captures the whole object subscribes to
  // every value in it (the light clock included) and re-runs on every frame of the block.
  const pop = values.pops[index];
  const { mark, flash: flashV, flashIdx } = values;
  const style = useAnimatedStyle(() => {
    const fill = clamp01(mark.get() - index) * total - before;
    const w = Math.min(line.width, Math.max(0, fill));
    const flash = flashIdx.get() === index ? flashV.get() : 0;
    // Reduce Motion keeps the lock and the flash as tone (KeyMark) and drops the band's growth.
    return {
      width: w > 0 ? w + LAYOUT.markerPadX * 2 : 0,
      transform: [{ scaleY: reduced ? 1 : 1 + MARK.popScale * pop.get() + MARK.flashScale * flash }],
    };
  });
  return <Animated.View style={[styles.band, {
    left: line.x - LAYOUT.markerPadX, top: line.y + LAYOUT.markerTop,
    height: TYPE.body.lineHeight - LAYOUT.markerTop - LAYOUT.markerBottom,
  }, style]} />;
}

/** A key sentence over the page: its bands, then its words (above the veil, so they stay sharp while the rest dims). */
function KeyMark({ k, index, values, reduced }: { k: KeyGeom; index: number; values: SummaryValues; reduced: boolean }) {
  const total = k.lines.reduce((s, l) => s + l.width, 0);
  const { flightD, flightDir, chipFade, mark, wait, think, flashIdx, flash, markAlpha } = values;
  const bands = useAnimatedStyle(() => {
    // The page's copy steps aside the moment its chip lifts off (and swaps back in place as the chip lands home, or
    // fades back in while a chip caught mid-flight fades out).
    const away = reduced ? 0 : 1 - Math.max(homeSwap(flightD.get(), index, flightDir.get()), 1 - chipFade.get());
    const locked = mark.get() >= index + 1 - 1e-3;
    const beat = Math.max(pulse(wait.get(), index) * think.get(), flashIdx.get() === index ? flash.get() : 0);
    const tone = locked ? MARK.toneLocked + (1 - MARK.toneLocked) * beat : MARK.toneSweep;
    return { opacity: markAlpha.get() * (1 - away) * tone };
  });
  const words = useAnimatedStyle(() => ({
    opacity: reduced ? 1 : Math.max(homeSwap(flightD.get(), index, flightDir.get()), 1 - chipFade.get()),
  }));
  const befores = k.lines.map((_, j) => k.lines.slice(0, j).reduce((s, l) => s + l.width, 0));
  return <View style={[styles.mark, { left: k.x, top: k.y, width: k.width + 8, height: k.height }]} pointerEvents="none"
    accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <Animated.View style={[StyleSheet.absoluteFill, bands]}>
      {k.lines.map((line, j) => <BandLine key={j} line={line} before={befores[j]} total={total} index={index} values={values} reduced={reduced} />)}
    </Animated.View>
    <Animated.View style={[StyleSheet.absoluteFill, words]}>
      {k.lines.map((line, j) => <KeyLineText key={j} line={line} leadIn={j === 0 ? k.leadIn : 0} style={styles.lineText} />)}
    </Animated.View>
  </View>;
}

export function ArticlePage({ stage, insetBottom, values, mode, reduceMotion, scrollLocked, native, keys, onKeys, onContentHeight, onSummarize, onShowArticle }: Props) {
  const { p, lift, driftX, driftY, pageAlpha, scrollY, scrollPos, scrolling, scrollLink, breath, dim } = values;
  const panelWidth = stage.width - LAYOUT.panelInset * 2;
  const textWidth = stage.width - LAYOUT.panelPadX * 2;
  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const onScroll = useAnimatedScrollHandler((e) => { scrollY.set(e.contentOffset.y); });
  // The motion moves the scroll with the page (to the top into the card, back to the reader's place out of it): while
  // linked it is derived from p itself, otherwise it follows the timed scroll of the autoplay's reading.
  useAnimatedReaction(() => {
    const l = scrollLink.get();
    if (l.on) return mix(l.from, l.to, smooth((p.get() - l.p0) / (l.p1 - l.p0)));
    return scrolling.get() ? scrollPos.get() : -1;
  }, (y, prev) => {
    if (y >= 0 && y !== prev) scrollTo(scrollRef, 0, y, false);
  });

  const clip = useAnimatedStyle(() => {
    const f = pageFrame(stage, p.get(), lift.get(), driftX.get(), driftY.get(), breath.get());
    return {
      height: f.clipHeight,
      borderRadius: f.radius / f.scale,
      opacity: pageAlpha.get(),
      transform: [{ translateX: f.x }, { translateY: f.y }, { scale: f.scale }],
    };
  });
  const inner = useAnimatedStyle(() => ({ transform: [{ translateY: -pageFrame(stage, p.get(), 0, 0, 0).trim }] }));
  const chrome = useAnimatedStyle(() => ({ opacity: 1 - clamp01(p.get() / TIMING.toolbarFadeSpan) }));
  const veil = useAnimatedStyle(() => ({ opacity: dim.get() }));
  // The status-bar band's soft edge shows only once content passes under it (at rest it would fade the masthead).
  const safeEdge = useAnimatedStyle(() => ({ opacity: clamp01(scrollY.get() / LAYOUT.safeFade) }));
  const reading = mode === 'reading';

  // Measured once: where each key sentence's lines fall in the scroll content.
  const measure = useRef({ bodyY: -1, paraY: KEY_PARAS.map(() => -1), lines: KEY_PARAS.map(() => null as KeyLine[] | null), done: false });
  const flush = useCallback(() => {
    const m = measure.current;
    if (m.done || m.bodyY < 0 || m.paraY.some((y) => y < 0) || m.lines.some((l) => l === null)) return;
    m.done = true;
    onKeys(KEY_PARAS.map((pi, i) => {
      const lines = m.lines[i] ?? [];
      const last = lines[lines.length - 1];
      return {
        x: LAYOUT.panelPadX,
        y: m.bodyY + m.paraY[i],
        width: lines.reduce((w, l) => Math.max(w, l.width), 0),
        height: last ? last.y + TYPE.body.lineHeight : 0,
        lines,
        leadIn: COPY.body[pi].leadIn?.length ?? 0,
      };
    }));
  }, [onKeys]);
  const onBody = useCallback((e: LayoutChangeEvent) => { measure.current.bodyY = e.nativeEvent.layout.y; flush(); }, [flush]);
  const onPara = useCallback((i: number) => (e: LayoutChangeEvent) => { measure.current.paraY[i] = e.nativeEvent.layout.y; flush(); }, [flush]);
  const onKeyText = useCallback((i: number) => (e: NativeSyntheticEvent<TextLayoutEventData>) => {
    measure.current.lines[i] = e.nativeEvent.lines.map((l) => ({ text: l.text.replace(/\s+$/, ''), x: l.x, y: l.y, width: l.width, height: l.height }));
    flush();
  }, [flush]);
  const ghost = keys !== null;

  return <Animated.View style={[styles.clip, { width: stage.width }, clip]}
    accessibilityElementsHidden={!reading} importantForAccessibility={reading ? 'auto' : 'no-hide-descendants'}
    accessibilityActions={[{ name: 'summarize', label: 'Summarize article' }]}
    onAccessibilityAction={(e) => { if (e.nativeEvent.actionName === 'summarize') onSummarize(); }}>
    <Animated.View style={[styles.inner, { width: stage.width, height: stage.height }, inner]}>
      <GestureDetector gesture={native}>
        <Animated.ScrollView ref={scrollRef} scrollEnabled={!scrollLocked} showsVerticalScrollIndicator={false} onScroll={onScroll} scrollEventThrottle={16}
          onContentSizeChange={(_w, h) => onContentHeight(h)}
          contentContainerStyle={{ paddingBottom: LAYOUT.toolbarHeight + insetBottom + 32 }}>
          <View style={{ paddingTop: stage.insetTop }}>
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
            <View style={styles.body} onLayout={onBody}>
              <View style={styles.sectionRow}>
                <Text style={TYPE.section}>{COPY.section}</Text>
                <View style={styles.sectionRule} />
              </View>
              {COPY.body.map((para, i) => {
                const k = KEY_PARAS.indexOf(i);
                // A key sentence is drawn by its overlay once measured; the paragraph keeps it (invisible) for layout
                // and for VoiceOver.
                return <Text key={i} style={[TYPE.body, styles.paragraph]} onLayout={k >= 0 ? onPara(k) : undefined}>
                  {para.leadIn ? <Text style={[styles.leadIn, ghost && styles.ghost]}>{para.leadIn}</Text> : null}
                  {para.key ? <Text style={ghost ? styles.ghost : undefined}>{para.key}</Text> : null}
                  {para.rest}
                </Text>;
              })}
              {!ghost && KEY_PARAS.map((pi, i) => <Text key={`m${i}`} style={[TYPE.body, styles.measure, { width: textWidth }]} onTextLayout={onKeyText(i)}
                accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
                {COPY.body[pi].leadIn ? <Text style={styles.leadIn}>{COPY.body[pi].leadIn}</Text> : null}
                {COPY.body[pi].key}
              </Text>)}
            </View>
            {/* Everything but the key sentences washes out as the pinch deepens. */}
            <Animated.View style={[StyleSheet.absoluteFill, styles.veil, veil]} pointerEvents="none" />
            {keys?.map((k, i) => <KeyMark key={i} k={k} index={i} values={values} reduced={reduceMotion} />)}
          </View>
        </Animated.ScrollView>
      </GestureDetector>
      {/* The page's own status-bar band: nothing scrolls over the clock or the Dynamic Island. */}
      <View style={[styles.safe, { height: stage.insetTop, width: stage.width }]} pointerEvents="none" />
      <Animated.View style={[styles.safeEdge, { top: stage.insetTop, width: stage.width }, safeEdge]} pointerEvents="none">
        <LinearGradient colors={[COLOR.page, COLOR.pageClear]} style={StyleSheet.absoluteFill} />
      </Animated.View>
      <Animated.View style={[styles.toolbarFade, { height: LAYOUT.toolbarHeight + insetBottom + LAYOUT.toolbarFade, width: stage.width }, chrome]} pointerEvents="none">
        <LinearGradient colors={[COLOR.pageClear, COLOR.page]} locations={[0, 0.45]} style={StyleSheet.absoluteFill} />
      </Animated.View>
      <Animated.View style={[styles.toolbar, { bottom: LAYOUT.toolbarCenterFromBottom - LAYOUT.toolbarHeight / 2, width: stage.width }, chrome]} pointerEvents={reading ? 'box-none' : 'none'}>
        {/* As in the source: a small stacked app icon, a grey "+" pill and a round chevron. */}
        <View style={styles.stack} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <View style={[styles.stackCard, styles.stackBack]} />
          <LinearGradient colors={[...COLOR.stackIcon]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.stackCard} />
        </View>
        <PressScale onPress={onSummarize} style={styles.plus} reduced={reduceMotion} accessibilityRole="button" accessibilityLabel="Summarize article"
          accessibilityHint="Or pinch the page with two fingers" hitSlop={8}>
          <Ionicons name="add" size={LAYOUT.plusGlyph} color={COLOR.toolbarInk} />
        </PressScale>
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
  ghost: { color: 'transparent' },
  measure: { position: 'absolute', left: LAYOUT.panelPadX, top: 0, opacity: 0 },
  veil: { backgroundColor: COLOR.veil },
  mark: { position: 'absolute' },
  band: { position: 'absolute', borderRadius: LAYOUT.markerRadius, backgroundColor: COLOR.marker },
  lineText: { position: 'absolute' },
  safe: { position: 'absolute', left: 0, top: 0, backgroundColor: COLOR.page },
  safeEdge: { position: 'absolute', left: 0, height: LAYOUT.safeFade },
  toolbarFade: { position: 'absolute', left: 0, bottom: 0 },
  toolbar: { position: 'absolute', left: 0, height: LAYOUT.toolbarHeight, paddingHorizontal: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  stack: { width: LAYOUT.toolbarRound, height: LAYOUT.toolbarRound, justifyContent: 'center', alignItems: 'center' },
  stackCard: { position: 'absolute', width: 22, height: 22, borderRadius: 6, transform: [{ rotate: '-8deg' }] },
  stackBack: { backgroundColor: COLOR.toolbarInk, opacity: 0.25, transform: [{ rotate: '8deg' }, { translateX: 3 }] },
  plus: { width: LAYOUT.toolbarPill.width, height: LAYOUT.toolbarPill.height, borderRadius: LAYOUT.toolbarPill.height / 2, backgroundColor: COLOR.toolbar, alignItems: 'center', justifyContent: 'center' },
  round: { width: LAYOUT.toolbarRound, height: LAYOUT.toolbarRound, borderRadius: LAYOUT.toolbarRound / 2, backgroundColor: COLOR.toolbar, alignItems: 'center', justifyContent: 'center' },
});
