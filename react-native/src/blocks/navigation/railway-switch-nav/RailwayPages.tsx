import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';

/*
 The four section pages of Northline (static content; the parent slides them in and out). Each is
 a full section sized to the page area: a main card, and a second block when there is room
 (`room` = the page area's height). Copy and names are invented.
 */

export const INK = '#EEF1EA';
export const MUTED = 'rgba(238, 241, 234, 0.58)';
export const GOLD = '#F2C14E';
const GOOD = '#7BE39A';
const WARN = '#F2A65A';
/** Below this page height the second block is left out. */
const ROOM_FOR_MORE = 290;

type PageProps = { room: number };

function Card({ children, style }: { children: React.ReactNode; style?: object }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

function Heading({ kicker, title }: { kicker: string; title: string }) {
  return (
    <View style={styles.heading}>
      <Text style={styles.kicker}>{kicker}</Text>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>
    </View>
  );
}

function HomePage({ room }: PageProps) {
  const lines = [
    ['Harbour line', 'Good service', GOOD],
    ['Millbank loop', 'Minor delays, 4 min', WARN],
    ['Fenwick branch', 'Good service', GOOD],
  ];
  return (
    <View style={styles.page}>
      <Heading kicker="GOOD MORNING, MAREN" title="Your 08:14 is on time" />
      <Card>
        <View style={styles.rowBetween}>
          <Text style={styles.big}>08:14</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>Platform 3</Text>
          </View>
        </View>
        <Text style={styles.line}>Elm Cross to Harbour Row</Text>
        <Text style={styles.sub}>22 min · 4 stops · leave home in 6 min</Text>
        <View style={styles.progress}>
          <View style={[styles.progressFill, { width: '62%' }]} />
        </View>
      </Card>
      {room >= ROOM_FOR_MORE ? (
        <Card style={styles.compact}>
          {lines.map((l) => (
            <View key={l[0]} style={styles.statusRow}>
              <View style={[styles.statusDot, { backgroundColor: l[2] }]} />
              <Text style={styles.statusName}>{l[0]}</Text>
              <Text style={styles.statusText}>{l[1]}</Text>
            </View>
          ))}
        </Card>
      ) : null}
    </View>
  );
}

function TripsPage({ room }: PageProps) {
  const rows = [
    ['Today', '08:14', 'Elm Cross → Harbour Row', '22 min'],
    ['Fri', '17:42', 'Harbour Row → Millbank', '35 min'],
    ['Sun', '10:05', 'Elm Cross → Fenwick Bay', '1 h 12'],
  ];
  return (
    <View style={styles.page}>
      <Heading kicker="TRIPS" title="Three trips this week" />
      <Card style={styles.compact}>
        {rows.map((r, i) => (
          <View key={r[2]} style={[styles.tripRow, i > 0 && styles.rule]}>
            <View style={styles.tripWhen}>
              <Text style={styles.tripDay}>{r[0]}</Text>
              <Text style={styles.tripTime}>{r[1]}</Text>
            </View>
            <Text style={styles.tripName} numberOfLines={1}>
              {r[2]}
            </Text>
            <Text style={styles.tripLen}>{r[3]}</Text>
          </View>
        ))}
      </Card>
      {room >= ROOM_FOR_MORE ? (
        <View style={styles.mapThumb}>
          <Svg width="100%" height="100%" viewBox="0 0 320 64" preserveAspectRatio="xMidYMid slice">
            <Path d="M0 50 C60 50 70 22 120 22 S190 44 240 30 S300 12 320 14" stroke="rgba(232,224,200,0.18)" strokeWidth={6} fill="none" />
            <Path d="M24 48 C60 48 76 22 120 22 S190 44 236 31" stroke={GOLD} strokeWidth={3} strokeLinecap="round" fill="none" />
            <Circle cx={24} cy={48} r={5} fill="#1A2420" stroke={GOLD} strokeWidth={2} />
            <Circle cx={120} cy={22} r={3.5} fill={GOLD} />
            <Circle cx={180} cy={36} r={3.5} fill={GOLD} />
            <Circle cx={236} cy={31} r={5} fill={GOLD} />
          </Svg>
          <View style={styles.mapLabels}>
            <Text style={styles.mapLabel}>Elm Cross</Text>
            <Text style={styles.mapLabel}>Harbour Row · next trip</Text>
          </View>
        </View>
      ) : null}
    </View>
  );
}

const BARS = [3, 1, 2, 1, 1, 3, 2, 1, 3, 1, 2, 2, 1, 3, 1, 1, 2, 3, 1, 2, 1, 3, 2, 1, 1, 2, 3, 1, 2, 1, 3, 1, 2, 1, 1, 3];

function TicketsPage({ room }: PageProps) {
  return (
    <View style={styles.page}>
      <Heading kicker="TICKETS" title="Monthly pass" />
      <View style={styles.ticket}>
        <View style={styles.rowBetween}>
          <Text style={styles.ticketZone}>Zones 1 – 3</Text>
          <Text style={styles.ticketPrice}>£142.50</Text>
        </View>
        <Text style={styles.ticketSub}>Peak and off-peak · Maren Holt</Text>
        <View style={styles.barcode}>
          {BARS.map((b, i) => (
            <View
              key={i}
              style={{
                width: b,
                height: 34,
                marginRight: 2,
                backgroundColor: '#1A2420',
                borderRadius: 0.5,
              }}
            />
          ))}
        </View>
        <Text style={styles.ticketCode}>NL · 4471 2093 8810</Text>
        <View style={styles.validity}>
          <View style={[styles.validityFill, { width: '40%' }]} />
        </View>
        <View style={styles.rowBetween}>
          <Text style={styles.ticketSub}>Valid 1 – 30 Nov</Text>
          <Text style={styles.ticketDays}>18 days left</Text>
        </View>
      </View>
      {room >= ROOM_FOR_MORE ? (
        <View style={styles.chips}>
          <View style={styles.chip}>
            <View style={[styles.statusDot, { backgroundColor: GOOD }]} />
            <Text style={styles.chipText}>Auto-renews 1 Dec</Text>
          </View>
          <View style={styles.chip}>
            <Text style={styles.chipText}>Saved £38 vs daily fares</Text>
          </View>
        </View>
      ) : null}
    </View>
  );
}

function AccountPage({ room }: PageProps) {
  const rows = [
    ['Card', 'ending 4421'],
    ['Commute alerts', 'On, 07:45'],
    ['Home station', 'Elm Cross'],
  ];
  return (
    <View style={styles.page}>
      <Heading kicker="ACCOUNT" title="Maren Holt" />
      {room >= ROOM_FOR_MORE ? (
        <View style={styles.profile}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>MH</Text>
          </View>
          <View style={styles.profileText}>
            <Text style={styles.line}>Member since 2021</Text>
            <Text style={styles.sub}>312 trips · 4,860 km this year</Text>
          </View>
        </View>
      ) : null}
      <Card style={styles.compact}>
        {rows.map((r, i) => (
          <View key={r[0]} style={[styles.accRow, i > 0 && styles.rule]}>
            <Text style={styles.accLabel}>{r[0]}</Text>
            <Text style={styles.accValue}>{r[1]}</Text>
          </View>
        ))}
      </Card>
      {room >= ROOM_FOR_MORE ? <Text style={styles.note}>Signed in on this phone · Sign out</Text> : null}
    </View>
  );
}

export const PAGES = [HomePage, TripsPage, TicketsPage, AccountPage];

const styles = StyleSheet.create({
  page: { gap: 12 },
  heading: { gap: 6 },
  kicker: { color: GOLD, fontSize: 12, fontWeight: '700', letterSpacing: 2 },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.5 },
  card: {
    padding: 16,
    gap: 6,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(236, 244, 236, 0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(236, 244, 236, 0.16)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 12px 30px rgba(0,0,0,0.25)',
  },
  compact: { paddingVertical: 10 },
  rowBetween: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  big: {
    color: INK,
    fontSize: 34,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
    letterSpacing: -0.8,
  },
  badge: {
    height: 26,
    paddingHorizontal: 10,
    borderRadius: 13,
    justifyContent: 'center',
    backgroundColor: 'rgba(242, 193, 78, 0.16)',
  },
  badgeText: { color: GOLD, fontSize: 12, fontWeight: '700' },
  line: { color: INK, fontSize: 16, fontWeight: '600' },
  sub: { color: MUTED, fontSize: 13, fontVariant: ['tabular-nums'] },
  progress: {
    height: 4,
    borderRadius: 2,
    backgroundColor: 'rgba(236, 244, 236, 0.12)',
    marginTop: 6,
    overflow: 'hidden',
  },
  progressFill: { height: 4, borderRadius: 2, backgroundColor: GOLD },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    height: 22,
  },
  statusDot: { width: 8, height: 8, borderRadius: 4 },
  statusName: { flex: 1, color: INK, fontSize: 14, fontWeight: '500' },
  statusText: { color: MUTED, fontSize: 13 },
  tripRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 7,
  },
  rule: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(236, 244, 236, 0.14)',
  },
  tripWhen: { width: 48 },
  tripDay: { color: MUTED, fontSize: 11, fontWeight: '600' },
  tripTime: {
    color: INK,
    fontSize: 16,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  tripName: { flex: 1, color: INK, fontSize: 14, fontWeight: '500' },
  tripLen: { color: MUTED, fontSize: 13, fontVariant: ['tabular-nums'] },
  mapThumb: {
    height: 72,
    borderRadius: 18,
    borderCurve: 'continuous',
    overflow: 'hidden',
    backgroundColor: '#1D2B27',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(236, 244, 236, 0.16)',
  },
  mapLabels: {
    position: 'absolute',
    left: 14,
    right: 14,
    bottom: 6,
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  mapLabel: { color: MUTED, fontSize: 10, fontWeight: '600' },
  ticket: {
    padding: 16,
    gap: 6,
    borderRadius: 20,
    borderCurve: 'continuous',
    backgroundColor: '#EFE6CC',
    boxShadow: '0 1px 2px rgba(0,0,0,0.35), 0 14px 30px rgba(0,0,0,0.3)',
  },
  ticketZone: { color: '#1A2420', fontSize: 20, fontWeight: '700' },
  ticketPrice: {
    color: '#1A2420',
    fontSize: 16,
    fontWeight: '600',
    fontVariant: ['tabular-nums'],
  },
  ticketSub: {
    color: 'rgba(26, 36, 32, 0.62)',
    fontSize: 12,
    fontVariant: ['tabular-nums'],
  },
  ticketDays: {
    color: '#1A2420',
    fontSize: 12,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  barcode: { flexDirection: 'row', alignItems: 'flex-end', marginTop: 6 },
  ticketCode: {
    color: 'rgba(26, 36, 32, 0.7)',
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 1.2,
    fontVariant: ['tabular-nums'],
  },
  validity: {
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(26, 36, 32, 0.14)',
    marginTop: 4,
    overflow: 'hidden',
  },
  validityFill: { height: 6, borderRadius: 3, backgroundColor: '#2E6B4E' },
  chips: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 30,
    paddingHorizontal: 12,
    borderRadius: 15,
    backgroundColor: 'rgba(236, 244, 236, 0.06)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(236, 244, 236, 0.16)',
  },
  chipText: { color: INK, fontSize: 12, fontWeight: '600' },
  profile: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: GOLD,
  },
  avatarText: { color: '#1A2420', fontSize: 17, fontWeight: '800' },
  profileText: { gap: 2 },
  accRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 9,
  },
  accLabel: { color: MUTED, fontSize: 14 },
  accValue: { color: INK, fontSize: 14, fontWeight: '600' },
  note: { color: MUTED, fontSize: 13, textAlign: 'center' },
});
