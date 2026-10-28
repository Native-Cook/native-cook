import { StatusBar } from 'expo-status-bar';
import { useReducedMotionPreference } from '../../../shared/useReducedMotionPreference';
import * as Haptics from 'expo-haptics';
import { LinearGradient as ExpoLinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN, scheduleOnUI } from 'react-native-worklets';
import Animated, { useAnimatedProps, useAnimatedStyle, useFrameCallback, useSharedValue, type SharedValue } from 'react-native-reanimated';
import Svg, { Circle, G, Path, Rect, Text as SvgText } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

const LOOP = 8;
const CREAM = '#FFE7BD';
const clamp = (value: number, low: number, high: number) => { 'worklet'; return Math.max(low, Math.min(high, value)); };
const smooth = (value: number) => { 'worklet'; const u = clamp(value, 0, 1); return u * u * (3 - 2 * u); };
const envelope = (time: number) => { 'worklet'; return smooth((time - 1.4) / 1.8) * (1 - smooth((time - 4.7) / 1.8)); };

type Props = { autoPlay?: boolean; previewTime?: number };
const AnimatedSvgCircle = Animated.createAnimatedComponent(Circle);

function resetIris(pose: SharedValue<number>, clock: SharedValue<number>, mode: SharedValue<number>, previewTime: number | undefined, reduced: boolean) {
  'worklet';
  const time = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  clock.value = time;
  mode.value = 0;
  pose.value = reduced ? 1 : previewTime == null ? 0 : envelope(time);
}
function beginPull(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; start.value = pose.value; mode.value = 1;
}
function updatePull(pose: SharedValue<number>, start: SharedValue<number>, mode: SharedValue<number>, translationY: number) {
  'worklet'; mode.value = 1; pose.value = clamp(start.value + translationY / 180, 0, 1);
}
function releasePull(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>) {
  'worklet'; from.value = pose.value; elapsed.value = 0; mode.value = 2;
}
function setIris(pose: SharedValue<number>, from: SharedValue<number>, elapsed: SharedValue<number>, mode: SharedValue<number>, value: number) {
  'worklet'; pose.value = value; from.value = value; elapsed.value = 0; mode.value = 2;
}

export function SignalIrisLibrary({ autoPlay = false, previewTime }: Props) {
  const { width, height } = useWindowDimensions();
  const safe = useSafeAreaInsets();
  const reduced = useReducedMotionPreference();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [manualActive, setManualActive] = useState(false);
  const [selected, setSelected] = useState(0);
  const [playing, setPlaying] = useState(true);
  const scale = Math.min(width / 393, Math.max(0, height - safe.top - safe.bottom) / 760);
  const initialTime = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
  const pose = useSharedValue(reduced ? 1 : previewTime == null ? 0 : envelope(initialTime));
  const clock = useSharedValue(initialTime);
  const mode = useSharedValue(0);
  const dragStart = useSharedValue(0);
  const returnFrom = useSharedValue(0);
  const returnElapsed = useSharedValue(0);
  const clearManual = useCallback(() => setManualActive(false), []);
  const impact = useCallback(() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }, []);
  const selection = useCallback(() => { Haptics.selectionAsync(); }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', state => { const visible = state === 'active'; setActive(visible); if (visible) setManualActive(false); });
    return () => sub.remove();
  }, []);
  const frame = useFrameCallback(info => {
    const dt = Math.max(0, (info.timeSincePreviousFrame ?? 0) / 1000);
    if (mode.value === 2) {
      returnElapsed.value += dt;
      if (returnElapsed.value < 2) pose.value = returnFrom.value;
      else {
        const t = (returnElapsed.value - 2) / 0.45;
        pose.value = returnFrom.value * (1 - smooth(t));
        if (t >= 1) {
          pose.value = previewTime == null ? 0 : envelope(((previewTime % LOOP) + LOOP) % LOOP);
          clock.value = previewTime == null ? 0 : ((previewTime % LOOP) + LOOP) % LOOP;
          mode.value = 0;
          if (!autoPlay || previewTime != null) scheduleOnRN(clearManual);
        }
      }
      return;
    }
    if (mode.value === 0 && !autoPlay) { scheduleOnRN(clearManual); return; }
    if (mode.value === 0 && autoPlay && previewTime == null) {
      clock.value = (clock.value + dt) % LOOP;
      pose.value = envelope(clock.value);
    }
  }, false);

  useEffect(() => {
    scheduleOnUI(resetIris, pose, clock, mode, previewTime, reduced);
    scheduleOnRN(clearManual);
  }, [active, autoPlay, clearManual, clock, mode, pose, previewTime, reduced]);
  useEffect(() => {
    frame.setActive(active && !reduced && (previewTime == null || manualActive) && (autoPlay || manualActive));
  }, [active, autoPlay, frame, manualActive, previewTime, reduced]);

  const begin = useCallback(() => setManualActive(true), []);
  const pan = useMemo(() => Gesture.Pan().minDistance(1)
    .onStart(() => {
      if (reduced) return;
      beginPull(pose, dragStart, mode);
      scheduleOnRN(begin);
      scheduleOnRN(impact);
    })
    .onUpdate(event => {
      if (reduced) return;
      updatePull(pose, dragStart, mode, event.translationY);
    })
    .onFinalize(() => {
      if (reduced || mode.value !== 1) return;
      releasePull(pose, returnFrom, returnElapsed, mode);
    }), [begin, dragStart, impact, mode, pose, reduced, returnElapsed, returnFrom]);

  const accessibleIris = useCallback((value: number) => {
    if (reduced) return;
    setManualActive(true);
    scheduleOnUI(setIris, pose, returnFrom, returnElapsed, mode, value);
    selection();
  }, [mode, pose, reduced, returnElapsed, returnFrom, selection]);
  const apertureStyle = useIrisMaskStyle(pose);
  const artStyle = useIrisArtStyle(pose);
  const titleStyle = useCoverTitleStyle(pose);
  const selectEpisode = useCallback((episode: number) => {
    setSelected(episode);
    setPlaying(true);
    setManualActive(true);
    scheduleOnUI(releasePull, pose, returnFrom, returnElapsed, mode);
    selection();
  }, [mode, pose, returnElapsed, returnFrom, selection]);
  const interrupt = useCallback(() => {
    setManualActive(true);
    scheduleOnUI(releasePull, pose, returnFrom, returnElapsed, mode);
    selection();
  }, [mode, pose, returnElapsed, returnFrom, selection]);
  const togglePlay = useCallback(() => {
    setPlaying(value => !value);
    interrupt();
    impact();
  }, [impact, interrupt]);

  return (
    <View style={styles.root}>
      <StatusBar style="light" />
      <ExpoLinearGradient colors={['#F64624', '#F64624', '#571D14']} locations={[0, 0.5, 0.5001]} start={{ x: 0.5, y: 0 }} end={{ x: 0.5, y: 1 }} style={StyleSheet.absoluteFill} />
      <View style={[styles.stageOuter, { top: safe.top, bottom: safe.bottom }]}>
        <View style={[styles.stage, { transform: [{ scale }] }]}>
          <View style={styles.header}>
            <Text style={styles.book}>▤</Text>
            <Text style={styles.headerTitle}>SIGNAL / LIBRARY</Text>
            <Text style={styles.issue}>02</Text>
          </View>
          <GestureDetector gesture={pan}>
            <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel="New ways to notice, illustrated cover" accessibilityHint="Pull down to open the listening window" accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]} onAccessibilityAction={event => accessibleIris(event.nativeEvent.actionName === 'increment' ? 1 : 0)} style={styles.cover}>
              <Svg pointerEvents="none" width={393} height={330} viewBox="0 0 393 330" style={StyleSheet.absoluteFill}>
                <Path d="M330 0 V330 M0 80 H330 M-57 255 A175 175 0 1 1 293 255 A175 175 0 1 1 -57 255" fill="none" stroke="rgba(255,231,189,0.75)" strokeWidth={3} />
              </Svg>
              <View style={styles.irisFrame}>
                <Animated.View style={[styles.irisMask, apertureStyle]}>
                  <Animated.View style={[styles.irisArt, artStyle]}>
                    <IrisArtwork pose={pose} />
                  </Animated.View>
                </Animated.View>
                <Svg pointerEvents="none" width={300} height={300} viewBox="0 0 300 300" style={StyleSheet.absoluteFill}>
                  <AnimatedCircle pose={pose} />
                </Svg>
              </View>
              <Animated.Text style={[styles.coverTitle, titleStyle]}>New ways{`\n`}to notice</Animated.Text>
              <View style={styles.coverHandle} />
            </Animated.View>
          </GestureDetector>

          <View style={styles.bookCopy}>
            <Text style={styles.bookTitle}>New ways to notice</Text>
            <Text style={styles.bookSubtitle}>A field guide for curious ears.</Text>
          </View>
          <EpisodeRow episode={0} selected={selected === 0} playing={playing && selected === 0} onPress={() => selectEpisode(0)} />
          <EpisodeRow episode={1} selected={selected === 1} playing={playing && selected === 1} onPress={() => selectEpisode(1)} />
          <View style={styles.player}>
            <Thumbnail episode={selected} size={36} />
            <View style={styles.playerCopy}>
              <Text style={styles.playerTitle} numberOfLines={1}>{selected === 0 ? 'The quiet art of looking' : 'Good ideas take the long way'}</Text>
              <Text style={styles.playerState}>{playing ? 'Now playing · Signal Radio' : 'Paused · Signal Radio'}</Text>
            </View>
            <Pressable onPress={togglePlay} style={styles.playButton} accessibilityRole="button" accessibilityLabel={playing ? 'Pause' : 'Play'}>
              <Text style={styles.playGlyph}>{playing ? 'Ⅱ' : '▶'}</Text>
            </Pressable>
          </View>
          <Text style={styles.footer}>PULL THE COVER TO LOOK CLOSER</Text>
        </View>
      </View>
    </View>
  );
}

function useIrisMaskStyle(pose: SharedValue<number>) {
  return useAnimatedStyle(() => ({ width: 300 * pose.value, height: 300 * pose.value, borderRadius: 150 * pose.value }));
}
function useIrisArtStyle(pose: SharedValue<number>) {
  return useAnimatedStyle(() => ({ transform: [{ translateX: -150 * (1 - pose.value) }, { translateY: -150 * (1 - pose.value) }] }));
}
function useCoverTitleStyle(pose: SharedValue<number>) {
  return useAnimatedStyle(() => ({ opacity: 1 - pose.value, transform: [{ translateY: pose.value * 18 }] }));
}

function AnimatedCircle({ pose }: { pose: SharedValue<number> }) {
  const props = useAnimatedProps(() => ({ r: Math.max(0, 150 * pose.value - 1), strokeOpacity: pose.value * 0.8 }));
  return <AnimatedSvgCircle animatedProps={props} cx={150} cy={150} fill="none" stroke={CREAM} strokeWidth={2} />;
}

function IrisArtwork({ pose }: { pose: SharedValue<number> }) {
  const rayMotion = useAnimatedProps(() => {
    const angle = pose.value * 25 * Math.PI / 180;
    const a = Math.cos(angle), b = Math.sin(angle);
    return { opacity: 1, matrix: [a, b, -b, a, 150 - a * 150 + b * 150, 150 - b * 150 - a * 150] };
  });
  return <Svg width={300} height={300} viewBox="0 0 300 300">
    <Rect width={300} height={300} fill="#E7B76A" />
    <AnimatedSvgG animatedProps={rayMotion}>
      {Array.from({ length: 12 }, (_, i) => <Rect key={i} x={139} y={0} width={22} height={300} fill={i % 2 === 0 ? '#6A2217' : '#F64624'} transform={`rotate(${i * 30} 150 150)`} />)}
    </AnimatedSvgG>
    <Circle cx={150} cy={150} r={51} fill="#21130F" />
    <SvgText x={150} y={145} fill={CREAM} textAnchor="middle" fontFamily="sans-serif" fontSize={14} fontWeight="700" letterSpacing={1.5}>LISTEN</SvgText>
    <SvgText x={150} y={163} fill={CREAM} textAnchor="middle" fontFamily="sans-serif" fontSize={14} fontWeight="700" letterSpacing={1.5}>CLOSER</SvgText>
  </Svg>;
}

const AnimatedSvgG = Animated.createAnimatedComponent(G);

function EpisodeRow({ episode, selected, playing, onPress }: { episode: number; selected: boolean; playing: boolean; onPress: () => void }) {
  const number = episode === 0 ? '01' : '02';
  const title = episode === 0 ? 'The quiet art of looking' : 'Good ideas take the long way';
  const duration = episode === 0 ? '18 min' : '26 min';
  return <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={`Play ${title}, ${duration}`} accessibilityState={{ selected }} style={[styles.episode, { top: episode === 0 ? 478 : 567 }]}>
    <View style={styles.episodeCopy}>
      <Text style={styles.episodeNo}>EPISODE {number}</Text>
      <Text style={styles.episodeTitle}>{title}</Text>
      <View style={styles.durationLine}><Text style={styles.episodeIcon}>{selected && playing ? '≋' : '▶'}</Text><Text style={styles.duration}>{duration}</Text></View>
    </View>
    <Thumbnail episode={episode} size={64} />
    <View style={styles.episodeRule} />
  </Pressable>;
}

function Thumbnail({ episode, size }: { episode: number; size: number }) {
  const tint = episode === 0 ? '#E8BB87' : '#E67E69';
  return <Svg width={size} height={size} viewBox="0 0 64 64" style={{ borderRadius: size === 36 ? 8 : 8, overflow: 'hidden' }} accessibilityElementsHidden>
    <Rect width={64} height={64} fill={tint} />
    <Circle cx={24.8} cy={42.9} r={27.2} fill="none" stroke="#571D14" strokeWidth={2.24} />
    <Rect x={43} y={0} width={1.6} height={64} fill="#571D14" transform={episode === 0 ? undefined : 'rotate(35 32 32)'} />
    <Circle cx={19.2} cy={19.84} r={6.4} fill="#571D14" />
  </Svg>;
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#571D14' },
  stageOuter: { position: 'absolute', left: 0, right: 0, alignItems: 'center', justifyContent: 'center' },
  stage: { width: 393, height: 760, overflow: 'visible' },
  header: { position: 'absolute', top: 18, left: 28, width: 337, height: 24, flexDirection: 'row', alignItems: 'center' },
  book: { color: CREAM, fontSize: 15, marginRight: 8 },
  headerTitle: { color: CREAM, fontSize: 10, fontWeight: '600', letterSpacing: 1.8 },
  issue: { marginLeft: 'auto', color: CREAM, fontSize: 10 },
  cover: { position: 'absolute', top: 65, left: 0, width: 393, height: 330, backgroundColor: '#F64624', overflow: 'hidden' },
  irisFrame: { position: 'absolute', left: 46.5, top: 10, width: 300, height: 300, alignItems: 'center', justifyContent: 'center' },
  irisMask: { alignItems: 'center', justifyContent: 'center', overflow: 'hidden', backgroundColor: 'transparent' },
  irisArt: { position: 'absolute', left: 0, top: 0, width: 300, height: 300 },
  coverTitle: { position: 'absolute', left: 27, top: 204, color: '#21130F', fontSize: 48, fontWeight: '700', letterSpacing: -2, lineHeight: 53 },
  coverHandle: { position: 'absolute', left: 180.5, top: 317.5, width: 32, height: 3, borderRadius: 2, backgroundColor: 'rgba(255,231,189,0.6)' },
  bookCopy: { position: 'absolute', left: 28, top: 410, width: 337, gap: 6 },
  bookTitle: { color: CREAM, fontSize: 22, fontWeight: '600' },
  bookSubtitle: { color: 'rgba(255,231,189,0.55)', fontSize: 13 },
  episode: { position: 'absolute', left: 28, width: 337, height: 84, flexDirection: 'row', alignItems: 'center', gap: 14 },
  episodeCopy: { flex: 1, height: 84, justifyContent: 'center', gap: 7 },
  episodeNo: { color: 'rgba(255,231,189,0.5)', fontSize: 8, fontWeight: '500', letterSpacing: 1.5 },
  episodeTitle: { color: CREAM, fontSize: 15, fontWeight: '500' },
  durationLine: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  episodeIcon: { color: 'rgba(255,231,189,0.6)', fontSize: 9 },
  duration: { color: 'rgba(255,231,189,0.6)', fontSize: 9 },
  episodeRule: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 1, backgroundColor: 'rgba(255,231,189,0.1)' },
  player: { position: 'absolute', left: 22, top: 672, width: 349, height: 58, paddingHorizontal: 12, borderRadius: 22, borderWidth: 1, borderColor: 'rgba(255,231,189,0.1)', backgroundColor: 'rgba(255,231,189,0.12)', flexDirection: 'row', alignItems: 'center', gap: 12 },
  playerCopy: { flex: 1, gap: 3 },
  playerTitle: { color: CREAM, fontSize: 11, fontWeight: '600' },
  playerState: { color: 'rgba(255,231,189,0.55)', fontSize: 9 },
  playButton: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },
  playGlyph: { color: CREAM, fontSize: 17, fontWeight: '700' },
  footer: { position: 'absolute', top: 741, width: 393, color: 'rgba(255,231,189,0.4)', fontSize: 8, fontWeight: '600', letterSpacing: 1.8, textAlign: 'center' },
});
