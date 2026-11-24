import { Host, Picker, Text } from '@expo/ui/swift-ui';
import { pickerStyle, tag } from '@expo/ui/swift-ui/modifiers';
import type { JSX } from 'react';

import type { FeedFilterProps } from './FeedFilter.types';

/** The inbox filter: a real SwiftUI segmented control (iOS). Android gets FeedFilter.tsx. */
export function FeedFilter({ options, selected, onSelect, width }: FeedFilterProps): JSX.Element {
  return (
    <Host colorScheme="light" matchContents={{ vertical: true }} style={{ width }}>
      <Picker modifiers={[pickerStyle('segmented')]} selection={selected} onSelectionChange={onSelect}>
        {options.map((label, i) => (
          <Text key={label} modifiers={[tag(i)]}>
            {label}
          </Text>
        ))}
      </Picker>
    </Host>
  );
}
