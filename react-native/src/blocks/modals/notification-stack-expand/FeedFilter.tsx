import type { JSX } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import type { FeedFilterProps } from './FeedFilter.types';

/** The inbox filter where SwiftUI is not available (Android, web). iOS: FeedFilter.ios.tsx. */
export function FeedFilter({ options, selected, onSelect, width }: FeedFilterProps): JSX.Element {
  return (
    <View style={[styles.track, { width }]} accessibilityRole="tablist">
      {options.map((label, i) => (
        <Pressable
          key={label}
          onPress={() => onSelect(i)}
          style={[styles.segment, selected === i && styles.on]}
          accessibilityRole="tab"
          accessibilityState={{ selected: selected === i }}
        >
          <Text style={[styles.label, selected === i && styles.labelOn]}>{label}</Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  track: { flexDirection: 'row', height: 32, borderRadius: 9, backgroundColor: 'rgba(118,118,128,0.12)', padding: 2 },
  segment: { flex: 1, borderRadius: 7, alignItems: 'center', justifyContent: 'center' },
  on: { backgroundColor: '#FFFFFF', boxShadow: '0 1px 3px rgba(0,0,0,0.1), 0 1px 1px rgba(0,0,0,0.04)' },
  label: { fontSize: 13, fontWeight: '500', color: '#1B1F24' },
  labelOn: { fontWeight: '600' },
});
