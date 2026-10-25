import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type DerivedValue, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Path as SvgPath } from 'react-native-svg';

import { type Pose } from './waxSealMotion';

export const CHIPS = ['Sat 14 June', 'Lake Orta', 'RSVP by 1 May'];

const MAIL = [
  ['Studio Morrow', 'Your proofs are ready to review', '9:41'],
  ['Ada Brennan', 'Train times for Saturday', 'Yesterday'],
  ['Lena & Theo', 'Something special in the post', 'Yesterday'],
  ['Harbor Books', 'Your order has shipped', 'Tue'],
  ['Nico Alvarez', 'Dinner on Friday?', 'Mon'],
  ['Pine & Plaster', 'Quote for the hallway', 'Sun'],
];

/** The app under the modal: an inbox, blurred and dimmed. */
export function Inbox({ top }: { top: number }) {
  return (
    <View style={[styles.inbox, { paddingTop: top + 12 }]}>
      <Text style={styles.inboxTitle}>Inbox</Text>
      {MAIL.map(([from, subject, when]) => (
        <View key={from} style={styles.mail}>
          <View style={styles.mailDot} />
          <View style={{ flex: 1 }}>
            <View style={styles.mailTop}>
              <Text style={styles.mailFrom}>{from}</Text>
              <Text style={styles.mailWhen}>{when}</Text>
            </View>
            <Text style={styles.mailSubject}>{subject}</Text>
          </View>
        </View>
      ))}
    </View>
  );
}

type Rsvp = 'yes' | 'no' | null;

/** The card's RSVP buttons (they follow the card) and the close button. */
export function RsvpOverlay({
  open,
  closable,
  card,
  closeTop,
  pose,
  place,
  answer,
  onAnswer,
  onClose,
}: {
  open: boolean;
  closable: boolean;
  card: { x: number; w: number; h: number };
  closeTop: number;
  pose: DerivedValue<Pose>;
  place: DerivedValue<{ top: number; s: number; front: boolean }>;
  answer: Rsvp;
  onAnswer: (a: 'yes' | 'no') => void;
  onClose: () => void;
}) {
  const buttonsStyle = useAnimatedStyle(() => {
    const k = place.get();
    return { opacity: pose.get().buttons, transform: [{ translateY: k.top }, { scale: k.s }] };
  });
  const closeStyle = useAnimatedStyle(() => ({ opacity: pose.get().buttons }));
  return (
    <>
      <Animated.View
        pointerEvents={open ? 'box-none' : 'none'}
        style={[styles.rsvp, { left: card.x, width: card.w, height: card.h }, buttonsStyle]}
      >
        <View style={styles.rsvpRow}>
          <Pressable
            onPress={() => onAnswer('yes')}
            style={({ pressed }) => [styles.pill, styles.yes, answer === 'no' && styles.faded, { transform: [{ scale: pressed ? 0.97 : 1 }] }]}
            accessibilityRole="button"
          >
            <Text style={styles.yesText}>{answer === 'yes' ? 'See you there' : 'Attending'}</Text>
          </Pressable>
          <Pressable
            onPress={() => onAnswer('no')}
            style={({ pressed }) => [styles.pill, styles.no, answer === 'yes' && styles.faded, { transform: [{ scale: pressed ? 0.97 : 1 }] }]}
            accessibilityRole="button"
          >
            <Text style={styles.noText}>{answer === 'no' ? 'Sent your regrets' : "Can't make it"}</Text>
          </Pressable>
        </View>
      </Animated.View>

      <Animated.View pointerEvents={closable ? 'auto' : 'none'} style={[styles.close, { top: closeTop }, closeStyle]}>
        <Pressable
          onPress={onClose}
          hitSlop={8}
          accessibilityRole="button"
          accessibilityLabel="Close and seal again"
          style={styles.closeButton}
        >
          <Svg width={14} height={14} viewBox="0 0 14 14">
            <SvgPath d="M2 2l10 10M12 2L2 12" stroke="#f3e6d4" strokeWidth={1.8} strokeLinecap="round" />
          </Svg>
        </Pressable>
      </Animated.View>
    </>
  );
}

export const styles = StyleSheet.create({
  root: { flex: 1, overflow: 'hidden', backgroundColor: '#f2ede6' },
  dim: { backgroundColor: 'rgba(30,16,12,0.62)' },
  inbox: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, paddingHorizontal: 20, gap: 14 },
  inboxTitle: { fontSize: 34, fontWeight: '800', color: '#1d1714', marginBottom: 8 },
  mail: { flexDirection: 'row', gap: 12, paddingVertical: 6 },
  mailDot: { width: 40, height: 40, borderRadius: 20, backgroundColor: '#cdb9a6' },
  mailTop: { flexDirection: 'row', justifyContent: 'space-between' },
  mailFrom: { fontSize: 16, fontWeight: '700', color: '#1d1714' },
  mailWhen: { fontSize: 13, color: '#8a7d72' },
  mailSubject: { fontSize: 14, color: '#5d524a', marginTop: 2 },
  header: { position: 'absolute', left: 24, right: 72, gap: 4 },
  kicker: { color: '#e9b9a4', fontSize: 11, fontWeight: '700', letterSpacing: 2.4 },
  title: { color: '#fbf3ea', fontSize: 22, fontWeight: '700', letterSpacing: -0.3 },
  sub: { color: 'rgba(251,243,234,0.62)', fontSize: 14, fontWeight: '500' },
  sealHit: { position: 'absolute', borderRadius: 999 },
  rsvp: { position: 'absolute', top: 0 },
  rsvpRow: { position: 'absolute', left: 18, right: 18, bottom: 16, flexDirection: 'row', gap: 10 },
  pill: { flex: 1, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  yes: { backgroundColor: '#7c1a20', boxShadow: '0 1px 2px rgba(60,10,10,0.35), 0 6px 14px rgba(60,10,10,0.18)' },
  no: { borderWidth: 1, borderColor: '#b48f58', backgroundColor: 'rgba(255,250,240,0.6)' },
  faded: { opacity: 0.45 },
  yesText: { color: '#fbf3e6', fontSize: 14, fontWeight: '700' },
  noText: { color: '#4a2a22', fontSize: 14, fontWeight: '600' },
  close: { position: 'absolute', right: 20 },
  closeButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,240,225,0.14)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,240,225,0.3)',
  },
  chips: { position: 'absolute', left: 16, right: 16, flexDirection: 'row', justifyContent: 'center', gap: 8 },
  chip: {
    height: 30,
    paddingHorizontal: 12,
    borderRadius: 15,
    justifyContent: 'center',
    backgroundColor: 'rgba(255,240,225,0.1)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,240,225,0.28)',
  },
  chipText: { color: 'rgba(251,243,234,0.85)', fontSize: 13, fontWeight: '600', fontVariant: ['tabular-nums'] },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: 'rgba(251,243,234,0.55)', fontSize: 13, fontWeight: '500' },
});
