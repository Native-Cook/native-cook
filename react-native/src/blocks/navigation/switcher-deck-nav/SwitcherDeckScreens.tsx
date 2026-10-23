import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Circle, G, Path, Rect, Text as SvgText } from 'react-native-svg';

/**
 * The four pages of "Slow Departures", a photo journal of trains and window seats: the journal
 * feed, one entry, the route drawn as a map and the writer's profile. They are still pictures of
 * an app, drawn at the full size of the screen; the switcher shrinks and turns them.
 */

const SERIF = process.env.EXPO_OS === 'ios' ? 'Iowan Old Style' : 'serif';
const SANS = process.env.EXPO_OS === 'ios' ? 'Avenir Next' : 'sans-serif';

/** An Unsplash photo, cropped to `width` points at 3x. */
export function photo(id: string, width: number) {
  return `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${Math.round(width * 3)}&q=80`;
}

// Train station photos (s) and airplane window photos (w), each used once.
const S = {
  hero: 'photo-1553184257-604db3e574a8',
  tall: 'photo-1532105956626-9569c03602f6',
  dusk: 'photo-1693572322416-9ea4b36482ce',
  stop: 'photo-1527839321757-ad3a2f2be351',
  arch: 'photo-1495313196544-7d1adf4e628f',
  hall: 'photo-1604212399401-5fd807a65e18',
  rails: 'photo-1521075821520-ae40128c1ea0',
};
const W = {
  hero: 'photo-1543797414-a0c3ad076f7c',
  a: 'photo-1488085061387-422e29b40080',
  b: 'photo-1545132147-d037e6c54cfd',
  c: 'photo-1603277103691-756354934c2d',
  d: 'photo-1507812984078-917a274065be',
  e: 'photo-1591536813111-b6dccb6f67e0',
  f: 'photo-1542640662-fd5f54cc23b8',
};

// The handle pill sits HANDLE_BOTTOM pt above the bottom safe area; its centre is what the demo's
// virtual finger pulls (see HANDLE_Y in SwitcherDeckNav.tsx).
export const HANDLE_BOTTOM = 12;
export const HANDLE_HEIGHT = 5;

export type PageProps = { width: number; height: number; top: number; bottom: number };

export type PageInfo = {
  title: string;
  /** Background of the page, also used for the switcher's chip icon. */
  tint: string;
  ink: string;
  Page: (props: PageProps) => React.JSX.Element;
};

/** The small pill at the bottom of every page that the switcher is pulled up from. */
function Handle({ bottom, ink }: { bottom: number; ink: string }) {
  return (
    <View pointerEvents="none" style={[styles.handleWrap, { bottom: bottom + HANDLE_BOTTOM }]}>
      <View style={[styles.handle, { backgroundColor: ink }]} />
    </View>
  );
}

function JournalPage({ width, top, bottom }: PageProps) {
  const ink = '#1d1a16';
  const inner = width - 40;
  const half = (inner - 12) / 2;
  return (
    <View style={[styles.page, { backgroundColor: '#f2ede4', paddingTop: top + 10, paddingHorizontal: 20 }]}>
      <View style={styles.row}>
        <Text style={[styles.kicker, { color: '#a0502f' }]}>WEEK 41 · THE AUTUMN LINE</Text>
        <View style={styles.monogram}>
          <Text style={styles.monogramText}>IM</Text>
        </View>
      </View>
      <Text style={[styles.journalTitle, { color: ink }]}>Slow Departures</Text>
      <Text style={styles.journalLead}>Notes from platforms and window seats</Text>

      <Image source={photo(S.hero, inner)} style={[styles.heroImage, { width: inner, height: inner * 0.56 }]} contentFit="cover" transition={250} />
      <Text style={[styles.meta, { color: '#a0502f' }]}>06:12 · LISBOURNE CENTRAL</Text>
      <Text style={[styles.entryTitle, { color: ink }]}>The first train hums before the light does</Text>
      <Text style={styles.entryBody} numberOfLines={2}>
        Steam off the coffee cart, a conductor counting under his breath, and the departures board flipping to
        green one line at a time.
      </Text>

      <View style={[styles.row, { marginTop: 22, gap: 12, alignItems: 'flex-start' }]}>
        <View style={{ width: half, gap: 8 }}>
          <Image source={photo(S.tall, half)} style={[styles.tile, { width: half, height: half }]} contentFit="cover" transition={250} />
          <Text style={[styles.tileTitle, { color: ink }]}>Calder Bay</Text>
          <Text style={styles.tileMeta}>8 Oct · 4 photos</Text>
        </View>
        <View style={{ width: half, gap: 8 }}>
          <Image source={photo(S.dusk, half)} style={[styles.tile, { width: half, height: half }]} contentFit="cover" transition={250} />
          <Text style={[styles.tileTitle, { color: ink }]}>Platform 9, dusk</Text>
          <Text style={styles.tileMeta}>6 Oct · 7 photos</Text>
        </View>
      </View>
      <LinearGradient colors={['rgba(242,237,228,0)', '#f2ede4']} style={styles.fade} pointerEvents="none" />
      <Handle bottom={bottom} ink={ink} />
    </View>
  );
}

function EntryPage({ width, height, top, bottom }: PageProps) {
  const ink = '#f1ece3';
  const heroH = height * 0.54;
  const thumb = (width - 40 - 16) / 3;
  return (
    <View style={[styles.page, { backgroundColor: '#12110f' }]}>
      <Image source={photo(W.hero, width)} style={{ width, height: heroH }} contentFit="cover" transition={250} />
      <LinearGradient
        colors={['rgba(18,17,15,0.55)', 'rgba(18,17,15,0)', 'rgba(18,17,15,0)', '#12110f']}
        locations={[0, 0.22, 0.55, 1]}
        style={[StyleSheet.absoluteFill, { height: heroH }]}
      />
      <View style={[styles.row, styles.entryBar, { top: top + 6 }]}>
        <View style={styles.glassButton}>
          <Svg width={12} height={18} viewBox="0 0 12 18">
            <Path d="M9 2 L3 9 L9 16" stroke={ink} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </Svg>
        </View>
        <View style={styles.seatChip}>
          <Text style={styles.seatText}>SEAT 34A</Text>
        </View>
      </View>
      <View style={{ marginTop: -118, paddingHorizontal: 20, gap: 8 }}>
        <Text style={[styles.meta, { color: '#e3a27d', marginTop: 0 }]}>ENTRY 17 · 9 OCTOBER</Text>
        <Text style={[styles.entryHeadline, { color: ink }]}>Window seat, over the Vessmark coast</Text>
      </View>
      <View style={[styles.row, styles.statRow]}>
        {[
          ['Altitude', '11,280 m'],
          ['Local time', '07:40'],
          ['Flight', '2 h 15'],
        ].map(([label, value]) => (
          <View key={label} style={{ gap: 3 }}>
            <Text style={styles.statLabel}>{label}</Text>
            <Text style={[styles.statValue, { color: ink }]}>{value}</Text>
          </View>
        ))}
      </View>
      <Text style={[styles.entryBody, { color: '#bdb5a8', marginHorizontal: 20, marginTop: 18, fontSize: 16, lineHeight: 24 }]}>
        The wing caught the sun before the cabin did. Below, the fjords were still ink, and someone three rows
        back had already fallen asleep against the glass.
      </Text>
      <View style={[styles.row, { paddingHorizontal: 20, marginTop: 20, gap: 8 }]}>
        {[W.a, W.b, W.c].map((id) => (
          <Image key={id} source={photo(id, thumb)} style={[styles.tile, { width: thumb, height: thumb }]} contentFit="cover" transition={250} />
        ))}
      </View>
      <Handle bottom={bottom} ink={ink} />
    </View>
  );
}

// The drawn map: coastlines, the rail line, a flight arc and the stops, in a 400 × 520 box.
const LAND = [
  'M -10 40 C 60 30 120 70 150 120 C 175 165 140 210 170 250 C 200 290 260 270 300 300 C 340 330 330 400 380 430 L 410 430 L 410 -10 L -10 -10 Z',
  'M -10 360 C 40 340 80 380 90 420 C 100 460 70 500 120 530 L -10 530 Z',
  'M 220 470 C 250 440 300 450 320 480 C 335 505 300 530 260 530 L 215 530 C 205 510 205 490 220 470 Z',
];
const RAIL = 'M 48 96 C 90 120 120 160 140 210 C 158 255 200 280 250 300 C 300 320 320 360 340 400';
const FLIGHT = 'M 340 400 C 300 470 160 470 70 440';
const STOPS: [number, number, string, 'l' | 'r'][] = [
  [48, 96, 'Lisbourne', 'r'],
  [140, 210, 'Hallow Junction', 'r'],
  [250, 300, 'Vessmark', 'l'],
  [340, 400, 'Orrin Fjord', 'l'],
  [70, 440, 'Calder Bay', 'r'],
];

function MapPage({ width, top, bottom }: PageProps) {
  const ink = '#24302f';
  const mapH = width * 1.3;
  return (
    <View style={[styles.page, { backgroundColor: '#dfe6e4', paddingTop: top + 10 }]}>
      <View style={{ paddingHorizontal: 20, gap: 4 }}>
        <Text style={[styles.kicker, { color: '#3f6f69' }]}>ROUTE · THE AUTUMN LINE</Text>
        <Text style={[styles.journalTitle, { color: ink, fontSize: 32 }]}>2,140 km, six stops</Text>
      </View>
      <Svg width={width} height={mapH} viewBox="0 0 400 520" style={{ marginTop: 6 }}>
        {Array.from({ length: 9 }, (_, i) => (
          <Path key={`g${i}`} d={`M ${i * 50} 0 L ${i * 50} 520`} stroke="#c9d3d0" strokeWidth={0.7} />
        ))}
        {Array.from({ length: 11 }, (_, i) => (
          <Path key={`h${i}`} d={`M 0 ${i * 50} L 400 ${i * 50}`} stroke="#c9d3d0" strokeWidth={0.7} />
        ))}
        {LAND.map((d, i) => (
          <Path key={`l${i}`} d={d} fill="#efe9dc" stroke="#b9b29f" strokeWidth={1} />
        ))}
        <Path d={RAIL} stroke={ink} strokeWidth={5} fill="none" strokeLinecap="round" />
        <Path d={RAIL} stroke="#efe9dc" strokeWidth={2} fill="none" strokeDasharray="7 7" strokeLinecap="butt" />
        <Path d={FLIGHT} stroke="#b4562f" strokeWidth={2} fill="none" strokeDasharray="2 6" strokeLinecap="round" />
        <G transform="translate(205 456) rotate(-168)">
          <Path d="M -9 0 L 9 0 M 2 -7 L 5 0 L 2 7 M -7 -3 L -8 0 L -7 3" stroke="#b4562f" strokeWidth={2} fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </G>
        {STOPS.map(([x, y, name, side], i) => (
          <G key={name}>
            <Circle cx={x} cy={y} r={i === 2 ? 9 : 6.5} fill={i === 2 ? '#b4562f' : '#fbf8f1'} stroke={ink} strokeWidth={2.2} />
            <SvgText
              x={side === 'r' ? x + 14 : x - 14}
              y={y + 5}
              fontSize={14}
              fontFamily={SANS}
              fontWeight="600"
              fill={ink}
              textAnchor={side === 'r' ? 'start' : 'end'}
            >
              {name}
            </SvgText>
          </G>
        ))}
        <Rect x={300} y={28} width={70} height={1.5} fill={ink} />
        <SvgText x={335} y={20} fontSize={11} fontFamily={SANS} fill={ink} textAnchor="middle">
          100 km
        </SvgText>
      </Svg>
      <View style={[styles.nextCard, { bottom: bottom + 36 }]}>
        <Image source={photo(S.stop, 64)} style={styles.nextThumb} contentFit="cover" transition={250} />
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={styles.statLabel}>Next stop</Text>
          <Text style={[styles.tileTitle, { color: ink, fontSize: 18 }]}>Vessmark Central</Text>
          <Text style={styles.tileMeta}>14:05 · Platform 3</Text>
        </View>
        <Text style={[styles.statValue, { color: '#b4562f' }]}>38 min</Text>
      </View>
      <Handle bottom={bottom} ink={ink} />
    </View>
  );
}

function ProfilePage({ width, top, bottom }: PageProps) {
  const ink = '#efe7da';
  const cell = (width - 40 - 8) / 3;
  const grid = [S.arch, W.d, S.hall, W.e, S.rails, W.f];
  return (
    <View style={[styles.page, { backgroundColor: '#17212b', paddingTop: top + 18, paddingHorizontal: 20 }]}>
      <View style={[styles.row, { gap: 16, justifyContent: 'flex-start' }]}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>IM</Text>
        </View>
        <View style={{ gap: 2, flex: 1 }}>
          <Text style={[styles.entryHeadline, { color: ink, fontSize: 26, lineHeight: 30 }]}>Ines Marlow</Text>
          <Text style={[styles.tileMeta, { color: '#8fa0ad' }]}>Collecting quiet platforms since 2019</Text>
        </View>
      </View>
      <View style={[styles.row, styles.profileStats]}>
        {[
          ['41', 'trips'],
          ['1.3k', 'photos'],
          ['18.2k', 'km by rail'],
        ].map(([value, label]) => (
          <View key={label} style={{ gap: 2 }}>
            <Text style={[styles.profileNumber, { color: ink }]}>{value}</Text>
            <Text style={[styles.statLabel, { color: '#8fa0ad' }]}>{label}</Text>
          </View>
        ))}
      </View>
      <Text style={[styles.kicker, { color: '#e3a27d', marginTop: 26, marginBottom: 12 }]}>SAVED FRAMES</Text>
      <View style={styles.grid}>
        {grid.map((id) => (
          <Image key={id} source={photo(id, cell)} style={[styles.gridCell, { width: cell, height: cell * 1.18 }]} contentFit="cover" transition={250} />
        ))}
      </View>
      <View style={styles.badge}>
        <Text style={[styles.tileTitle, { color: ink, fontSize: 16 }]}>Next trip</Text>
        <Text style={[styles.tileMeta, { color: '#8fa0ad' }]}>Orrin Fjord by the night train · 3 Nov</Text>
      </View>
      <Handle bottom={bottom} ink={ink} />
    </View>
  );
}

export const PAGES: PageInfo[] = [
  { title: 'Journal', tint: '#f2ede4', ink: '#1d1a16', Page: JournalPage },
  { title: 'Window seat', tint: '#12110f', ink: '#f1ece3', Page: EntryPage },
  { title: 'Route map', tint: '#dfe6e4', ink: '#24302f', Page: MapPage },
  { title: 'Profile', tint: '#17212b', ink: '#efe7da', Page: ProfilePage },
];

const styles = StyleSheet.create({
  page: { flex: 1, overflow: 'hidden' },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  kicker: { fontFamily: SANS, fontSize: 11, fontWeight: '700', letterSpacing: 1.6, paddingHorizontal: 0 },
  monogram: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: '#1d1a16',
    alignItems: 'center',
    justifyContent: 'center',
  },
  monogramText: { fontFamily: SERIF, color: '#f2ede4', fontSize: 13, fontWeight: '700' },
  journalTitle: { fontFamily: SERIF, fontSize: 40, fontWeight: '700', letterSpacing: -0.8, marginTop: 6 },
  journalLead: { fontFamily: SERIF, fontStyle: 'italic', fontSize: 16, color: '#6f665b', marginTop: 2 },
  heroImage: { borderRadius: 18, borderCurve: 'continuous', marginTop: 20, backgroundColor: '#ddd5c8' },
  meta: { fontFamily: SANS, fontSize: 11, fontWeight: '700', letterSpacing: 1.4, marginTop: 14 },
  entryTitle: { fontFamily: SERIF, fontSize: 22, lineHeight: 27, fontWeight: '700', marginTop: 4, letterSpacing: -0.2 },
  entryBody: { fontFamily: SERIF, fontSize: 15, lineHeight: 21, color: '#6f665b', marginTop: 6 },
  tile: { borderRadius: 14, borderCurve: 'continuous', backgroundColor: '#3a3631' },
  tileTitle: { fontFamily: SERIF, fontSize: 17, fontWeight: '700' },
  tileMeta: { fontFamily: SANS, fontSize: 12, fontWeight: '500', color: '#8a8073' },
  fade: { position: 'absolute', left: 0, right: 0, bottom: 0, height: 150 },
  handleWrap: { position: 'absolute', left: 0, right: 0, alignItems: 'center' },
  handle: { width: 54, height: HANDLE_HEIGHT, borderRadius: 2.5, opacity: 0.5 },
  entryBar: { position: 'absolute', left: 20, right: 20 },
  glassButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(18,17,15,0.45)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(241,236,227,0.35)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  seatChip: {
    paddingHorizontal: 12,
    height: 28,
    borderRadius: 14,
    justifyContent: 'center',
    backgroundColor: 'rgba(241,236,227,0.9)',
  },
  seatText: { fontFamily: SANS, fontSize: 11, fontWeight: '700', letterSpacing: 1.2, color: '#12110f' },
  entryHeadline: { fontFamily: SERIF, fontSize: 31, lineHeight: 36, fontWeight: '700', letterSpacing: -0.4 },
  statRow: {
    marginHorizontal: 20,
    marginTop: 20,
    paddingVertical: 14,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(241,236,227,0.22)',
  },
  statLabel: { fontFamily: SANS, fontSize: 11, fontWeight: '600', letterSpacing: 0.6, color: '#8a8073', textTransform: 'uppercase' },
  statValue: { fontFamily: SANS, fontSize: 17, fontWeight: '600', fontVariant: ['tabular-nums'] },
  nextCard: {
    position: 'absolute',
    left: 16,
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    padding: 12,
    borderRadius: 22,
    borderCurve: 'continuous',
    backgroundColor: '#fbf8f1',
    boxShadow: '0 10px 30px rgba(36,48,47,0.16)',
  },
  nextThumb: { width: 64, height: 64, borderRadius: 14, borderCurve: 'continuous' },
  avatar: {
    width: 76,
    height: 76,
    borderRadius: 38,
    backgroundColor: '#e3a27d',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontFamily: SERIF, fontSize: 28, fontWeight: '700', color: '#17212b' },
  profileStats: {
    marginTop: 26,
    paddingVertical: 16,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(239,231,218,0.2)',
  },
  profileNumber: { fontFamily: SERIF, fontSize: 28, fontWeight: '700', fontVariant: ['tabular-nums'] },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  gridCell: { borderRadius: 6, backgroundColor: '#24303b' },
  badge: {
    marginTop: 16,
    padding: 16,
    gap: 3,
    borderRadius: 18,
    borderCurve: 'continuous',
    backgroundColor: 'rgba(239,231,218,0.07)',
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(239,231,218,0.16)',
  },
});
