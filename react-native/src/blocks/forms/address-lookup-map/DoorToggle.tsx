import { Switch } from 'react-native';

import type { DoorToggleProps } from './DoorToggle.types';

/** Android and web: the platform switch. iOS uses DoorToggle.ios.tsx (SwiftUI). */
export function DoorToggle({ label, value, onChange, accent }: DoorToggleProps) {
  return <Switch accessibilityLabel={label} value={value} onValueChange={onChange} trackColor={{ true: accent }} />;
}
