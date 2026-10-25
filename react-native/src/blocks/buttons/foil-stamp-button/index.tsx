import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FoilStampButton } from './FoilStampButton';

export default function FoilStampButtonDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#e6ddcf' }}>
      <StatusBar hidden />
      <FoilStampButton autoPlay />
    </View>
  );
}
