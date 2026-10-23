/** The top bar and tool row of the editor, and the controls that float over the video at full screen. */
import { Feather } from '@expo/vector-icons';
import { SymbolView, type SFSymbol } from 'expo-symbols';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { fullness, squeeze } from './layout';
import { COLOR, LAYOUT, SQUEEZE, TYPE } from './theme';
import { UPGRADE } from './motion';
import { PressScale } from './press-scale';

// Filled, heavy symbols as in the source; Add clips and Edit are dimmed there (nothing selected).
const TOOLS: { icon: SFSymbol | 'Aa'; label: string; dim?: boolean }[] = [
  { icon: 'Aa', label: 'Text' },
  { icon: 'face.smiling.inverse', label: 'Sticker' },
  { icon: 'music.note.list', label: 'Audio' },
  { icon: 'play.rectangle.fill', label: 'Add clips', dim: true },
  { icon: 'square.filled.on.square', label: 'Overlay' },
  { icon: 'scissors', label: 'Edit', dim: true },
  { icon: 'mic.fill', label: 'Voiceover' },
];
const FULL_ICONS = ['camera', 'type', 'smile', 'music', 'layers', 'sliders'] as const;

type Props = {
  shown: SharedValue<number>;
  topInset: number;
  bottomInset: number;
  full: boolean;
  onCollapse: () => void;
  onExitFull: () => void;
};

/** At full screen the floating tools come in one after another, rising 8 pt (stagger with intent: left to right). */
function FullIcon({ icon, index, shown }: { icon: (typeof FULL_ICONS)[number]; index: number; shown: SharedValue<number> }) {
  const style = useAnimatedStyle(() => {
    const u = Math.min(Math.max((fullness(shown.get()) - 0.85 - UPGRADE.stagger * index) / 0.1, 0), 1);
    return { opacity: u, transform: [{ translateY: 8 * (1 - u) }] };
  });
  return <Animated.View style={[styles.fullIcon, style]}><Feather name={icon} size={17} color={COLOR.ink} /></Animated.View>;
}

export function Chrome({ shown, topInset, bottomInset, full, onCollapse, onExitFull }: Props) {
  const editor = useAnimatedStyle(() => ({ opacity: 1 - Math.min(fullness(shown.get()) * 1.6, 1) }));
  // As in the source, the tool row rises a little as the preview grows.
  const toolsStyle = useAnimatedStyle(() => ({
    opacity: 1 - Math.min(fullness(shown.get()) * 1.6, 1),
    transform: [{ translateY: SQUEEZE.tools * squeeze(shown.get()) }],
  }));
  const overlay = useAnimatedStyle(() => ({ opacity: Math.min(Math.max((fullness(shown.get()) - 0.85) / 0.15, 0), 1) }));
  return (
    <>
      <Animated.View style={[styles.bar, { top: topInset + LAYOUT.barY - LAYOUT.barButton / 2 }, editor]} pointerEvents={full ? 'none' : 'box-none'}>
        <PressScale style={styles.barButton} onPress={onCollapse} accessibilityRole="button" accessibilityLabel="Smallest preview" hitSlop={6}>
          <Feather name="chevron-up" size={18} color={COLOR.ink} />
        </PressScale>
        <PressScale style={[styles.barButton, styles.next]} accessibilityRole="button" accessibilityLabel="Next" hitSlop={6}>
          <Feather name="arrow-right" size={18} color={COLOR.ink} />
        </PressScale>
      </Animated.View>

      <Animated.View style={[styles.tools, { height: LAYOUT.tools + bottomInset, paddingBottom: bottomInset }, toolsStyle]} pointerEvents={full ? 'none' : 'auto'}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.toolRow}>
          {TOOLS.map((tool) => (
            <PressScale key={tool.label} style={styles.tool} accessibilityRole="button" accessibilityLabel={tool.label}
              accessibilityState={{ disabled: !!tool.dim }}>
              <View style={styles.toolIcon}>
                {tool.icon === 'Aa'
                  ? <Text style={[styles.toolAa, tool.dim && styles.dim]}>Aa</Text>
                  : <SymbolView name={tool.icon} size={20} weight="bold" tintColor={tool.dim ? COLOR.toolDim : COLOR.ink} />}
              </View>
              <Text style={[styles.toolText, tool.dim && styles.dim]} numberOfLines={1}>{tool.label}</Text>
            </PressScale>
          ))}
        </ScrollView>
      </Animated.View>

      <Animated.View style={[StyleSheet.absoluteFill, overlay]} pointerEvents={full ? 'box-none' : 'none'}>
        <PressScale style={[styles.barButton, styles.close, { top: topInset + LAYOUT.barY - LAYOUT.barButton / 2 }]} onPress={onExitFull} accessibilityRole="button" accessibilityLabel="Leave full screen" hitSlop={6}>
          <Feather name="x" size={18} color={COLOR.ink} />
        </PressScale>
        <View style={[styles.fullIcons, { bottom: bottomInset + 72 }]}>
          {FULL_ICONS.map((icon, i) => <FullIcon key={icon} icon={icon} index={i} shown={shown} />)}
        </View>
        <View style={[styles.pills, { bottom: bottomInset + 16 }]}>
          <PressScale style={styles.pill} onPress={onExitFull} accessibilityRole="button" accessibilityLabel="Edit video">
            <Text style={styles.pillText}>Edit video</Text>
          </PressScale>
          <PressScale style={[styles.pill, styles.pillBlue]} accessibilityRole="button" accessibilityLabel="Next">
            <Text style={styles.pillText}>Next</Text>
            <Feather name="arrow-right" size={15} color={COLOR.ink} />
          </PressScale>
        </View>
      </Animated.View>
    </>
  );
}

const styles = StyleSheet.create({
  bar: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, flexDirection: 'row', justifyContent: 'space-between' },
  barButton: { width: LAYOUT.barButton, height: LAYOUT.barButton, borderRadius: LAYOUT.barButton / 2, backgroundColor: COLOR.chromeHi, alignItems: 'center', justifyContent: 'center' },
  next: { backgroundColor: COLOR.blue },
  tools: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: COLOR.background },
  toolRow: { paddingHorizontal: LAYOUT.toolInset, paddingTop: 10, gap: LAYOUT.toolGap },
  tool: { width: LAYOUT.tool, alignItems: 'center', gap: 4 },
  toolIcon: { width: LAYOUT.tool, height: LAYOUT.toolH, borderRadius: LAYOUT.toolRadius, backgroundColor: COLOR.chrome, alignItems: 'center', justifyContent: 'center' },
  toolAa: { fontSize: 19, fontWeight: '600', color: COLOR.ink },
  toolText: { ...TYPE.tool, color: COLOR.ink },
  dim: { color: COLOR.toolDim },
  close: { position: 'absolute', left: LAYOUT.gutter, backgroundColor: COLOR.overlayButton },
  fullIcons: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, flexDirection: 'row', justifyContent: 'space-between' },
  fullIcon: { width: 40, height: 40, borderRadius: 20, backgroundColor: COLOR.overlayButton, alignItems: 'center', justifyContent: 'center' },
  pills: { position: 'absolute', left: LAYOUT.gutter, right: LAYOUT.gutter, flexDirection: 'row', justifyContent: 'space-between' },
  pill: { height: 44, borderRadius: 22, paddingHorizontal: 18, backgroundColor: COLOR.overlayButton, flexDirection: 'row', alignItems: 'center', gap: 6 },
  pillBlue: { backgroundColor: COLOR.blue },
  pillText: { ...TYPE.pill, color: COLOR.ink },
});
