import type { JSX } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { LIGHT_OPTIONS, type CompareControlsProps } from './CompareControls.types';

/** Day / Evening where SwiftUI is not available (Android). iOS: CompareControls.ios.tsx. */
export function CompareControls({ selected, onSelect, width }: CompareControlsProps): JSX.Element {
  return (
    <View style={[styles.segments, { width }]}>
      {LIGHT_OPTIONS.map((o, i) => (
        <Pressable accessibilityRole="button" accessibilityState={{ selected: selected === i }} key={o} onPress={() => onSelect(i)} style={[styles.segment, selected === i && styles.segmentOn]}>
          <Text style={[styles.text, selected === i && styles.textOn]}>{o}</Text>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  segments: { flexDirection: 'row', height: 32, borderRadius: 9, backgroundColor: 'rgba(118,118,128,0.14)', padding: 2 },
  segment: { flex: 1, borderRadius: 7, alignItems: 'center', justifyContent: 'center' },
  segmentOn: { backgroundColor: '#fffdf8', boxShadow: '0 1px 3px rgba(33,29,24,0.14)' },
  text: { fontSize: 13, fontWeight: '500', color: '#211d18' },
  textOn: { fontWeight: '600' },
});
