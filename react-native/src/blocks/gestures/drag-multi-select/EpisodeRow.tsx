/**
 * One episode as a stop on the flight's timeline: the time you would start it (once it is on the timeline), a rail
 * segment as long as the row in the episode's colour (a dotted rail while it is not), its title and details, and its
 * download state on the right (arrow, progress ring, check). Everything moving reads the model on the UI thread; the
 * time and the colour are text and props, set when the selection changes, never per frame.
 */
import { memo } from 'react';
import { StyleSheet, Text, View, type AccessibilityActionEvent } from 'react-native';
import Animated, { useAnimatedProps, useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle } from 'react-native-svg';

import { formatMinutes, spokenMinutes, type Episode } from './episodes';
import { GLYPH } from './glyph-paths';
import { Stroke } from './Glyphs';
import { clamp01, easeOut, type Model } from './model';
import { TIMELINE } from './motion';
import { share } from './route';
import { COLOR, LAYOUT, TYPE } from './theme';

const ACircle = Animated.createAnimatedComponent(Circle);
const RING_R = 9;
const RING_C = 2 * Math.PI * RING_R;
const RAIL_H = LAYOUT.row - 8;
const DOTS = Array.from({ length: Math.floor(RAIL_H / 6) }, (_, k) => k);

type Props = {
  ep: Episode;
  index: number;
  model: SharedValue<Model>;
  selecting: boolean;
  selected: boolean;
  downloaded: boolean;
  /** When this episode would start, if it is on the timeline: "10:09" and "AM". */
  time: [string, string] | null;
  color: string;
  onActivate: (index: number) => void;
};

export const EpisodeRow = memo(function EpisodeRow({ ep, index, model, selecting, selected, downloaded, time, color, onActivate }: Props) {
  // The segment grows down from the row's top as the episode joins the timeline and shrinks back up as it leaves.
  // Under Reduce Motion it fades instead of growing.
  const seg = useAnimatedStyle(() => {
    const m = model.get();
    const s = share(m, index);
    if (m.reduced) return { opacity: s, transform: [{ translateY: 0 }, { scaleY: 1 }] };
    return { opacity: s > 0.002 ? 1 : 0, transform: [{ translateY: -(1 - s) * RAIL_H / 2 }, { scaleY: Math.max(s, 0.001) }] };
  });
  const dots = useAnimatedStyle(() => ({ opacity: 1 - clamp01(share(model.get(), index) * 3) }));
  const timeOn = useAnimatedStyle(() => {
    const s = share(model.get(), index);
    return { opacity: clamp01((s - TIMELINE.timeFrom) / TIMELINE.timeSpan) };
  });
  const dash = useAnimatedStyle(() => ({ opacity: 1 - clamp01(share(model.get(), index) * 4) }));
  const arrow = useAnimatedStyle(() => {
    const m = model.get();
    return { opacity: (1 - clamp01(m.prog[index] * 8)) * easeOut(1 - m.mark[index]) };
  });
  // Picked for download: the arrow takes the violet of the action.
  const arrowOn = useAnimatedStyle(() => ({ opacity: clamp01((model.get().shown[index] - 0.45) / 0.4) }));
  const ring = useAnimatedStyle(() => {
    const m = model.get();
    return { opacity: clamp01(m.prog[index] * 8) * (1 - easeOut(m.mark[index] * 2)) };
  });
  const ringArc = useAnimatedProps(() => ({ strokeDashoffset: RING_C * (1 - clamp01(model.get().prog[index])) }));
  const check = useAnimatedStyle(() => {
    // The ring leaves in the first half of the mark's time and the check draws in over the second: never both.
    const m = model.get();
    const u = clamp01(m.mark[index] * 2 - 1);
    const k = m.done[index] === 1 ? easeOut(u) : 1 - easeOut(1 - u);
    return { opacity: k, transform: [{ scale: m.reduced ? 1 : 0.9 + 0.1 * k }] };
  });

  const onAction = (e: AccessibilityActionEvent) => {
    if (e.nativeEvent.actionName === 'activate') onActivate(index);
  };
  const when = time ? `, starts at ${time[0]} ${time[1]}` : '';
  const label = `Episode ${ep.number}, ${ep.title}, ${spokenMinutes(ep.minutes)}, ${ep.guest}${when}${downloaded ? ', downloaded' : ''}`;

  return (
    <View
      style={styles.row}
      accessible
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={selecting ? { selected } : undefined}
      accessibilityHint={downloaded ? undefined : selecting ? 'Double tap to toggle' : 'Double tap to start planning'}
      accessibilityActions={[{ name: 'activate' }]}
      onAccessibilityAction={onAction}
    >
      <View style={styles.lead}>
        <Animated.Text style={[styles.dash, dash]}>—</Animated.Text>
        {time && (
          <Animated.View style={[styles.time, timeOn]}>
            <Text style={styles.timeText}>{time[0]}</Text>
            <Text style={styles.ap}>{time[1]}</Text>
          </Animated.View>
        )}
      </View>
      <Animated.View style={[styles.dots, dots]}>
        {DOTS.map((k) => <View key={k} style={styles.dot} />)}
      </Animated.View>
      <Animated.View style={[styles.seg, { backgroundColor: color }, seg]} />
      <View style={styles.text}>
        <Text style={styles.title} numberOfLines={1}>{ep.title}</Text>
        <Text style={styles.meta} numberOfLines={1}>{`#${ep.number} · ${formatMinutes(ep.minutes)} · ${ep.guest}`}</Text>
      </View>
      <View style={styles.trail} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
        <Animated.View style={[styles.glyph, arrow]}>
          <Stroke d={GLYPH.download} size={22} color={COLOR.glyph} weight={1.6} />
          <Animated.View style={[styles.over, arrowOn]}>
            <Stroke d={GLYPH.download} size={22} color={COLOR.violet} weight={1.6} />
          </Animated.View>
        </Animated.View>
        <Animated.View style={[styles.glyph, ring]}>
          <Svg width={22} height={22} viewBox="0 0 24 24">
            <Circle cx={12} cy={12} r={RING_R} stroke={COLOR.ring} strokeWidth={2} fill="none" />
            <ACircle
              cx={12} cy={12} r={RING_R} stroke={COLOR.violet} strokeWidth={2} fill="none" strokeLinecap="round"
              strokeDasharray={`${RING_C} ${RING_C}`} animatedProps={ringArc} transform="rotate(-90 12 12)"
            />
          </Svg>
        </Animated.View>
        <Animated.View style={[styles.glyph, styles.checkDisc, check]}>
          <Stroke d={GLYPH.check} size={16} color={COLOR.green} weight={2.2} />
        </Animated.View>
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  row: { height: LAYOUT.row },
  lead: { position: 'absolute', left: LAYOUT.numberX, top: 12, width: LAYOUT.numberW, height: 32 },
  dash: { ...TYPE.rowTime, color: COLOR.dim, position: 'absolute', left: 0, top: 0 },
  time: { position: 'absolute', left: 0, top: 0 },
  timeText: { ...TYPE.rowTime, color: COLOR.ink },
  ap: { ...TYPE.rowAp, color: COLOR.mute },
  dots: { position: 'absolute', left: LAYOUT.railX + 1, top: 4, height: RAIL_H, width: 2, justifyContent: 'space-between' },
  dot: { width: 2, height: 2, borderRadius: 1, backgroundColor: COLOR.dot },
  seg: { position: 'absolute', left: LAYOUT.railX, top: 4, width: LAYOUT.rail, height: RAIL_H, borderRadius: LAYOUT.rail / 2 },
  // Title 24 + 4 + meta 16 = 44, centred in the 64 pt row.
  text: { position: 'absolute', left: LAYOUT.textX, right: LAYOUT.trailRight + LAYOUT.glyph + 12, top: 10, gap: 4 },
  title: { ...TYPE.title, color: COLOR.ink },
  meta: { ...TYPE.meta, color: COLOR.mute },
  trail: { position: 'absolute', right: LAYOUT.trailRight, top: 0, bottom: 0, width: LAYOUT.glyph, alignItems: 'center', justifyContent: 'center' },
  glyph: { position: 'absolute' },
  over: { position: 'absolute', left: 0, top: 0 },
  checkDisc: { width: 22, height: 22, borderRadius: 11, backgroundColor: COLOR.greenSoft, alignItems: 'center', justifyContent: 'center' },
});
