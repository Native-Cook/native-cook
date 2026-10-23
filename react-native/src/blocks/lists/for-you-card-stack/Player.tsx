import { Image } from 'expo-image';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import Svg, { Path, Rect } from 'react-native-svg';

import { Cover, COVER_TITLES, SpreadArt, SpreadText } from './covers';
import { LINES } from './passage';
import { look, type Look } from './script';
import { COLORS, COVER, FONTS, PLAYER, READING } from './theme';

type Props = {
  width: number;
  open: SharedValue<number>;
  close: SharedValue<number>;
  playing: SharedValue<number>;
  words: SharedValue<number>;
  onBack: () => void;
  onPlay: () => void;
  pressBack: SharedValue<number>;
  pressPlay: SharedValue<number>;
  onPressIn: (which: 'back' | 'play') => void;
  onPressOut: (which: 'back' | 'play') => void;
};

const LANDSCAPE = require('./assets/page-landscape.jpg');
const PORTRAIT = require('./assets/page-portrait.jpg');
const PAGE = PLAYER.page;
/** Pages are designed at the cover's 200 x 300 and scaled to the page box. */
const PAGE_SCALE = PAGE.w / COVER.w;
const NB_W = PAGE.w * PLAYER.neighbour.scale;
const SKIP_LEFT = 201 - PLAYER.controls.skipX - PLAYER.controls.icon / 2;
const SKIP_RIGHT = 201 + PLAYER.controls.skipX - PLAYER.controls.icon / 2;
const NB_H = PAGE.h * PLAYER.neighbour.scale;

export function Player(p: Props) {
  const { width } = p;
  const l = useDerivedValue(() => look(p.open.get(), p.close.get()));

  const neighbourStyle = useAnimatedStyle(() => ({ opacity: l.get().neighbours }));
  const chapterStyle = useAnimatedStyle(() => ({ opacity: l.get().chapter }));
  const titleStyle = useAnimatedStyle(() => ({ transform: [{ translateY: READING.titleShift * l.get().title }] }));
  const waveStyle = useAnimatedStyle(() => ({ transform: [{ translateY: -(READING.wave.left + 40) * (1 - l.get().wave) }] }));
  const textStyle = useAnimatedStyle(() => ({ opacity: l.get().text }));

  // The book: the spine sits at the container's left edge; it scales about the spine's middle.
  const bookStyle = useAnimatedStyle(() => {
    const v = l.get();
    return {
      transform: [{ translateX: v.spineX }, { translateY: v.centerY - PAGE.h / 2 }, { scale: v.scale }],
    };
  });
  const playIcon = useAnimatedStyle(() => ({ opacity: 1 - p.playing.get() }));
  const pauseIcon = useAnimatedStyle(() => ({ opacity: p.playing.get() }));
  const playScale = useAnimatedStyle(() => ({ transform: [{ scale: p.pressPlay.get() }] }));
  const backScale = useAnimatedStyle(() => ({ transform: [{ scale: p.pressBack.get() }] }));
  const W = READING.wave;

  return (
    <View style={styles.page}>
      <Animated.View style={[styles.wave, waveStyle]} pointerEvents="none">
        <Svg width={width} height={W.left + 2}>
          <Path d={`M0 0 H${width} V${W.right} Q${W.cx} ${W.cy} 0 ${W.left} Z`} fill={COLORS.reading} />
        </Svg>
      </Animated.View>

      <Animated.View style={[styles.passage, textStyle]} pointerEvents="none">
        {LINES.map((line, li) => (
          <View key={li} style={styles.line}>
            {line.map((w, wi) => (
              <Word key={`${li}-${wi}`} word={w} at={li + (wi + 1) / line.length} words={p.words} />
            ))}
          </View>
        ))}
      </Animated.View>

      {/* neighbours in the cover carousel */}
      <Animated.View style={[styles.neighbour, { left: PLAYER.neighbour.leftEdge - NB_W }, neighbourStyle]}>
        <PageBox kind="lines" />
      </Animated.View>
      <Animated.View style={[styles.neighbour, { left: PLAYER.neighbour.rightEdge }, neighbourStyle]}>
        <PageBox kind="shapes" />
      </Animated.View>

      <Animated.View style={[styles.book, bookStyle]} accessibilityLabel={COVER_TITLES.seeing}>
        <View style={styles.hinge}>
          <Scaled>
            <SpreadText />
          </Scaled>
        </View>
        <Leaf look={l} index={2} front={<SpreadText />} back={<SpreadArt />} />
        <Leaf
          look={l}
          index={1}
          front={<Image source={LANDSCAPE} style={[styles.half, styles.halfRight]} contentFit="cover" />}
          back={<Image source={PORTRAIT} style={StyleSheet.absoluteFill} contentFit="cover" />}
        />
        <Leaf
          look={l}
          index={0}
          front={<Cover kind="seeing" />}
          back={<Image source={LANDSCAPE} style={[styles.half, styles.halfLeft]} contentFit="cover" />}
        />
      </Animated.View>

      <Animated.View style={[styles.titleBlock, titleStyle]}>
        <Text style={styles.author}>Ines Marlowe</Text>
        <Text style={styles.title}>{'Inner Light\nof Color'}</Text>
        <Animated.Text style={[styles.chapter, chapterStyle]}>Chapter 1 of 25</Animated.Text>
      </Animated.View>

      <View style={[styles.controls, { top: PLAYER.controls.y - PLAYER.controls.play / 2 }]}>
        <Svg
          style={[styles.skip, { left: SKIP_LEFT }]}
          width={PLAYER.controls.icon}
          height={PLAYER.controls.icon}
          viewBox="0 0 24 24"
          accessibilityLabel="Back 15 seconds"
        >
          <Path d="M5.5 12a6.5 6.5 0 1 0 2-4.7" stroke={COLORS.white} strokeWidth={1.7} fill="none" strokeLinecap="round" />
          <Path
            d="M7.3 3.8v3.6h3.6"
            stroke={COLORS.white}
            strokeWidth={1.7}
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </Svg>
        <Pressable
          style={styles.playHit}
          accessibilityRole="button"
          accessibilityLabel="Play or pause"
          hitSlop={6}
          pressRetentionOffset={RETENTION}
          onPressIn={() => p.onPressIn('play')}
          onPressOut={() => p.onPressOut('play')}
          onPress={p.onPlay}
        >
          <Animated.View style={[styles.play, playScale]}>
            <Animated.View style={[styles.glyph, playIcon]}>
              <Svg width={16} height={16} viewBox="0 0 16 16">
                <Path d="M4.8 2.8v10.4L13 8z" stroke={COLORS.player} strokeWidth={1.5} fill="none" strokeLinejoin="round" />
              </Svg>
            </Animated.View>
            <Animated.View style={[styles.glyph, pauseIcon]}>
              <Svg width={16} height={16} viewBox="0 0 16 16">
                <Rect x={4.4} y={3} width={2} height={10} rx={1} fill={COLORS.player} />
                <Rect x={9.6} y={3} width={2} height={10} rx={1} fill={COLORS.player} />
              </Svg>
            </Animated.View>
          </Animated.View>
        </Pressable>
        <Svg
          style={[styles.skip, { left: SKIP_RIGHT }]}
          width={PLAYER.controls.icon}
          height={PLAYER.controls.icon}
          viewBox="0 0 24 24"
          accessibilityLabel="Forward 15 seconds"
        >
          <Path d="M18.5 12a6.5 6.5 0 1 1-2-4.7" stroke={COLORS.white} strokeWidth={1.7} fill="none" strokeLinecap="round" />
          <Path
            d="M16.7 3.8v3.6h-3.6"
            stroke={COLORS.white}
            strokeWidth={1.7}
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </Svg>
      </View>

      <View style={[styles.track, { top: PLAYER.progress.y }]}>
        <View style={[styles.played, { width: `${PLAYER.progress.played * 100}%` }]} />
      </View>
      <Text style={[styles.time, { left: PLAYER.progress.left }]}>00:00:10</Text>
      <Text style={[styles.time, { right: PLAYER.progress.right }]}>01:42:21</Text>

      <Pressable
        style={styles.backHit}
        accessibilityRole="button"
        accessibilityLabel="Back"
        hitSlop={14}
        pressRetentionOffset={RETENTION}
        onPressIn={() => p.onPressIn('back')}
        onPressOut={() => p.onPressOut('back')}
        onPress={p.onBack}
      >
        <Animated.View style={backScale}>
          <Svg width={PLAYER.back.w} height={PLAYER.back.h} viewBox="0 0 13 12.7">
            <Path
              d="M12.2 6.35H1.2M6.4 1 1 6.35l5.4 5.35"
              stroke={COLORS.white}
              strokeWidth={1.5}
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </Svg>
        </Animated.View>
      </Pressable>
      <View style={styles.bookIcon} accessibilityLabel="Book contents">
        <Svg width={PLAYER.bookIcon.w} height={PLAYER.bookIcon.h} viewBox="0 0 16.7 14.7">
          <Path
            d="M8.35 2.4C6.6 1.1 4 .8.8 1.1v11.6c3.2-.3 5.8 0 7.55 1.3 1.75-1.3 4.35-1.6 7.55-1.3V1.1C12.7.8 10.1 1.1 8.35 2.4zM8.35 2.4V14"
            stroke={COLORS.white}
            strokeWidth={1.4}
            fill="none"
            strokeLinejoin="round"
          />
        </Svg>
      </View>
    </View>
  );
}

/** A page-sized box holding content designed at the cover's 200 x 300. */
function Scaled({ children }: { children: React.ReactNode }) {
  return (
    <View style={styles.scaledBox}>
      <View style={styles.scaledInner}>{children}</View>
    </View>
  );
}

function PageBox({ kind }: { kind: 'lines' | 'shapes' }) {
  return (
    <View style={styles.nbBox}>
      <View style={styles.nbInner}>
        <Cover kind={kind} />
      </View>
    </View>
  );
}

/** One leaf of the book, hinged on the spine: its front faces right, its back shows once it has turned. */
function Leaf({
  look: l,
  index,
  front,
  back,
}: {
  look: SharedValue<Look>;
  index: 0 | 1 | 2;
  front: React.ReactNode;
  back: React.ReactNode;
}) {
  const style = useAnimatedStyle(() => {
    const v = l.get();
    const f = index === 0 ? v.leaf0 : index === 1 ? v.leaf1 : v.leaf2;
    const moving = f > 0 && f < 1;
    return {
      zIndex: moving ? 30 : f >= 0.5 ? 10 + index : 10 - index,
      transform: [{ perspective: 1200 }, { rotateY: `${-180 * f}deg` }],
    };
  });
  // a face shows only while it looks at the viewer (backface culling does not reach children of a rotated view)
  const frontStyle = useAnimatedStyle(() => {
    const v = l.get();
    const f = index === 0 ? v.leaf0 : index === 1 ? v.leaf1 : v.leaf2;
    return { opacity: f < 0.5 ? 1 : 0 };
  });
  const backStyle = useAnimatedStyle(() => {
    const v = l.get();
    const f = index === 0 ? v.leaf0 : index === 1 ? v.leaf1 : v.leaf2;
    return { opacity: f >= 0.5 ? 1 : 0 };
  });
  return (
    <Animated.View style={[styles.hinge, style]}>
      <Animated.View style={[styles.face, frontStyle]}>
        <Scaled>{front}</Scaled>
      </Animated.View>
      <Animated.View style={[styles.face, styles.back, backStyle]}>
        <Scaled>{back}</Scaled>
      </Animated.View>
    </Animated.View>
  );
}

function Word({ word, at, words }: { word: string; at: number; words: SharedValue<number> }) {
  // `words` counts lines read; a word lights once the reading has passed it
  const style = useAnimatedStyle(() => ({ opacity: at <= words.get() + 0.001 ? 1 : READING.text.dim }));
  return <Animated.Text style={[styles.word, style]}>{word}</Animated.Text>;
}

const RETENTION = { top: 16, bottom: 16, left: 16, right: 16 };

const styles = StyleSheet.create({
  page: { ...StyleSheet.absoluteFill, backgroundColor: COLORS.player, overflow: 'hidden' },
  wave: { position: 'absolute', top: 0, left: 0, right: 0 },
  passage: { position: 'absolute', top: READING.text.top, left: READING.text.left, right: 0 },
  line: { flexDirection: 'row', height: READING.text.lineHeight },
  word: {
    fontFamily: FONTS.reading,
    fontSize: READING.text.size,
    lineHeight: READING.text.lineHeight,
    color: COLORS.white,
    marginRight: READING.text.size * 0.25,
  },
  neighbour: {
    position: 'absolute',
    top: PLAYER.centerY - NB_H / 2,
    width: NB_W,
    height: NB_H,
  },
  nbBox: { width: NB_W, height: NB_H, overflow: 'hidden' },
  nbInner: {
    width: COVER.w,
    height: COVER.h,
    transformOrigin: 'top left',
    transform: [{ scale: NB_W / COVER.w }],
  },
  book: { position: 'absolute', top: 0, left: 0, width: PAGE.w, height: PAGE.h, transformOrigin: 'left center' },
  hinge: { ...StyleSheet.absoluteFill, transformOrigin: 'left center' },
  face: { ...StyleSheet.absoluteFill, overflow: 'hidden' },
  back: { transform: [{ rotateY: '180deg' }] },
  half: { position: 'absolute', top: 0, width: COVER.w * 2, height: COVER.h },
  halfLeft: { left: 0 },
  halfRight: { left: -COVER.w },
  scaledBox: { width: PAGE.w, height: PAGE.h, overflow: 'hidden', backgroundColor: '#FFFFFF' },
  scaledInner: {
    width: COVER.w,
    height: COVER.h,
    transformOrigin: 'top left',
    transform: [{ scale: PAGE_SCALE }],
  },
  titleBlock: { position: 'absolute', top: 0, left: 0, right: 0 },
  author: {
    position: 'absolute',
    top: PLAYER.author.top,
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: FONTS.regular,
    fontSize: PLAYER.author.size,
    color: COLORS.dim,
  },
  title: {
    position: 'absolute',
    top: PLAYER.title.top,
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: FONTS.medium,
    fontSize: PLAYER.title.size,
    lineHeight: PLAYER.title.lineHeight,
    color: COLORS.white,
  },
  chapter: {
    position: 'absolute',
    top: PLAYER.chapter.top,
    left: 0,
    right: 0,
    textAlign: 'center',
    fontFamily: FONTS.regular,
    fontSize: PLAYER.chapter.size,
    color: COLORS.faint,
  },
  controls: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: PLAYER.controls.play,
    alignItems: 'center',
    justifyContent: 'center',
  },
  playHit: { position: 'absolute' },
  skip: { position: 'absolute', top: (PLAYER.controls.play - PLAYER.controls.icon) / 2 },
  play: {
    width: PLAYER.controls.play,
    height: PLAYER.controls.play,
    borderRadius: PLAYER.controls.play / 2,
    backgroundColor: COLORS.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  glyph: { position: 'absolute' },
  track: {
    position: 'absolute',
    left: PLAYER.progress.left,
    right: PLAYER.progress.right,
    height: 1.5,
    backgroundColor: COLORS.faint,
  },
  played: { height: 1.5, backgroundColor: COLORS.white },
  time: {
    position: 'absolute',
    top: PLAYER.progress.timesTop,
    fontFamily: FONTS.regular,
    fontSize: PLAYER.progress.timeSize,
    color: COLORS.dim,
  },
  backHit: { position: 'absolute', top: PLAYER.back.top, left: PLAYER.back.left },
  bookIcon: { position: 'absolute', top: PLAYER.bookIcon.top, left: PLAYER.bookIcon.left },
});
