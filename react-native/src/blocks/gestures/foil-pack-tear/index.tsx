import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FoilPackTear } from './FoilPackTear';

export default function FoilPackTearDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#010207' }}>
      <StatusBar hidden />
      <FoilPackTear autoPlay />
    </View>
  );
}
