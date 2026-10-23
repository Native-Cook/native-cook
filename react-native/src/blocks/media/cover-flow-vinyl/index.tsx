import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CoverFlowVinyl } from './CoverFlowVinyl';

export default function CoverFlowVinylDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0d0c0b' }}>
      <StatusBar hidden />
      <CoverFlowVinyl autoPlay />
    </View>
  );
}
