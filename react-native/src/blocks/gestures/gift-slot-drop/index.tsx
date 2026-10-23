import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { GiftSlotDrop } from './GiftSlotDrop';
import { COLORS } from './theme';

export default function GiftSlotDropDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: COLORS.stageEdge }}>
      <StatusBar style="light" />
      <GiftSlotDrop autoPlay />
    </View>
  );
}
