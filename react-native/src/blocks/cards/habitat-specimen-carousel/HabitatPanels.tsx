import { Image } from 'expo-image';
import { memo, useEffect, useState } from 'react';
import { StyleSheet, Text, View, type ImageSourcePropType, type LayoutChangeEvent, type ViewStyle } from 'react-native';
import { Pressable, ScrollView } from 'react-native-gesture-handler';
import Animated, {
  type CSSStyle, cubicBezier, Easing, interpolateColor, ReduceMotion, type SharedValue,
  useAnimatedStyle, useDerivedValue, useSharedValue, withSpring, withTiming,
} from 'react-native-reanimated';

import { type HabitatTab, wrapPosition } from './HabitatMotion';
import { TextSwap } from './HabitatTextMotion';

export type HabitatPanelSpec = {
  name: string;
  number: string;
  image: ImageSourcePropType;
  copy: string;
  stats: readonly number[];
  evolution: readonly { name: string; number: string; level?: number }[];
};

const TABS = ['About', 'Base stats', 'Evolution'] as const;
const STAT_LABELS = ['HP', 'Attack', 'Defense', 'Sp. Atk', 'Sp. Def', 'Speed'] as const;
/** Element tints in carousel order, repeated so colour interpolation wraps 2 → 0. */
const ELEMENT_COLORS = ['#F2A766', '#7FD3EA', '#A9DD7F', '#F2A766'];
const STAT_CEILING = 150;
const TAB_EASING = cubicBezier(0.2, 0, 0, 1);

/** Value of one base stat at a continuous carousel position: it morphs with the swipe itself. */
function statAt(stats: readonly (readonly number[])[], stat: number, position: number) {
  'worklet';
  const p = wrapPosition(position);
  const from = Math.floor(p) % 3;
  const to = (from + 1) % 3;
  const f = p - Math.floor(p);
  const eased = f * f * (3 - 2 * f);
  return stats[from][stat] + (stats[to][stat] - stats[from][stat]) * eased;
}

const StatRow = memo(function StatRow({ index, stats, position, reveal, reduced, scale, height, selectedIndex }: {
  index: number; stats: readonly (readonly number[])[]; position: SharedValue<number>; reveal: SharedValue<number>;
  reduced: SharedValue<boolean>; scale: number; height: number; selectedIndex: number;
}) {
  // Rows fill in reading order, 45 ms apart, from one shared reveal.
  const local = useDerivedValue(() => {
    if (reduced.get()) return 1;
    const t = Math.max(0, Math.min(1, (reveal.get() - index * 0.075) / 0.6));
    return 1 - Math.pow(1 - t, 3);
  });
  const value = useDerivedValue(() => statAt(stats, index, position.get()));
  const fill = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(wrapPosition(position.get()), [0, 1, 2, 3], ELEMENT_COLORS),
    transform: [{ scaleX: Math.min(1, value.get() / STAT_CEILING) * local.get() }],
  }));
  const line = 15 * scale;
  return <View style={[styles.statRow, { height }]}>
    <Text maxFontSizeMultiplier={1.3} style={[styles.statLabel, { fontSize: 9.5 * scale, width: 44 * scale }]}>{STAT_LABELS[index]}</Text>
    <TextSwap position={position} reduced={reduced} height={line} selectedIndex={selectedIndex} style={{ width: 26 * scale, marginRight: 10 }}
      items={stats.map((values, specimen) => <Text key={specimen} maxFontSizeMultiplier={1.3} style={[styles.statNumber, { fontSize: 11.5 * scale, lineHeight: line }]}>{values[index]}</Text>)} />
    <View style={[styles.statTrack, { height: 4 * scale, borderRadius: 2 * scale }]}>
      <Animated.View style={[styles.statFill, { borderRadius: 2 * scale }, fill]} />
    </View>
  </View>;
});

function Evolution({ spec, reduced, reveal, scale, width }: {
  spec: HabitatPanelSpec; reduced: SharedValue<boolean>; reveal: SharedValue<number>; scale: number; width: number;
}) {
  const current = spec.evolution.findIndex(stage => stage.number === spec.number);
  const node = 40 * scale;
  const column = 74 * scale;
  const centers = [column / 2, width / 2, width - column / 2];
  return <View pointerEvents="none" style={StyleSheet.absoluteFill}>
    {[0, 1].map(link => <EvolutionLink key={link} order={link} reached={link < current} level={spec.evolution[link + 1]?.level}
      from={centers[link] + node / 2 + 6 * scale} to={centers[link + 1] - node / 2 - 6 * scale} top={node / 2}
      reveal={reveal} reduced={reduced} scale={scale} />)}
    {spec.evolution.map((stage, stageIndex) => <EvolutionStage key={stage.number} stage={stage} active={stageIndex === current}
      order={stageIndex} image={spec.image} reveal={reveal} reduced={reduced} scale={scale} node={node}
      left={centers[stageIndex] - column / 2} width={column} />)}
  </View>;
}

function EvolutionLink({ order, reached, level, from, to, top, reveal, reduced, scale }: {
  order: number; reached: boolean; level?: number; from: number; to: number; top: number;
  reveal: SharedValue<number>; reduced: SharedValue<boolean>; scale: number;
}) {
  // Reached links draw toward the specimen on show; the next evolution stays a quiet rail.
  const fill = useAnimatedStyle(() => {
    const t = reduced.get() ? 1 : Math.max(0, Math.min(1, (reveal.get() - 0.12 - order * 0.16) / 0.45));
    return { transform: [{ scaleX: reached ? 1 - Math.pow(1 - t, 3) : 0 }] };
  });
  const width = Math.max(0, to - from);
  return <View style={{ position: 'absolute', left: from, top: top - 0.5, width, height: 1 }}>
    <View style={styles.linkTrack} />
    <Animated.View style={[styles.linkFill, fill]} />
    {level != null && <View style={[styles.levelWrap, { top: -9 * scale }]}>
      <Text maxFontSizeMultiplier={1.3} style={[styles.level, { fontSize: 8.5 * scale, paddingHorizontal: 6 * scale, height: 18 * scale, lineHeight: 18 * scale, borderRadius: 9 * scale }]}>Lv {level}</Text>
    </View>}
  </View>;
}

function EvolutionStage({ stage, active, order, image, reveal, reduced, scale, node, left, width }: {
  stage: HabitatPanelSpec['evolution'][number]; active: boolean; order: number; image: ImageSourcePropType;
  reveal: SharedValue<number>; reduced: SharedValue<boolean>; scale: number; node: number; left: number; width: number;
}) {
  const pop = useAnimatedStyle(() => {
    if (reduced.get()) return { opacity: 1, transform: [{ scale: 1 }] };
    const t = Math.max(0, Math.min(1, (reveal.get() - order * 0.16) / 0.5));
    const eased = 1 - Math.pow(1 - t, 3);
    return { opacity: eased, transform: [{ scale: 0.92 + 0.08 * eased }] };
  });
  return <Animated.View style={[styles.stage, { left, width }, pop]}>
    <View style={[styles.node, { width: node, height: node, borderRadius: node / 2 }, active && styles.nodeActive]}>
      {active ? <Image source={image} contentFit="contain" style={{ width: node * 0.92, height: node * 0.92 }} accessible={false} />
        : <Text style={[styles.nodeNumber, { fontSize: 9.5 * scale }]}>#{stage.number}</Text>}
    </View>
    <Text numberOfLines={1} maxFontSizeMultiplier={1.3} style={[styles.stageName, active && styles.stageNameActive, { fontSize: 10 * scale, marginTop: 6 * scale }]}>{stage.name}</Text>
  </Animated.View>;
}

export function HabitatPanels({ specs, position, reduced, reducedMotion, scale, width, tabsTop, contentTop, contentHeight, tab, onTab, selectedIndex }: {
  specs: readonly HabitatPanelSpec[]; position: SharedValue<number>; reduced: SharedValue<boolean>; reducedMotion: boolean;
  scale: number; width: number; tabsTop: number; contentTop: number; contentHeight: number;
  tab: HabitatTab; onTab: (tab: HabitatTab) => void; selectedIndex: number;
}) {
  const [layouts, setLayouts] = useState<readonly { x: number; width: number }[]>([]);
  const underlineX = useSharedValue(0);
  const underlineWidth = useSharedValue(21 * scale);
  const reveal = useSharedValue(1);
  const stats = specs.map(spec => spec.stats);
  useEffect(() => {
    const target = layouts[tab];
    if (!target) return;
    const config = { duration: 380, dampingRatio: 1, reduceMotion: reducedMotion ? ReduceMotion.Always : ReduceMotion.Never };
    // The underline keeps the short 21 pt mark of the design, centred on the label's start.
    underlineX.set(withSpring(target.x, config));
    underlineWidth.set(withSpring(Math.min(21 * scale, target.width), config));
  }, [layouts, reducedMotion, scale, tab, underlineWidth, underlineX]);
  useEffect(() => {
    if (reducedMotion) { reveal.set(1); return; }
    reveal.set(0);
    reveal.set(withTiming(1, { duration: 900, easing: Easing.linear, reduceMotion: ReduceMotion.Never }));
  }, [reducedMotion, reveal, tab]);
  const underline = useAnimatedStyle(() => ({ width: underlineWidth.get(), transform: [{ translateX: underlineX.get() }] }));
  const layout = (index: number) => (event: LayoutChangeEvent) => {
    const { x, width } = event.nativeEvent.layout;
    setLayouts(previous => {
      if (previous[index]?.x === x && previous[index]?.width === width) return previous;
      const next = [...previous]; next[index] = { x, width }; return next;
    });
  };
  const rowHeight = Math.min(24 * scale, contentHeight / 3);
  const panelStyle = (index: HabitatTab): CSSStyle<ViewStyle> => ({
    opacity: tab === index ? 1 : 0,
    transform: [{ translateY: tab === index || reducedMotion ? 0 : 4 * scale }],
    transitionProperty: reducedMotion ? 'opacity' : ['opacity', 'transform'],
    transitionDuration: 160, transitionTimingFunction: TAB_EASING,
  });
  const active = specs[selectedIndex];
  const panelWidth = width - 54 * scale;
  return <>
    <View style={[styles.tabs, { top: tabsTop, left: 27 * scale, right: 27 * scale, gap: 31 * scale, height: 32 * scale }]} accessibilityRole="tablist">
      {TABS.map((label, index) => <Pressable key={label} onLayout={layout(index)} accessibilityRole="tab" accessibilityState={{ selected: tab === index }}
        onPress={() => onTab(index as HabitatTab)} hitSlop={{ top: 10, bottom: 10, left: 12, right: 12 }} style={styles.tab}>
        <Animated.Text maxFontSizeMultiplier={1.3} style={[styles.tabLabel, { fontSize: 11 * scale, color: tab === index ? '#F1F4E5' : '#8F9E8F',
          transitionProperty: 'color', transitionDuration: 150, transitionTimingFunction: TAB_EASING }]}>{label}</Animated.Text>
      </Pressable>)}
      <Animated.View pointerEvents="none" style={[styles.underline, { top: 12 + 11 * scale * 1.2 + 12 * scale - 2 }, underline]} />
    </View>
    <Animated.View style={[styles.panel, { top: contentTop, left: 27 * scale, right: 27 * scale, height: contentHeight }, panelStyle(0)]}
      pointerEvents={tab === 0 ? 'box-none' : 'none'} accessibilityElementsHidden={tab !== 0} importantForAccessibility={tab === 0 ? 'auto' : 'no-hide-descendants'}>
      <TextSwap position={position} reduced={reduced} height={contentHeight} selectedIndex={selectedIndex}
        items={specs.map(spec => <ScrollView key={spec.name} showsVerticalScrollIndicator={false} bounces={false}>
          <Text style={[styles.description, { fontSize: 11.5 * scale, lineHeight: 19 * scale }]}>{spec.copy}</Text>
        </ScrollView>)} />
    </Animated.View>
    <Animated.View style={[styles.panel, styles.statsPanel, { top: contentTop, left: 27 * scale, right: 27 * scale, height: contentHeight, columnGap: 22 * scale }, panelStyle(1)]}
      pointerEvents="none" accessible={tab === 1} accessibilityLabel={active ? `${active.name} base stats: ${active.stats.map((value, index) => `${STAT_LABELS[index]} ${value}`).join(', ')}` : undefined}
      accessibilityElementsHidden={tab !== 1} importantForAccessibility={tab === 1 ? 'yes' : 'no-hide-descendants'}>
      {[0, 1].map(column => <View key={column} style={styles.statColumn}>
        {[0, 1, 2].map(row => <StatRow key={row} index={column * 3 + row} stats={stats} position={position} reveal={reveal} reduced={reduced} scale={scale} height={rowHeight} selectedIndex={selectedIndex} />)}
      </View>)}
    </Animated.View>
    <Animated.View style={[styles.panel, { top: contentTop, left: 27 * scale, right: 27 * scale, height: contentHeight }, panelStyle(2)]}
      pointerEvents="none" accessible={tab === 2} accessibilityLabel={active ? `Evolution: ${active.evolution.map(stage => stage.name).join(', then ')}` : undefined}
      accessibilityElementsHidden={tab !== 2} importantForAccessibility={tab === 2 ? 'yes' : 'no-hide-descendants'}>
      <TextSwap position={position} reduced={reduced} height={contentHeight} selectedIndex={selectedIndex}
        items={specs.map(spec => <Evolution key={spec.name} spec={spec} reduced={reduced} reveal={reveal} scale={scale} width={panelWidth} />)} />
    </Animated.View>
  </>;
}

const styles = StyleSheet.create({
  tabs: { position: 'absolute', flexDirection: 'row', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: '#D4E4DA19', paddingTop: 12, zIndex: 10 },
  tab: { justifyContent: 'flex-start' },
  tabLabel: { fontWeight: '600' },
  underline: { position: 'absolute', left: 0, height: 2, borderRadius: 2, backgroundColor: '#E0EACC' },
  panel: { position: 'absolute', zIndex: 10 },
  statsPanel: { flexDirection: 'row' },
  statColumn: { flex: 1, minWidth: 0 },
  statRow: { flexDirection: 'row', alignItems: 'center' },
  statLabel: { color: '#9FAE9E', letterSpacing: 0.3 },
  statNumber: { color: '#F1F4E5', fontWeight: '600', fontVariant: ['tabular-nums'], textAlign: 'right' },
  statTrack: { flex: 1, backgroundColor: '#FFFFFF14', overflow: 'hidden' },
  statFill: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, transformOrigin: 'left center' },
  description: { color: '#C4CEC0', letterSpacing: 0.02 },
  stage: { position: 'absolute', top: 0, alignItems: 'center' },
  node: { alignItems: 'center', justifyContent: 'center', borderWidth: StyleSheet.hairlineWidth, borderColor: '#D4E4DA33', backgroundColor: '#FFFFFF08' },
  nodeActive: { borderColor: '#E9EFD8AA', borderWidth: 1, backgroundColor: '#FFFFFF12' },
  nodeNumber: { color: '#A5B49D', fontWeight: '600', letterSpacing: 0.6, fontVariant: ['tabular-nums'] },
  stageName: { color: '#9FAE9E', fontWeight: '500' },
  stageNameActive: { color: '#F1F4E5', fontWeight: '600' },
  linkTrack: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: '#D4E4DA22' },
  linkFill: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, backgroundColor: '#E0EACC88', transformOrigin: 'left center' },
  levelWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  level: { color: '#C9D4C3', backgroundColor: '#18241F', overflow: 'hidden', borderWidth: StyleSheet.hairlineWidth, borderColor: '#D4E4DA2A', fontWeight: '600', letterSpacing: 0.4 },
});
