import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Path } from 'react-native-svg';

import type { Memo } from './memos';
import { PRESS, PULL, SPREAD, STAGGER } from './motion';
import { C, CARD, FONT, OPEN_TYPE, PHOTO, POCKET, POCKET_OPEN, S, TYPE } from './theme';

// One memo folder. `open` runs 0 (closed: the front photo fills the card's top, the pocket
// stands upright) to 1 (open: three framed prints fan out and the pocket tips towards you).

type Props = {
  memo: Memo;
  left: number; // screen pt of the card's top-left
  top: number;
  open: SharedValue<number>;
  press: SharedValue<number>;
  pull: SharedValue<number>; // the front print drawn out of the pocket, 0..1
  spread: SharedValue<number>; // the side prints fanned wider, 0..1
  reduceMotion: boolean;
  onPress: () => void;
  onPressIn: () => void;
  onPressOut: () => void;
  isOpen: boolean;
};

const W = CARD.w * S;
const H = CARD.h * S;

// Pocket outline in the card's pin pixels, as [command, x, y, ...] steps so it can be reshaped per frame.
type Step = [string, ...number[]];
function pocketSteps(): Step[] {
  const { tabTop, tabEnd, slopeEnd, top, tabRadius: r, cornerRadius: cr } = POCKET;
  const R = CARD.radius;
  const w = CARD.w;
  const h = CARD.h;
  return [
    ['M', 0, tabTop + r],
    ['Q', 0, tabTop, r, tabTop],
    ['L', tabEnd, tabTop],
    ['C', tabEnd + 22, tabTop, tabEnd + 26, top, slopeEnd + 8, top],
    ['L', w - cr, top],
    ['Q', w, top, w, top + cr],
    ['L', w, h - R],
    ['Q', w, h, w - R, h],
    ['L', R, h],
    ['Q', 0, h, 0, h - R],
    ['C', 0, 320.3, 0, 246.7, 0, tabTop + r],
  ];
}
const STEPS = pocketSteps();

/**
 * The pin draws the open pocket flat, as a trapezoid that leans towards you (wide top, narrow bottom);
 * corresponding closed/open contour points interpolate without distorting the rounded lip.
 */
function pocketD(v: number) {
  'worklet';
  let d = '';
  for (let i = 0; i < STEPS.length; i++) {
    const st = STEPS[i];
    d += st[0];
    for (let k = 1; k < st.length; k += 2) {
      const x = st[k] as number;
      const y = st[k + 1] as number;
      const ox = POCKET_OPEN[i][k - 1] as number;
      const oy = POCKET_OPEN[i][k] as number;
      d += ` ${(x + (ox - x) * v).toFixed(1)} ${(y + (oy - y) * v).toFixed(1)}`;
    }
    d += ' ';
  }
  return d + 'Z';
}

const AnimatedPath = Animated.createAnimatedComponent(Path);

export function Folder({ memo, left, top, open, press, pull, spread, reduceMotion, onPress, onPressIn, onPressOut, isOpen }: Props) {
  'use no memo';
  // Reduce Motion: no travel; the folder dips out and comes back in its other state
  const shown = (v: number) => {
    'worklet';
    return reduceMotion ? (v < 0.5 ? 0 : 1) : v;
  };
  const wrap = useAnimatedStyle(() => ({
    opacity: reduceMotion ? 1 - Math.sin(Math.PI * open.get()) : 1,
    transform: [{ scale: 1 - (1 - PRESS.scale) * press.get() }],
  }));
  const pocketProps = useAnimatedProps(() => ({ d: pocketD(shown(open.get())) }));
  const typeAt = (d: { dx: number; dy: number }) => {
    'worklet';
    const v = shown(open.get());
    return { transform: [{ translateX: d.dx * S * v }, { translateY: d.dy * S * v }, { scale: 1 + (OPEN_TYPE.scale - 1) * v }] };
  };
  const titleT = useAnimatedStyle(() => typeAt(OPEN_TYPE.title));
  const subT = useAnimatedStyle(() => typeAt(OPEN_TYPE.sub));
  const countT = useAnimatedStyle(() => typeAt(OPEN_TYPE.count));
  const notesT = useAnimatedStyle(() => typeAt(OPEN_TYPE.notes));
  const label = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, shown(open.get()) * 3) }));

  return (
    <Animated.View style={[styles.folder, { left, top }, wrap]}>
      <Pressable
        onPress={onPress}
        onPressIn={onPressIn}
        onPressOut={onPressOut}
        pressRetentionOffset={16}
        accessibilityRole="button"
        accessibilityLabel={`${memo.title}, ${memo.sub}, ${memo.count} documents, ${memo.notes}`}
        accessibilityState={{ expanded: isOpen }}
        accessibilityHint={isOpen ? 'Puts the photos back in the folder' : 'Fans out the photos'}
        style={StyleSheet.absoluteFill}
      >
        <View style={styles.back} />
        {/* side prints under the front one */}
        {[2, 1, 0].map((k) => (
          <Print key={k} source={memo.photos[k]} index={k} open={open} shown={shown} pull={pull} spread={spread} />
        ))}
        {memo.label ? (
          <Animated.Text style={[styles.label, label]} numberOfLines={2}>
            {memo.label}
          </Animated.Text>
        ) : null}

        <View style={styles.pocket} pointerEvents="none">
          <Svg width={W * 3} height={H} viewBox={`${-CARD.w} 0 ${CARD.w * 3} ${CARD.h}`} style={styles.pocketSvg}>
            <AnimatedPath animatedProps={pocketProps} fill={C.folder} />
          </Svg>
        </View>
        {/* the front print again, above the pocket: it takes over once the print is out of it */}
        <Print source={memo.photos[0]} index={0} open={open} shown={shown} pull={pull} spread={spread} over />
        {/* The pin draws the open pocket's lettering flat, not foreshortened with the pocket: the type
            slides to the open spots and grows by OPEN_TYPE.scale instead of turning with the tilt. */}
        <Animated.Text style={[styles.title, { left: TYPE.title.x * S, top: (TYPE.title.y - TYPE.title.size * 0.62) * S }, titleT]}>{memo.title}</Animated.Text>
        <Animated.Text style={[styles.sub, { left: TYPE.sub.x * S, top: (TYPE.sub.y - TYPE.sub.size * 0.62) * S }, subT]}>{memo.sub}</Animated.Text>
        <Animated.View style={[styles.countRow, { left: TYPE.count.x * S, top: (TYPE.count.y - TYPE.count.size * 0.62) * S }, countT]}>
          <Text style={styles.count}>{memo.count}</Text>
          <Text style={styles.unit}>{memo.unit}</Text>
        </Animated.View>
        <Animated.Text style={[styles.notes, { right: TYPE.notes.right * S, top: (TYPE.notes.y - TYPE.notes.size * 0.62) * S }, notesT]}>{memo.notes}</Animated.Text>
      </Pressable>
    </Animated.View>
  );
}

type PrintProps = {
  source: Memo['photos'][number];
  index: number;
  open: SharedValue<number>;
  shown: (v: number) => number;
  pull: SharedValue<number>;
  spread: SharedValue<number>;
  over?: boolean; // the copy drawn above the pocket
};
function Print({ source, index, open, shown, pull, spread, over = false }: PrintProps) {
  'use no memo';
  const to = PHOTO.open[index];
  const style = useAnimatedStyle(() => {
    // the side prints leave a beat after the front one and come back a beat before it
    const lag = index === 0 ? 0 : STAGGER;
    const v = Math.max(0, Math.min(1, (shown(open.get()) - lag) / (1 - lag)));
    const x = PHOTO.closed.x + (to.x - PHOTO.closed.x) * v;
    const y = PHOTO.closed.y + (to.y - PHOTO.closed.y) * v;
    const sc = PHOTO.closedScale + (1 - PHOTO.closedScale) * v;
    // hero: the front print rises out of the pocket and turns; the side prints spread like a hand
    const side = index === 1 ? -1 : index === 2 ? 1 : 0;
    const sp = spread.get();
    const p = index === 0 ? pull.get() : 0;
    const lifted = Math.max(0, p);
    const px = x + side * SPREAD.dx * sp;
    const py = y + side * side * SPREAD.dy * sp - PULL.rise * p;
    const rot = to.rot * v + side * SPREAD.rot * sp + PULL.rot * lifted;
    return {
      // out of the pocket it is drawn in front of it (by then the two no longer overlap)
      opacity: index !== 0 ? 1 : (p > PULL.clear) === over ? 1 : 0,
      shadowOpacity: PULL.shadow.opacity * Math.min(1, lifted),
      shadowRadius: PULL.shadow.radius[0] + (PULL.shadow.radius[1] - PULL.shadow.radius[0]) * lifted,
      shadowOffset: { width: 0, height: PULL.shadow.dy[0] + (PULL.shadow.dy[1] - PULL.shadow.dy[0]) * lifted },
      transform: [
        { translateX: (px - PHOTO.w / 2) * S },
        { translateY: (py - PHOTO.h / 2) * S },
        { rotate: `${rot}deg` },
        { scale: sc * (1 + PULL.scale * lifted) },
      ],
    };
  });
  // the prints left in the hand fall into shade while the front one is out
  const shade = useAnimatedStyle(() => ({ opacity: index === 0 ? 0 : PULL.dim * Math.max(0, Math.min(1, pull.get())) }));
  // a soft band of light crosses the photo as the print turns in the hand
  const sheen = useAnimatedStyle(() => {
    const p = index === 0 ? Math.max(0, pull.get()) : 0;
    return {
      opacity: PULL.sheen * Math.sin(Math.PI * Math.min(1, p)),
      transform: [{ translateX: (-PHOTO.w * 0.7 + PHOTO.w * 1.4 * Math.min(1, p)) * S }, { rotate: '20deg' }],
    };
  });
  // the white frame of a print only shows once it is out of the folder
  const frame = useAnimatedStyle(() => ({ opacity: Math.min(1, shown(open.get()) * 2.5) }));
  // closed, the photo grows over its frame so it runs edge to edge across the card
  const fill = PHOTO.w / (PHOTO.w - PHOTO.frame * 2);
  const photo = useAnimatedStyle(() => {
    const f = Math.min(1, shown(open.get()) * 2.5);
    return { transform: [{ scale: fill + (1 - fill) * f }] };
  });
  return (
    <Animated.View style={[styles.print, style]}>
      <Animated.View style={[styles.frame, frame]} pointerEvents="none" />
      <Animated.View style={[styles.photo, photo]} pointerEvents="none">
        <Image source={source} style={StyleSheet.absoluteFill} contentFit="cover" transition={0} />
        <Animated.View style={[StyleSheet.absoluteFill, styles.shade, shade]} pointerEvents="none" />
        <Animated.View style={[styles.sheen, sheen]} pointerEvents="none">
          <LinearGradient
            colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.9)', 'rgba(255,255,255,0)']}
            start={{ x: 0, y: 0.5 }}
            end={{ x: 1, y: 0.5 }}
            style={StyleSheet.absoluteFill}
          />
        </Animated.View>
      </Animated.View>
    </Animated.View>
  );
}

const F = PHOTO.frame * S;
const styles = StyleSheet.create({
  folder: {
    position: 'absolute',
    width: W,
    height: H,
    shadowColor: '#000000',
    shadowOpacity: 0.72,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 7 },
  },
  back: { position: 'absolute', left: 0, top: 0, width: W, height: H, borderRadius: CARD.radius * S, borderCurve: 'continuous', backgroundColor: C.folder },
  print: { position: 'absolute', left: 0, top: 0, width: PHOTO.w * S, height: PHOTO.h * S, shadowColor: '#000000', shadowOpacity: 0 },
  shade: { backgroundColor: '#000000' },
  sheen: { position: 'absolute', top: -PHOTO.h * 0.3 * S, bottom: -PHOTO.h * 0.3 * S, left: 0, width: PHOTO.w * 0.35 * S },
  frame: { position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, borderRadius: PHOTO.radius * S, borderCurve: 'continuous', backgroundColor: C.frame },
  photo: { position: 'absolute', left: F, top: F, right: F, bottom: F, borderRadius: (PHOTO.radius - PHOTO.frame) * S, borderCurve: 'continuous', overflow: 'hidden' },
  label: {
    position: 'absolute',
    right: TYPE.label.right * S,
    top: (TYPE.label.y - TYPE.label.size * 0.62) * S,
    fontFamily: FONT.regular,
    fontSize: TYPE.label.size * S,
    lineHeight: TYPE.label.line * S,
    color: C.ink,
  },
  pocket: { position: 'absolute', left: 0, top: 0, width: W, height: H, zIndex: 1 },
  pocketSvg: { position: 'absolute', left: -W, top: 0 },
  title: { position: 'absolute', zIndex: 5, transformOrigin: 'left top', fontFamily: FONT.semibold, fontSize: TYPE.title.size * S, color: C.ink },
  sub: { position: 'absolute', zIndex: 5, transformOrigin: 'left top', fontFamily: FONT.regular, fontSize: TYPE.sub.size * S, letterSpacing: 0.2, color: C.mute },
  countRow: { position: 'absolute', zIndex: 5, transformOrigin: 'left top', flexDirection: 'row', alignItems: 'baseline', gap: TYPE.unit.gap * S },
  count: { fontFamily: FONT.regular, fontSize: TYPE.count.size * S, letterSpacing: -0.5, fontVariant: ['tabular-nums'], color: C.ink },
  unit: { fontFamily: FONT.regular, fontSize: TYPE.unit.size * S, color: C.mute, transform: [{ translateY: TYPE.unit.dy * S }] },
  notes: { position: 'absolute', zIndex: 5, transformOrigin: 'right top', fontFamily: FONT.semibold, fontSize: TYPE.notes.size * S, color: C.ink },
});
