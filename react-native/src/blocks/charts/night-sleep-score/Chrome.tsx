import MaskedView from '@react-native-masked-view/masked-view';
import { BlurView } from 'expo-blur';
import { GlassView, isLiquidGlassAvailable } from 'expo-glass-effect';
import { LinearGradient } from 'expo-linear-gradient';
import { useEffect, useRef, type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withSequence, withSpring, withTiming } from 'react-native-reanimated';
import Svg, { Circle, Line, Path, Rect, Text as SvgText } from 'react-native-svg';

import { NIGHT_SLEEP_SCORE_TAB_MOTION as TM } from './motion';
import { NIGHT_SLEEP_SCORE_RAIL as RAIL, NIGHT_SLEEP_SCORE_TAB as TAB } from './theme';

/** iOS 26 Liquid Glass for the tab bar; older systems keep a blur under a translucent fill. */
const GLASS = isLiquidGlassAvailable();
const EASE_OUT = Easing.bezier(0.23, 1, 0.32, 1);
const RC = RAIL.colors;

/* ───────────── Header rail (pin's metric chips) ───────────── */

/** Icons are drawn in a 40-unit box shown at the measured 30.7 pt. */
function RailIcon({ children }: { children: ReactNode }) {
  return <Svg width={RAIL.icon} height={RAIL.icon} viewBox="0 0 40 40">{children}</Svg>;
}
const RING_R = 16.2;
const RING_C = 2 * Math.PI * RING_R;
const dash = (share: number) => `${RING_C * share} ${RING_C * (1 - share)}`;

function SleepIcon() {
  return (
    <RailIcon>
      <Circle cx="20" cy="20" r={RING_R} fill="none" stroke="#F67D65" strokeWidth="7.6" strokeDasharray={dash(.18)} strokeDashoffset={-RING_C * .01} rotation="-90" origin="20,20" />
      <Circle cx="20" cy="20" r={RING_R} fill="none" stroke="#4E63F8" strokeWidth="7.6" strokeDasharray={dash(.47)} strokeDashoffset={-RING_C * .22} rotation="-90" origin="20,20" />
      <Circle cx="20" cy="20" r={RING_R} fill="none" stroke="#13C9BB" strokeWidth="7.6" strokeDasharray={dash(.27)} strokeDashoffset={-RING_C * .72} rotation="-90" origin="20,20" />
      <SvgText x="20" y="24.6" textAnchor="middle" fontSize="13" fontWeight="700" fill={RC.value}>84</SvgText>
    </RailIcon>
  );
}

function ReadinessIcon() {
  const arc = (from: number, to: number) => {
    const p = (turn: number) => [20 + RING_R * Math.cos(turn * Math.PI * 2), 21 + RING_R * Math.sin(turn * Math.PI * 2)];
    const a = p(from); const b = p(to);
    return `M${a[0]} ${a[1]} A${RING_R} ${RING_R} 0 ${to - from > .5 ? 1 : 0} 1 ${b[0]} ${b[1]}`;
  };
  return (
    <RailIcon>
      <Path d={arc(.44, 1.06)} fill="none" stroke={RC.track} strokeWidth="6.4" strokeLinecap="round" />
      <Path d={arc(.75, .87)} fill="none" stroke={RC.gaugeLight} strokeWidth="6.4" strokeLinecap="round" />
      <Circle cx={20 + RING_R * Math.cos(.875 * Math.PI * 2)} cy={21 + RING_R * Math.sin(.875 * Math.PI * 2)} r="3.3" fill={RC.gaugeBlue} />
      <SvgText x="20" y="30" textAnchor="middle" fontSize="13" fontWeight="700" fill={RC.gaugeBlue}>7</SvgText>
    </RailIcon>
  );
}

function FitnessIcon() {
  const ring = (r: number, share: number) => { const c = 2 * Math.PI * r; return `${c * share} ${c * (1 - share)}`; };
  return (
    <RailIcon>
      <Circle cx="20" cy="20" r="16.6" fill="none" stroke={RC.fitnessPink} strokeWidth="6.4" />
      <Circle cx="20" cy="20" r="16.6" fill="none" stroke={RC.fitnessRed} strokeWidth="6.4" strokeLinecap="round" strokeDasharray={ring(16.6, .86)} rotation="-90" origin="20,20" />
      <Circle cx="20" cy="20" r="10.2" fill="none" stroke={RC.fitnessGreen} strokeWidth="5.2" strokeLinecap="round" strokeDasharray={ring(10.2, .8)} rotation="-90" origin="20,20" />
      <Circle cx="20" cy="20" r="4.6" fill={RC.fitnessCyan} />
    </RailIcon>
  );
}

function Chip({ left, width, title, value, icon, scale }: { left: number; width: number; title: string; value: string; icon: ReactNode; scale: number }) {
  return (
    <View style={[styles.chip, { left: left * scale, top: RAIL.top * scale, width: width * scale, height: RAIL.height * scale, borderRadius: RAIL.radius * scale }]}>
      <View style={[styles.chipRim, { borderRadius: (RAIL.radius - 1) * scale }]} />
      <View style={{ position: 'absolute', left: (RAIL.iconCentre - RAIL.icon / 2) * scale, top: (RAIL.height / 2 - RAIL.icon / 2) * scale, transform: [{ scale }], transformOrigin: 'top left' }}>{icon}</View>
      <Text allowFontScaling={false} numberOfLines={1} style={[styles.chipTitle, { left: RAIL.textLeft * scale, top: RAIL.titleTop * scale, fontSize: RAIL.textSize * scale, lineHeight: RAIL.textLine * scale }]}>{title}</Text>
      <Text allowFontScaling={false} numberOfLines={1} style={[styles.chipValue, { left: RAIL.textLeft * scale, top: RAIL.valueTop * scale, fontSize: RAIL.valueSize * scale, lineHeight: RAIL.textLine * scale }]}>{value}</Text>
    </View>
  );
}

const HEART_PATH = 'M12 21.35 10.55 20C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09A6.04 6.04 0 0 1 16.5 3C19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.5L12 21.35Z';

export function MetricRail({ scale }: { scale: number }) {
  const H = RAIL.heart; const C = RAIL.chips;
  return (
    <View style={{ height: 62 * scale }} pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <View style={[styles.heartTile, { left: H.left * scale, top: RAIL.top * scale, width: H.width * scale, height: RAIL.height * scale, borderRadius: RAIL.radius * scale }]}>
        <View style={{ position: 'absolute', left: (H.width / 2 - H.heartSize / 2) * scale, top: (H.heartCentre - H.heartSize / 2) * scale }}>
          <Svg width={H.heartSize * scale} height={H.heartSize * scale} viewBox="0 0 24 24"><Path d={HEART_PATH} fill={RC.heart} /></Svg>
        </View>
        {H.lines.map((line) => <View key={line.top} style={{ position: 'absolute', left: H.lineLeft * scale, top: line.top * scale, width: line.width * scale, height: H.lineHeight * scale, borderRadius: H.lineHeight * scale, backgroundColor: RC.line }} />)}
      </View>
      <Chip scale={scale} left={C.sleep.left} width={C.sleep.width} title="Sleep" value="High" icon={<SleepIcon />} />
      <Chip scale={scale} left={C.readiness.left} width={C.readiness.width} title="Readiness" value="Ready" icon={<ReadinessIcon />} />
      <Chip scale={scale} left={C.fitness.left} width={C.fitness.width} title="Fitness" value="Steady" icon={<FitnessIcon />} />
    </View>
  );
}

/* ───────────── Liquid Glass tab bar ───────────── */

function PressScale({ onPress, children, style, ...a11y }: {
  onPress: () => void; children: ReactNode; style?: object;
  accessibilityRole?: 'tab'; accessibilityLabel?: string; accessibilityState?: { selected: boolean };
}) {
  const pressed = useSharedValue(1);
  const pressStyle = useAnimatedStyle(() => ({ transform: [{ scale: pressed.get() }] }));
  return (
    <Pressable
      {...a11y}
      accessible
      hitSlop={4}
      onPress={onPress}
      onPressIn={() => pressed.set(withTiming(TM.press.scale, { duration: TM.press.inMs, easing: EASE_OUT }))}
      onPressOut={() => pressed.set(withTiming(1, { duration: TM.press.outMs, easing: EASE_OUT }))}
    >
      <Animated.View style={[style, pressStyle]}>{children}</Animated.View>
    </Pressable>
  );
}

/** The pin's Longevity mark: a dark aperture of eight petals around a small hole. */
function ApertureIcon({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      <Circle cx={12} cy={12} r={10.4} fill={color} />
      {Array.from({ length: 8 }, (_, index) => {
        const angle = (index * Math.PI) / 4 + Math.PI / 8;
        return <Line key={index} x1={12} y1={12} x2={12 + 11 * Math.cos(angle)} y2={12 + 11 * Math.sin(angle)} stroke={TAB.colors.card} strokeWidth={1.7} strokeLinecap="round" />;
      })}
      <Circle cx={12} cy={12} r={4} fill={TAB.colors.card} />
    </Svg>
  );
}
function BrowseIcon({ size, color }: { size: number; color: string }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessibilityElementsHidden>
      {[0, 1].flatMap((row) => [0, 1].map((column) => (
        <Rect key={`${row}-${column}`} x={1.5 + column * 11} y={1.5 + row * 11} width={10} height={10} rx={2.3} fill={color} />
      )))}
    </Svg>
  );
}

/** Scroll-edge effect: the feed blurs progressively as it reaches the bar (a blurred copy masked by a gradient). */
export function ScrollEdgeBlur({ scale }: { scale: number }) {
  const E = TAB.edgeBlur;
  return (
    <MaskedView pointerEvents="none" style={[styles.edgeBlur, { height: E.height * scale }]}
      maskElement={<LinearGradient colors={['transparent', '#000', '#000']} locations={[0, E.solidFrom, 1]} style={StyleSheet.absoluteFill} />}>
      <BlurView intensity={E.intensity} tint="light" style={StyleSheet.absoluteFill} />
    </MaskedView>
  );
}

/**
 * The selection lens as a small piece of glass: the bar and the feed under it refract through a tinted capsule (Liquid Glass on
 * iOS 26, a blur elsewhere), and a white rim with a sheen along its top edge catches the light. Its tint keeps the pin's grey pill.
 */
function GlassLens({ radius, blurTarget }: { radius: number; blurTarget: React.RefObject<View | null> }) {
  const L = TAB.lens;
  return (
    <>
      {GLASS ? (
        <GlassView glassEffectStyle="clear" tintColor={L.glassTint} style={[StyleSheet.absoluteFill, { borderRadius: radius }]} />
      ) : (
        <>
          <BlurView blurTarget={blurTarget} blurMethod="dimezisBlurViewSdk31Plus" intensity={L.blur} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, { backgroundColor: L.fallback }]} />
        </>
      )}
      <LinearGradient colors={[L.sheen, 'transparent']} locations={[0, L.sheenTo]} style={StyleSheet.absoluteFill} />
      <View style={[StyleSheet.absoluteFill, { borderRadius: radius, borderWidth: L.rimWidth, borderColor: L.rim }]} />
    </>
  );
}

export type NightSleepScoreTab = 'insights' | 'longevity' | 'browse';
const TABS: readonly { key: NightSleepScoreTab; title: string }[] = [
  { key: 'insights', title: 'Insights' }, { key: 'longevity', title: 'Longevity' }, { key: 'browse', title: 'Browse' },
];

/**
 * The pin's floating tab bar as iOS 26 Liquid Glass: the page refracts under the capsule and the selection lens slides to a
 * tapped tab on a spring, stretching as it travels. Tabs are peers, so nothing else moves. Still under Reduce Motion.
 */
export function GlassTabBar({ active, scale, still, blurTarget, onSelect }: {
  active: NightSleepScoreTab; scale: number; still: boolean; blurTarget: React.RefObject<View | null>; onSelect: (tab: NightSleepScoreTab) => void;
}) {
  const index = TABS.findIndex((tab) => tab.key === active);
  const lensX = useSharedValue(index);
  const lensStretch = useSharedValue(1);
  const previous = useRef(index);
  useEffect(() => {
    if (previous.current === index) return;
    previous.current = index;
    if (still) { lensX.set(index); return; }
    lensX.set(withSpring(index, TM.lens));
    lensStretch.set(withSequence(withTiming(TM.stretch, { duration: TM.stretchMs, easing: EASE_OUT }), withSpring(1, TM.settle)));
  }, [index, lensStretch, lensX, still]);
  const lensStyle = useAnimatedStyle(() => ({ transform: [{ translateX: lensX.get() * TAB.item * scale }, { scaleX: lensStretch.get() }] }));
  return (
    <View
      style={[styles.tabBar, {
        outlineColor: TAB.colors.outline, outlineWidth: StyleSheet.hairlineWidth * 2,
        width: TAB.width * scale, height: TAB.height * scale, borderRadius: TAB.radius * scale, padding: TAB.padding * scale,
        bottom: TAB.bottom * scale, marginLeft: (-TAB.width / 2) * scale,
        boxShadow: [{ offsetX: 0, offsetY: 5 * scale, blurRadius: 12 * scale, color: TAB.colors.shadow }],
      }]}
      accessibilityRole="tablist"
    >
      {GLASS ? (
        <GlassView glassEffectStyle="regular" tintColor={TAB.colors.glassTint} style={[StyleSheet.absoluteFill, { borderRadius: TAB.radius * scale }]} />
      ) : (
        <>
          <BlurView blurTarget={blurTarget} blurMethod="dimezisBlurViewSdk31Plus" intensity={30} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, { backgroundColor: TAB.colors.fallback }]} />
        </>
      )}
      <Animated.View
        pointerEvents="none"
        style={[styles.tabLens, { left: TAB.padding * scale, top: TAB.padding * scale, width: TAB.item * scale, height: TAB.itemHeight * scale, borderRadius: (TAB.itemHeight / 2) * scale }, lensStyle]}
      >
        <GlassLens radius={(TAB.itemHeight / 2) * scale} blurTarget={blurTarget} />
      </Animated.View>
      {TABS.map((tab) => {
        const selected = tab.key === active;
        const tint = selected ? TAB.colors.active : TAB.colors.ink;
        const icon = TAB.icon * scale;
        return (
          <PressScale key={tab.key} onPress={() => onSelect(tab.key)} style={[styles.tabItem, { width: TAB.item * scale, height: TAB.itemHeight * scale }]}
            accessibilityRole="tab" accessibilityState={{ selected }} accessibilityLabel={tab.title}>
            <View style={{ position: 'absolute', top: (TAB.iconCentre - TAB.icon / 2) * scale, width: icon, height: icon }}>
              {tab.key === 'insights'
                ? <Svg width={icon} height={icon} viewBox="0 0 24 24"><Path d={HEART_PATH} fill={tint} /></Svg>
                : tab.key === 'longevity' ? <ApertureIcon size={icon} color={tint} /> : <BrowseIcon size={icon} color={tint} />}
            </View>
            <Text allowFontScaling={false} style={[styles.tabLabel, { top: TAB.labelTop * scale, color: tint, fontSize: TAB.labelSize * scale, lineHeight: TAB.labelLine * scale }]}>{tab.title}</Text>
          </PressScale>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  heartTile: { position: 'absolute', backgroundColor: RC.tile },
  chip: { position: 'absolute', backgroundColor: RC.chip, borderWidth: StyleSheet.hairlineWidth * 2, borderColor: RC.chipBorder },
  chipRim: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, borderWidth: 1.5, borderColor: RC.chipRim },
  chipTitle: { position: 'absolute', color: RC.title, fontWeight: '400' },
  chipValue: { position: 'absolute', color: RC.value, fontWeight: '600' },
  edgeBlur: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  tabBar: { position: 'absolute', left: '50%', flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: TAB.colors.border, overflow: 'hidden' },
  tabItem: { alignItems: 'center' },
  tabLens: { position: 'absolute', overflow: 'hidden' },
  tabLabel: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontWeight: '600' },
});
