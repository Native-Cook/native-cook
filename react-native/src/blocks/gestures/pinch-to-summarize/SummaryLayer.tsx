/**
 * The summary: a title row over the card and its reading-progress bar, the lines of the chosen depth (each lands
 * from its chip or streams in around it), live facts (the price rolls, the sentiment meter fills), entity chips,
 * the footer, and the long-press share card.
 */
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Clipboard, Pressable, Share, StyleSheet, Text, View, type LayoutChangeEvent, type NativeSyntheticEvent, type TextLayoutEventData,
} from 'react-native';
import Animated, {
  cubicBezier, Easing, useAnimatedScrollHandler, useAnimatedStyle, useDerivedValue, useSharedValue, withDelay, withSequence, withSpring, withTiming,
  type SharedValue,
} from 'react-native-reanimated';

import { cardTop, cardWidth, clamp01, easeInOut, pageFrame, span, type Stage } from './geometry';
import { playSummaryHaptic } from './haptics';
import { CURVE, FLIGHT, SPRING, TIMING } from './motion';
import { PressScale, useReleaseOnPinch } from './PressScale';
import { RollingNumber } from './RollingNumber';
import {
  chipLand, edgeFade, footerBottom, LINE, riseIn, rowAlpha, segments, summaryTop, type Group, type Row, type SummaryLayout, type SummaryLines,
} from './summary-layout';
import { COLOR, COPY, LAYOUT, TYPE } from './theme';
import type { Mode, SummaryValues } from './use-summary-motion';

const PILL = LAYOUT.creditPill;
const EASE_OUT = Easing.bezier(CURVE.out[0], CURVE.out[1], CURVE.out[2], CURVE.out[3]);
const CSS_EASE_OUT = cubicBezier(CURVE.out[0], CURVE.out[1], CURVE.out[2], CURVE.out[3]);
const PRICE = COPY.entities.findIndex((e) => e.term === COPY.price.term);

type Ctx = {
  values: SummaryValues;
  reduced: boolean;
  scrollY: SharedValue<number>;
  room: number;
  contentHeight: number;
  terms: SharedValue<number>[];
};

/** When a line has landed on the flight clock (its live data starts then). */
function landOf(row: Row): number {
  return row.chip >= 0 ? chipLand(row.chip) : row.at + FLIGHT.lineMs / 2;
}

function typeOf(row: Row) {
  return row.kind === 'lead' ? TYPE.lead : row.kind === 'tldr' ? TYPE.tldr : row.kind === 'title' ? TYPE.itemTitle : TYPE.itemBody;
}

/** "$1,899": the digits roll in from 0,000 when the line lands. */
function Price({ row, ctx }: { row: Row; ctx: Ctx }) {
  const { flightD, flightDir } = ctx.values;
  const at = landOf(row);
  const value = useDerivedValue<number>(() => (flightDir.get() < 0 || flightD.get() >= at ? COPY.price.value : 0));
  const type = typeOf(row);
  return <View style={styles.inline} accessible accessibilityLabel={COPY.price.term}>
    <Text style={type}>$</Text>
    <RollingNumber value={value} decimals={0} wholeDigits={4} padded separator="," still={ctx.reduced} font={type}
      rollMs={FLIGHT.priceRollMs} rollEasing={EASE_OUT} />
  </View>;
}

/** Words an entity chip lights for a moment. */
function Term({ entity, ctx, children }: { entity: number; ctx: Ctx; children: ReactNode }) {
  const lit = ctx.terms[entity];
  const glow = useAnimatedStyle(() => ({ opacity: lit.get() }));
  return <View style={styles.inline}>
    <Animated.View style={[styles.termGlow, glow]} />
    {children}
  </View>;
}

function MeterSegment({ i, on, at, ctx }: { i: number; on: boolean; at: number; ctx: Ctx }) {
  const { flightD, flightDir } = ctx.values;
  const reduced = ctx.reduced;
  const fill = useAnimatedStyle(() => {
    const u = !on ? 0 : reduced || flightDir.get() < 0 ? 1 : riseIn(flightD.get(), at + i * FLIGHT.meterStagger, FLIGHT.meterMs);
    return { opacity: u, transform: [{ scaleX: 0.4 + 0.6 * u }] };
  });
  return <View style={styles.meterSeg}>
    <Animated.View style={[styles.meterFill, fill]} />
  </View>;
}

/** Review sentiment, 4 of 5: five slim segments fill left → right once the line has landed. */
function Meter({ value, at, ctx }: { value: number; at: number; ctx: Ctx }) {
  return <View style={styles.meter} accessible accessibilityLabel={`${value} out of 5`}>
    {[0, 1, 2, 3, 4].map((i) => <MeterSegment key={i} i={i} on={i < value} at={at} ctx={ctx} />)}
  </View>;
}

/** A title's icon disc: it pops from 0.8 with its title (easeOutBack) and hides with it. */
function Disc({ row, icon, ctx }: { row: Row; icon: NonNullable<Row['point']>['icon']; ctx: Ctx }) {
  const { reduced, scrollY, room, contentHeight } = ctx;
  const { flightD, flightDir, summaryAlpha, extraAlpha, p } = ctx.values;
  const disc = useAnimatedStyle(() => {
    const t = reduced ? 1 : clamp01((flightD.get() - row.at) / FLIGHT.lineMs);
    const a = rowAlpha(row, flightD.get(), flightDir.get(), reduced);
    // The disc hides with its row while the page is pulled open over it (see SummaryRow's `clear`).
    const clear = reduced ? 1 : span(p.get(), 0.8, 0.95);
    // easeOutBack: the icon disc pops slightly past full size with its title and settles.
    const c = TIMING.discBack;
    const back = flightDir.get() < 0 ? 1 : 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2);
    return {
      opacity: a * extraAlpha.get() * summaryAlpha.get() * clear * edgeFade(row.y, row.height, scrollY.get(), room, contentHeight),
      transform: [{ scale: reduced ? 1 : TIMING.discFrom + (1 - TIMING.discFrom) * back }],
    };
  });
  return <Animated.View style={[styles.disc, { top: row.y + (LINE.title - LAYOUT.iconDisc) / 2 }, disc]}>
    <Ionicons name={icon} size={LAYOUT.iconGlyph} color={COLOR.iconGlyph} />
  </Animated.View>;
}

function SummaryRow({ row, ctx }: { row: Row; ctx: Ctx }) {
  const { reduced, scrollY, room, contentHeight } = ctx;
  const { flightD, flightDir, summaryAlpha, extraAlpha, p } = ctx.values;
  // A line without a chip fades and rises 6 pt on the strong ease-out; a chip's line swaps in place with its chip, so
  // its style is opacity only (a transform array is new on every run, and would be sent to the view on every frame).
  const rises = row.chip < 0 && !reduced;
  const style = useAnimatedStyle(() => {
    const a = rowAlpha(row, flightD.get(), flightDir.get(), reduced);
    const extra = row.chip >= 0 && !reduced ? 1 : extraAlpha.get();
    // A line shows only where the page has cleared it (white words never sit on the white page): on the way in it waits
    // for the shrinking page, under a pull it hides as the page opens over it, and after a pull it does not pop back.
    const clear = reduced ? 1 : span(p.get(), 0.8, 0.95);
    const opacity = a * extra * clear * summaryAlpha.get() * edgeFade(row.y, row.height, scrollY.get(), room, contentHeight);
    if (!rises) return { opacity };
    return { opacity, transform: [{ translateY: flightDir.get() > 0 ? (1 - a) * FLIGHT.lineRise : 0 }] };
  });
  const type = typeOf(row);
  const icon = row.kind === 'title' ? row.point?.icon : undefined;
  return <>
    {icon && <Disc row={row} icon={icon} ctx={ctx} />}
    <Animated.View style={[styles.row, { left: row.x, top: row.y, height: row.height }, style]}>
      {row.kind === 'title'
        ? <>
          <Text style={type} numberOfLines={1}>{row.text}<Text style={TYPE.sup}>{`\u2009${row.sup ?? ''}`}</Text></Text>
          {row.point?.meter !== undefined && <Meter value={row.point.meter} at={row.at + FLIGHT.meterAfter} ctx={ctx} />}
        </>
        : segments(row.text).map((s, i) => {
          if (s.entity < 0) return <Text key={i} style={type}>{s.text}</Text>;
          const word = s.entity === PRICE ? <Price row={row} ctx={ctx} /> : <Text style={type}>{s.text}</Text>;
          return <Term key={i} entity={s.entity} ctx={ctx}>{word}</Term>;
        })}
    </Animated.View>
  </>;
}

/** Renders the summaries invisibly once to learn where their lines break. */
export function SummaryMeasure({ width, onLines }: { width: number; onLines: (lines: SummaryLines) => void }) {
  const textWidth = width - LAYOUT.textInset * 2;
  const measured = useMemo(() => ({ leads: [] as string[][], bodies: [] as string[][], count: 0 }), []);
  const total = COPY.levels.length + COPY.points.length;
  const take = useCallback((slot: 'lead' | 'body', n: number) => (e: NativeSyntheticEvent<TextLayoutEventData>) => {
    const texts = e.nativeEvent.lines.map((l) => l.text.replace(/\s+$/, ''));
    if (slot === 'lead') measured.leads[n] = texts;
    else measured.bodies[n] = texts;
    measured.count += 1;
    if (measured.count === total) onLines({ leads: measured.leads, bodies: measured.bodies });
  }, [measured, onLines, total]);
  return <View style={styles.measure} pointerEvents="none" importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
    {COPY.levels.map((level, n) => <Text key={`l${n}`} style={[level.tldr ? TYPE.tldr : TYPE.lead, { width: textWidth }]} onTextLayout={take('lead', n)}>{level.lead}</Text>)}
    {COPY.points.map((point, n) => <Text key={`b${n}`} style={[TYPE.itemBody, { width: textWidth - LAYOUT.itemIndent }]} onTextLayout={take('body', n)}>{point.body}</Text>)}
  </View>;
}

/**
 * A point of the summary (or its lead): a tap opens its sentence in the article, a hold lifts the summary. A soft
 * backing answers on press-in (opacity only, 150 ms in and 100 ms out on the strong ease-out), so the 350 ms hold is
 * never dead.
 */
function PointGroup({ group, live, fingers, onSource, onLongPress }: {
  group: Group; live: boolean; fingers: SharedValue<number>; onSource: (s: number) => void; onLongPress: () => void;
}) {
  const [pressed, setPressed] = useState(false);
  useReleaseOnPinch(fingers, setPressed);
  return <Pressable style={[styles.group, { left: group.x, top: group.y, width: group.width, height: Math.max(group.height, 44) }]}
    onPressIn={() => setPressed(fingers.get() < 2)} onPressOut={() => setPressed(false)} pressRetentionOffset={16}
    // The lifted card takes over from the backing, so the two never stack.
    onPress={() => onSource(group.source)} onLongPress={() => { setPressed(false); onLongPress(); }} delayLongPress={TIMING.longPressMs} accessibilityRole="button"
    accessibilityLabel={group.label} accessibilityHint="Shows where this comes from in the article" accessibilityElementsHidden={!live}>
    <Animated.View style={[styles.groupPress, {
      opacity: pressed ? 1 : 0, transitionProperty: 'opacity', transitionDuration: pressed ? TIMING.pressInMs : TIMING.pressOutMs,
      transitionTimingFunction: CSS_EASE_OUT,
    }]} />
  </Pressable>;
}

function EntityChip({ entity, order, at, ctx, onPress, onLongPress }: { entity: number; order: number; at: number; ctx: Ctx; onPress: (e: number) => void; onLongPress: () => void }) {
  const { flightD, flightDir, extraAlpha, summaryAlpha, p } = ctx.values;
  const reduced = ctx.reduced;
  const style = useAnimatedStyle(() => {
    const u = reduced || flightDir.get() < 0 ? 1 : riseIn(flightD.get(), at + order * FLIGHT.entityStagger, FLIGHT.entityMs);
    const a = reduced ? clamp01(flightD.get() / TIMING.reducedFade) : u;
    // Like the lines: hidden once a pull opens the page over them, so no chip floats over the article.
    const clear = reduced ? 1 : span(p.get(), 0.8, 0.95);
    return { opacity: a * extraAlpha.get() * summaryAlpha.get() * clear, transform: [{ translateY: reduced ? 0 : (1 - u) * FLIGHT.entityRise }] };
  });
  return <Animated.View style={style}>
    <PressScale onPress={() => onPress(entity)} onLongPress={onLongPress} delayLongPress={TIMING.longPressMs} hitSlop={{ top: 8, bottom: 8 }}
      style={styles.entity} reduced={ctx.reduced} fingers={ctx.values.fingers} accessibilityRole="button"
      accessibilityLabel={COPY.entities[entity].label} accessibilityHint="Highlights it in the summary">
      <Text style={TYPE.entity}>{COPY.entities[entity].label}</Text>
    </PressScale>
  </Animated.View>;
}

type Props = {
  stage: Stage;
  insetBottom: number;
  values: SummaryValues;
  mode: Mode;
  reduceMotion: boolean;
  layout: SummaryLayout;
  onClose: () => void;
  onSource: (source: number) => void;
};

export function SummaryLayer({ stage, insetBottom, values, mode, reduceMotion, layout, onClose, onSource }: Props) {
  const { p, lift, flightD, flightDir, extraAlpha, driftX, driftY, readPct, bloomAt, clock } = values;
  const top = summaryTop();
  const bottom = footerBottom(insetBottom);
  // The summary's room ends at the footer; it scrolls (fading whole lines at its edges) only if it does not fit.
  const room = stage.height - top - bottom - LAYOUT.footerHeight;
  const overflow = layout.height + LAYOUT.rowsBreath > room;
  const scrollY = useSharedValue(0);
  const onScroll = useAnimatedScrollHandler((e) => { scrollY.set(e.contentOffset.y); });
  const term0 = useSharedValue(0);
  const term1 = useSharedValue(0);
  const term2 = useSharedValue(0);
  const terms = useMemo(() => [term0, term1, term2], [term0, term1, term2]);
  const ctx = useMemo<Ctx>(() => ({ values, reduced: reduceMotion, scrollY, room, contentHeight: layout.height, terms }),
    [layout.height, reduceMotion, room, scrollY, terms, values]);
  const creditWidth = useSharedValue<number>(0);
  const onCreditLayout = useCallback((e: LayoutChangeEvent) => { creditWidth.set(e.nativeEvent.layout.width); }, [creditWidth]);
  const live = mode === 'summary';

  // Long-press lifts the summary as a card, with its actions under it; a tap anywhere else drops it.
  const lifted = useSharedValue(0);
  const [sharing, setSharing] = useState(false);
  const lift_ = useCallback(() => {
    // A long press that is really the start of a slow pinch (two fingers down) does not lift the summary.
    if (!live || values.fingers.get() > 1) return;
    setSharing(true);
    playSummaryHaptic('lift');
    lifted.set(withSpring(1, SPRING.share));
  }, [lifted, live, values.fingers]);
  // The drop is the system's answer to a tap away: faster than the lift, no overshoot.
  const drop = useCallback(() => {
    setSharing(false);
    lifted.set(withTiming(0, { duration: TIMING.dropMs, easing: EASE_OUT }));
  }, [lifted]);
  // Leaving the summary (pinch out, close, a tap on a point) puts the card back.
  const [wasLive, setWasLive] = useState(live);
  if (wasLive !== live) {
    setWasLive(live);
    if (!live) setSharing(false);
  }
  useEffect(() => { if (!live) lifted.set(withTiming(0, { duration: TIMING.dropMs, easing: EASE_OUT })); }, [lifted, live]);
  const shareText = useMemo(() => {
    const level = COPY.levels[layout.level];
    return [level.lead, ...level.points.map((n) => `• ${COPY.points[n].title}: ${COPY.points[n].body}`)].join('\n');
  }, [layout.level]);
  const copy = useCallback(() => {
    Clipboard.setString(shareText);
    playSummaryHaptic('copied');
    drop();
  }, [drop, shareText]);
  const share = useCallback(() => {
    void Share.share({ message: shareText }).catch(() => {});
    drop();
  }, [drop, shareText]);
  const lightTerm = useCallback((e: number) => {
    const v = terms[e];
    v.set(withSequence(
      withTiming(1, { duration: TIMING.termIn, easing: EASE_OUT }),
      withDelay(TIMING.termHold, withTiming(0, { duration: TIMING.termOut, easing: EASE_OUT })),
    ));
  }, [terms]);

  // The page-title tab row rides above the card (and the stacked card behind it) from the moment it lands.
  const titleRow = useAnimatedStyle(() => ({
    opacity: span(p.get(), 0.9, 1),
    transform: [{ translateY: cardTop(lift.get()) - LAYOUT.titleRowGap }],
  }));
  // How far the reader had read, on the thumbnail: the bar fills once the card has settled.
  // The bar is as wide as the settled card; it rides the card by transform (it only shows once the card is home).
  const barWidth = cardWidth(stage) - LAYOUT.progressInset * 2;
  const track = useAnimatedStyle(() => {
    const f = pageFrame(stage, p.get(), lift.get(), driftX.get(), driftY.get());
    return {
      opacity: span(p.get(), 0.9, 1) * extraAlpha.get(),
      transform: [{ translateX: f.x + LAYOUT.progressInset }, { translateY: f.y + f.height - LAYOUT.progressBottom - LAYOUT.progressHeight }],
    };
  });
  // The fill is absolute and childless, so its width may animate.
  const fill = useAnimatedStyle(() => {
    const u = reduceMotion || flightDir.get() < 0 ? 1 : riseIn(flightD.get(), FLIGHT.barAt, FLIGHT.barMs);
    return { width: barWidth * clamp01(readPct.get() / 100) * u };
  });
  const closeIn = useAnimatedStyle(() => ({ opacity: span(p.get(), 0.9, 1) * extraAlpha.get() * (1 - lifted.get()) }));
  // The credit waits for the last chip to land, so no chip ever flies over its words.
  const creditAt = layout.land + FLIGHT.creditAfter;
  const rollAt = layout.land + FLIGHT.rollAfter;
  const credit = useAnimatedStyle(() => {
    const u = reduceMotion || flightDir.get() < 0 ? 1 : riseIn(flightD.get(), creditAt, FLIGHT.creditMs);
    return {
      opacity: span(p.get(), 0.9, 1) * extraAlpha.get() * u * (1 - lifted.get()),
      transform: [{ translateY: reduceMotion ? 0 : (1 - u) * FLIGHT.lineRise }],
    };
  });
  // The share read and the reading time roll once the credit is in.
  const pct = useDerivedValue<number>(() => (flightDir.get() < 0 || flightD.get() >= rollAt ? Math.min(99, readPct.get()) : 0));
  const secs = useDerivedValue<number>(() => (flightDir.get() < 0 || flightD.get() >= rollAt ? COPY.readSeconds : 0));
  // Payoff: a light sweeps across the credit pill once the summary has landed. Its progress is a primitive, so once the
  // sweep is done (or before it starts) the light clock ticking on wakes nothing.
  const sweepU = useDerivedValue(() => {
    const at = bloomAt.get();
    return at < 0 ? 0 : clamp01((clock.get() - at - TIMING.sweepDelay) / TIMING.sweepMs);
  });
  const sweep = useAnimatedStyle(() => {
    const u = sweepU.get();
    const e = easeInOut(u);
    return { opacity: u > 0 && u < 1 ? 1 : 0, transform: [{ translateX: -LAYOUT.sweepWidth + e * (creditWidth.get() + LAYOUT.sweepWidth) }] };
  });
  // Reduce Motion: the glass and the actions fade; the card neither scales nor do the actions rise.
  const card = useAnimatedStyle(() => ({ transform: [{ scale: reduceMotion ? 1 : 1 + (TIMING.shareScale - 1) * lifted.get() }] }));
  const glass = useAnimatedStyle(() => ({ opacity: lifted.get() }));
  const actions = useAnimatedStyle(() => ({
    opacity: lifted.get(), transform: [{ translateY: reduceMotion ? 0 : (1 - lifted.get()) * TIMING.shareRise }],
  }));
  const cw = cardWidth(stage);
  const entities = layout.entities;

  return <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
    <Animated.View style={[styles.titleRow, { left: (stage.width - cw) / 2, width: cw }, titleRow]}
      importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
      <View style={styles.favicon}><Text style={styles.faviconText}>M</Text></View>
      <Text style={TYPE.cardTitle} numberOfLines={1}>{COPY.pageTitle}</Text>
    </Animated.View>
    <Animated.View style={[styles.track, { width: barWidth }, track]} pointerEvents="none" accessible={live} accessibilityLabel={live ? 'Reading progress before the summary' : undefined}>
      <Animated.View style={[styles.fill, fill]} />
    </Animated.View>
    <View style={[styles.footer, { bottom }]} pointerEvents={live && !sharing ? 'box-none' : 'none'}>
      <Animated.View style={[styles.credit, credit]} onLayout={onCreditLayout} accessibilityElementsHidden={!live}
        importantForAccessibility={live ? 'auto' : 'no-hide-descendants'}>
        <LinearGradient colors={[...COLOR.creditIcon]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.creditIcon}>
          <Ionicons name="sparkles" size={PILL.glyph} color={COLOR.closeInk} />
        </LinearGradient>
        <View>
          <Text style={TYPE.creditTitle}>{COPY.credit}</Text>
          <View style={styles.inline}>
            <Text style={TYPE.creditSub}>{COPY.creditRead[0]}</Text>
            <RollingNumber value={pct} decimals={0} wholeDigits={2} still={reduceMotion} font={TYPE.creditSub} rollMs={FLIGHT.rollMs} rollEasing={EASE_OUT} />
            <Text style={TYPE.creditSub}>{COPY.creditRead[1]}</Text>
            <RollingNumber value={secs} decimals={0} wholeDigits={2} padded still={reduceMotion} font={TYPE.creditSub} rollMs={FLIGHT.rollMs} rollEasing={EASE_OUT} />
            <Text style={TYPE.creditSub}>{COPY.creditRead[2]}</Text>
          </View>
        </View>
        <View style={styles.sweepClip} pointerEvents="none">
          <Animated.View style={[styles.sweep, sweep]}>
            <LinearGradient colors={[...COLOR.sweep]} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
          </Animated.View>
        </View>
      </Animated.View>
      <Animated.View style={closeIn}>
        <PressScale onPress={onClose} style={styles.close} reduced={reduceMotion} fingers={values.fingers} accessibilityRole="button" accessibilityLabel="Close summary"
          accessibilityHint="Shows the full article. You can also pinch out" accessibilityElementsHidden={!live}>
          <Ionicons name="close" size={LAYOUT.closeGlyph} color={COLOR.closeInk} />
        </PressScale>
      </Animated.View>
    </View>
    {sharing && <Pressable style={StyleSheet.absoluteFill} onPress={drop} accessibilityRole="button" accessibilityLabel="Put the summary back" />}
    <Animated.View style={[styles.card, { top, height: room }, card]} pointerEvents={live ? 'box-none' : 'none'}>
      <Animated.View style={[styles.glass, glass]} pointerEvents="none" />
      <Animated.ScrollView style={StyleSheet.absoluteFill} contentContainerStyle={{ height: layout.height + LAYOUT.rowsBreath }}
        scrollEnabled={live && overflow} showsVerticalScrollIndicator={false} onScroll={onScroll} scrollEventThrottle={16}>
        {/* Long-press anywhere on the summary lifts it; each block opens its source sentence in the article. */}
        <Pressable style={StyleSheet.absoluteFill} onLongPress={lift_} delayLongPress={TIMING.longPressMs} accessible={false} />
        {layout.groups.map((g, i) => <PointGroup key={i} group={g} live={live} fingers={values.fingers} onSource={onSource} onLongPress={lift_} />)}
        <View style={StyleSheet.absoluteFill} pointerEvents="none">
          {layout.rows.map((row) => <SummaryRow key={row.key} row={row} ctx={ctx} />)}
        </View>
        {entities && <View style={[styles.entities, { top: entities.y }]} pointerEvents="box-none">
          {entities.items.map((e, k) => <EntityChip key={e} entity={e} order={k} at={entities.at} ctx={ctx} onPress={lightTerm} onLongPress={lift_} />)}
        </View>}
      </Animated.ScrollView>
    </Animated.View>
    <Animated.View style={[styles.actions, { bottom }, actions]} pointerEvents={sharing ? 'box-none' : 'none'}>
      <PressScale onPress={copy} containerStyle={styles.actionSlot} style={styles.action} reduced={reduceMotion} accessibilityRole="button">
        <Ionicons name="copy-outline" size={17} color={COLOR.actionInk} />
        <Text style={TYPE.action}>{COPY.share[0]}</Text>
      </PressScale>
      <PressScale onPress={share} containerStyle={styles.actionSlot} style={styles.action} reduced={reduceMotion} accessibilityRole="button">
        <Ionicons name="share-outline" size={17} color={COLOR.actionInk} />
        <Text style={TYPE.action}>{COPY.share[1]}</Text>
      </PressScale>
    </Animated.View>
  </View>;
}

const styles = StyleSheet.create({
  measure: { position: 'absolute', left: LAYOUT.textInset, top: 0, opacity: 0 },
  titleRow: { position: 'absolute', top: 0, height: LAYOUT.titleRowHeight, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 },
  favicon: { width: 14, height: 14, borderRadius: 4, backgroundColor: COLOR.summaryInk, alignItems: 'center', justifyContent: 'center' },
  faviconText: { fontSize: 9, fontWeight: '900', color: COLOR.ink },
  track: { position: 'absolute', left: 0, top: 0, height: LAYOUT.progressHeight, borderRadius: LAYOUT.progressHeight / 2, backgroundColor: COLOR.progressTrack, overflow: 'hidden' },
  fill: { position: 'absolute', left: 0, top: 0, bottom: 0, borderRadius: LAYOUT.progressHeight / 2, backgroundColor: COLOR.progressFill },
  card: { position: 'absolute', left: 0, right: 0 },
  glass: {
    position: 'absolute', left: LAYOUT.textInset - LAYOUT.sharePad, right: LAYOUT.textInset - LAYOUT.sharePad, top: -LAYOUT.sharePad, bottom: 0,
    borderRadius: LAYOUT.shareRadius, backgroundColor: COLOR.shareGlass, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.shareBorder,
    shadowColor: COLOR.shareShadow, shadowOpacity: 0.45, shadowRadius: 28, shadowOffset: { width: 0, height: 16 },
  },
  group: { position: 'absolute' },
  groupPress: {
    position: 'absolute', left: -LAYOUT.pointPressPadX, right: -LAYOUT.pointPressPadX, top: -LAYOUT.pointPressPadY, bottom: -LAYOUT.pointPressPadY,
    borderRadius: LAYOUT.pointPressRadius, backgroundColor: COLOR.pointPress,
  },
  row: { position: 'absolute', flexDirection: 'row', alignItems: 'flex-start' },
  inline: { flexDirection: 'row', alignItems: 'flex-start' },
  termGlow: { position: 'absolute', left: -2, right: -2, top: 1, bottom: 1, borderRadius: 4, backgroundColor: COLOR.termFlash },
  disc: { position: 'absolute', left: LAYOUT.textInset, width: LAYOUT.iconDisc, height: LAYOUT.iconDisc, borderRadius: LAYOUT.iconDisc / 2, backgroundColor: COLOR.iconDisc, alignItems: 'center', justifyContent: 'center' },
  meter: { flexDirection: 'row', gap: LAYOUT.meterGap, marginLeft: LAYOUT.meterLeft, alignSelf: 'center' },
  meterSeg: { width: LAYOUT.meterSegW, height: LAYOUT.meterSegH, borderRadius: LAYOUT.meterSegH / 2, backgroundColor: COLOR.meterTrack, overflow: 'hidden' },
  meterFill: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, borderRadius: LAYOUT.meterSegH / 2, backgroundColor: COLOR.meterFill, transformOrigin: 'left center' },
  entities: { position: 'absolute', left: LAYOUT.textInset, right: LAYOUT.textInset, height: LAYOUT.entityHeight, flexDirection: 'row', gap: LAYOUT.entityGapX },
  entity: {
    height: LAYOUT.entityHeight, paddingHorizontal: LAYOUT.entityPadX, borderRadius: LAYOUT.entityHeight / 2, justifyContent: 'center',
    backgroundColor: COLOR.entityFill, borderWidth: StyleSheet.hairlineWidth, borderColor: COLOR.entityBorder,
  },
  footer: { position: 'absolute', left: LAYOUT.textInset, right: LAYOUT.textInset, height: LAYOUT.footerHeight, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  credit: { height: PILL.height, borderRadius: PILL.height / 2, paddingLeft: PILL.padLeft, paddingRight: PILL.padRight, flexDirection: 'row', alignItems: 'center', gap: PILL.gap, overflow: 'hidden', backgroundColor: COLOR.closeFill },
  creditIcon: { width: PILL.icon, height: PILL.icon, borderRadius: PILL.icon / 2, alignItems: 'center', justifyContent: 'center' },
  sweepClip: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, borderRadius: PILL.height / 2, overflow: 'hidden' },
  sweep: { position: 'absolute', top: 0, bottom: 0, width: LAYOUT.sweepWidth },
  close: { width: LAYOUT.closeSize, height: LAYOUT.closeSize, borderRadius: LAYOUT.closeSize / 2, backgroundColor: COLOR.closeFill, alignItems: 'center', justifyContent: 'center' },
  actions: { position: 'absolute', left: LAYOUT.textInset, right: LAYOUT.textInset, height: LAYOUT.actionHeight, flexDirection: 'row', gap: LAYOUT.actionGap },
  actionSlot: { flex: 1 },
  action: {
    height: LAYOUT.actionHeight, borderRadius: LAYOUT.actionHeight / 2, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
    backgroundColor: COLOR.actionFill, paddingHorizontal: LAYOUT.actionPadX,
  },
});
