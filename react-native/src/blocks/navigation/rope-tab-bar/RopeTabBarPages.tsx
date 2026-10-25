import { LinearGradient } from 'expo-linear-gradient';
import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Animated, { type SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import Svg, { Circle, Defs, LinearGradient as SvgGradient, Path, Rect, Stop } from 'react-native-svg';

/*
 The four pages of "Basecamp" (static content, rendered once). Each page's artwork sits in a
 layer that lags behind the page by 30 % of the slide (`p` is the knot's place), so the pages
 cross-slide with a parallax.
 */

export const INK = '#f2ebdd';
export const INK_SOFT = '#9fb0a5';
const CARD = '#1d2c25';
const LINE = 'rgba(226,238,229,0.09)';
const ACCENT = '#e8743b';

/** A seeded wobble, so the contour map and the ridges look hand-surveyed but never change. */
function hash(i: number) {
  const s = Math.sin(i * 12.9898) * 43758.5453;
  return s - Math.floor(s);
}

function ridge(width: number, height: number, base: number, rough: number, seed: number) {
  let d = `M0 ${height}`;
  for (let x = 0; x <= width; x += width / 14) {
    const y = base - rough * (0.5 + 0.5 * Math.sin(x * 0.018 + seed) + 0.35 * hash(Math.round(x) + seed * 31));
    d += ` L${x.toFixed(1)} ${y.toFixed(1)}`;
  }
  return `${d} L${width} ${height} Z`;
}

function contour(cx: number, cy: number, r: number, seed: number) {
  let d = '';
  for (let k = 0; k <= 36; k++) {
    const a = (k / 36) * Math.PI * 2;
    const rr = r * (1 + 0.16 * Math.sin(3 * a + seed) + 0.08 * Math.sin(5 * a + seed * 2));
    d += `${k === 0 ? 'M' : 'L'}${(cx + Math.cos(a) * rr * 1.35).toFixed(1)} ${(cy + Math.sin(a) * rr).toFixed(1)} `;
  }
  return `${d}Z`;
}

function Parallax({ p, index, width, children }: { p: SharedValue<number>; index: number; width: number; children: React.ReactNode }) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: (p.get() - index) * width * 0.3 }] }));
  return <Animated.View style={[StyleSheet.absoluteFill, { left: -width * 0.35, right: -width * 0.35 }, style]}>{children}</Animated.View>;
}

function Row({ title, detail, trailing }: { title: string; detail: string; trailing: string }) {
  return (
    <View style={styles.row}>
      <View style={styles.rowText}>
        <Text style={styles.rowTitle} numberOfLines={1}>
          {title}
        </Text>
        <Text style={styles.rowDetail} numberOfLines={1}>
          {detail}
        </Text>
      </View>
      <Text style={styles.rowTrailing}>{trailing}</Text>
    </View>
  );
}

function Landscape({ w }: { w: number }) {
  const W = w * 1.7;
  return (
    <Svg width={W} height={220}>
      <Defs>
        <SvgGradient id="sky" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#f6d6b0" />
          <Stop offset="0.45" stopColor="#e9a986" />
          <Stop offset="1" stopColor="#9db0bd" />
        </SvgGradient>
        <SvgGradient id="lake" x1="0" y1="0" x2="0" y2="1">
          <Stop offset="0" stopColor="#7d97a6" />
          <Stop offset="1" stopColor="#2b4246" />
        </SvgGradient>
      </Defs>
      <Rect width={W} height={220} fill="url(#sky)" />
      <Circle cx={W * 0.55} cy={96} r={22} fill="#fff1d6" opacity={0.9} />
      <Path d={ridge(W, 220, 112, 46, 1.3)} fill="#8d9bb0" opacity={0.85} />
      <Path d={ridge(W, 220, 136, 40, 4.1)} fill="#5a6f84" />
      <Rect y={150} width={W} height={70} fill="url(#lake)" />
      <Path d={ridge(W, 220, 176, 24, 7.7)} fill="#253b35" />
      <Path d={ridge(W, 220, 204, 18, 2.2)} fill="#16261f" />
    </Svg>
  );
}

function Topo({ w }: { w: number }) {
  const W = w * 1.7;
  const cx = W * 0.5;
  return (
    <Svg width={W} height={220}>
      <Rect width={W} height={220} fill="#e9e1cf" />
      {[0, 1, 2, 3, 4, 5, 6].map((k) => (
        <Path key={k} d={contour(cx - 40, 104, 14 + k * 15, 0.7 + k * 0.4)} stroke="#a2906c" strokeWidth={k % 3 === 0 ? 1.4 : 0.8} fill="none" opacity={0.8} />
      ))}
      {[0, 1, 2, 3].map((k) => (
        <Path key={`b${k}`} d={contour(cx + 150, 60, 10 + k * 13, 2.1 + k)} stroke="#a2906c" strokeWidth={0.8} fill="none" opacity={0.7} />
      ))}
      <Path d={`M${cx - 150} 190 C${cx - 90} 150 ${cx - 120} 110 ${cx - 50} 100 S${cx + 40} 60 ${cx + 110} 70`} stroke={ACCENT} strokeWidth={3} strokeDasharray="7 5" strokeLinecap="round" fill="none" />
      <Circle cx={cx - 150} cy={190} r={6} fill="#1d2c25" stroke="#f2ebdd" strokeWidth={2} />
      <Circle cx={cx + 110} cy={70} r={7} fill={ACCENT} stroke="#f2ebdd" strokeWidth={2} />
    </Svg>
  );
}

function Sparkline({ seed }: { seed: number }) {
  let d = 'M0 22';
  for (let k = 0; k <= 12; k++) d += ` L${k * 5} ${(22 - 16 * (0.3 + 0.7 * Math.abs(Math.sin(k * 0.55 + seed)) * hash(k + seed * 7))).toFixed(1)}`;
  return (
    <Svg width={60} height={26}>
      <Path d={d} stroke={ACCENT} strokeWidth={1.6} fill="none" strokeLinejoin="round" />
    </Svg>
  );
}

type PageProps = { p: SharedValue<number>; width: number };

const Explore = memo(function Explore({ p, width }: PageProps) {
  return (
    <View style={styles.page}>
      <Text style={styles.lead}>Near Alder Ridge · 14 °C and clear</Text>
      <View style={styles.hero}>
        <Parallax p={p} index={0} width={width}>
          <Landscape w={width} />
        </Parallax>
        <LinearGradient colors={['rgba(12,22,17,0)', 'rgba(12,22,17,0.35)', 'rgba(12,22,17,0.78)']} locations={[0, 0.4, 1]} style={styles.heroShade} />
        <View style={styles.chip}>
          <Text style={styles.chipText}>Trail of the week</Text>
        </View>
        <View style={styles.heroText}>
          <Text style={styles.heroTitle}>Kestrel Lake sunrise</Text>
          <Text style={styles.heroDetail}>4.2 km · 2 h 10 min · Moderate</Text>
        </View>
      </View>
      <Text style={styles.section}>Nearby</Text>
      <View style={styles.list}>
        <View style={styles.trailRow}>
          <Row title="Pinecrest Loop" detail="6.8 km · ↑ 410 m · 3 h" trailing="" />
          <Sparkline seed={1.2} />
        </View>
        <View style={[styles.trailRow, styles.divider]}>
          <Row title="Granite Steps" detail="3.1 km · ↑ 280 m · 1 h 40" trailing="" />
          <Sparkline seed={3.4} />
        </View>
      </View>
    </View>
  );
});

const Routes = memo(function Routes({ p, width }: PageProps) {
  return (
    <View style={styles.page}>
      <Text style={styles.lead}>3 saved · 21.3 km planned this month</Text>
      <View style={styles.hero}>
        <Parallax p={p} index={1} width={width}>
          <Topo w={width} />
        </Parallax>
        <View style={[styles.chip, styles.chipDark]}>
          <Text style={styles.chipText}>Fernwater Trail · offline</Text>
        </View>
      </View>
      <Text style={styles.section}>Saved routes</Text>
      <View style={styles.list}>
        <Row title="Fernwater Trail" detail="11.4 km · ↑ 620 m" trailing="Sat" />
        <View style={styles.divider}>
          <Row title="Pinecrest Loop" detail="6.8 km · ↑ 410 m" trailing="Sun" />
        </View>
      </View>
    </View>
  );
});

const PACK = [
  ['Shelter', 2.6, '#e8743b'],
  ['Sleep', 2.1, '#d9b36c'],
  ['Kitchen', 1.4, '#7fa58e'],
  ['Clothing', 2.3, '#6f8fa8'],
] as const;

const Gear = memo(function Gear({ p, width }: PageProps) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: (p.get() - 2) * width * 0.12 }] }));
  return (
    <View style={styles.page}>
      <Text style={styles.lead}>Weekend pack · 2 nights</Text>
      <View style={[styles.hero, styles.packCard]}>
        <Animated.View style={[styles.packInner, style]}>
          <Text style={styles.packLabel}>BASE WEIGHT</Text>
          <Text style={styles.packWeight}>8.4 kg</Text>
          <View style={styles.packBar}>
            {PACK.map(([name, kg, color]) => (
              <View key={name} style={{ flex: kg, backgroundColor: color }} />
            ))}
          </View>
          <View style={styles.legend}>
            {PACK.map(([name, kg, color]) => (
              <View key={name} style={styles.legendItem}>
                <View style={[styles.dot, { backgroundColor: color }]} />
                <Text style={styles.legendText}>
                  {name} {kg} kg
                </Text>
              </View>
            ))}
          </View>
        </Animated.View>
      </View>
      <Text style={styles.section}>Still to pack</Text>
      <View style={styles.list}>
        <Row title="Shell jacket" detail="Clothing" trailing="420 g" />
        <View style={styles.divider}>
          <Row title="Water filter" detail="Kitchen" trailing="210 g" />
        </View>
      </View>
    </View>
  );
});

const Profile = memo(function Profile({ p, width }: PageProps) {
  const style = useAnimatedStyle(() => ({ transform: [{ translateX: (p.get() - 3) * width * 0.12 }] }));
  return (
    <View style={styles.page}>
      <Text style={styles.lead}>Alder Ridge hiking club · since 2023</Text>
      <View style={[styles.hero, styles.packCard]}>
        <Animated.View style={[styles.packInner, style]}>
          <View style={styles.person}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>MR</Text>
            </View>
            <View>
              <Text style={styles.heroTitle}>Mara Reyes</Text>
              <Text style={styles.heroDetail}>Next out: Kestrel Lake, Sat 25 Oct</Text>
            </View>
          </View>
          <View style={styles.statsRow}>
            {[
              ['23', 'Summits'],
              ['412', 'km this year'],
              ['38', 'Nights out'],
            ].map(([v, l]) => (
              <View key={l} style={styles.statTile}>
                <Text style={styles.statValue}>{v}</Text>
                <Text style={styles.statLabel}>{l}</Text>
              </View>
            ))}
          </View>
        </Animated.View>
      </View>
      <Text style={styles.section}>Safety</Text>
      <View style={styles.list}>
        <Row title="Trip plan shared" detail="With Theo Lund" trailing="On" />
        <View style={styles.divider}>
          <Row title="Offline maps" detail="3 regions · 412 MB" trailing="Ready" />
        </View>
      </View>
    </View>
  );
});

export function RopeTabBarPages({ p, width }: PageProps) {
  return (
    <>
      <Explore p={p} width={width} />
      <Routes p={p} width={width} />
      <Gear p={p} width={width} />
      <Profile p={p} width={width} />
    </>
  );
}

const styles = StyleSheet.create({
  page: { width: '25%', paddingHorizontal: 20, gap: 12 },
  lead: { color: INK_SOFT, fontSize: 15, fontWeight: '500' },
  hero: { height: 196, borderRadius: 24, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: CARD, boxShadow: '0 1px 2px rgba(0,0,0,0.3), 0 16px 32px rgba(0,0,0,0.25)' },
  heroShade: { position: 'absolute', left: 0, right: 0, bottom: 0, top: '35%' },
  chip: { position: 'absolute', top: 14, left: 14, paddingHorizontal: 12, height: 28, borderRadius: 14, justifyContent: 'center', backgroundColor: 'rgba(16,26,21,0.55)' },
  chipDark: { backgroundColor: 'rgba(29,44,37,0.85)' },
  chipText: { color: INK, fontSize: 12, fontWeight: '600' },
  heroText: { position: 'absolute', left: 18, right: 18, bottom: 16, gap: 2 },
  heroTitle: { color: INK, fontSize: 20, fontWeight: '700' },
  heroDetail: { color: 'rgba(242,235,221,0.75)', fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  section: { color: INK, fontSize: 17, fontWeight: '600', marginTop: 8 },
  list: { borderRadius: 20, borderCurve: 'continuous', backgroundColor: CARD, paddingHorizontal: 16, boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.05)' },
  row: { flex: 1, flexDirection: 'row', alignItems: 'center', minHeight: 60, gap: 12 },
  rowText: { flex: 1, gap: 2 },
  rowTitle: { color: INK, fontSize: 16, fontWeight: '600' },
  rowDetail: { color: INK_SOFT, fontSize: 13, fontVariant: ['tabular-nums'] },
  rowTrailing: { color: INK_SOFT, fontSize: 14, fontWeight: '500', fontVariant: ['tabular-nums'] },
  trailRow: { flexDirection: 'row', alignItems: 'center' },
  divider: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: LINE },
  packCard: { backgroundColor: '#22342b', padding: 0 },
  packInner: { flex: 1, padding: 20, gap: 10, justifyContent: 'center' },
  packLabel: { color: INK_SOFT, fontSize: 11, fontWeight: '700', letterSpacing: 1.4 },
  packWeight: { color: INK, fontSize: 40, fontWeight: '700', fontVariant: ['tabular-nums'], letterSpacing: -1 },
  packBar: { flexDirection: 'row', height: 10, borderRadius: 5, overflow: 'hidden', gap: 2 },
  legend: { flexDirection: 'row', flexWrap: 'wrap', columnGap: 14, rowGap: 6 },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  dot: { width: 8, height: 8, borderRadius: 4 },
  legendText: { color: INK_SOFT, fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  person: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  avatar: { width: 52, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center', backgroundColor: '#e8743b', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.3)' },
  avatarText: { color: '#2a140a', fontSize: 18, fontWeight: '700' },
  statsRow: { flexDirection: 'row', gap: 8, marginTop: 6 },
  statTile: { flex: 1, borderRadius: 14, borderCurve: 'continuous', paddingVertical: 10, alignItems: 'center', backgroundColor: 'rgba(242,235,221,0.06)' },
  statValue: { color: INK, fontSize: 20, fontWeight: '700', fontVariant: ['tabular-nums'] },
  statLabel: { color: INK_SOFT, fontSize: 11, fontWeight: '500' },
});
