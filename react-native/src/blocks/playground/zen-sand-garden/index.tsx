import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ZenSandGarden } from './ZenSandGarden';

export default function ZenSandGardenDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#d9cdb8' }}>
      <StatusBar hidden />
      <ZenSandGarden autoPlay />
    </View>
  );
}
