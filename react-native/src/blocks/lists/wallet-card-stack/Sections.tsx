import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import { Pressable } from 'react-native-gesture-handler';
import Svg, { Circle } from 'react-native-svg';

import { EARLIER, LATEST as PAYMENTS, type Person, QUICK } from './data';
import { CardGlyph, ChartGlyph, HomeGlyph, MenuGlyph, PlusGlyph, PulseGlyph, ScanGlyph } from './glyphs';
import { COLOR, HEADER, LATEST, TABBAR, TOPUP } from './theme';

/** A round avatar; the art carries its own background colour. */
const AVATAR_ZOOM = 1; // the art is framed like the pin's avatars (generated against crops of them)

export function Avatar({ person, size }: { person: Person; size: number }) {
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, overflow: 'hidden', backgroundColor: person.tint }}>
      <Image source={person.photo} contentFit="cover"
        style={{ position: 'absolute', width: size * AVATAR_ZOOM, height: size * AVATAR_ZOOM, left: -size * (AVATAR_ZOOM - 1) / 2, top: -size * (AVATAR_ZOOM - 1) / 2 }} />
    </View>
  );
}

function Pressy({ children, label, style }: { children: React.ReactNode; label: string; style?: object }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label}
      pressRetentionOffset={16} hitSlop={6} style={({ pressed }) => [style, { transform: [{ scale: pressed ? 0.97 : 1 }] }]}>
      {children}
    </Pressable>
  );
}

export function Header({ width, oy }: { width: number; oy: number }) {
  const top = oy + HEADER.y - HEADER.button / 2;
  return (
    <>
      <Pressy label="Menu" style={[styles.round, { left: HEADER.inset, top }]}><MenuGlyph color={COLOR.ink} size={22} /></Pressy>
      <Pressy label="Add card" style={[styles.round, { left: width - HEADER.inset - HEADER.button, top }]}><PlusGlyph color={COLOR.ink} size={22} /></Pressy>
      <Text style={[styles.title, { top: oy + HEADER.titleY - 13, width }]} accessibilityRole="header">Wallet</Text>
      <Text style={[styles.subtitle, { top: oy + HEADER.subtitleY - 9, width }]}>My Cards & Transaction</Text>
    </>
  );
}

function SectionHead({ title, y, width }: { title: string; y: number; width: number }) {
  return (
    <View style={[styles.sectionHead, { top: y - 12, width: width - 40 }]}>
      <Text style={styles.sectionTitle} accessibilityRole="header">{title}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={`See more ${title}`} hitSlop={10}>
        <Text style={styles.seeMore}>See more</Text>
      </Pressable>
    </View>
  );
}

export function QuickTopUp({ width, oy }: { width: number; oy: number }) {
  const s = TOPUP.avatar;
  return (
    <>
      <SectionHead title="Quick Top-Up" y={oy + TOPUP.titleY} width={width} />
      <Pressy label="Add contact" style={[styles.person, { left: TOPUP.addX - 40, top: oy + TOPUP.rowY - s / 2 }]}>
        <View style={{ width: s, height: s, alignItems: 'center', justifyContent: 'center' }}>
          <Svg width={s} height={s} style={StyleSheet.absoluteFill}>
            <Circle cx={s / 2} cy={s / 2} r={s / 2 - 1} stroke={COLOR.accent} strokeWidth={1.4} strokeDasharray="3.2 3.2" fill="none" />
          </Svg>
          <PlusGlyph color={COLOR.accent} size={22} stroke={2.2} />
        </View>
        <Text style={styles.personName}>Add</Text>
      </Pressy>
      <View style={[styles.divider, { left: TOPUP.dividerX, top: oy + TOPUP.rowY - 26 }]} />
      {QUICK.map((p, i) => (
        <Pressy key={p.name} label={`Top up ${p.name}`} style={[styles.person, { left: TOPUP.xs[i] - 40, top: oy + TOPUP.rowY - s / 2 }]}>
          <Avatar person={p} size={s} />
          <Text style={styles.personName}>{p.name}</Text>
        </Pressy>
      ))}
    </>
  );
}

export function LatestTransactions({ width, oy }: { width: number; oy: number }) {
  return (
    <>
      <SectionHead title="Latest Transactions" y={oy + LATEST.titleY} width={width} />
      {[PAYMENTS, EARLIER].map((list, row) => list.map((t, i) => (
        <Pressy key={`${row}${i}`} label={`${t.person.name}, ${t.date.replace(',', ', ')}, ${t.amount}`}
          style={[styles.tile, { left: LATEST.xs[i], top: oy + LATEST.top + row * (LATEST.height + 15) }]}>
          <Avatar person={t.person} size={LATEST.avatar} />
          <Text style={styles.tileName}>{t.person.name}</Text>
          <Text style={styles.tileDate}>{t.date}</Text>
          <Text style={[styles.tileAmount, { color: t.incoming ? COLOR.green : COLOR.red }]}>{t.amount}</Text>
        </Pressy>
      )))}
    </>
  );
}

const TABS = [
  { label: 'Home', Glyph: HomeGlyph, active: true },
  { label: 'Statistic', Glyph: ChartGlyph, active: false },
  { label: 'Activity', Glyph: PulseGlyph, active: false },
  { label: 'Activity', Glyph: CardGlyph, active: false }, // the pin labels both tabs "Activity"
];

export function TabBar({ width, height, oy }: { width: number; height: number; oy: number }) {
  const top = oy + TABBAR.top;
  return (
    <View style={[styles.tabBar, { top, height: height - top, width }]}>
      {TABS.map(({ label, Glyph, active }, i) => (
        <Pressable key={i} accessibilityRole="tab" accessibilityLabel={label} accessibilityState={{ selected: active }}
          style={({ pressed }) => [styles.tab, { left: TABBAR.xs[i] - 40, opacity: pressed ? 0.6 : 1 }]}>
          <Glyph color={active ? COLOR.ink : COLOR.muted} size={26} />
          <Text style={[styles.tabLabel, { color: active ? COLOR.ink : COLOR.muted, fontWeight: active ? '700' : '600' }]}>{label}</Text>
        </Pressable>
      ))}
      <Pressable accessibilityRole="button" accessibilityLabel="Scan"
        style={({ pressed }) => [styles.fabSlot, { left: width / 2 - TABBAR.fab / 2, top: TABBAR.fabY - TABBAR.top - TABBAR.fab / 2, transform: [{ scale: pressed ? 0.97 : 1 }] }]}>
        <View style={styles.fab}>
          <LinearGradient colors={[COLOR.fabTop, COLOR.fabBottom]} style={StyleSheet.absoluteFill} />
          <ScanGlyph color={COLOR.white} size={30} />
        </View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  round: {
    position: 'absolute', width: HEADER.button, height: HEADER.button, borderRadius: HEADER.button / 2,
    backgroundColor: COLOR.button, alignItems: 'center', justifyContent: 'center',
    boxShadow: '0 10px 18px rgba(0, 0, 0, 0.07)',
  },
  title: { position: 'absolute', left: 0, textAlign: 'center', fontSize: 19, lineHeight: 26, fontWeight: '700', color: COLOR.ink },
  subtitle: { position: 'absolute', left: 0, textAlign: 'center', fontSize: 12.1, lineHeight: 18, fontWeight: '600', color: COLOR.inkSoft },
  sectionHead: { position: 'absolute', left: 20, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sectionTitle: { fontSize: 14.5, lineHeight: 22, fontWeight: '700', color: COLOR.ink },
  seeMore: { fontSize: 12.4, lineHeight: 20, fontWeight: '500', color: COLOR.inkSoft },
  person: { position: 'absolute', width: 80, alignItems: 'center' },
  personName: { marginTop: 5.5, fontSize: 13.5, lineHeight: 18, fontWeight: '600', color: COLOR.ink },
  divider: { position: 'absolute', width: 1.5, height: 52, borderRadius: 1, backgroundColor: COLOR.divider },
  tile: {
    position: 'absolute', width: LATEST.width, height: LATEST.height, borderRadius: LATEST.radius,
    backgroundColor: COLOR.tile, paddingLeft: 10, paddingTop: 14,
  },
  tileName: { marginTop: 9, fontSize: 15.5, lineHeight: 20, fontWeight: '700', color: COLOR.ink },
  tileDate: { marginTop: 1, fontSize: 10.4, lineHeight: 15, fontWeight: '500', color: COLOR.inkSoft },
  tileAmount: { marginTop: 7, fontSize: 11.5, lineHeight: 17, fontWeight: '700', fontVariant: ['tabular-nums'] },
  tabBar: { position: 'absolute', left: 0, backgroundColor: COLOR.white },
  fabSlot: { position: 'absolute', width: TABBAR.fab, height: TABBAR.fab },
  tab: { position: 'absolute', top: TABBAR.iconY - TABBAR.top - 13, width: 80, alignItems: 'center' },
  tabLabel: { marginTop: 2, fontSize: 12, lineHeight: 16 },
  fab: {
    width: TABBAR.fab, height: TABBAR.fab, borderRadius: TABBAR.fab / 2, overflow: 'hidden',
    alignItems: 'center', justifyContent: 'center', boxShadow: '0 8px 20px rgba(92, 75, 227, 0.28)',
  },
});
