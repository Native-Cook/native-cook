import * as Haptics from 'expo-haptics';
import { StatusBar } from 'expo-status-bar';
import { useCallback, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { useSharedValue } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { DualRangeHistogram } from './DualRangeHistogram';
import { NookControls, NookPlacePicker } from './NookControls';
import { AMENITIES, makeBins, PRICE_DEFAULT, PRICE_GAP, PRICE_MAX, PRICE_MIN, PRICE_STEP } from './nookData';
import { RollingDigit } from './RollingDigit';

const PAGE = '#f6f1e8';
const INK = '#2a2420';
const MUTE = '#7c7267';
const SIDE = 20;
const NO_AMENITIES = [false, false, false] as const;

/** The filter sheet of Nook, an invented rental search app, around the histogram. */
export function NookFilterScreen({ autoPlay = true }: { autoPlay?: boolean }) {
  const { width, height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const compact = height < 740;
  const contentW = Math.min(width, 520) - SIDE * 2;

  const [placeType, setPlaceType] = useState(0);
  const [bedrooms, setBedrooms] = useState(0);
  const [amenities, setAmenities] = useState<readonly boolean[]>(NO_AMENITIES);
  const [resetKey, setResetKey] = useState(0);
  const count = useSharedValue(0);

  const bins = useMemo(() => {
    let share = 1;
    amenities.forEach((on, i) => {
      if (on) share *= AMENITIES[i].share;
    });
    return makeBins(placeType, bedrooms, share);
  }, [amenities, bedrooms, placeType]);

  const onAmenity = useCallback((index: number, on: boolean) => {
    void Haptics.selectionAsync();
    setAmenities((list) => list.map((v, i) => (i === index ? on : v)));
  }, []);
  const clear = useCallback(() => {
    void Haptics.selectionAsync();
    setPlaceType(0);
    setBedrooms(0);
    setAmenities(NO_AMENITIES);
    setResetKey((k) => k + 1);
  }, []);

  const amenityRows = compact ? 2 : 3;
  const reserved = compact ? 370 : 420;
  const chartHeight = Math.round(Math.max(96, Math.min(compact ? 150 : 196, height - insets.top - insets.bottom - reserved - amenityRows * 40)));

  return (
    <View style={[styles.screen, { paddingTop: insets.top + (compact ? 4 : 10), paddingBottom: Math.max(insets.bottom, 12) + 4 }]}>
      <StatusBar style="dark" />
      <View style={[styles.head, { width: contentW }]}>
        <Text accessibilityRole="header" style={styles.title}>
          Filters
        </Text>
        <Pressable accessibilityRole="button" hitSlop={12} onPress={clear}>
          <Text style={styles.clear}>Clear all</Text>
        </Pressable>
      </View>

      <View style={[styles.body, { width: contentW, gap: compact ? 12 : 20 }]}>
        <NookPlacePicker onPlaceType={setPlaceType} placeType={placeType} width={contentW} />

        <View>
          <Text style={styles.section}>Price per month</Text>
          <Text style={styles.sub}>Most homes in Harbour District are $1,900 to $2,800</Text>
          <View style={{ height: compact ? 8 : 16 }} />
          <DualRangeHistogram
            autoPlay={autoPlay}
            bins={bins}
            chartHeight={chartHeight}
            count={count}
            defaultValue={PRICE_DEFAULT}
            max={PRICE_MAX}
            min={PRICE_MIN}
            minGap={PRICE_GAP}
            resetKey={resetKey}
            step={PRICE_STEP}
            width={contentW}
          />
        </View>

        <NookControls
          amenities={amenities}
          amenityRows={amenityRows}
          bedrooms={bedrooms}
          onAmenity={onAmenity}
          onBedrooms={setBedrooms}
          placeType={placeType}
          onPlaceType={setPlaceType}
          width={contentW}
        />
      </View>

      <Pressable accessibilityLabel="Show homes" accessibilityRole="button" style={({ pressed }) => [styles.cta, { width: contentW }, pressed && styles.ctaPressed]}>
        <Text allowFontScaling={false} style={styles.ctaText}>Show </Text>
        <RollingDigit height={22} hideLeading place={100} textStyle={styles.ctaText} value={count} width={10.5} />
        <RollingDigit height={22} hideLeading place={10} textStyle={styles.ctaText} value={count} width={10.5} />
        <RollingDigit height={22} lowest place={1} textStyle={styles.ctaText} value={count} width={10.5} />
        <Text allowFontScaling={false} style={styles.ctaText}> homes</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, alignItems: 'center', backgroundColor: PAGE },
  head: { height: 44, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { color: INK, fontSize: 28, fontWeight: '700', letterSpacing: -0.4 },
  clear: { color: MUTE, fontSize: 15, fontWeight: '500' },
  body: { flex: 1, paddingTop: 12 },
  section: { color: INK, fontSize: 17, fontWeight: '600' },
  sub: { color: MUTE, fontSize: 13, marginTop: 2 },
  cta: {
    height: 52,
    borderRadius: 26,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#2a2420',
    boxShadow: '0 1px 2px rgba(42,36,32,0.3), 0 10px 24px rgba(42,36,32,0.18)',
  },
  ctaPressed: { transform: [{ scale: 0.98 }] },
  ctaText: { color: '#fbf6ee', fontSize: 17, fontWeight: '600', lineHeight: 22, fontVariant: ['tabular-nums'] },
});
