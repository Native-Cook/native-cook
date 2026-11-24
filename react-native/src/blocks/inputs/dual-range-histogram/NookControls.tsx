import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';

import { AMENITIES, BEDROOMS, PLACE_TYPES } from './nookData';
import type { NookControlsProps, NookPlacePickerProps } from './NookControls.types';

const ACCENT = '#d9542f';
const INK = '#2a2420';
const MUTE = '#7c7267';

function Segments({ items, value, onChange }: { items: readonly string[]; value: number; onChange: (i: number) => void }) {
  return (
    <View style={styles.segments}>
      {items.map((name, i) => (
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ selected: value === i }}
          key={name}
          onPress={() => onChange(i)}
          style={[styles.segment, value === i && styles.segmentOn]}
        >
          <Text numberOfLines={1} style={[styles.segmentText, value === i && styles.segmentTextOn]}>
            {name}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}

export function NookPlacePicker({ width, placeType, onPlaceType }: NookPlacePickerProps) {
  return (
    <View style={{ width }}>
      <Segments items={PLACE_TYPES} onChange={onPlaceType} value={placeType} />
    </View>
  );
}

/** The filter's other controls where SwiftUI is not available (Android). */
export function NookControls({ width, bedrooms, amenities, amenityRows, onBedrooms, onAmenity }: NookControlsProps) {
  return (
    <View style={{ width, gap: 8 }}>
      <View style={styles.row}>
        <Text style={styles.label}>Bedrooms</Text>
        <View style={styles.bedrooms}>
          <Segments items={BEDROOMS} onChange={onBedrooms} value={bedrooms} />
        </View>
      </View>
      {AMENITIES.slice(0, amenityRows).map((a, i) => (
        <View key={a.label} style={styles.row}>
          <Text style={styles.label}>{a.label}</Text>
          <Switch onValueChange={(on) => onAmenity(i, on)} thumbColor="#fffdf9" trackColor={{ true: ACCENT, false: '#d8d1c5' }} value={amenities[i]} />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: 40, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { color: INK, fontSize: 15, fontWeight: '500' },
  bedrooms: { width: 200 },
  segments: { flexDirection: 'row', padding: 2, borderRadius: 9, backgroundColor: 'rgba(42,36,32,0.08)' },
  segment: { flex: 1, height: 32, borderRadius: 7, alignItems: 'center', justifyContent: 'center' },
  segmentOn: { backgroundColor: '#fffdf9', boxShadow: '0 1px 3px rgba(60,40,25,0.2)' },
  segmentText: { color: MUTE, fontSize: 13, fontWeight: '500' },
  segmentTextOn: { color: INK, fontWeight: '600' },
});
