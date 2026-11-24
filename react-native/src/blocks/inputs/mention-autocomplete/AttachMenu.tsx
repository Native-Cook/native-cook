import { SymbolView } from 'expo-symbols';
import { Pressable } from 'react-native';

import type { AttachMenuProps } from './AttachMenu.types';

/** Android and web: a plain + button. iOS uses AttachMenu.ios.tsx (a SwiftUI menu). */
export function AttachMenu({ onTouch, tint }: AttachMenuProps) {
  return (
    <Pressable onPress={onTouch} accessibilityRole="button" accessibilityLabel="Attach" hitSlop={8}>
      <SymbolView name={{ ios: 'plus', android: 'add', web: 'add' }} size={18} tintColor={tint} />
    </Pressable>
  );
}
