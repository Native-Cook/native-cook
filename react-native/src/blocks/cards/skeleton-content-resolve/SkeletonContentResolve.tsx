import { Canvas } from '@shopify/react-native-skia';
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import type { SFSymbol } from 'sf-symbols-typescript';
import { useCallback, useMemo, useRef, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useFrameCallback,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { CoverArt } from './CoverArt';
import { FEEDS, PLACEHOLDER, type CoverSpec, type Episode } from './hearthData';
import { Skeleton, SkeletonCover, SkeletonGroup, type SkeletonGroupValue } from './Skeleton';
import {
  HOLD,
  IDLE_S,
  LOAD_S,
  LOOP,
  MODE_AUTO,
  MODE_MANUAL,
  MODE_RETURN,
  OFFSET_SPRING,
  RESOLVE_TOTAL,
  SETTLED,
  SLANT,
  SWAP_AFTER,
  THRESHOLD,
  TICKS,
  clamp,
  clamp01,
  filmData,
  filmFinger,
  filmGhost,
  filmOffset,
  filmRelease,
  filmResolve,
  filmReveal,
  filmSinceHide,
  filmSinceShow,
  filmTarget,
  pullFor,
  seg,
  tickArming,
  tickSpinning,
} from './skeletonMotion';

const PAGE = '#15120f';
const CARD = '#211c17';
const RAISED = '#2a231c';
const INK = '#f5ede1';
const MUTE = '#a3978a';
const FAINT = '#6f665c';
const EMBER = '#f0803c';
const HAIR = 'rgba(245,232,214,0.09)';
const BAR = 'rgba(245,232,214,0.11)';
const SHINE = 'rgba(255,240,222,0.22)';
const SHINE_CLEAR = 'rgba(255,240,222,0)';
const BLOCK = '#2e2821';
const BLOCK_CLEAR = 'rgba(46,40,33,0)';

const SIDE = 16;
const DOT = 46; // the ghost finger
const SPINNER = 28;
const SPIN_FADE = 0.3; // s the spinner takes to leave once the content lands
const RETURN_LOAD_S = 0.9; // s of skeleton when the film brings the first feed back by itself

const NOW_PLAYING: CoverSpec = { motif: 'dots', ground: ['#20424a', '#17323a', '#10242b'], ink: '#8fd0c4', accent: '#f0c987' };
const TABS: { symbol: SFSymbol; label: string }[] = [
  { symbol: 'house.fill', label: 'Home' },
  { symbol: 'magnifyingglass', label: 'Search' },
  { symbol: 'square.stack.fill', label: 'Library' },
  { symbol: 'arrow.down.circle', label: 'Downloads' },
];
const TICK_INDEXES = Array.from({ length: TICKS }, (_, i) => i);

function armTick() {
  void Haptics.selectionAsync();
}

function landTick() {
  void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
}

function put(sv: SharedValue<number>, v: number) {
  'worklet';
  if (sv.get() !== v) sv.set(v);
}

type Props = { autoPlay?: boolean };

/**
 * A podcast home whose loading state turns into its content instead of being swapped for it.
 * Pull down to refresh: every line becomes a bar with one band of light crossing all of them; then,
 * top to bottom, each bar stretches to the exact width of the text it stood for, the text fades in
 * inside it and the bar dissolves, while the covers open through a soft radial wipe.
 */
export function SkeletonContentResolve({ autoPlay = true }: Props) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const film = autoPlay && !reduced;
  const compact = height < 740;

  const contentW = Math.min(width, 480);
  const w = contentW - SIDE * 2;
  const top = insets.top + (compact ? 2 : 6);
  const headerH = compact ? 52 : 60;
  const gap = compact ? 10 : 14;
  const tabH = 49 + Math.max(insets.bottom, 6);
  const miniH = compact ? 54 : 60;
  const miniBottom = tabH + 8;
  const listTop = top + headerH + gap;
  const listH = height - listTop - miniBottom - miniH - (compact ? 6 : 10);
  const featuredH = compact ? 148 : 160;
  const sectionGap = compact ? 12 : 18;
  const sectionH = compact ? 26 : 28;
  const rowH = clamp(Math.floor((listH - 4 - featuredH - sectionGap - sectionH - 4) / 4), 56, 84);
  const coverSize = Math.min(60, rowH - 18);
  const bigCover = featuredH - 28;
  const ghostY = listTop + featuredH * 0.55;

  const [data, setData] = useState(0);
  const feed = FEEDS[data];

  const container = useRef<View>(null);

  const mode = useSharedValue(film ? MODE_AUTO : MODE_MANUAL);
  const clock = useSharedValue(0); // the frame clock, seconds
  const loopStart = useSharedValue(-1);
  const tau = useSharedValue(0); // loop time, seconds
  const lastTouch = useSharedValue(0);
  const holding = useSharedValue(0);

  const sinceHide = useSharedValue(-1);
  const sinceShow = useSharedValue(SETTLED);
  const offset = useSharedValue(0); // pt the list is down
  const reveal = useSharedValue(0); // how armed the spinner is, 0 … 1
  const ghost = useSharedValue(0);
  const fingerDy = useSharedValue(0);
  const wantData = useSharedValue(0); // which feed should be mounted

  // a refresh started by a finger runs the same two clocks from timestamps
  const hideAt = useSharedValue(-1);
  const showAt = useSharedValue(-1);
  const target = useSharedValue(0);
  const landed = useSharedValue(1);

  /** Start a refresh: bars now, the `feed` after `load` seconds. */
  const begin = useCallback(
    (now: number, next: number, load: number) => {
      'worklet';
      hideAt.set(now);
      showAt.set(now + load);
      target.set(next);
      landed.set(0);
      sinceHide.set(0);
      sinceShow.set(-1);
    },
    [hideAt, landed, showAt, sinceHide, sinceShow, target],
  );

  useFrameCallback((frame) => {
    'worklet';
    const now = frame.timestamp / 1000;
    clock.set(now);
    const m = mode.get();
    if (m === MODE_AUTO) {
      if (!film) {
        mode.set(MODE_MANUAL);
        return;
      }
      if (loopStart.get() < 0) loopStart.set(now);
      const t = (now - loopStart.get()) % LOOP;
      put(tau, t);
      put(sinceHide, filmSinceHide(t));
      put(sinceShow, filmSinceShow(t));
      put(offset, filmOffset(t));
      put(reveal, filmReveal(t));
      put(ghost, filmGhost(t));
      put(fingerDy, filmFinger(t));
      put(wantData, filmData(t));
      return;
    }
    const hidden = hideAt.get();
    const shown = showAt.get();
    const ss = now >= shown ? Math.min(SETTLED, now - shown) : -1;
    if (ss < 0 && hidden >= 0) put(sinceHide, now - hidden);
    put(sinceShow, ss);
    if (hidden >= 0 && now - hidden >= SWAP_AFTER) put(wantData, target.get());
    if (ss >= 0 && landed.get() === 0) {
      landed.set(1);
      offset.set(withSpring(0, OFFSET_SPRING));
      scheduleOnRN(landTick);
    }
    if (!film || holding.get() > 0) return;
    const idle = ss >= SETTLED;
    if (m === MODE_MANUAL && idle && now - lastTouch.get() > IDLE_S) {
      mode.set(MODE_RETURN);
      offset.set(withSpring(0, OFFSET_SPRING));
      reveal.set(withTiming(0, { duration: 160 }));
      // the film starts on the first feed: bring it back the way a refresh would
      if (target.get() !== 0) begin(now, 0, RETURN_LOAD_S);
    } else if (m === MODE_RETURN && idle && target.get() === 0 && wantData.get() === 0) {
      loopStart.set(now);
      put(tau, 0);
      mode.set(MODE_AUTO);
    }
  });

  /** A finger anywhere: the film stops, and whatever it was loading goes on loading. */
  const take = useCallback(() => {
    'worklet';
    const now = clock.get();
    lastTouch.set(now);
    const m = mode.get();
    if (m === MODE_MANUAL) return;
    if (m === MODE_AUTO) {
      const t = tau.get();
      const r = filmRelease(t);
      if (r < 0) {
        hideAt.set(-1);
        showAt.set(-1);
        target.set(0);
        landed.set(1);
        offset.set(withSpring(0, OFFSET_SPRING));
      } else {
        const s = filmResolve(t);
        hideAt.set(now - (t - r));
        showAt.set(now + (s - t));
        target.set(filmTarget(t));
        landed.set(t >= s ? 1 : 0);
        offset.set(withSpring(t < s ? HOLD : 0, OFFSET_SPRING));
      }
      reveal.set(withTiming(0, { duration: 160 }));
      ghost.set(withTiming(0, { duration: 160 }));
    }
    mode.set(MODE_MANUAL);
  }, [clock, ghost, hideAt, landed, lastTouch, mode, offset, reveal, showAt, target, tau]);

  const pan = Gesture.Pan()
    .activeOffsetY(8)
    .failOffsetX([-24, 24])
    .onBegin(() => {
      take();
    })
    .onStart(() => {
      holding.set(1);
    })
    .onChange((e) => {
      lastTouch.set(clock.get());
      // nothing to pull while a refresh is loading or still landing
      if (sinceShow.get() < RESOLVE_TOTAL) return;
      const o = pullFor(e.translationY);
      offset.set(o);
      reveal.set(clamp01(o / THRESHOLD));
    })
    .onFinalize(() => {
      if (holding.get() <= 0) return;
      holding.set(0);
      const now = clock.get();
      lastTouch.set(now);
      if (sinceShow.get() < RESOLVE_TOTAL) return;
      if (offset.get() >= THRESHOLD) {
        begin(now, 1 - target.get(), LOAD_S);
        offset.set(withSpring(HOLD, OFFSET_SPRING));
      } else {
        offset.set(withSpring(0, OFFSET_SPRING));
        reveal.set(withTiming(0, { duration: 180 }));
      }
    });

  // a tick when the pull arms the refresh
  useAnimatedReaction(
    () => holding.get() > 0 && reveal.get() >= 1,
    (now, before) => {
      if (now && !before) scheduleOnRN(armTick);
    },
  );

  // mount the feed the clocks ask for (it changes while the bars hide the text)
  useAnimatedReaction(
    () => wantData.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setData, now);
    },
  );

  const refresh = useCallback(() => {
    take();
    if (sinceShow.get() < RESOLVE_TOTAL) return;
    begin(clock.get(), 1 - target.get(), LOAD_S);
    offset.set(withSpring(HOLD, OFFSET_SPRING));
  }, [begin, clock, offset, sinceShow, take, target]);

  const group = useMemo<SkeletonGroupValue>(
    () => ({
      sinceHide,
      sinceShow,
      container,
      span: contentW + SLANT * listH,
      bar: BAR,
      shine: SHINE,
      shineClear: SHINE_CLEAR,
      block: BLOCK,
      blockClear: BLOCK_CLEAR,
    }),
    [contentW, listH, sinceHide, sinceShow],
  );

  const listStyle = useAnimatedStyle(() => ({ transform: [{ translateY: offset.get() }] }));
  const spinnerStyle = useAnimatedStyle(() => {
    const ss = sinceShow.get();
    const spinning = sinceHide.get() >= 0 && ss < SPIN_FADE;
    const a = spinning ? (ss < 0 ? 1 : 1 - seg(ss, 0, SPIN_FADE)) : reveal.get() > 0 ? 1 : 0;
    return { opacity: a, transform: [{ scale: 0.8 + 0.2 * a }] };
  });
  const ghostStyle = useAnimatedStyle(() => ({
    opacity: ghost.get() * 0.9,
    transform: [{ translateX: width / 2 - DOT / 2 }, { translateY: ghostY + fingerDy.get() - DOT / 2 }],
  }));

  const titleSize = compact ? 28 : 32;
  const featuredTitle = compact ? 18 : 19;

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={{ width: contentW, alignSelf: 'center', flex: 1 }}>
        <View style={[styles.header, { marginTop: top, height: headerH }]}>
          <View>
            <Text style={styles.kicker}>Tuesday evening</Text>
            <Text style={[styles.title, { fontSize: titleSize, lineHeight: titleSize + 5 }]}>Listen now</Text>
          </View>
          <LinearGradient colors={['#f3a25c', '#d9653a', '#8a3a3f']} end={{ x: 0.9, y: 1 }} start={{ x: 0.1, y: 0 }} style={styles.avatar}>
            <Text style={styles.avatarText}>MR</Text>
          </LinearGradient>
        </View>

        <GestureDetector gesture={pan}>
          <View
            accessibilityActions={[{ name: 'refresh', label: 'Refresh' }]}
            onAccessibilityAction={refresh}
            style={{ marginTop: gap, height: listH, overflow: 'hidden' }}>
            <Animated.View pointerEvents="none" style={[styles.spinner, { left: contentW / 2 - SPINNER / 2 }, spinnerStyle]}>
              {TICK_INDEXES.map((i) => (
                <Tick index={i} key={i} reveal={reveal} sinceHide={sinceHide} sinceShow={sinceShow} />
              ))}
            </Animated.View>

            <Animated.View style={listStyle}>
              <View ref={container} style={styles.list}>
                <SkeletonGroup value={group}>
                  <View style={[styles.featured, { height: featuredH }]}>
                    <SkeletonCover order={0} radius={16} size={bigCover}>
                      <CoverArt size={bigCover} spec={feed.cover} />
                    </SkeletonCover>
                    <View style={styles.featuredText}>
                      <View style={styles.featuredLines}>
                        <Skeleton barHeight={10} order={0.5} placeholder={PLACEHOLDER.eyebrow}>
                          <Text numberOfLines={1} style={styles.eyebrow}>
                            {feed.eyebrow}
                          </Text>
                        </Skeleton>
                        <Skeleton barHeight={15} order={1} placeholder={PLACEHOLDER.titleA}>
                          <Text numberOfLines={1} style={[styles.featuredTitle, { fontSize: featuredTitle, lineHeight: featuredTitle + 4 }]}>
                            {feed.title[0]}
                          </Text>
                        </Skeleton>
                        <Skeleton barHeight={15} order={1.5} placeholder={PLACEHOLDER.titleB}>
                          <Text numberOfLines={1} style={[styles.featuredTitle, { fontSize: featuredTitle, lineHeight: featuredTitle + 4 }]}>
                            {feed.title[1]}
                          </Text>
                        </Skeleton>
                        <Skeleton barHeight={10} order={2.2} placeholder={PLACEHOLDER.meta}>
                          <Text numberOfLines={1} style={styles.meta}>
                            {feed.show} · {feed.minutes} min
                          </Text>
                        </Skeleton>
                      </View>
                      <Skeleton barHeight={30} order={3} placeholder={PLACEHOLDER.play} radius={15}>
                        <View style={styles.play}>
                          <SymbolView name="play.fill" size={11} tintColor={PAGE} />
                          <Text style={styles.playText}>Play</Text>
                        </View>
                      </Skeleton>
                    </View>
                  </View>

                  <View style={[styles.section, { marginTop: sectionGap, height: sectionH }]}>
                    <Text style={styles.sectionTitle}>Up next</Text>
                    <Text style={styles.sectionLink}>See all</Text>
                  </View>

                  <View style={styles.rows}>
                    {feed.episodes.map((episode, r) => (
                      <Row coverSize={coverSize} episode={episode} height={rowH} index={r} key={r} last={r === feed.episodes.length - 1} />
                    ))}
                  </View>
                </SkeletonGroup>
              </View>
            </Animated.View>
          </View>
        </GestureDetector>

        {/* what is playing stays put through a refresh */}
        <View style={[styles.mini, { height: miniH, bottom: miniBottom, left: SIDE, width: w }]}>
          <View style={styles.miniCover}>
            <Canvas style={{ width: miniH - 16, height: miniH - 16 }}>
              <CoverArt size={miniH - 16} spec={NOW_PLAYING} />
            </Canvas>
          </View>
          <View style={styles.miniText}>
            <Text numberOfLines={1} style={styles.miniTitle}>
              Night Swim Radio
            </Text>
            <Text numberOfLines={1} style={styles.miniMeta}>
              Low Tide Hours · 12:08 left
            </Text>
          </View>
          <SymbolView name="pause.fill" size={20} tintColor={INK} />
          <View style={styles.miniTrack}>
            <View style={styles.miniProgress} />
          </View>
        </View>

        <View style={[styles.tabs, { height: tabH, paddingBottom: Math.max(insets.bottom, 6) }]}>
          {TABS.map((tab, i) => (
            <View key={tab.label} style={styles.tab}>
              <SymbolView name={tab.symbol} size={22} tintColor={i === 0 ? EMBER : FAINT} />
              <Text style={[styles.tabLabel, i === 0 && styles.tabLabelOn]}>{tab.label}</Text>
            </View>
          ))}
        </View>
      </View>

      <Animated.View pointerEvents="none" style={[styles.dot, ghostStyle]} />
    </View>
  );
}

// ---------------------------------------------------------------- one episode row

type RowProps = { episode: Episode; index: number; height: number; coverSize: number; last: boolean };

function Row({ episode, index, height, coverSize, last }: RowProps) {
  const order = 4 + index * 2;
  return (
    <View style={[styles.row, { height }]}>
      <SkeletonCover order={order} radius={12} size={coverSize}>
        <CoverArt size={coverSize} spec={episode.cover} />
      </SkeletonCover>
      <View style={styles.rowText}>
        <Skeleton barHeight={12} order={order + 0.5} placeholder={PLACEHOLDER.rowTitle[index]}>
          <Text numberOfLines={1} style={styles.rowTitle}>
            {episode.title}
          </Text>
        </Skeleton>
        <Skeleton barHeight={9} order={order + 1} placeholder={PLACEHOLDER.rowMeta[index]}>
          <Text numberOfLines={1} style={styles.rowMeta}>
            {episode.show} · {episode.minutes} min
          </Text>
        </Skeleton>
      </View>
      <View>
        <Skeleton align="right" barHeight={9} order={order + 0.5} placeholder={PLACEHOLDER.day}>
          <Text style={styles.day}>{episode.day}</Text>
        </Skeleton>
      </View>
      {last ? null : <View style={[styles.rowRule, { left: coverSize + 12 }]} />}
    </View>
  );
}

// ---------------------------------------------------------------- the spinner

type TickProps = { index: number; reveal: SharedValue<number>; sinceHide: SharedValue<number>; sinceShow: SharedValue<number> };

/** One of the spinner's eight ticks: they appear one by one as the pull arms it, then chase each other. */
function Tick({ index, reveal, sinceHide, sinceShow }: TickProps) {
  const style = useAnimatedStyle(() => {
    const hidden = sinceHide.get();
    const spinning = hidden >= 0 && sinceShow.get() < SPIN_FADE;
    return { opacity: spinning ? tickSpinning(index, hidden) : tickArming(index, reveal.get()) };
  });
  return <Animated.View style={[styles.tick, { transform: [{ rotate: `${(index * 360) / TICKS}deg` }, { translateY: -8.5 }] }, style]} />;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: PAGE },
  header: { flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', paddingHorizontal: SIDE },
  kicker: { color: MUTE, fontSize: 14, lineHeight: 18, fontWeight: '500' },
  title: { color: INK, fontWeight: '700', letterSpacing: -0.5 },
  avatar: { width: 36, height: 36, borderRadius: 18, marginBottom: 3, alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: '#fff6e8', fontSize: 13, fontWeight: '700', letterSpacing: 0.2 },

  spinner: { position: 'absolute', top: 12, width: SPINNER, height: SPINNER },
  tick: { position: 'absolute', left: SPINNER / 2 - 1.25, top: SPINNER / 2 - 3.5, width: 2.5, height: 7, borderRadius: 1.25, backgroundColor: INK },
  list: { paddingHorizontal: SIDE, paddingTop: 4, backgroundColor: PAGE },

  featured: {
    flexDirection: 'row',
    padding: 14,
    borderRadius: 24,
    borderCurve: 'continuous',
    backgroundColor: CARD,
    boxShadow: '0 1px 0 rgba(255,240,220,0.07) inset, 0 1px 2px rgba(0,0,0,0.4), 0 14px 28px rgba(0,0,0,0.3)',
  },
  featuredText: { flex: 1, marginLeft: 14, justifyContent: 'space-between' },
  featuredLines: { gap: 3 },
  eyebrow: { color: EMBER, fontSize: 13, lineHeight: 16, fontWeight: '600' },
  featuredTitle: { color: INK, fontWeight: '700', letterSpacing: -0.3 },
  meta: { color: MUTE, fontSize: 13, lineHeight: 17, fontVariant: ['tabular-nums'] },
  play: { height: 30, paddingHorizontal: 14, borderRadius: 15, flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: INK },
  playText: { color: PAGE, fontSize: 14, fontWeight: '600' },

  section: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionTitle: { color: INK, fontSize: 20, fontWeight: '700', letterSpacing: -0.3 },
  sectionLink: { color: EMBER, fontSize: 15, fontWeight: '500' },

  rows: { marginTop: 4 },
  row: { flexDirection: 'row', alignItems: 'center' },
  rowText: { flex: 1, marginLeft: 12, marginRight: 10, gap: 3 },
  rowTitle: { color: INK, fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  rowMeta: { color: MUTE, fontSize: 13, lineHeight: 17, fontVariant: ['tabular-nums'] },
  day: { color: FAINT, fontSize: 13, lineHeight: 17, fontWeight: '500' },
  rowRule: { position: 'absolute', right: 0, bottom: 0, height: StyleSheet.hairlineWidth, backgroundColor: HAIR },

  mini: {
    position: 'absolute',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: RAISED,
    overflow: 'hidden',
    boxShadow: '0 1px 0 rgba(255,240,220,0.08) inset, 0 2px 4px rgba(0,0,0,0.4), 0 16px 30px rgba(0,0,0,0.38)',
  },
  miniCover: { borderRadius: 10, borderCurve: 'continuous', overflow: 'hidden' },
  miniText: { flex: 1, marginHorizontal: 10, gap: 1 },
  miniTitle: { color: INK, fontSize: 15, fontWeight: '600', letterSpacing: -0.2 },
  miniMeta: { color: MUTE, fontSize: 12.5, fontVariant: ['tabular-nums'] },
  miniTrack: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 2, backgroundColor: 'rgba(245,232,214,0.08)' },
  miniProgress: { width: '38%', height: 2, backgroundColor: EMBER },

  tabs: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    paddingTop: 7,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: HAIR,
    backgroundColor: '#191511',
  },
  tab: { flex: 1, alignItems: 'center', gap: 3 },
  tabLabel: { color: FAINT, fontSize: 10, fontWeight: '500' },
  tabLabelOn: { color: EMBER },

  dot: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: DOT,
    height: DOT,
    borderRadius: DOT / 2,
    backgroundColor: 'rgba(255,255,255,0.22)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.6)',
  },
});
