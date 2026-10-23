import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { type Ref, useEffect } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, {
  ReduceMotion,
  cancelAnimation,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

export type FileItem = {
  name: string;
  size: string;
  /** Tile colour, the glyph on it and the glyph's colour. */
  tint: string;
  icon: keyof typeof Ionicons.glyphMap;
  ink?: string;
  /** Written under the name, after the size. */
  detail?: string;
  /** A picture shown in place of the tile. */
  photo?: string;
};

export const ROW_HEIGHT = 72;
export const ROW_GAP = 12;
export const TRASH_SIZE = 36;
export const TRASH_INK = '#d14b5a';
const COLLAPSE_MS = 350;
const RETURN_MS = 500;

type Props = {
  file: FileItem;
  /** True once the file is deleted: the row fades and closes up, and comes back when it is false. */
  removed: boolean;
  /** The trash button is pressed down (by a touch or by the loop). */
  pressed: boolean;
  trashRef: Ref<View>;
  onTrash: () => void;
};

/** One file in the list; its trash button is where the dialog grows from. */
export function FileRow({ file, removed, pressed, trashRef, onTrash }: Props) {
  const present = useSharedValue(1);

  useEffect(() => {
    present.set(withTiming(removed ? 0 : 1, { duration: removed ? COLLAPSE_MS : RETURN_MS, reduceMotion: ReduceMotion.Never }));
  }, [removed, present]);
  useEffect(() => () => cancelAnimation(present), [present]);

  // The row fades and shrinks a little faster than its slot closes, so it never overlaps the next one.
  const wrap = useAnimatedStyle(() => {
    const p = present.get();
    return { height: (ROW_HEIGHT + ROW_GAP) * p, opacity: p * p * p, transform: [{ scale: 0.94 + 0.06 * p }] };
  });

  return (
    <Animated.View style={[styles.wrap, wrap]} pointerEvents={removed ? 'none' : 'auto'}>
      <View style={styles.row}>
        {file.photo ? (
          <Image source={file.photo} style={styles.tile} contentFit="cover" cachePolicy="memory-disk" transition={0} />
        ) : (
          <View style={[styles.tile, { backgroundColor: file.tint }]}>
            <Ionicons name={file.icon} size={22} color={file.ink ?? 'rgba(0,0,0,0.55)'} />
          </View>
        )}
        <View style={styles.text}>
          <Text style={styles.name} numberOfLines={1}>
            {file.name}
          </Text>
          <Text style={styles.size} numberOfLines={1}>
            {file.detail ? `${file.size} · ${file.detail}` : file.size}
          </Text>
        </View>
        <Pressable onPress={onTrash} hitSlop={8} accessibilityRole="button" accessibilityLabel={`Delete ${file.name}`}>
          <Animated.View
            ref={trashRef}
            collapsable={false}
            style={[styles.trash, { transform: [{ scale: pressed ? 0.9 : 1 }], backgroundColor: pressed ? '#f7d6da' : '#fdf0f1', transitionProperty: ['transform', 'backgroundColor'], transitionDuration: 120 }]}
          >
            <Ionicons name="trash-outline" size={19} color={TRASH_INK} />
          </Animated.View>
        </Pressable>
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: { overflow: 'visible' },
  row: {
    height: ROW_HEIGHT,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: '#ffffff',
    flexDirection: 'row',
    alignItems: 'center',
    paddingLeft: 14,
    paddingRight: 16,
    gap: 14,
    boxShadow: [
      { offsetX: 0, offsetY: 1, blurRadius: 2, color: 'rgba(17,19,24,0.04)' },
      { offsetX: 0, offsetY: 6, blurRadius: 18, color: 'rgba(17,19,24,0.05)' },
    ],
  },
  tile: { width: 46, height: 46, borderRadius: 13, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
  text: { flex: 1 },
  name: { color: '#111318', fontSize: 16, lineHeight: 21, fontWeight: '600', letterSpacing: -0.2 },
  size: { color: '#7c808a', fontSize: 13, lineHeight: 17, marginTop: 2 },
  trash: { width: TRASH_SIZE, height: TRASH_SIZE, borderRadius: 11, borderCurve: 'continuous', alignItems: 'center', justifyContent: 'center' },
});
