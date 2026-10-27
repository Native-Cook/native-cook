import { Blur, Canvas, Group, Path as SkiaPath, Skia } from '@shopify/react-native-skia';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useDerivedValue } from 'react-native-reanimated';
import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { clamp } from './motion';

const TAU = Math.PI * 2;
function hash(index: number) {
  const value = Math.sin(index * 127.1 + 31.7) * 43758.5453;
  return value - Math.floor(value);
}
const PARTICLES = Array.from({ length: 240 }, (_, i) => {
  const size = hash(i + 661);
  return { x: hash(i + 1), y: hash(i + 221), phase: hash(i + 441) * TAU, radius: 1.1 + size * 2 + Math.pow(Math.max(0, (size - .72) / .28), 1.5) * 5 };
});
const DENSITY = [.4, 1, .52, .95, .2];
const EXTENT = [.48, .4, .33, .4, .22];

function ParticleLayer({ group, time, page, width, height }: { group: number; time: SharedValue<number>; page: SharedValue<number>; width: number; height: number }) {
  const color = useDerivedValue(() => {
    const mix = clamp(page.get());
    const warm = [[212, 46, 74], [239, 108, 92], [234, 116, 172]][group];
    const gray = [[51, 50, 57], [118, 116, 122], [197, 193, 198]][group];
    return `rgb(${Math.round(warm[0] + (gray[0] - warm[0]) * mix)},${Math.round(warm[1] + (gray[1] - warm[1]) * mix)},${Math.round(warm[2] + (gray[2] - warm[2]) * mix)})`;
  });
  const path = useDerivedValue(() => {
    const p = clamp(page.get(), 0, 4);
    const first = Math.floor(p);
    const second = Math.min(4, first + 1);
    const blend = p - first;
    const density = DENSITY[first] + (DENSITY[second] - DENSITY[first]) * blend;
    const extent = EXTENT[first] + (EXTENT[second] - EXTENT[first]) * blend;
    const phase = time.get() / 22 * TAU;
    const result = Skia.PathBuilder.Make();
    for (let i = group; i < PARTICLES.length; i += 3) {
      const seed = PARTICLES[i];
      const weight = clamp((density * PARTICLES.length - i) / 10 + 1);
      if (weight <= 0) continue;
      const spread = clamp(p - 3);
      const x = (seed.x * width + Math.sin(phase * (1 + i % 3) + seed.phase) * (8 + p * 2) - width / 2) * (1 + spread * .32) + width / 2;
      const y = 8 + Math.pow(seed.y, 1.9) * height * extent + Math.cos(phase * (2 + i % 3) + seed.phase) * 13 - spread * seed.y * 60;
      result.addCircle(x, y, seed.radius * weight);
    }
    return result.detach();
  });
  // The largest, palest layer sits out of focus, so the field reads with depth.
  return <SkiaPath path={path} color={color} opacity={[.82, .66, .5][group]}>{group === 2 && <Blur blur={1.8} />}</SkiaPath>;
}

/** 240 deterministic particles batched into three paths on one bounded canvas. */
export function SpectrumParticles({ time, page, width, height }: { time: SharedValue<number>; page: SharedValue<number>; width: number; height: number }) {
  const opacity = useDerivedValue(() => .78 + clamp(page.get()) * .22);
  return <Canvas style={{ position: 'absolute', left: 0, top: 0, width, height }} pointerEvents="none"><Group opacity={opacity}>{[0, 1, 2].map(group => <ParticleLayer key={group} group={group} time={time} page={page} width={width} height={height} />)}</Group></Canvas>;
}

export function SpectrumMark({ size = 72 }: { size?: number }) {
  return <Svg width={size} height={size} viewBox="0 0 72 72"><Circle cx={36} cy={36} r={35} fill="#17171B" />{Array.from({ length: 23 }, (_, i) => { const a = i * 2.4; const r = 5 + Math.sqrt(i / 22) * 19; return <Circle key={i} cx={36 + Math.cos(a) * r} cy={36 + Math.sin(a) * r} r={i % 4 === 0 ? 2.2 : 1.5} fill="#F7F6F2" />; })}</Svg>;
}

const BADGES = [
  { name: 'DAILY', color: '#2163D2', x: 50, y: 78, size: 30 }, { name: 'N', color: '#18171C', x: 129, y: 54, size: 33 },
  { name: 'POST', color: '#8B1B40', x: 209, y: 69, size: 29 }, { name: 'S', color: '#DB5839', x: 28, y: 113, size: 22 },
  { name: 'LOOP', color: '#143D36', x: 242, y: 114, size: 29 }, { name: 'F', color: '#1E2228', x: 69, y: 129, size: 26 },
  { name: 'ECHO', color: '#E2B589', x: 186, y: 105, size: 32 }, { name: 'R', color: '#5B92B7', x: 99, y: 87, size: 22 },
  { name: 'M', color: '#3D725B', x: 170, y: 43, size: 23 }, { name: 'O', color: '#AA629F', x: 235, y: 63, size: 21 },
];

function Badge({ index, time }: { index: number; time: SharedValue<number> }) {
  const badge = BADGES[index];
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: Math.sin(time.get() / 22 * TAU * 2 + index) * 3 }, { translateY: Math.cos(time.get() / 22 * TAU * 3 + index) * 4 }] }));
  return <Animated.View style={[art.badge, { left: badge.x, top: badge.y - 34, width: badge.size, height: badge.size, borderRadius: badge.size / 2, backgroundColor: badge.color }, style]}><Text allowFontScaling={false} style={[art.badgeText, { fontSize: badge.name.length > 1 ? 6 : 14, color: index === 6 ? '#7D4930' : '#FFFFFF' }]}>{badge.name}</Text></Animated.View>;
}

function NewsSources({ time }: { time: SharedValue<number> }) {
  return <View style={art.illustration}><View style={art.haloOuter} /><View style={art.haloInner} />{BADGES.map((_, i) => <Badge key={i} index={i} time={time} />)}<LinearGradient colors={['#24BDD8', '#137BCB', '#1962B4']} style={art.newsIcon}>
    <Svg width={40} height={40} viewBox="0 0 40 40"><Rect x={11} y={8} width={23} height={26} rx={2} stroke="#F2FDFF" strokeWidth={2} fill="none" /><Path d="M7 13H5v18q0 3 3 3h23M16 14h12M16 18h12M16 22h4v5h-4zM24 23h5M24 27h5" stroke="#F2FDFF" strokeWidth={2} fill="none" /></Svg>
  </LinearGradient></View>;
}

function TopicSelection({ scale }: { scale: number }) {
  return <View style={[art.illustration, { transform: [{ translateY: -26 / scale }] }]}><Svg width={76} height={91} style={{ position: 'absolute', top: 56, left: 112 }} viewBox="0 0 76 91"><Circle cx={38} cy={22} r={17} fill="#8C8C8C" /><Path d="M5 78c0-23 14-35 33-35s33 12 33 35q-33 9-66 0Z" fill="#969696" /></Svg>
    <View style={art.topicColumns}><View style={art.topicColumn}>{['Less noise', 'Celebrity', 'Hot takes'].map((label, i) => <View key={label} style={[art.topic, { alignSelf: 'flex-end', width: 107 - i * 13, backgroundColor: ['#B52C48', '#CF4057', '#F0C7CE'][i] }]}><Text allowFontScaling={false} style={[art.topicText, i === 2 && { color: '#9E5E68' }]}>{label} −</Text></View>)}</View><View style={art.topicDivider} /><View style={art.topicColumn}>{['Design', 'Our planet', 'Technology', 'Culture'].map((label, i) => <View key={label} style={[art.topic, { width: 103 - i * 11, backgroundColor: ['#7EBC52', '#96CB6F', '#AFD694', '#D0E7BA'][i] }]}><Text allowFontScaling={false} style={[art.topicText, { color: '#3B6C2B' }]}>+ {label}</Text></View>)}</View></View>
  </View>;
}

function AudioWave({ time }: { time: SharedValue<number> }) {
  const style = useAnimatedStyle(() => ({ opacity: .7 + Math.sin(time.get() / 22 * TAU * 9) * .25, transform: [{ scaleY: .8 + Math.sin(time.get() / 22 * TAU * 11) * .2 }] }));
  return <Animated.View style={[art.audioBars, style]}>{[7, 13, 18, 11, 6].map((height, i) => <View key={i} style={{ width: 2, height, borderRadius: 1, backgroundColor: '#E4F8FF' }} />)}</Animated.View>;
}

function StorySummary({ time }: { time: SharedValue<number> }) {
  return <View style={art.illustration}><View style={art.articleBack}><Text allowFontScaling={false} style={art.articleSource}>THE DAILY FIELD</Text><Text allowFontScaling={false} style={art.articleHeading}>A greener way{ '\n' }through the city</Text>{[1, 2, 3, 4, 5].map(i => <View key={i} style={[art.articleLine, { width: i === 5 ? 85 : 134 }]} />)}<View style={art.storyDot}><Text allowFontScaling={false} style={art.storyDotText}>F</Text></View></View>
    <LinearGradient colors={['#28BADB', '#168ACB', '#1B68B9']} style={art.summaryCard}><Text allowFontScaling={false} style={art.summaryLabel}>THE SHORT VERSION</Text>{[125, 104, 116, 87].map((w, i) => <View key={i} style={art.summaryRow}><View style={art.summaryBullet} /><View style={[art.summaryLine, { width: w }]} /></View>)}</LinearGradient>
    <View style={art.countPill}><Text allowFontScaling={false} style={art.countText}>16 perspectives</Text><View style={art.tinySources}><Text allowFontScaling={false} style={art.tinySourceText}>N  F</Text></View></View>
    <LinearGradient colors={['#30C2DD', '#1988C7']} style={art.audioChip}><AudioWave time={time} /><Svg width={13} height={16} viewBox="0 0 13 16"><Path d="M2 1 12 8 2 15Z" fill="#EAFBFF" /></Svg></LinearGradient>
  </View>;
}

function StoryQuestions() {
  return <View style={art.illustration}><View style={art.askPill}><Text allowFontScaling={false} style={{ fontSize: 12, color: '#389CB1' }}>⌕</Text><Text allowFontScaling={false} style={art.askText}>A little more context</Text></View><View style={art.photoCard}><Image source={require('./assets/waterfront-garden.webp')} style={art.photo} contentFit="cover" /><Text allowFontScaling={false} style={art.photoCaption}>ROOM FOR THE RIVER</Text></View><View style={art.questionCard}><View style={art.questionHeader}><Text allowFontScaling={false} style={art.questionTitle}>What changes when a city makes room for water?</Text></View><View style={art.answer}><Text allowFontScaling={false} style={art.answerText}>More public space.{ '\n' }A cooler walk home.{ '\n' }A river people can reach.</Text><Text allowFontScaling={false} style={art.answerSource}>3 source perspectives ↗</Text></View></View></View>;
}

export function SpectrumIllustration({ page, time, scale = 1 }: { page: number; time: SharedValue<number>; scale?: number }) {
  return <View style={{ width: 300 * scale, height: 285 * scale }}><View style={{ width: 300, height: 285, transform: [{ scale }], transformOrigin: 'top left' }}>{page === 0 ? <View style={art.welcomeMark}><SpectrumMark size={90} /><Text allowFontScaling={false} style={art.wordmark}>Spectrum</Text></View> : page === 1 ? <NewsSources time={time} /> : page === 2 ? <TopicSelection scale={scale} /> : page === 3 ? <StorySummary time={time} /> : <StoryQuestions />}</View></View>;
}

const art = StyleSheet.create({ illustration: { width: 300, height: 285 }, welcomeMark: { alignItems: 'center', paddingTop: 135, gap: 12 }, wordmark: { color: '#111111', fontSize: 27, fontWeight: '800', letterSpacing: -.6 }, haloOuter: { position: 'absolute', width: 230, height: 230, left: 35, top: 26, borderRadius: 115, backgroundColor: '#EAF2F84D' }, haloInner: { position: 'absolute', width: 175, height: 175, left: 62.5, top: 53.5, borderRadius: 88, backgroundColor: '#C4E6F15E' }, newsIcon: { position: 'absolute', width: 102, height: 102, left: 99, top: 79, borderRadius: 51, alignItems: 'center', justifyContent: 'center', boxShadow: '0 9px 14px #287A9E55, 0 2px 3px #1B659950' }, badge: { position: 'absolute', alignItems: 'center', justifyContent: 'center', boxShadow: '0 3px 5px #55556630' }, badgeText: { fontWeight: '800' }, topicColumns: { position: 'absolute', left: 22, right: 22, top: 160, flexDirection: 'row', gap: 10 }, topicColumn: { flex: 1, gap: 9 }, topic: { height: 17, borderRadius: 9, paddingHorizontal: 8, justifyContent: 'center' }, topicText: { color: '#FFF8F6', fontSize: 8, fontWeight: '600' }, topicDivider: { width: 2, height: 94, backgroundColor: '#D4D2CF' },
  articleBack: { position: 'absolute', left: 49, top: 90, width: 176, height: 166, padding: 17, backgroundColor: '#FDFCFA', borderRadius: 10, borderWidth: 1, borderColor: '#E7E5E0', transform: [{ rotate: '-6deg' }], boxShadow: '0 7px 16px #3C3F4A20' }, articleSource: { color: '#8D8B85', fontSize: 6, fontWeight: '700', letterSpacing: .9 }, articleHeading: { color: '#807F79', fontSize: 13, lineHeight: 17, fontWeight: '700', marginTop: 8, marginBottom: 8 }, articleLine: { height: 3, backgroundColor: '#D9D7D0', marginTop: 5, borderRadius: 1.5 }, storyDot: { position: 'absolute', left: 17, bottom: 14, width: 22, height: 22, borderRadius: 11, backgroundColor: '#354145', alignItems: 'center', justifyContent: 'center' }, storyDotText: { color: '#FFFFFF', fontSize: 11, fontWeight: '700' }, summaryCard: { position: 'absolute', right: 20, top: 61, width: 172, height: 128, borderRadius: 17, paddingHorizontal: 14, paddingVertical: 18, boxShadow: '0 12px 18px #1E75943F, 0 2px 3px #2F77962F' }, summaryLabel: { color: '#CCF4FE', fontSize: 7, letterSpacing: .8, fontWeight: '700', marginBottom: 13 }, summaryRow: { flexDirection: 'row', gap: 7, alignItems: 'center', marginBottom: 11 }, summaryBullet: { width: 5, height: 5, borderRadius: 2.5, backgroundColor: '#C5F2FF' }, summaryLine: { height: 5, borderRadius: 3, backgroundColor: '#B7EBFA' }, countPill: { position: 'absolute', left: 19, top: 196, height: 28, borderRadius: 14, backgroundColor: '#26A8D1', paddingHorizontal: 12, justifyContent: 'center', boxShadow: '0 4px 8px #237B9D40' }, countText: { color: '#F5FEFF', fontSize: 10, fontWeight: '600' }, tinySources: { position: 'absolute', right: -15, top: 18, borderRadius: 8, backgroundColor: '#4D6E80', paddingHorizontal: 5, paddingVertical: 2 }, tinySourceText: { color: '#D3E1EB', fontSize: 6 }, audioChip: { position: 'absolute', right: 37, top: 225, height: 32, width: 78, borderRadius: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, boxShadow: '0 5px 8px #21749340' }, audioBars: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  askPill: { position: 'absolute', top: 63, left: 73, width: 167, height: 29, borderRadius: 16, backgroundColor: '#FFFEFC', flexDirection: 'row', alignItems: 'center', gap: 7, paddingHorizontal: 12, boxShadow: '0 2px 8px #625A4D18' }, askText: { color: '#A6A5A0', fontSize: 9 }, photoCard: { position: 'absolute', left: 20, top: 105, width: 198, height: 142, borderRadius: 9, overflow: 'hidden', backgroundColor: '#E5E8DB', transform: [{ rotate: '-4deg' }], boxShadow: '0 8px 14px #3B362D28' }, photo: { width: '100%', height: 119 }, photoCaption: { color: '#5C675D', fontSize: 6, letterSpacing: 1.1, fontWeight: '700', marginLeft: 10, marginTop: 8 }, questionCard: { position: 'absolute', right: 13, top: 133, width: 169, borderRadius: 12, overflow: 'hidden', backgroundColor: '#FFFDFC', boxShadow: '0 8px 18px #3A312C28, 0 1px 2px #3A312C15' }, questionHeader: { paddingHorizontal: 12, paddingVertical: 11, backgroundColor: '#29292D' }, questionTitle: { color: '#F8F7F4', fontSize: 10, lineHeight: 13, fontWeight: '600' }, answer: { padding: 12 }, answerText: { color: '#7E7A73', fontSize: 9, lineHeight: 14 }, answerSource: { color: '#A7A19A', fontSize: 6, marginTop: 8 },
});
