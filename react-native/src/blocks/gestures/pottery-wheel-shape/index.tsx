import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PotteryWheelShape } from './PotteryWheelShape';

export default function PotteryWheelShapeDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#130F0C' }}>
      <StatusBar hidden />
      <PotteryWheelShape autoPlay />
    </View>
  );
}
