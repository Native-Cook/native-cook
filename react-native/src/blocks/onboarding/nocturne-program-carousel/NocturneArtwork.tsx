import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { interpolate, type SharedValue, useAnimatedProps, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle, Defs, G, LinearGradient as SvgGradient, Path, Stop } from 'react-native-svg';

import { enter, segment } from './motion';

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

export function NocturneMark({ size = 34 }: { size?: number }) {
  return <Svg width={size} height={size} viewBox="0 0 40 40"><Path d="M23 5C13 5 7 12 7 20s6 15 16 15c4 0 7-1 10-4-12 3-20-7-15-17 1-3 3-6 5-9Z" fill="#6FE8D6" /><Path d="m27 11 2 5 5 2-5 2-2 5-2-5-5-2 5-2Z" fill="#6493FF" /></Svg>;
}

function PlanStrip({ index, time, reduced }: { index: number; time: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const p = reduced ? 1 : enter(segment(time.get(), 2.82 + index * .1, 3.25 + index * .1));
    return { opacity: p * [1, .42, .1][index], transform: [{ translateX: -18 * (1 - p) }] };
  });
  return <Animated.View style={[art.strip, { top: 42 + index * 47, width: 220 - index * 13 }, style]}><Text allowFontScaling={false} style={art.stripNumber}>0{index + 1}</Text><Text allowFontScaling={false} style={art.stripText}>{['Unwind gently', 'Make room for rest', 'Find your rhythm'][index]}</Text></Animated.View>;
}

export function GuidePortrait({ time, reduced, height }: { time: SharedValue<number>; reduced: boolean; height: number }) {
  const portrait = useAnimatedStyle(() => ({ opacity: reduced ? 1 : enter(segment(time.get(), 2.25, 2.9)), transform: [{ translateY: reduced ? 0 : 18 * (1 - enter(segment(time.get(), 2.25, 3.1))) }] }));
  const bookmark = useAnimatedStyle(() => ({ opacity: reduced ? 1 : segment(time.get(), 3.8, 4.1), transform: [{ translateY: reduced ? 0 : -8 * (1 - segment(time.get(), 3.8, 4.1)) }] }));
  return <View style={{ height, overflow: 'hidden' }} accessibilityLabel="An evening guide stands in front of three numbered routine steps.">
    {[0, 1, 2].map(index => <PlanStrip key={index} index={index} time={time} reduced={reduced} />)}
    <Animated.View style={[StyleSheet.absoluteFill, portrait]}><Image source={require('./assets/evening-guide.webp')} style={{ position: 'absolute', height: height + 26, width: height * .86, right: -10, top: 8 }} contentFit="contain" /></Animated.View>
    <LinearGradient colors={['#10275A00', '#10234C', '#0F2043']} locations={[0, .78, 1]} style={{ position: 'absolute', bottom: -1, left: 0, right: 0, height: height * .32 }} />
    <Animated.View style={[art.bookmark, bookmark]}><Svg width={22} height={32} viewBox="0 0 22 32"><Path d="M2 0h18v30l-9-6-9 6Z" fill="#EAD899" /><Path d="m11 7 1.4 3 3.6.4-2.6 2.5.6 3.5-3-1.6L8 16.4l.6-3.5L6 10.4l3.6-.4Z" fill="#AC8C46" /></Svg></Animated.View>
  </View>;
}

function ScoreDigit({ time, reduced, tens }: { time: SharedValue<number>; reduced: boolean; tens: boolean }) {
  const style = useAnimatedStyle(() => {
    const value = reduced ? (tens ? 7 : 8) : tens
      ? interpolate(time.get(), [5.25, 6.25, 6.5, 6.583, 6.75, 7, 7.35], [0, 0, 1.4, 2.4, 4.4, 6.2, 7], 'clamp')
      : interpolate(time.get(), [5.25, 5.75, 6, 6.25, 6.5, 6.9, 7.1], [0, .6, 2.2, 5.2, 7, 7.85, 8], 'clamp');
    return { transform: [{ translateY: -value * 32 }] };
  });
  return <View style={{ width: 22, height: 32, overflow: 'hidden' }}><Animated.View style={style}>{Array.from({ length: 10 }, (_, digit) => <Text allowFontScaling={false} key={digit} style={{ height: 32, lineHeight: 32, color: '#DCECF7', fontSize: 27, fontWeight: '700', textAlign: 'center', fontVariant: ['tabular-nums'] }}>{digit}</Text>)}</Animated.View></View>;
}

export function MiniDashboard({ time, reduced, height }: { time: SharedValue<number>; reduced: boolean; height: number }) {
  const ring = useAnimatedProps(() => ({ strokeDashoffset: 226.2 * (1 - (reduced ? .78 : interpolate(time.get(), [5.25, 5.75, 6, 6.25, 6.5, 6.9, 7.4], [0, .04, .2, .44, .64, .78, .78], 'clamp'))) }));
  const emphasis = useAnimatedStyle(() => ({ transform: [{ scale: reduced ? 1.13 : 1 + .13 * enter(segment(time.get(), 7.25, 8)) }] }));
  const revealStyle = useAnimatedStyle(() => ({ opacity: reduced ? 1 : segment(time.get(), 5.1, 5.65), transform: [{ translateY: reduced ? 0 : 16 * (1 - enter(segment(time.get(), 5.1, 5.8))) }] }));
  return <View style={{ height, alignItems: 'center', overflow: 'hidden' }} accessibilityLabel="Illustrative evening check-in. Routine score 78, wind-down time 22 minutes, seven-day consistency chart.">
    <Animated.View style={[art.phone, { height: height + 30, width: 204 }, revealStyle]}>
      <View style={art.speaker} /><View style={art.phoneTop}><Text allowFontScaling={false} style={art.phoneTime}>9:41</Text><Text allowFontScaling={false} style={art.phoneTime}>••• ▰</Text></View>
      <Text allowFontScaling={false} style={art.phoneTitle}>Your evening</Text><Text allowFontScaling={false} style={art.phoneDate}>MONDAY, OCT 26</Text>
      <View style={art.week}>{['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((day, i) => <View key={i} style={[art.day, i === 0 && { borderColor: '#64E0CE' }]}><Text allowFontScaling={false} style={art.dayLabel}>{day}</Text></View>)}</View>
      <View style={art.scoreRow}><Animated.View style={[art.score, emphasis]}>
        <Svg width={90} height={90} viewBox="0 0 90 90"><Defs><SvgGradient id="scoreArc" x1="1" y1="0" x2="0" y2="0"><Stop offset="0" stopColor="#397AFF" /><Stop offset="1" stopColor="#6AD9DE" /></SvgGradient></Defs><Circle cx={45} cy={45} r={36} stroke="#173044" strokeWidth={4} fill="none" /><AnimatedCircle cx={45} cy={45} r={36} stroke="url(#scoreArc)" strokeWidth={4} strokeLinecap="round" strokeDasharray="226.2 226.2" rotation={-90} origin="45,45" fill="none" animatedProps={ring} /></Svg>
        <View style={{ position: 'absolute', top: 24, left: 23, flexDirection: 'row', height: 32 }}><ScoreDigit time={time} reduced={reduced} tens /><ScoreDigit time={time} reduced={reduced} tens={false} /></View>
        <Text allowFontScaling={false} style={art.scoreCaption}>ROUTINE</Text>
      </Animated.View><View style={{ gap: 12 }}><View><Text allowFontScaling={false} style={art.metricLabel}>Wind-down</Text><Text allowFontScaling={false} style={art.metric}>22 min</Text></View><View><Text allowFontScaling={false} style={art.metricLabel}>This week</Text><Text allowFontScaling={false} style={art.metric}>5 evenings</Text></View></View></View>
      <Text allowFontScaling={false} style={art.chartTitle}>A little more consistent</Text><View style={art.chart}>{[18, 31, 24, 42, 36, 53, 48].map((h, i) => <View key={i} style={[art.chartBar, { height: h, backgroundColor: i === 6 ? '#6FE8D6' : '#28617B' }]} />)}</View>
      <Text allowFontScaling={false} style={art.sampleLabel}>SAMPLE CHECK-IN · NOT A HEALTH SCORE</Text>
    </Animated.View>
    <LinearGradient colors={['#0F204300', '#0F2043']} style={{ position: 'absolute', bottom: -1, left: 0, right: 0, height: 45 }} />
  </View>;
}

function Hotspot({ x, y, phase, time, reduced }: { x: number; y: number; phase: number; time: SharedValue<number>; reduced: boolean }) {
  const style = useAnimatedStyle(() => {
    const t = time.get();
    const shown = reduced ? .75 : segment(t, 11.6 + phase * .25, 12.2 + phase * .25);
    const pulse = reduced ? 0 : Math.sin(t * Math.PI * 1.4 + phase * 2) * .5 + .5;
    return { opacity: shown * (.7 + .3 * pulse), transform: [{ scale: .85 + .3 * pulse }] };
  });
  return <Animated.View pointerEvents="none" style={[art.hotspot, { left: `${x * 100}%`, top: `${y * 100}%` }, style]}>
    <View style={art.hotspotHalo} /><View style={art.hotspotCore} />
  </Animated.View>;
}

// Card 3: a translucent hologram of a body over a blueprint grid, framed by corner brackets,
// with a scan line that travels down and back up before three points of attention light up.
export function BodyScan({ time, reduced, height }: { time: SharedValue<number>; reduced: boolean; height: number }) {
  const scan = useAnimatedStyle(() => {
    const t = time.get();
    const down = segment(t, 10.5, 11.15), up = segment(t, 12.6, 13.25);
    const p = t < 12 ? down : 1 - up;
    const visible = reduced ? 0 : Math.min(1, segment(t, 10.45, 10.6) * (1 - segment(t, 11.1, 11.3)) + segment(t, 12.55, 12.7) * (1 - segment(t, 13.15, 13.35)));
    return { opacity: visible, transform: [{ translateY: height * (.06 + .62 * p) }] };
  });
  const figure = useAnimatedStyle(() => ({ opacity: reduced ? 1 : segment(time.get(), 10.2, 10.8), transform: [{ scale: reduced ? 1 : 1.04 - .04 * enter(segment(time.get(), 10.2, 11.2)) }] }));
  const brackets = useAnimatedStyle(() => ({ opacity: reduced ? 1 : segment(time.get(), 10.9, 11.3), transform: [{ scale: reduced ? 1 : 1.06 - .06 * enter(segment(time.get(), 10.9, 11.4)) }] }));
  return <View style={{ height, overflow: 'hidden' }} accessibilityLabel="A translucent blue figure on a scanning grid with three gentle points of attention.">
    <View style={[StyleSheet.absoluteFill, { backgroundColor: '#0E2A66' }]} />
    <Animated.View style={[StyleSheet.absoluteFill, figure]}>
      <View style={{ position: 'absolute', top: 0, left: 0, right: 0, alignItems: 'center' }}><Image source={require('./assets/body-hologram.webp')} style={{ height: height * 1.38, width: height * .92 }} contentFit="cover" transition={0} /></View>
    </Animated.View>
    <Svg pointerEvents="none" width="100%" height={height} style={StyleSheet.absoluteFill}>
      <G stroke="#5D8CE6" strokeWidth={.5} opacity={.22}>{Array.from({ length: 18 }, (_, i) => <Path key={`h${i}`} d={`M0 ${i * 18}H400`} />)}{Array.from({ length: 22 }, (_, i) => <Path key={`v${i}`} d={`M${i * 18} 0V${height}`} />)}</G>
    </Svg>
    <LinearGradient pointerEvents="none" colors={['#0B2A7000', '#0A2560']} style={{ position: 'absolute', left: 0, right: 0, bottom: -1, height: height * .3 }} />
    <Animated.View pointerEvents="none" style={[art.brackets, brackets]}>
      <View style={[art.corner, { top: 0, left: 0, borderTopWidth: 2.5, borderLeftWidth: 2.5, borderTopLeftRadius: 6 }]} />
      <View style={[art.corner, { top: 0, right: 0, borderTopWidth: 2.5, borderRightWidth: 2.5, borderTopRightRadius: 6 }]} />
    </Animated.View>
    <Hotspot x={.5} y={.22} phase={0} time={time} reduced={reduced} /><Hotspot x={.585} y={.56} phase={1} time={time} reduced={reduced} /><Hotspot x={.48} y={.86} phase={2} time={time} reduced={reduced} />
    <Animated.View pointerEvents="none" style={[art.scan, scan]}><LinearGradient colors={['#5FF3E000', '#5FF3E0', '#5FF3E000']} start={{ x: 0, y: .5 }} end={{ x: 1, y: .5 }} style={{ height: 2 }} /><LinearGradient colors={['#5FF3E033', '#5FF3E000']} style={{ height: 26 }} /></Animated.View>
  </View>;
}

const TABS = ['Home', 'Program', 'Sleep', 'Statistic', 'Profile'] as const;
function TabIcon({ name, color }: { name: typeof TABS[number]; color: string }) {
  if (name === 'Program') return <NocturneMark size={22} />;
  return <Svg width={22} height={22} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    {name === 'Home' ? <Path d="M4 11.5 12 5l8 6.5V20a1 1 0 0 1-1 1h-4.5v-5.5h-5V21H5a1 1 0 0 1-1-1Z" fill={color} stroke="none" /> : name === 'Statistic' ? <Path d="M3 13h4l3-7 4 13 3-6h4" /> : <><Circle cx={12} cy={12} r={9} fill={color} stroke="none" /><Circle cx={9} cy={10} r={1.2} fill="#010A16" stroke="none" /><Circle cx={15} cy={10} r={1.2} fill="#010A16" stroke="none" /><Path d="M8.5 14.2c1.8 2 5.2 2 7 0" stroke="#010A16" strokeWidth={1.6} /></>}
  </Svg>;
}

/** The app's tab bar, as the reference shows it: Program is the current tab, Sleep the raised action. */
export function TabBar() {
  return <View style={art.tabBar} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
    {TABS.map(name => name === 'Sleep'
      ? <View key={name} style={art.tab}><LinearGradient colors={['#5B8DFF', '#2D57E8']} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={art.sleep}><Svg width={26} height={26} viewBox="0 0 24 24"><Path d="M14.5 3.5a8.5 8.5 0 1 0 6 13.9A7 7 0 0 1 14.5 3.5Z" fill="#FFFFFF" /><Circle cx={17.6} cy={7} r={1.4} fill="#FFFFFF" /></Svg></LinearGradient><Text allowFontScaling={false} style={[art.tabLabel, { color: '#334263' }]}>Sleep</Text></View>
      : <View key={name} style={art.tab}><TabIcon name={name} color={name === 'Program' ? '#FFFFFF' : '#3A4866'} /><Text allowFontScaling={false} style={[art.tabLabel, name === 'Program' && { color: '#FFFFFF' }]}>{name}</Text></View>)}
  </View>;
}

const art = StyleSheet.create({
  hotspot: { position: 'absolute', width: 34, height: 34, marginLeft: -17, marginTop: -17, alignItems: 'center', justifyContent: 'center' }, hotspotHalo: { position: 'absolute', width: 34, height: 34, borderRadius: 17, backgroundColor: '#FF4FA8', opacity: .22 }, hotspotCore: { width: 9, height: 9, borderRadius: 5, backgroundColor: '#FF8FD0', boxShadow: '0 0 10px #FF4FA8' },
  brackets: { position: 'absolute', left: 14, right: 14, top: 4, height: 60 }, corner: { position: 'absolute', width: 30, height: 30, borderColor: '#2F7BFF' },
  scan: { position: 'absolute', left: 8, right: 8, top: 0 },
  tabBar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginHorizontal: -14, paddingTop: 2 }, tab: { flex: 1, alignItems: 'center', gap: 5 }, tabLabel: { color: '#3A4866', fontSize: 10, fontWeight: '600' }, sleep: { width: 66, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', marginTop: -16, boxShadow: '0 8px 22px #2D57E880' },
  strip: { position: 'absolute', left: 8, height: 35, borderRadius: 7, backgroundColor: '#2F62C68C', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 10, gap: 10, borderTopWidth: 1, borderTopColor: '#5C89D94D' },
  stripNumber: { color: '#A5C4FF', fontSize: 11, fontWeight: '700' }, stripText: { color: '#BDD0F2', fontSize: 11 }, bookmark: { position: 'absolute', right: 10, top: 0 },
  phone: { top: 12, paddingHorizontal: 13, paddingTop: 16, borderRadius: 25, borderWidth: 3, borderColor: '#344357', backgroundColor: '#071421', boxShadow: '0 6px 12px #02091580' }, speaker: { position: 'absolute', top: 5, left: 78, width: 43, height: 4, borderRadius: 3, backgroundColor: '#020A13' },
  phoneTop: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 }, phoneTime: { color: '#AFC2D4', fontSize: 7, fontWeight: '700' }, phoneTitle: { color: '#E2EDFA', fontSize: 14, fontWeight: '700' }, phoneDate: { color: '#7492AB', fontSize: 7, letterSpacing: 1, marginTop: 5 }, week: { flexDirection: 'row', gap: 6, marginTop: 12 }, day: { width: 17, height: 17, borderRadius: 9, borderWidth: 1, borderColor: '#27435C', alignItems: 'center', justifyContent: 'center' }, dayLabel: { color: '#85A3BA', fontSize: 7 },
  scoreRow: { flexDirection: 'row', alignItems: 'center', marginTop: 9, gap: 6 }, score: { width: 90, height: 90 }, scoreDigits: { position: 'absolute', top: 23, left: 17, width: 56, height: 38, color: '#DCECF7', fontSize: 27, fontWeight: '700', padding: 0, textAlign: 'center', fontVariant: ['tabular-nums'] }, scoreCaption: { position: 'absolute', top: 59, left: 19, width: 52, color: '#6F96A8', fontSize: 6, textAlign: 'center', letterSpacing: 1 }, metricLabel: { color: '#7897AA', fontSize: 8 }, metric: { color: '#D0E2EA', fontSize: 11, fontWeight: '600', marginTop: 3 }, chartTitle: { color: '#86A0B3', fontSize: 8, marginTop: 12 }, chart: { height: 60, flexDirection: 'row', gap: 9, alignItems: 'flex-end', paddingHorizontal: 3, marginTop: 8, borderBottomColor: '#1D374B', borderBottomWidth: 1 }, chartBar: { width: 13, borderTopLeftRadius: 3, borderTopRightRadius: 3 }, sampleLabel: { color: '#567486', fontSize: 5, letterSpacing: .25, marginTop: 10 },
});
