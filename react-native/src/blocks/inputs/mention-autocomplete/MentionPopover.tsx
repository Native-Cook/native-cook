import { memo, useEffect, useRef } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';

import { Avatar } from './Avatar';
import { MAX_ROWS, PEOPLE, type Person, fullName, matchRange } from './mentionData';
import { POP_HEAD, POP_PAD, type Pose, ROW_H, autoPose, clamp01 } from './mentionMotion';

/*
 MentionPopover — the people list that grows out of the caret.

 It sits above the composer, anchored at its bottom edge; it scales from the caret's x (`anchor`)
 and resizes to the rows it holds. Rows are placed by slot (0 = top) from the bottom edge, so the
 list can grow and shrink while every row slides to its new place. The loop places rows from its
 pose; a person's typing places them with springs.
*/

export const POP_MAX_H = POP_HEAD + POP_PAD * 2 + MAX_ROWS * ROW_H;

type Props = {
  pose: SharedValue<Pose>;
  query: string;
  /** Slot of each person for the current query, -1 when not listed. */
  slots: number[];
  open: boolean;
  driver: 'auto' | 'user';
  /** Loop time the person took over at: rows start from where the loop had them. */
  takeT: number;
  left: number;
  bottom: number;
  width: number;
  /** Caret x inside the popover, the point it grows from. */
  anchor: number;
  channel: string;
  accent: string;
  onPick: (person: number) => void;
};

export function MentionPopover({ pose, query, slots, open, driver, takeT, left, bottom, width, anchor, channel, accent, onPick }: Props) {
  const top = bottom - POP_MAX_H;
  const frame = useAnimatedStyle(() => {
    const p = pose.get().pop;
    const s = 0.3 + 0.7 * p;
    const dx = anchor - width / 2;
    return {
      opacity: clamp01(p * 2.2),
      transform: [{ translateX: dx }, { translateY: POP_MAX_H / 2 }, { scale: s }, { translateY: -POP_MAX_H / 2 }, { translateX: -dx }],
    };
  });
  const bg = useAnimatedStyle(() => ({ height: POP_HEAD + POP_PAD * 2 + Math.max(0, pose.get().count) * ROW_H }));
  const head = useAnimatedStyle(() => ({ transform: [{ translateY: -(POP_PAD + Math.max(0, pose.get().count) * ROW_H) }] }));
  const highlight = useAnimatedStyle(() => {
    const n = Math.max(0, pose.get().count);
    return { opacity: clamp01(n), transform: [{ translateY: -(POP_PAD + (n - 1) * ROW_H) }] };
  });

  return (
    <Animated.View style={[styles.frame, { left, top, width, height: POP_MAX_H }, frame]} pointerEvents={open ? 'box-none' : 'none'}>
      <Animated.View style={[styles.bg, bg]} />
      <Animated.View style={[styles.head, head]}>
        <Text style={styles.headText} numberOfLines={1}>
          People in <Text style={styles.headChannel}>{channel}</Text>
        </Text>
        <Text style={styles.headHint}>return to pick</Text>
      </Animated.View>
      <Animated.View style={[styles.highlight, { backgroundColor: `${accent}14` }, highlight]} pointerEvents="none" />
      {PEOPLE.map((p, i) => (
        <Row key={p.id} index={i} person={p} query={query} slot={slots[i]} open={open} driver={driver} takeT={takeT} pose={pose} onPick={onPick} />
      ))}
    </Animated.View>
  );
}

type RowProps = {
  index: number;
  person: Person;
  query: string;
  slot: number;
  open: boolean;
  driver: 'auto' | 'user';
  takeT: number;
  pose: SharedValue<Pose>;
  onPick: (person: number) => void;
};

const Row = memo(function Row({ index, person, query, slot, open, driver, takeT, pose, onPick }: RowProps) {
  const y = useSharedValue(0);
  const a = useSharedValue(0);
  const userMode = useSharedValue(0);
  const was = useRef(driver);

  useEffect(() => {
    if (driver === 'auto') {
      userMode.set(0);
      was.current = driver;
      return;
    }
    if (was.current === 'auto') {
      const p = autoPose(takeT);
      y.set(p.rowY[index]);
      a.set(p.rowA[index]);
      was.current = driver;
    }
    userMode.set(1);
    const visible = open && slot >= 0;
    if (slot >= 0) y.set(withSpring(slot, { duration: 380, dampingRatio: 0.72 }));
    a.set(withTiming(visible ? 1 : 0, { duration: visible ? 220 : 150 }));
  }, [a, driver, index, open, slot, takeT, userMode, y]);

  const style = useAnimatedStyle(() => {
    const p = pose.get();
    const yy = userMode.get() ? y.get() : p.rowY[index] ?? 0;
    const aa = clamp01(userMode.get() ? a.get() : p.rowA[index] ?? 0);
    const n = Math.max(0, p.count);
    return {
      opacity: aa,
      transform: [{ translateY: -(POP_PAD + (n - 1 - yy) * ROW_H) + 6 * (1 - aa) }, { scale: 0.96 + 0.04 * aa }],
    };
  });

  const name = fullName(person);
  const range = matchRange(person, query);
  const visible = open && slot >= 0;
  return (
    <Animated.View style={[styles.row, style]} pointerEvents={visible ? 'auto' : 'none'}>
      <Pressable
        onPress={() => onPick(index)}
        style={({ pressed }) => [styles.rowPress, pressed && styles.rowPressed]}
        accessibilityRole="button"
        accessibilityLabel={`Mention ${name}`}
      >
        <Avatar initials={`${person.first[0]}${person.last[0]}`} colors={person.colors} size={32} online={person.online} ring="#FFFEFB" />
        <View style={styles.rowText}>
          <Text style={styles.name} numberOfLines={1}>
            {range ? (
              <>
                {name.slice(0, range[0])}
                <Text style={styles.match}>{name.slice(range[0], range[1])}</Text>
                {name.slice(range[1])}
              </>
            ) : (
              name
            )}
          </Text>
          <Text style={styles.role} numberOfLines={1}>
            {person.role}
            {person.online ? '' : ' · away'}
          </Text>
        </View>
      </Pressable>
    </Animated.View>
  );
});

const styles = StyleSheet.create({
  frame: { position: 'absolute' },
  bg: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: '#FFFEFB',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(30,28,24,0.12)',
    boxShadow: '0 2px 4px rgba(30,26,20,0.08), 0 16px 36px rgba(30,26,20,0.16)',
  },
  head: { position: 'absolute', left: 14, right: 14, bottom: 0, height: POP_HEAD, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  headText: { flex: 1, fontSize: 12.5, fontWeight: '500', color: '#7A766D' },
  headChannel: { color: '#3F3C36', fontWeight: '600' },
  headHint: { fontSize: 12, color: '#A7A297' },
  highlight: { position: 'absolute', left: 6, right: 6, bottom: 0, height: ROW_H, borderRadius: 12, borderCurve: 'continuous' },
  row: { position: 'absolute', left: 6, right: 6, bottom: 0, height: ROW_H },
  rowPress: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 11, paddingHorizontal: 9, borderRadius: 12, borderCurve: 'continuous' },
  rowPressed: { backgroundColor: 'rgba(30,28,24,0.05)' },
  rowText: { flex: 1, gap: 1 },
  name: { fontSize: 16, color: '#57534B', letterSpacing: -0.2 },
  match: { fontWeight: '700', color: '#1E1D1A' },
  role: { fontSize: 13, color: '#8A857B' },
});
