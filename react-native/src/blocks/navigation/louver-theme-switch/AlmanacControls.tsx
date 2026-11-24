import { Pressable, StyleSheet, Switch, Text, View } from 'react-native';

import type { RowToggleProps, ThemePickerProps } from './AlmanacControls.types';

/** Android and web: the same two controls in React Native. iOS uses AlmanacControls.ios.tsx. */
export function ThemePicker({ theme, onSelect }: ThemePickerProps) {
  const dark = theme === 'dark';
  return (
    <View style={[styles.track, dark && styles.trackDark]}>
      {(['light', 'dark'] as const).map((option) => {
        const on = option === theme;
        return (
          <Pressable
            key={option}
            accessibilityRole="button"
            accessibilityState={{ selected: on }}
            onPress={() => onSelect(option)}
            style={[styles.segment, on && (dark ? styles.onDark : styles.onLight)]}
          >
            <Text style={[styles.label, dark && styles.labelDark]}>{option === 'light' ? 'Light' : 'Dark'}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function RowToggle({ label, value, onChange, tint }: RowToggleProps) {
  return <Switch accessibilityLabel={label} value={value} onValueChange={onChange} trackColor={{ true: tint }} />;
}

const styles = StyleSheet.create({
  track: { flexDirection: 'row', height: 36, borderRadius: 18, padding: 2, backgroundColor: 'rgba(40,36,28,0.09)' },
  trackDark: { backgroundColor: 'rgba(235,232,225,0.12)' },
  segment: { flex: 1, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  onLight: { backgroundColor: '#FFFDF8', boxShadow: '0 1px 3px rgba(40,36,28,0.2)' },
  onDark: { backgroundColor: '#3A3B45' },
  label: { fontSize: 14, fontWeight: '600', color: '#1E1C18' },
  labelDark: { color: '#ECE9E2' },
});
