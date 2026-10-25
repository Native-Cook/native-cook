import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { DrawerCabinetSheet } from './DrawerCabinetSheet';

export default function DrawerCabinetSheetDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EFE7DA' }}>
      <StatusBar hidden />
      <DrawerCabinetSheet autoPlay />
    </View>
  );
}
