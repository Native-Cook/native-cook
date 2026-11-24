// "Longhand": a long read whose words ink in as you scroll. One scroll value drives the front, the progress line and
// the minutes left. The text is recorded once per paragraph (./page); a frame only masks the paragraph at the front.
import { BlendMode, Canvas, Picture, Skia, type SkCanvas, type SkPaint } from '@shopify/react-native-skia';
import { LinearGradient } from 'expo-linear-gradient';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import { StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import Animated, {
  scrollTo,
  useAnimatedReaction,
  useAnimatedRef,
  useAnimatedScrollHandler,
  useAnimatedStyle,
  useDerivedValue,
  useFrameCallback,
  useSharedValue,
  type SharedValue,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { scheduleOnRN } from 'react-native-worklets';

import { useReducedMotionPreference } from '@/shared/useReducedMotionPreference';

import { ARTICLE, type Article } from './data';
import { easeInOutCubic, FADE_WORDS, frontAt, ghostOpacityAt, ghostTravelAt, LOOP, mix, RESUME_AFTER, RETURN_SECONDS, scriptAt, seg, wordInk, WORDS_PER_MINUTE } from './motion';
import { type Block, makePage, type Page, type PageColors } from './page';

export { LOOP };
export type ScrollReadArticle = Article;
export type ScrollReadHighlightProps = {
  /** Plays the 10 s scripted scroll until a finger takes over; it carries on 2 s after the last touch. */
  autoPlay?: boolean;
  /** The article to read. */
  article?: ScrollReadArticle;
};

const BACKGROUND = '#15171B';
const INK = '#F1ECE2';
const AMBER = '#E9B872';
const COLORS: PageColors = {
  ink: INK,
  dim: 'rgba(241,236,226,0.26)',
  soft: '#9A988F',
  coverTop: '#2B3346',
  coverMid: '#B0705A',
  coverLow: '#EDB27C',
  hill: '#3A3A48',
  hillNear: '#262A35',
  sun: 'rgba(255,221,170,0.95)',
};

const SIDE = 22;
const MAX_WIDTH = 620;
const NAV = 48;

const MODE_AUTO = 0;
const MODE_MANUAL = 1;
const MODE_RETURN = 2;

function drawBlockInk(canvas: SkCanvas, block: Block, page: Page, front: number, mask: SkPaint, layer: SkPaint, width: number) {
  'worklet';
  canvas.drawPicture(block.dim);
  canvas.saveLayer(undefined, Skia.XYWHRect(0, -2, width, block.height + 4));
  canvas.drawPicture(block.ink);
  canvas.saveLayer(layer, Skia.XYWHRect(0, -2, width, block.height + 4));
  for (let word = block.first; word <= block.last; word += 1) {
    const alpha = wordInk(front, word);
    if (alpha <= 0) break;
    mask.setAlphaf(alpha);
    canvas.drawRect(Skia.XYWHRect(page.wordX[word] - 2, page.wordY[word] - block.y - 2, page.wordW[word] + 4, page.wordH[word] + 4), mask);
  }
  canvas.restore();
  canvas.restore();
}

function drawArticle(canvas: SkCanvas, page: Page, offset: number, viewport: number, front: number, allInked: boolean, mask: SkPaint, layer: SkPaint, width: number) {
  'worklet';
  canvas.save();
  canvas.translate(0, -offset);
  if (offset < page.headHeight) canvas.drawPicture(page.head);
  for (let i = 0; i < page.blocks.length; i += 1) {
    const block = page.blocks[i];
    if (block.y + block.height < offset - 4 || block.y > offset + viewport + 4) continue;
    canvas.save();
    canvas.translate(0, block.y);
    if (allInked || front >= block.last + FADE_WORDS) canvas.drawPicture(block.ink);
    else if (front <= block.first) canvas.drawPicture(block.dim);
    else drawBlockInk(canvas, block, page, front, mask, layer, width);
    canvas.restore();
  }
  canvas.restore();
}

function MinutesLeft({ minutes, initial }: { minutes: SharedValue<number>; initial: number }) {
  const [shown, setShown] = useState(initial);
  useAnimatedReaction(
    () => minutes.get(),
    (now, before) => {
      if (now !== before) scheduleOnRN(setShown, now);
    },
  );
  return <Text style={styles.minutes}>{shown === 0 ? 'Finished' : `${shown} min left`}</Text>;
}

export function ScrollReadHighlight({ autoPlay = true, article = ARTICLE }: ScrollReadHighlightProps) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reducedMotion = useReducedMotionPreference();
  const playing = autoPlay && !reducedMotion;

  const navHeight = insets.top + NAV;
  const viewport = Math.max(height - navHeight, 1);
  const column = Math.min(width - SIDE * 2, MAX_WIDTH);
  const left = (width - column) / 2;
  const bodySize = width >= 390 ? 20 : 18;
  const bodyLeading = width >= 390 ? 31 : 28;

  const page = useMemo(() => makePage(article, left, column, bodySize, bodyLeading, COLORS), [article, left, column, bodySize, bodyLeading]);
  const contentHeight = page.height + insets.bottom;
  const maxScroll = Math.max(contentHeight - viewport, 1);
  // The reading line starts three lines into the body and reaches the last line exactly at the end of the scroll.
  const readStart = page.firstBodyTop + bodyLeading * 3.4;
  const readEnd = page.lastBottom + 2 - maxScroll;
  const allText = useMemo(() => [article.title, article.byline, ...article.paragraphs].join('\n\n'), [article]);
  const totalMinutes = Math.max(Math.ceil(page.wordCount / WORDS_PER_MINUTE), 1);

  const scrollRef = useAnimatedRef<Animated.ScrollView>();
  const offset = useSharedValue(0);
  const clock = useSharedValue(0);
  const mode = useSharedValue(MODE_AUTO);
  const held = useSharedValue(0);
  const idle = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnClock = useSharedValue(0);
  // 1 while the script has drawn a scroll the native scroll view may not hold yet.
  const needsSync = useSharedValue(0);
  const minutes = useSharedValue(totalMinutes);

  const paints = useMemo(() => {
    const mask = Skia.Paint();
    mask.setColor(Skia.Color('#FFFFFF'));
    const layer = Skia.Paint();
    layer.setBlendMode(BlendMode.DstIn);
    return { mask, layer };
  }, []);

  useEffect(() => {
    clock.set(0);
    mode.set(MODE_AUTO);
    offset.set(0);
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [reducedMotion, page, clock, mode, offset, scrollRef]);

  useFrameCallback((frame) => {
    const dt = Math.min(frame.timeSincePreviousFrame ?? 16, 48) / 1000;
    if (dt <= 0 || !playing) return;
    const current = mode.get();
    if (current === MODE_AUTO) {
      let t = clock.get() + dt;
      if (t >= LOOP) t -= LOOP;
      clock.set(t);
      const y = scriptAt(t) * maxScroll;
      if (y !== offset.get()) {
        offset.set(y);
        needsSync.set(1);
        scrollTo(scrollRef, 0, y, false);
      }
      return;
    }
    if (current === MODE_MANUAL) {
      if (held.get() === 1) return;
      idle.set(idle.get() + dt);
      if (idle.get() >= RESUME_AFTER) {
        returnFrom.set(offset.get());
        returnClock.set(0);
        mode.set(MODE_RETURN);
      }
      return;
    }
    const k = Math.min(returnClock.get() + dt / RETURN_SECONDS, 1);
    returnClock.set(k);
    const y = returnFrom.get() * (1 - easeInOutCubic(k));
    offset.set(y);
    needsSync.set(1);
    scrollTo(scrollRef, 0, y, false);
    if (k >= 1) {
      clock.set(0);
      mode.set(MODE_AUTO);
    }
  });

  // A finger resting on the page stops the script too, before it becomes a drag.
  // The script draws from `offset`; the native scroll view is moved to it here, on the JS thread, before the drag
  // starts, because the UI-thread scrollTo of the script does not reliably move it and the page would jump on takeover.
  const onTouchStart = () => {
    if (needsSync.get() === 1) {
      scrollRef.current?.scrollTo({ y: offset.get(), animated: false });
      needsSync.set(0);
    }
    mode.set(MODE_MANUAL);
    held.set(1);
    idle.set(0);
  };
  const onTouchEnd = () => {
    held.set(0);
    idle.set(0);
  };

  const onScroll = useAnimatedScrollHandler({
    onBeginDrag: () => {
      mode.set(MODE_MANUAL);
      held.set(1);
      idle.set(0);
    },
    onScroll: (event) => {
      // Until the JS thread has moved the native view to the script's scroll, its offsets are stale.
      if (mode.get() !== MODE_MANUAL || needsSync.get() === 1) return;
      offset.set(event.contentOffset.y);
      idle.set(0);
    },
    onEndDrag: () => {
      held.set(0);
      idle.set(0);
    },
  });

  const front = useDerivedValue(() => {
    if (reducedMotion) return page.wordCount + FADE_WORDS;
    const y = offset.get();
    const share = Math.min(Math.max(y / maxScroll, 0), 1);
    const line = Math.max(y, 0) + mix(readStart, readEnd, share);
    // At the very end the last words are carried home, so a finished article is fully inked.
    const ending = seg(share, 0.985, 1);
    return frontAt(line, page.lineTop, page.lineHeight, page.lineFirst, page.lineCount) + ending * FADE_WORDS;
  });

  useAnimatedReaction(
    () => {
      const wordsLeft = Math.max(page.wordCount - front.get(), 0);
      return wordsLeft < 0.5 ? 0 : Math.max(Math.ceil(wordsLeft / WORDS_PER_MINUTE), 1);
    },
    (now, before) => {
      if (now !== before) minutes.set(now);
    },
  );

  const picture = useDerivedValue(() => {
    const y = offset.get();
    const at = front.get();
    const recorder = Skia.PictureRecorder();
    const canvas = recorder.beginRecording(Skia.XYWHRect(0, 0, width, viewport));
    drawArticle(canvas, page, y, viewport, at, reducedMotion, paints.mask, paints.layer, width);
    return recorder.finishRecordingAsPicture();
  });

  const line = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.min(Math.max(front.get() / Math.max(page.wordCount, 1), 0), 1) }] }));
  const ghost = useAnimatedStyle(() => {
    const t = clock.get();
    const shown = mode.get() === MODE_AUTO && playing ? ghostOpacityAt(t) : 0;
    return { opacity: shown, transform: [{ translateY: -ghostTravelAt(t) * 120 }] };
  });

  return (
    <View style={styles.screen}>
      <StatusBar style="light" />
      <View style={[styles.reader, { top: navHeight }]}>
        <Canvas style={StyleSheet.absoluteFill} pointerEvents="none">
          <Picture picture={picture} />
        </Canvas>
        <Animated.ScrollView
          ref={scrollRef}
          style={StyleSheet.absoluteFill}
          onScroll={onScroll}
          onTouchStart={onTouchStart}
          onTouchEnd={onTouchEnd}
          onTouchCancel={onTouchEnd}
          scrollEventThrottle={1}
          showsVerticalScrollIndicator={false}
          contentInsetAdjustmentBehavior="never"
        >
          <View style={{ height: contentHeight }} accessible accessibilityRole="text" accessibilityLabel={allText} />
        </Animated.ScrollView>
        <LinearGradient pointerEvents="none" colors={['#15171B', 'rgba(21,23,27,0.6)', 'rgba(21,23,27,0)']} locations={[0, 0.4, 1]} style={styles.topFade} />
        <LinearGradient pointerEvents="none" colors={['rgba(21,23,27,0)', 'rgba(21,23,27,0.7)', '#15171B']} locations={[0, 0.6, 1]} style={[styles.bottomFade, { height: 56 + insets.bottom }]} />
      </View>

      <View style={[styles.nav, { height: navHeight, paddingTop: insets.top }]}>
        <Text style={styles.brand}>{article.section}</Text>
        <MinutesLeft minutes={minutes} initial={totalMinutes} />
        <View style={styles.lineTrack}>
          <Animated.View style={[styles.lineFill, line]} />
        </View>
      </View>
      <Animated.View pointerEvents="none" style={[styles.ghost, { left: width - 84, top: height * 0.66 }, ghost]} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: BACKGROUND },
  reader: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  nav: { position: 'absolute', left: 0, right: 0, top: 0, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: SIDE, backgroundColor: BACKGROUND },
  brand: { color: INK, fontSize: 17, fontWeight: '700', letterSpacing: -0.2 },
  minutes: { color: AMBER, fontSize: 14, fontWeight: '600', fontVariant: ['tabular-nums'] },
  lineTrack: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 2, backgroundColor: 'rgba(241,236,226,0.1)' },
  lineFill: { width: '100%', height: 2, backgroundColor: AMBER, transformOrigin: 'left' },
  topFade: { position: 'absolute', left: 0, right: 0, top: 0, height: 22 },
  bottomFade: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  ghost: { position: 'absolute', width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(241,236,226,0.2)', borderWidth: 1, borderColor: 'rgba(241,236,226,0.55)' },
});
