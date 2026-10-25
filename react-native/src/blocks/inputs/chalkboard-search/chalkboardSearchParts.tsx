import { BlurMask, Group, Path, RoundedRect, Shader, type SkPath, type SkRuntimeEffect } from '@shopify/react-native-skia';
import { SymbolView } from 'expo-symbols';
import { type ComponentProps, useMemo } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';

/*
 Chalkboard Search — the smaller pieces: a chalk-filled path, the felt eraser, and the café's
 details under the board (hours, address, categories, and the counter's specials when there is
 room for them).
*/

const P3 = Platform.OS === 'ios' ? 1 : 0;

type SymbolName = ComponentProps<typeof SymbolView>['name'];
const SPECIALS: { name: string; price: string; icon: SymbolName }[] = [
  { name: 'Cardamom bun', price: '$5 · 6 left', icon: { ios: 'birthday.cake', android: 'bakery_dining', web: 'bakery_dining' } },
  { name: 'Flat white', price: '$4 · oat +$0.5', icon: { ios: 'cup.and.saucer', android: 'coffee', web: 'coffee' } },
  { name: 'Fresh mint tea', price: '$3 · pot for two', icon: { ios: 'leaf', android: 'eco', web: 'eco' } },
];

/** The café details below the board; the specials row shows when the screen is tall enough. */
export function CafeInfo({ top, H, insetBottom }: { top: number; H: number; insetBottom: number }) {
  const showSpecials = top + 98 + 116 <= H - insetBottom - 8;
  return (
    <>
    <View style={[styles.below, { top }]}>
      <View style={styles.row}>
        <Text style={styles.open}>Open today · 7:00 – 18:00</Text>
        <Text style={styles.dist}>0.4 km</Text>
      </View>
      <Text style={styles.addr}>14 Alder Lane, by the canal</Text>
      <View style={styles.chips}>
        {['Pastry', 'Coffee', 'Brunch', 'Tea'].map((c, i) => (
          <View key={c} style={[styles.chip, i === 0 && styles.chipOn]}>
            <Text style={[styles.chipText, i === 0 && styles.chipTextOn]}>{c}</Text>
          </View>
        ))}
      </View>
      {showSpecials ? (
        <View style={styles.specials}>
          <Text style={styles.specialsTitle}>At the counter now</Text>
          <View style={styles.specialsRow}>
            {SPECIALS.map((sp) => (
              <View key={sp.name} style={styles.special}>
                <View style={styles.specialIcon}>
                  <SymbolView name={sp.icon} size={18} tintColor="#2c3a32" />
                </View>
                <Text style={styles.specialName} numberOfLines={1}>
                  {sp.name}
                </Text>
                <Text style={styles.specialPrice}>{sp.price}</Text>
              </View>
            ))}
          </View>
        </View>
      ) : null}
    </View>
    {showSpecials ? null : (
      <Text style={[styles.hint, { bottom: insetBottom + 16 }]}>Tap the board to search · swipe across it to wipe</Text>
    )}
    </>
  );
}

/** A chalk line: a soft haze, the chalk fill, and a rough edge pass. */
export function ChalkPath({ path, chalk, seed }: { path: SkPath; chalk: SkRuntimeEffect; seed: number }) {
  const u1 = useMemo(() => ({ p3: P3, seed }), [seed]);
  const u2 = useMemo(() => ({ p3: P3, seed: seed + 5 }), [seed]);
  return (
    <Group>
      <Path path={path} color="rgba(238,240,232,0.07)">
        <BlurMask blur={3} style="normal" />
      </Path>
      <Path path={path}>
        <Shader source={chalk} uniforms={u1} />
      </Path>
      <Path path={path} style="stroke" strokeWidth={0.9} opacity={0.45} transform={[{ translateX: 0.6 }, { translateY: 0.5 }]}>
        <Shader source={chalk} uniforms={u2} />
      </Path>
    </Group>
  );
}

/** The felt eraser, centred on (0, 0), its long side across the wipe. */
export function EraserArt() {
  return (
    <Group>
      <RoundedRect x={-12} y={-30} width={34} height={70} r={7} color="rgba(0,0,0,0.35)" transform={[{ translateX: 6 }, { translateY: 8 }]}>
        <BlurMask blur={8} style="normal" />
      </RoundedRect>
      <RoundedRect x={-15} y={-32} width={30} height={64} r={6} color="#d9d3c5" />
      <RoundedRect x={-11} y={-32} width={22} height={64} r={5} color="#8e5c33" />
      <RoundedRect x={-9} y={-30} width={4} height={60} r={2} color="rgba(255,226,184,0.45)" />
      <RoundedRect x={5} y={-30} width={3} height={60} r={1.5} color="rgba(40,20,8,0.35)" />
    </Group>
  );
}

const styles = StyleSheet.create({
  below: { position: 'absolute', left: 24, right: 24, gap: 8 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline' },
  open: { color: '#33261b', fontSize: 17, fontWeight: '600' },
  dist: { color: '#7a6a58', fontSize: 13, fontWeight: '500', fontVariant: ['tabular-nums'] },
  addr: { color: '#7a6a58', fontSize: 14, fontWeight: '400' },
  chips: { flexDirection: 'row', gap: 8, marginTop: 8 },
  chip: { height: 32, paddingHorizontal: 16, borderRadius: 16, justifyContent: 'center', backgroundColor: '#e2d6c2', boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.6)' },
  chipOn: { backgroundColor: '#2c3a32' },
  chipText: { color: '#4a3a2a', fontSize: 13, fontWeight: '600' },
  chipTextOn: { color: '#eef0e8' },
  specials: { marginTop: 16, gap: 10 },
  specialsTitle: { color: '#7a6a58', fontSize: 11, fontWeight: '700', letterSpacing: 1.6, textTransform: 'uppercase' },
  specialsRow: { flexDirection: 'row', gap: 8 },
  special: {
    flex: 1,
    padding: 10,
    gap: 2,
    borderRadius: 16,
    borderCurve: 'continuous',
    backgroundColor: '#f6efe3',
    boxShadow: '0 1px 2px rgba(60,40,20,0.12), 0 8px 18px rgba(60,40,20,0.1), inset 0 1px 0 rgba(255,255,255,0.8)',
  },
  specialIcon: { width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: '#e2d6c2', marginBottom: 4 },
  specialName: { color: '#33261b', fontSize: 14, fontWeight: '600' },
  specialPrice: { color: '#7a6a58', fontSize: 12, fontWeight: '500', fontVariant: ['tabular-nums'] },
  hint: { position: 'absolute', left: 24, right: 24, textAlign: 'center', color: 'rgba(74,58,42,0.6)', fontSize: 12, fontWeight: '500' },
});
