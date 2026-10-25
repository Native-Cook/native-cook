import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PaperCutDioramaSplash } from './PaperCutDioramaSplash';

export default function PaperCutDioramaSplashDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#EFE6D6' }}>
      <StatusBar hidden />
      <PaperCutDioramaSplash autoPlay />
    </View>
  );
}
