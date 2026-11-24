import { Host, Picker, Text } from '@expo/ui/swift-ui';
import { pickerStyle, tag } from '@expo/ui/swift-ui/modifiers';
import type { JSX } from 'react';

import { LIGHT_OPTIONS, type CompareControlsProps } from './CompareControls.types';

/**
 * Day / Evening: the system segmented control (SwiftUI through @expo/ui). The film drives it through
 * `selected`; a finger uses it the native way. Android gets the React Native row in CompareControls.tsx.
 */
export function CompareControls({ selected, onSelect, width }: CompareControlsProps): JSX.Element {
  return (
    <Host colorScheme="light" style={{ width, height: 32 }}>
      <Picker modifiers={[pickerStyle('segmented')]} onSelectionChange={onSelect} selection={selected}>
        {LIGHT_OPTIONS.map((o, i) => (
          <Text key={o} modifiers={[tag(i)]}>
            {o}
          </Text>
        ))}
      </Picker>
    </Host>
  );
}
