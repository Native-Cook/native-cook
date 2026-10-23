/**
 * "Requested $64.40 · Undo": an ink slip above the tab bar with a brass Undo. For a split, the requested people's
 * monograms are the flyers that came from the row (Flight), so the toast leaves room for them; for a delete it shows
 * a bin. A brass hairline along its foot drains over the four seconds the toast stays.
 */
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, type SharedValue } from 'react-native-reanimated';

import { toastOverlap } from './Flight';
import { Glyph } from './Glyphs';
import { TIMING } from './motion';
import { PressScale } from './PressScale';
import { COLOR, LAYOUT, SHADOW, TYPE } from './theme';
import { moneyText, others, shares, type Transaction } from './transactions';

export type ToastState =
  | { kind: 'split'; key: string; n: number; amount: number }
  | { kind: 'delete'; key: string; item: Transaction; index: number };

type Props = {
  toast: ToastState | null;
  live: boolean;
  shown: SharedValue<number>;
  countdown: SharedValue<number>;
  top: number;
  width: number;
  onUndo: () => void;
  reduceMotion: boolean;
};

const AV = LAYOUT.mono;
/** The share of the toast's rise that belongs to the "All transactions" link stepping aside (TiltSwipeActions). */
export const LINK_SPAN = 0.1;

function lines(toast: ToastState): { title: string; sub: string; lead: number } {
  if (toast.kind === 'delete') {
    return { title: 'Transaction deleted', sub: `${toast.item.title} · ${moneyText(toast.item.amount)}`, lead: AV };
  }
  const parts = shares(toast.amount, toast.n);
  const requested = parts.slice(1).reduce((s, v) => s + v, 0);
  const each = parts[1];
  const who = toast.n <= 3 ? others(toast.n, ' and ') : `${toast.n - 1} people`;
  const count = toast.n - 1;
  return { title: `Requested ${moneyText(requested, false)}`, sub: `${who} · ${moneyText(each, false)} each`, lead: AV + (count - 1) * (AV - toastOverlap(count)) };
}

export function Toast({ toast, live, shown, countdown, top, width, onUndo, reduceMotion }: Props) {
  // The last toast stays rendered while it fades out.
  const [kept, setKept] = useState<ToastState | null>(toast);
  if (toast && toast !== kept) setKept(toast);
  const box = useAnimatedStyle(() => {
    const s = shown.get();
    // Opaque early in its rise, and only once the ledger's link under it has gone (and gone before the link returns).
    return { opacity: Math.min(Math.max((s - LINK_SPAN) * 5, 0), 1), transform: [{ translateY: (1 - s) * TIMING.toastRise }] };
  });
  const content = useAnimatedStyle(() => ({ opacity: Math.min(Math.max((shown.get() - TIMING.toastContent) / (1 - TIMING.toastContent), 0), 1) }));
  const drain = useAnimatedStyle(() => ({ transform: [{ scaleX: Math.max(countdown.get(), 0.001) }] }));
  if (!kept) return null;
  const { title, sub, lead } = lines(kept);
  return (
    <Animated.View
      style={[styles.toast, { top, width }, box]}
      pointerEvents={live ? 'box-none' : 'none'}
      accessibilityLiveRegion="polite"
      accessibilityElementsHidden={!live}
      importantForAccessibility={live ? 'auto' : 'no-hide-descendants'}
    >
      <Animated.View style={[styles.row, content]} pointerEvents="box-none">
        {kept.kind === 'delete' ? (
          <View style={styles.bin}><Glyph name="trash" color={COLOR.brass} size={16} /></View>
        ) : (
          <View style={{ width: lead, height: AV }} />
        )}
        <View style={styles.text} accessible accessibilityLabel={`${title}. ${sub}`}>
          <Text style={styles.title} numberOfLines={1}>{title}</Text>
          <Text style={styles.sub} numberOfLines={1}>{sub}</Text>
        </View>
        <PressScale
          style={styles.undo}
          scale={0.96}
          reduceMotion={reduceMotion}
          onPress={onUndo}
          accessibilityRole="button"
          accessibilityLabel={kept.kind === 'split' ? 'Undo request' : `Undo, restore ${kept.item.title}`}
          hitSlop={8}
        >
          <Glyph name="undo" color={COLOR.brass} size={16} />
          <Text style={styles.undoText}>Undo</Text>
        </PressScale>
      </Animated.View>
      <Animated.View style={[styles.drain, drain]} pointerEvents="none" />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  toast: {
    position: 'absolute', left: LAYOUT.gutter, height: LAYOUT.toast, borderRadius: LAYOUT.toastRadius, backgroundColor: COLOR.ink,
    boxShadow: SHADOW.toast,
  },
  row: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingLeft: 12, paddingRight: 8, gap: 12 },
  bin: { width: AV, height: AV, borderRadius: AV / 2, backgroundColor: COLOR.monoToast, alignItems: 'center', justifyContent: 'center' },
  text: { flex: 1, minWidth: 0 },
  title: { ...TYPE.toastTitle, color: COLOR.ivory },
  sub: { ...TYPE.toastSub, color: COLOR.ivory55 },
  undo: { height: 40, paddingHorizontal: 12, borderRadius: 12, flexDirection: 'row', alignItems: 'center', gap: 6 },
  undoText: { ...TYPE.toastAction, color: COLOR.brass },
  // The time left to undo: a brass hairline along the slip's foot, draining toward its start.
  drain: {
    position: 'absolute', left: LAYOUT.toastRadius, right: LAYOUT.toastRadius, bottom: 0, height: 1,
    backgroundColor: COLOR.brass, opacity: 0.45, transformOrigin: 'left center',
  },
});
