import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { VelvetCurtainSheet } from './VelvetCurtainSheet';

export default function VelvetCurtainSheetDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#140A0F' }}>
      <StatusBar hidden />
      <VelvetCurtainSheet autoPlay />
    </View>
  );
}
