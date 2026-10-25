import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { CrossStitchInput } from './CrossStitchInput';

export default function CrossStitchInputDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EDE3D3' }}>
      <StatusBar hidden />
      <CrossStitchInput autoPlay />
    </View>
  );
}
