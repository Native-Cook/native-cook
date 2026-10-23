import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { CARD_DATA, PAYMENTS, type Payment } from './data';
import { ArrowGlyph, GridGlyph, LedgerGlyph, PlusGlyph } from './glyphs';
import { PRESS_LIFT } from './motion';
import { CARD, COLOR, FAMILY, FONT, HEADER, HINT, LIST } from './theme';

type Props = { width: number; oy: number; lift: SharedValue<number>; arm: SharedValue<number>; sheet: SharedValue<number> };

/** The two round buttons beside the island. They stay put; the sheet covers them. */
export function HeaderButtons({ width, oy, sheet }: Pick<Props, 'width' | 'oy' | 'sheet'>) {
  const style = useAnimatedStyle(() => ({ opacity: 1 - Math.min(1, sheet.get() * 1.6) }));
  const top = oy + HEADER.buttonY - HEADER.button / 2;
  return (
    <Animated.View style={[StyleSheet.absoluteFill, style]} pointerEvents="box-none">
      <View accessible accessibilityRole="button" accessibilityLabel="Menu"
        style={[styles.round, { top, left: HEADER.inset }]}>
        <GridGlyph color={COLOR.ink} size={HEADER.gridGlyph} />
      </View>
      <View accessible accessibilityRole="button" accessibilityLabel="Add card"
        style={[styles.round, { top, left: width - HEADER.inset - HEADER.button }]}>
        <PlusGlyph color={COLOR.ink} size={HEADER.plusGlyph} stroke={1.5} />
      </View>
    </Animated.View>
  );
}

function Action({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={styles.action}>
      <View style={styles.actionDisc}>{children}</View>
      <Text style={styles.actionLabel}>{label}</Text>
    </View>
  );
}

export function PaymentCard({ width }: { width: number }) {
  const w = width - CARD.inset * 2;
  return (
    <View style={[styles.card, { width: w }]}>
      <Text style={[styles.brand, { top: CARD.brandY - 13 }]}>{CARD_DATA.network}</Text>
      <Text style={[styles.balance, { top: CARD.brandY - 19 }]} numberOfLines={1}>{CARD_DATA.balance}</Text>
      <View style={styles.chip} />
      {/* Three groups of four round dots, drawn (the pin's dots are rounder and bolder than a bullet glyph). */}
      {[0, 1, 2].map(g => [0, 1, 2, 3].map(d => (
        <View key={`${g}${d}`} style={[styles.dot, {
          left: CARD.dots.x + g * CARD.dots.groupPitch + d * CARD.dots.pitch, top: CARD.dots.cy - CARD.dots.size / 2,
        }]} />
      )))}
      <Text style={[styles.number, { position: 'absolute', left: CARD.last4X, top: CARD.numberY - 10 }]}>{CARD_DATA.last4}</Text>
      <Text style={[styles.tiny, styles.right, { top: CARD.validY - 5 }]}>VALID THRU</Text>
      <Text style={[styles.number, styles.right, styles.valid, { position: 'absolute', top: CARD.numberY - 9 }]}>{CARD_DATA.valid}</Text>
      <Text style={[styles.tiny, { left: CARD.padX, top: CARD.nameY - 5 }]}>{CARD_DATA.holder}</Text>
      <View style={[styles.actions, { top: CARD.actionY - CARD.action / 2 }]}>
        <Action label="Send to"><ArrowGlyph direction="upLeft" color={COLOR.white} size={CARD.actionGlyph} /></Action>
        <Action label="Receive"><ArrowGlyph direction="downLeft" color={COLOR.white} size={CARD.actionGlyph} /></Action>
        <Action label="Add"><PlusGlyph color={COLOR.white} size={CARD.actionGlyph} stroke={1.5} /></Action>
      </View>
    </View>
  );
}

function PaymentRow({ item, width }: { item: Payment; width: number }) {
  return (
    <View style={[styles.row, { width: width - LIST.inset * 2 }]}
      accessible accessibilityLabel={`${item.merchant}, ${item.date}, ${item.amount.replace(' ', '')}`}>
      <View style={styles.rowIcon}><LedgerGlyph incoming={item.incoming} color={COLOR.inkSoft} size={LIST.glyph} /></View>
      <View style={styles.rowText}>
        <Text style={styles.rowName} numberOfLines={1}>{item.merchant}</Text>
        <Text style={styles.rowDate}>{item.date}</Text>
      </View>
      <Text style={styles.rowAmount}>{item.amount}</Text>
    </View>
  );
}

/** Hint, card and transactions travel as one column: the card's lift carries them all. */
export function HomeColumn({ width, oy, lift, hintLift, arm, cardSlot }: Props & { cardSlot: React.ReactNode; hintLift: SharedValue<number> }) {
  const column = useAnimatedStyle(() => ({ transform: [{ translateY: lift.get() }] }));
  const hint = useAnimatedStyle(() => ({ opacity: arm.get(), transform: [{ translateY: hintLift.get() - lift.get() }] }));
  return (
    <Animated.View style={[StyleSheet.absoluteFill, column]} pointerEvents="box-none">
      <Animated.Text style={[styles.hint, { top: oy + HINT.y - 11 - PRESS_LIFT, width }, hint]}>Drag to send</Animated.Text>
      <View style={{ position: 'absolute', top: oy + CARD.top, left: CARD.inset }}>{cardSlot}</View>
      <View style={[styles.listHead, { top: oy + LIST.titleY, left: LIST.inset, width: width - LIST.inset * 2 }]}>
        <Text style={styles.sectionLine}>
          <Text style={styles.sectionSoft}>{'Last\n'}</Text>
          <Text style={styles.sectionStrong}>Transactions</Text>
        </Text>
      </View>
      <View accessible accessibilityRole="button" accessibilityLabel="All transactions"
        style={[styles.roundArrow, { left: LIST.arrowX - LIST.arrowButton / 2, top: oy + LIST.arrowY - LIST.arrowButton / 2 }]}>
        <ArrowGlyph direction="upRight" color={COLOR.ink} size={LIST.arrowGlyph} stroke={1.5} />
      </View>
      {PAYMENTS.map((item, i) => (
        <View key={i} style={{ position: 'absolute', left: LIST.inset, top: oy + LIST.top + i * LIST.pitch }}>
          <PaymentRow item={item} width={width} />
        </View>
      ))}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  round: {
    position: 'absolute', width: HEADER.button, height: HEADER.button, borderRadius: HEADER.button / 2,
    backgroundColor: COLOR.chip, alignItems: 'center', justifyContent: 'center',
  },
  hint: { position: 'absolute', left: 0, textAlign: 'center', fontFamily: FAMILY.regular, fontSize: FONT.hint, lineHeight: 22, color: COLOR.inkSoft },
  card: { height: CARD.height, borderRadius: CARD.radius, backgroundColor: COLOR.blue, borderCurve: 'continuous' },
  brand: {
    position: 'absolute', left: CARD.padX, fontSize: FONT.brand, fontWeight: '900', fontStyle: 'italic',
    color: COLOR.white, letterSpacing: -3.4,
  },
  balance: {
    position: 'absolute', right: CARD.padRight, fontFamily: FAMILY.semibold, fontSize: FONT.balance, lineHeight: 44, color: COLOR.white,
    letterSpacing: -0.3,
  },
  chip: {
    position: 'absolute', left: CARD.padX, top: CARD.chipY, width: CARD.chip.w, height: CARD.chip.h,
    borderRadius: CARD.chip.r, backgroundColor: COLOR.cardChip,
  },
  dot: { position: 'absolute', width: CARD.dots.size, height: CARD.dots.size, borderRadius: CARD.dots.size / 2, backgroundColor: COLOR.white },
  number: { fontFamily: FAMILY.semibold, fontSize: FONT.number, lineHeight: 20, color: COLOR.white },
  valid: { letterSpacing: 0, fontSize: FONT.valid, right: CARD.validRight },
  tiny: { position: 'absolute', fontFamily: FAMILY.medium, fontSize: FONT.tiny, lineHeight: 10, color: COLOR.blueText, letterSpacing: 0.2 },
  right: { right: CARD.padRight, textAlign: 'right' },
  actions: { position: 'absolute', left: 0, right: 0, flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 19 },
  action: { alignItems: 'center', width: CARD.action },
  actionDisc: {
    width: CARD.action, height: CARD.action, borderRadius: CARD.action / 2, backgroundColor: COLOR.blueLift,
    alignItems: 'center', justifyContent: 'center',
  },
  actionLabel: { marginTop: CARD.labelGap, fontFamily: FAMILY.regular, fontSize: FONT.action, lineHeight: 18, color: COLOR.white, width: 80, textAlign: 'center' },
  listHead: { position: 'absolute', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  sectionLine: { fontSize: FONT.sectionTitle, lineHeight: 26, letterSpacing: -0.2 },
  sectionSoft: { fontWeight: '400', fontSize: FONT.sectionLead, color: COLOR.inkSoft },
  sectionStrong: { fontWeight: '600', color: COLOR.ink },
  roundArrow: {
    width: LIST.arrowButton, height: LIST.arrowButton, borderRadius: LIST.arrowButton / 2, backgroundColor: COLOR.chip,
    position: 'absolute', alignItems: 'center', justifyContent: 'center',
  },
  row: {
    height: LIST.rowHeight, borderRadius: LIST.rowHeight / 2, backgroundColor: COLOR.row,
    flexDirection: 'row', alignItems: 'center', paddingLeft: LIST.iconPad, paddingRight: LIST.padRight,
  },
  rowIcon: {
    width: LIST.icon, height: LIST.icon, borderRadius: LIST.icon / 2, backgroundColor: COLOR.rowIcon,
    alignItems: 'center', justifyContent: 'center',
  },
  rowText: { flex: 1, marginLeft: LIST.nameGap },
  rowName: { fontFamily: FAMILY.medium, fontSize: FONT.rowName, lineHeight: 19, color: COLOR.ink },
  rowDate: { fontFamily: FAMILY.regular, fontSize: FONT.rowMeta, lineHeight: 17, color: COLOR.muted },
  rowAmount: { fontFamily: FAMILY.regular, fontSize: FONT.rowAmount, lineHeight: 14, color: COLOR.inkSoft, fontVariant: ['tabular-nums'] },
});
