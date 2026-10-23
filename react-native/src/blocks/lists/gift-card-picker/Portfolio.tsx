import { BlurView } from 'expo-blur';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';

import { AnalyzeGlyph, BackGlyph, ChatGlyph, DepositGlyph, HomeGlyph, InvestGlyph, PayGlyph, TransferGlyph } from './Icons';
import { PRESS } from './motion';
import { COLORS, PORTFOLIO as P, TOUCH } from './theme';

// The screen the gift picker opens from: the pin's first and last frames.
type Props = {
  width: number;
  dy: number; // safe-area difference from the pin's 54 pt status bar
  bottom: number; // safe-area difference at the bottom (0 on the pin's phone)
  press: SharedValue<number>;
  landing: SharedValue<number>;
  onGiftPressIn: () => void;
  onGiftPressOut: () => void;
  onGift: () => void;
};

const NBSP = ' ';

export function Portfolio({ width, dy, bottom, press, landing, onGiftPressIn, onGiftPressOut, onGift }: Props) {
  const inner = width - P.side * 2;
  const tileW = (inner - P.tileGap * 2) / 3;
  const giftStyle = useAnimatedStyle(() => {
    const p = Math.min(1, Math.max(0, (landing.get() - 2120 / 2540) / (420 / 2540)));
    return {
      transform: [{ scale: (1 - (1 - PRESS.scale) * press.get()) * (1 + Math.sin(p * Math.PI) * 0.03) }],
      borderWidth: p > 0 && p < 1 ? 3 : 0,
      borderColor: `rgba(252,77,0,${Math.sin(p * Math.PI) * 0.8})`,
    };
  });

  return (
    <View style={[StyleSheet.absoluteFill, { backgroundColor: COLORS.page }]}>
      <View style={[styles.back, { left: P.back.x - P.back.size / 2, top: P.back.y - P.back.size / 2 + dy }]} accessibilityRole="button" accessibilityLabel="Back">
        <BackGlyph />
      </View>

      <Text style={[styles.label, { top: P.labelY - 11 + dy }]}>Portfolio</Text>
      <Text style={[styles.total, { top: P.totalY - 29 + dy }]} accessibilityRole="header">
        USD 8,000.00
      </Text>
      <Text style={[styles.change, { top: P.changeY - 11 + dy }]}>
        {`USD${NBSP}0.00 · 0.00% · `}
        <Text style={styles.changeInk}>All time</Text>
      </Text>

      <View style={[styles.tiles, { top: P.tilesTop + dy, left: P.side, width: inner }]}>
        {[
          { label: 'Deposit', icon: <DepositGlyph /> },
          { label: 'Analyze', icon: <AnalyzeGlyph /> },
          { label: 'Transfer', icon: <TransferGlyph /> },
        ].map((t) => (
          <View key={t.label} style={[styles.tile, { width: tileW }]} accessibilityRole="button" accessibilityLabel={t.label}>
            {t.icon}
            <Text style={styles.tileLabel}>{t.label}</Text>
          </View>
        ))}
      </View>

      <View style={[styles.card, { top: P.cashTop + dy, left: P.side, width: inner, height: P.cashH }]}>
        <Text style={styles.cardTitle}>Cash</Text>
        <Text style={styles.cardSub}>{`USD${NBSP}8,000.00`}</Text>
        <View style={styles.interest} accessibilityRole="button">
          <Text style={styles.interestLabel}>Get 3% annual interest for free</Text>
        </View>
      </View>

      <Animated.View style={[styles.giftWrap, { top: P.giftTop + dy, left: P.side, width: inner, height: P.giftH }, giftStyle]}>
        <Pressable pressRetentionOffset={TOUCH.retention}
          style={[StyleSheet.absoluteFill, styles.giftClip]}
          onPressIn={onGiftPressIn}
          onPressOut={onGiftPressOut}
          onPress={onGift}
          accessibilityRole="button"
          accessibilityLabel="Choose your gift stock"
        >
          <LinearGradient
            colors={[COLORS.tile, COLORS.tile, '#ffd2b4', '#ff9a5e']}
            locations={[0, 0.34, 0.62, 1]}
            start={{ x: 0, y: 0.5 }}
            end={{ x: 1, y: 0.5 }}
            style={[StyleSheet.absoluteFill, styles.giftBg]}
          />
          <Image source={require('./assets/tile-art.jpg')} style={styles.giftArt} contentFit="fill" />
          <Text style={styles.giftTitle}>{'Choose your\ngift stock'}</Text>
        </Pressable>
      </Animated.View>

      <View style={[styles.card, { top: P.txTop + dy, left: P.side, width: inner, height: P.txH, justifyContent: 'center' }]}>
        <Text style={[styles.cardTitle, { marginTop: 0 }]}>Transactions</Text>
        <Text style={styles.cardSub}>4 in June</Text>
      </View>

      {/* the next card, mostly under the tab bar as in the pin */}
      <View style={[styles.card, { top: P.promoTop + dy, left: P.side, width: inner, height: 160 }]}>
        <Text style={[styles.cardTitle, styles.promo]}>How to grow your gift</Text>
      </View>
      {/* the scroll edge under the tab bar: what scrolls beneath it is frosted, as in the pin */}
      <BlurView intensity={P.edgeBlur} tint="light" style={[styles.edge, { top: P.edgeTop + dy }]} />

      <View style={[styles.tabRow, { bottom: 874 - P.tabTop - P.tabH + bottom, left: P.side, width: inner }]}>
        <View style={[styles.tabBar, { width: P.tabW }]}>
          <BlurView intensity={40} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, styles.tabTint]} />
          <Tab label="Home" icon={<HomeGlyph />} badge />
          <Tab label="Pay" icon={<PayGlyph />} />
          <Tab label="Invest" icon={<InvestGlyph />} active />
          <Tab label="Invite" icon={<Text style={styles.invite}>$100</Text>} />
        </View>
        <View style={styles.chat} accessibilityRole="button" accessibilityLabel="Chat">
          <BlurView intensity={40} tint="light" style={StyleSheet.absoluteFill} />
          <View style={[StyleSheet.absoluteFill, styles.tabTint]} />
          <ChatGlyph />
        </View>
      </View>
    </View>
  );
}

function Tab({ label, icon, active, badge }: { label: string; icon: ReactNode; active?: boolean; badge?: boolean }) {
  return (
    <View style={[styles.tab, active && styles.tabActive]} accessibilityRole="tab" accessibilityLabel={label} accessibilityState={{ selected: !!active }}>
      <View>
        {icon}
        {badge ? (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>1</Text>
          </View>
        ) : null}
      </View>
      <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{label}</Text>
    </View>
  );
}

const shadow = {
  shadowColor: '#000000',
  shadowOpacity: 0.05,
  shadowRadius: 12,
  shadowOffset: { width: 0, height: 4 },
};

const styles = StyleSheet.create({
  back: {
    position: 'absolute',
    width: P.back.size,
    height: P.back.size,
    borderRadius: P.back.size / 2,
    backgroundColor: COLORS.white,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadow,
  },
  label: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 17, lineHeight: 22, color: COLORS.ink },
  total: {
    position: 'absolute',
    left: 0,
    right: 0,
    textAlign: 'center',
    fontSize: P.totalSize,
    lineHeight: 58,
    fontWeight: '700',
    letterSpacing: 0,
    fontVariant: ['tabular-nums'],
    color: COLORS.ink,
  },
  change: { position: 'absolute', left: 0, right: 0, textAlign: 'center', fontSize: 17, lineHeight: 22, fontVariant: ['tabular-nums'], color: COLORS.mute },
  changeInk: { color: COLORS.ink },
  tiles: { position: 'absolute', height: P.tileH, flexDirection: 'row', justifyContent: 'space-between' },
  tile: {
    height: P.tileH,
    borderRadius: P.radius,
    borderCurve: 'continuous',
    backgroundColor: COLORS.tile,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    ...shadow,
  },
  tileLabel: { fontSize: 13, fontWeight: '600', color: COLORS.ink },
  card: {
    position: 'absolute',
    borderRadius: P.radius,
    borderCurve: 'continuous',
    backgroundColor: COLORS.tile,
    paddingHorizontal: 20,
    ...shadow,
  },
  cardTitle: { marginTop: 20, fontSize: 20, lineHeight: 25, fontWeight: '700', color: COLORS.ink },
  cardSub: { marginTop: 2, fontSize: 15, lineHeight: 22, fontVariant: ['tabular-nums'], color: COLORS.ink },
  interest: {
    marginTop: 16,
    height: 47,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: COLORS.orange,
    alignItems: 'center',
    justifyContent: 'center',
  },
  interestLabel: { fontSize: 15, fontWeight: '600', color: COLORS.white },
  giftWrap: { position: 'absolute', borderRadius: P.radius, borderCurve: 'continuous', ...shadow },
  giftBg: { borderRadius: P.radius, borderCurve: 'continuous' },
  giftClip: { borderRadius: P.radius, borderCurve: 'continuous', overflow: 'hidden' },
  // the art covers the tile's right end exactly as the pin frames it (x 240–382, full height)
  giftArt: { position: 'absolute', right: 0, top: 0, width: P.artW, height: P.giftH },
  giftTitle: { position: 'absolute', left: 20, top: 19, fontSize: 20, lineHeight: 24, fontWeight: '700', color: COLORS.ink2 },
  promo: { fontSize: 30, lineHeight: 36, fontWeight: '800', color: '#3a3a3e', marginTop: 18 },
  tabRow: { position: 'absolute', height: P.tabH, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  tabBar: {
    height: P.tabH,
    borderRadius: P.tabH / 2,
    overflow: 'hidden',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 4,
  },
  tabTint: { backgroundColor: 'rgba(255, 255, 255, 0.72)' },
  tab: { flex: 1, height: P.tabH - 8, borderRadius: (P.tabH - 8) / 2, alignItems: 'center', justifyContent: 'center', gap: 5 },
  tabActive: { backgroundColor: 'rgba(118, 118, 128, 0.06)' },
  edge: { position: 'absolute', left: 0, right: 0, bottom: 0 },
  tabLabel: { fontSize: 10, fontWeight: '500', color: '#6d6d72' },
  tabLabelActive: { color: COLORS.orange, fontWeight: '600' },
  invite: { fontSize: 17, lineHeight: 26, fontWeight: '600', color: '#6d6d72' },
  badge: {
    position: 'absolute',
    right: -8,
    top: -5,
    minWidth: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: COLORS.badge,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { fontSize: 11, fontWeight: '700', color: COLORS.white },
  chat: {
    width: P.chat,
    height: P.chat,
    borderRadius: P.chat / 2,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
});
