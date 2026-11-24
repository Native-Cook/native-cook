import { Button, Host, Image, Menu } from '@expo/ui/swift-ui';
import { View } from 'react-native';

import type { AttachMenuProps } from './AttachMenu.types';

/** The composer's + button: a real SwiftUI menu. Android and web use AttachMenu.tsx. */
export function AttachMenu({ onPick, onTouch, tint }: AttachMenuProps) {
  return (
    <View onTouchStart={onTouch}>
      <Host colorScheme="light" matchContents>
        <Menu label={<Image systemName="plus" size={18} color={tint} />}>
          <Button label="Photo" systemImage="photo" onPress={() => onPick('photo')} />
          <Button label="File" systemImage="doc" onPress={() => onPick('file')} />
          <Button label="Poll" systemImage="chart.bar" onPress={() => onPick('poll')} />
        </Menu>
      </Host>
    </View>
  );
}
