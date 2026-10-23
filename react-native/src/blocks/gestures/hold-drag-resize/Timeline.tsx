/** Everything under the preview: transport, ruler, clip strip with the playhead, tracks, hint. It moves as one. */
import { Feather } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { PressScale } from './press-scale';
import { belowShift, fullness, squeeze } from './layout';
import { FRAMES, LOOP_MS, SIZES, TIMING } from './motion';
import { THUMBS } from './reel';
import { COLOR, LAYOUT, SQUEEZE, TYPE } from './theme';

const PERIOD = (LOOP_MS / 1000) * TIMING.stripSpeed; // one loop of the reel, in pt: two ruler pitches of dot + label
const REPEAT = 4;
// Ruler marks: a dot, then a label, every LAYOUT.rulerPitch; the offset puts a dot at x = 71 at rest, as in the source.
const RULER_OFFSET = 139;
const THUMBS_PER_CLIP = 3;

type Props = {
  shown: SharedValue<number>;
  lag: SharedValue<number>;
  clock: SharedValue<number>;
  width: number;
  topInset: number;
  playing: boolean;
  second: number;
  onTogglePlay: () => void;
};

function clockText(s: number) {
  return `0:${String(s).padStart(2, '0')}`;
}

export function Timeline({ shown, lag, clock, width, topInset, playing, second, onTogglePlay }: Props) {
  const top = FRAMES[0].top + SIZES[0];
  const group = useAnimatedStyle(() => ({
    // The rows ride on the preview's bottom edge, a beat behind it (follow-through).
    transform: [{ translateY: belowShift(lag.get()) }],
    opacity: 1 - Math.min(fullness(shown.get()) * 1.6, 1),
  }));
  // The strip is periodic in PERIOD, so wrapping the clock does not jump.
  const strip = useAnimatedStyle(() => ({ transform: [{ translateX: -clock.get() * TIMING.stripSpeed }] }));
  const centre = width / 2;
  const thumbs = THUMBS.length * THUMBS_PER_CLIP * REPEAT * 2;
  const firstX = centre - PERIOD * REPEAT;
  const marks = Math.ceil((PERIOD * REPEAT * 2) / LAYOUT.rulerPitch);
  // The source closes the rows up a little as the preview grows.
  const controlsShift = useAnimatedStyle(() => ({ transform: [{ translateY: SQUEEZE.controls * squeeze(shown.get()) }] }));
  const rulerShift = useAnimatedStyle(() => ({ transform: [{ translateY: (SQUEEZE.ruler - SQUEEZE.below) * squeeze(shown.get()) }] }));
  const dividerShift = useAnimatedStyle(() => ({ transform: [{ translateY: SQUEEZE.ruler * squeeze(shown.get()) }] }));
  const belowShiftStyle = useAnimatedStyle(() => ({ transform: [{ translateY: SQUEEZE.below * squeeze(shown.get()) }] }));
  // The hint is pinned to the screen (the tracks slide under it), so it cancels the group's shift.
  const hintStyle = useAnimatedStyle(() => ({ transform: [{ translateY: SQUEEZE.hint * squeeze(shown.get()) - belowShift(lag.get()) }] }));

  return (
    <Animated.View style={[styles.group, { top }, group]}>
      <Animated.View style={[styles.controls, controlsShift]}>
        <PressScale style={styles.round} onPress={onTogglePlay} accessibilityRole="button" accessibilityLabel={playing ? 'Pause' : 'Play'} hitSlop={6}>
          <Feather name={playing ? 'pause' : 'play'} size={14} color={COLOR.ink} />
        </PressScale>
        <Text style={styles.time}>
          {clockText(second)}<Text style={styles.timeMute}>  /  0:12</Text>
        </Text>
        <View style={styles.pair}>
          <View style={styles.round} accessible accessibilityRole="button" accessibilityLabel="Undo"><Feather name="rotate-ccw" size={14} color={COLOR.ink} /></View>
          <View style={styles.round} accessible accessibilityRole="button" accessibilityLabel="Redo"><Feather name="rotate-cw" size={14} color={COLOR.faint} /></View>
        </View>
      </Animated.View>
      <Animated.View style={[styles.divider, dividerShift]} />

      <Animated.View style={[styles.lane, belowShiftStyle]}>
        <Animated.View style={[styles.rail, { left: firstX }, strip]}>
          <Animated.View style={[styles.ruler, rulerShift]}>
            {Array.from({ length: marks }, (_, m) => (m % 2 === 0
              ? <View key={m} style={[styles.tick, { left: RULER_OFFSET + m * LAYOUT.rulerPitch - 1.5 }]} />
              : <Text key={m} style={[styles.rulerLabel, { left: RULER_OFFSET + m * LAYOUT.rulerPitch - LAYOUT.rulerLabelW / 2 }]}>{m % 4 === 1 ? '10s' : '30s'}</Text>))}
          </Animated.View>
          <View style={styles.thumbs}>
            {Array.from({ length: thumbs }, (_, i) => (
              <Image key={i} source={THUMBS[Math.floor(i / THUMBS_PER_CLIP) % THUMBS.length]} style={styles.thumb} contentFit="cover" transition={0} />
            ))}
          </View>
        </Animated.View>
        <View style={styles.railSpace} />
        {/* The source's trim handle and the round sticker on the clip, left of the playhead. */}
        <View style={styles.trim} pointerEvents="none" />
        <View style={styles.sticker} pointerEvents="none">
          <Feather name="smile" size={14} color={COLOR.stickerInk} />
        </View>
        <View style={[styles.track, styles.trackFirst]}>
          <Feather name="music" size={13} color={COLOR.mute} />
          <Text style={styles.trackText}>Tap to add audio</Text>
        </View>
        <View style={styles.track}>
          <Text style={styles.trackGlyph}>Aa</Text>
          <Text style={styles.trackText}>Tap to add text</Text>
        </View>
        <View style={[styles.playhead, { left: centre - 1 }]} pointerEvents="none" />
      </Animated.View>
      <Animated.Text style={[styles.hint, hintStyle]}>Tap on a track to trim. Pinch to zoom.</Animated.Text>
    </Animated.View>
  );
}

const THUMB_W = LAYOUT.thumb;

const styles = StyleSheet.create({
  group: { position: 'absolute', left: 0, right: 0 },
  controls: {
    height: LAYOUT.control, marginTop: LAYOUT.controlsGap - LAYOUT.control / 2,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: LAYOUT.gutter,
  },
  round: { width: LAYOUT.control, height: LAYOUT.control, borderRadius: LAYOUT.control / 2, backgroundColor: COLOR.chromeHi, alignItems: 'center', justifyContent: 'center' },
  pair: { flexDirection: 'row', gap: 8 },
  time: { ...TYPE.time, color: COLOR.mute, position: 'absolute', left: 0, right: 0, textAlign: 'center' },
  timeMute: { color: COLOR.faint },
  lane: { marginTop: LAYOUT.rulerGap - LAYOUT.dividerGap - LAYOUT.ruler / 2, overflow: 'hidden' },
  rail: { position: 'absolute', top: 0 },
  railSpace: { height: LAYOUT.ruler + LAYOUT.stripGap + LAYOUT.strip },
  ruler: { height: LAYOUT.ruler, marginBottom: LAYOUT.stripGap },
  tick: { position: 'absolute', top: LAYOUT.ruler / 2 - 1.5, width: 3, height: 3, borderRadius: 1.5, backgroundColor: COLOR.mute },
  rulerLabel: { ...TYPE.ruler, position: 'absolute', top: 0, width: LAYOUT.rulerLabelW, textAlign: 'center', color: COLOR.mute },
  divider: { height: StyleSheet.hairlineWidth * 2, backgroundColor: COLOR.divider, marginTop: LAYOUT.dividerGap - LAYOUT.control / 2 },
  thumbs: { flexDirection: 'row', height: LAYOUT.strip },
  thumb: { width: THUMB_W, height: LAYOUT.strip },
  track: {
    height: LAYOUT.track, marginTop: LAYOUT.trackGap, backgroundColor: COLOR.chrome,
    flexDirection: 'row', alignItems: 'center', gap: 6, paddingLeft: LAYOUT.trackIndent,
  },
  trackFirst: { marginTop: LAYOUT.trackFirstGap },
  trackGlyph: { ...TYPE.track, color: COLOR.mute, fontWeight: '700' },
  trim: {
    position: 'absolute', left: LAYOUT.trimHandle.x - LAYOUT.trimHandle.width / 2,
    top: LAYOUT.ruler + LAYOUT.stripGap + (LAYOUT.strip - LAYOUT.trimHandle.height) / 2,
    width: LAYOUT.trimHandle.width, height: LAYOUT.trimHandle.height, borderRadius: 3, backgroundColor: COLOR.ink,
  },
  sticker: {
    position: 'absolute', left: LAYOUT.sticker.x - LAYOUT.sticker.size / 2,
    top: LAYOUT.ruler + LAYOUT.stripGap + (LAYOUT.strip - LAYOUT.sticker.size) / 2,
    width: LAYOUT.sticker.size, height: LAYOUT.sticker.size, borderRadius: LAYOUT.sticker.size / 2,
    backgroundColor: COLOR.sticker, borderWidth: 2, borderColor: COLOR.stickerRing, alignItems: 'center', justifyContent: 'center',
  },
  trackText: { ...TYPE.track, color: COLOR.mute },
  playhead: { position: 'absolute', top: 0, width: 2, borderRadius: 1, height: LAYOUT.ruler + LAYOUT.stripGap + LAYOUT.strip + (LAYOUT.track + LAYOUT.trackGap) * 2 + 6, backgroundColor: COLOR.playhead },
  hint: { ...TYPE.hint, color: COLOR.mute, textAlign: 'center', marginTop: LAYOUT.hintGap - LAYOUT.hint - 4, paddingVertical: 4, backgroundColor: COLOR.background },
});
