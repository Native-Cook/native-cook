// "Margin": a reading list whose rows can be peeked (long press) and popped into the article (swipe up).
// The peek itself is ./PeekPreview (reusable); this file is the screen, what is drawn inside the frame, and
// the autoPlay script's clock and ghost finger.
import * as Haptics from 'expo-haptics';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { SymbolView } from 'expo-symbols';
import { useEffect, useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, useWindowDimensions, View, type ViewProps } from 'react-native';
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useFrameCallback, useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { CoverArt, CoverThumb } from './CoverArt';
import { ACTIONS, ARTICLES, COPY, type ActionKey, type Article } from './data';
import {
  clamp,
  easeInOutCubic,
  easeOutCubic,
  EXCERPT_AT,
  EXCERPT_BLUR,
  GHOST_DISMISS,
  GHOST_FADE,
  GHOST_PRESS,
  GHOST_SCROLL,
  GHOST_SINK,
  GHOST_SIZE,
  GHOST_SWIPE_UP,
  ghostPull,
  LOOP,
  mix3,
  NAV_AT,
  NAV_DROP,
  RADIUS_ROW,
  RESUME_AFTER,
  REVEAL_SPAN,
  ROW_A,
  ROW_B,
  scriptPeek,
  scriptPop,
  scriptPress,
  scriptReveal,
  scriptRow,
  scriptScroll,
  seg,
  T,
} from './motion';
import { PeekActions, PeekBackdrop, peekClose, PeekFrame, PeekScene, peekSettle, peekTouch, PeekTrigger, usePeekPreview, type PeekState, type Rect } from './PeekPreview';

export { LOOP };
export type PeekPopPreviewProps = {
  /** Plays the 10.5 s loop until a finger takes over; it resumes 2 s after the last touch. */
  autoPlay?: boolean;
  articles?: Article[];
  onOpen?: (article: Article) => void;
  onAction?: (action: ActionKey, article: Article) => void;
};

const IOS = Platform.OS === 'ios';

// Palette: a cool paper, one ink, one green.
const DESK = '#EFEEE9';
const SHEET = '#FDFCF9';
const INK = '#1A1B1E';
const INK_BODY = '#2C2D32';
const INK_SOFT = '#7C7D84';
const LEAF = '#0F7B5F';
const DANGER = '#D23A2E';
const REST_SHADOW = '0 1px 2px rgba(26,24,18,0.08), 0 4px 10px rgba(26,24,18,0.05)';
const LIFT_SHADOW = '0 2px 6px rgba(26,24,18,0.16), 0 26px 52px rgba(26,24,18,0.30)';

// Layout, in points.
const SIDE = 16;
const MAX_COLUMN = 520;
const HEADER = 104;
const ROW_GAP = 8;
const ROW_MAX = 88;
const ROW_MIN = 72;
const FOOT = 60; // room for the "Save a link" pill
const COVER_INSET = 12;
const THUMB_RADIUS = 12;
const CARD_SIDE = 14;
const CARD_MAX = 400;
const CARD_PAD = 18;
const PAGE_PAD = 20;
const ACTIONS_GAP = 10;
const ACTIONS_WIDTH = 250;
const ACTION_ROW = 46;
// Title, meta and text are set once at the page's size and scaled down for the preview and the row, in the list
// too, so all three states are the same pixels and wrap the same way.
const TITLE_SIZE = 26;
const TITLE_LINE = 31;
const TITLE_ROW = 16 / TITLE_SIZE;
const TITLE_CARD = 21 / TITLE_SIZE;
const META_LINE = 18;
const BODY_SIZE = 17;
const BODY_LINE = 26;
const EXCERPT_LINES = 4; // how much of the first paragraph the preview shows
const NAV = 38;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

type Metrics = {
  width: number;
  height: number;
  rows: Rect[];
  rowHeight: number;
  thumb: number;
  card: Rect;
  coverHeight: number;
  full: Rect;
  heroHeight: number;
  textWidth: number;
  /** x, y in the row, the preview card and the page. */
  titleX: [number, number, number];
  titleY: [number, number, number];
  metaY: [number, number, number];
  contentY: [number, number];
  contentScale: number;
  /** The title's scale in the preview card (never wider than the card's text column). */
  titleCard: number;
  actions: { x: number; y: number };
  footer: number | null;
  navTop: number;
};

function measure(width: number, height: number, top: number, bottom: number, count: number): Metrics {
  const column = Math.min(width, MAX_COLUMN);
  const ox = (width - column) / 2;
  const listTop = top + 8 + HEADER;
  const room = height - Math.max(bottom, 12) - listTop;
  const gaps = ROW_GAP * (count - 1);
  let rowHeight = Math.floor((room - FOOT - gaps) / count);
  let footer: number | null = null;
  if (rowHeight >= ROW_MIN) {
    rowHeight = Math.min(rowHeight, ROW_MAX);
    const end = listTop + rowHeight * count + gaps;
    footer = end + (height - Math.max(bottom, 12) - end - 48) / 2;
  } else {
    rowHeight = Math.min(Math.floor((room - gaps) / count), ROW_MAX);
  }
  const rows: Rect[] = [];
  for (let i = 0; i < count; i += 1) rows.push({ x: ox + SIDE, y: listTop + i * (rowHeight + ROW_GAP), width: column - SIDE * 2, height: rowHeight });
  const thumb = rowHeight - 24;
  const textWidth = column - PAGE_PAD * 2;

  const cardWidth = Math.min(column - CARD_SIDE * 2, CARD_MAX);
  const coverHeight = Math.round(Math.min(cardWidth * 0.5, height * 0.21));
  const contentScale = (cardWidth - CARD_PAD * 2) / textWidth;
  const titleScale = Math.min(TITLE_CARD, contentScale);
  const titleCard = coverHeight + 14;
  const metaCard = titleCard + Math.round(TITLE_LINE * 2 * titleScale) + 5;
  const contentCard = metaCard + META_LINE + 10;
  const cardHeight = contentCard + Math.round(EXCERPT_LINES * BODY_LINE * contentScale) + 16;
  const actionsHeight = ACTION_ROW * ACTIONS.length;
  const stack = cardHeight + ACTIONS_GAP + actionsHeight;
  const cardTop = top + Math.max(8, (height - top - bottom - stack) / 2);
  const card = { x: (width - cardWidth) / 2, y: cardTop, width: cardWidth, height: cardHeight };

  const heroHeight = Math.round(height * 0.36);
  const titleFull = heroHeight + 20;
  const metaFull = titleFull + TITLE_LINE * 2 + 8;
  const titleRow = Math.round((rowHeight - (Math.round(TITLE_LINE * 2 * TITLE_ROW) + 4 + META_LINE)) / 2);
  return {
    width,
    height,
    rows,
    rowHeight,
    thumb,
    card,
    coverHeight,
    full: { x: 0, y: 0, width, height },
    heroHeight,
    textWidth,
    titleX: [COVER_INSET + thumb + 12, CARD_PAD, ox + PAGE_PAD],
    titleY: [titleRow, titleCard, titleFull],
    metaY: [titleRow + Math.round(TITLE_LINE * 2 * TITLE_ROW) + 4, metaCard, metaFull],
    contentY: [contentCard, metaFull + META_LINE + 18],
    contentScale,
    titleCard: titleScale,
    actions: { x: card.x, y: card.y + cardHeight + ACTIONS_GAP },
    footer,
    navTop: top + 4,
  };
}

function impact() {
  if (Platform.OS !== 'web') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
}

/** A touch that closes (an action, the back button): it takes over from the script first. */
function closeFromTouch(state: PeekState, reduced: boolean) {
  'worklet';
  peekTouch(state, reduced);
  peekClose(state, 0, reduced);
}

/**
 * The ghost finger at loop time t: [x, y, opacity, pressed 0…1].
 * a and b are where it lands on rows A and B; s where it starts to scroll; d where it starts the pull down.
 */
function ghostAt(t: number, ax: number, ay: number, bx: number, by: number, sx: number, sy: number, dx: number, dy: number) {
  'worklet';
  if (t < T.releaseA + 0.45) {
    const shown = seg(t, T.appearA, T.appearA + GHOST_FADE) * (1 - seg(t, T.releaseA, T.releaseA + GHOST_FADE));
    return [ax, ay - GHOST_SWIPE_UP * ghostPull(seg(t, T.swipeA, T.releaseA)), shown, seg(t, T.downA - 0.08, T.downA)];
  }
  if (t < T.scrollEnd + 0.4) {
    const shown = seg(t, T.scrollStart - 0.3, T.scrollStart - 0.12) * (1 - seg(t, T.scrollEnd + 0.05, T.scrollEnd + 0.05 + GHOST_FADE));
    return [sx, sy - GHOST_SCROLL * easeInOutCubic(seg(t, T.scrollStart, T.scrollEnd)), shown, seg(t, T.scrollStart - 0.1, T.scrollStart)];
  }
  if (t < T.releaseD + 0.45) {
    const shown = seg(t, T.pullStart - 0.3, T.pullStart - 0.12) * (1 - seg(t, T.releaseD, T.releaseD + GHOST_FADE));
    return [dx, dy + GHOST_DISMISS * ghostPull(seg(t, T.pullStart, T.releaseD)), shown, seg(t, T.pullStart - 0.1, T.pullStart)];
  }
  const shown = seg(t, T.appearB, T.appearB + GHOST_FADE) * (1 - seg(t, T.releaseB, T.releaseB + GHOST_FADE));
  return [bx, by + GHOST_SINK * ghostPull(seg(t, T.pullB, T.releaseB)), shown, seg(t, T.downB - 0.08, T.downB)];
}

export function PeekPopPreview({ autoPlay = true, articles = ARTICLES, onOpen, onAction }: PeekPopPreviewProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotionPreference();
  const playing = autoPlay && !reducedMotion;
  const m = measure(width, height, insets.top, insets.bottom, articles.length);
  const state = usePeekPreview();
  const { active, press, peek, sink, pop, reveal, scroll, maxScroll, wants, held, touches, scripted } = state;

  // autoPlay: one clock, and what a touch does to it.
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const idle = useSharedValue(0);
  const seen = useSharedValue(0);
  // The height of each article's text at the page's size, for how far the page scrolls.
  const textHeights = useSharedValue<number[]>(articles.map(() => 900));
  const rowA = Math.min(ROW_A, articles.length - 1);
  const rowB = Math.min(ROW_B, articles.length - 1);
  const pageBottom = Math.max(insets.bottom, 12) + 28;
  const contentTop = m.contentY[1];

  useEffect(() => {
    clock.set(0);
    mode.set(playing ? MODE_RETURN : MODE_MANUAL);
    scripted.set(0);
    scheduleOnUI(peekClose, state, 0, true);
    // `state` is a stable bag of shared values.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [playing]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0) return;
    const out = active.get();
    if (out >= 0) maxScroll.set(Math.max(0, contentTop + (textHeights.get()[out] ?? 900) + pageBottom - height));

    // A touch takes over: the script lets go and whatever is out settles where it is nearest to.
    if (touches.get() !== seen.get()) {
      seen.set(touches.get());
      idle.set(0);
      if (scripted.get() === 1) {
        scripted.set(0);
        peekSettle(state, reducedMotion);
      }
      mode.set(MODE_MANUAL);
    }
    if (mode.get() === MODE_MANUAL) {
      if (!playing) return;
      if (held.get() === 0) idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        peekClose(state, 0, false);
        mode.set(MODE_RETURN);
      }
      return;
    }
    if (mode.get() === MODE_RETURN) {
      // Back at rest: the loop starts again from 0.
      if (active.get() < 0 && playing) {
        clock.set(0);
        scripted.set(1);
        mode.set(MODE_AUTO);
      }
      return;
    }
    if (!playing) return;

    let t = clock.get() + dt;
    if (t >= LOOP) t -= LOOP;
    clock.set(t);
    const row = scriptRow(t) === ROW_A ? rowA : rowB;
    const pressed = scriptPress(t);
    const peeked = scriptPeek(t);
    const popped = scriptPop(t);
    press.set(pressed);
    peek.set(peeked);
    sink.set(0);
    pop.set(popped);
    reveal.set(scriptReveal(t));
    scroll.set(Math.min(scriptScroll(t), maxScroll.get()));
    wants.set(0);
    active.set(pressed + Math.abs(peeked) + Math.abs(popped) > 0.0005 ? row : -1);
  });

  const ghost = useAnimatedStyle(() => {
    const a = m.rows[rowA];
    const b = m.rows[rowB];
    const g = ghostAt(
      clock.get(),
      a.x + a.width * 0.55,
      a.y + a.height / 2,
      b.x + b.width * 0.7,
      b.y + b.height / 2,
      width * 0.62,
      height * 0.66,
      width * 0.5,
      height * 0.3,
    );
    return {
      opacity: scripted.get() === 1 ? g[2] : 0,
      transform: [{ translateX: g[0] - GHOST_SIZE / 2 }, { translateY: g[1] - GHOST_SIZE / 2 }, { scale: 1 - GHOST_PRESS * g[3] }],
    };
  });

  // Inside the frame: the page scrolls, the nav settles, the preview's last line fades out.
  const page = useAnimatedStyle(() => ({ transform: [{ translateY: -scroll.get() }] }));
  const nav = useAnimatedStyle(() => {
    const u = easeOutCubic(seg(pop.get(), NAV_AT[0], NAV_AT[1]));
    return { opacity: u, transform: [{ translateY: -NAV_DROP * (1 - u) }] };
  });
  const navTouch = useAnimatedProps<ViewProps>(() => ({ pointerEvents: pop.get() > 0.8 ? 'box-none' : 'none' }));
  const fade = useAnimatedStyle(() => ({
    opacity: seg(peek.get() - sink.get(), 0.4, 0.8) * (1 - seg(pop.get(), 0.05, 0.45)),
  }));

  // Over the open page's cover the status bar turns light; it flips once, as the page takes the screen.
  const [onPage, setOnPage] = useState(false);
  useAnimatedReaction(
    () => pop.get() > 0.6,
    (now, before) => {
      if (now !== before) scheduleOnRN(setOnPage, now);
    },
  );

  const bump = () => touches.set(touches.get() + 1);
  const closeNow = () => {
    impact();
    scheduleOnUI(closeFromTouch, state, reducedMotion);
  };
  const act = (key: string) => {
    impact();
    const article = articles[active.get()];
    if (article && onAction) onAction(key as ActionKey, article);
    scheduleOnUI(closeFromTouch, state, reducedMotion);
  };
  const opened = () => {
    impact();
    const article = articles[active.get()];
    if (article && onOpen) onOpen(article);
  };
  const setTextHeight = (index: number, value: number) => {
    const next = textHeights.get().slice();
    next[index] = value;
    textHeights.set(next);
  };

  const minutes = articles.reduce((sum, article) => sum + article.minutes, 0);

  return (
    <View style={styles.screen} onTouchStart={bump}>
      <StatusBar style={onPage ? 'light' : 'dark'} animated />
      <PeekScene state={state}>
        <View style={[styles.header, { top: insets.top + 8, left: m.rows[0].x, width: m.rows[0].width }]}>
          <View style={styles.brandRow}>
            <Text style={styles.brand}>{COPY.brand}</Text>
            <Text style={styles.edit}>{COPY.edit}</Text>
          </View>
          <Text style={styles.heading} accessibilityRole="header">
            {COPY.title}
          </Text>
          <Text style={styles.count}>{`${articles.length} saved · ${minutes} min of reading`}</Text>
        </View>
        {articles.map((article, i) => (
          <PeekTrigger
            key={article.key}
            state={state}
            index={i}
            rect={m.rows[i]}
            reducedMotion={reducedMotion}
            onPeek={impact}
            onPop={opened}
            onClose={impact}
            accessibilityLabel={`${article.title}, ${article.source}, ${article.minutes} minutes`}>
            <View style={styles.row}>
              <RowFace article={article} m={m} />
            </View>
          </PeekTrigger>
        ))}
        {m.footer !== null ? (
          <View style={[styles.add, { top: m.footer, left: (width - 164) / 2 }]}>
            <SymbolView name="plus" size={15} weight="bold" tintColor={SHEET} fallback={<Text style={styles.addPlus}>+</Text>} />
            <Text style={styles.addLabel}>{COPY.add}</Text>
          </View>
        ) : null}
      </PeekScene>

      <PeekBackdrop state={state} reducedMotion={reducedMotion} onClose={impact} />
      <PeekActions
        state={state}
        anchor={m.actions}
        width={Math.min(ACTIONS_WIDTH, m.card.width)}
        actions={ACTIONS}
        rowHeight={ACTION_ROW}
        ink={INK}
        danger={DANGER}
        backgroundColor={SHEET}
        onAction={act}
      />
      <PeekFrame
        state={state}
        geometry={m}
        reducedMotion={reducedMotion}
        backgroundColor={SHEET}
        restShadow={REST_SHADOW}
        liftShadow={LIFT_SHADOW}
        onPop={opened}
        onClose={impact}>
        <Animated.View style={[StyleSheet.absoluteFill, page]}>
          {articles.map((article, i) => (
            <MorphFace key={article.key} article={article} index={i} m={m} state={state} onTextHeight={setTextHeight} />
          ))}
        </Animated.View>
        <Animated.View pointerEvents="none" style={[styles.fade, fade]}>
          <LinearGradient colors={['rgba(253,252,249,0)', 'rgba(253,252,249,0.92)', SHEET]} locations={[0, 0.6, 1]} style={StyleSheet.absoluteFill} />
        </Animated.View>
        <Animated.View style={[styles.nav, { top: 0, height: m.navTop + NAV + 28 }, nav]} animatedProps={navTouch}>
          <LinearGradient pointerEvents="none" colors={['rgba(10,10,12,0.36)', 'rgba(10,10,12,0)']} style={StyleSheet.absoluteFill} />
          <Pressable onPress={closeNow} hitSlop={8} accessibilityRole="button" accessibilityLabel={`Back to ${COPY.back}`} style={[styles.navButton, { top: m.navTop, left: m.titleX[2] - 6 }]}>
            <SymbolView name="chevron.left" size={17} weight="semibold" tintColor={INK} fallback={<Text style={styles.navGlyph}>‹</Text>} />
          </Pressable>
          <View style={[styles.navButton, { top: m.navTop, right: m.titleX[2] - 6 }]}>
            <SymbolView name="textformat.size" size={17} weight="medium" tintColor={INK} fallback={<Text style={styles.navGlyph}>Aa</Text>} />
          </View>
        </Animated.View>
      </PeekFrame>

      <Animated.View pointerEvents="none" style={[styles.ghost, ghost]} />
    </View>
  );
}

function metaLine(article: Article) {
  return `${article.source} · ${article.minutes} min read`;
}

/** A row at rest. It is drawn exactly as the frame is at peek 0, so the hand-over is invisible. */
function RowFace({ article, m }: { article: Article; m: Metrics }) {
  return (
    <>
      <View style={{ position: 'absolute', left: COVER_INSET, top: (m.rowHeight - m.thumb) / 2 }}>
        <CoverThumb cover={article.cover} width={m.width} height={m.heroHeight} size={m.thumb} radius={THUMB_RADIUS} />
      </View>
      <Text
        numberOfLines={2}
        style={[styles.title, { width: m.textWidth, transform: [{ translateX: m.titleX[0] }, { translateY: m.titleY[0] }, { scale: TITLE_ROW }] }]}>
        {article.title}
      </Text>
      <Text numberOfLines={1} style={[styles.meta, { width: m.textWidth, transform: [{ translateX: m.titleX[0] }, { translateY: m.metaY[0] }] }]}>
        {metaLine(article)}
      </Text>
      <Text style={[styles.topic, { top: m.metaY[0] }]}>{article.topic}</Text>
    </>
  );
}

/** The same article inside the frame: every piece travels row → preview → page on the shared values. */
function MorphFace({ article, index, m, state, onTextHeight }: { article: Article; index: number; m: Metrics; state: PeekState; onTextHeight: (index: number, height: number) => void }) {
  const { active, peek, sink, pop, reveal } = state;
  const { thumb, card, full, coverHeight, heroHeight, rowHeight, titleX, titleY, metaY, contentY, contentScale, titleCard } = m;

  const shown = useAnimatedStyle(() => ({ opacity: active.get() === index ? 1 : 0 }));
  const cover = useAnimatedStyle(() => {
    const pk = peek.get() - sink.get();
    const pp = pop.get();
    return {
      width: Math.max(1, mix3(thumb, card.width, full.width, pk, pp)),
      height: Math.max(1, mix3(thumb, coverHeight, heroHeight, pk, pp)),
      borderRadius: Math.max(0, mix3(THUMB_RADIUS, 0, 0, clamp(pk, 0, 1), clamp(pp, 0, 1))),
      transform: [{ translateX: mix3(COVER_INSET, 0, 0, pk, pp) }, { translateY: mix3((rowHeight - thumb) / 2, 0, 0, pk, pp) }],
    };
  });
  // The drawing always covers its box, centred: the cover grows as a shared element.
  const art = useAnimatedStyle(() => {
    const pk = peek.get() - sink.get();
    const pp = pop.get();
    const w = Math.max(1, mix3(thumb, card.width, full.width, pk, pp));
    const h = Math.max(1, mix3(thumb, coverHeight, heroHeight, pk, pp));
    return { transform: [{ translateX: (w - full.width) / 2 }, { translateY: (h - heroHeight) / 2 }, { scale: Math.max(w / full.width, h / heroHeight) }] };
  });
  const title = useAnimatedStyle(() => {
    const pk = peek.get() - sink.get();
    const pp = pop.get();
    return {
      transform: [
        { translateX: mix3(titleX[0], titleX[1], titleX[2], pk, pp) },
        { translateY: mix3(titleY[0], titleY[1], titleY[2], pk, pp) },
        { scale: mix3(TITLE_ROW, titleCard, 1, pk, pp) },
      ],
    };
  });
  const meta = useAnimatedStyle(() => {
    const pk = peek.get() - sink.get();
    const pp = pop.get();
    return { transform: [{ translateX: mix3(titleX[0], titleX[1], titleX[2], pk, pp) }, { translateY: mix3(metaY[0], metaY[1], metaY[2], pk, pp) }] };
  });
  const topic = useAnimatedStyle(() => ({ opacity: 1 - seg(peek.get() - sink.get(), 0, 0.3) }));
  // The first paragraph comes out of a blur as the preview lands; on the page it is simply there.
  const content = useAnimatedStyle(() => {
    const pk = peek.get() - sink.get();
    const pp = pop.get();
    const lands = easeOutCubic(seg(reveal.get(), EXCERPT_AT, EXCERPT_AT + REVEAL_SPAN)) * seg(pk, 0.45, 0.8);
    const u = Math.max(lands, seg(pp, 0, 0.3));
    return {
      opacity: u,
      filter: [{ blur: (1 - u) * EXCERPT_BLUR }] as unknown as string,
      transform: [
        { translateX: mix3(titleX[1], titleX[1], titleX[2], pk, pp) },
        { translateY: mix3(contentY[0] - 10, contentY[0], contentY[1], pk, pp) },
        { scale: mix3(contentScale, contentScale, 1, pk, pp) },
      ],
    };
  });

  return (
    <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, shown]}>
      <Animated.View style={[styles.cover, cover]}>
        <Animated.View style={[{ position: 'absolute', left: 0, top: 0, width: full.width, height: heroHeight }, art]}>
          <CoverArt cover={article.cover} width={full.width} height={heroHeight} />
        </Animated.View>
      </Animated.View>
      <Animated.Text numberOfLines={2} style={[styles.title, { width: m.textWidth }, title]}>
        {article.title}
      </Animated.Text>
      <Animated.Text numberOfLines={1} style={[styles.meta, { width: m.textWidth }, meta]}>
        {metaLine(article)}
      </Animated.Text>
      <Animated.Text style={[styles.topic, { top: metaY[0] }, topic]}>{article.topic}</Animated.Text>
      <Animated.View style={[styles.content, { width: m.textWidth }, content]} onLayout={(event) => onTextHeight(index, event.nativeEvent.layout.height)}>
        <Text style={styles.body}>{article.excerpt}</Text>
        {article.body.map((paragraph, i) => (
          <Text key={i} style={[styles.body, styles.paragraph]}>
            {paragraph}
          </Text>
        ))}
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: DESK },
  header: { position: 'absolute', height: HEADER },
  brandRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', paddingHorizontal: 4 },
  brand: { fontSize: 15, fontWeight: '700', color: LEAF, letterSpacing: -0.2 },
  edit: { fontSize: 16, fontWeight: '500', color: LEAF },
  heading: { marginTop: 6, paddingHorizontal: 4, fontSize: 33, lineHeight: 38, fontWeight: '800', color: INK, letterSpacing: -0.8 },
  count: { marginTop: 2, paddingHorizontal: 4, fontSize: 14, fontWeight: '500', color: INK_SOFT, fontVariant: ['tabular-nums'] },
  row: { flex: 1, borderRadius: RADIUS_ROW, borderCurve: 'continuous', backgroundColor: SHEET, boxShadow: REST_SHADOW },
  cover: { position: 'absolute', left: 0, top: 0, overflow: 'hidden', borderCurve: 'continuous' },
  title: {
    position: 'absolute',
    left: 0,
    top: 0,
    transformOrigin: 'left top',
    fontSize: TITLE_SIZE,
    lineHeight: TITLE_LINE,
    fontWeight: '800',
    color: INK,
    letterSpacing: -0.6,
  },
  meta: {
    position: 'absolute',
    left: 0,
    top: 0,
    height: META_LINE,
    fontSize: 13,
    lineHeight: META_LINE,
    fontWeight: '500',
    color: INK_SOFT,
    fontVariant: ['tabular-nums'],
  },
  topic: { position: 'absolute', right: 14, height: META_LINE, fontSize: 12, lineHeight: META_LINE, fontWeight: '600', color: LEAF },
  content: { position: 'absolute', left: 0, top: 0, transformOrigin: 'left top' },
  body: { fontSize: BODY_SIZE, lineHeight: BODY_LINE, color: INK_BODY, letterSpacing: -0.2 },
  paragraph: { marginTop: 16 },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 56 },
  nav: { position: 'absolute', left: 0, right: 0 },
  navButton: {
    position: 'absolute',
    width: NAV,
    height: NAV,
    borderRadius: NAV / 2,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(253,252,249,0.90)',
    boxShadow: '0 1px 2px rgba(10,10,12,0.16), 0 6px 14px rgba(10,10,12,0.14)',
  },
  navGlyph: { fontSize: IOS ? 17 : 15, fontWeight: '600', color: INK },
  add: {
    position: 'absolute',
    width: 164,
    height: 48,
    borderRadius: 24,
    backgroundColor: INK,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    boxShadow: '0 1px 2px rgba(26,24,18,0.20), 0 10px 22px rgba(26,24,18,0.18)',
  },
  addPlus: { fontSize: 18, fontWeight: '700', color: SHEET },
  addLabel: { fontSize: 16, fontWeight: '600', color: SHEET, letterSpacing: -0.2 },
  ghost: {
    position: 'absolute',
    left: 0,
    top: 0,
    width: GHOST_SIZE,
    height: GHOST_SIZE,
    borderRadius: GHOST_SIZE / 2,
    backgroundColor: 'rgba(26,27,30,0.20)',
    borderWidth: 1.5,
    borderColor: 'rgba(255,255,255,0.9)',
  },
});
