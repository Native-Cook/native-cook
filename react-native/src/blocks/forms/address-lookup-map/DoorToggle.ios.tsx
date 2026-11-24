import { Host, Toggle } from '@expo/ui/swift-ui';
import { labelsHidden, tint } from '@expo/ui/swift-ui/modifiers';

import type { DoorToggleProps } from './DoorToggle.types';

/** The real SwiftUI switch. Android and web use DoorToggle.tsx. */
export function DoorToggle({ label, value, onChange, accent }: DoorToggleProps) {
  return (
    <Host colorScheme="light" matchContents>
      <Toggle label={label} isOn={value} onIsOnChange={onChange} modifiers={[labelsHidden(), tint(accent)]} />
    </Host>
  );
}
