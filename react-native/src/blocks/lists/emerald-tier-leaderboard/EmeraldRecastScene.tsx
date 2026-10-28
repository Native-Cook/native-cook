import { useState, type ComponentType } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import Animated, { useAnimatedProps, useAnimatedReaction, useAnimatedStyle, useDerivedValue, type SharedValue } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';
import { ArtMaterial } from './EmeraldMaterials';
import { EmeraldRecastSurface } from './EmeraldRecastSurface';
import { PROMOTION_LINE_Y, recastFrame, type RecastFrame } from './recastMotion';
import type { EmeraldFrame } from './motion';

const CROWN = require('./assets/gold-crown.png');
const AnimatedInput = Animated.createAnimatedComponent(TextInput);
type ActorId = 'ari' | 'milo' | 'juno' | 'theo';
const IDS: ActorId[] = ['ari', 'milo', 'juno', 'theo'];
const LABELS = { ari: 'Ari Vale', milo: 'Milo Chen', juno: 'Juno Reed', theo: 'Theo Park' };
const INITIAL_SCORES = { ari: 1000, milo: 950, juno: 900, theo: 850 };
type AvatarComponent = ComponentType<{ name: ActorId; size: number }>;

function RecastParticipant({ id, frame, Avatar, winner }: {
  id: ActorId; frame: SharedValue<RecastFrame>; Avatar: AvatarComponent; winner: boolean;
}) {
  const faceStyle = useAnimatedStyle(() => {
    const a = frame.get().actors[id];
    return { transform: [{ translateX: a.cx - 35 }, { translateY: a.cy - 35 }, { scale: a.size / 70 }] };
  });
  const nameStyle = useAnimatedStyle(() => {
    const a = frame.get().actors[id];
    return { opacity: a.labelOpacity, textAlign: a.rowMix > .5 ? 'left' as const : 'center' as const,
      transform: [{ translateX: a.nameX }, { translateY: a.nameY }] };
  });
  const scoreStyle = useAnimatedStyle(() => {
    const a = frame.get().actors[id];
    return { opacity: a.labelOpacity, transform: [{ translateX: a.scoreX - 27 }, { translateY: a.scoreY }] };
  });
  const rowRankStyle = useAnimatedStyle(() => {
    const a = frame.get().actors[id];
    return { opacity: a.rowMix * a.labelOpacity,
      transform: [{ translateX: 33 }, { translateY: 303 }] };
  });
  const scoreProps = useAnimatedProps(() => {
    const value = String(frame.get().actors[id].score);
    return { text: value, defaultValue: value };
  });
  const semanticScore = id === 'theo' && winner ? 1050 : INITIAL_SCORES[id];
  return <View pointerEvents="none" style={StyleSheet.absoluteFill}>
    <Animated.View style={[styles.face, { zIndex: id === 'theo' ? 12 : 10 }, faceStyle]}
      accessible accessibilityLabel={`${LABELS[id]}, rank ${winner ? ({ ari: 2, milo: 3, juno: 4, theo: 1 }[id]) : ({ ari: 1, milo: 2, juno: 3, theo: 4 }[id])}, ${semanticScore} points`}>
      <Avatar name={id} size={70} />
    </Animated.View>
    <Animated.Text maxFontSizeMultiplier={1} accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" numberOfLines={1} style={[styles.name, nameStyle]}>{LABELS[id]}</Animated.Text>
    <Animated.View style={[styles.scoreLine, scoreStyle]} accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {id === 'theo' ? <AnimatedInput maxFontSizeMultiplier={1} editable={false} caretHidden accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" underlineColorAndroid="transparent"
        defaultValue="850" animatedProps={scoreProps} style={styles.scoreInput} />
        : <Text maxFontSizeMultiplier={1} style={styles.score}>{INITIAL_SCORES[id]}</Text>}
      <Text maxFontSizeMultiplier={1} style={styles.bolt}>ϟ</Text>
    </Animated.View>
    <Animated.Text maxFontSizeMultiplier={1} accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.rowRank, rowRankStyle]}>04</Animated.Text>
  </View>;
}

const RANK_COLORS: Record<number, string> = { 1: '#FFD66C', 2: '#BBBDB6', 3: '#BD8C66', 4: '#9EA39E' };
const RANKS: Record<ActorId, readonly [number, number]> = { ari: [1, 2], milo: [2, 3], juno: [3, 4], theo: [4, 1] };
const RANK_LINE = 40;

/** Each plinth keeps its numeral and rolls it to the new order, like a mechanical counter. */
function RecastRank({ id, frame }: { id: ActorId; frame: SharedValue<RecastFrame> }) {
  const [from, to] = RANKS[id];
  const style = useAnimatedStyle(() => {
    const f = frame.get();
    const b = f.surfaces[id];
    return { opacity: b.rankOpacity * (1 - f.actors[id].rowMix), transform: [{ translateX: b.x + b.width / 2 - 36 },
      { translateY: b.y + b.height / 2 - 20 }] };
  });
  const strip = useAnimatedStyle(() => ({ transform: [{ translateY: -frame.get().roll * RANK_LINE }] }));
  // Laurels belong to first place: they leave Ari's plinth and grow on Theo's.
  const laurel = useAnimatedStyle(() => {
    const r = frame.get().roll;
    const shown = from === 1 ? 1 - r : to === 1 ? r : 0;
    return { opacity: shown, transform: [{ scale: 0.7 + 0.3 * shown }] };
  });
  return <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.rankWrap, style]}>
    {from === 1 || to === 1 ? <Animated.Text maxFontSizeMultiplier={1} style={[styles.laurel, laurel]}>❧</Animated.Text> : null}
    <View style={styles.rankWindow}>
      <Animated.View style={strip}>
        {[from, to].map((rank, index) => <Text key={index} maxFontSizeMultiplier={1} style={[styles.rank, { color: RANK_COLORS[rank] }]}>{rank}</Text>)}
      </Animated.View>
    </View>
    {from === 1 || to === 1 ? <Animated.Text maxFontSizeMultiplier={1} style={[styles.laurel, styles.laurelMirror, laurel]}>❧</Animated.Text> : null}
  </Animated.View>;
}

/** The boundary between the podium (promoted) and the list lights up where someone crosses it. */
function PromotionLine({ frame }: { frame: SharedValue<RecastFrame> }) {
  const rising = useAnimatedStyle(() => {
    const a = frame.get().actors.theo;
    const near = Math.exp(-Math.pow((a.cy - PROMOTION_LINE_Y) / 22, 2));
    return { opacity: near, transform: [{ translateX: a.cx - 60 }, { scaleX: 0.6 + near * 0.4 }] };
  });
  const falling = useAnimatedStyle(() => {
    const a = frame.get().actors.juno;
    const near = Math.exp(-Math.pow((a.cy - PROMOTION_LINE_Y) / 22, 2));
    return { opacity: near * 0.55, transform: [{ translateX: a.cx - 60 }, { scaleX: 0.6 + near * 0.4 }] };
  });
  return <View pointerEvents="none" style={styles.promotion} accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    <View style={styles.promotionRail} />
    <Animated.View style={[styles.promotionFlare, rising]}><View style={styles.promotionFlareCore} /></Animated.View>
    <Animated.View style={[styles.promotionFlare, falling]}><View style={[styles.promotionFlareCore, styles.promotionFlareCool]} /></Animated.View>
    <View style={styles.promotionTag}><Text maxFontSizeMultiplier={1} style={styles.promotionText}>▲ PROMOTION ZONE</Text></View>
  </View>;
}

const SPARKS = [0, 1, 2, 3, 4, 5];
function CrownSpark({ index, frame }: { index: number; frame: SharedValue<RecastFrame> }) {
  const angle = -Math.PI / 2 + (index - 2.5) * 0.5;
  const style = useAnimatedStyle(() => {
    const p = frame.get().crown.sparkle;
    const reach = 10 + 16 * (1 - Math.pow(1 - p, 2));
    return { opacity: p > 0 && p < 1 ? Math.sin(p * Math.PI) : 0,
      transform: [{ translateX: Math.cos(angle) * reach }, { translateY: Math.sin(angle) * reach }, { rotate: '45deg' }, { scale: 1 - p * 0.6 }] };
  });
  return <Animated.View style={[styles.spark, style]} />;
}

export function EmeraldRecastScene({ frame: originalFrame, renderer, solid, onRendererStatus, Avatar }: {
  frame: SharedValue<EmeraldFrame>; renderer: 'auto' | 'skia'; solid: boolean;
  onRendererStatus?: (status: string) => void; Avatar: AvatarComponent;
}) {
  const frame = useDerivedValue(() => {
    const source = originalFrame.get();
    const next = recastFrame(source.progress, source.reduced);
    if (!source.allowResponse) { next.response = 0; next.light.response = 0; }
    return next;
  });
  const edgeStyle = useAnimatedStyle(() => ({ transform: [{ translateY: originalFrame.get().reduced ? 0 :
    (originalFrame.get().progress - originalFrame.get().rawProgress) * 18 }] }));
  const [winner, setWinner] = useState(false);
  useAnimatedReaction(() => frame.get().phase >= .90, (next, previous) => {
    if (next !== previous) scheduleOnRN(setWinner, next);
  });
  const crownStyle = useAnimatedStyle(() => {
    const c = frame.get().crown;
    return { opacity: c.opacity, transform: [{ translateX: c.x }, { translateY: c.y }] };
  });
  const crownBody = useAnimatedStyle(() => {
    const c = frame.get().crown;
    return { transform: [{ rotate: `${c.rotation}deg` }, { scaleX: 1 + 0.09 * c.squash }, { scaleY: 1 - 0.13 * c.squash }] };
  });
  const gainStyle = useAnimatedStyle(() => {
    const gain = frame.get().gain;
    return { opacity: gain.opacity, transform: [{ translateX: gain.x }, { translateY: gain.y }] };
  });
  return <Animated.View style={[styles.stage, edgeStyle]} testID="emerald-rank-recast">
    <EmeraldRecastSurface frame={frame} renderer={renderer} solid={solid} onRendererStatus={onRendererStatus} />
    <PromotionLine frame={frame} />
    {IDS.map(id => <RecastRank key={`rank-${id}`} id={id} frame={frame} />)}
    {IDS.map(id => <RecastParticipant key={id} id={id} frame={frame} Avatar={Avatar} winner={winner} />)}
    <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.crown, crownStyle]}>
      <Animated.View style={[styles.crownBody, crownBody]}><ArtMaterial asset={CROWN} width={34} height={30} frame={originalFrame} /></Animated.View>
      <View style={styles.sparks}>{SPARKS.map(index => <CrownSpark key={index} index={index} frame={frame} />)}</View>
    </Animated.View>
    <Animated.View pointerEvents="none" accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={[styles.gain, gainStyle]}><Text maxFontSizeMultiplier={1} style={styles.gainText}>+200 XP</Text></Animated.View>
  </Animated.View>;
}

const styles = StyleSheet.create({
  stage: { width: 393, height: 335 },
  face: { position: 'absolute', width: 70, height: 70, left: 0, top: 0 },
  name: { position: 'absolute', left: 0, top: 0, width: 110, height: 18, zIndex: 14,
    color: '#FFFFFF', fontSize: 12, lineHeight: 17, fontWeight: '600', letterSpacing: -.1 },
  scoreLine: { position: 'absolute', left: 0, top: 0, width: 54, height: 21, zIndex: 14,
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 3 },
  score: { color: '#FFFFFF', fontSize: 14, lineHeight: 19, fontVariant: ['tabular-nums'] },
  scoreInput: { width: 35, height: 21, padding: 0, margin: 0, textAlign: 'right',
    color: '#FFFFFF', fontSize: 14, lineHeight: 19, fontVariant: ['tabular-nums'] },
  bolt: { color: '#FFC846', fontSize: 19, lineHeight: 21, fontWeight: '700' },
  rowRank: { position: 'absolute', left: 0, top: 0, color: '#B5B9B5', fontSize: 12, lineHeight: 18, zIndex: 14 },
  rankWrap: { position: 'absolute', left: 0, top: 0, width: 72, height: 40,
    flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 5, zIndex: 8 },
  rank: { height: RANK_LINE, lineHeight: RANK_LINE, fontSize: 29, fontWeight: '700', fontVariant: ['tabular-nums'], textAlign: 'center' },
  rankWindow: { height: RANK_LINE, minWidth: 22, overflow: 'hidden' },
  laurel: { fontSize: 25, color: '#BDA448' },
  laurelMirror: { transform: [{ scaleX: -1 }] },
  crownBody: { width: 34, height: 30, transformOrigin: '50% 90%' },
  sparks: { position: 'absolute', left: 17, top: 18, width: 0, height: 0 },
  spark: { position: 'absolute', left: -2.5, top: -2.5, width: 5, height: 5, borderRadius: 1, backgroundColor: '#FFE7A3' },
  promotion: { position: 'absolute', left: 19, top: PROMOTION_LINE_Y - 7, width: 355, height: 14, zIndex: 9, justifyContent: 'center' },
  promotionRail: { position: 'absolute', left: 0, right: 96, top: 6.5, height: 1, backgroundColor: '#7DF0B526' },
  promotionFlare: { position: 'absolute', left: -19, top: 0, width: 120, height: 14, alignItems: 'center', justifyContent: 'center' },
  promotionFlareCore: { width: 120, height: 2, borderRadius: 1, backgroundColor: '#B9FFD9', shadowColor: '#5CF2A8', shadowOpacity: 0.9, shadowRadius: 6, shadowOffset: { width: 0, height: 0 } },
  promotionFlareCool: { backgroundColor: '#E8F3EE', shadowColor: '#9DB5AA' },
  promotionTag: { position: 'absolute', right: 0, top: 0, height: 14, justifyContent: 'center' },
  promotionText: { color: '#7DF0B5', fontSize: 7.5, fontWeight: '700', letterSpacing: 1.1 },
  crown: { position: 'absolute', top: 0, left: 0, width: 34, height: 30, zIndex: 18 },
  gain: { position: 'absolute', top: 0, left: 0, width: 68, height: 19, zIndex: 16, alignItems: 'center' },
  gainText: { color: '#B2F9D8', fontSize: 10, lineHeight: 15, fontWeight: '600' },
});
