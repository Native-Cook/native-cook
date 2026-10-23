import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { ClayPressBlob } from './ClayPressBlob';

export default function ClayPressBlobDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#F2EEE8' }}>
      <StatusBar hidden />
      <ClayPressBlob autoPlay />
    </View>
  );
}
