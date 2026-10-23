import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { FerrofluidBlob } from './FerrofluidBlob';

export default function FerrofluidBlobDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0B0B0C' }}>
      <StatusBar hidden />
      <FerrofluidBlob autoPlay />
    </View>
  );
}
