// Quiet native typography, animated from the same shared state as the canvas.
import { useState } from 'react';
import { StyleSheet, Text, View, type TextStyle } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';
import { copyFrame } from './collect-sim';
import { COPY, PALETTE, TYPE, type Layout } from './gem-contract';
import { RollingNumber, rollingNumberWidth } from './RollingNumber';
import type { GemCollectController } from './use-gem-collect';

type LineProps = { before: string; after: string; progress: SharedValue<number>; target: SharedValue<number>; speed: SharedValue<number>; style: TextStyle; reduced: boolean; word?: boolean };
function CopyLine({ before, after, progress, target, speed, style, reduced, word = false }: LineProps) {
  const line = style.lineHeight ?? 24;
  const oldStyle = useAnimatedStyle(() => {
    const frame = copyFrame(progress.get(), target.get(), false, line, speed.get() * line, reduced);
    return { opacity: frame.opacity, textShadowRadius: reduced ? 0 : (frame.scale - 1) * 18,
      textShadowOffset: { width: 0, height: 0 }, textShadowColor: style.color,
      transform: [{ translateY: frame.offset }, { scaleY: frame.scale }] };
  });
  const newStyle = useAnimatedStyle(() => {
    const frame = copyFrame(progress.get(), target.get(), true, line, speed.get() * line, reduced);
    return { opacity: frame.opacity, textShadowRadius: reduced ? 0 : (frame.scale - 1) * 18,
      textShadowOffset: { width: 0, height: 0 }, textShadowColor: style.color,
      transform: [{ translateY: frame.offset }, { scaleY: frame.scale }] };
  });
  return <View style={{ height: line, overflow: 'hidden' }}>
    <Animated.Text accessible={false} allowFontScaling={false} adjustsFontSizeToFit={false} numberOfLines={1} style={[styles.line, style, word && styles.word, oldStyle]}>{before}</Animated.Text>
    <Animated.Text accessible={false} allowFontScaling={false} adjustsFontSizeToFit={false} numberOfLines={1} style={[styles.line, style, word && styles.word, newStyle]}>{after}</Animated.Text>
  </View>;
}
/** Measure with the loaded font once, before the first swap; the fallback rolls the whole title. */
function TitleLine({ width, ...props }: LineProps & { width: number }) {
  const [measured, setMeasured] = useState({ ready: 0, collected: 0, full: 0 });
  const { progress, style, reduced } = props;
  const line = style.lineHeight ?? 34;
  const prefix = measured.full - measured.ready;
  const position = useAnimatedStyle(() => ({ transform: [{ translateX: (width - prefix - measured.ready - (measured.collected - measured.ready) * progress.get()) / 2 }] }));
  const complete = measured.ready > 0 && measured.collected > 0 && measured.full > 0;
  const measure = (key: keyof typeof measured, value: number) => {
    if (value > 0) setMeasured(old => old[key] === value ? old : { ...old, [key]: value });
  };
  return <>
    {complete && !reduced ? <Animated.View style={[{ height: line }, position]}>
      <Text accessible={false} allowFontScaling={false} numberOfLines={1}
        style={[style, { position: 'absolute', width: prefix + 2, includeFontPadding: false }]}>200 Lumens </Text>
      <View style={{ position: 'absolute', left: prefix, width: Math.ceil(Math.max(measured.ready, measured.collected)), height: line }}>
        <CopyLine {...props} before="ready" after="collected" word />
      </View>
    </Animated.View> : <CopyLine {...props} />}
    {!complete && <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" pointerEvents="none" style={{ position: 'absolute', opacity: 0, width }}>
      <Text accessible={false} allowFontScaling={false} numberOfLines={1} style={style} onTextLayout={event => measure('ready', event.nativeEvent.lines[0]?.width ?? 0)}>ready</Text>
      <Text accessible={false} allowFontScaling={false} numberOfLines={1} style={style} onTextLayout={event => measure('collected', event.nativeEvent.lines[0]?.width ?? 0)}>collected</Text>
      <Text accessible={false} allowFontScaling={false} numberOfLines={1} style={style} onTextLayout={event => measure('full', event.nativeEvent.lines[0]?.width ?? 0)}>{COPY.titleBefore}</Text>
    </View>}
  </>;
}
export function CollectCopy({ layout: l, controller: c, reduced }: { layout: Layout; controller: GemCollectController; reduced: boolean }) {
  // Destructure shared values so no style worklet captures a controller containing gestures.
  const { visible, tallyX, tallyY, tallyScale, tallyOpacity, rollFade } = c;
  const capsuleScale = c.capsule.scale, barScale = c.bar.scale, barOpacity = c.bar.opacity;
  const page = useAnimatedStyle(() => ({ opacity: visible.get() }));
  const tally = useAnimatedStyle(() => ({ opacity: tallyOpacity.get(), transform: [{ translateX: tallyX.get() - l.width / 2 }, { translateY: tallyY.get() - TYPE.tally.line * l.type / 2 }, { scale: tallyScale.get() }] }));
  const number = useAnimatedStyle(() => ({ opacity: rollFade.get(), transform: [{ scale: capsuleScale.get() }] }));
  const button = useAnimatedStyle(() => ({ opacity: barOpacity.get(), transform: [{ scale: barScale.get() }] }));
  const numberWidth = rollingNumberWidth(4, TYPE.number.size);
  const after = c.text.phase === 'after' || c.text.phase === 'burst';
  return <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, page]}>
    <Animated.Text accessible={false} allowFontScaling={false} numberOfLines={1}
      style={[styles.line, { top: 0, left: (l.width - 112) / 2, right: undefined, width: 112, paddingHorizontal: 0, fontFamily: TYPE.tally.family, fontSize: TYPE.tally.size * l.type, lineHeight: TYPE.tally.line * l.type, color: '#E9E4FF', fontVariant: ['tabular-nums'] }, tally]}>{`+${c.text.tally}`}</Animated.Text>
    <View accessible accessibilityRole="header" accessibilityLabel={after ? COPY.titleAfter : COPY.titleBefore}
      style={{ position: 'absolute', top: l.titleTop, width: l.width, height: TYPE.title.line * l.type }}>
      <TitleLine width={l.width} speed={c.copySpeed} before={COPY.titleBefore} after={COPY.titleAfter} progress={c.copy} target={c.copyTarget} reduced={reduced}
        style={{ fontFamily: TYPE.title.family, fontSize: TYPE.title.size * l.type, lineHeight: TYPE.title.line * l.type, letterSpacing: TYPE.title.tracking, color: PALETTE.title }} />
    </View>
    <View accessible accessibilityLabel={after ? COPY.bodyAfter : COPY.bodyBefore}
      style={{ position: 'absolute', top: l.bodyTop, width: l.width, height: TYPE.body.line * l.type }}>
      <CopyLine speed={c.bodyCopySpeed} before={COPY.bodyBefore} after={COPY.bodyAfter} progress={c.bodyCopy} target={c.copyTarget} reduced={reduced}
        style={{ fontFamily: TYPE.body.family, fontSize: TYPE.body.size * l.type, lineHeight: TYPE.body.line * l.type, color: PALETTE.body }} />
    </View>
    <Animated.View accessible accessibilityRole="text" accessibilityLabel={`Balance: ${c.text.balance.toLocaleString('en-US')} Lumens`}
      style={[{ position: 'absolute', left: l.capsule.x, top: l.capsule.y, width: l.capsule.width, height: l.capsule.height }, number]}>
      <View style={{ position: 'absolute', left: 44, top: (l.capsule.height - TYPE.number.line) / 2, width: numberWidth, height: TYPE.number.line }}>
        <RollingNumber value={c.value} digits={4} size={TYPE.number.size} line={TYPE.number.line} color={PALETTE.number} />
      </View>
    </Animated.View>
    <Animated.View style={[{ position: 'absolute', left: l.button.x, top: l.button.y, width: l.button.width, height: l.button.height, justifyContent: 'center' }, button]}>
      <View style={{ height: TYPE.button.line }}>
        <CopyLine speed={c.barLabelSpeed} before={COPY.buttonBefore} after={COPY.buttonAfter} progress={c.barLabel} target={c.barLabelTarget} reduced={reduced}
          style={{ fontFamily: TYPE.button.family, fontSize: TYPE.button.size, lineHeight: TYPE.button.line, color: PALETTE.buttonLabel }} />
      </View>
    </Animated.View>
  </Animated.View>;
}
const styles = StyleSheet.create({ line: { position: 'absolute', left: 0, right: 0, textAlign: 'center', includeFontPadding: false, paddingHorizontal: 24 }, word: { paddingHorizontal: 0, textAlign: 'left' } });
