import { Ionicons } from '@expo/vector-icons';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

export type Mode = {
  label: string;
  caption: string;
  icon: keyof typeof Ionicons.glyphMap;
  pro?: boolean;
};

export type Model = {
  name: string;
  caption: string;
  /** A small badge after the name, such as "New". */
  tag?: 'New' | 'Max';
  /** A group heading shown above this row. */
  heading?: string;
};

export const MODE_HEIGHT = 66;
export const MODE_GAP = 8;
export const MODEL_HEIGHT = 56;
export const MODEL_GAP = 4;
export const HEADING_HEIGHT = 30;

const TEAL = '#1e8f95';
const TEAL_SOFT = '#cfebec';
const SELECTED_FILL = '#e4f2f2';
const SELECTED_BORDER = '#4cb8bd';
const ROW_FILL = '#f5f5f2';
const INK = '#1c1c1c';
const MUTE = '#8a8a8a';
const SWITCH_OFF = '#d8d8d8';
const SWITCH_ON = '#1fa37a';
const TAG_BLUE = '#3b82f6';
const TAG_GOLD = '#d99a1c';
const ROW_FADE = { transitionDuration: 200 } as const;

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function ramp(value: number, from: number, to: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (value - from) / (to - from)));
  return t * t * (3 - 2 * t);
}

/** A row of the compact sheet; the selected one takes a teal fill and border. */
export function ModeRow({
  mode,
  selected,
  onPress,
  trailing,
}: {
  mode: Mode;
  selected: boolean;
  onPress: () => void;
  trailing?: ReactNode;
}) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityState={{ selected }}>
      <Animated.View
        style={[
          styles.mode,
          { backgroundColor: selected ? SELECTED_FILL : ROW_FILL, borderColor: selected ? SELECTED_BORDER : 'transparent' },
          { transitionProperty: ['backgroundColor', 'borderColor'], ...ROW_FADE },
        ]}
      >
        <View style={[styles.icon, selected && styles.iconSelected]}>
          <Ionicons name={mode.icon} size={19} color={selected ? TEAL : INK} />
        </View>
        <View style={styles.text}>
          <View style={styles.nameRow}>
            <Text style={styles.name}>{mode.label}</Text>
            {mode.pro ? (
              <View style={styles.pro}>
                <Text style={styles.proText}>PRO</Text>
              </View>
            ) : null}
          </View>
          <Text style={styles.caption} numberOfLines={1}>
            {mode.caption}
          </Text>
        </View>
        {trailing}
      </Animated.View>
    </Pressable>
  );
}

/** The incognito row: a switch that slides its knob while the track changes colour. */
export function SwitchRow({ label, caption, on, onPress }: { label: string; caption: string; on: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="switch" accessibilityState={{ checked: on }}>
      <View style={styles.mode}>
        <View style={styles.icon}>
          <Ionicons name="shield-half-outline" size={19} color={INK} />
        </View>
        <View style={styles.text}>
          <Text style={styles.name}>{label}</Text>
          <Text style={styles.caption} numberOfLines={1}>
            {caption}
          </Text>
        </View>
        <Animated.View style={[styles.track, { backgroundColor: on ? SWITCH_ON : SWITCH_OFF }, { transitionProperty: 'backgroundColor', ...ROW_FADE }]}>
          <Animated.View style={[styles.knob, { transform: [{ translateX: on ? 20 : 0 }] }, { transitionProperty: 'transform', ...ROW_FADE }]} />
        </Animated.View>
      </View>
    </Pressable>
  );
}

/** A row of the tall sheet. It slides up 12 points and fades in as the sheet grows, a little after the row above. */
export function ModelRow({
  model,
  index,
  selected,
  grow,
  onPress,
}: {
  model: Model;
  index: number;
  selected: boolean;
  /** 0 compact, 1 tall. */
  grow: SharedValue<number>;
  onPress: () => void;
}) {
  const style = useAnimatedStyle(() => {
    const t = ramp(grow.get(), 0.4 + index * 0.02, 0.85 + index * 0.02);
    return { opacity: t, transform: [{ translateY: lerp(12, 0, t) }] };
  });
  return (
    <Animated.View style={style}>
      {model.heading ? <Text style={styles.heading}>{model.heading}</Text> : null}
      <Pressable onPress={onPress} accessibilityRole="button" accessibilityState={{ selected }}>
        <View style={styles.model}>
          <View style={styles.text}>
            <View style={styles.nameRow}>
              <Text style={styles.modelName}>{model.name}</Text>
              {model.tag ? (
                <View style={[styles.tag, { backgroundColor: model.tag === 'New' ? TAG_BLUE : TAG_GOLD }]}>
                  <Text style={styles.tagText}>{model.tag}</Text>
                </View>
              ) : null}
            </View>
            <Text style={styles.modelCaption} numberOfLines={1}>
              {model.caption}
            </Text>
          </View>
          <Animated.View
            style={[
              { opacity: selected ? 1 : 0, transform: [{ scale: selected ? 1 : 0.4 }] },
              { transitionProperty: ['opacity', 'transform'], ...ROW_FADE },
            ]}
          >
            <View style={styles.tick}>
              <Ionicons name="checkmark" size={14} color="#ffffff" />
            </View>
          </Animated.View>
        </View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  mode: { height: MODE_HEIGHT, borderRadius: 16, borderWidth: 1.5, borderColor: 'transparent', backgroundColor: ROW_FILL, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 12 },
  icon: { width: 38, height: 38, borderRadius: 19, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center', shadowColor: '#000000', shadowOpacity: 0.06, shadowRadius: 3, shadowOffset: { width: 0, height: 1 } },
  iconSelected: { backgroundColor: '#ffffff' },
  text: { flex: 1, gap: 2 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  name: { fontSize: 15.5, lineHeight: 20, fontWeight: '600', letterSpacing: -0.2, color: INK },
  caption: { fontSize: 12.5, lineHeight: 16, color: MUTE },
  pro: { height: 16, paddingHorizontal: 5, borderRadius: 5, backgroundColor: TEAL_SOFT, alignItems: 'center', justifyContent: 'center' },
  proText: { fontSize: 9.5, lineHeight: 12, fontWeight: '800', letterSpacing: 0.4, color: TEAL },
  track: { width: 50, height: 30, borderRadius: 15, padding: 2 },
  knob: { width: 26, height: 26, borderRadius: 13, backgroundColor: '#ffffff', shadowColor: '#000000', shadowOpacity: 0.2, shadowRadius: 3, shadowOffset: { width: 0, height: 1.5 } },
  heading: { height: HEADING_HEIGHT, paddingTop: 12, paddingHorizontal: 20, fontSize: 12, lineHeight: 16, fontWeight: '700', letterSpacing: 0.6, textTransform: 'uppercase', color: MUTE },
  model: { height: MODEL_HEIGHT, marginHorizontal: 12, marginBottom: MODEL_GAP, borderRadius: 14, backgroundColor: ROW_FILL, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  modelName: { fontSize: 15, lineHeight: 19, fontWeight: '600', letterSpacing: -0.2, color: INK },
  modelCaption: { fontSize: 12, lineHeight: 15, color: MUTE },
  tag: { height: 16, paddingHorizontal: 5, borderRadius: 5, alignItems: 'center', justifyContent: 'center' },
  tagText: { fontSize: 9.5, lineHeight: 12, fontWeight: '800', color: '#ffffff' },
  tick: { width: 22, height: 22, borderRadius: 11, backgroundColor: TEAL, alignItems: 'center', justifyContent: 'center' },
});
