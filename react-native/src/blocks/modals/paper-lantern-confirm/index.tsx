import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { PaperLanternConfirm } from './PaperLanternConfirm';

export default function PaperLanternConfirmDemo() {
  return (
    <View style={{ flex: 1, backgroundColor: '#0A0D22' }}>
      <StatusBar hidden />
      <PaperLanternConfirm autoPlay />
    </View>
  );
}
