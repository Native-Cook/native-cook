/**
 * The summary: a title row over the card, the lines (measured once, then one row per line so each can stream in
 * on its own), and the footer with the credit and the close button.
 */
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View, type NativeSyntheticEvent, type TextLayoutEventData } from 'react-native';
import Animated, { interpolateColor, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { cardTop, cardWidth, clamp01, type Stage } from './geometry';
import { QA_HOLD, TIMING } from './motion';
import { COLOR, COPY, LAYOUT, TYPE } from './theme';
import type { Mode, SummaryValues } from './use-summary-motion';

export type Row = { key: string; text: string; x: number; y: number; width: number; kind: 'lead' | 'title' | 'body'; icon?: (typeof COPY.items)[number]['icon'] };
export type SummaryLines = { lead: string[]; titles: string[][]; bodies: string[][] };

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

const LINE = { lead: TYPE.lead.lineHeight, title: TYPE.itemTitle.lineHeight, body: TYPE.itemBody.lineHeight };

/** Total streaming time for n rows, then the footer. */
export function revealDuration(rows: number): number {
  return Math.max(0, rows) * TIMING.lineStagger + TIMING.lineMs;
}

export function layoutRows(lines: SummaryLines, width: number): Row[] {
  const rows: Row[] = [];
  const left = LAYOUT.textInset;
  const textWidth = width - LAYOUT.textInset * 2;
  let y = 0;
  lines.lead.forEach((text, i) => { rows.push({ key: `l${i}`, text, x: left, y, width: textWidth, kind: 'lead' }); y += LINE.lead; });
  y += LAYOUT.leadGap;
  COPY.items.forEach((item, n) => {
    const x = left + LAYOUT.itemIndent;
    (lines.titles[n] ?? []).forEach((text, i) => {
      rows.push({ key: `t${n}-${i}`, text, x, y, width: textWidth - LAYOUT.itemIndent, kind: 'title', icon: i === 0 ? item.icon : undefined });
      y += LINE.title;
    });
    (lines.bodies[n] ?? []).forEach((text, i) => { rows.push({ key: `b${n}-${i}`, text, x, y, width: textWidth - LAYOUT.itemIndent, kind: 'body' }); y += LINE.body; });
    y += LAYOUT.itemGap;
  });
  return rows;
}

function lineProgress(reveal: number, index: number, total: number, reduced: boolean): number {
  'worklet';
  if (reduced) return clamp01(reveal / Math.max(1, total));
  return clamp01((reveal - index * TIMING.lineStagger) / TIMING.lineMs);
}

function SummaryRow({ row, index, reveal, alpha, total, reduced }: { row: Row; index: number; reveal: SharedValue<number>; alpha: SharedValue<number>; total: number; reduced: boolean }) {
  const final = row.kind === 'body' ? COLOR.summaryInkSoft : COLOR.summaryInk;
  const style = useAnimatedStyle(() => {
    const u = lineProgress(reveal.get(), index, total, reduced);
    // Each line arrives lavender and cools to white: the text "settles" as it lands.
    return {
      opacity: u * alpha.get(),
      color: interpolateColor(clamp01((u - 0.35) / 0.65), [0, 1], [COLOR.summaryArrive, final]),
      transform: [{ translateY: reduced ? 0 : (1 - u) * TIMING.lineRise }],
    };
  });
  const disc = useAnimatedStyle(() => {
    const u = lineProgress(reveal.get(), index, total, reduced);
    // easeOutBack: the icon disc pops slightly past full size and settles.
    const c = 1.9;
    const back = 1 + (c + 1) * Math.pow(u - 1, 3) + c * Math.pow(u - 1, 2);
    return { opacity: u * alpha.get(), transform: [{ scale: reduced ? 1 : TIMING.discFrom + (1 - TIMING.discFrom) * back }] };
  });
  // Lines arrive ghosted: two copies slide in from ±6 pt and fade as the line sharpens (a blur stand-in).
  const ghostL = useAnimatedStyle(() => {
    const u = lineProgress(reveal.get(), index, total, reduced);
    return { opacity: reduced ? 0 : TIMING.ghostAlpha * Math.sin(Math.PI * u) * alpha.get(), transform: [{ translateX: -(1 - u) * TIMING.ghostOffset }, { translateY: (1 - u) * TIMING.lineRise }] };
  });
  const ghostR = useAnimatedStyle(() => {
    const u = lineProgress(reveal.get(), index, total, reduced);
    return { opacity: reduced ? 0 : TIMING.ghostAlpha * Math.sin(Math.PI * u) * alpha.get(), transform: [{ translateX: (1 - u) * TIMING.ghostOffset }, { translateY: (1 - u) * TIMING.lineRise }] };
  });
  const type = row.kind === 'lead' ? TYPE.lead : row.kind === 'title' ? TYPE.itemTitle : TYPE.itemBody;
  const at = { left: row.x, top: row.y, width: row.width + 8 };
  return <>
    <Animated.Text numberOfLines={1} style={[type, styles.row, at, { color: COLOR.summaryArrive }, ghostL]}>{row.text}</Animated.Text>
    <Animated.Text numberOfLines={1} style={[type, styles.row, at, { color: COLOR.summaryArrive }, ghostR]}>{row.text}</Animated.Text>
    {row.icon && <Animated.View style={[styles.disc, { left: LAYOUT.textInset, top: row.y + (LINE.title - LAYOUT.iconDisc) / 2 }, disc]}>
      <Ionicons name={row.icon} size={13} color={COLOR.iconGlyph} />
    </Animated.View>}
    <Animated.Text numberOfLines={1} style={[type, styles.row, at, style]}>{row.text}</Animated.Text>
  </>;
}

/** Renders the summary invisibly once to learn where its lines break. */
export function SummaryMeasure({ width, onLines }: { width: number; onLines: (lines: SummaryLines) => void }) {
  const textWidth = width - LAYOUT.textInset * 2;
  const measured = useMemo(() => ({ lead: [] as string[], titles: [] as string[][], bodies: [] as string[][], count: 0 }), []);
  const total = 1 + COPY.items.length * 2;
  const take = useCallback((slot: 'lead' | 'title' | 'body', n: number) => (e: NativeSyntheticEvent<TextLayoutEventData>) => {
    const texts = e.nativeEvent.lines.map((l) => l.text.replace(/\s+$/, ''));
    if (slot === 'lead') measured.lead = texts;
    else if (slot === 'title') measured.titles[n] = texts;
    else measured.bodies[n] = texts;
    measured.count += 1;
    if (measured.count === total) onLines({ lead: measured.lead, titles: measured.titles, bodies: measured.bodies });
  }, [measured, onLines, total]);
  return <View style={styles.measure} pointerEvents="none" importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
    <Text style={[TYPE.lead, { width: textWidth }]} onTextLayout={take('lead', 0)}>{COPY.lead}</Text>
    {COPY.items.map((item, n) => <View key={n}>
      <Text style={[TYPE.itemTitle, { width: textWidth - LAYOUT.itemIndent }]} onTextLayout={take('title', n)}>{item.title}</Text>
      <Text style={[TYPE.itemBody, { width: textWidth - LAYOUT.itemIndent }]} onTextLayout={take('body', n)}>{item.body}</Text>
    </View>)}
  </View>;
}

type Props = { stage: Stage; insetBottom: number; values: SummaryValues; mode: Mode; reduceMotion: boolean; rows: Row[]; revealTotal: number; onClose: () => void; summaryText: string };

export function SummaryLayer({ stage, insetBottom, values, mode, reduceMotion, rows, revealTotal, onClose, summaryText }: Props) {
  const { reveal, summaryAlpha, lift } = values;
  const top = cardTop(1) + LAYOUT.cardHeight + LAYOUT.summaryGap;
  // The page-title tab row rides just above the card from the moment it lands, as in the source.
  const titleRow = useAnimatedStyle(() => ({
    opacity: clamp01((values.p.get() - 0.9) / 0.1),
    transform: [{ translateY: cardTop(lift.get()) - LAYOUT.titleRowGap }],
  }));
  const footerBottom = Math.max(LAYOUT.footerBottom, insetBottom - LAYOUT.footerBottom);
  // The close button is there as soon as the card lands (the source shows it while summarizing).
  const closeIn = useAnimatedStyle(() => ({ opacity: clamp01((values.p.get() - 0.9) / 0.1) }));
  const startW = LAYOUT.toolbarPill.width;
  const startH = LAYOUT.toolbarPill.height;
  const startLeft = stage.width / 2 - startW / 2;
  const startBottom = LAYOUT.toolbarCenterFromBottom - startH / 2;
  const endLeft = LAYOUT.textInset - LAYOUT.creditPad;
  const endBottom = footerBottom + (LAYOUT.footerHeight - LAYOUT.creditPill.height) / 2;
  const morph = useAnimatedStyle(() => {
    const t = clamp01(values.p.get());
    const e = t * t * (3 - 2 * t);
    return {
      opacity: QA_HOLD !== null ? 0 : reduceMotion ? clamp01((t - 0.9) / 0.1) : clamp01(t / 0.25),
      left: startLeft + (endLeft - startLeft) * e,
      bottom: startBottom + (endBottom - startBottom) * e,
      width: startW + (LAYOUT.creditPill.width - startW) * e,
      height: startH + (LAYOUT.creditPill.height - startH) * e,
      borderRadius: (startH + (LAYOUT.creditPill.height - startH) * e) / 2,
      backgroundColor: interpolateColor(e, [0, 1], [COLOR.toolbar, COLOR.closeFill]),
    };
  });
  // Payoff: a light sweeps across the credit pill once the summary has landed.
  const sweep = useAnimatedStyle(() => {
    const at = values.bloomAt.get();
    const u = at < 0 ? 0 : clamp01((values.clock.get() - at - revealTotal - TIMING.sweepDelay) / TIMING.sweepMs);
    const e = u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2;
    return { opacity: u > 0 && u < 1 ? 1 : 0, transform: [{ translateX: -LAYOUT.sweepWidth + e * (LAYOUT.creditPill.width + LAYOUT.sweepWidth) }] };
  });
  // The credit lands in the pill as soon as the card does, so the morphed pill is never empty.
  const footer = useAnimatedStyle(() => ({
    opacity: QA_HOLD !== null ? summaryAlpha.get() * clamp01((reveal.get() - revealTotal + TIMING.lineMs) / TIMING.lineMs) : clamp01((values.p.get() - 0.92) / 0.08),
  }));
  const live = mode === 'summary';
  const cw = cardWidth(stage);

  return <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
    <Animated.View style={[styles.titleRow, { top: 0, left: (stage.width - cw) / 2, width: cw }, titleRow]}
      importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
      <View style={styles.favicon}><Text style={styles.faviconText}>M</Text></View>
      <Text style={TYPE.cardTitle} numberOfLines={1}>{COPY.pageTitle}</Text>
    </Animated.View>
    {/* The summary is cut off above the footer, as in the source (it scrolls under it there). */}
    <View style={[styles.rows, { top, bottom: footerBottom + LAYOUT.footerHeight }]} accessible={live} accessibilityLabel={live ? `Summary. ${summaryText}` : undefined}
      importantForAccessibility={live ? 'yes' : 'no-hide-descendants'} pointerEvents="none">
      {rows.map((row, i) => <SummaryRow key={row.key} row={row} index={i} reveal={reveal} alpha={summaryAlpha} total={revealTotal} reduced={reduceMotion} />)}
    </View>
    {/* Hero continuation: the toolbar's "+" pill travels and widens into the credit pill (a childless, absolute view). */}
    <Animated.View style={[styles.morph, morph]} pointerEvents="none" />
    <View style={[styles.footer, { bottom: footerBottom }]} pointerEvents={live ? 'box-none' : 'none'}>
      <Animated.View style={[styles.credit, footer]} accessibilityElementsHidden={!live} importantForAccessibility={live ? 'auto' : 'no-hide-descendants'}>
        <LinearGradient colors={[...COLOR.creditIcon]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.creditIcon}>
          <Ionicons name="sparkles" size={12} color={COLOR.closeInk} />
        </LinearGradient>
        <View>
          <Text style={TYPE.credit}>{COPY.credit[0]}</Text>
          <Text style={TYPE.credit}>{COPY.credit[1]}</Text>
        </View>
        <View style={styles.sweepClip} pointerEvents="none">
          <Animated.View style={[styles.sweep, sweep]}>
            <LinearGradient colors={[...COLOR.sweep]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
          </Animated.View>
        </View>
      </Animated.View>
      <AnimatedPressable onPress={onClose} style={[styles.close, closeIn]} hitSlop={6} accessibilityRole="button" accessibilityLabel="Close summary"
        accessibilityHint="Shows the full article. You can also pinch out" accessibilityElementsHidden={!live}>
        <Ionicons name="close" size={16} color={COLOR.closeInk} />
      </AnimatedPressable>
    </View>
  </View>;
}


const styles = StyleSheet.create({
  measure: { position: 'absolute', left: LAYOUT.textInset, top: 0, opacity: 0 },
  titleRow: { position: 'absolute', height: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  favicon: { width: 14, height: 14, borderRadius: 3, backgroundColor: COLOR.summaryInk, alignItems: 'center', justifyContent: 'center' },
  faviconText: { fontSize: 9, fontWeight: '900', color: COLOR.ink },
  rows: { position: 'absolute', left: 0, right: 0, overflow: 'hidden' },
  morph: { position: 'absolute' },
  sweepClip: { position: 'absolute', left: -LAYOUT.creditPad, top: -(LAYOUT.creditPill.height - 26) / 2, width: LAYOUT.creditPill.width, height: LAYOUT.creditPill.height, borderRadius: LAYOUT.creditPill.height / 2, overflow: 'hidden' },
  sweep: { position: 'absolute', top: 0, bottom: 0, width: LAYOUT.sweepWidth },
  row: { position: 'absolute' },
  disc: { position: 'absolute', width: LAYOUT.iconDisc, height: LAYOUT.iconDisc, borderRadius: LAYOUT.iconDisc / 2, backgroundColor: COLOR.iconDisc, alignItems: 'center', justifyContent: 'center' },
  footer: { position: 'absolute', left: LAYOUT.textInset, right: 20, height: LAYOUT.footerHeight, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  credit: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingLeft: 0 },
  creditIcon: { width: 24, height: 24, borderRadius: 7, alignItems: 'center', justifyContent: 'center' },
  close: { width: LAYOUT.closeSize, height: LAYOUT.closeSize, borderRadius: LAYOUT.closeSize / 2, backgroundColor: COLOR.closeFill, alignItems: 'center', justifyContent: 'center' },
});
