import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { RollerBlindSheet } from './RollerBlindSheet';

export default function RollerBlindSheetDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#E6DED2' }}>
      <StatusBar hidden />
      <RollerBlindSheet autoPlay />
    </View>
  );
}
