import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedProps, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Rect } from 'react-native-svg';

export const PHONE_HEIGHT = 520;
const PHONE_COLORS = ['#3446b4', '#4a5cc4', '#6a7fdc'] as const;
const NOTCH = { width: 96, height: 24 };

const ICON = 58;
const ICON_LEFT = 22;
const ICON_TOP = 54;
const ROW_PITCH = 92;
const SLOT_TOP = 246;
const SLOT_HEIGHT = 138;
const SLOT_INSET = 14;
const DOCK_HEIGHT = 72;
const DOCK_ICON = 50;
const DASH = [6, 4];

const ICONS: { color: string; glyph: keyof typeof Ionicons.glyphMap; label: string }[] = [
  { color: '#e84c6a', glyph: 'heart', label: 'Health' },
  { color: '#f2634f', glyph: 'cloud', label: 'Weather' },
  { color: '#f4b23e', glyph: 'leaf', label: 'Garden' },
  { color: '#4bd964', glyph: 'flag', label: 'Goals' },
  { color: '#3ccfa8', glyph: 'triangle', label: 'Trails' },
  { color: '#3aa9e0', glyph: 'star', label: 'Stars' },
  { color: '#5d6cc8', glyph: 'sunny', label: 'Sun' },
  { color: '#7a55d9', glyph: 'moon', label: 'Sleep' },
];
const DOCK: { color: string; glyph: keyof typeof Ionicons.glyphMap }[] = [
  { color: '#34c759', glyph: 'call' },
  { color: '#0a84ff', glyph: 'compass' },
  { color: '#30d158', glyph: 'chatbubble' },
  { color: '#ff9f0a', glyph: 'musical-notes' },
];
// The widget covers the two icons at the left of each row.
const COVERED = [0, 1, 4, 5];

const TILES = ['#3fd6a8', '#f0476b', '#f4b23e', '#3cc4e0'];
const TILE_DAYS = ['21', '7', '1', '9'];
const GREEN = '#2fcf8a';
const WHITE = '#ffffff';
const GREY = '#e9e9ec';
const INK = '#14141f';
const MUTE = '#8a8a96';
const PINK = '#f0476b';
const AMBER = '#f4a000';
const CYAN = '#1fb3d6';

function lerp(from: number, to: number, t: number) {
  'worklet';
  return from + (to - from) * t;
}

function ramp(value: number, from: number, to: number) {
  'worklet';
  const t = Math.min(1, Math.max(0, (value - from) / (to - from)));
  return t * t * (3 - 2 * t);
}

/** 0 → 1 with a small overshoot, for a pop. */
function pop(t: number) {
  'worklet';
  const c = 1.9;
  return 1 + (c + 1) * (t - 1) * (t - 1) * (t - 1) + c * (t - 1) * (t - 1);
}

type Props = {
  width: number;
  /** Which slide is on show, as a number that moves between slides: 0 to 4. */
  step: SharedValue<number>;
  /** 0 → 8 every second, the march of the dashes round the empty slot. */
  dash: SharedValue<number>;
  /** 0 → 1: a light that crosses the picker's tiles once. */
  shimmer: SharedValue<number>;
  labels: {
    search: string;
    countdown: string;
    until: string;
    event: string;
    trip: string;
    tomorrow: string;
    firstDate: string;
    secondDate: string;
    firstName: string;
    secondName: string;
    done: string;
    edit: string;
  };
};

const AnimatedRect = Animated.createAnimatedComponent(Rect);
const AnimatedBlurView = Animated.createAnimatedComponent(BlurView);

/**
 * The miniature home screen inside the sheet. Everything here is a function of `step`, so the
 * slides cross-fade into each other, run backwards, and stop wherever a swipe leaves them.
 */
export function Scene({ width, step, dash, shimmer, labels }: Props) {
  const slotWidth = width - SLOT_INSET * 2;
  const pitch = (width - ICON_LEFT * 2 - ICON) / 3;
  const widgetWidth = pitch + ICON;
  const widgetHeight = ROW_PITCH + ICON;
  const pickerTop = 70;
  const tileWidth = (width - 12 - 28 - 12) / 2;

  const slotStyle = useAnimatedStyle(() => ({ opacity: 1 - ramp(step.get(), 2.3, 2.7) }));
  const slotProps = useAnimatedProps(() => ({ strokeDashoffset: -dash.get() }));
  const plusStyle = useAnimatedStyle(() => {
    const s = step.get();
    const press = 1 - 0.1 * Math.max(0, 1 - Math.abs(s - 1.45) * 6);
    const t = pop(ramp(s, 0.35, 1)) * (1 - ramp(s, 2.6, 2.9));
    return { opacity: ramp(s, 0.35, 0.6) * (1 - ramp(s, 2.6, 2.9)), transform: [{ scale: t * press }] };
  });
  const pickerStyle = useAnimatedStyle(() => {
    const s = step.get();
    const up = 1 - Math.pow(1 - ramp(s, 1.45, 2.05), 3);
    const down = ramp(s, 2.55, 3);
    return { transform: [{ translateY: lerp(PHONE_HEIGHT - pickerTop, 0, up) + down * (PHONE_HEIGHT - pickerTop) }] };
  });
  const shimmerStyle = useAnimatedStyle(() => ({ transform: [{ translateX: lerp(-width, width, shimmer.get()) }] }));
  const greenStyle = useAnimatedStyle(() => {
    const s = step.get();
    const t = ramp(s, 2.55, 2.95);
    return { opacity: t, transform: [{ scale: lerp(1.15, 1, t) }] };
  });
  const listStyle = useAnimatedStyle(() => {
    const t = ramp(step.get(), 2.65, 3);
    return { opacity: t, transform: [{ translateY: lerp(24, 0, t) }] };
  });
  const doneStyle = useAnimatedStyle(() => {
    const s = step.get();
    return { opacity: ramp(s, 2.7, 3) * (1 - ramp(s, 3.2, 3.5)) };
  });
  const blurProps = useAnimatedProps(() => ({ intensity: 40 * ramp(step.get(), 3.1, 3.9) }));
  const menuStyle = useAnimatedStyle(() => {
    const t = ramp(step.get(), 3.4, 3.95);
    return { opacity: Math.min(1, t * 3), transform: [{ scale: pop(t) }] };
  });

  return (
    <View style={[styles.phone, { width }]}>
      <LinearGradient colors={PHONE_COLORS} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      {/* Soft light and shade across the wallpaper, so it reads as a picture and not a fill. */}
      <View style={[styles.blob, { left: -80, top: -60, width: 260, height: 260, borderRadius: 130, backgroundColor: '#8fa2ff', opacity: 0.28 }]} />
      <View style={[styles.blob, { right: -90, top: 150, width: 280, height: 280, borderRadius: 140, backgroundColor: '#25319a', opacity: 0.35 }]} />
      <View style={[styles.blob, { left: 60, bottom: -120, width: 240, height: 240, borderRadius: 120, backgroundColor: '#a7b4ff', opacity: 0.2 }]} />

      <View style={[styles.searchPill, { left: (width - 92) / 2, bottom: 12 + DOCK_HEIGHT + 12 }]}>
        <Ionicons name="search" size={12} color={WHITE} />
        <Text style={styles.searchPillText}>Search</Text>
      </View>
      <View style={[styles.dock, { left: SLOT_INSET, right: SLOT_INSET, bottom: 12 }]}>
        {DOCK.map((item) => (
          <View key={item.glyph} style={[styles.dockIcon, { backgroundColor: item.color }]}>
            <LinearGradient colors={['rgba(255,255,255,0.28)', 'rgba(255,255,255,0)']} style={StyleSheet.absoluteFill} />
            <Ionicons name={item.glyph} size={24} color={WHITE} />
          </View>
        ))}
      </View>

      {ICONS.map((icon, index) => (
        <AppIcon key={icon.glyph} index={index} icon={icon} step={step} left={ICON_LEFT + (index % 4) * pitch} top={ICON_TOP + Math.floor(index / 4) * ROW_PITCH} />
      ))}

      <Animated.View style={[styles.slot, { left: SLOT_INSET, top: SLOT_TOP, width: slotWidth, height: SLOT_HEIGHT }, slotStyle]}>
        <Svg width={slotWidth} height={SLOT_HEIGHT}>
          <AnimatedRect
            x={1}
            y={1}
            width={slotWidth - 2}
            height={SLOT_HEIGHT - 2}
            rx={18}
            fill="none"
            stroke="rgba(255,255,255,0.85)"
            strokeWidth={1.5}
            strokeDasharray={DASH}
            animatedProps={slotProps}
          />
        </Svg>
      </Animated.View>

      <Animated.View style={[styles.list, { left: SLOT_INSET, top: SLOT_TOP, width: slotWidth, height: SLOT_HEIGHT }, listStyle]}>
        <View style={styles.listHead}>
          <View>
            <Text style={[styles.listTitle, { color: AMBER }]}>{labels.trip}</Text>
            <Text style={styles.listSub}>{labels.tomorrow}</Text>
          </View>
          <View style={styles.listBadge}>
            <Ionicons name="star" size={12} color={AMBER} />
          </View>
        </View>
        <View style={styles.listRow}>
          <Text style={[styles.listName, { color: PINK }]} numberOfLines={1}>
            {labels.firstName}
          </Text>
          <Text style={styles.listDate}>{labels.firstDate}</Text>
        </View>
        <View style={styles.listRow}>
          <Text style={[styles.listName, { color: CYAN }]} numberOfLines={1}>
            {labels.secondName}
          </Text>
          <Text style={styles.listDate}>{labels.secondDate}</Text>
        </View>
      </Animated.View>

      <AnimatedBlurView pointerEvents="none" tint="systemUltraThinMaterialLight" style={StyleSheet.absoluteFill} animatedProps={blurProps} />

      <Animated.View style={[styles.green, { left: ICON_LEFT, top: ICON_TOP, width: widgetWidth, height: widgetHeight }, greenStyle]}>
        <Text style={styles.greenNumber}>21</Text>
        <Text style={styles.greenSub}>{labels.until}</Text>
        <Text style={styles.greenEvent}>{labels.event}</Text>
        <View style={styles.greenBadge}>
          <Ionicons name="gift" size={14} color={GREEN} />
        </View>
      </Animated.View>

      <Animated.View style={[styles.menu, { left: ICON_LEFT, top: ICON_TOP + widgetHeight + 8 }, menuStyle]}>
        <View style={styles.menuHead}>
          <Text style={styles.menuTitle}>{labels.edit}</Text>
          <Ionicons name="information-circle-outline" size={16} color={MUTE} />
        </View>
        <View style={styles.menuRule} />
        <View style={styles.menuRow}>
          <Text style={styles.menuLabel}>Event</Text>
          <Text style={[styles.menuValue, { color: PINK }]}>{labels.event}</Text>
        </View>
        <View style={styles.menuRow}>
          <Text style={styles.menuLabel}>Show days</Text>
          <View style={styles.toggle}>
            <View style={styles.knob} />
          </View>
        </View>
      </Animated.View>

      <Animated.View style={[styles.plus, plusStyle]}>
        <Ionicons name="add" size={18} color={INK} />
      </Animated.View>

      <Animated.View style={[styles.done, doneStyle]}>
        <Text style={styles.doneText}>{labels.done}</Text>
      </Animated.View>

      <Animated.View style={[styles.picker, { top: pickerTop }, pickerStyle]}>
        <View style={styles.search}>
          <Ionicons name="search" size={15} color={MUTE} />
          <Text style={styles.searchText}>{labels.countdown}</Text>
        </View>
        <View style={styles.tiles}>
          {TILES.map((color, index) => (
            <View key={color} style={[styles.tile, { width: tileWidth, backgroundColor: color }]}>
              <Text style={styles.tileNumber}>{TILE_DAYS[index]}</Text>
              <View style={[styles.line, { width: '70%' }]} />
              <View style={[styles.line, { width: '50%' }]} />
            </View>
          ))}
        </View>
        <Animated.View pointerEvents="none" style={[styles.shimmer, shimmerStyle]}>
          <LinearGradient colors={['rgba(255,255,255,0)', 'rgba(255,255,255,0.55)', 'rgba(255,255,255,0)']} start={{ x: 0, y: 0.5 }} end={{ x: 1, y: 0.5 }} style={StyleSheet.absoluteFill} />
        </Animated.View>
      </Animated.View>

      <View style={[styles.notch, { left: (width - NOTCH.width) / 2 }]} />
    </View>
  );
}

/** An app icon: dimmed a little once the plus shows, and covered when the widget lands on it. */
function AppIcon({
  index,
  icon,
  step,
  left,
  top,
}: {
  index: number;
  icon: { color: string; glyph: keyof typeof Ionicons.glyphMap; label: string };
  step: SharedValue<number>;
  left: number;
  top: number;
}) {
  const covered = COVERED.includes(index);
  const style = useAnimatedStyle(() => {
    const s = step.get();
    const dim = lerp(1, 0.75, ramp(s, 0.4, 1));
    return { opacity: covered ? dim * (1 - ramp(s, 2.55, 2.95)) : dim };
  });
  return (
    <Animated.View style={[styles.iconCell, { left: left - 6, top }, style]}>
      <View style={[styles.icon, { backgroundColor: icon.color }]}>
        <LinearGradient colors={['rgba(255,255,255,0.3)', 'rgba(255,255,255,0)']} locations={[0, 0.6]} style={StyleSheet.absoluteFill} />
        <Ionicons name={icon.glyph} size={27} color={WHITE} />
      </View>
      <Text style={styles.iconLabel} numberOfLines={1}>
        {icon.label}
      </Text>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  phone: { height: PHONE_HEIGHT, borderRadius: 30, overflow: 'hidden' },
  blob: { position: 'absolute' },
  iconCell: { position: 'absolute', width: ICON + 12, alignItems: 'center', gap: 5 },
  icon: { width: ICON, height: ICON, borderRadius: 15, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  iconLabel: { fontSize: 11, lineHeight: 13, fontWeight: '500', color: WHITE },
  searchPill: { position: 'absolute', width: 92, height: 28, borderRadius: 14, backgroundColor: 'rgba(255,255,255,0.22)', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 5 },
  searchPillText: { fontSize: 12, lineHeight: 15, fontWeight: '600', color: WHITE },
  dock: { position: 'absolute', height: DOCK_HEIGHT, borderRadius: 26, backgroundColor: 'rgba(255,255,255,0.22)', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around', paddingHorizontal: 10 },
  dockIcon: { width: DOCK_ICON, height: DOCK_ICON, borderRadius: 13, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  slot: { position: 'absolute' },
  notch: { position: 'absolute', top: 0, width: NOTCH.width, height: NOTCH.height, borderBottomLeftRadius: NOTCH.height / 2, borderBottomRightRadius: NOTCH.height / 2, backgroundColor: WHITE },
  plus: { position: 'absolute', left: 14, top: 10, width: 42, height: 26, borderRadius: 13, backgroundColor: WHITE, alignItems: 'center', justifyContent: 'center', shadowColor: '#000000', shadowOpacity: 0.15, shadowRadius: 4, shadowOffset: { width: 0, height: 2 } },
  done: { position: 'absolute', right: 14, top: 10, height: 26, paddingHorizontal: 12, borderRadius: 13, backgroundColor: WHITE, alignItems: 'center', justifyContent: 'center', shadowColor: '#000000', shadowOpacity: 0.15, shadowRadius: 4, shadowOffset: { width: 0, height: 2 } },
  doneText: { fontSize: 13, lineHeight: 16, fontWeight: '800', color: INK },
  green: { position: 'absolute', borderRadius: 26, backgroundColor: GREEN, padding: 14, shadowColor: '#0b3b26', shadowOpacity: 0.35, shadowRadius: 14, shadowOffset: { width: 0, height: 8 } },
  greenNumber: { fontSize: 40, lineHeight: 44, fontWeight: '800', letterSpacing: -1, color: WHITE },
  greenSub: { fontSize: 12, lineHeight: 15, fontWeight: '600', color: 'rgba(255,255,255,0.82)' },
  greenEvent: { marginTop: 2, fontSize: 14, lineHeight: 17, fontWeight: '800', color: WHITE },
  greenBadge: { position: 'absolute', top: 14, right: 14, width: 26, height: 26, borderRadius: 8, backgroundColor: WHITE, alignItems: 'center', justifyContent: 'center' },
  list: { position: 'absolute', borderRadius: 24, backgroundColor: WHITE, paddingHorizontal: 16, paddingVertical: 14, justifyContent: 'space-between', shadowColor: '#000000', shadowOpacity: 0.18, shadowRadius: 12, shadowOffset: { width: 0, height: 6 } },
  listHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  listTitle: { fontSize: 16, lineHeight: 19, fontWeight: '800' },
  listSub: { fontSize: 13, lineHeight: 16, fontWeight: '700', color: INK },
  listBadge: { width: 28, height: 28, borderRadius: 9, backgroundColor: '#fff2d6', alignItems: 'center', justifyContent: 'center' },
  listRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  listName: { flex: 1, fontSize: 13, lineHeight: 16, fontWeight: '800' },
  listDate: { fontSize: 12, lineHeight: 15, fontWeight: '700', color: INK },
  menu: { position: 'absolute', width: 196, borderRadius: 16, backgroundColor: WHITE, padding: 12, gap: 10, transformOrigin: 'left top', shadowColor: '#000000', shadowOpacity: 0.25, shadowRadius: 18, shadowOffset: { width: 0, height: 8 } },
  menuHead: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  menuTitle: { fontSize: 14, lineHeight: 18, fontWeight: '800', color: INK },
  menuRule: { height: StyleSheet.hairlineWidth, backgroundColor: '#e2e2e8', marginHorizontal: -12 },
  menuRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  menuLabel: { fontSize: 13, lineHeight: 16, fontWeight: '500', color: MUTE },
  menuValue: { fontSize: 13, lineHeight: 16, fontWeight: '700' },
  toggle: { width: 36, height: 22, borderRadius: 11, backgroundColor: PINK, padding: 2, alignItems: 'flex-end' },
  knob: { width: 18, height: 18, borderRadius: 9, backgroundColor: WHITE },
  picker: { position: 'absolute', left: 6, right: 6, height: PHONE_HEIGHT, borderRadius: 24, backgroundColor: '#f6f6f8', padding: 14, gap: 12, overflow: 'hidden' },
  search: { height: 38, borderRadius: 12, backgroundColor: GREY, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', gap: 6 },
  searchText: { fontSize: 15, lineHeight: 19, color: MUTE },
  tiles: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  tile: { height: 132, borderRadius: 20, padding: 12, gap: 6 },
  tileNumber: { fontSize: 30, lineHeight: 34, fontWeight: '800', color: WHITE, marginBottom: 4 },
  line: { height: 7, borderRadius: 3.5, backgroundColor: 'rgba(255,255,255,0.6)' },
  shimmer: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 140 },
});
