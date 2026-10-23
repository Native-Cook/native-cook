/**
 * The account card: the one dark object on the page, ink with a brass hairline frame and woven cheque lines
 * (assets/guilloche@3x.png, drawn once from the mock's 44 interleaved sine lines). The balance is set like a cheque:
 * a raised brass dollar sign and ivory cents, the two ones hand-kerned.
 */
import { LinearGradient } from 'expo-linear-gradient';
import { Image, StyleSheet, Text, View } from 'react-native';

import { COLOR, DIDONE_DROP, DIDONE_ROOM, LAYOUT, SHADOW, TYPE } from './theme';
import { BALANCE, CARD_NUMBER, money } from './transactions';

const GUILLOCHE = require('./assets/guilloche.png');

export function BalanceCard({ width }: { width: number }) {
  const figure = money(BALANCE);
  return (
    <View style={[styles.card, { width }]} accessible accessibilityLabel={`Private account ending ${CARD_NUMBER}, ${figure.whole}${figure.cents} available today`}>
      <View style={styles.clip}>
        {/* 160°: the light falls from the top left, as on the mock's card. */}
        <LinearGradient colors={COLOR.card} locations={COLOR.cardStops} start={{ x: 0.386, y: -0.406 }} end={{ x: 0.614, y: 1.406 }} style={StyleSheet.absoluteFill} />
        <Image source={GUILLOCHE} style={styles.guilloche} />
        <View style={styles.frame} />
      </View>
      <Text style={styles.label}>PRIVATE ACCOUNT</Text>
      <Text style={styles.number}>{`···· ${CARD_NUMBER}`}</Text>
      <View style={styles.balance}>
        <Text style={styles.sign}>$</Text>
        <Text style={[styles.figure, styles.k1]}>1</Text>
        <Text style={styles.figure}>,</Text>
        <Text style={[styles.figure, styles.k2]}>1</Text>
        <Text style={styles.figure}>48</Text>
        <Text style={styles.cents}>{figure.cents}</Text>
      </View>
      <Text style={styles.note}>Available today</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { height: LAYOUT.cardHeight, borderRadius: LAYOUT.cardRadius, boxShadow: SHADOW.card },
  clip: { ...StyleSheet.absoluteFill, borderRadius: LAYOUT.cardRadius, overflow: 'hidden' },
  guilloche: { position: 'absolute', left: 0, top: 0, width: 370, height: 128 },
  frame: {
    position: 'absolute', left: LAYOUT.cardFrameInset, top: LAYOUT.cardFrameInset, right: LAYOUT.cardFrameInset, bottom: LAYOUT.cardFrameInset,
    borderRadius: LAYOUT.cardFrameRadius, borderWidth: 0.75, borderColor: COLOR.cardFrame,
  },
  label: { ...TYPE.cardLabel, color: COLOR.brass, position: 'absolute', left: LAYOUT.cardPad, top: 22 },
  number: { ...TYPE.cardNumber, color: COLOR.ivory55, position: 'absolute', right: LAYOUT.cardPad, top: 22 },
  balance: { position: 'absolute', left: 22, top: 48 + DIDONE_DROP.money22, flexDirection: 'row', alignItems: 'flex-start' },
  sign: { ...TYPE.balanceSign, color: COLOR.brass, marginTop: 4, marginRight: 3 },
  figure: { ...TYPE.balance, color: COLOR.ivory, marginTop: DIDONE_DROP.display - DIDONE_DROP.money22 - DIDONE_ROOM.display },
  k1: { marginRight: -2 },
  k2: { marginLeft: -1, marginRight: -4 },
  cents: { ...TYPE.balanceCents, color: COLOR.ivory, marginTop: 4, marginLeft: 2 },
  note: { ...TYPE.cardNote, color: COLOR.ivory50, position: 'absolute', left: LAYOUT.cardPad, top: 100 },
});
