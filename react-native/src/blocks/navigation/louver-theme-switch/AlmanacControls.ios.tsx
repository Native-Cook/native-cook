import { Host, Picker, Text, Toggle } from '@expo/ui/swift-ui';
import { labelsHidden, pickerStyle, tag, tint as tintColor } from '@expo/ui/swift-ui/modifiers';

import type { RowToggleProps, ThemePickerProps } from './AlmanacControls.types';

/** The real SwiftUI segmented control and switch. Android and web use AlmanacControls.tsx. */
export function ThemePicker({ theme, onSelect }: ThemePickerProps) {
  return (
    <Host colorScheme={theme} matchContents={{ vertical: true }} style={styles.fill}>
      <Picker
        modifiers={[pickerStyle('segmented')]}
        selection={theme === 'dark' ? 1 : 0}
        onSelectionChange={(index: number) => onSelect(index === 1 ? 'dark' : 'light')}
      >
        <Text modifiers={[tag(0)]}>Light</Text>
        <Text modifiers={[tag(1)]}>Dark</Text>
      </Picker>
    </Host>
  );
}

export function RowToggle({ theme, label, value, onChange, tint }: RowToggleProps) {
  return (
    <Host colorScheme={theme} matchContents>
      <Toggle label={label} isOn={value} onIsOnChange={onChange} modifiers={[labelsHidden(), tintColor(tint)]} />
    </Host>
  );
}

const styles = { fill: { width: '100%' } } as const;
